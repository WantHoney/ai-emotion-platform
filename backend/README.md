# 后端服务（Spring Boot）

最后同步日期：`2026-05-15`

本目录是系统后端服务，负责认证、任务、报告、内容、心理中心、预警治理、模型调用和数据库访问。

## 1. 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+

## 2. 本地启动

```bash
# 1) 导入数据库基线
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql

# 2) 按顺序执行迁移脚本 V2 -> V11
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

# 3) 启动后端
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8080`

健康检查：

```bash
curl http://127.0.0.1:8080/api/health
```

## 3. 关键配置

- `SPRING_DATASOURCE_URL`：数据库连接地址。
- `SPRING_DATASOURCE_USERNAME`：数据库用户名。
- `SPRING_DATASOURCE_PASSWORD`：数据库密码。
- `AUTH_SEED_ADMIN_USERNAME`：默认管理员账号，默认 `operator`。
- `AUTH_SEED_ADMIN_PASSWORD`：默认管理员密码，默认 `operator123`，仅用于本地演示。
- `SER_ENABLED`：是否启用模型服务调用。
- `SER_BASE_URL`：模型服务地址，默认 `http://127.0.0.1:8001`。
- `AI_MODE`：大模型增强模式，默认本地演示模式，可按需切换。
- `OPENROUTER_API_KEY`：启用外部大模型增强时使用。
- `OPENROUTER_MODEL`：外部模型名称。

本地单元测试默认不需要外部大模型密钥。

## 4. 数据库脚本

- 基线脚本：`docs/db/schema_v1.sql`
- 迁移目录：`docs/db/migrations/`
- 当前最新迁移：`V11__content_hub_daily_schedule.sql`
- 当前终辩口径：V11 后共 `31` 张业务表。

V11 新增内容排期相关表：

- `content_daily_schedule`
- `content_daily_item`
- `user_content_history`

数据库分域说明见：

- `../docs/db.md`

## 5. 主要模块

- `controller/`：接口层。
- `service/`：业务逻辑层。
- `repository/`：数据访问层。
- `domain/`：实体和领域对象。
- `config/`：安全、跨域、WebSocket、任务等配置。
- `ai/`：大模型增强和报告生成相关逻辑。
- `ser/`：模型服务调用与音频分析链路。

## 6. 相关文档

- 根目录说明：`../README.md`
- 接口文档：`../docs/api.md`
- 架构说明：`../docs/architecture.md`
- 数据库说明：`../docs/db.md`
- 答辩速查：`../DEFENSE_QUICK_GUIDE.md`
