// node webos/tests/dhh.test.js — parses the saved Android sample feed with the web parser.
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const D = require("../app/dhh.js");
const xml = fs.readFileSync(path.join(__dirname, "../../launcher/app/src/test/resources/dhh37/sample-feed.xml"), "utf8");
const v = D.parse(xml);
assert.ok(v.length >= 3, "parsed entries");
assert.ok(v.every((x) => /^[A-Za-z0-9_-]{11}$/.test(x.id)), "only valid ids");
assert.strictEqual(new Set(v.map((x) => x.id)).size, v.length, "deduped");
assert.ok(v.every((x) => !x.thumb || /^https:\/\/i\d?\.ytimg\.com\//.test(x.thumb)), "ytimg thumbs only");
for (let i = 1; i < v.length; i++) assert.ok(v[i - 1].published >= v[i].published, "newest first");
assert.ok(v.some((x) => x.id === "MOIG0QGJZe8"));
assert.deepStrictEqual(D.parse('<!DOCTYPE x [<!ENTITY a "b">]><feed/>'), []);
const m = D.merge(v, D.curated());
assert.strictEqual(new Set(m.map((x) => x.id)).size, m.length);
assert.ok(D.isNew({ published: 2000 }, 1000, 3000) && !D.isNew({ published: 500 }, 1000, 3000));
const store = { d: {}, getItem(k) { return this.d[k] ?? null; }, setItem(k, x) { this.d[k] = x; } };
D.load(store, () => Promise.resolve(xml), true, Date.now()).then((r) => {
  assert.ok(r.videos.length >= v.length && !r.error);
  return D.load(store, () => Promise.reject(new Error("offline")), true, Date.now());
}).then((r) => {
  assert.ok(r.videos.length > 0, "offline falls back to cache");
  console.log("dhh.test.js: all assertions passed");
}).catch((e) => { console.error(e); process.exit(1); });
