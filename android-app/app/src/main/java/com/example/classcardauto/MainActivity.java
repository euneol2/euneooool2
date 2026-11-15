
package com.example.classcardauto;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class MainActivity extends Activity {

    private WebView webView;
    private final Handler handler = new Handler();

    private boolean memorizeEnabled = true;
    private boolean recallEnabled = true;
    private boolean spellEnabled = true;

    private int repeatPercent = 300;

    private String lastMode = "";
    private boolean automationStarted = false;
    private boolean captureInProgress = false;

    private TextView statusText;
    private String lastDisplayedStatus = "";

    private SharedPreferences preferences;
    private String savedWordsJson = "[]";
    private int savedWordCount = 0;

    private final Runnable detector = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
                captureWordData();
                detectStudyScreen();
                pollSpellStatus();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("classcard_word_memory", MODE_PRIVATE);
        loadSavedWords();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 10, 16, 10);
        panel.setBackgroundColor(Color.rgb(240, 243, 248));

        TextView title = new TextView(this);
        title.setText("클카모바일 자동 학습");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER);

        CheckBox memorize = new CheckBox(this);
        memorize.setText("암기");
        memorize.setChecked(true);

        CheckBox recall = new CheckBox(this);
        recall.setText("리콜");
        recall.setChecked(true);

        CheckBox spell = new CheckBox(this);
        spell.setText("스펠");
        spell.setChecked(true);

        options.addView(memorize);
        options.addView(recall);
        options.addView(spell);
        panel.addView(options);

        memorize.setOnCheckedChangeListener((button, checked) -> {
            memorizeEnabled = checked;
            restartDetection();
        });

        recall.setOnCheckedChangeListener((button, checked) -> {
            recallEnabled = checked;
            restartDetection();
        });

        spell.setOnCheckedChangeListener((button, checked) -> {
            spellEnabled = checked;
            restartDetection();
        });

        LinearLayout repeatOptions = new LinearLayout(this);
        repeatOptions.setGravity(Gravity.CENTER);

        Button repeat100 = new Button(this);
        repeat100.setText("100%");

        Button repeat200 = new Button(this);
        repeat200.setText("200%");

        Button repeat300 = new Button(this);
        repeat300.setText("300%");

        repeatOptions.addView(repeat100);
        repeatOptions.addView(repeat200);
        repeatOptions.addView(repeat300);
        panel.addView(repeatOptions);

        repeat100.setOnClickListener(v -> setRepeat(100));
        repeat200.setOnClickListener(v -> setRepeat(200));
        repeat300.setOnClickListener(v -> setRepeat(300));

        statusText = new TextView(this);
        statusText.setText("학습 화면을 기다리는 중...");
        statusText.setTextColor(Color.DKGRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(14);
        panel.addView(statusText);

        root.addView(panel);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1
        ));

        setContentView(root);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        );

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);

        webView.addJavascriptInterface(new WordBridge(), "ClasscardBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("https://www.classcard.net/login");

        handler.postDelayed(detector, 1500);
    }

    private class WordBridge {

        @JavascriptInterface
        public void saveWords(String json) {
            runOnUiThread(() -> storeWords(json));
        }

        @JavascriptInterface
        public void sendSpellAnswer(String answer) {
            runOnUiThread(() -> {
                if (webView == null || answer == null) return;

                String script =
                        "(()=>{" +
                        "const answer=" + JSONObject.quote(answer) + ";" +
                        "const card=document.querySelector('.CardItem.current.showing');" +
                        "if(!card)return false;" +
                        "const visible=el=>el&&el.getClientRects().length>0&&getComputedStyle(el).display!=='none'&&getComputedStyle(el).visibility!=='hidden';" +
                        "const input=[...card.querySelectorAll('.spell-input:not(.hidden) input[name=\"input_answer\"],input[name=\"input_answer\"]')].find(visible);" +
                        "if(!input)return false;" +
                        "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                        "input.focus();" +
                        "input.click();" +
                        "setter.call(input,'');" +
                        "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "setter.call(input,answer);" +
                        "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "input.dispatchEvent(new Event('change',{bubbles:true}));" +
                        "return input.value===answer;" +
                        "})()";

                webView.evaluateJavascript(script, result -> {
                    if ("false".equals(result)) {
                        setStatus("입력창을 찾지 못했습니다.");
                    }
                });
            });
        }
    }

    private void loadSavedWords() {
        savedWordsJson = preferences.getString("words", "[]");
        validateSavedWords();
    }

    private void validateSavedWords() {
        try {
            JSONArray array = new JSONArray(savedWordsJson);
            JSONArray valid = new JSONArray();

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String en = item.optString("en", "").trim();
                String ko = item.optString("ko", "").trim();

                if (en.isEmpty() || ko.isEmpty()) continue;

                JSONObject word = new JSONObject();
                word.put("en", en);
                word.put("ko", ko);
                valid.put(word);
            }

            savedWordsJson = valid.toString();
            savedWordCount = valid.length();

        } catch (Exception e) {
            savedWordsJson = "[]";
            savedWordCount = 0;
        }
    }

    private void storeWords(String json) {
        try {
            JSONArray array = new JSONArray(json);
            JSONArray valid = new JSONArray();

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String en = item.optString("en", "").trim();
                String ko = item.optString("ko", "").trim();

                if (en.isEmpty() || ko.isEmpty()) continue;

                JSONObject word = new JSONObject();
                word.put("en", en);
                word.put("ko", ko);
                valid.put(word);
            }

            if (valid.length() == 0) {
                captureInProgress = false;
                setStatus("단어 수집 실패");
                return;
            }

            savedWordsJson = valid.toString();
            savedWordCount = valid.length();

            preferences.edit()
                    .putString("words", savedWordsJson)
                    .putInt("word_count", savedWordCount)
                    .apply();

            captureInProgress = false;
            setStatus("단어 저장 완료: " + savedWordCount + "개");

        } catch (Exception e) {
            captureInProgress = false;
            setStatus("단어 저장 오류: " + e.getMessage());
        }
    }

    private void setStatus(String message) {
        if (statusText == null || message == null) return;
        if (message.equals(lastDisplayedStatus)) return;

        lastDisplayedStatus = message;
        statusText.setText(message);
    }

    private void pollSpellStatus() {
        if (webView == null || !lastMode.equals("spell")) return;

        webView.evaluateJavascript(
                "window.__mobileSpellStatus || ''",
                result -> {
                    if (result == null || result.equals("null")) return;

                    try {
                        Object value = new JSONTokener(result).nextValue();

                        if (value instanceof String) {
                            setStatus((String) value);
                        }
                    } catch (Exception ignored) {}
                }
        );
    }

    private void setRepeat(int percent) {
        repeatPercent = percent;

        Toast.makeText(
                this,
                "반복 설정: " + percent + "%",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void restartDetection() {
        automationStarted = false;
        lastMode = "";
        lastDisplayedStatus = "";

        stopCurrentAutomation();
        setStatus("학습 화면을 다시 확인하는 중...");
    }

    private void stopCurrentAutomation() {
        if (webView == null) return;

        webView.evaluateJavascript(
                "window.__mobileMemorizeRunning=false;" +
                "window.__mobileRecallRunning=false;" +
                "window.__mobileSpellRunning=false;" +
                "clearInterval(window.__mobileRecallTimer);" +
                "clearTimeout(window.__mobileRecallAnswerTimer);" +
                "clearTimeout(window.__mobileRecallNextTimer);" +
                "clearTimeout(window.__mobileSpellTimer);",
                null
        );
    }

    private void captureWordData() {
        if (webView == null || captureInProgress) return;

        captureInProgress = true;

        String script =
                "(()=>{" +
                "if(window.__classcardCaptureRunning)return 'waiting';" +
                "let root=document.querySelector('#tab_set_all');" +
                "if(!root)return 'missing';" +
                "let rows=root.querySelectorAll(':scope > div:nth-child(2) > div');" +
                "let toggle=root.querySelector('.card-list-title > div > div:nth-child(1) > a');" +
                "if(!rows.length||!toggle)return 'missing';" +
                "let words=[];" +
                "for(let row of rows){" +
                "let en=row.querySelector('div:nth-child(4) > div:nth-child(1) > div:nth-child(1) > div > div');" +
                "if(en)words.push({en:en.innerText.trim(),ko:''});" +
                "}" +
                "if(!words.length)return 'missing';" +
                "window.__classcardCaptureRunning=true;" +
                "toggle.click();" +
                "setTimeout(()=>{" +
                "try{" +
                "let current=document.querySelectorAll('#tab_set_all > div:nth-child(2) > div');" +
                "for(let i=0;i<current.length&&i<words.length;i++){" +
                "let ko=current[i].querySelector('div:nth-child(4) > div:nth-child(2) > div:nth-child(1) > div > div');" +
                "if(ko)words[i].ko=ko.innerText.split('\\n')[0].trim();" +
                "}" +
                "let valid=words.filter(w=>w.en&&w.ko);" +
                "window.__classcardWords=valid;" +
                "window.__classcardWordsReady=valid.length>0;" +
                "if(valid.length)ClasscardBridge.saveWords(JSON.stringify(valid));" +
                "}catch(e){}finally{" +
                "let t=document.querySelector('#tab_set_all .card-list-title > div > div:nth-child(1) > a');" +
                "if(t)t.click();" +
                "window.__classcardCaptureRunning=false;" +
                "}" +
                "},1000);" +
                "return 'started';" +
                "})()";

        webView.evaluateJavascript(script, result -> {
            String value = result == null ? "" : result.replace("\"", "");

            if (value.equals("missing") || value.equals("waiting")) {
                captureInProgress = false;
            } else {
                handler.postDelayed(() -> captureInProgress = false, 5000);
            }
        });
    }

    private void detectStudyScreen() {
        if (webView == null) return;

        String script =
                "(()=>{" +
                "let card=document.querySelector('.CardItem.current.showing');" +
                "if(!card)return 'none';" +
                "let visible=el=>{if(!el)return false;let r=el.getBoundingClientRect();let s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';};" +
                "let input=[...card.querySelectorAll('input[name=\"input_answer\"]')].find(visible);" +
                "let prompt=card.querySelector('.spell-content.font-32,.spell-content,.card-quest-front');" +
                "if(input&&visible(prompt))return 'spell';" +
                "if(card.querySelector('.cc-table.middle.fill-parent-w,.cc-table.middle.fill-parent'))return 'recall';" +
                "if(card.querySelector('.btn_kown_o,.card-cover'))return 'memorize';" +
                "return 'unknown';" +
                "})()";

        webView.evaluateJavascript(script, result -> {
            if (result == null) return;

            String mode = result.replace("\"", "");

            if (mode.equals("none") || mode.equals("unknown")) {
                if (automationStarted) stopCurrentAutomation();

                automationStarted = false;
                lastMode = "";
                return;
            }

            if (mode.equals("spell") && savedWordCount == 0) {
                automationStarted = false;
                lastMode = "";

                setStatus("스펠 대기: 단어 목록을 먼저 수집해 주세요.");
                return;
            }

            if (automationStarted && mode.equals(lastMode)) return;
            if (automationStarted) stopCurrentAutomation();

            automationStarted = true;
            lastMode = mode;

            if (mode.equals("memorize") && memorizeEnabled) startMemorize();
            if (mode.equals("recall") && recallEnabled) startRecall();
            if (mode.equals("spell") && spellEnabled) startSpell();
        });
    }

    private void startMemorize() {
        setStatus("암기 자동 학습 중");

        int rounds = repeatPercent / 100;

        String script =
                "(async()=>{" +
                "window.__mobileMemorizeRunning=true;" +
                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "for(let round=1;round<=" + rounds + ";round++){" +
                "while(window.__mobileMemorizeRunning){" +
                "let end=document.querySelector('#study_end');" +
                "if(end&&getComputedStyle(end).display!=='none'&&end.getClientRects().length)break;" +
                "let card=document.querySelector('.CardItem.showing.current');" +
                "if(!card){await sleep(300);continue;}" +
                "let cover=card.querySelector('.card-cover');if(cover){cover.click();await sleep(500);}" +
                "let answer=card.querySelector('.btn_kown_o');if(!answer){await sleep(300);continue;}" +
                "await sleep(800);answer=card.querySelector('.btn_kown_o');if(answer)answer.click();" +
                "await sleep(400);let next=document.querySelector('.btnNextCard2');if(next)next.click();await sleep(600);" +
                "}" +
                "if(!window.__mobileMemorizeRunning)break;" +
                "if(round<" + rounds + "){let p=document.querySelector('#study_end > div:nth-child(2) > div:nth-child(3) > div:nth-child(3) > div:nth-child(1) > a');if(!p)break;p.click();await sleep(1500);}" +
                "}" +
                "window.__mobileMemorizeRunning=false;" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    private void startRecall() {
        setStatus("리콜 학습 자동 실행 중");

        int rounds = repeatPercent / 100;

        String script =
                "(()=>{" +
                "clearInterval(window.__mobileRecallTimer);" +
                "clearTimeout(window.__mobileRecallAnswerTimer);" +
                "clearTimeout(window.__mobileRecallNextTimer);" +

                "window.__mobileRecallRunning=true;" +
                "let lastCard=null;" +
                "let round=1;" +
                "const rounds=" + rounds + ";" +

                "const stop=()=>{" +
                "clearInterval(window.__mobileRecallTimer);" +
                "clearTimeout(window.__mobileRecallAnswerTimer);" +
                "clearTimeout(window.__mobileRecallNextTimer);" +
                "window.__mobileRecallRunning=false;" +
                "};" +

                "const run=()=>{" +
                "if(!window.__mobileRecallRunning){stop();return;}" +

                "let end=document.querySelector('#study_end');" +
                "if(end && getComputedStyle(end).display!=='none' && end.getClientRects().length>0){" +
                "if(round>=rounds){stop();return;}" +
                "let nextPercent=document.querySelector('#study_end > div:nth-child(2) > div:nth-child(3) > div:nth-child(3) > div:nth-child(1) > a');" +
                "if(!nextPercent){stop();return;}" +
                "round++;lastCard=null;nextPercent.click();return;" +
                "}" +

                "let card=document.querySelector('.CardItem.current.showing');" +
                "if(!card || card===lastCard)return;" +

                "let choices=[...card.querySelectorAll('.cc-table.middle.fill-parent-w,.cc-table.middle.fill-parent')];" +
                "let answer=choices.find(item=>item.classList.contains('answer'));" +
                "if(!answer)return;" +

                "let handler=window.jQuery?._data(answer,'events')?.click?.[0]?.handler;" +
                "if(!handler)return;" +

                "lastCard=card;" +

                "window.__mobileRecallAnswerTimer=setTimeout(()=>{" +
                "if(!window.__mobileRecallRunning)return;" +
                "if(document.querySelector('.CardItem.current.showing')!==card)return;" +

                "let event=window.jQuery.Event('click');" +
                "event.target=answer;event.currentTarget=answer;event.which=1;" +
                "Object.defineProperty(event,'originalEvent',{" +
                "value:{type:'click',isTrusted:true,target:answer,currentTarget:answer,which:1}," +
                "configurable:true" +
                "});" +

                "handler.call(answer,event);" +

                "window.__mobileRecallNextTimer=setTimeout(()=>{" +
                "if(!window.__mobileRecallRunning)return;" +
                "let next=document.querySelector('.btn-short-change-next');" +
                "if(next && window.jQuery)window.jQuery(next).trigger('click');" +
                "},800);" +
                "},1200);" +
                "};" +

                "run();" +
                "window.__mobileRecallTimer=setInterval(run,200);" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    private void startSpell() {
        setStatus("저장된 단어 " + savedWordCount + "개 복구 중...");

        String restore =
                "window.__classcardWords=JSON.parse(" +
                JSONObject.quote(savedWordsJson) +
                ");window.__classcardWordsReady=true;";

        webView.evaluateJavascript(restore, result -> runSpellScript());
    }

    private void runSpellScript() {
        String script =
                "(async()=>{" +
                "if(window.__mobileSpellRunning)return;" +
                "window.__mobileSpellRunning=true;" +
                "window.__mobileSpellStatus='스펠 자동화 시작';" +

                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();" +
                "const visible=el=>{if(!el)return false;let r=el.getBoundingClientRect();let s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';};" +
                "const card=()=>document.querySelector('.CardItem.current.showing');" +
                "const input=c=>c?[...c.querySelectorAll('.spell-input:not(.hidden) input[name=\"input_answer\"],input[name=\"input_answer\"]')].find(visible):null;" +
                "const prompt=c=>{let e=c&&c.querySelector('.spell-content.font-32,.spell-content,.card-quest-front');return visible(e)?e.innerText.trim():'';};" +
                "const words=window.__classcardWords||[];" +

                "const popup=()=>{" +
                "let b=[...document.querySelectorAll('button,a,[role=\"button\"]')].find(e=>visible(e)&&e.textContent.trim()==='알겠습니다!');" +
                "if(b)b.click();" +
                "};" +

                "const key=c=>((c&&c.dataset.idx)||'')+'|'+norm(prompt(c));" +

                "const submit=i=>{" +
                "let b=[...document.querySelectorAll('.btn-confirm,button[type=\"submit\"],#wrapper-learn .btn-primary')].find(visible);" +
                "if(b){b.click();return true;}" +
                "if(window.jQuery){" +
                "window.jQuery(i).trigger(window.jQuery.Event('keydown',{key:'Enter',keyCode:13,which:13}));" +
                "window.jQuery(i).trigger(window.jQuery.Event('keyup',{key:'Enter',keyCode:13,which:13}));" +
                "return true;" +
                "}" +
                "i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));" +
                "i.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));" +
                "return true;" +
                "};" +

                "let rounds=" + (repeatPercent / 100) + ";" +

                "for(let round=1;round<=rounds&&window.__mobileSpellRunning;round++){" +
                "let lastKey='';" +
                "while(window.__mobileSpellRunning){" +

                "popup();" +
                "let c=card();" +

                "if(!c){" +
                "let end=document.querySelector('#study_end');" +
                "if(end&&visible(end))break;" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "let p=prompt(c);" +
                "let i=input(c);" +
                "if(!p||!i){await sleep(250);continue;}" +

                "let currentKey=key(c);" +
                "if(currentKey===lastKey){await sleep(200);continue;}" +

                "let w=words.find(x=>norm(x.ko)===norm(p)||norm(x.en)===norm(p));" +
                "if(!w){" +
                "window.__mobileSpellStatus='정답 데이터 없음: '+p;" +
                "await sleep(700);" +
                "continue;" +
                "}" +

                "let answer=norm(w.ko)===norm(p)?w.en:w.ko;" +
                "lastKey=currentKey;" +
                "window.__mobileSpellStatus='입력 준비 중: '+p;" +

                "i.focus();" +
                "i.click();" +
                "try{ClasscardBridge.sendSpellAnswer(answer);}catch(e){}" +

                "let filled=false;" +
                "for(let t=0;t<30;t++){" +
                "await sleep(200);" +
                "if(!window.__mobileSpellRunning)break;" +
                "if(i.value===answer){filled=true;break;}" +
                "}" +

                "if(!filled){" +
                "lastKey='';" +
                "window.__mobileSpellStatus='입력 확인 실패';" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='입력 확인 완료';" +
                "await sleep(300);" +

                "submit(i);" +
                "window.__mobileSpellStatus='정답 제출 중';" +

                "let changed=false;" +
                "for(let t=0;t<40;t++){" +
                "await sleep(150);" +
                "popup();" +
                "let now=card();" +
                "if(!now||key(now)!==currentKey){" +
                "changed=true;" +
                "break;" +
                "}" +
                "}" +

                "if(!changed){" +
                "let n=[...document.querySelectorAll('.btn-short-change-next')].find(visible);" +
                "if(n){" +
                "n.click();" +
                "for(let t=0;t<40;t++){" +
                "await sleep(150);" +
                "popup();" +
                "let now=card();" +
                "if(!now||key(now)!==currentKey){" +
                "changed=true;" +
                "break;" +
                "}" +
                "}" +
                "}" +
                "}" +

                "if(!changed){" +
                "lastKey='';" +
                "window.__mobileSpellStatus='다음 문제 이동 실패';" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='문제 처리 완료';" +
                "await sleep(250);" +
                "}" +
                "}" +

                "window.__mobileSpellStatus='스펠 반복 완료';" +
                "window.__mobileSpellRunning=false;" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(detector);
        stopCurrentAutomation();

        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("ClasscardBridge");
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
