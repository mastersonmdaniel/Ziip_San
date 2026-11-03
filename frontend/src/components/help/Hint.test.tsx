import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Hint } from './Hint'

describe('<Hint> (P1.21)', () => {
  it('exibe a dica da chave com o texto acessível', () => {
    render(<Hint id="cadastro.property.search" />)
    const hint = screen.getByLabelText(/Matrícula/)
    expect(hint).toHaveAttribute('data-hint-id', 'cadastro.property.search')
    expect(hint).toHaveAttribute('title', expect.stringContaining('Matrícula'))
  })

  it('chave desconhecida não renderiza nada', () => {
    const { container } = render(<Hint id="campo.inexistente" />)
    expect(container).toBeEmptyDOMElement()
  })
})
