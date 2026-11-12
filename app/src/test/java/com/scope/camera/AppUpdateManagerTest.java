package com.scope.camera;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
}
