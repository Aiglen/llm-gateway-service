package com.example.llmgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm.rag")
public class RagProperties {
    /** 向量库类型：milvus / pinecone / chroma */
    private String vectorStore = "milvus";
    private String host = "localhost";
    private int port = 19530;
    /** 集合/索引名 */
    private String collectionName = "knowledge_base";
    /** 检索返回的 top-K 片段数 */
    private int topK = 5;
    /** 相似度阈值，低于该分数的片段不参与拼接上下文 */
    private double scoreThreshold = 0.35;
    /** 使用的 embedding 模型 */
    private String embeddingModel = "text-embedding-3-small";
    /** 文档切分时每个 chunk 的目标字符数 */
    private int chunkSize = 800;
    /** 相邻 chunk 之间的重叠字符数，避免语义在切分处被截断 */
    private int chunkOverlap = 120;
}
