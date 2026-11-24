// Prueba del buzzer (GPIO 22) y del servo (GPIO 23) de la LILYGO, sin nada más.
// Sirve para saber si falla la conexión o el programa del lector.
//
// Monitor serie a 115200 (con "New Line" o "No line ending"), escribe un número:
//   1  buzzer con voltaje fijo 1 s        (buzzer ACTIVO de 2 patas)
//   2  buzzer con tono de 2.7 kHz 1 s     (buzzer PASIVO)
//   3  buzzer "al revés" 1 s: pin en BAJO (módulo de 3 patas que suena con LOW)
//   4  servo a 0°      5  servo a 90°     6  servo a 180°
//   7  parpadeo en GPIO 22 y 23 (pon un LED con 220 Ω en cada pin para ver si el pin funciona)
//
// Placa: "ESP32 Wrover Module". Núcleo ESP32 3.x.

#define BUZZER 22
#define SERVO  23
#define SERVO_BITS 14

void servoGrados(int grados) {
  uint32_t us = 500 + (uint32_t)constrain(grados, 0, 180) * 2000 / 180;
  ledcAttach(SERVO, 50, SERVO_BITS);
  ledcWrite(SERVO, us * ((1UL << SERVO_BITS) - 1) / 20000);
  Serial.printf("Servo a %d° (pulso de %lu µs). Debe girar y quedarse ahí.\n", grados, (unsigned long)us);
}

void setup() {
  Serial.begin(115200);
  delay(500);
  pinMode(BUZZER, OUTPUT);
  digitalWrite(BUZZER, LOW);
  Serial.println("\n=== Prueba de buzzer (GPIO 22) y servo (GPIO 23) ===");
  Serial.println("1 fijo · 2 tono · 3 al revés · 4/5/6 servo 0/90/180 · 7 parpadeo de pines");
}

void loop() {
  if (!Serial.available()) return;
  char c = Serial.read();
  if (c == '1') {
    Serial.println("Buzzer: voltaje fijo 1 s");
    pinMode(BUZZER, OUTPUT); digitalWrite(BUZZER, HIGH); delay(1000); digitalWrite(BUZZER, LOW);
  } else if (c == '2') {
    Serial.println("Buzzer: tono de 2.7 kHz 1 s");
    tone(BUZZER, 2700); delay(1000); noTone(BUZZER);
    pinMode(BUZZER, OUTPUT); digitalWrite(BUZZER, LOW);
  } else if (c == '3') {
    Serial.println("Buzzer: pin en BAJO 1 s (y luego queda en ALTO para que calle)");
    pinMode(BUZZER, OUTPUT); digitalWrite(BUZZER, LOW); delay(1000); digitalWrite(BUZZER, HIGH);
  } else if (c == '4') {
    servoGrados(0);
  } else if (c == '5') {
    servoGrados(90);
  } else if (c == '6') {
    servoGrados(180);
  } else if (c == '7') {
    Serial.println("Parpadeo de GPIO 22 y 23, 5 veces");
    ledcDetach(SERVO);
    pinMode(BUZZER, OUTPUT); pinMode(SERVO, OUTPUT);
    for (int i = 0; i < 5; i++) {
      digitalWrite(BUZZER, HIGH); digitalWrite(SERVO, HIGH); delay(400);
      digitalWrite(BUZZER, LOW);  digitalWrite(SERVO, LOW);  delay(400);
    }
  }
}
