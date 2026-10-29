/* OmarchyTV (webOS / Tizen) · Cinemarchy @dhh37 channel, v2.13.
 * DHH's public YouTube uploads feed (Atom), cached for 6 h, merged with a curated list of real uploads.
 * Links and metadata only (title, thumbnail, link, published): nothing is downloaded, no stream extraction.
 * Pure functions, also loaded by webos/tests/dhh.test.js under Node. */
(function (root) {
  "use strict";
  var CHANNEL_ID = "UCUkM9uMpWatT7gVWShgtKFw";   // resolved once from youtube.com/@dhh37 (2026-10-29)
  var FEED_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=" + CHANNEL_ID;
  var TTL_MS = 6 * 3600 * 1000;
  var FIRST_RUN_MS = 14 * 86400 * 1000;
  var CACHE_KEY = "omarchytv_dhh37_feed";
  var SEEN_KEY = "omarchytv_dhh37_seen";

  var CURATED = [
    ["F7fe9pa8OeE", "Omarchy Quattro"],
    ["jgSjG0IYw4o", "Omarchy running on 2011 X220 2GB RAM (with room to spare)"],
    ["sMxskir7Rug", "Omacon Keynote"],
    ["jqDZy2zaxcE", "Tmux comes to the next Omarchy"],
    ["L3EafsSCv80", "Omarchy 3"],
    ["TcHY0AEd2Uw", "Omarchy 2.0"],
    ["I5Mnni7cea8", "Omarchy"],
    ["Cft6mZDzIng", "Omarchy: The Unified Menu System"],
    ["YRqHxBY3wps", "Omarchy: Install extra theme"],
    ["P9qzvCkqLAA", "Omarchy: Change font"],
    ["c8sJ2gmA_Zo", "Omarchy: Change background"]
  ];

  function validId(id) { return typeof id === "string" && /^[A-Za-z0-9_-]{11}$/.test(id); }
  function watchUrl(id) { return "https://www.youtube.com/watch?v=" + id; }
  function thumbUrl(id) { return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg"; }
  function video(id, title, thumb, published) {
    return { id: id, title: title, link: watchUrl(id), thumb: thumb || thumbUrl(id), published: published || 0 };
  }

  function curated() { return CURATED.map(function (c) { return video(c[0], c[1], null, 0); }); }

  function decode(s) {
    return String(s || "")
      .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
      .replace(/&#x([0-9a-f]+);/gi, function (_, h) { return String.fromCharCode(parseInt(h, 16)); })
      .replace(/&#(\d+);/g, function (_, d) { return String.fromCharCode(parseInt(d, 10)); })
      .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
      .replace(/&amp;/g, "&");
  }
  function clean(s, max) {
    var t = decode(s).replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim();
    return t.length > max ? t.slice(0, max - 1) + "…" : t;
  }
  function tag(block, name) {
    var m = new RegExp("<" + name + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + name + ">").exec(block);
    return m ? m[1].trim() : "";
  }

  /** Atom text -> videos, newest first. Entities beyond the XML five are not expanded; a DOCTYPE is refused. */
  function parse(xml) {
    var out = [], seen = {};
    if (typeof xml !== "string" || !xml || /<!DOCTYPE/i.test(xml) || !/<feed[\s>]/.test(xml)) return out;
    var re = /<entry(?:\s[^>]*)?>([\s\S]*?)<\/entry>/g, m;
    while ((m = re.exec(xml)) && out.length < 30) {
      var e = m[1];
      var id = tag(e, "yt:videoId");
      if (!validId(id) || seen[id]) continue;
      var body = e.replace(/<media:group[\s\S]*?<\/media:group>/, "");
      var title = clean(tag(body, "title"), 160);
      if (!title) continue;
      seen[id] = true;
      var th = /<media:thumbnail[^>]*\burl="([^"]+)"/.exec(e);
      var thumb = th && /^https:\/\/i\d?\.ytimg\.com\//.test(th[1]) ? th[1] : thumbUrl(id);
      var pub = Date.parse(tag(e, "published")) || 0;
      out.push(video(id, title, thumb, pub));
    }
    out.sort(function (a, b) { return b.published - a.published; });
    return out;
  }

  function merge(feed, cur) {
    var out = [], ids = {};
    (feed || []).concat(cur || []).forEach(function (v) { if (!ids[v.id]) { ids[v.id] = true; out.push(v); } });
    return out;
  }

  function isNew(v, lastSeen, now) {
    if (!v.published) return false;
    return lastSeen > 0 ? v.published > lastSeen : v.published > now - FIRST_RUN_MS;
  }

  function newest(list) { return (list || []).reduce(function (m, v) { return Math.max(m, v.published || 0); }, 0); }

  /** Cached load: store = localStorage-like, fetchText(url) -> Promise<string>. Resolves {videos, at, error}. */
  function load(store, fetchText, force, now) {
    now = now || Date.now();
    var cached = null;
    try { cached = JSON.parse(store.getItem(CACHE_KEY) || "null"); } catch (e) { cached = null; }
    var cachedList = cached && cached.xml ? parse(cached.xml) : [];
    if (!force && cached && now - cached.at < TTL_MS && cachedList.length) {
      return Promise.resolve({ videos: cachedList, at: cached.at, error: "" });
    }
    return Promise.resolve().then(function () { return fetchText(FEED_URL); }).then(function (xml) {
      var list = parse(xml);
      if (!list.length) throw new Error("feed unreadable");
      try { store.setItem(CACHE_KEY, JSON.stringify({ at: now, xml: xml })); } catch (e) {}
      return { videos: list, at: now, error: "" };
    }).catch(function (err) {
      return { videos: cachedList, at: cached ? cached.at : 0, error: cachedList.length ? "" : String((err && err.message) || "offline") };
    });
  }

  function lastSeen(store) { return parseInt(store.getItem(SEEN_KEY) || "0", 10) || 0; }
  function markSeen(store, list) {
    var n = newest(list);
    if (n > lastSeen(store)) try { store.setItem(SEEN_KEY, String(n)); } catch (e) {}
  }

  var api = {
    CHANNEL_ID: CHANNEL_ID, FEED_URL: FEED_URL, TTL_MS: TTL_MS, CHANNEL_URL: "https://www.youtube.com/@dhh37/videos",
    validId: validId, watchUrl: watchUrl, thumbUrl: thumbUrl, curated: curated, parse: parse, merge: merge,
    isNew: isNew, newest: newest, load: load, lastSeen: lastSeen, markSeen: markSeen
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.OmarchyDhh = api;
})(typeof window !== "undefined" ? window : this);
