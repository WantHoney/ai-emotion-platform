# 实验记录（毕设主线）
最后同步日期：`2026-02-21`

## 1. 数据与任务定义

- 英文语音情绪：IEMOCAP（主）+ RAVDESS（补）。
- 中文语音情绪：CASIA（微调）。
- 标签体系：4 类（`ANG/HAP/NEU/SAD`）。
- 主目标：验证多模态融合相对单模态语音的增益，并评估校准效果（`ECE`）。

特征集统计（`backend/ser-service/training/fusion/features_exp01_full/summary.json`）：

| split | total | en | zh | zh ratio | asr_missing_transcript |
|---|---:|---:|---:|---:|---:|
| train | 4307 | 3907 | 400 | 9.29% | 53 |
| val | 1339 | 1139 | 200 | 14.94% | 11 |
| test | 1549 | 1349 | 200 | 12.91% | 17 |

## 2. 消融核心结果（Ablation）

来源：`backend/ser-service/training/fusion/ablation_exp01/ablation_summary.json`

| 模式 | val macro-F1 | test macro-F1 | test ECE |
|---|---:|---:|---:|
| audio_only | 0.6501 | 0.6219 | 0.0322 |
| text_only | 0.3178 | 0.3499 | 0.0471 |
| fusion | 0.6463 | 0.6317 | 0.0250 |

关键增益（`fusion` vs `audio_only`）：

- `test macro-F1`：`+0.0099`（相对提升约 `1.59%`）。
- `test ECE`：`-0.0072`（相对下降约 `22.39%`）。

结论：

- 文本单模态在 4 类情绪任务上不足以独立承担分类主干。
- 文本作为辅助模态可以提升最终分类稳健性并改善置信度校准。

## 3. 温度校准收益（Fusion 模型）

来源：`backend/ser-service/training/fusion/models/fusion_exp01/train_report.json`

| split | 指标 | uncalibrated | calibrated | delta |
|---|---|---:|---:|---:|
| val | NLL | 1.1458 | 0.9146 | -0.2312 |
| val | Brier | 0.5475 | 0.4894 | -0.0582 |
| val | ECE | 0.2079 | 0.0463 | -0.1616 |
| test | NLL | 1.1451 | 0.9498 | -0.1953 |
| test | Brier | 0.5777 | 0.5132 | -0.0645 |
| test | ECE | 0.2058 | 0.0250 | -0.1808 |

备注：`test ECE` 下降约 `87.87%`，是论文“模型可靠性”部分的重点证据。

## 4. 融合调参记录（Model Selection）

来源：`backend/ser-service/training/fusion/models/model_selection_exp01.json`

| run | val macro-F1 | test macro-F1 | test ECE |
|---|---:|---:|---:|
| fusion_exp01 | 0.6463 | 0.6317 | 0.0250 |
| tune_a | 0.6428 | 0.6280 | 0.0325 |
| tune_b | 0.6458 | 0.6274 | 0.0396 |
| tune_c | 0.6480 | 0.6249 | 0.0388 |

当前保留模型：

- `backend/ser-service/training/fusion/models/fusion_best`

## 5. 实时通道与治理闭环证据

### 5.1 WebSocket 实时通道压测

实测命令：`scripts/stress-realtime.ps1 -TaskId 27 -Connections 30 -DurationSec 40`  
实测结果：`success=30/30, failed=0, totalMessages=30`  
记录文件：

- `docs/figures/realtime_stress_exp01.md`
- `docs/figures/realtime_stress_exp01.json`

### 5.2 漂移扫描默认阈值回归

执行：

`POST /api/admin/governance/drift/scan?windowDays=7&baselineDays=7&mediumThreshold=0.15&highThreshold=0.25&minSamples=20`

返回：`{"created":0}`，说明演示期低阈值扫描未被持续使用，已回到默认治理口径。

### 5.3 处置闭环状态

当前预警总量：`3`，状态分布：`NEW=2, FOLLOWING=1`。  
证明链路：检测 -> 入库 -> 运营动作回写已打通。

## 6. 可复现实验命令（训练链路）

- 特征构建：`training/build_fusion_features.py`
- 融合训练：`training/train_late_fusion.py`
- 消融评估：`training/run_fusion_ablation.py`
- 图表源数据导出：`python scripts/export_experiment_assets.py`

详细参数以各脚本 `--help` 和训练目录配置为准。

## 7. 下一轮优化（代码可继续推进）

- 提升中文样本占比（当前 `test` 仅约 `12.91%`），优化跨语言泛化。
- 引入类别重加权或 focal loss，重点拉升 `ANG/SAD` 召回。
- 增加时序平滑消融（曲线平滑前后预警稳定性对比）用于第 10 周答辩加分项。
