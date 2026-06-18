-- =============================================================
-- V23 字段规范收口：按 refactor-ui-rbac-plan 附录 A 修正培养目标联动默认值。
-- V1-V22 冻结；本脚本幂等可重跑。
-- =============================================================

UPDATE training_goal_config
   SET default_internship_location = 'other',
       allowed_internship_locations_json = JSON_ARRAY('other'),
       updated_at = NOW(),
       deleted = 0,
       status = 1
 WHERE training_goal_code = 'secondary_vocational_school_teacher';
