package cn.edu.gpnu.platform.common.context;

/**
 * 当前登录用户上下文（ThreadLocal）。Phase 2 登录拦截器写入；用于审计、数据权限、自动填充。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long id) {
        USER_ID.set(id);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /** 取当前用户 id，未登录返回系统账号 0 */
    public static Long getUserIdOrSystem() {
        Long id = USER_ID.get();
        return id == null ? 0L : id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
