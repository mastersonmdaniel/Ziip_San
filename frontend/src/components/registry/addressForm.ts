import { z } from 'zod'
import { COORDINATE_MESSAGES, coordinatesToText, EMPTY_COORDINATES, textToCoordinates, type CoordinateText } from './MapPointPicker'
import type { Address } from '@services/registry/types'
import { UF_LIST } from '@utils/labels'
import { stripCep, isValidCep } from '@utils/validators/cep'
import { checkCoordinatePair } from '@utils/validators/geo'
import { textField, toTextOrNull } from '@utils/zodFields'

/** Endereço como o formulário o enxerga: tudo texto. */
export interface AddressFormValue {
  cep: string
  streetName: string
  number: string
  complement: string
  neighborhood: string
  city: string
  state: string
  coordinates: CoordinateText
}

export const EMPTY_ADDRESS: AddressFormValue = {
  cep: '',
  streetName: '',
  number: '',
  complement: '',
  neighborhood: '',
  city: '',
  state: '',
  coordinates: EMPTY_COORDINATES,
}

export const coordinateTextSchema = z
  .object({ lat: z.string(), lng: z.string() })
  .superRefine((value, ctx) => {
    const issue = checkCoordinatePair(value.lat, value.lng)
    if (issue) ctx.addIssue({ code: 'custom', message: COORDINATE_MESSAGES[issue], path: ['lat'] })
  })

export const addressSchema = z.object({
  cep: z.string().refine((v) => v === '' || isValidCep(v), 'CEP inválido: informe 8 dígitos.'),
  streetName: textField({ required: true, max: 200 }),
  number: textField({ required: true, max: 20 }),
  complement: textField({ max: 100 }),
  neighborhood: textField({ max: 100 }),
  city: textField({ max: 100 }),
  state: z.string().refine((v) => v === '' || (UF_LIST as readonly string[]).includes(v), 'UF inválida.'),
  coordinates: coordinateTextSchema,
})

export function addressToForm(address: Address | null | undefined): AddressFormValue {
  if (!address) return EMPTY_ADDRESS
  return {
    cep: address.cep ? stripCep(address.cep) : '',
    streetName: address.streetName,
    number: address.number,
    complement: address.complement ?? '',
    neighborhood: address.neighborhood ?? '',
    city: address.city ?? '',
    state: address.state ?? '',
    coordinates: coordinatesToText(address.coordinates),
  }
}

export function addressToApi(value: AddressFormValue): Address {
  return {
    cep: value.cep === '' ? null : stripCep(value.cep),
    streetName: value.streetName.trim(),
    number: value.number.trim(),
    complement: toTextOrNull(value.complement),
    neighborhood: toTextOrNull(value.neighborhood),
    city: toTextOrNull(value.city),
    state: value.state === '' ? null : value.state,
    coordinates: textToCoordinates(value.coordinates),
  }
}
