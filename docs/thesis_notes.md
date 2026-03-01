# 论文技术要点备忘
最后同步日期：`2026-03-01`

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
- 实时压测证据：`docs/figures/realtime_stress_exp01.md`

## 5. 当前可写结论（更新到 Exp02 二次重跑）

- Exp01 融合测试：`macro-F1=0.6317`, `ECE=0.0250`。
- Exp02 融合测试（calibrated）：`macro-F1=0.7049`, `ECE=0.0601`。
- 相比 Exp01，Exp02 分类能力显著提升（`+0.0732` macro-F1）。
- Exp02 中 `fusion` 与 `audio_only` 基本持平，说明当前文本分支对分类增益有限。
- Exp02 ECE 仍高于目标阈值（`0.03`），后续应优先做分语言校准。

## 6. 答辩材料入口

- 答辩演示脚本：`docs/defense_script.md`
- API 契约：`docs/api.md`
- 架构说明：`docs/architecture.md`

## 7. 收尾写作建议

1. 先写“方法与实现”，再写“实验结果与分析”。
2. 每个实验图回答三个问题：提升多少、为什么、代价是什么。
3. 在结论中强调“可部署、可解释、可治理”的工程价值。
4. 对未达标项（Exp02 ECE）给出明确改进路线，增加学术可信度。

## 8. ESD 引用（中文语音增强实验）

如果论文中使用 ESD，请引用：

- Zhou, K., Sisman, B., Liu, R., Li, H. (ICASSP 2021).
- Zhou, K., Sisman, B., Liu, R., Li, H. (Speech Communication, 2022).
