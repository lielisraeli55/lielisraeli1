/**
 * Optional realtime cloud sync.
 *
 * The starter ships in OFFLINE mode — every captured cell is stored
 * only on the player's device. To enable a shared multi-player world:
 *
 *   1. `npm install firebase`
 *   2. Create a free project at https://console.firebase.google.com
 *      and turn on Realtime Database (test rules are fine for dev).
 *   3. Paste your config into FIREBASE_CONFIG below and flip
 *      CLOUD_ENABLED to true.
 *
 * The protocol is intentionally tiny:
 *   /cells/<q|r> = { owner, color, capturedAt, score }
 * Subscribers get the whole map and merge it with their local copy
 * (last-write-wins on capturedAt).
 */

export const CLOUD_ENABLED = false;

const FIREBASE_CONFIG = {
  apiKey: 'YOUR_API_KEY',
  databaseURL: 'https://YOUR_PROJECT.firebaseio.com',
  projectId: 'YOUR_PROJECT',
  appId: 'YOUR_APP_ID',
};

let firebaseLib = null;
let app = null;
let database = null;

async function ensureInit() {
  if (!CLOUD_ENABLED) return false;
  if (database) return true;
  try {
    const fb = require('firebase/app');
    const db = require('firebase/database');
    app = fb.initializeApp(FIREBASE_CONFIG);
    database = db.getDatabase(app);
    firebaseLib = { ...fb, ...db };
    return true;
  } catch (e) {
    console.warn('[cloud] firebase unavailable, staying offline:', e?.message);
    return false;
  }
}

/** Subscribe to remote cell map updates. Returns an unsubscribe fn. */
export function subscribeCells(onUpdate) {
  let unsub = () => {};
  ensureInit().then((ok) => {
    if (!ok) return;
    const cellsRef = firebaseLib.ref(database, 'cells');
    const cb = firebaseLib.onValue(cellsRef, (snap) => onUpdate(snap.val() || {}));
    unsub = () => firebaseLib.off(cellsRef, 'value', cb);
  });
  return () => unsub();
}

/** Push one capture upstream. */
export async function pushCapture(cellKey, payload) {
  const ok = await ensureInit();
  if (!ok) return;
  await firebaseLib.set(firebaseLib.ref(database, `cells/${cellKey}`), payload);
}
