// Тір 1: Колапс матерії (page-exchange) + Грейди / Break Infinity (page-upgrades_t1)

var matterState = {
  brokenInfinity: false,
  matterCollapses: 0,
  particles: { p: 0, n: 0, e: 0 },
  energyLog10: 0,
  energyCapLog10: 308,
  breakInfinityRequired: 10,
  collapseReady: false,
  autobuyEnabled: true,
  autoUpgradeEnabled: false,
  autoUpgradeUnlockCollapses: 4,
  autoUpgradeUnlocked: false,
  protonEnergyMult: 1,
  neutronCostReduction: 0,
  electronCrystalMult: 1,
  cycleBoost: 1,
  vcPersistsAfterCollapses: 10000
};
var selectedParticle = 'p';
var matterFetchInFlight = false;

var PARTICLE_NAMES = { p: 'Протон', n: 'Нейтрон', e: 'Електрон' };

// Формат бусту циклу колапсів — може бути 1.0 або астрономічно великим.
function fmtCycleBoost(v) {
  if (v == null || !isFinite(v) || v <= 0) return '1';
  if (v < 1000) return v.toFixed(2);
  var exp = Math.floor(Math.log10(v));
  return (v / Math.pow(10, exp)).toFixed(2) + 'e' + exp;
}

// Що саме дає кожна частинка — щоб не доводилось здогадуватись (розділ 7.1 CLAUDE.md).
// Значення рахуються на бекенді (ParticleBonus), тут лише форматуємо текст.
function _particleEffectText(code) {
  if (code === 'p') {
    var pPct = Math.round((matterState.protonEnergyMult - 1) * 100);
    return '+' + pPct + '% до енергії';
  }
  if (code === 'n') {
    var nAbs = (matterState.neutronCostReduction || 0).toFixed(2);
    return '−' + nAbs + ' до ціни генераторів';
  }
  if (code === 'e') {
    var ePct = Math.round((matterState.electronCrystalMult - 1) * 100);
    return '+' + ePct + '% кристалів при престижі';
  }
  return '';
}

async function fetchMatterInfo() {
  if (matterFetchInFlight) return;
  matterFetchInFlight = true;
  try {
    var res = await fetch('/api/matter-info/' + SAVE_ID);
    matterState = await res.json();
    if (typeof refreshTierLocks === 'function') refreshTierLocks();
    if (typeof renderAutobuyToggle === 'function') renderAutobuyToggle();
    if (typeof renderAutoSynthesizeToggle === 'function') renderAutoSynthesizeToggle();
    if (typeof renderAutoUpgradeToggle === 'function') renderAutoUpgradeToggle();
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

  var vcPersists = (matterState.matterCollapses || 0) >= (matterState.vcPersistsAfterCollapses || Infinity);
  if (matterState.collapseReady) {
    var resetText = vcPersists
      ? 'це повністю скине Тір 0 (енергію, генератори, апгрейди) — Кристали Пустоти вже НЕ скидаються '
        + '(поріг ' + matterState.vcPersistsAfterCollapses + ' колапсів пройдено)'
      : 'це повністю скине Тір 0 (енергію, генератори, апгрейди й кристали пустоти)';
    intro.innerHTML = 'Енергія досягла межі! Обери частинку і сколапсуй — ' +
      resetText + ' та додасть +1 обраної частинки — вона залишиться назавжди.' +
      '<div class="matter-intro-sub">Колапсів виконано: ' + matterState.matterCollapses +
      ' · Цикл-буст: ×' + fmtCycleBoost(matterState.cycleBoost) + '</div>';
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
      '<div class="matter-particle-bonus">' + _particleEffectText(code) + '</div>' +
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
  var vcPersistThreshold = matterState.vcPersistsAfterCollapses || 0;
  var vcPersists = done >= vcPersistThreshold;

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
    '</div>' +
    '<div class="matter-upg-card' + (vcPersists ? ' done' : '') + '">' +
      '<div class="matter-upg-title">Цикл колапсів</div>' +
      '<div class="matter-upg-desc">Кожен колапс матерії — новий цикл, досвід якого лишається назавжди ' +
        '(на відміну від Кристалів Пустоти). Поточний множник виробництва: ×' + fmtCycleBoost(matterState.cycleBoost) + '.' +
        (vcPersists ? '' : ' Після ' + vcPersistThreshold + ' колапсів Кристали Пустоти більше не скидатимуться.') +
      '</div>' +
      '<div class="matter-upg-status">' +
        (vcPersists ? 'Кристали більше не скидаються' : 'До постійних кристалів: ' + done + ' / ' + vcPersistThreshold) +
      '</div>' +
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
