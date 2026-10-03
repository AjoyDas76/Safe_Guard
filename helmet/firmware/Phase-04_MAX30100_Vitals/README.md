# Phase 4 — GY-MAX30100 Forehead Placement, Filtering & Vitals Threshold

Standalone verification: see `Phase-01_Component_Verification/MAX30100_Test.ino`.

The vitals-threshold alert logic (abnormal heart rate / SpO2 triggering the health-alert path) is implemented directly in the combined firmware at `Phase-09_DualNano_Integration/Nano1_SensorNode.ino`, rather than as a separate standalone sketch.

Forehead/temple placement notes: mount at the padding where it's held snug against skin, with foam/felt light-blocking around the sensor, and treat readings as trend-level rather than medical-grade (see the sensor reference doc).
