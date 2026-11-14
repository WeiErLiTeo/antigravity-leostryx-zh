package com.agy.bridge;

import android.app.Activity;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class AgyPrintClient extends WebViewClient {
    private Activity activity;
    private boolean started;
    private String title;

    public AgyPrintClient(Activity activity, String title) {
        this.activity = activity;
        this.title = title;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (!this.started) {
            this.started = true;
            PrintManager pm = (PrintManager) this.activity.getSystemService("print");
            if (pm != null) {
                String t = this.title;
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(t);
                pm.print(t, adapter, null);
            }
        }
    }
}
