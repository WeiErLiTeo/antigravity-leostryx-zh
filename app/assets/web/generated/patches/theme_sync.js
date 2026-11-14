(function(){
/**
 * patches/theme_sync/theme_sync.js - deep status/navigation bar immersion
 * including secondary pages, bottom sheets, modals, dimmed backdrops, and presets.
 */
'use strict';

if (window.__agThemeSync && window.__agThemeSync.__installed) {
  return;
}

var doc = window.document;
var hostTheme = 'system';
try {
  var params = new URLSearchParams(window.location.search);
  hostTheme = params.get('hostTheme') || window.__AG_HOST_THEME__ || 'system';
} catch (e) {
  hostTheme = window.__AG_HOST_THEME__ || 'system';
}
if (hostTheme !== 'light' && hostTheme !== 'dark') {
  hostTheme = 'system';
}

var state = {
  installed: false,
  last: null,
  lastBars: null,
  notifications: 0,
  observer: null,
  domObserver: null,
  storageBound: false,
  hasAndroid: false,
  timers: []
};

function fromClassList() {
  try {
    var body = doc && doc.body;
    var root = doc && doc.documentElement;
    if (body && body.classList) {
      if (body.classList.contains('theme-light')) return 'light';
      if (body.classList.contains('theme-dark')) return 'dark';
    }
    if (root && root.classList) {
      if (root.classList.contains('theme-light') || root.classList.contains('light')) return 'light';
      if (root.classList.contains('theme-dark') || root.classList.contains('dark')) return 'dark';
    }
  } catch (e) { /* ignore */ }
  return null;
}

function resolveTheme() {
  var cl = fromClassList();
  if (cl) return cl;
  if (hostTheme === 'light') return 'light';
  if (hostTheme === 'dark') return 'dark';
  if (typeof window !== 'undefined' && window.Android && typeof window.Android.getTheme === 'function') {
    try {
      var t = window.Android.getTheme();
      if (t === 'dark' || t === 'light') return t;
    } catch (e) {}
  }
  if (typeof window !== 'undefined' && window.__AG_RESOLVED_THEME__) {
    return window.__AG_RESOLVED_THEME__;
  }
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
}

function parseCssColor(str) {
  if (!str || str === 'transparent' || str === 'none') return null;
  var s = String(str).trim();
  if (s.charAt(0) === '#') {
    var h = s.slice(1);
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    if (h.length >= 6) {
      var r = parseInt(h.slice(0, 2), 16);
      var g = parseInt(h.slice(2, 4), 16);
      var b = parseInt(h.slice(4, 6), 16);
      var a = h.length === 8 ? (parseInt(h.slice(6, 8), 16) / 255) : 1;
      if (!isNaN(r) && !isNaN(g) && !isNaN(b)) return { r: r, g: g, b: b, a: a };
    }
  }
  var m = s.match(/rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\/\s]+([\d.]+))?\s*\)/i);
  if (m) {
    var rr = Math.max(0, Math.min(255, parseFloat(m[1])));
    var gg = Math.max(0, Math.min(255, parseFloat(m[2])));
    var bb = Math.max(0, Math.min(255, parseFloat(m[3])));
    var aa = m[4] !== undefined ? Math.max(0, Math.min(1, parseFloat(m[4]))) : 1;
    if (aa <= 0.005) return null;
    return { r: rr, g: gg, b: bb, a: aa };
  }
  return null;
}

function blendOver(fg, bg) {
  var a = fg.a != null ? fg.a : 1;
  if (a >= 0.995) return { r: fg.r, g: fg.g, b: fg.b, a: 1 };
  return {
    r: fg.r * a + bg.r * (1 - a),
    g: fg.g * a + bg.g * (1 - a),
    b: fg.b * a + bg.b * (1 - a),
    a: 1
  };
}

function toHex(c) {
  function h(v) {
    var n = Math.max(0, Math.min(255, Math.round(v))).toString(16);
    return n.length === 1 ? '0' + n : n;
  }
  return '#' + h(c.r) + h(c.g) + h(c.b);
}

function isColorDark(c) {
  function lin(v) {
    v /= 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  }
  var lum = 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
  return lum < 0.45;
}

function getBaseCanvasColor(theme) {
  try {
    var body = doc.body;
    var root = doc.documentElement;
    var bc = body ? parseCssColor(window.getComputedStyle(body).backgroundColor) : null;
    if (bc && bc.a >= 0.95) return { r: bc.r, g: bc.g, b: bc.b, a: 1 };
    var rc = root ? parseCssColor(window.getComputedStyle(root).backgroundColor) : null;
    if (rc && rc.a >= 0.95) return { r: rc.r, g: rc.g, b: rc.b, a: 1 };
    if (root) {
      var v = window.getComputedStyle(root).getPropertyValue('--background').trim();
      var vc = parseCssColor(v);
      if (vc && vc.a >= 0.95) return { r: vc.r, g: vc.g, b: vc.b, a: 1 };
    }
  } catch (e) {}
  return theme === 'dark' ? { r: 16, g: 16, b: 16, a: 1 } : { r: 255, g: 255, b: 255, a: 1 };
}

function extractBgFromStyle(cs) {
  if (!cs) return null;
  var col = parseCssColor(cs.backgroundColor);
  if (col && col.a > 0.01) return col;
  var img = cs.backgroundImage;
  if (img && img !== 'none' && img.indexOf('gradient') >= 0) {
    var m = img.match(/rgba?\([^)]+\)|#[0-9a-fA-F]{3,8}/);
    if (m) return parseCssColor(m[0]);
  }
  return null;
}

function effectiveOpacity(el) {
  var op = 1;
  var cur = el;
  var depth = 0;
  while (cur && cur.nodeType === 1 && depth < 6) {
    try {
      var cs = window.getComputedStyle(cur);
      if (cs.display === 'none' || cs.visibility === 'hidden') return 0;
      var o = parseFloat(cs.opacity);
      if (!isNaN(o)) op *= o;
      if (op <= 0.01) return 0;
    } catch (e) {}
    cur = cur.parentElement;
    depth++;
  }
  return op;
}

function samplePoint(x, y, baseColor) {
  try {
    if (typeof doc.elementsFromPoint !== 'function') return baseColor;
    var els = doc.elementsFromPoint(x, y);
    if (!els || !els.length) return baseColor;
    var acc = { r: baseColor.r, g: baseColor.g, b: baseColor.b, a: 1 };
    // Bottom-to-top compositing (skip html/body since baseColor already represents them)
    for (var i = els.length - 1; i >= 0; i--) {
      var el = els[i];
      if (!el || el === doc.documentElement || el === doc.body) continue;
      if (el.id === 'agy-copy-toast' || (el.getAttribute && el.getAttribute('data-agy-toast') === '1')) continue;
      var cs = window.getComputedStyle(el);
      if (!cs) continue;
      if (cs.visibility === 'hidden' || cs.display === 'none') continue;
      var z = parseInt(cs.zIndex, 10);
      if (!isNaN(z) && z >= 2147483640 && cs.pointerEvents === 'none') continue;
      var bg = extractBgFromStyle(cs);
      if (!bg) continue;
      var op = effectiveOpacity(el);
      if (op <= 0.01) continue;
      acc = blendOver({ r: bg.r, g: bg.g, b: bg.b, a: bg.a * op }, acc);
    }
    return acc;
  } catch (e) {
    return baseColor;
  }
}

function sampleEdge(y, vw, baseColor) {
  var xs = [Math.round(vw * 0.2), Math.round(vw * 0.5), Math.round(vw * 0.8)];
  var samples = xs.map(function (x) { return samplePoint(x, y, baseColor); });
  // Prefer median-ish / consensus color so a centered pill/notch doesn't skew edges
  var h0 = toHex(samples[0]), h1 = toHex(samples[1]), h2 = toHex(samples[2]);
  if (h0 === h2) return samples[0];
  if (h1 === h0 || h1 === h2) return samples[1];
  return samples[0];
}

function sampleBars(theme) {
  var base = getBaseCanvasColor(theme);
  try {
    var vw = window.innerWidth || (doc.documentElement && doc.documentElement.clientWidth) || 360;
    var vh = window.innerHeight || (doc.documentElement && doc.documentElement.clientHeight) || 640;
    if (vw < 40 || vh < 40) return { top: base, bottom: base };
    var topY = Math.min(8, Math.max(2, Math.floor(vh * 0.01)));
    var botY = Math.max(topY + 2, vh - 6);
    var topCol = sampleEdge(topY, vw, base);
    var botCol = sampleEdge(botY, vw, base);
    return { top: topCol, bottom: botCol };
  } catch (e) {
    return { top: base, bottom: base };
  }
}

function pushVisualBars(theme) {
  try {
    if (!window.Android || typeof window.Android.onVisualThemeChanged !== 'function') return;
    state.hasAndroid = true;
    var bars = sampleBars(theme);
    var topHex = toHex(bars.top);
    var botHex = toHex(bars.bottom);
    var sDark = isColorDark(bars.top) ? '1' : '0';
    var nDark = isColorDark(bars.bottom) ? '1' : '0';
    var msg = 'bars:' + topHex + ',' + botHex + ',' + sDark + ',' + nDark;
    if (msg === state.lastBars) return;
    state.lastBars = msg;
    window.Android.onVisualThemeChanged(msg);
  } catch (e) { /* never throw */ }
}

function notify() {
  var theme = resolveTheme();
  var themeChanged = (theme !== state.last);
  if (themeChanged) {
    state.last = theme;
    state.notifications++;
    try {
      var isDark = (theme === 'dark');
      doc.documentElement.classList.toggle('dark', isDark);
      doc.documentElement.classList.toggle('light', !isDark);
      doc.documentElement.style.colorScheme = theme;
    } catch (e) { /* ignore */ }
    try {
      if (typeof window.__agyUpdateGlassTheme === 'function') {
        window.__agyUpdateGlassTheme();
      }
    } catch (e) { /* ignore */ }
    try {
      if (window.Android) {
        state.hasAndroid = true;
        if (typeof window.Android.setThemeByUser === 'function') {
          window.Android.setThemeByUser(theme);
        } else if (typeof window.Android.setTheme === 'function') {
          window.Android.setTheme(theme);
        }
      }
    } catch (e) { /* ignore */ }
  }
  pushVisualBars(theme);
  return theme;
}

function scheduleSettledSync() {
  notify();
  try {
    [45, 160, 340].forEach(function (ms) {
      var tid = setTimeout(function () { pushVisualBars(resolveTheme()); }, ms);
      state.timers.push(tid);
      if (state.timers.length > 24) {
        clearTimeout(state.timers.shift());
      }
    });
  } catch (e) {}
}

var domRaf = 0;
function queueDomSync() {
  if (domRaf) return;
  domRaf = window.requestAnimationFrame ? window.requestAnimationFrame(function () {
    domRaf = 0;
    scheduleSettledSync();
  }) : setTimeout(function () {
    domRaf = 0;
    scheduleSettledSync();
  }, 16);
}

function install() {
  if (state.installed) return;
  state.installed = true;

  if (window.MutationObserver && doc.documentElement) {
    try {
      state.observer = new MutationObserver(function () {
        state.lastBars = null;
        scheduleSettledSync();
      });
      state.observer.observe(doc.documentElement, {
        attributes: true,
        attributeFilter: ['class', 'style', 'data-theme']
      });
      var bindBody = function () {
        if (!doc.body) return;
        if (state.observer) {
          state.observer.observe(doc.body, {
            attributes: true,
            attributeFilter: ['class', 'style', 'data-theme']
          });
        }
        if (!state.domObserver) {
          state.domObserver = new MutationObserver(function () { queueDomSync(); });
          state.domObserver.observe(doc.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class', 'style', 'data-state', 'open', 'aria-hidden']
          });
        }
        scheduleSettledSync();
      };
      if (doc.body) bindBody();
      else if (doc.addEventListener) doc.addEventListener('DOMContentLoaded', bindBody);
    } catch (e) { /* ignore */ }
  }

  try {
    if (!state.storageBound) {
      window.addEventListener('storage', function () { state.lastBars = null; scheduleSettledSync(); }, false);
      window.addEventListener('resize', function () { state.lastBars = null; queueDomSync(); }, { passive: true });
      window.addEventListener('popstate', function () { scheduleSettledSync(); }, { passive: true });
      window.addEventListener('hashchange', function () { scheduleSettledSync(); }, { passive: true });
      window.addEventListener('transitionend', function () { pushVisualBars(resolveTheme()); }, { passive: true, capture: true });
      window.addEventListener('animationend', function () { pushVisualBars(resolveTheme()); }, { passive: true, capture: true });
      window.addEventListener('agy:brand-changed', function () { state.lastBars = null; scheduleSettledSync(); });
      doc.addEventListener('click', function () { scheduleSettledSync(); }, { passive: true, capture: true });
      doc.addEventListener('visibilitychange', function () {
        if (doc.visibilityState === 'visible') {
          state.lastBars = null;
          scheduleSettledSync();
        }
      });
      state.storageBound = true;
    }
    if (window.matchMedia) {
      var mql = window.matchMedia('(prefers-color-scheme: dark)');
      if (mql.addEventListener) {
        mql.addEventListener('change', function () { state.lastBars = null; scheduleSettledSync(); });
      }
    }
  } catch (e) { /* ignore */ }

  scheduleSettledSync();
}

window.__agThemeSync = {
  __installed: true,
  getTheme: resolveTheme,
  sync: function () {
    state.lastBars = null;
    return notify();
  },
  probe: function () {
    return {
      installed: state.installed,
      theme: resolveTheme(),
      last: state.last,
      lastBars: state.lastBars,
      notifications: state.notifications,
      hostTheme: hostTheme,
      hasAndroid: state.hasAndroid,
      observers: state.observer ? 1 : 0
    };
  }
};

if (doc && doc.readyState === 'loading') {
  doc.addEventListener('DOMContentLoaded', install);
} else {
  install();
}

})();
