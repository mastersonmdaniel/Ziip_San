import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PermissionsProvider } from '@hooks/usePermissions'
import { GisWebEditor } from './GisWebEditor'

/**
 * R41-4-35 — "Editor GIS" exibido a quem só tem `gis:view`.
 *
 * Prova com permissões mockadas (PermissionsProvider real, lista de
 * permissões injectada; serviços GIS mockados; mapa real fora — o
 * gisMap.style rejeita e a tela cai na superfície local, como declara o
 * cabeçalho do editor):
 * - com só `gis:view`, o editor ABRE (a rota é de leitura — legítimo),
 *   mas tem de abrir SÓ DE CONSULTA: aviso visível, ferramentas de
 *   desenho desactivadas, desenho sem efeito e gravação desactivada.
 * - com `gis:import-edit` + `gis:change-set-edit`, tudo fica activo e
 *   a gravação chama o servidor (controlo negativo: a guarda é da
 *   permissão, não um bloqueio geral).
 * - mesmo com escrita, se o servidor negar (403), a tela mostra a
 *   recusa (controlo do segundo degrau: o servidor continua a ser a
 *   autoridade — GisChangeSetController exige `gis:change-set-edit`).
 *
 * As authorities usadas existem no backend: `gis:import-edit` é a
 * escrita GIS vigente (GisWebEditRoles.EDIT) e `gis:change-set-edit`
 * é a da criação de pacotes (GisChangeSetRoles.EDIT). Nenhuma
 * permissão nova é inventada aqui.
 */
const webEditMocks = vi.hoisted(() => ({
  myLayerPermissions: vi.fn(),
  transform: vi.fn(),
  formFromSchema: vi.fn(),
}))
const mapMocks = vi.hoisted(() => ({
  style: vi.fn(),
}))
const changeSetsMocks = vi.hoisted(() => ({
  create: vi.fn(),
  addItem: vi.fn(),
  submit: vi.fn(),
}))

vi.mock('../../services/gis/webEdit', () => ({
  webEdit: {
    myLayerPermissions: webEditMocks.myLayerPermissions,
    transform: webEditMocks.transform,
    formFromSchema: webEditMocks.formFromSchema,
  },
}))
vi.mock('../../services/gis/map', () => ({
  gisMap: { style: mapMocks.style },
}))
vi.mock('../../services/gis/changeSets', async (importOriginal) => {
  const orig = await importOriginal<typeof import('../../services/gis/changeSets')>()
  return {
    ...orig,
    changeSets: {
      ...orig.changeSets,
      create: changeSetsMocks.create,
      addItem: changeSetsMocks.addItem,
      submit: changeSetsMocks.submit,
    },
  }
})
vi.mock('maplibre-gl', () => ({
  Map: vi.fn(),
  NavigationControl: vi.fn(),
}))

const TOOL_LABELS = [
  'Selecionar',
  'Ponto',
  'Linha',
  'Poligono',
  'Mover',
  'Rotacionar',
  'Escalar',
  'Dividir arco',
  'Unir arcos',
  'Inserir no',
]

function renderEditor(permissions: string[]) {
  return render(
    <PermissionsProvider permissions={permissions}>
      <GisWebEditor />
    </PermissionsProvider>,
  )
}

describe('GisWebEditor — só de leitura (R41-4-35)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    webEditMocks.myLayerPermissions.mockResolvedValue([])
    mapMocks.style.mockRejectedValue(new Error('estilo indisponível'))
  })

  it('com só gis:view, o editor abre em modo só de leitura', async () => {
    renderEditor(['gis:view'])
    // A rota é de leitura: o editor abre (superfície local presente).
    expect(screen.getByRole('heading', { name: 'Editor GIS' })).toBeInTheDocument()
    expect(screen.getByTestId('gis-editor-canvas')).toBeInTheDocument()
    // ... mas como só de consulta: aviso visível.
    expect(screen.getByTestId('gis-editor-readonly-notice')).toBeInTheDocument()
    // Ferramentas de desenho desactivadas.
    for (const label of TOOL_LABELS) {
      expect(screen.getByRole('button', { name: label })).toBeDisabled()
    }
    // "Conferir na API" e gravar em pacote desactivados.
    expect(screen.getByRole('button', { name: /Conferir na API/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Gravar em pacote/ })).toBeDisabled()
    // Nada foi chamado ao servidor para gravar.
    expect(changeSetsMocks.create).not.toHaveBeenCalled()
    // Sem permissões de camada, o estado declara-o.
    await waitFor(() =>
      expect(screen.getByText(/Sem permissoes de camada/)).toBeInTheDocument(),
    )
  })

  it('com só gis:view, arrastar um vértice não altera a feição de trabalho', async () => {
    renderEditor(['gis:view'])
    const work = screen.getByText(/Feicao de trabalho/)
    const before = work.textContent
    const vertex = screen.getByTestId('vertex-0')
    fireEvent.pointerDown(vertex)
    fireEvent.pointerMove(screen.getByTestId('gis-editor-canvas'), {
      clientX: 150,
      clientY: 150,
    })
    fireEvent.pointerUp(screen.getByTestId('gis-editor-canvas'))
    expect(screen.getByText(/Feicao de trabalho/).textContent).toBe(before)
  })

  it('com gis:import-edit + gis:change-set-edit, ferramentas e gravação ficam activas (controlo negativo)', async () => {
    const user = userEvent.setup()
    changeSetsMocks.create.mockResolvedValue({ id: 'cs-1', status: 'DRAFT' })
    changeSetsMocks.addItem.mockResolvedValue({ id: 'item-1' })
    changeSetsMocks.submit.mockResolvedValue({ id: 'cs-1', status: 'IN_REVIEW' })
    renderEditor(['gis:view', 'gis:import-edit', 'gis:change-set-edit'])
    expect(screen.queryByTestId('gis-editor-readonly-notice')).not.toBeInTheDocument()
    for (const label of TOOL_LABELS) {
      expect(screen.getByRole('button', { name: label })).not.toBeDisabled()
    }
    const save = screen.getByRole('button', { name: /Gravar em pacote/ })
    expect(save).not.toBeDisabled()
    await user.type(
      screen.getByPlaceholderText('uuid da feição editada'),
      '00000000-0000-0000-0000-000000000001',
    )
    await user.click(save)
    await waitFor(() => expect(changeSetsMocks.create).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByText(/submetido/)).toBeInTheDocument())
  })

  it('com escrita, se o servidor negar a criação (403), a tela mostra a recusa', async () => {
    const user = userEvent.setup()
    changeSetsMocks.create.mockRejectedValue(new Error('403 Forbidden'))
    renderEditor(['gis:view', 'gis:import-edit', 'gis:change-set-edit'])
    await user.type(
      screen.getByPlaceholderText('uuid da feição editada'),
      '00000000-0000-0000-0000-000000000001',
    )
    await user.click(screen.getByRole('button', { name: /Gravar em pacote/ }))
    await waitFor(() =>
      expect(screen.getByText(/Gravação recusada/)).toBeInTheDocument(),
    )
  })
})
