# 数据库说明

## 1. 基线与迁移

- 基线 SQL：`backend/docs/db/schema_v1.sql`
- 迁移脚本目录：`backend/docs/db/migrations/`
  - `V2__task_queue_schema.sql`
  - `V3__resource_observability_upgrade.sql`
  - `V4__home_cms_content.sql`

## 2. 核心领域表（概览）

- 用户与鉴权：`app_user`、`app_refresh_token`（及相关）
- 音频资源：`audio_file`
- 分析任务：`audio_analysis`、`analysis_task`
- 分段结果：`audio_segment`、`segment_emotion`、`emotion_label`
- 报告：`core_report`
- CMS：`cms_banner`、`cms_quote`、`cms_article`、`cms_book`、`psy_center`

## 3. 初始化建议

1. 先导入 `schema_v1.sql`。
2. 按版本顺序执行 `migrations/` 下脚本。
3. 启动 backend 后通过 `/api/health/db` 验证连通。

## 4. 关联文档

- `backend/docs/db/schema_review.md`
- `backend/docs/db/alignment_report.md`
- `backend/docs/db/alignment/alignment_pr01.md`

这些文档保留为历史设计/评审记录，数据库事实来源仍以 SQL 脚本为准。
