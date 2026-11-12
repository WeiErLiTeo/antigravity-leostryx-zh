/* Leostryx — Settings › Appearance › Кастомизация (Leostryx branding).
 * Renders into the Appearance screen (main.js `eLb` calls
 * window.__agyBrandMount(el)). Two controls:
 *   1. Logo pack picker — 4 packs (Owl, Lynx, LSX, Angular X), default 02_lynx.
 *      Swaps the in-app header logo, login-screen logo, splash logo, and
 *      Android home-screen launcher icon immediately without rebuilding the APK.
 *   2. Accent colour table — sets --primary / --primary-foreground live with
 *      priority over theme presets, and re-tints all in-app logos & buttons. */
(function () {
  "use strict";
  if (window.__agyBrandReady) return;
  window.__agyBrandReady = true;

  var STORE_KEY = "agyBrandConfig";

  function getLang() {
    try {
      if (window.__agyI18n && window.__agyI18n.getLanguage) return window.__agyI18n.getLanguage();
      if (window.AgyLang && window.AgyLang.getLang) return window.AgyLang.getLang();
    } catch (e) {}
    var nav = (navigator.language || "").toLowerCase();
    return nav.indexOf("zh") === 0 ? "zh" : nav.indexOf("ru") === 0 ? "ru" : "en";
  }
  function T(r, e, z) {
    var l = getLang();
    if (l === "zh") return z !== undefined ? z : (e || r);
    if (l === "ru") return r;
    return e || r;
  }

  function store(k, v) {
    try {
      if (v === undefined) return localStorage.getItem(k);
      if (v === null) localStorage.removeItem(k);
      else localStorage.setItem(k, v);
    } catch (e) {}
    return null;
  }

  /* ------------------------------------------------------------- catalog */
  var PACKS = [
    { id: "00_original",  ru: "Antigravity (оригинал)", en: "Antigravity", zh: "Antigravity 原版" },
    { id: "01_owl",       ru: "Сова",           en: "Owl",          zh: "猫头鹰 (Owl)" },
    { id: "02_lynx",      ru: "Рысь",           en: "Lynx",         zh: "猞猁 (Lynx)" },
    { id: "03_lsx",       ru: "Монограмма LSX", en: "LSX monogram", zh: "LSX 字母印章" },
    { id: "04_angular_x", ru: "Угловой X",      en: "Angular X",    zh: "锋芒 X (Angular X)" }
  ];
  var DEFAULT_PACK = "02_lynx";

  var COLORS = [
    { hex: "#0b84ff", ru: "Синий",      en: "Blue",    zh: "宝石蓝" },
    { hex: "#3b82f6", ru: "Индиго",     en: "Indigo",  zh: "靛蓝" },
    { hex: "#8b5cf6", ru: "Фиолетовый", en: "Violet",  zh: "罗兰紫" },
    { hex: "#ec4899", ru: "Розовый",    en: "Pink",    zh: "樱桃粉" },
    { hex: "#ef4444", ru: "Красный",    en: "Red",     zh: "中国红" },
    { hex: "#f97316", ru: "Оранжевый",  en: "Orange",  zh: "活力橙" },
    { hex: "#f59e0b", ru: "Янтарный",   en: "Amber",   zh: "琥珀黄" },
    { hex: "#10b981", ru: "Изумрудный", en: "Emerald", zh: "翡翠绿" },
    { hex: "#14b8a6", ru: "Бирюзовый",  en: "Teal",    zh: "青碧绿" },
    { hex: "#64748b", ru: "Серый",      en: "Slate",   zh: "高级灰" },
    { hex: "#0ea5e9", ru: "Небесный",   en: "Sky",     zh: "晴空蓝" },
    { hex: "#a3e635", ru: "Лаймовый",   en: "Lime",    zh: "青柠绿" }
  ];
  var DEFAULT_COLOR = "#0b84ff";

  /* -------------------------------------------------------------- state */
  function loadCfg() {
    var raw = store(STORE_KEY);
    var cfg = { pack: DEFAULT_PACK, color: DEFAULT_COLOR, recolorLogo: false };
    if (raw) {
      try {
        var p = JSON.parse(raw);
        if (p && typeof p === "object") {
          if (p.pack) cfg.pack = p.pack;
          if (p.color) cfg.color = p.color;
          if (p.recolorLogo !== undefined) cfg.recolorLogo = !!p.recolorLogo;
        }
      } catch (e) {}
    }
    if (!PACKS.some(function (p) { return p.id === cfg.pack; })) cfg.pack = DEFAULT_PACK;
    if (!COLORS.some(function (c) { return c.hex.toLowerCase() === String(cfg.color).toLowerCase(); })) cfg.color = DEFAULT_COLOR;
    return cfg;
  }
  var cfg = loadCfg();

  function saveCfg() { store(STORE_KEY, JSON.stringify(cfg)); }

  window.__agyGetBrandAccent = function () {
    return cfg && cfg.color ? cfg.color : DEFAULT_COLOR;
  };

  /* ------------------------------------------------------- colour apply */
  function hexToRgb(hex) {
    var h = String(hex).replace("#", "");
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var n = parseInt(h, 16);
    if (isNaN(n)) return { r: 11, g: 132, b: 255 };
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }
  function luminance(hex) {
    var c = hexToRgb(hex);
    function lin(v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
  }
  function contrastText(hex) { return luminance(hex) > 0.45 ? "#111111" : "#ffffff"; }

  function applyColor(hex) {
    var root = document.documentElement;
    if (!root || !root.style) return;
    var fg = contrastText(hex);
    root.style.setProperty("--primary", hex);
    root.style.setProperty("--primary-foreground", fg);
    root.style.setProperty("--color-primary", hex);
    root.style.setProperty("--color-primary-foreground", fg);
    root.style.setProperty("--accent", hex);
    root.style.setProperty("--brand-accent", hex);

    try {
      var st = document.getElementById("agy-brand-accent-css");
      if (!st) {
        st = document.createElement("style");
        st.id = "agy-brand-accent-css";
        (document.head || root).appendChild(st);
      }
      var css =
        ":root, html, body {\n" +
        "  --primary: " + hex + " !important;\n" +
        "  --primary-foreground: " + fg + " !important;\n" +
        "  --color-primary: " + hex + " !important;\n" +
        "  --color-primary-foreground: " + fg + " !important;\n" +
        "  --accent: " + hex + " !important;\n" +
        "  --brand-accent: " + hex + " !important;\n" +
        "}\n" +
        ".ripple-container .ripple {\n" +
        "  border-color: " + hex + " !important;\n" +
        "}\n";
      if (cfg.recolorLogo) {
        css +=
          ".google-symbols,\n" +
          "[data-testid=\"app-icon-new-conversation-button\"] svg,\n" +
          "[data-testid=\"sidebar-header-logo\"] svg,\n" +
          ".floating-icon,\n" +
          ".animated-logo-container svg,\n" +
          "button[aria-selected=\"true\"] svg,\n" +
          "button[data-state=\"active\"] svg,\n" +
          "[role=\"tab\"][aria-selected=\"true\"] svg,\n" +
          "nav button svg,\n" +
          "header button svg {\n" +
          "  color: " + hex + " !important;\n" +
          "}\n" +
          "[data-testid=\"app-icon-new-conversation-button\"] svg path,\n" +
          ".floating-icon path {\n" +
          "  fill: " + hex + " !important;\n" +
          "}\n";
      }
      st.textContent = css;
    } catch (e) {}
  }

  /* --------------------------------------------------------- logo apply */
  function bridge() {
    return window.AgyTools && typeof window.AgyTools.start === "function" ? window.AgyTools : null;
  }
  function applyLogo(packId, after) {
    var b = bridge();
    if (!b) { if (after) after(false); return; }
    var id = b.start("brand", packId + "@" + String(cfg.color || "").replace("#", ""));
    if (!id || /^ERR/.test(String(id))) { if (after) after(false); return; }
    var tries = 0;
    (function tick() {
      tries++;
      var r = {};
      try { r = JSON.parse(b.read(id) || "{}"); } catch (e) {}
      if (r.rc !== undefined && r.rc !== "") {
        if (after) after(String(r.rc) === "0");
        return;
      }
      if (tries > 240) { if (after) after(false); return; }
      setTimeout(tick, 250);
    })();
  }

  function persistForBuild() {
    try {
      var b = bridge();
      if (!b) return;
      b.start("brand", cfg.pack + "@" + String(cfg.color || "").replace("#", ""));
    } catch (e) {}
  }

  /* --------------------------------------------------------------- UI */
  function makeCard() {
    var c = document.createElement("div");
    c.style.cssText = "background:rgba(255,255,255,.025);border:1px solid rgba(127,127,127,.18);" +
      "border-radius:16px;padding:16px;margin-bottom:12px";
    return c;
  }
  function makeTitle(text) {
    var t = document.createElement("div");
    t.style.cssText = "font-size:16px;font-weight:600;color:var(--foreground,#eee);margin-bottom:4px";
    t.textContent = text;
    return t;
  }
  function makeNote(text) {
    var n = document.createElement("div");
    n.style.cssText = "font-size:12.5px;line-height:1.5;color:#999;margin-bottom:12px";
    n.textContent = text;
    return n;
  }

  function render(el) {
    try {
      _renderInner(el);
    } catch(err) {
      console.error("agy_brand render error:", err);
    }
  }
  function _renderInner(el) {
    el.innerHTML = "";

    /* ── header ── */
    var header = document.createElement("div");
    header.style.cssText = "font-size:18px;font-weight:600;color:var(--foreground,#eee);margin-bottom:12px";
    header.textContent = T("Кастомизация", "Customization", "个性化定制与外观");
    el.appendChild(header);

    /* ── Card 1: logo pack ── */
    var c1 = makeCard();
    c1.appendChild(makeTitle(T("Логотип", "Logo", "应用图标与品牌 Logo")));
    c1.appendChild(makeNote(T(
      "Сова, рысь, монограмма LSX или угловой X. Сразу меняет логотип в шапке, на экране входа, сплэш-скрине и иконку приложения на рабочем столе.",
      "Owl, lynx, LSX monogram or angular X. Immediately updates the header logo, login screen, splash screen and home-screen app icon.",
      "提供猫头鹰、猞猁、LSX 印章或锋芒 X 4套定制图标包。即时更换顶部栏 Logo、登录页 Logo、启动页及桌面应用图标。"
    )));

    var grid = document.createElement("div");
    grid.style.cssText = "display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:10px";

    PACKS.forEach(function (p) {
      var selected = cfg.pack === p.id;
      var cell = document.createElement("button");
      cell.type = "button";
      cell.style.cssText = "position:relative;display:flex;flex-direction:column;align-items:center;gap:8px;" +
        "padding:12px 8px;border-radius:14px;cursor:pointer;font:inherit;text-align:center;" +
        "border:1px solid " + (selected ? cfg.color : "rgba(127,127,127,.22)") + ";" +
        "background:" + (selected ? "color-mix(in srgb, " + cfg.color + " 14%, transparent)" : "rgba(255,255,255,.02)") + ";" +
        "transition:border-color .15s, background .15s";

      var iconWrap = document.createElement("div");
      iconWrap.style.cssText = "width:56px;height:56px;display:flex;align-items:center;justify-content:center;pointer-events:none";
      if (typeof window.__agyBrandSvgMarkup === "function") {
        iconWrap.innerHTML = window.__agyBrandSvgMarkup(p.id, cfg.recolorLogo ? cfg.color : null, 52);
      } else {
        var img = document.createElement("img");
        img.src = "./brand/" + p.id + "/ic_launcher_logo.png";
        img.alt = "";
        img.loading = "lazy";
        img.style.cssText = "width:56px;height:56px;object-fit:contain;pointer-events:none";
        iconWrap.appendChild(img);
      }
      cell.appendChild(iconWrap);

      var lbl = document.createElement("div");
      lbl.style.cssText = "font-size:12.5px;line-height:1.3;color:var(--foreground,#ddd)";
      lbl.textContent = T(p.ru, p.en, p.zh);
      cell.appendChild(lbl);

      if (selected) {
        var tick = document.createElement("span");
        tick.style.cssText = "position:absolute;top:6px;right:8px;font-size:11px;color:" + cfg.color;
        tick.textContent = "✓";
        cell.appendChild(tick);
      }

      cell.onclick = function () {
        if (cfg.pack === p.id) return;
        cfg.pack = p.id;
        saveCfg();
        try {
          window.dispatchEvent(new CustomEvent("agy:brand-changed", { detail: { pack: p.id, color: cfg.color } }));
        } catch (e) {}
        render(el);
        try {
          if (window.AgyTools && typeof window.AgyTools.switchIconPack === "function") {
            window.AgyTools.switchIconPack(p.id);
          }
        } catch (e) {}
        applyLogo(cfg.pack, function (ok) {
          var s = document.getElementById("agy-brand-status");
          if (!s) return;
          s.textContent = ok
            ? T("Логотип и иконка рабочего стола обновлены.",
                "Logo and home-screen icon updated.")
            : T("Логотип обновлён.",
                "Logo updated.");
          s.style.color = "#10b981";
        });
      };
      grid.appendChild(cell);
    });
    c1.appendChild(grid);
    el.appendChild(c1);

    /* ── Card 2: accent colour ── */
    var c2 = makeCard();
    c2.appendChild(makeTitle(T("Цвет интерфейса и кнопок", "UI and button colour", "界面与按钮强调色")));
    c2.appendChild(makeNote(T(
      "Акцентный цвет: задаёт тон кнопок, активных элементов и интерфейсных акцентов.",
      "Accent colour: sets the tint for buttons, active controls and UI accents.",
      "自选全局强调色：即时定制主按钮、激活指示器、光标及界面交互高亮色。"
    )));

    /* Switch: Recolor logos & icons */
    var toggleRow = document.createElement("label");
    toggleRow.style.cssText = "display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;" +
      "background:rgba(255,255,255,.03);border:1px solid rgba(127,127,127,.18);border-radius:12px;margin-bottom:14px;cursor:pointer";

    var toggleInfo = document.createElement("div");
    toggleInfo.style.cssText = "display:flex;flex-direction:column;gap:2px";
    var toggleTitle = document.createElement("div");
    toggleTitle.style.cssText = "font-size:13.5px;font-weight:600;color:var(--foreground,#eee)";
    toggleTitle.textContent = T("Перекрашивать логотипы и значки", "Recolor logos and icons", "将图标重绘为强调色");
    var toggleDesc = document.createElement("div");
    toggleDesc.style.cssText = "font-size:11.5px;color:#999;line-height:1.35";
    toggleDesc.textContent = T(
      "Если выключено — все 4 логотипа и значки сохраняют оригинальные авторские цвета (как на рабочем столе)",
      "When off — all 4 logos and icons retain original designer colors (matching the launcher icon)",
      "开启后图标与 Logo 同步跟随强调色；关闭则保留俄语原作者设计的独立经典配色。"
    );
    toggleInfo.appendChild(toggleTitle);
    toggleInfo.appendChild(toggleDesc);
    toggleRow.appendChild(toggleInfo);

    var toggleSwitch = document.createElement("input");
    toggleSwitch.type = "checkbox";
    toggleSwitch.checked = !!cfg.recolorLogo;
    toggleSwitch.style.cssText = "width:20px;height:20px;accent-color:" + cfg.color + ";cursor:pointer;flex-shrink:0";
    toggleSwitch.onchange = function () {
      cfg.recolorLogo = toggleSwitch.checked;
      saveCfg();
      applyColor(cfg.color);
      try {
        window.dispatchEvent(new CustomEvent("agy:brand-changed", { detail: { pack: cfg.pack, color: cfg.color, recolorLogo: cfg.recolorLogo } }));
      } catch (e) {}
      render(el);
    };
    toggleRow.appendChild(toggleSwitch);
    c2.appendChild(toggleRow);

    var sw = document.createElement("div");
    sw.style.cssText = "display:grid;grid-template-columns:repeat(auto-fill,minmax(44px,1fr));gap:8px";

    COLORS.forEach(function (c) {
      var selected = cfg.color.toLowerCase() === c.hex.toLowerCase();
      var b = document.createElement("button");
      b.type = "button";
      b.title = T(c.ru, c.en, c.zh) + " " + c.hex;
      b.setAttribute("aria-label", T(c.ru, c.en, c.zh));
      b.style.cssText = "position:relative;width:100%;aspect-ratio:1;border-radius:12px;cursor:pointer;" +
        "border:2px solid " + (selected ? "var(--foreground,#eee)" : "transparent") + ";" +
        "background:" + c.hex + ";padding:0;transition:transform .12s";
      b.onmousedown = function () { b.style.transform = "scale(.92)"; };
      b.onmouseup = b.onmouseleave = function () { b.style.transform = ""; };
      b.onclick = function () {
        if (cfg.color.toLowerCase() === c.hex.toLowerCase()) return;
        cfg.color = c.hex;
        saveCfg();
        persistForBuild();
        applyColor(c.hex);
        try {
          window.dispatchEvent(new CustomEvent("agy:brand-changed", { detail: { pack: cfg.pack, color: c.hex } }));
        } catch (e) {}
        render(el);
        var s = document.getElementById("agy-brand-status");
        if (s) {
          s.textContent = T("Акцентный цвет применён: ", "Accent colour applied: ") + c.hex.toUpperCase();
          s.style.color = "#10b981";
        }
      };
      sw.appendChild(b);
    });
    c2.appendChild(sw);

    /* current colour chip + hex */
    var cur = document.createElement("div");
    cur.style.cssText = "display:flex;align-items:center;gap:10px;margin-top:12px;font-size:13px;color:#999";
    var chip = document.createElement("span");
    chip.style.cssText = "width:18px;height:18px;border-radius:6px;background:" + cfg.color +
      ";border:1px solid rgba(127,127,127,.3);flex-shrink:0";
    cur.appendChild(chip);
    var curTxt = document.createElement("span");
    curTxt.style.cssText = "font-family:ui-monospace,monospace";
    curTxt.textContent = cfg.color.toUpperCase();
    cur.appendChild(curTxt);
    c2.appendChild(cur);
    el.appendChild(c2);

    
    /* ── UI Scaling (DPI) & Model Menu Sizing Controls ── */
    var cScale = document.createElement("div");
    cScale.style.cssText = "margin-bottom:18px";
    var cScaleHead = document.createElement("div");
    cScaleHead.style.cssText = "font-size:13px;font-weight:600;margin-bottom:6px;color:var(--foreground,#f1f5f9)";
    cScaleHead.textContent = T("Масштаб интерфейса и меню", "UI Scaling & Menu Size", "界面缩放与菜单大小 (DPI)");
    cScale.appendChild(cScaleHead);

    var curScale = localStorage.getItem("agy_ui_scale") || "100";
    var curMenuScale = localStorage.getItem("agy_menu_scale") || "100";

    var scaleRow = document.createElement("div");
    scaleRow.style.cssText = "display:flex;flex-direction:column;gap:10px;padding:12px 14px;border-radius:10px;background:var(--muted,rgba(255,255,255,0.04));border:1px solid var(--border,rgba(255,255,255,0.08))";

    scaleRow.innerHTML = 
      '<div style="display:flex;align-items:center;justify-content:space-between;">' +
      '  <span style="font-size:12.5px;color:var(--foreground,#f1f5f9);font-weight:500;">' + T("Общий масштаб интерфейса", "Global UI Scale", "全局界面缩放 (DPI)") + '</span>' +
      '  <span id="agy-scale-val" style="font-size:12px;font-family:monospace;color:var(--primary,#38bdf8);font-weight:600;">' + curScale + '%</span>' +
      '</div>' +
      '<input type="range" id="agy-scale-slider" min="85" max="140" step="5" value="' + curScale + '" style="width:100%;accent-color:var(--primary,#38bdf8);cursor:pointer;" />' +
      '<div style="display:flex;align-items:center;justify-content:space-between;margin-top:6px;">' +
      '  <span style="font-size:12.5px;color:var(--foreground,#f1f5f9);font-weight:500;">' + T("Размер меню выбора модели", "Model Menu Font & Spacing", "模型选择弹窗字体与间距") + '</span>' +
      '  <span id="agy-menu-scale-val" style="font-size:12px;font-family:monospace;color:var(--primary,#38bdf8);font-weight:600;">' + curMenuScale + '%</span>' +
      '</div>' +
      '<input type="range" id="agy-menu-scale-slider" min="90" max="150" step="5" value="' + curMenuScale + '" style="width:100%;accent-color:var(--primary,#38bdf8);cursor:pointer;" />';

    cScale.appendChild(scaleRow);
    el.appendChild(cScale);

    /* ── Card 4: Code Syntax Themes & Live Preview (Task 4) ── */
    var cTheme = makeCard();
    cTheme.appendChild(makeTitle(T("Тема подсветки синтаксиса", "Code Syntax Theme", "代码高亮主题与实时预览")));
    cTheme.appendChild(makeNote(T(
      "Выберите цветовую схему для блоков кода и редактора. Ниже отображается интерактивный предпросмотр подсветки синтаксиса.",
      "Select a color scheme for code blocks and the editor. Below is an interactive syntax highlighting preview.",
      "自选代码块与编辑区着色方案。支持经典浅色/深色及流行开发者配色预设，下方提供实时高亮预览卡片。"
    )));

    var SYNTAX_PRESETS = [
      { id: "Default Dark", label: "Default Dark (默认深色)", bg: "#101010", fg: "#cccccc", primary: "#007acc", kw: "#569cd6", fn: "#dcdcaa", str: "#ce9178", com: "#6a9955" },
      { id: "One Dark Pro", label: "One Dark Pro",            bg: "#282c34", fg: "#abb2bf", primary: "#61afef", kw: "#c678dd", fn: "#61afef", str: "#98c379", com: "#5c6370" },
      { id: "Dracula",      label: "Dracula",                 bg: "#282a36", fg: "#f8f8f2", primary: "#bd93f9", kw: "#ff79c6", fn: "#50fa7b", str: "#f1fa8c", com: "#6272a4" },
      { id: "Tokyo Night",  label: "Tokyo Night",             bg: "#1a1b26", fg: "#a9b1d6", primary: "#7aa2f7", kw: "#bb9af7", fn: "#7aa2f7", str: "#9ece6a", com: "#565f89" },
      { id: "Monokai",      label: "Monokai",                 bg: "#272822", fg: "#f8f8f2", primary: "#f92672", kw: "#f92672", fn: "#a6e22e", str: "#e6db74", com: "#75715e" },
      { id: "Catppuccin",   label: "Catppuccin (Macchiato)",  bg: "#24273a", fg: "#cad3f5", primary: "#c6a0f6", kw: "#c6a0f6", fn: "#8aadf4", str: "#a6da95", com: "#6e738d" },
      { id: "Solarized Dark", label: "Solarized Dark",        bg: "#002b36", fg: "#93a1a1", primary: "#268bd2", kw: "#859900", fn: "#268bd2", str: "#2aa198", com: "#586e75" },
      { id: "Default Light", label: "Default Light (默认浅色)", bg: "#f9f9f9", fg: "#101010", primary: "#007acc", kw: "#0000ff", fn: "#795e26", str: "#a31515", com: "#008000" },
      { id: "One Light",    label: "One Light",               bg: "#f5f5f5", fg: "#383a42", primary: "#4078f2", kw: "#a626a4", fn: "#4078f2", str: "#50a14f", com: "#a0a1a7" }
    ];

    var curPresetId = localStorage.getItem("theme-preset-dark") || localStorage.getItem("agy_syntax_preset") || "One Dark Pro";
    var curPreset = SYNTAX_PRESETS.find(function(p) { return p.id === curPresetId; }) || SYNTAX_PRESETS[1];

    var presetSelectWrap = document.createElement("div");
    presetSelectWrap.style.cssText = "display:flex;flex-direction:column;gap:8px;margin-bottom:12px";
    
    var selLabel = document.createElement("div");
    selLabel.style.cssText = "font-size:12.5px;color:var(--foreground,#f1f5f9);font-weight:500;";
    selLabel.textContent = T("Схема подсветки кода", "Code Theme Preset", "预设方案：");
    presetSelectWrap.appendChild(selLabel);

    var sel = document.createElement("select");
    sel.style.cssText = "width:100%;padding:8px 12px;border-radius:10px;background:rgba(255,255,255,0.06);color:var(--foreground,#f1f5f9);border:1px solid rgba(127,127,127,0.25);font-size:13px;outline:none;cursor:pointer;";
    SYNTAX_PRESETS.forEach(function(p) {
      var opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = p.label;
      opt.style.background = "#222";
      opt.style.color = "#fff";
      if (p.id === curPreset.id) opt.selected = true;
      sel.appendChild(opt);
    });
    presetSelectWrap.appendChild(sel);
    cTheme.appendChild(presetSelectWrap);

    /* Live Preview Box */
    var previewBox = document.createElement("div");
    previewBox.id = "agy-syntax-preview-box";
    previewBox.style.cssText = "border-radius:12px;border:1px solid rgba(127,127,127,0.2);padding:14px;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:12.5px;line-height:1.6;overflow-x:auto;box-shadow:inset 0 1px 4px rgba(0,0,0,0.2);transition:all 0.2s ease;";

    function updatePreview(p) {
      previewBox.style.backgroundColor = p.bg;
      previewBox.style.color = p.fg;
      previewBox.innerHTML = 
        '<div style="display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid rgba(127,127,127,0.15);padding-bottom:6px;margin-bottom:10px;">' +
        '  <span style="font-size:11px;color:rgba(127,127,127,0.8);letter-spacing:0.5px;">PREVIEW: theme_preview.ts</span>' +
        '  <span style="width:10px;height:10px;border-radius:50%;background:' + p.primary + ';display:inline-block;"></span>' +
        '</div>' +
        '<div><span style="color:' + p.kw + ';font-weight:600;">import</span> { <span style="color:' + p.fn + ';">AgentWindow</span> } <span style="color:' + p.kw + ';font-weight:600;">from</span> <span style="color:' + p.str + ';">"@leostryx/core"</span>;</div>' +
        '<div style="color:' + p.com + ';font-style:italic;">// ' + T("Настройки темы применены успешно", "Theme applied successfully", "代码着色器工作正常") + '</div>' +
        '<div><span style="color:' + p.kw + ';font-weight:600;">export async function</span> <span style="color:' + p.fn + ';">initSession</span>(): <span style="color:' + p.primary + ';">Promise</span>&lt;<span style="color:' + p.primary + ';">boolean</span>&gt; {</div>' +
        '  <div>  <span style="color:' + p.kw + ';font-weight:600;">const</span> model = <span style="color:' + p.str + ';">"Gemini 2.5 Pro"</span>;</div>' +
        '  <div>  <span style="color:' + p.kw + ';font-weight:600;">return</span> <span style="color:' + p.fn + ';">mountAgent</span>({ model, fps: <span style="color:' + p.primary + ';">120</span> });</div>' +
        '<div>}</div>';
    }
    updatePreview(curPreset);
    cTheme.appendChild(previewBox);

    sel.onchange = function() {
      var found = SYNTAX_PRESETS.find(function(p) { return p.id === sel.value; });
      if (found) {
        localStorage.setItem("theme-preset-dark", found.id);
        localStorage.setItem("theme-preset-light", found.id);
        localStorage.setItem("agy_syntax_preset", found.id);
        updatePreview(found);
      }
    };

    el.appendChild(cTheme);


    setTimeout(function() {
      var sl = scaleRow.querySelector("#agy-scale-slider");
      var slVal = scaleRow.querySelector("#agy-scale-val");
      var mSl = scaleRow.querySelector("#agy-menu-scale-slider");
      var mSlVal = scaleRow.querySelector("#agy-menu-scale-val");
      if (sl) {
        sl.oninput = function() {
          slVal.textContent = sl.value + "%";
          localStorage.setItem("agy_ui_scale", sl.value);
          if (window.__agyApplyScaling) window.__agyApplyScaling();
        };
      }
      if (mSl) {
        mSl.oninput = function() {
          mSlVal.textContent = mSl.value + "%";
          localStorage.setItem("agy_menu_scale", mSl.value);
          if (window.__agyApplyScaling) window.__agyApplyScaling();
        };
      }
    }, 50);

    /* ── status ── */
    var st = document.createElement("div");
    st.id = "agy-brand-status";
    st.style.cssText = "font-size:12.5px;line-height:1.5;color:#999;min-height:18px;padding:0 2px";
    st.textContent = T(
      "Логотип: " + packName(cfg.pack) + " · Цвет: " + cfg.color.toUpperCase(),
      "Logo: " + packNameEn(cfg.pack) + " · Colour: " + cfg.color.toUpperCase()
    );
    el.appendChild(st);
  }

  function packName(id) {
    var p = PACKS.filter(function (x) { return x.id === id; })[0];
    return p ? T(p.ru, p.en, p.zh) : id;
  }
  function packNameEn(id) {
    var p = PACKS.filter(function (x) { return x.id === id; })[0];
    return p ? p.en : id;
  }

  /* ------------------------------------------------------------- mount */
  window.__agyBrandMount = function (el) {
    if (!el) return;
    try {
      applyColor(cfg.color);
      el.style.cssText = "padding:4px 4px 24px;max-width:620px;box-sizing:border-box";
      render(el);
    } catch (err) {
      console.warn("Brand mount error:", err);
    }
  };

  window.__agyApplyScaling = function () {
    var scale = parseInt(localStorage.getItem("agy_ui_scale") || "100", 10) / 100;
    var menuScale = parseInt(localStorage.getItem("agy_menu_scale") || "100", 10) / 100;
    var styleId = "agy-scale-override-style";
    var st = document.getElementById(styleId);
    if (!st) {
      st = document.createElement("style");
      st.id = styleId;
      document.head.appendChild(st);
    }
    st.textContent =
      (scale !== 1 ? "html, body { zoom: " + scale + "; } " : "") +
      (menuScale !== 1 ?
        "[data-testid='model-selector-panel'] [data-testid='model-selector-item'] { font-size: " + (14 * menuScale).toFixed(1) + "px !important; padding: " + (8 * menuScale).toFixed(1) + "px " + (10 * menuScale).toFixed(1) + "px !important; } " +
        "[data-testid='model-selector-panel'] [data-testid='model-selector-header'] { font-size: " + (12 * menuScale).toFixed(1) + "px !important; } " +
        "[data-testid='bottom-sheet'] :is(button, [role='menuitem']) { font-size: " + (14 * menuScale).toFixed(1) + "px !important; } "
        : "");
  };

  
  /* ------------------------------------------------ MutationObserver Auto-Mount */
  function scanAndMount() {
    try {
      var hosts = document.querySelectorAll(".agy-brand-host");
      for (var i = 0; i < hosts.length; i++) {
        var host = hosts[i];
        if (!host.__brandMounted) {
          window.__agyBrandMount(host);
        }
      }
    } catch (e) {
      console.warn("scanAndMount error:", e);
    }
  }

  if (typeof window !== "undefined" && window.MutationObserver) {
    try {
      var _brandObserver = new MutationObserver(function(mutations) {
        scanAndMount();
      });
      var targetNode = document.body || document.documentElement;
      if (targetNode) {
        _brandObserver.observe(targetNode, { childList: true, subtree: true });
      } else {
        document.addEventListener("DOMContentLoaded", function() {
          _brandObserver.observe(document.body, { childList: true, subtree: true });
        });
      }
    } catch (e) {
      console.warn("MutationObserver setup failed:", e);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      applyColor(cfg.color);
      window.__agyApplyScaling();
    });
  } else {
    applyColor(cfg.color);
    window.__agyApplyScaling();
  }
})();
