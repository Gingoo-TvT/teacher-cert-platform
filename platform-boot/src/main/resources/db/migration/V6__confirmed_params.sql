-- =============================================================
-- V6 待确认事项落地（学校书面确认：确认单 2026-06-14）
-- 来源：docs/待确认事项确认单.md（20 项已确认）。
-- 取值变更仅 1 处：姓名校验模式 strict→loose
--   （确认单#15 选“放宽”，兼容少数民族/外文姓名；plan §15.4 loose 模式）。
-- 其余 19 项均确认采用既有默认值（cert.seq.scope=SCHOOL_YEAR_SEGMENT、
--   video.passLine=60、review.return.target=FIRST_REVIEW、validate.idcard.checksum=false 等
--   保持不变），无需改参数。
-- 幂等：UPDATE 可重复执行。
-- =============================================================
UPDATE sys_param
   SET param_value = 'loose', updated_at = NOW()
 WHERE param_key = 'validate.name.mode';
