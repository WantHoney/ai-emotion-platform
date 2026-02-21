# 论文技术要点备忘
最后同步日期：`2026-02-21`

## 1. 题目与研究边界

题目建议：

- 面向用户的多模态语音情绪分析与心理状态预警 Web 系统设计与实现。

边界声明：

- 系统输出“风险提示 + 趋势评分 + 干预建议”，不输出医学诊断结论。

## 2. 可写创新点（对应代码）

- 多模态融合：语音情绪概率 + 文本情感向量 -> late fusion。
- 校准治理：temperature scaling 显著降低 ECE。
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
- 消融数据：`docs/figures/ablation_metrics.csv`
- 校准数据：`docs/figures/calibration_metrics.csv`
- 调参数据：`docs/figures/model_selection.csv`
- 数据构成：`docs/figures/dataset_composition.csv`
- 实时压测证据：`docs/figures/realtime_stress_exp01.md`

## 5. 可直接写进论文的结果结论

- 语音单模态（audio_only）测试 `macro-F1=0.6219`。
- 文本单模态（text_only）测试 `macro-F1=0.3499`。
- 融合模型（fusion）测试 `macro-F1=0.6317`，较语音单模态提升 `+0.0099`。
- 融合模型测试 `ECE=0.0250`，较语音单模态下降 `22.39%`。
- 温度校准使融合模型测试 `ECE` 从 `0.2058` 降到 `0.0250`。

## 6. 答辩材料入口

- 答辩演示脚本：`docs/defense_script.md`
- API 契约：`docs/api.md`
- 架构说明：`docs/architecture.md`

## 7. 收尾写作建议

1. 先写“方法与实现”，再写“实验结果与分析”。
2. 每个实验图至少回答三个问题：提升多少、为什么、代价是什么。
3. 结论章节强调“可部署、可解释、可治理”的工程价值。
