package org.albedo.vllmpt.common.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 身份过滤器。
 * <p>
 * 优先解析 {@code Authorization: Bearer <jwt>}；开发环境在
 * {@code app.identity.default-user-enabled=true} 时允许回退到 {@code X-User-Id}
 * 或默认用户。生产环境关闭默认用户后只接受 JWT。
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_HEADER = "X-User-Id";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Value("${app.identity.default-user-enabled:false}")
    private boolean defaultUserEnabled;

    @Value("${app.identity.default-user-id:1}")
    private Long defaultUserId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Long userId = resolveUserId(request, response);
            if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                return;
            }
            if (userId == null && defaultUserEnabled) {
                userId = defaultUserId;
            }
            if (userId != null) {
                UserContextHolder.setUserId(userId);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 优先 Bearer Token；没有 Token 时保留开发期 X-User-Id。
     * Token 存在但无效时直接 401，不再回退到请求头。
     */
    private Long resolveUserId(HttpServletRequest request, HttpServletResponse response)
            throws java.io.IOException {
        String authorization = request.getHeader(AUTH_HEADER);
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith(BEARER_PREFIX)) {
                unauthorized(response, "Authorization 必须使用 Bearer Token");
                return null;
            }
            try {
                return jwtService.parseUserId(authorization.substring(BEARER_PREFIX.length()).trim());
            } catch (JwtException | IllegalArgumentException e) {
                unauthorized(response, "登录凭证无效或已过期");
                return null;
            }
        }
        // X-User-Id 仅作为开发期回退；关闭默认用户后必须使用 JWT。
        return defaultUserEnabled ? parseUserId(request.getHeader(USER_HEADER)) : null;
    }

    private void unauthorized(HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }

    private Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
