package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String model;
    private String provider;
    private String content;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    /** 本次响应是否由降级策略（备用模型/缓存/拒绝）产生 */
    private boolean degraded;
    private String degradeReason;
}
