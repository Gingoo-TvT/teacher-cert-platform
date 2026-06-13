package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.MajorTrainingGoal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MajorTrainingGoalMapper extends BaseMapper<MajorTrainingGoal> {

    @Select("""
            SELECT id, major_id, training_goal_code, sort, status,
                   created_by, created_at, updated_by, updated_at, deleted
              FROM major_training_goal
             WHERE major_id = #{majorId}
            """)
    List<MajorTrainingGoal> selectAllByMajorId(@Param("majorId") Long majorId);

    @Update("""
            UPDATE major_training_goal
               SET sort = #{sort},
                   status = #{status},
                   deleted = #{deleted},
                   updated_by = #{updatedBy},
                   updated_at = NOW()
             WHERE id = #{id}
            """)
    int updateIncludingDeleted(MajorTrainingGoal entity);
}
