/**
 * P0.46 — licencas de terceiros exibidas no Sobre do frontend (a tela do
 * Sobre completa e P1.24; esta lista e a fonte dela, nao o /api/v1/about,
 * que e de outra frente). O Lucide e o pacote de icones da interface.
 */
export interface FrontendLicense {
  name: string
  version: string
  license: string
  url: string
  note: string
}

export const FRONTEND_LICENSES: FrontendLicense[] = [
  {
    name: 'Lucide',
    version: '0.303.0',
    license: 'ISC',
    url: 'https://github.com/lucide-icons/lucide/blob/main/LICENSE',
    note: 'Pacote de icones da interface (IconRegistry, P0.46).',
  },
]
