package cn.edu.gpnu.platform.common.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前登录用户上下文（ThreadLocal）。Phase 2 登录拦截器写入；用于审计、数据权限、自动填充。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long id) {
        CurrentUser user = new CurrentUser();
        user.setUserId(id);
        CURRENT.set(user);
    }

    public static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    public static CurrentUser get() {
        return CURRENT.get();
    }

    public static Long getUserId() {
        CurrentUser user = CURRENT.get();
        return user == null ? null : user.getUserId();
    }

    /** 取当前用户 id，未登录返回系统账号 0 */
    public static Long getUserIdOrSystem() {
        Long id = getUserId();
        return id == null ? 0L : id;
    }

    public static Set<String> getPermissions() {
        CurrentUser user = CURRENT.get();
        return user == null ? Collections.emptySet() : user.getPermissions();
    }

    public static boolean hasPermission(String code) {
        return getPermissions().contains(code);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static class CurrentUser {
        private Long userId;
        private String username;
        private String realName;
        private String userType;
        private Long collegeId;
        private Long studentId;
        private boolean mustChangePwd;
        private Set<String> roles = new LinkedHashSet<>();
        private Set<String> permissions = new LinkedHashSet<>();

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getUserType() {
            return userType;
        }

        public void setUserType(String userType) {
            this.userType = userType;
        }

        public Long getCollegeId() {
            return collegeId;
        }

        public void setCollegeId(Long collegeId) {
            this.collegeId = collegeId;
        }

        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long studentId) {
            this.studentId = studentId;
        }

        public boolean isMustChangePwd() {
            return mustChangePwd;
        }

        public void setMustChangePwd(boolean mustChangePwd) {
            this.mustChangePwd = mustChangePwd;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public void setRoles(Set<String> roles) {
            this.roles = roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles);
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
        }
    }
}
