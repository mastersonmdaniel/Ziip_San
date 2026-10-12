import type { ReactNode } from 'react'
import { format, parseISO, isValid } from 'date-fns'
import { ptBR } from 'date-fns/locale'

export interface TimelineChange {
  field: string
  from: string | null
  to: string | null
}

export interface TimelineEntry {
  id: string
  /** ISO 8601. */
  at: string
  title: ReactNode
  /** Texto curto de apoio (ex.: tipo do evento). */
  tag?: string
  actor?: string | null
  changes?: TimelineChange[]
}

function formatWhen(iso: string): string {
  const date = parseISO(iso)
  return isValid(date) ? format(date, "dd/MM/yyyy 'às' HH:mm", { locale: ptBR }) : iso
}

/** Linha do tempo semântica (lista ordenada). A ordem recebida é preservada. */
export function Timeline({ entries, emptyText = 'Nenhum evento registrado.' }: { entries: TimelineEntry[]; emptyText?: string }) {
  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyText}</p>
  }
  return (
    <ol className="relative space-y-6 border-l-2 border-border pl-6">
      {entries.map((entry) => (
        <li key={entry.id} className="relative">
          <span
            className="absolute -left-[31px] top-1.5 h-3 w-3 rounded-full border-2 border-primary bg-background"
            aria-hidden="true"
          />
          <p className="text-sm font-semibold">
            {entry.title}
            {entry.tag ? (
              <span className="ml-2 rounded-full border px-2 py-0.5 text-xs font-medium text-muted-foreground">{entry.tag}</span>
            ) : null}
          </p>
          <p className="text-xs text-muted-foreground">
            <time dateTime={entry.at}>{formatWhen(entry.at)}</time>
            {entry.actor ? ` · por ${entry.actor}` : null}
          </p>
          {entry.changes && entry.changes.length > 0 ? (
            <ul className="mt-2 space-y-1 text-sm">
              {entry.changes.map((c, i) => (
                <li key={`${c.field}-${i}`}>
                  <span className="font-medium">{c.field}:</span>{' '}
                  <span>
                    <span className="sr-only">de </span>
                    {c.from ?? 'vazio'}
                  </span>{' '}
                  <span aria-hidden="true">→</span>
                  <span className="sr-only"> para </span> <span>{c.to ?? 'vazio'}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </li>
      ))}
    </ol>
  )
}
