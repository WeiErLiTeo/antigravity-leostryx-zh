package com.agy.ru;

import android.webkit.ValueCallback;
import com.agy.ui.MainActivity;

public final class BackCb implements ValueCallback {
    private final MainActivity a;

    public BackCb(MainActivity a) {
        this.a = a;
    }

    @Override
    public void onReceiveValue(Object value) {
        if (!"\"1\"".equals(value)) {
            this.a.agyBackDefault();
        }
    }
}
