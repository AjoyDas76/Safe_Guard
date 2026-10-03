# SafeGuard — Master Project File

> **Purpose of this file:** a complete handoff document for the "SafeGuard" (Smart Industrial Safety Vest + Helmet) academic engineering project. Give this file to any AI assistant so it has full context to continue the project without re-asking what's already been decided. This supersedes the earlier master file — helmet Phase 1 is now complete and a full system integration draft exists.

---

## 0. Project Identity & Context

- Student: Ajoy (engineering student, embedded systems)
- Academic project, likely for a thesis or formal report
- **Project name: "SafeGuard"**
- Two sections under one system: **Vest** (complete) and **Helmet** (Phase 1 done, integration code drafted, awaiting full physical test)
- GitHub repo: `Safe_Guard-main`, restructured as `vest/`, `helmet/`, `final-project/` folders with the live web dashboard kept at repo root
- Communication with the user has been mostly in Bengali; technical deliverables (docs, diagrams, code comments) are in English.

---

## 1. Repository Structure

```
.
├── index.html, app.js, style.css   ← Web dashboard, now a PWA (manifest.json, logo.png, icon-192.png added by user)
├── sw.js                            ← Service worker (root-scoped, must stay at root)
├── README.md                        ← Top-level overview, lists ESP32, LoRa, Arduino Nano, MQ-2, MAX30100,
│                                        solar charging, and PiezoElectric Charging as features
├── vest/                            ← Smart Safety Vest — Complete
│   ├── README.md
│   ├── VEST-CONNECTIONS.md          ← Full wiring reference, pulled from the final firmware source
│   ├── firmware/ (Phase-01 .. Phase-10)
│   ├── diagrams/, datasheets/, docs/, hardware/, images/, results/, thesis/
├── helmet/                          ← Smart Safety Helmet — Phase 1 complete, integration drafted
│   ├── README.md
│   ├── firmware/ (Phase-01 .. Phase-10, see 3.9)
│   ├── diagrams/, datasheets/, docs/
└── final-project/                   ← Combined final-release deliverables (empty so far — for final report,
                                         presentation, consolidated BOM/diagrams once both sections are done)
```

All branding across the repo (root README, index.html, vest/README.md, helmet/README.md, VEST-CONNECTIONS.md) has been unified to "SafeGuard" — this was done in a recent pass, no stale naming should remain.

---

## 2. VEST SECTION — STATUS: COMPLETE

### 2.1 Hardware
BME280 (environment), NEO-M8N GPS, MPU-6050 (motion/fall), SX1278 RA-02 LoRa module, ESP32 DevKit V1 x2 (transmitter + receiver), SSD1306 OLED, DFRobot Solar Power Manager 5V, 18650 Li-ion battery, 5V flexible solar panel, SOS push button, active buzzer, status LED, on/off switch. Also has a documented piezo energy-harvesting sub-design (4x 35mm piezo discs, bridge rectifiers, supercapacitor, feeding the same TP4056/18650 path) referenced from a separate B.Sc. EEE thesis document.

### 2.2 Final wiring (from transmitter_final.ino / receiver_final.ino — source of truth; supersedes older per-phase README pin numbers)

**Vest Transmitter (ESP32 #1):**
| Subsystem | ESP32 GPIO |
|---|---|
| LoRa SCK / MISO / MOSI / NSS / RESET / DIO0 | 18 / 19 / 23 / 5 / 14 / 2 |
| BME280, MPU6050, OLED (shared I2C) SDA / SCL | 21 / 22 |
| GPS (NEO-M8N) RX2 / TX2 | 16 / 17 |
| SOS button (INPUT_PULLUP) | 15 |
| Buzzer | 12 |
| LED | 13 |
| Battery monitor (ADC, via 2:1 voltage divider) | 34 |

**Base Station Receiver (ESP32 #2):** same LoRa pins as above, no sensors — WiFi + Firebase gateway only.

Note: earlier individual-phase sketches (Phase 3-5) used different pins for some signals (SOS button was GPIO4, DIO0 was GPIO26) — these were superseded during Phase 9 final integration. The phase READMEs have been corrected to note both the historical and final values.

### 2.3 Software stack
- Web dashboard (index.html + app.js + style.css, now PWA-enabled): Chart.js, Leaflet, Firebase Realtime Database, SheetJS (xlsx export), dark/light theme, pulsing SOS button, offline detection (10s threshold), Map Tracking + Alerts full-screen pages, Reports (daily/weekly/monthly + Excel export + offline-gap detection).
- Android app ("SmartVestCommand"): Kotlin, Material 3, MVVM/Clean Architecture, Firebase backend, Google Maps SDK. 5 tabs: Dashboard, Map, History, Alerts, Settings. Version 1.1.
- Firebase: worker-safety-vest-92b97-default-rtdb.firebaseio.com, vest data under /worker1 (live) and /worker1/logs (history).

### 2.4 Known issue flagged (not yet fixed by user)
receiver_final.ino has the real WiFi SSID/password hardcoded in #define constants. Recommended it be moved to a .gitignore'd Secrets.h before the repo is made public — same treatment already given to Firebase_Config.h. SIM800L test files in the helmet section have the same category of issue (a real phone number hardcoded) — same recommendation given there.

### 2.5 Standing preference for this codebase
Fix/add exactly what's asked, leave every other file untouched — don't refactor unrelated code.

---

## 3. HELMET SECTION — STATUS: Phase 1 complete, Phase 9 integration code drafted

### 3.1 Hardware in hand
Ultrasonic x3, MQ-2 gas sensor, GY-30100 (MAX30100), Arduino Nano x2, ESP-01 (ESP8266, via HW-580 adapter — onboard 1117-3.3 regulator), SIM800L GSM, Buzzer x2, LED x3 (white), LDR, miniature vibration motor (powered via a separate AMS1117 — cannot take direct 5V).

### 3.2 Hardware gaps (status as of last check — may have changed)
Resistor set (220 ohm/1k/10k), NPN transistors x2, 1N4007 diode — needed for the LED array and vibration-motor transistor drivers. SIM800L is currently running directly off the Nano's 5V rail rather than a dedicated 3.7-4.2V/~2A supply (see 3.8 power note).

### 3.3 Architecture
- Nano #1 — Sensor Node: all sensors (Ultrasonic x3, MQ-2, MAX30100) + local alerts (Buzzer x2, Vibration Motor). Self-contained, offline-capable.
- Nano #2 — Communication Node: receives a summary line from Nano #1 over serial, forwards to ESP-01 (WiFi to Firebase) and SIM800L (GSM SOS) in parallel.
- LDR + 3-LED white headlamp array: a completely independent, relay-controlled circuit with its own power source — not wired to either Nano. Turns the LED array on together in low light; no separate status/warning LED exists in this design.
- ESP32-C3 SuperMini was considered as a replacement for the dual-Nano+ESP-01 setup but rejected once ESP-01 was confirmed working.
- Communication redundancy: both WiFi (ESP-01) and GSM (SIM800L) together, user's explicit choice.

### 3.4 Final pin map

**Nano #1 (Sensor Node):**
| Pins | Connects to |
|---|---|
| D2, D3 / D4, D5 / D6, D7 | Ultrasonic 1 (Front) / 2 (Left) / 3 (Right) — Trig, Echo |
| A0 | MQ-2 (analog) |
| A4 (SDA), A5 (SCL) | MAX30100 (I2C) |
| D8 | Buzzer 1 — obstacle alert |
| D9 | Buzzer 2 — gas/health alert |
| D13 | Vibration motor (via NPN transistor, AMS1117-powered) |
| D10 (RX), D11 (TX) | SoftwareSerial link to Nano #2 |

**Nano #2 (Communication Node):**
| Pins | Connects to |
|---|---|
| D0 (RX) — Hardware Serial | Link from Nano #1's D11 |
| D2 (RX), D3 (TX) — SoftwareSerial | ESP-01 adapter (crossed TX/RX) |
| D6 (RX), D7 (TX) — SoftwareSerial | SIM800L |

All grounds (both Nanos, ESP-01 adapter, SIM800L, AMS1117) must be common. Disconnect Nano #2's D0 wire before uploading new code to it (USB conflict otherwise).

### 3.5 Firebase integration
Same project as the vest, under /helmet1 (/helmet1/live for latest status, /helmet1/logs for timestamped history) — kept separate from the vest's /worker1 so data doesn't mix. ESP-01 runs standalone custom Arduino code (not AT-command mode — it was reflashed, see 3.7), parsing a line like OBST:1,GAS:0,HR:78,SPO2:96 from Nano #2 and uploading it via lightweight HTTPS REST (not the full Firebase SDK, due to ESP-01's 1MB flash).

### 3.6 Module function & working principle (summary)
- Ultrasonic x3: time-of-flight distance to near-miss obstacle warning (min distance across all 3 triggers Buzzer 1 + vibration).
- MQ-2: clean-air baseline + relative threshold to gas alert (Buzzer 2 + vibration). Needs 24-48h burn-in before calibration is meaningful.
- MAX30100 (GY-30100): forehead/temple-mounted PPG to heart rate & SpO2 trend (not medical-grade). Needs light-blocking foam and constant gentle pressure; moving-average filter for motion artifacts.
- LDR + LED array: independent relay circuit, not firmware-controlled (see 3.3).
- Buzzer x2: Buzzer 1 = obstacle, Buzzer 2 = gas/health — distinguishable by which one sounds.
- Vibration motor: silent haptic alert via NPN transistor + flyback diode, AMS1117-powered.
- ESP-01: standalone WiFi node, uploads to Firebase (3.5).
- SIM800L: SMS/call SOS backup, AT-command controlled, cooldown-limited (60s) to avoid alert spam.

### 3.7 ESP-01 reflashing story (useful context if it comes up again)
The ESP-01 in use was reused from an older project and had custom (non-AT) firmware, which is why it initially didn't respond to AT commands. It was reflashed using a Nano as a USB-TTL passthrough (Nano RESET tied to GND, ESP-01 GPIO0 tied to GND for flash mode, Board: Generic ESP8266 Module in Arduino IDE). This worked, and the design now runs custom Arduino code directly on the ESP-01 rather than AT-command mode — eliminating the need for AT parsing on Nano #2's side.

### 3.8 Power note (flagged, not yet resolved)
SIM800L is powered directly from the Nano's 5V rail and has tested successfully (SMS + calls both work). Under full-system load (all sensors + both radios running together), this carries brownout/reset risk during SIM800L's transmit current spikes (~2A peak). If instability appears during Phase 9/10 full-system testing, move SIM800L to its own dedicated 3.7-4.2V supply.

### 3.9 Firmware repository structure (as organized for GitHub)
```
helmet/firmware/
├── Phase-01_Component_Verification/   ← standalone test sketches for every module (all verified)
├── Phase-02_Ultrasonic_Obstacle_Detection/   ← pointer README (logic merged into Phase 9)
├── Phase-03_MQ_Gas_Sensor/                   ← pointer README (merged into Phase 9)
├── Phase-04_MAX30100_Vitals/                 ← pointer README (merged into Phase 9)
├── Phase-05_LDR_Headlamp_Relay/               ← docs only, no code (independent circuit)
├── Phase-06_Alert_System/                     ← pointer README (merged into Phase 9)
├── Phase-07_ESP01_WiFi_Firebase/              ← canonical ESP-01 standalone sketch
├── Phase-08_SIM800L_GSM_SOS/                  ← user's call/SMS test sketches (phone number hardcoded — flag before public push)
├── Phase-09_DualNano_Integration/              ← Nano1_SensorNode.ino + Nano2_CommNode.ino — FULL INTEGRATED FIRMWARE
└── Phase-10_Final_Testing_Documentation/       ← placeholder, pending physical field test
```

### 3.10 Roadmap status
1. DONE — Component Verification & Testing — complete (both Nanos, all sensors, ESP-01 WiFi+Firebase, SIM800L SMS+call)
2-4, 6. DONE — Logic implemented, merged directly into the Phase 9 integrated firmware (not yet physically tested as a combined whole)
5. DONE — LDR/LED — independent relay circuit, user-built, not firmware
7. DONE — ESP-01 WiFi to Firebase — implemented and verified standalone
8. DONE — SIM800L GSM SOS — implemented (call/SMS tests verified standalone; cooldown-limited auto-trigger logic drafted in Phase 9 code, not yet physically tested together with everything else)
9. IN PROGRESS — Dual-Nano integration — code written and delivered (Nano1_SensorNode.ino + Nano2_CommNode.ino), not yet confirmed working together as a full physical system
10. PENDING — Final field testing & documentation

Current position: Phase 9 code is ready to upload and test as a complete system. This is the immediate next step.

### 3.11 Limitations (carried forward, still apply)
Forehead MAX30100 is trend-level only; MQ-2 confirmed as the gas sensor (chosen deliberately — covers flammable gas/smoke, not CO; MQ-7 would be needed for CO specifically, considered out of scope for now); no accelerometer on the helmet (no direct head-impact detection, vest-only); SIM800L SOS depends on cellular coverage + SIM card; ultrasonic can misread on reflective/angled surfaces; no onboard GPS on the helmet (location only via correlating with the vest's GPS).

---

## 4. Working Preferences

- Prefers a phase-by-phase guided build; confirms scope/decisions before generating code rather than assuming.
- Wants generated references/diagrams as actual downloadable files (.md, .html, .zip) when requested — organized to match the existing repo's phase-folder convention so they drop in directly.
- Appreciates proactive gap-checking (missing hardware, stale docs, hardcoded secrets) — flag issues rather than assume everything's fine.
- For the vest codebase: change only what's explicitly asked, don't touch unrelated files.
- Has been testing hardware one module at a time, then wants it all combined once individually verified.

---

## 5. Immediate Next Step

Upload Phase-09_DualNano_Integration/Nano1_SensorNode.ino to Nano #1 and Nano2_CommNode.ino to Nano #2 (with EMERGENCY_NUMBER filled in, and Nano #2's D0 wire disconnected during upload), wire everything per 3.4, and run the first full-system test — all sensors + both Nanos + ESP-01 + SIM800L together. Tune threshold values (OBSTACLE_THRESHOLD_CM, GAS_MARGIN, heart rate/SpO2 ranges) based on real results, and watch for the SIM800L power brownout risk noted in 3.8. This is Phase 9/10 territory — once stable, move to final field testing and documentation (Phase 10).
