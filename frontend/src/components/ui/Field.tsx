import {
  cloneElement,
  forwardRef,
  Fragment,
  isValidElement,
  useId,
  type InputHTMLAttributes,
  type ReactElement,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import { Icon } from '@components/ui/Icon'
import { cn } from '@utils/cn'

/** Atributos de acessibilidade que o controle deve receber do Field.
 *  (Copia do espelho da ED-08, Field.tsx linhas 5-11.) */
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
  /**
   * DUAS convencoes entregues, as duas suportadas (achado de consistencia
   * da galeria de provas, RESUMO.md das provas-de-telas, ressalva 4):
   * 1. Filho como FUNCAO DE RENDER — espelho/entrega da ED-08
   *    (CatalogCrud, CustomerList) e P3.25 (GisMap/GisStyles):
   *      <Field label="Nome">{(aria) => <Input {...aria} />}</Field>
   * 2. Filho DIRETO — P2.22 (ChangeSetReview), P2.28 (MinhasTarefas) e
   *    P2.33 (DocumentosFiscais):
   *      <Field label="Motivo"><Input value={...} /></Field>
   * Na convencao 2, sendo o filho um unico elemento, o Field injeta-lhe
   * por cloneElement o id e os atributos aria (se o elemento ainda nao os
   * trouxer), para o <label htmlFor> ficar associado ao controle — sem
   * isso a associacao quebrava nas paginas de filho direto. Se o filho
   * directo ja trouxer id proprio, o id do elemento prevalece e o label
   * aponta para ele.
   *
   * FILHO MULTIPLO / FRAGMENTO (correcção R2-F6, Rodada 8 da auditoria):
   * o rotulo NUNCA fica orfao.
   * - Fragmento ou array com UM so controle: a injecao atravessa o
   *   fragmento/array e o <label htmlFor> aponta para esse controle,
   *   exactamente como no filho unico.
   * - Varios controles, texto ou mistura (um <label for> nao pode apontar
   *   para mais de um controle): o Field NAO emite <label htmlFor> —
   *   emite uma legenda de grupo (<span id>) e envolve os filhos num
   *   <div role="group" aria-labelledby={legenda}>, que e a associacao
   *   correcta para um conjunto de controles sob um mesmo rotulo; a
   *   dica/erro ligam-se ao grupo por aria-describedby. Quem precisa de
   *   associacao individual deve dar um Field a cada controle ou usar a
   *   convencao 1 (funcao de render) por controle.
   */
  children: ReactNode | ((aria: FieldAria) => ReactNode)
}

/**
 * Tenta injetar o `aria` do Field num UNICO controle alcancavel a partir de
 * `node`, atravessando fragmentos e arrays de um so elemento significativo
 * (null/undefined/booleanos nao contam). Devolve o no reescrito e o id do
 * controle, ou null quando nao ha exactamente um controle (multiplo,
 * texto, vazio) — nesse caso o chamador usa a associacao por grupo.
 */
function injectIntoSingle(node: ReactNode, aria: FieldAria): { node: ReactNode; targetId: string } | null {
  if (Array.isArray(node)) {
    const meaningful = node.filter((n) => n !== null && n !== undefined && typeof n !== 'boolean')
    if (meaningful.length !== 1) return null
    return injectIntoSingle(meaningful[0], aria)
  }
  if (!isValidElement(node)) return null
  const el = node as ReactElement<Record<string, unknown>>
  if (el.type === Fragment) {
    const inner = injectIntoSingle((el.props as { children?: ReactNode }).children, aria)
    if (!inner) return null
    return { node: cloneElement(el, {}, inner.node), targetId: inner.targetId }
  }
  const existingId = typeof el.props.id === 'string' ? el.props.id : undefined
  const targetId = existingId ?? aria.id
  return {
    targetId,
    node: cloneElement(el, {
      id: targetId,
      'aria-describedby': el.props['aria-describedby'] ?? aria['aria-describedby'],
      'aria-invalid': el.props['aria-invalid'] ?? aria['aria-invalid'],
      'aria-required': el.props['aria-required'] ?? aria['aria-required'],
    }),
  }
}

/**
 * Liga rótulo, dica e mensagem de erro ao controle (label/for, aria-describedby, aria-invalid).
 * O erro tem ícone + texto: nunca só cor (WCAG 1.4.1). — espelho ED-08.
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

  let content: ReactNode
  let labelFor = controlId
  // R2-F6: grouped = sem controle unico associavel; nesse caso nao ha
  // <label htmlFor> (seria orfao) e a associacao faz-se por grupo.
  let grouped = false
  const labelId = `${controlId}-label`
  if (typeof children === 'function') {
    content = (children as (a: FieldAria) => ReactNode)(aria)
  } else {
    const injected = injectIntoSingle(children, aria)
    if (injected) {
      labelFor = injected.targetId
      content = injected.node
    } else {
      grouped = true
      content = (
        <div role="group" aria-labelledby={labelId} aria-describedby={describedBy} className="ui-field-group">
          {children}
        </div>
      )
    }
  }

  const labelInner = (
    <>
      {label}
      {required ? (
        <span className="ui-field-required" aria-hidden="true">
          *
        </span>
      ) : null}
    </>
  )

  return (
    <div className={cn('ui-field', className)}>
      {grouped ? (
        <span id={labelId} className="ui-field-label">
          {labelInner}
        </span>
      ) : (
        <label htmlFor={labelFor} className="ui-field-label">
          {labelInner}
        </label>
      )}
      {content}
      {hint ? (
        <p id={hintId} className="ui-field-hint">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="ui-field-error">
          <Icon name="alert-circle" className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  )
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, type = 'text', ...props },
  ref,
) {
  return <input ref={ref} type={type} className={cn('ui-input', className)} {...props} />
})

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, ...props },
  ref,
) {
  return <textarea ref={ref} className={cn('ui-textarea', className)} {...props} />
})

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...props },
  ref,
) {
  return (
    <select ref={ref} className={cn('ui-select', className)} {...props}>
      {children}
    </select>
  )
})

/** Caixa de seleção com rótulo clicável e área de toque adequada.
 *  (Espelho ED-08: label/hint proprios + restantes atributos do input;
 *  uso entregue em P3.25 GisMap e ED-08 CatalogCrud com id/label/checked/onChange.) */
export const Checkbox = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & { label: ReactNode; hint?: ReactNode }
>(function Checkbox({ label, hint, className, id, ...props }, ref) {
  const autoId = useId()
  const controlId = id ?? `c${autoId.replace(/:/g, '')}`
  const hintId = hint ? `${controlId}-hint` : undefined
  return (
    <div className={cn('ui-checkbox', className)}>
      <input
        ref={ref}
        id={controlId}
        type="checkbox"
        aria-describedby={hintId}
        className="ui-checkbox-input"
        {...props}
      />
      <div>
        <label htmlFor={controlId} className="ui-field-label">
          {label}
        </label>
        {hint ? (
          <p id={hintId} className="ui-field-hint">
            {hint}
          </p>
        ) : null}
      </div>
    </div>
  )
})
