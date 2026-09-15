package com.obsidianmind.service;

import com.obsidianmind.config.AiProperties;
import com.obsidianmind.domain.Chunk;
import com.obsidianmind.domain.Document;
import com.obsidianmind.exception.ConfigurationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 切块器（Chunker v2）：Document → List&lt;Chunk&gt;。纯函数式组件：不碰文件系统与网络。
 *
 * 策略（边界优先级）：heading &gt; 代码块围栏 &gt; 表格 &gt; 段落 &gt; 行。
 * <ul>
 *   <li>按标题层级切分，每个 Chunk 携带完整 headingPath（如 "JVM / 内存区域 / Heap"）；</li>
 *   <li>```/~~~ 围栏内的代码块与 Markdown 表格永不从中间切开（超限时整块独立成 Chunk，见 Known Limitations）；</li>
 *   <li>段落聚合到 chunk.size 字符上限，超长段落按行二次切分；相邻 Chunk 间保留 chunk.overlap 字符的行级重叠（仅普通段落行参与重叠）；</li>
 *   <li>startOffset/endOffset 为 Chunk 在正文（content）中的字符区间，满足 content.substring(start, end) == chunk.content；</li>
 *   <li>空正文产生 0 个 Chunk（正常状态，不是错误）。</li>
 * </ul>
 * 参数来源 ai.chunk.size / ai.chunk.overlap（构造期校验，非法配置快速失败）。
 */
@Component
public class Chunker {

    private final int maxChars;
    private final int overlap;

    public Chunker(AiProperties aiProperties) {
        AiProperties.Chunk chunk = aiProperties.chunkOrDefault();
        if (chunk.size() <= 0) {
            throw new ConfigurationException("ai.chunk.size 必须为正数，当前: " + chunk.size());
        }
        if (chunk.overlap() < 0 || chunk.overlap() >= chunk.size()) {
            throw new ConfigurationException("ai.chunk.overlap 必须满足 0 <= overlap < size，当前: "
                    + chunk.overlap() + "/" + chunk.size());
        }
        this.maxChars = chunk.size();
        this.overlap = chunk.overlap();
    }

    /**
     * 切块主流程。
     *
     * @param document 已解析文档（content 为正文、contentHash 已计算、id 为 Vault 相对路径）
     * @return 有序 Chunk 列表（chunkIndex 从 0 连续递增）；正文为空时返回空列表
     */
    public List<Chunk> chunk(Document document) {
        String body = document.content() == null ? "" : document.content();
        List<Chunk> chunks = new ArrayList<>();
        if (body.isBlank()) {
            return chunks;
        }
        List<Unit> units = splitIntoUnits(body);
        LineTable table = new LineTable(body);
        HeadingStack stack = new HeadingStack();

        int chunkIndex = 0;
        List<Unit> buffer = new ArrayList<>();
        int bufferLen = 0;
        String bufferHeading = "";
        Unit overlapUnit = null; // 上一 Chunk 尾部待回借的行区间

        for (Unit unit : units) {
            if (unit.type() == UnitType.HEADING) {
                // 标题强制开新 Chunk：冲刷缓冲（并计算可回借的重叠行）
                chunkIndex = flush(chunks, document, buffer, bufferHeading, table, chunkIndex);
                overlapUnit = chunks.isEmpty() ? null : tailOverlapUnit(chunks.get(chunks.size() - 1), table);
                stack.push(unit.level(), unit.text());
                buffer = new ArrayList<>();
                bufferLen = 0;
                bufferHeading = stack.path();
                buffer.add(unit);
                bufferLen += table.charLength(unit);
                continue;
            }
            if (buffer.isEmpty() && overlapUnit != null) {
                // 新缓冲以重叠行开头（本 Chunk 语义上延续上一 Chunk 的段落尾部）
                buffer.add(overlapUnit);
                bufferLen += table.charLength(overlapUnit);
                overlapUnit = null;
            }
            if (bufferLen > 0 && bufferLen + table.charLength(unit) > maxChars) {
                chunkIndex = flush(chunks, document, buffer, bufferHeading, table, chunkIndex);
                overlapUnit = chunks.isEmpty() ? null : tailOverlapUnit(chunks.get(chunks.size() - 1), table);
                buffer = new ArrayList<>();
                bufferLen = 0;
                bufferHeading = stack.path();
            }
            if (bufferLen + table.charLength(unit) > maxChars && bufferLen == 0) {
                // 单个单元就超限：段落按行切分；代码块/表格整块独立成 Chunk（不从中间切断）
                if (unit.type() == UnitType.PARAGRAPH) {
                    for (Unit piece : splitParagraphByLines(unit, table)) {
                        chunkIndex = flush(chunks, document, List.of(piece), stack.path(), table, chunkIndex);
                    }
                    overlapUnit = tailOverlapUnit(chunks.get(chunks.size() - 1), table);
                } else {
                    chunkIndex = flush(chunks, document, List.of(unit), stack.path(), table, chunkIndex);
                    overlapUnit = null; // 代码块/表格尾部不参与重叠
                }
                buffer = new ArrayList<>();
                bufferLen = 0;
                bufferHeading = stack.path();
                continue;
            }
            buffer.add(unit);
            bufferLen += table.charLength(unit);
        }
        flush(chunks, document, buffer, bufferHeading, table, chunkIndex);
        return chunks;
    }

    /** 把缓冲写为一个 Chunk；缓冲为空时无操作。返回新的 chunkIndex。 */
    private int flush(List<Chunk> chunks, Document document, List<Unit> buffer, String headingPath,
                      LineTable table, int chunkIndex) {
        if (buffer.isEmpty()) {
            return chunkIndex;
        }
        Unit first = buffer.get(0);
        Unit last = buffer.get(buffer.size() - 1);
        int startOffset = table.startOffset(first);
        int endOffset = table.endOffset(last);
        chunks.add(new Chunk(
                document.id() + "#c" + chunkIndex,
                document.vaultId(),
                document.id(),
                document.relativePath(),
                document.title(),
                headingPath,
                chunkIndex,
                startOffset,
                endOffset,
                document.content().substring(startOffset, endOffset),
                document.contentHash()));
        return chunkIndex + 1;
    }

    /** 计算上一 Chunk 尾部可回借的行区间（仅当其末单元是普通段落；重叠字符数 ≤ overlap，至少 0 行）。 */
    private Unit tailOverlapUnit(Chunk prev, LineTable table) {
        if (overlap == 0) {
            return null;
        }
        // 反查上一 Chunk 的行区间：由其 startOffset/endOffset 映射回行号
        int lastLine = table.lineOfOffset(prev.endOffset() - 1);
        int firstLine = table.lineOfOffset(prev.startOffset());
        // 从末行向前回溯，跳过尾随空行，累计 ≤ overlap 字符的普通行
        int taken = 0;
        int used = 0;
        int line = lastLine;
        while (line >= firstLine) {
            String text = table.line(line);
            if (text.isBlank()) {
                if (taken == 0) {
                    line--;
                    continue; // 尾随空行不计入重叠
                }
                break; // 段落中间的空行 = 段落边界，停止
            }
            if (text.strip().startsWith("```") || text.strip().startsWith("~~~")
                    || text.strip().startsWith("#") || text.strip().startsWith("|")) {
                break; // 围栏/表格/标题行不参与重叠
            }
            if (used + text.length() + 1 > overlap) {
                break;
            }
            used += text.length() + 1;
            taken++;
            line--;
        }
        if (taken == 0) {
            return null;
        }
        return new Unit(UnitType.PARAGRAPH, line + 1, lastLine + 1, 0, "");
    }

    /**
     * 把正文按行切分为逻辑单元（行区间连续覆盖全文）：HEADING / CODE / TABLE / PARAGRAPH。
     * 段落间与单元间的空行并入相邻单元区间，保证任意 Chunk 的 substring 截取完整无误。
     */
    private List<Unit> splitIntoUnits(String body) {
        String[] lines = body.split("\n", -1);
        List<Unit> units = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        int pendingStart = -1;
        int gapStart = -1; // 尚未被任何单元吸收的空行起点
        int i = 0;
        while (i < lines.length) {
            String trimmed = lines[i].strip();
            boolean fence = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            boolean tableLine = trimmed.startsWith("|");
            boolean heading = trimmed.matches("^#{1,6}\\s+.*");
            if (!fence && !tableLine && !heading && !trimmed.isEmpty()) {
                if (pendingStart < 0) {
                    pendingStart = gapStart >= 0 ? gapStart : i; // 吸收前导空行
                    gapStart = -1;
                }
                pending.add(lines[i]);
                i++;
                continue;
            }
            // 冲刷当前段落
            if (pendingStart >= 0) {
                units.add(new Unit(UnitType.PARAGRAPH, pendingStart, i, 0, ""));
                pending.clear();
                pendingStart = -1;
            }
            if (trimmed.isEmpty()) {
                if (gapStart < 0) {
                    gapStart = i;
                }
                i++;
                continue;
            }
            int unitStart = gapStart >= 0 ? gapStart : i;
            gapStart = -1;
            if (fence) {
                String mark = trimmed.substring(0, 3);
                int end = i + 1;
                while (end < lines.length && !lines[end].strip().startsWith(mark)) {
                    end++;
                }
                end = Math.min(end + 1, lines.length); // 含收尾围栏行；缺失收尾则到文末
                units.add(new Unit(UnitType.CODE, unitStart, end, 0, ""));
                i = end;
            } else if (tableLine) {
                int end = i;
                while (end < lines.length && lines[end].strip().startsWith("|")) {
                    end++;
                }
                units.add(new Unit(UnitType.TABLE, unitStart, end, 0, ""));
                i = end;
            } else {
                int level = trimmed.length() - trimmed.replaceFirst("^#+.*", "#").length();
                String text = trimmed.replaceFirst("^#+\\s+", "").trim();
                units.add(new Unit(UnitType.HEADING, unitStart, i + 1, level, text));
                i++;
            }
        }
        // 冲刷文末段落；纯空行结尾并入最后一个单元
        if (pendingStart >= 0) {
            units.add(new Unit(UnitType.PARAGRAPH, pendingStart, lines.length, 0, ""));
        } else if (gapStart >= 0 && !units.isEmpty()) {
            Unit last = units.remove(units.size() - 1);
            units.add(new Unit(last.type(), last.startLine(), lines.length, last.level(), last.text()));
        }
        return units;
    }

    /** 超长段落按行切分为若干 ≤ maxChars 的单元（保持行连续）。 */
    private List<Unit> splitParagraphByLines(Unit unit, LineTable table) {
        List<Unit> parts = new ArrayList<>();
        int start = unit.startLine();
        int len = 0;
        for (int i = unit.startLine(); i < unit.endLine(); i++) {
            int lineLen = table.line(i).length() + 1;
            if (len > 0 && len + lineLen > maxChars) {
                parts.add(new Unit(UnitType.PARAGRAPH, start, i, 0, ""));
                start = i;
                len = 0;
            }
            len += lineLen;
        }
        if (len > 0) {
            parts.add(new Unit(UnitType.PARAGRAPH, start, unit.endLine(), 0, ""));
        }
        return parts;
    }

    /** 逻辑单元：行区间 [startLine, endLine)。HEADING 额外携带层级与标题文本。 */
    private enum UnitType { HEADING, CODE, TABLE, PARAGRAPH }

    private record Unit(UnitType type, int startLine, int endLine, int level, String text) {
    }

    /** 正文行表：行文本与字符偏移的映射（split("\n", -1) 与偏移严格对应，含末尾换行语义）。 */
    private static final class LineTable {
        private final String[] lines;
        private final int[] startOffsets;

        LineTable(String body) {
            this.lines = body.split("\n", -1);
            this.startOffsets = new int[lines.length];
            int off = 0;
            for (int i = 0; i < lines.length; i++) {
                startOffsets[i] = off;
                off += lines[i].length() + 1; // +1 换行符
            }
        }

        String line(int index) {
            return lines[index];
        }

        int startOffset(Unit unit) {
            return startOffsets[unit.startLine()];
        }

        int endOffset(Unit unit) {
            int last = unit.endLine() - 1;
            return startOffsets[last] + lines[last].length();
        }

        int charLength(Unit unit) {
            return endOffset(unit) - startOffset(unit);
        }

        /** 字符偏移 → 行号（二分）。 */
        int lineOfOffset(int offset) {
            int lo = 0;
            int hi = lines.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (startOffsets[mid] <= offset) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            return lo;
        }
    }

    /** 标题层级栈：维护 "A / B / C" 形式的 headingPath（按出现顺序拼接）。 */
    private static final class HeadingStack {
        private final List<Integer> levels = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();

        void push(int level, String text) {
            while (!levels.isEmpty() && levels.get(levels.size() - 1) >= level) {
                levels.remove(levels.size() - 1);
                texts.remove(texts.size() - 1);
            }
            levels.add(level);
            texts.add(text);
        }

        String path() {
            return String.join(" / ", texts);
        }
    }
}
