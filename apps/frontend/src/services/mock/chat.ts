import type { ChatMessage } from '@/types/knowledge';

/**
 * Mock AI 回答库 —— 按问题关键词匹配最合适的预置回答。
 * 未来由 aiService 切换为 POST /api/chat (SSE) 真实实现。
 */

interface MockAnswer {
  keywords: string[];
  content: string;
  sources: { noteId: string; title: string; score: number; chunkId: string }[];
  relatedNotes: { noteId: string; title: string; score: number; relation: string }[];
}

const a = (keywords: string[], content: string, sources: MockAnswer['sources'], related: MockAnswer['relatedNotes']): MockAnswer => ({
  keywords,
  content,
  sources,
  relatedNotes: related,
});

export const MOCK_ANSWERS: MockAnswer[] = [
  a(
    ['rag', '区别', '普通', 'llm'],
    `RAG 和普通 LLM 最大的区别在于：**知识从哪里来**。

**普通 LLM 的回答路径：**

\`\`\`
用户问题 → LLM → 答案
\`\`\`

模型只能依靠训练时"记住"的知识，遇到私有知识或新知识就会幻觉或拒答。

**RAG 的回答路径：**

\`\`\`
用户问题 → 向量检索知识库 → 取回 Top-K 相关片段 → LLM（问题 + 片段） → 带引用的答案
\`\`\`

三个关键收益：

1. **知识可更新**：改文档即可，不用重新训练
2. **答案可追溯**：每句话都能指向来源笔记（见下方引用）
3. **幻觉显著减少**：模型被约束在检索到的上下文内作答

一句话总结：普通 LLM 是"靠记忆答题"，RAG 是"开卷考试"。`,
    [
      { noteId: 'note-rag', title: 'RAG 技术原理.md', score: 0.94, chunkId: 'chunk-12' },
      { noteId: 'note-embedding', title: 'Embedding.md', score: 0.89, chunkId: 'chunk-03' },
      { noteId: 'note-milvus', title: 'Milvus 向量数据库.md', score: 0.84, chunkId: 'chunk-07' },
    ],
    [
      { noteId: 'note-embedding', title: 'Embedding.md', score: 0.91, relation: 'RAG → 使用 → Embedding' },
      { noteId: 'note-milvus', title: 'Milvus 向量数据库.md', score: 0.87, relation: 'RAG → 检索于 → Milvus' },
      { noteId: 'note-llm', title: '大模型基础.md', score: 0.82, relation: 'RAG → 依赖 → LLM' },
      { noteId: 'note-transformer', title: 'Transformer.md', score: 0.74, relation: 'LLM → 基于 → Transformer' },
    ],
  ),
  a(
    ['rag', '流程', '工作', '怎么', '如何', '原理'],
    `RAG 的工作流程分为**离线索引**和**在线问答**两条链路。

**① 离线：建索引**

\`\`\`
文档 → 分块 Chunking → Embedding 向量化 → 写入 Milvus
\`\`\`

**② 在线：问答**

\`\`\`
用户问题 → Embedding → 向量相似度检索 → Top-K 片段
→ 拼装 Prompt（问题 + 片段 + 指令）
→ LLM 生成 → 答案 + 引用来源
\`\`\`

你知识库中的关键笔记已经覆盖了每个环节，点击下方来源可以直接跳转对照阅读。`,
    [
      { noteId: 'note-rag', title: 'RAG 技术原理.md', score: 0.96, chunkId: 'chunk-01' },
      { noteId: 'note-milvus', title: 'Milvus 向量数据库.md', score: 0.91, chunkId: 'chunk-05' },
      { noteId: 'note-embedding', title: 'Embedding.md', score: 0.87, chunkId: 'chunk-02' },
    ],
    [
      { noteId: 'note-embedding', title: 'Embedding.md', score: 0.93, relation: 'RAG → 使用 → Embedding' },
      { noteId: 'note-milvus', title: 'Milvus 向量数据库.md', score: 0.9, relation: 'RAG → 检索于 → Milvus' },
      { noteId: 'note-project-om', title: 'ObsidianMind 设计构思.md', score: 0.78, relation: 'ObsidianMind → 构建于 → RAG' },
    ],
  ),
  a(
    ['jvm', '内存', '堆', '栈'],
    `JVM 运行时内存分为**线程私有**和**线程共享**两大区。

\`\`\`
线程私有：程序计数器 | 虚拟机栈 | 本地方法栈
线程共享：堆（Heap）      | 方法区 / 元空间
\`\`\`

重点看堆：

- **新生代**：Eden + S0 + S1（8:1:1），对象优先在 Eden 分配
- **老年代**：多次 Minor GC 仍存活的对象晋升至此
- **元空间**：JDK 8 后取代永久代，用本地内存存类元信息

你笔记中记录了常见的 OOM 场景对照表，建议配合 GC Roots 一起复习，理解"哪些对象算存活"。`,
    [
      { noteId: 'note-jvm-memory', title: 'JVM 内存结构.md', score: 0.95, chunkId: 'chunk-21' },
      { noteId: 'note-g1', title: 'G1 垃圾回收器.md', score: 0.86, chunkId: 'chunk-33' },
      { noteId: 'note-gc-roots', title: 'GC Roots.md', score: 0.8, chunkId: 'chunk-40' },
    ],
    [
      { noteId: 'note-g1', title: 'G1 垃圾回收器.md', score: 0.88, relation: 'GC → 实现 → G1' },
      { noteId: 'note-gc-roots', title: 'GC Roots.md', score: 0.85, relation: 'GC → 依据 → GC Roots' },
      { noteId: 'note-ioc', title: 'Spring IOC.md', score: 0.62, relation: 'Java → 生态 → Spring' },
    ],
  ),
  a(
    ['并发', 'volatile', 'synchronized', 'cas', '锁'],
    `这三个是 Java 并发的核心工具，定位完全不同：

| 工具 | 原子性 | 可见性 | 有序性 | 典型场景 |
| --- | --- | --- | --- | --- |
| volatile | ✗ | ✓ | ✓ | 状态标志、DCL 单例 |
| synchronized | ✓ | ✓ | ✓ | 复合操作的互斥 |
| CAS | ✓ | - | - | 无锁计数（AtomicInteger） |

关键结论：

1. \`count++\` 是读-改-写三步，volatile 管不住，要么加锁要么用 AtomicInteger
2. CAS 的隐患是 **ABA 问题**，用 AtomicStampedReference 加版本号解决
3. synchronized 在 JDK 6 后有锁升级：无锁 → 偏向 → 轻量级 → 重量级

你的笔记里 volatile + DCL 单例的例子很典型，建议从"指令重排"角度再推一遍为什么第 3 行需要 volatile。`,
    [
      { noteId: 'note-volatile', title: 'volatile.md', score: 0.93, chunkId: 'chunk-52' },
      { noteId: 'note-synchronized', title: 'synchronized.md', score: 0.9, chunkId: 'chunk-45' },
      { noteId: 'note-cas', title: 'CAS.md', score: 0.88, chunkId: 'chunk-60' },
    ],
    [
      { noteId: 'note-cas', title: 'CAS.md', score: 0.86, relation: '并发 → 原语 → CAS' },
      { noteId: 'note-synchronized', title: 'synchronized.md', score: 0.84, relation: '并发 → 互斥 → synchronized' },
      { noteId: 'note-jvm-memory', title: 'JVM 内存结构.md', score: 0.71, relation: '并发 → 底层 → JVM 内存模型' },
    ],
  ),
  a(
    ['agent', '智能体', 'mcp'],
    `Agent = **LLM + 记忆 + 工具 + 规划**，本质是让模型从"回答问题"升级为"完成任务"。

核心是 Agent Loop：

\`\`\`
目标 → LLM 思考 → 选择工具 → 执行
 ↑                              ↓
 └──────── 观察结果 ←──────────┘
         （循环直到完成）
\`\`\`

三个关键能力：

1. **Function Calling**：模型输出结构化的工具调用请求
2. **ReAct 模式**：思考（Thought）→ 行动（Action）→ 观察（Observation）交替进行
3. **MCP**：统一工具接入协议，Agent 用标准方式连接文件系统、数据库、外部 API

和你 RAG 笔记的关系：RAG 可以看作 Agent 的一种"检索工具"——Agent 决定何时检索、检索什么，比固定流程的 RAG 更灵活。`,
    [
      { noteId: 'note-agent', title: 'Agent 入门.md', score: 0.95, chunkId: 'chunk-70' },
      { noteId: 'note-llm', title: '大模型基础.md', score: 0.81, chunkId: 'chunk-15' },
    ],
    [
      { noteId: 'note-llm', title: '大模型基础.md', score: 0.83, relation: 'Agent → 驱动 → LLM' },
      { noteId: 'note-rag', title: 'RAG 技术原理.md', score: 0.79, relation: 'Agent → 工具 → RAG' },
      { noteId: 'note-project-om', title: 'ObsidianMind 设计构思.md', score: 0.72, relation: 'ObsidianMind → 未来 → Agent' },
    ],
  ),
  a(
    ['embedding', '向量', '相似'],
    `Embedding 把文本映射为高维向量，**语义相近 → 距离相近**。

\`\`\`
"猫在睡觉"  → [0.12, -0.83, 0.44, ...]
"小猫打盹"  → [0.11, -0.79, 0.46, ...]   ← 距离很近
"Redis 持久化" → [-0.65, 0.32, -0.88, ...] ← 距离很远
\`\`\`

相似度用余弦计算，RAG 中一般取 COSINE、Top-K 阈值 0.7 以上。

两个最容易踩的坑：

1. **查询和文档必须用同一个 Embedding 模型**，否则不在同一语义空间
2. **中文场景选多语模型**（如 bge-m3），纯英文模型对中文语义支持差`,
    [
      { noteId: 'note-embedding', title: 'Embedding.md', score: 0.97, chunkId: 'chunk-02' },
      { noteId: 'note-rag', title: 'RAG 技术原理.md', score: 0.85, chunkId: 'chunk-08' },
    ],
    [
      { noteId: 'note-rag', title: 'RAG 技术原理.md', score: 0.88, relation: 'Embedding ← 使用 ← RAG' },
      { noteId: 'note-milvus', title: 'Milvus 向量数据库.md', score: 0.84, relation: 'Embedding → 存储于 → Milvus' },
      { noteId: 'note-transformer', title: 'Transformer.md', score: 0.76, relation: 'Embedding ← 基于 ← Transformer' },
    ],
  ),
];

/** 兜底回答 */
export const MOCK_FALLBACK: MockAnswer = a(
  [],
  `我检索了你的知识库，没有找到与这个问题高度相关的内容。

可以尝试：

1. **换一种问法**——例如把"这玩意怎么配"改成"JVM 调优如何设置 G1 参数"
2. **扩大知识范围**——把检索范围从当前文件夹切到整个知识库
3. **补充笔记**——如果这个主题还没有笔记，先记录一篇，我下次就能回答了

你的知识库目前侧重 **RAG / JVM / 并发编程**，这些方向的问题我可以回答得更好。`,
  [],
  [],
);

/** 历史会话（演示用） */
export const MOCK_HISTORY: ChatMessage[] = [];
