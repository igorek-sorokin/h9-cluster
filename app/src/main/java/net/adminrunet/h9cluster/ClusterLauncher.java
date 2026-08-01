package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

/** Starts the cluster activity only on the confirmed instrument display. */
public final class ClusterLauncher {
    private static final String TAG = "GWMClusterLauncher";

    private ClusterLauncher() {
    }

    public static boolean startOnClusterDisplay(Context context) {
        return startOnClusterDisplay(context, false);
    }

    /**
     * @param userInitiated true when the user pressed Save / left settings;
     *                      false for boot and ACC auto-resume (respects autostart flag)
     */
    public static boolean startOnClusterDisplay(Context context, boolean userInitiated) {
        if (!userInitiated && !SkinPreferences.isAutostartEnabled(context)) {
            Log.i(TAG, "Skip launch: autostart disabled");
            return false;
        }
        if (!SkinRegistry.overlaysCluster(SkinPreferences.getSelectedSkin(context))) {
            return releaseClusterDisplay(context);
        }
        if (ClusterPowerController.isSuspendedByPower()) {
            Log.i(TAG, "Skip launch: suspended after ignition/ACC off");
            return false;
        }
        return launchOnClusterDisplay(context, null);
    }

    static boolean previewOnClusterDisplay(
            Context context,
            SkinSettingsSession.Snapshot draft) {
        ClusterPowerController.clearSuspendForUserLaunch();
        if (draft != null && !SkinRegistry.overlaysCluster(draft.skinId)) {
            return releaseClusterDisplay(context);
        }
        return launchOnClusterDisplay(context, draft);
    }

    /** Closes a running overlay so the stock cluster on Display ID 2 is visible. */
    static boolean releaseClusterDisplay(Context context) {
        PreviewActivity.forceRemoveOverlay(context);
        return true;
    }

    private static boolean launchOnClusterDisplay(
            Context context,
            SkinSettingsSession.Snapshot draft) {
        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        boolean clusterDisplayAvailable =
                displayManager != null && hasClusterDisplay(displayManager);
        int currentDisplayId = getCurrentDisplayId(context);
        int targetDisplayId = ClusterDisplayPolicy.resolveTargetDisplay(
                BuildConfig.DEMO_MODE,
                clusterDisplayAvailable,
                currentDisplayId);
        if (targetDisplayId == ClusterDisplayPolicy.NO_DISPLAY) {
            Log.w(TAG, "Display 2 is not ready");
            return false;
        }

        Intent intent = new Intent(context, PreviewActivity.class);
        if (draft == null) {
            intent.putExtra(PreviewActivity.EXTRA_RELOAD_SKIN, true);
        } else {
            intent.putExtra(PreviewActivity.EXTRA_HAS_DRAFT, true);
            intent.putExtra(PreviewActivity.EXTRA_DRAFT_SKIN, draft.skinId);
            intent.putExtra(
                    PreviewActivity.EXTRA_DRAFT_SETTINGS,
                    SkinSettingsTransport.toBundle(draft.settings));
        }
        intent.putExtra(
                PreviewActivity.EXTRA_SINGLE_DISPLAY_FALLBACK,
                ClusterDisplayPolicy.isSingleDisplayFallback(
                        BuildConfig.DEMO_MODE,
                        targetDisplayId));
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(targetDisplayId);
        try {
            context.startActivity(intent, options.toBundle());
            ClusterPowerController.ensureRunning();
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot start cluster on Display " + targetDisplayId, error);
            return false;
        }
    }

    private static boolean hasClusterDisplay(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == ClusterDisplayPolicy.CLUSTER_DISPLAY_ID) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static int getCurrentDisplayId(Context context) {
        if (context instanceof Activity) {
            return ((Activity) context)
                    .getWindowManager()
                    .getDefaultDisplay()
                    .getDisplayId();
        }
        return Display.DEFAULT_DISPLAY;
    }
}
