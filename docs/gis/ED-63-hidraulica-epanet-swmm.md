# ED-63 / GIS-12 — Hidráulica: EPANET e SWMM, exportação e importação

Fonte do aceite: JSON do plano v3.0 (tarefas-onda-B), ID ED-63; desenho no
GIS-CADASTRO-TECNICO §2.3 (M5/M6), §11 (integrações EPANET/SWMM) e na linha
GIS-12 do quadro de entregas; ADR-0034, decisão 8. Achados próprios:
COD-34 (solver real), GES-31 (exportação ao gêmeo de terceiro), OPE-35
(licença/viabilidade antes da adoção).

## O que a entrega faz

- **EPANET (.inp) por zona, estado e data** — `EpanetInpWriter` sobre a
  foto lida de `gis_node`/`gis_arc` (nunca da view): rugosidade = C de
  Hazen-Williams de `gis_material` (sobrescrita do elemento quando
  declarada; sem C, o arquivo não sai — problema listado); demanda =
  consumo faturado agregado das ligações do nó no período
  (`volume ÷ dias ÷ 86400 × 1000` L/s), injetado pela porta
  `BilledConsumptionSource` (a agregação é do faturamento, GIS-05).
- **SWMM 5.2 (.inp) de esgoto e drenagem** — `SwmmInpWriter`: junções
  (profundidade = tampa − fundo), condutos com seção geom1–geom4 e
  Manning do material, armazenamento FUNCTIONAL de área constante
  declarada, outfalls, subcatchments da GIS-13 com infiltração
  HORTON/GREEN_AMPT/…, [DWF] quando há vazão de tempo seco declarada e
  inlets [INLETS]+[INLET_USAGE] no formato do `inlet.c` do SWMM 5.2
  (grelha GENERIC exige a fração de área aberta). Sem seção [END]:
  o SWMM a recusa com ERROR 205 (conferido executando a biblioteca).
- **Importação .inp como pacote (SG-1339, parte INP)** — `InpImporter`
  devolve feições com o código preservado + SRS declarado obrigatório,
  para entrar no pacote de edição da GIS-04. Ida e volta preserva os
  códigos (teste).
- **Mapeamento tipo → elemento por tenant** — tabela
  `gis_hydraulic_mapping` (SQL pendente, semente de fábrica), lida pelo
  serviço; é a tabela que o GIS-CADASTRO-TECNICO §3 previa para a GIS-12.
- **Solver real (COD-34)** — `EpanetSimulationService` SUBSTITUÍDO: lê a
  topologia persistida, gera o .inp e executa pela `SolverPort`; a
  implementação é a `NativeSolverRunner`, que roda os binários oficiais
  compilados (EPANET 2.2/SWMM 5.2, `scripts/gis/build-solvers.sh`) e lê
  o resultado do relatório do próprio solver. O aviso de "solução não
  calculada" acabou: sem biblioteca, a chamada falha declaradamente.
- **Calibração e cenários** — `HydraulicCalibration` (séries de pressão,
  vazão e nível da telemetria ED-17; sem telemetria, recusa calibrar;
  grade declarada de multiplicador de demanda reexecutando o solver) e
  `HydraulicScenarios` (falta de energia, rompimento, fechamento de
  válvula, plano de manobra, bombeamento fora da ponta com janela
  declarada). Publicação só pelo portão `HydraulicPublication`:
  solver + versão + premissas + grau de confiança (N7; ver marcador).
- **GES-31** — gêmeo em tempo real fora do escopo próprio:
  `DigitalTwinExport` exporta demanda por zona e por nó, consumo
  faturado agregado por zona e o cadastro, em JSON/CSV por conector de
  arquivo, com o mesmo conteúdo como DTO para a API.
- **Remoção (M5/M6)** — SQL pendente parte 2: `DROP VIEW
  gis_network_element` e `DROP TABLE gis_network_element_legacy`,
  para aplicar só na release seguinte à troca do serviço; a entidade
  JPA `GisNetworkElement` e o `GisNetworkElementRepository` saem do
  código na mesma release (o serviço desta entrega já não os usa —
  teste de arquitetura).

## Marcadores e fronteiras declaradas

- **Regra N7 do 15c**: o texto do documento 15c não foi localizado nas
  fontes locais desta frente; o portão implementa os quatro campos que
  o aceite nomeia (solver, versão, premissas, grau de confiança).
- **Válvulas e bombas como NÓS do modelo novo**: o carregador trata
  válvula/bomba quando modeladas como ARCO (mapeamento PUMP/VALVE);
  o recorte de tubulação por válvula-nó do `NetworkTopologyBuilder`
  (GIS-03) é a peça de integração quando o tenant modela assim —
  declarado, não escondido.
- **Inlet em conduto circular**: o SWMM remove o inlet com WARNING 12
  (forma não suportada); inlet vale para condutos de rua/drenagem —
  comportamento da biblioteca, registrado nas provas.
- **Execução no CI**: o aceite pede a execução "na biblioteca no CI";
  nesta VM os binários oficiais FORAM compilados da fonte e executados
  de verdade (provas/), e o script de CI reproduz a construção com
  versão fixada e manifesto de hashes/licenças.
