package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubjectSelectRequest {

    @NotBlank(message = "任教学段不能为空")
    private String segmentCode;

    @NotBlank(message = "任教学科编码不能为空")
    private String subjectCode;

    private String yearVersion;
}
