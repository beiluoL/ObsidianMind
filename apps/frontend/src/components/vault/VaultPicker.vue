<script setup lang="ts">
/**
 * Vault 选择区（第一操作层级）：
 * 内置卡片式拖拽落区 + Primary Button。
 *
 * 拖拽能力基于 File System Access API 的 DataTransferItem.getAsFileSystemHandle()，
 * 仅 Chromium 系可用；不支持时落区不隐藏，但会把「拖入」降级为提示点击按钮，
 * 不假装浏览器能任意读取目录。
 */
import { computed, ref } from 'vue';
import { FolderOpen, Loader2, Gem } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    /** 正在连接 / 扫描中 */
    busy?: boolean;
    /** 是否支持拖入目录（Chromium + File System Access API） */
    dropSupported?: boolean;
    /** 恢复授权模式下的 Vault 名 */
    restoreName?: string;
    mode?: 'first' | 'restore';
    /** 上一次失败后，主按钮改为「重新选择 Vault」 */
    retry?: boolean;
  }>(),
  { busy: false, dropSupported: false, restoreName: '', mode: 'first', retry: false },
);

const emit = defineEmits<{
  (e: 'pick'): void;
  (e: 'drop', handle: FileSystemDirectoryHandle): void;
  (e: 'drop-reject'): void;
}>();

const dragging = ref(false);
let dragDepth = 0;

const primaryLabel = computed(() => (props.mode === 'restore' ? '重新授权此目录' : '选择 Obsidian Vault'));
const dropHint = computed(() =>
  props.busy
    ? '正在读取 Vault…'
    : props.dropSupported
      ? '或将 Vault 文件夹拖到这里'
      : '浏览器将弹出目录选择窗口，由你主动授权',
);

function onDragEnter(event: DragEvent): void {
  if (props.busy) return;
  event.preventDefault();
  dragDepth++;
  dragging.value = true;
}

function onDragOver(event: DragEvent): void {
  if (props.busy) return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
}

function onDragLeave(event: DragEvent): void {
  event.preventDefault();
  dragDepth = Math.max(0, dragDepth - 1);
  if (dragDepth === 0) dragging.value = false;
}

async function onDrop(event: DragEvent): Promise<void> {
  event.preventDefault();
  dragDepth = 0;
  dragging.value = false;
  if (props.busy) return;

  if (!props.dropSupported) {
    emit('drop-reject');
    return;
  }

  const item = event.dataTransfer?.items?.[0];
  const itemWithHandle = item as (DataTransferItem & {
    getAsFileSystemHandle?: () => Promise<FileSystemHandle | null>;
  }) | undefined;

  if (!item || typeof itemWithHandle?.getAsFileSystemHandle !== 'function') {
    emit('drop-reject');
    return;
  }

  try {
    const handle = await itemWithHandle.getAsFileSystemHandle();
    if (handle && handle.kind === 'directory') {
      emit('drop', handle as FileSystemDirectoryHandle);
    } else {
      emit('drop-reject');
    }
  } catch {
    emit('drop-reject');
  }
}
</script>

<template>
  <div
    class="picker"
    :class="{ 'picker--dragging': dragging, 'picker--busy': busy }"
    @dragenter="onDragEnter"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <div class="picker__glyph" :class="{ 'picker__glyph--spin': busy }">
      <Loader2 v-if="busy" :size="20" :stroke-width="1.7" />
      <Gem v-else :size="20" :stroke-width="1.5" />
    </div>

    <h3 class="picker__title">选择你的 Vault</h3>
    <p class="picker__desc">
      <template v-if="mode === 'restore'">
        检测到你之前连接过 <strong>{{ restoreName }}</strong
        >，浏览器需要你重新确认授权后才能读取。
      </template>
      <template v-else> 选择一个本地 Obsidian Vault，ObsidianMind 不会上传你的文件。 </template>
    </p>

    <button
      class="picker__primary"
      type="button"
      :disabled="busy"
      :aria-busy="busy"
      @click="emit('pick')"
    >
      <Loader2 v-if="busy" :size="15" class="picker__spinner" />
      <FolderOpen v-else :size="15" :stroke-width="1.9" />
      {{ primaryLabel }}
    </button>

    <p class="picker__hint">{{ dropHint }}</p>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--sp-6) var(--sp-5) var(--sp-5);
  background: var(--surface-2);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-lg);
  transition: border-color var(--dur) var(--ease), background var(--dur) var(--ease);
}

.picker--dragging {
  border-color: var(--primary);
  border-style: solid;
  background: var(--primary-muted);
}

.picker__glyph {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--r-lg);
  color: var(--brand-icon);
  background: var(--brand-gradient);
  border: 1px solid var(--primary-border);
}

.picker__glyph--spin {
  color: var(--primary);
}

.picker__spinner,
.picker__glyph--spin :deep(svg) {
  animation: picker-spin 0.9s linear infinite;
}

@keyframes picker-spin {
  to {
    transform: rotate(360deg);
  }
}

.picker__title {
  margin-top: var(--sp-3);
  font-size: var(--fs-md);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.picker__desc {
  margin-top: var(--sp-1);
  max-width: 34ch;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--text-2);
}

.picker__desc strong {
  color: var(--text-1);
  font-weight: 600;
}

/* ---------- Primary Action：整个弹窗视觉权重最高 ---------- */
.picker__primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  min-width: 208px;
  margin-top: var(--sp-4);
  padding: 10px var(--sp-5);
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--on-primary);
  background: var(--primary-solid);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-sm);
  transition: background var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease), opacity var(--dur-fast) var(--ease);
}

.picker__primary:hover:not(:disabled) {
  background: var(--primary-solid-hover);
  box-shadow: var(--shadow-md);
}

.picker__primary:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: var(--shadow-sm);
}

.picker__primary:disabled {
  opacity: 0.72;
  cursor: progress;
  box-shadow: none;
}

.picker__hint {
  margin-top: var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
}
</style>
