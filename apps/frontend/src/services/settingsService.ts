import type { AppSettings, IndexStatus, ModelInfo, ServiceHealth } from '@/types/knowledge';

/** 索引与设置服务 —— 未来替换为 /api/index /api/models /api/settings */

export const indexService = {
  async getStatus(): Promise<IndexStatus> {
    return { isIndexing: false, progress: 100, totalNotes: 1248, indexedNotes: 1248 };
  },

  async triggerReindex(): Promise<void> {
    // 未来: POST /api/index
    await new Promise((resolve) => setTimeout(resolve, 500));
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
