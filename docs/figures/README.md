# 论文图表源数据
最后同步日期：`2026-02-21`

本目录用于保存论文图表的可复现实验数据源，避免手工抄写带来的口径漂移。

## 1. 自动导出

在仓库根目录执行：

```bash
python scripts/export_experiment_assets.py
```

将更新以下文件：

- `docs/figures/ablation_metrics.csv`：`audio_only` / `text_only` / `fusion` 消融结果。
- `docs/figures/model_selection.csv`：`fusion_exp01` 与 `tune_*` 调参对比。
- `docs/figures/calibration_metrics.csv`：融合模型校准前后 `NLL/Brier/ECE` 对比。
- `docs/figures/dataset_composition.csv`：训练/验证/测试集的中英样本构成与 ASR 缺失统计。

## 2. 实时与治理证据

- `docs/figures/realtime_stress_exp01.md`：WebSocket 通道压测记录。
- `docs/figures/realtime_stress_exp01.json`：压测摘要结构化数据。

## 3. 论文作图建议

- 图 1：使用 `ablation_metrics.csv` 绘制 `macro-F1` + `ECE` 对比柱状图。
- 图 2：使用 `calibration_metrics.csv` 绘制校准前后折线或分组柱状图。
- 图 3：使用 `model_selection.csv` 绘制模型选择散点图（x=`test_ece`, y=`test_macro_f1`）。
- 图 4：使用 `dataset_composition.csv` 绘制语种占比堆叠柱状图。
