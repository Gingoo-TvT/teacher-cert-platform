# WS-8 · 身份证号应用层加密 + HMAC 唯一键（迁移） — 提示词（Opus 4.8）

你是执行 **WS-8** 的 opus。对应 `docs/audit-remediation-plan.md` §4 WS-8（审计 #5）。**存量迁移风险高**。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-8**；`git remote -v` 空 → `git switch -c feature/ws08-idcard-encryption`。
- **迁移编号**：开工时磁盘最大版本为 **V32**，本工作流固定使用 **V33**；不得修改或复用已应用的 V24/V27–V32。
- **风格对齐**：`pepper` 是**新的必配 secret** → prod **fail-fast** 缺失即拒启（同 `JWT_SECRET`/WS-2 bootstrap）。
- **⚠️ 与 WS-2 协同**：`Phase3StudentIT.createdStudentAccountCanLoginWithInitialPassword:270` 用**证件号后六位**登录 = 从 `id_card_no` 派生口令（`StudentServiceImpl.initialPassword`）。**WS-2（P0）先落地**会废掉该 PII 派生口令 → 到 WS-8 该分支应已改；别再为 last-6 登录纠结，以 WS-2 落地后的口令机制为准（若 WS-2 未落地，则加密后"取后六位"须先解密）。

## 任务
- `id_card_no` **应用层加密存储**（写时加密、读脱敏）。
- 唯一键从"明文生成列"改为 **`HMAC-SHA256(id_card_no, pepper)`**。**⚠️ 关键：HMAC 列不能是 MySQL 生成列**（生成列拿不到应用 pepper 密钥）→ 必须是**应用侧写时算好、落库的普通列** + 唯一索引；`StudentServiceImpl.existsIdCardNo`（`:~473`，现 `eq(getIdCardNo, 明文)`）改为**按 HMAC 列查重**。
- **V24 交互（已核实、必处理）**：`V24` 的 `idcard_key` 是**明文** `id_card_no` 的 **STORED 生成列**（V24 注释明说"18位明文存储、可直接生成列唯一"）+ 唯一索引——**V24 已应用不可改**，**新迁移（编号见上 max+1）** 须 **DROP `idcard_key` 生成列及其唯一索引**、改建 HMAC 列 + 唯一索引（软删行 HMAC=NULL 以保留"软删同证件号可共存"语义）；**存量回填** HMAC 列 + 加密 `id_card_no`。
- 对外明文只允许走**受审计、受权限控制的敏感路径**：`plainIdCard`，以及上位规格 `plan.md §15.1` / Phase 10 已冻结的 `exchange:export:sensitive`。内部为校验、脱敏和迁移可短暂解密，但不得把明文写回数据库、日志或普通响应。
- **Exchange 明文列**：导入读明文 `ID_CARD_NO`（`ExchangeColumn:16`）→ 校验后算 HMAC 查重 + 加密存；普通导出保持脱敏；已有 `exchange:export:sensitive` 明文导出必须继续要求权限并留审计，不得借本整改静默删减既有功能。

## 验收（活体 + IT）
- 相同证件号仍触发**唯一约束**（`Phase10ExchangeIT` 重复 idCardNo 导入冲突用例 `:~210/219/241` 仍拒）。
- 普通列表/详情只返回**脱敏**（`Phase3StudentIT:117-118` 详情 `xxxxxx********xx` 掩码、`Phase10ExchangeIT:282-291` 导出单元格含 `*` 非明文——均须保持绿）；无敏感权限**拿不到明文**（复现被拒）；有权限走 `plainIdCard` 解密留审计。
- `student`、`certificate` 及 Exchange 预览/回滚快照/错误值的备份抽样**不出现可读证件号**。
- 全套 `mvn -B -ntp clean verify` **全绿**（现 119 基线 + 若新增 id-card 加密 IT；demo 驻留库照跑；`Phase3/10/14/48` 的 id-card 断言逐一复看）。

## 风险处置（硬要求）
- **存量迁移先在库快照上演练回填**，确认幂等/可回滚再对共享库跑。
- prod 未配 `pepper` → 启动失败（贴日志）。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿。
2. `DEVLOG.md`（倒序，含回填演练结果）+ `docs/launch-readiness-plan.md` §11 追加 WS-8 条目。
3. **单 commit**（`feature/ws08-idcard-encryption`）→ **STOP** 交主控复核。**禁止 merge / push**。
