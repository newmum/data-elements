/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3030,
    proxy: {
      '/dws': 'http://127.0.0.1:8088',
      '/api': 'http://127.0.0.1:8088',
      '/magic': 'http://127.0.0.1:8088',
      '/dst': 'http://127.0.0.1:8088',
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './vitest.setup.ts',
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/compiler/**', 'src/domain/**'],
      reporter: ['text', 'text-summary'],
    },
  },
});
