/*
  MAX30100_Vitals_Threshold.ino
  Phase 4 — GY-MAX30100 Forehead Placement, Filtering & Vitals Threshold

  Builds on Phase 1's basic fingertip test by adding a health-alert
  flag for abnormal heart rate / SpO2. Runs standalone on Nano #1
  before Phase 6 wires it into the buzzer/vibration outputs.

  Wiring: SDA -> A4, SCL -> A5, VCC -> 5V (or 3.3V per module), GND -> GND

  Placement note: for the real helmet build this sensor mounts at the
  forehead/temple padding (not fingertip) — see the sensor reference
  doc for light-blocking and pressure notes. Use a fingertip for this
  bench test first to confirm the code/library works.

  Expected result: Serial Monitor (9600 baud) prints heart rate, SpO2,
  and a HEALTH_ALERT flag (0/1) once per second, flipping to 1 if HR
  or SpO2 fall outside the configured safe range.
*/

#include <Wire.h>
#include "MAX30100_PulseOximeter.h"

PulseOximeter pox;
float lastHR = 0, lastSpO2 = 0;
bool vitalsReady = false;

// Safe ranges — tune after physical testing
#define HR_MIN 50
#define HR_MAX 120
#define SPO2_MIN 90

void onBeatDetected() {
  Serial.println("Beat detected!");
}

void setup() {
  Serial.begin(9600);
  Serial.print("Initializing MAX30100...");
  if (!pox.begin()) {
    Serial.println("FAILED");
    while (1);
  } else {
    Serial.println("SUCCESS");
  }
  pox.setOnBeatDetectedCallback(onBeatDetected);
}

unsigned long lastReport = 0;

void loop() {
  pox.update();
  if (pox.getSpO2() > 0) {
    lastHR = pox.getHeartRate();
    lastSpO2 = pox.getSpO2();
    vitalsReady = true;
  }

  if (millis() - lastReport > 1000) {
    lastReport = millis();
    bool healthAlert = vitalsReady && (lastHR < HR_MIN || lastHR > HR_MAX || lastSpO2 < SPO2_MIN);

    Serial.print("HR:"); Serial.print((int)lastHR);
    Serial.print(" bpm  SpO2:"); Serial.print((int)lastSpO2);
    Serial.print("%  => HEALTH_ALERT:"); Serial.println(healthAlert ? 1 : 0);
  }
}
