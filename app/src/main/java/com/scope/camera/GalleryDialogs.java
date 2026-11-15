package com.scope.camera;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Leica Obsidian & Gold modal dialogs for GalleryActivity:
 * GIF mode selector and frame deletion sheet.
 */
public final class GalleryDialogs {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lika-gallery-dialog-io");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GalleryDialogs() {}

    public static void showGifCreationDialog(
            Activity activity,
            Runnable onSelectFrames,
            Runnable onLaunchStudio
    ) {
        FrameLayout scrim = new FrameLayout(activity);
        LinearLayout sheet = new LinearLayout(activity);
        Dialog dialog = UiKit.createBottomSheetDialog(activity, scrim, sheet, 440);

        TextView title = UiKit.text(activity, "ANIMATED GIF CREATOR", 11, UiKit.ACCENT, true);
        title.setLetterSpacing(.12f);
        sheet.addView(title);

        TextView sub = UiKit.text(activity, "Create stop-motion, burst loops, or video GIFs with Leica film grading.", 9, 0xff9aa49e, false);
        sub.setPadding(0, UiKit.dp(activity, 6), 0, UiKit.dp(activity, 14));
        sheet.addView(sub);

        TextView fromPhotos = UiKit.pill(activity, "SELECT FRAMES FROM LIBRARY", true);
        sheet.addView(fromPhotos, new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 40)));
        fromPhotos.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSelectFrames != null) onSelectFrames.run();
        });

        TextView fromVideo = UiKit.pill(activity, "CONVERT VIDEO TO GIF", false);
        LinearLayout.LayoutParams fvLp = new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 40));
        fvLp.topMargin = UiKit.dp(activity, 8);
        sheet.addView(fromVideo, fvLp);
        fromVideo.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(activity, GifStudioActivity.class);
            intent.putExtra(GifStudioActivity.EXTRA_VIDEO_URI, "");
            activity.startActivity(intent);
        });

        TextView openStudio = UiKit.pill(activity, "OPEN GIF STUDIO", false);
        LinearLayout.LayoutParams osLp = new LinearLayout.LayoutParams(-1, UiKit.dp(activity, 40));
        osLp.topMargin = UiKit.dp(activity, 8);
        sheet.addView(openStudio, osLp);
        openStudio.setOnClickListener(v -> {
            dialog.dismiss();
            if (onLaunchStudio != null) onLaunchStudio.run();
            else activity.startActivity(new Intent(activity, GifStudioActivity.class));
        });

        dialog.show();
    }

    public static void showDeleteFrameDialog(
            Activity activity,
            PhotoRepository.Photo photo,
            Runnable onDeleted
    ) {
        FrameLayout scrim = new FrameLayout(activity);
        LinearLayout sheet = new LinearLayout(activity);
        Dialog dialog = UiKit.createBottomSheetDialog(activity, scrim, sheet, 440);

        TextView title = UiKit.text(activity, "DELETE FRAME?", 11, UiKit.ACCENT, true);
        title.setLetterSpacing(.12f);
        sheet.addView(title);

        TextView sub = UiKit.text(activity, "Removes " + photo.name + " and its associated RAW archive.", 9, 0xff9aa49e, false);
        sub.setPadding(0, UiKit.dp(activity, 6), 0, UiKit.dp(activity, 16));
        sheet.addView(sub);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = UiKit.pill(activity, "CANCEL", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, UiKit.dp(activity, 38), 1f));
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView del = UiKit.pill(activity, "DELETE", true);
        del.setTextColor(0xfff0443c);
        del.setBackground(UiKit.round(activity, 0x22f0443c, 18, 0xfff0443c, UiKit.dp(activity, 1)));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, UiKit.dp(activity, 38), 1f);
        delLp.leftMargin = UiKit.dp(activity, 8);
        actions.addView(del, delLp);
        del.setOnClickListener(v -> {
            del.setEnabled(false);
            cancel.setEnabled(false);
            Context appContext = activity.getApplicationContext();
            String key = PhotoRepository.archiveKey(photo.name);
            IO.execute(() -> {
                PhotoRepository.delete(appContext, photo.uri, photo.name, key);
                MAIN.post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        dialog.dismiss();
                        if (onDeleted != null) onDeleted.run();
                    }
                });
            });
        });
        sheet.addView(actions);

        dialog.show();
    }
}
