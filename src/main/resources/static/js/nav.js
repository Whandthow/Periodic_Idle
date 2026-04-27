// Навігація: вибір етапу + перемикання вкладок

/**
 * Перевіряє умови TIER_UNLOCKS і знімає клас `locked` з відповідних tier-btn.
 * Викликається з renderLoop / після fetchState.
 */
function refreshTierLocks() {
  if (typeof TIER_UNLOCKS === 'undefined' || typeof resourceState === 'undefined') return;
  var changed = false;
  Object.keys(TIER_UNLOCKS).forEach(function(tierKey) {
    var cfg = TIER_UNLOCKS[tierKey];
    var btn = document.getElementById('tier-btn-' + tierKey);
    if (!btn) return;
    var r = resourceState[cfg.resource];
    var unlocked = false;
    if (r && r.number > 0) {
      var log10 = Math.log10(r.number) + r.exponent;
      unlocked = log10 >= cfg.minLog10;
    }
    var wasLocked = btn.classList.contains('locked');
    if (unlocked && wasLocked) { btn.classList.remove('locked'); changed = true; }
    else if (!unlocked && !wasLocked) { btn.classList.add('locked'); changed = true; }
  });
  // Якщо розблокувався/заблокувався тір — перемалювати resource-bar,
  // щоб ресурси тіру зʼявились/зникли.
  if (changed && typeof buildResourceBar === 'function') buildResourceBar();
}

function activatePage(name) {
  var page = document.getElementById('page-' + name);
  if (page) hydrateDeferredMedia(page);
  if (name === 'generators' && typeof fetchGenerators === 'function') fetchGenerators();
  if (name === 'upgrades' && typeof fetchUpgrades === 'function') fetchUpgrades();
  if (name === 'settings' && typeof refreshPrestigeInfo === 'function') refreshPrestigeInfo();
}

function selectTier(tier, el) {
  if (el && el.classList.contains('locked')) return;
  // Виходимо з режиму налаштувань (повертаємо другу панель)
  document.body.classList.remove('settings-open');
  document.querySelectorAll('.tier-btn').forEach(function(b) { b.classList.remove('active'); });
  if (el) el.classList.add('active');

  var tierData = TIERS[tier];
  var subNav = document.getElementById('sub-nav');
  var firstUnlocked = tierData.tabs.find(function(t) { return !t.locked; });

  subNav.innerHTML = '<div class="sub-nav-title">' + tierData.name + '</div>' +
    tierData.tabs.map(function(tab) {
      var isFirst = tab === firstUnlocked;
      var cls = tab.locked ? 'sub-nav-item locked' : ('sub-nav-item' + (isFirst ? ' active' : ''));
      var click = tab.locked ? '' : 'onclick="showPage(\'' + tab.id + '\', this)"';
      return '<div class="' + cls + '" ' + click + '>' + tab.label + '</div>';
    }).join('');

  if (firstUnlocked) {
    document.querySelectorAll('.page').forEach(function(p) { p.classList.remove('active'); });
    var page = document.getElementById('page-' + firstUnlocked.id);
    if (page) page.classList.add('active');
    activatePage(firstUnlocked.id);
  }
}

function showPage(name, el) {
  document.querySelectorAll('.page').forEach(function(p) { p.classList.remove('active'); });
  document.querySelectorAll('.sub-nav-item').forEach(function(s) { s.classList.remove('active'); });
  var page = document.getElementById('page-' + name);
  if (page) page.classList.add('active');
  if (el) el.classList.add('active');
  activatePage(name);
}

// Відкриття налаштувань: ховаємо другу панель + показуємо сторінку налаштувань
function openSettings(el) {
  document.body.classList.add('settings-open');
  document.querySelectorAll('.tier-btn').forEach(function(b) { b.classList.remove('active'); });
  if (el) el.classList.add('active');

  document.querySelectorAll('.page').forEach(function(p) { p.classList.remove('active'); });
  var page = document.getElementById('page-settings');
  if (page) page.classList.add('active');
  activatePage('settings');
}

// Скидання збереження: POST /api/reset — обнуляє ресурси, генератори та апгрейди.
async function resetSave() {
  if (!confirm('Видалити весь прогрес і почати з нуля?\nЦю дію неможливо скасувати.')) return;
  try {
    var res = await fetch('/api/reset', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID })
    });
    if (!res.ok) {
      var txt = await res.text();
      var msg = txt;
      try { var p = JSON.parse(txt); msg = p.error || p.message || txt; } catch (_) {}
      alert('Помилка скидання: ' + msg);
      return;
    }
    // Перечитаємо весь стан
    if (typeof fetchState === 'function') await fetchState();
    if (typeof fetchGenerators === 'function') await fetchGenerators();
    if (typeof fetchUpgrades === 'function') await fetchUpgrades();
  } catch (e) {
    console.error('resetSave failed', e);
    alert('Помилка скидання: ' + e);
  }
}
