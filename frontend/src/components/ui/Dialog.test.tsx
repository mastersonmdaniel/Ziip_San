import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Dialog } from './Dialog'

// P-TEMA-08 (pacote temas-componentes): armadilha de foco completa do
// Dialog — Tab/Shift+Tab circulam dentro, Escape fecha, foco inicial no
// [data-autofocus], retorno de foco ao fechar, zero focaveis e conteudo
// dinamico. Os Tabs sao despachados como keydown no document (onde o
// Dialog ouve) e o retorno do fireEvent diz se houve preventDefault:
// false = o gesto foi interceptado pela armadilha.
function tab(shift = false) {
  return fireEvent.keyDown(document, { key: 'Tab', shiftKey: shift })
}

describe('Dialog — foco inicial, Escape e retorno de foco (pré-existentes)', () => {
  it('ao abrir, o foco vai para o [data-autofocus]', () => {
    render(
      <Dialog open onClose={vi.fn()} title="Excluir">
        <textarea data-autofocus aria-label="Motivo" />
        <button type="button">Outro</button>
      </Dialog>,
    )
    expect(screen.getByLabelText('Motivo')).toHaveFocus()
  })

  it('sem [data-autofocus], o foco vai para o primeiro focável', () => {
    render(
      <Dialog open title="Sem autofocus">
        <button type="button">Primeiro</button>
        <button type="button">Segundo</button>
      </Dialog>,
    )
    expect(screen.getByRole('button', { name: 'Primeiro' })).toHaveFocus()
  })

  it('Escape chama onClose', () => {
    const onClose = vi.fn()
    render(
      <Dialog open onClose={onClose} title="Fechar com Escape">
        <button type="button">Dentro</button>
      </Dialog>,
    )
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('ao fechar, o foco volta ao elemento focado antes de abrir', () => {
    const view = (open: boolean) => (
      <>
        <button type="button">Fora do diálogo</button>
        <Dialog open={open} onClose={vi.fn()} title="Retorno de foco">
          <button type="button">Dentro</button>
        </Dialog>
      </>
    )
    const { rerender } = render(view(false))
    const outside = screen.getByRole('button', { name: 'Fora do diálogo' })
    outside.focus()
    expect(outside).toHaveFocus()
    rerender(view(true))
    // Sem [data-autofocus], o foco inicial vai para o PRIMEIRO focavel do
    // painel — que e o botao Fechar do cabecalho (precede os filhos no
    // DOM; e a mesma ordem que os testes da armadilha assumem e o
    // comportamento que o Dialog ja tinha antes da P-TEMA-08).
    // Corrigido na 2a vaga: a assercao herdada esperava o botao "Dentro",
    // que nunca foi o primeiro focavel com onClose presente — ver
    // logs/vitest-final2.log (falha 1/37) e o RELATORIO do pacote.
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()
    rerender(view(false))
    expect(outside).toHaveFocus()
  })
})

describe('Dialog — armadilha de foco (P-TEMA-08)', () => {
  it('Tab no último focável circula para o primeiro', () => {
    render(
      <Dialog open onClose={vi.fn()} title="Circula para a frente">
        <button type="button">Meio</button>
      </Dialog>,
    )
    // Ordem no painel: Fechar (cabecalho) -> Meio. O ultimo e "Meio".
    const last = screen.getByRole('button', { name: 'Meio' })
    const first = screen.getByRole('button', { name: 'Fechar' })
    last.focus()
    expect(tab()).toBe(false)
    expect(first).toHaveFocus()
  })

  it('Shift+Tab no primeiro focável circula para o último', () => {
    render(
      <Dialog open onClose={vi.fn()} title="Circula para tras">
        <button type="button">Meio</button>
      </Dialog>,
    )
    const first = screen.getByRole('button', { name: 'Fechar' })
    const last = screen.getByRole('button', { name: 'Meio' })
    first.focus()
    expect(tab(true)).toBe(false)
    expect(last).toHaveFocus()
  })

  it('Tab a meio da lista NÃO é interceptado (o browser avança dentro do diálogo)', () => {
    render(
      <Dialog open onClose={vi.fn()} title="Meio da lista">
        <button type="button">Meio</button>
      </Dialog>,
    )
    const first = screen.getByRole('button', { name: 'Fechar' })
    first.focus()
    // Sem preventDefault: o gesto segue o curso normal do browser,
    // que e o elemento seguinte DENTRO do painel.
    expect(tab()).toBe(true)
  })

  it('rodada completa: Tab repetido nunca sai do diálogo', () => {
    render(
      <Dialog
        open
        onClose={vi.fn()}
        title="Rodada completa"
        footer={<button type="button">Rodapé</button>}
      >
        <button type="button">Corpo</button>
      </Dialog>,
    )
    const panel = screen.getByRole('dialog')
    // Focaveis: Fechar, Corpo, Rodape. Percorre 6 Tabs (>= 2 voltas).
    screen.getByRole('button', { name: 'Fechar' }).focus()
    for (let i = 0; i < 6; i += 1) {
      fireEvent.keyDown(document, { key: 'Tab' })
      // Nas posicoes de fronteira a armadilha move o foco; a meio, o
      // jsdom nao move — em ambos os casos o foco activo, quando muda,
      // fica dentro do painel. A prova forte da fronteira esta nos
      // testes de circulacao acima; aqui garante-se que o foco nunca
      // fica num elemento de fora.
      const active = document.activeElement
      if (active && active !== document.body) {
        expect(panel.contains(active)).toBe(true)
      }
    }
  })

  it('com um único focável, Tab e Shift+Tab ficam nele', () => {
    render(
      <Dialog open title="Um so focavel">
        <button type="button">Único</button>
      </Dialog>,
    )
    const only = screen.getByRole('button', { name: 'Único' })
    only.focus()
    expect(tab()).toBe(false)
    expect(only).toHaveFocus()
    expect(tab(true)).toBe(false)
    expect(only).toHaveFocus()
  })

  it('com ZERO focáveis, o foco fica no contentor e Tab não escapa', () => {
    render(
      <Dialog open title="Sem focaveis">
        <p>Só texto, nenhum controle.</p>
      </Dialog>,
    )
    const panel = screen.getByRole('dialog')
    expect(panel).toHaveFocus()
    expect(tab()).toBe(false)
    expect(panel).toHaveFocus()
    expect(tab(true)).toBe(false)
    expect(panel).toHaveFocus()
  })

  it('conteúdo dinâmico: elemento que entra com o diálogo aberto entra na circulação', () => {
    const view = (extra: boolean) => (
      <Dialog open onClose={vi.fn()} title="Dinamico">
        <button type="button">Fixo</button>
        {extra ? <button type="button">Novo</button> : null}
      </Dialog>
    )
    const { rerender } = render(view(false))
    const first = screen.getByRole('button', { name: 'Fechar' })
    const fixed = screen.getByRole('button', { name: 'Fixo' })
    // Antes: ultimo = Fixo; Tab em Fixo -> Fechar.
    fixed.focus()
    expect(tab()).toBe(false)
    expect(first).toHaveFocus()
    // Depois de entrar o "Novo": ultimo = Novo; Tab em Novo -> Fechar,
    // e Tab em Fixo ja NAO circula (passou a ser o meio da lista).
    rerender(view(true))
    const added = screen.getByRole('button', { name: 'Novo' })
    added.focus()
    expect(tab()).toBe(false)
    expect(first).toHaveFocus()
    fixed.focus()
    expect(tab()).toBe(true)
  })

  it('foco que entra de fora (focusin) é puxado de volta para dentro', () => {
    render(
      <>
        <button type="button">Fora</button>
        <Dialog open onClose={vi.fn()} title="Puxa de volta">
          <button type="button">Dentro</button>
        </Dialog>
      </>,
    )
    const outside = screen.getByRole('button', { name: 'Fora' })
    fireEvent.focusIn(outside)
    const active = document.activeElement
    expect(screen.getByRole('dialog').contains(active)).toBe(true)
  })

  it('botões desactivados ficam fora da circulação', () => {
    render(
      <Dialog open onClose={vi.fn()} title="Desactivados fora">
        <button type="button">Activo</button>
        <button type="button" disabled>
          Inactivo
        </button>
      </Dialog>,
    )
    // Focaveis efectivos: Fechar, Activo. Tab em Activo (ultimo) -> Fechar.
    const active = screen.getByRole('button', { name: 'Activo' })
    active.focus()
    expect(tab()).toBe(false)
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()
  })
})
