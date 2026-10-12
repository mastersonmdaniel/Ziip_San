import { forwardRef, useState, type ChangeEvent, type FocusEvent, type ReactNode } from 'react'
import { Field, Input } from '@components/ui/Field'
import {
  detectDocumentKind,
  formatDocument,
  isValidDocument,
  stripDocument,
  type DocumentKind,
} from '@utils/validators/document'

export interface CpfCnpjInputProps {
  label: ReactNode
  /** Valor SEM máscara (CPF: 11 dígitos; CNPJ: 14 caracteres A-Z/0-9). */
  value: string
  onChange: (raw: string) => void
  onBlur?: (event: FocusEvent<HTMLInputElement>) => void
  /** CPF, CNPJ ou 'auto' (decide pelo conteúdo: letras ou mais de 11 posições = CNPJ). */
  kind?: DocumentKind | 'auto'
  required?: boolean
  /** Erro vindo de fora (servidor ou esquema do formulário); tem prioridade. */
  error?: string | null
  hint?: ReactNode
  disabled?: boolean
  id?: string
  name?: string
}

function messageFor(kind: DocumentKind, raw: string): string | null {
  if (raw === '') return null
  if (isValidDocument(raw, kind)) return null
  const expected = kind === 'CPF' ? 11 : 14
  if (raw.length < expected) {
    return kind === 'CPF'
      ? 'CPF incompleto: informe os 11 dígitos.'
      : 'CNPJ incompleto: informe as 14 posições (letras e números).'
  }
  return kind === 'CPF'
    ? 'CPF inválido: confira os dígitos.'
    : 'CNPJ inválido: confira os caracteres e os dígitos verificadores.'
}

/**
 * Campo de CPF/CNPJ com máscara e validação do dígito verificador, incluindo o CNPJ
 * alfanumérico. O valor entregue ao formulário é sempre o bruto (sem pontuação).
 * Nunca guarda o documento fora do estado do formulário e nunca o registra em log.
 */
export const CpfCnpjInput = forwardRef<HTMLInputElement, CpfCnpjInputProps>(function CpfCnpjInput(
  { label, value, onChange, onBlur, kind = 'auto', required, error, hint, disabled, id, name },
  ref,
) {
  const [touched, setTouched] = useState(false)
  const activeKind: DocumentKind = kind === 'auto' ? detectDocumentKind(value) : kind
  const shown = formatDocument(value, activeKind)
  const ownError = touched ? messageFor(activeKind, stripDocument(value, activeKind)) : null
  const message = error ?? ownError

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const text = event.target.value
    const nextKind: DocumentKind = kind === 'auto' ? detectDocumentKind(text) : kind
    onChange(stripDocument(text, nextKind))
  }

  const placeholder =
    activeKind === 'CPF' ? '000.000.000-00' : kind === 'auto' ? 'CPF ou CNPJ' : '00.000.000/0000-00'

  return (
    <Field
      id={id}
      label={label}
      required={required}
      error={message}
      hint={hint ?? (activeKind === 'CNPJ' ? 'O CNPJ pode ter letras e números (formato alfanumérico).' : undefined)}
    >
      {(aria) => (
        <Input
          {...aria}
          ref={ref}
          name={name}
          value={shown}
          onChange={handleChange}
          onBlur={(event) => {
            setTouched(true)
            onBlur?.(event)
          }}
          disabled={disabled}
          placeholder={placeholder}
          inputMode={activeKind === 'CPF' ? 'numeric' : 'text'}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
        />
      )}
    </Field>
  )
})
