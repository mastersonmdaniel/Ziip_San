import { describe, expect, it } from 'vitest'
import { dec, decAdd, decMul, decSub, decToString } from './decimal'
import {
  aggregateBalance,
  analyzeNightFlow,
  calculateBalance,
  rankDmcs,
  resolveNightWindow,
  NIGHT_WINDOW_DEFAULT,
  type DmcPeriodInputs,
  type FlowSample,
  type RankingEntry,
} from './dmaPanels'

/**
 * Fixtures copiadas das suites Java do núcleo II-6 (fonte nomeada em
 * cada describe): DmaFixtures (START 2026-10-01, END 2026-10-31),
 * ExactWaterBalanceTest, MinimumNightFlowTest, DmcReturnRankingTest.
 * Nenhum valor foi inventado para estes testes.
 */
const START = '2026-10-01'
const END = '2026-10-31'

function inputs(
  code: string,
  input: string,
  exported: string,
  billedMetered: string,
  billedUnmetered: string,
  unbilled: string,
  apparentOrNull: string | null,
): DmcPeriodInputs {
  return {
    dmcId: `fixture-${code}`,
    dmcCode: code,
    periodStart: START,
    periodEnd: END,
    systemInputM3: input,
    exportedM3: exported,
    billedMeteredM3: billedMetered,
    billedUnmeteredM3: billedUnmetered,
    authorizedUnbilledM3: unbilled,
    apparentLossesM3: apparentOrNull,
  }
}

describe('decimal exato (BigDecimal)', () => {
  it('soma, subtrai e multiplica sem erro de float e com a escala do BigDecimal', () => {
    expect(decToString(decAdd(dec('0.1'), dec('0.2')))).toBe('0.3')
    expect(decToString(decSub(dec('10000.50'), dec('500.25')))).toBe('9500.25')
    expect(decToString(decMul(dec('2000'), dec('7.50')))).toBe('15000.00')
    expect(decToString(decMul(dec('1.70'), dec('1000')))).toBe('1700.00')
  })
})

describe('calculateBalance — ExactWaterBalanceTest do núcleo Java', () => {
  it('balanço calculado à mão é exato (DMC-A da suite Java)', () => {
    const b = calculateBalance(inputs('DMC-A', '10000.50', '500.25', '6000.10', '300', '200.05', '500.05'))
    expect(b.netInputM3).toBe('9500.25')
    expect(b.billedTotalM3).toBe('6300.10')
    expect(b.authorizedConsumptionM3).toBe('6500.15')
    expect(b.totalLossesM3).toBe('3000.10')
    expect(b.realLossesM3).toBe('2500.05')
    expect(b.nonRevenueWaterM3).toBe('3200.15')
    expect(b.absentComponents).toEqual([])
  })

  it('perdas aparentes ausentes: perda real ausente e DECLARADA, nunca estimada', () => {
    const b = calculateBalance(inputs('DMC-N', '1000', '0', '700', '0', '0', null))
    expect(b.totalLossesM3).toBe('300')
    expect(b.realLossesM3).toBeNull()
    expect(b.absentComponents).toEqual(['apparentLossesM3'])
  })

  it('exportado maior que a entrada e período invertido recusam, como o record Java', () => {
    expect(() => calculateBalance(inputs('DMC-X', '100', '200', '0', '0', '0', null))).toThrow(/exportado/)
    expect(() =>
      calculateBalance({ ...inputs('DMC-X', '100', '0', '0', '0', '0', null), periodStart: END, periodEnd: START }),
    ).toThrow(/invertido/)
  })
})

describe('aggregateBalance — ExactWaterBalanceTest do núcleo Java', () => {
  it('agrega somando exatamente e exige exatamente os DMCs da definição', () => {
    const a = calculateBalance(inputs('DMC-A', '10000.50', '500.25', '6000.10', '300', '200.05', '500.05'))
    const b = calculateBalance(inputs('DMC-B', '2000', '0', '1000', '0', '100', '50'))
    const definition = { dmaCode: 'DMA-1', sectorCode: 'SETOR-1', memberDmcCodes: ['DMC-A', 'DMC-B'] }
    const agg = aggregateBalance(definition, [b, a])
    expect(agg.memberCount).toBe(2)
    expect(agg.netInputM3).toBe('11500.25')
    expect(agg.totalLossesM3).toBe('3900.10')
    expect(agg.realLossesM3).toBe('3350.05')
    expect(() => aggregateBalance(definition, [a])).toThrow(/exatamente/)
  })

  it('perda real do agregado ausente quando algum membro não conhece as aparentes', () => {
    const a = calculateBalance(inputs('DMC-A', '1000', '0', '700', '0', '0', '10'))
    const b = calculateBalance(inputs('DMC-B', '1000', '0', '700', '0', '0', null))
    const agg = aggregateBalance({ dmaCode: 'DMA-2', sectorCode: 'SETOR-1', memberDmcCodes: ['DMC-A', 'DMC-B'] }, [a, b])
    expect(agg.totalLossesM3).toBe('600')
    expect(agg.realLossesM3).toBeNull()
    expect(agg.absentComponents).toEqual(['apparentLossesM3'])
  })
})

/** MinimumNightFlowTest.fullNight: noite completa a 30 min, mínimo 4200.00 às 03:00. */
function fullNight(): FlowSample[] {
  return [
    { instant: '2026-10-15T01:00:00', flowLitresPerHour: '9000' },
    { instant: '2026-10-15T02:00:00', flowLitresPerHour: '5000.50' },
    { instant: '2026-10-15T02:30:00', flowLitresPerHour: '4600' },
    { instant: '2026-10-15T03:00:00', flowLitresPerHour: '4200.00' },
    { instant: '2026-10-15T03:30:00', flowLitresPerHour: '4800' },
    { instant: '2026-10-15T12:00:00', flowLitresPerHour: '15000' },
  ]
}

describe('analyzeNightFlow — MinimumNightFlowTest do núcleo Java', () => {
  it('mínimo noturno conhecido é exato e a líquida subtrai o legítimo (4200 − 1,70×1000 = 2500)', () => {
    const r = analyzeNightFlow(fullNight(), NIGHT_WINDOW_DEFAULT, 30, '1.70', 1000)
    expect(r.status).toBe('COMPLETE')
    expect(r.minimumNightFlowLPerH).toBe('4200.00')
    expect(r.netNightFlowLPerH).toBe('2500.00')
    expect(r.samplesInWindow).toBe(4)
    expect(r.nightsEvaluated).toBe(1)
  })

  it('sem legítimo declarado, a líquida sai ausente e declarada', () => {
    const r = analyzeNightFlow(fullNight(), NIGHT_WINDOW_DEFAULT, 30, null, 1000)
    expect(r.status).toBe('COMPLETE')
    expect(r.netNightFlowLPerH).toBeNull()
    expect(r.absentComponents).toEqual(['netNightFlowLPerH'])
  })

  it('janela incompleta é desfecho tipificado e NÃO devolve mínimo', () => {
    const partial = fullNight()
    partial.splice(2, 1)
    const r = analyzeNightFlow(partial, NIGHT_WINDOW_DEFAULT, 30, null, 1000)
    expect(r.status).toBe('INCOMPLETE_COVERAGE')
    expect(r.minimumNightFlowLPerH).toBeNull()
    expect(r.incompleteNights).toEqual(['2026-10-15'])
  })

  it('nenhuma amostra na janela é desfecho tipificado', () => {
    const r = analyzeNightFlow(
      [{ instant: '2026-10-15T12:00:00', flowLitresPerHour: '15000' }],
      NIGHT_WINDOW_DEFAULT,
      30,
      null,
      1000,
    )
    expect(r.status).toBe('NO_SAMPLE_IN_WINDOW')
    expect(r.minimumNightFlowLPerH).toBeNull()
  })

  it('líquida negativa é exposta, não truncada (4200 − 5,00×1000 = −800)', () => {
    const r = analyzeNightFlow(fullNight(), NIGHT_WINDOW_DEFAULT, 30, '5.00', 1000)
    expect(r.netNightFlowLPerH).toBe('-800.00')
    expect(r.legitExceedsMinimum).toBe(true)
  })
})

describe('resolveNightWindow — R41-4-17', () => {
  it('sem janela na fonte, vale o padrão NOMEADO do aceite da ED-85 (02:00–04:00)', () => {
    expect(resolveNightWindow(undefined)).toEqual({ start: '02:00', end: '04:00' })
    expect(resolveNightWindow(null)).toEqual(NIGHT_WINDOW_DEFAULT)
  })

  it('janela declarada pela fonte passa intacta', () => {
    expect(resolveNightWindow({ start: '01:00', end: '05:00' })).toEqual({ start: '01:00', end: '05:00' })
  })
})

describe('analyzeNightFlow — R41-4-18', () => {
  it('janela invertida (04:00–02:00) RECUSA com erro visível — nunca INCOMPLETE_COVERAGE silencioso', () => {
    expect(() => analyzeNightFlow(fullNight(), { start: '04:00', end: '02:00' }, 30, null, 1000)).toThrow(
      /janela noturna invertida/,
    )
  })

  it('janela vazia (02:00–02:00) RECUSA com erro visível', () => {
    expect(() => analyzeNightFlow(fullNight(), { start: '02:00', end: '02:00' }, 30, null, 1000)).toThrow(
      /janela noturna invertida/,
    )
  })
})

/** DmcReturnRankingTest.fixture do núcleo Java. */
function rankingFixture(): RankingEntry[] {
  return [
    { dmcCode: 'DMC-B', totalLossesM3: '900', realLossesM3: '800' },
    { dmcCode: 'DMC-A', totalLossesM3: '1200', realLossesM3: '800' },
    { dmcCode: 'DMC-C', totalLossesM3: '2000', realLossesM3: null },
    { dmcCode: 'DMC-D', totalLossesM3: '100', realLossesM3: '50' },
  ]
}

describe('rankDmcs — DmcReturnRankingTest do núcleo Java', () => {
  it('sem tarifa ([A DEFINIR]) ordena por volume, com o modo declarado', () => {
    const r = rankDmcs(rankingFixture(), null)
    expect(r.mode).toBe('LOSS_VOLUME_ONLY')
    expect(r.ranked.map((x) => x.dmcCode)).toEqual(['DMC-C', 'DMC-A', 'DMC-B', 'DMC-D'])
    expect(r.ranked[0]?.basis).toBe('TOTAL_LOSSES')
    expect(r.ranked[1]?.basis).toBe('REAL_LOSSES')
    expect(r.ranked[0]?.position).toBe(1)
  })

  it('com tarifa declarada, o índice é o produto exato (2000 × 7,50 = 15000,00)', () => {
    const r = rankDmcs(rankingFixture(), '7.50')
    expect(r.mode).toBe('TARIFF_WEIGHTED')
    expect(r.ranked.map((x) => x.dmcCode)).toEqual(['DMC-C', 'DMC-A', 'DMC-B', 'DMC-D'])
    expect(r.ranked[0]?.index).toBe('15000.00')
    expect(r.ranked[1]?.index).toBe('6000.00')
    expect(r.ranked[3]?.index).toBe('375.00')
  })

  it('desempate determinístico: índice e volume iguais ⇒ código ascendente', () => {
    const tie: RankingEntry[] = [
      { dmcCode: 'DMC-Y', totalLossesM3: '100', realLossesM3: null },
      { dmcCode: 'DMC-X', totalLossesM3: '300', realLossesM3: '100' },
    ]
    expect(rankDmcs(tie, null).ranked.map((x) => x.dmcCode)).toEqual(['DMC-X', 'DMC-Y'])
  })
})
