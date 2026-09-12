<script setup lang="ts">
/**
 * 状态栏：Vault 连接状态、扫描统计与索引进度实时展示（数据源自 knowledge/vault store）。
 */
import { useKnowledgeStore } from '@/stores/knowledge';
import { useVaultStore } from '@/stores/vault';
import { Lock, Database, Cpu, Layers, RefreshCw, FolderOpen, Loader2 } from 'lucide-vue-next';

const knowledge = useKnowledgeStore();
const vault = useVaultStore();
</script>

<template>
  <footer class="statusbar">
    <div class="statusbar__group">
      <span class="statusbar__local"><Lock :size="11" /> Local First</span>
      <span class="statusbar__sep"></span>
      <span class="statusbar__svc">
        <FolderOpen :size="11" />
        {{ vault.info ? vault.info.name : '未连接 Vault' }}
      </span>
      <span class="statusbar__svc">
        <Cpu :size="11" /> Ollama <span class="dot" :class="knowledge.health?.ollama === 'connected' ? 'dot--ok' : 'dot--off'"></span>
      </span>
      <span class="statusbar__svc">
        <Database :size="11" /> Milvus <span class="dot" :class="knowledge.health?.milvus === 'connected' ? 'dot--ok' : 'dot--off'"></span>
      </span>
    </div>

    <div class="statusbar__group">
      <span v-if="vault.progress.scanning" class="statusbar__stat statusbar__stat--scan">
        <Loader2 :size="11" class="spin" />
        {{ vault.progress.message }}
      </span>
      <template v-else-if="vault.info">
        <span class="statusbar__stat">
          <Layers :size="11" />
          {{ vault.info.noteCount.toLocaleString() }} Notes · {{ vault.info.linkCount.toLocaleString() }} Links
        </span>
      </template>
      <span v-else-if="knowledge.health" class="statusbar__stat">
        <Layers :size="11" />
        {{ knowledge.health.noteCount.toLocaleString() }} Notes · {{ knowledge.health.chunkCount.toLocaleString() }} Chunks
      </span>
      <button class="statusbar__btn" title="重新扫描 Vault" @click="vault.rescan()">
        <RefreshCw :size="11" />
      </button>
    </div>
  </footer>
</template>

<style scoped>
.statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--statusbar-h);
  padding: 0 var(--sp-4);
  background: var(--surface);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.statusbar__group {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}

.statusbar__local {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-2);
  font-weight: 550;
}

.statusbar__svc {
  display: flex;
  align-items: center;
  gap: 5px;
}

.statusbar__sep {
  width: 1px;
  height: 12px;
  background: var(--border);
}

.statusbar__stat {
  display: flex;
  align-items: center;
  gap: 5px;
  font-variant-numeric: tabular-nums;
}

.statusbar__muted {
  color: var(--text-3);
}

.statusbar__btn {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
  color: var(--text-3);
  transition: all var(--dur-fast) var(--ease);
}

.statusbar__btn:hover {
  background: var(--surface-hover);
  color: var(--text-1);
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
</style>
