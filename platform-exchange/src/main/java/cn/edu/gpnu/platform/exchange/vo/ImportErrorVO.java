package cn.edu.gpnu.platform.exchange.vo;

import lombok.Data;

@Data
public class ImportErrorVO {

    private Long id;
    private Integer rowNo;
    private String studentNo;
    private String studentName;
    private String fieldName;
    private String errorValue;
    private String errorReason;
    private String suggestion;
}
