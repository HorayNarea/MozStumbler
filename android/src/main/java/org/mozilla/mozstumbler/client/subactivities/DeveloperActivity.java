/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.serialize.GPXFragment;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.LocationChangeSensor;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.MotionSensor;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.util.HashMap;

import static org.mozilla.mozstumbler.client.ClientDataStorageManager.sdcardArchivePath;

public class DeveloperActivity extends AppCompatActivity {

    private final String LOG_TAG = LoggerUtil.makeLogTag(DeveloperActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);
        if (savedInstanceState == null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.add(R.id.frame0, new GPXFragment());
            ft.add(R.id.frame1, new KMLFragment());
            ft.add(R.id.frame2, new DeveloperOptions());
            ft.commit();
        }

        TextView tv = (TextView) findViewById(R.id.textViewDeveloperTitle);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                final AlertDialog.Builder b = new AlertDialog.Builder(DeveloperActivity.this);
                final String[] menuList = {"ACRA Crash Test",
                        "Fake no motion", "Fake motion", "Battery Low", "Battery OK"};
                b.setTitle("Secret testing.. shhh.");
                b.setItems(menuList, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        switch (item) {
                            case 0:
                                // Trigger a bad HTTPS POST to see what the TLS stuff does.
                                IHttpUtil httpUtil = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);
                                httpUtil.post("https://location.services.mozilla.com/",
                                        new byte[0],
                                        new HashMap<String, String>(),
                                        false);
                                Object a = null;
                                a.hashCode();
                                break;
                            case 1:
                                LocationChangeSensor.debugSendLocationUnchanging();
                                break;
                            case 2:
                                MotionSensor.debugMotionDetected();
                                break;
                            case 3:
                                int pct = ClientPrefs.getInstance(DeveloperActivity.this).getMinBatteryPercent();
                                BatteryCheckReceiver.debugSendBattery(pct - 1);
                                break;
                            case 4:
                                BatteryCheckReceiver.debugSendBattery(99);
                                break;
                        }
                    }
                });
                b.create().show();
                return true;
            }
        });
    }

    // For misc developer options
    public static class DeveloperOptions extends Fragment {
        private final String LOG_TAG = LoggerUtil.makeLogTag(DeveloperOptions.class);

        private View mRootView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_developer_options, container, false);

            // Setup for any logical group of config options should self contained in their
            // own methods.  This is mostly to help with merges in the event that multiple
            // source branches update the developer options.
            setupOfflineGeoToggle();
            setupHighPowerMode();
            setupSaveJSONLogs();
            setupSimulationPreference();
            setupLocationChangeSpinners();
            setupMinPauseTime();
            setupPassiveMode();
            return mRootView;
        }

        private void checkRestartScanning() {
            MainApp mainApp = ((MainApp) getActivity().getApplication());
            if (mainApp.isScanningOrPaused()) {
                mainApp.stopScanning();
                mainApp.startScanning();
            }
        }

        private void setupOfflineGeoToggle() {
            boolean useOfflineGeo = Prefs.getInstance(mRootView.getContext()).useOfflineGeo();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleOfflineGeo);
            button.setChecked(useOfflineGeo);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    // do nothing here as we are just going to query the value
                    // when we trigger an AsyncGeolocate call
                    Prefs.getInstance(mRootView.getContext()).setOfflineGeo(isChecked);
                }
            });

        }

        private void setupHighPowerMode() {
            boolean isHighPowerMode = Prefs.getInstance(mRootView.getContext()).isHighPowerMode();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleHighPowerMode);
            button.setChecked(isHighPowerMode);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    ClientPrefs.getInstance(mRootView.getContext()).setIsHighPowerMode(isChecked);
                    checkRestartScanning();
                }
            });

        }

        private void setupPassiveMode() {
            boolean isPassive = ClientPrefs.getInstance(mRootView.getContext()).isScanningPassive();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.togglePassiveScanning);
            button.setChecked(isPassive);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    ClientPrefs.getInstance(mRootView.getContext()).setIsScanningPassive(isChecked);
                    checkRestartScanning();
                }
            });
        }
        
        private void setupMinPauseTime() {
            ClientPrefs cPrefs = ClientPrefs.getInstance(mRootView.getContext());
            final String[] timeArray = {"5 s", "10 s", "20 s", "30 s", "60 s", "120 s"};
            final ArrayAdapter<String> timeAdapter =
                    new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, timeArray);
            final Spinner timeSpinner = (Spinner) mRootView.findViewById(R.id.spinnerMotionDetectionPauseTimeSeconds);
            timeSpinner.setAdapter(timeAdapter);
            final int time = (int) cPrefs.getMotionDetectionMinPauseTime();
            timeSpinner.setSelection(findIndexOf(time, timeArray));

            timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                    String item = parent.getItemAtPosition(position).toString();
                    int val = Integer.valueOf(item.substring(0, item.indexOf(" ")));
                    Prefs.getInstance(mRootView.getContext()).setMotionDetectionMinPauseTime(val);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
        }

        private void setupSaveJSONLogs() {
            boolean saveStumbleLogs = Prefs.getInstance(mRootView.getContext()).isSaveStumbleLogs();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleSaveStumbleLogs);
            button.setChecked(saveStumbleLogs);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    onToggleSaveStumbleLogs(isChecked);
                }
            });
        }

        private void onToggleSimulation(boolean isChecked) {
            Prefs.getInstance(mRootView.getContext()).setSimulateStumble(isChecked);
        }

        private void setupSimulationPreference() {
            boolean simulationEnabled = Prefs.getInstance(mRootView.getContext()).isSimulateStumble();
            final CheckBox simCheckBox = (CheckBox) mRootView.findViewById(R.id.toggleSimulation);
            final Button simResetBtn = (Button) mRootView.findViewById(R.id.buttonClearSimulationDefault);

            if (!AppGlobals.isDebug) {
                simCheckBox.setEnabled(false);
                simResetBtn.setEnabled(false);
                return;
            }

            simCheckBox.setChecked(simulationEnabled);
            simCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onToggleSimulation(isChecked);
                }
            });

            simResetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(android.view.View view) {
                    ClientPrefs cPrefs = ClientPrefs.getInstance(mRootView.getContext());
                    cPrefs.clearSimulationStart();
                    Context btnCtx = simResetBtn.getContext();
                    Toast.makeText(btnCtx,
                            btnCtx.getText(R.string.reset_simulation_start),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }


        private void setupLocationChangeSpinners() {
            final ClientPrefs cPrefs = ClientPrefs.getInstance(mRootView.getContext());
            final String[] distanceArray = {"30 m", "50 m", "75 m", "100 m", "125 m", "150 m", "175 m", "200 m"};
            final ArrayAdapter<String> distanceAdapter =
                    new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, distanceArray);
            final Spinner distanceSpinner = (Spinner) mRootView.findViewById(R.id.spinnerMotionDetectionDistanceMeters);
            distanceSpinner.setAdapter(distanceAdapter);
            final int dist = cPrefs.getMotionChangeDistanceMeters();
            distanceSpinner.setSelection(findIndexOf(dist, distanceArray));

            distanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                    changeOfMotionDetectionDistanceOrTime(parent, position, IsDistanceOrTime.DISTANCE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });

            final String[] timeArray = {"5 s", "30 s", "60 s", "90 s", "120 s", "180 s", "210 s", "240 s", "270 s", "300 s"};
            final ArrayAdapter<String> timeAdapter =
                    new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, timeArray);
            final Spinner timeSpinner = (Spinner) mRootView.findViewById(R.id.spinnerMotionDetectionTimeSeconds);
            timeSpinner.setAdapter(timeAdapter);
            final int time = cPrefs.getMotionChangeTimeWindowSeconds();
            timeSpinner.setSelection(findIndexOf(time, timeArray));

            timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                    changeOfMotionDetectionDistanceOrTime(parent, position, IsDistanceOrTime.TIME);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
        }

        private void onToggleSaveStumbleLogs(boolean isChecked) {
            if (isChecked) {
                Context viewCtx = mRootView.getContext();
                if (!archiveDirCreatedAndMounted()) {

                    Toast.makeText(viewCtx,
                            viewCtx.getString(R.string.create_log_archive_failure),
                            Toast.LENGTH_SHORT).show();
                    isChecked = false;
                    CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleSaveStumbleLogs);
                    button.setChecked(isChecked);
                } else {
                    Toast.makeText(viewCtx,
                            viewCtx.getString(R.string.create_log_archive_success) +
                                    sdcardArchivePath(),
                            Toast.LENGTH_LONG).show();
                }
            }
            Prefs.getInstance(mRootView.getContext()).setSaveStumbleLogs(isChecked);
        }

        public boolean archiveDirCreatedAndMounted() {
            File saveDir = new File(sdcardArchivePath());
            String storageState = Environment.getExternalStorageState();

            // You have to check the mount state of the external storage.
            // Using the mkdirs() result isn't good enough.
            if (!storageState.equals(Environment.MEDIA_MOUNTED)) {
                return false;
            }

            saveDir.mkdirs();
            if (!saveDir.exists()) {
                return false;
            }
            ClientLog.d(LOG_TAG, "Created: [" + saveDir.getAbsolutePath() + "]");
            return true;
        }

        private void changeOfMotionDetectionDistanceOrTime(AdapterView<?> parent, int position, IsDistanceOrTime isDistanceOrTime) {
            String item = parent.getItemAtPosition(position).toString();
            final int val = Integer.valueOf(item.substring(0, item.indexOf(" ")));
            boolean changed;
            ClientPrefs prefs = ClientPrefs.getInstance(getActivity().getApplicationContext());
            if (isDistanceOrTime == IsDistanceOrTime.DISTANCE) {
                changed = (val != prefs.getMotionChangeDistanceMeters());
                prefs.setMotionChangeDistanceMeters(val);
            } else {
                changed = (val != prefs.getMotionChangeTimeWindowSeconds());
                prefs.setMotionChangeTimeWindowSeconds(val);
            }

            // avoid restart when initially setting the pref
            if (changed) {
                checkRestartScanning();
            }
        }

        private int findIndexOf(int needle, String[] haystack) {
            int i = 0;
            for (String item : haystack) {
                int val = Integer.valueOf(item.substring(0, item.indexOf(" ")));
                if (val == needle) {
                    return i;
                }
                i++;
            }
            return 0;
        }

        private enum IsDistanceOrTime {DISTANCE, TIME}
    }
}
