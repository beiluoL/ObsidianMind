# 01-frontend-engineering

## Purpose

约束 `apps/frontend/`（Vue 3.5 + TypeScript strict + Vite 6 + Pinia + Vue Router(hash)）的前端工程质量。

## Scope

`apps/frontend/src/` 下所有 `.vue` / `.ts` / `.css`。

## When To Use

- 新增 / 修改组件、composable、store、service、路由、样式。
- 涉及主题、可访问性、Chat 流式 UI、Vault 交互的工作。

## When NOT To Use

- 后端 Java 代码（见 02）；API 契约本身（见 03）。

## Rules

### 1. UI Architecture

- 单组件不超过 ~400 行；超过即拆子组件或抽 composable。现有最大组件约 300 行量级，保持。
- 组件按域归目录：`ui/`（通用弹窗等）、`layout/`、`knowledge/`、`vault/`、`ai/`、`theme/`。跨域通用才进 `ui/`。
- 逻辑复用抽 composable（`useXxx`），不靠 mixin（Vue 3 无 mixin 需求）。
- `<script setup lang="ts">` + Composition API；props 用 `defineProps<{...}>()` 类型化，emits 用 `defineEmits` 显式声明事件名。

### 2. TypeScript

- strict 模式（`tsconfig` 已开）；禁止 `any`（确需时 `unknown` + 收窄）。
- 领域类型集中在 `src/types/`（`knowledge.ts` / `vault.ts`），组件间传递的数据结构必须类型化。
- 与后端 DTO 对应的类型字段名 / 可空性与 03-api-design 保持一致。

### 3. API：统一出口

- 所有后端调用经由 `src/services/api.ts` 的 `apiFetch`；**禁止在组件 / store 中直接 fetch、硬编码 `http://localhost:8080`**。
- 每个后端域一个 service（现有 `aiService` / `knowledgeService` / `searchService` / `settingsService` / `repositories/`），新域新建对应 service 文件。
- 开发环境走 Vite proxy 的 `/api` 相对路径；自定义基地址只通过 `VITE_API_BASE_URL`。
- service 层允许存在 Mock 实现（`services/mock/`）用于后端未就绪阶段；切换真实后端时保持 service 签名不变（aiService 即此模式）。

### 4. State（Pinia）

区分三类状态，不混放：
- **UI State**：弹窗开关、选中项 → 组件内 `ref` 即可，不进 store。
- **Application State**：主题（`theme.ts`）、Vault 连接（`vault.ts`）→ store + localStorage 持久化。
- **Server State**：笔记内容、搜索结果、Chat 消息（`knowledge.ts` / `chat.ts`）→ store 保存，但来源永远是 service 调用，组件不得绕过 store 直接改。

### 5. Loading / Empty / Error / Success

- 任何展示远端或异步数据的视图必须显式处理四态；Loading 用骨架或既有 spinner 风格，Empty 给引导动作，Error 给重试入口。
- 异步按钮（如 Vault 连接）在 `vault.connecting || progress.scanning` 时必须 Loading 且禁用重复点击。

### 6. CSS / Design Tokens / Theme

- **组件内禁止硬编码 `hex` / `rgba`**；颜色一律用 `src/assets/styles/` 的 CSS Variables（`--primary`、`--primary-solid`、`--on-primary`、`--bg-*`、`--text-*` 等）。
- 实心按钮用 `--primary-solid` + `--on-primary`（AA 对比度）；强调 / 描边用 `--primary`。
- 主题三态 system / light / dark，store 为 `stores/theme.ts`（key=`obsidianmind-theme`）；改主题色只动 `theme-dark.css` / `theme-light.css`，不动 tokens.css。
- 新增与主题无关的变量（间距 / 圆角 / 动效）进 `tokens.css`。
- 样式四件套加载顺序固定：tokens → dark → light → base。

### 7. Accessibility

- 交互元素用语义标签（button / nav / main）；键盘可达，focus 可见（base.css 已有 focus-visible，别覆盖）。
- 弹窗统一走 `components/ui/AppModal.vue`（ESC / 遮罩点击 / 焦点陷阱 / data-autofocus），不要手写新弹窗。
- 图标按钮必须带 `aria-label`；文字对比度满足 AA。

### 8. Performance

- 避免重复请求：数据请求放 store action，组件只消费。
- 大列表（文件树、搜索结果）考虑虚拟化或分页——当前数据量小未实现，列表超过 ~500 项时触发。
- 不写无意义的 deep watcher；`watch` 依赖数组精确。
- Markdown 渲染用现有 `marked` 封装，Chat 流式追加时避免整段重解析（Phase 6 实现时落实）。

### 9. Browser API 边界

- Vault 目录访问走 `DataTransferItem.getAsFileSystemHandle()`（仅 Chromium），能力检测降级到手动选择；失败要给用户可见提示。
- 所有浏览器 API 调用做 try/catch，不因 API 不支持而白屏。

## Patterns

- `AppModal.vue`：通用弹窗（焦点管理 + 关闭语义），新弹窗一律复用。
- `VaultConnectModal` → `VaultPicker` → `LocalFirstNotice`：弹窗内分步组件组合。
- `services/repositories/vaultRepository.ts`：前端文件系统仓储封装。

## Anti-Patterns

- 组件里直接 `fetch('http://...')`。
- 硬编码颜色绕过 Token 体系。
- 把 Server State 复制进组件 local ref 造成双源。
- 一切都塞 Pinia（UI 开关也进 store）。
- `v-html` 渲染未消毒的 Markdown（XSS，见 07-security）。

## Checklist

- [ ] 组件 < 400 行，目录归属正确
- [ ] props / emits 类型化，无 `any`
- [ ] 后端调用走 apiFetch + 对应 service
- [ ] 四态（Loading/Empty/Error/Success）齐备
- [ ] 颜色全部走 Token，两种主题下检查过
- [ ] 键盘可操作，图标按钮有 aria-label
- [ ] `npm run build`（vue-tsc + vite）零错误

## Examples

参考 `components/vault/VaultConnectModal.vue`（分步弹窗 + Loading + 能力检测降级）与 `stores/theme.ts`（三态持久化）。

## Related Skills

00-coding-standards、03-api-design（契约同步）、05-testing（前端四态测试）、07-security（XSS / v-html）。

## Project-specific Notes

- 项目目前**无 ESLint / Prettier / Stylelint 配置、无前端测试框架**——这是已知缺口（见 ENGINEERING_AUDIT_V1.md），不要"顺手"引入；引入属于用户决策。
- 演示 / Mock 服务是刻意设计（UI 与数据层解耦），不是待删除的临时代码。

## Status

active
