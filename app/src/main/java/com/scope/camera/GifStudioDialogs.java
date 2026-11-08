package com.scope.camera;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Leica Obsidian & Gold modal dialogs for GifStudioActivity:
 * Progress dialog with real-time percentage and export success sheet.
 */
public final class GifStudioDialogs {
    private static final int ACCENT = 0xffd7b574;
    private static final int SURFACE = 0xff141819;
    private static final int BORDER = 0xff2a3233;
    private static final int MUTED = 0xff89948e;

    private GifStudioDialogs() {}

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static TextView text(Activity activity, String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(activity);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return tv;
    }

    private static TextView pill(Activity activity, String label, boolean active) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(8);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setGravity(Gravity.CENTER);
        if (active) {
            tv.setTextColor(0xff0b0d0e);
            tv.setBackground(round(activity, ACCENT, 16, ACCENT, 0));
        } else {
            tv.setTextColor(0xffd8ded6);
            tv.setBackground(round(activity, 0x22ffffff, 16, BORDER, dp(activity, 1)));
        }
        return tv;
    }

    private static GradientDrawable round(Activity activity, int fill, int radiusDp, int strokeColor, int strokeWidthPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(activity, radiusDp));
        if (strokeWidthPx > 0) gd.setStroke(strokeWidthPx, strokeColor);
        return gd;
    }

    public static final class ProgressHandle {
        public final Dialog dialog;
        public final TextView statusView;
        public final ProgressBar progressBar;

        public ProgressHandle(Dialog dialog, TextView statusView, ProgressBar progressBar) {
            this.dialog = dialog;
            this.statusView = statusView;
            this.progressBar = progressBar;
        }

        public void update(String text, int progressPercent) {
            if (statusView != null) statusView.setText(text);
            if (progressBar != null) progressBar.setProgress(progressPercent);
        }

        public void dismiss() {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    public static ProgressHandle showExportProgressDialog(Activity activity) {
        Dialog progressDialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        progressDialog.setCancelable(false);
        FrameLayout scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(0x99000000);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(activity, SURFACE, 18, BORDER, dp(activity, 1)));
        card.setPadding(dp(activity, 24), dp(activity, 22), dp(activity, 24), dp(activity, 22));

        TextView title = text(activity, "CREATING ANIMATED GIF", 12, ACCENT, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);

        TextView status = text(activity, "Extracting frames…", 9, 0xffd8ded6, false);
        status.setPadding(0, dp(activity, 8), 0, dp(activity, 14));
        card.addView(status);

        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgress(5);
        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(activity, 8)));

        int cardW = Math.min(dp(activity, 360), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 48));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardW, -2, Gravity.CENTER);
        scrim.addView(card, cardLp);
        progressDialog.setContentView(scrim);
        progressDialog.show();

        return new ProgressHandle(progressDialog, status, progress);
    }

    public static void showExportSuccessDialog(
            Activity activity,
            Uri uri,
            String name,
            int byteSize,
            int frames,
            int fps,
            Runnable onViewAction,
            Runnable onCloseAction
    ) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(0xaa000000);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(activity, SURFACE, 20, BORDER, dp(activity, 1)));
        card.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 20));

        TextView title = text(activity, "GIF EXPORTED", 12, ACCENT, true);
        title.setLetterSpacing(0.12f);
        card.addView(title);

        String sizeStr = String.format(Locale.US, "%.1f MB", byteSize / (1024.0 * 1024.0));
        TextView info = text(activity, name + "\n" + sizeStr + " · " + frames + " frames · " + fps + " fps", 9, 0xffd8ded6, false);
        info.setPadding(0, dp(activity, 6), 0, dp(activity, 18));
        card.addView(info);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView shareBtn = pill(activity, "SHARE", false);
        actions.addView(shareBtn, new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
        shareBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/gif");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(share, "Share Lika Lens GIF"));
        });

        TextView viewBtn = pill(activity, "VIEW", true);
        LinearLayout.LayoutParams viewLp = new LinearLayout.LayoutParams(0, dp(activity, 38), 1f);
        viewLp.leftMargin = dp(activity, 8);
        actions.addView(viewBtn, viewLp);
        viewBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            if (onViewAction != null) onViewAction.run();
        });

        card.addView(actions);

        TextView done = text(activity, "CLOSE", 9, MUTED, true);
        done.setGravity(Gravity.CENTER);
        done.setPadding(0, dp(activity, 14), 0, dp(activity, 4));
        done.setOnClickListener(v -> {
            dialog.dismiss();
            if (onCloseAction != null) onCloseAction.run();
        });
        card.addView(done);

        int cardW = Math.min(dp(activity, 380), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 40));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardW, -2, Gravity.CENTER);
        scrim.addView(card, cardLp);
        dialog.setContentView(scrim);
        dialog.show();
    }
}
