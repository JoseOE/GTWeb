// Etapa 2 del lector de acceso: la lógica de la puerta, todavía sin red.
//
// Una lista fija de socios (los tags que tenemos) y, para cada uno, si está
// dentro o fuera del gimnasio. Al pasar el tag:
//   fuera + membresía activa   -> ENTRADA  (LED verde, 1 pitido, abre)
//   fuera + membresía vencida  -> NEGADO   (LED rojo, 3 pitidos)
//   dentro                     -> SALIDA   (LED azul, 2 pitidos, abre; salir no se valida)
//   tag que no está en la lista -> NEGADO  (LED rojo, 3 pitidos)
//
// En cada NEGADO se manda un pulso por DISPARO_CAMARA a la XIAO (D1), que
// toma 3 fotos y las guarda en su microSD.
//
// Un tag frente al lector se lee varias veces por segundo; solo cuenta de
// nuevo cuando se alejó REARME_MS, y entre una entrada y su salida tienen que
// pasar MIN_ENTRE_EVENTOS_MS (en la puerta real serán 2 minutos).
//
// Comandos por el monitor serie (115200):
//   e  ver quién está dentro (aforo)     r  reiniciar (todos fuera)
//   +  subir la potencia 1 dBm           -  bajarla 1 dBm
//   n  simular un acceso negado (dispara la cámara sin pasar un tag)
//
// Placa: "ESP32 Wrover Module". Mismas conexiones que prueba_conexion, más
// el cable de GPIO 13 a la XIAO.

#define RFID_RX         32   // llega del TXD del YRM
#define RFID_TX         33   // va al RXD del YRM
#define LED_VERDE       18
#define LED_AZUL        19
#define LED_ROJO        21
#define BUZZER          22
#define DISPARO_CAMARA  13   // va a D1 de la XIAO
// Reservados para la versión final, sin conectar todavía:
//   servo de la puerta en GPIO 23; botón "Solicitar acceso" en GPIO 34 (con 10 kΩ a 3V3).

const uint32_t REARME_MS            = 3000;
const uint32_t MIN_ENTRE_EVENTOS_MS = 10000;
const uint32_t SENAL_MS             = 1500;

HardwareSerial yrm(2);
int potenciaDbm = 15;

struct Socio {
  const char* epc;
  const char* nombre;
  bool membresiaActiva;
  bool dentro;
  uint32_t ultimoEvento;    // millis() de la última entrada o salida; 0 = nunca
  uint32_t ultimaLectura;   // millis() de la última vez que el lector lo vio
};

Socio socios[] = {
  {"E2004712C1A060265132010B", "Tarjeta (al corriente)",   true},
  {"E28069150000502D9DF80D65", "Sticker 1 (al corriente)", true},
  {"E2827802000000000DFFC5F9", "Sticker 2 (vencida)",      false},
};
const int NUM_SOCIOS = sizeof(socios) / sizeof(socios[0]);

String ultimoDesconocido;
uint32_t ultimoDesconocidoMs = 0;

// ------------------------------------------------------------
// Protocolo del YRM (chip M100), igual que en prueba_conexion:
// BB | tipo | cmd | largo (2) | datos | checksum | 7E
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

// ------------------------------------------------------------
// Señales y puerta
// ------------------------------------------------------------
void pitido(int ms) {
  digitalWrite(BUZZER, HIGH); delay(ms); digitalWrite(BUZZER, LOW);
}

// Enciende el LED SENAL_MS con los pitidos al principio.
void senal(int led, int pitidos) {
  digitalWrite(led, HIGH);
  uint32_t inicio = millis();
  for (int i = 0; i < pitidos; i++) { pitido(80); delay(80); }
  while (millis() - inicio < SENAL_MS) delay(10);
  digitalWrite(led, LOW);
}

// El servo llega después; por ahora solo se avisa.
void abrirPuerta() {
  Serial.println("         [puerta] abrir 3 s");
}

// Pulso corto: la XIAO lo ve en D1 y toma las fotos.
void dispararCamara() {
  Serial.println("         [camara] tomar 3 fotos");
  digitalWrite(DISPARO_CAMARA, HIGH);
  delay(100);
  digitalWrite(DISPARO_CAMARA, LOW);
}

// ------------------------------------------------------------
// Lógica de acceso
// ------------------------------------------------------------
Socio* buscar(const String& epc) {
  for (int i = 0; i < NUM_SOCIOS; i++) {
    if (epc.equals(socios[i].epc)) return &socios[i];
  }
  return nullptr;
}

void procesarTag(const String& epc, int8_t rssi) {
  uint32_t ahora = millis();
  Socio* s = buscar(epc);

  if (!s) {
    bool mismoQueAntes = epc == ultimoDesconocido && ahora - ultimoDesconocidoMs < REARME_MS;
    ultimoDesconocido = epc;
    ultimoDesconocidoMs = ahora;
    if (mismoQueAntes) return;
    Serial.printf("NEGADO   tag desconocido  EPC=%s  RSSI=%d\n", epc.c_str(), rssi);
    dispararCamara();
    senal(LED_ROJO, 3);
    return;
  }

  // Sigue frente al lector desde la última lectura: ya se contó.
  bool seguiaCerca = s->ultimaLectura != 0 && ahora - s->ultimaLectura < REARME_MS;
  s->ultimaLectura = ahora;
  if (seguiaCerca) return;

  if (s->ultimoEvento != 0 && ahora - s->ultimoEvento < MIN_ENTRE_EVENTOS_MS) {
    Serial.printf("ignorado %s: muy pronto desde su %s\n", s->nombre, s->dentro ? "entrada" : "salida");
    return;
  }

  if (s->dentro) {
    s->dentro = false;
    s->ultimoEvento = ahora;
    Serial.printf("SALIDA   %s  RSSI=%d\n", s->nombre, rssi);
    abrirPuerta();
    senal(LED_AZUL, 2);
  } else if (s->membresiaActiva) {
    s->dentro = true;
    s->ultimoEvento = ahora;
    Serial.printf("ENTRADA  %s  RSSI=%d\n", s->nombre, rssi);
    abrirPuerta();
    senal(LED_VERDE, 1);
  } else {
    Serial.printf("NEGADO   %s: membresia vencida\n", s->nombre);
    dispararCamara();
    senal(LED_ROJO, 3);
  }
}

// Un inventario; cada tag leído pasa por procesarTag.
void inventario() {
  vaciarEntrada();
  enviar(0x22, nullptr, 0);
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

    char epc[2 * 32 + 1];
    for (int i = 0; i < largoEpc && i < 32; i++) sprintf(epc + 2 * i, "%02X", datos[3 + i]);
    procesarTag(String(epc), rssi);
  }
}

void mostrarAforo() {
  int dentro = 0;
  Serial.println("--- Estado ---");
  for (int i = 0; i < NUM_SOCIOS; i++) {
    Serial.printf("  %-26s %s%s\n", socios[i].nombre,
                  socios[i].dentro ? "DENTRO" : "fuera",
                  socios[i].membresiaActiva ? "" : "  (membresia vencida)");
    if (socios[i].dentro) dentro++;
  }
  Serial.printf("Aforo: %d de %d\n", dentro, NUM_SOCIOS);
}

void reiniciar() {
  for (int i = 0; i < NUM_SOCIOS; i++) {
    socios[i].dentro = false;
    socios[i].ultimoEvento = 0;
  }
  Serial.println("Todos fuera.");
}

// ------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Etapa 2: puerta sin red (T-SIM7070G + YRM1004) ===");

  pinMode(LED_VERDE, OUTPUT); pinMode(LED_AZUL, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);  pinMode(BUZZER, OUTPUT);
  pinMode(DISPARO_CAMARA, OUTPUT);
  digitalWrite(DISPARO_CAMARA, LOW);

  yrm.begin(115200, SERIAL_8N1, RFID_RX, RFID_TX);
  delay(300);
  if (!pedirVersion()) {
    Serial.println("El YRM NO responde: revisa GND comun, TX/RX cruzados, EN y los 5 V.");
    digitalWrite(LED_ROJO, HIGH);
    return;
  }
  fijarPotencia(potenciaDbm);
  mostrarAforo();
  Serial.println("Listo. Comandos: e (estado), r (reiniciar), n (simular negado), + / - (potencia)");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'e') mostrarAforo();
    if (c == 'r') reiniciar();
    if (c == 'n') {
      Serial.println("NEGADO   (simulado desde el monitor)");
      dispararCamara();
      senal(LED_ROJO, 3);
    }
    if (c == '+' && potenciaDbm < 26) fijarPotencia(++potenciaDbm);
    if (c == '-' && potenciaDbm > 5)  fijarPotencia(--potenciaDbm);
  }

  inventario();
  delay(250);
}
