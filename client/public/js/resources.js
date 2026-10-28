// Панель ресурсів + плавна інтерполяція через requestAnimationFrame

// Локальний стан: { E: { number, exponent, ratePerSec, lastSync } }
var resourceState = {};
var resourceDom = {};
var stateFetchInFlight = false;

// Ресурси, які показуються лише коли відповідний тір розблокований.
var TIER_RESOURCES = { 1: ['p', 'n', 'e'] };

function _isTierUnlocked(tier) {
  if (typeof _tierUnlocked === 'function') return _tierUnlocked(tier);
  var btn = document.getElementById('tier-btn-' + tier);
  return btn && !btn.classList.contains('locked');
}

function _isEnergyCapped(code, currExp) {
  if (code !== 'E') return false;
  if (typeof matterState !== 'undefined' && matterState.brokenInfinity) return false;
  if (typeof ENERGY_CAP_LOG10 === 'undefined') return false;
  return currExp >= ENERGY_CAP_LOG10;
}

function buildResourceBar() {
  var bar = document.getElementById('resources-bar');
  var codes = Object.keys(resourceState).filter(function(code) {
    // Приховати ресурси заблокованих тірів
    for (var tier in TIER_RESOURCES) {
      if (TIER_RESOURCES[tier].indexOf(code) !== -1 && !_isTierUnlocked(tier)) return false;
    }
    return true;
  });
  bar.innerHTML = codes.map(function(code) {
    var icon = ICONS[code] || 'Energy';
    return '<div class="res-card">' +
      '<img class="res-icon" src="' + pngPath(icon) + '" alt="' + code + '" loading="eager" decoding="async" width="36" height="36">' +
      '<div>' +
        '<div class="res-value" id="res-val-' + code + '">0</div>' +
        '<div class="res-rate" id="res-rate-' + code + '">+0/с</div>' +
      '</div>' +
    '</div>';
  }).join('');

  resourceDom = {};
  codes.forEach(function(code) {
    resourceDom[code] = {
      valEl: document.getElementById('res-val-' + code),
      rateEl: document.getElementById('res-rate-' + code)
    };
  });
}

// Синхронізація з сервером (викликається раз на секунду)
async function fetchState() {
  if (stateFetchInFlight) return;
  stateFetchInFlight = true;
  try {
  var res = await fetch('/api/state/' + SAVE_ID);
  var data = await res.json();
  var now = performance.now();

  var newCodes = data.map(function(r) { return r.resource; }).sort().join(',');
  var oldCodes = Object.keys(resourceState).sort().join(',');

  data.forEach(function(r) {
    resourceState[r.resource] = {
      number: r.number,
      exponent: r.exponent,
      ratePerSec: r.ratePerSec || 0,
      lastSync: now
    };
  });

  if (newCodes !== oldCodes) buildResourceBar();
  data.forEach(function(r) {
    var dom = resourceDom[r.resource];
    if (dom && dom.rateEl) dom.rateEl.textContent = (r.ratePerSec >= 0 ? '+' : '') + fmtRate(r.ratePerSec || 0);
  });
  if (typeof refreshTierLocks === 'function') refreshTierLocks();
  if (typeof renderCompass === 'function' && typeof upgradesState !== 'undefined' && upgradesState.list && upgradesState.list.length && isPageActive('upgrades')) renderCompass();
  } finally {
    stateFetchInFlight = false;
  }
}

// Додає приріст rate*dt (звичайний double) до (number, exponent) без переповнення,
// навіть якщо exponent астрономічний (після Break Infinity — розділ 7.6 CLAUDE.md).
// Той самий підхід "вирівняти менший показник степеня, потім скласти мантиси", що й
// BigNum.add на бекенді — НІКОЛИ не множить 10^exponent напряму (це й переповнювало
// double в Infinity, коли exponent перевищував ~308, і показник ресурсу мовчки
// згортався в "0" замість реального значення).
function _addRateIncrement(number, exponent, incr) {
  if (incr === 0) return { num: number, exp: exponent };

  var incrAbs = Math.abs(incr);
  var incrExp = Math.floor(Math.log10(incrAbs));
  var incrMantissa = (incr < 0 ? -1 : 1) * (incrAbs / Math.pow(10, incrExp));

  var baseNum, baseExp, addNum, addExp;
  if (exponent >= incrExp) {
    baseNum = number; baseExp = exponent;
    addNum = incrMantissa; addExp = incrExp;
  } else {
    baseNum = incrMantissa; baseExp = incrExp;
    addNum = number; addExp = exponent;
  }
  var delta = addExp - baseExp; // <= 0
  var summed = delta < -300 ? baseNum : baseNum + addNum * Math.pow(10, delta);

  if (summed <= 0) return { num: 0, exp: 0 };
  var norm = Math.floor(Math.log10(summed));
  return { num: summed / Math.pow(10, norm), exp: baseExp + norm };
}

// Плавне оновлення значень між синхронізаціями
function renderLoop() {
  var now = performance.now();
  Object.keys(resourceState).forEach(function(code) {
    var r = resourceState[code];
    var dt = (now - r.lastSync) / 1000;
    var incr = r.ratePerSec * dt;

    var dispNum, dispExp;
    if (r.number <= 0 && incr <= 0) {
      dispNum = 0;
      dispExp = 0;
    } else {
      var curr = r.number > 0 ? _addRateIncrement(r.number, r.exponent, incr)
                               : _addRateIncrement(incr, 0, 0);
      dispNum = curr.num;
      dispExp = curr.exp;
    }

    var dom = resourceDom[code];
    if (dom && dom.valEl) {
      if (_isEnergyCapped(code, dispExp)) {
        dom.valEl.textContent = '\u221e';
        dom.valEl.classList.add('res-value-inf');
      } else {
        dom.valEl.textContent = fmt(dispNum, dispExp);
        dom.valEl.classList.remove('res-value-inf');
      }
    }
  });
}

