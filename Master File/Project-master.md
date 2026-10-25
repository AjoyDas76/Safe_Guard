# SafeGuard — Smart Safety Vest & Helmet: সম্পূর্ণ প্রজেক্ট ডকুমেন্ট

> তৈরি: ২০২৬-১০-২৫ · প্রজেক্ট: "vest+helmet" · মালিক: Ajoy
> এই ফাইলে আছে: (১) আপনার সব নির্দেশনা ও পছন্দ, (২) মেমোরিতে থাকা তথ্য, (৩) পুরো সিস্টেমের বিবরণ (hardware, firmware, cloud, web, app), (৪) থিসিস, (৫) এ পর্যন্ত ডেলিভারি, (৬) জানা অসংগতি, (৭) বাকি কাজ।
> কোনো secret/API key/password এখানে নেই।

---

## ১. মালিক ও কাজের ধরন
- নাম: **Ajoy**; ইমেইল: ajoy.pc22@gmail.com
- Android অ্যাপ ডেভেলপার (একাই কাজ করেন); Kotlin, Firebase, ESP32 IoT, web (HTML/CSS/JS)।
- Native Android, React Native ও WebView — তিন ধরনেই কাজ করেছেন; LoRa-ভিত্তিক wireless sensor system চেনেন।
- দ্রুত iterate করে full-stack IoT ও media অ্যাপ বানানোর অভ্যাস।
- ভাষা: ইংরেজি ও বাংলা; টেকনিক্যাল ব্যাখ্যা প্রায়ই **বাংলায়** চান।
- সময় অঞ্চল: Asia/Dhaka (UTC+6)।
- অন্য প্রজেক্ট (মেমোরিতে): AjoyTV (বাংলা live TV, Kotlin + ExoPlayer); ছাত্রদের Admit Card Excel তৈরি (openpyxl); একটি বাংলা print-ready ডকুমেন্ট (সোর্স পাওয়া যায়নি, কাজ অসম্পূর্ণ)।

## ২. আপনার স্থায়ী নির্দেশনা ও পছন্দ
1. **শুধু যে সমস্যা বলা হয়েছে সেটাই ঠিক করো; বাকি কোড অক্ষত রাখো।**
2. **সম্পূর্ণ deliverable zip** চান (Android Studio-তে খোলার মতো প্রস্তুত)।
3. ব্যাখ্যা/নির্দেশনা প্রায়ই **বাংলায়**।
4. কিছু অস্পষ্ট হলে আগে জিজ্ঞাসা করো।
5. Secret মান প্রিন্ট করা যাবে না; শুধু ফাইল ও লাইন নম্বর জানাবে।

### কাজ-নির্দিষ্ট নির্দেশনা (সময়ক্রমে)
**Website + Helmet ডেটা**
- Helmet-এর ডেটা vest-এর মতো করে দেখাতে হবে।
- Vest-এর ৪টি block-এর নিচে helmet-এর ৪টি block (HR, SpO₂, Gas, Obstacle)।
- Live temperature/humidity/pressure chart-এর নিচে helmet-এর live HR/SpO₂/Gas chart।
- প্রতিটি block/card-এর ডিজাইন একই থাকবে।
- আলাদা সেকশন নয় — সবকিছু বিদ্যমান card-এ merge: system card-এর পাশে helmet online/offline; worker info card-এ helmet তথ্য; device connection card-এ helmet connection; proximity ও live-distance card বাদ; helmet alert/event বিদ্যমান Active Alerts ও History-তে।
- Device Connection card বাদ, map বড়; vest offline/online; helmet অনেক দেরিতে offline হওয়ার সমস্যা ঠিক করা (এই fix আলাদাভাবে প্রয়োগ হয়নি বলে নোট আছে)।
- পরে: "ওয়েবসাইটের কাজ শেষ, আর কিছু করতে হবে না" — ডেলিভার করা zip GitHub-এ আপলোড করা হয়েছে।

**Repo audit ও README**
- `Safe_Guard-main_1.zip` (AI integration সহ): সব ফাইল চেক করে অসংগতি জানাতে হবে।
- সব README পরীক্ষা করে আপডেট করতে হবে → ১৩টি README আপডেট করে দেওয়া হয়েছে (আপনি GitHub-এ দিয়েছেন)।
- "রিপো চালিয়ে দেখো" → headless test হয়েছে।

**থিসিস**
- ৬০+ পেজ, ইংরেজি, standard format, **IEEE** reference, **PDF**।
- যেসব ফলাফল নেই সেখানে **placeholder** রাখা (বানানো মান নয়)।
- বিভাগের তথ্য (supervisor, student info) ফর্মের ছবি থেকে বসানো।
- **A4**, কোনো blank page নয়; কোনো পেজে ১–২ লাইন থাকলে আগের পেজে এনে ফেলা।
- মোবাইল অ্যাপের ফিচার ও সব ফাংশন থিসিসে অন্তর্ভুক্ত করা (সম্পন্ন)।

## ৩. প্রতিষ্ঠান ও থিসিস তথ্য
- **Port City International University (PCIU)**, Dept. of EEE; B.Sc. থিসিস।
- Supervisor: **Akib Jayed Islam**; Batch: **EEE-31-E**।
- Title page-এর তারিখ এখনো "[Month, Year]" (আপনাকে দিতে হবে)।

## ৪. সিস্টেম আর্কিটেকচার (চার স্তর)
1. **Wearable**: Vest (ESP32) ও Helmet (২×Nano + ESP-01)।
2. **Communication**: LoRa 433 MHz (vest→base), Wi-Fi (base→cloud, helmet→cloud), GSM SMS (vest জরুরি)।
3. **Cloud**: Firebase Realtime Database, প্রজেক্ট `worker-safety-vest-92b97`।
4. **Application**: Web dashboard (PWA) + Android app **SafeGuard Command**।

### 4.১ Smart Vest (ESP32)
- সেন্সর/মডিউল: BME280 (temp/humidity/pressure), MPU6050 (posture/fall), NEO-M8N GPS, SX1278 (RA-02) LoRa 433 MHz, SIM800L GSM, SOS বাটন, buzzer, LED, OLED, solar + 18650 Li-ion।
- Fall detection: **posture-gated** threshold state machine (free-fall + impact + lying angle + recovery); status: standing/walking/running/lying/fall; fall latch।
- Telemetry: প্রতি ১ সেকেন্ডে LoRa প্যাকেট, sequence number + CRC।
- জরুরি অবস্থায় (fall, SOS, উচ্চ তাপমাত্রা) local alarm + SMS (শেষ GPS লোকেশনসহ, rate-limited, ৬০ সেকেন্ড cool-down)।
- Library: BME280, MPU6050, TinyGPS++, SSD1306 (transmitter); LoRa by Sandeep Mistry (receiver)।

### 4.২ Base Station (ESP32 gateway)
- LoRa রিসিভ → validate → Firebase।
- **Dual-core pipeline**: radio reception নেটওয়ার্ক latency থেকে আলাদা; live data এক-স্লট overwrite queue; history batch; **তিন-স্তরের watchdog** (TLS reset → Wi-Fi re-association → reboot)।
- PATCH ও explicit keys ব্যবহার।
- পুরনো সমস্যা: ~২০ সেকেন্ড Firebase write delay ও আটকে যাওয়া — কারণ Wi-Fi auto-reconnect না থাকা ও প্রতি request-এ TLS handshake; দুটোই ঠিক করা হয়েছে।

### 4.৩ Smart Helmet
- **Nano #1**: ৩×ultrasonic (বাম/সামনে/ডান), MQ-2 gas (warm-up baseline + relative threshold + hysteresis), MAX30100 (HR/SpO₂), ২টি buzzer ও vibration motor; obstacle two-hit filter; LDR-নিয়ন্ত্রিত স্বাধীন headlamp।
- **Nano #2**: সিরিয়াল লাইন ফরওয়ার্ড; **ESP-01**: Wi-Fi দিয়ে Firebase `/helmet1`-এ আপলোড।
- Alert: obstacle → buzzer 1; gas/health → buzzer 2 + vibration।
- সীমাবদ্ধতা: LoRa/IMU নেই; HR/SpO₂ কপালে PPG — trend-level, medical নয়; MQ-2 selective নয়।

### 4.৪ Firebase ডেটা মডেল
- `/worker1` — live vest: `alerts{fall_detected,sos_active}`, `battery{voltage}`, `environment{temperature,humidity,pressure}`, `gps{latitude,longitude}`, `status{motion_state}`; `/worker1/commands/web_sos`।
- `/worker1/logs` — vest history (ফিল্ড `ts`)।
- `/helmet1/live` — heartRate, spo2, gasLevel, gasBaseline, distLeft/Front/Right, obstLeft/Front/Right, obstacle, gas।
- `/helmet1/logs` — একই ফিল্ড + `timestamp`; ~৩০ সেকেন্ড পর পর।

### 4.৫ ডিফল্ট থ্রেশহোল্ড (web, app, AI সব একই)
| বিষয় | মান |
|---|---|
| তাপমাত্রা | ১৫–৪৫ °C (vest firmware-এ SMS/alarm ৫০ °C — অসংগতি নোট করা আছে) |
| আর্দ্রতা | ২০–৯০ % |
| চাপ | ৯৫০–১০৫০ hPa |
| Heart rate | ৫০–১২০ bpm |
| SpO₂ | ≥ ৯০ % |
| Gas | baseline + ১৫০ ADC |
| Obstacle | < ৫০ cm |
| Immobility timeout | ৬০ s |

## ৫. Web Dashboard
- HTML/JS, Chart.js, Leaflet, PWA (service worker `sw.js`), GitHub Pages-এ হোস্ট।
- উন্নয়ন: CSS polish, staggered animation, ARIA accessibility, PWA meta, `.info/connected` toast, Browser Notification API, tab-title flashing, localStorage-এ audio পছন্দ।
- Vest ও helmet এক ভিউতে; merged alerts/history; map; daily/weekly/monthly report; Excel export; online/offline।
- **Browser-side AI**: robust z-score (MAD), jump, trend regression, flat-line ও gap detection; দ্বিভাষিক "Ask SafeGuard"; কোনো সার্ভারে ডেটা যায় না।
- পরিকল্পিত (মেমোরি অনুযায়ী): multi-worker, FCM push, geofencing alerts, forgot-password, CSV export।

## ৬. Android অ্যাপ — SafeGuard Command
- `applicationId com.smartvest.command`, v1.2 (code 3), minSdk 24, target/compile 36।
- Kotlin 2.2, Jetpack Compose Material3, Room 2.7, Firebase (Auth, RTDB, AI, App Check), Google Maps + Location, MPAndroidChart, Retrofit/OkHttp/Moshi, Credential Manager, Robolectric/Roborazzi।
- ইতিহাস: WebView-hybrid → React Native (SmartSafetyVest-app.zip) → বর্তমান full native Kotlin। Custom dependency-free XLSX writer (Apache POI Android-এ অসঙ্গত বলে বাদ)।
- **৯টি স্ক্রিন**: Dashboard, Live Chart, Map Tracking, Worker Profiles, Safety Rules, Alerts, Report, AI Analyze, Settings (+ Splash, Login) এবং ভাসমান **Ask SafeGuard** বাটন।
- **Managers**: VestTelemetry (watchdog ৫ s, hazard evaluation), HelmetTelemetry (৬ s window, ৩-আপডেট debounce), EmergencySiren (৫ টোন + synthesized wail ৬৫০–১৩৫০ Hz; silence-এর পর ১৫ s cool-down), Breadcrumbs/Beacon (১৫০ পয়েন্ট, TTS ২২ s cool-down), NotificationHelper (১০ s throttle)।
- **Geofence**: ৩টি default zone (Gas ১৮০ m, Crane ১৪০ m, Substation ১২০ m), কাস্টম zone, ভেতরে/৬০ m approach।
- **Local DB**: Room `vest_command_db` v2 — `telemetry_logs` (৩০ দিন), `alert_history`; SharedPreferences-এ rules, profiles, zones, trail, settings।
- **Report**: PDF + hand-written XLSX (vest sheet, helmet sheet, helmet offline sheet); FileProvider শেয়ার।
- **AI**: Kotlin port (`SGEngine`, `AskEngine` …), `/worker1/logs` ও `/helmet1/logs` থেকে; JVM test আছে; Gradle build sandbox-এ compile করা হয়নি।
- Login: Firebase Auth email/password + Credential Manager।

## ৭. থিসিস (SafeGuard_Thesis_Draft.pdf)
- ১০৫ পেজ, A4, IEEE, LaTeX (pdflatex, TikZ)।
- অধ্যায়: ১ Introduction · ২ Literature Review · ৩ Theory · ৪ Requirements & Design (FR1–FR23) · ৫ Hardware · ৬ Firmware & Software · **৭ Mobile Application** · ৮ Testing · ৯ Discussion · ১০ Conclusion & Future Work · References (২২) · Appendix A–F + Android Reference।
- সোর্স: `/home/claude/thesis/*.tex`; বিল্ড: `pdflatex main.tex` ×৩; চেক: `python3 chk.py`।
- ঠিক করা সমস্যা: LaTeX অপশনাল-আর্গুমেন্ট বাগ, TikZ ওভারল্যাপ, চওড়া টেবিল, blank/orphan পেজ, অযাচাইকৃত literature claim নরম করা, SIM800L/Pitch সূত্র সংশোধন, gas-test নিরাপত্তা (লাইটারের বদলে alcohol swab)।

## ৮. এ পর্যন্ত ডেলিভারি
| ফাইল | বিষয় |
|---|---|
| Website_with_Helmet.zip | helmet সহ ওয়েবসাইট |
| Updated_READMEs.zip | ১৩টি README |
| SafeGuard_Thesis_Draft.pdf | থিসিস (অ্যাপসহ, ১০৫ পেজ) |
| SafeGuard_Thesis_LaTeX_Source.zip | থিসিসের LaTeX সোর্স |
| SafeGuard_Project_Master.md | এই ফাইল |

## ৯. জানা অসংগতি / ঝুঁকি
1. অ্যাপে LoRa frequency লেবেল **915 MHz**, firmware **433 MHz**।
2. অ্যাপের `fetchHistory()` → `/worker1/history`; gateway/web → `/worker1/logs`; ফাঁকা হলে নকল synthetic history।
3. Login-এ **demo entry** যা authentication এড়ায়।
4. ফিল্ড না এলে অ্যাপ স্বাভাবিক default মান (28 °C, 55 %, 1013 hPa, 3.95 V, ঢাকার লোকেশন) বসায়।
5. Alert History-তে প্রথমবার sample alert seed হয়।
6. যেকোনো alert-এ siren বাজে (severity নীতি নেই)।
7. অ্যাপ background-এ নির্ভরযোগ্য নয় (foreground service/FCM নেই)।
8. Beacon নোড নাম `GW-ALPHA-01` হার্ডকোড।
9. Firmware SMS-তাপমাত্রা ৫০ °C বনাম dashboard/app ৪৫ °C।
10. Vest-এর helmet "অনেক দেরিতে offline" fix আগের নির্দেশনায় ছিল কিন্তু প্রয়োগ হয়নি।
11. Helmet শুধু Wi-Fi-নির্ভর; LoRa নেই।
12. LoRa লিংক এনক্রিপ্টেড নয়; একটিই gateway; Firebase legacy secret/rules সঠিকভাবে কনফিগার করতে হবে।
13. সিস্টেম এক শ্রমিকের (`worker1`/`helmet1`) জন্য।

## ১০. বাকি কাজ
**আপনার দিক থেকে**
- Title page-এর "[Month, Year]"।
- সব test result টেবিল ("TBD") — আসল মাপ বসাতে হবে।
- অ্যাপের ৪টি screenshot, hardware ফটো।
- Reference [2]-এর তথ্য সম্পূর্ণ, URL access date, Literature Review-এর `todo` নোট।
- Abstract-এ ফিল্ড-ফলাফলের এক বাক্য।
- অ্যাপ Gradle build ও ফোনে টেস্ট করে ফলাফল থিসিসে লেখা।

**কোড (আপনি চাইলে)**
- §৯-এর অসংগতিগুলো অ্যাপে ঠিক করা; ওয়েবে helmet offline-delay fix।
- Background alerting (foreground service / FCM), multi-worker, zones/profiles ক্লাউডে sync।

## ১১. মূল পরিসংখ্যান-সূত্র (থিসিসে ব্যবহৃত)
- Anomaly: `z = (x − median) / (1.4826 · MAD)`।
- LoRa airtime সূত্র (SF, BW, CR, payload) — থিসিস Ch3।
- Latency budget: vest-event → dashboard (থিসিস §Latency)।
