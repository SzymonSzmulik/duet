/**
 * Duet release script: the backend of the "Send to testers" / "Release to everyone" buttons in
 * Duet Owner (ReleaseControls.kt). Runs as a Google Apps Script web app in the owner's account and
 * copies the owner feed's version into the tester and release feeds. See docs/RELEASING.md.
 *
 * OWNER_KEY must equal duet.ownerKey in local.properties (baked into Duet Owner builds only).
 */
const OWNER_KEY = 'PASTE_OWNER_KEY';
const FEEDS = {
  owner: 'PASTE_DOCUMENT_ID',
  tester: 'PASTE_DOCUMENT_ID',
  release: 'PASTE_DOCUMENT_ID',
};

function readFeed(id) {
  const text = DocumentApp.openById(id).getBody().getText().replace(/[“”]/g, '"').trim();
  return JSON.parse(text);
}

function writeFeed(id, feed) {
  DocumentApp.openById(id).getBody().setText(JSON.stringify(feed));
}

/** Copies the owner build to testers ("testers") or to testers and everyone ("everyone"). */
function publish(action) {
  const owner = readFeed(FEEDS.owner);
  const base = { versionCode: owner.versionCode, versionName: owner.versionName, notes: owner.notes || '' };
  if (action !== 'testers' && action !== 'everyone') return { ok: false, error: 'unknown action' };
  if (!owner.testerApkUrl || (action === 'everyone' && !owner.releaseApkUrl)) return { ok: false, error: 'owner feed has no APK links' };
  if (readFeed(FEEDS.tester).versionCode < owner.versionCode) writeFeed(FEEDS.tester, Object.assign({}, base, { apkUrl: owner.testerApkUrl }));
  if (action === 'everyone') writeFeed(FEEDS.release, Object.assign({}, base, { apkUrl: owner.releaseApkUrl }));
  return { ok: true };
}

function doGet(e) {
  const p = (e && e.parameter) || {};
  const result = p.key === OWNER_KEY ? publish(p.action) : { ok: false, error: 'denied' };
  return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
}
