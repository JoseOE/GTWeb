// Comandos de voz del lector: XIAO ESP32-S3 Sense + modelo de Edge Impulse.
//
// Mientras se mantiene presionado el botón, la XIAO graba con su micrófono
// (hasta 2 s). Al soltarlo, el modelo (modelo_voz.h) decide qué se dijo:
//   "entrada" -> prende el LED        "salida" -> apaga el LED
//   otra cosa o poca seguridad -> 3 parpadeos rápidos y el LED se queda como estaba
// Cada comando reconocido se manda a GTWeb (POST /api/lectores/voz): aparece en
// el historial de la puerta y el panel muestra si el LED está prendido.
//
// Internet: las redes de secrets.h, o la red propia de la LILYGO cuando ella usa
// LTE/2G (LECTOR_AP_SSID); en ese caso le manda el comando a la LILYGO y ella lo
// reenvía al servidor por el módem.
//
// Conexión: botón entre D2 y GND (sin resistencia: se usa la interna).
// LED: el naranja de la placa y, si quieres uno más visible, un LED en D3
// (con resistencia de 220 Ω a GND).
//
// Monitor serie (115200): muestra lo que oyó el modelo con el porcentaje de cada clase.
// Placa: "XIAO_ESP32S3". En Herramientas, PSRAM: "OPI PSRAM".
// Antes de subirlo: el secrets.h (el mismo que la LILYGO; ya está copiado) y el
// nombre de tu librería de Edge Impulse en modelo_voz.h.

#include <ESP_I2S.h>
#include <Preferences.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include "secrets.h"
#include "modelo_voz.h"

#if !defined(EI_CLASSIFIER_SENSOR) || EI_CLASSIFIER_SENSOR != EI_CLASSIFIER_SENSOR_MICROPHONE
#warning "La librería de modelo_voz.h no es un modelo de audio: cambia el include por el de tu modelo de voz."
#endif

// Lo opcional de secrets.h: un secrets.h viejo, sin esto, también compila.
#ifndef WIFI_SSID_2
#define WIFI_SSID_2 ""
#define WIFI_PASS_2 ""
#endif
#ifndef WIFI_SSID_3
#define WIFI_SSID_3 ""
#define WIFI_PASS_3 ""
#endif
#ifndef LECTOR_AP_SSID
#define LECTOR_AP_SSID "GymTrack-Lector"
#define LECTOR_AP_PASS "gymtrack2026"
#endif
#define IP_LECTOR "http://192.168.4.1"   // la LILYGO en su propia red

#define BOTON          D2     // a GND al presionar
#define LED_PLACA      21     // LED naranja de la placa; enciende en LOW
#define LED_EXTRA      D3     // LED opcional; enciende en HIGH
#define MIC_CLK        42     // micrófono PDM de la placa Sense
#define MIC_DATOS      41
#define FRECUENCIA     16000
#define MAX_MUESTRAS   (FRECUENCIA * 2)

I2SClass mic;
Preferences prefs;
WiFiMulti redes;
uint32_t ultimoIntentoWifi = 0;

int16_t* grabacion = nullptr;   // lo que se grabó con el botón (PSRAM)
int16_t* ventana = nullptr;     // el pedazo que ve el modelo
size_t muestras = 0;
bool ledEncendido = false;      // sobrevive a los reinicios

// ------------------------------------------------------------
// LED
// ------------------------------------------------------------
void ponerLed(bool encendido) {
  ledEncendido = encendido;
  digitalWrite(LED_PLACA, encendido ? LOW : HIGH);
  digitalWrite(LED_EXTRA, encendido ? HIGH : LOW);
}

// "No entendí": parpadea y regresa como estaba.
void parpadeoNoEntendi() {
  bool antes = ledEncendido;
  for (int i = 0; i < 3; i++) { ponerLed(!antes); delay(120); ponerLed(antes); delay(120); }
}

// ------------------------------------------------------------
// Red
// ------------------------------------------------------------
// Conectada a la red de la LILYGO (ella usa LTE/2G): todo va a la LILYGO.
String urlBase() {
  return WiFi.SSID() == LECTOR_AP_SSID ? String(IP_LECTOR) : String(SERVIDOR);
}

String campo(const String& json, const char* nombre) {
  String clave = String("\"") + nombre + "\":\"";
  int i = json.indexOf(clave);
  if (i < 0) return "";
  i += clave.length();
  int f = json.indexOf('"', i);
  return f < 0 ? "" : json.substring(i, f);
}

// TODO versión final: fijar el certificado raíz de Render en lugar de setInsecure().
void avisarServidor(const char* comando, float confianza) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("         sin WiFi: el panel no se entera de este comando");
    return;
  }
  WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
  HTTPClient http;
  String url = urlBase() + "/api/lectores/voz";
  if (!(url.startsWith("https") ? http.begin(seguro, url) : http.begin(simple, url))) return;
  http.setTimeout(url.startsWith(IP_LECTOR) ? 30000 : 8000);   // por el módem tarda más
  http.addHeader("X-Lector-Key", LECTOR_LLAVE);
  http.addHeader("Content-Type", "application/json");
  int code = http.POST(String("{\"comando\":\"") + comando + "\",\"confianza\":" + String(confianza, 3) + "}");
  String respuesta = code > 0 ? http.getString() : "";
  http.end();
  Serial.printf("         panel: %s%s\n", code == 200 ? ("LED " + campo(respuesta, "led")).c_str() : ("HTTP " + String(code)).c_str(),
                url.startsWith(IP_LECTOR) ? " (por la LILYGO)" : "");
}

// ------------------------------------------------------------
// Audio y modelo
// ------------------------------------------------------------
// Graba mientras el botón siga presionado (o hasta 2 s).
void grabar() {
  int16_t basura[256];
  mic.readBytes((char*)basura, sizeof(basura));   // lo primero trae basura
  muestras = 0;
  while (digitalRead(BOTON) == LOW && muestras < MAX_MUESTRAS) {
    size_t pedir = min((size_t)512, MAX_MUESTRAS - muestras);
    muestras += mic.readBytes((char*)(grabacion + muestras), pedir * 2) / 2;
  }
  while (digitalRead(BOTON) == LOW) delay(10);    // si llegó a 2 s, esperar a que suelte
}

// El modelo ve EI_CLASSIFIER_RAW_SAMPLE_COUNT muestras (normalmente 1 s). Si se
// grabó más, se toma el pedazo con más volumen (donde está la palabra); si se
// grabó menos, se centra y se rellena con silencio.
void armarVentana() {
  const size_t n = EI_CLASSIFIER_RAW_SAMPLE_COUNT;
  memset(ventana, 0, n * sizeof(int16_t));
  if (muestras <= n) {
    memcpy(ventana + (n - muestras) / 2, grabacion, muestras * sizeof(int16_t));
    return;
  }
  size_t mejor = 0;
  uint64_t mayor = 0;
  for (size_t inicio = 0; inicio + n <= muestras; inicio += FRECUENCIA / 20) {
    uint64_t energia = 0;
    for (size_t i = inicio; i < inicio + n; i += 4) energia += (int32_t)grabacion[i] * grabacion[i];
    if (energia > mayor) { mayor = energia; mejor = inicio; }
  }
  memcpy(ventana, grabacion + mejor, n * sizeof(int16_t));
}

static int datosAudio(size_t offset, size_t length, float* out) {
  ei::numpy::int16_to_float(ventana + offset, out, length);
  return 0;
}

// ¿Qué se dijo? false = no se pudo correr el modelo.
bool clasificar(String& etiqueta, float& valor) {
  armarVentana();
  ei::signal_t senal;
  senal.total_length = EI_CLASSIFIER_RAW_SAMPLE_COUNT;
  senal.get_data = &datosAudio;
  ei_impulse_result_t resultado = {0};
  if (run_classifier(&senal, &resultado, false) != EI_IMPULSE_OK) return false;
  valor = 0;
  Serial.print("         modelo:");
  for (size_t i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
    Serial.printf(" %s %.0f%%", resultado.classification[i].label, resultado.classification[i].value * 100);
    if (resultado.classification[i].value > valor) {
      valor = resultado.classification[i].value;
      etiqueta = resultado.classification[i].label;
    }
  }
  Serial.println();
  return true;
}

void atenderBoton() {
  grabar();
  float segundos = muestras / (float)FRECUENCIA;
  if (muestras < FRECUENCIA / 4) {
    Serial.println("Muy corta (menos de 0.25 s): mantén el botón mientras hablas.");
    return;
  }
  Serial.printf("VOZ      %.1f s grabados\n", segundos);
  String etiqueta;
  float valor = 0;
  if (!clasificar(etiqueta, valor)) {
    Serial.println("         no se pudo correr el modelo");
    return;
  }
  if (valor >= UMBRAL_VOZ && etiqueta.equalsIgnoreCase(CLASE_ENTRADA)) {
    Serial.printf("         \"%s\" (%.0f %%): LED encendido\n", CLASE_ENTRADA, valor * 100);
    ponerLed(true);
    prefs.putBool("led", true);
    avisarServidor(CLASE_ENTRADA, valor);
  } else if (valor >= UMBRAL_VOZ && etiqueta.equalsIgnoreCase(CLASE_SALIDA)) {
    Serial.printf("         \"%s\" (%.0f %%): LED apagado\n", CLASE_SALIDA, valor * 100);
    ponerLed(false);
    prefs.putBool("led", false);
    avisarServidor(CLASE_SALIDA, valor);
  } else {
    Serial.printf("         no entendí (%s %.0f %%)\n", etiqueta.c_str(), valor * 100);
    parpadeoNoEntendi();
  }
}

// ------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Comandos de voz del lector (XIAO ESP32-S3 Sense) ===");
  pinMode(BOTON, INPUT_PULLUP);
  pinMode(LED_PLACA, OUTPUT);
  pinMode(LED_EXTRA, OUTPUT);
  prefs.begin("voz", false);
  ponerLed(prefs.getBool("led", false));

  grabacion = (int16_t*)ps_malloc(MAX_MUESTRAS * sizeof(int16_t));
  ventana = (int16_t*)ps_malloc(EI_CLASSIFIER_RAW_SAMPLE_COUNT * sizeof(int16_t));
  mic.setPinsPdmRx(MIC_CLK, MIC_DATOS);
  if (!grabacion || !ventana || !mic.begin(I2S_MODE_PDM_RX, FRECUENCIA, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO)) {
    Serial.println("No se pudo iniciar el micrófono (¿PSRAM en OPI PSRAM?).");
  }
  Serial.printf("Modelo: %d clases, %d muestras por ventana (%.1f s)\n", EI_CLASSIFIER_LABEL_COUNT,
                EI_CLASSIFIER_RAW_SAMPLE_COUNT, EI_CLASSIFIER_RAW_SAMPLE_COUNT / (float)FRECUENCIA);

  // Las mismas redes que la LILYGO, más su red propia (cuando usa LTE/2G).
  WiFi.mode(WIFI_STA);
  const char* nombres[] = {WIFI_SSID, WIFI_SSID_2, WIFI_SSID_3, LECTOR_AP_SSID};
  const char* claves[]  = {WIFI_PASS, WIFI_PASS_2, WIFI_PASS_3, LECTOR_AP_PASS};
  for (int i = 0; i < 4; i++) {
    if (strlen(nombres[i])) redes.addAP(nombres[i], claves[i]);
  }
  Serial.println("Buscando una red conocida...");
  for (int i = 0; i < 3 && redes.run(8000) != WL_CONNECTED; i++) Serial.println("  ...otra vez");
  ultimoIntentoWifi = millis();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("WiFi: conectado a \"%s\"%s\n", WiFi.SSID().c_str(),
                  urlBase() == IP_LECTOR ? " (la red de la LILYGO: todo va por su módem)" : "");
  } else {
    Serial.println("Sin WiFi: los comandos funcionan igual, pero el panel no se entera; se reintenta cada 15 s.");
  }
  Serial.println("Listo: mantén el botón, di \"entrada\" o \"salida\" y suéltalo.");
}

void loop() {
  if (digitalRead(BOTON) == LOW) {
    delay(20);                                   // rebote del botón
    if (digitalRead(BOTON) == LOW) atenderBoton();
  }
  // Sin WiFi, otro intento cada 15 s (también si la LILYGO cambió de red).
  if (WiFi.status() != WL_CONNECTED && millis() - ultimoIntentoWifi > 15000) {
    ultimoIntentoWifi = millis();
    if (redes.run(4000) == WL_CONNECTED) Serial.printf("WiFi: conectado a \"%s\"\n", WiFi.SSID().c_str());
  }
  delay(5);
}
