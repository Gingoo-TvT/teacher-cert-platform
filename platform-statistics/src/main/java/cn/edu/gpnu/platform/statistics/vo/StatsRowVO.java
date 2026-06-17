package cn.edu.gpnu.platform.statistics.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class StatsRowVO {

    private String dimension;
    private String dimensionLabel;
    private String status;
    private String statusLabel;
    private Long count;
    private Map<String, String> values = new LinkedHashMap<>();
}
