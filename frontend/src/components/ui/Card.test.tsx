import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Card, CardContent, CardHeader, CardTitle } from './Card'

// P-TEMA-06 (pacote temas-componentes): CardHeader/CardTitle pedidos pelas
// telas reais da main — Templates (so children), CustomerDetail/
// CustomerForm/PropertyDetail (as="h2" + className, CardHeader className).
describe('CardHeader / CardTitle (P-TEMA-06)', () => {
  it('CardHeader renderiza os filhos como contentor com a classe da biblioteca', () => {
    const { container } = render(
      <CardHeader>
        <span>Conteúdo do cabeçalho</span>
      </CardHeader>,
    )
    expect(screen.getByText('Conteúdo do cabeçalho')).toBeInTheDocument()
    expect(container.firstElementChild?.tagName).toBe('DIV')
    expect(container.firstElementChild?.className).toContain('ui-card-header')
  })

  it('CardHeader junta o className de quem chama (uso pb-3 do PropertyDetail)', () => {
    const { container } = render(<CardHeader className="pb-3">X</CardHeader>)
    expect(container.firstElementChild?.className).toContain('ui-card-header')
    expect(container.firstElementChild?.className).toContain('pb-3')
  })

  it('CardTitle sem `as` renderiza um h3 (forma do stub do harness de provas)', () => {
    render(<CardTitle>Modelos do tenant</CardTitle>)
    const heading = screen.getByRole('heading', { name: 'Modelos do tenant' })
    expect(heading.tagName).toBe('H3')
    expect(heading.className).toContain('ui-card-title')
  })

  it('CardTitle com as="h2" e className respeita o nível e a classe (uso das telas de cadastro)', () => {
    render(
      <CardTitle as="h2" className="text-base">
        Dados
      </CardTitle>,
    )
    const heading = screen.getByRole('heading', { name: 'Dados', level: 2 })
    expect(heading.tagName).toBe('H2')
    expect(heading.className).toContain('ui-card-title')
    expect(heading.className).toContain('text-base')
  })

  it('composição completa Card > CardHeader > CardTitle + CardContent (padrão das telas)', () => {
    render(
      <Card>
        <CardHeader>
          <CardTitle>Versões do modelo</CardTitle>
        </CardHeader>
        <CardContent>Corpo do cartão</CardContent>
      </Card>,
    )
    expect(screen.getByRole('heading', { name: 'Versões do modelo' })).toBeInTheDocument()
    expect(screen.getByText('Corpo do cartão')).toBeInTheDocument()
  })

  it('CardTitle aceita conteúdo dinâmico como filho (uso "Versões de {code}" do Templates)', () => {
    const code = 'MOD-01'
    render(<CardTitle>Versões de {code}</CardTitle>)
    expect(screen.getByRole('heading', { name: 'Versões de MOD-01' })).toBeInTheDocument()
  })
})
