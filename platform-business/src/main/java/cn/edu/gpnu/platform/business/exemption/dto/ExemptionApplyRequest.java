package cn.edu.gpnu.platform.business.exemption.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ExemptionApplyRequest {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "考核年度不能为空")
    private String assessmentYear;

    @NotBlank(message = "任教学段不能为空")
    private String teachingSegment;

    @Valid
    @NotEmpty(message = "免考科目不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank(message = "免考科目不能为空")
        private String subject;

        @NotBlank(message = "免考依据不能为空")
        private String basis;

        private String remark;
    }
}
