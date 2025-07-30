package com.example.novidenteproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ContactoEmergenciaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker marker;
    private DatabaseReference ubicacionRef;
    private DatabaseReference usuariosRef;

    // Componentes UI
    private Spinner spinnerUsuariosUbicacion;
    private TextView tvEstadoConexion;
    private TextView tvUltimaActualizacion;
    private View statusIndicator;

    // Variables para manejo de usuarios
    private List<String> listaUsuarios;
    private Map<String, String> mapaUsuarios; // Nombre completo -> UID
    private ArrayAdapter<String> spinnerAdapter;
    private String usuarioSeleccionadoUID = null;
    private ValueEventListener ubicacionListener;

    // SharedPreferences - MEJORADO
    private static final String PREFS_NAME = "UbicacionPrefs";
    private static final String PREF_SELECTED_USER = "selected_user_uid";
    private static final String PREF_MONITORING_ACTIVE = "monitoring_active";
    private static final String PREF_STAY_IN_INTERFACE = "stay_in_interface";
    private static final String PREF_LAST_ACTIVITY = "last_activity";
    private SharedPreferences sharedPreferences;

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
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Marcar que el usuario está en esta interfaz
        marcarComoInterfazActiva();

        // Inicializar componentes UI
        initializeComponents();

        // Configurar spinner
        setupSpinner();

        // Obtener referencia del fragmento de mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Referencias Firebase
        usuariosRef = FirebaseDatabase.getInstance().getReference("usuarios");
        ubicacionRef = FirebaseDatabase.getInstance().getReference("ubicacion_novidente");

        // Cargar usuarios
        cargarUsuariosDesdeFirebase();
    }

    private void marcarComoInterfazActiva() {
        // Marcar que esta interfaz debe mantenerse activa
        sharedPreferences.edit()
                .putBoolean(PREF_STAY_IN_INTERFACE, true)
                .putString(PREF_LAST_ACTIVITY, ContactoEmergenciaActivity.class.getSimpleName())
                .putLong("timestamp", System.currentTimeMillis())
                .apply();

        Log.d("ContactoEmergencia", "Interfaz marcada como activa");
    }

    private void initializeComponents() {
        spinnerUsuariosUbicacion = findViewById(R.id.spinnerUsuariosUbicacion);
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion);
        tvUltimaActualizacion = findViewById(R.id.tvUltimaActualizacion);
        statusIndicator = findViewById(R.id.statusIndicator);

        // Inicializar listas
        listaUsuarios = new ArrayList<>();
        mapaUsuarios = new HashMap<>();
    }

    private void setupSpinner() {
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, listaUsuarios);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUsuariosUbicacion.setAdapter(spinnerAdapter);

        spinnerUsuariosUbicacion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { // Ignorar "Seleccione un usuario"
                    String usuarioSeleccionado = listaUsuarios.get(position);
                    usuarioSeleccionadoUID = mapaUsuarios.get(usuarioSeleccionado);

                    // Guardar la selección y estado activo
                    guardarEstadoMonitoreo(usuarioSeleccionadoUID, true);

                    iniciarMonitoreoUbicacion();

                    tvEstadoConexion.setText("🔄 Conectando con " + usuarioSeleccionado + "...");
                    statusIndicator.setBackgroundTintList(
                            ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_orange_light)
                    );
                } else {
                    detenerMonitoreoUbicacion();
                    usuarioSeleccionadoUID = null;

                    // Limpiar selección guardada
                    guardarEstadoMonitoreo(null, false);

                    limpiarMapa();

                    tvEstadoConexion.setText("⚠️ Seleccione un usuario para monitorear");
                    tvUltimaActualizacion.setText("");
                    statusIndicator.setBackgroundTintList(
                            ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_red_light)
                    );
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                detenerMonitoreoUbicacion();
                usuarioSeleccionadoUID = null;
                guardarEstadoMonitoreo(null, false);
                limpiarMapa();
            }
        });
    }

    private void guardarEstadoMonitoreo(String uid, boolean isActive) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (uid != null && isActive) {
            editor.putString(PREF_SELECTED_USER, uid);
            editor.putBoolean(PREF_MONITORING_ACTIVE, true);
        } else {
            editor.remove(PREF_SELECTED_USER);
            editor.putBoolean(PREF_MONITORING_ACTIVE, false);
        }

        editor.putLong("last_update", System.currentTimeMillis());
        editor.apply();

        Log.d("ContactoEmergencia", "Estado guardado - UID: " + uid + ", Activo: " + isActive);
    }

    private void cargarSeleccionGuardada() {
        // Verificar si hay un monitoreo activo guardado
        boolean monitoreoActivo = sharedPreferences.getBoolean(PREF_MONITORING_ACTIVE, false);
        usuarioSeleccionadoUID = sharedPreferences.getString(PREF_SELECTED_USER, null);

        Log.d("ContactoEmergencia", "Cargando estado - UID: " + usuarioSeleccionadoUID + ", Activo: " + monitoreoActivo);

        if (usuarioSeleccionadoUID != null && monitoreoActivo) {
            // Buscar el usuario en la lista y seleccionarlo en el spinner
            for (Map.Entry<String, String> entry : mapaUsuarios.entrySet()) {
                if (entry.getValue().equals(usuarioSeleccionadoUID)) {
                    int position = listaUsuarios.indexOf(entry.getKey());
                    if (position >= 0) {
                        spinnerUsuariosUbicacion.setSelection(position);
                        iniciarMonitoreoUbicacion();

                        tvEstadoConexion.setText("🔄 Reconectando con " + entry.getKey() + "...");
                        statusIndicator.setBackgroundTintList(
                                ContextCompat.getColorStateList(this, android.R.color.holo_orange_light)
                        );

                        Toast.makeText(this, "📍 Reanudando monitoreo de ubicación", Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
            }
        }
    }

    private void cargarUsuariosDesdeFirebase() {
        usuariosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaUsuarios.clear();
                mapaUsuarios.clear();

                // Agregar opción por defecto
                listaUsuarios.add("Seleccione un usuario para monitorear");

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String uid = userSnapshot.getKey();
                    String nombre = userSnapshot.child("nombre").getValue(String.class);
                    String apellido = userSnapshot.child("apellido").getValue(String.class);
                    String tipo = userSnapshot.child("tipo").getValue(String.class);

                    // Solo agregar usuarios de tipo "No vidente"
                    if ("No vidente".equals(tipo) && nombre != null && apellido != null) {
                        String nombreCompleto = nombre + " " + apellido;
                        listaUsuarios.add(nombreCompleto);
                        mapaUsuarios.put(nombreCompleto, uid);
                    }
                }

                spinnerAdapter.notifyDataSetChanged();

                // Cargar selección guardada después de poblar el spinner
                cargarSeleccionGuardada();

                if (listaUsuarios.size() <= 1) {
                    Toast.makeText(ContactoEmergenciaActivity.this,
                            "⚠️ No se encontraron usuarios de tipo 'No vidente'",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ContactoEmergenciaActivity.this,
                        "❌ Error al cargar usuarios: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e("ContactoEmergencia", "Error al cargar usuarios", error.toException());
            }
        });
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

    private void iniciarMonitoreoUbicacion() {
        if (usuarioSeleccionadoUID == null) {
            Log.w("ContactoEmergencia", "No hay usuario seleccionado");
            return;
        }

        // Detener listener anterior si existe
        detenerMonitoreoUbicacion();

        // Crear nuevo listener para el usuario seleccionado
        ubicacionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    Double lat = snapshot.child("latitud").getValue(Double.class);
                    Double lng = snapshot.child("longitud").getValue(Double.class);

                    if (lat == null || lng == null) {
                        Log.w("ContactoEmergencia", "Latitud o longitud nula para usuario: " + usuarioSeleccionadoUID);

                        tvEstadoConexion.setText("⚠️ Esperando ubicación del usuario...");
                        statusIndicator.setBackgroundTintList(
                                ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_orange_light)
                        );
                        return;
                    }

                    LatLng ubicacion = new LatLng(lat, lng);
                    actualizarUbicacionEnMapa(ubicacion);

                    // Actualizar estado de conexión
                    String nombreUsuario = obtenerNombreUsuarioSeleccionado();
                    tvEstadoConexion.setText("✅ Monitoreando: " + nombreUsuario);
                    tvUltimaActualizacion.setText("Actualizado: " + timeFormat.format(new Date()));
                    statusIndicator.setBackgroundTintList(
                            ContextCompat.getColorStateList(ContactoEmergenciaActivity.this, android.R.color.holo_green_light)
                    );

                    // Actualizar timestamp en SharedPreferences
                    sharedPreferences.edit()
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

        // Escuchar cambios en la ubicación del usuario específico
        ubicacionRef.child(usuarioSeleccionadoUID).addValueEventListener(ubicacionListener);
        Log.d("ContactoEmergencia", "Iniciado monitoreo para usuario: " + usuarioSeleccionadoUID);
    }

    private void detenerMonitoreoUbicacion() {
        if (ubicacionListener != null && usuarioSeleccionadoUID != null) {
            ubicacionRef.child(usuarioSeleccionadoUID).removeEventListener(ubicacionListener);
            ubicacionListener = null;
            Log.d("ContactoEmergencia", "Monitoreo detenido");
        }
    }

    private void actualizarUbicacionEnMapa(LatLng ubicacion) {
        if (mMap == null) {
            Log.w("ContactoEmergencia", "Mapa no está listo");
            return;
        }

        String nombreUsuario = obtenerNombreUsuarioSeleccionado();

        if (marker == null) {
            // Crear nuevo marcador
            marker = mMap.addMarker(new MarkerOptions()
                    .position(ubicacion)
                    .title("📍 " + nombreUsuario)
                    .snippet("Ubicación en tiempo real"));

            // Centrar el mapa en la ubicación
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ubicacion, 17));
        } else {
            // Actualizar posición del marcador existente
            marker.setPosition(ubicacion);
            marker.setTitle("📍 " + nombreUsuario);

            // Mover la cámara suavemente a la nueva ubicación
            mMap.animateCamera(CameraUpdateFactory.newLatLng(ubicacion));
        }
    }

    private String obtenerNombreUsuarioSeleccionado() {
        if (usuarioSeleccionadoUID != null) {
            for (Map.Entry<String, String> entry : mapaUsuarios.entrySet()) {
                if (entry.getValue().equals(usuarioSeleccionadoUID)) {
                    return entry.getKey();
                }
            }
        }
        return "Usuario desconocido";
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
        // NO detener el monitoreo en onDestroy para mantenerlo activo
        Log.d("ContactoEmergencia", "Activity destruida - manteniendo estado en SharedPreferences");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Mantener el monitoreo activo en segundo plano
        Log.d("ContactoEmergencia", "Activity pausada - monitoreo manteniéndose activo");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Marcar nuevamente como interfaz activa
        marcarComoInterfazActiva();

        // Recargar usuarios en caso de que hayan cambiado
        cargarUsuariosDesdeFirebase();

        Log.d("ContactoEmergencia", "Activity reanudada");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("ContactoEmergencia", "Activity iniciada - verificando estado guardado");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Mantener el estado guardado para poder reanudar
        Log.d("ContactoEmergencia", "Activity detenida - estado mantenido");
    }

    @Override
    public void onBackPressed() {
        // Personalizar comportamiento del botón atrás
        if (sharedPreferences.getBoolean(PREF_MONITORING_ACTIVE, false)) {
            Toast.makeText(this, "📍 Monitoreo activo en segundo plano", Toast.LENGTH_SHORT).show();
        }
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Método público para verificar si hay monitoreo activo (para usar desde otras activities)
    public static boolean hayMonitoreoActivo(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_MONITORING_ACTIVE, false) &&
                prefs.getString(PREF_SELECTED_USER, null) != null;
    }

    // Método para limpiar el estado (usar cuando sea necesario resetear)
    public static void limpiarEstadoMonitoreo(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .remove(PREF_SELECTED_USER)
                .putBoolean(PREF_MONITORING_ACTIVE, false)
                .putBoolean(PREF_STAY_IN_INTERFACE, false)
                .apply();
    }
}