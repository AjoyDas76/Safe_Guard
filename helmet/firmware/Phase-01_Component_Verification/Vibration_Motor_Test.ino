/*
  Vibration_Motor_Test.ino
  Phase 1 — Component Verification

  Wiring:
    Nano D13 -> 1kOhm resistor -> NPN transistor Base (e.g. BC547/2N2222)
    Transistor Emitter -> GND
    Transistor Collector -> Motor (-)
    Motor (+) -> regulated supply (AMS1117 output) — NOT direct 5V,
                 the miniature vibration motor used in this project
                 is damaged by direct 5V.
    1N4007 flyback diode across the motor terminals (cathode toward
    motor (+)) for back-EMF protection.

  Expected result: motor vibrates for 1 second, stops for 1 second,
  repeating. Transistor/motor should not get noticeably hot.
*/

#define VIBRATION_PIN 13

void setup() {
  pinMode(VIBRATION_PIN, OUTPUT);
}

void loop() {
  digitalWrite(VIBRATION_PIN, HIGH);
  delay(1000);
  digitalWrite(VIBRATION_PIN, LOW);
  delay(1000);
}
