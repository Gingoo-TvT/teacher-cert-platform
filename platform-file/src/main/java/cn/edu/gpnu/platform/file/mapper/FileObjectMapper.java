package cn.edu.gpnu.platform.file.mapper;

import cn.edu.gpnu.platform.file.entity.FileObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface FileObjectMapper extends BaseMapper<FileObject> {

    @Update("""
            UPDATE file_object
               SET status = #{targetStatus},
                   updated_at = #{updatedAt}
             WHERE id = #{id}
               AND status = #{expectedStatus}
               AND deleted = 0
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("targetStatus") String targetStatus,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
