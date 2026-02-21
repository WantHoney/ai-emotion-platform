# API 概览（按模块）

> HTTP 基础前缀：`/api`  
> WebSocket 实时通道：`/ws`

## 1. 健康检查

- `GET /api/health`
- `GET /api/health/db`
- `GET /api/health/ser`

## 2. 认证与用户

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/admin/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `POST /api/auth/logout`

## 3. 音频资源

- `POST /api/audio/upload`
- `GET /api/audio/list`
- `DELETE /api/audio/{id}`
- `GET /api/audios`
- `DELETE /api/audios/{audioId}`

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

## 5. 任务实时流（WebSocket）

### 5.1 连接地址

- `ws://127.0.0.1:8080/ws/tasks/stream?taskId=<taskId>&accessToken=<token>`

查询参数：

- `taskId`：任务 ID（必填，正整数）
- `accessToken`：用户登录令牌（必填，Bearer token 原文）

说明：

- 服务端会按 `analysis.realtime.push-interval-ms`（默认 `1000ms`）推送快照。
- 任务进入终态（`SUCCESS/FAILED/CANCELED`）后，服务端发送最后一条快照并主动关闭连接。

### 5.2 消息事件

当前仅发送一种事件：

- `event = "snapshot"`

消息结构（字段名与代码保持一致）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `event` | string | 固定为 `snapshot` |
| `taskId` | number | 任务 ID |
| `taskNo` | string | 任务编号 |
| `status` | string | `PENDING/RUNNING/RETRY_WAIT/SUCCESS/FAILED/CANCELED` |
| `attemptCount` | number | 已尝试次数 |
| `maxAttempts` | number | 最大重试次数 |
| `traceId` | string | 链路追踪 ID |
| `nextRunAt` | string | 下次调度时间（重试等待时有值） |
| `updatedAt` | string | 最近更新时间 |
| `errorMessage` | string | 错误信息（失败时有值） |
| `terminal` | boolean | 是否终态 |
| `risk` | object \| null | 风险摘要 |
| `progress` | object \| null | 阶段进度 |
| `curve` | array | 风险曲线点列表 |

`risk` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `riskScore` | number | 风险分（0~1） |
| `riskLevel` | string | 风险等级 |
| `pSad` | number | 悲伤概率 |
| `pAngry` | number | 愤怒概率 |
| `varConf` | number | 情绪波动项 |
| `textNeg` | number | 文本负向强度 |

`progress` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `phase` | string | 阶段码（如 `CLAIMED/ASR_RUNNING/TEXT_DONE/SER_RUNNING/PERSISTING/DONE/FAILED`） |
| `message` | string | 阶段描述 |
| `sequence` | number | 该任务内单调递增序号 |
| `emittedAtMs` | number | 服务端毫秒时间戳 |
| `details` | object | 阶段附加信息 |

`curve[]` 元素字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `index` | number | 序号 |
| `startMs` | number | 片段起始毫秒 |
| `endMs` | number | 片段结束毫秒 |
| `emotion` | string | 片段情绪标签 |
| `confidence` | number | 片段置信度（0~1） |
| `riskIndex` | number | 片段风险指数（0~100） |

### 5.3 快照示例

```json
{
  "event": "snapshot",
  "taskId": 1201,
  "taskNo": "TASK-1201",
  "status": "RUNNING",
  "attemptCount": 1,
  "maxAttempts": 4,
  "traceId": "c4f0f6f1a0d84f7c9a8b4c9d38a5b901",
  "nextRunAt": null,
  "updatedAt": "2026-02-21 23:48:10",
  "errorMessage": null,
  "terminal": false,
  "risk": {
    "riskScore": 0.5213,
    "riskLevel": "ATTENTION",
    "pSad": 0.4121,
    "pAngry": 0.1064,
    "varConf": 0.2318,
    "textNeg": 0.4876
  },
  "progress": {
    "phase": "SER_RUNNING",
    "message": "正在执行语音情绪识别与融合",
    "sequence": 6,
    "emittedAtMs": 1771688890123,
    "details": {
      "textLength": 42
    }
  },
  "curve": []
}
```

### 5.4 关闭码约定

| 关闭码 | 含义 |
|---|---|
| `1000` | 正常关闭（任务完成后） |
| `4400` | 参数错误（如 `taskId` 非法） |
| `4401` | 未授权（缺少或失效 token） |
| `4403` | 无权限访问该任务 |
| `4500` | 服务端内部错误 |

## 6. CMS / 运营端

### 6.1 对外内容接口

- `GET /api/home`
- `GET /api/psy-centers`

### 6.2 运营管理接口（`/api/admin`）

- Banner：`GET/POST/PUT/DELETE /api/admin/banners[/{id}]`
- 金句：`GET/POST/PUT/DELETE /api/admin/quotes[/{id}]`
- 文章：`GET/POST/PUT/DELETE /api/admin/articles[/{id}]`
- 书籍：`GET/POST/PUT/DELETE /api/admin/books[/{id}]`
- 心理机构：`GET/POST/PUT/DELETE /api/admin/psy-centers[/{id}]`
- 心理机构导入导出：
- `GET /api/admin/psy-centers/export`
- `POST /api/admin/psy-centers/import`
- 看板：`GET /api/admin/dashboard/light`
- 埋点：`POST /api/admin/content-events/click`

## 7. 调试接口（仅开发环境建议开启）

- `GET /api/ai/diag`
- `POST /api/analysis/{analysisId}/mock-success`
- `POST /api/analysis/{analysisId}/mock-fail`
- `POST /api/analysis/{analysisId}/mock-segments`
- `POST /api/analysis/{analysisId}/mock-run`
- `POST /api/analysis/{analysisId}/mock-run-async`

## 8. 说明

- 本文档为接口目录与关键字段说明，最终以代码与运行时响应为准。
- 若后续引入 OpenAPI/Swagger，可在此基础上自动生成并持续校验一致性。
