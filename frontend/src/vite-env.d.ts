/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base da API. Padrão: /api. */
  readonly VITE_API_BASE_URL?: string
  /** URL de um style.json do MapLibre. Sem ela, o seletor de ponto no mapa não é exibido. */
  readonly VITE_MAP_STYLE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
