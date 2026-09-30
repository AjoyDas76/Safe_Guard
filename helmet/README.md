# Smart Safety Helmet

Second section of the Smart Industrial Safety System, alongside the [Smart Safety Vest](../). A dual-Arduino-Nano helmet for near-miss obstacle detection, hazardous gas monitoring, wearer vitals tracking, and auto headlamp control — with WiFi + GSM redundant connectivity back to the same Firebase project as the vest.

**Status: 🚧 In Progress — Phase 1 (Component Verification) nearly complete**

## Architecture

- **Nano #1 — Sensor Node:** reads all sensors (Ultrasonic ×3, MQ-2, MAX30100), drives local alerts (Buzzer ×2, Vibration Motor). Self-contained, works offline.
- **Nano #2 — Communication Node:** receives data from Nano #1 over serial, forwards it via ESP-01 (WiFi → Firebase) and SIM800L (GSM SOS backup) in parallel.
- **LDR + LED headlamp array (×3 white LEDs):** an independent, relay-controlled circuit — not connected to either Nano.

## Hardware

Ultrasonic ×3, MQ-2 gas sensor, GY-30100 (MAX30100), Arduino Nano ×2, ESP-01 (ESP8266, via HW-580 adapter), SIM800L GSM, Buzzer ×2, LED ×3 (white), LDR, miniature vibration motor.

## Roadmap

1. **Component Verification & Testing** *(current — see `firmware/Phase-01_Component_Verification/`)*
2. Ultrasonic Obstacle/Proximity Detection
3. MQ Gas Sensor — threshold & alert logic
4. GY-MAX30100 — forehead placement, filtering & vitals threshold
5. LDR — auto headlamp LED control (independent relay circuit)
6. Alert System Integration — buzzer tones + vibration motor
7. ESP-01 WiFi → Firebase upload
8. SIM800L GSM — SOS SMS/call backup
9. Dual-Nano System Integration
10. Final field testing & documentation

## Folder structure

```
helmet/
├── firmware/
│   └── Phase-01_Component_Verification/   ← individual test sketches, one per module
├── diagrams/
├── datasheets/
└── docs/
```
