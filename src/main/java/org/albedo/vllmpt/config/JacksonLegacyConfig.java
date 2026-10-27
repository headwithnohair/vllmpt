package org.albedo.vllmpt.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 默认使用 Jackson 3，不再自动装配 Jackson 2 的 ObjectMapper。
 * 审批条件 DSL、表单校验等代码仍使用 Jackson 2 API，这里显式提供 Bean，
 * 避免与后续可能出现的 Jackson 2 自动配置重复。
 */
@Configuration
public class JacksonLegacyConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper()
                // 草稿/审批工具会序列化 LocalDateTime，必须注册 JSR310 模块
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
