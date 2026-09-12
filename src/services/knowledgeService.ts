import type { KnowledgeGraphData, Note, VaultNode } from '@/types/knowledge';
import type { VaultChangeEvent } from '@/types/vault';
import { vaultRepository, VaultNotFoundError } from './repositories/vaultRepository';
import { MOCK_NOTES, MOCK_TREE } from './mock/notes';
import { MOCK_GRAPH } from './mock/graph';

/**
 * 统一知识服务 —— 所有 UI 获取知识数据的唯一入口。
 *
 * 已连接真实 Vault → Repository（本地文件系统）
 * 未连接           → Mock（演示数据）
 *
 * 未来替换为 REST：
 * GET /api/notes  GET /api/notes/:id  PUT /api/notes/:id  GET /api/graph
 */

function formatTime(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const mockNoteById = (id: string): Note | null => MOCK_NOTES.find((note) => note.id === id) ?? null;

export const knowledgeService = {
  /** 是否工作在真实 Vault 模式 */
  isVaultConnected(): boolean {
    return vaultRepository.connected;
  },

  async getFileTree(): Promise<VaultNode> {
    if (vaultRepository.connected) {
      return vaultRepository.buildTree();
    }
    return structuredClone(MOCK_TREE);
  },

  /** 全部笔记元数据（不含正文，正文按需懒加载） */
  async getAllNotes(): Promise<Note[]> {
    if (vaultRepository.connected) {
      return vaultRepository.getAllMetas().map((meta) => ({
        id: meta.path,
        title: meta.title,
        path: meta.path,
        folder: meta.path.split('/').slice(0, -1).join('/'),
        content: '',
        tags: meta.tags,
        updatedAt: formatTime(meta.modifiedAt),
        wordCount: meta.size,
        mtime: meta.modifiedAt,
      }));
    }
    return structuredClone(MOCK_NOTES);
  },

  /** 打开单篇笔记（读取真实正文） */
  async getNoteById(id: string): Promise<Note | null> {
    if (vaultRepository.connected) {
      try {
        const raw = await vaultRepository.readNote(id);
        return {
          id: raw.path,
          title: raw.title,
          path: raw.path,
          folder: raw.folder,
          content: raw.content,
          tags: raw.tags,
          updatedAt: formatTime(raw.modifiedAt),
          wordCount: raw.content.length,
          mtime: raw.modifiedAt,
        };
      } catch (error) {
        if (error instanceof VaultNotFoundError) return null;
        console.error('[ObsidianMind] 读取笔记失败', error);
        return null;
      }
    }
    return mockNoteById(id);
  },

  getNoteTitle(noteId: string): string {
    if (vaultRepository.connected) {
      return vaultRepository.getMeta(noteId)?.title ?? '';
    }
    return MOCK_NOTES.find((note) => note.id === noteId)?.title ?? '';
  },

  /** 保存并写回原始 Markdown 文件 */
  async saveNote(noteId: string, content: string): Promise<number> {
    return vaultRepository.saveNote(noteId, content);
  },

  /** 外部修改检测 */
  async checkExternalChange(
    noteId: string,
    knownMtime: number,
  ): Promise<{ changed: boolean; content?: string; mtime?: number }> {
    if (!vaultRepository.connected) return { changed: false };
    return vaultRepository.checkExternalChange(noteId, knownMtime);
  },

  /** 反向链接 */
  async getBacklinks(notePath: string): Promise<{ sourcePath: string; sourceTitle: string; context: string }[]> {
    if (!vaultRepository.connected) return [];
    // 确保内容缓存已预热
    await vaultRepository.resolveLinks();
    return vaultRepository.backlinksOf(notePath);
  },

  async getGraph(): Promise<KnowledgeGraphData> {
    if (vaultRepository.connected) {
      return vaultRepository.getGraph();
    }
    return structuredClone(MOCK_GRAPH);
  },

  /** 增量刷新（watcher 轮询调用），返回变更事件 */
  async refreshVault(): Promise<VaultChangeEvent[]> {
    return vaultRepository.refresh();
  },

  /** 全量重扫 */
  async rescan(): Promise<void> {
    await vaultRepository.scan();
  },

  async getLinkCount(): Promise<number> {
    if (!vaultRepository.connected) return 0;
    const links = await vaultRepository.resolveLinks();
    return links.length;
  },
};
