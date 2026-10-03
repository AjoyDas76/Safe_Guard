# 🦺👷🏻‍♂️ SafeGuard-Smart Safety System for Industrial Workers

An ESP32 and LoRa based **Smart Safety Vest and Smart Safety Helmet** designed for industrial worker safety monitoring, fall detection, GPS tracking, environmental monitoring, emergency SOS alerts, and real-time IoT communication.

SafeGuard combines **ESP32, LoRa, GPS, MPU6050, BME280, MQ-2, MAX30100, Firebase, solar charging, and piezoelectric energy harvesting** to build a connected industrial worker safety system.

## Project Overview

SafeGuard is an IoT-based industrial safety system consisting of:

- 🦺 Smart Safety Vest
- ⛑️ Smart Safety Helmet
- 📍 GPS Worker Tracking
- 🚨 Fall Detection & Emergency SOS
- 🌡️ Environmental Monitoring
- 📡 Long-Range LoRa Communication
- ☁️ Firebase Cloud Monitoring
- 📊 Real-Time Web Dashboard

## Key Features

### 🦺 Smart Safety Vest
- Real-time industrial worker monitoring
- MPU6050-based motion and fall detection
- BME280 temperature and humidity monitoring
- GPS worker location tracking
- LoRa long-range communication
- Emergency SOS alert system
- Firebase IoT cloud synchronization
- Real-time web monitoring dashboard

### ⛑️ Smart Safety Helmet
- Industrial worker helmet safety monitoring
- Ultrasonic obstacle and proximity detection
- MQ-2 harmful gas detection
- MAX30100 heart rate and SpO₂ monitoring
- Wi-Fi and GSM connectivity
- Firebase-based remote monitoring

### ⚡ Smart Power System
- Solar-powered charging
- Piezoelectric energy harvesting
- Rechargeable Li-ion battery system
- Low-power wearable safety architecture

## Technologies Used

`ESP32` `Arduino Nano` `LoRa` `GPS` `MPU6050` `BME280`
`MQ-2` `MAX30100` `Firebase` `IoT` `GSM` `Wi-Fi`

## Project Resources

### 🌐 Live Monitoring Dashboard

[Open SafeGuard Web Dashboard](https://ajoydas76.github.io/Safe_Guard/)

Real-time worker safety monitoring dashboard for sensor data, worker status, alerts, GPS tracking and environmental monitoring.

### 🦺 Smart Safety Vest

[Explore Smart Safety Vest](./vest/)

The Smart Safety Vest focuses on worker fall detection, motion monitoring, environmental sensing, GPS tracking, LoRa communication and emergency safety features.

### ⛑️ Smart Safety Helmet

[Explore Smart Safety Helmet](./helmet/)

The Smart Safety Helmet includes obstacle detection, harmful gas monitoring, heart-rate and SpO₂ monitoring, and wireless communication.

### 📦 Final Project

[Explore Final Project](./final-project/)

Combined project materials, documentation and final-release resources.

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

## System Architecture

```text
SafeGuard
│
├── 🦺 Smart Safety Vest
│   ├── ESP32
│   ├── MPU6050
│   ├── BME280
│   ├── GPS
│   └── LoRa
│
├── ⛑️ Smart Safety Helmet
│   ├── Arduino Nano
│   ├── Ultrasonic Sensor
│   ├── MQ-2
│   ├── MAX30100
│   ├── Wi-Fi
│   └── GSM
│
└── ☁️ IoT Monitoring Platform
    ├── Firebase
    └── Web Dashboard

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


