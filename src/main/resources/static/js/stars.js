// Тір 4: Зорі головної послідовності — пасивний синтез H -> He з часом.
// Ризик: якщо Гідрогену не вистачить на тік зорі — гіпернова обнуляє всі
// синтезовані атоми/молекули (StarService.tick(), backend).

var starsState = { stars: [], hydrogenAvailable: 0, hypernovaCount: 0 };
var starsFetchInFlight = false;

async function fetchStars() {
  if (starsFetchInFlight || typeof SAVE_ID !== 'number') return;
  starsFetchInFlight = true;
  try {
    var res = await fetch('/api/stars/' + SAVE_ID);
    if (!res.ok) return;
    starsState = await res.json();
    if (isPageActive('stars')) renderStarsPage();
  } catch (e) {
    console.error('fetchStars failed', e);
  } finally {
    starsFetchInFlight = false;
  }
}

function _fmtBig(n) {
  if (n == null || !isFinite(n) || n === 0) return '0';
  if (Math.abs(n) < 100000) return n.toFixed(n < 10 ? 2 : 0);
  var exp = Math.floor(Math.log10(Math.abs(n)));
  return (n / Math.pow(10, exp)).toFixed(2) + 'e' + exp;
}

function renderStarsPage() {
  var intro = document.getElementById('stars-intro');
  var grid = document.getElementById('stars-grid');
  if (!grid) return;

  var stars = starsState.stars || [];
  var hAvailable = starsState.hydrogenAvailable || 0;
  var hypernovaCount = starsState.hypernovaCount || 0;

  var cnoActive = !!starsState.cnoCatalystActive;
  var cnoMult = starsState.cnoCatalystMult || 1;

  if (intro) {
    intro.innerHTML = 'Зоря пасивно перетворює Гідроген (вже синтезований у Тірі 2) на Гелій ' +
      'з часом — реальний proton-proton chain (4 H → 1 He), як у справжніх зорях головної послідовності.' +
      '<div class="stars-intro-sub">Гідрогену в запасі: ' + _fmtBig(hAvailable) + '</div>' +
      (cnoActive
        ? '<div class="stars-intro-sub">CNO-каталіз активний (C+N+O синтезовано): ×' + cnoMult +
          ' до пропускної здатності зорі</div>'
        : '<div class="stars-intro-sub">CNO-каталіз: синтезуй Карбон, Нітроген і Оксиген (Z=6,7,8), ' +
          'щоб прискорити фузію — реальний CNO-цикл</div>') +
      (hypernovaCount > 0
        ? '<div class="stars-intro-warn">⚠ Гіпернов пережито: ' + hypernovaCount +
          ' — кожна обнуляла всі синтезовані атоми й молекули</div>'
        : '<div class="stars-intro-warn-hint">⚠ Якщо Гідрогену не вистачить на споживання зорі — ' +
          'вона вибухне гіпернового і обнулить усі синтезовані атоми/молекули. Слідкуй за балансом ' +
          'видобутку й автосинтезу Гідрогену.</div>');
  }

  grid.innerHTML = '<div class="stars-grid">' + stars.map(function(s) {
    var fuelPerSec = s.fuelHPerSec || 0;
    var risky = s.level > 0 && fuelPerSec > 0 && hAvailable < fuelPerSec * 5;
    var cls = 'star-card' + (s.level > 0 ? ' active' : '') + (risky ? ' risky' : '');
    var canAfford = hAvailable >= s.nextLevelCostHydrogen;
    return '<div class="' + cls + '">' +
      '<div class="star-card-name">' + s.name + '</div>' +
      '<div class="star-card-level">Рівень ' + s.level + '</div>' +
      (s.level > 0
        ? '<div class="star-card-rates">−' + _fmtBig(fuelPerSec) + ' H/с · +' +
          _fmtBig(s.outputHePerSec) + ' He/с</div>'
        : '<div class="star-card-rates">Ще не запалена</div>') +
      '<div class="star-card-cost">Наступний рівень: ' + _fmtBig(s.nextLevelCostHydrogen) + ' H</div>' +
      '<button class="star-buy-btn" ' + (canAfford ? '' : 'disabled') +
        ' onclick="buyStar(' + s.id + ')">Прокачати</button>' +
      '<div class="star-card-msg" id="star-msg-' + s.id + '"></div>' +
    '</div>';
  }).join('') + '</div>';
}

async function buyStar(starId) {
  var msgEl = document.getElementById('star-msg-' + starId);
  try {
    var res = await fetch('/api/buy-star', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ saveId: SAVE_ID, starId: starId, amount: 1 })
    });
    if (!res.ok) {
      var txt = await res.text();
      var errMsg = txt;
      try { var p = JSON.parse(txt); errMsg = p.error || p.message || txt; } catch (_) {}
      if (msgEl) msgEl.textContent = errMsg;
      return;
    }
    if (msgEl) msgEl.textContent = 'Прокачано!';
    await fetchStars();
  } catch (e) {
    console.error('buyStar failed', e);
    if (msgEl) msgEl.textContent = 'Помилка: ' + e;
  }
}
