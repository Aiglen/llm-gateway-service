package com.example.llmgateway.controller;

import com.example.llmgateway.dto.DocumentSummary;
import com.example.llmgateway.dto.IngestResponse;
import com.example.llmgateway.dto.IngestTextRequest;
import com.example.llmgateway.service.rag.DocumentIngestionService;
import com.example.llmgateway.service.rag.DocumentRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 知识库管理接口：把文档写入向量库（供 RagService 检索使用），并支持查看/删除已上传文档。
 * <p>
 * 当前文本抽取仅原生支持纯文本格式（.txt / .md）：直接按 UTF-8 解码。
 * 如果要支持 PDF / Word，需要引入 Apache Tika 或 PDFBox / POI 做文本抽取，
 * 抽取出纯文本后仍然复用下面同一条 {@link DocumentIngestionService#ingestText} 链路，
 * 不需要改动切分、向量化、写入逻辑。
 */
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private static final Set<String> SUPPORTED_TEXT_EXTENSIONS = Set.of("txt", "md", "markdown");

    private final DocumentIngestionService ingestionService;
    private final DocumentRegistry documentRegistry;

    /** 直接粘贴文本写入知识库，适合没有现成文件、临时补充资料的场景 */
    @PostMapping("/text")
    public Mono<ResponseEntity<IngestResponse>> ingestText(@Valid @RequestBody IngestTextRequest request) {
        return ingestionService.ingestText(request.getSource(), request.getContent())
                .map(ResponseEntity::ok);
    }

    /** 上传文件写入知识库，当前原生支持 .txt / .md，其他格式建议先转换为纯文本 */
    @PostMapping("/upload")
    public Mono<ResponseEntity<IngestResponse>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "未命名文档";
        String extension = extractExtension(filename);

        if (!SUPPORTED_TEXT_EXTENSIONS.contains(extension)) {
            return Mono.error(new IllegalArgumentException(
                    "暂不支持 ." + extension + " 格式的直接解析（当前仅支持 .txt / .md），" +
                            "请先转换为纯文本后上传，或在后端接入 Apache Tika / PDFBox 扩展文本抽取能力"));
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return ingestionService.ingestText(filename, content).map(ResponseEntity::ok);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("文件读取失败: " + e.getMessage()));
        }
    }

    /** 列出已写入知识库的文档（元数据存于内存，服务重启后会清空，向量数据本身不受影响） */
    @GetMapping("/documents")
    public List<DocumentSummary> listDocuments() {
        return documentRegistry.list();
    }

    /** 删除某个文档：同时清理其在向量库中的所有 chunk 与本地元数据记录 */
    @DeleteMapping("/documents/{documentId}")
    public Mono<ResponseEntity<Void>> deleteDocument(@PathVariable String documentId) {
        return ingestionService.deleteDocument(documentId)
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
