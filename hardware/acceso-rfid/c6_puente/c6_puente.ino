// ESP32-C6 puente: recibe los avisos de la LILYGO y es el sensor Matter
// "Gimnasio ocupado". No lleva relevador: el foco es un foco inteligente que la
// Alexa prende y apaga con una rutina.
//
// La LILYGO le manda por serial (9600) una línea por evento:
//   "E|Chema|3"  entrada (quedan 3 dentro)      "S|Chema|0"  salida (no queda nadie)
//   "N|Edwin|-1" acceso negado                   "A|Visitante|-1" abierta desde el panel
//   "E|Voz|-1"   entrada por comando de voz      "D||2"  solo el aforo: quedan 2 dentro
// Qué hace:
//   - Sensor de contacto Matter "Gimnasio ocupado": se ABRE cuando entra la
//     primera persona y se CIERRA cuando ya no queda nadie. En la app Alexa:
//       "Cuando Gimnasio ocupado se abra → encender la lámpara (foco Steren)"
//       "Cuando Gimnasio ocupado se cierre → apagar la lámpara"
//   - Reenvía cada aviso por la red WiFi local (UDP) a la ESP32-C6 de la Alexa
//     (c6_alexa), que tiene los sensores para los anuncios.
//
// Conexión: GPIO 13 de la LILYGO → RX_LILYGO de esta C6, y GND con GND.
// Se alimenta por su USB-C (un cargador de celular).
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

#define RX_LILYGO     18      // llega de GPIO 13 de la LILYGO
#define BOTON         BOOT_PIN
#define PUERTO_UDP    4210    // el mismo en c6_alexa

MatterContactSensor ocupado;   // en Matter "contacto = true" es cerrado (gimnasio vacío)
WiFiUDP udp;
String linea;
uint32_t botonDesde = 0;
uint32_t ultimoAvisoCodigo = 0;

void reenviarAlexa(const String& aviso) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Sin WiFi: la C6 de la Alexa no se entera");
    return;
  }
  udp.beginPacket(IPAddress(255, 255, 255, 255), PUERTO_UDP);
  udp.print(aviso);
  udp.endPacket();
}

// Abierto con la primera persona, cerrado cuando ya no queda nadie.
// dentro = cuántos quedan (-1 = no se sabe todavía: se queda como está, salvo
// en una entrada, que sí lo abre).
void actualizarOcupado(char tipo, int dentro) {
  bool antes = !ocupado.getContact();
  bool ahora = antes;
  if ((tipo == 'E' || tipo == 'A') && dentro != 0) ahora = true;
  if (dentro > 0) ahora = true;
  if (dentro == 0) ahora = false;
  if (ahora != antes) {
    ocupado.setContact(!ahora);
    Serial.printf("Gimnasio ocupado: %s (la rutina %s el foco)\n", ahora ? "ABIERTO" : "cerrado", ahora ? "prende" : "apaga");
  }
}

void atender(const String& aviso) {
  int p1 = aviso.indexOf('|'), p2 = aviso.indexOf('|', p1 + 1);
  if (p1 != 1 || p2 < 0) return;   // no es un aviso de la LILYGO
  char tipo = aviso[0];
  String nombre = aviso.substring(p1 + 1, p2);
  int dentro = aviso.substring(p2 + 1).toInt();
  Serial.printf("LILYGO: %c %s (dentro: %d)\n", tipo, nombre.c_str(), dentro);

  actualizarOcupado(tipo, dentro);
  if (tipo != 'D') reenviarAlexa(aviso);   // el aforo solo le importa a este sensor
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== ESP32-C6 puente (Matter: Gimnasio ocupado) ===");
  pinMode(BOTON, INPUT_PULLUP);
  Serial1.begin(9600, SERIAL_8N1, RX_LILYGO, -1);

#if !CONFIG_ENABLE_CHIPOBLE
  // Sin BLE, la C6 entra al WiFi con secrets.h (con BLE, la Alexa le da la red al emparejar).
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.printf("Conectando a %s", WIFI_SSID);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println(" OK");
#endif

  ocupado.begin(true);   // cerrado: gimnasio vacío
  Matter.begin();
  udp.begin(PUERTO_UDP);
  if (!Matter.isDeviceCommissioned()) {
    Serial.printf("Sin emparejar. Código para la app Alexa: %s\n", Matter.getManualPairingCode().c_str());
    Serial.printf("QR: %s\n", Matter.getOnboardingQRCodeUrl().c_str());
  }
}

void loop() {
  // Avisos de la LILYGO, una línea a la vez.
  while (Serial1.available()) {
    char c = Serial1.read();
    if (c == '\n') { linea.trim(); atender(linea); linea = ""; }
    else if (linea.length() < 80) linea += c;
  }

  if (!Matter.isDeviceCommissioned() && millis() - ultimoAvisoCodigo > 15000) {
    ultimoAvisoCodigo = millis();
    Serial.printf("Esperando emparejar con la Alexa. Código: %s\n", Matter.getManualPairingCode().c_str());
  }

  // BOOT 5 s: se borra el emparejamiento (para volver a agregarla a la Alexa).
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
