import { expect, test, type Page } from '@playwright/test'

/**
 * BFF (P0.14), ponta a ponta contra Keycloak + api + frontend de verdade.
 *
 * Pré-requisitos (ver README.md): usuário E2E_USER/E2E_PASSWORD já criado no realm
 * ziipsan com tenant_id e papel VIEWER; perfil local (cookie `ziipsan-session`,
 * sem Secure — sem TLS local); stack em http://localhost:3000 (proxy do Vite para
 * a api em 8090). NÃO RODADO nesta VM no turno 9 (sem Keycloak nem navegador
 * instalados aqui): o spec existe, o CI não o executa, e a P0.14 NÃO fecha como
 * concluída sem ele (ver docs/seguranca/BFF.md).
 *
 * O que cada teste prova:
 * - nenhum token no navegador (sessionStorage/localStorage sem token/oidc);
 * - cookie de sessão HttpOnly + SameSite=Lax (+ Secure fora do perfil local);
 * - POST sem CSRF -> 403;
 * - logout invalida a sessão (o cookie velho passa a dar 401);
 * - refresh antigo reutilizado -> rejeitado (via sessão: após o logout, o refresh
 *   token guardado no servidor já não vale; a prova direta do token exigiria
 *   acesso ao servidor, por isso o spec prova pela sessão).
 */
const USER = process.env.E2E_USER ?? ''
const PASSWORD = process.env.E2E_PASSWORD ?? ''
const SESSION_COOKIE = process.env.E2E_SESSION_COOKIE ?? 'ziipsan-session'
const EXPECT_SECURE = process.env.E2E_EXPECT_SECURE === 'true'

test.beforeEach(async ({ page }) => {
  test.skip(!USER || !PASSWORD, 'defina E2E_USER e E2E_PASSWORD com um usuário do realm ziipsan')
  await page.goto('/login')
  await page.getByRole('button', { name: 'Entrar' }).click()
  // Formulário do Keycloak (tema padrão).
  await page.locator('#username').fill(USER)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login').click()
  // Volta para o app (qualquer tela de cadastro), nunca mais na tela de entrada.
  await expect(page).toHaveURL(/\/cadastro\//, { timeout: 30_000 })
})

async function storageHasToken(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const keys = [...Object.keys(sessionStorage), ...Object.keys(localStorage)]
    return keys.some((k) => /token|oidc|access|refresh/i.test(k))
      || [...Object.values(sessionStorage), ...Object.values(localStorage)]
        .some((v) => /eyJ[A-Za-z0-9_-]+\.eyJ/.test(v ?? ''))
  })
}

test('nenhum token no navegador e cookie de sessão HttpOnly + SameSite', async ({ page, context }) => {
  expect(await storageHasToken(page)).toBe(false)
  const cookies = await context.cookies()
  const session = cookies.find((c) => c.name === SESSION_COOKIE)
  expect(session).toBeDefined()
  expect(session!.httpOnly).toBe(true)
  expect(session!.sameSite).toBe('Lax')
  expect(session!.secure).toBe(EXPECT_SECURE)
})

test('POST sem CSRF -> 403', async ({ page }) => {
  const status = await page.evaluate(async () => {
    const r = await fetch('/api/v1/registry/customers/search', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    })
    return r.status
  })
  expect(status).toBe(403)
})

test('logout invalida a sessão: o cookie velho passa a dar 401', async ({ page, context }) => {
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible({ timeout: 30_000 })
  const status = await page.evaluate(async () => {
    const r = await fetch('/api/v1/me', { credentials: 'include' })
    return r.status
  })
  expect(status).toBe(401)
  const names = (await context.cookies()).map((c) => c.name)
  expect(names).not.toContain(SESSION_COOKIE)
})

test.skip('duas réplicas sem afinidade: sessão sobrevive à morte de uma réplica', async () => {
  // Procedimento (fora do Playwright, exige orquestração externa):
  // 1. subir DUAS apis (8090 e 8091) na mesma base PG, atrás do mesmo proxy;
  // 2. logar (o teste acima) e carregar /api/v1/me pela réplica A;
  // 3. matar a réplica A no meio da sessão;
  // 4. /api/v1/me pela réplica B deve continuar 200, sem novo login.
  // Automatizar isto aqui exigiria controlar os processos a partir do spec; até
  // lá, o ensaio é manual e a prova de persistência em PG está no
  // BffSessionRepositoryTest (backend). Este teste fica skip até haver o harness
  // de duas réplicas (item em aberto da P0.14).
})
