package com.scope.camera;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared darkroom modal dialog allowing users to add, remove, reorder, and reset
 * the controls shown in the camera Adjust panel. Backed directly by AppSettings.
 */
final class AdjustControlsDialog {
    private static final int CARD_BG = 0xff161b1c;
    private static final int CARD_BORDER = 0xff293132;
    private static final int MUTED = 0xff8d9892;

    private AdjustControlsDialog() {}

    static void show(Activity activity, Runnable onChanged) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        FrameLayout scrim = new FrameLayout(activity);
        LinearLayout sheet = new LinearLayout(activity);
        Dialog dialog = UiKit.createBottomSheetDialog(activity, scrim, sheet, 440);

        View handle = new View(activity);
        handle.setBackground(UiKit.round(activity, 0xff404846, 2, 0, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                UiKit.dp(activity, 36), UiKit.dp(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = UiKit.dp(activity, 12);
        sheet.addView(handle, handleLp);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = UiKit.text(activity, "CUSTOMISE ADJUST PANEL", 10, UiKit.ACCENT, true);
        title.setLetterSpacing(.12f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView reset = UiKit.pill(activity, "RESET", false);
        reset.setPadding(UiKit.dp(activity, 10), 0, UiKit.dp(activity, 10), 0);
        header.addView(reset, new LinearLayout.LayoutParams(-2, UiKit.dp(activity, 32)));

        TextView done = UiKit.pill(activity, "DONE", true);
        done.setPadding(UiKit.dp(activity, 12), 0, UiKit.dp(activity, 12), 0);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-2, UiKit.dp(activity, 32));
        doneLp.leftMargin = UiKit.dp(activity, 6);
        header.addView(done, doneLp);
        sheet.addView(header);

        TextView subtitle = UiKit.text(activity,
                "Choose and reorder which controls appear in the camera Adjust panel.",
                8.5f, MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = UiKit.dp(activity, 4);
        subLp.bottomMargin = UiKit.dp(activity, 10);
        sheet.addView(subtitle, subLp);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));

        int maxHeight = Math.min(UiKit.dp(activity, 400),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.56f));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, maxHeight));

        final List<String> enabled = new ArrayList<>(AppSettings.getAdjustControls(activity));
        final Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            list.removeAllViews();

            TextView activeHead = UiKit.text(activity,
                    "ACTIVE IN ADJUST (" + enabled.size() + ")", 8.5f, UiKit.ACCENT, true);
            activeHead.setLetterSpacing(.12f);
            LinearLayout.LayoutParams ahLp = new LinearLayout.LayoutParams(-1, -2);
            ahLp.bottomMargin = UiKit.dp(activity, 6);
            list.addView(activeHead, ahLp);

            for (int i = 0; i < enabled.size(); i++) {
                final int index = i;
                final String key = enabled.get(i);
                LinearLayout row = buildActiveRow(activity, key, index == 0, index == enabled.size() - 1,
                        () -> {
                            if (index > 0) {
                                String moved = enabled.remove(index);
                                enabled.add(index - 1, moved);
                                AppSettings.setAdjustControls(activity, enabled);
                                if (onChanged != null) onChanged.run();
                                renderRef[0].run();
                            }
                        },
                        () -> {
                            if (index < enabled.size() - 1) {
                                String moved = enabled.remove(index);
                                enabled.add(index + 1, moved);
                                AppSettings.setAdjustControls(activity, enabled);
                                if (onChanged != null) onChanged.run();
                                renderRef[0].run();
                            }
                        },
                        () -> {
                            if (enabled.size() <= 1) {
                                Toast.makeText(activity, "Keep at least one control in Adjust.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            enabled.remove(index);
                            AppSettings.setAdjustControls(activity, enabled);
                            if (onChanged != null) onChanged.run();
                            renderRef[0].run();
                        });
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = UiKit.dp(activity, 5);
                list.addView(row, rowLp);
            }

            List<String> remaining = new ArrayList<>();
            for (String candidate : AppSettings.ALL_ADJUST_CONTROLS) {
                if (!enabled.contains(candidate)) {
                    remaining.add(candidate);
                }
            }
            if (!remaining.isEmpty()) {
                TextView availHead = UiKit.text(activity, "AVAILABLE TO ADD", 8.5f, MUTED, true);
                availHead.setLetterSpacing(.12f);
                LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(-1, -2);
                avLp.topMargin = UiKit.dp(activity, 10);
                avLp.bottomMargin = UiKit.dp(activity, 6);
                list.addView(availHead, avLp);

                for (String key : remaining) {
                    LinearLayout row = buildAvailableRow(activity, key, () -> {
                        enabled.add(key);
                        AppSettings.setAdjustControls(activity, enabled);
                        if (onChanged != null) onChanged.run();
                        renderRef[0].run();
                    });
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                    rowLp.bottomMargin = UiKit.dp(activity, 5);
                    list.addView(row, rowLp);
                }
            }
        };

        reset.setOnClickListener(v -> {
            UiKit.hapticClick(activity, v);
            AppSettings.resetAdjustControls(activity);
            enabled.clear();
            enabled.addAll(AppSettings.getAdjustControls(activity));
            if (onChanged != null) onChanged.run();
            renderRef[0].run();
            Toast.makeText(activity, "Adjust controls reset to default.", Toast.LENGTH_SHORT).show();
        });
        done.setOnClickListener(v -> {
            UiKit.hapticClick(activity, v);
            dialog.dismiss();
        });

        renderRef[0].run();
        dialog.show();
    }

    private static LinearLayout buildActiveRow(Activity activity, String key, boolean isFirst, boolean isLast,
                                               Runnable onUp, Runnable onDown, Runnable onRemove) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(activity, 10), UiKit.dp(activity, 7),
                UiKit.dp(activity, 8), UiKit.dp(activity, 7));
        row.setBackground(UiKit.round(activity, CARD_BG, 10, CARD_BORDER, UiKit.dp(activity, 1)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(activity, AppSettings.adjustControlLabel(key), 10, 0xffe6eae4, true));
        copy.addView(UiKit.text(activity, controlTypeHint(key), 7.5f, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView upBtn = actionBtn(activity, "↑", !isFirst);
        upBtn.setContentDescription("Move " + AppSettings.adjustControlLabel(key) + " up");
        upBtn.setOnClickListener(v -> {
            if (isFirst) return;
            UiKit.hapticClick(activity, v);
            onUp.run();
        });
        row.addView(upBtn, new LinearLayout.LayoutParams(UiKit.dp(activity, 32), UiKit.dp(activity, 30)));

        TextView downBtn = actionBtn(activity, "↓", !isLast);
        downBtn.setContentDescription("Move " + AppSettings.adjustControlLabel(key) + " down");
        LinearLayout.LayoutParams downLp = new LinearLayout.LayoutParams(
                UiKit.dp(activity, 32), UiKit.dp(activity, 30));
        downLp.leftMargin = UiKit.dp(activity, 4);
        downBtn.setOnClickListener(v -> {
            if (isLast) return;
            UiKit.hapticClick(activity, v);
            onDown.run();
        });
        row.addView(downBtn, downLp);

        TextView removeBtn = UiKit.pill(activity, "REMOVE", false);
        removeBtn.setTextSize(8f);
        removeBtn.setPadding(UiKit.dp(activity, 8), 0, UiKit.dp(activity, 8), 0);
        LinearLayout.LayoutParams remLp = new LinearLayout.LayoutParams(-2, UiKit.dp(activity, 30));
        remLp.leftMargin = UiKit.dp(activity, 6);
        removeBtn.setOnClickListener(v -> {
            UiKit.hapticClick(activity, v);
            onRemove.run();
        });
        row.addView(removeBtn, remLp);
        return row;
    }

    private static LinearLayout buildAvailableRow(Activity activity, String key, Runnable onAdd) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(activity, 10), UiKit.dp(activity, 7),
                UiKit.dp(activity, 8), UiKit.dp(activity, 7));
        row.setBackground(UiKit.round(activity, 0xff111516, 10, 0xff222829, UiKit.dp(activity, 1)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(UiKit.text(activity, AppSettings.adjustControlLabel(key), 10, 0xffc6cdc6, true));
        copy.addView(UiKit.text(activity, controlTypeHint(key), 7.5f, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addBtn = UiKit.pill(activity, "+ ADD", true);
        addBtn.setTextSize(8f);
        addBtn.setPadding(UiKit.dp(activity, 10), 0, UiKit.dp(activity, 10), 0);
        addBtn.setOnClickListener(v -> {
            UiKit.hapticClick(activity, v);
            onAdd.run();
        });
        row.addView(addBtn, new LinearLayout.LayoutParams(-2, UiKit.dp(activity, 30)));
        return row;
    }

    private static TextView actionBtn(Activity activity, String label, boolean enabled) {
        TextView view = UiKit.pill(activity, label, false);
        view.setTextSize(10f);
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.3f);
        return view;
    }

    private static String controlTypeHint(String key) {
        if (AppSettings.GRAIN_STRUCTURE.equals(key)
                || AppSettings.TONE_CURVE.equals(key)
                || AppSettings.SHADOW_PROFILE.equals(key)
                || AppSettings.HIGHLIGHT_RECOVERY.equals(key)) {
            return "Preset selector";
        }
        return "Slider";
    }
}
