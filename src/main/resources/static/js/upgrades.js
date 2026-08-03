// Компас апдейтів: тягне дані з сервера, ховає заблоковані, рахує affordable,
// заповнює data-* атрибути, які читає upgrade-info.js.

var upgradesState = { list: [], byCode: {} };
var upgradesFetchInFlight = false;

function _coreLevel() {
  var c = upgradesState.byCode['core'];
  return c ? (c.currentLevel || 0) : 0;
}

function _currentEnergyLog10() {
  if (typeof resourceState === 'undefined' || !resourceState) return -Infinity;
  var e = resourceState['E'];
  if (!e || !e.number || e.number <= 0) return -Infinity;
  // Рахуємо з урахуванням накопиченого приросту між синками (щоб кнопка миттєво активувалась)
  var now = performance.now();
  var dt = (now - (e.lastSync || now)) / 1000;
  var base = e.number * Math.pow(10, e.exponent);
  var curr = base + (e.ratePerSec || 0) * dt;
  if (curr <= 0) return -Infinity;
  return Math.log10(curr);
}

function _upgradeCostLog10(u) {
  var lvl = u.currentLevel || 0;
  var scaled = (u.costNumber || 0) * Math.pow(u.costMultiplier || 1, lvl);
  if (scaled <= 0) return Infinity;
  return Math.log10(scaled) + (u.costExponent || 0);
}

function _fmtUpgradeCost(u) {
  var lvl = u.currentLevel || 0;
  var scaled = u.costNumber * Math.pow(u.costMultiplier, lvl);
  if (!isFinite(scaled) || scaled <= 0) return '—';
  var extraExp = Math.floor(Math.log10(scaled));
  var mantissa = scaled / Math.pow(10, extraExp);
  return fmt(mantissa, u.costExponent + extraExp);
}

function _resourceLabel(u) {
  return ' E';
}

function _canAfford(u) {
  return _currentEnergyLog10() >= _upgradeCostLog10(u);
}

function renderCompass() {
  var cLvl = _coreLevel();
  var upgEls = document.querySelectorAll('.upg[data-code]');
  upgEls.forEach(function(el) {
    var code = el.getAttribute('data-code');
    var u = upgradesState.byCode[code];
    if (!u) { el.style.display = 'none'; return; }

    var locked = (u.unlockCoreTier || 0) > cLvl;
    el.style.display = locked ? 'none' : '';
    if (locked) return;

    var lvl = u.currentLevel || 0;
    var maxed = u.maxLevel && lvl >= u.maxLevel;
    el.dataset.title  = (u.name || code) + (lvl ? ' · T' + lvl : '');
    el.dataset.desc   = u.description || '';
    el.dataset.cost   = maxed ? 'MAX' : (_fmtUpgradeCost(u) + _resourceLabel(u));
    el.dataset.level  = lvl;
    el.dataset.max    = u.maxLevel || 0;
    el.dataset.id     = u.id;
    el.dataset.afford = (maxed ? '0' : (_canAfford(u) ? '1' : '0'));

    // Візуальний хінт: якщо можна купити — додаємо клас can-buy
    if (!maxed && _canAfford(u)) {
      el.classList.add('can-buy');
    } else {
      el.classList.remove('can-buy');
    }
  });

  // Якщо панель закріплена — оновимо її вміст через upgrade-info.js
  if (typeof refreshPinnedPanel === 'function') refreshPinnedPanel();
}

async function fetchUpgrades() {
  if (upgradesFetchInFlight) return;
  upgradesFetchInFlight = true;
  try {
    var res = await fetch('/api/upgrades/' + SAVE_ID);
    if (!res.ok) return;
    var data = await res.json();
    upgradesState.list = data;
    upgradesState.byCode = {};
    data.forEach(function(u) { upgradesState.byCode[u.code] = u; });
    renderCompass();
    if (typeof renderAutobuyToggle === 'function') renderAutobuyToggle();
    if (typeof renderAutoUpgradeToggle === 'function') renderAutoUpgradeToggle();
  } catch (e) {
    console.error('fetchUpgrades failed', e);
  } finally {
    upgradesFetchInFlight = false;
  }
}

/**
 * Повертає { ok: boolean, error?: string }
 */
async function buyUpgrade(id, amount) {
  if (typeof amount !== 'number') amount = 1;
  try {
    var res = await fetch('/api/buy-upgrade', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, upgradeId: id, amount: amount })
    });
    if (!res.ok) {
      var msg = await res.text();
      // Spring зазвичай віддає JSON з "message"/"error" — намагаємось витягти
      var humanMsg = msg;
      try {
        var parsed = JSON.parse(msg);
        humanMsg = parsed.message || parsed.error || msg;
      } catch (_) {}
      return { ok: false, error: humanMsg };
    }
    await fetchState();
    await fetchUpgrades();
    return { ok: true };
  } catch (e) {
    console.error('buyUpgrade failed', e);
    return { ok: false, error: String(e) };
  }
}

/**
 * Автопокупка апгрейдів Тіру 0 — окремий перемикач від автобаю генераторів.
 * Розблоковується у UI лише після matterState.autoUpgradeUnlockCollapses колапсів
 * матерії (AutoUpgradeService.AUTO_UPGRADE_UNLOCK_COLLAPSES): до того — показуємо
 * прогрес-хінт замість кнопки, як і решта progressive-disclosure підказок у грі.
 */
function renderAutoUpgradeToggle() {
  var row = document.getElementById('autoupgrade-toggle-row');
  var btn = document.getElementById('autoupgrade-toggle-btn');
  var hint = document.getElementById('autoupgrade-toggle-hint');
  if (!row || !btn) return;
  if (typeof matterState === 'undefined') return;

  var done = matterState.matterCollapses || 0;
  var required = matterState.autoUpgradeUnlockCollapses || 4;
  var unlocked = !!matterState.autoUpgradeUnlocked;

  // До першого колапсу матерії гравець ще не бачив саму механіку колапсу —
  // показувати прогрес "0 / 4 колапсів" тут було б передчасним спойлером.
  if (done <= 0 && !unlocked) {
    row.style.display = 'none';
    return;
  }
  row.style.display = '';

  if (!unlocked) {
    btn.style.display = 'none';
    if (hint) {
      hint.style.display = '';
      hint.textContent = 'Автопокупка апгрейдів: ' + done + ' / ' + required + ' колапсів матерії';
    }
    return;
  }

  if (hint) hint.style.display = 'none';
  btn.style.display = '';
  btn.disabled = false;
  var enabled = !!matterState.autoUpgradeEnabled;
  btn.classList.toggle('on', enabled);
  btn.classList.toggle('off', !enabled);
  btn.textContent = enabled ? 'Авто-апгрейди: УВІМК' : 'Авто-апгрейди: ВИМК';
  btn.title = enabled
    ? 'Автопокупка всіх доступних апгрейдів кожну секунду. Натисни щоб вимкнути.'
    : 'Автопокупка апгрейдів вимкнена. Натисни щоб увімкнути.';
}

async function toggleAutoUpgrade() {
  var btn = document.getElementById('autoupgrade-toggle-btn');
  if (!btn || btn.disabled || typeof matterState === 'undefined') return;
  var enabled = !!matterState.autoUpgradeEnabled;
  var next = !enabled;
  matterState.autoUpgradeEnabled = next;
  renderAutoUpgradeToggle();
  try {
    var res = await fetch('/api/autoupgrade-toggle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, enabled: next })
    });
    if (!res.ok) throw new Error('toggle failed');
    var data = await res.json();
    matterState.autoUpgradeEnabled = !!data.autoUpgradeEnabled;
    renderAutoUpgradeToggle();
  } catch (err) {
    console.error('toggleAutoUpgrade failed', err);
    matterState.autoUpgradeEnabled = enabled;
    renderAutoUpgradeToggle();
  }
}
