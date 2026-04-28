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

var MATTER_UNLOCK_LOG10 = 308;
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
    { id: 'periodic_table', label: 'Таблиця', locked: true },
    { id: 'synthesis', label: 'Синтез', locked: true }
  ]}
};

// Умови розблокування тірів. minLog10 — мін. значення log10(ресурс).
// Матерія відкривається на 1e308 енергії й більше не замикається після першої частинки.
var TIER_UNLOCKS = {
  1: { resource: 'E', minLog10: MATTER_UNLOCK_LOG10 },
  2: { resource: 'p',  minLog10: 3 }
};
