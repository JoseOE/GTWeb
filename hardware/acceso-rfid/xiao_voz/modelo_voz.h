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
