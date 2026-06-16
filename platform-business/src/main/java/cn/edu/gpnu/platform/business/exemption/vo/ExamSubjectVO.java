package cn.edu.gpnu.platform.business.exemption.vo;

import lombok.Data;

@Data
public class ExamSubjectVO {

    private String subject;
    private String subjectLabel;
    private boolean exempted;
    private boolean includedInExam;
}
