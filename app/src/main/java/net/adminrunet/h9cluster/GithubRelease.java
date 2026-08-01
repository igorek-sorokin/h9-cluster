package net.adminrunet.h9cluster;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses GitHub release metadata used by the in-app updater. */
public final class GithubRelease {
    private static final Pattern VERSION_CODE_PATTERN =
            Pattern.compile("versionCode\\s*[:=]\\s*`?(\\d+)`?", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_NAME_PATTERN =
            Pattern.compile("(?i)(?:версия|version)\\s*[:=]\\s*`?v?(\\d+(?:\\.\\d+){0,3})`?");

    public final String tagName;
    public final String versionName;
    public final int versionCode;
    public final String apkName;
    public final String apkDownloadUrl;
    public final long apkSizeBytes;
    public final String body;

    public GithubRelease(
            String tagName,
            String versionName,
            int versionCode,
            String apkName,
            String apkDownloadUrl,
            long apkSizeBytes,
            String body) {
        this.tagName = tagName == null ? "" : tagName;
        this.versionName = versionName == null ? "" : versionName;
        this.versionCode = versionCode;
        this.apkName = apkName == null ? "" : apkName;
        this.apkDownloadUrl = apkDownloadUrl == null ? "" : apkDownloadUrl;
        this.apkSizeBytes = Math.max(0L, apkSizeBytes);
        this.body = body == null ? "" : body;
    }

    public boolean hasApk() {
        return apkDownloadUrl.length() > 0;
    }

    public static String normalizeVersionName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        int dash = trimmed.indexOf('-');
        if (dash > 0) {
            trimmed = trimmed.substring(0, dash);
        }
        return trimmed;
    }

    public static int parseVersionCode(String releaseBody) {
        if (releaseBody == null) {
            return -1;
        }
        Matcher matcher = VERSION_CODE_PATTERN.matcher(releaseBody);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static String parseVersionName(String tagName, String releaseBody) {
        String fromBody = parseVersionNameFromBody(releaseBody);
        if (fromBody.length() > 0) {
            return fromBody;
        }
        return normalizeVersionName(tagName);
    }

    private static String parseVersionNameFromBody(String releaseBody) {
        if (releaseBody == null) {
            return "";
        }
        Matcher matcher = VERSION_NAME_PATTERN.matcher(releaseBody);
        if (!matcher.find()) {
            return "";
        }
        return normalizeVersionName(matcher.group(1));
    }

    /**
     * Returns true when the GitHub release should replace the installed build.
     * Prefers versionCode; falls back to dotted versionName comparison.
     */
    public static boolean isNewerThan(
            int remoteVersionCode,
            String remoteVersionName,
            int localVersionCode,
            String localVersionName) {
        if (remoteVersionCode > 0) {
            return remoteVersionCode > localVersionCode;
        }
        return compareVersionNames(remoteVersionName, localVersionName) > 0;
    }

    public static int compareVersionNames(String left, String right) {
        int[] a = splitVersion(normalizeVersionName(left));
        int[] b = splitVersion(normalizeVersionName(right));
        int length = Math.max(a.length, b.length);
        for (int index = 0; index < length; index++) {
            int av = index < a.length ? a[index] : 0;
            int bv = index < b.length ? b[index] : 0;
            if (av != bv) {
                return av < bv ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] splitVersion(String version) {
        if (version.length() == 0) {
            return new int[0];
        }
        String[] parts = version.toLowerCase(Locale.US).split("\\.");
        int[] values = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String digits = parts[index].replaceAll("[^0-9].*$", "");
            if (digits.length() == 0) {
                values[index] = 0;
                continue;
            }
            try {
                values[index] = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                values[index] = 0;
            }
        }
        return values;
    }
}
