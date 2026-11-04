export interface PrivateDraft { content: string; recording?: { file: File; seconds: number } }
interface StoredDraft extends PrivateDraft { key: string; owner: string; expiresAt: number }
const STORE = 'drafts';
let database: Promise<IDBDatabase> | undefined;
function open() {
  database ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('manabihub-private-drafts', 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE, { keyPath: 'key' });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => { database = undefined; reject(request.error); };
  });
  return database;
}
const keyFor = (owner: string, cohort: string, assignment: string) => `${owner}:${cohort}:${assignment}`;
export async function loadPrivateDraft(owner: string, cohort: string, assignment: string): Promise<PrivateDraft | undefined> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE, 'readwrite'); const store = transaction.objectStore(STORE);
    const request = store.get(keyFor(owner, cohort, assignment)); let value: StoredDraft | undefined;
    request.onsuccess = () => {
      value = request.result as StoredDraft | undefined;
      if (value && value.expiresAt < Date.now()) { store.delete(value.key); value = undefined; }
    };
    transaction.oncomplete = () => resolve(value);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
export async function savePrivateDraft(owner: string, cohort: string, assignment: string, draft: PrivateDraft) {
  const db = await open();
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORE, 'readwrite');
    transaction.objectStore(STORE).put({ ...draft, key: keyFor(owner, cohort, assignment), owner, expiresAt: Date.now() + 7 * 86400000 } satisfies StoredDraft);
    transaction.oncomplete = () => resolve(); transaction.onerror = () => reject(transaction.error); transaction.onabort = () => reject(transaction.error);
  });
}
export async function deletePrivateDraft(owner: string, cohort: string, assignment: string) {
  const db = await open();
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORE, 'readwrite'); transaction.objectStore(STORE).delete(keyFor(owner, cohort, assignment));
    transaction.oncomplete = () => resolve(); transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
export async function clearPrivateDrafts(owner: string) {
  if (typeof indexedDB === 'undefined') return;
  const db = await open();
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORE, 'readwrite'); const cursor = transaction.objectStore(STORE).openCursor();
    cursor.onsuccess = () => {
      const row = cursor.result;
      if (row) { const item = row.value as StoredDraft; if (item.owner === owner || item.expiresAt < Date.now()) row.delete(); row.continue(); }
    };
    transaction.oncomplete = () => resolve(); transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
