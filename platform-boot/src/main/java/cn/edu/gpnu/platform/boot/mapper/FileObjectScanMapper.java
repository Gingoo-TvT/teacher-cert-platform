package cn.edu.gpnu.platform.boot.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 47（P1-9 定时清理）：孤儿 file_object 扫描（<b>仅报告、绝不删除</b>）。
 *
 * <p>放在 platform-boot 是因为「孤儿」判定需跨模块的业务表引用知识——而本模块正是聚合全部迁移/表结构处。
 * file_object 被 5 处业务外键引用：process_material.file_id、exemption_material.file_id、
 * video_upload_session.file_id、video_review.video_file_id、import_export_batch.error_report_file_id
 * （已对 V11–V17 迁移与各实体 fileId 字段逐一核验）。无任一引用即视为孤儿。
 *
 * <p>查询保守：① 跳过逻辑已删（deleted=1）行；② 跳过 grace 窗口内的新上传（upload_time 近于 cutoff，
 * 避免误报「已入库 file_object、业务行尚未提交」的在途上传）；③ NOT EXISTS 不过滤业务侧 deleted，
 * 即被软删业务行引用的文件仍算「有引用」（宁可少报，不误伤）。仅统计/取样上报，交运维人工核查。
 */
public interface FileObjectScanMapper {

    /** 孤儿判定谓词（编译期常量，供 count 与 sample 复用）。 */
    String ORPHAN_PREDICATE =
            " FROM file_object fo"
            + " WHERE fo.deleted = 0"
            + " AND (fo.upload_time IS NULL OR fo.upload_time < #{graceCutoff})"
            + " AND NOT EXISTS (SELECT 1 FROM process_material pm WHERE pm.file_id = fo.id)"
            + " AND NOT EXISTS (SELECT 1 FROM exemption_material em WHERE em.file_id = fo.id)"
            + " AND NOT EXISTS (SELECT 1 FROM video_upload_session vus WHERE vus.file_id = fo.id)"
            + " AND NOT EXISTS (SELECT 1 FROM video_review vr WHERE vr.video_file_id = fo.id)"
            + " AND NOT EXISTS (SELECT 1 FROM import_export_batch ieb WHERE ieb.error_report_file_id = fo.id)";

    /** 统计无业务引用的 file_object 行数（早于 graceCutoff、未逻辑删除）。 */
    @Select("SELECT COUNT(*)" + ORPHAN_PREDICATE)
    long countOrphans(@Param("graceCutoff") LocalDateTime graceCutoff);

    /** 取样若干孤儿 id 供运维核查（不返回全量，避免海量）。 */
    @Select("SELECT fo.id" + ORPHAN_PREDICATE + " ORDER BY fo.id LIMIT #{limit}")
    List<Long> sampleOrphanIds(@Param("graceCutoff") LocalDateTime graceCutoff, @Param("limit") int limit);
}
