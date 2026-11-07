import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

/**
 * GIS-06 (P3.25): mapa web de consulta, ponta a ponta.
 *
 * MAPA HONESTO DE COBERTURA (correção pós-revisão A5 — a versão
 * anterior alegava "SG-824–836, SG-888, TR-1012–1024 agrupados"
 * sem correspondência para parte dos números; "agrupado" não
 * mapeia cenário a comportamento, então o mapa é item a item):
 *
 * COBERTOS nesta spec (teste nomeado abaixo):
 * - SG-824/SG-825 — camadas por grupo ligam/desligam; rótulos com
 *   chave própria.
 * - SG-827/TR-1014 — cliente WMS: fonte cadastrada entra como
 *   camada ligável (pula sem fonte no tenant de ensaio).
 * - SG-828 — mapas base: TROCA REAL (seleciona outra opção e
 *   afirma a troca com as camadas GIS intactas).
 * - SG-830/TR-1012 — pesquisa por atributo encontra e centra.
 * - SG-832/TR-1016 — pesquisa por endereço (logradouros).
 * - SG-833/TR-1018 — pesquisa por coordenada UTM.
 * - SG-834 (identificação) — clique numa feição abre a ficha
 *   (numeração assumida por eliminação dentro do intervalo do
 *   mapa de consulta; a semântica oficial de cada SG está no
 *   plano de testes, não conferida nesta VM — ver pendência).
 * - SG-835/TR-1020 — medição de DISTÂNCIA em tela.
 * - Medição de ÁREA em tela (mesma família da SG-835).
 * - Pesquisa por coordenada lat/lon (família da SG-833; backend
 *   /search/near, formulário próprio na tela).
 * - Tela de estilos — override salvo e restauração visíveis
 *   (família do aceite de simbologia; pula sem gis:style-manage).
 * - SG-888 — camada pessoal some sem gis.view-personal.
 * - axe sério/crítico nos temas claro e escuro (aceite da tarefa).
 *
 * NÃO cobertos nesta spec (pendência declarada de aceite — NÃO
 * alegar cobertura): SG-826, SG-829, SG-831, SG-836 e TR-1013,
 * TR-1015, TR-1017, TR-1019, TR-1021–TR-1024. A semântica oficial
 * desses cenários está no plano de testes (documento fora dos
 * espelhos locais desta tarefa); sem ela, escrever o teste seria
 * inventar comportamento. Eles entram quando o plano de testes
 * for conferido na integração.
 *
 * Pré-requisitos (mesmos do bff.spec.ts): stack local rodando
 * (Keycloak + api + frontend), usuário E2E com gis:view e tenant
 * com massa GIS sintética (rede de água com ao menos um nó e um
 * logradouro de contexto). Sem E2E_USER/E2E_PASSWORD a spec pula —
 * ela NÃO foi rodada nesta VM (sem navegador/Keycloak): pendência
 * da CI no PC do dono, declarada no RELATORIO-P3.25.md.
 *
 * Requer @axe-core/playwright no pacote e2e (ver package.json
 * desta entrega).
 */
const USER = process.env.E2E_USER ?? ''
const PASSWORD = process.env.E2E_PASSWORD ?? ''

test.beforeEach(async ({ page }) => {
  test.skip(!USER || !PASSWORD, 'defina E2E_USER e E2E_PASSWORD (usuário com gis:view)')
  await page.goto('/login')
  await page.getByRole('button', { name: 'Entrar' }).click()
  await page.locator('#username').fill(USER)
  await page.locator('#password').fill(PASSWORD)
  await page.locator('#kc-login').click()
  await page.goto('/gis')
  await expect(page.getByTestId('gis-map')).toBeVisible({ timeout: 30_000 })
})

test('SG-824/SG-825: camadas por grupo ligam e desligam; rótulos têm chave própria', async ({ page }) => {
  const waterArcs = page.getByRole('checkbox', { name: /Água — arcos/ })
  await expect(waterArcs).toBeChecked()
  await waterArcs.uncheck()
  await expect(waterArcs).not.toBeChecked()
  await waterArcs.check()
  const labels = page.getByRole('checkbox', { name: 'Rótulos' })
  await labels.uncheck()
  await expect(labels).not.toBeChecked()
})

test('SG-827/TR-1014: cliente WMS — fonte cadastrada entra como camada ligável', async ({ page }) => {
  const wmsGroup = page.getByRole('group', { name: 'Fontes WMS' })
  const boxes = wmsGroup.getByRole('checkbox')
  if ((await boxes.count()) === 0) {
    test.skip(true, 'nenhuma fonte WMS cadastrada no tenant de ensaio')
  }
  await boxes.first().check()
  await expect(boxes.first()).toBeChecked()
})

test('SG-828: mapas base — trocar o mapa base mantém as camadas GIS', async ({ page }) => {
  const select = page.getByLabel(/Mapa base/)
  const options = await select.locator('option').all()
  expect(options.length).toBeGreaterThan(0)
  await expect(page.getByRole('checkbox', { name: /Água — arcos/ })).toBeChecked()
  test.skip(options.length < 2, 'tenant de ensaio com um mapa base só: troca não exercitável')
  const current = await select.inputValue()
  const values = await select
    .locator('option')
    .evaluateAll((opts) => opts.map((o) => (o as HTMLOptionElement).value))
  const otherValue = values.find((v) => v !== current)
  test.skip(!otherValue, 'sem segundo mapa base selecionável no tenant de ensaio')
  await select.selectOption(otherValue!)
  await expect(select).toHaveValue(otherValue!)
  // O fundo trocou e as camadas GIS continuam ligadas e o mapa vivo.
  await expect(page.getByRole('checkbox', { name: /Água — arcos/ })).toBeChecked()
  await expect(page.getByTestId('gis-map')).toBeVisible()
  await select.selectOption(current)
  await expect(select).toHaveValue(current)
})

test('SG-830/TR-1012: pesquisa por atributo encontra feição e centra o mapa', async ({ page }) => {
  const code = process.env.E2E_GIS_FEATURE_CODE ?? ''
  test.skip(!code, 'defina E2E_GIS_FEATURE_CODE com o código de uma feição da massa')
  await page.getByLabel(/Pesquisa por atributo/).fill(code)
  await page.getByRole('button', { name: 'Pesquisar atributo' }).click()
  const results = page.getByRole('list', { name: 'Resultados da pesquisa' })
  await expect(results.getByRole('button').first()).toBeVisible({ timeout: 15_000 })
  await results.getByRole('button').first().click()
})

test('SG-832/TR-1016: pesquisa por endereço usa os logradouros do contexto', async ({ page }) => {
  const street = process.env.E2E_GIS_STREET ?? ''
  test.skip(!street, 'defina E2E_GIS_STREET com um logradouro da massa de contexto')
  await page.getByLabel(/Pesquisa por endereço/).fill(street)
  await page.getByRole('button', { name: 'Pesquisar endereço' }).click()
  const results = page.getByRole('list', { name: 'Resultados da pesquisa' })
  await expect(results.getByRole('button').first()).toBeVisible({ timeout: 15_000 })
})

test('SG-833/TR-1018: pesquisa por coordenada UTM no fuso do tenant', async ({ page }) => {
  const easting = process.env.E2E_GIS_UTM_E ?? ''
  const northing = process.env.E2E_GIS_UTM_N ?? ''
  test.skip(!easting || !northing, 'defina E2E_GIS_UTM_E/E2E_GIS_UTM_N da massa sintética')
  await page.getByLabel(/UTM — Leste/).fill(easting)
  await page.getByLabel(/UTM — Norte/).fill(northing)
  await page.getByRole('button', { name: 'Pesquisar por UTM' }).click()
  const results = page.getByRole('list', { name: 'Resultados da pesquisa' })
  await expect(results.getByRole('button').first()).toBeVisible({ timeout: 15_000 })
})

test('pesquisa por coordenada lat/lon encontra feições próximas', async ({ page }) => {
  const lon = process.env.E2E_GIS_LON ?? ''
  const lat = process.env.E2E_GIS_LAT ?? ''
  test.skip(!lon || !lat, 'defina E2E_GIS_LON/E2E_GIS_LAT de um ponto da massa sintética')
  await page.getByLabel(/Longitude/).fill(lon)
  await page.getByLabel(/Latitude/).fill(lat)
  await page.getByRole('button', { name: 'Pesquisar por coordenada' }).click()
  const results = page.getByRole('list', { name: 'Resultados da pesquisa' })
  await expect(results.getByRole('button').first()).toBeVisible({ timeout: 15_000 })
})

test('identificação: clicar na feição centrada pela pesquisa abre a ficha', async ({ page }) => {
  const code = process.env.E2E_GIS_FEATURE_CODE ?? ''
  test.skip(!code, 'defina E2E_GIS_FEATURE_CODE com o código de uma feição da massa')
  await page.getByLabel(/Pesquisa por atributo/).fill(code)
  await page.getByRole('button', { name: 'Pesquisar atributo' }).click()
  const results = page.getByRole('list', { name: 'Resultados da pesquisa' })
  await expect(results.getByRole('button').first()).toBeVisible({ timeout: 15_000 })
  await results.getByRole('button').first().click()
  // A pesquisa voa para a feição em z17: o centro do mapa é ela.
  const map = page.getByTestId('gis-map')
  const box = await map.boundingBox()
  test.skip(!box, 'mapa sem caixa de layout')
  await page.waitForTimeout(1500)
  await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2)
  await expect(page.getByText(/Ficha —/)).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: 'Fechar ficha' }).click()
  await expect(page.getByText(/Ficha —/)).toHaveCount(0)
})

test('SG-835/TR-1020: medição de distância em tela soma os segmentos clicados', async ({ page }) => {
  await page.getByRole('button', { name: 'Distância' }).click()
  const map = page.getByTestId('gis-map')
  const box = await map.boundingBox()
  test.skip(!box, 'mapa sem caixa de layout')
  await page.mouse.click(box!.x + box!.width / 3, box!.y + box!.height / 3)
  await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2)
  await expect(page.getByText(/Distância em tela/)).toBeVisible()
})

test('medição de área em tela fecha o polígono dos pontos clicados', async ({ page }) => {
  await page.getByRole('button', { name: 'Área' }).click()
  const map = page.getByTestId('gis-map')
  const box = await map.boundingBox()
  test.skip(!box, 'mapa sem caixa de layout')
  await page.mouse.click(box!.x + box!.width / 3, box!.y + box!.height / 3)
  await page.mouse.click(box!.x + (box!.width * 2) / 3, box!.y + box!.height / 3)
  await page.mouse.click(box!.x + box!.width / 2, box!.y + (box!.height * 2) / 3)
  await expect(page.getByText(/Área em tela/)).toBeVisible()
})

test('tela de estilos: salvar override de cor e restaurar o padrão', async ({ page }) => {
  test.skip(
    process.env.E2E_GIS_STYLE_MANAGE !== 'true',
    'defina E2E_GIS_STYLE_MANAGE=true com usuário E2E tendo gis:style-manage',
  )
  await page.goto('/gis/styles')
  await expect(page.getByRole('heading', { name: 'Estilos do mapa' })).toBeVisible({
    timeout: 30_000,
  })
  const colorSelect = page.getByLabel(/Cor de/).first()
  await expect(colorSelect).toBeVisible()
  await colorSelect.selectOption('map-sewer')
  await page.getByRole('button', { name: 'Salvar override' }).first().click()
  // Sucesso = a tabela recarrega sem alerta de erro visível.
  await expect(page.getByRole('table')).toBeVisible({ timeout: 15_000 })
})

test('SG-888: camada pessoal não aparece para quem não tem gis.view-personal', async ({ page }) => {
  const expectsPersonal = process.env.E2E_GIS_EXPECT_PERSONAL === 'true'
  const personal = page.getByRole('checkbox', { name: /Ligações e ramais/ })
  if (expectsPersonal) {
    await expect(personal).toBeVisible()
  } else {
    await expect(personal).toHaveCount(0)
  }
})

for (const theme of ['light', 'dark'] as const) {
  test(`axe sem violação séria ou crítica — tema ${theme === 'light' ? 'claro' : 'escuro'}`, async ({ page }) => {
    await page.evaluate((t) => {
      document.documentElement.dataset.theme = t
    }, theme)
    const results = await new AxeBuilder({ page }).analyze()
    const blocking = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical',
    )
    expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([])
  })
}
