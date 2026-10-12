import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { AlertCircle } from 'lucide-react'
import { cn } from '@utils/cn'

/** Atributos de acessibilidade que o controle deve receber do Field. */
export interface FieldAria {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  'aria-required'?: true
}

export interface FieldProps {
  label: ReactNode
  hint?: ReactNode
  error?: string | null
  required?: boolean
  /** Fixa o id do controle (útil em testes e para links externos). */
  id?: string
  className?: string
  children: (aria: FieldAria) => ReactNode
}

/**
 * Liga rótulo, dica e mensagem de erro ao controle (label/for, aria-describedby, aria-invalid).
 * O erro tem ícone + texto: nunca só cor (WCAG 1.4.1).
 */
export function Field({ label, hint, error, required, id, className, children }: FieldProps) {
  const autoId = useId()
  const controlId = id ?? `f${autoId.replace(/:/g, '')}`
  const hintId = hint ? `${controlId}-hint` : undefined
  const errorId = error ? `${controlId}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  const aria: FieldAria = { id: controlId }
  if (describedBy) aria['aria-describedby'] = describedBy
  if (error) aria['aria-invalid'] = true
  if (required) aria['aria-required'] = true

  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={controlId} className="block text-sm font-medium leading-snug">
        {label}
        {required ? (
          <span className="ml-1 text-destructive" aria-hidden="true">
            *
          </span>
        ) : null}
      </label>
      {children(aria)}
      {hint ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="flex items-start gap-1.5 text-sm text-destructive">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  )
}

const controlBase =
  'flex w-full rounded-lg border border-input bg-background px-3 text-sm placeholder:text-muted-foreground ' +
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background ' +
  'disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-destructive aria-[invalid=true]:border-2'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, type = 'text', ...props },
  ref,
) {
  return <input ref={ref} type={type} className={cn(controlBase, 'h-10 py-2', className)} {...props} />
})

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, ...props },
  ref,
) {
  return <textarea ref={ref} className={cn(controlBase, 'min-h-[88px] py-2', className)} {...props} />
})

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...props },
  ref,
) {
  return (
    <select ref={ref} className={cn(controlBase, 'h-10 py-2', className)} {...props}>
      {children}
    </select>
  )
})

/** Caixa de seleção com rótulo clicável e área de toque adequada. */
export const Checkbox = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & { label: ReactNode; hint?: ReactNode }
>(function Checkbox({ label, hint, className, id, ...props }, ref) {
  const autoId = useId()
  const controlId = id ?? `c${autoId.replace(/:/g, '')}`
  const hintId = hint ? `${controlId}-hint` : undefined
  return (
    <div className={cn('flex items-start gap-2', className)}>
      <input
        ref={ref}
        id={controlId}
        type="checkbox"
        aria-describedby={hintId}
        className="mt-0.5 h-4 w-4 rounded border-input accent-[hsl(var(--primary))] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        {...props}
      />
      <div>
        <label htmlFor={controlId} className="text-sm font-medium leading-snug">
          {label}
        </label>
        {hint ? (
          <p id={hintId} className="text-xs text-muted-foreground">
            {hint}
          </p>
        ) : null}
      </div>
    </div>
  )
})
