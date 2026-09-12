/**
 * 极简 IndexedDB 封装 —— 持久化 FileSystemDirectoryHandle，
 * 让用户下次打开 ObsidianMind 时免重新授权。
 */

const DB_NAME = 'obsidianmind';
const STORE = 'handles';
const KEY_HANDLE = 'vault-handle';
const KEY_META = 'vault-meta';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) {
        req.result.createObjectStore(STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/** 读：返回请求结果 */
async function readStore<T>(key: string): Promise<T | undefined> {
  const db = await openDb();
  return new Promise<T | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).get(key);
    req.onsuccess = () => resolve(req.result as T | undefined);
    req.onerror = () => reject(req.error);
    tx.oncomplete = () => db.close();
  });
}

/** 写：put / delete，完成即关闭 */
async function writeStore(key: string, value?: unknown): Promise<void> {
  const db = await openDb();
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    const store = tx.objectStore(STORE);
    if (value === undefined) {
      store.delete(key);
    } else {
      store.put(value, key);
    }
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = () => reject(tx.error);
  });
}

export const vaultIdb = {
  async saveHandle(handle: FileSystemDirectoryHandle): Promise<void> {
    await writeStore(KEY_HANDLE, handle);
  },

  async loadHandle(): Promise<FileSystemDirectoryHandle | null> {
    const handle = await readStore<FileSystemDirectoryHandle>(KEY_HANDLE);
    return handle ?? null;
  },

  async saveMeta(meta: Record<string, unknown>): Promise<void> {
    await writeStore(KEY_META, meta);
  },

  async loadMeta(): Promise<Record<string, unknown> | null> {
    const meta = await readStore<Record<string, unknown>>(KEY_META);
    return meta ?? null;
  },

  async clear(): Promise<void> {
    await writeStore(KEY_HANDLE);
    await writeStore(KEY_META);
  },
};
