package com.scope.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Leica Obsidian & Gold modal dialogs for GalleryActivity:
 * GIF mode selector, frame deletion sheet, and batch look recipe paste.
 */
public final class GalleryDialogs {

    private GalleryDialogs() {}

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static TextView text(Activity activity, String value, float size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static TextView pill(Activity activity, String value, boolean active) {
        TextView view = text(activity, value, 8, active ? 0xffd7b574 : 0xffd8ded7, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? 0xff1d1d1a : 0xff202626);
        bg.setCornerRadius(dp(activity, 18));
        bg.setStroke(dp(activity, 1), active ? 0xffd7b574 : 0xff39413e);
        view.setBackground(bg);
        return view;
    }

    private static GradientDrawable round(Activity activity, int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(activity, radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    public static void showGifCreationDialog(
            Activity activity,
            Runnable onSelectFrames,
            Runnable onLaunchStudio
    ) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(round(activity, 0xff0e1314, 20, 0xff2a3233, dp(activity, 1)));
        sheet.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 20));

        TextView title = text(activity, "ANIMATED GIF CREATOR", 11, 0xffd7b574, true);
        title.setLetterSpacing(.12f);
        sheet.addView(title);

        TextView sub = text(activity, "Create stop-motion, burst loops, or video GIFs with Leica film grading.", 9, 0xff9aa49e, false);
        sub.setPadding(0, dp(activity, 6), 0, dp(activity, 14));
        sheet.addView(sub);

        TextView fromPhotos = pill(activity, "SELECT FRAMES FROM LIBRARY", true);
        sheet.addView(fromPhotos, new LinearLayout.LayoutParams(-1, dp(activity, 40)));
        fromPhotos.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSelectFrames != null) onSelectFrames.run();
        });

        TextView fromVideo = pill(activity, "CONVERT VIDEO TO GIF", false);
        LinearLayout.LayoutParams fvLp = new LinearLayout.LayoutParams(-1, dp(activity, 40));
        fvLp.topMargin = dp(activity, 8);
        sheet.addView(fromVideo, fvLp);
        fromVideo.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(activity, GifStudioActivity.class);
            intent.putExtra(GifStudioActivity.EXTRA_VIDEO_URI, "");
            activity.startActivity(intent);
        });

        TextView openStudio = pill(activity, "OPEN GIF STUDIO", false);
        LinearLayout.LayoutParams osLp = new LinearLayout.LayoutParams(-1, dp(activity, 40));
        osLp.topMargin = dp(activity, 8);
        sheet.addView(openStudio, osLp);
        openStudio.setOnClickListener(v -> {
            dialog.dismiss();
            if (onLaunchStudio != null) onLaunchStudio.run();
            else activity.startActivity(new Intent(activity, GifStudioActivity.class));
        });

        int maxSheetWidth = Math.min(dp(activity, 440), activity.getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                maxSheetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        scrim.addView(sheet, sheetLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    public static void showDeleteFrameDialog(
            Activity activity,
            PhotoRepository.Photo photo,
            Runnable onDeleted
    ) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        FrameLayout scrim = new FrameLayout(activity);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x77000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setBackground(round(activity, 0xff0e1314, 20, 0xff2a3233, dp(activity, 1)));
        sheet.setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 20));

        TextView title = text(activity, "DELETE FRAME?", 11, 0xffd7b574, true);
        title.setLetterSpacing(.12f);
        sheet.addView(title);

        TextView sub = text(activity, "Removes " + photo.name + " and its associated RAW archive.", 9, 0xff9aa49e, false);
        sub.setPadding(0, dp(activity, 6), 0, dp(activity, 16));
        sheet.addView(sub);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill(activity, "CANCEL", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView del = pill(activity, "DELETE", true);
        del.setTextColor(0xfff0443c);
        del.setBackground(round(activity, 0x22f0443c, 18, 0xfff0443c, dp(activity, 1)));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, dp(activity, 38), 1f);
        delLp.leftMargin = dp(activity, 8);
        actions.addView(del, delLp);
        del.setOnClickListener(v -> {
            PhotoRepository.delete(activity, photo.uri, photo.name, PhotoRepository.archiveKey(photo.name));
            dialog.dismiss();
            if (onDeleted != null) onDeleted.run();
        });
        sheet.addView(actions);

        int maxSheetWidth = Math.min(dp(activity, 440), activity.getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                maxSheetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        scrim.addView(sheet, sheetLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    public static void showBatchPasteLookDialog(Activity activity, Runnable onApplied) {
        if (!PhotoEditStore.hasClipboard(activity)) {
            Toast.makeText(activity, "No copied recipe yet. Open DEVELOP on a photo and tap COPY.", Toast.LENGTH_SHORT).show();
            return;
        }
        RawDeveloper.Look copied = PhotoEditStore.pasteFromClipboard(activity);
        if (copied == null) return;

        new AlertDialog.Builder(activity)
                .setTitle("PASTE LOOK RECIPE")
                .setMessage("Brain: " + copied.brain + "\nApply this graded recipe to:")
                .setPositiveButton("All Editable RAW Photos", (dialog, which) -> {
                    List<PhotoRepository.Photo> photos = PhotoRepository.all(activity);
                    int count = 0;
                    for (PhotoRepository.Photo p : photos) {
                        String key = PhotoRepository.archiveKey(p.name);
                        if (RawArchive.file(activity, key).exists()) {
                            PhotoEditStore.save(activity, key, copied.copy());
                            count++;
                        }
                    }
                    Toast.makeText(activity, "Recipe applied to " + count + " RAW frame(s)", Toast.LENGTH_SHORT).show();
                    if (onApplied != null) onApplied.run();
                })
                .setNeutralButton("Latest Photo Only", (dialog, which) -> {
                    PhotoRepository.Photo latest = PhotoRepository.latest(activity);
                    if (latest != null) {
                        String key = PhotoRepository.archiveKey(latest.name);
                        PhotoEditStore.save(activity, key, copied.copy());
                        Toast.makeText(activity, "Recipe applied to " + latest.name, Toast.LENGTH_SHORT).show();
                        if (onApplied != null) onApplied.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
