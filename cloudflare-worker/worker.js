/**
 * Cloudflare Worker Download Proxy
 * 
 * This script acts as a download proxy that forwards requests to the original URL.
 * It allows you to bypass download restrictions by routing traffic through Cloudflare's network.
 * 
 * Setup Instructions:
 * 1. Go to https://workers.cloudflare.com/
 * 2. Create a new Worker
 * 3. Copy and paste this code into the editor
 * 4. Replace 'YOUR_SECRET_KEY_HERE' with your own secret key
 * 5. Deploy the Worker
 * 6. Copy the Worker URL (e.g., https://your-worker.your-subdomain.workers.dev)
 * 7. In AB Download Manager, go to Settings > Download Engine
 * 8. Enable Cloudflare Worker Proxy and enter the Worker URL and your secret key
 */

// Your secret key for authentication
// IMPORTANT: Change this to a strong random string before deploying!
const SECRET_KEY = 'YOUR_SECRET_KEY_HERE';

// Maximum response size to handle (in bytes)
const MAX_RESPONSE_SIZE = 100 * 1024 * 1024; // 100MB

addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request));
});

async function handleRequest(request) {
  // Only allow GET and HEAD requests
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return new Response('Method Not Allowed', { status: 405 });
  }

  // Verify secret key
  const providedKey = request.headers.get('X-Secret-Key');
  if (providedKey !== SECRET_KEY) {
    return new Response('Unauthorized', { status: 401 });
  }

  // Get the original URL from header
  const originalUrl = request.headers.get('X-Original-Url');
  if (!originalUrl) {
    return new Response('Missing X-Original-Url header', { status: 400 });
  }

  // Validate URL
  let targetUrl;
  try {
    targetUrl = new URL(originalUrl);
  } catch (e) {
    return new Response('Invalid URL', { status: 400 });
  }

  // Only allow HTTP and HTTPS protocols
  if (targetUrl.protocol !== 'http:' && targetUrl.protocol !== 'https:') {
    return new Response('Invalid protocol', { status: 400 });
  }

  try {
    // Build the proxy request
    const proxyHeaders = new Headers();
    
    // Copy relevant headers from the original request
    const headersToForward = [
      'range',
      'user-agent',
      'accept',
      'accept-encoding',
      'accept-language',
      'authorization',
      'referer',
      'cookie',
    ];
    
    for (const header of headersToForward) {
      const value = request.headers.get(header);
      if (value) {
        proxyHeaders.set(header, value);
      }
    }
    
    // Make the request to the original server
    const proxyRequest = new Request(targetUrl, {
      method: request.method,
      headers: proxyHeaders,
      redirect: 'follow',
    });

    const response = await fetch(proxyRequest);
    
    // Build the response headers
    const responseHeaders = new Headers();
    
    // Copy response headers
    const responseHeadersToForward = [
      'content-type',
      'content-length',
      'content-range',
      'content-disposition',
      'accept-ranges',
      'etag',
      'last-modified',
      'cache-control',
      'expires',
    ];
    
    for (const header of responseHeadersToForward) {
      const value = response.headers.get(header);
      if (value) {
        responseHeaders.set(header, value);
      }
    }
    
    // Add CORS headers for cross-origin requests
    responseHeaders.set('Access-Control-Allow-Origin', '*');
    responseHeaders.set('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    responseHeaders.set('Access-Control-Allow-Headers', 'X-Original-Url, X-Secret-Key, Range, User-Agent, Accept, Authorization');
    
    // Add custom header to identify this is a proxied response
    responseHeaders.set('X-Proxied-By', 'AB-Download-Manager-Worker');

    // Return the response
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
    
  } catch (error) {
    console.error('Proxy error:', error);
    return new Response(`Proxy Error: ${error.message}`, { status: 502 });
  }
}

// Handle OPTIONS requests for CORS preflight
addEventListener('fetch', event => {
  if (event.request.method === 'OPTIONS') {
    event.respondWith(handleOptions(event.request));
  }
});

function handleOptions(request) {
  const headers = new Headers();
  headers.set('Access-Control-Allow-Origin', '*');
  headers.set('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  headers.set('Access-Control-Allow-Headers', 'X-Original-Url, X-Secret-Key, Range, User-Agent, Accept, Authorization');
  headers.set('Access-Control-Max-Age', '86400');
  
  return new Response(null, {
    status: 204,
    headers: headers,
  });
}
