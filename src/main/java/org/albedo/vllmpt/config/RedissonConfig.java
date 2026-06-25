package org.albedo.vllmpt.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import jakarta.annotation.PostConstruct;

/**
 * Redisson 配置类
 * 手动配置 Redisson 客户端和 RedisTemplate，避免自动配置的兼容性问题
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.lettuce.pool.max-active:20}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.min-idle:5}")
    private int minIdle;

    @PostConstruct
    public void init() {
        System.out.println("[Redisson] 初始化 Redis 连接: " + redisHost + ":" + redisPort);
        System.out.println("[Redisson] 密码: " + (redisPassword != null && !redisPassword.isEmpty() ? "[已设置]" : "[空]"));
        System.out.println("[Redisson] Database: " + redisDatabase);
        System.out.println("[Redisson] 激活的 Profile: " + System.getProperty("spring.profiles.active", "未设置"));
    }

    /**
     * 配置 Redisson 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        String address = "redis://" + redisHost + ":" + redisPort;
        var singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(maxActive)
                .setConnectionMinimumIdleSize(minIdle);
        
        // 设置密码
        if (redisPassword != null && !redisPassword.isEmpty()) {
            singleServerConfig.setPassword(redisPassword);
            System.out.println("[Redisson] ✅ 已设置密码");
        }
        
        System.out.println("[Redisson] RedissonClient 初始化成功，地址: " + address);
        return Redisson.create(config);
    }

    /**
     * 配置 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用新的序列化器 API（Spring Data Redis 4.0+）
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashValueSerializer(RedisSerializer.json());
        template.afterPropertiesSet();
        
        return template;
    }
}
