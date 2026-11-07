# P3.26 — GIS-07: importador do cadastro técnico (DWG via DXF, DXF, SHP, GPKG)

**Pacote de entrega** (caminhos do repo `mastersonmdaniel/Ziip_San`, base main HEAD `0fbcd22`; **nenhum push feito**).

## Espelho e o HTTP 429 (declaração)

O espelho via MCP foi iniciado em lote: a 1ª chamada (`get_file_contents` do diretório
`intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/`) respondeu OK confirmando o HEAD
`0fbcd22` e os arquivos V13_1/V13_2/R__; a 2ª chamada (ADR-0033) devolveu **HTTP 429** e, conforme a
instrução da tarefa, **as chamadas MCP pararam**. O trabalho seguiu com os espelhos locais das
P2.21/P2.22 (mesmo HEAD `0fbcd22`), o documento `GIS-CADASTRO-TECNICO.md` (§3.4, §9.1, §9.2, §12 item 8,
linha GIS-07 do §13.2) e as fontes em `~/workspace/ziipsan-retomada/fontes/`. Evidência em
`~/workspace/ziipsan-producao/p3.26/espelho/EVIDENCIA-MCP.md`. O ADR-0033 foi lido pelos trechos do
documento GIS e do índice local da P3.7, não pelo arquivo integral via MCP.

## Decisão de numeração da migration (evidência + coordenação com a P2.23)

A spec reservava `V13_7`, **ocupada** (fix da integração P1c). Série V13_x conferida: V13_1–V13_7 no
repo (HEAD `0fbcd22`), V13_8 = P2.21 e V13_9 = P2.22 (pacotes locais entregues). A tarefa paralela
**P2.23 toma a primeira versão livre seguinte (V13_10)**. Seguindo o precedente do projeto (slot
ocupado → próxima versão livre da série, sem editar migration aplicada), a P3.26 entrega **uma única
migration nova: `V13_11__gis_import_staging.sql`**. ⚠️ **Coordenação para o agente principal
reconciliar antes do push:** confirmar que a P2.23 de fato pousou como V13_10; se ela tiver tomado
outro número, renumerar esta para a próxima livre — nunca reaproveitar slot.

## Arquivos (9)

**Banco — `intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/`**
- `V13_11__gis_import_staging.sql` (novo; **parse OK no parser real do PostgreSQL via pglast, 29
  comandos**): tabelas `gis_import_profile`, `gis_cad_layer_map`, `gis_import_batch`, `gis_import_raw`,
  `gis_context_feature`, `gis_quality_report` (RLS via `register_tenant_table`, FKs `(tenant_id, …)`);
  seeds por tenant dos perfis CORSAN_ANEXO_A e GENERICO; guarda do lote (máquina de estados + SRS +
  grade do IBGE + anonimização, com `search_path` fixado no padrão V13_8/V13_9); `gis_import_clean`
  (limpeza em conjunto com contagens antes/depois); `gis_import_extract_labels` (rótulo → arco mais
  próximo, texto não interpretado guardado); `gis_import_publish` (pacote IMPORT submetido + feições
  PLANNED + itens INSERT).

**Java — `intelligence/ziipsan-gis`, pacote `importer` (novo)**
- `GisImportRules` (regras puras: conversão assistida, SRS/grade, anonimização, estados),
  `GisImportDtos`, `GisImportService` (JdbcTemplate; JSON só via `Json` do core; tenant/usuário do
  contexto; traduz recusas do banco), `GisImportContext`.
- `web/GisImportController` (`/api/v1/gis/imports`: lotes, staging, map, labels, clean, contexto,
  quality-report, publish, profiles/layer-maps) e `web/GisImportRoles` (`gis:import-read/edit`).

**Testes**
- `GisImportRulesTest` (unitário, 6 testes): DWG/ODA, DGN v8, hash, SRS/grade, anonimização, estados.
- `Gis07ImportIT` (app, padrão `Gis03TopologyIT`/`Gis04ChangeSetIT`, migrations até V13_9 + V13_11):
  7 testes — conversão assistida, hash duplicado, SRS/grade, staging×cadastro+perfis, limpeza com
  contagens, publicação só via pacote IMPORT revisado (autor não aplica; revisor aplica; republicação
  recusada), contexto + recusa de camada pessoal sem método.

**Sem JDK/Maven/PostgreSQL nesta VM: nada compilou nem rodou — a prova é a CI total no PC do dono,
por camada, como no restante do projeto.** Verificação estática feita aqui: pglast na V13_11 e
balanceamento de chaves/parênteses de todos os Java.

## Cobertura do acceptance (item a item)

| Item | Estado |
|---|---|
| DWG convertido pelo ODA File Converter na etapa assistida, com hash do original e do DXF | ✅ modelo/registro: CHECK do lote exige `converted_format=DXF`, `conversion_tool=ODA_FILE_CONVERTER` e os dois hashes SHA-256; a conversão em si é etapa assistida externa (ADR-0028 b) — registrada e validada, não executada (declarado no código e aqui). IT + teste unitário |
| Staging sem tocar no cadastro | ✅ tudo entra em `gis_import_raw`; IT prova `gis_arc`/`gis_node` vazios após staging |
| Arquivo sem SRS recusado ou com escolha registrada | ✅ guarda do lote: transição a STAGED exige EPSG + `srs_source`; escolha do operador exige o responsável (`srs_declared_by`). IT |
| SAD69 e Córrego Alegre convertidos pela grade do IBGE | ⚠️ **parcial por desenho honesto** — EPSG 4618/4225 só avançam com `datum_transform=IBGE_GRID` e a referência da grade registrada no lote (IT + unitário); a aplicação da grade em si é do worker/GDAL-PROJ fora do banco e **nenhuma grade foi inventada**; o teste de resíduo contra arquivo de referência do §13.2 fica para a homologação com o arquivo real |
| Mapeamento de camadas salvo por cliente e perfis Corsan Anexo A e genérico com amostras sintéticas | ✅ `gis_import_profile`/`gis_cad_layer_map` por tenant; seed CORSAN_ANEXO_A com a lista integral de camadas do §9.2 — pré-mapeadas só as 6 com tipo conferido no catálogo de fábrica do espelho (A_REDE→`gis01:WATER_MAIN`, A_RAMAIS→`WATER_SERVICE_LINE`, A_HIDRANTE→`HYDRANT`, E_REDE→`GRAVITY_SEWER`, E_POCO_VISITA→`MANHOLE`, E_RAMAL→`SEWER_SERVICE_LINE`); as demais nascem sem alvo para o tenant completar (nada inventado) e a publicação recusa camada FEATURE sem alvo. GENERICO nasce vazio. Amostras sintéticas nas ITs. **Saneago não semeado** (§9.2 manda validar a IN 00.0229 antes — pendência) |
| Limpeza topológica com contagem antes e depois | ✅ `gis_import_clean` em conjunto no staging: `ST_MakeValid`, duplicatas geométricas por camada, segmentos abaixo da tolerância do lote; `counts_before`/`counts_after` (JSONB) no lote. IT confere as contagens. Snap/quebra nos nós (ST_Node) acontecem na publicação pelos gatilhos da GIS-03 sobre as feições do pacote (desenho do §9.1) |
| Relatório ISO 19157 no pacote | ✅ `gis_quality_report` (JSONB, um por lote) copiado para o `quality_report` do pacote IMPORT na publicação; publicação exige o relatório. ⚠️ O **PDF** do relatório (§9.1 etapa 6) não foi gerado: nenhuma biblioteca de PDF foi conferida no POM do módulo GIS — pendência, sem dependência inventada |
| Publicação só por pacote `IMPORT` revisado | ✅ `gis_import_publish` só cria pacote `IMPORT`/`IMPORT` SUBMITTED + feições PLANNED (`CAD_IMPORT`/`IMPORT`, `source_batch_id`/`source_ref`); quem aplica é o `gis_apply_change_set` da GIS-04 após revisão por outra pessoa. IT prova: 0 feições em operação antes, autor recusado, revisor aplica |
| 50 km sintéticos em até 2 h | 🔗 **pendência de medição real** (critério de homologação/perf na máquina de referência) — não medido nem fingido; o desenho (limpeza em conjunto, sem gatilho por linha no staging) é o que o §9.1/risco 4 prescreve |
| Reimportação sem duplicar | ✅ UNIQUE `(tenant, original_sha256)` no lote (IT) + UNIQUE parcial `(batch, camada, source_ref)` no staging + republicação do lote recusada pela máquina de estados (IT prova 1 feição só) |
| Camadas de contexto importadas | ✅ `gis_context_feature` (NEIGHBORHOOD/BLOCK/PARCEL/STREET) com serviço/endpoint próprios; IT importa bairro e lote. Linhas cruas mapeadas como CONTEXT não viram feição de rede na publicação |
| DGN v8 só depois da conversão assistida para DWG ou DXF, com hash do original e do convertido | ✅ mesmo CHECK/regra do DWG, com `conversion_tool=ASSISTED_EXTERNAL`. IT + unitário |
| Rede real do piloto na homologação local; ligações/ramais/imóveis só sintéticos ou anonimizados; lote com camada pessoal sem método registrado recusado (ADR-0033) | ⚠️ **regra implementada e testada; homologação em si é externa** — flag `pilot_homologation` no lote; CHECKs em `gis_cad_layer_map` e `gis_context_feature` + guarda de publicação recusam camada pessoal sem método (`SHIFTED_WITHIN_BLOCK`/`AGGREGATED_BY_BLOCK`/`SYNTHETIC`); IT cobre as recusas por SQL direto. A importação da rede real do piloto é ato de homologação local (GL), não desta entrega |

## Pendências e riscos (ordenados)

1. **Numeração V13_10 × V13_11**: reconciliar com a entrega da P2.23 antes do push (ver decisão acima).
   Pela mesma razão, a IT lista as migrations até a V13_9 e salta para a V13_11 — na integração, a
   V13_10 da P2.23 entra na lista da `Gis07ImportIT`.
2. **Worker de leitura GDAL** (DXF/SHP/GPKG/DGN v7 → linhas do staging já em 4674, com a grade do IBGE
   aplicada e o resíduo conferido): não existe nesta entrega — a api recebe as linhas prontas
   (`POST /batches/{id}/rows`). É o elo com a P1.9/P1.20, como o publicador de outbox foi na P2.21.
3. **PDF do relatório ISO 19157 e metadado ISO 19115/MGB** (§9.1 etapa 6): dependem de biblioteca de
   PDF/XML a confirmar no POM — não inventada aqui.
4. **Benchmark 50 km/2 h e rede real do piloto**: homologação local (GL.1), por natureza fora desta VM.
5. **Perfil Saneago**: validar a IN 00.0229 rev. 04 antes de semear (§9.2).
6. **Riscos de CI (revisão estática apenas):** (a) aplicação do pacote IMPORT exercita o snap da
   GIS-03 sobre arco PLANNED→IN_SERVICE (criação de JUNCTION nas pontas) — mesmo caminho da
   `Gis04ChangeSetIT`, mas é o primeiro arco real aplicado por pacote; (b) `gis01:WATER_MAIN` e
   demais tipos do seed Corsan precisam existir no tenant da IT (vêm do legado/catálogo, como o
   `gis01:GATE_VALVE` da Gis04); (c) feições `gis_structure` publicam por `geom_point` quando pontuais
   e por `geom` quando poligonais — caminho sem IT própria nesta entrega (só arco é exercitado);
   (d) `gis_import_extract_labels` usa raio fixo de 5 m para associar rótulo ao arco (parâmetro de
   referência do §9.1 etapa 3, a validar na homologação — registrado aqui, não escondido).
