// Etapa 1: probar el lector YRM con la T-SIM7070G, sin red.
//
// Sirve para encontrar la potencia con la que el lector detecta la tarjeta a
// 30-50 cm y no más lejos (para no abrir con alguien que solo pasa cerca).
//
// Abre el Monitor Serie a 115200 con "Nueva línea" y escribe:
//   p 15   fija la potencia en 15 dBm
//   g      pregunta la potencia actual
//   r      reinicia los contadores
//
// Cada segundo imprime, por etiqueta, cuántas veces la leyó y su RSSI promedio.
// Aleja la tarjeta poco a poco: donde las lecturas por segundo caen a 0 es el
// alcance con esa potencia.

#include "yrm.h"

#define YRM_RX 32   // al TXD del YRM
#define YRM_TX 33   // al RXD del YRM
#define LED_PLACA 12
#define LED_PLACA_ENCENDIDO LOW

const unsigned long CADA_LECTURA_MS = 100;
const unsigned long CADA_RESUMEN_MS = 1000;
const int MAX_VISTAS = 8;

struct Vista {
  char epc[2 * yrm::MAX_EPC + 1];
  int lecturas;
  long sumaRssi;
};

yrm::Lector lector;
Vista vistas[MAX_VISTAS];
int totalVistas = 0;
unsigned long ultimaLectura = 0;
unsigned long ultimoResumen = 0;
String linea;

void enviar(const uint8_t* orden, size_t n) {
  Serial2.write(orden, n);
}

void anotar(const yrm::Etiqueta& e) {
  char epc[2 * yrm::MAX_EPC + 1];
  yrm::epcHex(e, epc);
  for (int i = 0; i < totalVistas; i++) {
    if (strcmp(vistas[i].epc, epc) == 0) {
      vistas[i].lecturas++;
      vistas[i].sumaRssi += e.rssi;
      return;
    }
  }
  if (totalVistas == MAX_VISTAS) return;
  Vista& v = vistas[totalVistas++];
  strcpy(v.epc, epc);
  v.lecturas = 1;
  v.sumaRssi = e.rssi;
  Serial.printf("Nueva etiqueta: %s  (RSSI %d dBm)\n", epc, e.rssi);
}

void imprimirResumen() {
  if (totalVistas == 0) return;
  Serial.println("--- último segundo ---");
  for (int i = 0; i < totalVistas; i++) {
    Vista& v = vistas[i];
    if (v.lecturas == 0) {
      Serial.printf("%s  sin lecturas\n", v.epc);
    } else {
      Serial.printf("%s  %2d lect/s  RSSI %ld dBm\n", v.epc, v.lecturas, v.sumaRssi / v.lecturas);
    }
    v.lecturas = 0;
    v.sumaRssi = 0;
  }
}

void atenderOrden(String s) {
  s.trim();
  uint8_t orden[16];
  if (s.startsWith("p ")) {
    int dbm = s.substring(2).toInt();
    if (dbm < 0 || dbm > 30) {
      Serial.println("La potencia va de 0 a 30 dBm (el módulo puede aceptar menos).");
      return;
    }
    enviar(orden, yrm::ordenFijarPotencia(dbm, orden));
    Serial.printf("Potencia pedida: %d dBm\n", dbm);
    delay(50);
    enviar(orden, yrm::ordenLeerPotencia(orden));
  } else if (s == "g") {
    enviar(orden, yrm::ordenLeerPotencia(orden));
  } else if (s == "r") {
    totalVistas = 0;
    Serial.println("Contadores en cero.");
  } else if (s.length()) {
    Serial.println("Órdenes: p <dBm>, g, r");
  }
}

void setup() {
  Serial.begin(115200);
  Serial2.begin(115200, SERIAL_8N1, YRM_RX, YRM_TX);
  pinMode(LED_PLACA, OUTPUT);
  digitalWrite(LED_PLACA, !LED_PLACA_ENCENDIDO);
  delay(300);
  Serial.println("\nPrueba del lector YRM. Órdenes: p <dBm>, g, r");
  uint8_t orden[8];
  enviar(orden, yrm::ordenLeerPotencia(orden));
}

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      atenderOrden(linea);
      linea = "";
    } else if (c != '\r') {
      linea += c;
    }
  }

  bool vioEtiqueta = false;
  while (Serial2.available()) {
    if (!lector.alimentar(Serial2.read())) continue;
    const yrm::Trama& t = lector.trama();
    yrm::Etiqueta e;
    int dbm;
    if (yrm::leerEtiqueta(t, e)) {
      anotar(e);
      vioEtiqueta = true;
    } else if ((dbm = yrm::potenciaLeida(t)) >= 0) {
      Serial.printf("Potencia actual: %d dBm\n", dbm);
    } else if (t.tipo == yrm::TIPO_RESPUESTA && t.comando == yrm::CMD_FIJAR_POTENCIA) {
      Serial.println(t.datos[0] == 0 ? "El módulo aceptó la potencia." : "El módulo rechazó la potencia.");
    }
  }
  if (vioEtiqueta) digitalWrite(LED_PLACA, LED_PLACA_ENCENDIDO);

  unsigned long ahora = millis();
  if (ahora - ultimaLectura >= CADA_LECTURA_MS) {
    ultimaLectura = ahora;
    digitalWrite(LED_PLACA, !LED_PLACA_ENCENDIDO);
    uint8_t orden[8];
    enviar(orden, yrm::ordenInventario(orden));
  }
  if (ahora - ultimoResumen >= CADA_RESUMEN_MS) {
    ultimoResumen = ahora;
    imprimirResumen();
    if (lector.descartadas()) Serial.printf("(tramas dañadas hasta ahora: %lu)\n", lector.descartadas());
  }
}
