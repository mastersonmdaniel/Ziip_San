# Plano de execução do Ziip SAN: fases P0–P4 e go-live piloto

**Versão 2.2, 12/10/2026.** Produto Ziip SAN; nome interno do código, SIGSAN, renomeado para `ziipsan` na P0.33. O
nome do arquivo foi mantido por causa dos links. Este é o plano canônico; em divergência com um ADR, vale o ADR mais
recente.

**O que mudou em relação à v1**

- Roadmap oficial P0–P4 com marco de go-live piloto; R1–R9 vira conteúdo das fases (ADR-0001).
- G1, G2, G4, G5, G6 e G9 viram parâmetros do tenant com aceite formal e deixam de bloquear fases (ADR-0002); G3, G7 e
  G8 foram decididas pelo produto (ADR-0003, ADR-0004).
- Entram a camada de serviço operado (artefato, ambientes, backup, lote, observabilidade, carga), a migração do legado
  e todos os achados críticos, altos e médios da revisão 17.
- `outOfOrder(true)` ganha prazo; depois dele, versões Flyway sempre crescentes (§3).
- NFAg-e sobre motor fiscal próprio, portado do Ziip Cloud, no lugar do integrador.
- BFF com cookie, MFA por tenant, máscara configurável, push automático, desenvolvimento fora do Google Drive.
- Versão 2.1: marca e renomeação `ziipsan`, temas, Central Ziip, cobrança por contrato, ajuda e suporte, Sobre e
  changelog, histórico e documentos nos portais (ADR-0016 a ADR-0022), com as decisões do dono do produto de
  12/10/2026 sobre suspensão, eliminação, retenção de chamados e antivírus (§2.2).
- Versão 2.2: sete pendências do §2.4 resolvidas pelo dono do produto em 12/10/2026 (carência de 30 dias, papéis
  que suspendem e eliminam, acesso de emergência, conta do consumidor no portal, documentos anteriores ao go-live,
  cores dos temas e idioma dos identificadores), com tarefas e critérios ajustados, a nova P2.19 e a convenção de
  idioma no §8.

**Referências.** Revisão 17: [levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md](levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md);
ADRs: [arquitetura/adr/README.md](arquitetura/adr/README.md); parâmetros: [arquitetura/PARAMETROS-DO-TENANT.md](arquitetura/PARAMETROS-DO-TENANT.md);
RNF: [arquitetura/REQUISITOS-NAO-FUNCIONAIS.md](arquitetura/REQUISITOS-NAO-FUNCIONAIS.md); arquitetura-alvo:
[arquitetura/ARQUITETURA-ALVO.md](arquitetura/ARQUITETURA-ALVO.md); porte fiscal: [arquitetura/FISCAL-PORTE-JAVA.md](arquitetura/FISCAL-PORTE-JAVA.md);
PDCA: [levantamento/16-PDCA-2026-10-12.md](levantamento/16-PDCA-2026-10-12.md); execução: [DIARIO-DE-BORDO.md](DIARIO-DE-BORDO.md) §18.

**Convenção.** Achados da revisão 17 levam o prefixo `17:` (`17:X1`, `17:S1`, `17:D1`), para não confundir com as
decisões D1–D13 e S1–S3 da v1. Os médios foram numerados aqui `17:M1`–`17:M15` e os baixos `17:B1`–`17:B11`, na ordem
do relatório.

---

## 1. Estado atual (12/10/2026)

- Fundações E0–E6 concluídas em 12/10/2026 (alvo era 27/11): 973 testes Java e 134 do frontend verdes.
- Isolamento por tenant provado no PostgreSQL real: roles separadas, função da RLS de `sigsan_owner`, RLS FORCE, GUC
  definida e limpa pela conexão, tenant só do JWT.
- Auditoria sem dado pessoal; CPF/CNPJ cifrados com índice cego no cadastro e no MTR.
- Aplicação executável (Spring Boot 4.1.1, Keycloak 26.8.0, PKCE, API do cadastro), aceita de ponta a ponta.
- Cadastro de cliente pela tela bloqueado até o R2 (tipo de cliente, perfil de imóvel, endereço).
- Faturamento, medição e arrecadação: motores puros, sem serviço, API nem lote.
- Chave só de desenvolvimento; nenhum CPF real antes do R1.
- Só ambiente local: sem Dockerfile, CI remoto, backup nem observabilidade.
- GitHub privado `mastersonmdaniel/Ziip_San` (push manual até `fb2b53d`); código ainda editado no Google Drive.
- Sem cliente piloto e sem hospedagem definidos.

**Histórico.** A v1 consolidou o PDCA de 12/10 (C1–C5) e o roadmap R0–R9 do `15c` em fundações E0–E6, com mapa Flyway e
portões de decisão. As etapas foram executadas no mesmo dia, um commit por etapa (diário §18). A revisão 17, sobre
`fb2b53d`, concluiu que o código está correto mas ainda não é serviço operável, o que motivou esta versão.

---

## 2. Decisões

### 2.1 Técnicas da v1

**=** mantida; **Δ** alterada.

| # | Decisão | Situação |
|---|---|---|
| D1 | `.git` fora do Drive (`--separate-git-dir`) | Δ todo o desenvolvimento sai do Drive no P0 (ADR-0009) |
| D2 | Push só com autorização | Δ **revogada pelo ADR-0009**: push automático com testes verdes |
| D3 | Autor: nome e e-mail do usuário | = |
| D4 | Spring Boot 4.1.1 (suporte OSS até 31/07/2027) | = linha seguinte antes de 31/07/2027 (P3.14) |
| D5/D6 | Realm único, claim `tenant_id` (UUID) | Δ mais gestão de usuários que fixa o `tenant_id` (17:S3); consumidores do portal em realm próprio, `ziipsan-portal` (ADR-0022) |
| D7 | Keycloak 26, banco próprio | = modo de produção na P0.29 |
| D8 | API na 8090 | = |
| D9 | Cifra de dev por HKDF; só dado sintético até o R1 | Δ `dev-env` só em lista fechada de perfis (17:M2) |
| D10 | Migrations por job separado | Δ a JVM da API não recebe credencial de migração (17:S2) |
| D11 | Rascunhos e `validate-step` no R2 | = (P1.3) |
| D12 | ADMIN, MANAGER, OPERATOR, VIEWER, `registry.reveal-document` | Δ novos: busca por documento, aprovação de parâmetros, plataforma |
| D13 | `oidc-client-ts`, token em memória | Δ **substituída pelo ADR-0006** (BFF); hoje o token está em `sessionStorage` |
| G10a | `outOfOrder(true)` + teste "tenant antigo alcança o atual" | Δ só até o fim do P3; depois, versões sempre crescentes (17:D1) |
| S1–S3 (v1) | `DocumentNumber` no core; CPF mascarado no PDF do MTR; `drivers` cifrado | = implementadas; máscara segue o ADR-0008 |

### 2.2 Decisões de 12/10/2026

| ADR | Decisão |
|---|---|
| [0001](arquitetura/adr/ADR-0001-roadmap-p0-p4.md) | P0–P4 com go-live piloto; piloto escolhido no P0 |
| [0002](arquitetura/adr/ADR-0002-decisoes-g-como-parametros-do-tenant.md) | Parâmetros do tenant com vigência, sugerido, limite e aceite; contrato, DPA e termos no P3 |
| [0003](arquitetura/adr/ADR-0003-motor-fiscal-portado-para-java.md) | Motor fiscal C# portado para Java; NFAg-e (modelo 75) e NFS-e avulsa sobre ele |
| [0004](arquitetura/adr/ADR-0004-sinisa-e-sinir-assistidos.md) | SINISA e SINIR assistidos: arquivo oficial, o cliente envia |
| [0005](arquitetura/adr/ADR-0005-isolamento-hibrido-por-porte.md) | Isolamento híbrido por porte; shard no catálogo; teste com 300 schemas |
| [0006](arquitetura/adr/ADR-0006-bff-com-cookie-httponly.md) | BFF com cookie HttpOnly, no processo `api`, sessão no PostgreSQL |
| [0007](arquitetura/adr/ADR-0007-mfa-configuravel-por-tenant.md) | MFA configurável por tenant; plataforma sempre com MFA. Prevalece sobre o 17:S4 (MFA obrigatório por papel) |
| [0008](arquitetura/adr/ADR-0008-mascara-de-documento-configuravel.md) | Máscara configurável com teto; busca por documento com papel, registro e cota |
| [0009](arquitetura/adr/ADR-0009-push-automatico-e-dev-fora-do-drive.md) | Push automático; desenvolvimento em disco local + GitHub |
| [0010](arquitetura/adr/ADR-0010-infra-nao-usada-congelada.md) | Infra não usada em perfis em 127.0.0.1; k8s e Terraform em `infra/_arquivado/` |
| [0011](arquitetura/adr/ADR-0011-hospedagem-a-decidir.md) | Hospedagem: **proposto, pendente**; plano neutro; R1, backup e IaC bloqueados |
| [0012](arquitetura/adr/ADR-0012-metas-nao-funcionais.md) | Metas não funcionais do §6 da revisão 17 |
| [0013](arquitetura/adr/ADR-0013-postgresql-17-no-p0.md) | PostgreSQL 17 + PostGIS 3.5 no P0, antes de dado real |
| [0014](arquitetura/adr/ADR-0014-outbox-no-postgresql.md) | Outbox no PostgreSQL com workers `SKIP LOCKED`, atrás de interface |
| [0015](arquitetura/adr/ADR-0015-limites-legais-dos-parametros.md) | Classes de parâmetro (a–d); multa padrão 2% com aceite reforçado acima; vedações de corte por inadimplência travadas; piso da tarifa social; pisos de retenção |
| [0016](arquitetura/adr/ADR-0016-rebrand-e-renomeacao-ziipsan.md) | Pacote de marca único; marca do tenant; renomeação `sigsan` → `ziipsan` no P0, com contexto HKDF congelado |
| [0017](arquitetura/adr/ADR-0017-temas-claro-escuro-azul-e-verde-agua.md) | Temas claro, escuro e do sistema; padrões Azul Água e Verde Água; preferência no servidor; contraste no CI |
| [0018](arquitetura/adr/ADR-0018-central-ziip-propria.md) | Central Ziip própria com o console; papéis de plataforma com MFA; sem leitura de dado pessoal de tenant; API de métricas `v1` |
| [0019](arquitetura/adr/ADR-0019-cobranca-saas-por-contrato.md) | Cobrança do SaaS por contrato; nenhum bloqueio automático; restrição só manual |
| [0020](arquitetura/adr/ADR-0020-ajuda-e-suporte-com-protocolo.md) | Ajuda e suporte com protocolo e SLA por contrato; ClamAV; acesso assistido com consentimento no lugar da impersonação; acesso de emergência só em incidente de segurança declarado |
| [0021](arquitetura/adr/ADR-0021-sobre-versao-e-changelog.md) | SemVer gerado no build; changelog do cliente obrigatório para segurança, legislação e parâmetro regulatório; Sobre |
| [0022](arquitetura/adr/ADR-0022-portais-historico-e-documentos.md) | Portal do consumidor com 120 meses, conta paga, DANFAG e XML original; conta do consumidor no realm `ziipsan-portal`, vínculo por código impresso na conta e CPF/CNPJ; documentos anteriores ao go-live importados; área do tenant com faturas e NFS-e do contrato |

**Decisões do dono do produto (cartões de 12/10/2026)**, aplicadas nos ADRs 0018–0020, no ADR-0010 e nos documentos
de arquitetura:

- Suspensão de tenant sempre manual, com aviso prévio; backoffice em somente leitura; portal do consumidor, Pix,
  retornos bancários e 2ª via continuam (serviço essencial).
- Suspensão, bloqueio e eliminação de dados de tenant: uma pessoa com MFA basta, sempre com justificativa e auditoria;
  a eliminação tem carência de 30 dias e certificado de descarte. Suspensão e bloqueio: `PLATFORM_OWNER` ou
  `PLATFORM_FINANCE`, este só por inadimplência contratual; eliminação: só `PLATFORM_OWNER` (§2.4). A aprovação dupla
  continua nos parâmetros do tenant (ADR-0015), no ajuste de conciliação e na liberação da emissão fiscal em produção.
- Chamados de suporte e anexos retidos pela vigência do contrato mais 5 anos, com minimização, acesso restrito e
  eliminação no fim do prazo.
- ClamAV junto do `worker`, com quarentena até a varredura, substituível pelo serviço da hospedagem: exceção do
  ADR-0010 (anexos de chamados e logos de tenant).
- Corte por inadimplência: nenhuma configuração do tenant afrouxa (ADR-0015, item 3).

### 2.3 G1–G9 reclassificadas e padrões do produto

Padrões definidos pelo dono do produto em 12/10/2026 estão na última coluna.

| # | Tema | Classe | Fase | Padrão do produto |
|---|---|---|---|---|
| G1 | Agência, norma, NR 11/13 | parâmetro | P1 | — |
| G2 | Tarifa social | parâmetro | P1 | piso federal travado no sistema após conferência no DOU; o tenant só concede benefício maior |
| G3 | NFAg-e | produto | P1–P2 | construir sobre o motor portado (ADR-0003) |
| G4 | Bancos, convênios, Pix, PSP | parâmetro, dentro do escopo suportado (§2.4) | P3 | — |
| G5 | Multa e juros | parâmetro | P2 | multa padrão 2%; acima de 2% só com aceite formal registrado |
| G6 | Retenção e prescrição | parâmetro | P2 | pisos fixos pelo Ziip SAN (logs de acesso 6 meses, Marco Civil; histórico 120 meses, NR 11); o tenant só amplia; eliminação no fim do contrato por procedimento do Ziip com certificado de descarte |
| G7, G8 | SINISA, SINIR | produto | P4, 2028 | assistidos (ADR-0004) |
| G9 | Corte | parâmetro | P3 | vedações da Lei 14.015 (sexta, fim de semana, feriado, véspera) para todos os motivos; exceção só emergência técnica com justificativa e foto; outra regra só com aceite formal |

Sem valor aceito e vigente, a operação falha com erro explícito; o tenant não entra em produção sem o conjunto
obrigatório aceito.

### 2.4 Pendências de decisão

**Em aberto**

| Decisão | Dono | Até | Bloqueia |
|---|---|---|---|
| Hospedagem (ADR-0011) | Daniel | 30/10/2026 | P0.27–P0.29; P1.2 com dado real |
| Cliente piloto, acesso ao legado, acordo de tratamento de dados mínimo | Daniel | 13/11/2026 | P1.1, P1.2, go-live |
| G4 no produto: leiautes (FEBRABAN 150, CNAB 240) e PSPs suportados no go-live | Daniel | 29/01/2027 | P3.1, P3.2 |
| NFAg-e: obrigatoriedade e data para o perfil do piloto, no DOU; manual e XSD oficiais | Daniel | 27/11/2026 | P2.6 |
| Arredondamento (half-even no C#, `HALF_DOWN` no billing) e tributo no CHECK de total de `bills` (hoje tratado como retenção) | técnico | 29/01/2027 | P2.4, P2.5 |

**Resolvidas pelo dono do produto em 12/10/2026 (cartões)**

| Pendência | Decisão | Onde foi aplicada |
|---|---|---|
| Valores HSL de Azul Água e Verde Água | Proposta de MARCA-E-TEMAS §4.2 aprovada, nos modos claro e escuro; ajustes finos permitidos depois de ver as telas, mantendo o teste de contraste | ADR-0017; MARCA-E-TEMAS §4.2; P0.37 |
| Duração da carência antes da eliminação de dados de tenant | 30 dias contados do fim do contrato, para o cliente baixar a exportação e contestar. O RNF-42 passa a exigir eliminação concluída e certificado de descarte em até 30 dias após o fim da carência, ou seja, até 60 dias do fim do contrato | RNF-42; ADR-0015; ADR-0018; CENTRAL-ZIIP §6.3; PARAMETROS G6; P1.6, P3.16 |
| Quem executa suspensão, bloqueio e eliminação | Suspensão e bloqueio: `PLATFORM_OWNER` ou `PLATFORM_FINANCE`, este só por inadimplência contratual; eliminação: só `PLATFORM_OWNER`. Sempre uma pessoa com MFA, step-up, justificativa e auditoria; sempre manual, com aviso prévio; suspensão deixa o backoffice só leitura e mantém portal, Pix, retornos e 2ª via | ADR-0018; ADR-0019; CENTRAL-ZIIP §2 e §6.2; P0.43, P1.6, P2.12 |
| Exceção de emergência ao consentimento do acesso assistido | Admitida só em incidente de segurança declarado: somente leitura, só `PLATFORM_OWNER`, reautenticação com MFA no momento, aviso ao tenant em até 24 h, registro completo na auditoria e no registro do incidente | ADR-0020 (seção Exceção); CENTRAL-ZIIP §7; AJUDA-SUPORTE-SOBRE §5; P3.17 |
| Autenticação do consumidor no portal e vínculo com a matrícula | Conta própria no Keycloak, no realm `ziipsan-portal`, separado do realm do backoffice (e-mail ou celular e senha, MFA opcional); vínculo comprovado pelo código de vinculação impresso na conta mais o CPF/CNPJ do titular; uma conta com vários imóveis; limite de taxa e resposta indistinta na vinculação | ADR-0022; ARQUITETURA-ALVO §1; P2.8, P3.3, P3.8, P3.20; tenant `V8_2` |
| Contas e NFAg-e anteriores ao go-live no portal | Na migração, importar os XML autorizados que o cliente tiver (sistema antigo ou emissor usado no intervalo) e gerar o PDF das contas antigas a partir dos dados importados; o portal mostra os 120 meses num lugar só, com a origem de cada documento | ADR-0022; FISCAL-PORTE-JAVA §3.8; nova P2.19 (`V14_3`); GL.1; P3.20 |
| Idioma dos identificadores | Tabelas, colunas, schemas, rotas e identificadores de código em inglês, como no código atual; textos ao usuário e documentação em português. Nomes renomeados em todos os documentos (`suporte` → `support`, `chamado` → `ticket`, `aceite_documento` → `document_acceptance`, `implantacao` → `deployment`, `/ajuda` → `/help`, `/suporte` → `/support`, `/sobre` → `/about`) | §8 (Idioma); §3; ADR-0014, ADR-0020 a ADR-0022; AJUDA-SUPORTE-SOBRE; CENTRAL-ZIIP; FISCAL-PORTE-JAVA; ARQUITETURA-ALVO |

---

## 3. Mapa de versões Flyway

Duas histórias: **core** (`public`, uma vez por banco) e **tenant** (uma por schema). Existentes, conferidas em
`**/db/migration/**` em 12/10/2026: core `V1`, `V2`; tenant `V1_1`–`V1_4`, `V2_1`–`V2_10`, `V3_1`–`V3_3`, `V4_1`, `V5_1`,
`V5_2`, `V6_1`.

**Regra de ordem.**
- Core nunca usa `outOfOrder`: o número sai na ordem do merge; a tabela mostra a ordem esperada e é corrigida se ela mudar.
- Tenant: faixa por módulo com `outOfOrder(true)` até o fim do P3; até lá todo tenant é recriável (massa sintética ou
  reimportação do legado, que segue como fonte da verdade). Na P3.12, antes do paralelo, `outOfOrder(false)`; daí em
  diante a versão vem de uma sequência global a partir de `V100`, com o módulo no nome (`V100__dunning_actions.sql`), e o
  CI recusa versão menor ou igual à maior publicada. Reservas não usadas até lá são renumeradas aqui.
- Expand/contract: coluna só sai na release seguinte à que deixou de usá-la.
- Subversões do core: para não renumerar reservas, tabelas novas do core entram como subversão da reserva da mesma
  fase (core `V5_1`, `V9_1`…), que o Flyway ordena entre as inteiras. O prefixo "core" as distingue das versões de
  tenant de mesmo número (por exemplo, tenant `V9_1`, field), que ficam em outra tabela de histórico.
- A coluna "Módulo" com `platform` designa o módulo Maven `platform/*-platform` (história de tenant). Os schemas
  `platform` e `support` do banco do core (ADR-0018, ADR-0020) entram pelas versões do core. Só o banco principal, o
  do catálogo, os usa; nos clusters adicionais (ADR-0005) ficam vazios, salvo decisão diferente na P1.14.
- Renomeação (P0.33): muda o checksum das migrations existentes, porque os `GRANT` citam as roles; aceito porque não
  há banco com dado real, e banco e Keycloak de desenvolvimento são recriados. Depois dela, as migrations voltam a ser
  imutáveis.

**Mapa da v1, corrigido pelo que foi aplicado**

| Versão | Módulo | Conteúdo | Situação |
|---|---|---|---|
| core `V1`, `V2`; `V1_1`–`V1_4` | core, platform | catálogo e `audit_log`; redação; schema do tenant; auditoria endurecida | existentes |
| `V1_5` | platform | parâmetros do tenant; `holidays` sai do `V6_1` | reservada, P0 (era R4) |
| `V2_1`–`V2_10` | quality, losses, gis, mtr, lgpd | schemas; redação; cifra do MTR em `V2_8` (a v1 previa `V2_6`) | existentes |
| `V3_1`–`V3_3` | registry | schema; redação (a v1 reservava `V3_3` para o log de acesso, que vai para `V1_6`) | existentes |
| `V4_1`; `V5_1`, `V5_2`; `V6_1` | billing, metering, collection | schemas; `V5_2` é redação (tarefas de leitura vão para `V5_3`) | existentes |
| `V4_2` / `V4_3`, `V4_4` | billing | tarifa social / ciclo, modelos de conta | reservadas, P1 / P2 |
| `V4_5` | `sigsan-fiscal` | tributos com vigência (CBS/IBS) e motor fiscal: certificados, séries, documentos, eventos, lotes | reservada, P1 |
| `V6_2`; `V8_1`, `V9_1` | collection; portal, field | conciliação, Pix; protocolo; OS e bloqueios | reservadas, P3 |
| `V7_x`, `V10_x`, `V12_x`, `V13_x` | integration, dunning, GRS, GIS | SINISA; cobrança; DMR; incidente | P4/2028, sequência global |
| `V11_x` | events | outbox por tenant | substituída por core `V8` (ADR-0014: uma outbox por banco) |

**Reservas novas**

| Versão | Módulo | Conteúdo | Tarefa |
|---|---|---|---|
| core `V3` | core | `parameter_definitions`, `parameter_legal_limits` (catálogo global do produto, só leitura para a aplicação) | P0.19 |
| core `V4` | core | `tenant_catalog.cluster_id`, `isolation_mode`; revisão do `schema_name UNIQUE` (`core/V1:21`); `tenant_schema_version`; `sigsan_app` só com SELECT | P0.17, P0.21, P1.14 |
| core `V5` | core | sessões do BFF | P0.14 |
| core `V5_1` | core | schema `platform`; `platform.deployment` (registro de implantação), gravada pelo `migrate` (ADR-0021) | P0.42 |
| core `V6` | core | `tenant_key` (DEK por tenant). Era "core `V3`" no R1 da v1: o R1 depende da hospedagem e o core não aceita fora de ordem | P0.28 |
| core `V7` | core | `audit_log` particionado por mês, PK `(created_at, id)` com UUIDv7, retenção | P1.7 |
| core `V8` | core | `outbox` com `tenant_id`, RLS e partição diária | P1.9 |
| core `V9` | core | console v0 no schema `platform`: `plan`, `tenant_subscription`, `feature_flag`, `onboarding_step`, `tenant_lifecycle`, `audit_event`. A impersonação sai: o acesso assistido do ADR-0020 vai para o core `V10_3` | P1.6 |
| core `V9_1` | core | Central v0: `platform.metric_definition`, `metric_point` (partição diária), `metric_daily`, `alert_rule`, `alert_event`, `config_item`; role `ziipsan_central`; funções `platform_stats.*` | P1.17 |
| core `V9_2` | core | `public.tenant_assets` (logo do tenant, com estado da varredura); função `SECURITY DEFINER` da marca pré-login, restrita às chaves `branding.*` de `tenant_settings` | P1.19 |
| core `V9_3` | core | schema `support`; conteúdo de ajuda: `article`, `article_version`, `article_feedback`, `tour_completion`, `error_explanation` | P1.21 |
| core `V9_4` | core | `support.ticket`, `ticket_message`, `ticket_attachment`, `ticket_event`; sequência do protocolo `SUP-AAAA-NNNNNN` | P1.23 |
| core `V9_5` | core | `platform.document_acceptance`; `support.release_note_view` | P1.24 |
| core `V10` | core | `job_runs` e travas por (tenant, ciclo) | P2.1 |
| core `V10_1` | core | contrato SaaS no schema `platform`: contrato (com a política de SLA), aditivo, empenho, reajuste, fatura, pagamento, referências aos documentos da fatura e da NFS-e | P2.12–P2.14 |
| core `V10_2` | core | `support.sla_policy`, `csat_response`; prazos e pausa no chamado | P2.15 |
| core `V10_3` | core | `support.access_consent`, `assisted_access_session` (tipo `CONSENTED` ou `EMERGENCY`; na emergência, referência ao incidente no lugar do consentimento) | P3.17 |
| core `V10_4` | core | `support.status_component`, `status_incident`, `status_update`, `maintenance_window` | P3.18 |
| `V1_5` | platform | `parameter_versions` (vigência com EXCLUDE, quem propõe ≠ quem aprova), `parameter_acceptances`, `regulatory_profiles`, `holidays` | P0.19 |
| `V1_6` | platform | `personal_data_access_log`; cota de busca por documento | P0.11 |
| `V1_7` | platform | `user_preferences` (modo e padrão de tema do usuário) | P1.18 |
| `V3_4` | registry | GIN trigram sobre `unaccent(lower(name))` | P0.22 |
| `V3_5` | registry | sequência de matrícula por tenant | P1.4 |
| `V3_6` | registry | R2: `customer_types`, `property_profiles`, endereço, `block_faces`, rascunhos | P1.3 |
| `V4_6` | billing | partição de `bills`/`bill_items`; auditoria por lote; CHECK de total com tributo | P2.3, P2.5 |
| `V4_7` | `sigsan-fiscal` | NFAg-e e NFS-e avulsa: leiaute, contingência, inutilização | P2.6, P3.6 |
| `V5_3` / `V5_4` | metering | tarefas e importação de leitura / partição de `meter_readings`, chave de idempotência | P2.7 / P2.3 |
| `V6_3` | collection | partição de `payments`; `pix_inbox` | P3.2 |
| `V8_2` | portal | vínculo da conta do consumidor (`sub` do realm `ziipsan-portal`) com a matrícula: método de comprovação, data, revogação; hash do código de vinculação por imóvel; tentativas de vinculação | P3.20 |
| `V14_1`, `V14_2` | importação de legado | lotes, staging, conciliação; histórico de 120 meses | P1.2, P2.10 |
| `V14_3` | importação de legado | lotes de XML autorizados anteriores ao go-live, resultado da validação (assinatura, protocolo, emitente, duplicidade) e origem do documento | P2.19 |

`current_tenant_id()` como SQL STABLE (17:M10) não é migration: a função é de `sigsan_owner` e nasce no init de
segurança (`infra/postgres/init/sql/security.sql`); o migrador não pode redefini-la, por desenho. Nos bancos existentes,
script de operação como `postgres`.

---

## 4. Fases

Tamanho: **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa com agentes. Toda tarefa termina
com testes verdes, registro no diário e push.

**Nomes depois da renomeação.** A partir da P0.33, os caminhos, módulos, pacotes, propriedades, roles e realm citados
neste plano com `sigsan` (`commercial/sigsan-fiscal`, `sigsan_worker`, `sigsan.fiscal.*`, realm `sigsan`) leem-se com
`ziipsan`. O plano não é reescrito por isso; tarefas novas já usam o nome final (`ziipsan_central`,
`urn:ziipsan:problema:`). Commits e diário anteriores valem até a tag `pre-rename`.

### P0 — Plataforma operável · 13/10–27/11/2026

Objetivo: ambiente fora do Drive, código renomeado para `ziipsan`, artefato implantável com versão gerada, marca e
temas, altos e médios de segurança, parâmetros do tenant e, se a hospedagem for decidida a tempo, homologação e
produção com PITR e R1.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P0.1 | Desenvolvimento em disco local; GitHub como fonte da verdade | ADR-0009, 17:M15 | M | — | build e `ci.sh` verdes num clone fora do Drive, sem robocopy nem PowerShell; `.env` fora do Drive |
| P0.2 | Push automático ao fim de etapa | ADR-0009 | P | P0.1 | com teste vermelho nada é publicado |
| P0.33 | Renomeação `sigsan` → `ziipsan` como tarefa única, logo após a P0.1 e antes das demais tarefas de código do P0, na ordem de MARCA-E-TEMAS §5.2 | ADR-0016 | M | P0.1 | tag `pre-rename`, sem branch aberto; `mvn clean verify`, testes do frontend, `ci.sh` e `dev-up.sh` do zero verdes; login ponta a ponta no realm `ziipsan`; teste de vetor fixo de `ziipsan/enc/v1` e `ziipsan/bidx/v1`, congelados daí em diante; busca residual sem `sigsan` fora da lista permitida |
| P0.3 | CI no GitHub Actions | 17:X1, 17:S6 | M | P0.1 | Java com Postgres de serviço e frontend; `npm audit` alto e gitleaks bloqueiam; `main` protegida |
| P0.4 | PostgreSQL 17 + PostGIS 3.5 | ADR-0013, 17:X2 | M | — | dev, integração e CI na mesma versão; suíte verde |
| P0.5 | Dockerfile multi-stage não root; uma imagem para `api`, `worker`, `migrate`; `config.json` do frontend em execução | 17:X1, 17:B7 | M | P0.3 | UID ≠ 0, sem segredo, SBOM, tag fixa; trocar o emissor OIDC não exige novo build |
| P0.6 | Migração só no job `migrate` | 17:S2, D10 | P | P0.5 | perfil `prod` sem `migratorDataSource` (teste de contexto) |
| P0.7 | Congelar infra parada | ADR-0010, 17:M13, 17:O2, 17:B8 | P | — | `docker compose up` sobe só PostgreSQL e Keycloak; demais em `profiles:` e 127.0.0.1; k8s e Terraform arquivados |
| P0.8 | Observabilidade mínima | 17:O1 | M | P0.5 | métricas HTTP e Hikari, traces com `tenant_id`, `pg_stat_statements`, logs JSON com `X-Request-ID` |
| P0.9 | Pools e timeouts | 17:D3 | P | — | Hikari dimensionado; `statement_timeout` e `idle_in_transaction_session_timeout` na role; consulta longa cancelada (teste) |
| P0.10 | API versionada | 17:M12 | P | — | `/api/v1`; OpenAPI gerado e conferido no CI |
| P0.11 | Máscara com teto; busca por documento com papel, registro e cota; leituras registradas | 17:S1, 17:M6, ADR-0008 | M | P0.19 | teto acima recusado; sem papel → 403; buscas e leituras em `V1_6`; cota excedida → 429 e alerta |
| P0.12 | Recalcular em lote as máscaras já gravadas | ADR-0008 | P | P0.11 | job decifra e regrava `*_masked` (`CustomerDocumentService.java:72`, `MtrDocumentProtection.java:74,99`); nenhuma máscara acima do teto |
| P0.13 | Gestão de usuários por tenant | 17:S3, 17:M5, 17:B3 | G | — | ADMIN de A não altera usuário de B; `tenant_id` só pela API própria; papel de plataforma auditado; realm por tenant e hash duplicado em `users` removidos |
| P0.14 | BFF com cookie HttpOnly no processo `api`, sessão no PostgreSQL | 17:S5, ADR-0006 | G | P0.5 | core `V5`; nenhum token no navegador (teste E2E); cookie `__Host-` HttpOnly, Secure, SameSite; CSRF; refresh com rotação; réplicas sem afinidade |
| P0.15 | MFA | ADR-0007 (prevalece sobre 17:S4) | M | P0.13 | plataforma sempre com segundo fator; política por tenant aplicada no login |
| P0.16 | Superfície web | 17:S5 | M | P0.14 | CSP estrita; HSTS atrás de proxy; rate limit por IP, usuário e tenant (429); `@Size` (400); limite de corpo (413) |
| P0.17 | Médios de segurança | 17:M1–M4, 17:B4, 17:S6 | P | P0.3 | um teste por item: cache com tenant do contexto; `dev-env` só em perfis listados; `sigsan_app` sem escrita no catálogo; schema existente recusado; `/api/me` sem schema; `maplibre-gl` 6 e `react-router` 7 |
| P0.18 | `current_tenant_id()` como SQL STABLE | 17:M10 | P | P0.4 | `DatabaseHardeningTest` verde; sem subtransação por linha |
| P0.19 | Parâmetros do tenant | ADR-0002 | G | P0.4 | core `V3` + `V1_5`; fora do limite recusado; vigência sobreposta recusada pelo banco; sem valor aceito → erro explícito |
| P0.20 | Limites, sugeridos e pisos do §2.3 | ADR-0002 | M | P0.19 | cada limite com norma e teste; multa > 2% só com aceite (ajustar CHECK de `V6_1` e `LegalLimits`); piso de retenção não reduzível |
| P0.21 | Shard no catálogo; teste com 300 schemas | ADR-0005, 17:D2 | G | P0.4 | core `V4`; tempo de migração, catálogo, vacuum e latência medidos; teto por cluster registrado |
| P0.22 | Busca e listas | 17:D4, 17:D5 | M | — | `V3_4`; busca "contém" usa o índice; listas com número constante de consultas; keyset nas grandes |
| P0.23 | Carga inicial | 17:O4, ADR-0012 | M | P0.8 | k6 ou Gatling noturno, 20 mil ligações sintéticas; p95 contra o RNF; regressão > 15% no p95 falha (RNF-70) |
| P0.24 | Modelagem de ameaças (STRIDE) | 17 §7 | P | P0.14 | API, BFF, worker, webhook e importador com controle ou tarefa |
| P0.25 | Dívidas da execução; SINIR em modo assistido | diário §18, ADR-0004 | P | — | sem `ObjectMapper` do Jackson 2 em collection; `SinirIntegrationService` removido ou desligado sem caminho de chamada; `grantConsent` sem versão manual |
| P0.26 | Decisão de hospedagem | ADR-0011 | P | — | ADR-0011 aceito: provedor, região brasileira, banco, cofre, custo |
| P0.27 | Homologação e produção com IaC | 17:X1, 17:O2 | G | P0.26 | sem segredo no state; deploy pelo pipeline; produção com aprovação manual; rollback exercitado |
| P0.28 | HA, backup e PITR; R1 (cofre e chaves) | 17:X2, R1 | G | P0.26 | PITR com RPO ≤ 15 min e RTO ≤ 4 h medidos; core `V6`; `TenantKeyProviderContract` verde no cofre; rotação; backup da KEK |
| P0.29 | Keycloak de produção | 17:O3 | M | P0.27 | `start --optimized`, TLS, `sslRequired=all`, 2 réplicas |
| P0.30 | Escolha do piloto | ADR-0001 | P | — | termo de intenção, acesso ao legado, acordo de tratamento de dados e critério de divergência do go-live |
| P0.31 | Comunicação aos clientes-alvo | G0 (v1) | P | — | até 23/10/2026: o que entra em cada fase e o que segue no legado |
| P0.32 | Esqueleto fiscal | ADR-0003 | M | — | `commercial/sigsan-fiscal`; vetores do C# passam no Java; sem `production-allowed` não há `tpAmb=1` |
| P0.34 | Pacote de marca | ADR-0016 | M | P0.33, P0.5 | `branding/ziipsan` validado por schema no CI (SVG com `script` recusado); `BrandProvider`; plugin do Vite; `GET /api/v1/public/brand`; `check-brand-literals.sh` no `ci.sh` falha com literal de marca fora do pacote |
| P0.35 | Tema de login e de e-mail do Keycloak gerado do pacote | ADR-0016 | P | P0.34 | realm com `loginTheme`, `emailTheme` e `displayName` do pacote; trocar `ZIIPSAN_BRAND` muda login e e-mail sem alterar código; o tema entra na imagem de produção da P0.29 |
| P0.36 | Temas claro, escuro e do sistema | ADR-0017 | M | P0.16 | tokens semânticos; registro com Azul Água e Verde Água; lint recusa cor literal (`Badge.tsx`, `Alert.tsx` migrados); script inline aplica o tema antes da primeira pintura, com hash na CSP conferido pelo CI; contraste WCAG 2.1 AA por padrão × modo (Vitest e Playwright com axe) bloqueia o merge |
| P0.37 | Valores dos padrões aplicados e ajuste fino | ADR-0017 | P | P0.36 | valores de MARCA-E-TEMAS §4.2, aprovados pelo dono em 12/10/2026, no registro de padrões; teste de contraste verde com eles; ajuste fino feito depois de o dono ver as telas só entra com o teste verde e o valor novo registrado no §4.2 |
| P0.38 | Keycloak Organizations para a marca no login | ADR-0016 | P | P0.13 | decisão registrada em MARCA-E-TEMAS §3.3 (subdomínio, e-mail primeiro ou caminho), com prova de conceito |
| P0.39 | Versão gerada pela tag | ADR-0021 | M | P0.3, P0.5 | `-Drevision` no Maven; mesma versão, commit e data no Vite; `build-info`, `git.properties`, `version.json`; nenhuma versão escrita à mão; dois builds do mesmo commit dão a mesma versão; `Ziip-Version` e `Ziip-Min-Client-Version` em toda resposta; SPA antigo mostra aviso de recarga (teste E2E) |
| P0.40 | Licenças de terceiros pelo SBOM | ADR-0021, RNF-35 | P | P0.5 | `third-party-licenses.json` gerado do CycloneDX (Maven e npm); licença fora da lista permitida bloqueia o CI |
| P0.41 | Changelog do cliente | ADR-0021 | P | P0.3 | esquema de `content/release-notes/<version>.yaml` validado; commit marcado como segurança, legislação ou parâmetro regulatório sem item bloqueia a release (teste do CI) |
| P0.42 | Registro de implantação e Sobre mínimo | ADR-0021 | P | P0.6, P0.39 | core `V5_1`; o `migrate` grava `platform.deployment`; `GET /api/v1/about` devolve versão, commit, ambiente e data gravada |
| P0.43 | Papéis de plataforma | ADR-0018, ADR-0007 | P | P0.13, P0.15 | `PLATFORM_OWNER`, `PLATFORM_OPS`, `PLATFORM_SUPPORT` e `PLATFORM_FINANCE` no realm; matriz de permissões de CENTRAL-ZIIP §2 com teste por papel; rota `/api/v1/platform/*` sem `amr` → 403 `mfa_obrigatorio`; token com `tenant_id` recusado em rota de plataforma |

P0.33 vem logo depois da P0.1: as demais tarefas de código do P0 dependem dela (dependência implícita, não repetida
na coluna). P0.27–P0.29 só começam depois da P0.26. A regra pura do R3 pode começar no P0, em paralelo.

### P1 — Migração e satélite · 30/11/2026–29/01/2027

Objetivo: cadastro do piloto importado e conciliado; R2, R3, console e Central Ziip v0, outbox, marca do tenant,
ajuda e chamados v0.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P1.1 | Discovery do legado | 17:X5 | M | P0.30 | dicionário, mapeamento campo a campo e volumes aprovados pelo piloto |
| P1.2 | Importador v0 do cadastro com conciliação | 17:X5 | G | P1.1, P1.4, P0.28 | `V14_1`; reimportação não duplica; totais por setor e rota iguais ao legado; dado real só em produção |
| P1.3 | R2: cadastro completo | R2 (v1) | G | — | `V3_6`; cliente cadastrado pela tela; rascunho e `validate-step`; CEP; CSV |
| P1.4 | Matrícula por sequência | 17:M9 | P | — | `V3_5`; dois lotes paralelos sem espera, sem lacuna nem duplicata |
| P1.5 | R3: tarifa social satélite + N4 | R3 (v1), G1, G2 | M | P0.20 | `V4_2`; casos da v1 (T-03 → 102,00 e 87,00; CadÚnico de 25 meses inelegível; `GRACE_3M`); benefício abaixo do piso federal recusado; em homologação até 11/12/2026 |
| P1.6 | Console da plataforma v0 | 17:P1, ADR-0002, ADR-0018 | G | P0.13, P0.19, P0.43, P1.9 | core `V9`; assistente de implantação; produção bloqueada sem parâmetros aceitos; planos, cotas, flags; suspensão manual com aviso prévio, por uma pessoa com MFA, step-up e justificativa (`PLATFORM_OWNER`, ou `PLATFORM_FINANCE` só com motivo inadimplência contratual), deixando o backoffice só leitura e o portal, o Pix, os retornos e a 2ª via funcionando (teste); `PLATFORM_FINANCE` com outro motivo ou pedindo eliminação → 403 (teste); exportação e eliminação com carência de 30 dias e certificado de descarte, eliminação só por `PLATFORM_OWNER`; toda ação auditada em `platform.audit_event`. A impersonação sai: acesso assistido na P3.17 (ADR-0020) |
| P1.7 | Partição do `audit_log` | 17:X3, 17:M7 | G | P0.4 | core `V7`; UUIDv7; retenção com piso; consulta por período poda partições |
| P1.8 | Porte fiscal: NFS-e Nacional, cofre de certificado, numeração | ADR-0003 | G | P0.32, P0.28 | `V4_5`; emissão, cancelamento e substituição na produção restrita; sem número repetido ou perdido |
| P1.9 | Outbox e `worker` básico | ADR-0014, 17:X4 | G | P0.5 | core `V8`; mensagem na transação do negócio; `SKIP LOCKED` com prazo de posse; role `sigsan_worker`; usado no provisionamento e no console |
| P1.10 | Corpus de XML de referência nos dois CIs | ADR-0003 | M | P0.32 | o mesmo corpus roda no Ziip Cloud e no Ziip SAN |
| P1.11 | `Idempotency-Key` e ETag/If-Match | 17:M12 | M | P0.10 | POST repetido devolve a mesma resposta; sem `If-Match` → 428; versão velha → 412 |
| P1.12 | Frontend robusto | 17:F1, 17:M14, 17:B9–B11 | M | P0.14 | Error Boundary; code splitting; ações por papel; filtro preservado; tabelas no celular; menu modal (axe limpo); retry por tipo; Web Vitals |
| P1.13 | Fronteiras de módulos | 17:M11 | M | — | ArchUnit ou Spring Modulith no CI; duplicatas billing/metering removidas |
| P1.14 | Rollout de migrations por tenant | 17:D1, ADR-0005 | M | P0.6, P0.21 | status por tenant; falha em quarentena sem parar os demais; paralelismo limitado; canário |
| P1.15 | Baixos de dado pessoal | 17:B5, 17:B6 | P | — | VIEWER não vê excluídos; `consentId` conferido contra o titular |
| P1.16 | Arredondamento e tributo na conta | FISCAL-PORTE-JAVA | P | P0.32 | regra única registrada em ADR; vetores C# e Java iguais; CHECK de `bills` definido para `V4_6` |
| P1.17 | Central Ziip v0, junto do console v0 | ADR-0018 | G | P1.6, P1.9, P0.8 | core `V9_1`; coletor de 5 min com trava e watchdog; abas Visão geral, Plataforma, Segurança e Alertas; alerta abre e fecha sem duplicar; `SELECT` de `ziipsan_central` em tabela de tenant falha no teste; cada função `platform_stats.*` com teste de ausência de coluna pessoal; API de métricas `v1` com catálogo e teste de contrato |
| P1.18 | Preferência de tema no servidor; padrão e restrição do tenant | ADR-0017 | M | P0.36, P1.6 | tenant `V1_7`; `GET/PUT /api/v1/me/preferences`; precedência usuário → tenant → Azul Água no modo do sistema (teste); padrão retirado da lista do tenant cai no padrão dele sem apagar a escolha |
| P1.19 | Marca do tenant | ADR-0016 | M | P0.34, P1.6, P1.20 | core `V9_2`; chaves `branding.*`; logo PNG/WebP reencodado e varrido (EICAR e arquivo poliglota recusados); `GET /api/v1/public/tenant-brand` pelo subdomínio, com slug desconhecido devolvendo a marca do produto e o mesmo status; regra de contraste da cor aplicada ao salvar |
| P1.20 | Pipeline de arquivos enviados com ClamAV | ADR-0010 (Exceções), ADR-0020 | M | P1.9 | tipo pelo conteúdo; arquivo em quarentena até a varredura; EICAR bloqueado e com alerta; PDF limpo liberado; idade das assinaturas monitorada; varredor atrás de interface substituível pelo serviço da hospedagem |
| P1.21 | Ajuda: conteúdo, publicação e ajuda contextual | ADR-0020 | M | P0.3, P0.6 | core `V9_3`; `content/help` validado no CI (cabeçalho, links, rotas, alvos `data-tour` existentes); publicação pelo `migrate` sem nova imagem; busca em português; `useContextualHelp` e `<Hint>` |
| P1.22 | Catálogo de erros explicados | ADR-0020 | P | P1.21 | teste falha se um código `urn:ziipsan:problema:*` não tiver explicação |
| P1.23 | Chamados v0 e fila na Central | ADR-0020 | G | P1.9, P1.17, P1.20 | core `V9_4`; protocolo `SUP-AAAA-NNNNNN`; conversa, estados e notificações pela outbox; usuário de outro tenant recebe 404; e-mail sem conteúdo nem anexo; formulário orienta a não anexar dado pessoal e marca `contains_personal_data` |
| P1.24 | Sobre completo e novidades por usuário | ADR-0021 | M | P0.34, P0.41, P0.42 | core `V9_5`; Sobre com marca, versão, documentos com versão vigente e aceita (`platform.document_acceptance`) e licenças; novidades uma vez por versão por usuário, com estado no servidor |

### P2 — Ciclo de faturamento · 01/02–26/03/2027

Objetivo: ciclo completo em homologação com NFAg-e, dentro das metas de carga; cobrança por contrato e área do
tenant prontas antes do primeiro contrato.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P2.1 | Fundação de lote: Spring Batch por tenant e rota, `job_runs`, trava (tenant, ciclo), fila justa | 17:X4, ADR-0014 | G | P1.9 | core `V10`; reinício continua só rotas pendentes; disparo duplo não corre; tenant grande não atrasa o pequeno além do RNF |
| P2.2 | GUC por transação; pools por papel | 17:D3, ADR-0005 | M | P2.1 | `set_config(..., true)`; isolamento com PgBouncer em modo transaction; pools API, lote e ingestão; base para schema compartilhado |
| P2.3 | Tabelas de fato particionadas; auditoria por lote | 17:M8, 17:X3, 17:B1, 17:B2 | G | P1.7 | `V4_6`, `V5_4`; 100 mil contas auditadas por `run_id`; leitura reenviada não duplica |
| P2.4 | R4a: CBS/IBS com vigência | R4a (v1) | P | P1.8, P1.16 | alíquota ausente → erro; vigências sem sobreposição |
| P2.5 | R4: medição, ciclo, conta, N6 | R4 (v1), G5, G6 | G | P2.1, P0.20 | `V4_3`, `V4_4`; casos da v1 (1000→1027 com E-01 → 201,60; ciclo repetido sem segunda conta); conta grava as versões de parâmetro |
| P2.6 | NFAg-e (modelo 75) sobre o motor portado | ADR-0003, 17:P4 | G | P1.8, P2.5 | `V4_7`; autorizada na homologação da SEFAZ do piloto; ciclo reexecutado sem segunda NFAg-e |
| P2.7 | Leitura terceirizada | 17:P3 | M | P2.5 | `V5_3`; arquivo do fornecedor do piloto; reenvio não duplica |
| P2.8 | Arquivo para gráfica | 17:P4 | M | P2.5 | leiaute aprovado com amostra impressa; conta traz o código de vinculação ao portal do imóvel (ADR-0022), que não aparece em log nem em arquivo intermediário fora do da gráfica |
| P2.9 | Carga com 300 mil ligações | 17:O4, ADR-0012 | M | P2.5 | 100 mil contas em < 2 h sem estourar o p95 da API |
| P2.10 | Importador v1 com 120 meses | 17:X5 | G | P1.2 | `V14_2`; saldo por matrícula igual ao legado |
| P2.11 | Central: abas Faturamento e Fiscal | ADR-0018 | M | P1.17, P2.1, P2.6 | ciclo atrasado e fila fiscal acima do RNF-08 disparam alerta em homologação |
| P2.12 | Cobrança do SaaS por contrato, antes do primeiro contrato | ADR-0019 | G | P1.6, P1.17 | core `V10_1`; contrato com aditivos, empenhos, reajuste (só proposta até o apostilamento), faturas e pagamentos conciliados; alertas do item 7; restrição manual por uma pessoa com MFA, step-up e justificativa, executada por `PLATFORM_FINANCE` ou `PLATFORM_OWNER`, com motivo inadimplência contratual ligado a fatura vencida e ao aviso prévio registrados (sem eles, recusada); teste prova que nenhum caminho de código bloqueia tenant sozinho |
| P2.13 | NFS-e da venda pelo motor portado | ADR-0019, ADR-0003 | M | P1.8, P2.12 | contexto fiscal da plataforma definido; fatura gera NFS-e na produção restrita |
| P2.14 | Área do tenant: contrato com o Ziip | ADR-0022 | M | P2.12, P2.13 | tenant vê faturas e pagamentos do próprio contrato e baixa a fatura paga e a NFS-e em PDF e XML; outro tenant → 404; XML idêntico ao autorizado (hash conferido); acesso auditado |
| P2.15 | SLA de suporte por contrato e CSAT | ADR-0020 | M | P1.23, P2.12 | core `V10_2`; política referenciada no contrato; prazo bate com casos de feriado municipal e pausa; alerta a 80% e no estouro; relatório mensal de FRT, MTTR, SLA e CSAT na Central |
| P2.16 | Rejeições fiscais no catálogo de erros; novidades filtradas por tenant | ADR-0020, ADR-0021 | P | P1.22, P1.24, P2.6 | todo código de rejeição da NFAg-e (leiaute vigente) e da NFS-e com explicação (teste); novidade cujo `affects` não inclui o tenant não aparece |
| P2.17 | Marca do tenant e metadados nos PDFs | ADR-0016 | P | P1.19, P2.5 | conta e documentos com nome, logo e rodapé do tenant e metadados do `BrandProvider`; regras de contraste de MARCA-E-TEMAS §3.4 |
| P2.18 | XML autorizado em armazenamento com retenção imutável | ADR-0003, ADR-0022 | P | P2.6, P0.27 | cópia com bloqueio de retenção e hash na `HashChain`; apagar ou sobrescrever é recusado; leitura confere o SHA-256 |
| P2.19 | Importador: documentos anteriores ao go-live | ADR-0022, 17:X5 | M | P2.10, P2.18 | `V14_3`; importa os XML autorizados que o piloto entregar (sistema antigo ou emissor usado no intervalo), recusando os de assinatura inválida, sem protocolo de autorização, de outro emitente ou repetidos, com relatório por lote; aceitos vão para o armazenamento imutável da P2.18 com o hash e a origem (`LEGACY_IMPORT`), sem alteração do arquivo; o PDF das contas antigas sai sob demanda dos dados importados pela P2.10, com a indicação de que foi gerado a partir do histórico importado; contagem por competência conferida com o legado |

### P3 — Pronto para operar · 29/03–28/05/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P3.1 | R5a: retorno e conciliação N3 | R5 (v1), G4 | G | P2.5 | `V6_2`; retorno idempotente (NSA + hash); aprovação dupla |
| P3.2 | R5b: Pix Cobrança e boleto híbrido | R5 (v1), 17:P4 | G | P3.1 | `V6_3`; webhook assinado; tenant do convênio; 200 em < 2 s após gravar na `pix_inbox` |
| P3.3 | Portal do cliente: 2ª via, Pix, protocolo | 17:P2, ADR-0022 | G | P3.2, P0.14 | realm `ziipsan-portal` com conta própria do consumidor (e-mail ou celular e senha, MFA opcional) e client confidencial do BFF; token do realm do portal recusado nas rotas do backoffice e token do backoffice recusado nas do portal (teste); sem acesso ao backoffice; disponibilidade medida contra 99,9%; histórico, documentos e vínculo na P3.20 |
| P3.4 | R7 mínimo: protocolo NR 11, OS com bloqueio de corte | R7 (v1), G9 | G | P2.5 | `V8_1`, `V9_1`; casos da v1 (aviso em 15/10 → 16/11; 19/11 e 20/11 bloqueados; ADASA; sem prova, religação sem taxa); vedações para todos os motivos; emergência exige justificativa e foto |
| P3.5 | Notificações com prova de entrega | 17:P4 | M | P1.9 | aviso de corte sem prova bloqueia a OS |
| P3.6 | NFS-e de serviços avulsos | ADR-0003 | M | P1.8 | serviço da OS gera NFS-e na produção restrita |
| P3.7 | Homologação fiscal com certificado do piloto | ADR-0003 | M | P2.6 | contingência e restauração do XML autorizado testadas |
| P3.8 | Pentest: isolamento, cofre, portal do consumidor, acesso assistido e de emergência | 17 §7, ADR-0020, ADR-0022 | M | P0.27 | nenhum achado crítico ou alto aberto; vínculo, enumeração de matrícula e de código de vinculação, troca de token entre realms e download do portal cobertos |
| P3.9 | Contrato SaaS, DPA, termos, SLA, suboperadores | ADR-0002, ADR-0019, ADR-0020 | M | P2.12, P2.14, P2.15 | assinados; Ziip SAN operador, cliente controlador; cláusulas de aviso, restrição só manual e saída; política de SLA de suporte; retenção de chamados pela vigência mais 5 anos e a exceção no certificado de descarte previstas no DPA |
| P3.10 | Resposta a incidente; modelo de RIPD | 17 §7 | P | — | prazos ao controlador e à ANPD; simulação de mesa feita |
| P3.11 | Operação: runbook, plantão, status, error tracking, burn rate | 17:O1 | M | P0.8, P0.27 | falha provocada dispara alerta; runbook exercitado |
| P3.12 | Fim do `outOfOrder` | 17:D1, G10a | P | P1.14 | sequência global a partir de `V100`; CI recusa versão ≤ maior publicada |
| P3.13 | Plano de paralelo e de corte | ADR-0001 | P | P0.30 | critérios e plano de volta aprovados pelo piloto |
| P3.14 | Spring Boot na linha com suporte | D4 | M | — | atualizado antes de 31/07/2027 |
| P3.15 | Central Ziip completa | ADR-0018 | G | P2.11, P3.1, P3.2, P3.11 | abas Arrecadação, Pix, LGPD, Regulatório, Infraestrutura com SLO, Radar e Suporte; falha provocada alerta; kit de evidências com hash na trilha |
| P3.16 | Ensaio de saída de tenant sintético | ADR-0018, ADR-0015, RNF-42 | M | P1.6, P0.28 | exportação em até 15 dias; carência de 30 dias contada do fim do contrato, com download da exportação e contestação registradas; eliminação agendada só por `PLATFORM_OWNER` com MFA e cancelável durante a carência; execução antes do fim da carência recusada (teste); crypto-shredding; eliminação concluída e certificado emitido em até 30 dias após o fim da carência; certificado com hashes, expiração de backups e de cópias imutáveis e a exceção dos chamados |
| P3.17 | Acesso assistido com consentimento e acesso de emergência | ADR-0020 | M | P1.23, P0.43, P3.10 | core `V10_3`; sem consentimento → negado e auditado; revogação encerra a sessão em até 1 min; revelar documento e exportar bloqueados; cada requisição no `audit_log` do tenant com o id do consentimento. Emergência: sem incidente de segurança declarado, sem `PLATFORM_OWNER` ou sem reautenticação com MFA no momento → negado e auditado; qualquer escrita → 403; aviso ao tenant enviado pela outbox em até 24 h (teste com relógio); cada requisição no `audit_log` do tenant e em `audit_event` com o id do incidente, e a sessão no registro do incidente |
| P3.18 | Página pública de status | ADR-0020, RNF-54 | M | P0.27, P3.11 | core `V10_4`; hospedagem separada da produção; status muda sozinho com a sonda fora; janela de manutenção publicada com 72 h |
| P3.19 | Retenção, expurgo e exportação de chamados | ADR-0020 | P | P1.23, P2.12 | `purge_at` = fim da vigência do contrato mais 5 anos; ensaio de expurgo com contagem; chamados na exportação de fim de contrato; RIPD do módulo atualizado; manual de atendimento |
| P3.20 | Portal do consumidor: vínculo, histórico de 120 meses e documentos | ADR-0022 | G | P3.3, P2.6, P2.8, P2.10, P2.18, P2.19 | tenant `V8_2`; vínculo pelo código de vinculação da conta mais o CPF/CNPJ do titular, comparado pelo índice cego; resposta igual para matrícula inexistente, código errado e documento errado; tentativas limitadas por conta, IP e tenant, com bloqueio progressivo, registro e alerta de volume (ADR-0008); uma conta com vários imóveis; troca de titularidade revoga o vínculo e invalida o código; contas e pagamentos de 120 meses numa só lista, com a origem de cada documento (emitido pelo Ziip SAN, importado do sistema anterior ou do emissor do intervalo); conta paga com quitação, DANFAG em PDF e XML original; consumidor só vê matrículas vinculadas (teste com dois titulares e troca de titularidade); XML com hash igual ao gravado; todo download no `personal_data_access_log`; excesso → 429; PDF com marca do tenant e estrutura marcada; axe sem violação `serious` ou `critical` |
| P3.21 | Marca do tenant no portal e nos e-mails | ADR-0016 | P | P1.19, P3.3, P3.5 | portal e e-mails com nome, logo e cor do tenant; atribuição "operado com" conforme o pacote |

### Go-live piloto · 01/06–30/07/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| GL.1 | Carga final do legado | 17:X5 | M | P2.10, P2.19 | conciliação assinada pelo piloto; XML autorizados até o corte importados pela P2.19, com o relatório de recusas aceito pelo piloto |
| GL.2 | Dois ou três ciclos de faturamento paralelo, conta a conta | ADR-0001 | G | GL.1 | divergência dentro do critério da P0.30, toda explicada; sem emissão fiscal de produção |
| GL.3 | Restauração da produção | 17:X2 | P | P0.28 | dentro do RTO, antes do corte |
| GL.4 | Corte do legado | ADR-0001 | P | GL.2, GL.3 | decisão formal; emissão fiscal ligada com aprovação dupla |

### P4 · 02/08–17/12/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P4.1 | R8: cobrança, sem negativação | R8 (v1) | G | P3.4 | parcelamento com demonstrativo; contestação bloqueia corte |
| P4.2 | R6: SINISA assistido | R6 (v1), ADR-0004 | M | P2.5 | arquivo oficial; valor manual com justificativa; casos da v1 (água não faturada 3.000 m³; ILI) |
| P4.3 | BI e prestação de contas em réplica | 17 §5 | M | P0.28 | relatórios fora do primário |
| P4.4 | App de leitura offline (PWA) | 17:P3 | G | P2.3 | sincronização com chave de idempotência |
| P4.5 | R5c: Pix Automático | R5 (v1) | M | P3.2 | cobrança recorrente no PSP do piloto |
| P4.6 | Schema compartilhado e banco dedicado | ADR-0005 | G | P1.14, P2.2 | só com demanda; mesmo teste de isolamento |
| P4.7 | Restauração mensal automatizada | ADR-0012 | P | P0.28 | relatório mensal |
| P4.8 | Suporte: resposta por e-mail, deflexão, assinatura de status, notas do tenant | ADR-0020 | G | P3.17, P3.18 | resposta por e-mail entra no chamado certo só de remetente verificado e sem anexo não varrido; deflexão cita artigo e transfere para humano; assinatura de status por e-mail |

O R6 fica depois do alvo de 30/04/2027 da v1 porque, sem cliente em produção antes de junho de 2027, o primeiro envio
assistido com dado do Ziip SAN é o do ano-base 2027; até lá o piloto envia pelo processo atual.

**2028, sob demanda:** trilha GRS (D-MTR-1..7, DMR e SINIR assistidos, lista IBAMA, NBR 10004); R9 (incidente, N5, N1,
N7, GIS); negativação; ISO 27001; instância dedicada; qualidade da água (Portaria 888).

**Melhorias contínuas.** Os baixos 17:B1–B11 já estão distribuídos (P0.5, P0.7, P0.13, P0.17, P1.12, P1.15, P2.3);
baixos de revisões futuras entram nesta lista.

---

## 5. Caminho crítico e marcos

Caminho crítico: hospedagem (P0.26) → ambientes, PITR e R1 (P0.27, P0.28) → importador com dado real (P1.2) → outbox
(P1.9) → lote (P2.1) → ciclo (P2.5) → NFAg-e (P2.6) → arrecadação (P3.1, P3.2) → paralelo (GL.2) → corte (GL.4). Ramo
igualmente crítico: piloto (P0.30) → discovery (P1.1) → importadores → carga final (GL.1). Não há folga: atraso na
hospedagem ou no piloto desloca o go-live na mesma medida.

As tarefas da versão 2.1 não mudam esse caminho, com três ressalvas: a renomeação (P0.33) vem logo depois da P0.1 e
antes de toda tarefa de código do P0, em paralelo com a decisão de hospedagem; cobrança por contrato, NFS-e da venda e
área do tenant (P2.12–P2.14) precedem a assinatura do contrato do piloto (P3.9); e o histórico com documentos no
portal (P3.20) depende da NFAg-e (P2.6), da cópia imutável do XML (P2.18), que depende da hospedagem (P0.27), e da
importação dos documentos anteriores ao go-live (P2.19). As decisões da versão 2.2 não alteram o caminho.

| Marco | Data | Critério |
|---|---|---|
| M1 | 23/10/2026 | clientes-alvo comunicados |
| M2 | 30/10/2026 | hospedagem decidida |
| M3 | 13/11/2026 | piloto escolhido, com acordo de tratamento de dados |
| M4 | 27/11/2026 | fim do P0: ambientes no ar, PITR testado, R1 |
| M5 | 11/12/2026 | R3 em homologação |
| M6 | 29/01/2027 | fim do P1: cadastro do piloto importado e conciliado |
| M7 | 26/03/2027 | fim do P2: ciclo e NFAg-e em homologação |
| M8 | 28/05/2027 | fim do P3: pronto para operar |
| M9 | 01/06/2027 | início do paralelo |
| M10 | 30/07/2027 | go-live: legado desligado |

**Prazos legais do prestador.** Até o corte, o piloto cumpre pelo sistema atual: NFAg-e 01/12/2026, a confirmar (Ziip SAN
no P2, emite após o corte); tarifa social 11/12/2026 (P1, satélite); CBS/IBS 01/01/2027 (P2); boleto híbrido 01/02/2027,
facultativo (P3); NR 11 em 20/05/2027 e Lei 14.015, em vigor (P3); SINISA anual (P4); DMR trimestral (2028).

---

## 6. Riscos

| Risco | Dono | Mitigação |
|---|---|---|
| Capacidade: uma pessoa com IA | Daniel | uma fase aberta por vez; infra congelada; tarefa bloqueada não libera antecipação de funcionalidade; revisão de escopo ao fim de cada fase; P4 cede antes do go-live |
| Duplicação do motor fiscal C# × Java; o C# não é referência validada | Daniel | corpus comum nos dois CIs (P1.10) conferido contra o XSD oficial, não só contra o C#; tabela de versões de NT; monitor regulatório único; reavaliar em 2028 qual motor permanece |
| NFAg-e sem manual e XSD oficiais; obrigatória em 01/12/2026 (a confirmar) e go-live só em jun/2027 | Daniel | o prestador precisa de outro emissor nesse intervalo, o que consta da comunicação de 23/10; builder gerado do XSD, vetores por versão |
| Arredondamento divergente (half-even × `HALF_DOWN`) e CHECK de `bills` com tributo como retenção | técnico | P1.16 antes do P2 |
| Hospedagem indefinida | Daniel | data-limite 30/10; imagem e IaC portáveis; sem decisão, P0 fecha sem P0.27–P0.29 e o P1 só usa dado sintético |
| Piloto indefinido | Daniel | data-limite 13/11; critérios do ADR-0001; importador sobre leiaute genérico e massa sintética |
| Prazos legais do prestador antes do go-live | Daniel | contrato explicita que as obrigações seguem no legado até o corte; nenhuma data regulatória prometida antes do go-live |
| Multa acima de 2% aceita pelo tenant (CDC art. 52 §1º) | Daniel | aceite formal com responsável e justificativa; contrato aloca a responsabilidade ao controlador |
| Limite legal errado no catálogo | Daniel | norma citada e teste por limite; revisão quando a norma muda |
| Schema por tenant no limite | técnico | 300 schemas (P0.21); teto por cluster; shard |
| Dado real antes do R1 ou do acordo de tratamento | técnico | `dev-env` recusado em `prod`; importador exige cofre ativo e acordo registrado |
| Homologação em terceiros (SEFAZ, PSP, banco, gráfica) | Daniel | credenciamento a partir do P1; simuladores |
| Fim de suporte (Spring Boot 4.1 em 31/07/2027; PostgreSQL 15 em nov/2027) | técnico | P3.14; P0.4 |
| Escopo da versão 2.1 (marca, temas, Central, cobrança, suporte, portais) sobre a mesma capacidade | Daniel | Central e suporte crescem por fase (v0 no P1); P4 cede antes do go-live; tarefas fora do caminho crítico escorregam primeiro |
| Renomeação em massa (P0.33) quebra build ou cifra | técnico | tag `pre-rename`; nenhum branch aberto; commit só de `git mv` separado do de conteúdo; teste de vetor fixo da cifra; busca residual no CI |
| Retenção longa de chamados com dado pessoal (vigência mais 5 anos) | Daniel | minimização no formulário, acesso restrito e registrado, expurgo com contagem, previsão no DPA |
| XML do intervalo antes do go-live não entregues ou incompletos | Daniel | pedido formal ao piloto na P1.1; relatório de recusas da P2.19; o portal mostra a origem de cada documento e não inventa o que falta |
| Acesso de emergência usado fora de incidente real | Daniel | só `PLATFORM_OWNER`, incidente declarado e registrado, reautenticação, somente leitura, aviso ao tenant em 24 h e revisão de cada uso na simulação de incidente (P3.10) |

---

## 7. Resumo

| Fase | Período | Entregas principais | Bloqueios |
|---|---|---|---|
| P0 | 13/10–27/11/2026 | dev fora do Drive, renomeação `ziipsan`, CI, Dockerfile, versão pela tag e changelog, PG 17, segurança, BFF, MFA, papéis de plataforma, pacote de marca, temas, parâmetros, 300 schemas, carga inicial; ambientes, PITR, R1 | hospedagem; piloto |
| P1 | 30/11/2026–29/01/2027 | importador v0, R2, R3, console e Central v0, marca do tenant, preferência de tema, ClamAV, ajuda e chamados v0, Sobre, partição do `audit_log`, outbox, porte fiscal | piloto; R1 |
| P2 | 01/02–26/03/2027 | lote, R4, R4a, NFAg-e e XML imutável, leitura terceirizada, gráfica, carga, importador v1 e documentos anteriores ao go-live; cobrança por contrato, NFS-e da venda e área do tenant; SLA de suporte; Central de faturamento e fiscal | P1.9, P1.16; SEFAZ |
| P3 | 29/03–28/05/2027 | R5a, R5b, portal com histórico e documentos, R7 mínimo, NFS-e avulsa, pentest, contrato, operação; Central completa, acesso assistido e de emergência, página de status, ensaio de saída | escopo de G4; PSP |
| Go-live | 01/06–30/07/2027 | paralelo e corte | aceite do piloto |
| P4 | 02/08–17/12/2027 | R8, R6, BI, app offline, R5c, isolamento por porte, suporte por e-mail | demanda |
| 2028 | — | GRS, R9, ISO 27001, instância dedicada | demanda |

---

## 8. Governança

- **Release.** `main` protegida; PR com CI verde; versão semântica gerada da tag (ADR-0021), com notas de versão e item
  obrigatório no changelog do cliente para segurança, legislação e parâmetro regulatório; homologação antes de
  produção, com aprovação manual; `migrate` antes do rollout; rollback da imagem testado; janela de manutenção avisada
  com a antecedência do SLA.
- **Ambientes.** Local (sintético), CI (banco efêmero), homologação (sintético ou anonimizado) e produção, a única com
  dado real e com cofre obrigatório. Segredos só no cofre.
- **API.** `/api/v1`; OpenAPI no repositório, conferido no CI; mudança aditiva na mesma versão; incompatível vai para
  `/api/v2`, com convivência mínima de seis meses.
- **Feature flags por tenant.** No console (core `V9`); módulo contratado é flag de plano; flag de rollout tem dono e
  data de remoção; mudança vai para a auditoria.
- **ADRs.** Regras em `arquitetura/adr/README.md`; decisão nova só por ADR, que este plano cita sem repetir.
- **Idioma (decisão do dono do produto, 12/10/2026).** Tabelas, colunas, schemas, valores de enumeração gravados,
  rotas da API e do frontend, cabeçalhos HTTP, propriedades, permissões, chaves de configuração e de arquivos de
  conteúdo, pastas e identificadores de código em inglês, como o código atual (`consumer_units`, `'ACTIVE'`,
  `/api/registry/consumer-units`); textos exibidos ao usuário, conteúdo de ajuda, mensagens e documentação em português. Rotas
  novas: `/help`, `/support`, `/about`; `/api/v1/public/status` se mantém. Exceções: o prefixo
  `urn:ziipsan:problema:` e os códigos de problema já existentes ou novos (`versao-desatualizada`, `mfa_obrigatorio`),
  contrato congelado da API (MARCA-E-TEMAS §5.5); o formato do protocolo exibido ao usuário (`SUP-AAAA-NNNNNN`);
  nomes de campos de leiautes oficiais (`tpAmb`, `cStat`) e de código de terceiros citado (C# do Ziip Cloud). Em
  revisão de PR e de documento, nome novo em português é corrigido antes do merge.
- **Diário.** `docs/DIARIO-DE-BORDO.md` ao fim de cada tarefa: o que foi feito, testes, pendências, commit.
- **Push automático (ADR-0009).** Fim de etapa com suíte verde → commit e push; vermelho não publica.
- **Aceite de fase.** Todos os critérios cumpridos e registrados; tarefa que escorregar recebe nova data aqui e no diário.
