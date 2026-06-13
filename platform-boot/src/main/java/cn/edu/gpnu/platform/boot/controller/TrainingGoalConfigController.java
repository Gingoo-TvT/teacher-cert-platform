package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.dto.TrainingGoalConfigSaveRequest;
import cn.edu.gpnu.platform.system.service.OrganizationService;
import cn.edu.gpnu.platform.system.vo.TrainingGoalConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "培养目标联动配置")
@RestController
@RequestMapping("/api/training-goal-config")
@RequiredArgsConstructor
public class TrainingGoalConfigController {

    private final OrganizationService organizationService;

    @Operation(summary = "查询培养目标联动配置")
    @DataScope(alias = "training_goal_config")
    @GetMapping
    public Result<List<TrainingGoalConfigVO>> list(@RequestParam(value = "trainingGoalCode", required = false)
                                                   String trainingGoalCode) {
        return Result.ok(organizationService.listTrainingGoalConfigs(trainingGoalCode));
    }

    @Operation(summary = "保存培养目标联动配置")
    @AuditLog(bizType = "trainingGoalConfig", operation = "save")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody TrainingGoalConfigSaveRequest request) {
        organizationService.saveTrainingGoalConfig(request);
        return Result.ok();
    }
}
