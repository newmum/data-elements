import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { localServer } from './dev-server.config';
const proxy = { '/api': { target: process.env.VITE_IDAAS_API_TARGET || 'http://localhost:8088', changeOrigin: true, rewrite: (path: string) => path.replace(/^\/api/, '') } };
export default defineConfig({ plugins: [react()], base: './', server: { ...localServer, proxy, watch: { ignored: ['**/playwright-report/**', '**/test-results/**'] } }, preview: { ...localServer, proxy }, build: { target: 'es2022', sourcemap: false, rollupOptions: { output: { manualChunks: { 'react-vendor': ['react', 'react-dom', 'react-router-dom'], 'antd-vendor': ['antd', '@ant-design/icons'] } } } } });
