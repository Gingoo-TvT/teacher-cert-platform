package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.vo.DataScopeProbeVO;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!prod")
@RequiredArgsConstructor
public class Phase2ProbeService {

    private final SysUserMapper userMapper;

    public List<DataScopeProbeVO> listStudents() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "STUDENT")
                        .orderByAsc(SysUser::getStudentId))
                .stream()
                .map(this::toProbeVO)
                .toList();
    }

    public DataScopeProbeVO getStudent(Long studentId) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "STUDENT")
                        .eq(SysUser::getStudentId, studentId)
                        .orderByAsc(SysUser::getStudentId))
                .stream()
                .map(this::toProbeVO)
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该学生"));
    }

    private DataScopeProbeVO toProbeVO(SysUser user) {
        return new DataScopeProbeVO(user.getStudentId(), user.getRealName(), user.getCollegeId());
    }
}
