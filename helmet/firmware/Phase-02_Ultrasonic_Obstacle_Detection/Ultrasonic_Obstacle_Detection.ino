/*
  Ultrasonic_Obstacle_Detection.ino
  Phase 2 — Ultrasonic Obstacle/Proximity Detection

  Builds on Phase 1's raw-distance test by adding sensor fusion
  (minimum distance across all three) and a threshold-based alert
  flag. Runs standalone on Nano #1 for isolated testing before
  Phase 6 wires it into the buzzer/vibration outputs, and before
  Phase 9 combines it with every other sensor.

  Wiring:
    Ultrasonic 1 (Front) -> Trig D2, Echo D3
    Ultrasonic 2 (Left)  -> Trig D4, Echo D5
    Ultrasonic 3 (Right) -> Trig D6, Echo D7

  Expected result: Serial Monitor (9600 baud) prints all three
  distances plus an OBST flag (0/1) that flips to 1 when the closest
  reading drops under OBSTACLE_THRESHOLD_CM.
*/

#define TRIG1 2
#define ECHO1 3
#define TRIG2 4
#define ECHO2 5
#define TRIG3 6
#define ECHO3 7
#define OBSTACLE_THRESHOLD_CM 50 // tune after physical testing

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
  pinMode(TRIG1, OUTPUT); pinMode(ECHO1, INPUT);
  pinMode(TRIG2, OUTPUT); pinMode(ECHO2, INPUT);
  pinMode(TRIG3, OUTPUT); pinMode(ECHO3, INPUT);
}

void loop() {
  long d1 = readDistance(TRIG1, ECHO1); delay(10);
  long d2 = readDistance(TRIG2, ECHO2); delay(10);
  long d3 = readDistance(TRIG3, ECHO3); delay(10);
  long minDist = min(d1, min(d2, d3));
  bool obstacleAlert = (minDist < OBSTACLE_THRESHOLD_CM);

  Serial.print("Front:"); Serial.print(d1);
  Serial.print("cm  Left:"); Serial.print(d2);
  Serial.print("cm  Right:"); Serial.print(d3);
  Serial.print("cm  => OBST:"); Serial.println(obstacleAlert ? 1 : 0);

  delay(300);
}
