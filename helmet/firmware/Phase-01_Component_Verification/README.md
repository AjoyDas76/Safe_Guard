# Phase 1 — Component Verification & Testing

Goal: confirm every physical unit (both Arduino Nanos and every sensor/module) works correctly on its own, before writing any integration logic.

## Status

| Module | Test file | Status |
|---|---|---|
| Nano #1 | `Nano_Blink_Serial_Test.ino` | ✅ Verified |
| Nano #2 | `Nano_Blink_Serial_Test.ino` | ✅ Verified |
| Ultrasonic ×3 (Front/Left/Right) | `Ultrasonic_Test.ino` | ✅ Verified |
| MQ-2 gas sensor | `MQ2_Gas_Test.ino` | ✅ Verified |
| MAX30100 (GY-30100) | `MAX30100_Test.ino` | ✅ Verified — ~60-70 bpm, ~95-96% SpO2 on fingertip |
| Buzzer ×2 (active type) | `Buzzer_Test.ino` | ✅ Verified |
| Vibration motor | `Vibration_Motor_Test.ino` | ✅ Verified (powered via AMS1117, not direct 5V) |
| ESP-01 (reflashed with custom firmware) | `ESP01_AT_BaudScan_Test.ino` → `ESP01_WiFi_Test.ino` | ✅ Verified — connects to WiFi, gets an IP |
| ESP-01 → Firebase upload | `ESP01_Firebase_Test.ino` | ✅ Verified — HTTP 200, `/helmet1/live` and `/helmet1/logs` populated |
| SIM800L GSM | *(pending)* | ⏳ Waiting on a dedicated 3.7–4.2V (~2A peak) power source |
| LDR + LED headlamp | — | Not applicable to Nano firmware — this is an independent relay-based circuit, not connected to either Nano |

## Notes / decisions made during this phase

- The ESP-01 module in use was reused from an older project and had custom (non-AT) firmware on it. It was reflashed using a Nano as a USB-TTL passthrough (Nano's RESET tied to GND, GPIO0 tied to GND on the ESP-01 for flash mode) with **Board: Generic ESP8266 Module** in Arduino IDE.
- Because reflashing worked, the design now runs **custom Arduino code directly on the ESP-01** (not AT-command mode) — Nano #2 will send it a simple text line over serial, and the ESP-01 itself handles the WiFi + Firebase upload. This removed the need for AT-command parsing on Nano #2's side.
- The miniature vibration motor cannot take direct 5V — it's powered through an AMS1117 regulator instead.
- The LDR is **not** wired to either Nano. It's a separate, independent circuit (its own power source, relay-controlled) that switches the 3-LED headlamp array automatically in low light.
- An ESP32-C3 SuperMini was considered as a replacement for the dual-Nano + ESP-01 setup, but the project is staying with the original architecture since the ESP-01 is now working.
- Firebase reuses the same project as the vest (`worker-safety-vest-92b97-default-rtdb.firebaseio.com`), under a separate `/helmet1` node so vest (`/worker1`) and helmet data don't mix. Writes require the database's legacy secret as `?auth=`.

## Still needed for this phase

- SIM800L GSM module test (AT response + network registration) — blocked on a dedicated 3.7–4.2V/~2A power source.
