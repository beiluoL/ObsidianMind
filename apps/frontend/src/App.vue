<script setup lang="ts">
/**
 * 应用外壳：三栏布局（Header / Sidebar / 工作区 / AI 面板）+ 欢迎引导层。
 * 未连接 Vault 时整屏展示 VaultWelcome；连接后由各路由视图接管工作区。
 */
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useVaultStore } from '@/stores/vault';
import AppHeader from '@/components/layout/AppHeader.vue';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import StatusBar from '@/components/layout/StatusBar.vue';
import ChatPanel from '@/components/ai/ChatPanel.vue';
import VaultWelcome from '@/components/vault/VaultWelcome.vue';

const route = useRoute();
const knowledge = useKnowledgeStore();
const vault = useVaultStore();

const showAiPanel = computed(() => !['graph', 'settings'].includes(route.name as string));
const isWideView = computed(() => ['graph', 'settings'].includes(route.name as string));

onMounted(() => {
  void vault.init().then(() => {
    knowledge.loadHealth();
  });
});
</script>

<template>
  <div class="app-shell">
    <AppHeader class="app-shell__header" />
    <div class="app-shell__body">
      <AppSidebar v-if="!isWideView" class="app-shell__sidebar" />
      <main class="app-shell__main" :class="{ 'app-shell__main--wide': isWideView }">
        <RouterView />
      </main>
      <ChatPanel v-if="showAiPanel" class="app-shell__ai" />
    </div>
    <StatusBar class="app-shell__status" />
    <VaultWelcome v-if="vault.showWelcome" />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
}

.app-shell__header {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
}

.app-shell__body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.app-shell__sidebar {
  flex-shrink: 0;
  width: var(--sidebar-w);
  border-right: 1px solid var(--border);
}

.app-shell__main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.app-shell__main--wide {
  overflow: hidden;
}

.app-shell__ai {
  flex-shrink: 0;
  width: var(--ai-panel-w);
  border-left: 1px solid var(--border);
}

.app-shell__status {
  flex-shrink: 0;
  border-top: 1px solid var(--border);
}
</style>
