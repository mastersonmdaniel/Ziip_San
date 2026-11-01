# MFA por tenant (P0.15)

Segundo fator nativo do Keycloak + gate na API. Sem SPI próprio: a prova de
conceito do fluxo nativo expressa a regra (condição por atributo do usuário),
então não há autenticador customizado para manter.

## Regra

- **Usuário de plataforma** (`ziipsan_user_type=platform`, sem tenant): segundo
  fator **sempre**, sem exceção.
- **Usuário de tenant**: segundo fator só se o tenant exigir, pelo parâmetro
  do tenant (mudança só pelo parâmetro — nunca por flag solta no usuário).

## Parâmetro (fonte no código: `MfaPolicy`, platform)

| Item | Valor |
|---|---|
| Chave em `public.tenant_settings` | `security.mfa_required` |
| Formato | `setting_value` JSONB booleano (`true`/`false`) |
| Ausente | `false` (fail-open — ver lag abaixo) |
| Valor inválido | não sincronizado; o tenant é pulado com aviso |
| Atributo no Keycloak | `mfa_required` = `"true"`/`"false"` |

Quem muda o parâmetro hoje é o operador (escrita direta na tabela — a tela de
parâmetros é P1.6 e o workflow de aceite é P0.19). **Depois de mudar, rode a
sincronização** (abaixo); sem ela, nada muda no login.

## Sincronização (`scripts/sync-tenant-mfa.py`, fora do módulo app)

O ArchUnit proíbe `org.keycloak.*` na app, então a sincronização é um script
(python stdlib, `psql` + Admin REST), não um job da api:

```sh
python3 scripts/sync-tenant-mfa.py --dry-run   # padrão: só mostra
python3 scripts/sync-tenant-mfa.py --apply     # aplica de verdade
```

Ambiente: `PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD` (a conexão precisa
enxergar **todos** os tenants — role com BYPASSRLS ou superuser de manutenção,
porque a RLS de `tenant_settings` esconderia os outros tenants),
`KC_URL`, `KC_SYNC_CLIENT_ID`, `KC_SYNC_CLIENT_SECRET` (client de serviço com
`manage-users` no realm; segredo nunca no repo). Usuários de plataforma não
são tocados (atributo manual no cadastro). Usuários são listados e filtrados
localmente por `tenant_id` (a busca por atributo na Admin API varia por versão;
na escala do piloto, listar e filtrar basta).

**Lag fail-open (não escondido):** entre a mudança do parâmetro e a próxima
execução do script, o login usa o atributo antigo; atributo ausente vale como
não-exigente. Um tenant recém-exigente fica sem MFA até a próxima sincronização
— por isso o procedimento manda rodar o script logo após mudar o parâmetro.
**Registro de aceite (lacuna P0.19):** `parameter_acceptances` ainda não
existe. Até lá, cada mudança aplicada sai como uma linha JSON
`{"event": "mfa_attribute_sync", ...}` no stdout (ou `SYNC_AUDIT_FILE`) — é o
registro de auditoria, não um workflow de aceite inventado.

## Fluxo no realm (`ziipsan-realm.json`, client `ziipsan-bff`)

O client BFF usa o fluxo próprio `ziipsan-bff-browser` (override só dele; o
fluxo `browser` do realm e o client público não mudam):

- `auth-cookie` ALTERNATIVE, `auth-username-password-form` REQUIRED;
- subfluxo `ziipsan-platform-mfa` CONDITIONAL: condição
  `conditional-user-attribute` (`ziipsan_user_type=platform`) + `auth-otp-form`
  ALTERNATIVE + `webauthn-authenticator` ALTERNATIVE;
- subfluxo `ziipsan-tenant-mfa` CONDITIONAL: condição
  (`mfa_required=true`) + OTP/WebAuthn ALTERNATIVE.

Políticas: OTP HmacSHA1, 6 dígitos, período 30s; WebAuthn com RP `ZIIPSAN`,
ES256/RS256, verificação `preferred`. Required actions de enrolamento
(`CONFIGURE_TOTP`, `webauthn-register`) existem **sem ser padrão**: o admin as
atribui ao criar o usuário que precisa de MFA — padrão forçaria enrolamento
em todo usuário novo, inclusive de tenant sem MFA.

**Mapper AMR + reference values:** o client BFF tem o mapper `oidc-amr-mapper`
(claim `amr` em access + ID token). Reference values por execution:

| Execution | Reference value (`amr`) |
|---|---|
| `auth-otp-form` | `otp` |
| `webauthn-authenticator` | `hwk` |

Estes são os únicos valores aceitos pelo gate (`MfaPolicy.MFA_AMR_VALUES`).
Mudar um reference value sem mudar o gate quebra o login de plataforma — os
dois andam juntos.

**Reimportação (IGNORE_EXISTING):** o compose importa o realm só se ele ainda
não existir (`--import-realm`). Mudar o JSON **não** altera um realm já
criado: para aplicar o fluxo novo, apague o realm no console ou recrie o
banco do Keycloak. Na importação real (P0.27), confirmar contra um export do
Keycloak 26.8: (a) as chaves `attribute`/`value` das configs das condições e
(b) a persistência dos reference values nas executions — o teste estrutural
(`RealmMfaStructureTest`) confere alias, requisitos e valores, não a aceitação
pelo servidor.

## Gate na API (`MfaGateFilter`, nas duas cadeias)

Para `/api/v1/platform/*`, **depois** da autenticação e **antes** do
`JwtTenantFilter`: sem `amr` de segundo fator, 403 `mfa-required`. Sem JWT, o
filtro deixa passar (quem dá 401 é a cadeia). Fora de rota de plataforma, o
filtro não atua. O filtro **não** recusa token com `tenant_id` em rota de
plataforma — isso é P0.43.

Interação com o filtro de tenant: token de plataforma (sem `tenant_id`) com
segundo fator passa no gate e depois cai em `tenant-missing`, até a P0.43
isentar as rotas de plataforma do filtro de tenant. O gate está certo; falta
a rota (P0.43) — por isso os testes usam um caminho de plataforma inexistente
(403 com/sem amr, 404 com amr e tenant).

## Provas e o que falta

- `MfaGateFilterTest` (platform, sem Spring), `MfaGateIntegrationTest`
  (cadeia real, ordem gate-antes-de-tenant), `RealmMfaStructureTest` (JSON),
  `test-sync-tenant-mfa.py` (plano puro, sem PG nem Keycloak).
- **Login real (P0.27)**: fluxo condicional, enrolamento, claim `amr` nos
  tokens e sincronização contra um Keycloak de verdade — fora do alcance
  desta tarefa, por decisão congelada. A P0.15 fecha no nível estrutural.
