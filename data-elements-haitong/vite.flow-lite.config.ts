import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  publicDir: false,
  resolve: { alias: { '@': path.resolve(__dirname, 'src/nifi') } },
  build: {
    target: 'chrome100',
    outDir: '../logs/haitong-flow-lite/frontend-build',
    emptyOutDir: true,
    rollupOptions: { input: path.resolve(__dirname, 'flow-lite.html') },
  },
});
