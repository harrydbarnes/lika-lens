package com.scope.camera;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared programmatic UI primitives and dialog helpers.
 */
final class UiKit {
    static final int ACCENT = 0xffd7b574;

    private UiKit() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context context, int color, float radiusDp, int strokeColor, int strokeWidthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(context, radiusDp));
        if (strokeWidthPx > 0) d.setStroke(strokeWidthPx, strokeColor);
        return d;
    }

    static TextView text(Context context, CharSequence value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    static TextView pill(Context context, CharSequence label, boolean active) {
        return pill(context, label, active, 8f, 18f,
                ACCENT, 0xffd8ded7, 0xff1d1d1a, 0xff202626, ACCENT, 0xff39413e);
    }

    static TextView pill(
            Context context,
            CharSequence label,
            boolean active,
            float sizeSp,
            float radiusDp,
            int activeTextColor,
            int inactiveTextColor,
            int activeBgColor,
            int inactiveBgColor,
            int activeStrokeColor,
            int inactiveStrokeColor
    ) {
        TextView view = text(context, label, sizeSp, active ? activeTextColor : inactiveTextColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(context, active ? activeBgColor : inactiveBgColor, radiusDp,
                active ? activeStrokeColor : inactiveStrokeColor, dp(context, 1)));
        return view;
    }

    static void hapticClick(Context context, View view) {
        if (view != null && context != null && AppSettings.getBoolean(context, AppSettings.HAPTICS, true)) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    @SuppressWarnings("deprecation")
    static void keepSheetAboveNavigationBar(FrameLayout scrim, View sheet) {
        if (scrim == null || sheet == null) return;
        scrim.setOnApplyWindowInsetsListener((view, insets) -> {
            int leftInset;
            int rightInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safeInsets = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                leftInset = safeInsets.left;
                rightInset = safeInsets.right;
                bottomInset = safeInsets.bottom;
            } else {
                leftInset = insets.getSystemWindowInsetLeft();
                rightInset = insets.getSystemWindowInsetRight();
                bottomInset = insets.getSystemWindowInsetBottom();
            }

            // Navigation may occupy the bottom in portrait or either side in landscape.
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams sheetParams = (FrameLayout.LayoutParams) params;
                if (sheetParams.leftMargin != leftInset
                        || sheetParams.rightMargin != rightInset
                        || sheetParams.bottomMargin != bottomInset) {
                    sheetParams.leftMargin = leftInset;
                    sheetParams.rightMargin = rightInset;
                    sheetParams.bottomMargin = bottomInset;
                    sheet.setLayoutParams(sheetParams);
                }
            }
            return insets;
        });
    }

    static Dialog createBottomSheetDialog(Activity activity, FrameLayout scrim, LinearLayout sheet, int maxWidthDp) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        scrim.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(round(activity, 0xff0e1314, 20, 0xff2a3233, dp(activity, 1)));
        sheet.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 20));

        int maxSheetWidth = Math.min(dp(activity, maxWidthDp), activity.getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                maxSheetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        scrim.addView(sheet, sheetLp);
        keepSheetAboveNavigationBar(scrim, sheet);
        dialog.setContentView(scrim);
        return dialog;
    }

    static Dialog createModalCardDialog(Activity activity, FrameLayout scrim, LinearLayout card,
                                        int maxWidthDp, float widthFraction) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        scrim.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setBackground(round(activity, 0xff0e1314, 18, 0xff2a3233, dp(activity, 1)));
        card.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 18));

        int dialogWidth = Math.min(dp(activity, maxWidthDp),
                Math.round(activity.getResources().getDisplayMetrics().widthPixels * widthFraction));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                dialogWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(card, boxLp);
        dialog.setContentView(scrim);
        return dialog;
    }

    static LinearLayout createDialogActionRow(Context context,
                                              String negativeLabel, Runnable onNegative,
                                              String positiveLabel, Runnable onPositive) {
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView neg = pill(context, negativeLabel, false, 8f, 20f,
                ACCENT, 0xffd8ded7, 0xff1d1d1a, 0xff202626, ACCENT, 0xff39413e);
        neg.setPadding(dp(context, 9), 0, dp(context, 9), 0);
        LinearLayout.LayoutParams negLp = new LinearLayout.LayoutParams(0, dp(context, 36), 1f);
        negLp.rightMargin = dp(context, 6);
        actions.addView(neg, negLp);
        if (onNegative != null) neg.setOnClickListener(v -> onNegative.run());

        TextView pos = pill(context, positiveLabel, true, 8f, 20f,
                ACCENT, 0xffd8ded7, 0xff1d1d1a, 0xff202626, ACCENT, 0xff39413e);
        pos.setPadding(dp(context, 9), 0, dp(context, 9), 0);
        LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(0, dp(context, 36), 1f);
        posLp.leftMargin = dp(context, 6);
        actions.addView(pos, posLp);
        if (onPositive != null) pos.setOnClickListener(v -> onPositive.run());
        return actions;
    }
}
