package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.vo.DictItemVO;
import cn.edu.gpnu.platform.system.vo.DictTypeVO;

import java.util.List;
import java.util.Map;

public interface DictService {

    List<DictTypeVO> listTypes();

    Long createType(DictTypeSaveRequest request);

    void updateType(Long id, DictTypeSaveRequest request);

    void deleteType(Long id);

    List<DictItemVO> listItems(String typeCode, Boolean onlyEnabled);

    Long createItem(DictItemSaveRequest request);

    void updateItem(Long id, DictItemSaveRequest request);

    void deleteItem(Long id);

    void evictItemsCache(String typeCode);

    /**
     * Phase 44c（§7.3）：启用字典项的 code→label 标签表（不限年度版本），跨业务模块 toVO/校验共用（缓存、写时逐出）。
     * 返回<b>不可变</b>视图，调用方如需改写请自行复制。
     */
    Map<String, String> dictLabels(String typeCode);

    /**
     * Phase 44c（§7.3）：GLOBAL 年度版本的启用字典项（code→item），供组织专业列表消除逐条同字典查询（缓存、写时逐出）。
     * 返回<b>不可变</b>视图，调用方如需改写请自行复制。
     */
    Map<String, SysDictItem> globalEnabledDictItems(String typeCode);
}
