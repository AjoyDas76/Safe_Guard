/*
  Nano_Blink_Serial_Test.ino
  Phase 1 — Component Verification

  Purpose: confirm an Arduino Nano board itself is working (bootloader,
  drivers, USB link) before wiring any sensors to it.

  Upload this to Nano #1 first, then to Nano #2, one at a time.

  Expected result:
    - Onboard LED (D13) blinks on/off every 500ms
    - Serial Monitor (9600 baud) prints "LED ON" / "LED OFF"
*/

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(9600);
  Serial.println("Nano is alive!");
}

void loop() {
  digitalWrite(LED_BUILTIN, HIGH);
  Serial.println("LED ON");
  delay(500);
  digitalWrite(LED_BUILTIN, LOW);
  Serial.println("LED OFF");
  delay(500);
}
