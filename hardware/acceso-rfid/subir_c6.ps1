# Sube a una ESP32-C6 lo que compiló compilar_c6.sh (en Ubuntu).
# Usa el esptool que ya trae el Arduino IDE (Windows no lo bloquea).
#
# Uso, en PowerShell, dentro de la carpeta hardware\acceso-rfid:
#   Primera vez (placa nueva o para quitar el emparejamiento Matter):
#     .\subir_c6.ps1 -Puerto COM5 -Programa c6_puente -Borrar
#   Actualizar el programa SIN perder el emparejamiento con la Alexa:
#     .\subir_c6.ps1 -Puerto COM5 -Programa c6_puente
#
# -Borrar: borra toda la memoria y sube la imagen completa (.bin). Se pierde el
#          emparejamiento: hay que volver a agregarla a la Alexa.
# Sin -Borrar: sube solo el programa (.app.bin, en 0x10000) y no toca la parte
#          de la memoria donde Matter guarda el emparejamiento.
# El puerto sale en el Administrador de dispositivos (use el USB "ESP32C6").
param(
    [Parameter(Mandatory = $true)][string]$Puerto,
    [Parameter(Mandatory = $true)][ValidateSet('c6_puente', 'c6_alexa')][string]$Programa,
    [switch]$Borrar
)

$completo = Join-Path $PSScriptRoot "build_c6\$Programa.bin"
$programa = Join-Path $PSScriptRoot "build_c6\$Programa.app.bin"
$archivo = if ($Borrar) { $completo } else { $programa }
if (-not (Test-Path $archivo)) {
    Write-Error "No existe $archivo. Primero corre compilar_c6.sh en Ubuntu."
    exit 1
}

$esptool = Get-ChildItem "$env:LOCALAPPDATA\Arduino15\packages\esp32\tools\esptool_py" -Recurse -Filter esptool.exe |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $esptool) {
    Write-Error "No encontré esptool.exe: instala el paquete esp32 en el Arduino IDE."
    exit 1
}

Write-Host "esptool: $($esptool.FullName)"
if ($Borrar) {
    Write-Host "Borrando toda la memoria de la C6 en $Puerto (se pierde el emparejamiento)..."
    & $esptool.FullName --chip esp32c6 --port $Puerto erase_flash
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Subiendo $Programa completo a la C6 en $Puerto..."
    & $esptool.FullName --chip esp32c6 --port $Puerto --baud 460800 write_flash 0x0 $completo
} else {
    Write-Host "Actualizando solo el programa de $Programa en $Puerto (el emparejamiento se queda)..."
    & $esptool.FullName --chip esp32c6 --port $Puerto --baud 460800 write_flash 0x10000 $programa
}
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nListo. Abre el monitor serie (Arduino IDE, 115200) en $Puerto."
}
exit $LASTEXITCODE
