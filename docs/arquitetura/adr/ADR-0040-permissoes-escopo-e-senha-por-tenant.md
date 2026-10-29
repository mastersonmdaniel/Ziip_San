# ADR-0040 — Permissões por recurso e ação, escopo por área e política de senha por tenant no realm único

- **Status:** Aceito, 14/10/2026
- **Decisor:** dono do produto (cartões de 14/10/2026)
- **Afeta:** ADR-0006 (BFF), ADR-0007 (MFA por tenant), ADR-0018 (papéis da plataforma), ADR-0032 (alçada e
  segregação), ADR-0038, ADR-0039; `docs/arquitetura/PARAMETROS-DO-TENANT.md` §4 e seção de bifurcações (chaves
  `sec.password_*`, `sec.lockout_attempts`, `sec.user_inactivity_days`, `sec.single_session`); plano v2.15 (P0.13, nova
  P3.30, ED-06, GL.2); mapa Flyway (`V1_8`); levantamento `docs/levantamento/gsan/20d` (§4, lacunas 3 e 5, perguntas
  2 e 3)
- **Nota de alinhamento ao NIST SP 800-63B-4 (28/10/2026, plano v3.0), esclarecimento sem mudança de decisão:** a
  revisão 4 final do NIST SP 800-63B (31/07/2025) exige comparar a senha nova com lista de senhas vazadas ou proibidas,
  veda exigir troca periódica sem evidência de comprometimento, veda regras de composição e recomenda mínimo de 15
  caracteres quando a senha é o único fator e de 8 quando há MFA; admite passkey sincronizável em AAL2 (22c, F07). Os
  padrões de fábrica deste ADR já seguem essa linha: lista de senhas vazadas obrigatória, mínimo de 12 com MFA
  disponível, `sec.password_max_age_days` desligado e histórico de 5. A leitura dos itens 4 e 5 passa a ser esta:
  (1) expiração periódica e regra de complexidade continuam disponíveis por tenant para atender a exigência de edital
  (por exemplo, POC-05), mas o console as marca como contrárias à recomendação do NIST e só as liga com aceite
  registrado do tenant (classe b); (2) quando o tenant dispensa o MFA para um perfil (ADR-0007), o console avisa que o
  mínimo recomendado para senha como único fator é 15 caracteres, sem mudar o padrão de fábrica; (3) o autenticador
  próprio da ED-06 aplica a lista de senhas vazadas também na troca e na redefinição de senha, e o teste com dois
  tenants do item 5 cobre essas regras; (4) passkey (WebAuthn, já previsto no ADR-0007 para o backoffice, com suporte no
  Keycloak desde a 26.4.0) entra no portal do consumidor pela ED-38, com o parâmetro `portal.passkey_enabled` (classe d),
  no realm `ziipsan-portal`, que este ADR não rege (ADR-0022, ADR-0043).

## Contexto

O Ziip SAN autentica pelo Keycloak, com um realm de backoffice (`sigsan`, `ziipsan` depois da P0.33) e o realm do
portal (`ziipsan-portal`, ADR-0022). Os papéis do realm viram `ROLE_*` no serviço; as tabelas `roles` (com
`permissions` em JSONB) e `user_roles` existem no `V1_1`, com RLS por tenant, mas nenhuma regra as usa. Não há unidade
organizacional, permissão granular nem escopo por área: o isolamento é só por tenant. A ED-06 (onda A) prevê perfis
compostos pelo ADMIN do tenant, bloqueio por tentativas e expiração de senha por tenant.

O levantamento 20d mostrou que o GSAN tem bom modelo conceitual de autorização (funcionalidade × operação, restrição
negativa, abrangência por nível hierárquico) e implementação frágil: rotas de pesquisa e relatório sem checagem,
protocolo e OS sem filtro de abrangência, bloqueio por tentativas contado na sessão HTTP. Apontou duas lacunas do Ziip
SAN: sem escopo por área, o piloto operaria atendimento, OS e corte com quatro papéis, sem segregação por localidade;
e, com realm único, a política de senha e a detecção de força bruta do Keycloak valem para o realm inteiro, não por
tenant, o que impede atender a ED-06 e a POC-05 (bloqueio com 3 tentativas) sem solução própria.

## Decisão

1. **Catálogo de permissões `recurso:ação` no P0, junto da P0.13.** O produto mantém um catálogo versionado de
   permissões (`protocol:create`, `so:close`, `connection:cut`), verificadas no serviço, com negação por padrão. Toda
   rota declara a permissão que exige, e um teste no CI recusa rota sem declaração. Os papéis ADMIN, OPERATOR, MANAGER
   e VIEWER passam a perfis de fábrica compostos dessas permissões. O Keycloak autentica e entrega identidade, tenant e
   perfis; a permissão fina é resolvida na aplicação.
2. **Unidade organizacional e escopo por área no P3, na nova P3.30, antes da homologação local.** Cada tenant tem
   unidades organizacionais hierárquicas, ligadas a áreas (localidade, setor, rota). O escopo do usuário por unidade e
   área é aplicado por filtro obrigatório de repositório em toda leitura e escrita de protocolo, OS, unidade
   consumidora e conta; o RLS continua responsável só pelo isolamento entre tenants. O ADMIN do tenant só concede
   escopo e permissão que possui.
3. **A ED-06 amplia sobre os itens 1 e 2.** Perfis compostos pelo ADMIN do tenant, restrição negativa por usuário,
   sessões ativas visíveis e revogáveis, trilha por usuário e federação LDAP/AD continuam na ED-06 (onda A), agora
   construídos sobre o catálogo da P0.13 e o escopo da P3.30.
4. **Política de senha por tenant por autenticador próprio no Keycloak, num realm só.** Bloqueio por tentativas,
   expiração, histórico e complexidade da senha seguem a política do tenant do usuário, lida por um autenticador
   próprio no realm único de backoffice. Os valores são parâmetros do tenant: `sec.password_min_length` (12, só
   amplia), `sec.password_max_age_days` (desligado por padrão), `sec.password_history` (5, só amplia),
   `sec.lockout_attempts` (5, só reduz) e `sec.user_inactivity_days` (90, só reduz), conforme o levantamento 20d §5. A
   contagem de tentativas fica no servidor, por usuário e por IP, com resposta indistinta para usuário inexistente e
   senha errada. Entra na ED-06 (onda A).
5. **Teste a cada atualização do Keycloak.** Um teste com dois tenants de políticas diferentes no mesmo realm
   comprova bloqueio, expiração, histórico e complexidade de cada um. Ele roda no CI e é condição para atualizar a
   versão do Keycloak.

## Consequências

### Positivas

- O piloto opera a homologação local com segregação por área e permissão fina, e não só com quatro papéis.
- Autorização uniforme: nenhuma rota escapa da checagem por nome, ao contrário do GSAN.
- Um só realm de backoffice para todos os tenants, sem multiplicar configuração, chaves e pontos de falha do Keycloak.
- Cada tenant cumpre a exigência do seu edital (por exemplo, POC-05 com 3 tentativas) sem impor a regra aos demais.

### Negativas

- Autenticador próprio no Keycloak é código a manter e pode quebrar a cada atualização; o teste do item 5 vira
  condição de atualização.
- O filtro de escopo em toda consulta de protocolo, OS, unidade consumidora e conta acrescenta custo de
  desenvolvimento e de teste ao P3.
- O catálogo de permissões precisa acompanhar cada rota nova; esquecer a declaração bloqueia o build.

## Alternativas consideradas

- **Permissões e escopo inteiros no P3:** rejeitada pelo dono do produto; o catálogo `recurso:ação` entra no P0,
  junto da P0.13, e só o escopo por área fica no P3.
- **Permissões e escopo só na ED-06 (onda A), com o piloto nos quatro papéis:** rejeitada pelo dono do produto; a
  homologação local ficaria sem segregação por área.
- **Política mais restritiva entre os tenants aplicada ao realm inteiro:** rejeitada pelo dono do produto; impõe a todos
  os tenants a regra do mais exigente.
- **Realm separado sob demanda para o tenant que exigir política própria:** rejeitada pelo dono do produto; multiplica
  realms e foge do modelo de realm único.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| P0.13: catálogo de permissões `recurso:ação`, negação por padrão, teste de rota sem permissão no CI, papéis atuais como perfis de fábrica | P0 |
| Nova P3.30: unidades organizacionais, áreas (localidade, setor, rota) e escopo do usuário com filtro obrigatório, `V1_8`; delegação limitada | P3 |
| GL.2 passa a depender da P3.30 | homologação local |
| ED-06: perfis compostos, restrição negativa, autenticador de senha por tenant com os parâmetros `sec.*` e teste com dois tenants a cada atualização do Keycloak | onda A |
| PARAMETROS-DO-TENANT: chaves `sec.password_min_length`, `sec.password_max_age_days`, `sec.password_history`, `sec.lockout_attempts`, `sec.user_inactivity_days`, `sec.single_session` e nota no §4 | v2.15 |
