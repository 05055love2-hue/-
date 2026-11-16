// أرقام نسخ التطبيقات من آخر إصدار — برمجة أبو نواف
const URL_V = 'https://github.com/05055love2-hue/tatbeeqati/releases/latest/download/versions.json';
const H = { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'public, max-age=120' };

export async function onRequestGet() {
  try {
    const r = await fetch(URL_V + '?t=' + Date.now(), { redirect: 'follow', cf: { cacheTtl: 60 } });
    if (!r.ok) return new Response(JSON.stringify({ error: 'up' }), { status: 502, headers: H });
    return new Response(await r.text(), { headers: H });
  } catch (e) {
    return new Response(JSON.stringify({ error: 'up' }), { status: 502, headers: H });
  }
}
