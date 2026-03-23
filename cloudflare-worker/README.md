# Cloudflare Worker Download Proxy / Cloudflare Worker 下载代理

This folder contains the Cloudflare Worker script that acts as a download proxy for AB Download Manager.

本文件夹包含用作 AB Download Manager 下载代理的 Cloudflare Worker 脚本。

## What is this for? / 功能说明

This Worker allows you to route your downloads through Cloudflare's network, which can help:

此 Worker 允许您通过 Cloudflare 网络路由下载，可以帮助：

- Bypass download restrictions from certain ISPs or networks / 绕过某些 ISP 或网络的下载限制
- Access content that might be blocked in your region / 访问可能在您所在地区被屏蔽的内容
- Add an extra layer of privacy to your downloads / 为您的下载增加额外的隐私保护

## Setup Instructions / 设置说明

### Step 1: Create a Cloudflare Account / 第一步：创建 Cloudflare 账户

1. Go to [Cloudflare](https://dash.cloudflare.com/sign-up) / 访问 [Cloudflare](https://dash.cloudflare.com/sign-up)
2. Sign up for a free account / 注册一个免费账户

### Step 2: Create a Worker / 第二步：创建 Worker

1. Go to [Workers & Pages](https://dash.cloudflare.com/?to=/:account/workers-and-pages) / 访问 [Workers & Pages](https://dash.cloudflare.com/?to=/:account/workers-and-pages)
2. Click "Create application" / 点击 "Create application"
3. Select "Create Worker" / 选择 "Create Worker"
4. Give your Worker a name (e.g., "download-proxy") / 为您的 Worker 命名（例如 "download-proxy"）
5. Click "Deploy" / 点击 "Deploy"

### Step 3: Edit the Worker Code / 第三步：编辑 Worker 代码

1. After deployment, click "Edit code" / 部署后，点击 "Edit code"
2. Delete all existing code / 删除所有现有代码
3. Copy the entire contents of `worker.js` from this folder / 复制此文件夹中 `worker.js` 的全部内容
4. **IMPORTANT**: Change the `SECRET_KEY` to a strong random string / **重要**：将 `SECRET_KEY` 更改为强随机字符串
   ```javascript
   const SECRET_KEY = 'YOUR_SECRET_KEY_HERE'; // Change this! / 更改此项！
   ```
5. Click "Save and Deploy" / 点击 "Save and Deploy"

### Step 4: Get Your Worker URL / 第四步：获取 Worker URL

After deployment, you'll see your Worker URL in the format:

部署后，您将看到以下格式的 Worker URL：

```
https://download-proxy.your-subdomain.workers.dev
```

### Step 5: Configure AB Download Manager / 第五步：配置 AB Download Manager

1. Open AB Download Manager / 打开 AB Download Manager
2. Go to Settings → Download Engine / 进入 设置 → 下载引擎
3. Find "Cloudflare Worker Proxy" and click "Change" / 找到 "Cloudflare Worker 代理" 并点击 "更改"
4. Enable the proxy / 启用代理
5. Enter your Worker URL / 输入您的 Worker URL
6. Enter your secret key (the same one you set in the Worker) / 输入您的密钥（与 Worker 中设置的相同）
7. Click "Change" to save / 点击 "更改" 保存

## Security Notes / 安全注意事项

- **Keep your secret key private**: Anyone with your secret key can use your Worker / **保密您的密钥**：任何拥有您密钥的人都可以使用您的 Worker
- **Monitor usage**: Check your Cloudflare dashboard for usage statistics / **监控使用情况**：检查您的 Cloudflare 仪表板以获取使用统计信息
- **Free tier limits**: Cloudflare Workers free tier includes / **免费套餐限制**：Cloudflare Workers 免费套餐包括：
  - 100,000 requests per day / 每天 100,000 次请求
  - 10ms CPU time per request / 每次请求 10ms CPU 时间
  - For heavy downloading, consider upgrading to a paid plan / 对于大量下载，请考虑升级到付费计划

## How It Works / 工作原理

1. When you start a download with the proxy enabled, AB Download Manager sends the request to your Cloudflare Worker / 当您启用代理开始下载时，AB Download Manager 将请求发送到您的 Cloudflare Worker
2. The Worker validates your secret key / Worker 验证您的密钥
3. The Worker forwards the request to the original download URL / Worker 将请求转发到原始下载 URL
4. The Worker streams the response back to AB Download Manager / Worker 将响应流式传输回 AB Download Manager

The original URL and your secret key are sent in custom headers:

原始 URL 和您的密钥通过自定义请求头发送：

- `X-Original-Url`: The actual download URL / 实际下载 URL
- `X-Secret-Key`: Your authentication key / 您的身份验证密钥

## Troubleshooting / 故障排除

### "Unauthorized" Error / "Unauthorized" 错误

- Make sure the secret key in AB Download Manager matches the one in your Worker / 确保 AB Download Manager 中的密钥与 Worker 中的密钥匹配

### "Missing X-Original-Url header" Error / "Missing X-Original-Url header" 错误

- This is an internal error. Try restarting AB Download Manager / 这是内部错误。尝试重启 AB Download Manager

### Slow Downloads / 下载速度慢

- The Worker adds a small overhead to each request / Worker 会为每个请求增加少量开销
- For very large files, the free tier CPU limits might affect performance / 对于非常大的文件，免费套餐的 CPU 限制可能会影响性能
- Consider upgrading to a paid Cloudflare plan for better performance / 考虑升级到付费 Cloudflare 计划以获得更好的性能

### Worker Not Responding / Worker 无响应

- Check the Cloudflare dashboard for any error logs / 检查 Cloudflare 仪表板是否有任何错误日志
- Make sure your Worker is deployed and active / 确保您的 Worker 已部署并处于活动状态
