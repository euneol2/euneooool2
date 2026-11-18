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

    private boolean memorizeEnabled = true;
    private boolean recallEnabled = true;
    private boolean spellEnabled = true;

    private final int repeatPercent = 100;

    private String lastMode = "";
    private boolean automationStarted = false;
    private boolean captureInProgress = false;

    private TextView statusText;
    private TextView statusLabel;
    private TextView logButton;
    private String lastDisplayedStatus = "";

    private LinearLayout statusCard;
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
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(220, 255, 255, 255));
        drawable.setCornerRadius(dp(25));
        drawable.setStroke(dp(1), Color.argb(190, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable pageBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(235, 241, 255),
                        Color.rgb(244, 239, 255),
                        Color.rgb(238, 245, 250)
                }
        );
        return drawable;
    }

    private GradientDrawable logButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(170, 255, 255, 255));
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), Color.argb(170, 255, 255, 255));
        return drawable;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("classcard_word_memory", MODE_PRIVATE);

        loadSavedWords();
        loadLogs();

        addLog("앱 시작", "앱 실행 / 저장된 단어: " + savedWordCount + "개");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(pageBackground());

        LinearLayout topArea = new LinearLayout(this);
        topArea.setOrientation(LinearLayout.VERTICAL);
        topArea.setPadding(dp(18), dp(16), dp(18), dp(14));

        TextView appTitle = new TextView(this);
        appTitle.setText("클카모바일");
        appTitle.setTextColor(Color.rgb(35, 43, 65));
        appTitle.setTextSize(23);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appTitle.setGravity(Gravity.CENTER_VERTICAL);
        appTitle.setPadding(dp(5), dp(4), dp(5), dp(13));

        topArea.addView(appTitle);

        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(19), dp(16), dp(19), dp(16));
        statusCard.setBackground(glassBackground());
        statusCard.setElevation(dp(8));

        LinearLayout statusHeader = new LinearLayout(this);
        statusHeader.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(15);
        statusDot.setTextColor(Color.rgb(90, 190, 150));

        statusHeader.addView(statusDot);

        TextView nowLabel = new TextView(this);
        nowLabel.setText("  NOW");
        nowLabel.setTextSize(11);
        nowLabel.setTextColor(Color.rgb(115, 125, 150));
        nowLabel.setTypeface(null, android.graphics.Typeface.BOLD);

        statusHeader.addView(nowLabel);
        statusCard.addView(statusHeader);

        statusText = new TextView(this);
        statusText.setText("학습 화면을 기다리는 중...");
        statusText.setTextSize(18);
        statusText.setTextColor(Color.rgb(35, 43, 65));
        statusText.setTypeface(null, android.graphics.Typeface.BOLD);
        statusText.setPadding(0, dp(10), 0, dp(3));

        statusCard.addView(statusText);

        statusLabel = new TextView(this);
        statusLabel.setText("클래스카드 학습 상태");
        statusLabel.setTextSize(12);
        statusLabel.setTextColor(Color.rgb(125, 133, 153));

        statusCard.addView(statusLabel);
        topArea.addView(statusCard);

        logButton = new TextView(this);
        updateLogButton();

        logButton.setTextSize(13);
        logButton.setTextColor(Color.rgb(65, 76, 105));
        logButton.setTypeface(null, android.graphics.Typeface.BOLD);
        logButton.setGravity(Gravity.CENTER);
        logButton.setPadding(dp(14), dp(11), dp(14), dp(11));
        logButton.setBackground(logButtonBackground());
        logButton.setElevation(dp(3));

        LinearLayout.LayoutParams logButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        logButtonParams.topMargin = dp(10);
        topArea.addView(logButton, logButtonParams);

        logButton.setOnClickListener(v -> showLogs());

        root.addView(topArea);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        pulseAnimator = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.45f, 1f);
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
            logButton.setText("☷  로그 보기 (" + logEntries.length() + ")");
        }
    }

    private void addLog(String category, String message) {
        Runnable action = () -> {
            try {
                JSONObject entry = new JSONObject();

                String time = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.KOREA
                ).format(new Date());

                entry.put("time", time);
                entry.put("category", category == null ? "일반" : category);
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
            String saved = preferences.getString("app_logs", "[]");
            logEntries = new JSONArray(saved);

            while (logEntries.length() > MAX_LOG_ENTRIES) {
                logEntries.remove(0);
            }

        } catch (Exception e) {
            logEntries = new JSONArray();
        }
    }

    private String getLogsText() {
        StringBuilder builder = new StringBuilder();

        if (logEntries.length() == 0) {
            builder.append("기록이 없습니다.\n");
        } else {
            for (int i = 0; i < logEntries.length(); i++) {
                JSONObject entry = logEntries.optJSONObject(i);

                if (entry == null) continue;

                builder.append(entry.optString("time", ""))
                        .append("\n")
                        .append("[")
                        .append(entry.optString("category", "일반"))
                        .append("] ")
                        .append(entry.optString("message", ""))
                        .append("\n\n");
            }
        }

        builder.append("\n")
                .append("========== 저장된 단어 목록 ==========\n")
                .append("총 ")
                .append(savedWordCount)
                .append("개\n\n");

        try {
            JSONArray words = new JSONArray(savedWordsJson);

            if (words.length() == 0) {
                builder.append("저장된 단어가 없습니다.\n");
            } else {
                for (int i = 0; i < words.length(); i++) {
                    JSONObject word = words.optJSONObject(i);
                    if (word == null) continue;

                    builder.append(i + 1)
                            .append(". ")
                            .append(word.optString("ko", ""))
                            .append(" → ")
                            .append(word.optString("en", ""))
                            .append("\n");
                }
            }
        } catch (Exception e) {
            builder.append("단어 목록을 읽지 못했습니다: ")
                    .append(e.getMessage());
        }

        return builder.toString();
    }

    private void showLogs() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView content = new TextView(this);
        content.setText(getLogsText());
        content.setTextSize(12);
        content.setTextColor(Color.rgb(40, 47, 65));
        content.setTextIsSelectable(true);
        content.setLineSpacing(dp(3), 1f);

        scrollView.addView(content);

        logScrollView = scrollView;
        logContent = content;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("앱 로그 및 저장 단어")
                .setView(scrollView)
                .setPositiveButton("닫기", null)
                .setNeutralButton("복사", null)
                .setNegativeButton("기록 삭제", null)
                .create();

        logDialog = dialog;

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> copyLogs());

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("로그 삭제")
                        .setMessage("저장된 로그를 모두 삭제할까요? 단어 데이터는 삭제되지 않습니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("삭제", (dialogInterface, which) -> {
                            clearLogs();
                        })
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

        int oldScrollY = logScrollView == null ? 0 : logScrollView.getScrollY();

        logContent.setText(getLogsText());

        if (logScrollView != null) {
            logScrollView.post(() -> logScrollView.scrollTo(0, oldScrollY));
        }
    }

    private void copyLogs() {
        try {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null) return;

            ClipData clip = ClipData.newPlainText(
                    "Classcard Logs",
                    getLogsText()
            );

            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "로그와 저장 단어 목록을 복사했습니다.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "로그 복사에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearLogs() {
        logEntries = new JSONArray();

        preferences.edit()
                .putString("app_logs", "[]")
                .apply();

        updateLogButton();
        refreshLogDialog();

        Toast.makeText(this, "로그를 삭제했습니다. 저장된 단어는 유지됩니다.", Toast.LENGTH_SHORT).show();
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

                addLog("스펠 입력 요청", "입력할 정답: " + answer);

                String prepareScript =
                        "(()=>{" +
                        "const card=document.querySelector('.CardItem.current.showing');" +
                        "if(!card)return false;" +
                        "const visible=el=>el&&el.getClientRects().length>0&&getComputedStyle(el).display!=='none'&&getComputedStyle(el).visibility!=='hidden';" +
                        "const input=[...card.querySelectorAll('input[name=\"input_answer\"]')].find(visible);" +
                        "if(!input)return false;" +
                        "input.focus();" +
                        "input.click();" +
                        "input.select();" +
                        "return true;" +
                        "})()";

                webView.evaluateJavascript(prepareScript, result -> {
                    if (webView == null) return;

                    if (!"true".equals(result)) {
                        setStatus("스펠 입력창을 찾지 못했습니다.");
                        addLog("스펠 입력 오류", "입력창을 찾지 못했습니다. 반환값: " + result);
                        return;
                    }

                    try {
                        EditorInfo editorInfo = new EditorInfo();
                        InputConnection connection = webView.onCreateInputConnection(editorInfo);

                        if (connection == null) {
                            setStatus("키보드 입력 연결을 가져오지 못했습니다.");
                            addLog("스펠 입력 오류", "InputConnection이 null입니다.");
                            return;
                        }

                        boolean committed = connection.commitText(answer, 1);

                        if (!committed) {
                            setStatus("Android 키보드 입력에 실패했습니다.");
                            addLog("스펠 입력 오류", "commitText 결과가 false입니다.");
                        } else {
                            addLog("스펠 입력", "Android 입력 성공: " + answer);
                        }

                    } catch (Exception e) {
                        setStatus("스펠 입력 오류: " + e.getMessage());
                        addLog("스펠 입력 예외", e.toString());
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

                addLog(
                        "단어 수집 실패",
                        "수집된 단어가 없습니다. 전달받은 데이터: " + json
                );

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

            if (!savedWordsJson.equals(lastLoggedWordsJson)) {
                addLog(
                        "단어 저장 완료",
                        "저장된 단어: " + savedWordCount + "개"
                );

                lastLoggedWordsJson = savedWordsJson;
            }

            refreshLogDialog();

        } catch (Exception e) {
            captureInProgress = false;
            setStatus("단어 저장 오류: " + e.getMessage());
            addLog("단어 저장 예외", e.toString());
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
            statusLabel.setText("학습 작업이 완료됐어요");
        } else if (message.contains("실패") || message.contains("오류")) {
            statusLabel.setText("확인이 필요한 상태예요");
        } else if (message.contains("중")) {
            statusLabel.setText("자동 학습이 진행되고 있어요");
        } else {
            statusLabel.setText("클래스카드 학습 상태");
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
                    } catch (Exception ignored) {}
                }
        );
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
                "if(ko)words[i].ko=ko.innerText.trim();" +
                "}" +
                "let valid=words.filter(w=>w.en&&w.ko);" +
                "window.__classcardWords=valid;" +
                "window.__classcardWordsReady=valid.length>0;" +
                "if(valid.length){" +
                "ClasscardBridge.saveWords(JSON.stringify(valid));" +
                "}else{" +
                "ClasscardBridge.spellLog('단어 수집 실패','영어 단어는 찾았지만 한국어 뜻을 수집하지 못했습니다.');" +
                "}" +
                "}catch(e){" +
                "try{ClasscardBridge.spellLog('단어 수집 예외',String(e));}catch(ignore){}" +
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
                    addLog("단어 수집", "수집 영역 상태: " + value);
                    lastCaptureLog = value;
                }

            } else {
                if (!value.equals(lastCaptureLog)) {
                    addLog("단어 수집", "단어 목록 수집 작업 시작");
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

                setStatus("스펠 대기: 단어 목록을 먼저 수집해 주세요.");
                return;
            }

            if (automationStarted && mode.equals(lastMode)) return;
            if (automationStarted) stopCurrentAutomation();

            automationStarted = true;
            lastMode = mode;

            addLog("화면 감지", "학습 모드 감지: " + mode);

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

        addLog("스펠 시작", "저장된 단어 " + savedWordCount + "개를 복구합니다.");

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
                "const log=(category,message)=>{try{ClasscardBridge.spellLog(category,String(message));}catch(e){}};" +
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
                "let lastMissingKey='';" +
                "log('스펠 자동화','시작 / 저장 단어 수: '+words.length);" +
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
                "let missingKey=norm(p);" +
                "if(lastMissingKey!==missingKey){" +
                "let examples=words.slice(0,5).map(x=>(x.ko||'')+' → '+(x.en||'')).join(' | ');" +
                "log('정답 데이터 없음','표시된 뜻: '+p+' / 저장 단어 수: '+words.length+' / 저장 예시: '+examples);" +
                "lastMissingKey=missingKey;" +
                "}" +
                "await sleep(700);" +
                "continue;" +
                "}" +
                "lastMissingKey='';" +
                "let answer=norm(w.ko)===norm(p)?w.en:w.ko;" +
                "lastKey=currentKey;" +
                "log('단어 매칭 성공','문제: '+p+' / 연결된 단어: '+w.ko+' → '+w.en+' / 입력할 답: '+answer);" +
                "window.__mobileSpellStatus='입력 준비 중: '+p;" +
                "i.focus();" +
                "i.click();" +
                "try{ClasscardBridge.sendSpellAnswer(answer);}catch(e){log('스펠 입력 예외',String(e));}" +
                "let filled=false;" +
                "for(let t=0;t<30;t++){" +
                "await sleep(200);" +
                "if(!window.__mobileSpellRunning)break;" +
                "if(i.value===answer){filled=true;break;}" +
                "}" +
                "if(!filled){" +
                "lastKey='';" +
                "window.__mobileSpellStatus='입력 확인 실패';" +
                "log('스펠 입력 실패','문제: '+p+' / 예상 답: '+answer+' / 실제 입력값: '+i.value);" +
                "await sleep(500);" +
                "continue;" +
                "}" +
                "window.__mobileSpellStatus='입력 확인 완료';" +
                "log('스펠 입력 확인','문제: '+p+' / 입력된 답: '+i.value);" +
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
                "log('스펠 이동 실패','정답 제출 후 다음 문제로 이동하지 못했습니다. 문제: '+p);" +
                "await sleep(1000);" +
                "continue;" +
                "}" +
                "window.__mobileSpellStatus='문제 처리 완료';" +
                "log('스펠 진행','문제 처리 완료: '+p);" +
                "await sleep(250);" +
                "}" +
                "}" +
                "window.__mobileSpellStatus='스펠 반복 완료';" +
                "log('스펠 완료','스펠 자동화가 종료됐습니다.');" +
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