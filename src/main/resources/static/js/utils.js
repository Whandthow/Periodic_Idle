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

 var OPTIMIZED_PNGS = {
   AtomTear2: true,
   Electron: true,
   Energy: true,
   Generator1Tier1: true,
   Generator2Tier1: true,
   Generator3Tier1: true,
   Generator4Tier1: true,
   Generator5Tier1: true,
   Generator6Tier1: true,
   Neutron: true,
   Proton: true,
   VoidCrystal: true,
   VoidVortexTir1: true
 };

 function pngPath(name) {
   return '/img/' + name + '.png';
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
