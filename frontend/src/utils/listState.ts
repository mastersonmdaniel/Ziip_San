/**
 * P1.12: filtro preservado ao voltar da ficha. A lista salva o filtro aplicado
 * e a página em sessionStorage (só nesta aba; fechar a aba esquece) sempre que
 * mudam; ao montar, a lista restaura o que foi salvo em vez de começar vazia.
 * O detalhe não precisa saber disso: o link "Voltar à pesquisa" já volta para
 * a rota da lista, e a lista se restaura sozinha. Falha de leitura/escrita
 * (storage indisponível, JSON inválido) cai para o filtro vazio, sem quebrar
 * a tela.
 */
export function listStateKey(key: string): string {
  return `ziipsan:list:${key}`
}

export function saveListState(key: string, state: unknown): void {
  try {
    sessionStorage.setItem(listStateKey(key), JSON.stringify(state))
  } catch {
    /* storage indisponível: a lista segue sem preservação */
  }
}

export function loadListState<T extends object>(key: string, fallback: T): T {
  try {
    const raw = sessionStorage.getItem(listStateKey(key))
    if (!raw) return fallback
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return fallback
    return { ...fallback, ...(parsed as Partial<T>) }
  } catch {
    return fallback
  }
}

export function clearListState(key: string): void {
  try {
    sessionStorage.removeItem(listStateKey(key))
  } catch {
    /* nada a fazer */
  }
}
