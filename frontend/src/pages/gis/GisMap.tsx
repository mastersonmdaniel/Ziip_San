import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import maplibregl, { type Map as MapLibreMap } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { Alert, Spinner } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { Checkbox, Field, Input, Select } from '@components/ui/Field'
import { PageHeader } from '@components/ui/PageHeader'
import { errorMessage } from '@services/registry/errors'
import {
  gisMap,
  type FeatureDetail,
  type SearchHit,
} from '@services/gis/map'
import {
  collectIdentifiedFeatures,
  hasUnidentifiedLayer,
  type IdentifiedCandidate,
} from '@services/gis/identify'
import {
  clickOs,
  clickTargetForFeature,
  type ClickPayloadView,
  type CreatedOrderView,
  type PropagationMode,
} from '@services/gis/clickOs'
import {
  computeLayerVisibility,
  readMapTokens,
  resolveTokenColors,
  type StyleLayerLike,
} from '@services/gis/mapStyle'
import {
  MY_LOCATION_ZOOM,
  requestMyLocation,
} from '@services/gis/myLocation'
import {
  formatArea,
  formatDistance,
  lngLatBounds,
  pathDistanceM,
  polygonAreaM2,
} from '@utils/measure'

/**
 * GIS-06 (§10.3): mapa web de consulta — MapLibre sobre os tiles
 * vetoriais da api, estilo gerado da fonte única (gis_style) com os
 * tokens map-* resolvidos no tema corrente, camadas por grupo com
 * rótulos, mapas base, cliente WMS (fontes cadastradas pelo
 * tenant), identificação com a ficha da feição, medição de
 * distância e área em tela e pesquisas por atributo, endereço e
 * coordenada (geográfica e UTM SIRGAS 2000).
 *
 * O tema vem da prop (a fiação com o ThemeProvider do app é da
 * integração — ver README-ROTAS.md): trocar o tema regera a
 * resolução dos tokens sobre o documento original, sem novo
 * download do estilo.
 *
 * Ciclo 2 do PDCA GIS: o clique identifica TODAS as feições com
 * ficha sob o ponto (identify.ts) — uma só identifica directo,
 * várias abrem a lista "Elementos neste ponto" para o utilizador
 * escolher — e a ficha de nó/arco traz a secção de OS por clique
 * (II-1) sobre o cliente clickOs.ts. A geometria nasce só do
 * clique/rato no mapa: nenhum botão de parâmetros fixos.
 */
export function GisMap({ theme = 'light' }: { theme?: 'light' | 'dark' }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapLibreMap | null>(null)
  const rawStyleRef = useRef<Record<string, unknown> | null>(null)

  const [visibleKeys, setVisibleKeys] = useState<ReadonlySet<string>>(new Set())
  const [labelsVisible, setLabelsVisible] = useState(true)
  const [basemapKey, setBasemapKey] = useState<string>('')
  const [activeWms, setActiveWms] = useState<ReadonlySet<string>>(new Set())
  const [measureMode, setMeasureMode] = useState<'off' | 'distance' | 'area'>('off')
  const [measurePoints, setMeasurePoints] = useState<{ lng: number; lat: number }[]>([])
  const [detail, setDetail] = useState<FeatureDetail | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [candidates, setCandidates] = useState<IdentifiedCandidate[]>([])
  const [clickServiceType, setClickServiceType] = useState('')
  const [clickMode, setClickMode] = useState<PropagationMode>('DOWNSTREAM')
  const [clickPayload, setClickPayload] = useState<ClickPayloadView | null>(null)
  const [clickCreated, setClickCreated] = useState<CreatedOrderView | null>(null)
  const [clickError, setClickError] = useState<string | null>(null)
  const [clickBusy, setClickBusy] = useState<'preview' | 'create' | null>(null)
  const [hits, setHits] = useState<SearchHit[]>([])
  const [searchError, setSearchError] = useState<string | null>(null)
  const [locating, setLocating] = useState(false)
  const [myLocationError, setMyLocationError] = useState<string | null>(null)
  const [myLocationInfo, setMyLocationInfo] = useState<string | null>(null)

  const [attributeQuery, setAttributeQuery] = useState('')
  const [addressQuery, setAddressQuery] = useState('')
  const [coordLon, setCoordLon] = useState('')
  const [coordLat, setCoordLat] = useState('')
  const [utmEasting, setUtmEasting] = useState('')
  const [utmNorthing, setUtmNorthing] = useState('')
  const [utmZone, setUtmZone] = useState('22')

  const layersQuery = useQuery({ queryKey: ['gis', 'map', 'layers'], queryFn: gisMap.layers })
  const basemapsQuery = useQuery({
    queryKey: ['gis', 'map', 'basemaps'],
    queryFn: gisMap.basemaps,
  })
  const wmsQuery = useQuery({
    queryKey: ['gis', 'map', 'wms-sources'],
    queryFn: gisMap.wmsSources,
  })
  const styleQuery = useQuery({
    queryKey: ['gis', 'map', 'style', theme],
    queryFn: () => gisMap.style(theme),
  })

  // Todas as camadas começam ligadas quando a lista chega.
  useEffect(() => {
    if (layersQuery.data) {
      setVisibleKeys(new Set(layersQuery.data.filter((l) => l.active).map((l) => l.key)))
    }
  }, [layersQuery.data])
  useEffect(() => {
    if (basemapsQuery.data && !basemapKey) {
      const def = basemapsQuery.data.find((b) => b.isDefault) ?? basemapsQuery.data[0]
      if (def) setBasemapKey(def.key)
    }
  }, [basemapsQuery.data, basemapKey])

  // Criação do mapa e troca de tema (re resolve os tokens).
  useEffect(() => {
    if (!styleQuery.data || !containerRef.current) return
    rawStyleRef.current = styleQuery.data
    const resolved = resolveTokenColors(
      structuredClone(styleQuery.data),
      readMapTokens(),
    ) as maplibregl.StyleSpecification
    if (!mapRef.current) {
      const map = new maplibregl.Map({
        container: containerRef.current,
        style: resolved,
        // Centro neutro (Brasil): a sede do tenant centra o mapa
        // pelas pesquisas e pela extensão das camadas na integração,
        // não por uma coordenada chutada nesta tela.
        center: [-52.0, -14.5],
        zoom: 4,
        attributionControl: { compact: true },
      })
      map.addControl(new maplibregl.NavigationControl(), 'top-right')
      map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left')
      map.on('click', (event) => {
        // O handler é registrado uma vez na criação do mapa: o modo
        // de medição corrente é lido do dataset do contêiner (a
        // alternativa, re-registrar o handler a cada troca de modo,
        // duplicaria cliques).
        const mode = containerRef.current?.dataset.measureMode ?? 'off'
        if (mode !== 'off') {
          setMeasurePoints((points) => [...points, event.lngLat])
          return
        }
        const features = map.queryRenderedFeatures(event.point)
        // Identificação multi-camadas (Ciclo 2): TODAS as feições
        // com ficha sob o ponto, na ordem de renderização — antes
        // ficava só a primeira (features.find) e as restantes
        // camadas activas sob o ponto ficavam invisíveis.
        const identified = collectIdentifiedFeatures(features)
        if (identified.length === 1) {
          const only = identified[0]
          setCandidates([])
          setDetailError(null)
          gisMap
            .identify(only.table, only.id)
            .then(setDetail)
            .catch((e) => setDetailError(errorMessage(e)))
        } else if (identified.length > 1) {
          setDetail(null)
          setDetailError(null)
          setCandidates(identified)
        } else if (hasUnidentifiedLayer(features)) {
          // Correção pós-revisão B3: camada sem ficha GIS própria
          // (contexto importado e imóveis) não é silêncio — avisa.
          // Imóvel se consulta no cadastro comercial; o contexto é
          // referência de fundo (ver README-ROTAS.md).
          setCandidates([])
          setDetail(null)
          setDetailError(
            'Esta camada não tem ficha GIS: imóveis são consultados no cadastro '
              + 'comercial e o contexto importado é referência de fundo.',
          )
        } else {
          setCandidates([])
        }
      })
      mapRef.current = map
    } else {
      mapRef.current.setStyle(resolved)
    }
  }, [styleQuery.data, theme])

  // Visibilidade de camadas, rótulos, base e WMS.
  useEffect(() => {
    const map = mapRef.current
    if (!map || !map.getStyle()) return
    const apply = () => {
      const layers = (map.getStyle().layers ?? []) as unknown as StyleLayerLike[]
      const visibility = computeLayerVisibility(layers, visibleKeys, labelsVisible)
      for (const [layerId, value] of Object.entries(visibility)) {
        if (map.getLayer(layerId)) map.setLayoutProperty(layerId, 'visibility', value)
      }
      for (const layer of layers) {
        if (layer.id.startsWith('basemap-') && map.getLayer(layer.id)) {
          map.setLayoutProperty(
            layer.id,
            'visibility',
            layer.id === `basemap-${basemapKey}` ? 'visible' : 'none',
          )
        }
        if (layer.id.startsWith('wms-') && map.getLayer(layer.id)) {
          map.setLayoutProperty(
            layer.id,
            'visibility',
            activeWms.has(layer.id.slice('wms-'.length)) ? 'visible' : 'none',
          )
        }
      }
    }
    if (map.loaded()) apply()
    else map.once('load', apply)
  }, [visibleKeys, labelsVisible, basemapKey, activeWms, styleQuery.data])

  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.dataset.measureMode = measureMode
    }
  }, [measureMode])

  // A pré-visualização/criação da OS por clique pertence à ficha
  // aberta: trocar de elemento invalida o payload anterior.
  useEffect(() => {
    setClickPayload(null)
    setClickCreated(null)
    setClickError(null)
  }, [detail?.featureTable, detail?.id])

  function identifyCandidate(candidate: IdentifiedCandidate) {
    setCandidates([])
    setDetailError(null)
    gisMap
      .identify(candidate.table, candidate.id)
      .then(setDetail)
      .catch((e) => setDetailError(errorMessage(e)))
  }

  function clickOsInput() {
    if (!detail) return null
    const target = clickTargetForFeature(detail.featureTable, detail.id)
    if (!target) return null
    const code = clickServiceType.trim()
    return {
      arcId: target.arcId,
      nodeId: target.nodeId,
      mode: clickMode,
      // Vazio não é erro: o servidor nomeia a falta no payload.
      serviceTypeCode: code.length > 0 ? code : null,
    }
  }

  async function previewClickOs() {
    const input = clickOsInput()
    if (!input) return
    setClickBusy('preview')
    setClickError(null)
    setClickCreated(null)
    try {
      setClickPayload(await clickOs.payload(input))
    } catch (error) {
      setClickPayload(null)
      setClickError(errorMessage(error))
    } finally {
      setClickBusy(null)
    }
  }

  async function createClickOs() {
    const input = clickOsInput()
    if (!input) return
    setClickBusy('create')
    setClickError(null)
    try {
      const created = await clickOs.create(input)
      setClickCreated(created)
      setClickPayload(created.payload)
    } catch (error) {
      setClickError(errorMessage(error))
    } finally {
      setClickBusy(null)
    }
  }

  function toggleLayer(key: string, checked: boolean) {
    setVisibleKeys((prev) => {
      const next = new Set(prev)
      if (checked) next.add(key)
      else next.delete(key)
      return next
    })
  }

  function toggleWms(id: string, checked: boolean) {
    setActiveWms((prev) => {
      const next = new Set(prev)
      if (checked) next.add(id)
      else next.delete(id)
      return next
    })
  }

  async function runSearch(kind: 'attribute' | 'address' | 'near' | 'utm', e: FormEvent) {
    e.preventDefault()
    setSearchError(null)
    try {
      if (kind === 'attribute') setHits(await gisMap.searchAttribute(attributeQuery))
      else if (kind === 'address') setHits(await gisMap.searchAddress(addressQuery))
      else if (kind === 'near')
        setHits(
          await gisMap.searchNear(
            Number(coordLon.replace(',', '.')),
            Number(coordLat.replace(',', '.')),
          ),
        )
      else
        setHits(
          await gisMap.searchUtm(
            Number(utmEasting.replace(',', '.')),
            Number(utmNorthing.replace(',', '.')),
            Number(utmZone),
            true,
          ),
        )
    } catch (error) {
      setSearchError(errorMessage(error))
    }
  }

  function goToHit(hit: SearchHit) {
    mapRef.current?.flyTo({ center: [hit.lon, hit.lat], zoom: 17 })
  }

  // Minha localização (lacuna E2 do anexo à Rodada 04, Ciclo 1 do
  // PDCA GIS): centra o mapa na posição do browser. A forma mobile
  // é esta mesma página no navegador do celular; o app de campo usa
  // o GNSS próprio (campo/src/gis/gnss.ts), não esta via.
  async function locateMe() {
    setLocating(true)
    setMyLocationError(null)
    setMyLocationInfo(null)
    try {
      const fix = await requestMyLocation(navigator.geolocation)
      mapRef.current?.flyTo({
        center: [fix.lng, fix.lat],
        zoom: MY_LOCATION_ZOOM,
      })
      setMyLocationInfo(
        fix.accuracyM !== null
          ? `Posição obtida — precisão declarada pelo aparelho: ${formatDistance(fix.accuracyM)}.`
          : 'Posição obtida — o aparelho não declarou precisão.',
      )
    } catch (error) {
      setMyLocationError(errorMessage(error))
    } finally {
      setLocating(false)
    }
  }

  // Enquadrar resultados: bounds dos pontos que a pesquisa já
  // devolveu (as camadas não expõem extensão — ver lngLatBounds).
  function frameHits() {
    const map = mapRef.current
    if (!map || hits.length === 0) return
    const bounds = lngLatBounds(hits.map((h) => ({ lng: h.lon, lat: h.lat })))
    if (!bounds) return
    if (bounds.west === bounds.east && bounds.south === bounds.north) {
      map.flyTo({ center: [bounds.west, bounds.south], zoom: MY_LOCATION_ZOOM })
    } else {
      map.fitBounds(
        [
          [bounds.west, bounds.south],
          [bounds.east, bounds.north],
        ],
        { padding: 40, maxZoom: MY_LOCATION_ZOOM },
      )
    }
  }

  const groups = new Map<string, NonNullable<typeof layersQuery.data>>()
  for (const layer of layersQuery.data ?? []) {
    const list = groups.get(layer.groupLabel) ?? []
    list.push(layer)
    groups.set(layer.groupLabel, list)
  }

  // Alvo da OS por clique da ficha aberta: só nó/arco da rede têm
  // alvo no núcleo II-1; as restantes fichas não mostram a secção.
  const clickTarget = detail
    ? clickTargetForFeature(detail.featureTable, detail.id)
    : null

  const measureText =
    measureMode === 'distance'
      ? `Distância em tela: ${formatDistance(pathDistanceM(measurePoints))}`
      : measureMode === 'area'
        ? `Área em tela: ${formatArea(polygonAreaM2(measurePoints))}`
        : null

  if (layersQuery.isLoading || styleQuery.isLoading) return <Spinner />
  if (layersQuery.isError) return <Alert>{errorMessage(layersQuery.error)}</Alert>
  if (styleQuery.isError) return <Alert>{errorMessage(styleQuery.error)}</Alert>

  return (
    <div>
      <PageHeader
        title="Mapa do cadastro técnico"
        description="Consulta sobre tiles vetoriais; o detalhe vem da ficha da feição."
      />
      <div style={{ display: 'flex', gap: '1rem', alignItems: 'flex-start' }}>
        <div style={{ flex: '1 1 auto', minWidth: 0 }}>
          <div
            ref={containerRef}
            data-testid="gis-map"
            role="application"
            aria-label="Mapa do cadastro técnico"
            style={{ height: '560px', width: '100%' }}
          />
          {measureText && (
            <Alert>
              {measureText} ({measurePoints.length} pontos) — medição em tela, não é a
              extensão oficial da rede.
            </Alert>
          )}
          {detailError && <Alert>{detailError}</Alert>}
          {candidates.length > 1 && (
            <Card>
              <CardContent>
                <strong>Elementos neste ponto</strong>
                <p>
                  Mais do que um elemento com ficha está sob este ponto do mapa.
                  Escolha qual quer identificar:
                </p>
                <ul aria-label="Elementos neste ponto">
                  {candidates.map((candidate) => (
                    <li key={`${candidate.table}-${candidate.id}`}>
                      <Button onClick={() => identifyCandidate(candidate)}>
                        {candidate.code ?? candidate.id} — {candidate.table}
                      </Button>
                    </li>
                  ))}
                </ul>
                <Button onClick={() => setCandidates([])}>Fechar lista</Button>
              </CardContent>
            </Card>
          )}
          {detail && (
            <Card>
              <CardContent>
                <strong>
                  Ficha — {detail.typeLabel} {detail.code}
                </strong>
                <p>
                  Estado: {detail.lifecycleStatus}
                  {detail.accuracyStatus ? ` · Precisão: ${detail.accuracyStatus}` : ''}
                  {detail.serviceConnectionId
                    ? ` · Ligação: ${detail.serviceConnectionId}`
                    : ''}
                </p>
                {!detail.personalDataVisible && (
                  <p>Dados pessoais ocultos: exige a permissão gis.view-personal.</p>
                )}
                <dl>
                  {Object.entries(detail.attributes).map(([key, value]) => (
                    <div key={key}>
                      <dt>{key}</dt>
                      <dd>{String(value)}</dd>
                    </div>
                  ))}
                </dl>
                {clickTarget && (
                  <fieldset>
                    <legend>Ordem de serviço por clique</legend>
                    <Field
                      label="Tipo de serviço (código do catálogo do tenant)"
                      id="click-os-service-type"
                      hint="Vazio não é erro: o servidor nomeia a falta na pré-visualização."
                    >
                      {(aria) => (
                        <Input
                          {...aria}
                          value={clickServiceType}
                          onChange={(e) => {
                            setClickServiceType(e.target.value)
                            setClickPayload(null)
                            setClickCreated(null)
                          }}
                        />
                      )}
                    </Field>
                    <Field label="Modo de propagação" id="click-os-mode">
                      {(aria) => (
                        <Select
                          {...aria}
                          value={clickMode}
                          onChange={(e) => {
                            setClickMode(e.target.value as PropagationMode)
                            setClickPayload(null)
                            setClickCreated(null)
                          }}
                        >
                          <option value="DOWNSTREAM">A jusante</option>
                          <option value="UPSTREAM">A montante</option>
                          <option value="BOTH">A jusante e a montante</option>
                          <option value="ISOLATION">Isolamento por válvulas</option>
                        </Select>
                      )}
                    </Field>
                    <Button
                      onClick={() => void previewClickOs()}
                      disabled={clickBusy !== null}
                    >
                      {clickBusy === 'preview'
                        ? 'A pré-visualizar…'
                        : 'Pré-visualizar OS por clique'}
                    </Button>
                    {clickError && <Alert>{clickError}</Alert>}
                    {clickPayload && (
                      <div>
                        <p>
                          {clickPayload.readyToSubmit
                            ? 'Payload pronto para criar a OS.'
                            : 'Payload ainda não está pronto para criar a OS.'}
                        </p>
                        <p>
                          UCs afectadas: {clickPayload.affectedUnitIds.length}
                          {' · '}Economias: {clickPayload.totalEconomies}
                          {' · '}Nós: {clickPayload.affectedNodeIds.length}
                          {' · '}Arcos: {clickPayload.affectedArcIds.length}
                          {' · '}Ligações: {clickPayload.affectedConnectionIds.length}
                        </p>
                        <p>
                          Desfecho: {clickPayload.outcome} · Modo: {clickPayload.mode}
                          {clickPayload.truncated
                            ? ' · Propagação truncada por limite'
                            : ''}
                        </p>
                        {clickPayload.missingForSubmit.length > 0 && (
                          <>
                            <p>Faltas para submeter:</p>
                            <ul>
                              {clickPayload.missingForSubmit.map((missing) => (
                                <li key={missing}>{missing}</li>
                              ))}
                            </ul>
                          </>
                        )}
                      </div>
                    )}
                    {clickPayload?.readyToSubmit && !clickCreated && (
                      <Button
                        onClick={() => void createClickOs()}
                        disabled={clickBusy !== null}
                      >
                        {clickBusy === 'create'
                          ? 'A criar…'
                          : 'Criar OS a partir deste elemento'}
                      </Button>
                    )}
                    {clickCreated && <p>OS criada: {clickCreated.orderId}</p>}
                  </fieldset>
                )}
                <Button onClick={() => setDetail(null)}>Fechar ficha</Button>
              </CardContent>
            </Card>
          )}
        </div>

        <aside aria-label="Painel do mapa" style={{ flex: '0 0 300px' }}>
          <Card>
            <CardContent>
              <fieldset>
                <legend>Camadas</legend>
                {[...groups.entries()].map(([group, layers]) => (
                  <fieldset key={group}>
                    <legend>{group}</legend>
                    {layers.map((layer) => (
                      <Checkbox
                        key={layer.key}
                        id={`layer-${layer.key}`}
                        label={`${layer.label}${layer.personal ? ' (pessoal)' : ''}`}
                        checked={visibleKeys.has(layer.key)}
                        onChange={(e) => toggleLayer(layer.key, e.target.checked)}
                      />
                    ))}
                  </fieldset>
                ))}
                <Checkbox
                  id="labels-visible"
                  label="Rótulos"
                  checked={labelsVisible}
                  onChange={(e) => setLabelsVisible(e.target.checked)}
                />
              </fieldset>

              <Field label="Mapa base" id="basemap-select">
                {(aria) => (
                  <Select
                    {...aria}
                    value={basemapKey}
                    onChange={(e) => setBasemapKey(e.target.value)}
                  >
                    {(basemapsQuery.data ?? []).map((b) => (
                      <option key={b.key} value={b.key}>
                        {b.label}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>

              <fieldset>
                <legend>Fontes WMS</legend>
                {(wmsQuery.data ?? []).length === 0 && <p>Nenhuma fonte cadastrada.</p>}
                {(wmsQuery.data ?? []).map((source) => (
                  <Checkbox
                    key={source.id}
                    id={`wms-${source.id}`}
                    label={source.label}
                    checked={activeWms.has(source.id)}
                    onChange={(e) => toggleWms(source.id, e.target.checked)}
                  />
                ))}
              </fieldset>

              <fieldset>
                <legend>Localização</legend>
                <Button onClick={() => void locateMe()} disabled={locating}>
                  {locating ? 'A localizar…' : 'Minha localização'}
                </Button>
                {myLocationInfo && <p>{myLocationInfo}</p>}
                {myLocationError && <Alert>{myLocationError}</Alert>}
              </fieldset>

              <fieldset>
                <legend>Medição</legend>
                <Button
                  onClick={() => {
                    setMeasureMode(measureMode === 'distance' ? 'off' : 'distance')
                    setMeasurePoints([])
                  }}
                  aria-pressed={measureMode === 'distance'}
                >
                  Distância
                </Button>{' '}
                <Button
                  onClick={() => {
                    setMeasureMode(measureMode === 'area' ? 'off' : 'area')
                    setMeasurePoints([])
                  }}
                  aria-pressed={measureMode === 'area'}
                >
                  Área
                </Button>
              </fieldset>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <form onSubmit={(e) => void runSearch('attribute', e)}>
                <Field label="Pesquisa por atributo (código ou nome)" id="search-attribute">
                  {(aria) => (
                    <Input
                      {...aria}
                      value={attributeQuery}
                      onChange={(e) => setAttributeQuery(e.target.value)}
                    />
                  )}
                </Field>
                <Button type="submit">Pesquisar atributo</Button>
              </form>
              <form onSubmit={(e) => void runSearch('address', e)}>
                <Field label="Pesquisa por endereço (logradouro)" id="search-address">
                  {(aria) => (
                    <Input
                      {...aria}
                      value={addressQuery}
                      onChange={(e) => setAddressQuery(e.target.value)}
                    />
                  )}
                </Field>
                <Button type="submit">Pesquisar endereço</Button>
              </form>
              <form onSubmit={(e) => void runSearch('near', e)}>
                <Field label="Pesquisa por coordenada — Longitude" id="coord-lon">
                  {(aria) => (
                    <Input
                      {...aria}
                      inputMode="decimal"
                      value={coordLon}
                      onChange={(e) => setCoordLon(e.target.value)}
                    />
                  )}
                </Field>
                <Field label="Pesquisa por coordenada — Latitude" id="coord-lat">
                  {(aria) => (
                    <Input
                      {...aria}
                      inputMode="decimal"
                      value={coordLat}
                      onChange={(e) => setCoordLat(e.target.value)}
                    />
                  )}
                </Field>
                <Button type="submit">Pesquisar por coordenada</Button>
              </form>
              <form onSubmit={(e) => void runSearch('utm', e)}>
                <Field label="UTM — Leste (E)" id="utm-easting">
                  {(aria) => (
                    <Input
                      {...aria}
                      inputMode="decimal"
                      value={utmEasting}
                      onChange={(e) => setUtmEasting(e.target.value)}
                    />
                  )}
                </Field>
                <Field label="UTM — Norte (N)" id="utm-northing">
                  {(aria) => (
                    <Input
                      {...aria}
                      inputMode="decimal"
                      value={utmNorthing}
                      onChange={(e) => setUtmNorthing(e.target.value)}
                    />
                  )}
                </Field>
                <Field label="Fuso UTM (SIRGAS 2000, hemisfério sul)" id="utm-zone">
                  {(aria) => (
                    <Input
                      {...aria}
                      inputMode="numeric"
                      value={utmZone}
                      onChange={(e) => setUtmZone(e.target.value)}
                    />
                  )}
                </Field>
                <Button type="submit">Pesquisar por UTM</Button>
              </form>
              {searchError && <Alert>{searchError}</Alert>}
              {hits.length > 0 && (
                <Button onClick={frameHits}>Enquadrar resultados</Button>
              )}
              {hits.length > 0 && (
                <ul aria-label="Resultados da pesquisa">
                  {hits.map((hit) => (
                    <li key={`${hit.featureTable}-${hit.id}`}>
                      <Button onClick={() => goToHit(hit)}>
                        {hit.code ?? hit.name ?? hit.id} — {hit.typeCode}
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </aside>
      </div>
    </div>
  )
}
