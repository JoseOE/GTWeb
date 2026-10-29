// Lector de acceso conectado a GymTrack: LILYGO T-SIM7070G + YRM1004.
//
// Cada tag que lee se lo pregunta a GTWeb (POST /api/lectores/accesos) y hace
// lo que el servidor responda:
//   ENTRADA  -> LED verde y abre        SALIDA   -> LED azul y abre
//   NEGADO   -> LED rojo; queda la alerta en el panel para que el dueño decida
//   REGISTRO -> LED azul: el panel estaba en "Registrar tag nuevo" y capturó este tag
//   IGNORADO -> nada (pasó otra vez muy pronto)
// En paralelo, una tarea pregunta por los comandos del servidor (GET /api/lectores/comandos):
// si el dueño da acceso desde la alerta,
// se prende el verde y abre.
//
// Tres formas de llegar a internet (se elige con el monitor y queda guardada):
//   WiFi  -> las redes de secrets.h (hasta 3).
//   LTE-M -> el módem SIM7070G con la SIM (LTE Cat-M o NB-IoT).
//   2G    -> el mismo módem en GSM/GPRS.
// Con LTE o 2G la LILYGO crea su propia red WiFi (LECTOR_AP_SSID) para que la
// XIAO se conecte a ella; lo que la XIAO le manda (sus comandos de voz) lo reenvía al
// servidor por el módem.
//
// Sin internet decide con la lista de tags que descarga cada minuto (/lista).
//
// Cada entrada, salida o negado se le avisa por GPIO 13 a la ESP32-C6 del foco
// (c6_foco): ella prende el foco y le pasa el aviso a la C6 de la Alexa (c6_alexa),
// que por Matter hace que la Alexa diga "Bienvenido Chema", "Hasta luego Chema"
// o "Acceso denegado".
//
// Comandos por el monitor serie (115200):
//   w  usar WiFi        l  usar LTE-M        g  usar 2G        r  estado de la red
//   n  simular un tag desconocido (crea la alerta en el panel)
//   +  subir la potencia 1 dBm      -  bajarla 1 dBm
//
// Placa: "ESP32 Wrover Module". Librerías: TinyGSM y ArduinoHttpClient.
// Mismas conexiones que prueba_puerta; el módem y la antena LTE ya vienen en la placa.
// Para LTE/2G: SIM con datos y, de preferencia, una batería 18650 puesta (el
// módem pide picos de corriente que el USB solo a veces no da, sobre todo en 2G).
// Antes de subirlo: copia secrets.example.h como secrets.h y llena tus datos.

#define TINY_GSM_MODEM_SIM7080   // el SIM7070G usa los mismos comandos que el SIM7080
#define TINY_GSM_RX_BUFFER 1024

#include <WiFi.h>
#include <WiFiMulti.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Preferences.h>
#include <TinyGsmClient.h>
#include <ArduinoHttpClient.h>
#include "secrets.h"

// Lo opcional de secrets.h: un secrets.h viejo, sin esto, también compila.
#ifndef WIFI_SSID_2
#define WIFI_SSID_2 ""
#define WIFI_PASS_2 ""
#endif
#ifndef WIFI_SSID_3
#define WIFI_SSID_3 ""
#define WIFI_PASS_3 ""
#endif
#ifndef APN
#define APN "internet.itelcel.com"   // Telcel
#endif
#ifndef LECTOR_AP_SSID
#define LECTOR_AP_SSID "GymTrack-Lector"
#define LECTOR_AP_PASS "gymtrack2026"
#endif

#define RFID_RX         32   // llega del TXD del YRM
#define RFID_TX         33   // va al RXD del YRM
#define LED_VERDE       18
#define LED_AZUL        19
#define LED_ROJO        21
#define BUZZER          22
#define MODEM_RX        26   // llega del TX del módem (dentro de la placa)
#define MODEM_TX        27
#define MODEM_PWR       4    // pulso para encender el módem
#define AVISO_C6        13   // va al RX de la ESP32-C6 del foco (serial de 9600, solo TX)
#define BAUDIOS_C6      9600
// Reservados para la versión final, sin conectar todavía:
//   servo de la puerta en GPIO 23; botón "Solicitar acceso" en GPIO 34 (con 10 kΩ a 3V3).

const uint32_t REARME_MS        = 3000;    // el tag tiene que alejarse esto para contar otra vez
const uint32_t SENAL_MS         = 1500;
const uint32_t LISTA_CADA_MS    = 60000;
const uint16_t ESPERA_WIFI_MS   = 4000;    // más que esto y se decide con la lista local
const uint16_t ESPERA_MODEM_MS  = 12000;   // por LTE/2G cada petición tarda más (TLS por el módem)
const char*    EPC_SIMULADO     = "E20000000000000000000001";

enum Red { RED_WIFI = 0, RED_LTE = 1, RED_2G = 2 };
const char* NOMBRE_RED[] = {"WiFi", "LTE-M", "2G"};

HardwareSerial yrm(2);
HardwareSerial modemSerial(1);
TinyGsm modem(modemSerial);
TinyGsmClientSecure modemSeguro(modem, 0);
TinyGsmClient modemSimple(modem, 1);
SemaphoreHandle_t candado;          // el módem atiende una petición a la vez
WiFiServer relevo(80);              // en LTE/2G: lo que manda la XIAO
Preferences prefs;
WiFiMulti redes;
int potenciaDbm = 15;

volatile int red = RED_WIFI;
volatile bool redLista = false;     // false mientras se cambia de red

String hostServidor;
uint16_t puertoServidor = 443;
bool servidorSeguro = true;

// Lo que deja la tarea de comandos para que loop() lo haga (0 = nada, 1 = abrir, 2 = negar).
volatile int comandoPendiente = 0;
char nombreComando[48] = "";

// Tags vistos hace poco, para no mandar el mismo 4 veces por segundo.
struct Visto { char epc[65]; uint32_t ultima; };
Visto vistos[8];

// Respuesta del servidor (codigo < 0 = no hubo respuesta).
struct Respuesta { int codigo; String cuerpo; };
int dentroServidor = -1;        // cuántos quedan dentro, según la última respuesta

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

// ------------------------------------------------------------
// Peticiones al servidor, por WiFi o por el módem
// ------------------------------------------------------------
// Saca el valor de "campo":"valor" de una respuesta JSON sencilla.
String campo(const String& json, const char* nombre) {
  String clave = String("\"") + nombre + "\":\"";
  int i = json.indexOf(clave);
  if (i < 0) return "";
  i += clave.length();
  int f = json.indexOf('"', i);
  return f < 0 ? "" : json.substring(i, f);
}

// "https://gtweb.onrender.com" → host, puerto y si va cifrado (para el módem).
void leerServidor() {
  String s = SERVIDOR;
  servidorSeguro = s.startsWith("https://");
  s = s.substring(s.indexOf("//") + 2);
  int barra = s.indexOf('/');
  if (barra >= 0) s = s.substring(0, barra);
  int dosPuntos = s.indexOf(':');
  if (dosPuntos >= 0) {
    puertoServidor = s.substring(dosPuntos + 1).toInt();
    hostServidor = s.substring(0, dosPuntos);
  } else {
    puertoServidor = servidorSeguro ? 443 : 80;
    hostServidor = s;
  }
}

// Una petición al servidor con la llave del lector. codigo < 0 = no hubo respuesta.
// TODO versión final: fijar el certificado raíz de Render en lugar de no revisarlo.
Respuesta pedir(const char* metodo, const String& ruta, const uint8_t* cuerpo, size_t largo,
                const char* tipo, uint32_t esperaMs) {
  Respuesta r = {-1, ""};
  if (!redLista) return r;

  if (red == RED_WIFI) {
    if (WiFi.status() != WL_CONNECTED) return r;
    WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
    HTTPClient http;
    http.setConnectTimeout(esperaMs);
    http.setTimeout(esperaMs);
    String url = String(SERVIDOR) + ruta;
    if (!(servidorSeguro ? http.begin(seguro, url) : http.begin(simple, url))) return r;
    http.addHeader("X-Lector-Key", LECTOR_LLAVE);
    if (tipo) http.addHeader("Content-Type", tipo);
    r.codigo = http.sendRequest(metodo, (uint8_t*)cuerpo, largo);
    if (r.codigo > 0) r.cuerpo = http.getString();
    http.end();
    return r;
  }

  // LTE / 2G: el módem es uno solo, así que se toma por turnos.
  if (xSemaphoreTake(candado, pdMS_TO_TICKS(esperaMs + 5000)) != pdTRUE) return r;
  if (modem.isGprsConnected()) {
    Client& cliente = servidorSeguro ? (Client&)modemSeguro : (Client&)modemSimple;
    HttpClient http(cliente, hostServidor.c_str(), puertoServidor);
    http.setHttpResponseTimeout(esperaMs);
    http.beginRequest();
    int error = strcmp(metodo, "POST") == 0 ? http.post(ruta) : http.get(ruta);
    if (error == 0) {
      http.sendHeader("X-Lector-Key", LECTOR_LLAVE);
      if (cuerpo && largo) {
        http.sendHeader("Content-Type", tipo ? tipo : "application/octet-stream");
        http.sendHeader("Content-Length", (int)largo);
        http.beginBody();
        http.write(cuerpo, largo);
      }
      http.endRequest();
      r.codigo = http.responseStatusCode();
      if (r.codigo > 0) r.cuerpo = http.responseBody();
    }
    http.stop();
  }
  xSemaphoreGive(candado);
  return r;
}

uint32_t esperaServidor() {
  return red == RED_WIFI ? ESPERA_WIFI_MS : ESPERA_MODEM_MS;
}

// ------------------------------------------------------------
// WiFi
// ------------------------------------------------------------
// Las redes que se sabe la placa (hasta 3, en secrets.h). Se conecta sola a la
// conocida que tenga mejor señal: en casa, en la escuela o con el hotspot.
void agregarRedes() {
  static bool agregadas = false;
  if (agregadas) return;
  agregadas = true;
  const char* nombres[] = {WIFI_SSID, WIFI_SSID_2, WIFI_SSID_3};
  const char* claves[]  = {WIFI_PASS, WIFI_PASS_2, WIFI_PASS_3};
  Serial.print("Redes conocidas:");
  for (int i = 0; i < 3; i++) {
    if (strlen(nombres[i]) == 0) continue;
    redes.addAP(nombres[i], claves[i]);
    Serial.printf(" \"%s\"", nombres[i]);
  }
  Serial.println();
}

// Por qué no conecta, en palabras (WiFi.status()).
const char* motivoWifi(wl_status_t s) {
  switch (s) {
    case WL_NO_SSID_AVAIL:  return "no encuentra ninguna de las redes (nombre mal escrito, red de 5 GHz o señal débil)";
    case WL_CONNECT_FAILED: return "la red rechazó la conexión (revisa la contraseña)";
    default:                return "no encuentra ninguna de las redes conocidas cerca";
  }
}

bool wifiAvisado = false;      // ya se imprimió que conectó
uint32_t ultimoIntentoRed = 0;

void avisarConexion() {
  wifiAvisado = true;
  Serial.printf("WiFi: conectado a \"%s\", IP %s, señal %d dBm\n",
                WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(), WiFi.RSSI());
}

void conectarWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  agregarRedes();
  Serial.println("Buscando una red conocida...");
  for (int i = 0; i < 3 && redes.run(8000) != WL_CONNECTED; i++) Serial.println("  ...otra vez");
  ultimoIntentoRed = millis();
  if (WiFi.status() == WL_CONNECTED) {
    avisarConexion();
  } else {
    wifiAvisado = false;
    Serial.printf("Sin WiFi: %s. Se reintenta cada 10 s; mientras, decide con la lista local.\n",
                  motivoWifi(WiFi.status()));
  }
}

// Si no hay WiFi, otro intento cada 10 s (busca las redes unos segundos).
void vigilarWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiAvisado) {
      avisarConexion();
      descargarLista();
    }
    return;
  }
  if (wifiAvisado) {
    wifiAvisado = false;
    Serial.println("Se perdió el WiFi: decide con la lista local mientras vuelve.");
  }
  if (millis() - ultimoIntentoRed < 10000) return;
  ultimoIntentoRed = millis();
  Serial.printf("Reintentando WiFi (%s)\n", motivoWifi(WiFi.status()));
  redes.run(5000);
}

// ------------------------------------------------------------
// Módem (LTE-M y 2G). Quien llame debe tener el candado.
// ------------------------------------------------------------
// El pulso en PWRKEY prende el módem si está apagado, pero lo APAGA si ya estaba
// prendido (p. ej. si el ESP32 se reinició y el módem no).
void pulsoModem() {
  pinMode(MODEM_PWR, OUTPUT);
  digitalWrite(MODEM_PWR, LOW);  delay(100);
  digitalWrite(MODEM_PWR, HIGH); delay(1000);
  digitalWrite(MODEM_PWR, LOW);
}

bool encenderModem(int cual) {
  Serial.printf("Encendiendo el módem para %s...\n", NOMBRE_RED[cual]);
  modemSerial.begin(115200, SERIAL_8N1, MODEM_RX, MODEM_TX);
  // Primero se le pregunta un buen rato: si ya está prendido, no se le da el pulso.
  bool responde = modem.testAT(4000);
  // Si no, pulso para prenderlo; si tampoco, quizá el pulso lo apagó: otro pulso.
  for (int intento = 0; intento < 2 && !responde; intento++) {
    Serial.println(intento == 0 ? "  prendiendo el módem..." : "  no respondió: otro pulso...");
    pulsoModem();
    delay(5000);
    responde = modem.testAT(10000);
  }
  if (!responde) {
    Serial.println("El módem no responde. Revisa que la placa tenga batería o buena alimentación.");
    return false;
  }
  modem.init();
  if (modem.getSimStatus() != 1) {
    Serial.println("La SIM no está lista: revisa que esté bien puesta y sin PIN.");
    return false;
  }
  modem.setNetworkMode(cual == RED_2G ? 13 : 38);    // 13 = solo GSM, 38 = solo LTE
  if (cual == RED_LTE) modem.setPreferredMode(3);    // LTE Cat-M o NB-IoT, la que haya
  Serial.print("Buscando red celular (hasta 90 s)...");
  if (!modem.waitForNetwork(90000L)) {
    Serial.printf(" sin red %s. ¿Hay cobertura de ese tipo con tu operador?\n", NOMBRE_RED[cual]);
    return false;
  }
  Serial.println(" OK");
  Serial.printf("Conectando datos con el APN \"%s\"...", APN);
  if (!modem.gprsConnect(APN, "", "")) {
    Serial.println(" falló. Revisa el APN y que la SIM tenga datos.");
    return false;
  }
  Serial.printf(" OK\n%s: %s, señal %d/31, IP %s\n", NOMBRE_RED[cual], modem.getOperator().c_str(),
                modem.getSignalQuality(), modem.getLocalIP().c_str());
  return true;
}

void apagarModem() {
  modem.gprsDisconnect();
  modem.poweroff();
}

// Si se cayeron los datos celulares, otro intento cada 30 s; a los 3 fallos
// seguidos se reinicia el módem completo (a veces se queda trabado sin red).
int fallosDatos = 0;

void vigilarModem() {
  if (millis() - ultimoIntentoRed < 30000) return;
  ultimoIntentoRed = millis();
  if (xSemaphoreTake(candado, 0) != pdTRUE) return;   // ocupado en una petición: después
  if (!modem.isGprsConnected()) {
    fallosDatos++;
    Serial.printf("Sin datos por %s (señal %d/31, intento %d): reconectando...\n",
                  NOMBRE_RED[red], modem.getSignalQuality(), fallosDatos);
    bool ok = (modem.isNetworkConnected() || modem.waitForNetwork(30000L)) && modem.gprsConnect(APN, "", "");
    if (ok) {
      fallosDatos = 0;
      Serial.printf("%s de vuelta, IP %s\n", NOMBRE_RED[red], modem.getLocalIP().c_str());
    } else if (fallosDatos >= 3) {
      Serial.println("No regresa: reiniciando el módem. Si pasa seguido, revisa la batería y la alimentación del YRM.");
      apagarModem();
      delay(3000);
      if (encenderModem(red)) fallosDatos = 0;
    }
  }
  xSemaphoreGive(candado);
}

// Cambiar de red reinicia la placa: así la red nueva arranca limpia (al volver
// de LTE a WiFi, el WiFi quedaba conectado pero sin poder llegar al servidor).
void cambiarRed(int nueva) {
  if (nueva == red) return;
  Serial.printf("\nCambiando a %s: se guarda y se reinicia la placa...\n", NOMBRE_RED[nueva]);
  prefs.putInt("red", nueva);
  if (red != RED_WIFI) {
    redLista = false;
    xSemaphoreTake(candado, portMAX_DELAY);
    apagarModem();
  }
  delay(300);
  ESP.restart();
}

// ------------------------------------------------------------
// Cambio de red
// ------------------------------------------------------------
void usarRed(int nueva) {
  redLista = false;
  delay(300);   // que la tarea de comandos suelte lo que estaba haciendo
  Serial.printf("\n--- Cambiando a %s ---\n", NOMBRE_RED[nueva]);

  // Se apaga lo de la red anterior.
  xSemaphoreTake(candado, portMAX_DELAY);
  if (red != RED_WIFI) {
    apagarModem();
    relevo.end();
    WiFi.softAPdisconnect(true);
  }
  xSemaphoreGive(candado);

  red = nueva;
  prefs.putInt("red", nueva);
  if (nueva == RED_WIFI) {
    conectarWifi();
  } else {
    // Red propia para la XIAO: ella se conecta aquí y la LILYGO reenvía.
    WiFi.disconnect(true);
    WiFi.mode(WIFI_AP);
    WiFi.softAP(LECTOR_AP_SSID, LECTOR_AP_PASS);
    relevo.begin();
    Serial.printf("Red para la XIAO: \"%s\" (IP %s)\n", LECTOR_AP_SSID, WiFi.softAPIP().toString().c_str());
    xSemaphoreTake(candado, portMAX_DELAY);
    bool ok = encenderModem(nueva);
    xSemaphoreGive(candado);
    if (!ok) Serial.println("Sin datos celulares por ahora: se reintenta cada 30 s; mientras, decide con la lista local.");
    ultimoIntentoRed = millis();
  }
  redLista = true;
  descargarLista();
}

void mostrarEstado() {
  Serial.printf("\nRed elegida: %s\n", NOMBRE_RED[red]);
  if (red == RED_WIFI) {
    if (WiFi.status() == WL_CONNECTED) {
      Serial.printf("  WiFi \"%s\", IP %s, señal %d dBm\n", WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(), WiFi.RSSI());
    } else {
      Serial.printf("  Sin WiFi: %s\n", motivoWifi(WiFi.status()));
    }
  } else if (xSemaphoreTake(candado, pdMS_TO_TICKS(15000)) == pdTRUE) {
    String info;
    modem.sendAT(GF("+CPSI?"));          // tipo de red real: GSM, LTE CAT-M1, LTE NB-IOT
    modem.waitResponse(2000L, info);
    info.trim();
    Serial.printf("  Operador: %s, señal %d/31, datos: %s, IP %s\n", modem.getOperator().c_str(),
                  modem.getSignalQuality(), modem.isGprsConnected() ? "sí" : "no", modem.getLocalIP().c_str());
    Serial.printf("  %s\n", info.c_str());
    Serial.printf("  Red para la XIAO: \"%s\", %d conectada(s)\n", LECTOR_AP_SSID, WiFi.softAPgetStationNum());
    xSemaphoreGive(candado);
  }
  Serial.printf("  Servidor: %s · lista local: %d tags\n", SERVIDOR, tamLista);
}

// ------------------------------------------------------------
// Relevo para la XIAO (solo en LTE/2G): recibe su petición por la red propia
// y la manda al servidor por el módem, con la llave del lector.
// ------------------------------------------------------------
String leerLinea(WiFiClient& c, uint32_t limite) {
  String linea;
  while (millis() < limite) {
    if (!c.available()) { delay(1); continue; }
    char ch = c.read();
    if (ch == '\n') break;
    if (ch != '\r') linea += ch;
  }
  return linea;
}

void responderRelevo(WiFiClient& c, int codigo, const String& cuerpo) {
  c.printf("HTTP/1.1 %d %s\r\nContent-Type: application/json\r\nContent-Length: %u\r\nConnection: close\r\n\r\n",
           codigo, codigo == 200 ? "OK" : "Error", (unsigned)cuerpo.length());
  c.print(cuerpo);
  c.stop();
}

void atenderRelevo() {
  WiFiClient c = relevo.available();
  if (!c) return;
  uint32_t limite = millis() + 10000;
  String primera = leerLinea(c, limite);               // "POST /api/lectores/fotos HTTP/1.1"
  int esp1 = primera.indexOf(' '), esp2 = primera.indexOf(' ', esp1 + 1);
  String metodo = primera.substring(0, esp1);
  String ruta = esp2 > esp1 ? primera.substring(esp1 + 1, esp2) : "";
  size_t largo = 0;
  String tipo = "application/octet-stream";
  while (millis() < limite) {
    String h = leerLinea(c, limite);
    if (h.length() == 0) break;
    String minus = h; minus.toLowerCase();
    if (minus.startsWith("content-length:")) largo = h.substring(15).toInt();
    if (minus.startsWith("content-type:")) { tipo = h.substring(13); tipo.trim(); }
  }
  // Solo se reenvía a la API del lector, nada más.
  if (!ruta.startsWith("/api/lectores/") || largo > 400000) {
    responderRelevo(c, 400, "{\"error\":\"El relevo solo reenvía a /api/lectores.\"}");
    return;
  }
  uint8_t* cuerpo = nullptr;
  if (largo) {
    cuerpo = (uint8_t*)(psramFound() ? ps_malloc(largo) : malloc(largo));
    if (!cuerpo) { responderRelevo(c, 500, "{\"error\":\"Sin memoria.\"}"); return; }
    size_t leidos = 0;
    while (leidos < largo && millis() < limite + 20000) {
      int n = c.read(cuerpo + leidos, largo - leidos);
      if (n > 0) leidos += n; else delay(2);
    }
    if (leidos < largo) { free(cuerpo); responderRelevo(c, 400, "{\"error\":\"Llegó incompleto.\"}"); return; }
  }
  Respuesta r = pedir(metodo.c_str(), ruta, cuerpo, largo, tipo.c_str(), 30000);
  free(cuerpo);
  Serial.printf("[relevo] XIAO %s %s (%u bytes) -> servidor: HTTP %d\n", metodo.c_str(), ruta.c_str(), (unsigned)largo, r.codigo);
  responderRelevo(c, r.codigo > 0 ? r.codigo : 502,
                  r.cuerpo.length() ? r.cuerpo : String("{\"error\":\"El servidor no respondió por ") + NOMBRE_RED[red] + ".\"}");
}

// ------------------------------------------------------------
// Lista local y consultas
// ------------------------------------------------------------
int leerLista(const String& json) {
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
  return n;
}

// La última lista queda guardada en la memoria de la placa: si se reinicia
// sin internet, sigue sabiendo quién puede entrar.
void cargarListaGuardada() {
  String json = prefs.getString("lista", "");
  tamLista = leerLista(json);
  if (tamLista) Serial.printf("Lista guardada en la placa: %d tags\n", tamLista);
}

void descargarLista() {
  ultimaLista = millis();
  Respuesta r = pedir("GET", "/api/lectores/lista", nullptr, 0, nullptr, esperaServidor());
  if (r.codigo == 200) {
    tamLista = leerLista(r.cuerpo);
    if (r.cuerpo.length() < 3900 && r.cuerpo != prefs.getString("lista", "")) prefs.putString("lista", r.cuerpo);
    Serial.printf("Lista local: %d tags (por %s)\n", tamLista, NOMBRE_RED[red]);
  } else if (r.codigo > 0) {
    Serial.printf("No se pudo bajar la lista (HTTP %d)\n", r.codigo);
  }
}

// Pregunta al servidor qué hacer con el tag. false = no hubo respuesta.
// fotos = el servidor abrió una alerta nueva (si no, su alerta seguía pendiente).
bool preguntarAlServidor(const char* epc, String& resultado, String& nombre, String& motivo, bool& fotos) {
  String json = String("{\"epc\":\"") + epc + "\"}";
  Respuesta r = pedir("POST", "/api/lectores/accesos", (const uint8_t*)json.c_str(), json.length(),
                      "application/json", esperaServidor());
  if (r.codigo != 200) {
    if (r.codigo > 0) Serial.printf("Servidor: HTTP %d %s\n", r.codigo, r.cuerpo.c_str());
    return false;
  }
  resultado = campo(r.cuerpo, "resultado");
  nombre = campo(r.cuerpo, "nombre");
  motivo = campo(r.cuerpo, "motivo");
  fotos = r.cuerpo.indexOf("\"fotos\":true") >= 0;
  int i = r.cuerpo.indexOf("\"dentro\":");
  dentroServidor = i >= 0 ? r.cuerpo.substring(i + 9).toInt() : -1;
  return resultado.length() > 0;
}

// ------------------------------------------------------------
// Aviso a la ESP32-C6 del foco (y de ahí a la Alexa por Matter)
// ------------------------------------------------------------
// Los tres UART del ESP32 ya están ocupados (USB, módem y YRM): este serial
// de solo transmisión se hace a mano, bit por bit, a 9600 baudios.
portMUX_TYPE candadoBits = portMUX_INITIALIZER_UNLOCKED;

void enviarByteC6(uint8_t b) {
  const uint32_t bit = 1000000 / BAUDIOS_C6;   // 104 µs
  taskENTER_CRITICAL(&candadoBits);
  digitalWrite(AVISO_C6, LOW);                  // bit de inicio
  esp_rom_delay_us(bit);
  for (int i = 0; i < 8; i++) {
    digitalWrite(AVISO_C6, (b >> i) & 1);
    esp_rom_delay_us(bit);
  }
  digitalWrite(AVISO_C6, HIGH);                 // bit de parada
  esp_rom_delay_us(bit);
  taskEXIT_CRITICAL(&candadoBits);
}

// Una línea "TIPO|nombre|dentro": E entrada, S salida, N negado, A abierta
// desde el panel. dentro = cuántos quedan en el gimnasio (-1 = no se sabe).
void avisarC6(char tipo, const String& nombre, int dentro) {
  String linea = String(tipo) + "|" + nombre + "|" + dentro + "\n";
  for (size_t i = 0; i < linea.length(); i++) enviarByteC6(linea[i]);
  Serial.printf("         [C6] %s", linea.c_str());
}

// Corre aparte (en el núcleo 1, ver setup). Por WiFi el servidor tiene la consulta abierta hasta
// 15 s (responde en cuanto haya algo); por LTE/2G se pregunta cada 4 s sin
// esperar, para no tener el módem ocupado y que los tags pasen rápido.
void tareaComandos(void*) {
  for (;;) {
    if (!redLista) { vTaskDelay(pdMS_TO_TICKS(1000)); continue; }
    bool porModem = red != RED_WIFI;
    Respuesta r = pedir("GET", porModem ? "/api/lectores/comandos?espera=0" : "/api/lectores/comandos?espera=15",
                        nullptr, 0, nullptr, porModem ? ESPERA_MODEM_MS : 20000);
    if (r.codigo == 200) {
      String c = campo(r.cuerpo, "comando");
      strlcpy(nombreComando, campo(r.cuerpo, "nombre").c_str(), sizeof(nombreComando));
      comandoPendiente = c == "abrir" ? 1 : c == "negar" ? 2 : 0;
    } else if (r.codigo != 204) {
      vTaskDelay(pdMS_TO_TICKS(3000));   // sin red o servidor despertando: no saturarlo
    }
    vTaskDelay(pdMS_TO_TICKS(porModem ? 4000 : 100));
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
        avisarC6('E', lista[i].nombre, -1);
        abrirPuerta();
        senal(LED_VERDE, 1);
      } else {
        Serial.printf("NEGADO   %s: membresia vencida  [sin red]\n", lista[i].nombre);
        avisarC6('N', lista[i].nombre, -1);
        senal(LED_ROJO, 3);
      }
      return;
    }
  }
  Serial.printf("NEGADO   tag desconocido %s  [sin red]\n", epc);
  avisarC6('N', "", -1);
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
    avisarC6('E', nombre, dentroServidor);
    abrirPuerta();
    senal(LED_VERDE, 1);
  } else if (resultado == "SALIDA") {
    Serial.printf("SALIDA   %s  RSSI=%d\n", nombre.c_str(), rssi);
    avisarC6('S', nombre, dentroServidor);
    abrirPuerta();
    senal(LED_AZUL, 2);
  } else if (resultado == "NEGADO") {
    Serial.printf("NEGADO   %s %s (%s)\n", nombre.length() ? nombre.c_str() : epc, motivo.c_str(),
                  fotos ? "alerta nueva en el panel" : "su alerta sigue pendiente");
    if (fotos) avisarC6('N', nombre, -1);   // la misma alerta otra vez: la Alexa no lo repite
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
    avisarC6('A', nombreComando, -1);
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
  pinMode(AVISO_C6, OUTPUT);
  digitalWrite(AVISO_C6, HIGH);   // un serial en reposo está en alto

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

  candado = xSemaphoreCreateMutex();
  leerServidor();
  prefs.begin("lector", false);
  cargarListaGuardada();
  Serial.printf("Servidor: %s\n", SERVIDOR);
  int guardada = prefs.getInt("red", RED_WIFI);
  red = RED_WIFI;   // al arrancar no hay nada de otra red que apagar
  usarRed(guardada >= RED_WIFI && guardada <= RED_2G ? guardada : RED_WIFI);
  // En el núcleo 1, junto a loop(): por el módem, la librería HTTP espera la
  // respuesta sin soltar el procesador, y en el núcleo 0 eso dispara el
  // watchdog del sistema (task_wdt) y reinicia la placa.
  xTaskCreatePinnedToCore(tareaComandos, "comandos", 10240, nullptr, 1, nullptr, 1);
  Serial.println("Listo. Comandos: w / l / g (WiFi, LTE-M, 2G), r (estado), n (simular tag desconocido), + / - (potencia)");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'w') cambiarRed(RED_WIFI);
    if (c == 'l') cambiarRed(RED_LTE);
    if (c == 'g') cambiarRed(RED_2G);
    if (c == 'r') mostrarEstado();
    if (c == 'n') alLeerTag(EPC_SIMULADO, 0, true);
    if (c == '+' && potenciaDbm < 26) fijarPotencia(++potenciaDbm);
    if (c == '-' && potenciaDbm > 5)  fijarPotencia(--potenciaDbm);
  }
  atenderComando();
  if (red == RED_WIFI) {
    vigilarWifi();
  } else {
    atenderRelevo();
    vigilarModem();
  }
  if (millis() - ultimaLista > LISTA_CADA_MS) descargarLista();
  inventario();
  delay(150);
}
