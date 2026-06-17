package cn.edu.gpnu.platform.business.certificate.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cert_sequence")
public class CertSequence extends BaseEntity {

    private String scopeKey;
    private Integer currentSeq;
}
