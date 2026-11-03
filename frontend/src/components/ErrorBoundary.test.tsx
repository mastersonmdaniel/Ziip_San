import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

function Thrower({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) throw new Error('quebrou de propósito no teste')
  return <p>Conteúdo saudável</p>
}

function Harness() {
  const [broken, setBroken] = useState(true)
  return (
    <MemoryRouter>
      <button onClick={() => setBroken(false)}>Consertar</button>
      <ErrorBoundary onError={vi.fn()}>
        <Thrower shouldThrow={broken} />
      </ErrorBoundary>
    </MemoryRouter>
  )
}

describe('ErrorBoundary (P1.12)', () => {
  it('mostra a mensagem e a nova tentativa em vez de derrubar a tela, e volta ao conteúdo após o conserto', async () => {
    const user = userEvent.setup()
    // O erro de renderização vai para o console do React; silencia só neste teste.
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    render(<Harness />)
    expect(screen.getByRole('alert')).toHaveTextContent('Algo deu errado nesta tela')
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Consertar' }))
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByText('Conteúdo saudável')).toBeInTheDocument()
    consoleSpy.mockRestore()
  })
})
