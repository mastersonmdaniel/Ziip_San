import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert } from '@components/ui/Alert'
import { Button } from '@components/ui/Button'
import { Dialog } from '@components/ui/Dialog'
import { getReleaseNotes, markReleaseNoteViewed } from '@services/about'
import { errorMessage } from '@services/registry/errors'
import { formatDate } from '@utils/labels'

/**
 * P1.24: "Novidades" — abre uma vez por versão por usuário (ADR-0021 decisão 8).
 *
 * Montado dentro da área privada (só renderiza autenticado): busca as
 * novidades e abre o diálogo quando há versão não vista. O estado "visto"
 * vive no SERVIDOR (support.release_note_view), não no navegador: outro
 * dispositivo do mesmo usuário não reexibe o que já foi visto.
 *
 * Fechar com "Entendi" marca todas as versões exibidas como vistas.
 * "Lembrar depois" fecha sem marcar — na próxima sessão o diálogo reabre,
 * porque a novidade continua não vista. Isso é o comportamento correto,
 * não um defeito: "uma vez" conta a partir do visto, não da exibição.
 */
export function WhatsNew() {
  const queryClient = useQueryClient()
  const notes = useQuery({ queryKey: ['release-notes'], queryFn: getReleaseNotes })
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const unviewed = (notes.data ?? []).filter((n) => !n.viewed)

  useEffect(() => {
    if (!notes.isSuccess) return
    if (unviewed.length > 0) setOpen(true)
    // Abrir só quando o fetch resolve; reabrir depois de "lembrar depois"
    // acontece na próxima montagem (próxima sessão), não a cada render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [notes.isSuccess])

  const markAll = useMutation({
    mutationFn: async () => {
      for (const note of unviewed) {
        await markReleaseNoteViewed(note.version)
      }
    },
    onSuccess: async () => {
      setError(null)
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['release-notes'] })
    },
    onError: (e) => setError(errorMessage(e)),
  })

  if (!notes.isSuccess || unviewed.length === 0) return null

  return (
    <Dialog
      open={open}
      onClose={() => setOpen(false)}
      title="Novidades"
      description="O que mudou nesta versão. Esta janela aparece uma vez por versão por usuário."
      footer={
        <>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Lembrar depois
          </Button>
          <Button loading={markAll.isPending} onClick={() => markAll.mutate()}>
            Entendi
          </Button>
        </>
      }
    >
      {error ? (
        <Alert tone="error" title="Não foi possível marcar as novidades como vistas">
          {error}
        </Alert>
      ) : null}
      <ul className="space-y-4">
        {unviewed.map((n) => (
          <li key={n.version}>
            <p className="font-medium">
              Versão {n.version} · {formatDate(n.date)}
            </p>
            <ul className="mt-1 list-disc space-y-1 pl-5 text-sm">
              {n.entries.map((e, i) => (
                <li key={`${n.version}-${i}`}>{e.title}</li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </Dialog>
  )
}
