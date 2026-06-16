package cn.edu.gpnu.platform.business.material.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_material")
public class ProcessMaterial extends BaseEntity {

    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String category;
    private Long fileId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Long uploaderId;
    private LocalDateTime uploadTime;
    private String firstReviewStatus;
    private Long firstReviewerId;
    private LocalDateTime firstReviewTime;
    private String firstReviewComment;
    private String secondReviewStatus;
    private Long secondReviewerId;
    private LocalDateTime secondReviewTime;
    private String secondReviewComment;
    private String status;
    private Integer locked;
}
