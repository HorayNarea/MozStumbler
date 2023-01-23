package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;

/**
 * Created by victorng on 2016-04-07.
 */
public class PressureScanner implements SensorEventListener, IHaltable {
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".PressureScanner.";
    public static final String ACTION_PRESSURE_SCANNED = ACTION_BASE + "PRESSURE_SCANNED";
    public static final String ACTION_PRESSURE_SCANNED_PRESSURE = ACTION_BASE + ".pressure_reading";

    private final Context mAppContext;

    private Sensor mPressure;
    private SensorManager snsMgr;
    private boolean mStarted;


    public PressureScanner(Context appContext) {
        mAppContext = appContext;

        snsMgr = (SensorManager) mAppContext.getSystemService(Service.SENSOR_SERVICE);
        if (snsMgr != null) {
            mPressure = snsMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);
        }
    }

    public synchronized void start() {
        if (mPressure == null) {
            // No pressure sensor exists
            return;
        }
        mStarted = true;
        snsMgr.registerListener(this, mPressure, SensorManager.SENSOR_DELAY_NORMAL);
    }


    public synchronized void stop() {
        if (mStarted) {
            snsMgr.unregisterListener(this);
        }
        mStarted = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event != null && event.values != null && event.values.length > 0) {
            float millibars_of_pressure = event.values[0];
            Intent i = new Intent(ACTION_PRESSURE_SCANNED);
            i.putExtra(ACTION_PRESSURE_SCANNED_PRESSURE, millibars_of_pressure);
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(i);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }
}
