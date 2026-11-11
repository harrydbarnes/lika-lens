package com.scope.camera;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Date-grouped grid of Lika Lens renders with Android's document picker for imports. */
public class GalleryActivity extends Activity {
    private static final int BG = 0xff0b0d0e;
    private static final int IMPORT_REQUEST = 50;
    private LinearLayout list;
    private TextView countView;
    private LinearLayout filters;
    private String activeFilter = "All";
    private TextView selectionActionButton;
    private boolean exporting;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private ScrollView scroll;
    private int savedScrollY;

    private final List<Uri> selectedUris = new ArrayList<>();
    private boolean isSelectionMode = false;
    private LinearLayout selectionBar;
    private TextView selectionCountView;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        loadPhotos();
    }

    @Override protected void onPause() {
        if (scroll != null) savedScrollY = scroll.getScrollY();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode();
            return;
        }
        super.onBackPressed();
    }

    private void hapticClick(View v) {
        if (v != null && AppSettings.getBoolean(this, AppSettings.HAPTICS, true)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView back = text("‹", 29, 0xffedf0e9, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(34), dp(40)));
        back.setOnClickListener(v -> {
            hapticClick(v);
            if (isSelectionMode) exitSelectionMode();
            else finishAfterTransition();
        });

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.setPadding(dp(8), 0, 0, 0);
        title.addView(text("Library", 18, 0xfff0f2eb, true));
        countView = text("0 PHOTOS", 11, 0xff89948e, false);
        countView.setPadding(0, dp(3), 0, 0);
        title.addView(countView);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView selectButton = pill("Select", false);
        selectButton.setPadding(dp(14), 0, dp(14), 0);
        header.addView(selectButton, new LinearLayout.LayoutParams(-2, dp(44)));
        selectButton.setOnClickListener(v -> { hapticClick(v); if (isSelectionMode) exitSelectionMode(); else enterSelectionMode(); });

        TextView importButton = pill("Import", true);
        importButton.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-2, dp(44));
        importLp.leftMargin = dp(4);
        header.addView(importButton, importLp);
        importButton.setOnClickListener(v -> { hapticClick(v); chooseImport(); });
        page.addView(header);
        filters = new LinearLayout(this);
        filters.setPadding(dp(16), dp(4), dp(16), dp(10));
        refreshFilters();
        page.addView(filters);

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(3), dp(14), dp(isSelectionMode ? 110 : 25));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Floating Selection Bar
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setBackground(round(0xf0141819, 22, 0xffd7b574, dp(1)));
        selectionBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        selectionBar.setVisibility(View.GONE);

        selectionCountView = text("0 selected", 11, 0xffedf0e9, true);
        selectionCountView.setLetterSpacing(0.08f);
        selectionBar.addView(selectionCountView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView cancelBtn = pill("Cancel", false);
        cancelBtn.setPadding(dp(12), 0, dp(12), 0);
        selectionBar.addView(cancelBtn, new LinearLayout.LayoutParams(-2, dp(44)));
        cancelBtn.setOnClickListener(v -> { hapticClick(v); exitSelectionMode(); });

        selectionActionButton = pill("Actions", true);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-2, dp(44));
        actionsLp.leftMargin = dp(8);
        selectionActionButton.setPadding(dp(16), 0, dp(16), 0);
        selectionBar.addView(selectionActionButton, actionsLp);
        selectionActionButton.setOnClickListener(v -> { hapticClick(v); showSelectionActions(); });

        int maxBarW = Math.min(dp(440), getResources().getDisplayMetrics().widthPixels - dp(32));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(maxBarW, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        barLp.bottomMargin = dp(16);
        root.addView(selectionBar, barLp);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = android.os.Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                    : insets.getSystemWindowInsetBottom();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) selectionBar.getLayoutParams();
            params.bottomMargin = Math.max(dp(16), bottom + dp(12));
            selectionBar.setLayoutParams(params);
            return insets;
        });
        setContentView(root);
    }

    private void loadPhotos() {
        if (list == null) return;
        list.removeAllViews();
        list.setPadding(list.getPaddingLeft(), dp(3), list.getPaddingRight(), dp(isSelectionMode ? 110 : 25));
        List<PhotoRepository.Photo> allPhotos = PhotoRepository.all(this);
        List<PhotoRepository.Photo> photos = new ArrayList<>();
        for (PhotoRepository.Photo photo : allPhotos) {
            String key = PhotoRepository.archiveKey(photo.name);
            if ("All".equals(activeFilter)
                    || ("RAW".equals(activeFilter) && RawArchive.exists(this, key))
                    || ("Edited".equals(activeFilter) && PhotoEditStore.hasEdits(this, key))
                    || ("GIFs".equals(activeFilter) && photo.isGif)) photos.add(photo);
        }
        countView.setText(String.format(Locale.US, "%02d %s", photos.size(), photos.size() == 1 ? "PHOTO" : "PHOTOS"));
        if (photos.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(28), dp(82), dp(28), dp(40));
            TextView title = text("All".equals(activeFilter) ? "NO PHOTOS YET" : ("GIFs".equals(activeFilter) ? "NO GIFS YET" : "NO " + activeFilter.toUpperCase(Locale.US) + " PHOTOS"), 13, 0xffd8ded6, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView hint = text("All".equals(activeFilter) ? "Capture a photo or tap Import to start your library." : "Choose All to see the rest of your library.", 12, 0xff89948e, false);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(10), 0, 0);
            empty.addView(hint);
            list.addView(empty);
            scroll.post(() -> scroll.scrollTo(0, savedScrollY));
            return;
        }

        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        int columns = WindowMetricsHelper.calculateGalleryColumns(windowInfo.widthDp);
        int rowHeight = columns >= 5 ? dp(130) : dp(112);
        String currentDay = "";
        LinearLayout row = null;
        for (PhotoRepository.Photo photo : photos) {
            String day = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date(photo.timestampMillis));
            if (!day.equals(currentDay)) {
                completeRow(row, columns);
                currentDay = day;
                TextView heading = text(day.toUpperCase(Locale.getDefault()), 11, 0xffbbc1ba, true);
                heading.setLetterSpacing(.12f);
                LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, dp(30));
                headingLp.topMargin = dp(4);
                list.addView(heading, headingLp);
                row = null;
            }
            if (row == null || row.getChildCount() == columns) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, rowHeight);
                rowLp.bottomMargin = dp(3);
                list.addView(row, rowLp);
            }
            addPhotoTile(row, photo);
        }
        completeRow(row, columns);
        scroll.post(() -> scroll.scrollTo(0, savedScrollY));
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (list != null) {
            WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
            int horizontalPad = Math.max(dp(14), windowInfo.widthDp >= 840 ? dp(48) : dp(14));
            list.setPadding(horizontalPad, dp(3), horizontalPad, dp(25));
        }
        loadPhotos();
    }

    private void addPhotoTile(LinearLayout row, PhotoRepository.Photo photo) {
        FrameLayout tile = new FrameLayout(this);
        boolean isSelected = isSelectionMode && selectedUris.contains(photo.uri);
        tile.setBackground(round(0xff202626, 9, isSelected ? 0xffd7b574 : 0xff293031, isSelected ? dp(2) : dp(1)));
        tile.setClipToOutline(true);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTransitionName("scope_photo");
        Bitmap thumbnail = decodeThumbnail(photo.uri);
        if (thumbnail != null) image.setImageBitmap(thumbnail);
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));

        if (photo.isGif) {
            TextView gifBadge = text("GIF", 7, 0xffd7b574, true);
            gifBadge.setBackground(round(0xdd0b0d0e, 6, 0xffd7b574, dp(1)));
            gifBadge.setPadding(dp(5), dp(2), dp(5), dp(2));
            FrameLayout.LayoutParams gLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
            gLp.setMargins(0, dp(4), dp(4), 0);
            tile.addView(gifBadge, gLp);
        }

        if (isSelectionMode && isSelected) {
            int seqNum = selectedUris.indexOf(photo.uri) + 1;
            TextView numBadge = text(String.valueOf(seqNum), 8, 0xff0b0d0e, true);
            numBadge.setBackground(round(0xffd7b574, 8, 0xffd7b574, 0));
            numBadge.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.LEFT);
            nLp.setMargins(dp(4), dp(4), 0, 0);
            tile.addView(numBadge, nLp);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.rightMargin = dp(3);
        row.addView(tile, lp);

        tile.setOnClickListener(v -> {
            hapticClick(v);
            if (isSelectionMode) {
                if (selectedUris.contains(photo.uri)) {
                    selectedUris.remove(photo.uri);
                } else {
                    selectedUris.add(photo.uri);
                }
                updateSelectionBar();
                loadPhotos();
            } else {
                openPhoto(photo, image);
            }
        });

        tile.setOnLongClickListener(v -> {
            hapticClick(v);
            if (!isSelectionMode) {
                isSelectionMode = true;
                selectedUris.add(photo.uri);
                updateSelectionBar();
                loadPhotos();
                Toast.makeText(this, "Photo selected. Tap more photos, then choose Actions.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void handleGifButtonClick() {
        if (isSelectionMode && selectedUris.size() >= 2) {
            launchGifStudioWithSelection();
            return;
        }
        GalleryDialogs.showGifCreationDialog(
                this,
                this::enterSelectionMode,
                () -> startActivity(new Intent(this, GifStudioActivity.class))
        );
    }

    private void enterSelectionMode() {
        isSelectionMode = true;
        selectedUris.clear();
        updateSelectionBar();
        loadPhotos();
        Toast.makeText(this, "Select photos, then choose Actions", Toast.LENGTH_SHORT).show();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedUris.clear();
        updateSelectionBar();
        loadPhotos();
    }

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        if (selectionActionButton != null) {
            selectionActionButton.setEnabled(!selectedUris.isEmpty() && !exporting);
            selectionActionButton.setAlpha(selectionActionButton.isEnabled() ? 1f : 0.45f);
        }
        if (isSelectionMode && !selectedUris.isEmpty()) {
            selectionBar.setVisibility(View.VISIBLE);
            selectionCountView.setText(String.format(Locale.US, "%d selected", selectedUris.size()));
        } else if (isSelectionMode) {
            selectionBar.setVisibility(View.VISIBLE);
            selectionCountView.setText("SELECT PHOTOS");
        } else {
            selectionBar.setVisibility(View.GONE);
        }
    }

    private void launchGifStudioWithSelection() {
        if (selectedUris.size() < 2) {
            Toast.makeText(this, "Please select at least 2 frames for GIF", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> uriStrings = new ArrayList<>();
        for (Uri u : selectedUris) uriStrings.add(u.toString());
        Intent intent = new Intent(this, GifStudioActivity.class);
        intent.putStringArrayListExtra(GifStudioActivity.EXTRA_PHOTO_URIS, uriStrings);
        startActivity(intent);
        exitSelectionMode();
    }

    private void confirmDeleteTile(PhotoRepository.Photo photo) {
        GalleryDialogs.showDeleteFrameDialog(this, photo, this::loadPhotos);
    }

    private void completeRow(LinearLayout row, int columns) {
        if (row == null) return;
        while (row.getChildCount() < columns) {
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private Bitmap decodeThumbnail(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 16;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (Exception e) {
            android.util.Log.w("GalleryActivity", "Failed to decode thumbnail: " + uri, e);
            return null;
        }
    }

    private void openPhoto(PhotoRepository.Photo photo, ImageView image) {
        Intent view = new Intent(this, PhotoViewerActivity.class);
        view.putExtra(PhotoViewerActivity.EXTRA_URI, photo.uri.toString());
        view.putExtra(PhotoViewerActivity.EXTRA_NAME, photo.name);
        view.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
        view.putExtra(PhotoEditorActivity.EXTRA_KEY, PhotoRepository.archiveKey(photo.name));
        Bundle transition = ActivityOptions.makeSceneTransitionAnimation(this, image, "scope_photo").toBundle();
        startActivity(view, transition);
    }

    private void chooseImport() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, IMPORT_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            android.util.Log.w("GalleryActivity", "Could not take persistable URI permission for imported image", e);
        }
        Intent view = new Intent(this, PhotoViewerActivity.class);
        view.putExtra(PhotoViewerActivity.EXTRA_URI, uri.toString());
        view.putExtra(PhotoViewerActivity.EXTRA_NAME, "Imported image");
        view.putExtra(PhotoViewerActivity.EXTRA_IMPORTED, true);
        startActivity(view);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView pill(String value, boolean active) {
        TextView view = text(value, 11, active ? 0xffd7b574 : 0xffd8ded7, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? 0xff1d1d1a : 0xff202626);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), active ? 0xffd7b574 : 0xff39413e);
        view.setBackground(bg);
        return view;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void generateContactSheet() {
        List<PhotoRepository.Photo> photos = selectedPhotos();
        if (photos.isEmpty()) {
            Toast.makeText(this, "No photos to generate proof sheet", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Developing 35mm Darkroom Proof Sheet…", Toast.LENGTH_SHORT).show();
        ContactSheetGenerator.generate(this, photos, new ContactSheetGenerator.Callback() {
            @Override
            public void onCompleted(Uri savedUri, String fileName) {
                Toast.makeText(GalleryActivity.this, "Proof sheet saved to Pictures/Lika Lens", Toast.LENGTH_LONG).show();
                loadPhotos();
                Intent view = new Intent(GalleryActivity.this, PhotoViewerActivity.class);
                view.putExtra(PhotoViewerActivity.EXTRA_URI, savedUri.toString());
                view.putExtra(PhotoViewerActivity.EXTRA_NAME, fileName);
                view.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
                startActivity(view);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(GalleryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showBatchPasteLookDialog() {
        List<PhotoRepository.Photo> photos = selectedPhotos();
        List<PhotoRepository.Photo> editable = new ArrayList<>();
        for (PhotoRepository.Photo photo : photos) {
            if (RawArchive.exists(this, PhotoRepository.archiveKey(photo.name))) editable.add(photo);
        }
        if (!PhotoEditStore.hasClipboard(this)) {
            new AlertDialog.Builder(this).setTitle("No copied look")
                    .setMessage("Open a RAW photo in Edit and choose Copy look from its menu.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (editable.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("RAW original required")
                    .setMessage("None of the selected photos has an editable RAW original.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        RawDeveloper.Look copied = PhotoEditStore.pasteFromClipboard(this);
        new AlertDialog.Builder(this).setTitle("Apply copied look?")
                .setMessage("Save new editable copies of " + editable.size() + " selected RAW photos. Originals stay unchanged."
                        + (editable.size() < photos.size() ? " Photos without RAW data will be skipped." : ""))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save copies", (d, w) -> applyCopiedLook(editable, copied)).show();
    }

    private void refreshFilters() {
        filters.removeAllViews();
        for (String name : new String[]{"All", "RAW", "Edited", "GIFs"}) {
            TextView filter = pill(name, name.equals(activeFilter));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (filters.getChildCount() > 0) lp.leftMargin = dp(6);
            filters.addView(filter, lp);
            filter.setOnClickListener(v -> {
                hapticClick(v);
                activeFilter = name;
                savedScrollY = 0;
                refreshFilters();
                loadPhotos();
            });
        }
    }

    private List<PhotoRepository.Photo> selectedPhotos() {
        List<PhotoRepository.Photo> photos = new ArrayList<>();
        List<PhotoRepository.Photo> all = PhotoRepository.all(this);
        for (Uri uri : selectedUris) {
            for (PhotoRepository.Photo photo : all) if (photo.uri.equals(uri)) { photos.add(photo); break; }
        }
        return photos;
    }

    private void showSelectionActions() {
        if (selectedUris.isEmpty() || exporting) return;
        new AlertDialog.Builder(this).setTitle(selectedUris.size() + " photos selected")
                .setItems(new String[]{"Share", "Apply copied look…", "Create GIF…", "Proof sheet…", "Video to GIF…", "Delete selected…"}, (d, which) -> {
                    if (which == 0) shareSelection();
                    else if (which == 1) showBatchPasteLookDialog();
                    else if (which == 2) launchGifStudioWithSelection();
                    else if (which == 3) generateContactSheet();
                    else if (which == 4) {
                        Intent video = new Intent(this, GifStudioActivity.class);
                        video.putExtra(GifStudioActivity.EXTRA_VIDEO_URI, "");
                        startActivity(video);
                    } else new AlertDialog.Builder(this).setTitle("Delete selected photos?")
                            .setMessage("Delete " + selectedUris.size() + " selected photos and their RAW originals? This cannot be undone.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, choice) -> {
                                for (PhotoRepository.Photo photo : selectedPhotos())
                                    PhotoRepository.delete(this, photo.uri, photo.name, PhotoRepository.archiveKey(photo.name));
                                exitSelectionMode();
                            }).show();
                }).show();
    }

    private void shareSelection() {
        Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
        share.setType("image/*");
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(selectedUris));
        android.content.ClipData clip = android.content.ClipData.newUri(getContentResolver(), "Lika Lens", selectedUris.get(0));
        for (int i = 1; i < selectedUris.size(); i++) clip.addItem(new android.content.ClipData.Item(selectedUris.get(i)));
        share.setClipData(clip);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share photos"));
    }

    private void applyCopiedLook(List<PhotoRepository.Photo> photos, RawDeveloper.Look look) {
        exporting = true;
        updateSelectionBar();
        selectionCountView.setText("SAVING COPIES…");
        android.content.Context context = getApplicationContext();
        new Thread(() -> {
            int saved = 0;
            for (PhotoRepository.Photo photo : photos) {
                try {
                    String key = PhotoRepository.archiveKey(photo.name);
                    PhotoCopyExporter.export(context, key, RawArchive.read(context, key), look.copy());
                    saved++;
                } catch (Exception e) { android.util.Log.e("GalleryActivity", "Could not export selected photo", e); }
            }
            final int completed = saved;
            mainHandler.post(() -> {
                exporting = false;
                if (isFinishing() || isDestroyed()) return;
                exitSelectionMode();
                Toast.makeText(this, "Saved " + completed + " of " + photos.size() + " copies. Originals unchanged.", Toast.LENGTH_LONG).show();
            });
        }, "LibraryLookExport").start();
    }


    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
