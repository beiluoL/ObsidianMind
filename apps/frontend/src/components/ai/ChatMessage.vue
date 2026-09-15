<script setup lang="ts">
/**
 * 单条聊天消息（Phase 5 RAG）：Markdown 渲染（DOMPurify 消毒）+ [SRC-n]→[n] 引用映射 + 流式状态。
 * 引用来源为每条回答独立（citation 事件先于 token，流式期间即可点击）。
 */
import { computed } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { FileText, AlertCircle, Ban } from 'lucide-vue-next';
import type { ChatMessage } from '@/types/knowledge';
import { useKnowledgeStore } from '@/stores/knowledge';

const props = defineProps<{
  message: ChatMessage;
}>();

const knowledge = useKnowledgeStore();
const isUser = computed(() => props.message.role === 'user');

// [SRC-1] 等内部引用标记 → 用户可读的 [1]（内部协议与 UI 解耦）
const withCitationBadges = computed(() =>
  props.message.content.replace(/\[SRC-(\d+)\]/gi, '[$1]'),
);

const rendered = computed(() => {
  if (isUser.value) return '';
  const html = marked.parse(withCitationBadges.value, { async: false }) as string;
  // LLM 输出可能回显知识库注入内容：渲染前必须消毒（07-security §5）
  return DOMPurify.sanitize(html);
});

function openNote(path: string): void {
  knowledge.openNote(path);
}
</script>

<template>
  <div class="msg" :class="isUser ? 'msg--user' : 'msg--ai'">
    <!-- 用户消息 -->
    <div v-if="isUser" class="msg__bubble">{{ message.content }}</div>

    <!-- AI 消息 -->
    <template v-else>
      <div class="msg__body md-body" v-html="rendered"></div>
      <div v-if="message.status === 'streaming'" class="msg__caret"><span class="pulse-dot"></span></div>

      <!-- 状态条 -->
      <div v-if="message.status === 'error'" class="msg__status msg__status--error">
        <AlertCircle :size="13" />
        <span>回答失败（{{ message.errorCode }}）：{{ message.errorMessage }}</span>
      </div>
      <div v-else-if="message.status === 'cancelled'" class="msg__status msg__status--cancelled">
        <Ban :size="13" />
        <span>已停止生成</span>
      </div>

      <!-- 引用来源（每条回答独立；[n] 与正文 [SRC-n] 一一对应） -->
      <div v-if="message.sources.length" class="msg__section">
        <div class="section-label">引用来源</div>
        <div class="msg__sources">
          <button
            v-for="src in message.sources"
            :key="src.sourceId"
            class="source-card"
            :title="`${src.path} · ${src.heading || '正文'}`"
            @click="openNote(src.path)"
          >
            <span class="source-card__index">[{{ src.index }}]</span>
            <FileText :size="14" class="source-card__icon" />
            <div class="source-card__meta">
              <span class="source-card__title">{{ src.title }}</span>
              <span class="source-card__path">{{ src.path }}<template v-if="src.heading"> · {{ src.heading }}</template></span>
              <div class="source-card__bar">
                <div class="source-card__fill" :style="{ width: `${Math.round(src.score * 100)}%` }"></div>
              </div>
            </div>
            <span class="source-card__score">{{ Math.round(src.score * 100) }}%</span>
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.msg {
  margin-bottom: var(--sp-5);
}

.msg--user {
  display: flex;
  justify-content: flex-end;
}

.msg__bubble {
  max-width: 85%;
  padding: var(--sp-2) var(--sp-3);
  background: var(--primary-muted);
  border: 1px solid var(--primary-border);
  border-radius: var(--r-lg) var(--r-lg) var(--r-sm) var(--r-lg);
  font-size: var(--fs-base);
  line-height: 1.6;
  color: var(--text-1);
}

.msg--ai .msg__body {
  font-size: var(--fs-base);
}

.msg__body :deep(h1) {
  font-size: var(--fs-md);
  margin: var(--sp-3) 0 var(--sp-2);
}

.msg__body :deep(h2) {
  font-size: var(--fs-base);
  margin: var(--sp-4) 0 var(--sp-2);
}

.msg__body :deep(p),
.msg__body :deep(ul),
.msg__body :deep(ol) {
  color: var(--text-2);
}

.msg__body :deep(strong) {
  color: var(--text-1);
}

.msg__body :deep(table) {
  font-size: var(--fs-xs);
}

.msg__caret {
  height: 14px;
  margin-top: 2px;
}

.pulse-dot {
  display: inline-block;
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

.msg__status {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-md);
  font-size: var(--fs-xs);
}

.msg__status--error {
  color: var(--danger);
  background: var(--surface-2);
  border: 1px solid var(--border);
}

.msg__status--cancelled {
  color: var(--text-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
}

.msg__section {
  margin-top: var(--sp-4);
}

.msg__sources {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.source-card {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.source-card:hover {
  border-color: var(--primary-border);
  background: var(--surface-hover);
}

.source-card__index {
  font-size: var(--fs-xs);
  font-weight: 650;
  color: var(--primary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.source-card__icon {
  color: var(--primary);
  flex-shrink: 0;
}

.source-card__meta {
  flex: 1;
  min-width: 0;
}

.source-card__title {
  display: block;
  font-size: var(--fs-sm);
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-card__path {
  display: block;
  font-size: var(--fs-xs);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-card__bar {
  height: 2px;
  margin-top: 4px;
  background: var(--surface-active);
  border-radius: var(--r-full);
  overflow: hidden;
}

.source-card__fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), var(--info));
  border-radius: var(--r-full);
}

.source-card__score {
  font-size: var(--fs-xs);
  color: var(--primary);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  flex-shrink: 0;
}
</style>
