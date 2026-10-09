package org.albedo.vllmpt.module.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 每日 Token 配额配置，对应 application-dev.yml 中的 ai.quota 段。
 * <p>
 * 与 {@link ConcurrentLimitProperties} 同风格：{@code @Data + @Component + 字段默认值}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.quota")
public class TokenQuotaProperties {

    /**
     * 每次请求的预扣估算值（固定保守值，不依赖 maxTokens）。
     * <p>
     * ⚠️ 边界：若某用户的日额度小于该值，第一次预扣就会因「已用 0 + 估算值 &gt; 额度」直接返回超额，
     * 该用户将永远无法使用。把用户额度调小做测试时会撞到这条下界。
     */
    private long estimateTokens = 2000L;

    /**
     * 用户在 ai_user_token_quota 里没有记录时使用的默认日额度。
     * <p>
     * 注意：这个值直接决定「无配额记录的用户每天能发几次」，取值要明显大于 estimateTokens。
     */
    private long defaultDailyLimit = 10000L;

    /** 日额度缓存的过期时间（秒）。TTL 只是兜底，配额变更时应主动删掉 {@code ai:quota:limit:{userId}} */
    private long cacheTtlSeconds = 600L;

    /**
     * 额度计数器与分模型用量 Hash 的兜底过期时间（秒），默认 2 天。
     * <p>
     * 用「从此刻起 N 秒」而不是「到今天结束」，只是为了让昨天的键能自然消失，
     * 真正决定归属哪一天的是键名里的 {@code yyyyMMdd}。
     */
    private long counterTtlSeconds = 172800L;
}
