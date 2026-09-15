# AI / RAG 规则（AI-RAG Rules）

> 人读版。执行细则、链路完整推导与 Checklist 见 `.agents/skills/04-ai-rag-engineering/SKILL.md`（项目最核心 Skill）。

## 核心链路（十步）

Vault Markdown → Parser（结构化 Document，保留 path/frontmatter/headings/wikilinks）→ Chunk（尊重 heading 边界，不切代码块/表格，禁止 substring 式切分）→ Embedding（文本→稠密向量；区分 Token ID / Token / Text / Embedding）→ Milvus（只存向量+元数据，可全量重建）→ Query Embedding（与文档**同一模型**，无例外）→ Retrieval（TopK 5~8 + 相似度阈值 + 去重）→ Context（Chunk 只进"资料区"，与 system 指令隔离）→ LLM（仅依据资料回答，不足明说）→ Sources（**后端生成**，四级可定位：Vault / Path / Heading / Chunk）。

每一步"为什么"的完整文字推导在 Skill 04，此处不重复。

## 现状（诚实声明，2026-09-16 Phase 4 完成后）

- 已落地：Parser 三件套、**Chunker v2（headingPath / offset / 代码块表格保护 / 可配置 size+overlap）**、Ollama Embedding（**批量 + 超时 + 校验 + 有限重试**）、**MilvusVectorStore（SDK 2.4.5，HNSW/COSINE，文档级 replace，vaultId 边界过滤）**、**KnowledgeIndexService 增量索引（INDEXED/UPDATED/SKIPPED/DELETED/FAILED）**、`POST /api/v1/index/run` 同步管线、前端「立即同步知识库」入口。
- **Phase 4 已落地**：**RetrievalService 语义检索**（校验→Query Embedding→向量检索→阈值（默认关闭）→同文档多样性去重→Source 映射/snippet 码点安全截断）、`POST /api/v1/search/semantic`、前端 SearchView「语义」模式（四态）、评估集 10 条查询（**Recall@5 = 1.0**，真实 bge-m3）。
- 未落地：Context Assembly → RAG Answer → SSE Streaming Citations（Phase 5）、Reranker、Hybrid Search。
- 实测：Demo Vault 8 Markdown → 8 Documents → 16 Chunks → 16 Embeddings → 16 Vectors（真实 Ollama bge-m3，1024 维）；第二次同步全 SKIPPED。Milvus 实机验证条件启用（MILVUS_IT=1）。

## 关键红线

1. Milvus 永远只是索引；删除 Vault 文件后的幽灵索引结果必须能被识别/重建。
2. 搜索 score 诚实标注：全文匹配就是 TEXT_MATCH，不得伪装成向量相似度。
3. Vault 内容是 untrusted：禁止拼进 system prompt；Prompt 注入防线见 Skill 07。
4. 无来源不硬答：检索为空/低相关时明确告知，不用模型自由发挥填空。
5. 一切 AI 调用有 Timeout / 有限 Retry / Fallback / 空响应兜底；配置（模型 / URL / Temperature / TopK）全部走 `AiProperties` + 环境变量。
6. Ollama / Milvus 不可用 → 503 + 明确 code，不静默返回空结果，不阻塞应用启动。
