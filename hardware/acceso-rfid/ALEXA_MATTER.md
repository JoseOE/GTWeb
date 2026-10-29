# Foco y Alexa con dos ESP32-C6 (Matter)

```
 LILYGO ──cable serial──► C6 del foco (c6_foco) ──WiFi local (UDP)──► C6 de la Alexa (c6_alexa)
 (tag: entra/sale/negado)  · foco Matter (relevador)                  · 6 sensores de contacto Matter
                           · prende con la 1ª entrada                 · la Alexa reacciona con rutinas
                           · apaga cuando ya no queda nadie
```

La LILYGO le manda a la C6 del foco una línea por evento: `E|Chema|3` (entró,
quedan 3), `S|Chema|0` (salió, no queda nadie), `N|Edwin|-1` (negado) o
`A|Visitante|-1` (abierta desde el panel). El número de personas dentro lo
calcula GTWeb.

## Conexiones
| De | A |
|---|---|
| LILYGO **GPIO 13** | C6 del foco **GPIO 18** (RX) |
| LILYGO **GND** | C6 del foco **GND** |
| C6 del foco **GPIO 19** | relevador del foco (o un LED con 220 Ω para la demo) |

- Cada C6 se alimenta por su USB-C.
- Si tu placa C6 no tiene el GPIO 18 o el 19, cambia `RX_LILYGO` y `RELE` en `c6_foco.ino`.
- Si tu relevador se activa en bajo, cambia `RELE_ACTIVO` a `LOW`.
- ⚠ Para un foco de 127 V usa un módulo de relevador para corriente alterna, dentro
  de una caja, y que la conexión a la corriente la haga alguien que sepa.

## Subir los programas
- Placa: tu ESP32-C6 ("ESP32C6 Dev Module", "XIAO_ESP32C6"...).
- Herramientas → **Partition Scheme: "Huge APP (3MB No OTA/1MB SPIFFS)"** (Matter es grande).
- La primera vez: Herramientas → **Erase All Flash Before Sketch Upload: Enabled**.
- `secrets.h` (ya está en cada carpeta) solo se usa si la C6 no empareja por
  Bluetooth; las dos C6 tienen que quedar en la **misma red WiFi**.

## Emparejar con la Alexa (Matter)
Necesitas un Echo compatible con Matter (Echo Dot 4/5, Echo 4, Echo Show...).
1. Sube el programa y abre el monitor serie: sale `Código para la app Alexa: 1234-567-8901`
   y una URL con el QR.
2. App Alexa → **Dispositivos → + → Agregar dispositivo → Matter** → escanea el QR
   (abre la URL en la PC) o escribe el código.
3. Repite con la otra C6.
4. En la app quedan:
   - **un foco** (c6_foco). Ponle "Foco del gimnasio";
   - **6 sensores de contacto** (c6_alexa). Ponles nombre en este orden:
     1 Chema, 2 Valeria, 3 Yael, 4 Eduardo, 5 Edwin, 6 Acceso denegado.
5. Para volver a emparejar: mantén **BOOT 5 s** en la C6 y bórrala de la app.

## Las rutinas (app Alexa → Más → Rutinas → +)
| Cuando... | Acción "Alexa dice" |
|---|---|
| el sensor **Chema** se **abra** | Bienvenido Chema |
| el sensor **Chema** se **cierre** | Hasta luego Chema |
| (lo mismo para Valeria, Yael, Eduardo y Edwin) | |
| el sensor **Acceso denegado** se **abra** | Acceso denegado |

En "Desde", elige el Echo que debe hablar. La Alexa tarda unos segundos en
reaccionar.

Si tu Alexa no muestra los 6 sensores (no todas aceptan varios en un mismo
dispositivo Matter), deja en `SOCIOS` solo a los que vayas a usar en la demo.

## Probar sin pasar tags
En el monitor de la LILYGO, `n` simula un tag desconocido: la C6 del foco
muestra `LILYGO: N ...`, la de la Alexa `Acceso denegado: sensor abierto` y la
Alexa dice "Acceso denegado". Luego pasa el tag de Chema: se prende el foco y
la Alexa dice "Bienvenido Chema".
