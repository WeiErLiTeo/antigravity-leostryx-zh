package com.agy.ru;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.webkit.WebChromeClient;
import com.agy.util.AppFileProvider;
import java.io.File;
import java.util.ArrayList;

public final class AgyMediaChooser {
    private static File lastCameraFile;
    private static Uri lastCameraUri;

    private AgyMediaChooser() {}

    public static Intent buildChooserIntent(Activity activity, WebChromeClient.FileChooserParams params) {
        boolean isMedia = false;
        try {
            if (params != null) {
                String[] acceptTypes = params.getAcceptTypes();
                if (acceptTypes != null) {
                    for (String t : acceptTypes) {
                        if (t != null) {
                            String low = t.toLowerCase();
                            if (low.contains("image") || low.contains("video")) {
                                isMedia = true;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            isMedia = false;
        }

        if (!isMedia) {
            cleanupCameraFile();
            Intent docIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            docIntent.addCategory(Intent.CATEGORY_OPENABLE);
            docIntent.setType("*/*");
            docIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            return docIntent;
        }

        cleanupCameraFile();
        ArrayList<Intent> initialIntents = new ArrayList<Intent>();
        try {
            Intent takePictureIntent = new Intent("android.media.action.IMAGE_CAPTURE");
            File cacheDir = activity.getCacheDir();
            File photoFile = File.createTempFile("capture_" + System.currentTimeMillis() + "_", ".jpg", cacheDir);
            Uri photoUri = AppFileProvider.getUriForFile(activity, photoFile);
            lastCameraFile = photoFile;
            lastCameraUri = photoUri;
            takePictureIntent.putExtra("output", photoUri);
            takePictureIntent.setClipData(ClipData.newRawUri("", photoUri));
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            initialIntents.add(takePictureIntent);
        } catch (Throwable t) {
            initialIntents.add(new Intent("android.media.action.IMAGE_CAPTURE"));
        }

        initialIntents.add(new Intent("android.media.action.VIDEO_CAPTURE"));

        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("image/*");
        contentIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "image/*", "video/*" });
        contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        Intent chooser = Intent.createChooser(contentIntent, null);
        if (!initialIntents.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toArray(new Parcelable[0]));
        }
        return chooser;
    }

    public static Uri[] parseResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            cleanupCameraFile();
            return null;
        }
        if (data != null) {
            try {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    cleanupCameraFile();
                    return null;
                }
            } catch (Throwable ignored) {
            }
            Uri single = data.getData();
            if (single != null) {
                cleanupCameraFile();
                return new Uri[] { single };
            }
            String ds = data.getDataString();
            if (ds != null && !ds.isEmpty()) {
                cleanupCameraFile();
                return new Uri[] { Uri.parse(ds) };
            }
        }
        if (lastCameraFile != null && lastCameraUri != null) {
            if (lastCameraFile.exists() && lastCameraFile.length() > 0L) {
                Uri captured = lastCameraUri;
                lastCameraFile = null;
                lastCameraUri = null;
                return new Uri[] { captured };
            }
            cleanupCameraFile();
        }
        return WebChromeClient.FileChooserParams.parseResult(resultCode, data);
    }

    private static void cleanupCameraFile() {
        if (lastCameraFile != null) {
            try {
                if (lastCameraFile.exists() && lastCameraFile.length() == 0L) {
                    lastCameraFile.delete();
                }
            } catch (Throwable ignored) {
            }
            lastCameraFile = null;
        }
        lastCameraUri = null;
    }
}
