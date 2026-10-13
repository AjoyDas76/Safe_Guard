/* =====================================================================
   SafeGuard — AI Engine (pure logic, no DOM, no network)
   ---------------------------------------------------------------------
   Everything here runs inside the browser. No API key, no server, no
   cost. It is split from the UI (ai-ui.js) so it can be unit-tested and
   described in the thesis as a self-contained module.

   Contents
     1. Fixed thresholds       (same numbers as app.js / helmet firmware)
     2. Row normalisers        (Firebase log entry -> clean object)
     3. Alert evaluation       (which fixed-threshold alerts a row raises)
     4. Incident grouping      (30 s snapshots -> real incidents)
     5. Anomaly detection      (robust z-score, jump, trend, flat-line,
                                data gaps, prolonged lying, cross-signal)
   ===================================================================== */
(function (root) {
  'use strict';

  // ---------- 1. Fixed thresholds (mirror of app.js) ----------
  const TH = {
    TEMP: [15, 45], HUM: [20, 90], PRES: [950, 1050],
    HR: [50, 120], SPO2_MIN: 90, GAS_MARGIN: 150, OBST_CM: 50, NO_ECHO: 999
  };
  const GAP_MS = 90 * 1000;          // logs are written about every 30 s

  const num = (v) => {
    if (v === undefined || v === null || v === '') return null;
    const x = Number(v);
    return isFinite(x) ? x : null;
  };

  // ---------- 2. Row normalisers ----------
  // /worker1/logs entry
  function normVest(v) {
    return {
      ts: num(v.ts),
      temp: num(v.temperature), hum: num(v.humidity), pres: num(v.pressure),
      fall: !!v.fall, sos: !!v.sos,
      motion: v.motion_state ? String(v.motion_state).trim().toUpperCase() : '',
      lat: num(v.latitude), lng: num(v.longitude)
    };
  }
  // /helmet1/logs entry (same field names the dashboard reads)
  function normHelmet(v) {
    const d = {
      L: num(v.distLeft), F: num(v.distFront), R: num(v.distRight)
    };
    const o = { L: num(v.obstLeft) === 1, F: num(v.obstFront) === 1, R: num(v.obstRight) === 1 };
    return {
      ts: num(v.timestamp),
      hr: num(v.heartRate) || 0, spo2: num(v.spo2) || 0,
      gas: num(v.gasLevel) || 0, gasBase: num(v.gasBaseline) || 0,
      gasFlag: num(v.gas) === 1,
      obstFlag: num(v.obstacle) === 1 || o.L || o.F || o.R,
      obst: o, dist: d
    };
  }
  // /worker1 live node
  function normVestLive(d) {
    d = d || {};
    const env = d.environment || {}, st = d.status || {}, al = d.alerts || {},
          gps = d.gps || {}, sg = d.signal || {};
    return {
      temp: num(env.temperature), hum: num(env.humidity), pres: num(env.pressure),
      motion: st.motion_state ? String(st.motion_state).trim().toUpperCase() : '',
      fall: !!al.fall_detected || String(st.motion_state || '').trim().toUpperCase() === 'FALL', sos: !!al.sos_active,
      lat: num(gps.latitude), lng: num(gps.longitude), rssi: num(sg.rssi)
    };
  }
  // /helmet1/live node has the same flat shape as a helmet log entry
  function normHelmetLive(d) {
    return normHelmet(Object.assign({}, d || {}, { timestamp: Date.now() }));
  }

  // ---------- 3. Alert evaluation (fixed thresholds) ----------
  // Returns [{key, bn, en}] for one vest row.
  function evalVest(r) {
    const a = [];
    if (r.fall) a.push({ key: 'fall', bn: 'ফল ডিটেক্ট', en: 'Fall detected' });
    if (r.sos) a.push({ key: 'sos', bn: 'SOS সক্রিয়', en: 'SOS active' });
    if (r.temp !== null && (r.temp > TH.TEMP[1] || r.temp < TH.TEMP[0]))
      a.push({ key: 'temp', bn: (r.temp > TH.TEMP[1] ? 'উচ্চ' : 'নিম্ন') + ' তাপমাত্রা ' + r.temp.toFixed(1) + '°C', en: (r.temp > TH.TEMP[1] ? 'High' : 'Low') + ' temperature ' + r.temp.toFixed(1) + '°C' });
    if (r.hum !== null && (r.hum > TH.HUM[1] || r.hum < TH.HUM[0]))
      a.push({ key: 'humidity', bn: (r.hum > TH.HUM[1] ? 'উচ্চ' : 'নিম্ন') + ' আর্দ্রতা ' + r.hum.toFixed(1) + '%', en: (r.hum > TH.HUM[1] ? 'High' : 'Low') + ' humidity ' + r.hum.toFixed(1) + '%' });
    if (r.pres !== null && (r.pres > TH.PRES[1] || r.pres < TH.PRES[0]))
      a.push({ key: 'pressure', bn: (r.pres > TH.PRES[1] ? 'উচ্চ' : 'নিম্ন') + ' চাপ ' + r.pres.toFixed(0) + ' hPa', en: (r.pres > TH.PRES[1] ? 'High' : 'Low') + ' pressure ' + r.pres.toFixed(0) + ' hPa' });
    return a;
  }
  // Returns [{key, bn, en}] for one helmet row. `debounced` is not needed
  // for logs (the live view debounces vitals, the log reports raw values).
  function evalHelmet(r) {
    const a = [];
    if (r.obstFlag) a.push({ key: 'obstacle', bn: 'বাধা শনাক্ত', en: 'Obstacle detected' });
    if (r.gasFlag) a.push({ key: 'gas', bn: 'গ্যাস শনাক্ত (MQ-2 ' + r.gas + ')', en: 'Gas detected (MQ-2 ' + r.gas + ')' });
    if (r.hr > 0 && (r.hr < TH.HR[0] || r.hr > TH.HR[1]))
      a.push({ key: 'hr', bn: (r.hr < TH.HR[0] ? 'কম' : 'বেশি') + ' হার্ট রেট ' + r.hr + ' bpm', en: (r.hr < TH.HR[0] ? 'Low' : 'High') + ' heart rate ' + r.hr + ' bpm' });
    if (r.spo2 > 0 && r.spo2 < TH.SPO2_MIN)
      a.push({ key: 'spo2', bn: 'SpO₂ কম ' + r.spo2 + '%', en: 'Low SpO₂ ' + r.spo2 + '%' });
    return a;
  }

  // ---------- 4. Incident grouping ----------
  // A fall that stays "true" for 10 consecutive 30-second logs is ONE
  // incident, not ten. Rows of the same alert key that are <= gapMs
  // apart are merged.
  function groupIncidents(rows, evalFn, gapMs) {
    gapMs = gapMs || GAP_MS;
    const open = {};            // key -> incident being extended
    const done = [];
    const sorted = rows.slice().sort((a, b) => a.ts - b.ts);
    sorted.forEach((r) => {
      const hits = evalFn(r);
      const seen = {};
      hits.forEach((h) => {
        seen[h.key] = true;
        const cur = open[h.key];
        if (cur && r.ts - cur.end <= gapMs) { cur.end = r.ts; cur.snaps++; cur.last = h; }
        else {
          if (cur) done.push(cur);
          open[h.key] = { key: h.key, start: r.ts, end: r.ts, snaps: 1, first: h, last: h };
        }
      });
      Object.keys(open).forEach((k) => {
        if (!seen[k] && r.ts - open[k].end > gapMs) { done.push(open[k]); delete open[k]; }
      });
    });
    Object.keys(open).forEach((k) => done.push(open[k]));
    return done.sort((a, b) => a.start - b.start);
  }

  // ---------- 5. Anomaly detection ----------
  // Per-metric tuning. `floor` is the smallest change we consider real
  // (sensor noise), `jump` the smallest sudden step between two readings,
  // `slope` the smallest sustained drift per minute. These are starting
  // values — tune them with your own recorded data.
  const METRICS = {
    temp: { src: 'vest',   bn: 'তাপমাত্রা',  en: 'Temperature', unit: '°C',  floor: 0.3, jump: 3,   slope: 0.25, lim: [TH.TEMP[0], TH.TEMP[1]],  dec: 1 },
    hum:  { src: 'vest',   bn: 'আর্দ্রতা',    en: 'Humidity',    unit: '%',   floor: 1.5, jump: 10,  slope: 1.5,  lim: [TH.HUM[0], TH.HUM[1]],    dec: 1 },
    pres: { src: 'vest',   bn: 'বায়ুচাপ',    en: 'Pressure',    unit: ' hPa',floor: 0.4, jump: 4,   slope: 0.5,  lim: [TH.PRES[0], TH.PRES[1]],  dec: 1 },
    hr:   { src: 'helmet', bn: 'হার্ট রেট',   en: 'Heart rate',  unit: ' bpm',floor: 6,   jump: 25,  slope: 4,    lim: [TH.HR[0], TH.HR[1]],      dec: 0 },
    spo2: { src: 'helmet', bn: 'SpO₂',        en: 'SpO₂',        unit: '%',   floor: 1,   jump: 4,   slope: 1,    lim: [TH.SPO2_MIN, null],       dec: 0 },
    gas:  { src: 'helmet', bn: 'গ্যাস লেভেল', en: 'Gas level',   unit: '',    floor: 15,  jump: 100, slope: 20,   lim: null,                      dec: 0 }
  };
  const CFG = { BASE_WIN: 60, MIN_BASE: 20, Z_FLAG: 4, TREND_WIN: 20, TREND_R2: 0.8, FLAT_RUN: 60, LYING_MS: 5 * 60 * 1000 };

  function median(arr) {
    const s = arr.slice().sort((a, b) => a - b), n = s.length;
    return n % 2 ? s[(n - 1) / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
  }

  // Build a clean [{ts, v, row}] series for one metric.
  function buildSeries(key, vestRows, helmetRows) {
    const m = METRICS[key];
    const rows = m.src === 'vest' ? vestRows : helmetRows;
    const pts = [];
    rows.forEach((r) => {
      let v = null;
      if (key === 'temp') v = r.temp; else if (key === 'hum') v = r.hum; else if (key === 'pres') v = r.pres;
      else if (key === 'hr') v = r.hr > 0 ? r.hr : null;          // 0 = no finger on sensor
      else if (key === 'spo2') v = r.spo2 > 0 ? r.spo2 : null;
      else if (key === 'gas') v = r.gas > 0 ? r.gas : null;
      if (v !== null && r.ts) pts.push({ ts: r.ts, v, row: r });
    });
    pts.sort((a, b) => a.ts - b.ts);
    return pts;
  }

  // Does this single point violate the FIXED threshold?
  function breaksFixed(key, p) {
    const m = METRICS[key];
    if (key === 'gas') return !!p.row.gasFlag;
    if (!m.lim) return false;
    if (m.lim[0] !== null && p.v < m.lim[0]) return true;
    if (m.lim[1] !== null && p.v > m.lim[1]) return true;
    return false;
  }

  // Ordinary least squares over pts[a..b): slope per minute and R².
  function regress(pts, a, b) {
    const n = b - a;
    if (n < 5) return null;
    const t0 = pts[a].ts;
    let sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
    for (let i = a; i < b; i++) {
      const x = (pts[i].ts - t0) / 60000, y = pts[i].v;
      sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y;
    }
    const den = n * sxx - sx * sx;
    if (den === 0) return null;
    const slope = (n * sxy - sx * sy) / den;
    const ssTot = syy - (sy * sy) / n;
    const intercept = (sy - slope * sx) / n;
    let ssRes = 0;
    for (let i = a; i < b; i++) {
      const x = (pts[i].ts - t0) / 60000;
      const e = pts[i].v - (intercept + slope * x);
      ssRes += e * e;
    }
    const r2 = ssTot > 1e-9 ? 1 - ssRes / ssTot : 0;
    return { slope, r2 };
  }

  // Detect anomalies in one metric. Returns { points, flags, episodes, trendNow }.
  //   flags[i] = { z, jump, trend } markers used by the chart
  function detectMetric(key, pts) {
    const m = METRICS[key];
    const flags = new Array(pts.length).fill(null);
    const raw = [];                       // {i, method, dir, z, delta}
    if (pts.length < CFG.MIN_BASE + 5) return { key, points: pts, flags, episodes: [], trendNow: null, enough: false };

    // Typical sampling interval, so that long gaps are never treated as "sudden jumps".
    const dts = [];
    for (let i = 1; i < pts.length; i++) dts.push(pts[i].ts - pts[i - 1].ts);
    const typDt = median(dts) || 30000;
    const maxStep = Math.max(GAP_MS, typDt * 3);

    // (A) robust z-score against the previous BASE_WIN readings,  (B) sudden jump
    for (let i = CFG.MIN_BASE; i < pts.length; i++) {
      const from = Math.max(0, i - CFG.BASE_WIN);
      const base = [];
      for (let j = from; j < i; j++) base.push(pts[j].v);
      const med = median(base);
      const mad = median(base.map((x) => Math.abs(x - med)));
      const scale = Math.max(1.4826 * mad, m.floor);
      const z = (pts[i].v - med) / scale;
      if (Math.abs(z) >= CFG.Z_FLAG && Math.abs(pts[i].v - med) >= m.floor * 2) {
        raw.push({ i, method: 'z', dir: z > 0 ? 1 : -1, z });
      }
      const step = pts[i].v - pts[i - 1].v;
      if (pts[i].ts - pts[i - 1].ts <= maxStep && Math.abs(step) >= m.jump) {
        raw.push({ i, method: 'jump', dir: step > 0 ? 1 : -1, z: step / scale, delta: step });
      }
    }

    // (C) sustained trend: sliding window, OLS slope + R²
    const W = CFG.TREND_WIN;
    for (let a = 0; a + W <= pts.length; a += Math.floor(W / 2)) {
      const b = a + W;
      if (pts[b - 1].ts - pts[a].ts > W * maxStep) continue;       // window spans a data gap
      const rg = regress(pts, a, b);
      if (rg && Math.abs(rg.slope) >= m.slope && rg.r2 >= CFG.TREND_R2) {
        for (let i = a; i < b; i++) raw.push({ i, method: 'trend', dir: rg.slope > 0 ? 1 : -1, z: 0, slope: rg.slope });
      }
    }

    // Trend of the most recent window (for "will cross the limit in N min").
    let trendNow = null;
    if (pts.length >= W) {
      const rg = regress(pts, pts.length - W, pts.length);
      if (rg) {
        const last = pts[pts.length - 1].v;
        let eta = null, limit = null;
        if (m.lim && Math.abs(rg.slope) >= m.slope && rg.r2 >= CFG.TREND_R2) {
          if (rg.slope > 0 && m.lim[1] !== null && last < m.lim[1]) { limit = m.lim[1]; eta = (limit - last) / rg.slope; }
          if (rg.slope < 0 && m.lim[0] !== null && last > m.lim[0]) { limit = m.lim[0]; eta = (limit - last) / rg.slope; }
        }
        trendNow = { slope: rg.slope, r2: rg.r2, last, eta: (eta !== null && eta > 0 && eta <= 120) ? eta : null, limit };
      }
    }

    // (D) flat-line (stuck sensor) — only for float sensors that always carry noise
    if (key === 'temp' || key === 'hum' || key === 'pres') {
      let runStart = 0;
      for (let i = 1; i <= pts.length; i++) {
        if (i === pts.length || pts[i].v !== pts[runStart].v) {
          if (i - runStart >= CFG.FLAT_RUN) {
            for (let k = runStart; k < i; k++) raw.push({ i: k, method: 'flat', dir: 0, z: 0 });
          }
          runStart = i;
        }
      }
    }

    // ---- turn flagged points into episodes (merge neighbours of the same metric) ----
    raw.sort((x, y) => x.i - y.i);
    const episodes = [];
    raw.forEach((f) => {
      const t = pts[f.i].ts;
      const last = episodes[episodes.length - 1];
      if (last && t - last.endTs <= 2 * Math.max(typDt, 30000) + 60000) {
        last.endTs = Math.max(last.endTs, t); last.endI = Math.max(last.endI, f.i);
        last.methods[f.method] = true; last.n++;
        if (Math.abs(f.z) > Math.abs(last.maxZ)) last.maxZ = f.z;
        if (f.delta !== undefined && Math.abs(f.delta) > Math.abs(last.maxJump || 0)) last.maxJump = f.delta;
        if (f.slope !== undefined) last.slope = f.slope;
        if (f.dir && Math.abs(f.z) >= Math.abs(last.dirZ || 0)) { last.dir = f.dir; last.dirZ = f.z; }
      } else {
        episodes.push({
          key, startTs: t, endTs: t, startI: f.i, endI: f.i, methods: { [f.method]: true }, n: 1,
          maxZ: f.z, maxJump: f.delta, slope: f.slope, dir: f.dir, dirZ: f.z
        });
      }
      flags[f.i] = Object.assign(flags[f.i] || {}, { [f.method]: true });
    });

    episodes.forEach((e) => {
      let peak = null;
      for (let i = e.startI; i <= e.endI; i++) {
        if (!peak || Math.abs(pts[i].v - pts[Math.max(0, e.startI - 1)].v) > Math.abs(peak.v - pts[Math.max(0, e.startI - 1)].v)) peak = pts[i];
      }
      e.peakV = peak ? peak.v : pts[e.startI].v;
      e.caught = false;                               // would the fixed threshold have alarmed?
      for (let i = e.startI; i <= e.endI; i++) if (breaksFixed(key, pts[i])) { e.caught = true; break; }
      e.sev = severityOf(e);
    });

    return { key, points: pts, flags, episodes, trendNow, enough: true, typDt };
  }

  function severityOf(e) {
    const az = Math.abs(e.maxZ || 0), m = METRICS[e.key];
    if (e.methods.jump && Math.abs(e.maxJump || 0) >= m.jump * 2) return 'high';
    if (az >= 8) return 'high';
    if (e.methods.z && e.methods.trend) return 'high';
    if (e.methods.jump || az >= 5.5 || e.methods.trend) return 'medium';
    return 'low';                                      // z in [4, 5.5) or flat-line
  }

  // Data-gap (offline) detection for any ts-sorted row list.
  function findGaps(rows) {
    const gaps = [];
    for (let i = 1; i < rows.length; i++) {
      const d = rows[i].ts - rows[i - 1].ts;
      if (d > GAP_MS * 2) gaps.push({ from: rows[i - 1].ts, to: rows[i].ts, ms: d });
    }
    return gaps;
  }

  // Worker lying still for too long (vest motion_state).
  function findLying(vestRows) {
    const out = [];
    let start = null, prev = null, fallInside = false;
    const close = (end) => {
      if (start !== null && end - start >= CFG.LYING_MS) out.push({ from: start, to: end, ms: end - start, fall: fallInside });
      start = null; fallInside = false;
    };
    vestRows.forEach((r) => {
      if (r.motion === 'LYING' || r.motion === 'FALL') {
        if (start === null) start = r.ts;
        else if (r.ts - prev > GAP_MS * 2) { close(prev); start = r.ts; }
        if (r.motion === 'FALL' || r.fall) fallInside = true;
        prev = r.ts;
      } else if (start !== null) { close(prev); }
    });
    if (start !== null) close(prev);
    return out;
  }

  // ---------- Main entry ----------
  // opts: { vest:[normVest rows], helmet:[normHelmet rows] }
  function analyze(opts) {
    const vest = (opts.vest || []).filter((r) => r.ts).sort((a, b) => a.ts - b.ts);
    const helmet = (opts.helmet || []).filter((r) => r.ts).sort((a, b) => a.ts - b.ts);

    const perMetric = {};
    const findings = [];
    Object.keys(METRICS).forEach((k) => {
      const pts = buildSeries(k, vest, helmet);
      const res = detectMetric(k, pts);
      perMetric[k] = res;
      res.episodes.forEach((e) => findings.push(describeEpisode(e)));
      if (res.trendNow && res.trendNow.eta !== null && res.trendNow.eta <= 60) {
        findings.push(describeForecast(k, res.trendNow, pts[pts.length - 1].ts));
      }
    });

    // Cross-signal: temperature AND heart rate drifting up together (possible heat stress).
    const rising = (e) => (e.methods.trend ? e.slope > 0 : e.dir > 0) && (e.methods.trend || e.methods.z);
    const tEp = perMetric.temp ? perMetric.temp.episodes.filter(rising) : [];
    const hEp = perMetric.hr ? perMetric.hr.episodes.filter(rising) : [];
    tEp.forEach((t) => hEp.forEach((h) => {
      if (t.startTs <= h.endTs + 5 * 60000 && h.startTs <= t.endTs + 5 * 60000) {
        findings.push({
          kind: 'cross', key: 'temp+hr', sev: 'high', startTs: Math.min(t.startTs, h.startTs), endTs: Math.max(t.endTs, h.endTs),
          caught: false, tag: 'CROSS',
          bn: 'তাপমাত্রা ও হার্ট রেট একসাথে বাড়ছে — হিট-স্ট্রেসের সম্ভাব্য প্যাটার্ন (যাচাই করুন)',
          en: 'Temperature and heart rate are rising together — possible heat-stress pattern (verify)'
        });
      }
    }));

    // Prolonged lying
    findLying(vest).forEach((l) => findings.push({
      kind: 'lying', key: 'motion', sev: l.fall ? 'high' : 'medium', startTs: l.from, endTs: l.to, caught: l.fall, tag: 'INACTIVE',
      bn: 'ওয়ার্কার ' + durBn(l.ms) + ' ধরে শুয়ে/নিশ্চল অবস্থায়' + (l.fall ? ' (ফল সহ)' : ''),
      en: 'Worker lying still for ' + durEn(l.ms) + (l.fall ? ' (with fall)' : '')
    }));

    // Data gaps
    const vGaps = findGaps(vest), hGaps = findGaps(helmet);
    [['ভেস্ট', 'Vest', vGaps], ['হেলমেট', 'Helmet', hGaps]].forEach((g) => {
      if (!g[2].length) return;
      const longest = g[2].reduce((a, b) => (b.ms > a.ms ? b : a));
      findings.push({
        kind: 'gap', key: 'gap', sev: longest.ms >= 5 * 60000 ? 'medium' : 'low', startTs: longest.from, endTs: longest.to, caught: false, tag: 'DATA GAP',
        bn: g[0] + ' ডেটায় ' + g[2].length + 'টি বিরতি; সবচেয়ে বড়টি ' + durBn(longest.ms),
        en: g[1] + ' data has ' + g[2].length + ' gap(s); longest ' + durEn(longest.ms)
      });
    });

    const order = { high: 0, medium: 1, low: 2 };
    findings.sort((a, b) => (order[a.sev] - order[b.sev]) || (b.endTs - a.endTs));

    const real = findings.filter((f) => f.kind !== 'gap');
    const missed = real.filter((f) => !f.caught && f.sev !== 'low').length;
    const verdict = findings.some((f) => f.sev === 'high') ? 'high' : findings.some((f) => f.sev === 'medium') ? 'medium' : 'normal';

    return {
      verdict, findings, perMetric,
      counts: { vest: vest.length, helmet: helmet.length, anomalies: real.length, missed, gaps: vGaps.length + hGaps.length },
      range: { from: Math.min(vest.length ? vest[0].ts : Infinity, helmet.length ? helmet[0].ts : Infinity),
               to: Math.max(vest.length ? vest[vest.length - 1].ts : 0, helmet.length ? helmet[helmet.length - 1].ts : 0) }
    };
  }

  function describeEpisode(e) {
    const m = METRICS[e.key];
    const fmt = (v) => v.toFixed(m.dec) + m.unit;
    const parts = [], partsEn = [];
    if (e.methods.jump) { parts.push('হঠাৎ লাফ ' + (e.maxJump > 0 ? '+' : '') + e.maxJump.toFixed(m.dec) + m.unit); partsEn.push('sudden jump ' + (e.maxJump > 0 ? '+' : '') + e.maxJump.toFixed(m.dec) + m.unit); }
    if (e.methods.z) { parts.push('সাম্প্রতিক স্বাভাবিক মান থেকে ' + Math.abs(e.maxZ).toFixed(1) + 'σ দূরে'); partsEn.push(Math.abs(e.maxZ).toFixed(1) + 'σ from recent normal'); }
    if (e.methods.trend) { parts.push('টানা ' + (e.slope > 0 ? 'বাড়ছে' : 'কমছে') + ' (' + Math.abs(e.slope).toFixed(2) + m.unit + '/মিনিট)'); partsEn.push('sustained ' + (e.slope > 0 ? 'rise' : 'fall') + ' (' + Math.abs(e.slope).toFixed(2) + m.unit + '/min)'); }
    if (e.methods.flat) { parts.push('মান একদম একই থেকে গেছে — সেন্সর আটকে যেতে পারে'); partsEn.push('value frozen — sensor may be stuck'); }
    const tag = e.methods.flat ? 'FLATLINE' : e.methods.jump ? 'JUMP' : e.methods.trend ? 'TREND' : 'DEVIATION';
    return {
      kind: 'episode', key: e.key, sev: e.sev, startTs: e.startTs, endTs: e.endTs, caught: e.caught, tag,
      bn: m.bn + ': ' + parts.join(' · ') + ' (সর্বোচ্চ/সর্বনিম্ন মান ' + fmt(e.peakV) + ')',
      en: m.en + ': ' + partsEn.join(' · ') + ' (peak ' + fmt(e.peakV) + ')'
    };
  }

  function describeForecast(key, t, lastTs) {
    const m = METRICS[key];
    const min = Math.max(1, Math.round(t.eta));
    return {
      kind: 'forecast', key, sev: t.eta <= 15 ? 'high' : 'medium', startTs: lastTs, endTs: lastTs, caught: false, tag: 'FORECAST',
      bn: m.bn + ' এই হারে ' + (t.slope > 0 ? 'বাড়লে' : 'কমলে') + ' প্রায় ' + min + ' মিনিটে নিরাপদ সীমা (' + t.limit + m.unit + ') ছুঁয়ে ফেলবে — এখনো সীমার ভেতরে',
      en: m.en + ' will reach the safe limit (' + t.limit + m.unit + ') in about ' + min + ' min at the current rate — still inside the limit'
    };
  }

  function durBn(ms) { const m = Math.round(ms / 60000); return m < 1 ? Math.round(ms / 1000) + ' সেকেন্ড' : m < 60 ? m + ' মিনিট' : Math.floor(m / 60) + ' ঘণ্টা ' + (m % 60) + ' মিনিট'; }
  function durEn(ms) { const m = Math.round(ms / 60000); return m < 1 ? Math.round(ms / 1000) + ' s' : m < 60 ? m + ' min' : Math.floor(m / 60) + ' h ' + (m % 60) + ' min'; }

  // ---------- 6. Tiny bilingual NLU (Bangla + English + a little Banglish) ----------
  // Rule-based on purpose: every answer is computed from real data, so the
  // chat can never "hallucinate" a sensor value.
  const BN_DIG = '০১২৩৪৫৬৭৮৯';
  const bn2en = (s) => String(s).replace(/[০-৯]/g, (d) => BN_DIG.indexOf(d));
  const RX = {
    help: /সাহায্য|কি কি পার|কী কী পার|কি করতে পার|কী করতে পার|\bhelp\b|what can you|কিভাবে ব্যবহার/i,
    greet: /^\s*(hi|hello|hey|হাই|হ্যালো|হেলো|সালাম|আসসালামু|নমস্কার)/i,
    alert: /অ্যালার্ট|এ্যালার্ট|এলার্ট|অ্যালার্ম|এলার্ম|সতর্ক|alert|alarm|warning/i,
    count: /কয়টা|কয়টি|কতটা|কতটি|কতগুলো|কতগুলি|কত\s*বার|কতবার|সংখ্যা|how many|count|number of|total|koyta|koyti/i,
    last: /শেষ|সর্বশেষ|সাম্প্রতিক|\blast\b|latest|recent|কখন|\bwhen\b/i,
    now: /এখন|বর্তমান|চলমান|সক্রিয়|\bnow\b|current|\bactive\b|right now|\blive\b/i,
    anomaly: /অস্বাভাবিক|অসঙ্গতি|অ্যানোমালি|এনোমালি|অ্যানমালি|anomal|unusual|abnormal|strange|weird|সন্দেহজনক|ট্রেন্ড|\btrend|প্যাটার্ন|pattern/i,
    location: /অবস্থান|লোকেশন|কোথায়|জিপিএস|\bgps\b|location|\bwhere\b|ম্যাপ|\bmap\b/i,
    status: /অবস্থা(?!ন)|কেমন|স্ট্যাটাস|status|condition|how is|how's|নিরাপদ|\bsafe\b|ঠিক আছে|ঠিকআছে|কি খবর|কী খবর|kemon|obostha|ওয়ার্কার|শ্রমিক|\bworker\b/i,
    summary: /সামারি|সারাংশ|সারসংক্ষেপ|সংক্ষেপে|summary|overview|রিপোর্ট|\breport\b/i,
    stats: /গড়|সর্বোচ্চ|সর্বনিম্ন|average|\bavg\b|\bmax\b|\bmin\b|highest|lowest|maximum|minimum|peak/i
  };
  const SENSOR_RX = [
    ['temp', /তাপমাত্রা|তাপ|গরম|টেম্পারেচার|ঠান্ডা|\btemp|temperature|\bhot\b|\bheat/i],
    ['hum', /আর্দ্রতা|আদ্রতা|হিউমিডিটি|humid/i],
    ['pres', /বায়ুচাপ|চাপ|প্রেসার|প্রেশার|pressure|\bhpa\b/i],
    ['hr', /হার্ট|হৃদ|পালস|নাড়ি|heart|pulse|\bbpm\b|\bhr\b/i],
    ['spo2', /অক্সিজেন|স্যাচুরেশন|spo2|spo₂|oxygen|saturation/i],
    ['gas', /গ্যাস|ধোঁয়া|ধোয়া|\bgas\b|smoke|mq-?2/i],
    ['obstacle', /বাধা|অবস্টাকল|obstacle|proximity|দূরত্ব|distance|ultrasonic/i],
    ['motion', /পড়ে গেছে|পড়ে গেল|পতন|ফল ডিটেক্ট|\bfall\b|motion|নড়াচড়া|হাঁট|দাঁড়|শুয়ে|walking|running|lying|standing|\bsos\b|এসওএস/i],
    ['signal', /সিগন্যাল|সিগনাল|rssi|\bsignal\b|lora|নেটওয়ার্ক/i]
  ];

  // "last 2 hours" / "গত ৩ ঘণ্টা" / today / yesterday / week
  function parseScope(q, now) {
    now = now || Date.now();
    const s = bn2en(String(q).toLowerCase());
    const d = new Date(now); d.setHours(0, 0, 0, 0);
    const sod = d.getTime(), day = 864e5;
    let m;
    if (/গতকাল|yesterday/.test(s)) return { since: sod - day, until: sod, bn: 'গতকাল', poss: 'গতকালের', en: 'yesterday', kind: 'yesterday' };
    if ((m = s.match(/(\d+)\s*(ঘণ্টা|ঘন্টা|hours?|hrs?|h\b)/))) {
      const n = Math.min(168, Math.max(1, parseInt(m[1], 10)));
      return { since: now - n * 36e5, until: now, bn: 'গত ' + n + ' ঘণ্টায়', poss: 'গত ' + n + ' ঘণ্টার', en: 'in the last ' + n + ' h', kind: 'hours', hours: n };
    }
    if (/ঘণ্টা|ঘন্টা|\bhour/.test(s) && /গত|শেষ|last|past/.test(s))
      return { since: now - 36e5, until: now, bn: 'গত ১ ঘণ্টায়', poss: 'গত ১ ঘণ্টার', en: 'in the last hour', kind: 'hours', hours: 1 };
    if (/সপ্তাহ|\bweek|7\s*(দিন|days)|মাস|month/.test(s))
      return { since: now - 7 * day, until: now, bn: 'গত ৭ দিনে', poss: 'গত ৭ দিনের', en: 'in the last 7 days', kind: 'week', capped: /মাস|month/.test(s) };
    return { since: sod, until: now, bn: 'আজ', poss: 'আজকের', en: 'today', kind: 'today' };
  }

  // Which alert keys does a sensor word refer to? (for "temperature alerts")
  const SENSOR_ALERT_KEYS = {
    temp: ['temp'], hum: ['humidity'], pres: ['pressure'], hr: ['hr'], spo2: ['spo2'],
    gas: ['gas'], obstacle: ['obstacle'], motion: ['fall', 'sos']
  };

  function detectIntent(q, now) {
    const s = String(q || '').trim();
    const scope = parseScope(s, now);
    const sensors = SENSOR_RX.filter((x) => x[1].test(s)).map((x) => x[0]);
    const base = { scope, sensors, stats: RX.stats.test(s), hasScopeWord: scope.kind !== 'today' || /আজ|today|আজকে/i.test(s) };
    if (s.length <= 30 && RX.greet.test(s)) return Object.assign(base, { intent: 'greet' });
    if (RX.help.test(s)) return Object.assign(base, { intent: 'help' });
    if (RX.alert.test(s)) {
      const filter = [];
      sensors.forEach((k) => (SENSOR_ALERT_KEYS[k] || []).forEach((a) => filter.push(a)));
      if (RX.last.test(s) && !RX.count.test(s)) return Object.assign(base, { intent: 'lastAlert', filter });
      if (RX.now.test(s) && !RX.count.test(s)) return Object.assign(base, { intent: 'activeAlerts', filter });
      return Object.assign(base, { intent: 'alertCount', filter });
    }
    if (RX.anomaly.test(s)) return Object.assign(base, { intent: 'anomaly' });
    if (RX.location.test(s)) return Object.assign(base, { intent: 'location' });
    if (sensors.length) return Object.assign(base, { intent: 'sensor' });
    if (RX.summary.test(s)) return Object.assign(base, { intent: 'summary' });
    if (RX.status.test(s)) return Object.assign(base, { intent: 'status' });
    return Object.assign(base, { intent: 'unknown' });
  }

  const API = {
    TH, METRICS, CFG, GAP_MS,
    normVest, normHelmet, normVestLive, normHelmetLive,
    evalVest, evalHelmet, groupIncidents,
    buildSeries, detectMetric, analyze, findGaps, findLying,
    durBn, durEn, median,
    bn2en, parseScope, detectIntent, SENSOR_ALERT_KEYS
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  root.SGEngine = API;
})(typeof window !== 'undefined' ? window : globalThis);
