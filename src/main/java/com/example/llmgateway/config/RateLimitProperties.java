package com.example.llmgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm.rate-limit")
public class RateLimitProperties {
    /** 是否开启限流 */
    private boolean enabled = true;
    /** 每个用户/apiKey 每分钟允许的请求数 */
    private int requestsPerMinute = 30;
    /** 每个用户/apiKey 每天允许消耗的 token 数，超出后触发降级（切换到更便宜的模型或拒绝） */
    private long dailyTokenQuota = 200_000;
}
