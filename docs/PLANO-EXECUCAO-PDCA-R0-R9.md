# Plano de execução do Ziip SAN: fases P0–P4 e go-live piloto

**Versão 2, 12/10/2026.** Produto Ziip SAN; nome interno do código, SIGSAN. O nome do arquivo foi mantido por causa dos
links. Este é o plano canônico; em divergência com um ADR, vale o ADR mais recente.

**O que mudou em relação à v1**

- Roadmap oficial P0–P4 com marco de go-live piloto; R1–R9 vira conteúdo das fases (ADR-0001).
- G1, G2, G4, G5, G6 e G9 viram parâmetros do tenant com aceite formal e deixam de bloquear fases (ADR-0002); G3, G7 e
  G8 foram decididas pelo produto (ADR-0003, ADR-0004).
- Entram a camada de serviço operado (artefato, ambientes, backup, lote, observabilidade, carga), a migração do legado
  e todos os achados críticos, altos e médios da revisão 17.
- `outOfOrder(true)` ganha prazo; depois dele, versões Flyway sempre crescentes (§3).
- NFAg-e sobre motor fiscal próprio, portado do Ziip Cloud, no lugar do integrador.
- BFF com cookie, MFA por tenant, máscara configurável, push automático, desenvolvimento fora do Google Drive.

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
| D5/D6 | Realm único, claim `tenant_id` (UUID) | Δ mais gestão de usuários que fixa o `tenant_id` (17:S3) |
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

| Decisão | Dono | Até | Bloqueia |
|---|---|---|---|
| Hospedagem (ADR-0011) | Daniel | 30/10/2026 | P0.27–P0.29; P1.2 com dado real |
| Cliente piloto, acesso ao legado, acordo de tratamento de dados mínimo | Daniel | 13/11/2026 | P1.1, P1.2, go-live |
| G4 no produto: leiautes (FEBRABAN 150, CNAB 240) e PSPs suportados no go-live | Daniel | 29/01/2027 | P3.1, P3.2 |
| NFAg-e: obrigatoriedade e data para o perfil do piloto, no DOU; manual e XSD oficiais | Daniel | 27/11/2026 | P2.6 |
| Arredondamento (half-even no C#, `HALF_DOWN` no billing) e tributo no CHECK de total de `bills` (hoje tratado como retenção) | técnico | 29/01/2027 | P2.4, P2.5 |

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
| core `V6` | core | `tenant_key` (DEK por tenant). Era "core `V3`" no R1 da v1: o R1 depende da hospedagem e o core não aceita fora de ordem | P0.28 |
| core `V7` | core | `audit_log` particionado por mês, PK `(created_at, id)` com UUIDv7, retenção | P1.7 |
| core `V8` | core | `outbox` com `tenant_id`, RLS e partição diária | P1.9 |
| core `V9` | core | planos, cotas, módulos e flags por tenant; implantação; impersonação | P1.6 |
| core `V10` | core | `job_runs` e travas por (tenant, ciclo) | P2.1 |
| `V1_5` | platform | `parameter_versions` (vigência com EXCLUDE, quem propõe ≠ quem aprova), `parameter_acceptances`, `regulatory_profiles`, `holidays` | P0.19 |
| `V1_6` | platform | `personal_data_access_log`; cota de busca por documento | P0.11 |
| `V3_4` | registry | GIN trigram sobre `unaccent(lower(name))` | P0.22 |
| `V3_5` | registry | sequência de matrícula por tenant | P1.4 |
| `V3_6` | registry | R2: `customer_types`, `property_profiles`, endereço, `block_faces`, rascunhos | P1.3 |
| `V4_6` | billing | partição de `bills`/`bill_items`; auditoria por lote; CHECK de total com tributo | P2.3, P2.5 |
| `V4_7` | `sigsan-fiscal` | NFAg-e e NFS-e avulsa: leiaute, contingência, inutilização | P2.6, P3.6 |
| `V5_3` / `V5_4` | metering | tarefas e importação de leitura / partição de `meter_readings`, chave de idempotência | P2.7 / P2.3 |
| `V6_3` | collection | partição de `payments`; `pix_inbox` | P3.2 |
| `V14_1`, `V14_2` | importação de legado | lotes, staging, conciliação; histórico de 120 meses | P1.2, P2.10 |

`current_tenant_id()` como SQL STABLE (17:M10) não é migration: a função é de `sigsan_owner` e nasce no init de
segurança (`infra/postgres/init/sql/security.sql`); o migrador não pode redefini-la, por desenho. Nos bancos existentes,
script de operação como `postgres`.

---

## 4. Fases

Tamanho: **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa com agentes. Toda tarefa termina
com testes verdes, registro no diário e push.

### P0 — Plataforma operável · 13/10–27/11/2026

Objetivo: ambiente fora do Drive, artefato implantável, altos e médios de segurança, parâmetros do tenant e, se a
hospedagem for decidida a tempo, homologação e produção com PITR e R1.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P0.1 | Desenvolvimento em disco local; GitHub como fonte da verdade | ADR-0009, 17:M15 | M | — | build e `ci.sh` verdes num clone fora do Drive, sem robocopy nem PowerShell; `.env` fora do Drive |
| P0.2 | Push automático ao fim de etapa | ADR-0009 | P | P0.1 | com teste vermelho nada é publicado |
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
| P0.23 | Carga inicial | 17:O4, ADR-0012 | M | P0.8 | k6 ou Gatling noturno, 20 mil ligações sintéticas; p95 contra o RNF; regressão > 20% falha |
| P0.24 | Modelagem de ameaças (STRIDE) | 17 §7 | P | P0.14 | API, BFF, worker, webhook e importador com controle ou tarefa |
| P0.25 | Dívidas da execução; SINIR em modo assistido | diário §18, ADR-0004 | P | — | sem `ObjectMapper` do Jackson 2 em collection; `SinirIntegrationService` removido ou desligado sem caminho de chamada; `grantConsent` sem versão manual |
| P0.26 | Decisão de hospedagem | ADR-0011 | P | — | ADR-0011 aceito: provedor, região brasileira, banco, cofre, custo |
| P0.27 | Homologação e produção com IaC | 17:X1, 17:O2 | G | P0.26 | sem segredo no state; deploy pelo pipeline; produção com aprovação manual; rollback exercitado |
| P0.28 | HA, backup e PITR; R1 (cofre e chaves) | 17:X2, R1 | G | P0.26 | PITR com RPO ≤ 15 min e RTO ≤ 4 h medidos; core `V6`; `TenantKeyProviderContract` verde no cofre; rotação; backup da KEK |
| P0.29 | Keycloak de produção | 17:O3 | M | P0.27 | `start --optimized`, TLS, `sslRequired=all`, 2 réplicas |
| P0.30 | Escolha do piloto | ADR-0001 | P | — | termo de intenção, acesso ao legado, acordo de tratamento de dados e critério de divergência do go-live |
| P0.31 | Comunicação aos clientes-alvo | G0 (v1) | P | — | até 23/10/2026: o que entra em cada fase e o que segue no legado |
| P0.32 | Esqueleto fiscal | ADR-0003 | M | — | `commercial/sigsan-fiscal`; vetores do C# passam no Java; sem `producao-permitida` não há `tpAmb=1` |

P0.27–P0.29 só começam depois da P0.26. A regra pura do R3 pode começar no P0, em paralelo.

### P1 — Migração e satélite · 30/11/2026–29/01/2027

Objetivo: cadastro do piloto importado e conciliado; R2, R3, console v0 e outbox.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P1.1 | Discovery do legado | 17:X5 | M | P0.30 | dicionário, mapeamento campo a campo e volumes aprovados pelo piloto |
| P1.2 | Importador v0 do cadastro com conciliação | 17:X5 | G | P1.1, P1.4, P0.28 | `V14_1`; reimportação não duplica; totais por setor e rota iguais ao legado; dado real só em produção |
| P1.3 | R2: cadastro completo | R2 (v1) | G | — | `V3_6`; cliente cadastrado pela tela; rascunho e `validate-step`; CEP; CSV |
| P1.4 | Matrícula por sequência | 17:M9 | P | — | `V3_5`; dois lotes paralelos sem espera, sem lacuna nem duplicata |
| P1.5 | R3: tarifa social satélite + N4 | R3 (v1), G1, G2 | M | P0.20 | `V4_2`; casos da v1 (T-03 → 102,00 e 87,00; CadÚnico de 25 meses inelegível; `GRACE_3M`); benefício abaixo do piso federal recusado; em homologação até 11/12/2026 |
| P1.6 | Console da plataforma v0 | 17:P1, ADR-0002 | G | P0.13, P0.19, P1.9 | core `V9`; assistente de implantação; produção bloqueada sem parâmetros aceitos; planos, cotas, flags; suspensão; exportação e eliminação com certificado de descarte; impersonação com MFA e auditoria |
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

### P2 — Ciclo de faturamento · 01/02–26/03/2027

Objetivo: ciclo completo em homologação com NFAg-e, dentro das metas de carga.

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P2.1 | Fundação de lote: Spring Batch por tenant e rota, `job_runs`, trava (tenant, ciclo), fila justa | 17:X4, ADR-0014 | G | P1.9 | core `V10`; reinício continua só rotas pendentes; disparo duplo não corre; tenant grande não atrasa o pequeno além do RNF |
| P2.2 | GUC por transação; pools por papel | 17:D3, ADR-0005 | M | P2.1 | `set_config(..., true)`; isolamento com PgBouncer em modo transaction; pools API, lote e ingestão; base para schema compartilhado |
| P2.3 | Tabelas de fato particionadas; auditoria por lote | 17:M8, 17:X3, 17:B1, 17:B2 | G | P1.7 | `V4_6`, `V5_4`; 100 mil contas auditadas por `run_id`; leitura reenviada não duplica |
| P2.4 | R4a: CBS/IBS com vigência | R4a (v1) | P | P1.8, P1.16 | alíquota ausente → erro; vigências sem sobreposição |
| P2.5 | R4: medição, ciclo, conta, N6 | R4 (v1), G5, G6 | G | P2.1, P0.20 | `V4_3`, `V4_4`; casos da v1 (1000→1027 com E-01 → 201,60; ciclo repetido sem segunda conta); conta grava as versões de parâmetro |
| P2.6 | NFAg-e (modelo 75) sobre o motor portado | ADR-0003, 17:P4 | G | P1.8, P2.5 | `V4_7`; autorizada na homologação da SEFAZ do piloto; ciclo reexecutado sem segunda NFAg-e |
| P2.7 | Leitura terceirizada | 17:P3 | M | P2.5 | `V5_3`; arquivo do fornecedor do piloto; reenvio não duplica |
| P2.8 | Arquivo para gráfica | 17:P4 | M | P2.5 | leiaute aprovado com amostra impressa |
| P2.9 | Carga com 300 mil ligações | 17:O4, ADR-0012 | M | P2.5 | 100 mil contas em < 2 h sem estourar o p95 da API |
| P2.10 | Importador v1 com 120 meses | 17:X5 | G | P1.2 | `V14_2`; saldo por matrícula igual ao legado |

### P3 — Pronto para operar · 29/03–28/05/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| P3.1 | R5a: retorno e conciliação N3 | R5 (v1), G4 | G | P2.5 | `V6_2`; retorno idempotente (NSA + hash); aprovação dupla |
| P3.2 | R5b: Pix Cobrança e boleto híbrido | R5 (v1), 17:P4 | G | P3.1 | `V6_3`; webhook assinado; tenant do convênio; 200 em < 2 s após gravar na `pix_inbox` |
| P3.3 | Portal do cliente: 2ª via, Pix, protocolo | 17:P2 | G | P3.2 | sem acesso ao backoffice; disponibilidade medida contra 99,9% |
| P3.4 | R7 mínimo: protocolo NR 11, OS com bloqueio de corte | R7 (v1), G9 | G | P2.5 | `V8_1`, `V9_1`; casos da v1 (aviso em 15/10 → 16/11; 19/11 e 20/11 bloqueados; ADASA; sem prova, religação sem taxa); vedações para todos os motivos; emergência exige justificativa e foto |
| P3.5 | Notificações com prova de entrega | 17:P4 | M | P1.9 | aviso de corte sem prova bloqueia a OS |
| P3.6 | NFS-e de serviços avulsos | ADR-0003 | M | P1.8 | serviço da OS gera NFS-e na produção restrita |
| P3.7 | Homologação fiscal com certificado do piloto | ADR-0003 | M | P2.6 | contingência e restauração do XML autorizado testadas |
| P3.8 | Pentest: isolamento e cofre | 17 §7 | M | P0.27 | nenhum achado crítico ou alto aberto |
| P3.9 | Contrato SaaS, DPA, termos, SLA, suboperadores | ADR-0002 | M | — | assinados; Ziip SAN operador, cliente controlador |
| P3.10 | Resposta a incidente; modelo de RIPD | 17 §7 | P | — | prazos ao controlador e à ANPD; simulação de mesa feita |
| P3.11 | Operação: runbook, plantão, status, error tracking, burn rate | 17:O1 | M | P0.8, P0.27 | falha provocada dispara alerta; runbook exercitado |
| P3.12 | Fim do `outOfOrder` | 17:D1, G10a | P | P1.14 | sequência global a partir de `V100`; CI recusa versão ≤ maior publicada |
| P3.13 | Plano de paralelo e de corte | ADR-0001 | P | P0.30 | critérios e plano de volta aprovados pelo piloto |
| P3.14 | Spring Boot na linha com suporte | D4 | M | — | atualizado antes de 31/07/2027 |

### Go-live piloto · 01/06–30/07/2027

| # | Tarefa | Origem | Tam. | Dep. | Aceite |
|---|---|---|---|---|---|
| GL.1 | Carga final do legado | 17:X5 | M | P2.10 | conciliação assinada pelo piloto |
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

---

## 7. Resumo

| Fase | Período | Entregas principais | Bloqueios |
|---|---|---|---|
| P0 | 13/10–27/11/2026 | dev fora do Drive, CI, Dockerfile, PG 17, segurança, BFF, MFA, parâmetros, 300 schemas, carga inicial; ambientes, PITR, R1 | hospedagem; piloto |
| P1 | 30/11/2026–29/01/2027 | importador v0, R2, R3, console v0, partição do `audit_log`, outbox, porte fiscal | piloto; R1 |
| P2 | 01/02–26/03/2027 | lote, R4, R4a, NFAg-e, leitura terceirizada, gráfica, carga, importador v1 | P1.9, P1.16; SEFAZ |
| P3 | 29/03–28/05/2027 | R5a, R5b, portal, R7 mínimo, NFS-e avulsa, pentest, contrato, operação | escopo de G4; PSP |
| Go-live | 01/06–30/07/2027 | paralelo e corte | aceite do piloto |
| P4 | 02/08–17/12/2027 | R8, R6, BI, app offline, R5c, isolamento por porte | demanda |
| 2028 | — | GRS, R9, ISO 27001, instância dedicada | demanda |

---

## 8. Governança

- **Release.** `main` protegida; PR com CI verde; versão semântica com tag e notas de versão; homologação antes de
  produção, com aprovação manual; `migrate` antes do rollout; rollback da imagem testado; janela de manutenção avisada
  com a antecedência do SLA.
- **Ambientes.** Local (sintético), CI (banco efêmero), homologação (sintético ou anonimizado) e produção, a única com
  dado real e com cofre obrigatório. Segredos só no cofre.
- **API.** `/api/v1`; OpenAPI no repositório, conferido no CI; mudança aditiva na mesma versão; incompatível vai para
  `/api/v2`, com convivência mínima de seis meses.
- **Feature flags por tenant.** No console (core `V9`); módulo contratado é flag de plano; flag de rollout tem dono e
  data de remoção; mudança vai para a auditoria.
- **ADRs.** Regras em `arquitetura/adr/README.md`; decisão nova só por ADR, que este plano cita sem repetir.
- **Diário.** `docs/DIARIO-DE-BORDO.md` ao fim de cada tarefa: o que foi feito, testes, pendências, commit.
- **Push automático (ADR-0009).** Fim de etapa com suíte verde → commit e push; vermelho não publica.
- **Aceite de fase.** Todos os critérios cumpridos e registrados; tarefa que escorregar recebe nova data aqui e no diário.
