# Frontend（Vue 3 + Vite）

## 环境要求

- Node.js `^20.19.0 || >=22.12.0`
- npm

## 开发启动

```bash
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## 常用命令

```bash
npm run lint
npm run build
npm run preview
```

## API 联调

- 本地开发默认通过 Vite proxy 转发：`/api -> http://localhost:8080`
- 配置文件：`vite.config.ts`

如果出现 `ECONNREFUSED`：
1. 确认 backend 已启动在 `8080`。
2. 确认 proxy target 没有被改坏。
3. 检查本机端口占用与代理软件。
