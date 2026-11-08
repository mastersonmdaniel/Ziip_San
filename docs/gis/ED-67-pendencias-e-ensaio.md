# ED-67 — Pendências nomeadas e roteiro de ensaio (GIS-16/GIS-17)

## Divergência de título (registrada, não escondida)
O despacho desta frente chamou a ED-67 de "Demais leiautes do
piloto". Nos JSONs de tarefas (tarefas-onda-B.json) e no plano, a
ED-67 é "Exportação, devolução da base e OGC autenticado por
tenant (GIS-16, GIS-17)" — deps P3.25 e P2.22. A entrega segue a
spec do plano. Nenhum "leiaute do piloto" foi inventado: o piloto
real (P0.30) não foi escolhido e não há leiaute dele no projeto.
Os perfis Corsan/Saneago são os do §9.2 do GIS-CADASTRO-TECNICO
(fontes 19b/IN 00.0229), e o Saneago está marcado PROPOSTA porque
o próprio §9.2 declara o conteúdo "a validar na leitura da IN".

## Pendências (nomeadas)
1. **TEAM Engine (SG-1357)** — conformidade OGC conferida no
   validador oficial: exige ambiente com a api de pé; não roda
   nesta VM. Roteiro: subir a camada integrada, apontar o TEAM
   Engine para /api/v1/gis/ogc e para a fachada WFS, arquivar o
   relatório das classes DECLARADAS (correção D6: a conformsTo
   cobre só Core/GeoJSON da parte 1 e CRS da parte 2 — parte 3,
   cql2-text e oas30 não são declarados nem ensaiados como
   entregues) e do WFS 2.0. A validação XSD do XML MGB contra o
   perfil MGB 2.0 entra no mesmo ensaio (correção D10).
2. **GDAL (GPKG/SHP e leitura em QGIS)** — a escrita binária sai
   pela GisBinaryExportPort; ligar o writer GDAL do worker na
   integração e conferir a leitura em GDAL/QGIS (aceite GIS-16).
3. **GeoServer real** — versão/imagem fixadas na implantação
   (variável do compose), provisionamento REST executado contra
   um GeoServer de verdade e teste de isolamento pela role do
   workspace, em schema próprio e compartilhado (CI). Inclui a
   **rota/proxy WMS**: esta entrega tem SOMENTE a guarda
   (GisWmsProxyGuard) — não há controller que a aplique e
   encaminhe GetCapabilities/GetMap/GetFeatureInfo ao GeoServer
   (correção D14). Inclui também ligar o DataSource do
   GisTenantParameterReader (parâmetro de agregação do tenant,
   correção D7).
4. **Views pub_* e roles gis_ro_<tenant>** — criadas pela rotina
   privilegiada de implantação (como as roles do QGIS, §7.1), com
   as colunas finais da V13_2; SQL desta entrega está em
   db/pendente/ sem número de Flyway (o plano só reserva a
   família V13_x para ED-53..ED-67).
5. **Credencial OGC** — emissão/revogação da credencial do tenant
   e a authority gis:third-party na autenticação (integração).
6. **Ensaio de saída (devolução)** — rodar o GisReturnOfBase com
   o pacote real no ensaio da P3.16 e medir os 10 dias úteis.
7. **WFS Transaction → pacote** — ligar a WfsPackagePort ao
   GisChangeSetService (GIS-04): abrir DRAFT do usuário, canal
   WFS, e mapear TransactionDraft para ItemInput.
8. **FeatureSource** — ligar a leitura PostGIS (rede na data via
   GIS-04) que alimenta exportação, OGC e WFS com o tenant do
   contexto.
9. **Beans das portas (correção D12)** — os serviços da entrega
   estão anotados com @Service; os BEANS das portas
   (FeatureSource, BinaryExportPort, PersonalAccessRegistrar,
   WfsPackagePort, GeoServerRestPort e o DataSource do leitor de
   parâmetros) são da integração — sem eles os controllers não
   sobem, e isso é esperado até esta ligação.
10. **Comparação das 3 saídas de estilo (P8)** — o teste existente
    compara SLD × resolução da fonte única (2 pernas, verde). O
    cruzamento com as pernas MapLibre (P3.25) e QGIS (ED-55) não
    existe em nenhum pacote e fecha o item 14 do acceptance.
11. **DXF/KMZ (limitações declaradas)** — o DXF é mínimo (camada
    por tipo, sem blocos por símbolo) e KMZ não é entregue (só
    KML); ambos só entram com trabalho novo nomeado.
12. **Authority da exportação (D16)** — o §6.6 define
    `gis.export`, mas nenhum vizinho a usa e o controller segue
    com `gis:view`; a integração decide e registra.
