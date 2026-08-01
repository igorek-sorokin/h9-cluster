package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GithubReleaseTest {
    @Test
    public void parsesVersionCodeFromReleaseNotes() {
        assertEquals(
                2026073001,
                GithubRelease.parseVersionCode(
                        "versionCode: `2026073001`.\nAndroid: 9"));
    }

    @Test
    public void prefersBodyVersionNameThenTag() {
        assertEquals(
                "9.3.2",
                GithubRelease.parseVersionName(
                        "v9.3.2",
                        "версия: `9.3.2`"));
        assertEquals(
                "9.3.2",
                GithubRelease.parseVersionName("v9.3.2", ""));
    }

    @Test
    public void comparesVersionCodesFirst() {
        assertTrue(GithubRelease.isNewerThan(
                2026080101,
                "9.3.3",
                2026073001,
                "9.3.2"));
        assertFalse(GithubRelease.isNewerThan(
                2026073001,
                "9.3.2",
                2026073001,
                "9.3.2"));
    }

    @Test
    public void fallsBackToVersionNameWhenCodeMissing() {
        assertTrue(GithubRelease.isNewerThan(
                -1,
                "9.4.0",
                2026073001,
                "9.3.2"));
        assertFalse(GithubRelease.isNewerThan(
                -1,
                "9.3.1",
                2026073001,
                "9.3.2"));
    }

    @Test
    public void parsesReleaseJsonAsset() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v9.3.2\","
                + "\"body\":\"versionCode: `2026073001`\\nверсия: `9.3.2`\","
                + "\"assets\":[{"
                + "\"name\":\"H9_Cluster_v9.3.2_adminrunet_release.apk\","
                + "\"browser_download_url\":\"https://example.com/app.apk\","
                + "\"size\":1000"
                + "}]"
                + "}";
        GithubRelease release = GithubUpdateClient.parseReleaseJson(json);
        assertEquals("v9.3.2", release.tagName);
        assertEquals("9.3.2", release.versionName);
        assertEquals(2026073001, release.versionCode);
        assertTrue(release.hasApk());
        assertEquals("https://example.com/app.apk", release.apkDownloadUrl);
    }
}
