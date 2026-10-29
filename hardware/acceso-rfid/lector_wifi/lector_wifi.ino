// Lector de acceso conectado a GymTrack: LILYGO T-SIM7070G + YRM1004.
//
// Cada tag que lee se lo pregunta a GTWeb (POST /api/lectores/accesos) y hace
// lo que el servidor responda:
//   ENTRADA  -> LED verde y abre        SALIDA   -> LED azul y abre
//   NEGADO   -> LED rojo y, si es una alerta nueva, pulso a la XIAO (toma y sube 3 fotos)
//   REGISTRO -> LED azul: el panel estaba en "Registrar tag nuevo" y capturó este tag
//   IGNORADO -> nada (pasó otra vez muy pronto)
// En paralelo, una tarea espera los comandos del panel (GET /api/lectores/comandos):
// si el dueño da acceso desde la alerta, se prende el verde y abre.
//
// Sin internet decide con la lista de tags que descarga cada minuto (/lista).
//
// Comandos por el monitor serie (115200):
//   n  simular un tag desconocido (crea la alerta en el panel y dispara la cámara)
//   +  subir la potencia 1 dBm      -  bajarla 1 dBm
//
// Placa: "ESP32 Wrover Module". Mismas conexiones que prueba_puerta.
// Antes de subirlo: copia secrets.example.h como secrets.h y llena el WiFi y el servidor.

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include "secrets.h"

#define RFID_RX         32   // llega del TXD del YRM
#define RFID_TX         33   // va al RXD del YRM
#define LED_VERDE       18
#define LED_AZUL        19
#define LED_ROJO        21
#define BUZZER          22
#define DISPARO_CAMARA  13   // va a D1 de la XIAO
// Reservados para la versión final, sin conectar todavía:
//   servo de la puerta en GPIO 23; botón "Solicitar acceso" en GPIO 34 (con 10 kΩ a 3V3).

const uint32_t REARME_MS        = 3000;    // el tag tiene que alejarse esto para contar otra vez
const uint32_t SENAL_MS         = 1500;
const uint32_t LISTA_CADA_MS    = 60000;
const uint16_t ESPERA_HTTP_MS   = 4000;    // más que esto y se decide con la lista local
const char*    EPC_SIMULADO     = "E20000000000000000000001";

HardwareSerial yrm(2);
int potenciaDbm = 15;

// Lo que deja la tarea de comandos para que loop() lo haga (0 = nada, 1 = abrir, 2 = negar).
volatile int comandoPendiente = 0;
char nombreComando[48] = "";

// Tags vistos hace poco, para no mandar el mismo 4 veces por segundo.
struct Visto { char epc[65]; uint32_t ultima; };
Visto vistos[8];

// Lista para decidir sin internet.
struct Permitido { char epc[65]; char nombre[32]; bool permitido; };
Permitido lista[60];
int tamLista = 0;
uint32_t ultimaLista = 0;

// ------------------------------------------------------------
// Protocolo del YRM (chip M100), igual que en prueba_puerta:
// BB | tipo | cmd | largo (2) | datos | checksum | 7E
// ------------------------------------------------------------
void enviar(uint8_t cmd, const uint8_t* datos, uint16_t n) {
  uint8_t cs = cmd + (n >> 8) + (n & 0xFF);
  yrm.write(0xBB); yrm.write(0x00); yrm.write(cmd);
  yrm.write(n >> 8); yrm.write(n & 0xFF);
  for (uint16_t i = 0; i < n; i++) { yrm.write(datos[i]); cs += datos[i]; }
  yrm.write(cs); yrm.write(0x7E);
}

bool leerByte(uint8_t& b, uint32_t limite) {
  while (!yrm.available()) {
    if (millis() > limite) return false;
  }
  b = yrm.read();
  return true;
}

// Devuelve el largo de los datos; -1 si no llegó nada, -2 si la trama vino mal.
int leerTrama(uint8_t& tipo, uint8_t& cmd, uint8_t* datos, int max, uint32_t esperaMs) {
  uint32_t limite = millis() + esperaMs;
  uint8_t b;
  do {
    if (!leerByte(b, limite)) return -1;
  } while (b != 0xBB);

  uint8_t largoH, largoL;
  if (!leerByte(tipo, limite) || !leerByte(cmd, limite) ||
      !leerByte(largoH, limite) || !leerByte(largoL, limite)) return -1;
  int n = (largoH << 8) | largoL;
  if (n > max) return -2;

  uint8_t cs = tipo + cmd + largoH + largoL;
  for (int i = 0; i < n; i++) {
    if (!leerByte(datos[i], limite)) return -1;
    cs += datos[i];
  }
  uint8_t csRecibido, fin;
  if (!leerByte(csRecibido, limite) || !leerByte(fin, limite)) return -1;
  if (fin != 0x7E || cs != csRecibido) return -2;
  return n;
}

void vaciarEntrada() {
  while (yrm.available()) yrm.read();
}

bool pedirVersion() {
  const uint8_t p[] = {0x00};
  vaciarEntrada();
  enviar(0x03, p, 1);
  uint8_t tipo, cmd, datos[64];
  int n = leerTrama(tipo, cmd, datos, sizeof(datos), 500);
  if (n <= 1 || cmd != 0x03) return false;
  Serial.print("YRM responde, version: ");
  for (int i = 1; i < n; i++) Serial.write(datos[i]);
  Serial.println();
  return true;
}

bool fijarPotencia(int dbm) {
  uint16_t valor = dbm * 100;
  const uint8_t p[] = {(uint8_t)(valor >> 8), (uint8_t)(valor & 0xFF)};
  vaciarEntrada();
  enviar(0xB6, p, 2);
  uint8_t tipo, cmd, datos[8];
  int n = leerTrama(tipo, cmd, datos, sizeof(datos), 500);
  bool ok = n == 1 && cmd == 0xB6 && datos[0] == 0x00;
  Serial.printf("Potencia %d dBm: %s\n", dbm, ok ? "OK" : "sin confirmar");
  return ok;
}

// ------------------------------------------------------------
// Señales y puerta
// ------------------------------------------------------------
void pitido(int ms) {
  digitalWrite(BUZZER, HIGH); delay(ms); digitalWrite(BUZZER, LOW);
}

void senal(int led, int pitidos) {
  digitalWrite(led, HIGH);
  uint32_t inicio = millis();
  for (int i = 0; i < pitidos; i++) { pitido(80); delay(80); }
  while (millis() - inicio < SENAL_MS) delay(10);
  digitalWrite(led, LOW);
}

// El servo llega después; por ahora solo se avisa.
void abrirPuerta() {
  Serial.println("         [puerta] abrir 3 s");
}

void dispararCamara() {
  Serial.println("         [camara] tomar 3 fotos");
  digitalWrite(DISPARO_CAMARA, HIGH);
  delay(100);
  digitalWrite(DISPARO_CAMARA, LOW);
}

// ------------------------------------------------------------
// Red
// ------------------------------------------------------------
// http:// en la red local; https:// con Render.
// TODO versión final: fijar el certificado raíz de Render en lugar de setInsecure().
bool empezar(HTTPClient& http, WiFiClientSecure& seguro, WiFiClient& simple, const char* ruta) {
  String url = String(SERVIDOR) + ruta;
  bool ok = url.startsWith("https") ? http.begin(seguro, url) : http.begin(simple, url);
  http.addHeader("X-Lector-Key", LECTOR_LLAVE);
  return ok;
}

// Saca el valor de "campo":"valor" de una respuesta JSON sencilla.
String campo(const String& json, const char* nombre) {
  String clave = String("\"") + nombre + "\":\"";
  int i = json.indexOf(clave);
  if (i < 0) return "";
  i += clave.length();
  int f = json.indexOf('"', i);
  return f < 0 ? "" : json.substring(i, f);
}

void conectarWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("Conectando a %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  for (int i = 0; i < 30 && WiFi.status() != WL_CONNECTED; i++) { delay(500); Serial.print("."); }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf(" OK, IP %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println(" sin WiFi (se decide con la lista local)");
  }
}

void descargarLista() {
  ultimaLista = millis();
  if (WiFi.status() != WL_CONNECTED) return;
  WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
  HTTPClient http;
  http.setTimeout(ESPERA_HTTP_MS);
  if (!empezar(http, seguro, simple, "/api/lectores/lista")) return;
  int code = http.GET();
  if (code == 200) {
    String json = http.getString();
    int n = 0, i = 0;
    while (n < 60 && (i = json.indexOf("{", i)) >= 0) {
      int f = json.indexOf("}", i);
      if (f < 0) break;
      String obj = json.substring(i, f + 1);
      String epc = campo(obj, "epc");
      if (epc.length()) {
        strlcpy(lista[n].epc, epc.c_str(), sizeof(lista[n].epc));
        strlcpy(lista[n].nombre, campo(obj, "nombre").c_str(), sizeof(lista[n].nombre));
        lista[n].permitido = obj.indexOf("\"permitido\":true") >= 0;
        n++;
      }
      i = f + 1;
    }
    tamLista = n;
    Serial.printf("Lista local: %d tags\n", tamLista);
  } else {
    Serial.printf("No se pudo bajar la lista (HTTP %d)\n", code);
  }
  http.end();
}

// Pregunta al servidor qué hacer con el tag. false = no hubo respuesta.
// fotos = el servidor abrió una alerta nueva y quiere las fotos de la XIAO.
bool preguntarAlServidor(const char* epc, String& resultado, String& nombre, String& motivo, bool& fotos) {
  if (WiFi.status() != WL_CONNECTED) return false;
  WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
  HTTPClient http;
  http.setConnectTimeout(ESPERA_HTTP_MS);
  http.setTimeout(ESPERA_HTTP_MS);
  if (!empezar(http, seguro, simple, "/api/lectores/accesos")) return false;
  http.addHeader("Content-Type", "application/json");
  int code = http.POST(String("{\"epc\":\"") + epc + "\"}");
  String cuerpo = code > 0 ? http.getString() : "";
  http.end();
  if (code != 200) {
    Serial.printf("Servidor: HTTP %d %s\n", code, cuerpo.c_str());
    return false;
  }
  resultado = campo(cuerpo, "resultado");
  nombre = campo(cuerpo, "nombre");
  motivo = campo(cuerpo, "motivo");
  fotos = cuerpo.indexOf("\"fotos\":true") >= 0;
  return resultado.length() > 0;
}

// Corre aparte (núcleo 0): espera hasta 15 s a que el dueño decida en el panel.
void tareaComandos(void*) {
  WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
  for (;;) {
    if (WiFi.status() != WL_CONNECTED) { vTaskDelay(pdMS_TO_TICKS(1000)); continue; }
    HTTPClient http;
    http.setTimeout(20000);
    if (empezar(http, seguro, simple, "/api/lectores/comandos")) {
      int code = http.GET();
      if (code == 200) {
        String cuerpo = http.getString();
        String c = campo(cuerpo, "comando");
        strlcpy(nombreComando, campo(cuerpo, "nombre").c_str(), sizeof(nombreComando));
        comandoPendiente = c == "abrir" ? 1 : c == "negar" ? 2 : 0;
      } else if (code != 204) {
        vTaskDelay(pdMS_TO_TICKS(3000));   // servidor caído o despertando: no saturarlo
      }
      http.end();
    }
    vTaskDelay(pdMS_TO_TICKS(100));
  }
}

// ------------------------------------------------------------
// Lógica de la puerta
// ------------------------------------------------------------
bool seguiaCerca(const char* epc) {
  uint32_t ahora = millis();
  int libre = 0;
  for (int i = 0; i < 8; i++) {
    if (strcmp(vistos[i].epc, epc) == 0) {
      bool cerca = ahora - vistos[i].ultima < REARME_MS;
      vistos[i].ultima = ahora;
      return cerca;
    }
    if (vistos[i].ultima < vistos[libre].ultima) libre = i;
  }
  strlcpy(vistos[libre].epc, epc, sizeof(vistos[libre].epc));
  vistos[libre].ultima = ahora;
  return false;
}

void decidirSinRed(const char* epc) {
  for (int i = 0; i < tamLista; i++) {
    if (strcmp(lista[i].epc, epc) == 0) {
      if (lista[i].permitido) {
        Serial.printf("ENTRADA  %s  [sin red: lista local]\n", lista[i].nombre);
        abrirPuerta();
        senal(LED_VERDE, 1);
      } else {
        Serial.printf("NEGADO   %s: membresia vencida  [sin red]\n", lista[i].nombre);
        dispararCamara();
        senal(LED_ROJO, 3);
      }
      return;
    }
  }
  Serial.printf("NEGADO   tag desconocido %s  [sin red]\n", epc);
  dispararCamara();
  senal(LED_ROJO, 3);
}

// forzar = sin esperar a que el tag se aleje (el comando 'n' del monitor).
void alLeerTag(const char* epc, int8_t rssi, bool forzar) {
  if (seguiaCerca(epc) && !forzar) return;
  String resultado, nombre, motivo;
  bool fotos = false;
  if (!preguntarAlServidor(epc, resultado, nombre, motivo, fotos)) {
    decidirSinRed(epc);
    return;
  }
  if (resultado == "ENTRADA") {
    Serial.printf("ENTRADA  %s  RSSI=%d\n", nombre.c_str(), rssi);
    abrirPuerta();
    senal(LED_VERDE, 1);
  } else if (resultado == "SALIDA") {
    Serial.printf("SALIDA   %s  RSSI=%d\n", nombre.c_str(), rssi);
    abrirPuerta();
    senal(LED_AZUL, 2);
  } else if (resultado == "NEGADO") {
    // Si su alerta ya estaba abierta, ya tiene sus fotos: no se toman otras.
    Serial.printf("NEGADO   %s %s (%s)\n", nombre.length() ? nombre.c_str() : epc, motivo.c_str(),
                  fotos ? "alerta nueva en el panel" : "su alerta sigue pendiente");
    if (fotos) dispararCamara();
    senal(LED_ROJO, 3);
  } else if (resultado == "REGISTRO") {
    Serial.printf("REGISTRO tag %s capturado%s%s: asignalo en el panel\n", epc,
                  nombre.length() ? ", ahora es de " : "", nombre.c_str());
    senal(LED_AZUL, 1);
  } else {
    Serial.printf("ignorado %s: %s\n", nombre.c_str(), motivo.c_str());
  }
}

void inventario() {
  vaciarEntrada();
  enviar(0x22, nullptr, 0);
  uint8_t tipo, cmd, datos[64];
  while (true) {
    int n = leerTrama(tipo, cmd, datos, sizeof(datos), 150);
    if (n == -1) break;
    if (n == -2) { Serial.println("Trama con error (revisa GND o alimentacion)"); continue; }
    if (tipo == 0x01 && cmd == 0xFF) break;   // "ningún tag"
    if (tipo != 0x02 || cmd != 0x22 || n < 5) continue;

    int8_t rssi = (int8_t)datos[0];
    uint16_t pc = (datos[1] << 8) | datos[2];
    int largoEpc = ((pc >> 11) & 0x1F) * 2;
    if (3 + largoEpc + 2 > n || largoEpc > 32) continue;

    char epc[65];
    for (int i = 0; i < largoEpc; i++) sprintf(epc + 2 * i, "%02X", datos[3 + i]);
    epc[2 * largoEpc] = '\0';
    alLeerTag(epc, rssi, false);
  }
}

void atenderComando() {
  int c = comandoPendiente;
  if (!c) return;
  comandoPendiente = 0;
  if (c == 1) {
    Serial.printf("PANEL    el dueño abrio la puerta a %s\n", nombreComando);
    abrirPuerta();
    senal(LED_VERDE, 1);
  } else {
    Serial.printf("PANEL    el dueño nego el acceso a %s\n", nombreComando);
    senal(LED_ROJO, 1);
  }
}

// ------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Lector de acceso GymTrack (T-SIM7070G + YRM1004) ===");

  pinMode(LED_VERDE, OUTPUT); pinMode(LED_AZUL, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);  pinMode(BUZZER, OUTPUT);
  pinMode(DISPARO_CAMARA, OUTPUT);
  digitalWrite(DISPARO_CAMARA, LOW);

  // El YRM puede tardar en encender más que la LILYGO: se reintenta unos segundos.
  yrm.begin(115200, SERIAL_8N1, RFID_RX, RFID_TX);
  bool yrmListo = false;
  for (int i = 0; i < 8 && !yrmListo; i++) { delay(500); yrmListo = pedirVersion(); }
  if (!yrmListo) {
    Serial.println("El YRM NO responde: revisa GND comun, TX/RX cruzados, EN y los 5 V.");
    digitalWrite(LED_ROJO, HIGH);
  } else {
    fijarPotencia(potenciaDbm);
  }

  conectarWifi();
  Serial.printf("Servidor: %s\n", SERVIDOR);
  descargarLista();
  xTaskCreatePinnedToCore(tareaComandos, "comandos", 8192, nullptr, 1, nullptr, 0);
  Serial.println("Listo. Comandos: n (simular tag desconocido), + / - (potencia)");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'n') alLeerTag(EPC_SIMULADO, 0, true);
    if (c == '+' && potenciaDbm < 26) fijarPotencia(++potenciaDbm);
    if (c == '-' && potenciaDbm > 5)  fijarPotencia(--potenciaDbm);
  }
  atenderComando();
  // Si se cae el WiFi, setAutoReconnect lo recupera solo; mientras, se usa la lista.
  if (millis() - ultimaLista > LISTA_CADA_MS) descargarLista();
  inventario();
  delay(150);
}
