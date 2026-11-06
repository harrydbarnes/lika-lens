package com.scope.camera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.transition.ChangeBounds;
import android.transition.ChangeImageTransform;
import android.transition.TransitionSet;
import android.view.Window;

import java.io.InputStream;

/** Full-screen viewer for the rendered JPEG while retaining the DNG alongside it. */
public class PhotoViewerActivity extends Activity {
    static final String EXTRA_URI = "scope_image_uri";
    static final String EXTRA_NAME = "scope_image_name";
    static final String EXTRA_FROM_LIBRARY = "scope_from_library";
    static final String EXTRA_IMPORTED = "scope_imported";
    private static final int EDIT_REQUEST = 51;
    private ImageView photoView;
    private Uri photoUri;
    private String archiveKey;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        getWindow().setStatusBarColor(0xff0b0d0e);
        getWindow().setNavigationBarColor(0xff0b0d0e);
        TransitionSet enter = new TransitionSet().addTransition(new ChangeBounds()).addTransition(new ChangeImageTransform()).setDuration(240);
        TransitionSet exit = new TransitionSet().addTransition(new ChangeBounds()).addTransition(new ChangeImageTransform()).setDuration(220);
        getWindow().setSharedElementEnterTransition(enter);
        getWindow().setSharedElementReturnTransition(exit);
        String uriValue = getIntent().getStringExtra(EXTRA_URI);
        photoUri = uriValue == null ? null : Uri.parse(uriValue);
        archiveKey = getIntent().getStringExtra(PhotoEditorActivity.EXTRA_KEY);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xff0b0d0e);
        page.setFitsSystemWindows(true);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView back = text("‹", 28, 0xffedf0e9, true);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(36), dp(40)));
        back.setOnClickListener(v -> finishAfterTransition());
        TextView name = text(getIntent().getStringExtra(EXTRA_NAME), 10, 0xffe6eae4, true);
        name.setPadding(dp(8), 0, 0, 0);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (archiveKey != null && RawArchive.exists(this, archiveKey)) {
            TextView edit = text("EDIT", 8, 0xffd7b574, true);
            edit.setGravity(Gravity.CENTER);
            edit.setPadding(dp(9), dp(7), dp(9), dp(7));
            edit.setBackground(round(0xff1d1d1a, 16));
            header.addView(edit);
            edit.setOnClickListener(v -> openEditor());
        }
        if (!getIntent().getBooleanExtra(EXTRA_FROM_LIBRARY, false)) {
            TextView library = text("GRID", 8, 0xffd7b574, true);
            library.setGravity(Gravity.CENTER);
            library.setPadding(dp(9), dp(7), dp(9), dp(7));
            library.setBackground(round(0xff1d1d1a, 16));
            header.addView(library);
            library.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        }
        page.addView(header);
        photoView = new ImageView(this);
        photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoView.setTransitionName("scope_photo");
        page.addView(photoView, new LinearLayout.LayoutParams(-1, 0, 1));
        boolean imported = getIntent().getBooleanExtra(EXTRA_IMPORTED, false);
        String footerText = imported ? "IMPORTED IMAGE  ·  SOURCE FILE LEFT IN PLACE"
                : RawArchive.exists(this, archiveKey) ? "RENDER DEVELOPED FROM SENSOR ORIGINAL  ·  DNG PRESERVED"
                : "LIKA LENS RENDER  ·  SENSOR ORIGINAL NOT AVAILABLE FOR RE-DEVELOPMENT";
        TextView footer = text(footerText,
                8, 0xff98a39c, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(16), dp(12), dp(16), dp(18));
        page.addView(footer);
        setContentView(page);
        loadPhoto();
    }

    private void openEditor() {
        Intent edit = new Intent(this, PhotoEditorActivity.class);
        edit.putExtra(PhotoEditorActivity.EXTRA_KEY, archiveKey);
        edit.putExtra(EXTRA_URI, photoUri == null ? null : photoUri.toString());
        startActivityForResult(edit, EDIT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_REQUEST && resultCode == RESULT_OK) loadPhoto();
    }

    private void loadPhoto() {
        if (photoUri == null || photoView == null) return;
        try (InputStream boundsStream = getContentResolver().openInputStream(photoUri)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            int sample = 1;
            int maxWidth = getResources().getDisplayMetrics().widthPixels * 2;
            int maxHeight = getResources().getDisplayMetrics().heightPixels * 2;
            while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream stream = getContentResolver().openInputStream(photoUri)) {
                Bitmap bitmap = stream == null ? null : BitmapFactory.decodeStream(stream, null, options);
                if (bitmap != null) photoView.setImageBitmap(bitmap);
            }
        } catch (Exception ignored) {
            android.widget.Toast.makeText(this, "Could not open this frame.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "LIKA LENS FRAME" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
