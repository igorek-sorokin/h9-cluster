package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinSettings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import java.util.Map;

/** Main-display settings window. Boot startup never opens this activity. */
public final class SettingsActivity extends Activity {
    private SkinSettingsSession session;
    private SettingsView settingsView;
    private AppUpdateManager updateManager;
    private boolean unsavedPreviewActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        updateManager = new AppUpdateManager(this);
        session = new SkinSettingsSession(
                SkinPreferences.getSelectedSkin(this),
                new SkinSettingsSession.Loader() {
                    @Override
                    public SkinSettings load(String skinId) {
                        return SkinSettingsStore.load(
                                SettingsActivity.this,
                                skinId);
                    }
                });
        settingsView = new SettingsView(
                this,
                session,
                new SettingsView.Listener() {
                    @Override
                    public void onDraftChanged(
                            SkinSettingsSession.Snapshot draft) {
                        if (ClusterLauncher.previewOnClusterDisplay(
                                SettingsActivity.this,
                                draft)) {
                            unsavedPreviewActive = true;
                        }
                    }

                    @Override
                    public void onSaveRequested(
                            SkinSettingsSession.Snapshot draft) {
                        SkinPreferences.setSelectedSkin(
                                SettingsActivity.this,
                                draft.skinId);
                        for (Map.Entry<String, SkinSettings> entry
                                : session.drafts().entrySet()) {
                            SkinSettingsStore.save(
                                    SettingsActivity.this,
                                    entry.getKey(),
                                    entry.getValue());
                        }
                        unsavedPreviewActive = false;
                        ClusterPowerController.clearSuspendForUserLaunch();
                        boolean launched =
                                ClusterLauncher.startOnClusterDisplay(
                                        SettingsActivity.this,
                                        true);
                        settingsView.showSaveResult(launched);
                    }

                    @Override
                    public void onCheckUpdateRequested() {
                        checkForUpdates();
                    }
                });
        setContentView(settingsView);
    }

    private void checkForUpdates() {
        updateManager.checkForUpdate(new AppUpdateManager.Listener() {
            @Override
            public void onStatus(String message) {
                settingsView.setStatusMessage(message);
            }

            @Override
            public void onUpdateAvailable(final GithubRelease release) {
                String remoteLabel = release.versionName.length() == 0
                        ? release.tagName
                        : release.versionName;
                String message = "Доступна версия "
                        + remoteLabel
                        + ".\nСейчас установлена "
                        + BuildConfig.VERSION_NAME
                        + ".\n\nБудет скачан APK с GitHub ("
                        + BuildConfig.UPDATE_GITHUB_REPO
                        + ").\nЕсли у вас свои доработки, они могут пропасть"
                        + " после установки официального релиза.";
                settingsView.setStatusMessage(
                        "Найдено обновление " + remoteLabel);
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Обновление H9 Cluster")
                        .setMessage(message)
                        .setPositiveButton(
                                "Скачать и установить",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        updateManager.downloadAndInstall(
                                                SettingsActivity.this,
                                                release,
                                                updateUiListener());
                                    }
                                })
                        .setNegativeButton("Позже", null)
                        .show();
            }

            @Override
            public void onNoUpdate(GithubRelease release) {
                String remoteLabel = release.versionName.length() == 0
                        ? release.tagName
                        : release.versionName;
                settingsView.setStatusMessage(
                        "Уже актуальная версия ("
                                + BuildConfig.VERSION_NAME
                                + ", GitHub "
                                + remoteLabel
                                + ")");
            }

            @Override
            public void onError(String message) {
                settingsView.setStatusMessage(message);
            }
        });
    }

    private AppUpdateManager.Listener updateUiListener() {
        return new AppUpdateManager.Listener() {
            @Override
            public void onStatus(String message) {
                settingsView.setStatusMessage(message);
            }

            @Override
            public void onUpdateAvailable(GithubRelease release) {
            }

            @Override
            public void onNoUpdate(GithubRelease release) {
            }

            @Override
            public void onError(String message) {
                settingsView.setStatusMessage(message);
            }
        };
    }

    @Override
    public void onBackPressed() {
        restorePersistedPreview();
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        if (isFinishing()) {
            restorePersistedPreview();
        }
        super.onStop();
    }

    private void restorePersistedPreview() {
        if (unsavedPreviewActive) {
            unsavedPreviewActive = false;
            ClusterLauncher.startOnClusterDisplay(this, true);
        }
    }
}
