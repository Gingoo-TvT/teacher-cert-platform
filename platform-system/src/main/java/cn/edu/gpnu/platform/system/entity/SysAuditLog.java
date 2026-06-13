package cn.edu.gpnu.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志（追加写，无逻辑删除）。
 */
@Data
@TableName("audit_log")
public class SysAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String bizType;
    private Long bizId;
    private String target;
    private Long operatorId;
    private LocalDateTime operateTime;
    private String comment;
    private String oldStatus;
    private String newStatus;
    private String operation;
    private String ip;
}
