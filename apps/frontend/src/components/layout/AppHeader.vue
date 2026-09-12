<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { Search, Settings, Gem, ChevronDown } from 'lucide-vue-next';
import { useChatStore } from '@/stores/chat';

const router = useRouter();
const chat = useChatStore();

const showLocalPopover = ref(false);

function onSearchFocus(): void {
  router.push({ name: 'search' });
}

function onSearchEnter(event: KeyboardEvent): void {
  const value = (event.target as HTMLInputElement).value.trim();
  if (!value) return;
  chat.inputDraft = value;
  router.push({ name: 'search', query: { q: value } });
}
</script>

<template>
  <header class="header">
    <div class="header__brand">
      <div class="header__logo">
        <Gem :size="17" :stroke-width="1.8" />
      </div>
      <span class="header__name">ObsidianMind</span>
    </div>

    <div class="header__search">
      <Search :size="15" class="header__search-icon" />
      <input
        class="header__search-input"
        type="text"
        placeholder="搜索笔记、知识，或问 AI..."
        @focus="onSearchFocus"
        @keydown.enter="onSearchEnter"
      />
      <span class="header__search-hint">AI Search</span>
    </div>

    <div class="header__actions">
      <button
        class="header__local"
        :class="{ 'header__local--open': showLocalPopover }"
        @click="showLocalPopover = !showLocalPopover"
      >
        <span class="header__local-dot"></span>
        本地运行
        <ChevronDown :size="13" class="header__local-chevron" :class="{ open: showLocalPopover }" />
      </button>
      <button class="header__icon-btn" title="设置" @click="router.push({ name: 'settings' })">
        <Settings :size="16" :stroke-width="1.7" />
      </button>
    </div>

    <Transition name="pop">
      <div v-if="showLocalPopover" class="local-pop" @mouseleave="showLocalPopover = false">
        <div class="local-pop__title">Local AI</div>
        <div class="local-pop__row">
          <span>Ollama</span>
          <span class="local-pop__status"><span class="dot dot--ok"></span>Connected</span>
        </div>
        <div class="local-pop__row">
          <span>Milvus</span>
          <span class="local-pop__status"><span class="dot dot--ok"></span>Connected</span>
        </div>
        <div class="local-pop__row">
          <span>Embedding</span>
          <span class="local-pop__status"><span class="dot dot--ok"></span>Ready</span>
        </div>
        <div class="local-pop__note">你的数据不会离开本机</div>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.header {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-5);
  height: var(--header-h);
  padding: 0 var(--sp-4);
  background: var(--surface);
}

.header__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: calc(var(--sidebar-w) - var(--sp-4));
  flex-shrink: 0;
}

.header__logo {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-md);
  color: #c4b5fd;
  background: linear-gradient(135deg, rgba(139, 124, 246, 0.22), rgba(96, 165, 250, 0.12));
  border: 1px solid var(--primary-border);
}

.header__name {
  font-size: var(--fs-md);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.header__search {
  flex: 1;
  max-width: 560px;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 32px;
  padding: 0 var(--sp-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  transition: border-color var(--dur) var(--ease), background var(--dur) var(--ease);
}

.header__search:hover,
.header__search:focus-within {
  border-color: var(--primary-border);
  background: var(--surface-hover);
}

.header__search-icon {
  color: var(--text-3);
}

.header__search-input {
  flex: 1;
  font-size: var(--fs-base);
  color: var(--text-1);
}

.header__search-input::placeholder {
  color: var(--text-3);
}

.header__search-hint {
  font-size: var(--fs-xs);
  color: var(--primary);
  background: var(--primary-muted);
  padding: 1px 7px;
  border-radius: var(--r-full);
  white-space: nowrap;
}

.header__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.header__local {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 30px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--text-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-2);
  transition: all var(--dur) var(--ease);
}

.header__local:hover,
.header__local--open {
  border-color: var(--border-strong);
  color: var(--text-1);
  background: var(--surface-hover);
}

.header__local-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--r-full);
  background: var(--success);
  box-shadow: 0 0 6px rgba(74, 222, 128, 0.5);
}

.header__local-chevron {
  transition: transform var(--dur) var(--ease);
}

.header__local-chevron.open {
  transform: rotate(180deg);
}

.header__icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--r-md);
  color: var(--text-2);
  transition: all var(--dur) var(--ease);
}

.header__icon-btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

/* 本地状态弹出层 */
.local-pop {
  position: absolute;
  top: calc(var(--header-h) + 6px);
  right: var(--sp-4);
  width: 264px;
  padding: var(--sp-4);
  background: var(--surface-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-pop);
  z-index: 100;
}

.local-pop__title {
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--sp-3);
}

.local-pop__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-1) 0;
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.local-pop__status {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--success);
}

.local-pop__status--muted {
  color: var(--text-3);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: var(--r-full);
  display: inline-block;
}

.dot--ok {
  background: var(--success);
}

.local-pop__note {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.pop-enter-active,
.pop-leave-active {
  transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
