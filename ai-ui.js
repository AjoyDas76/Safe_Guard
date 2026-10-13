/* =====================================================================
   SafeGuard — AI UI  (Ask SafeGuard chat + AI Anomaly Analysis)
   ---------------------------------------------------------------------
   Depends on:  ai-engine.js (window.SGEngine), Firebase `db` from app.js,
                Chart.js and XLSX (already loaded by index.html).
   Reads only (same Firebase paths the dashboard already reads):
        /worker1  /worker1/logs  /helmet1/live  /helmet1/logs
   Nothing is written to Firebase and no Firebase rules need changing.

   Default mode needs NO API key: answers are computed locally from the
   real sensor data. Optionally the user can paste a free Google Gemini
   key (stored only in this browser) so questions the local engine does
   not understand are answered by an LLM that is given the live data.
   ===================================================================== */
(function () {
  'use strict';
  const E = window.SGEngine;
  if (!E) { console.warn('[SafeGuard AI] ai-engine.js missing'); return; }

  const LS_KEY = 'sg_ai_gemini_key';
  const GEMINI_MODEL = 'gemini-flash-latest';   // change here if Google renames the model
  const LOG_LIMIT = 20000, CACHE_TTL = 30000;
  const M = E.METRICS;

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const getDb = () => (typeof db !== 'undefined' ? db : null);
  const el = (tag, attrs, html) => {
    const n = document.createElement(tag);
    Object.keys(attrs || {}).forEach((k) => n.setAttribute(k, attrs[k]));
    if (html !== undefined) n.innerHTML = html;
    return n;
  };

  let lang = 'bn';
  const T = (bn, en) => (lang === 'bn' ? bn : en);

  const fmtTime = (ts) => new Date(ts).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  const fmtDT = (ts) => new Date(ts).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true });

  // ====================================================================
  // DATA LAYER (read-only Firebase access with a short cache)
  // ====================================================================
  const cache = new Map();

  function fetchLogs(kind, since) {
    const d = getDb();
    if (!d) return Promise.reject(new Error('NO_DB'));
    const path = kind === 'vest' ? '/worker1/logs' : '/helmet1/logs';
    const field = kind === 'vest' ? 'ts' : 'timestamp';
    const key = kind + ':' + Math.floor(since / 60000);
    const hit = cache.get(key);
    if (hit && Date.now() - hit.t < CACHE_TTL) return hit.p;
    const p = d.ref(path).orderByChild(field).startAt(since).limitToLast(LOG_LIMIT).once('value').then((snap) => {
      const rows = [];
      snap.forEach((ch) => {
        const v = ch.val();
        if (v && typeof v[field] === 'number') {
          const r = kind === 'vest' ? E.normVest(v) : E.normHelmet(v);
          if (r.ts) rows.push(r);
        }
      });
      rows.sort((a, b) => a.ts - b.ts);
      return rows;
    });
    cache.set(key, { t: Date.now(), p });
    p.catch(() => cache.delete(key));
    return p;
  }

  async function loadRows(scope) {
    const settle = (p) => p.then((rows) => ({ ok: true, rows }), (err) => ({ ok: false, rows: [], err }));
    const [v, h] = await Promise.all([settle(fetchLogs('vest', scope.since)), settle(fetchLogs('helmet', scope.since))]);
    if (!v.ok && !h.ok) throw v.err || h.err;
    const cut = (rows) => rows.filter((r) => r.ts <= scope.until);
    return { vest: cut(v.rows), helmet: cut(h.rows), vestOk: v.ok, helmetOk: h.ok };
  }

  const pillOn = (id) => { const n = $(id); return !!n && n.classList.contains('connected'); };

  async function fetchLive() {
    const d = getDb();
    if (!d) throw new Error('NO_DB');
    const once = (p) => d.ref(p).once('value').then((s) => s.val()).catch(() => null);
    const [v, h] = await Promise.all([once('/worker1'), once('/helmet1/live')]);
    return {
      vest: v ? E.normVestLive(v) : null,
      helmet: h ? E.normHelmetLive(h) : null,
      vestOnline: pillOn('connPill'),
      helmetOnline: pillOn('helmetConnPill')
    };
  }

  function errMsg(e) {
    const m = String((e && (e.code || e.message)) || e || '');
    if (/PERMISSION_DENIED/i.test(m)) return T('ডাটাবেজ পড়ার অনুমতি পাওয়া যায়নি — লগইন/Firebase Rules দেখুন।', 'Database read permission denied — check login / Firebase rules.');
    if (m === 'NO_DB') return T('ডাটাবেজ সংযোগ পাওয়া যায়নি।', 'Database connection not available.');
    return T('ডেটা আনতে সমস্যা হয়েছে: ', 'Could not load data: ') + m;
  }

  // ====================================================================
  // SMALL HELPERS FOR ANSWERS
  // ====================================================================
  const ALERT_NAME = {
    fall: ['ফল', 'Fall'], sos: ['SOS', 'SOS'], temp: ['তাপমাত্রা', 'Temperature'], humidity: ['আর্দ্রতা', 'Humidity'],
    pressure: ['বায়ুচাপ', 'Pressure'], obstacle: ['বাধা', 'Obstacle'], gas: ['গ্যাস', 'Gas'], hr: ['হার্ট রেট', 'Heart rate'], spo2: ['SpO₂', 'SpO₂']
  };
  const aName = (k) => { const n = ALERT_NAME[k] || [k, k]; return T(n[0], n[1]); };
  const mLabel = (k) => T(M[k].bn, M[k].en);
  const fmtVal = (k, v) => Number(v).toFixed(M[k].dec) + M[k].unit;
  const inRange = (k, v) => { const l = M[k].lim; if (!l) return true; if (l[0] !== null && v < l[0]) return false; if (l[1] !== null && v > l[1]) return false; return true; };
  const rangeText = (k) => { const l = M[k].lim; if (!l) return ''; if (l[0] !== null && l[1] !== null) return l[0] + '–' + l[1] + M[k].unit; return l[0] !== null ? '≥ ' + l[0] + M[k].unit : '≤ ' + l[1] + M[k].unit; };
  const lines = (arr) => arr.filter(Boolean).join('<br>');
  const small = (t) => '<span class="ai-small">' + t + '</span>';
  const btn = (act, label, extra) => '<button type="button" class="ai-inline-btn" data-act="' + act + '"' + (extra || '') + '>' + label + '</button>';

  function liveValue(k, live) {
    if (!live) return null;
    if (k === 'temp' || k === 'hum' || k === 'pres') return live.vest && live.vest[k] !== null ? live.vest[k] : null;
    const h = live.helmet;
    if (!h) return null;
    if (k === 'hr') return h.hr > 0 ? h.hr : null;
    if (k === 'spo2') return h.spo2 > 0 ? h.spo2 : null;
    if (k === 'gas') return h.gas > 0 ? h.gas : null;
    return null;
  }
  const isOnlineFor = (k, live) => (live ? (M[k].src === 'vest' ? live.vestOnline : live.helmetOnline) : false);

  function liveAlerts(live) {
    const out = [];
    if (!live) return out;
    if (live.vestOnline && live.vest) E.evalVest(live.vest).forEach((a) => out.push(a));
    if (live.helmetOnline && live.helmet) E.evalHelmet(live.helmet).forEach((a) => out.push(a));
    return out;
  }

  function statsOf(k, rows) {
    const pts = E.buildSeries(k, rows.vest, rows.helmet);
    if (!pts.length) return null;
    let min = pts[0], max = pts[0], sum = 0;
    pts.forEach((p) => { if (p.v < min.v) min = p; if (p.v > max.v) max = p; sum += p.v; });
    return { n: pts.length, min, max, avg: sum / pts.length };
  }

  const scopeTxt = (s) => T(s.bn, s.en);
  const lastSeenTxt = (rows) => (rows.length ? fmtTime(rows[rows.length - 1].ts) : null);

  // ====================================================================
  // LOCAL ANSWER ENGINE (one handler per intent)
  // ====================================================================
  async function hGreet() {
    return T('হ্যালো! আমি <b>Ask SafeGuard</b>। লাইভ সেন্সর ডেটা আর লগ দেখে আপনার প্রশ্নের উত্তর দিই।', 'Hello! I\'m <b>Ask SafeGuard</b>. I answer from the live sensor data and logs.')
      + '<br>' + small(T('যেমন: "আজকে কয়টা অ্যালার্ট হয়েছে?" বা "ওয়ার্কারের অবস্থা এখন কেমন?"', 'e.g. "How many alerts today?" or "What is the worker status now?"'));
  }

  async function hHelp() {
    return lines([
      T('<b>আমি যা যা বলতে পারি:</b>', '<b>What I can answer:</b>'),
      T('• আজ/গত N ঘণ্টায় কয়টা অ্যালার্ট হয়েছে', '• How many alerts today / in the last N hours'),
      T('• ওয়ার্কারের বর্তমান অবস্থা (ভেস্ট + হেলমেট)', '• Current worker status (vest + helmet)'),
      T('• তাপমাত্রা, আর্দ্রতা, বায়ুচাপ, হার্ট রেট, SpO₂, গ্যাস — এখনকার মান ও গড়/সর্বোচ্চ/সর্বনিম্ন', '• Temperature, humidity, pressure, heart rate, SpO₂, gas — now and avg/max/min'),
      T('• সর্বশেষ অ্যালার্ট কখন হয়েছিল', '• When the last alert happened'),
      T('• ওয়ার্কার এখন কোথায় (GPS)', '• Where the worker is (GPS)'),
      T('• অস্বাভাবিক কোনো প্যাটার্ন আছে কি না (AI অ্যানোমালি ডিটেকশন)', '• Any unusual pattern (AI anomaly detection)'),
      T('• আজকের সামারি', '• Today\'s summary')
    ]);
  }

  async function hAlertCount(it) {
    const rows = await loadRows(it.scope);
    const filter = it.filter && it.filter.length ? it.filter : null;
    const keep = (inc) => !filter || filter.indexOf(inc.key) !== -1;
    const vInc = E.groupIncidents(rows.vest, E.evalVest).filter(keep);
    const hInc = E.groupIncidents(rows.helmet, E.evalHelmet).filter(keep);
    const all = vInc.concat(hInc).sort((a, b) => a.start - b.start);
    const snaps = all.reduce((s, i) => s + i.snaps, 0);
    const total = rows.vest.length + rows.helmet.length;
    const scopeName = scopeTxt(it.scope);
    const what = filter ? filter.map(aName).join('/') + ' ' : '';
    const out = [];

    if (!total) {
      out.push(T('⚠️ ' + scopeName + ' কোনো লগ ডেটা পাওয়া যায়নি, তাই অ্যালার্ট গণনা করা যায়নি (ডিভাইস অফলাইন ছিল?)।',
                 '⚠️ No log data found ' + scopeName + ', so alerts cannot be counted (device offline?).'));
    } else if (!all.length) {
      out.push(T('✅ ' + scopeName + ' কোনো ' + what + 'অ্যালার্ট রেকর্ড হয়নি।', '✅ No ' + what + 'alerts recorded ' + scopeName + '.'));
    } else {
      out.push(T(scopeName + ' মোট <b>' + all.length + 'টি</b> ' + what + 'অ্যালার্ট ঘটনা রেকর্ড হয়েছে (ভেস্ট ' + vInc.length + ', হেলমেট ' + hInc.length + ')।',
                 '<b>' + all.length + '</b> ' + what + 'alert incident(s) recorded ' + scopeName + ' (vest ' + vInc.length + ', helmet ' + hInc.length + ').'));
      const by = {};
      all.forEach((i) => { by[i.key] = (by[i.key] || 0) + 1; });
      out.push(Object.keys(by).map((k) => aName(k) + ' ×' + by[k]).join(' · '));
      const last = all[all.length - 1];
      out.push(T('সর্বশেষ: ' + fmtTime(last.end) + ' — ' + last.last.bn, 'Latest: ' + fmtTime(last.end) + ' — ' + last.last.en));
    }

    if (!filter && (it.scope.kind === 'today' || it.scope.kind === 'hours')) {
      try {
        const live = await fetchLive();
        const now = liveAlerts(live);
        if (now.length) out.push('🔴 ' + T('এখন সক্রিয়: ', 'Active now: ') + now.map((a) => esc(T(a.bn, a.en))).join(', '));
      } catch (e) { /* live part is optional */ }
    }
    if (all.length) out.push(small(T('একই ঘটনার টানা ~৩০ সেকেন্ডের লগ একসাথে ১টি ধরা হয়েছে (মোট ' + snaps + 'টি লগ-স্ন্যাপশট)।', 'Consecutive ~30 s logs of the same event count as one incident (' + snaps + ' log snapshots in total).')));
    if (it.scope.capped) out.push(small(T('সর্বোচ্চ ৭ দিনের ডেটা দেখা হয়।', 'At most 7 days of data are scanned.')));
    return lines(out);
  }

  async function hLastAlert(it) {
    const scope = it.scope.kind === 'today' ? { since: Date.now() - 7 * 864e5, until: Date.now(), bn: 'গত ৭ দিনে', en: 'in the last 7 days', kind: 'week' } : it.scope;
    const rows = await loadRows(scope);
    const filter = it.filter && it.filter.length ? it.filter : null;
    const keep = (inc) => !filter || filter.indexOf(inc.key) !== -1;
    const all = E.groupIncidents(rows.vest, E.evalVest).concat(E.groupIncidents(rows.helmet, E.evalHelmet)).filter(keep).sort((a, b) => a.start - b.start);
    if (!all.length) return T('✅ ' + scopeTxt(scope) + ' কোনো অ্যালার্ট পাওয়া যায়নি।', '✅ No alerts found ' + scopeTxt(scope) + '.');
    const last = all[all.length - 1];
    const ago = Date.now() - last.end;
    return lines([
      T('সর্বশেষ অ্যালার্ট: <b>' + aName(last.key) + '</b> — ' + fmtDT(last.start) + (last.end - last.start > 60000 ? ' থেকে ' + fmtTime(last.end) : '') + ' (' + E.durBn(ago) + ' আগে)',
        'Latest alert: <b>' + aName(last.key) + '</b> — ' + fmtDT(last.start) + (last.end - last.start > 60000 ? ' to ' + fmtTime(last.end) : '') + ' (' + E.durEn(ago) + ' ago)'),
      esc(T(last.last.bn, last.last.en)),
      small(T(scopeTxt(scope) + ' মোট ' + all.length + 'টি ঘটনা।', all.length + ' incident(s) ' + scopeTxt(scope) + '.'))
    ]);
  }

  async function hActiveAlerts() {
    const live = await fetchLive();
    if (!live.vestOnline && !live.helmetOnline) return T('⚪ ভেস্ট ও হেলমেট দুটোই এখন অফলাইন, তাই লাইভ অ্যালার্ট বলা যাচ্ছে না।', '⚪ Both vest and helmet are offline, so there are no live alerts to report.');
    const now = liveAlerts(live);
    if (!now.length) return T('✅ এখন কোনো সক্রিয় অ্যালার্ট নেই।', '✅ No active alerts right now.')
      + (live.vestOnline && live.helmetOnline ? '' : '<br>' + small(T('(' + (live.vestOnline ? 'হেলমেট' : 'ভেস্ট') + ' অফলাইন)', '(' + (live.vestOnline ? 'helmet' : 'vest') + ' offline)')));
    return lines(['🔴 ' + T('এখন <b>' + now.length + 'টি</b> সক্রিয় অ্যালার্ট:', '<b>' + now.length + '</b> active alert(s) now:')].concat(now.map((a) => '• ' + esc(T(a.bn, a.en)))));
  }

  async function hStatus() {
    const [live, rows] = await Promise.all([
      fetchLive(),
      loadRows(E.parseScope('today')).catch(() => null)
    ]);
    const out = [];
    const lastV = rows ? lastSeenTxt(rows.vest) : null, lastH = rows ? lastSeenTxt(rows.helmet) : null;
    const dev = (name, on, last) => (on ? '🟢 ' + name + ': ' + T('অনলাইন', 'online') : '🔴 ' + name + ': ' + T('অফলাইন', 'offline') + (last ? ' (' + T('সর্বশেষ ডেটা ', 'last data ') + last + ')' : ''));
    out.push(dev(T('ভেস্ট', 'Vest'), live.vestOnline, lastV));
    out.push(dev(T('হেলমেট', 'Helmet'), live.helmetOnline, lastH));

    const v = live.vest, h = live.helmet;
    if (live.vestOnline && v) {
      const env = [];
      if (v.temp !== null) env.push(fmtVal('temp', v.temp));
      if (v.hum !== null) env.push(fmtVal('hum', v.hum));
      if (v.pres !== null) env.push(fmtVal('pres', v.pres));
      out.push('📟 ' + env.join(' · '));
      if (v.motion) out.push('🚶 ' + T('গতি: ', 'Motion: ') + esc(v.motion) + (v.fall ? ' — ⚠️ FALL' : '') + (v.sos ? ' — 🆘 SOS' : ''));
    }
    if (live.helmetOnline && h) {
      const parts = [];
      if (h.hr > 0) parts.push('❤️ ' + h.hr + ' bpm');
      if (h.spo2 > 0) parts.push('SpO₂ ' + h.spo2 + '%');
      if (h.gas > 0) parts.push(T('গ্যাস ', 'gas ') + h.gas + (h.gasFlag ? ' ⚠️' : ''));
      if (parts.length) out.push('⛑️ ' + parts.join(' · '));
      else out.push('⛑️ ' + T('হার্ট রেট/SpO₂ এখনো পড়া যায়নি (সেন্সরে আঙুল নেই?)', 'No heart-rate / SpO₂ reading yet (finger off the sensor?)'));
    }

    const act = liveAlerts(live);
    let verdict;
    if (!live.vestOnline && !live.helmetOnline) verdict = '⚪ ' + T('<b>তথ্য নেই</b> — দুটো ডিভাইসই অফলাইন', '<b>No data</b> — both devices offline');
    else if (act.some((a) => a.key === 'fall' || a.key === 'sos')) verdict = '🔴 ' + T('<b>জরুরি</b> — ফল/SOS সক্রিয়!', '<b>EMERGENCY</b> — fall/SOS active!');
    else if (act.length) verdict = '🟠 ' + T('<b>সতর্কতা</b> — ' + act.map((a) => esc(a.bn)).join(', '), '<b>Warning</b> — ' + act.map((a) => esc(a.en)).join(', '));
    else if (!live.vestOnline || !live.helmetOnline) verdict = '🟡 ' + T('<b>আংশিক</b> — কোনো অ্যালার্ট নেই, তবে একটি ডিভাইস অফলাইন', '<b>Partial</b> — no alerts, but one device is offline');
    else verdict = '🟢 ' + T('<b>স্বাভাবিক</b> — কোনো সক্রিয় অ্যালার্ট নেই', '<b>Normal</b> — no active alerts');
    out.push('', verdict);
    return lines(out);
  }

  async function hSensor(it) {
    const keys = it.sensors.slice();
    const wantStats = it.stats || it.hasScopeWord;
    const live = await fetchLive().catch(() => null);
    const rows = await loadRows(it.scope).catch(() => null);   // today's min/avg/max next to the live value
    const out = [];

    keys.forEach((k) => {
      if (M[k]) {
        const cur = liveValue(k, live), online = isOfflineSafe(k, live);
        let line = '';
        if (!wantStats || it.scope.kind === 'today') {
          if (cur === null) line = '• <b>' + mLabel(k) + '</b>: ' + T('এখন কোনো মান নেই', 'no current reading') + (isOnlineFor(k, live) ? '' : T(' (' + (M[k].src === 'vest' ? 'ভেস্ট' : 'হেলমেট') + ' অফলাইন)', ' (' + (M[k].src === 'vest' ? 'vest' : 'helmet') + ' offline)'));
          else {
            const ok = inRange(k, cur) && !(k === 'gas' && live.helmet && live.helmet.gasFlag);
            line = '• <b>' + mLabel(k) + '</b>: <b>' + fmtVal(k, cur) + '</b> ' + (ok ? '✅' : '⚠️') + (M[k].lim ? ' ' + small(T('নিরাপদ সীমা ', 'safe ') + rangeText(k)) : '')
              + (online ? '' : ' ' + small(T('(সর্বশেষ জানা মান, ডিভাইস অফলাইন)', '(last known value, device offline)')));
          }
          out.push(line);
        }
        if (rows && (wantStats || it.scope.kind === 'today')) {
          const st = statsOf(k, rows);
          if (st) out.push('&nbsp;&nbsp;' + small(scopeTxt(it.scope) + ': ' + T('গড় ', 'avg ') + fmtVal(k, st.avg) + ' · ' + T('সর্বোচ্চ ', 'max ') + fmtVal(k, st.max.v) + ' (' + fmtTime(st.max.ts) + ') · ' + T('সর্বনিম্ন ', 'min ') + fmtVal(k, st.min.v) + ' (' + fmtTime(st.min.ts) + ')'));
          else if (wantStats) out.push('• ' + mLabel(k) + ': ' + T(scopeTxt(it.scope) + ' কোনো লগ নেই', 'no logs ' + scopeTxt(it.scope)));
        }
      } else if (k === 'obstacle') {
        const h = live && live.helmetOnline ? live.helmet : null;
        if (h) {
          const d = (x) => (x === null || x >= E.TH.NO_ECHO ? '—' : x + ' cm');
          out.push('• <b>' + T('বাধা/দূরত্ব', 'Obstacle / distance') + '</b>: ' + T('বাম ', 'L ') + d(h.dist.L) + ' · ' + T('সামনে ', 'F ') + d(h.dist.F) + ' · ' + T('ডান ', 'R ') + d(h.dist.R) + (h.obstFlag ? ' ⚠️ ' + T('বাধা শনাক্ত', 'obstacle detected') : ' ✅'));
        } else out.push('• <b>' + T('বাধা/দূরত্ব', 'Obstacle') + '</b>: ' + T('হেলমেট অফলাইন', 'helmet offline'));
        if (rows) {
          const n = E.groupIncidents(rows.helmet, E.evalHelmet).filter((i) => i.key === 'obstacle').length;
          out.push('&nbsp;&nbsp;' + small(scopeTxt(it.scope) + ': ' + n + T('বার বাধা শনাক্ত', ' obstacle event(s)')));
        }
      } else if (k === 'motion') {
        const v = live && live.vestOnline ? live.vest : null;
        if (v) out.push('• <b>' + T('গতি', 'Motion') + '</b>: ' + esc(v.motion || '—') + (v.fall ? ' ⚠️ FALL' : ' ✅') + (v.sos ? ' 🆘 SOS' : ''));
        else out.push('• <b>' + T('গতি', 'Motion') + '</b>: ' + T('ভেস্ট অফলাইন', 'vest offline'));
        if (rows) {
          const inc = E.groupIncidents(rows.vest, E.evalVest);
          const f = inc.filter((i) => i.key === 'fall').length, s = inc.filter((i) => i.key === 'sos').length;
          out.push('&nbsp;&nbsp;' + small(scopeTxt(it.scope) + ': ' + T('ফল ' + f + ' বার, SOS ' + s + ' বার', f + ' fall(s), ' + s + ' SOS')));
        }
      } else if (k === 'signal') {
        const r = live && live.vestOnline && live.vest ? live.vest.rssi : null;
        if (r === null || r === undefined) out.push('• <b>' + T('LoRa সিগন্যাল', 'LoRa signal') + '</b>: ' + T('এখন কোনো মান নেই', 'no current value'));
        else out.push('• <b>' + T('LoRa সিগন্যাল', 'LoRa signal') + '</b>: <b>' + r + ' dBm</b> ' + small(r > -100 ? T('ভালো', 'good') : r > -115 ? T('মাঝারি', 'fair') : T('দুর্বল', 'weak')));
      }
    });
    if (it.scope.capped) out.push(small(T('সর্বোচ্চ ৭ দিনের ডেটা দেখা হয়।', 'At most 7 days of data are scanned.')));
    return lines(out);
  }
  function isOfflineSafe(k, live) { return isOnlineFor(k, live); }

  async function hLocation() {
    const live = await fetchLive();
    const v = live.vest;
    if (!v || v.lat === null || v.lng === null || (v.lat === 0 && v.lng === 0))
      return T('📍 এখন কোনো GPS ফিক্স নেই (খোলা আকাশের নিচে নিলে ফিক্স পেতে সময় লাগে)।', '📍 No GPS fix right now (it can take a while to get a fix outdoors).');
    const link = 'https://www.google.com/maps?q=' + v.lat + ',' + v.lng;
    return lines([
      '📍 ' + T('ওয়ার্কারের অবস্থান: ', 'Worker location: ') + '<b>' + v.lat.toFixed(5) + ', ' + v.lng.toFixed(5) + '</b>',
      live.vestOnline ? '' : small(T('(ভেস্ট অফলাইন — এটি সর্বশেষ জানা অবস্থান)', '(vest offline — last known position)')),
      '<a href="' + link + '" target="_blank" rel="noopener">' + T('গুগল ম্যাপে দেখুন', 'Open in Google Maps') + '</a>'
    ]);
  }

  async function hSummary(it) {
    const rows = await loadRows(it.scope);
    const out = [T('<b>' + (it.scope.poss || it.scope.bn) + ' সামারি</b>', '<b>Summary — ' + scopeTxt(it.scope) + '</b>')];
    out.push(T('লগ: ভেস্ট ' + rows.vest.length + ', হেলমেট ' + rows.helmet.length, 'Logs: vest ' + rows.vest.length + ', helmet ' + rows.helmet.length));
    ['temp', 'hum', 'pres', 'hr', 'spo2'].forEach((k) => {
      const st = statsOf(k, rows);
      if (st) out.push('• ' + mLabel(k) + ': ' + T('গড় ', 'avg ') + fmtVal(k, st.avg) + ' (' + fmtVal(k, st.min.v) + ' – ' + fmtVal(k, st.max.v) + ')');
    });
    const inc = E.groupIncidents(rows.vest, E.evalVest).concat(E.groupIncidents(rows.helmet, E.evalHelmet));
    out.push(T('অ্যালার্ট ঘটনা: <b>' + inc.length + '</b>', 'Alert incidents: <b>' + inc.length + '</b>'));
    const gaps = E.findGaps(rows.vest).length + E.findGaps(rows.helmet).length;
    if (gaps) out.push(T('ডেটা বিরতি: ' + gaps + ' বার', 'Data gaps: ' + gaps));
    if (rows.vest.length + rows.helmet.length >= 25) {
      const an = E.analyze({ vest: rows.vest, helmet: rows.helmet });
      const mm = an.findings.filter((f) => f.sev !== 'low' && f.kind !== 'gap');
      out.push('🤖 ' + (mm.length ? T(mm.length + 'টি অস্বাভাবিক প্যাটার্ন পাওয়া গেছে ', mm.length + ' unusual pattern(s) found ') + btn('open-analyze', T('বিস্তারিত', 'Details')) : T('কোনো অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি', 'no unusual pattern found')));
    }
    return lines(out);
  }

  async function hAnomaly(it) {
    const hours = it.scope.kind === 'hours' ? it.scope.hours : it.scope.kind === 'week' ? 168 : it.scope.kind === 'today' ? 6 : 24;
    const scope = { since: Date.now() - hours * 36e5, until: Date.now() };
    const rows = await loadRows(scope);
    const n = rows.vest.length + rows.helmet.length;
    if (n < 25) return T('বিশ্লেষণের জন্য যথেষ্ট লগ নেই (গত ' + hours + ' ঘণ্টায় ' + n + 'টি)। অন্তত ~২৫টি লগ লাগে।', 'Not enough logs to analyse (' + n + ' in the last ' + hours + ' h). About 25 are needed.');
    const an = E.analyze({ vest: rows.vest, helmet: rows.helmet });
    const main = an.findings.filter((f) => f.sev !== 'low');
    const head = an.verdict === 'normal'
      ? '✅ ' + T('গত ' + hours + ' ঘণ্টার ডেটায় কোনো অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি।', 'No unusual pattern in the last ' + hours + ' h of data.')
      : (an.verdict === 'high' ? '🔴 ' : '🟠 ') + T('গত ' + hours + ' ঘণ্টায় <b>' + main.length + 'টি</b> অস্বাভাবিক বিষয় পাওয়া গেছে:', '<b>' + main.length + '</b> unusual finding(s) in the last ' + hours + ' h:');
    const top = main.slice(0, 3).map((f) => '• ' + fmtTime(f.endTs) + ' — ' + esc(T(f.bn, f.en)));
    const missed = an.counts.missed;
    return lines([head].concat(top, [
      missed ? small(T(missed + 'টি ফিক্সড থ্রেশহোল্ডে ধরা পড়ত না।', missed + ' of these would not trigger the fixed thresholds.')) : '',
      btn('open-analyze', T('পুরো বিশ্লেষণ ও চার্ট দেখুন', 'Open full analysis & charts'), ' data-hours="' + hours + '"')
    ]));
  }

  function hUnknown() {
    return lines([
      T('দুঃখিত, প্রশ্নটা পুরোপুরি বুঝতে পারিনি। এগুলো জিজ্ঞেস করে দেখুন:', 'Sorry, I didn\'t quite get that. Try one of these:'),
      T('• আজকে কয়টা অ্যালার্ট হয়েছে?', '• How many alerts today?'),
      T('• ওয়ার্কারের অবস্থা এখন কেমন?', '• What is the worker status now?'),
      T('• তাপমাত্রা কত?', '• What is the temperature?'),
      T('• অস্বাভাবিক কিছু আছে?', '• Is anything unusual?'),
      small(T('(⚙️ থেকে ফ্রি Gemini কী দিলে যেকোনো ধরনের প্রশ্ন করা যাবে)', '(add a free Gemini key in ⚙️ to ask free-form questions)'))
    ]);
  }

  // ====================================================================
  // OPTIONAL LLM FALLBACK (free Gemini key, user-supplied, stays in browser)
  // ====================================================================
  const getKey = () => { try { return localStorage.getItem(LS_KEY) || ''; } catch (e) { return ''; } };

  async function buildContext() {
    const scope = E.parseScope('today');
    const [live, rows] = await Promise.all([fetchLive().catch(() => null), loadRows(scope).catch(() => null)]);
    const ctx = { now: new Date().toString() };
    if (live) {
      ctx.live = {
        vestOnline: live.vestOnline, helmetOnline: live.helmetOnline,
        vest: live.vest ? { temp: live.vest.temp, hum: live.vest.hum, pres: live.vest.pres, motion: live.vest.motion, fall: live.vest.fall, sos: live.vest.sos, rssi: live.vest.rssi } : null,
        helmet: live.helmet ? { hr: live.helmet.hr, spo2: live.helmet.spo2, gas: live.helmet.gas, gasBaseline: live.helmet.gasBase, gasAlert: live.helmet.gasFlag, obstacle: live.helmet.obstFlag } : null,
        activeAlerts: liveAlerts(live).map((a) => a.en)
      };
    }
    if (rows) {
      const inc = E.groupIncidents(rows.vest, E.evalVest).concat(E.groupIncidents(rows.helmet, E.evalHelmet)).sort((a, b) => a.start - b.start);
      const byType = {}; inc.forEach((i) => { byType[i.key] = (byType[i.key] || 0) + 1; });
      const stats = {};
      Object.keys(M).forEach((k) => { const st = statsOf(k, rows); if (st) stats[k] = { avg: +st.avg.toFixed(2), min: st.min.v, max: st.max.v }; });
      ctx.today = {
        logCount: { vest: rows.vest.length, helmet: rows.helmet.length },
        alertIncidents: inc.length, alertsByType: byType,
        recentAlerts: inc.slice(-5).map((i) => ({ type: i.key, time: new Date(i.start).toLocaleTimeString(), detail: i.last.en })),
        stats
      };
      if (rows.vest.length + rows.helmet.length >= 25) {
        const an = E.analyze({ vest: rows.vest, helmet: rows.helmet });
        ctx.anomalies = an.findings.filter((f) => f.sev !== 'low').slice(0, 6).map((f) => ({ severity: f.sev, time: new Date(f.endTs).toLocaleTimeString(), text: f.en }));
      }
    }
    return ctx;
  }

  async function askGemini(question) {
    const key = getKey();
    const ctx = await buildContext();
    const sys = 'You are "Ask SafeGuard", the assistant of an industrial worker-safety dashboard (smart vest + smart helmet). '
      + 'Answer ONLY from the JSON context provided. If the context does not contain the answer, say you do not have that data. '
      + 'Never invent numbers. Reply in the same language as the question (Bengali or English), in at most 5 short sentences, plain text. '
      + 'For anything safety-critical remind the user that the dashboard alerts are authoritative.';
    const res = await fetch('https://generativelanguage.googleapis.com/v1beta/models/' + GEMINI_MODEL + ':generateContent', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: sys }] },
        contents: [{ role: 'user', parts: [{ text: 'CONTEXT:\n' + JSON.stringify(ctx) + '\n\nQUESTION: ' + question }] }],
        generationConfig: { temperature: 0.2, maxOutputTokens: 600 }
      })
    });
    const j = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((j.error && j.error.message) || ('HTTP ' + res.status));
    const parts = j.candidates && j.candidates[0] && j.candidates[0].content && j.candidates[0].content.parts;
    const text = parts ? parts.map((p) => p.text || '').join('') : '';
    if (!text) throw new Error(T('LLM থেকে কোনো উত্তর আসেনি', 'The LLM returned no answer'));
    return esc(text).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>') + '<br>' + small('✨ Gemini');
  }

  // ====================================================================
  // ROUTER
  // ====================================================================
  async function route(it, text) {
    switch (it.intent) {
      case 'greet': return hGreet();
      case 'help': return hHelp();
      case 'alertCount': return hAlertCount(it);
      case 'lastAlert': return hLastAlert(it);
      case 'activeAlerts': return hActiveAlerts();
      case 'status': return hStatus();
      case 'sensor': return hSensor(it);
      case 'location': return hLocation();
      case 'summary': return hSummary(it);
      case 'anomaly': return hAnomaly(it);
      default:
        if (getKey()) return askGemini(text);
        return hUnknown();
    }
  }

  // ====================================================================
  // CHAT WIDGET
  // ====================================================================
  let busy = false, welcomed = false;

  function setScaleVar() {
    // The dashboard renders a 1280px desktop layout on phones (see index.html).
    // Scale the widget up there so it stays readable.
    const sw = Math.min(screen.width, screen.height) || window.innerWidth;
    const k = sw >= 1024 ? 1 : Math.max(1, Math.min(3.2, (1280 / sw) * 0.85));
    document.documentElement.style.setProperty('--ai-k', k.toFixed(2));
  }

  const CHIPS_BN = ['আজকে কয়টা অ্যালার্ট হয়েছে?', 'ওয়ার্কারের অবস্থা এখন কেমন?', 'অস্বাভাবিক কিছু আছে?', 'তাপমাত্রা কত?', 'আজকের সামারি'];

  function buildChat() {
    const fab = el('button', { class: 'ai-fab', id: 'aiFab', type: 'button', 'aria-label': 'Open Ask SafeGuard chat' },
      '<i class="fa-solid fa-robot" aria-hidden="true"></i><span>Ask SafeGuard</span>');
    const panel = el('section', { class: 'ai-panel', id: 'aiPanel', role: 'dialog', 'aria-label': 'Ask SafeGuard' }, `
      <div class="ai-head">
        <div class="ai-head-ico"><i class="fa-solid fa-robot" aria-hidden="true"></i></div>
        <div class="ai-head-txt"><b>Ask SafeGuard</b><span id="aiModeLabel"></span></div>
        <button type="button" class="ai-head-btn" id="aiGear" aria-label="AI settings" title="Settings"><i class="fa-solid fa-gear"></i></button>
        <button type="button" class="ai-head-btn" id="aiClose" aria-label="Close chat" title="Close"><i class="fa-solid fa-xmark"></i></button>
      </div>
      <div class="ai-settings" id="aiSettings" style="display:none"></div>
      <div class="ai-msgs" id="aiMsgs" aria-live="polite"></div>
      <div class="ai-chips" id="aiChips"></div>
      <div class="ai-input-row">
        <input type="text" id="aiInput" class="ai-input" placeholder="প্রশ্ন লিখুন… / Ask a question…" autocomplete="off" maxlength="300">
        <button type="button" class="ai-send" id="aiSend" aria-label="Send"><i class="fa-solid fa-paper-plane"></i></button>
      </div>`);
    document.body.appendChild(fab);
    document.body.appendChild(panel);

    const chips = $('aiChips');
    CHIPS_BN.forEach((c) => {
      const b = el('button', { type: 'button', class: 'ai-chip' }, esc(c));
      b.addEventListener('click', () => onSend(c));
      chips.appendChild(b);
    });

    fab.addEventListener('click', () => toggleChat(true));
    $('aiClose').addEventListener('click', () => toggleChat(false));
    $('aiGear').addEventListener('click', toggleSettings);
    $('aiSend').addEventListener('click', () => { const i = $('aiInput'); onSend(i.value); i.value = ''; });
    $('aiInput').addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); const i = $('aiInput'); onSend(i.value); i.value = ''; }
    });
    $('aiMsgs').addEventListener('click', (e) => {
      const t = e.target.closest && e.target.closest('[data-act="open-analyze"]');
      if (t) { openAnalysis(t.getAttribute('data-hours')); }
    });
    refreshModeLabel();
  }

  function refreshModeLabel() {
    const n = $('aiModeLabel');
    if (n) n.textContent = getKey() ? 'Local engine + Gemini' : 'Local AI engine · live data';
  }

  function toggleChat(open) {
    $('aiPanel').classList.toggle('open', open);
    $('aiFab').classList.toggle('hide', open);
    if (open) {
      if (!welcomed) {
        welcomed = true;
        lang = 'bn';
        addMsg('bot', hGreetSync());
      }
      setTimeout(() => $('aiInput').focus(), 50);
    }
  }
  function hGreetSync() {
    return 'হ্যালো! আমি <b>Ask SafeGuard</b> 👋<br>' + small('লাইভ সেন্সর ডেটা ও লগ দেখে উত্তর দিই। নিচের প্রশ্নগুলো চেপে দেখুন, অথবা নিজের মতো লিখুন (বাংলা/English)।');
  }

  function addMsg(who, html) {
    const box = $('aiMsgs');
    const m = el('div', { class: 'ai-msg ' + who }, html);
    box.appendChild(m);
    box.scrollTop = box.scrollHeight;
    return m;
  }
  function addTyping() {
    return addMsg('bot typing', '<span class="ai-dots"><i></i><i></i><i></i></span>');
  }

  async function onSend(text) {
    text = String(text || '').trim();
    if (!text || busy) return;
    addMsg('user', esc(text));
    busy = true;
    const typing = addTyping();
    try {
      lang = /[\u0980-\u09FF]/.test(text) ? 'bn' : 'en';
      const it = E.detectIntent(text);
      const html = await route(it, text);
      typing.remove();
      addMsg('bot', html);
    } catch (e) {
      console.error('[SafeGuard AI]', e);
      typing.remove();
      addMsg('bot', '⚠️ ' + esc(errMsg(e)));
    } finally { busy = false; }
  }

  function toggleSettings() {
    const s = $('aiSettings');
    if (s.style.display === 'none') {
      const has = !!getKey();
      s.innerHTML = `
        <div class="ai-set-title">${has ? '✅ Gemini কী সেট করা আছে' : 'ঐচ্ছিক: ফ্রি Gemini কী'}</div>
        <div class="ai-small">কী ছাড়াই লোকাল ইঞ্জিন কাজ করে। কী দিলে ইঞ্জিন যা বোঝে না, সেই প্রশ্ন Google Gemini-কে পাঠানো হয় (সাথে সামারাইজড সেন্সর ডেটা; GPS পাঠানো হয় না)। কী শুধু এই ব্রাউজারে থাকে। ফ্রি কী: aistudio.google.com/apikey</div>
        <div class="ai-set-row">
          <input type="password" id="aiKeyInput" class="ai-input" placeholder="AIza…" autocomplete="off">
          <button type="button" class="ai-inline-btn" id="aiKeySave">Save</button>
          ${has ? '<button type="button" class="ai-inline-btn danger" id="aiKeyDel">Remove</button>' : ''}
        </div>`;
      s.style.display = 'block';
      $('aiKeySave').addEventListener('click', () => {
        const v = $('aiKeyInput').value.trim();
        if (!v) return;
        try { localStorage.setItem(LS_KEY, v); } catch (e) { /* storage blocked */ }
        s.style.display = 'none'; refreshModeLabel();
        addMsg('bot', '✅ Gemini কী সংরক্ষিত হয়েছে।');
      });
      const del = $('aiKeyDel');
      if (del) del.addEventListener('click', () => { try { localStorage.removeItem(LS_KEY); } catch (e) { /* ignore */ } s.style.display = 'none'; refreshModeLabel(); addMsg('bot', 'Gemini কী মুছে ফেলা হয়েছে।'); });
    } else s.style.display = 'none';
  }

  // ====================================================================
  // AI ANOMALY ANALYSIS MODAL (manual: runs only when ANALYZE is pressed)
  // ====================================================================
  const WINDOWS = { '1h': 36e5, '6h': 6 * 36e5, '24h': 864e5, '7d': 7 * 864e5 };
  let winKey = '6h', lastResult = null, lastMetric = null, aiChart = null, showMinor = false, running = false;

  function buildModal() {
    const m = el('div', { class: 'map-modal', id: 'aiModal', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'AI anomaly analysis' }, `
      <div class="map-modal-content ai-modal-content">
        <div class="map-modal-header">
          <h3><i class="fa-solid fa-brain"></i> AI ANOMALY ANALYSIS</h3>
          <button class="close-modal-btn" id="aiModalClose" aria-label="Close analysis"><i class="fa-solid fa-xmark" aria-hidden="true"></i> CLOSE</button>
        </div>
        <div class="ai-modal-body">
          <div class="ai-controls">
            <div class="report-range-tabs" id="aiWinTabs">
              <button type="button" class="report-tab" data-w="1h">LAST 1H</button>
              <button type="button" class="report-tab active" data-w="6h">LAST 6H</button>
              <button type="button" class="report-tab" data-w="24h">LAST 24H</button>
              <button type="button" class="report-tab" data-w="7d">LAST 7D</button>
            </div>
            <button type="button" class="btn-action ai-run-btn" id="aiRunBtn"><i class="fa-solid fa-wand-magic-sparkles"></i> ANALYZE</button>
          </div>
          <div class="ai-intro" id="aiIntro">সময়সীমা বেছে <b>ANALYZE</b> চাপুন। সিস্টেম সংরক্ষিত লগ ডেটা দেখে ফিক্সড থ্রেশহোল্ডের বাইরের অস্বাভাবিক প্যাটার্ন (হঠাৎ লাফ, ধীরে ধীরে বাড়া/কমা, আটকে যাওয়া সেন্সর, ডেটা বিরতি, একসাথে একাধিক সেন্সরের পরিবর্তন) খুঁজে বের করবে। এটি নিজে থেকে চলে না — শুধু বাটন চাপলে।</div>
          <div class="report-state-msg" id="aiState" style="display:none;"></div>
          <div id="aiResult" style="display:none;">
            <div class="report-summary-grid" id="aiCards"></div>
            <div class="ai-metric-tabs" id="aiMetricTabs"></div>
            <div class="ai-chart-wrap"><canvas id="aiChartCanvas"></canvas></div>
            <div class="ai-legend"><span class="lg-line"></span> মান &nbsp; <span class="lg-dot"></span> অস্বাভাবিক বিন্দু &nbsp; <span class="lg-dash"></span> ফিক্সড নিরাপদ সীমা</div>
            <h4 class="ai-h">FINDINGS</h4>
            <div class="ai-findings" id="aiFindings"></div>
            <div class="ai-actions">
              <button type="button" class="hm-tab" id="aiMinorBtn" style="display:none"></button>
              <button type="button" class="btn-action" id="aiExportBtn"><i class="fa-solid fa-download"></i> Export findings (Excel)</button>
            </div>
            <details class="ai-how"><summary>এটি কীভাবে কাজ করে? (How it works)</summary><div id="aiHow"></div></details>
          </div>
        </div>
      </div>`);
    document.body.appendChild(m);
    $('aiModalClose').addEventListener('click', closeAnalysis);
    m.addEventListener('click', (e) => { if (e.target === m) closeAnalysis(); });
    $('aiRunBtn').addEventListener('click', runAnalysis);
    $('aiWinTabs').addEventListener('click', (e) => {
      const b = e.target.closest('[data-w]');
      if (!b) return;
      setWindow(b.getAttribute('data-w'));
    });
    $('aiMinorBtn').addEventListener('click', () => { showMinor = !showMinor; renderFindings(); });
    $('aiExportBtn').addEventListener('click', exportFindings);
    $('aiHow').innerHTML = howItWorks();
  }

  function setWindow(w) {
    winKey = w;
    document.querySelectorAll('#aiWinTabs .report-tab').forEach((b) => b.classList.toggle('active', b.getAttribute('data-w') === w));
  }

  function openAnalysis(hours) {
    if (hours) {
      const h = parseInt(hours, 10);
      setWindow(h <= 1 ? '1h' : h <= 6 ? '6h' : h <= 24 ? '24h' : '7d');
    }
    toggleChat(false);
    $('aiModal').classList.add('active');
    document.body.style.overflow = 'hidden';
    if (hours) runAnalysis();
  }
  function closeAnalysis() {
    $('aiModal').classList.remove('active');
    document.body.style.overflow = '';
  }

  function stateMsg(html, isErr) {
    const s = $('aiState');
    s.style.display = html ? 'block' : 'none';
    s.innerHTML = html || '';
    s.style.color = isErr ? 'var(--red)' : '';
  }

  async function runAnalysis() {
    if (running) return;
    running = true;
    lang = 'bn';
    const runBtn = $('aiRunBtn');
    const original = runBtn.innerHTML;
    runBtn.disabled = true;
    runBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> ANALYZING…';
    $('aiResult').style.display = 'none';
    $('aiIntro').style.display = 'none';
    stateMsg('<i class="fa-solid fa-spinner fa-spin"></i> লগ ডেটা লোড ও বিশ্লেষণ হচ্ছে…');
    try {
      cache.clear();                                   // always analyse fresh data
      const scope = { since: Date.now() - WINDOWS[winKey], until: Date.now() };
      const rows = await loadRows(scope);
      const n = rows.vest.length + rows.helmet.length;
      if (n < 25) {
        stateMsg('এই সময়সীমায় যথেষ্ট লগ ডেটা নেই (' + n + 'টি)। বিশ্লেষণের জন্য অন্তত ~২৫টি লগ লাগে — ডিভাইস কিছুক্ষণ চালু রেখে বা বড় সময়সীমা বেছে আবার চেষ্টা করুন।');
        return;
      }
      const t0 = performance.now();
      lastResult = E.analyze({ vest: rows.vest, helmet: rows.helmet });
      lastResult.ms = Math.round(performance.now() - t0);
      lastResult.win = winKey;
      showMinor = false;
      stateMsg('');
      renderResult();
    } catch (e) {
      console.error('[SafeGuard AI]', e);
      stateMsg(esc(errMsg(e)), true);
    } finally {
      running = false;
      runBtn.disabled = false;
      runBtn.innerHTML = original;
    }
  }

  function renderResult() {
    const r = lastResult;
    $('aiResult').style.display = 'block';
    const verdictTxt = { normal: 'স্বাভাবিক', medium: 'পর্যবেক্ষণ করুন', high: 'অস্বাভাবিক!' }[r.verdict];
    const verdictCol = { normal: 'var(--green)', medium: 'var(--amber)', high: 'var(--red)' }[r.verdict];
    const cards = [
      ['AI VERDICT', '<span style="color:' + verdictCol + '">' + verdictTxt + '</span>'],
      ['RECORDS', r.counts.vest + ' / ' + r.counts.helmet + '<div class="ai-card-sub">vest / helmet</div>'],
      ['ANOMALIES', String(r.counts.anomalies)],
      ['MISSED BY FIXED LIMITS', '<span style="color:' + (r.counts.missed ? 'var(--amber)' : 'inherit') + '">' + r.counts.missed + '</span><div class="ai-card-sub">শুধু AI ধরেছে</div>'],
      ['DATA GAPS', String(r.counts.gaps)],
      ['RUN TIME', r.ms + ' ms']
    ];
    $('aiCards').innerHTML = cards.map((c) => '<div class="report-summary-card"><div class="rsc-label">' + c[0] + '</div><div class="rsc-value">' + c[1] + '</div></div>').join('');

    // metric tabs (only metrics that have data)
    const keys = Object.keys(r.perMetric).filter((k) => r.perMetric[k].points.length);
    const withEp = keys.filter((k) => r.perMetric[k].episodes.some((e) => e.sev !== 'low'));
    lastMetric = withEp[0] || keys[0] || null;
    $('aiMetricTabs').innerHTML = keys.map((k) => {
      const nEp = r.perMetric[k].episodes.filter((e) => e.sev !== 'low').length;
      return '<button type="button" class="hm-tab' + (k === lastMetric ? ' active' : '') + '" data-m="' + k + '">' + esc(M[k].en) + (nEp ? ' <span class="ai-badge">' + nEp + '</span>' : '') + '</button>';
    }).join('');
    $('aiMetricTabs').onclick = (e) => {
      const b = e.target.closest('[data-m]');
      if (!b) return;
      lastMetric = b.getAttribute('data-m');
      document.querySelectorAll('#aiMetricTabs .hm-tab').forEach((x) => x.classList.toggle('active', x === b));
      drawChart();
    };
    drawChart();
    renderFindings();
  }

  function drawChart() {
    const canvas = $('aiChartCanvas');
    if (aiChart) { aiChart.destroy(); aiChart = null; }
    const r = lastResult;
    if (!r || !lastMetric || typeof Chart === 'undefined') return;
    const res = r.perMetric[lastMetric], m = M[lastMetric], pts = res.points;
    const longWin = pts.length && (pts[pts.length - 1].ts - pts[0].ts) > 20 * 36e5;
    const label = (ts) => (longWin ? new Date(ts).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
                                   : new Date(ts).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }));
    const stride = Math.max(1, Math.ceil(pts.length / 1200));
    const labels = [], vals = [], flagged = [];
    pts.forEach((p, i) => {
      const f = res.flags[i];
      if (i % stride === 0 || f) {
        labels.push(label(p.ts));
        vals.push(p.v);
        flagged.push(f ? p.v : null);
      }
    });
    const ds = [
      { label: m.en, data: vals, borderColor: '#00f2ff', backgroundColor: 'rgba(0,242,255,0.08)', borderWidth: 1.5, pointRadius: 0, tension: 0.15, fill: false },
      { label: 'Anomaly', data: flagged, borderColor: '#ff3366', backgroundColor: '#ff3366', showLine: false, pointRadius: 4, pointHoverRadius: 6 }
    ];
    if (m.lim) m.lim.forEach((lv, idx) => {
      if (lv === null) return;
      ds.push({ label: idx === 0 ? 'Min limit' : 'Max limit', data: labels.map(() => lv), borderColor: '#f59e0b', borderDash: [5, 4], borderWidth: 1, pointRadius: 0, fill: false });
    });
    aiChart = new Chart(canvas, {
      type: 'line',
      data: { labels, datasets: ds },
      options: {
        responsive: true, maintainAspectRatio: false, animation: false,
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: '#8493a8', maxTicksLimit: 8, maxRotation: 0, font: { size: 9 } }, grid: { color: 'rgba(132,147,168,0.12)' } },
          y: { ticks: { color: '#8493a8', font: { size: 9 } }, grid: { color: 'rgba(132,147,168,0.12)' }, title: { display: !!m.unit.trim(), text: m.unit.trim(), color: '#8493a8' } }
        }
      }
    });
  }

  const SEV_LABEL = { high: 'HIGH', medium: 'MEDIUM', low: 'MINOR' };
  function renderFindings() {
    const r = lastResult;
    const box = $('aiFindings');
    const main = r.findings.filter((f) => f.sev !== 'low');
    const minor = r.findings.filter((f) => f.sev === 'low');
    const item = (f) => {
      const when = fmtDT(f.startTs) + (f.endTs - f.startTs > 60000 ? ' → ' + fmtTime(f.endTs) : '');
      let cmp = '';
      if (f.kind === 'episode' || f.kind === 'forecast' || f.kind === 'cross')
        cmp = f.caught ? '<span class="ai-cmp ok">ফিক্সড থ্রেশহোল্ডেও অ্যালার্ট হতো</span>' : '<span class="ai-cmp miss">ফিক্সড থ্রেশহোল্ড মিস করত</span>';
      return '<div class="ai-find sev-' + f.sev + '"><div class="ai-find-top"><span class="ai-sev">' + SEV_LABEL[f.sev] + '</span><span class="ai-tag">' + esc(f.tag) + '</span>' + cmp + '<span class="ai-when">' + when + '</span></div><div class="ai-find-txt">' + esc(f.bn) + '</div></div>';
    };
    let html = main.length ? main.map(item).join('') : '<div class="ai-ok">✅ এই সময়সীমায় কোনো উল্লেখযোগ্য অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি।</div>';
    if (showMinor) html += minor.map(item).join('');
    box.innerHTML = html;
    const mb = $('aiMinorBtn');
    mb.style.display = minor.length ? '' : 'none';
    mb.textContent = showMinor ? 'Hide minor findings' : 'Show ' + minor.length + ' minor finding(s)';
  }

  function exportFindings() {
    if (!lastResult || typeof XLSX === 'undefined') { alert('Excel লাইব্রেরি লোড হয়নি।'); return; }
    const r = lastResult;
    const aoa = [
      ['SafeGuard AI anomaly analysis'], ['Window', r.win], ['Generated', new Date().toLocaleString()],
      ['Vest records', r.counts.vest], ['Helmet records', r.counts.helmet], ['Verdict', r.verdict], [],
      ['Severity', 'Type', 'Metric', 'Start', 'End', 'Fixed threshold also alarms?', 'Description (Bangla)', 'Description (English)']
    ];
    r.findings.forEach((f) => aoa.push([f.sev, f.tag, f.key, new Date(f.startTs).toLocaleString(), new Date(f.endTs).toLocaleString(),
      (f.kind === 'episode' || f.kind === 'forecast' || f.kind === 'cross') ? (f.caught ? 'YES' : 'NO') : '', f.bn, f.en]));
    const ws = XLSX.utils.aoa_to_sheet(aoa);
    ws['!cols'] = [{ wch: 9 }, { wch: 11 }, { wch: 9 }, { wch: 20 }, { wch: 20 }, { wch: 14 }, { wch: 60 }, { wch: 60 }];
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'AI Findings');
    XLSX.writeFile(wb, 'safeguard_ai_findings_' + new Date().toISOString().replace(/[:.]/g, '-') + '.xlsx');
  }

  function howItWorks() {
    const C = E.CFG;
    return '<ol>'
      + '<li><b>Robust z-score:</b> প্রতিটি রিডিং তার আগের ' + C.BASE_WIN + 'টি রিডিংয়ের median ও MAD-এর সাথে তুলনা করা হয়। ' + C.Z_FLAG + 'σ-এর বেশি দূরে গেলে ফ্ল্যাগ। (সেন্সর নয়েজের চেয়ে ছোট পরিবর্তন উপেক্ষা করা হয়।)</li>'
      + '<li><b>Sudden jump:</b> পরপর দুই রিডিংয়ের মধ্যে অস্বাভাবিক বড় লাফ (যেমন তাপমাত্রা ' + M.temp.jump + '°C+, হার্ট রেট ' + M.hr.jump + ' bpm+)।</li>'
      + '<li><b>Sustained trend:</b> ~' + C.TREND_WIN + 'টি রিডিংয়ের উইন্ডোতে রৈখিক রিগ্রেশন (slope ও R² ≥ ' + C.TREND_R2 + ')। ধীরে বাড়া/কমা ধরে, এবং বর্তমান হারে নিরাপদ সীমা ছুঁতে কত মিনিট লাগবে তার পূর্বাভাস দেয় — সীমা পার হওয়ার <i>আগেই</i> সতর্কতা।</li>'
      + '<li><b>Flat-line:</b> ' + C.FLAT_RUN + 'টি টানা রিডিং হুবহু একই থাকলে সেন্সর আটকে যাওয়ার সন্দেহ।</li>'
      + '<li><b>Data gap:</b> লগে ৩ মিনিটের বেশি ফাঁক থাকলে সংযোগ-সমস্যা হিসেবে গণনা।</li>'
      + '<li><b>Prolonged inactivity:</b> ভেস্টের motion_state টানা ' + (C.LYING_MS / 60000) + ' মিনিটের বেশি LYING/FALL থাকলে।</li>'
      + '<li><b>Cross-signal:</b> তাপমাত্রা ও হার্ট রেট একই সময়ে বাড়লে হিট-স্ট্রেসের সম্ভাব্য প্যাটার্ন (ডাক্তারি রোগনির্ণয় নয় — যাচাই করতে হবে)।</li>'
      + '</ol><p class="ai-small">সব হিসাব ব্রাউজারেই হয়; কোনো API কী, সার্ভার বা খরচ লাগে না। প্যারামিটারগুলো <code>ai-engine.js</code>-এর <code>METRICS</code> ও <code>CFG</code>-এ পরিবর্তনযোগ্য। সেন্সর অনুযায়ী (বিশেষ করে হার্ট রেট) আপনাদের রেকর্ড করা ডেটা দিয়ে টিউন করা উচিত।</p>';
  }

  // ====================================================================
  // INIT
  // ====================================================================
  function init() {
    setScaleVar();
    buildChat();
    buildModal();
    document.addEventListener('keydown', (e) => {
      if (e.key !== 'Escape') return;
      if ($('aiModal').classList.contains('active')) closeAnalysis();
      else if ($('aiPanel').classList.contains('open')) toggleChat(false);
    });
    window.addEventListener('orientationchange', () => setTimeout(setScaleVar, 350));
    window.openAiAnalysis = function (e) { if (e && e.preventDefault) e.preventDefault(); openAnalysis(); };
    window.closeAiAnalysis = closeAnalysis;
    window.toggleAskSafeGuard = function () { toggleChat(!$('aiPanel').classList.contains('open')); };
    window.SGAI = { ask: onSend };
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
