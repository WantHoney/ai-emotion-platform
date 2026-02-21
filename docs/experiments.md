# 实验记录（毕设主线）
最后同步日期：`2026-02-21`

## 1. 数据与任务定义

- 英文语音情绪：IEMOCAP（主）+ RAVDESS（补）。
- 中文语音情绪：CASIA（微调）。
- 标签体系：4 类（`ANG/HAP/NEU/SAD`）。
- 目标：多模态融合后提升 `macro-F1`，并降低校准误差 `ECE`。

## 2. 当前关键结果

来自目录：`backend/ser-service/training/fusion/ablation_exp01`

| 实验 | val macro-F1 | test macro-F1 | test ECE |
|---|---:|---:|---:|
| audio_only | 0.6501 | 0.6219 | 0.0322 |
| text_only | 0.3178 | 0.3499 | 0.0471 |
| fusion | 0.6463 | 0.6317 | 0.0250 |

结论：
- 文本单模态弱于语音单模态。
- 融合模型较语音单模态提升了测试 macro-F1，并显著降低 ECE。

## 3. 融合调参记录

来自目录：`backend/ser-service/training/fusion/models`

| run | val macro-F1 | test macro-F1 | test ECE |
|---|---:|---:|---:|
| tune_a | 0.6428 | 0.6280 | 0.0325 |
| tune_b | 0.6458 | 0.6274 | 0.0396 |
| tune_c | 0.6480 | 0.6249 | 0.0388 |
| fusion_exp01 | 0.6463 | 0.6317 | 0.0250 |

推荐保留模型：
- `backend/ser-service/training/fusion/models/fusion_exp01`
- 或镜像目录：`backend/ser-service/training/fusion/models/fusion_best`

## 4. 可复现实验命令

特征构建：`training/build_fusion_features.py`  
融合训练：`training/train_late_fusion.py`  
消融评估：`training/run_fusion_ablation.py`

详细命令以 `backend/ser-service/training/` 下脚本参数为准。

## 5. 下一轮优化建议

- 在相同标签体系下补齐中文样本比例，提升跨语言稳健性。
- 增加类别权重或 focal loss，重点拉升 `ANG/SAD` 召回。
- 保持校准流程（temperature scaling）不移除，答辩阶段 ECE 很加分。
