import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The dev server proxies backend calls to the local gateway so the browser
// stays same-origin and no CORS settings are needed during development.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
