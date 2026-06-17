package cn.edu.gpnu.platform.exchange.vo;

import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import lombok.Data;

@Data
public class ImportPreviewRowVO {

    private Integer rowNo;
    private ExchangeStandardRow row;
}
