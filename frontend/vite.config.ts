import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      // In development the API is proxied, so the browser talks to a single origin.
      proxy: { '/api': { target: env.VITE_DEV_API_PROXY || 'http://localhost:8080', changeOrigin: true } },
    },
    build: {
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: { manualChunks: { recharts: ['recharts'], react: ['react', 'react-dom', 'react-router-dom'] } },
      },
    },
  };
});
