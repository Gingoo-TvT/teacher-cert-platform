# WS-5a · TLS/HSTS 全链路 + CSP 基线（T1/T2） — 提示词（codex）

你是执行 **WS-5 传输安全**的 codex。对应 `docs/audit-remediation-plan.md` §4 WS-5（T1/T2）+ launch-readiness 开放 **P0-9** + 审计 #3。opus 并行做 T3（refresh HttpOnly，`08-ws05b-opus-httponly-refresh.md`）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-5**；`git remote -v` 空 → `git switch -c feature/ws05-tls-csp`。
- 动前端 → 收尾跑 `cd frontend && npm run type-check && npm run build`。
- **联动 WS-3**：预签名 `minio.public-endpoint` 必须与站点**同 scheme（https）**；建议 MinIO 公网面走**同一反代/证书**（避免 mixed-content 拦直传/直下）。CSP 必须**放行 MinIO public endpoint**（见 T2）。

## 任务
### T1 TLS/HSTS 全链路（P1，最高优先）
- `frontend/nginx.conf` 中 443/HSTS 配置**已有注释模板**（实际 `:4-17`：443 ssl block + `ssl_certificate .../fullchain.pem` 标注"运维提供" + HSTS `:13` + `:17` 已备 `:80→301` 跳转行）——**取消注释启用它**：证书走卷挂载 + env 路径、HTTP→HTTPS **301**、**HSTS** 头（现 `:80` 块 `:28` 已有 P1-4 安全头，CSP 就加在那处）。
- `docker-compose.yml` 发布 **443** + 证书卷；`.env.example` / 部署文档写清证书来源（学校签发或 Let's Encrypt）。
- **依赖学校提供域名/证书**——未到位前先用**自签证书在内网**把整条路径演练通，文档标注"待正式证书替换"。
- 动机：当前 :80 明文传输登录密码/JWT/身份证 PII，面向数千学生的公网服务上线前必须关掉。

### T2 CSP 基线（审计 #3 快赢）
- `frontend/nginx.conf` 增严格 CSP：`default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'self'` 等。
- **放行 MinIO public endpoint**：`connect-src`/`media-src`/`img-src` 需含它，**否则 WS-3 直传/播放被 CSP 拦**。
- 缩短 access token TTL（现 `application.yml:36 access-ttl-seconds: ${JWT_ACCESS_TTL_SECONDS:3600}` = 1 小时 → 建议降至 5–15 分钟；`refresh-ttl-seconds:37` 保持）。

## 验收（活体）
- 全站 https 可达；http **301** 跳转；**HSTS 头**在。
- 响应含 **CSP**，且 WS-3 直传/播放**不被拦**（联动验证）。
- （自签阶段）文档明确标注待正式证书替换。

## 收尾（硬门槛）
1. 前端 `type-check` + `build` 绿；nginx 配置本地起栈验证。
2. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-5 T1/T2 条目（P0-9 勾销证据）。
3. **单 commit**（`feature/ws05-tls-csp`）→ **STOP** 交主控复核。**禁止 merge / push**。
