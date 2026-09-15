# 06-code-review

## Purpose

AI Coding Agent（与人）每次完成代码后的强制自审清单。实现完成后、报告"完成"之前必须执行。

## Scope

任何进入提交的 diff。

## When To Use

- 每个功能 / 修复完成时。
- 大改动分批提交前，逐 commit 审。

## When NOT To Use

- 纯文档错字。

## Review 顺序（固定）

1. **Correctness**：逻辑正确吗？边界条件（空输入 / 超长 / 并发）覆盖了吗？
2. **Architecture**：分层正确（Controller 薄 / Service 编排）？有没有越层调用？
3. **Security**：Secret 泄露？路径校验？untrusted 内容进 Prompt / v-html？（细节见 07）
4. **Error Handling**：异常有映射、不吞、不泄漏堆栈；前端四态齐备。
5. **Performance**：N+1 文件读取？重复请求？无界集合？（深层优化见 08，此处只查明显问题）
6. **Maintainability**：重复代码、巨型类 / 方法（类 >400 行 / 方法 >50 行要说明理由）、隐藏副作用、魔法字符串 / 数字（应进配置或常量）。
7. **Testing**：新逻辑有测试？`mvn test` / `npm run build` 真的跑过且绿？
8. **UX**：Loading / Empty / Error / Success；错误信息用户能懂；键盘可达；两套主题下不破相。
9. **Documentation**：README 环境变量表 / docs/api 快速索引 / 相关 docs 是否需要同步。

## 重点禁止项（发现即打回）

- 复制粘贴重复代码（第二处出现必须提取）
- 巨大类 / 巨型方法
- 隐藏副作用（getter 里改状态、构造函数里起 IO）
- 魔法字符串 / 魔法数字
- 异常吞掉（`catch` 后继续运行不记录）
- 日志泄露 Secret / 用户笔记内容
- 未处理的 Promise（前端 floating promise）
- NPE 风险（未判空就链式调用，尤其 Optional 误用为字段）
- 资源未释放（流 / 通道未 try-with-resources）
- 连接未关闭（HTTP client / SSE emitter）
- 无限 Retry / 无限循环 / 无界缓存
- 无超时的 AI / 网络请求

## 自审流程

1. `git diff` 逐行读一遍（自己写的也要读）。
2. 按上面 1→9 顺序过。
3. 跑验证命令（05-testing Checklist）。
4. 向用户汇报时如实说明：改了什么、为什么、验证结果、遗留问题。禁止"应该可以"。

## Checklist

- [ ] git diff 逐行可解释
- [ ] 1→9 九项全过
- [ ] 无"重点禁止项"中的任何一条
- [ ] 验证命令实际执行且通过
- [ ] 文档同步完成

## Related Skills

所有 Skill 的规则最终都汇入本 Checklist；冲突时按 AGENTS.md 的优先级排序。

## Project-specific Notes

- 本仓库多数提交由 AI Agent 产出，本 Skill 是质量的最后一道闸门，不可跳过。

## Status

active
