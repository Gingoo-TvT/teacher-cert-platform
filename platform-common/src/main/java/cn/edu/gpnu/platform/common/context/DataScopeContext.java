package cn.edu.gpnu.platform.common.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DataScopeContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(Scope scope) {
        CURRENT.set(scope);
    }

    public static Scope get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public enum ScopeType {
        NONE, SELF, COLLEGE, SCHOOL, SYSTEM, LOGIN_ALL, ASSIGNED
    }

    public static class Scope {
        private ScopeType scopeType = ScopeType.NONE;
        private Long userId;
        private Long collegeId;
        private Long studentId;
        private String alias;
        private Set<Long> collegeIds = new LinkedHashSet<>();
        private Set<Long> majorIds = new LinkedHashSet<>();

        public ScopeType getScopeType() {
            return scopeType;
        }

        public void setScopeType(ScopeType scopeType) {
            this.scopeType = scopeType == null ? ScopeType.NONE : scopeType;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
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

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public Set<Long> getCollegeIds() {
            return Collections.unmodifiableSet(collegeIds);
        }

        public void setCollegeIds(Set<Long> collegeIds) {
            this.collegeIds = collegeIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(collegeIds);
        }

        public Set<Long> getMajorIds() {
            return Collections.unmodifiableSet(majorIds);
        }

        public void setMajorIds(Set<Long> majorIds) {
            this.majorIds = majorIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(majorIds);
        }

        public boolean allSchool() {
            return scopeType == ScopeType.SCHOOL || scopeType == ScopeType.SYSTEM || scopeType == ScopeType.LOGIN_ALL;
        }
    }
}
