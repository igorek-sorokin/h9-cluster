package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

/**
 * Removes the custom cluster overlay when the vehicle powers down and restores
 * the last selected theme when the vehicle wakes again.
 *
 * <p>Haval often blanks the HU without {@code SCREEN_OFF}. Overlay close is driven
 * by GWM telemetry going stale; wake is detected by rare background listens that
 * wait for live GWM pushes (cached GET_DATA alone is ignored).</p>
 */
final class ClusterPowerController {
    private static final String TAG = "H9ClusterPower";
    private static final int MAX_ATTEMPTS = 8;
    private static final long RETRY_DELAY_MS = 1500L;
    private static final long START_DEBOUNCE_MS = 800L;
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long RESUME_PING_INTERVAL_MS = 20000L;
    private static final long RESUME_PING_FIRST_DELAY_MS = 8000L;

    private static final String[] EXTRA_OFF_ACTIONS = {
            Intent.ACTION_SHUTDOWN,
            "android.intent.action.QUICKBOOT_POWEROFF",
            "android.intent.action.ACTION_SHUTDOWN",
            "android.intent.action.ACTION_ACC_OFF",
            "android.intent.action.ACC_OFF",
            "com.android.internal.car.ACC_OFF",
            "com.gwm.android.action.ACC_OFF",
            "com.gwm.android.action.POWER_OFF",
            "autosdk.intent.action.ACC_OFF",
            "com.yx.intent.action.ACC_OFF"
    };

    private static final String[] EXTRA_ON_ACTIONS = {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.ACTION_ACC_ON",
            "android.intent.action.ACC_ON",
            "com.android.internal.car.ACC_ON",
            "com.gwm.android.action.ACC_ON",
            "com.gwm.android.action.POWER_ON",
            "autosdk.intent.action.ACC_ON",
            "com.yx.intent.action.ACC_ON"
    };

    private static ClusterPowerController instance;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DisplayManager displayManager;
    private final PowerManager powerManager;
    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY
                            || displayId == ClusterDisplayPolicy.CLUSTER_DISPLAY_ID) {
                        releaseToFactory("display removed " + displayId);
                    }
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY
                            || displayId == ClusterDisplayPolicy.CLUSTER_DISPLAY_ID) {
                        evaluatePowerSignals("display-changed:" + displayId);
                    }
                }
            };
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            Log.i(TAG, "Power broadcast: " + action);
            if (Intent.ACTION_SCREEN_OFF.equals(action) || isOffAction(action)) {
                releaseToFactory(action);
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || isOnAction(action)) {
                clearSuspendAndStart(action);
            }
        }
    };
    private final ContentObserver brightnessObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            evaluatePowerSignals("brightness");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            evaluatePowerSignals("brightness");
        }
    };
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            evaluatePowerSignals("poll");
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private Runnable pendingStart;
    private Runnable startAttempts;
    private boolean suspendedByPower;
    private boolean registered;
    private boolean resumePingInFlight;
    private boolean resumePingsArmed;
    private final Runnable resumePingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!suspendedByPower || BuildConfig.DEMO_MODE) {
                resumePingsArmed = false;
                return;
            }
            if (!SkinRegistry.overlaysCluster(
                    SkinPreferences.getSelectedSkin(appContext))) {
                resumePingsArmed = false;
                return;
            }
            if (resumePingInFlight) {
                return;
            }
            resumePingInFlight = true;
            Log.i(TAG, "Periodic GWM presence ping (live pushes only)");
            new GwmPresenceProbe(appContext, new GwmPresenceProbe.Callback() {
                @Override
                public void onVehicleAlive() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (suspendedByPower) {
                                clearSuspendAndStart("gwm-live-push");
                            }
                        }
                    });
                }

                @Override
                public void onFinished() {
                    resumePingInFlight = false;
                    if (suspendedByPower) {
                        scheduleNextResumePing(RESUME_PING_INTERVAL_MS);
                    } else {
                        resumePingsArmed = false;
                    }
                }
            }).start();
        }
    };

    ClusterPowerController(Context context) {
        this.appContext = context.getApplicationContext();
        this.displayManager =
                (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        this.powerManager =
                (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        instance = this;
    }

    static void clearSuspendForUserLaunch() {
        ClusterPowerController controller = instance;
        if (controller != null) {
            controller.suspendedByPower = false;
            controller.stopResumePings();
        }
    }

    static boolean isSuspendedByPower() {
        ClusterPowerController controller = instance;
        return controller != null && controller.suspendedByPower;
    }

    void start() {
        if (registered || displayManager == null) {
            return;
        }
        displayManager.registerDisplayListener(displayListener, handler);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        for (String action : EXTRA_OFF_ACTIONS) {
            filter.addAction(action);
        }
        for (String action : EXTRA_ON_ACTIONS) {
            filter.addAction(action);
        }
        appContext.registerReceiver(powerReceiver, filter);
        try {
            appContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                    false,
                    brightnessObserver);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot watch screen brightness", error);
        }
        handler.post(pollRunnable);
        registered = true;
        evaluatePowerSignals("initial");
        Log.i(TAG, "Cluster power controller armed");
    }

    private void evaluatePowerSignals(String reason) {
        long now = SystemClock.elapsedRealtime();
        boolean interactive = powerManager == null || powerManager.isInteractive();
        int brightness = readBrightness();
        int hostState = readDisplayState(Display.DEFAULT_DISPLAY);
        // Telemetry stale only matters while the overlay is live. After suspend the
        // tracker is reset and a background GWM probe watches for wake.
        boolean telemetryStale = !BuildConfig.DEMO_MODE
                && !suspendedByPower
                && PreviewActivity.isShowing()
                && VehicleAwakeTracker.get().isStale(now);
        long telemetryAge = VehicleAwakeTracker.get().lastTelemetryAgeMs(now);

        boolean release = ClusterDisplayPowerPolicy.shouldReleaseForInteractive(interactive)
                || ClusterDisplayPowerPolicy.shouldReleaseCluster(hostState)
                || ClusterDisplayPowerPolicy.shouldReleaseForBrightness(brightness)
                || telemetryStale;

        Log.i(TAG, "Power eval ("
                + reason
                + "): interactive="
                + interactive
                + " brightness="
                + brightness
                + " hostState="
                + hostState
                + " telemetryAgeMs="
                + telemetryAge
                + " stale="
                + telemetryStale
                + " suspended="
                + suspendedByPower
                + " release="
                + release);

        if (release) {
            releaseToFactory(reason);
            return;
        }

        if (suspendedByPower) {
            ensureResumePings();
        }
    }

    private void releaseToFactory(String reason) {
        cancelPendingWork();
        boolean alreadySuspended = suspendedByPower;
        suspendedByPower = true;
        Log.i(TAG, "Switching to factory cluster (" + reason + ")");
        PreviewActivity.forceRemoveOverlay(appContext);
        VehicleAwakeTracker.get().reset();
        if (!BuildConfig.DEMO_MODE) {
            ensureResumePings();
        }
        if (!alreadySuspended) {
            Log.i(TAG, "Waiting for vehicle wake via rare GWM pings / ACC broadcasts");
        }
    }

    private void ensureResumePings() {
        if (BuildConfig.DEMO_MODE) {
            return;
        }
        if (!SkinRegistry.overlaysCluster(
                SkinPreferences.getSelectedSkin(appContext))) {
            stopResumePings();
            return;
        }
        // Do not re-post every poll tick — that cancelled the delay and
        // restarted the overlay from cached GWM replies.
        if (resumePingsArmed || resumePingInFlight) {
            return;
        }
        scheduleNextResumePing(RESUME_PING_FIRST_DELAY_MS);
    }

    private void scheduleNextResumePing(long delayMs) {
        resumePingsArmed = true;
        handler.removeCallbacks(resumePingRunnable);
        handler.postDelayed(resumePingRunnable, delayMs);
    }

    private void stopResumePings() {
        handler.removeCallbacks(resumePingRunnable);
        resumePingInFlight = false;
        resumePingsArmed = false;
    }

    private void clearSuspendAndStart(String reason) {
        if (!SkinRegistry.overlaysCluster(
                SkinPreferences.getSelectedSkin(appContext))) {
            suspendedByPower = false;
            stopResumePings();
            return;
        }
        suspendedByPower = false;
        stopResumePings();
        scheduleClusterStart(reason);
    }

    private void scheduleClusterStart(String reason) {
        if (!SkinRegistry.overlaysCluster(
                SkinPreferences.getSelectedSkin(appContext))) {
            return;
        }
        if (shouldReleaseNow()) {
            Log.i(TAG, "Skip start, still off (" + reason + ")");
            return;
        }
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
                if (shouldReleaseNow()) {
                    startAttempts = null;
                    releaseToFactory("start-aborted-still-off");
                    return;
                }
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

    private boolean shouldReleaseNow() {
        boolean interactive = powerManager == null || powerManager.isInteractive();
        int brightness = readBrightness();
        int hostState = readDisplayState(Display.DEFAULT_DISPLAY);
        return ClusterDisplayPowerPolicy.shouldReleaseForInteractive(interactive)
                || ClusterDisplayPowerPolicy.shouldReleaseCluster(hostState)
                || ClusterDisplayPowerPolicy.shouldReleaseForBrightness(brightness);
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

    private int readBrightness() {
        try {
            return Settings.System.getInt(
                    appContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    -1);
        } catch (RuntimeException error) {
            return -1;
        }
    }

    private int readDisplayState(int displayId) {
        if (displayManager == null) {
            return Display.STATE_UNKNOWN;
        }
        Display display = displayManager.getDisplay(displayId);
        return display == null ? Display.STATE_UNKNOWN : display.getState();
    }

    private static boolean isOffAction(String action) {
        for (String candidate : EXTRA_OFF_ACTIONS) {
            if (candidate.equals(action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnAction(String action) {
        for (String candidate : EXTRA_ON_ACTIONS) {
            if (candidate.equals(action)) {
                return true;
            }
        }
        return false;
    }
}
