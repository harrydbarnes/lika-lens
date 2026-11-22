package com.scope.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Date-grouped grid of Lika Lens renders with Android's document picker for imports. */
public class GalleryActivity extends Activity {
    private static final int BG = 0xff0b0d0e;
    private static final int IMPORT_REQUEST = 50;
    private ListView listView;
    private LinearLayout emptyStateView;
    private TextView emptyTitleView;
    private TextView emptyHintView;
    private GalleryRowAdapter adapter;
    private TextView countView;
    private LinearLayout filters;
    private String activeFilter = "All";
    private TextView selectionActionButton;
    private final JobState exportJob = new JobState();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "lika-gallery-loader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
    private volatile List<PhotoRepository.Photo> cachedAllPhotos = Collections.emptyList();
    private LruCache<String, Bitmap> thumbCache;
    private int savedFirstVisiblePos;
    private int savedTopOffset;
    private String lastRenderedSignature;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final Map<Uri, Integer> selectionOrderMap = new HashMap<>();
    private enum SelectionMode { BROWSING, SELECTING }
    private SelectionMode selectionMode = SelectionMode.BROWSING;
    private LinearLayout selectionBar;
    private TextView selectionCountView;

    private static final class FilteredEntry {
        final PhotoRepository.Photo photo;
        final String dayLabel;

        FilteredEntry(PhotoRepository.Photo photo, String dayLabel) {
            this.photo = photo;
            this.dayLabel = dayLabel;
        }
    }

    private static final class GridRow {
        final String headerLabel;
        final PhotoRepository.Photo[] photos;

        GridRow(String headerLabel) {
            this.headerLabel = headerLabel;
            this.photos = null;
        }

        GridRow(PhotoRepository.Photo[] photos) {
            this.headerLabel = null;
            this.photos = photos;
        }

        boolean isHeader() {
            return headerLabel != null;
        }
    }

    private static final class TileSlotHolder {
        final FrameLayout tile;
        final ImageView image;
        final TextView gifBadge;
        final TextView numBadge;

        TileSlotHolder(FrameLayout tile, ImageView image, TextView gifBadge, TextView numBadge) {
            this.tile = tile;
            this.image = image;
            this.gifBadge = gifBadge;
            this.numBadge = numBadge;
        }
    }

    private static final class RowHolder {
        final int columns;
        final TileSlotHolder[] slots;

        RowHolder(int columns, TileSlotHolder[] slots) {
            this.columns = columns;
            this.slots = slots;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        int cacheKb = Math.max(8 * 1024, maxKb / 8);
        thumbCache = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        loadPhotos();
    }

    @Override protected void onPause() {
        saveScrollPosition();
        super.onPause();
    }

    private void saveScrollPosition() {
        if (listView != null) {
            savedFirstVisiblePos = listView.getFirstVisiblePosition();
            View first = listView.getChildAt(0);
            savedTopOffset = first != null ? first.getTop() - listView.getPaddingTop() : 0;
        }
    }

    @Override
    public void onBackPressed() {
        if ((selectionMode == SelectionMode.SELECTING)) {
            exitSelectionMode();
            return;
        }
        super.onBackPressed();
    }

    private void hapticClick(View v) {
        UiKit.hapticClick(this, v);
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
            if ((selectionMode == SelectionMode.SELECTING)) exitSelectionMode();
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
        selectButton.setOnClickListener(v -> { hapticClick(v); if ((selectionMode == SelectionMode.SELECTING)) exitSelectionMode(); else enterSelectionMode(); });

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

        FrameLayout contentArea = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(new ColorDrawable(0));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setCacheColorHint(BG);
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        int horizontalPad = Math.max(dp(14), windowInfo.widthDp >= 840 ? dp(48) : dp(14));
        listView.setPadding(horizontalPad, dp(3), horizontalPad, dp((selectionMode == SelectionMode.SELECTING) ? 110 : 25));
        adapter = new GalleryRowAdapter();
        listView.setAdapter(adapter);
        contentArea.addView(listView, new FrameLayout.LayoutParams(-1, -1));

        emptyStateView = new LinearLayout(this);
        emptyStateView.setOrientation(LinearLayout.VERTICAL);
        emptyStateView.setGravity(Gravity.CENTER);
        emptyStateView.setPadding(dp(28), dp(82), dp(28), dp(40));
        emptyTitleView = text("NO PHOTOS YET", 13, 0xffd8ded6, true);
        emptyTitleView.setGravity(Gravity.CENTER);
        emptyStateView.addView(emptyTitleView);
        emptyHintView = text("Capture a photo or tap Import to start your library.", 12, 0xff89948e, false);
        emptyHintView.setGravity(Gravity.CENTER);
        emptyHintView.setPadding(0, dp(10), 0, 0);
        emptyStateView.addView(emptyHintView);
        emptyStateView.setVisibility(View.GONE);
        contentArea.addView(emptyStateView, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        page.addView(contentArea, new LinearLayout.LayoutParams(-1, 0, 1));
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
        if (listView == null) return;
        final int generation = loadGeneration.incrementAndGet();
        final String filter = activeFilter;
        final Context appContext = getApplicationContext();
        final Locale locale = Locale.getDefault();
        loadExecutor.execute(() -> {
            if (generation != loadGeneration.get()) return;
            List<PhotoRepository.Photo> allPhotos = PhotoRepository.all(appContext);
            cachedAllPhotos = allPhotos;
            List<FilteredEntry> filtered = new ArrayList<>();
            Date dateBuf = new Date();
            for (PhotoRepository.Photo photo : allPhotos) {
                if (generation != loadGeneration.get()) return;
                String key = PhotoRepository.archiveKey(photo.name);
                if ("All".equals(filter)
                        || ("RAW".equals(filter) && RawArchive.exists(appContext, key))
                        || ("Edited".equals(filter) && PhotoEditStore.hasEdits(appContext, key))
                        || ("GIFs".equals(filter) && photo.isGif)) {
                    String day;
                    synchronized (dayFormat) {
                        dateBuf.setTime(photo.timestampMillis);
                        day = dayFormat.format(dateBuf).toUpperCase(locale);
                    }
                    filtered.add(new FilteredEntry(photo, day));
                }
            }
            mainHandler.post(() -> {
                if (generation != loadGeneration.get() || isFinishing() || isDestroyed() || listView == null) return;
                renderPhotoGrid(filtered, filter, generation);
            });
        });
    }

    private static String computeGridSignature(List<FilteredEntry> entries, String filter, int columns) {
        int n = entries.size();
        if (n == 0) return filter + "|" + columns + "|0";
        PhotoRepository.Photo first = entries.get(0).photo;
        PhotoRepository.Photo last = entries.get(n - 1).photo;
        return filter + "|" + columns + "|" + n + "|" + first.uri + "|" + first.timestampMillis
                + "|" + last.uri + "|" + last.timestampMillis;
    }

    private void renderPhotoGrid(List<FilteredEntry> entries, String filter, int generation) {
        countView.setText(String.format(Locale.US, "%02d %s", entries.size(), entries.size() == 1 ? "PHOTO" : "PHOTOS"));
        WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
        int columns = WindowMetricsHelper.calculateGalleryColumns(windowInfo.widthDp);
        String signature = computeGridSignature(entries, filter, columns);
        if (signature.equals(lastRenderedSignature)) {
            return;
        }
        lastRenderedSignature = signature;

        listView.setPadding(listView.getPaddingLeft(), dp(3), listView.getPaddingRight(),
                dp((selectionMode == SelectionMode.SELECTING) ? 110 : 25));

        if (entries.isEmpty()) {
            adapter.setData(Collections.emptyList(), columns, dp(112), dp(112), generation);
            emptyTitleView.setText("All".equals(filter) ? "NO PHOTOS YET"
                    : ("GIFs".equals(filter) ? "NO GIFS YET" : "NO " + filter.toUpperCase(Locale.US) + " PHOTOS"));
            emptyHintView.setText("All".equals(filter)
                    ? "Capture a photo or tap Import to start your library."
                    : "Choose All to see the rest of your library.");
            emptyStateView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            return;
        }

        emptyStateView.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);

        int rowHeight = columns >= 5 ? dp(130) : dp(112);
        List<GridRow> rows = new ArrayList<>();
        String currentDay = "";
        List<PhotoRepository.Photo> currentRow = null;
        for (FilteredEntry entry : entries) {
            if (!entry.dayLabel.equals(currentDay)) {
                if (currentRow != null && !currentRow.isEmpty()) {
                    rows.add(new GridRow(currentRow.toArray(new PhotoRepository.Photo[0])));
                }
                currentDay = entry.dayLabel;
                rows.add(new GridRow(currentDay));
                currentRow = new ArrayList<>(columns);
            }
            if (currentRow == null || currentRow.size() == columns) {
                if (currentRow != null && !currentRow.isEmpty()) {
                    rows.add(new GridRow(currentRow.toArray(new PhotoRepository.Photo[0])));
                }
                currentRow = new ArrayList<>(columns);
            }
            currentRow.add(entry.photo);
        }
        if (currentRow != null && !currentRow.isEmpty()) {
            rows.add(new GridRow(currentRow.toArray(new PhotoRepository.Photo[0])));
        }

        adapter.setData(rows, columns, rowHeight, rowHeight, generation);
        if (savedFirstVisiblePos > 0 || savedTopOffset != 0) {
            final int pos = Math.min(savedFirstVisiblePos, Math.max(0, rows.size() - 1));
            final int off = savedTopOffset;
            listView.post(() -> {
                if (listView != null) listView.setSelectionFromTop(pos, off);
            });
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        saveScrollPosition();
        lastRenderedSignature = null;
        if (listView != null) {
            WindowMetricsHelper.WindowSizeInfo windowInfo = WindowMetricsHelper.computeWindowSizeInfo(this);
            int horizontalPad = Math.max(dp(14), windowInfo.widthDp >= 840 ? dp(48) : dp(14));
            listView.setPadding(horizontalPad, dp(3), horizontalPad, dp((selectionMode == SelectionMode.SELECTING) ? 110 : 25));
        }
        loadPhotos();
    }

    private final class GalleryRowAdapter extends BaseAdapter {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_PHOTO_ROW = 1;

        private List<GridRow> rows = Collections.emptyList();
        private int columns = 3;
        private int rowHeightPx = dp(112);
        private int thumbTargetPx = dp(112);
        private int generation = 0;

        void setData(List<GridRow> newRows, int columns, int rowHeightPx, int thumbTargetPx, int generation) {
            this.rows = newRows != null ? newRows : Collections.emptyList();
            this.columns = Math.max(1, columns);
            this.rowHeightPx = rowHeightPx;
            this.thumbTargetPx = thumbTargetPx;
            this.generation = generation;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public GridRow getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) {
            return rows.get(position).isHeader() ? TYPE_HEADER : TYPE_PHOTO_ROW;
        }
        @Override public boolean isEnabled(int position) { return false; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            GridRow item = rows.get(position);
            if (item.isHeader()) {
                TextView heading;
                if (convertView instanceof TextView) {
                    heading = (TextView) convertView;
                } else {
                    heading = text("", 11, 0xffbbc1ba, true);
                    heading.setLetterSpacing(.12f);
                    heading.setPadding(0, dp(4), 0, 0);
                    heading.setLayoutParams(new ListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
                }
                heading.setText(item.headerLabel);
                return heading;
            }

            LinearLayout rowView;
            RowHolder holder;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof RowHolder
                    && ((RowHolder) convertView.getTag()).columns == columns) {
                rowView = (LinearLayout) convertView;
                holder = (RowHolder) convertView.getTag();
            } else {
                rowView = new LinearLayout(GalleryActivity.this);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                rowView.setPadding(0, 0, 0, dp(3));
                rowView.setLayoutParams(new ListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx + dp(3)));
                TileSlotHolder[] slots = new TileSlotHolder[columns];
                for (int c = 0; c < columns; c++) {
                    FrameLayout tile = new FrameLayout(GalleryActivity.this);
                    tile.setClipToOutline(true);

                    ImageView image = new ImageView(GalleryActivity.this);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    tile.addView(image, new FrameLayout.LayoutParams(-1, -1));

                    TextView gifBadge = text("GIF", 7, 0xffd7b574, true);
                    gifBadge.setBackground(round(0xdd0b0d0e, 6, 0xffd7b574, dp(1)));
                    gifBadge.setPadding(dp(5), dp(2), dp(5), dp(2));
                    FrameLayout.LayoutParams gLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                    gLp.setMargins(0, dp(4), dp(4), 0);
                    gifBadge.setVisibility(View.GONE);
                    tile.addView(gifBadge, gLp);

                    TextView numBadge = text("", 8, 0xff0b0d0e, true);
                    numBadge.setBackground(round(0xffd7b574, 8, 0xffd7b574, 0));
                    numBadge.setGravity(Gravity.CENTER);
                    numBadge.setVisibility(View.GONE);
                    FrameLayout.LayoutParams nLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.LEFT);
                    nLp.setMargins(dp(4), dp(4), 0, 0);
                    tile.addView(numBadge, nLp);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, rowHeightPx, 1f);
                    if (c < columns - 1) lp.rightMargin = dp(3);
                    rowView.addView(tile, lp);
                    slots[c] = new TileSlotHolder(tile, image, gifBadge, numBadge);
                }
                holder = new RowHolder(columns, slots);
                rowView.setTag(holder);
            }

            boolean selecting = selectionMode == SelectionMode.SELECTING;
            int pad2 = dp(2);
            int pad1 = dp(1);
            int bindGen = generation;
            int targetPx = thumbTargetPx;
            for (int c = 0; c < columns; c++) {
                TileSlotHolder slot = holder.slots[c];
                if (item.photos == null || c >= item.photos.length) {
                    slot.tile.setVisibility(View.INVISIBLE);
                    slot.tile.setOnClickListener(null);
                    slot.tile.setOnLongClickListener(null);
                    slot.image.setTag(null);
                    slot.image.setImageDrawable(null);
                    continue;
                }
                final PhotoRepository.Photo photo = item.photos[c];
                slot.tile.setVisibility(View.VISIBLE);

                Integer order = selecting ? selectionOrderMap.get(photo.uri) : null;
                boolean isSelected = order != null;
                slot.tile.setBackground(round(0xff202626, 9, isSelected ? 0xffd7b574 : 0xff293031, isSelected ? pad2 : pad1));
                slot.gifBadge.setVisibility(photo.isGif ? View.VISIBLE : View.GONE);
                if (isSelected) {
                    slot.numBadge.setText(String.valueOf(order));
                    slot.numBadge.setVisibility(View.VISIBLE);
                } else {
                    slot.numBadge.setVisibility(View.GONE);
                }

                slot.image.setTag(photo.uri);
                String cacheKey = photo.uri.toString();
                Bitmap cached = thumbCache != null ? thumbCache.get(cacheKey) : null;
                if (cached != null && !cached.isRecycled()) {
                    slot.image.setImageBitmap(cached);
                } else {
                    slot.image.setImageDrawable(null);
                    final Context appContext = getApplicationContext();
                    final ImageView targetImage = slot.image;
                    loadExecutor.execute(() -> {
                        if (bindGen != loadGeneration.get()) return;
                        if (!photo.uri.equals(targetImage.getTag())) return;
                        Bitmap decoded = decodeThumbnail(appContext, photo.uri, targetPx);
                        if (decoded == null) return;
                        if (thumbCache != null) thumbCache.put(cacheKey, decoded);
                        mainHandler.post(() -> {
                            if (bindGen != loadGeneration.get() || isFinishing() || isDestroyed()) return;
                            if (photo.uri.equals(targetImage.getTag())) {
                                targetImage.setImageBitmap(decoded);
                            }
                        });
                    });
                }

                slot.tile.setOnClickListener(v -> {
                    hapticClick(v);
                    if (selectionMode == SelectionMode.SELECTING) {
                        if (selectedUris.contains(photo.uri)) {
                            selectedUris.remove(photo.uri);
                        } else {
                            selectedUris.add(photo.uri);
                        }
                        refreshSelectionStates();
                    } else {
                        openPhoto(photo);
                    }
                });

                slot.tile.setOnLongClickListener(v -> {
                    hapticClick(v);
                    if (selectionMode != SelectionMode.SELECTING) {
                        selectionMode = SelectionMode.SELECTING;
                        selectedUris.add(photo.uri);
                        refreshSelectionStates();
                        Toast.makeText(GalleryActivity.this, "Photo selected. Tap more photos, then choose Actions.", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
            }
            return rowView;
        }
    }

    private void refreshSelectionStates() {
        boolean selecting = selectionMode == SelectionMode.SELECTING;
        selectionOrderMap.clear();
        if (selecting) {
            for (int i = 0; i < selectedUris.size(); i++) {
                selectionOrderMap.put(selectedUris.get(i), i + 1);
            }
        }
        if (listView != null) {
            listView.setPadding(listView.getPaddingLeft(), dp(3), listView.getPaddingRight(), dp(selecting ? 110 : 25));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateSelectionBar();
    }

    private void handleGifButtonClick() {
        if ((selectionMode == SelectionMode.SELECTING) && selectedUris.size() >= 2) {
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
        selectionMode = SelectionMode.SELECTING;
        selectedUris.clear();
        refreshSelectionStates();
        Toast.makeText(this, "Select photos, then choose Actions", Toast.LENGTH_SHORT).show();
    }

    private void exitSelectionMode() {
        selectionMode = SelectionMode.BROWSING;
        selectedUris.clear();
        refreshSelectionStates();
    }

    private String exportStatusLabel = "SAVING COPIES…";

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        if (selectionActionButton != null) {
            selectionActionButton.setEnabled(!selectedUris.isEmpty() && !exportJob.isBusy());
            selectionActionButton.setAlpha(selectionActionButton.isEnabled() ? 1f : 0.45f);
        }
        if (exportJob.isBusy()) {
            selectionBar.setVisibility(View.VISIBLE);
            selectionCountView.setText(exportStatusLabel);
        } else if ((selectionMode == SelectionMode.SELECTING) && !selectedUris.isEmpty()) {
            selectionBar.setVisibility(View.VISIBLE);
            selectionCountView.setText(String.format(Locale.US, "%d selected", selectedUris.size()));
        } else if ((selectionMode == SelectionMode.SELECTING)) {
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
        ArrayList<String> uriStrings = new ArrayList<>(selectedUris.size());
        for (Uri u : selectedUris) uriStrings.add(u.toString());
        Intent intent = new Intent(this, GifStudioActivity.class);
        intent.putStringArrayListExtra(GifStudioActivity.EXTRA_PHOTO_URIS, uriStrings);
        startActivity(intent);
        exitSelectionMode();
    }

    private void confirmDeleteTile(PhotoRepository.Photo photo) {
        GalleryDialogs.showDeleteFrameDialog(this, photo, () -> {
            lastRenderedSignature = null;
            loadPhotos();
        });
    }

    private static Bitmap decodeThumbnail(Context context, Uri uri, int targetPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int minTarget = Math.max(128, targetPx);
            while (bounds.outWidth / (sample * 2) >= minTarget && bounds.outHeight / (sample * 2) >= minTarget) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
                return CaptureOrientation.orientBitmap(context, uri, decoded);
            }
        } catch (Exception e) {
            android.util.Log.w("GalleryActivity", "Failed to decode thumbnail: " + uri, e);
            return null;
        }
    }

    private void openPhoto(PhotoRepository.Photo photo) {
        Intent view = new Intent(this, PhotoViewerActivity.class);
        view.putExtra(PhotoViewerActivity.EXTRA_URI, photo.uri.toString());
        view.putExtra(PhotoViewerActivity.EXTRA_NAME, photo.name);
        view.putExtra(PhotoViewerActivity.EXTRA_FROM_LIBRARY, true);
        view.putExtra(PhotoEditorActivity.EXTRA_KEY, PhotoRepository.archiveKey(photo.name));
        startActivity(view);
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
        return UiKit.text(this, value, size, color, bold);
    }

    private TextView pill(String value, boolean active) {
        return UiKit.pill(this, value, active, 11f, 18f,
                0xffd7b574, 0xffd8ded7, 0xff1d1d1a, 0xff202626, 0xffd7b574, 0xff39413e);
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        return UiKit.round(this, color, radius, strokeColor, strokeWidth);
    }

    private void generateContactSheet() {
        List<PhotoRepository.Photo> photos = selectedPhotos();
        if (photos.isEmpty()) {
            Toast.makeText(this, "No photos to generate proof sheet", Toast.LENGTH_SHORT).show();
            return;
        }
        final long proofTicket = exportJob.tryBegin(JobState.Phase.SAVING);
        if (proofTicket == 0) return;
        exportStatusLabel = "CREATING PROOF SHEET…";
        updateSelectionBar();
        Toast.makeText(this, "Developing 35mm Darkroom Proof Sheet…", Toast.LENGTH_SHORT).show();
        ContactSheetGenerator.generate(this, photos, new ContactSheetGenerator.Callback() {
            @Override
            public void onCompleted(Uri savedUri, String fileName) {
                if (!exportJob.move(proofTicket, JobState.Phase.READY) || isDestroyed() || isFinishing()) return;
                updateSelectionBar();
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
                if (!exportJob.move(proofTicket, JobState.Phase.FAILED) || isDestroyed() || isFinishing()) return;
                updateSelectionBar();
                Toast.makeText(GalleryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showBatchPasteLookDialog() {
        final List<PhotoRepository.Photo> photos = selectedPhotos();
        if (!PhotoEditStore.hasClipboard(this)) {
            new AlertDialog.Builder(this).setTitle("No copied look")
                    .setMessage("Open a RAW photo in Edit and choose Copy look from its menu.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        final Context appContext = getApplicationContext();
        final RawDeveloper.Look copied = PhotoEditStore.pasteFromClipboard(this);
        loadExecutor.execute(() -> {
            List<PhotoRepository.Photo> editable = new ArrayList<>();
            for (PhotoRepository.Photo photo : photos) {
                if (RawArchive.exists(appContext, PhotoRepository.archiveKey(photo.name))) editable.add(photo);
            }
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (editable.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("RAW original required")
                            .setMessage("None of the selected photos has an editable RAW original.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Apply copied look?")
                        .setMessage("Save new editable copies of " + editable.size() + " selected RAW photos. Originals stay unchanged."
                                + (editable.size() < photos.size() ? " Photos without RAW data will be skipped." : ""))
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save copies", (d, w) -> applyCopiedLook(editable, copied)).show();
            });
        });
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
                savedFirstVisiblePos = 0;
                savedTopOffset = 0;
                lastRenderedSignature = null;
                refreshFilters();
                loadPhotos();
            });
        }
    }

    private List<PhotoRepository.Photo> selectedPhotos() {
        List<PhotoRepository.Photo> photos = new ArrayList<>(selectedUris.size());
        List<PhotoRepository.Photo> all = cachedAllPhotos;
        if (all.isEmpty()) all = PhotoRepository.all(this);
        for (Uri uri : selectedUris) {
            for (PhotoRepository.Photo photo : all) {
                if (photo.uri.equals(uri)) {
                    photos.add(photo);
                    break;
                }
            }
        }
        return photos;
    }

    private void showSelectionActions() {
        if (selectedUris.isEmpty() || exportJob.isBusy()) return;
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
                            .setMessage("Delete " + selectedUris.size() + " selected photos and their private editable cache? Any saved DNG originals stay untouched. This cannot be undone.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, choice) -> {
                                final List<PhotoRepository.Photo> toDelete = selectedPhotos();
                                final Context appContext = getApplicationContext();
                                exitSelectionMode();
                                loadExecutor.execute(() -> {
                                    for (PhotoRepository.Photo photo : toDelete) {
                                        PhotoRepository.delete(appContext, photo.uri, photo.name, PhotoRepository.archiveKey(photo.name));
                                    }
                                    mainHandler.post(() -> {
                                        if (!isFinishing() && !isDestroyed()) loadPhotos();
                                    });
                                });
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
        final long exportTicket = exportJob.tryBegin(JobState.Phase.SAVING);
        if (exportTicket == 0) return;
        final List<PhotoRepository.Photo> snapshot = new ArrayList<>(photos);
        final RawDeveloper.Look recipe = look.copy();
        exportStatusLabel = "SAVING COPIES…";
        updateSelectionBar();
        Context context = getApplicationContext();
        loadExecutor.execute(() -> {
            int saved = 0;
            for (PhotoRepository.Photo photo : snapshot) {
                try {
                    String key = PhotoRepository.archiveKey(photo.name);
                    PhotoCopyExporter.export(context, key, photo.uri, RawArchive.read(context, key), recipe.copy());
                    saved++;
                } catch (Exception e) { android.util.Log.e("GalleryActivity", "Could not export selected photo", e); }
            }
            final int completed = saved;
            mainHandler.post(() -> {
                if (!exportJob.move(exportTicket, JobState.Phase.READY) || isFinishing() || isDestroyed()) return;
                exitSelectionMode();
                Toast.makeText(this, "Saved " + completed + " of " + snapshot.size() + " copies. Originals unchanged.", Toast.LENGTH_LONG).show();
                loadPhotos();
            });
        });
    }

    @Override protected void onDestroy() {
        loadGeneration.incrementAndGet();
        exportJob.close();
        mainHandler.removeCallbacksAndMessages(null);
        loadExecutor.shutdownNow();
        if (thumbCache != null) thumbCache.evictAll();
        super.onDestroy();
    }

    private int dp(float value) { return UiKit.dp(this, value); }
}
