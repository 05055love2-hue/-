// شوف — تنظيف الإعلانات وتشغيل بالخلفية • برمجة أبو نواف
(function () {
  if (window.__shoof) return; window.__shoof = 1;

  // 1) الصفحة تحسب نفسها ظاهرة دايم، عشان الفيديو ما يوقف لما تطلع من التطبيق
  try {
    Object.defineProperty(document, 'hidden', { get: function () { return false; }, configurable: true });
    Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; }, configurable: true });
    Object.defineProperty(document, 'webkitHidden', { get: function () { return false; }, configurable: true });
    var stop = function (e) { e.stopImmediatePropagation(); };
    document.addEventListener('visibilitychange', stop, true);
    document.addEventListener('webkitvisibilitychange', stop, true);
    window.addEventListener('pagehide', stop, true);
    window.addEventListener('blur', stop, true);
  } catch (e) {}

  // 2) نشيل بيانات الإعلانات من رد السيرفر قبل ما يستخدمها المشغّل
  var KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  var strip = function (o) {
    try {
      if (!o || typeof o !== 'object') return o;
      KEYS.forEach(function (k) { if (k in o) delete o[k]; });
      if (o.playerResponse) strip(o.playerResponse);
    } catch (e) {}
    return o;
  };
  try {
    var jp = JSON.parse;
    JSON.parse = function () { return strip(jp.apply(this, arguments)); };
    var rj = Response.prototype.json;
    Response.prototype.json = function () { return rj.apply(this, arguments).then(strip); };
    ['ytInitialPlayerResponse'].forEach(function (n) {
      var v = window[n];
      Object.defineProperty(window, n, { get: function () { return v; }, set: function (x) { v = strip(x); }, configurable: true });
      if (v) strip(v);
    });
  } catch (e) {}

  // 3) نخفي أماكن الإعلانات والدعايات في الصفحة
  var css = [
    'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
    'ad-slot-renderer', 'ytm-ad-slot-renderer', 'ytd-ad-slot-renderer', 'ytm-in-feed-ad-layout-renderer',
    'ytm-mealbar-promo-renderer', 'ytm-statement-banner-renderer', 'ytm-brand-video-singleton-renderer',
    '.ytp-ad-overlay-container', '.ytp-ad-module .ytp-ad-image-overlay', '#player-ads', '.masthead-ad',
    'ytm-companion-slot', 'ytm-promoted-sparkles-text-search-renderer', '[layout="display-ad-layout"]',
    'ytm-rich-item-renderer:has(ad-slot-renderer)', 'ytm-item-section-renderer:has(ytm-ad-slot-renderer)'
  ].join(',') + '{display:none!important}';
  var addCss = function () {
    if (document.getElementById('shoofCss')) return;
    var s = document.createElement('style'); s.id = 'shoofCss'; s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  };
  addCss();

  // 4) لو طلع إعلان فيديو رغم كل هذا: نكتمه ونقفز لآخره ونضغط تخطي
  setInterval(function () {
    try {
      addCss();
      var ad = document.querySelector('.ad-showing, .ad-interrupting');
      var v = document.querySelector('video');
      if (ad && v) {
        v.muted = true;
        v.playbackRate = 16;
        if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration - 0.1;
        v.__shoofMuted = true;
      } else if (v && v.__shoofMuted) {
        v.muted = false; v.playbackRate = 1; v.__shoofMuted = false;
      }
      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button[class*="skip-button"], [id^="skip-button"] button');
      if (skip) skip.click();
      var close = document.querySelector('.ytp-ad-overlay-close-button');
      if (close) close.click();
    } catch (e) {}
  }, 300);
})();
