// Cámara del lector de acceso: XIAO ESP32-S3 Sense.
//
// 1. Fotos de un acceso negado: cuando la LILYGO niega un acceso manda un pulso
//    a D1. La XIAO toma 3 fotos, las guarda en la microSD como
//    /accesos/foto_00001.jpg... y las sube a GTWeb (POST /api/lectores/fotos):
//    aparecen en la alerta del panel.
// 2. Reconocimiento de caras: mientras no hay pulso, revisa la cámara con el
//    modelo de Edge Impulse (modelo.h). Si ve a un socio varias veces seguidas
//    con suficiente seguridad, se lo dice a GTWeb (POST /api/lectores/rostro):
//    el servidor revisa su membresía y le ordena a la LILYGO abrir, sin tag.
//    Después toma y sube 3 fotos, que quedan en ese pase.
//
// Internet: las redes de secrets.h, o la red propia de la LILYGO cuando ella usa
// LTE/2G (LECTOR_AP_SSID); en ese caso le manda todo a la LILYGO y ella lo
// reenvía al servidor por el módem.
//
// LED naranja: un parpadeo largo al arrancar = cámara lista; 10 rápidos = falló;
// uno por cada foto. Monitor serie (115200), si está conectada por USB:
//   f  tomar las fotos sin esperar el pulso      c  ver qué ve el modelo ahora
//
// Placa: "XIAO_ESP32S3". En Herramientas, PSRAM: "OPI PSRAM" (la cámara la necesita).
// Antes de subirlo: copia secrets.example.h como secrets.h (el mismo que la LILYGO)
// y pon el nombre de tu librería de Edge Impulse en modelo.h.

#include "esp_camera.h"
#include "img_converters.h"
#include "FS.h"
#include "SD.h"
#include "SPI.h"
#include <Preferences.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include "secrets.h"
#include "modelo.h"

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

#define DISPARO         D1   // GPIO 2; llega de GPIO 13 de la LILYGO
#define SD_CS           21   // la microSD y el LED de la placa comparten este pin
#define LED             21   // LED naranja; enciende en LOW
#define FOTOS           3
#define ENTRE_FOTOS_MS  500
#define ARRANQUE_MS     2000 // pulsos antes de esto se ignoran
#define DESCANSO_MS     8000 // tras una tanda de fotos, otro pulso tan pronto se ignora
#define CADA_CARA_MS    700  // cada cuánto se revisa la cámara con el modelo

// Pines de la cámara de la XIAO ESP32-S3 Sense
#define PWDN_GPIO_NUM   -1
#define RESET_GPIO_NUM  -1
#define XCLK_GPIO_NUM   10
#define SIOD_GPIO_NUM   40
#define SIOC_GPIO_NUM   39
#define Y9_GPIO_NUM     48
#define Y8_GPIO_NUM     11
#define Y7_GPIO_NUM     12
#define Y6_GPIO_NUM     14
#define Y5_GPIO_NUM     16
#define Y4_GPIO_NUM     18
#define Y3_GPIO_NUM     17
#define Y2_GPIO_NUM     15
#define VSYNC_GPIO_NUM  38
#define HREF_GPIO_NUM   47
#define PCLK_GPIO_NUM   13

Preferences prefs;
WiFiMulti redes;
uint32_t ultimoIntentoWifi = 0;
bool camaraLista = false;
uint32_t contador = 0;   // número de la última foto; sobrevive a los reinicios
bool disparoAntes = false;
uint32_t ultimaTanda = 0;

bool iniciarCamara() {
  camera_config_t c = {};
  c.ledc_channel = LEDC_CHANNEL_0;
  c.ledc_timer   = LEDC_TIMER_0;
  c.pin_d0 = Y2_GPIO_NUM;  c.pin_d1 = Y3_GPIO_NUM;
  c.pin_d2 = Y4_GPIO_NUM;  c.pin_d3 = Y5_GPIO_NUM;
  c.pin_d4 = Y6_GPIO_NUM;  c.pin_d5 = Y7_GPIO_NUM;
  c.pin_d6 = Y8_GPIO_NUM;  c.pin_d7 = Y9_GPIO_NUM;
  c.pin_xclk  = XCLK_GPIO_NUM;
  c.pin_pclk  = PCLK_GPIO_NUM;
  c.pin_vsync = VSYNC_GPIO_NUM;
  c.pin_href  = HREF_GPIO_NUM;
  c.pin_sccb_sda = SIOD_GPIO_NUM;
  c.pin_sccb_scl = SIOC_GPIO_NUM;
  c.pin_pwdn  = PWDN_GPIO_NUM;
  c.pin_reset = RESET_GPIO_NUM;
  c.xclk_freq_hz = 10000000;
  c.pixel_format = PIXFORMAT_JPEG;
  c.frame_size   = FRAMESIZE_VGA;    // 640x480, unos 20-40 KB por foto
  c.jpeg_quality = 12;
  // Un solo búfer y solo cuando se pide una foto: con la cámara capturando sin
  // parar, el búfer se desborda mientras nadie lo lee ("cam_hal: FB-OVF").
  c.fb_count     = 1;
  c.fb_location  = CAMERA_FB_IN_PSRAM;
  c.grab_mode    = CAMERA_GRAB_WHEN_EMPTY;

  esp_err_t err = esp_camera_init(&c);
  if (err != ESP_OK) {
    Serial.printf("La camara no inicio (error 0x%x). Revisa PSRAM = OPI PSRAM.\n", err);
    return false;
  }
  return true;
}

// Entre foto y foto la microSD se suelta (SD.end) para poder usar el pin 21
// como LED sin estorbarle.
void parpadeo(int ms) {
  pinMode(LED, OUTPUT);
  digitalWrite(LED, LOW);  delay(ms);
  digitalWrite(LED, HIGH); delay(ms);
}

bool guardar(camera_fb_t* fb, uint32_t n) {
  if (!SD.begin(SD_CS)) {
    Serial.println("Sin microSD: la foto no se guardo");
    return false;
  }
  SD.mkdir("/accesos");
  char ruta[40];
  snprintf(ruta, sizeof(ruta), "/accesos/foto_%05lu.jpg", (unsigned long)n);
  File f = SD.open(ruta, FILE_WRITE);
  bool ok = f && f.write(fb->buf, fb->len) == fb->len;
  if (f) f.close();
  SD.end();
  Serial.printf("%s %s (%u bytes)\n", ok ? "Guardada" : "Fallo al guardar", ruta, (unsigned)fb->len);
  return ok;
}

// ------------------------------------------------------------
// Red
// ------------------------------------------------------------
// Conectada a la red de la LILYGO (ella usa LTE/2G): todo va a la LILYGO.
// En cualquier otra red: directo al servidor.
String urlBase() {
  return WiFi.SSID() == LECTOR_AP_SSID ? String(IP_LECTOR) : String(SERVIDOR);
}

// Manda algo a la API del lector con su llave. Devuelve el código HTTP (< 0 = sin respuesta).
// TODO versión final: fijar el certificado raíz de Render en lugar de setInsecure().
int enviar(const char* ruta, const uint8_t* datos, size_t largo, const char* tipo, String& respuesta) {
  if (WiFi.status() != WL_CONNECTED) return -1;
  WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
  HTTPClient http;
  String url = urlBase() + ruta;
  if (!(url.startsWith("https") ? http.begin(seguro, url) : http.begin(simple, url))) return -1;
  // Por la LILYGO y su módem tarda más.
  http.setTimeout(url.startsWith(IP_LECTOR) ? 30000 : 8000);
  http.addHeader("X-Lector-Key", LECTOR_LLAVE);
  http.addHeader("Content-Type", tipo);
  int code = http.POST((uint8_t*)datos, largo);
  respuesta = code > 0 ? http.getString() : "";
  http.end();
  return code;
}

String campo(const String& json, const char* nombre) {
  String clave = String("\"") + nombre + "\":\"";
  int i = json.indexOf(clave);
  if (i < 0) return "";
  i += clave.length();
  int f = json.indexOf('"', i);
  return f < 0 ? "" : json.substring(i, f);
}

bool subir(camera_fb_t* fb) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Sin WiFi: la foto solo queda en la microSD");
    return false;
  }
  String respuesta;
  int code = enviar("/api/lectores/fotos", fb->buf, fb->len, "image/jpeg", respuesta);
  Serial.printf("Subida al panel: %s (HTTP %d)%s\n", code == 200 ? "OK" : "fallo", code,
                urlBase() == IP_LECTOR ? " por la LILYGO" : "");
  return code == 200;
}

void tomarFotos() {
  Serial.println("Tomando fotos");
  if (!camaraLista) {
    Serial.println("La camara no esta lista");
    for (int i = 0; i < 10; i++) parpadeo(60);
    return;
  }
  // La primera imagen puede ser de antes del disparo: se descarta.
  camera_fb_t* vieja = esp_camera_fb_get();
  if (vieja) esp_camera_fb_return(vieja);

  for (int i = 0; i < FOTOS; i++) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("La camara no entrego imagen");
      continue;
    }
    contador++;
    prefs.putUInt("contador", contador);
    guardar(fb, contador);
    subir(fb);
    esp_camera_fb_return(fb);
    parpadeo(150);
    delay(ENTRE_FOTOS_MS);
  }
  ultimaTanda = millis();
  Serial.println("Listo, esperando otro disparo");
}

// ------------------------------------------------------------
// Reconocimiento de caras (Edge Impulse)
// ------------------------------------------------------------
#if RECONOCER_CARAS
uint8_t* imagenRgb = nullptr;   // un cuadro VGA en RGB888 (900 KB, en la PSRAM)
uint32_t ultimaRevision = 0;
String candidato;               // la persona que se está viendo en cuadros seguidos
int seguidos = 0;
String ultimaPersona;           // a quién se le avisó al servidor
uint32_t ultimaVista = 0, ultimoAviso = 0;

// El modelo pide los píxeles así (como en el ejemplo esp32_camera de Edge Impulse).
static int datosModelo(size_t offset, size_t length, float* out) {
  size_t px = offset * 3;
  for (size_t i = 0; i < length; i++, px += 3) {
    out[i] = (imagenRgb[px + 2] << 16) + (imagenRgb[px + 1] << 8) + imagenRgb[px];
  }
  return 0;
}

// Toma un cuadro y le pregunta al modelo quién es. false = no se pudo.
bool clasificar(String& etiqueta, float& valor) {
  camera_fb_t* fb = esp_camera_fb_get();
  if (!fb) return false;
  int ancho = fb->width, alto = fb->height;
  bool ok = fmt2rgb888(fb->buf, fb->len, PIXFORMAT_JPEG, imagenRgb);
  esp_camera_fb_return(fb);
  if (!ok) return false;
  ei::image::processing::crop_and_interpolate_rgb888(imagenRgb, ancho, alto, imagenRgb,
                                                     EI_CLASSIFIER_INPUT_WIDTH, EI_CLASSIFIER_INPUT_HEIGHT);
  ei::signal_t senal;
  senal.total_length = EI_CLASSIFIER_INPUT_WIDTH * EI_CLASSIFIER_INPUT_HEIGHT;
  senal.get_data = &datosModelo;
  ei_impulse_result_t resultado = {0};
  if (run_classifier(&senal, &resultado, false) != EI_IMPULSE_OK) return false;
  valor = 0;
  for (size_t i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
    if (resultado.classification[i].value > valor) {
      valor = resultado.classification[i].value;
      etiqueta = resultado.classification[i].label;
    }
  }
  return true;
}

bool esIgnorada(const String& etiqueta) {
  for (const char* e : ETIQUETAS_IGNORADAS) {
    if (etiqueta.equalsIgnoreCase(e)) return true;
  }
  return false;
}

// Le dice al servidor quién llegó. El servidor decide (membresía) y, si puede
// pasar, le ordena a la LILYGO abrir. Luego quedan 3 fotos en ese pase.
void avisarCara(const String& etiqueta, float valor) {
  Serial.printf("CARA     %s (%.0f %%): avisando al servidor\n", etiqueta.c_str(), valor * 100);
  String json = String("{\"etiqueta\":\"") + etiqueta + "\",\"confianza\":" + String(valor, 3) + "}";
  String respuesta;
  int code = enviar("/api/lectores/rostro", (const uint8_t*)json.c_str(), json.length(), "application/json", respuesta);
  String resultado = campo(respuesta, "resultado");
  Serial.printf("         servidor: %s %s %s\n", code == 200 ? resultado.c_str() : ("HTTP " + String(code)).c_str(),
                campo(respuesta, "nombre").c_str(), campo(respuesta, "motivo").c_str());
  // Nadie a quien abrir o ya se contó hace nada: sin fotos.
  if (resultado == "IGNORADO" || resultado == "DESCONOCIDO") return;
  tomarFotos();
}

void vigilarCaras(bool mostrar) {
  if (!camaraLista || !imagenRgb) return;
  if (!mostrar && millis() - ultimaRevision < CADA_CARA_MS) return;
  if (!mostrar && ultimaTanda && millis() - ultimaTanda < 2000) return;   // recién tomó fotos
  ultimaRevision = millis();

  String etiqueta;
  float valor = 0;
  if (!clasificar(etiqueta, valor)) return;
  if (mostrar) Serial.printf("El modelo ve: %s (%.0f %%)\n", etiqueta.c_str(), valor * 100);

  uint32_t ahora = millis();
  if (valor < UMBRAL_CARA || esIgnorada(etiqueta)) {
    seguidos = 0;
    candidato = "";
    return;
  }
  // Sigue frente a la cámara la misma persona a la que ya se le avisó: nada.
  if (etiqueta == ultimaPersona && ahora - ultimaVista < 5000) {
    ultimaVista = ahora;
    return;
  }
  if (etiqueta == candidato) seguidos++;
  else { candidato = etiqueta; seguidos = 1; }
  if (seguidos < CUADROS_SEGUIDOS) return;
  seguidos = 0;
  // La misma persona otra vez antes de 30 s (p. ej. se asomó de nuevo): nada.
  if (etiqueta == ultimaPersona && ahora - ultimoAviso < 30000) {
    ultimaVista = ahora;
    return;
  }
  ultimaPersona = etiqueta;
  ultimaVista = ultimoAviso = ahora;
  avisarCara(etiqueta, valor);
}
#endif

// ------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Camara del lector (XIAO ESP32-S3 Sense) ===");

  pinMode(DISPARO, INPUT_PULLDOWN);   // sin la LILYGO conectada se queda en LOW
  pinMode(LED, OUTPUT);
  digitalWrite(LED, HIGH);

  prefs.begin("camara", false);
  contador = prefs.getUInt("contador", 0);

  // Las mismas redes que la LILYGO, más la red propia de la LILYGO (cuando usa
  // LTE/2G). Se conecta a la conocida con mejor señal.
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
    Serial.printf("WiFi: conectado a \"%s\", señal %d dBm%s\n", WiFi.SSID().c_str(), WiFi.RSSI(),
                  urlBase() == IP_LECTOR ? " (la red de la LILYGO: todo va por su módem)" : "");
  } else {
    Serial.println("Sin WiFi: las fotos quedan en la microSD; se reintenta cada 15 s.");
  }

  camaraLista = iniciarCamara();
#if RECONOCER_CARAS
  imagenRgb = (uint8_t*)ps_malloc(640 * 480 * 3);
  Serial.printf("Reconocimiento de caras: %s (modelo de %dx%d, %d clases)\n",
                imagenRgb ? "activo" : "SIN MEMORIA, desactivado",
                EI_CLASSIFIER_INPUT_WIDTH, EI_CLASSIFIER_INPUT_HEIGHT, EI_CLASSIFIER_LABEL_COUNT);
#endif
  if (camaraLista) {
    Serial.printf("Camara lista. Ultima foto: %lu. Esperando pulso en D1 (o 'f').\n", (unsigned long)contador);
    parpadeo(1000);
  } else {
    for (int i = 0; i < 10; i++) parpadeo(60);
  }
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'f') tomarFotos();
#if RECONOCER_CARAS
    if (c == 'c') vigilarCaras(true);
#endif
  }

  // Sin WiFi, otro intento cada 15 s; si la red se cambió (p. ej. la LILYGO pasó
  // a LTE y prendió su red), también se busca otra vez.
  if (WiFi.status() != WL_CONNECTED && millis() - ultimoIntentoWifi > 15000) {
    ultimoIntentoWifi = millis();
    if (redes.run(4000) == WL_CONNECTED) {
      Serial.printf("WiFi: conectado a \"%s\"%s\n", WiFi.SSID().c_str(), urlBase() == IP_LECTOR ? " (red de la LILYGO)" : "");
    }
  }

  // El pulso de la LILYGO tiene prioridad sobre las caras.
  bool disparo = digitalRead(DISPARO) == HIGH;
  if (disparo && !disparoAntes && millis() > ARRANQUE_MS) {
    if (ultimaTanda && millis() - ultimaTanda < DESCANSO_MS) {
      Serial.println("Pulso ignorado: acabo de tomar fotos");
    } else {
      Serial.println("Pulso de la LILYGO (acceso negado)");
      tomarFotos();
    }
  }
  disparoAntes = disparo;

#if RECONOCER_CARAS
  vigilarCaras(false);
#endif
  delay(5);
}
