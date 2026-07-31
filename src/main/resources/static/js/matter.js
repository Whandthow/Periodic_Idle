// Тір 1: Колапс матерії (page-exchange) + Грейди / Break Infinity (page-upgrades_t1)

var matterState = {
  brokenInfinity: false,
  matterCollapses: 0,
  particles: { p: 0, n: 0, e: 0 },
  energyLog10: 0,
  energyCapLog10: 308,
  breakInfinityRequired: 10,
  collapseReady: false,
  autobuyEnabled: true
};
var selectedParticle = 'p';
var matterFetchInFlight = false;

var PARTICLE_NAMES = { p: 'Протон', n: 'Нейтрон', e: 'Електрон' };

async function fetchMatterInfo() {
  if (matterFetchInFlight) return;
  matterFetchInFlight = true;
  try {
    var res = await fetch('/api/matter-info/' + SAVE_ID);
    matterState = await res.json();
    if (typeof refreshTierLocks === 'function') refreshTierLocks();
    if (typeof renderAutobuyToggle === 'function') renderAutobuyToggle();
    if (isPageActive('exchange')) renderMatterPage();
    if (isPageActive('upgrades_t1')) renderMatterUpgrades();
  } finally {
    matterFetchInFlight = false;
  }
}

function selectParticle(code) {
  selectedParticle = code;
  renderMatterPage();
}

function renderMatterPage() {
  var intro = document.getElementById('matter-intro');
  var particlesEl = document.getElementById('matter-particles');
  var btn = document.getElementById('matter-collapse-btn');
  if (!intro || !particlesEl || !btn) return;

  if (matterState.collapseReady) {
    intro.innerHTML = 'Енергія досягла межі! Обери частинку і сколапсуй — ' +
      'це скине Тір 0 (енергію й генератори) та додасть +1 обраної частинки.' +
      '<div class="matter-intro-sub">Колапсів виконано: ' + matterState.matterCollapses + '</div>';
  } else {
    var curExp = Math.floor(matterState.energyLog10 || 0);
    intro.innerHTML = 'Досягни 1e' + matterState.energyCapLog10 + ' енергії, щоб зробити колапс матерії.' +
      '<div class="matter-intro-sub">Поточна енергія: ~1e' + curExp + '</div>';
  }

  particlesEl.innerHTML = ['p', 'n', 'e'].map(function(code) {
    var count = (matterState.particles && matterState.particles[code]) || 0;
    var isSelected = code === selectedParticle;
    return '<div class="matter-particle' + (isSelected ? ' selected' : '') + '" onclick="selectParticle(\'' + code + '\')">' +
      '<img src="' + pngPath(ICONS[code]) + '" alt="" width="40" height="40" loading="lazy" decoding="async">' +
      '<div class="matter-particle-name">' + PARTICLE_NAMES[code] + '</div>' +
      '<div class="matter-particle-count">' + count + '</div>' +
    '</div>';
  }).join('');

  btn.disabled = !matterState.collapseReady;
}

async function doMatterCollapse() {
  var msg = document.getElementById('matter-msg');
  if (msg) msg.textContent = '';
  try {
    var res = await fetch('/api/matter-collapse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, particle: selectedParticle })
    });
    if (!res.ok) {
      var txt = await res.text();
      var errMsg = txt;
      try { var p = JSON.parse(txt); errMsg = p.error || p.message || txt; } catch (_) {}
      if (msg) msg.textContent = errMsg;
      return;
    }
    if (msg) msg.textContent = 'Колапс виконано!';
    await Promise.all([fetchState(), fetchGenerators(), fetchMatterInfo()]);
  } catch (e) {
    console.error('doMatterCollapse failed', e);
    if (msg) msg.textContent = 'Помилка: ' + e;
  }
}

function renderMatterUpgrades() {
  var container = document.getElementById('matter-upgrades');
  var msg = document.getElementById('matter-upg-msg');
  if (!container) return;

  var required = matterState.breakInfinityRequired || 0;
  var done = matterState.matterCollapses || 0;
  var ready = matterState.brokenInfinity || done >= required;

  container.innerHTML =
    '<div class="matter-upg-card' + (matterState.brokenInfinity ? ' done' : '') + '">' +
      '<div class="matter-upg-title">Зламати нескінченність</div>' +
      '<div class="matter-upg-desc">Знімає кап 1e' + matterState.energyCapLog10 + ' енергії — дозволяє рости далі без обмеження.</div>' +
      '<div class="matter-upg-status">' +
        (matterState.brokenInfinity ? 'Виконано' : 'Колапсів: ' + done + ' / ' + required) +
      '</div>' +
      (matterState.brokenInfinity ? '' :
        '<div class="matter-actions">' +
          '<button class="gen-buy-all-btn" ' + (ready ? '' : 'disabled') + ' onclick="doBreakInfinity()">Зламати нескінченність</button>' +
        '</div>') +
    '</div>';

  if (msg) msg.textContent = '';
}

async function doBreakInfinity() {
  var msg = document.getElementById('matter-upg-msg');
  try {
    var res = await fetch('/api/break-infinity', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID })
    });
    if (!res.ok) {
      var txt = await res.text();
      var errMsg = txt;
      try { var p = JSON.parse(txt); errMsg = p.error || p.message || txt; } catch (_) {}
      if (msg) msg.textContent = errMsg;
      return;
    }
    if (msg) msg.textContent = 'Нескінченність зламано!';
    await fetchMatterInfo();
    if (typeof refreshTierLocks === 'function') refreshTierLocks();
  } catch (e) {
    console.error('doBreakInfinity failed', e);
    if (msg) msg.textContent = 'Помилка: ' + e;
  }
}
