package com.example.classcardauto;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

public class ClasscardImeService extends InputMethodService {

    private static volatile ClasscardImeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public View onCreateInputView() {
        TextView view = new TextView(this);
        view.setText("클카 입력 키보드");
        view.setTextSize(16);
        view.setPadding(24, 24, 24, 24);
        return view;
    }

    public static boolean typeAnswer(String answer) {
        ClasscardImeService service = instance;

        if (service == null || answer == null) {
            return false;
        }

        service.handler.post(() -> {
            InputConnection connection =
                    service.getCurrentInputConnection();

            if (connection != null) {
                connection.commitText(answer, 1);
            }
        });

        return true;
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
