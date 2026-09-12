import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { ScanProgress, VaultChangeEvent, VaultInfo } from '@/types/vault';
import { vaultRepository } from '@/services/repositories/vaultRepository';
import { vaultIdb } from '@/services/fs/idb';
import { knowledgeService } from '@/services/knowledgeService';
import { useKnowledgeStore } from './knowledge';

/**
 * Vault 连接状态中心：连接 / 扫描 / 监听 / 断开。
 * UI 只与本 Store 交互，不直接触碰文件系统。
 */

const WATCHER_INTERVAL = 8000;

export const useVaultStore = defineStore('vault', () => {
  const status = ref<'none' | 'restoring' | 'needs-permission' | 'connected' | 'error'>('none');
  const error = ref('');
  const info = ref<VaultInfo | null>(null);
  const progress = ref<ScanProgress>({ scanning: false, phase: 'idle', found: 0, message: '' });
  const changes = ref<VaultChangeEvent[]>([]);
  const welcomeDismissed = ref(localStorage.getItem('om-welcome-dismissed') === '1');
  const pendingHandle = ref<FileSystemDirectoryHandle | null>(null);
  const showWelcome = ref(false);

  let watcherTimer: ReturnType<typeof setInterval> | null = null;

  async function refreshInfo(): Promise<void> {
    if (!vaultRepository.connected) {
      info.value = null;
      return;
    }
    const stats = vaultRepository.getStats();
    const linkCount = await knowledgeService.getLinkCount();
    const meta = await vaultIdb.loadMeta();
    info.value = {
      name: vaultRepository.displayName,
      path: vaultRepository.demo ? '浏览器内置存储（OPFS）' : `已授权目录「${vaultRepository.displayName}」`,
      connected: true,
      noteCount: stats.noteCount,
      folderCount: stats.folderCount,
      tagCount: stats.tagCount,
      linkCount,
      lastScanAt: (meta?.lastScanAt as string) ?? null,
      isDemo: vaultRepository.demo,
    };
  }

  async function scanAndLoad(): Promise<void> {
    const knowledge = useKnowledgeStore();
    progress.value = { scanning: true, phase: 'scanning', found: 0, message: '正在扫描知识库…' };
    try {
      const summary = await vaultRepository.scan((found) => {
        progress.value = { scanning: true, phase: 'scanning', found, message: `已发现 ${found} 篇笔记` };
      });
      vaultRepository.baseline();
      await vaultIdb.saveMeta({ lastScanAt: new Date().toISOString() });
      await knowledge.reloadAll();
      await refreshInfo();
      progress.value = {
        scanning: false,
        phase: 'done',
        found: summary.noteCount,
        message: `扫描完成：${summary.noteCount} 篇笔记 · ${summary.folderCount} 个文件夹`,
      };
      setTimeout(() => {
        progress.value = { scanning: false, phase: 'idle', found: 0, message: '' };
      }, 4000);
    } catch (err) {
      progress.value = { scanning: false, phase: 'idle', found: 0, message: '' };
      status.value = 'error';
      error.value = `扫描失败：${(err as Error).message}`;
    }
  }

  /** 应用启动：尝试恢复上次授权的 Vault */
  async function init(): Promise<void> {
    if (status.value !== 'none') return;
    status.value = 'restoring';
    try {
      const restored = await vaultRepository.restore();
      if (restored && !restored.needsPermission) {
        status.value = 'connected';
        await scanAndLoad();
        startWatcher();
      } else if (restored) {
        pendingHandle.value = restored.handle;
        status.value = 'needs-permission';
        showWelcome.value = true;
      } else {
        status.value = 'none';
        const knowledge = useKnowledgeStore();
        await knowledge.reloadAll();
        showWelcome.value = !welcomeDismissed.value;
      }
    } catch (err) {
      status.value = 'error';
      error.value = (err as Error).message;
    }
  }

  /** 用户主动选择 Vault 目录（浏览器目录选择器） */
  async function pickAndConnect(): Promise<boolean> {
    try {
      await vaultRepository.pick();
      status.value = 'connected';
      error.value = '';
      showWelcome.value = false;
      await scanAndLoad();
      startWatcher();
      return true;
    } catch (err) {
      const message = (err as Error).message;
      if (message.includes('abort')) return false; // 用户取消选择
      status.value = 'error';
      error.value = message;
      return false;
    }
  }

  /** 恢复上次 Vault 时补授权 */
  async function grantPermission(): Promise<boolean> {
    const handle = pendingHandle.value;
    if (!handle) return false;
    const granted = await vaultRepository.requestPermission(handle);
    if (granted) {
      pendingHandle.value = null;
      status.value = 'connected';
      showWelcome.value = false;
      await scanAndLoad();
      startWatcher();
    }
    return granted;
  }

  /** 连接浏览器内置演示 Vault（OPFS，真实文件读写，同一套 Repository 代码） */
  async function connectDemo(): Promise<void> {
    try {
      await vaultRepository.connectDemo();
      status.value = 'connected';
      error.value = '';
      showWelcome.value = false;
      await scanAndLoad();
      startWatcher();
    } catch (err) {
      status.value = 'error';
      error.value = `演示 Vault 连接失败：${(err as Error).message}`;
    }
  }

  async function disconnect(): Promise<void> {
    stopWatcher();
    await vaultRepository.disconnect();
    status.value = 'none';
    info.value = null;
    changes.value = [];
    showWelcome.value = true;
    const knowledge = useKnowledgeStore();
    await knowledge.reloadAll();
  }

  async function rescan(): Promise<void> {
    if (!vaultRepository.connected) return;
    await scanAndLoad();
  }

  /* ---------- Watcher：轮询增量 diff（浏览器无递归 fs watcher） ---------- */

  function startWatcher(): void {
    stopWatcher();
    watcherTimer = setInterval(async () => {
      if (!vaultRepository.connected) return;
      try {
        const events = await knowledgeService.refreshVault();
        if (events.length) {
          changes.value = [...events, ...changes.value].slice(0, 30);
          await scanAndLoad();
        }
      } catch {
        // 权限丢失 / 目录被移除，下轮再试
      }
    }, WATCHER_INTERVAL);
  }

  function stopWatcher(): void {
    if (watcherTimer) {
      clearInterval(watcherTimer);
      watcherTimer = null;
    }
  }

  function dismissWelcome(): void {
    welcomeDismissed.value = true;
    showWelcome.value = false;
    localStorage.setItem('om-welcome-dismissed', '1');
  }

  return {
    status,
    error,
    info,
    progress,
    changes,
    showWelcome,
    pendingHandle,
    init,
    pickAndConnect,
    grantPermission,
    connectDemo,
    disconnect,
    rescan,
    startWatcher,
    stopWatcher,
    dismissWelcome,
  };
});
