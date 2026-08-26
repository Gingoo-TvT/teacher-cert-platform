# WS-9 · 拆分巨型服务类（Exchange / VideoReview） — 提示词（Opus 4.8）

你是执行 **WS-9** 的 opus。对应 `docs/audit-remediation-plan.md` §4 WS-9（审计 #7）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-9**；`git remote -v` 空 → `git switch -c feature/ws09-split-services`。
- **顺序红线**：**WS-3 先行**。WS-3 把视频直传逻辑挪出后，`VideoReviewServiceImpl` 上传段自然瘦身——**等 WS-3 合并后再拆**，避免双向冲突。

## 任务
- `platform-exchange/.../service/impl/ExchangeServiceImpl.java`（**现 1677 行**，非 ~1547、已增长）抽：`ExchangeImportValidator` / `ExchangeExportRowBuilder` / `ExchangeRollbackService` / 字典下拉 helper。
- `platform-business/.../video/service/impl/VideoReviewServiceImpl.java`（**现 1542 行**，非 ~1420）抽：`VideoUploadComposer` / `VideoReviewSettlement` / VO 转换。
- **⚠️ 行数会再变、按符号定位**：**WS-3 先落地**后 Video 上传段（`uploadChunk`/`merge`/`composeServerSide`/`registerComposedFile`）大改并挪出，`VideoReviewServiceImpl` 自然瘦身——**开工时 `wc -l` 复核实际行数**，别信本文数字。
- **手法**：先抽**无状态纯函数 helper**；controller 依赖**更窄接口**。纯重构、不改行为。

## 验收
- 拆分前后 `mvn -B -ntp clean verify` **119 IT 全绿**（demo 驻留库照跑，WS-1 已落地）——重点护栏：**导出行字段快照**（`Phase14E2EIT` 断言导出 Excel 各列单元格 `:~589-603`、`Phase10ExchangeIT` 读工作簿字节）、**视频结算 IT**（`Phase7VideoReviewIT` 对 `finalScore`/`finalConclusion`/`REVIEW_COMPLETED` 的断言）——纯重构后逐字段/逐字节不变。
- 新 helper 补 **focused 单测**。**⚠️ 现状**：全库**没有纯单元测试**（84+ 个测试全是 `@SpringBootTest` boot 层 IT，见 launch-readiness §9.3）——helper 的 focused 单测是**首批纯单测**，放到对应 module 的 `src/test`（纯函数直测、不起 Spring 上下文），别硬塞进 boot IT。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-9 条目。
3. **单 commit**（`feature/ws09-split-services`）→ **STOP** 交主控复核。**禁止 merge / push**。
