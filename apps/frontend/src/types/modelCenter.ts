/**
 * AI Model Center 类型（Phase 5.5）。
 * 安全契约：所有类型均不含 API Key 原文——只有 configured / masked / source。
 */

export type ProviderType = 'OLLAMA' | 'DEEPSEEK' | 'DASHSCOPE' | 'OPENAI_COMPATIBLE';

export type CredentialSource = 'USER_CONFIGURED' | 'ENVIRONMENT' | 'NONE';

export interface ProviderView {
  id: string;
  name: string;
  type: ProviderType;
  baseUrl: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  apiKeyMasked: string | null;
  credentialSource: CredentialSource;
  createdAt: string;
  updatedAt: string;
}

/** Upsert 请求：apiKey 为 null/undefined/空串 = 保留原凭据（编辑 baseUrl 时不清 Key） */
export interface ProviderUpsertPayload {
  name: string;
  type: ProviderType;
  baseUrl: string;
  enabled?: boolean;
  apiKey?: string;
}

export type ModelCapability = 'CHAT' | 'STREAMING' | 'REASONING';

export interface ModelView {
  id: string;
  providerId: string;
  providerName: string;
  modelName: string;
  displayName: string;
  capabilities: ModelCapability[];
  enabled: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ModelUpsertPayload {
  providerId: string;
  modelName: string;
  displayName?: string;
  capabilities?: ModelCapability[];
  enabled?: boolean;
  isDefault?: boolean;
}

export interface TestConnectionResult {
  success: boolean;
  provider: string;
  model: string | null;
  latencyMs: number;
  errorCode: string | null;
  message: string;
}

export interface AvailableModels {
  providerId: string;
  models: string[];
}
