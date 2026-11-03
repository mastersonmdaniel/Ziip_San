import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Icon } from '@components/ui/Icon'
import { DeleteReasonDialog } from '@components/registry/DeleteReasonDialog'
import { Timeline, type TimelineEntry } from '@components/registry/Timeline'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { Tabs } from '@components/ui/Tabs'
import { useCan } from '@hooks/usePermissions'
import { errorMessage, shouldOfferRetry, toApiError } from '@services/registry/errors'
import { consumerUnits } from '@services/registry/resources'
import type { ConsumerUnit, HistoryEvent, Ref, UnitCustomerLink } from '@services/registry/types'
import { formatDate, formatNumber, orDash, RELATION_LABEL } from '@utils/labels'
import { formatInscription } from '@utils/validators/inscription'
import { formatMatricula } from '@utils/validators/matricula'
import { UNIT_SHEET_TABS, unitSheetTabById } from './unitSheetTabs'

type TabId = string

// P1.12: as abas vêm da regra da P0.47 (unitSheetTabs.ts): Cadastro, Clientes e
// Histórico são da própria ficha; Contas, Débitos, Qualidade e Documentos são
// as abas declaradas no manifesto e aparecem com aviso até o módulo ter fonte.
const TABS = UNIT_SHEET_TABS.map((tab) => ({ id: tab.id, label: tab.label }))

function refText(ref: Ref | null | undefined): string {
  if (!ref) return '—'
  return ref.code !== undefined && ref.code !== null && ref.code !== '' ? `${ref.code} · ${ref.name}` : ref.name
}

function DescriptionList({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
      {items.map(([term, value]) => (
        <div key={term}>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{term}</dt>
          <dd className="mt-0.5 text-sm">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle as="h2" className="text-base">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

function RegistrationTab({ unit }: { unit: ConsumerUnit }) {
  const a = unit.address
  const coords = a.coordinates ? `${a.coordinates.lat.toFixed(6).replace('.', ',')}, ${a.coordinates.lng.toFixed(6).replace('.', ',')}` : '—'
  return (
    <div className="space-y-4">
      <Section title="Localização">
        <DescriptionList
          items={[
            ['Localidade', refText(unit.locality)],
            ['Setor comercial', refText(unit.sector)],
            ['Quadra', refText(unit.block)],
            ['Face da quadra', refText(unit.blockFace)],
            ['Lote', String(unit.lot).padStart(4, '0')],
            ['Sublote', String(unit.sublot).padStart(3, '0')],
            ['Sequência na rota', orDash(unit.readingSequence)],
            ['Testada do lote (m)', formatNumber(unit.lotFrontageM, 2)],
          ]}
        />
      </Section>

      <Section title="Endereço">
        <DescriptionList
          items={[
            ['CEP', orDash(a.cep)],
            ['Logradouro', a.streetName],
            ['Número', a.number],
            ['Complemento', orDash(a.complement)],
            ['Bairro', orDash(a.neighborhood)],
            ['Município/UF', [a.city, a.state].filter(Boolean).join('/') || '—'],
            ['Coordenadas (SIRGAS 2000)', coords],
          ]}
        />
      </Section>

      <Section title="Categorias e economias">
        {unit.subcategories.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhuma subcategoria registrada.</p>
        ) : (
          <Table caption="Subcategorias e economias do imóvel">
            <TableHeader>
              <TableRow>
                <TableHead>Categoria</TableHead>
                <TableHead>Subcategoria</TableHead>
                <TableHead className="text-right">Economias</TableHead>
                <TableHead className="text-right">Un. privativas</TableHead>
                <TableHead className="text-right">Un. coletivas</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {unit.subcategories.map((s) => (
                <TableRow key={s.subcategoryId}>
                  <TableCell>{refText(s.category)}</TableCell>
                  <TableCell>{refText(s.subcategory)}</TableCell>
                  <TableCell className="text-right">{s.economies}</TableCell>
                  <TableCell className="text-right">{orDash(s.privateUnits)}</TableCell>
                  <TableCell className="text-right">{orDash(s.collectiveUnits)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <p className="mt-3 text-sm text-muted-foreground">
          Categoria principal: <span className="font-medium text-foreground">{refText(unit.mainCategory)}</span> · Subcategoria principal:{' '}
          <span className="font-medium text-foreground">{refText(unit.mainSubcategory)}</span> · Total de economias:{' '}
          <span className="font-medium text-foreground">{unit.economies}</span> (derivados pelo servidor)
        </p>
      </Section>

      <Section title="Características">
        <DescriptionList
          items={[
            ['Área construída (m²)', formatNumber(unit.builtAreaM2, 2)],
            ['Faixa de área (derivada)', orDash(unit.builtAreaRange)],
            ['Situação de água', refText(unit.waterStatus)],
            ['Situação de esgoto', refText(unit.sewerStatus)],
            ['Esgotamento', refText(unit.sewerDischarge)],
            ['Perfil do imóvel', refText(unit.profile)],
            ['Fonte de abastecimento', refText(unit.waterSource)],
            ['Tipo de poço', refText(unit.wellType)],
            ['Pavimento da rua', refText(unit.pavementStreet)],
            ['Pavimento da calçada', refText(unit.pavementSidewalk)],
            ['Jardim', unit.physical?.garden === null || unit.physical === null ? '—' : unit.physical.garden ? 'Sim' : 'Não'],
            ['Reservatório inferior (m³)', formatNumber(unit.physical?.lowerReservoirM3, 2)],
            ['Reservatório superior (m³)', formatNumber(unit.physical?.upperReservoirM3, 2)],
            ['Piscina (m³)', formatNumber(unit.physical?.poolM3, 2)],
          ]}
        />
      </Section>

      <Section title="Conclusão">
        <DescriptionList
          items={[
            ['Pontos de utilização', orDash(unit.usagePoints)],
            ['Número de moradores', orDash(unit.residents)],
            ['IPTU', orDash(unit.externalIds?.iptu)],
            ['Modo de envio da conta', refText(unit.deliveryMode)],
            ['Rota de entrega', refText(unit.deliveryRoute)],
            ['Sequência de entrega', orDash(unit.deliverySequence)],
            ['Rota alternativa', refText(unit.altRoute)],
            ['Imóvel principal', refText(unit.parentUnit)],
            ['Observações', orDash(unit.notes)],
          ]}
        />
      </Section>
    </div>
  )
}

function CustomersTab({ unitId }: { unitId: string }) {
  const query = useQuery({
    queryKey: ['consumer-unit', unitId, 'customers'],
    queryFn: ({ signal }) => consumerUnits.customers(unitId, signal),
  })

  if (query.isLoading) return <Spinner label="Carregando clientes..." />
  if (query.isError) return <Alert tone="error" title="Não foi possível carregar os clientes">{errorMessage(query.error)}</Alert>
  const links = query.data ?? []
  const current = links.filter((l) => l.endDate === null)
  const past = links.filter((l) => l.endDate !== null)

  const renderTable = (rows: UnitCustomerLink[], caption: string, showEnd: boolean) => (
    <Table caption={caption}>
      <TableHeader>
        <TableRow>
          <TableHead>Cliente</TableHead>
          <TableHead>Documento</TableHead>
          <TableHead>Relação</TableHead>
          <TableHead>Início</TableHead>
          {showEnd ? <TableHead>Fim</TableHead> : null}
          {showEnd ? <TableHead>Motivo do fim</TableHead> : null}
          {!showEnd ? <TableHead>Nome na conta</TableHead> : null}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((l) => (
          <TableRow key={l.id}>
            <TableCell>
              <Link to={`/cadastro/clientes/${l.customerId}`} className="font-medium text-primary underline underline-offset-4">
                {l.customerName}
              </Link>
            </TableCell>
            <TableCell className="font-mono text-xs">
              <span className="sr-only">Documento mascarado: </span>
              {orDash(l.customerDocumentMasked)}
            </TableCell>
            <TableCell>{RELATION_LABEL[l.relation]}</TableCell>
            <TableCell>{formatDate(l.startDate)}</TableCell>
            {showEnd ? <TableCell>{formatDate(l.endDate)}</TableCell> : null}
            {showEnd ? <TableCell>{orDash(l.endReason)}</TableCell> : null}
            {!showEnd ? <TableCell>{l.showOnBill ? <Badge variant="info">Sim</Badge> : 'Não'}</TableCell> : null}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  return (
    <div className="space-y-6">
      <section aria-labelledby="clientes-vigentes">
        <h2 id="clientes-vigentes" className="mb-2 text-base font-semibold">
          Vínculos vigentes
        </h2>
        {current.length === 0 ? <p className="text-sm text-muted-foreground">Nenhum vínculo vigente.</p> : renderTable(current, 'Clientes vinculados ao imóvel (vigentes)', false)}
      </section>
      <section aria-labelledby="clientes-historico">
        <h2 id="clientes-historico" className="mb-2 text-base font-semibold">
          Vínculos encerrados
        </h2>
        {past.length === 0 ? <p className="text-sm text-muted-foreground">Nenhum vínculo encerrado.</p> : renderTable(past, 'Clientes vinculados ao imóvel (encerrados)', true)}
      </section>
    </div>
  )
}

const EVENT_TAG: Record<HistoryEvent['type'], string> = {
  STATUS: 'Situação',
  INSCRIPTION: 'Inscrição',
  AUDIT: 'Auditoria',
}

function HistoryTab({ unitId }: { unitId: string }) {
  const query = useQuery({
    queryKey: ['consumer-unit', unitId, 'history'],
    queryFn: ({ signal }) => consumerUnits.history(unitId, signal),
  })
  if (query.isLoading) return <Spinner label="Carregando histórico..." />
  if (query.isError) return <Alert tone="error" title="Não foi possível carregar o histórico">{errorMessage(query.error)}</Alert>
  const entries: TimelineEntry[] = (query.data ?? []).map((e) => ({
    id: e.id,
    at: e.occurredAt,
    title: e.title,
    tag: EVENT_TAG[e.type],
    actor: e.actor,
    changes: e.changes,
  }))
  return <Timeline entries={entries} emptyText="Nenhum evento registrado para este imóvel." />
}

/** S3: ficha do imóvel (cabeçalho fixo + abas). Rota: /cadastro/imoveis/:id */
export function PropertyDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<TabId>('cadastro')
  const [deleting, setDeleting] = useState(false)
  const canWrite = useCan('registry:write')

  const query = useQuery({
    queryKey: ['consumer-unit', id],
    queryFn: ({ signal }) => consumerUnits.get(id, signal),
    enabled: id !== '',
  })

  if (query.isLoading) return <Spinner label="Carregando imóvel..." />
  if (query.isError || !query.data) {
    const err = toApiError(query.error)
    return (
      <div className="space-y-4">
        <PageHeader title="Ficha do imóvel" />
        <Alert tone="error" title={err.kind === 'not-found' ? 'Imóvel não encontrado' : 'Não foi possível carregar o imóvel'}>
          <p>{err.message}</p>
          <div className="mt-2 flex gap-2">
            {shouldOfferRetry(query.error) ? (
              <Button size="sm" variant="outline" onClick={() => void query.refetch()}>
                Tentar novamente
              </Button>
            ) : null}
            <Link to="/cadastro/imoveis" className="inline-flex h-9 items-center rounded-md px-3 text-sm font-medium underline underline-offset-4">
              Voltar à pesquisa
            </Link>
          </div>
        </Alert>
      </div>
    )
  }

  const unit = query.data
  const deleted = unit.deletedAt !== null
  const matricula = formatMatricula(unit.registration, unit.registrationDv)

  return (
    <div className="space-y-6">
      <PageHeader
        title={`Imóvel ${matricula}`}
        actions={
          !deleted && canWrite ? (
            <>
              <Link
                to={`/cadastro/imoveis/${unit.id}/editar`}
                className="inline-flex h-10 items-center gap-2 rounded-lg border bg-background px-4 text-sm font-medium hover:bg-accent"
              >
                <Icon name="pencil" className="h-4 w-4" />
                Editar
              </Link>
              <Button variant="outline" onClick={() => setDeleting(true)}>
                <Icon name="trash-2" className="h-4 w-4" />
                Excluir
              </Button>
            </>
          ) : null
        }
      />

      {/* Cabeçalho fixo: identifica o imóvel em todas as abas. */}
      <section aria-label="Resumo do imóvel" className="sticky top-16 z-10 rounded-xl border bg-card p-4 shadow-sm">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
          <p className="font-mono text-lg font-bold">{matricula}</p>
          <p className="font-mono text-sm">
            <span className="sr-only">Inscrição: </span>
            {formatInscription(unit.inscription)}
          </p>
          <p className="text-sm">
            Água: <Badge variant="secondary">{unit.waterStatus?.name ?? 'Não informada'}</Badge>
          </p>
          <p className="text-sm">
            Esgoto: <Badge variant="secondary">{unit.sewerStatus?.name ?? 'Não informada'}</Badge>
          </p>
          {unit.status === 'PROVISIONAL' ? <Badge variant="warning">Cadastro provisório</Badge> : null}
          {deleted ? <Badge variant="destructive">Excluído</Badge> : null}
        </div>
        <p className="mt-2 flex items-start gap-1.5 text-sm text-muted-foreground">
          <Icon name="map-pin" className="mt-0.5 h-4 w-4 shrink-0" />
          {unit.addressText}
        </p>
      </section>

      {deleted ? (
        <Alert tone="warning" title="Este imóvel foi excluído">
          Exclusão em {formatDate(unit.deletedAt)}. Motivo: {orDash(unit.deletedReason)}. O histórico continua disponível.
        </Alert>
      ) : null}

      <Tabs tabs={TABS} active={tab} onChange={(t) => setTab(t as TabId)} idPrefix="ficha" label="Seções da ficha do imóvel">
        {tab === 'cadastro' ? <RegistrationTab unit={unit} /> : null}
        {tab === 'clientes' ? <CustomersTab unitId={unit.id} /> : null}
        {tab === 'historico' ? <HistoryTab unitId={unit.id} /> : null}
        {unitSheetTabById(tab)?.available === false ? (
          <p className="text-sm text-muted-foreground">{unitSheetTabById(tab)?.unavailableText}</p>
        ) : null}
      </Tabs>

      {deleting ? (
        <DeleteReasonDialog
          title="Excluir imóvel"
          description={`A exclusão é lógica: o imóvel ${matricula} deixa de aparecer nas pesquisas, mas o histórico é preservado.`}
          onClose={() => setDeleting(false)}
          onConfirm={async (reason) => {
            await consumerUnits.remove(unit.id, reason)
            setDeleting(false)
            await queryClient.invalidateQueries({ queryKey: ['consumer-unit', unit.id] })
            await queryClient.invalidateQueries({ queryKey: ['consumer-units'] })
            navigate('/cadastro/imoveis')
          }}
        />
      ) : null}
    </div>
  )
}
