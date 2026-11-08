import { useEffect, useMemo, useState } from 'react'
import { Spinner } from '@components/ui/Alert'
import {
  listThematics,
  thematicData,
  type GisThematic,
  type LegendEntry,
  type ThematicData,
} from '@services/gis/thematic'

/**
 * GIS-08: painel de temáticos do mapa web (§10.3) — lista as
 * definições do tenant (fábrica + próprias), mostra a legenda
 * automática com as contagens e o filtro por categoria do SG-883.
 * A camada de pontos é desenhada pela página do mapa (GIS-06) a
 * partir de `thematicData`; esta página é o painel de controle e
 * de conferência do temático, não um segundo mapa.
 *
 * Temático com `requiresConfiguration` aparece com o aviso e SEM
 * botão de carregar: fingir mapa vazio seria dado falso (SG-877 a
 * SG-879 sem os tipos do tenant; SG-882 sem as faixas).
 *
 * Correção RG-Lote3 (08/11/2026): estado de carregando das duas
 * buscas (lista de temáticos e dados do temático) com o `Spinner`
 * de `@components/ui/Alert`, o mesmo padrão das irmãs GIS
 * (GisMap/GisStyles) e das demais páginas — antes a tela ficava
 * estática durante as buscas. Nenhum outro comportamento mudou.
 */
export default function GisThematics() {
  const [thematics, setThematics] = useState<GisThematic[]>([])
  const [selectedKey, setSelectedKey] = useState<string>('')
  const [categoryCode, setCategoryCode] = useState<string>('')
  const [data, setData] = useState<ThematicData | null>(null)
  const [error, setError] = useState<string>('')
  const [loadingThematics, setLoadingThematics] = useState(true)
  const [loadingData, setLoadingData] = useState(false)

  useEffect(() => {
    setLoadingThematics(true)
    listThematics()
      .then((items) => {
        setThematics(items)
        const first = items.find((t) => t.active && !t.requiresConfiguration)
        if (first) {
          setSelectedKey(first.key)
        }
      })
      .catch(() => setError('Não foi possível carregar os temáticos.'))
      .finally(() => setLoadingThematics(false))
  }, [])

  const selected = useMemo(
    () => thematics.find((t) => t.key === selectedKey),
    [thematics, selectedKey],
  )

  useEffect(() => {
    if (!selectedKey || selected?.requiresConfiguration) {
      setData(null)
      setLoadingData(false)
      return
    }
    setLoadingData(true)
    thematicData(selectedKey, categoryCode || undefined)
      .then(setData)
      .catch(() => setError('Não foi possível carregar os dados do temático.'))
      .finally(() => setLoadingData(false))
  }, [selectedKey, categoryCode, selected?.requiresConfiguration])

  return (
    <section aria-label="Mapas temáticos">
      <h1>Mapas temáticos</h1>
      {error && <p role="alert">{error}</p>}
      {loadingThematics || loadingData ? <Spinner /> : null}
      <label>
        Temático
        <select
          value={selectedKey}
          onChange={(event) => {
            setSelectedKey(event.target.value)
            setCategoryCode('')
          }}
        >
          <option value="">Selecione</option>
          {thematics.map((thematic) => (
            <option key={thematic.key} value={thematic.key} disabled={!thematic.active}>
              {thematic.name}
              {thematic.origin === 'TENANT' ? ' (do tenant)' : ''}
            </option>
          ))}
        </select>
      </label>

      {selected?.requiresConfiguration && (
        <p role="alert">
          Este cenário exige configuração do tenant antes do primeiro uso:
          defina os tipos de OS ou as faixas na definição do temático.
        </p>
      )}
      {selected?.description && <p>{selected.description}</p>}

      {selected && selected.sourceKind !== 'SERVICE_ORDER' && (
        <label>
          Categoria (SG-883)
          <input
            value={categoryCode}
            onChange={(event) => setCategoryCode(event.target.value)}
            placeholder="Código da categoria"
          />
        </label>
      )}

      {data && (
        <>
          <h2>Legenda</h2>
          <ul>
            {data.legend.map((entry: LegendEntry) => (
              <li key={entry.key}>
                {entry.label}: {entry.count}
              </li>
            ))}
          </ul>
          <p>
            {data.points.length} pontos
            {data.truncated ? ' (resultado truncado no teto de desenho)' : ''}
          </p>
        </>
      )}
    </section>
  )
}
