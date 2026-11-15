
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
                "})();",
                null
        );
    }

    private void detectStudyScreen() {

        String script =
                "(function() {" +
                "var card = document.querySelector('.CardItem.current.showing, .CardItem.showing.current');" +
                "if (!card) return 'none';" +

                "var visible = function(el) {" +
                "return !!el && el.getClientRects().length > 0 && getComputedStyle(el).display !== 'none';" +
                "};" +

                "var input = card.querySelector('.spell-input:not(.hidden) input[name=\"input_answer\"], input[name=\"input_answer\"]');" +
                "var prompt = card.querySelector('.spell-content.font-32, .spell-content, .card-quest-front');" +

                "if ((input && visible(input)) || (prompt && visible(prompt) && card.querySelector('input[type=\"text\"]'))) return 'spell';" +

                "if (card.querySelector('.cc-table.middle.fill-parent-w, .cc-table.middle.fill-parent')) return 'recall';" +

                "if (card.querySelector('.btn_kown_o, .card-cover')) return 'memorize';" +

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
                    statusText.setText("스펠 학습 감지 · 정답 데이터 연결 필요");
                } else {
                    statusText.setText("스펠 학습 감지 · 비활성화 상태");
                }
            }
        });
    }

    private void startMemorize() {

        statusText.setText("암기 학습 자동 실행 중");

        String script =
                "(async function() {" +
                "if (window.__mobileMemorizeRunning) return;" +
                "window.__mobileMemorizeRunning = true;" +

                "const sleep = ms => new Promise(r => setTimeout(r, ms));" +

                "try {" +
                "while (window.__mobileMemorizeRunning) {" +

                "var end = document.querySelector('#study_end');" +
                "if (end && getComputedStyle(end).display !== 'none' && end.getClientRects().length > 0) break;" +

                "var card = document.querySelector('.CardItem.showing.current');" +
                "if (!card) break;" +

                "var cover = card.querySelector('.card-cover');" +
                "if (cover) {" +
                "cover.click();" +
                "await sleep(500);" +
                "}" +

                "var answer = card.querySelector('.btn_kown_o');" +
                "if (!answer) {" +
                "await sleep(300);" +
                "continue;" +
                "}" +

                "await sleep(1200);" +

                "if (!window.__mobileMemorizeRunning) break;" +
                "answer = card.querySelector('.btn_kown_o');" +
                "if (!answer) continue;" +

                "answer.click();" +
                "await sleep(400);" +

                "var next = document.querySelector('.btnNextCard2');" +
                "if (!next) break;" +

                "next.click();" +
                "await sleep(600);" +
                "}" +
                "} finally {" +
                "window.__mobileMemorizeRunning = false;" +
                "}" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    private void startRecall() {

        statusText.setText("리콜 학습 자동 실행 중");

        String script =
                "(function() {" +

                "if (window.__mobileRecallRunning) return;" +
                "window.__mobileRecallRunning = true;" +

                "var lastCard = null;" +

                "var timer = setInterval(function() {" +

                "if (!window.__mobileRecallRunning) {" +
                "clearInterval(timer);" +
                "return;" +
                "}" +

                "var end = document.querySelector('#study_end');" +
                "if (end && getComputedStyle(end).display !== 'none' && end.getClientRects().length > 0) {" +
                "clearInterval(timer);" +
                "window.__mobileRecallRunning = false;" +
                "return;" +
                "}" +

                "var card = document.querySelector('.CardItem.current.showing');" +
                "if (!card || card === lastCard) return;" +

                "var choices = Array.from(card.querySelectorAll('.cc-table.middle.fill-parent-w, .cc-table.middle.fill-parent'));" +
                "var answer = choices.find(x => x.classList.contains('answer'));" +

                "if (!answer) return;" +

                "lastCard = card;" +

                "setTimeout(function() {" +

                "if (!window.__mobileRecallRunning) return;" +
                "if (document.querySelector('.CardItem.current.showing') !== card) return;" +

                "var handler = window.jQuery?._data(answer, 'events')?.click?.[0]?.handler;" +

                "if (handler) {" +
                "var event = window.jQuery.Event('click');" +
                "event.target = answer;" +
                "event.currentTarget = answer;" +
                "event.which = 1;" +
                "handler.call(answer, event);" +
                "} else {" +
                "answer.click();" +
                "}" +

                "setTimeout(function() {" +
                "var next = document.querySelector('.btn-short-change-next');" +
                "if (next && window.__mobileRecallRunning) next.click();" +
                "}, 700);" +

                "}, 1200);" +

                "}, 250);" +

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
