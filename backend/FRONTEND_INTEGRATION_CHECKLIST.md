# 前端对接清单（Frontend Integration Checklist）

> 目标：让前端可以立刻进入“写页面 + 调接口 + 联调”。

> 参考：可直接复制使用的 TypeScript 对接模板见 `docs/frontend/api-client-example.ts`（含 `types + axios + 轮询 hook`）。

## 1. API 可用性盘点

### 1.1 已可用（可直接联调）

| 模块 | API | 说明 |
|---|---|---|
| 健康检查 | `GET /api/health` | 服务存活探针 |
| 音频上传 | `POST /api/audio/upload` | 上传后拿 `audioId` |
| 音频列表 | `GET /api/audio/list` | 支持分页、可按 `userId` 过滤 |
| 音频删除 | `DELETE /api/audio/{id}` | 软删除 |
| 创建任务 | `POST /api/audio/{audioId}/analysis/start` | 创建 `analysis_task` |
| 查任务状态 | `GET /api/analysis/task/{taskId}` | 轮询主接口 |
| 查任务结果 | `GET /api/analysis/task/{taskId}/result` | 拿总体+分段 |
| 分段分页 | `GET /api/analysis/task/{taskId}/segments` | 图表页可按窗口拉取 |
| 管理列表 | `GET /api/analysis/list` | 可按状态筛选 |
| 分析详情 | `GET /api/analysis/{analysisId}` | 基本元数据 |
| 报告查询 | `GET /api/analysis/{analysisId}/report` | 详情页核心接口 |
| 报告生成 | `POST /api/analysis/{analysisId}/report/generate` | 触发生成并回包 |
| 同步执行 | `POST /api/analysis/{analysisId}/run` | 一次拿结果 |
| 异步执行 | `POST /api/analysis/{analysisId}/run-async` | 适合真实场景 |
| 历史列表 | `GET /api/audio/{audioId}/analysis/list` | 单音频历史 |
| 最新记录 | `GET /api/audio/{audioId}/analysis/latest` | 首屏快捷入口 |

### 1.2 认证接口（新增，可直接联调）
- `POST /api/auth/register`：用户注册（默认 USER）
- `POST /api/auth/login`：用户端登录
- `POST /api/auth/admin/login`：运营端登录（仅 ADMIN）
- `POST /api/auth/refresh`：刷新 access token（当前为 body 传 `refreshToken`）
- `GET /api/auth/me`：获取当前登录用户
- `POST /api/auth/logout`：退出登录

统一返回结构（`register/login/admin-login/refresh`）：
```json
{
  "accessToken": "atk_xxx",
  "refreshToken": "rtk_xxx",
  "accessExpiresIn": 7200,
  "refreshExpiresIn": 604800,
  "user": {
    "userId": 1001,
    "username": "demo",
    "role": "USER"
  }
}
```

> 默认内置运营账号：`operator / operator123`（可由配置覆盖）。

### 1.3 已有但仅开发环境建议使用
- `POST /api/analysis/{analysisId}/mock-run`
- `POST /api/analysis/{analysisId}/mock-run-async`
- `POST /api/analysis/{analysisId}/mock-success`
- `POST /api/analysis/{analysisId}/mock-fail`
- `POST /api/analysis/{analysisId}/mock-segments`

> 这些接口需要 `dev` profile 或 `ai.mock.enabled=true` 才会暴露。

### 1.4 当前缺失（前端常用但还没有）
1. 统一字典接口（情绪 code-name 映射、状态枚举等）。
2. 仪表盘聚合接口（总数、趋势、风险分层）。

---

## 2. 前端重点字段清单（页面导向）

## 2.1 列表页必需字段

### 音频列表 `/api/audio/list`
- 顶层：`total, page, size`
- `items[]`：`id, originalName, url, sizeBytes, durationMs, status, createdAt`

### 分析列表 `/api/analysis/list`
- 顶层：`total, page, size`
- `items[]`：`id, audioId, audioOriginalName, modelName, modelVersion, status, createdAt, updatedAt`

## 2.2 详情页必需字段

### 分析详情 `/api/analysis/{id}`
- `id, audioId, status, summary, errorMessage, createdAt, updatedAt`

### 报告详情 `/api/analysis/{id}/report`
- `overall.emotionCode / emotionNameZh / confidence`
- `segments[].startMs/endMs/transcript`
- `segments[].emotions[]`（图表和标签展示核心）

## 2.3 图表页必需字段

### 任务结果 `/api/analysis/task/{taskId}/result`
- 总体：`analysis_result.overall_emotion_code, overall_confidence, duration_ms`
- 风险：`analysis_result.risk_assessment.risk_score, risk_level, advice_text`
- 时间序列：`analysis_segment[].start_ms, end_ms, emotion_code, confidence`
- 分页辅助：`segments_total, segments_truncated`

---

## 3. 可直接使用的 Mock 数据样例

## 3.1 音频列表 Mock

```json
{
  "total": 2,
  "page": 1,
  "size": 10,
  "items": [
    {
      "id": 101,
      "userId": 1,
      "originalName": "sample_01.mp3",
      "storedName": "8b9be8fb47ef4271.mp3",
      "url": "/uploads/8b9be8fb47ef4271.mp3",
      "sizeBytes": 9673328,
      "durationMs": 45231,
      "status": "UPLOADED",
      "createdAt": "2026-02-10 10:12:33"
    }
  ]
}
```

## 3.2 任务状态 Mock

```json
{
  "taskId": 3001,
  "status": "RUNNING",
  "attempt_count": 1,
  "next_run_at": null,
  "error_message": null,
  "created_at": "2026-02-10 10:13:00",
  "updated_at": "2026-02-10 10:13:03",
  "overall": null
}
```

## 3.3 报告详情 Mock

```json
{
  "analysisId": 2001,
  "audioId": 101,
  "modelName": "openrouter/free",
  "modelVersion": "v1",
  "status": "SUCCESS",
  "summary": {
    "text": "整体情绪偏中性，后段出现轻度悲伤倾向"
  },
  "errorMessage": null,
  "createdAt": "2026-02-10 10:13:00",
  "updatedAt": "2026-02-10 10:13:20",
  "overall": {
    "emotionCode": "neutral",
    "emotionNameZh": "中性",
    "confidence": 0.81
  },
  "segments": [
    {
      "segmentId": 1,
      "startMs": 0,
      "endMs": 8000,
      "transcript": "今天总体还好",
      "emotions": [
        { "emotionId": 11, "code": "neutral", "nameZh": "中性", "scheme": "ekman", "score": 0.76 },
        { "emotionId": 12, "code": "sad", "nameZh": "悲伤", "scheme": "ekman", "score": 0.18 }
      ]
    }
  ]
}
```

## 3.4 图表页结果 Mock

```json
{
  "analysis_result": {
    "id": 9001,
    "task_id": 3001,
    "model_name": "openrouter/free",
    "overall_emotion_code": "sad",
    "overall_confidence": 0.67,
    "duration_ms": 45231,
    "sample_rate": 16000,
    "raw_json": "{}",
    "transcript": "今天状态一般",
    "risk_assessment": {
      "risk_score": 0.73,
      "risk_level": "MEDIUM",
      "advice_text": "建议关注近期睡眠与社交状态",
      "p_sad": 0.49,
      "p_angry": 0.11,
      "var_conf": 0.18,
      "text_neg": 0.41
    },
    "created_at": "2026-02-10 10:13:20"
  },
  "analysis_segment": [
    { "id": 1, "task_id": 3001, "start_ms": 0, "end_ms": 8000, "emotion_code": "neutral", "confidence": 0.76, "created_at": "2026-02-10 10:13:20" },
    { "id": 2, "task_id": 3001, "start_ms": 8000, "end_ms": 16000, "emotion_code": "sad", "confidence": 0.69, "created_at": "2026-02-10 10:13:20" }
  ],
  "segments_total": 2,
  "segments_truncated": false
}
```

---

## 4. 登录态存储与刷新策略（建议实现方案）

> 后端已提供基础 token 认证（内存态实现，重启失效），前端可先按此策略对接。

- **Access Token**：短期（30~120 分钟），放内存 + `localStorage` 备份。
- **Refresh Token**：长期（7~30 天），推荐 HttpOnly Cookie。
- **请求携带**：`Authorization: Bearer <accessToken>`。
- **刷新机制**：
  1. 业务请求返回 401 时，自动调用 `/api/auth/refresh`。
  2. 刷新成功后重放原请求。
  3. 刷新失败则清空登录态并跳转登录页。

---

## 5. 跨域与环境变量建议（dev/prod）

## 5.1 Base URL

- `dev`：`VITE_API_BASE_URL=http://localhost:8080`
- `prod`：`VITE_API_BASE_URL=https://<your-domain>`

## 5.2 CORS 建议（后端需补充）

- 允许源：`http://localhost:5173`（Vite 默认端口）。
- 允许方法：`GET,POST,PUT,DELETE,OPTIONS`。
- 允许头：`Authorization,Content-Type`。
- 若用 Cookie 刷新：需 `allowCredentials=true` 且前端 `withCredentials=true`。

## 5.3 前端环境变量模板

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080
VITE_ENABLE_MOCK=false

# .env.production
VITE_API_BASE_URL=https://api.example.com
VITE_ENABLE_MOCK=false
```

---

## 6. 进入联调前的一次性 Checklist

1. 后端启动并可访问 `GET /api/health`。
2. 前端 `.env.development` 已配置 `VITE_API_BASE_URL`。
3. 先跑通最短链路：上传 -> 创建任务 -> 异步执行 -> 轮询任务 -> 拉报告。
4. 页面级兜底：
   - `RUNNING` 显示进度占位；
   - `RETRY_WAIT` 显示“模型首次加载中，预计需要 1~3 分钟”；
   - 若 `next_run_at` 有值，前端按 `next_run_at - now` 实时显示倒计时（单位：秒）；
   - `FAILED` 展示 `error_message`；
   - `SUCCESS` 展示图表 + 风险建议。
5. 对接后统一抽一层 `apiClient`（含 401 刷新、错误提示、重试策略）。

