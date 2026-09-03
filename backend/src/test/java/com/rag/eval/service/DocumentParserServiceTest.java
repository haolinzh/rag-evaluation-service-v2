package com.rag.eval.service;

import com.rag.eval.model.ChunkConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserServiceTest {

    private final DocumentParserService parser =
        new DocumentParserService(true, "chi_sim+eng", 300, 120);

    private static ChunkData byContent(List<ChunkData> chunks, String content) {
        return chunks.stream()
            .filter(c -> c.getContent().equals(content))
            .findFirst()
            .orElseThrow(() -> new AssertionError("chunk not found: " + content));
    }

    @Test
    void chapterPropagatesAcrossChunks() {
        String text = String.join("----",
            "第一章 概述",
            "RAG 是检索增强生成。",
            "第二章 方法",
            "第一节 向量检索",
            "它通过 embedding 计算相似度。",
            "第二节 重排",
            "对候选进行精排。");
        ChunkConfig config = new ChunkConfig(ChunkConfig.MODE_DELIMITER, 0, "----", 0);

        List<ChunkData> chunks = parser.splitAndEnrich(text, "doc.txt", "digital", config);

        // 章节标题所在 chunk 本身命中
        assertEquals("第一章 概述", byContent(chunks, "第一章 概述").getChapter());
        assertNull(byContent(chunks, "第一章 概述").getSection());

        // 章节传播到标题之后的普通正文 chunk（修复前为 null）
        assertEquals("第一章 概述", byContent(chunks, "RAG 是检索增强生成。").getChapter());
        assertNull(byContent(chunks, "RAG 是检索增强生成。").getSection());

        // 换章后，章节 + 节都传播到后续 chunk
        assertEquals("第二章 方法", byContent(chunks, "它通过 embedding 计算相似度。").getChapter());
        assertEquals("第一节 向量检索", byContent(chunks, "它通过 embedding 计算相似度。").getSection());

        // 换节后，节更新但章保持
        assertEquals("第二章 方法", byContent(chunks, "对候选进行精排。").getChapter());
        assertEquals("第二节 重排", byContent(chunks, "对候选进行精排。").getSection());
    }

    @Test
    void chapterTitleMidChunk_detectedViaMultiline() {
        // 单 chunk 内换行，章节标题不在 chunk 开头；MULTILINE 应命中行首
        String text = "前文铺垫。\n第三章 结论\n后续说明。";
        ChunkConfig config = new ChunkConfig(ChunkConfig.MODE_DELIMITER, 0, "===", 0);

        List<ChunkData> chunks = parser.splitAndEnrich(text, "doc.txt", "digital", config);

        assertEquals(1, chunks.size());
        assertEquals("第三章 结论", chunks.get(0).getChapter());
    }

    @Test
    void sectionTitleMidChunk_detectedViaMultiline() {
        String text = "正文内容。\n第二节 讨论\n更多内容。";
        ChunkConfig config = new ChunkConfig(ChunkConfig.MODE_DELIMITER, 0, "===", 0);

        List<ChunkData> chunks = parser.splitAndEnrich(text, "doc.txt", "digital", config);

        assertEquals(1, chunks.size());
        assertNull(chunks.get(0).getChapter());
        assertEquals("第二节 讨论", chunks.get(0).getSection());
    }

    @Test
    void arabicNumeralChapter_detected() {
        String text = String.join("----",
            "第1章 引言",
            "这是正文。",
            "第2章 并发模型",
            "第3章 线程与锁",
            "线程是基本单元。");
        ChunkConfig config = new ChunkConfig(ChunkConfig.MODE_DELIMITER, 0, "----", 0);

        List<ChunkData> chunks = parser.splitAndEnrich(text, "doc.txt", "digital", config);

        assertEquals("第1章 引言", byContent(chunks, "这是正文。").getChapter());
        assertEquals("第3章 线程与锁", byContent(chunks, "线程是基本单元。").getChapter());
    }

    @Test
    void bodyReferenceToChapter_notTreatedAsHeading() {
        // 正文中「第2章 提到的…」这类引用是长句/含标点，不应被误判为章节标题
        String text = String.join("----",
            "第2章 提到的有关锁的一些规则，都是针对于线程之间共享的可变的数据——换个说法就是",
            "这是普通正文。");
        ChunkConfig config = new ChunkConfig(ChunkConfig.MODE_DELIMITER, 0, "----", 0);

        List<ChunkData> chunks = parser.splitAndEnrich(text, "doc.txt", "digital", config);

        assertNull(byContent(chunks, "这是普通正文。").getChapter());
    }
}
