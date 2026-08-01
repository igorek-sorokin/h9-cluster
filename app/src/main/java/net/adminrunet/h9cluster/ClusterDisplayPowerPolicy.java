package net.adminrunet.h9cluster;

import android.view.Display;

/** Maps host-display / power signals to cluster overlay actions. */
public final class ClusterDisplayPowerPolicy {
    static final int BRIGHTNESS_OFF_THRESHOLD = 0;

    private ClusterDisplayPowerPolicy() {
    }

    public static boolean shouldReleaseCluster(int displayState) {
        return displayState == Display.STATE_OFF
                || displayState == Display.STATE_DOZE
                || displayState == Display.STATE_DOZE_SUSPEND;
    }

    public static boolean shouldStartCluster(int displayState) {
        return displayState == Display.STATE_ON
                || displayState == Display.STATE_ON_SUSPEND;
    }

    public static boolean shouldReleaseForBrightness(int brightness) {
        return brightness >= 0 && brightness <= BRIGHTNESS_OFF_THRESHOLD;
    }

    public static boolean shouldStartForBrightness(int brightness) {
        return brightness > BRIGHTNESS_OFF_THRESHOLD;
    }

    public static boolean shouldReleaseForInteractive(boolean interactive) {
        return !interactive;
    }
}
