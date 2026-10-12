# ADR-0035 — Planejamento sem gestão de equipe; GeoServer para publicação WMS

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto (cartão de 12/10/2026)
- **Afeta:** ADR-0029 (revogado); ADR-0010 (exceção do GeoServer); ADR-0028 (item b.1); ADR-0034 (ED-67); plano v2.12;
  consolidado 18; `docs/arquitetura/GIS-CADASTRO-TECNICO.md`

## Contexto

Os planos v2.4 a v2.11 passaram a calcular capacidade (pessoas-equivalentes, contratações, gatilhos, folga) e a
reprogramar datas por falta de gente. O dono do produto declarou que o objetivo é fazer software, não gerir equipes, e
que o planejamento deve tratar do que será feito e de como.

Na mesma rodada, a publicação WMS da onda B precisava de um servidor de mapas, o que é exceção ao congelamento de
infraestrutura do ADR-0010.

## Decisão

1. A gestão de equipe e de capacidade sai do escopo do plano. O plano descreve escopo, desenho, ordem, dependências e
   critérios de aceite. O tamanho das tarefas (P/M/G e semanas-pessoa) fica só como indicação de esforço. As datas das
   fases e dos marcos são metas de calendário, sem cálculo de capacidade. O ADR-0029 fica revogado e mantido como
   histórico.
2. A publicação WMS da onda B (ED-67) usa o **GeoServer**, registrado como exceção no ADR-0010. A OGC API Features
   continua na própria aplicação. O isolamento por tenant e a simbologia seguem o ADR-0010 (seção Exceções) e o
   `GIS-CADASTRO-TECNICO.md` §7.2.

## Consequências

- O plano fica mais curto e estável; mudanças de pessoal não geram nova versão do plano.
- Riscos de prazo deixam de ser tratados no plano; o acompanhamento de ritmo, se houver, fica fora dele.
- Mais um componente a operar a partir da onda B (GeoServer), com isolamento por tenant a testar.

## Alternativas consideradas

- Manter a análise de capacidade no plano: rejeitada pelo dono.
- QGIS Server para WMS: preterido em favor do GeoServer, escolhido pelo dono.
- Publicar WMS só sob demanda: rejeitada; a publicação OGC entra na onda B (ADR-0034, ajuste v2.11).

## Tarefas derivadas

- ED-67 (onda B): GeoServer com camadas por tenant, estilos SLD alinhados à simbologia do projeto QGIS gerado e teste de
  ausência de acesso cruzado entre tenants.
