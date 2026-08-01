package net.adminrunet.h9cluster;

import android.util.Log;

/**
 * Tracks whether the vehicle data path still looks "awake".
 * On Haval, ACC/ignition off often stops GWM updates without SCREEN_OFF.
 */
public final class VehicleAwakeTracker {
    private static final String TAG = "H9ClusterPower";
    private static final long STALE_TELEMETRY_MS = 4000L;

    private static final VehicleAwakeTracker INSTANCE = new VehicleAwakeTracker();

    private volatile long lastTelemetryAtMs;
    private volatile boolean everReceivedTelemetry;

    private VehicleAwakeTracker() {
    }

    public static VehicleAwakeTracker get() {
        return INSTANCE;
    }

    public void onTelemetry() {
        everReceivedTelemetry = true;
        lastTelemetryAtMs = android.os.SystemClock.elapsedRealtime();
    }

    public void reset() {
        lastTelemetryAtMs = 0L;
        everReceivedTelemetry = false;
    }

    public boolean isStale(long nowElapsedRealtimeMs) {
        if (!everReceivedTelemetry || lastTelemetryAtMs <= 0L) {
            return false;
        }
        long age = nowElapsedRealtimeMs - lastTelemetryAtMs;
        boolean stale = age >= STALE_TELEMETRY_MS;
        if (stale) {
            Log.i(TAG, "Telemetry stale for " + age + " ms");
        }
        return stale;
    }

    public boolean hasEverReceivedTelemetry() {
        return everReceivedTelemetry;
    }

    public long lastTelemetryAgeMs(long nowElapsedRealtimeMs) {
        if (lastTelemetryAtMs <= 0L) {
            return -1L;
        }
        return nowElapsedRealtimeMs - lastTelemetryAtMs;
    }
}
