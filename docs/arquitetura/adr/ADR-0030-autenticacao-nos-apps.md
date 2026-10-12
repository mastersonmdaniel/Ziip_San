# ADR-0030 — Autenticação nos apps

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0006 (BFF com cookie, que continua valendo para o navegador); ADR-0007 (MFA); ADR-0018 (Central
  Ziip: revogação); ADR-0022 (realm `ziipsan-portal`); ADR-0026 (apps Capacitor; primeira tarefa derivada);
  `docs/arquitetura/REQUISITOS-NAO-FUNCIONAIS.md` (RNF-31 e o novo RNF-39); `docs/arquitetura/ARQUITETURA-ALVO.md`
  §1 e §2; `docs/arquitetura/CENTRAL-ZIIP.md`; plano ED-18, ED-20, ED-39
- **Base:** ADR-0026 (Consequências negativas, "Autenticação nos apps"); RFC 8252 (OAuth 2.0 para apps nativos);
  RFC 7636 (PKCE); RFC 9700 (boas práticas de segurança do OAuth 2.0)

## Contexto

O ADR-0026 adotou apps híbridos com Capacitor: app de campo só Android, por APK, e app do cliente Android e iOS nas
lojas, com a marca do tenant. O BFF do ADR-0006 guarda os tokens no servidor e entrega ao navegador um cookie `__Host-`
HttpOnly. Esse desenho não se transpõe ao app: a WebView do Capacitor tem origem local (`https://localhost` no Android,
`capacitor://localhost` no iOS), o cookie de terceiro é bloqueado no iOS e o login dentro de WebView embutida é
desaconselhado pela RFC 8252 (o app poderia ler a senha digitada, e não há sessão compartilhada com o navegador).

O app de campo trabalha offline por uma jornada inteira ou mais, em aparelho que circula entre funcionários e pode ser
perdido ou furtado, com dados de consumidores no banco local. O app do cliente é usado pelo consumidor final no
próprio celular, com a mesma conta do portal (ADR-0022).

## Decisão

1. **Fluxo.** Authorization code com **PKCE (S256)**, aberto no **navegador do sistema** (Custom Tabs no Android,
   `ASWebAuthenticationSession` no iOS), nunca em WebView embutida (RFC 8252). Retorno por **App Link** (Android) ou
   **Universal Link** (iOS) verificado num domínio do produto; esquema próprio de URI só onde o link verificado não
   for possível, com o PKCE como proteção contra interceptação. Cada app publicado tem client público próprio no
   Keycloak, criado pelo pipeline do app (ED-18, ED-39).
2. **Realms.** O **app do cliente** usa o realm **`ziipsan-portal`**, com a mesma conta e o mesmo login do portal
   (CPF/CNPJ ou e-mail e senha, MFA opcional; ADR-0022). O **app de campo** usa o realm **`ziipsan`**, com os usuários
   do tenant e o claim `tenant_id`, e a política de MFA do tenant (ADR-0007), aplicada na tela do navegador do
   sistema. Token de um realm é recusado nas rotas do outro (mesma regra da P3.3).
3. **Validade curta e rotação.** Access token de **5 minutos**; refresh token **rotativo** a cada uso, e a reutilização
   de refresh já trocado revoga a sessão inteira. Valores iniciais: app de campo com sessão ociosa de 12 horas e
   máxima de 7 dias; app do cliente com sessão ociosa de 30 dias e máxima de 90 dias. Os valores ficam no realm e podem
   ser reduzidos sem mudar o código; aumentá-los exige novo ADR. Fora do ar, o app de campo continua operando com o
   banco local (item 6) e só precisa de token válido para sincronizar; com a sessão expirada, o usuário entra de novo
   ao voltar à rede, e as pendências seguem na fila até lá.
4. **Armazenamento.** Refresh token só no armazenamento seguro do aparelho: **Android Keystore** (chave não exportável,
   AES-GCM, em StrongBox ou TEE quando houver) e **iOS Keychain** (`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`),
   por plugin nativo. As chamadas à API saem por plugin nativo de HTTP, que anexa o token: o JavaScript da WebView não
   lê o refresh token, e o access token existe só na memória do processo nativo. Nada de token em `localStorage`,
   IndexedDB ou `Preferences` sem cifra. É a forma do RNF-31 nos apps (RNF-39).
5. **Revogação.** Logout revoga a sessão no Keycloak e apaga o armazenamento seguro. O administrador do tenant revoga
   sessões e dispositivos dos usuários do próprio tenant; a **Central Ziip** (`PLATFORM_OWNER` ou `PLATFORM_OPS`, com
   MFA, justificativa e auditoria, e aviso ao tenant) revoga sessões de app e bloqueia dispositivos de qualquer tenant,
   sem ler dado pessoal (CENTRAL-ZIIP §2). A revogação vale no próximo refresh, no máximo em 5 minutos.
6. **App de campo em dispositivo registrado e gerenciado.**
   - Registro no primeiro login, aprovado pelo administrador do tenant (ou pré-cadastro em lote): o app gera um par de
     chaves no Keystore, com atestação de chave quando o aparelho suportar, e o servidor vincula o dispositivo ao
     tenant. A `api` só aceita sincronização de dispositivo registrado e ativo, com o token ligado à chave do aparelho
     (DPoP, RFC 9449, se a versão do Keycloak em uso suportar; senão, assinatura da requisição com a chave do aparelho
     conferida pela `api`; a confirmar no início da ED-18).
   - Requisitos mínimos conferidos pelo app a cada abertura: tela bloqueada com PIN ou biometria, versão mínima do
     Android e do patch de segurança, integridade do aparelho (Play Integrity) sem root. Aparelho fora do mínimo não
     sincroniza.
   - Gestão pelo MDM do cliente quando houver (Android Enterprise, perfil de trabalho ou dispositivo dedicado); o Ziip
     não exige MDM próprio.
   - Banco local cifrado (SQLCipher ou equivalente) com chave no Keystore, liberada pelo desbloqueio do aparelho.
   - **Bloqueio remoto** em dois modos: `RETIRE` (devolução do aparelho: envia as pendências e apaga os dados locais) e
     `BLOCK` (perda ou furto: o servidor recusa na hora qualquer sincronização do aparelho, e o app apaga os dados
     locais no próximo contato, sem enviar). Leitura e OS gravam aparelho, usuário e hora.
7. **App do cliente** sem registro de dispositivo; desbloqueio local por biometria opcional, só para reabrir a sessão
   já existente. Push sem dado pessoal (ADR-0026).
8. **Navegador continua no BFF.** Backoffice e portal web seguem o ADR-0006 sem mudança. A `api` passa a aceitar, além
   do BFF, Bearer tokens dos clients públicos dos apps, conferindo emissor, audiência e client (`azp`); as rotas de
   plataforma continuam recusando token de app.

## Consequências

### Positivas

- Login pelo navegador do sistema: o app não vê a senha, e o fluxo segue a RFC 8252 e as regras de revisão das lojas.
- Token fora do alcance do JavaScript, como no web, o que mantém o efeito do RNF-31 nos apps.
- Aparelho perdido ou furtado pode ser cortado na hora pelo tenant ou pela Central.

### Negativas

- Plugins nativos de HTTP e de armazenamento seguro a manter em cada versão do Android, do iOS e do Capacitor.
- Um client por app publicado e verificação de domínio (App Link, Universal Link) para cada tenant com app do cliente.
- A `api` passa a ser alcançada diretamente pelos apps: borda com WAF e limite de taxa também para esse caminho.
- Registro e aprovação de aparelho adicionam um passo à implantação do app de campo.
- Aparelho bloqueado por perda perde as leituras não sincronizadas; o modo `RETIRE` evita isso nas devoluções.

### O que muda no código

- Plugin nativo de HTTP com injeção do token e plugin de armazenamento seguro (Keystore e Keychain).
- Clients públicos com PKCE no realm `ziipsan` (app de campo) e no realm `ziipsan-portal` (um por app do cliente).
- Core `V11_1` (plano §3): `platform.app_device` (tenant, aparelho, app, `sub` do usuário, plataforma, resumo da
  atestação, estado `ACTIVE`, `BLOCKED` ou `RETIRED`, aprovação, último contato, motivo), com RLS por tenant e sem
  outro dado pessoal.
- Telas de dispositivos na área do tenant e na Central; validação de aparelho e de vínculo do token na `api`.

### O que muda no roadmap

- ED-18 passa a incluir o fluxo, o armazenamento seguro, o registro e o bloqueio remoto (+1 semana, consolidado 18).
- ED-39 reaproveita o fluxo no realm `ziipsan-portal`.
- Pentest dos apps (interceptação de redirect, armazenamento, revogação) antes do primeiro ensaio de POC com app.

## Alternativas consideradas

- **Login em WebView embutida com cookie do BFF.** Rejeitada: cookie de terceiro bloqueado no iOS, origem local na
  WebView e senha exposta ao app (RFC 8252).
- **Token em `localStorage` ou `Preferences`.** Rejeitada: legível pelo JavaScript; repete o problema que o ADR-0006
  resolveu no navegador.
- **Refresh token longo para o app de campo trabalhar offline.** Rejeitada: o trabalho offline não precisa de token, só
  a sincronização; validade longa amplia o dano de aparelho perdido.
- **MDM obrigatório fornecido pelo Ziip.** Rejeitada: custo por aparelho e conflito com a gestão de TI do cliente
  público; o registro de dispositivo no produto cobre o mínimo.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Plugins de HTTP e de armazenamento seguro; client público com PKCE no realm `ziipsan`; App Link | P4 (ED-18) |
| Registro de dispositivo, requisitos mínimos, bloqueio `RETIRE` e `BLOCK`; core `V11_1` | P4 (ED-18) |
| Revogação de sessão e de dispositivo na área do tenant e na Central | P4 (ED-18) |
| Client por app do cliente no realm `ziipsan-portal`, criado pelo pipeline; Universal Link | P4 (ED-39) |
| Testes do RNF-39 no CI (emulador) e pentest dos apps | P4, antes do primeiro ensaio com app |
