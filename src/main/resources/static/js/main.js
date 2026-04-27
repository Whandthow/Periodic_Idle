// Точка входу: перший fetch + періодична синхронізація

fetchState().then(function() {
  requestAnimationFrame(renderLoop);
});
fetchGenerators();
fetchUpgrades();

setInterval(fetchState, 1000);
setInterval(fetchGenerators, 2000);
setInterval(fetchUpgrades, 2000);
setInterval(refreshPrestigeInfo, 2000);
refreshPrestigeInfo();
