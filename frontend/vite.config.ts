/// <reference types="vitest" />
import { execSync } from 'child_process'
import fs from 'fs'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'
import { brandPlugin } from './vite-plugins/brand'
import { resolveBuildInfo } from './src/services/versionInfo'

// P0.39: informação de build a partir da tag git (mesma regra de resolveBuildInfo, usada
// pelo app). Data = data DO COMMIT, não o relógio: dois builds do mesmo commit geram o
// mesmo version.json. Tudo guardado: fora de um checkout git, cai para
// 0.0.0-SNAPSHOT/unknown sem quebrar o build (o CI sempre roda num checkout).
// O formato da data é o MESMO do backend (git-commit-id com America/Sao_Paulo): o
// timestamp unix do commit formatado em -03:00, para os dois version.json carregarem
// literalmente a mesma string (o %cI do git usaria o fuso gravado no commit).
function saoPauloIso(unixSeconds: string | undefined): string | undefined {
  if (!unixSeconds) return undefined
  const ms = Number(unixSeconds) * 1000
  if (!Number.isFinite(ms)) return undefined
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(ms))
  const get = (type: string): string => parts.find((p) => p.type === type)?.value ?? '00'
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}:${get('second')}-03:00`
}

function gitInfo(): { tagVersion?: string; commit?: string; date?: string } {
  const run = (cmd: string): string | undefined => {
    try {
      return execSync(cmd, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || undefined
    } catch {
      return undefined
    }
  }
  const tag = run('git describe --tags --exact-match HEAD')
  return {
    tagVersion: tag ? tag.replace(/^v/, '') : undefined,
    commit: run('git rev-parse --short HEAD'),
    date: saoPauloIso(run('git log -1 --format=%ct')),
  }
}

export default defineConfig(({ mode }) => {
  // ZIIPSAN_API_URL vem do ambiente ou de .env.local, sem prefixo VITE_: só o servidor de dev a lê,
  // ela nunca vai para o pacote do navegador. Padrão: backend em 8090 (a 8080 é do Keycloak).
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.ZIIPSAN_API_URL || 'http://localhost:8090'
  // ZIIPSAN_VERSION/COMMIT/BUILD_DATE do ambiente vencem a tag (release via CI); sem eles,
  // vale a tag git. A versão NUNCA vem do package.json (sentinela "0.0.0").
  const buildInfo = resolveBuildInfo(
    {
      ZIIPSAN_VERSION: process.env.ZIIPSAN_VERSION ?? env.ZIIPSAN_VERSION,
      ZIIPSAN_COMMIT: process.env.ZIIPSAN_COMMIT,
      ZIIPSAN_BUILD_DATE: process.env.ZIIPSAN_BUILD_DATE,
    },
    gitInfo(),
  )

  return {
    define: {
      __ZIIPSAN_VERSION__: JSON.stringify(buildInfo.version),
      __ZIIPSAN_COMMIT__: JSON.stringify(buildInfo.commit),
      __ZIIPSAN_BUILD_DATE__: JSON.stringify(buildInfo.buildDate),
    },
    plugins: [
      react(),
      brandPlugin(),
      // P0.39: version.json GERADO no dist (mesma versão/commit/data do backend), para o
      // deploy e o Sobre conferirem a versão servida sem abrir o bundle.
      {
        name: 'ziipsan-version-json',
        writeBundle(options) {
          if (!options.dir) return
          fs.writeFileSync(
            path.join(options.dir, 'version.json'),
            `${JSON.stringify(buildInfo, null, 2)}\n`,
            'utf-8',
          )
        },
      },
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
        '@components': path.resolve(__dirname, './src/components'),
        '@pages': path.resolve(__dirname, './src/pages'),
        '@hooks': path.resolve(__dirname, './src/hooks'),
        '@i18n': path.resolve(__dirname, './src/i18n'),
        '@services': path.resolve(__dirname, './src/services'),
        '@theme': path.resolve(__dirname, './src/theme'),
        '@utils': path.resolve(__dirname, './src/utils'),
        '@assets': path.resolve(__dirname, './src/assets'),
      },
    },
    server: {
      // A porta 3000 é a registrada no client ziipsan-web do Keycloak (redirect URI e web origin).
      port: 3000,
      strictPort: true,
      // P1.12: o teste de contrato das abas da ficha (unitSheetTabs.test.ts) lê o
      // manifesto do produto em packages/sanitation/vertical.json, fora da raiz do
      // frontend; sem isto o Vite nega o import ("Denied ID"). Só afeta o
      // servidor local de dev/teste, nunca o build.
      fs: {
        allow: [path.resolve(__dirname, '..')],
      },
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
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      include: ['src/**/*.test.{ts,tsx}'],
      // P0.17: no Vitest 4 os testes com user-event ficam ~1,7x mais lentos que no
      // Vitest 1 (PropertyWizard: ~3,1 s/teste no v1, ~5,2 s/teste no v4, mesmas versões
      // de jsdom/testing-library/user-event), e o default de 5 s estourou. 10 s dá folga
      // sem mascarar travamento (que continua falhando no teto).
      testTimeout: 10000,
    },
  }
})
