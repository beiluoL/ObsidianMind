<script setup lang="ts">
/**
 * Chat 模型选择器（Phase 5.5）：头部 [Provider / Model ▼]，Popover 展示可选模型。
 * 流式生成期间 disabled（避免切换造成状态错乱）；未配置任何模型时显示「默认模型」。
 * 不显示 API Key 任何形式的信息。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ChevronDown, Cpu, Cloud, Check } from 'lucide-vue-next';
import { useChatStore } from '@/stores/chat';

const chat = useChatStore();
const open = ref(false);
const wrapRef = ref<HTMLElement | null>(null);

const label = computed(() => (chat.availableModels.length ? chat.model : '默认模型'));

const iconFor = (providerName: string) =>
  providerName.toLowerCase().includes('ollama') ? Cpu : Cloud;

function toggle(): void {
  if (chat.isThinking()) return;
  open.value = !open.value;
  if (open.value) void chat.loadModels();
}

function choose(id: string | null): void {
  chat.selectModel(id);
  open.value = false;
}

function onDocClick(e: MouseEvent): void {
  if (wrapRef.value && !wrapRef.value.contains(e.target as Node)) {
    open.value = false;
  }
}

onMounted(() => document.addEventListener('click', onDocClick));
onBeforeUnmount(() => document.removeEventListener('click', onDocClick));
</script>

<template>
  <div ref="wrapRef" class="ms">
    <button
      class="ms__trigger"
      :disabled="chat.isThinking()"
      :title="chat.isThinking() ? '生成期间不可切换模型' : '切换模型'"
      @click.stop="toggle"
    >
      <span class="ms__dot" :class="{ 'ms__dot--busy': chat.isThinking() }"></span>
      {{ label }}
      <ChevronDown :size="12" />
    </button>

    <div v-if="open" class="ms__popover">
      <button class="ms__option" @click="choose(null)">
        <span class="ms__option-main">
          <span class="ms__option-name">默认模型</span>
          <span class="ms__option-sub">跟随 Model Center 默认设置</span>
        </span>
        <Check v-if="chat.selectedModelId === null" :size="13" class="ms__check" />
      </button>
      <button
        v-for="m in chat.availableModels"
        :key="m.id"
        class="ms__option"
        @click="choose(m.id)"
      >
        <component :is="iconFor(m.providerName)" :size="13" class="ms__option-icon" />
        <span class="ms__option-main">
          <span class="ms__option-name">{{ m.providerName }} / {{ m.displayName }}</span>
          <span class="ms__option-sub">{{ m.modelName }} · {{ m.capabilities.join(' · ') }}</span>
        </span>
        <Check v-if="chat.selectedModelId === m.id" :size="13" class="ms__check" />
      </button>
      <p v-if="!chat.availableModels.length" class="ms__empty">
        尚未配置模型，使用后端默认模型。可在 设置 → AI 模型 中添加。
      </p>
    </div>
  </div>
</template>

<style scoped>
.ms {
  position: relative;
}

.ms__trigger {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  max-width: 220px;
  padding: 4px var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-2);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-full);
  transition: all var(--dur-fast) var(--ease);
}

.ms__trigger:hover:not(:disabled) {
  color: var(--text-1);
  border-color: var(--border-strong);
}

.ms__trigger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ms__dot {
  width: 7px;
  height: 7px;
  border-radius: var(--r-full);
  background: var(--success);
  flex-shrink: 0;
}

.ms__dot--busy {
  background: var(--warning, var(--primary));
  animation: pulse-soft 1.1s ease-in-out infinite;
}

@keyframes pulse-soft {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

.ms__popover {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 50;
  width: 280px;
  padding: var(--sp-2);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-lg, 0 8px 24px rgb(0 0 0 / 12%));
}

.ms__option {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-sm);
  text-align: left;
  transition: background var(--dur-fast) var(--ease);
}

.ms__option:hover {
  background: var(--surface-hover);
}

.ms__option-icon {
  color: var(--text-3);
  flex-shrink: 0;
}

.ms__option-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.ms__option-name {
  font-size: var(--fs-xs);
  font-weight: 550;
  color: var(--text-1);
}

.ms__option-sub {
  font-size: 10px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms__check {
  color: var(--primary);
  flex-shrink: 0;
}

.ms__empty {
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: 1.6;
}
</style>
