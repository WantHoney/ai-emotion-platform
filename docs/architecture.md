# 架构与模块说明（与代码同步）
最后同步日期：`2026-02-21`

## 1. 总体拓扑

```text
Frontend (Vue3/Vite, :5173)
    | HTTP /api + WS /ws/tasks/stream
Backend (Spring Boot, :8080)
    | JDBC
MySQL
    |
    +-- HTTP -> SER Service (FastAPI, :8001, 可选)
            |- ASR (faster-whisper)
            |- SER (wav2vec2 EN/ZH)
            |- Text Sentiment (HF EN/ZH)
            |- Late Fusion + PSI
```

说明：
- 默认是本地模型链路（不开外部大模型即可运行）。
- 外部 LLM（如 OpenRouter）仅作可选增强，不是主路径。

## 2. 后端核心模块

- `controller/`：接口入口，覆盖认证、上传、分析、报告、治理、CMS。
- `service/`：任务调度、报告生成、风险计算、实时快照组装。
- `websocket/`：`/ws/tasks/stream` 实时推送任务快照。
- `repository/`：JDBC 数据访问。
- `integration/`：SER/ASR/AI 外部能力集成。
- `dto/`：前后端交互契约。

## 3. 前端核心模块

- `views/user/`：上传、任务、报告、趋势等用户侧页面。
- `views/admin/`：治理、告警、CMS 管理页面。
- `api/`：按域封装接口调用。
- `composables/`：任务轮询、WebSocket 实时订阅、状态管理。
- `stores/`：Pinia 全局状态。

## 4. 端到端分析流程

1. 用户上传音频（支持普通上传和分片上传）。
2. 创建分析任务并进入队列。
3. 后端调用 SER 服务执行：ASR -> 音频情绪 -> 文本情绪 -> 融合。
4. 生成风险评分与 PSI 相关贡献项。
5. 建议引擎按风险等级 + 情绪因子输出分层干预建议（即时/短期/资源）。
6. 安全规则对建议文本做合规处理（提示性质、非医疗诊断边界）。
7. 写入分段结果、任务状态、报告数据，并触发规则预警。
8. 治理侧定时执行漂移监控，达到阈值自动生成系统预警事件。
9. 运营动作回写预警快照与报告治理字段，形成可审计闭环。
10. 前端通过 WebSocket + 轮询看到任务阶段与风险曲线变化。

## 5. 实时能力边界（毕设可交付）

当前实现目标是：
- 时间轴持续更新。
- 风险曲线可视化。
- 阶段进度可追踪。

不追求：
- 毫秒级硬实时。
- 真正在线增量 ASR 重训练。
- 动态模型重训练闭环。

## 6. 与文档的同步关系

- 接口细节：`docs/api.md`
- 数据库迁移：`docs/db.md`
- 实验结果：`docs/experiments.md`
- 论文技术素材：`docs/thesis_notes.md`
