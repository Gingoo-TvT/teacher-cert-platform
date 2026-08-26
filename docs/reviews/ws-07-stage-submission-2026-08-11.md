# WS-7 容器/CI 供应链硬化阶段候选提交

> 后续独立复核（2026-08-11）：本提交绑定的候选 fingerprint
> `b8055c66ff1472955a0ad2556175ed0cbc747d0c48434820d290fa7fbbda223b` 已正式退回为
> `CHANGES_REQUESTED / INDEPENDENT_REVIEW_PENDING（0 Critical / 0 High / 1 Medium / 3 Low）`。
> 正式报告为
> `C:\Users\wenbibuhaoqwq\Documents\脚本\ws07-stage-independent-review-20260811-b8055c66\review.md`
> （SHA-256 `8f211ce9021f310a23a7476f57ae340bac89285a9bb288c08acdb494702383d8`）。
> 以下保留开发者首次提交快照；整改状态与新候选见当前统一执行入口，不把本文件原门禁结果改写为 PASS。

> 原提交状态：`LOCAL_STAGE_CANDIDATE_READY / INDEPENDENT_REVIEW_PENDING`
>
> 分支：`feature/ws07-supply-chain`
>
> HEAD：`aa3509a19151e2caad03014324b9b36e98bc0f05`
>
> 候选 manifest：
> `C:\Users\wenbibuhaoqwq\Documents\脚本\teacher-cert-ws7-stage-candidate-2026-08-11.json`

本文件是开发者提交说明，不是独立 PASS、项目发布 GO，也不授权 merge、push、deploy 或 cutover。

## 1. 冻结范围

- 后端 runtime 固定为非 root `10001:10001`，预建并授权媒体探测目录。
- 前端 runtime 固定为非 root `101:101`，Nginx 改用内部 8080/8443，并授权模板、cache 与 PID 路径。
- 四个 Dockerfile `FROM`、生产 MySQL/Redis/MinIO、CI service/CLI 与 Phase 41 手工 MySQL 镜像固定到
  `tag@sha256:<64 hex>`。
- `.github/workflows/*.yml|*.yaml` 的 action 全部固定到 40 位 commit SHA。
- 最终 `container-images` job 依赖 `phase41-runner-contract`、`backend`、`frontend`，只在三者成功后构建
  `sha-${GITHUB_SHA}` 双镜像，核验默认 UID 非 0，并配置生成双 SPDX JSON、镜像身份与校验和 artifact。
- 不纳入漏洞扫描、依赖扫描、签名、provenance、registry push、业务代码或 Flyway 变更。

## 2. 关键实现与防回归

- `scripts/test_ws7_supply_chain_contract.py` 使用 Python 标准库，覆盖完整 digest、`- uses:` / `uses:` action SHA、
  最终 job 的真实 `needs`、双镜像与各自 SBOM 输出映射、UID 反例、校验和、Compose 端口/健康检查、证书组及
  前端 Docker context。
- `frontend/.dockerignore` 排除宿主 `node_modules`、构建输出和本地环境文件；增量 build 的 context 从此前约
  249 MB 收敛到约 9 KB，production build 仍成功。
- 前端 Compose 加入 `TLS_CERTIFICATE_GID` 补充组。README 要求挂载实体证书（或目标完整位于挂载根内的链接）、
  最小组读权限，禁止私钥 world-readable。
- README/Phase 14 明确旧 root `video-probe-temp` 的停机升级：停全部旧 backend/worker、保留卷、一次性迁移
  `10001:10001`、默认用户写入检查通过后再启动；禁止删卷或在线改属主。

## 3. 开发者门禁结果

| 门禁 | 结果 |
|---|---|
| `python scripts/test_ws7_supply_chain_contract.py` | PASS |
| `npm --prefix frontend run test:csp-contract` | PASS |
| 两份 workflow 使用 `js-yaml` 解析 | PASS |
| `bash -n scripts/test-phase41-mysql-init-real.sh` | PASS |
| `docker compose --env-file .env.example -f docker-compose.yml config --quiet` | PASS |
| backend image build | PASS；全模块 Checkstyle/compile/testCompile + `-DskipTests package` |
| frontend image build | PASS；Vite 4977 modules，仅保留既有大 chunk warning |
| backend 默认 UID / probe 目录 | `10001` / `test -w` PASS |
| frontend 默认 UID | `101` |
| 非 root TLS 配置 | 临时证书 + 补充组 + 孤立 `backend` DNS 映射下 `nginx -t` PASS |
| `git diff --check` | PASS |

后端第一次镜像构建因 Docker 构建容器访问 Maven Central 超时失败；未改镜像源或绕过依赖，保持同一 Dockerfile
重试后成功。现有生产样容器、数据卷、Compose 服务和业务端口均未启动、停止或修改。

## 4. 独立复核边界

建议按一个完整 WS-7 阶段集中复核，不拆成小切片：

1. manifest 前后 verify 一致；核对所有 source/ref 与本候选绑定。
2. 重跑静态合同、Compose config、workflow YAML 解析。
3. 构建双镜像；以一次性容器核验 UID 非 0、backend probe 目录可写、frontend 带测试证书 `nginx -t`。
4. 在授权 GitHub Hosted CI 验证 `needs` 顺序、双镜像构建、两份 SPDX JSON、
   `image-identities.txt`、`SHA256SUMS` 与 artifact 上传。

开发者本地未运行 hosted CI，因而没有声称远端 SBOM artifact 已生成；也未运行完整真实依赖 `mvn verify`，
Dockerfile 的 skip-tests package 不能替代 CI backend job 的隔离 MySQL/Redis/MinIO 回归。项目状态继续为
`CHANGES_REQUESTED / NO-GO`，WS-7 独立 PASS 前不进入 WS-8。
