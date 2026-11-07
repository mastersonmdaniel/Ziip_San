import { expect, test } from '@playwright/test'

/**
 * ED-08: compatibilidade de navegadores demonstrável — a evidência da alínea a
 * do roteiro VISAN (POC-01 a POC-03). Diferente dos specs do BFF e do banner de
 * versão, este spec NÃO exige login nem usuário: ele prova, em cada projeto da
 * matriz (edge-88, chrome-100, firefox-116 e o chromium de referência), que a
 * aplicação CARREGA no motor daquele navegador, sem erro de script, e que as
 * APIs do PISO de linguagem dos alvos existem. É o spec que o job
 * `e2e-navegadores` do CI executa sobre o build servido (vite preview).
 *
 * Pré-requisito: só o frontend servido em E2E_BASE_URL (padrão
 * http://localhost:3000). A tela de entrada é pública: o botão "Entrar" aparece
 * sem sessão (o login em si, contra o Keycloak, é provado pelos outros specs).
 *
 * O que cada teste prova:
 * - a casca da aplicação renderiza a tela de entrada naquele motor, sem
 *   `pageerror` (erro de script não tratado é o sintoma clássico de recurso de
 *   linguagem acima do piso do navegador);
 * - o motor executado é o da família do projeto (user-agent) e a versão REAL
 *   do build usado fica anexada ao relatório — o alvo do edital é o nome do
 *   projeto; o build exato varia conforme o canal provisionado (ver
 *   playwright.config.ts e a nota técnica em docs/editais/);
 * - o piso de APIs que o pacote usa e que já existia nos três alvos
 *   (fetch, Promise.allSettled, String.replaceAll, IntersectionObserver,
 *   ResizeObserver, CSS grid e propriedades customizadas) está presente.
 */

test('a tela de entrada carrega sem erro de script e o motor é o do projeto', async ({ page }, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(String(error)))

  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible({ timeout: 30_000 })

  const ua = await page.evaluate(() => navigator.userAgent)
  await testInfo.attach('user-agent.txt', { body: ua, contentType: 'text/plain' })
  const version = page.context().browser()?.version() ?? ''
  await testInfo.attach('browser-version.txt', { body: version, contentType: 'text/plain' })

  // Família do motor por projeto. O Edge também traz "Chrome/" no user-agent:
  // por isso ele é conferido primeiro, pelo token próprio "Edg/".
  if (testInfo.project.name === 'edge-88') {
    expect(ua).toContain('Edg/')
  } else if (testInfo.project.name === 'firefox-116') {
    expect(ua).toContain('Firefox/')
  } else {
    expect(ua).toContain('Chrome/')
  }

  expect(errors).toEqual([])
})

test('as APIs do piso de linguagem dos alvos (Edge 88, Chrome 100, Firefox 116) existem', async ({ page }) => {
  await page.goto('/login')
  const floor = await page.evaluate(() => ({
    fetch: typeof fetch === 'function',
    allSettled: typeof Promise.allSettled === 'function',
    replaceAll: typeof String.prototype.replaceAll === 'function',
    intersectionObserver: typeof IntersectionObserver === 'function',
    resizeObserver: typeof ResizeObserver === 'function',
    grid: CSS.supports('display', 'grid'),
    customProperties: CSS.supports('--ziipsan-probe', '1'),
  }))
  expect(floor).toEqual({
    fetch: true,
    allSettled: true,
    replaceAll: true,
    intersectionObserver: true,
    resizeObserver: true,
    grid: true,
    customProperties: true,
  })
})
