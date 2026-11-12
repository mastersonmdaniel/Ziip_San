import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Showcase } from './Showcase'

// P-TEMA-CORR-02 (pacote temas-componentes): a vitrine exerce os casos
// que faltavam — Field com fragmento, Field com multiplos controlos e
// Icon com nome desconhecido — e a composicao CardHeader/CardTitle da
// P-TEMA-06. Os comportamentos provados sao os que a biblioteca define:
// fragmento com um controle = label associado (R2-F6); multiplos
// controlos = associacao por grupo, sem <label for> orfao (R2-F6);
// icone desconhecido = glifo de recurso marcado data-icon-unknown +
// aviso em desenvolvimento, sem partir (R2-F9, ver Icon.tsx).
describe('Showcase — casos P-TEMA-CORR-02 e P-TEMA-06', () => {
  it('Field com fragmento: o rótulo fica associado ao único controle do fragmento', () => {
    render(<Showcase />)
    const input = screen.getByLabelText('Campo em fragmento')
    expect(input).toHaveAttribute('placeholder', 'Controle dentro do fragmento')
    expect(input.id).not.toBe('')
  })

  it('Field com múltiplos controlos: associação por grupo, sem label órfão', () => {
    const { container } = render(<Showcase />)
    const group = screen.getByRole('group', { name: 'Grupo de controles' })
    expect(within(group).getByPlaceholderText('Primeiro controle do grupo')).toBeInTheDocument()
    expect(within(group).getByPlaceholderText('Segundo controle do grupo')).toBeInTheDocument()
    // Nenhum <label for> com este texto: um `for` so pode apontar para
    // um controle — a legenda do grupo e um <span>, nao um <label>.
    const orphan = Array.from(container.querySelectorAll('label')).find(
      (l) => l.textContent === 'Grupo de controles',
    )
    expect(orphan).toBeUndefined()
  })

  it('Icon com nome desconhecido: fallback marcado da biblioteca, sem partir a vitrine', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    try {
      const { container } = render(<Showcase />)
      const unknown = container.querySelector('svg[data-icon="icone-que-nao-existe"]')
      expect(unknown).not.toBeNull()
      expect(unknown?.getAttribute('data-icon-unknown')).toBe('true')
      // Em desenvolvimento a biblioteca avisa (R2-F9): o nome pedido
      // aparece no aviso.
      const warned = warn.mock.calls.some((args) =>
        args.some((a) => typeof a === 'string' && a.includes('icone-que-nao-existe')),
      )
      expect(warned).toBe(true)
      // E os icones conhecidos NAO levam a marca de desconhecido.
      const known = container.querySelector('svg[data-icon="plus"]')
      expect(known).not.toBeNull()
      expect(known?.hasAttribute('data-icon-unknown')).toBe(false)
    } finally {
      warn.mockRestore()
    }
  })

  it('CardHeader/CardTitle: a vitrine compõe o cabeçalho do cartão nos dois níveis', () => {
    render(<Showcase />)
    expect(screen.getByRole('heading', { name: 'Cabeçalho do cartão (P-TEMA-06)', level: 3 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Título em h2 (as="h2")', level: 2 })).toBeInTheDocument()
  })
})
