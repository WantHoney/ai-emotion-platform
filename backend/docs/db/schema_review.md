# 数据库映射核对

本文档记录主要业务表与后端代码的对应关系，便于答辩时快速说明“表从哪里来、被谁使用、支撑什么功能”。

## 1. 音频与任务链路

- `audio_file`：保存上传音频的文件名、路径、大小、状态和所属用户。
- `audio_upload_session`：记录分片上传会话。
- `audio_upload_chunk`：记录每个分片的上传情况。
- `analysis_task`：记录分析任务状态、进度、重试和队列信息。
- `analysis_result`：保存任务完成后的结构化分析结果。

支撑代码：

- `AudioController`
- `AudioUploadController`
- `AnalysisTaskController`
- `AnalysisTaskWorkerService`

## 2. 报告与结果展示

- `core_report`：保存报告快照。
- `report_resource`：保存报告关联资源。
- `audio_analysis`、`audio_segment`、`segment_emotion`、`emotion_label`：保留兼容分析结果和分段情绪信息。

支撑代码：

- `ReportController`
- `AudioAnalysisService`
- `ResourceManagementService`

## 3. 认证与用户

- `auth_user`：用户账号。
- `auth_role`：角色。
- `auth_user_role`：用户与角色关系。
- `auth_session`：登录会话。

支撑代码：

- `AuthController`
- `AuthService`
- `AuthInterceptor`

## 4. 内容中心与心理中心

- `banners`：首页轮播。
- `articles`：文章内容。
- `books`：书籍内容。
- `psy_centers`：心理中心资源。
- `content_daily_schedule`：每日内容排期。
- `content_daily_item`：每日排期条目。
- `user_content_history`：用户内容访问记录。

支撑代码：

- `ContentController`
- `ContentHubService`
- `HomeService`
- `PsyCenterController`
- `PsyCenterService`

## 5. 预警与治理

- `warning_rule`：预警规则。
- `warning_event`：预警事件。
- `warning_action_log`：处置记录。
- `analytics_daily_summary`：每日统计摘要。
- `model_config`：模型配置。
- `model_switch_log`：模型切换记录。

支撑代码：

- `AdminWarningController`
- `WarningEventTriggerService`
- `AdminModelController`
- `AdminAnalyticsController`

## 6. 当前版本口径

- 最新迁移：`V11__content_hub_daily_schedule.sql`
- 当前终辩口径：`31` 张业务表。
- V11 新增：`content_daily_schedule`、`content_daily_item`、`user_content_history`。

完整分域说明见仓库根目录 `docs/db.md`。
