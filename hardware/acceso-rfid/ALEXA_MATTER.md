# Foco y Alexa con dos ESP32-C6 (Matter)

```
 XIAO (voz) ─pulso─►┐
 Tag RFID ─────────► LILYGO ──cable serial──► C6 del foco (c6_foco) ──WiFi (UDP)──► C6 de la Alexa (c6_alexa)
                     decide y avisa            · foco Matter + relevador            · 7 sensores de contacto Matter
                                               · prende con la 1ª persona           · la Alexa habla con rutinas
                                               · apaga cuando ya no queda nadie
```

| Qué pasa | La Alexa dice | Foco |
|---|---|---|
| Entra Chema con su tag | Bienvenido Chema | se prende si era el primero |
| Sale Chema con su tag | Hasta luego Chema | se apaga si ya no queda nadie |
| Tag negado (desconocido o vencido) | Acceso denegado | igual |
| "entrada" por voz | Entrada por comando de voz | se prende si era el primero |
| "salida" por voz | Salida por comando de voz | se apaga si ya no queda nadie |

La LILYGO le manda a la C6 del foco una línea por evento: `E|Chema|3` (entró,
quedan 3), `S|Chema|0` (salió, no queda nadie), `N|Edwin|-1` (negado),
`E|Voz|-1` / `S|Voz|-1` (voz) y `D||2` (solo el aforo: tras un comando de voz,
la LILYGO le pregunta a GTWeb cuántos quedan y se lo pasa al foco).

## Conexiones
| De | A |
|---|---|
| LILYGO **GPIO 13** | C6 del foco **GPIO 18** (RX) |
| LILYGO **GND** | C6 del foco **GND** |
| C6 del foco **GPIO 19** | **IN** del módulo de relevador |
| **5V** (cargador USB o el Arduino) | **VCC** del módulo de relevador |
| **GND** de esa misma fuente | **GND** del relevador y **GND** de la C6 del foco |

- Si tu placa C6 no tiene el GPIO 18 o el 19, cambia `RX_LILYGO` y `RELE` en `c6_foco.ino`.
- Si el relevador se activa en bajo (muchos módulos de 1 canal), cambia `RELE_ACTIVO` a `LOW`.
- ⚠ Para un foco de 127 V usa un módulo de relevador para corriente alterna, dentro
  de una caja, y que la conexión a la corriente la haga alguien que sepa. Para la
  demo basta un LED con 220 Ω en lugar del relevador.

## Alimentación (cómo se comunican)
- **C6 del foco:** tiene que estar **cerca de la LILYGO**, porque la une un cable
  (señal + GND). Se alimenta por su USB-C (un cargador de celular). El Arduino
  puede darle 5 V al relevador, pero no hace falta para que se comuniquen.
- **C6 de la Alexa:** puede estar **en cualquier lugar** con WiFi: no lleva cables
  a nada, solo su USB-C a un cargador. Habla con la otra C6 por la red WiFi.
- Las **dos C6 en la misma red WiFi** (la del Echo, de 2.4 GHz).

## Subir los programas
En Windows con el Control inteligente de aplicaciones activado el compilador de
la C6 está bloqueado: se compila en Ubuntu (WSL) con `compilar_c6.sh` y se sube
con `subir_c6.ps1` (los pasos están en esos archivos). En una PC sin ese bloqueo
basta el Arduino IDE con:
- Placa: tu ESP32-C6 ("ESP32C6 Dev Module", "XIAO_ESP32C6"...).
- **Partition Scheme: "Huge APP (3MB No OTA/1MB SPIFFS)"** (Matter es grande).
- La primera vez: **Erase All Flash Before Sketch Upload: Enabled**.
- **USB CDC On Boot: Enabled** (para ver el monitor serie).

## Emparejar con la Alexa (Matter)
Necesitas un Echo compatible con Matter (Echo Dot 4/5, Echo 4, Echo Show...).
1. Monitor serie de la C6 a 115200, RST: sale `Código para la app Alexa: 1234-567-8901`
   y una URL con el QR.
2. App Alexa → **Dispositivos → + → Agregar dispositivo → Otro → Matter** → escanea
   el QR (abre la URL en la PC) o escribe el código.
3. Repite con la otra C6.
4. En la app quedan:
   - **un foco** (c6_foco). Ponle "Foco del gimnasio";
   - **7 sensores de contacto** (c6_alexa). Ponles nombre en este orden:
     1 Chema, 2 Valeria, 3 Yael, 4 Eduardo, 5 Edwin, 6 Voz, 7 Acceso denegado.
5. Para volver a emparejar: mantén **BOOT 5 s** en la C6 y bórrala de la app.

## Las rutinas (app Alexa → Más → Rutinas → +)
| Cuando... | Acción "Alexa dice" |
|---|---|
| el sensor **Chema** se **abra** | Bienvenido Chema |
| el sensor **Chema** se **cierre** | Hasta luego Chema |
| (lo mismo para Valeria, Yael, Eduardo y Edwin) | |
| el sensor **Voz** se **abra** | Entrada por comando de voz |
| el sensor **Voz** se **cierre** | Salida por comando de voz |
| el sensor **Acceso denegado** se **abra** | Acceso denegado |

En "Desde", elige el Echo que debe hablar. La Alexa tarda unos segundos en
reaccionar.

Si tu Alexa no muestra los 7 sensores (no todas aceptan varios en un mismo
dispositivo Matter), deja en `SOCIOS` solo a los que vayas a usar en la demo.

## Probar
- Monitor de la LILYGO, `n` (tag desconocido): la C6 del foco muestra `LILYGO: N ...`,
  la de la Alexa `Acceso denegado: sensor abierto` y la Alexa dice "Acceso denegado".
- Tag de Chema: se prende el foco y la Alexa dice "Bienvenido Chema".
- "entrada" a la XIAO: la Alexa dice "Entrada por comando de voz"; 3 s después la
  C6 del foco recibe `D||n` con cuántos quedan dentro.
