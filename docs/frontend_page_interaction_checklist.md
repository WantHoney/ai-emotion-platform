# 前端逐页面交互验收清单

本清单用于你手动逐页验收用户端与管理端交互闭环。

## 0. 前置条件

- 服务已启动：
  - `http://127.0.0.1:5173`（frontend）
  - `http://127.0.0.1:8080`（backend）
  - `http://127.0.0.1:8001`（ser-service）
- 健康检查通过：
  - `GET /api/health` 返回 `status=UP`
- 管理员账号可登录（默认）：
  - username: `operator`
  - password: `operator123`

## 1. 用户端验收

### 1.1 `/login` 登录注册分流

- [ ] 切换 `User Portal` 与 `Admin Console` 标签页可用
- [ ] 用户注册成功后自动登录并跳转用户端页面
- [ ] 管理员仅支持登录，不提供注册入口
- [ ] 登录失败时页面不白屏，出现错误提示

### 1.2 `/home` 首页

- [ ] Hero 区 CTA 可点击：`Start Recording` 跳 `upload`，`View Reports` 跳 `reports`
- [ ] 三步流程、创新点、内容推荐模块正常展示
- [ ] 心理中心区域：
  - [ ] 城市切换可刷新列表
  - [ ] `Locate Nearby` 可触发定位查询
  - [ ] 接口失败时只显示局部错误态，不影响整页

### 1.3 `/upload` 上传与录音

- [ ] 文件上传类型校验生效（仅音频）
- [ ] 分片上传进度条实时变化
- [ ] 可取消当前上传会话
- [ ] 浏览器支持时可录音并上传
- [ ] 上传完成后自动创建任务并跳转 `/tasks/{id}`

### 1.4 `/tasks` 与 `/tasks/{id}`

- [ ] 任务列表可加载分页
- [ ] 任务详情页状态可轮询刷新
- [ ] 任务详情页可显示实时通道状态（已连接/重连中/断开）
- [ ] 任务详情页可显示风险曲线随任务推进更新
- [ ] 任务详情页可展示融合指标卡片：语音分/文本分/融合分/PSI 贡献项
- [ ] 成功任务可查看最终结果与报告入口
- [ ] 失败任务错误信息可见，不白屏

### 1.5 `/reports` 与 `/reports/{id}`

- [ ] 报告列表筛选（keyword/emotion/risk）可用
- [ ] 卡片点击可进详情页
- [ ] 详情页可展示：emotion/confidence/score/risk/segments/advice
- [ ] 报告详情页可展示融合指标卡片：语音分/文本分/融合分/PSI 贡献项
- [ ] 推荐文章/书籍区域可正常打开外链（若配置了URL）

### 1.6 `/trends`

- [ ] 天数切换（7/30/90）可刷新趋势数据
- [ ] 图表（柱 + 线）正常绘制
- [ ] 表格数据与图表同步
- [ ] 空数据时显示友好占位

### 1.7 `/psy-centers`

- [ ] 按城市编码查询可返回列表
- [ ] 按坐标 + 半径查询可返回列表
- [ ] `Locate Me` 定位失败时给出友好提示
- [ ] 空/错状态均不白屏

### 1.8 `/profile`

- [ ] 个人身份信息显示正确（username/role）
- [ ] 近30日汇总 KPI 可展示
- [ ] 最近趋势表格可展示（有数据时）

## 2. 管理端验收

### 2.1 `/admin/analytics`

- [ ] 日统计与质量统计接口返回后页面可渲染
- [ ] 图表/指标加载失败时有错误态

### 2.2 `/system`

- [ ] backend/db/ser 状态可见
- [ ] `SER_BASE_URL` 与超时配置展示正确

### 2.3 `/admin/models` `/admin/rules` `/admin/warnings`

- [ ] 列表可加载
- [ ] 规则启停、预警动作（若有数据）可操作
- [ ] 分页/筛选行为正常

### 2.4 `/admin/content`

- [ ] banner/quote/article/book/psy-center 切换正常
- [ ] CRUD 操作后列表刷新正常

## 3. 联调稳定性关注点

- [ ] 浏览器控制台无持续 `ECONNREFUSED`
- [ ] 页面接口失败时均有 Loading/Empty/Error 三态
- [ ] 不出现 `[object Object]` 作为错误提示文案
- [ ] 用户端与管理端权限隔离有效（越权访问会被拦截）

## 4. 建议验收顺序（最短路径）

1. `login -> home -> upload -> task -> report`
2. `reports -> report detail -> trends -> profile`
3. `admin login -> analytics -> system -> rules/warnings -> content`
