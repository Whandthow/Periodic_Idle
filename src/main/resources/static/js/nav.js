// Навігація: вибір етапу + перемикання вкладок

 var activeTierId = 0;

 function _tierUnlocked(tierKey) {
   if (String(tierKey) === '1') return isMatterTierUnlocked();
   var cfg = TIER_UNLOCKS[tierKey];
   if (!cfg) return true;
   return resourceLog10(cfg.resource) >= cfg.minLog10;
 }

 function isCompactNav() {
   return window.matchMedia('(max-width: 900px)').matches;
 }

 function openTierDrawer() {
   if (isCompactNav()) document.body.classList.add('tier-drawer-open');
 }

 function closeTierDrawer() {
   document.body.classList.remove('tier-drawer-open');
 }

 function _activePageName() {
   var activePage = document.querySelector('.page.active');
   return activePage ? activePage.id.replace('page-', '') : '';
 }

 function _renderSubNav(tier, activePageName) {
   var tierData = TIERS[tier];
   var subNav = document.getElementById('sub-nav');
   var firstUnlocked = tierData.tabs.find(function(t) { return !t.locked; });
   var chosenPage = activePageName && tierData.tabs.some(function(tab) { return !tab.locked && tab.id === activePageName; })
     ? activePageName
     : (firstUnlocked ? firstUnlocked.id : '');

   subNav.innerHTML = '<div class="sub-nav-title">' + tierData.name + '</div>' +
     tierData.tabs.map(function(tab) {
       var isActive = tab.id === chosenPage;
       var cls = tab.locked ? 'sub-nav-item locked' : ('sub-nav-item' + (isActive ? ' active' : ''));
       var click = tab.locked ? '' : 'onclick="showPage(\'' + tab.id + '\', this)"';
       return '<div class="' + cls + '" data-page="' + tab.id + '" ' + click + '>' + tab.label + '</div>';
     }).join('');

   return firstUnlocked;
 }

/**
 * Перевіряє умови TIER_UNLOCKS і знімає клас `locked` з відповідних tier-btn.
 * Викликається з renderLoop / після fetchState.
 */
function refreshTierLocks() {
  if (typeof TIER_UNLOCKS === 'undefined' || typeof resourceState === 'undefined') return;
  var changed = false;
  Object.keys(TIER_UNLOCKS).forEach(function(tierKey) {
    var btn = document.getElementById('tier-btn-' + tierKey);
    if (!btn) return;
    var unlocked = _tierUnlocked(tierKey);
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
  if ((name === 'exchange' || name === 'upgrades_t1' || name === 'stats') && typeof fetchUpgrades === 'function') fetchUpgrades();
  if (name === 'settings' && typeof refreshPrestigeInfo === 'function') refreshPrestigeInfo();
  if ((name === 'exchange' || name === 'upgrades_t1') && typeof fetchMatterInfo === 'function') fetchMatterInfo();
  if (name === 'exchange' && typeof renderMatterPage === 'function') renderMatterPage();
  if (name === 'upgrades_t1' && typeof renderMatterUpgrades === 'function') renderMatterUpgrades();
  if (name === 'stats' && typeof fetchStats === 'function') fetchStats();
  if (name === 'stats' && typeof renderStatsPage === 'function') renderStatsPage();
}

function toggleTierDrawer() {
  if (document.body.classList.contains('tier-drawer-open')) closeTierDrawer();
  else openTierDrawer();
}

function selectTier(tier, el) {
  if (el && el.classList.contains('locked')) return;
  if (activeTierId === tier && isCompactNav() && document.body.classList.contains('tier-drawer-open')) {
    closeTierDrawer();
    return;
  }
  // Виходимо з режиму налаштувань (повертаємо другу панель)
  document.body.classList.remove('settings-open');
  document.querySelectorAll('.tier-btn').forEach(function(b) { b.classList.remove('active'); });
  if (el) el.classList.add('active');

  activeTierId = tier;
  var currentPageName = _activePageName();
  var firstUnlocked = _renderSubNav(tier, currentPageName);
  var targetPage = currentPageName && TIERS[tier].tabs.some(function(tab) {
    return !tab.locked && tab.id === currentPageName;
  }) ? currentPageName : (firstUnlocked ? firstUnlocked.id : '');

  if (targetPage) {
    var activeTab = document.querySelector('.sub-nav-item[data-page="' + targetPage + '"]');
    showPage(targetPage, activeTab, true);
  }

  if (isCompactNav()) openTierDrawer();
}

function showPage(name, el, keepDrawerOpen) {
  document.querySelectorAll('.page').forEach(function(p) { p.classList.remove('active'); });
  document.querySelectorAll('.sub-nav-item').forEach(function(s) { s.classList.remove('active'); });
  var page = document.getElementById('page-' + name);
  if (page) page.classList.add('active');
  if (el) el.classList.add('active');
  if (!keepDrawerOpen && isCompactNav()) closeTierDrawer();
  activatePage(name);
}

// Відкриття налаштувань: ховаємо другу панель + показуємо сторінку налаштувань
function openSettings(el) {
  document.body.classList.add('settings-open');
  closeTierDrawer();
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
    if (typeof fetchMatterInfo === 'function') await fetchMatterInfo();
  } catch (e) {
    console.error('resetSave failed', e);
    alert('Помилка скидання: ' + e);
  }
}

 window.addEventListener('resize', function() {
   if (!isCompactNav()) closeTierDrawer();
 });
