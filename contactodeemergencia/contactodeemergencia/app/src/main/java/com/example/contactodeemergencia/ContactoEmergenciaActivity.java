package com.example.contactodeemergencia;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ContactoEmergenciaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker marker;
    private DatabaseReference ubicacionRef;
    private DatabaseReference usuariosRef;

    // Componentes UI
    private TextView tvUsuarioMonitoreado;
    private TextView tvEstadoConexion;
    private TextView tvUltimaActualizacion;
    private View statusIndicator;
    private TextView btnCerrarSesion;

    // Variables para manejo del usuario relacionado
    private String usuarioNoVidenteUID = null;
    private String usuarioNoVidenteNombre = "";
    private ValueEventListener ubicacionListener;

    // SharedPreferences para datos del usuario loggeado
    private static final String USER_PREFS_NAME = "UserSession";
    private SharedPreferences userPrefs;

    // SharedPreferences para monitoreo
    private static final String MONITORING_PREFS_NAME = "UbicacionPrefs";
    private static final String PREF_MONITORING_ACTIVE = "monitoring_active";
    private static final String PREF_MONITORED_USER_UID = "monitored_user_uid";
    private static final String PREF_MONITORED_USER_NAME = "monitored_user_name";
    private SharedPreferences monitoringPrefs;

    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacto_emergencia);

        // Configurar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📍 Ubicación en Tiempo Real");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Inicializar SharedPreferences
        userPrefs = getSharedPreferences(USER_PREFS_NAME, MODE_PRIVATE);
        monitoringPrefs = getSharedPreferences(MONITORING_PREFS_NAME, MODE_PRIVATE);

        // Inicializar componentes UI
        initializeComponents();

        // Obtener referencia del fragmento de mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Referencias Firebase
        usuariosRef = FirebaseDatabase.getInstance().getReference("usuarios");
        ubicacionRef = FirebaseDatabase.getInstance().getReference("ubicacion_novidente");

        // Buscar automáticamente el usuario no vidente relacionado
        buscarUsuarioRelacionado();
    }

    private void initializeComponents() {
        tvUsuarioMonitoreado = findViewById(R.id.tvUsuarioMonitoreado);
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion);
        tvUltimaActualizacion = findViewById(R.id.tvUltimaActualizacion);
        statusIndicator = findViewById(R.id.statusIndicator);

        // Inicializar botón de cerrar sesión
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion);

        // Configurar listener del botón de cerrar sesión
        btnCerrarSesion.setOnClickListener(v -> mostrarDialogoCerrarSesion());
    }

    // Método para mostrar diálogo de confirmación antes de cerrar sesión
    private void mostrarDialogoCerrarSesion() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚪 Cerrar Sesión")
                .setMessage("¿Estás seguro que deseas cerrar sesión?\n\nSe detendrá el monitoreo en tiempo real.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Sí, cerrar sesión", (dialog, which) -> {
                    cerrarSesion();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    // Método para cerrar sesión
    private void cerrarSesion() {
        try {
            // Detener el monitoreo activo
            detenerMonitoreoUbicacion();

            // Limpiar el estado de monitoreo
            ContactoEmergenciaActivity.limpiarEstadoMonitoreo(this);

            // Cerrar sesión usando el método estático de MainActivity
            MainActivity.cerrarSesion(this);

            // Mostrar mensaje de despedida
            Toast.makeText(this, "👋 Sesión cerrada correctamente", Toast.LENGTH_SHORT).show();

            // Regresar a MainActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e("ContactoEmergencia", "Error al cerrar sesión", e);
            Toast.makeText(this, "Error al cerrar sesión: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void buscarUsuarioRelacionado() {
        // Obtener el teléfono del contacto de emergencia loggeado
        String telefonoContacto = userPrefs.getString("user_phone", null);

        if (telefonoContacto == null || telefonoContacto.isEmpty()) {
            mostrarError("❌ No se encontró número de teléfono del contacto de emergencia");
            return;
        }

        tvEstadoConexion.setText("🔍 Buscando usuario relacionado...");
        statusIndicator.setBackgroundTintList(
                ContextCompat.getColorStateList(this, android.R.color.holo_orange_light)
        );

        Log.d("ContactoEmergencia", "Buscando usuario no vidente con teléfono: " + telefonoContacto);

        // Buscar en Firebase el usuario no vidente que tenga como contacto de emergencia este teléfono
        usuariosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean usuarioEncontrado = false;

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String uid = userSnapshot.getKey();
                    String tipo = userSnapshot.child("tipo").getValue(String.class);
                    // Usar "telefonoEmergencia" en lugar de "contactoEmergencia"
                    String contactoEmergencia = userSnapshot.child("telefonoEmergencia").getValue(String.class);
                    String nombre = userSnapshot.child("nombre").getValue(String.class);
                    String apellido = userSnapshot.child("apellido").getValue(String.class);

                    // Verificar si es un usuario no vidente y si su contacto de emergencia coincide
                    if ("No vidente".equals(tipo) &&
                            contactoEmergencia != null &&
                            contactoEmergencia.equals(telefonoContacto)) {

                        usuarioNoVidenteUID = uid;
                        usuarioNoVidenteNombre = (nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "");
                        usuarioEncontrado = true;

                        // Guardar en SharedPreferences para persistencia
                        monitoringPrefs.edit()
                                .putString(PREF_MONITORED_USER_UID, usuarioNoVidenteUID)
                                .putString(PREF_MONITORED_USER_NAME, usuarioNoVidenteNombre)
                                .putBoolean(PREF_MONITORING_ACTIVE, true)
                                .putLong("search_timestamp", System.currentTimeMillis())
                                .apply();

                        // Actualizar UI
                        tvUsuarioMonitoreado.setText("👤 Monitoreando: " + usuarioNoVidenteNombre);
                        tvUsuarioMonitoreado.setVisibility(View.VISIBLE);

                        // Iniciar monitoreo automáticamente
                        iniciarMonitoreoAutomatico();

                        Log.d("ContactoEmergencia", "Usuario no vidente encontrado: " + usuarioNoVidenteNombre + " (UID: " + usuarioNoVidenteUID + ")");
                        break;
                    }
                }

                if (!usuarioEncontrado) {
                    mostrarError("⚠️ No se encontró un usuario no vidente asociado a este número: " + telefonoContacto);
                    Log.d("ContactoEmergencia", "No se encontró usuario. Teléfono buscado: " + telefonoContacto);

                    // Debug: Mostrar todos los usuarios encontrados
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        String tipo = userSnapshot.child("tipo").getValue(String.class);
                        String telefono = userSnapshot.child("telefonoEmergencia").getValue(String.class);
                        String nombre = userSnapshot.child("nombre").getValue(String.class);
                        Log.d("ContactoEmergencia", "Usuario encontrado - Tipo: " + tipo + ", Teléfono: " + telefono + ", Nombre: " + nombre);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mostrarError("❌ Error al buscar usuario relacionado: " + error.getMessage());
                Log.e("ContactoEmergencia", "Error al buscar usuario relacionado", error.toException());
            }
        });
    }

    private void iniciarMonitoreoAutomatico() {
        if (usuarioNoVidenteUID == null) {
            Log.w("ContactoEmergencia", "No hay usuario no vidente para monitorear");
            return;
        }

        // Detener listener anterior si existe
        detenerMonitoreoUbicacion();

        tvEstadoConexion.setText("🔄 Conectando con " + usuarioNoVidenteNombre + "...");
        statusIndicator.setBackgroundTintList(
                ContextCompat.getColorStateList(this, android.R.color.holo_orange_light)
        );

        // Crear listener para el usuario no vidente
        ubicacionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    Double lat = snapshot.child("latitud").getValue(Double.class);
                    Double lng = snapshot.child("longitud").getValue(Double.class);

                    if (lat == null || lng == null) {
                        Log.w("ContactoEmergencia", "Latitud o longitud nula para usuario: " + usuarioNoVidenteUID);

                        tvEstadoConexion.setText("⚠️ Esperando ubicación de " + usuarioNoVidenteNombre + "...");
                        statusIndicator.setBackgroundTintList(
                                ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_orange_light)
                        );
                        return;
                    }

                    LatLng ubicacion = new LatLng(lat, lng);
                    actualizarUbicacionEnMapa(ubicacion);

                    // Actualizar estado de conexión
                    tvEstadoConexion.setText("✅ Monitoreando: " + usuarioNoVidenteNombre);
                    tvUltimaActualizacion.setText("Actualizado: " + timeFormat.format(new Date()));
                    statusIndicator.setBackgroundTintList(
                            ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_green_light)
                    );

                    // Actualizar timestamp en SharedPreferences
                    monitoringPrefs.edit()
                            .putLong("last_location_update", System.currentTimeMillis())
                            .apply();

                    Log.d("ContactoEmergencia", "Ubicación actualizada: " + lat + ", " + lng);

                } catch (Exception e) {
                    Log.e("ContactoEmergencia", "Error al procesar ubicación", e);
                    tvEstadoConexion.setText("❌ Error al obtener ubicación");
                    statusIndicator.setBackgroundTintList(
                            ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_red_light)
                    );
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ContactoEmergencia", "Error al obtener ubicación: " + error.getMessage());
                tvEstadoConexion.setText("❌ Error de conexión");
                statusIndicator.setBackgroundTintList(
                        ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_red_light)
                );
                Toast.makeText(ContactoEmergenciaActivity.this,
                        "Error al conectar con Firebase: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        // Escuchar cambios en la ubicación del usuario no vidente
        ubicacionRef.child(usuarioNoVidenteUID).addValueEventListener(ubicacionListener);
        Log.d("ContactoEmergencia", "Iniciado monitoreo automático para: " + usuarioNoVidenteNombre);
    }

    private void mostrarError(String mensaje) {
        tvEstadoConexion.setText(mensaje);
        tvUsuarioMonitoreado.setVisibility(View.GONE);
        statusIndicator.setBackgroundTintList(
                ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        );
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    private void detenerMonitoreoUbicacion() {
        if (ubicacionListener != null && usuarioNoVidenteUID != null) {
            ubicacionRef.child(usuarioNoVidenteUID).removeEventListener(ubicacionListener);
            ubicacionListener = null;
            Log.d("ContactoEmergencia", "Monitoreo detenido");
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Configurar el mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        // Centrar en Ecuador por defecto
        LatLng ecuador = new LatLng(-0.1807, -78.4678);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ecuador, 6));

        Log.d("ContactoEmergencia", "Mapa listo");
    }

    private void actualizarUbicacionEnMapa(LatLng ubicacion) {
        if (mMap == null) {
            Log.w("ContactoEmergencia", "Mapa no está listo");
            return;
        }

        if (marker == null) {
            // Crear nuevo marcador
            marker = mMap.addMarker(new MarkerOptions()
                    .position(ubicacion)
                    .title("📍 " + usuarioNoVidenteNombre)
                    .snippet("Ubicación en tiempo real"));

            // Centrar el mapa en la ubicación
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ubicacion, 17));
        } else {
            // Actualizar posición del marcador existente
            marker.setPosition(ubicacion);
            marker.setTitle("📍 " + usuarioNoVidenteNombre);

            // Mover la cámara suavemente a la nueva ubicación
            mMap.animateCamera(CameraUpdateFactory.newLatLng(ubicacion));
        }
    }

    private void limpiarMapa() {
        if (mMap != null && marker != null) {
            marker.remove();
            marker = null;

            // Volver a la vista general de Ecuador
            LatLng ecuador = new LatLng(-0.1807, -78.4678);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ecuador, 6));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("ContactoEmergencia", "Activity destruida - manteniendo estado en SharedPreferences");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("ContactoEmergencia", "Activity pausada - monitoreo manteniéndose activo");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Verificar si hay un monitoreo guardado y reanudar si es necesario
        boolean monitoreoActivo = monitoringPrefs.getBoolean(PREF_MONITORING_ACTIVE, false);
        String uidGuardado = monitoringPrefs.getString(PREF_MONITORED_USER_UID, null);
        String nombreGuardado = monitoringPrefs.getString(PREF_MONITORED_USER_NAME, null);

        if (monitoreoActivo && uidGuardado != null && nombreGuardado != null) {
            usuarioNoVidenteUID = uidGuardado;
            usuarioNoVidenteNombre = nombreGuardado;

            tvUsuarioMonitoreado.setText("👤 Monitoreando: " + usuarioNoVidenteNombre);
            tvUsuarioMonitoreado.setVisibility(View.VISIBLE);

            iniciarMonitoreoAutomatico();
            Toast.makeText(this, "📍 Reanudando monitoreo automático", Toast.LENGTH_SHORT).show();
        }

        Log.d("ContactoEmergencia", "Activity reanudada");
    }

    @Override
    public void onBackPressed() {
        // Personalizar comportamiento del botón atrás
        if (monitoringPrefs.getBoolean(PREF_MONITORING_ACTIVE, false)) {
            Toast.makeText(this, "📍 Monitoreo activo en segundo plano", Toast.LENGTH_SHORT).show();
        }
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static boolean hayMonitoreoActivo(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MONITORING_PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_MONITORING_ACTIVE, false) &&
                prefs.getString(PREF_MONITORED_USER_UID, null) != null;
    }

    public static void limpiarEstadoMonitoreo(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MONITORING_PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .remove(PREF_MONITORED_USER_UID)
                .remove(PREF_MONITORED_USER_NAME)
                .putBoolean(PREF_MONITORING_ACTIVE, false)
                .apply();
    }
}