import { forwardRef, useEffect, useRef, useState, type FocusEvent, type ReactNode } from 'react'
import { Field, Input } from '@components/ui/Field'
import { toApiError } from '@services/registry/errors'
import type { CepLookupResult } from '@services/registry/types'
import { formatCep, isValidCep, stripCep } from '@utils/validators/cep'

export interface CepInputProps {
  label?: ReactNode
  /** 8 dígitos sem máscara. */
  value: string
  onChange: (digits: string) => void
  /** Consulta opcional ao completar o CEP (rejeitar/retornar null = não encontrado). */
  onLookup?: (cep: string) => Promise<CepLookupResult | null>
  /** Recebe o resultado da consulta para preencher o endereço. */
  onResolved?: (result: CepLookupResult) => void
  onBlur?: (event: FocusEvent<HTMLInputElement>) => void
  required?: boolean
  error?: string | null
  disabled?: boolean
  id?: string
  name?: string
}

type LookupState = 'idle' | 'loading' | 'found' | 'not-found' | 'unavailable'

const STATUS_TEXT: Record<LookupState, string> = {
  idle: '',
  loading: 'Consultando CEP...',
  found: 'Endereço preenchido a partir do CEP. Confira os dados.',
  'not-found': 'CEP não encontrado. Preencha o endereço manualmente.',
  unavailable: 'Consulta de CEP indisponível no momento. Preencha o endereço manualmente.',
}

/** CEP com máscara 00000-000; consulta automática opcional ao completar os 8 dígitos. */
export const CepInput = forwardRef<HTMLInputElement, CepInputProps>(function CepInput(
  { label = 'CEP', value, onChange, onLookup, onResolved, onBlur, required, error, disabled, id, name },
  ref,
) {
  const [touched, setTouched] = useState(false)
  const [lookup, setLookup] = useState<LookupState>('idle')
  const sequence = useRef(0)

  const digits = stripCep(value)
  const ownError = touched && digits.length > 0 && !isValidCep(digits) ? 'CEP inválido: informe 8 dígitos.' : null

  useEffect(() => {
    if (!onLookup || !isValidCep(digits)) {
      setLookup('idle')
      return
    }
    const current = (sequence.current += 1)
    setLookup('loading')
    onLookup(digits)
      .then((result) => {
        if (current !== sequence.current) return
        if (result) {
          setLookup('found')
          onResolved?.(result)
        } else {
          setLookup('not-found')
        }
      })
      .catch((e: unknown) => {
        if (current !== sequence.current) return
        setLookup(toApiError(e).kind === 'not-found' ? 'not-found' : 'unavailable')
      })
    // onLookup/onResolved mudam de identidade a cada render; a consulta depende só do CEP.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [digits])

  return (
    <Field id={id} label={label} required={required} error={error ?? ownError}>
      {(aria) => (
        <>
          <Input
            {...aria}
            ref={ref}
            name={name}
            value={formatCep(value)}
            onChange={(event) => onChange(stripCep(event.target.value))}
            onBlur={(event) => {
              setTouched(true)
              onBlur?.(event)
            }}
            disabled={disabled}
            placeholder="00000-000"
            inputMode="numeric"
            autoComplete="postal-code"
          />
          <p className="text-xs text-muted-foreground" role="status" aria-live="polite">
            {STATUS_TEXT[lookup]}
          </p>
        </>
      )}
    </Field>
  )
})
