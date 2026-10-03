/*
  Buzzer_Test.ino
  Phase 1 — Component Verification

  Tests both buzzers (active type, confirmed by an on/off click
  pattern with a simple digitalWrite HIGH/LOW — no tone() needed).

  Wiring:
    Buzzer 1 (obstacle alert)   -> D8, GND
    Buzzer 2 (gas/health alert) -> D9, GND

  Expected result: each buzzer clicks on for 500ms, off for 500ms,
  repeating. Buzzer 1 and Buzzer 2 alternate.
*/

#define BUZZER1 8
#define BUZZER2 9

void setup() {
  pinMode(BUZZER1, OUTPUT);
  pinMode(BUZZER2, OUTPUT);
}

void loop() {
  digitalWrite(BUZZER1, HIGH);
  delay(500);
  digitalWrite(BUZZER1, LOW);
  delay(500);

  digitalWrite(BUZZER2, HIGH);
  delay(500);
  digitalWrite(BUZZER2, LOW);
  delay(500);
}
