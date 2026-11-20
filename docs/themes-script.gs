/**
 * Duet shared styles: the backend of Settings > Appearance > Shared styles (shop/ThemeShop.kt, D66).
 * A Google Apps Script web app in the owner's account, storing styles in a Google Sheet.
 *
 * Setup (once): script.new, paste this file, set SHEET_ID, Deploy > New deployment > Web app,
 * Execute as: Me, Who has access: Anyone. Put the /exec URL into ThemeShop.URL in the app.
 *
 * Every call is a GET with ?action=...; replies are JSON {ok:true,...} or {ok:false,error:"..."}.
 * Nobody signs in here: a person's username is tied to a random key their phone made, stored as a SHA-256 hash,
 * so only that phone can share or delete under that name. Style codes are checked against the app's format.
 */
const SHEET_ID = 'PASTE_DOCUMENT_ID'; // "Duet shared styles (do not delete)" in Duet/Feeds
const MAX_PER_USER = 30;
const LIST_LIMIT = 300;

function sheet(name, header) {
  const book = SpreadsheetApp.openById(SHEET_ID);
  let s = book.getSheetByName(name);
  if (!s) { s = book.insertSheet(name); s.appendRow(header); }
  // Columns added later (description, tags) get their heading on the first call after an update.
  if (s.getLastColumn() < header.length) s.getRange(1, 1, 1, header.length).setValues([header]);
  return s;
}
const themes = () => sheet('themes', ['id', 'name', 'author', 'code', 'likes', 'created', 'description', 'tags']);
const users = () => sheet('users', ['username', 'keyHash', 'created']);

function reply(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
function hash(key) {
  return Utilities.base64Encode(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(key)));
}
function rows(s) { return s.getDataRange().getValues().slice(1); }

// Usernames: 3-20 letters, digits, _ or . ; style names: 1-24 plain characters.
const USER_RE = /^[A-Za-z0-9_.]{3,20}$/;
const NAME_RE = /^[^|;=\n\r<>]{1,24}$/;
// The app's share code: base|accent|Knob=Option;Knob=Option (only letters, digits and . : _ - in values).
// Descriptions: up to 140 plain characters. Tags: up to 4 from the app's fixed list.
const DESC_RE = /^[^<>\n\r]{0,140}$/;
const TAGS = ['dark', 'light', 'minimal', 'colourful', 'bold', 'soft', 'retro', 'clean'];
const CODE_RE = /^[a-z]{1,20}\|\d{1,12}\|([A-Za-z]{1,20}=[A-Za-z0-9._:-]{1,20})(;[A-Za-z]{1,20}=[A-Za-z0-9._:-]{1,20}){0,40}$/;

/** True when [username] exists and [key] is its key. */
function owns(username, key) {
  const row = rows(users()).find(r => String(r[0]).toLowerCase() === String(username).toLowerCase());
  return !!row && row[1] === hash(key);
}

function register(p) {
  if (!USER_RE.test(p.username || '') || String(p.key || '').length < 24) return { ok: false, error: 'bad username' };
  const s = users();
  const row = rows(s).find(r => String(r[0]).toLowerCase() === p.username.toLowerCase());
  if (row) return row[1] === hash(p.key) ? { ok: true } : { ok: false, error: 'taken' };
  s.appendRow([p.username, hash(p.key), new Date().toISOString()]);
  return { ok: true };
}

function publish(p) {
  if (!owns(p.username, p.key)) return { ok: false, error: 'not yours' };
  if (!NAME_RE.test(p.name || '') || !CODE_RE.test(p.code || '') || !DESC_RE.test(p.description || '')) return { ok: false, error: 'bad style' };
  const tags = String(p.tags || '').split(',').filter(t => TAGS.indexOf(t) >= 0).slice(0, 4).join(',');
  const s = themes();
  const mine = rows(s).filter(r => String(r[2]).toLowerCase() === p.username.toLowerCase());
  if (mine.length >= MAX_PER_USER) return { ok: false, error: 'too many' };
  const id = Utilities.getUuid().slice(0, 8);
  s.appendRow([id, p.name, p.username, p.code, 0, new Date().toISOString(), p.description || '', tags]);
  return { ok: true, id: id };
}

function remove(p) {
  if (!owns(p.username, p.key)) return { ok: false, error: 'not yours' };
  const s = themes();
  const all = s.getDataRange().getValues();
  for (let i = all.length - 1; i >= 1; i--) {
    if (all[i][0] === p.id && String(all[i][2]).toLowerCase() === p.username.toLowerCase()) { s.deleteRow(i + 1); return { ok: true }; }
  }
  return { ok: false, error: 'not found' };
}

function like(p) {
  const s = themes();
  const all = s.getDataRange().getValues();
  for (let i = 1; i < all.length; i++) {
    if (all[i][0] === p.id) { s.getRange(i + 1, 5).setValue(Number(all[i][4] || 0) + 1); return { ok: true }; }
  }
  return { ok: false, error: 'not found' };
}

function list() {
  const items = rows(themes())
    .map(r => ({ id: String(r[0]), name: String(r[1]), author: String(r[2]), code: String(r[3]), likes: Number(r[4] || 0), created: String(r[5]),
                 description: String(r[6] || ''), tags: String(r[7] || '').split(',').filter(String) }))
    .sort((a, b) => b.created.localeCompare(a.created))
    .slice(0, LIST_LIMIT);
  return { ok: true, themes: items };
}

function doGet(e) {
  const p = e.parameter || {};
  if (p.action === 'list') return reply(list());
  // Writes take a lock so two phones can't both claim a name or clash on a row.
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(10000)) return reply({ ok: false, error: 'busy' });
  try {
    switch (p.action) {
      case 'register': return reply(register(p));
      case 'publish': return reply(publish(p));
      case 'delete': return reply(remove(p));
      case 'like': return reply(like(p));
      default: return reply({ ok: false, error: 'unknown action' });
    }
  } finally {
    lock.releaseLock();
  }
}
