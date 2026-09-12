/**
 * Markdown 解析：Frontmatter / Tags / Wiki Links。
 * 只做读取与解析，绝不修改用户原始 Frontmatter。
 */

export interface ParsedMarkdown {
  frontmatter: Record<string, string>;
  /** frontmatter tags + 正文内联 #tag 合并去重 */
  tags: string[];
  /** 去掉 frontmatter 后的正文 */
  body: string;
  /** [[Wiki Link]] 原始目标（不含别名），按出现顺序去重 */
  wikiLinks: string[];
}

/** 解析 YAML frontmatter（支持 key: value、- item 列表、[a, b] 行内列表） */
function parseFrontmatter(raw: string): Record<string, string> {
  const result: Record<string, string> = {};
  let currentKey = '';
  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const listItem = trimmed.startsWith('- ');
    const kv = !listItem && trimmed.match(/^([A-Za-z_\u4e00-\u9fa5][\w\u4e00-\u9fa5-]*)\s*:\s*(.*)$/);
    if (kv) {
      currentKey = kv[1];
      let value = kv[2].trim().replace(/^["']|["']$/g, '');
      // 行内数组 [a, b, c]
      const inlineArr = value.match(/^\[(.*)\]$/);
      if (inlineArr) {
        value = inlineArr[1]
          .split(',')
          .map((s) => s.trim().replace(/^["']|["']$/g, ''))
          .filter(Boolean)
          .join(', ');
      }
      if (value) result[currentKey] = value;
    } else if (listItem && currentKey) {
      const item = trimmed.slice(2).trim().replace(/^["']|["']$/g, '');
      result[currentKey] = result[currentKey] ? `${result[currentKey]}, ${item}` : item;
    }
  }
  return result;
}

/** 从 frontmatter 值里拆 tag 列表 */
function splitTags(value: string | undefined): string[] {
  if (!value) return [];
  return value
    .split(',')
    .map((s) => s.trim().replace(/^#/, ''))
    .filter(Boolean);
}

/** 提取正文内联标签 #tag（排除标题行、代码块、[[链接]] 内部） */
function extractInlineTags(body: string): string[] {
  const tags = new Set<string>();
  let inCode = false;
  for (const line of body.split('\n')) {
    if (line.trim().startsWith('```')) {
      inCode = !inCode;
      continue;
    }
    if (inCode || line.trim().startsWith('#')) continue;
    const matches = line.matchAll(/(?:^|[\s(（【])#([\w\u4e00-\u9fa5][\w\u4e00-\u9fa5/-]*)/g);
    for (const m of matches) tags.add(m[1]);
  }
  return [...tags];
}

/** 提取 [[Wiki Link]]（支持 [[Note]] 与 [[Note|别名]]，不含嵌入 ![[...]]） */
function extractWikiLinks(body: string): string[] {
  const links = new Set<string>();
  const matches = body.matchAll(/(?<!!)\[\[([^\]|#]+)(?:\|[^\]]*)?\]\]/g);
  for (const m of matches) {
    const target = m[1].trim();
    if (target) links.add(target);
  }
  return [...links];
}

export function parseMarkdown(content: string): ParsedMarkdown {
  let frontmatter: Record<string, string> = {};
  let body = content;

  const fmMatch = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (fmMatch) {
    frontmatter = parseFrontmatter(fmMatch[1]);
    body = content.slice(fmMatch[0].length);
  }

  const fmTags = splitTags(frontmatter.tags);
  const inlineTags = extractInlineTags(body);
  const tags = [...new Set([...fmTags, ...inlineTags])];

  return { frontmatter, tags, body, wikiLinks: extractWikiLinks(body) };
}

/** 从 frontmatter.title / 正文首个 # 标题 / 文件名推断笔记标题 */
export function resolveTitle(fileName: string, parsed: ParsedMarkdown): string {
  if (parsed.frontmatter.title) return parsed.frontmatter.title;
  const base = fileName.replace(/\.md$/i, '');
  const h1 = parsed.body.match(/^#\s+(.+)$/m);
  // 标题与文件名相同就不重复显示
  if (h1 && h1[1].trim() !== base) return h1[1].trim();
  return base;
}
