import { Field, Input, Select } from '@components/ui/Field'
import { lookupCep } from '@services/registry/resources'
import { UF_LIST } from '@utils/labels'
import { CepInput } from './CepInput'
import { MapPointPicker } from './MapPointPicker'
import type { AddressFormValue } from './addressForm'

export type AddressErrors = Partial<Record<keyof AddressFormValue, string | undefined>>

interface AddressFieldsProps {
  value: AddressFormValue
  onChange: (next: AddressFormValue) => void
  errors?: AddressErrors
  /** Exibe o seletor de ponto (lat/long). Só o imóvel precisa. */
  withCoordinates?: boolean
  /** Prefixo para tornar os rótulos únicos quando há vários endereços na tela. */
  legendSuffix?: string
}

/** Bloco de endereço: CEP com consulta, logradouro, número, complemento, bairro, município, UF e ponto no mapa. */
export function AddressFields({ value, onChange, errors = {}, withCoordinates = false, legendSuffix = '' }: AddressFieldsProps) {
  const set = <K extends keyof AddressFormValue>(key: K, v: AddressFormValue[K]) => onChange({ ...value, [key]: v })
  const suffix = legendSuffix ? ` (${legendSuffix})` : ''

  return (
    <div className="space-y-4">
      <div className="grid gap-4 md:grid-cols-3">
        <CepInput
          label={`CEP${suffix}`}
          value={value.cep}
          onChange={(v) => set('cep', v)}
          error={errors.cep}
          onLookup={(cep) => lookupCep(cep)}
          onResolved={(r) =>
            onChange({
              ...value,
              cep: r.cep.replace(/\D/g, ''),
              streetName: r.streetName ?? value.streetName,
              neighborhood: r.neighborhood ?? value.neighborhood,
              city: r.city ?? value.city,
              state: r.state ?? value.state,
            })
          }
        />
        <div className="md:col-span-2">
          <Field label={`Logradouro${suffix}`} required error={errors.streetName}>
            {(aria) => <Input {...aria} value={value.streetName} onChange={(e) => set('streetName', e.target.value)} maxLength={200} autoComplete="off" />}
          </Field>
        </div>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <Field label={`Número${suffix}`} required error={errors.number} hint='Use "S/N" se não houver número.'>
          {(aria) => <Input {...aria} value={value.number} onChange={(e) => set('number', e.target.value)} maxLength={20} autoComplete="off" />}
        </Field>
        <div className="md:col-span-2">
          <Field label={`Complemento${suffix}`} error={errors.complement}>
            {(aria) => <Input {...aria} value={value.complement} onChange={(e) => set('complement', e.target.value)} maxLength={100} autoComplete="off" />}
          </Field>
        </div>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        <Field label={`Bairro${suffix}`} error={errors.neighborhood}>
          {(aria) => <Input {...aria} value={value.neighborhood} onChange={(e) => set('neighborhood', e.target.value)} maxLength={100} autoComplete="off" />}
        </Field>
        <Field label={`Município${suffix}`} error={errors.city}>
          {(aria) => <Input {...aria} value={value.city} onChange={(e) => set('city', e.target.value)} maxLength={100} autoComplete="off" />}
        </Field>
        <Field label={`UF${suffix}`} error={errors.state}>
          {(aria) => (
            <Select {...aria} value={value.state} onChange={(e) => set('state', e.target.value)}>
              <option value="">—</option>
              {UF_LIST.map((uf) => (
                <option key={uf} value={uf}>
                  {uf}
                </option>
              ))}
            </Select>
          )}
        </Field>
      </div>
      {withCoordinates ? <MapPointPicker value={value.coordinates} onChange={(c) => set('coordinates', c)} error={errors.coordinates} /> : null}
    </div>
  )
}
