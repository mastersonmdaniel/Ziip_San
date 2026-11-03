import { useMemo } from 'react'
import { useLocation } from 'react-router-dom'
import { HELP_BUNDLE, type HelpArticle, type HelpTour } from '../help/helpBundle.generated'

/**
 * P1.21 — ajuda contextual (AJUDA-SUPORTE-SOBRE.md §3).
 *
 * Lê a rota atual (useLocation) e devolve os artigos, o tour e as dicas da
 * tela, a partir do bundle gerado de content/help
 * (scripts/help/generate-help-content.py — nunca edite o gerado à mão).
 * A busca é em português e sem acento: "imovel" encontra "imóvel".
 *
 * Limite honesto: o bundle é a fonte até a API de ajuda
 * (GET /api/v1/help/..., AJUDA §6) existir — ela é pendência declarada da
 * frente, não um atalho inventado aqui. Quando ela existir, este hook passa
 * a buscar nela; a interface (articles, tour, hints, search) não muda.
 */
function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
}

export interface ContextualHelp {
  articles: HelpArticle[]
  tour: HelpTour | undefined
  hints: Record<string, string>
  getHint: (id: string) => string | undefined
  search: (query: string) => HelpArticle[]
}

export function useContextualHelp(): ContextualHelp {
  const { pathname } = useLocation()

  return useMemo(() => {
    const articles = HELP_BUNDLE.articles.filter((article) => article.routes.includes(pathname))
    const tour = HELP_BUNDLE.tours.find((t) => t.route === pathname)
    const search = (query: string): HelpArticle[] => {
      const needle = normalize(query.trim())
      if (!needle) return []
      return HELP_BUNDLE.articles.filter((article) => {
        const haystack = normalize(
          [article.title, article.body, article.keywords.join(' ')].join(' '),
        )
        return needle.split(/\s+/).every((word) => haystack.includes(word))
      })
    }
    return {
      articles,
      tour,
      hints: HELP_BUNDLE.hints,
      getHint: (id: string) => HELP_BUNDLE.hints[id],
      search,
    }
  }, [pathname])
}
