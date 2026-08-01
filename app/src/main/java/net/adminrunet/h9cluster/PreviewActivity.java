package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Full-screen host for the live cluster renderer. */
@SuppressWarnings("deprecation")
public final class PreviewActivity extends Activity {
    public static final String EXTRA_RELOAD_SKIN = "reload_skin";
    public static final String EXTRA_SINGLE_DISPLAY_FALLBACK = "single_display_fallback";
    public static final String EXTRA_HAS_DRAFT = "has_skin_settings_draft";
    public static final String EXTRA_DRAFT_SKIN = "draft_skin";
    public static final String EXTRA_DRAFT_SETTINGS = "draft_skin_settings";
    private static final String TAG = "H9Cluster";
    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private static PreviewActivity showingInstance;

    private ClusterRenderer clusterRenderer;
    private SkinSettingsSession.Snapshot activeSnapshot;
    private ClusterDataSource dataSource;
    private ClusterState lastState = ClusterState.empty();

    /** Finishes the live overlay so the stock instrument cluster can show through. */
    static void closeIfShowing() {
        blankAndCloseIfShowing();
    }

    /** Blanks Display ID 2 immediately, then finishes the overlay activity. */
    static void blankAndCloseIfShowing() {
        final PreviewActivity instance = showingInstance;
        if (instance == null) {
            return;
        }
        instance.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (instance.isFinishing()) {
                    return;
                }
                try {
                    WindowManager.LayoutParams params =
                            instance.getWindow().getAttributes();
                    params.screenBrightness = 0.0f;
                    instance.getWindow().setAttributes(params);
                    instance.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (RuntimeException ignored) {
                }
                instance.finish();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showingInstance = this;
        Log.i(TAG, "Starting build "
                + BuildConfig.VERSION_NAME
                + "-display2-api28");

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Do not use FLAG_KEEP_SCREEN_ON: on Haval it keeps Display ID 2 lit after
        // ACC/ignition blanks the head unit without sending SCREEN_OFF.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0.0f);
        getWindow().setFormat(PreviewAppearance.usesOpaqueWindow(BuildConfig.DEMO_MODE)
                ? PixelFormat.OPAQUE
                : PixelFormat.TRANSLUCENT);
        int backgroundColor = PreviewAppearance.backgroundColor(BuildConfig.DEMO_MODE);
        getWindow().setBackgroundDrawable(new ColorDrawable(backgroundColor));
        getWindow().getDecorView().setBackgroundColor(backgroundColor);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        SkinSettingsSession.Snapshot snapshot = resolveSnapshot(getIntent());
        if (!SkinRegistry.overlaysCluster(snapshot.skinId)) {
            finish();
            return;
        }
        applySnapshot(snapshot, true);

        dataSource = BuildConfig.DEMO_MODE
                ? new DemoClusterDataSource(this)
                : new GwmClusterDataSource(this);
        dataSource.start(new ClusterDataSource.Listener() {
            @Override
            public void onClusterState(ClusterState state) {
                lastState = state;
                if (clusterRenderer != null) {
                    clusterRenderer.setClusterState(state);
                }
            }
        });
        hideSystemUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        SkinSettingsSession.Snapshot snapshot = resolveSnapshot(intent);
        if (!SkinRegistry.overlaysCluster(snapshot.skinId)) {
            finish();
            return;
        }
        boolean forceReload = intent != null
                && intent.getBooleanExtra(EXTRA_RELOAD_SKIN, false);
        applySnapshot(snapshot, forceReload);
    }

    private SkinSettingsSession.Snapshot resolveSnapshot(Intent intent) {
        boolean hasDraft = intent != null
                && intent.getBooleanExtra(EXTRA_HAS_DRAFT, false);
        if (hasDraft) {
            String draftSkin = intent.getStringExtra(EXTRA_DRAFT_SKIN);
            if (SkinRegistry.isSupported(draftSkin)) {
                SkinSettings draftSettings = SkinSettingsTransport.fromBundle(
                        intent.getBundleExtra(EXTRA_DRAFT_SETTINGS));
                return new SkinSettingsSession.Snapshot(
                        draftSkin,
                        SkinRegistry.normalizeSettings(
                                draftSkin,
                                draftSettings));
            }
        }
        String persistedSkin = SkinPreferences.getSelectedSkin(this);
        return new SkinSettingsSession.Snapshot(
                persistedSkin,
                SkinSettingsStore.load(this, persistedSkin));
    }

    private void applySnapshot(
            SkinSettingsSession.Snapshot snapshot,
            boolean forceReload) {
        if (!forceReload && snapshot.equals(activeSnapshot)) {
            return;
        }
        View rendererView = SkinRegistry.createRenderer(
                this,
                snapshot.skinId,
                snapshot.settings);
        clusterRenderer = (ClusterRenderer) rendererView;
        activeSnapshot = snapshot;
        rendererView.setBackgroundColor(
                PreviewAppearance.backgroundColor(BuildConfig.DEMO_MODE));
        if (shouldReturnToSettingsOnInteraction()) {
            rendererView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    returnToSettings();
                }
            });
        }
        setContentView(rendererView);
        clusterRenderer.setClusterState(lastState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        if (showingInstance == this) {
            showingInstance = null;
        }
        if (dataSource != null) {
            dataSource.stop();
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (shouldReturnToSettingsOnInteraction()) {
            returnToSettings();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
    }

    private boolean shouldReturnToSettingsOnInteraction() {
        return ClusterDisplayPolicy.shouldReturnToSettingsOnInteraction(
                BuildConfig.DEMO_MODE,
                getIntent().getBooleanExtra(EXTRA_SINGLE_DISPLAY_FALLBACK, false));
    }

    @SuppressWarnings("deprecation")
    private void returnToSettings() {
        int currentDisplayId = getWindowManager().getDefaultDisplay().getDisplayId();
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        settingsIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(currentDisplayId);
        startActivity(settingsIntent, options.toBundle());
        finish();
    }
}
