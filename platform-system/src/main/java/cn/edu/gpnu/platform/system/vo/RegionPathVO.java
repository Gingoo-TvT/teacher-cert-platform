package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 行政区划路径。
 */
@Data
public class RegionPathVO {

    private String code;
    private String fullName;
    private List<RegionVO> nodes;
}
