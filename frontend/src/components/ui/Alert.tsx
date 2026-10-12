import type { HTMLAttributes, ReactNode } from 'react'
import { AlertCircle, AlertTriangle, CheckCircle2, Info, Loader2 } from 'lucide-react'
import { cn } from '@utils/cn'

export type AlertTone = 'error' | 'warning' | 'success' | 'info'

const tones: Record<AlertTone, { box: string; icon: typeof Info }> = {
  error: { box: 'border-destructive bg-destructive/10 text-destructive', icon: AlertCircle },
  warning: { box: 'border-warning bg-warning/10 text-warning', icon: AlertTriangle },
  success: { box: 'border-green-700 bg-green-100 text-green-900 dark:bg-green-950 dark:text-green-100', icon: CheckCircle2 },
  info: { box: 'border-primary bg-primary/10 text-foreground', icon: Info },
}

interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  tone?: AlertTone
  title?: ReactNode
}

/**
 * Banner de mensagem. Erros usam role="alert" (anunciado na hora); os demais, role="status".
 * Ícone + título/texto: a tonalidade nunca é o único sinal.
 */
export function Alert({ tone = 'info', title, className, children, ...props }: AlertProps) {
  const { box, icon: Icon } = tones[tone]
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-lg border-l-4 p-4 text-sm', box, className)}
      {...props}
    >
      <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1 space-y-1">
        {title ? <p className="font-semibold">{title}</p> : null}
        {children ? <div>{children}</div> : null}
      </div>
    </div>
  )
}

export function Spinner({ label = 'Carregando...' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-2 py-10 text-muted-foreground">
      <Loader2 className="h-6 w-6 animate-spin" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
