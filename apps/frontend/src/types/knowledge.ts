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

/** Phase 5 RAG 回答的引用来源（后端 CitationView；index 与正文 [SRC-n] 数字一致） */
export interface RagCitation {
  index: number;
  sourceId: string;
  title: string;
  path: string;
  heading: string;
  snippet: string;
  score: number;
}

/** Phase 5 RAG 性能指标（后端 RagMetrics） */
export interface RagStreamMetrics {
  retrievalMs: number;
  contextMs: number;
  firstTokenMs: number;
  llmMs: number;
  totalMs: number;
  contextChunks: number;
  contextChars: number;
  promptChars: number;
  contextTruncated: boolean;
}

/** done 事件负载（后端 RagCompletion） */
export interface RagCompletionPayload {
  content: string;
  citedSourceIds: string[];
  sources: RagCitation[];
  metrics: RagStreamMetrics;
  noContext: boolean;
}

/** 流式事件错误负载 */
export interface RagStreamError {
  code: string;
  message: string;
}

/** 助手消息状态机：不用 content === '' 判断状态 */
export type MessageStatus = 'streaming' | 'complete' | 'error' | 'cancelled';

/** 一条聊天消息 */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** 引用来源（assistant 专用；citation 事件先于 token 到达，流式期间即可点击） */
  sources: RagCitation[];
  /** assistant 消息状态（user 消息恒为 complete） */
  status: MessageStatus;
  errorCode?: string;
  errorMessage?: string;
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

/** Phase 3 知识索引 per-file 错误 */
export interface IndexDocError {
  documentId: string;
  code: string;
  message: string;
}

/** Phase 3 知识索引结果（POST /api/v1/index/run） */
export interface IndexResult {
  total: number;
  indexed: number;
  updated: number;
  skipped: number;
  deleted: number;
  failed: number;
  chunkCount: number;
  elapsedMs: number;
  errors: IndexDocError[];
}

/** Phase 4 语义检索 Source（后端 RetrievalService.Source） */
export interface SemanticSource {
  title: string;
  path: string;
  heading: string;
  snippet: string;
  score: number;
  documentId: string;
  chunkIndex: number;
}

/** Phase 4 语义检索响应（POST /api/v1/search/semantic） */
export interface SemanticSearchResponse {
  query: string;
  sources: SemanticSource[];
  elapsedMs: number;
}

/** Phase 6 检索模式（后端 RetrievalMode 枚举；UI 显示为 混合/语义/关键词） */
export type RetrievalMode = 'VECTOR' | 'KEYWORD' | 'HYBRID';

/** Phase 6 混合检索响应（POST /api/v1/search；Source 形状与语义检索兼容） */
export interface HybridSearchResponse {
  query: string;
  requestedMode: RetrievalMode;
  effectiveMode: RetrievalMode;
  /** 降级记录（空数组 = 无降级），如 VECTOR_FALLBACK_TO_KEYWORD */
  fallbacks: string[];
  /** Reranker 状态：APPLIED / NOT_CONFIGURED / SKIPPED_EMPTY */
  rerankerStatus: string;
  elapsedMs: number;
  sources: SemanticSource[];
}

/** 检索配置（GET /api/v1/search/config，只读，Advanced Retrieval 面板数据源） */
export interface RetrievalConfig {
  defaultMode: string;
  defaultTopK: number;
  maxTopK: number;
  vectorCandidates: number;
  keywordCandidates: number;
  rrfK: number;
  maxPerDocument: number;
  scoreThreshold: number;
  rerankerEnabled: boolean;
  rerankerTopN: number;
  debugEnabled: boolean;
}

/** Debug trace 里的单条候选（null = 该分数通道未产出） */
export interface RetrievalTraceItem {
  rank: number;
  chunkId: string;
  documentId: string;
  title: string;
  heading: string;
  vectorScore: number | null;
  keywordScore: number | null;
  rrfScore: number | null;
  rerankScore: number | null;
  finalScore: number | null;
}

/** Phase 6 Retrieval Debug 响应（POST /api/v1/search/debug，仅开发模式） */
export interface RetrievalDebugTrace {
  requestedMode: string;
  effectiveMode: string;
  fallbacks: string[];
  rerankerStatus: string;
  vector: RetrievalTraceItem[];
  keyword: RetrievalTraceItem[];
  fused: RetrievalTraceItem[];
  finalResults: RetrievalTraceItem[];
  timing: { vectorMs: number; keywordMs: number; fusionMs: number; rerankerMs: number; totalMs: number };
}
