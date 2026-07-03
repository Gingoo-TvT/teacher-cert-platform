-- Phase 42.1（P0-10 并发收尾）：唯一约束防并发重复创建（MySQL 生成列 + 活跃态才非空方案）
--
-- 背景/为何不能加普通唯一键：
--   certificate(student_id, assessment_year) 仅非唯一索引 → 两并发 generate 各自过 activeCertificate 快照预检、
--   都插入 → 同一 student-year 出现两张有效证书；student.id_card_no 仅非唯一 → 两并发建同证件号两人。
--   直接加普通唯一键不行：① reissue 合法地为同一 student-year 产生多张（VOIDED/REISSUED 原证 + 新活跃证书共存）；
--   ② 本库全程软删，deleted=1 的历史行会与新行撞唯一键（P0-14 教训：多数唯一键不含 deleted）。
-- 方案：加 STORED 生成列——仅"活跃"行取业务键、非活跃/软删行取 NULL；再对生成列建唯一索引。
--   MySQL 唯一索引对 NULL 视为互不相等（多 NULL 可共存）→ VOIDED/REISSUED/软删证书、软删学生均豁免，
--   只保证「每 student-year ≤1 张活跃证书」「每 id_card_no ≤1 名未删学生」。
--
-- 证书"活跃态"口径 = 源码 CertificateServiceImpl.activeCertificate（唯一真源）：
--   deleted=0 AND status NOT IN ('VOIDED','REISSUED')（@TableLogic 自动追加 deleted=0；notIn(VOIDED,REISSUED)）。
--   注意：本口径 **包含 WAIT_GENERATE**，与 activeCertificate/generate 的应用层预检完全一致，
--   刻意采用「NOT IN(VOIDED,REISSUED)」而非计划示例的「IN(GENERATED,ISSUED,EXPORTED,ARCHIVED)」，
--   以保证 DB 约束与应用层守卫口径严格对齐、不留缝隙（WAIT_GENERATE 实际不落库，两种写法对真实数据等价）。
--
-- 上线前存量违约前置检查（2026-07-04 在共享 dev 库 teacher_cert 上执行，均为空=无违约，方可建唯一索引）：
--   SELECT student_id,assessment_year,COUNT(*) c FROM certificate
--     WHERE deleted=0 AND status NOT IN ('VOIDED','REISSUED') GROUP BY 1,2 HAVING c>1;   -- 结果：空（certificate 0 行）
--   SELECT id_card_no,COUNT(*) c FROM student
--     WHERE deleted=0 AND id_card_no IS NOT NULL GROUP BY 1 HAVING c>1;                    -- 结果：空（未删学生证件号全唯一）
--   另在 tcp-mysql(8.0.46) 上以真实数据的抛弃型克隆表试跑本文件全部 DDL：两唯一索引均成功建立、语法有效。
-- 备注：id_card_no 为 18 位明文存储（无加密，见 student 实体/StudentServiceImpl.plainIdCard），可直接生成列唯一，无需摘要列。

-- 证书：仅"活跃"证书受唯一约束；VOIDED/REISSUED/软删 → active_key=NULL（可共存）
ALTER TABLE certificate ADD COLUMN active_key VARCHAR(48)
    GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status NOT IN ('VOIDED', 'REISSUED')
             THEN CONCAT(student_id, '-', assessment_year) END
    ) STORED COMMENT '活跃证书唯一键：仅未删且非作废/重开时=student_id-assessment_year，否则NULL（Phase42.1）';
CREATE UNIQUE INDEX uk_cert_active ON certificate (active_key);

-- 学生证件号：仅未删学生受唯一约束；软删 → idcard_key=NULL（多软删同证件号可共存）
ALTER TABLE student ADD COLUMN idcard_key VARCHAR(64)
    GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN id_card_no END
    ) STORED COMMENT '未删学生证件号唯一键：deleted=0 时=id_card_no，否则NULL（Phase42.1）';
CREATE UNIQUE INDEX uk_student_idcard ON student (idcard_key);
