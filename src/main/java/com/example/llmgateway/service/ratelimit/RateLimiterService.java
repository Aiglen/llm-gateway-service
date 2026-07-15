package com.example.llmgateway.service.ratelimit;

import com.example.llmgateway.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于令牌桶算法（Bucket4j）实现的按用户/API Key 限流。
 * 生产环境建议把 bucket 状态放到 Redis（bucket4j-redis）以支持多实例共享，
 * 这里用内存 Map 演示单实例场景下的限流能力。
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** 尝试消费一个请求配额，返回是否放行 */
    public boolean tryAcquire(String userId) {
        if (!properties.isEnabled()) {
            return true;
        }
        Bucket bucket = buckets.computeIfAbsent(userId, id -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        int permitsPerMinute = properties.getRequestsPerMinute();
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(permitsPerMinute).refillGreedy(permitsPerMinute, Duration.ofMinutes(1)))
                .build();
    }
}
