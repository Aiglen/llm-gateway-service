package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private String documentId;
    private String source;
    private int chunkCount;
}
