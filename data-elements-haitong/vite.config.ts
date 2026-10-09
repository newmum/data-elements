import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig(({ command, isPreview }) => ({
  plugins: [react()],
  // The platform serves this bundle at /haitong/. Keep dev at / so existing
  // localhost links work, but emit absolute production asset URLs for reloads.
  // `vite preview` must resolve the same /haitong/assets URLs emitted by
  // `vite build`; selecting a custom build mode must not change deployment.
  base: command === 'build' || isPreview ? '/haitong/' : '/',
  resolve: { alias: { '@': path.resolve(__dirname, 'src/nifi') } },
  server: {
    host: 'localhost',
    port: 3002,
    strictPort: true,
    // Transform the first canvas view during server startup. Vite otherwise
    // transforms this lazy route only on the first user navigation in dev.
    warmup: { clientFiles: [
      './src/pages/development/CanvasPage.tsx',
      './src/nifi/pages/EditorPage.tsx',
      './src/nifi/canvas/Canvas.tsx',
      './src/nifi/canvas/BaseNode.tsx',
      './src/nifi/panels/Toolbar.tsx',
    ] },
    proxy: {
      '/nifi/api': { target: 'http://localhost:8088', changeOrigin: true },
      '/nifi-api': { target: 'http://localhost:8088', changeOrigin: true },
      '/nifi-ui': { target: 'http://localhost:8088', changeOrigin: true },
      '/dev-api': { target: 'http://localhost:8088', changeOrigin: true, rewrite: (requestPath) => requestPath.replace(/^\/dev-api/, '') },
    },
  },
  preview: { host: '127.0.0.1', port: 4173 },
  // Production workstations include Chrome 109; Vite must also lower CSS syntax
  // for that engine instead of using its newer default browser targets.
  build: { target: 'chrome109', cssTarget: 'chrome109', chunkSizeWarningLimit: 1200 },
}));
