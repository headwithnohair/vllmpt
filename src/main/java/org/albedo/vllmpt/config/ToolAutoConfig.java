package org.albedo.vllmpt.config;

import org.albedo.vllmpt.module.chat.tool.AiTool;
import org.albedo.vllmpt.module.chat.tool.AiToolProvider;
import org.albedo.vllmpt.module.chat.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

@Configuration
public class ToolAutoConfig {

    /** 所有标注 @AiTool 的 Bean 自动收集 */
    @Bean
    public ToolRegistry toolRegistry(List<AiToolProvider> allBeans) {
        ToolRegistry registry = new ToolRegistry();

        allBeans.stream()
                .filter(b -> b.getClass().isAnnotationPresent(AiTool.class))
                .forEach(bean -> {
                    AiTool meta = bean.getClass().getAnnotation(AiTool.class);
                    registry.register(bean, meta.groups());
                });

        return registry;
    }
}

