# 数据库说明（与代码同步）
最后同步日期：`2026-04-03`

## 1. 基线、迁移与清理

- 基线 SQL：`backend/docs/db/schema_v1.sql`
- 迁移目录：`backend/docs/db/migrations/`
- 审计与清理目录：`backend/docs/db/audit/`

当前迁移序列：
- `V2__task_queue_schema.sql`
- `V3__resource_observability_upgrade.sql`
- `V4__home_cms_content.sql`
- `V5__model_warning_ops.sql`
- `V6__warning_sla_and_quality.sql`
- `V7__task_report_user_sequence_indexes.sql`
- `V8__cleanup_legacy_sequence_indexes.sql`
- `V9__cms_seed_source_metadata.sql`
- `V10__repair_psy_center_seed_data.sql`

当前最新迁移：`V10`

说明：
- `migrations/` 记录的是历史增量变更。
- 当前本地运行库不是只看 `migrations/` 就能完全还原的，因为还执行过一次遗留表清理。
- 清理记录见：
  - `backend/docs/db/audit/V6_table_usage_audit.md`
  - `backend/docs/db/audit/V6_cleanup_execution_20260216.md`
  - `backend/docs/db/audit/V6_cleanup_candidate.sql`

## 2. 初始化顺序

1. 导入 `schema_v1.sql`。
2. 按版本顺序执行 `migrations/` 下脚本（V2 -> V10）。
3. 启动 backend。
4. 访问 `GET /api/health/db` 验证连通。

补充：
- 如果需要与当前本地运行库完全一致，还要参考 `audit/` 中的清理记录。
- 根据 `V6_cleanup_execution_20260216.md`，当前本地运行库已在 `2026-02-16` 从 `45` 张表清理到 `28` 张活跃表。

## 3. 当前运行库表（2026-03-23 实库核对）

当前 `ai_emotion` 库中实际存在的表共 `28` 张：

- 认证与会话：
  - `auth_user`
  - `auth_role`
  - `auth_user_role`
  - `auth_session`
- 音频与上传：
  - `audio_file`
  - `audio_upload_session`
  - `audio_upload_chunk`
- 当前主分析链路：
  - `analysis_task`
  - `analysis_result`
  - `analysis_segment`
  - `report_resource`
- 兼容 / mock 分析链路：
  - `audio_analysis`
  - `audio_segment`
  - `emotion_label`
  - `segment_emotion`
  - `core_report`
- 治理与模型：
  - `warning_rule`
  - `warning_event`
  - `warning_action_log`
  - `model_registry`
  - `model_switch_log`
  - `analytics_daily_summary`
- CMS 与资源：
  - `banners`
  - `quotes`
  - `articles`
  - `books`
  - `psy_centers`
  - `content_events`

## 4. 关键领域表（按实际使用归类）

- 用户认证：
  - `auth_user`、`auth_role`、`auth_user_role`、`auth_session`
- 音频资源：
  - `audio_file`、`audio_upload_session`、`audio_upload_chunk`
- 当前主任务链路：
  - `analysis_task`、`analysis_result`、`analysis_segment`、`report_resource`
- 兼容 / mock 链路：
  - `audio_analysis`、`audio_segment`、`segment_emotion`、`emotion_label`、`core_report`
- 治理与告警：
  - `model_registry`、`model_switch_log`、`warning_rule`、`warning_event`、`warning_action_log`、`analytics_daily_summary`
- CMS：
  - `banners`、`quotes`、`articles`、`books`、`psy_centers`、`content_events`

## 5. 当前设计说明

- 当前工程主流程已经切到 `analysis_task -> analysis_result / analysis_segment -> report_resource`。
- `audio_analysis -> audio_segment -> segment_emotion -> core_report` 仍保留，用于兼容旧接口和 mock 调试流。
- `report_resource.report_json`、`warning_event.trigger_snapshot`、`model_registry.metrics_json` 等字段采用 JSON，是为了保存推理结果快照、治理快照和模型指标，避免高维结果过度拆表。
- 对列表筛选频繁使用的字段，当前都已经保留了顶层列或索引，例如：
  - `report_resource.risk_level`
  - `report_resource.overall_emotion`
  - `warning_event.status`
  - `warning_event.risk_level`
  - `analysis_task.status`

## 5.1 V9 / V10 CMS 与心理中心修正说明

- `quotes`、`articles`、`books`、`psy_centers` 在 `V9` 中统一新增了 `seed_key`、`data_source`、`is_active`。
- `seed_key` 只给 `data_source='seed'` 的默认数据使用，`manual` 数据保持 `NULL`，并对 `seed_key` 建唯一索引。
- `articles` 额外新增 `category`、`source_name`、`source_url`、`is_external`、`difficulty_tag`。
- `psy_centers` 额外新增 `source_name`、`source_url`、`source_level`。
- 用户端内容接口统一按 `is_active=1 AND is_enabled=1` 过滤。
- `articles.source_url` 是新正式字段，`content_url` 仅保留兼容期映射。
- `V9` 的 `source_url` 回填 SQL 已带主键条件，兼容 MySQL Workbench safe update mode 的手工执行。
- `V10` 会按 `seed_key` 修复心理中心 seed 数据中的乱码与错位记录，并把名单收敛为精神卫生中心、精神专科医院和明确的心理专科支持机构。
- `V10` 不新增表，只对 `psy_centers` 的 seeded 记录做幂等 upsert 修正，便于已有环境直接修复。

## 6. 文档一致性检查

为避免 README 或 docs 落后于 SQL，执行：

```bash
python scripts/check_doc_sync.py
```

通过条件：最新迁移文件（当前 `V9`）必须在 `README.md` 与 `backend/README.md` 中出现。
