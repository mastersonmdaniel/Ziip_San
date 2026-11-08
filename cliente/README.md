# ZiipSan Cliente — template do app do cliente (ED-39)

Template Capacitor (Android e iOS) do app do cliente. **Nao e um app
publicavel**: o pipeline (`pipeline/generate.ts`) carimba nome,
identificador, cores, host do link e client do Keycloak por tenant a
partir da configuracao da core `V11_1`, e o build assinado roda no CI
(`.github/workflows/cliente-mobile.yml`).

- Login: navegador do sistema + PKCE no realm `ziipsan-portal`
  (`src/auth/`), retorno por App Link/Universal Link.
- Refresh token: so no Keystore (Android) ou no Keychain (iOS),
  inacessivel ao JS (`src/plugins/secureToken.ts`) — RNF-39.
- Push sem dado pessoal: so codigos (`src/push/`).
- Consumo quase em tempo real: regras da ED-17 (`src/realtime/`).
- Marca do tenant: mesmas variaveis CSS do portal da P3.21
  (`src/branding/`).
- Acessibilidade do aparelho: SG-782 (`src/accessibility/`).
- Atualizacao remota: so bundle web, mesmo proposito, sem Appflow
  (`src/update/`, regra PLT-33 da ED-18).

Testes (Node 24): `node --test "src/**/*.test.ts" "pipeline/**/*.test.ts"`.
