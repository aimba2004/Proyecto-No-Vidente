package com.example.novidenteproject;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackgroundService extends Service {

    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "location_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "NoVidentePrefs";
    private static final String PREF_SELECTED_USER = "selected_user_uid";

    private LocationManager locationManager;
    private DatabaseReference ubicacionRef;
    private Handler locationHandler;
    private Runnable locationRunnable;
    private String usuarioSeleccionadoUID;
    private Location ultimaUbicacion;
    private SimpleDateFormat timeFormat;
    private SharedPreferences sharedPreferences;
    private NotificationManager notificationManager;

    // LocationListener para GPS
    private LocationListener gpsLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "Nueva ubicación GPS: " + location.getLatitude() + ", " + location.getLongitude());
            actualizarUbicacion(location);
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {
            Log.d(TAG, "GPS habilitado");
        }
        @Override public void onProviderDisabled(String provider) {
            Log.d(TAG, "GPS deshabilitado");
        }
    };

    // LocationListener para Network
    private LocationListener networkLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "Nueva ubicación Network: " + location.getLatitude() + ", " + location.getLongitude());
            // Solo usar ubicación de red si no tenemos GPS o es más precisa
            if (ultimaUbicacion == null ||
                    (!ultimaUbicacion.getProvider().equals(LocationManager.GPS_PROVIDER) &&
                            location.getAccuracy() < ultimaUbicacion.getAccuracy())) {
                actualizarUbicacion(location);
            }
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio creado");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        ubicacionRef = FirebaseDatabase.getInstance().getReference("ubicacion_novidente");
        locationHandler = new Handler(Looper.getMainLooper());
        timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Servicio iniciado");

        // Obtener el usuario seleccionado
        usuarioSeleccionadoUID = sharedPreferences.getString(PREF_SELECTED_USER, null);

        if (usuarioSeleccionadoUID == null) {
            Log.w(TAG, "No hay usuario seleccionado, deteniendo servicio");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Crear y mostrar notificación de primer plano
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        // Iniciar actualizaciones de ubicación
        startLocationUpdates();

        // Iniciar el handler para actualizaciones periódicas
        startPeriodicUpdates();

        // START_STICKY asegura que el servicio se reinicie si es terminado por el sistema
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Servicio de Ubicación",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantiene actualizada la ubicación en segundo plano");
            channel.setShowBadge(false);
            channel.setSound(null, null);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, NoVidenteActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        String contentText = ultimaUbicacion != null ?
                "Última actualización: " + timeFormat.format(new Date()) :
                "Obteniendo ubicación...";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📍 Rastreando Ubicación")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Sin permisos de ubicación");
            stopSelf();
            return;
        }

        try {
            // Solicitar actualizaciones de GPS (más preciso pero consume más batería)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        10000, // 10 segundos
                        5,     // 5 metros
                        gpsLocationListener
                );
                Log.d(TAG, "Actualizaciones GPS iniciadas");
            }

            // Solicitar actualizaciones de red (menos preciso pero más eficiente)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        15000, // 15 segundos
                        10,    // 10 metros
                        networkLocationListener
                );
                Log.d(TAG, "Actualizaciones de red iniciadas");
            }

            // Obtener última ubicación conocida al iniciar
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown == null) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastKnown != null) {
                actualizarUbicacion(lastKnown);
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos al iniciar actualizaciones de ubicación", e);
            stopSelf();
        }
    }

    private void startPeriodicUpdates() {
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                if (usuarioSeleccionadoUID != null) {
                    // Actualizar notificación con la hora actual
                    updateNotification();

                    // Forzar actualización de ubicación si no hemos recibido una reciente
                    long tiempoSinActualizacion = ultimaUbicacion != null ?
                            System.currentTimeMillis() - ultimaUbicacion.getTime() : Long.MAX_VALUE;

                    if (tiempoSinActualizacion > 30000) { // 30 segundos sin actualización
                        solicitarUbicacionForzada();
                    }

                    // Programar siguiente ejecución
                    locationHandler.postDelayed(this, 20000); // Cada 20 segundos
                } else {
                    Log.w(TAG, "Usuario no seleccionado, deteniendo actualizaciones");
                    stopSelf();
                }
            }
        };

        locationHandler.post(locationRunnable);
    }

    @SuppressWarnings("MissingPermission")
    private void solicitarUbicacionForzada() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                // Solicitar una ubicación única
                locationManager.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER,
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(Location location) {
                                Log.d(TAG, "Ubicación forzada obtenida: " + location.getLatitude() + ", " + location.getLongitude());
                                actualizarUbicacion(location);
                            }
                            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                            @Override public void onProviderEnabled(String provider) {}
                            @Override public void onProviderDisabled(String provider) {}
                        },
                        null
                );
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos en actualización forzada", e);
        }
    }

    private void actualizarUbicacion(Location location) {
        ultimaUbicacion = location;

        if (usuarioSeleccionadoUID != null) {
            NoVidenteActivity.Ubicacion ubicacion = new NoVidenteActivity.Ubicacion(
                    location.getLatitude(),
                    location.getLongitude()
            );

            ubicacionRef.child(usuarioSeleccionadoUID).setValue(ubicacion)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Ubicación actualizada en Firebase: " +
                                location.getLatitude() + ", " + location.getLongitude());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al actualizar ubicación en Firebase", e);
                    });
        }
    }

    private void updateNotification() {
        Notification notification = createNotification();
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Servicio destruido");

        // Detener actualizaciones de ubicación
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(gpsLocationListener);
                locationManager.removeUpdates(networkLocationListener);
            } catch (SecurityException e) {
                Log.e(TAG, "Error al remover actualizaciones de ubicación", e);
            }
        }

        // Detener handler
        if (locationHandler != null && locationRunnable != null) {
            locationHandler.removeCallbacks(locationRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // No necesitamos binding
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Reiniciar el servicio si la tarea es removida
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        startService(restartServiceIntent);
        super.onTaskRemoved(rootIntent);
    }
}