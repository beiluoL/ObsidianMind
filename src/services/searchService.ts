import type { KnowledgeGraphData, SearchResult, ServiceHealth } from '@/types/knowledge';
import { knowledgeService } from './knowledgeService';

/**
 * 搜索服务 —— 本地全文检索（标题 / 正文 / Tags / 路径）。
 *
 * Phase 2：Local Full Text Search，内容按需读取 + 缓存。
 * Phase 3+：替换为 POST /api/search（向量检索 + 混合检索 + Rerank）。
 */

const STOPWORDS = ['怎么', '什么', '如何', '工作', '区别', '哪些', '关于', '的', '是', '和', '有', '吗', '呢'];

/** 并发受控的内容读取 */
async function mapLimit<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await fn(items[index]);
    }
  });
  await Promise.all(workers);
  return results;
}

export const searchService = {
  async search(query: string, scope: string, currentFolder = ''): Promise<SearchResult[]> {
    const q = query.trim().toLowerCase();
    if (!q) return [];

    const keywords = q.split(/\s+/).filter((kw) => kw.length >= 2);
    const meaningful = keywords.filter((kw) => !STOPWORDS.includes(kw));
    const terms = meaningful.length ? meaningful : keywords;

    const notes = await knowledgeService.getAllNotes();
    const isReal = knowledgeService.isVaultConnected();

    // 真实模式：并发读取正文（repository 内部有缓存）
    const contents = new Map<string, string>();
    if (isReal) {
      const fullNotes = await mapLimit(notes, 8, async (note) => ({
        id: note.id,
        content: (await knowledgeService.getNoteById(note.id))?.content ?? '',
      }));
      fullNotes.forEach((n) => contents.set(n.id, n.content));
    }

    const results: SearchResult[] = [];
    for (const note of notes) {
      if (scope === 'folder' && currentFolder && note.folder !== currentFolder) continue;

      const content = isReal ? contents.get(note.id) ?? '' : note.content;
      const lowerContent = content.toLowerCase();

      const titleHit = terms.some((kw) => note.title.toLowerCase().includes(kw));
      const tagHit = note.tags.find((tag) => terms.some((kw) => tag.toLowerCase().includes(kw)));
      const pathHit = terms.some((kw) => note.path.toLowerCase().includes(kw));
      const bodyHits = terms.filter((kw) => lowerContent.includes(kw));

      let score = 0;
      let reason = '';
      if (titleHit) {
        score = 0.96;
        reason = '标题匹配';
      } else if (tagHit) {
        score = 0.92;
        reason = `标签「${tagHit}」匹配`;
      } else if (pathHit) {
        score = 0.86;
        reason = '路径匹配';
      } else if (bodyHits.length) {
        score = 0.78 + Math.min(0.14, bodyHits.length * 0.05);
        reason = `正文命中 ${bodyHits.length} 个关键词`;
      } else {
        continue;
      }

      // 摘要：优先取含命中词的句子
      const firstHitTerm = bodyHits[0] ?? terms[0];
      const hitIndex = lowerContent.indexOf(firstHitTerm);
      let excerpt: string;
      if (hitIndex >= 0) {
        excerpt = content
          .slice(Math.max(0, hitIndex - 40), hitIndex + 90)
          .replace(/\n/g, ' ')
          .replace(/[*`>|#[\]]/g, '')
          .trim();
      } else {
        const firstPara = content
          .split('\n')
          .map((line) => line.trim())
          .find((line) => line.length > 20 && !line.startsWith('#') && !line.startsWith('```'));
        excerpt = firstPara ? firstPara.replace(/[*`>|#[\]]/g, '').slice(0, 120) : '（无摘要）';
      }

      results.push({
        noteId: note.id,
        title: note.title,
        path: note.path,
        excerpt,
        score: Math.max(0.7, Math.min(0.99, score)),
        reason,
        tags: note.tags.slice(0, 4),
        modifiedAt: note.updatedAt,
        matchTerms: terms.filter((kw) => lowerContent.includes(kw) || note.title.toLowerCase().includes(kw)),
      });
    }

    return results.sort((x, y) => y.score - x.score).slice(0, 12);
  },

  async getGraph(): Promise<KnowledgeGraphData> {
    return knowledgeService.getGraph();
  },

  async getHealth(): Promise<ServiceHealth> {
    if (knowledgeService.isVaultConnected()) {
      const notes = await knowledgeService.getAllNotes();
      return {
        ollama: 'disconnected',
        milvus: 'disconnected',
        embedding: 'disconnected',
        noteCount: notes.length,
        chunkCount: 0,
        connectionCount: 0,
        lastIndexedAt: '尚未索引',
      };
    }
    return {
      ollama: 'connected',
      milvus: 'connected',
      embedding: 'ready',
      noteCount: 21,
      chunkCount: 32581,
      connectionCount: 18492,
      lastIndexedAt: '2 分钟前',
    };
  },
};
