package cn.edu.gpnu.platform.statistics.service;

import cn.edu.gpnu.platform.statistics.dto.StatsQuery;
import cn.edu.gpnu.platform.statistics.vo.StatsExportFile;
import cn.edu.gpnu.platform.statistics.vo.StatsReportVO;

public interface StatsService {

    StatsReportVO report(String type, StatsQuery query);

    StatsExportFile export(String type, StatsQuery query);
}
