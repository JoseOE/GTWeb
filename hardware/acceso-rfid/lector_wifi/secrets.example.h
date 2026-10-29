// Copia este archivo como secrets.h (no se sube a git) y llena tus datos.
// pruebas/lector-rfid.mjs preparar escribe la llave del lector por ti.
// La LILYGO y la XIAO usan los mismos datos: copia el mismo secrets.h en las dos carpetas.
#pragma once

// Red 1 (por ejemplo, la de tu casa).
#define WIFI_SSID       "nombre-de-tu-red"
#define WIFI_PASS       "contraseña-de-tu-red"
// En local: la IP de tu PC con GTWeb corriendo (ipconfig → IPv4). En producción: https://gtweb.onrender.com
#define SERVIDOR        "http://192.168.1.100:8080"
#define LECTOR_LLAVE    "pega-aqui-la-llave-del-lector"

// Redes 2 y 3 (opcionales): la placa se sabe las tres y se conecta sola a la
// que encuentre con mejor señal. Solo redes de 2.4 GHz con una contraseña
// (no las que piden usuario de alumno ni una página para iniciar sesión).
// Déjalas en "" si no las usas.
#define WIFI_SSID_2     ""
#define WIFI_PASS_2     ""
#define WIFI_SSID_3     ""
#define WIFI_PASS_3     ""
