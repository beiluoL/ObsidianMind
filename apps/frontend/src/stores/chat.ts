import { defineStore } from 'pinia';
import { reactive, ref } from 'vue';
import type { AiPhase, ChatMessage } from '@/types/knowledge';
import { streamRagAnswer } from '@/services/ragChatService';

/**
 * AI 助手状态（Phase 5 RAG）：单轮流式问答 + 每条回答独立的 Sources + 取消生成。
 * 状态区分：消息状态存于 message.status（streaming/complete/error/cancelled），
 * 禁止用 content === '' 推断状态。
 */
export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([]);
  const phase = ref<AiPhase>('idle');
  const scope = ref<string>('vault');
  const model = ref<string>('Qwen3');
  const inputDraft = ref('');
  let abortController: AbortController | null = null;

  const isThinking = (): boolean => phase.value === 'searching' || phase.value === 'generating';

  async function ask(question: string): Promise<void> {
    const q = question.trim();
    if (!q || isThinking()) return;

    messages.value.push({
      id: `msg-u-${Date.now()}`,
      role: 'user',
      content: q,
      sources: [],
      status: 'complete',
      createdAt: new Date().toISOString(),
    });

    // reactive 包装：push 进 store 后仍通过同一代理追加以保证响应式
    const assistant = reactive<ChatMessage>({
      id: `msg-a-${Date.now()}`,
      role: 'assistant',
      content: '',
      sources: [],
      status: 'streaming',
      createdAt: new Date().toISOString(),
    });
    messages.value.push(assistant);

    phase.value = 'searching';
    abortController = new AbortController();

    try {
      await streamRagAnswer(q, undefined, {
        onPhase: (p) => {
          phase.value = p;
        },
        onCitation: (citation) => {
          assistant.sources.push(citation);
        },
        onToken: (content) => {
          assistant.content += content;
        },
        onDone: (payload) => {
          // 以服务端全文为准（涵盖被停止/丢失的尾帧），citations 以 done.sources 补齐
          if (payload.content) assistant.content = payload.content;
          if (payload.sources.length > assistant.sources.length) {
            assistant.sources = payload.sources;
          }
          assistant.status = 'complete';
          phase.value = 'done';
        },
        onError: (error) => {
          assistant.status = 'error';
          assistant.errorCode = error.code;
          assistant.errorMessage = error.message;
          phase.value = 'error';
        },
      }, abortController.signal);
      // 流正常关闭但服务端未发 done（连接中断）：不得停留在 streaming
      if (assistant.status === 'streaming') {
        assistant.status = 'complete';
        phase.value = 'done';
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        assistant.status = 'cancelled';
        phase.value = 'done';
      } else {
        assistant.status = 'error';
        assistant.errorCode = 'NETWORK_ERROR';
        assistant.errorMessage = '无法连接到后端服务';
        phase.value = 'error';
      }
    } finally {
      abortController = null;
    }
  }

  /** 停止生成：中止 SSE；后端检测断连后取消上游 Ollama 流 */
  function stop(): void {
    abortController?.abort();
  }

  function clearConversation(): void {
    stop();
    messages.value = [];
    phase.value = 'idle';
  }

  return { messages, phase, scope, model, inputDraft, isThinking, ask, stop, clearConversation };
});
