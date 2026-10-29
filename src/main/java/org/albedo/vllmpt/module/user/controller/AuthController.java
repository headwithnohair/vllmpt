package org.albedo.vllmpt.module.user.controller;

import lombok.RequiredArgsConstructor;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发期 token 接口。
 * <p>
 * 仅用于本地联调：生产环境必须保持 {@code app.jwt.dev-token-enabled=false}，
 * 由统一登录服务签发 token。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @Value("${app.jwt.dev-token-enabled:false}")
    private boolean devTokenEnabled;

    @Value("${app.identity.default-user-enabled:false}")
    private boolean defaultUserEnabled;

    @PostMapping("/token")
    public Result<String> token(@RequestBody DevTokenRequest request) {
        if (!devTokenEnabled || !defaultUserEnabled) {
            throw new BusinessException(404, "开发 token 接口未开启");
        }
        if (request == null || request.userId() == null) {
            throw new BusinessException(400, "userId 不能为空");
        }
        return Result.success(jwtService.issue(request.userId()));
    }

    public record DevTokenRequest(Long userId) {
    }
}
