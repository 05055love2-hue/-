
// ════════ الطبقة الحقيقية: تربط تصميم Antigravity بالجهاز • برمجة أبو نواف ════════
(function(){
const N = window.Native || {getApps(){return '[]'},openApp(){return false},openUrl(){},installed(){return ''},openAction(){return false},status(){return '{}'},volume(){return -1}};
const Y = window.YTN || {show(){},hide(){},back(){},home(){},toggle(){},seek(){},mute(){},full(){}};
const $ = s => document.querySelector(s);

/* ── حفظ الواجهة والشريط ── */
const save = () => { try { localStorage.dock = JSON.stringify(dockSlots); localStorage.theme = curTheme; } catch(e){} };
try {
  const d = JSON.parse(localStorage.dock || 'null'); if (Array.isArray(d)) dockSlots = d;
  if (localStorage.theme) curTheme = localStorage.theme;
} catch(e){}

/* ── تطبيقات الجهاز الحقيقية ── */
let devApps = [];
function loadDev(){ try { devApps = JSON.parse(N.getApps()).sort((a,b) => a.n.localeCompare(b.n, 'ar')); } catch(e){ devApps = []; } }
const PKG = {
  maps:'com.google.android.apps.maps,com.waze', waze:'com.waze', spotify:'com.spotify.music', whatsapp:'com.whatsapp,com.whatsapp.w4b',
  phone:'com.android.dialer,com.google.android.dialer,com.samsung.android.dialer',
  messages:'com.google.android.apps.messaging,com.samsung.android.messaging,com.android.mms',
  music:'com.anghami,com.spotify.music,com.google.android.apps.youtube.music,com.apple.android.music,com.android.music,com.miui.player',
  camera:'com.android.camera,com.android.camera2,com.google.android.GoogleCamera,com.sec.android.app.camera',
  clock:'com.google.android.deskclock,com.android.deskclock,com.sec.android.app.clockpackage',
  calculator:'com.google.android.calculator,com.android.calculator2,com.sec.android.app.popupcalculator',
  carplay:'com.zjinnova.zlink,com.suding.speedplay,com.autokit.autokit', radio:'com.android.fmradio,com.syu.radio,com.microntek.radio',
  obd:'org.prowl.torque,com.ovz.carscanner'
};
const _openAppPage = openAppPage;
openAppPage = function(id){
  if (String(id).startsWith('pkg:')) { if (!N.openApp(id.slice(4))) toast('ما قدرت أفتح التطبيق'); return; }
  if (id === 'settings') return _openAppPage(id);          // إعدادات اللانشر نفسه
  const p = PKG[id] && N.installed(PKG[id]);
  if (p) { N.openApp(p); return; }
  if (id === 'maps') { N.openUrl('https://www.google.com/maps'); return; }
  if (id === 'phone') { N.openUrl('tel:'); return; }
  _openAppPage(id);                                          // ما فيه تطبيق؟ نعرض صفحة التصميم
};

/* المودال: نضيف تطبيقات جهازك تحت تطبيقات التصميم */
const _openModal = openModal;
openModal = function(si){
  _openModal(si);
  if (!devApps.length) loadDev();
  const list = $('#app-list'); if (!list || !devApps.length) return;
  const h = document.createElement('div'); h.className = 'mu'; h.style.cssText = 'padding:14px 4px 6px;font-weight:700;font-size:13px;border-top:1px solid rgba(255,255,255,.08);margin-top:8px';
  h.textContent = '📱 تطبيقات جهازك'; list.appendChild(h);
  const used = dockSlots.filter(Boolean).map(a => a.id);
  devApps.forEach(a => {
    const id = 'pkg:' + a.pkg, u = used.includes(id), d = document.createElement('div'); d.className = 'ao'; if (u) d.style.opacity = '.4';
    d.innerHTML = '<img style="width:44px;height:44px;border-radius:11px;flex-shrink:0" alt=""><div style="flex:1;font-weight:700;font-size:14px"></div>';
    d.querySelector('img').src = a.img; d.querySelector('div').textContent = a.n;
    if (!u) d.onclick = () => { dockSlots[editSlot] = {id, name: a.n, icon: '', gradient: `center/82% no-repeat url(${a.img}), rgba(255,255,255,.06)`}; renderDock(); closeModal(); };
    list.appendChild(d);
  });
};

/* الشريط: ضغطة مطوّلة = تغيير التطبيق (الشاشة ما فيها ماوس) + حفظ */
const _renderDock = renderDock;
renderDock = function(){
  _renderDock(); save();
  document.querySelectorAll('#app-dock .da').forEach((d, i) => {
    let t; d.addEventListener('touchstart', () => { t = setTimeout(() => { d._long = true; openModal(i); }, 650); }, {passive: true});
    d.addEventListener('touchend', () => clearTimeout(t)); d.addEventListener('touchmove', () => clearTimeout(t));
    d.addEventListener('click', e => { if (d._long) { e.stopImmediatePropagation(); d._long = false; } }, true);
  });
};
const _renderMain = renderMain;
renderMain = function(){ _renderMain(); save(); fixYt(); applyStatus(); };

/* ── يوتيوب الحقيقي داخل الخانة ── */
function ytArea(){ const r = $('#ytReal'); if (r) return r; const v = $('#visualizer'); return v && v.parentElement; }
function fixYt(){
  const a = ytArea(); if (!a) return;
  if (!a._real) { a._real = true; a.id = 'ytReal'; a.innerHTML = ''; a.style.background = '#000'; }
  const t = $('#yt-title'); if (t) t.textContent = 'يوتيوب';
  const pf = $('#progress-fill'); const row = pf && pf.parentElement && pf.parentElement.parentElement;
  if (row && row.parentElement === a.parentElement) row.style.display = 'none';
}
togglePlay = () => Y.toggle();
nextTrack = () => Y.seek(10);
prevTrack = () => Y.seek(-10);
let last = '';

/* ── ضبط الواجهة على أي شاشة: نرسمها بارتفاع ثابت ونكبّرها/نصغّرها لين تعبّي الشاشة بالضبط ── */
const SIZES = {auto: 720, big: 620, mid: 720, small: 820};
function uiBase(){ try { return SIZES[localStorage.uiSize || 'auto'] || 720; } catch(e){ return 720; } }
const fitCss = document.createElement('style'); document.head.appendChild(fitCss);
function fit(){
  const H0 = uiBase(), s = innerHeight / H0, W = innerWidth / s;
  fitCss.textContent = `html{overflow:hidden;background:#0a0e1a}
  body{width:${W}px!important;height:${H0}px!important;transform:scale(${s});transform-origin:0 0;position:absolute!important;left:0;top:0;right:auto}
  .h-screen{height:${H0}px!important}.w-screen{width:${W}px!important}`;
  last = '';
}
addEventListener('resize', fit);
fit();

/* حجم الواجهة من إعدادات الشاشة */
if (typeof pDisp === 'function') {
  const _pDisp = pDisp;
  pDisp = function(){
    const cur = (() => { try { return localStorage.uiSize || 'auto'; } catch(e){ return 'auto'; } })();
    const b = (v, n) => `<button onclick="setUiSize('${v}')" style="flex:1;height:44px;border-radius:12px;border:1px solid ${cur===v?'#4f8cff':'rgba(255,255,255,.1)'};background:${cur===v?'rgba(79,140,255,.18)':'rgba(255,255,255,.05)'};color:${cur===v?'#4f8cff':'#e8eaf0'};font-weight:700;font-size:14px">${n}</button>`;
    return `<div class="glass-sm p-4" style="margin-bottom:14px"><div style="font-weight:700;font-size:15px;margin-bottom:4px">📐 حجم الواجهة على الشاشة</div><div class="mu" style="font-size:12px;margin-bottom:10px">لو شي طالع برا الشاشة اختر «صغير»، ولو الكلام صغير اختر «كبير»</div><div style="display:flex;gap:8px">${b('small','صغير')}${b('auto','تلقائي')}${b('big','كبير')}</div></div>` + _pDisp.apply(this, arguments);
  };
}
window.setUiSize = v => { try { localStorage.uiSize = v; } catch(e){} fit(); if (typeof stTab === 'function') { const t = document.querySelector('#st-tabs .tb.active'); if (t) stTab(t, 'display'); } };
function placeYt(){
  const a = ytArea();
  const blocked = !a || document.hidden || $('#page-overlay.show') || $('#app-modal.show');
  if (blocked) { if (last !== 'hide') { Y.hide(); last = 'hide'; } return; }
  const r = a.getBoundingClientRect(), d = devicePixelRatio || 1;
  if (r.width < 20 || r.height < 20) { if (last !== 'hide') { Y.hide(); last = 'hide'; } return; }
  const k = [r.left, r.top, r.width, r.height].map(v => Math.round(v * d));
  const key = k.join(',');
  if (key !== last) { Y.show('yt', k[0], k[1], k[2], k[3]); last = key; }
}
setInterval(placeYt, 300);
addEventListener('resize', () => { last = ''; setTimeout(placeYt, 60); });

/* ── السرعة من الـGPS ── */
simSpeed = () => {}; simRPM = () => {};
window.onGps = (la, lo, kmh, gps) => {
  if (!gps) return;
  speed = Math.max(0, Math.round(kmh < 0 ? 0 : kmh));
  document.querySelectorAll('[id=speed-val]').forEach(e => e.textContent = speed);
  if (curTheme === 'sport' && typeof animSportGauge === 'function') animSportGauge();
};
function noRpm(){ const e = $('#rpm-val'); if (e) e.textContent = '--'; const b = $('#rpm-bar'); if (b) b.style.width = '0%'; }

/* ── البلوتوث والواي فاي الحقيقية ── */
const _bt = toggleBT, _wf = toggleWifi;
function setBT(on){ btOn = !on; _bt(); }
function setWifi(on){ wifiOn = !on; _wf(); }
toggleBT = () => N.openAction('android.settings.BLUETOOTH_SETTINGS');
toggleWifi = () => N.openAction('android.settings.WIFI_SETTINGS');
function applyStatus(){
  let s = {}; try { s = JSON.parse(N.status()); } catch(e){}
  if (s.bOn !== undefined) setBT(!!s.bConn || !!s.bOn);
  if (s.wOn !== undefined) setWifi(!!s.wConn);
  noRpm();
}
setInterval(applyStatus, 4000);

/* ── زر الرجوع والرئيسية ── */
window.goBack = () => {
  if ($('#app-modal.show')) { closeModal(); return; }
  if ($('#page-overlay.show')) { closePage(); return; }
};
window.goHome = () => { if ($('#app-modal.show')) closeModal(); if ($('#page-overlay.show')) closePage(); };
window.onAppResume = () => { last = ''; applyStatus(); };

/* ── رسالة صغيرة ── */
function toast(m){ let t = $('#rtoast'); if (!t) { t = document.createElement('div'); t.id = 'rtoast'; t.style.cssText = 'position:fixed;bottom:110px;left:50%;transform:translateX(-50%);background:#fff;color:#111;font-weight:700;border-radius:20px;padding:10px 22px;z-index:99;display:none'; document.body.appendChild(t); }
  t.textContent = m; t.style.display = 'block'; clearTimeout(t._h); t._h = setTimeout(() => t.style.display = 'none', 2200); }

/* ── الحقوق ورقم النسخة ── */
const c = document.createElement('div');
c.style.cssText = 'position:fixed;left:10px;bottom:2px;font-size:10px;color:rgba(255,255,255,.35);z-index:50;pointer-events:none';
c.innerHTML = '<span>برمجة أبو نواف</span>';
document.body.appendChild(c);

setTimeout(() => { loadDev(); renderMain(); }, 50);
})();
