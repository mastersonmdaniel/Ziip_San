/**
 * P1.12: abas da ficha da unidade, a partir do mapa do produto da P0.47
 * (`docs/arquitetura/product-map.md`, gerado de `packages/sanitation/vertical.json`,
 * campo `unitSheetTabs` de cada módulo).
 *
 * Regra da P0.47: cada tarefa que cria uma fonte da ficha acrescenta a aba ao
 * manifesto; o registry fornece a própria ficha, não uma aba. Abas já declaradas
 * no manifesto: contas (billing, `bills`), débitos (collection, `debts`),
 * documentos (mtr, `documents`), qualidade (quality, `quality`). OS e
 * atendimentos ainda não têm módulo nem aba — entram com as tarefas que os criarem.
 *
 * As abas declaradas aparecem na ficha, mas `available: false` enquanto o módulo
 * está `declared` sem rota de API: a ficha mostra um aviso, sem inventar dado
 * nem chamar rota que não existe. A tarefa que criar a fonte torna a aba
 * disponível. O teste unitSheetTabs.test.ts confere esta lista contra o
 * vertical.json, para a ficha não divergir do manifesto.
 */
export interface UnitSheetTab {
  id: string
  label: string
  /** Módulo do manifesto que declarou a aba. */
  module: 'registry' | 'billing' | 'collection' | 'mtr' | 'quality'
  /** Falso enquanto o módulo não tem rota de API para alimentar a aba. */
  available: boolean
  /** Texto exibido quando a aba ainda não tem fonte. */
  unavailableText?: string
}

export const UNIT_SHEET_TABS: UnitSheetTab[] = [
  { id: 'cadastro', label: 'Cadastro', module: 'registry', available: true },
  { id: 'clientes', label: 'Clientes', module: 'registry', available: true },
  { id: 'historico', label: 'Histórico', module: 'registry', available: true },
  {
    id: 'bills',
    label: 'Contas',
    module: 'billing',
    available: false,
    unavailableText:
      'O módulo de Faturamento está declarado no mapa do produto, mas ainda não tem rota de API para alimentar esta aba. As contas aparecem aqui quando a tarefa do módulo as criar.',
  },
  {
    id: 'debts',
    label: 'Débitos',
    module: 'collection',
    available: false,
    unavailableText:
      'O módulo de Arrecadação está declarado no mapa do produto, mas ainda não tem rota de API para alimentar esta aba. Os débitos aparecem aqui quando a tarefa do módulo os criar.',
  },
  {
    // Ordem do manifesto (vertical.json: billing, collection, quality, mtr) — a
    // ficha segue a ordem do mapa do produto, não uma ordem inventada aqui.
    id: 'quality',
    label: 'Qualidade',
    module: 'quality',
    available: false,
    unavailableText:
      'O módulo de Qualidade da água está declarado no mapa do produto, mas ainda não tem rota de API para alimentar esta aba. Os dados de qualidade aparecem aqui quando a tarefa do módulo os criar.',
  },
  {
    id: 'documents',
    label: 'Documentos',
    module: 'mtr',
    available: false,
    unavailableText:
      'O módulo de Resíduos / MTR está declarado no mapa do produto, mas ainda não tem rota de API para alimentar esta aba. Os documentos aparecem aqui quando a tarefa do módulo os criar.',
  },
]

export function unitSheetTabById(id: string): UnitSheetTab | undefined {
  return UNIT_SHEET_TABS.find((tab) => tab.id === id)
}
