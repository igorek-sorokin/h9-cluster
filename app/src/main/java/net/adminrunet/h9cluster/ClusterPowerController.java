package net.adminrunet.h9cluster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

/**
 * Closes the Display ID 2 overlay when the head-unit display blanks (ignition /
 * ACC off) and brings it back when the host display wakes.
 *
 * <p>{@link PreviewActivity} uses {@code FLAG_KEEP_SCREEN_ON}, so Display 2 would
 * otherwise stay lit after the main screen goes dark.</p>
 */
final class ClusterPowerController {
    private static final String TAG = "H9ClusterPower";
    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_DELAY_MS = 1500L;
    private static final long START_DEBOUNCE_MS = 800L;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DisplayManager displayManager;
    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        releaseCluster("default display removed");
                    }
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId != Display.DEFAULT_DISPLAY) {
                        return;
                    }
                    Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                    if (display == null) {
                        releaseCluster("default display missing");
                        return;
                    }
                    handleDisplayState(display.getState());
                }
            };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                releaseCluster("SCREEN_OFF");
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                scheduleClusterStart("SCREEN_ON");
            }
        }
    };

    private Runnable pendingStart;
    private Runnable startAttempts;
    private int lastHandledState = Display.STATE_UNKNOWN;
    private boolean registered;

    ClusterPowerController(Context context) {
        this.appContext = context.getApplicationContext();
        this.displayManager =
                (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
    }

    void start() {
        if (registered || displayManager == null) {
            return;
        }
        displayManager.registerDisplayListener(displayListener, handler);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        appContext.registerReceiver(screenReceiver, filter);
        registered = true;

        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null) {
            lastHandledState = display.getState();
            if (ClusterDisplayPowerPolicy.shouldReleaseCluster(lastHandledState)) {
                releaseCluster("initial host display blank");
            }
        }
        Log.i(TAG, "Cluster power controller armed");
    }

    void stop() {
        if (!registered) {
            return;
        }
        cancelPendingWork();
        try {
            appContext.unregisterReceiver(screenReceiver);
        } catch (RuntimeException ignored) {
            // Already unregistered.
        }
        displayManager.unregisterDisplayListener(displayListener);
        registered = false;
    }

    private void handleDisplayState(int displayState) {
        if (displayState == lastHandledState) {
            return;
        }
        lastHandledState = displayState;
        if (ClusterDisplayPowerPolicy.shouldReleaseCluster(displayState)) {
            releaseCluster("display state " + displayState);
        } else if (ClusterDisplayPowerPolicy.shouldStartCluster(displayState)) {
            scheduleClusterStart("display state " + displayState);
        }
    }

    private void releaseCluster(String reason) {
        cancelPendingWork();
        Log.i(TAG, "Releasing cluster overlay (" + reason + ")");
        PreviewActivity.closeIfShowing();
    }

    private void scheduleClusterStart(String reason) {
        cancelPendingWork();
        Log.i(TAG, "Scheduling cluster start (" + reason + ")");
        pendingStart = new Runnable() {
            @Override
            public void run() {
                pendingStart = null;
                startWithRetries();
            }
        };
        handler.postDelayed(pendingStart, START_DEBOUNCE_MS);
    }

    private void startWithRetries() {
        startAttempts = new Runnable() {
            private int attempt;

            @Override
            public void run() {
                attempt++;
                if (ClusterLauncher.startOnClusterDisplay(appContext)
                        || attempt >= MAX_ATTEMPTS) {
                    startAttempts = null;
                    return;
                }
                handler.postDelayed(this, RETRY_DELAY_MS);
            }
        };
        handler.post(startAttempts);
    }

    private void cancelPendingWork() {
        if (pendingStart != null) {
            handler.removeCallbacks(pendingStart);
            pendingStart = null;
        }
        if (startAttempts != null) {
            handler.removeCallbacks(startAttempts);
            startAttempts = null;
        }
    }
}
