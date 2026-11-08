# ED-93 — Sala de situação operacional: integração do console nativo

Console operacional NATIVO, fora do BI, em mapa (OPE-22). O painel
operacional de GESTÃO continua no BI da ED-49 (Superset sobre
réplica); este console não é um dashboard do Superset e não consulta
o BI — a fronteira é executável nos dois lados
(`RealtimeConsoleBoundary` na ED-49; `SituationSourceGuard` aqui).

## Arquitetura em três tempos

1. **Fontes → projeção.** Adaptadores das portas de
   `SituationFeeds` leem as fontes reais (ocorrências da ED-92, OS
   da P1.31/ED-36, alarmes e paradas da ED-17, posição de equipes e
   nível de reservatórios pelas respectivas portas) e o
   `SituationProjector` grava o retrato na tabela de estado.
2. **Tabela de estado resumida e nativa** (`ops_situation_state`,
   SQL pendente `ED-93__situation_room.sql`, sem número Flyway):
   uma linha por item visível, com instante da fonte e da projeção.
   É a ÚNICA fonte do console. O `status_label` tem segunda barreira
   no banco (correção D3 da revisão ED-93-REV-1): o CHECK
   `ck_ops_situation_status_by_kind` só admite, por `item_kind`, os
   rótulos que o projetor grava — linha inserida direto com rótulo
   de item já encerrado/resolvido é recusada pelo banco.
3. **Console → tela.** `SituationConsoleService` monta o
   `SituationSnapshot` só da tabela de estado; `SituationMapView`
   é o contrato de dados do mapa (marcador só com coordenadas;
   sem posição = contado, nunca presumido). Controller HTTP, tela
   web e agendamento da projeção são fiação desta integração.

## Latência (RNF — ADR-0012)

A meta do aceite é textual — "segundos a poucos minutos" — e nenhuma
fonte nomeia o número. A implantação DEVE configurar o teto
(`SituationLatencyPolicy`) por tenant/ambiente a partir da decisão
que fixar o número; sem teto configurado, o console recusa operar
(`SITUATION_LATENCY_BUDGET_REQUIRED`, `[SEM FONTE]`). O frescor por
conjunto aparece no retrato (`freshnessByKind`): FRESH/STALE/NO_DATA.
Critério do conjunto (correção D2 da revisão ED-93-REV-1): o frescor
é o do fato MAIS ANTIGO do conjunto (pior caso), o mesmo critério da
sonda de latência — item velho não some atrás de item novo. Nas OS,
"em execução" e "vencida" são predicados independentes (correção
D1): a OS em execução e vencida aparece nos dois conjuntos do
retrato.
A sonda `SituationLatencyProbe` mede o caminho fato → projeção →
tabela → leitura no núcleo; na integração, repetir a medição sobre
o caminho real (banco + HTTP + navegador) — pendência P-ED93-03.

## Indicadores (JOR-19)

Valor, meta e semáforo do console vêm do registro da P3.41 via
`SituationIndicators` — transporte verbatim da apuração, sem fórmula
no console (mesmo desenho da ED-49). A ligação console → indicador
é declarada por tenant em `ops_console_indicator_bindings`; nenhuma
fonte lista os indicadores do console, logo nenhuma nasce pronta.

## Fiação pendente (integração)

1. Adaptadores JDBC das portas de `SituationFeeds` e do
   `SituationStateStore` sobre o SQL pendente numerado; conversão
   dos instantes locais das fontes (ED-92/ED-36) do fuso do tenant
   para `Instant` antes da projeção (o núcleo lê em UTC, convenção
   declarada no `SituationProjector`).
2. Agendamento/acionamento da projeção (intervalo é decisão da
   implantação, amarrada ao teto de latência configurado).
3. Controller + tela do mapa sobre `SituationSnapshot`/
   `SituationMapView`; autenticação/autorização da sala (papéis da
   plataforma).
4. Fontes reais de posição de equipe e de nível de reservatório:
   nenhuma dep define GPS de equipe nem telemetria de nível — as
   portas estão tipadas; ligar às fontes quando existirem, sem
   presumir limiar (reservatório: faixa só contra limiar declarado
   do tenant).
