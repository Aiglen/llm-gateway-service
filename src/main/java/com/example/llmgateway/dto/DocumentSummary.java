package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 已写入知识库的文档元信息（用于前端展示知识库文档列表）。
 * 注意：当前用内存注册表实现，服务重启后会丢失；
 * 生产环境应把这份元数据持久化到数据库，向量数据本身仍在 Milvus 中不受影响。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSummary {
    private String documentId;
    private String source;
    private int chunkCount;
    private Instant uploadedAt;
    /** 该文档拆分出的所有 chunk ID，删除文档时用于批量删除向量 */
    private List<String> chunkIds;
}
