package com.agy.ru;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import com.agy.theme.ThemeManager;
import com.agy.ui.MainActivity;
import com.agy.util.ViewParams;

public final class FirstRun {
    static MainActivity act;
    static boolean autoArmed;
    static boolean coreStarted;
    static long lastAuto;
    static FrameLayout root;
    static ThemeManager tm;
    static WebView view;

    private FirstRun() {}

    public static final class Bridge {
        private final Context ctx;

        public Bridge(Context ctx) {
            this.ctx = ctx;
        }

        @JavascriptInterface
        public void action(String action, String arg) {
            new Handler(Looper.getMainLooper()).post(new Task(action, arg));
        }

        @JavascriptInterface
        public String getLang() {
            return FirstRun.getLang(this.ctx);
        }

        @JavascriptInterface
        public boolean isOnboarded() {
            return FirstRun.isDone(this.ctx);
        }

        @JavascriptInterface
        public void setLang(String lang) {
            FirstRun.setLang(this.ctx, lang);
        }
    }

    static final class Task implements Runnable {
        final String action;
        final String arg;

        Task(String action, String arg) {
            this.action = action;
            this.arg = arg;
        }

        @Override
        public void run() {
            try {
                FirstRun.dispatch(this.action, this.arg);
            } catch (Throwable t) {
                Log.w("AgyFirstRun", "action failed", t);
            }
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("agy_ru", 0);
    }

    static void putStep(Context ctx, int step) {
        prefs(ctx).edit().putInt("step", step).commit();
    }

    static boolean validLang(String lang) {
        if (lang == null) {
            return false;
        }
        return "system".equals(lang) || "ru".equals(lang) || "en".equals(lang);
    }

    public static String getLang(Context ctx) {
        return prefs(ctx).getString("lang", "system");
    }

    public static void setLang(Context ctx, String lang) {
        if (validLang(lang)) {
            prefs(ctx).edit().putString("lang", lang).commit();
        }
    }

    public static boolean isDone(Context ctx) {
        return prefs(ctx).getBoolean("onboardingCompleted", false);
    }

    public static boolean isShowing() {
        return view != null;
    }

    static boolean filesGranted(Context ctx) {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            return true;
        }
        return ctx.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == 0;
    }

    static boolean notifEnabled(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService("notification");
        if (nm == null) {
            return true;
        }
        return nm.areNotificationsEnabled();
    }

    static boolean startSafely(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean openNotifSettings(Activity activity) {
        Intent intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
        String pkg = activity.getPackageName();
        intent.putExtra("android.provider.extra.APP_PACKAGE", pkg);
        intent.putExtra("app_package", pkg);
        return startSafely(activity, intent);
    }

    static void postAuto() {
        new Handler(Looper.getMainLooper()).postDelayed(new Task("auto", "auto"), 400L);
    }

    static void startCore() {
        if (!coreStarted && act != null) {
            coreStarted = true;
            act.agyStartCore();
        }
    }

    static void close() {
        WebView wv = view;
        if (wv != null) {
            view = null;
            FrameLayout r = root;
            if (r != null) {
                r.removeView(wv);
            }
            wv.destroy();
        }
    }

    public static void start(MainActivity activity, FrameLayout rootLayout, ThemeManager themeManager) {
        act = activity;
        root = rootLayout;
        tm = themeManager;
        coreStarted = false;
        view = null;
        if (isDone(activity)) {
            startCore();
            return;
        }
        activity.agyHoldCore();
        WebView wv = new WebView(activity);
        wv.setBackgroundColor(themeManager.getBackgroundColor());
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wv.addJavascriptInterface(new Bridge(activity.getApplicationContext()), "AgyFirstRun");
        rootLayout.addView(wv, ViewParams.full());
        view = wv;
        refresh();
    }

    public static void onResume(MainActivity activity) {
        if (act == activity) {
            refresh();
        }
    }

    public static void onDestroy(MainActivity activity) {
        if (act == activity) {
            close();
            act = null;
            root = null;
            tm = null;
        }
    }

    static void refresh() {
        MainActivity a = act;
        WebView wv = view;
        if (a == null || wv == null) {
            return;
        }
        SharedPreferences sp = prefs(a);
        int step = sp.getInt("step", 0);
        if (step <= 0 && filesGranted(a)) {
            step = 1;
        }
        if (step == 1 && notifEnabled(a)) {
            step = 2;
        }
        putStep(a, step);
        if (step < 2 && !autoArmed) {
            autoArmed = true;
            postAuto();
        }
        String stepName = "files";
        if (step != 0) {
            stepName = "notif";
            if (step != 1) {
                stepName = "lang";
                if (notifEnabled(a) || sp.getBoolean("notifAsked", false)) {
                    startCore();
                }
            }
        }
        StringBuilder url = new StringBuilder();
        url.append("file:///android_asset/web/agy_firstrun.html#step=");
        url.append(stepName);
        url.append("&theme=");
        String theme = "dark";
        if (a.getSharedPreferences("agy_prefs", 0).contains("theme_mode_v3")) {
            theme = "light";
            if (tm != null && tm.isDark()) {
                theme = "dark";
            }
        }
        int bgColor = -1;
        if ("dark".equals(theme)) {
            bgColor = 0xFF101010;
        }
        wv.setBackgroundColor(bgColor);
        url.append(theme);
        wv.loadUrl(url.toString());
    }

    static void dispatch(String action, String arg) {
        MainActivity a = act;
        WebView wv = view;
        if (a == null || wv == null || action == null) {
            return;
        }
        if ("auto".equals(action)) {
            SharedPreferences sp = prefs(a);
            int step = sp.getInt("step", 0);
            if (step >= 2) {
                autoArmed = false;
                return;
            }
            if (a.hasWindowFocus()) {
                long now = SystemClock.uptimeMillis();
                if (now - lastAuto >= 1500L) {
                    lastAuto = now;
                    dispatch(step == 0 ? "requestFiles" : "requestNotif", "");
                }
            }
            if (view == null) {
                autoArmed = false;
                return;
            }
            postAuto();
            return;
        }
        if ("requestFiles".equals(action)) {
            putStep(a, 1);
            a.requestPermissions(new String[] {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
            }, 100);
            return;
        }
        if ("skipFiles".equals(action)) {
            putStep(a, 1);
            refresh();
            return;
        }
        if ("requestNotif".equals(action)) {
            prefs(a).edit().putBoolean("notifAsked", true).commit();
            if (notifEnabled(a)) {
                putStep(a, 2);
                refresh();
                return;
            }
            if (Build.VERSION.SDK_INT >= 33) {
                NotificationManager nm = (NotificationManager) a.getSystemService("notification");
                if (nm != null) {
                    nm.createNotificationChannel(new NotificationChannel("core_channel", "Core", 2));
                    new Handler(Looper.getMainLooper()).postDelayed(new Task("notifCheck", ""), 900L);
                    return;
                }
            }
            if (!openNotifSettings(a)) {
                refresh();
            }
            return;
        }
        if ("notifCheck".equals(action)) {
            if (a.hasWindowFocus()) {
                if (!notifEnabled(a)) {
                    if (openNotifSettings(a)) {
                        return;
                    }
                }
                refresh();
            }
            return;
        }
        if ("skipNotif".equals(action)) {
            putStep(a, 2);
            refresh();
            return;
        }
        if ("chooseLang".equals(action) && validLang(arg)) {
            prefs(a).edit()
                .putString("lang", arg)
                .putInt("step", 3)
                .putBoolean("onboardingCompleted", true)
                .commit();
            a.agyAfterOnboarding();
            close();
            startCore();
        }
    }
}
