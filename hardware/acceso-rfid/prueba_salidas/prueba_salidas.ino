// Prueba de los LEDs (GPIO 18, 19, 21) y del buzzer pasivo (GPIO 22) de la
// LILYGO, sin YRM ni internet. Sirve para revisar las conexiones.
//
// Monitor serie a 115200 (con "New Line" o "No line ending"), escribe un número:
//   1  entrada: LED verde y 1 pitido
//   2  salida:  LED azul y 2 pitidos
//   3  negado:  LED rojo y 3 pitidos
//   4  buzzer: escala de tonos (si suena, el buzzer y su conexión están bien)
//   5  todo seguido (1, 2 y 3)
//
// Placa: "ESP32 Wrover Module".

#define LED_VERDE 18
#define LED_AZUL  19
#define LED_ROJO  21
#define BUZZER    22
#define BUZZER_HZ 2700   // el mismo tono que usa el lector

// El buzzer es pasivo: suena con un tono, no con voltaje fijo.
void pitido(int ms, int hz = BUZZER_HZ) {
  tone(BUZZER, hz);
  delay(ms);
  noTone(BUZZER);
  pinMode(BUZZER, OUTPUT);
  digitalWrite(BUZZER, LOW);
}

void senal(const char* nombre, int led, int pitidos) {
  Serial.printf("%s: LED en GPIO %d y %d pitido(s)\n", nombre, led, pitidos);
  digitalWrite(led, HIGH);
  for (int i = 0; i < pitidos; i++) { pitido(80); delay(80); }
  delay(1200);
  digitalWrite(led, LOW);
}

void setup() {
  Serial.begin(115200);
  delay(500);
  pinMode(LED_VERDE, OUTPUT); pinMode(LED_AZUL, OUTPUT);
  pinMode(LED_ROJO, OUTPUT);  pinMode(BUZZER, OUTPUT);
  digitalWrite(LED_VERDE, LOW); digitalWrite(LED_AZUL, LOW);
  digitalWrite(LED_ROJO, LOW);  digitalWrite(BUZZER, LOW);
  Serial.println("\n=== Prueba de LEDs y buzzer ===");
  Serial.println("1 entrada · 2 salida · 3 negado · 4 escala del buzzer · 5 todo");
  Serial.println("Con nada pulsado los 3 LEDs deben quedar APAGADOS por completo.");
}

void loop() {
  if (!Serial.available()) return;
  char c = Serial.read();
  if (c == '1') senal("Entrada", LED_VERDE, 1);
  if (c == '2') senal("Salida", LED_AZUL, 2);
  if (c == '3') senal("Negado", LED_ROJO, 3);
  if (c == '4') {
    Serial.println("Buzzer: escala de 1 a 4 kHz");
    for (int hz = 1000; hz <= 4000; hz += 500) pitido(200, hz);
  }
  if (c == '5') {
    senal("Entrada", LED_VERDE, 1);
    senal("Salida", LED_AZUL, 2);
    senal("Negado", LED_ROJO, 3);
  }
}
