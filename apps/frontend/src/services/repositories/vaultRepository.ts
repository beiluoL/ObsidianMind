import type { VaultNode } from '@/types/knowledge';
import type { NoteLink, VaultChangeEvent, VaultFileMeta } from '@/types/vault';
import type { KnowledgeGraphData, GraphTopic, GraphEdge } from '@/types/knowledge';
import { parseMarkdown, resolveTitle } from '../fs/markdown';
import { vaultIdb } from '../fs/idb';

/**
 * Vault Repository —— 本地文件系统访问的唯一边界。
 *
 * 面向 FileSystemDirectoryHandle 编程：真实磁盘 Vault（用户授权）与
 * OPFS 演示 Vault 共用同一代码路径。未来接入 Node/Electron 后端时，
 * 只需替换本层实现（LocalFileRepository → RemoteRepository）。
 */

export class VaultNotFoundError extends Error {}
export class VaultPermissionError extends Error {}
export class VaultWriteError extends Error {}

export interface ScanSummary {
  noteCount: number;
  folderCount: number;
}

interface ContentCache {
  content: string;
  mtime: number;
}

/** 演示 Vault 播种文件（与 tests/fixtures/demo-vault 同源，?raw 导入） */
import ragMd from '../../../../../tests/fixtures/demo-vault/AI/RAG.md?raw';
import embeddingMd from '../../../../../tests/fixtures/demo-vault/AI/Embedding.md?raw';
import milvusMd from '../../../../../tests/fixtures/demo-vault/AI/Milvus.md?raw';
import llmMd from '../../../../../tests/fixtures/demo-vault/AI/LLM.md?raw';
import agentMd from '../../../../../tests/fixtures/demo-vault/AI/Agent.md?raw';
import transformerMd from '../../../../../tests/fixtures/demo-vault/AI/Transformer.md?raw';
import jvmMd from '../../../../../tests/fixtures/demo-vault/Java/JVM 内存结构.md?raw';
import g1Md from '../../../../../tests/fixtures/demo-vault/Java/G1 垃圾回收器.md?raw';
import gcRootsMd from '../../../../../tests/fixtures/demo-vault/Java/GC Roots.md?raw';
import concurrentMd from '../../../../../tests/fixtures/demo-vault/Java/并发编程.md?raw';
import casMd from '../../../../../tests/fixtures/demo-vault/Java/CAS.md?raw';
import springMd from '../../../../../tests/fixtures/demo-vault/Java/Spring IOC 与 AOP.md?raw';
import projectMd from '../../../../../tests/fixtures/demo-vault/Projects/ObsidianMind.md?raw';
import readingMd from '../../../../../tests/fixtures/demo-vault/01-个人成长/读书笔记方法.md?raw';

const DEMO_FILES: Record<string, string> = {
  'AI/RAG.md': ragMd,
  'AI/Embedding.md': embeddingMd,
  'AI/Milvus.md': milvusMd,
  'AI/LLM.md': llmMd,
  'AI/Agent.md': agentMd,
  'AI/Transformer.md': transformerMd,
  'Java/JVM 内存结构.md': jvmMd,
  'Java/G1 垃圾回收器.md': g1Md,
  'Java/GC Roots.md': gcRootsMd,
  'Java/并发编程.md': concurrentMd,
  'Java/CAS.md': casMd,
  'Java/Spring IOC 与 AOP.md': springMd,
  'Projects/ObsidianMind.md': projectMd,
  '01-个人成长/读书笔记方法.md': readingMd,
};

const MARKER_FILE = '.obsidianmind-demo-vault';

/** 按顶层目录给图谱节点分类 */
function categoryOf(topFolder: string): GraphTopic['category'] {
  if (/java|jvm/i.test(topFolder)) return 'java';
  if (/ai|llm|技术|学习/i.test(topFolder)) return 'ai';
  if (/project|项目/i.test(topFolder)) return 'project';
  if (/media|自媒体|成长/i.test(topFolder)) return 'media';
  return 'root';
}

class VaultRepository {
  private root: FileSystemDirectoryHandle | null = null;
  private isDemoVault = false;
  /** md 文件元数据：path → meta */
  private files = new Map<string, VaultFileMeta>();
  private folderCount = 0;
  private contentCache = new Map<string, ContentCache>();
  private lastScanPaths: Map<string, number> = new Map();

  get connected(): boolean {
    return this.root !== null;
  }

  get demo(): boolean {
    return this.isDemoVault;
  }

  get displayName(): string {
    return this.root?.name || (this.isDemoVault ? '演示 Vault' : 'Obsidian Vault');
  }

  get supported(): boolean {
    return typeof window !== 'undefined' && 'showDirectoryPicker' in window;
  }

  /* ---------- 连接 ---------- */

  /** 用户主动授权选择 Vault 目录（绝不全盘扫描） */
  async pick(): Promise<FileSystemDirectoryHandle> {
    if (!this.supported) {
      throw new Error('当前浏览器不支持 File System Access API，请使用 Chrome / Edge');
    }
    const picker = window.showDirectoryPicker;
    if (!picker) throw new Error('当前浏览器不支持目录选择');
    const handle = await picker.call(window, { mode: 'readwrite', id: 'obsidianmind-vault' });
    await this.connect(handle, false);
    return handle;
  }

  async connect(handle: FileSystemDirectoryHandle, demo: boolean): Promise<void> {
    // 验证可读权限
    const perm = await handle.queryPermission?.({ mode: 'readwrite' });
    if (perm === 'denied') throw new VaultPermissionError('目录访问被拒绝');
    this.root = handle;
    this.isDemoVault = demo;
    this.files.clear();
    this.contentCache.clear();
    if (!demo) await vaultIdb.saveHandle(handle);
  }

  /** 启动时恢复上次授权的 Vault（可能需要用户点击重新授权） */
  async restore(): Promise<{ handle: FileSystemDirectoryHandle; needsPermission: boolean } | null> {
    const handle = await vaultIdb.loadHandle();
    if (!handle) return null;
    const perm = await handle.queryPermission?.({ mode: 'readwrite' });
    if (perm === 'granted') {
      await this.connect(handle, false);
      return { handle, needsPermission: false };
    }
    return { handle, needsPermission: true };
  }

  /** 在用户手势上下文中请求权限 */
  async requestPermission(handle: FileSystemDirectoryHandle): Promise<boolean> {
    const perm = await handle.requestPermission?.({ mode: 'readwrite' });
    if (perm === 'granted') {
      await this.connect(handle, false);
      return true;
    }
    return false;
  }

  async disconnect(): Promise<void> {
    this.root = null;
    this.isDemoVault = false;
    this.files.clear();
    this.contentCache.clear();
    await vaultIdb.clear();
  }

  /* ---------- 演示 Vault（OPFS，真实文件读写） ---------- */

  async connectDemo(): Promise<void> {
    const root = await navigator.storage.getDirectory();
    await this.seedDemoIfNeeded(root);
    await this.connect(root, true);
  }

  private async seedDemoIfNeeded(root: FileSystemDirectoryHandle): Promise<void> {
    try {
      await root.getFileHandle(MARKER_FILE);
      return; // 已播种
    } catch {
      // 首次播种
    }
    for (const [path, content] of Object.entries(DEMO_FILES)) {
      const segments = path.split('/');
      const fileName = segments.pop() as string;
      let dir = root;
      for (const seg of segments) {
        dir = await dir.getDirectoryHandle(seg, { create: true });
      }
      const fileHandle = await dir.getFileHandle(fileName, { create: true });
      const writable = await fileHandle.createWritable();
      await writable.write(content);
      await writable.close();
    }
    const marker = await root.getFileHandle(MARKER_FILE, { create: true });
    const w = await marker.createWritable();
    await w.write('demo');
    await w.close();
  }

  /* ---------- 内部路径工具 ---------- */

  private assertRoot(): FileSystemDirectoryHandle {
    if (!this.root) throw new VaultNotFoundError('未连接 Vault');
    return this.root;
  }

  private async getFileHandle(path: string): Promise<FileSystemFileHandle> {
    const root = this.assertRoot();
    const segments = path.split('/').filter(Boolean);
    const fileName = segments.pop();
    if (!fileName) throw new VaultNotFoundError(`无效路径: ${path}`);
    let dir = root;
    for (const seg of segments) {
      dir = await dir.getDirectoryHandle(seg);
    }
    return dir.getFileHandle(fileName);
  }

  /* ---------- 扫描 ---------- */

  async scan(onProgress?: (found: number) => void): Promise<ScanSummary> {
    const root = this.assertRoot();
    const files = new Map<string, VaultFileMeta>();
    let folderCount = 0;
    let found = 0;

    const walk = async (dir: FileSystemDirectoryHandle, prefix: string): Promise<void> => {
      for await (const [name, handle] of dir.entries()) {
        if (name.startsWith('.')) continue; // 跳过隐藏文件（.obsidian 等）
        const path = prefix ? `${prefix}/${name}` : name;
        if (handle.kind === 'directory') {
          folderCount++;
          await walk(handle as FileSystemDirectoryHandle, path);
        } else if (name.toLowerCase().endsWith('.md')) {
          found++;
          onProgress?.(found);
          const file = await (handle as FileSystemFileHandle).getFile();
          const content = await file.text();
          const parsed = parseMarkdown(content);
          files.set(path, {
            path,
            name,
            title: resolveTitle(name, parsed),
            extension: 'md',
            size: file.size,
            modifiedAt: file.lastModified,
            tags: parsed.tags,
            frontmatter: parsed.frontmatter,
          });
        }
      }
    };

    await walk(root, '');
    this.files = files;
    this.folderCount = folderCount;
    return { noteCount: files.size, folderCount };
  }

  /** 扫描后建立增量对比基线（watcher 首次 tick 前必须调用） */
  baseline(): void {
    this.lastScanPaths = new Map([...this.files.entries()].map(([p, m]) => [p, m.modifiedAt]));
  }

  /* ---------- 文件树 ---------- */

  buildTree(): VaultNode {
    this.assertRoot();
    const rootName = this.root?.name || (this.isDemoVault ? '演示 Vault' : 'Obsidian Vault');
    const rootNode: VaultNode = { id: '', name: rootName, type: 'folder', children: [] };

    const ensureFolder = (path: string): VaultNode => {
      let node = rootNode;
      let acc = '';
      for (const seg of path.split('/')) {
        acc = acc ? `${acc}/${seg}` : seg;
        node.children ??= [];
        let child = node.children.find((c) => c.type === 'folder' && c.id === acc);
        if (!child) {
          child = { id: acc, name: seg, type: 'folder', children: [] };
          node.children.push(child);
        }
        node = child;
      }
      return node;
    };

    const sortedPaths = [...this.files.keys()].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'));
    for (const path of sortedPaths) {
      const segments = path.split('/');
      const fileName = segments.pop() as string;
      const folderPath = segments.join('/');
      const parentNode = folderPath ? ensureFolder(folderPath) : rootNode;
      parentNode.children ??= [];
      parentNode.children.push({
        id: path,
        name: fileName.replace(/\.md$/i, ''),
        type: 'note',
        noteId: path,
      });
    }

    // 文件夹排前、字母序
    const sortNode = (node: VaultNode): void => {
      if (!node.children) return;
      node.children.sort((a, b) => {
        if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
        return a.name.localeCompare(b.name, 'zh-Hans-CN');
      });
      node.children.forEach(sortNode);
    };
    sortNode(rootNode);
    return rootNode;
  }

  /* ---------- 读取 / 写入 ---------- */

  async readNote(path: string): Promise<{
    path: string;
    title: string;
    content: string;
    folder: string;
    tags: string[];
    modifiedAt: number;
  }> {
    const fileHandle = await this.getFileHandle(path);
    const file = await fileHandle.getFile();
    const content = await file.text();
    this.contentCache.set(path, { content, mtime: file.lastModified });
    const parsed = parseMarkdown(content);
    const segments = path.split('/');
    segments.pop();
    return {
      path,
      title: resolveTitle(segments[segments.length - 1] ? file.name : file.name, parsed),
      content: parsed.body,
      folder: segments.join('/'),
      tags: parsed.tags,
      modifiedAt: file.lastModified,
    };
  }

  async readRaw(path: string): Promise<string> {
    const fileHandle = await this.getFileHandle(path);
    const file = await fileHandle.getFile();
    return file.text();
  }

  /** 保存写回原始 Markdown 文件，返回新 mtime */
  async saveNote(path: string, content: string): Promise<number> {
    const fileHandle = await this.getFileHandle(path);
    try {
      const writable = await fileHandle.createWritable();
      await writable.write(content);
      await writable.close();
    } catch (err) {
      throw new VaultWriteError(`保存失败: ${(err as Error).message}`);
    }
    const file = await fileHandle.getFile();
    const cached = this.contentCache.get(path);
    if (cached) {
      cached.content = content;
      cached.mtime = file.lastModified;
    }
    const meta = this.files.get(path);
    if (meta) meta.modifiedAt = file.lastModified;
    return file.lastModified;
  }

  /** 外部修改检测：对比已知 mtime */
  async checkExternalChange(
    path: string,
    knownMtime: number,
  ): Promise<{ changed: boolean; content?: string; mtime?: number }> {
    try {
      const fileHandle = await this.getFileHandle(path);
      const file = await fileHandle.getFile();
      if (file.lastModified !== knownMtime) {
        const content = await file.text();
        return { changed: true, content, mtime: file.lastModified };
      }
      return { changed: false };
    } catch {
      return { changed: false }; // 文件暂时不可读（Obsidian 写入中），不误报
    }
  }

  /* ---------- 增量刷新（轮询 diff，代替递归 watcher） ---------- */

  async refresh(): Promise<VaultChangeEvent[]> {
    await this.scan();
    const events: VaultChangeEvent[] = [];
    const current = new Map([...this.files.entries()].map(([p, m]) => [p, m.modifiedAt]));

    for (const [path, mtime] of current) {
      const old = this.lastScanPaths.get(path);
      if (old === undefined) events.push({ type: 'create', path });
      else if (old !== mtime) events.push({ type: 'modify', path });
    }
    for (const path of this.lastScanPaths.keys()) {
      if (!current.has(path)) events.push({ type: 'delete', path });
    }
    this.lastScanPaths = current;
    return events;
  }  /* ---------- 统计 / 链接 / 图谱 ---------- */

  getStats(): { noteCount: number; folderCount: number; tagCount: number } {
    const tags = new Set<string>();
    for (const meta of this.files.values()) meta.tags.forEach((t) => tags.add(t));
    return { noteCount: this.files.size, folderCount: this.folderCount, tagCount: tags.size };
  }

  /** 全部笔记元数据（不含正文） */
  getAllMetas(): VaultFileMeta[] {
    return [...this.files.values()].sort((a, b) => b.modifiedAt - a.modifiedAt);
  }

  getMeta(path: string): VaultFileMeta | undefined {
    return this.files.get(path);
  }

  /**
   * 解析全部 [[Wiki Link]]。
   * 内容按需读取（带缓存），链接目标按「文件名（去 .md）」匹配。
   */
  async resolveLinks(onProgress?: (done: number, total: number) => void): Promise<NoteLink[]> {
    const byName = new Map<string, string>();
    for (const meta of this.files.values()) {
      byName.set(meta.name.replace(/\.md$/i, ''), meta.path);
      byName.set(meta.title, meta.path);
    }

    const links: NoteLink[] = [];
    const paths = [...this.files.keys()];
    let done = 0;
    for (const path of paths) {
      const cached = this.contentCache.get(path);
      let content = cached?.content;
      if (content === undefined) {
        content = await this.readRaw(path);
        try {
          const file = await (await this.getFileHandle(path)).getFile();
          this.contentCache.set(path, { content, mtime: file.lastModified });
        } catch {
          this.contentCache.set(path, { content, mtime: this.files.get(path)?.modifiedAt ?? 0 });
        }
      }
      const parsed = parseMarkdown(content);
      for (const target of parsed.wikiLinks) {
        const lastSeg = target.split('/').pop() ?? target;
        links.push({ sourcePath: path, targetPath: byName.get(target) ?? byName.get(lastSeg) ?? '', targetName: target });
      }
      done++;
      onProgress?.(done, paths.length);
    }
    return links;
  }

  /** 反向链接：哪些笔记链接到 target */
  backlinksOf(targetPath: string): { sourcePath: string; sourceTitle: string; context: string }[] {
    const result: { sourcePath: string; sourceTitle: string; context: string }[] = [];
    const targetMeta = this.files.get(targetPath);
    if (!targetMeta) return result;
    const baseName = targetMeta.name.replace(/\.md$/i, '');
    for (const [path, cached] of this.contentCache) {
      if (path === targetPath) continue;
      const regex = new RegExp(`\\[\\[([^\\]|#]*${this.escapeReg(baseName)}[^\\]|#]*)(?:\\|[^\\]]*)?\\]\\]`);
      const match = cached.content.match(regex);
      if (match) {
        const meta = this.files.get(path);
        result.push({
          sourcePath: path,
          sourceTitle: meta?.title ?? path,
          context: cached.content.slice(Math.max(0, (match.index ?? 0) - 40), (match.index ?? 0) + 60).replace(/\n/g, ' '),
        });
      }
    }
    return result;
  }

  private escapeReg(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  /** 从真实 wiki links 构建知识图谱 */
  async getGraph(): Promise<KnowledgeGraphData> {
    await this.resolveLinks();
    const links = await this.resolveLinks();

    const topics: GraphTopic[] = [];
    const topicIds = new Set<string>();
    const addTopic = (id: string, label: string, category: GraphTopic['category']): void => {
      if (topicIds.has(id)) return;
      topicIds.add(id);
      topics.push({ id, label, category });
    };

    for (const meta of this.files.values()) {
      const top = meta.path.split('/')[0] ?? '';
      addTopic(meta.path, meta.title, categoryOf(top));
    }

    const edges: GraphEdge[] = [];
    for (const link of links) {
      if (!link.targetPath) {
        // 未解析的链接目标 → 虚拟节点
        const virtualId = `virtual:${link.targetName}`;
        addTopic(virtualId, link.targetName, 'root');
        edges.push({ source: link.sourcePath, target: virtualId, label: '链接' });
      } else {
        edges.push({ source: link.sourcePath, target: link.targetPath, label: '链接' });
      }
    }
    return { topics, edges };
  }
}

export const vaultRepository = new VaultRepository();
