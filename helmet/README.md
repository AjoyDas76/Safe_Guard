# ⛑️ SafeGuard Smart Safety Helmet

The **SafeGuard Smart Safety Helmet** is an industrial worker safety system designed for **near-miss obstacle detection, hazardous gas monitoring, wearer vital-sign monitoring, automatic headlamp control, and emergency communication**.

It is the second major subsystem of the **SafeGuard Smart Industrial Safety System**, working alongside the Smart Safety Vest.

The helmet uses a **dual-Arduino-Nano architecture** with **Ultrasonic Sensors, MQ-2 Gas Sensor, MAX30100, ESP-01 WiFi and SIM800L GSM** to provide local safety monitoring and redundant wireless connectivity to the same Firebase platform used by the Smart Safety Vest.

---

## 🚧 Development Status

**Status: In Progress — Phase 1: Component Verification**

The component verification phase is currently in progress, with Phase 1 nearly complete.

---

# 🏗️ System Architecture

The Smart Safety Helmet uses two Arduino Nano controllers with separate responsibilities.

```text
                    ⛑️ SMART SAFETY HELMET
                             │
              ┌──────────────┴──────────────┐
              │                             │
        Arduino Nano #1               Arduino Nano #2
         Sensor Node                Communication Node
              │                             │
      ┌───────┼────────┐            ┌───────┼────────┐
      │       │        │            │                │
 Ultrasonic  MQ-2   MAX30100      ESP-01          SIM800L
    ×3       Gas     Vitals       WiFi             GSM
      │       │        │            │                │
      └───────┴────────┘            │                │
              │                     │                │
       Local Safety Alerts          └───────┬────────┘
              │                             │
       ┌──────┴──────┐                 Firebase
       │             │                     │
    Buzzer ×2   Vibration Motor       Cloud Monitoring
```

---

# 🔹 Nano #1 — Sensor Node

**Arduino Nano #1** is responsible for sensor acquisition and local safety response.

It reads:

- Ultrasonic Sensor ×3
- MQ-2 Gas Sensor
- MAX30100

It also controls:

- Buzzer ×2
- Vibration Motor

The Sensor Node is designed to operate as a **self-contained offline safety unit**, allowing local alerts without depending on an internet connection.

---

# 🔹 Nano #2 — Communication Node

**Arduino Nano #2** is responsible for communication and data forwarding.

It receives sensor information from Nano #1 through serial communication and forwards the information through two communication paths.

### ESP-01 WiFi

```text
Nano #1 → Nano #2 → ESP-01 → WiFi → Firebase
```

### SIM800L GSM Backup

```text
Nano #1 → Nano #2 → SIM800L → GSM → Emergency SOS Backup
```

---

# 🚧 Ultrasonic Obstacle & Proximity Detection

The Smart Safety Helmet uses **three ultrasonic sensors** for obstacle and proximity detection.

The system is designed to support:

- Nearby obstacle detection
- Proximity monitoring
- Near-miss detection
- Worker environmental awareness

---

# 🧪 MQ-2 Gas Detection

The **MQ-2 gas sensor** is used for hazardous gas monitoring.

The gas-monitoring subsystem is designed to:

- Monitor gas conditions
- Detect potentially hazardous gas levels
- Generate safety alerts
- Provide gas-related information to the communication system

---

# ❤️ MAX30100 Wearer Vital Monitoring

The **MAX30100** sensor is used for wearer vital-sign monitoring.

The project includes development of:

- Heart-rate monitoring
- SpO₂ monitoring
- Forehead sensor placement
- Signal filtering
- Vital-sign threshold logic

---

# 💡 Automatic Headlamp System

The Smart Safety Helmet includes an independent automatic headlamp system.

It consists of:

- LDR
- White LED ×3
- Relay-controlled circuit

```text
Ambient Light
      ↓
     LDR
      ↓
Relay Control
      ↓
White LED ×3
```

The headlamp circuit is **independent** and is not connected directly to either Arduino Nano.

---

# 🔊 Local Safety Alert System

The helmet includes local alert hardware for immediate safety feedback.

### Buzzer

**Buzzer ×2** provides audible safety alerts.

### Vibration Motor

The vibration motor provides tactile feedback to the helmet wearer.

---

# 📡 WiFi & GSM Communication

The helmet uses two communication technologies.

## ESP-01 WiFi

The **ESP-01 / ESP8266** provides WiFi connectivity and is used to upload helmet data to Firebase.

## SIM800L GSM

The **SIM800L GSM module** provides a backup communication path for emergency SOS communication.

---

# ☁️ Firebase IoT Integration

The Smart Safety Helmet is designed to connect to the same **Firebase project** used by the Smart Safety Vest.

```text
Smart Safety Helmet
        │
   WiFi / GSM
        │
        ▼
     Firebase
        ▲
        │
Smart Safety Vest
```

---

# 🧩 Hardware Components

| Component | Quantity | Function |
|---|---:|---|
| Arduino Nano | 2 | Sensor and communication control |
| Ultrasonic Sensor | 3 | Obstacle/proximity detection |
| MQ-2 Gas Sensor | 1 | Hazardous gas monitoring |
| GY-MAX30100 | 1 | Heart-rate and SpO₂ monitoring |
| ESP-01 / ESP8266 | 1 | WiFi communication |
| SIM800L GSM | 1 | GSM communication / SOS backup |
| Buzzer | 2 | Audible safety alerts |
| Vibration Motor | 1 | Tactile safety alert |
| LDR | 1 | Ambient light detection |
| White LED | 3 | Automatic headlamp |

---

# 🗺️ Development Roadmap

## Phase 1 — Component Verification & Testing

**Current Phase**

- Individual hardware component verification
- Sensor testing
- Communication module testing

Firmware:

`firmware/Phase-01_Component_Verification/`

## Phase 2 — Ultrasonic Obstacle & Proximity Detection

- Ultrasonic sensor integration
- Distance measurement
- Obstacle detection
- Proximity detection
- Near-miss detection

## Phase 3 — MQ-2 Gas Sensor

- MQ-2 sensor integration
- Gas monitoring
- Threshold detection
- Safety alert logic

## Phase 4 — GY-MAX30100 Vital Monitoring

- MAX30100 integration
- Forehead sensor placement
- Heart-rate monitoring
- SpO₂ monitoring
- Signal filtering
- Vital-sign threshold logic

## Phase 5 — LDR Automatic Headlamp Control

- LDR integration
- Ambient light detection
- Relay control
- Automatic LED headlamp control

The headlamp remains an independent circuit.

## Phase 6 — Alert System Integration

- Buzzer ×2
- Vibration motor
- Safety alert tones
- Sensor-triggered warnings

## Phase 7 — ESP-01 WiFi → Firebase

- ESP-01 communication
- WiFi connectivity
- Firebase connection
- Helmet data upload
- Cloud monitoring

## Phase 8 — SIM800L GSM Emergency Backup

- SIM800L integration
- GSM communication
- SOS SMS
- SOS call backup

## Phase 9 — Dual-Nano System Integration

```text
Nano #1 — Sensor Node
        ↓
Serial Communication
        ↓
Nano #2 — Communication Node
        ↓
ESP-01 / SIM800L
```

## Phase 10 — Final Field Testing & Documentation

- Complete system testing
- Field testing
- Performance verification
- Communication testing
- Safety-function verification
- Documentation
- Final project preparation

---

# 📁 Project Structure

```text
helmet/
│
├── README.md
│
├── firmware/
│   └── Phase-01_Component_Verification/
│       └── Individual module test sketches
│
├── diagrams/
│
├── datasheets/
│
└── docs/
```

---

# 🛠️ Technologies Used

```text
Arduino Nano
Ultrasonic Sensor
MQ-2
MAX30100
ESP-01 / ESP8266
SIM800L GSM
LDR
White LED
Buzzer
Vibration Motor
WiFi
GSM
Firebase
IoT
Industrial Safety
Worker Safety
Obstacle Detection
Gas Detection
Vital Monitoring
```

---

# 🎯 Project Objective

The objective of the Smart Safety Helmet is to provide an additional layer of protection for industrial workers through:

**Obstacle Detection + Gas Monitoring + Vital Monitoring + Automatic Headlamp Control + Local Alerts + WiFi/GSM Communication**

The helmet is designed to operate as a dedicated subsystem of the larger **SafeGuard Smart Industrial Safety System**.

---

# 🔗 SafeGuard Smart Industrial Safety System

The Smart Safety Helmet works alongside the Smart Safety Vest as part of the complete SafeGuard system.

```text
                 SafeGuard
                     │
          ┌──────────┴──────────┐
          │                     │
    🦺 Smart Safety Vest   ⛑️ Smart Safety Helmet
          │                     │
        ESP32              Dual Arduino Nano
          │                     │
        LoRa             WiFi + GSM
          │                     │
          └──────────┬──────────┘
                     │
                  Firebase
                     │
              Monitoring System
```

---

# 👨‍💻 Project Information

**Project:** SafeGuard – Smart Industrial Safety System  
**Module:** Smart Safety Helmet  
**Application:** Industrial Worker Safety  
**Status:** In Progress — Phase 1  
**Developer:** Ajoy Das Team
