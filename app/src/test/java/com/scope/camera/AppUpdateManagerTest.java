package com.scope.camera;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppUpdateManagerTest {

    @Test
    public void testIsNewerVersion() {
        // Newer patch version
        assertTrue(AppUpdateManager.isNewerVersion("v0.1.2", "0.1.1", 1));
        assertTrue(AppUpdateManager.isNewerVersion("0.1.2", "0.1.1", 1));

        // Newer minor version
        assertTrue(AppUpdateManager.isNewerVersion("v0.2.0", "0.1.9", 1));

        // Newer major version
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.0", "0.9.9", 1));

        // Same version
        assertFalse(AppUpdateManager.isNewerVersion("v0.1.0", "0.1.0", 1));
        assertFalse(AppUpdateManager.isNewerVersion("0.1.0", "0.1.0", 1));
        assertFalse(AppUpdateManager.isNewerVersion("v0.1.0-beta", "0.1.0-beta", 1));

        // Older version
        assertFalse(AppUpdateManager.isNewerVersion("v0.1.0", "0.1.5", 1));
        assertFalse(AppUpdateManager.isNewerVersion("v0.0.9", "0.1.0", 1));

        // Run number / build indicator check
        assertTrue(AppUpdateManager.isNewerVersion("v0.1.45-beta", "0.1.10", 10));
        assertFalse(AppUpdateManager.isNewerVersion("v0.1.5-beta", "0.1.5", 10));

        // Null and empty safety
        assertFalse(AppUpdateManager.isNewerVersion(null, "0.1.0", 1));
        assertFalse(AppUpdateManager.isNewerVersion("", "0.1.0", 1));
    }

    @Test
    public void testParseLatestReleaseBetaChannel() {
        String json = "[\n" +
                "  {\n" +
                "    \"tag_name\": \"v0.1.15-beta\",\n" +
                "    \"name\": \"Lika Lens v0.1.15 (Beta)\",\n" +
                "    \"body\": \"* Fix video recorder orientation hint\\n* Extract LookRecipeControl\",\n" +
                "    \"prerelease\": true,\n" +
                "    \"draft\": false,\n" +
                "    \"published_at\": \"2026-11-08T12:00:00Z\",\n" +
                "    \"assets\": [\n" +
                "      {\n" +
                "        \"name\": \"lika-lens-v0.1.15-beta.apk\",\n" +
                "        \"size\": 5242880,\n" +
                "        \"browser_download_url\": \"https://github.com/harrydbarnes/lika-lens/releases/download/v0.1.15-beta/lika-lens-v0.1.15-beta.apk\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "]";

        AppUpdateManager.UpdateInfo update = AppUpdateManager.parseLatestRelease(json, "Beta", "0.1.0", 1);
        assertNotNull(update);
        assertEquals("v0.1.15-beta", update.tagName);
        assertTrue(update.isBeta);
        assertEquals(5242880L, update.apkSize);
        assertEquals("https://github.com/harrydbarnes/lika-lens/releases/download/v0.1.15-beta/lika-lens-v0.1.15-beta.apk", update.apkDownloadUrl);
        assertTrue(update.changelog.contains("LookRecipeControl"));
    }

    @Test
    public void testParseLatestReleaseStableChannelFiltersBeta() {
        String json = "[\n" +
                "  {\n" +
                "    \"tag_name\": \"v0.1.15-beta\",\n" +
                "    \"name\": \"Lika Lens v0.1.15 (Beta)\",\n" +
                "    \"body\": \"Beta changes\",\n" +
                "    \"prerelease\": true,\n" +
                "    \"draft\": false,\n" +
                "    \"assets\": [\n" +
                "      {\"name\": \"app-beta.apk\", \"browser_download_url\": \"https://example.com/beta.apk\"}\n" +
                "    ]\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag_name\": \"v0.1.10\",\n" +
                "    \"name\": \"Lika Lens v0.1.10 (Stable)\",\n" +
                "    \"body\": \"Stable release notes\",\n" +
                "    \"prerelease\": false,\n" +
                "    \"draft\": false,\n" +
                "    \"assets\": [\n" +
                "      {\"name\": \"app-stable.apk\", \"browser_download_url\": \"https://example.com/stable.apk\"}\n" +
                "    ]\n" +
                "  }\n" +
                "]";

        // Stable channel should skip v0.1.15-beta and select v0.1.10
        AppUpdateManager.UpdateInfo update = AppUpdateManager.parseLatestRelease(json, "Stable", "0.1.0", 1);
        assertNotNull(update);
        assertEquals("v0.1.10", update.tagName);
        assertFalse(update.isBeta);
        assertEquals("https://example.com/stable.apk", update.apkDownloadUrl);
    }

    @Test
    public void testParseLatestReleaseSingleObjectEndpoint() {
        String latestObjectJson = "{\n" +
                "  \"tag_name\": \"v0.2.0\",\n" +
                "  \"name\": \"Lika Lens v0.2.0\",\n" +
                "  \"body\": \"* Major release\",\n" +
                "  \"prerelease\": false,\n" +
                "  \"draft\": false,\n" +
                "  \"published_at\": \"2026-11-15T10:00:00Z\",\n" +
                "  \"assets\": [\n" +
                "    {\"name\": \"lika-lens-v0.2.0.apk\", \"size\": 4096000, \"browser_download_url\": \"https://example.com/v0.2.0.apk\"}\n" +
                "  ]\n" +
                "}";

        AppUpdateManager.UpdateInfo stable = AppUpdateManager.parseLatestRelease(latestObjectJson, AppSettings.UPDATE_CHANNEL_STABLE, "0.1.99", 99);
        assertNotNull(stable);
        assertEquals("v0.2.0", stable.tagName);
        assertFalse(stable.isBeta);
        assertEquals(4096000L, stable.apkSize);

        String prereleaseObjectJson = latestObjectJson.replace("\"prerelease\": false", "\"prerelease\": true");
        assertNull(AppUpdateManager.parseLatestRelease(prereleaseObjectJson, AppSettings.UPDATE_CHANNEL_STABLE, "0.1.99", 99));
        assertNotNull(AppUpdateManager.parseLatestRelease(prereleaseObjectJson, AppSettings.UPDATE_CHANNEL_BETA, "0.1.99", 99));
    }

    @Test
    public void testParseRecentReleasesSkipsDraftsAndRespectsLimit() {
        String json = "[\n" +
                "  {\"tag_name\":\"v0.1.110-beta\",\"name\":\"v0.1.110-beta\",\"body\":\"Draft note\",\"prerelease\":true,\"draft\":true,\"published_at\":\"2026-11-15T12:00:00Z\",\"assets\":[]},\n" +
                "  {\"tag_name\":\"v0.1.109-beta\",\"name\":\"Beta 109\",\"body\":\"* Fix viewfinder\",\"prerelease\":true,\"draft\":false,\"published_at\":\"2026-11-15T11:00:00Z\",\"assets\":[{\"name\":\"app.apk\",\"size\":2048,\"browser_download_url\":\"https://example.com/109.apk\"}]},\n" +
                "  {\"tag_name\":\"v0.1.108\",\"name\":\"Stable 108\",\"body\":\"* Stable build\",\"prerelease\":false,\"draft\":false,\"published_at\":\"2026-11-14T11:00:00Z\",\"assets\":[]},\n" +
                "  {\"tag_name\":\"v0.1.107-beta\",\"name\":\"Beta 107\",\"body\":\"* Older\",\"prerelease\":true,\"draft\":false,\"published_at\":\"2026-11-13T11:00:00Z\",\"assets\":[]}\n" +
                "]";

        List<AppUpdateManager.UpdateInfo> two = AppUpdateManager.parseRecentReleases(json, 2);
        assertEquals(2, two.size());
        assertEquals("v0.1.109-beta", two.get(0).tagName);
        assertTrue(two.get(0).isBeta);
        assertEquals("v0.1.108", two.get(1).tagName);
        assertFalse(two.get(1).isBeta);
    }

    @Test
    public void testChannelUrlsCacheKeysAndRelativeTime() {
        assertEquals(AppUpdateManager.GITHUB_RELEASES_LIST_API,
                AppUpdateManager.releasesUrlForChannel(AppSettings.UPDATE_CHANNEL_BETA));
        assertEquals(AppUpdateManager.GITHUB_LATEST_RELEASE_API,
                AppUpdateManager.releasesUrlForChannel(AppSettings.UPDATE_CHANNEL_STABLE));

        assertEquals(AppSettings.UPDATE_CACHE_ETAG_BETA, AppUpdateManager.etagKeyForEndpoint(true));
        assertEquals(AppSettings.UPDATE_CACHE_ETAG_STABLE, AppUpdateManager.etagKeyForEndpoint(false));
        assertEquals(AppSettings.UPDATE_CACHE_JSON_BETA, AppUpdateManager.jsonCacheKeyForEndpoint(true));
        assertEquals(AppSettings.UPDATE_CACHE_JSON_STABLE, AppUpdateManager.jsonCacheKeyForEndpoint(false));

        long now = 10_000_000_000L;
        assertEquals("Not checked yet", AppUpdateManager.formatRelativeCheckTime(0L, now));
        assertEquals("Just now", AppUpdateManager.formatRelativeCheckTime(now - 20_000L, now));
        assertEquals("5m ago", AppUpdateManager.formatRelativeCheckTime(now - 5 * 60_000L, now));
        assertEquals("3h ago", AppUpdateManager.formatRelativeCheckTime(now - 3 * 3_600_000L, now));
        assertEquals("2d ago", AppUpdateManager.formatRelativeCheckTime(now - 2 * 86_400_000L, now));
    }

    @Test
    public void testIsDownloadedApkValidEnforcesExactSizeWhenKnown() throws Exception {
        File tmp = File.createTempFile("lika_test_apk_", ".apk");
        tmp.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(new byte[2048]);
        }
        assertTrue(AppUpdateManager.isDownloadedApkValid(tmp, 2048L));
        assertFalse(AppUpdateManager.isDownloadedApkValid(tmp, 2049L));
        assertTrue(AppUpdateManager.isDownloadedApkValid(tmp, 0L));

        File tiny = File.createTempFile("lika_test_tiny_", ".apk");
        tiny.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tiny)) {
            out.write(new byte[512]);
        }
        assertFalse(AppUpdateManager.isDownloadedApkValid(tiny, 512L));
        assertFalse(AppUpdateManager.isDownloadedApkValid(null, 2048L));
    }

    @Test
    public void testFormatReleaseNotes() {
        String raw = "## What's Changed\n\n" +
                "* feat: implement 5 next-frontier features (Palm Timer, 10-bit HLG HDR) (568e235)\n" +
                "* fix(exif): format GPS coordinates according to standard Android ExifInterface (192b5cb)\n\n" +
                "---\n" +
                "*Continuous delivery beta build #74\n" +
                "(568e235d8229966f7c1f1635c92484a836565f5d)*";

        CharSequence formatted = AppUpdateManager.formatReleaseNotes(raw);
        assertNotNull(formatted);
        String text = formatted.toString();

        assertFalse("Should not contain markdown heading", text.contains("## What's Changed"));
        assertFalse("Should not contain markdown hr", text.contains("---"));
        assertFalse("Should not contain continuous delivery footer", text.contains("Continuous delivery"));
        assertFalse("Should not contain full git sha footer line", text.contains("568e235d8229966f7c1f1635c92484a836565f5d"));
        assertTrue("Should contain bullet point", text.contains("• feat: implement 5 next-frontier features"));
        assertTrue("Should contain second bullet point", text.contains("• fix(exif): format GPS coordinates"));
        assertFalse("Should strip commit hash from end of bullet", text.contains("(568e235)"));
    }

    @Test
    public void testUpdateStateActionLabelsAndInstallPrompting() {
        assertEquals("DOWNLOAD & INSTALL", AppUpdateManager.actionLabelForState(null));
        assertEquals("DOWNLOAD & INSTALL", AppUpdateManager.actionLabelForState(AppUpdateManager.UpdateState.AVAILABLE));
        assertEquals("DOWNLOADING…", AppUpdateManager.actionLabelForState(AppUpdateManager.UpdateState.DOWNLOADING));
        assertEquals("RETRY DOWNLOAD", AppUpdateManager.actionLabelForState(AppUpdateManager.UpdateState.FAILED));
        assertEquals("INSTALL NOW", AppUpdateManager.actionLabelForState(AppUpdateManager.UpdateState.READY_TO_INSTALL));
        assertEquals("DOWNLOAD & INSTALL", AppUpdateManager.actionLabelForState(AppUpdateManager.UpdateState.AWAITING_PERMISSION));

        assertTrue(AppUpdateManager.shouldPromptInstallInsteadOfDownload(AppUpdateManager.UpdateState.READY_TO_INSTALL, true));
        assertTrue(AppUpdateManager.shouldPromptInstallInsteadOfDownload(AppUpdateManager.UpdateState.AWAITING_PERMISSION, true));
        assertFalse(AppUpdateManager.shouldPromptInstallInsteadOfDownload(AppUpdateManager.UpdateState.FAILED, true));
        assertFalse(AppUpdateManager.shouldPromptInstallInsteadOfDownload(AppUpdateManager.UpdateState.DOWNLOADING, true));
        assertFalse(AppUpdateManager.shouldPromptInstallInsteadOfDownload(AppUpdateManager.UpdateState.READY_TO_INSTALL, false));
    }

    @Test
    public void testAutoUpdateCheckRunsOnColdStartAndBackgroundReturnAfterDebounce() {
        long t0 = 1_000_000L;
        // Cold start always checks immediately
        assertTrue(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, false, false, false, false, false, 0L, t0));
        // Auto-check disabled skips even on cold start
        assertFalse(AppUpdateManager.shouldRunAutoUpdateCheck(
                false, false, true, true, false, false, 0L, t0));
        // Active dialog or download/install suppresses background checks
        assertFalse(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, false, true, false, true, false, 0L, t0));
        assertFalse(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, false, true, false, false, true, 0L, t0));

        // Returning from a child activity within the session does not re-check before periodic window
        assertFalse(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, true, false, false, false, false, t0, t0 + 60_000L));
        // Unless user switched Beta <-> Stable channel
        assertTrue(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, true, false, true, false, false, t0, t0 + 2_000L));

        // Returning after leaving the app (Home/Recents) checks once debounce elapses, without needing 4h wait
        assertFalse(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, true, true, false, false, false, t0, t0 + 5_000L));
        assertTrue(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, true, true, false, false, false, t0, t0 + AppUpdateManager.FOREGROUND_CHECK_DEBOUNCE_MS));

        // Long idle in foreground checks periodically too
        assertTrue(AppUpdateManager.shouldRunAutoUpdateCheck(
                true, true, false, false, false, false, t0, t0 + AppUpdateManager.IN_SESSION_PERIODIC_CHECK_MS));
    }

    @Test
    public void testShouldShowAutoUpdateDialogAndActiveInstallStates() {
        AppUpdateManager.UpdateInfo info = new AppUpdateManager.UpdateInfo(
                "v0.1.106-beta", "Lika Lens v0.1.106", "", true, "https://example.com/a.apk", "a.apk", 100L, "");

        assertTrue(AppUpdateManager.shouldShowAutoUpdateDialog(info, null, false, false));
        assertFalse(AppUpdateManager.shouldShowAutoUpdateDialog(info, "v0.1.106-beta", false, false));
        assertTrue(AppUpdateManager.shouldShowAutoUpdateDialog(info, "v0.1.105-beta", false, false));
        assertFalse(AppUpdateManager.shouldShowAutoUpdateDialog(info, null, true, false));
        assertFalse(AppUpdateManager.shouldShowAutoUpdateDialog(info, null, false, true));

        assertTrue(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.DOWNLOADING));
        assertTrue(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.READY_TO_INSTALL));
        assertTrue(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.AWAITING_PERMISSION));
        assertTrue(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.INSTALLER_OPEN));
        assertFalse(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.AVAILABLE));
        assertFalse(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.FAILED));
        assertFalse(AppUpdateManager.isDownloadOrInstallActive(AppUpdateManager.UpdateState.CANCELLED));
    }
}
