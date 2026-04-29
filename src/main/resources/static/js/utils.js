// Форматування чисел для гри

function fmt(number, exponent) {
  if (number === 0 || !isFinite(number)) return '0';
  if (exponent >= 0 && exponent <= 3) {
    var val = number * Math.pow(10, exponent);
    return val >= 100 ? val.toFixed(0) : val.toFixed(1);
  }
  return number.toFixed(2) + 'e' + exponent;
}

function fmtRate(ratePerSec) {
  if (ratePerSec == null || !isFinite(ratePerSec) || ratePerSec === 0) return '0/с';
  if (Math.abs(ratePerSec) < 1000) return ratePerSec.toFixed(1) + '/с';
  var exp = Math.floor(Math.log10(Math.abs(ratePerSec)));
  return (ratePerSec / Math.pow(10, exp)).toFixed(2) + 'e' + exp + '/с';
}

function fmtCost(num, exp, mult, level) {
  if (level === 0) return fmt(num, exp);
  var scaled = num * Math.pow(mult, level);
  var extraExp = Math.floor(Math.log10(scaled));
  return fmt(scaled / Math.pow(10, extraExp), exp + extraExp);
}

 function pngPath(name) {
   return '/img/' + name + '.png';
 }

 function resourceLog10(code) {
   if (typeof resourceState === 'undefined' || !resourceState) return -Infinity;
   var r = resourceState[code];
   if (!r || !r.number || r.number <= 0) return -Infinity;
   return Math.log10(r.number) + r.exponent;
 }

 function resourceAtLeast(code, number, exponent) {
   if (typeof resourceState === 'undefined' || !resourceState) return false;
   var r = resourceState[code];
   if (!r || !r.number || r.number <= 0) return false;
   if ((r.exponent || 0) !== exponent) return (r.exponent || 0) > exponent;
   return (r.number || 0) >= number;
 }

 function upgradeLevel(code) {
   if (typeof upgradesState === 'undefined' || !upgradesState || !upgradesState.byCode) return 0;
   var upg = upgradesState.byCode[code];
   return upg ? (upg.currentLevel || 0) : 0;
 }

 function hasBrokenInfinity() {
   return typeof matterState !== 'undefined' && !!matterState.brokenInfinity;
 }

 function isMatterTierUnlocked() {
   return resourceLog10('E') >= MATTER_UNLOCK_LOG10 || resourceLog10('p') > -Infinity || resourceLog10('n') > -Infinity || resourceLog10('e') > -Infinity || hasBrokenInfinity();
 }

 function hydrateDeferredMedia(root) {
   if (!root || !root.querySelectorAll) return;
   root.querySelectorAll('img[data-src]').forEach(function(img) {
     var dataSrc = img.getAttribute('data-src');
     if (dataSrc && !img.getAttribute('src')) img.setAttribute('src', dataSrc);
     img.removeAttribute('data-src');
   });
 }

 function isPageActive(name) {
   var page = document.getElementById('page-' + name);
   return !!(page && page.classList.contains('active'));
 }
