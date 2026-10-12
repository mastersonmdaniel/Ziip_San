import { forwardRef, useState, type FocusEvent, type ReactNode } from 'react'
import { Field, Input } from '@components/ui/Field'
import { maskInscription, parseInscription, stripInscription } from '@utils/validators/inscription'

export interface InscriptionInputProps {
  label?: ReactNode
  /** Texto já mascarado (LLL.SSS.QQQ.LLLL.SSS); vazio = sem inscrição. */
  value: string
  onChange: (masked: string) => void
  onBlur?: (event: FocusEvent<HTMLInputElement>) => void
  required?: boolean
  error?: string | null
  hint?: ReactNode
  disabled?: boolean
  id?: string
  name?: string
}

/**
 * Campo de inscrição LLL.SSS.QQQ.LLLL.SSS. Aceita só dígitos e aplica a máscara ao digitar.
 * Os zeros à esquerda aparecem só na exibição: use parseInscription() para obter os números.
 */
export const InscriptionInput = forwardRef<HTMLInputElement, InscriptionInputProps>(function InscriptionInput(
  { label = 'Inscrição', value, onChange, onBlur, required, error, hint, disabled, id, name },
  ref,
) {
  const [touched, setTouched] = useState(false)
  const digits = stripInscription(value)
  let ownError: string | null = null
  if (touched && digits.length > 0) {
    if (digits.length < 16) {
      ownError = 'Inscrição incompleta: informe os 16 dígitos (LLL.SSS.QQQ.LLLL.SSS).'
    } else if (parseInscription(digits) === null) {
      ownError = 'Inscrição fora das faixas: localidade, setor e quadra de 1 a 999; lote de 1 a 9999; sublote de 0 a 999.'
    }
  }

  return (
    <Field
      id={id}
      label={label}
      required={required}
      error={error ?? ownError}
      hint={hint ?? 'Localidade, setor, quadra, lote e sublote: LLL.SSS.QQQ.LLLL.SSS'}
    >
      {(aria) => (
        <Input
          {...aria}
          ref={ref}
          name={name}
          value={maskInscription(value)}
          onChange={(event) => onChange(maskInscription(event.target.value))}
          onBlur={(event) => {
            setTouched(true)
            onBlur?.(event)
          }}
          disabled={disabled}
          placeholder="000.000.000.0000.000"
          inputMode="numeric"
          autoComplete="off"
        />
      )}
    </Field>
  )
})
