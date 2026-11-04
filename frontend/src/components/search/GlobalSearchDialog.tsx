import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Dialog } from '@components/ui/Dialog'
import { Button } from '@components/ui/Button'
import { Alert, Spinner } from '@components/ui/Alert'
import { useDebounced } from '@hooks/useDebounced'
import { useDensity } from '@hooks/useDensity'
import { useSearchHistory } from '@hooks/useSearchHistory'
import type { ShortcutSettings } from '@hooks/useShortcuts'
import { bindingOf } from '@hooks/useShortcuts'
import {
  SEARCH_TYPE_LABEL,
  globalSearch,
  savedViewsApi,
  type SearchHit,
  type GlobalSearchResult,
} from '@services/search'
import { errorMessage } from '@services/registry/errors'

/**
 * P1.33: paleta de busca global (Ctrl ou Cmd+K). O diálogo é o `Dialog` acessível
 * do projeto (foco preso, Esc fecha e devolve o foco, título associado).
 *
 * Teclado dentro da paleta: setas sobem/descem entre os resultados, Enter abre o
 * resultado (quando há página), Esc fecha. A busca é POST com o texto no corpo
 * (nunca na URL) e com debounce; o servidor devolve só o mascarado, nunca o
 * documento em claro. Busca por documento completo só abrange cliente e unidade
 * (o aviso abaixo diz isso); seção sem permissão aparece como aviso, sem dado.
 *
 * Recentes e favoritos vivem só na aba (ver `useSearchHistory`); busca com cara
 * de documento nunca entra no histórico. Salvar a busca como visão grava no
 * servidor (`/search/saved-views`, recurso `global`).
 */
interface GlobalSearchDialogProps {
  open: boolean
  onClose: () => void
  shortcuts: ShortcutSettings
  setShortcutsEnabled: (enabled: boolean) => void
  setOpenSearchBinding: (binding: string) => void
  resetShortcuts: () => void
}

const TYPE_ORDER: SearchHit['type'][] = ['CUSTOMER', 'CONSUMER_UNIT', 'SERVICE_ORDER', 'BILL', 'DOCUMENT']

export function GlobalSearchDialog({
  open,
  onClose,
  shortcuts,
  setShortcutsEnabled,
  setOpenSearchBinding,
  resetShortcuts,
}: GlobalSearchDialogProps) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebounced(query, 300)
  const [result, setResult] = useState<GlobalSearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [activeIndex, setActiveIndex] = useState(-1)
  const [saveTitle, setSaveTitle] = useState('')
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [recording, setRecording] = useState(false)
  const { density, setDensity } = useDensity()
  const { recents, favorites, addRecent, toggleFavorite, isFavorite } = useSearchHistory()
  const rowRefs = useRef<Array<HTMLButtonElement | HTMLAnchorElement | null>>([])
  const inputRef = useRef<HTMLInputElement>(null)

  const trimmed = debouncedQuery.trim()

  useEffect(() => {
    if (!open) return undefined
    if (!trimmed) {
      setResult(null)
      setError(null)
      setLoading(false)
      return undefined
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    globalSearch(trimmed)
      .then((data) => {
        if (cancelled) return
        setResult(data)
        setActiveIndex(-1)
        addRecent(trimmed)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        setError(errorMessage(e))
        setResult(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [trimmed, open, addRecent])

  useEffect(() => {
    if (!open) {
      setQuery('')
      setResult(null)
      setError(null)
      setSaveMessage(null)
      setRecording(false)
    }
  }, [open ])

  const flatResults: SearchHit[] = result
    ? [...TYPE_ORDER.flatMap((type) => result.results.filter((hit) => hit.type === type))]
    : []

  const onInputKeyDown = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' && flatResults.length > 0) {
      event.preventDefault()
      const next = activeIndex + 1 < flatResults.length ? activeIndex + 1 : 0
      setActiveIndex(next)
      rowRefs.current[next]?.focus()
      return
    }
    if (event.key === 'ArrowUp' && flatResults.length > 0) {
      event.preventDefault()
      const next = activeIndex - 1 >= 0 ? activeIndex - 1 : flatResults.length - 1
      setActiveIndex(next)
      rowRefs.current[next]?.focus()
      return
    }
  }

  const openHit = (hit: SearchHit) => {
    if (hit.href) {
      onClose()
      navigate(hit.href)
    }
  }

  const saveCurrentSearch = async () => {
    const title = saveTitle.trim() || `Busca: ${trimmed}`
    setSaveMessage(null)
    try {
      await savedViewsApi.create({ title, resource: 'global', filters: JSON.stringify({ q: trimmed }) })
      setSaveMessage(`Visão “${title}” salva.`)
    } catch (e: unknown) {
      setSaveMessage(errorMessage(e))
    }
  }

  const onRecordKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>) => {
    if (!recording) return
    event.preventDefault()
    // Tecla única sozinha não vira atalho (WCAG 2.1.4): exige modificador.
    if (!event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey) return
    if (['Control', 'Meta', 'Shift', 'Alt'].includes(event.key)) return
    setOpenSearchBinding(bindingOf(event.nativeEvent))
    setRecording(false)
  }

  const rowPadding = density === 'compact' ? 'py-1' : 'py-2'

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Busca global"
      description="Pesquise cliente, unidade, ordem de serviço, conta e documento. Ctrl ou Cmd+K abre esta paleta."
    >
      <div className="space-y-4">
        <label className="sr-only" htmlFor="global-search-input">
          Texto da busca
        </label>
        <input
          id="global-search-input"
          ref={inputRef}
          data-autofocus
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={onInputKeyDown}
          placeholder="Buscar cliente, unidade, OS, conta ou documento…"
          autoComplete="off"
          className="w-full rounded-md border border-input bg-card px-3 py-2 text-sm"
        />

        {loading ? <Spinner label="Buscando…" /> : null}
        {error ? <Alert tone="error">{error}</Alert> : null}

        {!trimmed ? (
          <div className="space-y-4">
            {favorites.length > 0 ? (
              <section aria-label="Favoritos">
                <h3 className="text-sm font-medium">Favoritos</h3>
                <ul className="mt-1 space-y-1">
                  {favorites.map((favorite) => (
                    <li key={`${favorite.type}:${favorite.id}`}>
                      {favorite.href ? (
                        <Link
                          to={favorite.href}
                          onClick={onClose}
                          className="block rounded px-2 py-1 text-sm hover:bg-muted"
                        >
                          {SEARCH_TYPE_LABEL[favorite.type]} · {favorite.title}
                        </Link>
                      ) : (
                        <span className="block rounded px-2 py-1 text-sm">
                          {SEARCH_TYPE_LABEL[favorite.type]} · {favorite.title}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
            {recents.length > 0 ? (
              <section aria-label="Buscas recentes">
                <h3 className="text-sm font-medium">Recentes</h3>
                <ul className="mt-1 space-y-1">
                  {recents.map((recent) => (
                    <li key={recent}>
                      <button
                        type="button"
                        onClick={() => setQuery(recent)}
                        className="block w-full rounded px-2 py-1 text-left text-sm hover:bg-muted"
                      >
                        {recent}
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
            {favorites.length === 0 && recents.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                Digite para buscar. Buscas com cara de documento nunca entram no histórico.
              </p>
            ) : null}
          </div>
        ) : null}

        {result && flatResults.length === 0 && !loading ? (
          <p className="text-sm text-muted-foreground">Nenhum resultado para “{result.q}”.</p>
        ) : null}

        {result ? (
          <div className="space-y-3">
            {result.documentSearch ? (
              <p className="text-sm text-muted-foreground">
                Busca por documento completo: abrange só cliente e unidade, com permissão própria, registro e cota.
              </p>
            ) : null}
            {result.deniedTypes.length > 0 ? (
              <p className="text-sm text-muted-foreground">
                Sem permissão para: {result.deniedTypes.map((type) => SEARCH_TYPE_LABEL[type]).join(', ')}.
              </p>
            ) : null}
            {result.pendingTypes.length > 0 ? (
              <p className="text-sm text-muted-foreground">
                Protocolo ainda não está na busca (tabela da P1.36, em construção).
              </p>
            ) : null}
            <div aria-label="Resultados da busca" className="space-y-3">
              {TYPE_ORDER.map((type) => {
                const hits = flatResults.filter((hit) => hit.type === type)
                if (hits.length === 0) return null
                return (
                  <div key={type}>
                    <h3 className="text-sm font-medium">{SEARCH_TYPE_LABEL[type]}</h3>
                    <div className="mt-1 space-y-1">
                      {hits.map((hit) => {
                        const flatIndex = flatResults.indexOf(hit)
                        const favorite = isFavorite(hit.type, hit.id)
                        const row = (
                          <span className="flex w-full items-center gap-2">
                            <span className="min-w-0 flex-1 text-left">
                              <span className="block truncate font-medium">{hit.title}</span>
                              <span className="block truncate text-xs text-muted-foreground">{hit.subtitle}</span>
                            </span>
                            <span aria-hidden="true">{favorite ? '★' : '☆'}</span>
                          </span>
                        )
                        return (
                          <div key={hit.id} className="flex items-center gap-1">
                            {hit.href ? (
                              <button
                                type="button"
                                ref={(el) => {
                                  rowRefs.current[flatIndex] = el
                                }}
                                onClick={() => openHit(hit)}
                                onFocus={() => setActiveIndex(flatIndex)}
                                className={`flex-1 rounded px-2 text-sm hover:bg-muted ${rowPadding} ${
                                  activeIndex === flatIndex ? 'bg-muted' : ''
                                }`}
                              >
                                {row}
                              </button>
                            ) : (
                              <span
                                className={`flex-1 rounded px-2 text-sm ${rowPadding}`}
                              >
                                {row}
                              </span>
                            )}
                            <Button
                              variant="ghost"
                              size="sm"
                              aria-label={`${favorite ? 'Desfavoritar' : 'Favoritar'} ${hit.title}`}
                              aria-pressed={favorite}
                              onClick={() => toggleFavorite(hit)}
                            >
                              {favorite ? '★' : '☆'}
                            </Button>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </div>
            <div className="flex flex-wrap items-center gap-2 border-t pt-3">
              <label className="sr-only" htmlFor="save-view-title">
                Título da visão
              </label>
              <input
                id="save-view-title"
                type="text"
                value={saveTitle}
                onChange={(event) => setSaveTitle(event.target.value)}
                placeholder={`Salvar busca como visão (ex.: Busca: ${trimmed})`}
                className="min-w-0 flex-1 rounded-md border border-input bg-card px-2 py-1.5 text-sm"
              />
              <Button variant="outline" size="sm" onClick={() => void saveCurrentSearch()}>
                Salvar visão
              </Button>
              <Link to="/busca/visoes-salvas" onClick={onClose} className="text-sm underline">
                Visões salvas
              </Link>
            </div>
            {saveMessage ? <p className="text-sm text-muted-foreground">{saveMessage}</p> : null}
          </div>
        ) : null}

        <div className="flex flex-wrap items-center gap-2 border-t pt-3 text-sm">
          <label className="inline-flex items-center gap-1">
            <input
              type="checkbox"
              checked={shortcuts.enabled}
              onChange={(event) => setShortcutsEnabled(event.target.checked)}
            />
            Atalhos ligados
          </label>
          <span className="text-muted-foreground">Abrir busca: {shortcuts.openSearch}</span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setRecording((r) => !r)}
            onKeyDown={onRecordKeyDown}
            aria-label="Gravar nova combinação para abrir a busca"
          >
            {recording ? 'Pressione a combinação…' : 'Remapear'}
          </Button>
          <Button variant="ghost" size="sm" onClick={resetShortcuts}>
            Restaurar padrão
          </Button>
          <label className="inline-flex items-center gap-1">
            Densidade
            <select
              aria-label="Densidade da tabela"
              value={density}
              onChange={(event) => setDensity(event.target.value as 'comfortable' | 'compact')}
              className="rounded-md border border-input bg-card px-1 py-1 text-sm"
            >
              <option value="comfortable">Confortável</option>
              <option value="compact">Compacta</option>
            </select>
          </label>
        </div>
        <p className="text-xs text-muted-foreground">
          Teclas de atalho podem ser desligadas acima (WCAG 2.1.4). Atalhos não roubam a digitação em campos.
        </p>
      </div>
    </Dialog>
  )
}
