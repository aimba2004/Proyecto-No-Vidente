package com.example.novidenteproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Iniciar directamente con NoVidenteActivity
        Intent intent = new Intent(MainActivity.this, NoVidenteActivity.class);
        startActivity(intent);
        finish(); // Cerrar MainActivity para que no quede en el stack

        // El resto del código original se mantiene por si necesitas volver al login
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        btnRegistrarse = findViewById(R.id.btnRegistrarse);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("usuarios");

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

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();
                            verificarTipoUsuario(uid);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Autenticación fallida", Toast.LENGTH_SHORT).show();
                    }
                });
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

                switch (tipo.trim()) {
                    case "No vidente":
                        startActivity(new Intent(MainActivity.this, NoVidenteActivity.class));
                        finish();
                        break;

                    case "Contacto de emergencia":
                        startActivity(new Intent(MainActivity.this, ContactoEmergenciaActivity.class));
                        finish();
                        break;

                    default:
                        Toast.makeText(MainActivity.this, "Tipo de usuario desconocido: " + tipo, Toast.LENGTH_SHORT).show();
                        break;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error al leer tipo de usuario", Toast.LENGTH_SHORT).show();
            }
        });
    }
}