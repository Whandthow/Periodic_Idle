// Глобальні константи гри
var SAVE_ID = 1;

// Відповідність коду ресурсу та файла іконки
var ICONS = {
  E: 'Energy',
  p: 'Proton',
  n: 'Neutron',
  e: 'Electron',
  VC: 'VoidCrystal'
};

// Конфігурація вкладок для кожного етапу
var TIERS = {
  0: { name: 'Пустота', tabs: [
    { id: 'generators', label: 'Генератори' },
    { id: 'upgrades', label: 'Апгрейди' },
    { id: 'prestige', label: 'Престиж', locked: true },
    { id: 'stats', label: 'Статистика' }
  ]},
  1: { name: 'Матерія', tabs: [
    { id: 'exchange', label: 'Обмін' },
    { id: 'upgrades_t1', label: 'Апгрейди', locked: true },
    { id: 'prestige_t1', label: 'Престиж', locked: true }
  ]},
  2: { name: 'Атоми', tabs: [
    { id: 'periodic_table', label: 'Таблиця', locked: true },
    { id: 'synthesis', label: 'Синтез', locked: true }
  ]}
};

// Умови розблокування тірів. minLog10 — мін. значення log10(ресурс).
// 1e2 = 100 кристалів; 1e3 = 1000 протонів (заглушка для Т2).
var TIER_UNLOCKS = {
  1: { resource: 'VC', minLog10: 2 },
  2: { resource: 'p',  minLog10: 3 }
};
