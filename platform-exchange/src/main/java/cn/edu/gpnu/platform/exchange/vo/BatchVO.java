package cn.edu.gpnu.platform.exchange.vo;

import lombok.Data;

@Data
public class BatchVO {

    private Long id;
    private String batchNo;
    private String type;
    private String fileName;
    private String operateTime;
    private Integer total;
    private Integer successCount;
    private Integer failCount;
    private String strategy;
    private String status;
    private String remark;
}
