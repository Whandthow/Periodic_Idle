// Глобальні константи гри
// SAVE_ID підставляється у main.js після /api/save/init на основі localStorage 'pidleToken'.
// Залишаємо null до bootstrap-у — всі fetch-функції чекають на bootstrapPromise.
var SAVE_ID = null;

// Відповідність коду ресурсу та файла іконки
var ICONS = {
  E: 'Energy',
  p: 'Proton',
  n: 'Neutron',
  e: 'Electron',
  VC: 'VoidCrystal'
};

var ENERGY_CAP_LOG10 = 308;

// Конфігурація вкладок для кожного етапу
var TIERS = {
  0: { name: 'Пустота', tabs: [
    { id: 'generators', label: 'Генератори' },
    { id: 'upgrades', label: 'Апгрейди' },
    { id: 'prestige', label: 'Престиж' },
    { id: 'stats', label: 'Статистика' }
  ]},
  1: { name: 'Матерія', tabs: [
    { id: 'exchange', label: 'Колапс' },
    { id: 'upgrades_t1', label: 'Грейди' }
  ]},
  2: { name: 'Атоми', tabs: [
    { id: 'periodic_table', label: 'Таблиця' }
  ]},
  3: { name: 'Молекули', tabs: [
    { id: 'molecules', label: 'Молекули' }
  ]}
};

// Умови розблокування тірів: {tier: [{resource, minLog10}, ...]}, OR за рядками одного tier.
// Data-driven — підвантажується з /api/tier-unlocks при bootstrap (див. nav.js fetchTierUnlocks).
var TIER_UNLOCK_CONDITIONS = {};
