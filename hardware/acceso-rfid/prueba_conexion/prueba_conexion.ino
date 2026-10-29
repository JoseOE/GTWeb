// Prueba de conexión del lector de acceso: LILYGO T-SIM7070G + YRM1004.
//
// Confirma, en este orden: LEDs y buzzer, que el YRM responde por el UART
// (versión de hardware), que acepta el cambio de potencia y que lee tags.
// No usa red. Comandos por el monitor serie (115200):
//   +  subir la potencia 1 dBm      -  bajarla 1 dBm
//   l  repetir la prueba de LEDs y buzzer
//
// Placa: "ESP32 Wrover Module". El servo se agrega después; por ahora solo LEDs.

#define RFID_RX    32   // llega del TXD del YRM
#define RFID_TX    33   // va al RXD del YRM
#define LED_VERDE  18
#define LED_AZUL   19
#define LED_ROJO   21
#define BUZZER     22

HardwareSerial yrm(2);
int potenciaDbm = 15;

// ------------------------------------------------------------
// Protocolo del YRM (chip M100):
// BB | tipo | cmd | largo (2) | datos | checksum | 7E
// checksum = byte bajo de la suma de tipo, cmd, largo y datos.
// ------------------------------------------------------------
void enviar(uint8_t cmd, const uint8_t* datos, uint16_t n) {
  uint8_t cs = cmd + (n >> 8) + (n & 0xFF);
  yrm.write(0xBB); yrm.write(0x00); yrm.write(cmd);
  yrm.write(n >> 8); yrm.write(n & 0xFF);
  for (uint16_t i = 0; i < n; i++) { yrm.write(datos[i]); cs += datos[i]; }
  yrm.write(cs); yrm.write(0x7E);
}

bool leerByte(uint8_t& b, uint32_t limite) {
  while (!yrm.available()) {
    if (millis() > limite) return false;
  }
  b = yrm.read();
  return true;
}

// Devuelve el largo de los datos; -1 si no llegó nada, -2 si la trama vino mal.
int leerTrama(uint8_t& tipo, uint8_t& cmd, uint8_t* datos, int max, uint32_t esperaMs) {
  uint32_t limite = millis() + esperaMs;
  uint8_t b;
  do {
    if (!leerByte(b, limite)) return -1;
  } while (b != 0xBB);

  uint8_t largoH, largoL;
  if (!leerByte(tipo, limite) || !leerByte(cmd, limite) ||
      !leerByte(largoH, limite) || !leerByte(largoL, limite)) return -1;
  int n = (largoH << 8) | largoL;
  if (n > max) return -2;

  uint8_t cs = tipo + cmd + largoH + largoL;
  for (int i = 0; i < n; i++) {
    if (!leerByte(datos[i], limite)) return -1;
    cs += datos[i];
  }
  uint8_t csRecibido, fin;
  if (!leerByte(csRecibido, limite) || !leerByte(fin, limite)) return -1;
  if (fin != 0x7E || cs != csRecibido) return -2;
  return n;
}

void vaciarEntrada() {
  while (yrm.available()) yrm.read();
}

// ------------------------------------------------------------
// Comandos del YRM
// ------------------------------------------------------------
bool pedirVersion() {
  const uint8_t p[] = {0x00};
  vaciarEntrada();
  enviar(0x03, p, 1);
  uint8_t tipo, cmd, datos[64];
  int n = leerTrama(tipo, cmd, datos, sizeof(datos), 500);
  if (n <= 1 || cmd != 0x03) return false;
  Serial.print("YRM responde, version: ");
  for (int i = 1; i < n; i++) Serial.write(datos[i]);
  Serial.println();
  return true;
}

bool fijarPotencia(int dbm) {
  uint16_t valor = dbm * 100;
  const uint8_t p[] = {(uint8_t)(valor >> 8), (uint8_t)(valor & 0xFF)};
  vaciarEntrada();
  enviar(0xB6, p, 2);
  uint8_t tipo, cmd, datos[8];
  int n = leerTrama(tipo, cmd, datos, sizeof(datos), 500);
  bool ok = n == 1 && cmd == 0xB6 && datos[0] == 0x00;
  Serial.printf("Potencia %d dBm: %s\n", dbm, ok ? "OK" : "sin confirmar");
  return ok;
}

// Un inventario: imprime cada tag y devuelve cuántos hubo.
int inventario() {
  vaciarEntrada();
  enviar(0x22, nullptr, 0);
  int tags = 0;
  uint8_t tipo, cmd, datos[64];
  while (true) {
    int n = leerTrama(tipo, cmd, datos, sizeof(datos), 150);
    if (n == -1) break;                       // ya no llegó nada más
    if (n == -2) { Serial.println("Trama con error (revisa GND o alimentacion)"); continue; }
    if (tipo == 0x01 && cmd == 0xFF) break;   // "ningún tag"
    if (tipo != 0x02 || cmd != 0x22 || n < 5) continue;

    int8_t rssi = (int8_t)datos[0];
    uint16_t pc = (datos[1] << 8) | datos[2];
    int largoEpc = ((pc >> 11) & 0x1F) * 2;   // el PC dice cuántas palabras trae el EPC
    if (3 + largoEpc + 2 > n) continue;

    Serial.print("TAG  EPC=");
    for (int i = 0; i < largoEpc; i++) Serial.printf("%02X", datos[3 + i]);
    Serial.printf("  RSSI=%d dBm\n", rssi);
    tags++;
  }
  return tags;
}

// ------------------------------------------------------------
// Actuadores
// ------------------------------------------------------------
void pitido(int ms) {
  digitalWrite(BUZZER, HIGH); delay(ms); digitalWrite(BUZZER, LOW);
}

void probarLeds() {
  Serial.println("Prueba: verde, azul, rojo y pitido");
  int leds[] = {LED_VERDE, LED_AZUL, LED_ROJO};
  for (int led : leds) { digitalWrite(led, HIGH); delay(400); digitalWrite(led, LOW); }
  pitido(150);
}

// ------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Prueba de conexion: T-SIM7070G + YRM1004 ===");

  pinMode(LED_VERDE, OUTPUT); pinMode(LED_AZUL, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);  pinMode(BUZZER, OUTPUT);
  probarLeds();

  yrm.begin(115200, SERIAL_8N1, RFID_RX, RFID_TX);
  delay(300);
  if (!pedirVersion()) {
    Serial.println("El YRM NO responde: revisa GND comun, TX/RX cruzados, EN y los 5 V.");
    digitalWrite(LED_ROJO, HIGH);
    return;
  }
  fijarPotencia(potenciaDbm);
  Serial.println("Acerca un tag. Comandos: + / - (potencia), l (LEDs)");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'l') probarLeds();
    if (c == '+' && potenciaDbm < 26) fijarPotencia(++potenciaDbm);
    if (c == '-' && potenciaDbm > 5)  fijarPotencia(--potenciaDbm);
  }

  if (inventario() > 0) {
    digitalWrite(LED_VERDE, HIGH);
    pitido(40);
    delay(150);
    digitalWrite(LED_VERDE, LOW);
  }
  delay(250);
}
