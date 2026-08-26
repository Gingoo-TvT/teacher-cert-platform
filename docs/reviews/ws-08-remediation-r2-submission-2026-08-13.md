# WS-8 Hosted selector profile 第二轮整改提交说明（2026-08-13）

## 1. 身份与裁定边界

- 分支：`feature/ws08-idcard-encryption`
- 基线 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- 第一轮整改候选 fingerprint：
  `dd8940dd20ae22b95495f35bc8fc8bef6e6209ea8ef24a395e3c3f0f888c9184`
- 正式报告：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\ws08-remediation-independent-rereview-20260813-dd8940dd\review.md`
  （SHA-256 `4948B89D03785F04AF46E0793A160C9C691506B303C022E7243DC76B008E5A4E`）
- 正式结论：`CHANGES_REQUESTED（0 Critical / 0 High / 1 Medium / 0 Low）`
- R2 manifest：
  `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws8-hosted-profile-remediation-candidate-2026-08-13.json`
- 提交者状态：`LOCAL_REMEDIATION_R2_READY / HOSTED_CI_EVIDENCE_PENDING`

本材料只请求重核唯一 Hosted CI profile Medium；不自行签发 WS-8 PASS，不放行 WS-9，也不授权产品主线 merge、
deploy 或 cutover。项目继续 `CHANGES_REQUESTED / NO-GO`。

## 2. 唯一 finding 与最小整改

冻结的 `dd8940dd...c9184` workflow 为 WS-8 selector 配置了数据库、Redis、MinIO、JWT 和身份证件 secret，但未
显式激活运行 profile。`Phase3StudentIT` / `Phase10ExchangeIT` 没有测试级 `@ActiveProfiles`，因此
`RuntimeProfileGuard` 会在上下文创建前拒绝启动；这是 fail-closed，不是假绿，但六-suite verifier/artifact 不可用。

R2 只在 `.github/workflows/ci.yml` 的“WS-8 加密、启动护栏与 V33 迁移精确门禁”step 局部 `env` 增加：

```yaml
SPRING_PROFILES_ACTIVE: dev
```

该变量不提升到 workflow 或 backend job 全局，不影响 Phase 0 exact gate，不修改生产 profile、测试注解、产品代码、
迁移或业务合同。第一轮复核已经关闭的 V33 切换、三项 secret 独立与 HMAC collision 不重开。

## 3. 冻结前静态证据

- `.github/workflows/ci.yml` 经 js-yaml 解析：PASS。
- `git diff --check -- .github/workflows/ci.yml`：PASS。
- `SPRING_PROFILES_ACTIVE: dev` 只存在于 WS-8 selector step 的局部 `env`。
- 未在本地伪造 Hosted 六-suite PASS；动态证据必须由 R2 carrier 的 GitHub Actions run 形成。

## 4. Hosted 验收合同

1. R2 manifest 在 Hosted run 前后 verify PASS，并记录 candidate → carrier → PR merge SHA 映射。
2. backend job 与 WS-8 selector step 成功。
3. 下载 `ws8-ci-evidence-<GITHUB_SHA>-attempt-<n>` artifact；记录 run ID、attempt、artifact ID/digest 或 ZIP SHA-256。
4. `test-summary.json` 的 `candidateSha/runId/runAttempt` 与 Hosted run 一致。
5. `reports/` 精确包含以下六份 fresh XML，且每 suite `tests > 0`，总计 0 failure/error/skip：
   - `IdCardProtectionServiceTest`
   - `RuntimeProfileGuardTest`
   - `V33IdCardProtectionMigrationIT`
   - `Phase3StudentIT`
   - `Phase10ExchangeIT`
   - `Phase41BackupIT`
6. 证据完成后移除本地临时 remote；不 merge Draft PR、不部署、不切流。

不需要重跑前端、生产 Compose、真实切流、攻击性测试、KMS/轮换或 WS-9 重构。
