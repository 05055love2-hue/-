// تعليقات وتقييمات موقع تطبيقاتي — برمجة أبو نواف
const APPS = ['car', 'tv', 'ytcar', 'seasons', 'pay', 'obd', 'hammil', 'store', 'cleaner', 'salati', 'sayarati'];
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};
const json = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...CORS } });

async function ready(db) {
  await db.prepare(`CREATE TABLE IF NOT EXISTS reviews (
    id INTEGER PRIMARY KEY AUTOINCREMENT, app TEXT NOT NULL, stars INTEGER NOT NULL,
    name TEXT, body TEXT, ip TEXT, t INTEGER NOT NULL)`).run();
}
async function hash(s) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return [...new Uint8Array(d)].slice(0, 12).map(b => b.toString(16).padStart(2, '0')).join('');
}
const clean = (s, n) => String(s || '').replace(/[\u0000-\u0008\u000B-\u001F\u007F]/g, '').replace(/\s{3,}/g, '  ').trim().slice(0, n);
const BAD = /(https?:\/\/|www\.|\.com\b|واتس|سناب|تلقرام|telegram)/i;

export async function onRequestOptions() { return new Response(null, { headers: CORS }); }

export async function onRequestGet({ env }) {
  if (!env.DB) return json({ error: 'no-db' }, 503);
  await ready(env.DB);
  const out = {};
  for (const a of APPS) out[a] = { avg: 0, count: 0, items: [] };
  const sum = await env.DB.prepare('SELECT app, COUNT(*) c, AVG(stars) s FROM reviews GROUP BY app').all();
  for (const r of sum.results) if (out[r.app]) { out[r.app].count = r.c; out[r.app].avg = Math.round(r.s * 10) / 10; }
  const items = await env.DB.prepare(`SELECT id, app, stars, name, body, t FROM (
      SELECT *, ROW_NUMBER() OVER (PARTITION BY app ORDER BY t DESC) rn FROM reviews WHERE body <> '') WHERE rn <= 30 ORDER BY t DESC`).all();
  for (const r of items.results) if (out[r.app]) out[r.app].items.push({ id: r.id, stars: r.stars, name: r.name, text: r.body, t: r.t });
  return json({ apps: out });
}

export async function onRequestPost({ request, env }) {
  if (!env.DB) return json({ error: 'no-db' }, 503);
  let b; try { b = await request.json(); } catch { return json({ error: 'bad' }, 400); }
  const app = String(b.app || ''), stars = Math.round(Number(b.stars));
  if (!APPS.includes(app) || !(stars >= 1 && stars <= 5)) return json({ error: 'bad' }, 400);
  const name = clean(b.name, 30) || 'زائر';
  const body = clean(b.text, 400);
  if (BAD.test(body) || BAD.test(name)) return json({ error: 'links' }, 400);
  await ready(env.DB);
  const ip = await hash((request.headers.get('CF-Connecting-IP') || '') + '|tatbeeqati');
  const now = Date.now();
  const same = await env.DB.prepare('SELECT COUNT(*) c FROM reviews WHERE ip=? AND app=? AND t>?').bind(ip, app, now - 864e5).first();
  if (same.c >= 1) return json({ error: 'once' }, 429);
  const all = await env.DB.prepare('SELECT COUNT(*) c FROM reviews WHERE ip=? AND t>?').bind(ip, now - 36e5).first();
  if (all.c >= 5) return json({ error: 'slow' }, 429);
  await env.DB.prepare('INSERT INTO reviews (app, stars, name, body, ip, t) VALUES (?,?,?,?,?,?)').bind(app, stars, name, body, ip, now).run();
  return json({ ok: true });
}

// حذف تعليق: يحتاج كلمة سر ADMIN_KEY في إعدادات المشروع
export async function onRequestDelete({ request, env }) {
  const u = new URL(request.url);
  if (!env.DB || !env.ADMIN_KEY || u.searchParams.get('key') !== env.ADMIN_KEY) return json({ error: 'no' }, 403);
  await env.DB.prepare('DELETE FROM reviews WHERE id=?').bind(Number(u.searchParams.get('id'))).run();
  return json({ ok: true });
}
