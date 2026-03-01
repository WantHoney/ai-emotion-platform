# 论文图表源数据
最后同步日期：`2026-03-01`

本目录用于保存论文图表的可复现实验数据源，避免手工抄写带来的口径漂移。

## 1. 自动导出

在仓库根目录执行：

```bash
python scripts/export_experiment_assets.py
```

将更新以下 Exp01 文件：

- `docs/figures/ablation_metrics.csv`
- `docs/figures/model_selection.csv`
- `docs/figures/calibration_metrics.csv`
- `docs/figures/dataset_composition.csv`

## 2. Exp02（ESD 二次重跑）固定文件

以下文件已落盘，可直接用于论文绘图：

- `docs/figures/ablation_metrics_exp02_esd.csv`
- `docs/figures/model_selection_exp02_esd.csv`
- `docs/figures/calibration_metrics_exp02_esd.csv`
- `docs/figures/dataset_composition_exp02_esd.csv`

数据来源分别对应：

- `training/fusion/ablation_exp02_esd/ablation_summary.json`
- `training/fusion/models/fusion_exp02_esd/train_report.json`
- `training/fusion/features_exp02_esd/summary.json`

## 3. Exp03（文本重训 + 分语言校准）固定文件

以下文件已落盘，可直接用于论文绘图：

- `docs/figures/ablation_metrics_exp03_perlang.csv`
- `docs/figures/model_selection_exp03.csv`
- `docs/figures/calibration_metrics_exp03.csv`
- `docs/figures/text_shrink_exp03.csv`

数据来源分别对应：

- `training/fusion/ablation_exp03_perlang/ablation_summary.json`
- `training/fusion/models/fusion_exp03_perlang/train_report.json`
- `training/fusion/models/fusion_exp03_vector/train_report.json`
- `training/fusion/models/fusion_exp03_shrink_0*/train_report.json`

## 4. 实时与治理证据

- `docs/figures/realtime_stress_exp01.md`：WebSocket 通道压测记录。
- `docs/figures/realtime_stress_exp01.json`：压测摘要结构化数据。
- `docs/figures/realtime_stress_exp02.md`：Exp03 收尾复测记录。
- `docs/figures/realtime_stress_exp02.json`：Exp03 收尾复测摘要。

## 5. 论文作图建议

- 图 1：`ablation_metrics_exp02_esd.csv` 绘制消融对比（macro-F1 + ECE）。
- 图 2：`calibration_metrics_exp02_esd.csv` 绘制校准前后对比。
- 图 3：`model_selection_exp02_esd.csv` 与 `model_selection.csv` 组合绘制“代际改进图”。
- 图 4：`dataset_composition_exp02_esd.csv` 绘制中英比例与 ASR 缺失率。
- 图 5：`ablation_metrics_exp03_perlang.csv` 绘制 Exp03 消融（含 `zh/en`）。
- 图 6：`model_selection_exp03.csv` 绘制 `perlang`、`vector`、`shrink` 模型选择。
- 图 7：`calibration_metrics_exp03.csv` 绘制校准策略前后对比（NLL/Brier/ECE）。
- 图 8：`text_shrink_exp03.csv` 绘制低改动文本缩放实验结论。
