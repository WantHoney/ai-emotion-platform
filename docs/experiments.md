# 实验记录（毕设主线）
最后同步日期：`2026-03-10`

## 1. 任务与数据口径

- 标签体系：4 类（`ANG/HAP/NEU/SAD`），线上接口保持不变。
- 主线目标：在中文为主场景下提升 SER 与融合效果，并保持可解释与可治理能力。
- 数据源：
  - 英文：IEMOCAP + RAVDESS
  - 中文：CASIA + ESD

### 1.1 指标口径规范（固定）

- 同版本横向比较必须同口径：`calibrated` 对 `calibrated`，`uncalibrated` 对 `uncalibrated`。
- 训练日志里的 `uncalibrated` 仅用于说明校准增益，不作为最终上线比较口径。
- Exp03 口径固定：`uncalibrated ECE=0.2140`，`calibrated ECE=0.0562`。

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

Exp01 记录：  
命令：`scripts/stress-realtime.ps1 -TaskId 27 -Connections 30 -DurationSec 40`  
结果：`success=30/30, failed=0, totalMessages=30`  
文件：

- `docs/figures/realtime_stress_exp01.md`
- `docs/figures/realtime_stress_exp01.json`

Exp03 回归记录（本轮收尾）：  
命令：`scripts/stress-realtime.ps1 -TaskId 31 -Connections 30 -DurationSec 40`  
结果：`success=30/30, failed=0, totalMessages=30`  
文件：

- `docs/figures/realtime_stress_exp02.md`
- `docs/figures/realtime_stress_exp02.json`

### 5.2 漂移扫描默认阈值回归

`POST /api/admin/governance/drift/scan?windowDays=7&baselineDays=7&mediumThreshold=0.15&highThreshold=0.25&minSamples=20`  
返回：`{"created":0}`，说明治理已回归默认阈值口径。

## 6. Exp03（文本重训 + 分语言校准）最终结果

### 6.1 训练口径

- 中文文本分支重训：`backend/ser-service/training/text_models/zh_sentiment_exp03/`
  - 标签对齐：`ANG/SAD->negative`, `NEU->neutral`, `HAP->positive`
- 融合特征：`backend/ser-service/training/fusion/features_exp03_textzh/`
  - train/val/test：`26707/4139/4349`
  - 语言占比：`zh=79.35%`, `en=20.65%`（train）
- 融合模型主候选：`backend/ser-service/training/fusion/models/fusion_exp03_perlang/`

### 6.2 主模型结果（per-language temperature）

来源：`backend/ser-service/training/fusion/models/fusion_exp03_perlang/train_report.json`

- `test macro-F1 = 0.7030`
- `test accuracy = 0.6963`
- `test ECE = 0.0562`
- `test macro-F1_zh = 0.7221`
- `test macro-F1_en = 0.6239`

校准收益（同一模型，test）：

- `ECE: 0.2140 -> 0.0562`（显著下降）
- `NLL: 1.3451 -> 0.8077`

### 6.3 Exp03 消融（Ablation）

来源：`backend/ser-service/training/fusion/ablation_exp03_perlang/ablation_summary.json`

| 模式 | test macro-F1 | test ECE | test macro-F1_zh | test macro-F1_en |
|---|---:|---:|---:|---:|
| audio_only | 0.7049 | 0.0502 | 0.7287 | 0.6045 |
| text_only | 0.2606 | 0.0372 | 0.1914 | 0.3623 |
| fusion (perlang) | 0.7030 | 0.0562 | 0.7221 | 0.6239 |

结论：当前数据版本中，`audio_only` 仍略优于 `fusion`（F1 与 ECE 都更优）；文本分支对分类增益有限，但对跨语言信息补充仍有价值。

### 6.4 校准策略对比（perlang vs vector）

来源：

- `backend/ser-service/training/fusion/models/fusion_exp03_perlang/train_report.json`
- `backend/ser-service/training/fusion/models/fusion_exp03_vector/train_report.json`

| 模型 | calibration | test macro-F1 | test ECE | test macro-F1_zh | test macro-F1_en |
|---|---|---:|---:|---:|---:|
| fusion_exp03_perlang | per_language_temperature | 0.7030 | 0.0562 | 0.7221 | 0.6239 |
| fusion_exp03_vector | vector_scaling | 0.7129 | 0.1297 | 0.7417 | 0.6175 |

结论：`vector_scaling` 提高了分类 F1，但显著恶化 ECE，不满足风险预警场景“校准优先”的上线约束。

### 6.5 Text Shrink 小实验（低改动验证）

来源：`docs/figures/text_shrink_exp03.csv`

| 方案 | test macro-F1 | test ECE | 结论 |
|---|---:|---:|---|
| baseline(perlang) | 0.7030 | 0.0562 | 基线 |
| shrink 0.3 | 0.7029 | 0.0578 | 退化 |
| shrink 0.5 | 0.7030 | 0.0562 | 无收益 |
| shrink 0.7 | 0.7037 | 0.0577 | F1 微升但 ECE 变差 |

结论：本轮 `text shrink` 未带来 ECE 改善，维持基线模型更稳。

### 6.6 Exp03 上线选择

按“`ECE<=0.03` 优先，否则在 `testF1>=0.693` 条件下选最低 ECE”规则，最终选择：

- `backend/ser-service/training/fusion/models/fusion_exp03_perlang`

## 7. Exp04 候选结果（gated/mlp，对比用，待复核）

### 7.1 实际产物链与口径

本轮 `exp04` 不是正式上线结论，只作为候选评估保留。

实际跑通并写入 `features_exp04_full/summary.json` 的链路为：

- `audio_model_zh = backend/ser-service/training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/best_model`
- `text_model_zh = backend/ser-service/training/text_models/zh_sentiment_exp03/best_model`
- `features = backend/ser-service/training/fusion/features_exp04_full/`
- `fusion candidates = fusion_exp04_gated / fusion_exp04_mlp`

同时确认：

- `backend/ser-service/training/checkpoints/ser_multilingual_xlsr_stageB_exp04/` 当前仍为空目录
- 因此这轮不是“正式 Stage B exp04 完成”的结果，而是 `stageB_exp04_fast` 驱动的候选链路
- 已塌缩的 `zh_emotion4_exp04` 不再参与 full 流程

### 7.2 Stage-B fast 声学候选

来源：`backend/ser-service/training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/train_report.json`

| 模型 | train/val/test | best epoch | test macro-F1 | test acc | 说明 |
|---|---:|---:|---:|---:|---|
| stageB_exp04_fast | 26707/4139/4349 | 2 | 0.7814 | 0.7777 | 基于 `ser_zh_xlsr_stageA_exp04` 的候选 Stage-B 结果 |

判断：

- 这一步没有出现塌缩，验证集和测试集都保持正常 4 类分布能力
- 但因为目录名与正式目标不一致，不能直接当正式 `stageB_exp04` 归档

### 7.3 与当前最强稳定基线对比

来源：

- `backend/ser-service/training/fusion/models/fusion_exp03_perlang/train_report.json`
- `backend/ser-service/training/fusion/models/fusion_exp04_gated/train_report.json`
- `backend/ser-service/training/fusion/models/fusion_exp04_mlp/train_report.json`

| run | arch | test macro-F1 | test ECE | zh macro-F1 | en macro-F1 | 结论 |
|---|---|---:|---:|---:|---:|---|
| fusion_exp03_perlang | mlp | 0.7030 | 0.0562 | 0.7221 | 0.6239 | 当前稳定基线 |
| fusion_exp04_gated | gated | 0.7761 | 0.0454 | 0.8346 | 0.6219 | 整体最强候选 |
| fusion_exp04_mlp | mlp | 0.7756 | 0.0489 | 0.8356 | 0.6195 | 次优候选 |

相对 `fusion_exp03_perlang`：

- `fusion_exp04_gated`: `macro-F1 +0.0731`, `ECE -0.0109`, `zhF1 +0.1126`, `enF1 -0.0021`
- `fusion_exp04_mlp`: `macro-F1 +0.0726`, `ECE -0.0073`, `zhF1 +0.1136`, `enF1 -0.0044`

### 7.4 当前裁决

- `exp04` 没有出现“整体塌缩”，从整体分类与校准看，两个候选都明显强于 `exp03`
- 但这轮仍不满足“正式上线裁决”：
  - 实际链路使用的是 `stageB_exp04_fast`，不是目标 `stageB_exp04`
  - `en macro-F1` 相比稳定基线有小幅回退，不满足“全指标不退步”
- 因此结论分成两条：
  - 工程最终使用模型：`fusion_exp04_gated + zh_sentiment_exp03 + ser_multilingual_xlsr_stageB_exp04_fast`
  - 论文/严格实验归档：仍需注明该链路来自 `stageB_exp04_fast`，不是正式同名 `stageB_exp04`

### 7.5 工程最终模型选择

最终工程选择：

- zh audio：`backend/ser-service/training/checkpoints/ser_multilingual_xlsr_stageB_exp04_fast/best_model`
- zh text：`backend/ser-service/training/text_models/zh_sentiment_exp03/best_model`
- fusion：`backend/ser-service/training/fusion/models/fusion_exp04_gated/`

工程选择理由：

- 整体指标显著优于 `fusion_exp03_perlang`
- `ECE` 也同步下降，更适合风险预警场景
- 中文主场景收益非常明显，且收益幅度远大于英文侧轻微回退

## 8. 验收指标达成情况（工程最终口径）

| 指标 | 目标 | 工程最终结果 | 结论 |
|---|---|---|---|
| 融合 test macro-F1 | `>= 0.64` | `0.7761` | 达成 |
| 融合 test ECE | `<= 0.03` | `0.0454` | 未达成 |
| 分语言指标 | 必须提供 | `zh=0.8346`, `en=0.6219` | 达成 |
| 实时压测（30并发40秒） | 不低于历史基线 | `30/30 成功` | 达成 |

## 9. ESD 引用（论文必须带）

- Zhou et al., ICASSP 2021
- Zhou et al., Speech Communication 2022

## 10. 已知局限（当前版本）

- 工程最终模型 `fusion_exp04_gated` 的 `test ECE=0.0454`，仍高于目标 `<=0.03`。
- `exp04` 工程链路来自 `stageB_exp04_fast`，不是正式同名 `stageB_exp04`，论文归档时必须写明。
- 英文侧 `enF1` 相比 `fusion_exp03_perlang` 存在轻微回退，需要在答辩/报告中说明取舍。
- 文本分支仍不是主增益来源，后续重点依然是校准优化与跨语言均衡。
