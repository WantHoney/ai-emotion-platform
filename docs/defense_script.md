# 答辩演示脚本（第 9-10 周收尾）
最后同步日期：`2026-03-03`

## 1. 演示目标（8-10 分钟）

- 证明系统不是“单点模型 demo”，而是完整的多模态分析与治理闭环。
- 展示三条主线：
- 主线 A：分析能力（语音 + 文本 + 融合 + PSI）。
- 主线 B：实时能力（WebSocket 时间轴与风险曲线）。
- 主线 C：治理能力（漂移扫描、预警入库、处置回写）。

## 2. 演示前 5 分钟检查

1. 启动服务并确认健康状态。
2. 确认 SER 模型、文本模型、融合模型均 `ready=true`。
3. 准备一个已登录账号和一个可访问任务 ID。
4. 打开前端两个页面：`TaskView` 与 `ReportView`。

建议检查命令：

```powershell
Invoke-RestMethod http://127.0.0.1:8001/warmup | ConvertTo-Json -Depth 6
Invoke-RestMethod http://127.0.0.1:8001/health | ConvertTo-Json -Depth 6
Invoke-RestMethod http://127.0.0.1:8080/api/health | ConvertTo-Json -Depth 6
```

## 3. 现场脚本（建议话术）

### 3.1 第一段：端到端分析（约 3 分钟）

1. 上传音频并触发分析任务。
2. 打开 `TaskView` 展示阶段进度和风险曲线。
3. 打开 `ReportView` 展示可解释指标：
- 语音分
- 文本分
- 融合分
- PSI 贡献项

一句话要点：
“系统输出的是风险提示与趋势，不是医学诊断结论。”

### 3.2 第二段：实验结论（约 2 分钟）

配合 `docs/figures/*.csv` 图表：

1. 消融图（Exp03）：`audio_only` 与 `fusion` 接近，`audio_only` 略优（F1/ECE）。
2. 校准图（Exp03）：`fusion` 的 `ECE` 从 `0.2140` 降至 `0.0562`，校准收益显著。
3. 模型选择图（Exp03）：`vector_scaling` 虽提高 F1（`0.7129`），但 ECE 恶化到 `0.1297`，不满足上线约束。
4. 数据构成图：中文主导（约 80%）+ 英文保底（约 20%）的训练分布。
5. 口径说明：`0.2140` 是 Exp03 未校准 ECE，Exp03 校准后是 `0.0562`；`0.0250` 是 Exp01 校准后 ECE，不能跨口径比较。

### 3.3 第三段：治理闭环（约 2-3 分钟）

1. 管理员触发漂移扫描。
2. 查看预警列表（`NEW/FOLLOWING`）。
3. 对一条预警执行处置动作并回查 action 记录。

默认阈值命令（答辩口径）：

```powershell
Invoke-RestMethod -Method POST "http://127.0.0.1:8080/api/admin/governance/drift/scan?windowDays=7&baselineDays=7&mediumThreshold=0.15&highThreshold=0.25&minSamples=20" `
  -Headers @{ Authorization = "Bearer <adminToken>" }
```

## 4. 压测证据（加分项）

压测命令：

```powershell
.\scripts\stress-realtime.ps1 -TaskId 31 -AccessToken "<adminAccessToken>" -Connections 30 -DurationSec 40
```

本轮记录：`30/30` 连接成功，`failed=0`。  
证据文件：

- `docs/figures/realtime_stress_exp01.md`
- `docs/figures/realtime_stress_exp02.md`

## 5. 常见追问与回答模板

1. “为什么不是医学诊断？”
答：系统定位是风险预警与辅助干预，不替代临床诊断，避免伦理和合规风险。

2. “为什么最终上线不是 vector_scaling？”
答：本系统是风险预警场景，校准可靠性优先。`vector_scaling` 虽提升 F1，但 ECE 显著恶化（`0.1297`），因此不选。

3. “为什么 fusion 没超过 audio_only，还保留融合架构？”
答：当前文本分支仍有噪声，导致 fusion 未稳定超过 audio-only；但融合架构已具备语言感知校准、可解释输出与后续可扩展能力，工程价值成立。

4. “实时是不是毫秒级真流式？”
答：毕设目标是工程可落地的准实时监测，重点是时间轴更新、风险曲线平滑和可观测闭环。

## 6. 失败兜底预案

1. `8080` 端口冲突：先释放端口或切换 `server.port`。
2. `401 Unauthorized`：重新登录管理员并替换 token。
3. `SER unavailable`：先看 `http://127.0.0.1:8001/health`，再查 `backend/ser-service/logs/`。
4. 前端白屏：改用 API 直接演示（`Invoke-RestMethod` + `docs/figures` 实验结果）保证答辩可继续。
