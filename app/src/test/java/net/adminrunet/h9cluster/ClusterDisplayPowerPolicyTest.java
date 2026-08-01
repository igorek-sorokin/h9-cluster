package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.Display;

import org.junit.Test;

public final class ClusterDisplayPowerPolicyTest {
    @Test
    public void blankHostDisplayReleasesCluster() {
        assertTrue(ClusterDisplayPowerPolicy.shouldReleaseCluster(Display.STATE_OFF));
        assertTrue(ClusterDisplayPowerPolicy.shouldReleaseCluster(Display.STATE_DOZE));
        assertTrue(ClusterDisplayPowerPolicy.shouldReleaseCluster(
                Display.STATE_DOZE_SUSPEND));
        assertFalse(ClusterDisplayPowerPolicy.shouldReleaseCluster(Display.STATE_ON));
    }

    @Test
    public void awakeHostDisplayStartsCluster() {
        assertTrue(ClusterDisplayPowerPolicy.shouldStartCluster(Display.STATE_ON));
        assertTrue(ClusterDisplayPowerPolicy.shouldStartCluster(Display.STATE_ON_SUSPEND));
        assertFalse(ClusterDisplayPowerPolicy.shouldStartCluster(Display.STATE_OFF));
    }

    @Test
    public void zeroBrightnessReleasesCluster() {
        assertTrue(ClusterDisplayPowerPolicy.shouldReleaseForBrightness(0));
        assertFalse(ClusterDisplayPowerPolicy.shouldReleaseForBrightness(20));
        assertTrue(ClusterDisplayPowerPolicy.shouldStartForBrightness(20));
    }

    @Test
    public void nonInteractiveReleasesCluster() {
        assertTrue(ClusterDisplayPowerPolicy.shouldReleaseForInteractive(false));
        assertFalse(ClusterDisplayPowerPolicy.shouldReleaseForInteractive(true));
    }
}
