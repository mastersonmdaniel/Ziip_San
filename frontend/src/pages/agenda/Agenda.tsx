import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { errorMessage } from '@services/registry/errors'
import { home } from '@services/home/home'

/**
 * P3.39 — Agenda (somente leitura): eventos das fontes dos tipos
 * REGISTRADOS (nascem o cronograma de faturamento da P2.5 e as auditorias
 * da P3.31; os demais entram pela tarefa de cada fonte). Nao ha botao de
 * criar/editar porque a agenda nao cria nem altera evento: cada linha
 * leva ao registro na tela do modulo dono (recordAddress).
 *
 * REGISTRO NO MENU (integracao): item "Agenda" no menu principal, rota
 * /agenda, sem permissao propria. Tipo registrado sem fonte nesta
 * instalacao aparece declarado como indisponivel (sourceAvailable=false),
 * nunca como lista vazia silenciosa.
 */
export function Agenda() {
  const [from] = useState(() => new Date().toISOString())
  const [to] = useState(() => new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString())

  const types = useQuery({ queryKey: ['agenda-types'], queryFn: () => home.agendaTypes() })
  const events = useQuery({
    queryKey: ['agenda', from, to],
    queryFn: () => home.agenda(from, to),
  })

  return (
    <div>
      <PageHeader title="Agenda" />
      {types.data ? (
        <ul>
          {types.data.map((t) => (
            <li key={t.type.code}>
              {t.type.label}
              {t.sourceAvailable ? '' : ' — fonte ainda nao disponivel nesta instalacao'}
            </li>
          ))}
        </ul>
      ) : null}
      <Card>
        <CardContent>
          {events.isLoading ? <Spinner /> : null}
          {events.isError ? <Alert variant="danger">{errorMessage(events.error)}</Alert> : null}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Inicio</TableHead>
                <TableHead>Tipo</TableHead>
                <TableHead>Evento</TableHead>
                <TableHead>Registro</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(events.data ?? []).map((e) => (
                <TableRow key={e.id}>
                  <TableCell>{e.startsAt}</TableCell>
                  <TableCell>{e.typeCode}</TableCell>
                  <TableCell>{e.title}</TableCell>
                  <TableCell>
                    <a href={e.recordAddress}>Abrir na origem</a>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
