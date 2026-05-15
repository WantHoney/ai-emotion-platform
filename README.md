# AI 语音情绪分析与心理状态预警 Web 系统

最后同步日期：`2026-05-15`

本仓库是毕业设计项目的完整工程，包含用户端、管理端、后端接口、语音情绪识别服务、数据库脚本、训练资料和答辩文档。

## 1. 项目结构

- `frontend/`：Vue 3 + Vite + Element Plus，包含用户端和管理端页面。
- `backend/`：Spring Boot + MySQL，负责登录认证、任务调度、报告管理、内容专栏、心理中心、预警治理等接口。
- `backend/ser-service/`：FastAPI 模型服务，负责语音转写、语音情绪识别、文本情感分析和多模态融合。
- `backend/docs/db/`：数据库基线脚本、迁移脚本和审计记录。
- `backend/data/datasets/`：本机保留的原始数据集目录，供答辩现场查看来源。
- `docs/`：论文、接口、架构、数据库和答辩相关文档。
- `docs/midterm_ppt/`：当前终辩 PPT。
- `scripts/`：本地启动、检查和演示辅助脚本。
- `DEFENSE_QUICK_GUIDE.md`：答辩现场快速定位手册。

## 2. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0`
- MySQL 8+
- Python 3.10+（用于模型服务）

Windows PowerShell 建议先切换为 UTF-8，避免中文日志乱码：

```powershell
./scripts/enable-utf8.ps1
```

## 3. 一键启动

Windows PowerShell：

```powershell
./scripts/dev-all.ps1
```

macOS / Linux：

```bash
bash ./scripts/dev-all.sh
```

脚本会按顺序启动：

1. 模型服务：`http://127.0.0.1:8001`
2. 后端服务：`http://127.0.0.1:8080`
3. 前端页面：`http://127.0.0.1:5173`

## 4. 常用访问地址

- 用户端首页：`http://127.0.0.1:5173/app/home`
- 用户登录：`http://127.0.0.1:5173/app/login`
- 管理端登录：`http://127.0.0.1:5173/admin/login`
- 管理端首页：`http://127.0.0.1:5173/admin/dashboard`
- 后端健康检查：`http://127.0.0.1:8080/api/health`
- 模型服务健康检查：`http://127.0.0.1:8001/health`

## 5. 核心功能

用户端：

- 登录注册与会话保持
- 音频上传、分片上传和任务创建
- 任务详情、实时进度和分析结果展示
- 报告详情、风险提示和建议展示
- 趋势分析、内容专栏、心理中心和每日推荐

管理端：

- 用户与角色管理
- 模型配置与切换记录
- 预警规则配置
- 预警事件处置与跟踪
- 内容管理、内容排期和内容中心维护
- 系统数据统计与治理概览

## 6. AI 与模型链路

当前工程采用“本地模型为主、外部增强可配置”的设计：

- 语音上传后先进入任务队列，由后端创建异步分析任务。
- 模型服务完成 ASR 转写、语音情绪识别、文本情感分析和多模态融合。
- 本地推理链路优先使用 wav2vec2 / faster-whisper / Transformers 相关模型。
- Ollama Gemma 4 用于本地大模型解释增强，让报告表达更自然。
- OpenRouter 外部模型能力保留为可配置增强和兜底方案，不作为系统主链路。

默认本地模型配置可通过环境变量覆盖：

- `SER_ENGINE`
- `SER_HF_MODEL_DIR_ZH`
- `SER_HF_MODEL_DIR_EN`
- `TEXT_HF_MODEL_ZH`
- `FUSION_MODEL_DIR`
- `AI_MODE`
- `OPENROUTER_API_KEY`
- `OPENROUTER_MODEL`

## 7. 数据库口径

数据库基线脚本：

- `backend/docs/db/schema_v1.sql`

增量迁移脚本按版本顺序执行，当前最新迁移为：

- `backend/docs/db/migrations/V11__content_hub_daily_schedule.sql`

终辩口径：

- 当前系统按 V11 版本统计为 `31` 张业务表。
- V11 新增 `content_daily_schedule`、`content_daily_item`、`user_content_history`。
- 表结构分域说明见 `docs/db.md`。

## 8. 数据集口径

本机保留的原始数据集目录在：

- `backend/data/datasets/CASIA_raw/`
- `backend/data/datasets/ESD_raw/`
- `backend/data/datasets/IEMOCAP_raw/`
- `backend/data/datasets/RAVDESS_raw/`

终辩 PPT 使用的数据来源口径：

- CASIA：`800`
- ESD：`28,000`
- IEMOCAP：`5,531`
- RAVDESS：`864`

最终训练 / 验证 / 测试划分：

- 训练集：`26707`
- 验证集：`4139`
- 测试集：`4349`

## 9. 常用检查命令

后端编译：

```bash
cd backend
mvn -q -DskipTests compile
```

前端类型检查：

```bash
cd frontend
npm run type-check
```

接口与文档同步检查：

```bash
python scripts/check_doc_sync.py
```

快速接口检查：

```powershell
./scripts/smoke-api.ps1
```

## 10. 现场演示建议

现场查找功能时，优先打开 `DEFENSE_QUICK_GUIDE.md`。其中整理了页面、接口、代码目录和数据库表之间的对应关系，适合答辩时快速定位。

常见定位：

- 上传与任务：`frontend/src/views/app/UploadView.vue`、`backend/src/main/java/com/wuhao/aiemotion/controller/AudioUploadController.java`
- 报告详情：`frontend/src/views/app/ReportDetailView.vue`、`backend/src/main/java/com/wuhao/aiemotion/controller/ReportController.java`
- 内容专栏：`frontend/src/views/app/ContentHubView.vue`、`backend/src/main/java/com/wuhao/aiemotion/controller/ContentController.java`
- 心理中心：`frontend/src/views/app/PsyCentersView.vue`、`backend/src/main/java/com/wuhao/aiemotion/controller/PsyCenterController.java`
- 预警治理：`frontend/src/views/admin/AdminWarningsView.vue`、`backend/src/main/java/com/wuhao/aiemotion/controller/AdminWarningController.java`

## 11. 常见问题

如果前端访问 `/api/*` 报 `ECONNREFUSED`：

1. 确认后端运行在 `127.0.0.1:8080`。
2. 确认前端运行在 `127.0.0.1:5173`。
3. 检查 `frontend/vite.config.ts` 中的代理地址。

如果 `/api/health` 显示模型服务不可用：

1. 先启动 `backend/ser-service`。
2. 访问 `http://127.0.0.1:8001/health`。
3. 查看 `backend/ser-service/logs/` 下的日志。

如果需要启用外部大模型增强：

1. 设置 `AI_MODE=spring`。
2. 设置 `SPRING_AI_OPENAI_ENABLED=true`。
3. 配置 `OPENROUTER_API_KEY` 和 `OPENROUTER_MODEL`。
