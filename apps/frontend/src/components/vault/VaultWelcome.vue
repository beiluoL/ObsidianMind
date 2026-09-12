<script setup lang="ts">
import { computed } from 'vue';
import { FolderOpen, HardDriveDownload, X, ShieldCheck, Gem } from 'lucide-vue-next';
import { useVaultStore } from '@/stores/vault';

const vault = useVaultStore();

const mode = computed(() => (vault.status === 'needs-permission' ? 'restore' : 'first'));
const restoreName = computed(() => vault.pendingHandle?.name ?? '');

async function pick(): Promise<void> {
  if (mode.value === 'restore') {
    const ok = await vault.grantPermission();
    if (!ok) await vault.pickAndConnect();
  } else {
    await vault.pickAndConnect();
  }
}
</script>

<template>
  <div class="welcome">
    <div class="welcome__card">
      <button class="welcome__close" title="暂不连接" @click="vault.dismissWelcome()"><X :size="16" /></button>

      <div class="welcome__brand">
        <div class="welcome__logo"><Gem :size="22" :stroke-width="1.6" /></div>
        <span class="welcome__name">ObsidianMind</span>
      </div>

      <h1 class="welcome__title">
        {{ mode === 'restore' ? '欢迎回来' : '连接你的 Obsidian Vault' }}
      </h1>
      <p class="welcome__desc">
        <template v-if="mode === 'restore'">
          检测到你之前连接过 <strong>{{ restoreName }}</strong
          >，浏览器需要你重新确认授权后才能访问。
        </template>
        <template v-else>
          ObsidianMind 只读取<strong>你主动选择</strong>的 Markdown 文件夹，不会自动扫描其他目录，数据不会离开本机。
        </template>
      </p>

      <div class="welcome__privacy">
        <ShieldCheck :size="14" />
        <span>Local First · Markdown 是 Source of Truth · 不会上传任何文件</span>
      </div>

      <div class="welcome__actions">
        <button class="welcome__primary" @click="pick">
          <FolderOpen :size="15" />
          {{ mode === 'restore' ? '重新授权此目录' : '选择 Obsidian Vault' }}
        </button>
        <button class="welcome__ghost" @click="vault.connectDemo()">
          <HardDriveDownload :size="15" />
          载入演示 Vault
        </button>
      </div>
      <p class="welcome__hint">演示 Vault 写入浏览器内置存储（OPFS），读写与监听和真实目录走同一套引擎。</p>

      <p v-if="vault.error" class="welcome__error">⚠️ {{ vault.error }}</p>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  background: rgba(9, 9, 15, 0.78);
  backdrop-filter: blur(6px);
}

.welcome__card {
  position: relative;
  width: 460px;
  max-width: calc(100vw - 48px);
  padding: var(--sp-7);
  background: var(--surface-2);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-pop);
}

.welcome__close {
  position: absolute;
  top: var(--sp-4);
  right: var(--sp-4);
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-sm);
  color: var(--text-3);
  transition: all var(--dur-fast) var(--ease);
}

.welcome__close:hover {
  background: var(--surface-hover);
  color: var(--text-1);
}

.welcome__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-5);
}

.welcome__logo {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--r-md);
  color: #c4b5fd;
  background: linear-gradient(135deg, rgba(139, 124, 246, 0.22), rgba(96, 165, 250, 0.12));
  border: 1px solid var(--primary-border);
}

.welcome__name {
  font-size: var(--fs-md);
  font-weight: 700;
}

.welcome__title {
  font-size: var(--fs-xl);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.welcome__desc {
  margin-top: var(--sp-2);
  font-size: var(--fs-base);
  line-height: 1.7;
  color: var(--text-2);
}

.welcome__desc strong {
  color: var(--text-1);
}

.welcome__privacy {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--success);
  background: rgba(74, 222, 128, 0.08);
  border: 1px solid rgba(74, 222, 128, 0.18);
  border-radius: var(--r-md);
}

.welcome__actions {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-5);
}

.welcome__primary,
.welcome__ghost {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 9px var(--sp-4);
  font-size: var(--fs-base);
  font-weight: 550;
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.welcome__primary {
  color: #fff;
  background: var(--primary);
}

.welcome__primary:hover {
  background: var(--primary-hover);
}

.welcome__ghost {
  color: var(--text-2);
  border: 1px solid var(--border-strong);
}

.welcome__ghost:hover {
  color: var(--text-1);
  border-color: var(--primary-border);
}

.welcome__hint {
  margin-top: var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: 1.6;
}

.welcome__error {
  margin-top: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--danger);
}
</style>
