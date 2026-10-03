/*
  ESP01_WiFi_Test.ino
  Phase 1 — Component Verification

  This sketch is uploaded DIRECTLY to the ESP-01 itself (not run as
  an AT-command client from a Nano) — the ESP-01 in this project runs
  its own Arduino code rather than factory AT firmware.

  Arduino IDE setup:
    Board: Generic ESP8266 Module
    Flash Size: 1MB (FS:64KB), Flash Mode: DOUT

  To upload: wire the ESP-01 with GPIO0 tied to GND (flash/boot mode),
  CH_PD tied to 3.3V, then upload. Afterwards, disconnect GPIO0 from
  GND and power-cycle to boot normally.

  Expected result: Serial Monitor (9600 baud) prints connection dots,
  then "WiFi Connected!" and a local IP address.
*/

#include <ESP8266WiFi.h>

const char* ssid = "YOUR_WIFI_NAME";
const char* password = "YOUR_WIFI_PASSWORD";

void setup() {
  Serial.begin(9600);
  delay(1000);

  Serial.println();
  Serial.print("Connecting to WiFi: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("WiFi Connected!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println();
    Serial.println("Failed to connect. Check SSID/password.");
  }
}

void loop() {
  Serial.print("Status: ");
  Serial.println(WiFi.status() == WL_CONNECTED ? "Connected" : "Disconnected");
  delay(2000);
}
