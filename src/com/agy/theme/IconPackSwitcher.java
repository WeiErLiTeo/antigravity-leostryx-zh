package com.agy.theme;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public final class IconPackSwitcher {
    private static final String[] PACKS = new String[] {
        "00_original",
        "01_owl",
        "02_lynx",
        "03_lsx",
        "04_angular_x"
    };

    private static final String[] ALIASES = new String[] {
        "com.agy.ui.launcher.LauncherOriginal",
        "com.agy.ui.launcher.LauncherOwl",
        "com.agy.ui.launcher.LauncherLynx",
        "com.agy.ui.launcher.LauncherLsx",
        "com.agy.ui.launcher.LauncherAngularX"
    };

    private IconPackSwitcher() {}

    public static void switchPack(Context context, String packId) {
        if (context == null || packId == null) return;
        int match = -1;
        for (int i = 0; i < PACKS.length; i++) {
            if (PACKS[i].equals(packId)) {
                match = i;
                break;
            }
        }
        if (match == -1) return;

        Context appCtx = context.getApplicationContext();
        PackageManager pm = appCtx.getPackageManager();
        String pkg = appCtx.getPackageName();
        try {
            for (int i = 0; i < ALIASES.length; i++) {
                ComponentName cn = new ComponentName(pkg, ALIASES[i]);
                int state = (i == match)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
            }
        } catch (Exception ignored) {
        }
    }
}
