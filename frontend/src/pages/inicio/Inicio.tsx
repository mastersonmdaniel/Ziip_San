import { useQuery } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import { home } from '@services/home/home'

/**
 * P3.39 — "Inicio" (item de menu Inicio, rota /inicio, a pagina atribuida a
 * todo perfil de fabrica pela P1.37): a composicao vem PRONTA do servidor
 * (GET /me/home?profile=...) — atalhos do perfil, minhas tarefas ja
 * filtradas pelo escopo do usuario (P2.28), avisos da central (P3.36),
 * alertas do tenant (ED-07, quando existir) e no maximo quatro indicadores
 * do papel (registro da P3.41). Esta tela nao decide composicao nenhuma.
 *
 * REGISTRO NO MENU (integracao): item "Inicio" como PRIMEIRO item do menu
 * principal, rota /inicio, sem permissao propria (a pagina e do proprio
 * usuario; o servidor so compoe o que e dele). O perfil ativo vem da
 * sessao (atribuicoes da P1.37) — a fiacao resolve o codigo e o injeta
 * nesta pagina; aqui ele chega pela prop `profile`.
 *
 * Perfil desligado (auditor externo e regulador ate a ED-75): o servidor
 * devolve profileEnabled=false e secoes vazias; a tela declara isso em vez
 * de montar uma pagina vazia silenciosa.
 */
const SEMAPHORE_VARIANT: Record<string, 'success' | 'warning' | 'danger' | 'default'> = {
  GREEN: 'success',
  YELLOW: 'warning',
  RED: 'danger',
  NO_TARGET: 'default',
}

export function Inicio({ profile }: { profile: string }) {
  const query = useQuery({
    queryKey: ['home', profile],
    queryFn: () => home.page(profile),
  })
  const page = query.data

  return (
    <div>
      <PageHeader title="Inicio" />
      {query.isLoading ? <Spinner /> : null}
      {query.isError ? <Alert variant="danger">{errorMessage(query.error)}</Alert> : null}
      {page && !page.profileEnabled ? (
        <Alert variant="warning">
          Perfil {page.profileLabel} desligado nesta instalacao: o acesso por este perfil
          aguarda a liberacao prevista no produto.
        </Alert>
      ) : null}
      {page && page.profileEnabled ? (
        <div>
          <Card>
            <CardContent>
              <h2>Atalhos</h2>
              <ul>
                {page.shortcuts.map((s) => (
                  <li key={s.code}>
                    <a href={s.route}>{s.label}</a>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
          <Card>
            <CardContent>
              <h2>
                Minhas tarefas{' '}
                {page.workItemsOverdue > 0 ? (
                  <Badge variant="danger">{page.workItemsOverdue} vencida(s)</Badge>
                ) : null}
              </h2>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tipo</TableHead>
                    <TableHead>Origem</TableHead>
                    <TableHead>Prazo</TableHead>
                    <TableHead>Estado</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {page.workItems.map((i) => (
                    <TableRow key={i.id}>
                      <TableCell>{i.type}</TableCell>
                      <TableCell>{i.originModule}</TableCell>
                      <TableCell>{i.dueAt ?? 'sem prazo'}</TableCell>
                      <TableCell>{i.status}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
          <Card>
            <CardContent>
              <h2>
                Avisos{' '}
                {page.notificationsUnread > 0 ? (
                  <Badge>{page.notificationsUnread} nao lido(s)</Badge>
                ) : null}
              </h2>
              <ul>
                {page.notifications.map((n) => (
                  <li key={n.id}>{n.titleCode}</li>
                ))}
              </ul>
            </CardContent>
          </Card>
          {page.tenantAlerts.available ? (
            <Card>
              <CardContent>
                <h2>Alertas do tenant</h2>
                <ul>
                  {page.tenantAlerts.items.map((a) => (
                    <li key={a.code}>{a.title}</li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ) : null}
          <Card>
            <CardContent>
              <h2>Indicadores do papel</h2>
              <ul>
                {page.indicators.map((i) => (
                  <li key={i.code}>
                    {i.name}: {i.value} {i.unit}{' '}
                    <Badge variant={SEMAPHORE_VARIANT[i.semaphore] ?? 'default'}>
                      {i.semaphore}
                    </Badge>
                  </li>
                ))}
              </ul>
              {page.indicatorsTruncated ? (
                <p>
                  Exibindo {page.indicators.length} de {page.indicatorsTotal} indicadores
                  marcados para o perfil (teto da pagina inicial: quatro).
                </p>
              ) : null}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
