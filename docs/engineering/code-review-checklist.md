# 代码评审清单（Code Review Checklist）

> 人读版。AI Agent 的自审流程与"重点禁止项"见 `.agents/skills/06-code-review/SKILL.md`。本页是该 Checklist 的可打印精简版。

## 评审顺序（固定九项）

1. **Correctness**：逻辑正确？边界（空 / 超长 / 并发）覆盖？
2. **Architecture**：Controller 薄？Service 编排？没越层？
3. **Security**：Secret？路径过 VaultPaths？untrusted 内容隔离？（Skill 07 Checklist）
4. **Error Handling**：异常有映射不吞？前端四态齐？
5. **Performance**：明显 N+1 / 重复请求 / 无界集合？（深层见 Skill 08）
6. **Maintainability**：重复代码？巨型类（>400 行）/ 方法（>50 行）？魔法值？
7. **Testing**：新逻辑有测试？`mvn test` / `npm run build` 真跑过且绿？
8. **UX**：Loading/Empty/Error/Success？错误信息可懂？键盘可达？双主题不破？
9. **Documentation**：README 环境变量表 / docs/api 索引 / architecture 同步？

## 一票打回项

复制粘贴重复 · 巨大类/方法 · 隐藏副作用 · 魔法字符串/数字 · 异常吞掉 · 日志泄露 · floating Promise · NPE 风险 · 资源未释放 · 连接未关闭 · 无限 Retry/循环 · 无界缓存 · 无超时 AI 请求。

## 完成定义（Definition of Done）

编译通过 · 测试通过 · API 契约正确 · 错误处理完整 · 安全检查过 · 无死代码/调试输出 · 无 Secret 泄露 · 文档同步 · diff 可解释 · 自审（06）通过。全部满足才算 Done，"应该可以"不算。
