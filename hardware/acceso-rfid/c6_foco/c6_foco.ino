// ESP32-C6 del foco: recibe los avisos de la LILYGO y es un foco Matter.
//
// La LILYGO le manda por serial (9600) una línea por evento:
//   "E|Chema|3"  entrada (quedan 3 dentro)      "S|Chema|0"  salida (no queda nadie)
//   "N|Edwin|-1" acceso negado                   "A|Visitante|-1" abierta desde el panel
// Qué hace:
//   - Prende el foco (relevador) cuando alguien entra y lo apaga cuando ya no
//     queda nadie dentro. Como es un foco Matter, la Alexa lo ve, y también se
//     puede prender o apagar desde la app o con la voz ("Alexa, apaga el foco").
//   - Reenvía cada aviso por la red WiFi local (UDP) a la ESP32-C6 de la Alexa
//     (c6_alexa), que es la que la hace hablar.
//
// Conexión: GPIO 13 de la LILYGO → RX_LILYGO de esta C6, y GND con GND.
// El relevador del foco en RELE. Para la demo puede ser un LED con 220 Ω.
// ⚠ Si el foco es de 127 V, usa un módulo de relevador para corriente alterna,
// con su caja, y que la conexión a la corriente la haga alguien que sepa.
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
#define RELE          19      // relevador o LED del foco
#define RELE_ACTIVO   HIGH    // cámbialo a LOW si tu módulo de relevador se activa en bajo
#define BOTON         BOOT_PIN
#define PUERTO_UDP    4210    // el mismo en c6_alexa

MatterOnOffLight foco;
WiFiUDP udp;
String linea;
uint32_t botonDesde = 0;
uint32_t ultimoAvisoCodigo = 0;

bool moverRele(bool prendido) {
  digitalWrite(RELE, prendido ? RELE_ACTIVO : !RELE_ACTIVO);
  Serial.printf("Foco %s\n", prendido ? "PRENDIDO" : "apagado");
  return true;
}

// Desde la LILYGO: se cambia el foco y se le avisa a Matter (la Alexa lo ve).
void ponerFoco(bool prendido) {
  moverRele(prendido);
  foco.setOnOff(prendido);
}

void reenviarAlexa(const String& aviso) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Sin WiFi: la C6 de la Alexa no se entera");
    return;
  }
  udp.beginPacket(IPAddress(255, 255, 255, 255), PUERTO_UDP);
  udp.print(aviso);
  udp.endPacket();
}

void atender(const String& aviso) {
  int p1 = aviso.indexOf('|'), p2 = aviso.indexOf('|', p1 + 1);
  if (p1 != 1 || p2 < 0) return;   // no es un aviso de la LILYGO
  char tipo = aviso[0];
  String nombre = aviso.substring(p1 + 1, p2);
  int dentro = aviso.substring(p2 + 1).toInt();
  Serial.printf("LILYGO: %c %s (dentro: %d)\n", tipo, nombre.c_str(), dentro);

  if (tipo == 'E' || tipo == 'A') ponerFoco(true);
  if (tipo == 'S' && dentro == 0) ponerFoco(false);   // -1 = no se sabe: se queda como está
  reenviarAlexa(aviso);
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== ESP32-C6 del foco (Matter) ===");
  pinMode(RELE, OUTPUT);
  moverRele(false);
  pinMode(BOTON, INPUT_PULLUP);
  Serial1.begin(9600, SERIAL_8N1, RX_LILYGO, -1);

#if !CONFIG_ENABLE_CHIPOBLE
  // Sin BLE, la C6 entra al WiFi con secrets.h (con BLE, la Alexa le da la red al emparejar).
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.printf("Conectando a %s", WIFI_SSID);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println(" OK");
#endif

  foco.begin(false);
  foco.onChange(moverRele);   // la Alexa o la app de Matter lo prenden o apagan
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
