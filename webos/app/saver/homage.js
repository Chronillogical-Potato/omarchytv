/*
 * OmarchyTV "Aquarium" screensaver: homage edition (v2.13).
 * My personal tribute to the 90s demoscene: asciiquarium meets Future Crew's Second Reality (1993).
 * Inspired by asciiquarium by Kirk Baucom (GPL); no asciiquarium code or ASCII art is copied.
 * Every sprite, effect, and note of music here is my own original work (MIT, OmarchyTV).
 * Plain ES5 on purpose: it has to run on old LG webOS and Samsung Tizen browsers.
 */
(function (G) {
  'use strict';
  var COLS = 112, ROWS = 36, RULER_COL = 103; // creatures stay left of the ruler band
  var PARTS = [
    { name: 'SURFACE', bpm: 108, chords: [[48, 52, 55, 59], [45, 48, 52, 55], [41, 45, 48, 52], [43, 47, 50, 55]] },
    { name: 'DESCENT', bpm: 88, chords: [[45, 48, 52, 55], [41, 45, 48, 52], [38, 41, 45, 48], [40, 43, 47, 50]] },
    { name: 'MID WATERS', bpm: 100, chords: [[50, 53, 57, 60], [43, 47, 50, 53], [45, 48, 52, 55], [48, 52, 55, 59]] },
    { name: 'ABYSS', bpm: 66, chords: [[48, 51, 55, 62], [44, 48, 51, 55], [41, 44, 48, 51], [43, 46, 50, 55]] },
    { name: 'END CREDITS', bpm: 125, chords: [[48, 51, 55, 58], [44, 48, 51, 55], [41, 44, 48, 51], [43, 47, 50, 53]] }
  ];
  var MEL = [
    [0, -1, -1, 2, -1, -1, 1, -1, 3, -1, -1, -1, 2, -1, 1, -1, 0, -1, -1, -1, 4, -1, 3, -1, 2, -1, -1, 1, -1, -1, -1, -1],
    [2, -1, 3, -1, 4, -1, -1, 3, -1, 2, -1, -1, 1, -1, -1, -1, 2, -1, 1, -1, 0, -1, -1, -1, -1, -1, 1, -1, 2, -1, -1, -1],
    [4, -1, -1, -1, 3, -1, 2, -1, -1, -1, 0, -1, 1, -1, 2, -1, 3, -1, -1, 2, -1, -1, 4, -1, -1, -1, -1, -1, 3, -1, -1, -1]
  ];
  var PAN = [-0.75, 0.75, 0.6, -0.6]; // Amiga/ST3 style L R R L
  function mf(m) { return 440 * Math.pow(2, (m - 69) / 12); }
  function leviathanWindow(fr) { return fr > 0.45 && fr < 0.8; }

  // ---- deterministic tracker song: the same events drive live audio, the offline demo render and the VU meters
  function songEvents(t0, t1, partSec) {
    var out = [], k0 = Math.max(0, Math.floor(t0 / partSec)), k1 = Math.floor(t1 / partSec);
    for (var k = k0; k <= k1; k++) {
      var p = k % 5, cyc = Math.floor(k / 5), P = PARTS[p], ps = k * partSec, sd = 15 / P.bpm;
      if (ps >= t0 && ps < t1) out.push({ type: 'part', t: ps, p: p });
      var s = Math.max(0, Math.ceil((t0 - ps) / sd - 1e-9));
      for (; ; s++) {
        var t = ps + s * sd;
        if (t >= t1 || t >= ps + partSec - 0.05) break;
        genStep(out, p, s, cyc, t, sd, (t - ps) / partSec);
      }
    }
    return out;
  }
  function ev(out, ch, t, m, dur, vel, wave, atk, rel, extra) {
    out.push({ type: 'note', ch: ch, t: t, f: mf(m), dur: dur, vel: vel, wave: wave, atk: atk || 0.008, rel: rel || 0.08, arp: extra || null });
  }
  function genStep(out, p, s, cyc, t, sd, fr) {
    var P = PARTS[p], bar = Math.floor(s / 16), st = s % 16, ch = P.chords[bar % 4], n = ch.length;
    var mel = MEL[(Math.floor(bar / 2) + cyc + p) % 3], mi = s % 32;
    var tr = (cyc % 2 && p === 4) ? 2 : 0;
    if (p === 0) {
      if (st === 0 || st === 6 || st === 8 || st === 14) ev(out, 0, t, ch[0] - 12, sd * 1.6, 0.34, 'sawtooth');
      if (s % 2 === 0) ev(out, 1, t, ch[(s / 2) % n] + 12, sd * 1.2, 0.16, 'triangle');
      if (st === 0) for (var i = 0; i < 3; i++) ev(out, 2, t, ch[i] + 12, sd * 15, 0.05, 'triangle', 0.35, 1.2);
      if (mel[mi] >= 0 && bar % 4 >= 2) ev(out, 3, t, ch[mel[mi] % n] + 24, sd * 2.5, 0.06, 'square', 0.01, 0.18);
    } else if (p === 1) {
      if (st === 0 || st === 8) ev(out, 0, t, ch[0] - 12, sd * 7, 0.3, 'sine', 0.02, 0.4);
      if (s % 4 === 0) ev(out, 1, t, ch[(n - 1) - (s / 4) % n] + 12, sd * 3, 0.12, 'triangle', 0.01, 0.3);
      if (st === 0) for (var j = 0; j < 3; j++) ev(out, 2, t, ch[j] + 12, sd * 15.5, 0.055, 'sine', 0.6, 1.8);
      if (s % 32 === 0) ev(out, 3, t, 88, 0.3, 0.07, 'sine', 0.005, 0.25);
      if (s % 32 === 3) ev(out, 3, t, 88, 0.3, 0.025, 'sine', 0.005, 0.25);
    } else if (p === 2) {
      if (st === 0 || st === 3 || st === 6 || st === 10 || st === 12) ev(out, 0, t, ch[0] - 12 + (st === 10 ? 7 : 0), sd * 1.4, 0.32, 'sawtooth');
      if (st % 4 === 0) ev(out, 1, t, ch[0] + 12, sd * 2, 0.07, 'square', 0.006, 0.06, [mf(ch[0] + 12), mf(ch[1] + 12), mf(ch[2] + 12)]);
      if (st === 0) for (var q = 0; q < 3; q++) ev(out, 2, t, ch[q] + 12, sd * 15, 0.05, 'triangle', 0.4, 1.4);
      if (mel[mi] >= 0) ev(out, 3, t, ch[mel[mi] % n] + 24, sd * 2.2, 0.05, 'triangle', 0.01, 0.2);
    } else if (p === 3) {
      if (st === 0) ev(out, 0, t, ch[0] - 24, sd * 15.5, 0.36, 'sine', 0.5, 2.0);
      if (st === 0) for (var a = 0; a < n; a++) ev(out, 2, t, ch[a] + (a % 2 ? 12 : 0), sd * 15.8, 0.06, 'sawtooth', 1.2, 3.0);
      if (st === 0) ev(out, 3, t, 88, 0.35, 0.06, 'sine', 0.005, 0.3);
      if (st === 6) ev(out, 3, t, 88, 0.35, 0.02, 'sine', 0.005, 0.3);
      if (leviathanWindow(fr) && s % 2 === 0) ev(out, 1, t, ch[(s / 2) % n] + 24, sd * 1.6, 0.09, 'triangle', 0.008, 0.3);
    } else {
      var bl = [0, 0, 12, 0, 7, 0, 10, 12];
      if (s % 2 === 0) ev(out, 0, t, ch[0] - 12 + bl[(s / 2) % 8] + tr, sd * 1.7, 0.3, 'sawtooth');
      ev(out, 1, t, ch[(s + (s >> 2)) % n] + 12 + tr, sd * 0.9, 0.1, 'triangle');
      if (st % 8 === 0) for (var b = 0; b < 3; b++) ev(out, 2, t, ch[b] + 12 + tr, sd * 7.5, 0.045, 'triangle', 0.15, 0.8);
      if (mel[mi] >= 0) ev(out, 3, t, ch[mel[mi] % n] + 24 + tr, sd * 2, 0.055, 'square', 0.01, 0.15);
    }
  }
  function envAt(e, dt) {
    if (dt < 0) return 0;
    if (dt < e.atk) return e.vel * dt / e.atk;
    if (dt < e.dur) return e.vel * (1 - 0.35 * (dt - e.atk) / Math.max(0.001, e.dur - e.atk));
    var r = (dt - e.dur) / e.rel;
    return r >= 1 ? 0 : e.vel * 0.65 * (1 - r);
  }
  function modelLevels(t, partSec) {
    var lv = [0, 0, 0, 0], es = songEvents(Math.max(0, t - 4), t + 0.001, partSec);
    for (var i = 0; i < es.length; i++) { var e = es[i]; if (e.type === 'note') { var v = envAt(e, t - e.t); if (v > lv[e.ch]) lv[e.ch] = v; } }
    var k = [2.6, 6, 12, 10];
    for (var c = 0; c < 4; c++) lv[c] = Math.min(1, lv[c] * k[c]);
    return lv;
  }

  // ---- Web Audio graph: 4 channels, stereo pan per channel, pad lowpass + generated reverb, analysers for VU
  function buildGraph(ac) {
    var g = { ac: ac, ch: [], an: [] };
    var master = ac.createGain(); master.gain.value = 0.75;
    var comp = ac.createDynamicsCompressor ? ac.createDynamicsCompressor() : null;
    if (comp) { master.connect(comp); comp.connect(ac.destination); } else master.connect(ac.destination);
    var rev = ac.createConvolver(), len = Math.floor(ac.sampleRate * 3.6), ir = ac.createBuffer(2, len, ac.sampleRate);
    var seed = 7;
    for (var c = 0; c < 2; c++) {
      var d = ir.getChannelData(c);
      for (var i = 0; i < len; i++) { seed = (seed * 16807) % 2147483647; d[i] = ((seed / 2147483647) * 2 - 1) * Math.pow(1 - i / len, 2.6); }
    }
    rev.buffer = ir; var revOut = ac.createGain(); revOut.gain.value = 0.55; rev.connect(revOut); revOut.connect(master);
    g.send = [];
    for (var k = 0; k < 4; k++) {
      var bus = ac.createGain(), node = bus;
      if (k === 0) { var lp0 = ac.createBiquadFilter(); lp0.type = 'lowpass'; lp0.frequency.value = 900; bus.connect(lp0); node = lp0; }
      if (k === 2) { var lp = ac.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 1800; lp.Q.value = 0.7; bus.connect(lp); node = lp; g.padLp = lp; }
      var pan = ac.createStereoPanner ? ac.createStereoPanner() : null, out = node;
      if (pan) { pan.pan.value = PAN[k]; node.connect(pan); out = pan; }
      var an = ac.createAnalyser(); an.fftSize = 256; out.connect(an); an.connect(master);
      var snd = ac.createGain(); snd.gain.value = k === 2 ? 0.35 : (k === 3 ? 0.3 : 0.08); out.connect(snd); snd.connect(rev);
      g.ch.push(bus); g.an.push(an); g.send.push(snd);
    }
    return g;
  }
  function playEvents(g, es, offset) {
    var ac = g.ac;
    for (var i = 0; i < es.length; i++) {
      var e = es[i], t = e.t + offset;
      if (t < ac.currentTime - 0.01) continue;
      t = Math.max(t, ac.currentTime);
      if (e.type === 'part') {
        var abyss = e.p === 3;
        g.padLp.frequency.setTargetAtTime(abyss ? 480 : (e.p === 1 ? 1100 : 1800), t, 1.2);
        g.send[2].gain.setTargetAtTime(abyss ? 0.8 : 0.35, t, 1.2);
        continue;
      }
      var o = ac.createOscillator(), gn = ac.createGain();
      o.type = e.wave;
      o.frequency.setValueAtTime(e.f, t);
      if (e.arp) { for (var a = 0, at = t; at < t + e.dur; a++, at += 0.02) o.frequency.setValueAtTime(e.arp[a % e.arp.length], at); }
      if (e.ch === 2) o.detune.setValueAtTime((i % 2 ? 7 : -7), t);
      gn.gain.setValueAtTime(0, t);
      gn.gain.linearRampToValueAtTime(e.vel, t + e.atk);
      gn.gain.linearRampToValueAtTime(e.vel * 0.65, t + Math.max(e.atk + 0.005, e.dur));
      gn.gain.linearRampToValueAtTime(0, t + e.dur + e.rel);
      o.connect(gn); gn.connect(g.ch[e.ch]);
      o.start(t); o.stop(t + e.dur + e.rel + 0.05);
    }
  }
  /** Offline render (demo video): returns a Promise of a base64 16-bit stereo WAV. */
  function renderAudio(seconds, partSec, sr) {
    sr = sr || 48000;
    var OAC = G.OfflineAudioContext || G.webkitOfflineAudioContext;
    var ac = new OAC(2, Math.floor(sr * seconds), sr), g = buildGraph(ac);
    playEvents(g, songEvents(0, seconds, partSec), 0);
    return ac.startRendering().then(function (buf) {
      var n = buf.length, L = buf.getChannelData(0), R = buf.getChannelData(1), ab = new ArrayBuffer(44 + n * 4), v = new DataView(ab);
      function s(o, str) { for (var i = 0; i < str.length; i++) v.setUint8(o + i, str.charCodeAt(i)); }
      s(0, 'RIFF'); v.setUint32(4, 36 + n * 4, true); s(8, 'WAVEfmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 2, true);
      v.setUint32(24, sr, true); v.setUint32(28, sr * 4, true); v.setUint16(32, 4, true); v.setUint16(34, 16, true); s(36, 'data'); v.setUint32(40, n * 4, true);
      for (var i = 0, o = 44; i < n; i++, o += 4) { v.setInt16(o, Math.max(-1, Math.min(1, L[i])) * 32767, true); v.setInt16(o + 2, Math.max(-1, Math.min(1, R[i])) * 32767, true); }
      var bytes = new Uint8Array(ab), bin = '';
      for (var j = 0; j < bytes.length; j += 32768) bin += String.fromCharCode.apply(null, bytes.subarray(j, j + 32768));
      return btoa(bin);
    });
  }

  // ---- original sprites (all drawn by me; head faces right, mirrored at runtime)
  var MIR = { '<': '>', '>': '<', '(': ')', ')': '(', '[': ']', ']': '[', '{': '}', '}': '{', '/': '\\', '\\': '/', '◖': '◗', '◗': '◖', '▸': '◂', '◂': '▸', '▶': '◀', '◀': '▶', '◢': '◣', '◣': '◢', '◥': '◤', '◤': '◥', '▌': '▐', '▐': '▌', '╱': '╲', '╲': '╱', '◁': '▷', '▷': '◁', '‹': '›', '›': '‹' };
  function mirror(lines) {
    var w = 0, out = [];
    for (var i = 0; i < lines.length; i++) w = Math.max(w, lines[i].length);
    for (var j = 0; j < lines.length; j++) {
      var l = lines[j]; while (l.length < w) l += ' ';
      var r = '';
      for (var k = l.length - 1; k >= 0; k--) { var c = l.charAt(k); r += MIR[c] || c; }
      out.push(r);
    }
    return out;
  }
  var SPECIES = [
    { id: 'sardine', art: ['≡≡▸'], col: 'fg', spd: 7.5, school: true },
    { id: 'neon', art: ['~=◉>'], col: 'cyan', spd: 6.5, school: true },
    { id: 'butterfly', art: [' ◥◣ ', '▐█◉▶', ' ◢◤ '], col: 'yellow', spd: 4.2 },
    { id: 'parrot', art: ['╱▀▀▀▀╲ ', '▐ ≈≈ ◉▶', '╲▄▄▄▄╱ '], col: 'green', spd: 3.4 },
    { id: 'puffer', art: [' .ooo.  ', '{ ◉   ●>', " 'ooo'  "], col: 'accent2', spd: 2.6 },
    { id: 'angel', art: ['  ▲   ', '◁◖ ● )', '  ▼   '], col: 'magenta', spd: 3.0 },
    { id: 'eel', art: ['∽∿∽∿∽∿◖◉>'], col: 'accent', spd: 3.8 },
    { id: 'lantern', art: ['·◖◉>'], col: 'yellow', spd: 2.4, glow: true }
  ];
  var POOLS = [[0, 1, 2, 3, 4, 5], [0, 1, 3, 5, 6], [0, 1, 2, 3, 4, 5, 6], [6, 7, 1], [1, 7]];
  var SHARK = ['             ▄█▀             ', '  ▄▄▄▄▄▄▄▄▄▄██████▄▄▄▄▄▄▄▄▄▄  ', '◀█▀▀▀▀▀▀▀██████████████◉ ▀▀▄ ', '  ▀▀▀▀▀▀▀▀▀▀▀▄█▀▀▀▀▀▀ ▼▼▼▼▀▀  ', '               ▀             '];
  var WHALE = ['       ▄▄▄▄▄▄▄▄▄▄▄▄▄          ', '   ▄██████████████████▄▄  ▄█▀', ' ▐█◉██████████████████████▀  ', '  ▀▀▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▀▀▀       '];
  var SPOUT = [['   .  .  ', '    ::   ', '    ¦    '], [' ·  ¦  · ', '  ·:¦:·  ', '    ¦    '], ['·   :   ·', '  ` ¦ `  ', '    ¦    ']];
  var SHIP = ['       │╲        ', '       │▓╲   ⚑   ', '       │▓▓╲  │   ', '  ▄▄▄▄▄█▄▄▄▄█▄▄  ', '  ╲  OMARCHY  ╱  '];
  var DUCK = [' ◖°>', '◥██◤'];
  var MANTA = ['    ▁▂▄▆█▆▄▂▁    ', '◥◣▄▄  ◉   ◉  ▄▄◢◤', '       ╹╹╹       '];
  var JELLY = [['   .─▀▀▀─.   ', '  (  ● ●  )  ', '   `─┬─┬─\'   ', '    / │ \\    ', '   ~  ~  ~   '], ['  .──▀▀▀──.  ', ' (   ● ●   ) ', '  `──┬─┬──\'  ', '    │ │ │    ', '    ~ ~ ~    ']];
  var ANGLER = ['     .---.               ', '    /     \\  ___         ', '   *       `/   \\___/|   ', '          /  ●  /    |   ', '         <  ▲▲▲      |   ', '          \\_____\\___\\|   '];
  var LEVIATHAN = ['       .---.   .·°ºO                                 ', '   ___/     \\___                                     ', '  /   \'-----\'   \\____________________________________', ' /  ●   ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄       /  \\', '|      █████████████████████████████████████=====(    |', ' \\      ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀       \\__/', '  \\___,-----,___/¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯¯'];
  var LOGO1 = '█▀█ █▀▄▀█ ▄▀█ █▀█ █▀▀ █ █ █ █', LOGO2 = '█▄█ █ ▀ █ █▀█ █▀▄ █▄▄ █▀█  █ ';
  function rep(c, n) { var s = ''; while (n-- > 0) s += c; return s; }
  var CASTLE = (function () {
    var W = LOGO1.length + 6;
    var tw = '▐█▌', mid = Math.floor(W / 2);
    function put(line, x, s) { return line.substring(0, x) + s + line.substring(x + s.length); }
    var r0 = put(put(put(rep(' ', W), 2, ' ▲ '), W - 5, ' ▲ '), mid - 1, ' ⚑ ');
    var r1 = put(put(put(rep(' ', W), 2, tw), W - 5, tw), mid - 1, tw);
    var r2 = put(put(rep('▄', W), 2, tw), W - 5, tw); r2 = '▐' + r2.substring(1, W - 1) + '▌';
    var crenel = ''; for (var i = 0; i < W; i++) crenel += (i % 3 === 0) ? '▄' : ' ';
    var gap = '█' + rep(' ', W - 2) + '█';
    return [r0, r1, crenel, r2, gap, '█  ' + LOGO1 + '  █', '█  ' + LOGO2 + '  █', gap, '█' + rep('▄', mid - 3) + '█▀▀▀▀█' + rep('▄', W - mid - 5) + '█'];
  })();
  var GREETZ = '      OMARCHY TV  //  THE AQUARIUM  //  AN ASCIIQUARIUM X SECOND REALITY HOMAGE BY MARCO PORRO  //  CODE, PIXELS AND TRACKER TUNE: ALL ORIGINAL  //  GREETZ TO FUTURE CREW (SECOND REALITY, ASSEMBLY 1993), KIRK BAUCOM (ASCIIQUARIUM), DHH AND THE OMARCHY CREW, @ERIKRJOHANSSON, @TCBALLARD AND EVERY SCENER WHO EVER STAYED UP ALL NIGHT  //  PRESS M TO MUTE  //  ';
  var SMALL_SCROLL = '   ★ OMARCHY TV · AQUARIUM · ASCIIQUARIUM x SECOND REALITY 1993//2026 · CODE & PIXELS & MUSIC BY MARCO PORRO · GREETZ TO FUTURE CREW & KIRK BAUCOM & DHH ★   ';

  function mulberry(a) { return function () { a |= 0; a = a + 0x6D2B79F5 | 0; var t = Math.imul ? Math.imul(a ^ a >>> 15, 1 | a) : (a ^ a >>> 15) * (1 | a); t = t + (Math.imul ? Math.imul(t ^ t >>> 7, 61 | t) : (t ^ t >>> 7) * (61 | t)) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
  function hex(c) { c = String(c || '#000').replace('#', ''); if (c.length === 3) c = c[0] + c[0] + c[1] + c[1] + c[2] + c[2]; var n = parseInt(c.substring(0, 6), 16) || 0; return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  function mix(a, b, t) { var x = hex(a), y = hex(b); function h(v) { v = Math.round(v); return (v < 16 ? '0' : '') + v.toString(16); } return '#' + h(x[0] + (y[0] - x[0]) * t) + h(x[1] + (y[1] - x[1]) * t) + h(x[2] + (y[2] - x[2]) * t); }
  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
  function ease(x) { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); }
  function tri(x) { x = x - Math.floor(x); return x < 0.5 ? x * 2 : 2 - x * 2; }

  /** Depth in metres as a continuous function of song time (header + ruler both read this). */
  function depthAt(t, partSec) {
    var k = Math.floor(t / partSec), p = k % 5, fr = (t - k * partSec) / partSec;
    if (p === 0) return 6 + Math.sin(t * 0.4) * 1.5;
    if (p === 1) return 6 + ease(fr) * 214;
    if (p === 2) return 220 + fr * 180;
    if (p === 3) return 400 + ease(fr / 0.25) * 560 + fr * 40;
    return 1000;
  }

  function run(cv, S, opts) {
    opts = opts || {};
    var ctx = cv.getContext('2d');
    var partSec = (S && S.partSeconds && S.partSeconds()) || opts.partSec || 75;
    var startMs = Date.now(), rnd = mulberry(1993), glyphs = {}, glyphN = 0, chunky = {};
    var soundOn = opts.soundOn !== false, actx = null, graph = null, songOff = 0, schedTo = 0, timer = null, hudBoost = 0;
    var cw = 17, ch = 30, fs = 26, W = 1920, H = 1080, lastDraw = 0, lastSec = 0;
    var lensBuf = document.createElement('canvas'), lctx = lensBuf.getContext('2d');

    function pal() {
      var p = (S && S.palette && S.palette()) || {};
      return { name: p.name || 'tokyo-night', bg: p.bg || '#1a1b26', fg: p.fg || '#a9b1d6', fgBright: p.fgBright || '#c0caf5', fgDim: p.fgDim || '#565f89',
        accent: p.accent || '#7aa2f7', accent2: p.accent2 || '#bb9af7', cyan: p.cyan || '#7dcfff', green: p.green || '#9ece6a', magenta: p.magenta || '#bb9af7', yellow: p.yellow || '#e0af68', red: p.red || '#f7768e' };
    }
    var T = pal(), palT = 0;

    // ---- audio
    function startAudio() {
      if (timer) return;
      try {
        var AC = G.AudioContext || G.webkitAudioContext; if (!AC) return;
        if (!actx) { actx = new AC(); graph = buildGraph(actx); }
        if (actx.state === 'suspended' && actx.resume) actx.resume();
      } catch (x) { return; }
      songOff = actx.currentTime - songSec(); schedTo = songSec();
      timer = setInterval(function () {
        if (!soundOn || !actx) return;
        var now = actx.currentTime - songOff, to = now + 0.3;
        if (schedTo < now) schedTo = now;
        if (to > schedTo) { try { playEvents(graph, songEvents(schedTo, to, partSec), songOff); } catch (x) { } schedTo = to; }
      }, 70);
    }
    function stopAudio() { if (timer) { clearInterval(timer); timer = null; } if (actx && actx.suspend) try { actx.suspend(); } catch (x) { } }
    function liveLevels() {
      if (!graph || !timer || !actx || actx.state !== 'running') return null;
      var lv = [], buf = new Uint8Array(128);
      for (var c = 0; c < 4; c++) { graph.an[c].getByteTimeDomainData(buf); var s = 0; for (var i = 0; i < 128; i++) { var d = (buf[i] - 128) / 128; s += d * d; } lv.push(Math.min(1, Math.sqrt(s / 128) * 9)); }
      return lv;
    }
    var api = {
      toggleSound: function () { soundOn = !soundOn; hudBoost = 6; if (soundOn) { if (actx && actx.resume) actx.resume(); timer = null; startAudio(); } else stopAudio(); return soundOn; },
      isSoundOn: function () { return soundOn; }
    };
    function songSec() { return (Date.now() - startMs) / 1000; }
    if (soundOn && !opts.noAudio) startAudio();

    // ---- glyph atlas: each (char, colour, scale) is rasterised once into a small canvas, then blitted on the grid
    function glyph(c, col, sc) {
      var key = c + '|' + col + '|' + sc, g = glyphs[key];
      if (g) return g;
      if (glyphN > 3000) { glyphs = {}; glyphN = 0; }
      g = document.createElement('canvas');
      g.width = Math.ceil(cw * sc * 1.25) + 2; g.height = Math.ceil(ch * sc * 1.25);
      var x = g.getContext('2d');
      x.font = 'bold ' + Math.round(fs * sc) + 'px mono, monospace'; x.fillStyle = col; x.textBaseline = 'alphabetic';
      x.fillText(c, 1, Math.round(ch * sc * 0.92));
      glyphs[key] = g; glyphN++;
      return g;
    }
    function txt(s, col, row, color, alpha, sc, clipR) {
      sc = sc || 1;
      if (alpha !== undefined && alpha !== 1) ctx.globalAlpha = clamp(alpha, 0, 1);
      var y = (row - 0.12 * sc) * ch, maxC = clipR === undefined ? COLS + 4 : clipR;
      for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i); if (c === ' ') continue;
        var cx = col + i * sc; if (cx < -2 || cx > maxC - sc) continue;
        ctx.drawImage(glyph(c, color, sc), cx * cw - 1, y);
      }
      ctx.globalAlpha = 1;
    }
    function sprite(lines, col, row, color, hi, alpha, sc, clipR) {
      sc = sc || 1;
      for (var r = 0; r < lines.length; r++) {
        var l = lines[r];
        txt(l, col, row + r * sc, (hi && (l.indexOf('◉') >= 0 || l.indexOf('●') >= 0 || l.indexOf('*') >= 0)) ? hi : color, alpha, sc, clipR);
      }
    }
    // VGA 8x16 style chunky letters: rasterise each glyph of the bundled Caskaydia Mono (OFL) at 8x16 and draw its pixels as blocks
    function chunk(c) {
      if (chunky[c]) return chunky[c];
      var k = document.createElement('canvas'); k.width = 8; k.height = 16;
      var x = k.getContext('2d'); x.fillStyle = '#fff'; x.font = 'bold 14px mono, monospace'; x.textBaseline = 'alphabetic'; x.fillText(c, 0, 13);
      var d = x.getImageData(0, 0, 8, 16).data, bits = [];
      for (var i = 0; i < 128; i++) bits.push(d[i * 4 + 3] > 100 ? 1 : 0);
      chunky[c] = bits; return bits;
    }

    // ---- world state
    var fish = [], bubbles = [], bursts = [];
    var big = {
      shark: { x: -40, y: 15, w: 30, h: 5, dir: 1, yo: 0 },
      manta: { x: 120, y: 10, w: 17, h: 3, dir: -1, yo: 0 },
      lens: { x: 40, y: 18, w: 12, h: 10, yo: 0 },
      angler: { x: 110, y: 24, w: 25, h: 6, dir: -1, yo: 0 },
      jelly: [{ x: 14, y: 12, w: 13, h: 5, yo: 0 }, { x: 84, y: 20, w: 13, h: 5, yo: 0 }]
    };
    function spawnFish(part, fromEdge) {
      var pool = POOLS[part], sp = SPECIES[pool[Math.floor(rnd() * pool.length)]];
      var dir = rnd() < 0.5 ? 1 : -1, z = rnd() < 0.35 ? 0 : (rnd() < 0.6 ? 1 : 2);
      var lane = 7 + rnd() * 22, n = sp.school ? 3 + Math.floor(rnd() * 4) : 1;
      if (part === 0) lane = 19 + rnd() * 11;
      var baseX = fromEdge ? (dir > 0 ? -8 - rnd() * 20 : RULER_COL + 4 + rnd() * 20) : rnd() * 95;
      var spd = sp.spd * (z === 0 ? 0.5 : (z === 1 ? 0.8 : 1)) * (0.8 + rnd() * 0.4);
      for (var i = 0; i < n; i++) {
        fish.push({ sp: sp, art: dir > 0 ? sp.art : mirror(sp.art), dir: dir, z: z, x: baseX - dir * (i * (2.5 + rnd() * 4)), y: lane + (rnd() - 0.5) * 3, spd: spd * (0.88 + rnd() * 0.24),
          wa: 0.25 + rnd() * 0.6, wf: 0.6 + rnd() * 1.4, ph: rnd() * 6.28, bub: 2 + rnd() * 6, eaten: 0 });
      }
    }
    function fishTarget(part) { return [14, 16, 22, 9, 6][part]; }

    function bg(depth) { return mix(T.bg, '#000000', clamp(depth / 1000, 0, 1) * 0.8); }

    function drawCopper(y0, y1, n, sec, amp, alpha) {
      var cols = [T.accent, T.magenta, T.cyan, T.yellow, T.green, T.red, T.accent2];
      for (var i = 0; i < n; i++) {
        var cy = y0 + (y1 - y0) * (0.5 + 0.5 * Math.sin(sec * 1.3 + i * 0.62)) , hh = Math.max(4, ch * 0.42) * amp;
        var gr = ctx.createLinearGradient(0, cy - hh, 0, cy + hh), c = cols[i % cols.length];
        gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(0.5, c); gr.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.globalAlpha = alpha; ctx.fillStyle = gr; ctx.fillRect(0, cy - hh, (COLS + 2) * cw, hh * 2);
      }
      ctx.globalAlpha = 1;
    }

    // glenz vector: octahedron, each face split into 4 triangles in two alternating colours, additive translucent faces
    var GV = [[0, 1.3, 0], [0, -1.3, 0], [1.3, 0, 0], [-1.3, 0, 0], [0, 0, 1.3], [0, 0, -1.3]];
    var GF = [[0, 2, 4], [0, 4, 3], [0, 3, 5], [0, 5, 2], [1, 4, 2], [1, 3, 4], [1, 5, 3], [1, 2, 5]];
    function glenz(cxg, cyg, rad, sec, alpha) {
      var ax = sec * 0.9, ay = sec * 1.37, az = sec * 0.41, P = [];
      function rot(v) {
        var x = v[0], y = v[1], z = v[2], t;
        t = x * Math.cos(ay) - z * Math.sin(ay); z = x * Math.sin(ay) + z * Math.cos(ay); x = t;
        t = y * Math.cos(ax) - z * Math.sin(ax); z = y * Math.sin(ax) + z * Math.cos(ax); y = t;
        t = x * Math.cos(az) - y * Math.sin(az); y = x * Math.sin(az) + y * Math.cos(az); x = t;
        return [x, y, z];
      }
      var bounce = Math.abs(Math.sin(sec * 1.6)), sq = 1 - 0.12 * Math.pow(1 - bounce, 6);
      var cxp = cxg * cw, cyp = (cyg - bounce * 2.2) * ch, R = rad * ch;
      var tris = [];
      for (var f = 0; f < GF.length; f++) {
        var a = GV[GF[f][0]], b = GV[GF[f][1]], c = GV[GF[f][2]];
        var ab = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2], bc = [(b[0] + c[0]) / 2, (b[1] + c[1]) / 2, (b[2] + c[2]) / 2], ca = [(c[0] + a[0]) / 2, (c[1] + a[1]) / 2, (c[2] + a[2]) / 2];
        var sub = [[a, ab, ca, 0], [ab, b, bc, 0], [ca, bc, c, 0], [ab, bc, ca, 1]];
        for (var s = 0; s < 4; s++) tris.push([rot(sub[s][0]), rot(sub[s][1]), rot(sub[s][2]), (sub[s][3] + f) % 2]);
      }
      for (var i = 0; i < tris.length; i++) { var q = tris[i]; q.z = (q[0][2] + q[1][2] + q[2][2]) / 3; var ux = q[1][0] - q[0][0], uy = q[1][1] - q[0][1], vx = q[2][0] - q[0][0], vy = q[2][1] - q[0][1]; q.front = (ux * vy - uy * vx) < 0; }
      tris.sort(function (m, n) { return n.z - m.z; });
      function pj(v) { var k = 3.4 / (3.4 + v[2]); return [cxp + v[0] * R * k, cyp + v[1] * R * k * sq]; }
      // shadow on the "floor"
      ctx.globalAlpha = 0.18 * alpha * (0.5 + 0.5 * bounce); ctx.fillStyle = '#000';
      ctx.beginPath(); if (ctx.ellipse) ctx.ellipse(cxp, (cyg + rad * 1.2) * ch, R * 0.9, R * 0.16, 0, 0, 6.283); else ctx.rect(cxp - R * 0.9, (cyg + rad * 1.2) * ch - R * 0.1, R * 1.8, R * 0.2); ctx.fill();
      var prev = ctx.globalCompositeOperation; ctx.globalCompositeOperation = 'lighter';
      for (var j = 0; j < tris.length; j++) {
        var tr = tris[j], p0 = pj(tr[0]), p1 = pj(tr[1]), p2 = pj(tr[2]);
        ctx.globalAlpha = alpha * (tr.front ? 0.42 : 0.2);
        ctx.fillStyle = tr[3] ? T.cyan : T.magenta;
        ctx.beginPath(); ctx.moveTo(p0[0], p0[1]); ctx.lineTo(p1[0], p1[1]); ctx.lineTo(p2[0], p2[1]); ctx.closePath(); ctx.fill();
        if (tr.front) { ctx.globalAlpha = alpha * 0.35; ctx.strokeStyle = T.fgBright; ctx.lineWidth = 1.2; ctx.stroke(); }
      }
      ctx.globalCompositeOperation = prev; ctx.globalAlpha = 1;
    }

    function drawScene(sec, dt, part, fr, depth, fade) {
      var baseRow = 18, rowOf = function (m) { return baseRow + (m - depth) / 10; };
      var water = rowOf(0) - 0.6; // surface line (only on screen near the top)
      // sky + copper sunset above the water line
      if (water > 4) {
        var sky = ctx.createLinearGradient(0, 4 * ch, 0, water * ch);
        sky.addColorStop(0, mix(T.bg, '#000', 0.5)); sky.addColorStop(1, mix(T.bg, T.accent2, 0.25));
        ctx.fillStyle = sky; ctx.fillRect(0, 5.3 * ch, W, (water - 5.3) * ch);
        ctx.save(); ctx.beginPath(); ctx.rect(0, 5.3 * ch, W, (water - 5.3) * ch); ctx.clip();
        drawCopper(6.5 * ch, (water - 1.5) * ch, 6, sec * 0.7, 1.2, 0.55);
        ctx.restore();
        // ship sailing on the line, whale spouting, ducks bobbing
        var shipX = ((sec * 1.6) % (COLS + 40)) - 20, roll = Math.sin(sec * 1.2) * 0.25;
        sprite(SHIP, shipX, water - 4.3 + roll, T.fgBright, null, 1, 1, RULER_COL);
        var wx = RULER_COL - ((sec * 1.1 + 30) % (COLS + 40)), wy = water + 1.6 - Math.max(0, Math.sin(sec * 0.35)) * 0.9; // swims just under the line, spout breaks the surface
        var wSpr = WHALE;
        sprite(wSpr, wx, wy, T.accent, T.fgBright, 1, 1, RULER_COL);
        if (Math.sin(sec * 0.35) > 0.2) sprite(SPOUT[Math.floor(sec * 4) % 3], wx + 1, water - 3, T.cyan, null, 0.9, 1, RULER_COL);
        for (var d = 0; d < 3; d++) {
          var dx = ((shipX + 52 + d * 6) % (RULER_COL + 10)) - 4 + Math.sin(sec * 0.3 + d), dy = water - 1.3 + Math.sin(sec * 2.1 + d) * 0.18;
          sprite(DUCK, dx, dy, T.yellow, null, 1, 1, RULER_COL);
        }
      }
      if (water > 4 && water < 40) {
        var line = '';
        for (var c = 0; c < COLS; c++) { var v = Math.sin(c * 0.35 - sec * 2.2) + Math.sin(c * 0.13 + sec * 1.3); line += v > 1.1 ? '^' : (v > -0.6 ? '~' : '-'); }
        txt(line, 0, water, T.cyan, 1, 1, RULER_COL);
        var line2 = '';
        for (var c2 = 0; c2 < COLS; c2++) line2 += Math.sin(c2 * 0.21 + sec * 1.7) > 0.3 ? '~' : ' ';
        txt(line2, 0, water + 1, T.accent, 0.45, 1, RULER_COL);
      }
      // seabed (mid waters onwards slides up from below)
      var seabedVis = part === 2 ? ease(fr * 6) : (part === 3 ? 1 : (part === 4 ? 1 - ease(fr * 5) : 0));
      var bedRow = 31.2 + (1 - seabedVis) * 8;
      // far creatures first (parallax: dim, small, slow)
      for (var zf = 0; zf < 3; zf++) drawFishLayer(zf, sec, part);
      // bubbles
      for (var b = 0; b < bubbles.length; b++) { var bu = bubbles[b], age = bu.age; txt(age < 1.2 ? '.' : (age < 2.6 ? 'o' : 'O'), bu.x + Math.sin(bu.age * 3 + bu.ph) * 0.4, bu.y, T.cyan, 0.75 * (bu.z ? 1 : 0.5), bu.z ? 1 : 0.75, RULER_COL); }
      for (var u = 0; u < bursts.length; u++) { var br = bursts[u]; txt(br.age < 0.4 ? '*CHOMP*' : '* · °', br.x, br.y, T.red, 1 - br.age, 1, RULER_COL); }

      // part specific big entities
      if (part === 2 || (part === 1 && fr > 0.5)) {
        var mA = part === 1 ? ease((fr - 0.5) * 3) : 1;
        var m = big.manta; sprite(m.dir > 0 ? mirror(MANTA) : MANTA, m.x, m.y + m.yo, T.accent2, T.fgBright, mA, 1, RULER_COL);
      }
      if (part === 2) {
        var jf = Math.floor(sec * 2.6) % 2;
        for (var jj = 0; jj < 2; jj++) { var J = big.jelly[jj]; sprite(JELLY[jf], J.x, J.y + J.yo, jj ? T.cyan : T.magenta, T.accent, part === 4 ? 0.6 : 1, 1, RULER_COL); }
      }
      if (part === 2 || (part === 3 && !leviathanWindow(fr))) {
        var sk = big.shark; sprite(sk.dir > 0 ? SHARK : mirror(SHARK), sk.x, sk.y + sk.yo, T.accent, T.red, 1, 1, RULER_COL);
      }
      if (part === 3) {
        var an = big.angler, lit = 0.6 + 0.4 * Math.sin(sec * 3);
        sprite(an.dir < 0 ? ANGLER : mirror(ANGLER), an.x, an.y + an.yo, mix(T.magenta, '#000', 0.25), T.yellow, 1, 1, RULER_COL);
        if (an.dir < 0) txt('*', an.x + 3, an.y + an.yo + 2, T.yellow, lit, 1, RULER_COL);
        if (leviathanWindow(fr)) {
          var lf = (fr - 0.45) / 0.35, lx = RULER_COL - lf * (COLS + 60) + 4;
          sprite(LEVIATHAN, lx, 7.2, mix(T.accent2, '#000', 0.2), T.cyan, 0.85, 1, RULER_COL);
          txt('[ leviathan · sonar-lock ]', lx + 10, 6.2, T.green, 0.8, 1, RULER_COL);
        }
      }
      // seabed: kelp, castle, crab
      if (seabedVis > 0) {
        ctx.globalAlpha = 0.25 * seabedVis; ctx.fillStyle = T.accent;
        var hy = bedRow * ch; ctx.fillRect(0, hy, RULER_COL * cw, 1.5);
        ctx.globalAlpha = 1;
        var sand = ''; for (var sc2 = 0; sc2 < RULER_COL; sc2++) sand += ((sc2 * 7 + 3) % 11 < 3) ? '░' : (((sc2 * 5) % 13 < 2) ? '▒' : '·');
        txt(sand, 0, bedRow + 2.5, mix(T.yellow, '#000', 0.55), seabedVis, 1, RULER_COL);
        var kelp = [4, 9, 21, 27, 78, 83, 95, 99];
        for (var k = 0; k < kelp.length; k++) for (var sg = 0; sg < 5 + k % 3; sg++)
          txt(((Math.floor(sec * 2) + sg + k) % 2) ? '(' : ')', kelp[k] + Math.sin(sec * 1.6 + sg * 0.55 + k) * 0.6, bedRow + 1.6 - sg * 0.9, k % 2 ? T.green : T.cyan, seabedVis * 0.9, 1, RULER_COL);
        // Omarchy castle: slowly changes position along the seabed (anti burn-in easter egg)
        var cx = 33 + Math.sin(sec / 97) * 26 + Math.sin(sec / 41) * 4;
        sprite(CASTLE, cx, bedRow - 6.6, T.accent, null, seabedVis, 1, RULER_COL);
        var crabX = ((sec * 1.3) % (RULER_COL + 30)) - 15;
        txt((Math.floor(sec * 5) % 2) ? '(\\/)(°,,°)(\\/)' : '( /)(°,,°)(\\ )', crabX, bedRow + 1.5, T.red, seabedVis, 1, RULER_COL);
      }
      if (part === 3 || part === 4) {
        var ga = part === 3 ? ease(fr * 8) : 1;
        if (part === 3) glenz(52, 18.5, 5.5, sec, ga); else glenz(52, 10.5, 3.6, sec, 0.9);
      }
    }
    function drawFishLayer(z, sec, part) {
      var sc = [0.72, 0.86, 1][z], dim = [0.6, 0.3, 0][z];
      for (var i = 0; i < fish.length; i++) {
        var f = fish[i]; if (f.z !== z || f.eaten > 0) continue;
        var col = T[f.sp.col] || T.fg; if (dim) col = mix(col, bg(lastDepth), dim);
        sprite(f.art, f.x, f.y + Math.sin(sec * f.wf + f.ph) * f.wa, col, z === 2 ? T.fgBright : null, 1, sc, RULER_COL);
      }
    }
    var lastDepth = 0;

    function simulate(dt, sec, part, fr) {
      var target = fishTarget(part), alive = 0;
      for (var i = fish.length - 1; i >= 0; i--) {
        var f = fish[i];
        if (f.eaten > 0) { f.eaten -= dt; if (f.eaten <= 0) fish.splice(i, 1); continue; }
        f.x += f.dir * f.spd * dt;
        if ((f.dir > 0 && f.x > RULER_COL + 6) || (f.dir < 0 && f.x < -14)) { fish.splice(i, 1); continue; }
        if (part === 0 && f.y < 19) f.y += dt * 2; // stay under the surface
        f.bub -= dt;
        if (f.bub <= 0) { f.bub = 3 + rnd() * 7; bubbles.push({ x: f.x + (f.dir > 0 ? f.art[0].length * 0.9 : 0), y: f.y, age: 0, ph: rnd() * 6, z: f.z > 0, vy: 1.6 + rnd() }); }
        alive++;
      }
      while (alive < target) { spawnFish(part, true); alive += 2; }
      var top = part === 0 ? 19.5 : 5.6;
      for (var b = bubbles.length - 1; b >= 0; b--) { var bu = bubbles[b]; bu.age += dt; bu.y -= bu.vy * dt; if (bu.y < top || bu.age > 9) bubbles.splice(b, 1); }
      if (bubbles.length > 120) bubbles.splice(0, bubbles.length - 120);
      for (var u = bursts.length - 1; u >= 0; u--) { bursts[u].age += dt; if (bursts[u].age > 1) bursts.splice(u, 1); }
      // big entities
      var sk = big.shark; sk.x += sk.dir * 9 * dt; if (sk.x > RULER_COL + 10) { sk.dir = -1; sk.y = part === 3 ? 6 : 10 + rnd() * 12; } if (sk.x < -40) { sk.dir = 1; sk.y = part === 3 ? 6 : 10 + rnd() * 12; }
      if (part === 2 || (part === 3 && !leviathanWindow(fr))) {
        var mouth = sk.dir > 0 ? sk.x + 27 : sk.x + 1, my = sk.y + sk.yo + 2;
        for (var e = 0; e < fish.length; e++) {
          var g = fish[e];
          if (g.eaten > 0 || g.z === 0) continue;
          if (Math.abs(g.x - mouth) < 2.2 && Math.abs(g.y - my) < 1.6) { g.eaten = 0.01; bursts.push({ x: g.x, y: g.y - 0.5, age: 0 }); for (var q = 0; q < 4; q++) bubbles.push({ x: g.x + rnd() * 2, y: g.y, age: rnd(), ph: rnd() * 6, z: true, vy: 2 + rnd() }); }
        }
      }
      var m = big.manta; m.x += m.dir * 2.6 * dt; if (m.x < -20) { m.dir = 1; m.y = 8 + rnd() * 6; } if (m.x > RULER_COL + 4) { m.dir = -1; m.y = 8 + rnd() * 6; }
      var an = big.angler; an.x += an.dir * 2.2 * dt; if (an.x < -26) { an.dir = 1; } if (an.x > RULER_COL + 2) { an.dir = -1; }
      for (var j = 0; j < 2; j++) { var J = big.jelly[j]; J.x = (j ? 82 : 12) + Math.sin(sec * 0.21 + j * 2) * 8; J.y = (j ? 19 : 11) + Math.sin(sec * 0.6 + j) * 2.5; }
      // lens bounces inside the content area
      var L = big.lens; L.x = 8 + tri(sec / 23) * (RULER_COL - 22); L.y = 8 + Math.abs(Math.sin(sec * 0.55)) * 0 + tri(sec / 13.7) * 18;
      // minimum repulsion between the big entities that share the screen
      var act = [];
      if (part === 2 || (part === 3 && !leviathanWindow(fr))) act.push(sk);
      if (part === 2) { act.push(m, L, big.jelly[0], big.jelly[1]); }
      if (part === 3) { act.push(an); act.push({ x: 40, y: 10.5, w: 24, h: 14, yo: 0, fixed: true }); }
      for (var a1 = 0; a1 < act.length; a1++) {
        var A = act[a1], push = 0;
        if (A.fixed || A === L) continue;
        for (var a2 = 0; a2 < act.length; a2++) {
          var B = act[a2]; if (A === B) continue;
          var ox = Math.min(A.x + A.w, B.x + B.w) - Math.max(A.x, B.x) + 2;
          var ay = A.y + A.yo + A.h / 2, by = B.y + (B.yo || 0) + B.h / 2, need = (A.h + B.h) / 2 + 1;
          if (ox > 0 && Math.abs(ay - by) < need) push += (ay <= by ? -1 : 1) * (need - Math.abs(ay - by));
        }
        if (push) A.yo += clamp(push, -1, 1) * 6 * dt; else A.yo *= Math.max(0, 1 - dt * 0.4);
        var lo = 6 - A.y, hi = 30 - A.h - A.y; A.yo = clamp(A.yo, lo, hi);
      }
    }

    function hud(sec, part, depth, levels, hudA) {
      // anti burn-in: header + ticker fade out after a few seconds and come back cyclically
      var ov = (S && S.statusOverride && S.statusOverride()) || '';
      var name = PARTS[part].name;
      var head = '[ omarchy://abyss-node · part ' + (part + 1) + '/5 ' + name + ' · depth ' + Math.round(depth) + 'm · bio-sonar: ONLINE ' + (ov ? '· ' + ov + ' ' : '') + ']';
      var a = ov ? Math.max(hudA, 0.85) : hudA;
      if (a > 0.01) {
        ctx.save(); ctx.beginPath(); ctx.rect(0, 0, 66 * cw, 1.2 * ch); ctx.clip();
        txt(head, 1.5, 0.15, T.fgBright, a, 1, 66); ctx.restore();
        var vg = '▁▂▃▄▅▆▇█', vu = 'ST3 ';
        for (var c = 0; c < 4; c++) { var l = soundOn ? levels[c] : 0; vu += 'C' + (c + 1) + ':' + vg.charAt(clamp(Math.round(l * 7), 0, 7)) + ' '; }
        vu += '· ' + PARTS[part].bpm + ' BPM · ♪ ' + (soundOn ? 'ON' : 'MUTED') + ' (M)';
        ctx.save(); ctx.beginPath(); ctx.rect(67 * cw, 0, (COLS - 67) * cw, 1.2 * ch); ctx.clip();
        txt(vu, 68, 0.15, soundOn ? T.green : T.fgDim, a, 1, COLS); ctx.restore();
        // ticker: second row, own clipped zone
        var tick = '   theme: ' + T.name + ' · omarchy-link palette sync · glenz vector ready · lens online · ' + (opts.tickerExtra || 'asciiquarium x second reality') + '   ';
        var off = (sec * 6) % tick.length, s2 = tick + tick;
        ctx.save(); ctx.beginPath(); ctx.rect(1.5 * cw, 1.15 * ch, (RULER_COL - 2) * cw, 1.0 * ch); ctx.clip();
        txt(s2.substring(Math.floor(off), Math.floor(off) + RULER_COL + 2), 1.5 - (off % 1), 1.2, T.fgDim, a * 0.9, 1, RULER_COL);
        ctx.restore();
      }
      // depth ruler in its own right-hand band: labels follow the current depth (same value as the header)
      var rowOf = function (mm) { return 18 + (mm - depth) / 10; };
      var first = Math.floor((depth - 140) / 50) * 50;
      ctx.save(); ctx.beginPath(); ctx.rect((RULER_COL + 0.5) * cw, 5.4 * ch, (COLS - RULER_COL) * cw, 28 * ch); ctx.clip();
      for (var mm = first; mm < depth + 160; mm += 50) { if (mm < 0 || Math.abs(rowOf(mm) - 0.5 - 17.5) < 1.1) continue; txt('─ ' + mm + 'm', RULER_COL + 1, rowOf(mm) - 0.5, mm % 100 ? T.fgDim : T.fg, 0.7); }
      txt('◄' + Math.round(depth), RULER_COL + 1, 17.5, T.yellow, 0.9);
      ctx.restore();
    }

    function smallScroller(sec, alpha) {
      var off = Math.floor(sec * 12) % SMALL_SCROLL.length, ph = sec * 12 - Math.floor(sec * 12);
      ctx.save(); ctx.beginPath(); ctx.rect(0, 3.5 * ch, RULER_COL * cw, 1.9 * ch); ctx.clip();
      for (var i = 0; i < 72; i++) {
        var c = SMALL_SCROLL.charAt((off + i) % SMALL_SCROLL.length); if (c === ' ') continue;
        txt(c, 16 + i - ph, 4.05 + Math.sin(sec * 3.4 + i * 0.22) * 0.3, i % 3 === 0 ? T.yellow : (i % 2 ? T.cyan : T.fgBright), alpha);
      }
      ctx.restore();
    }
    function bigScroller(sec, rowC) {
      var px = Math.max(3, Math.round(ch * 0.4)), cwid = 8 * px + px, speed = 160;
      var x0 = -((sec * speed) % cwid), first = Math.floor((sec * speed) / cwid), n = Math.ceil(RULER_COL * cw / cwid) + 1;
      var rows = []; for (var r = 0; r < 16; r++) rows.push(mix(T.yellow, T.magenta, r / 15));
      for (var i = 0; i < n; i++) {
        var c = GREETZ.charAt((first + i) % GREETZ.length); if (c === ' ') continue;
        var bits = chunk(c), bx = x0 + i * cwid, by = rowC * ch + Math.sin(sec * 2.2 + (bx / 140)) * ch * 2.2;
        if (bx > RULER_COL * cw) break;
        for (var y = 0; y < 16; y++) { ctx.fillStyle = rows[y]; for (var x = 0; x < 8; x++) if (bits[y * 8 + x]) ctx.fillRect(bx + x * px, by + y * px, px - 1, px - 1); }
      }
    }
    function lens(sec, shiftX, shiftY, alpha) {
      var L = big.lens, R = 5.2 * ch, cx = (L.x + 6) * cw + shiftX, cy = (L.y + 5) * ch + shiftY, mag = 1.7, src = R / mag;
      var size = Math.ceil(R * 2);
      if (lensBuf.width !== size) { lensBuf.width = size; lensBuf.height = size; }
      lctx.clearRect(0, 0, size, size);
      try { lctx.drawImage(cv, cx - src, cy - src, src * 2, src * 2, 0, 0, size, size); } catch (x) { return; }
      ctx.save(); ctx.globalAlpha = alpha; ctx.beginPath(); ctx.arc(cx, cy, R, 0, 6.283); ctx.clip();
      ctx.drawImage(lensBuf, cx - R, cy - R);
      var gl = ctx.createRadialGradient(cx - R * 0.35, cy - R * 0.4, R * 0.05, cx, cy, R);
      gl.addColorStop(0, 'rgba(255,255,255,0.18)'); gl.addColorStop(0.7, 'rgba(255,255,255,0)'); gl.addColorStop(1, 'rgba(0,0,0,0.35)');
      ctx.fillStyle = gl; ctx.fillRect(cx - R, cy - R, R * 2, R * 2);
      ctx.restore();
      ctx.globalAlpha = alpha; ctx.strokeStyle = T.fg; ctx.lineWidth = Math.max(3, ch * 0.18); ctx.beginPath(); ctx.arc(cx, cy, R, 0, 6.283); ctx.stroke();
      ctx.fillStyle = T.fgDim; for (var k = 0; k < 8; k++) { var an = k * 0.785 + 0.39; ctx.fillRect(cx + Math.cos(an) * R - 3, cy + Math.sin(an) * R - 3, 6, 6); }
      ctx.globalAlpha = 1;
    }

    function frame() {
      var now = Date.now();
      if (opts.capFps !== 0 && now - lastDraw < 31) { G.requestAnimationFrame(frame); return; } // 30 fps cap: it's a screensaver
      var sec = (now - startMs) / 1000, dt = clamp(sec - lastSec, 0, 0.1); lastSec = sec; lastDraw = now;
      if (cv.width !== (G.innerWidth || 1920) || cv.height !== (G.innerHeight || 1080)) { cv.width = G.innerWidth || 1920; cv.height = G.innerHeight || 1080; glyphs = {}; glyphN = 0; }
      W = cv.width; H = cv.height; cw = W / COLS; ch = H / ROWS; fs = Math.min(cw * 1.65, ch * 0.88);
      if (sec - palT > 5) { T = pal(); palT = sec; glyphs = {}; glyphN = 0; }
      var k = Math.floor(sec / partSec), part = k % 5, fr = (sec - k * partSec) / partSec, depth = depthAt(sec, partSec); lastDepth = depth;
      simulate(dt, sec, part, fr);
      // whole-scene pixel shift: a few px per minute so nothing sits on the same OLED pixels for long
      var sx = Math.round(Math.sin(sec / 53) * 7 + Math.sin(sec / 211) * 5), sy = Math.round(Math.cos(sec / 67) * 5);
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.fillStyle = bg(depth); ctx.fillRect(0, 0, W, H);
      ctx.setTransform(1, 0, 0, 1, sx, sy);
      drawCopper(2.15 * ch, 3.25 * ch, 5, sec, 0.55, 0.8);
      drawScene(sec, dt, part, fr, depth);
      if (part === 2) { ctx.setTransform(1, 0, 0, 1, 0, 0); lens(sec, sx, sy, ease(fr * 10) * ease((1 - fr) * 10)); ctx.setTransform(1, 0, 0, 1, sx, sy); }
      if (part === 4) { drawCopper(18 * ch, 28 * ch, 7, sec * 1.4, 1.4, 0.35); bigScroller(sec, 19.5); }
      else smallScroller(sec, 1);
      var cyc = (sec + 3) % 45, hudA = cyc < 10 ? 1 : (cyc < 12 ? 1 - (cyc - 10) / 2 : (cyc > 43 ? (cyc - 43) / 2 : 0));
      if (hudBoost > 0) { hudBoost -= dt; hudA = 1; }
      var levels = liveLevels() || modelLevels(sec, partSec);
      hud(sec, part, depth, levels, hudA);
      // part transitions: short fade through black (demo style)
      var edge = Math.min(fr * partSec, (1 - fr) * partSec);
      if (edge < 0.8) { ctx.setTransform(1, 0, 0, 1, 0, 0); ctx.globalAlpha = 1 - edge / 0.8; ctx.fillStyle = '#000'; ctx.fillRect(0, 0, W, H); ctx.globalAlpha = 1; }
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      G.requestAnimationFrame(frame);
    }
    for (var i = 0; i < 4; i++) spawnFish(0, false);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () { glyphs = {}; glyphN = 0; chunky = {}; });
    G.requestAnimationFrame(frame);
    return api;
  }

  G.OmarchyHomage = { run: run, renderAudio: renderAudio, songEvents: songEvents, depthAt: depthAt, PARTS: PARTS, CASTLE: CASTLE, mirror: mirror };
})(window);
