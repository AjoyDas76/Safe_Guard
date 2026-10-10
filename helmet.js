// =====================================================================
// HELMET MONITORING (additive module, loads after app.js)
// Live data : /helmet1/live   (written by ESP-01 every ~2 s)
// History   : /helmet1/logs   (written every ~30 s, field "timestamp")
// Reuses from app.js: db, createSparkline/updateSparkline, playEmergencySound
// =====================================================================
(function initHelmetModule() {
  'use strict';
  if (typeof db === 'undefined') { console.warn('[Helmet] Firebase db not available'); return; }

  const CFG = {
    live: '/helmet1/live', logs: '/helmet1/logs',
    OBST_CM: 50, GAS_MARGIN: 150, HR_LOW: 50, HR_HIGH: 120, SPO2_LOW: 90,
    STALE_WITH_TS_MS: 15000,   // live node has updatedAt (server time)
    STALE_NO_TS_MS: 30000,     // fallback: no change seen for this long
    VITALS_CONFIRM: 2,         // consecutive bad vitals before alerting (avoids finger-placement glitches)
    OFFLINE_GAP_MS: 75000,     // logs arrive every 30 s -> bigger gap = offline
    LOG_LIMIT: 5000, MAX_PTS: 20
  };

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const num = (v) => { const n = Number(v); return isNaN(n) ? 0 : n; };
  const setText = (id, t) => { const el = $(id); if (el) el.textContent = t; };
  const setStatus = (id, t, color) => { const el = $(id); if (el) { el.textContent = t; el.style.color = color; } };

  // ---------------- server clock offset (for updatedAt freshness) ----------------
  let serverOffset = 0;
  db.ref('.info/serverTimeOffset').on('value', (s) => { serverOffset = Number(s.val()) || 0; });

  // ---------------- sparklines + live charts (same helpers/design as the vest) ----------------
  createSparkline('hmHrSpark', '#ff3366', 'rgba(255, 51, 102, 0.35)');
  createSparkline('hmSpo2Spark', '#00f2ff', 'rgba(0, 242, 255, 0.35)');
  createSparkline('hmGasSpark', '#a855f7', 'rgba(168, 85, 247, 0.35)');

  const hrChart = createLiveChart('hmHrChart', 'Heart Rate (bpm)', '#ff3366', 'rgba(255,51,102,0.1)');
  const spo2Chart = createLiveChart('hmSpo2Chart', 'SpO2 (%)', '#00f2ff', 'rgba(0,242,255,0.1)');
  const gasChart = createLiveChart('hmGasChart', 'Gas level (ADC)', '#a855f7', 'rgba(168,85,247,0.1)');

  // ---------------- alert rules (shared by live view + reports) ----------------
  function evaluate(d) {
    const out = [];
    const sides = [['Left', d.obstLeft, d.distLeft], ['Front', d.obstFront, d.distFront], ['Right', d.obstRight, d.distRight]];
    let anySide = false;
    sides.forEach(([name, flag, dist]) => {
      const dd = num(dist);
      const hit = (flag !== undefined && flag !== null) ? Number(flag) === 1 : (dd > 0 && dd < CFG.OBST_CM);
      if (hit) {
        anySide = true;
        out.push({ key: 'obst', label: 'OBSTACLE', icon: 'fa-triangle-exclamation', title: 'Obstacle ' + name,
          detail: dd && dd < 999 ? 'Object ' + dd + ' cm away on the ' + name.toLowerCase() + ' (limit ' + CFG.OBST_CM + ' cm)' : 'Obstacle detected on the ' + name.toLowerCase() });
      }
    });
    if (!anySide && Number(d.obstacle) === 1) {
      out.push({ key: 'obst', label: 'OBSTACLE', icon: 'fa-triangle-exclamation', title: 'Obstacle Detected', detail: 'Helmet obstacle flag is active' });
    }
    if (Number(d.gas) === 1) {
      out.push({ key: 'gas', label: 'GAS', icon: 'fa-smog', title: 'Gas Alert',
        detail: 'Gas level ' + num(d.gasLevel) + ' is above the clean-air baseline ' + num(d.gasBaseline) + ' (margin +' + CFG.GAS_MARGIN + ')' });
    }
    const hr = num(d.heartRate), sp = num(d.spo2);
    if (hr > 0 && hr < CFG.HR_LOW) out.push({ key: 'hr', label: 'HEART', icon: 'fa-heart-pulse', title: 'Low Heart Rate', detail: 'Heart rate is ' + hr + ' bpm - below ' + CFG.HR_LOW + ' bpm' });
    if (hr > CFG.HR_HIGH) out.push({ key: 'hr', label: 'HEART', icon: 'fa-heart-pulse', title: 'High Heart Rate', detail: 'Heart rate is ' + hr + ' bpm - above ' + CFG.HR_HIGH + ' bpm' });
    if (sp > 0 && sp < CFG.SPO2_LOW) out.push({ key: 'spo2', label: 'SpO2', icon: 'fa-lungs', title: 'Low Blood Oxygen', detail: 'SpO2 is ' + sp + '% - below ' + CFG.SPO2_LOW + '%' });
    return out;
  }

  // ---------------- live state ----------------
  let isConnected = false, isOffline = true, hasData = false, offlineShown = false;
  let lastSeen = 0, lastSeenAtWrite = 0, hasUpdatedAt = false, vitalsBad = 0, lastSound = 0;
  window.__hmAlerts = [];
  window.__hmOnline = false;

  function publishAlerts(list) {
    // Shown in the shared "Active Alerts" card (rendered by app.js)
    window.__hmAlerts = list.map((a) => ({ icon: a.icon, title: 'Helmet: ' + a.title, detail: a.detail }));
    if (typeof window.refreshActiveAlerts === 'function') window.refreshActiveAlerts();
  }

  // Adds a row to the shared "Event / Alert History" table (latest 5 rows)
  function addEventRow(eventText, isAlert, details) {
    const tb = $('eventLogTable');
    if (!tb) return;
    const placeholder = tb.querySelector('td[colspan]');
    if (placeholder) tb.removeChild(placeholder.parentNode);
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${esc(new Date().toLocaleTimeString())}</td><td>${esc(eventText)}</td>` +
      `<td><span class="badge ${isAlert ? 'badge-alert' : 'badge-safe'}">${isAlert ? 'ALERT' : 'SAFE'}</span></td><td>${esc(details)}</td>`;
    tb.insertBefore(tr, tb.firstChild);
    while (tb.children.length > 5) tb.removeChild(tb.lastChild);
  }

  const dist = (v) => { const n = num(v); return n > 0 && n < 999 ? n : null; };

  function onLive(d) {
    if (!d || typeof d !== 'object') return;
    hasData = true;
    hasUpdatedAt = d.updatedAt !== undefined && d.updatedAt !== null;
    lastSeen = hasUpdatedAt ? Number(d.updatedAt) - serverOffset : Date.now();
    lastSeenAtWrite = Date.now();
    if (hasUpdatedAt && Date.now() - lastSeen > CFG.STALE_WITH_TS_MS) { goOffline(); return; }

    isConnected = true; isOffline = false; offlineShown = false; window.__hmOnline = true;
    const timeStr = new Date().toLocaleTimeString();
    const hr = num(d.heartRate), sp = num(d.spo2), gl = num(d.gasLevel), gb = num(d.gasBaseline);
    const gasFlag = Number(d.gas) === 1;

    // --- Heart rate ---
    if (hr > 0) {
      setText('hmHrVal', String(hr));
      const bad = hr < CFG.HR_LOW || hr > CFG.HR_HIGH;
      setStatus('hmHrStatus', hr < CFG.HR_LOW ? 'Low' : hr > CFG.HR_HIGH ? 'High' : 'Normal', bad ? 'var(--red)' : 'var(--green)');
      updateSparkline('hmHrSpark', hr);
      pushLiveChartPoint(hrChart, timeStr, hr);
    } else {
      setText('hmHrVal', '--'); setStatus('hmHrStatus', 'No finger / no reading', 'var(--text-muted)');
    }
    // --- SpO2 ---
    if (sp > 0) {
      setText('hmSpo2Val', String(sp));
      setStatus('hmSpo2Status', sp < CFG.SPO2_LOW ? 'Low' : 'Normal', sp < CFG.SPO2_LOW ? 'var(--red)' : 'var(--green)');
      updateSparkline('hmSpo2Spark', sp);
      pushLiveChartPoint(spo2Chart, timeStr, sp);
    } else {
      setText('hmSpo2Val', '--'); setStatus('hmSpo2Status', 'No finger / no reading', 'var(--text-muted)');
    }
    // --- Gas ---
    setText('hmGasVal', String(gl));
    updateSparkline('hmGasSpark', gl);
    pushLiveChartPoint(gasChart, timeStr, gl);
    if (gb <= 0) {
      setStatus('hmGasStatus', 'Calibrating...', 'var(--amber)');
      setText('hmGasRange', 'Warming up (~30 s)');
    } else {
      setStatus('hmGasStatus', gasFlag ? 'GAS ALERT' : 'Normal', gasFlag ? 'var(--red)' : 'var(--green)');
      setText('hmGasRange', 'Baseline ' + gb + ' | alert > ' + (gb + CFG.GAS_MARGIN));
    }
    // --- Obstacle: Left / Front / Right values + SAFE/ALERT ---
    [['hmDistL', d.distLeft, d.obstLeft], ['hmDistF', d.distFront, d.obstFront], ['hmDistR', d.distRight, d.obstRight]].forEach(([id, dv, flag]) => {
      const el = $(id), dd = dist(dv);
      el.textContent = dd !== null ? dd + ' cm' : 'Clear';
      el.className = Number(flag) === 1 ? 'hit' : '';
    });
    const anyObst = Number(d.obstacle) === 1 || Number(d.obstLeft) === 1 || Number(d.obstFront) === 1 || Number(d.obstRight) === 1;
    const badge = $('hmObjBadge');
    badge.className = 'badge ' + (anyObst ? 'badge-alert' : 'badge-safe');
    badge.textContent = anyObst ? 'ALERT' : 'SAFE';

    // --- Alerts (vitals must stay abnormal for CFG.VITALS_CONFIRM updates) ---
    let alerts = evaluate(d);
    const vitalsHit = alerts.some((a) => a.key === 'hr' || a.key === 'spo2');
    vitalsBad = vitalsHit ? vitalsBad + 1 : 0;
    if (vitalsBad < CFG.VITALS_CONFIRM) alerts = alerts.filter((a) => a.key !== 'hr' && a.key !== 'spo2');
    const hazard = alerts.length > 0;
    publishAlerts(alerts);
    if (hazard && Date.now() - lastSound > 3000) { lastSound = Date.now(); if (typeof playEmergencySound === 'function') playEmergencySound(); }

    addEventRow(hazard ? 'Helmet: ' + alerts[0].title : 'Helmet Sync', hazard,
      'HR ' + (hr > 0 ? hr : '--') + ' | SpO2 ' + (sp > 0 ? sp + '%' : '--') + ' | GAS ' + gl);
  }

  function goOffline() {
    if (offlineShown) return;
    offlineShown = true; isOffline = true; isConnected = false; vitalsBad = 0; window.__hmOnline = false;
    ['hmHrVal', 'hmSpo2Val', 'hmGasVal', 'hmDistL', 'hmDistF', 'hmDistR'].forEach((id) => { setText(id, '--'); const el = $(id); if (el) el.className = ''; });
    ['hmHrStatus', 'hmSpo2Status', 'hmGasStatus'].forEach((id) => setStatus(id, 'No Data', 'var(--text-muted)'));
    setText('hmGasRange', 'Baseline: --');
    const badge = $('hmObjBadge'); badge.className = 'badge badge-safe'; badge.textContent = '--';
    publishAlerts([]);
    addEventRow('Helmet Connection Lost', true, 'No data received');
  }

  db.ref(CFG.live).on('value', (snap) => onLive(snap.val()), (err) => {
    console.error('[Helmet] live read failed (check Firebase rules for /helmet1):', err && err.message);
  });

  // Watchdog: no fresh helmet data -> offline
  setInterval(() => {
    if (!hasData || isOffline) return;
    const limit = hasUpdatedAt ? CFG.STALE_WITH_TS_MS : CFG.STALE_NO_TS_MS;
    const age = Date.now() - (hasUpdatedAt ? lastSeen : lastSeenAtWrite);
    if (age > limit) goOffline();
  }, 1000);

  // ---------------- sidebar entries ----------------
  const navItems = () => Array.from(document.querySelectorAll('.nav-item'));
  function markNav(el) { navItems().forEach((i) => i.classList.remove('active')); if (el) el.classList.add('active'); }
  window.scrollToHelmet = function (e) {
    if (e) { e.preventDefault(); markNav(e.currentTarget.closest('.nav-item')); }
    const t = $('helmetGrid');
    if (t) window.scrollTo({ top: Math.max(0, t.getBoundingClientRect().top + window.pageYOffset - 14), behavior: 'smooth' });
    const sb = $('sidebar'); if (sb && window.innerWidth <= 768) sb.classList.remove('open');
  };

  // =====================================================================
  // HELMET REPORTS (history from /helmet1/logs)
  // =====================================================================
  const modal = $('hmReportsModal');
  let range = 'daily', entries = [], offlineEvents = [];
  const rc = { vitals: null, gas: null };

  const startTs = (r) => Date.now() - (r === 'daily' ? 1 : r === 'weekly' ? 7 : 30) * 86400000;
  const tsOf = (e) => Number(e.timestamp) || 0;
  const fmtTime = (ts) => new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  function fmtDur(ms) {
    const m = Math.round(ms / 60000);
    if (m < 1) return '< ১ মিনিট';
    if (m < 60) return m + ' মিনিট';
    const h = Math.floor(m / 60), r = m % 60;
    return h + ' ঘণ্টা' + (r ? ' ' + r + ' মিনিট' : '');
  }

  window.openHelmetReports = function (e) {
    if (e) { e.preventDefault(); const li = e.currentTarget && e.currentTarget.closest && e.currentTarget.closest('.nav-item'); if (li) markNav(li); }
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
    modal.querySelectorAll('.report-tab').forEach((b) => b.classList.toggle('active', b.dataset.hrange === range));
    loadReport();
  };
  window.closeHelmetReports = function () { modal.classList.remove('active'); document.body.style.overflow = ''; };
  modal.addEventListener('click', (e) => { if (e.target === modal) window.closeHelmetReports(); });
  window.setHelmetRange = function (r) {
    range = r;
    modal.querySelectorAll('.report-tab').forEach((b) => b.classList.toggle('active', b.dataset.hrange === r));
    loadReport();
  };

  function computeOffline(list) {
    const ev = [];
    for (let i = 1; i < list.length; i++) {
      const gap = tsOf(list[i]) - tsOf(list[i - 1]);
      if (gap > CFG.OFFLINE_GAP_MS) ev.push({ from: tsOf(list[i - 1]), to: tsOf(list[i]), durationMs: gap, ongoing: false });
    }
    if (list.length) {
      const last = tsOf(list[list.length - 1]), now = Date.now();
      if (now - last > CFG.OFFLINE_GAP_MS) ev.push({ from: last, to: now, durationMs: now - last, ongoing: true });
    }
    return ev;
  }

  function loadReport() {
    const state = $('hmRepState'), charts = $('hmRepCharts'), exp = $('hmRepExportBtn');
    state.style.display = 'block';
    state.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Loading helmet report...';
    charts.style.display = 'none'; exp.disabled = true;
    ['hmRepSummary', 'hmRepAlerts', 'hmRepConn'].forEach((id) => { $(id).innerHTML = ''; });

    db.ref(CFG.logs).orderByChild('timestamp').startAt(startTs(range)).limitToLast(CFG.LOG_LIMIT).once('value')
      .then((snap) => {
        const list = [];
        snap.forEach((c) => { const v = c.val(); if (v && v.timestamp) list.push(v); });
        list.sort((a, b) => tsOf(a) - tsOf(b));
        entries = list;
        offlineEvents = computeOffline(list);
        if (!list.length) {
          state.innerHTML = 'এই সময়সীমার জন্য হেলমেটের কোনো রিপোর্ট ডাটা জমা হয়নি।<br><span style="font-size:10.5px;">হেলমেট চালু থাকলে প্রতি ৩০ সেকেন্ডে স্বয়ংক্রিয়ভাবে হিস্ট্রি জমা হবে।</span>';
          renderConn(offlineEvents);
          return;
        }
        state.style.display = 'none'; charts.style.display = 'block'; exp.disabled = false;
        renderSummary(list); renderCharts(list); renderAlertList(list); renderConn(offlineEvents);
      })
      .catch((err) => { state.style.display = 'block'; state.textContent = 'হেলমেট রিপোর্ট লোড করতে ব্যর্থ হয়েছে: ' + err.message; });
  }

  function renderSummary(list) {
    const hrs = list.map((e) => num(e.heartRate)).filter((v) => v > 0);
    const sps = list.map((e) => num(e.spo2)).filter((v) => v > 0);
    const avg = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
    let obst = 0, gas = 0, health = 0;
    list.forEach((e) => evaluate(e).forEach((a) => { if (a.key === 'obst') obst++; else if (a.key === 'gas') gas++; else health++; }));
    const f = (v, u) => v === null || v === undefined || !isFinite(v) ? '--' : v + u;
    const cards = [
      ['Records', list.length], ['Avg Heart Rate', f(avg(hrs), ' bpm')],
      ['Min / Max HR', hrs.length ? Math.min.apply(null, hrs) + ' / ' + Math.max.apply(null, hrs) : '--'],
      ['Avg SpO2', f(avg(sps), '%')], ['Min SpO2', sps.length ? Math.min.apply(null, sps) + '%' : '--'],
      ['Obstacle Alerts', obst], ['Gas Alerts', gas], ['Health Alerts', health], ['Offline Events', offlineEvents.length]
    ];
    $('hmRepSummary').innerHTML = cards.map((c) => `<div class="report-summary-card"><div class="rsc-label">${c[0]}</div><div class="rsc-value">${c[1]}</div></div>`).join('');
  }

  function chartOpts() {
    return { responsive: true, maintainAspectRatio: false, animation: false,
      plugins: { legend: { labels: { color: '#8493a8', font: { size: 9 } } } },
      scales: { x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 }, maxTicksLimit: 10 } },
                y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } }, beginAtZero: true } } };
  }

  function renderCharts(list) {
    if (rc.vitals) rc.vitals.destroy();
    if (rc.gas) rc.gas.destroy();
    let labels, hr, sp, gas;
    if (range === 'daily') {
      labels = list.map((e) => new Date(tsOf(e)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
      hr = list.map((e) => num(e.heartRate) || null); sp = list.map((e) => num(e.spo2) || null); gas = list.map((e) => num(e.gasLevel));
    } else {
      const g = {};
      list.forEach((e) => {
        const k = new Date(tsOf(e)).toLocaleDateString([], { month: 'short', day: 'numeric' });
        const o = g[k] || (g[k] = { hr: [], sp: [], gas: [] });
        if (num(e.heartRate) > 0) o.hr.push(num(e.heartRate));
        if (num(e.spo2) > 0) o.sp.push(num(e.spo2));
        o.gas.push(num(e.gasLevel));
      });
      const av = (a) => a.length ? Math.round(a.reduce((x, y) => x + y, 0) / a.length) : null;
      labels = Object.keys(g); hr = labels.map((k) => av(g[k].hr)); sp = labels.map((k) => av(g[k].sp)); gas = labels.map((k) => av(g[k].gas));
    }
    const line = (label, data, color) => ({ label, data, borderColor: color, backgroundColor: 'transparent', tension: 0.3, pointRadius: range === 'daily' ? 0 : 3, spanGaps: true });
    rc.vitals = new Chart($('hmRepVitals').getContext('2d'), { type: 'line', data: { labels, datasets: [line('Heart Rate (bpm)', hr, '#ff3366'), line('SpO2 (%)', sp, '#00f2ff')] }, options: chartOpts() });
    rc.gas = new Chart($('hmRepGas').getContext('2d'), { type: 'line', data: { labels, datasets: [line('Gas level', gas, '#a855f7')] }, options: chartOpts() });
  }

  function renderAlertList(list) {
    const rows = [];
    list.forEach((e) => { const t = evaluate(e); if (t.length) rows.push({ ts: tsOf(e), t }); });
    const box = $('hmRepAlerts');
    if (!rows.length) { box.innerHTML = '<div class="offline-empty">এই সময়সীমায় হেলমেটের কোনো অ্যালার্ট পাওয়া যায়নি — সব রিডিং নিরাপদ ছিল।</div>'; return; }
    const shown = rows.slice(-100).reverse();
    box.innerHTML = '<div class="hm-rep-title">Alert history (' + rows.length + (rows.length > 100 ? ', latest 100 shown' : '') + ')</div>' +
      shown.map((r) => `
        <div class="alert-log-item">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <div>
            <div class="alert-log-time">${esc(fmtTime(r.ts))}</div>
            <div class="alert-log-tags">${r.t.map((a) => `<span class="alert-type-tag alert-type-${a.key}">${esc(a.label)}</span>`).join('')}</div>
            <div class="alert-log-detail">${r.t.map((a) => esc(a.detail)).join(' &middot; ')}</div>
          </div>
        </div>`).join('');
  }

  function renderConn(ev) {
    const wrap = $('hmRepConn');
    if (!ev.length) {
      wrap.innerHTML = '<div class="report-connectivity-title"><i class="fa-solid fa-tower-broadcast"></i> CONNECTIVITY</div><div class="offline-empty">এই সময়সীমায় কোনো সংযোগ বিচ্ছিন্নতা পাওয়া যায়নি — হেলমেট সবসময় ডাটা পাঠিয়েছে।</div>';
      return;
    }
    wrap.innerHTML = '<div class="report-connectivity-title"><i class="fa-solid fa-tower-broadcast"></i> CONNECTIVITY (' + ev.length + ')</div><div class="offline-event-list">' +
      ev.slice().reverse().map((e) => `
        <div class="offline-event-item ${e.ongoing ? 'offline-event-live' : ''}">
          <i class="fa-solid ${e.ongoing ? 'fa-triangle-exclamation' : 'fa-plug-circle-xmark'}"></i>
          <div class="offline-event-text">
            <div>${e.ongoing ? 'বর্তমানে অফলাইন' : 'অফলাইন হয়েছিল'} — ${esc(fmtTime(e.from))}</div>
            <div class="offline-event-sub">${e.ongoing ? 'শেষ ডাটা পাওয়া গিয়েছিল এই সময়ে' : 'সংযোগ ফিরেছে: ' + esc(fmtTime(e.to))} &middot; স্থায়িত্ব: ${fmtDur(e.durationMs)}</div>
          </div>
        </div>`).join('') + '</div>';
  }

  window.exportHelmetReport = function () {
    if (!entries.length || typeof XLSX === 'undefined') return;
    const btn = $('hmRepExportBtn'), html = btn.innerHTML;
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Exporting...'; btn.disabled = true;
    try {
      const header = ['Timestamp', 'Heart Rate (bpm)', 'SpO2 (%)', 'Gas Level', 'Gas Baseline', 'Gas Alert', 'Dist Left (cm)', 'Dist Front (cm)', 'Dist Right (cm)', 'Obstacle Left', 'Obstacle Front', 'Obstacle Right', 'Alerts'];
      const rows = entries.map((e) => [
        new Date(tsOf(e)).toLocaleString(), num(e.heartRate) || '', num(e.spo2) || '', num(e.gasLevel), num(e.gasBaseline),
        Number(e.gas) === 1 ? 'YES' : 'NO', num(e.distLeft), num(e.distFront), num(e.distRight),
        Number(e.obstLeft) === 1 ? 'YES' : 'NO', Number(e.obstFront) === 1 ? 'YES' : 'NO', Number(e.obstRight) === 1 ? 'YES' : 'NO',
        evaluate(e).map((a) => a.title).join('; ')
      ]);
      const ws = XLSX.utils.aoa_to_sheet([['Worker ID', 'SV-001'], ['Helmet ID', 'HELMET-001'], ['Report Range', range.toUpperCase()],
        ['Generated At', new Date().toLocaleString()], ['Records', String(rows.length)], [], header, ...rows]);
      ws['!cols'] = [{ wch: 20 }, ...header.slice(1).map(() => ({ wch: 15 }))];
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'helmet ' + range);
      if (offlineEvents.length) {
        const os = XLSX.utils.aoa_to_sheet([['Offline Since', 'Reconnected / Status', 'Duration'],
          ...offlineEvents.map((e) => [new Date(e.from).toLocaleString(), e.ongoing ? 'Still offline' : new Date(e.to).toLocaleString(), fmtDur(e.durationMs)])]);
        os['!cols'] = [{ wch: 20 }, { wch: 20 }, { wch: 14 }];
        XLSX.utils.book_append_sheet(wb, os, 'offline events');
      }
      XLSX.writeFile(wb, 'helmet1_' + range + '_report_' + new Date().toISOString().replace(/[:.]/g, '-') + '.xlsx');
    } catch (err) {
      alert('রিপোর্ট এক্সপোর্ট করতে ব্যর্থ হয়েছে:\n' + err.message);
    } finally { btn.innerHTML = html; btn.disabled = false; }
  };
})();
