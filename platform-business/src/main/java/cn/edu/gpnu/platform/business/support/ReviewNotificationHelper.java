package cn.edu.gpnu.platform.business.support;

import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewNotificationHelper {

    private static final String COLLEGE_CLERK = "COLLEGE_CLERK";
    private static final String COLLEGE_AUDITOR = "COLLEGE_AUDITOR";

    private final SysUserMapper userMapper;
    private final NotificationService notificationService;

    public void notifySubmitted(Long collegeId, Long studentId, String moduleName, String status, String bizType, Long bizId) {
        try {
            if ("SECOND_REVIEW".equals(status)) {
                notifyCollegeAuditors(collegeId, moduleName + "待复审", content(moduleName, studentId, "已提交至待复审"), bizType, bizId);
            } else {
                notifyCollegeClerks(collegeId, moduleName + "待初审", content(moduleName, studentId, "已提交至待初审"), bizType, bizId);
            }
        } catch (Exception e) {
            log.warn("提交通知生成失败: bizType={}, bizId={}", bizType, bizId, e);
        }
    }

    public void notifyFirstReviewPassed(Long collegeId, Long studentId, String moduleName, String bizType, Long bizId) {
        try {
            notifyCollegeAuditors(collegeId, moduleName + "待复审", content(moduleName, studentId, "初审通过，待复审"), bizType, bizId);
        } catch (Exception e) {
            log.warn("初审通过通知生成失败: bizType={}, bizId={}", bizType, bizId, e);
        }
    }

    public void notifyReturnedToStudent(Long studentId, String moduleName, String action, String bizType, Long bizId) {
        try {
            SysUser studentUser = userMapper.selectEnabledByStudentId(studentId);
            if (studentUser != null) {
                String title = moduleName + ("FAIL".equals(action) ? "不通过" : "退回");
                String result = "FAIL".equals(action) ? "不通过" : "已退回";
                notificationService.send(studentUser.getId(), "REVIEW_RETURN", title,
                        content(moduleName, studentId, result), bizType, String.valueOf(bizId));
            }
        } catch (Exception e) {
            log.warn("退回通知生成失败: bizType={}, bizId={}", bizType, bizId, e);
        }
    }

    public void notifySecondReviewReturned(Long collegeId, Long studentId, String moduleName, String action, String bizType, Long bizId) {
        notifyReturnedToStudent(studentId, moduleName, action, bizType, bizId);
        try {
            String title = moduleName + ("FAIL".equals(action) ? "不通过" : "退回");
            String result = "FAIL".equals(action) ? "复审不通过" : "复审退回";
            notifyCollegeClerks(collegeId, title, content(moduleName, studentId, result), bizType, bizId);
        } catch (Exception e) {
            log.warn("复审退回抄送通知生成失败: bizType={}, bizId={}", bizType, bizId, e);
        }
    }

    public void notifyVideoAssigned(List<Long> reviewerIds, Long studentId, String bizType, Long bizId) {
        try {
            notificationService.sendBatch(distinct(reviewerIds), "VIDEO_ASSIGN", "视频评审提醒",
                    content("教学能力视频", studentId, "已分配评审任务"), bizType, String.valueOf(bizId));
        } catch (Exception e) {
            log.warn("视频评审分配通知生成失败: bizType={}, bizId={}", bizType, bizId, e);
        }
    }

    public void notifyExportCompleted(Long userId, String title, String fileName, String bizType, String bizId) {
        try {
            notificationService.send(userId, "EXPORT_DONE", title,
                    StringUtils.hasText(fileName) ? "导出完成：" + fileName : "导出完成", bizType, bizId);
        } catch (Exception e) {
            log.warn("导出完成通知生成失败: bizType={}, bizId={}", bizType, bizId, e);
        }
    }

    private void notifyCollegeClerks(Long collegeId, String title, String content, String bizType, Long bizId) {
        notifyRole(COLLEGE_CLERK, collegeId, title, content, bizType, bizId);
    }

    private void notifyCollegeAuditors(Long collegeId, String title, String content, String bizType, Long bizId) {
        notifyRole(COLLEGE_AUDITOR, collegeId, title, content, bizType, bizId);
    }

    private void notifyRole(String roleCode, Long collegeId, String title, String content, String bizType, Long bizId) {
        List<SysUser> users = userMapper.selectEnabledByRoleAndCollege(roleCode, collegeId);
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        notificationService.sendBatch(userIds, "REVIEW_TODO", title, content, bizType, String.valueOf(bizId));
    }

    private Set<Long> distinct(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                result.add(userId);
            }
        }
        return result;
    }

    private String content(String moduleName, Long studentId, String action) {
        return moduleName + "（学生ID：" + studentId + "）" + action + "。";
    }
}
