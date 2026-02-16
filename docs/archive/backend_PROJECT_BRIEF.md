# 项目简报（PROJECT BRIEF）

## 1. 题目 / 一句话简介

**题目**：面向用户的 AI 语音情绪分析与心理状态预警 Web 系统（后端）。

**一句话简介**：这是一个以 Spring Boot 为核心的后端系统，支持语音上传、情绪分析任务（同步/异步）、分段情绪结果与心理风险评估报告输出，用于毕业设计中的“辅助评估 + 预警支持”场景。

---

## 2. 背景与意义（毕业设计表述）

随着心理健康关注度提升，传统人工访谈方式在覆盖率、时效性和客观性上存在局限。语音中蕴含的声学特征与语义特征可作为情绪状态的辅助信号。本项目通过构建“语音上传—模型分析—风险评估—报告展示”的完整后端链路，实现对用户心理状态风险的早期辅助识别，为后续干预提供数据支持。

本系统定位为**教学与研究用途**，不替代医疗诊断，强调“辅助决策、风险预警、流程可追溯”。

---

## 3. 核心功能清单（按模块）

### 3.1 登录与用户
- **当前状态**：数据库已有 `auth_user / auth_role / auth_user_role` 等表设计，但后端尚未提供登录/注册/JWT 鉴权接口。
- **现阶段策略**：业务接口以“无鉴权”方式供前端联调，后续补齐认证与授权。

### 3.2 情绪识别（核心）
- 上传音频文件，保存文件元数据。
- 创建分析任务（task queue）。
- 执行分析（同步 `run` / 异步 `run-async`）。
- 支持 Mock 模式与 Spring AI（OpenRouter）模式切换。
- 可选对接独立 SER 服务（Python）。

### 3.3 报告
- 查询分析详情、聚合报告、分段情绪结果。
- 支持报告生成并落库（`/report/generate`）。
- 返回风险评估字段（risk_score/risk_level 等，任务结果接口中提供）。

### 3.4 历史记录
- 音频分页列表、按音频查看分析历史、获取最新分析。
- 支持软删除音频与分析记录状态删除。

### 3.5 管理端（后台）
- 提供分析记录总览列表（支持按状态过滤）。
- 可查看单条分析详情、报告、任务执行状态。

---

## 4. 技术栈

- **前端**：当前仓库未包含前端代码（建议 Vue3 + Vite + TypeScript 对接）。
- **后端**：Java 17、Spring Boot 3.3.x、Spring Web、Validation、JDBC（JdbcTemplate）。
- **模型/AI**：
  - Spring AI + OpenRouter（在线 LLM 推理）
  - Mock AI（本地开发）
  - 可选独立 SER 服务（Python/FastAPI + Whisper）。
- **数据库**：MySQL 8.x。
- **部署**：Dockerfile + docker-compose（backend + db + ser-service）。

---

## 5. 系统架构

### 5.1 前后端交互方式
- 前端通过 RESTful API 调用后端（默认 `http://localhost:8080/api/...`）。
- 文件上传采用 `multipart/form-data`。
- 报告、列表、详情采用 JSON 返回。

### 5.2 鉴权方式
- **当前**：无登录态、无 JWT、无 Session。
- **规划**：基于 `auth_*` 表补齐登录鉴权，建议采用 JWT（Access + Refresh）。

### 5.3 主要数据流
1. 前端上传音频 -> `audio_file` 落库。
2. 前端创建分析任务 -> `analysis_task` 新增 `PENDING` 任务。
3. Worker/接口触发执行 -> AI/SER 分析。
4. 结果落库 -> `analysis_result` + `analysis_segment`。
5. 前端查询任务状态、结果、报告，展示趋势与风险。

---

## 6. 目录结构导览（重要目录与职责）

- `src/main/java/com/wuhao/aiemotion/controller`：REST API 入口。
- `src/main/java/com/wuhao/aiemotion/service`：业务编排（上传、任务、报告、风险评分）。
- `src/main/java/com/wuhao/aiemotion/repository`：数据库访问层。
- `src/main/java/com/wuhao/aiemotion/integration`：AI/SER/ASR 外部能力集成。
- `src/main/java/com/wuhao/aiemotion/dto`：接口响应结构体。
- `src/main/resources/application*.yaml`：运行配置。
- `docs/db`：数据库 schema 与迁移脚本。
- `ser-service`：可选 Python SER 服务。

---

## 7. 前端页面与路由清单（建议版）

> 说明：当前仓库无前端代码，以下为**可直接开工**的页面拆分建议。

| 路由 | 页面职责 | 关键状态 | 依赖接口 |
|---|---|---|---|
| `/login` | 登录页（后续） | token/userInfo/loading | 暂无（待补） |
| `/dashboard` | 仪表盘（总体数据） | 最近分析、风险分布 | `GET /api/analysis/list` |
| `/audio/upload` | 上传音频 | file、uploadProgress | `POST /api/audio/upload` |
| `/audio/list` | 音频列表 | page/size/filter/list | `GET /api/audio/list`、`DELETE /api/audio/{id}` |
| `/analysis/list` | 分析记录列表（管理端） | status/page/list | `GET /api/analysis/list` |
| `/analysis/:id` | 分析详情 | detail/report/segments | `GET /api/analysis/{id}`、`GET /api/analysis/{id}/report` |
| `/analysis/task/:taskId` | 任务状态页 | status/polling | `GET /api/analysis/task/{taskId}`、`GET /api/analysis/task/{taskId}/result` |
| `/analysis/new/:audioId` | 发起分析页 | runMode/status | `POST /api/audio/{audioId}/analysis/start`、`POST /api/analysis/{id}/run(-async)` |
| `/history/:audioId` | 某音频历史分析 | list/latest | `GET /api/audio/{audioId}/analysis/list`、`GET /api/audio/{audioId}/analysis/latest` |

---

## 8. 后端接口列表（URL / 方法 / 参数 / 返回 / 错误）

### 8.1 健康检查
- `GET /api/health`
- 返回：`"ok"`

### 8.2 音频模块

1) `POST /api/audio/upload`
- 请求：`multipart/form-data`，字段 `file`
- 返回：`audioId, originalName, fileName, downloadUrl`

2) `GET /api/audio/list?page=1&size=10&userId=&onlyUploaded=true`
- 返回：`total, page, size, items[]`
- `items` 字段：`id, userId, originalName, storedName, url, sizeBytes, durationMs, status, createdAt`

3) `DELETE /api/audio/{id}`
- 返回：`audioId, status(DELETED)`

### 8.3 分析任务模块（推荐前端优先对接）

1) `POST /api/audio/{audioId}/analysis/start`
- 返回：`taskId, status`

2) `GET /api/analysis/task/{taskId}`
- 返回：`taskId, status, attempt_count, next_run_at, error_message, created_at, updated_at, overall`

3) `GET /api/analysis/task/{taskId}/result`
- 返回：`analysis_result, analysis_segment, segments_total, segments_truncated`

4) `GET /api/analysis/task/{taskId}/segments?fromMs=&toMs=&limit=&offset=`
- 返回：`taskId, fromMs, toMs, limit, offset, total, items`

### 8.4 分析记录/报告模块

1) `GET /api/analysis/list?page=1&size=10&status=`（管理端）
- 返回：`total, page, size, items[]`

2) `GET /api/analysis/{analysisId}`
- 返回：`id, audioId, modelName, modelVersion, status, summary, errorMessage, createdAt, updatedAt`

3) `GET /api/analysis/{analysisId}/report`
- 返回：`analysisId, audioId, modelName, modelVersion, status, summary, errorMessage, createdAt, updatedAt, overall, segments`

4) `POST /api/analysis/{analysisId}/report/generate`
- 返回：同 report

5) `GET /api/audio/{audioId}/analysis/latest`
- 返回：`AudioAnalysisDetailResponse`

6) `GET /api/audio/{audioId}/analysis/list?page=1&size=10`
- 返回：`total, page, size, items[]`

7) `POST /api/analysis/{analysisId}/run`
- 返回：完整报告（同步）

8) `POST /api/analysis/{analysisId}/run-async`
- 返回：`analysisId, status`

9) `DELETE /api/analysis/{analysisId}`
- 返回：`analysisId, status`

### 8.5 开发调试接口（条件启用）

- `POST /api/analysis/{analysisId}/mock-success`
- `POST /api/analysis/{analysisId}/mock-fail?msg=`
- `POST /api/analysis/{analysisId}/mock-segments`
- `POST /api/analysis/{analysisId}/mock-run`
- `POST /api/analysis/{analysisId}/mock-run-async`

### 8.6 通用错误码（当前全局异常）

- `400`：参数错误/业务前置条件不满足（`IllegalArgumentException`）。
- `429`：上游模型限流（含 `Retry-After`）。
- `500`：内部错误或上游服务异常。
- `503`：SER 服务不可用。

---

## 9. 数据库表结构（核心表与关系）

### 9.1 主流程核心表
- `audio_file`：音频文件主表。
- `audio_analysis`：分析记录（旧链路/报告聚合维度）。
- `analysis_task`：任务队列表。
- `analysis_result`：任务总体结果（1:1）。
- `analysis_segment`：任务分段结果（1:N）。

### 9.2 关系概览
- `audio_file (1) -> (N) audio_analysis`
- `analysis_task (1) -> (1) analysis_result`
- `analysis_task (1) -> (N) analysis_segment`
- `audio_analysis` 与 `audio_segment/segment_emotion/core_report` 共同支撑报告查询。

### 9.3 鉴权相关（已建表但接口未接入）
- `auth_user`, `auth_role`, `auth_user_role`, `auth_menu`, `auth_role_menu`

---

## 10. 当前进度

### 已完成
- 音频上传、列表、软删除。
- 分析任务创建/查询/结果查询/分段查询。
- 分析执行同步与异步链路。
- 报告查询与报告生成接口。
- 全局异常返回结构统一。
- Docker 化部署基础链路。

### 进行中
- SER 服务联调稳定性与超时重试策略打磨。
- 风险评估结果与报告字段的一致性对齐。

### 未开始 / 待完善
- 登录鉴权（JWT/权限控制）。
- 前端工程落地与页面开发。
- 监控告警与审计日志完善。

---

## 11. 目前最大问题 / 技术风险

1. **鉴权缺失**：接口当前无登录态保护，无法直接上线。
2. **跨域策略未显式配置**：前端开发联调可能遇到 CORS 问题。
3. **模型时延与稳定性**：上游 LLM/SER 在高并发下可能超时或限流。
4. **任务状态一致性**：异步任务失败重试与状态回写需持续验证。
5. **文件上传安全性**：文件类型校验、防滥用与存储清理策略需加强。

---

## 12. 近期 TODO（按优先级，可直接开写）

### P0（本周必须）
1. 新增认证接口：`/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout`。
2. 给核心业务接口加鉴权拦截与角色控制（普通用户/管理员）。
3. 明确 CORS 配置（dev 允许 `http://localhost:5173`）。
4. 输出统一 API 文档（OpenAPI/Swagger 或 markdown 固化）。

### P1（前端联调期）
1. 为任务状态接口增加推荐轮询间隔字段（如 `pollIntervalMs`）。
2. 报告接口补充图表友好字段（情绪占比数组、时间轴聚合）。
3. 补齐“按用户维度”的历史记录接口。
4. 增加批量删除/恢复能力（管理端）。

### P2（论文与答辩优化）
1. 增加关键链路压测数据（吞吐、时延、错误率）。
2. 增加可观测性（traceId、结构化日志、慢查询统计）。
3. 形成“实验对比”与“误差分析”报告模板。

