// Точка входу: перший fetch + періодична синхронізація

fetchState().then(function() {
  renderLoop();
});
fetchGenerators();
fetchUpgrades();

setInterval(renderLoop, 250);
setInterval(fetchState, 1500);
setInterval(fetchGenerators, 5000);
setInterval(fetchUpgrades, 5000);
setInterval(refreshPrestigeInfo, 5000);
refreshPrestigeInfo();
