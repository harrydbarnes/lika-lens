package com.scope.camera;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages GitHub Releases update checking, changelog parsing, background APK downloading,
 * and seamless package installer invocation for both Beta and Stable release channels.
 */
public final class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    public static final String GITHUB_RELEASES_API = "https://api.github.com/repos/harrydbarnes/lika-lens/releases";
    static final String GITHUB_RELEASES_LIST_API = GITHUB_RELEASES_API + "?per_page=15";
    static final String GITHUB_LATEST_RELEASE_API = GITHUB_RELEASES_API + "/latest";
    private static final String ACTION_INSTALL_COMMIT = "com.scope.camera.ACTION_INSTALL_COMMIT";

    private static final int ACCENT = 0xffd7b574;
    private static final int BG_DIALOG = 0xff0e1314;
    private static final int BORDER_CARD = 0xff2a3233;

    private static final Pattern SAFE_TAG_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("[^0-9]");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("\\r?\\n");
    private static final Pattern HEADING_WHATS_CHANGED = Pattern.compile("(?i)^#+\\s*what'?s\\s*changed.*$");
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^[-*_]{3,}$");
    private static final Pattern CD_FOOTER = Pattern.compile("(?i)^\\*?continuous (?:delivery|integration).*");
    private static final Pattern FOOTER_HASH = Pattern.compile("^\\(?[a-f0-9]{7,40}\\)?\\*?$");
    private static final Pattern TRAILING_COMMIT_HASH = Pattern.compile("\\s*\\([a-f0-9]{7,40}\\)$");

    private static final ExecutorService checkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UpdateCheckWorker");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UpdateDownloadWorker");
        t.setDaemon(true);
        return t;
    });
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile String cachedVersionName;
    private static volatile int cachedVersionCode = -1;

    enum UpdateState {
        AVAILABLE,
        DOWNLOADING,
        FAILED,
        READY_TO_INSTALL,
        AWAITING_PERMISSION,
        INSTALLER_OPEN,
        CANCELLED
    }

    private static final UpdateSession session = new UpdateSession();
    private static final JobState checkJob = new JobState();
    private static final JobState recentReleasesJob = new JobState();
    static final class UpdateSession {
        volatile File apk;
        volatile UpdateInfo info;
        volatile UpdateState state = UpdateState.AVAILABLE;
        private long ticket;
        synchronized long beginDownload(UpdateInfo release) {
            if(state==UpdateState.DOWNLOADING) return 0;
            info=release;apk=null;state=UpdateState.DOWNLOADING;return ++ticket;
        }
        synchronized boolean owns(long id) { return id!=0 && id==ticket; }
        synchronized boolean select(UpdateInfo release) {
            if(state==UpdateState.DOWNLOADING) return false;
            info=release;return true;
        }
        synchronized void clear() { ++ticket;apk=null;info=null;state=UpdateState.AVAILABLE; }
    }
    private static Dialog sCurrentUpdateDialog;
    private static java.lang.ref.WeakReference<Activity> dialogOwner = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<Activity> resumedActivity = new java.lang.ref.WeakReference<>(null);

    static final long FOREGROUND_CHECK_DEBOUNCE_MS = 15_000L;
    static final long IN_SESSION_PERIODIC_CHECK_MS = 15 * 60_000L;

    private static final AtomicBoolean lifecycleRegistered = new AtomicBoolean(false);
    private static final AtomicBoolean startupCleanupScheduled = new AtomicBoolean(false);
    private static int startedActivityCount = 0;
    private static boolean appWasStopped = false;
    private static volatile boolean pendingBackgroundReturn = false;
    private static volatile boolean hasCompletedAutoCheckThisProcess = false;
    private static volatile long lastAutoCheckTimeMs = 0L;
    private static volatile String lastAutoCheckChannel = null;
    private static volatile String dismissedTagThisProcess = null;

    static final class AutoCheckAttempt {
        final String channel;
        final boolean fromBackgroundReturn;
        AutoCheckAttempt(String channel, boolean fromBackgroundReturn) {
            this.channel = channel;
            this.fromBackgroundReturn = fromBackgroundReturn;
        }
    }

    static void ensureLifecycleTracking(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        cleanStaleApksOnStartup(appContext);
        if (!(appContext instanceof Application)) return;
        if (!lifecycleRegistered.compareAndSet(false, true)) return;
        ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) {
                if (startedActivityCount == 0 && appWasStopped) {
                    pendingBackgroundReturn = true;
                }
                startedActivityCount++;
                appWasStopped = false;
            }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
                if (startedActivityCount == 0 && !activity.isChangingConfigurations()) {
                    appWasStopped = true;
                }
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    static void markAppBackgrounded() {
        appWasStopped = true;
    }

    static boolean isDialogShowing() {
        return sCurrentUpdateDialog != null && sCurrentUpdateDialog.isShowing();
    }

    static boolean isDownloadOrInstallActive(UpdateState state) {
        return state == UpdateState.DOWNLOADING
                || state == UpdateState.READY_TO_INSTALL
                || state == UpdateState.AWAITING_PERMISSION
                || state == UpdateState.INSTALLER_OPEN;
    }

    static boolean shouldRunAutoUpdateCheck(
            boolean autoCheckEnabled,
            boolean checkSucceededThisLaunch,
            boolean returnedFromBackground,
            boolean channelChanged,
            boolean isDialogShowing,
            boolean isDownloadOrInstallActive,
            long lastSuccessfulCheckMs,
            long nowMs) {
        if (!autoCheckEnabled || isDialogShowing || isDownloadOrInstallActive) {
            return false;
        }
        if (!checkSucceededThisLaunch || channelChanged) {
            return true;
        }
        long elapsed = nowMs >= lastSuccessfulCheckMs ? (nowMs - lastSuccessfulCheckMs) : Long.MAX_VALUE;
        if (returnedFromBackground) {
            return elapsed >= FOREGROUND_CHECK_DEBOUNCE_MS;
        }
        return elapsed >= IN_SESSION_PERIODIC_CHECK_MS;
    }

    static boolean shouldShowAutoUpdateDialog(
            UpdateInfo info,
            String dismissedTag,
            boolean isDialogShowing,
            boolean isDownloadOrInstallActive) {
        if (info == null || isDialogShowing || isDownloadOrInstallActive) {
            return false;
        }
        return dismissedTag == null || !dismissedTag.equals(info.tagName);
    }

    static AutoCheckAttempt beginAutoCheckIfDue(Context context, long nowMs) {
        Context appContext = context.getApplicationContext();
        boolean autoEnabled = AppSettings.getBoolean(appContext, AppSettings.AUTO_CHECK_UPDATES, true);
        String channel = AppSettings.get(appContext, AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_BETA);
        boolean channelChanged = lastAutoCheckChannel != null && !lastAutoCheckChannel.equals(channel);
        if (!shouldRunAutoUpdateCheck(
                autoEnabled,
                hasCompletedAutoCheckThisProcess,
                pendingBackgroundReturn,
                channelChanged,
                isDialogShowing(),
                isDownloadOrInstallActive(session.state),
                lastAutoCheckTimeMs,
                nowMs)) {
            return null;
        }
        boolean fromBg = pendingBackgroundReturn;
        pendingBackgroundReturn = false;
        if (channelChanged) {
            dismissedTagThisProcess = null;
        }
        return new AutoCheckAttempt(channel, fromBg);
    }

    static void markAutoCheckSucceeded(String channel, long nowMs) {
        hasCompletedAutoCheckThisProcess = true;
        lastAutoCheckTimeMs = nowMs;
        if (channel != null) {
            lastAutoCheckChannel = channel;
        }
    }

    static void markAutoCheckFailed(boolean fromBackgroundReturn) {
        if (fromBackgroundReturn) {
            pendingBackgroundReturn = true;
        }
    }

    static boolean canShowAutoUpdateDialog(UpdateInfo info) {
        return shouldShowAutoUpdateDialog(
                info,
                dismissedTagThisProcess,
                isDialogShowing(),
                isDownloadOrInstallActive(session.state));
    }

    static boolean isActivityForeground(boolean sameResumedActivity, boolean hasShowingDialogForActivity, boolean hasWindowFocus) {
        return sameResumedActivity || hasShowingDialogForActivity || hasWindowFocus;
    }

    private static boolean isForegroundActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return false;
        boolean sameResumed = resumedActivity.get() == activity;
        boolean showingDialog = sCurrentUpdateDialog != null && sCurrentUpdateDialog.isShowing() && dialogOwner.get() == activity;
        return isActivityForeground(sameResumed, showingDialog, activity.hasWindowFocus());
    }

    static String actionLabelForState(UpdateState state) {
        if (state == null) return "DOWNLOAD & INSTALL";
        switch (state) {
            case READY_TO_INSTALL:
                return "INSTALL NOW";
            case DOWNLOADING:
                return "DOWNLOADING…";
            case FAILED:
                return "RETRY DOWNLOAD";
            case AVAILABLE:
            case AWAITING_PERMISSION:
            default:
                return "DOWNLOAD & INSTALL";
        }
    }

    static boolean shouldPromptInstallInsteadOfDownload(UpdateState state, boolean hasDownloadedApk) {
        return hasDownloadedApk && state != UpdateState.FAILED && state != UpdateState.DOWNLOADING;
    }

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

    public interface RecentReleasesCallback {
        void onLoaded(List<UpdateInfo> releases, boolean offlineFallback);
        void onError(String message);
    }

    public interface DownloadProgressListener {
        void onProgress(int percent, long bytesDownloaded, long totalBytes);
        void onComplete(File apkFile);
        void onError(String message);
    }

    static boolean isBetaChannel(String channel) {
        return channel != null && (channel.contains("Beta") || channel.equalsIgnoreCase("beta"));
    }

    static String releasesUrlForChannel(String channel) {
        return isBetaChannel(channel) ? GITHUB_RELEASES_LIST_API : GITHUB_LATEST_RELEASE_API;
    }

    static String etagKeyForEndpoint(boolean betaListEndpoint) {
        return betaListEndpoint ? AppSettings.UPDATE_CACHE_ETAG_BETA : AppSettings.UPDATE_CACHE_ETAG_STABLE;
    }

    static String jsonCacheKeyForEndpoint(boolean betaListEndpoint) {
        return betaListEndpoint ? AppSettings.UPDATE_CACHE_JSON_BETA : AppSettings.UPDATE_CACHE_JSON_STABLE;
    }

    static boolean isDownloadedApkValid(File apkFile, long expectedSize) {
        if (apkFile == null || !apkFile.isFile() || apkFile.length() <= 1024L) {
            return false;
        }
        if (expectedSize > 0L) {
            return apkFile.length() == expectedSize;
        }
        return true;
    }

    static String formatRelativeCheckTime(long checkedAtMs, long nowMs) {
        if (checkedAtMs <= 0L) return "Not checked yet";
        long diff = Math.max(0L, nowMs - checkedAtMs);
        if (diff < 60_000L) return "Just now";
        long minutes = diff / 60_000L;
        if (minutes < 60L) return minutes + "m ago";
        long hours = minutes / 60L;
        if (hours < 24L) return hours + "h ago";
        long days = hours / 24L;
        return days + "d ago";
    }

    static String formatUpdateStatusSubtitle(Context context, long nowMs) {
        if (context == null) return "Query GitHub Releases API for the latest APK";
        SharedPreferences prefs = AppSettings.prefs(context);
        String channel = prefs.getString(AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_BETA);
        String shortChannel = isBetaChannel(channel) ? "Beta" : "Stable";
        long lastChecked = prefs.getLong(AppSettings.LAST_UPDATE_CHECK_TIME, 0L);
        String cachedTag = prefs.getString(AppSettings.UPDATE_CACHED_AVAILABLE_TAG, "");
        String cachedChannel = prefs.getString(AppSettings.UPDATE_CACHED_AVAILABLE_CHANNEL, "");
        if (cachedTag != null && !cachedTag.isEmpty()
                && channel.equals(cachedChannel)
                && isNewerVersion(cachedTag, getAppVersionName(context), getAppVersionCode(context))) {
            return "Update available: " + cachedTag + " · Checked " + formatRelativeCheckTime(lastChecked, nowMs);
        }
        if (lastChecked > 0L) {
            return "Up to date (" + shortChannel + ") · Checked " + formatRelativeCheckTime(lastChecked, nowMs);
        }
        return "Query GitHub Releases API (" + shortChannel + " channel)";
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

    public static File getApkFileForTag(Context context, String tagName) {
        File updateDir = new File(context.getCacheDir(), "updates");
        if (!updateDir.exists()) updateDir.mkdirs();
        String safeTag = tagName != null ? SAFE_TAG_PATTERN.matcher(tagName).replaceAll("_") : "latest";
        return new File(updateDir, "lika-lens-update-" + safeTag + ".apk");
    }

    static void cleanStaleApksOnStartup(Context context) {
        if (context == null) return;
        if (!startupCleanupScheduled.compareAndSet(false, true)) return;
        final Context appContext = context.getApplicationContext();
        downloadExecutor.execute(() -> {
            if (isDownloadOrInstallActive(session.state)) return;
            try {
                File updateDir = new File(appContext.getCacheDir(), "updates");
                if (!updateDir.exists() || !updateDir.isDirectory()) return;
                String currentVer = getAppVersionName(appContext);
                int currentCode = getAppVersionCode(appContext);
                File[] files = updateDir.listFiles();
                if (files == null) return;
                for (File f : files) {
                    if (!f.isFile()) continue;
                    String name = f.getName();
                    if (name.endsWith(".apk.part") || name.endsWith(".part")) {
                        f.delete();
                    } else if (name.startsWith("lika-lens-update-") && name.endsWith(".apk")) {
                        String tag = name.substring("lika-lens-update-".length(), name.length() - ".apk".length());
                        if (!isNewerVersion(tag, currentVer, currentCode)) {
                            f.delete();
                        }
                    } else if (name.endsWith(".apk")) {
                        f.delete();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static void cleanStaleApks(Context context, String activeTag) {
        try {
            File updateDir = new File(context.getCacheDir(), "updates");
            if (!updateDir.exists() || !updateDir.isDirectory()) return;
            File activeFile = getApkFileForTag(context, activeTag);
            String activePartName = activeFile.getName() + ".part";
            File[] files = updateDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.isFile()) continue;
                    String name = f.getName();
                    if (name.endsWith(".apk")) {
                        if (!f.getAbsolutePath().equals(activeFile.getAbsolutePath())) {
                            f.delete();
                        }
                    } else if (name.endsWith(".part") && !name.equals(activePartName)) {
                        f.delete();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static int parseSafeInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(NON_DIGIT_PATTERN.matcher(s).replaceAll(""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int extractTrailingNumber(String s) {
        if (s == null) return 0;
        try {
            Matcher m = DIGITS_PATTERN.matcher(s);
            int lastNum = 0;
            while (m.find()) {
                lastNum = Integer.parseInt(m.group(1));
            }
            return lastNum;
        } catch (Exception e) {
            return 0;
        }
    }

    private static UpdateInfo parseReleaseJsonObject(JSONObject rel, boolean requireApkAsset) {
        if (rel == null) return null;
        boolean isDraft = rel.optBoolean("draft", false);
        if (isDraft) return null;
        boolean isPrerelease = rel.optBoolean("prerelease", false);
        String tagName = rel.optString("tag_name", "");
        if (tagName.isEmpty()) return null;
        String title = rel.optString("name", tagName);
        String body = rel.optString("body", "Continuous integration build.");
        String publishedAt = rel.optString("published_at", "");

        JSONArray assets = rel.optJSONArray("assets");
        String apkUrl = null;
        String apkName = null;
        long apkSize = 0L;
        if (assets != null) {
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (name.toLowerCase(Locale.US).endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", null);
                    apkName = name;
                    apkSize = asset.optLong("size", 0L);
                    break;
                }
            }
        }
        if (requireApkAsset && (apkUrl == null || apkUrl.isEmpty())) {
            return null;
        }
        return new UpdateInfo(tagName, title, body, isPrerelease, apkUrl, apkName, apkSize, publishedAt);
    }

    /**
     * Parses GitHub Releases JSON (array from /releases or single object from /releases/latest)
     * for the newest available update matching channel.
     */
    public static UpdateInfo parseLatestRelease(String jsonString, String channel,
                                                String currentVersionName, int currentVersionCode) {
        if (jsonString == null) return null;
        String trimmed = jsonString.trim();
        if (trimmed.isEmpty()) return null;
        try {
            boolean wantBeta = isBetaChannel(channel);
            if (trimmed.startsWith("{")) {
                UpdateInfo info = parseReleaseJsonObject(new JSONObject(trimmed), true);
                if (info == null) return null;
                if (!wantBeta && info.isBeta) return null;
                if (isNewerVersion(info.tagName, currentVersionName, currentVersionCode)) {
                    return info;
                }
                return null;
            }

            JSONArray releases = new JSONArray(trimmed);
            for (int i = 0; i < releases.length(); i++) {
                UpdateInfo info = parseReleaseJsonObject(releases.optJSONObject(i), true);
                if (info == null) continue;
                if (!wantBeta && info.isBeta) continue;
                if (isNewerVersion(info.tagName, currentVersionName, currentVersionCode)) {
                    return info;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing releases JSON", e);
        }
        return null;
    }

    /**
     * Parses up to {@code limit} recent non-draft releases from GitHub Releases JSON.
     */
    public static List<UpdateInfo> parseRecentReleases(String jsonString, int limit) {
        if (jsonString == null || limit <= 0) return Collections.emptyList();
        String trimmed = jsonString.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();
        List<UpdateInfo> list = new ArrayList<>();
        try {
            if (trimmed.startsWith("{")) {
                UpdateInfo info = parseReleaseJsonObject(new JSONObject(trimmed), false);
                if (info != null) list.add(info);
                return list;
            }
            JSONArray releases = new JSONArray(trimmed);
            for (int i = 0; i < releases.length() && list.size() < limit; i++) {
                UpdateInfo info = parseReleaseJsonObject(releases.optJSONObject(i), false);
                if (info != null) {
                    list.add(info);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing recent releases JSON", e);
        }
        return list;
    }

    private static final class FetchResult {
        final String json;
        final int httpCode;
        final String errorMessage;

        FetchResult(String json, int httpCode, String errorMessage) {
            this.json = json;
            this.httpCode = httpCode;
            this.errorMessage = errorMessage;
        }

        boolean isSuccess() {
            return json != null && (httpCode == HttpURLConnection.HTTP_OK || httpCode == HttpURLConnection.HTTP_NOT_MODIFIED);
        }
    }

    private static FetchResult fetchReleasesJsonWithEtag(Context appContext, String endpointUrl, boolean betaListEndpoint) {
        SharedPreferences prefs = AppSettings.prefs(appContext);
        String etagKey = etagKeyForEndpoint(betaListEndpoint);
        String jsonKey = jsonCacheKeyForEndpoint(betaListEndpoint);
        String cachedEtag = prefs.getString(etagKey, null);
        String cachedJson = prefs.getString(jsonKey, null);
        boolean canConditional = cachedEtag != null && !cachedEtag.isEmpty()
                && cachedJson != null && !cachedJson.isEmpty();

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpointUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("User-Agent", "LikaLens-Android");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (canConditional) {
                conn.setRequestProperty("If-None-Match", cachedEtag);
            }
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                String body = sb.toString();
                String newEtag = conn.getHeaderField("ETag");
                SharedPreferences.Editor editor = prefs.edit().putString(jsonKey, body);
                if (newEtag != null && !newEtag.trim().isEmpty()) {
                    editor.putString(etagKey, newEtag.trim());
                } else {
                    editor.remove(etagKey);
                }
                editor.apply();
                return new FetchResult(body, code, null);
            } else if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                if (cachedJson != null && !cachedJson.isEmpty()) {
                    return new FetchResult(cachedJson, code, null);
                }
                return new FetchResult(null, code, "Empty cache for 304");
            }
            return new FetchResult(null, code, "GitHub API HTTP " + code);
        } catch (Exception e) {
            Log.w(TAG, "Release API request failed: " + endpointUrl, e);
            return new FetchResult(null, -1, e.getMessage() != null ? e.getMessage() : "Network error");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Asynchronously checks GitHub Releases for an available update.
     */
    public static void checkForUpdate(Context context, boolean isManual, UpdateCallback callback) {
        final long checkTicket = checkJob.begin(JobState.Phase.LOADING);
        final Context appContext = context.getApplicationContext();
        final WeakReference<Activity> activityRef = new WeakReference<>(context instanceof Activity ? (Activity) context : null);
        String channel = AppSettings.get(appContext, AppSettings.UPDATE_CHANNEL, AppSettings.UPDATE_CHANNEL_BETA);
        boolean wantBeta = isBetaChannel(channel);
        String endpointUrl = releasesUrlForChannel(channel);
        String currentVersionName = getAppVersionName(appContext);
        int currentVersionCode = getAppVersionCode(appContext);

        checkExecutor.execute(() -> {
            FetchResult res = fetchReleasesJsonWithEtag(appContext, endpointUrl, wantBeta);
            if (res.isSuccess()) {
                UpdateInfo info = parseLatestRelease(res.json, channel, currentVersionName, currentVersionCode);
                final long checkedAt = System.currentTimeMillis();
                AppSettings.prefs(appContext).edit()
                        .putLong(AppSettings.LAST_UPDATE_CHECK_TIME, checkedAt)
                        .putString(AppSettings.UPDATE_CACHED_AVAILABLE_TAG, info != null ? info.tagName : "")
                        .putString(AppSettings.UPDATE_CACHED_AVAILABLE_CHANNEL, channel)
                        .apply();

                mainHandler.post(() -> {
                    Activity act = activityRef.get();
                    if (!checkJob.accepts(checkTicket) || (act != null && (act.isFinishing() || act.isDestroyed()))) return;
                    checkJob.move(checkTicket, JobState.Phase.READY);
                    markAutoCheckSucceeded(channel, checkedAt);
                    if (info != null) {
                        if (callback != null) callback.onUpdateAvailable(info);
                    } else {
                        if (callback != null) callback.onNoUpdateAvailable();
                    }
                });
            } else if (!wantBeta && res.httpCode == HttpURLConnection.HTTP_NOT_FOUND) {
                final long checkedAt = System.currentTimeMillis();
                AppSettings.prefs(appContext).edit()
                        .putLong(AppSettings.LAST_UPDATE_CHECK_TIME, checkedAt)
                        .putString(AppSettings.UPDATE_CACHED_AVAILABLE_TAG, "")
                        .putString(AppSettings.UPDATE_CACHED_AVAILABLE_CHANNEL, channel)
                        .apply();
                mainHandler.post(() -> {
                    Activity act = activityRef.get();
                    if (!checkJob.accepts(checkTicket) || (act != null && (act.isFinishing() || act.isDestroyed()))) return;
                    checkJob.move(checkTicket, JobState.Phase.READY);
                    markAutoCheckSucceeded(channel, checkedAt);
                    if (callback != null) callback.onNoUpdateAvailable();
                });
            } else {
                mainHandler.post(() -> {
                    Activity act = activityRef.get();
                    if (!checkJob.accepts(checkTicket) || (act != null && (act.isFinishing() || act.isDestroyed()))) return;
                    checkJob.move(checkTicket, JobState.Phase.READY);
                    if (callback != null) callback.onError(res.errorMessage != null ? res.errorMessage : "Update check failed");
                });
            }
        });
    }

    /**
     * Fetches recent releases (with ETag revalidation and offline cache fallback) for release notes browsing.
     */
    public static void fetchRecentReleases(Context context, int limit, RecentReleasesCallback callback) {
        if (context == null || callback == null) return;
        final long ticket = recentReleasesJob.begin(JobState.Phase.LOADING);
        final Context appContext = context.getApplicationContext();
        final WeakReference<Activity> activityRef = new WeakReference<>(context instanceof Activity ? (Activity) context : null);

        checkExecutor.execute(() -> {
            FetchResult res = fetchReleasesJsonWithEtag(appContext, GITHUB_RELEASES_LIST_API, true);
            List<UpdateInfo> parsed = Collections.emptyList();
            boolean offlineFallback = false;

            if (res.isSuccess()) {
                parsed = parseRecentReleases(res.json, limit);
            } else {
                SharedPreferences prefs = AppSettings.prefs(appContext);
                String cached = prefs.getString(AppSettings.UPDATE_CACHE_JSON_BETA, null);
                if (cached == null || cached.isEmpty()) {
                    cached = prefs.getString(AppSettings.UPDATE_CACHE_JSON_STABLE, null);
                }
                if (cached != null && !cached.isEmpty()) {
                    parsed = parseRecentReleases(cached, limit);
                    offlineFallback = !parsed.isEmpty();
                }
            }

            final List<UpdateInfo> finalReleases = parsed;
            final boolean finalOfflineFallback = offlineFallback;
            final boolean succeeded = res.isSuccess() || offlineFallback;
            final String errMsg = res.errorMessage != null ? res.errorMessage : "Could not load release notes";

            mainHandler.post(() -> {
                Activity act = activityRef.get();
                if (!recentReleasesJob.accepts(ticket) || (act != null && (act.isFinishing() || act.isDestroyed()))) return;
                recentReleasesJob.move(ticket, succeeded ? JobState.Phase.READY : JobState.Phase.FAILED);
                if (succeeded) {
                    callback.onLoaded(finalReleases, finalOfflineFallback);
                } else {
                    callback.onError(errMsg);
                }
            });
        });
    }

    /**
     * Downloads APK file in background with progress and prompts installation.
     */
    public static void downloadAndInstall(Activity activity, UpdateInfo info, DownloadProgressListener listener) {
        final long downloadTicket = session.beginDownload(info);
        if (downloadTicket == 0) return;
        final Context appContext = activity.getApplicationContext();
        final WeakReference<Activity> activityRef = new WeakReference<>(activity);
        downloadExecutor.execute(() -> {
            cleanStaleApks(appContext, info.tagName);
            File destFile = getApkFileForTag(appContext, info.tagName);
            File tempFile = new File(destFile.getParentFile(), destFile.getName() + ".part");
            if (tempFile.exists()) tempFile.delete();

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

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("Download HTTP " + responseCode);
                }

                long totalBytes = conn.getContentLengthLong();
                if (totalBytes <= 0) totalBytes = info.apkSize;

                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[32 * 1024];
                    long downloaded = 0;
                    int count;
                    long lastProgressUpdate = 0;

                    while ((count = input.read(buffer)) != -1) {
                        if (!session.owns(downloadTicket)) break;
                        output.write(buffer, 0, count);
                        downloaded += count;
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdate > 100 || downloaded == totalBytes) {
                            lastProgressUpdate = now;
                            final int percent = totalBytes > 0 ? (int) ((downloaded * 100) / totalBytes) : -1;
                            final long finalDownloaded = downloaded;
                            final long finalTotal = totalBytes;
                            mainHandler.post(() -> {
                                Activity act = activityRef.get();
                                if (!session.owns(downloadTicket) || act == null || act.isDestroyed()) return;
                                if (listener != null) listener.onProgress(percent, finalDownloaded, finalTotal);
                            });
                        }
                    }
                    output.flush();
                }

                if (!session.owns(downloadTicket)) {
                    if (tempFile.exists()) tempFile.delete();
                    return;
                }
                if (!isDownloadedApkValid(tempFile, info.apkSize)) {
                    if (tempFile.exists()) tempFile.delete();
                    throw new java.io.IOException("Downloaded APK is incomplete");
                }
                if (destFile.exists() && !destFile.delete()) {
                    throw new java.io.IOException("Could not replace existing APK");
                }
                if (!tempFile.renameTo(destFile)) {
                    throw new java.io.IOException("Could not finalize downloaded APK");
                }
                destFile.setReadable(true, false);
                if (!session.owns(downloadTicket)) return;
                session.apk = destFile;
                session.state = UpdateState.READY_TO_INSTALL;

                mainHandler.post(() -> {
                    Activity act = activityRef.get();
                    if (!session.owns(downloadTicket) || act == null || !isForegroundActivity(act)) return;
                    if (listener != null) listener.onComplete(destFile);
                    promptInstall(act, destFile, info);
                });
            } catch (Exception e) {
                if (tempFile.exists()) tempFile.delete();
                if (session.owns(downloadTicket)) session.state = UpdateState.FAILED;
                Log.e(TAG, "Download failed", e);
                mainHandler.post(() -> {
                    Activity act = activityRef.get();
                    if (session.owns(downloadTicket) && act != null && !act.isDestroyed() && listener != null) {
                        listener.onError("Download failed: " + e.getMessage());
                    }
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
        promptInstall(activity, apkFile, session.info);
    }

    public static void promptInstall(Activity activity, File apkFile, UpdateInfo info) {
        if (activity == null || apkFile == null || !apkFile.exists()) return;
        if (activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        session.apk = apkFile;
        if (info != null) session.info = info;

        // Check for Unknown App Sources permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                session.state = UpdateState.AWAITING_PERMISSION;
                Toast.makeText(activity, "Please allow Lika Lens to install updates", Toast.LENGTH_LONG).show();
                Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                permissionIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                try {
                    activity.startActivity(permissionIntent);
                } catch (Exception e) {
                    activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                }
                return;
            }
        }

        session.state = UpdateState.INSTALLER_OPEN;
        installViaPackageInstallerAsync(activity, apkFile);
    }

    private static void installViaPackageInstallerAsync(Activity activity, File apkFile) {
        final Context appContext = activity.getApplicationContext();
        final WeakReference<Activity> activityRef = new WeakReference<>(activity);
        downloadExecutor.execute(() -> {
            PackageInstaller.Session installerSession = null;
            try {
                PackageInstaller packageInstaller = appContext.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(appContext.getPackageName());
                if (apkFile.length() > 0L) {
                    params.setSize(apkFile.length());
                }
                int sessionId = packageInstaller.createSession(params);
                installerSession = packageInstaller.openSession(sessionId);

                try (InputStream in = new FileInputStream(apkFile);
                     OutputStream out = installerSession.openWrite("lika_lens_update.apk", 0, apkFile.length())) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    installerSession.fsync(out);
                }

                Intent callbackIntent = new Intent(appContext, UpdateInstallReceiver.class);
                callbackIntent.setAction(ACTION_INSTALL_COMMIT);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent statusIntent = PendingIntent.getBroadcast(appContext, sessionId, callbackIntent, flags);
                installerSession.commit(statusIntent.getIntentSender());
                installerSession.close();
                installerSession = null;
            } catch (Exception e) {
                Log.w(TAG, "PackageInstaller session failed, falling back to ACTION_VIEW installer", e);
                if (installerSession != null) {
                    try { installerSession.abandon(); } catch (Exception ignored) {}
                    try { installerSession.close(); } catch (Exception ignored) {}
                }
                mainHandler.post(() -> {
                    Activity act = activityRef.get();
                    if (act == null || act.isFinishing() || act.isDestroyed()) {
                        session.state = UpdateState.READY_TO_INSTALL;
                        return;
                    }
                    launchLegacyApkInstaller(act, apkFile);
                });
            }
        });
    }

    private static void launchLegacyApkInstaller(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            apkFile.setReadable(true, false);
            Uri apkUri = ApkFileProvider.getUriForFile(activity, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setClipData(android.content.ClipData.newRawUri("Lika Lens Update", apkUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            session.state = UpdateState.INSTALLER_OPEN;
        } catch (Exception e) {
            session.state = UpdateState.READY_TO_INSTALL;
            Log.e(TAG, "Failed to start package installer", e);
            Toast.makeText(activity, "Could not open installer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (session.info != null) {
                showUpdateDialog(activity, session.info);
            }
        }
    }

    static void onPackageInstallerResult(Context context, Intent intent) {
        if (context == null || intent == null) return;
        final Context appContext = context.getApplicationContext();
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                @SuppressWarnings("deprecation")
                Intent legacy = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                confirmIntent = legacy;
            }
            if (confirmIntent == null) return;
            mainHandler.post(() -> {
                Activity act = resumedActivity.get();
                try {
                    if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                        act.startActivity(confirmIntent);
                    } else {
                        Intent launchIntent = new Intent(confirmIntent);
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        appContext.startActivity(launchIntent);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to launch PackageInstaller confirmation UI", e);
                    if (act != null && !act.isFinishing() && !act.isDestroyed() && session.apk != null && session.apk.exists()) {
                        launchLegacyApkInstaller(act, session.apk);
                    } else {
                        session.state = UpdateState.READY_TO_INSTALL;
                    }
                }
            });
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            downloadExecutor.execute(() -> cleanStaleApks(appContext, ""));
            mainHandler.post(session::clear);
            return;
        }

        mainHandler.post(() -> {
            if (session.state == UpdateState.INSTALLER_OPEN) {
                session.state = UpdateState.READY_TO_INSTALL;
            }
            if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                Log.d(TAG, "PackageInstaller update cancelled by user");
                return;
            }
            Log.w(TAG, "PackageInstaller failed status=" + status + " message=" + statusMessage);
            Activity act = resumedActivity.get();
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                if (status == PackageInstaller.STATUS_FAILURE && session.apk != null && session.apk.exists()) {
                    launchLegacyApkInstaller(act, session.apk);
                } else {
                    String msg = (statusMessage != null && !statusMessage.trim().isEmpty())
                            ? statusMessage.trim()
                            : ("Install failed (code " + status + ")");
                    Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Called on Activity onResume to automatically resume pending installation
     * once unknown app sources permission has been granted, or re-show update dialog if returned.
     */
    public static void onPause(Activity activity) {
        if (resumedActivity.get() == activity) {
            resumedActivity.clear();
        }
        if(sCurrentUpdateDialog!=null && dialogOwner.get()==activity) {
            sCurrentUpdateDialog.dismiss();sCurrentUpdateDialog=null;dialogOwner.clear();
        }
    }

    public static void onResume(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        resumedActivity = new java.lang.ref.WeakReference<>(activity);

        // If the app has already updated to or past the pending update, clear pending state
        if (session.info != null) {
            String currentVer = getAppVersionName(activity);
            int currentCode = getAppVersionCode(activity);
            if (!isNewerVersion(session.info.tagName, currentVer, currentCode)) {
                cleanStaleApks(activity, "");
                session.clear();
                return;
            }
        }

        long expectedSize = session.info != null ? session.info.apkSize : 0L;
        if (session.state == UpdateState.READY_TO_INSTALL && isDownloadedApkValid(session.apk, expectedSize) && session.info != null) {
            showUpdateDialog(activity, session.info);
            return;
        }
        if(session.state==UpdateState.INSTALLER_OPEN) {
            session.state=UpdateState.READY_TO_INSTALL;
            if(session.info!=null) showUpdateDialog(activity,session.info);
            return;
        }
        if (session.state == UpdateState.AWAITING_PERMISSION) {
            session.state = UpdateState.READY_TO_INSTALL;
            boolean canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    || activity.getPackageManager().canRequestPackageInstalls();

            if (canInstall && isDownloadedApkValid(session.apk, expectedSize)) {
                Toast.makeText(activity, "Permission granted. Starting installation…", Toast.LENGTH_SHORT).show();
                promptInstall(activity, session.apk, session.info);
            } else if (session.info != null) {
                showUpdateDialog(activity, session.info);
            }
        }
    }

    /**
     * Displays a Leica Obsidian & Gold update notification dialog with changelog and one-tap install.
     */
    public static void showUpdateDialog(Activity activity, UpdateInfo info) {
        if (activity == null || activity.isFinishing() || info == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        if(!session.select(info)) return;
        if (sCurrentUpdateDialog != null && sCurrentUpdateDialog.isShowing()) {
            try {
                sCurrentUpdateDialog.dismiss();
            } catch (Exception ignored) {}
        }

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        sCurrentUpdateDialog = dialog;
        dialogOwner = new java.lang.ref.WeakReference<>(activity);
        dialog.setOnDismissListener(d -> {
            if (sCurrentUpdateDialog == dialog) {
                sCurrentUpdateDialog = null;
                dialogOwner.clear();
            }
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        float density = activity.getResources().getDisplayMetrics().density;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
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
        sheet.setPadding(dp20, dp20, dp20, dp20);

        // Header Title
        TextView header = new TextView(activity);
        header.setText("LIKA LENS UPDATE AVAILABLE");
        header.setTextSize(14);
        header.setTextColor(ACCENT);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(.14f);
        sheet.addView(header);

        // Version & Channel Tag Row
        LinearLayout tagRow = new LinearLayout(activity);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        tagRow.setPadding(0, dp8, 0, dp12);

        String tagText = info.tagName + (info.isBeta ? " · BETA CHANNEL" : " · STABLE RELEASE");
        TextView tagView = new TextView(activity);
        tagView.setText(tagText);
        tagView.setTextSize(11);
        tagView.setTextColor(0xfff0f2eb);
        tagView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tagRow.addView(tagView, new LinearLayout.LayoutParams(0, -2, 1f));

        if (info.apkSize > 0) {
            String sizeStr = String.format(Locale.US, "%.1f MB", info.apkSize / (1024f * 1024f));
            TextView sizeView = new TextView(activity);
            sizeView.setText(sizeStr);
            sizeView.setTextSize(10);
            sizeView.setTextColor(0xff9ba59e);
            sizeView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
            tagRow.addView(sizeView);
        }
        sheet.addView(tagRow);

        // Release Notes Header
        TextView changelogHeader = new TextView(activity);
        changelogHeader.setText("RELEASE NOTES");
        changelogHeader.setTextSize(11);
        changelogHeader.setTextColor(ACCENT);
        changelogHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        changelogHeader.setLetterSpacing(.12f);
        changelogHeader.setPadding(0, dp4, 0, 0);
        sheet.addView(changelogHeader);

        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(0xff14191a);
        scroll.setPadding(dp14, dp12, dp14, dp12);

        TextView changelogText = new TextView(activity);
        CharSequence formattedNotes = formatReleaseNotes(info.changelog);
        changelogText.setText(formattedNotes);
        changelogText.setTextSize(12.5f);
        changelogText.setTextColor(0xffd8ded7);
        changelogText.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        changelogText.setLineSpacing(dp4, 1.25f);
        scroll.addView(changelogText);

        int targetScrollH = Math.max(Math.round(240 * density), Math.min(Math.round(380 * density), Math.round(screenH * 0.44f)));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, targetScrollH);
        scrollLp.topMargin = dp8;
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
        progressText.setTextSize(11);
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
        laterBtn.setTextSize(11);
        laterBtn.setTextColor(0xffd8ded7);
        laterBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        laterBtn.setGravity(Gravity.CENTER);

        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setColor(0xd8191e1e);
        laterBg.setCornerRadius(16 * density);
        laterBg.setStroke(Math.round(1 * density), 0x556e7872);
        laterBtn.setBackground(laterBg);

        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(0, Math.round(44 * density), 1f);
        laterLp.rightMargin = dp6;
        actions.addView(laterBtn, laterLp);
        laterBtn.setOnClickListener(v -> {
            dismissedTagThisProcess = info.tagName;
            session.state = UpdateState.CANCELLED;
            dialog.dismiss();
        });

        File destFile = getApkFileForTag(activity, info.tagName);
        boolean isAlreadyDownloaded = isDownloadedApkValid(destFile, info.apkSize);
        if (isAlreadyDownloaded) {
            session.apk = destFile;
        }
        session.state = isAlreadyDownloaded ? UpdateState.READY_TO_INSTALL : UpdateState.AVAILABLE;

        TextView installBtn = new TextView(activity);
        installBtn.setText(actionLabelForState(session.state));
        installBtn.setTextSize(11);
        installBtn.setTextColor(0xff0b0d0e);
        installBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        installBtn.setGravity(Gravity.CENTER);

        GradientDrawable installBg = new GradientDrawable();
        installBg.setColor(ACCENT);
        installBg.setCornerRadius(16 * density);
        installBtn.setBackground(installBg);

        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(0, Math.round(44 * density), 1.5f);
        installLp.leftMargin = dp6;
        actions.addView(installBtn, installLp);

        sheet.addView(actions);

        installBtn.setOnClickListener(v -> {
            UiKit.hapticClick(activity, installBtn);
            boolean hasApk = isDownloadedApkValid(destFile, info.apkSize);
            if (shouldPromptInstallInsteadOfDownload(session.state, hasApk)) {
                session.state = UpdateState.READY_TO_INSTALL;
                dialog.dismiss();
                promptInstall(activity, destFile, info);
                return;
            }

            installBtn.setEnabled(false);
            laterBtn.setEnabled(false);
            installBtn.setText(actionLabelForState(UpdateState.DOWNLOADING));
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
                    session.state = UpdateState.READY_TO_INSTALL;
                    dialog.dismiss();
                }

                @Override
                public void onError(String message) {
                    session.state = UpdateState.FAILED;
                    progressBar.setVisibility(View.GONE);
                    progressText.setText(message);
                    installBtn.setEnabled(true);
                    laterBtn.setEnabled(true);
                    installBtn.setText(actionLabelForState(session.state));
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
        String cached = cachedVersionName;
        if (cached != null) return cached;
        try {
            PackageInfo p = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            cachedVersionName = p.versionName != null ? p.versionName : "0.1.0";
            if (cachedVersionCode < 0) cachedVersionCode = p.versionCode;
            return cachedVersionName;
        } catch (Exception e) {
            return "0.1.0";
        }
    }

    public static int getAppVersionCode(Context context) {
        int cached = cachedVersionCode;
        if (cached >= 0) return cached;
        try {
            PackageInfo p = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (cachedVersionName == null) cachedVersionName = p.versionName != null ? p.versionName : "0.1.0";
            cachedVersionCode = p.versionCode;
            return cachedVersionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Cleans and formats markdown release notes for elegant in-app dialog presentation.
     * Strips redundant "What's Changed" headers and raw continuous delivery build footers.
     */
    public static CharSequence formatReleaseNotes(String rawNotes) {
        if (rawNotes == null || rawNotes.trim().isEmpty()) {
            return "Latest bug fixes and performance improvements.";
        }
        String[] lines = LINE_SPLIT_PATTERN.split(rawNotes);
        StringBuilder sb = new StringBuilder(rawNotes.length());
        boolean inFooter = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                int len = sb.length();
                if (len > 0 && !(len >= 2 && sb.charAt(len - 1) == '\n' && sb.charAt(len - 2) == '\n')) {
                    sb.append('\n');
                }
                continue;
            }

            // Strip markdown heading "## What's Changed" or similar
            if (HEADING_WHATS_CHANGED.matcher(line).matches()) {
                continue;
            }

            // Strip horizontal rules
            if (HORIZONTAL_RULE.matcher(line).matches()) {
                continue;
            }

            // Strip continuous delivery footer notes
            if (CD_FOOTER.matcher(line).matches()) {
                inFooter = true;
                continue;
            }
            if (inFooter) {
                // If the footer wrapped onto subsequent lines (e.g., hash in parentheses), drop them
                if (FOOTER_HASH.matcher(line).matches()) {
                    continue;
                } else {
                    inFooter = false;
                }
            }

            // Format bullet points cleanly
            if (line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ")) {
                String bulletText = line.substring(2).trim();
                // Strip trailing commit hash like (568e235) or (568e235d8229966f7c1f1635c92484a836565f5d)
                bulletText = TRAILING_COMMIT_HASH.matcher(bulletText).replaceAll("");
                sb.append("• ").append(bulletText).append("\n\n");
            } else {
                sb.append(line).append("\n\n");
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "Latest bug fixes and performance improvements." : result;
    }
}
