/*
  ESP01_WiFi_Firebase_v3.ino
  Same as v2, but uploads the FULL helmet data. Expects a line like:
    OBST:1,GAS:0,HR:78,SPO2:96,DL:999,DF:34,DR:999,MQ:412,MB:380,OL:0,OF:1,OR:0

  /helmet1/live now contains:
    obstacle, gas, heartRate, spo2            (as before)
    distLeft, distFront, distRight            distance in cm (999 = clear)
    gasLevel, gasBaseline                     MQ-2 value and clean-air baseline
    obstLeft, obstFront, obstRight            1 = that sensor is under the threshold

  Copy YOUR four values (ssid, password, firebaseHost, databaseSecret)
  from your current sketch into the lines below before uploading.
*/

#include <ESP8266WiFi.h>
#include <WiFiClientSecure.h>
#include <ESP8266HTTPClient.h>

const char* ssid           = "Koushik";
const char* password       = "montus10";
const char* firebaseHost   = "worker-safety-vest-92b97-default-rtdb.firebaseio.com";
const char* databaseSecret = "V6ndR7T66VFKEhWVwt5ROC6muedzRLbGEvpdRlmL";

#define LIVE_INTERVAL 2000UL   // ms between /helmet1/live updates
#define LOG_INTERVAL  30000UL  // ms between /helmet1/logs entries
#define MAX_LINE_LEN  140      // longest line we accept from Nano #2

WiFiClientSecure client;
String incoming = "";
String pendingLine = "";
bool hasPending = false;
unsigned long lastLive = 0;
unsigned long lastLog = 0;

void connectWiFi() {
  Serial.print("WiFi connecting");
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
    delay(500);
    Serial.print(".");
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print(" OK, IP ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println(" FAILED");
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

String numOrZero(const String& v) {
  return v.length() ? v : String("0");
}

int sendToFirebase(const char* method, const String& path, const String& json) {
  HTTPClient http;
  String url = "https://" + String(firebaseHost) + path + ".json?auth=" + String(databaseSecret);
  http.setTimeout(5000);
  if (!http.begin(client, url)) return -100;
  http.addHeader("Content-Type", "application/json");
  int code = (method[0] == 'P' && method[1] == 'U') ? http.PUT(json) : http.POST(json);
  http.end();
  return code;
}

void processLine(String line) {
  String json = "{";
  json += "\"obstacle\":"     + numOrZero(getValue(line, "OBST")) + ",";
  json += "\"gas\":"          + numOrZero(getValue(line, "GAS"))  + ",";
  json += "\"heartRate\":"    + numOrZero(getValue(line, "HR"))   + ",";
  json += "\"spo2\":"         + numOrZero(getValue(line, "SPO2")) + ",";
  json += "\"distLeft\":"     + numOrZero(getValue(line, "DL"))   + ",";
  json += "\"distFront\":"    + numOrZero(getValue(line, "DF"))   + ",";
  json += "\"distRight\":"    + numOrZero(getValue(line, "DR"))   + ",";
  json += "\"gasLevel\":"     + numOrZero(getValue(line, "MQ"))   + ",";
  json += "\"gasBaseline\":"  + numOrZero(getValue(line, "MB"))   + ",";
  json += "\"obstLeft\":"     + numOrZero(getValue(line, "OL"))   + ",";
  json += "\"obstFront\":"    + numOrZero(getValue(line, "OF"))   + ",";
  json += "\"obstRight\":"    + numOrZero(getValue(line, "OR"));
  json += "}";

  unsigned long now = millis();

  if (now - lastLive >= LIVE_INTERVAL || lastLive == 0) {
    lastLive = now;
    int code = sendToFirebase("PUT", "/helmet1/live", json);
    Serial.print("live PUT -> ");
    Serial.print(code);
    Serial.print("  heap=");
    Serial.println(ESP.getFreeHeap());
  }

  if (now - lastLog >= LOG_INTERVAL || lastLog == 0) {
    lastLog = now;
    String withTs = json.substring(0, json.length() - 1) + ",\"timestamp\":{\".sv\":\"timestamp\"}}";
    int code = sendToFirebase("POST", "/helmet1/logs", withTs);
    Serial.print("log POST -> ");
    Serial.println(code);
  }
}

void setup() {
  Serial.begin(9600); // must match Nano #2's espSerial baud
  delay(200);
  Serial.println();
  Serial.println("ESP-01 v3 started");
  client.setInsecure();
  client.setBufferSizes(512, 512); // saves RAM on the ESP-01
  connectWiFi();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
  }

  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      if (incoming.startsWith("OBST:")) {
        pendingLine = incoming;
        hasPending = true;
      }
      incoming = "";
    } else if (c != '\r') {
      incoming += c;
      if (incoming.length() > MAX_LINE_LEN) incoming = "";
    }
  }

  if (hasPending) {
    hasPending = false;
    String line = pendingLine;
    processLine(line);
    // drop whatever piled up while the HTTPS call was blocking
    while (Serial.available()) Serial.read();
    incoming = "";
  }
}
