# Backend (Spring Boot)
最后同步日期：`2026-04-03`

## 1. 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+

## 2. 本地启动

```bash
# 1) 导入基线结构
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql

# 2) 按顺序执行迁移（V2 -> V11）
# backend/docs/db/migrations/V2__task_queue_schema.sql
# backend/docs/db/migrations/V3__resource_observability_upgrade.sql
# backend/docs/db/migrations/V4__home_cms_content.sql
# backend/docs/db/migrations/V5__model_warning_ops.sql
# backend/docs/db/migrations/V6__warning_sla_and_quality.sql
# backend/docs/db/migrations/V7__task_report_user_sequence_indexes.sql
# backend/docs/db/migrations/V8__cleanup_legacy_sequence_indexes.sql
# backend/docs/db/migrations/V9__cms_seed_source_metadata.sql
# backend/docs/db/migrations/V10__repair_psy_center_seed_data.sql
# backend/docs/db/migrations/V11__content_hub_daily_schedule.sql

# 3) 启动服务
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8080`  
健康检查：`curl http://127.0.0.1:8080/api/health`

## 3. 关键配置（建议环境变量）

- `AI_MODE`：默认 `mock`（本地模式），可切到 `spring`
- `SPRING_AI_OPENAI_ENABLED`：默认 `false`
- `OPENROUTER_API_KEY`：仅当 `AI_MODE=spring` 时需要
- `OPENROUTER_BASE_URL`、`OPENROUTER_MODEL`
- `APP_CORS_ALLOWED_ORIGINS`
- `AUTH_SEED_ADMIN_USERNAME`：默认 `operator`
- `AUTH_SEED_ADMIN_PASSWORD`：默认 `operator123`（仅本地开发演示，生产必须覆盖）
- `SER_ENABLED`、`SER_BASE_URL`
- `GOVERNANCE_DRIFT_MONITOR_ENABLED`、`GOVERNANCE_DRIFT_SCAN_INTERVAL_MS`
- `GOVERNANCE_DRIFT_WINDOW_DAYS`、`GOVERNANCE_DRIFT_BASELINE_DAYS`
- `GOVERNANCE_DRIFT_MEDIUM_THRESHOLD`、`GOVERNANCE_DRIFT_HIGH_THRESHOLD`
- `GOVERNANCE_DRIFT_MIN_SAMPLES`

补充：
- 本地单元测试固定走 `test` profile（`ai.mode=mock` + `spring.ai.openai.enabled=false`），不需要 OpenAI key。
- 若要启用远端 LLM，请显式设置 `AI_MODE=spring` 并提供 `OPENROUTER_API_KEY`。

## 4. 数据库脚本

- 基线：`docs/db/schema_v1.sql`
- 迁移：`docs/db/migrations/`
- 当前最新迁移：`V11__content_hub_daily_schedule.sql`
- 当前本地运行库（`2026-03-23` 实库核对）共有 `28` 张活跃表。
- `schema_v1.sql` 仍保留历史遗留表定义；当前运行库已在 `2026-02-16` 完成清理，表数由 `45` 降到 `28`。
- 清理与审计记录见：
  - `docs/db/audit/V6_table_usage_audit.md`
  - `docs/db/audit/V6_cleanup_execution_20260216.md`
- 当前实际表名和分域说明见：`../docs/db.md`
- `V9` 为 CMS 内容与心理中心补充了 seed 元数据、显式活跃状态和来源字段。
- `articles.source_url` 是新的正式外链字段，`content_url` 仅保留兼容期映射。
- `V9` 的 `source_url` 回填语句已兼容 MySQL Workbench safe update mode。

## 5. 说明

- 本文件是 backend 子项目说明。
- 项目整体说明请查看仓库根目录 `README.md`。
- 文档一致性检查请在仓库根目录执行：`python scripts/check_doc_sync.py`。
- 可手动触发漂移扫描：`POST /api/admin/governance/drift/scan`。
