# 论文技术要点备忘
最后同步日期：`2026-03-03`

## 1. 题目与研究边界

题目建议：

- 面向用户的多模态语音情绪分析与心理状态预警 Web 系统设计与实现。

边界声明：

- 系统输出“风险提示 + 趋势评分 + 干预建议”，不输出医学诊断结论。

## 2. 可写创新点（对应代码）

- 多模态融合：语音情绪概率 + 文本情感向量 -> late fusion。
- 校准治理：temperature scaling 量化模型置信度偏差。
- 实时能力：WebSocket 推送任务快照与风险曲线。
- 可解释性：报告页/任务页展示语音分、文本分、融合分、PSI 贡献。
- 治理闭环：漂移扫描 -> 预警落库 -> 运营动作回写。

## 3. 方法章节建议结构

1. 系统架构与技术选型（前端/后端/SER 服务/数据库）。
2. 语音情绪模型训练（wav2vec2 分类微调，4 类标签）。
3. 文本情感建模（中英文分路推理）。
4. 多模态融合与温度校准（性能 + 可靠性双目标）。
5. PSI 指标与风险分层策略。
6. 实时任务流与治理闭环实现。

## 4. 实验章节可直接引用材料

- 实验主文档：`docs/experiments.md`
- 图表源数据：`docs/figures/README.md`
- Exp01 基线：`docs/figures/ablation_metrics.csv`
- Exp02 结果：
  - `docs/figures/ablation_metrics_exp02_esd.csv`
  - `docs/figures/calibration_metrics_exp02_esd.csv`
  - `docs/figures/dataset_composition_exp02_esd.csv`
  - `docs/figures/model_selection_exp02_esd.csv`
- Exp03 结果：
  - `docs/figures/ablation_metrics_exp03_perlang.csv`
  - `docs/figures/calibration_metrics_exp03.csv`
  - `docs/figures/model_selection_exp03.csv`
  - `docs/figures/text_shrink_exp03.csv`
- 实时压测证据：`docs/figures/realtime_stress_exp01.md`
- 实时压测复测：`docs/figures/realtime_stress_exp02.md`

## 5. 当前可写结论（更新到 Exp03）

- Exp01 融合测试：`macro-F1=0.6317`, `ECE=0.0250`。
- Exp03 融合主候选（per-language calibrated）：`macro-F1=0.7030`, `ECE=0.0562`。
- Exp03 分语言指标：`zh F1=0.7221`, `en F1=0.6239`。
- Exp03 消融：`audio_only(F1=0.7049, ECE=0.0502)` 略优于 `fusion(F1=0.7030, ECE=0.0562)`。
- Exp03 校准对比：`vector_scaling` 虽提升 F1（`0.7129`）但 ECE 恶化（`0.1297`），不满足上线约束。
- 因此上线选择 `fusion_exp03_perlang`，并将“文本分支 4 类对齐增强”作为后续优化方向。

## 5.1 已实现的实验能力（可直接写方法章节）

- 文本分支重训脚本：`backend/ser-service/training/train_text_sentiment_from_features.py`
  - 中文域文本训练
  - 情绪到情感标签对齐（4 类 -> 3 类）
- 融合校准扩展：`backend/ser-service/training/train_late_fusion.py`
  - 支持 `per_language_temperature` 与 `vector_scaling`
- 分语言评估产物：
  - `test_macro_f1_zh`
  - `test_macro_f1_en`
  - `test_metrics_by_language_calibrated`

## 6. 答辩材料入口

- 答辩演示脚本：`docs/defense_script.md`
- API 契约：`docs/api.md`
- 架构说明：`docs/architecture.md`

## 7. 收尾写作建议

1. 先写“方法与实现”，再写“实验结果与分析”。
2. 每个实验图回答三个问题：提升多少、为什么、代价是什么。
3. 在结论中强调“可部署、可解释、可治理”的工程价值。
4. 对未达标项（Exp03 ECE）给出明确改进路线，增加学术可信度。

## 7.1 下一步优化项（可放展望）

- 文本分支做 4 类情绪对齐训练（`ANG/HAP/NEU/SAD`），减少文本模态噪声。
- 优先优化分语言校准（per-language calibration），目标进一步压低 ECE。
- 在保证 ECE 的前提下再追求融合 F1 提升，维持“风险预警场景校准优先”的路线。

## 8. ESD 引用（中文语音增强实验）

如果论文中使用 ESD，请引用：

- Zhou, K., Sisman, B., Liu, R., Li, H. (ICASSP 2021).
- Zhou, K., Sisman, B., Liu, R., Li, H. (Speech Communication, 2022).
