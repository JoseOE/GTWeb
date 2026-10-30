# Foco y Alexa con dos ESP32-C6 (Matter)

```
 XIAO (voz) ─pulso─►┐
 Tag RFID ─────────► LILYGO ──cable serial──► C6 puente (c6_puente) ──WiFi (UDP)──► C6 de la Alexa (c6_alexa)
                     decide y avisa            · sensor Matter                       · sensores Matter por socio,
                                                 "Gimnasio ocupado"                    Voz y Acceso denegado
                                                         │                                      │
                                                         ▼                                      ▼
                                          Rutina: prender/apagar la lámpara      Rutinas: la Alexa habla
```

No hay relevador: la lámpara es un foco inteligente que la Alexa ya controla.
Las dos C6 usan Matter.

| Qué pasa | La Alexa dice | Foco |
|---|---|---|
| Entra Chema con su tag | Bienvenido Chema | se prende si era el primero |
| Sale Chema con su tag | Hasta luego Chema | se apaga si ya no queda nadie |
| Tag negado (desconocido o vencido) | Acceso denegado | igual |
| "entrada" por voz | Entrada por comando de voz | se prende si era el primero |
| "salida" por voz | Salida por comando de voz | se apaga si ya no queda nadie |

La LILYGO le manda a la C6 puente una línea por evento: `E|Chema|3` (entró,
quedan 3), `S|Chema|0` (salió, no queda nadie), `N|Edwin|-1` (negado),
`E|Voz|-1` / `S|Voz|-1` (voz) y `D||2` (solo el aforo: tras un comando de voz,
la LILYGO le pregunta a GTWeb cuántos quedan).

## Antes de empezar
- La **lámpara Steren** agregada a tu Alexa (app/skill de Steren): que funcione
  "Alexa, prende la lámpara".
- Un **Echo compatible con Matter** (Echo Dot 4/5, Echo 4, Echo Show...).
- Una red **WiFi de 2.4 GHz** para el Echo y las dos C6 (la misma).

## Conexiones
| De | A |
|---|---|
| LILYGO **GPIO 13** | C6 puente **GPIO 18** (RX) |
| LILYGO **GND** | C6 puente **GND** |

- C6 puente: **cerca de la LILYGO** (los une el cable). Se alimenta por su USB-C.
- C6 de la Alexa: **donde quieras**, sin cables, solo su USB-C a un cargador.
- Si tu placa C6 no tiene el GPIO 18, cambia `RX_LILYGO` en `c6_puente.ino`.

## Subir los programas
En Windows con el Control inteligente de aplicaciones activado, el compilador de
la C6 está bloqueado: se compila en Ubuntu (WSL) con `compilar_c6.sh` y se sube
con `subir_c6.ps1` (los pasos están en esos archivos). En una PC sin ese
bloqueo basta el Arduino IDE con:
- Placa: tu ESP32-C6 ("ESP32C6 Dev Module", "XIAO_ESP32C6"...).
- **Partition Scheme: "Huge APP (3MB No OTA/1MB SPIFFS)"** (Matter es grande).
- La primera vez: **Erase All Flash Before Sketch Upload: Enabled**.
- **USB CDC On Boot: Enabled** (para ver el monitor serie).

## Emparejar con la Alexa (Matter)
1. Monitor serie de la C6 a 115200, RST: sale `Código para la app Alexa: 1234-567-8901`
   y una URL con el QR.
2. App Alexa → **Dispositivos → + → Agregar dispositivo → Otro → Matter** → escanea
   el QR (abre la URL en la PC) o escribe el código.
3. Repite con la otra C6.
4. En la app quedan:
   - de la C6 puente, **1 sensor de contacto**: ponle **Gimnasio ocupado**;
   - de la C6 de la Alexa, **7 sensores de contacto**, en este orden:
     1 Chema, 2 Valeria, 3 Yael, 4 Eduardo, 5 Edwin, 6 Voz, 7 Acceso denegado.
5. Para volver a emparejar: mantén **BOOT 5 s** en la C6 y bórrala de la app.

## Las rutinas (app Alexa → Más → Rutinas → +)
| Cuando... | Acción |
|---|---|
| **Gimnasio ocupado** se **abra** | Dispositivo inteligente → lámpara Steren → **encender** |
| **Gimnasio ocupado** se **cierre** | Dispositivo inteligente → lámpara Steren → **apagar** |
| **Chema** se **abra** | Alexa dice: Bienvenido Chema |
| **Chema** se **cierre** | Alexa dice: Hasta luego Chema |
| (lo mismo para Valeria, Yael, Eduardo y Edwin) | |
| **Voz** se **abra** | Alexa dice: Entrada por comando de voz |
| **Voz** se **cierre** | Alexa dice: Salida por comando de voz |
| **Acceso denegado** se **abra** | Alexa dice: Acceso denegado |

En "Alexa dice", elige en "Desde" el Echo que debe hablar. La Alexa tarda unos
segundos en reaccionar.

Si tu Alexa no muestra los 7 sensores (no todas aceptan varios en un mismo
dispositivo Matter), deja en `SOCIOS` solo a los que vayas a usar en la demo.

## Probar
- Monitor de la LILYGO, `n` (tag desconocido): la C6 puente muestra `LILYGO: N ...`,
  la de la Alexa `Acceso denegado: sensor abierto` y la Alexa dice "Acceso denegado".
- Tag de Chema con el gimnasio vacío: la C6 puente muestra `Gimnasio ocupado: ABIERTO`,
  se prende la lámpara y la Alexa dice "Bienvenido Chema".
- Sale Chema y no queda nadie: `Gimnasio ocupado: cerrado`, se apaga la lámpara.
- "entrada" a la XIAO: la Alexa dice "Entrada por comando de voz"; 3 s después la
  C6 puente recibe `D||n` con cuántos quedan dentro.
