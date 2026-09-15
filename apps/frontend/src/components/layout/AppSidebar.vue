<script setup lang="ts">
/**
 * 侧边导航：六个主视图入口，当前路由高亮（含 /note/:id 归属知识库高亮的边界处理）。
 * 文件树支持按文件名过滤：命中笔记保留、命中文件夹连同子树保留，过滤态下自动展开。
 */
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  House,
  BotMessageSquare,
  Waypoints,
  Search,
  History,
  Heart,
  Plus,
  FolderPlus,
  RefreshCw,
} from 'lucide-vue-next';
import FileTree from '@/components/knowledge/FileTree.vue';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useVaultStore } from '@/stores/vault';
import { Loader2 } from 'lucide-vue-next';
import type { VaultNode } from '@/types/knowledge';

const route = useRoute();
const router = useRouter();
const knowledge = useKnowledgeStore();
const vault = useVaultStore();

/** 文件树过滤关键词（按文件名匹配，大小写不敏感） */
const treeFilter = ref('');

/** 过滤后文件树：命中笔记保留；有命中后代的文件夹保留（子树随之保留） */
const filteredTree = computed<VaultNode | null>(() => {
  const keyword = treeFilter.value.trim().toLowerCase();
  const root = knowledge.tree;
  if (!keyword || !root) return root;
  const prune = (node: VaultNode): VaultNode | null => {
    if (node.type === 'note') {
      return node.name.toLowerCase().includes(keyword) ? node : null;
    }
    const children = (node.children ?? [])
      .map(prune)
      .filter((child): child is VaultNode => child !== null);
    return children.length ? { ...node, children } : null;
  };
  return prune(root);
});

const navItems = [
  { name: 'home', label: '首页', icon: House },
  { name: 'ai', label: 'AI 助手', icon: BotMessageSquare },
  { name: 'graph', label: '知识图谱', icon: Waypoints },
  { name: 'search', label: '搜索', icon: Search },
];

const listItems = [
  { name: 'recent', label: '最近笔记', icon: History },
  { name: 'favorites', label: '收藏', icon: Heart },
];

const activeList = ref('');

function clickListItem(name: string): void {
  activeList.value = activeList.value === name ? '' : name;
  if (name === 'recent') router.push({ name: 'home', query: { list: 'recent' } });
  if (name === 'favorites') router.push({ name: 'home', query: { list: 'favorites' } });
}
</script>

<template>
  <aside class="sidebar">
    <nav class="sidebar__nav">
      <RouterLink
        v-for="item in navItems"
        :key="item.name"
        :to="{ name: item.name }"
        class="sidebar__item"
        :class="{ 'sidebar__item--active': route.name === item.name }"
      >
        <component :is="item.icon" :size="15" :stroke-width="1.7" />
        <span>{{ item.label }}</span>
      </RouterLink>

      <div class="sidebar__divider"></div>

      <button
        v-for="item in listItems"
        :key="item.name"
        class="sidebar__item"
        :class="{ 'sidebar__item--active': activeList === item.name }"
        @click="clickListItem(item.name)"
      >
        <component :is="item.icon" :size="15" :stroke-width="1.7" />
        <span>{{ item.label }}</span>
      </button>

      <div class="sidebar__divider"></div>
    </nav>

    <div class="sidebar__tree-head">
      <span class="section-label">{{ vault.info ? vault.info.name : '文件树（演示数据）' }}</span>
      <div class="sidebar__tree-actions">
        <button class="sidebar__tree-btn" title="新建笔记"><Plus :size="13" /></button>
        <button class="sidebar__tree-btn" title="新建文件夹"><FolderPlus :size="13" /></button>
        <button class="sidebar__tree-btn" title="重新扫描 Vault" @click="vault.rescan()">
          <Loader2 v-if="vault.progress.scanning" :size="13" class="spin" />
          <RefreshCw v-else :size="13" />
        </button>
      </div>
    </div>

    <div class="sidebar__tree-search">
      <Search :size="12" :stroke-width="1.8" class="sidebar__tree-search-icon" />
      <input
        v-model="treeFilter"
        class="sidebar__tree-search-input"
        type="text"
        placeholder="搜索文件名…"
        aria-label="按文件名过滤文件树"
      />
    </div>

    <div class="sidebar__tree">
      <div v-if="vault.progress.scanning && !knowledge.tree" class="sidebar__tree-loading">
        <Loader2 :size="15" class="spin" />
        <span>{{ vault.progress.message || '正在扫描知识库…' }}</span>
      </div>
      <FileTree
        v-else-if="filteredTree"
        :node="filteredTree"
        :depth="0"
        :force-open="treeFilter.trim().length > 0"
      />
      <div v-else-if="treeFilter.trim()" class="sidebar__tree-loading">
        <span>没有匹配「{{ treeFilter.trim() }}」的文件</span>
      </div>
      <div v-else class="sidebar__tree-loading">
        <span>暂无数据</span>
      </div>
    </div>
  </aside>
</template>

<script lang="ts">
export default { name: 'AppSidebar' };
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  overflow: hidden;
}

.sidebar__nav {
  padding: var(--sp-3) var(--sp-2) 0;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  width: 100%;
  padding: 7px var(--sp-3);
  margin-bottom: 1px;
  border-radius: var(--r-md);
  font-size: var(--fs-base);
  color: var(--text-2);
  text-decoration: none;
  text-align: left;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.sidebar__item:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.sidebar__item--active {
  background: var(--primary-muted);
  color: var(--primary);
  font-weight: 550;
}

.sidebar__divider {
  height: 1px;
  margin: var(--sp-3) var(--sp-2);
  background: var(--border);
}

.sidebar__tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-2) var(--sp-4) var(--sp-1);
}

.sidebar__tree-actions {
  display: flex;
  gap: 2px;
}

.sidebar__tree-btn {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--r-sm);
  color: var(--text-3);
  transition: all var(--dur-fast) var(--ease);
}

.sidebar__tree-btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.sidebar__tree {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--sp-2) var(--sp-4);
}

.sidebar__tree-search {
  position: relative;
  display: flex;
  align-items: center;
  margin: 0 var(--sp-3) var(--sp-2);
}

.sidebar__tree-search-icon {
  position: absolute;
  left: 8px;
  color: var(--text-3);
  pointer-events: none;
}

.sidebar__tree-search-input {
  width: 100%;
  height: 26px;
  padding: 0 var(--sp-2) 0 26px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  background: var(--surface-raised, var(--surface));
  color: var(--text-1);
  font-size: var(--fs-sm);
  transition: border-color var(--dur-fast) var(--ease);
}

.sidebar__tree-search-input::placeholder {
  color: var(--text-3);
}

.sidebar__tree-search-input:focus {
  outline: none;
  border-color: var(--primary);
}
</style>
