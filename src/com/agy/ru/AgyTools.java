package com.agy.ru;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;
import com.agy.theme.IconPackSwitcher;
import com.agy.util.AppFileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Map;
import org.json.JSONObject;

public final class AgyTools {
    private final Context ctx;

    public AgyTools(Context ctx) {
        this.ctx = ctx;
    }

    private File dir() {
        File d = new File(this.ctx.getFilesDir(), "agy-tools");
        d.mkdirs();
        return d;
    }

    private void copyScript(File dst) throws Exception {
        InputStream in = this.ctx.getAssets().open("web/agy_tools.sh");
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        while (true) {
            int n = in.read(buf);
            if (n <= 0) {
                break;
            }
            out.write(buf, 0, n);
        }
        out.close();
        in.close();
    }

    private static String readTail(File file, int maxBytes) {
        if (!file.exists()) {
            return "";
        }
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            long len = raf.length();
            long start = len - (long) maxBytes;
            if (start < 0L) {
                start = 0L;
            }
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            raf.close();
            return new String(buf, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    @JavascriptInterface
    public String start(String op, String arg) {
        if (op == null || !op.matches("^[a-z]{2,16}$")) {
            return "ERR:op";
        }
        if (arg == null) {
            arg = "";
        }
        if (!arg.matches("^[A-Za-z0-9 ._+;:,=@/|#-]{0,65536}$")) {
            return "ERR:arg";
        }
        try {
            File baseDir = dir();
            File script = new File(baseDir, "agy-tools.sh");
            copyScript(script);
            String id = Long.toString(System.currentTimeMillis());
            File jobDir = new File(baseDir, "job-" + id);
            jobDir.mkdirs();
            File filesDir = this.ctx.getFilesDir();
            String homePath = filesDir.getAbsolutePath();
            String prefixPath = homePath + "/usr";
            String[] cmd = new String[] {
                "/system/bin/sh",
                "-c",
                ". \"$PREFIX/etc/profile\" >/dev/null 2>&1; \"$PREFIX/bin/bash\" \"$0\" \"$@\"; echo $? > \"$AGY_JOB_DIR/rc\"",
                script.getAbsolutePath(),
                op,
                arg
            };
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(filesDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(jobDir, "log"));
            Map<String, String> env = pb.environment();
            env.put("HOME", homePath);
            env.put("PREFIX", prefixPath);
            env.put("AGY_JOB_DIR", jobDir.getAbsolutePath());
            env.put("AGY_APPLICATION_ID", this.ctx.getPackageName());
            env.remove("LD_LIBRARY_PATH");
            pb.start();
            return id;
        } catch (Throwable t) {
            return "ERR:" + t.toString();
        }
    }

    @JavascriptInterface
    public String read(String id) {
        if (id == null || !id.matches("^[0-9]{1,20}$")) {
            return "{}";
        }
        try {
            File jobDir = new File(dir(), "job-" + id);
            JSONObject obj = new JSONObject();
            obj.put("log", readTail(new File(jobDir, "log"), 24576));
            obj.put("rc", readTail(new File(jobDir, "rc"), 32).trim());
            return obj.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void installApk(String path) {
        if (path == null) {
            return;
        }
        try {
            File f = new File(path);
            if (!f.exists()) {
                return;
            }
            Uri uri = AppFileProvider.getUriForFile(this.ctx, f);
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            this.ctx.startActivity(intent);
        } catch (Throwable t) {
            Log.e("AgyTools", "installApk failed", t);
        }
    }

    @JavascriptInterface
    public void switchIconPack(String packId) {
        IconPackSwitcher.switchPack(this.ctx, packId);
    }
}
