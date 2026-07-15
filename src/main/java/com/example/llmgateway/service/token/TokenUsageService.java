package com.example.llmgateway.service.token;

import com.example.llmgateway.config.RateLimitProperties;
import com.example.llmgateway.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token 用量统计服务：按用户维度累计当日已消耗 token 数，
 * 用于配额控制（超出配额时上层可切换到更便宜的模型或直接拒绝，即“降级”）。
 * 生产环境建议持久化到数据库/Redis，这里用内存实现演示统计口径。
 */
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final RateLimitProperties rateLimitProperties;

    private record DailyUsage(LocalDate date, AtomicLong tokens) {
    }

    private final ConcurrentHashMap<String, AtomicReference<DailyUsage>> usageMap = new ConcurrentHashMap<>();

    /** 记录一次调用的 token 消耗 */
    public void record(String userId, ChatResponse response) {
        AtomicReference<DailyUsage> ref = usageMap.computeIfAbsent(userId,
                id -> new AtomicReference<>(new DailyUsage(LocalDate.now(), new AtomicLong(0))));
        DailyUsage usage = ref.get();
        if (!usage.date().equals(LocalDate.now())) {
            usage = new DailyUsage(LocalDate.now(), new AtomicLong(0));
            ref.set(usage);
        }
        usage.tokens().addAndGet(response.getTotalTokens());
    }

    /** 当日已用 token 数 */
    public long getTodayUsage(String userId) {
        AtomicReference<DailyUsage> ref = usageMap.get(userId);
        if (ref == null) return 0L;
        DailyUsage usage = ref.get();
        return usage.date().equals(LocalDate.now()) ? usage.tokens().get() : 0L;
    }

    /** 是否已超出每日 token 配额，超出后调用方应触发降级策略 */
    public boolean isQuotaExceeded(String userId) {
        return getTodayUsage(userId) >= rateLimitProperties.getDailyTokenQuota();
    }
}
