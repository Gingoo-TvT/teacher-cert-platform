-- =============================================================
-- WS-2（凭据硬化 · 审计 #4 Medium）：把两个「学生自动开户 / PII 派生初始口令」的出厂默认
-- 从不安全翻为安全默认。这两个 sys_param 由 V1__base.sql 以 'true'/'idcard6' 播种（生产亦然），
-- 且 §0 禁止编辑已应用迁移，故以本新迁移 UPDATE 其值：
--   · student.autoCreateAccount:  true    → false    （导入不再默认自动开通可登录账号）
--   · student.defaultPwd:         idcard6 → random   （不再由证件号后六位派生）
-- 仅迁走旧危险值，不覆盖学校已设置的 false / 受控强口令。参数缺失时应用代码也以 false/random fail-closed。
--
-- 说明：admin / STAFF 的口令硬化走应用层（AdminAccountInitializer 覆盖 admin 口令 + 生产 fail-fast；
-- SecurityAdminServiceImpl 的 STAFF 初始口令 prod fail-fast）——生产密钥/哈希不入库、不入迁移。
-- 幂等：仅按 param_key UPDATE 既有行的安全默认与说明，重复执行结果一致。
-- =============================================================

UPDATE sys_param
   SET param_value = 'false',
       description = '导入/创建学生时是否自动开通账号（安全默认关闭）',
       updated_at = NOW()
 WHERE param_key = 'student.autoCreateAccount'
   AND LOWER(TRIM(param_value)) = 'true';

UPDATE sys_param
   SET param_value = 'random',
       description = '学生初始口令规则（random=停用账号的随机占位哈希，须管理员受控重置后启用）',
       updated_at = NOW()
 WHERE param_key = 'student.defaultPwd'
   AND LOWER(TRIM(param_value)) = 'idcard6';
