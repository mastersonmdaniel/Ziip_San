# ED-50 — Qualidade da água operacional: desenho e fiação

Módulo existente `shared/ziipsan-core`, pacote novo
`br.com.ziipsan.core.quality` (padrão ED-20/ED-35: núcleo puro, sem
JPA/Spring; persistência, controllers e telas são da integração).

## Disciplina de fontes (regra dura desta tarefa)

- **Nenhum limite de parâmetro** (cloro, turbidez, pH, coliformes etc.),
  **nenhuma frequência/quantidade de amostragem** e **nenhum leiaute**
  entra como valor fixo: as fontes do projeto (JSONs do plano, ADRs,
  pacotes das dependências) não trazem esses números. Eles vivem como
  **dado carregado por tenant** — catálogo normativo revisado pelo
  responsável técnico, plano de amostragem do prestador — ou como
  **parâmetro sem valor de fábrica**, com `[SEM FONTE]` e pendência
  nomeada. Nada foi copiado de portaria de memória.
- Único número com fonte no aceite: **30 dias** do Decreto 5.440/2005
  para a resposta à reclamação de qualidade
  (`sac.quality_complaint_deadline_days`, só reduz) — o próprio texto
  do aceite o fixa como "valor da norma".
- Os **14 valores da P3.9** não são tocados por esta tarefa.

## Componentes

| Classe | Papel |
|---|---|
| `QualityParameters` | 3 chaves: prazo da reclamação (30, só reduz, com fonte), `quality.sisagua_layout` [SEM FONTE], `quality.risk_template_key` [SEM FONTE/JOR-22] |
| `ParameterStandard` + `NormativeCatalog` | Catálogo da Portaria GM/MS 888/2021 como dado versionado com vigência; linha sem revisão do responsável técnico não fundamenta conformidade |
| `SamplingPoint` + `SamplingPlan` | Pontos e plano com quantidades definidas no plano do tenant (nada de fábrica) |
| `Sample` | Amostra + cadeia de custódia evento a evento; etiqueta obrigatória; recebimento registrado pelo laboratório (JOR-37) |
| `AnalysisResult` | Resultado como reportado (numérico OU qualitativo); correção versionada com justificativa (SG-813) |
| `ConformityEvaluator` | Veredito contra a linha revisada vigente; sem linha revisada → `INDETERMINATE`, nunca presumido |
| `MonthlyClosing` | Fechamento mensal por sistema: planejado × coletado × analisado × vereditos; % só sobre denominador nomeado |
| `BillQualityBlockSource` | Linhas da fonte `QUALITY_MODULE` do bloco da conta da P2.24 (Decreto 5.440), só de fechamento fechado |
| `PortalQualityPublication` | Publicação no portal (P3.3) por ponto/sistema; COMPLAINT nunca é publicado (é do protocolo) |
| `SisaguaExport` | Arquivo SISAGUA assistido, determinístico, só de fechamento fechado e resultados validados |
| `ComplaintQualityFlow` | OPE-26: protocolo (ED-35) → OS de coleta (ED-20) → laudo → recoleta se fora do padrão → resposta no mesmo protocolo no prazo |
| `HealthRiskCommunication` | JOR-22: disparo por modelo da P2.30 à autoridade de saúde e à população, com portão da conferência dos artigos e registro de envio/prazo |
| `QualityPorts` | Portas: OS de coleta (ED-20), resposta no protocolo (ED-35), disparo por modelo (P2.30) |
| `QualityReports` | Definições dos relatórios SAMAE 2.28.117–126 (SG-1007..SG-1016) sobre as fontes da entrega, p/ catálogo P3.22/ED-46 |

## Tabela de fiação (integração)

| Porta / consumidor | Ligação esperada |
|---|---|
| `FieldCollectionOrderPort` | OS de coleta da ED-20 (app de campo) sobre o motor da P1.31; a OS carrega ponto, tipo de amostra e etiqueta |
| `ProtocolResponsePort` | Registro da resposta no protocolo da ED-35 (mesmo protocolo da reclamação) |
| `LegalTemplateDispatchPort` | Motor de modelos da P2.30 (JOR-06); o canal de envio é o do motor/ED-41 — a ED-50 não envia por canal próprio |
| Bloco da conta | Adaptador converte `BillBlockLine` → `WaterQualityResult` da P2.24 com `source = QUALITY_MODULE`; o tenant troca `bill.water_quality_source` quando a ED-50 ativar (padrão da P2.24 segue `LAB_SPREADSHEET` até lá) |
| Portal | P3.3/ED-38 expõem `PortalQualityPublication` (só leitura, sem dado pessoal) |
| Relatórios | Registrar `QualityReports.definitions()` no catálogo da P3.22/ED-46 com views sobre as tabelas `quality_*` na réplica |
| ED-87 (diário de estações) | Leituras de processo do diário entram como amostras/resultados `PROCESS_CONTROL` e alimentam este fechamento e o SISAGUA sem redigitação (JOR-21, lado ED-87) |
| ED-51 (ISO 17025) | Consome amostras/resultados/custódia desta entrega; calibração/Westgard/incerteza são da ED-51 |
| ED-83 / ED-105 | Reaproveitam plano, laboratório e cadeia de custódia desta entrega (declarado nos aceites delas) |

## Fora de escopo (mandado do aceite)

Diário de operação de estações = ED-87 (OPE-02); insumos químicos e
limpeza de reservatórios = ED-89 (OPE-06/07); efluente não doméstico =
ED-83 (OPE-13); automonitoramento do efluente da ETE e lodo = ED-105
(JOR-16); apoio ISO/IEC 17025 = ED-51; paradas da ETA por supervisório
(SG-810) = ED-87/telemetria.
