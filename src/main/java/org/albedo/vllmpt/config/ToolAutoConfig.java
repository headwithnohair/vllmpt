package org.albedo.vllmpt.config;

import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.albedo.vllmpt.module.chat.tool.AiTool;
import org.albedo.vllmpt.module.chat.tool.AiToolProvider;
import org.albedo.vllmpt.module.chat.tool.ToolRegistry;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

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


        allBeans.forEach(bean -> {
            // ★ 修改点：获取真实的 Target Class，防止代理类导致注解丢失
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            AiTool meta = AnnotationUtils.findAnnotation(targetClass, AiTool.class);

            if (meta != null) {
                registry.register(bean, meta.groups());
            }
        });
        return registry;
    }
}

