// Duet local server. Zero dependencies: `node server.js` then open http://127.0.0.1:8000
// Serves the app and proxies YouTube Music search (unofficial InnerTube API, no key or login needed).
const http = require('http'), fs = require('fs'), path = require('path');
const PORT = process.env.PORT || 8000;

const CTX = { client: { clientName: 'WEB_REMIX', clientVersion: '1.20250101.01.00', hl: 'en', gl: 'GB' } };
const SONGS_FILTER = 'EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D';

// Walk any JSON and collect every object stored under `key`.
function collect(node, key, out = []) {
  if (Array.isArray(node)) node.forEach(n => collect(n, key, out));
  else if (node && typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) { if (k === key) out.push(v); else collect(v, key, out); }
  }
  return out;
}

function parseSongs(json) {
  const seen = new Set(), tracks = [];
  for (const it of collect(json, 'musicResponsiveListItemRenderer')) {
    const cols = it.flexColumns || [];
    const title = cols[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.text;
    const runs = (cols[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs || []).map(r => r.text).filter(t => t.trim() && t.trim() !== '•');
    const videoId = it.playlistItemData?.videoId || collect(it, 'videoId')[0];
    if (!title || !videoId || seen.has(videoId)) continue;
    seen.add(videoId);
    const durRun = runs.find(t => /^\d+:\d\d(:\d\d)?$/.test(t));
    const secs = durRun ? durRun.split(':').reduce((a, n) => a * 60 + +n, 0) : 0;
    const body = runs.filter(t => t !== durRun);
    const hasType = ['Song', 'Video'].includes(body[0]);
    const thumbs = it.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails || [];
    tracks.push({ src: 'youtube', id: videoId, title, artist: (hasType ? body.slice(1) : body)[0] || '', album: (hasType ? body.slice(1) : body)[1] || '',
      art: thumbs[0]?.url, dur: secs });
  }
  return tracks;
}

async function ytmSearch(q) {
  const r = await fetch('https://music.youtube.com/youtubei/v1/search?prettyPrint=false', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Origin: 'https://music.youtube.com', Referer: 'https://music.youtube.com/',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36' },
    body: JSON.stringify({ context: CTX, query: q, params: SONGS_FILTER })
  });
  if (!r.ok) throw new Error('YouTube Music returned ' + r.status);
  return parseSongs(await r.json());
}

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css' };
const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://x');
  try {
    if (u.pathname === '/api/yt/search') {
      const q = u.searchParams.get('q') || '';
      const tracks = q ? await ytmSearch(q) : [];
      res.writeHead(200, { 'Content-Type': 'application/json' }); return res.end(JSON.stringify(tracks));
    }
    const file = path.join(__dirname, u.pathname === '/' ? 'index.html' : path.basename(u.pathname));
    if (!fs.existsSync(file)) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  } catch (e) { res.writeHead(502, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ error: e.message })); }
});
if (require.main === module) server.listen(PORT, '127.0.0.1', () => console.log('Duet running at http://127.0.0.1:' + PORT));
module.exports = { parseSongs };
