# 终辩现场快速定位清单

## 1. 明天现场先打开这些

- PPT：`docs/midterm_ppt/吴昊_毕设终辩.pptx`
- 一键启动 GUI：`powershell -ExecutionPolicy Bypass -File scripts/ai-emotion-launcher-gui.ps1`
- 一键启动命令行：`powershell -ExecutionPolicy Bypass -File scripts/launch-best-dev.ps1`
- 停止服务：`powershell -ExecutionPolicy Bypass -File scripts/stop-dev-all.ps1`
- 用户端：`http://127.0.0.1:5173/app/home`
- 管理端：`http://127.0.0.1:5173/admin/login`
- 管理员账号：`operator / operator123`

## 2. 项目目录怎么讲

- `frontend/`：Vue 3 前端，用户端和管理端页面都在这里。
- `backend/`：Spring Boot 后端，接口、任务调度、权限、预警和内容管理在这里。
- `backend/ser-service/`：FastAPI 模型服务，本地语音情绪、文本情感和融合模型运行在这里。
- `backend/docs/db/`：数据库建表和迁移脚本，最新迁移是 `V11__content_hub_daily_schedule.sql`。
- `docs/GD/`：论文和论文图片材料。
- `docs/midterm_ppt/`：只保留最终答辩 PPT。
- `scripts/`：启动、停止、烟测和压测脚本。

## 3. 老师指功能时从这里找

| 功能 | 前端页面 | 后端入口 | 核心表 |
| --- | --- | --- | --- |
| 登录 / 注册 | `frontend/src/components/auth/UserAuthDialog.vue`、`frontend/src/views/user/UserLoginView.vue` | `backend/src/main/java/com/wuhao/aiemotion/controller/AuthController.java`、`service/AuthService.java` | `auth_user`、`auth_role`、`auth_user_role`、`auth_session` |
| 音频上传 | `frontend/src/views/user/UploadView.vue`、`frontend/src/api/audio.ts` | `controller/AudioController.java`、`service/AudioService.java`、`service/AnalysisTaskService.java` | `audio_file`、`audio_upload_session`、`audio_upload_chunk`、`analysis_task` |
| 任务详情 / 实时进度 | `frontend/src/views/user/TaskView.vue`、`frontend/src/composables/useTaskRealtimeStream.ts` | `controller/ResourceController.java`、`service/AnalysisTaskWorkerService.java`、`service/TaskRealtimeSnapshotService.java` | `analysis_task`、`analysis_result`、`analysis_segment` |
| 报告详情 | `frontend/src/views/user/ReportView.vue`、`frontend/src/api/report.ts` | `controller/ResourceController.java`、`service/ResourceManagementService.java`、`service/NarrativeGenerationService.java` | `report_resource`、`analysis_result` |
| 趋势分析 | `frontend/src/views/user/TrendsView.vue` | `service/TrendInsightGenerationService.java`、`service/ResourceManagementService.java` | `analysis_task`、`report_resource` |
| 内容专栏 | `frontend/src/views/user/ContentView.vue`、`ContentArticleDetailView.vue`、`ContentBookDetailView.vue` | `controller/ContentController.java`、`service/ContentHubService.java` | `articles`、`books`、`content_daily_schedule`、`content_daily_item`、`user_content_history` |
| 心理中心 | `frontend/src/views/user/PsyCentersView.vue`、`frontend/src/api/psyCenter.ts` | `controller/PsyCenterController.java`、`service/PsyCenterService.java` | `psy_centers` |
| 预警处置 | `frontend/src/views/admin/AdminWarningsView.vue` | `controller/AdminGovernanceController.java`、`service/WarningEventTriggerService.java`、`repository/WarningGovernanceRepository.java` | `warning_rule`、`warning_event`、`warning_action_log` |
| 规则配置 | `frontend/src/views/admin/AdminRulesView.vue`、`frontend/src/api/governance.ts` | `controller/AdminGovernanceController.java`、`service/AdminGovernanceService.java` | `warning_rule`、`model_registry` |
| 内容管理 | `frontend/src/views/admin/AdminContentView.vue`、`AdminArticlesView.vue`、`AdminBooksView.vue`、`AdminQuotesView.vue` | `controller/CmsAdminController.java`、`service/CmsService.java`、`service/CmsSeedService.java` | `banners`、`quotes`、`articles`、`books`、`psy_centers` |
| 每日排期 | `frontend/src/views/admin/AdminContentSchedulesView.vue` | `controller/CmsAdminController.java`、`service/CmsService.java`、`service/ContentHubService.java` | `content_daily_schedule`、`content_daily_item`、`user_content_history` |
| 模型治理 / 系统状态 | `frontend/src/views/admin/AdminModelsView.vue`、`frontend/src/views/admin/SystemView.vue` | `service/SystemStatusService.java`、`repository/ModelGovernanceRepository.java` | `model_registry`、`model_switch_log`、`analytics_daily_summary` |

## 4. 技术设计从这里找

- 前端路由：`frontend/src/router/index.ts`
- 用户端导航：`frontend/src/layouts/UserLayout.vue`
- 管理端导航：`frontend/src/layouts/AdminLayout.vue`
- HTTP 拦截与 Token：`frontend/src/api/http.ts`
- 后端配置：`backend/src/main/resources/application.yaml`
- 本地模型调用：`backend/src/main/java/com/wuhao/aiemotion/integration/ser/SerClient.java`
- Ollama Gemma 4：`backend/src/main/java/com/wuhao/aiemotion/integration/ai/local/OllamaNarrativeClient.java`
- Gemma 4 解释生成：`backend/src/main/java/com/wuhao/aiemotion/service/NarrativeGenerationService.java`
- 语义评分回退：`backend/src/main/java/com/wuhao/aiemotion/service/TranscriptSemanticScoringService.java`
- OpenRouter 可配置增强：`backend/src/main/java/com/wuhao/aiemotion/integration/ai/SpringAiClient.java`
- SER 服务入口：`backend/ser-service/app.py`
- 声学模型运行：`backend/ser-service/hf_wav2vec2_runtime.py`
- 文本模型运行：`backend/ser-service/text_sentiment_runtime.py`
- 融合模型运行：`backend/ser-service/fusion_runtime.py`

## 5. 数据库口径

- 终辩按 `31` 张表讲：`4 + 3 + 4 + 5 + 6 + 9 = 31`。
- V11 新增三张：`content_daily_schedule`、`content_daily_item`、`user_content_history`。
- 建表基线：`backend/docs/db/schema_v1.sql`
- 迁移目录：`backend/docs/db/migrations/`
- 31 张表说明：`docs/db.md`
- 最新迁移：`backend/docs/db/migrations/V11__content_hub_daily_schedule.sql`

## 6. 模型和数据口径

- 当前工程主链路模型：
  - 声学：`backend/ser-service/training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/best_model`
  - 文本：`backend/ser-service/training/text_models/zh_sentiment_exp03/best_model`
  - 融合：`backend/ser-service/training/fusion/models/fusion_exp04_gated`
- 训练和实验记录：`docs/experiments.md`
- 数据来源说明：CASIA、ESD、IEMOCAP、RAVDESS；最终划分 `26707 / 4139 / 4349`。
- 原始数据展示目录：`backend/data/datasets/`；CASIA、ESD、RAVDESS 已保留原始目录，IEMOCAP 保留授权下载说明和处理后 manifest。
- 答辩数据口径以 `docs/experiments.md`、`backend/ser-service/training/manifests/` 和 PPT 为准。

## 7. 回答时的稳妥边界

- 系统定位：心理状态辅助提示，不替代专业医疗诊断。
- 风险主判定：来自本地结构化模型链路，不依赖外部大模型。
- Gemma 4：用于报告解释、建议文本和趋势摘要增强。
- OpenRouter：可配置外部增强通道，不是主路径。
- 内容专栏和心理中心：是用户支持资源和运营内容，不参与风险主判定。
