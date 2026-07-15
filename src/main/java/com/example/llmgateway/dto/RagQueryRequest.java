package com.example.llmgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RagQueryRequest {
    @NotBlank
    private String question;
    private String model;
    private String userId = "anonymous";
    private Integer topK;
}
