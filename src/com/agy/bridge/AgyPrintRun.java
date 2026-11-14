package com.agy.bridge;

import android.app.Activity;
import android.webkit.WebView;

public final class AgyPrintRun implements Runnable {
    private Activity activity;
    private String html;
    private String title;

    public AgyPrintRun(Activity activity, String html, String title) {
        this.activity = activity;
        this.html = html;
        this.title = title;
    }

    @Override
    public void run() {
        WebView wv = new WebView(this.activity);
        wv.setWebViewClient(new AgyPrintClient(this.activity, this.title));
        wv.loadDataWithBaseURL(null, this.html, "text/html", "UTF-8", null);
    }
}
