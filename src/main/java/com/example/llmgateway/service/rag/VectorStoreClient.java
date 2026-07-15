package com.example.llmgateway.service.rag;

import com.example.llmgateway.dto.KnowledgeChunk;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 向量库统一抽象接口，屏蔽 Milvus / Pinecone / Chroma 等具体实现差异。
 * 上层 RagService 只依赖本接口，切换向量库只需替换 Bean 实现。
 */
public interface VectorStoreClient {

    /** 相似度检索，返回 top-K 知识片段（已按 score 降序排列） */
    Mono<List<KnowledgeChunk>> similaritySearch(float[] queryVector, int topK);

    /** 写入/更新一批知识片段的向量，用于构建知识库 */
    Mono<Void> upsert(List<KnowledgeChunk> chunks, List<float[]> vectors);

    /** 按 chunk ID 批量删除向量，用于删除某个已上传的文档 */
    Mono<Void> deleteByIds(List<String> ids);
}
