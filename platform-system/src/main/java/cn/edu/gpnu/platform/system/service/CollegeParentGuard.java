package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 无数据库外键模式下的学院父子完整性锁。
 *
 * <p>所有会新增或移动学院子记录的事务，与学院删除事务，都必须先锁定同一学院行。
 * 这样删除后的子记录写入会因父行已逻辑删除而失败，先完成的子记录写入则会被删除方统计到。</p>
 */
@Service
@RequiredArgsConstructor
public class CollegeParentGuard {

    private static final int ENABLED = 1;

    private final SysCollegeMapper collegeMapper;
    private final CollegeParentLockHook lockHook;

    public enum Operation {
        DELETE,
        CREATE_MAJOR,
        UPDATE_MAJOR,
        CREATE_USER,
        UPDATE_USER,
        CREATE_STUDENT,
        UPDATE_STUDENT,
        IMPORT_STUDENT
    }

    /**
     * 锁住仍存在的学院。用户和学生历史上允许归属停用学院，因此这里只校验存在性。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Integer lockExisting(Long collegeId, Operation operation) {
        if (collegeId == null) {
            throw new BizException("学院ID不能为空");
        }
        lockHook.beforeLock(operation, collegeId);
        Integer status = collegeMapper.selectStatusForUpdate(collegeId);
        lockHook.afterLock(operation, collegeId, status);
        if (status == null) {
            throw new BizException("学院不存在");
        }
        return status;
    }

    /**
     * 专业只能归属启用学院；锁查询与状态校验必须处在调用方写事务内。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void lockEnabled(Long collegeId, Operation operation) {
        Integer status = lockExisting(collegeId, operation);
        if (status != ENABLED) {
            throw new BizException("学院不存在或已停用");
        }
    }
}
