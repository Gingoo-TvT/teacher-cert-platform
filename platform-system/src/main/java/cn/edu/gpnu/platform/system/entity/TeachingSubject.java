package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任教学科标准库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("teaching_subject")
public class TeachingSubject extends BaseEntity {

    private String segmentCode;
    private String categoryNode;
    private String subjectCode;
    private String subjectName;
    private Integer isCategory;
    private String keyword;
    private String yearVersion;
    private Integer status;
}
