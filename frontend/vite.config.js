import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development the React app calls /api/... on its own origin and Vite forwards those
// requests to the Spring Boot backend, so the browser never needs CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_BACKEND_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'node',
    },
  };
});
