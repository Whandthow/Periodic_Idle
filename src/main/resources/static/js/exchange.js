// Тір 1 — Обмін: розщеплення кристалів пустоти на p/n/e.

var exchangeMult = 1; // 1 / 10 / 100 / -1 (Max)

function _initExchangeToolbar() {
  var page = document.getElementById('page-exchange');
  if (!page) return;
  var btns = page.querySelectorAll('.gen-mult-btn');
  btns.forEach(function(b) {
    b.addEventListener('click', function() {
      btns.forEach(function(x) { x.classList.remove('active'); });
      b.classList.add('active');
      exchangeMult = parseInt(b.dataset.mult, 10);
    });
  });
}

function _exchangeMsg(text, kind) {
  var el = document.getElementById('exchange-msg');
  if (!el) return;
  el.textContent = text;
  el.classList.remove('ok', 'error');
  if (kind) el.classList.add(kind);
  clearTimeout(_exchangeMsg._t);
  _exchangeMsg._t = setTimeout(function() { el.textContent = ''; }, 2500);
}

async function splitCrystals() {
  try {
    var res = await fetch('/api/exchange/split', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, amount: exchangeMult })
    });
    if (!res.ok) {
      var txt = await res.text();
      var msg = txt;
      try { var p = JSON.parse(txt); msg = p.error || p.message || txt; } catch (_) {}
      if (/Not enough crystals/i.test(msg)) msg = 'Недостатньо кристалів';
      _exchangeMsg(msg, 'error');
      return;
    }
    var data = await res.json();
    _exchangeMsg('Розщеплено: ' + data.split, 'ok');
    if (typeof fetchState === 'function') await fetchState();
  } catch (e) {
    console.error('splitCrystals failed', e);
    _exchangeMsg('Помилка: ' + e, 'error');
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', _initExchangeToolbar);
} else {
  _initExchangeToolbar();
}
