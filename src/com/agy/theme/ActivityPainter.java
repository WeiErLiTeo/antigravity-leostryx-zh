package com.agy.theme;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Paints the activity window (bars, background, system-ui flags),
 * unlocks native 120Hz/high-refresh display mode on the Window,
 * and smoothly animates theme color changes.
 */
public final class ActivityPainter implements ValueAnimator.AnimatorUpdateListener {

    private static ValueAnimator activeAnimator;
    private final Window targetWindow;

    private ActivityPainter(Window window) {
        this.targetWindow = window;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        if (targetWindow == null) {
            return;
        }
        try {
            Object val = animation.getAnimatedValue();
            if (val instanceof Integer) {
                int c = (Integer) val;
                targetWindow.setStatusBarColor(c);
                targetWindow.setNavigationBarColor(c);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void unlockHighRefreshRate(Activity activity, Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            Display display = activity.getWindowManager().getDefaultDisplay();
            if (display == null) {
                return;
            }
            Display.Mode current = display.getMode();
            Display.Mode[] modes = display.getSupportedModes();
            if (current == null || modes == null || modes.length == 0) {
                return;
            }
            Display.Mode best = current;
            for (int i = 0; i < modes.length; i++) {
                Display.Mode m = modes[i];
                if (m.getPhysicalWidth() == current.getPhysicalWidth()
                        && m.getPhysicalHeight() == current.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) {
                    best = m;
                }
            }
            WindowManager.LayoutParams lp = window.getAttributes();
            if (lp != null && (lp.preferredDisplayModeId != best.getModeId()
                    || lp.preferredRefreshRate != best.getRefreshRate())) {
                lp.preferredDisplayModeId = best.getModeId();
                lp.preferredRefreshRate = best.getRefreshRate();
                window.setAttributes(lp);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                View decor = window.getDecorView();
                if (decor != null) {
                    try {
                        java.lang.reflect.Method m = View.class.getMethod("setFrameRate", float.class, int.class);
                        m.invoke(decor, best.getRefreshRate(), 0);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void apply(Activity activity, boolean dark) {
        if (activity == null) {
            return;
        }
        int bg = dark ? ThemeColors.COLOR_DARK : ThemeColors.COLOR_LIGHT;
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        // 1. Unlock native 120Hz / highest supported refresh rate on the window
        unlockHighRefreshRate(activity, window);

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                window.setNavigationBarContrastEnforced(false);
                window.setStatusBarContrastEnforced(false);
            } catch (Throwable ignored) {
            }
        }

        // 2. Smooth color transition when switching between themes
        int oldColor = window.getStatusBarColor();
        if (activeAnimator != null) {
            try {
                activeAnimator.cancel();
            } catch (Throwable ignored) {
            }
            activeAnimator = null;
        }
        if (oldColor != 0 && oldColor != bg && Math.abs((oldColor & 0xFF) - (bg & 0xFF)) > 12) {
            try {
                ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), oldColor, bg);
                anim.setDuration(180L);
                anim.addUpdateListener(new ActivityPainter(window));
                anim.start();
                activeAnimator = anim;
            } catch (Throwable t) {
                window.setStatusBarColor(bg);
                window.setNavigationBarColor(bg);
            }
        } else {
            window.setStatusBarColor(bg);
            window.setNavigationBarColor(bg);
        }

        window.setBackgroundDrawable(new ColorDrawable(bg));
        View decor = window.getDecorView();
        decor.setBackgroundColor(bg);

        // 3. Apply light/dark system bar icons via both legacy flags and Android 11+ WindowInsetsController
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
