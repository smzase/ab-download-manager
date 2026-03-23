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
const API_TOKEN = '';

// 可选：限制允许的域名（为空数组表示允许所有域名）
const ALLOWED_DOMAINS = [];

// 可选：限制最大文件大小（字节），0 表示无限制
const MAX_FILE_SIZE = 0;

// 请求超时时间（秒）
const REQUEST_TIMEOUT = 300;

/**
 * 主入口点
 */
export default {
  async fetch(request, env, ctx) {
    // 获取目标 URL
    const url = new URL(request.url);
    const targetUrl = url.searchParams.get('target');

    // 如果没有提供目标 URL，返回错误
    if (!targetUrl) {
      return new Response(JSON.stringify({
        error: 'Missing target parameter',
        message: 'Please provide a target URL via the "target" query parameter'
      }), {
        status: 400,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      });
    }

    try {
      // 验证目标 URL
      const validatedUrl = validateUrl(targetUrl);
      if (!validatedUrl) {
        return new Response(JSON.stringify({
          error: 'Invalid target URL',
          message: 'The provided target URL is not valid or not allowed'
        }), {
          status: 400,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          }
        });
      }

      // 验证 API Token（如果配置了）
      if (API_TOKEN && API_TOKEN.length > 0) {
        const clientToken = request.headers.get('X-Worker-Token');
        if (clientToken !== API_TOKEN) {
          return new Response(JSON.stringify({
            error: 'Unauthorized',
            message: 'Invalid or missing API token'
          }), {
            status: 401,
            headers: {
              'Content-Type': 'application/json',
              'Access-Control-Allow-Origin': '*'
            }
          });
        }
      }

      // 验证域名白名单（如果配置了）
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
          return new Response(JSON.stringify({
            error: 'Domain not allowed',
            message: `The domain ${targetDomain} is not in the allowed list`
          }), {
            status: 403,
            headers: {
              'Content-Type': 'application/json',
              'Access-Control-Allow-Origin': '*'
            }
          });
        }
      }

      // 创建转发请求
      const response = await proxyRequest(request, validatedUrl);
      return response;

    } catch (error) {
      console.error('Worker error:', error);
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: error.message
      }), {
        status: 500,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      });
    }
  }
};

/**
 * 验证并清理 URL
 */
function validateUrl(urlString) {
  try {
    // 解码 URL（处理可能被编码的 URL）
    const decodedUrl = decodeURIComponent(urlString);
    const url = new URL(decodedUrl);

    // 只允许 http 和 https 协议
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null;
    }

    // 禁止访问本地地址
    const hostname = url.hostname.toLowerCase();
    const blockedHosts = [
      'localhost',
      '127.0.0.1',
      '0.0.0.0',
      '[::1]',
      '[::]'
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
  // 简单的私有 IP 检查
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
  // 创建新的请求头
  const headers = new Headers();

  // 复制原始请求头（排除一些敏感头）
  const skipHeaders = [
    'host',
    'cf-connecting-ip',
    'cf-ray',
    'cf-visitor',
    'cf-worker',
    'x-worker-token',
    'x-original-url'
  ];

  for (const [key, value] of originalRequest.headers) {
    if (!skipHeaders.includes(key.toLowerCase())) {
      headers.set(key, value);
    }
  }

  // 设置转发头
  headers.set('X-Forwarded-For', originalRequest.headers.get('CF-Connecting-IP') || '');
  headers.set('X-Forwarded-Proto', originalRequest.headers.get('X-Forwarded-Proto') || 'https');

  // 创建新请求
  const requestInit = {
    method: originalRequest.method,
    headers: headers,
    redirect: 'follow'
  };

  // 处理请求体（对于 POST/PUT 等请求）
  if (['POST', 'PUT', 'PATCH'].includes(originalRequest.method)) {
    requestInit.body = originalRequest.body;
  }

  // 设置超时
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT * 1000);

  try {
    // 发送请求到目标服务器
    const response = await fetch(targetUrl, {
      ...requestInit,
      signal: controller.signal
    });

    clearTimeout(timeoutId);

    // 检查文件大小限制
    if (MAX_FILE_SIZE > 0) {
      const contentLength = response.headers.get('content-length');
      if (contentLength && parseInt(contentLength) > MAX_FILE_SIZE) {
        return new Response(JSON.stringify({
          error: 'File too large',
          message: `File size exceeds maximum allowed size of ${MAX_FILE_SIZE} bytes`
        }), {
          status: 413,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          }
        });
      }
    }

    // 创建响应头
    const responseHeaders = new Headers();

    // 复制原始响应头（保留所有原始头信息，包括 Content-Disposition）
    const preserveHeaders = [
      'content-disposition',
      'content-type',
      'content-length',
      'last-modified',
      'etag',
      'accept-ranges',
      'content-range',
      'cache-control',
      'expires',
      'date'
    ];

    for (const [key, value] of response.headers) {
      // 保留所有原始头，但排除一些 Worker 相关的头
      if (!key.toLowerCase().startsWith('cf-') &&
          !key.toLowerCase().startsWith('x-worker-')) {
        responseHeaders.set(key, value);
      }
    }

    // 添加 CORS 头
    responseHeaders.set('Access-Control-Allow-Origin', '*');
    responseHeaders.set('Access-Control-Allow-Methods', 'GET, HEAD, POST, OPTIONS');
    responseHeaders.set('Access-Control-Allow-Headers', '*');

    // 添加自定义头，标识这是通过 Worker 转发的
    responseHeaders.set('X-Proxied-By', 'Cloudflare-Worker');

    // 返回响应
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders
    });

  } catch (error) {
    clearTimeout(timeoutId);

    if (error.name === 'AbortError') {
      return new Response(JSON.stringify({
        error: 'Request timeout',
        message: `Request timed out after ${REQUEST_TIMEOUT} seconds`
      }), {
        status: 504,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      });
    }

    throw error;
  }
}

/**
 * 处理 OPTIONS 预检请求（CORS）
 */
export async function onRequestOptions(request) {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, HEAD, POST, OPTIONS',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Max-Age': '86400'
    }
  });
}
