// Тір 3: Молекули — збірка з уже синтезованих атомів (хімічні зв'язки)

var moleculesState = [];
var moleculesFetchInFlight = false;

async function fetchMolecules() {
  if (moleculesFetchInFlight || typeof SAVE_ID !== 'number') return;
  moleculesFetchInFlight = true;
  try {
    var res = await fetch('/api/molecules/' + SAVE_ID);
    if (!res.ok) return;
    moleculesState = await res.json();
    if (isPageActive('molecules')) renderMoleculesPage();
  } catch (e) {
    console.error('fetchMolecules failed', e);
  } finally {
    moleculesFetchInFlight = false;
  }
}

function _fmtRecipe(recipe) {
  return recipe.map(function(r) { return r.atomCount + r.elementSymbol; }).join(' + ');
}

function renderMoleculesPage() {
  var intro = document.getElementById('molecules-intro');
  var grid = document.getElementById('molecules-grid');
  if (!grid) return;

  var discovered = moleculesState.filter(function(m) { return m.count > 0; }).length;
  if (intro) {
    intro.textContent = 'Зберіть молекули з уже синтезованих атомів (хімічний зв\'язок). ' +
      'Зібрано ' + discovered + ' з ' + moleculesState.length + '.';
  }

  grid.innerHTML = '<div class="molecules-grid">' + moleculesState.map(function(m) {
    var cls = 'molecule-card' + (m.unlocked ? '' : ' locked') + (m.count > 0 ? ' discovered' : '');
    var badge = m.count > 0 ? '<div class="molecule-card-badge">x' + m.count + '</div>' : '';
    var btn = '<button class="molecule-synth-btn" ' + (m.unlocked ? '' : 'disabled') +
      ' onclick="synthesizeMolecule(' + m.id + ')">Зібрати</button>';
    return '<div class="' + cls + '">' +
      badge +
      '<div class="molecule-card-formula">' + m.formula + '</div>' +
      '<div class="molecule-card-name">' + m.name + '</div>' +
      '<div class="molecule-card-recipe">' + _fmtRecipe(m.recipe) + '</div>' +
      '<div class="molecule-card-energy">⚡ +' + m.bondEnergyEv.toFixed(2) + ' еВ</div>' +
      btn +
      '<div class="molecule-card-msg" id="molecule-msg-' + m.id + '"></div>' +
    '</div>';
  }).join('') + '</div>';
}

async function synthesizeMolecule(moleculeId) {
  var msgEl = document.getElementById('molecule-msg-' + moleculeId);
  try {
    var res = await fetch('/api/synthesize-molecule', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, moleculeId: moleculeId, amount: 1 })
    });
    if (!res.ok) {
      var txt = await res.text();
      var errMsg = txt;
      try { var p = JSON.parse(txt); errMsg = p.error || p.message || txt; } catch (_) {}
      if (msgEl) msgEl.textContent = errMsg;
      return;
    }
    if (msgEl) msgEl.textContent = 'Зібрано!';
    await fetchState();
    await fetchMolecules();
  } catch (e) {
    console.error('synthesizeMolecule failed', e);
    if (msgEl) msgEl.textContent = 'Помилка: ' + e;
  }
}
