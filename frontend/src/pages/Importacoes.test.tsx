import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/render'
import { Importacoes } from './Importacoes'

const { jobs, downloadMock } = vi.hoisted(() => ({
  jobs: [] as Array<Record<string, unknown>>,
  downloadMock: vi.fn(async () => true),
}))

vi.mock('@services/imports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/imports')>()
  return { ...actual, listImports: () => Promise.resolve(jobs), downloadRejectionsCsv: downloadMock }
})

describe('Importações (P1.40)', () => {
  beforeEach(() => {
    jobs.length = 0
    downloadMock.mockClear()
  })

  it('lista arquivo, esquema, totais e OS de lote; só carga com recusa tem download', async () => {
    const user = userEvent.setup()
    jobs.push(
      {
        id: 'j-1', schemaCode: 'registry.categories', schemaLabel: 'Categorias',
        originalFilename: 'categorias.csv', status: 'APPLIED', totalRows: 3,
        acceptedCount: 2, rejectedCount: 1, appliedCount: 2,
        batchOrderId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', reversalOrderId: null,
        errorMessage: null, createdAt: '2026-11-03T10:00:00', createdBy: '11111111-2222-3333-4444-555555555555',
        duplicate: false,
      },
      {
        id: 'j-2', schemaCode: 'registry.subcategories', schemaLabel: 'Subcategorias',
        originalFilename: 'limpo.csv', status: 'VALIDATED', totalRows: 1,
        acceptedCount: 1, rejectedCount: 0, appliedCount: 0,
        batchOrderId: null, reversalOrderId: null,
        errorMessage: null, createdAt: '2026-11-03T10:00:00', createdBy: null,
        duplicate: false,
      },
    )
    renderWithProviders(<Importacoes />)
    expect(await screen.findByText('categorias.csv')).toBeInTheDocument()
    expect(screen.getByText('Categorias')).toBeInTheDocument()
    expect(screen.getByText('Aplicada')).toBeInTheDocument()
    expect(screen.getByText('aaaaaaaa')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Baixar' }))
    expect(downloadMock).toHaveBeenCalledWith('j-1', 'recusas-j-1.csv')
  })

  it('sem cargas, explica que as cargas aparecem ali', async () => {
    renderWithProviders(<Importacoes />)
    expect(await screen.findByText(/Nenhuma importação ainda/)).toBeInTheDocument()
  })
})
