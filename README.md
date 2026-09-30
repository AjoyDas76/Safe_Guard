# Smart Industrial Safety System — Vest + Helmet

A two-part academic embedded systems project: a LoRa-based industrial safety vest and a WiFi/GSM-connected safety helmet, sharing one Firebase backend.

## Repository structure

```
.
├── index.html          ← Web dashboard (must stay at repo root for GitHub Pages)
├── sw.js                ← Dashboard's service worker (root-scoped, must stay here)
│
├── vest/                 ← Smart Safety Vest — ✅ Complete
│   ├── README.md
│   ├── VEST-CONNECTIONS.md   (full hardware wiring reference)
│   ├── firmware/              (Phase-01 through Phase-10)
│   ├── diagrams/
│   ├── datasheets/
│   ├── docs/
│   ├── hardware/
│   ├── images/
│   ├── results/
│   └── thesis/
│
├── helmet/               ← Smart Safety Helmet — 🚧 In Progress
│   ├── README.md
│   ├── firmware/
│   │   └── Phase-01_Component_Verification/
│   ├── diagrams/
│   ├── datasheets/
│   └── docs/
│
└── final-project/        ← Combined final-release deliverables (report, final
                             presentation, consolidated diagrams, BOM, results)
                             for the project as a whole, once both sections
                             are complete
```

## Sections

- **[`/vest`](./vest)** — LoRa-based Smart Safety Vest: fall detection (MPU6050), environmental sensing (BME280), GPS tracking, LoRa communication, Firebase cloud sync, web dashboard, and Android app.
- **[`/helmet`](./helmet)** — Dual-Nano Smart Safety Helmet: near-miss obstacle detection (ultrasonic), gas monitoring (MQ-2), vitals (MAX30100), WiFi (ESP-01) + GSM (SIM800L) redundant connectivity to the same Firebase project.
- **[`/final-project`](./final-project)** — Combined final-release materials for the completed system.
- **Root (`index.html`, `sw.js`)** — the live web dashboard, kept at the repository root since GitHub Pages serves from root and the service worker's scope depends on its path.

## Status

| Section | Status |
|---|---|
| Vest | ✅ Complete |
| Helmet | 🚧 Phase 1 (Component Verification) nearly done |
| Final Project | ⏳ Pending both sections' completion |
