// Сторінка "Досягнення" — data-driven список з /api/achievements/{saveId}

var achievementsState = [];
var achievementsFetchInFlight = false;

async function fetchAchievements() {
  if (achievementsFetchInFlight || typeof SAVE_ID !== 'number') return;
  achievementsFetchInFlight = true;
  try {
    var res = await fetch('/api/achievements/' + SAVE_ID);
    if (!res.ok) return;
    achievementsState = await res.json();
    if (isPageActive('achievements')) renderAchievementsPage();
  } catch (e) {
    console.error('fetchAchievements failed', e);
  } finally {
    achievementsFetchInFlight = false;
  }
}

function _fmtUnlockedAt(iso) {
  if (!iso) return '';
  var d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function renderAchievementsPage() {
  var container = document.getElementById('achievements-list');
  if (!container) return;

  if (!achievementsState.length) {
    container.innerHTML = '<div class="empty-hint">Завантаження…</div>';
    return;
  }

  var unlockedCount = achievementsState.filter(function(a) { return a.unlocked; }).length;
  var summary = '<div class="achievements-summary">Розблоковано ' + unlockedCount + ' / ' + achievementsState.length + '</div>';

  var cards = achievementsState.map(function(a) {
    var cls = 'achievement-card' + (a.unlocked ? ' unlocked' : ' locked');
    var meta = a.unlocked
      ? '<div class="achievement-meta">Розблоковано ' + _fmtUnlockedAt(a.unlockedAt) + '</div>'
      : '<div class="achievement-meta">Ще не розблоковано</div>';
    return '<div class="' + cls + '">' +
      '<div class="achievement-name">' + a.name + '</div>' +
      '<div class="achievement-desc">' + a.description + '</div>' +
      meta +
      '</div>';
  }).join('');

  container.innerHTML = summary + '<div class="achievements-grid">' + cards + '</div>';
}
