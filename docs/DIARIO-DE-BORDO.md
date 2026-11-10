# Diário de Bordo — SIGSAN

Registro vivo do projeto: o que é, como está, o que travou e o que vem agora.
Última atualização: 10/11/2026

---

## 1. Identificação

| Item | Valor |
|---|---|
| Nome do produto | **Ziip SAN** (nome comercial; vem do pacote de marca, ADR-0016). "SIGSAN" é o nome interno do código até a renomeação para `ziipsan` (tarefa P0.33) |
| Tipo | Plataforma SaaS vertical multi-empresa para saneamento básico |
| Raiz do repositório | `G:\Meu Drive\PROJETOS\Ziip SAN\sigsan`. Git desde 12/10/2026, com os dados do repositório fora do Drive (`C:\Users\shyka\.sigsan-git\sigsan.git`; no G: há só o arquivo `.git`). A cópia antiga em `C:\PROJETOS\Ziip SAN\sigsan` não é editada. O Docker não enxerga o drive `G:`: o build usa um espelho local, ver seção 13 |
| Organização | `br.com.sigsan` |
| Versão | `1.0.0-SNAPSHOT` |
| Build | Maven reactor, 13 módulos, Java 21 |

**Ordem de prioridade:** a única válida é a de `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (desde 12/10/2026). Perdas, qualidade da água, LGPD e MTR correm como trilha paralela ao comercial, não à frente dele.

**Fora do escopo:** blockchain no MTR, integração de escrita com ESRI. *(Até 12/10/2026 POC e editais também estavam fora; o ADR-0023 os trouxe para o escopo: trilha de editais no P4 e prova de conceito como critério de aceite a partir do P3.)*

**Decisões e arquitetura:** registradas como ADRs em `docs/arquitetura/adr/` (índice no `README.md` da pasta). O plano canônico continua sendo `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`, agora organizado em fases P0–P4.

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

### E5 e E6 — fechamento e aceite de ponta a ponta (12/10/2026) ✔

- **Aplicação executável** (`platform/sigsan-app`): `SigsanApplication`, dois DataSources, `DatabaseMigrator` antes do JPA,
  resource server JWT (emissor, audiência `sigsan-api`, validade; `aud` como string única do Keycloak aceito), `JwtTenantFilter`
  (tenant só do token; cabeçalho divergente = 403), problem+json em tudo, `X-Request-ID`, `GET /api/me`
  (`{id, username, fullName, email, tenantId, tenantSchema, roles}`), `CryptoConfig` (provider `dev-env` recusado em `prod`;
  sem provider não sobe), API REST do cadastro (branch `e5-api`), `DevTenantBootstrap` só no perfil `local`.
- **Frontend**: login PKCE (`oidc-client-ts`), "Revelar documento" exige finalidade de uma lista fechada (vai em `{purpose}`;
  texto livre levaria dado pessoal à auditoria). ESLint 9 flat com jsx-a11y (0 erros).
- **Operação**: `scripts/dev-up.sh` (`--reset` recria do zero), `run-app.sh`, `ci.sh`, `kc-dev-user.sh`; `README.md` na raiz;
  `.env.example` completo (o `.env` local foi gerado com senhas aleatórias e não é versionado).
- **Banco de desenvolvimento recriado** no modelo novo (`dev-up.sh --reset`): nenhuma role `sigsan*` superusuária,
  `current_tenant_id()` de `sigsan_owner`, Keycloak 26.8.0 com banco `keycloak` próprio.
- **Aceite de ponta a ponta, no navegador**: tela de login → Keycloak (`code_challenge_method=S256`) → volta autenticado
  (`dev.local`) → `GET /api/me` 200 → `POST /api/registry/consumer-units/search` 200 (lista vazia real) → catálogos 200.
  O tenant de desenvolvimento foi provisionado na subida.
- **Lacuna achada no aceite (R2)**: o formulário de cliente exige "Tipo de cliente" (catálogo `customer-types` sem tabela no
  backend: 404) e endereço (o backend ainda não modela endereço do cliente). **Cadastrar cliente pela tela fica bloqueado** até
  o R2; a API de clientes funciona e é coberta pelo `RegistryApiE2ETest`. Também sem tabela: `property-profiles`.
- **Contagem final**: **973 testes Java** (core 36, security 28, platform 25, quality 38, losses 24, gis 31, mtr 76, lgpd 15,
  registry 37, billing 216, metering 200, collection 165, app 82) e **134 testes do frontend**, todos verdes.

### Pendências depois das fundações (E0–E6)

1. **R2 do cadastro**: `customer-types` e `property-profiles` com tabela; endereço do cliente; rascunhos e `validate-step`;
   `block-faces`; CEP; N+1 nas listas; índice trigram para busca por nome "contém".
2. **R1 Vault**: `TenantKeyProvider` de produção (hoje só `dev-env`). Nenhum CPF real antes disso.
3. **Decisões suas**: subir `maplibre-gl` (crítico no `npm audit`) e `react-router` de versão principal; remoto GitHub
   (autorizar o MCP `github` via `/mcp`; nenhum push foi feito).
4. **Infra**: `infra/kubernetes` não funcional (aviso no arquivo); portas de Redis/Kafka etc. ainda em 0.0.0.0 no compose
   (serviços não usados hoje).
5. **Leituras do sistema** (hash do MTR, SINIR, PDF) não registram acesso na auditoria — avaliar com o encarregado (DPO).
6. Decisões jurídicas e regulatórias G1–G9 (plano §2.2) seguem com seus prazos; a tarifa social (R3) depende de G1 e G2.

### 12/10/2026 — Nome do diário

- `docs/DIARIO-DE-BORDA.md` renomeado para `docs/DIARIO-DE-BORDO.md` (histórico preservado com `git mv`). Atualizados o
  título, o `DIARIO_DE_BORDO.md` da raiz, o `README.md`, o plano (§ decisões) e a análise de fontes abertas.
- Mantêm o nome antigo, de propósito: os comentários das migrations `V3_1`, `V4_1` e `V5_1` (alterá-los muda o checksum e
  o Flyway recusa a validação nos bancos já migrados) e os relatórios de `docs/levantamento/`, que são registros datados.

### 12/10/2026 — Acesso ao GitHub

- Gerada chave SSH ed25519 só para este repositório (deploy key), sem passphrase: `~/.ssh/id_ed25519_github_ziip_san`
  (fora do Drive), alias `github-ziip-san` no `~/.ssh/config`. Remoto `origin` configurado: `git@github-ziip-san:mastersonmdaniel/Ziip_San.git` (o produto e o repositório se chamam Ziip SAN; SIGSAN é o nome interno do código).
- Repositório privado criado e deploy key com escrita cadastrada pelo usuário. Primeiro push autorizado e feito em
  12/10/2026: `main` (30 commits, até `fb2b53d`) rastreando `origin/main`. Pushes seguintes continuam dependendo de
  autorização do usuário.

### 12/10/2026 — Revisão de estrutura para operação SaaS

- Cinco revisões só de leitura (backend e dados, segurança e LGPD, infraestrutura, frontend, plano e produto),
  consolidadas em `docs/levantamento/17-REVISAO-ESTRUTURA-SAAS-2026-10-12.md`.
- Conclusão: a fundação de isolamento e segurança é sólida; falta a camada de serviço operado. Críticos: sem artefato
  implantável nem ambientes (X1); banco sem HA, backup e PITR (X2); `audit_log` único sem partição com trigger por linha
  nas tabelas de lote (X3); sem infraestrutura de lote/assíncrono (X4); roadmap sem go-live, sem migração de legado e sem
  jurídico (X5). Alto de segurança confirmado no código: CPF reconstruível por quem só tem leitura (máscara + busca sem
  cota, S1).
- Proposto roadmap revisado P0–P4 com marco de go-live piloto (§7 do relatório) e metas não funcionais (§6). O plano
  canônico **não** foi alterado: depende das decisões do §8 (hospedagem, adoção do roadmap, piloto, jurídico/DPO, infra
  não usada, push, ambiente de dev).

### 12/10/2026 — Replanejamento: plano v2 a v2.5, 32 ADRs e aderência a editais

Escopo desta etapa: **só plano e documentação** (decisão do dono); a execução em código fica para as fases do plano.
Todas as decisões foram tomadas pelo dono do produto em cartões de múltipla escolha e estão registradas como ADRs.

- **Plano canônico** (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`) reescrito no lugar e levado de v2 a **v2.5**: fases P0
  (plataforma operável) → P1 (migração e satélite) → P2 (ciclo de faturamento) → P3 (pronto para operar) → go-live do
  piloto (jun–jul/2027) → P4 (inclui a **trilha de editais**, ondas A/B/C) → 2029. Tarefas numeradas com origem, tamanho,
  dependência e critério de aceite; mapa Flyway reservado por módulo; riscos e marcos.
- **Arquitetura** em `docs/arquitetura/`: parâmetros do tenant (classes de parâmetro e limites legais), requisitos não
  funcionais (RNF-01…71), arquitetura-alvo (AWS São Paulo), porte do motor fiscal do Ziip Cloud para Java (NFAg-e
  modelo 75), marca e temas, Central Ziip, Ajuda/Suporte/Sobre.
- **ADRs 0001–0032** (`docs/arquitetura/adr/README.md`). Principais: roadmap P0–P4 (0001); G1/G2/G4/G5/G6/G9 como
  parâmetros do tenant com limites legais (0002, 0015: multa padrão 2% configurável, vedações de corte para todos os
  motivos e inadimplência sempre travada, piso federal da tarifa social, pisos de retenção); motor fiscal portado para
  Java (0003); SINISA/SINIR assistidos (0004); isolamento híbrido por porte (0005); BFF com cookie no web (0006) e PKCE
  nativo nos apps (0030); MFA configurável por tenant (0007); máscara de documento configurável (0008); push automático
  e dev fora do Drive (0009); infra parada congelada (0010); **hospedagem AWS sa-east-1** (0011); metas não funcionais
  (0012); PostgreSQL 17 no P0 (0013); outbox no PostgreSQL (0014); re-brand com pacote de marca e renomeação
  `sigsan`→`ziipsan` no P0 (0016); temas claro/escuro Azul Água e Verde Água (0017); Central Ziip própria (0018);
  cobrança SaaS por contrato (0019); suporte com protocolo (0020); Sobre/versão/changelog (0021); portais com histórico
  e download de conta paga e nota em PDF/XML (0022); **editais e POC no escopo** (0023); instância dedicada com reversão
  (0024); nível de serviço Setor Público (0025); apps híbridos Capacitor com impressão Zebra (0026); BI nativo +
  Superset (0027); escopo ampliado — qualidade, GIS, omnichannel, dívida ativa, contabilidade/e-Sfinge, débito, SPC/
  protesto, cartão, ERP núcleo (almoxarifado e frota), telemetria, telefonia/totem (0028); equipe de 4–5 pessoas a
  partir de ago/2027 com prontidão de POC em jun/2028 (0029); cálculo offline e nota na emissão em campo (0031);
  **material da OS com alçada, reserva, baixa automática e saldo negativo permitido** (0032).
- **Editais** (PDFs fora do repositório, em `../editais/`): VISAN Videira PE 06/2026 e SAMAE São Ludgero PE 2/2026.
  Extração em `docs/levantamento/editais/18a`–`18c` (2.663 requisitos distintos) e matrizes por área `18d`–`18h`;
  consolidado em `18-ADERENCIA-EDITAIS-CONSOLIDADO.md`. Hoje o código atende ~3%, em parte ~21%; com o plano, a meta é
  aderência total na trilha P4 (62 entregas ED-xx, ≈220 semanas-pessoa). Itens de edital contrários à lei são
  respondidos com a regra legal e pedido de esclarecimento.
- **Revisão de estrutura SaaS** (relatório 17) e falhas de segurança do Ziip Cloud levantadas em passagem (tarefa
  separada sugerida).
- **Plano v2.6:** almoxarifado (ED-58) antecipado para a onda A, junto da OS completa (decisão do dono); prontidão de
  POC ≈199 semanas-pessoa, ≈4,6 pessoas-equivalentes para jun/2028.
- **Pendências do dono** (plano §2.4): escolha do piloto (P0.30, inclusive optantes de débito automático); custo e
  regime da equipe até o M8; parceiro de implantação presencial; manual/XSD de contingência da NFAg-e.
- **Próximo passo de execução:** P0 (Dockerfile, CI no GitHub Actions, PostgreSQL 17, migração do dev para disco local,
  renomeação `ziipsan`, correções de segurança S1–S6, AWS/R1), quando o dono autorizar o início da execução.

### 12/10/2026 — Plano v2.7 a v2.9: desenvolvimento local e lançamento em out/2028

- **ADR-0033:** todo o desenvolvimento é local (Docker Compose com PostgreSQL 17, Keycloak, emulador de S3 e cofre
  local) até concluir todas as fases. A AWS sa-east-1 (provedor já decidido, ADR-0011) só sobe no **lançamento
  comercial, em out/2028** (opção A). O código fica pronto para a AWS por interfaces com teste de contrato (P0.45).
- O antigo "go-live do piloto" (jun–jul/2027) virou **homologação local do piloto**, com dados sintéticos ou
  anonimizados; nenhum dado pessoal real entra na base local. Produção real e primeiro atestado só após o lançamento:
  atestado em mai–jul/2029, licitações com POC a partir do 3º tri/2029.
- Exceção registrada: **e-CNPJ da própria Ziip/TGP** no cofre local para homologação na produção restrita da SEFAZ;
  os XML assinados ficam em área separada e são apagados a cada ciclo de testes.
- **Equipe:** 4 pessoas a partir de ago/2027 (ADR-0029); 5ª vaga abre se a velocidade de 02/08 a 31/10/2027 ficar
  abaixo de 90% do planejado.
- Escopo continua **só documentação**; a execução do P0 local aguarda ordem do dono.

### 12/10/2026 — Projeto de enriquecimento do GIS de cadastro técnico (plano v2.10)

- Pesquisa em `docs/levantamento/gis/19a`–`19e`: Gisbase (Hidrogeo; documentos de Nova Trento; o edital 09/2020 da
  pasta é de São Ludgero), concorrentes nacionais e internacionais (Esri Utility Network, Giswater, TEKSI, Sansys,
  Waterfy), normativos (NBR 12586/12587 de cadastro, NR 12/2025 e NR 16/2026 da ANA, SIRGAS2000, ET-EDGV, ISO 19157),
  melhores práticas de modelo de dados e requisitos de GIS em 17 editais públicos.
- Projeto em `docs/arquitetura/GIS-CADASTRO-TECNICO.md` e **ADR-0034**: água, esgoto, drenagem e resíduos no mesmo
  modelo; catálogo brasileiro de fábrica extensível por tenant (curvas por ângulo, tês, reduções, caps, registros,
  ventosas, hidrantes, PV, bocas de lobo, contêineres, rotas); edição por pacotes com revisão, ciclo de vida e histórico;
  precisão por feição com meta por tenant; simbologia padrão Ziip sobrescrevível; QGIS com role por usuário e views;
  EPANET e SWMM juntos; núcleo do GIS entre P1 e P3 (GIS-01 a GIS-08) e avançado na trilha (GIS-09 a GIS-19).
- Esforço do GIS ≈61 semanas-pessoa (22 antes do P4, 39 na trilha). Total até 2028 passa de ≈220 para ≈260. Risco: o
  núcleo antes do P4 cai sobre o dono sozinho; pendências de capacidade no §2.4 do plano.
- MCPs Perplexity e Brave com chave inválida nesta sessão; pesquisa feita com WebSearch, WebFetch e markitdown.

### 12/10/2026 — Plano v2.11 e v2.12: decisões do GIS e planejamento sem gestão de equipe

- v2.11: base das ligações de água e esgoto no P2 (P2.23); rede real do piloto (sem imóveis) na homologação local;
  LGPD no mapa público com agregação por quadra/setor (mínimo 5 imóveis, parâmetro só ampliável); compra das NBR
  principais no P1; DGN v8 por conversão assistida; OGC API Features + WMS na onda B.
- v2.12 / **ADR-0035**: por decisão do dono, o plano não trata mais de equipe nem capacidade (ADR-0029 revogado); só o
  que será feito e como, com esforço por entrega como indicação e datas como metas. WMS da onda B pelo **GeoServer**
  (exceção registrada no ADR-0010).


### 14/10/2026 — Plano v2.13: ícones das telas e dos itens, símbolos do mapa e verticais

- Pedido do dono: criar os ícones das telas e dos itens; no futuro, outras verticais (GIS para saúde, para secretaria de
  segurança pública). Quatro decisões por cartão: ícones de interface em **Lucide mais próprios**; símbolos do mapa em
  **SVG próprio de fonte única** (sprite MapLibre, QGIS, SLD do GeoServer, PDF); **geração no desenvolvimento** com guia
  de estilo e aceite do dono por lote; **plataforma genérica com pacotes por vertical**, saneamento primeiro.
- Registrado em **ADR-0036** (ícones e símbolos) e **ADR-0037** (plataforma e verticais), nos projetos
  `docs/arquitetura/ICONES-E-SIMBOLOGIA.md` e `PLATAFORMA-E-VERTICAIS.md` e no plano v2.13: P0.46, P0.47, P1.29, P1.30,
  ajustes em P1.26 (`gis_domain`), GIS-06, ED-64 e ED-65, core `V9_7`, risco de generalização prematura e pendência do
  nome comercial das verticais (§2.4). ADRs 0016, 0017 e 0034 com linha de ajuste v2.13.
- Saúde e segurança pública ficam sem escopo, tarefa nem data: exigem ADR e análise jurídica de dados antes de qualquer
  requisito. Datas e marcos não mudam. Execução do P0 local segue aguardando ordem do dono.


### 14/10/2026 — Análise do GSAN, normas e mercado; plano v2.14

- Levantamentos novos em `docs/levantamento/gsan/`: 20a (cadastro e ligação, 64 regras e 205 parâmetros do GSAN),
  20e (lote, relatórios, gerencial e portal), 20f (52 demandas legais com vigência em 14/10/2026 e perfil de SC),
  20g (pesquisa web: GSAN GPL-3 6.11.5.1 ainda licitado em 2026; IPM, Betha e Fiorilli nos SAMAEs; NR 11, 13, 15 e 16
  da ANA; ARIS RN 19/2019). 20b, 20c e 20d em conclusão pelos agentes.
- Achados principais: NR 11 com verificação pelas agências a partir de 20/05/2027; ARIS veda corte por faturas vencidas
  há mais de 120 dias e exige aviso de 30 dias; juros de 1% a.m. base 30 excedem o teto de SC (0,033%/dia); lacunas de
  quitação anual, qualidade da água na conta, aviso pós-corte, compensação por corte indevido e vazamento oculto.
- Diretrizes do dono: **bifurcação normativa vira parâmetro** (ADR-0038; padrão mais protetivo; lei federal que proíbe
  continua travada, por cartão) e **unidade consumidora como raiz, toda solicitação ou alteração gera OS** (ADR-0039).
- Plano v2.14: nova P1.31 (núcleo de OS no P1, `V9_1` antecipada), P2.24 (quitação anual e qualidade da água na conta),
  P3.28 (vazamento e aferição); P1.2, P1.3, P3.3, P3.4, P3.20, ED-13, ED-16, ED-36 ajustadas; 16 parâmetros de bifurcação
  em PARAMETROS-DO-TENANT. Chave do Perplexity gravada no perfil (54 caracteres); falta reabrir o app para o MCP usá-la.


### 14/10/2026 — Consolidação dos levantamentos 20b, 20c e 20d; plano v2.15

- Levantamentos 20b (medição e faturamento), 20c (arrecadação, cobrança, parcelamento, negativação e contábil) e 20d
  (atendimento, OS e segurança) consolidados no planejamento; o 20c foi relido depois da regravação (sete regras
  confirmadas como fato; parâmetros de parcelamento mantidos).
- Oito decisões do dono por cartão: efeito da classificação na P3.1 e devolução na nova P3.29; parcelamentos ativos
  como prestações a vencer (P1.2, P2.10); seleção mínima do aviso na P3.4; cobrança terceirizada na nova ED-68 (onda
  C); permissões `recurso:ação` na P0.13 e escopo por área na nova P3.30; senha por tenant por autenticador próprio no
  realm único (ED-06); ouvidoria e interrupção programada na ED-35, com a área afetada na ED-54.
- **ADR-0040** registra permissões, escopo e senha por tenant. Perguntas dos levantamentos resolvidas pelas diretrizes
  sem novo cartão (núcleo de OS pela P1.31, usuário avulso como unidade consumidora, parcela bônus verificada na P1.1,
  NFAg-e da fatura agrupadora e conta de campo não devolvida como parâmetros, protocolo no módulo de atendimento).
- PARAMETROS-DO-TENANT: 70 chaves de bifurcação novas, 9 linhas ajustadas (chaves repetidas entre 20b, 20c e 20d
  unificadas) e travas legais novas, inclusive a revalidação do corte na execução, também offline.
- Plano v2.15: 18 tarefas ajustadas, P2.20 dependente da P1.31, GL.2 dependente da P3.29 e da P3.30; reservas `V1_8` e
  `V6_5`; dois riscos novos. Datas e marcos sem mudança. Só documentação; commit pelo dono.

### 15/10/2026 — Transferência de titularidade com débito

- Decisão do dono do produto: a transferência de titularidade da unidade consumidora sempre acontece, mesmo com débito;
  o débito é pessoal, fica com o titular que o contraiu e não acompanha o imóvel nem o novo titular.
  `unit.transfer_with_previous_debt` passa a classe a (travado), em PARAMETROS-DO-TENANT.

### 21/10/2026 — Confirmação dos padrões; ED-68 como pacote pendente; plano v2.16

- Perplexity e Brave funcionam após o reinício do app (busca testada).
- O dono confirmou os três padrões de fábrica: `bill.field_unreturned_rule = DIVERGENCE_QUEUE`,
  `fiscal.central_billing_invoice_mode = PER_UNIT` e `sac.deadline_counting_rule = CALENDAR`, com `BUSINESS` (dias
  úteis) disponível a cada tenant que o preferir.
- ED-68 (cobrança por empresa terceirizada) parada como pacote pendente: sem estimativa (proposta de G (4) semanas-pessoa
  não aprovada), fora dos totais da trilha e da prontidão de POC.
- Diretriz: a pesquisa de editais é suplemento de planejamento; o projeto não está sujeito a prazos, e as datas do plano
  são só indicação de sequência. Registrado em memória; nenhuma data de marco é cobrada como pendência.
- Plano v2.16 e PARAMETROS-DO-TENANT atualizados. Só documentação.

### 21/10/2026 — Módulo Certificações de qualidade; plano v2.17

- Análise das normas de certificação do setor: ISO 24510, 24511 e 24512 (2024) não são certificáveis; ISO/IEC 17025 é
  acreditável; normas NSF (60, 61, 372) certificam produto em contato com a água e não se aplicam ao software.
- Diretriz do dono: novo módulo "Certificações de qualidade", em que o cliente liga e desliga as normas, o sistema
  atende a todas (não conflitam) e as exigências específicas só valem com a norma ligada. Cartões: catálogo de
  voluntárias e obrigatórias numa lista só; estrutura no P1 a P3 e normas na trilha; tenant declara o escopo; perdas
  da NR 15 por dependência; esgoto e água bruta como pacote próprio; textos pagos só para curadoria.
- Dois agentes levantaram os requisitos: 21a (ISO 9001, 14001, 45001, 55001, ISO/IEC 27001 e 27701; 17 capacidades
  comuns) e 21b (normas do setor; 18 lacunas L-01 a L-18; 13 de 24 indicadores calculáveis com o plano). Achados:
  ISO 9001, 14001 e 27701 têm edição nova; CONAMA 503/2021 não se aplica a esgoto sanitário; cadastro de hidrômetros
  não representa a Portaria Inmetro 155/2022.
- ADR-0041 e CERTIFICACOES-DE-QUALIDADE gravados; plano v2.17 com P1.32, P2.25, P3.31 e ED-69 a ED-82 (tamanhos a
  estimar, totais da trilha não recalculados); 16 tarefas ajustadas; parâmetros `cert.*` e `meter.verification_interval_years`;
  reservas `V9_8` e `V21_x`. Só documentação.
- Pendentes de decisão do dono, sem trava: pacote de validação do sistema por versão (ISO/IEC 17025); certificação
  própria do Ziip além da ISO/IEC 27001 prevista.

### 22/10/2026 — Análise de lacunas e inovações em todo o projeto (22a a 22d)

- Pedido do dono: nova análise em todo o projeto para achar função, detalhamento de cadastro ou item esquecido e
  inovações, só para aprimorar o que já existe; pesquisa ampla na internet autorizada.
- Quatro frentes por agentes, com conferência no plano antes de apontar lacuna: 22a cadastro, comercial e relacionamento
  (40 achados, CAD-65 a CAD-104); 22b operação, campo, rede e inteligência (40, OPE-01 a OPE-40); 22c plataforma,
  segurança, integração e experiência (42, PLT-01 a PLT-42); 22d gestão, regulatório e inovação de mercado (34,
  GES-01 a GES-34). Total de 156 achados; nenhum contradiz ADR aceito.
- Relatórios gravados em docs/levantamento/lacunas/. O plano não foi alterado: as inclusões aguardam decisão do dono.
- A conferir: prazo do Pix Automático (Res. CMN 5.278/2025, lida em texto pelo 22c, contra a citação do 22d);
  artigos da NR 11 e NR 13 citados por resumo; achados de cobrança e faturamento do 22a contra 20b, 20c e 20e.

### 22/10/2026 — Incorporação dos 156 achados no plano; plano v2.18

- Cartões do dono: todos os achados dos levantamentos 22a a 22d entram no plano; NR do MTE obrigatórias no ADR-0041; módulo de perdas na onda B; ADR de política de IA; API pública com prioridade alta; desastre regional por Multi-AZ e cofre isolado na mesma região.
- Plano v2.18: 12 tarefas novas nas fases (P1.33, P2.26 a P2.29, P3.32 a P3.38), ED-83 a ED-103 na trilha (ED-102 e ED-103 como pacotes pendentes), 127 tarefas ajustadas com o ID de cada achado, ED-78 dependente da ED-85.
- ADR-0042 (política de IA) e mapa 22-INCORPORACAO-NO-PLANO gravados; ADR-0041 e CERTIFICACOES-DE-QUALIDADE ajustados. Só documentação.
- Pendências: ADR que altere o ADR-0022 (login gov.br e pagamento sem conta); tamanhos das tarefas novas e totais da trilha; reconferir contra 20b, 20c e 20e os achados de cobrança do 22a; conferir o prazo do Pix Automático (Res. CMN 5.278/2025 contra a citação do 22d).

### 22/10/2026 — Alvos da OS e hierarquia de ativos; plano v2.19

- Pedido do dono: o alvo da OS deve incluir booster, elevatórias e demais itens, pesquisando fontes técnicas.
- Fontes lidas: manual de controle patrimonial da ARSESP (NT.F-0064-2022, 385 páginas, 29 unidades de propriedade e centenas de unidades de adição e retirada, texto extraído localmente); Resolução ANA 302/2026 (NR 16); minuta de inventário da ANA; taxonomia da ISO 14224; resumos das NBR 12214 e 12208; glossário do SINISA; catálogo de GIS do projeto.
- Gravado ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS (classes de alvo, hierarquia de seis níveis, catálogo por instalação, equipamento, instrumento, edificação e frota). Plano v2.19: P1.27, P1.31, ED-14, ED-36 e ED-56 ajustadas. Só documentação.
- A confirmar: anexo do MCPSB da ANA (não publicado junto da resolução); NBR 12208:2020 marcada como cancelada no portal normas.com.br.

### 22/10/2026 — Conferências do catálogo de alvos; documento recriado

- A pedido do dono, o documento ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS foi removido e depois recriado com as correções.
- Confirmado: anexo do MCPSB da ANA (versão 2026, 363 páginas) lido; mesma estrutura de codificação da ARSESP (CIB, LOC, AC, BCP, BRV, NTS-SDS, UP, UAR, AT1 a AT5), 19 naturezas e subdivisões do serviço e tabela única nacional de vida útil. NBR 12208:2020 vigente (confirmação em 01/2025). ISO 14224 conferida na pré-visualização oficial.
- Pendências: valores de vida útil por UAR a conferir na tabela original; texto integral das NBR 12214 e 12208 depende da compra da ED-76.

### 22/10/2026 — Biblioteca de normas e legislação; plano v2.20

- Pedido do dono: carregar todas as normas e a legislação do setor numa área específica, acrescentando o que faltasse no projeto.
- Quatro levantamentos em paralelo (inventário do projeto, legislação federal e do setor, normas técnicas, agências reguladoras) e quatro lotes de verificação das normas que só o projeto citava.
- Catálogo consolidado em docs/normas: 1.676 normas; 1.298 em fonte oficial, 373 em fonte secundária, 5 sem confirmação; 114 para revisão; 12 com vigência futura. Leitura por 15 áreas em docs/normas/catalogo.
- Achados: NFAg-e obrigatória a partir de 01/12/2026; Decreto 13.135/2026 (SAC) em 02/11/2026; Res. BCB 587/2026 (cobrança híbrida) em 01/02/2027 e 565/2026 (débito automático) em 01/07/2027; CONAMA 430/2011 em revogação aprovada e sem publicação; normas de agências com regras que conflitam com a Lei 14.015/2020 (corte às sextas) e a Lei 14.898/2024 (tarifa social); 71 revogadas, 90 substituídas e várias citações do projeto com número, emissor ou escopo errados.
- Plano v2.20: nova P1.34 (área Normas e legislação), ajustes em ED-76, P3.15 e P1.32; ADR-0041 com o item 15. Lista de correções a aplicar nos documentos em docs/normas/correcoes-aos-documentos.md (não aplicadas). Só documentação.
- Pendências: aplicar as correções aos documentos; revisar os itens a confirmar (docs/normas/pendencias-consolidadas.md); contraparte jurídica sobre normas de agências que conflitam com lei federal e sobre as leis estaduais de corte (ADI 7.725/TO) e a Lei Distrital 5.990/2017.

### 22/10/2026 — Pendências da biblioteca de normas; plano v2.21

- Pendências pedidas pelo dono: aplicar as correções aos documentos, revisar os itens a confirmar e tratar as dúvidas jurídicas.
- Correções: cinco lotes de agentes, com arquivos distintos por lote, corrigiram as citações nos documentos (184 normas apuradas na primeira verificação; relatórios em docs/normas/aplicacao-correcoes). O diário não foi reescrito: a Lei Distrital 5.990/2017, citada acima em 22/10/2026 sem a ressalva, foi declarada inconstitucional (TJDFT 2019; STF, RE 1.314.679, 20/03/2024, segundo o SINJ-DF), e o catálogo a registra assim.
- Reverificação dos 114 itens a confirmar: 13 continuam pendentes; 6 não são norma em vigor. Catálogo final: 1.676 normas, 1.330 em fonte oficial e 346 em fonte secundária.
- Contradições resolvidas em fonte oficial: débito automático (01/01/2027 para convênios de serviços públicos, Res. CMN 5.278/2025; 01/07/2027 para o novo regime, Res. BCB 565/2026) e MP 1.393/2026 (alcança dívidas com prestadores de serviços públicos).
- Roteiro de 15 perguntas para parecer jurídico em docs/normas/questoes-para-parecer-juridico.md (não é parecer). Decisões a rever em docs/normas/decisoes-a-rever.md. Plano v2.21. Só documentação.
- Pendências do dono: decidir sobre as premissas em decisoes-a-rever.md; obter o parecer jurídico; regenerar o catálogo com scripts/normas/consolidar-catalogo.js quando houver nova verificação.

### 28/10/2026 — Revisão geral do projeto (23a a 23e)

- Pedido do dono: revisar o projeto todo e apontar melhorias de campos, cadastros, funções e módulos.
- Cinco frentes por agentes, sem repetir os achados 22a a 22d já incorporados: 23a coerência do plano (23 achados,
  COE-01 a COE-23); 23b modelo de dados e cadastros campo a campo (28 entidades, 40 achados, DAD-01 a DAD-40);
  23c código existente contra o plano (42 achados, COD-01 a COD-42); 23d módulos, funções e jornadas de 16 perfis
  (120 jornadas, 39 achados, JOR-01 a JOR-39); 23e rastreabilidade de 326 normas obrigatórias contra as tarefas
  (501 pares norma e obrigação: 47,5% cobertos, 47,7% parciais, 4,8% sem tarefa; 33 achados RAS).
- Destaques: alteração de unidade grava fora de OS no código (DAD-01); a API descarta campos que a tela envia
  (COD-10, DAD-02); regras de consumo duplicadas em billing e metering (COD-07); colisão de schema no provisionamento
  (COD-02); 28 linhas do plano com coluna a mais que esconde ajustes (COE-01); entregas da onda B listadas na onda C
  (COE-02); LGPD do titular só no pacote voluntário (JOR-15); CNPJ alfanumérico ausente (RAS-03); cashback de CBS e
  IBS só como título (RAS-02).
- Relatórios em docs/levantamento/revisao-geral. Plano e código não alterados. Decisões do dono a colher por cartão.

### 28/10/2026 — Plano v3.0 consolidado

- Cartões do dono: reescrever o plano numa v3.0 consolidada; aprovar todas as recomendações dos relatórios 23a a 23e
  (perfis de fábrica por função, LGPD do titular para todo tenant, limpeza do código antes da renomeação, hierarquia de
  ativos no P1); ADR-0043 (login gov.br só para tenant público e pagamento sem conta por código impresso, alterando o
  ADR-0022); ADR-0044 (o projeto não tem prazos); não reestimar agora; nome na conta só do usuário titular, como
  parâmetro; protocolo sempre antes da OS. As 10 decisões da estrutura (PD-01 a PD-10) foram aceitas como recomendadas.
- Método: extração das 270 tarefas e dos 181 ajustes da v2.21 (scripts/plano/extrair-tarefas.js); estrutura v3.0 e mapa
  de donos por um agente (286 tarefas, 18 novas, 2 fundidas; 333 achados com destino, 311 com tarefa dona); redação das
  tarefas por sete agentes, com os ajustes incorporados ao aceite; seções narrativas e ADRs por um oitavo; montagem e
  checagem por scripts/plano/montar-v3.js (0 erros: nenhuma dependência inexistente ou posterior, todo achado com dona).
- Correções da montagem: ED-88 e ED-92 no grupo B1 (SG-154 crítico na POC do SAMAE); curadoria das normas (ED-76) antes
  dos pacotes ISO; dependências antes só no texto declaradas; versões Flyway alinhadas ao mapa novo (P0.47 no core V6_1,
  usuários na V1_9, instância dedicada no core V13 e as reservas das tarefas novas).
- Documentos: plano canônico substituído pela v3.0; histórico de versões em docs/plano/HISTORICO-DE-VERSOES.md; ADRs
  0043 a 0046 (0046 proposto); notas aplicadas ao ADR-0040 (NIST 800-63B-4), ADR-0035, ADR-0037 e ao índice (nova regra 2);
  parâmetros bill.holder_name_display, portal.* e pay.own_cashier_enabled. Material de trabalho em docs/plano-v3.
- Pendências do dono: as da seção 2.4 do plano (pareceres jurídicos na ordem do PD-10, decisões a rever das normas e as
  dez abertas na redação). Só documentação; o P0 aguarda ordem.

### 29/10/2026 — Decisões sobre a seção 2.4 do plano

- Detalhamento da seção 2.4 (9 decisões em aberto, 5 pareceres jurídicos, 31 premissas a rever e 10 pendências da redação).
- Cartões do dono: ADR-0046 aceito (supervisório somente leitura, por REST e arquivo, sem broker; ajuste no ADR-0010);
  ASVS nível 2 em geral e nível 3 em autenticação, pagamento e dados pessoais (P3.8); unidade consumidora sem usuário
  vigente com padrão sem titular e fatura retida (P1.41); pgRouting na roteirização de equipes só com ADR próprio (ED-36).
- Seguem em aberto: "notas do tenant" na Central (P4.8) e alinhamento da instância dedicada (LC.15) ao ADR-0024.
- Plano remontado por scripts/plano/montar-v3.js (286 tarefas, 0 erros). Só documentação.

### 31/10/2026 — Decisão do dono: PostGIS 3.7.0 em vez de 3.5 (P0.4)

- Decisão do dono (31/10/2026, registrada pelo coordenador em execução): atualizar o projeto para
  PostGIS 3.7.0 — a versão entregue pelo PGDG — em vez da 3.5 do plano original. Motivo: 3.5 não está
  disponível no repositório PGDG. O major do PostgreSQL segue 17, sem mudança.
- Verificação local: PostgreSQL 17.11 online (cluster 17, porta 5433; cluster 16 em 5432 intocado) com
  PostGIS 3.7.0 confirmado por `PostGIS_Full_Version()` (POSTGIS 3.7.0, PGSQL 170, GEOS 3.15.0). A
  verificação do `scripts/ci-linux.sh` em instância dedicada PG17 (porta 55433) passa a valer contra
  3.7.0: backend BUILD SUCCESS com 973 testes, 0 falhas, e frontend verde (vitest 134 passed + build),
  com a etapa de portas vermelha como esperado até a P0.7.
- Imagem Docker PENDENTE, sem tag inventada: verificado na API do Docker Hub em 31/10/2026
  (`/v2/repositories/postgis/postgis/tags?name=17-3.7` e `name=17-3.7.0`: 0 resultados; na listagem
  geral só RCs de 3.7 para PG18/19, nenhum para PG17). Logo `docker-compose.yml` e
  `scripts/reset-it-db.sh` seguem com `postgis/postgis:15-3.4` até a tag existir, com comentário de
  pendência no compose; a P0.4 quanto à imagem do compose NÃO está cumprida. `17-3.6` não foi usado
  como substituto, pois contraria a decisão do dono.
- Diferença 3.5 → 3.7.0 observada, não mascarada: a partir do PostGIS 3.7, `address_standardizer` e
  `postgis_tiger_geocoder` deixam de ser fornecidos pelo PostGIS. Busca no projeto em 31/10/2026 não
  encontrou nenhum teste/migration dependendo dessas extensões (só `CREATE EXTENSION postgis` em
  `infra/postgres/init/sql/security.sql`, sem checagem de versão), e a suíte completa passou em
  PG17/3.7.0 — nenhuma dependência de comportamento específico do 3.5 foi encontrada.
- Documentos atualizados: plano canônico (P0.4 e seção de decisões), `docs/arquitetura/ARQUITETURA-ALVO.md`,
  `scripts/ci-linux.sh` (cabeçalho), comentário de pendência no `docker-compose.yml` e seção de ajuste
  no ADR-0013 (texto original preservado). Arquivos históricos (`docs/plano-v3/`, levantamentos e
  ADRs 0011/0024/0033/0034) mantêm o texto com 3.5 e leem-se como supersedidos por esta decisão.
- Nada mais foi alterado por conta desta decisão: major PG segue 17, nenhuma outra versão tocada.

### 31/10/2026 — Runtime de container desta VM: Podman (Docker daemon NÃO consertado)

- O daemon Docker continua inutilizável nesta VM: pull EOF no registry e runc setns
  operation not permitted, mesmo com --privileged e seccomp unconfined. NÃO afirmar que o Docker
  foi consertado — ele não foi.
- Caminho que funciona, verificado pelo agente principal e reconfirmado pelo coordenador:
  Podman 4.9.3 + crun 1.14.1, sempre com `--storage-driver=vfs --runtime crun
  --cgroups=disabled --network=host`. Provas do coordenador: hello-world OK; postgres:17 em
  container aceitou conexões em 127.0.0.1:55444 (log 'database system is ready', pg_isready OK,
  SELECT 1 OK), depois parado e removido, sem container restante. Pulls funcionam via
  Podman/Skopeo, não via cliente do daemon.
- Limitações desta VM: sem bridge (sem DNS de serviço `postgres-primary`/`keycloak` — usar
  127.0.0.1 com portas distintas por serviço, definidas na configuração do serviço, não em
  mapeamento `-p`, que é ignorado com `--network=host`); sem cgroups (limites de recursos do
  compose não se aplicam); storage vfs (mais lento, mais disco); `podman-compose` não instalado.
- Avaliação P0.27/P0.29: uma variante Podman local SERÁ necessária para verificar P0.27 (e P0.29,
  com 2 réplicas do Keycloak em portas distintas) nesta VM, mas só como adaptação de ambiente, a
  criar na hora da P0.27 — não agora e não substituindo o `docker-compose.yml` do PC normal
  (forma recomendada: `scripts/dev-up-podman.sh` com `podman run` por serviço, ou instalar
  `podman-compose`). O compose Docker segue intacto e canônico para o PC normal. Para P0.1–P0.25,
  o Podman não é necessário: a verificação já é feita no host (Java + PG17 dedicado).

### 31/10/2026 — P0.25 executada (turno 4 do coordenador; CI completo ainda pendente de PG17 nesta VM)
- Ordem segura seguida: ModuleRegistry/AuditListener → SINIR → SCADA → Keycloak → billing →
  Jackson → V1_1/V1_4 + sementes → vetor comum. Suíte verde por módulo afetado exigida antes
  de qualquer commit desta tarefa; o commit da P0.25 só sai com os testes dos módulos
  afetados verdes (o CI completo em PG17 depende da reinstalação do PostgreSQL 17 nesta VM,
  ver abaixo).
- SINIR: SinirIntegrationService apagado; MtrService sem campo/chamadas/partyDocuments/
  recordSinir; testes reescritos (coleta e destinação não tocam em sinir; o PDF do CDF segue
  recebendo o documento do gerador, uso legítimo preservado; advanceChain segue decifrando
  o CPF para o hash da cadeia). Campos sinir_* da entidade WasteMtr e da migration V2_4
  permanecem, sem escrita em modo assistido — a V2_4 não é alvo da P0.25; nenhum novo status
  foi inventado para eles.
- SCADA: ScadaIntegrationService, ScadaTopic e os 2 testes apagados; Paho removido do pom da
  quality (comentário P0.25, ED-17). ScadaDataPoint/ScadaTag e repositórios permanecem: o
  plano só manda apagar IntegrationService + Topic + Paho.
- Keycloak: KeycloakAdminService e KeycloakConfig apagados; keycloak-admin-client removido
  do pom raiz e do platform; TenantProvisioningService sem campo, chamada, seeds
  (seedRoles/seedAdminUser/seedTenantMetadata/initializeTenantData), sem
  isSchemaInitialized/listInitializedSchemas e sem validação de hash bcrypt no request — os
  campos admin saíram do TenantProvisioningRequest e de todos os chamadores
  (ProvisionedTenant, RegistryApiE2ETest, TenantConnectionProviderTest, DevTenantBootstrap).
  Usuário e papéis voltam na P0.13 (catálogo de usuários), não como seed; o acesso é pelo
  Keycloak do ambiente, não por realm por tenant. set_config passou a ser por parâmetro em
  registerTenantInCatalog e writeProvisioningAudit; a linha de log DEBUG em
  writeProvisioningAudit foi removida.
- Billing: as 3 cópias (NonMeteredConsumption, ExpectedReadingRange, CondominiumApportionment)
  e seus testes do billing foram apagados; o metering é o único dono. A entrada da tarifa do
  billing foi renomeada de EconomyGroup para TariffGroup (decisão de coordenador registrada:
  migração verbatim para o tipo do metering perderia categoryId/subcategoryId UUID e as
  economias sociais; um dono por nome resolve a colisão sem perda de informação).
- Vetor comum metering × billing: novo MeteringBillingVectorTest no sigsan-app (único módulo
  que depende dos dois; o billing não depende do metering). Vetores: não medido por tarifa
  2 economias RES × mínimo 10 = 20 nos dois lados; faixa esperada 100–200; rateio de
  condomínio macro 100, micros 30+30, a ratear 40, 20 por economia, alimentando o billing
  com 50 m³ por micro.
- Jackson: FreeFieldLayout e AgreementBarcodeLayout migrados para o Json do core (Jackson 3,
  tools.jackson); jackson-databind (Jackson 2) removido do pom do core; 2 testes que usavam
  ObjectMapper Jackson 2 migrados para tools.jackson. GIS mantém artefatos Jackson 2
  (jackson-datatype-jsr310/jdk8) sem ObjectMapper próprio — fora do escopo literal da P0.25.
- V1_1 reescrita: só consumer_units (sem owner_id/responsible_id, que referenciavam users)
  e addresses, com RLS forçada e políticas só dessas tabelas. tenant_metadata, users, roles,
  user_roles, documents e service_orders saíram (users/roles voltam na P0.13; service_orders
  na P1.31/V9_1). V1_4: regras de redação de users/documents/service_orders removidas;
  ficam as de addresses.
- Decisões de coordenador registradas aqui: tenant_schema MANTIDO (V1_2/register_tenant_table
  e V3/V4/V6 dependem dele; contexto explícito do tenant no worker, P1.9); grantConsent sem
  incremento manual de version (version é @Version do JPA em BaseEntity — o Hibernate
  incrementa no flush; o incremento manual dobraria o salto).
- Ambiente do turno 4: esta instância de VM foi substituída e chegou sem Java/Maven/Postgres
  17/Podman (só ~/workspace persistiu); o replay do sistema restaurou Java 21.0.12.1, Maven
  3.8.7, PostgreSQL 16 (5432) e Podman, mas NÃO o PostgreSQL 17. Novo bloqueio encontrado e
  resolvido: o truststore Java estava vazio (cacerts ausente; erro trustAnchors) e travava o
  download do Maven Central — resolvido com update-ca-certificates -f (cacerts recriado,
  incluindo o CA do proxy hatch-egress-ca). A instalação do PostgreSQL 17 via PGDG segue
  pendente: o apt-get update está lento (em andamento desde 23:43) e o PG17 ainda não está
  instalado nesta instância; o CI completo em PG17 (segunda execução com contorno
  jackson-bom) ainda não foi revalidado aqui.

### 31/10/2026 — P0.7 executada (turno 4)
- Compose: removidos timescaledb, redis, kafka, zookeeper e vault dev; ficam
  postgres-primary, keycloak (start-dev até a P0.29) e minio só no perfil
  "storage", com portas em 127.0.0.1 e senha exigida no .env (sem fallback).
  Imagem do Postgres segue 15-3.4 — isso é P0.4, não P0.7.
- Blocos comentados e volumes órfãos removidos do arquivo ativo (ADR-0010).
- Verificação de portas: scripts/check-compose-ports.py (lê o compose, ignora
  comentários, falha com porta fora de 127.0.0.1, incluindo forma curta sem
  host) + scripts/test-check-compose-ports.py (positivo + 3 negativos); o
  ci-linux invoca o script na etapa (d).
- infra/kubernetes e infra/terraform arquivados em infra/_arquivado/ com
  LEIA-ME; segredos literais das cópias arquivadas substituídos por
  REDACTED-ARQUIVADO (o original segue no histórico do Git).
- Divergência P0.7 × ADR-0010 (Vault): o ADR mandava 6 serviços para profiles:
  (incluindo o Vault); a P0.7 manda remover 5 e deixar só o SeaweedFS em
  perfil. Seguida a P0.7 (Vault removido); o ADR não foi alterado sem decisão
  do dono.

### 01/11/2026 — P0.33 executada (turno 4; tag pre-rename antes, ci-linux total verde depois)
- Tag `pre-rename` criada antes de qualquer renomeação, no commit 613a7f4
  (P0.7 verde), sem branch aberto.
- Renomeação mecânica sigsan/Sigsan/SIGSAN → ziipsan/Ziipsan/ZIIPSAN em
  conteúdo e em caminhos (pacotes br.com.ziipsan, módulos, artifactIds,
  roles ziipsan_app/ziipsan_migrator, realm ziipsan, env ZIIPSAN_*),
  exceto docs/ e infra/_arquivado/ (arquivo congelado da P0.7, fora do
  escopo da busca residual; o histórico do Git preserva o anterior).
  Busca residual: zero "sigsan" fora de docs/_arquivado/target/node_modules
  (antes: 1.743 ocorrências em 472 arquivos, fora docs/_arquivado).
- Códigos de problema em inglês pela tabela da P0.33 e prefixo
  urn:ziipsan:problem:; title/detail/mensagens seguem em português.
  titleFor passa a tratar 401, 413 e 429 explicitamente.
- HKDF de EnvDerivedTenantKeyProvider em ziipsan/enc/v1 e ziipsan/bidx/v1,
  com teste de vetor fixo congelado (valores calculados por implementação
  independente, RFC 5869, com chave mestra e tenant fixos do teste).
- Novo ProblemCodeContractTest (app): varre o fonte e falha com código fora
  da tabela, fora do formato, com prefixo/código antigo ou com "sigsan"
  residual (o próprio arquivo do teste é excluído da varredura, pois lista
  os códigos antigos como dados do teste).
- Verificação: ci-linux total verde pós-P0.33 — backend 907 testes, 0
  falhas (903 pós-P0.25 + 1 vetor HKDF congelado + 3 do teste de contrato),
  frontend 134 passed + build, portas OK.
- Incidente no meio da verificação (não é do código): /tmp (tmpfs de 512M)
  encheu com o diretório temporário do frontend da execução anterior
  (290M), porque o ci-linux saía antes do rm em caso de falha no
  typecheck; o node_modules seguinte veio truncado (date-fns/lucide-react)
  e o typecheck falhou. Resolvido limpando /tmp e corrigindo o ci-linux
  para remover o FE no trap de limpeza, em qualquer saída.
- Decisão registrada: infra/_arquivado/ não é renomeado (arquivo da P0.7,
  congelado por definição); a busca residual da P0.33 o exclui junto com
  docs/, target/ e node_modules.

### 01/11/2026 — Gate P0.25/P0.7/P0.33: reprovação estreita dos 2 revisores e correção (turno 4)
- Veredito dos 2 revisores independentes sobre o HEAD 977c40a: REPROVADO
  para o gate, por um único item estreito (o resto aprovado: P0.25 e P0.7
  cumpridas, números do CI turn4g conferem, nenhum segredo novo, nenhum
  teste vermelho). O item: a guarda da P0.33 não cumpria o especificado.
- Bloqueio 1: residual "sigsan" real num comentário
  (EnvDerivedTenantKeyProviderTest.java:60, "voltar a sigsan/...") — a
  afirmação "busca residual zero" da entrada anterior estava, portanto,
  incorreta nesse ponto, e fica aqui corrigida, não mascarada.
- Bloqueio 2: ProblemCodeContractTest.repoRoot() parava no próprio módulo
  app, porque o pom do app tem "ziipsan" como artifactId do PARENT; a
  varredura cobria só platform/ziipsan-app/**, e por isso passou no CI
  com o residual presente em outro módulo (security).
- Correção (commit 688c545, só 2 arquivos de teste, nenhum código de
  produção): comentário reescrito sem "sigsan" ("voltar ao contexto
  antigo"); repoRoot() agora exige artifactId ziipsan + packaging pom
  (a raiz agregadora). Prova negativa: arquivo de prova com "sigsan" em
  shared/ziipsan-core/ fez noSigsanRemainsInSource falhar apontando o
  arquivo; a prova foi removida e não commitada. Após a correção, o grep
  residual fora de docs/_arquivado só retorna o próprio teste de
  contrato, que se autoexclui da varredura.
- Verificação pós-correção: ci-linux total verde (log
  /tmp/ci-linux-turn4h.log, exit 0) — backend 907 testes, 0 falhas
  (core 36, security 29, platform 20, quality 2, losses 24, gis 31,
  mtr 75, lgpd 15, registry 37, billing 185, metering 200, collection
  165, app 88), frontend 134 passed + build, portas OK.
- Push continua bloqueado até a re-revisão do gate corrigido aprovar;
  nenhum push foi feito neste ponto.

### 07/11/2026 — Retomada: rodada 8 da CI total verde, integração P2a fechada

- **Registro do intervalo.** A execução de 03–04/11/2026 (camadas P1a–P1d e a
  primeira leva do P2) ficou registrada só nos commits; este diário não foi
  atualizado na época. Esta entrada resume esse intervalo fielmente aos
  commits, sem reconstruir o que não foi registrado.
- **P1 entregue** nas camadas a, b, c e d, cada uma com a sua integração e as
  suas rodadas de CI total (cadastro R2 completo, OS e protocolo, GIS,
  Central Ziip v0, suporte v0, importadores, perfis de fábrica, certificações,
  normas, marca do tenant, situações do titular, entre as demais tarefas
  P1.1–P1.41 dos commits do período).
- **P2, primeira leva (camada P2a):** P2.1 (fundação de lote), P2.2 (pools por
  papel), P2.3 (tabelas de fato particionadas), P2.4 (CBS/IBS com vigência),
  P2.30 (motor único de modelos de documento e mensagem), P2.12 (cobrança do
  SaaS por contrato), P2.13 (NFS-e da venda SaaS), P2.14 (área do tenant),
  P2.15 (SLA de suporte e CSAT), P2.25 (informação documentada), P2.26 (API
  pública e webhooks) e P2.27 (domínio próprio do tenant).
- **Integração P2a:** rodadas 1 a 7 da CI total geraram a sequência de
  `fix(p2a-integracao)` (manifesto, RLS das partições, harness dos ITs da
  P2.3, teste de volume e limpeza do resíduo do tenant compartilhado). O
  último commit, `cbef2f5` (04/11/2026), corrigiu a ordem esperada do menu e
  deixou registrado: "a prova integral fica para a rodada 8 da CI total".
  A rodada 8 não tinha rodado — o trabalho parou aí.
- **Retomada em 07/11/2026.** A cópia local do dono estava no commit
  `dc7ca94` (29/10/2026), com a renomeação `sigsan`→`ziipsan` aplicada só
  nos arquivos, sem commit; por isso a primeira tentativa da CI leu o POM
  antigo. O estado solto foi guardado em `git stash -u` (rede de segurança,
  sem pop) e a cópia foi atualizada por `git pull` até `cbef2f5`.
- **Rodada 8 executada no PC do dono** com `scripts/ci.sh` sobre o HEAD
  `cbef2f5`, terminando em `== OK` (log `rodada8-ci-total.log`): banco de
  integração virgem, `mvn clean verify`, frontend e checagens de marca —
  o script para na primeira falha, logo o `== OK` é a prova integral que
  faltava. **Integração P2a fechada.**
- **Resíduo conhecido, não bloqueante:** o `npm audit` do frontend aponta
  5 vulnerabilidades (2 moderadas, 2 altas, 1 crítica) em `maplibre-gl`,
  `react-router` e `zod`; a correção exige versão principal nova — decisão
  do dono já registrada como pendente desde as fundações (seção 18,
  "Pendências depois das fundações", item 3).
- **Próxima tarefa:** P2.5 — medição, ciclo e conta (`V4_3` e `V4_4`), da
  qual dependem P2.6, P2.7, P2.9, P2.10, P2.31, P2.32 e P2.34.

### 07/11/2026 — Ondas 1 a 4 publicadas: lote de 54 pacotes na `main`

- **Lote publicado** (autorização expressa do dono para pushes automáticos):
  os 54 pacotes das Ondas 1–4 produzidos e revisados localmente subiram para
  a `main` em commits `feat(<tarefa>)` por tarefa (fatiados em partes quando
  o pacote era grande), do HEAD `0fbcd22` ao **HEAD final `9e400e0`**.
  Fecham o lote a GL.3 (`8b64f36`/`9e400e0`) e a ED-14 (`3d5872b`).
- **Backup da camada:** branch `backup/2026-11-07-pos-ondas-1-a-4` criada
  apontando para `9e400e0`, conforme a política de backup do projeto.
- **Ressalvas registradas:**
  - A ED-04 subiu **sem** a migration `V11__tenant_isolation_environment.sql`:
    a numeração V11 colide com a `V11__app_device.sql` da ED-18 e será
    resolvida na série da LC.15 (decisão registrada no pacote da ED-04).
  - Dois arquivos de workflow **não** subiram — `.github/workflows/ci.yml`
    (ED-08) e `.github/workflows/campo-android.yml` (ED-18): a integração do
    GitHub em uso não tem permissão de workflows (HTTP 403). Aguardam entrada
    pelo clone local do dono ou a concessão dessa permissão ao app.
  - Caches `__pycache__/*.pyc` dos pacotes P2.31 e P3.23 ficaram fora do
    push (bytecode, não código do projeto).
  - Durante a publicação, o conector do GitHub apresentou timeouts longos
    com confirmação tardia: a P2.16 ganhou commits duplicados em conteúdo
    (`23f7f18`, `fe58530`, `b43db99`) e um reparo (`0730b58`) com os arquivos
    restantes — a tarefa está íntegra no repositório (84 arquivos).
  - A branch `teste-push-p216`, criada durante o diagnóstico dos timeouts,
    pode ser removida.
- **Próximo passo:** rodada da CI total sobre o HEAD `9e400e0` no PC do dono
  (`git pull` + `scripts/ci.sh`) para fechar a camada; as decisões pendentes
  do dono seguem no arquivo de trabalho `DECISOES-DO-DONO.md`.

### 08/11/2026 — Push de registro: Ondas 1–11 completas na `main`

- **Retomada de 07/11/2026:** a produção foi retomada com frentes paralelas
  por tarefa e portão de revisão cruzada independente (nenhum pacote conta
  como concluído sem veredito formal de outra frente). No mesmo dia, a
  **rodada 8 da CI total passou verde** no PC do dono (HEAD `cbef2f5`),
  fechando a integração da camada P2a; resíduo não bloqueante: `npm audit`
  com 5 vulnerabilidades (maplibre-gl, react-router, zod) cuja correção
  exige versões principais novas.
- **Ondas 1–11 concluídas (08/11/2026, ~02h20):** todas as 118 tarefas
  executáveis do plano v3.0 foram produzidas em pacotes locais com revisão
  cruzada e veredito formal — placar da retomada: **217 de 286 tarefas
  executadas (75,9%)**. O backlog de revisões foi zerado; defeitos reais
  pegos pela revisão (entre eles o da P3.41, com alvos de indicadores
  quebrados, e o da ED-29, com o corte de esgoto recusado pela API) foram
  corrigidos e re-conferidos antes da liberação.
- **Período de trabalho no Drive:** por ordem do dono (07/11/2026, 22h45),
  os pushes ao GitHub ficaram suspensos e o trabalho passou a viver no
  Google Drive (pasta "ZiipSan — Trabalho", arquivo `ziipsan-trabalho.zip`
  atualizado a cada 2h). No GitHub, commit só ao final ou a pedido expresso.
- **Este push de registro** (ordem expressa do dono em 08/11/2026,
  "registra tudo e sube pro git", levantando a suspensão só para esta
  operação): os 45 pacotes aprovados que aguardavam subiram em **59 lotes**
  (`feat(lote final): <tarefas> (parte N/59)`), 999 arquivos no total.
  O lote 1 pousou às 04h15 (`fb2f937`, completando a ED-06 e deixando a
  ED-11 parcial); em seguida o **canal de escrita do conector ficou preso**
  por ~8h — travamento idêntico para 87 KB, 26 KB e um arquivo único de
  4.989 bytes, com as leituras normais — até a retomada manual ordenada
  pelo dono às 12h02 ("tenta novamente"): a sonda pousou às 12h05
  (`7b19662`) e os lotes 2–59 pousaram em sequência, fechando em
  **HEAD `ab64927`** (59/59 lotes). Com isso, **a main passa de 173 para
  217 de 286 tarefas (75,9%)**: tudo o que está executado está agora na
  `main`, com ED-06 e ED-11 completas.
- **Exclusões aplicadas neste push:** `.github/workflows/cliente-mobile.yml`
  (ED-39) não subiu — a integração do GitHub em uso não tem permissão de
  workflows (HTTP 403), mesmo bloqueio já registrado para os workflows da
  ED-08 e da ED-18; e 2 arquivos `__pycache__/*.pyc` (ED-11) ficaram fora
  (bytecode, não código do projeto). Nenhum pacote aprovado foi retido.
- **Backup da camada:** branch `backup/2026-11-08-pos-ondas-1-a-11` criada
  sobre o HEAD final desta operação (este commit de diário incluso).
- **CI total:** a **rodada 9 passou verde** no PC do dono em 08/11/2026,
  mas sobre o HEAD do pull que ele fez enquanto os lotes ainda pousavam;
  a rodada definitiva da camada completa se repete agora, com o push
  fechado e um novo `git pull`. Enquanto ela não roda sobre o HEAD final,
  "na main" não é "provado integrado" — em especial a P2.16, que está na
  main com numeração de partes incoerente (só a CI diz se falta algo).
- **Decisões do dono em 08/11/2026 (bloqueantes):** (1) CI total — ele roda
  no PC dele e cola o log; (2) piloto P0.30 — ele indicará o nome da
  prestadora (aguardando); (3) RAS-06 — débito automático confirmado,
  caminho duplo: campanha de conversão ao Pix Automático (P4.5) e FEBRABAN
  150 mantido como legado para a base atual (débito novo só por Pix
  Automático); com isso, **P3.24 e ED-27 foram produzidas e aprovadas com
  revisão cruzada ainda hoje**, em pacotes locais fora do plano deste
  push, aguardando push posterior; o protesto de CDA da ED-31 segue
  desligado (o parecer PD-10 específico continua inexistente); (4) P3.9 —
  produzir as minutas agora, a partir da especificação completa do aceite
  (ADRs 0002/0019/0020/0033 + RAS-20); (5) `npm audit` — atualizar
  maplibre-gl, react-router/react-router-dom e zod nas versões principais
  novas.
- **Pendências vivas:** piloto P0.30 (trava P3.13, GL.4 e toda a trilha
  LC); minutas da P3.9 (seguram a ED-05 completa e ~42 ED condicionadas);
  as 52 tarefas condicionadas e as 16 bloqueadas-raiz do plano, e as sob
  demanda (P4.6, ED-51, ED-68, ED-102, ED-103, ED-104); a rodada definitiva
  da CI total sobre o HEAD final; e o push posterior da P3.24 e da ED-27.
### 08/11/2026 — Push 2: P3.24, ED-27, P3.9, npm audit e correções da 1ª revisão geral

- **Ordem expressa do dono** (08/11/2026, 14h26): "pode subir tudo
  agora", com a trava de aprovação por deploy aposentada (ele marcou
  "Permitir sempre" no primeiro cartão). Subiram todos os pacotes
  aprovados que aguardavam push, em 16 lotes sobre o HEAD `19f5890`,
  mais este registro. Placar na `main`: **220/286 tarefas (76,9%)**
  com commit de entrega; nenhum pacote aprovado ficou retido.
- **P3.24 e ED-27 na `main`** (decisão RAS-06 do dono: caminho duplo —
  conversão ao Pix Automático pela P4.5 e FEBRABAN 150 mantido como
  legado para a base atual; débito novo só por Pix Automático):
  débito automático FEBRABAN 150 para autorizações legadas no
  `ziipsan-collection` (19 arquivos, migration `V6_4` reservada) e o
  subpacote `collection.debit` da ED-27 (11 arquivos, SQL em
  `db/pendente/`). Os dois foram produzidos e aprovados com revisão
  cruzada hoje (confirmações independentes 24/24 e 26/26).
- **P3.9 produzida e aprovada, incluída neste push:** as 6 minutas do
  aceite (contrato SaaS, DPA, SLA/suporte, suboperadores e
  transferência internacional, termo de homologação local e termos de
  uso) e o índice de rastreabilidade estão em `docs/contratos/`,
  construídos das ADRs 0002/0019/0020/0033 e do RAS-20, sem cláusula
  inventada (o que não tem fonte ficou marcado `[A DEFINIR]`).
  Aprovação do piloto pendente por desenho; assinatura só na LC.9.
  Dos 22 marcadores `[A DEFINIR NA P3.9]` da ED-05, **8 resolvidos**
  pelas minutas e **14 abertos por falta de fonte** — fecham na
  contratação com o piloto.
- **Pacote npm audit aplicado:** `maplibre-gl` 6.16.0 → 6.17.0
  (`package.json` + `package-lock.json` do frontend). É a única
  mudança real: a frente provou que o repositório já estava nas
  versões corrigidas dos 5 advisories das rodadas 8/9 — eles viviam
  na cópia de trabalho desatualizada do PC do dono; `react-router`
  segue 7.18.5 (a v8 exige React ≥ 19.2.7; o projeto está em React
  18) e `zod` segue 4.6.7 (última release). A migração React 19 +
  react-router v8 ficou para depois, como onda própria de manutenção
  pós-lançamento (decisão delegada pelo dono e registrada).
- **Correções da 1ª rodada de revisão geral pousadas** (mesa de
  verificação da sub-sessão de auditoria; correções executadas na
  sessão principal, com revisão cruzada):
  - **G1 (grave):** os `V15_X` da ED-46/ED-48 saíram do caminho ativo
    de migrations do reporting e foram recolhidos para
    `db/pendente/`, byte-idênticos; os 3 testes consumidores foram
    ajustados só em caminho/comentário, com as asserções intactas.
    Numeração fica para a leva global da integração (P3.12).
  - **G2 (grave):** 29 tabelas de tenant inscritas com
    `register_tenant_table` em 7 migrations novas em `db/pendente/`
    (ED-19, ED-24, ED-30, ED-31, ED-33, ED-38, ED-84), na convenção
    agora registrada: toda tabela com `tenant_id` se registra na
    mesma leva que a cria. A ED-67 ficou fora com justificativa
    provada (RLS GIS da rotina privilegiada de implantação).
  - **M5 (falso verde):** `ziipsan-accounting` (ED-32) e
    `ziipsan-billing-rules` (ED-19) registrados no POM raiz (+2
    linhas) — os dois módulos passam a compilar e testar na CI.
  - **OBS-1 + guarda composta:** a lista
    `published-tenant-migrations.txt` pousou UMA vez, já composta
    (G1 + OBS-1): 114 linhas, com os 4 nomes que faltavam (V15_2,
    V8_2, V13_13, V23_1) e sem a linha do `V15_X` da ED-46. A réplica
    da guarda da P3.12 sobre os 117 nomes de migration da `main`
    resulta em **zero violações** com os dois pousos; a CI definitiva
    confirma executando o teste de verdade.
  - **Lote 2:** M7 — a V4_7 canônica é a fundida da P2.6 e já era a
    da `main` (prova por hash registrada; nenhum arquivo a pousar).
    M6 — nota de integração autoritativa da numeração pousada ao
    lado da lista de publicadas (sequência global da P3.12; as
    instruções de faixa dos relatórios antigos estão superadas). S1 —
    `App.tsx` com 6 rotas novas (GIS, documentos fiscais, minhas
    tarefas, agenda); `Inicio`, `GisThematics` e `GisWebEditor`
    seguem como pendência nomeada, sem rota inventada, e o menu
    lateral é passo de integração registrado. S2 —
    `@Transactional` no `OutsourcedReadingService` (P2.7) e no
    `promote` da ED-17, com testes novos de atomicidade (rollback
    provado); efeito conhecido registrado: o lote `REJECTED` de
    importação inválida não persiste mais. S3 —
    `@PreAuthorize("isAuthenticated()")` no `DpoBackofficeController`
    (P3.40), com teste; limitação registrada: qualquer autenticado
    passa até existir a permissão do encarregado no catálogo.
  - **Lote 3 (registros que a integração lança por este push):**
    (1) **M1** — telas do fluxo de leitura do app de campo produzidas
    sobre o bootstrap da ED-18 e a fachada da ED-19 (7 arquivos em
    `campo/src/ui/`, 51/51 testes), **sem tocar no calculador de
    campo** (hash do `fieldEmission.ts` inalterado; a decisão O7-6
    segue pendente do dono, provisoriamente no neutro de manter o
    contrato entregue); pendências de integração P-01 a P-08,
    incluindo a fiação do `boot.ts` e as portas de tarifa/gravação
    de leitura. (2) **M2** — registro do placar com segunda dimensão
    criado nas pastas de produção: das 219 executadas classificadas
    à época, 47 de aceite completo, 57 de núcleo entregue + ensaio
    externo pendente, 15 de categoria C e 100 pré-retomada sem
    classificação por relatório local — insumo para a rotina de
    status. (3) **M4** — ED-08 ressalvada no placar: veredito formal
    parcial; matriz E2E verde pendente da 1ª rodada do job
    `e2e-navegadores`. (4) **M3** — `REVISAO-REGISTRO.md` anexado aos
    13 pacotes sem artefato; 3 vereditos localizados só em prosa no
    próprio relatório (P2.7, P2.9, P2.10, com ressalva de forma) e
    **10 vereditos NÃO localizados — recuperação em execução** com
    revisores novos: P2.21, P2.23, P2.28, P3.15, P3.18, P3.22, P3.26,
    P3.29, P3.38, P4.8 (P2.28/P3.15/P3.22/P3.29 têm só a reprovação
    inicial sem confirmação; P4.8 tem auto-revisão declarada).
    (5) **M8** — tabela de aceite da ED-40 corrigida (quiosque e
    painel SG-625/626: núcleo aprovado / tela = integração).
    (6) **Menores** — contagem de frontend da P3.25 corrigida
    (11 → 12); `GisThematics` com estado de carregando (P3.27);
    `DocumentosFiscais` com estado vazio explícito (P2.33);
    comentário fundamentado no catch do `NativeSolverRunner`
    (ED-63); `Clock` injetado nos carimbos de auditoria da P3.22 e
    da P3.12, com testes.
  - **Micro-lotes de teste:** o regex de extração de colunas dos
    testes de consistência do metering foi corrigido SEM afrouxar
    (`[a-z_][a-z0-9_]*`, superconjunto mínimo — a forma sugerida
    `[a-z0-9_]+` foi rejeitada com prova, por passar a aceitar nome
    começando por dígito): `OutsourcedReadingMigrationConsistencyTest`
    (P2.7) de 5/6 para 6/6; e o `MigrationConsistencyTest` (P2.5)
    pousou na versão final do micro-lote V5_7, que soma o parser por
    statement completo. Sobre a V5_7: a migration está publicada e é
    SQL válido — o defeito era do parser do teste (o `ALTER` em duas
    linhas não era lido); **a migration NÃO foi reescrita** e o
    placar esperado da classe na `main` passa a 7/7.
- **Nota de execução:** o `package-lock.json` (283 KB) não cabe no
  canal de argumentos do conector (limite de ~128 KB por argumento);
  ele e os demais arquivos deste push foram pela rota de snapshot
  local do conector (`push-files`), com o HEAD conferido após cada
  lote. Nenhum lote precisou de segunda tentativa, exceto a troca de
  rota do lote do lock.
- **Backup da camada:** branch `backup/2026-11-08-pos-push-2` criada
  sobre o HEAD final desta operação (este commit de diário incluso).
- **Pendências vivas:** a rodada definitiva da CI total sobre o novo
  HEAD no PC do dono (é ela que prova integrado o que este push
  pousou, em especial a guarda de migrations e os dois módulos novos
  no reactor); piloto P0.30 (o dono indicará a prestadora; trava
  P3.13, GL.4, a trilha LC, a aprovação das minutas da P3.9 e o
  fechamento dos 14 marcadores abertos); a cascata de ~42 ED
  condicionadas à aprovação da P3.9; a recuperação M3 dos 10
  pacotes; as 52 condicionadas, as 16 bloqueadas-raiz e as sob
  demanda do plano.

### 08/11/2026 — Push 3: correções da recuperação M3

- **Ordem expressa do dono** (08/11/2026, 15h00): subir as 7 correções
  da recuperação M3, todas aprovadas com confirmação independente.
- **Recuperação M3 concluída:** os 10 pacotes que estavam na `main`
  sem veredito formal localizado (P2.21, P2.23, P2.28, P3.15, P3.18,
  P3.22, P3.26, P3.29, P3.38, P4.8) receberam revisão cruzada nova e
  independente — **10/10 com veredito formal: 8 aprovados e 2
  aprovados com pendências nomeadas** (P2.21 e P3.26). P2.23, P2.28 e
  P3.15 foram aprovados sem correção — nada a pousar deles. As
  correções abaixo já nasceram de defeitos provados por
  execução/sonda, com controles negativos nas confirmações.
- **P2.21 (GIS):** a classe `GisComponentService` **não compilava**
  (`jdbc.query` ambíguo) — corrigida; o checker passa a emitir
  `PGR_SPLIT_IN_JAVA`; `pieceElevation` propaga pela cadeia; 2 ciclos
  disjuntos geram 2 `GRAVITY_CYCLE`; lacuna do §5.3 implementada
  (10 testes novos). Suíte 76 (com erro de compilação) → **91/91**.
- **P3.18:** incidentes/janelas/resumos de componentes
  `visible=false` **vazavam no JSON da página pública** de status —
  vazamento fechado nos três canais; suíte 15/15 → **18/18**.
- **P3.22:** a **exportação PDF quebrava para qualquer entrada**
  (conta de objetos `3 + pages*2`; o correto é `2 + pages*2`) —
  correção de 1 linha no `PdfReportWriter`, conferida por 3 agentes;
  suíte 59/60 → **60/60**.
- **P3.26 (GIS):** relatório de qualidade (PDF ISO 19157) com serviço,
  endpoint e teste novos; benchmark medido de 50,047 km em ~1,1–1,2 s
  de servidor (teto de 2 h). O benchmark provou um **defeito real
  novo**: no PostGIS 3.4, `GeometryType()` devolve `'LINESTRING'`
  sem o prefixo `ST_`, e a V13_11 publicada compara com
  `'ST_LineString'` — a remoção de segmentos curtos **nunca
  disparava** (experimento em banco: 0 → 10 removidos). Como a
  V13_11 é migration publicada, ela **não foi reescrita**: a
  correção (seed Saneago + `ST_GeometryType` nas 3 funções) pousa
  como **migration nova V101**, pela sequência global da P3.12 —
  a V100 é o marcador de abertura da sequência já presente na
  `main`, nenhum V101+ existia em nenhum módulo, e a lista de
  publicadas foi atualizada no mesmo commit (114 → 115 linhas).
- **P3.29:** asserção de mensagem de teste desalinhada do validador
  (comportamento correto) — 1 linha de teste, suíte 59/60 → **60/60**.
- **P3.38:** o record passa a impor em Java 2 das 10 coerências dos
  CHECKs da V10_6 e `Policy:`/`Canonical:` aceitam só `https:`
  (RFC 9116); suíte 16/16 → **29/29**. A contagem errada de testes
  do relatório original (17 × 16 reais) foi corrigida por ERRATA
  local, sem reescrever o original.
- **P4.8:** a **transferência para humano por escrito não
  funcionava** ("quero falar com um humano" não casava as frases do
  matcher) — corrigida; e o guard de telemetria não recursava em
  listas (identificador dentro de array era aceito e gravado) —
  corrigido. Suíte 22/23 → **29/29**.
- **Backup da camada:** branch `backup/2026-11-08-pos-push-3` criada
  sobre o HEAD final desta operação (este commit de diário incluso).
- **Pendências nomeadas vivas:** P2.21 PEND-01 (publicador de área
  suja — a spec não decide quem publica/quando/`dedup_key` e a API
  da fila da P1.9 não existe nos espelhos; integração posterior) e
  PEND-02 (pgRouting real e `Gis03TopologyIT` — CI definitiva);
  P3.26 A3 (grade oficial do IBGE: arquivos `br_ibge_*.tif`
  ausentes — item não aprovado, destrava com os artefatos oficiais
  e os pontos de homologação), A1 (medidas ISO de homologação), A2
  (publicação IMPORT/leitura GDAL não medidas) e A4 (validação
  contra a IN 00.0229); P3.38 (canal e prazo do `security.txt`
  seguem decisão do dono — o gerador recusa publicar sem eles);
  P3.22 N2 (execução pode ficar `RUNNING` em falha de escrita —
  pendência separada na esteira); a **CI total definitiva sobre o
  novo HEAD** no PC do dono (prova integrada de tudo que pousou,
  incl. a guarda de migrations com a V101 e os ITs inexequíveis
  nesta VM); **piloto P0.30** (o dono indicará a prestadora; trava
  P3.13, GL.4, a trilha LC e a aprovação das minutas da P3.9).

## 08/11/2026 — Cascata ED, Camada C1 (ED-41, ED-73 e micro-lote P3.22 N2)

Produção contínua da cascata destravada pela P3.9 (mapa em
`ziipsan-producao/cascata-ed/MAPA-CASCATA.md`), ciclo completo por
tarefa (produção → revisão cruzada independente → correção →
confirmação por agente novo → push imediato):

- **Micro-lote P3.22 N2 — CORRIGIDO e na main** (`b56a3a6`): falha na
  escrita/exportação de relatório deixava a execução presa em
  `RUNNING`; agora toda falha pós-início fecha a execução em `FAILED`
  exatamente uma vez, com a causa registrada e a exceção original
  relançada. Revisão independente: 60/60 → 64/64, controle negativo
  exato (os 4 testes novos falham sem a correção). A pendência N2 sai
  da lista de pendências vivas.
- **ED-73 (Pacote ISO/IEC 27001) — APROVADA e na main** (`348cbf8`,
  `95f1de2`): subpacote `certification.infosec27001` no core, SoA com
  os 93 controles como mecanismo (títulos `[A DEFINIR]` — nenhum
  título/identificador literal do Anexo A reproduzido; a reprodução
  aguarda decisão do dono sobre licença × reescrita), matriz
  Ziip×cliente, campanha de recertificação sobre o catálogo da ED-06
  e pacote de evidência do fornecedor por referência + SHA-256.
  Revisão com ressalvas D1–D5, todas corrigidas e confirmadas por
  agente novo (53/53). SQL em `db/pendente/` (numeração global na
  integração).
- **ED-41 (Notificações multicanal e fatura por e-mail) — APROVADA e
  na main** (`1c3ef0a`, `c43bb0d`): disparo multicanal com guardas
  (opt-in por canal/finalidade, consentimento, contato verificado,
  provedor no DPA, remetente por tenant), fatura por e-mail do POC-20
  herdando as guardas de reputação da P3.5, classificação de modelos
  WhatsApp com estimativa e alerta de marketing, teto mensal com
  alertas 80%/100% sobre o gasto comprometido e medição de uso por
  tenant/módulo para o console da P1.6. Revisão com ressalvas D1–D5
  corrigidas e confirmadas (73/73; o teste de consistência da P3.36
  passou a enxergar a migration da ED-41 — controle provou que a
  asserção não foi afrouxada). SQL em `db/pendente/`.

Placar: **ED 52/98 · geral 222/286**. Pendências vivas da camada:
títulos do Anexo A da ISO 27001 (decisão do dono); provedores reais e
ensaio POC-20/SAMAE 2.19 (provas externas); fiação JDBC/beans e
numeração global dos SQL pendentes (integração); CI total definitiva
sobre o HEAD corrente no PC do dono; piloto P0.30.

## 08/11/2026 — Cascata ED, Camada C2 (ED-10, ED-20, ED-42, ED-54, ED-101)

Ciclo completo por tarefa (produção → revisão cruzada independente →
correção → confirmação por agente novo → push imediato):

- **ED-10 (Política comercial e preço por porte) — APROVADA**
  (`32e94ef`): planilha de custo/preço por porte sobre a P0.26, tetos
  VISAN/SAMAE só como comparação, franquia/repasse por categoria
  sobre o uso medido da ED-41 com fotografia de ligações (PLT-17).
  Ressalvas documentais e de guarda corrigidas e confirmadas (43/43).
  Nenhum preço/margem com valor de fábrica — parâmetros do dono.
- **ED-20 (OS em campo e acompanhamento) — APROVADA** (`f0c5c21`):
  pacote de corte com revalidação na sincronização, GPS só na
  jornada com governança LGPD (aviso ao empregado agora é portão
  composto na ingestão), aviso "a caminho" pela ED-41, desfoque de
  faces ligado à porta. Confirmação 36/36 Java + 5/5 TS.
- **ED-42 (Omnichannel híbrido) — APROVADA** (`d4197ae`, `4a10f63`):
  conversa nos 6 canais registrada no protocolo, webhook fail-closed
  (HMAC/segredo conferidos), recusas precoces agora lançam rastro na
  cadeia de integridade, medição lida da ED-41 sem duplicar regra.
  Confirmação 35/35 + sondas 36/36.
- **ED-54 (GIS-11 — traçados, isolamento e manobras) — APROVADA após
  REPROVAÇÃO** (`98ba075`, `c83ea3f`): a revisão cruzada pegou 2
  defeitos GRAVES no motor de isolamento (válvula já fechada não
  barrava a recomputação da área; área afetada vazia conforme a
  orientação do arco-alvo) — corrigidos com testes das sondas,
  captura provada (reversão derruba exatamente os testes novos) e
  confirmação por agente novo (30/30, coerência 53/53). Recorte O8-1
  respeitado: nenhum solver de equipes, só rede (ADR-0034).
- **ED-101 (Metas, comparação com pares e gamificação) — APROVADA**
  (`adf13dc`): delega o cálculo à PeerComparison da ED-38 com o piso
  de privacidade da O9-3 inviolável (coorte 4 não publica no piso 5),
  guarda de adesão fechada em todas as operações. Confirmação 40/40.

Placar: **ED 57/98 · geral 227/286**. Pendências vivas da camada:
provas externas B (provedores/contas reais da ED-42, ensaios
POC/TR/SAMAE), publicador de área suja (PEND-01 da P2.21) e provas
PostGIS/pgRouting na CI, valores comerciais reais da ED-10
(parâmetros do dono), fiação e numeração global dos SQL pendentes;
CI total definitiva e piloto P0.30, como antes.

## 08/11/2026 — Cascata ED, Camada C3 (ED-23, ED-43, ED-50, ED-56, ED-58, ED-66, ED-91, ED-92)

Oito tarefas, ciclo completo por tarefa (produção → revisão cruzada
independente → correção → confirmação por agente novo → push).
Duas REPROVAÇÕES na primeira revisão, ambas corrigidas e confirmadas:

- **ED-92 (Ocorrência de rede como entidade) — REPROVADA → APROVADA**
  (`52833b0`): o revisor aplicou o SQL em PostgreSQL real e ele NÃO
  aplicava (FK sem UNIQUE no pai — o padrão virou item de vigia da
  cascata). Catálogo de causas: só ALAGAMENTO tem fonte (ED-64);
  desconhecido nunca vira zero no JOR-33 (null + contadores).
- **ED-56 (GIS-19 ativos/manutenção) — REPROVADA → APROVADA**
  (`7155ff1`, `c97f534`): mesmo grave de SQL (FK sem UNIQUE) + 8
  defeitos, incl. operabilidade sem frescor (válvula vencida saía
  operável) — contrato virou tri-estado com DESCONHECIDA para a
  ED-54, e detecção de sobreposição que era verde falso passou a
  detecção real.
- **ED-50 (Qualidade da água) — APROVADA** (`f51cc6e`, `dd0fe28`):
  disciplina regulatória integral — NENHUM limite de portaria como
  valor fixo; catálogo é dado carregado que só fundamenta
  conformidade após revisão do responsável técnico (sem revisão =
  INDETERMINATE); vigências sobrepostas recusadas na carga.
- **ED-43 (Chatbot/IA) — APROVADA** (`c5bf750`): 4 moderados
  corrigidos — máscara de dados pessoais contornável fechada
  (fail-closed), vazamento na saída descartado, prompt sem
  avaliação não chama o modelo, EXPLAIN_BILL despacha pela função
  determinística da ED-38 (a IA redige, não calcula).
- **ED-23 (Revisão por vazamento) — APROVADA** (`ad131fb`):
  protocolo de fases sobre a P3.28 sem duplicar cálculo; retomada
  após recusa da P2.20 funciona sem re-assinar; assinatura só após
  validar fase/conclusão.
- **ED-58 (Almoxarifado) — APROVADA** (`f1fa421`, `0dcce85`):
  reconciliação disponível-negativo × físico-piso-zero provada;
  correções de atomicidade (baixa de campo, NF-e idempotente por
  chave, cancelamento sem mutação parcial, encerramento sem
  contagem em dobro).
- **ED-66 (Campo GIS Capacitor) — APROVADA** (`2885bf6`, `ef8c2a9`):
  portão de meta de precisão agora vale também para INSERT de
  as-built (aparelho e servidor); validade do pacote checada na
  construção do delta, não só na abertura.
- **ED-91 (Crise hídrica) — APROVADA** (`01f7042`): medida
  excepcional sobre LOCALITY/ROUTE não bloqueava corte em silêncio
  — correção fecha os 4 tipos de área (sem membros = recusa, nunca
  silêncio); item 32 só observado.

Placar: **ED 65/98 · geral 235/286**. Vigia permanente herdada da
camada: todo SQL novo é APLICADO em PostgreSQL real na revisão
(parse não basta — dois pacotes cairiam na CI sem isso).
Pendências vivas: fiação de integração dos pacotes (JPA/beans/
telas), numeração global dos SQL pendentes, provas externas B
(provedores reais da ED-43, ensaios POC/SAMAE), P-ED58-07 (silo de
serializados), CI total definitiva e piloto P0.30.
## 08/11/2026 — Lote de correções da rodada 2 (auditoria contínua) — pacotes A, B e menores; M3 residual fechado

Correções da 2ª rodada da auditoria contínua (delta: Push 2, Push 3, cascata C1). R2-G1 (V6_4 fora da sequência global) FORA deste lote — exige decisão expressa do dono, pedida em paralelo. Método das ondas por pacote (produção → revisão cruzada independente → correção → confirmação por agente novo → push imediato). Sem mudança no placar de tarefas (correções, não tarefas novas).

- **Pacote A — R2-G2 (GRAVE) — APROVADO e pousado** (`e2f5fdb`): `/privacidade/**` era negado a todos pela `apiSecurity` catch-all (`.anyRequest().denyAll()`), e o `@PreAuthorize` do `DpoBackofficeController` nunca era avaliado. Correção pelo menor impacto: 1 regra + comentário na `SecurityConfig` (`.requestMatchers("/privacidade/**").authenticated()`), `denyAll()` intacto; prova 5/5 verde com a correção e 3/5 vermelho sem ela. Fecha também o S3 (rodada 1) no nível provável aqui; o 200 HTTP de ponta a ponta aguarda o scan do módulo LGPD + CI total. Restrição ao encarregado pendente: o catálogo P0.13 não tem permissão de privacidade (troca por `hasAuthority(...)` é de 1 linha quando existir). Validação independente pós-pouso designada (o pacote pousou antes dela por limitação de profundidade das frentes — ver "Desvio de método" abaixo).
- **Pacote B — B2-2 a B2-7 (moderados de backend) — APROVADO e pousado** (`2b60d3e`, `035e8e3`, `dfacae5`; HEAD `dfacae5`): store canônico único de consentimento (`legacy_debit_conversion_consents` + adaptador; tabela duplicada removida do SQL pendente), portão `requireLegacyOperable` removido só de `processLegacyReturn`, remessa idempotente por `remittanceKey` com teste de reexecução, adaptador `LegacyDebitPort` com mapeamento definido, `register_tenant_table` descomentado no SQL pendente da ED-27, destino da ED-41 amarrado ao `contactId` do consentimento verificado. Harnesses 28/28 (P3.24), 36/36 (ED-27), 75/75 (ED-41). **Reparo de quebra real na main:** o módulo `collection` não compilava no HEAD anterior (o `.java` referenciava `ConversionConsentStore`, inexistente) — o lote 2 trouxe as 3 classes-companheiras (diretório `debit/` 5 → 8 arquivos) e a referência resolve. A confirmação refutou o push as-is por deriva de base (o javadoc B2-8 do pacote de menores chegara à main depois da revisão) — remediação de 1 arquivo aplicada, diff pós-remediação vazio, harness re-rodado 36/36. Residuais: UNIQUE de banco da chave de remessa adiada de propósito (aguarda a decisão da V6_4/R2-G1 — V102 não reclamado); fiação do adaptador real da P1.3 para os novos `resolve*ForContact`.
- **Pacote de menores — 9/9 fechados e pousados** (`ce8bb62`, `785490d`, `63ccec0`): fila de divergência visível + diretiva consultável fora da sync + teste DOM no app de campo (suíte 51/51 → 66/66); janela residual settle→markProcessed declarada (B2-8); B2-9 confirmado (`support.sla_policy` × `support.sla_policy_target` na V10_2 da P2.15); B2-10 com ED-74 D1–D6 6/6 e ED-69 D1 confirmados — **ED-69 D2 NÃO corrigido e segue aberto** (SQL sem `jsonb_array_length>0` e sem padrão em `clause_number`); P-ED34-1 fechada (N-R2-1); cópia corrigida da V13_11 movida para `remontagem/` com marcador NÃO-POUSAR (N-R2-2) — V13_11 na main byte-idêntica. **Defeito grave encontrado na revisão independente pós-pouso (D-RG2M-1):** o commit 2 (`785490d`) foi declarado "+15 linhas de Javadoc, 0 remoções", mas o diff real é +58/−4 e já continha o código B2-2/B2-3 (o produtor mediu o diff contra o arquivo do RG2-B em vez do pai na main) — foi essa a origem da quebra de compilação do `collection`, reparada pelo lote 2 do pacote B. A afirmação falsa fica para correção nos relatórios locais do pacote.
- **M3 residual — FECHADO com confirmação independente:** P2.7 REPROVADO no pacote local (D1, regex que perdia `file_sha256`) com o defeito JÁ fechado na main (`2f96fc9`, variante equivalente, 26/26 reexecutada — a correção local não precisa pousar); P2.9 APROVADO (7/7, UUID Python ≡ SQL, sonda SIGTERM 0 perdidas); P2.10 APROVADO (18/18, prova de banco refeita). Artefatos `REVISAO-P2.7/P2.9/P2.10.md` + confirmação em `correcoes-rg-lote3/REVISAO-CONFIRMACAO-M3-RESIDUAL.md`. Os 13 pacotes da rodada 1 sem artefato de revisão passam a ter veredito formal.
- **Pacote C (R2-M1 a R2-M4) — em rebase, NÃO pousado neste registro:** produção APROVADA em revisão independente (84/84), mas a confirmação refutou o push as-is — o commit `ce8bb62` do pacote de menores pousara no meio da revisão e o push reverteria ~313 linhas + 10 testes dele. Rebase em curso (integrar `ce8bb62` nos 6 arquivos, suíte mesclada esperada 94 testes) + nova revisão e nova confirmação antes do push.
- **Desvio de método registrado:** as frentes deste lote rodaram em profundidade 2/2 (sem gerar subagentes), então produção, revisão e confirmação caíram na mesma sessão em A e nos menores — os pacotes pousaram antes da validação independente, que foi designada pelo coordenador do lote e executada a posteriori (A em curso; menores com D-RG2M-1 encontrado e reparado). B e C tiveram revisão + confirmação por agentes novos antes do push (B) ou antes do rebase (C). Lição: frentes paralelas que tocam os mesmos arquivos (`campo/`, `DebitAccountIntegration.java`) exigem rebase verificado antes do push — foi o que as confirmações independentes pegaram antes de qualquer perda.

Pendências vivas: decisão expressa R2-G1 (V6_4: renumerar × exceção registrada); pacote C após o rebase; ED-69 D2; UNIQUE da remessa (B2-4); fiação P1.3 (B2-7); CI total definitiva e piloto P0.30.
## 08/11/2026 — Lote de correções da rodada 2 (auditoria contínua) — Pacote C pousado (fechamento do lote)

- **Pacote C — R2-M1 a R2-M4 (frontend do app de campo) — APROVADO, CONFIRMADO e pousado** (`15f08ce`, `4d6c739`, `c72aa5f`; HEAD `c72aa5f`): fiação das telas no app (`startCampoApp`/`startOrderScreens`, `campo.css` linkado), portas P-03 (`FieldTaskEmission` a partir do `FieldTariffSnapshot`, `inputJson` idêntico ao fixture da ED-19) e P-04 (`saveReading` no `LocalStore`, leitura sobrevive ao reinício), telas de OS da ED-20 no padrão das telas de leitura (lista + detalhe com corte, identidade, fotos antes/depois, avanço) e campo de endereço/identificação legível no pacote de rota (`unitAddress?`). O commit 1 do LANDING (117.328 bytes) foi dividido em 2 partes numeradas, sem mudar conteúdo. Suíte mesclada **94/94** (28 herdados + 42 leitura mesclada + 24 novos); calculador da ED-19 intocado (SHA `64374640…e9f7b60`); TS5097 pré-existente (35 erros, atinge também originais ED-18/19/20). Ciclo completo com rebase: a primeira confirmação refutou o push as-is porque o `ce8bb62` do pacote de menores pousara no meio da revisão — o rebase integrou os dois lados nos 6 arquivos (0 linhas da main removidas) e o resultado mesclado passou por NOVA revisão + NOVA confirmação antes do push. Ressalvas R1 (validador `assertValidSnapshot` incompleto) e R2 (avanço de OS sem exigir foto de ANTES) ficam para o próximo toque no campo; pendências de integração P1–P4 declaradas (bootstrap real + pontes nativas, dados do pacote de rota no servidor, pacote de OS emitido pelo servidor, SQLCipher). Com este pouso, o lote de correções da rodada 2 está fechado, exceto o R2-G1 (decisão expressa do dono pendente).

## 08/11/2026 — Cascata ED, Camada C4 (ED-44, ED-59, ED-62, ED-71, ED-72, ED-77, ED-85, ED-87, ED-89, ED-96)

Dez pacotes produzidos pelo método das ondas (executor da spec pelo ID → revisão cruzada independente com veredito formal → correção → confirmação por agente novo → push imediato). **Todos os 10: APROVADO COM RESSALVAS → corrigido → CONFIRMADO — nenhum grave, nenhum pousou sem correção+confirmação.** Placar: **ED 75/98 · geral 245/286**. Relatório completo da camada: `RELATORIO-C4.md` no pacote de trabalho da cascata.

- **ED-71** (ISO 45001) → `22b2ebe` (45/45). Ressalvas menores viraram pendências P-ED71-R1 (profundidade de vala na fiação) e P-ED71-R2 (trava dura do livro de acessos de saúde).
- **ED-44** (telefonia mínima) → `f3b839a` (12 arq.; correção 12/12). Pendências P-ED44-01 (PABX), P-ED44-02 (fiação: persistência de motivos de exclusão + escritor de `telephony_call_events`), P-ED44-03 (PG16/Flyway).
- **ED-62** (ERP Betha/IPM/Fiorilli) → `df6155d` (17 arq.; correção 22/22): centro de custo via `CostCenterLink`, sem omissão silenciosa; nenhum leiaute de fornecedor inventado — prova contra leiautes reais é a B1 (externa/piloto).
- **ED-59** (frota e equipes) → `72ef448` (19 arq.): correção só no SQL — 5 FKs retargetadas para `fleet_vehicle (tenant_id, asset_id)`, convenção declarada; aplicação na pilha PG 16 fica para a CI (R2 do pacote).
- **ED-72** (ISO 55001) → `e80b3d9` (19 arq.; correção 32/32 nas duas camadas): FK quádrupla com outcome, CHECK de `field_source`, triggers de evidência; risco lido da ED-56.
- **ED-96** (instrumento contratual regulatório) → `5c9c827` (18 arq.; correção 25/25): UNIQUE reescopada e `EXCLUDE USING gist` anti-sobreposição no recorte município+serviço **sem tipo** — seguindo o Java contra o brief inicial do coordenador, divergência documentada e validada pelo confirmador. Cláusulas/valores sem fonte: `[A DEFINIR]`/`[SEM FONTE]`, nenhum inventado.
- **ED-89** (insumos químicos e reservatórios) → `5b21abd` (24 arq., 2 lotes; correção 46/46): controle de produto fiscalizado fail-closed + snapshot da conferência habilitante; dosagem nunca presumida; P-ED89-02 ampliada (`pfCheckForVersion` + `cleaningOrderId` na implementação JDBC/JPA).
- **ED-85** (perdas operacional) → `05cfbcf` (19 arq.; correção 27/27): nova `MeasuringPointPort` fail-closed; P-ED85-01 reescrita nomeando o adaptador real sobre a ED-17; P-ED92-03 fechada pelo consumidor.
- **ED-77** (fichas ISO 24510/24512) → `150a3b6` (27 arq.): executor original caiu por erro de infraestrutura — finalizador recuperou a produção, achou e corrigiu defeito real de CHECK de linhagem; micro-correção só de documentação fechou a R-ED77-01 (javadoc `goal.*` falso removido sem criar código morto). Pendências P-ED77-01..06; valores da Portaria MCID 788/2024 **não** carregados como fato.
- **ED-87** (estações sem supervisório) → `7a9a0d6`, `5725e19` (22 arq., 2 lotes; correção 29/29; confirmação 29/29 + sondas próprias 13/13 + SQL reaplicado 3× exit 0): D1 dedupe do diário com rollback em falha, D2 `assessWindow` com lacunas de borda (leitura isolada = não-conforme), D3 dedupe de alertas que não marca chave antes do envio, D4 `IF NOT EXISTS`. Citação da Portaria 888/2021 art. 28 §3º verificada em fonte oficial (DOU/BVS) pelo revisor.

Entre os pousos da camada, a mesa de auditoria contínua pousou em paralelo o lote de correções da rodada 2 (até `c72aa5f`); o runner leu o HEAD real a cada lote — sem corrida, sem perda. Herdadas e vivas: fiação de integração dos pacotes (JPA/beans/telas/Flyway) e numeração global dos SQL em `db/pendente/`; pendências nomeadas por pacote (RELATORIO-C4.md); P-ED91-01, P-ED58-07, constraint de não-sobreposição da ED-50; decisão R2-G1 (V6_4) com o dono; CI total definitiva e piloto P0.30. Próxima camada: **C5** (ED-49, ED-78, ED-79, ED-81, ED-82, ED-86, ED-90, ED-94, ED-95, ED-105).

## 08/11/2026 — Cascata ED, Camada C5 (ED-49, ED-78, ED-79, ED-81, ED-82, ED-86, ED-90, ED-94, ED-95, ED-105)

Dez pacotes pelo método das ondas sob a ESTEIRA NOVA (protocolo do coordenador-portão, ordem do dono de 08/11/2026 18:04): produção → revisão independente → correção → confirmação por agente distinto do produtor e do revisor → push → verificação pós-pouso. **Todos os 10: APROVADO COM RESSALVAS → corrigido → CONFIRMADO → pousado** — a ED-81 foi REPROVADA na primeira revisão (D1/D2 graves), corrigida e reapreciada APROVADA antes de pousar. Placar: **ED 85/98 · geral 255/286**. Cadeia de HEADs da camada: `e54235b → f6fb11a → 94b9707 → ef643ec → 7ca5e4e → 023489b → 47e692b → b2cd987 → caa78bf → 2dc0969 → 497c091` (15 commits de entrega). Relatório completo: `RELATORIO-C5.md` no pacote de trabalho da cascata.

- **ED-94** (anomalia explicável em séries) → `f6fb11a` (17 arq.; confirmação 45/45). Pendências P-ED94-01..04.
- **ED-86** (pressão e vazamento ativo) → `94b9707` (20 arq.; 24/24, placares do corretor conferem 1:1). Pendências P-ED86-01..03.
- **ED-105** (efluente ETE e lodo ETA/ETE) → `2f70e4e`, `ef643ec` (27 arq.; placar original 46/46 conferido, correções D1–D5 reexecutadas). Pendências P-ED105-01..07; valores sem fonte como `[SEM FONTE]`.
- **ED-82** (perfis ISO 37120/PNQS, checklist NBR) → `7ca5e4e` (19 arq.; JUnit 21/21, sondas 10/10, PG 24/24): paridade Java×SQL provada em banco real (OWN1/2/3/8 recusadas). Meio de prova declarado: PG 18.6 embarcado ≠ PG 16 da pilha — a prova definitiva é a pendência P-ED82-05, com o comportamento esperado registrado; P-ED82-04 (records não são API pública).
- **ED-90** (eficiência energética e ESG) → `4de18cc`, `023489b` (29 arq.; JUnit 57/57 + aplicação PG). Pendências P-ED90-01..04.
- **ED-81** (uso próprio, ISO 46001) → `47e692b` (16 arq.): REPROVADA (D1/D2 graves) → correção → reapreciação APROVADO COM RESSALVAS → confirmação complementar (placar final 25 = 20+5). Pendências P-ED81-01..05 (01 = adapter de fiação especificado; 05 = ligação DMC × instalação como pré-fiação declarada).
- **ED-78** (balanço de perdas, NR 15) → `62fbe07`, `b2cd987` (26 arq.; confirmador reproduziu integralmente os placares). Pendências P-ED78-01..09 (05 = fiação do plano de gestão; 08/09 = ressalvas R1/R2 nomeadas).
- **ED-95** (perdas aparentes e fraude) → `e27e961`, `caa78bf` (25 arq.; 52/52 em harness próprio do confirmador). Pendências P-ED95-01..06 (fiação JDBC, conector municipal).
- **ED-49** (sete painéis padrão) → `2dc0969` (21 arq.; confirmação com harness próprio, placares reproduzidos). Pendências P-ED49-02/03.
- **ED-79** (sistemas, PSA, relatório anual e Carta de Serviços) → `1d9a947`, `497c091` (25 arq.; 71/71). Pendências P-ED79-03 (invariante só na camada de serviço, declaração formal), P-ED79-06, P-ED79-09, P-ED79-10 (decisão de fundo do LOS).

**Fechamento da camada pelo coordenador sucessor:** o coordenador anterior encerrou após o último pouso (ED-79, HEAD `497c091`) sem os atos formais de fechamento. O sucessor refez a verdade do terreno (os 10 pousos conferem na main, lote a lote, contra os `state.json`; o mtime de cada CONFIRMACAO é anterior ao respectivo commit — nenhum pouso precedeu a sua confirmação), rodou o portão mecânico `portao-deploy.py` nos 10 pacotes (**10/10 APROVADO**, saídas em `cascata-ed/portao-c5/`) e executou a verificação pós-pouso integral: **225/225 arquivos byte-idênticos** entre as entregas locais e a main (SHA de blob git), 0 divergências. Herdadas e vivas: fiação de integração (JPA/beans/telas) e numeração global dos SQL em `db/pendente/`; decisão R2-G1 (V6_4 → V102/V103) em lote paralelo; CI total definitiva e piloto P0.30. Próxima camada: **C6** (ED-70, ED-80, ED-83, ED-93).
## 08/11/2026 — Correção R2-G1: renumeração V6_4 → V102 (decisão do dono, item 33)

- **Decisão:** dono, 08/11/2026 19:00 −03 — opção (a): renumerar a migration `V6_4__legacy_debit_febraban150.sql` (P3.24, módulo collection) para `V102` na sequência global da P3.12, conteúdo byte-idêntico, lista de publicadas atualizada no mesmo pouso; a alternativa (b) (exceção registrada na regra da P3.12) fica rejeitada. Registrada como item 33 do `DECISOES-DO-DONO.md`.
- **Pouso (esteira nova, PROTOCOLO-ESTEIRA.md):** pacote `correcao-r2g1-v102` — produção → revisão independente **APROVADA** → confirmação independente **CONFIRMADA** → `portao-deploy.py` **APROVADO** (regras 1–6) → rebase reverificado no ato do push (HEAD `497c0914`; 15/15 caminhos tocados byte-idênticos à base) → **Commit 1** `f6641b63` (push_files com 14 arquivos: a V102 nasce byte-idêntica — blob `50394a5a…`, o mesmo que a V6_4 tinha —, lista de publicadas 115 → 116 e 12 referências vivas corrigidas, nenhuma asserção afrouxada) → **Commit 2** `aa4d961b` (delete_file da V6_4). Mecanismo declarado sem atomicidade fingida: o conector não cria e remove num commit só, por isso o pouso é em 2 commits sequenciais da mesma janela do Portão, HEAD conferido após cada um.
- **Verificação pós-pouso por hash:** V102 lida da main com SHA-256 `d26e38fa…` (= hash da V6_4 na base); lista com 116 linhas, SHA-256 `e5920e33…`, última linha `V102__legacy_debit_febraban150.sql`; V6_4 ausente; teste de consistência e `LegacyDebitService` conferidos hash a hash contra a entrega. **Guarda de migrations: antes 3 violações (2 deste item + 1 alheia) → depois 0 violações deste item**; resta a violação alheia pré-existente da V100 fora da lista (pendência P-R2G1-2, raiz no Push 3, fora deste escopo).
- **V103 (UNIQUE de banco da chave de remessa, residual B2-4): NÃO pousada — pendência P-R2G1-1.** Prova contra a main, confirmada na revisão e na confirmação: a chave de remessa não tem coluna persistida (`legacy_debit_entries` e `LegacyDebitEntryEntity` sem a coluna; `LegacyDebitEntryRepository` é interface vazia); é String derivada em aplicação, `LegacyDebitService.remittanceKey(...)`, guardada só em memória. Sem a forma exata provada, nenhuma constraint foi inventada; quando houver desenho decidido, a V103 entra como pacote próprio, com a lista 116 → 117 no mesmo pouso.
- **Pendências que ficam:** P-R2G1-1 (V103/B2-4 — desenho por decidir), P-R2G1-2 (V100 fora da lista), P-R2G1-3 (CiTest da P3.12 desalinhado desde o Push 3), P-R2G1-4 (limite da varredura repo-inteira fora do módulo collection). Calculador da ED-19 não tocado. A CI total definitiva no PC do dono segue como prova pendente do conjunto.

- **Adendo do Portão (08/11/2026, noite):** a primeira versão desta entrada, pousada no commit `640f92ea`, sobrescreveu por engano a entrada da Camada C5 da cascata — o commit de diário da C5 (`9d1acf6a`) pousou entre a leitura-base deste Portão e o push do diário, e o arquivo pousado partiu da cópia anterior. A entrada da C5 foi **restaurada na íntegra** no commit de reparo que acompanha este adendo, a partir do conteúdo preservado no commit `9d1acf6a`; nenhum outro conteúdo foi tocado. Registrado como desvio de execução do Portão (rebase do diário não reverificado no ato do push).

## 08/11/2026 — Cascata ED, Camada C6 (ED-70, ED-80, ED-83, ED-93)

Quatro pacotes pelo método das ondas sob a ESTEIRA NOVA (protocolo do coordenador-portão, ordem do dono de 08/11/2026 18:04): revisão e confirmação independentes, portão mecânico, rebase verificado, verificação pós-pouso por hash. Base `83a9539`, HEAD final da camada `fde45c5`. Placar: ED 89/98, geral 259/286.

- **ED-80** (esgoto e água bruta) → `386cec3` (20 arq.; APROVADO COM RESSALVAS; confirmação JUnit 21/21, PG apply/reapply reproduzidos). Pendências P-ED80-01..05.
- **ED-83** (esgoto não doméstico e carga poluidora) → `af04605`, `261402f` (25 arq., 2 lotes; APROVADO COM RESSALVAS com ERRATA datada na revisão; duas confirmações independentes: Java 36/36, PG 11 tabelas/RLS, negativas 10/10). Pendências P-ED83-01..07.
- **ED-70** (pacote ISO 14001) → `e14ee3d` (22 arq.): REPROVADA (D1/D2) → correção (39/39; PG 12/12) → reapreciação APROVADO → confirmação (integridade 10/10 por hash). Pendências P-ED70-01..06.
- **ED-93** (sala de situação operacional) → `fde45c5` (24 arq.): REPROVADA (D1/D2/D3) → correção (32/32; PG 10 negativas) → reapreciação APROVADO → confirmação (sondas próprias 17/17). Pendências P-ED93-01..06.

**Desvio de forma do Portão (registrado, §4(i)):** na ED-70, o pouso executou com o bloqueio do verificador mecânico ainda por demonstrar em artefato — o bloqueio era o falso positivo P-ESTEIRA-01 (a Regra 1 do `portao-deploy.py` não reconhece `REVISAO-REAPRECIACAO-*.md` como veredito vigente nem distingue citação histórica); a cadeia substantiva (revisão → correção → reapreciação → confirmação) estava completa antes do pouso e a demonstração foi lavrada em seguida (`ed-70/PORTAO-REGISTRO-ED-70.md`). Na ED-93, a demonstração precedeu o pouso (§3.2 cumprido, `ed-93/PORTAO-REGISTRO-ED-93.md`). A correção do script é o achado P-ESTEIRA-01, de lote próprio.

**Fechamento da camada pelo coordenador de fechamento C5+C6:** os coordenadores anteriores encerraram com notas de progresso (o pouso da ED-93 ficou "executando em segundo plano", sem desfecho declarado — confirmado pousado neste fechamento); verificação pós-pouso integral executada neste fechamento: **91/91 arquivos byte-idênticos** (ED-80 20/20 · ED-83 25/25 · ED-70 22/22 · ED-93 24/24), 0 divergências. C6 FECHADA — relatório `cascata-ed/RELATORIO-C6.md`. Faltam C7 (ED-75), C8 (ED-97) e C9 (ED-98) para a meta da cascata (ED 92/98, geral 262/286).

## 09/11/2026 — Lote de correções da Rodada 3 (auditoria contínua) — R3-A, R3-B e R3-C pousados; R3-D corrigido e verificado, não pousado

Correções da Rodada 3 da auditoria contínua (fonte: `00-CONSOLIDADO-RODADA3.md`), quatro lotes sob a ESTEIRA NOVA (produção → revisão independente → confirmação por agente distinto do produtor e do revisor → portão mecânico → rebase verificado no ato → push → verificação pós-pouso por hash). **Tentativa única R4-M1 consumada:** com as aprovações de escrita liberadas pelo dono (09/11/2026 00:12 −03), os três lotes pousaram nesta tentativa (R3-A `1ccacd17` 03:06:40Z, R3-B `4fde5dfb` 03:14:32Z, R3-C `9ae1c344` + `c01acb64`); entre a verificação do R3-B e o seu push pousou o diário da C6 (`4d88b898`, só `docs/`) — nenhum arquivo do lote tocado. Rebase verificado no ato de cada push (R3-A 4/4 contra `fde45c56`; R3-B 8/8 contra `1ccacd17`; R3-C 2/2 contra `1ccacd17`); nenhum push removeu linhas alheias. Relatório consolidado do lote: `RELATORIO-CONSOLIDADO-R3.md`.

- **R3-A (grave, ED-54) — POUSADO** (`1ccacd17`, 4/4 blobs verificados): R3-G1 — UNIQUE `(tenant_id, id)` em `gis_maneuver_plan` (a migration falharia quando numerada/aplicada: as FKs compostas apontavam para a chave sem UNIQUE); prova PG: o SQL original FALHA exit 3, o corrigido APLICA exit 0. B3-M1 — `IsolationEngine`: guarda contra área vazia quando o arco-alvo tem válvulas operáveis nas duas pontas (`affectedNodes.isEmpty()`); suíte 30/32 → **32/32**. Placar: ED 89/98 · geral 259/286 na main após este pouso (antes do R3-B).
- **R3-B (moderados) — POUSADO** (`4fde5dfb`, 8/8 blobs verificados): B3-1 (ED-43) — `PiiMasker` SEP/DIGIT_RUN passam a aceitar `,` e `_` (grafias como "123,456,789-09" chegavam cruas ao modelo); suíte **70/70**. B3-2 (ED-50) — `MonthlyClosing.collected` conta a amostra só quando há entrada para aquela chave (`entryKeysBySample`); suíte **64/64**. R3-M1 — 12 tabelas sem `register_tenant_table` registradas (ED-101: 5; ED-23: 4; ED-43: 3); PG 3/3 aplicam, **12/12** registradas. **P-R3B-03 sustentada (não é pendência):** a expectativa alterada em `MonthlyClosingTest` (2→3, cobertura 50→75%) foi julgada por dois agentes independentes como consequência necessária e MAIS exigente da regra prescrita — não é afrouxamento; não mexer. Pendências P-R3B-01 (RLS real é portão da CI total) e P-R3B-02 (ED-23 sem reaplicação, pré-existente).
- **R3-C (menores) — POUSADO PARCIAL** (commits `9ae1c344` B3-3 e `c01acb64` B3-4, hashes = LANDING): B3-3 (ED-66) — CHECK passa a recusar altitude normal com `vertical_datum` NULL; B3-4 (ED-58) — guarda de reexecução da UNIQUE (1ª e 2ª aplicação exit 0). R3-C4-1 (ED-44) — prova real de aplicação do SQL lavrada por adendo no relatório da ED-44 (o SQL é byte-idêntico, PG exit 0). **Commit 3 (sync de campo, R3-C4-2) NÃO pousado — SUPERSEDIDO:** a main já contém implementação paralela e mais nova de campo (R2-M1..R2-M4 com `fieldTariffSnapshot.ts`/`fieldReadingStore.ts`); pousar o sync sobrescreveria a versão mais nova pela versão RG2-menores (66 testes, sem a fiação R2-M1..M4) — remoção de linhas alheias, vedada pelo portão. O objetivo do achado (a main ter o campo corrigido) está atendido por implementação mais nova.
- **R3-D (persistentes R2-M1..M4 + B2-10/ED-69-D2, com a dobra R4-m4) — CORRIGIDO E VERIFICADO, NÃO POUSADO.** Suíte TS 71/71 → **95/95**, e **96/96** com a dobra (2 execuções), tsc exit 0; SQL da ED-69 alinhado ao Java (`jsonb_array_length > 0` + trigger + `clause_number`); portão **APROVADO pós-delta** (exit 0, 6/6). Pouso bloqueado pelo mesmo conflito de campo do commit 3 do R3-C: os arquivos de campo do lote regrediriam a main. Só o SQL da ED-69 seria pousável em princípio (a main ainda tem o original, blob `4cf4a93f…`), por decisão separada — o LANDING do lote é unitário (17 arquivos) e não foi fatiado. **Decisão de merge/canônico do campo: PENDENTE (nível dono)** — qual implementação é canônica (`fieldTariffSnapshot.ts`/`fieldReadingStore.ts` da main × `fieldTaskEmission.ts`/`syncEngine.saveReading` do R3-D) e qual o destino dos arquivos de campo do R3-D e do commit 3 do R3-C. Nada no lote precisa ser refeito — só a decisão de pouso.
- **Ciclo do grave R6-G1 (processo) — FECHADO.** A dobra R4-m4 (`submitReading` sem try/catch) entrou depois da revisão/confirmação/portão registrados, sem cobertura; o ciclo novo executou: produtor da dobra registrado ANTES do ciclo → re-apreciação do delta **APROVADO** (1 errata menor D1: linhas removidas `controller.ts` +8/−2, `screens.ts` +5/−0) → confirmação do delta **CONFIRMADO** → portão re-rodado sobre o pacote finalizado: exit 0. A F2-2/R6-M1 (rótulo `Re-apreciador:` → `Revisor:`) foi re-confirmada no mesmo ciclo. Lições registradas: artefatos de revisão/confirmação não são editados in place por terceiros; o portão não verifica cronologia conteúdo × veredito (limitação conhecida — a guarda é o ciclo de re-apreciação + confirmação; regra nova passa pelo rito próprio do portão, nunca edição in place).
- **Descartado com prova:** R3-M2 (FKs ED-56/ED-59 para `asset(tenant_id, id)` já cobertas pela migration real da P1.35, `V27_1__asset_registry.sql`, `uq_asset_tenant_id`). Não re-executado: R2-G1 (V102 já pousada por outro coordenador). M2 e M3 residual, da decisão em bloco do dono de 09/11/2026 ~00:10, fechados por outros coordenadores; M1 residual segue este fluxo do R3-D.
- **Pendências que ficam:** V103 / P-R2G1-1 (UNIQUE da chave de remessa — decisão de desenho pendente: coluna persistida + fiação × UNIQUE composta); P-R2G1-2 (V100 fora da lista de publicadas) e P-R2G1-3 (CiTest da P3.12 desalinhado), pré-existentes; P-R3D-7 parcialmente atendida (tsc completo segue para a CI no PC do dono); CI total definitiva sobre o HEAD corrente (gargalo nº 1); piloto P0.30 — decisão NOVA do dono em 09/11/2026 00:19: sem cliente real, produto vendido em licitação após a conclusão; a P0.30 vira base fictícia de testes (12.000 UCs, pacote `base-ficticia-12k` em produção na esteira).

## 09/11/2026 — Cascata ED, Camada C7 (ED-75)

Pacote de evidência e auditor externo pelo método das ondas sob a ESTEIRA NOVA (produção → revisão independente → correção → reapreciação → confirmação → portão mecânico → rebase verificado no ato → push → verificação pós-pouso por hash). Base `fde45c5`, HEAD no ato do push `82bad7ce` (releitura no ato; ausência remota dos 21 caminhos repetida contra esse HEAD — todos ausentes, lote de arquivos novos, nenhuma linha alheia tocada). **Pouso `625eb060`** (21 arquivos, 100.848 bytes, lote único): `feat(ED-75): pacote de evidencia e auditor externo (SQL pendente)`. **Pendência P-C7-POUSO — FECHADA:** aprovações de escrita liberadas pelo dono em 09/11/2026 às 00:12 −03; a tentativa anterior (08/11/2026) parara por `approval_expired`. Placar da cascata após o pouso: ED 90/98, geral 260/286 (meta da C7).

- **Base (C7):** revisão APROVADO (D1/D2 menores) → correção (JUnit 38/38) → reapreciação CORREÇÃO CONFIRMADA → confirmação CONFIRMADO (21/21 hashes) → portão mecânico APROVADO (exit 0). SQL em `db/pendente/` (numeração V100+ é a pendência P-ED75-04, da integração); calculador da ED-19 não tocado.
- **Dobra R5-A (repasse da Rodada 5):** F1-B1 (`safeRef()` nos caminhos concedidos), F1-B2 (índices UNIQUE idempotentes no SQL + NEG11/NEG12), F1-B3 (`coveredStandards` no manifesto) — reapreciação do delta APROVADO, confirmação do delta CONFIRMADO, portão re-aprovado (exit 0). JUnit 45/45, PG 12/12 negativas.
- **Dobra R6-A (repasse da Rodada 6):** R6-A1 (CHECK `evidence_ref = btrim(evidence_ref)` nas duas tabelas + NEG13/NEG14, recusa provada pelo CHECK), R6-A2 (Javadoc ajustado — `attemptWrite` recusa sem registrar; decisão de execução: ajustar o Javadoc, não o código), R6-A3 (`pg-proof.sh` captura o stdout em `harness-logs/pg-proof-stdout.log`) — produtor (sessão `96ec1dac`) → reapreciação do delta **Veredito: APROVADO** (sessão `e3e50404`, linha única com "veredito" + token, R6-A4; UUID no cabeçalho, R6-A5) → confirmação do delta **CONFIRMADO** (sessão `116385e8`, hashes 5/5, ordem temporal provada) → portão re-rodado sobre o pacote atualizado: **APROVADO (exit 0)**, regras 1–6. JUnit **47/47**, PG APPLY 0 / REAPPLY 0 / **14/14 negativas**.
- **Verificação pós-pouso por hash (estágio 8):** commit `625eb060` na história da main (entre `82bad7ce` e os lotes de seed que pousaram depois); **21/21 arquivos byte-idênticos** entre a entrega local e a main (SHA-1 de blob git + SHA-256 do texto), 0 divergências. A main andou durante a operação (lotes de seed 1–4/283 da base fictícia pousaram após o commit da ED-75; HEAD `a0ad0db8` na verificação) — nenhum arquivo da ED-75 foi tocado pelo trabalho paralelo.
- **Pendências que ficam (nomeadas no RELATORIO):** P-ED75-01 (conteúdo do escopo regulatório → ED-97) · P-ED75-02 (instância dedicada → LC.15) · P-ED75-03 (duração/teto de validade `[A DEFINIR]`) · P-ED75-04 (numeração V100+ do SQL pendente e destino do INSERT do parâmetro, na integração) · P-ED75-05 (fiação persistência/identidade/HTTP) · P-ED75-06 (esteira — fecha com este pouso). Próximas camadas: **C8** (ED-97) e **C9** (ED-98) para a meta da cascata (ED 92/98, geral 262/286).
## 09/11/2026 — Fusão do R3-D na implementação viva da main (decisão do dono, item 35)

A decisão de merge/canônico que ficou pendente no verbete do lote R3 foi tomada pelo dono em 09/11/2026 às 00:44 −03 (`DECISOES-DO-DONO.md`, item 35): **fundir o R3-D na implementação nova da main** — rejeitada a alternativa de pousar só o SQL da ED-69 e descartar os arquivos de campo do R3-D. Pacote `fusao-r3d-main` sob a ESTEIRA NOVA (produção → revisão independente → confirmação por agente distinto → portão → rebase verificado no ato → push → verificação pós-pouso por hash → diário). Com este pouso, o residual M1 (R2-M1..R2-M4) e o B2-10/ED-69-D2 estão POUSADOS — o lote R3-D encerra-se pela fusão.

- **Mapa da fusão (item a item, contra os blobs vivos):** R2-M1 já coberto (a main tem `startCampoApp` + montagem de OS; `index.html` byte-idêntico) — entrou só o teste da entrada (`main.test.ts`, novo, 3 testes adaptados). R2-M2/P-03 já coberto no essencial (`fieldTariffSnapshot.ts` vivo + `emit()` com try/catch) — fundidas as **4 recusas do espelho Java que faltavam** no snapshot vivo (largura de faixa > 0, preço ≥ 0, leitura pertencente à tarefa, débitos/créditos válidos, +3 testes). R2-M2/P-04 já coberto (`LocalStore.saveReading` obrigatório no vivo + `createSaveReading`) — fundida a **dobra R4-m4**, que a main NÃO tinha: try/catch em `submitReading` devolvendo `{ ok:false, error }` + `.catch` em `screens.ts`, com o teste R4-m4 portado byte-idêntico e o teste P-04 de sync. R2-M3 já coberto (módulo dedicado vivo `campo/src/ui/orders/*` supera as telas 6/7 do R3-D) — entrou só o teste DOM de OS (novo, 2 testes). R2-M4 já coberto (a main usa `RouteTask.unitAddress`, mesmo campo com outro nome, exibido nas Telas 1/2). Divergências reais (fallback porta→store do R3-D × teste vivo "sem a porta → memória de sessão"; exibição `[A DEFINIR]` × só-IDs) resolvidas **para a main**, com o destino dos testes não portados registrado no relatório da fusão. **B2-10/ED-69-D2:** o SQL alinhado do R3-D entrou byte-idêntico (`bb4167e8…`; +52/−4 sobre o original que a main tinha).
- **Esteira e placares:** produção (sessão `f6f27599`) → revisão **APROVADO** (sessão `9f4c1988`; achados menores R-1/R-2, não-condição de pouso) → confirmação **CONFIRMADO** (sessão `74967b8d`; hashes 9/9). Placares reexecutados pelos três papéis: baseline viva **99/99**, baseline R3-D **96/96**, suíte fundida **109/109, 0 fail** (99 vivos intactos + 10 portados/novos). Prova PG do SQL da ED-69 reexecutada pelos três papéis: apply exit 0, 6 tabelas, trigger presente, 2 positivos OK, 3 negativos rejeitados — **P-R3D-6 FECHADA** com prova própria. Calculador da ED-19 intocado (`fieldEmission.ts` SHA-256 `643746402546885b…` na main após o pouso; 0 arquivos Java).
- **Portão — falso positivo demonstrado em artefato (PROTOCOLO §3.2):** a rodada mecânica bloqueou nas Regras 1 e 3 porque o extrator de não-independência casou o padrão `não é confirmação` na frase de distinção de papel do cabeçalho da revisão ("Esta revisão NÃO é confirmação (estágio 4 é de terceiro agente)"). A demonstração de falso positivo, com a verificação manual de C1/C3 (quatro sessões distintas lidas dos cabeçalhos, corroboradas pelo item (e) da confirmação), está lavrada em `fusao-r3d-main/PORTAO-FUSAO-R3D.md`. O `portao-deploy.py` **não foi editado**; a limitação do extrator fica registrada (correção, se houver, pelo rito próprio do portão).
- **Pouso (2 lotes):** rebase verificado no ato (HEAD `4ffbff19`; 8/8 blobs vivos idênticos à base da fusão; os 2 arquivos novos ausentes na main; nenhuma linha alheia removida) → lote campo **`8e3941e8`** (8 arquivos) → lote SQL **`8c6dcaba`** (ED-69). **Verificação pós-pouso: 9/9 arquivos byte-idênticos** à entrega (SHA-256 lidos da main).
- **Pendências que ficam:** P-FUSAO-2 (o servidor ainda não emite `FieldOrderPackage` — RG2-C-P3), P-FUSAO-3 (desenho do `previousReading` nulo: o Java recusa × a main monta), P-FUSAO-4 (erro de tsc PRÉ-EXISTENTE na main em `ui/screens.dom.test.ts:126`, fora desta entrega); herdadas sobrevivem: P-R3D-3 (corpo SQLCipher do `LocalStore` — RG2-C-P4) e P-R3D-8 (bootstrap nativo chama `startCampoApp`); P-R3D-1/P-R3D-2/P-R3D-5 resolvidas por desenho na implementação viva. O SQL da ED-69 segue em `db/pendente/`, sem número (P-R3D-9).

## 09/11/2026 — Cascata ED, Camada C8 (ED-97)

Pacote regulatório de saída por agência, com subsídios e Índice de Tarifa Social, sob a ESTEIRA NOVA (produção → revisão independente → correção → reapreciação → confirmação → portão mecânico → rebase verificado no ato → push → verificação pós-pouso por hash). Base declarada pelo produtor `2d211228`, HEAD no ato do push `2d211228` (releitura no ato; ausência remota dos 17 caminhos conferida — todos novos, nenhuma linha alheia tocada). **Pouso `4e808ac1`** (17 arquivos, 59.303 bytes, lote único): `feat(core): ED-97 — pacote regulatorio de saida por agencia, subsidios e Indice de Tarifa Social (SQL pendente)`. Placar da cascata após o pouso: ED 91/98, geral 261/286.

- **Cadeia:** revisão APROVADO COM RESSALVAS (D1 MODERADO — ExportPackage: separação agregado×nominal e hash só pela fábrica; D2–D5 menores) → correção (D1–D5 por construção, régua no construtor canônico; 20 testes novos; JUnit 45/45; PG APPLY=0/REAPPLY=0, 4/4 negativas) → reapreciação APROVADO (45 sondas próprias, 0 falhas) → confirmação CONFIRMADO (17/17 hashes recomputados) → portão mecânico APROVADO (exit 0, regras 1–6).
- **Conteúdo:** camada de exportação parametrizada por agência sobre o perfil regulatório da P2.31 (RAS-01) — leiaute, periodicidade, canal, versão do leiaute e registro de envio com hash, modo assistido do ADR-0004 (`reg.reports`, classe d, REG); conteúdo GES-04 (consumo médio por categoria e faixa, receita e usuários subsidiados, custo do subsídio tarifário/fiscal/interno — Lei 11.445, art. 31, contratos de grandes usuários, inadimplência por categoria, relatório anual de beneficiários da tarifa social); Índice de Tarifa Social mensal (economias residenciais beneficiadas sobre ativas) com reconciliação contra a lista positiva mensal da ANA e relatório anual à agência (CAD-92); perfis de fábrica ARIS, AGIR e ARESC; regra RAS-31 (fora de SC sob demanda; sem perfil, valores próprios do tenant); separação entre exportação agregada pública e exportação nominal restrita; teste por agência com massa sintética e comparação com o leiaute. SQL em `db/pendente/` sem número (numeração é a P-ED97-05, da integração).
- **P-ED75-01 RESOLVIDA:** o escopo do perfil externo do regulador da ED-75 está definido nesta entrega (`RegulatorExternalScope`: este pacote, os indicadores da ED-48, o relatório do art. 54 da NR 11, a ouvidoria e as interrupções — sem pacote de evidência ISO e sem dado pessoal), sobre os tipos da ED-75 sem alterá-los, com teste vivo de coerência com a matriz da ED-75.
- **Verificação pós-pouso por hash (estágio 8):** 17/17 arquivos byte-idênticos entre a entrega local e a main (SHA-1 de blob git), 0 divergências.
- **Pendências que ficam (nomeadas no RELATORIO):** P-ED97-01 (leiautes oficiais SISARIS/AGIR `[SEM FONTE]` — campos marcados, `layoutComplete=false`) · P-ED97-02 (ato/leiaute ARESC `[SEM FONTE]`) · P-ED97-03 (prova externa, categoria B: envio real às agências e lista positiva real da ANA não fingidos; confirmação do denominador do ITS com a NR 13) · P-ED97-04 (fiação JDBC/formatos e ligação com a agenda da ED-98) · P-ED97-05 (numeração V100+ do SQL pendente na integração). Próxima e última camada: **C9** (ED-98) para a meta da cascata (ED 92/98, geral 262/286).

## 09/11/2026 — Cascata ED, Camada C9 (ED-98) — CASCATA FECHADA

- **ED-98 — Agenda de obrigações regulatórias e de controle do tenant (GES-05)** — commit `02090935` (`feat(core): ED-98 — agenda de obrigacoes regulatorias e de controle do tenant (SQL pendente)`), 17 arquivos novos no subpacote `core/regulatory/agenda` de `shared/ziipsan-core` (90.349 bytes, lote único). Calendário de obrigações por tenant gerado do perfil `reg.*` e da natureza jurídica (`reg.obligations`, classe d): ciclo anual do SINISA, relatório anual de tarifa social, declaração anual de quitação (fatura de maio), balanço de perdas da NR 15, remessa bimestral ao e-Sfinge (IN TC-28/2021), DMR, SISAGUA, publicação de reajuste com antecedência mínima executável de 30 dias da data-base, RREO e RGF do ente quando aplicável (natureza + flag); cada item com responsável, evidência, situação e lembrete pelos alertas da ED-07 (portas); regras lidas do catálogo de normas da P3.15 (RegulatoryLibrary real exercitada nos testes); evidência de envio da ED-97 ligada ao item com conferência de tenant, período, tipo e estado de envio (RAS-08); fiscalização (agência, Tribunal de Contas, órgão ambiental) registrada como ocorrência da P2.25 via porta, com plano de ação e lembrete (JOR-24); MTR, CDF e DMR como item manual travado enquanto a ED-104 não for liberada (RAS-17); alerta antecipado de norma com data de efeito futura a partir da P1.34; teste com tenants de naturezas jurídicas diferentes gerando calendários diferentes. SQL em `db/pendente/` sem número (numeração é da integração, P-ED98-05).
- **Esteira completa:** revisão **APROVADO COM RESSALVAS** (D1, D2, D4 moderados; D3, D5, D6, D7 menores — sondas S1–S8) → correção por construção (as sondas viraram negativos permanentes) → reapreciação **APROVADO** (sondas refeitas 44/44) → confirmação **CONFIRMADO** (hashes 17/17 recomputados). Placares finais reproduzidos pelas cinco frentes: **JUnit 39/39**; PG **APPLY=0/REAPPLY=0, 3 positivas, 12/12 negativas recusadas**. Portão mecânico bloqueado na Regra 3 por falso positivo do extrator (cabeçalho da reapreciação declara a frente sem o token literal que o extrator exige) — resolvido pela rota do §3.2 do protocolo, com demonstração e verificação manual lavradas em `PORTAO-REGISTRO-ED-98.md`; o portão NÃO foi editado.
- **Verificação pós-pouso por hash (estágio 8):** 17/17 arquivos byte-idênticos entre a entrega local e a main (SHA-1 de blob git), 0 divergências.
- **Pendências que ficam (nomeadas no RELATORIO):** P-ED98-01 (dia de vencimento e periodicidades `[A DEFINIR]`) · P-ED98-02 (fiação da porta de ocorrências à P2.25) · P-ED98-03 (fiação da porta de lembretes ao TenantAlertService da ED-07) · P-ED98-04 (prova externa, categoria B) · P-ED98-05 (persistência JDBC e numeração V100+ do SQL pendente, na integração) · P-ED98-06 (risco residual do casamento regra×catálogo por token) · P-ED98-07 (amarração definitiva perfil da ED-97 × obrigação, na integração).
- **CASCATA ED FECHADA — as 9 camadas (C1–C9) pousadas e verificadas.** Placar final da cascata: **ED 92/98 · geral 262/286** — meta atingida. Fora da cascata restam: ED-01 (raiz externa — piloto/ensaio real) e ED-51, ED-68, ED-102, ED-103, ED-104 (sob demanda, classificação do dono).

## 09/11/2026 — Base fictícia de testes da P0.30 (decisão 34 + complemento EMASA) — POUSO COMPLETO

- **O que é:** base de testes 100% fictícia que substitui o piloto real da P0.30 (decisão 34 do dono, 09/11/2026: não há cliente real — o produto será vendido em licitação após a conclusão). Destino `seed/base-ficticia/`, FORA de qualquer `db/migration`. Tenant `11111111-2222-4333-8444-555555555555` ("Autarquia Ficticia Intermunicipal de Aguas — Porto das Gaivotas e Vale do Rio Claro (TESTE FICTICIO)"); 12.000 UCs em 23 situações; dois municípios fictícios — Porto das Gaivotas (IBGE fictício 9990001, 9.000 UCs) e Vale do Rio Claro (9990002, 3.000 UCs); a EMASA é apenas o modelo estrutural (dois municípios, água e esgoto próprios), sem nome, CNPJ ou qualquer dado real; gerador determinístico (semente 20261109); 123.120 linhas em 32 tabelas (307 arquivos `carga-*.sql`, 23.540.541 bytes de carga); 0 CPF/CNPJ, 0 dados reais.
- **Provas pré-pouso:** carga e limpeza executadas em PostgreSQL descartável por produtor, revisor e confirmador — 7/7 e delta do complemento EMASA 12/12; após a limpeza, 0 linhas do tenant e sentinela intacta. Portão mecânico com exit 1 por três falsos positivos demonstrados do extrator, lavrados em `PORTAO-REGISTRO-BASE-FICTICIA-12K.md` (§3.2); o portão não foi editado.
- **Pouso (plano consolidado de espera, 262 lotes):** 289 ficheiros novos (21.855.777 bytes); os outros 26 ficheiros do pacote já estavam na main byte-idênticos. Histórico: paradas por HTTP 429 nos lotes 136 (05:18:54Z) e 192 (08:17:39Z), ambos com o commit criado no servidor antes de o limite estourar — regularizados sem repulsar (ritos A-01 e A-07). Na retomada final (ordem do dono, 09/11/2026 12:00 −03), o desvio de HEAD causado pelo pouso da P3.13 (commit `b17fce5`, só `docs/homologacao/`) foi verificado por leitura viva e regularizado em `exec.log` antes do arranque; os lotes 193–262 pousaram todos à primeira tentativa, sem novo 429. **FIM: 262/262 lotes — HEAD final `fb9d346cdae444cdf7a2a266888bf294ea01233e`.**
- **Verificação pós-pouso por hash:** listagem integral de `seed/base-ficticia/` — **315/315 ficheiros byte-idênticos** à entrega (SHA-1 de blob git + tamanho), 0 faltas, 0 sobras, 0 divergências; críticos conferidos um a um (gerador, limpeza, `00-tenant-ficticio.sql`, `carga-0001.sql`, `carga-0307.sql`, LEIA-ME, RELATORIO-COBERTURA, `matriz.csv`, `manifesto.json`); SHA-256 das cargas contra o manifesto: **308/308**.
- **Pendências que ficam:** P-BF-01..P-BF-05, nomeadas no pacote — a base é massa de TESTE: uso e limpeza seguem o LEIA-ME e o `limpeza-base-ficticia.sql` pousados. P-BF-06, P-BF-07 e P-BF-08 foram decididas no item 45 das decisões do dono e tratadas nos blocos tarifário e multimunicípio, fora deste pacote.

## 09/11/2026 — Lote R7-A · Correções R7-B1..B4 da ED-97 (repasse da Rodada 7 da auditoria contínua)

- **O que é:** correções dos 4 achados da Rodada 7 sobre a ED-97 já pousada (commit `4e808ac1`), sem reabrir a cadeia ED-97: **R7-B1** (moderado) — o escopo externo passa a distinguir o kind do pacote: `RegulatorExternalScope.resourceForPackage(Kind)` / `canRegulatorReadPackage(Kind)` (pacote `NOMINAL_RESTRICTED` sem recurso externo e ilegível pelo regulador), `SubmissionRecord.isRestricted()`, `evidenceRefsFor` só devolve refs agregadas por padrão (kind explícito via sobrecarga) e `recordsOfKind(Kind)`; **R7-B2** (menor) — o SQL pendente ganha o CHECK de protocolo não-branco (`ck_reg_export_submitted_needs_protocol` estendido) e o CHECK de formato de `evidence_ref` (`ck_reg_export_evidence_ref_format`), com +2 testes de consistência Java×SQL; **R7-B3** (menor) — `lineHasField` casa o campo pela chave do segmento (antes do primeiro `=`), case-insensitive; **R7-B4** (menor, processo) — regra do UUID de sessão de cada ator no cabeçalho dos artefatos, registrada e aplicada já neste lote. 8 arquivos (34.084 bytes): 7 alterados in place no subpacote `core/regulatory/export` de `shared/ziipsan-core` + o teste novo `CorrectionsR7ATest.java` (9 testes: 4 R7-B1 + 1 R7-B2 + 4 R7-B3). O SQL segue em `db/pendente/`, sem número Flyway.
- **Esteira completa:** correção → revisão **APROVADO** → confirmação **CONFIRMADO**, três sessões distintas com UUID registrado nos cabeçalhos. Placares reexecutados pelas três frentes: **JUnit 56/56** (45 originais + 11 novos); PostgreSQL descartável: **7/7 negativas recusadas** (4 originais + NEG5/NEG6/NEG7 do R7-B2), com a prova do erro literal dos CHECKs em instância descartável. Portão mecânico exit 1 só na Regra 3 — **falso positivo demonstrado** em `PORTAO-REGISTRO-R7-A.md` (§3.2: o rótulo "Corretor" do produtor não consta da lista de padrões do extrator; os três UUIDs são distintos e estão declarados nos cabeçalhos); o portão **não foi editado**. Nenhuma asserção afrouxada; os 10 arquivos restantes da entrega ED-97 seguem byte-idênticos ao pouso.
- **Pouso:** a primeira tentativa ficou suspensa por HTTP 429 (FECHO-R7-A, 09/11/2026 ~02:38 −03 — nenhum push tentado). Executado agora por ordem do dono (09/11/2026 13:01 −03), em lote único sobre o HEAD `3edcbf7…` (diário da base fictícia). **Rebase verificado no ato:** os 7 arquivos alterados estavam na main byte-idênticos aos originais da ED-97 (SHA-256 `a0bfb48b…`, `2542e4e8…`, `48ec351e…`, `456c1bd3…`, `a3f46fe7…`, `9584e248…`, `d6d4e16d…`) e `CorrectionsR7ATest.java` ausente — nenhuma linha alheia removida. A tabela hash a hash da verificação pós-pouso dos 8 arquivos (contra o `PORTAO-REGISTRO-R7-A.md` §5) fica arquivada no pacote do lote (`correcoes-r7-a/VERIFICACAO-POS-POUSO-R7-A.md`).
- **Pendências que ficam:** P-ED97-04 (a fiação externa deve consultar `resourceForPackage` e recusar expor pacote sem recurso — requisito registrado no Javadoc) segue fora do escopo; a observação sobre `AgendaItem.linkSubmission` (ED-98) ficou para a revisão completa da ED-98.

## Lote R7-B — correções da Rodada 7 (base fictícia 12k) — 09/11/2026

- **Origem:** achados R7-I-M1 (moderado), R7-I-m1 e R7-I-m2 (menores)
  da Rodada 7 da auditoria contínua (01:43 −03, 09/11/2026).
- **O que pousou:** errata do lote 11
  (`base-ficticia-12k/push/ERRATA-LOTE-11.md`); adendos com os números
  pós-complemento em `base-ficticia-12k/RELATORIO-BASE-FICTICIA-12K.md`
  e `base-ficticia-12k/LANDING.md` (123.120 linhas; 23.540.541 bytes);
  adendo + nota sobre o teto do runner no diário C6
  (`cascata-ed/RELATORIO-C6.md`,
  `cascata-ed/push/diario-c6/ADENDO-TETO.md`) e correção do texto
  "~110 KB" em `cascata-ed/push/runner-big.py` (teto real 400.000
  bytes); relatórios do lote em `correcoes-r7-b/`.
- **Precisão factual (ficheiro n.º 3):** `base-ficticia-12k/LANDING.md`
  pousa no estado `47acae72519fde7bb84fc14f9c5b1d1deca37a20` (39.714 bytes)
  por força do ciclo de reapreciação de 09/11/2026
  (`ADENDO-REAPRECIACAO-R7-B-2026-11-09.md`, SHA-256
  `94e4a76346b1cbab9d6a84d79c808864c9f3afb395f69c5917313f51d4625a06`,
  revisão APROVADO, confirmação CONFIRMADO — artefactos locais do lote),
  e NÃO no estado `8478e2cb…` da tabela original do
  `PORTAO-REGISTRO-R7-B.md` §5; a tabela pós-pouso actualizada é a do §4
  do adendo de reapreciação.
- **Cadeia:** correção (sessão b6cf0c91) → revisão APROVADO (4fa0183b)
  → confirmação CONFIRMADO (48bd52df); portão mecânico com falso
  positivo na Regra 3 resolvido pela rota do §3.2
  (`correcoes-r7-b/PORTAO-REGISTRO-R7-B.md`, local); verificação
  pós-pouso por hash dos 9 ficheiros contra a tabela actualizada —
  resultado registado no acto a seguir ao pouso, na tabela arquivada
  localmente em `correcoes-r7-b/VERIFICACAO-POS-POUSO-R7-B.md`.
- **Commit(s):** commit único deste lote (lote único sobre o HEAD
  `1bd9278a6d71e9888ca32d6b4eefed4b366aeab3`) — o próprio commit que
  introduz este verbete; o seu SHA é registado após o pouso na tabela
  de verificação local acima referida, nunca inventado aqui.
- **P-BF-06/07/08:** sem execução; aguardam decisão de desenho do dono.

## 09/11/2026 — Lote R7-C (registros) — Rodada 7 da auditoria contínua — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso, lote único)

Correções de registros da Rodada 7 (fonte: `00-CONSOLIDADO-RODADA7.md` §3). **R7-C1** — pouso do diário R3 (commit `9a8c9c59`, 00:45:12 −03, blob `4aac1ce4`) registrado por adendo datado em `diario-r3/README-BLOQUEIO.md` e `RELATORIO-CONSOLIDADO-R3.md`; bloqueio dado por ENCERRADO (sem retomada, remescla ou re-push do verbete). **R7-C2** — canônico do merge R3-D registrado (diário da fusão `fdec0557` + item 35 de `DECISOES-DO-DONO.md`); `VERBETE-R3.md` intocado. **R7-C3** — regra prospectiva (tabela hash a hash nas próximas camadas) registrada em `cascata-ed/ESTADO.md` e aplicada já neste pouso (tabela em `correcoes-r7-c/PORTAO-REGISTRO-R7-C.md`). **R7-B5** — divergência e-Sfinge do item 29 registrada por adendo em `cascata-ed/RELATORIO-C9.md` (bimestral segue o aceite da ED-98; decisão 29 com o dono). Cadeia: correção (sessão `3e666a70`) → revisão **APROVADO** (`b9a868db`) → confirmação **CONFIRMADO** (`d4fe6d4e`) → portão (regras 1, 2, 4, 6 OK; regra 3 falso positivo demonstrado em `PORTAO-REGISTRO-R7-C.md` §3.2; `LANDING.md` escrito pelo Portão) → rebase verificado no ato (HEAD `430c0a1df5189ff865cd342db217bb0d455e449a`; 7/7 arquivos novos na main; diário vivo blob `8be52da87777d53ed62a82aa4a4718d602888c32`) → verificação pós-pouso por hash (8/8).

**Precisão factual (ficheiro n.º 4):** `cascata-ed/RELATORIO-C9.md` pousa no estado reapreciado `debc3f5428e4e50d9052b60c5d973bac1b4dd016` (13.428 B, 203 linhas), já com a errata do lote R8-C (achado A-03), por força do ciclo de reapreciação de 09/11/2026 (adendo `ADENDO-REAPRECIACAO-R7-C-2026-11-09.md`, SHA-256 `c89be7ae032ded4800494f04a4ad16ab55ed1b0d1bc7cccfe06d1ecaade56217`, revisão APROVADO, confirmação CONFIRMADO — artefactos locais, não pousam), e NÃO no estado `ac070186ffe0ae0b2fb7ff934470b2929338ecaa` da tabela original do `PORTAO-REGISTRO-R7-C.md` §6; a tabela pós-pouso actualizada é a do §4 do adendo de reapreciação.

## 09/11/2026 — ED-51 — Apoio à ISO/IEC 17025 — pousada (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: 27 ficheiros novos do pacote `br.com.ziipsan.core.certification.lims17025` (17 Java de produção + 1 SQL pendente `shared/ziipsan-core/src/main/resources/db/pendente/ED-51__lims17025.sql`, SEM número Flyway — prefixo `ED-51__` intacto, 18 tabelas + 1 doc `docs/laboratorio/ED-51-LIMS-17025.md` + 8 testes) + o adendo ED51-1 a viajar junto (`ed-51/ADENDO-R8-ED51-1-PD08-DECIDIDO.md` — PD-08 decidido pelo dono, item 40; o pacote `ed-51-pd08` NÃO pousa neste lote). Cadeia: produção (sessão `50df4ab1`) → revisão **APROVADO** (`f715b5ec`) → confirmação **CONFIRMADO** (`026139fd`) → correcção R-01(i) (produtor `67022cc3`; 2 ficheiros emendados antes do pouso — `AssayExecution.java` e `AssayExecutionTest.java` —, SQL intocado; ciclo fechado no lote R8-A com revisão/reapreciação **APROVADO** do revisor `694c9b1b` e confirmação **CONFIRMADO** do confirmador `c6f3b1dc`, sessões distintas) → placar actualizado **41/41, 0 falhas** (40 originais + 1 teste novo `blankQualitativeIsNormalizedToNull_R01i`) → portão (exit 1 = falso positivo da Regra 3 demonstrado em `PORTAO-REGISTRO-ED-51.md` §3.2; portão não editado) → rebase verificado no ato (HEAD `70395bda8d7e622af953456ab621aed26723e0a0`; 28/28 destinos ausentes na main; diário vivo blob `1121442a5dcc1e324fa0bb22055f47a548022ae2`) → verificação pós-pouso por hash registada no artefacto local `ed-51/VERIFICACAO-POS-POUSO-ED-51.md` (não pousa). **Pendência P-ED51-02 permanece:** fiação das portas e numeração Flyway do SQL pendente são da integração/CI.

## 09/11/2026 — GL.4 — Aceite da homologação local — pousada (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: 2 ficheiros novos em `docs/homologacao/` — `DOSSIE-ACEITE-HOMOLOGACAO-GL4.md` (compilação GL.2/GL.3 + conferência FEBRABAN 150 + declaração oficial/emissor) e `TERMO-DE-ACEITE-GL4.md` (termo de decisão formal, pousa **em branco**, tal como entregue) — + este verbete sobre o blob vivo. **ACEITE formal do dono registado:** decisão nº 37 de `DECISOES-DO-DONO.md` (09/11/2026, 03:08 −03, cartão de decisão na sessão principal) — aceite sem ressalvas do dossiê tal como entregue e confirmado, incluindo os limites que o próprio dossiê declara: GL.2 sem ciclos reais executados (aparato 56/56; limiares na P3.13, pendentes), GL.3 sem ensaio real executado (aparato 65/65), conferência FEBRABAN 150 com arquivos de teste e **sem envio ao banco** (28/28), nenhum sistema real em produção (a base fictícia é só teste). Ramo FEBRABAN: **"com optantes"** (caminho duplo de 08/11/2026: Pix Automático + FEBRABAN 150 legado). Ato registado em `gl.4/ACEITE-DO-DONO-GL4.md` (artefacto local do pacote; o termo em branco não foi alterado — o ato preenche-o por referência). **Pendência GL.4-01 RESOLVIDA** com este registo; GL.4-02 (assinatura do piloto) não se aplica no modelo vigente (decisão nº 34: sem cliente real) e só nasce com cliente real. Cadeia: produção (sessão `ca3d8c13`) → revisão **APROVADO** (`137199b2`) → confirmação **CONFIRMADO** (`a98eaed1`), três sessões distintas → portão (exit 1 = falso positivo da Regra 1 demonstrado em `PORTAO-REGISTRO-GL.4.md`, §3.2, artefacto local; portão não editado) → rebase verificado no ato (HEAD `f941d016903bcc65e1f8b9c6186e9e4be97b4664`; 2/2 destinos ausentes na main; diário vivo blob `884f99c9ecbadacd46e834df3210fd2d22d5e765`) → verificação pós-pouso por hash registada no artefacto local `gl.4/VERIFICACAO-POS-POUSO-GL.4.md` (não pousa). **Desvio D-GL4-01 não corrigido neste pouso:** a contagem de testes no relatório da P3.24 (24 declarados; 28 reais) é errata separada no pacote P3.24 (pendência GL.4-04), conforme o `LANDING.md`.

## 09/11/2026 — LC.1 — Ensaio de implantação na AWS com dado sintético (preparação) — pousada (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: 4 ficheiros novos em `docs/lancamento/` — `ROTEIRO-ENSAIO-IMPLANTACAO-AWS-LC1.md` (roteiro executável do ensaio: 7 fases, 24 passos, cada passo com evidência esperada e critério de aprovação/reprovação), `MATRIZ-EVIDENCIAS-LC1.md` (matriz aceite A0–A11 → evidência, com o A7 desdobrado pelos 4 serviços), `PREREQUISITOS-LC1.md` (pré-requisitos com estado conferido localmente e limites declarados) e `MODELO-REGISTRO-DIFERENCAS-LC1.md` (modelo do registo A10) — + o adendo datado `ADENDO-PREREQUISITOS-LC1-2026-11-09.md`, prescrito pelo `LANDING.md` para o defeito menor D-R1 (estados que mudaram entre a produção e o pouso), + este verbete sobre o blob vivo. **Natureza: preparação — o ensaio NÃO foi executado nem simulado**; conferência mecânica local reexecutada na revisão e na confirmação: **12/12 itens do aceite (A0–A11)** com passo e evidência, **A7 4/4 serviços**, **24/24 passos** com evidência e critério. **Adendo regista:** GL.4 com aceite formal do dono (decisão nº 37 de `DECISOES-DO-DONO.md`, 09/11/2026 03:08 −03; pendências GL.4-01 e P-LC1-07 resolvidas; pacote pousado no commit `32fff01c0b7ea04e435d901443495366f6479b3b`); ED-01 pousada (commit `6495e78ead8eec9695f81807b32df2cdb526fba6`; verificação PD-01 55/59 comprovados, 4 parciais dos pacotes de origem — prontidão **não declarada fechada**); IaC local da LC.2 já produzida (ficheiros `.tf`), sem resolver por si a P-LC1-02 (IaC do ensaio continua **[A DEFINIR]**); conta AWS de ensaio **adiada por decisão do dono** (decisão nº 41, 09/11/2026 03:11 −03 — fica para a fase de contratação; P-LC1-01 passa a ato externo adiado por decisão). Cadeia: produção (sessão `a1981a33`) → revisão **APROVADO** (`9eb7d4a7`) → confirmação **CONFIRMADO** (`e9dac965`), três sessões distintas → portão **APROVADO** (`portao-saida.txt`: regras 1–6 OK; o item (3) do protocolo — rebase — é verificado neste ato) → rebase verificado no ato (HEAD `6495e78ead8eec9695f81807b32df2cdb526fba6`; os 4 destinos e a pasta `docs/lancamento/` ausentes na main; diário vivo blob `da76a76b3de15a2b63d4ba376573ee84ef3890cb`) → verificação pós-pouso por hash registada no artefacto local `lc.1/VERIFICACAO-POS-POUSO-LC.1.md` (não pousa). **Pendências que permanecem** (todas do ensaio futuro, nenhuma trava este pacote de preparação): P-LC1-01 (adiada por decisão), P-LC1-02 a P-LC1-06 e P-LC1-08, nomeadas no `RELATORIO-LC.1.md`.

## 09/11/2026 — LC.2 — IaC de homologação, produção e demonstração na AWS `sa-east-1`; borda — pousada (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **44 ficheiros novos** — `infra/aws/README.md`; 5 módulos reutilizáveis em `infra/aws/modules/` (20 `.tf`: `foundation`, `edge` — CloudFront/WAF/TLS/ACM e domínio do tenant, `platform` — ALB/ECS Fargate/ECR, `security` — TR-091/092/096, `messaging` — SES por tenant); 4 ambientes parametrizados em `infra/aws/envs/` (20 ficheiros: `homologacao`, `producao`, `demonstracao` e `dedicado`, modelo da LC.15); pipeline `.github/workflows/deploy-infra.yml` (validate → plan → apply, produção atrás do Environment `producao` com aprovação manual, rollback partido em plan/apply); e os procedimentos `docs/operacao/ROLLBACK.md` e `docs/operacao/MENSAGERIA.md` — + este verbete sobre o blob vivo. **Natureza: código produzido e validado localmente — NÃO aplicado na AWS**; aplicação, exercício de rollback e ambiente de demonstração na nuvem são pendências externas **P-LC2-01/02/03** (não registados como aplicados nem executados), com **P-LC2-04** (valores `[A DEFINIR]`), **P-LC2-05** (`terraform fmt`/`validate` reais — sem binário nesta VM) e **P-LC2-06** (BSP concreto de WhatsApp/SMS) também pendentes. Evidência: harness estrutural `validar_iac.py` — **12/12** verificações originais do produtor, expandidas para **24/24** após a correção, reexecutado com o mesmo placar na reapreciação e na confirmação. Cadeia: produção (sessão `764b25ad`) → revisão **REPROVADO** (`0d9b418c`; moderados R-1/R-2/R-3 e menores m1–m8) → correção (`b36fd7a8`) → reapreciação **APROVADO** pelo mesmo revisor (`0d9b418c`; R-1/R-2/R-3 e m1–m8 todos CORRIGIDO) → confirmação **CONFIRMADO** (`453303e0`), sessões distintas → portão mecânico exit 1 = falsos positivos das Regras 1–3 demonstrados em substância no `PORTAO-REGISTRO-LC.2.md` (local, §3.2 do protocolo; portão não editado) → rebase verificado no ato (HEAD `0d0b0c5205492734a64ed4f54b10c254b177cb66`; **44/44 destinos ausentes** na main; diário vivo blob `47298e594ab5ad623bae526b7672fd16262706d6`) → verificação pós-pouso por hash registada no artefacto local `lc.2/VERIFICACAO-POS-POUSO-LC.2.md` (não pousa). **Divergências registadas abertamente:** (i) o `RELATORIO-LC.2.md` e o `LANDING.md` dizem 43 ficheiros — a contagem real é **44**, corrigida por adendo (A-1 da `CORRECAO-LC.2.md`) e reconfirmada na reapreciação e na confirmação, sem edição dos originais; (ii) o artefacto `REVISAO-LC.2.md` está truncado em disco a meio do menor m2 (76 linhas / 13.926 bytes) — facto registado na reapreciação §4 e na confirmação §5 (§5.4 do protocolo: histórico não se reescreve), com o sumário fiel de m3–m7 arquivado na reapreciação §4 e a localização do handoff-fonte declarada no adendo local `ADENDO-R8-A5-HANDOFF-LC2.md` (texto integral não encontrado; vive fora de ficheiro, entregue em sessão ao coordenador).

## 09/11/2026 — Decisão nº 48 do dono — permissão Workflows à app e repetição do lote completo da LC.2 (registo transportado pelo lote R15-A, P-R15-A-04, neste pouso da LC.4 — commit único deste lote; o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

**Decisão nº 48 do dono** (09/11/2026 16:25 −03, cartão de decisão LC.2, sessão principal; texto-fonte: entrada nº 48, §24, do registo local de decisões — ficheiro local da esteira, não ficheiro deste repositório): **"Concedo a permissão Workflows à app e repete o lote completo"**. A rota alternativa — pousar a LC.2 sem o workflow, entrando este pela sessão do navegador — foi **expressamente recusada**. **Execução registada na entrada nº 48:** repetição do lote completo às 16:32 −03 **[SEM FONTE** — a hora não está registada em nenhum ficheiro do workspace; é a hora indicada no brief do lote de correcção R15-A, e o registo da tentativa não data a hora da 2.ª tentativa], a partir do staging preservado: devolveu o **mesmo 403** (`failed to create tree: … 403 Resource not accessible by integration`), **nenhum commit criado** — prova de que, àquela hora, a permissão ainda não estava efectiva (ou não cobria a app instalada); o desbloqueio da LC.2 ficou como acto externo do dono sobre a instalação da app. Registo posterior visível nesta main: o workflow acabou pousado pelo próprio dono a partir do seu PC (commit `13814e1651124ffbfc2999e2f2e78a0d45e1bf4e`, "ci(lc.2): workflow deploy-infra da LC.2"), fechando a LC.2 em 45/45. **Correcções documentais da Rodada 15 (lote R15-A):** errata R15-F2/F3 às frases falsas sobre a dependência da LC.1 e sobre o âmbito da paragem da cadeia, e registo R15-F4 da instrução expressa do despacho ("sem dividir o lote, sem outra rota"); cadeia do lote com **9 sessões distintas** (valor corrigido pelo lote R16-A; o LANDING do R15-A dizia 8) e portão mecânico cuja saída em disco é a **2.ª corrida — 18 linhas, SHA-256 `4ccb149678e151855f96a786085b2b0e7d829ca53941e2da27322256a57bcbaa`** (o LANDING regista `c4d91d8e…`/19 linhas, valores da 1.ª corrida — correcção R16-A). **Transporte neste lote (P-R15-A-04):** viaja **só este verbete**; as 4 cópias byte-idênticas do lote R15-A (`ERRATA-R15-F2-F3.md`, `REGISTO-INSTRUCAO-DESPACHO-R15-F4.md`, `ADENDO-R15-A-REV-01.md`, `ADENDO-R15-A-REV-02.md`) **não viajam**: os originais que corrigem (`PORTAO-REGISTRO-LC.2.md` e `POUSO-BLOQUEADO-LC.2.md`, registos da LC.2; `REVISAO-R15-A.md` e `REAPRECIACAO-R15-A.md`, registos do próprio lote R15-A) **não existem na main** — sondagem viva neste acto, busca por nome de ficheiro com 0 resultados para cada um; são registos locais da esteira, nunca ficheiros de entrega do repositório, e nenhum caminho foi inventado para os alojar.

## 09/11/2026 — LC.8 — Pentest externo (pacote de escopo) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **4 ficheiros novos** em `docs/security/` — `PACOTE-ESCOPO-PENTEST-LC8.md` (escopo fechado item a item: 19 itens — 4 de alvo/critério E-01…E-04, 12 vetores do aceite V-01…V-12 e 3 da baseline B-01…B-03 —, pré-condições verificáveis PC-01…PC-08 e o portão formal "nenhum achado crítico ou alto aberto antes do primeiro dado real"), `REQUISITOS-CONTRATACAO-PENTEST-LC8.md` (requisitos de contratação e do relatório do terceiro; fornecedor, qualificação, preço e prazos **[A DEFINIR]**, sem fonte), `MATRIZ-COBERTURA-ACEITE-LC8.csv` (21 linhas: 17 itens do aceite decomposto A-01…A-17, 3 de baseline e 1 fora-de-escopo declarado) e `TERMO-RESULTADO-PENTEST-LC8.md` (**em branco**, sem veredito nem data) — + este verbete sobre o blob vivo. **Natureza: escopo produzido — o pentest NÃO foi executado**; a execução é ato de terceiro, futuro, e o aceite final da LC.8 só fecha com o pentest executado e o Termo de Resultado com veredito LIBERADO. **Pendências que permanecem** (todas externas, nenhuma trava este pacote de escopo): **P-LC8-01** (produção AWS inexistente — sem alvo de pentest), **P-LC8-02** (contas/tenants de teste na produção), **P-LC8-03** (autorização formal do ensaio e regras de engajamento), **P-LC8-04** (contratação do terceiro) e **P-LC8-05** (execução do pentest e preenchimento do Termo). Evidência: harness `validar-lc8.py` — **29/29 PASS, exit 0**, reexecutado com o mesmo placar pelo produtor, pelo revisor, pelo confirmador e pelo executor do pouso; cobertura do aceite **17/17**. Cadeia: produção (sessão `d9b12bda`) → revisão **APROVADO** (`81bbc0ae`) → confirmação **CONFIRMADO** (`728417f0`), três sessões distintas → portão **APROVADO** (`portao-saida.txt`: regras 1–6 OK; o item (3) do protocolo — rebase — é verificado neste ato) → rebase verificado no ato (HEAD `32f9b6240e467d51b1b2a7b773132f96e6a95f94`; os 4 destinos ausentes na main; diário vivo blob `999fdf65b776e30a7dd1a020d41ca6e96fc13359`) → verificação pós-pouso por hash registada no artefacto local `lc.8/VERIFICACAO-POS-POUSO-LC.8.md` (não pousa). **Divergências da baseline registadas abertamente:** **D-LC8-01** — o LLM Top 10 entra como critério condicional (E-04: aplicável se e somente se a ED-43 estiver na produção testada, com verificação PC-07 na abertura da janela), resolução julgada fiel às fontes na revisão; **D-LC8-02** — o plano DAST da P3.8 (`ZapScanPlan`) recusa alvo de produção por construção, pelo que B-03 será executado pelo terceiro com plano próprio.

## 09/11/2026 — LC.9 — Conjunto contratual pronto para assinatura — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **5 ficheiros novos** em `docs/contratos/` — `INDICE-CONJUNTO-CONTRATUAL-LC9.md` (montagem do conjunto por referência às 6 minutas da P3.9 — Contrato SaaS e DPA como instrumentos do ato; SLA, Suboperadores e Termos de Uso como anexos integrantes; Termo de Homologação Local fora deste ato — e adaptação ao modelo de licitação do item 34 das decisões do dono), `FICHA-INCORPORACAO-LC9.md` (incorporação por referência dos 5 elementos do aceite F-1…F-5, com o estado real de cada um nas minutas), `CHECKLIST-ASSINATURA-LC9.md` (portão S-01…S-10, **todos desmarcados** — nenhuma pré-condição satisfeita nesta data), `TERMO-ASSINATURA-LC9.md` (**em branco**, sem CNPJ, sem data, sem assinatura) e `PENDENCIAS-LC9.md` (os 14 valores abertos herdados da P3.9/ED-05 + as 7 pendências próprias P-LC9-01…07) — + este verbete sobre o blob vivo. **Natureza: conjunto PRONTO PARA ASSINATURA — NÃO ASSINADO.** Não há piloto nem cliente real (item 34): quem assina é o **cliente futuro**, vencedor da contratação por licitação, como **controlador**; a ZiipSan assina como **operadora**. Nenhuma minuta da P3.9 foi copiada, movida ou alterada por este pacote — as 6 minutas e o índice de rastreabilidade já estão nesta mesma pasta `docs/contratos/` na main (verificado neste ato) e o conjunto monta-as **por referência**; o DPA e o «acordo de tratamento» do aceite são **um só instrumento** (título oficial da minuta — divergência D-LC9-02). **Lacuna grave declarada (D-LC9-01):** a cláusula de emissão do atestado com prazo contado do corte (ADR-0023) **não existe em nenhuma das 6 minutas** e os dois valores (número de ciclos em produção; prazo contado do corte) estão **[A DEFINIR — SEM FONTE]** — o intervalo «3 a 5 meses» do ADR-0033 é estimativa de planejamento, **não** prazo contratual, e não foi promovido a cláusula. Sobre esta lacuna existe **minuta-proposta destacada** (`MINUTA-CLAUSULA-ATESTADO-PROPOSTA.md`, no pacote local da LC.9 — não pousa neste lote: a sua incorporação é ato futuro e separado), produzida sob autorização do item 39, revista **APROVADO** e confirmada **CONFIRMADO** em sessões distintas: é **PROPOSTA PARA REVISÃO JURÍDICA — NÃO É TEXTO CONTRATUAL VIGENTE**, **não** fecha o item S-06 do checklist e **não** declara a cláusula válida nem vigente; a análise jurídica final dos documentos é a pendência **P-JUR-FINAL-01** (item 44), a executar no final da execução do projeto, e a P-LC9-01 muda de natureza para «minuta-proposta pronta aguardando revisão jurídica e incorporação na contratação». **Pendências que permanecem** (condições do **ato de assinatura** futuro, não deste pacote documental): **P-LC9-01** (cláusula do atestado — revisão jurídica, definição de A1/A2 e incorporação), **P-LC9-02** (escolha do nível de serviço, ADR-0025), **P-LC9-03** (aprovação das minutas e assinatura pelo cliente futuro), **P-LC9-04** (conferência da declaração da LC.3 contra a Cl. 10ª — **conferida e conforme** na revisão desta LC.9, §5: núcleo idêntico, sem contradição; regista-se por esta via a errata do defeito menor **R-LC9-m1** — a FICHA F-4 e o `RELATORIO-LC.9.md` §2, no pacote local, descreviam a LC.3 como «ainda em produção», descrição desatualizada face à entrega da LC.3 com relatório, texto autónomo do risco residual e revisão APROVADO), **P-LC9-05** (portão da LC.8 — pentest não executado; produção AWS inexistente), **P-LC9-06** (anexação do texto oficial das cláusulas-padrão da ANPD na contratação) e **P-LC9-07** (decisão expressa sobre a premissa provisória da P3.15); nas minutas permanecem **59 ocorrências de `[A DEFINIR`** (contagem conferida por grep na revisão e na confirmação). Evidência: harness `validar-lc9.py` — **47/47 PASS, exit 0**, reexecutado com o mesmo placar pelo produtor, pelo revisor, pelo confirmador e pelo executor do pouso; cobertura do aceite: 11 itens (6 cumpridos por referência/redação existente, 3 convertidos em portão ou campo aberto declarado, 1 lacuna sem fonte, com o item do acordo de tratamento resolvido por identidade documental). Cadeia: produção (sessão `d9b12bda`) → revisão **APROVADO** (`81bbc0ae`) → confirmação **CONFIRMADO** (`728417f0`), três sessões distintas; cadeia própria da minuta-proposta: produção (`c8ed3d6e`) → revisão **APROVADO** (`4f6063d4`) → confirmação **CONFIRMADO** (`2a7bfcd6`). Portão mecânico: **falso positivo demonstrado** no registo local `PORTAO-REGISTRO-LC.9.md` — a Regra 1 falhou na extração do veredito, cuja forma literal é idêntica à da LC.8, aprovada pelo mesmo verificador com exit 0; a substância da Regra 1 está satisfeita e nem o verificador nem a revisão foram editados. Rebase verificado no ato (HEAD `9d85086db5cf7f9c281dab3eb5953c8b94a8e555`; os 5 destinos ausentes na main; dependência da P3.9 cumprida — minutas já pousadas nesta pasta; diário vivo blob `76512f95b089ed03e920f1a8539f907b666ee047`) → verificação pós-pouso por hash registada no artefacto local `lc.9/VERIFICACAO-POS-POUSO-LC.9.md` (não pousa). **Divergências declaradas abertamente:** **D-LC9-01** (acima); **D-LC9-02** (DPA = acordo de tratamento, um só instrumento); **D-LC9-03** (o aceite diz «minutas aprovadas na P3.9» — elas foram entregues com **aprovação pendente**; a aprovação vira o item S-09 do checklist); **D-LC9-04** («assinados com o piloto» inexequível — item 34; lê-se cliente futuro por licitação); **D-LC9-05** (menor, de encaixe: confirmação do encaixe do arcabouço Setor Público face ao nível escolhido, pela revisão jurídica da contratação).

## 09/11/2026 — LC.10 — Carga real do legado (roteiro e modelos) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **5 ficheiros novos** em `docs/carga-real/` — `ROTEIRO-CARGA-REAL-LC10.md` (roteiro passo a passo da carga: importadores da P1.2, da P2.10 e da P2.19 referenciados pelo nome/caminho reais, pré-condições com estado declarado, execução em ordem P1.2 → P2.10 → conciliação/assinatura → P2.19 XML → fechamento, guarda da massa sintética + varredura P0.45, parada/volta), `MODELO-RELATORIO-CONCILIACAO-LC10.md` (template **em branco**, com campo de assinatura do piloto/cliente), `MODELO-RELATORIO-RECUSAS-XML-LC10.md` (template **em branco**, vocabulário `LegacyXmlRejection` da P2.19, com campo de aceite do piloto/cliente), `MODELO-REGISTRO-EXECUCAO-IMPORTADOR-LC10.md` (template **em branco** de registo por execução/reexecução de cada importador) e `CHECKLIST-ACEITE-CARGA-LC10.md` (portão de pré-condições A, importadores só na produção B, conciliação e aceites C, guarda dos ambientes D, fechamento E) — + este verbete sobre o blob vivo. **Natureza: roteiro e modelos — NENHUMA CARGA REAL FOI EXECUTADA, simulada ou afirmada.** A execução é evento real futuro, **só na produção**, e as pré-condições estão declaradas **NÃO CUMPRIDAS** no roteiro (§2) nesta data: LC.3/LC.4/LC.5 de pé na AWS real, LC.9 assinado com o cliente, GL.4 decidido, cliente com legado real (fator externo, item 34 — não há piloto; o produto será vendido em licitação após a conclusão) e a integração da retenção da P2.19. **Pendências que permanecem** (condições da **execução** futura, não deste pouso documental): **P-LC10-01** (cliente/dado real inexistente — fator externo), **P-LC10-02** (pré-condições não cumpridas), **P-LC10-03** (integração herdada da P2.19: adaptador de `LegacyXmlRetentionPort` para a P2.18 real com a origem `LEGACY_IMPORT` — sem o adaptador, o passo de XML do roteiro não executa) e **P-LC10-04** (operador, conferente e representante do cliente `[A DEFINIR]`, a nomear na execução real). Homologação e demonstração na nuvem só com massa sintética, com o teste de varredura da P0.45 verde nesses ambientes (passo obrigatório do roteiro, §5.2). Evidência: validação de **cobertura documental 9/9** das cláusulas do aceite pelo conjunto roteiro + checklist + modelos, verificada mecanicamente pelo produtor, reexecutada por busca própria do revisor e confirmada por amostragem do confirmador (contagens idênticas: «só na produção» ×4 no roteiro; `LegacyImportService` ×6; `V1ImportService` ×6; `V1BalanceConference` ×3; `LegacyXmlImportService` ×6 no conjunto da entrega) — pacote documental: nenhum teste executado, nenhum código novo, nenhuma migration, remoção zero. Cadeia: produção (sessão `1fb44147`) → revisão **APROVADO** (`7c58ad44`) → confirmação **CONFIRMADO** (`40ddd5cb`), três sessões distintas. Portão mecânico: **falso positivo demonstrado** no registo local `PORTAO-REGISTRO-LC.10.md` (§3.2 do PROTOCOLO-ESTEIRA.md) — a Regra 3 (identidade) falhou na extração porque os cabeçalhos declaram autoria sob o rótulo «Sessão …», com os três UUIDs distintos declarados nos três artefactos; a substância da Regra 3 está satisfeita e nem o verificador nem os artefactos da cadeia foram editados (precedente idêntico: Rodada 7). Observações menores da revisão (não são defeitos de aceite nem condições de pouso): **O-1** (roteiro §5.1 e checklist D — o parêntese da homologação local GL.1 está inserido na frase da guarda da nuvem; a regra principal e a varredura obrigatória estão intactas; errata futura recomendada) e **O-2** (roteiro §2, linha da LC.9 — «aprovadas» sem qualificador para as minutas da P3.9; o estado NÃO CUMPRIDA declarado está correto). Rebase verificado no ato (HEAD `a7f8e79b0919774d60a7e4bd9746d192dcd5083f`; os 5 destinos ausentes na main; diário vivo blob `a670b81f852925f5dc04a2f9e468bf3d0dd8475f`) → verificação pós-pouso por hash registada no artefacto local `lc.10/VERIFICACAO-POS-POUSO-LC.10.md` (não pousa).

## 09/11/2026 — LC.11 — Paralelo com dado real (roteiro, registo e termo) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **4 ficheiros novos** em `docs/paralelo-real/` — `ROTEIRO-PARALELO-REAL-LC11.md` (roteiro do paralelo real: articulação com o plano preliminar da P3.13, leitura adotada do critério de divergência com fontes declaradas, pré-condições-portão, execução de um ou dois ciclos na produção conta a conta contra o legado, trava «sem emissão fiscal de produção», diferenças novas em relação à GL.2, conferência do débito automático com optantes e fechamento), `MODELO-REGISTRO-DIVERGENCIAS-LC11.md` (template **em branco**, um por ciclo: divergência por conta com explicação **obrigatória**, julgamento pelos limiares L-01/L-02/L-03, campo «Nova × GL.2?» e bloco próprio de conferência do débito automático; campo de base com a opção expressa `SEM BASE COMPARÁVEL — GL.2 sem ciclo real`), `MODELO-TERMO-RESULTADO-PARALELO-LC11.md` (template **em branco**: resultado por ciclo, declarações da trava fiscal, do débito automático, das diferenças novas e de «toda explicada», conclusão apto/não apto para submeter à LC.13 e assinaturas) e `CHECKLIST-PARALELO-REAL-LC11.md` (pré-condições A, ciclos conta a conta B, critério e divergências C, trava fiscal D, débito automático E, fechamento F) — + este verbete sobre o blob vivo. **Natureza: roteiro, registo e termo — NENHUM CICLO DE PARALELO REAL FOI EXECUTADO, simulado ou afirmado.** A execução é evento real futuro, na produção, e as pré-condições estão declaradas **NÃO CUMPRIDAS** nesta data: LC.10 concluída, GL.4 decidido, limiares L-01/L-02/L-03 fixados (pendência P3.13-01) e cliente real com legado (fator externo, item 34 — não há cliente real; o produto será vendido em licitação após a conclusão). **Leitura do critério declarada (não é fato da fonte):** o critério numérico «da P0.30» deixou de existir porque o aceite da P0.30 o exigia definido com o piloto e o item 34 encerrou a P0.30-piloto; adota-se a **estrutura** da P3.13 §1 (conta a conta; tipos VALOR/AUSÊNCIA/CONSUMO/VENCIMENTO; toda divergência explicada; fora do limiar reprova mesmo explicada), com os valores dos limiares `[A DEFINIR]` sob P3.13-01 como **portão** — sem fixação registada, nenhum ciclo pode ser declarado «dentro do critério». **Débito automático** conferido no roteiro pelo caminho duplo do item 9 (conversão ao Pix Automático, P4.5, **e** FEBRABAN 150 mantido como legado para a base atual; débito novo só por Pix Automático; remessa do ZiipSan × remessa do legado **sem envio duplicado ao banco** — no paralelo, o legado é o oficial e emissor), com a declaração expressa de que o parecer formal RAS-06/PD-10 continua inexistente como documento — a decisão é do dono, não é parecer. **Trava fiscal operativa:** `tpAmb=1` desligado para o tenant antes do 1º ciclo; qualquer `tpAmb=1` do ZiipSan durante os ciclos é violação registada como ocorrência própria, com **ciclo reprovado e paralelo suspenso** até decisão formal; ensaio fiscal só em `tpAmb=2` fora da produção. **Pendências que permanecem** (condições da **execução** futura, não deste pouso documental): **P-LC11-01** (cliente/dado real inexistente — fator externo), **P-LC11-02** (limiares L-01/L-02/L-03 `[A DEFINIR]` sob P3.13-01; L-04 sob P3.13-03), **P-LC11-03** (base GL.2 sem ciclo real — a GL.2 entregou o aparato sem executar ciclo real), **P-LC11-04** (aprovação da P3.13 pendente — P3.13-02) e **P-LC11-05** (levantamento de optantes `[SEM FONTE]` até existir cliente real). Evidência: validação de **cobertura documental 9/9** das cláusulas do aceite pelo conjunto roteiro + checklist + modelos, verificada mecanicamente pelo produtor, reexecutada por busca própria do revisor e confirmada por amostragem do confirmador (contagens idênticas: «conta a conta» ×3 no roteiro; campo «SEM BASE COMPARÁVEL» ×1 no registo e ×2 no conjunto da entrega; `tpAmb=1` ×2 e `tpAmb=2` ×1 no §5 do roteiro) — pacote documental: nenhum teste executado, nenhum código novo, nenhuma migration, remoção zero. Cadeia: produção (sessão `1fb44147`) → revisão **APROVADO** (`7c58ad44`) → confirmação **CONFIRMADO** (`40ddd5cb`), três sessões distintas. Portão mecânico: **falso positivo demonstrado** no registo local `PORTAO-REGISTRO-LC.11.md` (§3.2 do PROTOCOLO-ESTEIRA.md) — a Regra 3 (identidade) falhou na extração porque os cabeçalhos declaram autoria sob o rótulo «Sessão …», com os três UUIDs distintos declarados nos três artefactos; a substância da Regra 3 está satisfeita e nem o verificador nem os artefactos da cadeia foram editados (precedente idêntico: Rodada 7). Observação menor da revisão (não é defeito de aceite nem condição de pouso): **O-1** (a referência «C-01…C-13» é citada como conjunto no roteiro §1 e no relatório §2, sem itemização individual de C-02/C-03/C-12/C-13, cuja substância o revisor conferiu coberta; recomenda-se nota de mapeamento em errata futura). Rebase verificado no ato (HEAD `de3d9bc8e7ee3adfb13f25acc293ae3325776274`; os 4 destinos ausentes na main; diário vivo blob `c86fb6594e5e603447086136005011909a504137`) → verificação pós-pouso por hash registada no artefacto local `lc.11/VERIFICACAO-POS-POUSO-LC.11.md` (não pousa).

## 09/11/2026 — Base fictícia 12k — LANDING actualizado com o adendo do lote R13-C (verificação integral 315/315) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

O `base-ficticia-12k/LANDING.md` pousa na versão integral de 847 linhas (SHA-256 `814cff1409a52d1a37c8307531e21d57fc8b7cacd31425c4a0d1ca2db0720e51`): as primeiras 451 linhas são byte-idênticas ao estado que já estava na main e o adendo do lote R13-C (linhas 452–847) acrescenta a tabela integral da verificação pós-pouso dos 315 ficheiros da base (262 lotes), sem reescrever nenhuma linha anterior. Neste mesmo append, o cabeçalho deste diário passa a «Última atualização: 09/11/2026», pela instrução P-R17-B-02 do lote R17-B (a contagem então corrente de verbetes de 09/11, 18 pelo método da instrução, regista-se no artefacto local de verificação pós-pouso deste lote). Pouso executado na cadeia de pousos autorizada pelo dono em 09/11/2026 às 23:34.

## 09/11/2026 — Tarifário Camada 0 — ADR-0048 (arquitetura tarifária unificada) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Pousa o `docs/adr/ADR-0048-arquitetura-tarifaria-unificada.md` (SHA-256 `9d11a959bfc436bae912b659827f4d3e3afa43d12c5b5b79694662b5d2fcab1b`), ACEITO pelos itens 43 e 45 das decisões do dono de 09/11/2026: arquitetura tarifária unificada em camadas (catálogo, maestro, convênios, campo), com a dimensão município decidida no §4 do ADR (chave `municipality_ibge`). O ADR-0047 produzido por engano NÃO pousa — fica só local, no pacote `proposta-tarifaria/camada-0-adr-desenho`, como registo da derivação falhada do número (o 0047 já estava atribuído na main, ao arredondamento e tributo da conta). Numeração reverificada no acto do pouso: nenhum ADR-0048 existia na main, nem como ficheiro nem como referência. Os documentos de desenho do bloco não pousam, por designação do ADENDO-LANDING da camada. Pouso executado na cadeia autorizada pelo dono em 09/11/2026 às 23:34.

## 09/11/2026 — Tarifário Camada 1 — Catálogo de cobranças (billing.catalog) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Pousam os 17 ficheiros novos da Camada 1 no módulo `ziipsan-billing`: o catálogo de cobranças (`ChargeOwner`, `CalculationModel`, `ChargeDefinition`, `ChargeVersion`, `ChargeCatalog`, `ChargeResolution`, `ChargeBenefitHook`, `ChargeResolver`), as entidades JPA e os repositórios de definições/versões, o SQL pendente `CAMADA-1__charge_catalog.sql` e os 4 testes do catálogo. O escopo é o tipado por município da decisão 45 (`municipality_ibge`; NULL = escopo geral do tenant) e o SQL pousa no estado corrigido pelo ciclo R8-C (idempotência de reaplicação provada em PostgreSQL descartável; SHA-256 `bca7147346dacb21d7023ae2fc141efdacbec91cdb5b7696d961fbf592012cde`). A numeração Flyway global do SQL pendente fica para a integração, como pendência nomeada, no padrão dos precedentes do repo. Pouso executado na cadeia autorizada pelo dono em 09/11/2026 às 23:34, a seguir à Camada 0 (ADR-0048).

## 09/11/2026 — Tarifário Camada 2 — Maestro de composição da conta (billing.composition) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Pousam os 21 ficheiros novos da Camada 2 no módulo `ziipsan-billing`: o maestro de composição (`BillComposer`, `BillComposition`, `BillChargeLine`, calculadoras por modelo — progressivo, percentual derivado, fixo por unidade, preço de catálogo, percentual sobre base e valor externo —, contexto e parâmetros de cálculo, adaptador para o motor vigente e o snapshot documental da conta), o SQL pendente `CAMADA-2__tariff_municipality_scope.sql` (escopo municipal das tarifas, decisão 45) e os 5 testes da camada, incluindo a prova diferencial contra o motor vigente. `TariffEngine` e `TariffSchedule` NÃO são tocados por este lote — o maestro compõe ao lado do motor, e a equivalência é provada pela harness diferencial da própria camada. O SQL pousa no estado inalterado pelo ciclo R8-C (SHA-256 `72fd73f7597727f578050ec0eaa77172f6a1b3d1a8c7106be6d3a915b56e7afc`; a guarda de idempotência dele foi mantida como estava, por decisão registada nesse ciclo). Pouso executado na cadeia autorizada pelo dono em 09/11/2026 às 23:34, a seguir às Camadas 0 e 1.

## 09/11/2026 — Tarifário Camada 3 — Convênios e co-billing (billing.cobill) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Pousam os 9 ficheiros novos da Camada 3 — o co-billing de convênios no `ziipsan-billing` (`CobillServiceKind`, `CobillServiceKindAdapter`, `CobillExternalValueProvider`, `CobillChargeAccountingMapping` e 3 testes) e a exclusão de terceiros na NFAGe no `ziipsan-fiscal` (`NfageThirdPartyExclusion` e o seu teste) — e 1 alteração aditiva: o enum `AccountingEventKey` (módulo `ziipsan-accounting`) ganha, no fim, as 3 constantes `COBILL_ISSUED`, `COBILL_RECEIVED` e `COBILL_TRANSFERRED`, sem nenhuma outra linha alterada (diff conferido no acto contra a base viva). Nenhum ficheiro da ED-25 é tocado por este lote. Pouso executado na cadeia autorizada pelo dono em 09/11/2026 às 23:34, a seguir às Camadas 0, 1 e 2.

## 09/11/2026 — Tarifário Camada 4 — Campo (billing.field) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Pousam os 5 ficheiros da Camada 4 no módulo `ziipsan-billing` (`FieldCalculablePolicy`, `FieldChargeResolver`, `FieldSnapshotProjector` e os testes do resolvedor e da equivalência do projector). Por força do lote de correcção R9-B, o `FieldChargeResolver` e o seu teste pousam já nas versões corrigidas (SHA-256 `faa28997…` e `e14f0329…`), verificadas no acto contra as bases da entrega da camada (`29057da8…` e `e3d0d869…`); o teste de equivalência do projector pousa intocado (`ed74b1e3…`). A decisão C4-01 (forma da prova INV-C2-11) NÃO é condição deste pouso e segue a aguardar decisão do dono. Pouso conjunto com o MM-09 e o fecho do R9-B, na cadeia autorizada pelo dono em 09/11/2026 às 23:34.

## 09/11/2026 — Multimunicípio MM-09 — Perfil integrado e consolidação oficial — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Pousam os 4 ficheiros do MM-09 no módulo `ziipsan-billing` (`OfficialConsolidation`, `MunicipalityReportSelection` e os 2 testes). Por força do lote de correcção R9-B, o `MunicipalityReportSelection` pousa já na versão corrigida (SHA-256 `35a0e150…`), verificada no acto contra a base da entrega do bloco (`69a0bba1…`); a consolidação oficial e os testes pousam intocados (`8a2c61ba…`, `5c79cf4d…`, `63101ca2…`). Com este pouso, o bloco multimunicípio fica pousado na íntegra excepto o MM-05, que continua bloqueado (pendência P-MM05-R8C-01). Pouso conjunto com a Camada 4 e o fecho do R9-B, na cadeia autorizada pelo dono em 09/11/2026 às 23:34.

## 09/11/2026 — Lotes R9-B e R10-B — Correcções à Camada 4 e ao MM-09 aplicadas no pouso; errata R10-B-01 registada — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

O consolidado do lote R9-B pousa em `correcoes-r9-b/RELATORIO-CONSOLIDADO-R9-B.md` (SHA-256 `b873612c…`, 82 linhas — guarda de rebase/hash conferida no acto, sem divergência), acompanhado da errata `ERRATA-R10-B-01-2026-11-09.md` (SHA-256 `5b8c0812…`), que o acompanha sem o reescrever. As 3 cópias corrigidas do R9-B foram as versões efectivamente pousadas nos verbetes da Camada 4 e do MM-09 deste mesmo commit. O R9-B continua APROVADO E CONFIRMADO.

## 10/11/2026 — ED-102 — Obras e convênios de investimento, Transferegov e afins (recorte de integração) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **13 ficheiros novos** no módulo `ziipsan-integration` — 8 classes Java em `br.com.ziipsan.integration.works` (`FundingAgreement`, `GisProjectPort`, `Measurement`, `Schedule`, `WorksAgreementService`, `AccountabilityService`, `TransferegovLayoutMapping`, `WorksErpIntegration`), o SQL pendente `ED-102__works_funding_agreements.sql` (4 tabelas) em `src/main/resources/db/pendente/` **com o nome original, sem numeração Flyway** (a atribuição do número global é a pendência nomeada **P-ED102-04**, no padrão dos SQL tarifário/multimunicípio já pousados) e 4 ficheiros de teste — + este verbete sobre o blob vivo, com o cabeçalho actualizado para 10/11/2026. **Âmbito:** o recorte de integração da ED-102 libertado pela **decisão 54 do dono** (âmbito completo): C1 convênios e repasses federais/estaduais ligados ao GIS (a ED-53 é consumida por porta própria, `GisProjectPort` — zero imports de tipos do GIS), C2 medição com travas de consistência, C3 cronograma físico-financeiro, C4 dados de prestação de contas ao Transferegov e C5 ponto de integração com o ERP de obras (padrão da ED-62). **Transferegov [SEM FONTE]:** o leiaute oficial vigente não foi obtido; o código **recusa** `officialLayout=true` (pendência **P-ED102-01**) e nenhum campo oficial ou valor legal foi inventado. Evidência: harness local — compilação de produção e de testes com exit 0 e JUnit **21/21** (11+7+3 por classe) lidos dos logs integrais; reproduções independentes do revisor e do confirmador, ambas 21/21 fora do pacote. Cadeia (retomada após corte de runtime, com a produção herdada verificada ficheiro a ficheiro; único defeito encontrado foi documental — um hash malformado na tabela original do relatório, corrigido por adendo datado, sem o ficheiro alguma vez divergir): produção (sessão `33e17da8`) → completamento (sessão `4e7f8af0`) → revisão **APROVADO COM RESSALVAS** (sessão `d795b95f`; 7 achados, nenhum grave) → confirmação **CONFIRMADO COM RESSALVAS** (sessão `ea06b48c`; 0 divergências) → portão mecânico **APROVADO, exit 0, 6/6 regras OK** (coordenador `b0f34e44`; o item (3) do protocolo — rebase — é verificado neste ato). Rebase verificado no ato (HEAD `1d7550536a721eecaccd1862629bba969c0c6f27`; os 13 destinos ausentes na main; hashes locais 13/13 contra a tabela do `LANDING.md` do pacote; diário vivo blob `8a11fb5f`) → verificação pós-pouso por hash registada no artefacto local `ed-102/VERIFICACAO-POS-POUSO-ED-102.md` (não pousa). **Pendências que sobrevivem ao pouso:** **P-ED102-01** (leiaute oficial do Transferegov [SEM FONTE]), **P-ED102-02** (submissão real ao Transferegov — acto externo futuro), **P-ED102-03** (ERP de obras concreto por tenant; absorve o achado 4 da revisão), **P-ED102-04** (fiação JDBC/JPA das 4 tabelas com numeração Flyway, beans Spring e adaptador real da `GisProjectPort` sobre a ED-53; absorve os achados 3 e 5 da revisão) e **P-ED102-05** (telas — front estacionado, decisão 57).

## 10/11/2026 — ED-68 — Cobrança por terceiros (cessão de cobrança a empresa terceira) — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **23 ficheiros novos** no módulo `ziipsan-collection` — 14 classes Java em `br.com.ziipsan.commercial.collection.dunning.thirdparty` (contrato com janela e percentual, cessão, payload mínimo, remessa/retorno, atribuição de pagamento, remuneração e relatório por empresa), a migration `V106__dunning_third_party_collection.sql` (5 tabelas) em `src/main/resources/db/pendente/` — número **V106 verificado livre no acto** (lista de publicadas relida ao vivo: 118 entradas, última V105, V104 reservada ao contract da V103; a lista **não** é alterada por este pouso — a inscrição faz-se na promoção da migration) — e 8 ficheiros de teste — + este verbete sobre o blob vivo, em append puro. **Âmbito:** a onda completa da ED-68 libertada pela **decisão 53 do dono**, decomposta na estimativa prévia em E1–E12 e executada 12/12: contrato com janela e percentual do contrato (sem defaults, ADR-0038), gate do contrato de operador LGPD antes do primeiro envio, empresa como empreiteira da ED-37 por porta (sem cadastro paralelo), selecção por critério explícito, payload mínimo com whitelist de 5 campos provada por reflexão, remessa/retorno de ida e volta, atribuição de pagamento só dentro da janela e com acordo válido da P4.1, retirada terminal pelas 3 causas, remuneração percentual pelo evento de pagamento idempotente e relatório por empresa. Evidência: placar **53/53** lido dos logs integrais (49/49 na produção; +4 após a correcção dos 3 achados da revisão, entre eles o moderado R-ED68-01 — a data do pagamento, `paid_on`, passou a ser persistida pela V106), com reexecuções próprias do revisor (49/49) e da re-revisão (53/53) e confirmação por hashes 23/23. Cadeia (6 sessões distintas): produção (sessão `eb6509a7`) → revisão **APROVADO COM RESSALVAS** (sessão `378e96f0`; 0 graves, 1 moderado, 2 menores) → correcção 3/3 (sessão `4791a04b`) → re-revisão **APROVADO COM RESSALVAS**, só documental (sessão `ef6c354e`) → confirmação **CONFIRMADO** (sessão `e146d1e4`; 0 divergências) → portão **APROVADO em substância** (coordenador `e5fb0b04`; exit mecânico 1 só pela Regra 3 — falso positivo demonstrado, instância (xii) das limitações do extractor; registo no pacote). Uma gralha de transcrição de 1 dígito num hash do relatório foi corrigida por ERRATA no pacote, com o ficheiro intacto. Rebase verificado no ato (HEAD `ab131d7262105b0cefedc87264e5e60ee28ca868`; os 23 destinos ausentes na main; hashes locais 23/23 contra a tabela do `LANDING.md` do pacote; diário vivo blob `b74a2feb`) → verificação pós-pouso por hash registada no artefacto local `ed-68/VERIFICACAO-POS-POUSO-ED-68.md` (não pousa). **Pendências que sobrevivem ao pouso:** **P-ED68-1** (fiação: adapter JDBC do store sobre as tabelas da V106, adapters reais das portas, endpoints REST e invocação operacional da varredura de fim de janela), **P-ED68-2** (transmissão real da remessa/retorno a uma empresa real — acto de integração) e **P-ED68-4** (o SQL não foi aplicado em PostgreSQL nesta VM; a aplicação real corre na integração/CI).

## 10/11/2026 — LC.15 — Instância dedicada na AWS — pousado pelo conector (16 ficheiros; o workflow entra pela rota do PC do dono) — (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único pelo conector: **16 ficheiros** — o env Terraform `infra/aws/envs/dedicado/` completo (5 ficheiros **alterados** sobre as bases vivas da LC.2: provider com `assume_role` para a conta dedicada e `check` de ativação, variáveis com a conta/pilha/TR-104 e `tenant_db_mode` fechado em `schema-per-tenant`, outputs do inventário RNF-44 e exemplos com valores `[A DEFINIR]`; 6 **novos**: `stack.tf` com worker e Keycloak sobre a mesma imagem e cluster, `database.tf` com o banco exclusivo via módulos da LC.3/LC.4, `access.tf` + política canónica + papel do realm do TR-104 — papel só-leitura com MFA/ExternalId e `Deny` explícito de escrita — e o `README.md` do ambiente), a migration core **`V13_1__tenant_dedicated_instance_account.sql`** com o seu teste de guardas (a parte "conta da instância" que o cabeçalho da V13 publicada reserva expressamente à LC.15) e os 3 documentos de operação em `docs/operacao/` (ativação, TR-104 e mapa de aceite A1–A11) — + este verbete sobre o blob vivo, em append puro. **Âmbito:** a execução integral da LC.15 libertada pela **decisão 52 do dono** (mesmo sem contrato de cliente): instância dedicada completa por código e documento — **o pouso não ativa instância nenhuma**; a ativação viva é acto da contratação (decisão 41). **O 17.º ficheiro NÃO está neste commit:** `.github/workflows/deploy-infra.yml` **não viaja pelo conector GitHub** (403 "Resource not accessible by integration", provado 3× na LC.2) — entra pelo **PC do dono**, como o workflow da LC.2; hash esperado do ficheiro pousado: SHA-256 `46d7c7ed…` (o da entrega); até lá, o pipeline vivo permanece o da LC.2 e nenhum apply corre para o `dedicado`. Evidência: harness estrutural **52/52** lido do log integral (37 herdadas + 15 da completação; saída reproduzível byte a byte), com controlos negativos apanhados pelos três papéis (34/37 arquivado; revisor 48/52; confirmador 47/52); `terraform validate/fmt` reais **não executados** nesta VM (binário ausente — pendência **P-LC15-05**; a semântica fica para o job `validate` do pipeline). Cadeia (5 sessões distintas): produção (sessão `b3a03bf9`) → completação (sessão `f01e02ba`; 0 alterações à entrega) → revisão **APROVADO COM RESSALVAS** (sessão `72ed0f5c`; 0 graves, 0 moderados, 2 menores) → confirmação **CONFIRMADO COM RESSALVAS** (sessão `51f3aa74`; 0 divergências) → portão **APROVADO em substância** (coordenador `09d53c98`; exit mecânico 1 só pela Regra 6 — falso positivo demonstrado, nova instância (xii) das limitações do portão: a gramática da sequência publicada do tenant aplicada a um ficheiro do namespace core; registo no pacote). Rebase verificado no ato (HEAD `38f5497099842c12bc2c682c2deec410b6111f26`; as bases dos 6 ficheiros alterados intactas desde a produção — diff vazio entre a base de produção e a main corrente; os 11 destinos novos ausentes na main; hashes locais 17/17 contra a tabela do relatório do pacote; diário vivo blob `02b83bb2`) → verificação pós-pouso por hash registada no artefacto local `lc.15/VERIFICACAO-POS-POUSO-LC.15.md` (não pousa), com a linha do workflow marcada **pendente-da-rota-PC**. **Pendências que sobrevivem ao pouso:** **P-LC15-01** (conta AWS dedicada real — só na contratação), **P-LC15-02** (ativação sob contrato de cliente concreto [A DEFINIR]), **P-LC15-03** (metade viva do teste TR-104 — escrita recusada + evento CloudTrail; não executada), **P-LC15-05** (`terraform validate/fmt`), **P-LC15-08** e **P-LC15-09** (julgadas na revisão: menções a `isolation_mode` só em negação/comentário/teste; V13_1 + teste dentro do aceite) e **P-REV-LC15-02** (o placar da CI core pós-pouso é a primeira execução real do teste Java da V13_1).

## 10/11/2026 — P4.6 — Schema compartilhado e banco dedicado — pousado (commit único deste lote — o SHA do próprio commit que introduz este verbete não pode constar dele e é registado no artefacto local de verificação pós-pouso)

Lote único: **19 ficheiros** — 7 classes novas em `br.com.ziipsan.platform.tenant` no módulo `ziipsan-platform` (`IsolationMode`, `TenantResolution`, `ClusterPoolRouter`, `DedicatedTenantProfile`, `SharedTenantLifecycle`, `TenantModeMigrationPlan`, `ClusterRolloutPlanner`), 2 alterados sobre as bases vivas (`TenantCatalog`, +29/−0; `TenantProvisioningService`), a migration core **`V15__tenant_catalog_shared_schema_unique.sql`** (índice único parcial que exclui as linhas SHARED; número **V15 verificado livre no acto** na série core V1–V14_1; nenhuma migration publicada é reescrita e a lista `published-tenant-migrations.txt` não é tocada, por só cobrir migrations de tenant), 8 ficheiros de teste (incl. `TenantProvisioningGuardsTest` e o IT `RlsIsolationThreeModesIT`) e o documento de operação `docs/operacao/P4.6-MUDANCA-DE-MODO-ENSAIO.md` — + este verbete sobre o blob vivo, em append puro. **Âmbito:** a P4.6 libertada pela **decisão 56 do dono** (a procura registada pelo próprio acto do dono): modos de isolamento de tenant com resolução de catálogo (`TenantCatalog`/`TenantResolution`), roteamento por cluster (`ClusterPoolRouter`), rollout (`ClusterRolloutPlanner`) e plano de migração de modo (`TenantModeMigrationPlan`). A revisão apanhou um **grave real**: o UNIQUE da core V7 tornava o modo SHARED impossível; a correcção criou a core V15 (índice parcial) sem reescrever a V7. Evidência: IT `RlsIsolationThreeModesIT` **7/7** em banco real (PG16+PostGIS, com a V15 aplicada por Flyway) sobre a main viva `b332192d` + entrega, lido do log integral arquivado no pacote; unitários do pacote tenant **29/29** (22 originais + 7 novos); regressão vizinha **29/29**. Cadeia (6 sessões distintas): produção (sessão `b843b2a8`; 17 ficheiros) → revisão **REPROVADO** (sessão `bd1d1917`; 2 graves, 3 moderados, 4 menores) → correcção 9/9 (sessão `05c3dce8`; entrega final de 19 ficheiros) → reapreciação **APROVADO COM RESSALVAS** (sessão `c6747113`) → confirmação **CONFIRMADO COM RESSALVAS** (sessão `e6e37836`; hashes 19/19) → portão **APROVADO em substância** (coordenador `8c14060b`; a Regra 6 sobre a core V15 é falso positivo demonstrado — a gramática V≥100 da série *tenant* aplicada à série *core*, mesma classe da instância (v) registada na Rodada 12; registo no pacote). Rebase verificado no ato (HEAD `6e01ef58cb56699943a78f9156bea196efeb272f`; os 17 destinos novos ausentes na main; bases dos 2 alterados idênticas às bases da correcção — blobs `08401be6…` e `f07ef2b9…`, sem commits intermédios sobre `platform/` ou `shared/` desde `b332192d`; V15 livre na série core; hashes locais 19/19 contra a tabela final do pacote; diário vivo blob `c2ca7315`) → verificação pós-pouso por hash registada no artefacto local `p4.6/VERIFICACAO-POS-POUSO-P4.6.md` (não pousa). **Pendências que sobrevivem ao pouso:** **P-P46-02** (reactor completo vermelho no fiscal — import de `billing.composition` sem dependência declarada, introduzido pela C4; declará-la fecha um ciclo de módulos; frente dona/CI — a CI total depende dela), **P-P46-03** (ensaios reais E1–E4 e primeira mudança de modo sobre tenants reais — acto operacional futuro), **P-P46-04** (prova do modo DEDICATED com segundo cluster físico — `[SEM FONTE]` de ambiente nesta VM), **P-P46-05** (re-descrever o log do reactor fiscal — registo), **P-P46-06** (arquivar os dumps `flyway_schema_history`/`pg_indexes` da prova da V15 — registo), **P-P46-07** (fechar a fuga de conexão no caminho de erro de `acquireClusterProvisioningLock` — micro-correcção) e **P-C46-01/P-C46-02** (persistência/consumo do perfil dedicado completo; wiring de produção de `ClusterPoolRouter`/`ClusterRolloutPlanner` quando existir o primeiro cluster além do `default`). Fechada neste ciclo: **P-P46-01** (o IT correu: 7/7 em banco real).

## 10/11/2026 — Mobile (app de campo) — Persistência SQLCipher (P-04) e dados de emissão por tarefa (P-03) — pousado (commit `b38ec85d`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote único: **15 ficheiros** — Frente A (P-04/RG2-C-P4): implementação real do `LocalStore` sobre SQLCipher no app de campo (`FieldDatabaseSupport`/`FieldDatabasePlugin` Kotlin novos, ponte TS `fieldDatabase.ts` e `sqlCipherLocalStore.ts` com testes, registo do plugin na `MainActivity` e fecho da base no wipe do `FieldSecurityPlugin`; desenho da chave: passphrase aleatória de 32 bytes embrulhada pela chave não-extraível do Keystore, desembrulhada só em memória no nativo — nunca atravessa a ponte JS) e o teste instrumentado `FieldDatabaseInstrumentedTest` (prova executável na CI Android — pendência **P-FA-01**); Frente B (P-03/RG2-C-P2): bloco opcional `emission` por tarefa no `RoutePackage` TS (espelho campo a campo do `FieldBillInput`/`RouteTask` Java, nenhum campo inventado) + resolvedor `routePackageEmission.ts`, e no servidor o `RoutePackageAssembler` (metering, entrada tipada explícita, padrão `FieldSnapshotProjector`) com o DTO de entrega `RoutePackageDeliveryDto` e a dependência `ziipsan-billing-rules` declarada no pom do metering. **Âmbito:** a **decisão 58 do dono** (a frente Mobile começa por fechar P-04+P-03; a decisão 57 continua a valer para telas). A correção de terminologia do dono de 10/11/2026 — "não é download, é **ENVIO** do servidor ao aparelho" — está aplicada nos artefactos novos e na única linha substituída do pouso (o comentário de cabeçalho do `routePackage.ts`); sem bloco de emissão, a emissão responde UNAVAILABLE, como hoje, e a **P-MOB-INT-01** (mecanismo de envio servidor → aparelho + fornecedor vivo dos `FieldBillInput` por rota) **não** é construída neste lote — segue pendência declarada. Evidência: TS combinado sobre a main viva **179/179** (166 base + 5 + 8) e Java dirigido **12/12** BUILD SUCCESS, placares lidos dos logs integrais; calculador da ED-19 intocado (SHA-256 `64374640…f60` conferido no overlay e na main). Cadeia (sessões distintas, mesa paralela do Mobile): Frente A — produção (`29e15571`) → revisão **APROVADO COM RESSALVAS** (`9034786c`; 3 menores de registo) → confirmação **CONFIRMADO COM RESSALVAS** (`cc9d2ce1`); Frente B — produção (`4a5ff873`) → revisão **APROVADO COM RESSALVAS** (`09136bb0`; 2 ressalvas documentais) → correção documental (`c20e3ab8`) → confirmação **CONFIRMADO** (`db1ac087`); evidência de testes e rebase do portão por sessão distinta (`8ca486e6`); portão mecânico **APROVADO, exit 0, nas duas frentes** (após adendos datados de identificação de autoria nos cabeçalhos e os LANDINGs do lote). Rebase verificado no ato (intersecção vazia entre os 54 ficheiros que a main moveu desde a base de produção `1d755053` e os 15 caminhos do lote; diff do pouso: 11 novos + 4 modificados, +1.520/−1) → verificação pós-pouso por hash: **15/15** conferidos na main (commit `b38ec85d`, pousado sobre `a3022adb` da P4.6, que pousou entretanto sem tocar nenhum caminho deste lote). **Pendências que sobrevivem ao pouso:** **P-FA-01** (compilar o Kotlin e correr o instrumentado na CI Android — sem SDK Android nesta VM), **P-FA-02** (fiação da casca), **P-FA-03** (`openEncryptedDatabase` legado), **P-MOB-INT-01** (mecanismo de envio + fornecedor vivo), **P-FB-01** (falhas pré-existentes do metering na main: `MigrationConsistencyTest` e `MeterInstallationServiceTest` — frente dona do metering) e a frente seguinte desta mesa, **P-MOB-GPS-01** (decisão 59 do dono: controle em tempo real via GPS com tela de acompanhamento, sobre a governança já entregue pela ED-20).

---

## 10/11/2026 — Mobile (app de campo) — Controle em tempo real via GPS (P-MOB-GPS-01, decisão 59) — pousado (commit `ecce890a`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Mesa paralela do Mobile (chat `922480b5`), sob a esteira completa (PROTOCOLO-ESTEIRA + portão mecânico). A ED-20 já tinha entregue a governança (portão `Tracking.ingest` — aviso ao empregado, só na jornada, trava — e o dado do painel `GpsPanel`), mas **nada da cadeia do tempo real existia**: nenhuma implementação de `TrackStore`, nenhum chamador de produção do `GpsPanel`, nenhuma coleta no aparelho. Este lote fecha a cadeia do mecanismo: **no servidor** (metering, 8 ficheiros + 2 de testes), o serviço de ingestão recebe lotes de pontos e submete **cada ponto** ao `Tracking.ingest` antes de o persistir — lote nunca tudo-ou-nada, com **recibo por ponto** (aceite, ou recusado com o motivo nomeado do próprio núcleo); `InMemoryTrackStore` entregue como implementação de ensaio, **sem bean Spring**, para nunca virar persistência de produção por acidente; o serviço de painel monta a trilha e o resumo **depois** do gate de chefia do próprio núcleo; controlador em `field/web/` (`POST /api/v1/field/track` com o portão do aparelho `X-Device-Id`, como no `/sync`; `GET /track/panel`). **No aparelho** (13 ficheiros): porta `TrackPositionSource` nova (sem reutilizar o GNSS RTK de cadastro da ED-66), motor de coleta com entradas todas explícitas e obrigatórias — **sem aviso registado não arranca, nem subscreve a fonte de GPS**; fora da jornada descarta cada ponto pelo seu instante e encerra no fim da jornada; fila de pontos com porta de persistência (implementação em memória declarada) e remetente em lotes com o mesmo contrato de recibos (aceite sai; recusado sai **com o motivo registado**; falha de rede mantém; recibo inconsistente lança `TRACK_RECEIPT_MISMATCH` e não toca na fila); ponte `fieldLocation.ts` + `FieldLocationPlugin.kt` (LocationManager só em primeiro plano; pára em `onStop`/`onDestroy`); `AndroidManifest` ganha **só** FINE/COARSE_LOCATION — sem background, sem serviço.

**Esteira:** Frente GPS-A — REVISAO **APROVADO COM RESSALVAS** (revisor `a6689a40`: 29/29 com harness próprio e **controlo negativo por mutação** — remover o `ingest` fez falhar exactamente os 4 testes de recusa; R-GPSA-01/02/03, menores) → CONFIRMACAO **CONFIRMADO COM RESSALVAS** (confirmador `c7f1e1d1`: 29/29 próprios, hashes 10/10). Frente GPS-B — REVISAO **APROVADO COM RESSALVAS** (revisor `649c239a`: baseline 179/179 → 200/200, typecheck sem erro de tipos novo; R-GPSB-01..04, menores — R-GPSB-01: o recibo TS e o `FieldTrackReceipt` Java têm a mesma semântica e formas diferentes; mapeamento posicional registado para o adaptador HTTP) → CONFIRMACAO **CONFIRMADO COM RESSALVAS** (confirmador `deb24c9f`: 200/200 próprios, invariantes amostrados no código, R-GPSB-01 confirmada como diferença de forma). Portão: evidência mecânica por sessão distinta (`363bf462`) — a main não saiu da base `d3a3d43f`; TS combinado **200/200**, Java dirigido **29/29 BUILD SUCCESS**; diff do pouso **23 ficheiros, +2.230/−0 linhas — zero linhas removidas ou alteradas em toda a main**; `portao-deploy.py` **APROVADO, exit 0, nas duas frentes** (checklist C1–C8 no `LANDING.md` do lote; C6 não se aplica — sem migrations). Guarda ED-19: calculador `fieldEmission.ts` **intocado** (`643746402546885bcac3516924b0766daaf48b6d2daa708aef0105945e9f7b60`).

**Pouso:** commit único **`ecce890a`** pelo conector GitHub (23 ficheiros). Verificação pós-pouso por hash: **23/23 remotos = entrega**.

**Fronteiras declaradas (pendências nomeadas, nada simulado):** **P-MOB-GPS-02** fontes vivas de jornada/aviso (portas e contextos entregues, nenhuma fonte presumida); **P-MOB-GPS-03** coleta com o app fechado (foreground service; exige prova em aparelho); **P-MOB-GPS-04** persistência cifrada da fila de trilha; **P-MOB-GPS-05** `TrackStore` JDBC sobre `field_track_points` + numeração Flyway do SQL pendente da ED-20; **P-MOB-GPS-06** a tela de acompanhamento ao vivo (decisão 57 — momento final, pós-CI verde; entregue o mecanismo e o dado); **P-MOB-GPS-07** fiação Spring de produção e prova do Kotlin na CI Android (precedente P-FA-01 — nesta VM não há SDK Android; o Kotlin pousa como código revisto por leitura, declarado); **P-MOB-GPS-08** testes web do controlador, com a caracterização do 500 de coordenadas fora da faixa (R-GPSA-03) e o mapeamento do recibo (R-GPSB-01); **P-MOB-GPS-09** estreitar o `catch` da ingestão junto à P-MOB-GPS-05 (falha transitória de escrita ≠ recusa — R-GPSA-01). Herdada da ED-20: agendamento do expurgo por retenção (a retenção já viaja no contexto entregue).

## 10/11/2026 — ED-104 — Trilha GRS sob demanda: MTR, CDF e DMR e operação de resíduos sólidos — pousado (commits `1c7ac4fa`, `f21fb4ba` e `9224daaf`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote da ED-104 pelo conector: **39 ficheiros** no módulo `grs/ziipsan-mtr` — 11 **alterados** sobre as bases vivas (entre eles o `pom.xml`, que ganha a dependência do Apache PDFBox 3.0.3, e o `MtrPdfGenerator`, substituído pelo gerador por biblioteca) e 28 **novos** (pacotes `dmr` e `pgrs`, portas de submissão IMA/SINIR, operação de coleta, emissão de MTR a partir do lodo da ED-105 e os testes novos), incluindo o SQL `ED-104__grs_trail.sql` em `src/main/resources/db/pendente/` **com o nome original, sem numeração Flyway** (a numeração global é a pendência **P-ED104-05**). **Os 2 ficheiros de fonte TTF do E11 (DejaVuSans e DejaVuSans-Bold, ~760 KB e ~709 KB) NÃO estão nestes commits:** são binários e o conector GitHub não transporta conteúdo binário — pousam pela **rota do PC do dono**, como o workflow da LC.2/LC.15 (ficheiros preparados no pacote local, `ed-104/VERIFICACAO-POS-POUSO-ED-104.md`); até lá, os testes do PDF ficam a aguardar as fontes na main. **Âmbito:** a trilha GRS completa libertada pela **decisão 60 do dono** — a procura registada pelo próprio acto do dono, cumprido o gatilho RAS-17: E1–E13 da estimativa prévia, **12/13 feitos e o E10 PARCIAL por `[SEM FONTE]`** (as fórmulas oficiais dos indicadores das NR ANA 7/2024 e 14/2025 não foram lidas — Resoluções ANA 187/2024 e 276/2025; pendência **P-ED104-07**). Nenhuma submissão real ao SINIR/IMA foi simulada e nenhum número oficial de MTR/CDF foi atribuído: o número interno nasce PROVISÓRIO e `officialMtrNumber` fica nulo até um recibo real. As migrations publicadas V2_4/V2_6/V2_8 permanecem byte-idênticas (prova por hash no acto: `e5abf2ad…`, `d9d9b324…`, `44f0210c…`) e a lista de publicadas não é tocada. Evidência: suíte do módulo **114/114** lida do log integral (base viva 76/76 + 38 testes novos, nenhum teste vivo apagado), BUILD SUCCESS. Cadeia (sessões distintas): estimativa do coordenador `6bba7938` (ANTES de produzir) → produção (sessão `6d762d43`) → revisão **APROVADO COM RESSALVAS** (sessão `fa5df3ed`; 0 graves, 0 moderados, 5 menores) → confirmação **CONFIRMADO COM RESSALVAS** (sessão `8502ff74`; 0 divergências, hashes 41/41) → portão mecânico **APROVADO, exit 0, 6/6 regras OK, sem falsos positivos** (registo no pacote). Rebase verificado no ato (HEAD `cc2e3766eead14589832b9ec28859d1fe732e50b`; o módulo `grs/ziipsan-mtr` intocado na main desde a base de produção `1d755053` — diff vazio; os 28 destinos novos de texto ausentes na main; hashes locais 41/41 contra a tabela do relatório do pacote; diário vivo blob `fca8c844`) → verificação pós-pouso por hash registada no artefacto local `ed-104/VERIFICACAO-POS-POUSO-ED-104.md` (não pousa). **Pendências que sobrevivem ao pouso:** **P-ED104-01** (leiautes oficiais de Web Service do SINIR e do IMA onde não confirmados `[SEM FONTE]`; submissões reais são actos externos), **P-ED104-02** (carga integral da Lista Brasileira de Resíduos Sólidos — 878 códigos do XLS oficial do IBAMA, IN 13/2012), **P-ED104-03** (conteúdo das classes da série NBR 10004-1/-2:2024 NÃO CONFIRMADO), **P-ED104-04** (enquadramento jurídico por tenant — junta-se ao gatilho jurídico do fim da execução), **P-ED104-05** (fiação de integração: adapters JDBC/JPA das tabelas novas, endpoints REST, agendamento trimestral da DMR, adapters reais das portas IMA/SINIR, aplicação do SQL pendente em PostgreSQL e numeração global — com o registo formal dos achados menores da revisão R-01 a R-04 e o endurecimento opcional R-05), **P-ED104-06** (telas da trilha GRS e da operação de coleta — front estacionado, decisão 57) e **P-ED104-07** (fórmulas oficiais dos indicadores ANA `[SEM FONTE]`).

## 10/11/2026 — ED-103 — Antecipação de recebíveis e conta vinculada: a MECÂNICA — pousado (commit `b4601a83`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote único: **18 ficheiros novos** no módulo `ziipsan-collection` — 11 classes no pacote novo `br.com.ziipsan.commercial.collection.cession`, o SQL `ED-103__receivables_cession_linked_account.sql` (5 tabelas) em `src/main/resources/db/pendente/` **com o nome original, sem número Flyway** (a numeração global é a pendência **P-ED103-INT-03**; a lista de migrations publicadas **não** é alterada por este pouso) e 6 ficheiros de teste — + este verbete sobre o blob vivo, em append puro. A entrega é **100% aditiva**: zero ficheiros vivos alterados, zero poms, nenhuma aresta nova entre módulos. **Âmbito:** a execução da ED-103 libertada pela **decisão 62 do dono** — adoptar a prática do mercado: o sistema trata **só da MECÂNICA** (relatório da carteira com aging e adimplência a partir das contas a receber da ED-32, servido por porta própria do módulo; minimização de dados pessoais por desenho — a linha da carteira tem exactamente 6 componentes, sem nome, CPF/CNPJ nem endereço — com trilha de auditoria de cada exportação; ciclo de vida do recebível DISPONÍVEL → RESERVADO → CEDIDO → LIQUIDADO/LIBERTADO com **trava contra dupla cessão** provada por teste de concorrência de 16 threads sobre o mesmo recebível — exactamente 1 sucesso e 15 recusas `DOUBLE_CESSION_BLOCKED`; e reconciliação de baixas com os movimentos da conta vinculada, com divergências tipificadas e **nenhum ajuste silencioso** — casar não liquida o registo); a **LOGÍSTICA** — forma jurídica (FIDC, cessão fiduciária, conta vinculada), autorizações, limites da LC 101/2000 e a operação em si — é do cliente e fica **fora do software**: nenhuma conclusão jurídica foi codificada, nenhuma submissão real a banco/fundo foi feita ou simulada e nenhum leiaute oficial foi presumido. **O dossiê da consulta jurídica NÃO pousa** (carta-consulta, 24 quesitos, sumário técnico e matriz quesito→decisão — instrumento do cliente, a entregar ao jurista por acto externo; hashes 4/4 byte-idênticos antes e depois da produção); a decisão 62 redefine o gatilho da decisão 55: a execução já não espera o parecer. Evidência: suíte nova **20/20 BUILD SUCCESS** lida dos logs integrais, reexecutada de forma independente pelo revisor e pelo confirmador (20/20; concorrência 6/6 em reexecuções isoladas), com 3 controlos negativos sensíveis do revisor (trava removida, aging adulterado e auditoria não gravada fazem falhar exactamente os testes certos); regressão do módulo **785 testes** (base 765 + 20 novos) com o conjunto de falhas **idêntico** ao da base (4F/7E pré-existentes, fora deste âmbito) — zero falhas novas. Cadeia (4 sessões distintas): estimativa prévia do coordenador (sessão `1d5acea9`; E1–E7, 17 pontos, gravada ANTES de produzir) → produção (sessão `66676ee1`) → revisão **APROVADO** (sessão `b47e2f7b`; 0 graves, 0 moderados, 3 menores R-01..R-03, nenhum deles condição de pouso) → confirmação **CONFIRMADO** (sessão `a4e20dc2`; 0 divergências, hashes 18/18 do ciclo + 4/4 do dossiê) → portão **APROVADO EM SUBSTÂNCIA** (coordenador `1d5acea9`; exit mecânico 1 só pela Regra 3 — falso positivo demonstrado, instância (xv) das limitações do extractor: os cabeçalhos de autoria de revisor e confirmador existem nas linhas 3 dos artefactos e a gramática do extractor não os lê; registo no pacote, `PORTAO-REGISTRO-EXECUCAO-ED-103.md`). Rebase verificado no ato (HEAD `8adee2d1c2a666c7cbfcbc696ef48cd31b83c90b` — a main não avançou desde a base de produção; os 18 destinos ausentes na main; hashes locais 18/18 contra a tabela do `RELATORIO-EXECUCAO-ED-103.md`; diário vivo blob `9544a361`) → verificação pós-pouso por hash registada no artefacto local `ed-103/VERIFICACAO-POS-POUSO-ED-103.md` (não pousa). **Com este pouso, a main fica 286/286** — todas as tarefas do plano v3.0 executadas e pousadas. **Pendências que sobrevivem ao pouso:** **P-ED103-INT-01** (fiação/adapter real da porta da carteira sobre os recebíveis vivos de billing/collection), **P-ED103-INT-02** (leiautes oficiais de banco/fundo/registradora `[SEM FONTE]` — formatos genéricos declarados até fonte oficial do cliente), **P-ED103-INT-03** (aplicação do SQL pendente em PostgreSQL descartável + numeração Flyway global na integração), **P-ED103-INT-04** (persistência real do `CessionStore` — JDBC/JPA sobre o SQL entregue — e fiação Spring/REST/agendamento) e **P-ED103-TELAS-01** (telas da mecânica — decisão 57, só após a CI total verde).
## 10/11/2026 — Front (tema do front web — molde) — biblioteca de componentes e correcções R2-F6..F9 pousadas (commit `c0d1c6ac`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote único: **18 ficheiros** em `frontend/src/` — biblioteca do molde (tokens e tema por variáveis CSS, 10 componentes `ui`, 2 hooks, `services/api.ts` + `registry/errors.ts`, `utils/cn.ts` e a vitrine `pages/tema/Showcase.tsx`) — dos quais 2 novos (`styles/tokens.css`, `pages/tema/Showcase.tsx`) e 16 sobre as bases vivas verificadas no acto (a `main` não avançou em nenhum destes caminhos desde a base de produção `1d755053` — prova por hash caminho a caminho; zero linhas alheias removidas). Inclui as correcções da fila estacionada **R2-F6..F9** (Field sem `<label htmlFor>` órfão em grupo; precedência `tone ?? variant` do Alert verificada e documentada; botão Fechar do Dialog com `aria-label="Fechar"` e alvo 48px; Icon com aviso em dev e `data-icon-unknown`). **Âmbito:** momento final do front libertado pela **decisão 65 do dono** (que levanta a condição "só após CI verde" da decisão 57); a aplicação do tema às telas é frente separada deste momento. Evidência: compilação **8/8 frentes + vitrine, 0 warnings**; unitários **55/55** (43 base + 12 aditivos); browser **16/16 + 6/6** — reexecutados por correcção, revisão e confirmação (logs integrais no pacote local). Cadeia (sessões distintas): produção (sessão `468fda68`) → correcção R2-F6..F9 (sessão `453dd660`) → revisão **APROVADO** (sessão `ba234999`) → confirmação **CONFIRMADO, 0 divergências, hashes 18/18** (sessão `dfe5e13f`) → portão **APROVADO EM SUBSTÂNCIA** (falso positivo demonstrado da Regra 3 do extractor — instância (xviii); registo `PORTAO-REGISTRO-TEMA.md` no pacote, protocolo §3.2). Rebase verificado no acto (HEAD pré-pouso `376bf6f4`; hashes locais 18/18 contra a secção de congelamento do LANDING — os 5 corrigidos pelos sha256 completos, não pelos prefixos pré-correcção) → verificação pós-pouso por hash registada no artefacto local `tema-front-web/VERIFICACAO-POS-POUSO-TEMA.md` (não pousa). **A prova de CI regista a dependência do reactor** (cura R19-A em curso): `tsc --noEmit` e as suítes vitest não correm nesta VM (P-TEMA-CORR-01) — nenhum verde declarado sobre o que não correu. **Pendências que sobrevivem ao pouso:** P-TEMA-01..12 (entre elas P-TEMA-03 cores semânticas provisórias e P-TEMA-09 auth do api) e P-TEMA-CORR-02 (a vitrine pousada não exercita os casos novos; a prova vive na página de trabalho do pacote). Observação cosmética registada: comentário inline no `Icon.tsx` descreve o inverso da guarda `typeof process` — sem impacto funcional.


## 10/11/2026 — Front (9 temas no código real) — 5 paletas novas e companheiros pousados em lote único (commit `80eef161`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote único: **13 ficheiros** — as 5 paletas novas de `frontend/src/theme/palettes/` (`violet`, `amber`, `executive-navy`, `graphite`, `sanitation-green`; ids por MARCA-E-TEMAS §4.3), os 3 gerados regenerados pelo gerador do projecto (`palettes.generated.ts`, `palettes.css`, `palette-ids.json` com 7 ids), o `contrast.test.ts` (só a asserção da lista de ids 2→7, limiares intactos), o par atómico de CSP (`frontend/index.html` com as 7 paletas + `inline-theme-script.sha256` novo) e os **2 companheiros do §4.7 no mesmo lote** (decisão do coordenador no congelamento, sobre parecer do revisor sustentado pelo confirmador): `UserPreferencesService.java` com `KNOWN_PALETTES` 2→7 — sem ele, a P1.18 rejeitaria as paletas novas com **422** e o `useTheme` engoliria o erro (falha silenciosa de persistência) — e o `docs/arquitetura/MARCA-E-TEMAS.md` §4.2 actualizado "nos dois lugares juntos". Os 9 modelos da decisão 63 lêem-se como **7 paletas × modos claro/escuro** (os Modos Escuro/Claro das provas são o modo pré-existente); o padrão de fábrica mantém-se `aqua-blue` (Azul Água, decisão 63). Ajustes só de luminância (H/S intactos) em 5 variantes claras para o piso WCAG dos testes; âmbar claro chart-2 fica a exactamente 3,00:1 (verde, sem margem — observação do revisor). **Âmbito:** momento final do front (decisão 65). Evidência: vitest real **85/85** (contraste 29 + iconContrast 14 + mapSymbolContrast 42); harness node **500/500, 0 falhas**; gerador com diff vazio; hash do script inline recomputado e conferido; typecheck dos gerados exit 0 — reexecutados por revisor e confirmador (logs integrais no pacote local). Cadeia (sessões distintas): produção (sessão `b7d7ae14`) → revisão **APROVADO COM PENDÊNCIAS** (sessão `d3542ea7`; recálculo independente 10/10 hex→HSL e 50/50 valores §4.2) → confirmação **CONFIRMADO, 0 divergências, hashes 13/13** (sessão `39b6baea`) → portão **APROVADO, exit 0, 6/6 regras OK**. Rebase verificado no acto (HEAD pré-pouso `20eb1531`; a `main` não avançou em nenhum destes 13 caminhos desde a base de produção `1d755053` — prova por hash; zero linhas alheias removidas; hashes locais 13/13 contra a secção de congelamento do LANDING) → verificação pós-pouso por hash registada no artefacto local `conclusao-front/temas-9-codigo/VERIFICACAO-POS-POUSO-TEMAS-9.md` (não pousa). **A prova de CI completa regista a dependência do reactor** (cura R19-A em curso) — nenhum verde declarado sobre o que não correu. **Pendências que sobrevivem ao pouso:** **P-T9-01** — a regra `ALLOWED_VALUES` da migration publicada V9_10 (linha 63) limita `theme.default_palette` a `["aqua-blue", "aqua-green"]`; fixar uma das 5 paletas novas como padrão de tenant via console exige migration NOVA (pacote de backend separado; o `theme.allowed_palettes` não tem esse limite) — **não** é condição deste pouso; P-T9-02..05 (aprovação formal do §4.2 neste portão; Playwright+axe com 14 combinações; suítes de backend na corrida de CI pós-pouso; folha de contactos de ícones opcional).

## 10/11/2026 — Front (portal do consumidor — P3.3/ED-38/ED-39) — telas do portal pousadas (commit `79322234`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote único: **6 ficheiros novos** em `cliente/src/portal/` (`transport.ts` com exactamente os 14 endpoints provados, `models.ts`, `screens.ts`, `boot.ts`, `portal.css` e a suíte `portal.test.ts`) — nenhum ficheiro existente alterado; os 6 destinos estavam ausentes na main. **Âmbito:** construção das telas do portal do consumidor sobre os serviços existentes (antes só núcleo/backend), no momento final do front libertado pela **decisão 65 do dono**. O `boot.ts` importa `../branding/tenantTheme.ts` da ED-39 — fiação conferida no acto: o ficheiro existe na main em `cliente/src/branding/tenantTheme.ts` (nota P-PRINTS-02 resolvida no pouso); a integração do `main.ts` da ED-39 é passo separado, fora deste pacote. Evidência: `node --test "src/portal/portal.test.ts"` = **16/16 PASS, 0 fail, 0 skipped** (13 pré-existentes + 3 novos da correcção R-PORTAL-02..04) — a errata documental **R2-F1** corrigiu o placar declarado de 13/13 para o real 16/16; a suíte foi reexecutada por correcção, errata, reapreciação e confirmação (logs integrais no pacote local). Registo de honestidade: a única linha de teste pré-existente alterada em todo o momento final está neste pacote (`validateYear(0)` → `validateYear(Number.NaN)`), declarada e justificada — codificava a própria regra inventada que a correcção corrigiu, e a cobertura de recusa cresceu. Cadeia (sessões distintas): produção (sessão `aff049d3`) → revisão **APROVADO** (sessão `7105234f`; 7 achados menores) → correcção 7/7 (sessão `904c25c1`) → errata R2-F1 (sessão `029def71`) → reapreciação **APROVADO com pendências nomeadas** (sessão `804b8624`; nenhuma condição de pouso) → confirmação **CONFIRMADO, 0 divergências** (sessão `b935ad0a`) → portão **APROVADO EM SUBSTÂNCIA** (falso positivo demonstrado da Regra 3 do extractor — instância (xvi); registo `PORTAO-REGISTRO-PORTAL.md` no pacote, protocolo §3.2). Rebase verificado no acto (HEAD pré-pouso `9e7deca6`; destinos novos/ausentes; hashes locais 6/6 contra a secção de congelamento do LANDING) → verificação pós-pouso por hash registada no artefacto local `portal-consumidor-telas/VERIFICACAO-POS-POUSO-PORTAL.md` (não pousa). **A prova de CI regista a dependência do reactor** (cura R19-A em curso) — nenhum verde declarado sobre o que não correu. **Pendências que sobrevivem ao pouso:** P-PORTAL-01..07 (entre elas P-PORTAL-01 = tela centralizadora não construída por desenho, sem rota provada; nenhuma é condição de pouso).

## 10/11/2026 — Front (quiosque e painel ED-40) — telas de atendimento pousadas em módulo novo `atendimento/` (commit `d5f5a2f0`; este verbete pousa em commit próprio imediatamente a seguir, sobre o blob vivo, em append puro)

Lote único: **12 ficheiros novos** no módulo novo `atendimento/` na raiz do repo (`index.html`, `package.json`, `tsconfig.json` e, em `src/kiosk/`, `models.ts`, `ports.ts`, `controller.ts`, `screens.ts`, `boot.ts`, `kiosk.css` + 3 ficheiros de teste) — o destino `atendimento/` é o **proposto no LANDING**, executado como proposto no acto do pouso (**P-ED40T-05**, decisão de fiação do pouso); o módulo estava ausente na main e nenhum ficheiro existente foi alterado. O 12.º ficheiro (`screens.test.ts`) foi acrescentado pela correcção, conforme a correcção à lista do LANDING registada no congelamento. Inclui as correcções da fila estacionada **R2-F2..F5**: F2 botão "Cancelar senha" por item da fila de espera (ramo WAITING morto removido); F3 "Ver o painel" tornado FUNCIONAL (SG-626 é tela separada com rota própria; sem gate `kiosk_enabled` em contrato algum); F4 alvo táctil 32px → **48px**; F5 cor fixa `#00548f` → `var(--brand-primary, #333333)` (2 ocorrências). **Âmbito:** telas do quiosque de atendimento e do painel de chamadas sobre o núcleo existente (antes só backend), no momento final do front libertado pela **decisão 65 do dono**. Evidência: suíte **37/37** (models 19/19 + controller 14/14 + screens 4/4; baseline 32/32), controlo negativo exacto nos testes novos — reexecutada por produtor da correcção, revisor e confirmador (logs integrais no pacote local). Cadeia (sessões distintas): produção (sessão `7d49a3aa`) → correcção R2-F2..F5 (sessão `2a4286d4`) → revisão **APROVADO COM PENDÊNCIAS NOMEADAS** (sessão `4d53832f`; nenhuma condição de pouso) → confirmação **CONFIRMADO, 0 divergências, hashes 12/12** (sessão `044d9aab`) → portão **APROVADO EM SUBSTÂNCIA** (falsos positivos demonstrados das Regras 3 e 1 do extractor — instância (xvii); registo `PORTAO-REGISTRO-ED-40.md` no pacote, protocolo §3.2). Rebase verificado no acto (HEAD pré-pouso `5c4a1f97`; destino novo/ausente; hashes locais 12/12 contra a secção de congelamento do LANDING) → verificação pós-pouso por hash registada no artefacto local `ed-40-telas/VERIFICACAO-POS-POUSO-ED-40.md` (não pousa). **A prova de CI regista a dependência do reactor** (cura R19-A em curso) — nenhum verde declarado sobre o que não correu. **Pendências que sobrevivem ao pouso:** P-ED40T-01..06 (rotas HTTP do guichê e da emissão, fiação JPA/sequência/parâmetros, go-live/CI — condicionam go-live e fiação, não o pouso do código).
