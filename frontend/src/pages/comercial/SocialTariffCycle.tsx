import { useEffect, useState } from 'react'
import { Alert } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import {
  DECLARED_PROOF_FORMAT,
  PROOF_PARAMETERS,
  declaredHeaderLine,
  groupActiveSearch,
  type ActiveSearchReason,
  type CrossingOutcome,
  type CycleReport,
  type DivergenceKind,
  type FamilyCrossingSummary,
  type GrantRefusalReason,
  type GrantStatus,
  type SocialBasis,
  type SocialCyclePort,
} from '@services/comercial/socialCycle'

/** Rótulos pt-BR dos códigos de enumeração do ciclo (padrão STATUS_LABEL). */
const BASIS_LABEL: Record<SocialBasis, string> = {
  CADUNICO: 'CadÚnico',
  BPC: 'BPC',
  LOCAL_PROGRAM: 'Programa local',
}

const DIVERGENCE_LABEL: Record<DivergenceKind, string> = {
  NO_UNIT_MATCH: 'Sem unidade correspondente',
  INELIGIBLE: 'Inelegível',
}

const GRANT_STATUS_LABEL: Record<GrantStatus, string> = {
  ELIGIBLE: 'Elegível',
  GRANTED: 'Concedida',
  UNDER_REVIEW: 'Em análise',
  GRACE: 'Em graça',
  ENDED: 'Encerrada',
}

const REFUSAL_REASON_LABEL: Record<GrantRefusalReason, string> = {
  INELIGIBLE: 'Inelegível',
  OUTSIDE_VALIDITY: 'Fora da validade',
  LEGAL_RULE_NOT_YET_APPLICABLE: 'Regra legal ainda não aplicável',
  LIST_SOURCE_NOT_ALLOWED: 'Origem da lista não permitida',
}

const ACTIVE_SEARCH_REASON_LABEL: Record<ActiveSearchReason, string> = {
  MATCHED_NOT_ENROLLED: 'Casada, não inscrita',
  NO_UNIT_MATCH: 'Sem unidade correspondente',
}

const OUTCOME_LABEL: Record<CrossingOutcome, string> = {
  MATCHED: 'Casada',
  UNMATCHED: 'Sem unidade correspondente',
  INELIGIBLE: 'Inelegível',
}

/**
 * P-II3-TELA — ciclo da tarifa social e busca ativa.
 *
 * Rota: /comercial/tarifa-social (decisão de executor pela convenção das
 * rotas irmãs; nenhuma fonte declara o caminho — registado no relatório
 * do pacote). A tela consome a porta tipada `SocialCyclePort`, espelho
 * do núcleo Java `billing/social/cycle/`; NÃO há rota HTTP afirmada —
 * a fiação é a pendência P-II3-INTEGRACAO. Sem porta ligada, a tela
 * mostra o formato declarado e as pendências, e nenhum dado.
 *
 * O leiaute oficial CadÚnico/BPC é [SEM FONTE] (P-II3-LEIAUTE): o que
 * se declara é o formato de prova do núcleo. Os parâmetros legais são
 * [A DEFINIR] (P-II3-PARAM): exibem-se os valores de prova da P1.5
 * (24/3/3) rotulados como prova, e a recertificação como [A DEFINIR].
 *
 * Paridade web↔mobile: padrão responsivo da casca (tabelas da
 * biblioteca `components/ui` com cartões em tela estreita); nenhuma
 * função exclusiva de desktop.
 */
export function SocialTariffCycle({ port }: { port?: SocialCyclePort }) {
  const [summaries, setSummaries] = useState<FamilyCrossingSummary[]>([])
  const [report, setReport] = useState<CycleReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Formato e parâmetros vêm da porta quando há porta ligada (R41-4-11);
  // sem porta, as constantes de prova são a reserva declarada.
  const [format, setFormat] = useState(DECLARED_PROOF_FORMAT)
  const [params, setParams] = useState(PROOF_PARAMETERS)
  const [formatFromPort, setFormatFromPort] = useState(false)
  const [paramsFromPort, setParamsFromPort] = useState(false)
  const [form, setForm] = useState({
    listContent: '',
    referenceDate: '',
    legalNature: '',
    listSource: '',
    actorId: '',
  })
  const [formErrors, setFormErrors] = useState<Partial<Record<keyof typeof form, string>>>({})

  useEffect(() => {
    if (!port) return
    let alive = true
    // A porta declara o formato e os parâmetros; se a leitura falhar,
    // mantêm-se as constantes de prova (reserva) sem bloquear os resumos.
    port
      .declaredFormat()
      .then((f) => {
        if (alive) {
          setFormat(f)
          setFormatFromPort(true)
        }
      })
      .catch(() => undefined)
    port
      .parameters()
      .then((p) => {
        if (alive) {
          setParams(p)
          setParamsFromPort(true)
        }
      })
      .catch(() => undefined)
    port
      .familySummaries()
      .then((s) => {
        if (alive) setSummaries(s)
      })
      .catch(() => {
        if (alive) setError('Não foi possível carregar os resumos do cruzamento.')
      })
    return () => {
      alive = false
    }
  }, [port])

  /** Validação dos cinco campos obrigatórios (R41-4-14); devolve true se válido. */
  function validateForm(values: typeof form): boolean {
    const errors: Partial<Record<keyof typeof form, string>> = {}
    if (values.listContent.trim() === '') errors.listContent = 'Conteúdo da lista é obrigatório.'
    if (values.referenceDate.trim() === '') errors.referenceDate = 'Data de referência é obrigatória.'
    if (values.legalNature.trim() === '') errors.legalNature = 'Natureza jurídica é obrigatória.'
    if (values.listSource.trim() === '') errors.listSource = 'Origem da lista é obrigatória.'
    if (values.actorId.trim() === '') errors.actorId = 'Ator é obrigatório.'
    setFormErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function runCycle() {
    if (!port) return
    try {
      setError(null)
      setReport(await port.runCycle(form))
    } catch {
      setError('A corrida do ciclo foi recusada pela porta.')
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!validateForm(form)) return
    await runCycle()
  }

  return (
    <div>
      <PageHeader
        title="Tarifa social — ciclo e busca ativa"
        description="Corrida do ciclo: importação da lista declarada, cruzamento, concessão sem pedido e busca ativa das famílias elegíveis não inscritas."
      />

      {!port && (
        <Alert tone="warning" title="Fiação pendente — P-II3-INTEGRACAO">
          Esta tela consome a porta tipada do núcleo do ciclo da tarifa social. Nenhuma rota HTTP
          está ligada (a fiação é a pendência P-II3-INTEGRACAO); por isso nenhum dado de corrida é
          mostrado.
        </Alert>
      )}
      {error && <Alert tone="error">{error}</Alert>}

      <Card>
        <CardContent>
          <h2>Formato da lista — declarado pelo núcleo</h2>
          <p>
            Formato <strong>{format.formatId}</strong> v{format.version}, delimitador «
            {format.delimiter}».{' '}
            {formatFromPort
              ? 'Este é o formato declarado pela porta do núcleo'
              : 'Este é o formato DECLARADO de prova do núcleo'}{' '}
            — o leiaute oficial CadÚnico/BPC é <strong>[SEM FONTE]</strong> (pendência
            P-II3-LEIAUTE) e não é afirmado por esta tela.
          </p>
          <p>
            Cabeçalho esperado: <code>{declaredHeaderLine(format)}</code>
          </p>
          <ul>
            {format.columns.map((column) => (
              <li key={column}>
                <code>{column}</code>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2>Parâmetros do ciclo</h2>
          <p>
            {paramsFromPort ? (
              <>
                Valores declarados pela porta do núcleo — os parâmetros legais de produção são{' '}
                <strong>[A DEFINIR]</strong> (pendência P-II3-PARAM).
              </>
            ) : (
              <>
                Valores de <strong>prova</strong> fixados pela P1.5 no núcleo — os parâmetros
                legais de produção são <strong>[A DEFINIR]</strong> (pendência P-II3-PARAM).
              </>
            )}
          </p>
          <ul>
            <li>
              Idade máxima do CadÚnico: {params.cadunicoMaxAgeMonths} meses (
              {paramsFromPort ? 'declarado pela porta' : 'prova'})
            </li>
            <li>
              Aviso de perda: {params.lossNoticeMonths} meses (
              {paramsFromPort ? 'declarado pela porta' : 'prova'})
            </li>
            <li>
              Graça após a perda: {params.graceMonths} meses (
              {paramsFromPort ? 'declarado pela porta' : 'prova'})
            </li>
            <li>
              Intervalo de recertificação:{' '}
              {params.recertificationIntervalMonths == null
                ? '[A DEFINIR]'
                : `${params.recertificationIntervalMonths} meses`}
            </li>
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <h2>Executar o ciclo</h2>
          <form noValidate onSubmit={(e) => void handleSubmit(e)}>
            <label>
              Conteúdo da lista (formato declarado)
              <textarea
                aria-label="Conteúdo da lista"
                aria-invalid={formErrors.listContent != null}
                aria-describedby={formErrors.listContent ? 'erro-listContent' : undefined}
                required
                value={form.listContent}
                onChange={(e) => setForm({ ...form, listContent: e.target.value })}
                rows={6}
              />
            </label>
            {formErrors.listContent && <p id="erro-listContent">{formErrors.listContent}</p>}
            <label>
              Data de referência
              <input
                aria-label="Data de referência"
                aria-invalid={formErrors.referenceDate != null}
                aria-describedby={formErrors.referenceDate ? 'erro-referenceDate' : undefined}
                required
                type="date"
                value={form.referenceDate}
                onChange={(e) => setForm({ ...form, referenceDate: e.target.value })}
              />
            </label>
            {formErrors.referenceDate && (
              <p id="erro-referenceDate">{formErrors.referenceDate}</p>
            )}
            <label>
              Natureza jurídica
              <input
                aria-label="Natureza jurídica"
                aria-invalid={formErrors.legalNature != null}
                aria-describedby={formErrors.legalNature ? 'erro-legalNature' : undefined}
                required
                value={form.legalNature}
                onChange={(e) => setForm({ ...form, legalNature: e.target.value })}
              />
            </label>
            {formErrors.legalNature && <p id="erro-legalNature">{formErrors.legalNature}</p>}
            <label>
              Origem da lista
              <input
                aria-label="Origem da lista"
                aria-invalid={formErrors.listSource != null}
                aria-describedby={formErrors.listSource ? 'erro-listSource' : undefined}
                required
                value={form.listSource}
                onChange={(e) => setForm({ ...form, listSource: e.target.value })}
              />
            </label>
            {formErrors.listSource && <p id="erro-listSource">{formErrors.listSource}</p>}
            <label>
              Ator
              <input
                aria-label="Ator"
                aria-invalid={formErrors.actorId != null}
                aria-describedby={formErrors.actorId ? 'erro-actorId' : undefined}
                required
                value={form.actorId}
                onChange={(e) => setForm({ ...form, actorId: e.target.value })}
              />
            </label>
            {formErrors.actorId && <p id="erro-actorId">{formErrors.actorId}</p>}
            <Button type="submit" disabled={!port}>
              Executar ciclo
            </Button>
          </form>
        </CardContent>
      </Card>

      {report && (
        <Card>
          <CardContent>
            <h2>Relatório da corrida</h2>
            <ul>
              <li>Linhas importadas: {report.importedRows}</li>
              <li>Duplicados removidos: {report.duplicatesRemoved}</li>
              <li>Já inscritos: {report.alreadyEnrolled}</li>
              <li>Concessões criadas: {report.grantsCreated.length}</li>
            </ul>

            <h3>Rejeições da importação</h3>
            {report.importRejections.length === 0 ? (
              <p>Nenhuma rejeição.</p>
            ) : (
              <ul>
                {report.importRejections.map((r) => (
                  <li key={`${r.lineNumber}-${r.code}`}>
                    Linha {r.lineNumber} — {r.field} — {r.code}: {r.message}
                  </li>
                ))}
              </ul>
            )}

            <h3>Cruzamento</h3>
            <ul>
              <li>Casadas: {report.crossing.matched.length}</li>
              <li>Sem unidade correspondente: {report.crossing.unmatched.length}</li>
              <li>Inelegíveis: {report.crossing.ineligible.length}</li>
              <li>Divergências: {report.crossing.divergences.length}</li>
            </ul>
            <ul>
              {report.crossing.divergences.map((d) => (
                <li key={`${d.familyCodeHash}-${d.kind}`}>
                  {d.familyCodeHash} — {DIVERGENCE_LABEL[d.kind] ?? d.kind}: {d.detail}
                </li>
              ))}
            </ul>

            <h3>Concessões criadas</h3>
            <ul>
              {report.grantsCreated.map((g) => (
                <li key={g.grantId}>
                  {g.familyCodeHash} — {BASIS_LABEL[g.basis] ?? g.basis} —{' '}
                  <Badge>{GRANT_STATUS_LABEL[g.status] ?? g.status}</Badge>
                </li>
              ))}
            </ul>

            <h3>Recusas de concessão</h3>
            <ul>
              {report.grantRefusals.map((r) => (
                <li key={`${r.familyCodeHash}-${r.reason}`}>
                  {r.familyCodeHash} — {REFUSAL_REASON_LABEL[r.reason] ?? r.reason}
                </li>
              ))}
            </ul>

            <h3>Busca ativa</h3>
            {groupActiveSearch(report.activeSearch).map((group) => (
              <div key={`${group.key.municipalityCode ?? ''}-${group.key.routeCode ?? ''}`}>
                <p>
                  Município {group.key.municipalityCode ?? '—'} · Rota {group.key.routeCode ?? '—'}{' '}
                  — {group.entries.length} família(s)
                </p>
                <ul>
                  {group.entries.map((entry) => (
                    <li key={entry.familyCodeHash}>
                      {entry.familyCodeHash} — {BASIS_LABEL[entry.basis] ?? entry.basis} —{' '}
                      {ACTIVE_SEARCH_REASON_LABEL[entry.reason] ?? entry.reason}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {summaries.length > 0 && (
        <Card>
          <CardContent>
            <h2>Resumos do cruzamento por família</h2>
            <ul>
              {summaries.map((s) => (
                <li key={s.familyCodeHash}>
                  {s.familyCodeHash} — {BASIS_LABEL[s.basis as SocialBasis] ?? s.basis} —{' '}
                  {OUTCOME_LABEL[s.outcome] ?? s.outcome} — {s.personCount} pessoa(s)
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
