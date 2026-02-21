# 实时通道压测记录（exp01）
最后同步日期：`2026-02-21`

## 1. 压测命令

```powershell
.\scripts\stress-realtime.ps1 -TaskId 27 -AccessToken "<adminAccessToken>" -Connections 30 -DurationSec 40
```

## 2. 运行环境

- 日期：`2026-02-21`
- WebSocket 地址：`ws://127.0.0.1:8080/ws/tasks/stream`
- 鉴权方式：`accessToken` query 参数
- 任务状态：目标任务已存在且可访问

## 3. 输出摘要

```text
[stress] summary
  total clients : 30
  success       : 30
  failed        : 0
  total messages: 30
  avg/client    : 1.0
```

## 4. 结论

- 在 30 并发连接、40 秒窗口下，连接建立成功率为 `100%`。
- 本轮属于“答辩级实时链路连通与稳定性”验证，覆盖鉴权、握手、消息接收、连接关闭全流程。
