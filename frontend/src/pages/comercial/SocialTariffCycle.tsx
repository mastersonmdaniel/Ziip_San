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
  type CycleReport,
  type FamilyCrossingSummary,
  type SocialCyclePort,
} from '@services/comercial/socialCycle'

/**
 * P-II3-TELA — ciclo da tarifa social e busca activa.
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
 * biblioteca `components/ui` com cartões em ecrã estreito); nenhuma
 * função exclusiva de desktop.
 */
export function SocialTariffCycle({ port }: { port?: SocialCyclePort }) {
  const [summaries, setSummaries] = useState<FamilyCrossingSummary[]>([])
  const [report, setReport] = useState<CycleReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    listContent: '',
    referenceDate: '',
    legalNature: '',
    listSource: '',
    actorId: '',
  })

  useEffect(() => {
    if (!port) return
    port
      .familySummaries()
      .then(setSummaries)
      .catch(() => setError('Não foi possível carregar os resumos do cruzamento.'))
  }, [port])

  async function runCycle() {
    if (!port) return
    try {
      setError(null)
      setReport(await port.runCycle(form))
    } catch {
      setError('A corrida do ciclo foi recusada pela porta.')
    }
  }

  const format = DECLARED_PROOF_FORMAT
  const params = PROOF_PARAMETERS

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
            {format.delimiter}». Este é o formato DECLARADO de prova do núcleo — o leiaute oficial
            CadÚnico/BPC é <strong>[SEM FONTE]</strong> (pendência P-II3-LEIAUTE) e não é afirmado
            por esta tela.
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
            Valores de <strong>prova</strong> fixados pela P1.5 no núcleo — os parâmetros legais de
            produção são <strong>[A DEFINIR]</strong> (pendência P-II3-PARAM).
          </p>
          <ul>
            <li>Idade máxima do CadÚnico: {params.cadunicoMaxAgeMonths} meses (prova)</li>
            <li>Aviso de perda: {params.lossNoticeMonths} meses (prova)</li>
            <li>Graça após a perda: {params.graceMonths} meses (prova)</li>
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
          <label>
            Conteúdo da lista (formato declarado)
            <textarea
              aria-label="Conteúdo da lista"
              value={form.listContent}
              onChange={(e) => setForm({ ...form, listContent: e.target.value })}
              rows={6}
            />
          </label>
          <label>
            Data de referência
            <input
              aria-label="Data de referência"
              type="date"
              value={form.referenceDate}
              onChange={(e) => setForm({ ...form, referenceDate: e.target.value })}
            />
          </label>
          <label>
            Natureza jurídica
            <input
              aria-label="Natureza jurídica"
              value={form.legalNature}
              onChange={(e) => setForm({ ...form, legalNature: e.target.value })}
            />
          </label>
          <label>
            Origem da lista
            <input
              aria-label="Origem da lista"
              value={form.listSource}
              onChange={(e) => setForm({ ...form, listSource: e.target.value })}
            />
          </label>
          <label>
            Ator
            <input
              aria-label="Ator"
              value={form.actorId}
              onChange={(e) => setForm({ ...form, actorId: e.target.value })}
            />
          </label>
          <Button onClick={runCycle} disabled={!port}>
            Executar ciclo
          </Button>
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
                  {d.familyCodeHash} — {d.kind}: {d.detail}
                </li>
              ))}
            </ul>

            <h3>Concessões criadas</h3>
            <ul>
              {report.grantsCreated.map((g) => (
                <li key={g.grantId}>
                  {g.familyCodeHash} — {g.basis} — <Badge>{g.status}</Badge>
                </li>
              ))}
            </ul>

            <h3>Recusas de concessão</h3>
            <ul>
              {report.grantRefusals.map((r) => (
                <li key={`${r.familyCodeHash}-${r.reason}`}>
                  {r.familyCodeHash} — {r.reason}
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
                      {entry.familyCodeHash} — {entry.basis} — {entry.reason}
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
                  {s.familyCodeHash} — {s.basis} — {s.outcome} — {s.personCount} pessoa(s)
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
