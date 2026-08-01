// Тір 2: Періодична таблиця — перегляд та синтез елементів з p+n+e

var elementsState = { list: [], byId: {} };
var elementsFetchInFlight = false;
var selectedElementId = null;

async function fetchElements() {
  if (elementsFetchInFlight) return;
  elementsFetchInFlight = true;
  try {
    var res = await fetch('/api/elements/' + SAVE_ID);
    var data = await res.json();
    elementsState.list = data.slice();
    elementsState.byId = {};
    data.forEach(function(el) { elementsState.byId[el.id] = el; });
    renderPeriodicTable();
    if (selectedElementId != null) _renderElementDetail(elementsState.byId[selectedElementId]);
  } finally {
    elementsFetchInFlight = false;
  }
}

function renderPeriodicTable() {
  var grid = document.getElementById('periodic-table-grid');
  var intro = document.getElementById('periodic-intro');
  if (!grid) return;

  var discovered = elementsState.list.filter(function(el) { return el.count > 0; }).length;
  if (intro) {
    intro.textContent = 'Синтезуй атоми з протонів, нейтронів та електронів. ' +
      'Відкрито ' + discovered + ' з ' + elementsState.list.length + '.';
  }

  grid.innerHTML = elementsState.list.map(function(el) {
    var locked = !el.unlocked;
    var cls = 'element-card' + (locked ? ' locked' : '') + (el.count > 0 ? ' discovered' : '');
    var badge = el.count > 0 ? '<div class="element-card-badge">x' + el.count + '</div>' : '';
    return '<div class="' + cls + '"' +
      ' style="grid-column:' + el.groupNumber + ';grid-row:' + el.period + '"' +
      ' data-id="' + el.id + '"' +
      ' onmouseenter="hoverElement(' + el.id + ', this)"' +
      ' onmouseleave="unhoverElement(' + el.id + ')"' +
      ' onclick="selectElement(' + el.id + ', this)">' +
      '<div class="element-card-number">' + el.atomicNumber + '</div>' +
      '<div class="element-card-symbol">' + el.symbol + '</div>' +
      '<div class="element-card-name">' + el.name + '</div>' +
      '<div class="element-card-weight">' + el.atomicWeight + '</div>' +
      badge +
    '</div>';
  }).join('');
}

function hoverElement(id, cardEl) {
  if (isCompactNav()) return; // на тачі — тільки клік/пін, щоб не заважати скролу
  selectedElementId = id;
  _positionDetailNear(cardEl);
  _renderElementDetail(elementsState.byId[id]);
}

function unhoverElement(id) {
  if (isCompactNav()) return;
  var detail = document.getElementById('element-detail');
  if (detail && !detail.classList.contains('pinned')) detail.classList.remove('visible');
}

function selectElement(id, cardEl) {
  selectedElementId = id;
  var detail = document.getElementById('element-detail');
  if (detail) detail.classList.add('pinned');
  _positionDetailNear(cardEl);
  _renderElementDetail(elementsState.byId[id]);
}

function _positionDetailNear(cardEl) {
  var detail = document.getElementById('element-detail');
  if (!detail || !cardEl) return;
  var rect = cardEl.getBoundingClientRect();
  var w = 220, h = 300;
  var left = Math.min(Math.max(8, rect.left + rect.width / 2 - w / 2), window.innerWidth - w - 8);
  var top = Math.min(Math.max(8, rect.top + rect.height / 2 - h / 2), window.innerHeight - h - 8);
  detail.style.left = left + 'px';
  detail.style.top = top + 'px';
  detail.classList.add('visible');
}

function _renderElementDetail(el) {
  if (!el) return;
  document.getElementById('element-detail-number').textContent = el.atomicNumber;
  document.getElementById('element-detail-symbol').textContent = el.symbol;
  document.getElementById('element-detail-name').textContent = el.name;
  document.getElementById('element-detail-weight').textContent = el.atomicWeight;

  var shells = el.shellConfig.split(',').map(function(s) { return parseInt(s, 10); });
  document.getElementById('element-detail-shells').innerHTML =
    shells.map(function(n) { return '<div>' + n + '</div>'; }).join('');
  document.getElementById('element-detail-orbits').innerHTML = _orbitsSvg(shells);

  var costEl = document.getElementById('element-detail-cost');
  costEl.innerHTML =
    '<span>' + el.costProtons + 'p</span>' +
    '<span>' + el.costNeutrons + 'n</span>' +
    '<span>' + el.costElectrons + 'e</span>';

  // Наукова концепція: реальна енергія зв'язку ядра (SEMF) — синтез до заліза
  // повертає енергію (як термоядерний синтез у зорі), важче за залізо — коштує її.
  var energyEl = document.getElementById('element-detail-energy');
  if (energyEl) {
    var beText = (el.bindingEnergyMeV || 0).toFixed(1) + ' МеВ';
    energyEl.className = 'element-detail-energy' + (el.exothermic ? ' exo' : ' endo');
    energyEl.textContent = el.bindingEnergyMeV > 0
      ? (el.exothermic ? '⚡ +' + beText + ' (синтез)' : '⚠ -' + beText + ' (синтез)')
      : '';
  }

  var btn = document.getElementById('element-detail-btn');
  btn.disabled = !el.unlocked;
  btn.textContent = el.unlocked ? 'Синтезувати' : 'Ще не відкрито';
  // lockedReason: наукова концепція — пояснює ЧОМУ заблоковано (потрібна зоря, попередній елемент).
  document.getElementById('element-detail-msg').textContent = el.unlocked ? '' : (el.lockedReason || '');
}

function _orbitsSvg(shells) {
  var cx = 100, cy = 100, maxR = 88;
  var n = shells.length;
  var out = '';
  for (var i = 0; i < n; i++) {
    var r = (i + 1) * (maxR / n);
    out += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r.toFixed(1) + '" class="orbit-ring"></circle>';

    var count = shells[i];
    // Кожна оболонка обертається в своїй <g> навколо центру (CSS-анімація transform-origin).
    // Внутрішні оболонки крутяться швидше — почергово за/проти годинникової стрілки.
    var dur = (3 + i * 2.2).toFixed(2);
    var dir = (i % 2 === 0) ? 'orbit-spin-cw' : 'orbit-spin-ccw';
    var electrons = '';
    for (var k = 0; k < count; k++) {
      var angle = (2 * Math.PI * k / count) - Math.PI / 2;
      var ex = cx + r * Math.cos(angle);
      var ey = cy + r * Math.sin(angle);
      electrons += '<circle cx="' + ex.toFixed(2) + '" cy="' + ey.toFixed(2) + '" r="2.6" class="orbit-electron"></circle>';
    }
    out += '<g class="orbit-shell ' + dir + '" style="animation-duration:' + dur + 's">' + electrons + '</g>';
  }
  return out;
}

async function synthesizeSelected() {
  if (selectedElementId == null) return;
  var msg = document.getElementById('element-detail-msg');
  try {
    var res = await fetch('/api/synthesize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, elementId: selectedElementId, amount: 1 })
    });
    if (!res.ok) {
      var txt = await res.text();
      var errMsg = txt;
      try { var p = JSON.parse(txt); errMsg = p.error || p.message || txt; } catch (_) {}
      if (msg) msg.textContent = errMsg;
      return;
    }
    if (msg) msg.textContent = 'Синтезовано!';
    await fetchState();
    await fetchElements();
  } catch (e) {
    console.error('synthesizeSelected failed', e);
    if (msg) msg.textContent = 'Помилка синтезу: ' + e;
  }
}

document.addEventListener('click', function(e) {
  var detail = document.getElementById('element-detail');
  if (!detail || !detail.classList.contains('pinned')) return;
  if (detail.contains(e.target)) return;
  if (e.target.closest && e.target.closest('.element-card')) return;
  detail.classList.remove('pinned', 'visible');
});

// === Автосинтез (Тір 2 елементи + Тір 3 молекули, спільний прапор save.autoSynthesizeEnabled) ===
// Тоглиться однією дією на обох сторінках — не потребує окремого апгрейду,
// щоб гравець не заїбувався клікати кожен синтез вручну (наукова концепція
// не про це, а про реалізм механік; ручний контроль лишається доступним завжди).

var _autoSynthesizeToggleIds = ['autosynthesize-toggle-row-elements', 'autosynthesize-toggle-row-molecules'];
var _autoSynthesizeBtnIds = ['autosynthesize-toggle-btn-elements', 'autosynthesize-toggle-btn-molecules'];

function renderAutoSynthesizeToggle() {
  if (typeof matterState === 'undefined') return;
  var enabled = !!matterState.autoSynthesizeEnabled;
  _autoSynthesizeToggleIds.forEach(function(id) {
    var row = document.getElementById(id);
    if (row) row.style.display = '';
  });
  _autoSynthesizeBtnIds.forEach(function(id) {
    var btn = document.getElementById(id);
    if (!btn) return;
    btn.classList.toggle('on', enabled);
    btn.classList.toggle('off', !enabled);
    btn.textContent = enabled ? 'Автосинтез: увімкнено' : 'Автосинтез: вимкнено';
  });
}

async function toggleAutoSynthesize() {
  var current = typeof matterState !== 'undefined' && !!matterState.autoSynthesizeEnabled;
  var next = !current;
  if (typeof matterState !== 'undefined') matterState.autoSynthesizeEnabled = next;
  renderAutoSynthesizeToggle();
  try {
    var res = await fetch('/api/autosynthesize-toggle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, enabled: next })
    });
    var data = await res.json();
    if (typeof matterState !== 'undefined') matterState.autoSynthesizeEnabled = !!data.autoSynthesizeEnabled;
    renderAutoSynthesizeToggle();
  } catch (err) {
    console.error('toggleAutoSynthesize failed', err);
    if (typeof matterState !== 'undefined') matterState.autoSynthesizeEnabled = current;
    renderAutoSynthesizeToggle();
  }
}
