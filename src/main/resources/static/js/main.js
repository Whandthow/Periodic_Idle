// Точка входу: перший fetch + періодична синхронізація

renderLoop();

Promise.all([
  fetchState(),
  fetchGenerators(),
  fetchMatterInfo(),
  fetchUpgrades()
]).then(function() {
  if (typeof refreshTierLocks === 'function') refreshTierLocks();
});

setInterval(renderLoop, 250);
setInterval(fetchState, 1500);
setInterval(function() {
  if (isPageActive('generators')) fetchGenerators();
}, 5000);
setInterval(function() {
  if (isPageActive('upgrades')) fetchUpgrades();
}, 5000);
setInterval(function() {
  if (isPageActive('settings')) refreshPrestigeInfo();
}, 5000);
setInterval(function() {
  // Тримаємо в курсі стан матерії: чи можна колапсувати, кількість частинок, прапор зламу.
  if (isPageActive('exchange') || isPageActive('upgrades_t1') || isPageActive('stats')) {
    fetchMatterInfo();
  }
}, 3000);
// Розблокування Тіру 1 — повільний поллінг, щоб гравець побачив "Матерія" одразу як досяг капу.
setInterval(fetchMatterInfo, 8000);
setInterval(function() {
  if (isPageActive('stats')) fetchStats();
}, 4000);
