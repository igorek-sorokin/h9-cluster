package net.adminrunet.h9cluster;

import android.app.Application;

/** Process-wide hooks that must outlive PreviewActivity (screen off / on). */
public final class ClusterApp extends Application {
    private ClusterPowerController powerController;

    @Override
    public void onCreate() {
        super.onCreate();
        powerController = new ClusterPowerController(this);
        powerController.start();
    }
}
