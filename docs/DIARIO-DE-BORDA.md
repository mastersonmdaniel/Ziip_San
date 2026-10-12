# Diário de Borda — SIGSAN

Registro vivo do projeto: o que é, como está, o que travou e o que vem agora.
Última atualização: 12/10/2026

---

## 1. Identificação

| Item | Valor |
|---|---|
| Nome do produto | SIGSAN |
| Tipo | Plataforma SaaS vertical multi-empresa para saneamento básico |
| Raiz do repositório | `G:\Meu Drive\PROJETOS\Ziip SAN\sigsan`. Git desde 12/10/2026, com os dados do repositório fora do Drive (`C:\Users\shyka\.sigsan-git\sigsan.git`; no G: há só o arquivo `.git`). A cópia antiga em `C:\PROJETOS\Ziip SAN\sigsan` não é editada. O Docker não enxerga o drive `G:`: o build usa um espelho local, ver seção 13 |
| Organização | `br.com.sigsan` |
| Versão | `1.0.0-SNAPSHOT` |
| Build | Maven reactor, 13 módulos, Java 21 |

**Ordem de prioridade:** a única válida é a de `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (desde 12/10/2026). Perdas, qualidade da água, LGPD e MTR correm como trilha paralela ao comercial, não à frente dele.

**Fora do escopo:** POC, editais, blockchain no MTR, integração de escrita com ESRI.

---

## 2. ESTADO ATUAL: BUILD VERDE, 793 TESTES, 13 MÓDULOS (12/10/2026)

```
[INFO] SIGSAN Core ....... SUCCESS    [29 testes]
[INFO] SIGSAN Security ... SUCCESS    [18 testes]   cifra de campo AES-256-GCM + índice cego
[INFO] SIGSAN Platform ... SUCCESS    [16 testes]
[INFO] SIGSAN Quality .... SUCCESS    [sem testes]
[INFO] SIGSAN Losses ..... SUCCESS    [21 testes]
[INFO] SIGSAN GIS ........ SUCCESS    [31 testes]
[INFO] SIGSAN MTR ........ SUCCESS    [33 testes]
[INFO] SIGSAN LGPD ....... SUCCESS    [sem testes]
[INFO] SIGSAN Registry ... SUCCESS    [41 testes]   cadastro comercial
[INFO] SIGSAN Billing .... SUCCESS    [216 testes]  tarifa, conta, acréscimos
[INFO] SIGSAN Metering ... SUCCESS    [200 testes]  hidrômetro, leitura, consumo
[INFO] SIGSAN Collection . SUCCESS    [165 testes]  boleto, código de barras, pagamentos
[INFO] SIGSAN App ........ SUCCESS    [23 testes de integração, banco virgem]
[INFO] BUILD SUCCESS
```

Os módulos `registry`, `billing`, `metering` e `collection` são **regras de domínio puras mais
entidades e migrations**. Não têm API REST, serviço de aplicação nem orquestração: continua não
existindo `@SpringBootApplication` nem `@RestController` no repositório (seção 16.4). O frontend
(`frontend/`) compila e passa 95 testes, mas não tem backend para falar.

**Correção de registro:** a versão anterior deste diário declarava "build verde, 35 testes". Em
11/10/2026 o baseline real foi medido antes de qualquer alteração e **não estava verde**: o
`sigsan-platform` tinha 16 testes (não 12) e 1 falhava, o que derrubava o reactor e deixava os 5
módulos seguintes sem compilar. Além disso, o build verde escondia os defeitos estruturais da
seção 15, porque nenhum teste juntava os módulos nem rodava a migration de core num banco virgem.

A suíte de integração de `platform/sigsan-app` (`FreshInstallTest`) agora faz o que o primeiro
deploy faria: migra o core num banco virgem, provisiona um tenant com **todos** os módulos e
valida o mapeamento JPA das entidades contra as tabelas criadas (eram 25 nesta data; 66 em 12/10/2026).

Stack local: os containers do compose precisam ser iniciados (`docker compose up -d`). Em
11/10/2026 só o `sigsan-postgres-primary` foi religado, o que basta para os testes.

O isolamento multi-tenant é fato testado contra PostgreSQL real, e o provisionamento de tenant é
verificado de ponta a ponta, incluindo as tabelas de todos os módulos (seção 15).

---

## 3. Decisões de arquitetura

1. **Multi-tenancy:** um PostgreSQL, um schema por empresa, RLS e `tenant_id`.
2. **Criptografia:** AES-256-GCM por tenant, DEK protegida por KEK no Vault.
3. **Stack:** Java 21 / Spring Boot 3.2, React + TypeScript, React Native + Capacitor.
4. **OSS/self-hosted:** bare metal para o núcleo, GPU em nuvem spot só para treino.
5. **MTR:** SINIR, OAuth2 Gov.br, ICP-Brasil, hash chain. Sem blockchain na v1.
6. **ESRI Connector:** somente leitura.
7. **GeoTools:** repositório OSGeo declarado no POM raiz; não existe no Maven Central.
8. **Duas roles de banco.** `sigsan_app` é a aplicação; `sigsan_migrator` roda DDL. Ver seção 5, esta é a decisão mais importante do projeto.

---

## 4. O achado mais importante: a RLS estava inerte

Este é o único registro que importa ler com atenção.

O projeto declarava Row Level Security em todas as tabelas de tenant. As policies eram
corretas. **Nenhuma delas jamais teria sido aplicada em produção.**

### Por quê

`POSTGRES_USER` do container cria a role da aplicação como **superusuário**. Superusuário
ignora RLS por definição no PostgreSQL. some com `BYPASSRLS` ligado.

Verificado antes da correção:

```
 current_user | usesuper | usebypassrls
--------------+----------+----------------
 sigsan_app   | t        | t
```

Com `usesuper = t` e `usebypassrls = t`, todo `SELECT` do `sigsan_app` via as policies sem
verificá-las. Qualquer tenant leria os dados de todos os outros. Em um SaaS de saneamento,
isso é dado de cliente, consumo e localização de infraestrutura da concessionária.

**Nenhum teste pegaria isso**, porque o SQL estava sintaticamente correto e o build passava.

### A correção

`infra/postgres/init/01-security-roles.sql` agora:

1. cria as extensões antes de qualquer migration;
2. cria `sigsan_migrator` para DDL;
3. aplica `ALTER ROLE sigsan_app NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE INHERIT`;
4. fixa `search_path = public` na role da aplicação;
5. cria `public.current_tenant_id()`, que lê a GUC `app.current_tenant`;
6. verifica ao final e emite `NOTICE` ou `WARNING` se a role voltar a ter bypass.

Estado verificado depois:

```
 current_user | usesuper | usebypassrls
--------------+----------+----------------
 sigsan_app   | f        | f
```

### Três bugs de produção que o teste revelou

**a) Schema de tenant pertencia à role da aplicação.**

`CREATE SCHEMA %s AUTHORIZATION sigsan_app` fazia a aplicação ser dona do schema. Dono de
tabela ignora RLS a menos que a tabela tenha `FORCE ROW LEVEL SECURITY`. Corrigido para
`AUTHORIZATION sigsan_migrator`, com a aplicação recebendo apenas `USAGE`.

**b) Faltava `GRANT` explícito nas tabelas depois das migrations.**

Só havia `ALTER DEFAULT PRIVILEGES`, que vale para objetos criados depois do `ALTER` e depende
de a migration ter rodado com a mesma role que concedeu. Adicionado
`grantTenantTablePrivileges()`, chamado logo após `runTenantMigrations()`.

**c) Flyway de tenant rodava sem `public` no search_path.**

As migrations de tenant chamam `uuid_generate_v4()`, que vive em `public`. Com
`.schemas(schemaName)` apenas, toda migration de tenant falharia com
`function uuid_generate_v4() does not exist`. Corrigido para
`.schemas(schemaName, PUBLIC_SCHEMA).defaultSchema(schemaName)`.

**Nenhum dos três falharia em build. Os três quebrariam todo provisionamento de tenant em produção.**

---

## 5. O segundo achado: provisionamento criava tenant vazio

Ao testar `TenantProvisioningService` contra banco real, apareceu um segundo bug da mesma família
do primeiro: **falha silenciosa**.

### O Flyway não tinha migration nenhuma

O serviço apontava para `.locations("db/migration/tenant")`, e essa pasta existia mas estava
**vazia**. O template de tenant (`V1_1__create_tenant_schema.sql`, com as 8 tabelas e as policies)
estava em `sigsan-core/db/migration`, num location que o provisionamento nunca lia.

O Flyway, sem migration no location, **não falha**. Aplica zero migrations, cria a tabela de
histórico, e devolve sucesso. Resultado: todo tenant provisionado ficava com schema criado e
**zero tabelas**, sem erro em log, sem exceção, com `success = true` na resposta.

Confirmado antes da correção:

```
SELECT count(*) FROM information_schema.tables WHERE table_schema='probe_flyway';
 tabelas
---------
       0
```

### Correções

1. Migration movida para `platform/sigsan-platform/src/main/resources/db/migration/tenant/`.
2. Removida a cópia de `sigsan-core`, que era a mesma migration num location enganoso.
3. Validação explícita depois do `migrate()`: se a contagem de tabelas for zero, lança
   `IllegalStateException`. **Falha barulhenta substitui falha silenciosa.**

### Mais quatro bugs que só apareceram ao rodar de verdade

**a) O serviço não conseguia criar o schema.** O `DataSource` da aplicação é `sigsan_app`, que é
`NOCREATEDB` de propósito. `CREATE SCHEMA ... AUTHORIZATION sigsan_migrator` falhava com
`must be member of role "sigsan_migrator"`. Criado um `migratorDataSource` dedicado: DDL de tenant
roda como migrador, runtime continua como aplicação.

**b) Flyway 10 exige módulo de banco separado.** `flyway-core` sozinho falha com
`Unsupported Database: PostgreSQL 15.19`. Adicionado `flyway-database-postgresql`.

**c) Faltava PostGIS.** A migration de tenant declara `GEOMETRY(POINT, 4674)` em `addresses` e
`GEOMETRY(LINESTRING, 4674)` em `service_orders.gps_track`, mas a extensão não era criada. O
provisionamento morria com `type "geometry" does not exist`. Trocada a imagem do compose para
`postgis/postgis:15-3.4`, que já traz a extensão.

**d) `audit_log` incompatível com a entidade.** `AuditLog` estende `BaseEntity`, que declara
`updated_at`, `version` e `updated_by`, mas a migration criava só `created_at` e `created_by`.
O JPA quebraria em runtime com `column "updated_by" of relation "audit_log" does not exist`.
Migration alinhada com a entidade.

**e) Seed não era idempotente.** Reprovisionar o mesmo tenant falhava em
`CREATE SCHEMA ... already exists` e depois em violação de chave única em `roles`. Corrigido com
`CREATE SCHEMA IF NOT EXISTS` e `ON CONFLICT (tenant_id, code) DO UPDATE`.

**f) O seed era barrado pela própria RLS.** `new row violates row-level security policy for table
"roles"`: o seed roda como `sigsan_migrator`, que também respeita RLS, e a GUC
`app.current_tenant` estava vazia. Reescrito para usar `SET LOCAL` dentro de uma transação
explícita numa única conexão. Não se adicionou `BYPASSRLS`, que foi removido de propósito.

**Nenhum desses seis falharia em build.** Todos quebram o provisionamento em produção.

### Os 12 testes de provisionamento

| Teste | O que prova |
|---|---|
| `provisionsSchemaWithAllTables` | schema criado com as 8 tabelas |
| `recordsFlywayHistory` | histórico do Flyway existe, logo a migration rodou |
| `registersTenantInCatalog` | tenant registrado em `public.tenant_catalog` |
| `createsDefaultRoles` | os 4 papéis (ADMIN, OPERATOR, MANAGER, VIEWER) |
| `createsAdminUserWithAdminRole` | admin criado e vinculado ao papel ADMIN |
| `writesTenantMetadata` | metadata gravada no schema do tenant |
| `enablesRowLevelSecurityOnEveryTable` | toda tabela de negócio tem RLS |
| `appRoleIsNotSchemaOwner` | `sigsan_app` não é dona do schema |
| `appRoleCanReadTenantTables` | aplicação lê sem erro de privilégio, e vê zero linhas |
| `rejectsSqlInjectionInSchemaName` | nome malicioso é neutralizado |
| `reprovisioningUpdatesCatalog` | reprovisionar atualiza, não duplica |
| `provisionedTenantIsIsolatedFromOthers` | dono vê 4 papéis, estranho vê zero |

`flyway_schema_history` é a única tabela sem RLS, e está correto: guarda histórico de migration, que
por definição é visível ao deploy.

---

## 6. Prova de isolamento entre tenants

`TenantRlsIsolationTest`, 8 testes contra o PostgreSQL do compose. Sem mock.

| Teste | O que prova |
|---|---|
| `tenantSeesOwnOrders` | cada tenant enxerga a própria ordem |
| `withoutTenantContextNothingIsVisible` | contexto ausente nega tudo, nunca libera tudo |
| `tenantMetadataIsAlsoIsolated` | `tenant_metadata` também é isolada |
| `crossTenantInsertIsRejected` | INSERT com `tenant_id` de outro é barrado pela policy |
| `crossTenantReadIsBlocked` | A não lê ordens de B mesmo no mesmo schema |
| `unknownTenantSeesNothing` | UUID inexistente não vaza nada |
| `tenantContextDoesNotLeakAcrossConnections` | GUC não sobrevive entre conexões |
| `javaContextIsIndependentPerThread` | contexto Java não cruza thread |

O teste usa `sigsan_migrator` para o DDL e `sigsan_app` para o `SELECT`. Isso é proposital:
prova que a RLS vale **para a role que a aplicação realmente usa**, não para um superusuário.

Sobre a transação: `set_config('app.current_tenant', ..., true)` é local à transação e morre no
commit. O helper faz `set_config`, executa a leitura e faz `rollback`, senão a GUC vazaria para
a próxima conexão reaproveitada do pool.

Configuração por ambiente: `SIGSAN_TEST_JDBC_URL`, `SIGSAN_TEST_DB_USER`,
`SIGSAN_TEST_DB_PASSWORD`, `SIGSAN_TEST_MIGRATOR_USER`, `SIGSAN_TEST_MIGRATOR_PASSWORD`.

---

## 7. Bug corrigido no filtro de tenant

`TenantFilter` chamava `UUID.fromString(header)` incondicionalmente. Como o header `X-Tenant-ID`
carrega **nome de schema** (`tenant_alpha`), e não UUID, **toda requisição autenticada quebrava**
com `IllegalArgumentException`.

Pior: o header alimenta o `search_path` do Hibernate, então um valor malformado seria injeção de
SQL via nome de schema. Não havia validação.

O filtro agora aceita as duas formas, valida o formato do schema contra
`^[A-Za-z_][A-Za-z0-9_]{0,62}$`, tem header separado `X-Tenant-UUID`, e registra e descarta
valores malformados. `TenantFilterTest` cobre os 9 casos, incluindo a tentativa de injeção
`public; DROP TABLE users--`.

---

## 8. Infraestrutura: estado

> **Desatualizado (12/10/2026):** esta tabela é de quando o compose subia 9 serviços. Desde a E3 só
> `postgres-primary` e `keycloak` são usados (`scripts/dev-up.sh`); `postgres-replica` foi removido (não
> replicava) e o Keycloak é o 26.8.0 com banco próprio. Ver seção 18.

9 serviços, todos saudáveis.

| Serviço | Estado | Nota |
|---|---|---|
| postgres-primary | healthy | init de segurança aplicado |
| postgres-replica | up | sem healthcheck |
| timescaledb | healthy | |
| redis | healthy | |
| kafka | healthy | |
| zookeeper | healthy | healthcheck corrigido |
| minio (SeaweedFS) | healthy | |
| keycloak | healthy | healthcheck corrigido |
| vault | healthy | healthcheck corrigido |

### Healthchecks que mentiam

Os três serviços estavam `unhealthy` desde o início, mas **funcionais**. O log do Keycloak mostra
`started in 8.728s. Listening on: http://0.0.0.0:8080`. O problema era o healthcheck, não o serviço.

| Serviço | Causa do falso negativo | Correção |
|---|---|---|
| keycloak | `curl: command not found`, imagem não tem curl | TCP contra `/realms/master`, sem dependência externa |
| vault | `vault status` tentava HTTPS contra servidor HTTP | `VAULT_ADDR=http://127.0.0.1:8200 vault status` |
| zookeeper | `zkServer.sh` fora do PATH, e `ruok` desabilitado | verificação de conexão TCP na 2181 |

Vale a lição: **healthcheck vermelho não é sempre serviço quebrado.** Verificar os logs antes de
caçar bug no serviço.

### YAMLs

Os 14 arquivos de `infra/kubernetes` e o `docker-compose.yml` passam em parser YAML. O
`grafana.yaml` tinha um `}` indevido no fim do dashboard JSON, que impedia qualquer parse.

---

## 9. Stubs honestos: o que compila mas não faz o trabalho

Nenhum destes finge sucesso. **Não consuma nenhum deles achando que funciona.**

### EPANET (`sigsan-gis`)
`runEpanetWasm` **não é solver hidráulico.** Faz contagem de nós, enlaces e comprimento de
tubulação. `nodeResults` e `linkResults` voltam **vazios**, não zero. `solverExecuted = false`,
`success` inicia `false`, e um aviso entra no resultado. Campos hidráulicos são `null` quando não
computados, e `null` significa "não calculado". `analyzeScenario` devolve `scenarioModelled=false`.

### MTR (`grs-mtr`)
`SinirIntegrationService.syncMtr` monta e valida payload e devolve `PENDING` com motivo; sem
endpoint e credenciais não há POST, e `SYNCED` é inalcançável. `GovBrSignatureService.sign`
devolve `PENDING` com assinatura nula, e o serviço não é chamado em lugar nenhum do repositório.
`MtrPdfGenerator` gera PDF de verdade, validado com parse: `pages=1` e texto correto.

### Perdas (`sigsan-losses`)
`getSystemInputVolume`, `getBilledAuthorizedConsumption`, `getUnbilledAuthorizedConsumption` e
`estimateUnauthorizedConsumption` devolvem **constantes hardcoded** marcadas `// Placeholder`. O
balanço hídrico IWA/AWWA está estruturado, mas a entrada de dados é fictícia. **Enquanto isso
durar, nenhuma análise de perdas é confiável.**

---

## 10. Defeitos de lógica corrigidos

| Onde | Defeito | Efeito |
|---|---|---|
| `MtrService` | `Map.of()` com chave duplicada | `IllegalArgumentException` em runtime |
| `LgpdEngineService`, `LabService` | `AuditAction.valueOf("GRANT")` com verbo inexistente | exceção em todos os audit logs |
| `LgpdEngineService` | `bytesToHex` duplicado | não compilava |
| `ScadaIntegrationService` | `MemoryPersistence` do pacote errado (Paho, não Hibernate) | não compilava |
| `QualitySample` | campo `status` duplicado | não compilava |
| `ConsentRecord` | `version` colidia com `BaseEntity.getVersion():Long` | não compilava |
| `LossesService` | parâmetros `periodStart, periodEnd` onde a assinatura espera `start, end` | resultado errado |
| `MtrService` | `routeGeometry` como `String` num record que espera `LineString` | contrato do DTO errado |
| Múltiplos | records chamados com `getX()` em vez de `x()` | não compilava |
| Múltiplos | `.builder()` em entidade sem `@Builder` | não compilava |

### Armadilha do Lombok `@Builder`

Classes que herdam de `BaseEntity` ou `TenantAwareEntity` **não podem receber `@Builder`**: o
Lombok descarta silenciosamente os campos herdados (`id`, `createdAt`, `updatedAt`, `version`).
A identidade fica nula em runtime sem nenhum erro. A correção é reescrever o call site como
cadeia de setters. Feito em Losses, MTR e LGPD.

### Dano que compila

Um script de correção em lote inseriu ponto e vírgula em 43 arquivos. Parte do estrago
**compilava sem erro**, dentro de strings e blocos de texto `"""`:

| Corrompido | Correto |
|---|---|
| `"https:; //app.sigsan.com.br/*"` | `"https://app.sigsan.com.br/*"` |
| `"${scada.mqtt.broker:tcp:; //localhost:1883}"` | `"${scada.mqtt.broker:tcp://localhost:1883}"` |
| `ON CONFLICT (schema_name) DO UPDATE SET;` | `ON CONFLICT (schema_name) DO UPDATE SET` |

As duas primeiras rejeitariam todo redirect de tenant e impediriam o SCADA de conectar.

**Regra permanente: build verde não prova que o código está correto.** Depois de qualquer
correção em lote, revisar strings e blocos de texto à mão. E **reparo em lote com regex sobre
código é mais perigoso que o defeito original**: foram 1.640 edições, boa parte errada.

---

## 11. Pendências

### 11.1 Cobertura de teste

793 testes (ver seção 2). Ainda **sem nenhum teste**: `intelligence/sigsan-quality` (`LabService`,
`ScadaIntegrationService`) e `compliance/sigsan-lgpd` (`LgpdEngineService` e os 4 engines).
`MtrService`, `LossesService` (balanço e pressão) e o escritor `.inp` do EPANET passaram a ter
teste em 11 e 12/10/2026.

### 11.2 Não existe aplicação executável

Nenhuma `@SpringBootApplication` e nenhum `@RestController`. Os módulos de domínio (`registry`,
`billing`, `metering`, `collection`) existem e estão testados, mas não há camada de serviço, API
HTTP nem entry point. `commercial/{accounting,dunning,portal}` e
`operations/{assets,field,maintenance,reading}` continuam vazios, sem POM. O `PlatformConfig` foi
ajustado (sem `validate` no boot, sem cache Redis inexistente) mas **nunca foi exercitado num
contexto Spring**.

### 11.3 Ferramentas ausentes

- `mvn` não instalado; build roda no container.
- `terraform` não instalado.

Testcontainers também não funciona: o Maven roda em container, o socket do Docker no Windows é
named pipe e não monta. O teste de RLS foi directing para o PostgreSQL do compose
(`host.docker.internal:5432`), o que é melhor de qualquer forma porque testa a stack real.

### 11.4 Dívidas técnicas

- `PressureAnalysis.isAnomaly` gera `getIsAnomaly()`, desalinhado com o nome do campo.
- `EpanetPipe.status` é `boolean` com setter em `int` e getter manual, porque Lombok geraria
  `isStatus()` e quebraria o escritor de `.inp`.
- `EpanetSimulationService` aninha `Statistic`, `DemandModel`, `LinkReport`, `NodeReport`,
  `SimulationOptions` e `SimulationRequest`; as classes top-level referenciam estes como
  `EpanetSimulationService.X`.
- Import duplicado de `AuditLogRepository` em `TenantProvisioningService`.
- `realm.setLoginWithEmailAllowed(true)` chamado duas vezes em `KeycloakAdminService`.
- Frontend não integrado ao build Maven.

---

## 12. Próximos passos

**Substituída em 12/10/2026.** As antigas Fases A–D desta seção ficaram desatualizadas (parte já feita, e a ordem
conflitava com `00` §3, `14` §3 e `15c` §3). O roadmap canônico é `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`:

- **Fundações (ações do PDCA):** E0 git e linha de base → E1 scripts, banco de teste, POMs → E2 Spring Boot 4.1.1 →
  E3 banco e tenant (C1, C2) + JSON e falhas silenciosas → E4 auditoria sem dado pessoal e cifra (C4) →
  E5 aplicação executável, JWT, API do cadastro (C3, C5) → E6 CI e documentação. Alvo: 27/11/2026.
- **Produto:** R3 tarifa social → R1 Vault → R4a alíquotas → GRS → R4 ciclo e conta → R5a conciliação / R6 SINISA 2027
  → R7 protocolo e OS com bloqueio de corte → R5b/c Pix → R8 cobrança → R9 GIS e perdas.

O andamento de cada etapa é registrado na seção 18.

---

## 13. Comandos

O fonte está no Google Drive (`G:`), que o Docker Desktop **não enxerga**: montar `G:\...` no
container deixa `/app` vazio e o Maven responde "there is no POM in this directory". Os scripts em
`scripts/` (Git Bash) espelham o fonte para uma pasta local e montam o espelho. Edite sempre no `G:`.

```bash
# Build e testes de todo o reactor
scripts/build.sh clean test

# Só um módulo
scripts/build.sh -pl shared/sigsan-core test

# Instalação do zero (precisa de um banco virgem: recrie antes de cada execução)
scripts/reset-it-db.sh
scripts/build.sh -pl platform/sigsan-app -am test -Dtest=FreshInstallTest -Dsurefire.failIfNoSpecifiedTests=false
```

`reset-it-db.sh` sobe um PostgreSQL descartável (`sigsan-it-pg`, porta 55433, mesma imagem e mesmo
init de segurança do compose). O `FreshInstallTest` não pode rodar no banco de desenvolvimento: a
migration de core roda sobre `public`, e o Flyway recusa uma migration já aplicada e depois alterada.

```powershell
# Stack
docker compose up -d
docker compose ps

# VERIFICAR QUE A RLS ESTÁ ATIVA — o comando mais importante do projeto
docker exec sigsan-postgres-primary psql -U sigsan_app -d sigsan -c "SELECT usesuper, usebypassrls FROM pg_user WHERE usename='sigsan_app';"
# esperado: f | f. Se vier t, a RLS está inerte e há vazamento entre tenants.

# Recriar o banco do zero (aplica o init de segurança)
docker compose down -v
docker compose up -d

# Criar o volume de cache do Maven (só uma vez)
docker volume create sigsan-m2
```

---

## 14. Registro de decisões

| Data | Decisão | Motivo |
|---|---|---|
| 09/10/2026 | MinIO substituído por SeaweedFS | imagem do MinIO indisponível no registry |
| 09/10/2026 | Build roda em container Maven | `mvn` não instalado |
| 09/10/2026 | Repositório OSGeo no POM raiz | GeoTools não está no Maven Central |
| 09/10/2026 | `maven-compiler-plugin` em `<build><plugins>` | Lombok não processava de `pluginManagement` |
| 09/10/2026 | Volume `sigsan-m2` para cache | build rebaixava tudo a cada run |
| 09/10/2026 | Nenhum `@Builder` em entidade com herança | Lombok descarta campos herdados |
| 09/10/2026 | Stubs de EPANET/SINIR/assinatura honestos | build verde não pode mascarar trabalho não feito |
| 09/10/2026 | Placeholders hídricos mantidos como constantes | marcados `// Placeholder`, sem risco de parecerem dados |
| 09/10/2026 | **Duas roles: `sigsan_app` e `sigsan_migrator`** | app sem SUPERUSER e sem BYPASSRLS, senão RLS é inerte |
| 09/10/2026 | Schema de tenant pertence ao migrador | dono de tabela ignora RLS sem FORCE |
| 09/10/2026 | GRANT explícito após migrations | `DEFAULT PRIVILEGES` não cobre tabelas já criadas |
| 09/10/2026 | `public` no search_path do Flyway de tenant | sem isso `uuid_generate_v4()` não resolve |
| 09/10/2026 | Teste de RLS usa o PostgreSQL do compose | Testcontainers não alcança o Docker no Windows |
| 09/10/2026 | Filtro valida formato de schema | header alimenta search_path; sem validação é injeção |
| 09/10/2026 | Migration de tenant movida para o location do Flyway | pasta vazia fazia todo tenant nascer sem tabela |
| 09/10/2026 | Validação de tabela pós-migrate | falha barulhenta em vez de tenant silenciosamente vazio |
| 09/10/2026 | `migratorDataSource` separado do `dataSource` | `sigsan_app` é NOCREATEDB de propósito; DDL exige migrador |
| 09/10/2026 | `flyway-database-postgresql` adicionado | Flyway 10 exige módulo de banco separado do core |
| 09/10/2026 | Imagem do compose trocada para `postgis/postgis:15-3.4` | migration declara `GEOMETRY` e falhava sem a extensão |
| 09/10/2026 | Seed usa `SET LOCAL` em transação única | RLS barra o seed; `BYPASSRLS` continua removido |
| 09/10/2026 | Seed idempotente com `IF NOT EXISTS` e `ON CONFLICT` | reprovisionar é operação legítima |
| 11/10/2026 | GSAN e GEOSAN usados só como fonte de domínio (conceitos, regras, fórmulas, catálogos) | decisão do Daniel: são abertos e podem ser usados; a licença exata não está em arquivo (GSAN) / é GPL-2.0 (GEOSAN). Reescrever em código próprio, sem copiar código, SQL, `.jrxml` nem layouts. Levantamento em `docs/levantamento/` |
| 11/10/2026 | Fase 0 antes de qualquer feature comercial | decisão do Daniel: corrigir os defeitos estruturais da seção 15 primeiro |
| 11/10/2026 | Criar o módulo `commercial/sigsan-registry` para o cadastro | decisão do Daniel: cliente, imóvel, ligação, hidrômetro e hierarquia territorial; ainda não criado |
| 11/10/2026 | Arrecadação cobre os ~20 maiores bancos de varejo (BB, Bradesco, Sicoob/Bancoob, Caixa, Itaú, Santander etc.) | decisão do Daniel: parsers CNAB/FEBRABAN por banco, `barcode_layout` por tenant; PIX ainda a confirmar |
| 11/10/2026 | Novo módulo `platform/sigsan-app` | único lugar onde migrations, entidades e provisionamento rodam juntos; cada módulo isolado passava sem enxergar os outros |
| 11/10/2026 | Migrations de módulo moram em `db/migration/tenant/` do próprio módulo, versões `V2_x` únicas | `V1__` de vários módulos colidiam na versão 1 e nunca rodavam num tenant |
| 11/10/2026 | Migration de core em `db/migration/core/` | o Flyway varre o location de forma recursiva: `db/migration` aplicaria as migrations de tenant em `public` |
| 11/10/2026 | `register_tenant_table()` (V1_2) aplica RLS com FORCE, policy, auditoria e colunas de entidade | cada módulo repetia isso à mão e esquecia passos diferentes |
| 11/10/2026 | Seeds de migration usam o placeholder Flyway `${tenant_id}` | dentro de uma migration `current_tenant_id()` é NULL |
| 11/10/2026 | `current_tenant_id()` definida só no init de segurança | a `sigsan_migrator` não pode substituir função de outro dono |
| 11/10/2026 | Removidas `set_current_tenant`, `create_tenant_schema`, `run_tenant_migration` | sem uso; a primeira vazava o tenant entre conexões do pool, a segunda criava schema com dono `sigsan_app` |
| 11/10/2026 | `audit_log` é somente-inserção para `sigsan_app` | trilha que a aplicação pode alterar não é trilha |
| 11/10/2026 | Séries temporais (`scada_data_point`, `epanet_*_result`) sem trigger de auditoria | auditar cada leitura em JSONB duplica o armazenamento sem valor |
| 11/10/2026 | Removido o seed de 4 DMAs fictícios e o de classes de resíduo do MTR | dado inventado que parece real alimentaria o balanço hídrico e iria num documento legal |
| 11/10/2026 | Mantido o seed de parâmetros da Portaria MS 888, **não validado** | exige revisão por especialista em qualidade da água antes de uso em produção |
| 11/10/2026 | Balanço IWA em calculador puro (`WaterBalanceCalculator`) e aprovação bloqueada com entrada fictícia | o ILI misturava volume do período com UARL diário; a água não faturada omitia o consumo autorizado não faturado |
| 11/10/2026 | `BaseEntity` com `@Id` e `@Version`; `version` nasce `null` | entidade sem `@Id` impede o Hibernate de subir; `@Version` dá concorrência otimista real |
| 11/10/2026 | Colunas `jsonb` mapeadas como `String` levam `@JdbcTypeCode(SqlTypes.JSON)` | sem isso o Hibernate valida como `varchar` e o Postgres recusa a gravação |
| 11/10/2026 | `hibernate-spatial` nos 4 módulos com geometria JTS | sem ele o Hibernate não mapeia `Point`/`Polygon` |
| 12/10/2026 | Seed de parâmetros de qualidade da água removido (era o último seed) | 16 de 28 linhas divergiam da Portaria 888/2021 (relatório 09). Catálogo vazio até ser carregado da norma, com modelo de regra de avaliação |
| 12/10/2026 | `.inp` do EPANET validado contra o EPANET real (`scripts/validate-inp.sh`, WNTR) | o parser recusava `Statistic AVERAGE`: só `AVERAGED`. Teste de texto não pega isso. `Statistic` padrão passa a `NONE` |
| 12/10/2026 | Construtor de topologia EPANET sem valores inventados (`NetworkTopologyBuilder`, `InpWriter`) | cota, bomba, válvula e tanque ausentes viram problema que **impede** gerar o arquivo, em vez de cota 0 / 50 kW / PRV 50 |
| 12/10/2026 | `Json.orDefault()` no core para todo `jsonb` | `Map.toString()` gerava `{a=b}`; o provisionamento com endereço quebrava (19 ocorrências em 3 módulos) |
| 12/10/2026 | MTR: `MtrStateMachine`, `HashChain` e `MtrCanonical` puros | `receiveMtr` falhava sempre; hash dependia de campos mutáveis, do charset da plataforma e caía em UUID aleatório |
| 12/10/2026 | Documento de cliente só cifrado + índice cego + máscara; busca por documento via `POST`, nunca na URL | LGPD; o contrato do relatório 07 punha CPF em `GET ?q=`, que vai para o log do servidor |
| 12/10/2026 | Novos módulos `shared/sigsan-security`, `commercial/sigsan-registry|billing|collection`, `operations/sigsan-metering` | fatias de domínio independentes; versões Flyway V3_x (registry), V4_x (billing), V5_x (metering), V6_x (collection) |
| 12/10/2026 | Faturamento: juros por dia, não por mês de calendário; tarifa social e tributos como parâmetro do tenant | decisão de implementação do módulo `billing`; a NR 13 fala em faixas "não cumulativas" e ainda não foi tratada |
| 12/10/2026 | Relatório do ChatGPT (`ANALISE-FONTES-OPEN-ENRIQUECIMENTO-ZIIP-SAN.md`) é visão de produto, não especificação | avaliado em `docs/levantamento/15a`, `15b`, `15c`: 24 de 36 afirmações sobre o GSAN confirmadas; leu errado o `frmAlteraConsumoPorPoligono` (é demanda do modelo hidráulico, não consumo faturado); sem nenhuma norma regulatória |

---

## 15. Fase 0: defeitos estruturais que o build verde escondia (11/10/2026)

Origem: o levantamento do GSAN e do GEOSAN (`docs/levantamento/`) apontou riscos no SIGSAN. Cada
um foi conferido no código, e a maior parte só apareceu quando o teste de instalação do zero
(`platform/sigsan-app/FreshInstallTest`) passou a juntar todos os módulos num banco virgem. **Nenhum
destes quebrava o build.** Todos quebrariam o primeiro deploy.

### 15.1 Migrations

| Defeito | Efeito | Correção |
|---|---|---|
| Migration de core nunca tinha rodado. O banco de dev não tem `public.flyway_schema_history`; as tabelas vinham de DDL copiado dentro do teste | `must be owner of function current_tenant_id` num banco virgem | Funções legadas removidas; `GRANT` explícito à aplicação |
| `CoreMigrationRunner` varria `classpath:db/migration` de forma recursiva | aplicaria o template de tenant (`users`, `roles`, `service_orders`...) em `public` | location `db/migration/core` |
| `V1__` de cada módulo na mesma versão, fora do location de tenant | tabelas de GIS, perdas, qualidade e MTR nunca criadas num tenant | `db/migration/tenant/V2_1`..`V2_5`, ordem por dependência |
| Seeds com `current_tenant_id()` (NULL numa migration) e cast para enum que nunca existiu | migration falharia | placeholder `${tenant_id}`; cast removido |
| FKs compostas `(tenant_id, id)` sem `UNIQUE` no pai | `there is no unique constraint matching given keys` | `UNIQUE (tenant_id, id)` nos pais |
| RLS sem `FORCE`, auditoria só em alguns módulos, sem `version`/`tenant_schema` | dono ignora RLS; entidade-base sem coluna | `register_tenant_table()` |
| `ENABLE ROW LEVEL SECURITY.` com ponto final; colunas duplicadas em `hydraulic_indicators`; `CHAR(1)` com `CHECK IN ('I','II','III')` | SQL inválido / valor que nunca cabia | corrigidos |
| 9 entidades sem tabela (LGPD: 4; SCADA e laboratório: 5) | `relation ... does not exist` | `V2_5` (LGPD) e extensão de `V2_1` |
| `ip_address INET` em `audit_log` com entidade `String` | validação de esquema falha | `VARCHAR(45)` |

### 15.2 Entidades JPA

- `BaseEntity` sem `@Id` e sem `@Version`, e 25 classes `@Entity` sem nenhum `@Id` no repositório.
- `RetentionSchedule` sem herança nenhuma e com índice em coluna que não declarava.
- 68 colunas `jsonb` mapeadas como `String` sem `@JdbcTypeCode(SqlTypes.JSON)`.
- `@Enumerated` em campo `String` (`ScadaTag.status`, `LabInstrument.status`, `WaterBalance.status`):
  o Hibernate recusa o mapeamento.
- `@JoinColumn(name = "dma_district_id")` em 3 entidades, mas a coluna é `dma_id`.
- `hazard_class` com `length = 1` para um enum com `II` e `III`.
- `hibernate-spatial` ausente nos módulos com geometria JTS.
- `@Index` com nome de propriedade Java no lugar do nome da coluna.

### 15.3 Domínio: balanço hídrico (`LossesService`)

- **ILI com unidades erradas:** dividia as perdas reais do **período** (m³) pelo UARL em **m³/dia**.
  Só valia para período de 1 dia: num mês, o ILI saía 30 vezes maior.
- **Água não faturada errada:** o código somava perdas aparentes e reais (que a IWA chama de
  *water losses*) e omitia o consumo autorizado não faturado. Com os números do cenário-base:
  2.800 m³ no código, 3.000 m³ correto.
- Perdas reais negativas eram zeradas em silêncio, o que fazia um balanço que não fecha parecer
  fechado. Agora o valor negativo é devolvido com aviso e o ILI não é calculado.
- `Lp`, `Lm`, `Nc` e `P` assumiam valores inventados quando faltavam dados. Agora faltar dado
  deixa UARL e ILI nulos, com aviso. Única exceção explícita: `Lp` ausente assume 0 km e avisa.
- `calculatedBy` recebia o id do **tenant**. Agora é `null` até existir usuário no contexto.
- O UARL anual era gravado em m³/dia. Agora em m³/ano.
- `WaterBalanceCalculatorTest`: 15 testes com valores calculados à mão (ex.: 2.560 ÷ 882 = 2,9025).
- Aprovar um balanço com entradas fictícias ou inconsistentes agora lança exceção.

### 15.4 Dados fabricados removidos

- 4 DMAs de exemplo ("DMA Centro", 3.200 ligações...) semeados em todo tenant.
- Classes de resíduo do MTR com códigos inventados e rótulos errados (Grupo D de saúde marcado
  como perigoso; um código repetido). O catálogo deve vir da lista oficial do IBAMA, em migration
  própria, conferido por responsável técnico.
- **Mantido, mas não validado:** parâmetros da Portaria MS 888 (`quality_parameter`). Os limites
  precisam de revisão por especialista em qualidade da água.

### 15.5 Ainda aberto

> **Resolvido depois (12/10/2026):** escritor `.inp`, `analyzePressure`, `LossesService.auditLog`,
> `PlatformConfig` e a falta de `@SpringBootApplication` (seções 16.2 e 18, E3–E5). O
> `TenantProvisioningServiceTest` passou a rodar a migration de core real (E1). Os itens abaixo ficam
> como registro.

- **Escritor `.inp` do EPANET continua inválido** (nós dos pipes vazios, `[PUMPS]` fora da
  sintaxe, `[OPTIONS]` com palavras de `[TIMES]`, diâmetro em metros, locale pt-BR, sem
  reservatórios, sem `[END]`). Relatório `06-geosan-funcionalidades-epanet-docs.md`.
- **`analyzePressure` usa `Double.MIN_VALUE` como máximo inicial**, que é o menor positivo e não o
  mais negativo: com pressão negativa o máximo sai errado.
- **`LossesService.auditLog` é um método vazio** (`// Implementation`): o serviço parece auditar e não audita.
- **`PlatformConfig` não funciona numa aplicação real:** `hbm2ddl.auto=validate` roda no boot sem
  tenant (as tabelas não estão em `public`), e aponta para `RedisRegionFactory`, que não está nas
  dependências. Não há `@SpringBootApplication`.
- `TenantProvisioningServiceTest` ainda copia o DDL de `public` em vez de rodar a migration real
  (o `FreshInstallTest` cobre a real).
- `scada_data_point` é tabela comum com RLS, `version` e colunas de auditoria por leitura:
  candidata a hypertable no TimescaleDB.
- As tabelas de SCADA e laboratório não têm `CHECK` para os valores de enum (o resto do projeto
  tem); por ora a validação fica só na entidade.

---

## 16. Rodada de 12/10/2026: cadastro, medição, tarifa, arrecadação e correções

### 16.1 O que entrou

- **`shared/sigsan-security`**: `FieldCipher` (AES-256-GCM, chave por tenant, o par tenant+campo entra na tag: valor de um tenant não decifra em outro) e `BlindIndex` (HMAC-SHA-256, para buscar e garantir unicidade sem decifrar). **A integração com o Vault não existe**: `TenantKeyProvider` é só a interface.
- **`commercial/sigsan-registry`**: hierarquia territorial, catálogos (sem semente), cliente, contatos, vínculo cliente-imóvel com vigência, subcategorias e economias, histórico de situação. Regras puras: matrícula com DV módulo 11, inscrição `LLL.SSS.QQQ.LLLL.SSS`, CPF/CNPJ **inclusive CNPJ alfanumérico** (confere com o exemplo `12.ABC.345/01DE-35`; a fonte normativa ainda precisa ser confirmada), consistência da unidade. A migration **corrige o modelo de V1_1** (proprietário e responsável apontavam para `users`).
- **`operations/sigsan-metering`**, **`commercial/sigsan-billing`**, **`commercial/sigsan-collection`**: ver os README de cada módulo (o que está e o que não está implementado, e as divergências encontradas nos relatórios 02, 03 e 11).
- **`RegistrySchemaTest`**: 19 restrições exercitadas no PostgreSQL real (inscrição única, um nome na conta, documento único por índice cego, FK composta entre tenants, RLS, auditoria).

### 16.2 Correções

- Escritor `.inp` do EPANET reescrito e **validado no EPANET real**; o `analyzePressure` errava o máximo de séries negativas; `LossesService.auditLog` (método vazio) removido, a auditoria é do trigger.
- `PlatformConfig`: sem `hbm2ddl=validate` no boot e sem cache de segundo nível apontando para classe inexistente.
- MTR: máquina de estados, cadeia de hash, resultado do SINIR gravado, CDF só para o destinador do MTR; 33 testes (era zero).
- `tenant_settings.tenant_id UNIQUE` (uma configuração por tenant) corrigido; `DataSubjectRequest` citava o art. 18 da LGPD para o prazo de 15 dias (é o art. 19, II).
- Contagens do relatório 01 corrigidas (1.598 JSPs, 23 validadores).

### 16.3 Validação regulatória

Seis levantamentos por agente, com pesquisa na web (`docs/levantamento/08` a `13`) e a síntese em `14-VALIDACAO-REGULATORIA-CONSOLIDADA.md`. Conclusão: **hoje o sistema não atende a maior parte das obrigações regulatórias, porque os módulos que as carregam ainda são regras sem serviço nem API**. Os prazos mais próximos: SINISA (03/11, 17/11, 31/12/2026), NFAg-e (01/12/2026, a confirmar), tarifa social (11/12/2026), boleto híbrido Pix (01/02/2027), NR 11 da ANA (20/05/2027). **Não é parecer jurídico**: exige revisão por advogado e tributarista.

### 16.4 Ainda aberto

- **Sem aplicação executável**: falta `@SpringBootApplication`, serviços de aplicação e API. É o gargalo de tudo que depende de cliente real.
- **Cifra**: integrar `TenantKeyProvider` ao Vault (que no compose está em modo dev) e **tirar o dado pessoal do `audit_log`**, que copia a linha inteira. Nenhum CPF real antes disso.
- **Frontend**: contratos de API supostos (paginação, `validate-step`, `drafts`, `confirmToken`) e sem login (`platform/sigsan-auth` vazio). Busca por documento deve ser `POST`.
- **Qualidade da água**: o modelo de conformidade (percentil, média móvel, plano de amostragem) precisa ser redesenhado; `LabService` só grava a conformidade que o chamador informa; a comunicação de não conformidade é `log.warn`.
- **MTR**: sem integração real com o SINIR (o manual do Web Service não foi encontrado); o MTR do SIGSAN não tem valor legal fora do SINIR ou de sistema estadual.
- **Testes ausentes**: `quality` e `lgpd`.
- **Decisões jurídicas pendentes** (relatório 14, seção 7): NFAg-e, renda/NIS como dado sensível, prescrição, agência dos primeiros clientes, NR 13 (faixas "não cumulativas"), juros pro rata die ou por mês de calendário.
- **Banco de desenvolvimento compartilhado**: os testes do core (`TenantRlsIsolationTest`) usam o PostgreSQL do compose; dois builds ao mesmo tempo colidem ("tuple concurrently updated"). Rodar os testes de banco um de cada vez, ou apontá-los para um banco próprio.
- **`FreshInstallTest` leva ~5 min** (um `SessionFactory` por entidade). Candidato a otimização.

---

## 17. PDCA de 12/10/2026

Relatório completo em `docs/levantamento/16-PDCA-2026-10-12.md`. Build reexecutado num banco virgem: **793 testes,
0 falhas** (o número da seção 2 se confirma). O build verde não pega os achados críticos abaixo, todos fora do
caminho que os testes exercitam:

- **C1** `sigsan_app` é dona de `current_tenant_id()` e consegue redefini-la (reproduzido em transação desfeita); o cluster fica sem superusuário.
- **C2** Nenhum código de produção define `app.current_tenant`; o `MultiTenantConnectionProvider` só troca o schema.
- **C3** O tenant vem de cabeçalho do cliente (`X-Tenant-ID`/`X-Tenant-UUID`), sem vínculo com o token.
- **C4** O `audit_log` guarda dado pessoal em claro para sempre e anula a anonimização.
- **C5** O frontend manda CPF/CNPJ em `GET ?q=` em três telas.

Também: JSON inválido em `jsonb` que escapou da correção anterior (`DpiaEngine`, `toJson` próprios em LGPD e
qualidade); volumes fixos no `LossesService`; MTR aceita qualquer classe se a lista não carregar; quatro ordens de
prioridade conflitantes nos documentos (seção 12 desatualizada); cópia antiga do projeto em `C:\PROJETOS\Ziip SAN\sigsan`
(origem do compose de dev). Ações propostas na seção "A" do relatório 16.

**Decisão (12/10/2026):** a cópia em `C:\PROJETOS\Ziip SAN\sigsan` fica como está e não é editada. A fonte única é
`G:\Meu Drive\PROJETOS\Ziip SAN\sigsan`. O PostgreSQL de desenvolvimento (`sigsan-postgres-primary`) foi iniciado a
partir da cópia em C:; o `docker-compose.yml` e o init de segurança das duas cópias são idênticos nesta data.

**Plano (12/10/2026):** `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` consolida as ações do PDCA (etapas E0–E6, fundações até
20/11/2026) e o roadmap R1–R9, com o mapa de versões Flyway reconciliado e as decisões D1–D13, G1–G10, S1–S3. Passa a
ser a ordem de prioridade canônica (a seção 12 deste diário será substituída na etapa E0). Execução ainda não iniciada.

**Decisões do usuário (12/10/2026):** git com `.git` fora do Drive (`--separate-git-dir`); remoto GitHub privado
(push só com autorização); Spring Boot 4.1.1; Keycloak com realm único `sigsan` e claim `tenant_id` (UUID).

---

## 18. Execução do plano (a partir de 12/10/2026)

### E0 — linha de base (12/10/2026) ✔

- `git init -b main --separate-git-dir=C:/Users/shyka/.sigsan-git/sigsan.git` (o Drive corrompe `.git` ao sincronizar
  `index.lock` e objetos); `core.autocrlf=false` no repositório; `.gitignore` e `.gitattributes` (`*.sh`/`*.sql` em LF).
- Varredura de segredos (gitleaks 8.21.2, em container, sobre uma cópia que incluía `docs/levantamento`): 0 achados. Busca
  manual: só senhas de desenvolvimento conhecidas (`changeme` no Kubernetes, senhas de dev no compose e nos scripts),
  tratadas na E3.
- Commit `e749025`: linha de base com 524 arquivos (793 testes verdes).
- Roadmap canônico: seção 12 substituída; remissões em `00` §3, `14` §3 e `15c` §3; `DIARIO_DE_BORDO.md` na raiz aponta para este arquivo.
- `scripts/build.sh`: robocopy passa a excluir o arquivo `.git` (`/XF .git`). Pasta vazia `sigsan/sigsan` removida.
- Remoto GitHub: pendente de autorizar o MCP `github` (`/mcp`). Push só com autorização do usuário.

### E1 — scripts, banco de teste e POMs (12/10/2026) ✔

- **`scripts/lib.sh`** (novo): `set -euo pipefail`; trava exclusiva (`mkdir` atômico + PID, trava velha detectada) usada
  por `build.sh` e `reset-it-db.sh` — dois builds simultâneos esperam em vez de colidir; espelho que **falha** se o
  `pom.xml` do G: não existir (Drive offline) ou se o robocopy devolver código ≥ 8 (antes o exit era forçado a 0).
  `validate-inp.sh`: nomes com espaço preservados, `wntr==1.5.0` fixo.
- **Testes fora do banco de dev:** `TenantRlsIsolationTest` (core) e `TenantProvisioningServiceTest` (platform) usam
  `SIGSAN_IT_*` com padrão na porta 55433. Aceite feito com `sigsan-postgres-primary` **parado**: 793 testes verdes.
- **`TenantProvisioningServiceTest` escondia dois defeitos de teste**, expostos ao trocar as tabelas criadas à mão
  pela migration de core real (`CoreMigrationRunner`): (1) CNPJ fixo em todos os testes, e o `tenant_catalog` real
  tem `UNIQUE (cnpj)` — o provisionamento colidia com linhas de outros testes e falhava; (2) 14 chamadas ignoravam o
  resultado do provisionamento, então asserções como "nenhuma senha no `audit_log`" passavam **no vazio**. Agora há
  CNPJ por tenant e `provisionOk()` exige sucesso.
- **POMs:** removidas dependências declaradas e não usadas por nenhuma classe (verificado por `git grep` de imports):
  GeoTools (14 artefatos) e o repositório OSGeo, PDFBox, POI, BouncyCastle, Spring Integration (MQTT/HTTP/file/JMS),
  `jackson-dataformat-xml`, WebFlux, Spring Security (sem uso ainda; volta na E5 como resource server),
  `keycloak-spring-boot-starter`, Vault e MinIO (voltam no R1), Redis, javafaker, Guava, mapstruct, micrometer-prometheus,
  Testcontainers; BOMs de Spring Cloud e Testcontainers; perfis Maven sem perfil Spring correspondente.
  `jts-core` com versão única (1.19.0) no POM raiz; compilador com uma configuração só, `--release 21` e `-parameters`;
  `lombok.config` copia `@Qualifier` para o construtor. `dependency:tree`: um só JTS, `snakeyaml` só 2.2.
- Banco de desenvolvimento religado ao final.

### E2 — Spring Boot 4.1.1 (12/10/2026) ✔

- Spring Boot 3.2.5 → **4.1.1** (o 3.5 está sem suporte OSS desde 30/06/2026). Versões que vêm do BOM: Spring Framework
  7.0.9, Hibernate **7.4.5**, Flyway **12.4.0** (com `flyway-database-postgresql`), Spring Data 2026.0.1, Lombok 1.18.46,
  PostgreSQL JDBC 42.7.13, Jackson 3.1.5 (+ Jackson 2.21.5 ainda gerenciado). `keycloak-admin-client` 26.0.12; compiler 3.15.0;
  surefire/failsafe 3.5.6. Os pins de PostgreSQL, Flyway e Lombok saíram do POM raiz.
- `spring-boot-starter-web` → `spring-boot-starter-webmvc` (o nome antigo está obsoleto no Boot 4).
- `TenantProvisioningService` deixou de injetar `FlywayProperties` (não usado; mudou de pacote no Boot 4) e `ObjectMapper`
  (o Boot 4 não publica mais um `ObjectMapper` do Jackson 2 como bean); a auditoria do provisionamento usa `Json`.
- **`Json` migrado para Jackson 3** (`tools.jackson`). Os testes pegaram duas mudanças de comportamento que, sem eles,
  passariam despercebidas: (1) o Boot 4 não traz mais o `jackson-datatype-jsr310` do Jackson 2, e `OffsetDateTime` deixava
  de serializar; (2) o Jackson 3 **desliga** `FAIL_ON_EMPTY_BEANS` por padrão, e um objeto sem propriedades viraria `{}`
  em silêncio — religado explicitamente.
- `hibernate.dialect` removido (o Hibernate 7 detecta e avisava 66 vezes por build).
- Compilou sem nenhuma alteração nas interfaces de multi-tenant (as implementações *raw* continuam aceitas pelo Hibernate 7).
- **Dívida registrada:** `MtrService`, `LossesService`, `EpanetSimulationService`, `LabService`, `ScadaIntegrationService` e
  `LgpdEngineService` ainda criam `ObjectMapper` do Jackson 2 por conta própria; sem o módulo jsr310 no classpath, datas
  nesses caminhos falham em tempo de execução. Já estavam na E3' (troca por `Json`).
- Aceite: 793 testes verdes, `FreshInstallTest` validando as 66 entidades com Hibernate 7.4.5, sem aviso de depreciação do Hibernate.

### E3 — banco e tenant: achados C1 e C2 (12/10/2026) ✔

- **Roles (`infra/postgres/init`)**: `00-roles.sh` (senhas por variável de ambiente, falha se vazias) + `sql/security.sql`
  (substitui `01-security-roles.sql`). Superusuário de bootstrap passa a ser `postgres`; **`sigsan_owner` (NOLOGIN) é dono do
  banco, de `public` e de `current_tenant_id()`**; `sigsan_migrator` tem CREATE no banco e em `public`; `sigsan_app` não cria
  nada (nem TEMP). `REVOKE ALL ... FROM PUBLIC` no banco e em `public`. Saíram o `ALTER DEFAULT PRIVILEGES` (sem efeito; se
  "corrigido" daria UPDATE/DELETE em `audit_log`) e a seção de RLS em tabelas que ainda não existiam no init. A verificação
  final usa `RAISE EXCEPTION`: o container **não sobe** se algo estiver errado (testado com a configuração antiga,
  `POSTGRES_USER=sigsan_app`: o init aborta). Banco e role próprios do Keycloak quando `KEYCLOAK_DB_PASSWORD` é definida.
- **`docker-compose.yml`**: `POSTGRES_USER=postgres`, senhas sem valor padrão (`:?`, ver `.env.example`), porta 5432 só em
  127.0.0.1, healthcheck como `postgres`; Keycloak no banco `keycloak`; **`postgres-replica` removido** (não replicava e
  expunha um PostgreSQL com `sigsan_app` superusuário na 5433).
- **Banco de integração**: `reset-it-db.sh` com o mesmo modelo; senhas só de teste em `scripts/it-db.env`; detecta init
  abortado (container parado) e falha com o log.
- **`V1_3__harden_audit_function.sql`** (platform): `audit_trigger_function` com `SET search_path = pg_catalog, public, pg_temp`,
  `public.current_tenant_id()` qualificada e `NULLIF(current_setting('app.current_user', true), '')::uuid` (sem isso, toda
  gravação auditada quebraria quando a conexão passasse a limpar a GUC); `register_tenant_table` cria a policy com a função
  qualificada.
- **Tenant na conexão (C2)**: `TenantRef(schema, tenantId)` (novo) é o identificador de tenant do Hibernate;
  `TenantSchemaResolver` devolve `TenantRef.NONE` sem contexto (antes: "public") e falha com contexto inconsistente;
  `MultiTenantConnectionProvider` define `search_path`, `app.current_tenant` e `app.current_user` numa ida ao banco ao entregar
  a conexão e limpa na devolução — se a limpeza falhar, a conexão é **descartada do pool** (Hikari `evictConnection`).
  `PlatformConfig` passa as **instâncias** ao Hibernate (pelo nome da classe a aplicação não subiria: o provider não tem
  construtor vazio). `TenantContextHolder` ganhou `userId`. Flyway dos tenants com **`outOfOrder(true)`** (G10a).
- **Testes novos** (`platform/sigsan-app`): `DatabaseHardeningTest` (6: app e migrador recebem 42501 ao redefinir
  `current_tenant_id()`; app não cria tabela em `public`; donos = `sigsan_owner` sem login; nenhuma role `sigsan*` com
  privilégio elevado e um superusuário de operação existe; trigger com `search_path` fixo) e `TenantConnectionProviderTest`
  (6, Hibernate real com o provider, dois tenants, pool de **uma** conexão: A só lê A; B só lê B na mesma conexão física;
  sem tenant nada é alcançado; gravar linha de B no contexto de A é barrado pela RLS; a conexão volta limpa; a gravação
  gera auditoria com o tenant certo sem GUC manual). Com o provider antigo o primeiro caso falharia (RLS devolveria zero linhas).
- Aceite: **805 testes verdes** (793 + 12).
- **Pendências registradas:** (1) o PostgreSQL de desenvolvimento (`sigsan-postgres-primary`) continua no modelo antigo:
  foi criado a partir da cópia em `C:\PROJETOS` e recriá-lo exige montar o init a partir de um espelho local (o Docker não
  enxerga o G:) — fica para a E5 (`scripts/dev-up.sh`), com `down -v`. (2) `infra/kubernetes/base/postgres.yaml` não é
  funcional (Patroni ignora `POSTGRES_*`, chaves de ConfigMap inexistentes, bibliotecas ausentes na imagem, sem bootstrap
  de segurança): saíram as senhas em texto e o usuário de aplicação como bootstrap, com aviso no topo do arquivo; a
  correção completa exige um cluster para validar.

### E3' — JSON e falhas silenciosas (12/10/2026) ✔

Feita no branch `e3-json` (worktree separada), em paralelo à E3.

- **`Json`** ganhou `readTree` (Jackson 3 `JsonNode`; nulo/branco → `MissingNode`) e `readMap` (mapa mutável; nulo,
  branco ou `null` → vazio). Inválido → `IllegalStateException`, sem repetir o conteúdo na mensagem.
  `FAIL_ON_TRAILING_TOKENS` explícito: `"A.1,B.2"` e `"1, 2"` não passam por JSON.
- **LGPD:** `DpiaEngine` grava as medidas com `Json.write` (antes `List.toString()`, recusado pelo jsonb) e conta
  entradas com `readTree` (a contagem por vírgula partia "clientes, inclusive menores" em dois).
  `DataSubjectRightsEngine` funde metadados com `readMap` + `put` (antes concatenava sem escape). `LgpdEngineService`
  sem `toJson` próprio: auditoria grava `ConsentSnapshot`/`DpiaSnapshot`/`DsrSnapshot` (`service/audit/`), sem IP,
  user agent, geolocalização, texto do consentimento, id do titular, descrição, `responseData` e motivos. O valor
  antigo do DPIA passou a ser tirado antes da reavaliação (era o mesmo objeto já alterado).
- **Qualidade:** `LabService` com snapshots (`lab/audit/`; o do instrumento omite `connectionConfig`).
  `ScadaIntegrationService` desligado por padrão (`sigsan.scada.mqtt.enabled=true` liga; broker obrigatório),
  `ScadaTopic` (`sigsan/{tenantUuid}/scada/{data|alert}/{tag}`, assinatura `sigsan/+/scada/+/+`), tenant do tópico
  no contexto com `finally`, cache de tags por (tenant, tag) sob demanda, mensagem sem `timestamp`/`quality`
  rejeitada, comando via `Json.write` em `sigsan/{tenant}/command/{tag}`. O `connect()` antigo ignorava as opções
  (usuário, senha, reconexão). **Antes de ligar em produção:** ACL no broker por tenant; validação contra o
  catálogo e resolução do schema ficam para a E5 (hoje o contexto recebe só o `tenantId`).
- **Perdas:** `loadInputs` lança `MissingInputDataException` (sem os volumes fixos 10000/7000/200/100/2%); nada é
  gravado; marca `PLACEHOLDER_INPUTS` e guarda de aprovação removidas.
- **MTR:** classes aceitas do destinador em falha fechada (só array JSON não vazio de textos; nulo, vazio, `[]`,
  CSV ou ilegível → `IllegalStateException`). Destinadores cadastrados em CSV precisam ser corrigidos.
- **GIS:** `EpanetSimulationService` lê propriedades com `Json.readTree`.
- **Provisionamento:** o hash do admin é gravado como recebido, validado como bcrypt completo antes de criar o
  schema (antes `"$2a$12$" + hash`). Testes usam um bcrypt real (custo 12) de senha de teste.
- **Defeito de repositório achado no caminho:** a amostra `febraban150-retorno-SINTETICO.ret` estava no git em LF
  (normalizada por `text=auto`); checkout novo quebrava 9 testes do `Febraban150ReturnTest`. `*.ret -text` e blob em
  CRLF (commit `fed8b1f`).
- **Testes novos (77):** core `JsonTest` +6; platform +2 (hash inválido recusado sem schema; hash gravado sem
  alteração); `sigsan-quality` 0 → 38 (`LabServiceTest`, `ScadaTopicTest`, `ScadaIntegrationServiceTest`);
  `sigsan-lgpd` 0 → 15 (`DpiaEngineTest`, `DataSubjectRightsEngineTest`, `LgpdEngineServiceTest`);
  `LossesServiceTest` 3; `MtrServiceTest` +13. Build completo: **870 testes verdes**.
- **Ainda aberto:** `commercial/sigsan-collection` (`AgreementBarcodeLayout`, `FreeFieldLayout`) usa Jackson 2 com
  `ObjectMapper` estático (objetos de valor, falham alto; migrar quando o Jackson 2 sair do core);
  `sigsan-gis/pom.xml` mantém `jackson-datatype-jsr310`/`jdk8` sem uso no código; `KeycloakAdminService` envia o
  hash como senha em texto (E5); `grantConsent` incrementa `version` à mão em consentimento existente.

### E4 — dado pessoal: achados C4 e C5 (12/10/2026) — parcial ✔

- **Auditoria sem dado pessoal (C4)**: core `V2__audit_redaction.sql` cria `public.audit_redaction_rule` (sem GRANT para a
  aplicação) e `public.audit_redact(tabela, linha, changed)`: `REDACT` troca o valor por `"[redacted]"`, `OMIT` remove a
  chave (em `changed_fields`, `"[changed]"`). `V1_4` (platform) reescreve o trigger: `changed_fields` é calculado sobre as
  linhas cruas e redigido depois (senão a troca de nome sumiria da trilha). Regras por módulo: platform `V1_4` (users,
  addresses, documents, service_orders), registry `V3_2`, lgpd `V2_7`, mtr `V2_6`, quality `V2_9`, losses `V2_10`,
  metering `V5_2`. **`AuditRedactionCoverageTest`**: coluna com nome de dado pessoal em tabela auditada sem regra nem
  justificativa explícita quebra o build — na primeira execução achou 10 colunas não classificadas (3 viraram regra:
  `consumer_units.notes`, `meters.notes`, `lgpd_dpia.dpo_signature`). `AuditRedactionTest` (6) prova no PostgreSQL real.
- **Cifra ligada ao cadastro**: `FieldProtector` (cifra + índice cego + máscara com contexto único) e
  `EnvDerivedTenantKeyProvider` (HKDF-SHA256 conferido com o vetor da RFC 5869; **só desenvolvimento**, chave mestra em
  `SIGSAN_DEV_MASTER_KEY`). `CustomerDocumentService` (registry): grava só cifra/índice/máscara, recusa documento inválido
  ou incompatível com PF/PJ, detecta duplicidade pelo índice, e `reveal` exige finalidade e registra o acesso na
  auditoria sem o valor. `DocumentNumber` foi para `shared/sigsan-core` (decisão S1). O bean de configuração
  (`CryptoConfig`) fica para a E5, junto com a aplicação.
- **Busca por documento fora da URL (C5)**: no frontend, `customers` e `consumer-units` buscam por `POST .../search`
  com os filtros no corpo; `list()` desses recursos recusa `q`; `cleanParams` recusa qualquer valor com cara de CPF/CNPJ
  (numérico ou alfanumérico). As três telas e a busca de imóvel do assistente passaram a usar `search`. Frontend: 97 testes,
  typecheck limpo. Os endpoints do backend nascem na E5.
- **Merge da E3'** (worktree paralela) sem regressão: **906 testes verdes**.
- **Falta na E4**: cifra das colunas do MTR (`waste_mtr.driver_cpf`, `waste_generator.cnpj_cpf`, `waste_transporter.drivers`)
  — em andamento num branch separado (`e4-mtr`).

### E5 — identidade e frontend (12/10/2026) — frente do frontend ✔ (branch `e5-front`)

- **Realm `sigsan`** (`infra/keycloak/realms/sigsan-realm.json`, para `--import-realm`; sem usuário nem segredo): client
  público `sigsan-web` só com *standard flow* e **PKCE S256** obrigatório (sem implicit, direct grant, device, CIBA),
  redirect `http://localhost:3000/*`, web origin `http://localhost:3000`; escopo `sigsan-api` com audiência `sigsan-api` e
  claim `tenant_id` (atributo do usuário, String, só no access token); papéis `ADMIN`, `MANAGER`, `OPERATOR`, `VIEWER`,
  `registry.reveal-document` (`fullScopeAllowed: false`: o token leva só esses); perfil de usuário com `tenant_id` visível e
  editável só por admin, validado como UUID, atributos não gerenciados descartados; força bruta (5 falhas, espera até 15 min),
  senha 12+ com maiúscula/minúscula/dígito e histórico 5; access token 5 min, SSO ocioso 30 min, máximo 10 h. Ao declarar
  `clientScopes`, o Keycloak não cria os escopos padrão: `basic` (sub), `roles`, `profile` e `email` estão no arquivo.
- **Verificado num Keycloak 26.8.0 descartável** (`start-dev --import-realm`): importação sem erro; PKCE ausente, `plain`,
  implicit, redirect fora da lista, password grant e troca sem `code_verifier` recusados; código + PKCE S256 dá access token
  com `iss=http://localhost:8080/realms/sigsan`, `aud="sigsan-api"` (string única), `tenant_id`, `realm_access.roles` só com os
  papéis do SIGSAN, 300 s; refresh mantém `aud`/`tenant_id`; CORS para a 3000; `tenant_id` não-UUID e atributo não gerenciado
  recusados; bloqueio de força bruta ativo. Ponta a ponta no navegador (Vite + API de mentira na 8090): login, `/api/me` só
  com `Authorization: Bearer`, renovação por refresh, logout encerrando a sessão SSO. **Não verificado:** o compose completo
  com o Keycloak no PostgreSQL (o teste usou o banco embutido do `start-dev`) e o backend real.
- **docker-compose (`keycloak`)**: `quay.io/keycloak/keycloak:26.8.0`; `KC_BOOTSTRAP_ADMIN_PASSWORD` de
  `KEYCLOAK_ADMIN_PASSWORD` sem padrão; `KC_PROXY` removido; `KC_HOSTNAME=http://localhost:8080` +
  `KC_HOSTNAME_BACKCHANNEL_DYNAMIC`; health em `/health/ready` (9000); `depends_on` saudável; `127.0.0.1:8080`; import de
  `${SIGSAN_KC_IMPORT_DIR:-./infra/keycloak/realms}`. `scripts/kc-dev-user.sh`: usuário de desenvolvimento via `kcadm.sh` no
  container, senha e `tenant_id` por variável (sem padrão), papéis opcionais, idempotente.
- **Frontend**: `oidc-client-ts` 3.5.0, tokens e estado em `sessionStorage`; `/login` com botão "Entrar", `/auth/callback`,
  logout pelo `end_session_endpoint`; `api.ts` sem empresa, sem `X-Tenant-ID`, sem `/auth/login`/`/auth/refresh`, sem token em
  `localStorage`; 401 renova uma vez ou volta ao login; `getMe` = `GET /api/me` (`{id, username, email, fullName, roles,
  tenantId}`); proxy `/api` → `SIGSAN_API_URL` (padrão 8090); build sem sourcemap. Removidos `zustand`, `recharts`,
  `@hello-pangea/dnd`; `tailwindcss` em dev. **134 testes** (97 → +37), typecheck e build limpos. `npm audit`: 24 avisos, todos
  com correção só em versão principal nova (não aplicados; ver `docs/frontend-cadastro.md`, seção 1).
- **Pendente**: integração com o backend da E5 (`/api/me`, validação de `iss`/`aud`/`tenant_id`); `maplibre-gl` 6 e
  `react-router-dom` 7 (avisos de produção); rotação de refresh token desligada de propósito (com `sessionStorage` cada aba
  tem o seu e a rotação derrubaria a aba que renovasse depois).

### E4 — MTR: documentos pessoais cifrados (12/10/2026) ✔

Feita no branch `e4-mtr` (worktree separada).

- **`V2_8__mtr_document_protection.sql`** (mtr): `waste_mtr.driver_cpf` → `driver_cpf_ciphertext`/`_hash`/`_masked` com
  CHECK (cifra nula ⇔ índice nulo); `waste_generator.cnpj_cpf` → `document_ciphertext`/`document_hash` (NOT NULL) e
  `document_masked`, com `idx_waste_generator_document (tenant_id, document_hash)` único no lugar de
  `idx_waste_generator_cnpj`; `waste_transporter.drivers` → `drivers_ciphertext` (lista JSON cifrada inteira).
  **Sem backfill** (a chave está na aplicação): antes de qualquer DROP define `app.current_tenant` (a RLS FORCE vale para
  o migrador; sem a GUC ele vê zero linhas) e faz `RAISE EXCEPTION` se houver valor em claro nas colunas antigas — banco
  com dados exige migração assistida pela aplicação antes desta versão. Regras de redação das colunas novas (`*_ciphertext`
  e `*_hash` OMIT, `*_masked` REDACT). As regras das colunas removidas são apagadas **só quando nenhum outro schema ainda
  tem a coluna** (`pg_attribute`): o catálogo é global e um tenant atrasado voltaria a gravar o CPF em claro na trilha.
- **Java**: `MtrDocumentProtection` (contextos `waste_mtr.driver_cpf`, `waste_generator.document`,
  `waste_transporter.drivers`; valida com `DocumentNumber`; tenant do `TenantContextHolder`, sem ele erro). `MtrService`
  cifra na entrada; novos `registerGenerator` (duplicidade pelo índice cego → `DuplicateGeneratorException`) e
  `replaceTransporterDrivers`. **`MtrCanonical.of(mtr, driverCpf)`** recebe o CPF decifrado: o hash da cadeia é o mesmo de
  antes (teste com hash congelado calculado pela versão antiga). `SinirIntegrationService.syncMtr(mtr, PartyDocuments)`
  recebe os documentos decifrados. `MtrPdfGenerator`: CPF do motorista e de gerador PF mascarados, CNPJ completo (S2).
  `toString` dos requests e de `PartyDocuments` sem documento. `sigsan-mtr` depende de `sigsan-security`.
- **Testes novos (38):** `MtrServiceTest` +20 (nada em claro nas entidades após emissão, coleta, cadastro de gerador e de
  motoristas; duplicidade pelo índice cego com e sem pontuação; índice por tenant; hash sobre o CPF decifrado; SINIR e PDF
  recebem os documentos decifrados; CPF/CNPJ inválido recusado; sem tenant → erro); `MtrCanonicalTest` +2 (formato e hash
  congelados; CPF faz parte do conteúdo); `MtrPdfGeneratorTest` 3; `MtrDocumentProtectionTest` 5 (contextos separados,
  outro tenant não decifra); `MtrDocumentProtectionMigrationTest` 8 (sigsan-app, PostgreSQL real: colunas antigas fora,
  índice único e CHECK, sem GUC o migrador vê a tabela vazia, a V2_8 recusa cada uma das três colunas com dado e aplica
  quando estão vazias). `FreshInstallTest` e `AuditRedactionCoverageTest` verdes sem allowlist nova.
- Build completo: **944 testes verdes** (core 45, security 28, platform 18, quality 38, losses 24, gis 31, mtr 76, lgpd 15,
  registry 37, billing 216, metering 200, collection 165, app 51).
- **Pendências:** bean `FieldProtector` (CryptoConfig) na E5 — sem ele o `MtrService` não sobe num contexto Spring;
  `reveal` do MTR (hash, SINIR, PDF) não registra acesso na auditoria (é processamento do sistema; avaliar com o DPO);
  `SinirMtrRequest` ainda tem `toString` padrão com os documentos.

### E5 — API do cadastro (12/10/2026) ✔ (branch `e5-api`)

- **Cifra configurada (`CryptoConfig`, sigsan-app)**: `TenantKeyProvider` só com `sigsan.crypto.provider=dev-env`
  (chave de `sigsan.crypto.dev-master-key` em teste ou `SIGSAN_DEV_MASTER_KEY`), recusado em perfil com "prod"; sem provider
  a aplicação não sobe e diz por quê. `FieldCipher`, `BlindIndex`, `FieldProtector`, `CustomerDocumentService` como beans.
  Perfil `local` usa `dev-env` por padrão; o provider do Vault continua por fazer.
- **API REST** (`commercial/sigsan-registry`, pacotes `application` e `web`): `localities`, `commercial-sectors`, `blocks`,
  `reading-routes` (sem exclusão: as tabelas não têm `deleted_at`; DELETE = 405 com o que usar), `categories`,
  `subcategories`, `water-statuses`, `sewer-statuses`, `customers` (+ `/search`, `/{id}/reveal-document` com papel
  `registry.reveal-document` e finalidade obrigatória), `consumer-units` (+ `/search`, `/{id}/customers`, `/{id}/history`;
  matrícula gerada com DV, inscrição única, `UnitConsistency`, avisos confirmáveis por `confirmTokens`) e `settings`
  (`public.tenant_settings`). Leitura `VIEWER`+, escrita `OPERATOR`/`MANAGER`/`ADMIN`; outro tenant = 404. Contrato real e
  diferenças em `docs/frontend-cadastro.md` §5.4 (ex.: `reveal-document` exige corpo `{purpose}`).
- **V3_3**: `consumer_units.deleted_reason` redigido na auditoria (texto livre que a API passou a gravar).
- **Testes**: `CryptoConfigTest` (4) e `RegistryApiE2ETest` (10, banco real, dois tenants: fluxo completo, página, 409,
  exclusão lógica, busca por CPF com/sem pontuação, `GET ?q=` → 400, reveal 403/200 com registro sem o valor, isolamento 404,
  `X-Tenant-ID` divergente 403, auditoria sem CPF nem nome).
- **Fora (pendente)**: rascunhos e `validate-step`, `block-faces`, catálogos sem tabela, CEP, exportação CSV, endereço e tipo
  de cliente (filtros `city`/`customerTypeId` = 400), layout de inscrição por tenant, montagem das listas sem N+1.
