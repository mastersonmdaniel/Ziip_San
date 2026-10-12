# Arquitetura-alvo de produção

Base: revisão 17 (§4 e §5) e ADRs 0003, 0005, 0006, 0007, 0010, 0011, 0012, 0013, 0014, 0018, 0020, 0022 e 0024 a
0032. Hospedagem: AWS `sa-east-1` com serviços gerenciados (ADR-0011, aceito em 12/10/2026); o código continua neutro
quanto a provedor, para a cláusula de reversão do ADR-0024. Metas em `REQUISITOS-NAO-FUNCIONAIS.md`. Os nomes de roles
abaixo (`sigsan_*`) passam a `ziipsan_*` na renomeação do P0 (plano P0.33). Nomes técnicos em inglês (plano §8,
Idioma).

## 1. Componentes

Monólito modular: um artefato (imagem única, não-root, assinada) executado em três processos.

| Componente | Papel |
|---|---|
| Borda | DNS, CDN e WAF; TLS 1.3, HSTS, CSP, rate limit por IP; SPA em armazenamento de objetos com assets imutáveis; `/api` e `/bff` para o balanceador |
| `api` | REST e BFF (ADR-0006) no mesmo processo; 3+ réplicas sem afinidade; sessão do BFF no PostgreSQL; role `sigsan_app` |
| `worker` | Outbox, lote, retorno bancário, fiscal (ADR-0003), PDFs, notificações e coletor da Central Ziip (ADR-0018); réplicas por fila; role `sigsan_worker` |
| ClamAV | Varredura dos arquivos enviados (anexos de chamados e logos de tenant), junto do `worker`, com quarentena até a varredura; substituível pelo serviço da hospedagem (exceção do ADR-0010) |
| `migrate` | Job antes de cada rollout; único detentor da credencial `sigsan_migrator` |
| Keycloak 26.8 | 2+ réplicas, `start --optimized`, banco próprio; MFA conforme ADR-0007; realm `ziipsan` para backoffice e plataforma e realm `ziipsan-portal` para os consumidores do portal, com client e emissor próprios (ADR-0022); no portal, login por CPF/CNPJ ou e-mail e senha, sem SMS, com autenticador próprio que localiza a conta pelo índice cego do documento e eventos de login sem o identificador digitado |
| PostgreSQL 17 + PostGIS 3.5 | RDS Multi-AZ: primário com standby síncrono, PITR e réplica de leitura para relatórios, SINISA e BI (ADR-0011, ADR-0013, ADR-0027); pooler PgBouncer em modo transaction (RDS Proxy só após teste de pinning, ADR-0011) |
| Armazenamento de objetos | S3 com Object Lock; prefixo e chave por tenant; retenção imutável para documentos fiscais (XML autorizado servido como gravado, ADR-0022) |
| Outbox | Tabela no PostgreSQL, workers com `SKIP LOCKED` (ADR-0014) |
| Cofre/KMS | AWS KMS (KEK e DEK por tenant, R1) e Secrets Manager (segredos de execução) (ADR-0011) |
| Relatórios e BI | Relatórios e painéis padrão nativos (`api` e `worker`); Apache Superset embarcado a partir do P4, lendo só a réplica, com role de banco por tenant (ADR-0027; exceção do ADR-0010) |
| Apps | App de campo Android e app do cliente Android e iOS por tenant, em Capacitor sobre o mesmo frontend (ADR-0026); login pelo navegador do sistema com PKCE, token no armazenamento seguro do aparelho e chamadas à `api` pela camada nativa; app de campo no realm `ziipsan`, em dispositivo registrado e com bloqueio remoto; app do cliente no realm `ziipsan-portal` (ADR-0030); conta calculada no aparelho pelo mesmo módulo Java do servidor (ADR-0031) |
| Observabilidade | OpenTelemetry Collector para métricas, logs e traces; error tracking; página de status pública em hospedagem separada da produção (ADR-0020) |
| CI/CD | GitHub Actions: testes, imagem com SBOM, homologação, produção com aprovação manual e rollback |

Kafka, Redis, MinIO, TimescaleDB e Loki/Tempo/Pyroscope ficam fora até haver caso concreto (ADR-0010). Exceções
registradas (ADR-0010, seção Exceções): ClamAV; Apache Superset (P4); ODA File Converter, só na etapa assistida de
implantação; ingestão de telemetria por REST e arquivo, sem broker.

```mermaid
flowchart LR
  U[Navegador / apps Capacitor] --> E[CDN + WAF]
  PSP[PSP Pix] --> E
  E -->|estáticos| OBJ[(Objetos)]
  E --> LB[Balanceador]
  LB --> API[api + BFF x3]
  API --> KC[Keycloak x2]
  API --> PGB[Pooler]
  W[worker xN] --> PGB
  M[migrate job] --> PG
  PGB --> PG[(PostgreSQL 17 primário)]
  PG --> SB[(standby)]
  PG --> RR[(réplica de leitura)]
  BI["Superset (P4)"] --> RR
  PG --> BK[(backup + WAL)]
  W --> OBJ
  W --> EXT["SEFAZ, banco, gráfica, e-mail, SMS/WhatsApp (P4)"]
  API & W --> KMS[Cofre/KMS]
  API & W & KC --> OTEL[OTel Collector]
```

## 2. Fluxos críticos

1. **Requisição autenticada.** O navegador envia só o cookie `__Host-` HttpOnly ao `/bff`. O BFF obtém o access token
   da sessão, renova com rotação quando necessário e chama a API internamente. A API valida emissor, audiência e
   `tenant_id` contra o catálogo, abre a transação, aplica o contexto local (§4) e a RLS filtra. Nos apps (ADR-0030),
   a camada nativa envia o Bearer token do client público do app; a API confere também o client e, no app de campo, o
   dispositivo registrado.
2. **Faturamento em lote.** O operador abre o ciclo; `job_runs` recebe trava única `(tenant_id, cycle_id)`. O ciclo é
   particionado por rota, cada rota vira uma mensagem na fila `billing`. O worker processa em blocos com commit e
   checkpoint (última ligação); falha retoma do checkpoint; `UNIQUE (cycle_id, unit_id)` impede conta duplicada. Ao fim,
   a mesma transação de cada bloco publica as mensagens fiscais e de gráfica.
3. **Emissão fiscal.** A mensagem `fiscal.issue` nasce na transação da conta. O worker monta a NFAg-e, assina com o
   certificado do cofre, envia por mTLS e grava o protocolo. Retry com backoff e jitter; idempotência por
   `(bill_id, model, series)`; rejeição vai para a fila de pendências do operador; contingência conforme o leiaute.
4. **Webhook Pix.** A API valida a origem e a assinatura do PSP, grava em `pix_inbox` com
   `UNIQUE (psp, end_to_end_id)` e `ON CONFLICT DO NOTHING`, e responde 200. O worker concilia e baixa a conta. Uma
   consulta diária ao PSP recupera notificações perdidas.
5. **Sincronização de campo.** O app guarda leituras offline com `client_reading_id` (UUIDv7) e envia lotes de até 500.
   A API grava com `UNIQUE (client_reading_id)` e responde item a item (aceita, duplicada, rejeitada). A crítica de
   leitura roda no worker. A carga de rota usa delta por `updated_at` com keyset. Na emissão simultânea (ADR-0031), o
   pacote da rota leva uma tarefa por ligação (`task_id`), tarifas e parâmetros com as versões vigentes; a conta
   calculada no aparelho volta com as versões usadas, é única por `task_id`, é recalculada pelo worker e, se divergir,
   vai para a fila de divergências; o material usado na OS de campo é baixado na mesma sincronização (ADR-0032).
6. **Migração em ondas.** O job `migrate` aplica o core e depois os tenants com paralelismo limitado, em ondas: tenant
   sintético, canário (1–2 tenants), 10%, restante. `tenant_schema_version` registra o status; falha põe o tenant em
   quarentena sem parar os demais. Mudanças em expand/contract, compatíveis com as versões N e N-1 da aplicação;
   `outOfOrder` desligado.
7. **Provisionamento.** O console da plataforma (MFA obrigatório) cria o tenant no catálogo como `PROVISIONING`, com
   porte e shard; recusa schema existente; cria estrutura conforme o porte, DEK no KMS, organização e primeiro
   administrador no Keycloak (convite) e parâmetros G com aceite (ADR-0002); só então `ACTIVE`. Cada passo é
   idempotente e retomável pela outbox.

## 3. Multi-tenant híbrido e roteamento

| Porte (ADR-0005) | Ligações | Dados | Lote |
|---|---|---|---|
| Pequeno | até 20 mil | Schema próprio (padrão do ADR-0005); autarquia muito pequena pode dividir schema com `tenant_id` e RLS, sob demanda (P4.6) | Workers compartilhados, 1 partição simultânea |
| Médio | 20–200 mil | Schema próprio no cluster compartilhado | Fila e janela de lote próprias |
| Grande | acima de 200 mil | Cluster dedicado | Workers dedicados |

O `tenant_catalog` ganha `size_tier` (porte), `shard_id` e `schema`. Cada shard tem pool próprio; o roteador resolve
`tenant → shard → schema` a partir do JWT, com cache em memória invalidado por evento. Mudança de porte é migração
assistida com janela.

## 4. Conexões e contexto de tenant

- Pools separados por processo e finalidade: `api` (20 por réplica), `ingestion` (webhook e sincronização, 5 por réplica,
  para não disputar com o backoffice), `worker` (10 por réplica). Todos com `connectionTimeout`, `maxLifetime` e
  métricas.
- Roles: `sigsan_app` com `statement_timeout` de 5 s; `sigsan_worker` com 15 min; ambas com
  `idle_in_transaction_session_timeout` de 30 s e sem BYPASSRLS.
- Contexto transacional: `search_path`, `app.current_tenant` e `app.current_user` aplicados com
  `set_config(..., true)` no início de cada transação, o que torna o pooler em modo transaction seguro. Hoje o escopo é
  de sessão (`MultiTenantConnectionProvider.java:39-47`).
- RDS Proxy: contexto de sessão (`SET` ou `set_config` sem escopo de transação), prepared statements do driver e
  outros estados de sessão fixam a conexão (pinning) e anulam a multiplexação; o proxy só entra após teste que meça
  as sessões fixadas e prove que o contexto de um tenant não aparece em outro (ADR-0011).
- Ferramentas com conexão direta ao banco (Superset, QGIS) não usam a GUC de tenant como fronteira, porque a sessão
  pode alterá-la: usam role de login por tenant ou por usuário, com a restrição de linhas feita pela identidade da
  conexão (ADR-0027, ADR-0028).
- `current_tenant_id()` reescrita como SQL `STABLE`.

## 5. Partição e retenção

| Tabela | Chave de partição | Retenção (a confirmar no RNF-40) |
|---|---|---|
| `audit_log` | Mês de `created_at`, PK `(created_at, id)` UUIDv7, `pg_partman` | 13 meses no banco; depois exportada para objetos imutáveis até o prazo legal |
| `bills`, `bill_items`, `payments` | Competência | Prazo fiscal e regulatório; histórico de 120 meses |
| `meter_readings` | Data da leitura | Idem |
| `outbox`, `pix_inbox` | Dia | 30 dias após processamento |

Em schema próprio a partição é anual, para limitar o catálogo (76 tabelas por tenant hoje, D2); no compartilhado,
mensal. Nas tabelas de lote a auditoria é por bloco, com `run_id`, e não por linha (X3).

## 6. Dimensionamento inicial

Valores de partida para o piloto, a validar pela carga de referência (RNF-70):

| Componente | Inicial |
|---|---|
| `api` | 3 × 2 vCPU / 4 GB |
| `worker` | 2 × 2 vCPU / 4 GB, autoscaling pela fila |
| Keycloak | 2 × 1 vCPU / 2 GB |
| PostgreSQL | 4 vCPU / 16 GB / 500 GB SSD, mais standby e réplica de leitura |
| Cluster dedicado (grande) | 8 vCPU / 32 GB, workers próprios |

## 7. Mapeamento por hospedagem (ADR-0011)

### 7.1 AWS `sa-east-1` (escolhida em 12/10/2026)

| Componente | Serviço | Situação |
|---|---|---|
| CDN e WAF | CloudFront + AWS WAF | Referência; confirmar na IaC (P0.27) |
| Balanceador | ALB | Referência; confirmar na IaC |
| `api`, `worker`, Keycloak | ECS Fargate | Referência; confirmar na IaC |
| `migrate` | Tarefa ECS avulsa | Referência; confirmar na IaC |
| PostgreSQL com HA | RDS for PostgreSQL 17 Multi-AZ | Decidido (ADR-0011) |
| Réplica de leitura | Read replica RDS (relatórios, SINISA, BI) | Decidido |
| Pooler | PgBouncer em modo transaction; RDS Proxy só após teste de pinning | Decidido |
| Backup e PITR | Backup automático do RDS com PITR; cópia de snapshots em outra conta na mesma região, com política de expiração | Decidido |
| Objetos | S3 com Object Lock | Decidido |
| Cofre/KMS | AWS KMS (KEK, DEK por tenant) + Secrets Manager | Decidido |
| Observabilidade | CloudWatch, X-Ray ou Managed Prometheus/Grafana, alimentados pelo OpenTelemetry Collector | Referência; confirmar na IaC |
| Registro de imagens | ECR | Referência; confirmar na IaC |
| Serviços de segurança do provedor (detecção de ameaças, inspeção de vulnerabilidades, registro de configuração) | Catálogo da AWS, conforme custo | A habilitar (edital de Videira) |

Instância dedicada (ADR-0024): a mesma pilha, criada pela mesma IaC numa conta ou ambiente separado.

### 7.2 Histórico: opções não escolhidas

Mantido como registro da comparação do ADR-0011. A coluna "VPS/servidor próprio" serve de referência para o pacote de
reversão do ADR-0024 (execução na infraestrutura do órgão). O pacote roda em Linux e, pelo ajuste da v2.5 do ADR-0024,
também em Windows Server 2022 ou superior, com os contêineres Linux sobre Docker no WSL2, homologado uma vez na ED-04.

| Componente | AWS sa-east-1 | Azure Brasil | VPS/servidor próprio |
|---|---|---|---|
| CDN e WAF | CloudFront + AWS WAF | Front Door Premium com WAF | Cloudflare ou Nginx com ModSecurity |
| Balanceador | ALB | Front Door / Application Gateway | HAProxy em par com keepalived |
| `api`, `worker`, Keycloak | ECS Fargate | Container Apps | Docker em 3+ hosts (Compose ou k3s) |
| `migrate` | Tarefa ECS avulsa | Container Apps Job | Container avulso no pipeline |
| PostgreSQL com HA | RDS Multi-AZ | Flexible Server com HA por zona | Patroni + etcd em 3 nós |
| Réplica de leitura | Read replica RDS | Read replica | Standby de streaming |
| Pooler | PgBouncer próprio (RDS Proxy só após teste de pinning) | PgBouncer embutido | PgBouncer |
| Backup e PITR | Backup automático + snapshot em outra conta | Backup do Flexible Server (geo-redundância pareia fora do Brasil) | pgBackRest em outro datacenter no Brasil |
| Objetos | S3 com Object Lock | Blob com imutabilidade | S3 compatível nacional ou MinIO dedicado (ADR-0010) |
| Cofre/KMS | KMS + Secrets Manager | Key Vault | OpenBao/Vault em 3 nós, KEK com cópia offline |
| Observabilidade | CloudWatch, X-Ray ou Managed Prometheus/Grafana | Azure Monitor, Application Insights | Prometheus + Grafana auto-hospedados ou SaaS sem dado pessoal |
| Registro de imagens | ECR | ACR | GHCR |

## 8. Lacunas em relação ao estado atual

| Achado | Estado atual | Alvo |
|---|---|---|
| X1 | Sem Dockerfile, CI remoto ou ambientes | Imagem única e pipeline com homologação |
| X2 | PG 15 em nó único, sem backup | PG 17 com HA, PITR e restauração testada |
| X3 | `audit_log` sem partição, gatilho por linha | Partição mensal; auditoria por bloco no lote |
| X4 | Sem lote nem assíncrono | Outbox e processo `worker` |
| D1 | Rollout sequencial, `outOfOrder(true)` | Ondas com canário e quarentena |
| D2 | Só schema por tenant | Híbrido por porte com shard |
| D3 | Pool único, contexto de sessão | Pools separados, contexto local |
| S2 | Credencial de migração na JVM | Só no job `migrate` |
| S3, S4 | Realm único sem MFA | Organizações, MFA por política |
| S5 | Token em `sessionStorage`, sem CSP | BFF com cookie, CSP estrita |
| O1 | Sem métricas nem traces | OpenTelemetry e SLOs |
| O2 | Terraform em `us-east-1` | IaC nova em AWS `sa-east-1` (ADR-0011) |
| O3 | Keycloak `start-dev` | 2+ réplicas com TLS |
| O4 | Sem metas nem carga | RNF e k6 no CI |
| P1, P2 | Sem console nem portal | Console e Central v0 (P1), Central completa e portal com histórico e documentos (P3) |
