package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

@Data
public class TeachingSubjectVO {

    private Long id;
    private String segmentCode;
    private String categoryNode;
    private String subjectCode;
    private String subjectName;
    private Integer isCategory;
    private Boolean selectable;
    private String keyword;
    private String yearVersion;
    private Integer status;
}
