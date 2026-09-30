/*
  MQ2_Gas_Test.ino
  Phase 1 — Component Verification

  Wiring:
    MQ-2 AO -> A0
    VCC -> 5V, GND -> GND

  Notes:
    - MQ-2 needs 2-5 minutes to warm up (internal heater) before
      readings respond properly to gas.
    - Clean-air baseline is typically ~200-400 raw ADC value, not
      stuck at 0 or 1023.
    - To test response: cup the sensor and release a small amount
      of lighter gas nearby (3-5 seconds) — value should jump by
      at least 100-200 points above baseline.

  Expected result: Serial Monitor (9600 baud) prints a changing raw
  value (0-1023) that rises when exposed to gas.
*/

#define MQ_PIN A0

void setup() {
  Serial.begin(9600);
}

void loop() {
  int rawValue = analogRead(MQ_PIN);
  Serial.print("MQ-2 raw value: ");
  Serial.println(rawValue);
  delay(500);
}
