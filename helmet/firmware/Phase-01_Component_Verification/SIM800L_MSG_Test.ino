#include <SoftwareSerial.h>

SoftwareSerial mySerial(6, 7);

void setup()
{
  Serial.begin(9600);
  mySerial.begin(9600);

  Serial.println("Initializing...");
  delay(1000);

  sendATCommand("AT");
  sendATCommand("AT+CMGF=1");
  sendATCommand("AT+CMGS=\"+8801672536964\""); // Enter your phone number here (prefix country code)
  sendATCommand("Hello from SafeGuard"); // Enter your message here
  mySerial.write(26);
}

void loop()
{
}

void sendATCommand(const char* command)
{
  mySerial.println(command);
  delay(500);

  while (mySerial.available())
  {
    Serial.write(mySerial.read());
  }
  Serial.println();
}