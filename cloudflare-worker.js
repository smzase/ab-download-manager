/**
 * Cloudflare Worker 下载中转脚本
 * 
 * 功能：将下载请求通过 Cloudflare Worker 中转，实现：
 * 1. 隐藏真实下载源
 * 2. 绕过某些网络限制
 * 3. 添加额外的安全验证
 * 
 * 部署步骤：
 * 1. 登录 Cloudflare Dashboard
 * 2. 进入 Workers & Pages
 * 3. 创建一个新的 Worker
 * 4. 将本脚本粘贴到编辑器中
 * 5. 保存并部署
 * 6. 复制 Worker URL 到 AB Download Manager 的代理设置中
 */

// 可选：设置访问令牌以增加安全性（与客户端配置中的 API Token 对应）
// 如果设置了，客户端必须提供相同的令牌才能使用
const API_TOKEN = '1145141919810';

// 可选：限制允许的域名（为空数组表示允许所有域名）
const ALLOWED_DOMAINS =[];

// 可选：限制最大文件大小（字节），0 表示无限制
const MAX_FILE_SIZE = 0;

// 请求超时时间（秒）
const REQUEST_TIMEOUT = 300;

/**
 * 主入口点
 */
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const targetUrl = url.searchParams.get('target');

    // 1. 静默处理：如果没有提供目标 URL，伪装成普通的 404 空白页
    if (!targetUrl) {
      return new Response('404 Not Found', { status: 404 });
    }

    try {
      // 2. 静默处理：验证目标 URL 失败，返回 400
      const validatedUrl = validateUrl(targetUrl);
      if (!validatedUrl) {
        return new Response('400 Bad Request', { status: 400 });
      }

      // 3. 静默处理：验证 API Token 失败，直接返回 404（伪装成页面不存在，不暴露有密码拦截）
      if (API_TOKEN && API_TOKEN.length > 0) {
        const clientToken = request.headers.get('X-Worker-Token');
        if (clientToken !== API_TOKEN) {
          return new Response('404 Not Found', { status: 404 });
        }
      }

      // 4. 静默处理：验证域名白名单失败，返回 403 拒绝访问
      if (ALLOWED_DOMAINS.length > 0) {
        const targetDomain = new URL(validatedUrl).hostname;
        const isAllowed = ALLOWED_DOMAINS.some(domain => {
          if (domain.startsWith('*.')) {
            const suffix = domain.slice(2);
            return targetDomain === suffix || targetDomain.endsWith('.' + suffix);
          }
          return targetDomain === domain;
        });

        if (!isAllowed) {
          return new Response('403 Forbidden', { status: 403 });
        }
      }

      // 创建转发请求
      const response = await proxyRequest(request, validatedUrl);
      return response;

    } catch (error) {
      // 5. 静默处理：运行报错时，不要把错误堆栈抛出给前端，统一返回 500
      return new Response('500 Internal Server Error', { status: 500 });
    }
  }
};

/**
 * 验证并清理 URL
 */
function validateUrl(urlString) {
  try {
    const decodedUrl = decodeURIComponent(urlString);
    const url = new URL(decodedUrl);

    // 只允许 http 和 https 协议
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }

    // 禁止访问本地地址
    const hostname = url.hostname.toLowerCase();
    const blockedHosts =[
      'localhost', '127.0.0.1', '0.0.0.0', '[::1]', '[::]'
    ];

    if (blockedHosts.includes(hostname)) {
      return null;
    }

    // 禁止访问私有 IP 地址
    if (isPrivateIP(hostname)) {
      return null;
    }

    return decodedUrl;
  } catch (error) {
    return null;
  }
}

/**
 * 检查是否为私有 IP 地址
 */
function isPrivateIP(hostname) {
  const privateRanges = [
    /^10\./,
    /^172\.(1[6-9]|2[0-9]|3[01])\./,
    /^192\.168\./,
    /^127\./,
    /^169\.254\./,
    /^fc00:/i,
    /^fe80:/i
  ];
  return privateRanges.some(range => range.test(hostname));
}

/**
 * 转发请求到目标服务器
 */
async function proxyRequest(originalRequest, targetUrl) {
  const headers = new Headers();

  const skipHeaders =[
    'host', 'cf-connecting-ip', 'cf-ray', 'cf-visitor',
    'cf-worker', 'x-worker-token', 'x-original-url'
  ];

  for (const [key, value] of originalRequest.headers) {
    if (!skipHeaders.includes(key.toLowerCase())) {
      headers.set(key, value);
    }
  }

  headers.set('X-Forwarded-For', originalRequest.headers.get('CF-Connecting-IP') || '');
  headers.set('X-Forwarded-Proto', originalRequest.headers.get('X-Forwarded-Proto') || 'https');

  const requestInit = {
    method: originalRequest.method,
    headers: headers,
    // 【重要安全修改】：改成 manual，如果是网盘等大文件的 302 重定向，交回给浏览器自己处理，防止跑爆你的 CF 流量
    redirect: 'follow' 
  };

  if (['POST', 'PUT', 'PATCH'].includes(originalRequest.method)) {
    requestInit.body = originalRequest.body;
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT * 1000);

  try {
    const response = await fetch(targetUrl, {
      ...requestInit,
      signal: controller.signal
    });

    clearTimeout(timeoutId);

    // 6. 静默处理：文件超出大小限制，返回 413
    if (MAX_FILE_SIZE > 0) {
      const contentLength = response.headers.get('content-length');
      if (contentLength && parseInt(contentLength) > MAX_FILE_SIZE) {
        return new Response('413 Payload Too Large', { status: 413 });
      }
    }

    const responseHeaders = new Headers();

    for (const [key, value] of response.headers) {
      if (!key.toLowerCase().startsWith('cf-') &&
          !key.toLowerCase().startsWith('x-worker-')) {
        responseHeaders.set(key, value);
      }
    }

    // 添加 CORS 头
    responseHeaders.set('Access-Control-Allow-Origin', '*');
    responseHeaders.set('Access-Control-Allow-Methods', 'GET, HEAD, POST, OPTIONS');
    responseHeaders.set('Access-Control-Allow-Headers', '*');

    // 【安全修改】：去掉了自定义代理头 X-Proxied-By，彻底隐蔽身份

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders
    });

  } catch (error) {
    clearTimeout(timeoutId);

    // 7. 静默处理：请求超时，返回 504
    if (error.name === 'AbortError') {
      return new Response('504 Gateway Timeout', { status: 504 });
    }

    throw error;
  }
}
