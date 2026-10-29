// El modelo de comandos de voz, entrenado en Edge Impulse.
//
// Cuando exporten su modelo (Deployment → Arduino library) e instalen el .zip
// en el Arduino IDE (Sketch → Include Library → Add .ZIP Library), cambien esta
// línea por el nombre de su librería: <nombre-del-proyecto>_inferencing.h
#pragma once
#include <XIAO_inferencing.h>

// Las clases del modelo que hacen algo. Cualquier otra ("ruido", "desconocido")
// no prende ni apaga nada.
#define CLASE_ENTRADA  "entrada"   // prende el LED
#define CLASE_SALIDA   "salida"    // apaga el LED

// Qué tan seguro tiene que estar el modelo (0 a 1) para obedecer.
#define UMBRAL_VOZ     0.80f
