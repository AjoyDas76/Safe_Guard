/*
  MQ_Gas_Threshold.ino
  Phase 3 — MQ Gas Sensor Threshold & Alert Logic

  Builds on Phase 1's raw-reading test by adding a clean-air baseline
  calibration (averaged at startup) and a relative threshold-based
  gas alert flag. Runs standalone on Nano #1 before Phase 6 wires it
  into the buzzer/vibration outputs.

  Wiring: MQ-2 AO -> A0, VCC -> 5V, GND -> GND

  IMPORTANT: let the sensor warm up for 2-5 minutes (powered on,
  undisturbed) before relying on the baseline calibration below —
  calibrating too early gives an unstable, meaningless baseline.

  Expected result: Serial Monitor (9600 baud) prints the baseline
  once at startup, then raw value + a GAS flag (0/1) that flips to 1
  when the reading rises more than GAS_MARGIN above baseline.
*/

#define MQ_PIN A0
int mqBaseline = 0;
#define GAS_MARGIN 150 // tune after physical testing

void setup() {
  Serial.begin(9600);
  Serial.println("Calibrating MQ-2 baseline (clean air)...");
  long sum = 0;
  for (int i = 0; i < 20; i++) {
    sum += analogRead(MQ_PIN);
    delay(50);
  }
  mqBaseline = sum / 20;
  Serial.print("Baseline: ");
  Serial.println(mqBaseline);
}

void loop() {
  int mqRaw = analogRead(MQ_PIN);
  bool gasAlert = (mqRaw > mqBaseline + GAS_MARGIN);

  Serial.print("MQ raw:"); Serial.print(mqRaw);
  Serial.print("  Baseline:"); Serial.print(mqBaseline);
  Serial.print("  => GAS:"); Serial.println(gasAlert ? 1 : 0);

  delay(500);
}
