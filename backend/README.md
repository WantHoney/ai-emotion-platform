# Backend (Spring Boot)

## 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+

## 本地启动

```bash
# 1) 导入基线结构
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql

# 2) 按顺序执行迁移（V2 -> V8）
# backend/docs/db/migrations/V2__task_queue_schema.sql
# backend/docs/db/migrations/V3__resource_observability_upgrade.sql
# backend/docs/db/migrations/V4__home_cms_content.sql
# backend/docs/db/migrations/V5__model_warning_ops.sql
# backend/docs/db/migrations/V6__warning_sla_and_quality.sql
# backend/docs/db/migrations/V7__task_report_user_sequence_indexes.sql
# backend/docs/db/migrations/V8__cleanup_legacy_sequence_indexes.sql

# 3) 启动服务
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8080`  
健康检查：`curl http://127.0.0.1:8080/api/health`

## 关键配置（建议环境变量）

- `AI_MODE`：默认 `mock`（本地模式），可切 `spring`
- `SPRING_AI_OPENAI_ENABLED`：默认 `false`
- `OPENROUTER_API_KEY`：仅当 `AI_MODE=spring` 时需要
- `OPENROUTER_BASE_URL`、`OPENROUTER_MODEL`
- `APP_CORS_ALLOWED_ORIGINS`
- `SER_ENABLED`、`SER_BASE_URL`
- `GOVERNANCE_DRIFT_MONITOR_ENABLED`、`GOVERNANCE_DRIFT_SCAN_INTERVAL_MS`
- `GOVERNANCE_DRIFT_WINDOW_DAYS`、`GOVERNANCE_DRIFT_BASELINE_DAYS`
- `GOVERNANCE_DRIFT_MEDIUM_THRESHOLD`、`GOVERNANCE_DRIFT_HIGH_THRESHOLD`
- `GOVERNANCE_DRIFT_MIN_SAMPLES`

## 数据库脚本

- 基线：`docs/db/schema_v1.sql`
- 迁移：`docs/db/migrations/`
- 当前最新迁移：`V8__cleanup_legacy_sequence_indexes.sql`

## 说明

- 本文件是 backend 子项目说明。
- 项目整体说明请查看仓库根目录 `README.md`。
- 文档一致性检查请在仓库根目录执行：`python scripts/check_doc_sync.py`。
- 可手动触发漂移扫描：`POST /api/admin/governance/drift/scan`。
