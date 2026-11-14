package com.agy.theme;

/** Resolved colors, mode constants, and live per-bar immersion state synced from WebView. */
public final class ThemeColors {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_DARK = 1;
    public static final int MODE_LIGHT = 2;

    public static int COLOR_DARK = 0xFF101010;
    public static int COLOR_LIGHT = 0xFFFFFFFF;

    public static volatile boolean HAS_CUSTOM_BARS = false;
    public static volatile int CUSTOM_STATUS_COLOR = 0xFF101010;
    public static volatile int CUSTOM_NAV_COLOR = 0xFF101010;
    public static volatile boolean CUSTOM_STATUS_DARK = true;
    public static volatile boolean CUSTOM_NAV_DARK = true;

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    public static final String BG_DARK_HEX = "#101010";
    public static final String BG_LIGHT_HEX = "#ffffff";
    public static final String FG_DARK_HEX = "#ffffff";
    public static final String FG_LIGHT_HEX = "#101010";

    private ThemeColors() {
    }
}
