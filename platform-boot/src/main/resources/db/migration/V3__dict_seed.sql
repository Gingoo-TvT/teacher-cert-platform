-- =============================================================
-- V3 字典标准值种子
-- 来源：plan.md §5.2；字典显示值必须逐字一致。
-- 免考依据/可免科目、证书签发人按确认单默认“学校维护，初始置空”，本脚本仅创建字典类型。
-- =============================================================

INSERT INTO sys_dict_type (id, type_code, type_name, description, sort, status, created_at, updated_at, deleted) VALUES
(300000000000000001, 'school', '学校', '学校代码与学校名称', 10, 1, NOW(), NOW(), 0),
(300000000000000002, 'province', '省级行政区划代码', '证书编号省码参数字典', 20, 1, NOW(), NOW(), 0),
(300000000000000003, 'identity_type', '身份类型', '师范生身份类型', 30, 1, NOW(), NOW(), 0),
(300000000000000004, 'id_card_type', '身份证件类型', '身份证件类型', 40, 1, NOW(), NOW(), 0),
(300000000000000005, 'education_level', '学历层次', '学历层次', 50, 1, NOW(), NOW(), 0),
(300000000000000006, 'training_goal', '专业培养目标', '专业培养目标', 60, 1, NOW(), NOW(), 0),
(300000000000000007, 'internship_org_mode', '教育实习实践组织方式', '教育实习实践组织方式', 70, 1, NOW(), NOW(), 0),
(300000000000000008, 'internship_location', '教育实习实践地点', '教育实习实践地点', 80, 1, NOW(), NOW(), 0),
(300000000000000009, 'teaching_segment', '任教学段', '任教学段', 90, 1, NOW(), NOW(), 0),
(300000000000000010, 'interview_org_mode', '面试考试组织方式', '面试考试组织方式', 100, 1, NOW(), NOW(), 0),
(300000000000000011, 'gender', '性别', '按上级平台允许值配置', 110, 1, NOW(), NOW(), 0),
(300000000000000012, 'material_category', '材料类别', '过程性考核材料类别', 120, 1, NOW(), NOW(), 0),
(300000000000000013, 'ability_test_conclusion', '测试结论', '教师职业能力测试结论', 130, 1, NOW(), NOW(), 0),
(300000000000000014, 'exemption_subject', '免考科目', '待学校提供清单后维护', 140, 1, NOW(), NOW(), 0),
(300000000000000015, 'exemption_basis', '免考依据/原因', '待学校提供清单后维护', 150, 1, NOW(), NOW(), 0),
(300000000000000016, 'cert_issuer', '证书签发人', '校（院）长姓名，学校维护', 160, 1, NOW(), NOW(), 0),
(300000000000000017, 'video_score_dimension', '视频评分维度', '教学能力视频评分维度', 170, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    type_name = VALUES(type_name),
    description = VALUES(description),
    sort = VALUES(sort),
    status = VALUES(status),
    updated_at = NOW(),
    deleted = 0;

INSERT INTO sys_dict_item (id, type_code, item_code, item_value, parent_code, sort, status, year_version, ext_json, created_at, updated_at, deleted) VALUES
-- 学校 + 省码
(300000000000001001, 'school', '10588', '广东技术师范大学', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000002001, 'province', '44', '广东', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 身份类型
(300000000000003001, 'identity_type', 'education_master', '教育类研究生', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000003002, 'identity_type', 'public_funded_normal', '公费师范生', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000003003, 'identity_type', 'normal_student', '普通师范生', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000003004, 'identity_type', 'excellent_teacher_plan', '优师计划师范生', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000003005, 'identity_type', 'national_excellent_plan', '国优计划师范生', NULL, 5, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 身份证件类型
(300000000000004001, 'id_card_type', 'resident_id_card', '居民身份证', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000004002, 'id_card_type', 'hmt_residence_permit', '港澳台居民居住证', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000004003, 'id_card_type', 'hm_travel_permit', '港澳居民来往内地通行证', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000004004, 'id_card_type', 'tw_travel_permit_5y', '五年有效期台湾居民来往大陆通行证', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 学历层次
(300000000000005001, 'education_level', 'doctor', '博士研究生', NULL, 1, 1, 'GLOBAL', JSON_OBJECT('certLevelCode', '1'), NOW(), NOW(), 0),
(300000000000005002, 'education_level', 'master', '硕士研究生', NULL, 2, 1, 'GLOBAL', JSON_OBJECT('certLevelCode', '2'), NOW(), NOW(), 0),
(300000000000005003, 'education_level', 'bachelor', '本科', NULL, 3, 1, 'GLOBAL', JSON_OBJECT('certLevelCode', '3'), NOW(), NOW(), 0),
(300000000000005004, 'education_level', 'junior_college', '专科', NULL, 4, 1, 'GLOBAL', JSON_OBJECT('certLevelCode', '4'), NOW(), NOW(), 0),

-- 专业培养目标
(300000000000006001, 'training_goal', 'kindergarten_teacher', '幼儿园教师', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000006002, 'training_goal', 'primary_school_teacher', '小学教师', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000006003, 'training_goal', 'junior_middle_school_teacher', '初级中学教师', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000006004, 'training_goal', 'senior_middle_school_teacher', '高级中学教师', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000006005, 'training_goal', 'secondary_vocational_school_teacher', '中等职业学校教师', NULL, 5, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 教育实习实践组织方式
(300000000000007001, 'internship_org_mode', 'school_organized', '学校组织', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000007002, 'internship_org_mode', 'self_contact', '个人联系', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 教育实习实践地点
(300000000000008001, 'internship_location', 'kindergarten', '幼儿园', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000008002, 'internship_location', 'primary_secondary_school', '中小学', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000008003, 'internship_location', 'enterprise_vocational_education', '企业（职业技术教育专业）', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000008004, 'internship_location', 'overseas_chinese_international_education', '海外（汉语国际教育专业）', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000008005, 'internship_location', 'other', '其他', NULL, 5, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 任教学段
(300000000000009001, 'teaching_segment', 'kindergarten', '幼儿园', NULL, 1, 1, 'GLOBAL', JSON_OBJECT('certSegmentCode', '1'), NOW(), NOW(), 0),
(300000000000009002, 'teaching_segment', 'primary_school', '小学', NULL, 2, 1, 'GLOBAL', JSON_OBJECT('certSegmentCode', '2'), NOW(), NOW(), 0),
(300000000000009003, 'teaching_segment', 'junior_middle_school', '初级中学', NULL, 3, 1, 'GLOBAL', JSON_OBJECT('certSegmentCode', '3'), NOW(), NOW(), 0),
(300000000000009004, 'teaching_segment', 'senior_middle_school', '高级中学', NULL, 4, 1, 'GLOBAL', JSON_OBJECT('certSegmentCode', '4'), NOW(), NOW(), 0),
(300000000000009005, 'teaching_segment', 'secondary_vocational_school', '中等职业学校', NULL, 5, 1, 'GLOBAL', JSON_OBJECT('certSegmentCode', '5'), NOW(), NOW(), 0),

-- 面试考试组织方式
(300000000000010001, 'interview_org_mode', 'with_internship_practice', '结合教育实习实践环节一并考核', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000010002, 'interview_org_mode', 'separate_interview', '单独面试', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 性别
(300000000000011001, 'gender', 'male', '男', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000011002, 'gender', 'female', '女', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 材料类别
(300000000000012001, 'material_category', 'morality_teacher_ethics', '思想品德及师德素养', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000012002, 'material_category', 'teacher_education_course', '教师教育课程学业', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000012003, 'material_category', 'education_internship_practice', '教育实习实践', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000012004, 'material_category', 'professional_ability_skill_training', '专业能力及技能培训', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 测试结论
(300000000000013001, 'ability_test_conclusion', 'qualified', '合格', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000013002, 'ability_test_conclusion', 'unqualified', '不合格', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000013003, 'ability_test_conclusion', 'exempted', '免考', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000013004, 'ability_test_conclusion', 'pending_confirm', '待确认', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),

-- 视频评分维度
(300000000000017001, 'video_score_dimension', 'lesson_design', '说课设计', NULL, 1, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017002, 'video_score_dimension', 'teaching_objective', '教学目标', NULL, 2, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017003, 'video_score_dimension', 'key_difficulty', '重难点', NULL, 3, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017004, 'video_score_dimension', 'teaching_implementation', '教学实施', NULL, 4, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017005, 'video_score_dimension', 'classroom_organization', '课堂组织', NULL, 5, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017006, 'video_score_dimension', 'subject_literacy', '学科素养', NULL, 6, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017007, 'video_score_dimension', 'language_expression', '语言表达', NULL, 7, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017008, 'video_score_dimension', 'courseware_blackboard', '课件/板书', NULL, 8, 1, 'GLOBAL', NULL, NOW(), NOW(), 0),
(300000000000017009, 'video_score_dimension', 'teaching_reflection', '教学反思', NULL, 9, 1, 'GLOBAL', NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    item_value = VALUES(item_value),
    parent_code = VALUES(parent_code),
    sort = VALUES(sort),
    status = VALUES(status),
    ext_json = VALUES(ext_json),
    updated_at = NOW(),
    deleted = 0;
