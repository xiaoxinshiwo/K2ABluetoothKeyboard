package mzrw.k2aplugin.bluetoothkeyboard.core;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthorizedDevicesManager {
    private static final String AUTHORIZED_DEVICES_PREFERENCES_NAME = "authorized_devices";
    private static final String AUTHORIZED_DEVICES = "authorized_devices";

    private final SharedPreferences preferences;
    private final Set<String> authorizedDevices = new HashSet<>();

    public AuthorizedDevicesManager(Context context) {
        preferences = context.getSharedPreferences(AUTHORIZED_DEVICES_PREFERENCES_NAME, Context.MODE_PRIVATE);
        authorizedDevices.addAll(preferences.getStringSet(AUTHORIZED_DEVICES, new HashSet<>()));
    }

    public boolean isDeviceAuthorized(BluetoothDevice device) {
        if(device == null)
            return false;
        if(device.getBondState() != BluetoothDevice.BOND_BONDED)
            return false;
        final String address = device.getAddress();
        return authorizedDevices.contains(address);
    }

    public void setAuthorizationState(BluetoothDevice device, boolean authorized) {
        if(authorized)
            authorizedDevices.add(device.getAddress());
        else
            authorizedDevices.remove(device.getAddress());
    }

    public List<BluetoothDevice> filterAuthorizedDevices(Set<BluetoothDevice> bluetoothDevices) {
        return bluetoothDevices.stream()
                .filter(this::isDeviceAuthorized)
                .collect(Collectors.toList());
    }

    public void saveAuthorizedDevices() {
        preferences.edit().putStringSet(AUTHORIZED_DEVICES_PREFERENCES_NAME, authorizedDevices).apply();
    }
}
