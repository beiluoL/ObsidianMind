<script setup lang="ts">
/**
 * 顶栏主题快捷切换：系统 → 浅色 → 深色 循环。
 * 完整三态选择放在「设置 → 外观」。
 */
import { computed } from 'vue';
import { Monitor, Sun, Moon } from 'lucide-vue-next';
import { useThemeStore } from '@/stores/theme';

const theme = useThemeStore();

const icon = computed(() => (theme.mode === 'light' ? Sun : theme.mode === 'dark' ? Moon : Monitor));
const label = computed(() =>
  theme.mode === 'light' ? '浅色' : theme.mode === 'dark' ? '深色' : '跟随系统',
);
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="`主题：${label}（点击切换）`"
    :title="`主题：${label}（点击切换）`"
    @click="theme.cycle()"
  >
    <component :is="icon" :size="16" :stroke-width="1.7" />
  </button>
</template>

<style scoped>
.theme-toggle {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--r-md);
  color: var(--text-2);
  transition: color var(--dur) var(--ease), background var(--dur) var(--ease);
}

.theme-toggle:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.theme-toggle:active {
  transform: translateY(0.5px);
}
</style>
