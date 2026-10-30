// Comandos de voz del lector: XIAO ESP32-S3 Sense + modelo de Edge Impulse.
//
// La XIAO escucha TODO EL TIEMPO (sin botón). Una tarea lee el micrófono sin
// parar y guarda los últimos 2 s; cada 250 ms se revisa el último segundo:
//   - si es puro ruido de fondo (más bajo que UMBRAL_SONIDO), no se hace nada;
//   - si hay sonido, el modelo (modelo_voz.h) decide qué se dijo.
// Si oye "entrada" o "salida" en VENTANAS_SEGUIDAS revisiones seguidas, con
// UMBRAL_VOZ o más:
//   "entrada" -> prende el LED        "salida" -> apaga el LED
// y descansa DESCANSO_VOZ_MS para no contar la misma palabra dos veces.
// Cada comando se manda a GTWeb (POST /api/lectores/voz): aparece en el
// historial de la puerta y el panel muestra si el LED está prendido.
//
// Internet: las redes de secrets.h, o la red propia de la LILYGO cuando ella usa
// LTE/2G (LECTOR_AP_SSID); en ese caso le manda el comando a la LILYGO y ella lo
// reenvía al servidor por el módem.
//
// LED: el naranja de la placa y, si quieres uno más visible, un LED en D1
// (en la placa del equipo, D2 y D3 fallan: probablemente una soldadura del header)
//
// A la LILYGO: D0 → GPIO 14 de la LILYGO, y GND con GND. En cada entrada o
// salida válida manda un pulso (corto = entrada, largo = salida) y la LILYGO
// prende su LED verde o el de salida. Por turnos: no cuenta "entrada" dos veces
// seguidas ni "salida" dos veces seguidas.
// (con resistencia de 220 Ω a GND). Ya no lleva botón.
//
// Monitor serie (115200): cada 3 s, el nivel del ruido de fondo (para ajustar
// UMBRAL_SONIDO); cuando hay sonido, lo que oyó el modelo con el % de cada clase.
// Placa: "XIAO_ESP32S3". En Herramientas, PSRAM: "OPI PSRAM".

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

#define LED_PLACA      21     // LED naranja de la placa; enciende en LOW
#define LED_EXTRA      D1     // LED opcional; enciende en HIGH
#define PULSO_LILYGO   D0     // a GPIO 14 de la LILYGO (y GND con GND)
#define PULSO_ENTRADA_MS 100  // pulso corto = entrada
#define PULSO_SALIDA_MS  300  // pulso largo = salida
#define MIC_CLK        42     // micrófono PDM de la placa Sense
#define MIC_DATOS      41
#define FRECUENCIA     16000
#define ANILLO         (FRECUENCIA * 2)    // los últimos 2 s de audio
#define CADA_MS        250                 // cada cuánto se revisa el último segundo
#define NIVEL_CADA_MS  3000                // cada cuánto se muestra el ruido de fondo

I2SClass mic;
Preferences prefs;
WiFiMulti redes;
uint32_t ultimoIntentoWifi = 0;

int16_t* anillo = nullptr;          // lo que va oyendo el micrófono (PSRAM)
int16_t* ventana = nullptr;         // el último segundo, el que ve el modelo
volatile uint32_t escritas = 0;     // muestras que ha leído el micrófono desde que arrancó
uint32_t revisadas = 0;             // hasta dónde se revisó
bool ledEncendido = false;          // sobrevive a los reinicios

String ultimoComando;               // el comando que se está oyendo en ventanas seguidas
int seguidas = 0;
uint32_t descansoHasta = 0;
uint32_t ultimoNivel = 0;
int32_t picoFondo = 0;              // el pico más alto del ruido de fondo desde el último aviso

// ------------------------------------------------------------
// LED
// ------------------------------------------------------------
void ponerLed(bool encendido) {
  ledEncendido = encendido;
  digitalWrite(LED_PLACA, encendido ? LOW : HIGH);
  digitalWrite(LED_EXTRA, encendido ? HIGH : LOW);
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
  String resultado = campo(respuesta, "resultado");
  Serial.printf("         panel: %s%s\n", code == 200 ? (resultado + (resultado == "IGNORADO" ? " (" + campo(respuesta, "motivo") + ")" : "")).c_str()
                                                   : ("HTTP " + String(code)).c_str(),
                url.startsWith(IP_LECTOR) ? " (por la LILYGO)" : "");
}

// ------------------------------------------------------------
// Micrófono (corre aparte, en el núcleo 0, sin parar)
// ------------------------------------------------------------
void tareaMicrofono(void*) {
  int16_t bloque[256];
  for (;;) {
    size_t n = mic.readBytes((char*)bloque, sizeof(bloque)) / 2;
    uint32_t base = escritas;
    for (size_t i = 0; i < n; i++) anillo[(base + i) % ANILLO] = bloque[i];
    escritas = base + n;
    if (n == 0) vTaskDelay(1);
  }
}

// ------------------------------------------------------------
// Modelo
// ------------------------------------------------------------
// Copia el último segundo, le quita el nivel de fondo (DC) del micrófono y
// devuelve su pico (0 a 32767).
int32_t tomarUltimoSegundo() {
  const uint32_t n = EI_CLASSIFIER_RAW_SAMPLE_COUNT;
  uint32_t inicio = escritas - n;
  int64_t suma = 0;
  for (uint32_t i = 0; i < n; i++) {
    ventana[i] = anillo[(inicio + i) % ANILLO];
    suma += ventana[i];
  }
  int32_t media = (int32_t)(suma / (int64_t)n);
  int32_t pico = 0;
  for (uint32_t i = 0; i < n; i++) {
    int32_t v = (int32_t)ventana[i] - media;
    if (v > 32767) v = 32767;
    if (v < -32768) v = -32768;
    ventana[i] = (int16_t)v;
    int32_t absoluto = v < 0 ? -v : v;
    if (absoluto > pico) pico = absoluto;
  }
  return pico;
}

// Con NORMALIZAR_VOZ, el volumen se sube a un nivel parecido al de las muestras
// de entrenamiento (el micrófono de la XIAO suena mucho más bajo que un celular).
void ajustarVolumen(int32_t pico) {
#if NORMALIZAR_VOZ
  if (pico <= 0) return;
  float ganancia = (float)PICO_OBJETIVO / (float)pico;
  if (ganancia > (float)GANANCIA_MAXIMA) ganancia = (float)GANANCIA_MAXIMA;
  for (uint32_t i = 0; i < EI_CLASSIFIER_RAW_SAMPLE_COUNT; i++) {
    int32_t v = (int32_t)(ventana[i] * ganancia);
    if (v > 32767) v = 32767;
    if (v < -32768) v = -32768;
    ventana[i] = (int16_t)v;
  }
#endif
}

static int datosAudio(size_t offset, size_t length, float* out) {
  ei::numpy::int16_to_float(ventana + offset, out, length);
  return 0;
}

// ¿Qué se dijo? false = no se pudo correr el modelo.
bool clasificar(String& etiqueta, float& valor, int32_t pico) {
  ei::signal_t senal;
  senal.total_length = EI_CLASSIFIER_RAW_SAMPLE_COUNT;
  senal.get_data = &datosAudio;
  ei_impulse_result_t resultado = {0};
  if (run_classifier(&senal, &resultado, false) != EI_IMPULSE_OK) return false;
  valor = 0;
  Serial.printf("sonido (pico %ld):", (long)pico);
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

// Le avisa a la LILYGO por un cable: pulso corto = entrada, largo = salida.
void pulsoLilygo(bool entrada) {
  digitalWrite(PULSO_LILYGO, HIGH);
  delay(entrada ? PULSO_ENTRADA_MS : PULSO_SALIDA_MS);
  digitalWrite(PULSO_LILYGO, LOW);
}

// Por turnos: el LED prendido es "alguien entró por voz y no ha salido".
// Otro "entrada" con el LED prendido (u otro "salida" con el LED apagado) no
// cuenta: ni pulso a la LILYGO ni aviso al panel.
void obedecer(const String& etiqueta, float valor) {
  bool entrada = etiqueta.equalsIgnoreCase(CLASE_ENTRADA);
  if (entrada == ledEncendido) {
    Serial.printf("VOZ      \"%s\" (%.0f %%): ya %s, no cuenta otra vez\n", entrada ? CLASE_ENTRADA : CLASE_SALIDA,
                  valor * 100, entrada ? "entró (falta su salida)" : "salió (falta una entrada)");
    return;
  }
  Serial.printf("VOZ      \"%s\" (%.0f %%): LED %s\n", entrada ? CLASE_ENTRADA : CLASE_SALIDA,
                valor * 100, entrada ? "encendido" : "apagado");
  ponerLed(entrada);
  prefs.putBool("led", entrada);
  pulsoLilygo(entrada);
  avisarServidor(entrada ? CLASE_ENTRADA : CLASE_SALIDA, valor);
}

// Revisa el último segundo de audio.
void revisar() {
  int32_t pico = tomarUltimoSegundo();
  if (millis() < descansoHasta) return;            // acaba de obedecer: no contar la misma palabra
  if (pico < UMBRAL_SONIDO) {                       // puro ruido de fondo
    seguidas = 0;
    if (pico > picoFondo) picoFondo = pico;
    return;
  }
  ajustarVolumen(pico);
  String etiqueta;
  float valor = 0;
  if (!clasificar(etiqueta, valor, pico)) return;
  bool esComando = valor >= UMBRAL_VOZ &&
                   (etiqueta.equalsIgnoreCase(CLASE_ENTRADA) || etiqueta.equalsIgnoreCase(CLASE_SALIDA));
  if (!esComando) {
    seguidas = 0;
    return;
  }
  if (etiqueta == ultimoComando) seguidas++;
  else { ultimoComando = etiqueta; seguidas = 1; }
  if (seguidas < VENTANAS_SEGUIDAS) return;
  seguidas = 0;
  descansoHasta = millis() + DESCANSO_VOZ_MS;
  obedecer(etiqueta, valor);
}

// ------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== XIAO DETECCION DE VOZ · version 7 (por turnos y pulso a la LILYGO) ===");
  pinMode(LED_PLACA, OUTPUT);
  pinMode(LED_EXTRA, OUTPUT);
  pinMode(PULSO_LILYGO, OUTPUT);
  digitalWrite(PULSO_LILYGO, LOW);
  // Prueba de los LEDs: el naranja de la placa y el de D1 parpadean 3 veces.
  // Si aquí no prenden, el problema es la conexión, no la voz.
  Serial.println("Prueba de LEDs: deben parpadear 3 veces (el naranja de la placa y el de D1)");
  for (int i = 0; i < 3; i++) { ponerLed(true); delay(300); ponerLed(false); delay(300); }
  prefs.begin("voz", false);
  ponerLed(prefs.getBool("led", false));
  Serial.printf("LED al arrancar: %s (como quedó la última vez)\n", ledEncendido ? "encendido" : "apagado");

  anillo = (int16_t*)ps_malloc(ANILLO * sizeof(int16_t));
  ventana = (int16_t*)ps_malloc(EI_CLASSIFIER_RAW_SAMPLE_COUNT * sizeof(int16_t));
  mic.setPinsPdmRx(MIC_CLK, MIC_DATOS);
  if (!anillo || !ventana || !mic.begin(I2S_MODE_PDM_RX, FRECUENCIA, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO)) {
    Serial.println("No se pudo iniciar el micrófono (¿PSRAM en OPI PSRAM? ¿placa Sense bien puesta?).");
    while (true) delay(1000);
  }
  memset(anillo, 0, ANILLO * sizeof(int16_t));
  xTaskCreatePinnedToCore(tareaMicrofono, "microfono", 4096, nullptr, 2, nullptr, 0);
  Serial.printf("Modelo: %d clases, ventana de %.1f s. Umbral de sonido: %d\n", EI_CLASSIFIER_LABEL_COUNT,
                EI_CLASSIFIER_RAW_SAMPLE_COUNT / (float)FRECUENCIA, UMBRAL_SONIDO);

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
  revisadas = escritas;
  Serial.println("Escuchando: di \"entrada\" o \"salida\".");
}

void loop() {
  // Cada 250 ms de audio nuevo, se revisa el último segundo.
  if (escritas >= EI_CLASSIFIER_RAW_SAMPLE_COUNT && escritas - revisadas >= (uint32_t)(FRECUENCIA * CADA_MS / 1000)) {
    revisadas = escritas;
    revisar();
  }

  // El ruido de fondo, para saber a cuánto poner UMBRAL_SONIDO en modelo_voz.h.
  if (millis() - ultimoNivel > NIVEL_CADA_MS) {
    ultimoNivel = millis();
    Serial.printf("(ruido de fondo: pico %ld; umbral de sonido %d)\n", (long)picoFondo, UMBRAL_SONIDO);
    picoFondo = 0;
  }

  // Sin WiFi, otro intento cada 15 s (también si la LILYGO cambió de red).
  if (WiFi.status() != WL_CONNECTED && millis() - ultimoIntentoWifi > 15000) {
    ultimoIntentoWifi = millis();
    if (redes.run(4000) == WL_CONNECTED) Serial.printf("WiFi: conectado a \"%s\"\n", WiFi.SSID().c_str());
  }
  delay(5);
}
