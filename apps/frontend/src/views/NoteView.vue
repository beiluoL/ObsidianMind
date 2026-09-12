<script setup lang="ts">
/**
 * 笔记页：阅读/编辑双模式切换，⌘S 保存；保存前比对 mtime 检测外部修改，
 * 冲突时展示横幅由用户选择（覆盖/放弃）；轮询感知文件变化并刷新 backlinks。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { marked } from 'marked';
import { useRoute, useRouter } from 'vue-router';
import {
  ChevronLeft,
  ChevronRight,
  Star,
  Pencil,
  BookOpen,
  Tag,
  Link2,
  AlertTriangle,
  RotateCcw,
  Save,
} from 'lucide-vue-next';
import { useKnowledgeStore } from '@/stores/knowledge';
import { knowledgeService } from '@/services/knowledgeService';

const route = useRoute();
const router = useRouter();
const knowledge = useKnowledgeStore();

const mode = ref<'read' | 'edit'>('read');
const draft = ref('');
const isDirty = ref(false);
const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle');
const conflict = ref<{ diskContent: string } | null>(null);
const backlinks = ref<{ sourcePath: string; sourceTitle: string; context: string }[]>([]);

const note = computed(() => knowledge.currentNote);
const rendered = computed(() =>
  note.value ? (marked.parse(note.value.content, { async: false }) as string) : '',
);

let conflictTimer: ReturnType<typeof setInterval> | null = null;

function stopConflictPolling(): void {
  if (conflictTimer) {
    clearInterval(conflictTimer);
    conflictTimer = null;
  }
}

/** 外部修改检测：编辑中或有未保存草稿时提示冲突，纯阅读时静默跟随 */
function startConflictPolling(): void {
  stopConflictPolling();
  conflictTimer = setInterval(async () => {
    const current = note.value;
    if (!current?.mtime || !knowledge.isVaultNote()) return;
    const result = await knowledgeService.checkExternalChange(current.id, current.mtime);
    if (!result.changed || !current) return;
    if (isDirty.value || mode.value === 'edit') {
      conflict.value = { diskContent: result.content ?? '' };
    } else {
      // 无未保存内容：静默刷新到磁盘最新版
      current.content = result.content ?? current.content;
      current.mtime = result.mtime;
      knowledge.touchNote(current.id, result.mtime);
    }
  }, 3000);
}

async function save(): Promise<void> {
  const current = note.value;
  if (!current || !knowledge.isVaultNote() || saveState.value === 'saving') return;
  saveState.value = 'saving';
  try {
    const newMtime = await knowledgeService.saveNote(current.id, draft.value);
    current.content = draft.value;
    current.mtime = newMtime;
    isDirty.value = false;
    saveState.value = 'saved';
    knowledge.touchNote(current.id, newMtime);
    setTimeout(() => {
      saveState.value = 'idle';
    }, 2000);
  } catch (err) {
    saveState.value = 'error';
    console.error('[ObsidianMind] 保存失败', err);
  }
}

function onEditorKeydown(event: KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault();
    void save();
  }
}

function startEdit(): void {
  if (!note.value) return;
  draft.value = note.value.content;
  isDirty.value = false;
  mode.value = 'edit';
}

async function reloadFromDisk(): Promise<void> {
  const current = note.value;
  if (!current || !conflict.value) return;
  current.content = conflict.value.diskContent;
  isDirty.value = false;
  conflict.value = null;
  const result = await knowledgeService.checkExternalChange(current.id, current.mtime ?? 0);
  current.mtime = result.mtime ?? current.mtime;
  knowledge.touchNote(current.id, current.mtime);
}

function keepLocal(): void {
  conflict.value = null;
}

async function loadBacklinks(noteId: string): Promise<void> {
  backlinks.value = [];
  if (!knowledge.isVaultNote()) return;
  backlinks.value = await knowledgeService.getBacklinks(noteId);
}

watch(
  () => route.params.id,
  async (id) => {
    if (typeof id === 'string') await knowledge.openNote(id);
  },
  { immediate: true },
);

/** 笔记内容切换（无论由路由还是文件树触发）后：重置编辑态 + Backlinks + 外部修改轮询 */
watch(
  () => knowledge.currentNote?.id,
  (id) => {
    if (!id) {
      stopConflictPolling();
      return;
    }
    conflict.value = null;
    saveState.value = 'idle';
    mode.value = 'read';
    isDirty.value = false;
    void loadBacklinks(id);
    startConflictPolling();
  },
);

onBeforeUnmount(stopConflictPolling);
</script>

<template>
  <div class="note">
    <!-- 工具栏 -->
    <header class="note__bar">
      <div class="note__nav">
        <button class="note__nav-btn" @click="router.back()"><ChevronLeft :size="16" /></button>
        <button class="note__nav-btn" @click="router.forward()"><ChevronRight :size="16" /></button>
      </div>
      <div class="note__crumb" v-if="note">{{ note.path }}</div>
      <div class="note__actions" v-if="note">
        <!-- 保存状态 -->
        <span v-if="saveState === 'saving'" class="note__save note__save--saving">
          <Save :size="12" /> 保存中…
        </span>
        <span v-else-if="saveState === 'saved'" class="note__save note__save--ok">✓ 已保存</span>
        <span v-else-if="saveState === 'error'" class="note__save note__save--error">⚠️ 保存失败，请重试</span>
        <span v-else-if="isDirty" class="note__save note__save--dirty">未保存 · ⌘S 保存</span>

        <button
          class="note__action-btn"
          :class="{ 'note__action-btn--fav': knowledge.isFavorite(note.id) }"
          :title="knowledge.isFavorite(note.id) ? '取消收藏' : '收藏'"
          @click="knowledge.toggleFavorite(note.id)"
        >
          <Star :size="14" :fill="knowledge.isFavorite(note.id) ? 'currentColor' : 'none'" />
        </button>
        <div class="note__mode">
          <button
            class="note__mode-btn"
            :class="{ 'note__mode-btn--active': mode === 'read' }"
            @click="mode = 'read'"
          >
            <BookOpen :size="13" /> 阅读
          </button>
          <button
            class="note__mode-btn"
            :class="{ 'note__mode-btn--active': mode === 'edit' }"
            @click="startEdit"
          >
            <Pencil :size="13" /> 编辑
          </button>
        </div>
      </div>
    </header>

    <!-- 外部修改冲突提示 -->
    <div v-if="conflict && note" class="note__conflict">
      <AlertTriangle :size="15" class="note__conflict-icon" />
      <span class="note__conflict-text">
        <strong>{{ note.title }}</strong> 已在其他应用中发生修改（可能是 Obsidian）
      </span>
      <button class="note__conflict-btn note__conflict-btn--primary" @click="reloadFromDisk">
        <RotateCcw :size="12" /> 重新加载
      </button>
      <button class="note__conflict-btn" @click="keepLocal">保留当前内容</button>
    </div>

    <!-- 正文 -->
    <div class="note__scroll">
      <article v-if="note" class="note__paper">
        <h1 class="note__title">{{ note.title }}</h1>
        <div class="note__meta">
          <span>更新于 {{ note.updatedAt }}</span>
          <span class="note__dot">·</span>
          <span>{{ note.wordCount }} 字符</span>
          <span v-if="note.tags.length" class="note__tags">
            <Tag :size="11" />
            <span v-for="tag in note.tags" :key="tag" class="note__tag">{{ tag }}</span>
          </span>
        </div>

        <!-- 阅读模式 -->
        <div v-if="mode === 'read'" class="md-body note__content" v-html="rendered"></div>

        <!-- 编辑模式 -->
        <textarea
          v-else
          v-model="draft"
          class="note__editor"
          spellcheck="false"
          @keydown="onEditorKeydown"
          @input="isDirty = draft !== note.content"
        ></textarea>

        <!-- 编辑模式底部保存条 -->
        <div v-if="mode === 'edit'" class="note__edit-bar">
          <span class="note__edit-hint">Markdown 是 Source of Truth，保存将直接写回原文件</span>
          <button class="note__save-btn" :disabled="!isDirty || saveState === 'saving'" @click="save">
            <Save :size="13" /> 保存（⌘S）
          </button>
        </div>

        <!-- 反向链接 -->
        <section v-if="backlinks.length" class="note__backlinks">
          <div class="note__backlinks-head">
            <Link2 :size="14" />
            <span>Backlinks · 被 {{ backlinks.length }} 篇笔记引用</span>
          </div>
          <button
            v-for="bl in backlinks"
            :key="bl.sourcePath"
            class="note__backlink"
            @click="router.push({ name: 'note', params: { id: encodeURIComponent(bl.sourcePath) } })"
          >
            <span class="note__backlink-title">{{ bl.sourceTitle }}</span>
            <span class="note__backlink-context">…{{ bl.context }}…</span>
          </button>
        </section>
      </article>

      <!-- 空状态 -->
      <div v-else class="note__empty">
        <p>从左侧文件树选择一篇笔记开始阅读</p>
        <p class="note__empty-hint">或者问 AI：「帮我找一篇关于 RAG 的笔记」</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.note {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.note__bar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: 44px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
}

.note__nav {
  display: flex;
  gap: 2px;
}

.note__nav-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: var(--r-sm);
  color: var(--text-3);
  transition: all var(--dur-fast) var(--ease);
}

.note__nav-btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.note__crumb {
  flex: 1;
  font-size: var(--fs-xs);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
}

.note__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.note__save {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-xs);
}

.note__save--ok {
  color: var(--success);
}

.note__save--error {
  color: var(--danger);
}

.note__save--saving,
.note__save--dirty {
  color: var(--text-3);
}

.note__action-btn {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-sm);
  color: var(--text-3);
  transition: all var(--dur-fast) var(--ease);
}

.note__action-btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.note__action-btn--fav {
  color: var(--warning);
}

.note__mode {
  display: flex;
  gap: 2px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 2px;
}

.note__mode-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  border-radius: 4px;
  transition: all var(--dur-fast) var(--ease);
}

.note__mode-btn--active {
  background: var(--surface-active);
  color: var(--text-1);
}

/* 冲突横幅 */
.note__conflict {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2) var(--sp-4);
  background: var(--warning-soft);
  border-bottom: 1px solid var(--warning-border);
  font-size: var(--fs-sm);
  flex-shrink: 0;
}

.note__conflict-icon {
  color: var(--warning);
  flex-shrink: 0;
}

.note__conflict-text {
  flex: 1;
  color: var(--text-2);
}

.note__conflict-text strong {
  color: var(--text-1);
}

.note__conflict-btn {
  padding: 4px var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-md);
  white-space: nowrap;
  transition: all var(--dur-fast) var(--ease);
}

.note__conflict-btn:hover {
  color: var(--text-1);
  border-color: var(--primary-border);
}

.note__conflict-btn--primary {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--warning);
  border-color: var(--warning-border-strong);
}

.note__scroll {
  flex: 1;
  overflow-y: auto;
}

.note__paper {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--sp-8) var(--sp-6) var(--sp-10);
}

.note__title {
  font-size: var(--fs-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.3;
}

.note__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin: var(--sp-3) 0 var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.note__dot {
  color: var(--text-3);
}

.note__tags {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  margin-left: var(--sp-2);
}

.note__tag {
  padding: 1px 8px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-full);
  color: var(--text-2);
}

.note__content {
  margin-top: var(--sp-4);
}

.note__editor {
  width: 100%;
  min-height: 60vh;
  margin-top: var(--sp-4);
  padding: var(--sp-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  font-family: var(--font-mono);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--text-1);
  resize: vertical;
}

.note__editor:focus {
  outline: none;
  border-color: var(--primary-border);
}

.note__edit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-top: var(--sp-3);
}

.note__edit-hint {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.note__save-btn {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 6px var(--sp-4);
  font-size: var(--fs-sm);
  font-weight: 550;
  color: var(--on-primary);
  background: var(--primary-solid);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.note__save-btn:hover:not(:disabled) {
  background: var(--primary-solid-hover);
}

.note__save-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* Backlinks */
.note__backlinks {
  margin-top: var(--sp-8);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border);
}

.note__backlinks-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: var(--sp-3);
}

.note__backlink {
  display: block;
  width: 100%;
  padding: var(--sp-2) var(--sp-3);
  margin-bottom: var(--sp-2);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.note__backlink:hover {
  border-color: var(--primary-border);
  background: var(--surface-2);
}

.note__backlink-title {
  display: block;
  font-size: var(--fs-sm);
  font-weight: 550;
  color: var(--primary);
}

.note__backlink-context {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note__empty {
  padding-top: 120px;
  text-align: center;
  color: var(--text-2);
  font-size: var(--fs-md);
}

.note__empty-hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-3);
}
</style>
