# Lector de acceso RFID (LILYGO T-SIM7070G + YRM1004)

La puerta del gimnasio: lee el tag UHF de cada socio, le pregunta a GTWeb si puede pasar y responde con LEDs, un buzzer y un servo que abre la puerta. Se conecta por WiFi y, si no hay, por datos móviles.

| Programa | Para qué |
|---|---|
| `prueba_conexion/` | Etapa 1: que la LILYGO hable con el YRM (versión y potencia). |
| `prueba_salidas/` | Revisar los LEDs y el buzzer, sin YRM ni internet. |
| `prueba_puerta/` | Etapa 2: LEDs y buzzer con una lista fija, sin internet. |
| `lector_wifi/` | El lector completo, conectado a GTWeb. |

## Conexiones

| Pieza | Pin de la pieza | LILYGO |
|---|---|---|
| YRM1004 | TXD | GPIO 32 |
| | RXD | GPIO 33 |
| | EN | 3V3 |
| | VCC | **5 V externos** (el YRM pide más corriente de la que da la LILYGO) |
| | GND | GND común |
| LED verde (entrada, acceso permitido) | ánodo, con resistencia de 220 Ω | GPIO 18 |
| LED azul (salida, registro, "sin internet") | ánodo, con 220 Ω | GPIO 19 |
| LED rojo (negado) | ánodo, con 220 Ω | GPIO 21 |
| Los 3 LEDs | cátodo (pata corta) | GND común |
| Buzzer pasivo | + | GPIO 22 |
| | − | GND común |
| Servo (SG90 / MG90S) | señal (naranja o amarillo) | GPIO 23 |
| | + (rojo) | **5 V externos** (el 5V de un Arduino conectado por USB, o una fuente) |
| | − (café o negro) | GND común |

- **Tierra común:** el GND de la fuente de 5 V, el del YRM y el de la LILYGO van juntos. Sin eso el YRM manda tramas con error.
- **Fuente externa:** solo alimenta al YRM. No lo conectes al 3V3 de la LILYGO: no da la corriente suficiente.
- **Buzzer:** es pasivo, así que el programa lo hace sonar con un tono de 2.7 kHz (con voltaje fijo no suena). Un buzzer activo también funciona.
- **Servo de la puerta:** abre (90°) al permitir una entrada o una salida y cierra solo (0°) a los 3 s; ya cerrado deja de recibir pulsos para que no zumbe. Se alimenta con 5 V externos, **con su GND unido al de la LILYGO** (sin tierra común solo vibra o cruje). Si se reinicia o tiembla al moverse, pon un capacitor de 100–470 µF entre 5 V y GND cerca del servo. Los ángulos y el tiempo están al inicio de la sección 3 de `lector_wifi.ino`.

## Internet

- **WiFi:** hasta 3 redes en `secrets.h`. Solo **2.4 GHz**: el ESP32 no ve las redes de 5 GHz. Con el hotspot del teléfono, activa "Maximizar compatibilidad" (iPhone) o la banda de 2.4 GHz (Android). No sirven las redes con usuario de alumno ni página de inicio de sesión.
- **Datos móviles:** SIM con datos (Telcel, AT&T o Movistar) y su APN en `secrets.h`. El SIM7070G usa **LTE-M / NB-IoT** y, si no hay, **2G**. No tiene 4G "normal" ni 5G. Con una batería 18650 puesta funciona mejor, porque el módem pide picos de corriente.
- **Automático (lo normal):** primero WiFi; si no hay, datos móviles. Si el WiFi se cae más de 45 s, pasa a datos, y en datos busca el WiFi cada 2 min para regresar. Cada cambio reinicia la placa, que tarda unos segundos.
- **Sin internet:** decide con la lista de tags que guarda en su memoria y el LED azul destella cada 3 s.

## Subirlo

1. Arduino IDE con la placa **ESP32 Wrover Module** y las librerías **TinyGSM** y **ArduinoHttpClient**.
2. En `lector_wifi/`, copia `secrets.example.h` como `secrets.h` y pon:
   - tu WiFi;
   - el servidor (`https://gtweb.onrender.com`);
   - la llave del lector, que da el panel al crearlo en Accesos RFID;
   - el APN.

   `secrets.h` no se sube a GitHub.
3. Súbelo y abre el monitor serie a 115200. Al arrancar se prenden los 3 LEDs uno por uno y suena un pitido.

## Monitor serie

| Tecla | Qué hace |
|---|---|
| `a` | Red automática (WiFi y, si no hay, datos) |
| `w` · `l` · `g` | Solo WiFi · solo LTE-M · solo 2G |
| `r` | Estado de la red, del servidor y de la lista local |
| `p` | Probar las señales: entrada (puerta, verde, 1 pitido), salida (azul, 2) y negado (rojo, 3) |
| `o` | Abrir la puerta (servo) 3 s |
| `n` | Simular un tag desconocido (crea una alerta en el panel) |
| `+` · `-` | Subir o bajar 1 dBm la potencia del YRM |

## Probar sin pasar tarjetas

En el panel, **Mis Usuarios → Simular entrada** hace como si el socio pasara su tarjeta. Con el lector conectado, una entrada permitida prende el LED verde y suena el pitido de verdad. Ver "Simular entrada" en el README principal.
