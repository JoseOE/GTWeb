// Lector de acceso de GymTrack: LILYGO T-SIM7070G + lector UHF YRM1004.
//
// Cada tag que lee se lo pregunta a GTWeb (POST /api/lectores/accesos) y hace
// lo que el servidor responda:
//   ENTRADA  -> LED verde, 1 pitido y abre la puerta (servo)
//   SALIDA   -> LED azul, 2 pitidos y abre la puerta
//   NEGADO   -> LED rojo y 3 pitidos. Si la membresía venció, al socio le llega
//               un aviso al teléfono para renovar; al pagar, el servidor manda
//               "abrir" y la puerta se abre sola.
//   REGISTRO -> LED azul y un pitido largo: el panel estaba en "Registrar tag
//               nuevo" y capturó este tag
//   IGNORADO -> nada (pasó otra vez muy pronto)
// En paralelo, una tarea pregunta por los comandos del servidor (GET
// /api/lectores/comandos): "abrir" cuando el dueño da acceso desde una alerta,
// cuando el socio renovó desde la app o con "Simular entrada" en el panel.
//
// Internet, por WiFi o por datos móviles (se elige solo):
//   1. WiFi: las redes de secrets.h (hasta 3; solo 2.4 GHz, el ESP32 no ve 5 GHz).
//   2. Si no hay WiFi: datos móviles con el módem SIM7070G (LTE-M / NB-IoT y,
//      si no hay, 2G). El SIM7070G no tiene 4G "normal" ni 5G.
//   - Si estando en WiFi se cae más de 45 s, se pasa a datos móviles.
//   - Estando en datos móviles, cada 2 min busca las redes WiFi conocidas y,
//     si alguna está cerca, regresa a WiFi.
//   Sin internet decide con la lista de tags que descarga cada minuto.
//
// Comandos por el monitor serie (115200):
//   a  red automática (WiFi y si no, datos)   w  solo WiFi
//   l  solo LTE-M                             g  solo 2G
//   r  estado de la red    p  probar la puerta (servo)    n  simular un tag desconocido
//   +  subir la potencia 1 dBm      -  bajarla 1 dBm
//
// Placa: "ESP32 Wrover Module". Librerías: TinyGSM y ArduinoHttpClient.
// Conexiones en README.md (misma carpeta de arriba). Para datos móviles: SIM con
// datos y, de preferencia, una batería 18650 puesta (el módem pide picos de
// corriente que el USB solo a veces no da, sobre todo en 2G).
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
#include <esp_arduino_version.h>
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

// ============================================================
// SECCIÓN 1 · Pines, tiempos y variables globales
// ============================================================
#define RFID_RX         32   // llega del TXD del YRM
#define RFID_TX         33   // va al RXD del YRM
#define LED_VERDE       18   // entrada / puerta abierta
#define LED_AZUL        19   // salida, registro y "sin internet"
#define LED_ROJO        21   // negado
#define BUZZER          22   // buzzer activo (suena con HIGH)
#define SERVO           23   // señal del servo de la puerta
#define MODEM_RX        26   // llega del TX del módem (dentro de la placa)
#define MODEM_TX        27
#define MODEM_PWR       4    // pulso para encender el módem

// La puerta: ángulos del servo y cuánto se queda abierta.
const int      SERVO_CERRADO    = 0;       // grados
const int      SERVO_ABIERTO    = 90;
const uint32_t PUERTA_ABIERTA_MS = 3000;

const uint32_t REARME_MS        = 3000;    // el tag tiene que alejarse esto para contar otra vez
const uint32_t SENAL_MS         = 1500;
const uint32_t LISTA_CADA_MS    = 60000;
const uint16_t ESPERA_WIFI_MS   = 4000;    // más que esto y se decide con la lista local
const uint16_t ESPERA_MODEM_MS  = 12000;   // por datos móviles cada petición tarda más (TLS por el módem)
const uint32_t WIFI_CAIDO_MS    = 45000;   // en automático: tanto sin WiFi y se pasa a datos
const uint32_t BUSCAR_WIFI_MS   = 120000;  // en datos: cada cuánto se buscan las redes WiFi
const char*    EPC_SIMULADO     = "E20000000000000000000001";

enum Red { RED_WIFI = 0, RED_LTE = 1, RED_2G = 2, RED_NINGUNA = 3 };
const char* NOMBRE_RED[] = {"WiFi", "LTE-M", "2G", "sin red"};
enum Modo { MODO_AUTO = 0, MODO_WIFI = 1, MODO_LTE = 2, MODO_2G = 3 };
const char* NOMBRE_MODO[] = {"automático (WiFi y si no, datos)", "solo WiFi", "solo LTE-M", "solo 2G"};

HardwareSerial yrm(2);
HardwareSerial modemSerial(1);
TinyGsm modem(modemSerial);
TinyGsmClientSecure modemSeguro(modem, 0);
TinyGsmClient modemSimple(modem, 1);
SemaphoreHandle_t candado;          // el módem atiende una petición a la vez
Preferences prefs;
WiFiMulti redes;
int potenciaDbm = 15;

int modo = MODO_AUTO;
volatile int red = RED_NINGUNA;     // por dónde se está saliendo a internet
volatile bool redLista = false;     // false mientras se conecta
// Sobrevive a ESP.restart(): en automático, si el WiFi se cayó, el siguiente
// arranque va directo a datos móviles en lugar de esperar al WiFi otra vez.
RTC_DATA_ATTR bool saltarWifi = false;
uint32_t wifiCaidoDesde = 0;        // cuándo se cayó el WiFi (0 = conectado)
uint32_t ultimoIntentoRed = 0;
uint32_t ultimaBusquedaWifi = 0;    // en datos: la última vez que se buscaron redes WiFi
int fallosDatos = 0;
bool modemPrendido = false;

String hostServidor;
uint16_t puertoServidor = 443;
bool servidorSeguro = true;

// Lo que deja la tarea de comandos para que loop() lo haga (0 = nada, 1 = abrir, 2 = negar).
volatile int comandoPendiente = 0;
char nombreComando[48] = "";
// Para saber si la tarea de comandos sigue hablando con el servidor.
volatile uint32_t ultimoSondeoOk = 0;
const uint32_t SIN_SONDEO_REINICIA_MS = 180000;

// Tags vistos hace poco, para no mandar el mismo 4 veces por segundo.
struct Visto { char epc[65]; uint32_t ultima; };
Visto vistos[8];

// Respuesta del servidor (codigo < 0 = no hubo respuesta).
struct Respuesta { int codigo; String cuerpo; };

// Lista para decidir sin internet.
struct Permitido { char epc[65]; char nombre[32]; bool permitido; };
Permitido lista[60];
int tamLista = 0;
uint32_t ultimaLista = 0;

// ============================================================
// SECCIÓN 2 · Comunicación con el lector UHF YRM1004 (UART2, 115200)
// Tramas del chip M100: BB | tipo | cmd | largo (2) | datos | checksum | 7E
// ============================================================
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

// ============================================================
// SECCIÓN 3 · Señales: LEDs, buzzer y la puerta (servo)
// El servo se mueve con PWM de 50 Hz (LEDC, sin librería extra): 500 µs = 0°
// y 2500 µs = 180°. La puerta se abre y se cierra sola a los 3 s, sin
// detener la lectura de tags (vigilarPuerta en loop).
// ============================================================
#define SERVO_CANAL 0      // solo para el core 2.x del ESP32
#define SERVO_BITS  14

void escribirServo(int grados) {
  uint32_t us = 500 + (uint32_t)constrain(grados, 0, 180) * 2000 / 180;
  uint32_t duty = us * ((1UL << SERVO_BITS) - 1) / 20000;
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(SERVO, duty);
#else
  ledcWrite(SERVO_CANAL, duty);
#endif
}

void iniciarServo() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(SERVO, 50, SERVO_BITS);
#else
  ledcSetup(SERVO_CANAL, 50, SERVO_BITS);
  ledcAttachPin(SERVO, SERVO_CANAL);
#endif
  escribirServo(SERVO_CERRADO);
}

uint32_t cerrarPuertaEn = 0;    // 0 = cerrada

void abrirPuerta() {
  if (!cerrarPuertaEn) Serial.println("         [puerta] abierta");
  escribirServo(SERVO_ABIERTO);
  cerrarPuertaEn = millis() + PUERTA_ABIERTA_MS;
}

void vigilarPuerta() {
  if (cerrarPuertaEn && (int32_t)(millis() - cerrarPuertaEn) >= 0) {
    escribirServo(SERVO_CERRADO);
    cerrarPuertaEn = 0;
    Serial.println("         [puerta] cerrada");
  }
}

void pitido(int ms) {
  digitalWrite(BUZZER, HIGH); delay(ms); digitalWrite(BUZZER, LOW);
}

// Prende el LED con sus pitidos (cada uno de msPitido) y lo deja encendido SENAL_MS.
void senalCon(int led, int pitidos, int msPitido) {
  digitalWrite(led, HIGH);
  uint32_t inicio = millis();
  for (int i = 0; i < pitidos; i++) { pitido(msPitido); delay(80); }
  while (millis() - inicio < SENAL_MS) { vigilarPuerta(); delay(10); }
  digitalWrite(led, LOW);
}

void senal(int led, int pitidos) {
  senalCon(led, pitidos, 80);
}

// Al arrancar: cada LED, un pitido y la puerta, para revisar las conexiones.
void probarSalidas() {
  int leds[] = {LED_VERDE, LED_AZUL, LED_ROJO};
  for (int led : leds) { digitalWrite(led, HIGH); delay(250); digitalWrite(led, LOW); }
  pitido(60);
}

// Sin internet: un destello azul cada 3 s (decide con la lista local).
uint32_t ultimoDestello = 0;

void avisarSinInternet() {
  bool conInternet = redLista && (red == RED_WIFI ? WiFi.status() == WL_CONNECTED : red != RED_NINGUNA);
  if (conInternet || millis() - ultimoDestello < 3000) return;
  ultimoDestello = millis();
  digitalWrite(LED_AZUL, HIGH); delay(40); digitalWrite(LED_AZUL, LOW);
}

// ============================================================
// SECCIÓN 4 · Peticiones HTTPS al servidor GTWeb
// Por WiFi usa HTTPClient con TLS; por datos móviles, el cliente TLS del
// módem. Como el módem es uno solo, la petición toma primero el semáforo
// "candado" (mutex de FreeRTOS) para no chocar con la tarea de comandos.
// ============================================================
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
  if (!redLista || red == RED_NINGUNA) return r;

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

  // Datos móviles: el módem es uno solo, así que se toma por turnos.
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

// ============================================================
// SECCIÓN 5 · WiFi (las 3 redes de secrets.h)
// Primero la red principal (WIFI_SSID) aunque otra conocida tenga mejor
// señal; si no está, WiFiMulti se conecta a la conocida con mejor señal.
// ============================================================
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

void avisarConexionWifi() {
  Serial.printf("WiFi: conectado a \"%s\", IP %s, señal %d dBm\n",
                WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(), WiFi.RSSI());
}

// true si conectó. Tarda hasta ~35 s si no hay ninguna red.
bool conectarWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  agregarRedes();
  Serial.printf("Probando primero la red principal \"%s\"...\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  for (uint32_t t = millis(); WiFi.status() != WL_CONNECTED && millis() - t < 10000;) delay(200);
  if (WiFi.status() != WL_CONNECTED) {
    WiFi.disconnect();
    Serial.println("No está cerca: buscando las otras redes conocidas...");
    for (int i = 0; i < 3 && redes.run(8000) != WL_CONNECTED; i++) Serial.println("  ...otra vez");
  }
  if (WiFi.status() == WL_CONNECTED) {
    avisarConexionWifi();
    return true;
  }
  Serial.printf("Sin WiFi: %s.\n", motivoWifi(WiFi.status()));
  return false;
}

// ¿Hay alguna red conocida cerca? (para regresar de datos móviles a WiFi)
bool hayWifiConocida() {
  const char* nombres[] = {WIFI_SSID, WIFI_SSID_2, WIFI_SSID_3};
  int n = WiFi.scanNetworks();
  bool hay = false;
  for (int i = 0; i < n && !hay; i++) {
    for (const char* nombre : nombres) {
      if (strlen(nombre) && WiFi.SSID(i) == nombre && WiFi.RSSI(i) > -80) {
        Serial.printf("Red WiFi \"%s\" cerca (%d dBm)\n", nombre, WiFi.RSSI(i));
        hay = true;
        break;
      }
    }
  }
  WiFi.scanDelete();
  return hay;
}

// ============================================================
// SECCIÓN 6 · Datos móviles: LTE-M y 2G con el módem SIM7070G
// prenderModem() prende el módem (con reintentos del pulso PWRKEY) y revisa
// la SIM; conectarDatos() elige el modo:
//   - LTE-M: setNetworkMode(38) = solo LTE y setPreferredMode(3) = Cat-M o NB-IoT.
//   - 2G:    setNetworkMode(13) = solo GSM/GPRS.
// Espera la red hasta 90 s y abre los datos con el APN. Quien llame debe
// tener el candado.
// ============================================================
// El pulso en PWRKEY prende el módem si está apagado, pero lo APAGA si ya estaba
// prendido (p. ej. si el ESP32 se reinició y el módem no).
void pulsoModem() {
  pinMode(MODEM_PWR, OUTPUT);
  digitalWrite(MODEM_PWR, LOW);  delay(100);
  digitalWrite(MODEM_PWR, HIGH); delay(1000);
  digitalWrite(MODEM_PWR, LOW);
}

bool prenderModem() {
  if (modemPrendido) return true;
  modemSerial.begin(115200, SERIAL_8N1, MODEM_RX, MODEM_TX);
  // Primero se le pregunta un buen rato: si ya está prendido, no se le da el pulso.
  bool responde = modem.testAT(4000);
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
    Serial.println("La SIM no está lista: revisa que esté bien puesta, con datos y sin PIN.");
    return false;
  }
  modemPrendido = true;
  return true;
}

bool conectarDatos(int cual) {
  Serial.printf("Datos móviles por %s...\n", NOMBRE_RED[cual]);
  if (!prenderModem()) return false;
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
  if (!modemPrendido) return;
  modem.gprsDisconnect();
  modem.poweroff();
  modemPrendido = false;
}

// ============================================================
// SECCIÓN 7 · Elegir por dónde salir a internet
// En automático: WiFi primero; si no hay, LTE-M; si tampoco, 2G. Cambiar de
// red reinicia la placa (al volver de datos a WiFi, el WiFi quedaba conectado
// pero sin llegar al servidor), así que cada cambio arranca limpio.
// ============================================================
void conectarInternet() {
  redLista = false;
  bool ok = false;
  bool probarWifi = modo == MODO_WIFI || (modo == MODO_AUTO && !saltarWifi);
  if (saltarWifi && modo == MODO_AUTO) Serial.println("El WiFi se había caído: voy directo a datos móviles.");
  saltarWifi = false;

  if (probarWifi && conectarWifi()) {
    red = RED_WIFI;
    ok = true;
  }
  if (!ok && modo != MODO_WIFI) {
    WiFi.disconnect(true);
    WiFi.mode(WIFI_STA);           // se queda prendido solo para buscar redes después
    xSemaphoreTake(candado, portMAX_DELAY);
    if (modo == MODO_AUTO || modo == MODO_LTE) {
      ok = conectarDatos(RED_LTE);
      if (ok) red = RED_LTE;
    }
    if (!ok && (modo == MODO_AUTO || modo == MODO_2G)) {
      ok = conectarDatos(RED_2G);
      if (ok) red = RED_2G;
    }
    xSemaphoreGive(candado);
  }
  if (!ok) {
    red = modo == MODO_WIFI ? RED_WIFI : RED_NINGUNA;   // en solo WiFi se sigue reintentando
    Serial.println("Sin internet por ahora: decide con la lista local y lo vuelve a intentar.");
  }
  ultimoIntentoRed = millis();
  ultimaBusquedaWifi = millis();
  redLista = true;
  if (ok) descargarLista();
}

void cambiarModo(int nuevo) {
  Serial.printf("\nModo de red: %s. Se guarda y se reinicia la placa...\n", NOMBRE_MODO[nuevo]);
  prefs.putInt("modo", nuevo);
  redLista = false;
  xSemaphoreTake(candado, portMAX_DELAY);
  apagarModem();
  delay(300);
  ESP.restart();
}

// Cambio automático de red, desde loop().
void vigilarRed() {
  uint32_t ahora = millis();
  if (red == RED_WIFI) {
    if (WiFi.status() == WL_CONNECTED) {
      if (wifiCaidoDesde) { wifiCaidoDesde = 0; avisarConexionWifi(); descargarLista(); }
      return;
    }
    if (!wifiCaidoDesde) {
      wifiCaidoDesde = ahora;
      Serial.println("Se perdió el WiFi: decide con la lista local mientras vuelve.");
    }
    if (modo == MODO_AUTO && ahora - wifiCaidoDesde > WIFI_CAIDO_MS) {
      Serial.println("El WiFi no regresa: paso a datos móviles (se reinicia la placa).");
      saltarWifi = true;
      delay(200);
      ESP.restart();
    }
    if (ahora - ultimoIntentoRed > 10000) {
      ultimoIntentoRed = ahora;
      redes.run(5000);
    }
    return;
  }

  // En datos móviles (o sin red): ¿ya hay WiFi conocida cerca? Entonces regresa a WiFi.
  if (modo == MODO_AUTO && ahora - ultimaBusquedaWifi > BUSCAR_WIFI_MS) {
    ultimaBusquedaWifi = ahora;
    if (hayWifiConocida()) {
      Serial.println("Regreso a WiFi (se reinicia la placa).");
      redLista = false;
      xSemaphoreTake(candado, portMAX_DELAY);
      apagarModem();
      delay(200);
      ESP.restart();
    }
  }
  if (red == RED_NINGUNA) {
    // Nada funcionó al arrancar: se vuelve a intentar todo cada 2 min.
    if (ahora - ultimoIntentoRed > 120000) { ultimoIntentoRed = ahora; ESP.restart(); }
    return;
  }
  // Datos móviles caídos: otro intento cada 30 s; a los 3 fallos se reinicia el módem.
  if (ahora - ultimoIntentoRed < 30000) return;
  ultimoIntentoRed = ahora;
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
      if (conectarDatos(red)) fallosDatos = 0;
    }
  }
  xSemaphoreGive(candado);
}

void mostrarEstado() {
  Serial.printf("\nModo: %s · saliendo por: %s\n", NOMBRE_MODO[modo], NOMBRE_RED[red]);
  if (red == RED_WIFI) {
    if (WiFi.status() == WL_CONNECTED) {
      Serial.printf("  WiFi \"%s\", IP %s, señal %d dBm\n", WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(), WiFi.RSSI());
    } else {
      Serial.printf("  Sin WiFi: %s\n", motivoWifi(WiFi.status()));
    }
  } else if (red != RED_NINGUNA && xSemaphoreTake(candado, pdMS_TO_TICKS(15000)) == pdTRUE) {
    String info;
    modem.sendAT(GF("+CPSI?"));          // tipo de red real: GSM, LTE CAT-M1, LTE NB-IOT
    modem.waitResponse(2000L, info);
    info.trim();
    Serial.printf("  Operador: %s, señal %d/31, datos: %s, IP %s\n", modem.getOperator().c_str(),
                  modem.getSignalQuality(), modem.isGprsConnected() ? "sí" : "no", modem.getLocalIP().c_str());
    Serial.printf("  %s\n", info.c_str());
    xSemaphoreGive(candado);
  }
  Serial.printf("  Servidor: %s · lista local: %d tags\n", SERVIDOR, tamLista);
  Serial.printf("  Comandos del panel: última consulta buena hace %lu s · memoria libre %u\n",
                (millis() - ultimoSondeoOk) / 1000, ESP.getFreeHeap());
}

// ============================================================
// SECCIÓN 8 · Lista local y consulta de cada tag
// Cada minuto se descarga la lista de tags permitidos (y se guarda en la
// memoria para decidir sin internet). Por cada tag leído se manda {"epc"}.
// ============================================================
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
// alertaNueva = el servidor abrió una alerta nueva (si no, la anterior seguía pendiente).
bool preguntarAlServidor(const char* epc, String& resultado, String& nombre, String& motivo, bool& alertaNueva) {
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
  alertaNueva = r.cuerpo.indexOf("\"fotos\":true") >= 0;
  return resultado.length() > 0;
}

// ============================================================
// SECCIÓN 9 · Tarea de FreeRTOS: comandos del servidor
// Corre aparte de loop() y pregunta si hay que abrir: el dueño dio acceso
// desde una alerta, el socio renovó desde la app o se simuló una entrada en
// el panel. No mueve LEDs ni la puerta: deja el comando en comandoPendiente
// y loop() lo ejecuta (atenderComando), así nunca se pisan los pines.
// Por WiFi el servidor tiene la consulta abierta hasta 15 s; por datos se
// pregunta cada 4 s sin esperar, para no tener el módem ocupado.
// ============================================================
void tareaComandos(void*) {
  for (;;) {
    if (!redLista || red == RED_NINGUNA) { vTaskDelay(pdMS_TO_TICKS(1000)); continue; }
    bool porModem = red != RED_WIFI;
    Respuesta r = pedir("GET", porModem ? "/api/lectores/comandos?espera=0" : "/api/lectores/comandos?espera=15",
                        nullptr, 0, nullptr, porModem ? ESPERA_MODEM_MS : 20000);
    if (r.codigo == 200 || r.codigo == 204) {
      if (millis() - ultimoSondeoOk > 30000) Serial.println("[comandos] el servidor responde otra vez");
      ultimoSondeoOk = millis();
    } else {
      Serial.printf("[comandos] no se pudo preguntar al servidor (código %d, memoria libre %u)\n",
                    r.codigo, ESP.getFreeHeap());
    }
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

void atenderComando() {
  int c = comandoPendiente;
  if (!c) return;
  comandoPendiente = 0;
  if (c == 1) {
    Serial.printf("ABRIR    %s (desde el servidor)\n", nombreComando);
    abrirPuerta();
    senal(LED_VERDE, 1);
  } else {
    Serial.printf("NEGAR    %s (el dueño negó el acceso)\n", nombreComando);
    senal(LED_ROJO, 1);
  }
}

// ============================================================
// SECCIÓN 10 · Lógica de la puerta
// Un tag se descarta si sigue frente al lector (rearme de 3 s). Si no, se
// pregunta al servidor y se actúa según la respuesta. Sin respuesta, decide
// con la lista local (decidirSinRed).
// ============================================================
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
        Serial.printf("NEGADO   %s: membresía vencida  [sin red]\n", lista[i].nombre);
        senal(LED_ROJO, 3);
      }
      return;
    }
  }
  Serial.printf("NEGADO   tag desconocido %s  [sin red]\n", epc);
  senal(LED_ROJO, 3);
}

// forzar = sin esperar a que el tag se aleje (el comando 'n' del monitor).
void alLeerTag(const char* epc, int8_t rssi, bool forzar) {
  if (seguiaCerca(epc) && !forzar) return;
  String resultado, nombre, motivo;
  bool alertaNueva = false;
  if (!preguntarAlServidor(epc, resultado, nombre, motivo, alertaNueva)) {
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
    Serial.printf("NEGADO   %s %s (%s)\n", nombre.length() ? nombre.c_str() : epc, motivo.c_str(),
                  alertaNueva ? "alerta nueva en el panel" : "su alerta sigue pendiente");
    senal(LED_ROJO, 3);
  } else if (resultado == "REGISTRO") {
    Serial.printf("REGISTRO tag %s capturado%s%s: asígnalo en el panel\n", epc,
                  nombre.length() ? ", ahora es de " : "", nombre.c_str());
    senalCon(LED_AZUL, 1, 400);
  } else {
    Serial.printf("ignorado %s: %s\n", nombre.c_str(), motivo.c_str());
  }
}

// ============================================================
// SECCIÓN 11 · Lectura continua de tags (inventario del YRM)
// En cada vuelta de loop() se pide un inventario (comando 0x22) y se leen
// las tramas de los tags: RSSI, PC y el EPC, que se pasa a texto hexadecimal.
// ============================================================
void inventario() {
  vaciarEntrada();
  enviar(0x22, nullptr, 0);
  uint8_t tipo, cmd, datos[64];
  while (true) {
    int n = leerTrama(tipo, cmd, datos, sizeof(datos), 150);
    if (n == -1) break;
    if (n == -2) { Serial.println("Trama con error (revisa GND o alimentación)"); continue; }
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

// ============================================================
// SECCIÓN 12 · Arranque (setup) y ciclo principal (loop)
// ============================================================
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Lector de acceso GymTrack (T-SIM7070G + YRM1004) ===");

  pinMode(LED_VERDE, OUTPUT); pinMode(LED_AZUL, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);  pinMode(BUZZER, OUTPUT);
  iniciarServo();
  probarSalidas();

  // El YRM puede tardar en encender más que la LILYGO: se reintenta unos segundos.
  yrm.begin(115200, SERIAL_8N1, RFID_RX, RFID_TX);
  bool yrmListo = false;
  for (int i = 0; i < 8 && !yrmListo; i++) { delay(500); yrmListo = pedirVersion(); }
  if (!yrmListo) {
    Serial.println("El YRM NO responde: revisa GND común, TX/RX cruzados, EN y los 5 V.");
    digitalWrite(LED_ROJO, HIGH);
  } else {
    fijarPotencia(potenciaDbm);
  }

  candado = xSemaphoreCreateMutex();
  leerServidor();
  prefs.begin("lector", false);
  cargarListaGuardada();
  modo = prefs.getInt("modo", MODO_AUTO);
  if (modo < MODO_AUTO || modo > MODO_2G) modo = MODO_AUTO;
  Serial.printf("Servidor: %s · modo de red: %s\n", SERVIDOR, NOMBRE_MODO[modo]);
  digitalWrite(LED_AZUL, HIGH);          // azul fijo mientras conecta
  conectarInternet();
  digitalWrite(LED_AZUL, LOW);
  // En el núcleo 1, junto a loop(): por el módem, la librería HTTP espera la
  // respuesta sin soltar el procesador, y en el núcleo 0 eso dispara el
  // watchdog del sistema (task_wdt) y reinicia la placa.
  xTaskCreatePinnedToCore(tareaComandos, "comandos", 10240, nullptr, 1, nullptr, 1);
  Serial.println("Listo. Comandos: a / w / l / g (red), r (estado), p (probar puerta), n (tag desconocido), + / - (potencia)");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'a') cambiarModo(MODO_AUTO);
    if (c == 'w') cambiarModo(MODO_WIFI);
    if (c == 'l') cambiarModo(MODO_LTE);
    if (c == 'g') cambiarModo(MODO_2G);
    if (c == 'r') mostrarEstado();
    if (c == 'p') { Serial.println("PRUEBA   puerta"); abrirPuerta(); senal(LED_VERDE, 1); }
    if (c == 'n') alLeerTag(EPC_SIMULADO, 0, true);
    if (c == '+' && potenciaDbm < 26) fijarPotencia(++potenciaDbm);
    if (c == '-' && potenciaDbm > 5)  fijarPotencia(--potenciaDbm);
  }
  atenderComando();
  vigilarPuerta();
  // Con WiFi pero sin poder preguntar por los comandos tanto tiempo: se
  // reinicia la placa (así se recupera si se quedó sin memoria para el https).
  if (redLista && red == RED_WIFI && WiFi.status() == WL_CONNECTED &&
      millis() > SIN_SONDEO_REINICIA_MS && millis() - ultimoSondeoOk > SIN_SONDEO_REINICIA_MS) {
    Serial.println("[comandos] 3 min sin poder preguntar al servidor: reinicio la placa");
    delay(200);
    ESP.restart();
  }
  vigilarRed();
  avisarSinInternet();
  if (red != RED_NINGUNA && millis() - ultimaLista > LISTA_CADA_MS) descargarLista();
  inventario();
  delay(150);
}
