#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include "BluetoothSerial.h"  // Bluetooth

#define TRIG_PIN 26
#define ECHO_PIN 25
#define BUZZER 33
#define PANIC_BUTTON 27
#define LED_INDICADOR 2

const char* ssid = "HONOR X6a";
const char* password = "1234578910";

WebServer server(80);
BluetoothSerial BTSerial;

float distancia = 0;
bool panicoActivo = false;
bool mensajeEnviado = false; // Para no enviar múltiples veces la alerta

float medirDistancia() {
    digitalWrite(TRIG_PIN, LOW);
    delayMicroseconds(2);
    digitalWrite(TRIG_PIN, HIGH);
    delayMicroseconds(10);
    digitalWrite(TRIG_PIN, LOW);

    long duracion = pulseIn(ECHO_PIN, HIGH, 30000);
    if (duracion == 0) return -1;

    float distancia_cm = duracion * 0.034 / 2;
    if (distancia_cm < 2 || distancia_cm > 400) return -1;

    return distancia_cm;
}

void handleRoot() {
    String html = "<!DOCTYPE html><html><head><meta http-equiv='refresh' content='1'>";
    html += "<meta charset='UTF-8'><title>Medición de Distancia</title>";
    html += "<style>";
    html += "body{background:#222;color:#fff;font-family:sans-serif;text-align:center;margin-top:50px;}";
    html += ".card{background:#333;padding:30px 50px;border-radius:16px;display:inline-block;box-shadow:0 4px 16px #0005;}";
    html += "h1{color:#00e676;} .dist{font-size:2.5rem;margin:20px 0;color:#00bcd4;}";
    html += ".panic{margin-top:10px;font-size:1.2rem;color:";
    html += panicoActivo ? "#ff5252" : "#888";
    html += ";}";
    html += ".footer{margin-top:40px;color:#888;font-size:0.9rem;}";
    html += "</style></head><body>";
    html += "<div class='card'>";
    html += "<h1>Medición de Distancia</h1>";
    if (distancia >= 0) {
        html += "<div class='dist'>" + String(distancia, 2) + " cm</div>";
    } else {
        html += "<div class='dist'>--- cm</div>";
    }
    html += "<div class='panic'>Botón de Pánico: ";
    html += panicoActivo ? "ACTIVADO" : "Desactivado";
    html += "</div>";
    html += "</div>";
    html += "<div class='footer'>Proyecto ESP32 &copy; 2025</div>";
    html += "</body></html>";
    server.send(200, "text/html", html);
}

void setup() {
    Serial.begin(115200);
    pinMode(TRIG_PIN, OUTPUT);
    pinMode(ECHO_PIN, INPUT);
    pinMode(BUZZER, OUTPUT);
    pinMode(PANIC_BUTTON, INPUT_PULLUP);
    pinMode(LED_INDICADOR, OUTPUT);

    // Conexión WiFi
    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nConectado a WiFi");
    Serial.println(WiFi.localIP());

    // Conexión Bluetooth
    BTSerial.begin("ESP_Panico");  // Nombre visible por Bluetooth
    Serial.println("Bluetooth iniciado como ESP_Panico");

    // Servidor web
    server.on("/", handleRoot);
    server.begin();
}

void loop() {
    server.handleClient();
    panicoActivo = digitalRead(PANIC_BUTTON) == LOW;

    if (panicoActivo) {
        digitalWrite(BUZZER, HIGH);
        digitalWrite(LED_INDICADOR, HIGH);

        // Enviar alerta por Bluetooth solo una vez por activación
        if (!mensajeEnviado) {
            BTSerial.println("BOTON_PANICO"); // Mensaje que espera la app Android
            Serial.println("[Bluetooth] Alerta enviada");
            mensajeEnviado = true;
        }

        delay(100); // Mantener alerta activa
        return;
    } else {
        digitalWrite(LED_INDICADOR, LOW);
        mensajeEnviado = false; // Permitir siguiente alerta
    }

    // Medir distancia solo si no está activo el pánico
    float nuevaDistancia = medirDistancia();
    if (nuevaDistancia != -1) {
        distancia = nuevaDistancia;
        Serial.print("Distancia medida: ");
        Serial.println(distancia);
    }

    // Lógica del buzzer por distancia
    if (distancia >= 0 && distancia < 50) {
        int beepDelay;
        if (distancia < 20) {
            beepDelay = map((int)distancia, 0, 20, 50, 200);
        } else {
            beepDelay = map((int)distancia, 20, 50, 200, 500);
        }
        digitalWrite(BUZZER, HIGH);
        delay(30);
        digitalWrite(BUZZER, LOW);
        delay(beepDelay);
    } else {
        digitalWrite(BUZZER, LOW);
        delay(100);
    }
}