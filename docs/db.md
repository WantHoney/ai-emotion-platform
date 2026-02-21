# 数据库说明（与代码同步）
最后同步日期：`2026-02-21`

## 1. 基线与迁移

- 基线 SQL：`backend/docs/db/schema_v1.sql`
- 迁移目录：`backend/docs/db/migrations/`

当前迁移序列：
- `V2__task_queue_schema.sql`
- `V3__resource_observability_upgrade.sql`
- `V4__home_cms_content.sql`
- `V5__model_warning_ops.sql`
- `V6__warning_sla_and_quality.sql`
- `V7__task_report_user_sequence_indexes.sql`
- `V8__cleanup_legacy_sequence_indexes.sql`

当前最新版本：`V8`

## 2. 初始化顺序

1. 导入 `schema_v1.sql`。
2. 按版本顺序执行 `migrations/` 下脚本（V2 -> V8）。
3. 启动 backend。
4. 访问 `GET /api/health/db` 验证连通。

## 3. 核心领域表（概览）

- 用户认证：`app_user`、`app_refresh_token`
- 音频资源：`audio_file`
- 分析任务：`audio_analysis`、`analysis_task`
- 分段结果：`audio_segment`、`segment_emotion`、`emotion_label`
- 报告：`core_report`
- 治理与告警：`model_registry`、`warning_rule`、`warning_event` 等
- CMS：`cms_banner`、`cms_quote`、`cms_article`、`cms_book`、`psy_center`

## 4. 文档一致性检查

为避免 README 或 docs 落后于 SQL，执行：

```bash
python scripts/check_doc_sync.py
```

通过条件：最新迁移文件（当前 `V8`）必须在 `README.md` 与 `backend/README.md` 中出现。
