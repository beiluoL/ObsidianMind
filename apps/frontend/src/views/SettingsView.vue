<script setup lang="ts">
/**
 * 设置页：Vault 连接管理（连接/断开/重扫）、演示 Vault 说明与索引状态展示。
 */
import { onMounted, ref } from 'vue';
import {
  Bot,
  Database,
  Boxes,
  HardDrive,
  RefreshCw,
  Palette,
  SlidersHorizontal,
  Check,
  FolderOpen,
  Loader2,
} from 'lucide-vue-next';
import type { AppSettings, IndexResult } from '@/types/knowledge';
import { indexService, settingsService } from '@/services/settingsService';
import { useVaultStore } from '@/stores/vault';
import ThemeSwitcher from '@/components/theme/ThemeSwitcher.vue';

const active = ref('knowledge');
const settings = ref<AppSettings | null>(null);
const vault = useVaultStore();
const fsSupported = 'showDirectoryPicker' in window;

/** Phase 3 知识索引：同步执行 + 结果展示（Loading / Error / Result 三态） */
const indexRunning = ref(false);
const indexResult = ref<IndexResult | null>(null);
const indexError = ref('');

async function runIndex(): Promise<void> {
  if (indexRunning.value) {
    return;
  }
  indexRunning.value = true;
  indexError.value = '';
  try {
    indexResult.value = await indexService.triggerReindex();
  } catch (err) {
    indexError.value = (err as Error).message;
  } finally {
    indexRunning.value = false;
  }
}

const sections = [
  { key: 'model', label: 'AI 模型', icon: Bot },
  { key: 'knowledge', label: '知识库', icon: Boxes },
  { key: 'embedding', label: 'Embedding', icon: SlidersHorizontal },
  { key: 'milvus', label: '向量数据库', icon: Database },
  { key: 'index', label: '索引', icon: RefreshCw },
  { key: 'appearance', label: '外观', icon: Palette },
  { key: 'advanced', label: '高级', icon: HardDrive },
];

onMounted(async () => {
  settings.value = await settingsService.getSettings();
});
</script>

<template>
  <div class="settings">
    <!-- 左侧分区导航 -->
    <aside class="settings__nav">
      <h1 class="settings__title">设置</h1>
      <button
        v-for="s in sections"
        :key="s.key"
        class="settings__nav-item"
        :class="{ 'settings__nav-item--active': active === s.key }"
        @click="active = s.key"
      >
        <component :is="s.icon" :size="14" :stroke-width="1.7" />
        {{ s.label }}
      </button>
    </aside>

    <!-- 右侧内容 -->
    <div class="settings__content" v-if="settings">
      <!-- AI 模型（Phase 5.5 Model Center） -->
      <section v-if="active === 'model'" class="settings__section settings__section--wide">
        <ModelCenter />
      </section>

      <!-- Embedding -->
      <section v-if="active === 'embedding'" class="settings__section">
        <h2>Embedding Provider</h2>
        <div class="field">
          <label class="field__label">Provider</label>
          <div class="field__static">Ollama</div>
        </div>
        <div class="field">
          <label class="field__label">Model</label>
          <div class="field__static">
            {{ settings.embeddingModel }}
            <span class="field__badge"><Check :size="11" /> Ready</span>
          </div>
        </div>
        <p class="settings__note">查询与文档必须使用同一 Embedding 模型，否则检索失效。</p>
      </section>

      <!-- 向量数据库 -->
      <section v-if="active === 'milvus'" class="settings__section">
        <h2>Milvus</h2>
        <div class="field-row">
          <div class="field">
            <label class="field__label">Host</label>
            <input class="field__input" :value="settings.milvusHost" />
          </div>
          <div class="field">
            <label class="field__label">Port</label>
            <input class="field__input" :value="settings.milvusPort" />
          </div>
        </div>
        <div class="field">
          <label class="field__label">状态</label>
          <div class="field__static">
            <span class="status-dot status-dot--ok"></span> Connected
          </div>
        </div>
      </section>

      <!-- 知识库：真实 Vault 连接 -->
      <section v-if="active === 'knowledge'" class="settings__section">
        <h2>Obsidian Vault</h2>

        <!-- 未连接 -->
        <template v-if="!vault.info">
          <div class="vault-empty">
            <FolderOpen :size="28" :stroke-width="1.4" class="vault-empty__icon" />
            <p class="vault-empty__title">尚未连接知识库</p>
            <p class="vault-empty__desc">选择你的 Obsidian Vault 文件夹，ObsidianMind 只读取你主动授权的目录。</p>
            <div class="vault-empty__actions">
              <button class="settings__btn" @click="vault.pickAndConnect()">选择 Vault</button>
              <button class="settings__btn settings__btn--ghost" @click="vault.connectDemo()">载入演示 Vault</button>
            </div>
            <p v-if="!fsSupported" class="settings__note">⚠️ 当前浏览器不支持 File System Access API（需 Chrome / Edge），仅可使用演示 Vault。</p>
          </div>
        </template>

        <!-- 已连接 -->
        <template v-else>
          <div class="vault-card">
            <div class="vault-card__row">
              <FolderOpen :size="18" class="vault-card__icon" />
              <div class="vault-card__path">
                <span class="vault-card__name">{{ vault.info.name }}</span>
                <span class="vault-card__sub">{{ vault.info.path }}</span>
              </div>
              <span class="field__badge"><Check :size="11" /> 已连接</span>
            </div>
            <div class="vault-card__stats">
              <div class="vault-card__stat">
                <span class="vault-card__num">{{ vault.info.noteCount.toLocaleString() }}</span>
                <span class="vault-card__label">Notes</span>
              </div>
              <div class="vault-card__stat">
                <span class="vault-card__num">{{ vault.info.folderCount.toLocaleString() }}</span>
                <span class="vault-card__label">Folders</span>
              </div>
              <div class="vault-card__stat">
                <span class="vault-card__num">{{ vault.info.tagCount.toLocaleString() }}</span>
                <span class="vault-card__label">Tags</span>
              </div>
              <div class="vault-card__stat">
                <span class="vault-card__num">{{ vault.info.linkCount.toLocaleString() }}</span>
                <span class="vault-card__label">Links</span>
              </div>
            </div>
            <div class="vault-card__actions">
              <button class="settings__btn" :disabled="vault.progress.scanning" @click="vault.rescan()">
                <Loader2 v-if="vault.progress.scanning" :size="13" class="spin" />
                重新扫描
              </button>
              <button class="settings__btn settings__btn--ghost" @click="vault.pickAndConnect()">更改 Vault</button>
              <button class="settings__btn settings__btn--danger" @click="vault.disconnect()">断开连接</button>
            </div>
            <p class="vault-card__watch">
              <RefreshCw :size="11" />
              文件监听已开启：每 8 秒增量检测文件变更（新建 / 修改 / 删除）
            </p>
          </div>
          <div class="field">
            <label class="field__label">分块策略</label>
            <div class="field__static">标题边界分块 · 800 字符 · overlap 100（ai.chunk 可配置）</div>
          </div>
          <p class="settings__note">Markdown 是 Source of Truth，ObsidianMind 绝不改动你的目录结构与 Frontmatter。</p>
        </template>
      </section>

      <!-- 索引 -->
      <section v-if="active === 'index'" class="settings__section">
        <h2>索引</h2>
        <div class="field">
          <label class="field__label">自动索引</label>
          <button
            class="toggle"
            :class="{ 'toggle--on': settings.autoIndex }"
            @click="settings.autoIndex = !settings.autoIndex"
          >
            <span class="toggle__knob"></span>
          </button>
        </div>
        <div class="field">
          <label class="field__label">索引间隔</label>
          <div class="field__static">{{ settings.indexInterval }}</div>
        </div>
        <button class="settings__btn" :disabled="indexRunning" @click="runIndex">
          <Loader2 v-if="indexRunning" :size="13" class="spin" />
          {{ indexRunning ? '正在同步知识库…' : '立即同步知识库' }}
        </button>
        <p v-if="indexError" class="settings__note">同步失败：{{ indexError }}</p>
        <p v-if="indexResult" class="settings__note">
          同步完成：已发现 {{ indexResult.total }} ·
          新增 {{ indexResult.indexed }} ·
          更新 {{ indexResult.updated }} ·
          跳过 {{ indexResult.skipped }} ·
          删除 {{ indexResult.deleted }} ·
          失败 {{ indexResult.failed }} ·
          共 {{ indexResult.chunkCount }} 个 Chunk（{{ indexResult.elapsedMs }}ms）
          <template v-if="indexResult.errors.length">
            <br />失败明细：<span v-for="e in indexResult.errors" :key="e.documentId">
              {{ e.documentId }}（{{ e.code }}）</span>
          </template>
        </p>
      </section>

      <!-- 外观 -->
      <section v-if="active === 'appearance'" class="settings__section">
        <h2>外观</h2>
        <div class="field">
          <span class="field__label">主题</span>
          <ThemeSwitcher />
        </div>
        <p class="settings__note">
          选择「跟随系统」时，ObsidianMind 会跟随 macOS / Windows 的外观设置自动切换；
          选择浅色或深色后将固定下来，并在刷新后保持。
        </p>
      </section>

      <!-- 高级 -->
      <section v-if="active === 'advanced'" class="settings__section">
        <h2>高级</h2>
        <div class="field">
          <label class="field__label">检索 Top-K</label>
          <input class="field__input" value="6" />
        </div>
        <div class="field">
          <label class="field__label">相似度阈值</label>
          <input class="field__input" value="0.70" />
        </div>
        <p class="settings__note">这些参数已按经验值预配置，通常无需修改。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings {
  display: flex;
  height: 100%;
}

.settings__nav {
  width: 220px;
  flex-shrink: 0;
  padding: var(--sp-5) var(--sp-3);
  border-right: 1px solid var(--border);
  background: var(--surface);
}

.settings__title {
  font-size: var(--fs-lg);
  font-weight: 700;
  padding: 0 var(--sp-2) var(--sp-4);
}

.settings__nav-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
  padding: 7px var(--sp-3);
  margin-bottom: 1px;
  border-radius: var(--r-md);
  font-size: var(--fs-base);
  color: var(--text-2);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.settings__nav-item:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.settings__nav-item--active {
  background: var(--primary-muted);
  color: var(--primary);
  font-weight: 550;
}

.settings__content {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-8);
}

.settings__section {
  max-width: 560px;
}

.settings__section--wide {
  max-width: 760px;
}

.settings__section h2 {
  font-size: var(--fs-lg);
  font-weight: 650;
  margin-bottom: var(--sp-5);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--border);
}

.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
}

.field-row {
  display: flex;
  gap: var(--sp-4);
}

.field-row .field {
  flex: 1;
}

.field__label {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.field__static {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-1);
}

.field__badge {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 1px 8px;
  font-size: var(--fs-xs);
  color: var(--success);
  background: var(--success-soft);
  border-radius: var(--r-full);
}

.field__options {
  display: flex;
  gap: var(--sp-2);
}

.field__option {
  padding: 5px var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--text-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.field__option:hover:not(:disabled) {
  color: var(--text-1);
  border-color: var(--border-strong);
}

.field__option--active {
  color: var(--primary);
  border-color: var(--primary-border);
  background: var(--primary-muted);
  font-weight: 550;
}

.field__option:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.field__input {
  width: 200px;
  padding: 6px var(--sp-3);
  font-size: var(--fs-sm);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  color: var(--text-1);
  text-align: right;
}

.field__input:focus {
  outline: none;
  border-color: var(--primary-border);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--r-full);
}

.status-dot--ok {
  background: var(--success);
  box-shadow: 0 0 6px var(--success-glow);
}

.toggle {
  width: 36px;
  height: 20px;
  background: var(--surface-active);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-full);
  position: relative;
  transition: all var(--dur) var(--ease);
}

.toggle--on {
  background: var(--primary);
  border-color: var(--primary);
}

.toggle__knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background: var(--knob);
  border-radius: var(--r-full);
  transition: transform var(--dur) var(--ease);
}

.toggle--on .toggle__knob {
  transform: translateX(16px);
}

.settings__btn {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
  padding: 7px var(--sp-4);
  font-size: var(--fs-sm);
  font-weight: 550;
  color: var(--on-primary);
  background: var(--primary-solid);
  border-radius: var(--r-md);
  transition: background var(--dur-fast) var(--ease);
}

.settings__btn:hover:not(:disabled) {
  background: var(--primary-solid-hover);
}

.settings__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.settings__btn--ghost {
  color: var(--text-2);
  background: transparent;
  border: 1px solid var(--border-strong);
}

.settings__btn--ghost:hover {
  color: var(--text-1);
  background: var(--surface-hover);
}

.settings__btn--danger {
  color: var(--danger);
  background: transparent;
  border: 1px solid var(--danger-border);
}

.settings__btn--danger:hover {
  background: var(--danger-soft);
}

/* Vault 卡片 */
.vault-empty {
  padding: var(--sp-6);
  text-align: center;
  background: var(--surface);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-lg);
}

.vault-empty__icon {
  color: var(--text-3);
}

.vault-empty__title {
  margin-top: var(--sp-3);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}

.vault-empty__desc {
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--text-3);
  line-height: 1.7;
}

.vault-empty__actions {
  display: flex;
  justify-content: center;
  gap: var(--sp-3);
}

.vault-card {
  padding: var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
}

.vault-card__row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.vault-card__icon {
  color: var(--primary);
}

.vault-card__path {
  flex: 1;
  min-width: 0;
}

.vault-card__name {
  display: block;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}

.vault-card__sub {
  display: block;
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.vault-card__stats {
  display: flex;
  gap: var(--sp-6);
  margin-top: var(--sp-4);
  padding: var(--sp-3) 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.vault-card__stat {
  display: flex;
  flex-direction: column;
}

.vault-card__num {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.vault-card__label {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.vault-card__actions {
  display: flex;
  gap: var(--sp-3);
}

.vault-card__actions .settings__btn {
  margin-top: var(--sp-4);
}

.vault-card__watch {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.settings__note {
  margin-top: var(--sp-4);
  padding: var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  line-height: 1.7;
}
</style>
