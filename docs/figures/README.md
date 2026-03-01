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

## 3. 实时与治理证据

- `docs/figures/realtime_stress_exp01.md`：WebSocket 通道压测记录。
- `docs/figures/realtime_stress_exp01.json`：压测摘要结构化数据。

## 4. 论文作图建议

- 图 1：`ablation_metrics_exp02_esd.csv` 绘制消融对比（macro-F1 + ECE）。
- 图 2：`calibration_metrics_exp02_esd.csv` 绘制校准前后对比。
- 图 3：`model_selection_exp02_esd.csv` 与 `model_selection.csv` 组合绘制“代际改进图”。
- 图 4：`dataset_composition_exp02_esd.csv` 绘制中英比例与 ASR 缺失率。

如果使用新训练脚本（per-language calibration）重跑，建议补充两列：

- `test_macro_f1_zh`
- `test_macro_f1_en`
