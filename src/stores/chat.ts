import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { AiPhase, ChatMessage } from '@/types/knowledge';
import { aiService } from '@/services/aiService';

/** AI 助手状态：会话、思考阶段、知识范围、模型选择 */
export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([]);
  const phase = ref<AiPhase>('idle');
  const scope = ref<string>('vault');
  const model = ref<string>('Qwen3');
  const inputDraft = ref('');

  const isThinking = (): boolean => phase.value === 'searching' || phase.value === 'generating';

  async function ask(question: string): Promise<void> {
    if (!question.trim() || isThinking()) return;

    messages.value.push({
      id: `msg-u-${Date.now()}`,
      role: 'user',
      content: question.trim(),
      sources: [],
      relatedNotes: [],
      createdAt: new Date().toISOString(),
    });

    await aiService.ask(question, scope.value, model.value, {
      onPhase: (p) => {
        phase.value = p;
      },
      onMessage: (msg) => {
        messages.value.push(msg);
      },
    });
  }

  function clearConversation(): void {
    messages.value = [];
    phase.value = 'idle';
  }

  return { messages, phase, scope, model, inputDraft, isThinking, ask, clearConversation };
});
