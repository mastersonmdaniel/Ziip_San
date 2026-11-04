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

describe('Table density (P1.33)', () => {
  it('compacta reduz a altura das células; o padrão continua confortável', () => {
    const { container, rerender } = render(
      <Table caption="Teste" density="compact">
        <TableHeader>
          <TableRow>
            <TableHead>Nome</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell label="Nome">X</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    )
    expect(container.querySelector('table')?.getAttribute('data-density')).toBe('compact')
    expect(container.querySelector('th')?.className).toContain('h-8')
    expect(container.querySelector('td')?.className).toContain('p-2')
    rerender(
      <Table caption="Teste">
        <TableBody>
          <TableRow>
            <TableCell>Y</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    )
    expect(container.querySelector('table')?.getAttribute('data-density')).toBe('comfortable')
    expect(container.querySelector('td')?.className).toContain('p-4')
  })
})
