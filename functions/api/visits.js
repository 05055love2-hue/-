// عدّاد زيارات موقع تطبيقاتي (على سيرفر الموقع نفسه) — برمجة أبو نواف
const H = { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-store' };
const json = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: H });

async function hash(s) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return [...new Uint8Array(d)].slice(0, 12).map(b => b.toString(16).padStart(2, '0')).join('');
}

export async function onRequestGet({ request, env }) {
  if (!env.DB) return json({ error: 'no-db' }, 503);
  const db = env.DB;
  await db.prepare('CREATE TABLE IF NOT EXISTS visits (day TEXT NOT NULL, who TEXT NOT NULL, PRIMARY KEY(day, who))').run();
  const url = new URL(request.url);
  if (url.searchParams.get('hit') === '1') {
    const ip = request.headers.get('CF-Connecting-IP') || '';
    const day = new Date().toISOString().slice(0, 10);
    // كل جهاز ينحسب مرة وحدة في اليوم
    await db.prepare('INSERT OR IGNORE INTO visits (day, who) VALUES (?, ?)').bind(day, await hash(ip + '|' + (request.headers.get('User-Agent') || ''))).run();
  }
  const r = await db.prepare('SELECT COUNT(*) c FROM visits').first();
  return json({ value: r ? r.c : 0 });
}
