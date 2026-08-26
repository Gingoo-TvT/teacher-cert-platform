# HANDOFF → codex：审计整改 WS 工单（WS-2 收尾 + WS-3..15 顺序执行）

> 生成：Claude（主控）交接。背景：auto-mode 分类器（`claude-opus-4-8`）持续不可用 → 主控 Bash 被 fail-closed 拦，无法自跑 `mvn verify`/`git`。改由 codex（自带 sandbox bypass、不经该分类器）执行，主控只读复核。
> **闭环纪律（`.claude/skills/codex-subagent` + `docs/REVIEW-GATE.md`）**：codex 实现 + 自测 → 置「待复核」→ 主控独立复核 → PASS 放行 / 退回。**一次只推进一个 WS、一个写工作树**；**勿加远程 / 勿 push / 勿自行 merge / 勿自行置 ✅**。

---

## 0. 当前仓库状态（务必先认清，别覆盖已有成果）

- 分支链：`main` → `feature/ws01-verify-green-it-isolation`(`25be4bd`, **WS-1 已完成单 commit、待主控合并**) → **`feature/ws02-credential-hardening`（当前分支，WS-2 已实现但【未提交】）**。
- `git remote -v` **为空**（本地私有，保持）。
- **⚠️ 工作树里有两类未提交改动，必须分清、勿混提**：
  1. **WS-2 的实现改动（9 个文件，见 §1）** —— 本次要 verify + 单 commit 的内容。
  2. **主控 prompt-vetting 的文档改动**（`docs/prompts/*.md`、`docs/audit-remediation-plan.md`、本文件）+ 若干未跟踪文件（`audit-report-*.html`、`.claude/audits/`）—— **不属于 WS-2，别 `git add -A`**；WS-2 提交只 `git add` §1 列出的 9 个路径。

---

## 1. WS-2 已实现改动清单（codex 先 review 再 verify，勿重写）

对应工单 `docs/prompts/02-ws02-opus-credential-hardening.md`（已 vetting）。主控已落地以下 9 处，**codex 的任务是核对 + 编译 + 验收 + 提交**，不是重做：

| # | 文件 | 改动 |
|---|---|---|
| 1 | `platform-boot/src/main/resources/application.yml` | 新增 `platform.security.initial-password: ${STAFF_INITIAL_PASSWORD:}`（**空默认**）+ `platform.security.admin.initial-password-hash: ${ADMIN_INITIAL_PASSWORD_HASH:}`（空默认） |
| 2 | `platform-boot/src/main/resources/application-dev.yml` | 新增 `platform.security.initial-password: ${STAFF_INITIAL_PASSWORD:ChangeMe123!}`（dev/IT 默认值，保证 IT 绿） |
| 3 | `platform-security/.../security/service/SecurityAdminServiceImpl.java` | `@Value` 默认从 `:ChangeMe123!` 改为**空**；新增 `@PostConstruct validateInitialPassword()` —— 空则抛 `BizException` fail-fast（同 `JwtService.init` 风格） |
| 4 | `platform-boot/.../boot/config/AdminAccountInitializer.java` | **新增组件**：`@PostConstruct` 里——有 `ADMIN_INITIAL_PASSWORD_HASH` 则覆盖 `admin` 口令哈希 + `must_change_pwd=1`；prod 且缺失 → fail-fast 拒启；非 prod 缺失 → no-op（保留 testseed admin）。§0 禁改已应用迁移，故 V8 admin 不动、用应用层中和。 |
| 5 | `platform-business/.../student/service/impl/StudentServiceImpl.java` | `initialPassword(idCardNo)` → `initialPassword()`：删除证件号后六位 PII 派生，改 `randomInitialPassword()`（SecureRandom 16 位）；`ensureStudentAccount` 调用点同步改 |
| 6 | `platform-boot/src/main/resources/db/migration/V27__ws02_credential_hardening_defaults.sql` | **新增迁移**：`UPDATE sys_param` 把 `student.autoCreateAccount` `true→false`、`student.defaultPwd` `idcard6→random`（这两默认由 `V1__base.sql:78-79` 播种，不可改 V1，故 V27 覆盖） |
| 7 | `platform-boot/src/test/.../Phase3StudentIT.java` | `createdStudentAccountCanLoginWithInitialPassword` → **`importedStudentHasNoIdCardDerivedLogin`**：断言默认不自动开户（`selectByUsername==null`）+ 证件号后六位登录被拒（code≠0）。**这是被测安全语义的正当变更（复现→阻断），不是"改绿"**。 |
| 8 | `README.md` | dev/test 测试口令 与 生产 bootstrap secret **分离成两段**；补 `ADMIN_INITIAL_PASSWORD_HASH`/`STAFF_INITIAL_PASSWORD`/学生随机口令说明 |
| 9 | `.env.example` | 新增 `ADMIN_INITIAL_PASSWORD_HASH` + `STAFF_INITIAL_PASSWORD`（均标注生产必配、fail-fast） |

**迁移号说明**：WS-2 取 **V27**（开工时磁盘 max 为 V26）。后续 WS-3/WS-8 也要迁移 → 它们开工时 max 已是 V27 → 自然取 V28/V29（各自 `ls db/migration | sort -V | tail -1` 复核）。

---

## 2. WS-2 收尾动作（codex 执行）

1. **§0 预检**：精确 PID 释放 :8080（`PID=$(netstat -ano|grep ":8080"|grep LISTENING|head -1|awk '{print $NF}'); [ -n "$PID" ] && taskkill //PID $PID //F`）；确认无其他 verify 在跑；依赖容器在跑（`docker compose -f docker-compose.dev.yml up -d`；注意 `tcp-mysql` 若端口未发布需 `--force-recreate mysql`）。
2. **编译 + 验收**：`mvn -B -ntp clean verify`。
   - **验收①（demo 驻留库）**：库里现应仍有 WS-1 遗留的 demo 行（`student 9101-9108` 等）——直接跑，须 **全绿**（现 119 IT；Phase3 用例已改写为安全反面仍应绿）。
   - **验收②（全新库）**：清掉 demo 行后再跑一遍全绿（demo 清理法见 WS-1 DEVLOG / 用固定 id 段 DELETE）。
   - 两库各至少一次绿即可（WS-1 已证隔离；WS-2 未新增计数敏感断言）。
3. **活体 fail-fast（复现→阻断，硬证据）**：
   - **STAFF**：prod profile 且不设 `STAFF_INITIAL_PASSWORD` 起栈 → 期望 `SecurityAdminServiceImpl.validateInitialPassword` 抛错、上下文启动失败（贴日志）。
   - **admin**：prod profile 且不设 `ADMIN_INITIAL_PASSWORD_HASH` 起栈 → 期望 `AdminAccountInitializer` 抛错拒启（贴日志）。
   - **正向**：设两者（admin 用 bcrypt 哈希）→ 起栈成功、admin 以新口令登录（旧 `ChangeMe123!` 被拒）。
   - ⚠️ 起 prod 栈需备齐 prod 必配 env（`JWT_SECRET`/`DB_*`/`REDIS_PASSWORD`），或用 dev profile + 显式 `--platform.security.initial-password=`（空）来单点复现 STAFF fail-fast，避免整套 prod 依赖；两法择一，如实记录。**注意 §0 坑⑥：headless 勿常驻起应用**——起栈自测须在会自然退出/超时的方式下做，或交外部终端。
4. **文档**：写 `DEVLOG.md`（倒序，含 verify 结果 + 三条 fail-fast 活体证据）+ `docs/launch-readiness-plan.md` §11 追加 WS-2 条目。
5. **单 commit**（**只 add §1 的 9 个路径**，勿含 prompt-vetting 文档）：分支 `feature/ws02-credential-hardening`，Conventional Commits，`Co-Authored-By: Claude ...`。
6. **置「待复核」 → STOP**，报告 3–5 行（做了什么/verify 结果/fail-fast 证据/置待复核）。**勿 merge、勿 push、勿自置 ✅**。

---

## 3. WS-2 之后：按 P0→P3 顺序逐个执行（每个 = 一次独立调度 + 复核）

**每个 WS 的权威指令 = `docs/prompts/NN-*.md`（均已 vetting、含 code-grounded 锚点与红线）。一次一个、串行 verify 门禁、逐个复核放行。**顺序（README §建议顺序）：

1. `02-ws02-*`（本次，收尾中）
2. `15-ws10-codex-profile-guard.md`（P3 但与 WS-2 强协同：删默认 `active=dev` + profile 守卫；**隐藏坑：全库 0 个 `@ActiveProfiles`、无 test `application.yml` → 必须中心化补测试 profile**，见其提示词）
3. `03-ws03a` + `04-ws03b`（MinIO 预签名，后端先行；**新增 `awssdk:s3` 依赖**、扩 `video_upload_session`、Phase7 上传 IT 须改写到直传契约——非"契约不破"）
4. `05-ws04a`(D0/D1/D4) + `06-ws04b`(D2/D3/D5/D6)（前端 UI；**跨断点自检硬门槛 + `ui-shots.mjs`**，需 frontend-design skill / naive-ui-skills / OpenDesign craft / Playwright MCP——见 plan §3 WS-4 装配说明）
5. `07-ws05a`(TLS/CSP) + `08-ws05b`(HttpOnly refresh；**Phase2SecurityIT 刷新契约会变、须改写**)
6. `09-ws06a`(Vitest/CI) + `10-ws06b`(Playwright E2E；复用 WS-4 D0 登录 helper) + `11-ws07`(供应链) + `12-ws13`(RBAC 天花板；**守卫加在 `SecurityAdminServiceImpl`、新 IT 须自带正反两路**)
7. `13-ws08`(idcard 加密；**HMAC 不能是生成列、须 DROP V24 `idcard_key`、与 WS-2 学生口令协同**) + `14-ws09`(拆巨类；**WS-3 之后**)
8. `16-ws11`(health/metrics；**现是自定义 HealthController、非 Actuator**) + `17-ws12`(bundle；**vite manualChunks 已拆、真活是路由懒加载 + ChartBox 按需 echarts**) + `18-ws14`(运维收尾) + `19-ws15`(异步审计，可选；**44f 已确认在位但"@Async==该锁"仍是假设、3×消融为唯一裁判**)

> 每个 WS 完成 → 主控按 `docs/REVIEW-GATE.md` 复核（读增量 diff + 干净重建 + 重跑该 WS 反例 + `/code-review`/`/security-review`）→ PASS 才放行下一个。opus-designated 的复杂/关键 WS（3a/5b/8/9/11/13/15）复核从严。

---

## 4. codex 启动命令（主控 Bash 恢复后自动跑；或用户 `!` 前缀手动起）

```bash
REPO="C:/Users/wenbibuhaoqwq/Desktop/teacher-cert-platform"
codex exec \
  --cd "$REPO" \
  --dangerously-bypass-approvals-and-sandbox \
  -o /tmp/codex_ws02_last.txt \
  "你在仓库 $REPO 工作。先读 docs/prompts/HANDOFF-codex-ws.md（本文件）→ AGENTS.md 红线/任务循环 → docs/prompts/02-ws02-opus-credential-hardening.md → docs/REVIEW-GATE.md。
   **当前 WS-2 代码已由主控实现但未提交（见 HANDOFF §1 的 9 文件）——你先逐一 review 这些改动是否正确，勿重写**；然后按 HANDOFF §2 收尾：§0 预检 → mvn -B -ntp clean verify（demo 驻留库 + 全新库各至少一次全绿）→ 三条 fail-fast 活体（STAFF/admin 缺 secret 拒启、正向可起）→ 写 DEVLOG + launch-readiness §11 → 只 add §1 的 9 个路径做单 commit（勿含 docs/prompts 等 vetting 文档）→ PROGRESS/置「待复核」→ 3-5 行报告。
   红线：勿加远程/勿 push/勿 merge/勿自置✅；Flyway 迁移勿改已应用脚本；headless 勿常驻起应用（自测用会自然退出的方式，见 HANDOFF §2.3 与 HANDOFF.md 坑⑥）。若发现主控实现有 bug，记 DEVLOG 并修正后再提交。" \
  < /dev/null > /tmp/codex_ws02_run.log 2>&1
```
- 用 `run_in_background: true` 跑；主控周期 `Read /tmp/codex_ws02_run.log` 监控（建分支已在、看 mvn 绿 → fail-fast 证据 → 单 commit → 待复核）。
- 退出后 `Read /tmp/codex_ws02_last.txt` 取末条总结，`git log --oneline` + `git show --stat` 核对只含 9 文件。
