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
 * Brief GWM listen used after ACC off. A static GET_DATA reply is not enough —
 * the adapter often still returns cached values while the car is asleep. Wake is
 * confirmed only when the live listener pushes at least one update.
 */
final class GwmPresenceProbe implements ServiceConnection, ReadOnlyDataListener.Callback {
    interface Callback {
        void onVehicleAlive();

        void onFinished();
    }

    private static final String TAG = "H9ClusterPower";
    private static final String SERVICE_PACKAGE = "com.gwm.android.adapter.server";
    private static final String SERVICE_CLASS =
            "com.gwm.android.adapter.server.GwmAdapterService";
    private static final String SERVICE_DESCRIPTOR =
            "com.gwm.android.adapter.IGwmAdapterService";
    private static final int TRANSACTION_REGISTER_LISTENER = 3;
    private static final int TRANSACTION_UNREGISTER_LISTENER = 4;
    private static final long LISTEN_WINDOW_MS = 3500L;

    private static final String[] WATCH_IDS = new String[] {
            "car.basic.vehicle_speed",
            "car.basic.engine_speed",
            "car.basic.battery_voltage",
            "car.basic.outside_temp",
            "car.basic.steering_wheel_angle"
    };

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private final ReadOnlyDataListener dataListener;
    private IBinder service;
    private boolean bound;
    private boolean listenerRegistered;
    private boolean finished;
    private boolean sawPush;
    private final Runnable timeoutTask = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    GwmPresenceProbe(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.dataListener = new ReadOnlyDataListener(handler, this);
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
        handler.postDelayed(timeoutTask, LISTEN_WINDOW_MS);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = binder;
        listenerRegistered = registerListener();
        if (!listenerRegistered) {
            Log.w(TAG, "GWM ping listener register failed");
            finish();
            return;
        }
        Log.i(TAG, "GWM ping listening for live pushes");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
        listenerRegistered = false;
        finish();
    }

    @Override
    public void onDataChanged(String id, String value) {
        if (finished || id == null) {
            return;
        }
        for (String watchId : WATCH_IDS) {
            if (watchId.equals(id)) {
                if (!sawPush) {
                    sawPush = true;
                    Log.i(TAG, "GWM ping got live push: " + id);
                    callback.onVehicleAlive();
                }
                finish();
                return;
            }
        }
    }

    private boolean registerListener() {
        IBinder current = service;
        if (current == null) {
            return false;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeString(context.getPackageName());
            data.writeStringArray(WATCH_IDS);
            data.writeStrongBinder(dataListener.asBinder());
            if (!current.transact(TRANSACTION_REGISTER_LISTENER, data, reply, 0)) {
                return false;
            }
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable error) {
            Log.w(TAG, "GWM ping register failed", error);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void unregisterListener() {
        IBinder current = service;
        if (!listenerRegistered || current == null) {
            listenerRegistered = false;
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeString(context.getPackageName());
            data.writeStrongBinder(dataListener.asBinder());
            if (current.transact(TRANSACTION_UNREGISTER_LISTENER, data, reply, 0)) {
                reply.readException();
                reply.readInt();
            }
        } catch (Throwable ignored) {
        } finally {
            listenerRegistered = false;
            data.recycle();
            reply.recycle();
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacks(timeoutTask);
        unregisterListener();
        service = null;
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
