# 后端运行手册

本文档用于本地联调和答辩现场快速验证后端服务。

## 1. 导入数据库

先导入基线脚本：

```bash
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql
```

再按顺序执行迁移脚本：

- `docs/db/migrations/V2__task_queue_schema.sql`
- `docs/db/migrations/V3__resource_observability_upgrade.sql`
- `docs/db/migrations/V4__home_cms_content.sql`
- `docs/db/migrations/V5__model_warning_ops.sql`
- `docs/db/migrations/V6__warning_sla_and_quality.sql`
- `docs/db/migrations/V7__task_report_user_sequence_indexes.sql`
- `docs/db/migrations/V8__cleanup_legacy_sequence_indexes.sql`
- `docs/db/migrations/V9__cms_seed_source_metadata.sql`
- `docs/db/migrations/V10__repair_psy_center_seed_data.sql`
- `docs/db/migrations/V11__content_hub_daily_schedule.sql`

当前终辩口径为 V11，业务表共 `31` 张。

## 2. 配置后端

建议通过环境变量配置数据库和模型服务：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SER_ENABLED`
- `SER_BASE_URL`
- `AUTH_SEED_ADMIN_USERNAME`
- `AUTH_SEED_ADMIN_PASSWORD`

示例：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/ai_emotion?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="your_password"
$env:SER_BASE_URL="http://127.0.0.1:8001"
```

## 3. 启动服务

```bash
mvn spring-boot:run
```

启动后检查：

```bash
curl http://127.0.0.1:8080/api/health
```

## 4. 主流程接口

上传音频：

```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@./sample.wav" \
  http://127.0.0.1:8080/api/audio/upload
```

查询任务：

```bash
curl -H "Authorization: Bearer <accessToken>" \
  "http://127.0.0.1:8080/api/tasks?page=1&pageSize=10"
```

查询报告：

```bash
curl -H "Authorization: Bearer <accessToken>" \
  "http://127.0.0.1:8080/api/reports?page=1&pageSize=10"
```

## 5. 兼容调试接口

以下接口主要用于开发联调和异常场景验证，正式演示以任务中心和报告中心为主：

- `POST /api/analysis/{analysisId}/mock-run`
- `POST /api/analysis/{analysisId}/mock-run-async`
- `POST /api/analysis/{analysisId}/mock-success`
- `POST /api/analysis/{analysisId}/mock-fail`
- `POST /api/analysis/{analysisId}/mock-segments`

这些接口可以帮助验证任务状态、报告生成和异常处理逻辑。

## 6. 排查建议

- 数据库连接失败：检查 MySQL 服务、库名、账号密码和字符集。
- 模型服务不可用：先访问 `http://127.0.0.1:8001/health`。
- 前端接口不通：确认 Vite 代理目标为 `http://127.0.0.1:8080`。
- 登录失败：确认种子管理员账号和密码是否被环境变量覆盖。
