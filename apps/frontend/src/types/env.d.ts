/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 基地址，留空走 Vite /api 代理 */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
