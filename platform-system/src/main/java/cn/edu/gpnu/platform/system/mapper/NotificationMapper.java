package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * Phase 44（PG-M3 整改）：站内信真批量写——一条 multi-values {@code INSERT}，走<b>当前 SqlSession/连接</b>，
     * 因此完整参与外层 {@code @Transactional}（与 44a 的 {@code Db.saveBatch} 相反：后者以 BATCH 执行器另开
     * SqlSession/连接、不参与外层事务，与外层事务持有的行锁互等，间歇触发 {@code Lock wait timeout}）。
     *
     * <p><b>本方法保持单个无 {@code @Param} 的 List 参数</b>：MyBatis 会将其包装为含
     * {@code list}/{@code collection} 的 ParamMap，因此下方 {@code foreach collection='list'} 与当前签名匹配。
     * 若未来改为 {@code @Param("rows")}，必须同步把 foreach 改为 {@code collection='rows'}，这是 MyBatis 参数绑定合同。
     * 它与 MyBatis-Plus 自动填充是两回事：锁定的 MP 3.5.16 在
     * {@code MybatisParameterHandler.processParameter -> extractParameters} 中会遍历 ParamMap 的全部 value、去重并展开
     * collection，所以 {@code @Param("rows")} 本身<b>不会</b>破坏逐元素
     * {@code populateKeys}/{@code MetaObjectHandler.insertFill}。
     *
     * <p>{@code deleted} 列刻意不出现在列清单里：交由 DDL 的 {@code DEFAULT 0}，与 MP 单行 insert 忽略 null
     * 字段的行为一致。可空列显式声明 {@code jdbcType}，避免 null 走 {@code JdbcType.OTHER} 依赖驱动实现。
     *
     * <p><b>为什么不用 MP 内置的批量 API</b>：3.5.16 的 {@code BaseMapper.insert(Collection)} 与
     * {@code Db.saveBatch} 一样经 {@code MybatisBatchUtils.execute(SqlSessionFactory, ...)} 从工厂<b>新开</b>
     * SqlSession（BATCH 执行器），正是 44a 事故的成因；且 JDBC batch 仍是 N 条语句，不是 multi-values。
     *
     * @param rows 待插入通知（调用方保证非空、且已完成字段裁剪/兜底）
     * @return 实际插入行数
     */
    @Insert("<script>"
            + "INSERT INTO notification (id, user_id, type, title, content, biz_type, biz_id, read_flag,"
            + " created_by, created_at, updated_by, updated_at) VALUES "
            + "<foreach collection='list' item='row' separator=','>"
            + "(#{row.id,jdbcType=BIGINT}, #{row.userId,jdbcType=BIGINT}, #{row.type,jdbcType=VARCHAR},"
            + " #{row.title,jdbcType=VARCHAR}, #{row.content,jdbcType=VARCHAR}, #{row.bizType,jdbcType=VARCHAR},"
            + " #{row.bizId,jdbcType=VARCHAR}, #{row.readFlag,jdbcType=TINYINT},"
            + " #{row.createdBy,jdbcType=BIGINT}, #{row.createdAt,jdbcType=TIMESTAMP},"
            + " #{row.updatedBy,jdbcType=BIGINT}, #{row.updatedAt,jdbcType=TIMESTAMP})"
            + "</foreach>"
            + "</script>")
    int insertBatch(List<Notification> rows);

    /**
     * Phase 47（P1-9 定时清理）：物理删除 created_at 早于 cutoff 的通知，单次至多 batchSize 行。
     * 刻意走原生 SQL——绕开实体 {@code @TableLogic} 的软删（UPDATE deleted=1），对过期运营数据做真物理
     * 删除以真正回收空间（不涉及业务数据）；LIMIT 分批（调用方循环）避免大清理长时间锁表。
     *
     * @return 本次实际删除行数（小于 batchSize 即表示已删尽）
     */
    @Delete("DELETE FROM notification WHERE created_at < #{cutoff} LIMIT #{batchSize}")
    int deletePhysicalOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
