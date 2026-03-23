/**
 * Cloudflare Worker Download Proxy Script
 *
 * This worker acts as a proxy to download files through Cloudflare's network.
 * Original URL -> Cloudflare Worker -> Your Computer
 *
 * Deployment Instructions:
 * 1. Go to https://dash.cloudflare.com/workers
 * 2. Create a new Worker
 * 3. Replace the default script with this code
 * 4. Configure your custom domain or use the default *.workers.dev domain
 * 5. Set the AUTHORIZATION_KEY environment variable in Worker settings
 * 6. Deploy the worker
 *
 * Usage:
 * After deployment, in the app settings, enter:
 * - Domain: your-worker.your-subdomain.workers.dev
 * - Key: The authorization key you set in step 5
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Handle the proxy request
    if (url.pathname === '/' && request.method === 'GET') {
      // This is a proxy request with ?url= parameter
      const targetUrl = url.searchParams.get('url');

      if (!targetUrl) {
        return new Response('Missing url parameter', { status: 400 });
      }

      // Validate the URL
      let parsedUrl;
      try {
        parsedUrl = new URL(targetUrl);
      } catch {
        return new Response('Invalid URL', { status: 400 });
      }

      // Verify protocol is allowed
      if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
        return new Response('Only HTTP and HTTPS protocols are allowed', { status: 400 });
      }

      // Get authorization key from header
      const authKey = request.headers.get('CF-Authorization');

      // Verify authorization if key is configured
      if (env.AUTHORIZATION_KEY && authKey !== env.AUTHORIZATION_KEY) {
        return new Response('Unauthorized', { status: 401 });
      }

      try {
        // Create the fetch request to the target URL
        const headers = new Headers();

        // Forward relevant headers
        const forwardedHeaders = [
          'Accept',
          'Accept-Encoding',
          'Accept-Language',
          'Cache-Control',
          'Range',
          'Referer',
          'Origin',
          'User-Agent',
        ];

        for (const header of forwardedHeaders) {
          const value = request.headers.get(header);
          if (value) {
            headers.set(header, value);
          }
        }

        // Remove hop-by-hop headers
        headers.delete('Host');
        headers.delete('Connection');
        headers.delete('Keep-Alive');
        headers.delete('Upgrade');
        headers.delete('Sec-WebSocket-Key');
        headers.delete('Sec-WebSocket-Version');
        headers.delete('Sec-WebSocket-Accept');

        // Make the request
        const response = await fetch(targetUrl, {
          method: request.method,
          headers,
          body: request.method !== 'GET' && request.method !== 'HEAD' ? request.body : undefined,
          redirect: 'follow',
        });

        // Create response headers
        const responseHeaders = new Headers();

        // Forward response headers
        const allowedResponseHeaders = [
          'Content-Type',
          'Content-Length',
          'Content-Range',
          'Accept-Ranges',
          'Content-Disposition',
          'Content-Description',
          'Last-Modified',
          'ETag',
          'Cache-Control',
          'Expires',
          'Connection',
          'Transfer-Encoding',
        ];

        for (const header of allowedResponseHeaders) {
          const value = response.headers.get(header);
          if (value) {
            responseHeaders.set(header, value);
          }
        }

        // Add CORS headers
        responseHeaders.set('Access-Control-Allow-Origin', '*');
        responseHeaders.set('Access-Control-Allow-Methods', 'GET, HEAD, POST, PUT, DELETE, OPTIONS');
        responseHeaders.set('Access-Control-Allow-Headers', '*');

        // Handle content-length for streaming
        const contentLength = response.headers.get('Content-Length');
        if (contentLength) {
          responseHeaders.set('Content-Length', contentLength);
        }

        // Return the response with streaming
        return new Response(response.body, {
          status: response.status,
          statusText: response.statusText,
          headers: responseHeaders,
        });

      } catch (error) {
        console.error('Proxy error:', error);
        return new Response('Proxy error: ' + error.message, { status: 502 });
      }
    }

    // Health check endpoint
    if (url.pathname === '/health') {
      return new Response(JSON.stringify({
        status: 'ok',
        timestamp: new Date().toISOString(),
      }), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Invalid request
    return new Response('Not found', { status: 404 });
  },
};