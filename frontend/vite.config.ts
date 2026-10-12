/// <reference types="vitest" />
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig(({ mode }) => {
  // SIGSAN_API_URL vem do ambiente ou de .env.local, sem prefixo VITE_: só o servidor de dev a lê,
  // ela nunca vai para o pacote do navegador. Padrão: backend em 8090 (a 8080 é do Keycloak).
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.SIGSAN_API_URL || 'http://localhost:8090'

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
        '@components': path.resolve(__dirname, './src/components'),
        '@pages': path.resolve(__dirname, './src/pages'),
        '@hooks': path.resolve(__dirname, './src/hooks'),
        '@services': path.resolve(__dirname, './src/services'),
        '@utils': path.resolve(__dirname, './src/utils'),
        '@assets': path.resolve(__dirname, './src/assets'),
      },
    },
    server: {
      // A porta 3000 é a registrada no client sigsan-web do Keycloak (redirect URI e web origin).
      port: 3000,
      strictPort: true,
      // O contrato do cadastro é /api/registry/... (relatório 07, seção 6.2): o prefixo /api
      // NÃO é removido.
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      // Sem sourcemap em produção: ele publicaria o código-fonte inteiro junto com o pacote.
      sourcemap: false,
      // O mapa (maplibre-gl, ~800 kB) vai num chunk próprio carregado sob demanda; o aviso é esperado.
      chunkSizeWarningLimit: 900,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'axios'],
            forms: ['react-hook-form', '@hookform/resolvers', 'zod'],
            auth: ['oidc-client-ts'],
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      include: ['src/**/*.test.{ts,tsx}'],
    },
  }
})
