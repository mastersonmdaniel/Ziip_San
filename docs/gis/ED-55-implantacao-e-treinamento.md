# ED-55 — Implantação do GIS em três etapas (SG-1334) e treinamento (SG-884, SG-885)

Documento de implantação da GIS-18. Os prazos concretos de cada cliente
são preenchidos pela implantação real na tabela `gis_rollout_plan`
(SQL pendente da ED-55) — este documento NÃO fixa datas de cliente;
fixa a estrutura das etapas, os critérios de saída e a regra dura do
ensaio: **a migração da amostra de rede termina em no máximo 60 dias**
(conferida pela CHECK `ck_gis_rollout_sample_window` e por
`GisExpandedImportRules.withinSampleWindow`).

## Etapa 1 — Amostra de rede (ensaio)

- Escopo: um recorte representativo da rede do cliente (setor/bacia),
  importado pelo pipeline GIS-07 + GIS-18 (DWG/DXF/SHP/GPKG da P3.26;
  DGN v7, INP e CSV GNSS da ED-55).
- Entradas: arquivos originais com hash, conversão assistida registrada
  (DWG→DXF via ODA; DGN v8→DWG/DXF), SRS declarado, perfil de camadas.
- Saídas: lote publicado por pacote IMPORT revisado, relatório ISO
  19157 da GIS-07, fila de casamento SG-1349 zerada ou decidida,
  conciliação SG-1356 com itens resolvidos ou com resolução registrada.
- Critério de saída: amostra concluída em ≤ 60 dias do início
  registrado; desvios viram item de risco da Etapa 2, não retrabalho
  silencioso.

## Etapa 2 — Rede completa e acesso QGIS

- Migração da rede completa com o mesmo pipeline e perfis ajustados
  pela Etapa 1.
- Acesso QGIS (GIS-10): rotina privilegiada cria as roles individuais
  a partir dos pedidos aprovados (`platform.gis_db_role_request` →
  `platform.gis_db_role`, core V12 — a rotina é a única que grava
  `gis_db_role`), concede SELECT nas `qgis_*`, escrita nas
  `qgis_edit_*` e — correção A1 pós-confirmação — o SELECT mínimo nas
  tabelas base sob as views, que o `security_invoker = true` exige
  (sem ele, a role recebe `permission denied` em qualquer leitura ou
  edição; escrita direta na base continua sem grant algum, e a role
  nasce sem BYPASSRLS); o `.qgz` e o `pg_service.conf` sem senha são
  entregues pela api (download no controller da GIS-10).
- Endpoint dedicado com TLS `verify-full`, fora do pooler, por VPN ou
  IP de origem (política validada por `GisQgisRules`).
- Critério de saída: teste de isolamento executado (role de um tenant
  não lê/grava outro — `Gis10QgisIT`), pentest do caminho QGIS
  contratado/executado (escopo da P3.8) antes do primeiro cliente.

## Etapa 3 — Operação assistida e treinamento (SG-884, SG-885)

- Roteiro de treinamento (conteúdo; carga horária é da implantação,
  não inventada aqui):
  1. Consulta web (GIS-06) × consulta QGIS: mesma simbologia, mesmas
     camadas; diferenças de canal (edição QGIS cai em pacote).
  2. Edição pelo QGIS: salvar = propor no pacote DRAFT; submeter;
     revisão por outra pessoa na web; o que a validação recusa e por quê.
  3. Importação: abrir lote, conversão assistida, staging, mapeamento
     de camadas, limpeza, relatório de qualidade, publicação.
  4. Fila de casamento e conciliação: decidir itens, registrar motivo.
  5. Higiene de acesso: validade da role, revogação, o que fazer se a
     credencial vazar (revogar primeiro, investigar depois).
- Registro: `training_delivered`/`training_note` por etapa em
  `gis_rollout_plan`.
- Critério de saída: operadores do cliente executam os fluxos 1–4 sem
  acompanhamento, em ambiente de homologação.

## O que este documento não é

Não é o ensaio: datas, rede real do piloto e pentest executado são
atos externos (GL/homologação), registrados como pendências no
RELATORIO-ED-55.
