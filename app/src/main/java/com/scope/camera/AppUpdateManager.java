package com.scope.camera;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages GitHub Releases update checking, changelog parsing, background APK downloading,
 * and seamless package installer invocation for both Beta and Stable release channels.
 */
public final class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    public static final String GITHUB_RELEASES_API = "https://api.github.com/repos/harrydbarnes/lika-lens/releases";

    private static final int ACCENT = 0xffd7b574;
    private static final int BG_DIALOG = 0xff0e1314;
    private static final int BORDER_CARD = 0xff2a3233;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppUpdateManager() {}

    public static final class UpdateInfo {
        public final String tagName;
        public final String title;
        public final String changelog;
        public final boolean isBeta;
        public final String apkDownloadUrl;
        public final String apkName;
        public final long apkSize;
        public final String publishedAt;

        public UpdateInfo(String tagName, String title, String changelog, boolean isBeta,
                          String apkDownloadUrl, String apkName, long apkSize, String publishedAt) {
            this.tagName = tagName;
            this.title = title;
            this.changelog = changelog;
            this.isBeta = isBeta;
            this.apkDownloadUrl = apkDownloadUrl;
            this.apkName = apkName;
            this.apkSize = apkSize;
            this.publishedAt = publishedAt;
        }
    }

    public interface UpdateCallback {
        void onUpdateAvailable(UpdateInfo updateInfo);
        void onNoUpdateAvailable();
        void onError(String message);
    }

    public interface DownloadProgressListener {
        void onProgress(int percent, long bytesDownloaded, long totalBytes);
        void onComplete(File apkFile);
        void onError(String message);
    }

    /**
     * Compares remote release tag string against current version and version code.
     * Returns true if remote release is strictly newer.
     */
    public static boolean isNewerVersion(String remoteTag, String currentVersionName, int currentVersionCode) {
        if (remoteTag == null || remoteTag.trim().isEmpty()) return false;
        String curVer = currentVersionName == null ? "0.0.0" : currentVersionName.trim();

        String cleanRemote = remoteTag.trim().toLowerCase(Locale.US);
        if (cleanRemote.startsWith("v")) cleanRemote = cleanRemote.substring(1);
        String cleanCurrent = curVer.toLowerCase(Locale.US);
        if (cleanCurrent.startsWith("v")) cleanCurrent = cleanCurrent.substring(1);

        // Exact match check
        if (cleanRemote.equals(cleanCurrent)) return false;

        String[] remoteMainAndSuffix = cleanRemote.split("[-_]");
        String[] currentMainAndSuffix = cleanCurrent.split("[-_]");

        String[] remoteParts = remoteMainAndSuffix[0].split("\\.");
        String[] currentParts = currentMainAndSuffix[0].split("\\.");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < remoteParts.length ? parseSafeInt(remoteParts[i]) : 0;
            int c = i < currentParts.length ? parseSafeInt(currentParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }

        // If numerical segments match, compare build numbers if remote has a run/build indicator
        int remoteRunNum = extractTrailingNumber(cleanRemote);
        if (remoteRunNum > 0 && currentVersionCode > 0 && remoteRunNum > currentVersionCode) {
            return true;
        }

        return false;
    }

    private static int parseSafeInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int extractTrailingNumber(String s) {
        if (s == null) return 0;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
            int lastNum = 0;
            while (m.find()) {
                lastNum = Integer.parseInt(m.group(1));
            }
            return lastNum;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parses GitHub Releases JSON array for the newest available update matching channel.
     */
    public static UpdateInfo parseLatestRelease(String jsonString, String channel,
                                                String currentVersionName, int currentVersionCode) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            JSONArray releases = new JSONArray(jsonString);
            boolean wantBeta = channel != null && (channel.contains("Beta") || channel.equalsIgnoreCase("beta"));

            for (int i = 0; i < releases.length(); i++) {
                JSONObject rel = releases.getJSONObject(i);
                boolean isPrerelease = rel.optBoolean("prerelease", false);
                boolean isDraft = rel.optBoolean("draft", false);
                if (isDraft) continue;

                // If user selected Stable channel, ignore beta / prereleases
                if (!wantBeta && isPrerelease) continue;

                String tagName = rel.optString("tag_name", "");
                String title = rel.optString("name", tagName);
                String body = rel.optString("body", "Continuous integration build.");
                String publishedAt = rel.optString("published_at", "");

                // Find APK asset
                JSONArray assets = rel.optJSONArray("assets");
                String apkUrl = null;
                String apkName = null;
                long apkSize = 0L;
                if (assets != null) {
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase(Locale.US).endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            apkName = name;
                            apkSize = asset.optLong("size", 0L);
                            break;
                        }
                    }
                }

                if (apkUrl != null && isNewerVersion(tagName, currentVersionName, currentVersionCode)) {
                    return new UpdateInfo(tagName, title, body, isPrerelease, apkUrl, apkName, apkSize, publishedAt);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing releases JSON", e);
        }
        return null;
    }

    /**
     * Asynchronously checks GitHub Releases for an available update.
     */
    public static void checkForUpdate(Context context, boolean isManual, UpdateCallback callback) {
        String channel = AppSettings.get(context, AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_BETA);
        String currentVersionName = getAppVersionName(context);
        int currentVersionCode = getAppVersionCode(context);

        worker.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_RELEASES_API);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "LikaLens-Android");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                    UpdateInfo info = parseLatestRelease(sb.toString(), channel, currentVersionName, currentVersionCode);
                    AppSettings.prefs(context).edit().putLong(AppSettings.LAST_UPDATE_CHECK_TIME, System.currentTimeMillis()).apply();

                    mainHandler.post(() -> {
                        if (info != null) {
                            if (callback != null) callback.onUpdateAvailable(info);
                        } else {
                            if (callback != null) callback.onNoUpdateAvailable();
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("GitHub API HTTP " + code);
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Update check failed", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "Network error");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Downloads APK file in background with progress and prompts installation.
     */
    public static void downloadAndInstall(Activity activity, UpdateInfo info, DownloadProgressListener listener) {
        worker.execute(() -> {
            File updateDir = new File(activity.getCacheDir(), "updates");
            if (!updateDir.exists()) updateDir.mkdirs();
            File destFile = new File(updateDir, "lika-lens-update.apk");

            HttpURLConnection conn = null;
            try {
                URL url = new URL(info.apkDownloadUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "LikaLens-Android");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int responseCode = conn.getResponseCode();
                int redirects = 0;
                // Follow redirect chain (e.g. github.com -> objects.githubusercontent.com AWS S3 signed URL)
                while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == 307
                        || responseCode == 308) && redirects < 5) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl == null) break;
                    conn.disconnect();
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "LikaLens-Android");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    responseCode = conn.getResponseCode();
                    redirects++;
                }

                long totalBytes = conn.getContentLengthLong();
                if (totalBytes <= 0) totalBytes = info.apkSize;

                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[32 * 1024];
                    long downloaded = 0;
                    int count;
                    long lastProgressUpdate = 0;

                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        downloaded += count;
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate > 100 || downloaded == totalBytes) {
                            lastProgressUpdate = now;
                            final int percent = totalBytes > 0 ? (int) ((downloaded * 100) / totalBytes) : -1;
                            final long finalDownloaded = downloaded;
                            final long finalTotal = totalBytes;
                            mainHandler.post(() -> {
                                if (listener != null) listener.onProgress(percent, finalDownloaded, finalTotal);
                            });
                        }
                    }
                    output.flush();
                }

                mainHandler.post(() -> {
                    if (listener != null) listener.onComplete(destFile);
                    promptInstall(activity, destFile);
                });
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                mainHandler.post(() -> {
                    if (listener != null) listener.onError("Download failed: " + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Prompts the Android Package Installer for the specified APK file.
     */
    public static void promptInstall(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) return;
        if (activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        // Check for Unknown App Sources permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Please allow Lika Lens to install updates", Toast.LENGTH_LONG).show();
                Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                permissionIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permissionIntent);
                return;
            }
        }

        try {
            Uri apkUri = ApkFileProvider.getUriForFile(activity, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start package installer", e);
            Toast.makeText(activity, "Could not open installer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Displays a Leica Obsidian & Gold update notification dialog with changelog and one-tap install.
     */
    public static void showUpdateDialog(Activity activity, UpdateInfo info) {
        if (activity == null || activity.isFinishing() || info == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int dp20 = Math.round(20 * density);
        int dp16 = Math.round(16 * density);
        int dp14 = Math.round(14 * density);
        int dp12 = Math.round(12 * density);
        int dp8 = Math.round(8 * density);
        int dp6 = Math.round(6 * density);
        int dp4 = Math.round(4 * density);

        FrameLayout scrim = new FrameLayout(activity);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(0x88000000);
        scrim.setOnClickListener(v -> dialog.dismiss());

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);

        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(BG_DIALOG);
        sheetBg.setCornerRadius(20 * density);
        sheetBg.setStroke(Math.round(1 * density), BORDER_CARD);
        sheet.setBackground(sheetBg);
        sheet.setPadding(dp20, dp16, dp20, dp20);

        // Header Title
        TextView header = new TextView(activity);
        header.setText("LIKA LENS UPDATE AVAILABLE");
        header.setTextSize(11);
        header.setTextColor(ACCENT);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(.14f);
        sheet.addView(header);

        // Version & Channel Tag Row
        LinearLayout tagRow = new LinearLayout(activity);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        tagRow.setPadding(0, dp6, 0, dp12);

        String tagText = info.tagName + (info.isBeta ? " · BETA CHANNEL" : " · STABLE RELEASE");
        TextView tagView = new TextView(activity);
        tagView.setText(tagText);
        tagView.setTextSize(9);
        tagView.setTextColor(0xfff0f2eb);
        tagView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tagRow.addView(tagView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (info.apkSize > 0) {
            String sizeStr = String.format(Locale.US, "%.1f MB", info.apkSize / (1024f * 1024f));
            TextView sizeView = new TextView(activity);
            sizeView.setText(sizeStr);
            sizeView.setTextSize(8);
            sizeView.setTextColor(0xff9ba59e);
            sizeView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
            tagRow.addView(sizeView);
        }
        sheet.addView(tagRow);

        // Changelog Container
        TextView changelogHeader = new TextView(activity);
        changelogHeader.setText("CHANGELOG / RELEASE NOTES:");
        changelogHeader.setTextSize(8);
        changelogHeader.setTextColor(ACCENT);
        changelogHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        changelogHeader.setLetterSpacing(.12f);
        sheet.addView(changelogHeader);

        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(0xff14191a);
        scroll.setPadding(dp12, dp8, dp12, dp8);

        TextView changelogText = new TextView(activity);
        changelogText.setText(info.changelog != null && !info.changelog.isEmpty() ? info.changelog : "Continuous integration update.");
        changelogText.setTextSize(8.5f);
        changelogText.setTextColor(0xffd8ded7);
        changelogText.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        changelogText.setLineSpacing(dp4, 1.15f);
        scroll.addView(changelogText);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, Math.round(140 * density));
        scrollLp.topMargin = dp6;
        scrollLp.bottomMargin = dp16;
        sheet.addView(scroll, scrollLp);

        // Progress bar container (initially hidden)
        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        sheet.addView(progressBar, new LinearLayout.LayoutParams(-1, dp8));

        TextView progressText = new TextView(activity);
        progressText.setTextSize(8);
        progressText.setTextColor(ACCENT);
        progressText.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        progressText.setGravity(Gravity.CENTER);
        progressText.setVisibility(View.GONE);
        sheet.addView(progressText, new LinearLayout.LayoutParams(-1, -2));

        // Action Buttons Row
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView laterBtn = new TextView(activity);
        laterBtn.setText("NOT NOW");
        laterBtn.setTextSize(8);
        laterBtn.setTextColor(0xffd8ded7);
        laterBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        laterBtn.setGravity(Gravity.CENTER);

        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setColor(0xd8191e1e);
        laterBg.setCornerRadius(16 * density);
        laterBg.setStroke(Math.round(1 * density), 0x556e7872);
        laterBtn.setBackground(laterBg);

        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(0, Math.round(38 * density), 1f);
        laterLp.rightMargin = dp6;
        actions.addView(laterBtn, laterLp);
        laterBtn.setOnClickListener(v -> dialog.dismiss());

        TextView installBtn = new TextView(activity);
        installBtn.setText("DOWNLOAD & INSTALL");
        installBtn.setTextSize(8);
        installBtn.setTextColor(0xff0b0d0e);
        installBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        installBtn.setGravity(Gravity.CENTER);

        GradientDrawable installBg = new GradientDrawable();
        installBg.setColor(ACCENT);
        installBg.setCornerRadius(16 * density);
        installBtn.setBackground(installBg);

        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(0, Math.round(38 * density), 1.5f);
        installLp.leftMargin = dp6;
        actions.addView(installBtn, installLp);

        sheet.addView(actions);

        installBtn.setOnClickListener(v -> {
            if (AppSettings.getBoolean(activity, AppSettings.HAPTICS, true)) {
                installBtn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            installBtn.setEnabled(false);
            laterBtn.setEnabled(false);
            installBtn.setText("DOWNLOADING…");
            progressBar.setVisibility(View.VISIBLE);
            progressText.setVisibility(View.VISIBLE);

            downloadAndInstall(activity, info, new DownloadProgressListener() {
                @Override
                public void onProgress(int percent, long bytesDownloaded, long totalBytes) {
                    progressBar.setProgress(percent);
                    progressText.setText(percent >= 0 ? (percent + "%  ·  " + String.format(Locale.US, "%.1f / %.1f MB", bytesDownloaded / 1048576f, totalBytes / 1048576f)) : "Downloading...");
                }

                @Override
                public void onComplete(File apkFile) {
                    dialog.dismiss();
                }

                @Override
                public void onError(String message) {
                    progressBar.setVisibility(View.GONE);
                    progressText.setText(message);
                    installBtn.setEnabled(true);
                    laterBtn.setEnabled(true);
                    installBtn.setText("RETRY DOWNLOAD");
                }
            });
        });

        int maxDialogW = Math.round(440 * density);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp20 * 2, maxDialogW),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        scrim.addView(sheet, sheetLp);
        dialog.setContentView(scrim);
        dialog.show();
    }

    public static String getAppVersionName(Context context) {
        try {
            PackageInfo p = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return p.versionName != null ? p.versionName : "0.1.0";
        } catch (Exception e) {
            return "0.1.0";
        }
    }

    public static int getAppVersionCode(Context context) {
        try {
            PackageInfo p = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return p.versionCode;
        } catch (Exception e) {
            return 1;
        }
    }
}
