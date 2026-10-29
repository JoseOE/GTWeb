// El modelo de reconocimiento de caras, entrenado en Edge Impulse.
//
// Cuando exporten su modelo (Deployment → Arduino library) e instalen el .zip
// en el Arduino IDE (Sketch → Include Library → Add .ZIP Library), cambien esta
// línea por el nombre de su librería: <nombre-del-proyecto>_inferencing.h
#pragma once
#include <XIAO_inferencing.h>
#include "edge-impulse-sdk/dsp/image/image.hpp"   // recorte y escalado de la imagen

// 0 = la XIAO solo es cámara (fotos con el pulso de la LILYGO), sin reconocer caras.
#define RECONOCER_CARAS 1

// Las clases del modelo se llaman como el primer nombre del socio en GymTrack
// ("chema", "valeria", "yael", "eduardo", "edwin"); el servidor busca al socio
// por ese nombre. Estas otras clases no son personas a las que se les abre:
static const char* ETIQUETAS_IGNORADAS[] = {"desconocido", "nadie"};

// Qué tan seguro tiene que estar el modelo (0 a 1) y cuántos cuadros seguidos
// tiene que ver a la misma persona antes de avisar al servidor.
#define UMBRAL_CARA       0.85f
#define CUADROS_SEGUIDOS  3
