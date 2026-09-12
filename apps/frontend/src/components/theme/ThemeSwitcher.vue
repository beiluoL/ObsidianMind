<script setup lang="ts">
/**
 * 主题分段控件：[ 系统 | 浅色 | 深色 ]。
 * 使用 radiogroup 语义，键盘左右方向键可切换。
 */
import { Monitor, Sun, Moon } from 'lucide-vue-next';
import { useThemeStore } from '@/stores/theme';
import type { ThemeMode } from '@/stores/theme';

const theme = useThemeStore();

const options: { value: ThemeMode; label: string; icon: typeof Monitor }[] = [
  { value: 'system', label: '跟随系统', icon: Monitor },
  { value: 'light', label: '浅色', icon: Sun },
  { value: 'dark', label: '深色', icon: Moon },
];

function move(offset: number): void {
  const index = options.findIndex((o) => o.value === theme.mode);
  const next = options[(index + offset + options.length) % options.length];
  if (next) theme.setMode(next.value);
}
</script>

<template>
  <div class="theme-switch" role="radiogroup" aria-label="主题" @keydown.right.prevent="move(1)" @keydown.left.prevent="move(-1)">
    <button
      v-for="option in options"
      :key="option.value"
      class="theme-switch__opt"
      type="button"
      role="radio"
      :aria-checked="theme.mode === option.value"
      :tabindex="theme.mode === option.value ? 0 : -1"
      :class="{ 'theme-switch__opt--active': theme.mode === option.value }"
      @click="theme.setMode(option.value)"
    >
      <component :is="option.icon" :size="14" :stroke-width="1.8" />
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.theme-switch {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
}

.theme-switch__opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--text-2);
  border-radius: var(--r-sm);
  transition: color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.theme-switch__opt:hover {
  color: var(--text-1);
  background: var(--surface-hover);
}

.theme-switch__opt:active {
  transform: translateY(0.5px);
}

.theme-switch__opt--active {
  color: var(--primary);
  background: var(--surface);
  border-color: var(--border);
  box-shadow: var(--shadow-sm);
  font-weight: 550;
}

.theme-switch__opt--active:hover {
  color: var(--primary);
  background: var(--surface);
}
</style>
