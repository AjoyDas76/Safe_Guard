# Phase 9 — Dual-Nano System Integration

This is the full combined firmware, with the logic from Phases 2, 3, 4, 6, 7, and 8 all integrated into two sketches — one per Nano.

## Files
- `Nano1_SensorNode.ino` — all sensors + local alerts (Ultrasonic ×3, MQ-2, MAX30100, Buzzer ×2, Vibration Motor)
- `Nano2_CommNode.ino` — WiFi forwarding (ESP-01) + GSM SOS backup (SIM800L)

## Full wiring

**Nano #1 — Sensor Node**

| Module | Pin |
|---|---|
| Ultrasonic 1 (Front) | Trig D2, Echo D3 |
| Ultrasonic 2 (Left) | Trig D4, Echo D5 |
| Ultrasonic 3 (Right) | Trig D6, Echo D7 |
| Buzzer 1 (obstacle) | D8 |
| Buzzer 2 (gas/health) | D9 |
| MQ-2 | A0 |
| MAX30100 (I2C) | SDA A4, SCL A5 |
| Vibration Motor | D13 → transistor (powered via AMS1117, not direct 5V) |
| Link to Nano #2 | D10 (RX), D11 (TX) — SoftwareSerial |

**Nano #2 — Communication Node**

| Module | Pin |
|---|---|
| Link from Nano #1 | D0 (RX) — Hardware Serial, wired to Nano #1's D11 |
| ESP-01 adapter | D2 (RX), D3 (TX) — SoftwareSerial, crossed with the adapter's TX/RX |
| SIM800L | D6 (RX), D7 (TX) — direct 5V power (see note below) |

All grounds (both Nanos, ESP-01 adapter, SIM800L, AMS1117) must be common.

> The LDR + 3-LED headlamp array is **not** part of this firmware — it's a separate, independent relay-controlled circuit (see `Phase-05_LDR_Headlamp_Relay/`).

## Before uploading
- Disconnect D0 on Nano #2 (the wire from Nano #1) before uploading `Nano2_CommNode.ino`, or the USB upload will conflict with it.
- Set `EMERGENCY_NUMBER` in `Nano2_CommNode.ino` to the tested phone number, with country code.
- Threshold values (`OBSTACLE_THRESHOLD_CM`, `GAS_MARGIN`, heart rate/SpO2 ranges) are starting points — tune them after physical testing on the actual helmet.
- `Nano2_CommNode.ino` expects the ESP-01 to be running `Phase-07_ESP01_WiFi_Firebase/ESP01_WiFi_Firebase.ino` already flashed and in standalone mode (not AT-command mode).

## Power note
SIM800L is currently powered directly from the Nano's 5V rail (confirmed working in testing — SMS and calls both receive successfully). Under full-system load this carries some brownout/reset risk during SIM800L's transmit current spikes (~2A peak) — if instability shows up once everything is running together, moving SIM800L to its own dedicated 3.7–4.2V supply is the fix.
