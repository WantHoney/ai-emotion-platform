# FRONTEND_API_INTEGRATION

## 1. API BaseURL 来源（配置位置）

| 配置项 | 位置 | 当前值/行为 | 说明 |
|---|---|---|---|
| Axios `baseURL` | `src/api/http.ts` | `'/'` | 所有接口请求以站点根路径为起点 |
| Vite 代理 | `vite.config.ts` | `/api -> http://localhost:8080` | 仅开发环境生效，解决本地联调跨域 |

> 结论：当前项目通过“相对路径 + Vite 代理”实现本地 API 对接，尚未提供 `.env` 驱动的多环境 BaseURL。

## 2. 鉴权方式（Token / Session / Cookie）

- 当前实现：**无鉴权**。
- 前端未见：
  - 登录接口调用；
  - token 存储（localStorage/sessionStorage/cookie）；
  - axios 请求拦截器注入 `Authorization`；
  - 401/403 全局处理。

## 3. 接口与前端页面/模块映射

| 接口 | 方法 | 前端调用函数 | 页面/模块 | 用途 |
|---|---|---|---|---|
| `/api/audio/{audioId}/analysis/start` | POST | `startAnalysis(audioId)` | `UploadView.vue` | 启动分析任务，返回 `taskId` |
| `/api/analysis/task/{taskId}` | GET | `getTask(taskId)` | `AnalysisView.vue` | 查询任务状态和分析结果（轮询） |

## 4. 接口字段明细

### 4.1 启动分析任务

- **接口**：`POST /api/audio/{audioId}/analysis/start`
- **前端入口函数**：`startAnalysis(audioId: number)`

#### 请求字段

| 位置 | 字段 | 必选 | 类型 | 说明 |
|---|---|---|---|---|
| Path | `audioId` | 是 | number | 音频主键 ID（来自页面输入） |
| Body | - | 否 | - | 当前前端未传请求体 |

#### 返回字段

| 字段 | 必选 | 类型 | 说明 |
|---|---|---|---|
| `taskId` | 是（按前端当前假设） | number | 新建分析任务 ID，用于跳转详情页 |

#### 示例请求/返回

**请求**
```http
POST /api/audio/123/analysis/start
```

**返回（示例）**
```json
{
  "taskId": 456
}
```

#### UI 显示位置
- `taskId` 不直接展示为字段，但用于前端路由跳转到 `/analysis/456`。

#### 是否已接入
- **是**（Upload 页面已调用）

---

### 4.2 查询任务详情

- **接口**：`GET /api/analysis/task/{taskId}`
- **前端入口函数**：`getTask(taskId: number)`

#### 请求字段

| 位置 | 字段 | 必选 | 类型 | 说明 |
|---|---|---|---|---|
| Path | `taskId` | 是 | number | 分析任务 ID（来自路由参数） |
| Query | - | 否 | - | 当前前端未传查询参数 |

#### 返回字段

| 字段 | 必选 | 类型 | 说明 |
|---|---|---|---|
| `id` | 是（按前端类型定义） | number | 任务 ID |
| `status` | 是 | string | 任务状态（轮询停止条件依赖该字段） |
| `result` | 否 | object | 成功后分析结果 |
| `result.overall` | 否 | string | 总体情绪 |
| `result.confidence` | 否 | number | 置信度 |
| `result.risk_score` | 否 | number | 风险评分 |
| `result.risk_level` | 否 | string | 风险等级 |
| `result.advice_text` | 否 | string | 建议文本 |

#### 示例请求/返回

**请求**
```http
GET /api/analysis/task/456
```

**返回（处理中示例）**
```json
{
  "id": 456,
  "status": "RUNNING"
}
```

**返回（完成示例）**
```json
{
  "id": 456,
  "status": "SUCCESS",
  "result": {
    "overall": "calm",
    "confidence": 0.93,
    "risk_score": 0.12,
    "risk_level": "low",
    "advice_text": "建议保持规律作息并持续观察情绪波动。"
  }
}
```

#### UI 显示字段所在页面

| 返回字段 | 页面 | 展示组件 |
|---|---|---|
| `status` | `AnalysisView.vue` | `el-descriptions-item` |
| `result.overall` | `AnalysisView.vue` | `el-descriptions-item` |
| `result.confidence` | `AnalysisView.vue` | `el-descriptions-item` |
| `result.risk_score` | `AnalysisView.vue` | `el-descriptions-item` |
| `result.risk_level` | `AnalysisView.vue` | `el-descriptions-item` |
| `result.advice_text` | `AnalysisView.vue` | `el-descriptions-item` |

#### 是否已接入
- **是**（Analysis 页面已轮询调用）

## 5. 接口接入状态总览

| 接口 | 接入状态 |
|---|---|
| 启动分析任务 `POST /api/audio/{audioId}/analysis/start` | 是 |
| 查询任务详情 `GET /api/analysis/task/{taskId}` | 是 |
| 登录鉴权相关接口 | 否 |
| 文件上传相关接口 | 否 |

## 6. Mock 数据样例（用于前端先行开发）

### 6.1 启动任务 mock

```json
{
  "taskId": 10001
}
```

### 6.2 任务处理中 mock

```json
{
  "id": 10001,
  "status": "RUNNING"
}
```

### 6.3 任务成功 mock

```json
{
  "id": 10001,
  "status": "SUCCESS",
  "result": {
    "overall": "anxious",
    "confidence": 0.88,
    "risk_score": 0.67,
    "risk_level": "medium",
    "advice_text": "建议增加放松训练，如呼吸练习，并减少连续高压工作时长。"
  }
}
```

### 6.4 任务失败 mock

```json
{
  "id": 10001,
  "status": "FAILED"
}
```

## 7. 当前对接问题与风险

1. **字段契约风险**：前端强依赖 `taskId`、`status`、`result.*` 命名；一旦后端字段名或层级变化会直接影响页面展示。
2. **状态枚举未统一管理**：前端用字符串字面量判断结束态（`SUCCESS/FAILED/CANCELED`），建议后端文档明确所有状态值。
3. **鉴权缺失导致未来 403 风险**：如果后端改为鉴权保护，当前前端会直接请求失败。
4. **错误处理缺乏细分**：无法区分 400 参数错误、404 资源不存在、500 服务异常，排障效率低。
5. **上传链路未打通**：现阶段只有“分析启动 + 任务查询”，缺少真实音频上传 API 与页面。
6. **开发代理仅覆盖本地联调**：生产环境若跨域部署，需要后端 CORS 或网关配置配合。
