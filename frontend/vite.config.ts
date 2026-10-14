import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 允许通过 Cloudflare Quick Tunnel 的 trycloudflare 域名访问（Vite 默认会拦截外部 host 的 DNS rebinding）
    allowedHosts: ['.trycloudflare.com', 'localhost', '127.0.0.1'],
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:18080',
        changeOrigin: true
      }
    }
  }
});
