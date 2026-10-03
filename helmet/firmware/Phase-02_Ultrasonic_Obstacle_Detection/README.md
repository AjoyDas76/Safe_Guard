# Phase 2 — Ultrasonic Obstacle/Proximity Detection

Standalone verification: see `Phase-01_Component_Verification/Ultrasonic_Test.ino`.

The sensor-fusion and threshold logic (minimum-distance check across all three sensors, triggering Buzzer 1 + the vibration motor) is implemented directly in the combined firmware at `Phase-09_DualNano_Integration/Nano1_SensorNode.ino`, rather than as a separate standalone sketch.
