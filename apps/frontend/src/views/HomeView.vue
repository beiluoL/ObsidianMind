<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  FileText,
  Sparkles,
  ArrowRight,
  BookOpen,
  Link2,
  BrainCircuit,
  History,
  Heart,
} from 'lucide-vue-next';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useChatStore } from '@/stores/chat';
import { useVaultStore } from '@/stores/vault';

const route = useRoute();
const router = useRouter();
const knowledge = useKnowledgeStore();
const chat = useChatStore();
const vault = useVaultStore();

const greeting = (): string => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 12) return '早上好';
  if (hour < 18) return '下午好';
  return '晚上好';
};

const listMode = computed(() => (route.query.list === 'favorites' ? 'favorites' : route.query.list === 'recent' ? 'recent' : ''));
const listNotes = computed(() => (listMode.value === 'favorites' ? knowledge.favoriteNotes() : knowledge.recentNotes()));

const recentEdited = computed(() => knowledge.recentEdited());

const learningTopics = ['JVM', 'RAG', 'Spring AI', 'Agent', 'Milvus'];

function openNote(noteId: string): void {
  knowledge.openNote(noteId);
  router.push({ name: 'note', params: { id: noteId } });
}

function askAi(question: string): void {
  chat.ask(question);
}
</script>

<template>
  <div class="home">
    <!-- 顶部问候 -->
    <section class="home__greet">
      <h1 class="home__hello">{{ greeting() }} <span class="home__wave">👋</span></h1>
      <p class="home__sub">你的知识库正在持续增长。</p>
      <div v-if="vault.info" class="home__stats">
        <div class="home__stat">
          <div class="home__stat-num">{{ vault.info.noteCount.toLocaleString() }}</div>
          <div class="home__stat-label">篇笔记</div>
        </div>
        <div class="home__stat">
          <div class="home__stat-num">{{ vault.info.folderCount.toLocaleString() }}</div>
          <div class="home__stat-label">个文件夹</div>
        </div>
        <div class="home__stat">
          <div class="home__stat-num">{{ vault.info.tagCount.toLocaleString() }}</div>
          <div class="home__stat-label">个标签</div>
        </div>
        <div class="home__stat">
          <div class="home__stat-num">{{ vault.info.linkCount.toLocaleString() }}</div>
          <div class="home__stat-label">条双链</div>
        </div>
      </div>
      <div v-else-if="knowledge.health" class="home__stats">
        <div class="home__stat">
          <div class="home__stat-num">{{ knowledge.health.noteCount.toLocaleString() }}</div>
          <div class="home__stat-label">篇笔记</div>
        </div>
        <div class="home__stat">
          <div class="home__stat-num">{{ knowledge.health.chunkCount.toLocaleString() }}</div>
          <div class="home__stat-label">个知识块</div>
        </div>
        <div class="home__stat">
          <div class="home__stat-num">{{ knowledge.health.connectionCount.toLocaleString() }}</div>
          <div class="home__stat-label">个知识连接</div>
        </div>
      </div>
    </section>

    <!-- 列表模式（最近/收藏） -->
    <section v-if="listMode" class="home__panel">
      <div class="home__panel-head">
        <component :is="listMode === 'favorites' ? Heart : History" :size="15" />
        <span>{{ listMode === 'favorites' ? '收藏' : '最近笔记' }}</span>
      </div>
      <button v-for="note in listNotes" :key="note.id" class="note-row" @click="openNote(note.id)">
        <FileText :size="14" class="note-row__icon" />
        <span class="note-row__title">{{ note.title }}</span>
        <span class="note-row__time">{{ note.updatedAt.slice(5, 16) }}</span>
      </button>
    </section>

    <template v-else>
      <div class="home__grid">
        <!-- 左列 -->
        <div class="home__col">
          <!-- 最近编辑 -->
          <section class="home__panel">
            <div class="home__panel-head">
              <FileText :size="15" />
              <span>最近编辑</span>
            </div>
            <button v-for="note in recentEdited" :key="note.id" class="note-row" @click="openNote(note.id)">
              <FileText :size="14" class="note-row__icon" />
              <span class="note-row__title">{{ note.title }}</span>
              <span class="note-row__time">{{ note.updatedAt.slice(5, 16) }}</span>
            </button>
          </section>

          <!-- 最近学习 -->
          <section class="home__panel">
            <div class="home__panel-head">
              <BookOpen :size="15" />
              <span>最近学习</span>
            </div>
            <div class="home__topics">
              <span v-for="topic in learningTopics" :key="topic" class="topic-chip">{{ topic }}</span>
            </div>
          </section>
        </div>

        <!-- 右列 -->
        <div class="home__col">
          <!-- AI 推荐 -->
          <section class="home__panel home__panel--ai">
            <div class="home__panel-head">
              <Sparkles :size="15" class="home__ai-icon" />
              <span>AI 推荐</span>
            </div>
            <p class="home__ai-text">
              你最近学习了 <strong>RAG</strong> 和 <strong>Milvus</strong>，知识库中还有
              <strong>7 篇笔记</strong>与这两个主题高度相关，其中 3 篇还没有建立双向链接。
            </p>
            <div class="home__ai-actions">
              <button class="home__ai-btn" @click="askAi('我的知识库中哪些笔记和 RAG、Milvus 相关？')">
                查看关联知识 <ArrowRight :size="13" />
              </button>
              <button class="home__ai-ghost" @click="router.push({ name: 'graph' })">
                <Link2 :size="13" /> 知识图谱
              </button>
            </div>
          </section>

          <!-- 今日知识 -->
          <section class="home__panel">
            <div class="home__panel-head">
              <BrainCircuit :size="15" />
              <span>今日知识</span>
            </div>
            <div class="today">
              <div class="today__row">
                <span class="today__icon">📚</span>
                <div>
                  <div class="today__num">3 篇笔记</div>
                  <div class="today__label">今日新增</div>
                </div>
              </div>
              <div class="today__row">
                <span class="today__icon">🔗</span>
                <div>
                  <div class="today__num">12 个知识关联</div>
                  <div class="today__label">新发现</div>
                </div>
              </div>
              <div class="today__row">
                <span class="today__icon">🤖</span>
                <div>
                  <div class="today__num">5 个知识薄弱点</div>
                  <div class="today__label">AI 分析</div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.home {
  max-width: 980px;
  width: 100%;
  margin: 0 auto;
  padding: var(--sp-8) var(--sp-6) var(--sp-10);
}

.home__hello {
  font-size: var(--fs-2xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.home__wave {
  font-size: 0.8em;
}

.home__sub {
  margin-top: var(--sp-1);
  color: var(--text-3);
  font-size: var(--fs-base);
}

.home__stats {
  display: flex;
  gap: var(--sp-8);
  margin-top: var(--sp-5);
  padding: var(--sp-4) var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
}

.home__stats--empty {
  color: var(--text-3);
  font-size: var(--fs-sm);
}

.home__stat-num {
  font-size: var(--fs-xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(135deg, var(--text-1), var(--primary));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.home__stat-label {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-top: 1px;
}

.home__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);
  margin-top: var(--sp-5);
}

.home__col {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-width: 0;
}

.home__panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
}

.home__panel--ai {
  border-color: var(--primary-border);
  background: linear-gradient(160deg, var(--primary-muted), var(--surface) 45%);
}

.home__panel-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: var(--sp-3);
}

.home__ai-icon {
  color: var(--primary);
}

.home__ai-text {
  font-size: var(--fs-base);
  color: var(--text-2);
  line-height: 1.7;
}

.home__ai-text strong {
  color: var(--text-1);
}

.home__ai-actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.home__ai-btn {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  padding: 6px var(--sp-3);
  font-size: var(--fs-sm);
  font-weight: 550;
  color: #fff;
  background: var(--primary);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.home__ai-btn:hover {
  background: var(--primary-hover);
}

.home__ai-ghost {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  padding: 6px var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--text-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.home__ai-ghost:hover {
  color: var(--text-1);
  border-color: var(--primary-border);
}

.note-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
  padding: var(--sp-2) var(--sp-2);
  border-radius: var(--r-md);
  text-align: left;
  transition: background var(--dur-fast) var(--ease);
}

.note-row:hover {
  background: var(--surface-hover);
}

.note-row__icon {
  color: var(--text-3);
}

.note-row__title {
  flex: 1;
  font-size: var(--fs-base);
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-row__time {
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.home__topics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.topic-chip {
  padding: 4px var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--text-2);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-full);
}

.today__row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
}

.today__row + .today__row {
  border-top: 1px solid var(--border);
}

.today__icon {
  font-size: var(--fs-md);
}

.today__num {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
}

.today__label {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
</style>
