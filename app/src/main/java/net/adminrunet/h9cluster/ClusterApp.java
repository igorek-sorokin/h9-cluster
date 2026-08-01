package net.adminrunet.h9cluster;

import android.app.Application;
import android.util.Log;

/** Process-wide hooks. Background power watch runs only when autostart is on
 * or while the cluster overlay is showing. */
public final class ClusterApp extends Application {
    private static final String TAG = "H9Cluster";
    private ClusterPowerController powerController;

    @Override
    public void onCreate() {
        super.onCreate();
        SkinPreferences.syncBootReceiver(this);
        powerController = new ClusterPowerController(this);
        if (SkinPreferences.isAutostartEnabled(this)) {
            powerController.start();
            Log.i(TAG, "Autostart on — power watch armed");
        } else {
            Log.i(TAG, "Autostart off — idle until user opens the app");
        }
    }
}
