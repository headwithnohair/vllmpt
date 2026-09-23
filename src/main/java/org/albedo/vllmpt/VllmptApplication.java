package org.albedo.vllmpt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 * <p>
 * 显式声明 Mapper 扫描包，同时每个 Mapper 接口上也加了 {@code @Mapper}（双保险）。
 * 注意两点：
 * <ul>
 *   <li>{@code basePackages} 不支持通配符</li>
 *   <li>如果把根包 {@code org.albedo.vllmpt} 作为扫描范围又不加 {@code annotationClass} 过滤，
 *       会把业务接口（如 MultimodalAssistant）误注册成数据访问接口</li>
 * </ul>
 * 新增模块时记得在这里补上对应的 mapper 包。
 */
@SpringBootApplication
@MapperScan({
        "org.albedo.vllmpt.module.user.mapper",
        "org.albedo.vllmpt.module.quota.mapper"
})
public class VllmptApplication {

    public static void main(String[] args) {
        SpringApplication.run(VllmptApplication.class, args);
    }

}
