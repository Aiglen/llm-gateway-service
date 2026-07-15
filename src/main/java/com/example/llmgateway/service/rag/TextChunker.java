package com.example.llmgateway.service.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分工具：把一段长文本切成若干个用于向量化的 chunk。
 * <p>
 * 策略：按目标字符数（chunkSize）做滑动窗口切分，相邻 chunk 保留一定重叠（overlap），
 * 避免语义在切分边界被截断；切分点会尽量往后找最近的换行/句末标点对齐，
 * 减少把一句话硬生生切成两半的情况。这是一个简化实现，
 * 生产场景可以按 Markdown 标题层级 / 语义段落做更细致的切分。
 */
public final class TextChunker {

    private TextChunker() {
    }

    public static List<String> chunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").trim();
        int size = Math.max(chunkSize, 100);
        int step = Math.max(size - Math.max(overlap, 0), 50);

        int start = 0;
        int length = normalized.length();
        while (start < length) {
            int end = Math.min(start + size, length);
            if (end < length) {
                end = snapToBoundary(normalized, start, end);
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= length) {
                break;
            }
            start = Math.max(end - overlap, start + step);
        }
        return chunks;
    }

    /** 在 [start, roughEnd] 附近向后寻找最近的换行或句末标点，让切分尽量落在语义边界上 */
    private static int snapToBoundary(String text, int start, int roughEnd) {
        int window = Math.min(80, roughEnd - start);
        for (int i = roughEnd; i > roughEnd - window && i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '\n' || c == '。' || c == '.' || c == '!' || c == '！' || c == '?' || c == '？') {
                return i;
            }
        }
        return roughEnd;
    }
}
