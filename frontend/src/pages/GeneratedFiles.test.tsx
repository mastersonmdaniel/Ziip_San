import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/render'
import { GeneratedFiles } from './GeneratedFiles'

const { files, downloadMock } = vi.hoisted(() => ({
  files: [] as Array<Record<string, unknown>>,
  downloadMock: vi.fn(async () => true),
}))

vi.mock('@services/export', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/export')>()
  return { ...actual, listGeneratedFiles: () => Promise.resolve(files), downloadGeneratedFile: downloadMock }
})

describe('Arquivos gerados (P1.12)', () => {
  beforeEach(() => {
    files.length = 0
    downloadMock.mockClear()
  })

  it('lista os arquivos; só o pronto tem download', async () => {
    const user = userEvent.setup()
    files.push(
      { id: 'f-1', fileName: 'clientes-2026-11-03.csv', resource: 'customers', status: 'READY', rowCount: 42, createdAt: '2026-11-03T10:00:00', readyAt: '2026-11-03T10:01:00' },
      { id: 'f-2', fileName: 'imoveis-2026-11-03.csv', resource: 'consumer-units', status: 'QUEUED', rowCount: null, createdAt: '2026-11-03T10:00:00', readyAt: null },
    )
    renderWithProviders(<GeneratedFiles />)
    expect(await screen.findByText('clientes-2026-11-03.csv')).toBeInTheDocument()
    expect(screen.getByText('imoveis-2026-11-03.csv')).toBeInTheDocument()
    expect(screen.getByText('Pronto')).toBeInTheDocument()
    expect(screen.getByText('Na fila')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Baixar' }))
    expect(downloadMock).toHaveBeenCalledWith('f-1', 'clientes-2026-11-03.csv')
  })

  it('sem arquivos, explica que as exportações grandes aparecem ali', async () => {
    renderWithProviders(<GeneratedFiles />)
    expect(await screen.findByText(/Nenhum arquivo gerado ainda/)).toBeInTheDocument()
  })
})
