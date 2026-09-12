/**
 * API 统一出口 —— 为后续 Spring Boot Backend（apps/backend）接入预留的唯一边界。
 *
 * 约定：
 * - 所有真实后端调用一律经由本模块发出，禁止在组件/store 中硬编码 http://localhost:8080
 * - 开发模式推荐 VITE_API_BASE_URL 留空，走 Vite proxy 的 /api 相对路径（见 vite.config.ts）
 * - 生产/直连场景通过 apps/frontend/.env.* 配置 VITE_API_BASE_URL
 */

/** 后端 API 基地址（默认空串 = 同源 + /api 代理） */
export const API_BASE_URL: string = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

/** 统一请求入口：自动拼接基地址，非 2xx 抛错 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  if (!response.ok) {
    throw new Error(`API ${path} failed: ${response.status}`);
  }
  return (await response.json()) as T;
}
