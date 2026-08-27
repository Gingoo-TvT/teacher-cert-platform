package cn.edu.gpnu.platform.business.certificate.mapper;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CertificateMapper extends BaseMapper<Certificate> {

    @Select("""
            SELECT *
              FROM certificate
             WHERE id = #{id}
               AND deleted = 0
             FOR UPDATE
            """)
    Certificate selectByIdForUpdate(@Param("id") Long id);
}
