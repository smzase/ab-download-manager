# Cloudflare Worker 下载代理

本文件夹包含用作 AB Download Manager 下载代理的 Cloudflare Worker 脚本。

## 功能说明

此 Worker 允许您通过 Cloudflare 网络路由下载，可以帮助：

- 绕过某些 ISP 或网络的下载限制
- 访问可能在您所在地区被屏蔽的内容
- 为您的下载增加额外的隐私保护

## 设置说明

### 第一步：创建 Cloudflare 账户

1. 访问 [Cloudflare](https://dash.cloudflare.com/sign-up)
2. 注册一个免费账户

### 第二步：创建 Worker

1. 访问 [Workers & Pages](https://dash.cloudflare.com/?to=/:account/workers-and-pages)
2. 点击 "Create application"
3. 选择 "Create Worker"
4. 为您的 Worker 命名（例如 "download-proxy"）
5. 点击 "Deploy"

### 第三步：编辑 Worker 代码

1. 部署后，点击 "Edit code"
2. 删除所有现有代码
3. 复制此文件夹中 `worker.js` 的全部内容
4. **重要**：将 `SECRET_KEY` 更改为强随机字符串
   ```javascript
   const SECRET_KEY = 'YOUR_SECRET_KEY_HERE'; // 更改此项！
   ```
5. 点击 "Save and Deploy"

### 第四步：获取 Worker URL

部署后，您将看到以下格式的 Worker URL：

```
https://download-proxy.your-subdomain.workers.dev
```

### 第五步：配置 AB Download Manager

1. 打开 AB Download Manager
2. 进入 设置 → 下载引擎
3. 找到 "Cloudflare Worker 代理" 并点击 "更改"
4. 启用代理
5. 输入您的 Worker URL
6. 输入您的密钥（与 Worker 中设置的相同）
7. 点击 "更改" 保存

## 安全注意事项

- **保密您的密钥**：任何拥有您密钥的人都可以使用您的 Worker
- **监控使用情况**：检查您的 Cloudflare 仪表板以获取使用统计信息
- **免费套餐限制**：Cloudflare Workers 免费套餐包括：
  - 每天 100,000 次请求
  - 每次请求 10ms CPU 时间
  - 对于大量下载，请考虑升级到付费计划

## 工作原理

1. 当您启用代理开始下载时，AB Download Manager 将请求发送到您的 Cloudflare Worker
2. Worker 验证您的密钥
3. Worker 将请求转发到原始下载 URL
4. Worker 将响应流式传输回 AB Download Manager

原始 URL 和您的密钥通过自定义请求头发送：

- `X-Original-Url`：实际下载 URL
- `X-Secret-Key`：您的身份验证密钥

## 故障排除

### "Unauthorized" 错误

- 确保 AB Download Manager 中的密钥与 Worker 中的密钥匹配

### "Missing X-Original-Url header" 错误

- 这是内部错误。尝试重启 AB Download Manager

### 下载速度慢

- Worker 会为每个请求增加少量开销
- 对于非常大的文件，免费套餐的 CPU 限制可能会影响性能
- 考虑升级到付费 Cloudflare 计划以获得更好的性能

### Worker 无响应

- 检查 Cloudflare 仪表板是否有任何错误日志
- 确保您的 Worker 已部署并处于活动状态
