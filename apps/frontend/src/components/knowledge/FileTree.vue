<script setup lang="ts">
/**
 * 文件树：递归渲染 Vault 目录结构，支持展开/折叠（展开态持久化）、
 * 点击笔记跳转 /note/:id（id 为含斜杠的相对路径）。
 */
import type { VaultNode } from '@/types/knowledge';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useRouter } from 'vue-router';
import { ChevronRight, FileText, Folder } from 'lucide-vue-next';

const props = defineProps<{
  node: VaultNode;
  depth: number;
}>();

const knowledge = useKnowledgeStore();
const router = useRouter();

const isFolder = (): boolean => props.node.type === 'folder';
const isOpen = (): boolean => knowledge.expandedFolders.has(props.node.id);

function onClick(): void {
  if (isFolder()) {
    knowledge.toggleFolder(props.node.id);
  } else if (props.node.noteId) {
    void knowledge.openNote(props.node.noteId);
    router.push({ name: 'note', params: { id: props.node.noteId } });
  }
}
</script>

<template>
  <div>
    <button
      class="tree-node"
      :class="{ 'tree-node--active': !isFolder() && knowledge.currentNote?.id === node.noteId }"
      :style="{ paddingLeft: `${depth * 14 + 8}px` }"
      :title="node.name"
      @click="onClick"
      @contextmenu.prevent
    >
      <ChevronRight v-if="isFolder()" :size="13" class="tree-node__chevron" :class="{ open: isOpen() }" />
      <span v-else class="tree-node__chevron tree-node__chevron--hidden"></span>
      <Folder v-if="isFolder()" :size="14" :stroke-width="1.7" class="tree-node__icon tree-node__icon--folder" />
      <FileText v-else :size="14" :stroke-width="1.7" class="tree-node__icon" />
      <span class="tree-node__name">{{ node.name }}</span>
    </button>

    <template v-if="isFolder() && isOpen() && node.children">
      <FileTree v-for="child in node.children" :key="child.id" :node="child" :depth="depth + 1" />
    </template>
  </div>
</template>

<style scoped>
.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  height: 27px;
  padding-right: var(--sp-2);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  color: var(--text-2);
  text-align: left;
  white-space: nowrap;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.tree-node:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.tree-node--active {
  background: var(--primary-muted);
  color: var(--primary);
}

.tree-node--active .tree-node__name {
  font-weight: 550;
}

.tree-node__chevron {
  color: var(--text-3);
  transition: transform var(--dur-fast) var(--ease);
}

.tree-node__chevron.open {
  transform: rotate(90deg);
}

.tree-node__chevron--hidden {
  width: 13px;
  flex-shrink: 0;
}

.tree-node__icon {
  color: var(--text-3);
}

.tree-node__icon--folder {
  color: var(--info);
  opacity: 0.75;
}

.tree-node__name {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
