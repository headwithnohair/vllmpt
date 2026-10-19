package org.albedo.vllmpt.common.security;

import org.albedo.vllmpt.common.exception.BusinessException;

/**
 * 当前登录用户上下文。
 * <p>
 * 现有项目没有接入登录鉴权，P0 由 {@code UserContextFilter} 从请求头
 * {@code X-User-Id} 读取；接入正式鉴权后只需要替换过滤器，业务代码不用改。
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
