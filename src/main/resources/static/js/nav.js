// Навігація: вибір етапу + перемикання вкладок

 var activeTierId = 0;

 function _tierUnlocked(tierKey) {
   var conditions = TIER_UNLOCK_CONDITIONS[tierKey];
   if (!conditions || !conditions.length) return true;
   // OR за умовами одного тіру: досить виконати одну (напр. E>=1e308 АБО вже є частинки).
   return conditions.some(function(c) { return resourceLog10(c.resource) >= c.minLog10; });
 }

/**
 * Підвантажує data-driven умови розблокування тірів з /api/tier-unlocks
 * і групує їх за tier: {1: [{resource, minLog10}, ...], 2: [...]}.
 * Контент, однаковий для всіх saves — викликається один раз при bootstrap.
 */
async function fetchTierUnlocks() {
  try {
    var res = await fetch('/api/tier-unlocks');
    if (!res.ok) return;
    var rows = await res.json();
    var grouped = {};
    rows.forEach(function(row) {
      var key = String(row.tier);
      if (!grouped[key]) grouped[key] = [];
      grouped[key].push({ resource: row.resource, minLog10: row.minLog10 });
    });
    TIER_UNLOCK_CONDITIONS = grouped;
  } catch (e) {
    console.error('fetchTierUnlocks failed', e);
  }
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

// Людські підписи кодів ресурсів для підказки розблокування тіру (title заблокованого tier-btn).
var _TIER_HINT_RESOURCE_LABELS = { E: 'енергії', p: 'протонів', n: 'нейтронів', e: 'електронів', VC: 'кристалів' };

/**
 * Текст підказки "чого бракує", щоб відкрити тір — data-driven з TIER_UNLOCK_CONDITIONS,
 * з живим прогресом (скільки вже є проти скільки треба). OR-умови з однаковим minLog10<=0
 * (напр. "хоча б одна частинка p/n/e") групуються в один компактний рядок.
 */
function _tierUnlockHint(tierKey) {
  var conditions = TIER_UNLOCK_CONDITIONS[tierKey];
  if (!conditions || !conditions.length) return '';

  var anyLabels = [];
  var thresholdParts = [];
  conditions.forEach(function(c) {
    var label = _TIER_HINT_RESOURCE_LABELS[c.resource] || c.resource;
    if (c.minLog10 <= 0) {
      anyLabels.push(label);
      return;
    }
    var current = resourceLog10(c.resource);
    var currentText = isFinite(current) && current > -300
      ? ' (зараз ~1e' + Math.floor(current) + ')' : '';
    thresholdParts.push('1e' + c.minLog10 + ' ' + label + currentText);
  });
  if (anyLabels.length) thresholdParts.push('хоча б трохи (' + anyLabels.join(', ') + ')');

  return 'Щоб відкрити: ' + thresholdParts.join(' АБО ');
}

/**
 * Перевіряє TIER_UNLOCK_CONDITIONS і знімає клас `locked` з відповідних tier-btn.
 * Показує підказку (title) лише для НАЙБЛИЖЧОГО заблокованого тіру — той, що
 * "наступний" за порядком номера. Тіри далі по черзі лишаються повністю
 * прихованими (locked-hidden, display:none), як і раніше, — інакше гравець
 * бачив би одразу умови Тіру 2/3, хоча ще навіть Тір 1 не відкрив, що більше
 * плутає, ніж допомагає.
 * Викликається з renderLoop / після fetchState.
 */
function refreshTierLocks() {
  if (typeof TIER_UNLOCK_CONDITIONS === 'undefined' || typeof resourceState === 'undefined') return;
  var changed = false;
  var tierNums = Object.keys(TIER_UNLOCK_CONDITIONS).map(Number).sort(function(a, b) { return a - b; });
  var nextHintShown = false;

  tierNums.forEach(function(tierNum) {
    var tierKey = String(tierNum);
    var btn = document.getElementById('tier-btn-' + tierKey);
    if (!btn) return;
    var unlocked = _tierUnlocked(tierKey);
    var wasLocked = btn.classList.contains('locked');
    var wasHidden = btn.classList.contains('locked-hidden');

    if (unlocked) {
      if (wasLocked || wasHidden) {
        btn.classList.remove('locked', 'locked-hidden');
        btn.removeAttribute('title');
        changed = true;
      }
      return;
    }

    if (!nextHintShown) {
      nextHintShown = true;
      if (!wasLocked || wasHidden) changed = true;
      btn.classList.add('locked');
      btn.classList.remove('locked-hidden');
      btn.title = _tierUnlockHint(tierKey);
    } else {
      if (!wasHidden) changed = true;
      btn.classList.add('locked', 'locked-hidden');
      btn.removeAttribute('title');
    }
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
  if (name === 'prestige' && typeof refreshPrestigeInfo === 'function') refreshPrestigeInfo();
  if ((name === 'exchange' || name === 'upgrades_t1') && typeof fetchMatterInfo === 'function') fetchMatterInfo();
  if (name === 'exchange' && typeof renderMatterPage === 'function') renderMatterPage();
  if (name === 'upgrades_t1' && typeof renderMatterUpgrades === 'function') renderMatterUpgrades();
  if (name === 'stats' && typeof fetchStats === 'function') fetchStats();
  if (name === 'stats' && typeof renderStatsPage === 'function') renderStatsPage();
  if ((name === 'periodic_table' || name === 'molecules') && typeof fetchMatterInfo === 'function') fetchMatterInfo();
  if (name === 'periodic_table' && typeof fetchElements === 'function') fetchElements();
  if (name === 'molecules' && typeof fetchMolecules === 'function') fetchMolecules();
  if (name === 'achievements' && typeof fetchAchievements === 'function') fetchAchievements();
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

  if (isCompactNav()) {
    openTierDrawer();
  } else {
    // Десктоп: фіксуємо панель видимою після кліку на іконку етапу.
    // Знімається кліком поза .sidebar/.sub-nav або відкриттям налаштувань.
    document.body.classList.add('subnav-pinned');
  }
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

// Відкриття досягнень: ховаємо другу панель + показуємо сторінку досягнень (як openSettings).
function openAchievements(el) {
  document.body.classList.add('settings-open');
  document.body.classList.remove('subnav-pinned');
  closeTierDrawer();
  document.querySelectorAll('.tier-btn').forEach(function(b) { b.classList.remove('active'); });
  if (el) el.classList.add('active');

  document.querySelectorAll('.page').forEach(function(p) { p.classList.remove('active'); });
  var page = document.getElementById('page-achievements');
  if (page) page.classList.add('active');
  activatePage('achievements');
}

// Відкриття налаштувань: ховаємо другу панель + показуємо сторінку налаштувань
function openSettings(el) {
  document.body.classList.add('settings-open');
  document.body.classList.remove('subnav-pinned');
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

 // Десктоп: клік поза .sidebar та .sub-nav знімає закріплення панелі.
 document.addEventListener('click', function(e) {
   if (isCompactNav()) return;
   if (!document.body.classList.contains('subnav-pinned')) return;
   var sidebar = document.querySelector('.sidebar');
   var subNav = document.getElementById('sub-nav');
   if (sidebar && sidebar.contains(e.target)) return;
   if (subNav && subNav.contains(e.target)) return;
   document.body.classList.remove('subnav-pinned');
 });
