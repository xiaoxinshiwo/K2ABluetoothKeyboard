package mzrw.k2aplugin.bluetoothkeyboard;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import mzrw.k2aplugin.bluetoothkeyboard.core.AuthorizedDevicesManager;
import mzrw.k2aplugin.bluetoothkeyboard.core.HidService;
import mzrw.k2aplugin.bluetoothkeyboard.layout.KeyboardLayoutFactory;

@SuppressLint("MissingPermission")
public class KeyboardActivity extends AbstractBluetoothActivity implements HidService.StateChangeListener {
    private static final String TAG = KeyboardActivity.class.getName();
    public static final String INTENT_EXTRA_STRING_TO_TYPE = "intent_extra_string_to_type";
    public static final String BUNDLE_KEY_SELECTED_DEVICE = "bundle_key_selected_device";
    public static final String ENTER_STRING = "\n";

    public static String CURRENT_SNED_STRING = "";

    private HidService hidService;

    private SharedPreferences selectedEntriesPreferences;
    private String selectedDevice;
    private List<BluetoothDevice> devices;

    private Spinner deviceSpinner;
    private Button btnPassword;
    private Button btnEnter;
    private Button btnNoDevicesAuthorized;
    private TextView txtState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyboard);

        hidService = new HidService(getApplicationContext(), bluetoothAdapter);
        selectedEntriesPreferences = getPreferences(MODE_PRIVATE);

        deviceSpinner = findViewById(R.id.deviceSpinner);
        btnPassword = findViewById(R.id.btnPassword);
        btnEnter = findViewById(R.id.btnEnter);
        btnNoDevicesAuthorized = findViewById(R.id.btnNoDevicesAuthorized);
        txtState = findViewById(R.id.txtState);

        registerListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateSelectionFromPreferences();
        checkBluetoothEnabled();
    }

    @Override
    protected void onPause() {
        super.onPause();

        selectedEntriesPreferences.edit()
                .putString(BUNDLE_KEY_SELECTED_DEVICE, selectedDevice)
                .apply();
    }

    private void registerListeners() {
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDevice = devices.get(position).getAddress();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        btnPassword.setOnClickListener(this::connectToDevice);
        btnEnter.setOnClickListener(this::connectToDevice);
        btnNoDevicesAuthorized.setOnClickListener(v -> startActivity(new Intent(KeyboardActivity.this, AuthorizedDevicesActivity.class)));
        hidService.setOnStateChangeListener(this);
    }

    @Override
    protected void onBluetoothEnabled() {
        devices = new AuthorizedDevicesManager(this).filterAuthorizedDevices(bluetoothAdapter.getBondedDevices());
        btnNoDevicesAuthorized.setVisibility(devices.size() > 0 ? View.GONE : View.VISIBLE);
        deviceSpinner.setVisibility(devices.size() > 0 ? View.VISIBLE : View.GONE);

        final List<String> names = devices.stream().map(BluetoothDevice::getName).collect(Collectors.toList());
        final SpinnerAdapter adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        deviceSpinner.setAdapter(adapter);

        updateSelectionFromPreferences();
    }

    private void updateSelectionFromPreferences() {
        if (devices != null) {
            selectedDevice = selectedEntriesPreferences.getString(BUNDLE_KEY_SELECTED_DEVICE, null);
            IntStream.range(0, devices.size())
                    .filter(index -> devices.get(index).getAddress().equals(selectedDevice))
                    .findAny()
                    .ifPresent(index -> deviceSpinner.setSelection(index));
        }
    }

    public void connectToDevice(View v) {
        setSendString(v.getId());
        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(selectedDevice);
        Log.i(TAG, "connecting to " + device.getName());
        try {
            hidService.connect(device, KeyboardLayoutFactory.getLayout("UK QWERTY"));
        } catch (Exception e) {
            Log.e(TAG, "failed to connect to " + device.getName());
        }
    }

    private void setSendString(int btnId) {
        CURRENT_SNED_STRING = btnId == R.id.btnEnter ? ENTER_STRING : getIntent().getStringExtra(INTENT_EXTRA_STRING_TO_TYPE);
    }

    @Override
    public void onStateChanged(int state) {
        switch (state) {
            case HidService.STATE_DISCONNECTED:
//                finishAndRemoveTask();
                // do nothing
                break;
            case HidService.STATE_CONNECTING:
                txtState.setText("Connecting");
                break;
            case HidService.STATE_CONNECTED:
                txtState.setText("Connected");
                hidService.sendText(CURRENT_SNED_STRING);
                break;
            case HidService.STATE_SENDING:
                txtState.setText("Sending");
                break;
            case HidService.STATE_SENT:
                txtState.setText("Sent");
                hidService.disconnect();
                break;
            case HidService.STATE_DISCONNECTING:
                txtState.setText("Disconnecting");
                break;
        }
    }
}
