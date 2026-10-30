// El modelo de comandos de voz, entrenado en Edge Impulse (proyecto "GYMTrack",
// clases: entrada, salida y silencio; ventanas de 1 s a 16 kHz).
//
// La librería se instala en el Arduino IDE con Sketch → Include Library →
// Add .ZIP Library (el ei-gymtrack-arduino-*.zip que exporta Edge Impulse).
// Si reentrenan con otro proyecto, cambien esta línea: <nombre-del-proyecto>_inferencing.h
#pragma once
#include <GYMTrack_inferencing.h>

// Las clases del modelo que hacen algo. Cualquier otra ("silencio") no prende
// ni apaga nada.
#define CLASE_ENTRADA  "entrada"   // prende el LED
#define CLASE_SALIDA   "salida"    // apaga el LED

// Qué tan seguro tiene que estar el modelo (0 a 1) para obedecer.
#define UMBRAL_VOZ     0.80f

// Volumen: el micrófono de la XIAO suena mucho más bajo que un celular. Con 1,
// cada grabación se sube hasta que su pico llegue a PICO_OBJETIVO (de 32767),
// sin multiplicar más de GANANCIA_MAXIMA veces. Pon 0 si entrenaron con
// audios grabados con la misma XIAO (grabar_voz) y reconoce bien sin esto.
#define NORMALIZAR_VOZ   1
#define PICO_OBJETIVO    16000
#define GANANCIA_MAXIMA  40

// Escucha continua.
// UMBRAL_SONIDO: debajo de este pico es ruido de fondo y ni se le pregunta al
// modelo. El monitor muestra cada 3 s "(ruido de fondo: pico N ...)": ponlo un
// poco arriba de ese N (si es 150, pon unos 300). Si no reacciona cuando hablas,
// bájalo; si reacciona con cualquier ruido, súbelo.
#define UMBRAL_SONIDO      300
// Cuántas revisiones seguidas (cada 250 ms) tiene que oír el mismo comando.
#define VENTANAS_SEGUIDAS  2
// Después de obedecer, cuánto tiempo no escucha (para no contar la palabra dos veces).
#define DESCANSO_VOZ_MS    2000
