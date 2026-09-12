import type { VaultNode, Note } from '@/types/knowledge';

/**
 * Mock 知识库 —— 模拟一个真实使用中的个人 Vault。
 * Markdown 是 Source of Truth，本文件未来将被文件系统读取替换。
 */

const n = (
  id: string,
  title: string,
  path: string,
  folder: string,
  tags: string[],
  updatedAt: string,
  content: string,
): Note => ({
  id,
  title,
  path,
  folder,
  tags,
  updatedAt,
  wordCount: content.length,
  content,
});

export const MOCK_NOTES: Note[] = [
  n(
    'note-rag',
    'RAG 技术原理',
    '02 - 技术学习/AI/RAG/RAG 技术原理.md',
    '02 - 技术学习',
    ['RAG', 'AI', '检索'],
    '2026-09-10 14:32',
    `# RAG（Retrieval-Augmented Generation）

## 1. 什么是 RAG

RAG 是一种**检索增强生成**技术：在 LLM 生成答案之前，先从外部知识库中检索相关内容，把检索结果作为上下文一起交给模型。

它解决两个核心问题：

1. 模型知识截止（不知道最新 / 私有知识）
2. 幻觉（编造不存在的事实）

## 2. 工作流程

\`\`\`
用户问题
   ↓
向量检索（Embedding 相似度）
   ↓
知识库 / 向量数据库（Milvus）
   ↓
Top-K 相关片段
   ↓
拼装 Prompt → LLM
   ↓
最终答案（带引用来源）
\`\`\`

## 3. 关键环节

| 环节 | 说明 | 常见方案 |
| --- | --- | --- |
| 分块 Chunking | 把长文档切成语义完整的片段 | 固定长度 / 递归 / 语义分块 |
| 向量化 | 文本 → 高维向量 | bge、Qwen-Embedding |
| 检索 | 相似度召回 Top-K | Milvus、HNSW |
| 重排 Rerank | 对召回结果精排 | bge-reranker |
| 生成 | LLM 基于上下文回答 | Qwen、DeepSeek |

## 4. 与微调的区别

- RAG：知识外挂，更新成本低，可追溯引用
- 微调：改变模型行为，成本高，不可追溯

> 结论：知识类问题优先 RAG，行为类需求才考虑微调。
`,
  ),
  n(
    'note-milvus',
    'Milvus 向量数据库',
    '02 - 技术学习/AI/RAG/Milvus 向量数据库.md',
    '02 - 技术学习',
    ['Milvus', '向量数据库', 'RAG'],
    '2026-09-09 20:15',
    `# Milvus 向量数据库

## 1. 定位

Milvus 是开源的**向量数据库**，专门用于存储和检索 Embedding 向量，是 RAG 系统的检索底座。

## 2. 核心概念

- **Collection**：类似关系库的表
- **Schema**：字段定义（向量字段 + 标量字段）
- **Index**：向量索引类型（HNSW、IVF）
- **相似度度量**：COSINE / L2 / IP

## 3. 检索流程

\`\`\`
query → Embedding → 搜索 Collection
     → 近似最近邻（ANN）
     → Top-K + 距离分数
\`\`\`

## 4. 部署形态

1. Milvus Lite（嵌入式，适合开发）
2. Docker Standalone（单机生产）
3. 分布式集群（大规模）

> 注意：Milvus 只是检索索引，**Markdown 原文才是 Source of Truth**。
`,
  ),
  n(
    'note-embedding',
    'Embedding',
    '02 - 技术学习/AI/RAG/Embedding.md',
    '02 - 技术学习',
    ['Embedding', 'AI'],
    '2026-09-08 11:02',
    `# Embedding

Embedding 把文本映射为**高维向量**，语义相近的文本在向量空间中距离更近。

## 1. 直觉理解

「猫在睡觉」和「小猫打盹」字面不同，但向量距离很近；「猫在睡觉」和「Redis 持久化」距离很远。

## 2. 常用模型

- bge-m3（多语言、开源）
- Qwen3-Embedding
- text-embedding-3（OpenAI）

## 3. 相似度计算

\`\`\`
cosine(a, b) = (a · b) / (|a| × |b|)
\`\`\`

取值 [-1, 1]，越大越相似。RAG 中一般取 Top-K = 0.7 以上阈值。

## 4. 在 RAG 中的位置

用户问题 和 知识库文档 必须**使用同一个 Embedding模型**向量化，否则不在同一语义空间，检索会失效。
`,
  ),
  n(
    'note-transformer',
    'Transformer',
    '02 - 技术学习/AI/大模型/Transformer.md',
    '02 - 技术学习',
    ['Transformer', '深度学习'],
    '2026-09-05 09:40',
    `# Transformer

Transformer 是现代大模型的基石架构（2017《Attention Is All You Need》）。

## 1. 核心机制：Self-Attention

每个 token 通过 Q / K / V 与序列中所有 token 计算相关性：

\`\`\`
Attention(Q, K, V) = softmax(QK^T / √d) V
\`\`\`

## 2. 结构要点

- 多头注意力（Multi-Head）
- 前馈网络（FFN）
- 残差连接 + LayerNorm
- 位置编码（RoPE 等）

## 3. 衍生架构

| 架构 | 代表 | 特点 |
| --- | --- | --- |
| Encoder-only | BERT | 理解任务 |
| Decoder-only | GPT / Qwen | 生成任务（当前主流） |
| Encoder-Decoder | T5 | 翻译 / 摘要 |
`,
  ),
  n(
    'note-llm',
    '大模型基础',
    '02 - 技术学习/AI/大模型/大模型基础.md',
    '02 - 技术学习',
    ['LLM', 'AI'],
    '2026-09-04 16:20',
    `# 大模型基础

## 1. 什么是 LLM

大语言模型是基于 Transformer 的超大规模概率模型，通过预测下一个 token 完成文本生成。

## 2. 关键参数

- **Temperature**：采样随机性，越高越发散
- **Top-P**：核采样阈值
- **Max Tokens**：输出长度上限
- **System Prompt**：定义模型角色与约束

## 3. Token

模型处理文本的最小单位。1 个中文字约为 1~2 个 token，计费与上下文长度都按 token 计。

## 4. 上下文窗口

模型一次能"看到"的最大 token 数（如 128K）。超出需 RAG 或摘要压缩。
`,
  ),
  n(
    'note-agent',
    'Agent 入门',
    '02 - 技术学习/AI/Agent/Agent 入门.md',
    '02 - 技术学习',
    ['Agent', 'AI', '工具调用'],
    '2026-09-11 10:08',
    `# Agent 入门

## 1. 定义

Agent = LLM + 记忆 + 工具 + 规划。模型不再只是"回答"，而是能**自主拆解任务、调用工具、循环迭代**直到完成目标。

## 2. 基本循环（Agent Loop）

\`\`\`
目标 → LLM 思考 → 选择工具 → 执行
 ↑                                ↓
 └────── 观察结果 ←──────────────┘
        （直到任务完成）
\`\`\`

## 3. 关键能力

1. **Function Calling**：模型输出结构化工具调用
2. **ReAct**：思考-行动-观察交替
3. **记忆**：短期（对话上下文）+ 长期（向量库）

## 4. MCP

Model Context Protocol：统一的工具接入协议，让 Agent 以标准方式连接文件系统、数据库、外部 API。
`,
  ),
  n(
    'note-jvm-memory',
    'JVM 内存结构',
    '02 - 技术学习/Java/JVM/JVM 内存结构.md',
    '02 - 技术学习',
    ['JVM', 'Java'],
    '2026-09-06 21:12',
    `# JVM 内存结构

## 1. 运行时数据区

\`\`\`
线程私有：程序计数器 | 虚拟机栈 | 本地方法栈
线程共享：堆（Heap） | 方法区 / 元空间
\`\`\`

## 2. 堆的分代

- **新生代**：Eden + 两个 Survivor（S0/S1），比例 8:1:1
- **老年代**：长期存活对象
- 对象优先在 Eden 分配，Minor GC 后存活对象进入 Survivor，年龄达标晋升老年代

## 3. 元空间

JDK 8 后方法区由元空间（Metaspace）实现，使用本地内存，存储类元信息。

## 4. 常见 OOM

| 区域 | 场景 |
| --- | --- |
| 堆 | 对象过多、内存泄漏 |
| 元空间 | 动态生成类过多 |
| 虚拟机栈 | 递归过深（StackOverflow） |
`,
  ),
  n(
    'note-g1',
    'G1 垃圾回收器',
    '02 - 技术学习/Java/JVM/G1 垃圾回收器.md',
    '02 - 技术学习',
    ['JVM', 'GC'],
    '2026-09-03 19:44',
    `# G1 垃圾回收器

## 1. 设计目标

可预测的停顿时间（\`-XX:MaxGCPauseMillis=200\`），面向大堆（4GB+）服务端应用。JDK 9 起为默认 GC。

## 2. Region 化内存

堆被划分为 2048 个左右等大的 Region（1~32MB），每个 Region 动态扮演 Eden / Survivor / Old / Humongous 角色。

## 3. 回收过程

1. **Young GC**：回收全部新生代 Region
2. **并发标记**：三色标记 + SATB 快照
3. **Mixed GC**：按**回收价值**排序，优先回收垃圾占比高的 Region（Garbage First 名字由来）

## 4. 调优要点

- 不建议显式设置 -Xmn，会干扰暂停目标
- 关注 Mixed GC 是否及时，避免退化成 Full GC
`,
  ),
  n(
    'note-gc-roots',
    'GC Roots',
    '02 - 技术学习/Java/JVM/GC Roots.md',
    '02 - 技术学习',
    ['JVM', 'GC'],
    '2026-08-28 15:30',
    `# GC Roots

可达性分析：从 GC Roots 出发，不可达的对象即可回收。

## GC Roots 包括

1. 虚拟机栈中引用的对象（局部变量）
2. 方法区中静态变量引用的对象
3. 方法区中常量引用的对象
4. JNI 引用的对象
5. 活跃线程对象

## 常见内存泄漏来源

- 静态集合持有大对象
- ThreadLocal 未 remove
- 未关闭的资源（连接、流）
- 监听器 / 回调未注销
`,
  ),
  n(
    'note-synchronized',
    'synchronized',
    '02 - 技术学习/Java/并发编程/synchronized.md',
    '02 - 技术学习',
    ['并发', 'Java'],
    '2026-09-01 13:25',
    `# synchronized

Java 内置锁，保证**原子性、可见性、有序性**。

## 1. 三种用法

\`\`\`java
synchronized (lock) { ... }      // 代码块
synchronized void m() { ... }    // 实例方法，锁 this
static synchronized void m() {}  // 类方法，锁 Class
\`\`\`

## 2. 锁升级（JDK 6+）

\`\`\`
无锁 → 偏向锁 → 轻量级锁 → 重量级锁
\`\`\`

竞争加剧时逐级膨胀，不可逆降。

## 3. 与 Lock 对比

| 维度 | synchronized | ReentrantLock |
| --- | --- | --- |
| 释放 | 自动 | 手动 unlock |
| 可中断 | 否 | 是 |
| 公平锁 | 否 | 可选 |
| 条件队列 | 单一 | 多 Condition |
`,
  ),
  n(
    'note-volatile',
    'volatile',
    '02 - 技术学习/Java/并发编程/volatile.md',
    '02 - 技术学习',
    ['并发', 'Java'],
    '2026-08-30 10:18',
    `# volatile

保证**可见性**与**有序性**，不保证原子性。

## 1. 原理

- 写操作立即刷回主内存，读操作强制从主内存加载
- 通过内存屏障禁止指令重排

## 2. 典型场景：双重检查锁单例

\`\`\`java
private static volatile Singleton instance;

public static Singleton getInstance() {
  if (instance == null) {                // 1
    synchronized (Singleton.class) {
      if (instance == null) {            // 2
        instance = new Singleton();      // 3
      }
    }
  }
  return instance;
}
\`\`\`

volatile 防止第 3 行的「分配内存 →初始化 → 赋值引用」被重排，导致其他线程拿到未初始化对象。

## 3. 不能替代 synchronized

\`count++\` 是「读-改-写」三步，volatile 无法保证原子性，应使用 AtomicInteger。
`,
  ),
  n(
    'note-cas',
    'CAS',
    '02 - 技术学习/Java/并发编程/CAS.md',
    '02 - 技术学习',
    ['并发', 'Java'],
    '2026-08-29 17:52',
    `# CAS（Compare-And-Swap）

## 1. 原理

\`\`\`
CAS(memory, expected, new):
  if memory == expected:
    memory = new; return true
  else: return false
\`\`\`

底层由 CPU 指令 \`cmpxchg\` 保证原子性，是乐观锁的基础。

## 2. ABA 问题

值从 A → B → A，CAS 认为没变。解决：\`AtomicStampedReference\` 加版本号。

## 3. 自旋开销

竞争激烈时反复失败自旋，浪费 CPU。JDK 8 引入 \`LongAdder\`：分段计数降低竞争。

## 4. 应用

AtomicInteger、AQS 获取锁的状态位、ConcurrentHashMap 的 transfer。
`,
  ),
  n(
    'note-ioc',
    'Spring IOC',
    '02 - 技术学习/Java/Spring/Spring IOC.md',
    '02 - 技术学习',
    ['Spring', 'Java'],
    '2026-08-26 14:02',
    `# Spring IOC

## 1. 核心思想

控制反转：对象的创建与依赖装配交给容器管理，程序只声明依赖。DI（依赖注入）是 IOC 的实现方式。

## 2. Bean 生命周期

\`\`\`
实例化 → 属性填充 → Aware 回调 → BeanPostProcessor 前置
→ @PostConstruct / afterPropertiesSet → BeanPostProcessor 后置
→ 使用 → 销毁
\`\`\`

AOP 代理在**初始化后**的 BeanPostProcessor 中生成。

## 3. 三级缓存解决循环依赖

| 缓存 | 内容 |
| --- | --- |
| singletonObjects | 成品 Bean |
| earlySingletonObjects | 半成品（提前暴露） |
| singletonFactories | ObjectFactory |

构造器注入的循环依赖无法解决（Spring Boot 2.6+ 默认禁止循环依赖）。
`,
  ),
  n(
    'note-aop',
    'Spring AOP',
    '02 - 技术学习/Java/Spring/Spring AOP.md',
    '02 - 技术学习',
    ['Spring', 'Java'],
    '2026-08-25 11:36',
    `# Spring AOP

## 1. 概念

面向切面编程：把日志、事务、权限等**横切关注点**从业务代码中抽离。

## 2. 核心术语

- JoinPoint：可插入点（方法执行）
- Pointcut：切入点表达式，如 \`execution(* com.x.service..*.*(..))\`
- Advice：通知（@Before / @After / @Around）

## 3. 代理实现

- 目标类有接口 → JDK 动态代理
- 无接口 → CGLIB 子类代理
- Spring Boot 2.x 起默认 CGLIB

## 4. 事务失效场景

1. 方法非 public
2. 自调用（this 调用绕过代理）
3. 异常被 catch 吞掉
4. rollbackFor 配置错误
`,
  ),
  n(
    'note-growth-review',
    '周复盘模板',
    '01 - 个人成长/思考/周复盘模板.md',
    '01 - 个人成长',
    ['复盘', '习惯'],
    '2026-09-07 22:10',
    `# 周复盘模板

## 1. 三个问题

1. 本周最重要的产出是什么？
2. 哪件事花了大量时间却没有价值？
3. 下周要砍掉什么、坚持什么？

## 2. 格式

\`\`\`
本周目标：xxx
实际完成：xxx / xxx
偏差原因：xxx
下周重点：最多 3 件
\`\`\`

> 复盘的目的不是自责，而是**调整下周的资源配置**。
`,
  ),
  n(
    'note-habit',
    '学习习惯清单',
    '01 - 个人成长/习惯/学习习惯清单.md',
    '01 - 个人成长',
    ['习惯', '学习'],
    '2026-09-02 08:05',
    `# 学习习惯清单

## 每日

- [ ] 早上 30 分钟：回顾昨天笔记（间隔重复）
- [ ] 晚上写 3 行当日学习日志

## 每周

- [ ] 周日整理本周新建笔记，建立双向链接
- [ ] 用 AI 找出知识薄弱点，排入下周

## 原则

1. 先建骨架再填肉：先写目录和问题，再补内容
2. 一篇笔记只回答一个问题
`,
  ),
  n(
    'note-book',
    '《卡片笔记写作法》摘录',
    '01 - 个人成长/读书笔记/《卡片笔记写作法》摘录.md',
    '01 - 个人成长',
    ['读书', '笔记方法'],
    '2026-08-24 20:40',
    `# 《卡片笔记写作法》摘录

## 核心观点

1. 笔记的价值在于**连接**，不在于收藏
2. 用自己的话重写，才是真正的理解（费曼）
3. 写作不是从零开始，而是从已有卡片中"生长"出来

## 对我的启发

知识库应该按**主题网络**组织，而不是按时间或文件夹堆放。每条笔记至少要链接到一个已有概念。

> 这也是我想做 ObsidianMind 的原因：让 AI 帮我发现那些我看不到的连接。
`,
  ),
  n(
    'note-project-om',
    'ObsidianMind 设计构思',
    '03 - 项目实践/ObsidianMind/ObsidianMind 设计构思.md',
    '03 - 项目实践',
    ['ObsidianMind', 'RAG', '项目'],
    '2026-09-11 23:18',
    `# ObsidianMind 设计构思

## 1. 一句话定位

Obsidian 的知识组织 + ChatGPT 的对话 + Perplexity 的引用 + 本地优先。

## 2. 核心原则

1. **Markdown 是 Source of Truth**，Milvus 只是索引
2. 数据不出本机：Ollama 推理 + 本地 Embedding
3. AI 回答必须可追溯：Source → 文件 → Chunk → 原文位置

## 3. 技术架构

\`\`\`
UI (Vue3 + TS)
   ↓ REST / SSE
Spring Boot
   ├─ Knowledge Service
   ├─ Search Service
   ├─ AI Service（Spring AI）
   └─ Index Service
        ├─ Ollama（LLM / Embedding）
        └─ Milvus（向量检索）
\`\`\`

## 4. 差异化

不是又一个 Chat 壳，而是让 AI **理解整个知识库的结构**：找关联、找薄弱点、挑战我的理解。
`,
  ),
  n(
    'note-project-kb',
    'AI Knowledge Base 方案对比',
    '03 - 项目实践/AI Knowledge Base/方案对比.md',
    '03 - 项目实践',
    ['RAG', '架构'],
    '2026-09-08 19:22',
    `# AI Knowledge Base 方案对比

## 检索方案

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| 全文检索（ES） | 精确匹配强 | 语义弱 |
| 向量检索（Milvus） | 语义强 | 精确匹配弱 |
| 混合检索 + Rerank | 两者兼得 | 链路复杂 |

**结论**：混合检索是终态，第一版先做向量检索 + 关键词过滤。

## Embedding 选型

- bge-m3：多语言、8K 上下文，首选
- 维度 1024，COSINE 相似度
`,
  ),
  n(
    'note-media-java',
    'Java 教程选题库',
    '04 - 自媒体/Java 教程/选题库.md',
    '04 - 自媒体',
    ['自媒体', 'Java'],
    '2026-09-05 12:00',
    `# Java 教程选题库

## 待写

1. 用开饭店讲懂 Spring IOC 和 AOP
2. JVM 内存结构一图流
3. volatile 为什么要配合 DCL 使用

## 已发

1. synchronized 锁升级（阅读 1.2w）
2. HashMap 扩容机制（阅读 8k）

## 复盘

带图解的文章完读率高 40%，动画演示类收藏率最高。
`,
  ),
  n(
    'note-media-token',
    'Token 动画脚本',
    '04 - 自媒体/AI 教程/Token 动画脚本.md',
    '04 - 自媒体',
    ['自媒体', 'LLM'],
    '2026-09-10 18:30',
    `# Token 动画脚本

## 分镜

1. 一句话逐字切成 token 块（动画：文字 → 彩色方块）
2. 每个方块映射为数字 ID
3. ID 进入 Embedding，变成向量点阵
4. 注意力连线在点阵之间闪烁

## 解说词

> 模型看到的不是文字，而是一个个 token。每生成一个字，都是在几十亿参数中做一次"下一块积木放哪"的预测。
`,
  ),
];

/** 文件树（模拟 Obsidian Vault 结构） */
export const MOCK_TREE: VaultNode = {
  id: 'vault-root',
  name: 'Obsidian Vault',
  type: 'folder',
  children: [
    {
      id: 'f-growth',
      name: '01 - 个人成长',
      type: 'folder',
      children: [
        {
          id: 'f-habit',
          name: '习惯',
          type: 'folder',
          children: [{ id: 'n-habit', name: '学习习惯清单.md', type: 'note', noteId: 'note-habit' }],
        },
        {
          id: 'f-think',
          name: '思考',
          type: 'folder',
          children: [{ id: 'n-review', name: '周复盘模板.md', type: 'note', noteId: 'note-growth-review' }],
        },
        {
          id: 'f-book',
          name: '读书笔记',
          type: 'folder',
          children: [{ id: 'n-book', name: '《卡片笔记写作法》摘录.md', type: 'note', noteId: 'note-book' }],
        },
      ],
    },
    {
      id: 'f-tech',
      name: '02 - 技术学习',
      type: 'folder',
      children: [
        {
          id: 'f-java',
          name: 'Java',
          type: 'folder',
          children: [
            {
              id: 'f-jvm',
              name: 'JVM',
              type: 'folder',
              children: [
                { id: 'n-jvm', name: 'JVM 内存结构.md', type: 'note', noteId: 'note-jvm-memory' },
                { id: 'n-g1', name: 'G1 垃圾回收器.md', type: 'note', noteId: 'note-g1' },
                { id: 'n-gcr', name: 'GC Roots.md', type: 'note', noteId: 'note-gc-roots' },
              ],
            },
            {
              id: 'f-conc',
              name: '并发编程',
              type: 'folder',
              children: [
                { id: 'n-sync', name: 'synchronized.md', type: 'note', noteId: 'note-synchronized' },
                { id: 'n-vol', name: 'volatile.md', type: 'note', noteId: 'note-volatile' },
                { id: 'n-cas', name: 'CAS.md', type: 'note', noteId: 'note-cas' },
              ],
            },
            {
              id: 'f-spring',
              name: 'Spring',
              type: 'folder',
              children: [
                { id: 'n-ioc', name: 'Spring IOC.md', type: 'note', noteId: 'note-ioc' },
                { id: 'n-aop', name: 'Spring AOP.md', type: 'note', noteId: 'note-aop' },
              ],
            },
          ],
        },
        {
          id: 'f-ai',
          name: 'AI',
          type: 'folder',
          children: [
            {
              id: 'f-llm',
              name: '大模型',
              type: 'folder',
              children: [
                { id: 'n-llm', name: '大模型基础.md', type: 'note', noteId: 'note-llm' },
                { id: 'n-tf', name: 'Transformer.md', type: 'note', noteId: 'note-transformer' },
              ],
            },
            {
              id: 'f-rag',
              name: 'RAG',
              type: 'folder',
              children: [
                { id: 'n-rag', name: 'RAG 技术原理.md', type: 'note', noteId: 'note-rag' },
                { id: 'n-milvus', name: 'Milvus 向量数据库.md', type: 'note', noteId: 'note-milvus' },
                { id: 'n-emb', name: 'Embedding.md', type: 'note', noteId: 'note-embedding' },
              ],
            },
            {
              id: 'f-agent',
              name: 'Agent',
              type: 'folder',
              children: [{ id: 'n-agent', name: 'Agent 入门.md', type: 'note', noteId: 'note-agent' }],
            },
          ],
        },
      ],
    },
    {
      id: 'f-project',
      name: '03 - 项目实践',
      type: 'folder',
      children: [
        {
          id: 'f-proj-om',
          name: 'ObsidianMind',
          type: 'folder',
          children: [{ id: 'n-pom', name: 'ObsidianMind 设计构思.md', type: 'note', noteId: 'note-project-om' }],
        },
        {
          id: 'f-proj-kb',
          name: 'AI Knowledge Base',
          type: 'folder',
          children: [{ id: 'n-pkb', name: '方案对比.md', type: 'note', noteId: 'note-project-kb' }],
        },
      ],
    },
    {
      id: 'f-media',
      name: '04 - 自媒体',
      type: 'folder',
      children: [
        {
          id: 'f-mj',
          name: 'Java 教程',
          type: 'folder',
          children: [{ id: 'n-mj', name: '选题库.md', type: 'note', noteId: 'note-media-java' }],
        },
        {
          id: 'f-mai',
          name: 'AI 教程',
          type: 'folder',
          children: [{ id: 'n-mt', name: 'Token 动画脚本.md', type: 'note', noteId: 'note-media-token' }],
        },
      ],
    },
  ],
};
