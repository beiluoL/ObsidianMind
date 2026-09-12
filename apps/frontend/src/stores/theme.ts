/**
 * 主题状态中心：Light / Dark / System 三态。
 *
 * - mode      : 用户显式选择（默认 system）
 * - resolved  : 实际生效的主题（system 模式下跟随 prefers-color-scheme）
 * - 持久化    : localStorage['obsidianmind-theme']
 * - 防闪烁    : index.html 内联脚本已按同一份 key 预先写入 data-theme，
 *               本 store 只负责后续同步与响应系统切换，不重复决策首屏。
 */
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

export type ThemeMode = 'system' | 'light' | 'dark';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'obsidianmind-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

function isMode(value: unknown): value is ThemeMode {
  return value === 'system' || value === 'light' || value === 'dark';
}

/** 读取持久化偏好，缺省 system；隐私模式下 localStorage 抛错则降级 */
function readStoredMode(): ThemeMode {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return isMode(raw) ? raw : 'system';
  } catch {
    return 'system';
  }
}

function systemTheme(): ResolvedTheme {
  if (typeof window === 'undefined' || !window.matchMedia) return 'dark';
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light';
}

/** 把主题写到 <html data-theme>，同时同步 color-scheme（滚动条/表单原生控件） */
function applyTheme(theme: ResolvedTheme): void {
  const root = document.documentElement;
  root.setAttribute('data-theme', theme);
  root.style.colorScheme = theme;
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStoredMode());
  const systemPrefers = ref<ResolvedTheme>(systemTheme());

  const resolved = computed<ResolvedTheme>(() =>
    mode.value === 'system' ? systemPrefers.value : mode.value,
  );

  /** 系统外观变化监听（仅注册一次） */
  let mediaQuery: MediaQueryList | null = null;
  const onSystemChange = (event: MediaQueryListEvent): void => {
    systemPrefers.value = event.matches ? 'dark' : 'light';
  };

  function init(): void {
    applyTheme(resolved.value);
    if (typeof window === 'undefined' || !window.matchMedia) return;
    mediaQuery = window.matchMedia(DARK_QUERY);
    // Safari < 14 只有 addListener
    if (mediaQuery.addEventListener) mediaQuery.addEventListener('change', onSystemChange);
    else mediaQuery.addListener(onSystemChange);
  }

  function setMode(next: ThemeMode): void {
    mode.value = next;
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // 隐私模式下写入失败：本次会话仍生效，仅不持久化
    }
    applyTheme(resolved.value);
  }

  /** 供 Header 快捷按钮三态循环使用 */
  function cycle(): void {
    const order: ThemeMode[] = ['system', 'light', 'dark'];
    const index = order.indexOf(mode.value);
    setMode(order[(index + 1) % order.length] as ThemeMode);
  }

  return { mode, resolved, init, setMode, cycle };
});
