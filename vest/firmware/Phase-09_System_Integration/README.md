# Phase-09 System Integration

## Objective
Combine every subsystem (MPU6050 motion/fall detection, GPS, LoRa, Firebase cloud, mobile app, and safety features) into one final worker-unit firmware and one final base-station firmware, then test the complete pipeline end-to-end.

## Tasks

- [ ] 9.1 Sensor Integration
- [ ] 9.2 GPS + LoRa Integration
- [ ] 9.3 Firebase + Mobile App Integration
- [ ] 9.4 End-to-End Testing
- [ ] 9.5 Performance Optimization
- [ ] 9.6 System Stability Testing
- [ ] 9.7 Integration Finalization

## Components
_(All hardware from Phases 1–8 combined onto the final worker-unit vest and base station.)_

## Wiring

Pulled directly from `transmitter_final.ino` / `receiver_final.ino` — this is the source of truth for the physical build (supersedes the pin numbers in the earlier Phase 3–5 individual-module READMEs). Full breakdown with power-system and battery-monitor wiring: see `VEST-CONNECTIONS.md` in the repo root.

**Vest Transmitter (ESP32 #1):**

| Subsystem | Pin | ESP32 GPIO |
|---|---|---|
| LoRa SCK / MISO / MOSI / NSS / RESET / DIO0 | | 18 / 19 / 23 / 5 / 14 / 2 |
| BME280, MPU6050, OLED (shared I2C) | SDA / SCL | 21 / 22 |
| GPS (NEO-M8N) | RX2 / TX2 | 16 / 17 |
| SOS button | Signal (INPUT_PULLUP) | 15 |
| Buzzer | Signal | 12 |
| LED | Signal | 13 |
| Battery monitor | ADC (via voltage divider) | 34 |

**Base Station Receiver (ESP32 #2):** LoRa on the same SCK/MISO/MOSI/NSS/RESET/DIO0 pins as above (18/19/23/5/14/2) — no sensors, WiFi + Firebase only.

## Code
_(Target: `Smart_Safety_Vest_Final.ino` for the worker unit, `Base_Station_Final.ino` for the receiver — bringing together the transmitter logic already built in Phase 5.6/5.8, plus Phase 8's safety features and Phase 6's cloud upload.)_

## Test Results
_(Pending)_

## Notes
- 9.2 (GPS + LoRa Integration) is largely already done in `5.5_Transmitter_GPS.ino` and `5_6_Transmitter_FallAlert.ino` — this task is mainly about merging that with the finalized MPU6050 module (Phase 3.8) and the new Phase 8 safety features into a single sketch.
- This is the phase where code from multiple `.ino` files gets merged — expect to need `.h`/`.cpp` files instead of one large `.ino`, for maintainability.

## Status

⏳ **Pending**
