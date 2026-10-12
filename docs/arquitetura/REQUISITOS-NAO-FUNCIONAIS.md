# Requisitos não funcionais

Metas aceitas pelo ADR-0012, a partir da revisão 17 (§6). Fases conforme o ADR-0001. Estado conferido no código em
12/10/2026: **Atende**, **Parcial**, **Não atende** ou **Não medido**.

Convenções: percentis medidos no servidor (span HTTP do processo `api`), janela móvel de 28 dias, sem erros 4xx de
cliente. "Carga de referência" é o cenário k6 com tenant sintético de 300 mil ligações e 300 schemas (RNF-70). A fase
indica quando a meta passa a ser critério de aceite.

As metas abaixo são o padrão do produto. O nível de serviço **Setor Público** (ADR-0025), contratável por cliente,
substitui algumas delas no contrato que o escolher: 99,9% de disponibilidade (RNF-10), exportação em 10 dias úteis e
consulta pós-contrato de até 120 dias (RNF-42), relatórios em 2 ou 5 minutos (RNF-71) e cinco severidades de suporte
com prazo de solução (ADR-0020). Hospedagem: AWS `sa-east-1` (ADR-0011). Apps Capacitor: autenticação pelo RNF-39
(ADR-0030) e sincronização de campo pelo RNF-07 (ADR-0031).

## 1. Desempenho

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-01 | API, leitura: p95 < 300 ms, p99 < 800 ms | Histograma por rota, na carga de referência e em produção | Micrometer, OpenTelemetry, k6 | P2 | Não medido: actuator expõe só `health` (`application.yml`) |
| RNF-02 | API, gravação: p95 < 800 ms, p99 < 1,5 s | Idem | Idem | P2 | Não medido |
| RNF-03 | Busca no cadastro: p95 < 1 s, p99 < 2 s em tenant de 300 mil ligações | k6 com nomes sintéticos | k6, `pg_stat_statements` | P1 | Não atende: `LIKE '%x%'` sem índice trigram (D5) |
| RNF-04 | Listas: no máximo 3 consultas por página; keyset acima de 10 mil linhas | Contagem de statements por requisição | Estatísticas do Hibernate em teste | P1 | Não atende: N+1 e offset com `count(*)` (D4) |
| RNF-05 | Faturamento: 100 mil contas em < 2 h por tenant; p95 da API piora no máximo 20% durante o lote | Lote cronometrado em homologação com k6 em paralelo | k6, métricas do worker | P2 | Não medido: não há lote (X4) |
| RNF-06 | Webhook Pix: resposta < 2 s (p99); baixa conciliada < 60 s (p95) | Recebimento × baixa | Métricas da caixa de entrada | P3 | Não atende: endpoint inexistente |
| RNF-07 | Sincronização de leitura: lote de até 500 leituras confirmado < 5 s (p95); reenvio idempotente pela chave da tarefa (`task_id`), sem segunda conta, segunda baixa de material nem segunda NFAg-e; conta emitida em campo recalculada no servidor com as mesmas versões de parâmetro (ADR-0031, ADR-0032) | k6 com lotes repetidos; vetores comuns no JVM e no emulador Android | k6, JUnit, teste instrumentado Android | P2 (importação), P4 (app) | Não atende: `meter_readings` sem chave de idempotência |
| RNF-08 | Emissão fiscal: 95% autorizadas < 10 min após o fechamento da conta, exceto indisponibilidade do autorizador | Idade da mensagem na fila fiscal | Métricas da outbox | P2 | Não medido |
| RNF-09 | Filas interativas: mensagem mais antiga < 5 min | Gauge por fila | Micrometer | P2 | Não medido |
| RNF-71 | Relatórios e arquivos gerados sob demanda: no nível Setor Público (ADR-0025), concluídos em até 2 min ou 5 min, conforme o contrato; no padrão do produto, sem prazo contratual (ADR-0012, ajuste de 12/10/2026) | Cada relatório do catálogo padrão (ADR-0027) cronometrado na carga de referência contra 2 min, o prazo mais exigente dos editais | k6, métricas do `worker` | P3 | Não atende: não há módulo de relatórios nem exportação PDF/XLSX/CSV (`docs/levantamento/editais/18g-aderencia-operacao-qualidade-bi.md` §1.2) |

## 2. Disponibilidade e continuidade

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-10 | Backoffice 99,5% ao mês (até 3 h 36 min fora); no nível Setor Público (ADR-0025), 99,9% ao mês, com a exclusão da janela de manutenção conforme o contrato | Sonda sintética autenticada a cada minuto e taxa de 5xx | Verificador externo, SLO | P3 | Não medido: só ambiente local (X1) |
| RNF-11 | Portal 99,9% ao mês (até 43 min fora), 24x7 (NR 11) | Sonda na 2ª via e no Pix | Idem | P3 | Não atende: portal inexistente (P2) |
| RNF-12 | Janela de manutenção: domingo, 0h–4h (Brasília), aviso de 72 h, até 4 h/mês; fora do cálculo do backoffice; portal mantém 2ª via e Pix | Registro de janelas | Página de status | P3 | Não atende |
| RNF-13 | Implantação sem indisponibilidade (rolling, migrations expand/contract) | Sonda durante o deploy | CI/CD | P2 | Não atende: sem artefato (X1) |
| RNF-14 | RPO ≤ 15 min (longo prazo: 5 min) | Atraso do arquivamento de WAL; PITR de ensaio | Backup automático e PITR do RDS (ADR-0011) | P0 | Não atende: sem backup nem PITR (X2) |
| RNF-15 | RTO ≤ 4 h (longo prazo: 1 h) | Ensaio cronometrado | Runbook | P0 | Não atende |
| RNF-16 | Restauração testada trimestralmente (mensal quando automatizada), com contagem e checksum de tabelas-chave e teste de RLS no banco restaurado | Ata do ensaio | Job de restauração | P0 (1º ensaio), P3 (mensal) | Não atende |
| RNF-17 | Keycloak com 2+ réplicas, `start --optimized`; queda de uma réplica sem perda de sessão | Teste de derrubada | — | P0 | Não atende: `start-dev` (`docker-compose.yml`, O3) |

## 3. Capacidade e escalabilidade

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-20 | Por cluster: ~50 tenants e 2 milhões de ligações | Carga de referência escalada | k6, métricas do banco | P2 | Não medido |
| RNF-21 | Tetos por porte (ADR-0005): pequeno ≤ 20 mil ligações; médio 20–200 mil; grande > 200 mil em banco dedicado. Alerta a 80% do teto | Ligações ativas por tenant no catálogo | Consulta ao catálogo | P1 | Não atende: catálogo sem porte nem shard (D2) |
| RNF-22 | 300 schemas: migração completa < 30 min; subida da `api` independente do número de tenants | CI noturno | Testcontainers | P1 | Não medido |
| RNF-23 | API horizontal: 3+ réplicas sem afinidade; autoscaling por latência e CPU | Derrubada de réplica sob carga | k6 | P2 | Parcial: API sem sessão; sessão do BFF ainda não existe |
| RNF-24 | Workers escalam pela profundidade e idade de cada fila | Métrica da outbox | Micrometer | P2 | Não atende (X4) |
| RNF-25 | Fila justa: um tenant ocupa no máximo 50% dos slots de uma fila (pequeno: 1 partição); espera de um tenant não cresce com o lote de outro | Teste com dois tenants concorrentes | Teste de integração | P2 | Não atende |
| RNF-26 | Limites por tenant: taxa por tenant, usuário e IP; corpo 1 MB (upload 20 MB); listas `@Size` ≤ 500; `statement_timeout` 5 s (api) e 15 min (worker); `idle_in_transaction_session_timeout` 30 s; cota de armazenamento | Teste de abuso; contagem de 429 | Testes de integração | P0 (timeouts, `@Size`), P2 (cotas) | Não atende: pool sem timeouts (`DataSourceConfig.java:20-27`), sem rate limit (S5) |

## 4. Segurança

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-30 | MFA sempre na plataforma; por tenant configurável, padrão ligado para ADMIN, MANAGER e revelação de documento (ADR-0007) | % de usuários com MFA por papel | Relatório do Keycloak | P0 (plataforma), P1 (tenants) | Não atende: realm sem OTP/WebAuthn (S4) |
| RNF-31 | Nenhum token acessível a JavaScript no navegador: cookie `__Host-`, HttpOnly, Secure, SameSite; refresh com rotação (ADR-0006); nos apps, RNF-39 | E2E que inspeciona o storage | Playwright | P0 | Não atende: `sessionStorage` (`frontend/src/services/auth/oidc.ts:28`) |
| RNF-32 | AES-GCM por campo com AAD de tenant; chave por tenant no AWS KMS (ADR-0011), rotação anual, backup da KEK (forma definida na P0.28, porque o KMS não exporta a chave); disco e backups cifrados | Teste e inspeção | Testes, console do provedor | P0 | Parcial: cifra existe; provedor de produção não (`sigsan.crypto.provider`) |
| RNF-33 | TLS 1.2+ (1.3 preferido), HSTS de 1 ano, TLS até banco e Keycloak, `sslRequired=all` | Varredura | testssl.sh | P0 | Não atende: Keycloak em HTTP; sem `forward-headers-strategy` |
| RNF-34 | CSP estrita sem `unsafe-inline` (o SPA é estático; o único script inline, o de tema, entra por hash, ADR-0017), `frame-ancestors 'none'` e demais cabeçalhos | Varredura em homologação | OWASP ZAP no CI | P0 | Não atende: sem CSP (S5) |
| RNF-35 | CI bloqueia vulnerabilidade alta; imagem não-root com SBOM e assinatura | Pipeline | npm audit, Trivy, Syft, cosign | P0 | Não atende: `maplibre-gl` ^4.7.1 (`frontend/package.json`, S6) |
| RNF-36 | Pentest externo com foco em isolamento antes do go-live; críticos e altos corrigidos; reteste anual | Relatório | Terceiro | P3 | Não atende |
| RNF-37 | Credencial de migração só no job `migrate` | Inspeção da configuração | — | P0 | Não atende: `migratorDataSource` sempre criado (`DataSourceConfig.java:29-39`, S2) |
| RNF-38 | Testes de cobertura de RLS e de isolamento bloqueiam o CI | CI | JUnit | P0 | Atende: `FreshInstallTest` e isolamento no PostgreSQL real |
| RNF-39 | Apps (ADR-0030): login só pelo navegador do sistema, com PKCE (S256) e retorno por App Link ou Universal Link; access token de 5 min; refresh rotativo, guardado só no Android Keystore ou no iOS Keychain e nunca legível pelo JavaScript da WebView; reutilização de refresh revoga a sessão; app de campo só em dispositivo registrado, com recusa imediata de sincronização após o bloqueio e apagamento dos dados locais no contato seguinte; revogação pela área do tenant e pela Central efetiva em até 5 min | Teste instrumentado que inspeciona armazenamento e WebView; teste de revogação e de bloqueio; pentest dos apps | Emulador Android e simulador iOS no CI, ferramenta de análise de app móvel | P4 | Não atende: apps inexistentes |

## 5. LGPD

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-40 | Retenção por categoria em tabela aprovada pelo jurídico e pelo DPO; expurgo por partição. Chamados de suporte e anexos: vigência do contrato mais 5 anos (ADR-0020) | Relatório mensal de expurgo | Job do worker | P1 (tabela), P2 (expurgo) | Não atende: `audit_log` sem partição (`V1__create_core_schema.sql:43`, X3) |
| RNF-41 | Registro de acesso a dado pessoal em lista, busca, detalhe, revelação e exportação | Teste por endpoint | Testes de integração | P1 | Parcial: só o `reveal` é registrado |
| RNF-42 | Fim de contrato: exportação em formato aberto em até 15 dias; carência de 30 dias contados do fim do contrato, para o cliente baixar a exportação e contestar; contestação registrada na carência suspende a eliminação e é respondida pelo Ziip em até 15 dias, depois do que a eliminação segue; eliminação concluída e certificado de descarte emitido em até 30 dias após o fim da carência ou da resposta à última contestação, o que ocorrer depois: até 60 dias do fim do contrato sem contestação e no máximo 75 dias com ela; ordem judicial sempre suspende a eliminação, e o prazo não corre enquanto ela vigorar (ADR-0015; decisões do dono do produto, 12/10/2026, plano v2.3). No nível Setor Público (ADR-0025): exportação em até 10 dias úteis, em CSV, JSON ou XML, com dicionário de dados, manifesto e hash; consulta somente leitura pelo prazo e pelo número de usuários do contrato, até 120 dias após o fim do contrato; quando o contrato exigir prazo maior que o padrão de 30 + 15 + 30 dias, a carência começa no fim da consulta e os demais prazos correm a partir dela; contrato que vede retenção após o fim mantém o padrão | Ensaio com tenant sintético | Console da plataforma | P3 | Não atende (P1) |
| RNF-43 | Crypto-shredding: destruir a chave do tenant torna ilegível o dado cifrado, inclusive em backup; backups expiram em até 35 dias | Ensaio | Cofre/KMS | P3 | Não atende: depende do R1 |
| RNF-44 | Dados de tenant e backups no Brasil (AWS `sa-east-1`, inclusive réplicas e cópias em outra conta, ADR-0011); exceções (telemetria sem dado pessoal; provedor de modelo de linguagem do chatbot, se processar fora do país, ADR-0028) registradas no RIPD | Inventário | — | P0 | Não atende: Terraform em `us-east-1` (O2) |

## 6. Observabilidade

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-50 | Métricas RED por rota e por tenant, Hikari, JVM, filas, `pg_stat_statements` | — | Micrometer, OTel Collector | P0 | Não atende: só `health` |
| RNF-51 | Traces com `tenant_id`, `correlation_id` e `run_id`; amostragem de 10% mais todos os erros e lentas | — | OpenTelemetry | P0 | Não atende |
| RNF-52 | Logs JSON sem dado pessoal; teste falha se CPF, e-mail ou telefone aparecer no log, inclusive o CPF/CNPJ digitado no login do portal e registrado em evento do Keycloak (ADR-0022) | Teste de log | JUnit, filtro no Collector | P0 | Parcial: ECS no perfil `prod` (`application-prod.yml`); sem filtro |
| RNF-53 | SLOs de RNF-01, 02, 06, 10 e 11 com alerta por burn rate (14,4x em 1 h/5 min; 6x em 6 h/30 min) e política de error budget | — | Backend de métricas | P2 | Não atende (O1) |
| RNF-54 | Error tracking, página de status pública e plantão definido; plantão 24x7 para a severidade 1 do nível Setor Público depende da decisão de equipe do P4 (ADR-0023, ADR-0025) | — | — | P3 | Não atende |

## 7. Frontend

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-60 | WCAG 2.1 AA e eMAG 3.1: zero violações `serious`/`critical` do axe por tela; auditoria manual com teclado e NVDA antes do portal | CI e auditoria | vitest-axe, Playwright + axe | P1 (CI), P3 (portal) | Parcial: `vitest-axe` no CI; sem auditoria manual |
| RNF-61 | Web Vitals no p75 real: LCP ≤ 2,5 s, INP ≤ 200 ms, CLS ≤ 0,1; portal medido em celular intermediário com 4G | RUM e laboratório | `web-vitals`, Lighthouse CI | P2 (backoffice), P3 (portal) | Não medido: sem `web-vitals` |
| RNF-62 | JS inicial ≤ 250 KB comprimido; code splitting por rota | Relatório de build | Vite | P1 | Não atende (F1) |
| RNF-63 | Telas utilizáveis de 360 px a 1920 px; tabelas com layout de celular no portal e no atendimento | Testes visuais | Playwright | P3 | Não atende |
| RNF-64 | Duas últimas versões de Chrome, Edge, Firefox e Safari (macOS e iOS), Chrome Android e Firefox ESR | E2E na matriz | Playwright (Chromium, Firefox, WebKit) | P2 | Não medido |

## 8. Carga

| ID | Meta | Como medir | Ferramenta | Fase | Estado (evidência) |
|---|---|---|---|---|---|
| RNF-70 | Carga de referência noturna; regressão acima de 15% no p95 bloqueia a liberação | Comparação com a linha de base | k6 | P2 | Não atende (O4) |
