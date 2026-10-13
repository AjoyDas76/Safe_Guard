/* Mobile bottom navigation: tab clicks, full-page panels and scroll-spy highlight. */
(function () {
  'use strict';
  var mq = window.matchMedia('(max-width: 768px)');
  var nav = document.getElementById('bottomNav');
  if (!nav) return;
  var items = {};
  [].forEach.call(nav.querySelectorAll('.bn-item'), function (b) { items[b.dataset.nav] = b; });

  var PANELS = {
    alerts:  { el: 'alertsModal',   open: 'openAlertsModal',   close: 'closeAlertsModal' },
    map:     { el: 'trackingModal', open: 'openTrackingModal', close: 'closeTrackingModal' },
    reports: { el: 'reportsModal',  open: 'openReportsModal',  close: 'closeReportsModal' }
  };

  function $(id) { return document.getElementById(id); }
  function openPanelKey() {
    for (var k in PANELS) { var e = $(PANELS[k].el); if (e && e.classList.contains('active')) return k; }
    return null;
  }
  function closePanels(except) {
    for (var k in PANELS) {
      if (k === except) continue;
      var e = $(PANELS[k].el);
      if (e && e.classList.contains('active') && typeof window[PANELS[k].close] === 'function') window[PANELS[k].close]();
    }
  }
  function setActive(key) {
    for (var k in items) items[k].classList.toggle('active', k === key);
  }

  // ---- section anchors (resolved lazily; elements exist in the static HTML) ----
  function anchors() {
    var chart = document.querySelector('.col-flex-chart');
    var mapCard = document.querySelector('.map-card-large');
        return {
      cards:  document.querySelector('.sensor-grid'),
      charts: chart && chart.closest('.row'),
      others: mapCard && mapCard.closest('.row')   // Live Satellite Tracking ... Active Alerts + footer
    };
  }

  function sectionFromScroll() {
    var a = anchors(), probe = window.innerHeight * 0.4, cur = 'home';
    ['cards', 'charts', 'others'].forEach(function (k) {
      if (a[k] && a[k].getBoundingClientRect().top <= probe) cur = k;
    });
    return cur;
  }

  var ticking = false;
  function refresh() {
    ticking = false;
    if (!mq.matches || !document.body.classList.contains('authed')) return;
    setActive(openPanelKey() || sectionFromScroll());
  }
  function schedule() { if (!ticking) { ticking = true; requestAnimationFrame(refresh); } }

  window.addEventListener('scroll', schedule, { passive: true });
  window.addEventListener('resize', schedule);
  window.addEventListener('load', schedule);
  if (mq.addEventListener) mq.addEventListener('change', function () { if (!mq.matches) closePanels(null); schedule(); });

  var mo = new MutationObserver(schedule);
  Object.keys(PANELS).forEach(function (k) {
    var e = $(PANELS[k].el);
    if (e) mo.observe(e, { attributes: true, attributeFilter: ['class'] });
  });

  function goTo(target, top) {
    closePanels(null);
    var y = 0;
    if (target) y = Math.max(0, target.getBoundingClientRect().top + window.pageYOffset - 10);
    window.scrollTo({ top: top ? 0 : y, behavior: 'smooth' });
  }

  nav.addEventListener('click', function (ev) {
    var btn = ev.target.closest('.bn-item');
    if (!btn) return;
    var k = btn.dataset.nav, a = anchors();
    if (k === 'home') { goTo(null, true); setActive('home'); return; }
    if (k === 'cards' || k === 'charts' || k === 'others') { goTo(a[k]); setActive(k); return; }
    var p = PANELS[k];
    closePanels(k);
    var e = $(p.el);
    if (e && !e.classList.contains('active') && typeof window[p.open] === 'function') window[p.open]();
    setActive(k);
  });

  new MutationObserver(schedule).observe(document.body, { attributes: true, attributeFilter: ['class'] });
  schedule();
})();
