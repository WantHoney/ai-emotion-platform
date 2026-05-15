# 前端项目（Vue 3 + Vite）

本目录包含用户端和管理端页面，主要技术栈为 Vue 3、Vite、Element Plus 和 Pinia。

## 1. 环境要求

- Node.js `^20.19.0 || >=22.12.0`
- npm

## 2. 本地启动

```bash
npm install
npm run dev
```

默认地址：

- `http://127.0.0.1:5173`

## 3. 常用命令

```bash
npm run type-check
npm run build
npm run preview
```

## 4. 页面入口

- 用户端首页：`/app/home`
- 用户登录：`/app/login`
- 音频上传：`/app/upload`
- 任务列表：`/app/tasks`
- 报告列表：`/app/reports`
- 内容专栏：`/app/content`
- 心理中心：`/app/psy-centers`
- 管理端登录：`/admin/login`
- 管理端首页：`/admin/dashboard`
- 预警治理：`/admin/warnings`
- 内容管理：`/admin/content`

## 5. 接口联调

本地开发通过 Vite 代理转发接口：

- 前端请求：`/api/*`
- 后端目标：`http://127.0.0.1:8080/api/*`
- 配置文件：`vite.config.ts`

如果出现 `ECONNREFUSED`：

1. 确认后端已启动在 `8080`。
2. 确认前端已启动在 `5173`。
3. 检查 `vite.config.ts` 中的代理目标。
