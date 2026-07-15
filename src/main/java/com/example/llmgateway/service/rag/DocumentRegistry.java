package com.example.llmgateway.service.rag;

import com.example.llmgateway.dto.DocumentSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已上传文档的元数据注册表。
 * <p>
 * 向量本身存储在 Milvus 里，但"这个文档叫什么名字、拆成了哪些 chunk"这类元信息
 * 需要单独记录，才能支持"列出知识库里有哪些文档""删除某个文档"这类管理操作。
 * 这里用内存 Map 演示，服务重启后元数据会丢失（Milvus 里的向量不受影响，但会变成
 * "孤儿数据"，无法再通过本注册表删除）——生产环境务必把这份元数据持久化到数据库。
 */
@Component
public class DocumentRegistry {

    private final ConcurrentHashMap<String, DocumentSummary> documents = new ConcurrentHashMap<>();

    public void register(DocumentSummary summary) {
        documents.put(summary.getDocumentId(), summary);
    }

    public List<DocumentSummary> list() {
        return documents.values().stream()
                .sorted((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()))
                .toList();
    }

    public Optional<DocumentSummary> find(String documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    public void remove(String documentId) {
        documents.remove(documentId);
    }
}
