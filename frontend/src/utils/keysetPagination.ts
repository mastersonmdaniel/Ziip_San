/**
 * ED-08: navegação sobre paginação keyset (cursor), a das listas grandes da
 * P0.22. Numa API keyset NÃO existe salto por offset: a única forma honesta de
 * "ir para a página N" é avançar página a página guardando os cursores —
 * as ÂNCORAS — e reusar a âncora guardada para voltar. É o que este navegador
 * faz:
 *
 * - `anchors[i]` é o cursor opaco que ABRE a página `i` (a página 0 abre com
 *   `null`). A âncora da página seguinte é guardada assim que a página atual
 *   é lida (`nextCursor` da resposta).
 * - Ir para uma página À FRENTE busca cada página intermediária, em sequência,
 *   uma requisição por página — nunca monta um cursor, nunca usa offset.
 * - Ir para uma página JÁ VISITADA reusa a âncora guardada: uma requisição.
 * - Se a lista acabar antes da página pedida (`hasNext` falso), para na
 *   última página alcançável e devolve `reached: false` — não finge que
 *   chegou.
 * - Trocar o tamanho da página INVALIDA os cursores (eles dependem do
 *   tamanho): as âncoras são descartadas e a navegação recomeça da página 0.
 *
 * O navegador é agnóstico de transporte: quem chama entrega um `fetcher` que
 * recebe `(cursor, size)` e devolve a página keyset do servidor.
 */

export interface KeysetPage<T> {
  content: T[]
  /** Cursor opaco da próxima página; `null` quando não há próxima. */
  nextCursor: string | null
  hasNext: boolean
}

/** Busca uma página keyset. `cursor === null` pede a primeira página. */
export type KeysetFetcher<T> = (cursor: string | null, size: number) => Promise<KeysetPage<T>>

export interface KeysetGoToResult<T> {
  page: KeysetPage<T>
  /** Página em que a navegação parou (base 0). */
  pageIndex: number
  /** `false` quando a lista acabou antes da página pedida. */
  reached: boolean
}

export interface KeysetNavigator<T> {
  /** Página atual (base 0). */
  readonly pageIndex: number
  /** Tamanho de página vigente. */
  readonly pageSize: number
  /** Última página lida, ou `null` antes da primeira leitura. */
  readonly current: KeysetPage<T> | null
  /** Lê a página 0 (âncora `null`). */
  open(): Promise<KeysetPage<T>>
  /** Avança uma página pelo cursor; na última, devolve a atual sem buscar. */
  next(): Promise<KeysetPage<T>>
  /** Volta uma página pela âncora guardada; na primeira, devolve a atual sem buscar. */
  prev(): Promise<KeysetPage<T>>
  /**
   * Vai para a página `target` (base 0): para trás, reusa a âncora; para a
   * frente, avança cursor a cursor. Alvo negativo é tratado como página 0.
   */
  goToPage(target: number): Promise<KeysetGoToResult<T>>
  /** Troca o tamanho, descarta as âncoras e relê a página 0. */
  setPageSize(size: number): Promise<KeysetPage<T>>
}

export function createKeysetNavigator<T>(fetcher: KeysetFetcher<T>, initialPageSize = 25): KeysetNavigator<T> {
  let size = initialPageSize
  let index = 0
  let anchors: (string | null)[] = [null]
  let current: KeysetPage<T> | null = null

  async function load(target: number): Promise<KeysetPage<T>> {
    const page = await fetcher(anchors[target] ?? null, size)
    current = page
    index = target
    if (page.hasNext && page.nextCursor != null) {
      anchors[target + 1] = page.nextCursor
    }
    return page
  }

  return {
    get pageIndex() {
      return index
    },
    get pageSize() {
      return size
    },
    get current() {
      return current
    },
    open() {
      return load(0)
    },
    async next() {
      if (!current) return load(0)
      if (!current.hasNext) return current
      // Revisão ED-08: resposta inconsistente do servidor (`hasNext` sem
      // `nextCursor`) não tem âncora guardada; sem ela, avançar buscaria com
      // cursor `null` e rotularia a página 0 como página seguinte. Parar na
      // página atual é o comportamento honesto.
      if (anchors[index + 1] === undefined) return current
      return load(index + 1)
    },
    async prev() {
      if (!current) return load(0)
      if (index <= 0) return current
      return load(index - 1)
    },
    async goToPage(target: number) {
      const wanted = Math.max(0, Math.trunc(target))
      if (!current) await load(0)
      if (wanted === index) {
        return { page: current as KeysetPage<T>, pageIndex: index, reached: true }
      }
      if (wanted < index) {
        // Para trás: a âncora da página alvo já foi guardada ao passar por ela.
        const page = await load(wanted)
        return { page, pageIndex: index, reached: true }
      }
      // Para a frente: avanço cursor a cursor, sem offset, até o alvo ou o fim.
      // Revisão ED-08: sem âncora guardada para a próxima página (resposta com
      // `hasNext` e sem `nextCursor`), o avanço para — `reached` sai `false`.
      while (index < wanted && (current as KeysetPage<T>).hasNext && anchors[index + 1] !== undefined) {
        await load(index + 1)
      }
      return { page: current as KeysetPage<T>, pageIndex: index, reached: index === wanted }
    },
    async setPageSize(newSize: number) {
      size = newSize
      anchors = [null]
      index = 0
      current = null
      return load(0)
    },
  }
}
