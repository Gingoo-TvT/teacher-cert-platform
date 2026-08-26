-- WS-8 起禁止再用裸 SQL 装载演示学生：该旧路径无法生成应用 AES-GCM 密文与 HMAC 唯一键，
-- 会在 Flyway AFTER_MIGRATE 已结束后重新写入可读身份证件号码。
--
-- 请以 dev profile 启动同版本应用并显式设置 platform.demo.enabled=true；
-- DemoDataInitializer 会使用 IdCardProtectionService 渲染
-- platform-boot/src/main/resources/db/demo/demo-data.sql 后再执行。
-- 本脚本保留为旧运维入口的自然退出保护，避免历史命令静默污染 V33+ 数据库。
SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'WS-8: use DemoDataInitializer with platform.demo.enabled=true';
