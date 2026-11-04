# P1.34 — Carga do catálogo de normas (área "Normas e legislação")

- **Fonte:** `docs/normas/catalogo-normas.json` (schema_version 1, 1.676 itens).
  Metadados e resumos em palavras próprias; o texto das normas NÃO é
  reproduzido em nenhum campo carregado.
- **Destino:** `platform.cert_standard` e `platform.cert_standard_edition`
  (core V9_8, P1.32), pela migration repetível
  `shared/ziipsan-core/src/main/resources/db/migration/core/R__norm_catalog.sql`.
- **Gerador:** `scripts/normas/generate-norm-catalog-load.py` (saída
  determinística; não edite a R__ à mão, regenere). A P1.34 NÃO cria
  migration versionada nova, como o header da V9_8 exige.
- **Carga:** 1.640 linhas (1.676 menos 36 excluídas, abaixo). Por classe:
  REGULATORY 795, VOLUNTARY 67, REFERENCE 738, EXPORT_PROFILE 40. Por
  selo: OFICIAL 1.322, SECUNDARIA 305, NAO_CONFIRMADA 13. Por situação
  (mapeada para os 4 valores do CHECK da V9_8): VIGENTE 1.431,
  VIGENCIA_FUTURA 14, REVOGADA 105, SUBSTITUIDA 90 — antes da transição
  de carga, que já vira as datas chegadas (ver abaixo).
- **Chave:** `code = catalog_key = id` estável do JSON. O JSON tem itens
  com o mesmo número e ano (ex.: NBR 12212 duas vezes), logo derivar o
  código de número/ano colidiria no UNIQUE de `code`.
- **Resumo:** `scope_summary` + "Nota de situação: " + `status_note`
  (quando houver nota). A situação original do JSON (10 valores) é
  mapeada — VIGENTE/CONFIRMADA→VIGENTE, VIGENCIA_FUTURA→VIGENCIA_FUTURA,
  REVOGADA/CANCELADA/NAO_E_NORMA_EM_VIGOR→REVOGADA,
  SUBSTITUIDA→SUBSTITUIDA, PARCIALMENTE_REVOGADA/EM_REVISAO→VIGENTE,
  A_CONFIRMAR→VIGENTE com selo NAO_CONFIRMADA — e a nota preserva o
  detalhe original. `module` = módulos afetados unidos por vírgula.
- **Edição:** uma por norma (`edition_year`, senão `year`, senão `s/e`
  — 5 itens sem edição declarada). `cert_clause` NÃO recebe linhas
  aqui: o JSON não traz mapa de cláusulas; o mapa por edição, em
  palavras próprias, é da ED-76, a partir dos textos comprados.
- **Idempotência:** upsert por `catalog_key` e por
  `(standard_id, edition)`; reexecutar não duplica. Linhas retiradas de
  versão futura do catálogo NÃO são apagadas (ativações de tenants
  referenciam o código). A R__ tem guarda de contagem (1.640, sem
  contar linhas `syn-` de teste) que falha alto se o total divergir.
- **Consulta:** `GET /api/v1/certification/standards` aceita
  `standardClass`, `area`, `module` (token inteiro, nunca substring),
  `status` (só os 4 valores do CHECK) e `agency`; a ativação continua
  por código (`GET /standards/{code}`), validada contra o catálogo.

## O que fica SEM carga e por quê (ABNT — compra pendente)

A compra das 33 normas ABNT da lista fechada da P1.28
(`docs/arquitetura/P1-28-LISTA-FECHADA-NORMAS-ABNT.md`) segue
**PENDENTE — NÃO COMPRADA**, sem aprovação do dono. Nada foi comprado,
nenhum texto de norma entra no repositório, e as **36 linhas do JSON
correspondentes a essas 33 normas NÃO entram no catálogo do produto**
enquanto a compra não for decidida:

> abnt-nbr-10004-1-2024, abnt-nbr-10004-2-2024, abnt-nbr-10004-2024,
> abnt-nbr-10160-2026, abnt-nbr-12208-2020, abnt-nbr-12209-2011,
> abnt-nbr-12212, abnt-nbr-12212-2017, abnt-nbr-12213-1992,
> abnt-nbr-12214-2020, abnt-nbr-12216-1992, abnt-nbr-12217-1994,
> abnt-nbr-12218-2017, abnt-nbr-12244-2006, abnt-nbr-12586-1992,
> abnt-nbr-12587-1992, abnt-nbr-14486-2000, abnt-nbr-15561-2024,
> abnt-nbr-15750-2024, abnt-nbr-15802-2010, abnt-nbr-17076-2024,
> abnt-nbr-17287-2026, abnt-nbr-5647-1-2025, abnt-nbr-5667-1-2006,
> abnt-nbr-5667-2-2006, abnt-nbr-5667-3-2006, abnt-nbr-7362-2025,
> abnt-nbr-7367-1988, abnt-nbr-7665-2025, abnt-nbr-7675-2026,
> abnt-nbr-8890, abnt-nbr-8890-2020, abnt-nbr-9648-1986,
> abnt-nbr-9649-1986, abnt-nbr-iso-21138-1-2021,
> abnt-nbr-iso-21138-3-2021.

São 36 linhas (não 33) porque o catálogo duplica NBR 12212 e NBR 8890
e traz o item combinado 10004-1/10004-2, que cobre as duas partes a
comprar. As demais 228 referências ABNT do JSON entram SOMENTE como
metadados (número/edição) + resumo em palavras próprias. Quando (e se)
a compra for aprovada e feita, a curadoria re-inclui os 36 itens
retirando-os da tabela de exclusão do gerador, em nova versão da carga.

## Transição automática VIGENCIA_FUTURA → VIGENTE

- **Regra:** norma com `status = VIGENCIA_FUTURA` e `effective_from`
  não nulo passa a `VIGENTE` quando `effective_from <= hoje`. Só esse
  status muda; REVOGADA, SUBSTITUIDA e VIGENTE nunca são tocadas.
- **Datas de efeito:** o JSON não tem campo estruturado de data; só os
  14 itens em vigência futura ganham `effective_from`, curado da
  `status_note` de cada um (tabela no gerador). 13 têm data; 1
  (nt-2026-001-rtc-nfag) não tem data fixada na fonte e fica NULL: a
  transição aguarda uma versão futura do catálogo. `superseded_by_id`
  fica NULL para todas as SUBSTITUIDA: o JSON não traz sucessor
  estruturado, e ligar por palpite a partir de texto livre seria
  inventar vínculo regulatório.
- **Duas camadas, mesma regra:** (1) a R__ aplica a transição no fim
  da carga, para o provisionamento já nascer correto (ex.: a
  Deliberação ARSESP 1.836/2026, com efeito em 23/10/2026, já nasce
  VIGENTE num provisionamento posterior a essa data); (2) o serviço
  `NormEffectivenessService` (core, relógio injetável para teste com
  data simulada) aplica a regra sob advisory lock transacional, e o
  scheduler `NormEffectivenessScheduler` o dispara diariamente.
- **Scheduler (padrão Central, P1.17):** só existe com
  `ziipsan.norm.effectiveness.enabled=true` (padrão desligado);
  intervalo por `ziipsan.norm.effectiveness.interval-ms` (padrão
  86.400.000, um dia). Desligar o job atrasa a transição, nunca a
  perde, porque a regra é por data. A escrita do job usa o GRANT de
  coluna `UPDATE (status, updated_at)` da própria R__ (a V9_8 concede
  só SELECT a `ziipsan_app`).
- **Alerta antecipado:** `upcomingEffective(hoje, janela)` lista as
  normas em vigência futura com data dentro da janela (padrão 30 dias);
  o scheduler registra a lista em WARN. A entrega do alerta ao
  responsável do tenant ainda não tem canal no módulo de certificação
  — o dado já sai do serviço, pronto para o notificador quando ele
  existir. Essa é a ressalva central desta frente quanto a alertas.

## Não entregue nesta frente (declarado, não esquecido)

- `norm_id` opcional nas tabelas com referência normativa em texto
  livre (`tariff_versions.regulatory_act`, `collection_policies.norm_source`,
  `holidays.source`, `surcharge_indexes.source`): exigiria migrations
  versionadas novas, vedadas para a P1.34 — fica para frente própria.
- Norma própria do tenant (classe d do plano): exige tabelas/rotas de
  tenant, fora do escopo desta carga global.
- Mapa de cláusulas (`cert_clause`): ED-76, após a compra.
- Notificação push do alerta ao responsável do tenant: sem canal no
  módulo; log WARN + serviço de consulta entregues.
