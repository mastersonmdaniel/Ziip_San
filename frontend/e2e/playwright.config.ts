import { defineConfig } from '@playwright/test'

/**
 * E2E do BFF (P0.14). NÃO sobe a stack sozinho: espera Keycloak + api + frontend já
 * rodando (ver README.md aqui). Um worker só, porque os testes dividem o mesmo
 * navegador/usuário e um login interfere no outro.
 */
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
})
