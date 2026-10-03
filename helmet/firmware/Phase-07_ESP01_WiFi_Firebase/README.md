# Phase 7 — ESP-01 WiFi → Firebase Upload

`ESP01_WiFi_Firebase.ino` is uploaded directly to the ESP-01 itself (Generic ESP8266 Module board, flashed via the Nano-passthrough method — see Phase 1's README for the flashing procedure). It runs standalone, listening on Serial for a line like:

```
OBST:1,GAS:0,HR:78,SPO2:96
```

(sent by `Phase-09_DualNano_Integration/Nano2_CommNode.ino`) and uploads it to Firebase:
- `/helmet1/live` — latest status (overwritten each time)
- `/helmet1/logs` — timestamped history (appended)

Reuses the same Firebase project as the vest, under a separate `/helmet1` node.

Fill in `ssid`, `password`, and `databaseSecret` before flashing. Get the database secret from Firebase Console → Project Settings → Service accounts → Database secrets.

Verified working: HTTP 200, `/helmet1/live` and `/helmet1/logs` both populating correctly.
