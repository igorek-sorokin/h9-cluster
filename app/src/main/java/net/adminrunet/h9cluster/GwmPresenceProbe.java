package net.adminrunet.h9cluster;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

/**
 * One-shot GWM adapter ping used after ACC off. Does not start FDBus/TBOX
 * readers and unbinds immediately after the first successful GET_DATA.
 */
final class GwmPresenceProbe implements ServiceConnection {
    interface Callback {
        void onVehicleAlive();

        void onFinished();
    }

    private static final String TAG = "H9ClusterPower";
    private static final String SERVICE_PACKAGE = "com.gwm.android.adapter";
    private static final String SERVICE_CLASS =
            "com.gwm.android.adapter.server.GwmAdapterService";
    private static final String SERVICE_DESCRIPTOR =
            "com.gwm.android.adapter.IGwmAdapter";
    private static final int TRANSACTION_GET_DATA = 1;
    private static final long TIMEOUT_MS = 2500L;

    private static final String[] PING_IDS = new String[] {
            "car.basic.vehicle_speed",
            "car.basic.battery_voltage"
    };

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private boolean bound;
    private boolean finished;
    private final Runnable timeoutTask = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    GwmPresenceProbe(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    void start() {
        Intent intent = new Intent();
        intent.setClassName(SERVICE_PACKAGE, SERVICE_CLASS);
        try {
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException error) {
            Log.w(TAG, "GWM ping bind failed", error);
            finish();
            return;
        }
        if (!bound) {
            finish();
            return;
        }
        handler.postDelayed(timeoutTask, TIMEOUT_MS);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        boolean alive = false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeInt(1);
            data.writeString(context.getPackageName());
            data.writeStringArray(PING_IDS);
            data.writeStringArray(null);
            data.writeInt(1);
            if (service.transact(TRANSACTION_GET_DATA, data, reply, 0)) {
                reply.readException();
                String[] response = reply.createStringArray();
                alive = response != null && response.length > 0;
            }
        } catch (Throwable error) {
            Log.w(TAG, "GWM ping failed", error);
        } finally {
            data.recycle();
            reply.recycle();
        }
        if (alive && !finished) {
            Log.i(TAG, "GWM ping ok — vehicle link alive");
            callback.onVehicleAlive();
        }
        finish();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacks(timeoutTask);
        if (bound) {
            try {
                context.unbindService(this);
            } catch (RuntimeException ignored) {
            }
            bound = false;
        }
        callback.onFinished();
    }
}
