package com.agy.ui.webview;

import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import com.agy.BuildConfig;
import com.agy.bridge.AndroidBridge;
import com.agy.theme.ThemeManager;

/** Creates and hardware-configures the host WebView. */
public final class WebViewFactory {

    private WebViewFactory() {
    }

    public static WebView create(Context context, AndroidBridge bridge, ThemeManager theme) {
        WebView view = new WebView(context);
        view.setBackgroundColor(theme.getBackgroundColor());
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus(View.FOCUS_DOWN);
        // Hardware layer: hand compositing to the GPU (manifest enables HW accel).
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.addJavascriptInterface(bridge, "Android");
        view.addJavascriptInterface(new com.agy.ru.FirstRun.Bridge(view.getContext().getApplicationContext()), "AgyLang");
        view.addJavascriptInterface(new com.agy.ru.AgyTools(view.getContext().getApplicationContext()), "AgyTools");
        view.addJavascriptInterface(new com.agy.ru.AgyProviders(view.getContext().getApplicationContext()), "AgyProviders");
        view.addJavascriptInterface(new com.agy.ru.AgyMic(view.getContext()), "AgyMic");
        view.addJavascriptInterface(new com.agy.ru.AgySTT(view.getContext()), "AgySTT");
        view.addJavascriptInterface(new com.agy.ru.AgyVosk(view.getContext()), "AgyVosk");
        view.addJavascriptInterface(new com.agy.ru.AgyShade(view.getContext().getApplicationContext(), view), "AgyShade");
        // Android WebView needs an explicit focus grant for the soft keyboard to show.
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    ((WebView) v).requestFocus(View.FOCUS_DOWN);
                }
                return false;
            }
        });

        WebSettingsFactory.configure(view);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        return view;
    }
}
