# WS-7 · 容器/CI 供应链硬化（非 root / digest pin / SBOM） — 提示词（codex）

你是执行 **WS-7** 的 codex。对应 `docs/audit-remediation-plan.md` §4 WS-7（审计 #8）。

## 开工前必读
- 完整读 `docs/audit-remediation-plan.md` **§0** 与 **§4 WS-7**；`git remote -v` 空 → `git switch -c feature/ws07-supply-chain`。
- 相关文件：后端 `Dockerfile`、前端 `frontend/Dockerfile`、`.github/workflows/ci.yml`、`docker-compose.yml`。

## 任务
- runtime 镜像建**非 root 用户**（后端 `Dockerfile`〔runtime `eclipse-temurin:17-jre-jammy`，现**无 `USER`**=root〕+ 前端 `frontend/Dockerfile`〔`nginx:1.27-alpine`，现 root；非 root nginx 需调 `listen` 端口>1024 + `/var/cache/nginx`等目录属主〕）。
- base image 与 **GitHub Actions pin 到 digest/SHA**（现均**浮动 tag**：`maven:3.9-eclipse-temurin-17`/`eclipse-temurin:17-jre-jammy`/`node:20-alpine`/`nginx:1.27-alpine`）。
- **CI 现无镜像构建步骤**（只并行跑 `mvn verify` + 前端 `build`、无 `needs:`）→ 本 WS **新增镜像 build 步骤**并用 `needs:` 门控在 verify/前端 test **之后**（验证→构建）。
- 生成 **SBOM**（接 Trivy/OSV 可作后续，本 WS 先出 SBOM 产物）。

## 验收
- `docker run` 后进程 **UID≠0**。
- digest bump 需**显式 PR**（不再浮动 tag）。
- CI 顺序为"验证→构建"。

## 收尾（硬门槛）
1. 本地 `docker build` 两镜像通过 + `docker run` 验 UID≠0；CI 顺序自检。
2. 若触及后端构建，确保 `mvn -B -ntp clean verify` 仍绿（门禁串行 + 释放 :8080）。
3. `DEVLOG.md`（倒序）+ `docs/launch-readiness-plan.md` §11 追加 WS-7 条目。
4. **单 commit**（`feature/ws07-supply-chain`）→ **STOP** 交主控复核。**禁止 merge / push**。
