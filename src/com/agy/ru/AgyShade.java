package com.agy.ru;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Notification shade bridge for Android.
 * Only attaches the Approve/Continue action button when needsAction == true.
 */
public class AgyShade {

    private static final String TAG = "AgyShade";
    private static final String CHANNEL_ID = "agy_agent_chat";
    private static final String ACTION_REPLY = "com.agyru.t2.ACTION_AGENT_REPLY";
    private static final String ACTION_APPROVE = "com.agyru.t2.ACTION_AGENT_APPROVE";
    private static final String EXTRA_CASCADE_ID = "cascadeId";
    private static final String EXTRA_TITLE = "title";
    private static final String KEY_TEXT_REPLY = "agy_reply_text";

    private static AgyShade sInstance;

    private final Context ctx;
    private final WebView webView;
    private final NotificationManager nm;
    private final Handler mainHandler;
    private final List<JSONObject> pendingReplies = new ArrayList<JSONObject>();
    private final BroadcastReceiver receiver;

    public AgyShade(Context context, WebView webView) {
        this.ctx = context.getApplicationContext();
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.nm = (NotificationManager) this.ctx.getSystemService("notification");
        sInstance = this;
        createChannel();

        this.receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                handleIntent(intent);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REPLY);
        filter.addAction(ACTION_APPROVE);
        if (Build.VERSION.SDK_INT >= 33) {
            this.ctx.registerReceiver(this.receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.ctx.registerReceiver(this.receiver, filter);
        }
    }

    public static AgyShade getInstance() {
        return sInstance;
    }

    private void createChannel() {
        if (this.nm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            NotificationChannel existing = this.nm.getNotificationChannel(CHANNEL_ID);
            if (existing == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID,
                        "智能体通知与快捷回复",
                        NotificationManager.IMPORTANCE_HIGH
                );
                ch.setDescription("在通知栏显示智能体的提问、确认请求与快捷回复");
                ch.enableLights(true);
                ch.setLightColor(0xFF0B84FF);
                ch.enableVibration(true);
                this.nm.createNotificationChannel(ch);
            } else {
                existing.setName("智能体通知与快捷回复");
                existing.setDescription("在通知栏显示智能体的提问、确认请求与快捷回复");
                this.nm.createNotificationChannel(existing);
            }
        } catch (Throwable t) {
            Log.w(TAG, "createChannel failed", t);
        }
    }

    private int getAppIcon() {
        try {
            int id = this.ctx.getResources().getIdentifier("ic_notification", "drawable", this.ctx.getPackageName());
            if (id != 0) {
                return id;
            }
        } catch (Throwable ignored) {
        }
        return android.R.drawable.ic_dialog_info;
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            String action = intent.getAction();
            String rawCid = intent.getStringExtra(EXTRA_CASCADE_ID);
            String cascadeId = rawCid != null ? rawCid : "";
            String replyText = null;
            String type = "reply";

            if (ACTION_REPLY.equals(action)) {
                Bundle results = RemoteInput.getResultsFromIntent(intent);
                if (results != null) {
                    CharSequence cs = results.getCharSequence(KEY_TEXT_REPLY);
                    if (cs != null) {
                        replyText = cs.toString().trim();
                    }
                }
                type = "reply";
            } else if (ACTION_APPROVE.equals(action)) {
                replyText = "__AGY_APPROVE__";
                type = "approve";
            } else {
                return;
            }

            if (replyText == null || replyText.isEmpty()) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("cascadeId", cascadeId);
            obj.put("text", replyText);
            obj.put("type", type);
            obj.put("time", System.currentTimeMillis());

            if (this.nm != null) {
                final int notifId = cascadeId.isEmpty() ? 0xbc1f : (cascadeId.hashCode() & 0x7fffffff);
                String rawTitle = intent.getStringExtra(EXTRA_TITLE);
                String notifTitle = (rawTitle != null && !rawTitle.isEmpty()) ? rawTitle : "Antigravity";
                String feedback = "__AGY_APPROVE__".equals(replyText) ? "操作已确认" : ("回复: " + replyText);

                Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ? new Notification.Builder(this.ctx, CHANNEL_ID)
                        : new Notification.Builder(this.ctx);
                b.setSmallIcon(getAppIcon())
                        .setContentTitle(notifTitle)
                        .setContentText(feedback)
                        .setAutoCancel(true);
                this.nm.notify("agy_agent", notifId, b.build());

                this.mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (nm != null) {
                                nm.cancel("agy_agent", notifId);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }, 1500L);
            }

            synchronized (this.pendingReplies) {
                this.pendingReplies.add(obj);
            }
            final String jsonStr = obj.toString();
            this.mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (webView != null) {
                            webView.evaluateJavascript(
                                    "(function(){ try { if (window.__agyOnShadeReply) { window.__agyOnShadeReply("
                                            + jsonStr + "); } } catch(e){} })();",
                                    null
                            );
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "evaluateJavascript failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "handleIntent failed", t);
        }
    }

    @JavascriptInterface
    public void notify(String cascadeId, String title, String body, boolean needsAction) {
        if (this.nm == null) {
            return;
        }
        try {
            String notifTitle = (title != null && !title.isEmpty()) ? title : "Antigravity";
            String notifBody = (body != null && !body.isEmpty()) ? body : "...";
            String cid = (cascadeId != null) ? cascadeId : "";
            int notifId = cid.isEmpty() ? 0xbc1f : (cid.hashCode() & 0x7fffffff);

            Intent launchIntent = this.ctx.getPackageManager().getLaunchIntentForPackage(this.ctx.getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                launchIntent.putExtra(EXTRA_CASCADE_ID, cid);
            } else {
                launchIntent = new Intent();
            }

            int immutableFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent contentPi = PendingIntent.getActivity(
                    this.ctx,
                    notifId,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag
            );

            Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? new Notification.Builder(this.ctx, CHANNEL_ID)
                    : new Notification.Builder(this.ctx);

            builder.setSmallIcon(getAppIcon())
                    .setContentTitle(notifTitle)
                    .setContentText(notifBody)
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setStyle(new Notification.BigTextStyle().bigText(notifBody));

            // Always attach Quick Reply action
            int replyMutableFlag = (Build.VERSION.SDK_INT >= 31) ? PendingIntent.FLAG_MUTABLE : 0;
            Intent replyIntent = new Intent(ACTION_REPLY);
            replyIntent.setPackage(this.ctx.getPackageName());
            replyIntent.putExtra(EXTRA_CASCADE_ID, cid);
            replyIntent.putExtra(EXTRA_TITLE, notifTitle);
            PendingIntent replyPi = PendingIntent.getBroadcast(
                    this.ctx,
                    notifId + 1,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | replyMutableFlag
            );

            RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel("回复智能体...")
                    .build();
            Notification.Action replyAction = new Notification.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "回复",
                    replyPi
            ).addRemoteInput(remoteInput).build();
            builder.addAction(replyAction);

            // ONLY attach Approve / Continue button when model explicitly requests permission/action
            if (needsAction) {
                Intent approveIntent = new Intent(ACTION_APPROVE);
                approveIntent.setPackage(this.ctx.getPackageName());
                approveIntent.putExtra(EXTRA_CASCADE_ID, cid);
                approveIntent.putExtra(EXTRA_TITLE, notifTitle);
                PendingIntent approvePi = PendingIntent.getBroadcast(
                        this.ctx,
                        notifId + 2,
                        approveIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag
                );
                Notification.Action approveAction = new Notification.Action.Builder(
                        android.R.drawable.ic_media_play,
                        "允许 / 继续",
                        approvePi
                ).build();
                builder.addAction(approveAction);
            }

            this.nm.notify("agy_agent", notifId, builder.build());
        } catch (Throwable t) {
            Log.e(TAG, "notify failed", t);
        }
    }

    @JavascriptInterface
    public void cancel(String cascadeId) {
        if (this.nm == null) {
            return;
        }
        try {
            if (cascadeId != null && !cascadeId.isEmpty()) {
                this.nm.cancel("agy_agent", cascadeId.hashCode() & 0x7fffffff);
            } else {
                this.nm.cancel("agy_agent", 0xbc1f);
            }
        } catch (Throwable ignored) {
        }
    }

    @JavascriptInterface
    public void cancelAll() {
        if (this.nm != null) {
            try {
                this.nm.cancelAll();
            } catch (Throwable ignored) {
            }
        }
    }

    @JavascriptInterface
    public String pollReplies() {
        synchronized (this.pendingReplies) {
            if (this.pendingReplies.isEmpty()) {
                return "[]";
            }
            JSONArray arr = new JSONArray();
            for (int i = 0; i < this.pendingReplies.size(); i++) {
                arr.put(this.pendingReplies.get(i));
            }
            this.pendingReplies.clear();
            return arr.toString();
        }
    }

    @JavascriptInterface
    public void openSystemPreferences() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, this.ctx.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.ctx.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }
}
