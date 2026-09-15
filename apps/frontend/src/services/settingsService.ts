import type { AppSettings, IndexResult, IndexStatus, ModelInfo, ServiceHealth } from '@/types/knowledge';
import { apiFetch } from './api';

/** 索引与设置服务：索引走真实后端（Phase 3），其余仍为 Mock */

export const indexService = {
  async getStatus(): Promise<IndexStatus> {
    return { isIndexing: false, progress: 100, totalNotes: 1248, indexedNotes: 1248 };
  },

  /** 同步执行一次增量索引（扫描 → 解析 → 切块 → Embedding → Milvus） */
  async triggerReindex(): Promise<IndexResult> {
    return apiFetch<IndexResult>('/api/v1/index/run', { method: 'POST' });
  },
};

export const settingsService = {
  async getModels(): Promise<ModelInfo[]> {
    return [
      { id: 'qwen3', name: 'Qwen3', provider: 'ollama', available: true },
      { id: 'llama3', name: 'Llama 3', provider: 'ollama', available: true },
      { id: 'deepseek', name: 'DeepSeek', provider: 'deepseek', available: false },
    ];
  },

  async getHealth(): Promise<ServiceHealth> {
    return {
      ollama: 'connected',
      milvus: 'connected',
      embedding: 'ready',
      noteCount: 1248,
      chunkCount: 32581,
      connectionCount: 18492,
      lastIndexedAt: '2 分钟前',
    };
  },

  async getSettings(): Promise<AppSettings> {
    return {
      llmProvider: 'Ollama (localhost:11434)',
      llmModel: 'Qwen3',
      embeddingModel: 'Qwen3-Embedding (bge-m3 兼容)',
      milvusHost: 'localhost',
      milvusPort: '19530',
      autoIndex: true,
      indexInterval: '每 30 分钟',
    };
  },
};
