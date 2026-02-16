# 架构与模块说明

## 1. 总体架构

- **frontend（Vue3 + Vite）**
  - 用户端：上传音频、查看任务、报告与历史。
  - 运营端 CMS：内容管理与轻量看板。
- **backend（Spring Boot）**
  - REST API、鉴权、任务调度、报告持久化。
  - 可切换 AI 调用模式（mock / spring）。
- **MySQL**
  - 存储用户、音频、分析任务、分段结果、报告、CMS 内容。
- **可选 SER 服务（Python FastAPI）**
  - 在开启 `SER_ENABLED=true` 时由 backend 调用。

## 2. 前后端交互

- 前端默认开发端口：`5173`
- 后端默认端口：`8080`
- 前端通过 Vite proxy 将 `/api` 转发至后端。

## 3. backend 主要模块

- `controller/`：API 入口（auth、audio、analysis、cms、health 等）
- `service/`：业务编排（任务处理、报告生成、CMS 管理等）
- `repository/`：JDBC 数据访问
- `integration/`：对接 AI/SER/ASR 等外部能力
- `domain` / `dto`：领域对象与接口出参
- `config/`：数据源、资源映射、启动检查等配置

## 4. frontend 主要模块

- `src/views/`：业务页面（上传、任务、报告、系统、运营端等）
- `src/api/`：按模块封装 API（auth/audio/analysis/admin 等）
- `src/router/`：路由与权限入口
- `src/stores/`：Pinia 状态管理

## 5. 运行形态建议

- **本地开发最小链路**：frontend + backend + MySQL，AI 先用 `mock`。
- **联调/演示链路**：frontend + backend + MySQL + OpenRouter（可选 + SER）。
