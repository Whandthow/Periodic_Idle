// Вкладка "Статистика" — множники й per-generator розбивка з /api/stats

var statsState = { multipliers: [], generators: [], totalEnergyPerSec: 0 };
var statsFetchInFlight = false;

async function fetchStats() {
  if (statsFetchInFlight) return;
  statsFetchInFlight = true;
  try {
    var res = await fetch('/api/stats/' + SAVE_ID);
    statsState = await res.json();
    if (isPageActive('stats')) renderStatsPage();
  } finally {
    statsFetchInFlight = false;
  }
}

function _fmtStat(v) {
  if (v == null || !isFinite(v)) return '—';
  if (Math.abs(v) < 1000) {
    var s = v.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
    return s === '' || s === '-' ? '0' : s;
  }
  var exp = Math.floor(Math.log10(Math.abs(v)));
  return (v / Math.pow(10, exp)).toFixed(2) + 'e' + exp;
}

function renderStatsPage() {
  var container = document.getElementById('stats-content');
  if (!container) return;

  var mults = statsState.multipliers || [];
  var gens = statsState.generators || [];

  var multsHtml = mults.map(function(m) {
    return '<div class="stats-row">' +
      '<div class="stats-row-name"><span class="stats-row-level">' + (m.level || 0) + '</span>' + m.name + '</div>' +
      '<div class="stats-row-value">×' + _fmtStat(m.value) + '</div>' +
      '<div class="stats-row-meta">' + m.formula + '</div>' +
    '</div>';
  }).join('');

  var gensHtml = gens.map(function(g) {
    var sharePct = (g.share || 0) * 100;
    var shareStr = sharePct >= 10 ? sharePct.toFixed(1) : sharePct.toFixed(2);
    return '<div class="stats-gen">' +
      '<div class="stats-gen-name"><span class="stats-row-level">x' + g.level + '</span>' + g.name + '</div>' +
      '<div class="stats-gen-rate">' + fmtRate(g.energyPerSec) + '</div>' +
      '<div class="stats-gen-meta">' + shareStr + '% від загального' +
        (g.phantomBonus > 0 ? ' · фантом +' + Math.round(g.phantomBonus) : '') +
        (g.genSpecificMult && g.genSpecificMult !== 1 ? ' · буст ×' + _fmtStat(g.genSpecificMult) : '') +
        (g.genStackMult && g.genStackMult !== 1 ? ' · стек ×' + _fmtStat(g.genStackMult) : '') +
      '</div>' +
    '</div>';
  }).join('');

  container.innerHTML =
    '<div class="stats-total">Загальне виробництво: <strong>' + fmtRate(statsState.totalEnergyPerSec) + '</strong></div>' +
    '<div class="stats-section-title">Множники</div>' +
    '<div class="stats-list">' + (multsHtml || '<div class="empty-hint">Немає даних</div>') + '</div>' +
    '<div class="stats-section-title">Генератори</div>' +
    '<div class="stats-gens">' + (gensHtml || '<div class="empty-hint">Немає активних генераторів</div>') + '</div>';
}
