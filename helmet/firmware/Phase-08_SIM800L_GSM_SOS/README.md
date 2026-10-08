# Phase 8 — SIM800L GSM SOS (Moved to Vest)

**SIM800L has been moved from the helmet to the vest.** Reasoning: an emergency SMS/call is far more useful when it carries the worker's GPS location and fall/SOS status — both of which live on the vest's sensors, not the helmet's. See `vest/firmware/Phase-09_System_Integration/transmitter_final.ino` for the current implementation (SMS triggered on fall detection, SOS button press, or high temperature, including the worker's last known GPS coordinates).

The two test sketches here (`sim800l_call_test.ino`, `sim800l_msg_test.ino`) are kept as a historical record of the original bench testing done on the helmet during Phase 1 — the wiring and AT-command approach they demonstrate is the same one now used on the vest, just on different hardware. They are **not part of the helmet's current firmware**.

> Same caution as before: both files have a real phone number hardcoded — keep that in mind if this folder is kept in a public repo.
