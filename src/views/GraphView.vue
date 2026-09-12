<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Waypoints, Loader2 } from 'lucide-vue-next';
import type { KnowledgeGraphData } from '@/types/knowledge';
import { searchService } from '@/services/searchService';
import KnowledgeGraph from '@/components/knowledge/KnowledgeGraph.vue';

const graph = ref<KnowledgeGraphData | null>(null);
const loading = ref(true);

onMounted(async () => {
  graph.value = await searchService.getGraph();
  loading.value = false;
});
</script>

<template>
  <div class="graph-view">
    <header class="graph-view__bar">
      <div class="graph-view__title">
        <Waypoints :size="15" />
        <span>知识图谱</span>
      </div>
      <span class="graph-view__meta" v-if="graph">
        {{ graph.topics.length }} 个主题 · {{ graph.edges.length }} 条关系
      </span>
    </header>

    <div class="graph-view__body">
      <div v-if="loading" class="graph-view__loading">
        <Loader2 :size="20" class="spin" />
        <span>正在加载知识图谱…</span>
      </div>
      <KnowledgeGraph
        v-else-if="graph"
        :topics="graph.topics"
        :edges="graph.edges"
      />
    </div>
  </div>
</template>

<style scoped>
.graph-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.graph-view__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
}

.graph-view__title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.graph-view__title svg {
  color: var(--primary);
}

.graph-view__meta {
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.graph-view__body {
  flex: 1;
  min-height: 0;
}

.graph-view__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  height: 100%;
  color: var(--text-3);
  font-size: var(--fs-sm);
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
