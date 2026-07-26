-- =============================================================
-- Phase 53：各角色 demo/mock 数据（仅 TEST/DEV，platform.demo.enabled=true 时由 DemoDataInitializer 执行）
--
-- 目的：测试者以各角色登录 dev 环境时看到「有内容」的界面（学生列表/审核队列/视频评审任务/
--       测试成绩/证书/站内信），而非空屏。本脚本只插入「演示实体数据」，样例文件（PDF/图片/MP4）
--       由 DemoDataInitializer 幂等上传到按内容 SHA-256 派生的 MinIO object key（见下方 file_path）。
--
-- 关键约束（务必遵守，勿破坏 IT 套件）：
--   · 本脚本【不是】Flyway 迁移，不参与 mvn verify；仅在 platform.demo.enabled=true 时运行。
--   · 全部使用【独立固定 id 段】，与基础 testseed(学院201/202、学生9001/9002、test_* 账号)及
--     IT 运行期动态行(9001/9002/雪花id)互不冲突：
--       - 演示学生 student.id     = 9101..9108
--       - 演示实体(其余表)id 前缀 = 8_100_000_000_000_0xxx（16 位，远小于 testseed 的 18 位 8.x/1.2x/3.x/5.x 段）
--   · 全部 INSERT ... ON DUPLICATE KEY UPDATE，可安全重跑（幂等，不产生重复行）。
--   · 引用的学院(201/202)、角色(STUDENT)、字典项(学段/学科/材料类别/证书状态等)均来自基础种子，需 dev 环境已加载。
--   · student.idcard_key / certificate.active_key 为 STORED 生成列，INSERT 不显式赋值（由 MySQL 计算）。
--   · DEMO_* 占位符由 DemoDataInitializer 在全部对象内容对账 + 受信媒体探测通过后注入；
--     禁止手写视频大小、指纹、时长、编码、策略哈希或探测器版本。
--
-- 年度统一 assessment_year='2026'（与 current_assessment_year 参数默认一致，保证 UI 默认年度筛选可见）。
-- =============================================================

-- =====================================================================================
-- 0) 演示学生登录账号 demo_student（用于「学生」角色看到完整本人视图；不改动基础 test_student→9001 绑定）
--    密码 = bcrypt("ChangeMe123!")（与 test_* 一致），must_change_pwd=0（免首登改密，演示顺滑）。
-- =====================================================================================
INSERT INTO sys_user
(id, username, password_hash, real_name, work_no, email, phone, status, user_type, college_id, student_id,
 last_login_at, must_change_pwd, failed_login_count, locked_until, created_at, updated_at, deleted)
VALUES
(8100000000000901, 'demo_student', '$2a$10$tzaemacCZVocmt9qla0G7Ou4bYh80qY5s9C23ViKO9FugAcTCvIOO',
 '演示学生-陈晓明', NULL, NULL, NULL, 'ENABLED', 'STUDENT', 800000000000000201, 9101,
 NULL, 0, 0, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash), real_name = VALUES(real_name), status = VALUES(status),
    user_type = VALUES(user_type), college_id = VALUES(college_id), student_id = VALUES(student_id),
    must_change_pwd = VALUES(must_change_pwd), updated_at = NOW(), deleted = 0;

INSERT INTO sys_user_role (id, user_id, role_id, created_at, updated_at, deleted)
SELECT 8100000000000902, 8100000000000901, r.id, NOW(), NOW(), 0
FROM sys_role r WHERE r.code = 'STUDENT' AND r.deleted = 0
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), role_id = VALUES(role_id), updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 1) 文件登记 file_object（bucket 与 object_key 由 DemoDataInitializer 按运行时配置/manifest 注入）
--    预览/播放走 FileService.presignedGet(fileId) → 读 file_object(bucket+object_key) → MinIO 预签名。
--    多行可共享同一 object_key（同一物理样例对象），演示足够且省存储。
-- =====================================================================================
INSERT INTO file_object
(id, original_name, stored_name, bucket, object_key, `size`, content_type, md5, biz_type, status,
 checksum_algorithm, content_hash_verified, media_codec, media_validation_policy_hash, media_probe_version,
 uploader_id, upload_time, created_at, updated_at, deleted)
VALUES
-- 教学能力视频（video/mp4），供 video_review.video_file_id 引用
(8100000000000001, '教学能力展示-陈晓明.mp4', 'demo-teaching-video.mp4', {{DEMO_BUCKET}}, {{DEMO_VIDEO_OBJECT_KEY}}, {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_CONTENT_TYPE}}, {{DEMO_VIDEO_FINGERPRINT}}, 'teaching-video', 'READY', {{DEMO_VIDEO_CHECKSUM_ALGORITHM}}, 1, {{DEMO_VIDEO_CODEC}}, {{DEMO_VIDEO_POLICY_HASH}}, {{DEMO_VIDEO_PROBE_VERSION}}, 800000000000003003, NOW(), NOW(), NOW(), 0),
(8100000000000002, '教学能力展示-周雅婷.mp4', 'demo-teaching-video.mp4', {{DEMO_BUCKET}}, {{DEMO_VIDEO_OBJECT_KEY}}, {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_CONTENT_TYPE}}, {{DEMO_VIDEO_FINGERPRINT}}, 'teaching-video', 'READY', {{DEMO_VIDEO_CHECKSUM_ALGORITHM}}, 1, {{DEMO_VIDEO_CODEC}}, {{DEMO_VIDEO_POLICY_HASH}}, {{DEMO_VIDEO_PROBE_VERSION}}, 800000000000003003, NOW(), NOW(), NOW(), 0),
(8100000000000003, '教学能力展示-郑丽华.mp4', 'demo-teaching-video.mp4', {{DEMO_BUCKET}}, {{DEMO_VIDEO_OBJECT_KEY}}, {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_CONTENT_TYPE}}, {{DEMO_VIDEO_FINGERPRINT}}, 'teaching-video', 'READY', {{DEMO_VIDEO_CHECKSUM_ALGORITHM}}, 1, {{DEMO_VIDEO_CODEC}}, {{DEMO_VIDEO_POLICY_HASH}}, {{DEMO_VIDEO_PROBE_VERSION}}, 800000000000003003, NOW(), NOW(), NOW(), 0),
-- 过程材料 PDF，供 process_material.file_id 引用
(8100000000000011, '教育实习实践证明.pdf', 'demo-material.pdf', {{DEMO_BUCKET}}, {{DEMO_MATERIAL_OBJECT_KEY}}, {{DEMO_MATERIAL_SIZE}}, {{DEMO_MATERIAL_CONTENT_TYPE}}, {{DEMO_MATERIAL_MD5}}, 'process-material', 'READY', NULL, 0, NULL, NULL, NULL, 800000000000003003, NOW(), NOW(), NOW(), 0),
(8100000000000013, '教师教育课程成绩单.pdf', 'demo-material.pdf', {{DEMO_BUCKET}}, {{DEMO_MATERIAL_OBJECT_KEY}}, {{DEMO_MATERIAL_SIZE}}, {{DEMO_MATERIAL_CONTENT_TYPE}}, {{DEMO_MATERIAL_MD5}}, 'process-material', 'READY', NULL, 0, NULL, NULL, NULL, 800000000000003003, NOW(), NOW(), NOW(), 0),
(8100000000000014, '专业技能培训证明.pdf', 'demo-material.pdf', {{DEMO_BUCKET}}, {{DEMO_MATERIAL_OBJECT_KEY}}, {{DEMO_MATERIAL_SIZE}}, {{DEMO_MATERIAL_CONTENT_TYPE}}, {{DEMO_MATERIAL_MD5}}, 'process-material', 'READY', NULL, 0, NULL, NULL, NULL, 800000000000003003, NOW(), NOW(), NOW(), 0),
-- 过程材料 图片（image/png）
(8100000000000012, '师德素养佐证.png', 'demo-material-image.png', {{DEMO_BUCKET}}, {{DEMO_IMAGE_OBJECT_KEY}}, {{DEMO_IMAGE_SIZE}}, {{DEMO_IMAGE_CONTENT_TYPE}}, {{DEMO_IMAGE_MD5}}, 'process-material', 'READY', NULL, 0, NULL, NULL, NULL, 800000000000003003, NOW(), NOW(), NOW(), 0),
-- 免考佐证 PDF，供 exemption_material.file_id 引用
(8100000000000021, '免考佐证-统考成绩单.pdf', 'demo-exemption.pdf', {{DEMO_BUCKET}}, {{DEMO_EXEMPTION_OBJECT_KEY}}, {{DEMO_EXEMPTION_SIZE}}, {{DEMO_EXEMPTION_CONTENT_TYPE}}, {{DEMO_EXEMPTION_MD5}}, 'exemption-material', 'READY', NULL, 0, NULL, NULL, NULL, 800000000000003003, NOW(), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    original_name = VALUES(original_name), stored_name = VALUES(stored_name), bucket = VALUES(bucket),
    object_key = VALUES(object_key), `size` = VALUES(`size`), content_type = VALUES(content_type),
    md5 = VALUES(md5), biz_type = VALUES(biz_type), status = VALUES(status),
    checksum_algorithm = VALUES(checksum_algorithm), content_hash_verified = VALUES(content_hash_verified),
    media_codec = VALUES(media_codec), media_validation_policy_hash = VALUES(media_validation_policy_hash),
    media_probe_version = VALUES(media_probe_version),
    uploader_id = VALUES(uploader_id), upload_time = VALUES(upload_time), updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 2) 演示学生 student（8 名，跨学院 201(A)/202(B)，覆盖多状态：DRAFT/FIRST_REVIEW/SECOND_REVIEW/FIRST_REJECTED/PASSED）
--    首审人=学院教务员 test_college_clerk(3003)，复审人=学院负责人 test_college_auditor(3004)。id_card_no 各不相同。
-- =====================================================================================
INSERT INTO student
(id, student_no, name, gender, id_card_type, id_card_no, birth_date, identity_type,
 source_province, source_city, source_county, source_full, college_id, grade, class_name,
 status, locked, first_reviewer_id, first_review_time, first_review_comment,
 second_reviewer_id, second_review_time, second_review_comment, created_at, updated_at, deleted)
VALUES
(9101, 'S9101', '陈晓明', 'male',   'resident_id_card', '440105200001010011', '2000/1/1',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000201, '2022', '师范1班', 'PASSED',         1, 800000000000003003, NOW(), '材料齐全，初审通过', 800000000000003004, NOW(), '复审通过，准予发证', NOW(), NOW(), 0),
(9102, 'S9102', '林芳',   'female', 'resident_id_card', '440105200102020022', '2001/2/2',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000201, '2022', '师范1班', 'DRAFT',          0, NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW(), 0),
(9103, 'S9103', '黄志强', 'male',   'resident_id_card', '440105200003030033', '2000/3/3',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000201, '2022', '师范2班', 'FIRST_REVIEW',   0, NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW(), 0),
(9104, 'S9104', '周雅婷', 'female', 'resident_id_card', '440105200104040044', '2001/4/4',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000201, '2022', '师范2班', 'SECOND_REVIEW',  0, 800000000000003003, NOW(), '初审通过，转复审', NULL, NULL, NULL, NOW(), NOW(), 0),
(9105, 'S9105', '吴俊杰', 'male',   'resident_id_card', '440105200005050055', '2000/5/5',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000201, '2022', '师范3班', 'FIRST_REJECTED', 0, 800000000000003003, NOW(), '实习证明缺章，请补充后重新提交', NULL, NULL, NULL, NOW(), NOW(), 0),
(9106, 'S9106', '郑丽华', 'female', 'resident_id_card', '440105200106060066', '2001/6/6',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000202, '2022', '师范1班', 'FIRST_REVIEW',   0, NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW(), 0),
(9107, 'S9107', '刘伟',   'male',   'resident_id_card', '440105200007070077', '2000/7/7',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000202, '2022', '师范1班', 'SECOND_REVIEW',  0, 800000000000003003, NOW(), '初审通过，转复审', NULL, NULL, NULL, NOW(), NOW(), 0),
(9108, 'S9108', '孙梦琪', 'female', 'resident_id_card', '440105200108080088', '2001/8/8',  'normal_student', '440000','440100','440105','广东省/广州市/海珠区', 800000000000000202, '2022', '师范2班', 'PASSED',         1, 800000000000003003, NOW(), '材料齐全，初审通过', 800000000000003004, NOW(), '复审通过，准予发证', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    student_no = VALUES(student_no), name = VALUES(name), gender = VALUES(gender), id_card_no = VALUES(id_card_no),
    college_id = VALUES(college_id), grade = VALUES(grade), class_name = VALUES(class_name), status = VALUES(status),
    locked = VALUES(locked), first_reviewer_id = VALUES(first_reviewer_id), first_review_time = VALUES(first_review_time),
    first_review_comment = VALUES(first_review_comment), second_reviewer_id = VALUES(second_reviewer_id),
    second_review_time = VALUES(second_review_time), second_review_comment = VALUES(second_review_comment),
    updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 3) 专业培养信息 training_profile（7 名，跳过 DRAFT 的 9102 以呈现「刚建档」学生）
-- =====================================================================================
INSERT INTO training_profile
(id, student_id, college_id, assessment_year, second_discipline_code, second_discipline_name,
 internal_major_code, internal_major_name, education_level, training_goal, internship_org_mode,
 internship_location, teaching_segment, teaching_subject_id, teaching_subject_code, teaching_subject_name,
 interview_org_mode, ability_test_conclusion, status, locked, created_at, updated_at, deleted)
VALUES
(8100000000001101, 9101, 800000000000000201, '2026', '050101', '汉语言文学', 'P4_NORMAL_A', 'Phase4普通师范试点专业A', 'bachelor', 'primary_school_teacher',       'school_organized', 'primary_secondary_school', 'primary_school',       500000000000000101, 'ps_chinese', '语文', 'separate_interview', 'qualified', 'PASSED',        1, NOW(), NOW(), 0),
(8100000000001103, 9103, 800000000000000201, '2026', '050101', '汉语言文学', 'P4_NORMAL_A', 'Phase4普通师范试点专业A', 'bachelor', 'primary_school_teacher',       'school_organized', 'primary_secondary_school', 'primary_school',       500000000000000102, 'ps_math',    '数学', 'separate_interview', NULL,        'FIRST_REVIEW',  0, NOW(), NOW(), 0),
(8100000000001104, 9104, 800000000000000201, '2026', '050101', '汉语言文学', 'P4_NORMAL_A', 'Phase4普通师范试点专业A', 'bachelor', 'junior_middle_school_teacher', 'school_organized', 'primary_secondary_school', 'junior_middle_school', 500000000000000201, 'jms_chinese','语文', 'separate_interview', NULL,        'SECOND_REVIEW', 0, NOW(), NOW(), 0),
(8100000000001105, 9105, 800000000000000201, '2026', '050101', '汉语言文学', 'P4_NORMAL_A', 'Phase4普通师范试点专业A', 'bachelor', 'primary_school_teacher',       'school_organized', 'primary_secondary_school', 'primary_school',       500000000000000103, 'ps_english', '英语', 'separate_interview', NULL,        'FIRST_REJECTED',0, NOW(), NOW(), 0),
(8100000000001106, 9106, 800000000000000202, '2026', '070101', '数学与应用数学', 'P4_NORMAL_B', 'Phase4普通师范试点专业B', 'bachelor', 'junior_middle_school_teacher', 'school_organized', 'primary_secondary_school', 'junior_middle_school', 500000000000000202, 'jms_math',   '数学', 'separate_interview', NULL,        'FIRST_REVIEW',  0, NOW(), NOW(), 0),
(8100000000001107, 9107, 800000000000000202, '2026', '070101', '数学与应用数学', 'P4_NORMAL_B', 'Phase4普通师范试点专业B', 'bachelor', 'junior_middle_school_teacher', 'school_organized', 'primary_secondary_school', 'junior_middle_school', 500000000000000202, 'jms_math',   '数学', 'separate_interview', 'exempted',  'SECOND_REVIEW', 0, NOW(), NOW(), 0),
(8100000000001108, 9108, 800000000000000202, '2026', '070101', '数学与应用数学', 'P4_NORMAL_B', 'Phase4普通师范试点专业B', 'bachelor', 'junior_middle_school_teacher', 'school_organized', 'primary_secondary_school', 'junior_middle_school', 500000000000000202, 'jms_math',   '数学', 'separate_interview', 'qualified', 'PASSED',        1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    college_id = VALUES(college_id), education_level = VALUES(education_level), training_goal = VALUES(training_goal),
    internship_org_mode = VALUES(internship_org_mode), internship_location = VALUES(internship_location),
    teaching_segment = VALUES(teaching_segment), teaching_subject_id = VALUES(teaching_subject_id),
    teaching_subject_code = VALUES(teaching_subject_code), teaching_subject_name = VALUES(teaching_subject_name),
    interview_org_mode = VALUES(interview_org_mode), ability_test_conclusion = VALUES(ability_test_conclusion),
    status = VALUES(status), locked = VALUES(locked), updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 4) 过程性材料 process_material（引用 file_object，file_path=object_key，预览可用）
-- =====================================================================================
INSERT INTO process_material
(id, student_id, college_id, assessment_year, category, file_id, file_name, file_path, file_size, content_type,
 uploader_id, upload_time, first_review_status, first_reviewer_id, first_review_time,
 second_review_status, second_reviewer_id, second_review_time, status, locked, created_at, updated_at, deleted)
VALUES
(8100000000002001, 9101, 800000000000000201, '2026', 'education_internship_practice', 8100000000000011, '教育实习实践证明.pdf', {{DEMO_MATERIAL_OBJECT_KEY}}, {{DEMO_MATERIAL_SIZE}}, {{DEMO_MATERIAL_CONTENT_TYPE}}, 8100000000000901, NOW(), 'PASS', 800000000000003003, NOW(), 'PASS', 800000000000003004, NOW(), 'PASSED',       1, NOW(), NOW(), 0),
(8100000000002002, 9101, 800000000000000201, '2026', 'morality_teacher_ethics',       8100000000000012, '师德素养佐证.png',     {{DEMO_IMAGE_OBJECT_KEY}}, {{DEMO_IMAGE_SIZE}}, {{DEMO_IMAGE_CONTENT_TYPE}}, 8100000000000901, NOW(), 'PASS', 800000000000003003, NOW(), 'PASS', 800000000000003004, NOW(), 'PASSED',       1, NOW(), NOW(), 0),
(8100000000002003, 9103, 800000000000000201, '2026', 'teacher_education_course',       8100000000000013, '教师教育课程成绩单.pdf', {{DEMO_MATERIAL_OBJECT_KEY}}, {{DEMO_MATERIAL_SIZE}}, {{DEMO_MATERIAL_CONTENT_TYPE}}, 800000000000003003, NOW(), NULL,   NULL, NULL, NULL, NULL, NULL, 'FIRST_REVIEW', 0, NOW(), NOW(), 0),
(8100000000002004, 9106, 800000000000000202, '2026', 'professional_ability_skill_training', 8100000000000014, '专业技能培训证明.pdf', {{DEMO_MATERIAL_OBJECT_KEY}}, {{DEMO_MATERIAL_SIZE}}, {{DEMO_MATERIAL_CONTENT_TYPE}}, 800000000000003003, NOW(), NULL,   NULL, NULL, NULL, NULL, NULL, 'FIRST_REVIEW', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    category = VALUES(category), file_id = VALUES(file_id), file_name = VALUES(file_name), file_path = VALUES(file_path),
    file_size = VALUES(file_size), content_type = VALUES(content_type), first_review_status = VALUES(first_review_status),
    second_review_status = VALUES(second_review_status), status = VALUES(status), locked = VALUES(locked),
    updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 5) 免考申请 exemption_request + 佐证 exemption_material（学生 9107，已通过）
-- =====================================================================================
INSERT INTO exemption_request
(id, student_id, college_id, assessment_year, teaching_segment, subject, subject_label, basis, basis_label, remark,
 first_review_status, first_reviewer_id, first_review_time, second_review_status, second_reviewer_id, second_review_time,
 final_status, included_in_exam, locked, created_at, updated_at, deleted)
VALUES
(8100000000003001, 9107, 800000000000000202, '2026', 'junior_middle_school', 'subject_knowledge_junior', '学科知识与教学能力（初中）', 'policy_exemption', '政策规定免考', '已通过国家统考对应科目，申请免考',
 'PASS', 800000000000003003, NOW(), 'PASS', 800000000000003004, NOW(), 'PASSED', 0, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    teaching_segment = VALUES(teaching_segment), subject = VALUES(subject), subject_label = VALUES(subject_label),
    basis = VALUES(basis), basis_label = VALUES(basis_label), remark = VALUES(remark),
    first_review_status = VALUES(first_review_status), second_review_status = VALUES(second_review_status),
    final_status = VALUES(final_status), included_in_exam = VALUES(included_in_exam), locked = VALUES(locked),
    updated_at = NOW(), deleted = 0;

INSERT INTO exemption_material
(id, exemption_request_id, student_id, college_id, file_id, file_name, file_path, file_size, content_type,
 uploader_id, upload_time, created_at, updated_at, deleted)
VALUES
(8100000000003101, 8100000000003001, 9107, 800000000000000202, 8100000000000021, '免考佐证-统考成绩单.pdf', {{DEMO_EXEMPTION_OBJECT_KEY}}, {{DEMO_EXEMPTION_SIZE}}, {{DEMO_EXEMPTION_CONTENT_TYPE}}, 800000000000003003, NOW(), NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    exemption_request_id = VALUES(exemption_request_id), file_id = VALUES(file_id), file_name = VALUES(file_name),
    file_path = VALUES(file_path), file_size = VALUES(file_size), content_type = VALUES(content_type),
    updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 6) 教学能力视频评审 video_review + 会话 video_upload_session + 评审任务 video_review_task
--    9101 已完成(CONFIRMED/PASS)；9104(REVIEWING)与9107(WAIT_REVIEW,跨学院)各有一条【待办】任务分配给
--    test_review_teacher(3005,submitted=0)——评审教师登录即见待办、可播放/下载视频；9101 另有一条已提交任务。
-- =====================================================================================
INSERT INTO video_review
(id, student_id, college_id, assessment_year, video_file_id, video_file_name, file_md5, duration_seconds,
 format_check, validation_message, status, final_score, final_conclusion, confirmed_by, confirmed_at, locked, created_at, updated_at, deleted)
VALUES
(8100000000004001, 9101, 800000000000000201, '2026', 8100000000000001, '教学能力展示-陈晓明.mp4', {{DEMO_VIDEO_FINGERPRINT}}, {{DEMO_VIDEO_DURATION_SECONDS}}, 'PASS', '服务端媒体探测通过', 'CONFIRMED',   85, 'PASS', 800000000000003004, NOW(), 1, NOW(), NOW(), 0),
(8100000000004002, 9104, 800000000000000201, '2026', 8100000000000002, '教学能力展示-周雅婷.mp4', {{DEMO_VIDEO_FINGERPRINT}}, {{DEMO_VIDEO_DURATION_SECONDS}}, 'PASS', '服务端媒体探测通过', 'REVIEWING',   NULL, NULL, NULL, NULL, 0, NOW(), NOW(), 0),
(8100000000004003, 9107, 800000000000000202, '2026', 8100000000000003, '教学能力展示-郑丽华.mp4', {{DEMO_VIDEO_FINGERPRINT}}, {{DEMO_VIDEO_DURATION_SECONDS}}, 'PASS', '服务端媒体探测通过', 'WAIT_REVIEW', NULL, NULL, NULL, NULL, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    video_file_id = VALUES(video_file_id), video_file_name = VALUES(video_file_name), file_md5 = VALUES(file_md5),
    duration_seconds = VALUES(duration_seconds), format_check = VALUES(format_check),
    validation_message = VALUES(validation_message), status = VALUES(status),
    final_score = VALUES(final_score), final_conclusion = VALUES(final_conclusion), confirmed_by = VALUES(confirmed_by),
    confirmed_at = VALUES(confirmed_at), locked = VALUES(locked), updated_at = NOW(), deleted = 0;

INSERT INTO video_upload_session
(id, upload_id, upload_mode, s3_upload_id, object_key, presign_expires_at, slot_claimed,
 student_id, college_id, assessment_year, file_md5, file_name, file_size, content_type,
 chunk_size, total_chunks, uploaded_chunks, uploaded_bytes, duration_seconds, status, file_id, validation_message, created_at, updated_at, deleted)
VALUES
(8100000000004201, 'demo-upload-9101', 'SERVER_CHUNK', NULL, {{DEMO_VIDEO_OBJECT_KEY}}, NULL, 0, 9101, 800000000000000201, '2026', {{DEMO_VIDEO_FINGERPRINT}}, '教学能力展示-陈晓明.mp4', {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_CONTENT_TYPE}}, 8388608, 1, 1, {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_DURATION_SECONDS}}, 'MERGED', 8100000000000001, '服务端媒体探测通过', NOW(), NOW(), 0),
(8100000000004202, 'demo-upload-9104', 'SERVER_CHUNK', NULL, {{DEMO_VIDEO_OBJECT_KEY}}, NULL, 0, 9104, 800000000000000201, '2026', {{DEMO_VIDEO_FINGERPRINT}}, '教学能力展示-周雅婷.mp4', {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_CONTENT_TYPE}}, 8388608, 1, 1, {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_DURATION_SECONDS}}, 'MERGED', 8100000000000002, '服务端媒体探测通过', NOW(), NOW(), 0),
(8100000000004203, 'demo-upload-9107', 'SERVER_CHUNK', NULL, {{DEMO_VIDEO_OBJECT_KEY}}, NULL, 0, 9107, 800000000000000202, '2026', {{DEMO_VIDEO_FINGERPRINT}}, '教学能力展示-郑丽华.mp4', {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_CONTENT_TYPE}}, 8388608, 1, 1, {{DEMO_VIDEO_SIZE}}, {{DEMO_VIDEO_DURATION_SECONDS}}, 'MERGED', 8100000000000003, '服务端媒体探测通过', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    upload_mode = VALUES(upload_mode), s3_upload_id = VALUES(s3_upload_id),
    object_key = VALUES(object_key), presign_expires_at = VALUES(presign_expires_at),
    slot_claimed = VALUES(slot_claimed), student_id = VALUES(student_id),
    file_md5 = VALUES(file_md5), file_name = VALUES(file_name),
    file_size = VALUES(file_size), content_type = VALUES(content_type), chunk_size = VALUES(chunk_size),
    total_chunks = VALUES(total_chunks), uploaded_chunks = VALUES(uploaded_chunks),
    uploaded_bytes = VALUES(uploaded_bytes), duration_seconds = VALUES(duration_seconds),
    status = VALUES(status), file_id = VALUES(file_id), validation_message = VALUES(validation_message),
    updated_at = NOW(), deleted = 0;

INSERT INTO video_review_task
(id, video_review_id, student_id, college_id, reviewer_id, reviewer_role, score, dimension_scores_json, comment,
 conclusion, submitted, submit_time, created_at, updated_at, deleted)
VALUES
(8100000000004101, 8100000000004001, 9101, 800000000000000201, 800000000000003005, 'REVIEWER', 85,
 JSON_OBJECT('lesson_design',9,'teaching_objective',9,'key_difficulty',8,'teaching_implementation',13,'classroom_organization',9,'subject_literacy',9,'language_expression',9,'courseware_blackboard',8,'teaching_reflection',11),
 '教学设计完整，语言表达清晰，课堂组织良好。', 'PASS', 1, NOW(), NOW(), NOW(), 0),
(8100000000004102, 8100000000004002, 9104, 800000000000000201, 800000000000003005, 'REVIEWER', NULL, NULL, NULL, NULL, 0, NULL, NOW(), NOW(), 0),
(8100000000004103, 8100000000004003, 9107, 800000000000000202, 800000000000003005, 'REVIEWER', NULL, NULL, NULL, NULL, 0, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    video_review_id = VALUES(video_review_id), student_id = VALUES(student_id), college_id = VALUES(college_id),
    reviewer_role = VALUES(reviewer_role), score = VALUES(score), dimension_scores_json = VALUES(dimension_scores_json),
    comment = VALUES(comment), conclusion = VALUES(conclusion), submitted = VALUES(submitted),
    submit_time = VALUES(submit_time), updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 7) 教师职业能力测试结果 ability_test_result
-- =====================================================================================
INSERT INTO ability_test_result
(id, student_id, college_id, assessment_year, exam_org_mode, exam_subjects, score, conclusion, exemption_relation,
 confirm_status, locked, created_at, updated_at, deleted)
VALUES
(8100000000005001, 9101, 800000000000000201, '2026', 'separate_interview', JSON_ARRAY('综合素质（小学）','教育教学知识与能力','学科知识与教学能力'), '85', 'qualified',       NULL, 'CONFIRMED', 1, NOW(), NOW(), 0),
(8100000000005002, 9108, 800000000000000202, '2026', 'separate_interview', JSON_ARRAY('综合素质（初中）','教育知识与能力','学科知识与教学能力（初中）'), '90', 'qualified',    NULL, 'CONFIRMED', 1, NOW(), NOW(), 0),
(8100000000005003, 9104, 800000000000000201, '2026', 'separate_interview', JSON_ARRAY('综合素质（初中）','教育知识与能力'), NULL, 'pending_confirm', NULL, 'PENDING', 0, NOW(), NOW(), 0),
(8100000000005004, 9107, 800000000000000202, '2026', 'separate_interview', JSON_ARRAY('学科知识与教学能力（初中）'), NULL, 'exempted', JSON_OBJECT('exemptionRequestId', 8100000000003001, 'subject', 'subject_knowledge_junior'), 'CONFIRMED', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    exam_org_mode = VALUES(exam_org_mode), exam_subjects = VALUES(exam_subjects), score = VALUES(score),
    conclusion = VALUES(conclusion), exemption_relation = VALUES(exemption_relation),
    confirm_status = VALUES(confirm_status), locked = VALUES(locked), updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 8) 证书 certificate（2 张已签发 ISSUED + 1 张已作废 VOIDED）。cert_no=年(4)+校码(5)+级码(1)+省码(2)+段码(1)+序(5)=18 位。
--    作废证书 active_key 生成列为 NULL，不与本人同年度有效证书冲突（uk_cert_active）。
-- =====================================================================================
INSERT INTO certificate
(id, student_id, college_id, assessment_year, cert_no, student_no, student_name, id_card_type, id_card_no,
 education_level, training_goal, teaching_segment, teaching_subject_code, teaching_subject_name,
 issuer, issue_date, valid_until, status, void_reason, void_operator_id, void_time, reissue_origin_cert_no,
 locked, created_at, updated_at, deleted)
VALUES
(8100000000006001, 9101, 800000000000000201, '2026', '202610588344290001', 'S9101', '陈晓明', 'resident_id_card', '440105200001010011', 'bachelor', 'primary_school_teacher',       'primary_school',       'ps_chinese', '语文', '广东技术师范大学教务处', '2026/06/30', NULL, 'ISSUED', NULL, NULL, NULL, '202610588344290000', 1, NOW(), NOW(), 0),
(8100000000006002, 9108, 800000000000000202, '2026', '202610588344390002', 'S9108', '孙梦琪', 'resident_id_card', '440105200108080088', 'bachelor', 'junior_middle_school_teacher', 'junior_middle_school', 'jms_math',   '数学', '广东技术师范大学教务处', '2026/06/30', NULL, 'ISSUED', NULL, NULL, NULL, NULL, 1, NOW(), NOW(), 0),
(8100000000006003, 9101, 800000000000000201, '2026', '202610588344290000', 'S9101', '陈晓明', 'resident_id_card', '440105200001010011', 'bachelor', 'primary_school_teacher',       'primary_school',       'ps_chinese', '语文', '广东技术师范大学教务处', '2026/06/20', NULL, 'VOIDED', '姓名信息更正，作废后重开', 800000000000003007, NOW(), NULL, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    student_name = VALUES(student_name), id_card_no = VALUES(id_card_no), education_level = VALUES(education_level),
    training_goal = VALUES(training_goal), teaching_segment = VALUES(teaching_segment),
    teaching_subject_code = VALUES(teaching_subject_code), teaching_subject_name = VALUES(teaching_subject_name),
    issuer = VALUES(issuer), issue_date = VALUES(issue_date), status = VALUES(status), void_reason = VALUES(void_reason),
    void_operator_id = VALUES(void_operator_id), void_time = VALUES(void_time),
    reissue_origin_cert_no = VALUES(reissue_origin_cert_no), locked = VALUES(locked), updated_at = NOW(), deleted = 0;

-- =====================================================================================
-- 9) 站内信 notification（未读，让各角色「铃铛」有未读项）：
--    demo_student / test_student(3002) / test_college_clerk(3003) / test_college_auditor(3004) / test_review_teacher(3005)
-- =====================================================================================
INSERT INTO notification
(id, user_id, type, title, content, biz_type, biz_id, read_flag, created_at, updated_at, deleted)
VALUES
-- 学生（demo_student 本人视图）
(8100000000007001, 8100000000000901, 'REVIEW_RETURN', '培养信息复审通过', '您的专业培养信息已复审通过，进入后续环节。', 'training', '8100000000001101', 0, NOW(), NOW(), 0),
(8100000000007002, 8100000000000901, 'EXPORT_DONE',   '证书已签发',       '您的教师职业能力证书已签发，可在证书页面查看。', 'certificate', '8100000000006001', 0, NOW(), NOW(), 0),
-- 学生（基础 test_student 账号，保证其铃铛也有未读）
(8100000000007011, 800000000000003002, 'REVIEW_RETURN', '过程材料待补充', '您有过程性材料需补充后重新提交，请及时处理。', 'material', '0', 0, NOW(), NOW(), 0),
(8100000000007012, 800000000000003002, 'REVIEW_TODO',   '欢迎使用考核平台', '欢迎使用师范生教育教学能力考核平台，请完善本人信息。', 'system', '0', 0, NOW(), NOW(), 0),
-- 学院教务员（初审待办）
(8100000000007021, 800000000000003003, 'REVIEW_TODO', '过程材料待初审', '过程性材料（学生ID：9103）已提交至待初审。', 'material', '8100000000002003', 0, NOW(), NOW(), 0),
(8100000000007022, 800000000000003003, 'REVIEW_TODO', '培养信息待初审', '专业培养信息（学生ID：9106）已提交至待初审。', 'training', '8100000000001106', 0, NOW(), NOW(), 0),
-- 学院负责人（复审待办）
(8100000000007031, 800000000000003004, 'REVIEW_TODO', '培养信息待复审', '专业培养信息（学生ID：9104）初审通过，待复审。', 'training', '8100000000001104', 0, NOW(), NOW(), 0),
-- 评审教师（视频评审待办）
(8100000000007041, 800000000000003005, 'VIDEO_ASSIGN', '视频评审提醒', '教学能力视频（学生ID：9104）已分配评审任务。', 'video_review', '8100000000004002', 0, NOW(), NOW(), 0),
(8100000000007042, 800000000000003005, 'VIDEO_ASSIGN', '视频评审提醒', '教学能力视频（学生ID：9107）已分配评审任务。', 'video_review', '8100000000004003', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id), type = VALUES(type), title = VALUES(title), content = VALUES(content),
    biz_type = VALUES(biz_type), biz_id = VALUES(biz_id), read_flag = VALUES(read_flag), updated_at = NOW(), deleted = 0;
