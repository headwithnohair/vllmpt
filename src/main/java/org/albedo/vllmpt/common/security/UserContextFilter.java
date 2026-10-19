package org.albedo.vllmpt.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 开发期身份过滤器。
 * <p>
 * 优先读取 {@code X-User-Id} 请求头；开发环境允许回退到默认用户，
 * 方便不接登录系统时联调。生产环境把
 * {@code app.identity.default-user-enabled} 设为 false 即可强制要求请求头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_HEADER = "X-User-Id";

    @Value("${app.identity.default-user-enabled:false}")
    private boolean defaultUserEnabled;

    @Value("${app.identity.default-user-id:1}")
    private Long defaultUserId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Long userId = parseUserId(request.getHeader(USER_HEADER));
            if (userId == null && defaultUserEnabled) {
                userId = defaultUserId;
            }
            if (userId != null) {
                UserContextHolder.setUserId(userId);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
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
