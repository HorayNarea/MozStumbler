/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.os.Handler;
import android.os.SystemClock;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unused")
public class SimulatorService implements ISimulatorService {
    private static final ILogger Log = (ILogger) ServiceLocator.getInstance()
            .getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(SimulatorService.class);

    public final static int SIMULATION_PING_INTERVAL = 500 * 1; // Every half second

    private Context ctx;
    Handler handler = new Handler();

    private double mLon;
    private double mLat;

    private LocationManager locationManager;

    private boolean keepRunning;

    @SuppressWarnings("unused")
    public SimulatorService() {}


    @Override
    public synchronized void startSimulation(Context c) {
        ctx = c.getApplicationContext();

        mLat = Prefs.getInstance(ctx).getSimulationLat();
        mLon = Prefs.getInstance(ctx).getSimulationLon();

        locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        // Register test provider for GPS
        final boolean REQUIRED_NETWORK = false;
        final boolean REQUIRES_SATELLITE = false;
        final boolean REQUIRES_CELL = false;
        final boolean HAS_MONETARY_COST = false;
        final boolean SUPPORTS_ALTITUDE = false;
        final boolean SUPPORTS_SPEED = false;
        final boolean SUPPORTS_BEARING = false;
        final int POWER_REQUIREMENT = 0;
        final int ACCURACY = 5;

        locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                REQUIRED_NETWORK, REQUIRES_SATELLITE,
                REQUIRES_CELL, HAS_MONETARY_COST, SUPPORTS_ALTITUDE, SUPPORTS_SPEED,
                SUPPORTS_BEARING, POWER_REQUIREMENT, ACCURACY);
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,
                true);

        keepRunning = true;


        handler.postDelayed(new Runnable() {
            public void run() {
                Location mockLocation = getNextGPSLocation();
                if (mockLocation == null) {
                    return;
                }

                synchronized (this) {
                    if (keepRunning) {
                        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);
                        // Send another ping
                        handler.postDelayed(this, SIMULATION_PING_INTERVAL);
                    }
                }
            }
        }, SIMULATION_PING_INTERVAL);
    }

    @Override
    public synchronized void stopSimulation() {
        keepRunning = false;

        try {
            if (locationManager != null) {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (IllegalArgumentException ex) {
            // no test provider was registered.  Shouldn't happen but it's totally safe.
        }
    }


    @Override
    public Location getNextGPSLocation() {
        Location mockLocation = new Location(LocationManager.GPS_PROVIDER); // a string
        mockLocation.setLatitude(mLat);
        mockLocation.setLongitude(mLon);
        mockLocation.setAltitude(42.0);  // meters above sea level
        mockLocation.setAccuracy(5);
        mockLocation.setTime(System.currentTimeMillis());

        mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        // This is rougly ~5m-ish.
        mLat += 0.00003;
        mLon += 0.00003;

        return mockLocation;    }

    @Override
    public List<ScanResult> getNextMockWifiBlock() {
        LinkedList<ScanResult> resultList = new LinkedList<ScanResult>();

        ScanResult scan = null;
        try {
            scan = makeScanResult();
            scan.BSSID = "E0:3F:49:98:A6:A4";
            resultList.add(scan);

            scan = makeScanResult();
            scan.BSSID = "E0:3F:49:98:A6:A0";
            resultList.add(scan);
        } catch (Exception e) {
            ClientLog.e(LOG_TAG, "Error creating a mock ScanResult", e);
        }

        return resultList;
    }

    @Override
    public List<CellInfo> getNextMockCellBlock() {
        LinkedList<CellInfo> result = new LinkedList<CellInfo>();

        try {
            CellInfo cell = new CellInfo();
            cell.setGsmCellInfo(1, 1, 60330, 1660199, 19);
            result.add(cell);
        } catch (Exception e) {
            ClientLog.e(LOG_TAG, "Error creating a mock CellInfo block", e);
        }
        return result;
    }

    private ScanResult makeScanResult() throws IllegalAccessException,
            InstantiationException,
            InvocationTargetException {

        Constructor<?>[] ctors = ScanResult.class.getDeclaredConstructors();
        for (Constructor<?> ctor : ctors) {
            int len = ctor.getParameterTypes().length;
            if (len == 5) {
                ctor.setAccessible(true);
                return (ScanResult) ctor.newInstance("", "", "", 0, 0);
            }
            if (len == 6) { // Android 4.4.3 has this constructor
                ctor.setAccessible(true);
                return (ScanResult) ctor.newInstance(null, "", "", 0, 0, 0);
            }
        }
        return null;
    }

}
