<script setup lang="ts">
/**
 * Local First 隐私说明块 —— 第三信息层级：轻量、紧凑，不抢主操作注意力。
 */
import { ShieldCheck } from 'lucide-vue-next';
</script>

<template>
  <div class="local-first">
    <ShieldCheck :size="14" :stroke-width="1.8" class="local-first__icon" />
    <div class="local-first__body">
      <span class="local-first__title">Local First</span>
      <p class="local-first__text">你的 Markdown 文件只在本机读取，不会自动上传你的 Vault。</p>
    </div>
  </div>
</template>

<style scoped>
.local-first {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
}

.local-first__icon {
  margin-top: 1px;
  color: var(--success);
  flex-shrink: 0;
}

.local-first__body {
  min-width: 0;
}

.local-first__title {
  display: block;
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-2);
}

.local-first__text {
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--text-3);
}
</style>
