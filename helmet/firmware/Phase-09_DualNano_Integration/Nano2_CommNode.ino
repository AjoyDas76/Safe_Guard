/*
  Nano2_CommNode.ino
  Phase 9 — Dual-Nano System Integration (Communication Node)

  Realizes Phase 7 (ESP-01 WiFi -> Firebase forwarding).

  Receives a summary line from Nano #1 over hardware serial (D0) and
  forwards it to the ESP-01 (which uploads to Firebase on its own —
  see Phase-07_ESP01_WiFi_Firebase/).

  SIM800L GSM SOS has been MOVED to the vest (not the helmet) — see
  vest/firmware/Phase-09_System_Integration/transmitter_final.ino.
  Reasoning: an emergency SMS is far more useful carrying the
  worker's GPS location and fall/SOS status, both of which live on
  the vest, not the helmet. The helmet no longer sends any SMS/calls;
  it only reports its own sensor data to Firebase over WiFi.

  Wiring — see Phase-09 README.md for the full table.

  IMPORTANT: disconnect the wire on D0 (coming from Nano #1) before
  uploading this sketch, or the USB upload will conflict with it.
*/

#include <SoftwareSerial.h>

SoftwareSerial espSerial(2, 3); // RX, TX -> ESP-01 adapter

String incoming = "";

void processLine(String line) {
  if (line.length() == 0) return;
  espSerial.println(line); // forward to ESP-01, which uploads to Firebase
}

void setup() {
  Serial.begin(9600);   // link from Nano #1 (D0)
  espSerial.begin(9600);
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
