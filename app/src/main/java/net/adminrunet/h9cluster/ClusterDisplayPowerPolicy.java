package net.adminrunet.h9cluster;

import android.view.Display;

/** Maps host-display power states to cluster overlay actions. */
public final class ClusterDisplayPowerPolicy {
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
}
