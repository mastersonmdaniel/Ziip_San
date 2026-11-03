# Modelagem de ameaças STRIDE (P0.24)

Modelo versionado no repositório. Escopo: API, BFF, worker, webhook e importador.
Cada ameaça tem um controle existente ou uma tarefa do plano que a trata — nenhuma
ameaça fica sem um dos dois, e nenhum controle é afirmado sem existir no código ou no
plano. Componentes marcados "projeto" não estão implementados nesta onda: as ameaças
deles são de desenho e as tarefas citadas são as que as tratam quando o componente
nascer. Componentes marcados "implementado" têm os controles conferidos no código
citado (caminhos relativos à raiz do repositório).

Legenda STRIDE: **S** falsificação de identidade, **T** adulteração, **R** repúdio,
**I** divulgação de informação, **D** negação de serviço, **E** elevação de privilégio.

## 1. API — implementado

Base: `platform/ziipsan-app` (controllers, `SecurityConfig`) e filtros de
`platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/security/`
(`JwtTenantFilter`, `RateLimitFilter`, `ContentLengthLimitFilter`, `MfaGateFilter`).
Recurso: resource server OAuth2 JWT; o tenant da requisição vem do token, nunca de
cabeçalho enviado pelo navegador.

| ID | STRIDE | Ameaça | Controle existente ou tarefa |
|---|---|---|---|
| T-API-01 | S | Token JWT de outro tenant ou forjado aceito | Resource server JWT com emissor/audiência/chave do teste e de produção separados; `JwtTenantFilter` rejeita `X-Tenant-UUID` enviado pelo navegador sem ligação com o usuário autenticado |
| T-API-02 | S/E | Usuário sem MFA alcança rota sensível | `MfaGateFilter` (P0.14); estrutura de MFA do realm conferida por `RealmMfaStructureTest` |
| T-API-03 | T | Rota inexistente/método/corpo malformado vaza detalhe interno | `GlobalExceptionHandler` + problem+json com códigos da tabela da P0.33 (`ProblemCodeContractTest`); fail-on-unknown-properties vira 422 (P0.16) |
| T-API-04 | T/E | Cross-tenant: ler/escrever dado de outro tenant | RLS forçada por tabela de tenant + `register_tenant_table`; `TenantRlsIsolationTest`; `DatabaseHardeningTest` |
| T-API-05 | I | Documento (CPF/CNPJ) revelado em lista/tela/log | `registry:reveal-document` sem perfil de fábrica (só concessão direta); redação em `audit_log` (`AuditRedactionTest`, `AuditRedactionCoverageTest`); CPF/CNPJ só cifrados (P0.13, `FieldProtector`) |
| T-API-06 | I | Erro de validação revela dado pessoal em log | `Json` do core não repete conteúdo em exceção (tamanho e tipo bastam) |
| T-API-07 | D | Rajada de requisições derruba a API | `RateLimitFilter` (teto próprio testado em `RateLimitIntegrationTest`; desligado na suíte compartilhada por decisão registrada) |
| T-API-08 | D | Corpo gigante esgota memória | `ContentLengthLimitFilter` |
| T-API-09 | R | Ação sem autor (quem alterou o quê?) | `audit_log` com trilha por tenant; `HashChain` no MTR para trilha encadeada (fiscal, P1.8, usa o mesmo padrão) |
| T-API-10 | E | Permissão nova fora de todo perfil vira concessão por omissão | Negação por padrão (papel/permissão desconhecida não concede, CATALOGO-PERMISSOES); teste do CI recusa permissão fora de perfil de fábrica (P1.37) |
| T-API-11 | T | Dependência vulnerável (ex.: Tomcat) entra no build | OWASP Dependency-Check/OSV bloqueia achado alto (P0.3); Tomcat fixado em 11.0.27 por CVEs da linha 11.0.24 |

## 2. BFF — implementado

Base: `BffController`, `BffLoginSuccessHandler`, `BffJwtReconstructionFilter`,
sessões Spring Session JDBC (core V5, tabelas `SPRING_SESSION*`, sem RLS de propósito
porque a sessão é anterior a qualquer tenant).

| ID | STRIDE | Ameaça | Controle existente ou tarefa |
|---|---|---|---|
| T-BFF-01 | S | Sessão roubada (cookie) reutilizada | Sessão no PostgreSQL, não na memória: réplicas leem a mesma base sem afinidade; expiração por `expiry_time`; `spring.session.jdbc.initialize-schema=never` (sem DDL em runtime) |
| T-BFF-02 | S | CSRF contra o BFF (cookie + navegador) | CSRF com `CookieCsrfTokenRepository` + `CsrfTokenRequestAttributeHandler` em `SecurityConfig`; CORS restrito por `CorsConfigurationSource` |
| T-BFF-03 | T | Token reconstruído com tenant errado | `BffJwtReconstructionFilter` + `BffJwtReconstructionFilterTest`; `BffChainIntegrationTest` |
| T-BFF-04 | I | Sessão vaza dado de tenant em atributo | Atributos de sessão são do servidor; sem RLS porque não há tenant antes do token — acesso só pela role da aplicação (GRANT da V5) |
| T-BFF-05 | D | Sessões órfãs acumulam sem expiração | `expiry_time` + limpeza do Spring Session; queda de réplica sem perda de sessão é RNF-17 (ensaio P0.29, LC.6) |
| T-BFF-06 | R | Login sem registro | `BffLoginSuccessHandler` + `audit_log`; trilha de acesso do encarregado (ED-75, P3) |

## 3. Worker — projeto (não implementado)

O worker nasce com a outbox (core V8, P1.9) e `job_runs` (core V10, P2.1). Até lá,
nenhum código de worker existe: as ameaças abaixo são requisitos de desenho, não
controles afirmados.

| ID | STRIDE | Ameaça | Tarefa que a trata |
|---|---|---|---|
| T-WRK-01 | T/E | Job executa sem contexto de tenant (vaza dado entre tenants) | P1.9: contexto explícito do tenant no worker (decisão registrada na P0.25, reescrita da V1_1) |
| T-WRK-02 | T | Reprocessamento gera segunda conta, segunda baixa ou segunda NFAg-e | P1.9/P1.8: outbox + idempotência (`UNIQUE (tenant, source_type, source_id, document_type)`); P0.32: DPS persistida antes do envio, nunca retransmitir sem reconciliar |
| T-WRK-03 | D | Um tenant esgota a cota/fila dos outros | P1.9: lote por tenant, `FOR UPDATE SKIP LOCKED`, bulkhead por autorizador e UF (fiscal, FISCAL-PORTE-JAVA §3.3) |
| T-WRK-04 | I | Worker decifra certificado e o registra em log | P1.8: só o worker fiscal decifra, só em memória; nada em disco, log, API ou `audit_log`; senha e PFX zerados após o uso |
| T-WRK-05 | R | Job sem rastro de quem o disparou | P2.1: `job_runs` com autor e resultado; `audit_log` |

## 4. Webhook — projeto (não implementado)

O endpoint de webhook Pix não existe (RNF-06: "endpoint inexistente"). Ameaças de
desenho para quando nascer (P3, com RNF-06: resposta < 2 s p99, baixa < 60 s p95).

| ID | STRIDE | Ameaça | Tarefa que a trata |
|---|---|---|---|
| T-WHK-01 | S | Webhook forjado (terceiro finge ser o PSP) | P3 (webhook Pix): verificação de assinatura do PSP antes de qualquer baixa; segredo por tenant no cofre, nunca em query string |
| T-WHK-02 | T | Replay do mesmo webhook baixa duas vezes | P3: idempotência por id do evento do PSP + baixa conciliada uma única vez |
| T-WHK-03 | D | Rajada de webhooks falsos enfileira a API | P3: resposta rápida (< 2 s p99) com processamento assíncrono; rate limit por origem; fila com idade máxima (RNF-09) |
| T-WHK-04 | I | Payload do webhook com dado pessoal cai em log | P3: log só com id e resultado; dado pessoal cifrado como o resto do cadastro |
| T-WHK-05 | R | Baixa sem prova de origem | P3: registro do evento bruto + assinatura verificada, encadeado no `audit_log` |

## 5. Importador — projeto (não implementado)

Importador = ingestão de arquivos e de dados legados: importação por REST e arquivo
(ED-17, ADR-0028, nasce na `api` e no `worker`) e importação de XML fiscal anterior à
primeira produção (P2.19, ADR-0022 item 7). Nenhum código de importação existe nesta
onda.

| ID | STRIDE | Ameaça | Tarefa que a trata |
|---|---|---|---|
| T-IMP-01 | T | Arquivo malicioso (XXE, zip bomb, poliglota) na ingestão | ED-17/ADR-0028: validação por schema antes de processar; anti-XXE (padrão do assinador fiscal, JSR 105 com validação segura); quarentena + ClamAV (mesmo pipeline dos anexos, MARCA-E-TEMAS §3.2) |
| T-IMP-02 | I | Importação de legado traz dado pessoal real para o ambiente local | ADR-0033: ambiente local só com dado sintético ou anonimizado; XML real do piloto só na produção, no lançamento (LC.10); importador testado localmente só com XML sintéticos |
| T-IMP-03 | T | XML importado regerado/reformatado perde a prova de origem | P2.19: o aceito é gravado como recebido, sem regerar nem reformatar; duplicidade pela chave de acesso; o recusado vai para o relatório do lote |
| T-IMP-04 | E | Importador ignora RLS e escreve em schema errado | ED-17: ingestão com contexto de tenant explícito (mesma regra do worker, P1.9) e RLS forçada |
| T-IMP-05 | R | Lote importado sem relatório do que entrou e do que foi recusado | P2.19: relatório do lote (aceitos, recusados, motivo); exclusão registrada por ciclo quando houver descarte |

## 6. Cobertura

- STRIDE por componente: API 11, BFF 6, worker 5, webhook 5, importador 5 = 32 ameaças.
- Sem órfãos: cada linha tem controle existente (código citado) ou tarefa do plano.
- Revisão: este modelo é revalidado quando worker (P1.9), webhook (P3) e importador
  (ED-17/P2.19) nascerem — as linhas "projeto" viram controles com código ou voltam
  como pendência, sem serem marcadas como atendidas antes.
