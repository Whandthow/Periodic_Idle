// Точка входу: bootstrap → перший fetch → періодична синхронізація.
// Мульти-юзер: SAVE_ID береться з /api/save/init за токеном у localStorage.

function _genToken() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
  // Fallback для старіших браузерів.
  return 'p-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}

async function bootstrapSave() {
  var token = localStorage.getItem('pidleToken');
  if (!token) {
    token = _genToken();
    localStorage.setItem('pidleToken', token);
  }
  var res = await fetch('/api/save/init', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token: token })
  });
  if (!res.ok) throw new Error('save init failed: ' + res.status);
  var data = await res.json();
  SAVE_ID = data.saveId;
}

renderLoop();
setInterval(renderLoop, 250);

bootstrapSave().then(function() {
  return Promise.all([
    fetchState(),
    fetchGenerators(),
    fetchMatterInfo(),
    fetchUpgrades()
  ]);
}).then(function() {
  if (typeof refreshTierLocks === 'function') refreshTierLocks();

  // Інтервали запускаємо ТІЛЬКИ після того, як SAVE_ID відомий.
  setInterval(fetchState, 1500);
  setInterval(function() {
    if (isPageActive('generators')) fetchGenerators();
  }, 5000);
  setInterval(function() {
    if (isPageActive('upgrades')) fetchUpgrades();
  }, 5000);
  setInterval(function() {
    if (isPageActive('prestige')) refreshPrestigeInfo();
  }, 5000);
  setInterval(function() {
    if (isPageActive('exchange') || isPageActive('upgrades_t1') || isPageActive('stats')) {
      fetchMatterInfo();
    }
  }, 3000);
  // Повільний поллінг для розблокування Тіру 1.
  setInterval(fetchMatterInfo, 8000);
  setInterval(function() {
    if (isPageActive('stats')) fetchStats();
  }, 4000);
}).catch(function(err) {
  console.error('bootstrap failed', err);
  alert('Не вдалося завантажити збереження. Перезавантажте сторінку.');
});
