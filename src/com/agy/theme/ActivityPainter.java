package com.agy.theme;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Paints the activity window (status bar, navigation bar, decor background, icon polarity)
 * with independent status/nav colors and smooth transitions for secondary pages/modals.
 */
public final class ActivityPainter implements ValueAnimator.AnimatorUpdateListener {

    private static ValueAnimator activeAnimator;

    private final Window targetWindow;
    private final int startStatus;
    private final int targetStatus;
    private final int startNav;
    private final int targetNav;
    private final ArgbEvaluator evaluator = new ArgbEvaluator();

    private ActivityPainter(Window window, int startStatus, int targetStatus, int startNav, int targetNav) {
        this.targetWindow = window;
        this.startStatus = startStatus;
        this.targetStatus = targetStatus;
        this.startNav = startNav;
        this.targetNav = targetNav;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        if (targetWindow == null) {
            return;
        }
        try {
            float frac = animation.getAnimatedFraction();
            int s = (Integer) evaluator.evaluate(frac, startStatus, targetStatus);
            int n = (Integer) evaluator.evaluate(frac, startNav, targetNav);
            targetWindow.setStatusBarColor(s);
            targetWindow.setNavigationBarColor(n);
        } catch (Throwable ignored) {
        }
    }

    private static int maxChannelDelta(int c1, int c2) {
        int dr = Math.abs(Color.red(c1) - Color.red(c2));
        int dg = Math.abs(Color.green(c1) - Color.green(c2));
        int db = Math.abs(Color.blue(c1) - Color.blue(c2));
        return Math.max(dr, Math.max(dg, db));
    }

    public static void apply(Activity activity, boolean dark) {
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        int baseBg = dark ? ThemeColors.COLOR_DARK : ThemeColors.COLOR_LIGHT;
        int targetStatus = ThemeColors.HAS_CUSTOM_BARS ? ThemeColors.CUSTOM_STATUS_COLOR : baseBg;
        int targetNav = ThemeColors.HAS_CUSTOM_BARS ? ThemeColors.CUSTOM_NAV_COLOR : baseBg;
        boolean statusDark = ThemeColors.HAS_CUSTOM_BARS ? ThemeColors.CUSTOM_STATUS_DARK : dark;
        boolean navDark = ThemeColors.HAS_CUSTOM_BARS ? ThemeColors.CUSTOM_NAV_DARK : dark;

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

        int oldStatus = window.getStatusBarColor();
        int oldNav = window.getNavigationBarColor();

        if (activeAnimator != null) {
            try {
                activeAnimator.cancel();
            } catch (Throwable ignored) {
            }
            activeAnimator = null;
        }

        int delta = Math.max(maxChannelDelta(oldStatus, targetStatus), maxChannelDelta(oldNav, targetNav));
        if (oldStatus != 0 && oldNav != 0 && delta > 10) {
            try {
                ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(140L);
                anim.addUpdateListener(new ActivityPainter(
                        window,
                        oldStatus,
                        targetStatus,
                        oldNav,
                        targetNav
                ));
                anim.start();
                activeAnimator = anim;
            } catch (Throwable t) {
                window.setStatusBarColor(targetStatus);
                window.setNavigationBarColor(targetNav);
            }
        } else {
            window.setStatusBarColor(targetStatus);
            window.setNavigationBarColor(targetNav);
        }

        window.setBackgroundDrawable(new ColorDrawable(baseBg));
        View decor = window.getDecorView();
        if (decor != null) {
            decor.setBackgroundColor(baseBg);

            int flags = decor.getSystemUiVisibility();
            if (statusDark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (navDark) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int appearance = 0;
                    if (!statusDark) {
                        appearance |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                    }
                    if (!navDark) {
                        appearance |= WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    }
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(appearance, mask);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
