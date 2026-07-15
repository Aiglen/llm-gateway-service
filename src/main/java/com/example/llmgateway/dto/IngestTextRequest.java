package com.example.llmgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 直接粘贴文本写入知识库的请求体（不经过文件上传）。
 */
@Data
public class IngestTextRequest {
    /** 文档来源标识，用于展示与引用溯源，如 "产品手册 v2" */
    @NotBlank
    private String source;

    @NotBlank
    private String content;
}
