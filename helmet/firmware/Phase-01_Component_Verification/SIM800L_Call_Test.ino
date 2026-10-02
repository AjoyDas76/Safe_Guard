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
  sendATCommand("ATD+8801672536964;"); // Enter the phone number you want to call here (prefix country code)
}

void loop()
{
  // Nothing to do here
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