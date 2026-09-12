/** Vault 领域模型 —— 本地知识库（Phase 2：真实文件系统） */

/** 已连接的 Vault 元信息 */
export interface VaultInfo {
  name: string;
  /** 显示用路径（浏览器环境无法获得绝对路径，展示授权目录名） */
  path: string;
  connected: boolean;
  noteCount: number;
  folderCount: number;
  tagCount: number;
  linkCount: number;
  lastScanAt: string | null;
  /** true 表示 OPFS 内置演示 Vault */
  isDemo: boolean;
}

/** Vault 内文件元数据（不含正文，正文按需懒加载） */
export interface VaultFileMeta {
  /** vault 相对路径，如 "AI/RAG.md"，作为 note id */
  path: string;
  name: string;
  title: string;
  extension: string;
  size: number;
  modifiedAt: number;
  tags: string[];
  /** frontmatter 原始数据 */
  frontmatter: Record<string, string>;
}

/** 扫描进度 */
export interface ScanProgress {
  scanning: boolean;
  phase: 'idle' | 'scanning' | 'reading' | 'done';
  found: number;
  message: string;
}

/** 文件监听事件 */
export type VaultChangeType = 'create' | 'modify' | 'delete' | 'rename';
export interface VaultChangeEvent {
  type: VaultChangeType;
  path: string;
  oldPath?: string;
}

/** 笔记之间的双向链接 */
export interface NoteLink {
  sourcePath: string;
  /** 解析后的目标笔记 path（无法解析时为空） */
  targetPath: string;
  targetName: string;
}

/** 未来 RAG：Document / Chunk 类型预留（本阶段不实现逻辑） */
export interface Document {
  id: string;
  notePath: string;
  path: string;
  content: string;
  metadata: Record<string, unknown>;
}

export interface Chunk {
  id: string;
  documentId: string;
  content: string;
  index: number;
  metadata: Record<string, unknown>;
}

/** 外部修改冲突状态 */
export interface ConflictState {
  path: string;
  /** 磁盘上的最新内容 */
  diskContent: string;
  detectedAt: string;
}
