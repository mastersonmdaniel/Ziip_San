import { forwardRef, useState, type FocusEvent, type ReactNode } from 'react'
import { Field, Input } from '@components/ui/Field'
import { formatPhone, isValidPhone, stripPhone } from '@utils/validators/phone'

export interface PhoneInputProps {
  label?: ReactNode
  /** Dígitos nacionais (DDD + número), sem máscara. Use toE164() para enviar à API. */
  value: string
  onChange: (digits: string) => void
  onBlur?: (event: FocusEvent<HTMLInputElement>) => void
  required?: boolean
  error?: string | null
  disabled?: boolean
  id?: string
  name?: string
}

export const PhoneInput = forwardRef<HTMLInputElement, PhoneInputProps>(function PhoneInput(
  { label = 'Telefone', value, onChange, onBlur, required, error, disabled, id, name },
  ref,
) {
  const [touched, setTouched] = useState(false)
  const digits = stripPhone(value)
  const ownError =
    touched && digits.length > 0 && !isValidPhone(digits)
      ? 'Telefone inválido: informe DDD e número (8 dígitos para fixo, 9 para celular).'
      : null

  return (
    <Field id={id} label={label} required={required} error={error ?? ownError} hint="Com DDD, por exemplo (11) 91234-5678">
      {(aria) => (
        <Input
          {...aria}
          ref={ref}
          name={name}
          type="tel"
          value={formatPhone(value)}
          onChange={(event) => onChange(stripPhone(event.target.value))}
          onBlur={(event) => {
            setTouched(true)
            onBlur?.(event)
          }}
          disabled={disabled}
          placeholder="(00) 00000-0000"
          inputMode="tel"
          autoComplete="off"
        />
      )}
    </Field>
  )
})
