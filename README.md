# AI Emotion Monorepo

本仓库为前后端一体 monorepo：

- `backend/`: Spring Boot 3 + Maven + MySQL
- `frontend/`: Vue 3 + Vite + Element Plus

## 1. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0`
- MySQL 8+

## 2. 端口约定

- Backend: `http://127.0.0.1:8080`
- Frontend (Vite): `http://127.0.0.1:5173`
- Vite 代理: `http://127.0.0.1:5173/api/* -> http://127.0.0.1:8080/api/*`

## 3. 快速启动

### 3.1 分别启动

1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

2. 启动前端

```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

### 3.2 一键启动（根目录）

- Windows PowerShell:

```powershell
./scripts/dev-all.ps1
```

- macOS / Linux:

```bash
bash ./scripts/dev-all.sh
```

## 4. 启动后自检

### 4.1 健康检查

```bash
curl http://127.0.0.1:8080/api/health
```

预期返回 200，示例：

```json
{"status":"DEGRADED","db":"UP","ser":"DOWN"}
```

### 4.2 代理联调检查

```bash
curl http://127.0.0.1:5173/api/health
curl http://127.0.0.1:5173/api/home
curl "http://127.0.0.1:5173/api/psy-centers?cityCode=310100"
```

## 5. 核心接口（当前实现）

- `GET /api/health`
- `GET /api/system/status`
- `GET /api/home`
- `GET /api/psy-centers?cityCode=310100`（兼容 `city_code`）
- `GET /api/reports?page=1&pageSize=10&keyword=&riskLevel=&emotion=&sortBy=createdAt&sortOrder=desc`
- `GET /api/reports/{id}`
- `GET /api/tasks?page=1&pageSize=10&status=&keyword=&sortBy=createdAt&sortOrder=desc`

列表接口统一结构：

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "pageSize": 10,
  "size": 10
}
```

错误结构统一：

```json
{
  "code": "HTTP_401",
  "message": "缺少 Authorization Bearer Token",
  "traceId": "xxxxxx"
}
```

## 6. 常见问题

### 6.1 前端报 `ECONNREFUSED`

- 确认 backend 已启动在 `127.0.0.1:8080`
- 确认 `frontend/vite.config.ts` 的 proxy target 为 `http://127.0.0.1:8080`

### 6.2 后端启动提示 OpenAI API key

- 默认走 `spring` 模式，需要可用的 `OPENROUTER_API_KEY`
- 若你只做本地联调、不依赖真实模型，可临时切换：
  - `AI_MODE=mock`

### 6.3 MySQL 连接问题

- 可通过环境变量覆盖：
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
