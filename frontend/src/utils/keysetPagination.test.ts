import { describe, expect, it } from 'vitest'
import { createKeysetNavigator, type KeysetFetcher, type KeysetPage } from './keysetPagination'

/**
 * Fetcher keyset de mentira, mas fiel ao contrato: o cursor é um token opaco
 * que ELE emite (aqui, o deslocamento interno dele — o navegador testado nunca
 * vê esse significado, só devolve o token). Registra cada cursor recebido
 * para o teste provar que a navegação só usa cursores emitidos pelo servidor.
 */
function fakeFetcher(totalItems: number) {
  const items = Array.from({ length: totalItems }, (_, i) => i + 1)
  const received: (string | null)[] = []
  const fetcher: KeysetFetcher<number> = async (cursor, size) => {
    received.push(cursor)
    const start = cursor === null ? 0 : Number(cursor)
    const content = items.slice(start, start + size)
    const nextStart = start + size
    const hasNext = nextStart < items.length
    const page: KeysetPage<number> = {
      content,
      nextCursor: hasNext ? String(nextStart) : null,
      hasNext,
    }
    return page
  }
  return { fetcher, received }
}

describe('keysetPagination (ED-08)', () => {
  it('open lê a página 0 com cursor nulo e guarda a âncora da próxima', async () => {
    const { fetcher, received } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    const page = await nav.open()
    expect(received).toEqual([null])
    expect(page.content[0]).toBe(1)
    expect(page.content).toHaveLength(25)
    expect(page.hasNext).toBe(true)
    expect(nav.pageIndex).toBe(0)
  })

  it('next e prev navegam só com os cursores emitidos pelo servidor', async () => {
    const { fetcher, received } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    const p2 = await nav.next()
    expect(p2.content[0]).toBe(26)
    const back = await nav.prev()
    expect(back.content[0]).toBe(1)
    // Sequência de cursores pedidos: null (pág. 0), '25' (pág. 1), null (volta à pág. 0).
    expect(received).toEqual([null, '25', null])
    expect(nav.pageIndex).toBe(0)
  })

  it('ir para página à frente avança cursor a cursor, sem offset', async () => {
    const { fetcher, received } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    received.length = 0
    const result = await nav.goToPage(3)
    expect(result.reached).toBe(true)
    expect(result.pageIndex).toBe(3)
    expect(result.page.content[0]).toBe(76)
    // Três buscas intermediárias, cada uma com o cursor emitido pela anterior.
    expect(received).toEqual(['25', '50', '75'])
  })

  it('voltar a uma página já visitada reusa a âncora guardada (uma busca)', async () => {
    const { fetcher, received } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    await nav.goToPage(3)
    received.length = 0
    const result = await nav.goToPage(1)
    expect(result.reached).toBe(true)
    expect(result.pageIndex).toBe(1)
    expect(result.page.content[0]).toBe(26)
    expect(received).toEqual(['25'])
  })

  it('ir além do fim para na última página e declara reached=false', async () => {
    const { fetcher } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    const result = await nav.goToPage(99)
    expect(result.reached).toBe(false)
    expect(result.pageIndex).toBe(3)
    expect(result.page.hasNext).toBe(false)
    expect(result.page.content).toHaveLength(20)
  })

  it('next na última página não busca de novo', async () => {
    const { fetcher, received } = fakeFetcher(30)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    await nav.next()
    const before = received.length
    const again = await nav.next()
    expect(received).toHaveLength(before)
    expect(again.content[0]).toBe(26)
    expect(nav.pageIndex).toBe(1)
  })

  it('trocar o tamanho descarta os cursores e recomeça da página 0', async () => {
    const { fetcher, received } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    await nav.goToPage(2)
    received.length = 0
    const page = await nav.setPageSize(10)
    expect(nav.pageSize).toBe(10)
    expect(nav.pageIndex).toBe(0)
    expect(page.content).toHaveLength(10)
    expect(received).toEqual([null])
    // E o avanço seguinte usa os cursores do NOVO tamanho.
    received.length = 0
    const result = await nav.goToPage(2)
    expect(result.page.content[0]).toBe(21)
    expect(received).toEqual(['10', '20'])
  })

  it('resposta inconsistente (hasNext sem nextCursor) não avança com cursor fabricado (revisão ED-08)', async () => {
    const received: (string | null)[] = []
    const fetcher: KeysetFetcher<number> = async (cursor) => {
      received.push(cursor)
      return { content: [1], nextCursor: null, hasNext: true }
    }
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    const again = await nav.next()
    expect(received).toEqual([null])
    expect(nav.pageIndex).toBe(0)
    expect(again.content).toEqual([1])
    const result = await nav.goToPage(3)
    expect(result.reached).toBe(false)
    expect(result.pageIndex).toBe(0)
  })

  it('goToPage para a página atual não busca de novo; alvo negativo vira página 0', async () => {
    const { fetcher, received } = fakeFetcher(95)
    const nav = createKeysetNavigator(fetcher, 25)
    await nav.open()
    received.length = 0
    const same = await nav.goToPage(0)
    expect(same.reached).toBe(true)
    expect(received).toEqual([])
    await nav.next()
    received.length = 0
    const negative = await nav.goToPage(-5)
    expect(negative.pageIndex).toBe(0)
    expect(received).toEqual([null])
  })
})
