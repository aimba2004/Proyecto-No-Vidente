package com.example.novidenteproject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "NoVidentePrefs";
    private static final String PREF_SELECTED_USER = "selected_user_uid";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Recibido: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {

            // Verificar si hay un usuario seleccionado
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String usuarioSeleccionado = prefs.getString(PREF_SELECTED_USER, null);

            if (usuarioSeleccionado != null) {
                Log.d(TAG, "Reiniciando servicio de ubicación para usuario: " + usuarioSeleccionado);

                Intent serviceIntent = new Intent(context, BackgroundService.class);

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.d(TAG, "Servicio iniciado correctamente");
                } catch (Exception e) {
                    Log.e(TAG, "Error al iniciar servicio", e);
                }
            } else {
                Log.d(TAG, "No hay usuario seleccionado, no se inicia el servicio");
            }
        }
    }
}