import { API_BASE_URL } from './api';
import type { RagCitation, RagCompletionPayload, RagStreamError } from '@/types/knowledge';

/**
 * RAG 流式问答服务 —— SSE 解析与 UI 完全解耦（ChatView 只消费回调，不关心 SSE 协议细节）。
 *
 * 端点：POST /api/v1/chat/stream（text/event-stream，data 均为 JSON）
 * 事件契约：
 *   phase     {"phase":"searching"|"generating"}
 *   citation  RagCitation（先于 token 到达，流式期间即可渲染可点击来源）
 *   message   {"content":"增量 token"}
 *   done      RagCompletionPayload（终态成功）
 *   error     RagStreamError（终态失败；HTTP 层错误如 400 在流开始前以 JSON 信封返回）
 */

export interface RagStreamCallbacks {
  onPhase: (phase: 'searching' | 'generating') => void;
  onCitation: (citation: RagCitation) => void;
  onToken: (content: string) => void;
  onDone: (payload: RagCompletionPayload) => void;
  onError: (error: RagStreamError) => void;
}

/** SSE 帧解析：按空行分帧，帧内 event/data 行；data 多行按 SSE 规范以 \n 拼接。 */
function parseSseFrame(frame: string): { event: string; data: string } | null {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''));
    }
    // 忽略 id:/retry:/注释行 —— 当前契约未使用
  }
  if (!dataLines.length) return null;
  return { event, data: dataLines.join('\n') };
}

/**
 * 发起 RAG 流式问答。resolve 时表示流已正常走完（done 或 error 事件均已回调）。
 * fetch 抛出 AbortError 表示用户主动停止（调用方据此区分 cancelled 与 error）。
 */
export async function streamRagAnswer(
  query: string,
  topK: number | undefined,
  callbacks: RagStreamCallbacks,
  signal: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ message: query, topK }),
    signal,
  });

  // 流开始前的 HTTP 错误（校验失败 400 / Vault 未连接 404 / 未预期 500）：统一信封
  if (!response.ok || !response.body) {
    let code = 'INVALID_REQUEST';
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const body = (await response.json()) as { code?: string; message?: string };
      if (body.code) code = body.code;
      if (body.message) message = body.message;
    } catch {
      // 非 JSON 错误体：保留默认提示
    }
    callbacks.onError({ code, message });
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const dispatch = (frame: string): void => {
    const parsed = parseSseFrame(frame);
    if (!parsed) return;
    try {
      const payload = JSON.parse(parsed.data);
      switch (parsed.event) {
        case 'phase':
          callbacks.onPhase(payload.phase as 'searching' | 'generating');
          break;
        case 'citation':
          callbacks.onCitation(payload as RagCitation);
          break;
        case 'message':
          callbacks.onToken(payload.content as string);
          break;
        case 'done':
          callbacks.onDone(payload as RagCompletionPayload);
          break;
        case 'error':
          callbacks.onError(payload as RagStreamError);
          break;
        default:
          // 未知事件：向前兼容，忽略
          break;
      }
    } catch {
      // 单帧 JSON 损坏：跳过该帧，不中断整个流
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let separatorIndex = buffer.indexOf('\n\n');
    while (separatorIndex >= 0) {
      const frame = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);
      if (frame.trim()) dispatch(frame);
      separatorIndex = buffer.indexOf('\n\n');
    }
  }
  // 冲刷残帧（服务端未以空行结尾的最后一段）
  if (buffer.trim()) dispatch(buffer);
}
