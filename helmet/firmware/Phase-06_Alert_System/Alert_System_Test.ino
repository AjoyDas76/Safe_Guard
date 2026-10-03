/*
  Alert_System_Test.ino
  Phase 6 — Alert System Integration

  Tests the ALERT OUTPUT logic (which buzzer fires for which alert
  type, plus the vibration motor) in isolation from the real sensors
  — alert flags are simulated via Serial input instead of actual
  sensor readings, so the buzzer/vibration behavior can be verified
  on the bench before Phase 9 wires it to real sensor data.

  Wiring:
    Buzzer 1 (obstacle alert)   -> D8
    Buzzer 2 (gas/health alert) -> D9
    Vibration motor             -> D13 -> transistor (AMS1117-powered,
                                   not direct 5V — see Phase 1 notes)

  Usage: open Serial Monitor (9600 baud, "No line ending" or any —
  single characters are read as typed) and send:
    o  -> toggle obstacle alert on
    g  -> toggle gas alert on
    h  -> toggle health alert on
    c  -> clear all alerts

  Expected result:
    - 'o' -> Buzzer 1 + vibration motor turn on
    - 'g' or 'h' -> Buzzer 2 + vibration motor turn on
    - 'c' -> everything off
*/

#define BUZZER1 8
#define BUZZER2 9
#define VIBRATION_PIN 13

bool obstacleAlert = false, gasAlert = false, healthAlert = false;

void setup() {
  Serial.begin(9600);
  pinMode(BUZZER1, OUTPUT);
  pinMode(BUZZER2, OUTPUT);
  pinMode(VIBRATION_PIN, OUTPUT);
  Serial.println("Alert System Test — send: o=obstacle, g=gas, h=health, c=clear");
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'o') obstacleAlert = true;
    else if (c == 'g') gasAlert = true;
    else if (c == 'h') healthAlert = true;
    else if (c == 'c') { obstacleAlert = false; gasAlert = false; healthAlert = false; }

    Serial.print("OBST:"); Serial.print(obstacleAlert);
    Serial.print(" GAS:"); Serial.print(gasAlert);
    Serial.print(" HEALTH:"); Serial.println(healthAlert);
  }

  digitalWrite(BUZZER1, obstacleAlert ? HIGH : LOW);
  digitalWrite(BUZZER2, (gasAlert || healthAlert) ? HIGH : LOW);
  digitalWrite(VIBRATION_PIN, (obstacleAlert || gasAlert || healthAlert) ? HIGH : LOW);
}
