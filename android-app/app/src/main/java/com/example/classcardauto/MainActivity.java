Java
package com.example.classcardauto;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private final Handler handler = new Handler();

    private final boolean memorizeEnabled = true;
    private final boolean recallEnabled = true;
    private final boolean spellEnabled = true;
    private final int repeatPercent = 100;

    private String lastMode = "";
    private boolean automationStarted = false;
    private boolean captureInProgress = false;

    private TextView statusText;
    private TextView statusLabel;
    private TextView logButton;
    private String lastDisplayedStatus = "";

    private ObjectAnimator pulseAnimator;
    private SharedPreferences preferences;

    private String savedWordsJson = "[]";
    private int savedWordCount = 0;

    private JSONArray logEntries = new JSONArray();
    private static final int MAX_LOG_ENTRIES = 300;

    private String lastCaptureLog = "";
    private String lastLoggedWordsJson = "";

    private AlertDialog logDialog;
    private TextView logContent;
    private ScrollView logScrollView;

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

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable glassBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(220, 255, 255, 255));
        d.setCornerRadius(dp(25));
        d.setStroke(dp(1), Color.argb(190, 255, 255, 255));
        return d;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(235, 241, 255),
                        Color.rgb(244, 239, 255),
                        Color.rgb(238, 245, 250)
                }
        );
    }

    private GradientDrawable logButtonBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(170, 255, 255, 255));
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), Color.argb(170, 255, 255, 255));
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("classcard_word_memory", MODE_PRIVATE);

        loadSavedWords();
        loadLogs();
        addLog("시작", "저장 단어 " + savedWordCount + "개");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(pageBackground());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(16), dp(18), dp(14));

        TextView title = new TextView(this);
        title.setText("클카모바일");
        title.setTextColor(Color.rgb(35, 43, 65));
        title.setTextSize(23);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(5), dp(4), dp(5), dp(13));
        top.addView(title);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(19), dp(16), dp(19), dp(16));
        card.setBackground(glassBackground());
        card.setElevation(dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(15);
        dot.setTextColor(Color.rgb(90, 190, 150));
        header.addView(dot);

        TextView now = new TextView(this);
        now.setText("  NOW");
        now.setTextSize(11);
        now.setTextColor(Color.rgb(115, 125, 150));
        now.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(now);

        card.addView(header);

        statusText = new TextView(this);
        statusText.setText("학습 대기 중");
        statusText.setTextSize(18);
        statusText.setTextColor(Color.rgb(35, 43, 65));
        statusText.setTypeface(null, android.graphics.Typeface.BOLD);
        statusText.setPadding(0, dp(10), 0, dp(3));
        card.addView(statusText);

        statusLabel = new TextView(this);
        statusLabel.setText("클래스카드");
        statusLabel.setTextSize(12);
        statusLabel.setTextColor(Color.rgb(125, 133, 153));
        card.addView(statusLabel);

        top.addView(card);

        logButton = new TextView(this);
        updateLogButton();
        logButton.setTextSize(13);
        logButton.setTextColor(Color.rgb(65, 76, 105));
        logButton.setTypeface(null, android.graphics.Typeface.BOLD);
        logButton.setGravity(Gravity.CENTER);
        logButton.setPadding(dp(14), dp(11), dp(14), dp(11));
        logButton.setBackground(logButtonBackground());
        logButton.setElevation(dp(3));

        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logParams.topMargin = dp(10);
        top.addView(logButton, logParams);
        logButton.setOnClickListener(v -> showLogs());

        root.addView(top);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        pulseAnimator = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.45f, 1f);
        pulseAnimator.setDuration(1600);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setInterpolator(new DecelerateInterpolator());
        pulseAnimator.start();

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

    private void updateLogButton() {
        if (logButton != null) {
            logButton.setText("☷ 로그 (" + logEntries.length() + ")");
        }
    }

    private void addLog(String category, String message) {
        Runnable action = () -> {
            try {
                JSONObject entry = new JSONObject();

                String time = new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.KOREA
                ).format(new Date());

                entry.put("time", time);
                entry.put("category", category == null ? "" : category);
                entry.put("message", message == null ? "" : message);

                logEntries.put(entry);

                while (logEntries.length() > MAX_LOG_ENTRIES) {
                    logEntries.remove(0);
                }

                preferences.edit()
                        .putString("app_logs", logEntries.toString())
                        .apply();

                updateLogButton();
                refreshLogDialog();

            } catch (Exception ignored) {
            }
        };

        if (Thread.currentThread() == getMainLooper().getThread()) {
            action.run();
        } else {
            runOnUiThread(action);
        }
    }

    private void loadLogs() {
        try {
            logEntries = new JSONArray(
                    preferences.getString("app_logs", "[]")
            );

            while (logEntries.length() > MAX_LOG_ENTRIES) {
                logEntries.remove(0);
            }

        } catch (Exception e) {
            logEntries = new JSONArray();
        }
    }

    private String getLogsText() {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < logEntries.length(); i++) {
            JSONObject item = logEntries.optJSONObject(i);
            if (item == null) continue;

            b.append(item.optString("time", ""))
                    .append(" ")
                    .append(item.optString("category", ""))
                    .append(": ")
                    .append(item.optString("message", ""))
                    .append("\n");
        }

        b.append("\n저장 단어 ").append(savedWordCount).append("개\n");

        try {
            JSONArray words = new JSONArray(savedWordsJson);

            for (int i = 0; i < words.length(); i++) {
                JSONObject word = words.optJSONObject(i);
                if (word == null) continue;

                b.append(word.optString("ko", ""))
                        .append(" : ")
                        .append(word.optString("en", ""))
                        .append("\n");
            }
        } catch (Exception ignored) {
        }

        return b.toString();
    }

    private void showLogs() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView content = new TextView(this);
        content.setText(getLogsText());
        content.setTextSize(12);
        content.setTextColor(Color.rgb(40, 47, 65));
        content.setTextIsSelectable(true);
        content.setLineSpacing(dp(3), 1f);

        scroll.addView(content);

        logScrollView = scroll;
        logContent = content;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("로그")
                .setView(scroll)
                .setPositiveButton("닫기", null)
                .setNeutralButton("복사", null)
                .setNegativeButton("삭제", null)
                .create();

        logDialog = dialog;

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> copyLogs());

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("로그 삭제")
                                .setMessage("로그를 삭제할까요?")
                                .setNegativeButton("취소", null)
                                .setPositiveButton("삭제", (di, which) -> clearLogs())
                                .show();
                    });
        });

        dialog.setOnDismissListener(d -> {
            logDialog = null;
            logContent = null;
            logScrollView = null;
        });

        dialog.show();
    }

    private void refreshLogDialog() {
        if (logDialog == null || !logDialog.isShowing() || logContent == null) {
            return;
        }

        int y = logScrollView == null ? 0 : logScrollView.getScrollY();
        logContent.setText(getLogsText());

        if (logScrollView != null) {
            logScrollView.post(() -> logScrollView.scrollTo(0, y));
        }
    }

    private void copyLogs() {
        try {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null) return;

            clipboard.setPrimaryClip(
                    ClipData.newPlainText("Logs", getLogsText())
            );

            Toast.makeText(this, "복사 완료", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "복사 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearLogs() {
        logEntries = new JSONArray();

        preferences.edit()
                .putString("app_logs", "[]")
                .apply();

        updateLogButton();
        refreshLogDialog();

        Toast.makeText(this, "로그 삭제 완료", Toast.LENGTH_SHORT).show();
    }

    private class WordBridge {

        @JavascriptInterface
        public void saveWords(String json) {
            runOnUiThread(() -> storeWords(json));
        }

        @JavascriptInterface
        public void spellLog(String category, String message) {
            runOnUiThread(() -> addLog(category, message));
        }

        @JavascriptInterface
        public void sendSpellAnswer(String answer) {
            runOnUiThread(() -> {
                if (webView == null || answer == null) return;

                String script =
                        "(()=>{" +
                        "const c=document.querySelector('.CardItem.current.showing');" +
                        "if(!c)return false;" +
                        "const v=e=>e&&e.getClientRects().length>0&&getComputedStyle(e).display!=='none'&&getComputedStyle(e).visibility!=='hidden';" +
                        "const i=[...c.querySelectorAll('input[name=\"input_answer\"]')].find(v);" +
                        "if(!i)return false;" +
                        "i.focus();i.click();i.select();return true;" +
                        "})()";

                webView.evaluateJavascript(script, result -> {
                    if (webView == null) return;

                    if (!"true".equals(result)) {
                        setStatus("입력창 없음");
                        addLog("입력 오류", "입력창 없음");
                        return;
                    }

                    try {
                        EditorInfo info = new EditorInfo();
                        InputConnection connection = webView.onCreateInputConnection(info);

                        if (connection == null) {
                            setStatus("입력 연결 오류");
                            addLog("입력 오류", "연결 없음");
                            return;
                        }

                        boolean ok = connection.commitText(answer, 1);

                        if (!ok) {
                            setStatus("입력 실패");
                            addLog("입력 오류", "commitText 실패");
                        } else {
                            addLog("입력", answer);
                        }

                    } catch (Exception e) {
                        setStatus("입력 오류");
                        addLog("입력 예외", e.toString());
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
                setStatus("수집 실패");
                addLog("수집 실패", "단어 0개");
                return;
            }

            savedWordsJson = valid.toString();
            savedWordCount = valid.length();

            preferences.edit()
                    .putString("words", savedWordsJson)
                    .putInt("word_count", savedWordCount)
                    .apply();

            captureInProgress = false;
            setStatus("저장 완료: " + savedWordCount + "개");

            if (!savedWordsJson.equals(lastLoggedWordsJson)) {
                addLog("저장", savedWordCount + "개");
                lastLoggedWordsJson = savedWordsJson;
            }

            refreshLogDialog();

        } catch (Exception e) {
            captureInProgress = false;
            setStatus("저장 오류");
            addLog("저장 오류", e.toString());
        }
    }

    private void setStatus(String message) {
        if (statusText == null || message == null) return;
        if (message.equals(lastDisplayedStatus)) return;

        lastDisplayedStatus = message;

        statusText.animate()
                .alpha(0f)
                .setDuration(130)
                .withEndAction(() -> {
                    statusText.setText(message);
                    statusText.animate()
                            .alpha(1f)
                            .setDuration(220)
                            .start();
                })
                .start();

        if (message.contains("완료")) {
            statusLabel.setText("완료");
        } else if (message.contains("실패") || message.contains("오류")) {
            statusLabel.setText("확인 필요");
        } else if (message.contains("중")) {
            statusLabel.setText("진행 중");
        } else {
            statusLabel.setText("클래스카드");
        }
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
                    } catch (Exception ignored) {
                    }
                }
        );
    }

    private void restartDetection() {
        automationStarted = false;
        lastMode = "";
        lastDisplayedStatus = "";

        stopCurrentAutomation();
        setStatus("화면 확인 중");
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
                "let missing=[];" +
                "for(let i=0;i<current.length&&i<words.length;i++){" +
                "let ko=current[i].querySelector('div:nth-child(4) > div:nth-child(2) > div:nth-child(1) > div > div');" +
                "if(ko)words[i].ko=ko.innerText.trim();" +
                "else missing.push('행 '+(i+1)+': '+(current[i].innerText||'').trim());" +
                "}" +
                "let valid=words.filter(w=>w.en&&w.ko);" +
                "window.__classcardWords=valid;" +
                "window.__classcardWordsReady=valid.length>0;" +
                "if(valid.length)ClasscardBridge.saveWords(JSON.stringify(valid));" +
                "if(missing.length)ClasscardBridge.spellLog('뜻 누락','누락 '+missing.length+'개 / 전체 '+current.length+'개');" +
                "if(!valid.length)ClasscardBridge.spellLog('수집 실패','영어 '+words.length+'개 / 뜻 0개');" +
                "}catch(e){" +
                "try{ClasscardBridge.spellLog('수집 예외',String(e));}catch(ignore){}" +
                "}finally{" +
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

                if (!value.equals(lastCaptureLog)) {
                    addLog("수집", value);
                    lastCaptureLog = value;
                }

            } else {
                if (!value.equals(lastCaptureLog)) {
                    addLog("수집", "시작");
                    lastCaptureLog = value;
                }

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
                setStatus("단어 수집 대기");
                return;
            }

            if (automationStarted && mode.equals(lastMode)) return;
            if (automationStarted) stopCurrentAutomation();

            automationStarted = true;
            lastMode = mode;

            addLog("화면", mode);

            if (mode.equals("memorize") && memorizeEnabled) startMemorize();
            if (mode.equals("recall") && recallEnabled) startRecall();
            if (mode.equals("spell") && spellEnabled) startSpell();
        });
    }

    private void startMemorize() {
        setStatus("암기 중");

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
        setStatus("리콜 중");

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
        setStatus("단어 복구 중");

        addLog("스펠", "단어 " + savedWordCount + "개");

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
                "window.__mobileSpellStatus='스펠 시작';" +
                "const sleep=ms=>new Promise(r=>setTimeout(r,ms));" +

                "const norm=s=>(s||'').normalize('NFKC').toLowerCase()" +
                ".replace(/[.,，。:：;；!?！？]/g,' ')" +
                ".replace(/\\s+/g,' ').trim();" +

                "const meaningParts=s=>(s||'').split(/[\\n\\r,，;；、/|]+/)" +
                ".map(x=>norm(x)).filter(x=>x.length>0);" +

                "const log=(category,message)=>{" +
                "try{ClasscardBridge.spellLog(category,String(message));}catch(e){}" +
                "};" +

                "const visible=el=>{" +
                "if(!el)return false;" +
                "let r=el.getBoundingClientRect();" +
                "let s=getComputedStyle(el);" +
                "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';" +
                "};" +

                "const card=()=>document.querySelector('.CardItem.current.showing');" +

                "const input=c=>c?" +
                "[...c.querySelectorAll('.spell-input:not(.hidden) input[name=\"input_answer\"],input[name=\"input_answer\"]')].find(visible):null;" +

                "const prompt=c=>{" +
                "if(!c)return '';" +
                "let e=c.querySelector('.spell-content.font-32')||" +
                "c.querySelector('.spell-content')||" +
                "c.querySelector('.card-quest-front');" +
                "return visible(e)?e.innerText.trim():'';" +
                "};" +

                "const words=window.__classcardWords||[];" +

                "const findWord=p=>{" +
                "let target=norm(p);" +
                "if(!target)return null;" +
                "let exact=words.find(w=>norm(w.ko)===target);" +
                "if(exact)return exact;" +
                "for(let w of words){" +
                "if(meaningParts(w.ko).includes(target))return w;" +
                "}" +
                "return null;" +
                "};" +

                "const popup=()=>{" +
                "let b=[...document.querySelectorAll('button,a,[role=\"button\"]')]" +
                ".find(e=>visible(e)&&e.textContent.trim()==='알겠습니다!');" +
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
                "let lastMissingKey='';" +

                "log('스펠','시작 / 단어 '+words.length+'개');" +

                "if(!words.length){" +
                "log('정답 오류','복구된 단어 없음');" +
                "window.__mobileSpellStatus='단어 없음';" +
                "window.__mobileSpellRunning=false;" +
                "return;" +
                "}" +

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

                "let w=findWord(p);" +

                "if(!w){" +
                "window.__mobileSpellStatus='정답 없음: '+p;" +
                "let missingKey=norm(p);" +
                "if(lastMissingKey!==missingKey){" +
                "log('정답 없음','문제: '+p+' / 단어 '+words.length+'개');" +
                "lastMissingKey=missingKey;" +
                "}" +
                "await sleep(700);" +
                "continue;" +
                "}" +

                "lastMissingKey='';" +
                "let answer=w.en;" +
                "lastKey=currentKey;" +

                "log('매칭',p+' : '+answer);" +

                "window.__mobileSpellStatus='입력 중: '+p;" +
                "i.focus();" +
                "i.click();" +

                "try{ClasscardBridge.sendSpellAnswer(answer);}" +
                "catch(e){log('입력 예외',String(e));}" +

                "let filled=false;" +

                "for(let t=0;t<30;t++){" +
                "await sleep(200);" +
                "if(!window.__mobileSpellRunning)break;" +
                "if(i.value===answer){filled=true;break;}" +
                "}" +

                "if(!filled){" +
                "lastKey='';" +
                "window.__mobileSpellStatus='입력 실패';" +
                "log('입력 실패','예상: '+answer+' / 실제: '+i.value);" +
                "await sleep(500);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='입력 완료';" +
                "log('입력 확인',answer);" +

                "await sleep(300);" +
                "submit(i);" +
                "window.__mobileSpellStatus='제출 중';" +

                "let changed=false;" +

                "for(let t=0;t<40;t++){" +
                "await sleep(150);" +
                "popup();" +
                "let now=card();" +
                "if(!now||key(now)!==currentKey){changed=true;break;}" +
                "}" +

                "if(!changed){" +
                "let n=[...document.querySelectorAll('.btn-short-change-next')].find(visible);" +
                "if(n){" +
                "n.click();" +
                "for(let t=0;t<40;t++){" +
                "await sleep(150);" +
                "popup();" +
                "let now=card();" +
                "if(!now||key(now)!==currentKey){changed=true;break;}" +
                "}" +
                "}" +
                "}" +

                "if(!changed){" +
                "lastKey='';" +
                "window.__mobileSpellStatus='이동 실패';" +
                "log('이동 실패',p);" +
                "await sleep(1000);" +
                "continue;" +
                "}" +

                "window.__mobileSpellStatus='처리 완료';" +
                "log('진행',p);" +
                "await sleep(250);" +
                "}" +
                "}" +

                "window.__mobileSpellStatus='스펠 완료';" +
                "log('스펠','완료');" +
                "window.__mobileSpellRunning=false;" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    @Override
    public void onBackPressed() {
        if (logDialog != null && logDialog.isShowing()) {
            logDialog.dismiss();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(detector);

        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }

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