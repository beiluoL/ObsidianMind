# Design System / Theme 体系

> 适用范围：`apps/frontend`。本文是 UI 改造（浅色主题 + Dark/Light 切换 + Vault 导入弹窗重构）的实现说明与扩展约定。

## 1. 文件结构

```text
src/assets/styles/
├── tokens.css        # 骨架变量（与主题无关）：字体 / 字号 / 间距 / 圆角 / 布局 / 动效 / z-index
├── theme-dark.css    # 深色配色：` :root`（默认）+ `[data-theme="dark"]`
├── theme-light.css   # 浅色配色：`[data-theme="light"]`（特异性高于 :root，无需 !important）
└── base.css          # reset、通用元素、:focus-visible、.sr-only、.md-body
```

`main.ts` 中的加载顺序必须是：`tokens → theme-dark → theme-light → base`。

## 2. 主题运行时

| 项 | 说明 |
| --- | --- |
| Store | `src/stores/theme.ts`（Pinia，`useThemeStore`） |
| 模式 | `system` / `light` / `dark`，默认 `system` |
| 持久化 | `localStorage['obsidianmind-theme']` |
| 生效方式 | `<html data-theme="light|dark">` + `documentElement.style.colorScheme` |
| 系统跟随 | `matchMedia('(prefers-color-scheme: dark)')`，Safari < 14 走 `addListener` 兜底 |
| 防闪烁 | `index.html` 内联脚本在样式解析前写入 `data-theme`，与 store 共用同一份 key |

**改主题色时的唯一入口是 `theme-dark.css` / `theme-light.css`**，不要回组件里找颜色。

## 3. Token 命名约定

| 类别 | Token |
| --- | --- |
| 背景/表面 | `--bg` `--surface` `--surface-2` `--surface-hover` `--surface-active` `--surface-elevated` |
| 描边 | `--border` `--border-strong` |
| 文字 | `--text-1` `--text-2` `--text-3` |
| 品牌 | `--primary`（强调/图标/选中）`--primary-solid`（实心按钮底色）`--on-primary`（按钮文字）`--primary-muted` `--primary-border` `--primary-strong-bg` `--brand-icon` `--brand-gradient` |
| 状态 | `--success/--warning/--danger/--info` + 各自 `-soft`（弱底）`-border`（描边）`-glow`（光晕） |
| 遮罩 | `--overlay` `--overlay-blur`；半透明浮层 `--glass-1` `--glass-2` `--graph-glow` |
| 阴影 | `--shadow-sm/md/lg` + 兼容别名 `--shadow-panel` `--shadow-pop` |
| 圆角/间距 | `--r-sm/md/lg/xl/full`；`--sp-1..10` |

### 为什么要 `--primary` 和 `--primary-solid` 两个

深色主题的品牌紫 `#8b7cf6` 上放白字对比度只有 3.3:1，达不到 AA。
因此：**非文字场景（图标、描边、选中态背景）用 `--primary`，实心按钮底色用加深过的 `--primary-solid`**，
两套颜色同色相，观感一致但按钮文字对比度 ≥ 4.5:1。浅色主题下两者取值相同（都已加深）。

## 4. 扩展约定（新增页面/组件时）

1. 只引用 Token，不写 `hex` / `rgba` 字面量（灰色地带先补 Token 再用）。
2. 可交互元素必须有 `:hover` / `:active` / `:disabled`，键盘可达用 `:focus-visible`（`base.css` 已有全局规则）。
3. 浅色主题靠「阴影 + 细描边」分层，不要照搬深色的重描边。
4. 弹窗统一用 `components/ui/AppModal.vue`（遮罩、ESC、点遮罩关闭、焦点归还、动画都由它负责）；
   需要把初始焦点给某个主操作时，在该元素上加 `data-autofocus`。

## 5. Vault 导入弹窗

```text
src/components/
├── ui/AppModal.vue              # 通用模态壳：遮罩/动画/焦点陷阱/ESC/点遮罩关闭
└── vault/
    ├── VaultConnectModal.vue    # 弹窗编排 + 错误文案转译（信息层级 1/3/4）
    ├── VaultPicker.vue          # 信息层级 2：拖拽落区 + Primary 按钮 + Loading
    ├── LocalFirstNotice.vue     # 信息层级 3：Local First 轻量说明
    └── VaultWelcome.vue         # 兼容入口（内部渲染 VaultConnectModal，App.vue 挂载点不变）
```

- 拖拽能力基于 `DataTransferItem.getAsFileSystemHandle()`（仅 Chromium），不支持时落区退化为提示文案，不假装能读目录。
- Loading 约定：`vault.connecting || vault.progress.scanning` 期间按钮显示「正在读取 Vault…」并禁用；扫描失败保持弹窗打开展示错误。
- 错误文案只给用户能看懂的中文，原始报错走 `console.error`。
