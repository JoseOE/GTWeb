# Lector de acceso RFID (YRM1004 + LILYGO T-SIM7070G)

El lector va en la puerta del gimnasio. Cuando un socio acerca su tarjeta, el lector
pregunta a GTWeb por HTTPS si puede pasar y responde con LED, buzzer y servo.
Si GTWeb no contesta en 1.5 s, decide con la lista de tarjetas que guarda en memoria
y sube los accesos después.

| Carpeta | Qué es |
| :--- | :--- |
| `prueba_lector/` | Etapa 1: leer etiquetas y ajustar la potencia. Sin red. |
| `pruebas-host/` | Prueba del protocolo YRM en la computadora, sin placa. |

## Etapa 0 · Pines y material

### Pines que ya usa la placa (no tocar)

Tomados de `utilities.h` de LilyGO ([LilyGo-Modem-Series](https://github.com/Xinyuan-LilyGO/LilyGo-Modem-Series), sección `LILYGO_SIM7070G`):

| GPIO | Uso en la placa |
| :--- | :--- |
| 4 | PWRKEY del módem SIM7070G |
| 25 | DTR del módem |
| 26 / 27 | RX / TX del módem (Serial1) |
| 12 | LED de la placa (enciende en LOW) |
| 2, 13, 14, 15 | Tarjeta microSD (MISO, CS, SCK, MOSI) |
| 35 | Voltaje de la batería (ADC) |
| 36 | Voltaje del panel solar (ADC) |
| 16, 17 | PSRAM del ESP32-WROVER-E |
| 6 – 11 | Memoria flash interna |

También evitar **0 y 5**: el ESP32 los lee al arrancar y un LED o servo ahí puede
impedir que encienda. **34 y 39** solo sirven como entrada.

### Pines propuestos

| GPIO | Conectar a | Notas |
| :--- | :--- | :--- |
| 32 | TXD del YRM | Serial2 RX (igual que el código de Edwin) |
| 33 | RXD del YRM | Serial2 TX |
| 18 | LED verde (+ resistencia 220 Ω) | Acceso permitido |
| 19 | LED azul (+ 220 Ω) | Modo registro / sin conexión |
| 21 | LED rojo (+ 220 Ω) | Acceso negado |
| 22 | Buzzer | Si es activo y pide más de ~12 mA, ponerlo con un transistor (2N2222 + 1 kΩ) |
| 23 | Señal del servo | El servo se alimenta con 5 V aparte, **no** del pin 3.3 V |

Libres para después: 34 y 39 (solo entrada, p. ej. un botón de "modo registro").

### Alimentación

- **YRM1004:** Edwin lo conectó a 3.3 V y funcionó. A potencia alta el módulo puede
  pedir varios cientos de mA; si se reinicia la placa o las lecturas fallan al subir la
  potencia, alimentarlo con 5 V (revisar en su hoja de datos que acepte 5 V en VCC;
  su UART es de 3.3 V, así que los pines 32/33 se conectan directo).
- **Servo (SG90 o similar):** puede pedir hasta ~650 mA al arrancar. Usar 5 V del
  USB o de una fuente aparte, con **GND en común** con la LILYGO.
- Antes de conectar, revisar en la serigrafía de la placa qué pin dice 5V/VBUS.

### Material

- [ ] LILYGO T-SIM7070G y cable USB-C de datos
- [ ] Módulo YRM1004 con su antena, tarjetas y pegatinas UHF
- [ ] LED verde, azul y rojo + 3 resistencias de 220 Ω
- [ ] Buzzer (activo, 3.3 – 5 V) y, si hace falta, transistor 2N2222 + resistencia 1 kΩ
- [ ] Servo SG90 (5 V)
- [ ] Protoboard, cables Dupont macho-hembra
- [ ] Fuente de 5 V para el servo (o USB aparte)

## Etapa 1 · Probar el lector (`prueba_lector/`)

1. En Arduino IDE instalar las placas **esp32 by Espressif** y elegir
   **ESP32 Wrover Module** (o "ESP32 Dev Module" con PSRAM activada).
2. Abrir `prueba_lector/prueba_lector.ino` y subirlo.
3. Abrir el Monitor Serie a **115200** con fin de línea **Nueva línea**.
4. Escribir `g` para ver la potencia actual y `p 10` para empezar en 10 dBm.
5. Acercar la tarjeta y alejarla poco a poco. Cada segundo sale algo como:

   ```
   --- último segundo ---
   E2004712C1A060265132010B   9 lect/s  RSSI -52 dBm
   ```

6. Anotar a qué distancia las lecturas caen a 0 y repetir con otras potencias
   (`p 5`, `p 15`, `p 20`…).

**Objetivo:** la potencia más baja que lea bien a 30 cm y ya no lea a ~80 cm.
Anotar aquí el resultado para usarlo en la Etapa 2:

| Potencia (dBm) | Alcance tarjeta (cm) | Alcance pegatina (cm) | RSSI a 30 cm |
| :--- | :--- | :--- | :--- |
| | | | |

### Diferencias con el código de Edwin

`yrm.h` lee el puerto byte por byte y usa el campo de **largo** de la trama para saber
dónde termina, además de revisar el **checksum**. El código anterior buscaba el
primer `0x7E`, y si un EPC trae ese byte en medio la tarjeta se leería mal.

### Probar el protocolo sin placa

```bash
cd hardware/acceso-rfid/pruebas-host
g++ -std=c++11 -Wall -o yrm_test yrm_test.cpp && ./yrm_test
```

Usa las tramas reales que Edwin capturó (tarjeta, pegatina y "nada cerca").
