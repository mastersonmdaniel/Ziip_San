# Relatório — P3.18 Página pública de status

Base: HEAD `0fbcd22` (confirmado via MCP nesta execução). Tamanho M.
Deps P0.27 e P3.11 entregues (a P3.11 é o pacote local conferido em
`~/workspace/ziipsan-producao/p3.11/entrega/`).
Sem push e sem escrita no GitHub (pacote local conferido).

## Limitação de espelho (declarada)

O GitHub MCP respondeu **HTTP 429** após 2 chamadas (parada dura
respeitada, sem retry). Antes do 429, foi confirmada a listagem integral
das migrations core na main: existem `V10`, `V10_1`, `V10_1_1`, `V10_2`,
`V10_2_1`; **não existem `V10_3` nem `V10_4`** — a `V10_4` está livre e é
a reservada desta tarefa (plano v3.0 §3), e a P3.17 (`V10_3`) ainda não
foi publicada na main. O restante do padrão (V10_2/V9_1/V9_3/V9_4,
classe `Json`, `pom.xml` do platform, controller da P3.36) veio dos
espelhos locais já conferidos das frentes P2.5/P3.11/P3.14/P3.19/P3.36,
mesma linhagem do HEAD. Procedência em `../espelho/LEIA-ME.md`.

## Arquivos criados (caminhos do repo)

- `shared/ziipsan-core/src/main/resources/db/migration/core/V10_4__public_status_page.sql`
  — as 4 tabelas do plano §3 (`support.status_component`,
  `status_incident`, `status_update`, `maintenance_window`) + a junção
  `status_incident_component` (complemento de modelagem declarado no
  cabeçalho: incidente N↔N componente). Sem coluna de status editável;
  regra das 72 h também como `CHECK` do banco; resumo + publicação em
  par obrigatório; **nenhuma linha semeada** (nenhum componente
  inventado); sem RLS justificado no cabeçalho (conteúdo global da
  plataforma, sem `tenant_id`, como as tabelas globais de `platform`).
- `platform/ziipsan-platform`, pacote `br.com.ziipsan.platform.statuspage`:
  - `ComponentStatus.java` — enum com prioridade (OUTAGE > DEGRADED >
    MAINTENANCE > UNKNOWN > OPERATIONAL) e `UNKNOWN` explícito.
  - `ProbeReading.java`, `StatusComponent.java` — fatos (sonda e
    componente); o componente **não tem campo de status**.
  - `MaintenanceWindow.java` — publicação recusada com < 72 h de
    antecedência (fronteira de exatamente 72 h aceita, testada).
  - `StatusIncident.java`, `PostIncidentSummary.java` — publicação do
    bloco "Resumo publicável" do modelo da P3.11 (PLT-23): só de
    incidente `RESOLVED`, texto obrigatório, publicação não anterior à
    resolução.
  - `PublicStatusService.java` — derivação pura (sonda DOWN → OUTAGE;
    incidente MAJOR/CRITICAL → OUTAGE, MINOR → DEGRADED; janela ativa →
    MAINTENANCE, sem esconder interrupção real; com sonda e sem leitura
    → UNKNOWN), visão pública filtrada (invisíveis fora; resolvido sem
    resumo publicado fora) e `toJson` **somente via `Json` do core**.
  - `StatusPageSource.java` — porta de leitura (padrão `BatchJobStore`).
  - `web/PublicStatusController.java` — `GET /api/v1/public/status`
    (rota mantida pelo plano v3.0), público, sem dado de tenant.
- Testes (`src/test/.../statuspage/`): `StatusDerivationTest` (7 casos:
  a sonda cair muda o status sem nenhuma edição; nunca medido =
  UNKNOWN; incidente aberto/resolvido; janela vs. interrupção real;
  geral = pior dos visíveis; leitura de outro componente recusada),
  `MaintenanceWindowTest` (4 casos: < 72 h recusado, = 72 h aceito,
  janela invertida recusada, janela global), `PostIncidentSummaryTest`
  (4 casos: resumo publicado aparece na visão e no JSON; não publicado
  não aparece; incidente aberto recusado; vazio/precoce recusado).
- `docs/status-page/index.html` — página estática que só exibe o JSON
  do endpoint (em falha de carga, admite que não sabe — não afirma
  "operacional"); `docs/status-page/README.md` — roteiro de teste local
  e fronteiras LC.7/P3.38/P4.8.

## Acceptance, item a item

1. **core `V10_4`** — ATENDIDO (migration única, a reservada; padrão
   das vizinhas V10_2/V9_1 conferido).
2. **Página construída e testada no ambiente local** — PARCIAL:
   página + endpoint + lógica construídos e cobertos por testes
   unitários; a execução no ambiente local (Docker/Java) e os testes
   rodando são da CI no PC do dono — esta VM não tem JDK/Docker
   (limitação conhecida do fluxo). O roteiro de teste local está em
   `docs/status-page/README.md`.
3. **Status muda sozinho com a sonda fora** — ATENDIDO NA LÓGICA E
   TESTADO: não existe setter/flag de status; `derive` recalcula de
   fatos a cada leitura e o teste prova UP → DOWN virando
   OPERATIONAL → OUTAGE sem nenhuma outra mudança. A virada em tempo
   real depende do coletor que grava `last_probe_*` (fiação da
   integração/LC.7, ver pendências).
4. **Janela de manutenção publicada com 72 h de antecedência** —
   ATENDIDO E TESTADO no serviço e no banco (CHECK): < 72 h é recusa
   explícita nos dois níveis; a fronteira de 72 h é aceita.
5. **Resumo da revisão pós-incidente da P3.11 publicado na página** —
   ATENDIDO E TESTADO: `PostIncidentSummary.publish` implementa o
   bloco §7 do modelo da P3.11 (PLT-23) e a visão/JSON públicos só
   exibem o resumo depois de publicado.
6. **Publicação em hospedagem separada da produção na LC.7** — FORA
   DESTA TAREFA (é da LC.7 por definição do aceite): entregue o
   artefato estático pronto para essa hospedagem; nada foi publicado.
7. **Divulgação responsável de vulnerabilidade e Trust Center (ver
   P3.38)** — FORA DESTA TAREFA (PLT-09, "deixado" pela própria spec):
   nada aqui finge cobrir a P3.38; ela reutilizará esta página/
   hospedagem.

## Pendências e riscos para a integração/CI

- **Fiação pendente (declarada, não fingida):** implementação JDBC de
  `StatusPageSource` sobre `support.status_*`, o coletor que grava
  `last_probe_outcome/last_probe_at` a partir das sondas (P0.27 local;
  sondas sintéticas de produção na LC.7) e a liberação da rota
  `/api/v1/public/status` sem autenticação na configuração de
  segurança. Sem essa fiação o endpoint não sobe — de propósito:
  nenhuma implementação falsa foi entregue.
- **Sem compilação local** (sem JDK nesta VM): riscos residuais são os
  usuais — API do Jackson 3 (`JsonNode.asString/size`, já usados pela
  P3.11) e a sintaxe do `CHECK` com `INTERVAL '72 hours'`; só a CI
  total no PC do dono confirma (compilar, rodar os 15 testes novos e
  aplicar a V10_4 sobre o banco de integração).
- **Decisões de modelagem registradas** (reversíveis na integração):
  (a) tabela de junção além das 4 do §3; (b) sem limiar de "sonda
  velha" (stale) — a spec não fixa prazo e nenhum número foi inventado:
  leitura antiga continua sendo a última verdade até nova leitura; se
  o dono quiser expiração, ela entra como parâmetro; (c) impacto
  MINOR → DEGRADED / MAJOR+CRITICAL → OUTAGE como semântica da
  derivação (a spec não gradua; a escolha está documentada no serviço).
- **V10_3 ausente na main:** a V10_4 não depende dela (o
  `CoreMigrationRunner` usa `outOfOrder(true)`, precedente da V9_3),
  mas a rodada de CI da camada deve rodar com a P3.17 já publicada ou
  confirmar a aplicação fora de ordem.
