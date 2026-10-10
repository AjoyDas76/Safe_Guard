let audioEnabled = false;
    let audioCtx = null;

    function toggleAudio() {
      audioEnabled = !audioEnabled;
      const audioBtn = document.getElementById('audioBtn');
      if (audioEnabled) {
        audioBtn.classList.add('active-sound');
        audioBtn.innerHTML = '<i class="fa-solid fa-volume-high" id="audioIcon"></i> Audio: ON';
        if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      } else {
        audioBtn.classList.remove('active-sound');
        audioBtn.innerHTML = '<i class="fa-solid fa-volume-xmark" id="audioIcon"></i> Audio: OFF';
      }
    }

    function playEmergencySound() {
      if (!audioEnabled) return;
      if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(880, audioCtx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(440, audioCtx.currentTime + 0.5);
      gain.gain.setValueAtTime(0.3, audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.5);
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      osc.stop(audioCtx.currentTime + 0.5);
    }

    function toggleTheme() { document.body.classList.toggle('light-theme'); }

    // ===================== Export Worker Data (Excel) =====================
    // Fetches the full '/worker1' node from Firebase Realtime Database,
    // ===================== Export Worker Data History (Excel) =====================
    // Firebase Realtime Database only ever holds the CURRENT snapshot at
    // '/worker1' — it doesn't keep past values. So to export "last 5/10"
    // readings, we build our own rolling history on the client: every time
    // a new snapshot streams in (processIncomingData), we flatten it and
    // push it into `dataHistory`, capped at MAX_HISTORY entries.

    const MAX_HISTORY = 10; // keeps up to the last 10 readings in memory
    let dataHistory = [];

    // Recursively flattens a nested object into a flat { "a > b": value } map,
    // e.g. { environment: { temperature: 30 } } -> { "environment > temperature": 30 }
    function flattenToObject(obj, prefix, result) {
      Object.keys(obj).forEach((key) => {
        const value = obj[key];
        const label = prefix ? `${prefix} > ${key}` : key;
        if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
          flattenToObject(value, label, result);
        } else if (Array.isArray(value)) {
          result[label] = JSON.stringify(value);
        } else {
          result[label] = value;
        }
      });
    }

    // Called from processIncomingData() on every new reading.
    function recordHistorySnapshot(data) {
      const flat = {};
      flattenToObject(data, '', flat);

      const last = dataHistory[dataHistory.length - 1];
      // Skip if identical to the last recorded reading (avoids duplicate
      // rows since both the realtime listener and the 2s polling interval
      // can fire for the same underlying value).
      if (last && JSON.stringify(last.fields) === JSON.stringify(flat)) return;

      dataHistory.push({ timestamp: new Date().toLocaleString(), fields: flat });
      if (dataHistory.length > MAX_HISTORY) dataHistory.shift();
    }

    function exportWorkerData() {
      if (dataHistory.length === 0) {
        alert('এক্সপোর্ট করার জন্য এখনো কোনো ডাটা রেকর্ড হয়নি।\nপেজটি একটু সময় ধরে খোলা রাখুন যাতে সাম্প্রতিক রিডিং জমা হতে পারে, তারপর আবার চেষ্টা করুন।');
        return;
      }

      const maxAvailable = dataHistory.length;
      const defaultCount = Math.min(10, maxAvailable);
      const input = prompt(
        `সাম্প্রতিক কতগুলো রিডিং এক্সপোর্ট করতে চান? (এখন পর্যন্ত সর্বোচ্চ ${maxAvailable}টি জমা আছে)`,
        String(defaultCount)
      );
      if (input === null) return; // user cancelled

      let count = parseInt(input, 10);
      if (isNaN(count) || count <= 0) count = defaultCount;
      count = Math.min(count, maxAvailable);

      const exportBtn = document.getElementById('exportBtn');
      const originalHtml = exportBtn.innerHTML;
      exportBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Exporting...';
      exportBtn.disabled = true;

      try {
        const selectedEntries = dataHistory.slice(-count);

        // Union of all field labels across selected entries, in first-seen order
        const fieldLabels = [];
        selectedEntries.forEach((entry) => {
          Object.keys(entry.fields).forEach((label) => {
            if (!fieldLabels.includes(label)) fieldLabels.push(label);
          });
        });

        const header = ['Timestamp', ...fieldLabels];
        const dataRows = selectedEntries.map((entry) => [
          entry.timestamp,
          ...fieldLabels.map((label) => (entry.fields[label] !== undefined ? entry.fields[label] : ''))
        ]);

        const sheetData = [
          ['Worker ID', 'SV-001'],
          ['Vest ID', 'VEST-001'],
          ['Exported At', new Date().toLocaleString()],
          ['Records Exported', String(selectedEntries.length)],
          [],
          header,
          ...dataRows
        ];

        const worksheet = XLSX.utils.aoa_to_sheet(sheetData);
        worksheet['!cols'] = [{ wch: 20 }, ...fieldLabels.map(() => ({ wch: 22 }))];

        const workbook = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(workbook, worksheet, 'Worker1 History');

        const dateStamp = new Date().toISOString().replace(/[:.]/g, '-');
        XLSX.writeFile(workbook, `worker1_history_${dateStamp}.xlsx`);
      } catch (err) {
        console.error('[Export] Excel generation FAILED:', err.message);
        alert('এক্সপোর্ট তৈরি করতে ব্যর্থ হয়েছে:\n' + err.message);
      } finally {
        exportBtn.innerHTML = originalHtml;
        exportBtn.disabled = false;
      }
    }

    const menuBtn = document.getElementById('menuBtn');
    const sidebar = document.getElementById('sidebar');
    menuBtn.addEventListener('click', (e) => { e.stopPropagation(); sidebar.classList.toggle('open'); });
    document.addEventListener('click', (e) => {
      if (sidebar.classList.contains('open') && !sidebar.contains(e.target) && e.target !== menuBtn) sidebar.classList.remove('open');
    });

    setInterval(() => { document.getElementById('liveClock').innerText = new Date().toLocaleTimeString(); }, 1000);

    const googleSatUrl = 'https://mt1.google.com/vt/lyrs=s,h&x={x}&y={y}&z={z}';

    // FIX: ONLY the small LIVE SATELLITE TRACKING map.
    // Google satellite remains unchanged; multiple Google tile servers prevent
    // part of the small card from staying white when one tile request fails.
    const map = L.map('map', {
      zoomControl: false,
      dragging: true,
      tap: true,
      fadeAnimation: false,
      zoomAnimation: false,
      markerZoomAnimation: false,
      trackResize: true
    }).setView([22.3569, 91.7832], 16);

    const smallGoogleSatellite = L.tileLayer(
      'https://{s}.google.com/vt/lyrs=s,h&x={x}&y={y}&z={z}',
      {
        subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
        maxZoom: 20,
        maxNativeZoom: 20,
        attribution: '&copy; Google Maps',
        keepBuffer: 8,
        updateWhenIdle: false,
        updateWhenZooming: false
      }
    ).addTo(map);

    let smallMapRetryTimer = null;
    smallGoogleSatellite.on('tileerror', () => {
      clearTimeout(smallMapRetryTimer);
      smallMapRetryTimer = setTimeout(() => {
        map.invalidateSize({ pan: false, animate: false });
        smallGoogleSatellite.redraw();
      }, 250);
    });

    const refreshSmallTrackingMap = () => {
      map.invalidateSize({ pan: false, animate: false });
      smallGoogleSatellite.redraw();
    };

    map.whenReady(() => {
      [0, 100, 300, 700, 1200].forEach((delay) => {
        setTimeout(refreshSmallTrackingMap, delay);
      });
    });

    const smallMapCard = document.querySelector('.map-wrapper');
    if (smallMapCard && window.ResizeObserver) {
      let resizeTimer = null;
      new ResizeObserver(() => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(refreshSmallTrackingMap, 50);
      }).observe(smallMapCard);
    }

    let fullMap = null;
    let fullWorkerMarker = null;
    let currentCoords = [22.3569, 91.7832];

    function openFullMap() {
      const modal = document.getElementById('mapModal');
      modal.classList.add('active');

      if (!fullMap) {
        fullMap = L.map('fullMap', { zoomControl: true }).setView(currentCoords, 18);
        L.tileLayer(googleSatUrl, { maxZoom: 20, attribution: '&copy; Google Maps' }).addTo(fullMap);
        fullWorkerMarker = L.marker(currentCoords).addTo(fullMap).bindPopup("Worker 01").openPopup();
      } else {
        fullMap.setView(currentCoords, 18);
        if (fullWorkerMarker) fullWorkerMarker.setLatLng(currentCoords);
      }
      setTimeout(() => { fullMap.invalidateSize(); }, 300);
    }

    function closeFullMap() {
      document.getElementById('mapModal').classList.remove('active');
    }

    let workerMarker = null;

    const sparklineInstances = {};
    function createSparkline(id, color, bgGradientColor) {
      const ctx = document.getElementById(id).getContext('2d');
      const gradient = ctx.createLinearGradient(0, 0, 0, 40);
      gradient.addColorStop(0, bgGradientColor);
      gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');

      sparklineInstances[id] = new Chart(ctx, {
        type: 'line',
        data: { labels: [], datasets: [{ data: [], borderColor: color, borderWidth: 1.5, pointRadius: 0, fill: true, backgroundColor: gradient, tension: 0.4 }] },
        options: { plugins: { legend: { display: false } }, scales: { x: { display: false }, y: { display: false } }, responsive: true, maintainAspectRatio: false }
      });
    }

    createSparkline('tempSpark', '#ff3366', 'rgba(255, 51, 102, 0.35)');
    createSparkline('humSpark', '#00f2ff', 'rgba(0, 242, 255, 0.35)');
    createSparkline('presSpark', '#a855f7', 'rgba(168, 85, 247, 0.35)');

    function updateSparkline(id, val) {
      const chart = sparklineInstances[id];
      if (!chart) return;
      chart.data.labels.push('');
      chart.data.datasets[0].data.push(val);
      if (chart.data.labels.length > 10) {
        chart.data.labels.shift();
        chart.data.datasets[0].data.shift();
      }
      chart.update();
    }

    function createLiveChart(canvasId, label, lineColor, fillColor) {
      const ctx = document.getElementById(canvasId).getContext('2d');
      return new Chart(ctx, {
        type: 'line',
        data: {
          labels: [],
          datasets: [{ label, data: [], borderColor: lineColor, backgroundColor: fillColor, fill: true, tension: 0.3 }]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } } },
            y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } } }
          }
        }
      });
    }

    const mainChart = createLiveChart('mainChart', 'Temp (°C)', '#ff3366', 'rgba(255,51,102,0.1)');
    const humChart = createLiveChart('humChart', 'Humidity (%)', '#00f2ff', 'rgba(0,242,255,0.1)');
    const presChart = createLiveChart('presChart', 'Pressure (hPa)', '#a855f7', 'rgba(168,85,247,0.1)');

    function pushLiveChartPoint(chart, label, val) {
      chart.data.labels.push(label);
      chart.data.datasets[0].data.push(val);
      if (chart.data.labels.length > 10) {
        chart.data.labels.shift();
        chart.data.datasets[0].data.shift();
      }
      chart.update();
    }

    const firebaseConfig = {
      apiKey: "AIzaSyDJl0vyF4mLdMVfIll9bdDXgA9GB0jc7RY",
      authDomain: "worker-safety-vest-92b97.firebaseapp.com",
      databaseURL: "https://worker-safety-vest-92b97-default-rtdb.firebaseio.com",
      projectId: "worker-safety-vest-92b97",
      storageBucket: "worker-safety-vest-92b97.firebasestorage.app",
      messagingSenderId: "734334381393",
      appId: "1:734334381393:web:ebfe2b722734068289b770"
    };

    if (!firebase.apps.length) firebase.initializeApp(firebaseConfig);

    // ===================== iPhone-style unlock reveal =====================
    // Mimics the iOS home-screen wake animation: every tile starts zoomed in
    // (scaled up around the centre of the screen), blurred and transparent,
    // then settles into place with a tiny spring. Tiles are staggered from
    // top-left to bottom-right so it ripples like the icons do on an iPhone.
    const iosUnlock = { bootDone: false, waiting: false };

    function playIosUnlock() {
      const main = document.getElementById('mainContent');
      if (!main || typeof main.animate !== 'function') return;
      if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

      const vw = window.innerWidth, vh = window.innerHeight;
      const nodes = main.querySelectorAll(
        '#mainContent > header, #mainContent .card, #mainContent .sensor-card-v2, #mainContent > footer'
      );

      // read phase (layout) first, then write phase
      const items = [];
      nodes.forEach((el) => {
        const r = el.getBoundingClientRect();
        if (!r.width || !r.height || r.bottom < 0 || r.top > vh) return; // only what is on screen
        items.push({ el, r });
      });
      if (!items.length) return;

      main.style.overflow = 'hidden'; // tiles fly in from beyond the edges without adding scroll
      main.classList.add('ios-anim');
      let finished = 0;
      const finishOne = () => {
        if (++finished === items.length) { main.style.overflow = ''; main.classList.remove('ios-anim'); }
      };

      items.forEach(({ el, r }) => {
        const clamp01 = (n) => Math.min(1, Math.max(0, n));
        // small base delay lets the freshly-shown dashboard finish its first
        // layout / chart sizing before the motion starts (avoids a stutter)
        const delay = 140 + Math.round(clamp01(r.top / vh) * 260 + clamp01(r.left / vw) * 90);
        // zoom around the screen centre (like the iPhone), not around each tile
        el.style.transformOrigin = (vw / 2 - r.left) + 'px ' + (vh / 2 - r.top) + 'px';

        const anim = el.animate([
          { opacity: 0, transform: 'scale(1.9)',  easing: 'cubic-bezier(.16,.84,.3,1)' },
          { opacity: 1, transform: 'scale(.985)', offset: 0.7, easing: 'cubic-bezier(.3,0,.3,1)' },
          { opacity: 1, transform: 'scale(1)' }
        ], { duration: 900, delay, fill: 'backwards', easing: 'linear' });

        const cleanup = () => { el.style.transformOrigin = ''; finishOne(); };
        anim.onfinish = cleanup;
        anim.oncancel = cleanup;
      });
    }

    // Page refresh with an existing session: wait until the boot loader has
    // faded before playing, otherwise the animation would run unseen behind it.
    function iosUnlockAfterBoot() {
      if (iosUnlock.bootDone) playIosUnlock();
      else iosUnlock.waiting = true;
    }

    // ===================== Authentication =====================
    const auth = firebase.auth();
    // Persist the login only for the current browser session — refreshing
    // the page keeps the user logged in, but closing the tab/browser
    // clears it, so a fresh visit requires logging in again.
    auth.setPersistence(firebase.auth.Auth.Persistence.SESSION).catch(() => {});
    const loginScreenEl = document.getElementById('loginScreen');
    const loginFormEl = document.getElementById('loginForm');
    const loginEmailEl = document.getElementById('loginEmail');
    const loginPasswordEl = document.getElementById('loginPassword');
    const loginSubmitBtnEl = document.getElementById('loginSubmitBtn');
    const loginErrorEl = document.getElementById('loginError');
    const loginErrorTextEl = document.getElementById('loginErrorText');
    const logoutUserLabelEl = document.getElementById('logoutUserLabel');

    function showLoginError(message) {
      loginErrorTextEl.textContent = message;
      loginErrorEl.classList.add('show');
    }
    function hideLoginError() {
      loginErrorEl.classList.remove('show');
    }
    function friendlyAuthError(err) {
      switch (err.code) {
        case 'auth/invalid-email': return 'That email address looks invalid.';
        case 'auth/user-disabled': return 'This account has been disabled.';
        case 'auth/user-not-found': return 'No account found with that email.';
        case 'auth/wrong-password': return 'Incorrect password. Try again.';
        case 'auth/invalid-credential': return 'Incorrect email or password.';
        case 'auth/too-many-requests': return 'Too many attempts. Try again later.';
        case 'auth/network-request-failed': return 'Network error — check your connection.';
        default: return 'Login failed. Please try again.';
      }
    }

    if (loginFormEl) {
      loginFormEl.addEventListener('submit', (e) => {
        e.preventDefault();
        hideLoginError();
        const email = loginEmailEl.value.trim();
        const password = loginPasswordEl.value;
        if (!email || !password) return;

        loginSubmitBtnEl.disabled = true;
        loginSubmitBtnEl.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> LOGGING IN...';

        auth.signInWithEmailAndPassword(email, password)
          .catch((err) => {
            showLoginError(friendlyAuthError(err));
          })
          .finally(() => {
            loginSubmitBtnEl.disabled = false;
            loginSubmitBtnEl.innerHTML = '<i class="fa-solid fa-right-to-bracket"></i> LOGIN';
          });
      });
    }

    function handleLogout() {
      auth.signOut();
    }

    // Premium 3-second post-login loader: reads this page's own source code
    // and displays it line-by-line before revealing the dashboard.
    const authCodeLoaderEl = document.getElementById('authCodeLoader');
    const authCodeWindowEl = document.getElementById('authCodeWindow');
    const authLoaderFillEl = document.getElementById('authLoaderFill');
    const authLoaderPercentEl = document.getElementById('authLoaderPercent');
    const authLoaderTextEl = document.getElementById('authLoaderText');
    const authLoaderStatusEl = document.getElementById('authLoaderStatus');
    let postLoginLoaderRunning = false;
    let authStateInitialized = false;

    async function getCurrentPageSourceLines() {
      try {
        const response = await fetch(window.location.href, { cache: 'no-store' });
        if (response.ok) {
          const source = await response.text();
          return source.split(/\r?\n/);
        }
      } catch (_) {}
      return [
        '<!DOCTYPE html>',
        '<html lang="en">',
        '<head>',
        '  <title>SafeGuard Monitoring System</title>',
        '</head>',
        '<body>',
        '  <!-- Initializing command interface -->',
        '  const firebaseConfig = { /* secure configuration */ };',
        '  auth.signInWithEmailAndPassword(email, password);',
        '  // Loading dashboard modules...',
        '</body>',
        '</html>'
      ];
    }

    async function runPostLoginLoader() {
      if (postLoginLoaderRunning || !authCodeLoaderEl) return;
      postLoginLoaderRunning = true;

      authCodeWindowEl.innerHTML = '';
      authLoaderFillEl.style.width = '0%';
      authLoaderPercentEl.textContent = '0%';
      authLoaderTextEl.textContent = 'LOADING SOURCE CODE...';
      authLoaderStatusEl.textContent = 'READING INTERFACE...';
      authCodeLoaderEl.classList.remove('fade-out');
      authCodeLoaderEl.classList.add('show');

      const lines = await getCurrentPageSourceLines();
      const totalDuration = 3000;
      const startedAt = performance.now();
      const visibleLineCount = Math.min(Math.max(42, Math.floor(lines.length / 7)), 120);
      const step = Math.max(1, Math.ceil(lines.length / visibleLineCount));
      const selectedLines = [];
      for (let i = 0; i < lines.length && selectedLines.length < visibleLineCount; i += step) {
        selectedLines.push({ no: i + 1, text: lines[i] || ' ' });
      }
      if (!selectedLines.length) selectedLines.push({ no: 1, text: 'Loading SafeGuard interface...' });

      let index = 0;
      let lastPercent = -1;
      let lastStage = -1;
      let finished = false;

      // Runs the "unlock" hand-off. The dashboard is shown first and given two
      // frames to lay out / paint, so the fade + iPhone-style reveal start on a
      // clean frame instead of sharing it with the heavy first layout.
      const finalize = () => {
        if (finished) return;
        finished = true;
        authLoaderFillEl.style.width = '100%';
        authLoaderPercentEl.textContent = '100%';
        authLoaderTextEl.textContent = 'DASHBOARD READY';
        authLoaderStatusEl.textContent = 'ACCESS GRANTED';

        setTimeout(() => {
          document.body.classList.add('authed');
          loginScreenEl.classList.add('hidden-screen');
          loginFormEl.reset();
          hideLoginError();

          let started = false;
          const go = () => {
            if (started) return;
            started = true;
            authCodeLoaderEl.classList.add('fade-out');
            playIosUnlock(); // dashboard opens like an iPhone being unlocked

            setTimeout(() => {
              authCodeLoaderEl.classList.remove('show', 'fade-out');
              postLoginLoaderRunning = false;
            }, 550);
          };
          requestAnimationFrame(() => requestAnimationFrame(go));
          setTimeout(go, 120); // safety net if the tab is in the background
        }, 260);
      };

      // Driven by requestAnimationFrame so every update lands on a display
      // refresh (60 / 90 / 120 / 144 Hz) instead of a fixed 40 ms timer (25 fps).
      const tick = () => {
        if (finished) return;
        const elapsed = Math.max(0, performance.now() - startedAt);
        const progress = Math.min(100, (elapsed / totalDuration) * 100);

        // progress bar moves every frame (sub-pixel smooth)
        authLoaderFillEl.style.width = progress.toFixed(2) + '%';

        const pct = Math.round(progress);
        if (pct !== lastPercent) {
          lastPercent = pct;
          authLoaderPercentEl.textContent = pct + '%';
        }

        const targetIndex = Math.min(
          selectedLines.length,
          Math.ceil((elapsed / totalDuration) * selectedLines.length)
        );

        // batch all new rows into one DOM write + one scroll per frame
        if (index < targetIndex) {
          const frag = document.createDocumentFragment();
          while (index < targetIndex) {
            const line = selectedLines[index];
            const row = document.createElement('div');
            row.className = 'auth-code-line active';
            row.innerHTML = '<span class="line-no">' + line.no + '</span><span class="line-code"></span>';
            row.querySelector('.line-code').textContent = line.text;
            frag.appendChild(row);
            index++;
          }
          authCodeWindowEl.appendChild(frag);
          authCodeWindowEl.scrollTop = authCodeWindowEl.scrollHeight;
        }

        const stage = progress < 34 ? 0 : progress < 72 ? 1 : progress < 100 ? 2 : 3;
        if (stage !== lastStage) {
          lastStage = stage;
          if (stage === 0) {
            authLoaderTextEl.textContent = 'READING SYSTEM SOURCE...';
            authLoaderStatusEl.textContent = 'ANALYZING CODE...';
          } else if (stage === 1) {
            authLoaderTextEl.textContent = 'LOADING DASHBOARD MODULES...';
            authLoaderStatusEl.textContent = 'COMPILING INTERFACE...';
          } else if (stage === 2) {
            authLoaderTextEl.textContent = 'FINALIZING SECURE SESSION...';
            authLoaderStatusEl.textContent = 'SYSTEM ALMOST READY...';
          }
        }

        if (elapsed >= totalDuration) {
          finalize();
        } else {
          requestAnimationFrame(tick);
        }
      };

      requestAnimationFrame(tick);
      // rAF pauses in background tabs — make sure the login can still finish.
      setTimeout(finalize, totalDuration + 1200);
    }

    auth.onAuthStateChanged((user) => {
      if (user) {
        if (logoutUserLabelEl) {
          logoutUserLabelEl.textContent = user.email ? user.email.split('@')[0] : 'Logout';
        }
        hideLoginError();

        // Initial auth check after page refresh keeps the normal dashboard flow.
        // A newly submitted login gets the premium 3-second loader.
        if (authStateInitialized && !document.body.classList.contains('authed')) {
          runPostLoginLoader();
        } else {
          const wasAuthed = document.body.classList.contains('authed');
          document.body.classList.add('authed');
          loginScreenEl.classList.add('hidden-screen');
          if (!authStateInitialized && !wasAuthed) iosUnlockAfterBoot();
        }
      } else {
        postLoginLoaderRunning = false;
        document.body.classList.remove('authed');
        loginScreenEl.classList.remove('hidden-screen');
        if (authCodeLoaderEl) authCodeLoaderEl.classList.remove('show', 'fade-out');
      }
      authStateInitialized = true;
    });

    // Auto Reconnection & Long Polling
    const db = firebase.database();
    
    let isConnected = false;

    // ===================== Offline / Stale-Data Detection =====================
    // Firebase RTDB always returns the LAST value written to '/worker1' —
    // it never tells us "nothing new has arrived". So if the transmitter
    // stops sending (or the receiver is off), Firebase just keeps handing
    // back the same old snapshot forever, and the dashboard would otherwise
    // keep showing it as if it were live. To fix that we track, ourselves,
    // when the data last actually CHANGED, and if too much time passes
    // without a genuine change, we treat the device as offline and clear
    // the dashboard instead of leaving stale readings on screen.
    const STALE_TIMEOUT_MS = 6000; // no fresh write for 6s => considered offline
    let lastUpdateTime = Date.now();
    let lastRawDataStr = null;
    let isOfflineState = false;

    // Resets the whole dashboard to a clear "no live data" state.
    function goOffline() {
      if (isOfflineState) return; // already showing offline state
      isOfflineState = true;
      isConnected = false;

      const connPill = document.getElementById('connPill');
      connPill.classList.remove('connected');
      connPill.innerHTML = '<i class="fa-solid fa-circle" style="font-size: 6px;"></i> OFFLINE';

      const sysIcon = document.getElementById('sysIcon');
      sysIcon.classList.remove('active');
      sysIcon.innerHTML = '<i class="fa-solid fa-plug-circle-xmark"></i>';

      const sysStatusText = document.getElementById('sysStatusText');
      sysStatusText.classList.remove('active');
      sysStatusText.innerText = 'SYSTEM OFFLINE';
      document.getElementById('sysSubText').innerText = 'NO DATA STREAM RECEIVED';

      document.getElementById('workerStatus').innerText = 'OFFLINE';
      document.getElementById('workerStatus').style.color = 'var(--red)';

      ['stEsp32', 'stGps', 'stMotion', 'stTemp', 'stNet'].forEach((id) => {
        const el = document.getElementById(id);
        el.innerText = 'Disconnected';
        el.style.color = 'var(--red)';
      });
      document.getElementById('rssiVal').innerText = '-- dBm';

      document.getElementById('onlineWorkerCount').innerText = '0 / 20';
      document.getElementById('onlineWorkerBar').style.width = '0%';

      // Reset every sensor readout so no stale value lingers on screen
      document.getElementById('tempVal').innerText = '--';
      document.getElementById('tempStatus').innerText = 'No Data';
      document.getElementById('tempStatus').style.color = 'var(--text-muted)';

      document.getElementById('humVal').innerText = '--';
      document.getElementById('humStatus').innerText = 'No Data';
      document.getElementById('humStatus').style.color = 'var(--text-muted)';

      document.getElementById('presVal').innerText = '--';
      document.getElementById('presStatus').innerText = 'No Data';
      document.getElementById('presStatus').style.color = 'var(--text-muted)';

      document.getElementById('motionStateVal').innerText = '--';
      document.getElementById('motionIcon').className = 'fa-solid fa-person';

      document.getElementById('fallAlertStatus').innerText = '--';
      document.getElementById('fallAlertStatus').style.color = 'var(--text-muted)';
      document.getElementById('fallBadge').className = 'badge badge-safe';
      document.getElementById('fallBadge').innerText = '--';

      document.getElementById('sosAlertStatus').innerText = '--';
      document.getElementById('sosAlertStatus').style.color = 'var(--text-muted)';
      document.getElementById('sosBadge').className = 'badge badge-safe';
      document.getElementById('sosBadge').innerText = '--';

      document.getElementById('lastSyncTime').innerText = '--:--:--';

      // Clear Active Alerts and show an explicit "no live data" notice
      document.getElementById('activeAlertCount').innerText = '0';
      document.getElementById('alertBox').innerHTML =
        '<div style="font-size: 10px; color: var(--amber); text-align: center; padding: 15px 0;">' +
        '<i class="fa-solid fa-plug-circle-xmark"></i> No live data — device offline</div>';

      // Log the disconnect event
      const eventTable = document.getElementById('eventLogTable');
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${new Date().toLocaleTimeString()}</td>
        <td>Connection Lost</td>
        <td><span class="badge badge-alert">OFFLINE</span></td>
        <td>No data received</td>
      `;
      eventTable.insertBefore(row, eventTable.firstChild);
      if (eventTable.children.length > 5) eventTable.removeChild(eventTable.lastChild);
    }

    // Gatekeeper for every Firebase read (both the realtime listener and
    // the polling fallback): only forwards data to processIncomingData()
    // when it has genuinely changed, and is the only place lastUpdateTime
    // gets refreshed. This is what makes offline detection reliable —
    // re-reading the same old snapshot no longer resets the staleness clock.
    function handleFirebaseSnapshot(data) {
      if (!data) return;
      const rawStr = JSON.stringify(data);
      if (rawStr === lastRawDataStr) return; // nothing new, ignore
      lastRawDataStr = rawStr;
      lastUpdateTime = Date.now();
      isOfflineState = false;
      processIncomingData(data);
    }

    // Renders the Active Alerts card at the bottom of the dashboard with
    // large, clearly visible alert entries — one per active hazard.
    function renderActiveAlerts(alerts) {
      const alertBox = document.getElementById('alertBox');
      const countEl = document.getElementById('activeAlertCount');
      countEl.innerText = alerts.length;

      if (alerts.length === 0) {
        alertBox.innerHTML = '<div style="font-size: 10px; color: var(--text-muted); text-align: center; padding: 15px 0;">No active hazardous alerts</div>';
        return;
      }

      alertBox.innerHTML = alerts.map((a) => `
        <div class="active-alert-item">
          <i class="fa-solid ${a.icon} active-alert-icon"></i>
          <div class="active-alert-text">
            <div class="active-alert-title">${a.title}</div>
            <div class="active-alert-detail">${a.detail}</div>
          </div>
        </div>
      `).join('');
    }

    // CORE UI UPDATE FUNCTION
    function processIncomingData(data) {
      if (!data) return;

      if (!isConnected) {
        isConnected = true;
        const connPill = document.getElementById('connPill');
        connPill.classList.add('connected');
        connPill.innerHTML = '<i class="fa-solid fa-circle" style="font-size: 6px;"></i> STREAMING';

        const sysIcon = document.getElementById('sysIcon');
        sysIcon.classList.add('active');
        sysIcon.innerHTML = '<i class="fa-solid fa-shield-halved"></i>';

        document.getElementById('sysStatusText').classList.add('active');
        document.getElementById('sysStatusText').innerText = 'SYSTEM NORMAL';
        document.getElementById('sysSubText').innerText = 'REALTIME STREAMING';
        document.getElementById('workerStatus').innerText = 'ONLINE';
        document.getElementById('workerStatus').style.color = 'var(--green)';

        document.getElementById('stEsp32').innerText = 'Connected'; document.getElementById('stEsp32').style.color = 'var(--green)';
        document.getElementById('stGps').innerText = 'Connected'; document.getElementById('stGps').style.color = 'var(--green)';
        document.getElementById('stMotion').innerText = 'Connected'; document.getElementById('stMotion').style.color = 'var(--green)';
        document.getElementById('stTemp').innerText = 'Connected'; document.getElementById('stTemp').style.color = 'var(--green)';
        document.getElementById('stNet').innerText = 'Connected'; document.getElementById('stNet').style.color = 'var(--green)';
        document.getElementById('rssiVal').innerText = '-60 dBm';

        document.getElementById('onlineWorkerCount').innerText = '1 / 20';
        document.getElementById('onlineWorkerBar').style.width = '5%';
        document.getElementById('eventLogTable').innerHTML = '';
      }

      const timeStr = new Date().toLocaleTimeString();
      document.getElementById('lastSyncTime').innerText = timeStr;

      let isHazard = false;
      const activeAlertsList = [];

      if (data.environment) {
        if (data.environment.temperature !== undefined) {
          const val = Number(data.environment.temperature);
          document.getElementById('tempVal').innerText = val.toFixed(1);
          if (val > 45 || val < 15) {
            document.getElementById('tempStatus').innerText = 'High Temp';
            document.getElementById('tempStatus').style.color = 'var(--red)';
            isHazard = true;
            activeAlertsList.push({
              icon: 'fa-temperature-high',
              title: val > 45 ? 'High Temperature' : 'Low Temperature',
              detail: `Reading is ${val.toFixed(1)}°C — outside the safe range of 15°C – 45°C`
            });
          } else {
            document.getElementById('tempStatus').innerText = 'Normal';
            document.getElementById('tempStatus').style.color = 'var(--green)';
          }
          updateSparkline('tempSpark', val);
          pushLiveChartPoint(mainChart, timeStr, val);
        }

        if (data.environment.humidity !== undefined) {
          const humVal = Number(data.environment.humidity);
          document.getElementById('humVal').innerText = humVal.toFixed(1);
          if (humVal > 90 || humVal < 20) {
            document.getElementById('humStatus').innerText = humVal > 90 ? 'High Humidity' : 'Low Humidity';
            document.getElementById('humStatus').style.color = 'var(--red)';
            isHazard = true;
            activeAlertsList.push({
              icon: 'fa-droplet',
              title: humVal > 90 ? 'High Humidity' : 'Low Humidity',
              detail: `Reading is ${humVal.toFixed(1)}% — outside the safe range of 20% – 90%`
            });
          } else {
            document.getElementById('humStatus').innerText = 'Normal';
            document.getElementById('humStatus').style.color = 'var(--green)';
          }
          updateSparkline('humSpark', humVal);
          pushLiveChartPoint(humChart, timeStr, humVal);
        }

        if (data.environment.pressure !== undefined) {
          const presVal = Number(data.environment.pressure);
          document.getElementById('presVal').innerText = presVal.toFixed(0);
          if (presVal > 1050 || presVal < 950) {
            document.getElementById('presStatus').innerText = presVal > 1050 ? 'High Pressure' : 'Low Pressure';
            document.getElementById('presStatus').style.color = 'var(--red)';
            isHazard = true;
            activeAlertsList.push({
              icon: 'fa-gauge-high',
              title: presVal > 1050 ? 'High Pressure' : 'Low Pressure',
              detail: `Reading is ${presVal.toFixed(0)} hPa — outside the safe range of 950 – 1050 hPa`
            });
          } else {
            document.getElementById('presStatus').innerText = 'Normal';
            document.getElementById('presStatus').style.color = 'var(--green)';
          }
          updateSparkline('presSpark', presVal);
          pushLiveChartPoint(presChart, timeStr, presVal);
        }
      }

      if (data.status && data.status.motion_state) {
        const state = String(data.status.motion_state).trim().toUpperCase();
        document.getElementById('motionStateVal').innerText = state;
        const iconEl = document.getElementById('motionIcon');
        if (state === "WALKING") iconEl.className = "fa-solid fa-person-walking";
        else if (state === "RUNNING") iconEl.className = "fa-solid fa-person-running";
        else if (state === "LYING") iconEl.className = "fa-solid fa-bed";
        else if (state === "FALL") {
          iconEl.className = "fa-solid fa-triangle-exclamation";
          isHazard = true;
          activeAlertsList.push({
            icon: 'fa-person-falling',
            title: 'Fall Detected (Motion)',
            detail: 'The motion sensor reports the worker is in a FALL state'
          });
        }
        else iconEl.className = "fa-solid fa-person"; // STANDING
      }

      if (data.alerts) {
        const fallBadge = document.getElementById('fallBadge');
        const fallStatus = document.getElementById('fallAlertStatus');
        if (data.alerts.fall_detected) {
          fallStatus.innerText = "FALL DETECTED!";
          fallStatus.style.color = "var(--red)";
          fallBadge.className = "badge badge-alert";
          fallBadge.innerText = "FALL ALERT";
          isHazard = true;
          activeAlertsList.push({
            icon: 'fa-person-falling',
            title: 'Fall Detected',
            detail: 'The vest\'s fall-detection alert flag is active'
          });
        } else {
          fallStatus.innerText = "Motion Normal";
          fallStatus.style.color = "var(--green)";
          fallBadge.className = "badge badge-safe";
          fallBadge.innerText = "NO FALL";
        }

        const sosBadge = document.getElementById('sosBadge');
        const sosStatus = document.getElementById('sosAlertStatus');
        if (data.alerts.sos_active) {
          sosStatus.innerText = "SOS Active!";
          sosStatus.style.color = "var(--red)";
          sosBadge.className = "badge badge-alert";
          sosBadge.innerText = "SOS ACTIVE";
          isHazard = true;
          activeAlertsList.push({
            icon: 'fa-triangle-exclamation',
            title: 'SOS Activated',
            detail: 'The worker has triggered the emergency SOS button'
          });
        } else {
          sosStatus.innerText = "SOS: Inactive";
          sosStatus.style.color = "var(--text-muted)";
          sosBadge.className = "badge badge-safe";
          sosBadge.innerText = "SOS NORMAL";
        }
      }

      if (data.gps && data.gps.latitude && data.gps.longitude) {
        const lat = Number(data.gps.latitude);
        const lng = Number(data.gps.longitude);
        if (!isNaN(lat) && !isNaN(lng) && lat !== 0 && lng !== 0) {
          currentCoords = [lat, lng];

          if (!workerMarker) workerMarker = L.marker(currentCoords).addTo(map);
          else workerMarker.setLatLng(currentCoords);
          map.panTo(currentCoords);

          if (fullMap && fullWorkerMarker) {
            fullWorkerMarker.setLatLng(currentCoords);
            fullMap.panTo(currentCoords);
          }
        }
      }

      if (isHazard) playEmergencySound();

      renderActiveAlerts(activeAlertsList);

      const eventTable = document.getElementById('eventLogTable');
      const newRow = document.createElement('tr');
      const tempText = data.environment ? data.environment.temperature : '--';
      const motionText = data.status ? data.status.motion_state : '--';
      newRow.innerHTML = `
        <td>${escapeHtml(timeStr)}</td>
        <td>${isHazard ? 'Hazard Alert' : 'Sync'}</td>
        <td><span class="badge ${isHazard ? 'badge-alert' : 'badge-safe'}">${isHazard ? 'ALERT' : 'SAFE'}</span></td>
        <td>${escapeHtml(motionText)} | ${escapeHtml(tempText)}°C</td>
      `;
      eventTable.insertBefore(newRow, eventTable.firstChild);
      if (eventTable.children.length > 5) eventTable.removeChild(eventTable.lastChild);

      // Save this reading into the rolling history buffer used by Export
      recordHistorySnapshot(data);
    }

    // 1. Primary Realtime Listener
    db.ref('/worker1').on('value', (snapshot) => {
      handleFirebaseSnapshot(snapshot.val());
    });

    // 2. Active Fallback Interval (Guarantees Realtime Updating without Manual Refresh)
    setInterval(() => {
      db.ref('/worker1').once('value').then((snapshot) => {
        if(snapshot.exists()) {
          handleFirebaseSnapshot(snapshot.val());
        }
      });
    }, 2000);

    // 3. Offline Watchdog — if no genuinely NEW data has arrived within
    // STALE_TIMEOUT_MS, the transmitter/receiver is treated as offline
    // and the dashboard is cleared instead of showing old readings.
    setInterval(() => {
      if (isConnected && !isOfflineState && (Date.now() - lastUpdateTime > STALE_TIMEOUT_MS)) {
        goOffline();
      }
    }, 1000);

    // ===================== Web SOS Toggle — REMOVED =====================
    // The dashboard-side SOS button (and its '/worker1/commands/web_sos' write)
    // has been removed because the buzzer trigger was not firing reliably.
    // The vest's own physical SOS button is untouched: its status is still read
    // from '/worker1/alerts/sos_active' and shown in the Motion State card
    // (#sosAlertStatus / #sosBadge) plus the alert log.
  

    // =========================================================
    // FUNCTIONAL SIDEBAR NAVIGATION
    // Existing HTML IDs/classes and application JavaScript remain
    // unchanged. This only connects the existing nav links to the
    // corresponding sections already present on the dashboard.
    // =========================================================
    (function initSidebarNavigation() {
      const navItems = Array.from(document.querySelectorAll('.nav-item'));
      const sidebarNav = document.querySelector('.nav-menu');

      if (!navItems.length || !sidebarNav) return;

      // Targets are existing elements; no new IDs/classes are introduced.
      const getNavigationTargets = () => {
        const workerInfo = document.querySelector('.worker-info-card');
        const sensorGrid = document.querySelector('.sensor-grid');
        const alertCard = document.querySelector('.alert-card');
        const mapWrapper = document.querySelector('.map-wrapper');
        const historyTable = document.getElementById('eventLogTable');

        return [
          document.getElementById('mainContent'),
          sensorGrid,
          workerInfo,
          alertCard,
          mapWrapper,
          historyTable ? historyTable.closest('.card') : null
        ];
      };

      const targets = getNavigationTargets();

      const setActiveNav = (index) => {
        navItems.forEach((item, i) => {
          item.classList.toggle('active', i === index);
        });
      };

      const scrollToTarget = (target, index) => {
        if (!target) return;

        setActiveNav(index);

        const headerOffset = window.innerWidth <= 768 ? 12 : 18;
        const targetTop = target.getBoundingClientRect().top + window.pageYOffset - headerOffset;

        window.scrollTo({
          top: Math.max(0, targetTop),
          behavior: 'smooth'
        });

        // Close the mobile drawer after a selection.
        if (window.innerWidth <= 768) {
          sidebar.classList.remove('open');
        }
      };

      navItems.forEach((item, index) => {
        const link = item.querySelector('a');
        if (!link) return;

        link.addEventListener('click', (event) => {
          event.preventDefault();
          event.stopPropagation();

          const target = targets[index];
          scrollToTarget(target, index);
        });
      });

      // Keep the selected navigation item synchronized with the
      // section currently visible while scrolling.
      const observerTargets = targets.filter(Boolean);

      if ('IntersectionObserver' in window && observerTargets.length) {
        const targetToIndex = new Map();
        targets.forEach((target, index) => {
          if (target) targetToIndex.set(target, index);
        });

        const observer = new IntersectionObserver((entries) => {
          const visible = entries
            .filter(entry => entry.isIntersecting)
            .sort((a, b) => b.intersectionRatio - a.intersectionRatio);

          if (visible.length) {
            const index = targetToIndex.get(visible[0].target);
            if (typeof index === 'number') setActiveNav(index);
          }
        }, {
          root: null,
          rootMargin: '-18% 0px -62% 0px',
          threshold: [0.05, 0.15, 0.3, 0.5]
        });

        observerTargets.forEach(target => observer.observe(target));
      }

      // Make the active item keyboard accessible without changing
      // the existing markup or classes.
      navItems.forEach((item, index) => {
        const link = item.querySelector('a');
        if (!link) return;

        link.addEventListener('keydown', (event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            scrollToTarget(targets[index], index);
          }
        });
      });
    })();

    // =========================================================
    // VISUAL-ONLY VALUE FEEDBACK (additive, non-invasive)
    // Purely observes existing DOM elements from the outside via
    // MutationObserver — does NOT call, wrap, or modify any of the
    // functions above (processIncomingData, handleFirebaseSnapshot,
    // goOffline, etc.). No IDs/classes are renamed; only the CSS
    // classes .val-flash and .still-waiting, defined in the stylesheet,
    // are toggled on top of the existing markup.
    // =========================================================
    (function initVisualValueFeedback() {
      // 1) Flash a value span briefly whenever its text actually changes.
      const flashIds = ['tempVal', 'humVal', 'presVal', 'motionStateVal'];
      flashIds.forEach((id) => {
        const el = document.getElementById(id);
        if (!el) return;
        let lastText = el.textContent;
        const obs = new MutationObserver(() => {
          if (el.textContent !== lastText) {
            lastText = el.textContent;
            el.classList.remove('val-flash');
            // Force reflow so the animation can restart on rapid updates.
            void el.offsetWidth;
            el.classList.add('val-flash');
          }
        });
        obs.observe(el, { characterData: true, childList: true, subtree: true });
      });

      // 2) Shimmer a sensor card / device row while it still shows a
      // placeholder value ("--" or "Waiting...") and remove the shimmer
      // the moment a real reading/status arrives.
      const waitingWatchIds = [
        'tempVal', 'humVal', 'presVal', 'motionStateVal',
        'stEsp32', 'stGps', 'stMotion', 'stTemp', 'stNet'
      ];
      const PLACEHOLDER_TEXTS = new Set(['--', 'Waiting...']);

      waitingWatchIds.forEach((id) => {
        const el = document.getElementById(id);
        if (!el) return;
        const container = el.closest('.sensor-card-v2') || el.closest('.device-item') || el;

        const syncWaitingState = () => {
          const isWaiting = PLACEHOLDER_TEXTS.has(el.textContent.trim());
          container.classList.toggle('still-waiting', isWaiting);
        };

        syncWaitingState();
        const obs = new MutationObserver(syncWaitingState);
        obs.observe(el, { characterData: true, childList: true, subtree: true });
      });
    })();

    // =========================================================
    // ALERT ESCALATION (additive, non-invasive)
    // Adds: desktop notifications when a new hazard alert appears,
    // a flashing browser-tab title while the tab is hidden and a
    // hazard is active, and a remembered Audio ON/OFF preference.
    //
    // This only OBSERVES the existing "Active Alerts" UI via
    // MutationObserver and reuses the existing toggleAudio() /
    // audioEnabled from the script above. It does not modify
    // processIncomingData, renderActiveAlerts, toggleAudio, or any
    // other existing function, and introduces no new IDs/classes
    // in the markup.
    // =========================================================
    (function initAlertEscalation() {
      const activeAlertCountEl = document.getElementById('activeAlertCount');
      const alertBoxEl = document.getElementById('alertBox');
      if (!activeAlertCountEl || !alertBoxEl) return;

      // ---- 1) Desktop / mobile notifications for newly appearing hazard alerts ----
      //
      // FIX: most browsers (Chrome/Edge in particular) silently ignore an
      // automatic Notification.requestPermission() call that isn't triggered
      // by a real user click — the prompt never appears and permission just
      // stays 'default' forever, so notifications randomly never arrive
      // depending on the browser/session. Instead of asking automatically on
      // load, we show a small "Enable Alerts" pill that asks on click, which
      // every browser honors reliably.
      //
      // A Service Worker is registered (sw.js) so notifications are shown via
      // registration.showNotification() instead of the plain Notification
      // constructor — this is required on Android Chrome and gives more
      // reliable delivery while the tab is backgrounded, plus support for
      // vibration and a proper app icon on the notification itself.
      let swRegistration = null;
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('sw.js')
          .then((reg) => {
            swRegistration = reg;
            // If a waiting worker is already sitting there (e.g. this tab
            // was open when a new version deployed), nudge it to activate.
            if (reg.waiting) reg.waiting.postMessage({ type: 'SKIP_WAITING' });
            reg.addEventListener('updatefound', () => {
              const installing = reg.installing;
              if (!installing) return;
              installing.addEventListener('statechange', () => {
                if (installing.state === 'installed' && navigator.serviceWorker.controller) {
                  // A new sw.js finished installing behind the currently
                  // active one — tell it to take over immediately.
                  installing.postMessage({ type: 'SKIP_WAITING' });
                }
              });
            });
          })
          .catch(() => { /* sw.js missing or blocked — falls back to plain Notification below */ });

        // The new service worker just took control of this page (fired once
        // it calls self.skipWaiting() + clients.claim()). Reload so every
        // tab picks up the new app-shell/code instead of running stale JS
        // against a newer server response.
        let hasReloadedForNewSW = false;
        navigator.serviceWorker.addEventListener('controllerchange', () => {
          if (hasReloadedForNewSW) return;
          hasReloadedForNewSW = true;
          window.location.reload();
        });
      }

      // --- Offline app-shell banner -------------------------------------
      // sw.js caches the app shell (this page, style.css, app.js, logo) so
      // that if the network drops entirely, the browser can still repaint
      // the last screen instead of showing a blank/broken page. This banner
      // just tells the user that's what's happening — the live vest data
      // itself still depends on Firebase and will show as OFFLINE via the
      // existing connPill logic above.
      function showOfflineShellBanner(show) {
        let el = document.getElementById('__offlineShellBanner');
        if (show) {
          if (el) return;
          el = document.createElement('div');
          el.id = '__offlineShellBanner';
          el.textContent = 'কানেকশন নেই — শেষ জানা অবস্থা দেখানো হচ্ছে';
          el.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:100001;background:#3a1d00;color:#ffcf7a;font:700 11px/1.4 "Inter",sans-serif;text-align:center;padding:6px 10px;border-bottom:1px solid rgba(255,207,122,.4);';
          document.body.prepend(el);
        } else if (el) {
          el.remove();
        }
      }
      window.addEventListener('offline', () => showOfflineShellBanner(true));
      window.addEventListener('online', () => showOfflineShellBanner(false));
      if (!navigator.onLine) showOfflineShellBanner(true);

      // --- Minimal XSS-safety helper --------------------------------------
      // Used anywhere a value that ultimately comes from the vest/Firebase
      // (sensor readings, motion state, worker-entered text, etc.) is placed
      // into innerHTML. Static, developer-written markup strings elsewhere
      // in this file don't need it — only text that could contain a stray
      // "<" from upstream data does.
      function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, (ch) => ({
          '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[ch]));
      }

      // --- FCM background push (fall/SOS alerts while the app is fully
      // closed, not just backgrounded) --------------------------------
      // Requires a Web Push "VAPID key" from Firebase Console > Project
      // Settings > Cloud Messaging > Web configuration. Paste it below —
      // until it's set this silently no-ops and only the existing
      // foreground/backgrounded-tab notifications work.
      const FCM_VAPID_KEY = 'PASTE_YOUR_FIREBASE_VAPID_KEY_HERE';
      function registerForPushNotifications() {
        if (!swRegistration || !FCM_VAPID_KEY || FCM_VAPID_KEY.indexOf('PASTE_') === 0) return;
        if (typeof firebase === 'undefined' || !firebase.messaging || !firebase.messaging.isSupported()) return;
        try {
          const messaging = firebase.messaging();
          messaging.getToken({ vapidKey: FCM_VAPID_KEY, serviceWorkerRegistration: swRegistration })
            .then((token) => {
              if (!token) return;
              // Stash the token in the DB so a Cloud Function can target
              // this device for fall/SOS pushes even when the app is closed.
              // (Sending the actual push from the server side is a separate
              // piece of work — this just makes the device reachable.)
              if (typeof database !== 'undefined' && database) {
                database.ref('fcmTokens/' + token).set({ updatedAt: Date.now() }).catch(() => {});
              }
            })
            .catch(() => { /* permission denied or token fetch failed — non-fatal */ });
        } catch (e) { /* firebase messaging not available in this build — non-fatal */ }
      }

      function showEnableNotifBtn() {
        if (document.getElementById('__enableNotifBtn')) return;
        const btn = document.createElement('button');
        btn.id = '__enableNotifBtn';
        btn.type = 'button';
        btn.textContent = '🔔 Enable Alert Notifications';
        btn.style.cssText = 'position:fixed;top:14px;right:14px;z-index:100000;background:rgba(0,242,255,.15);border:1px solid rgba(0,242,255,.4);color:#eafcff;font:700 11px/1.3 "Inter",sans-serif;padding:8px 14px;border-radius:20px;cursor:pointer;box-shadow:0 6px 20px rgba(0,0,0,.35);';
        btn.addEventListener('click', () => {
          Notification.requestPermission().then((perm) => {
            if (perm === 'granted') { btn.remove(); registerForPushNotifications(); }
            else if (perm === 'denied') btn.remove();
          }).catch(() => {});
        });
        document.body.appendChild(btn);
      }
      if ('Notification' in window) {
        if (Notification.permission === 'default') {
          showEnableNotifBtn();
        }
      }

      const notifLogoEl = document.querySelector('.brand-icon img');
      const notifLogoSrc = notifLogoEl ? notifLogoEl.src : undefined;

      function fireNotification(title, options) {
        if (swRegistration && swRegistration.showNotification) {
          swRegistration.showNotification(title, options).catch(() => {
            try { new Notification(title, options); } catch (e) { /* fail silently */ }
          });
        } else {
          try { new Notification(title, options); } catch (e) { /* fail silently */ }
        }
      }

      let lastAlertTitles = new Set();
      function notifyNewAlerts() {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;
        const titleEls = alertBoxEl.querySelectorAll('.active-alert-title');
        const currentTitles = new Set(Array.from(titleEls).map((el) => el.textContent));
        currentTitles.forEach((title) => {
          if (!lastAlertTitles.has(title)) {
            const isCritical = /fall|sos/i.test(title);
            fireNotification('⚠️ SafeGuard Alert', {
              body: title,
              tag: 'safety-vest-' + title, // collapses repeat notifications for the same ongoing alert
              renotify: true, // FIX: without this, a repeat alert with the same tag/title
                               // silently replaces the old popup with no new pop-up/sound at all,
                               // which looks exactly like "notification didn't come"
              icon: notifLogoSrc,
              badge: notifLogoSrc,
              vibrate: [200, 100, 200, 100, 200],
              requireInteraction: isCritical // Fall/SOS notifications stay on screen until dismissed
            });
          }
        });
        lastAlertTitles = currentTitles;
      }

      // ---- 2) Tab title flashing while the tab is hidden and a hazard is active ----
      const originalTitle = document.title;
      let titleFlashInterval = null;
      let flashOn = false;

      function startTitleFlash() {
        if (titleFlashInterval) return;
        titleFlashInterval = setInterval(() => {
          document.title = flashOn ? originalTitle : '⚠️ ALERT — SafeGuard';
          flashOn = !flashOn;
        }, 1000);
      }
      function stopTitleFlash() {
        if (titleFlashInterval) {
          clearInterval(titleFlashInterval);
          titleFlashInterval = null;
        }
        document.title = originalTitle;
      }
      function syncTitleFlash() {
        const hasAlerts = parseInt(activeAlertCountEl.textContent, 10) > 0;
        if (hasAlerts && document.hidden) startTitleFlash();
        else stopTitleFlash();
      }

      document.addEventListener('visibilitychange', syncTitleFlash);

      const alertObserver = new MutationObserver(() => {
        notifyNewAlerts();
        syncTitleFlash();
      });
      alertObserver.observe(activeAlertCountEl, { characterData: true, childList: true, subtree: true });
      alertObserver.observe(alertBoxEl, { childList: true, subtree: true });

      // ---- 3) Remember the Audio ON/OFF preference across page reloads ----
      // Reuses the existing toggleAudio()/audioEnabled declared earlier in
      // this same script; only adds persistence around them.
      try {
        const savedAudioPref = localStorage.getItem('vestAudioEnabled');
        if (savedAudioPref === 'true' && typeof toggleAudio === 'function' && !audioEnabled) {
          toggleAudio(); // replays the existing toggle so all its UI updates run normally
        }
      } catch (e) { /* localStorage may be unavailable, e.g. private browsing */ }

      const audioBtnEl = document.getElementById('audioBtn');
      if (audioBtnEl) {
        audioBtnEl.addEventListener('click', () => {
          // Runs after the button's own inline onclick has already
          // flipped audioEnabled, so this just persists the new value.
          setTimeout(() => {
            try { localStorage.setItem('vestAudioEnabled', String(audioEnabled)); } catch (e) {}
          }, 0);
        });
      }
    })();

    // =========================================================
    // ACCESSIBILITY: keyboard activation + aria-state sync
    // (additive) — makes the div/span/icon elements that act as
    // buttons (menuBtn, the map "Full View" triggers)
    // usable from the keyboard, and keeps their aria-expanded /
    // aria-pressed attributes in sync with the existing classes
    // toggled by the original functions. No existing function is
    // modified; this only reads DOM state and adds attributes.
    // =========================================================
    (function initAccessibilityHelpers() {
      // Enter/Space activates any element marked role="button" that
      // isn't a native <button>, by delegating to its own click handler.
      document.querySelectorAll('[role="button"]:not(button)').forEach((el) => {
        el.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            el.click();
          }
        });
      });

      // Keep aria-expanded on the hamburger in sync with the sidebar's
      // existing 'open' class (already toggled by the original code).
      const sidebarEl = document.getElementById('sidebar');
      const menuBtnEl = document.getElementById('menuBtn');
      if (sidebarEl && menuBtnEl) {
        const syncExpanded = () => {
          menuBtnEl.setAttribute('aria-expanded', sidebarEl.classList.contains('open') ? 'true' : 'false');
        };
        syncExpanded();
        new MutationObserver(syncExpanded).observe(sidebarEl, { attributes: true, attributeFilter: ['class'] });
      }

      // Keep aria-pressed on the Audio button in sync with
      // its existing 'active-sound' class.
      const audioBtnEl2 = document.getElementById('audioBtn');
      if (audioBtnEl2) {
        const syncAudio = () => audioBtnEl2.setAttribute('aria-pressed', audioBtnEl2.classList.contains('active-sound') ? 'true' : 'false');
        syncAudio();
        new MutationObserver(syncAudio).observe(audioBtnEl2, { attributes: true, attributeFilter: ['class'] });
      }
    })();

    // =========================================================
    // RESILIENCE: connection banner, GPS waiting hint, export toast
    // (additive) — all UI is created dynamically via JS so the
    // original markup stays untouched; all logic only observes
    // existing state (the '.info/connected' Firebase path, the
    // module-scope `workerMarker` variable, and the exportBtn's
    // disabled attribute) rather than editing existing functions.
    // =========================================================
    (function initResilienceHelpers() {
      // ---- Small reusable toast/banner system ----
      function ensureToastLayer() {
        let layer = document.getElementById('__toastLayer');
        if (!layer) {
          layer = document.createElement('div');
          layer.id = '__toastLayer';
          layer.style.cssText = 'position:fixed;left:50%;bottom:18px;transform:translateX(-50%);z-index:100000;display:flex;flex-direction:column;gap:8px;align-items:center;pointer-events:none;';
          document.body.appendChild(layer);
        }
        return layer;
      }
      function showToast(message, kind) {
        const layer = ensureToastLayer();
        const el = document.createElement('div');
        const bg = kind === 'error' ? 'rgba(255,51,102,.92)' : kind === 'warn' ? 'rgba(245,158,11,.92)' : 'rgba(16,185,129,.92)';
        el.textContent = message;
        el.style.cssText = `background:${bg};color:#04101c;font:700 12px/1.3 'Inter',sans-serif;padding:9px 16px;border-radius:10px;box-shadow:0 10px 28px rgba(0,0,0,.35);opacity:0;transform:translateY(8px);transition:opacity .25s ease, transform .25s ease;`;
        layer.appendChild(el);
        requestAnimationFrame(() => { el.style.opacity = '1'; el.style.transform = 'translateY(0)'; });
        setTimeout(() => {
          el.style.opacity = '0';
          el.style.transform = 'translateY(8px)';
          setTimeout(() => el.remove(), 300);
        }, 3200);
      }

      // ---- 1) Real connection-state banner via Firebase's built-in
      // '.info/connected' path, independent of the '/worker1' data ref ----
      if (typeof db !== 'undefined') {
        let firstConnectSeen = false;
        db.ref('.info/connected').on('value', (snap) => {
          const connected = snap.val() === true;
          if (!connected && firstConnectSeen) {
            showToast('⚠️ Realtime connection lost — retrying…', 'error');
          } else if (connected && firstConnectSeen) {
            showToast('✓ Reconnected to Firebase', 'ok');
          }
          if (connected) firstConnectSeen = true;
        });
      }

      // ---- 2) "Waiting for GPS" hint inside the map card until the
      // first valid coordinate arrives (existing `workerMarker` var) ----
      const mapWrapperEl = document.querySelector('.map-wrapper');
      if (mapWrapperEl && typeof workerMarker !== 'undefined') {
        const gpsHint = document.createElement('div');
        gpsHint.textContent = 'Waiting for GPS fix…';
        gpsHint.style.cssText = 'position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(3,8,16,.55);color:#8fa2b8;font:700 10px "Orbitron",sans-serif;letter-spacing:.5px;z-index:400;pointer-events:none;';
        mapWrapperEl.appendChild(gpsHint);
        const checkGps = setInterval(() => {
          if (typeof workerMarker !== 'undefined' && workerMarker) {
            gpsHint.remove();
            clearInterval(checkGps);
          }
        }, 1000);
      }

      // ---- 3) Export confirmation toast. exportWorkerData() runs fully
      // synchronously (including its try/catch/finally), and this
      // listener is registered after the button's own inline onclick,
      // so by the time this runs the export has already finished —
      // we just check `dataHistory` (declared earlier in this same
      // script) to report success vs. the "nothing to export" case. ----
      const exportBtnEl = document.getElementById('exportBtn');
      if (exportBtnEl) {
        exportBtnEl.addEventListener('click', () => {
          if (typeof dataHistory !== 'undefined' && dataHistory.length > 0) {
            showToast('✓ Excel file exported — check your downloads', 'ok');
          }
        });
      }
    })();

    // ===== Boot Loading Screen animation =====
    (function bootLoaderRun() {
      const loader = document.getElementById('bootLoader');
      const fill = document.getElementById('bootBarFill');
      const percentEl = document.getElementById('bootPercent');
      if (!loader || !fill || !percentEl) return;

      let progress = 0;
      let finished = false;

      function setProgress(p) {
        progress = Math.max(progress, Math.min(p, 100));
        fill.style.width = progress + '%';
        percentEl.textContent = Math.round(progress) + '%';
      }

      // Animate up to ~90% while the page/app finishes loading, then
      // jump to 100% and fade out once the window has fully loaded.
      const ticker = setInterval(() => {
        if (progress < 90) {
          setProgress(progress + Math.random() * 6 + 2);
        }
      }, 140);

      function finish() {
        if (finished) return;
        finished = true;
        clearInterval(ticker);
        setProgress(100);
        setTimeout(() => {
          loader.classList.add('hidden');
          iosUnlock.bootDone = true;
          if (iosUnlock.waiting) { iosUnlock.waiting = false; playIosUnlock(); }
          setTimeout(() => loader.remove(), 700);
        }, 350);
      }

      if (document.readyState === 'complete') {
        setTimeout(finish, 500);
      } else {
        window.addEventListener('load', () => setTimeout(finish, 500));
      }
      // Safety net in case 'load' never fires (e.g. blocked external resource)
      setTimeout(finish, 6000);
    })();

    // =========================================================
    // REPORTS FEATURE
    // History is now logged directly by the ESP32 receiver — every
    // LoRa packet it gets pushed to '/worker1/logs/{pushId}' with a
    // Firebase server timestamp (see receiver_final.ino). That's more
    // reliable than the old browser-side timer, since it doesn't
    // depend on this tab staying open, and works even if nobody is
    // viewing the dashboard. This section just reads that log and
    // renders/export the daily/weekly/monthly reports + charts.
    // =========================================================
    (function initReportsFeature() {
      const reportsModalEl = document.getElementById('reportsModal');
      if (!reportsModalEl) return;

      // ---- Reports modal open/close + range tabs ----
      let currentRange = 'daily';
      let lastFetchedEntries = [];
      let lastFetchedOfflineEvents = [];
      const charts = { trend: null, alert: null };

      // If no log entry arrives for longer than this, the gap is treated as
      // "vest offline" (transmitter and/or receiver powered off / out of
      // range). The transmitter normally sends every few seconds, so 30s of
      // silence is a safe margin above normal jitter/retries.
      const OFFLINE_GAP_MS = 10 * 1000;

      window.openReportsModal = function (e) {
        if (e) e.preventDefault();
        reportsModalEl.classList.add('active');
        document.body.style.overflow = 'hidden';
        loadReport(currentRange);
      };
      window.closeReportsModal = function () {
        reportsModalEl.classList.remove('active');
        document.body.style.overflow = '';
      };
      reportsModalEl.addEventListener('click', (e) => {
        if (e.target === reportsModalEl) window.closeReportsModal();
      });

      window.setReportRange = function (range) {
        currentRange = range;
        document.querySelectorAll('.report-tab').forEach((btn) => {
          btn.classList.toggle('active', btn.dataset.range === range);
        });
        loadReport(range);
      };

      function rangeStartTs(range) {
        const now = Date.now();
        if (range === 'daily') return now - 24 * 60 * 60 * 1000;
        if (range === 'weekly') return now - 7 * 24 * 60 * 60 * 1000;
        return now - 30 * 24 * 60 * 60 * 1000; // monthly
      }

      function loadReport(range) {
        const summaryEl = document.getElementById('reportSummaryCards');
        const chartsWrapEl = document.getElementById('reportChartsWrap');
        const stateMsgEl = document.getElementById('reportStateMsg');
        const exportBtnEl = document.getElementById('reportExportBtn');

        stateMsgEl.style.display = 'block';
        stateMsgEl.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Loading report...';
        chartsWrapEl.style.display = 'none';
        summaryEl.innerHTML = '';
        exportBtnEl.disabled = true;

        if (typeof db === 'undefined') {
          stateMsgEl.textContent = 'ডাটাবেজ সংযোগ পাওয়া যায়নি।';
          return;
        }

        const startTs = rangeStartTs(range);
        db.ref('/worker1/logs').orderByChild('ts').startAt(startTs).once('value')
          .then((snap) => {
            const entries = [];
            snap.forEach((child) => {
              const v = child.val();
              if (v && v.ts) entries.push(v);
            });
            entries.sort((a, b) => a.ts - b.ts);
            lastFetchedEntries = entries;
            lastFetchedOfflineEvents = computeOfflineEvents(entries);

            if (entries.length === 0) {
              stateMsgEl.style.display = 'block';
              stateMsgEl.innerHTML = 'এই সময়সীমার জন্য এখনো কোনো রিপোর্ট ডাটা জমা হয়নি।<br><span style="font-size:10.5px;">ভেস্ট চালু থাকলে ও ডাটা পাঠালে এখানে স্বয়ংক্রিয়ভাবে হিস্ট্রি জমা হবে।</span>';
              chartsWrapEl.style.display = 'none';
              exportBtnEl.disabled = true;
              renderConnectivity(lastFetchedOfflineEvents);
              return;
            }

            stateMsgEl.style.display = 'none';
            chartsWrapEl.style.display = 'block';
            exportBtnEl.disabled = false;

            renderSummary(entries, lastFetchedOfflineEvents);
            renderCharts(entries, range);
            renderConnectivity(lastFetchedOfflineEvents);
          })
          .catch((err) => {
            stateMsgEl.style.display = 'block';
            stateMsgEl.textContent = 'রিপোর্ট লোড করতে ব্যর্থ হয়েছে: ' + err.message;
            chartsWrapEl.style.display = 'none';
          });
      }

      // Scans the sorted log entries for gaps bigger than OFFLINE_GAP_MS and
      // turns each gap into an offline event: { from, to, durationMs, ongoing }.
      // 'from' is the last reading before it went quiet, 'to' is when data
      // resumed. If the newest entry is itself more than OFFLINE_GAP_MS in
      // the past, that's an ongoing offline period (device is offline right
      // now), so it's included too with ongoing:true and to = current time.
      function computeOfflineEvents(entries) {
        const events = [];
        for (let i = 1; i < entries.length; i++) {
          const gap = entries[i].ts - entries[i - 1].ts;
          if (gap > OFFLINE_GAP_MS) {
            events.push({ from: entries[i - 1].ts, to: entries[i].ts, durationMs: gap, ongoing: false });
          }
        }
        if (entries.length) {
          const lastTs = entries[entries.length - 1].ts;
          const now = Date.now();
          if (now - lastTs > OFFLINE_GAP_MS) {
            events.push({ from: lastTs, to: now, durationMs: now - lastTs, ongoing: true });
          }
        }
        return events;
      }

      function fmtOfflineTime(ts) {
        return new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
      }

      function fmtDuration(ms) {
        const totalMin = Math.round(ms / 60000);
        if (totalMin < 1) return '< ১ মিনিট';
        if (totalMin < 60) return totalMin + ' মিনিট';
        const h = Math.floor(totalMin / 60), m = totalMin % 60;
        return h + ' ঘণ্টা' + (m ? ' ' + m + ' মিনিট' : '');
      }

      function renderConnectivity(events) {
        const wrap = document.getElementById('reportConnectivityWrap');
        if (!wrap) return;

        if (!events.length) {
          wrap.innerHTML = '<div class="report-connectivity-title"><i class="fa-solid fa-tower-broadcast"></i> CONNECTIVITY</div>' +
            '<div class="offline-empty">এই সময়সীমায় কোনো সংযোগ বিচ্ছিন্নতা পাওয়া যায়নি — ভেস্ট সবসময় ডাটা পাঠিয়েছে।</div>';
          return;
        }

        const items = events.slice().reverse().map((e) => `
          <div class="offline-event-item ${e.ongoing ? 'offline-event-live' : ''}">
            <i class="fa-solid ${e.ongoing ? 'fa-triangle-exclamation' : 'fa-plug-circle-xmark'}"></i>
            <div class="offline-event-text">
              <div>${e.ongoing ? 'বর্তমানে অফলাইন' : 'অফলাইন হয়েছিল'} — ${fmtOfflineTime(e.from)}</div>
              <div class="offline-event-sub">${e.ongoing ? 'শেষ ডাটা পাওয়া গিয়েছিল এই সময়ে' : 'সংযোগ ফিরেছে: ' + fmtOfflineTime(e.to)} &middot; স্থায়িত্ব: ${fmtDuration(e.durationMs)}</div>
            </div>
          </div>
        `).join('');

        wrap.innerHTML = '<div class="report-connectivity-title"><i class="fa-solid fa-tower-broadcast"></i> CONNECTIVITY (' + events.length + ')</div>' +
          '<div class="offline-event-list">' + items + '</div>';
      }

      function renderSummary(entries, offlineEvents) {
        const temps = entries.map((e) => e.temperature).filter((v) => typeof v === 'number' && !isNaN(v));
        const hums = entries.map((e) => e.humidity).filter((v) => typeof v === 'number' && !isNaN(v));
        const fallCount = entries.filter((e) => e.fall).length;
        const sosCount = entries.filter((e) => e.sos).length;
        const offlineCount = (offlineEvents || []).length;

        const avg = (arr) => (arr.length ? (arr.reduce((a, b) => a + b, 0) / arr.length) : null);
        const fmt = (v, unit) => (v === null || isNaN(v)) ? '--' : v.toFixed(1) + unit;

        const cards = [
          { label: 'Records', value: String(entries.length) },
          { label: 'Avg Temp', value: fmt(avg(temps), '°C') },
          { label: 'Max Temp', value: temps.length ? fmt(Math.max(...temps), '°C') : '--' },
          { label: 'Avg Humidity', value: fmt(avg(hums), '%') },
          { label: 'Fall Snapshots', value: String(fallCount) },
          { label: 'SOS Snapshots', value: String(sosCount) },
          { label: 'Offline Events', value: String(offlineCount) }
        ];

        document.getElementById('reportSummaryCards').innerHTML = cards.map((c) => `
          <div class="report-summary-card">
            <div class="rsc-label">${c.label}</div>
            <div class="rsc-value">${c.value}</div>
          </div>
        `).join('');
      }

      function bucketLabel(ts) {
        return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      }

      function groupByDay(entries) {
        const map = {};
        entries.forEach((e) => {
          const key = new Date(e.ts).toLocaleDateString([], { month: 'short', day: 'numeric' });
          if (!map[key]) map[key] = { temps: [], falls: 0, sos: 0 };
          if (typeof e.temperature === 'number' && !isNaN(e.temperature)) map[key].temps.push(e.temperature);
          if (e.fall) map[key].falls++;
          if (e.sos) map[key].sos++;
        });
        return map;
      }

      function chartOptions(stacked) {
        return {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { labels: { color: '#8493a8', font: { size: 9 } } } },
          scales: {
            x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } }, stacked: !!stacked },
            y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } }, stacked: !!stacked, beginAtZero: true }
          }
        };
      }

      function renderCharts(entries, range) {
        const trendCtx = document.getElementById('reportTrendChart').getContext('2d');
        const alertCtx = document.getElementById('reportAlertChart').getContext('2d');

        if (charts.trend) charts.trend.destroy();
        if (charts.alert) charts.alert.destroy();

        if (range === 'daily') {
          const labels = entries.map((e) => bucketLabel(e.ts));
          charts.trend = new Chart(trendCtx, {
            type: 'line',
            data: {
              labels,
              datasets: [
                { label: 'Temp (°C)', data: entries.map((e) => e.temperature), borderColor: '#ff3366', backgroundColor: 'rgba(255,51,102,.1)', tension: 0.3, pointRadius: 0 },
                { label: 'Humidity (%)', data: entries.map((e) => e.humidity), borderColor: '#00f2ff', backgroundColor: 'rgba(0,242,255,.1)', tension: 0.3, pointRadius: 0 }
              ]
            },
            options: chartOptions()
          });

          charts.alert = new Chart(alertCtx, {
            type: 'bar',
            data: {
              labels,
              datasets: [
                { label: 'Fall', data: entries.map((e) => (e.fall ? 1 : 0)), backgroundColor: '#ff3366' },
                { label: 'SOS', data: entries.map((e) => (e.sos ? 1 : 0)), backgroundColor: '#f59e0b' }
              ]
            },
            options: chartOptions(true)
          });
        } else {
          const grouped = groupByDay(entries);
          const days = Object.keys(grouped);
          const avgTemps = days.map((d) => {
            const t = grouped[d].temps;
            return t.length ? (t.reduce((a, b) => a + b, 0) / t.length) : null;
          });

          charts.trend = new Chart(trendCtx, {
            type: 'line',
            data: {
              labels: days,
              datasets: [
                { label: 'Avg Temp (°C)', data: avgTemps, borderColor: '#ff3366', backgroundColor: 'rgba(255,51,102,.1)', tension: 0.3 }
              ]
            },
            options: chartOptions()
          });

          charts.alert = new Chart(alertCtx, {
            type: 'bar',
            data: {
              labels: days,
              datasets: [
                { label: 'Fall', data: days.map((d) => grouped[d].falls), backgroundColor: '#ff3366' },
                { label: 'SOS', data: days.map((d) => grouped[d].sos), backgroundColor: '#f59e0b' }
              ]
            },
            options: chartOptions(true)
          });
        }
      }

      // ---- 3) Export the currently loaded report range to Excel ----
      window.exportReportData = function () {
        if (!lastFetchedEntries.length) return;
        const btn = document.getElementById('reportExportBtn');
        const originalHtml = btn.innerHTML;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Exporting...';
        btn.disabled = true;

        try {
          const header = ['Timestamp', 'Temperature (C)', 'Humidity (%)', 'Pressure (hPa)', 'Fall', 'SOS'];
          const rows = lastFetchedEntries.map((e) => [
            new Date(e.ts).toLocaleString(),
            (typeof e.temperature === 'number' && !isNaN(e.temperature)) ? e.temperature : '',
            (typeof e.humidity === 'number' && !isNaN(e.humidity)) ? e.humidity : '',
            (typeof e.pressure === 'number' && !isNaN(e.pressure)) ? e.pressure : '',
            e.fall ? 'YES' : 'NO',
            e.sos ? 'YES' : 'NO'
          ]);

          const sheetData = [
            ['Worker ID', 'SV-001'],
            ['Vest ID', 'VEST-001'],
            ['Report Range', currentRange.toUpperCase()],
            ['Generated At', new Date().toLocaleString()],
            ['Records', String(rows.length)],
            [],
            header,
            ...rows
          ];

          const worksheet = XLSX.utils.aoa_to_sheet(sheetData);
          worksheet['!cols'] = [{ wch: 20 }, { wch: 16 }, { wch: 14 }, { wch: 14 }, { wch: 8 }, { wch: 8 }];

          const workbook = XLSX.utils.book_new();
          XLSX.utils.book_append_sheet(workbook, worksheet, currentRange + ' report');

          if (lastFetchedOfflineEvents.length) {
            const offlineHeader = ['Offline Since', 'Reconnected / Status', 'Duration'];
            const offlineRows = lastFetchedOfflineEvents.map((e) => [
              new Date(e.from).toLocaleString(),
              e.ongoing ? 'Still offline' : new Date(e.to).toLocaleString(),
              fmtDuration(e.durationMs)
            ]);
            const offlineSheet = XLSX.utils.aoa_to_sheet([offlineHeader, ...offlineRows]);
            offlineSheet['!cols'] = [{ wch: 20 }, { wch: 20 }, { wch: 14 }];
            XLSX.utils.book_append_sheet(workbook, offlineSheet, 'offline events');
          }

          const dateStamp = new Date().toISOString().replace(/[:.]/g, '-');
          XLSX.writeFile(workbook, 'worker1_' + currentRange + '_report_' + dateStamp + '.xlsx');
        } catch (err) {
          alert('রিপোর্ট এক্সপোর্ট করতে ব্যর্থ হয়েছে:\n' + err.message);
        } finally {
          btn.innerHTML = originalHtml;
          btn.disabled = false;
        }
      };
    })();

    // =========================================================
    // MAP TRACKING FEATURE
    // Opens as its own full-screen view (like Reports) and draws
    // the worker's movement as a path on the map, built from the
    // same '/worker1/logs' history the ESP32 already writes
    // (each entry carries latitude/longitude + a server timestamp).
    // =========================================================
    (function initTrackingFeature() {
      const trackingModalEl = document.getElementById('trackingModal');
      if (!trackingModalEl) return;

      let trackingRange = 'daily';
      let trackingMap = null;
      let trackingPath = null;
      let trackingStartMarker = null;
      let trackingEndMarker = null;

      function trackingRangeStartTs(range) {
        const now = Date.now();
        if (range === 'daily') return now - 24 * 60 * 60 * 1000;
        if (range === 'weekly') return now - 7 * 24 * 60 * 60 * 1000;
        return now - 30 * 24 * 60 * 60 * 1000; // monthly
      }

      window.openTrackingModal = function (e) {
        if (e) e.preventDefault();
        trackingModalEl.classList.add('active');
        document.body.style.overflow = 'hidden';

        if (!trackingMap) {
          trackingMap = L.map('trackingMap', { zoomControl: true }).setView([22.3569, 91.7832], 16);
          L.tileLayer(googleSatUrl, { maxZoom: 20, attribution: '&copy; Google Maps' }).addTo(trackingMap);
        }
        setTimeout(() => { trackingMap.invalidateSize(); loadTrackingPath(trackingRange); }, 300);
      };

      window.closeTrackingModal = function () {
        trackingModalEl.classList.remove('active');
        document.body.style.overflow = '';
      };

      trackingModalEl.addEventListener('click', (e) => {
        if (e.target === trackingModalEl) window.closeTrackingModal();
      });

      window.setTrackingRange = function (range) {
        trackingRange = range;
        document.querySelectorAll('#trackingModal .report-tab').forEach((btn) => {
          btn.classList.toggle('active', btn.dataset.trange === range);
        });
        loadTrackingPath(range);
      };

      function fmtTrackTime(ts) {
        return new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
      }

      function loadTrackingPath(range) {
        const summaryEl = document.getElementById('trackingSummary');
        const stateMsgEl = document.getElementById('trackingStateMsg');
        stateMsgEl.style.display = 'none';
        summaryEl.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> লোড হচ্ছে...';

        if (typeof db === 'undefined') {
          summaryEl.innerHTML = '';
          stateMsgEl.style.display = 'block';
          stateMsgEl.textContent = 'ডাটাবেজ সংযোগ পাওয়া যায়নি।';
          return;
        }

        const startTs = trackingRangeStartTs(range);
        db.ref('/worker1/logs').orderByChild('ts').startAt(startTs).once('value')
          .then((snap) => {
            const points = [];
            snap.forEach((child) => {
              const v = child.val();
              // Skip entries with no GPS fix yet (0,0 is the typical
              // "no fix" default from the GPS module).
              if (v && v.ts && typeof v.latitude === 'number' && typeof v.longitude === 'number' &&
                  !(v.latitude === 0 && v.longitude === 0)) {
                points.push({ ts: v.ts, lat: v.latitude, lng: v.longitude });
              }
            });
            points.sort((a, b) => a.ts - b.ts);
            renderTrackingPath(points);
          })
          .catch((err) => {
            summaryEl.innerHTML = '';
            stateMsgEl.style.display = 'block';
            stateMsgEl.textContent = 'ট্র্যাকিং ডাটা লোড করতে ব্যর্থ হয়েছে: ' + err.message;
          });
      }

      function renderTrackingPath(points) {
        const summaryEl = document.getElementById('trackingSummary');
        const stateMsgEl = document.getElementById('trackingStateMsg');

        if (trackingPath) { trackingMap.removeLayer(trackingPath); trackingPath = null; }
        if (trackingStartMarker) { trackingMap.removeLayer(trackingStartMarker); trackingStartMarker = null; }
        if (trackingEndMarker) { trackingMap.removeLayer(trackingEndMarker); trackingEndMarker = null; }

        if (!points.length) {
          summaryEl.innerHTML = '';
          stateMsgEl.style.display = 'block';
          stateMsgEl.innerHTML = 'এই সময়সীমার জন্য কোনো GPS অবস্থান পাওয়া যায়নি।';
          return;
        }

        stateMsgEl.style.display = 'none';
        const latlngs = points.map((p) => [p.lat, p.lng]);

        trackingPath = L.polyline(latlngs, { color: '#00f2ff', weight: 3, opacity: 0.85 }).addTo(trackingMap);
        trackingStartMarker = L.circleMarker(latlngs[0], { radius: 6, color: '#22c55e', fillColor: '#22c55e', fillOpacity: 1 })
          .addTo(trackingMap)
          .bindPopup('শুরু: ' + fmtTrackTime(points[0].ts));
        trackingEndMarker = L.marker(latlngs[latlngs.length - 1])
          .addTo(trackingMap)
          .bindPopup('সর্বশেষ অবস্থান: ' + fmtTrackTime(points[points.length - 1].ts))
          .openPopup();

        trackingMap.fitBounds(trackingPath.getBounds(), { padding: [30, 30] });

        summaryEl.innerHTML =
          '<span>' + points.length + '</span> পয়েন্ট &middot; ' +
          'শুরু: <span>' + fmtTrackTime(points[0].ts) + '</span> &middot; ' +
          'শেষ: <span>' + fmtTrackTime(points[points.length - 1].ts) + '</span>';
      }
    })();

    // =========================================================
    // ALL ALERTS FEATURE
    // Opens as its own full-screen view and lists every alert found
    // in '/worker1/logs' history: fall, SOS, and temperature/
    // humidity/pressure readings outside the safe range.
    // Uses the exact same thresholds as the live dashboard above
    // (15-45°C, 20-90% humidity, 950-1050 hPa) so the
    // two views always agree.
    // =========================================================
    (function initAlertsFeature() {
      const alertsModalEl = document.getElementById('alertsModal');
      if (!alertsModalEl) return;

      let alertsRange = 'daily';

      function alertsRangeStartTs(range) {
        const now = Date.now();
        if (range === 'daily') return now - 24 * 60 * 60 * 1000;
        if (range === 'weekly') return now - 7 * 24 * 60 * 60 * 1000;
        return now - 30 * 24 * 60 * 60 * 1000; // monthly
      }

      window.openAlertsModal = function (e) {
        if (e) e.preventDefault();
        alertsModalEl.classList.add('active');
        document.body.style.overflow = 'hidden';
        loadAlerts(alertsRange);
      };

      window.closeAlertsModal = function () {
        alertsModalEl.classList.remove('active');
        document.body.style.overflow = '';
      };

      alertsModalEl.addEventListener('click', (e) => {
        if (e.target === alertsModalEl) window.closeAlertsModal();
      });

      window.setAlertsRange = function (range) {
        alertsRange = range;
        document.querySelectorAll('#alertsModal .report-tab').forEach((btn) => {
          btn.classList.toggle('active', btn.dataset.arange === range);
        });
        loadAlerts(range);
      };

      function fmtAlertTime(ts) {
        return new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
      }

      // Same rules as the live dashboard's activeAlertsList logic above.
      function evaluateEntryAlerts(e) {
        const types = [];
        if (e.fall) {
          types.push({ key: 'fall', label: 'FALL', detail: 'ফল ডিটেক্ট হয়েছে' });
        }
        if (e.sos) {
          types.push({ key: 'sos', label: 'SOS', detail: 'SOS বাটন সক্রিয় হয়েছিল' });
        }
        if (typeof e.temperature === 'number' && !isNaN(e.temperature) && (e.temperature > 45 || e.temperature < 15)) {
          types.push({
            key: 'temp', label: 'TEMP',
            detail: (e.temperature > 45 ? 'উচ্চ' : 'নিম্ন') + ' তাপমাত্রা: ' + e.temperature.toFixed(1) + '°C (নিরাপদ সীমা 15°C–45°C)'
          });
        }
        if (typeof e.humidity === 'number' && !isNaN(e.humidity) && (e.humidity > 90 || e.humidity < 20)) {
          types.push({
            key: 'humidity', label: 'HUMIDITY',
            detail: (e.humidity > 90 ? 'উচ্চ' : 'নিম্ন') + ' আর্দ্রতা: ' + e.humidity.toFixed(1) + '% (নিরাপদ সীমা 20%–90%)'
          });
        }
        if (typeof e.pressure === 'number' && !isNaN(e.pressure) && (e.pressure > 1050 || e.pressure < 950)) {
          types.push({
            key: 'pressure', label: 'PRESSURE',
            detail: (e.pressure > 1050 ? 'উচ্চ' : 'নিম্ন') + ' চাপ: ' + e.pressure.toFixed(0) + ' hPa (নিরাপদ সীমা 950–1050 hPa)'
          });
        }
        return types;
      }

      function loadAlerts(range) {
        const summaryEl = document.getElementById('alertsSummaryCards');
        const listEl = document.getElementById('alertsListWrap');
        const stateMsgEl = document.getElementById('alertsStateMsg');

        stateMsgEl.style.display = 'block';
        stateMsgEl.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Loading alerts...';
        summaryEl.innerHTML = '';
        listEl.innerHTML = '';

        if (typeof db === 'undefined') {
          stateMsgEl.textContent = 'ডাটাবেজ সংযোগ পাওয়া যায়নি।';
          return;
        }

        const startTs = alertsRangeStartTs(range);
        db.ref('/worker1/logs').orderByChild('ts').startAt(startTs).once('value')
          .then((snap) => {
            const rows = [];
            const counts = { fall: 0, sos: 0, temp: 0, humidity: 0, pressure: 0 };

            snap.forEach((child) => {
              const v = child.val();
              if (!v || !v.ts) return;
              const types = evaluateEntryAlerts(v);
              if (types.length) {
                types.forEach((t) => { counts[t.key] = (counts[t.key] || 0) + 1; });
                rows.push({ ts: v.ts, types });
              }
            });

            rows.sort((a, b) => a.ts - b.ts);
            renderAlerts(rows, counts);
          })
          .catch((err) => {
            stateMsgEl.style.display = 'block';
            stateMsgEl.textContent = 'অ্যালার্ট লোড করতে ব্যর্থ হয়েছে: ' + err.message;
          });
      }

      function renderAlerts(rows, counts) {
        const summaryEl = document.getElementById('alertsSummaryCards');
        const listEl = document.getElementById('alertsListWrap');
        const stateMsgEl = document.getElementById('alertsStateMsg');

        const cards = [
          { label: 'Total Alert Events', value: String(rows.length) },
          { label: 'Fall', value: String(counts.fall) },
          { label: 'SOS', value: String(counts.sos) },
          { label: 'Temperature', value: String(counts.temp) },
          { label: 'Humidity', value: String(counts.humidity) },
          { label: 'Pressure', value: String(counts.pressure) }
        ];
        summaryEl.innerHTML = cards.map((c) => `
          <div class="report-summary-card">
            <div class="rsc-label">${c.label}</div>
            <div class="rsc-value">${c.value}</div>
          </div>
        `).join('');

        if (!rows.length) {
          stateMsgEl.style.display = 'block';
          stateMsgEl.innerHTML = 'এই সময়সীমায় কোনো অ্যালার্ট পাওয়া যায়নি — সব রিডিং নিরাপদ সীমার মধ্যে ছিল।';
          listEl.innerHTML = '';
          return;
        }

        stateMsgEl.style.display = 'none';

        const tagClass = { fall: 'alert-type-fall', sos: 'alert-type-sos', temp: 'alert-type-temp', humidity: 'alert-type-humidity', pressure: 'alert-type-pressure' };

        listEl.innerHTML = rows.slice().reverse().map((r) => {
          const tags = r.types.map((t) => `<span class="alert-type-tag ${tagClass[t.key]}">${t.label}</span>`).join('');
          const details = r.types.map((t) => t.detail).join(' &middot; ');
          return `
            <div class="alert-log-item">
              <i class="fa-solid fa-triangle-exclamation"></i>
              <div>
                <div class="alert-log-time">${fmtAlertTime(r.ts)}</div>
                <div class="alert-log-tags">${tags}</div>
                <div class="alert-log-detail">${details}</div>
              </div>
            </div>
          `;
        }).join('');
      }
    })();

    // =========================================================
    // HELMET MODULE (additive)
    // Shows the smart helmet's data (Firebase '/helmet1/live' and
    // '/helmet1/logs') the same way the vest is shown: live sensor
    // cards + sparklines, live charts, proximity meters, device
    // status, active alerts, event history, Excel export, and the
    // helmet tabs inside the Reports / Alerts windows.
    //
    // Nothing above this block is modified. It only reuses a few
    // existing globals (db, createSparkline, updateSparkline,
    // createLiveChart, pushLiveChartPoint, flattenToObject,
    // playEmergencySound, sidebar).
    //
    // Thresholds are the same as the helmet firmware
    // (Nano1_SensorNode_v3.ino).
    // =========================================================
    (function initHelmetModule() {
      const sectionEl = document.getElementById('helmetSection');
      if (!sectionEl) return;

      const LIVE_PATH = '/helmet1/live';
      const LOGS_PATH = '/helmet1/logs';

      const TH = {
        OBST_CM: 50,      // OBSTACLE_THRESHOLD_CM
        NO_ECHO: 999,     // firmware sends 999 = nothing in range
        GAS_MARGIN: 150,  // GAS_MARGIN above the clean-air baseline
        HR_MIN: 50, HR_MAX: 120, SPO2_MIN: 90
      };
      const PROX_SCALE_CM = 100;          // bar is full at 0 cm, empty at 100 cm
      const HELMET_STALE_MS = 20000;      // no new data for 20s => offline
      const HEALTH_CONFIRM = 3;           // abnormal vitals must repeat 3 updates
      const MAX_H_HISTORY = 10;
      const MAX_REPORT_ENTRIES = 20000;
      const OFFLINE_GAP_MS = 90 * 1000;   // logs are written every ~30s

      const $ = (id) => document.getElementById(id);
      const esc = (v) => String(v).replace(/[&<>"']/g, (ch) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
      }[ch]));
      const toNum = (v, d) => {
        const x = Number(v);
        return (v === undefined || v === null || v === '' || !isFinite(x)) ? d : x;
      };
      const setText = (id, text) => { const el = $(id); if (el) el.textContent = text; };
      const setStatus = (id, text, color) => {
        const el = $(id);
        if (!el) return;
        el.textContent = text;
        el.style.color = color;
      };

      const SIDES = [
        { key: 'L', name: 'LEFT',  bn: 'বাম',   dist: 'distLeft',  flag: 'obstLeft'  },
        { key: 'F', name: 'FRONT', bn: 'সামনে', dist: 'distFront', flag: 'obstFront' },
        { key: 'R', name: 'RIGHT', bn: 'ডান',   dist: 'distRight', flag: 'obstRight' }
      ];

      // ---------- data helpers ----------
      function normalize(d) {
        const p = {
          hr: toNum(d.heartRate, 0),
          spo2: toNum(d.spo2, 0),
          gasLevel: toNum(d.gasLevel, 0),
          gasBase: toNum(d.gasBaseline, 0),
          gasFlag: toNum(d.gas, 0) === 1,
          obstFlag: toNum(d.obstacle, 0) === 1,
          dist: {}, obst: {}
        };
        SIDES.forEach((s) => {
          p.dist[s.key] = toNum(d[s.dist], TH.NO_ECHO);
          p.obst[s.key] = toNum(d[s.flag], 0) === 1;
        });
        return p;
      }
      const anyObst = (p) => p.obstFlag || SIDES.some((s) => p.obst[s.key]);
      const hrBad = (p) => p.hr > 0 && (p.hr < TH.HR_MIN || p.hr > TH.HR_MAX);
      const spo2Bad = (p) => p.spo2 > 0 && p.spo2 < TH.SPO2_MIN;

      // One list of alerts for a reading. `health` says whether abnormal
      // vitals are confirmed (live view debounces them, logs do not).
      function evaluateAlerts(p, health) {
        const list = [];
        let sideHit = false;
        SIDES.forEach((s) => {
          if (!p.obst[s.key]) return;
          sideHit = true;
          const d = p.dist[s.key];
          const dTxt = d >= TH.NO_ECHO ? '' : d + ' cm';
          list.push({
            key: 'obstacle', label: 'OBSTACLE', icon: 'fa-road-barrier',
            title: 'Obstacle Detected — ' + s.name,
            detail: (dTxt ? 'Object ' + dTxt + ' away' : 'Object detected') + ' — closer than ' + TH.OBST_CM + ' cm',
            detailBn: s.bn + ' দিকে বাধা' + (dTxt ? ' (' + dTxt + ')' : '') + ' — ' + TH.OBST_CM + ' সেমি-র চেয়ে কাছে'
          });
        });
        if (!sideHit && p.obstFlag) {
          list.push({
            key: 'obstacle', label: 'OBSTACLE', icon: 'fa-road-barrier',
            title: 'Obstacle Detected',
            detail: 'An ultrasonic sensor reports an obstacle',
            detailBn: 'আলট্রাসনিক সেন্সর বাধা শনাক্ত করেছে'
          });
        }
        if (p.gasFlag) {
          const extra = p.gasBase ? ' — baseline ' + p.gasBase + ' (+' + (p.gasLevel - p.gasBase) + ')' : '';
          list.push({
            key: 'gas', label: 'GAS', icon: 'fa-smog',
            title: 'Gas Detected',
            detail: 'MQ-2 level ' + p.gasLevel + extra,
            detailBn: 'গ্যাস শনাক্ত হয়েছে: MQ-2 লেভেল ' + p.gasLevel + (p.gasBase ? ' (বেসলাইন ' + p.gasBase + ')' : '')
          });
        }
        if (health && health.hr) {
          list.push({
            key: 'hr', label: 'HEART RATE', icon: 'fa-heart-pulse',
            title: p.hr < TH.HR_MIN ? 'Low Heart Rate' : 'High Heart Rate',
            detail: 'Reading is ' + p.hr + ' bpm — outside the safe range of ' + TH.HR_MIN + ' – ' + TH.HR_MAX + ' bpm',
            detailBn: (p.hr < TH.HR_MIN ? 'কম' : 'বেশি') + ' হার্ট রেট: ' + p.hr + ' bpm (নিরাপদ সীমা ' + TH.HR_MIN + '–' + TH.HR_MAX + ')'
          });
        }
        if (health && health.spo2) {
          list.push({
            key: 'spo2', label: 'SPO2', icon: 'fa-lungs',
            title: 'Low SpO₂',
            detail: 'Reading is ' + p.spo2 + '% — below the safe minimum of ' + TH.SPO2_MIN + '%',
            detailBn: 'SpO₂ কম: ' + p.spo2 + '% (নিরাপদ সীমা ' + TH.SPO2_MIN + '% এর উপরে)'
          });
        }
        return list;
      }

      // ---------- live charts / sparklines ----------
      createSparkline('hHrSpark', '#ff3366', 'rgba(255, 51, 102, 0.35)');
      createSparkline('hSpo2Spark', '#00f2ff', 'rgba(0, 242, 255, 0.35)');
      createSparkline('hGasSpark', '#f59e0b', 'rgba(245, 158, 11, 0.35)');

      const hrChart = createLiveChart('hHrChart', 'Heart Rate (bpm)', '#ff3366', 'rgba(255,51,102,0.1)');
      const spo2Chart = createLiveChart('hSpo2Chart', 'SpO₂ (%)', '#00f2ff', 'rgba(0,242,255,0.1)');
      const gasChart = createLiveChart('hGasChart', 'Gas Level', '#f59e0b', 'rgba(245,158,11,0.1)');
      gasChart.data.datasets.push({
        label: 'Alert level', data: [], borderColor: '#ff3366', borderDash: [5, 4],
        borderWidth: 1.2, pointRadius: 0, fill: false, tension: 0
      });
      gasChart.options.plugins.legend = { display: true, labels: { color: '#8493a8', font: { size: 8 } } };
      spo2Chart.options.scales.y.suggestedMin = 80;
      spo2Chart.options.scales.y.suggestedMax = 100;

      const distChart = new Chart($('hDistChart').getContext('2d'), {
        type: 'line',
        data: {
          labels: [],
          datasets: [
            { label: 'Left',  data: [], borderColor: '#00f2ff', backgroundColor: 'rgba(0,242,255,0.08)', tension: 0.3, pointRadius: 0, borderWidth: 1.6 },
            { label: 'Front', data: [], borderColor: '#ff3366', backgroundColor: 'rgba(255,51,102,0.08)', tension: 0.3, pointRadius: 0, borderWidth: 1.6 },
            { label: 'Right', data: [], borderColor: '#a855f7', backgroundColor: 'rgba(168,85,247,0.08)', tension: 0.3, pointRadius: 0, borderWidth: 1.6 },
            { label: 'Alert (' + TH.OBST_CM + ' cm)', data: [], borderColor: '#f59e0b', borderDash: [5, 4], borderWidth: 1, pointRadius: 0, fill: false }
          ]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: true, labels: { color: '#8493a8', font: { size: 8 }, boxWidth: 10 } } },
          scales: {
            x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } } },
            y: {
              min: 0, max: 110,
              grid: { color: 'rgba(255,255,255,0.05)' },
              ticks: { color: '#8493a8', font: { size: 8 }, callback: (v) => (v > 100 ? '' : v) }
            }
          }
        }
      });

      function pushPoints(chart, label, values) {
        chart.data.labels.push(label);
        values.forEach((v, i) => chart.data.datasets[i].data.push(v));
        if (chart.data.labels.length > 10) {
          chart.data.labels.shift();
          chart.data.datasets.forEach((ds) => ds.data.shift());
        }
        chart.update();
      }

      // ---------- state ----------
      let hConnected = false;
      let hOffline = false;
      let hLastUpdate = Date.now();
      let hLastRaw = null;
      let hHrBadCount = 0;
      let hSpo2BadCount = 0;
      let hAccessDenied = false;
      let hHistory = [];
      let lastNotified = new Set();

      const pillEl = $('helmetConnPill');

      function setPill(cls, label) {
        if (!pillEl) return;
        pillEl.classList.toggle('connected', cls === 'connected');
        pillEl.innerHTML = '<i class="fa-solid fa-helmet-safety" style="font-size: 9px;"></i> ' + label;
      }

      function setDeviceRows(text, color) {
        ['hStNano1', 'hStEsp', 'hStPulse', 'hStGas', 'hStUltra'].forEach((id) => setStatus(id, text, color));
      }

      function setConnectedUi() {
        hConnected = true;
        hOffline = false;
        setPill('connected', 'HELMET STREAMING');
        $('hSysIcon').classList.add('active');
        $('hSysIcon').innerHTML = '<i class="fa-solid fa-helmet-safety"></i>';
        $('hSysStatusText').classList.add('active');
        setText('hSysSubText', 'REALTIME STREAMING');
        setText('hSysNote', 'Live from Firebase /helmet1');
        setStatus('hInfoStatus', 'ONLINE', 'var(--green)');
        setDeviceRows('Connected', 'var(--green)');
        $('hEventLogTable').innerHTML = '';
      }

      function goOffline() {
        if (hOffline) return;
        hOffline = true;
        hConnected = false;
        hHrBadCount = 0;
        hSpo2BadCount = 0;
        lastNotified = new Set();

        setPill('offline', 'HELMET OFFLINE');
        $('hSysIcon').classList.remove('active');
        $('hSysIcon').innerHTML = '<i class="fa-solid fa-plug-circle-xmark"></i>';
        $('hSysStatusText').classList.remove('active');
        $('hSysStatusText').textContent = 'HELMET OFFLINE';
        $('hSysStatusText').closest('.system-card').classList.remove('h-alert');
        setText('hSysSubText', 'NO DATA STREAM RECEIVED');
        setText('hSysNote', 'Waiting for the helmet to send data...');
        setStatus('hInfoStatus', 'OFFLINE', 'var(--red)');
        setText('hLastSync', '--:--:--');
        setDeviceRows('Disconnected', 'var(--red)');
        setText('hDataAge', '--');

        setText('hHrVal', '--');
        setStatus('hHrStatus', 'No Data', 'var(--text-muted)');
        setText('hSpo2Val', '--');
        setStatus('hSpo2Status', 'No Data', 'var(--text-muted)');
        setText('hGasVal', '--');
        setStatus('hGasStatus', 'No Data', 'var(--text-muted)');
        setText('hObstVal', '--');
        setText('hObstUnit', '');
        setStatus('hObstStatus', 'No Data', 'var(--text-muted)');
        SIDES.forEach((s) => {
          const b = $('hBadge' + s.key);
          b.className = 'badge badge-safe';
          b.textContent = s.key + ' · --';
          $('hProxFill' + s.key).style.width = '0%';
          $('hProx' + s.key).classList.remove('hit');
          setText('hProxVal' + s.key, '--');
        });

        setText('hActiveAlertCount', '0');
        $('hAlertBox').innerHTML =
          '<div style="font-size: 10px; color: var(--amber); text-align: center; padding: 15px 0;">' +
          '<i class="fa-solid fa-plug-circle-xmark"></i> No live data — helmet offline</div>';

        const row = document.createElement('tr');
        row.innerHTML = '<td>' + esc(new Date().toLocaleTimeString()) + '</td><td>Connection Lost</td>' +
          '<td><span class="badge badge-alert">OFFLINE</span></td><td>No data received</td>';
        const table = $('hEventLogTable');
        table.insertBefore(row, table.firstChild);
        if (table.children.length > 5) table.removeChild(table.lastChild);
      }

      // ---------- renderers ----------
      function renderActiveAlerts(list) {
        setText('hActiveAlertCount', String(list.length));
        const box = $('hAlertBox');
        if (!list.length) {
          box.innerHTML = '<div style="font-size: 10px; color: var(--text-muted); text-align: center; padding: 15px 0;">No active hazardous alerts</div>';
          return;
        }
        box.innerHTML = list.map((a) => `
          <div class="active-alert-item">
            <i class="fa-solid ${a.icon} active-alert-icon"></i>
            <div class="active-alert-text">
              <div class="active-alert-title">${esc(a.title)}</div>
              <div class="active-alert-detail">${esc(a.detail)}</div>
            </div>
          </div>
        `).join('');
      }

      function notifyNew(list) {
        const titles = new Set(list.map((a) => a.title));
        if ('Notification' in window && Notification.permission === 'granted') {
          const logo = document.querySelector('.brand-icon img');
          const icon = logo ? logo.src : undefined;
          list.forEach((a) => {
            if (lastNotified.has(a.title)) return;
            const critical = a.key === 'gas' || a.key === 'hr' || a.key === 'spo2';
            const opts = {
              body: a.title, tag: 'helmet-' + a.title, renotify: true, icon, badge: icon,
              vibrate: [200, 100, 200, 100, 200], requireInteraction: critical
            };
            const fallback = () => { try { new Notification('⚠️ SafeGuard Helmet Alert', opts); } catch (e) {} };
            if ('serviceWorker' in navigator && navigator.serviceWorker.getRegistration) {
              navigator.serviceWorker.getRegistration().then((reg) => {
                if (reg && reg.showNotification) reg.showNotification('⚠️ SafeGuard Helmet Alert', opts).catch(fallback);
                else fallback();
              }).catch(fallback);
            } else fallback();
          });
        }
        lastNotified = titles;
      }

      function processHelmet(d) {
        if (!d || typeof d !== 'object') return;
        if (!hConnected) setConnectedUi();

        const p = normalize(d);
        const timeStr = new Date().toLocaleTimeString();
        setText('hLastSync', timeStr);

        // --- debounce abnormal vitals (the helmet itself waits ~3 s too) ---
        hHrBadCount = hrBad(p) ? hHrBadCount + 1 : 0;
        hSpo2BadCount = spo2Bad(p) ? hSpo2BadCount + 1 : 0;
        const health = { hr: hHrBadCount >= HEALTH_CONFIRM, spo2: hSpo2BadCount >= HEALTH_CONFIRM };

        // --- heart rate ---
        if (p.hr > 0) {
          setText('hHrVal', String(Math.round(p.hr)));
          if (health.hr) setStatus('hHrStatus', p.hr < TH.HR_MIN ? 'Low Heart Rate' : 'High Heart Rate', 'var(--red)');
          else if (hrBad(p)) setStatus('hHrStatus', 'Checking...', 'var(--amber)');
          else setStatus('hHrStatus', 'Normal', 'var(--green)');
        } else {
          setText('hHrVal', '--');
          setStatus('hHrStatus', 'No finger / no signal', 'var(--text-muted)');
        }
        updateSparkline('hHrSpark', p.hr > 0 ? p.hr : null);
        pushLiveChartPoint(hrChart, timeStr, p.hr > 0 ? p.hr : null);

        // --- SpO2 ---
        if (p.spo2 > 0) {
          setText('hSpo2Val', String(Math.round(p.spo2)));
          if (health.spo2) setStatus('hSpo2Status', 'Low SpO₂', 'var(--red)');
          else if (spo2Bad(p)) setStatus('hSpo2Status', 'Checking...', 'var(--amber)');
          else setStatus('hSpo2Status', 'Normal', 'var(--green)');
        } else {
          setText('hSpo2Val', '--');
          setStatus('hSpo2Status', 'No finger / no signal', 'var(--text-muted)');
        }
        updateSparkline('hSpo2Spark', p.spo2 > 0 ? p.spo2 : null);
        pushLiveChartPoint(spo2Chart, timeStr, p.spo2 > 0 ? p.spo2 : null);

        // --- gas ---
        setText('hGasVal', String(Math.round(p.gasLevel)));
        if (p.gasBase <= 0) {
          setStatus('hGasStatus', 'Warming up...', 'var(--amber)');
          setText('hGasRange', 'Calibrating clean-air baseline (~30 s)');
        } else {
          setStatus('hGasStatus', p.gasFlag ? 'GAS DETECTED' : 'Safe', p.gasFlag ? 'var(--red)' : 'var(--green)');
          setText('hGasRange', 'Baseline ' + p.gasBase + ' · Alert above ' + (p.gasBase + TH.GAS_MARGIN));
        }
        updateSparkline('hGasSpark', p.gasLevel);
        pushPoints(gasChart, timeStr, [p.gasLevel, p.gasBase > 0 ? p.gasBase + TH.GAS_MARGIN : null]);

        // --- obstacle / proximity ---
        const hitSides = SIDES.filter((s) => p.obst[s.key]);
        const inRange = SIDES.map((s) => p.dist[s.key]).filter((v) => v < TH.NO_ECHO);
        const nearest = inRange.length ? Math.min.apply(null, inRange) : null;
        if (nearest !== null) { setText('hObstVal', String(nearest)); setText('hObstUnit', 'cm'); }
        else { setText('hObstVal', 'CLEAR'); setText('hObstUnit', ''); }
        if (hitSides.length) setStatus('hObstStatus', 'OBSTACLE — ' + hitSides.map((s) => s.name).join(', '), 'var(--red)');
        else if (p.obstFlag) setStatus('hObstStatus', 'OBSTACLE', 'var(--red)');
        else setStatus('hObstStatus', 'Path Clear', 'var(--green)');

        SIDES.forEach((s) => {
          const d1 = p.dist[s.key];
          const clear = d1 >= TH.NO_ECHO;
          const hit = p.obst[s.key];
          const b = $('hBadge' + s.key);
          b.className = 'badge ' + (hit ? 'badge-alert' : 'badge-safe');
          b.textContent = s.key + ' · ' + (clear ? 'CLEAR' : d1 + ' cm');
          const pct = clear ? 0 : Math.max(0, Math.min(100, (1 - d1 / PROX_SCALE_CM) * 100));
          $('hProxFill' + s.key).style.width = pct + '%';
          $('hProx' + s.key).classList.toggle('hit', hit);
          setText('hProxVal' + s.key, clear ? 'CLEAR' : d1 + ' cm');
        });
        const cap = (v) => (v >= TH.NO_ECHO ? 110 : v);
        pushPoints(distChart, timeStr, [cap(p.dist.L), cap(p.dist.F), cap(p.dist.R), TH.OBST_CM]);

        // --- device rows ---
        setStatus('hStPulse', p.hr > 0 ? 'Reading' : 'No finger', p.hr > 0 ? 'var(--green)' : 'var(--amber)');
        setStatus('hStGas', p.gasBase > 0 ? 'Ready' : 'Warming up', p.gasBase > 0 ? 'var(--green)' : 'var(--amber)');

        // --- alerts ---
        const alerts = evaluateAlerts(p, health);
        const isHazard = alerts.length > 0;
        renderActiveAlerts(alerts);
        notifyNew(alerts);
        if (isHazard && typeof playEmergencySound === 'function') playEmergencySound();

        const sysCard = $('hSysStatusText').closest('.system-card');
        sysCard.classList.toggle('h-alert', isHazard);
        setText('hSysStatusText', isHazard ? 'HELMET ALERT' : 'HELMET NORMAL');

        // --- event history ---
        const row = document.createElement('tr');
        row.innerHTML = `
          <td>${esc(timeStr)}</td>
          <td>${isHazard ? 'Hazard Alert' : 'Sync'}</td>
          <td><span class="badge ${isHazard ? 'badge-alert' : 'badge-safe'}">${isHazard ? 'ALERT' : 'SAFE'}</span></td>
          <td>HR ${p.hr > 0 ? esc(Math.round(p.hr)) : '--'} | SpO₂ ${p.spo2 > 0 ? esc(Math.round(p.spo2)) : '--'} | Gas ${esc(Math.round(p.gasLevel))}</td>
        `;
        const table = $('hEventLogTable');
        table.insertBefore(row, table.firstChild);
        if (table.children.length > 5) table.removeChild(table.lastChild);

        recordHistory(d);
      }

      function handleSnapshot(data) {
        if (!data) return;
        const raw = JSON.stringify(data);
        if (raw === hLastRaw) return;
        hLastRaw = raw;
        hLastUpdate = Date.now();
        hOffline = false;
        processHelmet(data);
      }

      function showAccessDenied() {
        if (hAccessDenied) return;
        hAccessDenied = true;
        setPill('offline', 'HELMET NO ACCESS');
        $('hSysIcon').innerHTML = '<i class="fa-solid fa-lock"></i>';
        setText('hSysStatusText', 'ACCESS DENIED');
        setText('hSysSubText', 'FIREBASE RULES BLOCK /helmet1');
        setText('hSysNote', "Add read permission for 'helmet1' in Realtime Database Rules.");
      }

      // ---------- Firebase listeners ----------
      db.ref(LIVE_PATH).on('value', (snap) => {
        handleSnapshot(snap.val());
      }, (err) => {
        if (err && /permission/i.test(String(err.code || err.message))) showAccessDenied();
      });

      setInterval(() => {
        if (hAccessDenied) return;
        db.ref(LIVE_PATH).once('value').then((snap) => {
          if (snap.exists()) handleSnapshot(snap.val());
        }).catch(() => {});
      }, 2000);

      setInterval(() => {
        if (hConnected && !hOffline && (Date.now() - hLastUpdate > HELMET_STALE_MS)) goOffline();
        if (hConnected && !hOffline) {
          setText('hDataAge', Math.max(0, Math.round((Date.now() - hLastUpdate) / 1000)) + ' s ago');
        }
      }, 1000);

      // ---------- sidebar link ----------
      window.scrollToHelmet = function (e) {
        if (e) { e.preventDefault(); e.stopPropagation(); }
        const top = sectionEl.getBoundingClientRect().top + window.pageYOffset - (window.innerWidth <= 768 ? 12 : 18);
        window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
        document.querySelectorAll('.nav-item').forEach((li) => li.classList.remove('active'));
        const link = document.getElementById('helmetNavItem');
        if (link) link.classList.add('active');
        if (window.innerWidth <= 768 && typeof sidebar !== 'undefined') sidebar.classList.remove('open');
      };
      if ('IntersectionObserver' in window) {
        new IntersectionObserver((entries) => {
          if (entries.some((en) => en.isIntersecting)) {
            document.querySelectorAll('.nav-item').forEach((li) => li.classList.remove('active'));
            const link = document.getElementById('helmetNavItem');
            if (link) link.classList.add('active');
          }
        }, { rootMargin: '-18% 0px -62% 0px', threshold: [0.05] }).observe(sectionEl);
      }

      // ---------- rolling history + Excel export ----------
      function recordHistory(data) {
        const flat = {};
        flattenToObject(data, '', flat);
        const last = hHistory[hHistory.length - 1];
        if (last && JSON.stringify(last.fields) === JSON.stringify(flat)) return;
        hHistory.push({ timestamp: new Date().toLocaleString(), fields: flat });
        if (hHistory.length > MAX_H_HISTORY) hHistory.shift();
      }

      window.exportHelmetData = function () {
        if (!hHistory.length) {
          alert('এক্সপোর্ট করার জন্য এখনো হেলমেটের কোনো ডাটা রেকর্ড হয়নি।\nপেজটি একটু সময় ধরে খোলা রাখুন যাতে সাম্প্রতিক রিডিং জমা হতে পারে, তারপর আবার চেষ্টা করুন।');
          return;
        }
        const maxAvailable = hHistory.length;
        const defaultCount = Math.min(10, maxAvailable);
        const input = prompt('সাম্প্রতিক কতগুলো রিডিং এক্সপোর্ট করতে চান? (এখন পর্যন্ত সর্বোচ্চ ' + maxAvailable + 'টি জমা আছে)', String(defaultCount));
        if (input === null) return;
        let count = parseInt(input, 10);
        if (isNaN(count) || count <= 0) count = defaultCount;
        count = Math.min(count, maxAvailable);

        const btn = $('hExportBtn');
        const originalHtml = btn.innerHTML;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Exporting...';
        btn.disabled = true;
        try {
          const entries = hHistory.slice(-count);
          const labels = [];
          entries.forEach((en) => Object.keys(en.fields).forEach((l) => { if (!labels.includes(l)) labels.push(l); }));
          const rows = entries.map((en) => [en.timestamp, ...labels.map((l) => (en.fields[l] !== undefined ? en.fields[l] : ''))]);
          const sheet = XLSX.utils.aoa_to_sheet([
            ['Helmet ID', 'HELMET-001'],
            ['Worker ID', 'SV-001'],
            ['Exported At', new Date().toLocaleString()],
            ['Records Exported', String(entries.length)],
            [],
            ['Timestamp', ...labels],
            ...rows
          ]);
          sheet['!cols'] = [{ wch: 20 }, ...labels.map(() => ({ wch: 16 }))];
          const wb = XLSX.utils.book_new();
          XLSX.utils.book_append_sheet(wb, sheet, 'Helmet1 History');
          XLSX.writeFile(wb, 'helmet1_history_' + new Date().toISOString().replace(/[:.]/g, '-') + '.xlsx');
        } catch (err) {
          alert('এক্সপোর্ট তৈরি করতে ব্যর্থ হয়েছে:\n' + err.message);
        } finally {
          btn.innerHTML = originalHtml;
          btn.disabled = false;
        }
      };

      // =========================================================
      // Reports + Alerts windows: VEST / HELMET switch
      // =========================================================
      const reportsBodyEl = $('reportsBody');
      const alertsBodyEl = $('alertsBody');
      const reportsModalEl = $('reportsModal');
      const alertsModalEl = $('alertsModal');

      function rangeStart(range) {
        const day = 24 * 60 * 60 * 1000;
        return Date.now() - (range === 'daily' ? day : range === 'weekly' ? 7 * day : 30 * day);
      }

      function fetchLogs(range) {
        return db.ref(LOGS_PATH).orderByChild('timestamp').startAt(rangeStart(range))
          .limitToLast(MAX_REPORT_ENTRIES).once('value').then((snap) => {
            const entries = [];
            snap.forEach((child) => {
              const v = child.val();
              if (v && typeof v.timestamp === 'number') entries.push({ ts: v.timestamp, p: normalize(v) });
            });
            entries.sort((a, b) => a.ts - b.ts);
            return { entries, capped: snap.numChildren() >= MAX_REPORT_ENTRIES };
          });
      }

      function syncSourceButtons(container, src) {
        container.querySelectorAll('.src-switch .hm-tab').forEach((b) => b.classList.toggle('active', b.dataset.src === src));
      }
      function resetOnClose(modalEl, bodyEl) {
        if (!modalEl || !bodyEl) return;
        new MutationObserver(() => {
          if (!modalEl.classList.contains('active')) {
            bodyEl.classList.remove('show-helmet');
            syncSourceButtons(bodyEl, 'vest');
          }
        }).observe(modalEl, { attributes: true, attributeFilter: ['class'] });
      }
      resetOnClose(reportsModalEl, reportsBodyEl);
      resetOnClose(alertsModalEl, alertsBodyEl);

      // ---------- Reports ----------
      let hReportRange = 'daily';
      let hReportEntries = [];
      let hReportOffline = [];
      const hCharts = { vitals: null, gas: null, alerts: null };

      window.setReportsSource = function (src) {
        if (!reportsBodyEl) return;
        reportsBodyEl.classList.toggle('show-helmet', src === 'helmet');
        syncSourceButtons(reportsBodyEl, src);
        if (src === 'helmet') loadHelmetReport(hReportRange);
      };
      window.setHelmetReportRange = function (range) {
        hReportRange = range;
        document.querySelectorAll('#helmetReportPane .hm-tab').forEach((b) => b.classList.toggle('active', b.dataset.hrange === range));
        loadHelmetReport(range);
      };

      function offlineEvents(entries) {
        const events = [];
        for (let i = 1; i < entries.length; i++) {
          const gap = entries[i].ts - entries[i - 1].ts;
          if (gap > OFFLINE_GAP_MS) events.push({ from: entries[i - 1].ts, to: entries[i].ts, durationMs: gap, ongoing: false });
        }
        if (entries.length) {
          const lastTs = entries[entries.length - 1].ts;
          const now = Date.now();
          if (now - lastTs > OFFLINE_GAP_MS) events.push({ from: lastTs, to: now, durationMs: now - lastTs, ongoing: true });
        }
        return events;
      }
      const fmtT = (ts) => new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
      function fmtDur(ms) {
        const m = Math.round(ms / 60000);
        if (m < 1) return '< ১ মিনিট';
        if (m < 60) return m + ' মিনিট';
        const h = Math.floor(m / 60), r = m % 60;
        return h + ' ঘণ্টা' + (r ? ' ' + r + ' মিনিট' : '');
      }

      function loadHelmetReport(range) {
        const summaryEl = $('hReportSummaryCards');
        const chartsWrap = $('hReportChartsWrap');
        const msgEl = $('hReportStateMsg');
        const exportBtn = $('hReportExportBtn');
        msgEl.style.display = 'block';
        msgEl.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Loading helmet report...';
        chartsWrap.style.display = 'none';
        summaryEl.innerHTML = '';
        exportBtn.disabled = true;

        fetchLogs(range).then(({ entries, capped }) => {
          hReportEntries = entries;
          hReportOffline = offlineEvents(entries);
          if (!entries.length) {
            msgEl.style.display = 'block';
            msgEl.innerHTML = 'এই সময়সীমার জন্য হেলমেটের কোনো রিপোর্ট ডাটা জমা হয়নি।<br><span style="font-size:10.5px;">হেলমেট চালু থাকলে ও ডাটা পাঠালে এখানে স্বয়ংক্রিয়ভাবে হিস্ট্রি জমা হবে।</span>';
            renderConnectivity(hReportOffline);
            return;
          }
          msgEl.style.display = capped ? 'block' : 'none';
          if (capped) msgEl.innerHTML = '<span style="font-size:10.5px;">সর্বশেষ ' + MAX_REPORT_ENTRIES + 'টি রেকর্ড দেখানো হচ্ছে।</span>';
          chartsWrap.style.display = 'block';
          exportBtn.disabled = false;
          renderReportSummary(entries, hReportOffline);
          renderReportCharts(entries, range);
          renderConnectivity(hReportOffline);
        }).catch((err) => {
          msgEl.style.display = 'block';
          msgEl.textContent = 'হেলমেট রিপোর্ট লোড করতে ব্যর্থ হয়েছে: ' + err.message;
          chartsWrap.style.display = 'none';
        });
      }

      function renderConnectivity(events) {
        const wrap = $('hReportConnectivityWrap');
        if (!events.length) {
          wrap.innerHTML = '<div class="report-connectivity-title"><i class="fa-solid fa-tower-broadcast"></i> CONNECTIVITY</div>' +
            '<div class="offline-empty">এই সময়সীমায় কোনো সংযোগ বিচ্ছিন্নতা পাওয়া যায়নি — হেলমেট সবসময় ডাটা পাঠিয়েছে।</div>';
          return;
        }
        const items = events.slice().reverse().map((e) => `
          <div class="offline-event-item ${e.ongoing ? 'offline-event-live' : ''}">
            <i class="fa-solid ${e.ongoing ? 'fa-triangle-exclamation' : 'fa-plug-circle-xmark'}"></i>
            <div class="offline-event-text">
              <div>${e.ongoing ? 'বর্তমানে অফলাইন' : 'অফলাইন হয়েছিল'} — ${fmtT(e.from)}</div>
              <div class="offline-event-sub">${e.ongoing ? 'শেষ ডাটা পাওয়া গিয়েছিল এই সময়ে' : 'সংযোগ ফিরেছে: ' + fmtT(e.to)} &middot; স্থায়িত্ব: ${fmtDur(e.durationMs)}</div>
            </div>
          </div>`).join('');
        wrap.innerHTML = '<div class="report-connectivity-title"><i class="fa-solid fa-tower-broadcast"></i> CONNECTIVITY (' + events.length + ')</div>' +
          '<div class="offline-event-list">' + items + '</div>';
      }

      const avg = (a) => (a.length ? a.reduce((x, y) => x + y, 0) / a.length : null);

      function renderReportSummary(entries, offline) {
        const hrs = entries.map((e) => e.p.hr).filter((v) => v > 0);
        const sps = entries.map((e) => e.p.spo2).filter((v) => v > 0);
        const gases = entries.map((e) => e.p.gasLevel);
        const f1 = (v, u) => (v === null ? '--' : v.toFixed(1) + u);
        const cards = [
          { label: 'Records', value: String(entries.length) },
          { label: 'Avg Heart Rate', value: f1(avg(hrs), ' bpm') },
          { label: 'HR Min – Max', value: hrs.length ? Math.min.apply(null, hrs) + ' – ' + Math.max.apply(null, hrs) : '--' },
          { label: 'Avg SpO₂', value: f1(avg(sps), '%') },
          { label: 'Min SpO₂', value: sps.length ? Math.min.apply(null, sps) + '%' : '--' },
          { label: 'Peak Gas Level', value: gases.length ? String(Math.max.apply(null, gases)) : '--' },
          { label: 'Obstacle Snapshots', value: String(entries.filter((e) => anyObst(e.p)).length) },
          { label: 'Gas Snapshots', value: String(entries.filter((e) => e.p.gasFlag).length) },
          { label: 'Vitals Snapshots', value: String(entries.filter((e) => hrBad(e.p) || spo2Bad(e.p)).length) },
          { label: 'Offline Events', value: String(offline.length) }
        ];
        $('hReportSummaryCards').innerHTML = cards.map((c) => `
          <div class="report-summary-card">
            <div class="rsc-label">${c.label}</div>
            <div class="rsc-value">${c.value}</div>
          </div>`).join('');
      }

      function chartOpts(stacked) {
        return {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { labels: { color: '#8493a8', font: { size: 9 } } } },
          scales: {
            x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 }, maxTicksLimit: 12 }, stacked: !!stacked },
            y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#8493a8', font: { size: 8 } }, stacked: !!stacked, beginAtZero: !!stacked }
          }
        };
      }

      // Groups sorted entries (by bucket index for daily, by day for longer
      // ranges) and reduces each group to one chart point.
      function aggregate(entries, keyFn) {
        const groups = [];
        const index = new Map();
        entries.forEach((e, i) => {
          const k = keyFn(e, i);
          let g = index.get(k);
          if (!g) { g = { ts: e.ts, hr: [], sp: [], gas: 0, thr: null, obst: 0, gasN: 0, vit: 0 }; index.set(k, g); groups.push(g); }
          if (e.p.hr > 0) g.hr.push(e.p.hr);
          if (e.p.spo2 > 0) g.sp.push(e.p.spo2);
          if (e.p.gasLevel > g.gas) g.gas = e.p.gasLevel;
          if (e.p.gasBase > 0) g.thr = e.p.gasBase + TH.GAS_MARGIN;
          if (anyObst(e.p)) g.obst++;
          if (e.p.gasFlag) g.gasN++;
          if (hrBad(e.p) || spo2Bad(e.p)) g.vit++;
        });
        const r1 = (v) => (v === null ? null : Math.round(v * 10) / 10);
        return groups.map((g) => ({ ts: g.ts, hr: r1(avg(g.hr)), sp: r1(avg(g.sp)), gas: g.gas, thr: g.thr, obst: g.obst, gasN: g.gasN, vit: g.vit }));
      }

      function renderReportCharts(entries, range) {
        Object.keys(hCharts).forEach((k) => { if (hCharts[k]) { hCharts[k].destroy(); hCharts[k] = null; } });
        let pts, labels;
        if (range === 'daily') {
          const bucket = Math.max(1, Math.ceil(entries.length / 720));
          pts = aggregate(entries, (e, i) => Math.floor(i / bucket));
          labels = pts.map((g) => new Date(g.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
        } else {
          const dayKey = (e) => new Date(e.ts).toLocaleDateString([], { month: 'short', day: 'numeric' });
          pts = aggregate(entries, dayKey);
          labels = pts.map((g) => dayKey(g));
        }
        const line = (label, data, color) => ({ label, data, borderColor: color, backgroundColor: color + '1a', tension: 0.3, pointRadius: range === 'daily' ? 0 : 3, borderWidth: 1.6, spanGaps: false });

        hCharts.vitals = new Chart($('hReportVitalsChart').getContext('2d'), {
          type: 'line',
          data: { labels, datasets: [line('Heart Rate (bpm)', pts.map((g) => g.hr), '#ff3366'), line('SpO₂ (%)', pts.map((g) => g.sp), '#00f2ff')] },
          options: chartOpts()
        });
        const gasLine = line((range === 'daily' ? 'Gas Level' : 'Peak Gas Level'), pts.map((g) => g.gas), '#f59e0b');
        gasLine.fill = true;
        hCharts.gas = new Chart($('hReportGasChart').getContext('2d'), {
          type: 'line',
          data: { labels, datasets: [gasLine, { label: 'Alert level', data: pts.map((g) => g.thr), borderColor: '#ff3366', borderDash: [5, 4], borderWidth: 1.2, pointRadius: 0, fill: false }] },
          options: chartOpts()
        });
        hCharts.alerts = new Chart($('hReportAlertChart').getContext('2d'), {
          type: 'bar',
          data: {
            labels,
            datasets: [
              { label: 'Obstacle', data: pts.map((g) => g.obst), backgroundColor: '#f97316' },
              { label: 'Gas', data: pts.map((g) => g.gasN), backgroundColor: '#f59e0b' },
              { label: 'Vitals', data: pts.map((g) => g.vit), backgroundColor: '#ff3366' }
            ]
          },
          options: chartOpts(true)
        });
      }

      window.exportHelmetReportData = function () {
        if (!hReportEntries.length) return;
        const btn = $('hReportExportBtn');
        const originalHtml = btn.innerHTML;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Exporting...';
        btn.disabled = true;
        try {
          const header = ['Timestamp', 'Heart Rate (bpm)', 'SpO2 (%)', 'Gas Level', 'Gas Baseline', 'Dist Left (cm)', 'Dist Front (cm)', 'Dist Right (cm)', 'Obstacle', 'Gas Alert', 'Vitals Alert'];
          const rows = hReportEntries.map((e) => [
            new Date(e.ts).toLocaleString(),
            e.p.hr > 0 ? e.p.hr : '', e.p.spo2 > 0 ? e.p.spo2 : '',
            e.p.gasLevel, e.p.gasBase,
            e.p.dist.L, e.p.dist.F, e.p.dist.R,
            anyObst(e.p) ? 'YES' : 'NO', e.p.gasFlag ? 'YES' : 'NO',
            (hrBad(e.p) || spo2Bad(e.p)) ? 'YES' : 'NO'
          ]);
          const sheet = XLSX.utils.aoa_to_sheet([
            ['Helmet ID', 'HELMET-001'], ['Worker ID', 'SV-001'],
            ['Report Range', hReportRange.toUpperCase()],
            ['Generated At', new Date().toLocaleString()],
            ['Records', String(rows.length)], [], header, ...rows
          ]);
          sheet['!cols'] = [{ wch: 20 }, ...header.slice(1).map(() => ({ wch: 15 }))];
          const wb = XLSX.utils.book_new();
          XLSX.utils.book_append_sheet(wb, sheet, 'helmet ' + hReportRange);
          if (hReportOffline.length) {
            const off = XLSX.utils.aoa_to_sheet([
              ['Offline Since', 'Reconnected / Status', 'Duration'],
              ...hReportOffline.map((e) => [new Date(e.from).toLocaleString(), e.ongoing ? 'Still offline' : new Date(e.to).toLocaleString(), fmtDur(e.durationMs)])
            ]);
            off['!cols'] = [{ wch: 20 }, { wch: 20 }, { wch: 14 }];
            XLSX.utils.book_append_sheet(wb, off, 'offline events');
          }
          XLSX.writeFile(wb, 'helmet1_' + hReportRange + '_report_' + new Date().toISOString().replace(/[:.]/g, '-') + '.xlsx');
        } catch (err) {
          alert('রিপোর্ট এক্সপোর্ট করতে ব্যর্থ হয়েছে:\n' + err.message);
        } finally {
          btn.innerHTML = originalHtml;
          btn.disabled = false;
        }
      };

      // ---------- Alerts window ----------
      let hAlertsRange = 'daily';

      window.setAlertsSource = function (src) {
        if (!alertsBodyEl) return;
        alertsBodyEl.classList.toggle('show-helmet', src === 'helmet');
        syncSourceButtons(alertsBodyEl, src);
        if (src === 'helmet') loadHelmetAlerts(hAlertsRange);
      };
      window.setHelmetAlertsRange = function (range) {
        hAlertsRange = range;
        document.querySelectorAll('#helmetAlertsPane .hm-tab').forEach((b) => b.classList.toggle('active', b.dataset.harange === range));
        loadHelmetAlerts(range);
      };

      function loadHelmetAlerts(range) {
        const summaryEl = $('hAlertsSummaryCards');
        const listEl = $('hAlertsListWrap');
        const msgEl = $('hAlertsStateMsg');
        msgEl.style.display = 'block';
        msgEl.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Loading helmet alerts...';
        summaryEl.innerHTML = '';
        listEl.innerHTML = '';

        fetchLogs(range).then(({ entries }) => {
          const counts = { obstacle: 0, gas: 0, hr: 0, spo2: 0 };
          const rows = [];
          entries.forEach((e) => {
            const types = evaluateAlerts(e.p, { hr: hrBad(e.p), spo2: spo2Bad(e.p) });
            if (types.length) {
              types.forEach((t) => { counts[t.key]++; });
              rows.push({ ts: e.ts, types });
            }
          });
          const cards = [
            { label: 'Total Alert Events', value: String(rows.length) },
            { label: 'Obstacle', value: String(counts.obstacle) },
            { label: 'Gas', value: String(counts.gas) },
            { label: 'Heart Rate', value: String(counts.hr) },
            { label: 'SpO₂', value: String(counts.spo2) }
          ];
          summaryEl.innerHTML = cards.map((c) => `
            <div class="report-summary-card">
              <div class="rsc-label">${c.label}</div>
              <div class="rsc-value">${c.value}</div>
            </div>`).join('');

          if (!rows.length) {
            msgEl.style.display = 'block';
            msgEl.innerHTML = 'এই সময়সীমায় হেলমেটের কোনো অ্যালার্ট পাওয়া যায়নি — সব রিডিং নিরাপদ সীমার মধ্যে ছিল।';
            return;
          }
          msgEl.style.display = 'none';
          listEl.innerHTML = rows.slice().reverse().map((r) => {
            const tags = r.types.map((t) => `<span class="alert-type-tag alert-type-${t.key}">${t.label}</span>`).join('');
            const details = r.types.map((t) => esc(t.detailBn)).join(' &middot; ');
            const when = new Date(r.ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
            return `
              <div class="alert-log-item">
                <i class="fa-solid fa-triangle-exclamation"></i>
                <div>
                  <div class="alert-log-time">${when}</div>
                  <div class="alert-log-tags">${tags}</div>
                  <div class="alert-log-detail">${details}</div>
                </div>
              </div>`;
          }).join('');
        }).catch((err) => {
          msgEl.style.display = 'block';
          msgEl.textContent = 'হেলমেট অ্যালার্ট লোড করতে ব্যর্থ হয়েছে: ' + err.message;
        });
      }
    })();
