import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Each Data Elements frontend owns its port and can run without its siblings.
const localServer = { host: 'localhost', port: 3020, strictPort: true };

export default defineConfig({
  plugins: [react()],
  base: '/',
  server: { ...localServer, proxy: { '/api/ai': { target: 'http://127.0.0.1:3021' } } },
  preview: localServer,
});
