package com.example.senzordeaer;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressLint("MissingPermission")
@SuppressWarnings("deprecation") // Older Android versions require the legacy GATT overloads.
public class BleProvisioningManager {
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    private BluetoothLeScanner scanner;
    private final ProvisioningCallback callback;
    private BluetoothGatt connectedGatt;
    private BluetoothGattCharacteristic provisioningCharacteristic;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private Runnable stopScanRunnable;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result != null && result.getDevice() != null && isCompatibleEspDevice(result)) {
                handler.post(() -> callback.onDeviceFound(result.getDevice()));
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            handler.post(() -> callback.onProvisioningResponse("Nu s-a putut porni scanarea Bluetooth (cod " + errorCode + ")."));
        }
    };

    public BleProvisioningManager(Context context, ProvisioningCallback callback) {
        this.callback = callback;
    }

    public void startScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        scanner = adapter != null && adapter.isEnabled() ? adapter.getBluetoothLeScanner() : null;
        if (scanner == null) {
            callback.onProvisioningResponse("Bluetooth trebuie activat pentru a căuta dispozitivul.");
            return;
        }
        stopScan();
        scanner.startScan(scanCallback);
        stopScanRunnable = this::stopScan;
        handler.postDelayed(stopScanRunnable, 10000);
    }

    private boolean isCompatibleEspDevice(ScanResult result) {
        String name = result.getDevice().getName();
        if (name != null) {
            String normalizedName = name.toLowerCase();
            if (normalizedName.contains("esp") || normalizedName.contains("nimble")) return true;
        }
        ScanRecord record = result.getScanRecord();
        if (record == null || record.getServiceUuids() == null) return false;
        for (ParcelUuid serviceUuid : record.getServiceUuids()) {
            if (SERVICE_UUID.equals(serviceUuid.getUuid())) return true;
        }
        return false;
    }

    public void stopScan() {
        if (stopScanRunnable != null) {
            handler.removeCallbacks(stopScanRunnable);
            stopScanRunnable = null;
        }
        if (scanner != null) {
            scanner.stopScan(scanCallback);
        }
    }

    public void connect(Context context, BluetoothDevice device) {
        cleanup();
        
        timeoutRunnable = () -> {
            handler.post(() -> callback.onProvisioningResponse("Timeout: Senzorul nu răspunde."));
            disconnect();
        };
        handler.postDelayed(timeoutRunnable, 15000);

        device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedGatt = gatt;
                    handler.post(() -> callback.onConnected());
                    handler.postDelayed(() -> {
                        if (connectedGatt != null) connectedGatt.discoverServices();
                    }, 800);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    cleanup();
                    handler.post(() -> callback.onDisconnected());
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                handler.removeCallbacks(timeoutRunnable);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handler.post(() -> callback.onProvisioningResponse("Eroare GATT discovery"));
                    return;
                }
                
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service == null) {
                    handler.post(() -> callback.onProvisioningResponse("Serviciul de configurare nu a fost găsit."));
                    disconnect();
                    return;
                }
                
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic == null) {
                    handler.post(() -> callback.onProvisioningResponse("Caracteristica de configurare nu a fost găsită."));
                    disconnect();
                    return;
                }
                provisioningCharacteristic = characteristic;
                
                gatt.setCharacteristicNotification(characteristic, true);
                
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    } else {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                } else {
                    handler.post(() -> callback.onProvisioningResponse("Eroare inițializare notificări."));
                }
            }
            
            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handler.post(() -> callback.onProvisioningSuccess());
                } else {
                    handler.post(() -> callback.onProvisioningResponse("Eroare setup comunicație."));
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                String response = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                if (response != null) {
                    handler.post(() -> callback.onProvisioningResponse(response));
                }
            }
            
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handler.post(() -> callback.onProvisioningResponse("Eroare la trimiterea comenzii."));
                }
            }
        });
    }

    public boolean sendWifiCredentials(String ssid, String password) {
        return sendCommand("WIFI|" + ssid + "|" + password);
    }

    public boolean requestWifiNetworks() {
        return sendCommand("SCAN_WIFI");
    }

    public boolean requestWifiStatus() {
        return sendCommand("STATUS");
    }

    public boolean sendPairingCode(String code) {
        return sendCommand("SHOW_PIN|" + code);
    }

    public boolean confirmPairing(String code) {
        return sendCommand("PAIR_OK");
    }

    public boolean sendCommand(String command) {
        if (connectedGatt == null || provisioningCharacteristic == null) {
            callback.onProvisioningResponse("Canalul Bluetooth nu este pregătit pentru trimitere.");
            return false;
        }
        int properties = provisioningCharacteristic.getProperties();
        int writeType = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
            callback.onProvisioningResponse("Caracteristica ESP nu permite comenzi de configurare.");
            return false;
        }

        byte[] value = command.getBytes(StandardCharsets.UTF_8);
        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            started = connectedGatt.writeCharacteristic(provisioningCharacteristic, value, writeType) == BluetoothStatusCodes.SUCCESS;
        } else {
            provisioningCharacteristic.setWriteType(writeType);
            provisioningCharacteristic.setValue(value);
            started = connectedGatt.writeCharacteristic(provisioningCharacteristic);
        }
        if (!started) callback.onProvisioningResponse("Comanda Bluetooth nu a putut fi trimisă către ESP.");
        return started;
    }

    public void disconnect() {
        stopScan();
        if (connectedGatt != null) {
            connectedGatt.disconnect();
        }
        cleanup();
    }

    private void cleanup() {
        handler.removeCallbacks(timeoutRunnable);
        if (connectedGatt != null) {
            connectedGatt.close();
            connectedGatt = null;
        }
        provisioningCharacteristic = null;
    }
}
