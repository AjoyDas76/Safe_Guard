# 🦺👷🏻‍♂️ SafeGuard-Smart Safety System for Industrial Workers

An ESP32 and LoRa based Smart Safety Vest and Safety Helmet for industrial worker monitoring, fall detection, GPS tracking, environmental monitoring, emergency SOS and real-time IoT communication.

## Features
- 🌡️ Environmental Monitoring (BME280)
- 📍 GPS Tracking (NEO-M8N)
- 🚶 Motion & Fall Detection (MPU6050)
- 📡 Long-range Communication (SX1278 RA-02 LoRa)
- ♒︎ Object/Proximity Detection (Ultrasonic Sensor)
- 🔥 Harmful Gas Detection (MQ-02)
- 💓 Oximetry and Heart Rate Monitoring
- ☀️ Solar Charging (DFRobot Solar Power Manager 5V)
- 🕳️ Piezo Energy Harvesting (Piezo Elements)
- 🆘 SOS Emergency Button
- 🔋 Battery-powered wearable system

## Hardware
- ESP32 DevKit V1
- Arduino NANO
- BME280
- MPU6050
- NEO-M8N GPS
- SX1278 RA-02
- Ultrasonic Sensor
- MQ-02
- GY-MAX30100
- ESP-01 WiFi
- SIM800L GSM
- 5V Flexible Solar Panel
- Piezo Elements 
- DFRobot Solar Power Manager 5V
- 18650 Li-ion Battery
- Active Buzzer
- SOS Push Button
- On/Off Switch

## Repository Structure
```
.
├── index.html          ← Web dashboard (must stay at repo root for GitHub Pages)
├── sw.js                ← Dashboard's service worker (root-scoped, must stay here)
│
├── vest/                 ← Smart Safety Vest — ✅ Complete
│   ├── README.md
│   ├── VEST-CONNECTIONS.md   (full hardware wiring reference)
│   ├── firmware/              (Phase-01 through Phase-10)
│   ├── diagrams/
│   ├── datasheets/
│   ├── docs/
│   ├── hardware/
│   ├── images/
│   ├── results/
│   └── thesis/
│
├── helmet/               ← Smart Safety Helmet — 🚧 In Progress
│   ├── README.md
│   ├── firmware/
│   │   └── Phase-01_Component_Verification/
│   ├── diagrams/
│   ├── datasheets/
│   └── docs/
│
└── final-project/        ← Combined final-release deliverables (report, final
                             presentation, consolidated diagrams, BOM, results)
                             for the project as a whole, once both sections
                             are complete
```

## Sections

- **[`/vest`](./vest)** — LoRa-based Smart Safety Vest: fall detection (MPU6050), environmental sensing (BME280), GPS tracking, LoRa communication, Firebase cloud sync, web dashboard, and Android app.
- **[`/helmet`](./helmet)** — Dual-Nano Smart Safety Helmet: near-miss obstacle detection (ultrasonic), gas monitoring (MQ-2), vitals (MAX30100), WiFi (ESP-01) + GSM (SIM800L) redundant connectivity to the same Firebase project.
- **[`/final-project`](./final-project)** — Combined final-release materials for the completed system.
- **Root (`index.html`, `sw.js`)** — the live web dashboard, kept at the repository root since GitHub Pages serves from root and the service worker's scope depends on its path.

## Status

| Section | Status |
|---|---|
| Vest | ✅ Complete |
| Helmet | 🚧 Phase 1 (Component Verification) nearly done |
| Final Project | ⏳ Pending both sections' completion |


## Team
- **Ajoy Das**
- **Shafiul Azam**
- **Ritu Biswas**
- **Abdullah Al Mamun**
- **Abdul Halim**


<img width="1149" height="1369" alt="Roadmap" src="https://github.com/user-attachments/assets/29d0c561-884e-4e45-ae82-a04fb72beb29" />


