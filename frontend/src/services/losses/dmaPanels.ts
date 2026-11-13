/**
 * Porta tipada + lógica pura dos painéis de perdas do DMA — P-II6-TELA
 * (só painéis; o MAPA temático de perdas é do programa PDCA-GIS/E3 e
 * fica FORA deste pacote — nenhum ficheiro de mapa é tocado).
 *
 * Espelho fiel dos records e das classes de cálculo do núcleo II-6 na
 * main (intelligence/ziipsan-losses):
 *  - `dma/DmaModel.java` — DmaDefinition, DmcPeriodInputs,
 *    DmcPeriodBalance, DmaPeriodBalance, FlowSample;
 *  - `dma/ExactWaterBalance.java` — balanço hídrico exato + agregado;
 *  - `dma/MinimumNightFlow.java` — vazão mínima noturna (VMN);
 *  - `dma/DmcReturnRanking.java` — ranking de DMC por retorno;
 *  - `dmc/LossParameters.java` — NightWindow (padrão do aceite da
 *    ED-85: 02:00–04:00) e chaves de parâmetros do tenant;
 *  - `domain/DmaDistrict.java` — distrito DMA persistido (GIS-03).
 *
 * Volumes são strings decimais (exatidão BigDecimal — ver decimal.ts).
 * Componente que a base não expõe entra como `null` (= Optional vazio
 * do Java) e sai DECLARADA ausente no resultado — nunca estimada.
 *
 * NENHUMA rota HTTP é afirmada: o núcleo II-6 não tem controlador REST
 * na main. A fiação HTTP é a pendência P-II6-FIACAO [A DEFINIR]; as
 * séries vivas são a pendência P-II6-SERIES (integração). A porta
 * por omissão (`unavailableDmaPanelsPort`) devolve `null` em tudo e a
 * tela mostra o estado vazio honesto com a pendência nomeada.
 */
import { DEC_ZERO, dec, decAdd, decCmp, decMul, decSign, decSub, decToString, type Decimal } from './decimal'

// ---------------------------------------------------------------------------
// Espelhos dos records Java (DmaModel.java)
// ---------------------------------------------------------------------------

/** DmaModel.DmaDefinition — agrupamento nomeado de DMCs do mesmo setor. */
export interface DmaDefinition {
  dmaCode: string
  sectorCode: string
  memberDmcCodes: string[]
}

/**
 * DmaModel.DmcPeriodInputs — entradas do balanço de UM DMC num período
 * [periodStart, periodEnd]. Datas ISO (LocalDate). `apparentLossesM3`
 * é a componente opcional: `null` = ausente declarada (a perda real
 * sai ausente, nunca estimada — regra da estimativa II-6 §2).
 */
export interface DmcPeriodInputs {
  dmcId: string
  dmcCode: string
  periodStart: string
  periodEnd: string
  systemInputM3: string
  exportedM3: string
  billedMeteredM3: string
  billedUnmeteredM3: string
  authorizedUnbilledM3: string
  apparentLossesM3: string | null
}

/** DmaModel.DmcPeriodBalance — identidades exatas do ExactWaterBalance. */
export interface DmcPeriodBalance {
  dmcId: string
  dmcCode: string
  periodStart: string
  periodEnd: string
  netInputM3: string
  billedTotalM3: string
  authorizedConsumptionM3: string
  totalLossesM3: string
  apparentLossesM3: string | null
  realLossesM3: string | null
  nonRevenueWaterM3: string
  absentComponents: string[]
}

/** DmaModel.DmaPeriodBalance — soma exata dos balanços dos membros. */
export interface DmaPeriodBalance {
  dmaCode: string
  sectorCode: string
  periodStart: string
  periodEnd: string
  memberCount: number
  netInputM3: string
  billedTotalM3: string
  authorizedConsumptionM3: string
  totalLossesM3: string
  apparentLossesM3: string | null
  realLossesM3: string | null
  nonRevenueWaterM3: string
  absentComponents: string[]
}

/** DmaModel.FlowSample — amostra da série de vazões do DMA (L/h). */
export interface FlowSample {
  /** LocalDateTime ISO, ex.: "2026-10-15T02:00:00". */
  instant: string
  flowLitresPerHour: string
}

/** LossParameters.NightWindow — janela noturna [start, end) no mesmo dia. */
export interface NightWindow {
  /** "HH:mm". */
  start: string
  /** "HH:mm". */
  end: string
}

/**
 * domain/DmaDistrict (entidade GIS-03) — só os campos escalares que um
 * painel lista; a geometria (`boundary`) fica fora (é do mapa, E3).
 */
export interface DmaDistrictSummary {
  code: string
  name: string
  description: string | null
  areaKm2: string | null
  populationServed: number | null
  connectionsCount: number | null
  pipeLengthKm: string | null
  avgPressureM: string | null
  minPressureM: string | null
  maxPressureM: string | null
  status: 'ACTIVE' | 'INACTIVE' | 'PLANNING' | 'UNDER_CONSTRUCTION' | 'DECOMMISSIONED'
}

// ---------------------------------------------------------------------------
// ExactWaterBalance (espelho)
// ---------------------------------------------------------------------------

/** ExactWaterBalance.ABSENT_APPARENT_LOSSES. */
export const ABSENT_APPARENT_LOSSES = 'apparentLossesM3'

function requireNonNegative(value: string, what: string): Decimal {
  const parsed = dec(value)
  if (decSign(parsed) < 0) throw new Error(`${what} negativo: ${value}`)
  return parsed
}

/**
 * Espelho de `ExactWaterBalance.calculate`: netInput = systemInput −
 * exported; billedTotal = billedMetered + billedUnmetered; authorized
 * = billedTotal + authorizedUnbilled; totalLosses = netInput −
 * authorized; nonRevenueWater = netInput − billedTotal; realLosses =
 * totalLosses − apparentLosses QUANDO declaradas (senão `null` e
 * `apparentLossesM3` consta de absentComponents).
 * Validações do record Java reproduzidas: período invertido recusa;
 * exportado maior que a entrada recusa; negativos recusam.
 */
export function calculateBalance(input: DmcPeriodInputs): DmcPeriodBalance {
  if (input.periodEnd < input.periodStart) {
    throw new Error(`periodo invertido: ${input.periodStart}..${input.periodEnd}`)
  }
  const systemInput = requireNonNegative(input.systemInputM3, 'volume de entrada macromedido')
  const exported = requireNonNegative(input.exportedM3, 'volume exportado em bloco medido')
  const billedMetered = requireNonNegative(input.billedMeteredM3, 'volume faturado medido')
  const billedUnmetered = requireNonNegative(input.billedUnmeteredM3, 'volume faturado nao medido')
  const authorizedUnbilled = requireNonNegative(input.authorizedUnbilledM3, 'volume autorizado nao faturado')
  const apparent = input.apparentLossesM3 === null ? null : requireNonNegative(input.apparentLossesM3, 'perdas aparentes')
  if (decCmp(exported, systemInput) > 0) {
    throw new Error('exportado em bloco maior que a entrada do sistema')
  }
  const netInput = decSub(systemInput, exported)
  const billedTotal = decAdd(billedMetered, billedUnmetered)
  const authorized = decAdd(billedTotal, authorizedUnbilled)
  const totalLosses = decSub(netInput, authorized)
  const nrw = decSub(netInput, billedTotal)
  const real = apparent === null ? null : decSub(totalLosses, apparent)
  return {
    dmcId: input.dmcId,
    dmcCode: input.dmcCode,
    periodStart: input.periodStart,
    periodEnd: input.periodEnd,
    netInputM3: decToString(netInput),
    billedTotalM3: decToString(billedTotal),
    authorizedConsumptionM3: decToString(authorized),
    totalLossesM3: decToString(totalLosses),
    apparentLossesM3: input.apparentLossesM3,
    realLossesM3: real === null ? null : decToString(real),
    nonRevenueWaterM3: decToString(nrw),
    absentComponents: real === null ? [ABSENT_APPARENT_LOSSES] : [],
  }
}

function sumDecimals(values: string[]): Decimal {
  let total = DEC_ZERO
  for (const v of values) total = decAdd(total, dec(v))
  return total
}

/**
 * Espelho de `ExactWaterBalance.aggregate`: exige exatamente os DMCs
 * da definição, no MESMO período — membro sem balanço ou balanço fora
 * da definição RECUSA (agregar um subconjunto em silêncio seria
 * inventar o DMA). A perda real do agregado só existe se TODOS os
 * membros a conhecerem.
 */
export function aggregateBalance(definition: DmaDefinition, memberBalances: DmcPeriodBalance[]): DmaPeriodBalance {
  const codes = memberBalances.map((b) => b.dmcCode).sort()
  const expected = [...definition.memberDmcCodes].sort()
  if (codes.length !== expected.length || codes.some((c, i) => c !== expected[i])) {
    throw new Error(
      `agregacao exige exatamente os DMCs da definicao ${JSON.stringify(expected)}; recebidos ${JSON.stringify(codes)}`,
    )
  }
  const first = memberBalances[0]
  if (!first) throw new Error('balancos dos DMCs membros obrigatorios')
  if (memberBalances.some((b) => b.periodStart !== first.periodStart || b.periodEnd !== first.periodEnd)) {
    throw new Error('agregacao de DMA exige o mesmo periodo em todos os DMCs')
  }
  const allApparent = memberBalances.every((b) => b.apparentLossesM3 !== null)
  const allReal = memberBalances.every((b) => b.realLossesM3 !== null)
  const apparent = allApparent
    ? decToString(sumDecimals(memberBalances.map((b) => b.apparentLossesM3 as string)))
    : null
  const real = allReal ? decToString(sumDecimals(memberBalances.map((b) => b.realLossesM3 as string))) : null
  return {
    dmaCode: definition.dmaCode,
    sectorCode: definition.sectorCode,
    periodStart: first.periodStart,
    periodEnd: first.periodEnd,
    memberCount: memberBalances.length,
    netInputM3: decToString(sumDecimals(memberBalances.map((b) => b.netInputM3))),
    billedTotalM3: decToString(sumDecimals(memberBalances.map((b) => b.billedTotalM3))),
    authorizedConsumptionM3: decToString(sumDecimals(memberBalances.map((b) => b.authorizedConsumptionM3))),
    totalLossesM3: decToString(sumDecimals(memberBalances.map((b) => b.totalLossesM3))),
    apparentLossesM3: apparent,
    realLossesM3: real,
    nonRevenueWaterM3: decToString(sumDecimals(memberBalances.map((b) => b.nonRevenueWaterM3))),
    absentComponents: real === null ? [ABSENT_APPARENT_LOSSES] : [],
  }
}

// ---------------------------------------------------------------------------
// MinimumNightFlow (espelho)
// ---------------------------------------------------------------------------

export type NightFlowStatus = 'COMPLETE' | 'NO_SAMPLE_IN_WINDOW' | 'INCOMPLETE_COVERAGE'

/** MinimumNightFlow.Result. */
export interface NightFlowResult {
  status: NightFlowStatus
  minimumNightFlowLPerH: string | null
  netNightFlowLPerH: string | null
  window: NightWindow
  sampleIntervalMinutes: number
  samplesInWindow: number
  nightsEvaluated: number
  incompleteNights: string[]
  absentComponents: string[]
  legitExceedsMinimum: boolean
}

/** MinimumNightFlow.ABSENT_NET_NIGHT_FLOW. */
export const ABSENT_NET_NIGHT_FLOW = 'netNightFlowLPerH'

/** LossParameters.NIGHT_WINDOW_DEFAULT — padrão NOMEADO no aceite da ED-85. */
export const NIGHT_WINDOW_DEFAULT: NightWindow = { start: '02:00', end: '04:00' }

/**
 * R41-4-17: resolve a janela noturna do painel de VMN — a janela
 * declarada pela fonte quando existe; quando a porta não a traz
 * (`undefined`/`null`), o padrão NOMEADO do aceite da ED-85
 * (02:00–04:00). A omissão é sempre a do aceite, nunca outra.
 */
export function resolveNightWindow(window?: NightWindow | null): NightWindow {
  return window ?? NIGHT_WINDOW_DEFAULT
}

function timeToMinutes(hhmm: string): number {
  const m = /^(\d{2}):(\d{2})$/.exec(hhmm)
  if (!m) throw new Error(`janela noturna exige o formato HH:mm-HH:mm: ${hhmm}`)
  return Number(m[1]) * 60 + Number(m[2])
}

function windowContains(window: NightWindow, instant: string): boolean {
  const time = instant.slice(11, 16)
  return time >= window.start && time < window.end
}

/**
 * Espelho de `MinimumNightFlow.analyze`: cobertura esperada por noite
 * = minutosDaJanela / sampleIntervalMinutes (divisão exata exigida).
 * COMPLETE ⇒ VMN = mínimo exato das amostras na janela; janela
 * parcial ⇒ INCOMPLETE_COVERAGE e a VMN NÃO é devolvida; nenhuma
 * amostra ⇒ NO_SAMPLE_IN_WINDOW. Sem consumo noturno legítimo
 * declarado (`legitNightUseLPerConnH === null`), a VMN líquida sai
 * ausente e declarada. VMN líquida negativa NÃO é truncada (a flag
 * `legitExceedsMinimum` expõe a inconsistência).
 *
 * R41-4-18: janela invertida ou vazia (`end <= start`) RECUSA com
 * erro visível — antes desta cura, `04:00–02:00` dava windowMinutes
 * negativo e noites sempre INCOMPLETE_COVERAGE em silêncio. Nota:
 * o espelho Java (MinimumNightFlow.java:87-90) NÃO valida isto —
 * o endurecimento é só do lado TS; alinhar o Java fica para um
 * lote próprio do núcleo, fora deste pacote de telas.
 */
export function analyzeNightFlow(
  series: FlowSample[],
  window: NightWindow,
  sampleIntervalMinutes: number,
  legitNightUseLPerConnH: string | null,
  connections: number,
): NightFlowResult {
  if (sampleIntervalMinutes <= 0) throw new Error(`cadencia da serie invalida: ${sampleIntervalMinutes}`)
  if (connections <= 0) throw new Error(`VMN liquida exige ligacoes > 0: ${connections}`)
  const legit = legitNightUseLPerConnH === null ? null : requireNonNegative(legitNightUseLPerConnH, 'consumo noturno legitimo')
  const startMinutes = timeToMinutes(window.start)
  const endMinutes = timeToMinutes(window.end)
  if (endMinutes <= startMinutes) {
    throw new Error(`janela noturna invertida ou vazia: ${window.start}–${window.end} (o fim deve ser depois do início)`)
  }
  const windowMinutes = endMinutes - startMinutes
  if (windowMinutes % sampleIntervalMinutes !== 0) {
    throw new Error(`cadencia ${sampleIntervalMinutes} min nao divide exatamente a janela de ${windowMinutes} min`)
  }
  const expectedPerNight = windowMinutes / sampleIntervalMinutes
  const absent = legit === null ? [ABSENT_NET_NIGHT_FLOW] : []
  const inWindow = series.filter((s) => windowContains(window, s.instant))
  if (inWindow.length === 0) {
    return {
      status: 'NO_SAMPLE_IN_WINDOW',
      minimumNightFlowLPerH: null,
      netNightFlowLPerH: null,
      window,
      sampleIntervalMinutes,
      samplesInWindow: 0,
      nightsEvaluated: 0,
      incompleteNights: [],
      absentComponents: absent,
      legitExceedsMinimum: false,
    }
  }
  const perNight = new Map<string, number>()
  for (const s of inWindow) {
    const night = s.instant.slice(0, 10)
    perNight.set(night, (perNight.get(night) ?? 0) + 1)
  }
  const incomplete = [...perNight.entries()].filter(([, count]) => count !== expectedPerNight).map(([night]) => night).sort()
  if (incomplete.length > 0) {
    return {
      status: 'INCOMPLETE_COVERAGE',
      minimumNightFlowLPerH: null,
      netNightFlowLPerH: null,
      window,
      sampleIntervalMinutes,
      samplesInWindow: inWindow.length,
      nightsEvaluated: perNight.size,
      incompleteNights: incomplete,
      absentComponents: absent,
      legitExceedsMinimum: false,
    }
  }
  let minimum: Decimal | null = null
  for (const s of inWindow) {
    const v = dec(s.flowLitresPerHour)
    if (minimum === null || decCmp(v, minimum) < 0) minimum = v
  }
  if (minimum === null) throw new Error('serie de vazoes obrigatoria')
  const net = legit === null ? null : decSub(minimum, decMul(legit, dec(String(connections))))
  return {
    status: 'COMPLETE',
    minimumNightFlowLPerH: decToString(minimum),
    netNightFlowLPerH: net === null ? null : decToString(net),
    window,
    sampleIntervalMinutes,
    samplesInWindow: inWindow.length,
    nightsEvaluated: perNight.size,
    incompleteNights: [],
    absentComponents: absent,
    legitExceedsMinimum: net !== null && decSign(net) < 0,
  }
}

// ---------------------------------------------------------------------------
// DmcReturnRanking (espelho)
// ---------------------------------------------------------------------------

export type RankingMode = 'TARIFF_WEIGHTED' | 'LOSS_VOLUME_ONLY'
export type LossBasis = 'REAL_LOSSES' | 'TOTAL_LOSSES'

/** DmcReturnRanking.Entry — recuperável = reais se conhecidas, senão totais (declarado por linha). */
export interface RankingEntry {
  dmcCode: string
  totalLossesM3: string
  realLossesM3: string | null
}

/** DmcReturnRanking.Ranked. */
export interface RankedDmc {
  dmcCode: string
  position: number
  index: string
  basis: LossBasis
  recoverableLossesM3: string
}

/** DmcReturnRanking.Ranking — o modo vigente é SEMPRE declarado. */
export interface DmcRanking {
  mode: RankingMode
  tariffPerM3: string | null
  ranked: RankedDmc[]
}

export function recoverableLossesM3(entry: RankingEntry): string {
  return entry.realLossesM3 ?? entry.totalLossesM3
}

export function lossBasis(entry: RankingEntry): LossBasis {
  return entry.realLossesM3 !== null ? 'REAL_LOSSES' : 'TOTAL_LOSSES'
}

function rankingIndex(entry: RankingEntry, tariff: Decimal | null): Decimal {
  const recoverable = dec(recoverableLossesM3(entry))
  return tariff === null ? recoverable : decMul(recoverable, tariff)
}

/**
 * Espelho de `DmcReturnRanking.rank`. Tarifa = P-II6-TARIFA (Ciclo 4
 * do PDCA-GIS): `tariffPerM3 === null` significa tarifa [A DEFINIR] ⇒
 * o ranking sai por VOLUME de perdas e o modo LOSS_VOLUME_ONLY fica
 * declarado — nenhum preço é presumido. Ordenação determinística:
 * índice desc; desempate 1 pelo volume recuperável desc; desempate 2
 * (final) pelo código do DMC asc.
 */
export function rankDmcs(entries: RankingEntry[], tariffPerM3: string | null): DmcRanking {
  const tariff = tariffPerM3 === null ? null : dec(tariffPerM3)
  if (tariff !== null && decSign(tariff) <= 0) {
    throw new Error(`tarifa declarada deve ser positiva: ${tariffPerM3}`)
  }
  const mode: RankingMode = tariff === null ? 'LOSS_VOLUME_ONLY' : 'TARIFF_WEIGHTED'
  const sorted = [...entries].sort((a, b) => {
    const byIndex = decCmp(rankingIndex(b, tariff), rankingIndex(a, tariff))
    if (byIndex !== 0) return byIndex
    const byVolume = decCmp(dec(recoverableLossesM3(b)), dec(recoverableLossesM3(a)))
    if (byVolume !== 0) return byVolume
    return a.dmcCode < b.dmcCode ? -1 : a.dmcCode > b.dmcCode ? 1 : 0
  })
  return {
    mode,
    tariffPerM3,
    ranked: sorted.map((e, i) => ({
      dmcCode: e.dmcCode,
      position: i + 1,
      index: decToString(rankingIndex(e, tariff)),
      basis: lossBasis(e),
      recoverableLossesM3: recoverableLossesM3(e),
    })),
  }
}

// ---------------------------------------------------------------------------
// Porta tipada (sem rota HTTP afirmada — P-II6-FIACAO [A DEFINIR])
// ---------------------------------------------------------------------------

/** Dados que o painel de balanço consome, quando a fiação os entregar. */
export interface DmaBalancePanelData {
  definition: DmaDefinition
  memberInputs: DmcPeriodInputs[]
}

/** Dados que o painel de VMN consome (série viva = P-II6-SERIES). */
export interface NightFlowPanelData {
  series: FlowSample[]
  /**
   * Janela noturna declarada pela fonte. Ausente quando a porta não a
   * traz — nesse caso vale o padrão NOMEADO do aceite da ED-85
   * (resolveNightWindow, R41-4-17); nunca se presume outra janela.
   */
  window?: NightWindow
  sampleIntervalMinutes: number
  /** Consumo noturno legítimo (L/ligação/h) do tenant; null = não declarado. */
  legitNightUseLPerConnH: string | null
  connections: number
}

/** Dados que o painel de ranking consome; tarifa null = [A DEFINIR] (P-II6-TARIFA). */
export interface RankingPanelData {
  entries: RankingEntry[]
  tariffPerM3: string | null
}

/**
 * Porta dos painéis II-6. Cada método devolve `null` quando a fonte
 * ainda não está fiada — a tela mostra o estado vazio honesto com a
 * pendência nomeada, nunca um número inventado.
 */
export interface DmaPanelsPort {
  balancePanel(): Promise<DmaBalancePanelData | null>
  nightFlowPanel(): Promise<NightFlowPanelData | null>
  rankingPanel(): Promise<RankingPanelData | null>
  districts(): Promise<DmaDistrictSummary[] | null>
}

/** Pendência de fiação dos painéis II-6 (nenhuma rota HTTP afirmada). */
export const DMA_PANELS_WIRING_PENDENCY = 'P-II6-FIACAO'
/** Pendência das séries vivas do DMA (integração). */
export const DMA_SERIES_PENDENCY = 'P-II6-SERIES'
/** Pendência da tarifa do ranking (Ciclo 4 do PDCA-GIS). */
export const DMA_TARIFF_PENDENCY = 'P-II6-TARIFA'

/** Porta por omissão: sem fiação, tudo ausente (estado vazio honesto). */
export const unavailableDmaPanelsPort: DmaPanelsPort = {
  balancePanel: () => Promise.resolve(null),
  nightFlowPanel: () => Promise.resolve(null),
  rankingPanel: () => Promise.resolve(null),
  districts: () => Promise.resolve(null),
}
