
package com.example.classcardauto;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;
    private Handler handler = new Handler();

    private boolean memorizeEnabled = true;
    private boolean recallEnabled = true;
    private boolean spellEnabled = true;

    private int repeatPercent = 300;

    private String lastMode = "";
    private boolean automationStarted = false;

    private TextView statusText;
    private String lastDisplayedStatus = "";

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
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        if (savedInstanceState != null &&
                webView.restoreState(savedInstanceState) != null) {
        } else {
            webView.loadUrl("https://www.classcard.net/login");
        }

        handler.postDelayed(detector, 1500);
    }

    private void setStatus(String message) {
        if (statusText == null) return;

        if (message == null || message.trim().isEmpty()) return;

        if (message.equals(lastDisplayedStatus)) return;

        lastDisplayedStatus = message;
        statusText.setText(message);
    }

    private void pollSpellStatus() {
        if (webView == null || !lastMode.equals("spell")) return;

        webView.evaluateJavascript(
                "(()=>window.__mobileSpellStatus || window.__classcardCaptureStatus || '')()",
                result -> {
                    if (result == null || result.equals("null")) return;

                    String message = result;

                    if (message.length() >= 2 &&
                            message.startsWith("\"") &&
                            message.endsWith("\"")) {
                        message = message.substring(1, message.length() - 1);
                    }

                    message = message
                            .replace("\\n", " ")
                            .replace("\\\"", "\"")
                            .trim();

                    if (!message.isEmpty()) {
                        setStatus(message);
                    }
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
                "(function() {" +
                "window.__mobileMemorizeRunning = false;" +
                "window.__mobileRecallRunning = false;" +
                "window.__mobileSpellRunning = false;" +
                "clearInterval(window.__mobileRecallTimer);" +
                "clearTimeout(window.__mobileRecallAnswerTimer);" +
                "clearTimeout(window.__mobileRecallNextTimer);" +
                "clearTimeout(window.__mobileSpellTimer);" +
                "})();",
                null
        );
    }

    private void captureWordData() {

        if (webView == null) return;

        String script =
                "(()=>{" +

                "if(window.__classcardWordsReady && window.__classcardWords?.length)return 'ready';" +
                "if(window.__classcardCaptureRunning)return 'waiting';" +

                "let root=document.querySelector('#tab_set_all');" +
                "if(!root)return 'missing';" +

                "let rows=root.querySelectorAll(':scope > div:nth-child(2) > div');" +
                "if(!rows.length)return 'missing';" +

                "let toggle=root.querySelector('.card-list-title > div > div:nth-child(1) > a');" +
                "if(!toggle)return 'missing';" +

                "let words=[];" +

                "for(let i=0;i<rows.length;i++){" +
                "let row=rows[i];" +
                "let en=row.querySelector('div:nth-child(4) > div:nth-child(1) > div:nth-child(1) > div > div');" +
                "if(!en)continue;" +
                "words.push({en:en.innerText.trim(),ko:''});" +
                "}" +

                "if(!words.length)return 'missing';" +

                "window.__classcardCaptureStatus='단어 데이터 수집 중...';" +
                "window.__classcardCaptureRunning=true;" +
                "toggle.click();" +

                "setTimeout(()=>{" +

                "try{" +
                "let current=root.querySelectorAll(':scope > div:nth-child(2) > div');" +

                "for(let i=0;i<current.length && i<words.length;i++){" +
                "let ko=current[i].querySelector('div:nth-child(4) > div:nth-child(2) > div:nth-child(1) > div > div');" +
                "if(ko)words[i].ko=ko.innerText.split('\\n')[0].trim();" +
                "}" +

                "window.__classcardWords=words.filter(w=>w.en&&w.ko);" +

                "}catch(e){" +
                "window.__classcardCaptureStatus='단어 데이터 수집 오류: '+e.message;" +
                "console.error('단어 데이터 수집 오류:',e);" +
                "}finally{" +
                "toggle.click();" +
                "window.__classcardWordsReady=!!(window.__classcardWords && window.__classcardWords.length);" +
                "window.__classcardCaptureRunning=false;" +
                "if(window.__classcardWordsReady){" +
                "window.__classcardCaptureStatus='단어 데이터 수집 완료: '+window.__classcardWords.length+'개';" +
                "}else{" +
                "window.__classcardCaptureStatus='단어 데이터 수집 실패: 한국어 뜻을 가져오지 못했습니다.';" +
                "}" +
                "}" +

                "},700);" +

                "return 'started';" +
                "})()";

        webView.evaluateJavascript(script, null);
    }

    private void detectStudyScreen() {

        String script =
                "(()=>{" +

                "let card=document.querySelector('.CardItem.current.showing');" +
                "if(!card)return 'none';" +

                "let visible=el=>{" +
                "if(!el)return false;" +
                "let r=el.getBoundingClientRect();" +
                "let s=getComputedStyle(el);" +
                "return r.width>0 && r.height>0 && " +
                "s.display!=='none' && s.visibility!=='hidden' && s.opacity!=='0';" +
                "};" +

                "let inputs=[...card.querySelectorAll('input[name=\"input_answer\"]')];" +
                "let input=inputs.find(visible);" +

                "let prompt=card.querySelector('.spell-content.font-32,.spell-content,.card-quest-front');" +

                "if(input && !input.disabled && visible(prompt))return 'spell';" +

                "if(card.querySelector('.cc-table.middle.fill-parent-w,.cc-table.middle.fill-parent'))return 'recall';" +

                "if(card.querySelector('.btn_kown_o,.card-cover'))return 'memorize';" +

                "return 'unknown';" +

                "})()";

        webView.evaluateJavascript(script, result -> {

            if (result == null) return;

            String mode = result.replace("\"", "");

            if (mode.equals("none") || mode.equals("unknown")) {

                if (automationStarted) {
                    stopCurrentAutomation();
                }

                automationStarted = false;
                lastMode = "";
                return;
            }

            if (automationStarted && mode.equals(lastMode)) {
                return;
            }

            if (automationStarted) {
                stopCurrentAutomation();
            }

            automationStarted = true;
            lastMode = mode;

            if (mode.equals("memorize")) {

                if (memorizeEnabled) {
                    startMemorize();
                } else {
                    setStatus("암기 학습 감지 · 비활성화 상태");
                }

            } else if (mode.equals("recall")) {

                if (recallEnabled) {
                    startRecall();
                } else {
                    setStatus("리콜 학습 감지 · 비활성화 상태");
                }

            } else if (mode.equals("spell")) {

                if (spellEnabled) {
                    startSpell();
                } else {
                    setStatus("스펠 학습 감지 · 비활성화 상태");
                }
            }
        });
    }

    private void startMemorize() {

        setStatus("암기 학습 자동 실행 중");

        int rounds = repeatPercent / 100;

        String script =
                "(async function() {" +

                "if(window.__mobileMemorizeRunning)return;" +
                "window.__mobileMemorizeRunning=true;" +

                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "const rounds=" + rounds + ";" +

                "try{" +

                "for(let round=1;round<=rounds;round++){" +

                "while(window.__mobileMemorizeRunning){" +

                "let end=document.querySelector('#study_end');" +
                "if(end && getComputedStyle(end).display!=='none' && end.getClientRects().length>0)break;" +

                "let card=document.querySelector('.CardItem.showing.current');" +

                "if(!card){" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "let cover=card.querySelector('.card-cover');" +

                "if(cover){" +
                "cover.click();" +
                "await sleep(500);" +
                "}" +

                "let answer=card.querySelector('.btn_kown_o');" +

                "if(!answer){" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "await sleep(1000);" +

                "if(!window.__mobileMemorizeRunning)break;" +

                "answer=card.querySelector('.btn_kown_o');" +
                "if(!answer)continue;" +

                "answer.click();" +
                "await sleep(400);" +

                "let next=document.querySelector('.btnNextCard2');" +
                "if(next)next.click();" +

                "await sleep(600);" +
                "}" +

                "if(!window.__mobileMemorizeRunning)break;" +

                "if(round<rounds){" +

                "let nextPercent=document.querySelector('#study_end > div:nth-child(2) > div:nth-child(3) > div:nth-child(3) > div:nth-child(1) > a');" +

                "if(!nextPercent)break;" +

                "nextPercent.click();" +
                "await sleep(1500);" +

                "}" +
                "}" +

                "}finally{" +
                "window.__mobileMemorizeRunning=false;" +
                "}" +

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

                "round++;" +
                "lastCard=null;" +
                "nextPercent.click();" +

                "return;" +
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
                "event.target=answer;" +
                "event.currentTarget=answer;" +
                "event.which=1;" +

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

        setStatus("스펠 자동화 준비 중...");

        int rounds = repeatPercent / 100;

        String script =
                "(async()=>{" +

                "if(window.__mobileSpellRunning)return;" +
                "window.__mobileSpellRunning=true;" +
                "window.__mobileSpellStatus='스펠 자동화 시작';" +

                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "const rounds=" + rounds + ";" +

                "const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLocaleLowerCase();" +

                "const visible=el=>{" +
                "if(!el)return false;" +
                "let r=el.getBoundingClientRect();" +
                "let s=getComputedStyle(el);" +
                "return r.width>0 && r.height>0 && " +
                "s.display!=='none' && s.visibility!=='hidden' && s.opacity!=='0';" +
                "};" +

                "const stop=()=>{" +
                "window.__mobileSpellRunning=false;" +
                "clearTimeout(window.__mobileSpellTimer);" +
                "};" +

                "const isComplete=()=>{" +
                "let end=document.querySelector('#study_end');" +
                "return visible(end);" +
                "};" +

                "const getCard=()=>document.querySelector('.CardItem.current.showing');" +

                "const getInput=card=>{" +
                "if(!card)return null;" +
                "let inputs=[...card.querySelectorAll('input[name=\"input_answer\"]')];" +
                "return inputs.find(el=>visible(el) && !el.disabled && !el.readOnly && el.type!=='hidden')||null;" +
                "};" +

                "const getPrompt=card=>{" +
                "if(!card)return '';" +
                "let selectors=['.spell-content.font-32','.spell-content','.card-quest-front'];" +
                "for(let selector of selectors){" +
                "let el=card.querySelector(selector);" +
                "if(visible(el) && el.innerText.trim())return el.innerText.trim();" +
                "}" +
                "return '';" +
                "};" +

                "const getSubmit=()=>{" +
                "let buttons=[...document.querySelectorAll('.btn-confirm')];" +
                "return buttons.find(button=>visible(button) && !button.disabled && !button.closest('#study_end'))||null;" +
                "};" +

                "const getNext=()=>{" +
                "let buttons=[...document.querySelectorAll('.btn-short-change-next')];" +
                "return buttons.find(button=>visible(button) && !button.disabled && !button.closest('#study_end'))||null;" +
                "};" +

                "const waitForCardChange=async(card,prompt)=>{" +
                "for(let i=0;i<30;i++){" +
                "if(!window.__mobileSpellRunning)return 'stopped';" +
                "if(isComplete())return 'complete';" +
                "let current=getCard();" +
                "if(current && (current!==card || norm(getPrompt(current))!==norm(prompt)))return 'changed';" +
                "await sleep(150);" +
                "}" +
                "return 'waiting';" +
                "};" +

                "try{" +

                "for(let round=1;round<=rounds;round++){" +

                "let processed=0;" +

                "while(window.__mobileSpellRunning){" +

                "if(isComplete()){" +
                "window.__mobileSpellStatus='스펠 학습 완료';" +
                "break;" +
                "}" +

                "let card=getCard();" +

                "if(!card){" +
                "window.__mobileSpellStatus='현재 문제 카드를 찾는 중...';" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='문제 내용 확인 중...';" +
                "let prompt=getPrompt(card);" +

                "if(!prompt){" +
                "window.__mobileSpellStatus='문제 내용 읽기 실패: 문제 선택자를 확인해야 합니다.';" +
                "await sleep(700);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='입력창 찾는 중...';" +
                "let input=getInput(card);" +

                "if(!input){" +
                "let count=card.querySelectorAll('input[name=\"input_answer\"]').length;" +
                "window.__mobileSpellStatus='입력창 찾기 실패: 현재 카드 입력창 '+count+'개, 표시되는 입력창 없음';" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='단어 데이터 확인 중...';" +
                "let words=window.__classcardWords||[];" +

                "if(!window.__classcardWordsReady || !words.length){" +
                "window.__mobileSpellStatus=window.__classcardCaptureStatus||'단어 데이터 준비 대기 중...';" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='정답 검색 중...';" +
                "let found=words.find(w=>norm(w.ko)===norm(prompt) || norm(w.en)===norm(prompt));" +

                "if(!found){" +
                "window.__mobileSpellStatus='정답 찾기 실패: '+prompt;" +
                "await sleep(1500);" +
                "continue;" +
                "}" +

                "let answer=norm(found.ko)===norm(prompt)?found.en:found.ko;" +

                "if(!answer){" +
                "window.__mobileSpellStatus='정답 데이터가 비어 있습니다.';" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='정답 확인 완료 · 입력 준비 중...';" +
                "await sleep(400);" +

                "if(!window.__mobileSpellRunning || isComplete())break;" +
                "if(getCard()!==card)continue;" +

                "input=getInput(card);" +

                "if(!input){" +
                "window.__mobileSpellStatus='입력 직전 실패: 입력창이 사라졌습니다.';" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='입력창 포커스 설정 중...';" +
                "input.scrollIntoView({block:'center'});" +
                "input.click();" +
                "input.focus();" +

                "if(document.activeElement!==input){" +
                "window.__mobileSpellStatus='입력 실패: 입력창 포커스를 얻지 못했습니다.';" +
                "await sleep(700);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='정답 입력 중...';" +
                "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                "setter.call(input,String(answer));" +

                "input.dispatchEvent(new InputEvent('input',{" +
                "bubbles:true,inputType:'insertText',data:String(answer)" +
                "}));" +

                "input.dispatchEvent(new Event('change',{bubbles:true}));" +

                "await sleep(400);" +

                "if(!visible(input) || input.value!==String(answer)){" +
                "window.__mobileSpellStatus='입력 검증 실패: 입력창 값이 유지되지 않았습니다.';" +
                "await sleep(700);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='정답 입력 확인 완료 · 제출 준비 중...';" +
                "let submit=getSubmit();" +

                "if(!submit){" +
                "window.__mobileSpellStatus='제출 실패: 확인 버튼을 찾지 못했습니다.';" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "await sleep(200);" +

                "if(!window.__mobileSpellRunning)break;" +

                "if(getCard()!==card || !visible(input) || input.value!==String(answer)){" +
                "window.__mobileSpellStatus='제출 취소: 제출 전 입력 상태가 변경됐습니다.';" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='정답 제출 중...';" +
                "submit.click();" +

                "let result=await waitForCardChange(card,prompt);" +

                "if(result==='stopped')break;" +
                "if(result==='complete'){" +
                "window.__mobileSpellStatus='스펠 학습 완료';" +
                "break;" +
                "}" +

                "if(result==='waiting'){" +
                "window.__mobileSpellStatus='제출 후 결과 대기 중...';" +
                "let next=getNext();" +

                "if(next){" +
                "window.__mobileSpellStatus='오답 버튼 확인 · 다음 문제로 이동 중...';" +
                "next.click();" +
                "result=await waitForCardChange(card,prompt);" +
                "if(result==='stopped')break;" +
                "if(result==='complete'){" +
                "window.__mobileSpellStatus='스펠 학습 완료';" +
                "break;" +
                "}" +
                "if(result==='waiting'){" +
                "window.__mobileSpellStatus='이동 실패: 오답 버튼을 눌렀지만 다음 문제로 바뀌지 않았습니다.';" +
                "await sleep(1500);" +
                "break;" +
                "}" +
                "}else{" +
                "window.__mobileSpellStatus='이동 실패: 제출 후 카드와 다음 버튼이 모두 확인되지 않았습니다.';" +
                "await sleep(1500);" +
                "break;" +
                "}" +
                "}" +

                "processed++;" +
                "window.__mobileSpellStatus='문제 처리 완료: '+processed+'개';" +

                "if(processed>500){" +
                "window.__mobileSpellStatus='중단: 처리 제한에 도달했습니다.';" +
                "break;" +
                "}" +

                "await sleep(300);" +
                "}" +

                "if(!window.__mobileSpellRunning)break;" +

                "if(round<rounds && isComplete()){" +
                "let nextPercent=document.querySelector('#study_end > div:nth-child(2) > div:nth-child(3) > div:nth-child(3) > div:nth-child(1) > a');" +
                "if(!nextPercent){" +
                "window.__mobileSpellStatus='다음 반복 버튼을 찾지 못했습니다.';" +
                "break;" +
                "}" +
                "window.__mobileSpellStatus='다음 반복 시작 중...';" +
                "nextPercent.click();" +
                "await sleep(1500);" +
                "}" +
                "}" +

                "}catch(error){" +
                "window.__mobileSpellStatus='스펠 오류: '+(error&&error.message?error.message:String(error));" +
                "console.error('스펠 자동화 오류:',error);" +
                "}finally{" +
                "stop();" +
                "}" +

                "})();";

        webView.evaluateJavascript(script, result -> {
            if (result == null) {
                setStatus("스펠 스크립트 실행 결과를 받지 못했습니다.");
            }
        });
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
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(detector);

        stopCurrentAutomation();

        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
