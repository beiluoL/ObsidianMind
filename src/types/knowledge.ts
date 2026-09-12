/** 知识库领域模型 —— UI 与数据层的唯一契约 */

/** 文件树节点（文件夹或笔记） */
export interface VaultNode {
  id: string;
  name: string;
  type: 'folder' | 'note';
  children?: VaultNode[];
  noteId?: string;
}

/** 一篇 Markdown 笔记 */
export interface Note {
  id: string;
  title: string;
  path: string;
  folder: string;
  content: string;
  tags: string[];
  updatedAt: string;
  wordCount: number;
  /** 真实 Vault 模式下的文件 mtime（外部修改检测用） */
  mtime?: number;
}

/** 知识图谱节点 */
export interface GraphTopic {
  id: string;
  label: string;
  category: 'java' | 'ai' | 'project' | 'media' | 'root';
}

/** 知识图谱边 */
export interface GraphEdge {
  source: string;
  target: string;
  label: string;
}

/** 知识图谱整体 */
export interface KnowledgeGraphData {
  topics: GraphTopic[];
  edges: GraphEdge[];
}

/** 搜索结果条目 */
export interface SearchResult {
  noteId: string;
  title: string;
  path: string;
  excerpt: string;
  score: number;
  reason: string;
  tags?: string[];
  modifiedAt?: string;
  /** 用于结果高亮的关键词 */
  matchTerms?: string[];
}

/** AI 回答中的引用来源 */
export interface ChatSource {
  noteId: string;
  title: string;
  score: number;
  chunkId: string;
}

/** AI 回答中的相关笔记 */
export interface RelatedNote {
  noteId: string;
  title: string;
  score: number;
  relation: string;
}

/** 一条聊天消息 */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources: ChatSource[];
  relatedNotes: RelatedNote[];
  createdAt: string;
}

/** AI 思考阶段 */
export type AiPhase = 'idle' | 'searching' | 'generating' | 'done' | 'error';

/** 服务连接状态 */
export type ServiceStatus = 'connected' | 'disconnected' | 'ready';

/** 服务健康信息 */
export interface ServiceHealth {
  ollama: ServiceStatus;
  milvus: ServiceStatus;
  embedding: ServiceStatus;
  noteCount: number;
  chunkCount: number;
  connectionCount: number;
  lastIndexedAt: string;
}

/** 索引状态 */
export interface IndexStatus {
  isIndexing: boolean;
  progress: number;
  totalNotes: number;
  indexedNotes: number;
}

/** 模型信息 */
export interface ModelInfo {
  id: string;
  name: string;
  provider: 'ollama' | 'openai' | 'deepseek';
  available: boolean;
}

/** 设置页各分区数据 */
export interface AppSettings {
  llmProvider: string;
  llmModel: string;
  embeddingModel: string;
  milvusHost: string;
  milvusPort: string;
  autoIndex: boolean;
  indexInterval: string;
}
