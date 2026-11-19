// Copia este archivo como secrets.h (no se sube a git) y llena tus datos.
// pruebas/lector-rfid.mjs preparar escribe la llave del lector por ti.
#pragma once

// Red 1, la principal (por ejemplo, el hotspot de tu teléfono). Se prueba primero.
// Solo redes de 2.4 GHz: el ESP32 no ve las de 5 GHz. En el teléfono, activa
// "Maximizar compatibilidad" (iPhone) o la banda de 2.4 GHz (Android).
#define WIFI_SSID       "nombre-de-tu-red"
#define WIFI_PASS       "contraseña-de-tu-red"
// En local: la IP de tu PC con GTWeb corriendo (ipconfig → IPv4). En producción: https://gtweb.onrender.com
#define SERVIDOR        "https://gtweb.onrender.com"
#define LECTOR_LLAVE    "pega-aqui-la-llave-del-lector"

// Redes 2 y 3 (opcionales): si la principal no está, se conecta a la que
// encuentre con mejor señal. Solo redes con una contraseña (no las que piden
// usuario de alumno ni una página para iniciar sesión). "" si no las usas.
#define WIFI_SSID_2     ""
#define WIFI_PASS_2     ""
#define WIFI_SSID_3     ""
#define WIFI_PASS_3     ""

// Datos móviles (si no hay WiFi): el APN de tu operador.
// Telcel: internet.itelcel.com · AT&T: modem.att.mx · Movistar: internet.movistar.mx
#define APN             "internet.itelcel.com"
