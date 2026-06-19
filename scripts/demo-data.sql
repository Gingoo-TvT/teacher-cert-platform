-- =============================================================
-- 演示/测试数据（DEMO ONLY）—— 故意「独立」于生产
--   · 不是 Flyway 迁移：放在 scripts/ 下，生产启动/Flyway 不会运行它，上线零污染。
--   · 仅在开发/验收库手动执行：
--       docker exec -i tcp-mysql mysql --default-character-set=utf8mb4 -uroot -proot123 teacher_cert < scripts/demo-data.sql
--   · 幂等：本脚本先清理 DEMO 数据再插入，可反复运行。
--   · 独立 id 区段 99xxxxxxxxxxxxxxxx + 学号前缀 DEMO，便于识别与清理。
--   · 清理（不想要时）：
--       DELETE FROM training_profile WHERE id BETWEEN 990000000000010000 AND 990000000000019999;
--       DELETE FROM student          WHERE id BETWEEN 990000000000000000 AND 990000000000009999;
-- =============================================================
SET NAMES utf8mb4;

-- 幂等清理（按 DEMO id 区段）
DELETE FROM training_profile WHERE id BETWEEN 990000000000010000 AND 990000000000019999;
DELETE FROM student          WHERE id BETWEEN 990000000000000000 AND 990000000000009999;

-- 学生（8 个学院A 800000000000000201 + 2 个学院B 800000000000000202，含多种状态）
-- 状态：DRAFT 草稿 / FIRST_REVIEW 待初审(教务员) / SECOND_REVIEW 待复审(负责人) / PASSED 复审通过 / FIRST_REJECTED 初审退回
INSERT INTO student
 (id, student_no, name, gender, id_card_type, id_card_no, birth_date, identity_type,
  source_province, source_city, source_county, source_full, college_id, grade, class_name,
  status, locked, created_by, created_at, updated_by, updated_at, deleted)
VALUES
 (990000000000000001,'DEMO2026001','张伟','male','resident_id_card','440106200203150011','2002/3/15','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范一班','DRAFT',0,0,NOW(),0,NOW(),0),
 (990000000000000002,'DEMO2026002','李娜','female','resident_id_card','440106200205120024','2002/5/12','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范一班','FIRST_REVIEW',0,0,NOW(),0,NOW(),0),
 (990000000000000003,'DEMO2026003','王芳','female','resident_id_card','440106200108090046','2001/8/9','public_funded_normal','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范一班','FIRST_REVIEW',0,0,NOW(),0,NOW(),0),
 (990000000000000004,'DEMO2026004','刘洋','male','resident_id_card','440106200111230058','2001/11/23','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范二班','SECOND_REVIEW',0,0,NOW(),0,NOW(),0),
 (990000000000000005,'DEMO2026005','陈静','female','resident_id_card','440106200202180061','2002/2/18','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范二班','PASSED',0,0,NOW(),0,NOW(),0),
 (990000000000000006,'DEMO2026006','杨光','male','resident_id_card','440106200107300073','2001/7/30','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范二班','PASSED',0,0,NOW(),0,NOW(),0),
 (990000000000000007,'DEMO2026007','赵敏','female','resident_id_card','440106200209050085','2002/9/5','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范三班','FIRST_REJECTED',0,0,NOW(),0,NOW(),0),
 (990000000000000008,'DEMO2026008','周强','male','resident_id_card','440106200112110097','2001/12/11','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000201,'2026','示范三班','DRAFT',0,0,NOW(),0,NOW(),0),
 (990000000000000009,'DEMO2026009','吴婷','female','resident_id_card','440106200204220102','2002/4/22','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000202,'2026','示范B班','FIRST_REVIEW',0,0,NOW(),0,NOW(),0),
 (990000000000000010,'DEMO2026010','郑浩','male','resident_id_card','440106200106170114','2001/6/17','normal_student','440000','440100','440106','广东省/广州市/天河区',800000000000000202,'2026','示范B班','DRAFT',0,0,NOW(),0,NOW(),0);

-- 专业培养信息（学院A 5 名，初中语文/普通师范 P4_NORMAL_A，多种状态，考核年度 2026）
INSERT INTO training_profile
 (id, student_id, college_id, assessment_year, second_discipline_code, second_discipline_name,
  internal_major_code, internal_major_name, education_level, training_goal,
  internship_org_mode, internship_location, teaching_segment,
  teaching_subject_id, teaching_subject_code, teaching_subject_name, interview_org_mode,
  status, locked, created_by, created_at, updated_by, updated_at, deleted)
VALUES
 (990000000000010001,990000000000000002,800000000000000201,'2026','050101','汉语言文学','P4_NORMAL_A','Phase4普通师范试点专业A','bachelor','junior_middle_school_teacher','school_organized','primary_secondary_school','junior_middle_school',500000000000000201,'jms_chinese','语文','separate_interview','FIRST_REVIEW',0,0,NOW(),0,NOW(),0),
 (990000000000010002,990000000000000003,800000000000000201,'2026','050101','汉语言文学','P4_NORMAL_A','Phase4普通师范试点专业A','bachelor','junior_middle_school_teacher','school_organized','primary_secondary_school','junior_middle_school',500000000000000202,'jms_math','数学','separate_interview','DRAFT',0,0,NOW(),0,NOW(),0),
 (990000000000010003,990000000000000004,800000000000000201,'2026','050101','汉语言文学','P4_NORMAL_A','Phase4普通师范试点专业A','bachelor','junior_middle_school_teacher','school_organized','primary_secondary_school','junior_middle_school',500000000000000201,'jms_chinese','语文','separate_interview','SECOND_REVIEW',0,0,NOW(),0,NOW(),0),
 (990000000000010004,990000000000000005,800000000000000201,'2026','050101','汉语言文学','P4_NORMAL_A','Phase4普通师范试点专业A','bachelor','junior_middle_school_teacher','school_organized','primary_secondary_school','junior_middle_school',500000000000000203,'jms_english','英语','separate_interview','PASSED',0,0,NOW(),0,NOW(),0),
 (990000000000010005,990000000000000006,800000000000000201,'2026','050101','汉语言文学','P4_NORMAL_A','Phase4普通师范试点专业A','bachelor','junior_middle_school_teacher','school_organized','primary_secondary_school','junior_middle_school',500000000000000201,'jms_chinese','语文','separate_interview','PASSED',0,0,NOW(),0,NOW(),0);
