# 08-performance

## Purpose

性能问题的处理流程与本项目各层的优化关注点。核心纪律：**先测量、再定位、后优化、再验证**；禁止提前优化。

## Scope

前端渲染 / 请求、后端 IO / 线程、RAG 链路参数、Ollama / Milvus 资源。

## When To Use

- 出现可观测的性能问题（卡顿、慢查询、内存增长）。
- RAG 链路 Phase 2+ 设计时做量级预判。

## When NOT To Use

- 没有测量数据的"感觉慢"（先测量，或确认是功能问题）。

## Rules

### 1. 流程（强制）

Measure → Identify Bottleneck → Optimize → Verify。优化前必须能回答：慢在哪（数据）？优化后怎么证明变快了（对比数据）？禁止无基线优化。

### 2. Frontend 关注点

- 避免重复请求：数据请求收敛到 store action。
- 大列表：文件树 / 搜索结果超过 ~500 项时虚拟化或分页。
- Markdown 渲染：长文档避免全量重复渲染；Chat 流式追加避免整段 re-parse。
- Sources 渲染：引用列表与回答分开更新，避免流式期间整块重绘。
- 不写无意义 deep watcher / 频繁 interval。

### 3. Backend 关注点

- **IO**：Vault 扫描只读元数据（已实现）；避免启动时全量读正文。
- **Thread Pool**：后台任务（索引）用有界线程池；IO 密集任务与 CPU 任务分开。
- **Async**：索引等长任务异步化（`POST /index` 已是异步语义），接口立即返回任务状态。
- **Streaming**：SSE 用独立线程写 emitter，不阻塞请求线程；断连及时释放。
- **Connection Pool**：Ollama / Milvus 客户端复用连接（接入时确认底层 client 的连接池配置），禁止每次请求新建 client。

### 4. RAG 关注点

- Chunk 数量：单文档 Chunk 数有上限意识（超大文档切分策略见 04）。
- Embedding 批量处理：索引时合批，禁止逐条 HTTP 调用。
- TopK：可配置、有默认（5~8），配合相似度阈值。
- Rerank：Phase 9，先证明召回质量是瓶颈再引入。
- Context Size：送入 LLM 前按预算截断，不把检索结果整包塞入。

### 5. AI（Ollama）关注点

- 模型加载昂贵：chat / embedding 模型常驻，避免频繁切换模型。
- Timeout 短且可配置（现有 3s 健康探测）；推理超时与探测超时分开设。
- Streaming 优先于同步等待长回答。
- 并发：Ollama 本地并发能力有限，多请求排队而非并发轰炸。

### 6. Milvus 关注点

- Collection / Index 类型 / Search Parameters 显式配置，不用默认值裸跑。
- 数据量小（个人 Vault，万级 Chunk 以下）时优先简单方案，不引入分区 / 分片复杂度。

## Patterns

- `application.yml` 的 timeout-seconds 模式：一切外部调用超时可配置。
- `system/status` 端点：组件健康与延迟观测入口。

## Anti-Patterns

- 没有测量就重构"为了性能"。
- 无界缓存（Map 只进不出）。
- 索引时逐 Chunk 请求 Embedding。
- 用 `Thread.sleep` 解决时序问题。
- 为当前 <10MB 的数据量引入分布式缓存 / 消息队列。

## Checklist

- [ ] 有性能数据（做什么测的、结果多少）
- [ ] 定位到具体瓶颈而不是猜
- [ ] 优化前后有对比
- [ ] 新参数进配置、有默认值
- [ ] 回归验证：`mvn test` / `npm run build` 仍绿

## Related Skills

04-ai-rag-engineering（RAG 参数）、02（线程 / 资源管理）、01（前端渲染）。

## Project-specific Notes

- 个人项目 + 单用户场景：性能预算宽松，正确性与可维护性永远优先；本 Skill 多数时候是"知道在哪查"，而非日常执行。

## Status

optional（流程与关注点有效；无度量数据前不主动执行优化）
