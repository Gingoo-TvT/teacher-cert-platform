package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.vo.RegionPathVO;
import cn.edu.gpnu.platform.system.vo.RegionVO;

import java.util.List;

public interface RegionService {

    List<RegionVO> children(String parentCode);

    RegionPathVO path(String code);

    String fullName(String code);

    void validateTriplet(String provinceCode, String cityCode, String countyCode);
}
