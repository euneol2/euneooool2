
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
    private boolean wordDataLoaded = false;

    private TextView statusText;

    private final Runnable detector = new Runnable() {
        @Override
        public void run() {
            if (webView != null) {
                captureWordData();
                detectStudyScreen();
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
        stopCurrentAutomation();
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

        if (wordDataLoaded || webView == null) return;

        String script =
                "(()=>{" +
                "let root=document.querySelector('#tab_set_all');" +
                "if(!root)return false;" +

                "let rows=root.querySelectorAll(':scope > div:nth-child(2) > div');" +
                "if(!rows.length)return false;" +

                "let toggle=root.querySelector('.card-list-title > div > div:nth-child(1) > a');" +
                "if(!toggle)return false;" +

                "let words=[];" +
                "for(let i=1;i<=rows.length;i++){" +
                "let row=rows[i-1];" +
                "let en=row.querySelector('div:nth-child(4) > div:nth-child(1) > div:nth-child(1) > div > div');" +
                "if(!en)continue;" +
                "words.push({en:en.innerText.trim(),ko:''});" +
                "}" +

                "toggle.click();" +

                "setTimeout(()=>{" +
                "let current=root.querySelectorAll(':scope > div:nth-child(2) > div');" +
                "for(let i=0;i<current.length && i<words.length;i++){" +
                "let ko=current[i].querySelector('div:nth-child(4) > div:nth-child(2) > div:nth-child(1) > div > div');" +
                "if(ko)words[i].ko=ko.innerText.split('\\n')[0].trim();" +
                "}" +
                "window.__classcardWords=words.filter(w=>w.en&&w.ko);" +
                "toggle.click();" +
                "},500);" +

                "return true;" +
                "})()";

        webView.evaluateJavascript(script, result -> {
            if ("true".equals(result)) {
                wordDataLoaded = true;
            }
        });
    }

    private void detectStudyScreen() {

        String script =
                "(()=>{" +
                "let card=document.querySelector('.CardItem.current.showing, .CardItem.showing.current');" +
                "if(!card)return 'none';" +

                "let visible=el=>!!el && el.getClientRects().length>0 && getComputedStyle(el).display!=='none';" +

                "let input=card.querySelector('input[name=\"input_answer\"],input.input-answer');" +
                "let prompt=card.querySelector('.spell-content.font-32,.spell-content,.card-quest-front');" +

                "if(input && visible(input) && prompt && visible(prompt))return 'spell';" +

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
                    statusText.setText("암기 학습 감지 · 비활성화 상태");
                }

            } else if (mode.equals("recall")) {

                if (recallEnabled) {
                    startRecall();
                } else {
                    statusText.setText("리콜 학습 감지 · 비활성화 상태");
                }

            } else if (mode.equals("spell")) {

                if (spellEnabled) {
                    startSpell();
                } else {
                    statusText.setText("스펠 학습 감지 · 비활성화 상태");
                }
            }
        });
    }

    private void startMemorize() {

        statusText.setText("암기 학습 자동 실행 중");

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

        statusText.setText("리콜 학습 자동 실행 중");

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

        statusText.setText("스펠 학습 자동 실행 중");

        int rounds = repeatPercent / 100;

        String script =
                "(async()=>{" +

                "if(window.__mobileSpellRunning)return;" +
                "window.__mobileSpellRunning=true;" +

                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +
                "const rounds=" + rounds + ";" +

                "const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLocaleLowerCase();" +

                "const stop=()=>{" +
                "window.__mobileSpellRunning=false;" +
                "clearTimeout(window.__mobileSpellTimer);" +
                "};" +

                "try{" +

                "for(let round=1;round<=rounds;round++){" +

                "while(window.__mobileSpellRunning){" +

                "let end=document.querySelector('#study_end');" +
                "if(end && getComputedStyle(end).display!=='none' && end.getClientRects().length>0)break;" +

                "let card=document.querySelector('.CardItem.current.showing');" +
                "if(!card){await sleep(300);continue;}" +

                "let promptEl=card.querySelector('.spell-content.font-32,.spell-content,.card-quest-front');" +
                "let input=card.querySelector('.spell-input:not(.hidden) input[name=\"input_answer\"],input[name=\"input_answer\"],input.input-answer');" +

                "if(!promptEl || !input){await sleep(300);continue;}" +

                "let prompt=norm(promptEl.innerText);" +
                "let words=window.__classcardWords||[];" +
                "let found=words.find(w=>norm(w.ko)===prompt || norm(w.en)===prompt);" +

                "if(!found){" +
                "console.log('스펠 정답을 찾지 못했습니다:',prompt);" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "let answer=norm(found.ko)===prompt?found.en:found.ko;" +

                "await sleep(1200);" +
                "if(!window.__mobileSpellRunning)break;" +

                "input.focus();" +
                "input.value=answer;" +
                "input.dispatchEvent(new Event('input',{bubbles:true}));" +
                "input.dispatchEvent(new Event('change',{bubbles:true}));" +

                "await sleep(300);" +

                "let submit=card.querySelector('.btn-confirm') || document.querySelector('#wrapper-learn .btn-confirm') || document.querySelector('#wrapper-learn button[type=\"submit\"]');" +

                "if(submit){" +
                "submit.click();" +
                "}else{" +
                "input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));" +
                "input.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));" +
                "}" +

                "await sleep(700);" +

                "let feedback=document.querySelector('.modal-content .btn-ok');" +
                "if(feedback)feedback.click();" +

                "await sleep(300);" +

                "let next=document.querySelector('.btn-short-change-next') || document.querySelector('#wrapper-learn .btn-next');" +
                "if(next)next.click();" +

                "await sleep(700);" +
                "}" +

                "if(!window.__mobileSpellRunning)break;" +

                "if(round<rounds){" +
                "let nextPercent=document.querySelector('#study_end > div:nth-child(2) > div:nth-child(3) > div:nth-child(3) > div:nth-child(1) > a');" +
                "if(!nextPercent)break;" +
                "nextPercent.click();" +
                "await sleep(1500);" +
                "}" +
                "}" +

                "}finally{" +
                "stop();" +
                "}" +
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

