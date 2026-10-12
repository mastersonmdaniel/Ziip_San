/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base da API. Padrão: /api. */
  readonly VITE_API_BASE_URL?: string
  /** URL de um style.json do MapLibre. Sem ela, o seletor de ponto no mapa não é exibido. */
  readonly VITE_MAP_STYLE_URL?: string
  /** Emissor OIDC (realm do Keycloak). Padrão: http://localhost:8080/realms/sigsan. */
  readonly VITE_OIDC_AUTHORITY?: string
  /** Client público do SPA no Keycloak. Padrão: sigsan-web. */
  readonly VITE_OIDC_CLIENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
