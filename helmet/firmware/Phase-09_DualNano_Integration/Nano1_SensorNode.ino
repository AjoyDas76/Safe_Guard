/*
  Nano1_SensorNode.ino
  Phase 9 — Dual-Nano System Integration (Sensor Node)

  Realizes Phase 2 (Ultrasonic), Phase 3 (MQ-2 gas), Phase 4 (MAX30100
  vitals), and Phase 6 (alert system: buzzer + vibration motor) together
  on a single Nano, self-contained and offline-capable.

  Sends a summary line to Nano #2 every second:
    OBST:1,GAS:0,HR:78,SPO2:96

  Wiring — see Phase-09 README.md for the full table.

  NOTE: threshold values below (OBSTACLE_THRESHOLD_CM, GAS_MARGIN,
  heart rate / SpO2 ranges) are starting points — tune after physical
  testing on the actual helmet.
*/

#include <Wire.h>
#include "MAX30100_PulseOximeter.h"
#include <SoftwareSerial.h>

#define TRIG1 2
#define ECHO1 3
#define TRIG2 4
#define ECHO2 5
#define TRIG3 6
#define ECHO3 7
#define OBSTACLE_THRESHOLD_CM 100

#define MQ_PIN A0
int mqBaseline = 0;
#define GAS_MARGIN 150

#define BUZZER1 8
#define BUZZER2 9
#define VIBRATION_PIN 13

SoftwareSerial toNano2(10, 11); // RX, TX (only TX used)

PulseOximeter pox;
float lastHR = 0, lastSpO2 = 0;
bool vitalsReady = false;

void onBeatDetected() {}

long readDistance(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  long duration = pulseIn(echoPin, HIGH, 30000);
  if (duration == 0) return 999; // no echo -> treat as "far away"
  return duration * 0.034 / 2;
}

void setup() {
  Serial.begin(9600);
  toNano2.begin(9600);

  pinMode(TRIG1, OUTPUT); pinMode(ECHO1, INPUT);
  pinMode(TRIG2, OUTPUT); pinMode(ECHO2, INPUT);
  pinMode(TRIG3, OUTPUT); pinMode(ECHO3, INPUT);
  pinMode(BUZZER1, OUTPUT);
  pinMode(BUZZER2, OUTPUT);
  pinMode(VIBRATION_PIN, OUTPUT);

  long sum = 0;
  for (int i = 0; i < 20; i++) { sum += analogRead(MQ_PIN); delay(50); }
  mqBaseline = sum / 20; // clean-air baseline

  if (!pox.begin()) Serial.println("MAX30100 FAILED");
  pox.setOnBeatDetectedCallback(onBeatDetected);
}

unsigned long lastSend = 0;

void loop() {
  pox.update();
  if (pox.getSpO2() > 0) {
    lastHR = pox.getHeartRate();
    lastSpO2 = pox.getSpO2();
    vitalsReady = true;
  }

  long d1 = readDistance(TRIG1, ECHO1); delay(10);
  long d2 = readDistance(TRIG2, ECHO2); delay(10);
  long d3 = readDistance(TRIG3, ECHO3); delay(10);
  long minDist = min(d1, min(d2, d3));
  bool obstacleAlert = (minDist < OBSTACLE_THRESHOLD_CM);

  int mqRaw = analogRead(MQ_PIN);
  bool gasAlert = (mqRaw > mqBaseline + GAS_MARGIN);

  bool healthAlert = vitalsReady && (lastHR < 50 || lastHR > 120 || lastSpO2 < 90);

  digitalWrite(BUZZER1, obstacleAlert ? HIGH : LOW);
  digitalWrite(BUZZER2, (gasAlert || healthAlert) ? HIGH : LOW);
  digitalWrite(VIBRATION_PIN, (obstacleAlert || gasAlert || healthAlert) ? HIGH : LOW);

  if (millis() - lastSend > 1000) {
    lastSend = millis();
    String line = "OBST:" + String(obstacleAlert ? 1 : 0) +
                  ",GAS:" + String(gasAlert ? 1 : 0) +
                  ",HR:" + String((int)lastHR) +
                  ",SPO2:" + String((int)lastSpO2);
    toNano2.println(line);
  }
}
