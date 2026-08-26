# WS-8 阶段候选提交说明（2026-08-12）

## 1. 候选身份与裁定边界

- 分支：`feature/ws08-idcard-encryption`
- 基线 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- 候选 manifest：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-stage-candidate-2026-08-12.json`
- 完整 fingerprint、tracked/untracked 清单与各文件 SHA-256：以该仓库外 manifest 为准；本文件不回写 fingerprint，
  避免候选身份自引用漂移。
- 提交者状态：`LOCAL_STAGE_CANDIDATE_READY / INDEPENDENT_REVIEW_PENDING`。

本材料不签发 WS-8 PASS，不放行 WS-9，也不授权 stage/commit/merge/push/deploy/cutover。项目继续
`CHANGES_REQUESTED / NO-GO`。独立复核由用户在本阶段完整候选上集中执行。

## 2. 冻结范围

WS-8 只关闭审计 #5 的身份证件号码存储边界：

1. `IDCARD_ENCRYPTION_KEY` 为 Base64 编码的 32 字节 AES key；`IDCARD_HMAC_PEPPER` 至少 32 UTF-8 字节，
   二者独立且不得复用 JWT secret。prod 缺失、示例或格式非法时在 Flyway 前失败关闭。
2. `IdCardProtectionService` 使用 AES-GCM、每次随机 12-byte IV、固定 AAD 和 `v1:` 协议前缀；HMAC-SHA256 输出
   64 位小写 hex，仅用于等值查找和唯一约束。密文重入必须先认证，篡改/错误密钥不降级为明文。
3. V33 扩大 `student/certificate.id_card_no`，新增应用写入的 `id_card_hmac`，移除 V24 的明文生成列
   `student.idcard_key` 及其索引，建立活跃学生 HMAC 唯一键和证书查询索引；不修改已发布迁移。
4. Spring `AFTER_MIGRATE` callback 在 repeatable seed 后幂等保护 Student、Certificate、导入 preview/scope、
   before/after snapshot 和证件号 error value；迁移前检查规范化 HMAC 冲突，失败时拒绝启动，不输出 PII。
5. Student/Certificate 新写入使用独立随机密文和同一规范化 HMAC；重复查询改走 HMAC；逻辑删除同时把唯一键
   置 NULL，允许同证件号按业务流程重建。普通投影继续脱敏，仅既有权限和审计覆盖的明文接口解密。
6. Exchange 导入、回滚、预览、错误投影、导出和证件号精确关键词查询适配密文/HMAC；普通导出继续脱敏，
   `exchange:export:sensitive` 既有功能保持。持久化的 `scope_json.keyword` 一律保护，避免完整筛选词留下明文。
7. 逻辑备份包含密文/HMAC但不包含 key/pepper；恢复必须使用原两项 secret，并验证可解密、默认脱敏、HMAC 与
   重复拒绝。demo 初始化在运行时生成密文；旧裸 SQL demo loader 失败关闭，避免 V33 后再次写入明文。

明确不纳入：在线密钥轮换/KMS 平台、其它 PII 字段、WS-9 服务拆分、依赖或镜像扫描、攻击载荷、模糊测试、
生产部署和发布切流。

## 3. 本地动态证据

### 3.1 后端单元与集成

- 全仓 Surefire：**52 suites / 364 tests / 0 failure / 0 error / 0 skip**。
- WS-8/邻接聚焦 Failsafe：`V33IdCardProtectionMigrationIT`、Phase 3/5/6/7/10、Phase 41 backup、WS-3 backup
  restore，共 **8 suites / 97 tests / 0 failure / 0 error / 0 skip**。
- 完整 Failsafe：显式排除必须由正式 fresh-target pre-attestation 驱动的 `Phase00ScaffoldIT` 后，
  **32 suites / 220 tests / 0 failure / 0 error / 0 skip**。
- V33 真实 MySQL 用例覆盖：V32 legacy → V33 全字段保护、删除行 HMAC=NULL、随机密文不相等、snapshot HMAC、
  未知历史 keyword 保护、二次 callback 字节不变；篡改 `v1:` 历史值触发整批回滚，修复后无需 Flyway repair
  即可重试收敛。
- Phase 41/WS-3 restore 覆盖：V33 最新版本、删除旧生成列、Student/Certificate 独立密文+同 HMAC、所有备份
  `FROM_BASE64(...)` 解码后均不包含已知证件号、恢复后继续可解密和查询。

`Phase00ScaffoldIT` 的排除不是豁免：它要求由 `scripts/phase00_ci_gate.py run` 在 fresh target 上先生成
preContextTargetAttestation；当前大工作树不能伪造 tracked-clean/fresh-target 证据。独立复核如需项目级正式
Phase 0 证据，必须按其既有 gate 在授权隔离环境执行。本 WS-8 提交不改写既有 Phase 0 PASS。

### 3.2 前端与配置回归

- ESLint：PASS。
- app `vue-tsc --noEmit` 与 tests/config `tsc --noEmit`：PASS。
- Vitest：**4 files / 16 tests PASS**。
- CSP、auth/logout、dashboard latest request、WS-4 video/auth 四项合同：PASS。
- Vite production build：PASS；只保留既有大 chunk warning，bundle budget 属 WS-12。
- `docker compose --env-file .env.example -f docker-compose.yml config --quiet`：PASS。
- `git diff --check`：PASS。

前端没有 WS-8 产品代码改动；本轮前端门禁只验证共享候选未被连带破坏，不重开已独立通过的 WS-6，也不冒充
Playwright/hosted CI 新证据。

### 3.3 隔离与清理

- 动态门禁只使用 task-owned Compose project `teacher-cert-ws8-gate`，MySQL/Redis/MinIO 仅发布到
  `127.0.0.1:13306/16379/19000/19001`。
- 测试期间未连接、重建或停止现有 production Compose 容器；临时凭据仅进入当前命令环境和临时栈。
- 门禁完成后精确执行该 project 的 `down -v`，并复核其容器、网络、卷、端口及 task-owned Java/Node 进程均无
  残留。

## 4. 建议独立阶段复核清单

1. 对外部 manifest 做复核前/后 verify，核对 HEAD、tracked/untracked 数量与完整 fingerprint 一致。
2. 重跑 crypto/config 反例：随机 IV、篡改/错误 key、明文/未知版本拒绝、key/pepper prod 示例或缺失拒绝。
3. 用真实 MySQL 重跑 V33 两个场景，重点检查 HMAC 冲突发生在任何数据更新之前、历史 `scope_json.keyword`、
   preview/error/snapshot 无可读证件号、篡改值整批回滚与无 repair 重试。
4. 重跑 Phase 3/10：重复证件号拒绝；软删后 HMAC=NULL 且同证件可重建；普通投影/导出脱敏；授权敏感导出仍
   返回规范化明文并留审计；证件号关键词只走 HMAC且 scope JSON 无原值。
5. 重跑 Phase 41 与 WS-3 backup restore，逐个解码 SQL 中 Base64 literal 查找已知证件号，并用原 key/pepper
   恢复后验证 decrypt/HMAC；确认 artifact 不携带两项 secret。
6. 复算 Surefire/Failsafe XML 精确 suite/test/failure/error/skip；正式 Phase 00 attestation 如纳入复核，应使用
   其原 gate，不接受本地手工替代。
7. 复核结束后确认隔离资源清理；不得把本次 scoped stage verdict 外推为项目发布 GO。

## 5. 请求裁定

请求用户对 manifest 绑定候选做一次 WS-8 阶段级独立复核。只有正式 `INDEPENDENT_STAGE_PASS` 才能把 WS-8
置为已复核并放行 WS-9；任何 finding 均按原失败条件最小整改后重冻新候选。本材料本身不构成 PASS。
