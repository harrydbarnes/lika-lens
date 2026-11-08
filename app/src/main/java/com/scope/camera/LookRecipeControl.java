package com.scope.camera;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Manages look recipe adjustment controls, slider binding, value formatting,
 * and haptic feedback landmarks.
 */
public class LookRecipeControl {
    public static final int TYPE_EXPOSURE = 0;
    public static final int TYPE_WHITE_BALANCE = 1;
    public static final int TYPE_CONTRAST = 2;
    public static final int TYPE_SATURATION = 3;
    public static final int TYPE_HIGHLIGHTS = 4;
    public static final int TYPE_SHADOWS = 5;
    public static final int TYPE_GRAIN = 6;
    public static final int TYPE_HALATION = 7;
    public static final int TYPE_PIVOT = 8;
    public static final int TYPE_SHARPENING = 9;
    public static final int TYPE_CHROMA_NR = 10;
    public static final int TYPE_VIGNETTE = 11;
    public static final int TYPE_WARMTH = 12;
    public static final int TYPE_COLOR_ROLL_OFF = 13;
    public static final int TYPE_LUT_INTENSITY = 14;

    private static final int ACCENT = 0xffd7b574;

    public interface OnChangeListener {
        void onValueChanged(int type, int value, boolean fromUser, boolean isTrackingTouch);
        void onReset(int type);
    }

    public final int type;
    public final SeekBar seek;
    public final TextView valueView;
    private final int min;
    private final int max;

    public LookRecipeControl(int type, SeekBar seek, TextView valueView, int min, int max) {
        this.type = type;
        this.seek = seek;
        this.valueView = valueView;
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public static int getControlMin(int type) {
        if (type == TYPE_EXPOSURE) return -20;
        if (type == TYPE_CONTRAST || type == TYPE_SATURATION) return 50;
        return 0;
    }

    public static int getDefaultValue(int type) {
        switch (type) {
            case TYPE_EXPOSURE: return 0;
            case TYPE_WHITE_BALANCE: return 0;
            case TYPE_CONTRAST: return 100;
            case TYPE_SATURATION: return 100;
            case TYPE_HIGHLIGHTS: return 50;
            case TYPE_SHADOWS: return 50;
            case TYPE_GRAIN: return 0;
            case TYPE_HALATION: return 0;
            case TYPE_PIVOT: return 50;
            case TYPE_SHARPENING: return 50;
            case TYPE_CHROMA_NR: return 50;
            case TYPE_VIGNETTE: return 0;
            case TYPE_WARMTH: return 50;
            case TYPE_COLOR_ROLL_OFF: return 50;
            case TYPE_LUT_INTENSITY: return 100;
            default: return 0;
        }
    }

    public static boolean isLandmarkValue(int type, int selected) {
        if (type == TYPE_EXPOSURE && selected == 0) return true;
        if (type == TYPE_WHITE_BALANCE && selected == 0) return true;
        if (type == TYPE_CONTRAST && selected == 100) return true;
        if (type == TYPE_SATURATION && selected == 100) return true;
        if (type == TYPE_HIGHLIGHTS && (selected == 0 || selected == 50)) return true;
        if (type == TYPE_SHADOWS && (selected == 0 || selected == 50)) return true;
        if (type == TYPE_GRAIN && selected == 0) return true;
        if (type == TYPE_HALATION && selected == 0) return true;
        if (type == TYPE_PIVOT && selected == 50) return true;
        if (type == TYPE_SHARPENING && selected == 50) return true;
        if (type == TYPE_CHROMA_NR && selected == 50) return true;
        if (type == TYPE_VIGNETTE && selected == 0) return true;
        if (type == TYPE_WARMTH && selected == 50) return true;
        if (type == TYPE_COLOR_ROLL_OFF && selected == 50) return true;
        if (type == TYPE_LUT_INTENSITY && selected == 100) return true;
        return false;
    }

    public static String formatValue(int type, int value) {
        switch (type) {
            case TYPE_EXPOSURE:
                return String.format(Locale.US, "%+.1f EV", value / 10f);
            case TYPE_WHITE_BALANCE:
                return value == 0 ? "AUTO" : (3200 + (value - 1) * 100) + " K";
            case TYPE_CONTRAST:
            case TYPE_SATURATION:
                return String.format(Locale.US, "%.2f", value / 100f);
            case TYPE_WARMTH:
                return value == 50 ? "0" : value > 50 ? "WARM +" + (value - 50) : "COOL " + (50 - value);
            default:
                return value + "%";
        }
    }

    public static void applyToLook(RawDeveloper.Look look, int type, int value) {
        if (look == null) return;
        switch (type) {
            case TYPE_EXPOSURE: look.exposureEv = value / 10f; break;
            case TYPE_WHITE_BALANCE: look.whiteBalanceKelvin = value == 0 ? 0 : 3200 + (value - 1) * 100; break;
            case TYPE_CONTRAST: look.contrast = value / 100f; break;
            case TYPE_SATURATION: look.saturation = value / 100f; break;
            case TYPE_HIGHLIGHTS: look.highlights = value / 100f; break;
            case TYPE_SHADOWS: look.shadows = value / 100f; break;
            case TYPE_GRAIN: look.grain = value / 100f; break;
            case TYPE_HALATION: look.halation = value / 100f; break;
            case TYPE_PIVOT: look.pivot = value / 100f; break;
            case TYPE_SHARPENING: look.sharpening = value / 100f; break;
            case TYPE_CHROMA_NR: look.chromaNr = value / 100f; break;
            case TYPE_VIGNETTE: look.vignette = value / 100f; break;
            case TYPE_WARMTH: look.warmth = (value - 50) / 50f; break;
            case TYPE_COLOR_ROLL_OFF: look.colorRollOff = value / 100f; break;
            case TYPE_LUT_INTENSITY: look.lutIntensity = value / 100f; break;
            default: break;
        }
    }

    public void updateLabel(int value) {
        if (valueView != null) {
            valueView.setText(formatValue(type, value));
        }
    }

    public static View createCard(Context context, String label, String unit, int type,
                                  int min, int max, int current,
                                  OnChangeListener listener, LookRecipeControl[] outHolder) {
        float density = context.getResources().getDisplayMetrics().density;
        int p12 = Math.round(12 * density);
        int p8 = Math.round(8 * density);
        int p6 = Math.round(6 * density);
        int p2 = Math.round(2 * density);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(p12, p8, p12, p6);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xff121617);
        bg.setCornerRadius(12 * density);
        bg.setStroke(Math.round(1 * density), 0xff293031);
        card.setBackground(bg);

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(10);
        name.setTextColor(0xffe6eae4);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView resetTag = new TextView(context);
        resetTag.setText("RESET");
        resetTag.setTextSize(7);
        resetTag.setTextColor(0x66d7b574);
        resetTag.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        resetTag.setPadding(p6, p2, p6, p2);

        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(0x22d7b574);
        resetBg.setCornerRadius(8 * density);
        resetBg.setStroke(Math.round(1 * density), 0x44d7b574);
        resetTag.setBackground(resetBg);
        head.addView(resetTag);

        TextView valView = new TextView(context);
        valView.setTextSize(9);
        valView.setTextColor(ACCENT);
        valView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        valView.setPadding(p8, 0, 0, 0);
        head.addView(valView);

        card.addView(head);

        SeekBar seek = new SeekBar(context);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, current - min)));
        seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        card.addView(seek, new LinearLayout.LayoutParams(-1, Math.round(30 * density)));

        LookRecipeControl control = new LookRecipeControl(type, seek, valView, min, max);
        if (outHolder != null && outHolder.length > 0) {
            outHolder[0] = control;
        }
        control.updateLabel(current);

        View.OnClickListener resetClickListener = v -> {
            if (listener != null) listener.onReset(type);
        };
        resetTag.setOnClickListener(resetClickListener);
        valView.setOnClickListener(resetClickListener);
        head.setOnLongClickListener(v -> {
            if (listener != null) listener.onReset(type);
            return true;
        });

        final int[] lastHapticVal = {current};
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                control.updateLabel(selected);
                if (fromUser && AppSettings.getBoolean(context, AppSettings.HAPTICS, true)) {
                    if (isLandmarkValue(type, selected)) {
                        bar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        lastHapticVal[0] = selected;
                    } else if (Math.abs(selected - lastHapticVal[0]) >= 6) {
                        bar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        lastHapticVal[0] = selected;
                    }
                }
                if (listener != null) {
                    listener.onValueChanged(type, selected, fromUser, false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                if (listener != null) {
                    listener.onValueChanged(type, min + bar.getProgress(), true, true);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (listener != null) {
                    listener.onValueChanged(type, min + bar.getProgress(), true, false);
                }
            }
        });

        return card;
    }
}
