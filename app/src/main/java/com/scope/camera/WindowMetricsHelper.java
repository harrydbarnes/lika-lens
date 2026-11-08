package com.scope.camera;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * WindowMetricsHelper provides standard Android Adaptive Window Size Class calculations
 * and safe insets resolution across phones, foldables, tablets, and desktop/freeform windows.
 *
 * Conforms to Google's Android Adaptive Layouts and Large Screen Quality guidelines:
 * - Compact: Width < 600dp (standard portrait phones)
 * - Medium: 600dp <= Width < 840dp (small tablets, foldables unfolded, landscape phones)
 * - Expanded: Width >= 840dp (large tablets, desktop/DeX, expanded foldables)
 */
public final class WindowMetricsHelper {

    public enum WidthSizeClass {
        COMPACT,
        MEDIUM,
        EXPANDED
    }

    public enum HeightSizeClass {
        COMPACT,
        MEDIUM,
        EXPANDED
    }

    public enum DevicePosture {
        NORMAL,
        BOOK_POSTURE,
        TABLETOP
    }

    public static class WindowSizeInfo {
        public final WidthSizeClass widthClass;
        public final HeightSizeClass heightClass;
        public final int widthDp;
        public final int heightDp;
        public final boolean isLandscape;
        public final boolean isTwoPaneRecommended;

        public WindowSizeInfo(WidthSizeClass widthClass, HeightSizeClass heightClass,
                              int widthDp, int heightDp, boolean isLandscape) {
            this.widthClass = widthClass;
            this.heightClass = heightClass;
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.isLandscape = isLandscape;
            // Recommend two-pane or side-grip when screen is wide (Medium or Expanded with landscape)
            // or Expanded in any orientation (large tablets)
            this.isTwoPaneRecommended = (widthClass == WidthSizeClass.EXPANDED)
                    || (widthClass == WidthSizeClass.MEDIUM && isLandscape)
                    || (isLandscape && widthDp >= 580);
        }

        @Override
        public String toString() {
            return "WindowSizeInfo{" +
                    "widthClass=" + widthClass +
                    ", heightClass=" + heightClass +
                    ", widthDp=" + widthDp +
                    ", heightDp=" + heightDp +
                    ", isLandscape=" + isLandscape +
                    ", isTwoPaneRecommended=" + isTwoPaneRecommended +
                    '}';
        }
    }

    private WindowMetricsHelper() {}

    /**
     * Compute the current window size class and metrics for an Activity.
     */
    public static WindowSizeInfo computeWindowSizeInfo(Activity activity) {
        if (activity == null) {
            return new WindowSizeInfo(WidthSizeClass.COMPACT, HeightSizeClass.MEDIUM, 360, 640, false);
        }

        int widthDp;
        int heightDp;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager wm = activity.getSystemService(WindowManager.class);
            if (wm != null) {
                WindowMetrics metrics = wm.getCurrentWindowMetrics();
                Rect bounds = metrics.getBounds();
                float density = activity.getResources().getDisplayMetrics().density;
                if (density > 0) {
                    widthDp = (int) (bounds.width() / density);
                    heightDp = (int) (bounds.height() / density);
                } else {
                    Configuration config = activity.getResources().getConfiguration();
                    widthDp = config.screenWidthDp;
                    heightDp = config.screenHeightDp;
                }
            } else {
                Configuration config = activity.getResources().getConfiguration();
                widthDp = config.screenWidthDp;
                heightDp = config.screenHeightDp;
            }
        } else {
            Configuration config = activity.getResources().getConfiguration();
            widthDp = config.screenWidthDp;
            heightDp = config.screenHeightDp;
        }

        boolean isLandscape = widthDp > heightDp;

        WidthSizeClass widthClass;
        if (widthDp < 600) {
            widthClass = WidthSizeClass.COMPACT;
        } else if (widthDp < 840) {
            widthClass = WidthSizeClass.MEDIUM;
        } else {
            widthClass = WidthSizeClass.EXPANDED;
        }

        HeightSizeClass heightClass;
        if (heightDp < 480) {
            heightClass = HeightSizeClass.COMPACT;
        } else if (heightDp < 900) {
            heightClass = HeightSizeClass.MEDIUM;
        } else {
            heightClass = HeightSizeClass.EXPANDED;
        }

        return new WindowSizeInfo(widthClass, heightClass, widthDp, heightDp, isLandscape);
    }

    /**
     * Calculates the number of optimal gallery columns for the available window width.
     */
    public static int calculateGalleryColumns(int widthDp) {
        if (widthDp <= 0) return 3;
        // Compact phone portrait: 3 columns
        // Medium / Foldables unfolded: 4-5 columns
        // Tablets / Expanded landscape: 5-6 columns
        int columns = widthDp / 135;
        if (columns < 2) columns = 2;
        if (columns > 6) columns = 6;
        return columns;
    }

    /**
     * Safe insets helper for display cutouts, notches, and navigation bars.
     */
    public static Rect getSafeInsets(View rootView) {
        Rect insets = new Rect(0, 0, 0, 0);
        if (rootView == null) return insets;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets wi = rootView.getRootWindowInsets();
            if (wi != null) {
                Insets barInsets = wi.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insets.left = barInsets.left;
                insets.top = barInsets.top;
                insets.right = barInsets.right;
                insets.bottom = barInsets.bottom;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets wi = rootView.getRootWindowInsets();
            if (wi != null) {
                DisplayCutout cutout = wi.getDisplayCutout();
                if (cutout != null) {
                    insets.left = cutout.getSafeInsetLeft();
                    insets.top = cutout.getSafeInsetTop();
                    insets.right = cutout.getSafeInsetRight();
                    insets.bottom = cutout.getSafeInsetBottom();
                }
            }
        }
        return insets;
    }
}
