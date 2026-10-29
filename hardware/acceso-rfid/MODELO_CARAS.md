# Modelo de reconocimiento de caras (XIAO ESP32-S3 Sense + Edge Impulse)

La XIAO revisa la cámara cada 0.7 s con un clasificador de imágenes entrenado en
Edge Impulse. Si ve a un socio 3 cuadros seguidos con 85 % de seguridad o más,
se lo dice a GTWeb (`POST /api/lectores/rostro`). El servidor revisa su
membresía con las mismas reglas que el tag y, si puede pasar, le ordena a la
LILYGO abrir. Después la XIAO toma 3 fotos, que quedan en ese pase del historial.

## Antes de empezar: consentimiento

Una cara es un **dato personal sensible** (dato biométrico, Ley Federal de
Protección de Datos Personales). Solo se entrena con personas que den su
consentimiento por escrito: los 5 integrantes del equipo, y para la clase
"desconocido", compañeros que también acepten. Las fotos del entrenamiento se
quedan en el proyecto privado de Edge Impulse; GymTrack no las guarda.

## 1. Crear el proyecto

En [edgeimpulse.com](https://edgeimpulse.com) crea un proyecto llamado
**GymTrackRostros**. La librería que exporta se llamará
`GymTrackRostros_inferencing.h`.

## 2. Juntar las fotos

Toma las fotos **con la misma XIAO, montada donde va a quedar en la puerta**:
así el modelo aprende con esa cámara, esa luz y esa distancia.

- Conecta la XIAO a Edge Impulse: carga el firmware de Edge Impulse para
  "Seeed XIAO ESP32S3 Sense" y corre `edge-impulse-daemon` (Data acquisition →
  Connect a device). También se pueden subir fotos del celular, pero el modelo
  sale peor porque la cámara es otra.
- Una clase por persona, **con su primer nombre como está en GymTrack**:
  `chema`, `valeria`, `yael`, `eduardo`, `edwin`.
- Dos clases más:
  - `desconocido`: otras personas (que hayan aceptado).
  - `nadie`: la puerta vacía, sin personas.
- **60 a 100 fotos por clase**, variando:
  - la distancia (40 a 80 cm) y el ángulo (de frente, un poco de lado, arriba y abajo);
  - la luz (día, noche con foco, contraluz);
  - con y sin lentes, gorra o cubrebocas si los suelen usar.
- Deja que Edge Impulse separe 80 % para entrenar y 20 % para probar.

## 3. Diseñar el impulse

- **Image data**: 96 × 96, *Resize mode: Fit shortest axis* (recorta al centro,
  igual que el firmware).
- **Processing block**: Image, color RGB.
- **Learning block**: Transfer Learning (Images), **MobileNetV2 96x96 0.35**.
- Entrena con 30 a 50 épocas y *data augmentation* activado.

## 4. Revisar que funcione

- En la pantalla de entrenamiento: la **exactitud** y la **matriz de
  confusión**. Esas dos capturas son la evidencia del modelo para el profe.
  Busquen más de 90 %.
- En *Model testing*: prueba con las fotos que no se usaron para entrenar.
- Si dos personas se confunden, junta más fotos de ellas y vuelve a entrenar.

## 5. Ponerlo en la XIAO

1. *Deployment* → **Arduino library**, con el compilador EON y el modelo
   cuantizado (int8). Descarga el `.zip`.
2. Arduino IDE → Sketch → Include Library → **Add .ZIP Library**.
3. En `xiao_camara/modelo.h` cambia la primera línea por tu librería:
   `#include <GymTrackRostros_inferencing.h>`.
4. Sube `xiao_camara.ino` (placa XIAO_ESP32S3, PSRAM: OPI PSRAM). La primera
   compilación del modelo tarda varios minutos.

## 6. Probar

- Monitor serie de la XIAO, comando **`c`**: muestra qué ve el modelo en ese
  momento y con qué seguridad.
- Párate frente a la cámara: debe salir `CARA chema (93 %)`, la respuesta del
  servidor (`ENTRADA Chema`) y en la LILYGO `ABRIR Chema`.
- En el panel, el historial dice "Reconocido por su cara (93 %)" con sus fotos.
- Edwin y Eduardo tienen la membresía vencida: su cara da **NEGADO** y una
  alerta con fotos, igual que con su tag.
- Si abre con quien no debe, sube `UMBRAL_CARA` en `modelo.h` (por ejemplo a
  0.92). Si casi nunca reconoce, bájalo o junta más fotos.

## Límites (para decirlos en la presentación)

- Es un clasificador de la imagen completa, no un sistema biométrico
  certificado: una foto impresa de un socio podría engañarlo. Para un
  gimnasio real haría falta detección de "persona viva".
- Se mitiga con el umbral alto, los 3 cuadros seguidos y que cada entrada por
  cara queda con sus fotos en el historial para que el dueño la revise.
- El tag sigue funcionando igual; la cara es una forma más de entrar.
