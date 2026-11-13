import { useState } from 'react'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@components/ui/Card'
import { Dialog } from '@components/ui/Dialog'
import { Checkbox, Field, Input, Select, Textarea } from '@components/ui/Field'
import { Icon, type IconName } from '@components/ui/Icon'
import { PageHeader } from '@components/ui/PageHeader'
import { Pagination } from '@components/ui/Pagination'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@components/ui/Table'
import { PermissionsProvider, useCan } from '@hooks/usePermissions'

/**
 * Pagina-vitrine do tema (pacote tema-front-web): usa SO componentes e
 * superficies desta biblioteca, para print posterior e para os testes de
 * interacao do pacote. Nao e uma tela de produto nem substitui nenhuma
 * das 21 telas entregues — essas aguardam os ajustes do dono
 * (decisao de 09/11/2026 02:22) e nao sao tocadas por este pacote.
 *
 * Extensao P-TEMA-CORR-02 (frente telas-onda2, pacote temas-componentes):
 * a vitrine passa a exercer os casos que faltavam — Field com filho em
 * fragmento, Field com multiplos controlos (associacao por grupo) e
 * Icon com nome desconhecido (fallback marcado da biblioteca) — e a
 * composicao CardHeader/CardTitle da P-TEMA-06.
 */
function PermissaoDemo() {
  const canWrite = useCan('registry:write')
  return (
    <p>
      useCan('registry:write') com PermissionsProvider[registry:write]:{' '}
      <strong>{canWrite ? 'permitido' : 'negado'}</strong>
    </p>
  )
}

export function Showcase() {
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(25)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [nome, setNome] = useState('')
  const [motivo, setMotivo] = useState('')
  const [aceite, setAceite] = useState(false)

  return (
    <PermissionsProvider permissions={['registry:write']}>
      <div className="ui-root">
        <PageHeader
          title="Vitrine do tema — ZiipSan"
          description="Biblioteca compartilhada do front web (pacote tema-front-web)."
          actions={
            <Button onClick={() => setDialogOpen(true)}>
              <Icon name="plus" className="h-4 w-4" />
              Abrir diálogo
            </Button>
          }
        />

        <Alert tone="success">Alert tone=success: registo guardado (demonstração).</Alert>
        <Alert variant="warning">Alert variant=warning: atenção (demonstração).</Alert>
        <Alert tone="error" title="Alert tone=error com título">
          <p>Mensagem de erro de demonstração.</p>
        </Alert>
        <Spinner label="Carregando demonstração..." />

        <Card>
          <CardContent>
            <h2>
              Cartão com emblemas <Badge>default</Badge> <Badge variant="success">success</Badge>{' '}
              <Badge variant="danger">danger</Badge> <Badge variant="info">info</Badge>{' '}
              <Badge variant="secondary">secondary</Badge>
            </h2>
            <PermissaoDemo />
            <p>
              Botões: <Button>Primário</Button> <Button variant="outline">Outline</Button>{' '}
              <Button variant="ghost">Ghost</Button> <Button variant="secondary">Secundário</Button>{' '}
              <Button variant="destructive">Destrutivo</Button> <Button loading>A carregar</Button>{' '}
              <Button size="sm" variant="outline">
                Pequeno
              </Button>
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <h2>Campos — as duas convenções do Field</h2>
            <Field label="Nome (filho como função de render)" required hint="Convenção da ED-08/P3.25.">
              {(aria) => <Input {...aria} value={nome} onChange={(e) => setNome(e.target.value)} />}
            </Field>
            <Field label="Motivo (filho direto)" error={motivo ? null : 'Preencha o motivo (demonstração de erro).'}>
              <Input value={motivo} onChange={(e) => setMotivo(e.target.value)} />
            </Field>
            <Field label="Tipo (função de render)" id="showcase-tipo">
              {(aria) => (
                <Select {...aria} defaultValue="a">
                  <option value="a">Opção A</option>
                  <option value="b">Opção B</option>
                </Select>
              )}
            </Field>
            <Field label="Observações (filho direto)">
              <Textarea placeholder="Texto livre" />
            </Field>
            <Checkbox label="Aceito a demonstração" hint="Checkbox da biblioteca." checked={aceite} onChange={(e) => setAceite(e.target.checked)} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Cabeçalho do cartão (P-TEMA-06)</CardTitle>
          </CardHeader>
          <CardContent>
            <p>Composição Card + CardHeader + CardTitle, como as telas de Modelos e de cadastro a usam. Com nível explícito:</p>
            <CardTitle as="h2">Título em h2 (as="h2")</CardTitle>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <h2>Casos-limite do Field e do Icon (P-TEMA-CORR-02)</h2>
            <Field label="Campo em fragmento" hint="Um único controle dentro de um fragmento: o rótulo continua associado a ele.">
              <>
                <Input placeholder="Controle dentro do fragmento" />
              </>
            </Field>
            <Field
              label="Grupo de controles"
              hint="Vários controles sob um mesmo rótulo: a associação faz-se por grupo (role=group), sem label órfão."
            >
              <Input placeholder="Primeiro controle do grupo" />
              <Input placeholder="Segundo controle do grupo" />
            </Field>
            <p>
              Ícone com nome desconhecido (fallback da biblioteca: glifo de recurso marcado, sem partir a página):{' '}
              {/* R41-4-29/30: o nome e intencionalmente desconhecido (o cast
                  atravessa o IconName fechado do registo restaurado); o
                  fallback de runtime (R2-F9) e o que esta linha demonstra. */}
              <Icon name={'icone-que-nao-existe' as IconName} /> <span>nome "icone-que-nao-existe"</span>
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Table caption="Tabela da vitrine" mobileCards>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell label="Nome">Registo um</TableCell>
                  <TableCell label="Estado">
                    <Badge variant="success">Ativo</Badge>
                  </TableCell>
                  <TableCell label="Ações" className="text-right">
                    <Button size="sm" variant="ghost">
                      <Icon name="pencil" className="h-4 w-4" /> Editar
                    </Button>
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell label="Nome">Registo dois</TableCell>
                  <TableCell label="Estado">
                    <Badge>—</Badge>
                  </TableCell>
                  <TableCell label="Ações" className="text-right">
                    <Button size="sm" variant="ghost">
                      <Icon name="trash-2" className="h-4 w-4" /> Excluir
                    </Button>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
            <Pagination
              page={page}
              totalPages={3}
              totalElements={60}
              onPageChange={setPage}
              pageSize={pageSize}
              onPageSizeChange={(size) => {
                setPageSize(size)
                setPage(0)
              }}
            />
            <p aria-live="polite">
              Página actual da vitrine: {page + 1}; tamanho: {pageSize}
            </p>
          </CardContent>
        </Card>

        <Dialog
          open={dialogOpen}
          onClose={() => setDialogOpen(false)}
          title="Diálogo da vitrine"
          description="Campos marcados com * são obrigatórios."
          footer={
            <>
              <Button variant="outline" onClick={() => setDialogOpen(false)}>
                Cancelar
              </Button>
              <Button onClick={() => setDialogOpen(false)}>Confirmar</Button>
            </>
          }
        >
          <Field label="Motivo da exclusão" required hint="Mínimo de 3 caracteres.">
            {(aria) => <Textarea {...aria} data-autofocus placeholder="Motivo" />}
          </Field>
        </Dialog>
      </div>
    </PermissionsProvider>
  )
}
