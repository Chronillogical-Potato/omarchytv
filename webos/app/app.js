/* Cinemarchy has its own version, independent of the OmarchyTV app version. */
var CINEMARCHY_VERSION = "0.1.0-alpha";
(function () {
  "use strict";

  var DATA = window.OMARCHY_DATA || { version: "2.13.0-beta", themes: {}, wallpapers: {}, logo: "OMARCHY TV", notice: "" };
  var START_TIME = Date.now() - (42 * 60 * 1000); // default 42m uptime look

  var STRINGS = {
    en: {
      tip_cinemarchy: "Cinemarchy (Media & TV)", tip_wifi: "Wi-Fi & network", tip_bt: "Bluetooth", tip_vol: "Audio", tip_inputs: "Inputs (TV / HDMI)", tip_saver: "Start screensaver",
      sys_clock: "Clock",
      box_weather: "weather", box_pc: "omarchy · pc", box_ai: "ai credits · pc", box_tv: "tv", box_app: "app", box_ws_pc: "pc", box_system: "system",
      term_theme: "theme ", term_net: "net ", term_up: "up ",
      wp_title: "Wallpaper", wp_sub: "Any theme's background. A picked one stays when you change theme.",
      wp_follow: "Follow the theme", wp_plain: "Theme colours, no image",
      pc_not_connected: "pc not connected",
      hint_tv: "OK watch TV · ▲ bar · long OK: Walker",
      hint_app: "OK open · ▲ bar · long OK: Move / Hide",
      hint_ws2: "OK switch input · ▲ bar · 1-5 workspaces",
      hint_ws3: "OK open · long OK: Move / Hide · 1-5 workspaces",
      ws4_heading: "Connect Omarchy Linux",
      ws4_desc: "Pair the PC over LAN HTTP (http://<pc-ip>:8765/v1/status) to see live CPU/RAM, AI credits and deploys on LG webOS.",
      sys_webos: "webOS TV settings", sys_themes: "Themes", sys_trans: "Transparency", sys_widgets: "Home widgets",
      sys_arrange: "Arrange panels", sys_saver: "Screensaver", sys_saver_idle: "Start screensaver after",
      walker_none: "no matching apps",
      sys_logo_anim: "Animated logo", sys_text_tiles: "Text tiles (terminal style)", sys_all: "All OmarchyTV settings", sys_about: "About & licences",
      tile_move: "Move", tile_hide: "Hide", tile_cancel: "Cancel", move_hint: "◀▶▲▼ move · OK save · Back cancel",
      app_hidden: "Hidden. Show it again in All settings › Hidden apps", hidden_apps: "Hidden apps", hidden_none: "No hidden apps",
      hidden_sub: "OK shows the app again", hidden_show: "show", hidden_show_all: "Show all hidden apps", hidden_all_shown: "All apps visible again", random_theme: "Random theme", random_wallpaper: "Random wallpaper / intro", sys_plugins: "Plugins (SpaceX TV & Grok Bot)", reset_order: "Reset app order", order_reset: "App order reset",
      wx_title: "Weather", wx_auto: "Automatic (from IP)", wx_search: "Search city…", wx_unit: "Temperature unit",
      root_boot: "Open when the TV starts", root_home: "Home button opens OmarchyTV", root_needed: "rooted TV only",
      root_keep: "Keep loaded in the background", root_keep_na: "not on this TV",
      root_missing: "Needs a rooted TV with Homebrew Channel (unrooted: see homewatch/ in the repo)", root_failed: "Could not change it: ",
      wx_min: "min ", wx_max: "max ",
      wmo: { 0: "clear", 1: "mostly clear", 2: "partly cloudy", 3: "overcast", 45: "fog", 51: "drizzle", 61: "rain", 71: "snow", 80: "showers", 95: "thunderstorm" }
    },
    it: {
      tip_cinemarchy: "Cinemarchy (Media e TV)", tip_wifi: "Wi-Fi e rete", tip_bt: "Bluetooth", tip_vol: "Audio", tip_inputs: "Ingressi (TV / HDMI)", tip_saver: "Avvia salvaschermo",
      sys_clock: "Orologio",
      box_weather: "meteo", box_pc: "omarchy · pc", box_ai: "crediti ia · pc", box_tv: "tv", box_app: "app", box_ws_pc: "pc", box_system: "sistema",
      term_theme: "tema ", term_net: "rete ", term_up: "up ",
      wp_title: "Sfondo", wp_sub: "Lo sfondo di qualsiasi tema. Quello scelto resta anche cambiando tema.",
      wp_follow: "Segui il tema", wp_plain: "Colori del tema, senza immagine",
      pc_not_connected: "pc non connesso",
      hint_tv: "OK guarda TV · ▲ barra · OK lungo: Walker",
      hint_app: "OK apri · ▲ barra · OK lungo: Sposta / Nascondi",
      hint_ws2: "OK cambia ingresso · ▲ barra · 1-5 spazi",
      hint_ws3: "OK apri · OK lungo: Sposta / Nascondi · 1-5 spazi",
      ws4_heading: "Collega Omarchy Linux",
      ws4_desc: "Abbina il PC via HTTP LAN (http://<ip-pc>:8765/v1/status) per vedere stato, crediti IA e deploy qui.",
      sys_webos: "Impostazioni TV webOS", sys_themes: "Temi", sys_trans: "Trasparenza", sys_widgets: "Widget della Home",
      sys_arrange: "Disponi pannelli", sys_saver: "Salvaschermo", sys_saver_idle: "Avvia salvaschermo dopo",
      walker_none: "nessuna app trovata",
      sys_logo_anim: "Logo animato", sys_text_tiles: "Tile solo testo (stile terminale)", sys_all: "Tutte le impostazioni OmarchyTV", sys_about: "Info e licenze",
      tile_move: "Sposta", tile_hide: "Nascondi", tile_cancel: "Annulla", move_hint: "◀▶▲▼ sposta · OK salva · Indietro annulla",
      app_hidden: "Nascosta. Mostrala di nuovo in Tutte le impostazioni › App nascoste", hidden_apps: "App nascoste", hidden_none: "Nessuna app nascosta",
      hidden_sub: "OK mostra di nuovo l'app", hidden_show: "mostra", hidden_show_all: "Mostra tutte le app nascoste", hidden_all_shown: "Tutte le app sono di nuovo visibili", random_theme: "Tema casuale", random_wallpaper: "Sfondo / intro casuale", sys_plugins: "Plugin (SpaceX TV e Grok Bot)", reset_order: "Ripristina ordine app", order_reset: "Ordine app ripristinato",
      wx_title: "Meteo", wx_auto: "Automatica (da IP)", wx_search: "Cerca città…", wx_unit: "Unità temperatura",
      root_boot: "Apri all'avvio della TV", root_home: "Il tasto Home apre OmarchyTV", root_needed: "solo TV con root",
      root_keep: "Tieni caricata in background", root_keep_na: "non su questa TV",
      root_missing: "Serve una TV con root e Homebrew Channel (senza root: vedi homewatch/ nel repo)", root_failed: "Impossibile cambiarlo: ",
      wx_min: "min ", wx_max: "max ",
      wmo: { 0: "sereno", 1: "poco nuvoloso", 2: "parz. nuvoloso", 3: "coperto", 45: "nebbia", 51: "pioviggine", 61: "pioggia", 71: "neve", 80: "rovesci", 95: "temporale" }
    },
    de: {
      tip_cinemarchy: "Cinemarchy (Medien & TV)", tip_wifi: "WLAN & Netzwerk", tip_bt: "Bluetooth", tip_vol: "Audio", tip_inputs: "Eingänge (TV / HDMI)", tip_saver: "Bildschirmschoner starten",
      sys_clock: "Uhr",
      box_weather: "wetter", box_pc: "omarchy · pc", box_ai: "ki-guthaben · pc", box_tv: "tv", box_app: "app", box_ws_pc: "pc", box_system: "system",
      term_theme: "thema ", term_net: "netz ", term_up: "up ",
      wp_title: "Hintergrund", wp_sub: "Der Hintergrund eines beliebigen Themas. Ein gewählter bleibt beim Themawechsel.",
      wp_follow: "Dem Thema folgen", wp_plain: "Themafarben, kein Bild",
      pc_not_connected: "pc nicht verbunden",
      hint_tv: "OK TV ansehen · ▲ Leiste · lang OK: Walker",
      hint_app: "OK öffnen · ▲ Leiste · lang OK: Verschieben / Ausblenden",
      hint_ws2: "OK Eingang wechseln · ▲ Leiste · 1-5 Arbeitsbereiche",
      hint_ws3: "OK öffnen · lang OK: Verschieben / Ausblenden · 1-5 Arbeitsbereiche",
      ws4_heading: "Omarchy Linux verbinden",
      ws4_desc: "Koppeln Sie den PC über LAN HTTP (http://<pc-ip>:8765/v1/status), um Status, KI-Guthaben und Deploys anzuzeigen.",
      sys_webos: "webOS TV-Einstellungen", sys_themes: "Themen", sys_trans: "Transparenz", sys_widgets: "Startseiten-Widgets",
      sys_arrange: "Panels anordnen", sys_saver: "Bildschirmschoner", sys_saver_idle: "Bildschirmschoner starten nach",
      walker_none: "keine passende App",
      sys_logo_anim: "Animiertes Logo", sys_text_tiles: "Text-Kacheln (Terminal-Stil)", sys_all: "Alle OmarchyTV-Einstellungen", sys_about: "Info & Lizenzen",
      tile_move: "Verschieben", tile_hide: "Ausblenden", tile_cancel: "Abbrechen", move_hint: "◀▶▲▼ verschieben · OK speichern · Zurück abbrechen",
      app_hidden: "Ausgeblendet. Wieder einblenden unter Alle Einstellungen › Ausgeblendete Apps", hidden_apps: "Ausgeblendete Apps", hidden_none: "Keine ausgeblendeten Apps",
      hidden_sub: "OK blendet die App wieder ein", hidden_show: "einblenden", hidden_show_all: "Alle ausgeblendeten Apps einblenden", hidden_all_shown: "Alle Apps wieder sichtbar", random_theme: "Zufälliges Thema", random_wallpaper: "Zufälliges Hintergrundbild / Intro", sys_plugins: "Plugins (SpaceX TV & Grok Bot)", reset_order: "App-Reihenfolge zurücksetzen", order_reset: "App-Reihenfolge zurückgesetzt",
      wx_title: "Wetter", wx_auto: "Automatisch (über IP)", wx_search: "Stadt suchen…", wx_unit: "Temperatureinheit",
      root_boot: "Beim Einschalten öffnen", root_home: "Home-Taste öffnet OmarchyTV", root_needed: "nur gerootete TVs",
      root_keep: "Im Hintergrund geladen lassen", root_keep_na: "nicht auf diesem TV",
      root_missing: "Benötigt einen gerooteten TV mit Homebrew Channel (ohne Root: siehe homewatch/ im Repo)", root_failed: "Konnte nicht geändert werden: ",
      wx_min: "Min ", wx_max: "Max ",
      wmo: { 0: "klar", 1: "überwiegend klar", 2: "teilweise bewölkt", 3: "bedeckt", 45: "Nebel", 51: "Nieselregen", 61: "Regen", 71: "Schnee", 80: "Schauer", 95: "Gewitter" }
    }
  };

  var DEFAULT_CFG = {
    theme: "last-horizon",
    randomTheme: true,
    randomWallpaper: true,
    pluginSpacexTv: true,
    pluginGrokbot: true,
    grokbotMessage: "", 
    lang: "",
    opacity: 0.75,
    wallpaperDim: 0,
    wallpaperIdx: 0,
    wallpaper: "",        // "" follows the theme, "plain" = theme colours, else a path from DATA.wallpapers
    proceduralBg: false,
    tempUnit: "C",
    weatherCity: "Alessandria",
    weatherLat: 44.913,
    weatherLon: 8.615,
    logoAnim: true,
    textTiles: false,
    tvEnabled: true,
    saverIdleMin: 2.5,
    saverMode: "aquarium",
    saverArt: "aquarium",
    saverSound: true,
    saverLogoScale: 0.65,
    saverShowClock: false,
    wallpaperIntro: true,
    antiBurnIn: true,
    offIdleMin: 0,
    offMode: "screen",
    promptUser: "user",
    promptHost: "omarchytv",
    pcHost: "",
    pcSimulated: false,
    aiCredits: "percent",
    clockFormat: "auto",   // "auto" (12 h in English, 24 h otherwise), "12" or "24"
    widgets: { term: true, weather: true, pc: true, ai: true, deploy: false },
    hiddenApps: [],
    appOrder: []   // app ids in the user's order; apps not listed keep their DEFAULT_APPS position after them
  };

  var cfg = Object.assign({}, DEFAULT_CFG);
  try {
    var saved = localStorage.getItem("omarchytv_cfg_2110");
    if (saved) cfg = Object.assign({}, DEFAULT_CFG, JSON.parse(saved));
  } catch (e) {}

  function saveCfg() {
    try { localStorage.setItem("omarchytv_cfg_2110", JSON.stringify(cfg)); } catch (e) {}
  }

  function getLang() {
    if (cfg.lang && STRINGS[cfg.lang]) return cfg.lang;
    var nav = (navigator.language || "en").slice(0, 2).toLowerCase();
    return STRINGS[nav] ? nav : "en";
  }

  function tr(key) {
    var l = getLang();
    return (STRINGS[l] && STRINGS[l][key]) || STRINGS.en[key] || key;
  }

  var DEFAULT_APPS = [
    { id: "tv", name: "TV", appId: "com.webos.app.livetv", brand: "tv" },
    { id: "disney", name: "Disney+", appId: "com.disney.disneyplus-prod", brand: "disney", bg: "radial-gradient(circle at 50% 35%, #113ccf 0%, #050b24 85%)", fg: "#ffffff", sub: "DISNEY+" },
    { id: "netflix", name: "Netflix", appId: "netflix", brand: "netflix", bg: "linear-gradient(135deg, #1b0000 0%, #090909 100%)", fg: "#e50914", sub: "NETFLIX" },
    { id: "plex", name: "Plex", appId: "cdp-30", brand: "plex", bg: "linear-gradient(135deg, #1f2326 0%, #121417 100%)", fg: "#e5a00d", sub: "plex" },
    { id: "prime", name: "Prime Video", appId: "amazon", brand: "prime", bg: "linear-gradient(135deg, #00273d 0%, #05111a 100%)", fg: "#00a8e1", sub: "prime video" },
    { id: "spotify", name: "Spotify", appId: "spotify-beehive", brand: "spotify", bg: "linear-gradient(135deg, #0c2916 0%, #080d0a 100%)", fg: "#1db954", sub: "Spotify" },
    { id: "youtube", name: "YouTube", appId: "youtube.leanback.v4", brand: "youtube", bg: "linear-gradient(135deg, #2b0707 0%, #0f0f0f 100%)", fg: "#ff0033", sub: "YouTube" },
    { id: "jellyfin", name: "Jellyfin", appId: "org.jellyfin.webos", brand: "jellyfin", bg: "linear-gradient(135deg, #1b112c 0%, #0b0914 100%)", fg: "#aa5cc3", sub: "Jellyfin" },
    { id: "moonlight", name: "Moonlight", appId: "com.limelight.webos", brand: "moonlight", bg: "linear-gradient(135deg, #1b2432 0%, #0d1117 100%)", fg: "#cdd9e5", sub: "Moonlight" },
    { id: "raiplay", name: "RaiPlay", appId: "it.rai.raiplay", brand: "raiplay", bg: "linear-gradient(135deg, #001c44 0%, #000b1e 100%)", fg: "#ffffff", sub: "RaiPlay" },
    { id: "browser", name: "Web Browser", appId: "com.webos.app.browser", brand: "browser", bg: "linear-gradient(135deg, #1e293b 0%, #0f172a 100%)", fg: "#38bdf8", sub: "WWW" },
    { id: "settings", name: "LG Settings", appId: "com.palm.app.settings", brand: "settings", bg: "linear-gradient(135deg, #27272a 0%, #18181b 100%)", fg: "#f43f5e", sub: "webOS" }
  ];

  var TV_INPUTS = [
    { id: "livetv", name: "TV", sub: "Live TV", appId: "com.webos.app.livetv" },
    { id: "hdmi1", name: "HDMI 1", sub: "HDMI 1", appId: "com.webos.app.hdmi1" },
    { id: "hdmi2", name: "HDMI 2", sub: "HDMI 2", appId: "com.webos.app.hdmi2" },
    { id: "hdmi3", name: "HDMI 3", sub: "HDMI 3", appId: "com.webos.app.hdmi3" },
    { id: "hdmi4", name: "HDMI 4", sub: "HDMI 4", appId: "com.webos.app.hdmi4" }
  ];

  var state = {
    ws: 0,
    zone: "dock", // "bar", "widgets", "dock", "ws2", "ws3", "ws4", "ws5", "modal", "arrange"
    barIdx: 1,
    widgetIdx: 0,
    dockIdx: 0,
    dockScroll: 0,
    ws2Idx: 0,
    ws3Idx: 0,
    ws4Idx: 0,
    ws5Idx: 0,
    modalOpen: false,
    modalItems: [],
    modalIdx: 0,
    movingId: null,     // id of the app being moved (long OK › Move), null otherwise
    moveSnapshot: null, // cfg.appOrder before the move, restored on Back
    okTimer: null,      // pending long-press timer for OK on an app tile
    okHeld: false,      // OK is still held after a long press: ignore its repeats and key-up
    weather: { tempC: 9, minC: 6, maxC: 16, code: 0, city: cfg.weatherCity || "Alessandria" },
    pcStatus: null,
    root: null    // rooted-TV helper: null = not checked yet, false = no Homebrew Channel, else { boot, home }
  };

  window.__OMARCHY_STATE = state;
  window.__OMARCHY_CFG = cfg;

  function hexToRgba(hex, a) {
    var h = (hex || "#1a1b26").replace("#", "");
    if (h.length === 3) h = h[0]+h[0]+h[1]+h[1]+h[2]+h[2];
    var r = parseInt(h.slice(0,2), 16) || 26;
    var g = parseInt(h.slice(2,4), 16) || 27;
    var b = parseInt(h.slice(4,6), 16) || 38;
    return "rgba(" + r + "," + g + "," + b + "," + a + ")";
  }

  function currentThemeObj() {
    var t = DATA.themes[cfg.theme] || DATA.themes["tokyo-night"] || {};
    var n = t.normal || {};
    var b = t.bright || {};
    var bg = t.background || t.bg || "#1a1b26";
    var fg = t.foreground || t.fg || "#a9b1d6";
    return {
      name: cfg.theme || "tokyo-night",
      bg: bg,
      bgAlt: t.lighter_background || n.black || "#24283b",
      fg: fg,
      fgBright: t.bright_foreground || b.white || fg,
      fgDim: t.dark_foreground || b.black || "#565f89",
      muted: t.muted || b.black || "#414868",
      accent: t.accent || t.blue || n.blue || "#7aa2f7",
      accent2: t.bright_magenta || t.magenta || n.magenta || "#bb9af7",
      magenta: t.magenta || n.magenta || "#bb9af7",
      green: t.green || n.green || "#9ece6a",
      yellow: t.yellow || n.yellow || "#e0af68",
      red: t.red || n.red || "#f7768e",
      cyan: t.cyan || n.cyan || "#7dcfff"
    };
  }

  function applyTheme() {
    var t = currentThemeObj();
    var r = document.documentElement.style;
    r.setProperty("--t-bg", t.bg);
    r.setProperty("--t-bg-alt", t.bgAlt);
    r.setProperty("--t-fg", t.fg);
    r.setProperty("--t-fg-bright", t.fgBright);
    r.setProperty("--t-fg-dim", t.fgDim);
    r.setProperty("--t-muted", t.muted);
    r.setProperty("--t-accent", t.accent);
    r.setProperty("--t-accent2", t.accent2);
    r.setProperty("--t-green", t.green);
    r.setProperty("--t-yellow", t.yellow);
    r.setProperty("--t-red", t.red);
    r.setProperty("--t-cyan", t.cyan);
    r.setProperty("--win-bg", hexToRgba(t.bg, cfg.opacity));
    r.setProperty("--bar-bg", hexToRgba(t.bg, Math.min(0.88, cfg.opacity + 0.05)));
    r.setProperty("--popup-bg", hexToRgba(t.bg, 0.95));

    var wl = document.getElementById("wallpaper-layer");
    var wv = document.getElementById("wallpaper-video");
    var wlist = DATA.wallpapers[cfg.theme] || [];
    var picked = cfg.wallpaper && cfg.wallpaper !== "plain" && allWallpapers().indexOf(cfg.wallpaper) >= 0 ? cfg.wallpaper : "";
    var activeWp = "";
    if (picked) {
      activeWp = picked;
      wl.style.backgroundImage = "url('" + picked + "')";
    } else if (cfg.proceduralBg || cfg.wallpaper === "plain" || !wlist.length) {
      wl.style.backgroundImage = "radial-gradient(circle at 75% 20%, " + hexToRgba(t.accent, 0.22) + " 0%, " + t.bg + " 70%)";
    } else {
      activeWp = wlist[cfg.wallpaperIdx % wlist.length];
      wl.style.backgroundImage = "url('" + activeWp + "')";
    }
    if (wv) {
      var intro = cfg.wallpaperIntro && activeWp && DATA.wallpaperIntros && DATA.wallpaperIntros[activeWp];
      if (intro) {
        wv.src = intro;
        wv.style.display = "block";
        wv.onended = function () { wv.style.display = "none"; };
        wv.onerror = function () { wv.style.display = "none"; };
        try { wv.play().catch(function () { wv.style.display = "none"; }); } catch (e) { wv.style.display = "none"; }
      } else {
        try { wv.pause(); } catch (e) {}
        wv.style.display = "none";
      }
    }
    var dim = document.getElementById("wallpaper-dim");
    dim.style.background = "rgba(0,0,0," + (cfg.wallpaperDim || 0) + ")";
    pushLightsTheme(t, false);
  }

  // Every bundled background, in theme order (DATA.wallpapers: theme -> list of paths).
  function allWallpapers() {
    var out = [];
    Object.keys(DATA.wallpapers || {}).forEach(function (th) { out = out.concat(DATA.wallpapers[th]); });
    return out;
  }

  function fitStage() {
    var st = document.getElementById("stage");
    var sx = window.innerWidth / 1920;
    var sy = window.innerHeight / 1080;
    var s = Math.min(sx, sy);
    st.style.transform = "scale(" + s + ")";
  }
  window.addEventListener("resize", fitStage);

  function updateClock() {
    var d = new Date();
    var lang = getLang();
    var days = {
      en: ["sun","mon","tue","wed","thu","fri","sat"],
      it: ["dom","lun","mar","mer","gio","ven","sab"],
      de: ["so","mo","di","mi","do","fr","sa"]
    }[lang] || ["sun","mon","tue","wed","thu","fri","sat"];
    var months = {
      en: ["jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"],
      it: ["gen","feb","mar","apr","mag","giu","lug","ago","set","ott","nov","dic"],
      de: ["jan","feb","mär","apr","mai","jun","jul","aug","sep","okt","nov","dez"]
    }[lang] || ["jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"];
    document.getElementById("bar-clock").textContent = days[d.getDay()] + " " + d.getDate() + " " + months[d.getMonth()] + "  " + clockText(d);
  }

  // 12 or 24 hour time for the bar and the screensaver clock (cfg.clockFormat, "auto" follows the language).
  function use24h() {
    return cfg.clockFormat === "24" || (cfg.clockFormat !== "12" && getLang() !== "en");
  }
  function clockText(d) {
    var h = d.getHours();
    var m = ("0" + d.getMinutes()).slice(-2);
    return use24h() ? ("0" + h).slice(-2) + ":" + m : ((h + 11) % 12 + 1) + ":" + m + (h >= 12 ? " PM" : " AM");
  }

  var logoTimer = null;
  function animateLogo() {
    var el = document.getElementById("term-logo");
    var target = DATA.logo || "OMARCHY TV";
    if (logoTimer) clearInterval(logoTimer);
    if (!cfg.logoAnim) {
      el.textContent = target;
      return;
    }
    var glyphs = "░▒▓█▄▀■□▪▫01";
    var step = 0, total = 14;
    logoTimer = setInterval(function () {
      step++;
      if (step >= total) {
        clearInterval(logoTimer);
        logoTimer = null;
        el.textContent = target;
        return;
      }
      var ratio = step / total;
      var out = "";
      for (var i = 0; i < target.length; i++) {
        var ch = target[i];
        if (ch === "\n" || ch === " ") out += ch;
        else if (Math.random() < ratio) out += ch;
        else out += glyphs[Math.floor(Math.random() * glyphs.length)];
      }
      el.textContent = out;
    }, 65);
  }

  function renderTerminal() {
    var t = currentThemeObj();
    var pr = document.getElementById("term-prompt");
    pr.innerHTML =
      '<span style="color:' + t.green + '">' + cfg.promptUser + '@' + cfg.promptHost + '</span>' +
      '<span style="color:' + t.fg + '">:</span>' +
      '<span style="color:' + t.accent + '">~</span>' +
      '<span style="color:' + t.fg + '">$ </span>' +
      '<span id="term-cursor">█</span>';

    var upMin = Math.max(1, Math.floor((Date.now() - START_TIME) / 60000));
    var upStr = upMin >= 60 ? (Math.floor(upMin / 60) + " h " + (upMin % 60) + " min") : (upMin + " min");
    var info = document.getElementById("term-info");
    info.innerHTML =
      '<span style="color:' + t.accent + '">' + tr("term_theme") + '</span><span style="color:' + t.fg + '">' + cfg.theme + '</span>\n' +
      '<span style="color:' + t.accent + '">' + tr("term_net") + '</span><span style="color:' + t.fg + '">Wi-Fi</span>\n' +
      '<span style="color:' + t.accent + '">' + tr("term_up") + '</span><span style="color:' + t.fg + '">' + upStr + '</span>' +
      '<span style="color:' + t.fgDim + '"> · 2.13.0-beta</span>';
  }

  function fmtTemp(c) {
    if (cfg.tempUnit === "F") {
      return Math.round(c * 9 / 5 + 32) + "°F";
    }
    return Math.round(c) + "°";
  }

  function formatAiPane(s) {
    if (!Array.isArray(s.ai)) {
      return "Claude   $" + (s.claude || "18.40") + " left\nOpenAI   $" + (s.openai || "42.10") + " left\nxAI Grok $" + (s.xai || "95.00") + " left";
    }
    var mode = cfg.aiCredits === "dollars" ? "dollars" : "percent";
    var rows = s.ai;
    var seen = {};
    var out = [];
    if (mode === "percent") {
      rows.forEach(function (e) {
        var key = e.id || e.name;
        if (!key || seen[key]) return;
        var err = String(e.error || "").toLowerCase();
        if (e.status === "error" || err.indexOf("waiting for auth") >= 0 || err.indexOf("unavailable") >= 0) return;
        var line = e.plan || (e.used != null ? Math.round(e.used) + "% used" : "");
        if (!line) return;
        seen[key] = 1;
        var label = e.plan ? String(e.name || key).split(" ")[0] : (e.name || key);
        out.push(label + "  " + line);
      });
      return out.length ? out.join("\n") : "No subscription meter";
    }
    rows = rows.filter(function (e) { return e.unit && e.unit !== "%" && e.unit !== "tokens"; });
    if (!rows.length) return "No dollar balances";
    return rows.map(function (e) {
      var rem = e.remaining != null ? e.remaining : (e.limit != null && e.used != null ? Math.round((e.limit - e.used) * 100) / 100 : null);
      return (e.name || "balance") + "  $" + (rem != null ? rem : "?") + " left";
    }).join("\n");
  }

  function renderWidgets() {
    document.getElementById("legend-wx").textContent = tr("box_weather");
    document.getElementById("legend-pc").textContent = tr("box_pc");
    document.getElementById("legend-ai").textContent = tr("box_ai");

    document.getElementById("term-box").style.display = cfg.widgets.term ? "block" : "none";
    document.getElementById("wx-box").style.display = cfg.widgets.weather ? "block" : "none";
    document.getElementById("pc-box").style.display = cfg.widgets.pc ? "block" : "none";
    document.getElementById("ai-box").style.display = cfg.widgets.ai ? "block" : "none";
    document.getElementById("deploy-box").style.display = cfg.widgets.deploy ? "block" : "none";

    var wx = state.weather;
    var wmoMap = tr("wmo") || {};
    var cond = wmoMap[wx.code] || "clear";
    document.getElementById("wx-temp").textContent = fmtTemp(wx.tempC);
    document.getElementById("wx-line1").textContent = (wx.city || cfg.weatherCity) + " · " + cond;
    document.getElementById("wx-line2").textContent = tr("wx_min") + fmtTemp(wx.minC) + " · " + tr("wx_max") + fmtTemp(wx.maxC);

    var pcBody = document.getElementById("pc-body");
    var aiBody = document.getElementById("ai-body");
    var ws4Live = document.getElementById("ws4-live-details");

    if (state.pcStatus) {
      var s = state.pcStatus;
      pcBody.style.color = "var(--t-fg)";
      pcBody.textContent = (s.host || "omarchy-pc") + " · " + (s.kernel || "Arch Linux") + "\nCPU " + s.cpu + "%  [████░░░░░░]\nRAM " + s.ram + "%  [██████░░░░]";
      aiBody.style.color = "var(--t-fg)";
      aiBody.textContent = formatAiPane(s);
      if (ws4Live) {
        ws4Live.textContent = "Connected to " + (s.host || "omarchy-pc") + " (" + (cfg.pcHost || "simulated LAN link") + ")\nCPU: " + s.cpu + "% · RAM: " + s.ram + "% · GPU: " + (s.gpu || "18%") + "\nDeploys: " + (s.deploy || "omarchytv-webos 2.13.0-beta ✓");
      }
    } else {
      pcBody.style.color = "var(--t-fg-dim)";
      pcBody.textContent = tr("pc_not_connected");
      aiBody.style.color = "var(--t-fg-dim)";
      aiBody.textContent = tr("pc_not_connected");
      if (ws4Live) ws4Live.textContent = "";
    }
  }

  function pollPcStatus() {
    if (cfg.pcSimulated) {
      state.pcStatus = { host: "omarchy-workstation", kernel: "Linux 6.14-arch", cpu: 14, ram: 38, gpu: "12%", claude: "24.80", openai: "51.20", xai: "110.00" };
      renderWidgets();
      return Promise.resolve(state.pcStatus);
    }
    if (!cfg.pcHost) {
      state.pcStatus = null;
      renderWidgets();
      return Promise.resolve(null);
    }
    var base = cfg.pcHost.indexOf("http") === 0 ? cfg.pcHost : ("http://" + cfg.pcHost + (cfg.pcHost.indexOf(":") > 0 ? "" : ":8765"));
    var url = base.replace(/\/+$/, "") + "/v1/status";
    return fetch(url).then(function (r) { return r.json(); }).then(function (j) {
      state.pcStatus = j;
      if (j && j.theme && DATA.themes[j.theme] && cfg.syncPcTheme) {
        cfg.theme = j.theme;
        applyTheme();
      }
      renderWidgets();
      return j;
    }).catch(function () {
      state.pcStatus = null;
      renderWidgets();
      return null;
    });
  }

  function fetchWeather() {
    var url = "https://api.open-meteo.com/v1/forecast?latitude=" + cfg.weatherLat + "&longitude=" + cfg.weatherLon + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1";
    fetch(url).then(function (r) { return r.json(); }).then(function (j) {
      if (j && j.current) {
        state.weather.tempC = j.current.temperature_2m;
        state.weather.code = j.current.weather_code || 0;
        if (j.daily && j.daily.temperature_2m_min) {
          state.weather.minC = j.daily.temperature_2m_min[0];
          state.weather.maxC = j.daily.temperature_2m_max[0];
        }
        state.weather.city = cfg.weatherCity;
        renderWidgets();
      }
    }).catch(function () {});
  }

  // DEFAULT_APPS sorted by cfg.appOrder; ids missing from the order keep their default position after it.
  function orderedApps() {
    var order = cfg.appOrder || [];
    return DEFAULT_APPS.slice().sort(function (a, b) {
      var ia = order.indexOf(a.id), ib = order.indexOf(b.id);
      if (ia < 0) ia = order.length + DEFAULT_APPS.indexOf(a);
      if (ib < 0) ib = order.length + DEFAULT_APPS.indexOf(b);
      return ia - ib;
    });
  }

  function visibleApps() {
    return orderedApps().filter(function (a) {
      if (a.id === "tv" && !cfg.tvEnabled) return false;
      return cfg.hiddenApps.indexOf(a.id) < 0;
    });
  }

  function buildTileHTML(app) {
    if (app.brand === "tv") {
      return '<div class="tile-banner" style="background:var(--t-bg-alt);">' +
        '<svg width="54" height="54" viewBox="0 0 24 24" fill="none" stroke="var(--t-accent)" stroke-width="2">' +
        '<rect x="2" y="3" width="20" height="14"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>' +
        '<div style="font-size:28px;font-weight:700;color:var(--t-fg-bright);margin-top:6px;">' + app.name + '</div></div>';
    }
    if (cfg.textTiles) {
      return '<div class="tile-banner" style="background:var(--t-bg-alt);"><div class="tile-text-only">' + app.name + '</div></div>';
    }
    return '<div class="tile-banner" style="background:' + (app.bg || "var(--t-bg-alt)") + ';">' +
      '<div style="font-size:30px;font-weight:700;letter-spacing:1.2px;color:' + (app.fg || "var(--t-fg-bright)") + ';">' + (app.sub || app.name) + '</div>' +
      '</div>';
  }

  function renderDock() {
    var apps = visibleApps();
    if (state.dockIdx >= apps.length) state.dockIdx = Math.max(0, apps.length - 1);
    if (state.dockIdx < state.dockScroll) state.dockScroll = state.dockIdx;
    if (state.dockIdx >= state.dockScroll + 6) state.dockScroll = state.dockIdx - 5;

    var strip = document.getElementById("dock-strip");
    strip.innerHTML = "";
    var slice = apps.slice(state.dockScroll, state.dockScroll + 6);
    slice.forEach(function (app, i) {
      var realIdx = state.dockScroll + i;
      var d = document.createElement("div");
      d.className = "app-tile" + (state.ws === 0 && state.zone === "dock" && state.dockIdx === realIdx ? " focused" : "") + (app.id === state.movingId ? " moving" : "");
      d.innerHTML = buildTileHTML(app);
      d.onclick = function () { state.zone = "dock"; state.dockIdx = realIdx; renderAll(); launchApp(app); };
      // renderAll() rebuilds the strip, so only re-render when focus actually moves; otherwise the
      // tile under the pointer is replaced on every hover and a click never lands.
      d.onmouseenter = function () {
        if (state.modalOpen || state.ws !== 0 || (state.zone === "dock" && state.dockIdx === realIdx)) return;
        state.zone = "dock"; state.dockIdx = realIdx; renderAll();
      };
      strip.appendChild(d);
    });

    var cur = apps[state.dockIdx] || apps[0] || { name: "OmarchyTV", id: "tv" };
    document.getElementById("dock-app-name").textContent = cur.name;
    document.getElementById("dock-hint").textContent = cur.id === "tv" ? tr("hint_tv") : tr("hint_app");
    var banner = document.getElementById("arrange-banner");
    banner.textContent = tr("move_hint");
    banner.style.display = state.movingId ? "block" : "none";
  }

  function renderWorkspaces() {
    document.getElementById("ws1-layer").style.display = state.ws === 0 ? "block" : "none";
    document.getElementById("ws2-panel").className = "box has-legend ws-panel" + (state.ws === 1 ? " active" : "");
    document.getElementById("ws3-panel").className = "box has-legend ws-panel" + (state.ws === 2 ? " active" : "");
    document.getElementById("ws4-panel").className = "box has-legend ws-panel" + (state.ws === 3 ? " active" : "");
    document.getElementById("ws5-panel").className = "box has-legend ws-panel" + (state.ws === 4 ? " active" : "");

    document.getElementById("legend-ws2").textContent = tr("box_tv");
    document.getElementById("legend-ws3").textContent = tr("box_app");
    document.getElementById("legend-ws4").textContent = tr("box_ws_pc");
    document.getElementById("legend-ws5").textContent = tr("box_system");

    // Waybar buttons
    var btns = document.querySelectorAll("[data-bar-idx]");
    for (var i = 0; i < btns.length; i++) {
      var b = btns[i];
      var idx = parseInt(b.getAttribute("data-bar-idx"), 10);
      var wsAttr = b.getAttribute("data-ws");
      var cls = b.classList.contains("bar-icon-btn") ? "bar-icon-btn" : ("bar-btn" + (idx === 0 ? " logo-btn" : " ws-btn"));
      if (wsAttr !== null && parseInt(wsAttr, 10) === state.ws) cls += " ws-active";
      if (state.zone === "bar" && state.barIdx === idx) cls += " focused";
      b.className = cls;
    }

    var tip = document.getElementById("bar-tip");
    if (state.zone === "bar" && state.barIdx >= 6) {
      var tipKeys = ["tip_cinemarchy", "tip_wifi", "tip_bt", "tip_vol", "tip_inputs", "tip_saver"];
      tip.textContent = tr(tipKeys[state.barIdx - 6] || "tip_saver");
      tip.style.display = "block";
    } else {
      tip.style.display = "none";
    }

    // Widget focus borders on WS1
    var wBoxes = [document.getElementById("term-box"), document.getElementById("wx-box"), document.getElementById("pc-box"), document.getElementById("ai-box")];
    wBoxes.forEach(function (el, idx) {
      if (!el) return;
      el.classList.toggle("focused", state.ws === 0 && state.zone === "widgets" && state.widgetIdx === idx);
    });

    // WS2: TV & HDMI Inputs
    if (state.ws === 1) {
      var g2 = document.getElementById("ws2-grid");
      g2.innerHTML = "";
      TV_INPUTS.forEach(function (inp, i) {
        var d = document.createElement("div");
        d.className = "app-tile" + (state.zone === "ws2" && state.ws2Idx === i ? " focused" : "");
        d.innerHTML = buildTileHTML({ name: inp.name, brand: "tv" });
        d.onclick = function () { state.zone = "ws2"; state.ws2Idx = i; renderAll(); launchApp(inp); };
        g2.appendChild(d);
      });
      var curInp = TV_INPUTS[state.ws2Idx] || TV_INPUTS[0];
      document.getElementById("ws2-title").textContent = curInp.sub;
      document.getElementById("ws2-hint").textContent = tr("hint_ws2");
    }

    // WS3: All Apps Grid
    if (state.ws === 2) {
      var g3 = document.getElementById("ws3-grid");
      g3.innerHTML = "";
      var all = visibleApps();
      all.forEach(function (app, i) {
        var d = document.createElement("div");
        d.className = "app-tile" + (state.zone === "ws3" && state.ws3Idx === i ? " focused" : "") + (app.id === state.movingId ? " moving" : "");
        d.innerHTML = buildTileHTML(app);
        d.onclick = function () { state.zone = "ws3"; state.ws3Idx = i; renderAll(); launchApp(app); };
        g3.appendChild(d);
      });
      var curApp = all[state.ws3Idx] || all[0];
      document.getElementById("ws3-title").textContent = curApp ? curApp.name : "Apps";
      document.getElementById("ws3-hint").textContent = tr("hint_ws3");
    }

    // WS4: Omarchy PC
    if (state.ws === 3) {
      document.getElementById("ws4-heading").textContent = tr("ws4_heading");
      document.getElementById("ws4-desc").textContent = tr("ws4_desc");
      var b0 = document.getElementById("ws4-pair-btn");
      var b1 = document.getElementById("ws4-demo-btn");
      b0.className = "ws4-btn" + (state.zone === "ws4" && state.ws4Idx === 0 ? " focused" : "");
      b1.className = "ws4-btn" + (state.zone === "ws4" && state.ws4Idx === 1 ? " focused" : "");
      b1.textContent = cfg.pcSimulated ? "[ disconnect simulated PC ]" : "[ simulate connected PC ]";
    }

    // WS5: System Settings
    if (state.ws === 4) {
      var rows = getSysRows();
      var list5 = document.getElementById("ws5-list");
      list5.innerHTML = "";
      rows.forEach(function (r, i) {
        var row = document.createElement("div");
        row.className = "sys-row" + (state.zone === "ws5" && state.ws5Idx === i ? " focused" : "");
        row.innerHTML = '<span class="sys-lbl">' + r.label + '</span><span class="sys-val">' + r.val + '</span>';
        row.onclick = function () { state.zone = "ws5"; state.ws5Idx = i; renderAll(); r.act(); };
        list5.appendChild(row);
      });
    }
  }

  function getSysRows() {
    var transPct = Math.round((1 - cfg.opacity) * 100) + "%";
    var idleStr = cfg.saverIdleMin === 0 ? "never" : (cfg.saverIdleMin + " min");
    return [
      { label: "🎬 Cinemarchy (Media, Streaming, Live TV & Snippets)", val: "C ›", act: function () { openCinemarchyModal(0); } },
      { label: "🧩 " + tr("sys_plugins"), val: ((cfg.pluginSpacexTv ? 1 : 0) + (cfg.pluginGrokbot ? 1 : 0)) + " enabled ›", act: openPluginsModal },
      { label: tr("sys_webos"), val: "›", act: function () { launchApp({ name: "LG Settings", appId: "com.palm.app.settings" }); } },
      { label: tr("sys_themes"), val: cfg.theme, act: openThemesModal },
      { label: tr("sys_trans"), val: transPct, act: function () {
        var steps = [0.95, 0.85, 0.75, 0.65, 0.50, 0.40];
        var idx = (steps.indexOf(cfg.opacity) + 1) % steps.length;
        cfg.opacity = steps[idx];
        saveCfg(); applyTheme(); renderAll();
      } },
      { label: tr("sys_widgets"), val: "›", act: openWidgetsModal },
      { label: tr("sys_arrange"), val: "›", act: function () { showToast("Arrange panels: use D-pad on Home"); switchWorkspace(0); } },
      { label: tr("sys_saver"), val: "›", act: openSaverModal },
      { label: "Ambilight & Lights", val: (cfg.lightsHub || "off") + " ›", act: openLightsModal },
      { label: tr("sys_saver_idle"), val: idleStr, act: function () {
        var opts = [1, 2.5, 5, 10, 0];
        cfg.saverIdleMin = opts[(opts.indexOf(cfg.saverIdleMin) + 1) % opts.length];
        saveCfg(); renderAll();
      } },
      { label: tr("sys_logo_anim"), val: cfg.logoAnim ? "on" : "off", act: function () { cfg.logoAnim = !cfg.logoAnim; saveCfg(); renderAll(); animateLogo(); } },
      { label: tr("sys_text_tiles"), val: cfg.textTiles ? "on" : "off", act: function () { cfg.textTiles = !cfg.textTiles; saveCfg(); renderAll(); } },
      { label: tr("sys_all"), val: "›", act: openAllSettingsModal },
      { label: tr("sys_about"), val: "2.13.0-beta ›", act: openAboutModal }
    ];
  }

  function openModal(title, sub, items, withInput, onInput, opts) {
    opts = opts || {};
    state.modalOpen = true;
    state.modalItems = items || [];
    state.modalIdx = opts.focusIdx > 0 && opts.focusIdx < state.modalItems.length ? opts.focusIdx : 0;
    state.modalDigit = null;
    document.getElementById("modal-title").textContent = title;
    var subEl = document.getElementById("modal-sub");
    subEl.textContent = sub || "";
    subEl.style.display = sub ? "block" : "none";

    var iw = document.getElementById("modal-input-wrap");
    var inp = document.getElementById("modal-input");
    if (withInput) {
      iw.style.display = "block";
      inp.value = "";
      inp.placeholder = withInput;
      inp.oninput = function () { if (onInput) onInput(inp.value); };
      // opts.noAutoFocus: the field is not focused on open, so the TV keyboard only appears once the
      // user moves onto the field (▲ from the first item) and selects it.
      if (!opts.noAutoFocus) setTimeout(function () { inp.focus(); }, 30);
    } else {
      iw.style.display = "none";
      inp.oninput = null;
    }
    document.getElementById("modal-backdrop").className = "open";
    renderModalList();
  }

  // Text field in a menu (Walker, city, PC address). Backspace edits the text instead of closing the
  // menu, and ▼ OK leave the field first: while it keeps focus, webOS sends OK to the on-screen
  // keyboard instead of the list. ▲ on the first list item goes back into the field.
  var modalInput = document.getElementById("modal-input");
  modalInput.addEventListener("keydown", function (e) {
    var k = e.key || { 8: "Backspace", 13: "Enter", 38: "ArrowUp", 40: "ArrowDown" }[e.keyCode];
    if (k === "Backspace" || k === "ArrowUp") e.stopPropagation();   // edit text / stay in the field
    else if (k === "ArrowDown") { modalInput.blur(); e.stopPropagation(); }   // land on the first item
    else if (k === "Enter") modalInput.blur();                                  // pick the highlighted item
  });
  document.addEventListener("keydown", function (e) {
    var up = e.key === "ArrowUp" || e.keyCode === 38;
    if (!up || !state.modalOpen || state.modalIdx !== 0 || document.activeElement === modalInput) return;
    if (document.getElementById("modal-input-wrap").style.display !== "block") return;
    modalInput.focus();
    e.preventDefault();
    e.stopPropagation();
  }, true);

  function closeModal() {
    if (state.cineTab === 0 && dhhState.videos && window.OmarchyDhh) window.OmarchyDhh.markSeen(window.localStorage, dhhState.videos);
    state.cineTab = -1;
    state.modalOpen = false;
    document.getElementById("modal-backdrop").className = "";
    renderAll();
  }

  function renderModalList() {
    var list = document.getElementById("modal-list");
    list.innerHTML = "";
    state.modalItems.forEach(function (it, idx) {
      var d = document.createElement("div");
      d.className = "modal-item" + (idx === state.modalIdx ? " focused" : "");
      d.innerHTML = '<span>' + it.label + '</span><span class="mi-right">' + (it.right || "") + '</span>';
      d.onclick = function () { state.modalIdx = idx; if (it.act) it.act(); };
      list.appendChild(d);
      if (idx === state.modalIdx && d.scrollIntoView) d.scrollIntoView({ block: "nearest" });
    });
  }

  var ANIMATED_INTROS = [
    { theme: "last-horizon", idx: 0, wp: "wallpapers/last-horizon/01-eyes-wide.webp" },
    { theme: "cumulus", idx: 0, wp: "wallpapers/cumulus/01-cloud-town-path.webp" },
    { theme: "cumulus", idx: 1, wp: "wallpapers/cumulus/02-cottage.webp" },
    { theme: "cumulus", idx: 2, wp: "wallpapers/cumulus/03-airship.webp" },
    { theme: "cumulus", idx: 3, wp: "wallpapers/cumulus/04-lighthouse.webp" }
  ];

  function rotateRandomIfEnabled() {
    if (!cfg.randomTheme && !cfg.randomWallpaper) return false;
    var pick = ANIMATED_INTROS[Math.floor(Math.random() * ANIMATED_INTROS.length)];
    if (cfg.randomTheme && cfg.randomWallpaper) {
      cfg.theme = pick.theme;
      cfg.wallpaper = "";
      cfg.wallpaperIdx = pick.idx;
    } else if (cfg.randomTheme) {
      cfg.theme = cfg.theme === "last-horizon" ? "cumulus" : "last-horizon";
    } else if (cfg.randomWallpaper) {
      cfg.wallpaper = pick.wp;
      cfg.wallpaperIdx = pick.idx;
    }
    saveCfg();
    applyTheme();
    renderAll();
    return true;
  }

  document.addEventListener("visibilitychange", function () {
    if (!document.hidden && !state.modalOpen) rotateRandomIfEnabled();
  });

  function openThemesModal(keepFocus) {
    var focus = state.modalIdx;
    var names = Object.keys(DATA.themes);
    var items = [
      { label: tr("random_theme"), right: cfg.randomTheme ? "on" : "off", act: function () {
        cfg.randomTheme = !cfg.randomTheme; saveCfg(); openThemesModal(true);
      } },
      { label: tr("random_wallpaper"), right: cfg.randomWallpaper ? "on" : "off", act: function () {
        cfg.randomWallpaper = !cfg.randomWallpaper; saveCfg(); openThemesModal(true);
      } }
    ].concat(names.map(function (n) {
      return {
        label: n,
        right: !cfg.randomTheme && n === cfg.theme ? "✓" : (n === cfg.theme ? "●" : ""),
        act: function () {
          cfg.theme = n;
          cfg.randomTheme = false;
          cfg.wallpaperIdx = 0;
          saveCfg(); applyTheme(); renderAll(); closeModal();
        }
      };
    }));
    var curIdx = names.indexOf(cfg.theme);
    openModal(tr("sys_themes") + " (" + names.length + ")", "Select a theme (MIT omarchy.webp + Liftoff Sunset)", items);
    if (keepFocus) { state.modalIdx = Math.min(focus, items.length - 1); renderModalList(); }
    else if (curIdx >= 0) { state.modalIdx = curIdx + 2; renderModalList(); }
  }

  function openWeatherModal() {
    var items = [
      { label: tr("wx_auto"), right: "", act: function () {
        cfg.weatherCity = "Alessandria"; cfg.weatherLat = 44.913; cfg.weatherLon = 8.615;
        saveCfg(); fetchWeather(); renderAll(); closeModal();
      } },
      { label: tr("wx_search"), right: cfg.weatherCity, act: function () {
        openModal(tr("wx_search"), "Type city name (e.g. Milan, Rome, Berlin, New York):", [
          { label: "Milano, IT", right: "45.46, 9.19", act: function () { setCity("Milano", 45.464, 9.190); } },
          { label: "Alessandria, IT", right: "44.91, 8.61", act: function () { setCity("Alessandria", 44.913, 8.615); } },
          { label: "Roma, IT", right: "41.89, 12.48", act: function () { setCity("Roma", 41.893, 12.483); } },
          { label: "Berlin, DE", right: "52.52, 13.40", act: function () { setCity("Berlin", 52.520, 13.405); } },
          { label: "San Francisco, US", right: "37.77, -122.42", act: function () { setCity("San Francisco", 37.775, -122.419); } }
        ], "City name…");
      } },
      { label: tr("wx_unit"), right: "°" + cfg.tempUnit, act: function () {
        cfg.tempUnit = cfg.tempUnit === "C" ? "F" : "C";
        saveCfg(); renderAll(); openWeatherModal();
      } }
    ];
    openModal(tr("wx_title"), "", items);
  }

  function setCity(name, lat, lon) {
    cfg.weatherCity = name; cfg.weatherLat = lat; cfg.weatherLon = lon;
    state.weather.city = name;
    saveCfg(); fetchWeather(); renderAll(); closeModal();
  }

  function openWidgetsModal() {
    function tog(k) { cfg.widgets[k] = !cfg.widgets[k]; saveCfg(); renderAll(); openWidgetsModal(); }
    openModal(tr("sys_widgets"), "", [
      { label: "terminal · logo", right: cfg.widgets.term ? "on" : "off", act: function () { tog("term"); } },
      { label: "weather", right: cfg.widgets.weather ? "on" : "off", act: function () { tog("weather"); } },
      { label: "omarchy · pc", right: cfg.widgets.pc ? "on" : "off", act: function () { tog("pc"); } },
      { label: "ai credits · pc", right: cfg.widgets.ai ? "on" : "off", act: function () { tog("ai"); } },
      { label: "deploys · pc", right: cfg.widgets.deploy ? "on" : "off", act: function () { tog("deploy"); } }
    ]);
  }

  function openSaverModal(keepFocus) {
    var focus = state.modalIdx;
    openModal(tr("sys_saver"), "37 tte.js TerminalTextEffects or multi-layer Omarchy Cyber-TUI Aquarium", [
      { label: "Preview screensaver now", right: "▶", act: function () { closeModal(); startScreensaver(); } },
      { label: "Screensaver mode / art", right: cfg.saverMode === "aquarium" ? (cfg.saverArt === "aquarium-classic" ? "Aquarium Classic" : "Omarchy Aquarium (homage)") : (cfg.saverArt === "cinemarchy" ? "CINEMARCHY" : "OMARCHY TV"), act: function () {
        // cycle: Aquarium (homage, default) -> Aquarium Classic -> OMARCHY TV -> CINEMARCHY -> Aquarium
        if (cfg.saverMode === "aquarium" && cfg.saverArt !== "aquarium-classic") { cfg.saverArt = "aquarium-classic"; cfg.saverMode = "aquarium"; }
        else if (cfg.saverMode === "aquarium") { cfg.saverArt = "omarchy-tv"; cfg.saverMode = "logo"; }
        else if (cfg.saverArt !== "cinemarchy") { cfg.saverArt = "cinemarchy"; cfg.saverMode = "logo"; }
        else { cfg.saverArt = "aquarium"; cfg.saverMode = "aquarium"; }
        saveCfg(); openSaverModal(true);
      } },
      { label: tr("sys_saver_idle"), right: cfg.saverIdleMin === 0 ? "never" : (cfg.saverIdleMin + " min"), act: function () {
        var opts = [1, 2.5, 5, 10, 0];
        cfg.saverIdleMin = opts[(opts.indexOf(cfg.saverIdleMin) + 1) % opts.length];
        saveCfg(); openSaverModal(true);
      } },
      { label: "Clock under logo", right: cfg.saverShowClock ? "on" : "off", act: function () {
        cfg.saverShowClock = !cfg.saverShowClock; saveCfg(); openSaverModal(true);
      } },
      { label: "Aquarium ST3 Synth Audio", right: cfg.saverSound !== false ? "on" : "off", act: function () {
        cfg.saverSound = cfg.saverSound === false; saveCfg(); openSaverModal(true);
      } },
      { label: "Turn off after idle", right: cfg.offIdleMin ? cfg.offIdleMin + " min" : "never", act: function () {
        var opts = [0, 5, 15, 30, 60, 120];
        cfg.offIdleMin = opts[(opts.indexOf(cfg.offIdleMin) + 1) % opts.length];
        saveCfg(); openSaverModal(true);
      } },
      { label: "Turn off", right: cfg.offMode === "tv" ? "TV" : "screen only", act: function () {
        cfg.offMode = cfg.offMode === "tv" ? "screen" : "tv";
        saveCfg(); openSaverModal(true);
      } },
      { label: "TV control", right: tvControlApproved() ? "approved" : "needs approval", act: function () {
        showToast("Checking TV control… accept the prompt on screen if it appears");
        ssapRequest("ssap://com.webos.service.tvpower/power/getPowerState", function (ok, info) {
          showToast(ok ? "TV control approved" : "TV control not available (" + info + ")");
          if (state.modalOpen) openSaverModal(true);
        });
      } }
    ]);
    // Toggling an item re-opens the menu; keep the cursor on it instead of jumping to the top.
    if (keepFocus) { state.modalIdx = Math.min(focus, state.modalItems.length - 1); renderModalList(); }
  }

  // ---- Rooted TVs: open at start-up and take over the Home button -------------------------------
  // Homebrew Channel's exec service runs shell commands as root. Toggling a setting creates or removes
  // a flag file in ROOT_FLAGS and installs ROOT_HOOK, which Homebrew Channel runs at boot to start
  // rooted/omarchytv-home.sh from this app's folder (see that script). Unrooted TVs have no such
  // service: the settings say so, and homewatch/ covers them from another machine.
  var ROOT_FLAGS = "/var/lib/omarchytv";
  var ROOT_HOOK = "/var/lib/webosbrew/init.d/omarchytv";   // run-parts skips names with a dot
  var ROOT_PID = "/tmp/omarchytv-home.pid";                // written by rooted/omarchytv-home.sh
  var rootCalls = [];                                       // keep bridges alive until they answer

  function rootExec(cmd, cb) {
    if (!window.PalmServiceBridge) { cb(false, "not running on webOS"); return; }
    var b = new PalmServiceBridge();
    rootCalls.push(b);
    b.onservicecallback = function (msg) {
      rootCalls.splice(rootCalls.indexOf(b), 1);
      var r = {};
      try { r = JSON.parse(msg); } catch (e) {}
      cb(!!r.returnValue, r.returnValue ? (r.stdoutString || "") : (r.errorText || r.stderrString || "error"));
    };
    b.call("luna://org.webosbrew.hbchannel.service/exec", JSON.stringify({ command: cmd }));
  }

  function rootRefresh(done) {
    rootExec("ls " + ROOT_FLAGS + " 2>/dev/null; true", function (ok, out) {
      state.root = ok ? { boot: /(^|\s)boot(\s|$)/.test(out), home: /(^|\s)home(\s|$)/.test(out) } : false;
      if (done) done();
    });
  }

  function rootSet(flag, on, done) {
    var dir = decodeURIComponent(location.pathname.replace(/\/[^\/]*$/, ""));
    var script = dir + "/rooted/omarchytv-home.sh";
    var cmd = on
      ? "mkdir -p " + ROOT_FLAGS + " && touch " + ROOT_FLAGS + "/" + flag +
        " && printf '#!/bin/sh\\n# OmarchyTV start-up and Home-button helper (rooted TVs)\\nS=\"%s\"\\n[ -f \"$S\" ] && setsid sh \"$S\" </dev/null >/dev/null 2>&1 &\\n' '" + script + "' > " + ROOT_HOOK +
        " && chmod 755 " + ROOT_HOOK +
        " && (setsid sh '" + script + "' --no-boot </dev/null >/dev/null 2>&1 &)"
      : "rm -f " + ROOT_FLAGS + "/" + flag +
        // no Home redirect wanted: stop the helper's process group (it waits on a luna subscription)
        "; [ -f " + ROOT_FLAGS + "/home ] || { p=$(cat " + ROOT_PID + " 2>/dev/null) && kill -- -$p; rm -f " + ROOT_PID + "; }" +
        "; [ -f " + ROOT_FLAGS + "/boot ] || [ -f " + ROOT_FLAGS + "/home ] || rm -f " + ROOT_HOOK + "; true";
    rootExec(cmd, function (ok, out) {
      if (!ok) showToast(tr("root_failed") + out);
      rootRefresh(done);
    });
  }

  function rootRow(flag, label) {
    var st = state.root;
    return { label: label, right: st === null ? "…" : st === false ? tr("root_needed") : (st[flag] ? "on" : "off"), act: function () {
      if (!state.root) { showToast(tr("root_missing")); return; }
      rootSet(flag, !state.root[flag], function () { if (state.modalOpen) openAllSettingsModal(true); });
    } };
  }

  // ---- Rooted TVs: keep OmarchyTV loaded when another app opens ---------------------------------
  // When memory runs short (most of the time on 1-1.5 GB TVs), webOS closes web apps in the background,
  // so every return to OmarchyTV is a cold start. LG's memory manager (memchute) skips apps on its
  // "KeepAlive" list, and it reads /home/root/memorymanager-conf.json, a writable override of
  // /etc/palm/memorymanager-conf.json, when that file exists. The toggle adds or removes this app in
  // the override (keeping any other edits in it) and restarts memchute. TVs whose memchute doesn't
  // know the override path show "not on this TV".
  var MEM_CONF = "/etc/palm/memorymanager-conf.json";
  var MEM_OVERRIDE = "/home/root/memorymanager-conf.json";
  var APP_ID = "com.omarchytv.home";

  function keepRead(cb) {
    rootExec("if grep -q " + MEM_OVERRIDE + " /usr/sbin/memchute 2>/dev/null && [ -f " + MEM_CONF + " ]; then cat " + MEM_CONF +
      "; echo; echo @@OVERRIDE@@; cat " + MEM_OVERRIDE + " 2>/dev/null; else echo UNSUPPORTED; fi", function (ok, out) {
      if (!ok) { cb(null); return; }
      var parts = out.split("@@OVERRIDE@@");
      var base, over = null;
      try { base = JSON.parse(parts[0]); } catch (e) { cb({ supported: false }); return; }
      try { if (parts[1] && parts[1].trim()) over = JSON.parse(parts[1]); } catch (e) { over = null; }
      cb({ supported: true, base: base, conf: over || JSON.parse(parts[0]) });
    });
  }

  function keepRefresh(done) {
    keepRead(function (r) {
      // "on" / "off", "na" (memchute without the override), "noroot" (no Homebrew Channel exec)
      state.keepLoaded = !r ? "noroot" : !r.supported ? "na" : (r.conf.KeepAlive || []).indexOf(APP_ID) >= 0 ? "on" : "off";
      if (done) done();
    });
  }

  function keepSet(on, done) {
    keepRead(function (r) {
      if (!r || !r.supported) { keepRefresh(done); return; }
      var list = (r.conf.KeepAlive || []).filter(function (id) { return id !== APP_ID; });
      if (on) list.push(APP_ID);
      r.conf.KeepAlive = list;
      var cmd;
      if (JSON.stringify(r.conf) === JSON.stringify(r.base)) {
        cmd = "rm -f " + MEM_OVERRIDE;   // nothing else customised: back to LG's file
      } else {
        var b64 = btoa(unescape(encodeURIComponent(JSON.stringify(r.conf, null, 1))));
        cmd = "echo " + b64 + " | base64 -d > " + MEM_OVERRIDE + ".tmp && mv -f " + MEM_OVERRIDE + ".tmp " + MEM_OVERRIDE;
      }
      rootExec(cmd + " && systemctl restart memchute", function (ok, out) {
        if (!ok) showToast(tr("root_failed") + out);
        keepRefresh(done);
      });
    });
  }

  function keepRow() {
    var st = state.keepLoaded;
    var right = !st ? "…" : st === "noroot" ? tr("root_needed") : st === "na" ? tr("root_keep_na") : st;
    return { label: tr("root_keep"), right: right, act: function () {
      if (state.keepLoaded === "noroot") { showToast(tr("root_missing")); return; }
      if (state.keepLoaded !== "on" && state.keepLoaded !== "off") return;
      keepSet(state.keepLoaded === "off", function () { if (state.modalOpen) openAllSettingsModal(true); });
    } };
  }

  function openAllSettingsModal(keepFocus) {
    var focus = state.modalIdx;
    var wlist = DATA.wallpapers[cfg.theme] || [];
    if (state.root === null) rootRefresh(function () { if (state.modalOpen) openAllSettingsModal(true); });
    if (state.keepLoaded === undefined) {
      state.keepLoaded = null;   // checking
      keepRefresh(function () { if (state.modalOpen) openAllSettingsModal(true); });
    }
    openModal(tr("sys_all"), "OmarchyTV 2.13.0-beta (LG webOS)", [
      { label: "Language", right: cfg.lang || "auto", act: function () {
        var l = ["", "en", "it", "de"];
        cfg.lang = l[(l.indexOf(cfg.lang) + 1) % l.length];
        saveCfg(); updateClock(); renderAll(); openAllSettingsModal();
      } },
      { label: tr("sys_clock"), right: cfg.clockFormat === "12" || cfg.clockFormat === "24" ? cfg.clockFormat + " h" : "auto (" + (use24h() ? "24" : "12") + " h)", act: function () {
        var f = ["auto", "12", "24"], at = state.modalIdx;
        cfg.clockFormat = f[(f.indexOf(cfg.clockFormat) + 1) % f.length];
        saveCfg(); updateClock(); openAllSettingsModal();
        state.modalIdx = at; renderModalList();   // stay on this row so OK keeps cycling
      } },
      { label: "Next background", right: wlist.length ? ((cfg.wallpaperIdx % wlist.length) + 1) + "/" + wlist.length : "procedural", act: function () {
        cfg.wallpaperIdx++; cfg.wallpaper = ""; saveCfg(); applyTheme(); openAllSettingsModal();
      } },
      { label: "Wallpaper dimming", right: Math.round((cfg.wallpaperDim || 0) * 100) + "%", act: function () {
        var d = [0, 0.25, 0.5];
        cfg.wallpaperDim = d[(d.indexOf(cfg.wallpaperDim) + 1) % d.length];
        saveCfg(); applyTheme(); openAllSettingsModal();
      } },
      { label: tr("wp_title"), right: "›", act: function () { openWallpaperModal(); } },
      { label: "Show TV tile on dock", right: cfg.tvEnabled ? "on" : "off", act: function () {
        cfg.tvEnabled = !cfg.tvEnabled; saveCfg(); renderAll(); openAllSettingsModal();
      } },
      { label: tr("hidden_apps"), right: String(cfg.hiddenApps.length) + " ›", act: openHiddenAppsModal },
      { label: tr("reset_order"), right: "", act: function () {
        cfg.appOrder = []; saveCfg(); renderAll(); showToast(tr("order_reset"));
      } },
      { label: "Temperature unit", right: "°" + cfg.tempUnit, act: function () {
        cfg.tempUnit = cfg.tempUnit === "C" ? "F" : "C"; saveCfg(); renderAll(); openAllSettingsModal(true);
      } },
      rootRow("boot", tr("root_boot")),
      rootRow("home", tr("root_home")),
      keepRow()
    ]);
    if (keepFocus) { state.modalIdx = Math.min(focus, state.modalItems.length - 1); renderModalList(); }
  }

  // Long OK on an app tile (dock or All apps): Move / Hide. The TV tile opens Walker instead.
  function openTileMenu(app) {
    if (!app) return;
    if (app.id === "tv") { openWalkerModal(); return; }
    openModal(app.name, app.appId, [
      { label: tr("tile_move"), right: "◀▶", act: function () { closeModal(); startMove(app.id); } },
      { label: tr("tile_hide"), right: "", act: function () {
        if (cfg.hiddenApps.indexOf(app.id) < 0) cfg.hiddenApps.push(app.id);
        saveCfg(); closeModal(); showToast(tr("app_hidden"));
      } },
      { label: tr("tile_cancel"), right: "", act: closeModal }
    ]);
  }

  function openHiddenAppsModal() {
    // Drop ids of apps that no longer exist (e.g. removed from DEFAULT_APPS) so the count stays honest.
    var known = DEFAULT_APPS.map(function (a) { return a.id; });
    var pruned = cfg.hiddenApps.filter(function (id) { return known.indexOf(id) >= 0; });
    if (pruned.length !== cfg.hiddenApps.length) { cfg.hiddenApps = pruned; saveCfg(); }
    var items = DEFAULT_APPS.filter(function (a) { return cfg.hiddenApps.indexOf(a.id) >= 0; }).map(function (a) {
      return { label: a.name, right: tr("hidden_show"), act: function () {
        cfg.hiddenApps = cfg.hiddenApps.filter(function (id) { return id !== a.id; });
        saveCfg(); renderAll();
        if (cfg.hiddenApps.length) openHiddenAppsModal(); else closeModal();
      } };
    });
    if (items.length > 1) items.push({ label: tr("hidden_show_all"), right: String(items.length), act: function () {
      cfg.hiddenApps = []; saveCfg(); renderAll(); closeModal(); showToast(tr("hidden_all_shown"));
    } });
    if (!items.length) items = [{ label: tr("hidden_none"), right: "", act: closeModal }];
    openModal(tr("hidden_apps"), tr("hidden_sub"), items);
  }

  function startMove(id) {
    state.movingId = id;
    state.moveSnapshot = (cfg.appOrder || []).slice();
    state.moveFocus = state.ws === 2 ? state.ws3Idx : state.dockIdx;
    // Pin the current order so every app has an explicit position while it moves.
    cfg.appOrder = orderedApps().map(function (a) { return a.id; });
    renderAll();
  }

  // Move the app being moved by `delta` places among the visible apps (hidden ones keep their slots).
  function moveBy(delta) {
    var vis = visibleApps();
    var from = -1;
    for (var i = 0; i < vis.length; i++) if (vis[i].id === state.movingId) from = i;
    var first = vis[0] && vis[0].id === "tv" ? 1 : 0;   // the TV tile stays first
    var to = Math.max(first, Math.min(vis.length - 1, from + delta));
    if (from < 0 || to === from) return;
    var order = cfg.appOrder.slice();
    order.splice(order.indexOf(state.movingId), 1);
    var anchor = order.indexOf(vis[to].id);
    order.splice(to > from ? anchor + 1 : anchor, 0, state.movingId);
    cfg.appOrder = order;
    if (state.ws === 2) state.ws3Idx = to; else state.dockIdx = to;
    renderAll();
  }

  function finishMove(save) {
    if (!save) {
      cfg.appOrder = state.moveSnapshot || [];
      if (state.ws === 2) state.ws3Idx = state.moveFocus; else state.dockIdx = state.moveFocus;
    }
    state.movingId = null;
    state.moveSnapshot = null;
    saveCfg();
    renderAll();
  }

  function openAboutModal() {
    var sub = "OmarchyTV 2.13.0-beta for LG webOS (community testing — NOT a beta)\n" +
      "Based on OmarchyTV v2.13.0-beta for Android TV by Marco Porro (MIT License).\n" +
      "Includes " + Object.keys(DATA.themes).length + " Omarchy themes, " + [].concat.apply([], Object.keys(DATA.wallpapers).map(function (t) { return DATA.wallpapers[t]; })).length +
      " wallpapers (MIT, and 'Liftoff Sunset' by Tom Ballard), and 37 tte.js effects.";
    openModal(tr("sys_about"), sub, [
      { label: "Version", right: "2.13.0-beta (webOS)", act: closeModal },
      { label: "Android base release", right: "2.13.0-beta", act: closeModal },
      { label: "Wallpaper credit (tokyo-night)", right: "Liftoff Sunset — Tom Ballard", act: closeModal },
      { label: "Close", right: "ESC / Back", act: closeModal }
    ]);
  }

  function openWalkerModal() {
    function buildList(q) {
      var f = (q || "").toLowerCase();
      var apps = visibleApps().filter(function (a) { return !f || a.name.toLowerCase().indexOf(f) >= 0; });
      if (!apps.length) return [{ label: tr("walker_none"), right: "" }];
      return apps.map(function (a) {
        return { label: a.name, right: a.appId, act: function () { closeModal(); launchApp(a); } };
      });
    }
    openModal("Walker · > search", "Filter and launch installed webOS apps", buildList(""), "> search apps…", function (val) {
      state.modalItems = buildList(val);
      state.modalIdx = 0;
      renderModalList();
    });
  }

  // Wallpaper picker: follow the theme (default), plain theme colours, or any theme's background.
  // Picking applies at once and keeps the menu open on that row, so you can compare a few.
  function openWallpaperModal(keepFocus) {
    var focus = state.modalIdx;
    function pick(v) { cfg.wallpaper = v; cfg.randomWallpaper = false; saveCfg(); applyTheme(); openWallpaperModal(true); }
    var items = [
      { label: tr("random_wallpaper"), right: cfg.randomWallpaper ? "on" : "off", act: function () {
        cfg.randomWallpaper = !cfg.randomWallpaper; saveCfg(); openWallpaperModal(true);
      } },
      { label: tr("wp_follow"), right: !cfg.randomWallpaper && !cfg.wallpaper ? "✓" : (!cfg.wallpaper ? "●" : ""), act: function () { pick(""); } },
      { label: tr("wp_plain"), right: !cfg.randomWallpaper && cfg.wallpaper === "plain" ? "✓" : "", act: function () { pick("plain"); } }
    ];
    allWallpapers().forEach(function (p) {
      var parts = p.split("/");   // wallpapers/<theme>/<NN-name>.webp
      var name = parts[parts.length - 1].replace(/^\d+-/, "").replace(/\.\w+$/, "");
      items.push({ label: parts[parts.length - 2] + " · " + name, right: cfg.wallpaper === p ? "✓" : "", act: function () { pick(p); } });
    });
    openModal(tr("wp_title"), tr("wp_sub"), items);
    var cur = 0;
    items.forEach(function (it, i) { if (it.right === "✓") cur = i; });
    state.modalIdx = keepFocus ? Math.min(focus, items.length - 1) : cur;
    renderModalList();
  }


  // ---- v2.13: Ambilight & Lights (Philips Hue Bridge + Home Assistant) -------------------------
  function hexRgb(hex) {
    var h = (hex || "#7aa2f7").replace("#", "");
    return [parseInt(h.slice(0,2),16)||0, parseInt(h.slice(2,4),16)||0, parseInt(h.slice(4,6),16)||0];
  }
  function rgbToXy(r, g, b) {
    function lin(v) { v /= 255; return v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92; }
    var R = lin(r), G = lin(g), B = lin(b);
    var X = R * 0.664511 + G * 0.154324 + B * 0.162028;
    var Y = R * 0.283881 + G * 0.668433 + B * 0.047685;
    var Z = R * 0.000088 + G * 0.072310 + B * 0.986039;
    var s = X + Y + Z;
    return s < 1e-6 ? [0.3127, 0.3290] : [+(X / s).toFixed(4), +(Y / s).toFixed(4)];
  }
  function pushLightsTheme(t, dim) {
    if (!cfg.lightsHub || cfg.lightsHub === "off") return;
    var rgb = hexRgb(dim ? (t.accent2 || t.accent) : t.accent);
    var pct = dim ? Math.min(12, cfg.lightsBri || 70) : (cfg.lightsBri || 70);
    if (cfg.lightsHub === "hue" && cfg.hueIp && cfg.hueUser && cfg.hueGroup) {
      fetch("http://" + cfg.hueIp + "/api/" + encodeURIComponent(cfg.hueUser) + "/groups/" + encodeURIComponent(cfg.hueGroup) + "/action", {
        method: "PUT",
        body: JSON.stringify({ on: true, xy: rgbToXy(rgb[0], rgb[1], rgb[2]), bri: Math.max(1, Math.round(pct * 2.54)), transitiontime: dim ? 20 : 8 })
      }).catch(function () {});
    } else if (cfg.lightsHub === "ha" && cfg.haUrl && cfg.haToken && cfg.haEntity) {
      fetch(cfg.haUrl.replace(/\/+$/, "") + "/api/services/light/turn_on", {
        method: "POST",
        headers: { "Authorization": "Bearer " + cfg.haToken, "Content-Type": "application/json" },
        body: JSON.stringify({ entity_id: cfg.haEntity.split(/[,\s]+/).filter(Boolean), rgb_color: rgb, brightness_pct: pct, transition: dim ? 2 : 1 })
      }).catch(function () {});
    }
  }
  function openLightsModal() {
    var hub = cfg.lightsHub || "off";
    var bri = cfg.lightsBri || 70;
    var items = [
      { label: "Lights hub", right: hub === "hue" ? "Philips Hue" : (hub === "ha" ? "Home Assistant" : "off"), act: function () {
        var order = ["off", "hue", "ha"];
        cfg.lightsHub = order[(order.indexOf(hub) + 1) % order.length];
        saveCfg(); openLightsModal();
      } },
      { label: "Brightness", right: bri + "%", act: function () {
        var steps = [10, 25, 40, 55, 70, 85, 100];
        cfg.lightsBri = steps[(steps.indexOf(bri) + 1) % steps.length];
        saveCfg(); applyTheme(); openLightsModal();
      } }
    ];
    if (hub === "hue") {
      items.push({ label: "Find Hue Bridge (meethue.com)", right: cfg.hueIp || "discover ›", act: function () {
        fetch("https://discovery.meethue.com").then(function (r) { return r.json(); }).then(function (a) {
          if (a && a[0] && a[0].internalipaddress) { cfg.hueIp = a[0].internalipaddress; saveCfg(); showToast("Hue Bridge: " + cfg.hueIp); openLightsModal(); }
          else showToast("No Hue Bridge found on LAN");
        }).catch(function () { showToast("Discovery failed — enter IP manually"); });
      } });
      items.push({ label: "Hue Bridge IP (use input above)", right: cfg.hueIp || "—", act: function () {
        var v = (document.getElementById("modal-input").value || "").trim();
        if (v) { cfg.hueIp = v; saveCfg(); openLightsModal(); }
      } });
      items.push({ label: "Pair (press bridge button first)", right: cfg.hueUser ? "paired" : "pair ›", act: function () {
        if (!cfg.hueIp) { showToast("Set Bridge IP first"); return; }
        fetch("http://" + cfg.hueIp + "/api", { method: "POST", body: JSON.stringify({ devicetype: "omarchytv#webos" }) })
          .then(function (r) { return r.json(); }).then(function (a) {
            if (a && a[0] && a[0].success && a[0].success.username) { cfg.hueUser = a[0].success.username; cfg.hueGroup = cfg.hueGroup || "0"; saveCfg(); showToast("Hue paired"); openLightsModal(); }
            else showToast((a && a[0] && a[0].error && a[0].error.description) || "Press link button on bridge, then retry");
          }).catch(function () { showToast("Cannot reach Hue Bridge"); });
      } });
      items.push({ label: "Entertainment area / Room ID", right: cfg.hueGroup || "0 (all)", act: function () {
        var v = (document.getElementById("modal-input").value || "").trim();
        if (v) { cfg.hueGroup = v; saveCfg(); openLightsModal(); }
      } });
    } else if (hub === "ha") {
      items.push({ label: "Home Assistant URL (use input above)", right: cfg.haUrl || "—", act: function () {
        var v = (document.getElementById("modal-input").value || "").trim();
        if (v) { cfg.haUrl = v; saveCfg(); openLightsModal(); }
      } });
      items.push({ label: "Long-lived token (use input above)", right: cfg.haToken ? "••••••" : "—", act: function () {
        var v = (document.getElementById("modal-input").value || "").trim();
        if (v) { cfg.haToken = v; saveCfg(); openLightsModal(); }
      } });
      items.push({ label: "Light entity_id(s) (use input above)", right: cfg.haEntity || "—", act: function () {
        var v = (document.getElementById("modal-input").value || "").trim();
        if (v) { cfg.haEntity = v; saveCfg(); openLightsModal(); }
      } });
    }
    if (hub !== "off") {
      items.push({ label: "Apply theme colours now", right: "▶", act: function () { pushLightsTheme(currentThemeObj(), false); showToast("Pushed theme to lights"); } });
      items.push({ label: "Forget hub credentials (confirm with OK)", right: "forget", act: function () {
        openModal("Forget hub?", "Remove saved Hue / Home Assistant credentials from this TV?", [
          { label: "Cancel", right: "", act: openLightsModal },
          { label: "Forget hub", right: "confirm", act: function () {
            cfg.lightsHub = "off"; cfg.hueUser = ""; cfg.haToken = ""; saveCfg(); openLightsModal();
          } }
        ]);
      } });
    }
    openModal("Ambilight & Lights", "Sync Philips Hue or Home Assistant lights with Omarchy themes & Cinemarchy", items, "IP / URL / entity_id / token…");
  }

  // v2.13 helpers: HTML-escape for modal labels built from feed data (renderModalList uses innerHTML).
  function escHtml(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  var dhhState = { videos: null, at: 0, error: "", loading: false, timer: 0 };
  function dhhFetchText(url) {
    return fetch(url).then(function (r) { if (!r.ok) throw new Error("HTTP " + r.status); return r.text(); });
  }
  function loadDhh(force, then) {
    var D = window.OmarchyDhh;
    if (!D || dhhState.loading) return;
    dhhState.loading = true;
    D.load(window.localStorage, dhhFetchText, force, Date.now()).then(function (res) {
      dhhState.loading = false;
      dhhState.videos = res.videos; dhhState.at = res.at; dhhState.error = res.error || "";
      if (then) then();
    });
    clearTimeout(dhhState.timer);
    dhhState.timer = setTimeout(function () { loadDhh(true); }, D.TTL_MS);   // every 6 h while the app runs
  }
  function agoText(t) {
    if (!t) return "never";
    var m = Math.round((Date.now() - t) / 60000);
    return m < 1 ? "just now" : m < 60 ? m + " min ago" : Math.round(m / 60) + " h ago";
  }

  // YouTube deep links. Not verified on a real TV: webOS takes contentTarget, Tizen a v=ID payload.
  function openYoutubeApp(id) {
    var url = "https://www.youtube.com/watch?v=" + id;
    try {
      if (window.webOS && window.webOS.service && window.webOS.service.request) {
        window.webOS.service.request("luna://com.webos.applicationManager", {
          method: "launch", parameters: { id: "youtube.leanback.v4", params: { contentTarget: "https://www.youtube.com/tv?v=" + id } }
        });
        return;
      }
      if (window.tizen && window.tizen.application && window.tizen.ApplicationControl) {
        var ac = new window.tizen.ApplicationControl("http://tizen.org/appcontrol/operation/view", null, null, null,
          [new window.tizen.ApplicationControlData("PAYLOAD", ['{"values":"v=' + id + '"}'])]);
        window.tizen.application.launchAppControl(ac, "111299001912", null, function () { window.tizen.application.launch("111299001912"); });
        return;
      }
    } catch (e) {}
    showToast("Open on YouTube: " + url);
  }

  // Optional official IFrame embed ("Play inside Cinemarchy", default off). Commands go over the documented
  // postMessage channel of the embed (no stream extraction). Errors 2/5/100/101/150/153 fall back to the app.
  var embed = { id: "", dur: 0, t: 0, playing: true, idx: 0, timer: 0 };
  var EMBED_ROWS = ["Start", "25%", "50%", "75%", "−30 s", "+30 s", "Play / pause", "Open in YouTube app"];
  function embedCmd(func, args) {
    var f = document.getElementById("yt-embed-frame");
    if (f && f.contentWindow) f.contentWindow.postMessage(JSON.stringify({ event: "command", func: func, args: args || [] }), "*");
  }
  function renderEmbedRail() {
    var rail = document.getElementById("yt-embed-rail");
    if (!rail) return;
    rail.innerHTML = EMBED_ROWS.map(function (r, i) {
      return '<span class="yt-chip' + (i === embed.idx ? " focused" : "") + '">' + (i < 4 ? "[" + (i + 1) + "] " : "") + escHtml(r) + "</span>";
    }).join("");
  }
  function embedAct(i) {
    if (i < 4) { if (embed.dur) embedCmd("seekTo", [embed.dur * i / 4, true]); }
    else if (i === 4) embedCmd("seekTo", [Math.max(0, embed.t - 30), true]);
    else if (i === 5) embedCmd("seekTo", [embed.t + 30, true]);
    else if (i === 6) { embedCmd(embed.playing ? "pauseVideo" : "playVideo"); embed.playing = !embed.playing; }
    else { var id = embed.id; closeEmbed(); openYoutubeApp(id); }
  }
  function openEmbed(id) {
    closeModal();
    var ov = document.getElementById("yt-embed");
    if (!ov) {
      ov = document.createElement("div");
      ov.id = "yt-embed";
      document.body.appendChild(ov);
    }
    embed = { id: id, dur: 0, t: 0, playing: true, idx: 0, timer: 0 };
    ov.innerHTML = '<iframe id="yt-embed-frame" allow="autoplay; encrypted-media" src="https://www.youtube-nocookie.com/embed/' + id +
      '?enablejsapi=1&autoplay=1&rel=0&playsinline=1"></iframe><div id="yt-embed-rail"></div>' +
      '<div class="yt-note">Official YouTube embed · Back closes · ←/→ + OK on the chapter rail</div>';
    ov.className = "open";
    state.embedOpen = true;
    renderEmbedRail();
    var f = document.getElementById("yt-embed-frame");
    f.onload = function () { f.contentWindow.postMessage(JSON.stringify({ event: "listening", id: 1 }), "*"); };
    embed.timer = setTimeout(function () { if (!embed.dur) { showToast("Embed did not start · opening the YouTube app"); var i2 = embed.id; closeEmbed(); openYoutubeApp(i2); } }, 20000);
  }
  function closeEmbed() {
    var ov = document.getElementById("yt-embed");
    clearTimeout(embed.timer);
    if (ov) { ov.className = ""; ov.innerHTML = ""; }
    state.embedOpen = false;
    renderAll();
  }
  window.addEventListener("message", function (ev) {
    if (!state.embedOpen || !/youtube(-nocookie)?\.com$/.test(String(ev.origin).replace(/^https?:\/\/(www\.)?/, ""))) return;
    var m; try { m = JSON.parse(ev.data); } catch (e) { return; }
    if (m.event === "onError") {
      var id = embed.id;
      showToast("YouTube does not allow this video here (error " + m.info + ") · opening the YouTube app");
      closeEmbed(); openYoutubeApp(id);
    } else if (m.event === "infoDelivery" && m.info) {
      if (m.info.duration) embed.dur = m.info.duration;
      if (m.info.currentTime != null) embed.t = m.info.currentTime;
    }
  });
  function embedKey(k, isBack) {
    if (isBack) closeEmbed();
    else if (k === "ArrowLeft") embed.idx = (embed.idx + EMBED_ROWS.length - 1) % EMBED_ROWS.length;
    else if (k === "ArrowRight") embed.idx = (embed.idx + 1) % EMBED_ROWS.length;
    else if (k === "Enter") embedAct(embed.idx);
    else if (k >= "1" && k <= "4") embedAct(parseInt(k, 10) - 1);
    renderEmbedRail();
  }
  function playDhh(v) {
    if (cfg.embedPlayer) openEmbed(v.id);
    else { closeModal(); openYoutubeApp(v.id); }
  }

  function openCinemarchyModal(tabIdx) {
    pushLightsTheme(currentThemeObj(), true);
    var tab = tabIdx || 0;
    var tabs = ["[1] @dhh37", "[2] Streaming", "[3] Live TV", "[4] Player & tools"];
    var items = [
      { label: "Switch tab: " + tabs.join(" · "), right: tabs[tab], act: function () { openCinemarchyModal((tab + 1) % 4); } }
    ];
    var D = window.OmarchyDhh;
    if (tab === 0 && D) {
      if (!dhhState.videos && !dhhState.loading) loadDhh(false, function () { if (state.modalOpen && state.cineTab === 0) openCinemarchyModal(0); });
      var seen = D.lastSeen(window.localStorage);
      var vids = dhhState.videos || D.curated();
      items.push({ label: dhhState.loading ? "Checking the @dhh37 channel feed…" : dhhState.error ? "Offline · showing the saved list (" + escHtml(dhhState.error) + ")" : "Feed updated " + agoText(dhhState.at) + " · OK refreshes",
        right: "↻", act: function () { loadDhh(true, function () { openCinemarchyModal(0); }); openCinemarchyModal(0); } });
      vids.forEach(function (v) {
        var fresh = D.isNew(v, seen, Date.now());
        items.push({ label: (fresh ? "★ " : "▶ ") + escHtml(v.title), right: fresh ? "NEW" : (cfg.embedPlayer ? "play ›" : "youtube ›"), act: function () { playDhh(v); } });
      });
      items.push({ label: "Omarchy manual (omarchy.org)", right: "docs", act: function () { closeModal(); showToast("Open on your TV browser or phone: omarchy.org"); } });
    } else if (tab === 1) {
      ["jellyfin", "plex", "netflix", "prime", "disney", "youtube", "raiplay"].forEach(function (id) {
        var a = DEFAULT_APPS.filter(function (x) { return x.id === id; })[0];
        if (a) items.push({ label: escHtml(a.name), right: a.appId, act: function () { closeModal(); launchApp(a); } });
      });
    } else if (tab === 2) {
      TV_INPUTS.forEach(function (inp) {
        items.push({ label: escHtml(inp.name + " · " + inp.sub), right: "watch ›", act: function () { closeModal(); launchApp(inp); } });
      });
    } else {
      items.push({ label: "Play @dhh37 inside Cinemarchy (official YouTube embed)", right: cfg.embedPlayer ? "on" : "off", act: function () {
        cfg.embedPlayer = !cfg.embedPlayer; saveCfg(); openCinemarchyModal(3);
      } });
      items.push({ label: "⏸ Preview the pause screensaver (10 s)", right: "test", act: function () {
        closeModal();
        state.pauseTitle = "Preview · OK to resume";
        startScreensaver("‖ PAUSED · " + state.pauseTitle);
      } });
    }
    state.cineTab = tab;
    openModal("CINEMARCHY · " + tabs[tab], "", items);
    state.modalDigit = function (n) { if (n >= 1 && n <= 4) openCinemarchyModal(n - 1); };
    var subEl = document.getElementById("modal-sub");
    if (subEl && DATA.cinemarchyLogo) {
      subEl.style.display = "block";
      subEl.innerHTML = '<pre style="font-size:9px;line-height:1.05;color:var(--t-accent);margin-bottom:8px;overflow:hidden;">' + DATA.cinemarchyLogo + '</pre>' +
        '<div style="margin-bottom:6px;"><span style="border:1px solid var(--t-accent);color:var(--t-accent);padding:0 5px;font-weight:bold;">ALPHA</span> Cinemarchy ' + CINEMARCHY_VERSION + ' (preview)</div>' +
        '<div>Press 1-4 or OK on the top row to switch tabs</div>';
    }
  }

  function openOmarchyMenu() {
    openModal("Omarchy Menu", "OmarchyTV 2.13.0-beta (LG webOS)", [
      { label: "🎬 Cinemarchy (Media, Streaming, Live TV & Snippets)", right: "C ›", act: function () { openCinemarchyModal(0); } },
      { label: "🧩 " + tr("sys_plugins"), right: ((cfg.pluginSpacexTv ? 1 : 0) + (cfg.pluginGrokbot ? 1 : 0)) + " on ›", act: openPluginsModal },
      { label: "Walker (app launcher)", right: "›", act: openWalkerModal },
      { label: tr("sys_themes"), right: cfg.theme, act: openThemesModal },
      { label: tr("wp_title"), right: "›", act: function () { openWallpaperModal(); } },
      { label: tr("wx_title"), right: "°" + cfg.tempUnit, act: openWeatherModal },
      { label: tr("sys_saver"), right: "›", act: openSaverModal },
      { label: tr("sys_all"), right: "›", act: openAllSettingsModal },
      { label: "AI meter", right: cfg.aiCredits === "dollars" ? "dollars" : "percent", act: function () {
        cfg.aiCredits = cfg.aiCredits === "dollars" ? "percent" : "dollars";
        saveCfg();
        renderWidgets();
        var row = state.modalItems[state.modalIdx];
        if (row) row.right = cfg.aiCredits === "dollars" ? "dollars" : "percent";
        renderModalList();
      } },
      { label: tr("sys_about"), right: "2.13.0-beta", act: openAboutModal }
    ]);
  }

  var saverStatusOverride = "";
  window.OmarchySaver = {
    text: function () { return cfg.saverArt === "cinemarchy" && DATA.cinemarchyLogo ? DATA.cinemarchyLogo : DATA.logo; },
    saverMode: function () { return cfg.saverMode || "logo"; },
    saverVariant: function () { return cfg.saverArt === "aquarium-classic" ? "classic" : "homage"; },
    palette: function () { return currentThemeObj(); },
    statusOverride: function () { return saverStatusOverride; },
    color: function () { return currentThemeObj().accent; },
    clockColor: function () { return currentThemeObj().fg; },
    showClock: function () { return !!cfg.saverShowClock; },
    clockText: function (d) { return clockText(d); },
    widthFraction: function () { return cfg.saverLogoScale || 0.65; },
    exclude: function () { return ""; },
    forceEffect: function () { return ""; },
    forceMode: function () { return "bundle"; },
    dismiss: function () { stopScreensaver(); },
    log: function (m) { console.log("[OmarchySaver]", m); }
  };

  function startScreensaver(statusText) {
    saverStatusOverride = typeof statusText === "string" ? statusText : "";
    var ov = document.getElementById("saver-overlay");
    var fr = document.getElementById("saver-frame");
    fr.src = "saver/index.html";
    ov.className = "active";
  }

  function stopScreensaver() {
    var ov = document.getElementById("saver-overlay");
    var fr = document.getElementById("saver-frame");
    if (ov.className === "active") {
      ov.className = "";
      fr.src = "about:blank";
      animateLogo();
    }
    // Keys pressed inside the saver iframe never reach this document, so restart the idle clock here.
    markInput();
  }

  // Idle timer: start the screensaver after cfg.saverIdleMin minutes without input (0 = never),
  // only while OmarchyTV is on screen and no menu is open.
  var lastInput = Date.now();
  function markInput() { lastInput = Date.now(); }
  ["keydown", "mousemove", "mousedown", "wheel", "touchstart"].forEach(function (t) {
    document.addEventListener(t, markInput, true);
  });
  document.addEventListener("visibilitychange", markInput);
  setInterval(function () {
    if (!cfg.saverIdleMin || document.hidden || state.modalOpen) return;
    if (document.getElementById("saver-overlay").className === "active") return;
    if (Date.now() - lastInput >= cfg.saverIdleMin * 60000) startScreensaver();
  }, 5000);

  // Keep LG's own screensaver from covering OmarchyTV's. The tvpower service asks registered clients
  // before it starts its screensaver and asks again every few minutes: ack:false vetoes, ack:true lets it
  // run. Veto only while we are on screen and our screensaver is enabled; in the background (HDMI, Live
  // TV, other apps) or with ours set to "never", LG's burn-in protection still runs. Same handshake as
  // Kodi on webOS (xbmc/windowing/wayland/OSScreenSaverWebOS.cpp). window.webOS is not loaded here,
  // so this uses PalmServiceBridge directly.
  if (window.PalmServiceBridge) {
    var lgSaverSub = new PalmServiceBridge(); // kept in scope so the subscription stays alive
    lgSaverSub.onservicecallback = function (msg) {
      var req;
      try { req = JSON.parse(msg); } catch (e) { return; }
      if (req.state !== "Active") return;
      var allow = document.hidden || !cfg.saverIdleMin;
      new PalmServiceBridge().call("luna://com.webos.service.tvpower/power/responseScreenSaverRequest",
        JSON.stringify({ clientName: "com.omarchytv.home", ack: allow, timestamp: req.timestamp }));
    };
    lgSaverSub.call("luna://com.webos.service.tvpower/power/registerScreenSaverRequest",
      JSON.stringify({ subscribe: true, clientName: "com.omarchytv.home" }));
  }

  function showToast(msg) {
    var t = document.getElementById("toast");
    t.textContent = msg;
    t.style.display = "block";
    clearTimeout(t._timer);
    t._timer = setTimeout(function () { t.style.display = "none"; }, 2600);
  }

  // Turn off after idle. Apps may not call tvpower's turnOffScreen directly ("Denied method call"), but
  // the TV's own remote-control API (SSAP on ws://127.0.0.1:3000, used by LG's phone app and Home
  // Assistant) allows it once the user approves this app in an on-screen prompt. The client key from
  // that approval is kept in localStorage.
  var SSAP_KEY = "omarchytv_ssap_key";
  function tvControlApproved() {
    try { return !!localStorage.getItem(SSAP_KEY); } catch (e) { return false; }
  }
  function ssapRequest(uri, done) {
    var ws, finished = false, timer;
    function finish(ok, info) {
      if (finished) return;
      finished = true; clearTimeout(timer);
      try { ws.close(); } catch (e) {}
      if (done) done(ok, info);
    }
    try { ws = new WebSocket("ws://127.0.0.1:3000"); } catch (e) { return finish(false, "unavailable"); }
    timer = setTimeout(function () { finish(false, "no answer"); }, 90000); // time to accept a prompt
    var payload = { forcePairing: false, pairingType: "PROMPT", manifest: { appVersion: "1.0", manifestVersion: 1,
      permissions: ["CONTROL_POWER", "CONTROL_TV_SCREEN", "READ_POWER_STATE"] } };
    try { var key = localStorage.getItem(SSAP_KEY); if (key) payload["client-key"] = key; } catch (e) {}
    ws.onerror = function () { finish(false, "unavailable"); };
    ws.onopen = function () { ws.send(JSON.stringify({ type: "register", id: "reg", payload: payload })); };
    ws.onmessage = function (m) {
      var msg;
      try { msg = JSON.parse(m.data); } catch (e) { return; }
      if (msg.id === "reg" && msg.type === "registered") {
        try { localStorage.setItem(SSAP_KEY, msg.payload["client-key"]); } catch (e) {}
        ws.send(JSON.stringify({ type: "request", id: "req", uri: uri, payload: {} }));
      } else if (msg.id === "reg" && msg.type === "error") {
        try { localStorage.removeItem(SSAP_KEY); } catch (e) {}
        finish(false, "not approved");
      } else if (msg.id === "req") {
        finish(msg.type === "response" && !(msg.payload && msg.payload.returnValue === false), msg.type);
      }
    };
  }
  // Fire once per idle stretch, only while OmarchyTV is on screen and TV control is approved (an
  // unattended night-time approval prompt would help nobody). Any input re-arms it.
  var offSent = false;
  setInterval(function () {
    if (!cfg.offIdleMin || document.hidden || Date.now() - lastInput < cfg.offIdleMin * 60000) { offSent = false; return; }
    if (offSent || !tvControlApproved()) return;
    offSent = true;
    ssapRequest(cfg.offMode === "tv" ? "ssap://system/turnOff" : "ssap://com.webos.service.tvpower/power/turnOffScreen");
  }, 5000);

  function launchApp(app) {
    if (!app) return;
    try {
      if (window.webOS && window.webOS.service && window.webOS.service.request) {
        window.webOS.service.request("luna://com.webos.applicationManager", {
          method: "launch",
          parameters: { id: app.appId }
        });
        return;
      }
      var tzId = app.tizenId || (window.OMARCHY_TIZEN_IDS || {})[app.id];
      if (window.tizen && window.tizen.application && tzId) {   // Samsung Tizen build (tizen/), see tizen-shim.js
        window.tizen.application.launch(tzId);
        return;
      }
      if (window.PalmServiceBridge) {
        var b = new window.PalmServiceBridge();
        b.call("luna://com.webos.applicationManager/launch", JSON.stringify({ id: app.appId }));
        return;
      }
    } catch (e) {}
    showToast("Launching " + app.name + " (" + app.appId + ")");
  }

  function switchWorkspace(ws) {
    state.ws = ws;
    if (ws === 0) { state.zone = "dock"; animateLogo(); }
    else if (ws === 1) state.zone = "ws2";
    else if (ws === 2) state.zone = "ws3";
    else if (ws === 3) state.zone = "ws4";
    else if (ws === 4) state.zone = "ws5";
    renderAll();
  }


  // v2.13 Grok Bot ball mark, redrawn from Tom Ballard's MIT grokbot-desktop.svg ((c) 2026 Tom Ballard):
  // round orange body (#E06512, or the mirrored bot's colour), soft highlight, capsule eyes (lid 1 open .. 0.1 shut).
  function orbSvg(color, lid, px) {
    var c = /^#[0-9a-fA-F]{6}$/.test(color || "") ? color : "#E06512";
    var n = parseInt(c.slice(1), 16), luma = (0.2126 * (n >> 16) + 0.7152 * ((n >> 8) & 255) + 0.0722 * (n & 255)) / 255;
    var eye = luma < 0.25 ? "#F4F4F5" : "#141416";
    var h = Math.max(3.2, 12 * (lid == null ? 1 : lid)), y = 27 - h / 2;
    return '<svg class="gb-orb" viewBox="0 0 64 64" width="' + (px || 44) + '" height="' + (px || 44) + '" aria-hidden="true">' +
      '<defs><radialGradient id="gbShine" cx="0.36" cy="0.3" r="0.6"><stop offset="0" stop-color="#fff" stop-opacity="0.42"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>' +
      '<circle cx="32" cy="32" r="28" fill="' + c + '" stroke="#141416" stroke-opacity="0.55" stroke-width="1.6"/>' +
      '<circle cx="32" cy="32" r="28" fill="url(#gbShine)"/>' +
      '<rect x="20.4" y="' + y + '" width="5.2" height="' + h + '" rx="2.6" fill="' + eye + '"/>' +
      '<rect x="38.4" y="' + y + '" width="5.2" height="' + h + '" rx="2.6" fill="' + eye + '"/></svg>';
  }
  var gbLid = 1;
  var grokbotBlink = false;
  setInterval(function () {
    if (!cfg.pluginGrokbot || document.hidden) return;
    if (Math.random() < 0.3) { gbLid = 0.5; renderPluginsUI(); setTimeout(function () { gbLid = 1; renderPluginsUI(); }, 1400); return; }   // idle squint
    grokbotBlink = true;
    renderPluginsUI();
    setTimeout(function () { grokbotBlink = false; renderPluginsUI(); }, 180);
  }, 3200);

  function renderPluginsUI() {
    var pill = document.getElementById("spacex-pill");
    if (pill) {
      pill.style.display = cfg.pluginSpacexTv ? "inline-flex" : "none";
      pill.textContent = "🚀 SpaceX TV · launches";
    }
    var gb = document.getElementById("grokbot-companion");
    if (gb) {
      gb.style.display = (cfg.pluginGrokbot && state.ws === 0) ? "block" : "none";
      var eyes = document.getElementById("grokbot-eyes");
      var sub = document.getElementById("grokbot-sub");
      if (eyes) eyes.innerHTML = orbSvg(cfg.grokbotColor, grokbotBlink ? 0.1 : gbLid, 44) + '<span class="gb-name">' + escHtml(cfg.grokbotName || "Grok Bot") + "</span>";
      if (sub) sub.textContent = cfg.grokbotMessage ? cfg.grokbotMessage : "Blue key · chat";
      placeCompanion(gb);
    }
  }

  function openPluginsModal() {
    openModal(tr("sys_plugins"), "Sandboxed declarative JSON plugins (v1 schema · zero native code execution)", [
      { label: "🚀 SpaceX TV (@sighmon · MIT)", right: cfg.pluginSpacexTv ? "on" : "off", act: function () {
        cfg.pluginSpacexTv = !cfg.pluginSpacexTv; saveCfg(); renderPluginsUI(); openPluginsModal();
      } },
      { label: "🚀 Open SpaceX TV Launches & Live Streams", right: "4 streams ›", act: openSpacexModal },
      { label: "🤖 Omarchy Grok Bot Desktop Companion (@tcballard · MIT)", right: cfg.pluginGrokbot ? "on" : "off", act: function () {
        cfg.pluginGrokbot = !cfg.pluginGrokbot; saveCfg(); renderPluginsUI(); openPluginsModal();
      } },
      { label: "🤖 Open Grok Bot Desktop Companion", right: "[ ◉ ◉ ] ›", act: openGrokbotCompanionModal }
    ]);
  }

  function openSpacexModal() {
    var streams = [
      { title: "🚀 SpaceX launches · next-launch countdown", sub: "spacex.com/launches" },
      { title: "🛰️ SpaceX broadcasts on X", sub: "x.com/SpaceX" },
      { title: "🎬 Starship (spacex.com)", sub: "spacex.com/vehicles/starship" }
    ];
    var items = streams.map(function (s) {
      return { label: s.title, right: s.sub, act: function () {
        closeModal();
        showToast("Open on your TV browser or phone: " + s.sub);
      } };
    });
    items.push({ label: "Top-bar countdown pill", right: cfg.pluginSpacexTv ? "on" : "off", act: function () {
      cfg.pluginSpacexTv = !cfg.pluginSpacexTv; saveCfg(); renderPluginsUI(); openSpacexModal();
    } });
    openModal("SpaceX TV · com.sighmon.spacex-tv", "Based on sighmon's Omarchy SpaceX TV plugin (github.com/sighmon/omarchy-spacex-tv · MIT)", items);
  }

  // v2.13 Grok Bot Companion (TV), webOS/Tizen. Experimental, tracks @tcballard's in-development
  // Grok Bot Desktop Companion (MIT); my own code. Never takes D-pad focus by itself: the Blue colour key
  // (or a click) opens the chat. There is no omarchy-link channel on webOS/Tizen yet, so questions go to
  // grok.com in the TV browser. Voice: the Web Speech API when the TV's engine has it, else keyboard only.
  var COMPANION_CORNERS = ["bottom-right", "bottom-left", "top-right", "top-left"];
  function placeCompanion(el) {
    var c = COMPANION_CORNERS.indexOf(cfg.companionCorner) >= 0 ? cfg.companionCorner : "bottom-right";
    var ox = 48 + (cfg.companionOffsetX || 0), oy = (cfg.companionOffsetY || 0);
    el.style.left = el.style.right = el.style.top = el.style.bottom = "";
    if (c.indexOf("right") >= 0) el.style.right = ox + "px"; else el.style.left = ox + "px";
    if (c.indexOf("bottom") >= 0) el.style.bottom = (340 + oy) + "px"; else el.style.top = (120 + oy) + "px";
  }
  function speechCtor() { return window.SpeechRecognition || window.webkitSpeechRecognition || null; }
  function askGrokWeb(q) {
    var url = "https://grok.com/?q=" + encodeURIComponent(q);
    try {
      if (window.webOS && window.webOS.service && window.webOS.service.request) {
        window.webOS.service.request("luna://com.webos.applicationManager", { method: "launch", parameters: { id: "com.webos.app.browser", params: { target: url } } });
        return;
      }
      if (window.tizen && window.tizen.ApplicationControl) {
        window.tizen.application.launchAppControl(new window.tizen.ApplicationControl("http://tizen.org/appcontrol/operation/view", url), null, null, null);
        return;
      }
    } catch (e) {}
    showToast("Ask Grok: " + url);
  }
  function openCompanionChat() {
    var q = "";
    var voice = speechCtor();
    var items = [
      { label: "Send to Grok (opens grok.com in the TV browser)", right: "OK ›", act: function () {
        if (!q.trim()) { showToast("Type a question first (▲ goes back to the field)"); return; }
        closeModal(); askGrokWeb(q.trim());
      } },
      voice ? { label: "🎤 Speak instead", right: "voice", act: function () {
        try {
          var r = new voice(); r.lang = navigator.language || "en-US";
          r.onresult = function (ev) { q = ev.results[0][0].transcript; modalInput.value = q; showToast("Heard: " + q); };
          r.onerror = function () { showToast("Voice is not available here · use the keyboard"); };
          r.start(); showToast("Listening…");
        } catch (e) { showToast("Voice is not available here · use the keyboard"); }
      } } : { label: "Voice: not available on this TV · keyboard only", right: "", act: function () {} },
      { label: "Companion corner", right: cfg.companionCorner || "bottom-right", act: function () {
        var i = COMPANION_CORNERS.indexOf(cfg.companionCorner || "bottom-right");
        cfg.companionCorner = COMPANION_CORNERS[(i + 1) % 4]; saveCfg(); renderPluginsUI();
        state.modalItems[state.modalIdx].right = cfg.companionCorner; renderModalList();
      } },
      { label: "Show floating companion on Home", right: cfg.pluginGrokbot ? "on" : "off", act: function () {
        cfg.pluginGrokbot = !cfg.pluginGrokbot; saveCfg(); renderPluginsUI();
        state.modalItems[state.modalIdx].right = cfg.pluginGrokbot ? "on" : "off"; renderModalList();
      } }
    ];
    openModal("Grok Bot Companion (TV) · experimental",
      "Tracks @tcballard's in-development Grok Bot Desktop Companion (MIT) · Blue key opens this chat", items,
      "Ask anything… (on-screen or Bluetooth keyboard)", function (v) { q = v; },
      { noAutoFocus: true, focusIdx: voice ? 1 : 0 });
    var subEl = document.getElementById("modal-sub");
    if (subEl) subEl.innerHTML = '<div style="display:flex;align-items:center;gap:12px;">' + orbSvg(cfg.grokbotColor, 1, 40) +
      "<div>Tracks @tcballard's in-development Grok Bot Desktop Companion (MIT) · Blue key opens this chat</div></div>";
  }

  function openGrokbotCompanionModal() {
    openModal("Grok Bot Desktop Companion · io.github.tcballard.grokbot-desktop",
      "Experimental, tracks @tcballard's in-development branch (MIT) · the PC bridge runs on Android TV via omarchy-link", [
      { label: "💬 Chat with the companion", right: "OK ›", act: openCompanionChat },
      { label: "Show floating companion on Home", right: cfg.pluginGrokbot ? "on" : "off", act: function () {
        cfg.pluginGrokbot = !cfg.pluginGrokbot; saveCfg(); renderPluginsUI(); openGrokbotCompanionModal();
      } },
      { label: "Companion corner", right: cfg.companionCorner || "bottom-right", act: function () {
        var j = COMPANION_CORNERS.indexOf(cfg.companionCorner || "bottom-right");
        cfg.companionCorner = COMPANION_CORNERS[(j + 1) % 4]; saveCfg(); renderPluginsUI(); openGrokbotCompanionModal();
      } }
    ]);
  }

  function renderAll() {
    renderTerminal();
    renderWidgets();
    renderDock();
    renderWorkspaces();
    renderPluginsUI();
    if (state.modalOpen) renderModalList();
  }

  function activateBarItem(idx) {
    if (idx === 0) openOmarchyMenu();
    else if (idx >= 1 && idx <= 5) switchWorkspace(idx - 1);
    else if (idx === 6) openCinemarchyModal(0);
    else if (idx === 10) switchWorkspace(1);
    else if (idx === 11) startScreensaver();
    else launchApp({ name: "LG Settings", appId: "com.palm.app.settings" });
  }

  // Home widgets that can take focus, in on-screen order (state.widgetIdx: 0 terminal, 1 weather,
  // 2 pc, 3 ai credits). Hidden ones (Home widgets menu) are skipped, so ▲ ▼ never stop on nothing.
  function focusableWidgets() {
    return [cfg.widgets.term, cfg.widgets.weather, cfg.widgets.pc, cfg.widgets.ai]
      .map(function (on, i) { return on ? i : -1; })
      .filter(function (i) { return i >= 0; });
  }

  // Moving into the widget row: land on a visible widget, or report that there is none.
  function enterWidgets() {
    var vw = focusableWidgets();
    if (!vw.length) return false;
    if (vw.indexOf(state.widgetIdx) < 0) state.widgetIdx = vw[0];
    return true;
  }

  // Keyboard / LG Remote Navigation
  var LEGACY_KEYS = { 8: "Backspace", 13: "Enter", 27: "Escape", 37: "ArrowLeft", 38: "ArrowUp", 39: "ArrowRight", 40: "ArrowDown" };
  window.addEventListener("keydown", function (e) {
    var ov = document.getElementById("saver-overlay");
    if (ov.className === "active") {
      stopScreensaver();
      e.preventDefault();
      return;
    }

    var code = e.keyCode;
    // webOS 3.x (Chrome 38) has no KeyboardEvent.key: derive it from the remote's keyCode
    var k = e.key || LEGACY_KEYS[code] || (code >= 48 && code <= 57 ? String(code - 48) : "");
    var isBack = (k === "Escape" || k === "Backspace" || code === 461 || code === 10009);   // 10009: Samsung Tizen Return

    // A long press opened a menu while OK is still held: drop its auto-repeat so it can't pick an item.
    if (state.okHeld && k === "Enter") { e.preventDefault(); return; }

    if (state.movingId) {
      var step = state.ws === 2 ? 7 : 0;
      if (k === "ArrowLeft") moveBy(-1);
      else if (k === "ArrowRight") moveBy(1);
      else if (k === "ArrowUp" && step) moveBy(-step);
      else if (k === "ArrowDown" && step) moveBy(step);
      else if (k === "Enter") finishMove(true);
      else if (isBack) finishMove(false);
      e.preventDefault();
      e.stopPropagation();
      return;
    }

    if (state.embedOpen) { embedKey(k, isBack); e.preventDefault(); return; }
    if ((code === 406 || k === "ColorF3Blue") && !state.modalOpen && cfg.pluginGrokbot) { openCompanionChat(); e.preventDefault(); return; }

    if (state.modalOpen) {
      if (isBack) { closeModal(); e.preventDefault(); return; }
      if (state.modalDigit && k >= "1" && k <= "9" && document.activeElement !== modalInput) { state.modalDigit(parseInt(k, 10)); e.preventDefault(); return; }
      if (k === "ArrowDown") { state.modalIdx = (state.modalIdx + 1) % Math.max(1, state.modalItems.length); renderModalList(); e.preventDefault(); return; }
      if (k === "ArrowUp") { state.modalIdx = (state.modalIdx - 1 + Math.max(1, state.modalItems.length)) % Math.max(1, state.modalItems.length); renderModalList(); e.preventDefault(); return; }
      if (k === "Enter") {
        var it = state.modalItems[state.modalIdx];
        if (it && it.act) it.act();
        e.preventDefault();
        return;
      }
      return;
    }

    // Number keys 1..5 switch workspaces directly
    if (k >= "1" && k <= "5") {
      switchWorkspace(parseInt(k, 10) - 1);
      e.preventDefault();
      return;
    }

    if (isBack) {
      // webOS hides the app when Back (461) is not cancelled. The Home key still leaves.
      // Back from another workspace returns to the dock; Back on the dock stays in the app.
      if (state.ws !== 0) switchWorkspace(0);
      e.preventDefault();
      e.stopPropagation();
      return;
    }

    if ((k === "c" || k === "C" || code === 67) && !state.modalOpen) {
      openCinemarchyModal(0);
      e.preventDefault();
      return;
    }

    if (state.zone === "bar") {
      if (k === "ArrowRight") { state.barIdx = Math.min(11, state.barIdx + 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowLeft") { state.barIdx = Math.max(0, state.barIdx - 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowDown") {
        if (state.ws === 0) state.zone = enterWidgets() ? "widgets" : "dock";
        else state.zone = "ws" + (state.ws + 1);
        renderAll(); e.preventDefault(); return;
      }
      if (k === "Enter") { activateBarItem(state.barIdx); e.preventDefault(); return; }
      return;
    }

    if (state.ws === 0 && state.zone === "widgets") {
      if (k === "ArrowRight" || k === "ArrowLeft") {
        var vw = focusableWidgets(), at = vw.indexOf(state.widgetIdx);
        var next = vw[Math.max(0, Math.min(vw.length - 1, at + (k === "ArrowRight" ? 1 : -1)))];
        if (next !== undefined) state.widgetIdx = next;
        renderAll(); e.preventDefault(); return;
      }
      if (k === "ArrowUp") { state.zone = "bar"; renderAll(); e.preventDefault(); return; }
      if (k === "ArrowDown") { state.zone = "dock"; renderAll(); e.preventDefault(); return; }
      if (k === "Enter") {
        if (state.widgetIdx === 0) openThemesModal();
        else if (state.widgetIdx === 1) openWeatherModal();
        else switchWorkspace(3);
        e.preventDefault(); return;
      }
      return;
    }

    if (state.ws === 0 && state.zone === "dock") {
      var apps = visibleApps();
      if (k === "ArrowRight") { state.dockIdx = Math.min(apps.length - 1, state.dockIdx + 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowLeft") { state.dockIdx = Math.max(0, state.dockIdx - 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowUp") { state.zone = enterWidgets() ? "widgets" : "bar"; renderAll(); e.preventDefault(); return; }
      if (k === "Enter") { pressOk(apps[state.dockIdx]); e.preventDefault(); return; }
    }

    if (state.ws === 1 && state.zone === "ws2") {
      if (k === "ArrowRight") { state.ws2Idx = Math.min(TV_INPUTS.length - 1, state.ws2Idx + 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowLeft") { state.ws2Idx = Math.max(0, state.ws2Idx - 1); renderAll(); e.preventDefault(); return; }
      // 4 tiles per row (#ws2-grid): ▼ goes to the row below, ▲ to the row above or the bar
      if (k === "ArrowDown") { if (state.ws2Idx + 4 < TV_INPUTS.length) { state.ws2Idx += 4; renderAll(); } e.preventDefault(); return; }
      if (k === "ArrowUp") {
        if (state.ws2Idx >= 4) state.ws2Idx -= 4;
        else state.zone = "bar";
        renderAll(); e.preventDefault(); return;
      }
      if (k === "Enter") { launchApp(TV_INPUTS[state.ws2Idx]); e.preventDefault(); return; }
    }

    if (state.ws === 2 && state.zone === "ws3") {
      var all = visibleApps();
      if (k === "ArrowRight") { state.ws3Idx = Math.min(all.length - 1, state.ws3Idx + 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowLeft") { state.ws3Idx = Math.max(0, state.ws3Idx - 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowDown") { state.ws3Idx = Math.min(all.length - 1, state.ws3Idx + 7); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowUp") {
        if (state.ws3Idx >= 7) state.ws3Idx -= 7;
        else state.zone = "bar";
        renderAll(); e.preventDefault(); return;
      }
      if (k === "Enter") { pressOk(all[state.ws3Idx]); e.preventDefault(); return; }
    }

    if (state.ws === 3 && state.zone === "ws4") {
      if (k === "ArrowRight") { state.ws4Idx = 1; renderAll(); e.preventDefault(); return; }
      if (k === "ArrowLeft") { state.ws4Idx = 0; renderAll(); e.preventDefault(); return; }
      if (k === "ArrowUp") { state.zone = "bar"; renderAll(); e.preventDefault(); return; }
      if (k === "Enter") {
        if (state.ws4Idx === 1) {
          cfg.pcSimulated = !cfg.pcSimulated;
          saveCfg(); pollPcStatus(); renderAll();
        } else {
          openModal("Pair Omarchy Linux PC", "Enter your Omarchy PC LAN IP (serves http://<ip>:8765/v1/status):", [
            { label: "Use 192.168.1.100:8765", right: "LAN", act: function () { cfg.pcHost = "192.168.1.100"; saveCfg(); pollPcStatus(); closeModal(); } },
            { label: "Toggle simulated PC link", right: cfg.pcSimulated ? "on" : "off", act: function () { cfg.pcSimulated = !cfg.pcSimulated; saveCfg(); pollPcStatus(); closeModal(); } }
          ], "192.168.1.x");
        }
        e.preventDefault(); return;
      }
    }

    if (state.ws === 4 && state.zone === "ws5") {
      var rows = getSysRows();
      if (k === "ArrowDown") { state.ws5Idx = Math.min(rows.length - 1, state.ws5Idx + 1); renderAll(); e.preventDefault(); return; }
      if (k === "ArrowUp") {
        if (state.ws5Idx > 0) state.ws5Idx--;
        else state.zone = "bar";
        renderAll(); e.preventDefault(); return;
      }
      if (k === "Enter") { rows[state.ws5Idx].act(); e.preventDefault(); return; }
    }
  });

  // OK on an app tile: a short press launches on key-up, holding it for LONG_OK_MS opens the tile menu.
  var LONG_OK_MS = 600;
  function pressOk(app) {
    if (state.okTimer) return; // auto-repeat while held
    state.okTimer = setTimeout(function () {
      state.okTimer = null;
      state.okHeld = true;
      // Walker focuses its search field, which brings up webOS's on-screen keyboard; opened while OK
      // is still held, the keyboard would take OK's auto-repeat and type letters. Open it on release.
      if (app && app.id === "tv") state.walkerOnRelease = true;
      else openTileMenu(app);
    }, LONG_OK_MS);
    state.okApp = app;
  }

  window.addEventListener("keyup", function (e) {
    if (e.keyCode !== 13 && e.key !== "Enter") return;
    if (state.okHeld) {
      state.okHeld = false;
      if (state.walkerOnRelease) { state.walkerOnRelease = false; openWalkerModal(); }
      e.preventDefault();
      return;
    }
    if (state.okTimer) {
      clearTimeout(state.okTimer);
      state.okTimer = null;
      launchApp(state.okApp);
      e.preventDefault();
    }
  });

  // Click bindings for Waybar and Widgets
  var waybarButtons = document.querySelectorAll("[data-bar-idx]");
  for (var bi = 0; bi < waybarButtons.length; bi++) {
    (function (button) {
      var idx = parseInt(button.getAttribute("data-bar-idx"), 10);
      button.onclick = function () {
        if (state.modalOpen) return;
        state.barIdx = idx;
        state.zone = "bar";
        renderAll();
        activateBarItem(idx);
      };
      button.onmouseenter = function () {
        if (state.modalOpen) return;
        state.barIdx = idx;
        state.zone = "bar";
        renderAll();
      };
    })(waybarButtons[bi]);
  }

  var spEl = document.getElementById("spacex-pill");
  if (spEl) spEl.onclick = openSpacexModal;
  var gbEl = document.getElementById("grokbot-companion");
  if (gbEl) gbEl.onclick = openCompanionChat;
  document.getElementById("wx-box").onclick = openWeatherModal;
  document.getElementById("term-box").onclick = openThemesModal;
  document.getElementById("pc-box").onclick = function () { switchWorkspace(3); };
  document.getElementById("ai-box").onclick = function () { switchWorkspace(3); };
  document.getElementById("ws4-pair-btn").onclick = function () { state.zone = "ws4"; state.ws4Idx = 0; renderAll(); };
  document.getElementById("ws4-demo-btn").onclick = function () { cfg.pcSimulated = !cfg.pcSimulated; saveCfg(); pollPcStatus(); renderAll(); };

  // Expose helper API for automated simulator tests
  window.OmarchyTestAPI = {
    switchWorkspace: switchWorkspace,
    openWeatherModal: openWeatherModal,
    openThemesModal: openThemesModal,
    openWalkerModal: openWalkerModal,
    openCinemarchyModal: openCinemarchyModal,
    openPluginsModal: openPluginsModal,
    openSpacexModal: openSpacexModal,
    openGrokbotCompanionModal: openGrokbotCompanionModal,
    openCompanionChat: openCompanionChat,
    openOmarchyMenu: openOmarchyMenu,
    openLightsModal: openLightsModal,
    openAllSettingsModal: openAllSettingsModal,
    openEmbed: openEmbed,
    closeEmbed: closeEmbed,
    openAboutModal: openAboutModal,
    startScreensaver: startScreensaver,
    stopScreensaver: stopScreensaver,
    setTheme: function (t) { cfg.theme = t; cfg.wallpaperIdx = 0; saveCfg(); applyTheme(); renderAll(); },
    setTempUnit: function (u) { cfg.tempUnit = u; saveCfg(); renderAll(); },
    setLang: function (l) { cfg.lang = l; saveCfg(); renderAll(); },
    setSimulatedPc: function (v) { cfg.pcSimulated = v; saveCfg(); pollPcStatus(); renderAll(); },
    connectPc: function (host) { cfg.pcSimulated = false; cfg.pcHost = host; saveCfg(); return pollPcStatus().then(function (s) { renderAll(); return s; }); }
  };

  // Boot
  fitStage();
  applyTheme();
  updateClock();
  setInterval(updateClock, 15000);
  animateLogo();
  renderAll();
  fetchWeather();
  pollPcStatus();

  // Keep the PC panels live: refresh /v1/status every 5 s while the app is on screen. A request that
  // gets no answer (e.g. a firewall dropping packets) can hang for a long time, so skip ticks while one
  // is still pending instead of piling them up.
  var pcPollPending = false;
  setInterval(function () {
    if (pcPollPending || document.hidden) return;
    pcPollPending = true;
    pollPcStatus().then(function () { pcPollPending = false; }, function () { pcPollPending = false; });
  }, 5000);
})();
