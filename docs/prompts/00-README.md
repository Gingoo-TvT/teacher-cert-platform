# 审计整改执行提示词库（供 opus 4.8 / codex 逐个开工）

> 依据：`docs/audit-remediation-plan.md`（基线 commit `e4f8228`）。本目录把该计划的 15 个 WS 拆成**可直接整份粘贴**的独立提示词，每个文件对应一个 agent 会话的一次开工。
> 拆分的 WS（WS-3/4/5/6）分成 opus 与 codex 两份（`a`=先行/架构方，`b`=铺开/配置方），各自含与对方的交接说明。

## 怎么用
1. 按下方**执行顺序**挑一个提示词文件，**整份**粘给对应 agent（opus 或 codex）。
2. agent 在本仓库内开工：切分支 → 做事 → verify 绿 → 单 commit → **STOP**，交你（主控）复核合并。
3. **一次只推进不冲突的 WS**；`mvn verify` 门禁**同一时刻只能跑一个**（见铁律 §3）。
4. 合并后再放下一个。WS-1 未落地前，所有 verify 都可能被 demo 数据污染误判——**先做 WS-1**。

## 文件索引（执行顺序即文件名前缀）
| 文件 | WS | 主题 | 归属 | 优先级 | 关键依赖 |
|------|----|------|------|--------|----------|
| `01-ws01-opus-verify-green.md` | WS-1 | 恢复 `mvn verify` 绿（IT 计数隔离） | opus | **P0** | 最先做 |
| `02-ws02-opus-credential-hardening.md` | WS-2 | 凭据硬化（admin bootstrap + STAFF/学生初始口令） | opus | **P0** | 与 WS-10 协同 |
| `03-ws03a-opus-minio-presign-backend.md` | WS-3 | MinIO 预签名·后端/架构（直传直下） | opus | P1 | 先于 WS-9；C3 先打通 |
| `04-ws03b-codex-minio-config-frontend.md` | WS-3 | MinIO 预签名·内外双端点配置 + 前端接线 | codex | P1 | 配合 WS-3a；协调 WS-5 TLS |
| `05-ws04a-opus-ui-baseline.md` | WS-4 | UI 优化·走查 + 设计基线 + 样板（D0/D1/D4） | opus | P1 | D0 先出，其余按样板 |
| `06-ws04b-codex-ui-rollout.md` | WS-4 | UI 优化·跨视图铺开（D2/D3/D5/D6） | codex | P1 | 依赖 WS-4a 样板；晚于 WS-3 碰上传文件 |
| `07-ws05a-codex-tls-csp.md` | WS-5 | TLS/HSTS 全链路 + CSP（T1/T2） | codex | **P1**/P2 | 依赖域名证书；联动 WS-3 public-endpoint |
| `08-ws05b-opus-httponly-refresh.md` | WS-5 | refresh token HttpOnly（T3） | opus | P2 | 依赖 T1 HTTPS + WS-6 E2E |
| `09-ws06a-codex-vitest-ci.md` | WS-6 | 前端 type-check + Vitest + CI 门禁 | codex | P2 | — |
| `10-ws06b-opus-playwright-e2e.md` | WS-6 | Playwright E2E 冒烟 + axe a11y | opus | P2 | 承接 WS-5 T3 回归 |
| `11-ws07-codex-supply-chain.md` | WS-7 | 容器/CI 供应链硬化（非 root/digest/SBOM） | codex | P2 | — |
| `12-ws13-opus-rbac-ceiling.md` | WS-13 | RBAC 授权天花板（防自提权守卫） | opus | P2 | 边界先与用户确认 |
| `13-ws08-opus-idcard-encryption.md` | WS-8 | 身份证号加密 + HMAC 唯一键（迁移） | opus | P2 | 存量回填风险高 |
| `14-ws09-opus-split-services.md` | WS-9 | 拆分 Exchange/VideoReview 巨型服务类 | opus | P2 | **WS-3 先行** |
| `15-ws10-codex-profile-guard.md` | WS-10 | 删默认 `active=dev` + profile fail-fast | codex | P3 | 与 WS-2 协同；测试 profile 隐坑 |
| `16-ws11-opus-health-metrics.md` | WS-11 | health liveness/readiness 拆分 + 指标 | opus | P3 | — |
| `17-ws12-codex-bundle-split.md` | WS-12 | 前端 bundle 拆分（echarts/naive 懒加载 + budget） | codex | P3 | **WS-4 D1 之后** |
| `18-ws14-codex-ops-wrapup.md` | WS-14 | 运维收尾（备份生命周期 + backup_record 归档 + demo 可播视频） | codex | P3 | demo 视频是 WS-4 D0 前置 |
| `19-ws15-opus-async-audit.md` | WS-15 | 审计写异步重试（可选，消融复验硬门槛） | opus | P3 | — |

## 建议执行顺序
```
WS-1  ──►  WS-2  ──►  ┌ WS-3(a→b) ┐
                      │            ├─► WS-5(T1 TLS 证书到即插) ─► WS-5(T3)/WS-6/WS-7/WS-13
                      └ WS-4(D0→D1→铺开) ┘
                                                        └─► WS-8 / WS-9(WS-3 之后)
                                                             └─► WS-10 / WS-11 / WS-12(WS-4后) / WS-14 / WS-15
```
- **开发可并行，verify 门禁串行。** 每 WS 各自分支 + verify 绿 + 单 commit + STOP，主控复核后合并再起下一个。
- WS-3 与 WS-4 并行安全，唯一交叉文件 `video/components/UploadPanel.vue`、`video/components/UploadVideoDrawer.vue`（及 `material/components/MaterialUploadDrawer.vue`）——**WS-3 先动，WS-4 D3 最后再碰**。

## WS-4 工具链（2026-07-12 主控已装配，分发 05/06 前无需再装）
- **frontend-design skill**：user 级插件已装（`frontend-design@claude-plugins-official`）——opus 新会话自动加载，D0/D1/D4 设计决策必用。
- **Playwright MCP**：user 级已注册并 Connected（`npx -y @playwright/mcp@latest`），chromium 已预热——opus 浏览器实渲染/跨断点验证用。
- **naive-ui-skills**：`~/.claude/skills/naive-ui-skills/`——组件 API/themeOverrides 权威参考（opus 自动加载；codex 直接读该磁盘路径）。
- **OpenDesign 桌面端**：`D:\Open Design\`（v0.14.1）——`resources/open-design/craft|design-systems|skills` 为设计参考源（agent 只读）；GUI 出图/评审归主控。
- **跨断点自检协议**（已写入 plan §3 WS-4 与 05/06 提示词）：五断点 375×812/768×1024/1024×768/1280×800/1920×1080 硬门槛；D0 交付 `frontend/scripts/ui-shots.mjs` + `ui-routes.json`，D2–D6 改前/改后各跑一遍，任一断点水平滚动/重叠/console 新错 = 验收不过。

## 铁律速查（权威全文见 `docs/audit-remediation-plan.md` §0，每次开工务必先读）
1. **Git 本地私有**：无 remote、**永不 push**。每 WS 一条 `feature/wsNN-...` 分支 off `main`；verify 全绿后**单 commit**；然后 **STOP** 交主控复核合并（**不自行 merge、不 commit 后 checkout main**）。开工前后 `git remote -v` 确认为空。
2. **构建门禁**：`mvn -B -ntp clean verify` 全绿；动前端另跑 `cd frontend && npm run type-check && npm run build`（echarts/naive chunk 告警可忽略）。跑 verify 前按**精确 PID** 释放 :8080：
   `PID=$(netstat -ano | grep ":8080" | grep LISTENING | head -1 | awk '{print $NF}'); [ -n "$PID" ] && taskkill //PID $PID //F`
   **禁止广杀 java 进程**（只杀那个 :8080 PID 或自己起的孤儿 JVM）。
3. **verify 门禁串行**：开发可并行，但同一时刻只能跑一个 `mvn verify`（共享 dev 库 + :8080 jar 锁 + MinIO/Redis）。跑前 `tasklist //FI "IMAGENAME eq java.exe"` 确认无别的 surefire/failsafe 在跑。
4. **迁移编号**：库头**当前 V26**；**经 vetting 核实需迁移的：WS-3 / WS-8（必）+ WS-2（仅当走迁移而非纯 app-bootstrap 中和 admin，见其提示词①）**；WS-13/14 无迁移——取**开工时磁盘 max+1**（先 `ls platform-boot/src/main/resources/db/migration | sort -V | tail -1`），**三者别都假定自己是 V27**；两 WS 不占同号，冲突由主控合并时顺延（V27/V28/V29）；**不改已应用迁移**。testseed=`R__` 可重复迁移，demo 在 `db/demo`（非 Flyway，`DemoDataInitializer` 门禁加载）。
5. **共享 dev 库**（docker）：`tcp-mysql`(root/root123, db `teacher_cert`)、`tcp-redis`、`tcp-minio`(minioadmin/minioadmin123, bucket `teacher-cert`)。已知残留：① `test_%` 账号被翻 `must_change_pwd=1`；② IT `readyLogin` 把种子口令 `ChangeMe123!`→`Changed123!`（手测口令"漂移"根源）。IT 报 401 或手测登不上先复位：
   `docker exec tcp-mysql mysql -uroot -proot123 teacher_cert -e "UPDATE sys_user SET must_change_pwd=0 WHERE username LIKE 'test_%';"`（口令漂移参照桌面 `测试账号.txt` 哈希复位）。
6. **demo 数据陷阱**（审计 #2 成因）：**WS-1 落地前**跑 verify 先清 demo 行（demo 走开关随时可重载）；**WS-1 落地后**demo 可常驻共享库且 verify 仍绿（目标状态）。CI 用全新 `mysql:8.0` service，本就干净。
7. 应用 **dev 免 `JWT_SECRET` 启动**（Phase 45）。profile：dev=`migration+testseed`、prod=`migration only`、demo 追加 `platform.demo.enabled`。
8. **诚实**：安全修复带**复现→阻断**活体证据；可靠性/性能带 **IT + 代码边界**证据。复杂/推迟诚实标注、不伪造验收。每 WS 写 `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加条目。

---
*本提示词库生成于 2026-07-05，对应 `docs/audit-remediation-plan.md`。每份提示词末尾的"收尾"步骤是硬性交付门槛。*
