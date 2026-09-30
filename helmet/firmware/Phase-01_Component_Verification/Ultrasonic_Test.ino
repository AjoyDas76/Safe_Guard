/*
  Ultrasonic_Test.ino
  Phase 1 — Component Verification

  Tests all three ultrasonic sensors (Front, Left, Right) on Nano #1.

  Wiring:
    Ultrasonic 1 (Front) -> Trig D2, Echo D3
    Ultrasonic 2 (Left)  -> Trig D4, Echo D5
    Ultrasonic 3 (Right) -> Trig D6, Echo D7
    VCC -> 5V, GND -> GND (all three)

  Expected result: Serial Monitor (9600 baud) prints live distance
  readings (cm) for all three sensors, changing as objects move
  closer/farther from each sensor.
*/

#define TRIG1 2
#define ECHO1 3
#define TRIG2 4
#define ECHO2 5
#define TRIG3 6
#define ECHO3 7

void setup() {
  Serial.begin(9600);
  pinMode(TRIG1, OUTPUT); pinMode(ECHO1, INPUT);
  pinMode(TRIG2, OUTPUT); pinMode(ECHO2, INPUT);
  pinMode(TRIG3, OUTPUT); pinMode(ECHO3, INPUT);
}

long readDistance(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duration = pulseIn(echoPin, HIGH, 30000); // 30ms timeout
  long distanceCm = duration * 0.034 / 2;
  return distanceCm;
}

void loop() {
  long d1 = readDistance(TRIG1, ECHO1);
  delay(30);
  long d2 = readDistance(TRIG2, ECHO2);
  delay(30);
  long d3 = readDistance(TRIG3, ECHO3);
  delay(30);

  Serial.print("Front: "); Serial.print(d1); Serial.print(" cm  |  ");
  Serial.print("Left: ");  Serial.print(d2); Serial.print(" cm  |  ");
  Serial.print("Right: "); Serial.print(d3); Serial.println(" cm");

  delay(300);
}
