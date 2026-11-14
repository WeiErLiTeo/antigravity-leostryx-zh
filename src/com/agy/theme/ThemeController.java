package com.agy.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import com.agy.util.MainThreadPost;

/** Theme controller preserving upstream contract while adding live status/nav surface immersion. */
public class ThemeController {

    private final Context appContext;
    private Context currentContext;
    private int mode;

    public ThemeController(Context context) {
        this.appContext = context.getApplicationContext();
        this.currentContext = context;
        this.mode = ThemePrefs.load(context);
    }

    public void updateContext(Context context) {
        if (context != null) {
            this.currentContext = context;
        }
    }

    public int mode() {
        return this.mode;
    }

    public void setMode(int newMode) {
        if (newMode < ThemeColors.MODE_SYSTEM || newMode > ThemeColors.MODE_LIGHT) {
            return;
        }
        this.mode = newMode;
        ThemePrefs.save(this.appContext, newMode);
    }

    public boolean isDark() {
        Context ctx = this.currentContext != null ? this.currentContext : this.appContext;
        return isDark(ctx);
    }

    public boolean isDark(Context context) {
        Context ctx = context != null ? context : (this.currentContext != null ? this.currentContext : this.appContext);
        return ThemeResolver.isDark(this.mode, ctx);
    }

    public boolean isLauncherDark() {
        return isDark();
    }

    public String getTheme() {
        return isDark() ? ThemeColors.THEME_DARK : ThemeColors.THEME_LIGHT;
    }

    public int getBackgroundColor() {
        if (ThemeColors.HAS_CUSTOM_BARS) {
            return ThemeColors.CUSTOM_STATUS_COLOR;
        }
        return isDark() ? ThemeColors.COLOR_DARK : ThemeColors.COLOR_LIGHT;
    }

    public String getBackgroundHex() {
        return isDark() ? ThemeColors.BG_DARK_HEX : ThemeColors.BG_LIGHT_HEX;
    }

    public String getForegroundHex() {
        return isDark() ? ThemeColors.FG_DARK_HEX : ThemeColors.FG_LIGHT_HEX;
    }

    public void applyLauncherIcon() {
        Context ctx = this.currentContext != null ? this.currentContext : this.appContext;
        LauncherIconSwitcher.apply(ctx, isLauncherDark());
    }

    public boolean setTheme(String theme) {
        if (ThemeColors.THEME_LIGHT.equalsIgnoreCase(theme)) {
            setMode(ThemeColors.MODE_LIGHT);
        } else if (ThemeColors.THEME_DARK.equalsIgnoreCase(theme)) {
            setMode(ThemeColors.MODE_DARK);
        } else if ("system".equalsIgnoreCase(theme) || "default".equalsIgnoreCase(theme)) {
            setMode(ThemeColors.MODE_SYSTEM);
        } else {
            return false;
        }
        ThemeColors.HAS_CUSTOM_BARS = false;
        boolean dark = isDark();
        LauncherIconSwitcher.scheduleSwitch(dark);
        postApply(dark);
        return true;
    }

    public boolean setThemeByUser(String theme) {
        boolean ok = setTheme(theme);
        LauncherIconSwitcher.scheduleSwitch(isDark());
        return ok;
    }

    private void postApply(final boolean dark) {
        final Activity act = (this.currentContext instanceof Activity) ? (Activity) this.currentContext : null;
        if (act != null) {
            MainThreadPost.post(new Runnable() {
                @Override
                public void run() {
                    ActivityPainter.apply(act, dark);
                }
            });
        }
    }

    private static int parseColorSafe(String s, int fallback) {
        try {
            return Color.parseColor(s.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    public void onVisualThemeChanged(String arg) {
        if (arg == null) {
            return;
        }
        String trimmed = arg.trim();

        // Format 1: "bars:#RRGGBB,#RRGGBB,statusDarkBit,navDarkBit"
        if (trimmed.startsWith("bars:")) {
            try {
                String[] parts = trimmed.substring(5).split(",");
                if (parts.length >= 4) {
                    int sCol = parseColorSafe(parts[0], ThemeColors.COLOR_DARK);
                    int nCol = parseColorSafe(parts[1], sCol);
                    boolean sDark = "1".equals(parts[2].trim()) || "true".equalsIgnoreCase(parts[2].trim());
                    boolean nDark = "1".equals(parts[3].trim()) || "true".equalsIgnoreCase(parts[3].trim());
                    ThemeColors.CUSTOM_STATUS_COLOR = sCol;
                    ThemeColors.CUSTOM_NAV_COLOR = nCol;
                    ThemeColors.CUSTOM_STATUS_DARK = sDark;
                    ThemeColors.CUSTOM_NAV_DARK = nDark;
                    ThemeColors.HAS_CUSTOM_BARS = true;
                    if (sCol == nCol) {
                        if (sDark) {
                            ThemeColors.COLOR_DARK = sCol;
                        } else {
                            ThemeColors.COLOR_LIGHT = sCol;
                        }
                    }
                    postApply(sDark);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // Format 2: legacy "dark" / "light"
        if (ThemeColors.THEME_DARK.equalsIgnoreCase(trimmed) || ThemeColors.THEME_LIGHT.equalsIgnoreCase(trimmed)) {
            boolean dark = ThemeColors.THEME_DARK.equalsIgnoreCase(trimmed);
            ThemeColors.HAS_CUSTOM_BARS = false;
            setMode(dark ? ThemeColors.MODE_DARK : ThemeColors.MODE_LIGHT);
            LauncherIconSwitcher.scheduleSwitch(dark);
            postApply(dark);
            return;
        }

        // Format 3: signed packed ARGB int string (bit 0 of blue channel = isDark)
        try {
            int packed = Integer.parseInt(trimmed);
            boolean dark = (packed & 1) != 0;
            int col = packed | 0xFF000000;
            ThemeColors.CUSTOM_STATUS_COLOR = col;
            ThemeColors.CUSTOM_NAV_COLOR = col;
            ThemeColors.CUSTOM_STATUS_DARK = dark;
            ThemeColors.CUSTOM_NAV_DARK = dark;
            ThemeColors.HAS_CUSTOM_BARS = true;
            if (dark) {
                ThemeColors.COLOR_DARK = col;
            } else {
                ThemeColors.COLOR_LIGHT = col;
            }
            postApply(dark);
        } catch (Throwable ignored) {
        }
    }
}
