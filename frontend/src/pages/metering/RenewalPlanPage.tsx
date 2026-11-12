import { useEffect, useState } from 'react'
import { Alert } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { restrictionLabel, type AnnualPlan, type RenewalOrderPayload, type RenewalPlanPort } from '@services/metering/renewal'

const CLOCK_LABEL = {
  OVERDUE: 'Verificação vencida', DUE_WITHIN_HORIZON: 'Vence no horizonte',
  CURRENT: 'Em dia', UNKNOWN_NO_VERIFICATION: 'Sem verificação registada',
} as const
const CRITERION_LABEL = {
  AGE: 'Idade', VOLUME: 'Volume', CLASS_TECHNOLOGY: 'Classe/tecnologia', WATER_QUALITY: 'Qualidade da água',
} as const

/**
 * P-II5-TELA — plano anual de troca de hidrómetros (II-5).
 * Mostra o plano exactamente como o núcleo o fecha: linhas ordenadas por
 * score, contagens de exclusão por motivo e custo total (null no núcleo =
 * "desconhecido" aqui, nunca estimado). Restrição/pesos/limiares são
 * parâmetros do tenant [A DEFINIR] (P-II5-PARAM). Sem porta (fiação
 * P-II5-INT-* pendente), estado vazio honesto.
 * Paridade web↔mobile: mesma página responsiva (tabela com cartões).
 */
export function RenewalPlanPage({ port, planYear, tenantId }: { port?: RenewalPlanPort; planYear: number; tenantId?: string }) {
  const [plan, setPlan] = useState<AnnualPlan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [payloads, setPayloads] = useState<RenewalOrderPayload[] | null>(null)

  // Pré-visualização pura dos payloads de OS (SelectiveRenewalOrderService
  // .buildPayloads, 1:1 com as linhas do plano). Abrir as OS é o dispatch,
  // que é fiação (P-II5-INT-*) — esta tela não abre OS nenhuma.
  async function previewPayloads() {
    if (!port || !plan || !tenantId) return
    try { setPayloads(await port.buildPayloads(tenantId, plan)) }
    catch { setError('Não foi possível gerar a pré-visualização das ordens de serviço.') }
  }

  useEffect(() => {
    if (!port) return
    let alive = true
    port.getPlan(planYear)
      .then((p) => { if (alive) setPlan(p) })
      .catch(() => { if (alive) setError('Não foi possível carregar o plano de troca.') })
    return () => { alive = false }
  }, [port, planYear])

  return (
    <main>
      <PageHeader title={`Plano de troca de hidrómetros — ${planYear}`}
        description="Plano anual selectivo (II-5): hidrómetros pontuados por idade, volume, classe/tecnologia e qualidade da água, seleccionados sob a restrição do tenant." />
      {!port ? (
        <Alert tone="warning" title="Fiação pendente">
          O plano ainda não está ligado ao servidor (pendências P-II5-INT-*). Nenhuma linha é mostrada sem fonte.
          A restrição anual e os parâmetros de score são definidos pelo tenant [A DEFINIR] (P-II5-PARAM).
        </Alert>
      ) : null}
      {error ? <Alert tone="error" title="Erro">{error}</Alert> : null}
      {port && !plan && !error ? <p>Sem plano fechado para {planYear}.</p> : null}
      {plan ? (
        <Card><CardContent>
          <p>Restrição: {restrictionLabel(plan.restriction)}
            {plan.minScore !== null ? ` · Piso de score: ${plan.minScore}` : ' · Sem piso de score'}</p>
          <p>Elegíveis: {plan.eligibleCount} · Excluídos por estado: {plan.excludedByStatusCount}
            {' '}· Abaixo do piso: {plan.excludedBelowMinScoreCount}
            {' '}· Pela restrição: {plan.excludedByRestrictionCount}</p>
          <p>Custo total estimado: {plan.totalEstimatedCost ?? 'desconhecido (há linha sem custo registado)'}</p>
          {plan.lines.length === 0 ? <p>O plano não seleccionou nenhum hidrómetro.</p> : (
            <Table caption={`Plano de troca ${planYear}`} mobileCards>
              <TableHeader><TableRow>
                <TableHead>#</TableHead><TableHead>Hidrómetro</TableHead><TableHead>Unidade</TableHead>
                <TableHead>Score</TableHead><TableHead>Critério dominante</TableHead>
                <TableHead>Relógio metrológico</TableHead><TableHead>Próx. verificação</TableHead>
                <TableHead>Custo de troca</TableHead>
              </TableRow></TableHeader>
              <TableBody>
                {plan.lines.map((l) => (
                  <TableRow key={l.meterId}>
                    <TableCell label="#">{l.position}</TableCell>
                    <TableCell label="Hidrómetro">{l.meterNumber}</TableCell>
                    <TableCell label="Unidade">{l.unitId}</TableCell>
                    <TableCell label="Score">{l.totalScore}</TableCell>
                    <TableCell label="Critério dominante">{CRITERION_LABEL[l.dominantCriterion]}</TableCell>
                    <TableCell label="Relógio metrológico">
                      <Badge variant={l.clockStatus === 'OVERDUE' ? 'danger' : 'default'}>{CLOCK_LABEL[l.clockStatus]}</Badge>
                    </TableCell>
                    <TableCell label="Próx. verificação">{l.nextVerificationOn ?? '—'}</TableCell>
                    <TableCell label="Custo de troca">{l.replacementCost ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {tenantId ? (
            <p><Button size="sm" variant="outline" onClick={() => void previewPayloads()}>
              Pré-visualizar ordens de serviço do plano</Button></p>
          ) : (
            <p>Pré-visualização das ordens de serviço: o tenant não foi fornecido a esta tela
              (fiação P-II5-INT-* pendente); abrir as OS é o dispatch da integração, não desta tela.</p>
          )}
          {payloads ? (
            payloads.length === 0 ? <p>O plano não gera nenhuma ordem de serviço.</p> : (
              <Table caption={`Ordens de serviço do plano ${planYear}`} mobileCards>
                <TableHeader><TableRow>
                  <TableHead>#</TableHead><TableHead>Hidrómetro</TableHead>
                  <TableHead>Tipo de serviço</TableHead><TableHead>Alvo</TableHead><TableHead>Score</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                  {payloads.map((p) => (
                    <TableRow key={p.meterId}>
                      <TableCell label="#">{p.position}</TableCell>
                      <TableCell label="Hidrómetro">{p.meterNumber}</TableCell>
                      <TableCell label="Tipo de serviço">{p.serviceTypeCode}</TableCell>
                      <TableCell label="Alvo">{p.targetClass}</TableCell>
                      <TableCell label="Score">{p.totalScore}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>)
          ) : null}
        </CardContent></Card>
      ) : null}
    </main>
  )
}
