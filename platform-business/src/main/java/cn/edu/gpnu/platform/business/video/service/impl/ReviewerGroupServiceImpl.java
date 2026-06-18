package cn.edu.gpnu.platform.business.video.service.impl;

import cn.edu.gpnu.platform.business.video.dto.ReviewerGroupMemberRequest;
import cn.edu.gpnu.platform.business.video.dto.ReviewerGroupRequest;
import cn.edu.gpnu.platform.business.video.entity.ReviewerGroup;
import cn.edu.gpnu.platform.business.video.entity.ReviewerGroupMember;
import cn.edu.gpnu.platform.business.video.mapper.ReviewerGroupMapper;
import cn.edu.gpnu.platform.business.video.mapper.ReviewerGroupMemberMapper;
import cn.edu.gpnu.platform.business.video.service.ReviewerGroupService;
import cn.edu.gpnu.platform.business.video.vo.ReviewerGroupMemberVO;
import cn.edu.gpnu.platform.business.video.vo.ReviewerGroupVO;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewerGroupServiceImpl implements ReviewerGroupService {

    private static final String REVIEW_TEACHER = "REVIEW_TEACHER";

    private final ReviewerGroupMapper groupMapper;
    private final ReviewerGroupMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final DataScopeService dataScopeService;

    @Override
    public List<ReviewerGroupVO> list() {
        DataScopeContext.Scope scope = assignScope();
        LambdaQueryWrapper<ReviewerGroup> wrapper = new LambdaQueryWrapper<ReviewerGroup>()
                .orderByAsc(ReviewerGroup::getCollegeId)
                .orderByAsc(ReviewerGroup::getName);
        if (scope.allSchool()) {
            return groupMapper.selectList(wrapper).stream().map(this::toVO).toList();
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE && !scope.getCollegeIds().isEmpty()) {
            wrapper.in(ReviewerGroup::getCollegeId, scope.getCollegeIds());
            return groupMapper.selectList(wrapper).stream().map(this::toVO).toList();
        }
        return List.of();
    }

    @Override
    public ReviewerGroupVO detail(Long id) {
        ReviewerGroup group = requireGroup(id);
        ensureCanManageCollege(group.getCollegeId());
        return toVO(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewerGroupVO create(ReviewerGroupRequest request) {
        Long collegeId = currentCollegeForCreate();
        ensureCanManageCollege(collegeId);
        String name = requiredTrim(request.getName(), "评审组名称不能为空");
        ensureNameUnique(null, collegeId, name);
        ReviewerGroup group = new ReviewerGroup();
        group.setCollegeId(collegeId);
        group.setName(name);
        group.setStatus(normalizeStatus(request.getStatus()));
        groupMapper.insert(group);
        return toVO(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewerGroupVO update(Long id, ReviewerGroupRequest request) {
        ReviewerGroup group = requireGroup(id);
        ensureCanManageCollege(group.getCollegeId());
        String name = requiredTrim(request.getName(), "评审组名称不能为空");
        ensureNameUnique(group.getId(), group.getCollegeId(), name);
        group.setName(name);
        group.setStatus(normalizeStatus(request.getStatus()));
        groupMapper.updateById(group);
        return toVO(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReviewerGroup group = requireGroup(id);
        ensureCanManageCollege(group.getCollegeId());
        memberMapper.update(null, new LambdaUpdateWrapper<ReviewerGroupMember>()
                .eq(ReviewerGroupMember::getGroupId, group.getId())
                .setSql("reviewer_user_id = id, deleted = 1"));
        groupMapper.update(null, new LambdaUpdateWrapper<ReviewerGroup>()
                .eq(ReviewerGroup::getId, group.getId())
                .setSql("name = CONCAT(LEFT(name, 80), '#', id), deleted = 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewerGroupVO addMember(Long groupId, ReviewerGroupMemberRequest request) {
        ReviewerGroup group = requireGroup(groupId);
        ensureCanManageCollege(group.getCollegeId());
        SysUser reviewer = requireReviewTeacherInCollege(request.getReviewerUserId(), group.getCollegeId());
        ReviewerGroupMember exists = memberMapper.selectOne(new LambdaQueryWrapper<ReviewerGroupMember>()
                .eq(ReviewerGroupMember::getGroupId, group.getId())
                .eq(ReviewerGroupMember::getReviewerUserId, reviewer.getId())
                .last("LIMIT 1"));
        if (exists == null) {
            ReviewerGroupMember member = new ReviewerGroupMember();
            member.setGroupId(group.getId());
            member.setReviewerUserId(reviewer.getId());
            memberMapper.insert(member);
        }
        return toVO(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long memberId) {
        ReviewerGroup group = requireGroup(groupId);
        ensureCanManageCollege(group.getCollegeId());
        if (memberId == null) {
            throw new BizException("评审组成员ID不能为空");
        }
        ReviewerGroupMember member = memberMapper.selectById(memberId);
        if (member == null || !group.getId().equals(member.getGroupId())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评审组成员不存在");
        }
        memberMapper.update(null, new LambdaUpdateWrapper<ReviewerGroupMember>()
                .eq(ReviewerGroupMember::getId, member.getId())
                .setSql("reviewer_user_id = id, deleted = 1"));
    }

    private ReviewerGroupVO toVO(ReviewerGroup group) {
        List<ReviewerGroupMemberVO> members = members(group.getId());
        ReviewerGroupVO vo = new ReviewerGroupVO();
        vo.setId(group.getId());
        vo.setCollegeId(group.getCollegeId());
        vo.setName(group.getName());
        vo.setStatus(group.getStatus());
        vo.setMemberCount(members.size());
        vo.setMembers(members);
        return vo;
    }

    private List<ReviewerGroupMemberVO> members(Long groupId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ReviewerGroupMember>()
                        .eq(ReviewerGroupMember::getGroupId, groupId)
                        .orderByAsc(ReviewerGroupMember::getId))
                .stream()
                .map(this::toMemberVO)
                .toList();
    }

    private ReviewerGroupMemberVO toMemberVO(ReviewerGroupMember member) {
        SysUser reviewer = userMapper.selectById(member.getReviewerUserId());
        ReviewerGroupMemberVO vo = new ReviewerGroupMemberVO();
        vo.setId(member.getId());
        vo.setReviewerUserId(member.getReviewerUserId());
        vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
        vo.setWorkNo(reviewer == null ? null : reviewer.getWorkNo());
        return vo;
    }

    private ReviewerGroup requireGroup(Long id) {
        if (id == null) {
            throw new BizException("评审组ID不能为空");
        }
        ReviewerGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评审组不存在");
        }
        return group;
    }

    private SysUser requireReviewTeacherInCollege(Long reviewerUserId, Long collegeId) {
        if (reviewerUserId == null) {
            throw new BizException("评审教师不能为空");
        }
        SysUser reviewer = userMapper.selectById(reviewerUserId);
        if (reviewer == null || !"ENABLED".equals(reviewer.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评审教师不存在或未启用");
        }
        if (!collegeId.equals(reviewer.getCollegeId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "评审教师不属于本学院");
        }
        List<String> roleCodes = roleMapper.selectCodesByUserId(reviewer.getId());
        if (!roleCodes.contains(REVIEW_TEACHER)) {
            throw new BizException("评审组成员必须是评审教师");
        }
        return reviewer;
    }

    private void ensureNameUnique(Long currentId, Long collegeId, String name) {
        ReviewerGroup exists = groupMapper.selectOne(new LambdaQueryWrapper<ReviewerGroup>()
                .eq(ReviewerGroup::getCollegeId, collegeId)
                .eq(ReviewerGroup::getName, name)
                .last("LIMIT 1"));
        if (exists != null && !exists.getId().equals(currentId)) {
            throw new BizException("同学院下评审组名称已存在");
        }
    }

    private Long currentCollegeForCreate() {
        DataScopeContext.Scope scope = assignScope();
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE && scope.getCollegeId() != null) {
            return scope.getCollegeId();
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE && scope.getCollegeIds().size() == 1) {
            return scope.getCollegeIds().iterator().next();
        }
        Long currentCollegeId = UserContext.get() == null ? null : UserContext.get().getCollegeId();
        if (scope.allSchool() && currentCollegeId != null) {
            return currentCollegeId;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无法确定评审组所属学院");
    }

    private void ensureCanManageCollege(Long collegeId) {
        DataScopeContext.Scope scope = assignScope();
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(collegeId)) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该学院评审组");
    }

    private DataScopeContext.Scope assignScope() {
        DataScopeContext.Scope scope = dataScopeService.resolve("video:assign");
        if (scope == null || scope.getScopeType() == DataScopeContext.ScopeType.NONE) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作评审组");
        }
        return scope;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ENABLED";
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ENABLED", "DISABLED").contains(value)) {
            throw new BizException("评审组状态仅支持 ENABLED/DISABLED");
        }
        return value;
    }

    private String requiredTrim(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
