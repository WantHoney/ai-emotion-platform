# API 文档（与代码同步）
最后同步日期：`2026-02-21`

> HTTP 前缀：`/api`  
> WebSocket 通道：`/ws/tasks/stream`

## 1. 健康与系统
- `GET /api/health`
- `GET /api/health/db`
- `GET /api/health/ser`
- `GET /api/system/status`
- `GET /api/ai/diag`（开发诊断）

## 2. 认证
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/admin/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `POST /api/auth/logout`

## 3. 用户资源与上传

### 3.1 音频资源
- `POST /api/audio/upload`
- `GET /api/audio/list`
- `DELETE /api/audio/{id}`
- `GET /api/audios`
- `DELETE /api/audios/{audioId}`

### 3.2 分片上传会话
- `POST /api/audio/upload-sessions/init`
- `PUT /api/audio/upload-sessions/{uploadId}/chunks/{chunkIndex}`
- `GET /api/audio/upload-sessions/{uploadId}`
- `POST /api/audio/upload-sessions/{uploadId}/complete`
- `DELETE /api/audio/upload-sessions/{uploadId}`

## 4. 分析任务与报告
- `POST /api/audio/{audioId}/analysis/start`
- `GET /api/analysis/list`
- `GET /api/analysis/{analysisId}`
- `DELETE /api/analysis/{analysisId}`
- `GET /api/analysis/task/{taskId}`
- `GET /api/analysis/task/{taskId}/result`
- `GET /api/analysis/task/{taskId}/segments`
- `POST /api/analysis/{analysisId}/run`
- `POST /api/analysis/{analysisId}/run-async`
- `GET /api/analysis/{analysisId}/report`
- `POST /api/analysis/{analysisId}/report/generate`
- `GET /api/audio/{audioId}/analysis/latest`
- `GET /api/audio/{audioId}/analysis/list`

说明：代码中的路由约束为 `{analysisId:\d+}`，文档统一简写为 `{analysisId}`。

### 4.1 任务/报告资源聚合
- `GET /api/tasks`
- `GET /api/reports`
- `GET /api/reports/trend`
- `GET /api/reports/{reportId}`
- `DELETE /api/reports/{reportId}`

### 4.2 Mock 调试接口（开发环境）
- `POST /api/analysis/{analysisId}/mock-success`
- `POST /api/analysis/{analysisId}/mock-fail`
- `POST /api/analysis/{analysisId}/mock-segments`
- `POST /api/analysis/{analysisId}/mock-run`
- `POST /api/analysis/{analysisId}/mock-run-async`

## 5. 管理端接口（`/api/admin`）

### 5.1 治理与告警
- `GET /api/admin/models`
- `POST /api/admin/models`
- `POST /api/admin/models/{modelId}/switch`
- `GET /api/admin/models/switch-logs`
- `GET /api/admin/warning-rules`
- `POST /api/admin/warning-rules`
- `PUT /api/admin/warning-rules/{ruleId}`
- `POST /api/admin/warning-rules/{ruleId}/toggle`
- `GET /api/admin/warnings`
- `POST /api/admin/warnings/{warningId}/actions`
- `GET /api/admin/warnings/{warningId}/actions`
- `GET /api/admin/analytics/daily`
- `GET /api/admin/analytics/quality`
- `GET /api/admin/governance/summary`
- `POST /api/admin/governance/drift/scan`
- `GET /api/admin/metrics`

`POST /api/admin/governance/drift/scan` 支持可选 query 参数：
- `windowDays`
- `baselineDays`
- `mediumThreshold`
- `highThreshold`
- `minSamples`

### 5.2 CMS 内容管理
- `GET /api/home`
- `GET /api/psy-centers`
- `GET /api/admin/banners`
- `POST /api/admin/banners`
- `PUT /api/admin/banners/{id}`
- `DELETE /api/admin/banners/{id}`
- `GET /api/admin/quotes`
- `POST /api/admin/quotes`
- `PUT /api/admin/quotes/{id}`
- `DELETE /api/admin/quotes/{id}`
- `GET /api/admin/articles`
- `POST /api/admin/articles`
- `PUT /api/admin/articles/{id}`
- `DELETE /api/admin/articles/{id}`
- `GET /api/admin/books`
- `POST /api/admin/books`
- `PUT /api/admin/books/{id}`
- `DELETE /api/admin/books/{id}`
- `GET /api/admin/psy-centers`
- `POST /api/admin/psy-centers`
- `PUT /api/admin/psy-centers/{id}`
- `DELETE /api/admin/psy-centers/{id}`
- `GET /api/admin/psy-centers/export`
- `POST /api/admin/psy-centers/import`（`text/plain`）
- `GET /api/admin/dashboard/light`
- `POST /api/admin/content-events/click`

## 6. WebSocket 实时任务流

### 6.1 连接地址
- `ws://127.0.0.1:8080/ws/tasks/stream?taskId=<taskId>&accessToken=<token>`

### 6.2 鉴权与参数
- `taskId`：必填，正整数。
- `accessToken`：可通过 3 种方式提供。
- query：`accessToken` 或 `token`
- header：`Authorization: Bearer <token>`
- cookie：`accessToken=<token>` 或 `access_token=<token>`

### 6.3 推送行为
- 事件类型固定：`event = "snapshot"`。
- 推送间隔来自 `analysis.realtime.push-interval-ms`（默认 `1000` 毫秒）。
- 任务进入终态（`SUCCESS/FAILED/CANCELED`）后，发送最后快照并关闭连接。

### 6.4 Snapshot 字段
| 字段 | 类型 | 说明 |
|---|---|---|
| `event` | string | 固定 `snapshot` |
| `taskId` | number | 任务 ID |
| `taskNo` | string | 任务编号 |
| `status` | string | `PENDING/RUNNING/RETRY_WAIT/SUCCESS/FAILED/CANCELED` |
| `attemptCount` | number | 已尝试次数 |
| `maxAttempts` | number/null | 最大重试次数 |
| `traceId` | string/null | 链路 ID |
| `nextRunAt` | string/null | 下次调度时间 |
| `updatedAt` | string/null | 更新时间 |
| `errorMessage` | string/null | 错误信息 |
| `terminal` | boolean | 是否终态 |
| `risk` | object/null | 风险摘要 |
| `progress` | object/null | 阶段进度 |
| `curve` | array | 风险曲线点 |

`risk` 子字段：`riskScore`、`riskLevel`、`pSad`、`pAngry`、`varConf`、`textNeg`。  
`progress` 子字段：`phase`、`message`、`sequence`、`emittedAtMs`、`details`。  
`curve[]` 子字段：`index`、`startMs`、`endMs`、`emotion`、`confidence`、`riskIndex`。

### 6.5 关闭码
- `1000`：正常关闭（任务结束）
- `4400`：参数错误
- `4401`：未授权
- `4403`：无权限访问该任务
- `4500`：服务端内部错误

## 7. 同步原则
- 文档优先作为目录与字段契约，行为细节以代码与运行响应为准。
- 数据库迁移同步参考：`docs/db.md`。
