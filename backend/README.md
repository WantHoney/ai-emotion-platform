# AI 语音情绪分析与心理状态预警系统（后端）

> 本仓库当前是 **Spring Boot 后端单体工程**，用于支撑语音上传、情绪分析任务管理、报告生成与查询。前端与独立 Python AI 服务暂未纳入本仓库。

## 1. 项目简介

本项目服务于毕业设计《面向用户的 AI 语音情绪分析与心理状态预警 Web 系统》，定位为“**辅助评估 + 预警支持**”后端：

- 提供语音上传与资源访问能力。
- 提供分析任务创建、执行（同步/异步）与状态管理能力。
- 提供分段情绪结果、聚合结论与报告快照读取能力。
- 支持 Mock / Spring AI 两种推理模式切换。

⚠️ 免责声明：本系统仅用于教学与研究，不作为医疗诊断依据。

---

## 2. 技术栈

- Java 17
- Spring Boot 3.3.x
- Spring Web + Validation + JDBC (JdbcTemplate)
- MySQL 8.x
- Spring AI（可选，`ai.mode=spring` 时启用）
- Maven

---

## 3. 仓库结构（当前）

```text
ai-emotion-backend/
├── src/main/java/com/wuhao/aiemotion
│   ├── controller/          # REST API
│   ├── service/             # 业务逻辑
│   ├── repository/          # JDBC 数据访问层
│   ├── integration/ai/      # AI 调用抽象与实现（mock/spring）
│   ├── domain/              # 领域模型（record）
│   ├── dto/                 # 响应与报告 DTO
│   ├── config/              # 资源映射、DB 启动检查
│   └── exception/           # 全局异常处理
├── src/main/resources/
│   └── application.yaml     # 本地配置
├── docs/
│   ├── db/schema_v1.sql     # 数据库基线 SQL
│   ├── db/*.md              # 数据库一致性/评审文档
│   ├── ai/*.md              # AI 集成设计说明
│   └── runbook_v1.md        # mock-only 快速联调脚本
├── pom.xml
└── README.md
```

---

## 4. 本地开发环境准备

### 4.1 依赖要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x

### 4.2 配置文件准备

建议将 `src/main/resources/application.yaml` 按本机环境修改，至少确认：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `app.upload.dir`（必须可写）
- `ai.mode`（默认 `mock`）

推荐额外准备一个 `application-local.yaml`（并通过 `--spring.profiles.active=local` 启动）来避免将本机账号密码写入默认配置。

---

## 5. 数据库迁移 / 初始化

当前仓库使用 SQL 脚本方式维护 schema（未引入 Flyway/Liquibase）。

### 5.1 初始化数据库

```bash
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql
```

执行后会创建 `ai_emotion` 数据库及核心表（`audio_file`、`audio_analysis`、`audio_segment`、`emotion_label`、`segment_emotion`、`core_report` 等）。

### 5.2 迁移策略建议（后续）

- 新增 `docs/db/migrations/` 目录，采用版本化脚本（如 `V2__*.sql`）。
- 引入 Flyway 后可改为 `mvn flyway:migrate` 自动迁移。

---

## 6. 启动后端

### 6.1 Maven 方式

```bash
mvn spring-boot:run
```

### 6.2 Wrapper 方式

```bash
./mvnw spring-boot:run
```

默认端口 `8080`。可通过健康检查验证：

```bash
curl http://localhost:8080/api/health
```

---

## 7. 启动前端（当前仓库说明）

本仓库 **不包含前端 Vue 项目代码**。

联调方式建议：

1. 在独立前端仓库启动 Vue（通常 `npm install && npm run dev`）。
2. 将前端 API Base URL 指向 `http://localhost:8080`。
3. 使用本 README 第 9 节 API 进行联调。

---

## 8. 启动 AI 子系统（当前实现）

当前 AI 子系统集成在后端进程内，通过 `ai.mode` 切换：

### 8.1 Spring 模式（默认，推荐生产/真实联调）

```yaml
ai:
  mode: spring
```

行为：默认走 Spring AI + OpenRouter 真实推理（需设置 `OPENROUTER_API_KEY`）。可通过 `AI_MODE=mock` 临时覆盖为 mock。

### 8.2 配置 OpenRouter（环境变量）

最小可用配置（建议全部通过环境变量注入，不要把 Key 写进 YAML）：

```bash
export AI_MODE=spring
export OPENROUTER_API_KEY=<your_openrouter_api_key>
export OPENROUTER_BASE_URL=https://openrouter.ai/api
export OPENROUTER_MODEL=openrouter/free
```

说明：
- `OPENROUTER_BASE_URL` 推荐 `https://openrouter.ai/api`，配合 Spring AI OpenAI-compatible 路径拼接后，请求会落到 `/api/v1/chat/completions`（避免手动写成双 `/v1`）。
- `OPENROUTER_MODEL` 默认就是免费模型 `openrouter/free`；你也可以显式改成其他 `:free` 或付费模型（例如 `openai/gpt-4o-mini`）。

`application.yaml` 读取方式：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENROUTER_API_KEY:}
      base-url: ${OPENROUTER_BASE_URL:https://openrouter.ai/api}
      chat:
        options:
          model: ${OPENROUTER_MODEL:openrouter/free}

ai:
  mode: ${AI_MODE:spring}
```

行为：当 `ai.mode=spring` 且 `OPENROUTER_API_KEY` 存在时，后端会走 Spring AI 的真实 OpenRouter 调用；开发场景可通过 `dev` profile 或 `ai.mock.enabled=true` 暴露 `mock-run` / `mock-run-async` 端点。应用启动时会输出 `AI startup mode=..., chatClientPresent=...` 便于确认是否加载了真实 ChatClient。

> 当前仓库未包含独立 Python 推理服务。如需 Python 模型服务，应新增独立项目并通过 HTTP/RPC 对接。

### 8.3 启用 Mock 端点（仅开发/测试）

`/mock-run` 与 `/mock-run-async` 默认不暴露。仅在以下条件下启用：
- `dev` profile 激活，或
- `ai.mock.enabled=true`

示例：

```bash
# 仅启用 mock 端点，但 /run 仍可按 ai.mode 决定走真实或 mock
export AI_MODE=spring
export OPENROUTER_API_KEY=<your_openrouter_api_key>
./mvnw spring-boot:run --spring-boot.run.arguments="--ai.mock.enabled=true"
```

如果要完整切换到 mock 执行链路，可设置：

```bash
export AI_MODE=mock
./mvnw spring-boot:run
```

> 在 `AI_MODE=mock` 或 `ai.mock.enabled=true` 时，应用会在启动阶段自动设置 `spring.ai.openai.enabled=false`，从而关闭 OpenAI 自动配置。


---

## 9. 本地联调测试（推荐流程）

假设服务已在 `localhost:8080` 运行，且存在 `./sample.mp3`：

### 9.1 上传音频

```bash
curl -X POST -F "file=@./sample.mp3" http://localhost:8080/api/audio/upload
```

### 9.2 创建分析任务

```bash
curl -X POST "http://localhost:8080/api/audio/<audioId>/analysis/start"
```

### 9.3 执行分析（同步或异步）

```bash
# 同步
curl -X POST "http://localhost:8080/api/analysis/<analysisId>/run"

# 异步
curl -X POST "http://localhost:8080/api/analysis/<analysisId>/run-async"
```

### 9.4 查询报告

```bash
curl -X GET "http://localhost:8080/api/analysis/<analysisId>/report"
```


### 9.5 Postman 调用真实 LLM（diag + run + report）

1) 在 Postman 的环境变量中设置：
- `baseUrl` = `http://localhost:8080`

2) **先做诊断请求**：
- `GET {{baseUrl}}/api/ai/diag`（建议在 `dev` profile 或 `--ai.diag.enabled=true` 下调用）
- 重点确认：
  - `chatClientPresent = true`
  - `resolvedBaseUrl = https://openrouter.ai/api`（或你自定义值）
  - `resolvedModel = openrouter/free`（或你指定模型）
  - `resolvedChatCompletionsEndpoint` 结尾是 `/api/v1/chat/completions`

3) 再触发分析执行：
- 同步：`POST {{baseUrl}}/api/analysis/{{analysisId}}/run`

4) 最后查询报告：
- `GET {{baseUrl}}/api/analysis/{{analysisId}}/report`

如果上游（OpenRouter）返回 `401/402/429/404` 等错误，后端会在错误消息中透出 `upstreamStatus` 与上游 JSON body，便于你在 Postman 直接调试。

示例返回（真实 LLM，节选）：

```json
{
  "analysisId": 101,
  "status": "SUCCESS",
  "summary": {
    "overallEmotion": "neutral",
    "confidence": 0.78,
    "summary": "语音整体情绪平稳，后段出现轻微紧张。"
  }
}
```

### 9.6 常见问题

- `400 audio_id 不存在`：先确认上传成功并使用正确 `audioId`。
- `500`：优先检查数据库连通性、上传目录权限、AI 模式参数。
- 异步场景下立即查详情可能仍是 `RUNNING`，稍后重试。

---

## 10. 当前能力边界（重要）

- ✅ 已有：音频上传、分析任务、mock/spring 推理接入、报告读写、异步执行。
- ⚠️ 待完善：认证授权、任务队列化、模型治理、前端项目集成、CI/CD、自动迁移。


---

## 11. SER（音频信号）推理服务集成

本仓库新增 `ser-service/`（FastAPI）用于 **真实语音情绪识别（SER）**：

- 接口：`POST /ser/analyze`
- 入参：`multipart/form-data`，字段 `file`（音频文件），可选 `segment_ms`、`overlap_ms`
- 能力：自动调用 ffmpeg 转 16kHz mono wav，按窗口分段后做预训练模型推理
- 输出：`overall` + `segments` + `meta`

### 11.1 快速启动（Backend + SER + DB）

```bash
docker compose up --build
```

默认：
- backend: `http://localhost:8080`
- ser-service: `http://localhost:8001`
- mysql: `localhost:3306`

### 11.2 后端开关配置

```yaml
ser:
  enabled: true
  base-url: http://localhost:8001
  segment-ms: 8000
  overlap-ms: 0
  llm-summary-enabled: false
```

说明：
- `ser.enabled=true` 时，`/api/analysis/{id}/run` 优先走 SER 音频推理并落库到 `audio_segment`/`segment_emotion`。
- LLM 仅作为可选叙事摘要扩展（默认关闭）。

### 11.3 Postman 验证（简版）

1) 上传音频（支持 m4a）
- `POST /api/audio/upload`（form-data: `file=@xx.m4a`）

2) 创建分析
- `POST /api/audio/{audioId}/analysis/start`

3) 执行分析
- `POST /api/analysis/{analysisId}/run`

4) 查询报告
- `GET /api/analysis/{analysisId}/report`

预期字段：
- `overall.emotionCode`
- `overall.confidence`
- `segments[]`（包含 `startMs/endMs/emotions[].code/emotions[].score`）

如果 ser-service 不可用，后端会返回 `503`，message 为：`SER service unavailable`。

## 12. 首页聚合 + 心理中心资源 + CMS（新增）

### 12.1 数据库变更（需手动执行）

请手动执行：`docs/db/migrations/V4__home_cms_content.sql`。

新增表：
- `banners`
- `quotes`
- `articles`
- `books`
- `psy_centers`
- `content_events`

### 12.2 新增接口（用户端）

- `GET /api/home`：首页聚合（banner、今日一句、推荐专栏、推荐书籍、自助练习入口）
- `GET /api/psy-centers?cityCode=310100&limit=20`：按城市查询心理中心资源
- `GET /api/psy-centers?latitude=31.23&longitude=121.47&radiusKm=10&limit=20`：按定位附近查询

> 定位数据仅用于当次本地推荐，后端不保存精确定位轨迹。

### 12.3 新增接口（运营端 CMS）

内容 CRUD：
- `GET/POST/PUT/DELETE /api/admin/banners`
- `GET/POST/PUT/DELETE /api/admin/quotes`
- `GET/POST/PUT/DELETE /api/admin/articles`
- `GET/POST/PUT/DELETE /api/admin/books`
- `GET/POST/PUT/DELETE /api/admin/psy-centers`

资源点导入导出：
- `GET /api/admin/psy-centers/export`：导出 CSV
- `POST /api/admin/psy-centers/import`（`text/plain`）：导入 CSV

轻量看板：
- `GET /api/admin/dashboard/light`：上传量、报告量、内容点击
- `POST /api/admin/content-events/click`：记录内容点击事件


## 13. 登录与权限（新增）

### 13.1 Auth 接口

- `POST /api/auth/register`：注册用户端账号（ROLE_USER）
- `POST /api/auth/login`：用户端登录
- `POST /api/auth/admin/login`：运营端登录（仅 ROLE_ADMIN）
- `POST /api/auth/refresh`：刷新 token
- `GET /api/auth/me`：获取当前登录用户信息
- `POST /api/auth/logout`：退出登录

默认内置运营账号（可通过配置覆盖）：
- `operator / operator123`

配置项：
- `auth.seed-admin.username`
- `auth.seed-admin.password`
- `auth.access-token-ttl-seconds`
- `auth.refresh-token-ttl-seconds`

> 当前为内存 token/session 实现，用于联调与演示；服务重启后登录态会失效。

### 13.2 接口权限规则

- **公开接口（无需 token）**：
  - `/api/health/**`
  - `/api/auth/login` `/api/auth/register` `/api/auth/admin/login` `/api/auth/refresh`
  - `/api/home`
  - `/api/psy-centers/**`
- **需登录（USER 或 ADMIN）**：其余 `/api/**`
- **仅运营端（ADMIN）**：
  - `/api/admin/**`
  - `/api/analysis/list`
  - `/api/admin/metrics`

请求头：
- `Authorization: Bearer <accessToken>`
