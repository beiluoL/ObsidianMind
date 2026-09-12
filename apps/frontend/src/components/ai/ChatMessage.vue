<script setup lang="ts">
/**
 * 单条聊天消息：Markdown 渲染回答正文 + 检索阶段指示（Sources 占位 Phase 3 接入）。
 */
import { computed } from 'vue';
import { marked } from 'marked';
import { FileText, ChevronRight, Link2 } from 'lucide-vue-next';
import type { ChatMessage } from '@/types/knowledge';
import { useKnowledgeStore } from '@/stores/knowledge';

const props = defineProps<{
  message: ChatMessage;
}>();

const knowledge = useKnowledgeStore();
const isUser = computed(() => props.message.role === 'user');

const rendered = computed(() =>
  isUser.value ? '' : (marked.parse(props.message.content, { async: false }) as string),
);

function openNote(noteId: string): void {
  knowledge.openNote(noteId);
}
</script>

<template>
  <div class="msg" :class="isUser ? 'msg--user' : 'msg--ai'">
    <!-- 用户消息 -->
    <div v-if="isUser" class="msg__bubble">{{ message.content }}</div>

    <!-- AI 消息 -->
    <template v-else>
      <div class="msg__body md-body" v-html="rendered"></div>

      <!-- 引用来源 -->
      <div v-if="message.sources.length" class="msg__section">
        <div class="section-label">引用来源</div>
        <div class="msg__sources">
          <button
            v-for="src in message.sources"
            :key="src.noteId + src.chunkId"
            class="source-card"
            @click="openNote(src.noteId)"
          >
            <FileText :size="14" class="source-card__icon" />
            <div class="source-card__meta">
              <span class="source-card__title">{{ src.title }}</span>
              <div class="source-card__bar">
                <div class="source-card__fill" :style="{ width: `${src.score * 100}%` }"></div>
              </div>
            </div>
            <span class="source-card__score">{{ Math.round(src.score * 100) }}%</span>
          </button>
        </div>
      </div>

      <!-- 相关笔记 -->
      <div v-if="message.relatedNotes.length" class="msg__section">
        <div class="section-label"><Link2 :size="11" style="vertical-align: -1px" /> 相关笔记</div>
        <div class="msg__related">
          <button
            v-for="rel in message.relatedNotes"
            :key="rel.noteId"
            class="related-item"
            @click="openNote(rel.noteId)"
          >
            <div class="related-item__top">
              <span class="related-item__title">{{ rel.title }}</span>
              <span class="related-item__score">{{ Math.round(rel.score * 100) }}%</span>
              <ChevronRight :size="12" class="related-item__arrow" />
            </div>
            <div class="related-item__relation">{{ rel.relation }}</div>
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
  gap: var(--sp-3);
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

.source-card__icon {
  color: var(--primary);
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
}

.msg__related {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  margin-top: var(--sp-2);
}

.related-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-md);
  text-align: left;
  transition: background var(--dur-fast) var(--ease);
}

.related-item:hover {
  background: var(--surface-hover);
}

.related-item__top {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.related-item__title {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-item__score {
  font-size: var(--fs-xs);
  color: var(--primary);
  font-variant-numeric: tabular-nums;
}

.related-item__arrow {
  color: var(--text-3);
}

.related-item__relation {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
</style>
