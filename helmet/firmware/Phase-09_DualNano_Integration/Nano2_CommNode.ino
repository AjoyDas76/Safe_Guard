/*
  Nano2_CommNode_final.ino
  Communication node: receives a line from Nano #1 and forwards it to the
  ESP-01 (which uploads it to Firebase /helmet1).

  Wiring:
    Nano1 D11 (TX) -> Nano2 D4 (RX)
    Nano1 GND      -> Nano2 GND
    Nano2 D3 (TX)  -> ESP-01 adapter RX
    Nano2 D2 (RX)  <- ESP-01 adapter TX
    Adapter VCC -> 5V, adapter GND -> GND

  D0/D1 stay free, so USB upload and Serial Monitor always work.
  Set DEBUG to 0 to silence the Serial Monitor output.
*/

#include <SoftwareSerial.h>

#define DEBUG 1

SoftwareSerial nano1Serial(4, 5); // RX = D4 (from Nano1 D11), TX unused
SoftwareSerial espSerial(2, 3);   // RX, TX -> ESP-01 adapter

String incoming = "";
unsigned long lineCount = 0;
unsigned long lastRxTime = 0;
unsigned long lastWarn = 0;

void processLine(const String& line) {
  if (!line.startsWith("OBST:")) return; // ignore garbage

  lineCount++;
  lastRxTime = millis();
  digitalWrite(LED_BUILTIN, HIGH);
  espSerial.println(line); // ESP-01 uploads it to Firebase
  digitalWrite(LED_BUILTIN, LOW);

#if DEBUG
  Serial.print("#");
  Serial.print(lineCount);
  Serial.print(" -> ESP: ");
  Serial.println(line);
#endif
}

void setup() {
  Serial.begin(9600);
  pinMode(LED_BUILTIN, OUTPUT);
  espSerial.begin(9600);
  nano1Serial.begin(9600);
  nano1Serial.listen(); // only one SoftwareSerial can listen at a time
}

void loop() {
  while (nano1Serial.available()) {
    char c = nano1Serial.read();
    if (c == '\n') {
      processLine(incoming);
      incoming = "";
    } else if (c != '\r') {
      incoming += c;
      if (incoming.length() > 140) incoming = ""; // lines are ~80 chars now
    }
  }

#if DEBUG
  if (millis() - lastRxTime > 3000 && millis() - lastWarn > 3000) {
    lastWarn = millis();
    Serial.println("... no data from Nano1 in last 3s (check D11->D4 and GND)");
  }
#endif
}
