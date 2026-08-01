package net.adminrunet.h9cluster;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Reads the latest GitHub release for the configured repository. */
public final class GithubUpdateClient {
    private static final String TAG = "H9ClusterUpdate";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private GithubUpdateClient() {
    }

    public static GithubRelease fetchLatestRelease(String ownerRepo) throws Exception {
        if (ownerRepo == null || ownerRepo.trim().length() == 0) {
            throw new IllegalArgumentException("GitHub repository is not configured");
        }
        String apiUrl = "https://api.github.com/repos/"
                + ownerRepo.trim()
                + "/releases/latest";
        HttpURLConnection connection = openGet(apiUrl);
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String body = readFully(stream);
            if (code >= 400) {
                throw new IllegalStateException(
                        "GitHub API HTTP " + code + ": " + shorten(body));
            }
            return parseReleaseJson(body);
        } finally {
            connection.disconnect();
        }
    }

    static GithubRelease parseReleaseJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        String tagName = root.optString("tag_name", "");
        String body = root.optString("body", "");
        int versionCode = GithubRelease.parseVersionCode(body);
        String versionName = GithubRelease.parseVersionName(tagName, body);

        JSONArray assets = root.optJSONArray("assets");
        String apkName = "";
        String apkUrl = "";
        long apkSize = 0L;
        if (assets != null) {
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                String name = asset.optString("name", "");
                if (!name.toLowerCase().endsWith(".apk")) {
                    continue;
                }
                apkName = name;
                apkUrl = asset.optString("browser_download_url", "");
                apkSize = asset.optLong("size", 0L);
                break;
            }
        }
        if (apkUrl.length() == 0) {
            Log.w(TAG, "Latest release has no APK asset");
        }
        return new GithubRelease(
                tagName,
                versionName,
                versionCode,
                apkName,
                apkUrl,
                apkSize,
                body);
    }

    private static HttpURLConnection openGet(String url) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "H9Cluster-Updater");
        return connection;
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180);
    }
}
