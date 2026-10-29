package org.albedo.vllmpt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 文档配置。
 * <p>
 * 业务接口统一从 Authorization: Bearer &lt;jwt&gt; 读取身份；
 * 开发联调也可以使用 X-User-Id。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI approvalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("vLLM 审批平台 API")
                        .description("通用审批平台：实例、任务、模板、AI 决策与业务插件")
                        .version("P5"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("userHeader", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-User-Id")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
