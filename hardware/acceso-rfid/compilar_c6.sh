#!/usr/bin/env bash
# Compila los dos programas de las ESP32-C6 (c6_puente y c6_alexa) en Ubuntu (WSL).
#
# En Windows, el "Control inteligente de aplicaciones" bloquea el compilador
# RISC-V de la C6. En Ubuntu se usa el compilador de Linux, que no lo revisa.
# El resultado son archivos .bin que se suben desde Windows con subir_c6.ps1.
#
# Uso (desde Ubuntu):
#   bash /mnt/c/Users/<tu-usuario>/Documents/Proyectos/Gym-Track/GTWeb/hardware/acceso-rfid/compilar_c6.sh [placa]
# placa: esp32c6 (ESP32C6 Dev Module, por defecto) o XIAO_ESP32C6.
# La primera vez descarga arduino-cli y las herramientas del ESP32 (~1.5 GB).
set -euo pipefail

PLACA="${1:-esp32c6}"
NUCLEO="esp32:esp32@3.3.11"          # la misma versión que el Arduino IDE
AQUI="$(cd "$(dirname "$0")" && pwd)"
SALIDA="$AQUI/build_c6"
CLI="$HOME/bin/arduino-cli"

# 1. arduino-cli (una sola vez).
if [ ! -x "$CLI" ]; then
  echo "== Instalando arduino-cli en ~/bin"
  mkdir -p "$HOME/bin"
  curl -fsSL https://raw.githubusercontent.com/arduino/arduino-cli/master/install.sh | BINDIR="$HOME/bin" sh
fi

# 2. El paquete del ESP32 (una sola vez).
if ! "$CLI" core list | grep -q "^esp32:esp32 *3.3.11"; then
  echo "== Instalando el paquete esp32 3.3.11 (tarda: son las herramientas de todos los ESP32)"
  "$CLI" config init --overwrite >/dev/null
  "$CLI" config add board_manager.additional_urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
  "$CLI" core update-index
  "$CLI" core install "$NUCLEO"
fi

# 3. Compilar. Matter necesita la partición grande; el USB CDC deja ver el monitor
# serie; DebugLevel=error muestra en el monitor los fallos internos de Matter.
OPCIONES="PartitionScheme=huge_app,DebugLevel=error"
[ "$PLACA" = "esp32c6" ] && OPCIONES="$OPCIONES,CDCOnBoot=cdc"
mkdir -p "$SALIDA"
for sketch in c6_puente c6_alexa; do
  if [ ! -f "$AQUI/$sketch/secrets.h" ]; then
    echo "Falta $sketch/secrets.h: copia secrets.example.h como secrets.h y pon tu WiFi." >&2
    exit 1
  fi
  echo "== Compilando $sketch para $PLACA"
  # Se compila en una carpeta de Ubuntu: arduino-cli cambia los permisos de lo
  # que copia (chmod) y eso no se puede en las carpetas de Windows (/mnt/c).
  # Después solo se copia el .bin a Windows.
  "$CLI" compile --fqbn "esp32:esp32:$PLACA:$OPCIONES" --output-dir "$HOME/build_c6/$sketch" "$AQUI/$sketch"
  cp "$HOME/build_c6/$sketch/$sketch.ino.merged.bin" "$SALIDA/$sketch.bin"
done

echo
echo "Listo. Los archivos para subir quedaron en:"
echo "  $SALIDA/c6_puente.bin"
echo "  $SALIDA/c6_alexa.bin"
echo "Ahora, en PowerShell de Windows:  .\\subir_c6.ps1 -Puerto COM5 -Programa c6_puente"
