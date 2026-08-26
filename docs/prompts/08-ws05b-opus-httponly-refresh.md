# WS-5b · refresh token HttpOnly（T3） — 提示词（Opus 4.8）

你是执行 **WS-5 会话安全 T3** 的 opus。对应 `docs/audit-remediation-plan.md` §4 WS-5（T3，审计 #3 长期）。**契约敏感**——务必配 WS-6 的 E2E 回归。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-5**；`git remote -v` 空 → `git switch -c feature/ws05-httponly-refresh`。
- **依赖**：`Secure` cookie 依赖 **WS-5 T1 的 HTTPS**（codex 那条）——T1 落地后再验 Secure 生效；开发期可先在 http 打通逻辑，标注 Secure 待 TLS。
- **不要回退已有语义**：改密/重置后旧 token 立即 401 由 Phase 37a `TokenRevocationService` 保障——**动 refresh 流时必须保住**。

## 任务
- refresh token 迁 **HttpOnly + Secure + SameSite cookie 或 BFF session**；前端只在**内存**持短期 access token。
- 涉及：`frontend/src/stores/user.ts`、`frontend/src/api/request.ts`（401 重试/刷新流）、后端 refresh 端点（`AuthService`/AuthController `/api/auth/refresh`）+ 登录端点（若 login 改为 `Set-Cookie` 下发 refresh）。
- 保持登录/刷新/401 重试流可用。
- **⚠️ 直接受影响的 IT（已核实、契约会变、非保绿）**：`Phase2SecurityIT` 驱动刷新流是**请求体传 refreshToken + 响应体读 token**——`refresh()` helper（`:~278` `POST /api/auth/refresh` body `{refreshToken}` → 读 `/data/refreshToken`）、正路 `:~189`、反路 `:~194`（拿 access 当 refresh → 401）、login 读 `/data/refreshToken`（`:~262`）。迁 cookie 后这些**须改写**为 cookie 契约（登录 `Set-Cookie` → 刷新带 cookie）；且 **`TestRestTemplate` 默认不跨请求带 cookie**，改写须显式抓 `Set-Cookie` 回传。其余 IT（Phase7/12 等）的 `LoginResult` **捕获但不使用** refreshToken → 登录不再回传时它们得空串仍绿（勿误改）。

## 验收（活体，复现→阻断）
- **注入脚本无法读到 refresh token**（复现：`document.cookie` / JS 取不到；HttpOnly 生效）。
- （T1 后）cookie 带 Secure；SameSite 生效。
- 登录/刷新仍可用；改密/重置后旧 token 立即 401 语义**未回退**。

## 收尾（硬门槛）
1. 释放 :8080（精确 PID）+ verify 门禁串行；`mvn -B -ntp clean verify` 全绿；前端 `type-check`+`build` 绿。
2. **配 WS-6 的 Playwright E2E 回归**（登录/刷新/401 重试路径）——若 WS-6 未就绪，至少补 IT/手测活体证据并标注。
3. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-5 T3 条目。
4. **单 commit**（`feature/ws05-httponly-refresh`）→ **STOP** 交主控复核。**禁止 merge / push**。
