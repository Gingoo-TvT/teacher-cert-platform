package cn.edu.gpnu.platform.business.testresult.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AbilityTestImportRequest {

    @Valid
    @NotEmpty(message = "导入记录不能为空")
    private List<AbilityTestSaveRequest> rows;
}
