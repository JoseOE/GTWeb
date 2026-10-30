# Modelo de comandos de voz (XIAO ESP32-S3 Sense + Edge Impulse)

Con el botón de la XIAO presionado se graba la voz. Al soltarlo, un modelo
entrenado en Edge Impulse decide qué se dijo:

| Palabra | Qué hace |
|---|---|
| **entrada** | prende el LED |
| **salida** | apaga el LED |
| otra cosa o poca seguridad | 3 parpadeos; el LED se queda como estaba |

Cada comando se manda a GTWeb (`POST /api/lectores/voz`): sale en el historial
de la puerta como "Voz" y el panel muestra si el LED está prendido o apagado.

## Conexión
- `xiao_deteccion` ya no usa botón: escucha todo el tiempo (ver `UMBRAL_SONIDO` en `modelo_voz.h`). El botón entre **D0** y **GND** solo lo usa `grabar_voz` para grabar muestras.
- LED: el naranja de la placa. Opcional, uno más visible en **D1** con 220 Ω a GND.
- El cable de la LILYGO a D1 ya no se usa: quítalo.

## 1. Grabar las muestras (`grabar_voz`)
1. Sube `grabar_voz/grabar_voz.ino` a la XIAO (placa XIAO_ESP32S3, PSRAM: OPI PSRAM).
2. En el monitor sale `http://<IP>/`: ábrelo en la PC (en la misma red).
3. Elige la clase en la página y graba con el botón: mantenlo, di la palabra y
   suéltalo. La página descarga cada grabación sola como `entrada.001.wav`,
   `entrada.002.wav`...
4. Clases y cantidad (más es mejor):
   - `entrada`: 50 o más.
   - `salida`: 50 o más.
   - `ruido`: 50 o más, presionando el botón **sin hablar** (el ruido normal del
     lugar, música, gente) y también diciendo otras palabras que no son comandos.
     Sin esta clase, el modelo confunde cualquier sonido con "entrada" o "salida".
5. Varía todo: quién habla (que graben los 5), el tono, la velocidad, la
   distancia al micrófono, con ruido y sin ruido.

## 2. Crear el proyecto y subir los audios
1. En [edgeimpulse.com](https://edgeimpulse.com) crea un proyecto, por ejemplo
   **GymTrackVoz** (la librería se llamará `GymTrackVoz_inferencing.h`).
2. *Data acquisition* → *Upload data*:
   - selecciona todos los `.wav`;
   - *Upload into category*: **Automatically split between training and testing**;
   - *Label*: **Infer from filename**.
3. Revisa que haya cantidades parecidas en las 3 clases.

## 3. Diseñar el impulse (*Create impulse*)
1. **Time series data**:
   - *Window size*: **1000 ms**;
   - *Window increase*: **500 ms**;
   - *Frequency*: **16000 Hz**;
   - *Zero-pad data*: activado (las grabaciones cortas se rellenan con silencio).
2. **Processing block**: **Audio (MFCC)** (el estándar para voz).
3. **Learning block**: **Classification**.
4. *Save Impulse*.

## 4. Generar las características (*MFCC*)
1. Deja los parámetros por defecto → *Save parameters*.
2. *Generate features*. En el *Feature explorer* las 3 clases deberían verse
   como grupos separados.

## 5. Entrenar (*Classifier*)
1. *Number of training cycles*: **100**; *Learning rate*: **0.005**.
2. Activa *Data augmentation* (añade ruido y enmascara pedazos: el modelo
   aprende a no depender de una grabación perfecta).
3. *Save & train*. Revisa la **exactitud** y la **matriz de confusión** (esas
   capturas son la evidencia para el profe). Busquen más de 90 %.
4. *Model testing* → *Classify all*: prueba con los audios que no se usaron
   para entrenar.

## 6. Ponerlo en la XIAO (`xiao_deteccion`)
1. *Deployment* → **Arduino library**, con el compilador EON y el modelo
   cuantizado (int8) → *Build*. Descarga el `.zip`.
2. Arduino IDE → Sketch → Include Library → **Add .ZIP Library**.
3. En `xiao_deteccion/modelo_voz.h` cambia la primera línea por tu librería:
   `#include <GymTrackVoz_inferencing.h>`.
4. Sube `xiao_deteccion/xiao_deteccion.ino`. La primera compilación del modelo tarda
   varios minutos.

## 7. Probar
- Mantén el botón, di "entrada" y suéltalo: el LED se prende. En el monitor sale
  el porcentaje de cada clase y `panel: LED encendido`.
- En el panel (Accesos RFID) aparece "Comando de voz (XIAO): LED encendido" y el
  comando en el historial.
- Si obedece a palabras que no son comandos, sube `UMBRAL_VOZ` en
  `modelo_voz.h` o graba más `ruido`. Si casi nunca obedece, bájalo o graba más
  muestras de la palabra.
- Con la LILYGO en LTE o 2G, la XIAO se conecta a la red de la LILYGO y el
  comando llega al panel por el módem.
