/*
  ESP01_Firebase_Test.ino
  Phase 1 — Component Verification (also serves as the Phase 7 base)

  Uploaded directly to the ESP-01 (Generic ESP8266 Module board),
  same flashing procedure as ESP01_WiFi_Test.ino.

  Listens on Serial for a line in the format:
    OBST:1,GAS:0,HR:78,SPO2:96

  and uploads it to Firebase Realtime Database:
    /helmet1/live  -> latest status (overwritten each time, PUT)
    /helmet1/logs  -> timestamped history (appended, POST)

  Reuses the same Firebase project as the vest, under a separate
  /helmet1 node so the two sections' data don't mix.

  Standalone test (before Nano #2 is wired in): with the ESP-01 still
  connected via USB-passthrough, open Serial Monitor, type a line like
  the example above, and press Enter — this uploads it immediately,
  letting you verify Firebase connectivity before the rest of the
  hardware is ready.

  Fill in ssid / password / databaseSecret before uploading.
  Get the database secret from:
    Firebase Console -> Project Settings -> Service accounts
    -> Database secrets
*/

#include <ESP8266WiFi.h>
#include <WiFiClientSecure.h>
#include <ESP8266HTTPClient.h>

const char* ssid = "YOUR_WIFI_NAME";
const char* password = "YOUR_WIFI_PASSWORD";
const char* firebaseHost = "worker-safety-vest-92b97-default-rtdb.firebaseio.com";
const char* databaseSecret = "YOUR_DATABASE_SECRET";

WiFiClientSecure client;
String incoming = "";

void connectWiFi() {
  WiFi.begin(ssid, password);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
    delay(500);
  }
}

String getValue(String data, String key) {
  int idx = data.indexOf(key + ":");
  if (idx == -1) return "";
  int start = idx + key.length() + 1;
  int end = data.indexOf(',', start);
  if (end == -1) end = data.length();
  return data.substring(start, end);
}

void uploadLive(String json) {
  HTTPClient http;
  String url = "https://" + String(firebaseHost) + "/helmet1/live.json?auth=" + String(databaseSecret);
  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");
  http.PUT(json);
  http.end();
}

void uploadLog(String json) {
  HTTPClient http;
  String url = "https://" + String(firebaseHost) + "/helmet1/logs.json?auth=" + String(databaseSecret);
  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");
  String withTimestamp = json.substring(0, json.length() - 1) + ",\"timestamp\":{\".sv\":\"timestamp\"}}";
  http.POST(withTimestamp);
  http.end();
}

void processLine(String line) {
  if (line.length() == 0) return;

  String obst = getValue(line, "OBST");
  String gas  = getValue(line, "GAS");
  String hr   = getValue(line, "HR");
  String spo2 = getValue(line, "SPO2");

  String json = "{";
  json += "\"obstacle\":" + (obst.length() ? obst : "0") + ",";
  json += "\"gas\":" + (gas.length() ? gas : "0") + ",";
  json += "\"heartRate\":" + (hr.length() ? hr : "0") + ",";
  json += "\"spo2\":" + (spo2.length() ? spo2 : "0");
  json += "}";

  uploadLive(json);
  uploadLog(json);
}

void setup() {
  Serial.begin(9600); // must match Nano #2's SoftwareSerial baud later
  connectWiFi();
  client.setInsecure();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
  }

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
