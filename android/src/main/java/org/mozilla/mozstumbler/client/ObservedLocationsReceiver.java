/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.client.mapview.ObservationPoint;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ObservedLocationsReceiver extends BroadcastReceiver {

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ObservedLocationsReceiver.class);


    private static final int MAX_QUEUED_MLS_POINTS_TO_FETCH = 10;
    private static final long FREQ_FETCH_MLS_MS = 5 * 1000;
    private static ObservedLocationsReceiver sInstance;
    private final List<ObservationPoint> mCollectionPoints = Collections.synchronizedList(new LinkedList<ObservationPoint>());
    private final List<ObservationPoint> mQueuedForMLS =
            Collections.synchronizedList(new LinkedList<ObservationPoint>());
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Upper bound on the size of the linked lists of points, for memory and performance safety.
    // On older devices, store fewer observations
    private final int MAX_SIZE_OF_POINT_LISTS = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) ?
            5000 : 2500;
    private WeakReference<MapFragment> mMapActivity = new WeakReference<MapFragment>(null);
    private Context mContext;

    private ObservedLocationsReceiver() {
    }

    public static void createGlobalInstance(Context context) {
        sInstance = new ObservedLocationsReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Reporter.ACTION_NEW_BUNDLE);
        LocalBroadcastManager.getInstance(context).registerReceiver(sInstance, intentFilter);
    }

    public static ObservedLocationsReceiver getInstance() {
        return sInstance;
    }

    public synchronized List<ObservationPoint> getObservationPoints_callerMustLock() {
        return mCollectionPoints;
   }

    // Must call when map activity stopped, to clear the reference to the map activity object
    public void removeMapActivity() {
        setMapActivity(null);
    }

    private synchronized MapFragment getMapActivity() {
        return mMapActivity.get();
    }

    private final Runnable mFetchMLSRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (ObservedLocationsReceiver.this) {
                if (mContext == null) {
                    return;
                }
                ClientPrefs prefs = ClientPrefs.getInstance(mContext);
                NetworkInfo networkInfo = new NetworkInfo(mContext);

                mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
                if (mQueuedForMLS.size() < 1 || !prefs.showMLSQueryResults()) {
                    return;
                }
                int count = 0;

                List<ObservationPoint> queued;
                synchronized (mQueuedForMLS) {
                    queued = new LinkedList<ObservationPoint>(mQueuedForMLS);
                }
                Iterator<ObservationPoint> li = queued.iterator();
                while (li.hasNext() && count < MAX_QUEUED_MLS_POINTS_TO_FETCH) {
                    ObservationPoint obs = li.next();
                    if (obs.needsToFetchMLS() && mContext != null) {
                        obs.fetchMLS(networkInfo.isConnected(),
                                     networkInfo.isWifiAvailable());
                        count++;
                    } else {
                        if (getMapActivity() != null && obs.pointMLS != null) {
                            getMapActivity().newMLSPoint(obs);
                        }
                        li.remove();
                    }
                }
            }
        }
    };

    // Must be called by map activity when it is showing to get points displayed
    public synchronized void setMapActivity(MapFragment m) {
        mMapActivity = new WeakReference<MapFragment>(m);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mContext == null) {
            // the first time here, start this queue handling runnable
            mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
        }
        mContext = context;

        ClientPrefs prefs = ClientPrefs.getInstance(context);
        if (prefs == null) {
            return;
        }

        final String action = intent.getAction();
        if (!action.equals(Reporter.ACTION_NEW_BUNDLE)) {
            return;
        }

        final StumblerBundle bundle = intent.getParcelableExtra(Reporter.NEW_BUNDLE_ARG_BUNDLE);
        if (bundle == null) {
            return;
        }

        Location position = bundle.getGpsPosition();
        if (position == null) {
            return;
        }
        ObservationPoint observation = new ObservationPoint(position);
        observation.mTrackSegment = bundle.getTrackSegment();

        try {
            observation.setCounts(bundle.toMLSGeosubmit());

            boolean getInfoForMLS = prefs.showMLSQueryResults() && bundle.hasRadioData();
            if (getInfoForMLS) {
                observation.setMLSQuery(bundle);

                if (mQueuedForMLS.size() < MAX_SIZE_OF_POINT_LISTS) {
                    mQueuedForMLS.add(0, observation);
                }
            }
        } catch (JSONException e) {
            ClientLog.w(LOG_TAG, "Failed to convert bundle to JSON: " + e);
        }

        synchronized(mCollectionPoints) {
            while (mCollectionPoints.size() > MAX_SIZE_OF_POINT_LISTS) {
                mCollectionPoints.remove(0);
            }

            if (mCollectionPoints.size() > 0 && !observation.pointGPS.hasBearing()) {
                final Location previous = mCollectionPoints.get(mCollectionPoints.size() - 1).pointGPS;
                observation.pointGPS.setBearing(previous.bearingTo(observation.pointGPS));
            }
            mCollectionPoints.add(observation);
        }

        if (getMapActivity() == null) {
            return;
        }

        if (bundle.hasRadioData()) {
            getMapActivity().getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addObservationPointToMap();
                }
            });
        }
    }

    private void addObservationPointToMap() {
        if (getMapActivity() == null) {
            return;
        }

        synchronized (mCollectionPoints) {
            getMapActivity().newObservationPoint(mCollectionPoints.get(mCollectionPoints.size() - 1));
        }
    }
}
