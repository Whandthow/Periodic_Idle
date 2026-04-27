// Dev-інструменти (швидкі тіки, накидання експоненти ресурсам).

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
  if (!hint || !btn) return;
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
