# API 概览（按模块）

> 基础前缀：`/api`

## 1. 健康检查

- `GET /health`
- `GET /health/db`
- `GET /health/ser`

## 2. 认证与用户

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/admin/login`
- `POST /auth/refresh`
- `GET /auth/me`
- `POST /auth/logout`

## 3. 音频资源

- `POST /audio/upload`
- `GET /audio/list`
- `DELETE /audio/{id}`
- `GET /audios`
- `DELETE /audios/{audioId}`

## 4. 分析任务与报告

- `POST /audio/{audioId}/analysis/start`
- `GET /analysis/list`
- `GET /analysis/{analysisId}`
- `DELETE /analysis/{analysisId}`
- `GET /analysis/task/{taskId}`
- `GET /analysis/task/{taskId}/result`
- `GET /analysis/task/{taskId}/segments`
- `POST /analysis/{analysisId}/run`
- `POST /analysis/{analysisId}/run-async`
- `GET /analysis/{analysisId}/report`
- `POST /analysis/{analysisId}/report/generate`
- `GET /audio/{audioId}/analysis/latest`
- `GET /audio/{audioId}/analysis/list`

## 5. CMS / 运营端

### 5.1 对外内容接口

- `GET /home`
- `GET /psy-centers`

### 5.2 运营管理接口（`/admin`）

- Banner：`GET/POST/PUT/DELETE /admin/banners[/{id}]`
- 金句：`GET/POST/PUT/DELETE /admin/quotes[/{id}]`
- 文章：`GET/POST/PUT/DELETE /admin/articles[/{id}]`
- 书籍：`GET/POST/PUT/DELETE /admin/books[/{id}]`
- 心理机构：`GET/POST/PUT/DELETE /admin/psy-centers[/{id}]`
- 心理机构导入导出：
  - `GET /admin/psy-centers/export`
  - `POST /admin/psy-centers/import`
- 看板：`GET /admin/dashboard/light`
- 埋点：`POST /admin/content-events/click`

## 6. 调试接口（仅开发/受配置限制）

- `GET /ai/diag`
- `POST /analysis/{analysisId}/mock-success`
- `POST /analysis/{analysisId}/mock-fail`
- `POST /analysis/{analysisId}/mock-segments`
- `POST /analysis/{analysisId}/mock-run`
- `POST /analysis/{analysisId}/mock-run-async`

## 7. 说明

- 以上为接口目录级概览，详细字段请结合 controller DTO 与前端调用代码。
- 若需 Swagger，可在后续版本统一补充 OpenAPI 定义。
