import type { KnowledgeGraphData } from '@/types/knowledge';

/** Mock 知识图谱 —— 节点为知识主题，边为语义关系 */
export const MOCK_GRAPH: KnowledgeGraphData = {
  topics: [
    { id: 't-java', label: 'Java', category: 'java' },
    { id: 't-jvm', label: 'JVM', category: 'java' },
    { id: 't-gc', label: 'GC', category: 'java' },
    { id: 't-g1', label: 'G1', category: 'java' },
    { id: 't-conc', label: '并发编程', category: 'java' },
    { id: 't-cas', label: 'CAS', category: 'java' },
    { id: 't-spring', label: 'Spring', category: 'java' },
    { id: 't-ai', label: 'AI', category: 'ai' },
    { id: 't-llm', label: 'LLM', category: 'ai' },
    { id: 't-tf', label: 'Transformer', category: 'ai' },
    { id: 't-emb', label: 'Embedding', category: 'ai' },
    { id: 't-rag', label: 'RAG', category: 'ai' },
    { id: 't-milvus', label: 'Milvus', category: 'ai' },
    { id: 't-agent', label: 'Agent', category: 'ai' },
    { id: 't-om', label: 'ObsidianMind', category: 'project' },
    { id: 't-media', label: '自媒体', category: 'media' },
  ],
  edges: [
    { source: 't-java', target: 't-jvm', label: '运行于' },
    { source: 't-java', target: 't-conc', label: '包含' },
    { source: 't-java', target: 't-spring', label: '生态框架' },
    { source: 't-jvm', target: 't-gc', label: '依赖' },
    { source: 't-gc', target: 't-g1', label: '实现' },
    { source: 't-conc', target: 't-cas', label: '核心原语' },
    { source: 't-ai', target: 't-llm', label: '核心' },
    { source: 't-llm', target: 't-tf', label: '基于' },
    { source: 't-ai', target: 't-rag', label: '应用' },
    { source: 't-rag', target: 't-emb', label: '使用' },
    { source: 't-rag', target: 't-milvus', label: '检索于' },
    { source: 't-ai', target: 't-agent', label: '演进' },
    { source: 't-agent', target: 't-llm', label: '驱动' },
    { source: 't-om', target: 't-rag', label: '构建于' },
    { source: 't-om', target: 't-milvus', label: '索引' },
    { source: 't-java', target: 't-om', label: '后端栈' },
    { source: 't-media', target: 't-llm', label: '科普选题' },
  ],
};

/** 节点 → 关联笔记（点开节点右侧展示） */
export const GRAPH_NODE_NOTES: Record<string, { noteId: string; title: string }[]> = {
  't-java': [
    { noteId: 'note-ioc', title: 'Spring IOC.md' },
    { noteId: 'note-jvm-memory', title: 'JVM 内存结构.md' },
    { noteId: 'note-synchronized', title: 'synchronized.md' },
  ],
  't-jvm': [
    { noteId: 'note-jvm-memory', title: 'JVM 内存结构.md' },
    { noteId: 'note-g1', title: 'G1 垃圾回收器.md' },
    { noteId: 'note-gc-roots', title: 'GC Roots.md' },
  ],
  't-gc': [
    { noteId: 'note-g1', title: 'G1 垃圾回收器.md' },
    { noteId: 'note-gc-roots', title: 'GC Roots.md' },
  ],
  't-g1': [{ noteId: 'note-g1', title: 'G1 垃圾回收器.md' }],
  't-conc': [
    { noteId: 'note-synchronized', title: 'synchronized.md' },
    { noteId: 'note-volatile', title: 'volatile.md' },
    { noteId: 'note-cas', title: 'CAS.md' },
  ],
  't-cas': [{ noteId: 'note-cas', title: 'CAS.md' }],
  't-spring': [
    { noteId: 'note-ioc', title: 'Spring IOC.md' },
    { noteId: 'note-aop', title: 'Spring AOP.md' },
  ],
  't-ai': [
    { noteId: 'note-llm', title: '大模型基础.md' },
    { noteId: 'note-rag', title: 'RAG 技术原理.md' },
    { noteId: 'note-agent', title: 'Agent 入门.md' },
  ],
  't-llm': [
    { noteId: 'note-llm', title: '大模型基础.md' },
    { noteId: 'note-transformer', title: 'Transformer.md' },
  ],
  't-tf': [{ noteId: 'note-transformer', title: 'Transformer.md' }],
  't-emb': [{ noteId: 'note-embedding', title: 'Embedding.md' }],
  't-rag': [
    { noteId: 'note-rag', title: 'RAG 技术原理.md' },
    { noteId: 'note-embedding', title: 'Embedding.md' },
    { noteId: 'note-milvus', title: 'Milvus 向量数据库.md' },
  ],
  't-milvus': [{ noteId: 'note-milvus', title: 'Milvus 向量数据库.md' }],
  't-agent': [{ noteId: 'note-agent', title: 'Agent 入门.md' }],
  't-om': [{ noteId: 'note-project-om', title: 'ObsidianMind 设计构思.md' }],
  't-media': [
    { noteId: 'note-media-java', title: '选题库.md' },
    { noteId: 'note-media-token', title: 'Token 动画脚本.md' },
  ],
};
