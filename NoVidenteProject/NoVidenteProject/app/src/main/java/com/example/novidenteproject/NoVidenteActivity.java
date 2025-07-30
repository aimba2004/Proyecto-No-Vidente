package com.example.novidenteproject;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class NoVidenteActivity extends AppCompatActivity {

    private TextView tvNombre, tvApellido, tvCedula, tvDireccion, tvContacto, tvBluetoothStatus;
    private ImageView bluetoothIcon, ivPanic;
    private Spinner spinnerUsuarios;
    private Button btnCargarUsuario;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private BluetoothAdapter bluetoothAdapter;
    private String contactoEmergencia;
    private Button btnConectarBluetooth;

    // Variables para manejo de usuarios
    private List<String> listaUsuarios;
    private Map<String, String> mapaUsuarios; // Nombre completo -> UID
    private ArrayAdapter<String> spinnerAdapter;
    private String usuarioSeleccionadoUID = null;

    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private Vibrator vibrator;

    // SharedPreferences
    private static final String PREFS_NAME = "NoVidentePrefs";
    private static final String PREF_SELECTED_USER = "selected_user_uid";
    private static final String PREF_BLUETOOTH_CONNECTED = "bluetooth_connected";
    private SharedPreferences sharedPreferences;

    private static final int SMS_PERMISSION_CODE = 101;
    private static final int LOCATION_PERMISSION_CODE = 102;
    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final String ESP_PANICO = "ESP_Panico"; // Nombre Bluetooth del ESP32
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private LocationManager locationManager;
    private DatabaseReference ubicacionRef;

    // Variables para actualización automática de ubicación
    private Handler locationHandler;
    private Runnable locationRunnable;
    private boolean isLocationUpdateActive = false;
    private Location ultimaUbicacion = null;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_no_vidente);

        // Configurar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🏠 Panel Principal");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().show();
        }

        // Configurar window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Enlazar vistas
        tvNombre = findViewById(R.id.tvNombre);
        tvApellido = findViewById(R.id.tvApellido);
        tvCedula = findViewById(R.id.tvCedula);
        tvDireccion = findViewById(R.id.tvDireccion);
        tvContacto = findViewById(R.id.tvContacto);
        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus);
        bluetoothIcon = findViewById(R.id.bluetoothIcon);
        ivPanic = findViewById(R.id.ivPanic);
        btnConectarBluetooth = findViewById(R.id.btnConectarBluetooth);
        spinnerUsuarios = findViewById(R.id.spinnerUsuarios);
        btnCargarUsuario = findViewById(R.id.btnCargarUsuario);

        // Inicializar listas y adaptadores
        listaUsuarios = new ArrayList<>();
        mapaUsuarios = new HashMap<>();
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, listaUsuarios);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUsuarios.setAdapter(spinnerAdapter);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Firebase
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        ubicacionRef = FirebaseDatabase.getInstance().getReference("ubicacion_novidente");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Inicializar Handler para ubicación
        locationHandler = new Handler(Looper.getMainLooper());

        // Configurar listeners
        btnConectarBluetooth.setOnClickListener(v -> verificarPermisosBluetooth());
        btnCargarUsuario.setOnClickListener(v -> cargarDatosUsuarioSeleccionado());

        // Listener para el spinner
        spinnerUsuarios.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String usuarioSeleccionado = listaUsuarios.get(position);
                    usuarioSeleccionadoUID = mapaUsuarios.get(usuarioSeleccionado);
                    btnCargarUsuario.setEnabled(true);

                    // Guardar la selección
                    sharedPreferences.edit()
                            .putString(PREF_SELECTED_USER, usuarioSeleccionadoUID)
                            .apply();

                    iniciarActualizacionUbicacion();
                } else {
                    usuarioSeleccionadoUID = null;
                    btnCargarUsuario.setEnabled(false);

                    // Limpiar la selección guardada
                    sharedPreferences.edit()
                            .remove(PREF_SELECTED_USER)
                            .apply();

                    detenerActualizacionUbicacion();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                usuarioSeleccionadoUID = null;
                btnCargarUsuario.setEnabled(false);

                sharedPreferences.edit()
                        .remove(PREF_SELECTED_USER)
                        .apply();

                detenerActualizacionUbicacion();
            }
        });

        // Pedir permisos
        requestSmsPermission();
        requestLocationPermission();

        // Cargar usuarios desde Firebase
        cargarUsuariosDesdeFirebase();

        // Acción del botón de pánico en la app
        ivPanic.setOnClickListener(v -> {
            if (contactoEmergencia != null && !contactoEmergencia.isEmpty()) {
                String ubicacionTexto = obtenerUbicacionTexto();
                String mensajePanico = "🆘 ¡EMERGENCIA! Botón de pánico activado.\n" +
                        "📍 Ubicación: " + ubicacionTexto + "\n" +
                        "⏰ Hora: " + timeFormat.format(new Date()) + "\n" +
                        "Se requiere asistencia inmediata.";

                enviarSMS(contactoEmergencia, mensajePanico);
                Toast.makeText(this, "🆘 Mensaje de emergencia enviado con ubicación", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Primero seleccione un usuario para obtener el contacto de emergencia", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cargarSeleccionGuardada() {
        // Cargar usuario seleccionado
        usuarioSeleccionadoUID = sharedPreferences.getString(PREF_SELECTED_USER, null);

        if (usuarioSeleccionadoUID != null) {
            // Buscar el usuario en la lista y seleccionarlo en el spinner
            for (Map.Entry<String, String> entry : mapaUsuarios.entrySet()) {
                if (entry.getValue().equals(usuarioSeleccionadoUID)) {
                    int position = listaUsuarios.indexOf(entry.getKey());
                    if (position >= 0) {
                        spinnerUsuarios.setSelection(position);
                        btnCargarUsuario.setEnabled(true);
                        cargarDatosUsuarioSeleccionado();
                        iniciarActualizacionUbicacion();
                    }
                    break;
                }
            }
        }

        // Cargar estado de Bluetooth
        boolean bluetoothConnected = sharedPreferences.getBoolean(PREF_BLUETOOTH_CONNECTED, false);
        if (bluetoothConnected) {
            tvBluetoothStatus.setText("📱 Bluetooth: Conectado");
            bluetoothIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        }
    }

    private void iniciarActualizacionUbicacion() {
        if (usuarioSeleccionadoUID == null) {
            return;
        }

        detenerActualizacionUbicacion();

        isLocationUpdateActive = true;

        locationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isLocationUpdateActive && usuarioSeleccionadoUID != null) {
                    actualizarUbicacionActual();
                    locationHandler.postDelayed(this, 5000);
                }
            }
        };

        locationHandler.post(locationRunnable);
    }

    private void detenerActualizacionUbicacion() {
        isLocationUpdateActive = false;
        if (locationHandler != null && locationRunnable != null) {
            locationHandler.removeCallbacks(locationRunnable);
        }
    }

    private void actualizarUbicacionActual() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {

                Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (lastKnownLocation != null) {
                    ultimaUbicacion = lastKnownLocation;
                    double lat = lastKnownLocation.getLatitude();
                    double lng = lastKnownLocation.getLongitude();

                    ubicacionRef.child(usuarioSeleccionadoUID).setValue(new Ubicacion(lat, lng))
                            .addOnSuccessListener(aVoid -> {
                                if (getSupportActionBar() != null) {
                                    getSupportActionBar().setSubtitle("📍 Ubicación actualizada: " + timeFormat.format(new Date()));
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "❌ Error al actualizar ubicación en Firebase", Toast.LENGTH_SHORT).show();
                            });
                } else {
                    solicitarNuevaUbicacion();
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "❌ Sin permisos de ubicación", Toast.LENGTH_SHORT).show();
            detenerActualizacionUbicacion();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void solicitarNuevaUbicacion() {
        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    ultimaUbicacion = location;
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    if (usuarioSeleccionadoUID != null) {
                        ubicacionRef.child(usuarioSeleccionadoUID).setValue(new Ubicacion(lat, lng));
                    }
                }

                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, null);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private String obtenerUbicacionTexto() {
        if (ultimaUbicacion != null) {
            return String.format(Locale.getDefault(), "%.6f, %.6f",
                    ultimaUbicacion.getLatitude(), ultimaUbicacion.getLongitude());
        } else {
            return "Ubicación no disponible";
        }
    }

    private void cargarUsuariosDesdeFirebase() {
        mDatabase.child("usuarios").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaUsuarios.clear();
                mapaUsuarios.clear();

                listaUsuarios.add("Seleccione un usuario");

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String uid = userSnapshot.getKey();
                    String nombre = userSnapshot.child("nombre").getValue(String.class);
                    String apellido = userSnapshot.child("apellido").getValue(String.class);
                    String tipo = userSnapshot.child("tipo").getValue(String.class);

                    if ("No vidente".equals(tipo) && nombre != null && apellido != null) {
                        String nombreCompleto = nombre + " " + apellido;
                        listaUsuarios.add(nombreCompleto);
                        mapaUsuarios.put(nombreCompleto, uid);
                    }
                }

                spinnerAdapter.notifyDataSetChanged();
                cargarSeleccionGuardada();

                if (listaUsuarios.size() <= 1) {
                    Toast.makeText(NoVidenteActivity.this,
                            "⚠️ No se encontraron usuarios de tipo 'No vidente'",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NoVidenteActivity.this,
                        "❌ Error al cargar usuarios: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarDatosUsuarioSeleccionado() {
        if (usuarioSeleccionadoUID == null) {
            Toast.makeText(this, "⚠️ Seleccione un usuario válido", Toast.LENGTH_SHORT).show();
            return;
        }

        mDatabase.child("usuarios").child(usuarioSeleccionadoUID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String nombre = snapshot.child("nombre").getValue(String.class);
                    String apellido = snapshot.child("apellido").getValue(String.class);
                    String cedula = snapshot.child("cedula").getValue(String.class);
                    String direccion = snapshot.child("direccion").getValue(String.class);
                    String telefono = snapshot.child("telefonoEmergencia").getValue(String.class);

                    tvNombre.setText("👤 Nombre: " + (nombre != null ? nombre : "No disponible"));
                    tvApellido.setText("👥 Apellido: " + (apellido != null ? apellido : "No disponible"));
                    tvCedula.setText("🆔 Cédula: " + (cedula != null ? cedula : "No disponible"));
                    tvDireccion.setText("🏠 Dirección: " + (direccion != null ? direccion : "No disponible"));
                    tvContacto.setText("🚨 Contacto de Emergencia: " + (telefono != null ? telefono : "No disponible"));

                    contactoEmergencia = telefono;
                } else {
                    Toast.makeText(NoVidenteActivity.this,
                            "❌ No se encontraron datos para el usuario seleccionado",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NoVidenteActivity.this,
                        "❌ Error al cargar datos: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_register) {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_emergency_contact) {
            Intent intent = new Intent(this, ContactoEmergenciaActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_settings) {
            Toast.makeText(this, "⚙️ Configuración - Próximamente", Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void requestBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));

                try {
                    startActivity(intent);
                    Toast.makeText(this, "Por favor, permite que la app ignore las optimizaciones de batería", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Para mejor funcionamiento, desactiva la optimización de batería para esta app", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 1, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        ultimaUbicacion = location;
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                });

                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        if (ultimaUbicacion == null || location.getAccuracy() < ultimaUbicacion.getAccuracy()) {
                            ultimaUbicacion = location;
                        }
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                });
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public static class Ubicacion {
        public double latitud, longitud;
        public long timestamp;

        public Ubicacion() {}

        public Ubicacion(double lat, double lng) {
            this.latitud = lat;
            this.longitud = lng;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
    }

    private void enviarSMS(String numero, String mensaje) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(numero, null, mensaje, null, null);
            Toast.makeText(getApplicationContext(), "📱 SMS enviado correctamente", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "❌ Error al enviar SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void verificarPermisosBluetooth() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            conectarBluetooth();
        }
    }

    private void conectarBluetooth() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth desactivado o no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothDevice device = null;
        try {
            for (BluetoothDevice bd : bluetoothAdapter.getBondedDevices()) {
                if (ESP_PANICO.equals(bd.getName())) {
                    device = bd;
                    break;
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "❌ Sin permisos de Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (device == null) {
            Toast.makeText(this, "Dispositivo ESP_Panico no encontrado", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothDevice finalDevice = device;
        new Thread(() -> {
            try {
                bluetoothSocket = finalDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();

                runOnUiThread(() -> {
                    tvBluetoothStatus.setText("📱 Bluetooth: Conectado");
                    bluetoothIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark));

                    sharedPreferences.edit()
                            .putBoolean(PREF_BLUETOOTH_CONNECTED, true)
                            .apply();
                });

                byte[] buffer = new byte[1024];
                int bytes;
                while ((bytes = inputStream.read(buffer)) > 0) {
                    String mensaje = new String(buffer, 0, bytes).trim();
                    runOnUiThread(() -> {
                        if (mensaje.contains("BOTON_PANICO")) {
                            Toast.makeText(this, "🆘 ¡Botón de pánico presionado desde ESP32!", Toast.LENGTH_SHORT).show();
                            vibrar();
                            ivPanic.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
                            if (contactoEmergencia != null && !contactoEmergencia.isEmpty()) {
                                String ubicacionTexto = obtenerUbicacionTexto();
                                String mensajePanico = "🆘 ¡EMERGENCIA ESP32! Botón de pánico activado.\n" +
                                        "📍 Ubicación: " + ubicacionTexto + "\n" +
                                        "⏰ Hora: " + timeFormat.format(new Date()) + "\n" +
                                        "Se requiere asistencia inmediata.";
                                enviarSMS(contactoEmergencia, mensajePanico);
                            } else {
                                Toast.makeText(this, "⚠️ No hay contacto de emergencia configurado", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Error de conexión Bluetooth", Toast.LENGTH_SHORT).show();
                    tvBluetoothStatus.setText("📱 Bluetooth: Error de conexión");
                    bluetoothIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));

                    sharedPreferences.edit()
                            .putBoolean(PREF_BLUETOOTH_CONNECTED, false)
                            .apply();
                });
            }
        }).start();
    }

    private void vibrar() {
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates();
                    Toast.makeText(this, "✅ Permiso de ubicación concedido", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "❌ Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
                }
                break;

            case SMS_PERMISSION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✅ Permiso de SMS concedido", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "❌ Permiso de SMS denegado - No se podrán enviar mensajes de emergencia", Toast.LENGTH_LONG).show();
                }
                break;

            case REQUEST_BLUETOOTH_PERMISSIONS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    conectarBluetooth();
                    Toast.makeText(this, "✅ Permiso de Bluetooth concedido", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "❌ Permiso de Bluetooth denegado", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerActualizacionUbicacion();

        try {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                bluetoothSocket.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarUsuariosDesdeFirebase();

        if (usuarioSeleccionadoUID != null && !isLocationUpdateActive) {
            iniciarActualizacionUbicacion();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isLocationUpdateActive && usuarioSeleccionadoUID != null) {
            startBackgroundService();
        }
    }
}