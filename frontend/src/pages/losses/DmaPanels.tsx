import { useEffect, useMemo, useState } from 'react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { Tabs } from '@components/ui/Tabs'
import {
  aggregateBalance,
  analyzeNightFlow,
  calculateBalance,
  rankDmcs,
  resolveNightWindow,
  unavailableDmaPanelsPort,
  DMA_PANELS_WIRING_PENDENCY,
  DMA_SERIES_PENDENCY,
  DMA_TARIFF_PENDENCY,
  NIGHT_WINDOW_DEFAULT,
  type DmaBalancePanelData,
  type DmaDistrictSummary,
  type DmaPanelsPort,
  type NightFlowPanelData,
  type RankingPanelData,
} from '@services/losses/dmaPanels'

type TabId = 'balanco' | 'vmn' | 'ranking' | 'distritos'

const TABS = [
  { id: 'balanco', label: 'Balanço hídrico' },
  { id: 'vmn', label: 'Vazão mínima noturna' },
  { id: 'ranking', label: 'Ranking DMC' },
  { id: 'distritos', label: 'Distritos DMA' },
]

function absentCell(value: string | null, absentComponents: string[]): string {
  if (value !== null) return value
  return absentComponents.length > 0 ? `ausente (${absentComponents.join(', ')})` : 'ausente'
}

/**
 * Painéis de perdas do DMA — P-II6-TELA (SÓ painéis, SEM mapa: o mapa
 * temático de perdas é do programa PDCA-GIS/E3).
 *
 * Os três painéis calculam sobre a porta tipada `DmaPanelsPort`, que
 * espelha o núcleo II-6 (DmaModel/ExactWaterBalance/MinimumNightFlow/
 * DmcReturnRanking). Sem fiação (porta por omissão), cada painel
 * mostra o estado vazio honesto com a pendência nomeada — nenhum
 * número é inventado. O ranking mostra o modo vigente como o núcleo
 * o computa: sem tarifa declarada (P-II6-TARIFA, Ciclo 4 do
 * PDCA-GIS), o modo é LOSS_VOLUME_ONLY, declarado no painel.
 *
 * Paridade web↔mobile: padrão responsivo — tabelas com `mobileCards`
 * (cartões no celular, via Table da biblioteca) e abas com rolagem
 * horizontal; nenhuma função é exclusiva do desktop.
 */
export function DmaPanels({ port = unavailableDmaPanelsPort }: { port?: DmaPanelsPort }) {
  const [tab, setTab] = useState<TabId>('balanco')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [balanceData, setBalanceData] = useState<DmaBalancePanelData | null>(null)
  const [nightFlowData, setNightFlowData] = useState<NightFlowPanelData | null>(null)
  const [rankingData, setRankingData] = useState<RankingPanelData | null>(null)
  const [districts, setDistricts] = useState<DmaDistrictSummary[] | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([port.balancePanel(), port.nightFlowPanel(), port.rankingPanel(), port.districts()])
      .then(([balance, nightFlow, ranking, districtList]) => {
        if (cancelled) return
        setBalanceData(balance)
        setNightFlowData(nightFlow)
        setRankingData(ranking)
        setDistricts(districtList)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'erro ao carregar os painéis de perdas')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [port])

  const balanceView = useMemo(() => {
    if (!balanceData) return null
    const members = balanceData.memberInputs.map((input) => calculateBalance(input))
    return { members, aggregate: aggregateBalance(balanceData.definition, members) }
  }, [balanceData])

  const nightFlowView = useMemo(() => {
    if (!nightFlowData) return null
    return analyzeNightFlow(
      nightFlowData.series,
      resolveNightWindow(nightFlowData.window),
      nightFlowData.sampleIntervalMinutes,
      nightFlowData.legitNightUseLPerConnH,
      nightFlowData.connections,
    )
  }, [nightFlowData])

  /** R41-4-17: a fonte não declarou janela — vale o padrão ED-85 (declarado no painel). */
  const nightWindowDefaulted = nightFlowData !== null && !nightFlowData.window

  const rankingView = useMemo(() => {
    if (!rankingData) return null
    return rankDmcs(rankingData.entries, rankingData.tariffPerM3)
  }, [rankingData])

  return (
    <div className="space-y-6">
      <PageHeader
        title="Painéis de perdas do DMA"
        description="Balanço hídrico exato, vazão mínima noturna e ranking de DMC por retorno (II-6). Painéis apenas — o mapa temático de perdas é do programa PDCA-GIS (E3)."
      />
      {error ? (
        <Alert tone="error" title="Não foi possível carregar os painéis de perdas">
          {error}
        </Alert>
      ) : null}
      {loading ? (
        <Spinner label="Carregando painéis de perdas..." />
      ) : (
        <Tabs tabs={TABS} active={tab} onChange={(id) => setTab(id as TabId)} idPrefix="dma-panels" label="Painéis de perdas do DMA">
          {tab === 'balanco' ? (
            balanceView ? (
              <div className="space-y-6">
                <Card>
                  <CardContent className="pt-6">
                    <h2 className="text-lg font-semibold">
                      DMA {balanceView.aggregate.dmaCode} — setor {balanceView.aggregate.sectorCode}
                    </h2>
                    <p className="mt-2 text-sm">
                      Período {balanceView.aggregate.periodStart} a {balanceView.aggregate.periodEnd} —{' '}
                      {balanceView.aggregate.memberCount} DMC(s) membros. Agregado = soma exata dos balanços dos membros.
                    </p>
                    <Table caption="Agregado do DMA (m³)" mobileCards>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Entrada líquida (m³)</TableHead>
                          <TableHead>Faturado total (m³)</TableHead>
                          <TableHead>Consumo autorizado (m³)</TableHead>
                          <TableHead>Perdas totais (m³)</TableHead>
                          <TableHead>Perdas aparentes (m³)</TableHead>
                          <TableHead>Perdas reais (m³)</TableHead>
                          <TableHead>Água não faturada (m³)</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        <TableRow>
                          <TableCell label="Entrada líquida (m³)">{balanceView.aggregate.netInputM3}</TableCell>
                          <TableCell label="Faturado total (m³)">{balanceView.aggregate.billedTotalM3}</TableCell>
                          <TableCell label="Consumo autorizado (m³)">{balanceView.aggregate.authorizedConsumptionM3}</TableCell>
                          <TableCell label="Perdas totais (m³)">{balanceView.aggregate.totalLossesM3}</TableCell>
                          <TableCell label="Perdas aparentes (m³)">
                            {absentCell(balanceView.aggregate.apparentLossesM3, balanceView.aggregate.absentComponents)}
                          </TableCell>
                          <TableCell label="Perdas reais (m³)">
                            {absentCell(balanceView.aggregate.realLossesM3, balanceView.aggregate.absentComponents)}
                          </TableCell>
                          <TableCell label="Água não faturada (m³)">{balanceView.aggregate.nonRevenueWaterM3}</TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                    {balanceView.aggregate.absentComponents.length > 0 ? (
                      <p className="mt-2 text-sm">
                        Componentes ausentes (declaradas, nunca estimadas): {balanceView.aggregate.absentComponents.join(', ')}
                      </p>
                    ) : null}
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-6">
                    <h2 className="text-lg font-semibold">Balanço por DMC</h2>
                    <Table caption="Balanço exato por DMC no período (m³)" mobileCards>
                      <TableHeader>
                        <TableRow>
                          <TableHead>DMC</TableHead>
                          <TableHead>Entrada líquida (m³)</TableHead>
                          <TableHead>Faturado total (m³)</TableHead>
                          <TableHead>Consumo autorizado (m³)</TableHead>
                          <TableHead>Perdas totais (m³)</TableHead>
                          <TableHead>Perdas reais (m³)</TableHead>
                          <TableHead>Água não faturada (m³)</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {balanceView.members.map((b) => (
                          <TableRow key={b.dmcId}>
                            <TableCell label="DMC">{b.dmcCode}</TableCell>
                            <TableCell label="Entrada líquida (m³)">{b.netInputM3}</TableCell>
                            <TableCell label="Faturado total (m³)">{b.billedTotalM3}</TableCell>
                            <TableCell label="Consumo autorizado (m³)">{b.authorizedConsumptionM3}</TableCell>
                            <TableCell label="Perdas totais (m³)">{b.totalLossesM3}</TableCell>
                            <TableCell label="Perdas reais (m³)">{absentCell(b.realLossesM3, b.absentComponents)}</TableCell>
                            <TableCell label="Água não faturada (m³)">{b.nonRevenueWaterM3}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CardContent>
                </Card>
              </div>
            ) : (
              <Alert tone="info" title="Sem dados de balanço">
                A fonte do balanço ainda não está fiada ({DMA_PANELS_WIRING_PENDENCY} [A DEFINIR] — nenhuma rota HTTP
                afirmada). Quando a fiação entregar os balanços dos DMCs, este painel mostra o balanço exato e o
                agregado do DMA.
              </Alert>
            )
          ) : null}

          {tab === 'vmn' ? (
            nightFlowView ? (
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Vazão mínima noturna</h2>
                  <p className="mt-2 text-sm">
                    Janela {nightFlowView.window.start}–{nightFlowView.window.end} — cadência de{' '}
                    {nightFlowView.sampleIntervalMinutes} min — {nightFlowView.samplesInWindow} amostra(s) na janela em{' '}
                    {nightFlowView.nightsEvaluated} noite(s) avaliada(s).
                  </p>
                  {nightWindowDefaulted ? (
                    <p className="mt-2 text-sm">
                      Janela padrão do aceite da ED-85 ({NIGHT_WINDOW_DEFAULT.start}–{NIGHT_WINDOW_DEFAULT.end}): a
                      fonte não declarou janela.
                    </p>
                  ) : null}
                  <p className="mt-2">
                    Desfecho: <Badge>{nightFlowView.status}</Badge>
                  </p>
                  {nightFlowView.status === 'COMPLETE' ? (
                    <ul className="mt-2 list-disc pl-5">
                      <li>VMN bruta: {nightFlowView.minimumNightFlowLPerH} L/h</li>
                      <li>
                        VMN líquida:{' '}
                        {nightFlowView.netNightFlowLPerH === null
                          ? `ausente (${nightFlowView.absentComponents.join(', ')}) — consumo noturno legítimo não declarado`
                          : `${nightFlowView.netNightFlowLPerH} L/h`}
                      </li>
                      {nightFlowView.legitExceedsMinimum ? (
                        <li>Consumo legítimo declarado excede o mínimo medido (VMN líquida negativa, não truncada).</li>
                      ) : null}
                    </ul>
                  ) : nightFlowView.status === 'INCOMPLETE_COVERAGE' ? (
                    <p className="mt-2 text-sm">
                      Cobertura incompleta nas noites: {nightFlowView.incompleteNights.join(', ')}. A VMN não é
                      devolvida sobre janela parcial.
                    </p>
                  ) : (
                    <p className="mt-2 text-sm">Nenhuma amostra dentro da janela noturna.</p>
                  )}
                </CardContent>
              </Card>
            ) : (
              <Alert tone="info" title="Sem série de vazões">
                As séries vivas do DMA são integração ({DMA_SERIES_PENDENCY}); a fiação é {DMA_PANELS_WIRING_PENDENCY}{' '}
                [A DEFINIR]. Sem série, não há vazão mínima noturna para mostrar — nenhum valor é estimado.
              </Alert>
            )
          ) : null}

          {tab === 'ranking' ? (
            rankingView ? (
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Ranking de DMC por retorno</h2>
                  <p className="mt-2 text-sm">
                    Modo vigente: <Badge>{rankingView.mode}</Badge>{' '}
                    {rankingView.mode === 'LOSS_VOLUME_ONLY'
                      ? `— tarifa ${DMA_TARIFF_PENDENCY} [A DEFINIR] (Ciclo 4 do PDCA-GIS): o ranking sai por volume de perdas, sem preço presumido.`
                      : `— tarifa declarada de ${rankingView.tariffPerM3} por m³.`}
                  </p>
                  <Table caption="Ranking de DMC por retorno" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Posição</TableHead>
                        <TableHead>DMC</TableHead>
                        <TableHead>Índice</TableHead>
                        <TableHead>Base do índice</TableHead>
                        <TableHead>Perdas recuperáveis (m³)</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {rankingView.ranked.map((r) => (
                        <TableRow key={r.dmcCode}>
                          <TableCell label="Posição">{r.position}</TableCell>
                          <TableCell label="DMC">{r.dmcCode}</TableCell>
                          <TableCell label="Índice">{r.index}</TableCell>
                          <TableCell label="Base do índice">{r.basis}</TableCell>
                          <TableCell label="Perdas recuperáveis (m³)">{r.recoverableLossesM3}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            ) : (
              <Alert tone="info" title="Sem dados para o ranking">
                A fonte do ranking ainda não está fiada ({DMA_PANELS_WIRING_PENDENCY} [A DEFINIR]). A tarifa do ranking
                é {DMA_TARIFF_PENDENCY} (Ciclo 4 do PDCA-GIS): sem tarifa declarada, o painel mostrará o ranking por
                volume de perdas com o modo LOSS_VOLUME_ONLY declarado.
              </Alert>
            )
          ) : null}

          {tab === 'distritos' ? (
            districts && districts.length > 0 ? (
              <Card>
                <CardContent className="pt-6">
                  <h2 className="text-lg font-semibold">Distritos DMA (GIS-03)</h2>
                  <Table caption="Distritos DMA" mobileCards>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Código</TableHead>
                        <TableHead>Nome</TableHead>
                        <TableHead>Estado</TableHead>
                        <TableHead>Ligações</TableHead>
                        <TableHead>População servida</TableHead>
                        <TableHead>Área (km²)</TableHead>
                        <TableHead>Pressão média (m)</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {districts.map((d) => (
                        <TableRow key={d.code}>
                          <TableCell label="Código">{d.code}</TableCell>
                          <TableCell label="Nome">{d.name}</TableCell>
                          <TableCell label="Estado">{d.status}</TableCell>
                          <TableCell label="Ligações">{d.connectionsCount ?? '—'}</TableCell>
                          <TableCell label="População servida">{d.populationServed ?? '—'}</TableCell>
                          <TableCell label="Área (km²)">{d.areaKm2 ?? '—'}</TableCell>
                          <TableCell label="Pressão média (m)">{d.avgPressureM ?? '—'}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            ) : (
              <Alert tone="info" title="Sem distritos para listar">
                A fonte dos distritos DMA ainda não está fiada ({DMA_PANELS_WIRING_PENDENCY} [A DEFINIR] — nenhuma rota
                HTTP afirmada).
              </Alert>
            )
          ) : null}
        </Tabs>
      )}
    </div>
  )
}
