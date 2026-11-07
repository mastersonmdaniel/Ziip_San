import { defineConfig, devices } from '@playwright/test'

/**
 * E2E do BFF (P0.14). NÃO sobe a stack sozinho: espera Keycloak + api + frontend já
 * rodando (ver README.md aqui). Um worker só, porque os testes dividem o mesmo
 * navegador/usuário e um login interfere no outro.
 *
 * ED-08 — matriz de navegadores de edital: os projetos `edge-88`, `chrome-100` e
 * `firefox-116` são os ALVOS do edital (Edge 88, Chrome 100, Firefox 116). O
 * Playwright NÃO distribui binários históricos: ele baixa apenas os builds
 * atuais dos seus canais. Por isso cada projeto roda pelo CANAL do navegador
 * (Edge e Chrome instalados) ou pelo Firefox do próprio Playwright, e aceita um
 * executável fixo via variável de ambiente (`E2E_EDGE_EXECUTABLE`,
 * `E2E_CHROME_EXECUTABLE`, `E2E_FIREFOX_EXECUTABLE`) para quando um build
 * compatível com a automação da versão-alvo for provisionado na máquina/CI.
 * A versão REAL de cada execução fica registrada nos anexos do spec
 * `navegadores.spec.ts` (user-agent + versão do navegador) — a divergência
 * entre o alvo do edital e o build executado está declarada na nota técnica
 * em docs/editais/, não escondida. O projeto `chromium` preserva o
 * comportamento anterior desta suíte (build Chromium do Playwright).
 */
const edgeExecutable = process.env.E2E_EDGE_EXECUTABLE
const chromeExecutable = process.env.E2E_CHROME_EXECUTABLE
const firefoxExecutable = process.env.E2E_FIREFOX_EXECUTABLE

export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'edge-88',
      use: {
        ...devices['Desktop Edge'],
        ...(edgeExecutable
          ? { launchOptions: { executablePath: edgeExecutable } }
          : { channel: 'msedge' as const }),
      },
    },
    {
      name: 'chrome-100',
      use: {
        ...devices['Desktop Chrome'],
        ...(chromeExecutable
          ? { launchOptions: { executablePath: chromeExecutable } }
          : { channel: 'chrome' as const }),
      },
    },
    {
      name: 'firefox-116',
      use: {
        ...devices['Desktop Firefox'],
        ...(firefoxExecutable ? { launchOptions: { executablePath: firefoxExecutable } } : {}),
      },
    },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
