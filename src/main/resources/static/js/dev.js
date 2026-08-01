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
  if (!confirm('Скинути генератори та всю енергію заради кристалів пустоти?')) return;
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
