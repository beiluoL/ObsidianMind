<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { Search, Loader2, FileText, Sparkles } from 'lucide-vue-next';
import type { SearchResult } from '@/types/knowledge';
import { searchService } from '@/services/searchService';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const knowledge = useKnowledgeStore();

const query = ref('');
const scope = ref('vault');
const results = ref<SearchResult[]>([]);
const loading = ref(false);
const searched = ref(false);

/** 摘要高亮：转义 HTML 后用 <mark> 包裹命中词 */
function highlight(excerpt: string, terms: string[]): string {
  let html = excerpt
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
  const unique = [...new Set(terms.filter((t) => t.length >= 2))].sort((a, b) => b.length - a.length);
  for (const term of unique) {
    const safe = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    html = html.replace(new RegExp(safe, 'gi'), (m) => `<mark>${m}</mark>`);
  }
  return html;
}

async function doSearch(): Promise<void> {
  const q = query.value.trim();
  searched.value = true;
  if (!q) {
    results.value = [];
    return;
  }
  loading.value = true;
  const currentFolder = knowledge.currentNote?.folder ?? '';
  results.value = await searchService.search(q, scope.value, currentFolder);
  loading.value = false;
}

function openNote(noteId: string): void {
  knowledge.openNote(noteId);
  router.push({ name: 'note', params: { id: noteId } });
}

watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string' && q) {
      query.value = q;
      void doSearch();
    }
  },
  { immediate: true },
);

onMounted(() => {
  // 进入页面自动聚焦搜索框
  const el = document.querySelector<HTMLInputElement>('.search__input');
  el?.focus();
});
</script>

<template>
  <div class="search">
    <div class="search__inner">
      <!-- 搜索区 -->
      <section class="search__hero">
        <h1 class="search__title">搜索你的知识库</h1>
        <p class="search__sub">不是文件搜索，而是 AI Knowledge Search</p>
        <div class="search__box">
          <Search :size="17" class="search__box-icon" />
          <input
            v-model="query"
            class="search__input"
            type="text"
            placeholder="试试问一个自然语言问题，如：RAG 是怎么工作的？"
            @keydown.enter="doSearch"
          />
          <button class="search__go" :disabled="loading" @click="doSearch">
            <Loader2 v-if="loading" :size="14" class="spin" />
            <span v-else>AI 搜索</span>
          </button>
        </div>
        <div class="search__scopes">
          <button
            v-for="s in [
              { v: 'vault', label: '整个知识库' },
              { v: 'folder', label: '当前文件夹' },
            ]"
            :key="s.v"
            class="search__scope"
            :class="{ 'search__scope--active': scope === s.v }"
            @click="scope = s.v"
          >
            {{ s.label }}
          </button>
        </div>
      </section>

      <!-- 结果区 -->
      <section v-if="loading" class="search__loading">
        <Loader2 :size="18" class="spin" />
        <span>AI 正在理解你的问题并检索知识库…</span>
      </section>

      <template v-else-if="results.length">
        <div class="search__best section-label">最佳匹配</div>
        <button
          v-for="(r, index) in results"
          :key="r.noteId"
          class="result"
          :class="{ 'result--best': index === 0 }"
          @click="openNote(r.noteId)"
        >
          <div class="result__head">
            <FileText :size="15" class="result__icon" />
            <span class="result__title">{{ r.title }}</span>
            <span v-if="index === 0" class="result__badge"><Sparkles :size="10" /> 最佳</span>
            <span class="result__score">{{ Math.round(r.score * 100) }}%</span>
          </div>
          <div class="result__path">{{ r.path }}</div>
          <!-- eslint-disable-next-line vue/no-v-html -->
          <p class="result__excerpt" v-html="highlight(r.excerpt, r.matchTerms ?? [])"></p>
          <div class="result__footer">
            <span v-for="tag in r.tags ?? []" :key="tag" class="result__tag">#{{ tag }}</span>
            <span class="result__reason">
              <Sparkles :size="10" />
              {{ r.reason }}
            </span>
            <span class="result__time" v-if="r.modifiedAt">{{ r.modifiedAt }}</span>
          </div>
        </button>
      </template>

      <!-- 空状态 -->
      <section v-else-if="searched && !loading" class="search__empty">
        <div class="search__empty-title">没有找到相关知识</div>
        <p class="search__empty-hint">尝试：</p>
        <ul class="search__empty-list">
          <li>换一个关键词</li>
          <li>扩大搜索范围</li>
          <li>让 AI 帮你重新搜索</li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.search {
  height: 100%;
  overflow-y: auto;
}

.search__inner {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--sp-8) var(--sp-5) var(--sp-10);
}

.search__hero {
  text-align: center;
  padding-bottom: var(--sp-6);
}

.search__title {
  font-size: var(--fs-xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.search__sub {
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.search__box {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-top: var(--sp-5);
  padding: 0 var(--sp-3) 0 var(--sp-4);
  height: 46px;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-lg);
  transition: border-color var(--dur) var(--ease);
}

.search__box:focus-within {
  border-color: var(--primary-border);
}

.search__box-icon {
  color: var(--text-3);
}

.search__input {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--text-1);
}

.search__input::placeholder {
  color: var(--text-3);
}

.search__go {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  height: 30px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-sm);
  font-weight: 550;
  color: #fff;
  background: var(--primary);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.search__go:hover:not(:disabled) {
  background: var(--primary-hover);
}

.search__go:disabled {
  opacity: 0.6;
}

.search__scopes {
  display: flex;
  justify-content: center;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.search__scope {
  padding: 3px var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
  border: 1px solid var(--border);
  border-radius: var(--r-full);
  transition: all var(--dur-fast) var(--ease);
}

.search__scope:hover {
  color: var(--text-1);
}

.search__scope--active {
  color: var(--primary);
  border-color: var(--primary-border);
  background: var(--primary-muted);
}

.search__best {
  margin-bottom: var(--sp-3);
}

.result {
  display: block;
  width: 100%;
  padding: var(--sp-4);
  margin-bottom: var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  text-align: left;
  transition: all var(--dur-fast) var(--ease);
}

.result:hover {
  border-color: var(--primary-border);
  background: var(--surface-2);
}

.result--best {
  border-color: var(--primary-border);
}

.result__head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.result__icon {
  color: var(--primary);
}

.result__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}

.result__badge {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 1px 8px;
  font-size: var(--fs-xs);
  color: var(--primary);
  background: var(--primary-muted);
  border-radius: var(--r-full);
}

.result__score {
  margin-left: auto;
  font-size: var(--fs-sm);
  font-weight: 650;
  color: var(--primary);
  font-variant-numeric: tabular-nums;
}

.result__path {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-family: var(--font-mono);
}

.result__excerpt {
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: 1.65;
}

.result__excerpt :deep(mark) {
  background: rgba(139, 124, 246, 0.25);
  color: var(--primary);
  border-radius: 3px;
  padding: 0 1px;
}

.result__footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.result__tag {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.result__time {
  margin-left: auto;
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.result__excerpt :deep(mark) {
  background: rgba(139, 124, 246, 0.25);
  color: var(--primary);
  border-radius: 3px;
  padding: 0 1px;
}

.result__footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.result__tag {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.result__time {
  margin-left: auto;
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.result__reason {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--primary);
  opacity: 0.85;
}

.search__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-8) 0;
  color: var(--text-3);
  font-size: var(--fs-sm);
}

.search__empty {
  padding: var(--sp-8) 0;
  text-align: center;
}

.search__empty-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}

.search__empty-hint {
  margin-top: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.search__empty-list {
  margin-top: var(--sp-1);
  list-style: none;
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: 1.9;
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
