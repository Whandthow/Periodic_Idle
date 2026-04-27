// Точка входу: перший fetch + періодична синхронізація

renderLoop();

Promise.all([
  fetchState(),
  fetchGenerators()
]);

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
