/*
  Nano1_SensorNode_v3.ino
  Sensor node: 3x ultrasonic, MQ-2 gas, MAX30100 vitals, buzzers, vibration.

  v3 sends the FULL data every second to Nano #2 (SoftwareSerial TX = D11):

    OBST:1,GAS:0,HR:78,SPO2:96,DL:999,DF:34,DR:999,MQ:412,MB:380,OL:0,OF:1,OR:0

    OBST / GAS   overall alert flags (0/1) - same as before
    HR / SPO2    vitals (0 when no finger)
    DL DF DR     distance in cm: Left / Front / Right (999 = nothing in range)
    MQ           gas level (smoothed raw ADC value, 0-1023)
    MB           clean-air baseline (0 while the MQ-2 is still warming up)
    OL OF OR     1 = THAT sensor is under the obstacle threshold

  The buzzers still work exactly as before: any sensor under the threshold
  -> buzzer 1 + vibration; gas over threshold or bad vitals -> buzzer 2.

  SENSOR POSITIONS: set the pins below to match where each sensor is
  really mounted on the helmet (swap the numbers if left/right are wrong).

  Debug text is printed on USB Serial (9600) once per second.
  Thresholds are starting points - tune on the real helmet.
*/

#include <Wire.h>
#include "MAX30100_PulseOximeter.h"
#include <SoftwareSerial.h>

// ---- Ultrasonic sensor positions (edit pins to match your wiring) ----
#define TRIG_FRONT 2
#define ECHO_FRONT 3
#define TRIG_LEFT  4
#define ECHO_LEFT  5
#define TRIG_RIGHT 6
#define ECHO_RIGHT 7
#define OBSTACLE_THRESHOLD_CM 50
#define ECHO_TIMEOUT_US 6000UL   // ~1 m max range, keeps each read short

#define MQ_PIN A0
#define GAS_MARGIN 150           // ADC counts above the clean-air baseline
#define GAS_HYSTERESIS 40        // must fall this far below the margin to clear
#define GAS_WARMUP_MS 30000UL    // MQ-2 heater warm-up before taking baseline

#define BUZZER1 8
#define BUZZER2 9
#define VIBRATION_PIN 13

#define VITALS_STALE_MS 5000UL   // reading older than this = no finger
#define VITALS_SETTLE_MS 10000UL // vitals must be valid this long before alerting
#define HEALTH_BAD_SECONDS 3     // abnormal for this many seconds in a row

SoftwareSerial toNano2(10, 11); // RX, TX (only TX used)

PulseOximeter pox;
int lastHR = 0, lastSpO2 = 0;
unsigned long lastValidVitals = 0;
unsigned long validSince = 0;
byte healthBadCount = 0;
bool healthAlert = false;

int mqBaseline = 0;
float mqSmooth = 0;
bool gasReady = false;
bool gasAlert = false;
unsigned long gasStart = 0;

// index 0 = LEFT, 1 = FRONT, 2 = RIGHT
long dist[3] = {999, 999, 999};
byte obstCnt[3] = {0, 0, 0};
bool obst[3] = {false, false, false};
const char* SIDE_NAME[3] = {"LEFT", "FRONT", "RIGHT"};

unsigned long lastSend = 0;

void onBeatDetected() {}

// Wait without starving the MAX30100.
void waitWithUpdate(unsigned long ms) {
  unsigned long t = millis();
  while (millis() - t < ms) pox.update();
}

long readDistance(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  long duration = pulseIn(echoPin, HIGH, ECHO_TIMEOUT_US);
  if (duration == 0) return 999; // no echo -> "far away"
  return duration / 58;          // microseconds -> cm
}

void updateVitals() {
  pox.update();
  float hr = pox.getHeartRate();
  float sp = pox.getSpO2();
  if (hr >= 30 && hr <= 220 && sp >= 50 && sp <= 100) {
    lastHR = (int)hr;
    lastSpO2 = (int)sp;
    lastValidVitals = millis();
    if (validSince == 0) validSince = millis();
  }
}

bool vitalsFresh() {
  return lastValidVitals != 0 && (millis() - lastValidVitals) < VITALS_STALE_MS;
}

void calibrateGas() {
  long sum = 0;
  for (int i = 0; i < 20; i++) { sum += analogRead(MQ_PIN); waitWithUpdate(50); }
  mqBaseline = sum / 20;
  gasReady = true;
}

// Buzzer 1 (active, obstacle): beep-beep pattern.
unsigned long buzzer1LastToggle = 0;
bool buzzer1State = false;
#define BUZZER1_BEEP_INTERVAL 150

// Buzzer 2 (passive, gas/health): two-tone siren.
unsigned long buzzer2LastToggle = 0;
bool buzzer2High = false;
#define BUZZER2_TONE_LOW 600
#define BUZZER2_TONE_HIGH 1200
#define BUZZER2_SIREN_INTERVAL 400

void updateBuzzers(bool obstacleAlert, bool gasAl, bool healthAl) {
  if (obstacleAlert) {
    if (millis() - buzzer1LastToggle > BUZZER1_BEEP_INTERVAL) {
      buzzer1LastToggle = millis();
      buzzer1State = !buzzer1State;
      digitalWrite(BUZZER1, buzzer1State ? HIGH : LOW);
    }
  } else {
    digitalWrite(BUZZER1, LOW);
    buzzer1State = false;
  }

  if (gasAl || healthAl) {
    if (millis() - buzzer2LastToggle > BUZZER2_SIREN_INTERVAL) {
      buzzer2LastToggle = millis();
      buzzer2High = !buzzer2High;
      tone(BUZZER2, buzzer2High ? BUZZER2_TONE_HIGH : BUZZER2_TONE_LOW);
    }
  } else {
    noTone(BUZZER2);
    buzzer2High = false;
  }
}

void setup() {
  Serial.begin(9600);
  toNano2.begin(9600);

  pinMode(TRIG_LEFT, OUTPUT);  pinMode(ECHO_LEFT, INPUT);
  pinMode(TRIG_FRONT, OUTPUT); pinMode(ECHO_FRONT, INPUT);
  pinMode(TRIG_RIGHT, OUTPUT); pinMode(ECHO_RIGHT, INPUT);
  pinMode(BUZZER1, OUTPUT);
  pinMode(BUZZER2, OUTPUT);
  pinMode(VIBRATION_PIN, OUTPUT);

  if (!pox.begin()) Serial.println("MAX30100 FAILED");
  pox.setOnBeatDetectedCallback(onBeatDetected);
  // Optional: if HR/SpO2 stay noisy or saturated, try a lower LED current:
  // pox.setIRLedCurrent(MAX30100_LED_CURR_7_6MA);

  mqSmooth = analogRead(MQ_PIN);
  gasStart = millis(); // gas baseline is taken later, after warm-up
}

void loop() {
  updateVitals();

  if (!gasReady && millis() - gasStart >= GAS_WARMUP_MS) calibrateGas();

  dist[0] = readDistance(TRIG_LEFT,  ECHO_LEFT);  waitWithUpdate(10);
  dist[1] = readDistance(TRIG_FRONT, ECHO_FRONT); waitWithUpdate(10);
  dist[2] = readDistance(TRIG_RIGHT, ECHO_RIGHT); waitWithUpdate(10);

  bool obstacleAlert = false;
  for (int i = 0; i < 3; i++) {
    if (dist[i] < OBSTACLE_THRESHOLD_CM) { if (obstCnt[i] < 255) obstCnt[i]++; }
    else obstCnt[i] = 0;
    obst[i] = (obstCnt[i] >= 2); // 2 hits in a row, avoids single-echo glitches
    if (obst[i]) obstacleAlert = true;
  }

  int mqRaw = analogRead(MQ_PIN);
  mqSmooth = mqSmooth * 0.9 + mqRaw * 0.1;
  if (gasReady) {
    if (!gasAlert && mqSmooth > mqBaseline + GAS_MARGIN) gasAlert = true;
    else if (gasAlert && mqSmooth < mqBaseline + GAS_MARGIN - GAS_HYSTERESIS) gasAlert = false;
  }

  updateBuzzers(obstacleAlert, gasAlert, healthAlert);
  digitalWrite(VIBRATION_PIN, (obstacleAlert || gasAlert || healthAlert) ? HIGH : LOW);

  if (millis() - lastSend >= 1000) {
    lastSend = millis();

    if (!vitalsFresh()) { validSince = 0; lastHR = 0; lastSpO2 = 0; }
    bool vitalsOK = vitalsFresh() && validSince != 0 &&
                    (millis() - validSince) > VITALS_SETTLE_MS;
    if (vitalsOK && (lastHR < 50 || lastHR > 120 || lastSpO2 < 90)) {
      if (healthBadCount < 255) healthBadCount++;
    } else {
      healthBadCount = 0;
    }
    healthAlert = (healthBadCount >= HEALTH_BAD_SECONDS);

    char line[100];
    snprintf(line, sizeof(line),
             "OBST:%d,GAS:%d,HR:%d,SPO2:%d,DL:%ld,DF:%ld,DR:%ld,MQ:%d,MB:%d,OL:%d,OF:%d,OR:%d",
             obstacleAlert ? 1 : 0, gasAlert ? 1 : 0, lastHR, lastSpO2,
             dist[0], dist[1], dist[2], (int)mqSmooth, mqBaseline,
             obst[0] ? 1 : 0, obst[1] ? 1 : 0, obst[2] ? 1 : 0);
    toNano2.println(line);

    // Human-readable debug on USB Serial
    Serial.print("DIST L/F/R = ");
    Serial.print(dist[0]); Serial.print(" / ");
    Serial.print(dist[1]); Serial.print(" / ");
    Serial.print(dist[2]); Serial.print(" cm");
    Serial.print(" | GAS level="); Serial.print((int)mqSmooth);
    Serial.print(" base="); Serial.print(mqBaseline);
    Serial.print(gasReady ? "" : " (warming up)");
    Serial.print(" | HR="); Serial.print(lastHR);
    Serial.print(" SpO2="); Serial.print(lastSpO2);
    for (int i = 0; i < 3; i++) {
      if (obst[i]) {
        Serial.print(" | OBSTACLE "); Serial.print(SIDE_NAME[i]);
        Serial.print(" ("); Serial.print(dist[i]); Serial.print(" cm)");
      }
    }
    if (gasAlert) Serial.print(" | GAS ALERT");
    if (healthAlert) Serial.print(" | HEALTH ALERT");
    Serial.println();
  }
}
