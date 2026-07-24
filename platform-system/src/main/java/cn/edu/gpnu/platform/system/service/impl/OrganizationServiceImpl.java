package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.dto.CollegeSaveRequest;
import cn.edu.gpnu.platform.system.dto.MajorSaveRequest;
import cn.edu.gpnu.platform.system.dto.TrainingGoalConfigSaveRequest;
import cn.edu.gpnu.platform.system.entity.MajorTrainingGoal;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.TrainingGoalConfig;
import cn.edu.gpnu.platform.system.mapper.MajorTrainingGoalMapper;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.TrainingGoalConfigMapper;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.service.OrganizationService;
import cn.edu.gpnu.platform.system.vo.CollegeVO;
import cn.edu.gpnu.platform.system.vo.MajorVO;
import cn.edu.gpnu.platform.system.vo.TrainingGoalConfigVO;
import cn.edu.gpnu.platform.system.vo.TrainingGoalVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String TRAINING_GOAL_TYPE = "training_goal";
    private static final String TEACHING_SEGMENT_TYPE = "teaching_segment";
    private static final String INTERNSHIP_LOCATION_TYPE = "internship_location";

    private final SysCollegeMapper collegeMapper;
    private final SysMajorMapper majorMapper;
    private final MajorTrainingGoalMapper majorTrainingGoalMapper;
    private final TrainingGoalConfigMapper trainingGoalConfigMapper;
    private final SysDictItemMapper dictItemMapper;
    private final SysUserMapper userMapper;
    private final CollegeParentGuard collegeParentGuard;
    private final ObjectMapper objectMapper;
    private final DictService dictService;

    @Override
    public List<CollegeVO> listColleges(String keyword, Integer status) {
        LambdaQueryWrapper<SysCollege> wrapper = new LambdaQueryWrapper<SysCollege>()
                .orderByAsc(SysCollege::getSort)
                .orderByAsc(SysCollege::getCode);
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(SysCollege::getCode, normalizedKeyword)
                    .or()
                    .like(SysCollege::getName, normalizedKeyword));
        }
        if (status != null) {
            wrapper.eq(SysCollege::getStatus, status);
        }
        return collegeMapper.selectList(wrapper).stream().map(this::toCollegeVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCollege(CollegeSaveRequest request) {
        String code = normalizeRequired(request.getCode(), "学院编码不能为空");
        if (existsCollegeCode(code, null)) {
            throw new BizException("学院编码已存在");
        }
        SysCollege entity = new SysCollege();
        entity.setCode(code);
        fillCollege(entity, request);
        collegeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCollege(Long id, CollegeSaveRequest request) {
        SysCollege entity = requireCollege(id);
        String code = normalizeRequired(request.getCode(), "学院编码不能为空");
        if (existsCollegeCode(code, id)) {
            throw new BizException("学院编码已存在");
        }
        entity.setCode(code);
        fillCollege(entity, request);
        collegeMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCollege(Long id) {
        // 删除事务的第一条数据库读取必须锁住父行；所有子记录写入方也锁同一行，
        // 从而把“检查无子记录 + 逻辑删除”串行化为一个不可穿透的完整性边界。
        collegeParentGuard.lockExisting(id, CollegeParentGuard.Operation.DELETE);
        Long majorCount = majorMapper.selectCount(new LambdaQueryWrapper<SysMajor>()
                .eq(SysMajor::getCollegeId, id));
        if (majorCount > 0) {
            throw new BizException("学院下存在专业，不能删除");
        }
        // P0-12：学院下仍有账号（学生/教职工，sys_user.college_id）时禁止删除，避免用户/学生等静默悬挂已删学院
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getCollegeId, id));
        if (userCount > 0) {
            throw new BizException("学院下存在用户（学生/教职工），不能删除");
        }
        Long studentCount = collegeMapper.countActiveStudentsByCollegeId(id);
        if (studentCount > 0) {
            throw new BizException("学院下存在学生，不能删除");
        }
        // 历史导入回滚按 ref 独立判冲突，可能出现学生保持原学院、培养/证书快照先恢复的部分回滚。
        // 删除守卫必须直接覆盖三类带 college_id 的补偿目标，不能把完整性间接寄托在学生行上。
        Long trainingCount = collegeMapper.countActiveTrainingProfilesByCollegeId(id);
        if (trainingCount > 0) {
            throw new BizException("学院下存在培养信息，不能删除");
        }
        Long certificateCount = collegeMapper.countActiveCertificatesByCollegeId(id);
        if (certificateCount > 0) {
            throw new BizException("学院下存在证书，不能删除");
        }
        collegeMapper.deleteById(id);
    }

    @Override
    public List<MajorVO> listMajors(Long collegeId, String yearVersion, Integer pilotScopeFlag, Integer status, String keyword) {
        LambdaQueryWrapper<SysMajor> wrapper = new LambdaQueryWrapper<SysMajor>()
                .orderByAsc(SysMajor::getSort)
                .orderByAsc(SysMajor::getInternalMajorCode);
        if (collegeId != null) {
            wrapper.eq(SysMajor::getCollegeId, collegeId);
        }
        String normalizedYear = trimToNull(yearVersion);
        if (normalizedYear != null) {
            wrapper.eq(SysMajor::getYearVersion, normalizedYear);
        }
        if (pilotScopeFlag != null) {
            wrapper.eq(SysMajor::getPilotScopeFlag, pilotScopeFlag);
        }
        if (status != null) {
            wrapper.eq(SysMajor::getStatus, status);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(SysMajor::getInternalMajorCode, normalizedKeyword)
                    .or()
                    .like(SysMajor::getInternalMajorName, normalizedKeyword)
                    .or()
                    .like(SysMajor::getSecondDisciplineCode, normalizedKeyword)
                    .or()
                    .like(SysMajor::getSecondDisciplineName, normalizedKeyword));
        }
        return majorMapper.selectList(wrapper).stream().map(this::toMajorVO).toList();
    }

    @Override
    public MajorVO getMajor(Long id) {
        SysMajor major = requireMajor(id);
        assertMajorVisible(major);
        return toMajorVO(major);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMajor(MajorSaveRequest request) {
        Long collegeId = request.getCollegeId();
        collegeParentGuard.lockEnabled(collegeId, CollegeParentGuard.Operation.CREATE_MAJOR);
        String code = normalizeRequired(request.getInternalMajorCode(), "校内专业代码不能为空");
        String yearVersion = normalizeYearVersion(request.getYearVersion());
        if (existsMajorCode(code, yearVersion, null)) {
            throw new BizException("校内专业代码在当前年度版本已存在");
        }
        SysMajor entity = new SysMajor();
        entity.setCollegeId(collegeId);
        entity.setInternalMajorCode(code);
        entity.setYearVersion(yearVersion);
        fillMajor(entity, request);
        majorMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMajor(Long id, MajorSaveRequest request) {
        SysMajor entity = requireMajor(id);
        Long collegeId = request.getCollegeId();
        collegeParentGuard.lockEnabled(collegeId, CollegeParentGuard.Operation.UPDATE_MAJOR);
        String code = normalizeRequired(request.getInternalMajorCode(), "校内专业代码不能为空");
        String yearVersion = normalizeYearVersion(request.getYearVersion());
        if (existsMajorCode(code, yearVersion, id)) {
            throw new BizException("校内专业代码在当前年度版本已存在");
        }
        entity.setCollegeId(collegeId);
        entity.setInternalMajorCode(code);
        entity.setYearVersion(yearVersion);
        fillMajor(entity, request);
        majorMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMajor(Long id) {
        // Phase 43.1 / P0-12：专业“删除”改为“停用”（status=DISABLED），不再软删该行。
        // 理由：引用方 student / training_profile 位于 business 模块，且以专业 code/name 快照
        // 引用（非 major_id），system 模块无法常规统计使用量；软删专业行会让这些快照静默悬挂成孤儿。
        // 停用后行仍在库、引用完整、可随时恢复启用，杜绝孤儿。幂等：已停用直接返回。
        // major_training_goal 联动一并保留（停用非删除，恢复后目标不丢；停用专业本身已从新增/
        // 下拉可选项中排除，联动只在启用专业上生效），不再随“删除”清除。
        SysMajor entity = requireMajor(id);
        if (entity.getStatus() != null && entity.getStatus() == DISABLED) {
            return;
        }
        entity.setStatus(DISABLED);
        majorMapper.updateById(entity);
    }

    @Override
    public List<TrainingGoalVO> getMajorTrainingGoals(Long majorId) {
        requireMajor(majorId);
        List<MajorTrainingGoal> relations = majorTrainingGoalMapper.selectList(new LambdaQueryWrapper<MajorTrainingGoal>()
                .eq(MajorTrainingGoal::getMajorId, majorId)
                .eq(MajorTrainingGoal::getStatus, ENABLED)
                .orderByAsc(MajorTrainingGoal::getSort));
        Map<String, SysDictItem> trainingGoalDict = dictItems(TRAINING_GOAL_TYPE);
        List<TrainingGoalVO> result = new ArrayList<>();
        for (MajorTrainingGoal relation : relations) {
            SysDictItem item = trainingGoalDict.get(relation.getTrainingGoalCode());
            if (item != null) {
                result.add(toTrainingGoalVO(item));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceMajorTrainingGoals(Long majorId, List<String> trainingGoalCodes) {
        requireMajor(majorId);
        List<String> normalizedCodes = normalizeCodeList(trainingGoalCodes, "培养目标不能为空");
        Map<String, SysDictItem> trainingGoalDict = dictItems(TRAINING_GOAL_TYPE);
        for (String code : normalizedCodes) {
            if (!trainingGoalDict.containsKey(code)) {
                throw new BizException("培养目标编码不存在或已停用：" + code);
            }
        }
        Map<String, MajorTrainingGoal> existing = new LinkedHashMap<>();
        for (MajorTrainingGoal relation : majorTrainingGoalMapper.selectAllByMajorId(majorId)) {
            existing.put(relation.getTrainingGoalCode(), relation);
        }
        Set<String> selected = new LinkedHashSet<>(normalizedCodes);
        for (int i = 0; i < normalizedCodes.size(); i++) {
            String code = normalizedCodes.get(i);
            MajorTrainingGoal relation = existing.get(code);
            if (relation == null) {
                relation = new MajorTrainingGoal();
                relation.setMajorId(majorId);
                relation.setTrainingGoalCode(code);
                relation.setSort(i + 1);
                relation.setStatus(ENABLED);
                majorTrainingGoalMapper.insert(relation);
                continue;
            }
            relation.setSort(i + 1);
            relation.setStatus(ENABLED);
            relation.setDeleted(0);
            relation.setUpdatedBy(UserContext.getUserIdOrSystem());
            majorTrainingGoalMapper.updateIncludingDeleted(relation);
        }
        for (MajorTrainingGoal relation : existing.values()) {
            if (selected.contains(relation.getTrainingGoalCode())) {
                continue;
            }
            relation.setStatus(0);
            relation.setDeleted(1);
            relation.setUpdatedBy(UserContext.getUserIdOrSystem());
            majorTrainingGoalMapper.updateIncludingDeleted(relation);
        }
    }

    @Override
    public List<TrainingGoalConfigVO> listTrainingGoalConfigs(String trainingGoalCode) {
        LambdaQueryWrapper<TrainingGoalConfig> wrapper = new LambdaQueryWrapper<TrainingGoalConfig>()
                .orderByAsc(TrainingGoalConfig::getTrainingGoalCode);
        String normalizedCode = trimToNull(trainingGoalCode);
        if (normalizedCode != null) {
            wrapper.eq(TrainingGoalConfig::getTrainingGoalCode, normalizedCode);
        }
        return trainingGoalConfigMapper.selectList(wrapper).stream().map(this::toTrainingGoalConfigVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTrainingGoalConfig(TrainingGoalConfigSaveRequest request) {
        String trainingGoalCode = normalizeRequired(request.getTrainingGoalCode(), "培养目标编码不能为空");
        requireDictItem(TRAINING_GOAL_TYPE, trainingGoalCode, "培养目标编码不存在或已停用");
        List<String> allowedSegments = normalizeCodeList(request.getAllowedSegments(), "允许任教学段不能为空");
        List<String> allowedLocations = normalizeCodeList(request.getAllowedInternshipLocations(), "允许实习地点不能为空");
        String defaultSegment = normalizeRequired(request.getDefaultSegment(), "默认任教学段不能为空");
        String defaultLocation = normalizeRequired(request.getDefaultInternshipLocation(), "默认实习地点不能为空");
        validateDictCodes(TEACHING_SEGMENT_TYPE, allowedSegments, "任教学段编码不存在或已停用：");
        validateDictCodes(INTERNSHIP_LOCATION_TYPE, allowedLocations, "实习地点编码不存在或已停用：");
        if (!allowedSegments.contains(defaultSegment)) {
            throw new BizException("默认任教学段必须包含在允许任教学段中");
        }
        if (!allowedLocations.contains(defaultLocation)) {
            throw new BizException("默认实习地点必须包含在允许实习地点中");
        }
        TrainingGoalConfig entity = trainingGoalConfigMapper.selectOne(new LambdaQueryWrapper<TrainingGoalConfig>()
                .eq(TrainingGoalConfig::getTrainingGoalCode, trainingGoalCode)
                .last("LIMIT 1"));
        if (entity == null) {
            entity = new TrainingGoalConfig();
            entity.setTrainingGoalCode(trainingGoalCode);
        }
        entity.setDefaultSegment(defaultSegment);
        entity.setAllowedSegmentsJson(writeJsonArray(allowedSegments));
        entity.setDefaultInternshipLocation(defaultLocation);
        entity.setAllowedInternshipLocationsJson(writeJsonArray(allowedLocations));
        entity.setStatus(defaultInt(request.getStatus(), ENABLED));
        if (entity.getId() == null) {
            trainingGoalConfigMapper.insert(entity);
        } else {
            trainingGoalConfigMapper.updateById(entity);
        }
    }

    private void fillCollege(SysCollege entity, CollegeSaveRequest request) {
        entity.setName(normalizeRequired(request.getName(), "学院名称不能为空"));
        entity.setSort(defaultInt(request.getSort(), 0));
        entity.setStatus(defaultInt(request.getStatus(), ENABLED));
    }

    private void assertMajorVisible(SysMajor major) {
        DataScopeContext.Scope scope = DataScopeContext.get();
        if (major == null) {
            throw new BizException(cn.edu.gpnu.platform.common.api.ResultCode.FORBIDDEN.getCode(), "无权访问该专业");
        }
        if (scope == null || scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            if (scope.getMajorIds().contains(major.getId()) || scope.getCollegeIds().contains(major.getCollegeId())) {
                return;
            }
        }
        throw new BizException(cn.edu.gpnu.platform.common.api.ResultCode.FORBIDDEN.getCode(), "无权访问该专业");
    }

    private void fillMajor(SysMajor entity, MajorSaveRequest request) {
        entity.setInternalMajorName(normalizeRequired(request.getInternalMajorName(), "校内专业名称不能为空"));
        entity.setSecondDisciplineCode(trimToNull(request.getSecondDisciplineCode()));
        entity.setSecondDisciplineName(trimToNull(request.getSecondDisciplineName()));
        entity.setPilotScopeFlag(defaultInt(request.getPilotScopeFlag(), 0));
        entity.setSort(defaultInt(request.getSort(), 0));
        entity.setStatus(defaultInt(request.getStatus(), ENABLED));
    }

    private boolean existsCollegeCode(String code, Long excludeId) {
        return collegeMapper.countByCodeIncludingDeleted(code, excludeId) > 0;
    }

    private boolean existsMajorCode(String code, String yearVersion, Long excludeId) {
        return majorMapper.countByCodeYearIncludingDeleted(code, yearVersion, excludeId) > 0;
    }

    private SysCollege requireCollege(Long id) {
        if (id == null) {
            throw new BizException("学院ID不能为空");
        }
        SysCollege entity = collegeMapper.selectById(id);
        if (entity == null) {
            throw new BizException("学院不存在");
        }
        return entity;
    }

    private SysMajor requireMajor(Long id) {
        if (id == null) {
            throw new BizException("专业ID不能为空");
        }
        SysMajor entity = majorMapper.selectById(id);
        if (entity == null) {
            throw new BizException("专业不存在");
        }
        return entity;
    }

    private SysDictItem requireDictItem(String typeCode, String itemCode, String message) {
        SysDictItem item = dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemCode, itemCode)
                .eq(SysDictItem::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysDictItem::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BizException(message + "：" + itemCode);
        }
        return item;
    }

    private void validateDictCodes(String typeCode, List<String> codes, String messagePrefix) {
        Map<String, SysDictItem> items = dictItems(typeCode);
        for (String code : codes) {
            if (!items.containsKey(code)) {
                throw new BizException(messagePrefix + code);
            }
        }
    }

    // Phase 44c（§7.3）：专业列表 toMajorVO→getMajorTrainingGoals 原对同一字典逐条查库（~3N+1），改走
    // DictService 的缓存（GLOBAL 版启用项，写时经 evictItemsCache 逐出）。返回可变副本，保持原「每次返回新 map」语义。
    private Map<String, SysDictItem> dictItems(String typeCode) {
        return new LinkedHashMap<>(dictService.globalEnabledDictItems(typeCode));
    }

    private List<String> normalizeCodeList(List<String> codes, String message) {
        if (codes == null || codes.isEmpty()) {
            throw new BizException(message);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String code : codes) {
            String normalized = trimToNull(code);
            if (normalized == null) {
                throw new BizException(message);
            }
            unique.add(normalized);
        }
        return new ArrayList<>(unique);
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new BizException("JSON序列化失败");
        }
    }

    private List<String> readJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new BizException("JSON解析失败");
        }
    }

    private CollegeVO toCollegeVO(SysCollege entity) {
        CollegeVO vo = new CollegeVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private MajorVO toMajorVO(SysMajor entity) {
        MajorVO vo = new MajorVO();
        vo.setId(entity.getId());
        vo.setCollegeId(entity.getCollegeId());
        SysCollege college = collegeMapper.selectById(entity.getCollegeId());
        vo.setCollegeName(college == null ? null : college.getName());
        vo.setInternalMajorCode(entity.getInternalMajorCode());
        vo.setInternalMajorName(entity.getInternalMajorName());
        vo.setSecondDisciplineCode(entity.getSecondDisciplineCode());
        vo.setSecondDisciplineName(entity.getSecondDisciplineName());
        vo.setPilotScopeFlag(entity.getPilotScopeFlag());
        vo.setYearVersion(entity.getYearVersion());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setTrainingGoals(getMajorTrainingGoals(entity.getId()));
        return vo;
    }

    private TrainingGoalVO toTrainingGoalVO(SysDictItem item) {
        TrainingGoalVO vo = new TrainingGoalVO();
        vo.setCode(item.getItemCode());
        vo.setName(item.getItemValue());
        vo.setSort(item.getSort());
        vo.setStatus(item.getStatus());
        return vo;
    }

    private TrainingGoalConfigVO toTrainingGoalConfigVO(TrainingGoalConfig entity) {
        Map<String, SysDictItem> trainingGoals = dictItems(TRAINING_GOAL_TYPE);
        Map<String, SysDictItem> segments = dictItems(TEACHING_SEGMENT_TYPE);
        Map<String, SysDictItem> locations = dictItems(INTERNSHIP_LOCATION_TYPE);
        TrainingGoalConfigVO vo = new TrainingGoalConfigVO();
        vo.setId(entity.getId());
        vo.setTrainingGoalCode(entity.getTrainingGoalCode());
        vo.setTrainingGoalName(nameOf(trainingGoals, entity.getTrainingGoalCode()));
        vo.setDefaultSegment(entity.getDefaultSegment());
        vo.setDefaultSegmentName(nameOf(segments, entity.getDefaultSegment()));
        vo.setAllowedSegments(readJsonArray(entity.getAllowedSegmentsJson()));
        vo.setDefaultInternshipLocation(entity.getDefaultInternshipLocation());
        vo.setDefaultInternshipLocationName(nameOf(locations, entity.getDefaultInternshipLocation()));
        vo.setAllowedInternshipLocations(readJsonArray(entity.getAllowedInternshipLocationsJson()));
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private String nameOf(Map<String, SysDictItem> items, String code) {
        SysDictItem item = items.get(code);
        return item == null ? null : item.getItemValue();
    }

    private String normalizeYearVersion(String yearVersion) {
        String normalized = trimToNull(yearVersion);
        return normalized == null ? DEFAULT_YEAR_VERSION : normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Integer defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
