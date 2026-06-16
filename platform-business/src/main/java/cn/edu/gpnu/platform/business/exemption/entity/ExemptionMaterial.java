package cn.edu.gpnu.platform.business.exemption.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exemption_material")
public class ExemptionMaterial extends BaseEntity {

    private Long exemptionRequestId;
    private Long studentId;
    private Long collegeId;
    private Long fileId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Long uploaderId;
    private LocalDateTime uploadTime;
}
