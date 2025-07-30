package com.example.contactodeemergencia;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

public class MainActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton, btnRegistrarse;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    // SharedPreferences para guardar datos del usuario logueado
    private static final String PREFS_NAME = "UserSession";
    private static final String PREF_USER_UID = "user_uid";
    private static final String PREF_USER_EMAIL = "user_email";
    private static final String PREF_USER_PHONE = "user_phone";
    private static final String PREF_USER_TYPE = "user_type";
    private static final String PREF_USER_NAME = "user_name";
    private static final String PREF_IS_LOGGED_IN = "is_logged_in";
    private SharedPreferences userPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializar Firebase y SharedPreferences
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("usuarios");
        userPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Verificar si el usuario ya está logueado
        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean isLoggedIn = userPrefs.getBoolean(PREF_IS_LOGGED_IN, false);

        if (currentUser != null && isLoggedIn) {
            // Si ya está logueado, verificar su tipo y redirigir
            String tipoGuardado = userPrefs.getString(PREF_USER_TYPE, null);
            if (tipoGuardado != null) {
                redirigirSegunTipo(tipoGuardado);
            } else {
                // Si no tenemos el tipo guardado, consultarlo de Firebase
                verificarTipoUsuario(currentUser.getUid());
            }
            return;
        }

        // Inicializar vistas
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        btnRegistrarse = findViewById(R.id.btnRegistrarse);

        // Configurar listeners
        loginButton.setOnClickListener(v -> loginUser());

        btnRegistrarse.setOnClickListener(v -> {
            Intent intentRegister = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intentRegister);
        });
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mostrar progreso
        loginButton.setEnabled(false);
        loginButton.setText("Iniciando sesión...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    // Restaurar botón
                    loginButton.setEnabled(true);
                    loginButton.setText("Iniciar sesión");

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();
                            // Guardar datos básicos inmediatamente
                            guardarDatosBasicosUsuario(uid, email);
                            // Luego obtener datos completos de Firebase
                            obtenerDatosCompletosUsuario(uid);
                        }
                    } else {
                        String errorMessage = "Autenticación fallida";
                        if (task.getException() != null) {
                            String exception = task.getException().getMessage();
                            if (exception != null) {
                                if (exception.contains("password")) {
                                    errorMessage = "Contraseña incorrecta";
                                } else if (exception.contains("user") && exception.contains("found")) {
                                    errorMessage = "Usuario no encontrado";
                                } else if (exception.contains("email")) {
                                    errorMessage = "Formato de email inválido";
                                }
                            }
                        }
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void guardarDatosBasicosUsuario(String uid, String email) {
        SharedPreferences.Editor editor = userPrefs.edit();
        editor.putString(PREF_USER_UID, uid);
        editor.putString(PREF_USER_EMAIL, email);
        editor.putBoolean(PREF_IS_LOGGED_IN, true);
        editor.putLong("login_timestamp", System.currentTimeMillis());
        editor.apply();
    }

    private void obtenerDatosCompletosUsuario(String uid) {
        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String tipo = snapshot.child("tipo").getValue(String.class);
                    String nombre = snapshot.child("nombre").getValue(String.class);
                    String apellido = snapshot.child("apellido").getValue(String.class);
                    String telefonoEmergencia = snapshot.child("telefonoEmergencia").getValue(String.class);

                    // Guardar todos los datos en SharedPreferences
                    SharedPreferences.Editor editor = userPrefs.edit();
                    editor.putString(PREF_USER_TYPE, tipo);
                    editor.putString(PREF_USER_NAME, (nombre != null ? nombre : "") + " " + (apellido != null ? apellido : ""));

                    // Guardar el teléfono según la estructura actual de Firebase
                    if (telefonoEmergencia != null) {
                        editor.putString(PREF_USER_PHONE, telefonoEmergencia);
                    }

                    editor.apply();

                    Log.d("MainActivity", "Datos guardados - Tipo: " + tipo + ", Teléfono: " + telefonoEmergencia);

                    if (tipo != null) {
                        redirigirSegunTipo(tipo.trim());
                    } else {
                        Toast.makeText(MainActivity.this, "No se encontró el tipo de usuario", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "No se encontraron datos del usuario", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error al obtener datos: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void redirigirSegunTipo(String tipo) {
        switch (tipo) {


            case "Contacto de emergencia":
                startActivity(new Intent(MainActivity.this, ContactoEmergenciaActivity.class));
                finish();
                break;

            default:
                Toast.makeText(MainActivity.this, "Tipo de usuario desconocido: " + tipo, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void verificarTipoUsuario(String uid) {
        usersRef.child(uid).child("tipo").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String tipo = snapshot.getValue(String.class);
                if (tipo == null) {
                    Toast.makeText(MainActivity.this, "No se encontró el tipo de usuario", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Actualizar el tipo en SharedPreferences
                userPrefs.edit().putString(PREF_USER_TYPE, tipo).apply();

                redirigirSegunTipo(tipo.trim());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error al leer tipo de usuario: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static SharedPreferences getUserPreferences(android.content.Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    // Método estático para cerrar sesión
    public static void cerrarSesion(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();

        // También cerrar sesión de Firebase
        FirebaseAuth.getInstance().signOut();
    }

    // Método estático para verificar si hay sesión activa
    public static boolean haySesionActiva(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_IS_LOGGED_IN, false);
    }
}