package org.albedo.vllmpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。生产环境通过 JWT_SECRET 注入至少 32 字节的随机密钥。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "change-me-change-me-change-me-123456";

    private String issuer = "vllmpt";

    private long expirationMinutes = 120;
}
