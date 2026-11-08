# ED-54 — GIS-11: traçados, isolamento e manobras

## O que a GIS-11 faz

Sobre a topologia persistida da GIS-03 (P2.21, com a correção M3) e a
base da ligação da GIS-05 (P2.23):

1. **Traçado montante/jusante** (`ManeuverGraph`): BFS dirigida pelo
   `flow_direction` da V13_2 (`WITH_GEOMETRY`, `AGAINST_GEOMETRY`,
   `BOTH`; `UNKNOWN` não gera aresta dirigida — sentido não se
   presume). Arco fora de operação não entra em traçado.
2. **Setores de manobra pré-calculados** (`ManeuverSectors`):
   partição da rede removendo os nós das válvulas **operáveis**;
   válvula inoperável não delimita setor. Códigos determinísticos
   `MS-01…` pela ordem do menor código de nó.
3. **Isolamento** (`IsolationEngine`): expansão não dirigida a
   partir do trecho de trabalho até as primeiras válvulas operáveis
   abertas (= válvulas a fechar); válvula inoperável é
   **atravessada** e reportada; válvula já fechada barra sem OS.
   Fonte alcançada sem válvula no caminho → isolamento impossível
   com motivo nomeado, sem área afetada afirmada. Imóveis/economias
   pelas conexões funcionais (SG-852), **sensíveis primeiro**,
   essenciais depois.
4. **Plano de manobra** (`ManeuverPlanService`): aviso **só pela
   ED-41** (porta `NoticePort`, evento proposto
   `PLANNED_INTERRUPTION_NOTICE`) e OS de fechamento/reabertura
   **em lote só pela ED-36** (porta `WorkOrderBatchPort`, finalidade
   `VALVE_CLOSE`/`VALVE_REOPEN`; reabertura depende do lote de
   fechamento). A baixa de cada OS grava o estado da válvula; o
   período de desabastecimento **por imóvel** abre no isolamento
   efetivo (última baixa de fechamento) e fecha na reabertura
   completa (última baixa de reabertura).
5. **Interrupção programada da ED-35** (`PlannedOutageAreaService`):
   a área afetada passa a ser a do isolamento — unidades afetadas,
   essenciais da P1.3 dentre elas e sensíveis — e o aviso usa o
   mesmo pedido da ED-41.
6. **Áreas de influência, bacias e polígonos** (`InfluenceBasinService`,
   SG-887/SG-889): bacia = montante; influência = jusante; bacias por
   fonte com órfãos visíveis em `SEM_FONTE`; polígono de roteamento
   = envolvente convexa dos nós da área (delimitação da área de
   trabalho — **não** é rota de equipe).

## Recorte O8-1 (DECISOES-DO-DONO)

O solver pgRouting para deslocamento de **equipes** segue bloqueado
por ADR (O8-1, herdado da ED-36). Esta entrega é só de **rede**
(traçado/isolamento/manobra), domínio da exceção do ADR-0034 — o
mesmo recorte que a ED-36 declara para rede. Nenhum solver de
deslocamento foi implementado; o `gis_routing_polygon` do SQL é
geometria de área, não caminho.

## Persistência

SQL sem número em `db/pendente/ED-54__gis_maneuver.sql` (regra da
cascata): setores pré-calculados, plano e suas válvulas, período por
imovel, funções `gis_trace_downstream`/`gis_trace_upstream`
(CTE recursiva), `gis_routing_polygon` (PostGIS) e a view
`gis_maneuver_affected_units`. PostGIS/pgRouting reais são prova da
CI (pendência P-ED54-02); o núcleo é provado sem banco.

## Fronteiras

- Exercício de válvulas (OPE-09) = ED-56 — aqui a operabilidade é
  fato de entrada do retrato.
- Rodízio/crise hídrica (OPE-10) = ED-91; ocorrência de rede
  (OPE-16) = ED-92.
- Publicador da área suja (PEND-01 da P2.21) segue pendente: o
  recálculo de setores expõe o cálculo puro e a tabela; quem dispara
  o recálculo é a mesma decisão de integração da P2.21.
