import type {
  AvailableModels,
  ModelUpsertPayload,
  ModelView,
  ProviderUpsertPayload,
  ProviderView,
  TestConnectionResult,
} from '@/types/modelCenter';
import { apiFetch } from './api';

/**
 * AI Model Center 服务（Phase 5.5）：Provider / Model 配置管理。
 * 安全边界：本层永远拿不到 API Key 原文——ProviderView 只含 masked 值；
 * Upsert 时 apiKey 仅在用户显式输入时随请求发送一次。
 */

const BASE = '/api/v1/ai';

export const aiModelsService = {
  // ---- Providers ----

  async listProviders(): Promise<ProviderView[]> {
    return apiFetch<ProviderView[]>(`${BASE}/providers`);
  },

  async createProvider(payload: ProviderUpsertPayload): Promise<ProviderView> {
    return apiFetch<ProviderView>(`${BASE}/providers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },

  async updateProvider(id: string, payload: ProviderUpsertPayload): Promise<ProviderView> {
    return apiFetch<ProviderView>(`${BASE}/providers/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },

  async deleteProvider(id: string): Promise<void> {
    await apiFetch<void>(`${BASE}/providers/${id}`, { method: 'DELETE' });
  },

  async testProvider(id: string, modelName?: string): Promise<TestConnectionResult> {
    return apiFetch<TestConnectionResult>(`${BASE}/providers/${id}/test`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(modelName ? { modelName } : {}),
    });
  },

  async availableModels(id: string): Promise<AvailableModels> {
    return apiFetch<AvailableModels>(`${BASE}/providers/${id}/models`);
  },

  // ---- Models ----

  async listModels(): Promise<ModelView[]> {
    return apiFetch<ModelView[]>(`${BASE}/models`);
  },

  async createModel(payload: ModelUpsertPayload): Promise<ModelView> {
    return apiFetch<ModelView>(`${BASE}/models`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },

  async updateModel(id: string, payload: ModelUpsertPayload): Promise<ModelView> {
    return apiFetch<ModelView>(`${BASE}/models/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },

  async deleteModel(id: string): Promise<void> {
    await apiFetch<void>(`${BASE}/models/${id}`, { method: 'DELETE' });
  },

  async setDefaultModel(id: string): Promise<ModelView> {
    return apiFetch<ModelView>(`${BASE}/models/${id}/default`, { method: 'POST' });
  },
};
