package org.albedo.vllmpt.common.security;

import org.albedo.vllmpt.common.exception.BusinessException;

/**
 * 当前登录用户上下文。
 * <p>
 * P5 起由 {@code UserContextFilter} 从 {@code Authorization: Bearer <jwt>}
 * 解析；开发环境可通过 {@code X-User-Id} 回退。业务代码只依赖本上下文。
 */
public final class UserContextHolder {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static Long requireUserId() {
        Long userId = CURRENT_USER.get();
        if (userId == null) {
            throw new BusinessException(401, "未识别到当前用户，请通过 X-User-Id 请求头指定用户");
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
