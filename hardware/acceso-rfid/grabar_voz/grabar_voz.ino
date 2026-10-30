// Muestras de voz para entrenar el modelo: XIAO ESP32-S3 Sense.
//
// Mientras se mantiene presionado el botón, la XIAO graba con su micrófono
// (16 kHz, 16 bits, mono; hasta 3 s). Al soltarlo, la grabación queda lista y la
// página web la descarga sola como entrada.001.wav, entrada.002.wav...: en Edge
// Impulse (Data acquisition → Upload data → "Infer from filename") la clase sale
// del nombre.
//
// Desde la PC (misma red): abre http://<IP que sale en el monitor>/, elige la
// clase ("entrada", "salida", "ruido") y graba con el botón de la XIAO.
// El LED naranja de la placa se prende mientras graba.
//
// Las muestras salen igual que lo que oirá el modelo: el mismo micrófono, el
// mismo botón y la misma forma de grabar que xiao_deteccion.
//
// Conexión: botón entre D2 y GND (sin resistencia: se usa la interna).
// Placa: "XIAO_ESP32S3". En Herramientas, PSRAM: "OPI PSRAM".
// Usa el mismo secrets.h que xiao_deteccion (ya está copiado en esta carpeta).

#include <ESP_I2S.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <WebServer.h>
#include "secrets.h"

#ifndef WIFI_SSID_2
#define WIFI_SSID_2 ""
#define WIFI_PASS_2 ""
#endif
#ifndef WIFI_SSID_3
#define WIFI_SSID_3 ""
#define WIFI_PASS_3 ""
#endif

#define BOTON          D2     // a GND al presionar
#define LED            21     // LED naranja de la placa; enciende en LOW
#define MIC_CLK        42     // micrófono PDM de la placa Sense
#define MIC_DATOS      41
#define FRECUENCIA     16000
#define MAX_SEGUNDOS   3
#define MAX_MUESTRAS   (FRECUENCIA * MAX_SEGUNDOS)

I2SClass mic;
WiFiMulti redes;
WebServer servidor(80);

int16_t* grabacion = nullptr;   // la última grabación (en la PSRAM)
size_t muestras = 0;
uint32_t numero = 0;            // cuántas grabaciones van; la página lo vigila

const char PAGINA[] PROGMEM = R"HTML(<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Muestras de voz</title>
<style>
 body{font-family:system-ui,sans-serif;margin:16px;background:#111;color:#eee;max-width:640px}
 .fila{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0;align-items:center}
 button,input{font-size:16px;padding:8px 14px;border-radius:6px;border:1px solid #444;background:#222;color:#eee;cursor:pointer}
 button.activa{background:#1565c0;border-color:#1565c0}
 #estado{font-size:20px;font-weight:bold;margin:16px 0} audio{width:100%} li{margin:4px 0}
</style></head><body>
<h2>Muestras de voz para el modelo</h2>
<div class="fila" id="clases">
 <button data-clase="entrada" class="activa">entrada</button>
 <button data-clase="salida">salida</button>
 <button data-clase="ruido">ruido</button>
 <input id="otra" placeholder="otra clase" size="10">
</div>
<div id="estado">Mantén el botón de la XIAO, di la palabra y suéltalo.</div>
<audio id="audio" controls></audio>
<ul id="lista"></ul>
<p>Una palabra por grabación. Varía el tono, la distancia y la velocidad; que graben varias personas.
Para <b>ruido</b>: presiona el botón sin hablar, con el ruido normal del lugar.
La primera vez Chrome pregunta si permites descargar varios archivos: dile que sí.</p>
<script>
 const $ = id => document.getElementById(id);
 let clase = 'entrada', visto = null; const cuenta = {};
 document.querySelectorAll('[data-clase]').forEach(b => b.onclick = () => {
   document.querySelectorAll('[data-clase]').forEach(x => x.classList.remove('activa'));
   b.classList.add('activa'); clase = b.dataset.clase; $('otra').value = '';
 });
 $('otra').oninput = () => { const v = $('otra').value.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '');
   if (v) { document.querySelectorAll('[data-clase]').forEach(x => x.classList.remove('activa')); clase = v; } };
 async function revisar() {
   try {
     const e = await (await fetch('/estado')).json();
     if (e.grabando) $('estado').textContent = '🔴 Grabando...';
     if (visto === null) { visto = e.numero; return; }
     if (e.numero !== visto && !e.grabando) {
       visto = e.numero;
       const b = await (await fetch('/ultima.wav?n=' + e.numero)).blob();
       cuenta[clase] = (cuenta[clase] || 0) + 1;
       const nombre = clase + '.' + String(cuenta[clase]).padStart(3, '0') + '.wav';
       const url = URL.createObjectURL(b);
       const a = document.createElement('a'); a.href = url; a.download = nombre; a.click();
       $('audio').src = url;
       $('estado').textContent = '✔ ' + nombre + ' (' + (e.ms / 1000).toFixed(1) + ' s) · ' + clase + ': ' + cuenta[clase];
       const li = document.createElement('li'); li.textContent = nombre + ' · ' + (e.ms / 1000).toFixed(1) + ' s';
       $('lista').prepend(li);
     } else if (!e.grabando && $('estado').textContent.startsWith('🔴')) {
       $('estado').textContent = 'Mantén el botón de la XIAO, di la palabra y suéltalo.';
     }
   } catch (err) {}
 }
 setInterval(revisar, 300);
</script></body></html>)HTML";

volatile bool grabando = false;

void escribirEncabezadoWav(uint8_t* h, uint32_t bytesDatos) {
  auto u32 = [&](int p, uint32_t v) { h[p] = v; h[p + 1] = v >> 8; h[p + 2] = v >> 16; h[p + 3] = v >> 24; };
  auto u16 = [&](int p, uint16_t v) { h[p] = v; h[p + 1] = v >> 8; };
  memcpy(h, "RIFF", 4);      u32(4, 36 + bytesDatos);
  memcpy(h + 8, "WAVEfmt ", 8); u32(16, 16); u16(20, 1); u16(22, 1);
  u32(24, FRECUENCIA);       u32(28, FRECUENCIA * 2); u16(32, 2); u16(34, 16);
  memcpy(h + 36, "data", 4); u32(40, bytesDatos);
}

void enviarWav() {
  uint32_t bytesDatos = muestras * 2;
  uint8_t encabezado[44];
  escribirEncabezadoWav(encabezado, bytesDatos);
  servidor.setContentLength(44 + bytesDatos);
  servidor.sendHeader("Cache-Control", "no-store");
  servidor.send(200, "audio/wav", "");
  servidor.sendContent((const char*)encabezado, 44);
  servidor.sendContent((const char*)grabacion, bytesDatos);
}

void enviarEstado() {
  char json[80];
  snprintf(json, sizeof(json), "{\"numero\":%lu,\"grabando\":%s,\"ms\":%lu}",
           (unsigned long)numero, grabando ? "true" : "false", (unsigned long)(muestras * 1000 / FRECUENCIA));
  servidor.send(200, "application/json", json);
}

// Graba mientras el botón siga presionado (o hasta 3 s).
void grabar() {
  grabando = true;
  digitalWrite(LED, LOW);
  // Lo primero que entrega el micrófono al empezar trae basura: se tira.
  int16_t basura[256];
  mic.readBytes((char*)basura, sizeof(basura));
  muestras = 0;
  while (digitalRead(BOTON) == LOW && muestras < MAX_MUESTRAS) {
    size_t pedir = min((size_t)512, MAX_MUESTRAS - muestras);
    size_t leidos = mic.readBytes((char*)(grabacion + muestras), pedir * 2) / 2;
    muestras += leidos;   // aquí no se atiende la página: no perder audio
  }
  digitalWrite(LED, HIGH);
  grabando = false;
  if (muestras < FRECUENCIA / 4) {
    Serial.println("Muy corta (menos de 0.25 s): no se guarda.");
    return;
  }
  numero++;
  Serial.printf("Grabación %lu: %.1f s\n", (unsigned long)numero, muestras / (float)FRECUENCIA);
  while (digitalRead(BOTON) == LOW) delay(10);   // si llegó a 3 s, esperar a que suelte
}

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Muestras de voz (XIAO ESP32-S3 Sense) ===");
  pinMode(BOTON, INPUT_PULLUP);
  pinMode(LED, OUTPUT);
  digitalWrite(LED, HIGH);

  grabacion = (int16_t*)ps_malloc(MAX_MUESTRAS * 2);
  mic.setPinsPdmRx(MIC_CLK, MIC_DATOS);
  if (!grabacion || !mic.begin(I2S_MODE_PDM_RX, FRECUENCIA, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO)) {
    Serial.println("No se pudo iniciar el micrófono (¿PSRAM en OPI PSRAM?).");
    return;
  }

  WiFi.mode(WIFI_STA);
  const char* nombres[] = {WIFI_SSID, WIFI_SSID_2, WIFI_SSID_3};
  const char* claves[]  = {WIFI_PASS, WIFI_PASS_2, WIFI_PASS_3};
  for (int i = 0; i < 3; i++) {
    if (strlen(nombres[i])) redes.addAP(nombres[i], claves[i]);
  }
  Serial.println("Buscando una red conocida...");
  while (redes.run(8000) != WL_CONNECTED) Serial.println("  ...otra vez");

  servidor.on("/", []() { servidor.send_P(200, "text/html", PAGINA); });
  servidor.on("/estado", enviarEstado);
  servidor.on("/ultima.wav", enviarWav);
  servidor.begin();
  Serial.printf("Listo. En la PC (misma red \"%s\") abre:  http://%s/\n",
                WiFi.SSID().c_str(), WiFi.localIP().toString().c_str());
}

void loop() {
  servidor.handleClient();
  if (digitalRead(BOTON) == LOW) {
    delay(20);                                   // rebote del botón
    if (digitalRead(BOTON) == LOW) grabar();
  }
}
