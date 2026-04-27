// Вкладка "Генератори"

// Поточний множник купівлі: 1 / 10 / 100 / -1 (Max)
var buyMult = 1;

function _setupMultButtons() {
  var btns = document.querySelectorAll('.gen-mult-btn');
  btns.forEach(function(btn) {
    btn.addEventListener('click', function() {
      btns.forEach(function(b) { b.classList.remove('active'); });
      btn.classList.add('active');
      buyMult = parseInt(btn.dataset.mult, 10);
      fetchGenerators(); // перемалювати з новим цінником
    });
  });
}

// Сума геометричної прогресії: base * mult^level * (mult^n - 1) / (mult - 1)
// Повертає {number, exponent} через Math.log10, щоб не переповнювалось.
function bulkCost(base, baseExp, mult, level, n) {
  if (n <= 0) return { num: 0, exp: 0 };
  if (Math.abs(mult - 1) < 1e-9) {
    var log = Math.log10(base) + baseExp + Math.log10(n);
    var e = Math.floor(log);
    return { num: Math.pow(10, log - e), exp: e };
  }
  // log10(cost) = log10(base) + baseExp + level*log10(mult) + log10(mult^n - 1) - log10(mult - 1)
  var logMult = Math.log10(mult);
  var logTerm;
  if (n * logMult > 15) {
    // mult^n >> 1 → log10(mult^n - 1) ≈ n*log10(mult)
    logTerm = n * logMult;
  } else {
    logTerm = Math.log10(Math.pow(mult, n) - 1);
  }
  var log = Math.log10(base) + baseExp + level * logMult + logTerm - Math.log10(mult - 1);
  var e = Math.floor(log);
  return { num: Math.pow(10, log - e), exp: e };
}

// Скільки можна дозволити собі купити (up to cap)
function maxAffordable(base, baseExp, mult, level, energyNum, energyExp, cap) {
  var logEnergy = Math.log10(Math.max(energyNum, 1e-12)) + energyExp;
  // Груба оцінка за верхньою межею cost(N) ≈ base * mult^(level+N-1) / (mult-1)
  // log10(cost) ≈ log10(base) + baseExp + (level+N-1)*log10(mult) - log10(mult-1)
  var n;
  if (Math.abs(mult - 1) < 1e-9) {
    var costPer = Math.log10(base) + baseExp;
    n = Math.floor(Math.pow(10, logEnergy - costPer));
  } else {
    var logMult = Math.log10(mult);
    n = Math.floor(
      (logEnergy + Math.log10(mult - 1) - Math.log10(base) - baseExp - (level - 1) * logMult) / logMult
    );
  }
  if (!isFinite(n) || n < 0) n = 0;
  if (n > cap) n = cap;
  // Уточнити: зменшити, поки cost > energy
  while (n > 0) {
    var c = bulkCost(base, baseExp, mult, level, n);
    if (c.exp < energyExp || (c.exp === energyExp && c.num <= energyNum)) break;
    n--;
  }
  return n;
}

function _currentEnergy() {
  var e = (typeof resourceState !== 'undefined') && resourceState.E;
  if (!e) return { num: 0, exp: 0 };
  return { num: e.number || 0, exp: e.exponent || 0 };
}

function _buttonLabel(g) {
  var mult = g.effectiveCostMultiplier || g.costMultiplier;
  if (buyMult > 0) {
    // Фіксована кількість (може не вистачити — сервер купить скільки зможе, але тут показуємо ціль)
    var c = bulkCost(g.baseCostNumber, g.baseCostExponent, mult, g.level, buyMult);
    return 'x' + buyMult + ' · ' + fmtBig(c.num, c.exp) + ' E';
  }
  // Max — скільки реально можна купити зараз
  var en = _currentEnergy();
  var n = maxAffordable(g.baseCostNumber, g.baseCostExponent, mult, g.level,
                         en.num, en.exp, 100000);
  if (n <= 0) return 'Max · —';
  var c2 = bulkCost(g.baseCostNumber, g.baseCostExponent, mult, g.level, n);
  return 'Max (x' + n + ') · ' + fmtBig(c2.num, c2.exp) + ' E';
}

// fmtBig: нормалізоване відображення (num, exp) як наукова нотація.
function fmtBig(num, exp) {
  if (!isFinite(num) || num <= 0) return '—';
  // нормалізувати num у діапазон [1,10)
  while (num >= 10) { num /= 10; exp++; }
  while (num < 1 && exp > 0) { num *= 10; exp--; }
  if (exp === 0) return num.toFixed(2);
  return num.toFixed(2) + 'e' + exp;
}

async function fetchGenerators() {
  var res = await fetch('/api/generators/' + SAVE_ID);
  var data = await res.json();
  var list = document.getElementById('generators-list');
  if (!list) return;

  var visible = [];
  var addedNext = false;
  for (var i = 0; i < data.length; i++) {
    if (data[i].level > 0) {
      visible.push(data[i]);
    } else if (!addedNext) {
      visible.push(data[i]);
      addedNext = true;
    }
  }

  list.innerHTML = visible.map(function(g) {
    var iconIdx = g.iconIndex || g.id || 1;
    var iconSrc = '/img/Generator' + iconIdx + 'Tier1.png';

    // Рівень + фантомні копії
    var phantom = Math.round(g.phantomBonus || 0);
    var levelText;
    if (g.level > 0) {
      levelText = 'x' + g.level;
      if (phantom > 0) levelText += ' <span class="gen-phantom">[+' + phantom + ' фантом]</span>';
    } else {
      levelText = '<span class="gen-locked">Не відкрито</span>';
    }

    // Частка від загальної генерації
    var sharePct = (g.shareOfTotal || 0) * 100;
    var shareText = '';
    if (g.level > 0 && sharePct > 0) {
      var shareStr = sharePct >= 10 ? sharePct.toFixed(1) : sharePct.toFixed(2);
      shareText = ' <span class="gen-share">(' + shareStr + '%)</span>';
    }

    // Виробництво з усіма бустами
    var produced = g.energyPerSec != null ? g.energyPerSec : (g.ratePerLevel * g.level);
    var rateLine = g.level > 0
      ? ('Виробляє ' + fmtRate(produced) + shareText)
      : (g.ratePerLevel + ' E/с за рівень');

    return '<div class="gen-row">' +
      '<img class="gen-icon" src="' + iconSrc + '" alt="" onerror="this.style.visibility=\'hidden\'">' +
      '<div class="gen-left">' +
        '<div class="gen-name">' + g.name + '  <span class="gen-count">' + levelText + '</span></div>' +
        '<div class="gen-level">' + rateLine + '</div>' +
      '</div>' +
      '<button class="gen-buy-btn" onclick="buyGenerator(' + g.id + ')">' +
        _buttonLabel(g) +
      '</button>' +
    '</div>';
  }).join('');
}

async function buyGenerator(id) {
  await fetch('/api/buy-generator', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ saveId: SAVE_ID, generatorId: id, amount: buyMult })
  });
  fetchState();
  fetchGenerators();
}

async function buyAllGenerators() {
  await fetch('/api/buy-generator-all', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ saveId: SAVE_ID })
  });
  fetchState();
  fetchGenerators();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', _setupMultButtons);
} else {
  _setupMultButtons();
}
