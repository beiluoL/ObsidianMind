import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { Note, ServiceHealth, VaultNode } from '@/types/knowledge';
import { knowledgeService } from '@/services/knowledgeService';
import { searchService } from '@/services/searchService';

/** 全局知识库状态：文件树、笔记、服务健康 */
export const useKnowledgeStore = defineStore('knowledge', () => {
  const tree = ref<VaultNode | null>(null);
  const notes = ref<Note[]>([]);
  const currentNote = ref<Note | null>(null);
  const expandedFolders = ref<Set<string>>(new Set());
  const recentNoteIds = ref<string[]>(JSON.parse(localStorage.getItem('om-recent-notes') ?? '[]') as string[]);
  const favoriteNoteIds = ref<string[]>(JSON.parse(localStorage.getItem('om-favorite-notes') ?? '[]') as string[]);
  const health = ref<ServiceHealth | null>(null);

  async function loadTree(): Promise<void> {
    tree.value = await knowledgeService.getFileTree();
  }

  /** 默认展开：根节点 + 顶层文件夹 */
  function applyDefaultExpansion(): void {
    if (tree.value?.children?.length) {
      const firstLevel = tree.value.children.filter((c) => c.type === 'folder').map((c) => c.id);
      expandedFolders.value = new Set(['', ...firstLevel]);
    }
  }

  async function loadNotes(): Promise<void> {
    notes.value = await knowledgeService.getAllNotes();
  }

  /** Vault 扫描 / 增量刷新后整体重载（保留仍然有效的展开状态） */
  async function reloadAll(): Promise<void> {
    const keepExpanded = expandedFolders.value;
    await Promise.all([loadTree(), loadNotes()]);

    // 只保留新树中真实存在的展开项（根节点恒展开）；文件夹全部失效（如切换 Vault）则用默认展开
    const folderIds = new Set(collectFolderIds(tree.value));
    const valid = [...keepExpanded].filter((id) => id !== '' && folderIds.has(id));
    if (valid.length) {
      expandedFolders.value = new Set(['', ...valid]);
    } else {
      applyDefaultExpansion();
    }

    // 清理已失效的最近/收藏引用
    const ids = new Set(notes.value.map((n) => n.id));
    recentNoteIds.value = recentNoteIds.value.filter((id) => ids.has(id));
    favoriteNoteIds.value = favoriteNoteIds.value.filter((id) => ids.has(id));
    persistLists();
    // 当前笔记被删除则清空
    if (currentNote.value && !ids.has(currentNote.value.id)) {
      currentNote.value = null;
    }
  }

  function collectFolderIds(node: VaultNode | null): string[] {
    if (!node?.children) return [];
    return node.children.flatMap((c) => (c.type === 'folder' ? [c.id, ...collectFolderIds(c)] : []));
  }

  async function loadHealth(): Promise<void> {
    health.value = await searchService.getHealth();
  }

  async function openNote(noteId: string): Promise<Note | null> {
    const note = await knowledgeService.getNoteById(noteId);
    if (note) {
      currentNote.value = note;
      if (!recentNoteIds.value.includes(noteId)) {
        recentNoteIds.value = [noteId, ...recentNoteIds.value].slice(0, 8);
        persistLists();
      }
    }
    return note;
  }

  function toggleFolder(folderId: string): void {
    const set = expandedFolders.value;
    if (set.has(folderId)) {
      set.delete(folderId);
    } else {
      set.add(folderId);
    }
    expandedFolders.value = new Set(set);
  }

  function toggleFavorite(noteId: string): void {
    const list = favoriteNoteIds.value;
    favoriteNoteIds.value = list.includes(noteId) ? list.filter((id) => id !== noteId) : [noteId, ...list];
    persistLists();
  }

  function persistLists(): void {
    localStorage.setItem('om-recent-notes', JSON.stringify(recentNoteIds.value));
    localStorage.setItem('om-favorite-notes', JSON.stringify(favoriteNoteIds.value));
  }

  function isFavorite(noteId: string): boolean {
    return favoriteNoteIds.value.includes(noteId);
  }

  /** 当前是否工作在真实 Vault 模式 */
  function isVaultNote(): boolean {
    return knowledgeService.isVaultConnected();
  }

  /** 文件变更后同步元数据（mtime / updatedAt），不动正文 */
  function touchNote(noteId: string, mtime: number | undefined): void {
    const meta = notes.value.find((n) => n.id === noteId);
    if (meta && mtime !== undefined) {
      meta.mtime = mtime;
      const d = new Date(mtime);
      const pad = (v: number): string => String(v).padStart(2, '0');
      meta.updatedAt = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }
  }

  /** 最近修改（真实数据按 mtime 排序） */
  const recentEdited = (): Note[] =>
    [...notes.value].sort((x, y) => (y.mtime ?? 0) - (x.mtime ?? 0) || (x.updatedAt < y.updatedAt ? 1 : -1)).slice(0, 5);

  const recentNotes = (): Note[] =>
    recentNoteIds.value
      .map((id) => notes.value.find((note) => note.id === id))
      .filter((note): note is Note => note !== undefined);

  const favoriteNotes = (): Note[] =>
    favoriteNoteIds.value
      .map((id) => notes.value.find((note) => note.id === id))
      .filter((note): note is Note => note !== undefined);

  return {
    tree,
    notes,
    currentNote,
    expandedFolders,
    recentNoteIds,
    favoriteNoteIds,
    health,
    loadTree,
    loadNotes,
    loadHealth,
    reloadAll,
    openNote,
    toggleFolder,
    toggleFavorite,
    isFavorite,
    isVaultNote,
    touchNote,
    recentEdited,
    recentNotes,
    favoriteNotes,
  };
});
