# Diário de Bordo — SIGSAN

Registro vivo do projeto: o que é, como está, o que travou e o que vem agora.
Última atualização: 08/11/2026

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
