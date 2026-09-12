<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { Bot, Sparkles, Trash2 } from 'lucide-vue-next';
import { useChatStore } from '@/stores/chat';
import ChatMessage from './ChatMessage.vue';
import ChatInput from './ChatInput.vue';

const chat = useChatStore();
const listRef = ref<HTMLElement | null>(null);
const activeTab = ref<'chat' | 'scenes' | 'settings'>('chat');

const tabs = [
  { key: 'chat', label: '对话' },
  { key: 'scenes', label: '使用场景' },
  { key: 'settings', label: '设置' },
] as const;

const scenes = [
  { icon: '✨', title: '解释', desc: '用大白话解释选中的概念' },
  { icon: '📝', title: '总结', desc: '把长笔记压缩成要点' },
  { icon: '🔗', title: '查找关联', desc: '找出与当前主题相关的知识' },
  { icon: '❓', title: '找出疑点', desc: '挑战笔记中可能错误的地方' },
  { icon: '🧠', title: '深入讲解', desc: '像导师一样逐层深入' },
  { icon: '✍️', title: '改写', desc: '润色表达，保持原意' },
];

const suggestions = [
  'RAG 和普通 LLM 有什么区别？',
  'JVM 内存是怎么划分的？',
  'volatile 和 synchronized 怎么选？',
  'Agent 是什么？和 RAG 有什么关系？',
];

watch(
  () => chat.messages.length,
  async () => {
    await nextTick();
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight;
    }
  },
);
</script>

<template>
  <section class="chat-panel">
    <header class="chat-panel__head">
      <div class="chat-panel__title">
        <Bot :size="15" class="chat-panel__bot" />
        <span>AI 助手</span>
      </div>
      <div class="chat-panel__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="chat-panel__tab"
          :class="{ 'chat-panel__tab--active': activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
      <button class="chat-panel__clear" title="清空对话" @click="chat.clearConversation">
        <Trash2 :size="13" />
      </button>
    </header>

    <!-- 对话 -->
    <div v-if="activeTab === 'chat'" ref="listRef" class="chat-panel__list">
      <!-- 空状态 -->
      <div v-if="chat.messages.length === 0" class="chat-panel__empty">
        <div class="chat-panel__empty-icon"><Sparkles :size="20" /></div>
        <div class="chat-panel__empty-title">Ask your Knowledge</div>
        <div class="chat-panel__empty-desc">AI 已索引你的整个知识库，试试问：</div>
        <button
          v-for="s in suggestions"
          :key="s"
          class="chat-panel__suggestion"
          @click="chat.ask(s)"
        >
          {{ s }}
        </button>
      </div>

      <ChatMessage v-for="msg in chat.messages" :key="msg.id" :message="msg" />

      <!-- 思考中 -->
      <div v-if="chat.isThinking()" class="chat-panel__thinking">
        <div v-if="chat.phase === 'searching'" class="chat-panel__thinking-step">
          <span class="pulse-dot"></span> ObsidianMind 正在搜索你的知识库…
        </div>
        <div v-else class="chat-panel__thinking-step">
          <span class="pulse-dot"></span> 正在生成回答…
        </div>
      </div>
    </div>

    <!-- 使用场景 -->
    <div v-else-if="activeTab === 'scenes'" class="chat-panel__scenes">
      <div class="chat-panel__scenes-desc">选中一段文字，或直接对整个知识库使用：</div>
      <button v-for="s in scenes" :key="s.title" class="scene-card">
        <span class="scene-card__icon">{{ s.icon }}</span>
        <div>
          <div class="scene-card__title">{{ s.title }}</div>
          <div class="scene-card__desc">{{ s.desc }}</div>
        </div>
      </button>
    </div>

    <!-- 设置 -->
    <div v-else class="chat-panel__settings">
      <div class="setting-row">
        <span class="setting-row__label">默认模型</span>
        <span class="setting-row__value">Qwen3 (Ollama)</span>
      </div>
      <div class="setting-row">
        <span class="setting-row__label">温度</span>
        <span class="setting-row__value">0.7</span>
      </div>
      <div class="setting-row">
        <span class="setting-row__label">回答语言</span>
        <span class="setting-row__value">跟随提问</span>
      </div>
      <div class="setting-row">
        <span class="setting-row__label">显示引用来源</span>
        <span class="setting-row__value">始终</span>
      </div>
      <p class="chat-panel__settings-note">完整设置见 设置 → AI 模型</p>
    </div>

    <ChatInput v-if="activeTab === 'chat'" />
  </section>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--surface);
  overflow: hidden;
}

.chat-panel__head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.chat-panel__title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.chat-panel__bot {
  color: var(--primary);
}

.chat-panel__tabs {
  display: flex;
  gap: 2px;
  margin-left: auto;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 2px;
}

.chat-panel__tab {
  padding: 3px 10px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  border-radius: 4px;
  transition: all var(--dur-fast) var(--ease);
}

.chat-panel__tab:hover {
  color: var(--text-1);
}

.chat-panel__tab--active {
  background: var(--surface-active);
  color: var(--text-1);
}

.chat-panel__clear {
  color: var(--text-3);
  padding: 4px;
  border-radius: var(--r-sm);
  transition: all var(--dur-fast) var(--ease);
}

.chat-panel__clear:hover {
  color: var(--danger);
  background: var(--surface-hover);
}

.chat-panel__list {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
}

.chat-panel__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: var(--sp-10);
  text-align: center;
}

.chat-panel__empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: var(--sp-4);
  border-radius: var(--r-lg);
  color: var(--primary);
  background: var(--primary-muted);
  border: 1px solid var(--primary-border);
}

.chat-panel__empty-title {
  font-size: var(--fs-lg);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.chat-panel__empty-desc {
  margin: var(--sp-2) 0 var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.chat-panel__suggestion {
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  margin-bottom: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-2);
  text-align: left;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.chat-panel__suggestion:hover {
  border-color: var(--primary-border);
  color: var(--text-1);
  background: var(--surface-hover);
}

.chat-panel__thinking {
  padding: var(--sp-3) 0;
}

.chat-panel__thinking-step {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--r-full);
  background: var(--primary);
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}

.chat-panel__scenes {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
}

.chat-panel__scenes-desc {
  font-size: var(--fs-sm);
  color: var(--text-3);
  margin-bottom: var(--sp-4);
}

.scene-card {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  width: 100%;
  padding: var(--sp-3);
  margin-bottom: var(--sp-2);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.scene-card:hover {
  border-color: var(--primary-border);
  background: var(--surface-hover);
}

.scene-card__icon {
  font-size: var(--fs-md);
}

.scene-card__title {
  font-size: var(--fs-base);
  font-weight: 550;
  color: var(--text-1);
}

.scene-card__desc {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-top: 1px;
}

.chat-panel__settings {
  flex: 1;
  padding: var(--sp-4);
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--border);
  font-size: var(--fs-sm);
}

.setting-row__label {
  color: var(--text-2);
}

.setting-row__value {
  color: var(--text-1);
}

.chat-panel__settings-note {
  margin-top: var(--sp-4);
  font-size: var(--fs-xs);
  color: var(--text-3);
}
</style>
