package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.dto.DictItemSaveRequest;
import cn.edu.gpnu.platform.system.dto.DictTypeSaveRequest;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.vo.DictItemVO;
import cn.edu.gpnu.platform.system.vo.DictTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "字典")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "查询字典类型")
    @PreAuthorize("@pms.has('dict:view')")
    @DataScope(alias = "sys_dict_type", permission = "dict:view")
    @GetMapping("/types")
    public Result<List<DictTypeVO>> listTypes() {
        return Result.ok(dictService.listTypes());
    }

    @Operation(summary = "按类型查询启用字典项")
    @PreAuthorize("@pms.has('dict:view')")
    @DataScope(alias = "sys_dict_item", permission = "dict:view")
    @GetMapping("/{typeCode}/items")
    public Result<List<DictItemVO>> listItems(@PathVariable String typeCode,
                                              @RequestParam(value = "onlyEnabled", defaultValue = "true") Boolean onlyEnabled) {
        return Result.ok(dictService.listItems(typeCode, onlyEnabled));
    }

    @Operation(summary = "新增字典类型")
    @PreAuthorize("@pms.has('dict:manage')")
    @AuditLog(bizType = "dict", operation = "createType")
    @PostMapping("/type")
    public Result<Long> createType(@Valid @RequestBody DictTypeSaveRequest request) {
        return Result.ok(dictService.createType(request));
    }

    @Operation(summary = "修改字典类型")
    @PreAuthorize("@pms.has('dict:manage')")
    @AuditLog(bizType = "dict", operation = "updateType")
    @PutMapping("/type/{id}")
    public Result<Void> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeSaveRequest request) {
        dictService.updateType(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除字典类型")
    @PreAuthorize("@pms.has('dict:manage')")
    @AuditLog(bizType = "dict", operation = "deleteType")
    @DeleteMapping("/type/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    @Operation(summary = "新增字典项")
    @PreAuthorize("@pms.has('dict:manage')")
    @AuditLog(bizType = "dict", operation = "createItem")
    @PostMapping("/item")
    public Result<Long> createItem(@Valid @RequestBody DictItemSaveRequest request) {
        return Result.ok(dictService.createItem(request));
    }

    @Operation(summary = "修改字典项")
    @PreAuthorize("@pms.has('dict:manage')")
    @AuditLog(bizType = "dict", operation = "updateItem")
    @PutMapping("/item/{id}")
    public Result<Void> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemSaveRequest request) {
        dictService.updateItem(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除字典项")
    @PreAuthorize("@pms.has('dict:manage')")
    @AuditLog(bizType = "dict", operation = "deleteItem")
    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.ok();
    }
}
