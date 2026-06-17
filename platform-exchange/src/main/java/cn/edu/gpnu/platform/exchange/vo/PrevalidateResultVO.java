package cn.edu.gpnu.platform.exchange.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PrevalidateResultVO {

    private Long batchId;
    private String batchNo;
    private Integer total;
    private Integer successCount;
    private Integer failCount;
    private List<ImportPreviewRowVO> previewRows = new ArrayList<>();
    private List<ImportErrorVO> errors = new ArrayList<>();
}
