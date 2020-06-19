package mzrw.k2aplugin.bluetoothkeyboard.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import mzrw.k2aplugin.bluetoothkeyboard.layout.Layout;

public class HidService {
    private static final String TAG = HidService.class.getName();
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_SENDING = 3;
    public static final int STATE_SENT = 4;
    public static final int STATE_DISCONNECTING = 5;
    private final BluetoothHidDeviceAppSdpSettings bluetoothHidDeviceAppSdpSettings = new BluetoothHidDeviceAppSdpSettings(
            "K2A Keyboard",
            "K2A Plugin Keyboard over Bluetooth",
            "K2A",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            UsbReports.USB_KEYBOARD_REPORT);
    private final BluetoothHidDeviceAppQosSettings bluetoothHidDeviceAppQosSettings = new BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
            800,
            9,
            0,
            11250,
            11250
    );

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice bluetoothHidDevice;

    public HidService(Context context, BluetoothAdapter bluetoothAdapter) {
        this.context = context;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    private BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            bluetoothHidDevice = (BluetoothHidDevice) proxy;
            bluetoothHidDevice.registerApp(bluetoothHidDeviceAppSdpSettings, null, bluetoothHidDeviceAppQosSettings, executor, hidCallback);
        }

        @Override
        public void onServiceDisconnected(int profile) {
            bluetoothHidDevice = null;
            updateState(STATE_DISCONNECTED);
        }
    };

    private BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
            if (registered) {
                bluetoothHidDevice.connect(bluetoothDevice);
            }
            super.onAppStatusChanged(pluggedDevice, registered);
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            switch (state) {
                case BluetoothHidDevice.STATE_DISCONNECTED:
                    updateState(STATE_DISCONNECTED);
                    bluetoothHidDevice.unregisterApp();
                    break;
                case BluetoothHidDevice.STATE_CONNECTING:
                    updateState(STATE_CONNECTING);
                    break;
                case BluetoothHidDevice.STATE_CONNECTED:
                    updateState(STATE_CONNECTED);
                    break;
                case BluetoothHidDevice.STATE_DISCONNECTING:
                    updateState(STATE_DISCONNECTING);
                    break;
            }
            super.onConnectionStateChanged(device, state);
        }

    };

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (onStateChangeListener != null)
                onStateChangeListener.onStateChanged(msg.what);
        }


    };

    public interface StateChangeListener {
        void onStateChanged(int state);
    }

    private StateChangeListener onStateChangeListener;
    private int state = STATE_DISCONNECTED;
    private BluetoothDevice bluetoothDevice;
    private Layout layout;

    public void connect(BluetoothDevice device, Layout layout) {
        if (state != STATE_DISCONNECTED)
            throw new IllegalStateException();

        executor.execute(() -> {
            if (state != STATE_DISCONNECTED)
                throw new IllegalStateException();

            this.bluetoothDevice = device;
            this.layout = layout;

            bluetoothAdapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE);
        });
    }

    public void sendText(String text) {
        if (state != STATE_CONNECTED && state != STATE_SENDING)
            throw new IllegalStateException();

        executor.execute(() -> {
            Log.i(TAG, "sending string " + text);
            updateState(STATE_SENDING);
            for (byte[] report : UsbReports.stringToKeystrokeReports(layout, text)) {
                if (!bluetoothHidDevice.sendReport(bluetoothDevice, 0x1, report)) {
                    Log.w(TAG, "Report was not sent");

                }
            }
            Log.i(TAG, "reports are completely sent");

            updateState(STATE_SENT);
        });
    }

    public void disconnect() {
        if (state == STATE_DISCONNECTED || state == STATE_DISCONNECTING)
            throw new IllegalStateException();

        executor.execute(() -> {
            if (bluetoothHidDevice != null)
                bluetoothHidDevice.unregisterApp();
            updateState(STATE_DISCONNECTED);
        });
    }

    public void setOnStateChangeListener(StateChangeListener onStateChangeListener) {
        this.onStateChangeListener = onStateChangeListener;
    }

    private void updateState(int state) {
        Log.i(TAG, "update state to "+state);
        this.state = state;

        Message.obtain(handler, state)
                .sendToTarget();
    }
}
