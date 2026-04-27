// Панель ресурсів + плавна інтерполяція через requestAnimationFrame

// Локальний стан: { E: { number, exponent, ratePerSec, lastSync } }
var resourceState = {};
var resourceDom = {};
var stateFetchInFlight = false;

// Ресурси, які показуються лише коли відповідний тір розблокований.
var TIER_RESOURCES = { 1: ['p', 'n', 'e'] };

function _isTierUnlocked(tier) {
  var btn = document.getElementById('tier-btn-' + tier);
  return btn && !btn.classList.contains('locked');
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

// Плавне оновлення значень між синхронізаціями (60fps)
function renderLoop() {
  var now = performance.now();
  Object.keys(resourceState).forEach(function(code) {
    var r = resourceState[code];
    var dt = (now - r.lastSync) / 1000;

    var base = r.number * Math.pow(10, r.exponent);
    var curr = base + r.ratePerSec * dt;

    var dispNum, dispExp;
    if (curr <= 0) {
      dispNum = 0;
      dispExp = 0;
    } else {
      dispExp = Math.floor(Math.log10(curr));
      dispNum = curr / Math.pow(10, dispExp);
    }

    var dom = resourceDom[code];
    if (dom && dom.valEl) dom.valEl.textContent = fmt(dispNum, dispExp);
  });
}

