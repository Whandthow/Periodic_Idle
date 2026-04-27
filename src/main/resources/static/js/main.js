// Точка входу: перший fetch + періодична синхронізація

fetchState()
  .then(function() {
    renderLoop();
    return fetchGenerators();
  })
  .then(function() {
    setTimeout(fetchUpgrades, 250);
    setTimeout(refreshPrestigeInfo, 500);
  });

setInterval(renderLoop, 250);
setInterval(fetchState, 1500);
setInterval(fetchGenerators, 5000);
setInterval(fetchUpgrades, 5000);
setInterval(refreshPrestigeInfo, 5000);
