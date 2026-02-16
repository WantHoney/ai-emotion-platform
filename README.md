# AI Emotion Platform

AI Emotion Platform 是一个前后端一体的 monorepo，包含：
- **用户端 Web**：上传语音、查看情绪分析任务与报告。
- **内容运营端 CMS**：管理 Banner/金句/文章/书籍/心理机构等内容。
- **后端服务**：提供认证、音频上传、情绪分析、报告查询、CMS 管理 API。

> 说明：本系统用于教学/研究场景，不作为医疗诊断依据。

## 目录结构

```text
ai-emotion-platform/
├── frontend/                  # Vue3 + Vite 用户端/运营端前端
├── backend/                   # Spring Boot API + 任务处理
│   ├── docs/db/               # 数据库基线与迁移 SQL
│   └── ser-service/           # 可选 Python SER 服务
└── docs/
    ├── architecture.md        # 架构与模块说明
    ├── api.md                 # API 概览
    ├── db.md                  # 数据库说明
    └── archive/               # 归档的历史/临时文档
```

## 开发启动

### 1) 启动 backend（默认 `http://localhost:8080`）

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

### 2) 启动 frontend（默认 `http://localhost:5173`）

```bash
cd frontend
npm install
npm run dev
```

前端 Vite 已将 `/api` 代理到 `http://localhost:8080`。

## 环境变量说明（示例）

请优先用环境变量注入配置，不要在仓库提交真实密钥。

### backend 关键变量

- `AI_MODE`：`mock` / `spring`
- `OPENROUTER_API_KEY`：OpenRouter API Key
- `OPENROUTER_BASE_URL`：默认 `https://openrouter.ai/api`
- `OPENROUTER_MODEL`：默认 `openrouter/free`
- `APP_CORS_ALLOWED_ORIGINS`：默认 `http://localhost:5173`
- `SER_ENABLED` / `SER_BASE_URL`：可选 SER 服务开关与地址
- `AUTH_SEED_ADMIN_USERNAME` / `AUTH_SEED_ADMIN_PASSWORD`：默认运营端账号

### frontend

当前以 Vite proxy 为主，不强制 `.env`。如需自定义可新增：

- `VITE_API_BASE_URL`（若不走 `/api` 代理时使用）

## 常见问题

### 1) 前端请求报 `ECONNREFUSED`（Vite proxy）

排查顺序：
1. backend 是否已启动并监听 `8080`。
2. `frontend/vite.config.ts` 的 `server.proxy['/api'].target` 是否为 `http://localhost:8080`。
3. backend CORS 与端口配置是否改动。
4. 本机是否有代理软件劫持 localhost 或端口占用。

### 2) backend 启动失败（数据库）

- 检查 MySQL 是否运行，且已导入 `backend/docs/db/schema_v1.sql`。
- 检查 `spring.datasource.*` 是否与本地一致。

### 3) AI 功能不可用

- `AI_MODE=spring` 时需配置 `OPENROUTER_API_KEY`。
- 本地联调可先改为 `AI_MODE=mock`。

## 文档导航

- 架构说明：`docs/architecture.md`
- API 概览：`docs/api.md`
- 数据库说明：`docs/db.md`
- 归档文档：`docs/archive/`（历史 brief/checklist，保留追溯）
