# Sube a una ESP32-C6 el .bin que compiló compilar_c6.sh (en Ubuntu).
# Usa el esptool que ya trae el Arduino IDE (Windows no lo bloquea).
#
# Uso, en PowerShell, dentro de la carpeta hardware\acceso-rfid:
#   .\subir_c6.ps1 -Puerto COM5 -Programa c6_foco
#   .\subir_c6.ps1 -Puerto COM6 -Programa c6_alexa
# -Borrar: borra toda la memoria antes (la primera vez, o para quitar el
#          emparejamiento Matter).
# El puerto sale en Arduino IDE → Herramientas → Puerto, o en el Administrador de dispositivos.
param(
    [Parameter(Mandatory = $true)][string]$Puerto,
    [Parameter(Mandatory = $true)][ValidateSet('c6_foco', 'c6_alexa')][string]$Programa,
    [switch]$Borrar
)

$bin = Join-Path $PSScriptRoot "build_c6\$Programa.bin"
if (-not (Test-Path $bin)) {
    Write-Error "No existe $bin. Primero corre compilar_c6.sh en Ubuntu."
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
    Write-Host "Borrando toda la memoria de la C6 en $Puerto..."
    & $esptool.FullName --chip esp32c6 --port $Puerto erase_flash
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Write-Host "Subiendo $Programa a la C6 en $Puerto..."
& $esptool.FullName --chip esp32c6 --port $Puerto --baud 460800 write_flash 0x0 $bin
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nListo. Abre el monitor serie (Arduino IDE, 115200) en $Puerto para ver el código de emparejamiento."
}
exit $LASTEXITCODE
