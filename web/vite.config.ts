import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// The API is called on this origin and proxied, so the browser never makes a cross-origin
// request and no CORS configuration is needed on the gateway for local development — same
// approach as admin/vite.config.ts.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    plugins: [react()],
    server: {
      port: 5174,
      proxy: {
        '/api': {
          target: env.VITE_API_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
