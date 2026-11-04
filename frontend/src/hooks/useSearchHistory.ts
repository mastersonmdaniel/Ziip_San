import { useCallback, useState } from 'react'
import type { SearchHit } from '@services/search'
import { looksLikeDocument } from '@services/registry/client'

/**
 * P1.33: ações recentes e favoritos da busca global, só na aba (sessionStorage,
 * como o filtro preservado da P1.12 em `listState`: fechar a aba esquece).
 *
 * Limite de propósito: NUNCA se guarda busca com cara de documento (CPF/CNPJ)
 * — nem o texto, nem o resultado. O que se guarda é o texto de busca comum e
 * o alvo favoritado (tipo, id, título, link). A persistência no servidor
 * (por usuário, entre dispositivos) aguarda a extensão de `user_preferences`
 * da P1.18: sem ela, guardar favorito no servidor seria inventar tabela.
 */
export interface FavoriteTarget {
  type: SearchHit['type']
  id: string
  title: string
  subtitle: string
  href: string | null
}

const KEY = 'ziipsan:search-history:v1'
const MAX_RECENTS = 10
const MAX_FAVORITES = 50

interface Stored {
  recents: string[]
  favorites: FavoriteTarget[]
}

function read(): Stored {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return { recents: [], favorites: [] }
    const parsed = JSON.parse(raw) as Partial<Stored>
    return {
      recents: Array.isArray(parsed.recents) ? parsed.recents.filter((q) => typeof q === 'string') : [],
      favorites: Array.isArray(parsed.favorites) ? parsed.favorites : [],
    }
  } catch {
    return { recents: [], favorites: [] }
  }
}

function persist(next: Stored) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    /* storage indisponível: o histórico vale só em memória */
  }
}

export function toFavorite(hit: SearchHit): FavoriteTarget {
  return { type: hit.type, id: hit.id, title: hit.title, subtitle: hit.subtitle, href: hit.href }
}

export function useSearchHistory() {
  const [stored, setStored] = useState<Stored>(read)

  const addRecent = useCallback((q: string) => {
    const query = q.trim()
    // Documento nunca entra no histórico: nem texto, nem resultado.
    if (!query || looksLikeDocument(query)) return
    setStored((prev) => {
      const recents = [query, ...prev.recents.filter((item) => item !== query)].slice(0, MAX_RECENTS)
      const next = { ...prev, recents }
      persist(next)
      return next
    })
  }, [])

  const toggleFavorite = useCallback((hit: SearchHit) => {
    const favorite = toFavorite(hit)
    setStored((prev) => {
      const exists = prev.favorites.some((item) => item.type === favorite.type && item.id === favorite.id)
      const favorites = exists
        ? prev.favorites.filter((item) => !(item.type === favorite.type && item.id === favorite.id))
        : [favorite, ...prev.favorites].slice(0, MAX_FAVORITES)
      const next = { ...prev, favorites }
      persist(next)
      return next
    })
  }, [])

  const isFavorite = useCallback(
    (type: SearchHit['type'], id: string) =>
      stored.favorites.some((item) => item.type === type && item.id === id),
    [stored.favorites],
  )

  return { recents: stored.recents, favorites: stored.favorites, addRecent, toggleFavorite, isFavorite }
}
