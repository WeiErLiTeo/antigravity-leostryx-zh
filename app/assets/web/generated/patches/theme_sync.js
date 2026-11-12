// Early Theme Sync — applies initial dark/light class before main.js renders,
// preserves "Follow System" mode in Android SharedPreferences, and syncs the
// exact computed background / custom theme / backdrop color to Android's
// native Status Bar and Navigation Bar.
(function () {
  var DARK_BG = '#101010';
  var LIGHT_BG = '#ffffff';

  var resolved = null;
  try {
    if (window.Android && typeof window.Android.getTheme === 'function') {
      var t = window.Android.getTheme();
      if (t === 'dark' || t === 'light') resolved = t;
    }
  } catch (e) {}

  if (!resolved) {
    try {
      if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
        resolved = 'light';
      } else {
        resolved = 'dark';
      }
    } catch (e) {
      resolved = 'dark';
    }
  }

  function applyEarly(theme) {
    var isDark = theme !== 'light';
    var bg = isDark ? DARK_BG : LIGHT_BG;
    var fg = isDark ? LIGHT_BG : DARK_BG;
    var root = document.documentElement;
    if (root) {
      root.style.colorScheme = isDark ? 'dark' : 'light';
      root.style.backgroundColor = bg;
      root.style.color = fg;
      root.setAttribute('data-agy-early-theme', isDark ? 'dark' : 'light');
    }
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', bg);
  }

  applyEarly(resolved);

  function parseRgb(str) {
    if (!str) return null;
    str = String(str).trim();
    if (str.charAt(0) === '#') {
      var hex = str.slice(1);
      if (hex.length === 3) {
        hex = hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
      }
      if (hex.length >= 6) {
        var r = parseInt(hex.slice(0, 2), 16);
        var g = parseInt(hex.slice(2, 4), 16);
        var b = parseInt(hex.slice(4, 6), 16);
        if (!isNaN(r) && !isNaN(g) && !isNaN(b)) return [r, g, b];
      }
    }
    var m = str.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)/i);
    if (m) {
      var a = m[4] !== undefined ? parseFloat(m[4]) : 1;
      if (a === 0) return null;
      return [parseInt(m[1], 10), parseInt(m[2], 10), parseInt(m[3], 10)];
    }
    return null;
  }

  function detectSurfaceRgb(isDark) {
    try {
      var body = document.body;
      var root = document.documentElement;
      var rgb = null;
      if (body) {
        var csBody = window.getComputedStyle(body);
        rgb = parseRgb(csBody.getPropertyValue('--background')) || parseRgb(csBody.backgroundColor);
      }
      if (!rgb && root) {
        var csRoot = window.getComputedStyle(root);
        rgb = parseRgb(csRoot.getPropertyValue('--background')) || parseRgb(csRoot.backgroundColor);
      }
      if (!rgb) {
        rgb = isDark ? [16, 16, 16] : [255, 255, 255];
      }
      return rgb;
    } catch (e) {
      return isDark ? [16, 16, 16] : [255, 255, 255];
    }
  }

  function rgbToSignedArgb(rgb, isDark) {
    var r = Math.max(0, Math.min(255, rgb[0] | 0));
    var g = Math.max(0, Math.min(255, rgb[1] | 0));
    var b = Math.max(0, Math.min(255, rgb[2] | 0));
    // Bit 0 of blue channel carries the isDark flag (1 = dark theme / light icons, 0 = light theme / dark icons)
    b = (b & 0xfe) | (isDark ? 1 : 0);
    var unsigned = ((0xff << 24) | (r << 16) | (g << 8) | b) >>> 0;
    return (unsigned | 0);
  }

  function rgbToHex(rgb) {
    var h = function (n) {
      var s = Math.max(0, Math.min(255, n | 0)).toString(16);
      return s.length < 2 ? '0' + s : s;
    };
    return '#' + h(rgb[0]) + h(rgb[1]) + h(rgb[2]);
  }

  // Wrap Android.onVisualThemeChanged so any caller passing "dark"/"light" or an integer string works seamlessly
  var rawOnVisualThemeChanged = null;
  try {
    if (window.Android && typeof window.Android.onVisualThemeChanged === 'function') {
      rawOnVisualThemeChanged = window.Android.onVisualThemeChanged.bind(window.Android);
    }
  } catch (e) {}

  var lastSentPayload = null;
  var lastTheme = resolved;

  function detectThemeFromBody() {
    var b = document.body;
    if (!b) return null;
    var cls = b.classList;
    if (!cls) return null;
    if (cls.contains('theme-light') || cls.contains('light')) return 'light';
    if (cls.contains('theme-dark') || cls.contains('dark')) return 'dark';
    var cs = null;
    try { cs = window.getComputedStyle(b); } catch (e) {}
    if (cs) {
      var rgb = parseRgb(cs.getPropertyValue('--background')) || parseRgb(cs.backgroundColor);
      if (rgb) {
        var lum = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]);
        return lum > 145 ? 'light' : 'dark';
      }
    }
    return null;
  }

  function syncNativeBars(forceTheme) {
    var t = (forceTheme === 'light' || forceTheme === 'dark') ? forceTheme : (detectThemeFromBody() || lastTheme || 'dark');
    lastTheme = t;
    var isDark = (t !== 'light');
    var rgb = detectSurfaceRgb(isDark);
    var hex = rgbToHex(rgb);

    var root = document.documentElement;
    if (root) {
      root.style.colorScheme = isDark ? 'dark' : 'light';
      root.style.backgroundColor = hex;
    }
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', hex);

    var packedInt = rgbToSignedArgb(rgb, isDark);
    var payload = String(packedInt);
    if (payload === lastSentPayload) return;
    lastSentPayload = payload;

    try {
      if (rawOnVisualThemeChanged) {
        rawOnVisualThemeChanged(payload);
      } else if (window.Android && typeof window.Android.onVisualThemeChanged === 'function') {
        window.Android.onVisualThemeChanged(payload);
      }
    } catch (e) {}
  }

  // Override window.Android.onVisualThemeChanged if possible so external calls with "dark"/"light" also send packed int
  try {
    if (window.Android && rawOnVisualThemeChanged) {
      window.Android.onVisualThemeChanged = function (arg) {
        if (arg === 'dark' || arg === 'light') {
          syncNativeBars(arg);
        } else if (arg !== undefined && arg !== null && !isNaN(parseInt(arg, 10))) {
          rawOnVisualThemeChanged(String(parseInt(arg, 10)));
        } else {
          syncNativeBars();
        }
      };
    }
  } catch (e) {}

  var syncTimer = 0;
  function scheduleSync() {
    if (syncTimer) clearTimeout(syncTimer);
    syncTimer = setTimeout(function () {
      syncTimer = 0;
      syncNativeBars();
    }, 16);
  }

  function attachObserver() {
    if (!document.body) {
      setTimeout(attachObserver, 16);
      return;
    }
    syncNativeBars();
    var obs = new MutationObserver(scheduleSync);
    obs.observe(document.body, {
      attributes: true,
      attributeFilter: ['class', 'style', 'data-theme'],
      childList: true
    });
    if (document.documentElement) {
      obs.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['class', 'style', 'data-theme']
      });
    }
    try {
      if (window.matchMedia) {
        var mq = window.matchMedia('(prefers-color-scheme: dark)');
        if (mq && typeof mq.addEventListener === 'function') {
          mq.addEventListener('change', function () {
            setTimeout(syncNativeBars, 50);
          });
        }
      }
    } catch (e) {}
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', attachObserver, { once: true });
  } else {
    attachObserver();
  }
})();
