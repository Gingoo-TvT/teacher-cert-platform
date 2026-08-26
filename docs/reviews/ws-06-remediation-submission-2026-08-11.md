# WS-6 前端自动化门禁整改提交（2026-08-11）

> 后续正式复核：本提交冻结的 fingerprint `cf3776b6...b0fb0` 结论为
> `CHANGES_REQUESTED（0 High / 1 Medium / 0 Low）`；两个 Low 已关闭，multipart 大小写 Medium 为
> `PARTIAL / OPEN`。本文件以下正文保留首轮整改提交快照；第二轮提交见
> `docs/reviews/ws-06-remediation-r2-submission-2026-08-11.md`。
>
> 状态：`LOCAL_REMEDIATION_READY / INDEPENDENT_REREVIEW_PENDING`
>
> 本文是开发侧整改提交，不是独立复核 PASS，也不授权 merge、push、deploy、cutover 或项目发布。

## 1. 正式退回基线

- 候选 HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
- 候选 fingerprint：`3146a0c476097f7be4458bd6846b4899d370475cbee7e34f478500aea3fa45c7`
- 正式报告：`C:\Users\wenbibuhaoqwq\Documents\脚本\ws06-stage-independent-evidence-20260811\independent-review-report.md`
- 报告 SHA-256：`C242899A8A9DC67463817D54A17BAD5364C8201B1668E758AC232E4CD27800D0`
- 结论：`CHANGES_REQUESTED（0 High / 1 Medium / 2 Low）`

## 2. 原三项整改

1. **Medium — multipart file 合同**：从 Playwright 实际请求头解析动态/quoted boundary；精确校验首个 part 的
   `name="file"`、`filename="students.xlsx"`、XLSX MIME，并用 Buffer 比较
   `WS6-XLSX-FIXTURE` 原字节和 closing boundary。没有把二进制 body 整体转字符串，也没有新增 parser 依赖。
2. **Low — 401 Authorization**：refresh mock 先模拟真实 store token 更新；单次请求精确断言
   `expired-token → refreshed-token`，并发请求精确断言两路均 `expired-token → shared-token` 且只 refresh 一次；
   另以真实 Pinia store 的 `restoreSession()` 覆盖成功 refresh 后 token、identity、roles 与 permissions 应用。
3. **Low — tests/config 静态门禁**：ESLint 扩至 `tests` 与 Vite/Vitest/Playwright config；新增独立
   `tsconfig.tests.json` 与 `type-check:tests`，并接入 CI。Node 20 types 只进入测试程序，不污染 app type-check。

## 3. 开发侧门禁

| 门禁 | 结果 |
|---|---:|
| `npm ci` | PASS |
| 扩面 ESLint | PASS |
| app `vue-tsc --noEmit` | PASS |
| tests/config `tsc --noEmit -p tsconfig.tests.json` | PASS |
| Vitest | 4 files / 16 tests PASS |
| 既有前端合同 | 4/4 PASS |
| Vite production build | PASS（仅既有大 chunk warning） |
| Playwright Chromium + scoped axe | 5/5 PASS |
| `git diff --check` | PASS |

## 4. 复核与范围边界

新候选身份冻结在仓库外：

`C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws6-remediation-candidate-2026-08-11.json`

增量复核只需核对原 1 Medium / 2 Low 及必要回归；无需 Docker、真实数据库、新测试平台、全站 WCAG、
全仓格式化或生产功能重构。正式 PASS 前 WS-6 不关闭，也不进入 WS-7。
