
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
                "console.error('단어 데이터 수집 오류:',e);" +
                "}finally{" +
                "toggle.click();" +
                "window.__classcardWordsReady=!!(window.__classcardWords && window.__classcardWords.length);" +
                "window.__classcardCaptureRunning=false;" +
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

                "let input=inputs.find(el=>{" +
                "if(!visible(el))return false;" +
                "if(el.disabled || el.readOnly || el.type==='hidden')return false;" +
                "return true;" +
                "});" +

                "return input||null;" +
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

                "for(let button of buttons){" +

                "if(!visible(button))continue;" +
                "if(button.disabled)continue;" +
                "if(button.closest('#study_end'))continue;" +

                "return button;" +
                "}" +

                "return null;" +
                "};" +

                "const getNext=()=>{" +

                "let buttons=[...document.querySelectorAll('.btn-short-change-next')];" +

                "for(let button of buttons){" +

                "if(!visible(button))continue;" +
                "if(button.disabled)continue;" +
                "if(button.closest('#study_end'))continue;" +

                "return button;" +
                "}" +

                "return null;" +
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

                "if(isComplete())break;" +

                "let card=getCard();" +

                "if(!card){" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "let prompt=getPrompt(card);" +

                "if(!prompt){" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "let input=getInput(card);" +

                "if(!input){" +
                "console.log('현재 카드의 보이는 입력창을 찾지 못했습니다.');" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "let words=window.__classcardWords||[];" +

                "if(!window.__classcardWordsReady || !words.length){" +
                "console.log('단어 데이터 준비를 기다리는 중입니다.');" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "let found=words.find(w=>norm(w.ko)===norm(prompt) || norm(w.en)===norm(prompt));" +

                "if(!found){" +
                "console.log('스펠 정답을 찾지 못했습니다:',prompt);" +
                "break;" +
                "}" +

                "let answer=norm(found.ko)===norm(prompt)?found.en:found.ko;" +

                "if(!answer)break;" +

                "await sleep(700);" +

                "if(!window.__mobileSpellRunning || isComplete())break;" +
                "if(getCard()!==card)continue;" +

                "input=getInput(card);" +

                "if(!input){" +
                "console.log('정답 입력 직전에 보이는 입력창이 사라졌습니다.');" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "input.scrollIntoView({block:'center'});" +
                "input.click();" +
                "input.focus();" +

                "if(document.activeElement!==input){" +
                "console.log('입력창 포커스 실패. 제출하지 않습니다.');" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "let setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;" +
                "setter.call(input,String(answer));" +

                "input.dispatchEvent(new InputEvent('input',{" +
                "bubbles:true,inputType:'insertText',data:String(answer)" +
                "}));" +

                "input.dispatchEvent(new Event('change',{bubbles:true}));" +

                "await sleep(300);" +

                "if(!visible(input) || document.activeElement!==input || input.value!==String(answer)){" +
                "console.log('정답 입력 검증 실패. 확인 버튼을 누르지 않습니다.');" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "let submit=getSubmit();" +

                "if(!submit){" +
                "console.log('확인 버튼을 찾지 못했습니다.');" +
                "break;" +
                "}" +

                "await sleep(200);" +

                "if(!window.__mobileSpellRunning)break;" +

                "if(getCard()!==card || !visible(input) || input.value!==String(answer)){" +
                "console.log('제출 직전 입력 상태가 변경되어 제출을 취소했습니다.');" +
                "continue;" +
                "}" +

                "submit.click();" +

                "let result=await waitForCardChange(card,prompt);" +

                "if(result==='stopped')break;" +
                "if(result==='complete')break;" +

                "if(result==='waiting'){" +

                "let next=getNext();" +

                "if(next){" +

                "next.click();" +

                "result=await waitForCardChange(card,prompt);" +

                "if(result==='stopped')break;" +
                "if(result==='complete')break;" +

                "if(result==='waiting'){" +
                "console.log('오답 처리 후에도 다음 카드로 이동하지 않았습니다.');" +
                "break;" +
                "}" +

                "}else{" +

                "console.log('제출 후 카드 이동 또는 오답 버튼을 확인하지 못했습니다.');" +
                "break;" +

                "}" +
                "}" +

                "processed++;" +

                "if(processed>500)break;" +

                "await sleep(300);" +
                "}" +

                "if(!window.__mobileSpellRunning)break;" +

                "if(round<rounds && isComplete()){" +

                "let nextPercent=document.querySelector('#study_end > div:nth-child(2) > div:nth-child(3) > div:nth-child(3) > div:nth-child(1) > a');" +

                "if(!nextPercent)break;" +

                "nextPercent.click();" +
                "await sleep(1500);" +

                "}" +
                "}" +

                "}catch(error){" +

                "console.error('스펠 자동화 오류:',error);" +

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
