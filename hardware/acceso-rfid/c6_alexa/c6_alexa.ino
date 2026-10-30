// ESP32-C6 de la Alexa: sensores de contacto Matter para que la Alexa hable.
//
// Una C6 no puede mandarle texto a la Alexa, pero la Alexa sí puede reaccionar
// a sensores Matter con rutinas. Esta C6 es un dispositivo Matter con un sensor
// de contacto por socio y uno de "acceso denegado" (el de "gimnasio ocupado",
// para el foco, está en la C6 puente):
//
//   Sensor               se ABRE cuando...         se CIERRA cuando...
//   1 Chema              entra Chema               sale Chema
//   2 Valeria            entra Valeria             sale Valeria
//   3 Yael               entra Yael                sale Yael
//   4 Eduardo            entra Eduardo             sale Eduardo
//   5 Edwin              entra Edwin               sale Edwin
//   6 Voz                entrada por voz           salida por voz
//   7 Acceso denegado    se niega un acceso        5 s después, solo
//
// En la app Alexa se le pone a cada sensor su nombre y se crean las rutinas:
//   "Cuando Chema se abra → Alexa dice: Bienvenido Chema"
//   "Cuando Chema se cierre → Alexa dice: Hasta luego Chema"
//   "Cuando Voz se abra → Alexa dice: Entrada por comando de voz"
//   "Cuando Voz se cierre → Alexa dice: Salida por comando de voz"
//   "Cuando Acceso denegado se abra → Alexa dice: Acceso denegado"
//
// Los avisos llegan de la C6 puente (c6_puente) por la red WiFi local (UDP).
// Las dos C6 tienen que estar en la misma red.
//
// Emparejar con la Alexa: al arrancar sin emparejar, el monitor muestra el código
// de 11 dígitos y una URL con el QR. App Alexa → Dispositivos → + → Matter.
// Mantén BOOT 5 s para borrar el emparejamiento.
//
// Placa: tu ESP32-C6 (p. ej. "ESP32C6 Dev Module" o "XIAO_ESP32C6").
// Herramientas → Partition Scheme: "Huge APP (3MB No OTA/1MB SPIFFS)".

#include <Matter.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include "secrets.h"

#define BOTON         BOOT_PIN
#define PUERTO_UDP    4210        // el mismo en c6_puente
#define CIERRE_DENEGADO_MS 5000

// En este orden quedan los sensores en la app Alexa. El nombre tiene que ser
// el primer nombre del socio en GymTrack (como lo manda la LILYGO).
const char* SOCIOS[] = {"Chema", "Valeria", "Yael", "Eduardo", "Edwin", "Voz"};
const int NUM_SOCIOS = sizeof(SOCIOS) / sizeof(SOCIOS[0]);

MatterContactSensor sensores[NUM_SOCIOS];
MatterContactSensor denegado;
bool abierto[NUM_SOCIOS] = {};  // lo que tiene cada sensor en Matter
WiFiUDP udp;
uint32_t denegadoDesde = 0;
uint32_t botonDesde = 0;
uint32_t ultimoAvisoCodigo = 0;

// "Chema Ortiz" → índice de "Chema"; -1 si no tiene sensor.
int indiceDe(const String& nombre) {
  String primero = nombre;
  int espacio = primero.indexOf(' ');
  if (espacio > 0) primero = primero.substring(0, espacio);
  for (int i = 0; i < NUM_SOCIOS; i++) {
    if (primero.equalsIgnoreCase(SOCIOS[i])) return i;
  }
  return -1;
}

// Se escribe el estado directo en Matter. setContact() de la librería no sirve:
// Matter guarda el estado de los sensores en la memoria y, si al arrancar uno
// quedó "abierto" de antes, setContact() cree que ya está y no lo cambia (y
// getContact() dice otra cosa). Así la Alexa nunca veía el cambio.
// En Matter "contacto = true" es cerrado; la Alexa lo muestra como "cerrado".
// Primero se lee el valor (así trae el tipo que Matter espera) y luego se cambia.
bool ponerContacto(MatterContactSensor& sensor, bool cerrado) {
  esp_matter_attr_val_t valor = esp_matter_invalid(NULL);
  bool ok = sensor.getAttributeVal(0x0045, 0x0000, &valor);      // BooleanState · StateValue
  if (ok && valor.val.b != cerrado) {
    valor.val.b = cerrado;
    ok = sensor.updateAttributeVal(0x0045, 0x0000, &valor);
  }
  if (!ok) Serial.println("  Matter RECHAZÓ el cambio del sensor");
  return ok;
}

void abrirSocio(int i, bool abrir) {
  if (ponerContacto(sensores[i], !abrir)) abierto[i] = abrir;
}

void atender(const String& aviso) {
  int p1 = aviso.indexOf('|'), p2 = aviso.indexOf('|', p1 + 1);
  if (p1 != 1 || p2 < 0) return;
  char tipo = aviso[0];
  String nombre = aviso.substring(p1 + 1, p2);
  int dentro = aviso.substring(p2 + 1).toInt();
  int i = indiceDe(nombre);
  Serial.printf("Aviso: %c %s (dentro: %d)\n", tipo, nombre.c_str(), dentro);

  if (tipo == 'D') return;                 // solo el aforo (tras un comando de voz)
  if (tipo == 'N') {
    ponerContacto(denegado, false);       // se abre → "Acceso denegado"
    denegadoDesde = millis();
    Serial.println("  Acceso denegado: sensor abierto");
    return;
  }
  if (i < 0) {
    Serial.printf("  %s no tiene sensor: agrégalo a SOCIOS si quieres que la Alexa lo salude\n", nombre.c_str());
    return;
  }
  if (tipo == 'E' || tipo == 'A') {
    // Si ya estaba abierto (no pasó su tag al salir), se cierra y se vuelve a
    // abrir para que la rutina de bienvenida corra otra vez.
    if (abierto[i]) { abrirSocio(i, false); delay(1500); }
    abrirSocio(i, true);
    Serial.printf("  %s: sensor abierto (bienvenida)\n", SOCIOS[i]);
  } else if (tipo == 'S') {
    abrirSocio(i, false);
    Serial.printf("  %s: sensor cerrado (despedida)\n", SOCIOS[i]);
  }
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== ESP32-C6 de la Alexa (Matter) ===");
  pinMode(BOTON, INPUT_PULLUP);

#if !CONFIG_ENABLE_CHIPOBLE
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.printf("Conectando a %s", WIFI_SSID);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println(" OK");
#endif

  for (int i = 0; i < NUM_SOCIOS; i++) sensores[i].begin(true);   // todos cerrados: nadie dentro
  denegado.begin(true);
  Matter.begin();
  // Al arrancar: todos cerrados, aunque se hayan guardado abiertos.
  for (int i = 0; i < NUM_SOCIOS; i++) ponerContacto(sensores[i], true);
  ponerContacto(denegado, true);
  udp.begin(PUERTO_UDP);
  Serial.printf("Sensores: %d socios + acceso denegado\n", NUM_SOCIOS);
  if (!Matter.isDeviceCommissioned()) {
    Serial.printf("Sin emparejar. Código para la app Alexa: %s\n", Matter.getManualPairingCode().c_str());
    Serial.printf("QR: %s\n", Matter.getOnboardingQRCodeUrl().c_str());
  }
}

void loop() {
  int largo = udp.parsePacket();
  if (largo > 0) {
    char texto[96];
    int n = udp.read(texto, sizeof(texto) - 1);
    texto[n > 0 ? n : 0] = '\0';
    String aviso = texto;
    aviso.trim();
    atender(aviso);
  }

  if (denegadoDesde && millis() - denegadoDesde > CIERRE_DENEGADO_MS) {
    ponerContacto(denegado, true);          // listo para el siguiente
    denegadoDesde = 0;
  }

  if (!Matter.isDeviceCommissioned() && millis() - ultimoAvisoCodigo > 15000) {
    ultimoAvisoCodigo = millis();
    Serial.printf("Esperando emparejar con la Alexa. Código: %s\n", Matter.getManualPairingCode().c_str());
  }

  if (digitalRead(BOTON) == LOW) {
    if (!botonDesde) botonDesde = millis();
    if (millis() - botonDesde > 5000) {
      Serial.println("Borrando el emparejamiento Matter...");
      Matter.decommission();
      botonDesde = 0;
    }
  } else {
    botonDesde = 0;
  }
  delay(5);
}
