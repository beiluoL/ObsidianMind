<script setup lang="ts">
/**
 * AI Model Center（Phase 5.5）：左侧 Provider 列表，右侧 Provider 详情 + Model 卡片。
 * 安全：API Key 永远只显示 masked 值；输入框只在用户显式修改时发送；编辑留空 = 保留原 Key。
 * 主题：全部 Design Tokens，无硬编码颜色（34 条 UI 铁律）。
 */
import { computed, onMounted, ref } from 'vue';
import {
  Bot, Check, ChevronRight, Cloud, Cpu, Loader2, Pencil,
  PlugZap, Plus, ShieldCheck, Star, Trash2, TriangleAlert,
} from 'lucide-vue-next';
import type { ProviderType, ProviderView, ModelView, ModelCapability } from '@/types/modelCenter';
import { aiModelsService } from '@/services/aiModelsService';

const providers = ref<ProviderView[]>([]);
const models = ref<ModelView[]>([]);
const loading = ref(true);
const loadError = ref('');
const selectedId = ref<string>('');

// 表单状态（编辑当前选中 Provider）
const formName = ref('');
const formBaseUrl = ref('');
const formApiKey = ref(''); // 空 = 保留原凭据
const formEnabled = ref(true);
const saving = ref(false);
const formError = ref('');
const saveSuccess = ref(false);

// 测试连接状态
const testing = ref(false);
const testResult = ref<{ success: boolean; text: string } | null>(null);
const testModelInput = ref('');

// 模型编辑
const showModelForm = ref(false);
const editingModelId = ref<string | null>(null);
const modelForm = ref({ modelName: '', displayName: '', capabilities: ['CHAT', 'STREAMING'] as string[], isDefault: false });
const modelSaving = ref(false);
const modelFormError = ref('');

const CAPTION: Record<ProviderType, string> = {
  OLLAMA: 'Local AI',
  DEEPSEEK: 'DeepSeek 官方 API',
  DASHSCOPE: '阿里云百炼（兼容模式）',
  OPENAI_COMPATIBLE: 'OpenAI 兼容接口',
};

const selected = computed(() => providers.value.find((p) => p.id === selectedId.value) ?? null);
const selectedModels = computed(() => models.value.filter((m) => m.providerId === selectedId.value));

const providerIcon = (p: ProviderView) => (p.type === 'OLLAMA' ? Cpu : Cloud);

function providerStatus(p: ProviderView): { label: string; tone: 'ok' | 'muted' | 'off' } {
  if (!p.enabled) return { label: '已禁用', tone: 'off' };
  if (p.credentialSource === 'USER_CONFIGURED' || p.credentialSource === 'ENVIRONMENT') {
    return { label: p.credentialSource === 'ENVIRONMENT' ? '已连接（环境变量）' : '已连接', tone: 'ok' };
  }
  if (p.type === 'OLLAMA') return { label: '本地服务', tone: 'ok' };
  return { label: '未配置', tone: 'muted' };
}

async function refresh(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    const [ps, ms] = await Promise.all([aiModelsService.listProviders(), aiModelsService.listModels()]);
    providers.value = ps;
    models.value = ms;
    if (!selectedId.value && ps.length) {
      select(ps[0].id);
    } else if (selectedId.value) {
      syncForm();
    }
  } catch (e) {
    loadError.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}

function select(id: string): void {
  selectedId.value = id;
  testResult.value = null;
  modelFormError.value = '';
  formError.value = '';
  syncForm();
}

function syncForm(): void {
  const p = selected.value;
  if (!p) return;
  formName.value = p.name;
  formBaseUrl.value = p.baseUrl;
  formApiKey.value = '';
  formEnabled.value = p.enabled;
}

async function saveProvider(): Promise<void> {
  if (!selected.value || saving.value) return;
  saving.value = true;
  formError.value = '';
  saveSuccess.value = false;
  try {
    const payload = {
      name: formName.value,
      type: selected.value.type,
      baseUrl: formBaseUrl.value,
      enabled: formEnabled.value,
      ...(formApiKey.value.trim() ? { apiKey: formApiKey.value.trim() } : {}),
    };
    const updated = await aiModelsService.updateProvider(selected.value.id, payload);
    const idx = providers.value.findIndex((p) => p.id === updated.id);
    if (idx >= 0) providers.value[idx] = updated;
    formApiKey.value = '';
    saveSuccess.value = true;
    setTimeout(() => (saveSuccess.value = false), 2000);
  } catch (e) {
    formError.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
}

async function testConnection(): Promise<void> {
  if (!selected.value || testing.value) return;
  testing.value = true;
  testResult.value = null;
  try {
    const r = await aiModelsService.testProvider(selected.value.id, testModelInput.value.trim() || undefined);
    testResult.value = {
      success: r.success,
      text: r.success
        ? `✓ 连接成功 · ${r.model ?? selected.value.name} · ${r.latencyMs}ms`
        : `✗ ${r.message}（${r.errorCode}）`,
    };
  } catch (e) {
    testResult.value = { success: false, text: `测试失败：${(e as Error).message}` };
  } finally {
    testing.value = false;
  }
}

async function loadOllamaModels(): Promise<void> {
  if (!selected.value) return;
  try {
    const r = await aiModelsService.availableModels(selected.value.id);
    testResult.value = { success: true, text: `本机已安装 ${r.models.length} 个模型：${r.models.join('、')}` };
  } catch (e) {
    testResult.value = { success: false, text: (e as Error).message };
  }
}

// ---- Model CRUD ----

function startAddModel(): void {
  showModelForm.value = true;
  editingModelId.value = null;
  modelForm.value = { modelName: '', displayName: '', capabilities: ['CHAT', 'STREAMING'], isDefault: false };
  modelFormError.value = '';
}

function startEditModel(m: ModelView): void {
  showModelForm.value = true;
  editingModelId.value = m.id;
  modelForm.value = {
    modelName: m.modelName,
    displayName: m.displayName,
    capabilities: [...m.capabilities],
    isDefault: m.isDefault,
  };
  modelFormError.value = '';
}

function toggleCapability(cap: string): void {
  const caps = modelForm.value.capabilities;
  const i = caps.indexOf(cap);
  if (i >= 0) caps.splice(i, 1);
  else caps.push(cap);
}

async function saveModel(): Promise<void> {
  if (!selected.value || modelSaving.value) return;
  modelSaving.value = true;
  modelFormError.value = '';
  try {
    const payload = {
      providerId: selected.value.id,
      modelName: modelForm.value.modelName,
      displayName: modelForm.value.displayName || undefined,
      capabilities: modelForm.value.capabilities as ModelCapability[],
      isDefault: modelForm.value.isDefault,
    };
    if (editingModelId.value) {
      await aiModelsService.updateModel(editingModelId.value, payload);
    } else {
      await aiModelsService.createModel(payload);
    }
    showModelForm.value = false;
    await refresh();
  } catch (e) {
    modelFormError.value = (e as Error).message;
  } finally {
    modelSaving.value = false;
  }
}

async function setDefault(m: ModelView): Promise<void> {
  await aiModelsService.setDefaultModel(m.id);
  await refresh();
}

async function removeModel(m: ModelView): Promise<void> {
  await aiModelsService.deleteModel(m.id);
  if (editingModelId.value === m.id) showModelForm.value = false;
  await refresh();
}

onMounted(refresh);
</script>

<template>
  <div class="mc">
    <h2 class="mc__title">AI 模型</h2>
    <p class="mc__subtitle">管理本地与云端 AI 模型</p>

    <div v-if="loading" class="mc__loading"><Loader2 :size="16" class="spin" /> 加载中…</div>
    <div v-else-if="loadError" class="mc__error">
      <TriangleAlert :size="14" /> {{ loadError }}
    </div>

    <div v-else class="mc__layout">
      <!-- 左：Provider 列表 -->
      <aside class="mc__list">
        <button
          v-for="p in providers"
          :key="p.id"
          class="mc__item"
          :class="{ 'mc__item--active': p.id === selectedId }"
          @click="select(p.id)"
        >
          <component :is="providerIcon(p)" :size="15" class="mc__item-icon" />
          <span class="mc__item-body">
            <span class="mc__item-name">{{ p.name }}</span>
            <span class="mc__item-desc" :class="`mc__item-desc--${providerStatus(p).tone}`">
              {{ providerStatus(p).label }}
            </span>
          </span>
          <ChevronRight :size="13" class="mc__item-arrow" />
        </button>
      </aside>

      <!-- 右：Provider 详情 -->
      <div v-if="selected" class="mc__detail">
        <div class="mc__detail-head">
          <div>
            <h3 class="mc__detail-title">{{ selected.name }}</h3>
            <p class="mc__detail-type">{{ CAPTION[selected.type] }}</p>
          </div>
          <span v-if="selectedModels.some((m) => m.isDefault)" class="mc__badge mc__badge--primary">
            <Star :size="10" /> 含默认模型
          </span>
        </div>

        <!-- 连接配置 -->
        <div class="mc__field">
          <label class="mc__label">名称</label>
          <input v-model="formName" class="mc__input" />
        </div>
        <div class="mc__field">
          <label class="mc__label">Base URL</label>
          <input v-model="formBaseUrl" class="mc__input" spellcheck="false" />
        </div>
        <div class="mc__field" v-if="selected.type !== 'OLLAMA'">
          <label class="mc__label">API Key</label>
          <div class="mc__key-row">
            <input
              v-model="formApiKey"
              class="mc__input mc__input--key"
              type="password"
              :placeholder="selected.apiKeyConfigured ? `已配置（${selected.apiKeyMasked ?? '••••'}）· 留空保留原 Key` : 'sk-xxxx'"
              autocomplete="off"
              spellcheck="false"
            />
          </div>
          <p class="mc__hint">
            <ShieldCheck :size="11" />
            Key 加密存储于本机，仅用于调用你配置的模型服务；页面与 API 响应只显示遮蔽值。
            <template v-if="selected.credentialSource === 'ENVIRONMENT'">
              当前生效的是环境变量凭据（DASHSCOPE_API_KEY / DEEPSEEK_API_KEY）。
            </template>
          </p>
        </div>
        <div class="mc__field" v-else>
          <label class="mc__label">API Key</label>
          <span class="mc__static">本地服务，无需 API Key</span>
        </div>
        <div class="mc__field">
          <label class="mc__label">启用</label>
          <button class="mc__toggle" :class="{ 'mc__toggle--on': formEnabled }" @click="formEnabled = !formEnabled">
            <span class="mc__toggle-knob"></span>
          </button>
        </div>

        <div class="mc__actions">
          <button class="mc__btn mc__btn--primary" :disabled="saving" @click="saveProvider">
            <Loader2 v-if="saving" :size="13" class="spin" />
            保存
          </button>
          <Check v-if="saveSuccess" :size="14" class="mc__save-ok" />
          <button class="mc__btn mc__btn--ghost" :disabled="testing" @click="testConnection">
            <Loader2 v-if="testing" :size="13" class="spin" />
            <PlugZap v-else :size="13" />
            测试连接
          </button>
          <input
            v-if="selected.type !== 'OLLAMA'"
            v-model="testModelInput"
            class="mc__input mc__input--test"
            placeholder="Model ID（如 deepseek-v4-flash）"
            spellcheck="false"
          />
          <button v-if="selected.type === 'OLLAMA'" class="mc__btn mc__btn--ghost" @click="loadOllamaModels">
            读取本机模型
          </button>
        </div>
        <p v-if="formError" class="mc__form-error"><TriangleAlert :size="12" /> {{ formError }}</p>
        <p v-if="testResult" class="mc__test-result" :class="testResult.success ? 'mc__test-result--ok' : 'mc__test-result--fail'">
          {{ testResult.text }}
        </p>

        <!-- Models -->
        <div class="mc__models-head">
          <h4 class="mc__models-title">Models</h4>
          <button class="mc__btn mc__btn--ghost mc__btn--sm" @click="startAddModel">
            <Plus :size="12" /> 添加模型
          </button>
        </div>

        <!-- 模型表单 -->
        <div v-if="showModelForm" class="mc__model-form">
          <div class="mc__field">
            <label class="mc__label">Model ID</label>
            <input v-model="modelForm.modelName" class="mc__input" placeholder="如 deepseek-v4-flash / qwen3.5:9b" spellcheck="false" />
          </div>
          <div class="mc__field">
            <label class="mc__label">显示名</label>
            <input v-model="modelForm.displayName" class="mc__input" placeholder="可选" />
          </div>
          <div class="mc__field">
            <label class="mc__label">能力</label>
            <div class="mc__caps">
              <button
                v-for="cap in ['CHAT', 'STREAMING', 'REASONING']"
                :key="cap"
                class="mc__cap"
                :class="{ 'mc__cap--on': modelForm.capabilities.includes(cap) }"
                @click="toggleCapability(cap)"
              >{{ cap }}</button>
            </div>
          </div>
          <div class="mc__field">
            <label class="mc__label">设为默认</label>
            <button class="mc__toggle" :class="{ 'mc__toggle--on': modelForm.isDefault }" @click="modelForm.isDefault = !modelForm.isDefault">
              <span class="mc__toggle-knob"></span>
            </button>
          </div>
          <p v-if="modelFormError" class="mc__form-error"><TriangleAlert :size="12" /> {{ modelFormError }}</p>
          <div class="mc__actions">
            <button class="mc__btn mc__btn--primary" :disabled="modelSaving" @click="saveModel">
              <Loader2 v-if="modelSaving" :size="13" class="spin" />
              保存模型
            </button>
            <button class="mc__btn mc__btn--ghost" @click="showModelForm = false">取消</button>
          </div>
        </div>

        <!-- 模型卡片 -->
        <div v-for="m in selectedModels" :key="m.id" class="mc__model-card">
          <div class="mc__model-main">
            <span class="mc__model-name">{{ m.displayName || m.modelName }}</span>
            <span class="mc__model-id">{{ m.modelName }}</span>
            <span class="mc__model-caps">{{ m.capabilities.join(' · ') }}</span>
          </div>
          <div class="mc__model-ops">
            <span v-if="m.isDefault" class="mc__badge mc__badge--primary"><Check :size="10" /> 默认</span>
            <button v-else class="mc__op" title="设为默认" @click="setDefault(m)"><Star :size="12" /></button>
            <button v-if="m.enabled" class="mc__op" title="编辑" @click="startEditModel(m)"><Pencil :size="12" /></button>
            <button class="mc__op mc__op--danger" title="删除" @click="removeModel(m)"><Trash2 :size="12" /></button>
          </div>
        </div>
        <p v-if="!selectedModels.length && !showModelForm" class="mc__empty-models">
          <Bot :size="13" /> 尚未添加模型。添加一个 Model ID 并设为默认后，即可在对话中使用。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mc__title {
  font-size: var(--fs-lg);
  font-weight: 650;
  margin-bottom: var(--sp-1);
}

.mc__subtitle {
  font-size: var(--fs-sm);
  color: var(--text-3);
  margin-bottom: var(--sp-5);
}

.mc__loading,
.mc__error {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.mc__error {
  color: var(--danger);
}

.mc__layout {
  display: flex;
  gap: var(--sp-5);
  align-items: flex-start;
}

/* 左列表 */
.mc__list {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mc__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3);
  border-radius: var(--r-md);
  text-align: left;
  transition: background var(--dur-fast) var(--ease);
}

.mc__item:hover {
  background: var(--surface-hover);
}

.mc__item--active {
  background: var(--primary-muted);
}

.mc__item--active .mc__item-name {
  color: var(--primary);
}

.mc__item-icon {
  color: var(--text-3);
  flex-shrink: 0;
}

.mc__item--active .mc__item-icon {
  color: var(--primary);
}

.mc__item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.mc__item-name {
  font-size: var(--fs-sm);
  font-weight: 550;
  color: var(--text-1);
}

.mc__item-desc {
  font-size: var(--fs-xs);
}

.mc__item-desc--ok {
  color: var(--success);
}

.mc__item-desc--muted {
  color: var(--text-3);
}

.mc__item-desc--off {
  color: var(--text-3);
}

.mc__item-arrow {
  color: var(--text-3);
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease);
}

.mc__item--active .mc__item-arrow {
  opacity: 1;
}

/* 右详情 */
.mc__detail {
  flex: 1;
  min-width: 0;
  padding: var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
}

.mc__detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--sp-4);
}

.mc__detail-title {
  font-size: var(--fs-md);
  font-weight: 650;
}

.mc__detail-type {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-top: 2px;
}

.mc__badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  font-size: var(--fs-xs);
  border-radius: var(--r-full);
}

.mc__badge--primary {
  color: var(--primary);
  background: var(--primary-muted);
}

.mc__field {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
}

.mc__label {
  width: 72px;
  flex-shrink: 0;
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.mc__input {
  flex: 1;
  min-width: 0;
  padding: 6px var(--sp-3);
  font-size: var(--fs-sm);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  color: var(--text-1);
  font-family: inherit;
}

.mc__input--key,
.mc__input--test {
  font-family: var(--font-mono, monospace);
}

.mc__input:focus {
  outline: none;
  border-color: var(--primary-border);
}

.mc__static {
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.mc__key-row {
  flex: 1;
}

.mc__hint {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-1);
  width: 100%;
  margin-left: 84px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: 1.6;
}

.mc__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-top: var(--sp-4);
  flex-wrap: wrap;
}

.mc__btn {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 6px var(--sp-4);
  font-size: var(--fs-sm);
  font-weight: 550;
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.mc__btn--primary {
  color: var(--on-primary);
  background: var(--primary-solid);
}

.mc__btn--primary:hover:not(:disabled) {
  background: var(--primary-solid-hover);
}

.mc__btn--ghost {
  color: var(--text-2);
  background: transparent;
  border: 1px solid var(--border-strong);
}

.mc__btn--ghost:hover:not(:disabled) {
  color: var(--text-1);
  background: var(--surface-hover);
}

.mc__btn--sm {
  padding: 3px var(--sp-3);
  font-size: var(--fs-xs);
}

.mc__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mc__save-ok {
  color: var(--success);
}

.mc__form-error {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--danger);
}

.mc__test-result {
  margin-top: var(--sp-3);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-xs);
  border-radius: var(--r-md);
  line-height: 1.6;
}

.mc__test-result--ok {
  color: var(--success);
  background: var(--success-soft);
}

.mc__test-result--fail {
  color: var(--danger);
  background: var(--danger-soft);
}

/* Models */
.mc__models-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-6);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border);
}

.mc__models-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-2);
}

.mc__model-form {
  margin-top: var(--sp-3);
  padding: var(--sp-4);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
}

.mc__caps {
  display: flex;
  gap: var(--sp-2);
}

.mc__cap {
  padding: 3px var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  transition: all var(--dur-fast) var(--ease);
}

.mc__cap--on {
  color: var(--primary);
  border-color: var(--primary-border);
  background: var(--primary-muted);
}

.mc__model-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
}

.mc__model-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.mc__model-name {
  font-size: var(--fs-sm);
  font-weight: 550;
  color: var(--text-1);
}

.mc__model-id {
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-family: var(--font-mono, monospace);
}

.mc__model-caps {
  font-size: var(--fs-xs);
  color: var(--text-3);
  margin-top: 2px;
}

.mc__model-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-shrink: 0;
}

.mc__op {
  padding: 4px;
  color: var(--text-3);
  border-radius: var(--r-sm);
  transition: all var(--dur-fast) var(--ease);
}

.mc__op:hover {
  color: var(--text-1);
  background: var(--surface-hover);
}

.mc__op--danger:hover {
  color: var(--danger);
}

.mc__empty-models {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--text-3);
}

/* Toggle */
.mc__toggle {
  width: 36px;
  height: 20px;
  background: var(--surface-active);
  border: 1px solid var(--border-strong);
  border-radius: var(--r-full);
  position: relative;
  transition: all var(--dur) var(--ease);
}

.mc__toggle--on {
  background: var(--primary);
  border-color: var(--primary);
}

.mc__toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  background: var(--knob);
  border-radius: var(--r-full);
  transition: transform var(--dur) var(--ease);
}

.mc__toggle--on .mc__toggle-knob {
  transform: translateX(16px);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
