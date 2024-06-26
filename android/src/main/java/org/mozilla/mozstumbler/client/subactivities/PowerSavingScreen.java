package org.mozilla.mozstumbler.client.subactivities;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.MotionSensor;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.SignificantMotionSensor;

public class PowerSavingScreen extends AppCompatActivity {

    TestSignificantMotionDialog mTesterDialog = new TestSignificantMotionDialog();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_saving_screen);

        setupMotionDetectionCheckbox();
        setupLowBatterySpinner();
        setupSensorRadio();

        if (AppGlobals.hasSignificantMotionSensor) {
            mTesterDialog.setupTestMotionButton();
        } else {
            findViewById(R.id.testSignificantMotionSensor).setVisibility(View.INVISIBLE);
        }
    }

    private void setupSensorRadio() {
        RadioGroup radioGroupSensorType = (RadioGroup) findViewById(R.id.radioGroupSensorType);
        if (ClientPrefs.getInstance(this).isMotionSensorTypeSignificant()) {
            radioGroupSensorType.check(R.id.radioSignificant);
        }

        radioGroupSensorType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean isAccelerometer = (checkedId == R.id.radioAccelerometer);
                ClientPrefs.getInstance(PowerSavingScreen.this).setMotionSensorTypeSignificant(!isAccelerometer);
                resetScanningToReflectChanges();
            }
        });

        String msg;
        if (AppGlobals.hasSignificantMotionSensor) {
            msg = getResources().getString(R.string.sensor_type_significant_motion);
        } else {
            msg = getResources().getString(R.string.sensor_type_legacy);
        }
        TextView infoMessage = (TextView) findViewById(R.id.message_sensor_type);
        infoMessage.setText(msg);
    }

    private void motionDetectionOptionsEnable(boolean on) {
        findViewById(R.id.titleUseSensor).setEnabled(on);
        findViewById(R.id.radioAccelerometer).setEnabled(on);
        findViewById(R.id.radioSignificant).setEnabled(on && AppGlobals.hasSignificantMotionSensor);
    }

    private void setupMotionDetectionCheckbox() {
        boolean isOn = ClientPrefs.getInstance(this).isMotionSensorEnabled();
        CheckBox motionDetectionCheckbox = (CheckBox) findViewById(R.id.checkbox_motion_detection);
        motionDetectionCheckbox.setChecked(isOn);
        motionDetectionOptionsEnable(isOn);
        motionDetectionCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ClientPrefs.getInstance(PowerSavingScreen.this).setMotionSensorEnabled(isChecked);
                resetScanningToReflectChanges();
                motionDetectionOptionsEnable(isChecked);
            }
        });
    }

    private void resetScanningToReflectChanges() {
        final MainApp app = ((MainApp) getApplication());
        if (app.isScanningOrPaused()) {
            app.stopScanning();
            app.startScanning();
        }
    }

    private void setupLowBatterySpinner() {
        Spinner batteryLevelSpinner = (Spinner) findViewById(R.id.spinnerBatteryPercent);
        final SpinnerAdapter spinnerAdapter = batteryLevelSpinner.getAdapter();
        assert (spinnerAdapter instanceof ArrayAdapter);
        @SuppressWarnings("unchecked")
        final ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerAdapter;
        final int percent = ClientPrefs.getInstance(this).getMinBatteryPercent();
        final int spinnerPosition = adapter.getPosition(percent + "%");
        batteryLevelSpinner.setSelection(spinnerPosition);

        batteryLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                String item = parent.getItemAtPosition(position).toString().replace("%", "");
                int percent = Integer.valueOf(item);
                ClientPrefs.getInstance(PowerSavingScreen.this).setMinBatteryPercent(percent);
            }

            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    class TestSignificantMotionDialog {
        private AlertDialog mDialogMotionTesting;
        private SignificantMotionSensor mSigSensor;

        BroadcastReceiver mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (mDialogMotionTesting != null) {
                    mDialogMotionTesting.dismiss();
                    mDialogMotionTesting = null;

                    Context c = PowerSavingScreen.this;
                    AlertDialog.Builder builder = new AlertDialog.Builder(c)
                            .setTitle(c.getString(R.string.test_significant_sensor_dialog_title))
                            .setMessage(getString(R.string.test_significant_sensor_confirmed_working))
                            .setNegativeButton(c.getString(android.R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                        }});

                    builder.create().show();
                }
            }
        };

        private void setupTestMotionButton() {
            Button button = (Button) findViewById(R.id.testSignificantMotionSensor);
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Context c = PowerSavingScreen.this;
                    AlertDialog.Builder builder = new AlertDialog.Builder(c)
                            .setTitle(c.getString(R.string.test_significant_sensor_dialog_title))
                            .setMessage(c.getString(R.string.test_significant_sensor_dialog_message))
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                public void onDismiss(DialogInterface dialog) {
                                    mSigSensor.stop();
                                    mDialogMotionTesting = null;
                                }
                            })
                            .setNegativeButton(c.getString(android.R.string.cancel),
                                    new Dialog.OnClickListener() {
                                        public void onClick(DialogInterface di, int which) {
                                            di.dismiss();
                                        }
                                    });
                    mDialogMotionTesting = builder.create();
                    mDialogMotionTesting.show();
                }
            });

            LocalBroadcastManager.getInstance(PowerSavingScreen.this).registerReceiver(mReceiver,
                    new IntentFilter(MotionSensor.ACTION_USER_MOTION_DETECTED));
            mSigSensor = SignificantMotionSensor.getSensor(PowerSavingScreen.this);
            mSigSensor.start();
        }
    }
}
