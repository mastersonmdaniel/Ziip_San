# ADR-0013 — PostgreSQL 17 e PostGIS 3.5 no P0

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17 (achado X2); `docker-compose.yml`; `scripts/reset-it-db.sh`
- **Ajuste de 12/10/2026 (v2.7), antes de qualquer implementação:** "todos os ambientes durante o P0" (item 1) passa
  a significar o ambiente local de desenvolvimento, homologação e demonstração e o CI, porque não há ambiente na nuvem
  até o lançamento comercial (ADR-0033); a conferência do PostgreSQL 17, do PostGIS 3.5 e do `pg_partman` na
  hospedagem é documental no P0 e prática no ensaio de implantação na AWS (plano LC.1); `pg_stat_statements` e
  arquivamento de WAL "em homologação" valem para a homologação local; "antes do primeiro dado real" passa a ser antes
  do lançamento, e o "go-live piloto" do Contexto lê-se como o lançamento; decisão do dono do produto.

## Contexto

O ambiente usa `postgis/postgis:15-3.4` (`docker-compose.yml`, `scripts/reset-it-db.sh`). O PostgreSQL 15 sai de
suporte em novembro de 2027, poucos meses após o go-live piloto. Atualizar a versão principal com dado real exige
`pg_upgrade` ou replicação lógica, janela e reteste de RLS. Hoje não há dado de cliente.

## Decisão

1. Adotar PostgreSQL 17 com PostGIS 3.5 em todos os ambientes durante o P0, antes do primeiro dado real.
2. Extensões usadas (`uuid-ossp`, `pgcrypto`, `pg_trgm`, `btree_gin`, `btree_gist`, `postgis`) e `pg_partman` são
   requisito da hospedagem escolhida no ADR-0011.
3. Versões principais seguintes entram por ADR, com ensaio em homologação.

## Consequências

- Suporte até novembro de 2029; backup incremental nativo, melhor uso de memória no vacuum e slots de replicação
  lógica com failover.
- Volumes locais de desenvolvimento são recriados; nenhum dado é preservado.
- Imagens de CI, Testcontainers e script de integração mudam juntos; `FreshInstallTest` e o teste de isolamento
  precisam passar na nova versão.
- A imagem `timescaledb:latest-pg15` do compose fica congelada pelo ADR-0010 e não acompanha a mudança.

## Alternativas consideradas

- **Manter 15 e atualizar depois do go-live.** Rejeitada: upgrade com dado real e prazo curto de suporte.
- **PostgreSQL 18.** Rejeitada por ora: ecossistema de extensões e ofertas gerenciadas menos testados; pode vir por
  ADR posterior.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Trocar a imagem no compose e no script de integração; recriar volumes locais | P0 |
| Rodar a suíte completa e os testes de RLS na nova versão | P0 |
| Conferir PG 17, PostGIS 3.5 e `pg_partman` na hospedagem escolhida | P0 |
| Habilitar `pg_stat_statements` e arquivamento de WAL em homologação | P0 |

## Ajuste de 31/10/2026 (decisão do dono — supersede parcial, texto original acima preservado)

- **Versão do PostGIS atualizada de 3.5 para 3.7.0.** Motivo: o repositório PGDG entrega PostGIS 3.7.0; a versão 3.5 não está disponível nele. O major do PostgreSQL segue 17, sem mudança.
- **Verificação local:** PostgreSQL 17.11 + PostGIS 3.7.0 via PGDG/host, confirmado por `PostGIS_Full_Version()`; a verificação do ci-linux em instância dedicada PG17 passa a valer contra 3.7.0.
- **Imagem Docker pendente:** `postgis/postgis:17-3.7` ainda NÃO publicada — verificado na API do Docker Hub em 31/10/2026 (consulta por `name=17-3.7` e `name=17-3.7.0`: 0 resultados; só RCs de 3.7 para PG18/19, nenhum para PG17). Nenhuma tag foi inventada; a troca da imagem no `docker-compose.yml` e no `scripts/reset-it-db.sh` (hoje `postgis/postgis:15-3.4`) fica PENDENTE até a tag existir, e a P0.4 quanto à imagem do compose não pode ser dada como cumprida antes disso. Não usar `17-3.6` como substituto: contraria a decisão do dono (3.7.0).
- **Diferença 3.5 → 3.7.0 a observar:** a partir do PostGIS 3.7, `address_standardizer` e `postgis_tiger_geocoder` deixam de ser fornecidos pelo PostGIS. Nenhum teste/migration do projeto foi encontrado dependendo dessas extensões (verificação em 31/10/2026: suíte completa verde em PG17/3.7.0, 973 testes); se alguma dependência surgir, será registrada em vez de mascarada.
- **Documentos atualizados por esta decisão:** plano canônico (P0.4 e § de decisões), `docs/arquitetura/ARQUITETURA-ALVO.md`, `scripts/ci-linux.sh` e este ADR (esta seção). Arquivos históricos (ADRs 0011/0024/0033/0034, levantamentos e `docs/plano-v3/`) mantêm o texto original com 3.5 e leem-se como supersedidos por esta seção.
