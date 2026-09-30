/*
  MAX30100_Test.ino (GY-30100 module)
  Phase 1 — Component Verification

  Requires library: "MAX30100lib" by OXullo Intersecans
  (Arduino IDE -> Sketch -> Include Library -> Manage Libraries)

  Wiring:
    SDA -> A4
    SCL -> A5
    VCC -> 5V (or 3.3V per module rating)
    GND -> GND

  Test procedure: place a fingertip gently but firmly over the
  sensor (covers both LED and photodetector, no gaps) and hold
  still for 5-10 seconds.

  Expected result: "Initializing MAX30100... SUCCESS", then
  "Beat detected!" messages with heart rate (bpm) and SpO2 (%)
  settling into a normal resting range (~60-100 bpm, ~95-100% SpO2).
*/

#include <Wire.h>
#include "MAX30100_PulseOximeter.h"

PulseOximeter pox;

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

void loop() {
  pox.update();

  static uint32_t lastReport = 0;
  if (millis() - lastReport > 1000) {
    lastReport = millis();
    Serial.print("Heart rate: ");
    Serial.print(pox.getHeartRate());
    Serial.print(" bpm  /  SpO2: ");
    Serial.print(pox.getSpO2());
    Serial.println(" %");
  }
}
