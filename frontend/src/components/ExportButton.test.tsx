import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ExportButton } from './ExportButton'

const { authValue, requestExportMock, downloadBlobMock } = vi.hoisted(() => ({
  authValue: { user: { permissions: ['registry:read', 'registry:export'] } as unknown },
  requestExportMock: vi.fn(),
  downloadBlobMock: vi.fn(() => true),
}))

vi.mock('@hooks/useAuth', () => ({
  useAuthOptional: () => authValue,
}))

vi.mock('@services/export', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@services/export')>()
  return { ...actual, requestExport: requestExportMock, downloadBlob: downloadBlobMock }
})

describe('ExportButton (P1.12)', () => {
  beforeEach(() => {
    requestExportMock.mockReset()
    downloadBlobMock.mockClear()
    authValue.user = { permissions: ['registry:read', 'registry:export'] }
  })

  it('sem registry:export, o botão nem aparece', () => {
    authValue.user = { permissions: ['registry:read'] }
    render(
      <MemoryRouter>
        <ExportButton resource="customers" filters={{ q: 'TESTE' }} />
      </MemoryRouter>,
    )
    expect(screen.queryByRole('button', { name: /Exportar/ })).not.toBeInTheDocument()
  })

  it('com a permissão, envia só os filtros e baixa o arquivo síncrono', async () => {
    const user = userEvent.setup()
    requestExportMock.mockResolvedValue({ kind: 'file', blob: new Blob(['a']), fileName: 'clientes.csv' })
    render(
      <MemoryRouter>
        <ExportButton resource="customers" filters={{ q: 'TESTE' }} />
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: 'Exportar CSV' }))
    expect(requestExportMock).toHaveBeenCalledWith('customers', { q: 'TESTE' })
    expect(downloadBlobMock).toHaveBeenCalled()
  })

  it('resposta 202 aponta para Arquivos gerados, sem baixar nada', async () => {
    const user = userEvent.setup()
    requestExportMock.mockResolvedValue({ kind: 'queued', generatedFileId: 'f-1' })
    render(
      <MemoryRouter>
        <ExportButton resource="customers" filters={{}} />
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: 'Exportar CSV' }))
    expect(await screen.findByRole('link', { name: 'Arquivos gerados' })).toHaveAttribute('href', '/arquivos-gerados')
    expect(downloadBlobMock).not.toHaveBeenCalled()
  })
})
