import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Spinner } from '@components/ui/Alert'
import { Badge } from '@components/ui/Badge'
import { Button } from '@components/ui/Button'
import { Card, CardContent } from '@components/ui/Card'
import { PageHeader } from '@components/ui/PageHeader'
import { FRONTEND_LICENSES } from '../brand/licenses'
import {
  acceptDocument,
  getAbout,
  getLicenses,
  getReleaseNotes,
  markReleaseNoteViewed,
  type AboutDocument,
} from '@services/about'
import { errorMessage } from '@services/registry/errors'
import { formatDate } from '@utils/labels'

/**
 * P1.24: tela /about — Sobre completo (ADR-0021; AJUDA §9).
 *
 * Mostra marca e versão (do build, nunca escrita à mão), documentos legais
 * com a versão vigente e a aceita PELO PRÓPRIO usuário (aceitar é um ato
 * do usuário: o botão aceita a versão vigente e o servidor grava a
 * evidência — hash, data, IP e autenticação), licenças de terceiros e a
 * relação de atualizações com o visto por versão.
 *
 * Honestidade da tela: sem documento publicado no catálogo, a seção de
 * documentos fica vazia (não se inventa Termos/Política/Contrato/DPA);
 * sem o SBOM embalado na imagem, as licenças do servidor aparecem como
 * indisponíveis — as do frontend (P0.46) aparecem sempre.
 */
function DocumentRow({ document }: { document: AboutDocument }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const accept = useMutation({
    mutationFn: () => acceptDocument(document.code),
    onSuccess: async () => {
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['about'] })
    },
    onError: (e) => setError(errorMessage(e)),
  })

  return (
    <li className="flex flex-wrap items-center gap-3 border-b py-3 last:border-b-0">
      <div className="min-w-0 flex-1">
        <p className="font-medium">{document.title}</p>
        <p className="text-sm text-muted-foreground">
          Versão vigente: {document.currentVersion}
          {document.acceptedVersion
            ? ` · Aceita: ${document.acceptedVersion} em ${formatDate(document.acceptedAt)}`
            : ' · Ainda não aceita'}
        </p>
        {error ? <p className="text-sm text-destructive">{error}</p> : null}
      </div>
      {document.upToDate ? (
        <Badge variant="success">Versão vigente aceita</Badge>
      ) : (
        <Button size="sm" loading={accept.isPending} onClick={() => accept.mutate()}>
          Aceitar a versão vigente
        </Button>
      )}
    </li>
  )
}

export function About() {
  const about = useQuery({ queryKey: ['about'], queryFn: getAbout })
  const licenses = useQuery({ queryKey: ['about-licenses'], queryFn: getLicenses })
  const notes = useQuery({ queryKey: ['release-notes'], queryFn: getReleaseNotes })
  const queryClient = useQueryClient()
  const [notesError, setNotesError] = useState<string | null>(null)
  const markViewed = useMutation({
    mutationFn: (version: string) => markReleaseNoteViewed(version),
    onSuccess: async () => {
      setNotesError(null)
      await queryClient.invalidateQueries({ queryKey: ['release-notes'] })
    },
    onError: (e) => setNotesError(errorMessage(e)),
  })

  if (about.isPending) return <Spinner label="Carregando o Sobre..." />
  if (about.isError || !about.data) {
    return (
      <Alert tone="error" title="Não foi possível carregar o Sobre">
        {errorMessage(about.error)}
      </Alert>
    )
  }

  const info = about.data
  const unviewed = (notes.data ?? []).filter((n) => !n.viewed)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Sobre"
        description={info.brand.description}
      />

      <Card>
        <CardContent className="space-y-2 pt-6">
          <h2 className="text-lg font-semibold">{info.brand.productName}</h2>
          <p>{info.brand.aboutText}</p>
          <dl className="grid gap-1 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Versão</dt>
              <dd>{info.version}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Commit</dt>
              <dd>{info.commit}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Ambiente</dt>
              <dd>{info.environment}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Implantação</dt>
              <dd>{info.deployedAt ? formatDate(info.deployedAt) : '—'}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <h2 className="text-lg font-semibold">Documentos</h2>
          {info.documents.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Nenhum documento publicado ainda. Quando Termos, Política, Contrato ou DPA forem
              publicados, eles aparecem aqui com a versão vigente e o seu aceite.
            </p>
          ) : (
            <ul className="mt-2">
              {info.documents.map((d) => (
                <DocumentRow key={d.code} document={d} />
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <h2 className="text-lg font-semibold">Licenças de terceiros</h2>
          {licenses.isPending ? (
            <Spinner label="Carregando licenças..." />
          ) : licenses.isError ? (
            <Alert tone="error" title="Não foi possível carregar as licenças do servidor">
              {errorMessage(licenses.error)}
            </Alert>
          ) : (
            <>
              {!licenses.data.available ? (
                <p className="mt-2 text-sm text-muted-foreground">
                  Licenças do servidor indisponíveis nesta implantação (o SBOM ainda não está
                  embalado na imagem). Abaixo, as licenças do próprio aplicativo.
                </p>
              ) : null}
              <ul className="mt-2 space-y-1 text-sm">
                {licenses.data.licenses.map((l) => (
                  <li key={`${l.name}@${l.version}`}>
                    {l.name} {l.version} — {l.licenses.join(', ') || 'sem licença informada'}
                  </li>
                ))}
                {FRONTEND_LICENSES.map((l) => (
                  <li key={`${l.name}@${l.version}`}>
                    {l.name} {l.version} — {l.license} <span className="text-muted-foreground">({l.note})</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <h2 className="text-lg font-semibold">Novidades</h2>
          {notes.isPending ? (
            <Spinner label="Carregando novidades..." />
          ) : notes.isError ? (
            <Alert tone="error" title="Não foi possível carregar as novidades">
              {errorMessage(notes.error)}
            </Alert>
          ) : (notes.data ?? []).length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Nenhuma novidade publicada ainda. Cada versão lançada aparece aqui uma vez por usuário.
            </p>
          ) : (
            <>
              {notesError ? (
                <Alert tone="error" title="Não foi possível marcar a novidade como vista">
                  {notesError}
                </Alert>
              ) : null}
              {unviewed.length > 0 ? (
                <p className="mt-2 text-sm text-muted-foreground">
                  {unviewed.length === 1 ? '1 novidade não vista' : `${unviewed.length} novidades não vistas`}
                </p>
              ) : null}
              <ul className="mt-2 space-y-4">
                {notes.data.map((n) => (
                  <li key={n.version} className="border-b pb-4 last:border-b-0">
                    <p className="flex flex-wrap items-center gap-2 font-medium">
                      Versão {n.version} · {formatDate(n.date)}
                      {n.viewed ? <Badge variant="secondary">Vista</Badge> : <Badge variant="info">Nova</Badge>}
                    </p>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-sm">
                      {n.entries.map((e, i) => (
                        <li key={`${n.version}-${i}`}>{e.title}</li>
                      ))}
                    </ul>
                    {!n.viewed ? (
                      <Button
                        size="sm"
                        variant="outline"
                        className="mt-2"
                        loading={markViewed.isPending}
                        onClick={() => markViewed.mutate(n.version)}
                      >
                        Marcar como vista
                      </Button>
                    ) : null}
                  </li>
                ))}
              </ul>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
