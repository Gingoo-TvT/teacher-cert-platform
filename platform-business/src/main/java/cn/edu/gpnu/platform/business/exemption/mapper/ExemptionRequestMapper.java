package cn.edu.gpnu.platform.business.exemption.mapper;

import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ExemptionRequestMapper extends BaseMapper<ExemptionRequest> {

    @Select("""
            SELECT *
            FROM exemption_request
            WHERE id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    ExemptionRequest selectByIdForUpdate(@Param("id") Long id);
}
