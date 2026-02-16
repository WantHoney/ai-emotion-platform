# Backend（Spring Boot）

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8+

## 本地启动

```bash
# 导入基线数据库
mysql -h 127.0.0.1 -P 3306 -u <user> -p < docs/db/schema_v1.sql

# 启动服务
mvn spring-boot:run
```

默认地址：`http://localhost:8080`

健康检查：

```bash
curl http://localhost:8080/api/health
```

## 关键配置（建议用环境变量）

- `AI_MODE`：`mock` / `spring`
- `OPENROUTER_API_KEY`、`OPENROUTER_BASE_URL`、`OPENROUTER_MODEL`
- `APP_CORS_ALLOWED_ORIGINS`（默认 `http://localhost:5173`）
- `SER_ENABLED`、`SER_BASE_URL`

## 数据库脚本

- 基线：`docs/db/schema_v1.sql`
- 迁移：`docs/db/migrations/`

## 说明

- 本目录仅说明 backend 子项目。
- 项目级说明见仓库根目录 `README.md`。
