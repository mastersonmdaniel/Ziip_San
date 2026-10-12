# Revisão de estrutura para operação SaaS — 12/10/2026

Escopo: o que já foi feito, o que está planejado e o que falta para o Ziip SAN operar como SaaS multi-tenant com muitos
clientes e muita carga, rápido, responsivo e seguro. Cinco revisões independentes, só leitura, sobre o código em `fb2b53d`:
backend e dados, segurança e LGPD, infraestrutura e operação, frontend, e plano e produto. Os achados mais graves foram
conferidos no código (caminhos relativos à raiz do repositório).

## 1. Conclusão

A fundação de segurança e de isolamento entre tenants é sólida e está provada por teste no PostgreSQL real. O que falta
não é correção do que existe, e sim a **camada que transforma o código em serviço operado**: artefato implantável,
ambientes, banco com alta disponibilidade e backup, processamento em lote, observabilidade, metas de desempenho e testes
de carga. Nenhuma dessas peças está no roadmap atual, que segue a ordem das funcionalidades e não tem um marco de
"primeiro cliente em produção".

No produto, faltam três itens que costumam decidir a venda: migração de dados do sistema legado, console de
administração da plataforma e portal do cliente final. No negócio, as decisões G1–G9 continuam sem responsável jurídico.

## 2. Estado

| Frente | Situação |
|---|---|
| Fundações E0–E6 | Concluídas em 12/10 (alvo era 27/11). 973 testes Java e 134 do frontend verdes. |
| Cadastro (registry) | Única API REST. Cadastro de cliente pela tela bloqueado (tipo de cliente e endereço sem backend). |
| Faturamento, medição, arrecadação | Motores de cálculo puros e testados; sem serviço, API nem lote. |
| Módulos `portal`, `dunning`, `accounting`, `field`, `reading`, `assets`, `events`, `integration` | Pastas sem POM. |
| Infraestrutura | Só ambiente local (compose). Sem Dockerfile, sem CI remoto, Kubernetes e Terraform não funcionais. |
| Código | GitHub privado `mastersonmdaniel/Ziip_San`, push manual. |

## 3. O que já está bom (manter)

- Tenant só do JWT, conferido no catálogo; cabeçalho divergente recusado; contexto limpo no `finally`.
- RLS FORCE em toda tabela de tenant, com teste que prova a cobertura (`FreshInstallTest`) e teste de isolamento de ponta
  a ponta. Roles separadas; `sigsan_app` sem BYPASSRLS; o banco não sobe com configuração de segurança errada.
- Conexão devolvida ao pool sempre limpa; descartada se a limpeza falhar.
- Cifra AES-GCM com AAD de tenant e campo; índice cego; aplicação não sobe sem provedor de chaves.
- Auditoria só de inserção com redação de dado pessoal; problem+json sem detalhe interno.
- Integridade no banco (CHECK da aritmética da conta, EXCLUDE de vigências, idempotência de pagamento e de arquivo de
  retorno), `@Version` em toda entidade, `open-in-view` desligado.
- Motores de cálculo sem estado, fáceis de paralelizar. API sem sessão, pronta para várias réplicas.
- Frontend: CPF fora da URL, revelação com finalidade e prazo, PKCE, TypeScript estrito, base de acessibilidade com axe.

## 4. Achados priorizados

### 4.1 Críticos — bloqueiam a entrada em produção

| ID | Achado | Evidência | Recomendação |
|---|---|---|---|
| X1 | Não existe artefato implantável nem ambiente além do local | Sem Dockerfile; `run-app.sh` roda JRE como root com jar montado; sem `.github/`; `ci.sh` depende de PowerShell/robocopy; login do frontend fixado no build (`VITE_OIDC_*`) | Dockerfile multi-stage não-root; `config.json` do frontend em tempo de execução; GitHub Actions com Postgres de serviço; ambientes de homologação e produção |
| X2 | Banco sem alta disponibilidade, backup, PITR ou meta de RPO/RTO | Nó único (réplica removida); Patroni do k8s não funcional; nenhum pgBackRest/WAL-G; PG 15 sai de suporte em nov/2027 | Banco gerenciado multi-AZ ou pgBackRest com PITR; restauração testada mensalmente; subir para PG 17 + PostGIS 3.5 antes do go-live |
| X3 | `audit_log` único para todos os tenants, sem partição, alimentado linha a linha pelas tabelas de lote | `core/V1__create_core_schema.sql:43` (sem PARTITION); triggers em `bills`, `bill_items`, `meter_readings`, `payments` | Particionar por mês (`pg_partman`), PK `(created_at, id)` com UUIDv7, retenção definida; nas tabelas de lote, auditoria por statement ou por lote (`run_id`) |
| X4 | Não há infraestrutura de lote nem trabalho assíncrono | Nenhum `@Service` em billing/metering/collection; sem Spring Batch, outbox, `job_runs`, `SKIP LOCKED`, `@Scheduled` | Fundação antes do R4: Spring Batch particionado por tenant → rota, outbox transacional, trava por (tenant, ciclo), processo `worker` separado da `api` com pool próprio, fila justa entre tenants |
| X5 | Roadmap sem marco de go-live, sem migração de legado e sem jurídico | Plano R1–R9; G1–G9 sem responsável; Q7 (migração) aberta no relatório 00 | Marco "piloto em produção" com faturamento paralelo; fase de migração (inclui 120 meses de histórico, NR 11); contratar jurídico/tributário até 31/10 |

### 4.2 Altos

**Segurança**

| ID | Achado | Evidência | Recomendação |
|---|---|---|---|
| S1 | CPF reconstruível por quem só tem leitura | Máscara mostra 6 dos 9 dígitos-base (`DocumentNumber.java:95`); busca por CPF aberta a `READ` sem limite nem registro; dígitos verificadores calculáveis → no máximo 1.000 tentativas | Mascarar mais (`***.***.789-**`); busca por documento com papel próprio, registro e cota; alerta de volume |
| S2 | Credencial de migração (dona das tabelas, pode desligar RLS) dentro da JVM da aplicação | `DataSourceConfig.java:29-39` cria `migratorDataSource` sempre | Em produção, migração em job separado; a aplicação recebe só `sigsan_app` |
| S3 | Administração de usuários por tenant inviável com segurança no realm único | `tenant_id` editável por "admin" (`sigsan-realm.json:359`); `ADMIN` é do realm inteiro | Keycloak Organizations ou API própria de gestão de usuários que fixe o `tenant_id`; papel de plataforma separado e auditado |
| S4 | Sem MFA | Realm sem OTP/WebAuthn | MFA obrigatório para ADMIN, MANAGER e quem revela documento |
| S5 | Superfície web incompleta | Sem CSP em lugar nenhum; token em `sessionStorage` (`oidc.ts:28`, o plano previa memória); sem rate limiting; sem `forward-headers-strategy` (HSTS atrás de proxy); listas sem `@Size`; refresh sem rotação | CSP estrita; token em memória ou BFF com cookie HttpOnly; limites por IP/usuário/tenant; HSTS; limites de corpo e de lista |
| S6 | Dependência crítica no navegador | `maplibre-gl` 4.7.1 (XSS no sanitizador); `npm audit` não bloqueia o CI | Subir para 6.x e fazer o audit bloquear severidade alta |

**Dados e escala**

| ID | Achado | Evidência | Recomendação |
|---|---|---|---|
| D1 | Rollout de migrations sequencial, que trava tudo se um tenant falhar, e com ordem divergente entre tenants | `DatabaseMigrator.java:50-58`; `outOfOrder(true)` (`TenantMigrator.java:51`) | Job de migração com paralelismo limitado, `tenant_schema_version` com status por tenant, quarentena, ondas canário, expand/contract, versões sempre crescentes |
| D2 | Limite prático do schema por tenant | 76 tabelas e 227 índices/constraints por tenant hoje; tende a dobrar | Teste sintético com 300 schemas; teto por cluster com coluna de shard no catálogo; modelo híbrido (pequenos em schema compartilhado, grandes em schema/cluster próprio) |
| D3 | Pool único sem dimensionamento e contexto de tenant em escopo de sessão | `DataSourceConfig.java:20-27` sem tamanho/timeouts; `set_config` de sessão incompatível com PgBouncer em modo transaction | Pools separados (API, lote, ingestão); `statement_timeout` e `idle_in_transaction_session_timeout` na role; `set_config(..., true)` dentro da transação |
| D4 | N+1 nas listas e paginação por offset com `count(*)` a cada página | `ConsumerUnitService.java:740-761`; `JpqlQuery.java:59-70` | Projeção única ou carga em lote por IN; keyset para listas grandes; contagem opcional |
| D5 | Busca por nome faz varredura completa | `lower(name) LIKE '%x%'` com índice btree; `pg_trgm` instalado e não usado | Índice GIN trigram sobre `unaccent(lower(name))` |

**Operação**

| ID | Achado | Recomendação |
|---|---|---|
| O1 | Observabilidade praticamente ausente: sem Prometheus/Micrometer, sem tracing; alertas do k8s usam métricas que não existem | Micrometer + OpenTelemetry com tenant nos spans, `pg_stat_statements`, métricas do Hikari, error tracking, SLOs com alerta por burn rate |
| O2 | Terraform inválido, em `us-east-1`, com senhas, token do Vault e chave SSH como outputs gravados no state; contradiz a decisão de residência no Brasil | Refazer quando a hospedagem for decidida; região brasileira; segredos fora do state |
| O3 | Keycloak em `start-dev`, HTTP; manifesto k8s na 24.0 | `start --optimized`, 2+ réplicas, TLS, `sslRequired=all` |
| O4 | Sem metas não funcionais nem teste de carga | Ver §6; k6 ou Gatling com massa sintética no CI |

**Produto e frontend**

| ID | Achado | Recomendação |
|---|---|---|
| P1 | Sem console da plataforma (onboarding, planos, cotas, módulos por tenant, suspensão, exportação e eliminação ao fim do contrato, impersonação auditada de suporte) | Versão 0 junto do R2 |
| P2 | Sem portal do cliente (2ª via, Pix, protocolo) — exigência da NR 11 e de licitação | Junto do R5a/R7 |
| P3 | Sem app de leitura em campo; leitura simultânea offline é a regra nos SAAEs | Importar arquivo de leitura terceirizada no R4; PWA offline-first separado do backoffice depois |
| P4 | Integrações não previstas: NFAg-e por integrador, gráfica, SMS/WhatsApp/e-mail com prova de entrega (aviso de corte), CEP, PSP Pix | R4/R5 |
| F1 | Frontend sem Error Boundary, sem code splitting por rota, sem autorização por papel na interface | Antes de crescer o número de telas |

### 4.3 Médios

- **Cache latente que mistura tenants:** a chave usa `#tenantId`, que não é parâmetro do método, e o evict usa outra
  chave (`LgpdEngineService.java:48,130`). Inerte hoje (sem `@EnableCaching`); corrigir antes de ligar qualquer cache.
- **Chave de dev aceita fora de produção:** `dev-env` só é recusado quando o perfil contém "prod" (`CryptoConfig.java:43`).
  Trocar por lista de perfis permitidos.
- **`sigsan_app` escreve no `tenant_catalog`** (sem RLS). Deixar só SELECT.
- **Colisão de schema no provisionamento:** nome truncado + `ON CONFLICT DO UPDATE` pode sobrescrever outro tenant
  (`TenantProvisioningService.java:231,465`).
- **Código legado de realm por tenant** no `KeycloakAdminService` (senha fraca, password grant). Remover.
- **Leituras de dado pessoal sem registro** (lista, busca, detalhe); só o `reveal` é auditado.
- **UUIDv4 em todas as PKs:** fragmenta índice e WAL. UUIDv7 nas tabelas de alto volume.
- **Tabelas de fato sem partição** (`bills`, `bill_items`, `meter_readings`, `payments`); `meter_readings` sem chave de
  idempotência para envio offline repetido.
- **Matrícula gerada com advisory lock + `MAX(CAST)`:** serializa importações. Tabela de sequência por tenant.
- **`current_tenant_id()` em PL/pgSQL com EXCEPTION:** subtransação por chamada sob RLS. Reescrever como SQL STABLE.
- **Fronteiras entre módulos frouxas** (classes duplicadas em billing e metering). Manter monólito modular e impor
  fronteiras com ArchUnit ou Spring Modulith; separar só os workers. Microsserviços não se justificam agora.
- **API sem versão, sem `Idempotency-Key`, sem ETag/If-Match e sem OpenAPI.**
- **Compose de dev expõe serviços não usados** (Redis sem senha, Kafka, Vault dev com root token fixo, SeaweedFS) em
  0.0.0.0. Mover para `profiles:` ou remover.
- **Listas do atendimento perdem filtro e piscam** ao voltar da ficha; tabelas sem layout de celular; menu móvel não modal;
  `retry: false` global.
- **Ambiente de desenvolvimento no Google Drive:** sincronização gera conflitos, I/O lento, CI preso ao Windows, e o `.env`
  local (valores aleatórios de dev) é sincronizado com o Google. Alternativa: clone em disco local ou WSL2, GitHub como
  fonte da verdade, Drive só para documentos.

### 4.4 Baixos

`reWriteBatchedInserts` ausente; índices faltando em `bill_debits`/`bill_credits`; tabela `users` do tenant duplica
`password_hash`/`mfa_secret` do Keycloak; `/api/me` devolve o nome do schema; VIEWER vê clientes excluídos; `consentId`
não validado contra o titular; tags `latest` em imagens; Secrets "changeme" nos manifestos k8s; tema pisca ao carregar;
sem formatador de moeda; sem Web Vitals.

## 5. Arquitetura-alvo de produção

Monólito modular, um único artefato em processos separados, hospedado no Brasil:

1. **Borda:** DNS → CDN + WAF (TLS 1.3, HTTP/2, Brotli, CSP, HSTS, rate limit). SPA servido do armazenamento de objetos
   com assets imutáveis; `/api` segue para o balanceador.
2. **Computação:** `api` (3+ réplicas, autoscaling por latência/CPU); `worker` (lote, outbox, retorno bancário, PDFs,
   notificações; escala pela fila); `migrate` (job antes de cada rollout); Keycloak com 2+ réplicas.
3. **Dados:** PostgreSQL 17 + PostGIS com HA e PITR, réplica de leitura para relatórios e SINISA, pooler após corrigir o
   escopo da GUC; armazenamento de objetos com chave por tenant e retenção imutável para documentos fiscais; fila (outbox
   no próprio Postgres basta no início). Redis e Kafka só com caso concreto.
4. **Segredos e chaves:** cofre gerenciado; chave de dados por tenant (R1), com rotação, backup da KEK e crypto-shredding
   no encerramento de contrato.
5. **Observabilidade:** OpenTelemetry → métricas, logs e traces; error tracking; página de status; plantão.
6. **Entrega:** GitHub Actions → imagem assinada com SBOM → homologação → produção com aprovação manual e rollback.

Fluxos críticos: webhook Pix responde 200 depois de gravar numa caixa de entrada idempotente e o worker concilia;
faturamento particionado por rota com retomada; app de campo sincroniza em lote com chave de idempotência.

Dimensionamento a validar por teste de carga: até 20 mil ligações em infraestrutura compartilhada; 20 a 200 mil com fila
e janela de lote próprias; acima de 200 mil com banco e workers dedicados.

## 6. Metas não funcionais propostas

| Métrica | Meta |
|---|---|
| API, leitura (p95) | < 300 ms |
| API, gravação (p95) | < 800 ms |
| Busca no cadastro (p95) | < 1 s |
| Ciclo de faturamento | 100 mil contas em < 2 h por tenant, sem afetar a API |
| Resposta ao webhook Pix | < 2 s |
| Disponibilidade | 99,5% backoffice; 99,9% portal (atendimento 24x7, NR 11) |
| RPO / RTO | 15 min / 4 h (meta de longo prazo: 5 min / 1 h) |
| Restauração | testada trimestralmente (mensal quando automatizada) |
| Capacidade de projeto | ~50 tenants e 2 milhões de ligações por cluster |
| Carga no CI | tenant sintético de 300 mil ligações; teste com 300 schemas |

## 7. Ajuste proposto do roadmap

A folga de cinco semanas das fundações deve ir para plataforma e migração, não para antecipar funcionalidade.

| Fase | Período | Conteúdo |
|---|---|---|
| **P0 — Plataforma operável** | out–nov/2026 | Jurídico/tributário contratado; hospedagem no Brasil decidida; Dockerfile, CI no GitHub Actions, homologação e produção; banco com backup e PITR testado; R1 (cofre e chaves); observabilidade mínima; metas não funcionais; correções S1–S6 e médios de segurança; parâmetros com vigência (`V1_5`) antecipados |
| **P1 — Migração e satélite** | nov/2026–jan/2027 | Discovery do legado do piloto; importador v0 do cadastro com conciliação; R2; R3 como módulo satélite sobre a base importada; console da plataforma v0; partição do `audit_log` |
| **P2 — Ciclo** | jan–mar/2027 | Fundação de lote (X4); R4 e R4a; NFAg-e por integrador; importação de leitura terceirizada; arquivo para gráfica; testes de carga; importador v1 com 120 meses de histórico |
| **P3 — Pronto para operar** | mar–mai/2027 | R5a e R5b; portal (2ª via, Pix, protocolo); R7 mínimo (protocolo NR 11, OS com bloqueio de corte, aviso com prova de entrega); pentest; contrato SaaS, DPA e SLA |
| **Go-live piloto** | jun–jul/2027 | Dois ou três ciclos de faturamento paralelo, conta a conta contra o legado; corte do sistema antigo |
| **P4** | 2º sem/2027 | R8, R6, BI e prestação de contas, app de leitura offline, R5c |
| **2028** | — | GRS e R9 sob demanda; ISO 27001; instância dedicada |

Congelar até o go-live, salvo cliente pagante: Kafka, Loki/Tempo/Pyroscope, MinIO, Redis, overlay de GPU, Terraform
atual, R9, SCADA/MQTT, motor de DPIA, trilha GRS e Pix Automático.

Itens de segurança e governança que o plano não tem e entram no P0/P3: modelagem de ameaças (STRIDE), pentest com foco em
isolamento entre tenants, plano de resposta a incidente com prazo de comunicação à ANPD (hoje só no R9), DPO nomeado,
RIPD do tratamento de NIS e renda, contratos de operador e suboperadores, versão explícita da API (`/api/v1`) com
OpenAPI, feature flags por tenant, notas de versão e janela de manutenção avisada.

## 8. Decisões que dependem do usuário

1. **Hospedagem:** nuvem gerenciada em região brasileira (banco gerenciado, objetos, fila, cofre) ou servidor próprio/VPS
   com pgBackRest. Define R1, backup e o desenho da infraestrutura.
2. **Adoção do roadmap revisado** (§7) no lugar do R1–R9 atual.
3. **Cliente piloto** e acesso ao sistema legado dele, para a fase de migração.
4. **Jurídico/tributário e DPO:** quem e até quando.
5. **Infraestrutura não usada:** congelar ou remover do compose e do k8s.
6. **Push:** manter manual ou automatizar o backup do código no GitHub (hoje o código vive no Drive e o push depende de
   autorização a cada vez).
7. **Ambiente de desenvolvimento:** sair do Google Drive para disco local ou WSL2.

## 9. Correções que não dependem de decisão

Podem ser feitas já, cada uma com teste:

- S1: máscara do CPF mais restrita, busca por documento com papel próprio, registro e cota.
- Chave de cache de consentimento com o tenant do contexto; evict coerente.
- `dev-env` só em perfis permitidos; `sigsan_app` só com SELECT em `tenant_catalog`; recusa de schema já existente no
  provisionamento; remoção do código de realm por tenant.
- Hikari dimensionado, `statement_timeout` e `idle_in_transaction_session_timeout` na role, `maxLifetime`.
- Índice trigram para busca por nome; `current_tenant_id()` como SQL STABLE; correção do N+1 das listas.
- `@Size` nas listas da API, `forward-headers-strategy`, rotação de refresh token.
- Frontend: token em memória, Error Boundary, code splitting por rota, `keepPreviousData`, autorização por papel na
  interface.
- Serviços não usados do compose em `profiles:` e ligados a 127.0.0.1.
