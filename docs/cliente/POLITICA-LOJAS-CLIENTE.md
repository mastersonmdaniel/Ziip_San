# Politica de lojas do app do cliente — ED-39 (PLT-33 da ED-18, ADR-0026)

A ED-18 fixou as regras de plataforma movel (PLT-33); esta politica e a
aplicacao delas ao app do cliente POR TENANT. O que a ED-18 decidiu nao
e redecidido aqui — e aplicado.

## 1. Contas de loja: do prestador, de organizacao

- O app de cada tenant e publicado **na conta do prestador**: Google
  Play Console (conta de **organizacao**, com D-U-N-S ou verificacao de
  orgao governamental) e Apple Developer Program (**organizacao**).
- Os rotulos das contas ficam nos parametros da core V11_1
  (`app.client_android_store_account`, `app.client_ios_store_account`,
  sem fabrica: ato do dono) e sao copiados para
  `platform.client_app_config.store_account_ref` na geracao. Sem conta
  registrada na plataforma, **nao ha submissao** naquela plataforma
  (regra executavel em `ClientAppConfigService.submitForReview`).
- Criar as contas, obter o D-U-N-S e aceitar os acordos das lojas sao
  **atos externos do dono/prestador** — pendencia declarada, nao codigo.

## 2. Requisitos de publicacao (conferidos a cada submissao)

- **Android:** API alvo exigida pelo Google Play para apps novos e
  atualizacoes na data do release (a ED-18 registrou Android 16/API 36;
  revisar a cada mudanca de exigencia, como la); AAB assinado com a
  chave de upload do prestador; ficha com declaracoes de privacidade
  (Data safety) coerentes com o app: o app trata dado do consumidor
  dentro da base do tenant; o push NAO carrega dado pessoal (contrato
  de `PushPayloadSanitizer`).
- **iOS:** build com SDK vigente exigido pela App Store; assinatura com
  certificado de distribuicao e perfil do prestador; rotulos de
  privacidade (Privacy Nutrition Labels) coerentes; login com o
  navegador do sistema (sem WebView de credencial); Universal Link
  verificado pelo `apple-app-site-association` do host do tenant.
- **Identificador imutavel:** depois da primeira publicacao, o
  identificador do app nao muda (as duas lojas tratam como outro app);
  por isso ele deriva deterministicamente do slug e e unico por
  plataforma na V11_1.
- **Revisao das lojas:** prazos e recusas das lojas sao externos; o
  estado IN_REVIEW da V11_1 existe para nao fingir aprovacao.

## 3. Atualizacao remota

- So codigo interpretado (bundle web), manifesto assinado, mesmo
  identificador e **mesmo proposito** — a guarda e
  `cliente/src/update/remoteUpdate.ts` (a mesma da ED-18).
- Conteudo baixado nao pode alterar a funcionalidade aprovada
  (politica do Google Play e diretriz da App Store): mudanca nativa,
  de permissoes ou de proposito = build novo submetido. Sem Appflow.

## 4. RNF-39 — matriz de evidencia (app do cliente)

| Controle | Onde | Prova |
|---|---|---|
| Refresh token so no Keystore (Android) | Plugin `SecureToken` da ED-18, reutilizado | Instrumentado da ED-18 (CI) + contrato |
| Refresh token so no Keychain (iOS) | Plugin `SecureToken` iOS (Keychain, sem iCloud) | Contrato TS; implementacao Swift e prova em aparelho sao da integracao/POC-55 |
| Refresh token inacessivel ao JS | `SecureTokenBridge` sem metodo de leitura + `bridgeRespectsRnf39` | Revisao do contrato + teste de forma na integracao |
| Client publico, PKCE S256, redirect unico verificado | `KeycloakClientProvisioning` + `pipeline/generate.ts` | Testes Java e TS desta entrega (executados) |

## 5. Acessibilidade (SG-782)

Liberacao exige a conferencia de `AccessibilityGate` completa
(TalkBack, VoiceOver, fonte dinamica, alvos minimos, contraste nos
temas do tenant, reducao de movimento, axe sem serio/critico). A
conferencia em aparelho real faz parte do ensaio da POC-55.
