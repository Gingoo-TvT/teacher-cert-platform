package cn.edu.gpnu.platform.statistics.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StatsReportVO {

    private String type;
    private String title;
    private String assessmentYear;
    private String denominatorRule;
    private List<StatsMetricVO> metrics = new ArrayList<>();
    private List<StatsRowVO> rows = new ArrayList<>();
    private List<StatsDetailVO> details = new ArrayList<>();
}
