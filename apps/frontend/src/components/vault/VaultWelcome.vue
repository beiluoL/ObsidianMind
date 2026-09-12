<script setup lang="ts">
/**
 * Vault 欢迎层（兼容入口）。
 *
 * 具体实现已迁移到 VaultConnectModal（新的视觉层级 / 拖拽落区 / 加载态 / 错误态）。
 * 本文件保留为 App.vue 的既有挂载点，避免改动应用外壳与既有调用方。
 */
import VaultConnectModal from '@/components/vault/VaultConnectModal.vue';
</script>

<template>
  <VaultConnectModal />
</template>
