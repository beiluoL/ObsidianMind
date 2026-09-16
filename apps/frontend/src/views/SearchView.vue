<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { Search, Loader2, FileText, Sparkles } from 'lucide-vue-next';
import type { SearchResult, SemanticSource } from '@/types/knowledge';
import { hybridSearchService, searchService, semanticSearchService } from '@/services/searchService';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useRouter } from 'vue-router';
import type { HybridSearchResponse, RetrievalDebugTrace } from '@/types/knowledge';

const route = useRoute();
const router = useRouter();
const knowledge = useKnowledgeStore();

const query = ref('');
const scope = ref('vault');
/** 检索模式：hybrid = 混合（默认）；semantic = 语义；text = 本地全文 */
const mode = ref<'hybrid' | 'semantic' | 'text'>('hybrid');
/** 混合检索响应（模式可观察字段：降级提示用） */
const hybridResp = ref<HybridSearchResponse | null>(null);
/** Retrieval Debug（仅 import.meta.env.DEV 显示入口；后端未启用时 debugTraceError 提示） */
const showDebug = ref(false);
const debugTrace = ref<RetrievalDebugTrace | null>(null);
const debugTraceError = ref('');
const isDev = import.meta.env.DEV;
const results = ref<SearchResult[]>([]);
const sources = ref<SemanticSource[]>([]);
const loading = ref(false);
const searched = ref(false);
const errorMsg = ref('');

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
    sources.value = [];
    errorMsg.value = '';
    return;
  }
  loading.value = true;
  errorMsg.value = '';
  hybridResp.value = null;
  debugTrace.value = null;
  debugTraceError.value = '';
  try {
    if (mode.value === 'hybrid') {
      const resp = await hybridSearchService.search(q, 'HYBRID');
      hybridResp.value = resp;
      sources.value = resp.sources;
      results.value = [];
      if (showDebug.value && isDev) {
        void loadDebugTrace(q);
      }
    } else if (mode.value === 'semantic') {
      // 后端已完成排序/去重/摘要；错误不裸抛给用户，转为界面可读文案（详情进 console）
      const resp = await semanticSearchService.search(q);
      sources.value = resp.sources;
      results.value = [];
    } else {
      const currentFolder = knowledge.currentNote?.folder ?? '';
      results.value = await searchService.search(q, scope.value, currentFolder);
      sources.value = [];
    }
  } catch (e) {
    console.error('[search] 检索失败', e);
    errorMsg.value = '知识库检索暂时不可用，请稍后重试';
    results.value = [];
    sources.value = [];
  } finally {
    loading.value = false;
  }
}

/** 模式 → 用户可读标签（不暴露 RRF/BM25 等技术名） */
function modeLabel(mode: string): string {
  switch (mode) {
    case 'HYBRID': return '混合';
    case 'VECTOR': return '语义';
    case 'KEYWORD': return '关键词';
    default: return mode;
  }
}

/** 拉取 Debug trace：后端未启用（RETRIEVAL_DEBUG_ENABLED=false）时展示可读提示而非裸错误 */
async function loadDebugTrace(q: string): Promise<void> {
  debugTraceError.value = '';
  try {
    debugTrace.value = await hybridSearchService.debug(q, 'HYBRID');
  } catch (e) {
    debugTrace.value = null;
    debugTraceError.value = 'Debug 未启用（后端需设置 RETRIEVAL_DEBUG_ENABLED=true）';
    console.debug('[search] debug trace 不可用', e);
  }
}

function toggleDebug(): void {
  showDebug.value = !showDebug.value;
  if (showDebug.value && !debugTrace.value && !debugTraceError.value && query.value.trim()) {
    void loadDebugTrace(query.value.trim());
  }
}

/** Debug 面板展示的阶段（Vector / Keyword / RRF / Final） */
const debugStages = computed(() => {
  if (!debugTrace.value) {
    return [];
  }
  return [
    { key: 'vector', label: 'Vector Top', items: debugTrace.value.vector },
    { key: 'keyword', label: 'Keyword Top (BM25)', items: debugTrace.value.keyword },
    { key: 'fused', label: 'RRF Fused', items: debugTrace.value.fused },
    { key: 'final', label: 'Final', items: debugTrace.value.finalResults },
  ];
});

/** 分数格式化：null = 该通道未产出 */
function fmtScore(value: number | null): string {
  return value == null ? '-' : value.toFixed(4);
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
          <span class="search__scope-divider" aria-hidden="true"></span>
          <button
            v-for="m in [
              { v: 'hybrid', label: '混合' },
              { v: 'semantic', label: '语义' },
              { v: 'text', label: '全文' },
            ]"
            :key="m.v"
            class="search__scope"
            :class="{ 'search__scope--active': mode === m.v }"
            @click="mode = m.v as 'hybrid' | 'semantic' | 'text'"
          >
            {{ m.label }}
          </button>
        </div>
      </section>

      <!-- 结果区：全文模式 -->
      <section v-if="loading" class="search__loading">
        <Loader2 :size="18" class="spin" />
        <span>{{ mode === 'text' ? '正在检索知识库…' : mode === 'semantic' ? '正在向量化查询并检索知识库…' : '正在混合检索知识库（语义 + 关键词）…' }}</span>
      </section>

      <section v-else-if="errorMsg" class="search__empty">
        <div class="search__empty-title">{{ errorMsg }}</div>
      </section>

      <template v-else-if="(mode === 'semantic' || mode === 'hybrid') && sources.length">
        <div class="search__best section-label">
          {{ mode === 'hybrid' ? '混合检索 Sources' : '语义匹配 Sources' }}
          <span v-if="hybridResp" class="search__mode-chip">{{ modeLabel(hybridResp.effectiveMode) }}</span>
        </div>
        <p v-if="hybridResp && hybridResp.fallbacks.length" class="search__degraded">
          部分检索通道暂不可用，本次结果来自{{ modeLabel(hybridResp.effectiveMode) }}。
        </p>
        <button
          v-for="(s, index) in sources"
          :key="s.documentId + '#c' + s.chunkIndex"
          class="result"
          :class="{ 'result--best': index === 0 }"
          @click="openNote(s.documentId)"
        >
          <div class="result__head">
            <FileText :size="15" class="result__icon" />
            <span class="result__title">{{ s.title }}</span>
            <span v-if="index === 0" class="result__badge"><Sparkles :size="10" /> 最佳</span>
            <span class="result__score">{{ s.score.toFixed(3) }}</span>
          </div>
          <div class="result__path">{{ s.path }}</div>
          <p v-if="s.heading" class="result__heading">{{ s.heading }}</p>
          <p class="result__excerpt">{{ s.snippet }}</p>
        </button>
      </template>

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

      <!-- Retrieval Debug：仅开发模式显示入口，普通用户不可见 -->
      <section v-if="isDev && mode === 'hybrid' && searched && !loading" class="search__debug">
        <button class="search__debug-toggle" @click="toggleDebug">
          {{ showDebug ? '收起 Retrieval Debug' : 'Retrieval Debug（开发模式）' }}
        </button>
        <div v-if="showDebug" class="search__debug-panel">
          <p v-if="debugTraceError" class="search__debug-hint">{{ debugTraceError }}</p>
          <template v-else-if="debugTrace">
            <div class="search__debug-meta">
              requested={{ debugTrace.requestedMode }} effective={{ debugTrace.effectiveMode }}
              reranker={{ debugTrace.rerankerStatus }}
              fallbacks={{ debugTrace.fallbacks.length ? debugTrace.fallbacks.join(', ') : '无' }}
              vectorMs={{ debugTrace.timing.vectorMs }} keywordMs={{ debugTrace.timing.keywordMs }}
              fusionMs={{ debugTrace.timing.fusionMs }} rerankerMs={{ debugTrace.timing.rerankerMs }}
              totalMs={{ debugTrace.timing.totalMs }}
            </div>
            <div v-for="stage in debugStages" :key="stage.key" class="search__debug-stage">
              <div class="search__debug-stage-title">{{ stage.label }}</div>
              <div v-for="item in stage.items" :key="item.rank + item.chunkId" class="search__debug-row">
                <span class="search__debug-rank">{{ item.rank }}</span>
                <span class="search__debug-doc">{{ item.title }}</span>
                <span class="search__debug-score">
                  v={{ fmtScore(item.vectorScore) }} k={{ fmtScore(item.keywordScore) }}
                  rrf={{ fmtScore(item.rrfScore) }} re={{ fmtScore(item.rerankScore) }}
                  final={{ fmtScore(item.finalScore) }}
                </span>
              </div>
            </div>
          </template>
        </div>
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
  color: var(--on-primary);
  background: var(--primary-solid);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.search__go:hover:not(:disabled) {
  background: var(--primary-solid-hover);
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

.search__scope-divider {
  width: 1px;
  height: 14px;
  margin: 0 var(--sp-1);
  background: var(--border);
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

.result__heading {
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  font-weight: 550;
  color: var(--primary);
}

.result__excerpt :deep(mark) {
  background: var(--primary-strong-bg);
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

.search__mode-chip {
  margin-left: var(--sp-2);
  padding: 1px 8px;
  font-size: var(--fs-xs);
  font-weight: 550;
  color: var(--text-2);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-full);
}

.search__degraded {
  margin: calc(-1 * var(--sp-2)) 0 var(--sp-3);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-2);
  background: var(--surface-2);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-md);
}

.search__debug {
  margin-top: var(--sp-6);
  text-align: left;
}

.search__debug-toggle {
  padding: 3px var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-full);
  transition: color var(--dur-fast) var(--ease);
}

.search__debug-toggle:hover {
  color: var(--text-1);
}

.search__debug-panel {
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
}

.search__debug-hint {
  color: var(--text-3);
}

.search__debug-meta {
  color: var(--text-3);
  word-break: break-all;
  line-height: 1.8;
}

.search__debug-stage {
  margin-top: var(--sp-3);
}

.search__debug-stage-title {
  font-weight: 650;
  color: var(--primary);
  margin-bottom: var(--sp-1);
}

.search__debug-row {
  display: flex;
  gap: var(--sp-2);
  padding: 1px 0;
  color: var(--text-2);
}

.search__debug-rank {
  width: 18px;
  text-align: right;
  color: var(--text-3);
}

.search__debug-doc {
  min-width: 120px;
  color: var(--text-1);
}

.search__debug-score {
  color: var(--text-3);
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
