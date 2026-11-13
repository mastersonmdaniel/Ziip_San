import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { SelfReadingReview } from './SelfReadingReview'
import { formatScore, type SelfReadingOutcome, type SelfReadingReviewPort } from '@services/metering/selfReading'

/**
 * Fixtures das suites Java do nucleo (fonte nomeada):
 *  - HumanReviewQueueTest.java: ids u1/u2/s1/s2/s3 e ordem (s1,s2,s3).
 *  - SelfReadingIntakeServiceTest.java: scoreBaixoVaiParaRevisaoHumana
 *    (57.50, SCORE_BELOW_THRESHOLD) e semFotoVaiParaRevisao
 *    (PHOTO_MISSING, sem score).
 */
const U1 = '11111111-1111-4111-8111-111111111111'
const U2 = '22222222-2222-4222-8222-222222222222'
const S1 = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const S2 = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const S3 = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
const GEO_ABSENT = { distanceMetres: null, withinMaxDistance: false, accuracySufficient: false, present: false }
const GEO_HERE = { distanceMetres: 0, withinMaxDistance: true, accuracySufficient: true, present: true }

const LOW_SCORE: SelfReadingOutcome = {
  submissionId: S1, unitId: U1, reference: '2026-11', status: 'PENDING_HUMAN_REVIEW',
  reasons: ['SCORE_BELOW_THRESHOLD'],
  score: { total: 57.5, ocrComponent: 0.5, coherenceComponent: 0.5, photoComponent: 0.5, geoComponent: 1 },
  ocrReading: 1018, geo: GEO_HERE, handoff: null,
}
const NO_PHOTO: SelfReadingOutcome = {
  submissionId: S2, unitId: U2, reference: '2026-11', status: 'PENDING_HUMAN_REVIEW',
  reasons: ['PHOTO_MISSING'], score: null, ocrReading: null, geo: GEO_ABSENT, handoff: null,
}
const DEC: SelfReadingOutcome = {
  submissionId: S3, unitId: U1, reference: '2026-12', status: 'PENDING_HUMAN_REVIEW',
  reasons: ['SCORE_BELOW_THRESHOLD'], score: null, ocrReading: null, geo: GEO_ABSENT, handoff: null,
}

function portWith(items: SelfReadingOutcome[]): SelfReadingReviewPort {
  return { pending: () => Promise.resolve(items) }
}

describe('Autoleitura — revisão humana (P-II7-TELA)', () => {
  it('sem porta, declara a pendencia P-II7-FIACAO sem fingir dados', () => {
    renderWithProviders(<SelfReadingReview />)
    expect(screen.getByText(/P-II7-FIACAO/)).toBeInTheDocument()
  })

  it('lista a fila na ordem deterministica do nucleo (s1, s2, s3)', async () => {
    renderWithProviders(<SelfReadingReview port={portWith([DEC, NO_PHOTO, LOW_SCORE])} />)
    expect(await screen.findByText(S1)).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    const text = rows.map((r) => r.textContent ?? '')
    const i1 = text.findIndex((t) => t.includes(S1))
    const i2 = text.findIndex((t) => t.includes(S2))
    const i3 = text.findIndex((t) => t.includes(S3))
    expect(i1).toBeGreaterThan(-1)
    expect(i1).toBeLessThan(i2)
    expect(i2).toBeLessThan(i3)
    expect(screen.getByText('57,50')).toBeInTheDocument()
    expect(screen.getAllByText('sem score').length).toBeGreaterThan(0)
  })

  it('detalhe mostra score por componentes, geo e residual da decisao (sem accoes inventadas)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SelfReadingReview port={portWith([LOW_SCORE])} />)
    await screen.findByText(S1)
    await user.click(screen.getByRole('button', { name: 'Ver detalhe' }))
    expect(await screen.findByText(/Score total: 57,50/)).toBeInTheDocument()
    expect(screen.getByText(/Componente OCR: 0,50/)).toBeInTheDocument()
    expect(screen.getByText(/Distância à ligação:/)).toBeInTheDocument()
    expect(screen.getByText(/P-II7-FIACAO/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aceitar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Corrigir' })).not.toBeInTheDocument()
  })

  it('R41-4-25: detalhe tem botão "Fechar detalhe" que fecha o detalhe', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SelfReadingReview port={portWith([LOW_SCORE])} />)
    await screen.findByText(S1)
    await user.click(screen.getByRole('button', { name: 'Ver detalhe' }))
    expect(await screen.findByText(/Score total: 57,50/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Fechar detalhe' }))
    expect(screen.queryByText(/Score total: 57,50/)).not.toBeInTheDocument()
  })

  it('R41-4-26: formatador de score vive no serviço (sem cópia local na página)', () => {
    expect(formatScore(57.5)).toBe('57,50')
    expect(formatScore(96)).toBe('96,00')
  })

  it('R41-4-34: rota da revisão usa o prefixo operacoes/ (convenção das rotas irmãs)', () => {
    const here = path.dirname(fileURLToPath(import.meta.url))
    const appSrc = fs.readFileSync(path.join(here, '..', '..', 'App.tsx'), 'utf8')
    expect(appSrc).toContain('path="operacoes/autoleitura/revisao"')
    expect(appSrc).not.toContain('operacao/autoleitura/revisao')
  })

  it('filtro por motivo PHOTO_MISSING reduz a fila e vazio e declarado', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SelfReadingReview port={portWith([LOW_SCORE, NO_PHOTO])} />)
    await screen.findByText(S1)
    await user.selectOptions(screen.getByLabelText(/Filtrar por motivo/), 'PHOTO_MISSING')
    expect(screen.queryByText(S1)).not.toBeInTheDocument()
    expect(screen.getByText(S2)).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText(/Filtrar por motivo/), 'OCR_NO_READING')
    expect(await screen.findByText(/Nenhuma submissão neste filtro/)).toBeInTheDocument()
  })
})
