package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.vo.DictItemVO;
import cn.edu.gpnu.platform.system.vo.DictTypeVO;

import java.util.List;

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
}
