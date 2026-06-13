package cn.edu.gpnu.platform.system.dto;

import lombok.Data;

@Data
public class SubjectImportError {

    private Integer rowNo;
    private String field;
    private String errorValue;
    private String reason;
}
