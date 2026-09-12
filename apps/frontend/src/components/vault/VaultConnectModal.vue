<script setup lang="ts">
/**
 * Vault 连接弹窗 —— 首次进入 ObsidianMind 的主引导。
 *
 * 信息层级（由强到弱）：
 *   1. 连接你的 Obsidian Vault（标题）
 *   2. 选择 Obsidian Vault（Primary Action，唯一高权重按钮）
 *   3. Local First（轻量说明）
 *   4. 使用演示 Vault（文字型 Secondary Action）
 *
 * 只做 UI 编排与错误文案转译，所有文件读取仍走 vault store / vaultRepository。
 */
import { computed, ref } from 'vue';
import { X, AlertTriangle, ArrowRight, Loader2 } from 'lucide-vue-next';
import AppModal from '@/components/ui/AppModal.vue';
import VaultPicker from '@/components/vault/VaultPicker.vue';
import LocalFirstNotice from '@/components/vault/LocalFirstNotice.vue';
import { useVaultStore } from '@/stores/vault';
import { vaultRepository } from '@/services/repositories/vaultRepository';

const vault = useVaultStore();

const TITLE_ID = 'vault-connect-title';
/** 本地错误（拖拽被拒等未进入 store 的场景） */
const localError = ref('');

const mode = computed<'first' | 'restore'>(() =>
  vault.status === 'needs-permission' ? 'restore' : 'first',
);
const restoreName = computed(() => vault.pendingHandle?.name ?? '');
const busy = computed(() => vault.connecting || vault.progress.scanning);
const dropSupported = computed(() => vaultRepository.dropSupported);

/** 把底层错误转译为用户能看懂的中文，原始信息只进 console */
function friendly(raw: string): string {
  if (!raw) return '';
  if (raw.includes('abort')) return ''; // 用户主动取消，不算错误
  if (raw.includes('不支持')) return '当前浏览器不支持选择本地目录，请使用 Chrome / Edge，或先体验演示 Vault。';
  if (raw.includes('拒绝') || raw.includes('denied')) return '目录访问被拒绝，请重新选择一个你有读取权限的文件夹。';
  return '无法读取 Vault，请确认你选择的是有效的 Obsidian Vault 文件夹。';
}

const errorText = computed(() => localError.value || friendly(vault.error));

function logError(raw: string): void {
  if (raw) console.error('[VaultConnect]', raw);
}

async function run(action: () => Promise<unknown>): Promise<void> {
  if (busy.value) return;
  localError.value = '';
  try {
    await action();
  } catch (err) {
    localError.value = friendly((err as Error).message);
    logError((err as Error).message);
  }
}

function pick(): void {
  void run(async () => {
    if (mode.value === 'restore') {
      const ok = await vault.grantPermission();
      if (!ok) await vault.pickAndConnect();
    } else {
      await vault.pickAndConnect();
    }
    logError(vault.error);
  });
}

function onDrop(handle: FileSystemDirectoryHandle): void {
  void run(() => vault.connectHandle(handle));
}

function onDropReject(): void {
  localError.value = '无法识别拖入的内容，请拖入一个文件夹，或点击下方按钮选择。';
}

function useDemo(): void {
  void run(() => vault.connectDemo());
}

function close(): void {
  if (busy.value) return;
  vault.dismissWelcome();
}
</script>

<template>
  <AppModal :title-id="TITLE_ID" :dismissible="!busy" width="var(--modal-w)" @close="close()">
    <!-- 品牌行 -->
    <header class="vc__head">
      <div class="vc__brand">
        <span class="vc__brand-glyph" aria-hidden="true">◈</span>
        <span class="vc__brand-name">ObsidianMind</span>
      </div>
      <button
        class="vc__close"
        type="button"
        aria-label="关闭"
        title="暂不连接"
        :disabled="busy"
        @click="close()"
      >
        <X :size="16" :stroke-width="1.8" />
      </button>
    </header>

    <div class="vc__body">
      <!-- 第一层级：标题 -->
      <h2 :id="TITLE_ID" class="vc__title">
        {{ mode === 'restore' ? '欢迎回来' : '连接你的 Obsidian Vault' }}
      </h2>
      <p class="vc__subtitle">
        {{ mode === 'restore' ? '重新授权后即可继续读取你的知识库' : '从你的 Markdown 知识开始' }}
      </p>

      <!-- 第二层级：主操作 -->
      <VaultPicker
        class="vc__picker"
        :mode="mode"
        :busy="busy"
        :retry="!!errorText"
        :drop-supported="dropSupported"
        :restore-name="restoreName"
        @pick="pick()"
        @drop="onDrop"
        @drop-reject="onDropReject()"
      />

      <!-- 错误态 -->
      <div v-if="errorText" class="vc__error" role="alert">
        <AlertTriangle :size="15" :stroke-width="1.8" class="vc__error-icon" />
        <div class="vc__error-body">
          <p class="vc__error-title">无法读取 Vault</p>
          <p class="vc__error-text">{{ errorText }}</p>
        </div>
      </div>

      <!-- 第三层级：Local First -->
      <LocalFirstNotice class="vc__local" />

      <!-- 第四层级：演示 Vault -->
      <div class="vc__demo">
        <span class="vc__demo-label">还没有 Vault？</span>
        <button class="vc__demo-btn" type="button" :disabled="busy" @click="useDemo()">
          <Loader2 v-if="busy" :size="13" class="vc__demo-spin" />
          使用演示 Vault
          <ArrowRight :size="13" :stroke-width="2" />
        </button>
      </div>
      <p class="vc__demo-hint">
        演示 Vault 写入浏览器内置存储（OPFS），读写与监听和真实目录走同一套引擎。
      </p>
    </div>
  </AppModal>
</template>

<style scoped>
.vc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-4) var(--sp-5) 0;
}

.vc__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.vc__brand-glyph {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  font-size: 13px;
  line-height: 1;
  color: var(--brand-icon);
  background: var(--brand-gradient);
  border: 1px solid var(--primary-border);
  border-radius: var(--r-md);
}

.vc__brand-name {
  font-size: var(--fs-base);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.vc__close {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-sm);
  color: var(--text-3);
  transition: color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.vc__close:hover:not(:disabled) {
  background: var(--surface-hover);
  color: var(--text-1);
}

.vc__close:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.vc__body {
  padding: var(--sp-3) var(--sp-6) var(--sp-5);
}

.vc__title {
  margin-top: var(--sp-3);
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.3;
  text-align: center;
}

.vc__subtitle {
  margin-top: var(--sp-1);
  font-size: var(--fs-base);
  color: var(--text-3);
  text-align: center;
}

.vc__picker {
  margin-top: var(--sp-5);
}

/* ---------- 错误态 ---------- */
.vc__error {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--danger-soft);
  border: 1px solid var(--danger-border);
  border-radius: var(--r-md);
}

.vc__error-icon {
  margin-top: 1px;
  color: var(--danger);
  flex-shrink: 0;
}

.vc__error-body {
  min-width: 0;
}

.vc__error-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--danger);
}

.vc__error-text {
  margin-top: 2px;
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--text-2);
}

.vc__local {
  margin-top: var(--sp-5);
}

/* ---------- 演示 Vault：明显弱于 Primary ---------- */
.vc__demo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border);
}

.vc__demo-label {
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.vc__demo-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 2px;
  font-size: var(--fs-sm);
  color: var(--text-2);
  border-radius: var(--r-sm);
  transition: color var(--dur-fast) var(--ease);
}

.vc__demo-btn:hover:not(:disabled) {
  color: var(--primary);
}

.vc__demo-btn:disabled {
  opacity: 0.55;
  cursor: progress;
}

.vc__demo-btn:hover:not(:disabled) :deep(svg:last-child) {
  transform: translateX(2px);
}

.vc__demo-btn :deep(svg:last-child) {
  transition: transform var(--dur-fast) var(--ease);
}

.vc__demo-spin {
  animation: vc-spin 0.9s linear infinite;
}

@keyframes vc-spin {
  to {
    transform: rotate(360deg);
  }
}

.vc__demo-hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--text-3);
  text-align: center;
}

/* ---------- 响应式：手机端不裁切、不出横向滚动 ---------- */
@media (max-width: 560px) {
  .vc__body {
    padding: var(--sp-2) var(--sp-4) var(--sp-4);
  }

  .vc__title {
    font-size: var(--fs-lg);
  }

  .picker :deep(.picker__primary) {
    width: 100%;
    min-width: 0;
  }
}
</style>
