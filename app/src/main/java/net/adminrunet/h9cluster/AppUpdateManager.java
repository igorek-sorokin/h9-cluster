package net.adminrunet.h9cluster;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks GitHub Releases and installs a newer APK when the user confirms. */
public final class AppUpdateManager {
    public interface Listener {
        void onStatus(String message);

        void onUpdateAvailable(GithubRelease release);

        void onNoUpdate(GithubRelease release);

        void onError(String message);
    }

    private static final String TAG = "H9ClusterUpdate";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 120000;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public AppUpdateManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isBusy() {
        return busy.get();
    }

    public void checkForUpdate(final Listener listener) {
        if (BuildConfig.DEMO_MODE) {
            listener.onError(
                    "В Demo обновления отключены. Соберите production APK.");
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            listener.onStatus("Уже выполняется проверка обновления…");
            return;
        }
        listener.onStatus("Проверка обновлений на GitHub…");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final GithubRelease release =
                            GithubUpdateClient.fetchLatestRelease(
                                    BuildConfig.UPDATE_GITHUB_REPO);
                    final boolean newer = GithubRelease.isNewerThan(
                            release.versionCode,
                            release.versionName,
                            BuildConfig.VERSION_CODE,
                            BuildConfig.VERSION_NAME);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            busy.set(false);
                            if (!release.hasApk()) {
                                listener.onError(
                                        "В релизе "
                                                + release.tagName
                                                + " нет APK");
                                return;
                            }
                            if (newer) {
                                listener.onUpdateAvailable(release);
                            } else {
                                listener.onNoUpdate(release);
                            }
                        }
                    });
                } catch (final Exception error) {
                    Log.w(TAG, "Update check failed", error);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            busy.set(false);
                            listener.onError(
                                    "Не удалось проверить обновление: "
                                            + safeMessage(error));
                        }
                    });
                }
            }
        });
    }

    public void downloadAndInstall(
            final Activity activity,
            final GithubRelease release,
            final Listener listener) {
        if (release == null || !release.hasApk()) {
            listener.onError("Нет ссылки на APK");
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            listener.onStatus("Уже идёт загрузка обновления…");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !appContext.getPackageManager().canRequestPackageInstalls()) {
            busy.set(false);
            listener.onStatus(
                    "Разрешите установку из этого источника и повторите");
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + appContext.getPackageName()));
            activity.startActivity(settings);
            return;
        }
        listener.onStatus("Скачивание " + release.apkName + "…");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                File apkFile = null;
                try {
                    apkFile = downloadApk(release, listener);
                    final File installed = apkFile;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                launchInstaller(activity, installed);
                                listener.onStatus(
                                        "Открыт установщик. Подтвердите обновление.");
                            } catch (Exception error) {
                                listener.onError(
                                        "Не удалось открыть установщик: "
                                                + safeMessage(error));
                            } finally {
                                busy.set(false);
                            }
                        }
                    });
                } catch (final Exception error) {
                    Log.w(TAG, "APK download failed", error);
                    if (apkFile != null && apkFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        apkFile.delete();
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            busy.set(false);
                            listener.onError(
                                    "Ошибка загрузки APK: " + safeMessage(error));
                        }
                    });
                }
            }
        });
    }

    private File downloadApk(GithubRelease release, final Listener listener)
            throws Exception {
        File dir = new File(appContext.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create updates cache");
        }
        String safeName = release.apkName.length() == 0
                ? "h9cluster-update.apk"
                : release.apkName.replaceAll("[^a-zA-Z0-9._-]", "_");
        File target = new File(dir, safeName);
        if (target.exists()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }

        HttpURLConnection connection =
                (HttpURLConnection) new URL(release.apkDownloadUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "H9Cluster-Updater");
        connection.connect();
        int code = connection.getResponseCode();
        if (code >= 400) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        long total = connection.getContentLengthLong();
        InputStream input = connection.getInputStream();
        FileOutputStream output = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[8192];
            long readTotal = 0L;
            int read;
            int lastPercent = -1;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                readTotal += read;
                if (total > 0L) {
                    final int percent = (int) ((readTotal * 100L) / total);
                    if (percent != lastPercent && percent % 5 == 0) {
                        lastPercent = percent;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onStatus(
                                        "Скачивание обновления: " + percent + "%");
                            }
                        });
                    }
                }
            }
            output.flush();
        } finally {
            try {
                output.close();
            } catch (Exception ignored) {
            }
            try {
                input.close();
            } catch (Exception ignored) {
            }
            connection.disconnect();
        }
        if (target.length() <= 0L) {
            throw new IllegalStateException("Downloaded APK is empty");
        }
        return target;
    }

    private void launchInstaller(Activity activity, File apkFile) {
        Uri uri = UpdateFileProvider.getUriForFile(activity, apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().length() == 0) {
            return error.getClass().getSimpleName();
        }
        return message.trim();
    }
}
