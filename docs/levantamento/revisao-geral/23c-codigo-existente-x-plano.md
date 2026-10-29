# 23c — Código existente frente ao plano e às decisões de arquitetura

**Data:** 28/10/2026. **Base:** árvore de trabalho em `G:\Meu Drive\PROJETOS\Ziip SAN\sigsan` (último commit
`f3eaac1`, plano v2.21). **Escopo:** backend Java (13 módulos Maven ativos e 14 diretórios vazios), migrations Flyway,
frontend, `infra/`, `docker-compose.yml` e `scripts/`, confrontados com `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (v2.21),
`docs/arquitetura/adr/`, `ARQUITETURA-ALVO.md`, `REQUISITOS-NAO-FUNCIONAIS.md`, `PLATAFORMA-E-VERTICAIS.md` e
`MARCA-E-TEMAS.md`. Nenhum arquivo de código, plano ou configuração foi alterado.

## 0. Leitura rápida

1. Têm fonte 13 módulos (25,6 mil linhas Java de código e 15,3 mil de teste); 14 diretórios de módulo estão vazios.
2. Só o cadastro está exposto pela API; billing, metering e collection são motores puros com teste; MTR, LGPD,
   qualidade, perdas e GIS têm serviço fora do scan da aplicação.
3. Isolamento no banco correto: as 76 tabelas de tenant têm RLS forçada e as roles não têm BYPASSRLS. O risco está na
   plataforma: a role da API grava o catálogo de tenants, o provisionamento aceita schema existente, a GUC é de sessão.
4. Quase todas as divergências de decisão já estão no plano (BFF, `/api/v1`, URN, PostgreSQL 17, job `migrate`,
   outbox, renomeação); aqui ganham linha de código. Há 15 achados novos e detalhes novos em outros 9.
5. Novos de maior peso: regras de consumo escritas duas vezes (billing e metering), colisão de schema no
   provisionamento, compose com serviços em 0.0.0.0 e credencial padrão, campos de tela descartados em silêncio e o
   código `requisicao-invalida` ausente da tabela da P0.33.
6. Recomendação central: apagar legado e sobras antes da P0.33, para não renomear o que vai ser apagado.

## 1. Método

- Leitura estática dos fontes com Grep, Read e scripts Node de apoio (contagem de linhas, conferência de RLS por
  tabela, versões resolvidas no `package-lock.json`, classificação de credenciais sem exibir valores). Não houve build,
  teste, instalação de dependência, `npm audit` nem container.
- Lidos integralmente: `sigsan-app` (main), `sigsan-platform` (main), `sigsan-core` (main e migrations core), segurança
  do banco (`infra/postgres/init`), realm do Keycloak, `docker-compose.yml`, `scripts/`, autenticação e cliente HTTP do
  frontend. Lidos por amostragem dirigida: serviços do cadastro, LGPD, MTR, qualidade, GIS, collection, migrations de
  tenant.
- Conferência de RLS: para cada `CREATE TABLE` em `db/migration/tenant`, procurou-se `register_tenant_table(...)` ou
  `ALTER TABLE ... FORCE ROW LEVEL SECURITY`. Resultado: 76 tabelas, nenhuma sem RLS forçada.
- Credenciais: identificadas por nome de variável e linha; valores não foram copiados para este documento.
- Cada achado diz se já está previsto no plano ("previsto") ou se é novo ("novo").
- Gravidade: **Alta** (bloqueia o P0 ou permite mistura entre tenants, exposição de credencial ou dado incorreto);
  **Média** (falha real com alcance limitado ou latente); **Baixa** (higiene, manutenção).

## 2. Inventário por módulo

Contagens: arquivos e linhas de `src/main` e `src/test`; "casos" é o número de anotações `@Test`/`@ParameterizedTest`
(o total do plano, 973, conta as execuções parametrizadas).

| Módulo | O que existe de fato | Maturidade | Testes | Divergências principais |
|---|---|---|---|---|
| `shared/sigsan-core` | `ApiException` (problem codes), `Json` (Jackson 3), `DocumentNumber` (CPF e CNPJ alfanumérico), `BaseEntity`/`TenantAwareEntity`, `AuditLog`, `TenantContextHolder` (ThreadLocal), `CoreMigrationRunner`; core `V1` (catálogo, `audit_log` e `tenant_settings` com RLS forçada) e `V2` (redação) | Funcional | 10/644 l main; 4/681 l teste; 36 casos, incluindo `TenantRlsIsolationTest` em PostgreSQL real | Jackson 2 e 3 juntos; `AuditListener.java` vazio; catálogo gravável pela `sigsan_app`; `audit_log` sem partição (X3) |
| `shared/sigsan-security` | `FieldCipher` (AES-256-GCM, AAD com tenant), `BlindIndex` (HMAC-SHA256), `FieldProtector`, `TenantKeyProvider`, `EnvDerivedTenantKeyProvider` (HKDF, só desenvolvimento) | Funcional para desenvolvimento | 6/376 l; 4/395 l; 28 casos | Sem provedor de cofre (P0.28); contexto HKDF ainda `sigsan/enc/v1` e `sigsan/bidx/v1` (P0.33) |
| `shared/sigsan-events` | Vazio | Inexistente | — | Outbox do ADR-0014 não existe em lugar nenhum |
| `platform/sigsan-platform` | `JwtTenantFilter`, `TenantCatalog`, `MultiTenantConnectionProvider`, `TenantSchemaResolver`, `DatabaseMigrator`, `TenantMigrator`, `TenantProvisioningService`, `KeycloakAdminService` e `KeycloakConfig` (legado), `ModuleRegistry` (sem uso); tenant `V1_1`–`V1_4` | Parcial | 13/1.853 l; 2/750 l; 25 casos | GUC de sessão; `outOfOrder(true)`; migração no processo da API; realm por tenant legado; `ModuleRegistry` contra o ADR-0037; tabelas legadas em `V1_1` |
| `platform/sigsan-auth`, `platform/sigsan-tenant` | Vazios | Inexistente | — | Funções já estão em `sigsan-platform` |
| `platform/sigsan-app` | `SigsanApplication` (scan restrito a core, platform e registry), `SecurityConfig` (resource server JWT), `GlobalExceptionHandler`, `MeController`, `RequestIdFilter`, `DataSourceConfig` (dois pools), `CryptoConfig`, `DevTenantBootstrap`, `application*.yml` | Funcional (API do cadastro ponta a ponta) | 9/597 l; 15/2.746 l; 83 casos (E2E do cadastro, endurecimento do banco, redação, instalação limpa, segurança) | Sem BFF, CSRF desligado, sem `/api/v1`, URN em português, sem CSP nem rate limit, sem observabilidade, `fail-on-unknown-properties: false` |
| `commercial/sigsan-registry` | Cliente, unidade consumidora, território (localidade, setor, quadra, rota), catálogos (categoria, subcategoria, situações de água e esgoto), parâmetros; 5 controllers; JPQL com parâmetros e ordenação por lista fechada; LIKE escapado; documento cifrado com índice cego; revelação com papel próprio; `V3_1`–`V3_3` | Funcional (único módulo exposto) | 56/4.479 l; 4/503 l no módulo, mais `RegistryApiE2ETest` (550 l) e `RegistrySchemaTest` no app | Rotas `/api/registry` sem versão; papéis fixos em vez do catálogo `recurso:ação` (ADR-0040); busca por documento sem papel nem cota (P0.11); paginação por offset (P0.22); DTOs de busca sem `@Valid` |
| `commercial/sigsan-billing` | Motor tarifário (faixas, categoria, social, esgoto), cálculo da conta (multa, juros, vencimento, créditos, débitos, retenção, parcelamento, rateio de condomínio), `Money`; 13 entidades; `V4_1` | Parcial: regra pura, sem serviço, API nem lote | 67/2.924 l; 17/2.694 l; 151 casos, com `MigrationConsistencyTest` e `SourceHygieneTest` | Regras de consumo duplicadas com metering; fiscal inexistente (P0.32) |
| `commercial/sigsan-collection` | Código de barras de arrecadação e de boleto, fator de vencimento, retorno FEBRABAN 150 (parser e validador), classificação de pagamento, acréscimos (multa, juros, correção, calendário útil); 7 entidades; `V6_1` | Parcial: regra pura | 40/3.237 l; 9/2.196 l; 165 casos | `ObjectMapper` do Jackson 2 (P0.25); CHECK da multa e `LegalLimits` (P0.20); `holidays` em `V6_1` (vai para `V1_5`) |
| `operations/sigsan-metering` | Crítica de leitura, cálculo de consumo (`ConsumptionCalculator`, 663 l), média, faixa esperada, não medido, rateio de condomínio, volume de esgoto; 6 entidades; `V5_1`–`V5_2` | Parcial: regra pura | 54/3.083 l; 10/2.564 l; 149 casos | Duplicação com billing |
| `grs/sigsan-mtr` | MTR e CDF com máquina de estados, cadeia de hash, forma canônica, PDF escrito byte a byte, cifra de documentos das partes; SINIR e gov.br como stubs que nunca declaram sucesso; `V2_4`, `V2_6`, `V2_8` | Parcial: serviço sem API, fora do scan | 21/2.033 l; 6/1.044 l; 58 casos | SINIR contra o ADR-0004 (P0.25); documentos decifrados para o stub; PDF artesanal |
| `compliance/sigsan-lgpd` | Consentimento, DPIA, requisição do titular, retenção (nunca apaga), fila de anonimização; `V2_5`, `V2_7` | Parcial, fora do scan | 18/1.694 l; 3/370 l; 15 casos | Chaves de cache sem tenant; `grantConsent` (P0.25) |
| `intelligence/sigsan-quality` | Amostras, parâmetros, pontos, resultados; laboratório (método, instrumento, controle de qualidade); SCADA por MQTT (Paho); `V2_1`, `V2_9` | Esqueleto de serviço, fora do scan | 25/1.744 l; 3/403 l; 15 casos | MQTT contra ADR-0010/ADR-0028 (REST e arquivo, sem broker); contexto de tenant incompleto |
| `intelligence/sigsan-losses` | Balanço hídrico, estatística de pressão, detecção de vazamento, DMA; `V2_2`, `V2_10` | Parcial: cálculo puro com teste | 12/1.124 l; 3/398 l; 24 casos | Depende de quality; `DmaDistrictRepository` repetido em gis |
| `intelligence/sigsan-gis` | Elemento de rede em tabela única, topologia por proximidade, escritor INP do EPANET; simulação sem solver; `V2_3` | Esqueleto, a ser substituído pelo núcleo GIS (`V13_x`) | 22/1.772 l; 2/555 l; 31 casos | Já tratado em `GIS-CADASTRO-TECNICO.md` §2.3 |
| Demais 9 diretórios (`commercial/sigsan-accounting`, `-dunning`, `-portal`; `compliance/sigsan-integration`; `grs/sigsan-collection-waste`, `-disposal`, `-pgrs`; `operations/sigsan-assets`, `-field`, `-maintenance`, `-reading`) | Vazios, fora do `pom.xml` raiz | Inexistente | — | Sobra de estrutura |
| `frontend/` | React 18, Vite 5, TypeScript; login OIDC com PKCE (`oidc-client-ts`, `sessionStorage`); cliente axios com renovação; telas do cadastro (clientes, imóveis com assistente, território, catálogos, mapa MapLibre); validadores | Funcional para o cadastro | 70 arquivos; 10 arquivos de teste, 141 casos (Vitest, axe) | Token no navegador (ADR-0006); marca `SIGSAN` literal; cores literais; dependências antigas; campos sem backend |
| `infra/` | Init do PostgreSQL com roles conferidas; realm `sigsan` (client público com PKCE, sem MFA); Kubernetes e Terraform (`us-east-1`) sem uso | Parcial | — | ADR-0010 (arquivar), ADR-0011 (região), ADR-0013 |
| `docker-compose.yml` | PostGIS 15, Keycloak `start-dev`, mais TimescaleDB, Redis, Kafka, Zookeeper, SeaweedFS e Vault dev, todos sem `profiles:` | Parcial | — | ADR-0010, ADR-0013, P0.29 |
| `scripts/` | Build e CI em container Maven, espelho do Drive por `robocopy` via PowerShell | Funcional | — | P0.1 (sair do Drive) |

## 3. Quadro de achados

| ID | Módulo | Tipo | Gravidade | Evidência | Proposta | Tarefa |
|---|---|---|---|---|---|---|
| COD-01 | core, platform | Segurança | Alta | `shared/sigsan-core/src/main/resources/db/migration/core/V1__create_core_schema.sql:180` concede SELECT, INSERT, UPDATE e DELETE em `public.tenant_catalog` à `sigsan_app`; `TenantProvisioningService.java:246` grava o catálogo pela conexão da aplicação. O catálogo liga tenant a schema: quem altera `schema_name` ou `status` com a role da API redireciona um tenant para os dados de outro. | `sigsan_app` só com SELECT no catálogo; escrita só pela role de migração ou de plataforma, com teste de privilégio em `DatabaseHardeningTest`. | P0.17 (previsto) |
| COD-02 | platform | Segurança | Alta | `TenantProvisioningService.java:159` usa `CREATE SCHEMA IF NOT EXISTS`; `:231` faz `ON CONFLICT (schema_name) DO UPDATE`; `:456-470` normaliza o nome (troca caracteres por `_`, trunca em 63) e duas entradas diferentes podem gerar o mesmo schema. O segundo provisionamento roda as sementes (`:363-447`) com o novo `tenant_id` dentro do schema existente e devolve sucesso com um UUID que não está no catálogo. | Recusar schema existente e nome que mude na normalização; catálogo com `id` como chave do conflito; passos idempotentes por `tenant_id`, não por nome. Detalhe novo: a colisão por normalização. | P0.17 (previsto em parte) |
| COD-03 | app, platform | Divergência de decisão | Alta | `DataSourceConfig.java:29-39` cria o `migratorDataSource` em todo perfil; `SigsanProperties.java:27-28` exige a senha do migrador; `DatabaseMigrator.java:47-58` migra na subida; `application-local.yml:6` liga por padrão. | Processo `migrate` separado; perfil `prod` sem o bean (teste de contexto). | P0.6 (previsto) |
| COD-04 | platform | Divergência de decisão | Alta | `MultiTenantConnectionProvider.java:39-47` usa `set_config(..., false)` (escopo de sessão). Incompatível com PgBouncer em modo transaction exigido pela ARQUITETURA-ALVO §4. | Contexto local à transação (`set_config(..., true)` após o BEGIN, por exemplo num `ConnectionCustomizer` do Hibernate ou na abertura da transação), com teste de vazamento entre tenants sob pooler. | P0.28 (previsto) |
| COD-05 | app, frontend | Divergência de decisão | Alta | `SecurityConfig.java:61` desliga CSRF, `:64` sessão STATELESS, Bearer no cabeçalho; `frontend/src/services/auth/oidc.ts:28` guarda tokens em `sessionStorage`; `frontend/src/services/api.ts:22-26` injeta o Bearer. Sem CSP, rate limit nem limite de corpo. | BFF no processo `api` com cookie `__Host-`, CSRF ligado, sessão no PostgreSQL; CSP e limites na mesma onda. | P0.14, P0.16 (previsto) |
| COD-06 | compose | Segurança | Alta | `docker-compose.yml:56-66` TimescaleDB em `0.0.0.0:5434` com valor padrão literal para `TIMESCALE_PASSWORD`; `:82-89` Redis em `0.0.0.0:6379` sem senha; `:96-114` Kafka em `0.0.0.0:9092/9093`; `:146-159` SeaweedFS (serviço `minio`) em `0.0.0.0:9000` e outras portas, com padrão literal para `MINIO_PASSWORD`; `:219-227` Vault em modo dev com token raiz fixo no comando (`:222`) e em `VAULT_DEV_ROOT_TOKEN_ID` (`:224`), em `0.0.0.0:8200`. Nenhum serviço tem `profiles:`; `docker compose up` sobe tudo na rede local da máquina. | Remover TimescaleDB, Redis, Kafka, Zookeeper e Vault dev; SeaweedFS só como emulador de S3 da P0.27, em `127.0.0.1`, sem senha padrão. | P0.7 (previsto; detalhe das portas e senhas novo) |
| COD-07 | billing, metering | Qualidade | Alta | Mesmas regras implementadas duas vezes, de forma independente: `billing/consumption/NonMeteredConsumption.java` (99 l) e `metering/rules/NonMeteredConsumption.java` (115 l); `billing/bill/CondominiumApportionment.java` (72 l) e `metering/rules/CondominiumApportionment.java` (123 l); `billing/consumption/ExpectedReadingRange.java` (65 l) e `metering/rules/ExpectedReadingRange.java` (29 l); `billing/tariff/EconomyGroup.java` e `metering/rules/EconomyGroup.java`. O README do metering declara as suas "escritas do zero". Duas fontes para o consumo faturado produzem contas divergentes. | Um dono por regra: consumo, faixa e rateio no metering; tarifa no billing, que recebe o consumo pronto. Apagar as cópias do billing e manter um teste de vetor comum. | P0.25 (novo) |
| COD-08 | platform | Sobra / segurança | Média | `KeycloakAdminService.java` cria realm por tenant (`:39-102`), client confidencial com password grant (`:112`), política de senha de 8 caracteres (`:75`), domínios `sigsan.com.br` fixos (`:116-123`), claim `tenant_id` preenchido com o nome do schema (`:126-127`) e grava o hash bcrypt como se fosse a senha (`:172`). É chamado em todo provisionamento (`TenantProvisioningService.java:116`); só não age porque a administração está desligada. Dependência `keycloak-admin-client` 26.0.12 (`pom.xml:34-35`) contra servidor 26.8.0. | Apagar `KeycloakAdminService`, `KeycloakConfig` e a dependência; gestão de usuários pela API própria. | P0.13 (previsto) |
| COD-09 | platform | Sobra / divergência | Média | `V1_1__create_tenant_schema.sql:29-36` cria `users` com `password_hash`; `:54`, `:68`, `:10`, `:144`, `:167` criam `roles`, `user_roles`, `tenant_metadata`, `documents` e `service_orders`. Nenhuma tem entidade ou uso em Java; `TenantProvisioningService.java:363-423` semeia papéis com permissões `consumer:read` etc. que a autorização não lê (ela usa `RegistryRoles.java:12-14`). `service_orders` antecipa um modelo de OS que o ADR-0039 e a P1.31 (`V9_1`) definem de outro jeito. | Como a P0.33 já muda checksums e recria os bancos, reescrever `V1_1` sem essas tabelas (ou contrair numa `V1_x`), e tirar as sementes do provisionamento. | P0.13 (previsto em parte: o hash); resto novo |
| COD-10 | app | Qualidade / dado | Média | `application.yml:19-21` liga `fail-on-unknown-properties: false`; o frontend coleta `rg` e `motherName` (`frontend/src/pages/cadastro/customerSchema.ts:39,42`) e `costCenter` (`territoryDefinitions.ts:20`), que o backend descarta sem aviso. O operador acredita ter gravado. | Recusar propriedade desconhecida (400 com o campo) ou remover os campos das telas até o R2; registrar no catálogo de erros. Coletar RG e nome da mãe sem guardar também contraria a minimização da LGPD. | P0.16 (novo) |
| COD-11 | app, platform | Divergência de decisão | Média | `ProblemResponses.java:20` usa `urn:sigsan:problema:`; códigos em português em `ApiException.java:51,68`, `GlobalExceptionHandler.java:47-91`, `SecurityConfig.java:55-58`, `JwtTenantFilter.java:62-78`. `GlobalExceptionHandler.java:91` emite `requisicao-invalida` (404 de rota, 405, corpo ilegível), código que **não está** na tabela da P0.33. `titleFor` (`:107-117`) não trata 401, 413 nem 429. | Acrescentar `requisicao-invalida` → `invalid-request` à tabela da P0.33; tratar 401, 413 e 429; teste de contrato que varre todos os códigos emitidos. | P0.33 (previsto; código faltante novo) |
| COD-12 | app, registry, frontend | Divergência de decisão | Média | `MeController.java:23` em `/api/me`; `RegistryRoles.java:16` em `/api/registry`; `frontend/src/services/api.ts:14` com base `/api`. | `/api/v1` com OpenAPI gerado e conferido no CI. | P0.10 (previsto) |
| COD-13 | todos | Divergência de decisão | Média | `sigsan` aparece 2.125 vezes em 508 arquivos fora de `docs/`, `target/` e `node_modules` (pacotes `br.com.sigsan`, roles `sigsan_*`, realm, propriedades `sigsan.*`, tópico MQTT, HKDF em `EnvDerivedTenantKeyProvider.java:32-33`). | Executar a P0.33 depois da limpeza da seção 4, para não renomear o que será apagado. | P0.33 (previsto) |
| COD-14 | platform | Dívida técnica | Média | `TenantMigrator.java:51` com `outOfOrder(true)`; migrations de tenant aplicadas em sequência na subida (`DatabaseMigrator.java:53-58`). | Manter até a P3.12, como decidido; ondas e quarentena no job `migrate`. | G10a, P3.12 (previsto) |
| COD-15 | app | Segurança | Média | `CryptoConfig.java:43` recusa `dev-env` só em perfil cujo nome contém `prod`; perfis de homologação ou demonstração aceitam a chave única de desenvolvimento. | Lista fechada de perfis permitidos. | P0.17 (previsto) |
| COD-16 | app | Segurança | Baixa | `MeController.java:33` devolve `tenantSchema`, nome interno do banco. | Retirar do contrato. | P0.17 (previsto) |
| COD-17 | lgpd | Segurança / qualidade | Média | `LgpdEngineService.java:130` usa `@Cacheable(key = "#tenantId + ... + #purpose")`, mas o método `getActiveConsent(UUID subjectId, String purpose)` não tem parâmetro `tenantId`: a chave sai sem tenant. `:48` (`grantConsent(request)`) despeja pela chave `#tenantId + '_' + #subjectId`, que também não existe, e nunca casa com a chave gravada: após conceder consentimento, a consulta continua a devolver o vazio em cache. Hoje latente (sem `@EnableCaching` e módulo fora do scan). | Chave a partir de `TenantContextHolder` (ou parâmetro explícito) e evict pela mesma chave; teste com dois tenants. | P0.17 (previsto como "cache com tenant"; o defeito do evict é novo) |
| COD-18 | quality | Divergência de decisão | Média | `ScadaIntegrationService.java:68-126` assina tópico MQTT com Paho (`sigsan-quality/pom.xml:30-32`, versão 1.2.5, de 2020); o ADR-0010 e o ADR-0028 decidem ingestão por REST e arquivo, sem broker. `:161` põe só o `tenantId` no contexto, sem schema: o `TenantSchemaResolver` lança "contexto de tenant inconsistente" na primeira gravação. O tenant vem do tópico, afirmado por quem publica. | Apagar o cliente MQTT, `ScadaTopic` e a dependência; a ingestão da ED-17 nasce na `api` e no `worker`. | P0.25 (novo) |
| COD-19 | mtr | Divergência / LGPD | Média | `SinirIntegrationService.java` nunca envia (devolve sempre pendente), mas `MtrService.java:155,207` o chama em toda coleta e destinação, e `:246` decifra o documento do gerador e o CPF do motorista só para montar esse pedido. | Remover a chamada e o serviço (SINIR assistido, ADR-0004); nenhuma decifração sem uso. | P0.25 (previsto; decifração desnecessária nova) |
| COD-20 | collection, core | Dívida técnica | Baixa | `FreeFieldLayout.java:117` e `AgreementBarcodeLayout.java:114` usam `ObjectMapper` do Jackson 2; `sigsan-core/pom.xml:28-37` declara Jackson 2 e 3. | Passar para `br.com.sigsan.core.json.Json` e remover o Jackson 2. | P0.25 (previsto) |
| COD-21 | platform | Sobra | Baixa | `ModuleRegistry.java` (427 l): `@Service` com `@PostConstruct` (`:20`) que registra módulos fictícios ("e-Sfinge", "SPC/Serasa", "Vans") com versão "1.0.0"; nenhuma referência no código. Concorre com o contrato de pacote de vertical (ADR-0037, `vertical.json`). | Apagar; o registro de pacotes nasce na P0.47. | P0.47 (novo) |
| COD-22 | core | Sobra | Baixa | `shared/sigsan-core/src/main/java/br/com/sigsan/core/audit/AuditListener.java` tem 0 bytes. | Apagar. | P0.25 (novo) |
| COD-23 | platform | Qualidade | Baixa | `TenantProvisioningService.java:317` deixa `log.error("DEBUG audit falhou", e)`; `:242-245` diz que o catálogo tem RLS, o que `V1__create_core_schema.sql:97` nega; `:502-509` (`isSchemaInitialized`, `listInitializedSchemas`) sem uso; `:250,306,343` montam `set_config` por concatenação (UUID tipado, sem risco imediato, mas fora do padrão de parâmetro). | Limpar na reescrita do provisionamento; `set_config` com parâmetro. | P0.17 (novo) |
| COD-24 | registry | Segurança | Baixa | `CustomerController.java:56` e `ConsumerUnitController.java:62` recebem `@RequestBody` de busca sem `@Valid`; `q` sem limite de tamanho; `JpqlQuery.java:67` calcula `page * size` em `int` sem teto de página (página muito alta transborda e vira 500). | `@Valid` e `@Size` nos DTOs de busca; teto de página ou keyset. | P0.16, P0.22 (previsto em parte) |
| COD-25 | registry | Divergência de decisão | Média | `RegistryRoles.java:12-14`: autorização por papel fixo (`hasAnyRole`) em anotação de classe; não há catálogo de permissões nem teste que recuse rota sem permissão. A leitura do cadastro, inclusive busca por documento, vale para VIEWER. | Catálogo `recurso:ação` com negação por padrão; teste de rota sem permissão; busca por documento com papel, registro e cota. | P0.13, P0.11 (previsto) |
| COD-26 | events | Divergência de decisão | Média | `shared/sigsan-events` vazio; nenhuma tabela `outbox` nem worker. | Outbox no core `V8` com interface e teste de contrato. | P0.45 (previsto) |
| COD-27 | compose, infra | Divergência de decisão | Média | `docker-compose.yml:7` usa `postgis/postgis:15-3.4`; `:177` Keycloak `start-dev`. | PostgreSQL 17 + PostGIS 3.5; Keycloak em modo de produção local. | P0.4, P0.29 (previsto) |
| COD-28 | infra | Segurança / sobra | Média | `infra/kubernetes/base/grafana-deployment.yaml:93-94`, `keycloak.yaml:132,142` e `minio.yaml:107` trazem `Secret` com senha literal de marcador (8 caracteres); `infra/terraform/main.tf:22,88` em `us-east-1`, contra o ADR-0011 (nada fora de `sa-east-1`). | Mover para `infra/_arquivado/` com LEIA-ME, como decidido; o gitleaks da P0.3 deve tolerar só essa pasta, ou apagar os `Secret`. | P0.7, P0.3 (previsto) |
| COD-29 | raiz | Segurança | Média | `.env` com as senhas de desenvolvimento (`POSTGRES_SUPERUSER_PASSWORD`, `SIGSAN_APP_PASSWORD`, `SIGSAN_MIGRATOR_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `SIGSAN_DEV_MASTER_KEY`, `SIGSAN_DEV_PASSWORD`) está na pasta do Google Drive, sincronizada com a nuvem. Não é versionado. | Tirar do Drive com a P0.1 e gerar valores novos no destino. | P0.1 (previsto) |
| COD-30 | scripts, testes | Segurança | Baixa | `scripts/it-db.env:7-9` versiona as senhas do banco descartável de integração (`SIGSAN_IT_SUPERUSER_PASSWORD`, `SIGSAN_IT_DB_PASSWORD`, `SIGSAN_IT_MIGRATOR_PASSWORD`); os mesmos valores são padrão em `TenantProvisioningServiceTest.java:68,72` e `TenantRlsIsolationTest.java:78`; `reset-it-db.sh:31` publica a porta 55433 em todas as interfaces. | Gerar a senha no próprio script ou no serviço do CI; banco e build numa rede Docker própria, sem publicar a porta (ou publicação em `127.0.0.1`, se o acesso por `host.docker.internal` continuar a funcionar); regra explícita do gitleaks, se mantidas. | P0.3 (novo) |
| COD-31 | build | Qualidade | Média | Não há JaCoCo, ArchUnit, SpotBugs nem análise de dependências Maven; `maven-enforcer-plugin` está só em `pluginManagement` (`pom.xml:104`), sem execução. A P0.3 cita `npm audit` e gitleaks, não análise de vulnerabilidade do Maven. | Cobertura medida (sem meta inicial), enforcer ativo, análise de dependências Maven (OWASP Dependency-Check ou OSV) no CI; teste de fronteira com ArchUnit. | P0.3, P0.47 (novo em parte; conferir se PLT-37 cobre) |
| COD-32 | app | Dívida técnica | Baixa | `platform/sigsan-app/pom.xml` traz todos os módulos (inclusive quality com Paho), mas `SigsanApplication.java:19-28` só escaneia core, platform e registry; `PlatformConfig.java:36` mapeia entidades de `br.com.sigsan` inteiro. A imagem leva código que não roda. | Aceitável até cada módulo ganhar API; registrar no SBOM e retirar Paho já (COD-18). | P0.5 (novo) |
| COD-33 | mtr | Dívida técnica | Baixa | `MtrPdfGenerator.java:4-7`: PDF montado byte a byte, sem biblioteca; acentuação, acessibilidade e PDF/A ficam por conta própria. | Congelar; trocar por biblioteca quando o módulo de resíduos entrar no roteiro. | Fora do P0 (novo) |
| COD-34 | gis | Esqueleto | Baixa | `EpanetSimulationService.java:191-216`: sem solver; devolve aviso de solução não calculada. | Seguir `GIS-CADASTRO-TECNICO.md` §2.3 (view de compatibilidade e GIS-12). | P1.26 em diante (previsto) |
| COD-35 | intelligence | Qualidade | Baixa | gis depende de losses, que depende de quality; `DmaDistrictRepository` existe em `sigsan-gis` e em `sigsan-losses`. | Resolver na migração do núcleo GIS; uma entidade e um repositório de DMA. | GIS-01 (novo) |
| COD-36 | core | Dívida técnica | Baixa | `TenantAwareEntity.java:17-18` e `register_tenant_table` acrescentam `tenant_schema` a toda tabela de tenant; o dado repete o schema em que a linha já está. `TenantContextHolder` é ThreadLocal, sem propagação para tarefa assíncrona. | Avaliar a remoção da coluna na reescrita de `V1_x`; contexto explícito no worker da outbox. | P0.45 (novo) |
| COD-37 | frontend | Divergência de decisão | Baixa | `index.html:6-8`, `Layout.tsx:61`, `PageHeader.tsx:6`, `Login.tsx:40` com "SIGSAN" literal; `Alert.tsx:10` e `Badge.tsx:11-13` com cores Tailwind literais. | Pacote de marca e tokens semânticos. | P0.34, P0.36 (previsto) |
| COD-38 | frontend | Dependências | Baixa | Resolvidos no `package-lock.json`: `vite` 5.4.21 com `esbuild` 0.21.5 (aviso moderado conhecido do servidor de desenvolvimento, corrigido a partir do 0.25, que exige Vite 6 ou superior); `vitest` 1.6.1; `react` 18.3.1; `react-router-dom` 6.30.6; `maplibre-gl` 4.7.1; `lucide-react` 0.303.0; `tailwindcss` 3.4.19; `zod` 3.25.76. `axios` 1.20.0 está atual. Nenhuma vulnerabilidade alta conhecida aparente na leitura; a confirmação depende do `npm audit` da P0.3. | Subir Vite e Vitest de linha junto com `react-router` 7 e MapLibre; Lucide fixado na P0.46. | P0.17, P0.46, P0.3 (previsto em parte) |
| COD-39 | backend | Dependências | Baixa | Spring Boot 4.1.1 (suporte até 31/07/2027, D4); Paho 1.2.5 sem manutenção; `keycloak-admin-client` 26.0.12 desalinhado do servidor; JTS 1.19.0 fixado por compatibilidade com o geolatte. | Remover Paho e o cliente admin (COD-08, COD-18); conferir o patch mais recente da linha 4.1 no primeiro build do CI. | P0.3 (novo em parte) |
| COD-40 | raiz | Sobra | Baixa | Pastas `target/` com compilados antigos em `compliance/sigsan-lgpd`, `grs/sigsan-mtr`, `intelligence/sigsan-gis`, `-losses`, `-quality`, `platform/sigsan-platform` e `shared/sigsan-core`, dentro do Drive. Não versionadas. | Apagar com a saída do Drive. | P0.1 (novo) |
| COD-41 | raiz | Sobra | Baixa | 14 diretórios de módulo vazios (§2). `sigsan-auth` e `sigsan-tenant` repetem funções de `sigsan-platform`; `sigsan-events` contradiz a outbox no core (§3 do plano). | Apagar; cada módulo nasce com a tarefa que o cria (por exemplo `sigsan-fiscal` na P0.32). | P0.25 (novo) |
| COD-42 | registry | Desempenho | Baixa | `ConsumerUnitService.java:511-520` gera a matrícula com trava consultiva e `MAX(CAST(matricula AS bigint))` sobre a tabela a cada inclusão. | Sequência por tenant ou tabela de contador; medir na carga da P0.23. | P0.22 (novo) |

Pontos conferidos e sem achado: RLS forçada nas 76 tabelas de tenant; roles sem SUPERUSER, BYPASSRLS, CREATEDB nem
CREATEROLE, com verificação no init (`infra/postgres/init/sql/security.sql`); tenant só do JWT, com cabeçalho legado
divergente recusado (`JwtTenantFilter.java:60-81`); validação de emissor, audiência e assinatura (`SecurityConfig.java:79-96`);
ordenação por lista fechada e LIKE escapado no cadastro (`PageSpec.java:50-59`, `JpqlQuery.java:85-99`); erro 500 sem stack
trace (`application.yml:25-27`, `GlobalExceptionHandler.java:67-72`); actuator só com `health` sem detalhes; CORS por lista
de origens, sem credenciais; realm com PKCE S256, sem password grant e com política de senha de 12 caracteres; cifra
AES-256-GCM com nonce aleatório e AAD; nenhum `dangerouslySetInnerHTML` nem `localStorage` no frontend; `safeReturnPath`
contra redirecionamento aberto.

## 4. Recomendações para o P0

### 4.1 Ordem

O plano põe a P0.33 logo depois da P0.1. Recomenda-se intercalar uma limpeza curta (extensão da P0.25, que não tem
dependência) entre as duas: apagar antes de renomear reduz o volume da P0.33, evita renomear código morto e aproveita a
única janela em que os checksums das migrations podem mudar (§3 do plano, item "Renomeação"). Sequência sugerida:
P0.1 (com COD-29, COD-40) → limpeza (4.3) → P0.7 (COD-06, COD-28) → P0.33 (com COD-11 corrigido na tabela) → demais.

### 4.2 Manter

- `sigsan-core`: `Json`, `DocumentNumber`, `ApiException` (com códigos novos), `TenantContextHolder` (com contexto
  explícito para o worker).
- `sigsan-security`: `FieldCipher`, `BlindIndex`, `FieldProtector`, `TenantKeyProvider` como interface da P0.45;
  `EnvDerivedTenantKeyProvider` só em perfis de desenvolvimento listados.
- Migrations com `register_tenant_table`, redação de auditoria e `DatabaseHardeningTest`: são a base do isolamento.
- `JwtTenantFilter` e `TenantCatalog` (adaptados ao BFF e a uma role só de leitura).
- `sigsan-registry` e as telas do cadastro, com os testes E2E.
- Motores de billing (tarifa e conta), collection, metering, losses e o domínio do MTR (máquina de estados, cadeia de
  hash, proteção de documentos), depois de resolver a duplicação COD-07.

### 4.3 Apagar

- `KeycloakAdminService`, `KeycloakConfig` e `keycloak-admin-client` (COD-08).
- `ModuleRegistry` (COD-21) e `AuditListener.java` (COD-22).
- `SinirIntegrationService` e as chamadas em `MtrService` (COD-19); `GovBrSignatureService.sign` pode ficar só como
  cálculo de resumo, sem o nome "assinatura".
- `ScadaIntegrationService`, `ScadaTopic` e a dependência Paho (COD-18).
- Cópias das regras de consumo no billing (COD-07).
- Tabelas `users`, `roles`, `user_roles`, `tenant_metadata`, `documents` e `service_orders` de `V1_1`, e as sementes
  correspondentes do provisionamento (COD-09).
- Serviços TimescaleDB, Redis, Kafka, Zookeeper e Vault dev do compose (COD-06); Kubernetes e Terraform para
  `infra/_arquivado/` (COD-28).
- Diretórios de módulo vazios (COD-41) e pastas `target/` do Drive (COD-40).
- Métodos sem uso do provisionamento e a linha de log "DEBUG" (COD-23).

### 4.4 Reescrever

- `SecurityConfig` e cliente do frontend para o BFF: cookie `__Host-`, CSRF, CSP, limites e rate limit (COD-05).
- `TenantProvisioningService`: recusar schema existente e colisão de nome, catálogo escrito só pela role de plataforma,
  passos idempotentes por `tenant_id` e retomáveis pela outbox (COD-01, COD-02, COD-23).
- `DataSourceConfig`, `DatabaseMigrator` e `SigsanProperties`: credencial de migração só no job `migrate` (COD-03).
- `MultiTenantConnectionProvider`: contexto local à transação (COD-04).
- `ProblemResponses` e `GlobalExceptionHandler`: URN `urn:ziipsan:problem:`, tabela completa de códigos e teste de
  contrato que varre o código-fonte (COD-11).
- `RegistryRoles`: catálogo de permissões `recurso:ação` (COD-25).
- `LgpdEngineService`: chaves de cache com tenant e evict coerente, quando o módulo entrar no scan (COD-17).
- `application.yml`: recusar propriedade desconhecida, ou retirar os campos sem backend das telas (COD-10).

### 4.5 Acrescentar ao plano

- Na tabela de códigos da P0.33: `requisicao-invalida` → `invalid-request` (COD-11).
- Na P0.25: COD-07, COD-18, COD-21, COD-22, COD-41.
- Na P0.3: análise de dependências Maven, cobertura medida, enforcer ativo e tratamento de `scripts/it-db.env` pelo
  gitleaks (COD-30, COD-31).
- Na P0.16: `fail-on-unknown-properties` e `@Valid` nos DTOs de busca (COD-10, COD-24).

## 5. Inferências

Afirmações que dependem de execução ou de contexto não verificado neste levantamento:

1. Os números de teste (973 Java e 134 frontend, no plano) não foram reexecutados; as contagens deste documento são de
   anotações e de chamadas `it`/`test`, não de execuções. A suíte Java exige o PostgreSQL de integração em 55433.
2. A cobertura é "aparente": a proporção entre linhas de teste e de código é de cerca de 22% a 35% em LGPD, qualidade,
   GIS e perdas; 50% no MTR; 68% em collection; 83% a 105% em metering, billing, core e security; 11% no
   `sigsan-registry`, compensados pelos testes E2E e de schema do app. Sem JaCoCo não há número de cobertura.
3. A ausência de vulnerabilidade alta nas dependências é leitura das versões resolvidas, não resultado de `npm audit`
   nem de análise Maven.
4. O defeito de cache do LGPD (COD-17) está latente porque não há `@EnableCaching` nem scan do módulo; passa a valer
   quando o módulo entrar na aplicação.
5. O cenário de colisão do provisionamento (COD-02) é alcançável hoje só pelo `DevTenantBootstrap`; passa a ser exposto
   quando houver console de plataforma.
6. Os valores de marcador nos `Secret` do Kubernetes (COD-28) foram classificados por padrão de texto, sem exibição; não
   se verificou se algum deles já foi usado em ambiente real.
7. A reescrita de `V1_1` (COD-09) pressupõe que nenhum banco fora da máquina de desenvolvimento tenha dado a preservar,
   como afirmam o ADR-0033 e o §3 do plano.
8. O impacto de `ModuleRegistry` e das entidades de módulos inativos no tempo de subida não foi medido.
