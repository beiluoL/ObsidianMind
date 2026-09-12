import type { AiPhase, ChatMessage } from '@/types/knowledge';
import { MOCK_ANSWERS, MOCK_FALLBACK } from './mock/chat';

/**
 * AI 服务 —— 未来替换为 POST /api/chat (SSE 流式)。
 * Mock 通过分阶段回调模拟「检索知识库 → 生成回答」的真实节奏。
 */

const matchAnswer = (question: string) => {
  const q = question.toLowerCase();
  const hit = MOCK_ANSWERS.find((ans) => ans.keywords.some((kw) => q.includes(kw)));
  return hit ?? MOCK_FALLBACK;
};

export interface ChatCallbacks {
  onPhase: (phase: AiPhase) => void;
  onMessage: (message: ChatMessage) => void;
}

export const aiService = {
  async ask(question: string, _scope: string, _model: string, callbacks: ChatCallbacks): Promise<void> {
    const { onPhase, onMessage } = callbacks;
    onPhase('searching');
    await new Promise((resolve) => setTimeout(resolve, 1100));

    onPhase('generating');
    const ans = matchAnswer(question);
    await new Promise((resolve) => setTimeout(resolve, 900));

    onMessage({
      id: `msg-${Date.now()}`,
      role: 'assistant',
      content: ans.content,
      sources: structuredClone(ans.sources),
      relatedNotes: structuredClone(ans.relatedNotes),
      createdAt: new Date().toISOString(),
    });
    onPhase('done');
  },
};
