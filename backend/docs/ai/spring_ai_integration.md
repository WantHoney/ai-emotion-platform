# 大模型增强链路说明

本文档说明后端如何接入本地大模型与外部可配置增强能力。系统主链路仍以本地模型服务为核心，外部大模型只作为解释增强和兜底能力。

## 1. 设计目标

- 保持音频分析主流程稳定：上传、任务、模型推理、报告入库、前端展示形成闭环。
- 本地模型优先：语音情绪识别、文本情感分析和融合判断优先走本机模型服务。
- 本地大模型增强：通过 Ollama Gemma 4 生成更自然的解释文本和建议表达。
- 外部模型可选：OpenRouter 作为可配置增强或保底能力，不影响核心功能运行。

## 2. 后端结构

```text
AnalysisTaskWorkerService
  -> SerClient：调用 FastAPI 模型服务
  -> NarrativeGenerationService：生成报告解释和建议
  -> OllamaNarrativeClient：调用本地 Ollama Gemma 4
  -> SpringAiClient：外部模型增强的兼容入口
  -> CoreReportRepository：保存报告快照
```

## 3. 输入与输出

输入来源：

- 音频文件元数据
- ASR 转写文本
- 语音情绪概率
- 文本情感分数
- 风险评估结果

输出结果：

- 任务状态
- 分段情绪结果
- 综合风险等级
- 报告正文
- 建议与资源推荐

## 4. 兜底机制

系统按以下顺序处理报告解释：

1. 优先使用本地模型服务返回的结构化结果。
2. 若启用本地大模型，则调用 Ollama Gemma 4 做解释增强。
3. 若本地大模型不可用，则使用后端结构化模板生成稳定报告。
4. 若配置了 OpenRouter，可按环境变量启用外部增强。

这样即使某个模型服务短时不可用，系统也能返回可解释、可展示的报告结果。

## 5. 关键配置

- `AI_MODE`
- `SPRING_AI_OPENAI_ENABLED`
- `OPENROUTER_API_KEY`
- `OPENROUTER_BASE_URL`
- `OPENROUTER_MODEL`
- `ANALYSIS_NARRATIVE_OLLAMA_ENABLED`
- `ANALYSIS_NARRATIVE_OLLAMA_BASE_URL`
- `ANALYSIS_NARRATIVE_OLLAMA_MODEL`

## 6. 验证方式

- 查看 `/api/health` 中后端、数据库、模型服务状态。
- 上传一段音频后观察任务状态是否从 `PENDING` 进入 `RUNNING` 和 `SUCCESS`。
- 打开报告详情页，检查风险等级、分段结果、建议内容是否完整。
- 在日志中查看 Ollama 或外部增强失败时是否进入本地结构化兜底。
