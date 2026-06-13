package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.dto.SubjectImportError;
import cn.edu.gpnu.platform.system.dto.SubjectImportResult;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import cn.edu.gpnu.platform.system.service.TeachingSubjectService;
import cn.edu.gpnu.platform.system.vo.TeachingSubjectVO;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeachingSubjectServiceImpl implements TeachingSubjectService {

    private static final int ENABLED = 1;
    private static final int CATEGORY = 1;
    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String TEACHING_SEGMENT_TYPE = "teaching_segment";
    private static final int RECENT_LIMIT = 10;
    private static final Duration RECENT_TTL = Duration.ofDays(180);

    private final TeachingSubjectMapper teachingSubjectMapper;
    private final SysDictItemMapper dictItemMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<TeachingSubjectVO> list(String segmentCode, String keyword, String category, String yearVersion) {
        LambdaQueryWrapper<TeachingSubject> wrapper = new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getStatus, ENABLED)
                .eq(TeachingSubject::getYearVersion, normalizeYearVersion(yearVersion))
                .orderByAsc(TeachingSubject::getSegmentCode)
                .orderByAsc(TeachingSubject::getCategoryNode)
                .orderByAsc(TeachingSubject::getIsCategory)
                .orderByAsc(TeachingSubject::getSubjectCode);
        String normalizedSegment = trimToNull(segmentCode);
        if (normalizedSegment != null) {
            wrapper.eq(TeachingSubject::getSegmentCode, normalizedSegment);
        }
        String normalizedCategory = trimToNull(category);
        if (normalizedCategory != null) {
            wrapper.eq(TeachingSubject::getCategoryNode, normalizedCategory);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(TeachingSubject::getSubjectName, normalizedKeyword)
                    .or()
                    .like(TeachingSubject::getKeyword, normalizedKeyword));
        }
        return teachingSubjectMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectImportResult importSubjects(InputStream inputStream, String defaultYearVersion) throws IOException {
        if (inputStream == null) {
            throw new BizException("导入文件不能为空");
        }
        String fallbackYearVersion = normalizeYearVersion(defaultYearVersion);
        SubjectExcelListener listener = new SubjectExcelListener(fallbackYearVersion);
        FastExcel.read(inputStream, listener)
                .autoCloseStream(false)
                .sheet()
                .doRead();
        SubjectImportResult result = listener.toResult();
        validateRows(listener.getRows(), result);
        if (!result.getErrors().isEmpty()) {
            result.setFailCount(result.getErrors().size());
            result.setSuccessCount(0);
            return result;
        }
        for (SubjectImportRow row : listener.getRows()) {
            TeachingSubject entity = findByCodeAndYear(row.getSubjectCode(), row.getYearVersion());
            if (entity == null) {
                entity = new TeachingSubject();
                entity.setSubjectCode(row.getSubjectCode());
                entity.setYearVersion(row.getYearVersion());
            }
            entity.setSegmentCode(row.getSegmentCode());
            entity.setCategoryNode(row.getCategoryNode());
            entity.setSubjectName(row.getSubjectName());
            entity.setIsCategory(row.getIsCategory());
            entity.setKeyword(row.getKeyword());
            entity.setStatus(row.getStatus());
            if (entity.getId() == null) {
                teachingSubjectMapper.insert(entity);
            } else {
                teachingSubjectMapper.updateById(entity);
            }
        }
        result.setSuccessCount(result.getTotal());
        result.setFailCount(0);
        return result;
    }

    @Override
    public void validateSelectable(String segmentCode, String subjectCode, String yearVersion) {
        requireSelectable(segmentCode, subjectCode, normalizeYearVersion(yearVersion));
    }

    @Override
    public void recordRecent(String segmentCode, String subjectCode, String yearVersion) {
        String normalizedYear = normalizeYearVersion(yearVersion);
        TeachingSubject subject = requireSelectable(segmentCode, subjectCode, normalizedYear);
        String key = recentKey(subject.getSegmentCode(), normalizedYear);
        redisTemplate.opsForList().remove(key, 0, subject.getSubjectCode());
        redisTemplate.opsForList().leftPush(key, subject.getSubjectCode());
        redisTemplate.opsForList().trim(key, 0, RECENT_LIMIT - 1);
        redisTemplate.expire(key, RECENT_TTL);
    }

    @Override
    public List<TeachingSubjectVO> recent(String segmentCode, String yearVersion) {
        String normalizedSegment = normalizeRequired(segmentCode, "任教学段不能为空");
        String normalizedYear = normalizeYearVersion(yearVersion);
        List<String> codes = redisTemplate.opsForList().range(recentKey(normalizedSegment, normalizedYear), 0, RECENT_LIMIT - 1);
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> orderedCodes = new LinkedHashSet<>(codes);
        List<TeachingSubject> subjects = teachingSubjectMapper.selectList(new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getSegmentCode, normalizedSegment)
                .eq(TeachingSubject::getYearVersion, normalizedYear)
                .eq(TeachingSubject::getStatus, ENABLED)
                .ne(TeachingSubject::getIsCategory, CATEGORY)
                .in(TeachingSubject::getSubjectCode, orderedCodes));
        Map<String, TeachingSubject> byCode = new HashMap<>();
        for (TeachingSubject subject : subjects) {
            byCode.put(subject.getSubjectCode(), subject);
        }
        List<TeachingSubjectVO> result = new ArrayList<>();
        for (String code : orderedCodes) {
            TeachingSubject subject = byCode.get(code);
            if (subject != null) {
                result.add(toVO(subject));
            }
        }
        return result;
    }

    private void validateRows(List<SubjectImportRow> rows, SubjectImportResult result) {
        if (rows.isEmpty()) {
            addError(result, 1, "file", "", "导入文件没有有效数据");
            return;
        }
        Set<String> validSegments = validSegmentCodes();
        Set<String> fileKeys = new HashSet<>();
        for (SubjectImportRow row : rows) {
            validateRequired(result, row.getRowNo(), "segment_code", row.getSegmentCode(), "任教学段编码不能为空");
            validateRequired(result, row.getRowNo(), "subject_code", row.getSubjectCode(), "任教学科编码不能为空");
            validateRequired(result, row.getRowNo(), "subject_name", row.getSubjectName(), "任教学科名称不能为空");
            if (StringUtils.hasText(row.getSegmentCode()) && !validSegments.contains(row.getSegmentCode())) {
                addError(result, row.getRowNo(), "segment_code", row.getSegmentCode(), "任教学段编码不在 teaching_segment 字典中");
            }
            if (row.getIsCategory() == CATEGORY && !StringUtils.hasText(row.getCategoryNode())) {
                addError(result, row.getRowNo(), "category_node", "", "类别节点必须填写 category_node");
            }
            if (row.getStatus() != 0 && row.getStatus() != 1) {
                addError(result, row.getRowNo(), "status", String.valueOf(row.getStatus()), "状态只能为 1 或 0");
            }
            String fileKey = row.getSubjectCode() + "#" + row.getYearVersion();
            if (!fileKeys.add(fileKey)) {
                addError(result, row.getRowNo(), "subject_code", row.getSubjectCode(), "同一导入文件内 subject_code + year_version 重复");
            }
        }
    }

    private void validateRequired(SubjectImportResult result, int rowNo, String field, String value, String reason) {
        if (!StringUtils.hasText(value)) {
            addError(result, rowNo, field, value, reason);
        }
    }

    private Set<String> validSegmentCodes() {
        List<SysDictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, TEACHING_SEGMENT_TYPE)
                .eq(SysDictItem::getStatus, ENABLED)
                .eq(SysDictItem::getYearVersion, DEFAULT_YEAR_VERSION));
        Set<String> codes = new HashSet<>();
        for (SysDictItem item : items) {
            codes.add(item.getItemCode());
        }
        return codes;
    }

    private TeachingSubject requireSelectable(String segmentCode, String subjectCode, String yearVersion) {
        String normalizedSegment = normalizeRequired(segmentCode, "任教学段不能为空");
        String normalizedSubjectCode = normalizeRequired(subjectCode, "任教学科编码不能为空");
        TeachingSubject subject = teachingSubjectMapper.selectOne(new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getSegmentCode, normalizedSegment)
                .eq(TeachingSubject::getSubjectCode, normalizedSubjectCode)
                .eq(TeachingSubject::getYearVersion, yearVersion)
                .eq(TeachingSubject::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (subject == null) {
            throw new BizException("任教学科不属于当前学段或未启用");
        }
        if (subject.getIsCategory() != null && subject.getIsCategory() == CATEGORY) {
            throw new BizException("任教学科类别节点不可选择");
        }
        return subject;
    }

    private TeachingSubject findByCodeAndYear(String subjectCode, String yearVersion) {
        return teachingSubjectMapper.selectOne(new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getSubjectCode, subjectCode)
                .eq(TeachingSubject::getYearVersion, yearVersion)
                .last("LIMIT 1"));
    }

    private TeachingSubjectVO toVO(TeachingSubject entity) {
        TeachingSubjectVO vo = new TeachingSubjectVO();
        vo.setId(entity.getId());
        vo.setSegmentCode(entity.getSegmentCode());
        vo.setCategoryNode(entity.getCategoryNode());
        vo.setSubjectCode(entity.getSubjectCode());
        vo.setSubjectName(entity.getSubjectName());
        vo.setIsCategory(entity.getIsCategory());
        vo.setSelectable(entity.getStatus() != null && entity.getStatus() == ENABLED
                && (entity.getIsCategory() == null || entity.getIsCategory() != CATEGORY));
        vo.setKeyword(entity.getKeyword());
        vo.setYearVersion(entity.getYearVersion());
        vo.setStatus(entity.getStatus());
        return vo;
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

    private void addError(SubjectImportResult result, int rowNo, String field, String value, String reason) {
        SubjectImportError error = new SubjectImportError();
        error.setRowNo(rowNo);
        error.setField(field);
        error.setErrorValue(value);
        error.setReason(reason);
        result.getErrors().add(error);
    }

    private String recentKey(String segmentCode, String yearVersion) {
        return "subject:recent:" + UserContext.getUserIdOrSystem() + ":" + yearVersion + ":" + segmentCode;
    }

    private static final class SubjectExcelListener implements ReadListener<Map<Integer, String>> {

        private final String fallbackYearVersion;
        private final Map<Integer, String> headers = new LinkedHashMap<>();
        private final List<SubjectImportRow> rows = new ArrayList<>();

        private SubjectExcelListener(String fallbackYearVersion) {
            this.fallbackYearVersion = fallbackYearVersion;
        }

        @Override
        public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
            headers.clear();
            for (Map.Entry<Integer, ReadCellData<?>> entry : headMap.entrySet()) {
                headers.put(entry.getKey(), normalizeHeader(cellToString(entry.getValue())));
            }
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            SubjectImportRow row = new SubjectImportRow();
            row.setRowNo(context.readRowHolder().getRowIndex() + 1);
            row.setSegmentCode(value(data, "segment_code", "segmentCode", "任教学段编码", "学段编码"));
            row.setCategoryNode(value(data, "category_node", "categoryNode", "类别节点编码", "类别节点"));
            row.setSubjectCode(value(data, "subject_code", "subjectCode", "任教学科编码", "学科编码"));
            row.setSubjectName(value(data, "subject_name", "subjectName", "任教学科名称", "学科名称"));
            row.setIsCategory(parseFlag(value(data, "is_category", "isCategory", "是否类别节点"), 0));
            row.setKeyword(value(data, "keyword", "关键词", "检索词"));
            row.setYearVersion(defaultString(value(data, "year_version", "yearVersion", "年度版本"), fallbackYearVersion));
            row.setStatus(parseFlag(value(data, "status", "状态"), ENABLED));
            if (row.hasAnyValue()) {
                rows.add(row);
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // no-op
        }

        private String value(Map<Integer, String> data, String... aliases) {
            for (Map.Entry<Integer, String> header : headers.entrySet()) {
                for (String alias : aliases) {
                    if (header.getValue().equals(normalizeHeader(alias))) {
                        return trimToNull(data.get(header.getKey()));
                    }
                }
            }
            return null;
        }

        private SubjectImportResult toResult() {
            SubjectImportResult result = new SubjectImportResult();
            result.setTotal(rows.size());
            return result;
        }

        private List<SubjectImportRow> getRows() {
            return rows;
        }

        private static String normalizeHeader(String header) {
            String normalized = trimToNull(header);
            return normalized == null ? "" : normalized.replace("_", "").replace(" ", "").toLowerCase();
        }

        private static String cellToString(ReadCellData<?> cell) {
            if (cell == null) {
                return null;
            }
            if (StringUtils.hasText(cell.getStringValue())) {
                return cell.getStringValue();
            }
            BigDecimal number = cell.getNumberValue();
            if (number != null) {
                return number.stripTrailingZeros().toPlainString();
            }
            Boolean bool = cell.getBooleanValue();
            return bool == null ? null : String.valueOf(bool);
        }

        private static Integer parseFlag(String value, int defaultValue) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                return defaultValue;
            }
            if ("是".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
                return 1;
            }
            if ("否".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
                return 0;
            }
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private static String defaultString(String value, String defaultValue) {
            String normalized = trimToNull(value);
            return normalized == null ? defaultValue : normalized;
        }

        private static String trimToNull(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim();
        }
    }

    private static final class SubjectImportRow {

        private int rowNo;
        private String segmentCode;
        private String categoryNode;
        private String subjectCode;
        private String subjectName;
        private Integer isCategory = 0;
        private String keyword;
        private String yearVersion = DEFAULT_YEAR_VERSION;
        private Integer status = ENABLED;

        private boolean hasAnyValue() {
            return StringUtils.hasText(segmentCode)
                    || StringUtils.hasText(categoryNode)
                    || StringUtils.hasText(subjectCode)
                    || StringUtils.hasText(subjectName)
                    || StringUtils.hasText(keyword);
        }

        private int getRowNo() {
            return rowNo;
        }

        private void setRowNo(int rowNo) {
            this.rowNo = rowNo;
        }

        private String getSegmentCode() {
            return segmentCode;
        }

        private void setSegmentCode(String segmentCode) {
            this.segmentCode = segmentCode;
        }

        private String getCategoryNode() {
            return categoryNode;
        }

        private void setCategoryNode(String categoryNode) {
            this.categoryNode = categoryNode;
        }

        private String getSubjectCode() {
            return subjectCode;
        }

        private void setSubjectCode(String subjectCode) {
            this.subjectCode = subjectCode;
        }

        private String getSubjectName() {
            return subjectName;
        }

        private void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        private Integer getIsCategory() {
            return isCategory;
        }

        private void setIsCategory(Integer isCategory) {
            this.isCategory = isCategory;
        }

        private String getKeyword() {
            return keyword;
        }

        private void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        private String getYearVersion() {
            return yearVersion;
        }

        private void setYearVersion(String yearVersion) {
            this.yearVersion = yearVersion;
        }

        private Integer getStatus() {
            return status;
        }

        private void setStatus(Integer status) {
            this.status = status;
        }
    }
}
