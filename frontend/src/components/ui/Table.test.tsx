import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './Table'

describe('Table mobileCards (P1.12)', () => {
  it('marca a tabela e dá rótulo a cada célula para o cartão do celular', () => {
    const { container } = render(
      <Table caption="Teste" mobileCards>
        <TableHeader>
          <TableRow>
            <TableHead>Nome</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell label="Nome">TESTE Cliente</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    )
    expect(container.querySelector('table')?.getAttribute('data-mobile-cards')).toBe('')
    expect(screen.getByText('TESTE Cliente').getAttribute('data-label')).toBe('Nome')
  })

  it('sem mobileCards, não marca a tabela', () => {
    const { container } = render(
      <Table caption="Teste">
        <TableBody>
          <TableRow>
            <TableCell>X</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    )
    expect(container.querySelector('table')?.hasAttribute('data-mobile-cards')).toBe(false)
  })
})
