# Phase 8 — SIM800L GSM SOS Backup

Standalone test sketches: `sim800l_call_test.ino` (places a call) and `sim800l_msg_test.ino` (sends an SMS). Both verified working — SMS and call both received successfully.

**Wiring:**
| Arduino | SIM800L |
|---|---|
| +5V | VCC |
| GND | GND |
| D6 | TXD |
| D7 | RXD |

Powered directly from the Nano's 5V rail (no separate 3.7–4.2V supply) — see the power note in `Phase-09_DualNano_Integration/README.md` for the brownout caveat under full-system load.

The combined SOS-trigger logic (cooldown-limited, fired automatically on an obstacle or gas alert) is implemented in `Phase-09_DualNano_Integration/Nano2_CommNode.ino`.

> **Before pushing this publicly:** both test files have a real phone number hardcoded in `ATD+...` / `AT+CMGS=...`. Consider moving it into a separate, `.gitignore`d config file before this goes into a public repo — same practice as keeping WiFi/Firebase credentials out of committed code.
