# FRONTEND_PROJECT_BRIEF

## 1. 项目名称
- **ai-emotion-frontend**

## 2. 技术栈说明

| 维度 | 现状 | 说明 |
|---|---|---|
| 前端框架 | Vue 3 | 使用 Composition API + `<script setup lang=\"ts\">` |
| 语言 | TypeScript | `.ts` + `.vue` 中 TS 类型声明 |
| 构建工具 | Vite 7 | 开发服务器 + 构建打包 |
| UI 框架 | Element Plus | 表单、卡片、按钮、描述列表、消息提示 |
| HTTP | Axios | 封装在 `src/api/http.ts` |
| 路由 | Vue Router 5 | 两个页面路由（提交页、分析详情页） |
| 状态管理 | Pinia 3 | 已安装并注册，但业务上暂未使用（仅保留模板 counter store） |

## 3. 本地运行方式

### 3.1 依赖要求
- Node.js：`^20.19.0 || >=22.12.0`
- npm（随 Node 安装）

### 3.2 安装与启动
```bash
npm install
npm run dev
```

### 3.3 其他常用命令
```bash
npm run build
npm run type-check
npm run lint
```

### 3.4 环境变量
- 当前代码中**未使用自定义环境变量文件**（如 `.env`）。
- API 访问方式为：
  - Axios `baseURL` 固定为 `'/'`；
  - 通过 Vite 开发代理将 `/api` 转发到 `http://localhost:8080`。

## 4. 目录结构导览

```text
.
├─ public/                 # 静态资源（如 favicon）
├─ src/
│  ├─ api/                 # 与后端交互的 API 封装
│  │  ├─ http.ts           # axios 实例配置
│  │  ├─ analysis.ts       # 启动分析接口
│  │  └─ task.ts           # 查询任务详情接口 + 类型定义
│  ├─ router/
│  │  └─ index.ts          # 前端路由定义
│  ├─ stores/
│  │  └─ counter.ts        # Pinia 示例 store（当前业务未用）
│  ├─ views/
│  │  ├─ UploadView.vue    # 提交 audioId 并创建分析任务
│  │  └─ AnalysisView.vue  # 轮询任务状态并展示分析结果
│  ├─ App.vue              # 根组件（仅承载 router-view）
│  └─ main.ts              # 应用入口（注册 Pinia/Router/Element Plus）
├─ vite.config.ts          # Vite 配置（别名、proxy）
├─ package.json            # 依赖与脚本
└─ README.md               # 项目基础说明（模板内容为主）
```

## 5. 路由清单

| 路由路径 | 路由名 | 页面组件 | 页面职责 |
|---|---|---|---|
| `/` | `upload` | `UploadView.vue` | 输入 `audioId`，调用后端启动分析任务，成功后跳转详情页 |
| `/analysis/:taskId` | `analysis` | `AnalysisView.vue` | 按 `taskId` 轮询任务状态，展示分析结果字段 |

## 6. 页面功能完成度

| 页面/模块 | 完成度 | 说明 |
|---|---|---|
| 任务提交页（Upload） | **已完成（基础版）** | 目前为“输入 audioId 并提交”，不含真实文件上传 |
| 分析详情页（Analysis） | **已完成（基础版）** | 支持轮询 + 结果字段展示 |
| 登录页/鉴权流程 | **未开始** | 无登录路由、无 token 管理逻辑 |
| 图表可视化 | **未开始** | 当前仅文本/数值展示，无图表库接入 |
| 全局状态管理 | **进行中（框架已接入，业务未落地）** | Pinia 已可用，但业务数据仍是页面内局部状态 |

## 7. 关键功能说明

### 7.1 登录鉴权
- 当前**未实现**登录与鉴权。
- Axios 请求未统一注入 token，也没有 401/403 全局拦截处理。

### 7.2 上传音频
- 当前不是文件上传流程，而是手动输入 `audioId`。
- 前端调用 `POST /api/audio/{audioId}/analysis/start` 启动分析任务。
- 因此“上传音频”能力在当前项目中**未真正实现**。

### 7.3 任务轮询
- 在分析页中通过 `setInterval` 每 3 秒调用查询接口。
- 当状态为 `SUCCESS / FAILED / CANCELED` 时停止轮询。
- 页面卸载时会清理定时器，避免内存泄漏。

### 7.4 报告页展示
- 报告内容复用在分析详情页中展示（非独立报告路由）。
- 已展示字段包括：`status / overall / confidence / risk_score / risk_level / advice_text`。

### 7.5 图表展示
- 当前无图表组件（例如 ECharts/Chart.js）。
- 分析结果仅通过 `el-descriptions` 以文本方式展示。

## 8. 当前项目中的问题 / 阻塞点

1. **鉴权链路缺失**：没有登录、token 存储、请求拦截器，生产环境接口受保护时将无法访问。
2. **“上传”与业务语义不一致**：页面标题是音频分析提交，但实际输入的是 `audioId`，依赖后端已存在音频记录。
3. **BaseURL 与部署环境耦合较强**：`baseURL` 固定 `'/'`，主要依赖开发代理；生产环境多域名场景需额外配置。
4. **轮询策略较基础**：固定 3 秒轮询，无退避、无超时上限、无错误重试策略。
5. **字段鲁棒性有限**：接口字段命名为下划线风格（`risk_score`），若后端调整为驼峰会直接影响 UI。
6. **错误信息粒度较粗**：catch 中提示为通用文案，缺少后端错误码/错误消息透出。
7. **缺少端到端与组件测试**：当前仓库未见业务测试，回归保障较弱。

## 9. 开发 TODO（按优先级）

### P0（最高优先级）
1. 增加登录鉴权闭环：登录页、token 持久化、axios 请求头注入、401 跳转。
2. 将 API BaseURL 改造为环境变量驱动（开发/测试/生产分离）。
3. 明确并实现“真实文件上传”接口与页面交互（替代手动输入 audioId）。

### P1
4. 升级任务轮询：增加超时上限、重试与指数退避；失败状态展示更明确。
5. 完善错误处理：按 HTTP 状态码和业务码展示用户可理解提示。
6. 增加独立“分析报告页”与分享链接能力（而不仅是任务详情页）。

### P2
7. 引入图表可视化（情绪趋势、风险分布等），提升报告可读性。
8. 用 Pinia 承载跨页面状态（任务信息、用户信息、配置缓存）。
9. 建立测试体系（至少覆盖 API 适配层 + 关键页面交互流程）。
