<script setup lang="ts">
/**
 * 通用模态框外壳：遮罩 + 居中容器 + 打开/关闭动画 + 无障碍语义。
 *
 * 职责边界：只管「壳」，不含任何业务内容。
 * - role="dialog" + aria-modal + aria-labelledby（标题 id 由调用方传入）
 * - ESC 关闭、点击遮罩关闭（:dismissible=false 时两者都禁用）
 * - 打开时锁定 body 滚动、焦点移入面板；卸载后归还焦点给触发元素
 *
 * 由外部 v-if 控制挂载，配合 Transition appear 实现入场动画。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 供 aria-labelledby 引用的标题元素 id */
    titleId: string;
    /** 是否允许 ESC / 点击遮罩关闭 */
    dismissible?: boolean;
    /** 面板最大宽度（CSS 长度） */
    width?: string;
  }>(),
  { dismissible: true, width: 'var(--modal-w)' },
);

const emit = defineEmits<{ (e: 'close'): void }>();

const panel = ref<HTMLElement | null>(null);
let lastFocused: HTMLElement | null = null;
let previousOverflow = '';

function requestClose(): void {
  if (!props.dismissible) return;
  emit('close');
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    requestClose();
    return;
  }
  // 焦点陷阱：Tab / Shift+Tab 在面板内循环
  if (event.key !== 'Tab' || !panel.value) return;
  const focusable = Array.from(
    panel.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((el) => el.offsetParent !== null);
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (!first || !last) return;
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

onMounted(() => {
  lastFocused = document.activeElement as HTMLElement | null;
  previousOverflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  document.addEventListener('keydown', onKeydown, true);
  requestAnimationFrame(() => {
    // 默认焦点落在面板本身（无聚焦环）；需要聚焦主操作时由内容标记 data-autofocus
    const target = panel.value?.querySelector<HTMLElement>('[data-autofocus]:not([disabled])');
    (target ?? panel.value)?.focus();
  });
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true);
  document.body.style.overflow = previousOverflow;
  lastFocused?.focus?.();
});
</script>

<template>
  <Transition name="modal" appear>
    <div class="modal" @click.self="requestClose()">
      <div
        ref="panel"
        class="modal__panel"
        :style="{ maxWidth: width }"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
      >
        <slot />
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: grid;
  place-items: center;
  padding: var(--sp-4);
  background: var(--overlay);
  backdrop-filter: blur(var(--overlay-blur));
  /* 小屏内容超高时允许纵向滚动，不裁切 */
  overflow-y: auto;
}

.modal__panel {
  position: relative;
  width: 100%;
  margin: auto;
  background: var(--surface-elevated);
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-pop);
  outline: none;
}

/* 入场 / 退场：遮罩 opacity，面板 opacity + scale + translateY，克制不夸张 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity var(--dur-modal) var(--ease-out);
}

.modal-enter-active .modal__panel,
.modal-leave-active .modal__panel {
  transition: transform var(--dur-modal) var(--ease-out), opacity var(--dur-modal) var(--ease-out);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal__panel,
.modal-leave-to .modal__panel {
  opacity: 0;
  transform: scale(0.98) translateY(6px);
}

/* 小屏：贴边并允许滚动，避免内容被裁切 */
@media (max-width: 480px) {
  .modal {
    padding: var(--sp-3);
    place-items: start center;
  }
}
</style>
