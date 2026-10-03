/*
  Nano2_CommNode.ino
  Phase 9 — Dual-Nano System Integration (Communication Node)

  Realizes Phase 7 (ESP-01 WiFi -> Firebase forwarding) and Phase 8
  (SIM800L GSM SOS backup) together.

  Receives a summary line from Nano #1 over hardware serial (D0),
  forwards it to the ESP-01 (which uploads to Firebase on its own —
  see Phase-07_ESP01_WiFi_Firebase/), and triggers an SMS via SIM800L
  when an obstacle or gas alert is active, cooldown-limited so it
  doesn't spam the same alert repeatedly.

  Wiring — see Phase-09 README.md for the full table.

  IMPORTANT: disconnect the wire on D0 (coming from Nano #1) before
  uploading this sketch, or the USB upload will conflict with it.
*/

#include <SoftwareSerial.h>

SoftwareSerial espSerial(2, 3); // RX, TX -> ESP-01 adapter
SoftwareSerial simSerial(6, 7); // RX, TX -> SIM800L

#define EMERGENCY_NUMBER "+8801XXXXXXXXX" // set to the tested number, with country code

unsigned long lastSOS = 0;
const unsigned long SOS_COOLDOWN = 60000; // 60s cooldown to avoid repeat SMS/calls for the same ongoing alert

void sendSimCommand(const char* cmd) {
  simSerial.println(cmd);
  delay(500);
}

void triggerSOS() {
  simSerial.listen();
  sendSimCommand("AT");
  sendSimCommand("AT+CMGF=1");
  simSerial.print("AT+CMGS=\"");
  simSerial.print(EMERGENCY_NUMBER);
  simSerial.println("\"");
  delay(500);
  simSerial.println("SafeGuard Helmet Alert: Obstacle/Gas/Health alert triggered!");
  simSerial.write(26); // Ctrl+Z, ends the message
  delay(3000);
}

String incoming = "";

int getValue(String data, String key) {
  int idx = data.indexOf(key + ":");
  if (idx == -1) return 0;
  int start = idx + key.length() + 1;
  int end = data.indexOf(',', start);
  if (end == -1) end = data.length();
  return data.substring(start, end).toInt();
}

void processLine(String line) {
  if (line.length() == 0) return;

  espSerial.listen();
  espSerial.println(line); // forward to ESP-01, which uploads to Firebase

  int obst = getValue(line, "OBST");
  int gas  = getValue(line, "GAS");

  if ((obst == 1 || gas == 1) && millis() - lastSOS > SOS_COOLDOWN) {
    triggerSOS();
    lastSOS = millis();
  }
}

void setup() {
  Serial.begin(9600);   // link from Nano #1 (D0)
  espSerial.begin(9600);
  simSerial.begin(9600);
}

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      processLine(incoming);
      incoming = "";
    } else if (c != '\r') {
      incoming += c;
    }
  }
}
