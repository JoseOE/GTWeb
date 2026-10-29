// Cámara del lector de acceso: XIAO ESP32-S3 Sense.
//
// Cuando la LILYGO niega un acceso manda un pulso a D1. La XIAO toma 3 fotos,
// las guarda en la microSD como /accesos/foto_00001.jpg, foto_00002.jpg... y,
// si hay WiFi, las sube a GTWeb (POST /api/lectores/fotos): aparecen en la
// alerta del panel. El LED naranja de la placa parpadea una vez por cada foto.
//
// Al arrancar: un parpadeo largo = cámara lista; 10 rápidos = la cámara falló.
// Monitor serie (115200), solo si está conectada por USB:
//   f  tomar las fotos sin esperar el pulso
//
// Placa: "XIAO_ESP32S3". En Herramientas, PSRAM: "OPI PSRAM" (la cámara la necesita).
// Antes de subirlo: copia secrets.example.h como secrets.h (el mismo WiFi,
// servidor y llave que la LILYGO).

#include "esp_camera.h"
#include "FS.h"
#include "SD.h"
#include "SPI.h"
#include <Preferences.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include "secrets.h"

#define DISPARO         D1   // GPIO 2; llega de GPIO 13 de la LILYGO
#define SD_CS           21   // la microSD y el LED de la placa comparten este pin
#define LED             21   // LED naranja; enciende en LOW
#define FOTOS           3
#define ENTRE_FOTOS_MS  500
#define ARRANQUE_MS     2000 // pulsos antes de esto se ignoran
#define DESCANSO_MS     8000 // tras una tanda de fotos, otro pulso tan pronto se ignora

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

// La foto va tal cual (JPEG) en el cuerpo. http:// en local, https:// con Render.
// TODO versión final: fijar el certificado raíz de Render en lugar de setInsecure().
bool subir(camera_fb_t* fb) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Sin WiFi: la foto solo queda en la microSD");
    return false;
  }
  WiFiClientSecure seguro; seguro.setInsecure(); WiFiClient simple;
  HTTPClient http;
  String url = String(SERVIDOR) + "/api/lectores/fotos";
  if (!(url.startsWith("https") ? http.begin(seguro, url) : http.begin(simple, url))) return false;
  http.setTimeout(8000);
  http.addHeader("X-Lector-Key", LECTOR_LLAVE);
  http.addHeader("Content-Type", "image/jpeg");
  int code = http.POST(fb->buf, fb->len);
  http.end();
  Serial.printf("Subida al panel: %s (HTTP %d)\n", code == 200 ? "OK" : "fallo", code);
  return code == 200;
}

void tomarFotos() {
  Serial.println("Disparo: tomando fotos");
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
  Serial.println("Listo, esperando otro disparo");
}

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Camara del lector (XIAO ESP32-S3 Sense) ===");

  pinMode(DISPARO, INPUT_PULLDOWN);   // sin la LILYGO conectada se queda en LOW
  pinMode(LED, OUTPUT);
  digitalWrite(LED, HIGH);

  prefs.begin("camara", false);
  contador = prefs.getUInt("contador", 0);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.printf("Conectando a %s", WIFI_SSID);
  for (int i = 0; i < 30 && WiFi.status() != WL_CONNECTED; i++) { delay(500); Serial.print("."); }
  Serial.println(WiFi.status() == WL_CONNECTED ? " OK" : " sin WiFi (solo microSD)");

  camaraLista = iniciarCamara();
  if (camaraLista) {
    Serial.printf("Camara lista. Ultima foto: %lu. Esperando pulso en D1 (o 'f').\n", (unsigned long)contador);
    parpadeo(1000);
  } else {
    for (int i = 0; i < 10; i++) parpadeo(60);
  }
}

void loop() {
  if (Serial.available() && Serial.read() == 'f') tomarFotos();

  bool disparo = digitalRead(DISPARO) == HIGH;
  if (disparo && !disparoAntes && millis() > ARRANQUE_MS) {
    if (ultimaTanda && millis() - ultimaTanda < DESCANSO_MS) {
      Serial.println("Pulso ignorado: acabo de tomar fotos");
    } else {
      tomarFotos();
      ultimaTanda = millis();
    }
  }
  disparoAntes = disparo;
  delay(5);
}
