# docs 索引
最后同步日期：`2026-02-21`

- `docs/api.md`：后端 HTTP + WebSocket 接口清单。
- `docs/architecture.md`：系统架构、链路与边界。
- `docs/db.md`：数据库基线与迁移（当前到 V8）。
- `docs/experiments.md`：实验结果与调参记录。
- `docs/thesis_notes.md`：论文写作技术素材提纲。
- `docs/frontend_page_interaction_checklist.md`：前端页面联调清单。
- `docs/archive/`：历史方案与旧版记录（非当前事实源）。

## 维护规则

- 功能有变更时，优先更新 `docs/api.md`、`docs/architecture.md`、`docs/db.md`。
- 每次跑完关键实验后，追加更新 `docs/experiments.md`。
- 论文写作前，先从 `docs/thesis_notes.md` 补齐图表与术语。
