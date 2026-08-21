package com.example.senzordeaer;

import android.bluetooth.BluetoothDevice;

public interface ProvisioningCallback {
    void onDeviceFound(BluetoothDevice device);
    void onConnected();
    void onProvisioningSuccess();
    void onProvisioningResponse(String message);
    void onDisconnected();
}