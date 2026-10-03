/*
  ESP01_AT_BaudScan_Test.ino
  Phase 1 — Component Verification

  Used to diagnose an ESP-01 that isn't responding to AT commands —
  scans a few common baud rates and prints any response (even
  garbage), which helps tell a baud mismatch apart from a module
  that needs its firmware reflashed.

  In this project, the ESP-01 in use was reused from an older
  project and had custom (non-AT) firmware on it — it needed to be
  reflashed with a normal Arduino sketch instead (see
  ESP01_WiFi_Test.ino / ESP01_Firebase_Test.ino, which are uploaded
  directly to the ESP-01 itself, not run as an AT-command client).

  Wiring (via ESP-01 adapter, e.g. HW-580):
    Adapter TX -> Nano D2
    Adapter RX -> Nano D3
    Adapter VCC -> 5V, GND -> GND
*/

#include <SoftwareSerial.h>
SoftwareSerial esp(2, 3); // RX, TX

long bauds[] = {9600, 57600, 74880, 115200};

void setup() {
  Serial.begin(9600);
  Serial.println("Starting baud scan...");
}

void loop() {
  for (int i = 0; i < 4; i++) {
    esp.begin(bauds[i]);
    delay(200);
    Serial.print("Trying baud: ");
    Serial.println(bauds[i]);
    esp.println("AT");
    delay(500);

    while (esp.available()) {
      Serial.write(esp.read());
    }
    Serial.println("---");
    esp.end();
    delay(300);
  }
  delay(2000);
}
