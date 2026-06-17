package cn.edu.gpnu.platform.exchange.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultVO {

    private Long batchId;
    private String batchNo;
    private Integer total;
    private Integer successCount;
    private Integer failCount;
    private String status;
    private List<String> messages = new ArrayList<>();
}
