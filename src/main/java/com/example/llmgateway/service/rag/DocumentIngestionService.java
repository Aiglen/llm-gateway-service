package com.example.llmgateway.service.rag;

import com.example.llmgateway.config.RagProperties;
import com.example.llmgateway.dto.DocumentSummary;
import com.example.llmgateway.dto.IngestResponse;
import com.example.llmgateway.dto.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库写入编排：文本切分 -> 逐 chunk 向量化 -> 批量写入向量库 -> 记录文档元数据。
 * <p>
 * 这是"上传知识库"这条链路的核心：{@link com.example.llmgateway.controller.KnowledgeBaseController}
 * 负责接收文件/文本请求并做基础校验，真正的切分与向量化编排都在这里完成，
 * 方便未来替换切分策略或 embedding 服务时只改这一处。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final EmbeddingService embeddingService;
    private final VectorStoreClient vectorStoreClient;
    private final DocumentRegistry documentRegistry;
    private final RagProperties ragProperties;

    public Mono<IngestResponse> ingestText(String source, String content) {
        List<String> pieces = TextChunker.chunk(content, ragProperties.getChunkSize(), ragProperties.getChunkOverlap());
        if (pieces.isEmpty()) {
            return Mono.error(new IllegalArgumentException("文档内容为空或过短，未能生成任何知识片段"));
        }

        String documentId = UUID.randomUUID().toString();
        List<KnowledgeChunk> chunks = pieces.stream()
                .map(text -> new KnowledgeChunk(
                        documentId + "-" + UUID.randomUUID(),
                        text,
                        0,
                        source
                ))
                .collect(Collectors.toList());

        // 逐个 chunk 调用 embedding 接口，用 concatMap 保证顺序、避免瞬时并发过高触发厂商限流；
        // 若知识库写入量很大，可以考虑改为按批次的 embedding 接口（多数厂商支持 batch input）。
        return Flux.fromIterable(chunks)
                .concatMap(c -> embeddingService.embed(c.getText()))
                .collectList()
                .flatMap(vectors -> vectorStoreClient.upsert(chunks, vectors))
                .then(Mono.fromCallable(() -> {
                    DocumentSummary summary = new DocumentSummary(
                            documentId,
                            source,
                            chunks.size(),
                            Instant.now(),
                            chunks.stream().map(KnowledgeChunk::getId).collect(Collectors.toList())
                    );
                    documentRegistry.register(summary);
                    log.info("知识库文档写入完成: source={}, documentId={}, chunkCount={}", source, documentId, chunks.size());
                    return new IngestResponse(documentId, source, chunks.size());
                }));
    }

    public Mono<Void> deleteDocument(String documentId) {
        return documentRegistry.find(documentId)
                .map(summary -> vectorStoreClient.deleteByIds(summary.getChunkIds())
                        .doOnSuccess(v -> documentRegistry.remove(documentId)))
                .orElseGet(() -> Mono.error(new IllegalArgumentException("文档不存在或已被删除: " + documentId)));
    }
}
