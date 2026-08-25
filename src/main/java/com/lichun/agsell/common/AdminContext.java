package com.lichun.agsell.common;

/**
 * 管理员上下文（ThreadLocal）
 * 用于在管理员端请求中存储当前登录管理员的 ID、角色和 JWT ID
 */
public class AdminContext {

    private static final ThreadLocal<Long> ADMIN_ID_LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<String> JTI_LOCAL = new ThreadLocal<>();

    public static void setCurrentAdmin(Long adminId, String role) {
        ADMIN_ID_LOCAL.set(adminId);
        ROLE_LOCAL.set(role);
    }

    public static void setCurrentAdmin(Long adminId, String role, String jti) {
        ADMIN_ID_LOCAL.set(adminId);
        ROLE_LOCAL.set(role);
        JTI_LOCAL.set(jti);
    }

    public static Long getCurrentAdminId() {
        return ADMIN_ID_LOCAL.get();
    }

    public static String getCurrentRole() {
        return ROLE_LOCAL.get();
    }

    public static String getCurrentJti() {
        return JTI_LOCAL.get();
    }

    public static void removeCurrentAdmin() {
        ADMIN_ID_LOCAL.remove();
        ROLE_LOCAL.remove();
        JTI_LOCAL.remove();
    }
}
