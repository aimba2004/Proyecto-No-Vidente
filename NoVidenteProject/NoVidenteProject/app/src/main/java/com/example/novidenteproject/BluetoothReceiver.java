package com.example.novidenteproject;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class BluetoothReceiver extends BroadcastReceiver {

    private static final String TAG = "BluetoothReceiver";

    public interface BluetoothConnectionListener {
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected(BluetoothDevice device);
    }

    private static BluetoothConnectionListener connectionListener;

    public static void setBluetoothConnectionListener(BluetoothConnectionListener listener) {
        connectionListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (device == null || action == null) return;

        // 🔒 Verifica el permiso antes de usar getName()
        String deviceName = "Desconocido";
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            deviceName = device.getName() != null ? device.getName() : "Desconocido";
        }

        switch (action) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
                Log.d(TAG, "Bluetooth conectado: " + deviceName);
                if (connectionListener != null) {
                    connectionListener.onDeviceConnected(device);
                }
                break;

            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                Log.d(TAG, "Bluetooth desconectado: " + deviceName);
                if (connectionListener != null) {
                    connectionListener.onDeviceDisconnected(device);
                }
                break;
        }
    }
}
