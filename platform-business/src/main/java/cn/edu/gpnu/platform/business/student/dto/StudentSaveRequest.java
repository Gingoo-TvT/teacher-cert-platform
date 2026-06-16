package cn.edu.gpnu.platform.business.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentSaveRequest {

    @NotBlank(message = "学号不能为空")
    @Size(max = 64, message = "学号过长")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 128, message = "姓名过长")
    private String name;

    @NotBlank(message = "性别不能为空")
    private String gender;

    @NotBlank(message = "证件类型不能为空")
    private String idCardType;

    @NotBlank(message = "证件号码不能为空")
    @Size(max = 64, message = "证件号码过长")
    private String idCardNo;

    @NotBlank(message = "出生日期不能为空")
    @Size(max = 32, message = "出生日期过长")
    private String birthDate;

    @NotBlank(message = "身份类型不能为空")
    private String identityType;

    private String sourceProvince;
    private String sourceCity;
    private String sourceCounty;
    private String sourceFull;

    @NotNull(message = "学院不能为空")
    private Long collegeId;

    @Size(max = 32, message = "年级过长")
    private String grade;

    @Size(max = 128, message = "班级过长")
    private String className;
}
