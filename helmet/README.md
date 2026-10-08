# SafeGuard Helmet

Second section of SafeGuard, alongside the [SafeGuard Vest](../vest). A dual-Arduino-Nano helmet for near-miss obstacle detection, hazardous gas monitoring, wearer vitals tracking, and auto headlamp control — with WiFi connectivity back to the same Firebase project as the vest.

**Status: Phase 1-8 complete, Phase 9 (full system integration) coded and ready to test, Phase 10 pending**

## Architecture

- **Nano #1 — Sensor Node:** reads all sensors (Ultrasonic ×3, MQ-2, MAX30100), drives local alerts (Buzzer ×2 — one active with a beep-beep pattern, one passive with an ambulance-style siren tone — plus a Vibration Motor). Self-contained, works offline.
- **Nano #2 — Communication Node:** receives data from Nano #1 over serial, forwards it via ESP-01 to Firebase.
- **LDR + LED headlamp array (×3 white LEDs):** an independent, relay-controlled circuit — not connected to either Nano.

> **SIM800L GSM SOS is not on the helmet** — it was moved to the vest, since an emergency SMS is far more useful carrying the worker's GPS location and fall/SOS status, both of which live on the vest. See `vest/firmware/Phase-09_System_Integration/transmitter_final.ino`.

## Hardware

Ultrasonic ×3, MQ-2 gas sensor, GY-30100 (MAX30100), Arduino Nano ×2, ESP-01 (ESP8266, via HW-580 adapter), Buzzer ×2 (1 active, 1 passive), LED ×3 (white), LDR, miniature vibration motor.

## Roadmap

1. ✅ Component Verification & Testing — see `firmware/Phase-01_Component_Verification/`
2. ✅ Ultrasonic Obstacle/Proximity Detection
3. ✅ MQ Gas Sensor — threshold & alert logic
4. ✅ GY-MAX30100 — forehead placement, filtering & vitals threshold
5. ✅ LDR — auto headlamp LED control (independent relay circuit)
6. ✅ Alert System Integration — buzzer tones + vibration motor
7. ✅ ESP-01 WiFi → Firebase upload
8. ➡️ *(SIM800L GSM SOS — moved to the vest, see note above)*
9. 🚧 Dual-Nano System Integration — firmware written (`Nano1_SensorNode.ino` + `Nano2_CommNode.ino`), not yet tested as a complete physical system
10. ⏳ Final field testing & documentation

## Folder structure

```
helmet/
├── firmware/
│   ├── Phase-01_Component_Verification/   ← individual test sketches, one per module
│   ├── Phase-02 .. Phase-06/                ← standalone per-subsystem sketches
│   ├── Phase-07_ESP01_WiFi_Firebase/
│   ├── Phase-08_SIM800L_GSM_SOS/            ← historical record only, feature moved to vest
│   ├── Phase-09_DualNano_Integration/       ← full combined firmware
│   └── Phase-10_Final_Testing_Documentation/
├── diagrams/
├── datasheets/
└── docs/
```
