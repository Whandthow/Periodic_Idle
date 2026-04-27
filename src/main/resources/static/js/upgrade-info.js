// Info-панель апгрейдів:
// - Hover:   тимчасове превʼю
// - Click:   закріплює панель, показує рівень/вартість/кнопку Купити
// - Buy:     викликає buyUpgrade() з upgrades.js, показує success/error у msg

var pinned = null;
var active = null; // наведений або закріплений — саме з ним працює кнопка Купити
var panel, titleEl, descEl, costEl, btnEl, levelEl, msgEl;
var msgTimeoutId = null;
var upgBuyMult = 1;   // 1 / 10 / 100 / -1 (Max)
var hoverBuy = false; // автокупівля при наведенні
var hoverBuyTimer = null;

function _fillPanel(upg) {
  if (!titleEl) return;
  titleEl.textContent = upg.dataset.title || 'Покращення';
  descEl.textContent  = upg.dataset.desc  || '—';
  costEl.textContent  = upg.dataset.cost  || '—';

  var lvl = parseInt(upg.dataset.level || '0', 10);
  var maxL = parseInt(upg.dataset.max || '0', 10);
  if (levelEl) {
    if (maxL >= 999) {
      levelEl.textContent = 'Рівень: ' + lvl;
    } else if (maxL > 0) {
      levelEl.textContent = 'Рівень: ' + lvl + ' / ' + maxL;
    } else {
      levelEl.textContent = 'Рівень: ' + lvl;
    }
  }

  if (btnEl) {
    var isMax = maxL > 0 && maxL < 999 && lvl >= maxL;
    btnEl.disabled = isMax;
    var label = upgBuyMult < 0 ? 'Max' : ('x' + upgBuyMult);
    btnEl.textContent = isMax ? 'Max' : ('Купити ' + label);
    if (upg.dataset.afford === '0' && !isMax) {
      btnEl.classList.add('cant-afford');
    } else {
      btnEl.classList.remove('cant-afford');
    }
  }
}

function _showMsg(text, kind) {
  if (!msgEl) return;
  msgEl.textContent = text;
  msgEl.classList.remove('error', 'ok');
  if (kind) msgEl.classList.add(kind);
  clearTimeout(msgTimeoutId);
  msgTimeoutId = setTimeout(function() {
    if (msgEl) msgEl.textContent = '';
  }, 2500);
}

function _setActive(upg) {
  active = upg;
  panel.classList.add('has-content');
  _fillPanel(upg);
}
function _clearActive() {
  active = null;
  panel.classList.remove('has-content');
  if (msgEl) msgEl.textContent = '';
}

function _pin(upg) {
  if (pinned) pinned.classList.remove('pinned');
  pinned = upg;
  upg.classList.add('pinned');
  panel.classList.add('pinned');
  _setActive(upg);
}

function _unpin() {
  if (pinned) pinned.classList.remove('pinned');
  pinned = null;
  panel.classList.remove('pinned');
  _clearActive();
}

// Дозволяємо upgrades.js просити нас перемалювати закріплену панель
// після renderCompass (щоб disabled/ціна/рівень оновлювались).
function refreshPinnedPanel() {
  if (active) _fillPanel(active);
}

function _onBuy() {
  var target = active || pinned;
  console.log('[upgrade-info] Buy clicked', { target: target, id: target && target.dataset.id });
  if (!target) { _showMsg('Наведи або обери апгрейд', 'error'); return; }
  var id = target.dataset.id;
  if (!id) { _showMsg('Апгрейд ще не завантажено, зачекай', 'error'); return; }

  panel.classList.add('flash');
  setTimeout(function() { panel.classList.remove('flash'); }, 300);

  if (typeof buyUpgrade !== 'function') {
    _showMsg('buyUpgrade не підключено', 'error');
    console.error('[upgrade-info] buyUpgrade missing');
    return;
  }
  buyUpgrade(parseInt(id, 10), upgBuyMult).then(function(result) {
    console.log('[upgrade-info] Buy result', result);
    if (!result) { _showMsg('Немає відповіді', 'error'); return; }
    if (result.ok) {
      _showMsg('Куплено!', 'ok');
      if (active) _fillPanel(active);
    } else {
      _showMsg(_humaniseError(result.error), 'error');
    }
  }).catch(function(err) {
    console.error('[upgrade-info] Buy exception', err);
    _showMsg('Помилка: ' + err, 'error');
  });
}

function _humaniseError(err) {
  if (!err) return 'Не вдалось купити';
  var s = String(err);
  if (/Not enough/i.test(s)) return 'Недостатньо ресурсів';
  if (/Already max/i.test(s)) return 'Вже максимальний рівень';
  if (/locked/i.test(s))     return s.replace(/^.*locked.*?tier\s+(\d+).*$/i, 'Потрібне Ядро T$1');
  return s;
}

function _initUpgradeInfo() {
  panel = document.getElementById('upg-info');
  if (!panel) return;

  titleEl = panel.querySelector('.upg-info-title');
  descEl  = panel.querySelector('.upg-info-desc');
  costEl  = panel.querySelector('.upg-info-cost');
  btnEl   = panel.querySelector('.upg-info-btn');
  levelEl = panel.querySelector('.upg-info-level');
  msgEl   = panel.querySelector('.upg-info-msg');

  if (btnEl) {
    btnEl.addEventListener('click', function(e) {
      e.stopPropagation();
      _onBuy();
    });
  }

  document.querySelectorAll('.upg').forEach(function(upg) {
    upg.addEventListener('mouseenter', function() {
      if (!pinned) _setActive(upg);
      if (hoverBuy) _startHoverBuyLoop(upg);
    });
    upg.addEventListener('mouseleave', function() {
      _stopHoverBuyLoop();
    });
    upg.addEventListener('click', function(e) {
      e.stopPropagation();
      if (pinned === upg) _unpin();
      else _pin(upg);
    });
  });

  // Множник купівлі
  var multBtns = panel.querySelectorAll('.upg-mult-btn');
  multBtns.forEach(function(b) {
    b.addEventListener('click', function(e) {
      e.stopPropagation();
      multBtns.forEach(function(x) { x.classList.remove('active'); });
      b.classList.add('active');
      upgBuyMult = parseInt(b.dataset.mult, 10);
      if (active) _fillPanel(active); // перемалювати label
    });
  });

  // Hover-to-buy toggle
  var hb = document.getElementById('upg-hover-buy-toggle');
  if (hb) {
    hb.addEventListener('change', function() {
      hoverBuy = hb.checked;
      panel.classList.toggle('hover-buy-active', hoverBuy);
      if (!hoverBuy) _stopHoverBuyLoop();
    });
    // щоб кліки по toggle не знімали пін
    hb.addEventListener('click', function(e) { e.stopPropagation(); });
  }

  // Клік поза апгрейдом і поза панеллю — скидає закріплення
  document.addEventListener('click', function(e) {
    if (!pinned) return;
    if (panel.contains(e.target)) return;
    _unpin();
  });
}

// Hover-to-buy: поки курсор над апгрейдом і ввімкнено toggle — купуємо кожні 250мс.
function _startHoverBuyLoop(upg) {
  _stopHoverBuyLoop();
  var tick = function() {
    if (!hoverBuy) return;
    var id = upg.dataset.id;
    if (!id) return;
    var maxL = parseInt(upg.dataset.max || '0', 10);
    var lvl  = parseInt(upg.dataset.level || '0', 10);
    if (maxL > 0 && maxL < 999 && lvl >= maxL) { _stopHoverBuyLoop(); return; }
    if (typeof buyUpgrade !== 'function') return;
    buyUpgrade(parseInt(id, 10), upgBuyMult).then(function(r) {
      if (r && r.ok && active) _fillPanel(active);
      // якщо не вистачає — мовчки чекаємо наступного тіку
    });
  };
  tick(); // перша купівля одразу
  hoverBuyTimer = setInterval(tick, 250);
}
function _stopHoverBuyLoop() {
  if (hoverBuyTimer) { clearInterval(hoverBuyTimer); hoverBuyTimer = null; }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', _initUpgradeInfo);
} else {
  _initUpgradeInfo();
}
