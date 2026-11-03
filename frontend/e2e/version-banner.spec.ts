import { expect, test } from '@playwright/test'

/**
 * P0.39, ponta a ponta contra Keycloak + api + frontend de verdade.
 *
 * Pré-requisitos: os mesmos do README.md aqui (usuário E2E_USER/E2E_PASSWORD com
 * tenant_id e papel VIEWER; stack em http://localhost:3000).
 *
 * NÃO RODADO nesta VM (sem Keycloak nem navegador instalados; o Playwright precisaria
 * baixar o Chromium — ver o relatório da frente P0.8+P0.39+P0.40+P0.41): o spec existe e
 * está pronto, mas a prova E2E do aviso de recarga fica PENDENTE até rodar contra a
 * stack real. O que já é provado sem ele: cabeçalhos em toda resposta (teste de
 * integração do backend), comparação de versão e banner (vitest).
 *
 * O que cada teste prova:
 * - respostas reais da API trazem Ziip-Version e Ziip-Min-Client-Version;
 * - SPA "antigo" (resposta interceptada com Ziip-Min-Client-Version acima da versão do
 *   build) mostra o aviso de recarga.
 */
const USER = process.env.E2E_USER ?? ''
const PASSWORD = process.env.E2E_PASSWORD ?? ''

test.beforeEach(async ({ page }) => {
  test.skip(!USER || !PASSWORD, 'defina E2E_USER e E2E_PASSWORD com um usuário do realm ziipsan')
  await page.goto('/login')
  await page.getByRole('button', { name: 'Entrar' }).click()
  await page.locator('#username').fill(USER)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login').click()
  await expect(page).toHaveURL(/\/cadastro\//, { timeout: 30_000 })
})

test('respostas da API trazem Ziip-Version e Ziip-Min-Client-Version', async ({ page }) => {
  const response = await page.evaluate(async () => {
    const r = await fetch('/api/v1/me', { credentials: 'include' })
    return { status: r.status, version: r.headers.get('Ziip-Version'), min: r.headers.get('Ziip-Min-Client-Version') }
  })
  expect(response.status).toBe(200)
  expect(response.version).toBeTruthy()
  expect(response.min).toBeTruthy()
})

test('SPA antigo mostra aviso de recarga quando o servidor exige versão maior', async ({ page }) => {
  // Intercepta a próxima chamada de negócio e devolve os mesmos dados/status, só com
  // um Ziip-Min-Client-Version inalcançável — simulando um deploy mais novo no servidor
  // do que o bundle servido ao navegador.
  await page.route('**/api/v1/**', async (route) => {
    const original = await route.fetch()
    const headers = { ...original.headers(), 'ziip-min-client-version': '99.0.0', 'ziip-version': '99.0.0' }
    await route.fulfill({ response: original, headers })
  })
  await page.goto('/cadastro/imoveis')
  await expect(page.getByText('Há uma versão nova do Ziip SAN')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('button', { name: 'Recarregar agora' })).toBeVisible()
})
