import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/render'
import { Templates } from './Templates'

const { templates, factory, createMock, copyMock } = vi.hoisted(() => ({
  templates: [] as Array<Record<string, unknown>>,
  factory: [] as Array<Record<string, unknown>>,
  createMock: vi.fn(async (input: unknown) => ({ id: 't-1', ...(input as object), factoryKey: null, status: 'ACTIVE' })),
  copyMock: vi.fn(async (_key: string, input: unknown) => ({ id: 't-2', ...(input as object), documentType: 'CUT_NOTICE', channel: 'PDF', factoryKey: 'cut-notice', status: 'ACTIVE' })),
}))

vi.mock('@services/templates', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/templates')>()
  return {
    ...actual,
    listTemplates: () => Promise.resolve(templates),
    listFactory: () => Promise.resolve(factory),
    createTemplate: createMock,
    copyFactory: copyMock,
    listVersions: () => Promise.resolve([]),
    listGenerated: () => Promise.resolve([]),
  }
})

vi.mock('@hooks/usePermissions', () => ({
  hasPermission: () => true,
  useCan: () => true,
}))

describe('Modelos (P2.30)', () => {
  beforeEach(() => {
    templates.length = 0
    factory.length = 0
    createMock.mockClear()
    copyMock.mockClear()
    templates.push({ id: 't-0', code: 'aviso-corte', title: 'Aviso de corte', documentType: 'CUT_NOTICE', channel: 'PDF', factoryKey: null, status: 'ACTIVE' })
    factory.push({ key: 'cut-notice', documentType: 'CUT_NOTICE', channel: 'PDF', title: 'Aviso de corte', body: 'AVISO', variables: [] })
  })

  it('lista os modelos do tenant e explica o fluxo de aprovação', async () => {
    renderWithProviders(<Templates />)
    expect(await screen.findByText('aviso-corte', {}, { timeout: 10000 })).toBeInTheDocument()
    expect(screen.getByText(/variável fora do catálogo do tipo ou de tipo incompatível recusa a versão/)).toBeInTheDocument()
  })

  it('cria um modelo com tipo e canal', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithProviders(<Templates />)
    await screen.findByText('aviso-corte', {}, { timeout: 10000 })
    await user.type(screen.getByLabelText('Código'), 'carta-cobranca')
    await user.type(screen.getByLabelText('Título'), 'Carta de cobrança')
    await user.click(screen.getByRole('button', { name: 'Criar modelo' }))
    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({
      code: 'carta-cobranca', title: 'Carta de cobrança', documentType: 'CUT_NOTICE', channel: 'PDF',
    }))
  }, 30000)

  it('copia um modelo de fábrica sem editar a fábrica', async () => {
    const user = userEvent.setup({ delay: null })
    renderWithProviders(<Templates />)
    await screen.findByText('aviso-corte', {}, { timeout: 10000 })
    await user.type(screen.getByLabelText('Código da cópia'), 'aviso-copiado')
    await user.type(screen.getByLabelText('Título da cópia'), 'Aviso copiado')
    await user.click(screen.getByRole('button', { name: 'Copiar fábrica' }))
    expect(copyMock).toHaveBeenCalledWith('cut-notice', expect.objectContaining({
      code: 'aviso-copiado', title: 'Aviso copiado',
    }))
  }, 30000)
})
