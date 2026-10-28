// Dev-інструменти (швидкі тіки, накидання експоненти ресурсам).

var prestigeInfoInFlight = false;

async function toggleFastTick(el) {
  var mult = el.checked ? 10 : 1;
  try {
    await fetch('/api/dev/tick-speed', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ multiplier: mult })
    });
  } catch (e) {
    console.error('toggleFastTick failed', e);
  }
}

async function refreshPrestigeInfo() {
  var hint = document.getElementById('prestige-hint');
  var btn = document.getElementById('prestige-btn');
  if (!hint || !btn || prestigeInfoInFlight) return;
  prestigeInfoInFlight = true;
  try {
    var res = await fetch('/api/prestige-info/' + SAVE_ID);
    if (!res.ok) return;
    var data = await res.json();
    var gainExp = data.exponent || 0;
    var gainNum = data.number || 0;
    var total = gainNum * Math.pow(10, gainExp);
    if (gainNum <= 0 || total < 1) {
      hint.textContent = 'Потрібно щонайменше 1e' + (data.minLog10Energy || 10) + ' енергії';
      btn.disabled = true;
      btn.style.opacity = 0.5;
    } else {
      hint.textContent = 'Отримаєш ' + fmt(gainNum, gainExp) + ' кристалів пустоти (VC)';
      btn.disabled = false;
      btn.style.opacity = 1;
    }
  } catch (e) {
    console.error('refreshPrestigeInfo', e);
  } finally {
    prestigeInfoInFlight = false;
  }
}

async function doPrestige() {
  try {
    var res = await fetch('/api/prestige', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID })
    });
    if (!res.ok) {
      var msg = await res.text();
      alert('Не вийшло: ' + msg);
      return;
    }
    await fetchState();
    await fetchGenerators();
    await fetchUpgrades();
    await refreshPrestigeInfo();
  } catch (e) {
    console.error('doPrestige', e);
  }
}

async function addResourceExp(code, delta) {
  try {
    await fetch('/api/dev/add-exp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, resourceCode: code, delta: delta })
    });
    await fetchState();
  } catch (e) {
    console.error('addResourceExp failed', e);
  }
}

/** Заповнює селекти елементів/молекул для dev-видачі (розділ "Dev-інструменти"). */
var _devGrantOptionsLoaded = false;

async function renderDevGrantOptions() {
  if (_devGrantOptionsLoaded) return;
  _devGrantOptionsLoaded = true;
  try {
    var res = await fetch('/api/elements/' + SAVE_ID);
    var elements = await res.json();
    var elSel = document.getElementById('dev-grant-element-select');
    if (elSel) {
      elSel.innerHTML = elements.map(function(el) {
        return '<option value="' + el.id + '">' + el.symbol + ' — ' + el.name + '</option>';
      }).join('');
    }
  } catch (e) {
    console.error('renderDevGrantOptions elements failed', e);
  }
  try {
    var res2 = await fetch('/api/molecules/' + SAVE_ID);
    var molecules = await res2.json();
    var molSel = document.getElementById('dev-grant-molecule-select');
    if (molSel) {
      molSel.innerHTML = molecules.map(function(m) {
        return '<option value="' + m.id + '">' + m.formula + ' — ' + m.name + '</option>';
      }).join('');
    }
  } catch (e) {
    console.error('renderDevGrantOptions molecules failed', e);
  }
}

async function grantParticle() {
  var code = document.getElementById('dev-grant-particle-select').value;
  var amount = parseInt(document.getElementById('dev-grant-particle-amount').value, 10) || 0;
  var status = document.getElementById('dev-grant-particle-status');
  if (amount <= 0) { if (status) status.textContent = 'Вкажи додатну кількість'; return; }
  try {
    var res = await fetch('/api/dev/grant-resource', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, resourceCode: code, amount: amount })
    });
    if (!res.ok) { if (status) status.textContent = 'Помилка: ' + (await res.text()); return; }
    if (status) status.textContent = 'Видано +' + amount + ' (' + code + ')';
    await fetchState();
  } catch (e) {
    console.error('grantParticle failed', e);
    if (status) status.textContent = 'Помилка: ' + e;
  }
}

async function grantElement() {
  var sel = document.getElementById('dev-grant-element-select');
  var amount = parseInt(document.getElementById('dev-grant-element-amount').value, 10) || 0;
  var status = document.getElementById('dev-grant-element-status');
  if (!sel.value || amount <= 0) { if (status) status.textContent = 'Вкажи елемент і додатну кількість'; return; }
  try {
    var res = await fetch('/api/dev/grant-element', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, elementId: Number(sel.value), amount: amount })
    });
    if (!res.ok) { if (status) status.textContent = 'Помилка: ' + (await res.text()); return; }
    var data = await res.json();
    if (status) status.textContent = 'Видано ' + data.element + ' x' + data.count;
    await fetchState();
    if (typeof fetchElements === 'function') await fetchElements();
  } catch (e) {
    console.error('grantElement failed', e);
    if (status) status.textContent = 'Помилка: ' + e;
  }
}

async function grantMolecule() {
  var sel = document.getElementById('dev-grant-molecule-select');
  var amount = parseInt(document.getElementById('dev-grant-molecule-amount').value, 10) || 0;
  var status = document.getElementById('dev-grant-molecule-status');
  if (!sel.value || amount <= 0) { if (status) status.textContent = 'Вкажи молекулу і додатну кількість'; return; }
  try {
    var res = await fetch('/api/dev/grant-molecule', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, moleculeId: Number(sel.value), amount: amount })
    });
    if (!res.ok) { if (status) status.textContent = 'Помилка: ' + (await res.text()); return; }
    var data = await res.json();
    if (status) status.textContent = 'Видано ' + data.molecule + ' x' + data.count;
    await fetchState();
    if (typeof fetchMolecules === 'function') await fetchMolecules();
  } catch (e) {
    console.error('grantMolecule failed', e);
    if (status) status.textContent = 'Помилка: ' + e;
  }
}

/** Малює кнопки "Перейти на етап" по TIERS (config.js) — автоматично враховує нові тіри. */
function renderDevTierJumpButtons() {
  var row = document.getElementById('dev-tier-jump-row');
  if (!row || typeof TIERS === 'undefined') return;
  row.innerHTML = Object.keys(TIERS).map(function(tier) {
    return '<button class="btn-primary" onclick="jumpToTier(' + tier + ')">' +
      TIERS[tier].name + '</button>';
  }).join('');
}

/**
 * Dev-стрибок на тір: бекенд видає мінімальний прогрес найлегшої OR-умови
 * розблокування цього тіру (той самий data-driven механізм, що й звичайна
 * прогресія — /api/dev/jump-tier), тоді перечитуємо стан і переходимо на тір
 * у сайдбарі.
 */
async function jumpToTier(tier) {
  var status = document.getElementById('dev-tier-jump-status');
  if (status) status.textContent = '';
  try {
    var res = await fetch('/api/dev/jump-tier', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, tier: tier })
    });
    if (!res.ok) {
      var txt = await res.text();
      if (status) status.textContent = 'Помилка: ' + txt;
      return;
    }
    var data = await res.json();
    await fetchState();
    if (typeof refreshTierLocks === 'function') refreshTierLocks();
    if (typeof fetchMatterInfo === 'function') await fetchMatterInfo();

    var btn = document.getElementById('tier-btn-' + tier);
    if (btn && !btn.classList.contains('locked')) {
      selectTier(tier, btn);
    }
    if (status) {
      status.textContent = data.granted
        ? 'Видано: ' + data.resource + ' → 1e' + data.exponent
        : 'Цей тір не має умов розблокування (завжди відкритий)';
    }
  } catch (e) {
    console.error('jumpToTier failed', e);
    if (status) status.textContent = 'Помилка: ' + e;
  }
}

function setSaveTransferStatus(text, ok) {
  var el = document.getElementById('save-transfer-status');
  if (!el) return;
  el.textContent = text;
  el.className = 'save-transfer-status' + (ok === true ? ' ok' : ok === false ? ' err' : '');
}

async function exportSaveToTextarea() {
  var textarea = document.getElementById('save-transfer-text');
  try {
    var res = await fetch('/api/save-export/' + SAVE_ID);
    if (!res.ok) throw new Error(await res.text());
    var data = await res.json();
    textarea.value = JSON.stringify(data, null, 2);
    setSaveTransferStatus('Готово. Скопіюй текст вище.', true);
  } catch (e) {
    console.error('exportSaveToTextarea failed', e);
    setSaveTransferStatus('Не вийшло експортувати збереження', false);
  }
}

async function importSaveFromTextarea() {
  var textarea = document.getElementById('save-transfer-text');
  var parsed;
  try {
    parsed = JSON.parse(textarea.value);
  } catch (e) {
    setSaveTransferStatus('Невалідний JSON', false);
    return;
  }
  if (!confirm('Замінити поточний прогрес даними з textarea?')) return;
  try {
    var res = await fetch('/api/save-import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, data: parsed })
    });
    if (!res.ok) throw new Error(await res.text());
    setSaveTransferStatus('Імпортовано успішно.', true);
    await fetchState();
    await fetchGenerators();
    await fetchUpgrades();
  } catch (e) {
    console.error('importSaveFromTextarea failed', e);
    setSaveTransferStatus('Не вийшло імпортувати: ' + e.message, false);
  }
}
