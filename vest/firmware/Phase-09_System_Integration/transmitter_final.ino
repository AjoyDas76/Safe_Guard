/*
 * Project : Smart Safety Vest
 * File    : Transmitter_Final.ino
 * Description:
 * ESP32 transmitter combining BME280 (env), MPU6050 (posture/movement/fall),
 * GPS, SOS button, battery monitor and LoRa transmission.
 *
 * Worker status reported to receiver (5 categories only):
 *   STANDING / WALKING / RUNNING / LYING / FALL
 *
 * NOTE: The finalized MPU module also detects LEAN (forward/back/left/right)
 * as an internal safety gate (it forces the movement classifier back to
 * STANDING while leaning so no stale WALKING/RUNNING carries over). Since
 * only 5 worker-status categories were requested, LEAN states are reported
 * externally as STANDING (see getSimplifiedStatus()).
 *
 * NOTE: The SOS button referenced here is the physical emergency button on
 * the vest itself (SOS_BUTTON_PIN), pressed by the worker.
 *
 * SIM800L SOS SMS: on a fall, SOS button press, or high-temperature alert,
 * this sends an SMS (via the SIM800L now mounted on the vest, not the
 * helmet) to EMERGENCY_NUMBER with the worker's last known GPS location and
 * alert reason, cooldown-limited to avoid repeat SMS for the same ongoing
 * alert. Powered from the vest's existing 18650/solar power system.
 *
 * LIVE-DATA UPDATE:
 * Telemetry now goes out once per second (TX_INTERVAL_MS) instead of every
 * two seconds, and each packet carries a sequence number as its 10th CSV
 * field so the receiver can spot packets that were lost over the air. LoRa
 * payload CRC is enabled so a corrupted packet is dropped rather than parsed
 * into bogus sensor values. The receiver sketch must be flashed together with
 * this one - the packet format changed.
 *
 * TIMING FIX: the MPU is sampled on a fixed 20 ms (50 Hz) schedule, the
 * BME280 is read once per second and I2C runs at 400 kHz, so the 15-sample
 * motion-energy window and 3-sample debounce see the rate they were tuned for.
 *
 * INIT: for the first 5 s after boot the status is "INITIALIZING" (Serial +
 * LoRa packets); STANDING appears only after calibration is complete.
 */

#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_BME280.h>
#include <MPU6050.h>
#include <TinyGPS++.h>
#include <HardwareSerial.h>
#include <SPI.h>
#include <LoRa.h>
#include <math.h>
#include <SoftwareSerial.h>

// ===================== Pin Definitions =====================
#define SOS_BUTTON_PIN 15
#define BUZZER_PIN     12
#define LED_PIN        13
#define BATT_ADC_PIN   34

// SIM800L (moved here from the helmet — see note below)
#define SIM800_RX_PIN 25 // ESP32 RX <- SIM800L TXD
#define SIM800_TX_PIN 27 // ESP32 TX -> SIM800L RXD (through a voltage divider, same as on the helmet)

// LoRa Pins
#define SCK_PIN   18
#define MISO_PIN  19
#define MOSI_PIN  23
#define SS_PIN    5
#define RST_PIN   14
#define DIO0_PIN  2

// ===================== Sensor Objects =====================
Adafruit_BME280 bme;
MPU6050 mpu;
TinyGPSPlus gps;
HardwareSerial gpsSerial(2); // UART2 for GPS (RX:16, TX:17)
SoftwareSerial sim800l(SIM800_RX_PIN, SIM800_TX_PIN);

// ===================== SIM800L SOS SMS =====================
#define EMERGENCY_NUMBER "+8801XXXXXXXXX" // set to the tested number, with country code
const unsigned long SMS_COOLDOWN_MS = 60000; // 60s — avoid repeat SMS while the same alert is still active
unsigned long lastSmsTime = 0;

// ===================== Environment / GPS / System Globals =====================
float temp, hum, pres;
float latitude = 0.0, longitude = 0.0;
float batteryVoltage = 0.0;
bool sosActive = false;
unsigned long lastTxTime = 0;

// ===================== Telemetry Timing =====================
// TX_INTERVAL_MS = how often a telemetry packet goes out. 1000 ms gives the
// dashboard a fresh reading every second. Airtime for this ~70-byte payload
// at 433 MHz / SF7 / BW125 is roughly 120-140 ms, so a 1 s cadence leaves
// plenty of headroom on the air and for the receiver's upload pipeline.
const unsigned long TX_INTERVAL_MS = 1000;

// Packet sequence number. It increments on every transmission and is sent as
// the 10th CSV field. The receiver uses it ONLY on its serial monitor to
// report gaps (i.e. packets lost over the air) - it is never written to
// Firebase, so the website's data structure is unchanged.
unsigned long packetSeq = 0;


// ===================== Loop Timing =====================
const unsigned long MPU_INTERVAL_MS  = 20;    // 50 Hz MPU sampling (energy window / debounce assume this)
const unsigned long BME_INTERVAL_MS  = 1000;  // BME280 read period
const unsigned long INIT_TOTAL_MS    = 5000;  // INITIALIZING duration after boot (last 2 s = calibration)

unsigned long lastMpuTime  = 0;
unsigned long lastBmeTime  = 0;
unsigned long mpuSampleCount = 0;             // samples taken since last TX (diagnostic, should be ~50)

/*
 * ==========================================================
 * MPU6050 Module (Posture + Movement + Fall Detection)
 * ==========================================================
 */

const int FILTER_SIZE = 10;
const int CAL_SAMPLES = 100;

long axBuf[FILTER_SIZE] = {0};
long ayBuf[FILTER_SIZE] = {0};
long azBuf[FILTER_SIZE] = {0};
int filterIndex = 0;

int16_t ax, ay, az;
int16_t gx, gy, gz;

float axF, ayF, azF;
float pitch, roll;
float refPitch, refRoll;
float tiltPitch, tiltRoll;

float totalAcceleration = 0.0;  // filtered accel magnitude
float rawAcceleration    = 0.0; // raw accel magnitude (free-fall/impact use)

// Posture / Lean thresholds
float pitchThreshold = 15.0;
float rollThreshold  = 20.0;

// Free Fall Detection
bool freeFallDetected = false;
const float FREE_FALL_THRESHOLD = 0.80;

// Impact Detection
bool impactDetected = false;
const float IMPACT_THRESHOLD = 1.8;

// Lying Position Detection
bool lyingDetected = false;
unsigned long lyingStartTime = 0;
const unsigned long LYING_CONFIRM_TIME = 1500; // must hold 1.5s
const float LYING_ANGLE = 70.0;

// Fall Latching
bool fallDetected = false;
bool fallLatched  = false;
bool workerRecovered = false;
unsigned long fallStartTime = 0;
const unsigned long FALL_TIMEOUT = 6000;

enum PostureState
{
  POSTURE_STANDING = 0,
  POSTURE_FORWARD_LEAN,
  POSTURE_BACKWARD_LEAN,
  POSTURE_LEFT_LEAN,
  POSTURE_RIGHT_LEAN,
  POSTURE_LYING,
  POSTURE_FALL
};

PostureState postureState = POSTURE_STANDING;
PostureState previousPostureState = POSTURE_STANDING;
bool postureChanged = false;
bool isUpright = true; // gates the movement classifier

// True while the 5 s boot initialization/calibration is running
bool mpuInitializing = true;
// True if the MPU6050 does not answer on I2C (reported as "MPU ERROR")
bool mpuFault = false;


// Movement-Energy Classifier (only runs while isUpright == true)
const int ENERGY_WINDOW = 15;
float energyBuf[ENERGY_WINDOW] = {0};
int energyIndex = 0;
bool energyBufFull = false;

float STANDING_ENERGY_MAX = 1500.0;
float WALKING_ENERGY_MAX  = 6000.0;
// anything above WALKING_ENERGY_MAX (while upright) = RUNNING

enum MovementState
{
  MOVEMENT_STANDING = 0,
  MOVEMENT_WALKING,
  MOVEMENT_RUNNING
};

MovementState movementState = MOVEMENT_STANDING;

// Debounce - 3 consecutive samples before switching state
int standingCount = 0;
int walkingCount  = 0;
int runningCount  = 0;

struct MPUData
{
  float tiltPitch;
  float tiltRoll;
  float accelerationG;
  float motionEnergy;

  bool isUpright;
  bool freeFall;
  bool impact;
  bool lying;
  bool fall;

  PostureState posture;
  MovementState movement;

  const char* status; // full status (includes LEAN, for local debug)
};

MPUData mpuData;

void updateMPUFilter()
{
  mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);

  axBuf[filterIndex] = ax;
  ayBuf[filterIndex] = ay;
  azBuf[filterIndex] = az;
  filterIndex = (filterIndex + 1) % FILTER_SIZE;

  long sx = 0, sy = 0, sz = 0;
  for (int i = 0; i < FILTER_SIZE; i++) { sx += axBuf[i]; sy += ayBuf[i]; sz += azBuf[i]; }

  axF = sx / (float)FILTER_SIZE;
  ayF = sy / (float)FILTER_SIZE;
  azF = sz / (float)FILTER_SIZE;

  pitch = atan2(azF, sqrt(axF * axF + ayF * ayF)) * 180.0 / PI;
  roll  = atan2(ayF, sqrt(axF * axF + azF * azF)) * 180.0 / PI;

  totalAcceleration = sqrt(axF * axF + ayF * ayF + azF * azF);
  rawAcceleration = sqrt((float)ax * ax + (float)ay * ay + (float)az * az);

  float instantEnergy = fabs(gx) + fabs(gy) + fabs(gz);
  energyBuf[energyIndex] = instantEnergy;
  energyIndex = (energyIndex + 1) % ENERGY_WINDOW;
  if (energyIndex == 0) energyBufFull = true;
}

float getAverageEnergy()
{
  int count = energyBufFull ? ENERGY_WINDOW : energyIndex;
  if (count == 0) return 0;

  float sum = 0;
  for (int i = 0; i < count; i++) sum += energyBuf[i];
  return sum / count;
}

void detectFreeFall()
{
  float normalizedAcceleration = rawAcceleration / 16384.0;
  if (normalizedAcceleration < FREE_FALL_THRESHOLD)
  {
    freeFallDetected = true;
    if (fallStartTime == 0) fallStartTime = millis();
  }
}

void detectImpact()
{
  float normalizedAcceleration = rawAcceleration / 16384.0;
  if (normalizedAcceleration > IMPACT_THRESHOLD)
  {
    impactDetected = true;
  }
}

void detectLyingPosition()
{
  bool horizontal = (fabs(tiltPitch) > LYING_ANGLE || fabs(tiltRoll) > LYING_ANGLE);

  if (horizontal)
  {
    if (lyingStartTime == 0) lyingStartTime = millis();
    if (millis() - lyingStartTime >= LYING_CONFIRM_TIME)
    {
      lyingDetected = true;
    }
  }
  else
  {
    lyingDetected = false;
    lyingStartTime = 0;
  }
}

void detectRecovery()
{
  workerRecovered = (!lyingDetected && fabs(tiltPitch) < 10 && fabs(tiltRoll) < 10);
}

void updatePostureState()
{
  if (fallLatched)
  {
    postureState = POSTURE_FALL;
    isUpright = false;
    postureChanged = (previousPostureState != postureState);
    previousPostureState = postureState;
    return;
  }

  if (lyingDetected)
  {
    postureState = POSTURE_LYING;
    isUpright = false;
    postureChanged = (previousPostureState != postureState);
    previousPostureState = postureState;
    return;
  }

  postureState = POSTURE_STANDING;

  if (tiltPitch < -pitchThreshold)
  {
    postureState = POSTURE_FORWARD_LEAN;
  }
  else if (tiltPitch > pitchThreshold)
  {
    postureState = POSTURE_BACKWARD_LEAN;
  }
  else if (tiltRoll > rollThreshold)
  {
    postureState = POSTURE_LEFT_LEAN;
  }
  else if (tiltRoll < -rollThreshold)
  {
    postureState = POSTURE_RIGHT_LEAN;
  }

  isUpright = (postureState == POSTURE_STANDING);

  postureChanged = (previousPostureState != postureState);
  previousPostureState = postureState;
}

void updateMovementState()
{
  if (!isUpright)
  {
    standingCount = 0;
    walkingCount  = 0;
    runningCount  = 0;
    movementState = MOVEMENT_STANDING;
    return;
  }

  float avgEnergy = getAverageEnergy();

  if (avgEnergy < STANDING_ENERGY_MAX)
  {
    standingCount++; walkingCount = 0; runningCount = 0;
    if (standingCount >= 3) movementState = MOVEMENT_STANDING;
  }
  else if (avgEnergy < WALKING_ENERGY_MAX)
  {
    walkingCount++; standingCount = 0; runningCount = 0;
    if (walkingCount >= 3) movementState = MOVEMENT_WALKING;
  }
  else
  {
    runningCount++; walkingCount = 0; standingCount = 0;
    if (runningCount >= 3) movementState = MOVEMENT_RUNNING;
  }
}

void updateFallDetection()
{
  if (fallLatched)
  {
    if (!workerRecovered)
    {
      fallDetected = true;
      return;
    }
    // Worker সত্যিই দাঁড়িয়েছে
    fallLatched = false;
    fallDetected = false;
    freeFallDetected = false;
    impactDetected = false;
    fallStartTime = 0;
  }

  if (fallStartTime != 0 && millis() - fallStartTime <= FALL_TIMEOUT)
  {
    if (!fallLatched && freeFallDetected && impactDetected && lyingDetected)
    {
      fallDetected = true;
      fallLatched = true;
      fallStartTime = 0;
    }
    else if (!fallLatched)
    {
      fallDetected = false;
    }
  }
  else
  {
    fallDetected = false;
    fallStartTime = 0;
    freeFallDetected = false;
    impactDetected = false;
  }
}

// Full status (for local Serial debugging - includes LEAN detail)
const char* getFullWorkerStatus()
{
  if (mpuInitializing) return "INITIALIZING";
  if (mpuFault) return "MPU ERROR";
  switch (postureState)
  {
    case POSTURE_FALL:           return "FALL DETECTED";
    case POSTURE_LYING:          return "LYING";
    case POSTURE_FORWARD_LEAN:   return "FORWARD LEAN";
    case POSTURE_BACKWARD_LEAN:  return "BACKWARD LEAN";
    case POSTURE_LEFT_LEAN:      return "LEFT LEAN";
    case POSTURE_RIGHT_LEAN:     return "RIGHT LEAN";
    case POSTURE_STANDING:
    default:
      switch (movementState)
      {
        case MOVEMENT_WALKING: return "WALKING";
        case MOVEMENT_RUNNING: return "RUNNING";
        case MOVEMENT_STANDING:
        default:                return "STANDING";
      }
  }
}

// Simplified status - ONLY the 5 requested categories, sent over LoRa
// LEAN states collapse into STANDING here (movement classifier already
// forces STANDING while leaning, so this stays consistent).
const char* getSimplifiedStatus()
{
  if (mpuInitializing) return "INITIALIZING";
  if (mpuFault) return "MPU ERROR";
  switch (postureState)
  {
    case POSTURE_FALL: return "FALL";
    case POSTURE_LYING: return "LYING";
    default:
      switch (movementState)
      {
        case MOVEMENT_WALKING: return "WALKING";
        case MOVEMENT_RUNNING: return "RUNNING";
        default:                return "STANDING";
      }
  }
}


void updateMPUData()
{
  mpuData.tiltPitch     = tiltPitch;
  mpuData.tiltRoll      = tiltRoll;
  mpuData.accelerationG = rawAcceleration / 16384.0;
  mpuData.motionEnergy  = getAverageEnergy();

  mpuData.isUpright = isUpright;
  mpuData.freeFall  = freeFallDetected;
  mpuData.impact    = impactDetected;
  mpuData.lying     = lyingDetected;
  mpuData.fall      = fallDetected;

  mpuData.posture  = postureState;
  mpuData.movement = movementState;

  mpuData.status = getFullWorkerStatus();
}


/*
 * ==========================================================
 * Telemetry (LoRa packet + Serial print) - used by loop() and
 * by the INITIALIZING phase
 * ==========================================================
 */
void sendTelemetry()
{
  const char* statusToSend = getSimplifiedStatus(); // INITIALIZING/STANDING/WALKING/RUNNING/LYING/FALL

  packetSeq++;

  // CSV: temp,hum,pres,state,fall,lat,lng,sos,batt,seq
  String payload = String(temp, 1) + "," + String(hum, 1) + "," + String(pres, 1) + "," +
                   String(statusToSend) + "," + String(mpuData.fall) + "," +
                   String(latitude, 6) + "," + String(longitude, 6) + "," +
                   String(sosActive) + "," + String(batteryVoltage, 2) + "," +
                   String(packetSeq);

  LoRa.beginPacket();
  LoRa.print(payload);
  LoRa.endPacket();

  Serial.printf("------ [ TRANSMITTING SENSOR DATA #%lu ] ------\n", packetSeq);
  Serial.printf("Temp: %.1f C | Hum: %.1f %% | Pres: %.1f hPa\n", temp, hum, pres);
  Serial.printf("Status (sent): %s | Status (full): %s\n", statusToSend, getFullWorkerStatus());
  Serial.printf("Fall: %s | SOS(button): %s\n", mpuData.fall ? "YES" : "NO", sosActive ? "YES" : "NO");
  Serial.printf("MPU samples/s: %lu (should be ~50) | Motion energy (avg): %.0f\n", mpuSampleCount, mpuData.motionEnergy);
  Serial.printf("GPS: Lat %.6f, Lng %.6f | Batt: %.2fV\n", latitude, longitude, batteryVoltage);
  Serial.printf("Payload Sent: %s\n", payload.c_str());
  Serial.println("------------------------------------------\n");

  mpuSampleCount = 0;
}

/*
 * ==========================================================
 * Initialization + Calibration (5 s total)
 *   0 - 3 s : settle (sensor sampled, nothing averaged)
 *   3 - 5 s : reference pitch/roll averaged (CAL_SAMPLES)
 * Status is "INITIALIZING" for the whole time; it becomes
 * STANDING only after this function returns.
 * ==========================================================
 */
void calibrateMPU()
{
  mpuInitializing = true;
  Serial.println("[MPU] INITIALIZING (5 s) - keep the worker still...");

  const unsigned long start = millis();
  const unsigned long calStart = INIT_TOTAL_MS - (unsigned long)CAL_SAMPLES * MPU_INTERVAL_MS;

  int lastSecLeft = -1;
  float ps = 0, rs = 0;
  int n = 0;

  while (millis() - start < INIT_TOTAL_MS)
  {
    unsigned long elapsed = millis() - start;

    updateMPUFilter();   // also fills the filter + energy buffers

    if (elapsed >= calStart) { ps += pitch; rs += roll; n++; }

    int secLeft = (int)((INIT_TOTAL_MS - elapsed + 999) / 1000);
    if (secLeft != lastSecLeft)
    {
      lastSecLeft = secLeft;
      Serial.printf("[MPU] Initializing... %d\n", secLeft);
      sendTelemetry();   // status = INITIALIZING
    }

    delay(MPU_INTERVAL_MS);
  }

  refPitch = (n > 0) ? ps / n : pitch;
  refRoll  = (n > 0) ? rs / n : roll;

  Serial.print("[MPU] Reference Pitch: "); Serial.println(refPitch, 2);
  Serial.print("[MPU] Reference Roll: ");  Serial.println(refRoll, 2);

  mpuSampleCount = 0;
  mpuInitializing = false;   // from here on: STANDING / WALKING / ...
  Serial.println("[MPU] Initialization complete.");
}


/*
 * ==========================================================
 * SIM800L SOS SMS
 * ==========================================================
 */
void sim800Command(const char* cmd) {
  sim800l.println(cmd);
  delay(300);
}

void sendSOSMessage(const char* reason) {
  Serial.println("[SIM800L] Sending SOS SMS...");

  sim800Command("AT");
  sim800Command("AT+CMGF=1"); // text mode

  sim800l.print("AT+CMGS=\"");
  sim800l.print(EMERGENCY_NUMBER);
  sim800l.println("\"");
  delay(300);

  sim800l.print("SafeGuard ALERT: ");
  sim800l.print(reason);
  sim800l.print(" | Status: ");
  sim800l.print(getFullWorkerStatus());
  sim800l.print(" | Last location: https://maps.google.com/?q=");
  sim800l.print(latitude, 6);
  sim800l.print(",");
  sim800l.print(longitude, 6);
  sim800l.println();

  sim800l.write(26); // Ctrl+Z, ends the message
  delay(3000);

  Serial.println("[SIM800L] SOS SMS sent.");
}

/*
 * ==========================================================
 * Setup
 * ==========================================================
 */
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n[System] Initializing Smart Vest Transmitter...");

  gpsSerial.begin(9600, SERIAL_8N1, 16, 17);
  sim800l.begin(9600);
  Wire.begin();          // I2C for BME280 + MPU6050 (default SDA21/SCL22)

  pinMode(SOS_BUTTON_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);

  // Initialize BME280
  if (!bme.begin(0x76)) Serial.println("[BME280] Sensor Failed!");
  else Serial.println("[BME280] Sensor Ready.");

  // Initialize LoRa (before MPU calibration so INITIALIZING can be transmitted)
  SPI.begin(SCK_PIN, MISO_PIN, MOSI_PIN, SS_PIN);
  LoRa.setPins(SS_PIN, RST_PIN, DIO0_PIN);
  if (!LoRa.begin(433E6)) {
    Serial.println("[LoRa] Transmitter Initialization Failed!");
    while (1);
  }

  // Payload CRC (must be enabled on BOTH radios): corrupted packets are
  // discarded by the receiver instead of being parsed as real data.
  LoRa.enableCrc();
  Serial.println("[LoRa] Transmitter Ready.");

  // First readings so the INITIALIZING packets carry real values
  temp = bme.readTemperature();
  hum  = bme.readHumidity();
  pres = bme.readPressure() / 100.0F;
  batteryVoltage = (analogRead(BATT_ADC_PIN) / 4095.0) * 2 * 3.3 * 1.1;

  // Initialize MPU6050. NOTE: testConnection() is only printed, NOT used to
  // decide - many GY-521 clones fail that WHO_AM_I check yet work fine. The
  // real test is whether the sensor returns valid accelerometer data.
  mpu.initialize();
  bool mpuAlive = false;
  for (int attempt = 1; attempt <= 3 && !mpuAlive; attempt++) {
    delay(100);
    Serial.printf("[MPU6050] WHO_AM_I = 0x%02X | testConnection: %s\n",
                  mpu.getDeviceID(), mpu.testConnection() ? "OK" : "FAIL");
    mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
    mpuAlive = !((ax == 0 && ay == 0 && az == 0) || (ax == -1 && ay == -1 && az == -1));
    if (!mpuAlive) {
      Serial.printf("[MPU6050] No valid data (attempt %d/3)\n", attempt);
      mpu.initialize();
    }
  }

  if (mpuAlive) {
    Serial.println("[MPU6050] Sensor Ready.");
    calibrateMPU();        // 5 s INITIALIZING, then STANDING etc.
  } else {
    mpuFault = true;
    mpuInitializing = false;
    Serial.println("[MPU6050] NOT RESPONDING - check SDA(21)/SCL(22), VCC, GND");
  }

  lastMpuTime = lastBmeTime = millis();
  Serial.println("[System] Setup complete.\n");
}

/*
 * ==========================================================
 * Loop
 * ==========================================================
 */
void loop() {
  unsigned long now = millis();

  // 1. MPU6050 pipeline - fixed 50 Hz schedule (independent of LoRa/BME timing)
  if (!mpuFault && now - lastMpuTime >= MPU_INTERVAL_MS) {
    lastMpuTime = now;
    mpuSampleCount++;

    updateMPUFilter();
    tiltPitch = pitch - refPitch;
    tiltRoll  = roll  - refRoll;

    detectFreeFall();
    detectImpact();
    detectLyingPosition();
    detectRecovery();

    updatePostureState();   // decides isUpright (the gate)
    updateMovementState();  // WALKING/RUNNING only evaluated if isUpright
    updateFallDetection();

    updateMPUData();
  }

  // 2. BME280 - once per second
  if (now - lastBmeTime >= BME_INTERVAL_MS) {
    lastBmeTime = now;
    temp = bme.readTemperature();
    hum  = bme.readHumidity();
    pres = bme.readPressure() / 100.0F;
  }

  // 3. Read GPS Data
  while (gpsSerial.available() > 0) {
    char c = gpsSerial.read();
    gps.encode(c);
  }

  if (gps.location.isValid()) {
    latitude = gps.location.lat();
    longitude = gps.location.lng();
  }

  // 4. Read SOS & Battery
  sosActive = (digitalRead(SOS_BUTTON_PIN) == LOW);
  batteryVoltage = (analogRead(BATT_ADC_PIN) / 4095.0) * 2 * 3.3 * 1.1;

  // 5. Trigger Alarm
  bool alertActive = (mpuData.fall || sosActive || temp > 50.0);
  if (alertActive) {
    digitalWrite(BUZZER_PIN, HIGH);
    digitalWrite(LED_PIN, HIGH);

    if (millis() - lastSmsTime > SMS_COOLDOWN_MS) {
      const char* reason = mpuData.fall ? "FALL DETECTED" : sosActive ? "SOS BUTTON PRESSED" : "HIGH TEMPERATURE";
      sendSOSMessage(reason);
      lastSmsTime = millis();
    }
  } else {
    digitalWrite(BUZZER_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
  }

  // 6. LoRa Transmission & Serial Print (every TX_INTERVAL_MS)
  if (millis() - lastTxTime >= TX_INTERVAL_MS) {
    sendTelemetry();
    lastTxTime = millis();
  }

  delay(1); // yield; MPU timing is handled by the 20 ms schedule above
}
