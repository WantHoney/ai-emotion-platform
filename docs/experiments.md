# 实验记录（毕设主线）
最后同步日期：`2026-03-01`

## 1. 任务与数据口径

- 标签体系：4 类（`ANG/HAP/NEU/SAD`），线上接口保持不变。
- 主线目标：在中文为主场景下提升 SER 与融合效果，并保持可解释与可治理能力。
- 数据源：
  - 英文：IEMOCAP + RAVDESS
  - 中文：CASIA + ESD

## 2. Exp01 基线（ESD 接入前）

来源：`backend/ser-service/training/fusion/ablation_exp01/ablation_summary.json`

| 模式 | val macro-F1 | test macro-F1 | test ECE |
|---|---:|---:|---:|
| audio_only | 0.6501 | 0.6219 | 0.0322 |
| text_only | 0.3178 | 0.3499 | 0.0471 |
| fusion | 0.6463 | 0.6317 | 0.0250 |

## 3. Exp02（ESD 接入 + 两阶段训练）最终结果

### 3.1 ESD 清洗与切分质量

来源：`backend/ser-service/training/manifests/esd_4class/summary.json`

- `detected_layout`: `A(speaker/emotion/file)`
- `label_source`: `folder`
- `text_source`: `metadata`
- 4 类保留样本：`28000`（每类 `7000`）
- 丢弃：`label_surprise=7000`（符合 4 类口径）
- speaker 独立切分：`train/val/test = 16/2/2`（总 20 speakers）

### 3.2 Stage-A / Stage-B 声学模型

来源：

- `backend/ser-service/training/checkpoints/ser_zh_esd_stageA_exp01/train_report.json`
- `backend/ser-service/training/checkpoints/ser_multilingual_esd_stageB_exp01/train_report.json`

| 阶段 | train/val/test | best epoch | test macro-F1 | test acc | 说明 |
|---|---:|---:|---:|---:|---|
| Stage-A (CASIA+ESD, zh) | 22800/3000/3000 | 5 | 0.6982 | 0.7017 | 中文增量微调 |
| Stage-B (multilingual, zh~80%) | 26707/4139/4349 | 2 | 0.7079 | 0.7027 | 中英再平衡微调 |

Stage-B 采样口径（报告内证据）：

- `expected_language_ratios`: `zh=0.7955`, `en=0.2045`
- 与“中文主系统、英文保底”的目标一致。

### 3.3 Exp02 融合特征构建

来源：`backend/ser-service/training/fusion/features_exp02_esd/summary.json`

| split | total | en | zh | zh ratio | asr_missing |
|---|---:|---:|---:|---:|---:|
| train | 26707 | 3907 | 22800 | 85.37% | 53 |
| val | 4139 | 1139 | 3000 | 72.48% | 11 |
| test | 4349 | 1349 | 3000 | 68.98% | 17 |

ESD 语言识别复核：ESD 样本在特征文件中全部为 `zh`（`en=0`），说明 `ch/eh` 误识别已修正并在第二次重跑生效。

### 3.4 Exp02 融合模型与校准

来源：`backend/ser-service/training/fusion/models/fusion_exp02_esd/train_report.json`

融合模型（calibrated）：

- `test macro-F1 = 0.7049`
- `test accuracy = 0.6986`
- `test ECE = 0.0601`

校准前后（fusion_exp02_esd）：

| split | metric | uncalibrated | calibrated | delta |
|---|---|---:|---:|---:|
| val | NLL | 1.0614 | 0.6432 | -0.4182 |
| val | Brier | 0.3780 | 0.3327 | -0.0453 |
| val | ECE | 0.1614 | 0.0572 | -0.1042 |
| test | NLL | 1.3530 | 0.8152 | -0.5378 |
| test | Brier | 0.5010 | 0.4335 | -0.0676 |
| test | ECE | 0.2111 | 0.0601 | -0.1509 |

### 3.5 Exp02 消融（Ablation）

来源：`backend/ser-service/training/fusion/ablation_exp02_esd/ablation_summary.json`

| 模式 | val macro-F1 | test macro-F1 | test ECE |
|---|---:|---:|---:|
| audio_only | 0.7873 | 0.7049 | 0.0496 |
| text_only | 0.2769 | 0.3048 | 0.0156 |
| fusion | 0.7840 | 0.7049 | 0.0601 |

结论：

- Exp02 相比 Exp01，整体分类能力显著提升：
  - `fusion test macro-F1`: `0.6317 -> 0.7049`（`+0.0732`）
- 但 `fusion` 未超过 `audio_only`，且 ECE 高于预期阈值（`0.0601 > 0.03`），说明文本分支当前主要提供弱辅助，仍需优化。

## 4. 验收指标达成情况（Exp02）

| 指标 | 目标 | 结果 | 结论 |
|---|---|---|---|
| 融合 test macro-F1 | `>= 0.64` | `0.7049` | 达成 |
| 融合 test ECE | `<= 0.03` | `0.0601` | 未达成 |
| Stage-B 语言占比 | `zh 60~80%, en 20~40%` | `zh 79.55%, en 20.45%` | 达成 |
| 中文主线稳定性 | ESD 全部中文路由 | ESD=zh, en=0 | 达成 |

## 5. 实时与治理闭环证据

### 5.1 WebSocket 实时通道压测

实测命令：`scripts/stress-realtime.ps1 -TaskId 27 -Connections 30 -DurationSec 40`  
实测结果：`success=30/30, failed=0, totalMessages=30`  
记录文件：

- `docs/figures/realtime_stress_exp01.md`
- `docs/figures/realtime_stress_exp01.json`

### 5.2 漂移扫描默认阈值回归

`POST /api/admin/governance/drift/scan?windowDays=7&baselineDays=7&mediumThreshold=0.15&highThreshold=0.25&minSamples=20`  
返回：`{"created":0}`，说明治理已回归默认阈值口径。

## 6. Exp03 代码能力（已落地，待重跑指标）

已新增：

1. 中文文本分支重训脚本：`training/train_text_sentiment_from_features.py`
   - 训练语料来源：`features_exp02_esd/*.csv` 转写文本
   - 标签对齐：`ANG/SAD->negative`, `NEU->neutral`, `HAP->positive`
2. 融合校准策略扩展：`training/train_late_fusion.py`
   - `global_temperature`
   - `per_language_temperature`
   - `vector_scaling`
3. 分语言评估产物：
   - `test_macro_f1_zh`
   - `test_macro_f1_en`
   - `test_metrics_by_language_calibrated`

说明：本节是“能力已实现”，新的数值结果需按最新脚本重跑后填回本文件。

## 7. ESD 引用（论文必须带）

- Zhou et al., ICASSP 2021
- Zhou et al., Speech Communication 2022
