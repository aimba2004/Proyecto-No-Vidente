package com.example.novidenteproject;

public class Usuario {
    public String nombre;
    public String apellido;
    public String cedula;
    public String direccion;
    public String telefonoEmergencia;
    public String tipo;

    public Usuario() {
        // Constructor vacío requerido por Firebase
    }

    public Usuario(String nombre, String apellido, String cedula, String direccion, String telefonoEmergencia, String tipo) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.cedula = cedula;
        this.direccion = direccion;
        this.telefonoEmergencia = telefonoEmergencia;
        this.tipo = tipo;
    }
}
