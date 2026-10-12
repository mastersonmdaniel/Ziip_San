# Plano de execução: ações do PDCA + roadmap R0–R9

Data: 12/10/2026. Origem: PDCA de 12/10 (`docs/levantamento/16-PDCA-2026-10-12.md`) e roadmap R0–R9
(`docs/levantamento/15c-*` §3). Detalhado por três agentes de planejamento e consolidado aqui. Nenhum arquivo de
código foi alterado para produzir este plano.

Este documento **substitui** as ordens de prioridade do diário §1 e §12, do `00` §3, do `14` §3 e do `15c` §3
(ação 7.1 aplica isso aos documentos).

---

## 1. Leitura realista dos prazos

Os prazos regulatórios são do **prestador** (tenant). Não há cliente em produção nem aplicação executável. Até
31/12/2026 o máximo entregável é **pacote de regra + decisões + exportação assistida em tenant de homologação**.

| Marco | Data | Entregável realista |
|---|---|---|
| DMR 3º tri | 31/10/2026 | **Nada de software.** MTR do SIGSAN não vale sem SINIR; a DMR é feita no SINIR |
| SINISA (ano-base 2025) | 03/11, 17/11, 31/12/2026 | **Nada de software.** A coleta fechou em 03/09/2026 (`08` §c.1); novembro é conferência. R6 mira o ciclo 2027 |
| NFAg-e | 01/12/2026 (a confirmar) | Só a decisão G3 e a parametrização. Emissor não fica pronto em 50 dias |
| Tarifa social | 11/12/2026 | **Pacote**: classificação, importador, simulador, relatório anual (R3) |
| CBS/IBS | 01/01/2027 | Só parametrização de alíquota com vigência (R4a) |
| Pix híbrido | 01/02/2027 | Facultativo e dependente do PSP; R5b |
| NR 11 ANA | 20/05/2027 | R7 (data de verificação da agência; dúvida 20 × 27/05) |
| Lei 14.015 | em vigor | O bloqueio de corte nasce com a OS (R7) |

Ação para o Daniel (G0): comunicar isso aos clientes-alvo até 23/10.

---

## 2. Decisões necessárias (portões)

### 2.1 Técnicas (bloqueiam o início da execução)

**Decididas pelo usuário em 12/10/2026:** D1 = `.git` fora do Drive (`--separate-git-dir`); D2 = GitHub privado
(push só com autorização; o MCP `github` precisa ser autorizado via `/mcp`); D4 = Spring Boot 4.1.1; D5/D6 = realm
único `sigsan` com claim `tenant_id` (UUID). As demais seguem a recomendação da tabela salvo decisão em contrário.

| # | Decisão | Recomendação |
|---|---|---|
| D1 | Onde fica o git | `git init --separate-git-dir` em `C:\Users\shyka\.sigsan-git\sigsan.git` (o Drive corrompe `.git`); no G: só o arquivo `.git` |
| D2 | Remoto | GitHub privado como backup externo; exige autorizar o MCP `github` (`/mcp`) e autorizar cada push |
| D3 | Autor dos commits | nome + e-mail do usuário |
| D4 | Versão do Spring Boot | **4.1.1** (suporte OSS até 31/07/2027). 3.5.16 está sem suporte OSS desde 30/06/2026 [verificado em endoflife.date] |
| D5/D6 | Keycloak | realm único `sigsan`, claim `tenant_id` com o **UUID** do tenant; um usuário pertence a um tenant no R0. Hoje o `KeycloakAdminService` cria um realm por tenant |
| D7 | Keycloak 24 ou 26; banco próprio | 26 com banco e role `keycloak` próprios |
| D8 | Porta da API | 8090 (Keycloak fica em 8080) |
| D9 | Cifra em dev antes do Vault | chave derivada por HKDF de `SIGSAN_DEV_MASTER_KEY` (fora do repo), só dados sintéticos até o R1 |
| D10 | Migrations em produção | job separado (na subida da aplicação só em local/test) |
| D11 | Rascunhos e `validate-step` do cadastro | ficam para o R2 |
| D12 | Papéis | ADMIN, MANAGER, OPERATOR, VIEWER + `registry.reveal-document` |
| D13 | OIDC no frontend | `oidc-client-ts`, PKCE, token em memória |
| G10a | Flyway fora de ordem | ligar `outOfOrder(true)` + teste "tenant antigo alcança o atual". Sem isso, uma `V1_3` criada depois da `V6_1` é recusada em tenant existente |
| S1 | Onde fica `DocumentNumber` | mover de `registry/rules` para `shared/sigsan-core` (MTR também precisa) |
| S2 | CPF no PDF do MTR | máscara, salvo exigência do SINIR |
| S3 | `waste_transporter.drivers` (JSONB com CPF/CNH) | cifrar na mesma migration do MTR |
| — | Nome do diário | manter `docs/DIARIO-DE-BORDA.md` e criar `DIARIO_DE_BORDO.md` na raiz apontando para ele (instrução global) |

### 2.2 De negócio (bloqueiam fases do roadmap)

| # | Decisão | Quem | Até | Bloqueia |
|---|---|---|---|---|
| G1 | Agência e norma dos primeiros clientes; adesão a NR 11/13 | Daniel + regulatório | 06/11/2026 | R3, R4, R7, R8 |
| G2 | Tarifa social: renda/NIS sensível? base legal; desconto na parte fixa e no esgoto; formato da base CadÚnico/BPC | jurídico + regulatório | 13/11/2026 | R3 |
| G3 | NFAg-e: obrigação do cliente; SAAE; modelo 29; **construir ou integrar emissor**; CBS/IBS | tributarista + Daniel | 20/11/2026 | R4 |
| G7 | SINISA: envio automatizado? glossário vigente? quem preenche | regulatório | 30/11/2026 | R6 |
| G4 | Bancos e convênios; código 8 × boleto de cobrança; Pix no MVP; PSP; Pix Automático | Daniel | 15/12/2026 | R5 |
| G8 | SINIR: Web Service para terceiros, credencial, lodo de ETA/ETE | jurídico + regulatório | 15/12/2026 | trilha GRS |
| G5 | Multa 2% CDC em tarifa; juros pro rata die × mês | jurídico | 15/01/2027 | R4, R5 |
| G6 | Retenção 120 meses × LGPD; prescrição | jurídico | 31/01/2027 | R4, R7, R8 |
| G9 | "Horário comercial" do corte; corte por outros motivos; débito pretérito; Decreto do SAC | jurídico | 28/02/2027 | R7, R8 |

Não há responsável jurídico nomeado em nenhum documento (`00` Q5). Sem decisão, o parâmetro fica sem valor e o
código dá erro explícito; o teste de aceite de cada fase cita a decisão que o desbloqueou.

---

## 3. Mapa de versões Flyway (reconciliado)

Os planos parciais usavam `V1_3` para coisas diferentes. Faixas finais:

| Versão | Módulo | Conteúdo | Etapa |
|---|---|---|---|
| core `V2` | core | `audit_redaction_rule` + `public.audit_redact()` | E4 |
| `V1_3` | platform | `audit_trigger_function` endurecida (`SET search_path`, `NULLIF(..,'')::uuid`, `public.current_tenant_id()`) | E3 |
| `V1_4` | platform | trigger de auditoria com redação + regras de `users` | E4 |
| `V1_5` | platform | `regulatory_profiles`, `regulatory_parameters` (vigência), `holidays` (sai de `V6_1`) | R4 |
| `V2_6` | mtr | documentos cifrados (`driver_cpf`, `cnpj_cpf`, `drivers`) | E4 |
| `V2_7` | lgpd | regras de redação de colunas LGPD | E4 |
| `V3_2` | registry | regras de redação do cadastro | E4 |
| `V3_3` | registry | `personal_data_access_log` | E5 |
| `V4_2`…`V4_5` | billing | tarifa social; ciclo; modelos de conta; tributos/fiscal | R3, R4 |
| `V5_2` | metering | tarefas e importação de leitura | R4 |
| `V6_2` | collection | conciliação, Pix, ajustes | R5 |
| `V7_x` | `compliance/sigsan-integration` | SINISA e exportadores | R6 |
| `V8_x` / `V9_x` / `V10_x` | portal / field / dunning | protocolo / OS / cobrança | R7, R8 |
| `V11_x` | `shared/sigsan-events` | outbox, `job_runs` | R4 |
| `V12_x` / `V13_x` | GRS / GIS-perdas | DMR, lodo, IBAMA / incidente, campanhas | GRS, R9 |

---

## 4. Etapas de execução (ações do PDCA = fundações)

Meta: fundações prontas até **20/11/2026**. Cada etapa termina com build verde no banco virgem, registro no
diário e commit.

### E0 — Linha de base (P) · depende de D1–D3
- 7.1: este plano vira o roadmap canônico; diário §12 substituído por remissão a este arquivo; `00`, `14`, `15c` e diário §1 recebem remissão.
- 6.1: `.gitignore` (`target/`, `node_modules/`, `dist/`, `coverage/`, `.env*` exceto `.env.example`, `*.log`, `*.tar`, IDE), `.gitattributes` (`*.sh`, `*.sql` com `eol=lf`), varredura de segredos (gitleaks em container sobre o espelho), **commit da linha de base** (793 testes).
- `robocopy` com `/XF .git`; remover a pasta vazia `sigsan/sigsan`; `DIARIO_DE_BORDO.md` na raiz apontando para o diário.
- **Aceite:** `git log` com 1 commit; nenhum segredo real versionado.

### E1 — Scripts, banco de teste e POMs (P)
- `scripts/lib.sh`: `set -euo pipefail`, lock atômico (`mkdir`, PID, `trap EXIT`, lock velho por `kill -0`), espelho que falha se `pom.xml` não existir no G: e com robocopy ≥ 8.
- `build.sh`, `reset-it-db.sh`, `validate-inp.sh` usam a lib; `docker rm -f … || true`.
- `TenantRlsIsolationTest` e `TenantProvisioningServiceTest` com URL padrão = banco de integração (55433); `ensureCoreSchema()` trocado por `CoreMigrationRunner`.
- POMs: remover `javafaker`, `keycloak-spring-boot-starter`, `spring-cloud-starter-vault-config`/MinIO (voltam no R1), Testcontainers; `jts.version` único; pins de `postgresql`/`flyway` entregues ao BOM; `<parameters>true</parameters>`; `lombok.config` copiando `@Qualifier`.
- **Aceite:** com `sigsan-postgres-primary` parado, `reset-it-db` + `build.sh clean test` verde; `dependency:tree` sem snakeyaml 1.x e com um único jts.

### E2 — Spring Boot 4.1.1 (M; G se o GeoTools conflitar) · depende de D4
- Starters modulares (`-webmvc`, resource server), Hibernate 7 + hibernate-spatial 7 (interfaces de multi-tenant genéricas), Flyway 11, Jackson 3 como padrão (`TenantProvisioningService` deixa de injetar `ObjectMapper`; usa `Json`), remover `FlywayProperties` não usado, `keycloak-admin-client` 26.x.
- Feito **antes** da ação 1 porque ela reescreve o provider de multi-tenant, cuja interface muda no Hibernate 7.
- **Aceite:** 793 testes + `FreshInstallTest` (66 entidades) verdes, em commit próprio. Se gis/losses quebrarem por jts × GeoTools 29, decidir GeoTools ≥ 33.

### E3 — Ação 1: banco e tenant (C1, C2) (G)
- **Roles:** `postgres` (superusuário de bootstrap, só init/operação), `sigsan_owner` NOLOGIN (dono do banco, de `public` e de `current_tenant_id()`), `sigsan_migrator` (CREATE em `public`), `sigsan_app` (sem CREATE nem TEMP). Nenhuma role de login é membro de `sigsan_owner`.
- `infra/postgres/init/00-roles.sh` (senhas por variável, falha se vazias) + `init/sql/security.sql` (reescrita do atual): `ALTER DATABASE … OWNER TO sigsan_owner`, `REVOKE ALL ON SCHEMA public FROM PUBLIC`, `REVOKE TEMPORARY`, função com dono `sigsan_owner`; remove a seção 6 e o `ALTER DEFAULT PRIVILEGES` (sem efeito hoje; "corrigido" daria UPDATE/DELETE em `audit_log`); verificação com `RAISE EXCEPTION` (container não sobe se errado).
- `docker-compose.yml`: `POSTGRES_USER: postgres`, senhas com `:?` sem padrão, `.env.example`; réplica removida ou corrigida. `reset-it-db.sh` e `scripts/it-db.env` (valores só de teste). Kubernetes: Patroni com superusuário próprio + job de bootstrap; Secrets externos.
- `V1_3` (acima). `MultiTenantConnectionProvider`: na entrega da conexão, uma ida ao banco com `set_config('search_path'|'app.current_tenant'|'app.current_user', …, false)`; sem tenant, `RESET`; na devolução, `RESET` e, se falhar, descarta a conexão do pool; schema validado por regex. `TenantSchemaResolver` devolve `TenantRef(schema, id)` conferido em `tenant_catalog` (cache). `PlatformConfig` passa **instâncias** ao Hibernate (hoje passa nome de classe, e o provider não tem construtor vazio).
- **Testes novos** (`platform/sigsan-app`): `DatabaseHardeningTest` (app e migrador recebem 42501 ao redefinir a função e ao criar tabela em `public`; dono = `sigsan_owner`; trigger com `search_path`); `TenantConnectionProviderTest` com pool de 1 conexão e dois tenants (A não vê B; sem tenant, 0 linhas e INSERT barrado; conexão devolvida sem GUC; `audit_log` com `tenant_id` certo sem GUC manual).
- Recriar o banco de dev (`down -v`) — os dados de dev são descartáveis; a cópia em `C:` não é tocada.
- **Aceite:** testes verdes; nenhum teste aponta para 5432; um container com `sigsan_app` superusuário **não sobe**.

### E3' — Ação 3: JSON e falhas silenciosas (M) · em paralelo com E3
- `Json.readMap/readTree` (inválido → erro). LGPD: `DpiaEngine:160` → `Json.write`; `DataSubjectRightsEngine:118` com `readMap`; `LgpdEngineService` e `LabService` sem `toJson` próprio, usando *snapshots* sem dado pessoal (sem IP, user agent, geolocalização, `consentText`, `responseData`); `MtrService:199` → `Json`.
- `ScadaIntegrationService`: desligado por padrão (`sigsan.scada.mqtt.enabled`), `ScadaTopic.parse` (`sigsan/{tenantUuid}/scada/{data|alert}/{tag}`), tenant validado e contexto em try/finally, mensagem sem `timestamp`/`quality` rejeitada.
- `LossesService.loadInputs` lança `MissingInputDataException` (sem placeholders). `MtrService` aceita só array JSON de classes; vazio/ilegível → erro. `TenantProvisioningService:422` valida bcrypt antes de criar schema.
- **Primeiros testes** de `sigsan-lgpd` e `sigsan-quality` (DpiaEngine, DataSubjectRights, LgpdEngineService, LabService, ScadaTopic, ScadaIntegrationService) + Losses, MTR, provisionamento.
- **Aceite:** nenhum `toJson` próprio, nenhuma concatenação de JSON, nenhum `new ObjectMapper()` em service.

### E4 — Ação 2: dado pessoal (C4, C5) (G) · migrations depois de E3
- **Auditoria sem PII:** core `V2` (`audit_redaction_rule` sem acesso da app, `audit_redact()` com REDACT/OMIT); `V1_4` calcula `changed_fields` sobre as linhas cruas e redige depois; regras em `V3_2` (customers, contatos, endereços), `V2_7` (LGPD), `V1_4` (users). Script manual `scripts/dev/scrub-audit-log.sql` para bancos que precisem ser mantidos.
- **MTR** `V2_6`: `driver_cpf`, `waste_generator.cnpj_cpf` e `waste_transporter.drivers` viram ciphertext + hash + máscara; a migration falha se houver valor em claro (sem backfill SQL). `MtrCanonical` continua sobre o CPF decifrado (hash da cadeia inalterado).
- **Cifra ligada:** `FieldProtector` (security), `EnvDerivedTenantKeyProvider` (HKDF; recusa perfil `prod`), `CryptoConfig` (sem provider, a aplicação não sobe), `CustomerDocumentService` (registry: `assign`, `findActiveByDocument`, `reveal` com evento sem valor), `DocumentNumber` em core. Contrato do Vault (Transit, envelope com DEK por tenant em `public.tenant_key`) documentado + `TenantKeyProviderContract`.
- **Frontend (parte pura):** `ResourceClient.search` via POST; guarda que **lança erro** se um parâmetro de URL parecer CPF/CNPJ.
- **Testes:** `AuditRedactionTest` (o texto do `audit_log` não contém nome, e-mail, CPF, IP; `changed_fields` registra mudança de nome; app não lê as regras); `AuditRedactionCoverageTest` (coluna com nome pessoal sem regra nem justificativa quebra o build); `EnvDerivedTenantKeyProviderTest`, `FieldProtectorTest`, `CustomerDocumentServiceTest`, `MtrServiceTest` ajustado; `registry.test.ts`.
- **Aceite:** nenhum dado pessoal em `audit_log` provado por teste; nenhuma coluna de documento em claro.

### E5 — Ação 4: aplicação executável mínima (G) · depende de E2–E4, D5–D13
- **Keycloak:** `infra/keycloak/realms/sigsan-realm.json` (client `sigsan-web` público com PKCE S256, audiência `sigsan-api`, claim `tenant_id`, papéis, sem usuários nem segredos); `scripts/kc-dev-user.sh`; compose com Keycloak 26, banco próprio, import de pasta única por execução; `KeycloakAdminService` atrás de propriedade.
- **Aplicação** (`platform/sigsan-app`): `SigsanApplication` com `scanBasePackages` restrito (core, platform, registry, app — não sobe SCADA/SINIR/LGPD); dois datasources (`appDataSource` @Primary, `migrator` com 2 conexões); `DatabaseMigrator` antes do EMF (core + tenants ACTIVE); `spring.flyway.enabled=false` (senão o Boot aplica migrations de tenant em `public`); `application.yml` só com variáveis; perfis `local`/`test`/`prod`; `SigsanProperties` validado; actuator só `health`; log JSON (ECS) em prod.
- **Segurança:** `SecurityConfig` stateless; `JwtDecoder` com issuer, audiência e expiração; `JwtTenantFilter` em platform depois do `BearerTokenAuthenticationFilter` — tenant **só do claim**, cabeçalho divergente → 403, tenant não ACTIVE → 403, MDC e contexto limpos no `finally`. `TenantFilter` do core removido.
- **Erros:** `GlobalExceptionHandler` em problem+json com `errors[]`/`warnings[]` no formato do frontend; 422/409/404/403; 500 sem stack; `X-Request-ID`; nunca logar corpo.
- **API do cadastro** (contrato de `docs/frontend-cadastro.md` §5): localities, commercial-sectors, blocks, reading-routes, 4 catálogos com tabela, customers (CRUD, `POST /customers/search`, `POST /customers/{id}/reveal-document` com `@PreAuthorize` e `V3_3`), consumer-units (CRUD, `POST /search`, vínculos, histórico), `GET /settings`, `GET /api/me`. GET com `q` que pareça documento → 400.
- **Frontend:** proxy para `SIGSAN_API_URL` (8090); login PKCE com `oidc-client-ts`, callback, token em memória; sai campo de empresa, `X-Tenant-ID` e `localStorage`; três telas com busca por POST.
- **Testes:** `ApplicationContextTest`, `JwtTenantFilterTest` (6 casos), `SecurityIntegrationTest` (tokens RSA reais; 401 por assinatura/issuer/audiência/expiração), `RegistryApiE2ETest` (fluxo completo, 409 de versão, exclusão lógica, busca por POST, documento em `?q=` → 400, reveal com e sem papel, tenant B não vê A, cabeçalho de A com token de B → 403, `audit_log` sem documento), `ProblemDetailsTest`, `TenantMigrationRunnerTest` (outOfOrder).
- **Aceite:** `/actuator/health` UP; login no navegador com PKCE; cadastrar e buscar cliente sintético sem documento em URL; usuário de B não vê A; nenhum segredo em `application*.yml`.

### E6 — CI, frontend e documentação (M)
- `scripts/ci.sh` (lock, reset-it-db, `build.sh clean verify`, frontend `npm ci && typecheck && lint && test:run && npm audit --audit-level=high`); GitHub Actions só com remoto.
- ESLint 9 flat (typescript-eslint, react-hooks, jsx-a11y); triagem do `npm audit`; remover `zustand`, `recharts`, `@hello-pangea/dnd` se confirmados sem uso; sourcemap fora do build de produção.
- 7.2: corrigir as inconsistências do relatório 16 (13 módulos, 66 entidades, §15.5, `14`, `00`, `15c`, READMEs de billing/metering). 7.3: `README.md` na raiz (pré-requisitos, espelho, comandos, portas 5432/55433/8080/8090/3000, `.env.example`, aviso sobre a cópia em C:). 7.4: `docs/frontend-cadastro.md` atualizado.
- **Aceite:** uma única ordem de prioridade nos documentos; nenhum número diverge do build.

---

## 5. Roadmap de produto (depois das fundações)

Capacidade suposta: duas trilhas — **A** comercial, **B** regulatório/GRS/atendimento. Com uma pessoa só, R6, R7 e
GRS escorregam ~2 meses. Datas abaixo supõem fundações prontas até 20/11/2026.

### R1 — Vault (M) · antes do primeiro CPF real
`VaultTransitTenantKeyProvider` (KEK no Transit, DEK por tenant cifrada em `public.tenant_key`, cache em memória com
TTL, rotação pelo byte de versão), core `V3`, Vault fora do modo dev. Passa no `TenantKeyProviderContract`.

### R2 — Cadastro completo
O que o R0 deixou: rascunhos e `validate-step`, 8 catálogos sem tabela, block-faces, CEP, exportação CSV.

### R3 — Tarifa social + simulador N4 (M) · alvo 09/12/2026 · G1, G2
- `V4_2`: lotes de importação (hash, `purge_at`), elegibilidades (família por índice cego do NIS, base CadÚnico/BPC, `ELIGIBLE`/`GRACE_3M`/`LOST`), decisões, pedidos manuais.
- `SocialTariffClassifier` puro (renda ≤ ½ SM carregado de fonte normativa e CadÚnico ≤ 2 anos, ou BPC; uma economia por família; nunca depende de adimplência), importador por agência (começar pelo CSV da ARSESP), `TariffImpactSimulator`, relatório anual.
- Casos: T-03 com banda fictícia de 50% → 102,00 (só variável) e 87,00 (com fixa); CadÚnico de 25 meses → inelegível; família fora da nova base → `GRACE_3M` por 3 ciclos.
- A parte de regra pura pode começar já, em paralelo às fundações.

### R4 — Medição, ciclo, conta, fiscal + N6 (G) · R4a 22/12/2026; R4 19/03/2027 · G1, G3, G5, G6
- R4a: `tax_rates` com vigência e norma-fonte; alíquota ausente → erro.
- `V11_1` (outbox, `job_runs`), `V1_5`, `V5_2`, `V4_3`–`V4_5`; ciclo M0–M12 em Spring Batch particionado por rota com a GUC reaplicada a cada *chunk*; PDF; porta `FiscalAuthorizer`; painel de fechamento (N6).
- Casos do relatório 02 (T, E, C, I, V, L, A, X); leitura 1000→1027 com E-01 → 201,60; ciclo rodado duas vezes não cria segunda conta; reinício continua só as rotas pendentes.
- NFAg-e por integrador: até 30/04/2027, se G3 confirmar.

### Trilha GRS — MTR e DMR assistidos (M) · 29/01/2027 · G8
Corrigir D-MTR-1..7; `V12_1` (itens, partes, lodo, DMR, CDF importado); ETL da Lista IBAMA (878 códigos, 449 com
asterisco); NBR 10004 por edição e UF. DMR assistida útil a partir do 1º tri de 2027 (30/04/2027).

### R5 — Arrecadação, conciliação N3, Pix · G4, G5
`V6_2`; retorno idempotente (NSA + hash) → `PaymentClassifier`; aprovação dupla (`requested_by ≠ approved_by`);
porta `PixProvider` com webhook assinado — **o tenant do webhook vem do convênio, nunca de cabeçalho**. Entregas:
R5a retorno e conciliação 30/04/2027; R5b Pix Cobrança e híbrido 31/05/2027; R5c Pix Automático 30/06/2027.

### R6 — SINISA ciclo 2027 e exportadores (M) · 30/04/2027 · G7
`V7_1`: glossário por ETL com referência; `sinisa_values` com origem COMPUTED/IMPORTED/MANUAL e justificativa
obrigatória para manual; checagens de consistência; exportação XLSX/CSV; plugins de agência depois. Levantamento do
glossário começa já. Casos: água não faturada 3.000 m³ (diário §15.3); ILI com unidade corrigida.

### R7 — Protocolo + OS com bloqueio de corte (G) · 14/05/2027 · G1, G6, G9
`V8_1` (protocolo único por tenant, SLA, resposta conclusiva), `V9_1` (tipos de serviço, OS, avisos com prova de
entrega, bloqueios); `CutoffEligibilityPolicy` pura e **sem passar por cima**. Casos: aviso em 15/10/2026 → 14/11
(sábado) bloqueado, primeira data válida 16/11/2026; 19/11 (véspera) e 20/11 (feriado) bloqueados, 23/11 permitido;
perfil ADASA (< 60 ou > 120 dias de atraso → bloqueado); sem prova de aviso → religação sem taxa.

### R8 — Cobrança (G) · 30/09/2027 · R7, R5, G5, G6, G9
`V10_1`: ações, critérios, parcelamento com demonstrativo, contestação bloqueia corte e negativação, prescrição sem
padrão. Negativação por último (2028, se houver demanda).

### R9 — GIS, incidente, perdas (G) · 2º semestre de 2027
N2 incidente e afetados (após R7, base legal decidida), N5 crítica por pares, N1 campanhas de perdas (só com entrada
real de R4/R6), N7 publicação de resultado hidráulico, pipeline GIS (`05` P1-7). `V13_x`.

Trilhas paralelas sem data: catálogo de qualidade da água carregado da Portaria 888 com modelo de conformidade
(percentil, média móvel, plano de amostragem); incidente LGPD (SEC-07).

---

## 6. Tabela-resumo

| Etapa/fase | Entrega | Prazo legal | Alvo | Bloqueio |
|---|---|---|---|---|
| E0 | roadmap canônico, git, commit de base | — | 14/10/2026 | D1–D3 |
| E1 | scripts, banco de teste, POMs | — | 16/10/2026 | — |
| E2 | Spring Boot 4.1.1 | — | 23/10/2026 | D4 |
| E3 + E3' | banco/tenant (C1, C2) + JSON e falhas silenciosas | — | 06/11/2026 | G10a |
| E4 | auditoria sem PII, MTR cifrado, cifra no cadastro (C4) | LGPD | 13/11/2026 | S1–S3 |
| E5 | aplicação, JWT, API do cadastro, login PKCE (C3, C5) | — | 20/11/2026 | D5–D13 |
| E6 | CI, lint, docs | — | 27/11/2026 | — |
| — | DMR 3º tri / SINISA 2025 | 31/10; 03/11–31/12/2026 | não entregável | ver §1 |
| R3 | tarifa social (pacote) + N4 | 11/12/2026 | 09/12/2026 | G1, G2 |
| R1 | Vault | antes do 1º CPF real | 18/12/2026 | — |
| R4a | alíquotas CBS/IBS | 01/01/2027 | 22/12/2026 | G3 |
| GRS | MTR/DMR assistidos | DMR trimestral | 29/01/2027 | G8 |
| R4 | ciclo, conta, emissão, N6 | — | 19/03/2027 | G1, G5, G6 |
| R5a / R6 | conciliação + N3 / SINISA 2027 | — / 31/12 anual | 30/04/2027 | G4 / G7 |
| R7 | protocolo + OS com bloqueio de corte | Lei 14.015; NR 11 20/05/2027 | 14/05/2027 | G9 |
| R5b / R5c | Pix híbrido / Pix Automático | 01/02/2027 (facult.) / 01/07/2027 | 31/05 / 30/06/2027 | G4 |
| R8 | cobrança | Lei 11.445 art. 40 | 30/09/2027 | R7, G6 |
| R9 | GIS, N1/N2/N5/N7 | NR 15 em 20/05/2028 | 2º sem. 2027 | R4, R6, R7 |

As datas de E0–E6 supõem execução contínua com agentes; as de R3 em diante supõem as duas trilhas.
