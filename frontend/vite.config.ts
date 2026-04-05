import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

const normalizePath = (id: string) => id.replace(/\\/g, '/')

const resolveElementPlusChunk = (id: string) => {
  const normalized = normalizePath(id)

  if (normalized.includes('/node_modules/@element-plus/icons-vue/')) {
    return 'element-plus-icons'
  }

  if (normalized.includes('/node_modules/element-plus/')) {
    return 'element-plus'
  }

  return null
}

const resolveVendorChunk = (id: string) => {
  const normalized = normalizePath(id)

  const elementChunk = resolveElementPlusChunk(normalized)
  if (elementChunk) {
    return elementChunk
  }

  if (normalized.includes('/node_modules/vue-router/')) {
    return 'vue-router'
  }

  if (normalized.includes('/node_modules/pinia/')) {
    return 'pinia'
  }

  if (normalized.includes('/node_modules/@vueuse/motion/')) {
    return 'vueuse-motion'
  }

  if (normalized.includes('/node_modules/vue/') || normalized.includes('/node_modules/@vue/')) {
    return 'vue-core'
  }

  return 'vendor'
}

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  plugins: [vue(), command === 'serve' ? vueDevTools() : null].filter(Boolean),
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            return resolveVendorChunk(id)
          }
          return undefined
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://127.0.0.1:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
}))
