<script setup lang="ts">
/**
 * AI 输入框：多行输入、Enter 发送（Shift+Enter 换行）、发送/停止状态切换。
 */
import { useChatStore } from '@/stores/chat';
import { Paperclip, Brain, SendHorizontal, Square } from 'lucide-vue-next';
import { computed, ref } from 'vue';

const chat = useChatStore();
const draft = ref('');
const showScope = ref(false);

const scopes = [
  { value: 'note', label: '当前笔记' },
  { value: 'folder', label: '当前文件夹' },
  { value: 'vault', label: '整个知识库' },
  { value: 'custom', label: '指定笔记' },
];

const scopeLabel = computed(() => scopes.find((s) => s.value === chat.scope)?.label ?? '整个知识库');

async function send(): Promise<void> {
  const q = draft.value.trim();
  if (!q || chat.isThinking()) return;
  draft.value = '';
  await chat.ask(q);
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    void send();
  }
}
</script>

<template>
  <div class="chat-input">
    <div v-if="showScope" class="scope-menu">
      <div class="section-label" style="padding: 0 0 6px">知识范围</div>
      <button
        v-for="s in scopes"
        :key="s.value"
        class="scope-menu__item"
        :class="{ 'scope-menu__item--active': chat.scope === s.value }"
        @click="chat.scope = s.value; showScope = false"
      >
        <span class="scope-menu__radio" :class="{ on: chat.scope === s.value }"></span>
        {{ s.label }}
      </button>
    </div>

    <textarea
      v-model="draft"
      class="chat-input__field"
      rows="2"
      placeholder="输入问题..."
      @keydown="onKeydown"
    ></textarea>

    <div class="chat-input__bar">
      <button class="chat-input__tool" @click="showScope = !showScope">
        <Paperclip :size="14" />
        <span>{{ scopeLabel }}</span>
      </button>
      <button class="chat-input__tool">
        <Brain :size="14" />
        <span>{{ chat.model }}</span>
      </button>
      <div class="chat-input__spacer"></div>
      <button
        v-if="chat.isThinking()"
        class="chat-input__send chat-input__send--stop"
        title="停止生成"
        @click="chat.stop()"
      >
        <Square :size="12" />
      </button>
      <button
        v-else
        class="chat-input__send"
        :disabled="!draft.trim()"
        title="发送"
        @click="send"
      >
        <SendHorizontal :size="15" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  position: relative;
  padding: var(--sp-3);
  border-top: 1px solid var(--border);
  background: var(--surface);
}

.chat-input__field {
  width: 100%;
  padding: var(--sp-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  font-size: var(--fs-base);
  color: var(--text-1);
  resize: none;
  transition: border-color var(--dur) var(--ease);
}

.chat-input__field:focus {
  border-color: var(--primary-border);
  outline: none;
}

.chat-input__field::placeholder {
  color: var(--text-3);
}

.chat-input__bar {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.chat-input__tool {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--text-3);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  transition: all var(--dur-fast) var(--ease);
}

.chat-input__tool:hover {
  color: var(--text-1);
  border-color: var(--border-strong);
  background: var(--surface-hover);
}

.chat-input__spacer {
  flex: 1;
}

.chat-input__send {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--r-md);
  background: var(--primary-solid);
  color: var(--on-primary);
  transition: all var(--dur-fast) var(--ease);
}

.chat-input__send:hover:not(:disabled) {
  background: var(--primary-solid-hover);
}

.chat-input__send:active:not(:disabled) {
  transform: scale(0.96);
}

.chat-input__send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.chat-input__send--stop {
  background: var(--surface-2);
  color: var(--text-2);
  border: 1px solid var(--border-strong);
}

.chat-input__send--stop:hover {
  color: var(--danger);
  border-color: var(--danger);
}

.scope-menu {
  position: absolute;
  bottom: 100%;
  left: var(--sp-3);
  width: 180px;
  padding: var(--sp-3);
  background: var(--surface-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-pop);
  z-index: 50;
}

.scope-menu__item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  padding: 5px var(--sp-2);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  color: var(--text-2);
  text-align: left;
}

.scope-menu__item:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.scope-menu__radio {
  width: 12px;
  height: 12px;
  border: 1.5px solid var(--text-3);
  border-radius: var(--r-full);
  flex-shrink: 0;
}

.scope-menu__radio.on {
  border-color: var(--primary);
  background: radial-gradient(circle, var(--primary) 40%, transparent 45%);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
