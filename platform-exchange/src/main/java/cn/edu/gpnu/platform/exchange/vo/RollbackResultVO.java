package cn.edu.gpnu.platform.exchange.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RollbackResultVO {

    private Long batchId;
    private String batchNo;
    private Integer rolledBackCount;
    private Integer conflictCount;
    private String status;
    private List<String> conflicts = new ArrayList<>();
}
