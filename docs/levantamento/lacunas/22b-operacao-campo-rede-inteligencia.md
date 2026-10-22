# 22b: operação, campo, rede e inteligência. Funções esquecidas, cadastros rasos e inovações

Data: 21/10/2026. Produto Ziip SAN (código interno SIGSAN). Base: plano v2.17, ADR-0001 a ADR-0041, levantamentos 08, 09, 12, 15c, 18d, 18f, 18g, 20d, 20f, 21a, 21b e arquitetura (GIS-CADASTRO-TECNICO, PARAMETROS-DO-TENANT, ARQUITETURA-ALVO). Só documentação. O documento recomenda; não decide. Decisões do dono do produto seguem por cartão.

Convenções. "NR n/ANA" é a Norma de Referência da ANA (por exemplo, NR 15/ANA, Resolução ANA 275/2025). "NR-n (MTE)" é a Norma Regulamentadora do Ministério do Trabalho e Emprego (por exemplo, NR-33). Datas citadas são datas de norma ou de vigência, nunca prazo de projeto. Esforço usa a escala do plano (P, M, G), sem conversão em equipe.

## 0. Leitura rápida

1. Foram encontrados 40 achados: 13 ausentes do plano (A), 19 presentes mas rasos (B) e 8 inovações (C). Nenhum contradiz decisão aceita.
2. O plano cobre bem o núcleo de OS (P1.31, ADR-0039), o GIS e a topologia (ADR-0034), o material da OS (ADR-0032), o app offline com emissão (ADR-0026, ADR-0031) e as certificações (ADR-0041). Esses itens não são repetidos aqui.
3. Maior vazio: o módulo de perdas não tem tarefa dona. ED-78 e ED-48 dependem de um "módulo de perdas" que só existe como código parcial e como item do R9 de 2028 sob demanda (OPE-01).
4. Segundo vazio: a operação das unidades (ETA, ETE, elevatórias, reservatórios) só tem telemetria por REST e registro manual de paradas (ED-17). Prestador pequeno, sem CLP, não tem como registrar ronda, turno, turbidez por filtro a cada 2 h nem energia (OPE-02).
5. Terceiro vazio: soluções individuais, limpa-fossa e ligação obrigatória à rede estão em "2028, sob demanda", apesar de a Lei 11.445 incluir o lodo de fossa no serviço público de esgoto e de o SG-154 ser crítico no SAMAE (OPE-03).
6. Segurança do trabalho: as NR do MTE (NR-1, 18, 33, 35) não constam da lista de normas obrigatórias do ADR-0041, e as etapas de análise de risco e permissão de trabalho são opcionais e desligadas por padrão. A lei não admite essa opcionalidade em espaço confinado e altura (OPE-14).
7. A OS do plano é desenhada em torno da unidade consumidora. OS de rede e de ativo (reparo, inspeção, manutenção) precisam de alvo próprio, e a ocorrência de rede (incidente) precisa de entidade, porque a NR 9/ANA mede intermitência por paralisação de 6 h ou mais e extravasamento por quilômetro (OPE-15, OPE-16).
8. Manutenção preventiva por ativo não linear (bombas, motores, painéis) está na onda C, junto de ED-56, mas as ISO 55001 e 45001 e a NR 15/ANA dependem dela (OPE-20).
9. Inovações de maior retorno: otimização de programação com habilidades e janelas (OPE-33), detecção de anomalia explicável (OPE-36) e cruzamento de edificações com unidades consumidoras para perdas aparentes (OPE-37).
10. Trinta e cinco achados viram parâmetro do tenant pelo ADR-0038 (cinco não: OPE-12, OPE-22, OPE-30, OPE-35 e OPE-39); as chaves propostas estão no detalhamento e todas dependem de cartão antes de entrar no catálogo.

## 1. Método e fontes

### 1.1 Método

1. Leitura integral do que está decidido nas partes relevantes: cabeçalho do plano (v2.1 a v2.17), tabelas de tarefas P0 a P4, GL, LC e as ondas A, B e C da trilha de editais (ED-01 a ED-82); ADR-0010, 0026, 0028, 0034, 0039 e 0041, e as demais por busca; GIS-CADASTRO-TECNICO (§4.2, §5.6, §8, §11); PARAMETROS-DO-TENANT (grupos de campo, almoxarifado, GIS, certificações e bifurcações); levantamentos 18g (seções 1, 2.1 a 2.3, 3 e 4), 18d (E4 a E7, E10, E11), 20d (catálogo OS-01 a OS-28), 21a e 21b (lacunas e tabelas), 09 (QA e SG), 12 (RES) e 15c (N1 a N7).
2. Verificação de ausência: cerca de 80 termos pesquisados por expressão regular em todos os arquivos `.md` de `docs/` (plano inteiro incluído), em português e inglês, com sinônimos. Exemplos: roteirização, despacho, janela de atendimento, OPC, Modbus, MQTT, SCADA, DMC, pitometria, vazão mínima noturna, balanço hídrico, pressão, gêmeo digital, OCR, diário de operação, produto químico, jar test, lodo, kWh, limpeza de reservatório, rodízio, carro-pipa, fossa, sumidouro, hidrante, exercício de válvula, CCTV, NR-33, NR-35, NR-18, trabalhador isolado, pesagem, CEMADEN. Só foi classificado como (A) o que não aparece em nenhuma tarefa nem em decisão; o que aparece em levantamento mas não em tarefa com ID foi classificado como (B) ou (A) conforme o caso, com a indicação do documento.
3. Classes: (A) ausente do plano; (B) presente, mas raso (falta cadastro, regra ou campo, ou falta dono); (C) inovação sobre o que já existe.
4. Prioridade: alta (obrigação legal, dependência de outra tarefa ou risco operacional), média, baixa. Esforço: P, M, G, indicativo.
5. Pesquisa externa: busca ampla em fontes oficiais (gov.br, Planalto, DOU, BVS), documentação pública de fornecedores e literatura técnica. NR-33 foi lida integralmente por conversão do PDF oficial. As NR-35, NR-18 e NR-1 foram lidas por trechos. O texto de fornecedor é tratado como informação de mercado, não como fato regulatório.
6. Limites: o plano tem cerca de 258 mil caracteres; as seções de decisão (§2.2) foram pesquisadas por termo, não lidas por inteiro. Fatos não confirmados em fonte primária levam [INFERÊNCIA]. Não foi lido nenhum dado pessoal real, chave ou variável de ambiente.

### 1.2 Fontes internas principais

- `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (v2.17); `docs/arquitetura/adr/ADR-0010, 0026, 0028, 0031, 0032, 0034, 0035, 0037, 0038, 0039, 0041`.
- `docs/arquitetura/GIS-CADASTRO-TECNICO.md`, `PARAMETROS-DO-TENANT.md`, `ARQUITETURA-ALVO.md`.
- `docs/levantamento/editais/18d, 18f, 18g` e `18-ADERENCIA-EDITAIS-CONSOLIDADO.md`; `docs/levantamento/gsan/20d, 20f`; `09-regulatorio-qualidade-da-agua.md`, `12-regulatorio-residuos-mtr-sinir.md`, `10-regulatorio-lgpd-privacidade.md`, `15c-avaliacao-chatgpt-cruzamento.md`; `certificacoes/21a` e `21b`; `gis/19a a 19e`.

### 1.3 Fontes externas (URL)

Oficiais.

- [O1] Resolução ANA 275/2025, NR 15/ANA (perdas): https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2025/275
- [O2] ANA, notícia sobre a NR 15/ANA e a verificação a partir de maio de 2028: https://www.gov.br/ana/pt-br/assuntos/noticias-e-eventos/noticias/diretoria-da-ana-aprova-normas-de-referencia-para-indicadores-de-residuos-solidos-e-reducao-de-perdas-de-agua
- [O3] Resolução ANA 230/2024, NR 11/ANA (condições gerais): https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/230
- [O4] Manual Orientativo da NR 11/ANA: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Entidades%20Reguladores/Manual_NR_11_atualizado1.pdf
- [O5] Resolução ANA 211/2024, NR 9/ANA (indicadores operacionais, fichas Nível I): https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/211
- [O6] Manual Orientativo da NR 9/ANA: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/manual-nr-9.pdf/@@download/file
- [O7] Resolução ANA 245/2025, NR 12/ANA (drenagem): https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2025/245
- [O8] ANA, lista de Normas de Referência: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/Normas-de-referencia
- [O9] Portaria GM/MS 888/2021 (BVS): https://bvsms.saude.gov.br/bvs/saudelegis/gm/2021/prt0888_07_05_2021.html
- [O10] Sisagua, Ministério da Saúde: https://sisagua-treinamento.saude.gov.br/sisagua/paginaExterna.jsf
- [O11] Inmetro, verificação de instrumentos: https://www.gov.br/inmetro/pt-br/assuntos/metrologia-legal/controle-legal-de-instrumentos-de-medicao/modalidades/verificacao
- [O12] Inmetro, normas de medidor de volume (água), incluindo bancadas NIT-Seflu-007 e 015: https://www.gov.br/inmetro/pt-br/assuntos/metrologia-legal/documentos-normativos-para-metrologia-legal/assunto/medidor-de-volume-agua
- [O13] Inmetro, FAQ sobre troca de hidrômetro (cita Portaria 295/2018, item 7.5.2): https://www.gov.br/inmetro/pt-br/acesso-a-informacao/perguntas-frequentes/metrologia-legal/medidor-de-consumo-de-agua-hidrometro/como-obter-informacoes-sobre-a-substituicao-de-hidrometro-apos-7-anos-de-uso
- [O14] NR-33 (MTE), texto integral: https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-partitaria-permanente/arquivos/normas-regulamentadoras/nr-33.pdf
- [O15] NR-35 (MTE), texto atualizado: https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-partitaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-35-atualizada-2025-1.pdf
- [O16] NR-18 (MTE): https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-partitaria-permanente/arquivos/normas-regulamentadoras/nr-18-atualizada-2020.pdf
- [O17] NR-1 (MTE), GRO e PGR: https://www.gov.br/trabalho-e-emprego/pt-br/acesso-a-informacao/participacao-social/conselhos-e-orgaos-colegiados/comissao-tripartite-partitaria-permanente/normas-regulamentadora/normas-regulamentadoras-vigentes/nr-1 e https://www.gov.br/trabalho-e-emprego/pt-br/assuntos/inspecao-do-trabalho/pgr
- [O18] Lei 11.445/2007 (Planalto): https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2007/lei/l11445.htm; Lei 14.026/2020 (DOU): https://www.in.gov.br/en/web/dou/-/lei-n-14.026-de-15-de-julho-de-2020-267035421; art. 11-B: https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2020/lei/l14026.htm
- [O19] Lei 10.357/2001 (produtos químicos controlados): https://www.planalto.gov.br/ccivil_03/leis/leis_2001/l10357.htm; Polícia Federal, legislação vigente: https://www.gov.br/pf/pt-br/assuntos/produtos-quimicos/legislacao/legislacao; Portaria 240/2019 (revogada, citada como referência de procedimento): https://www.gov.br/pf/pt-br/assuntos/produtos-quimicos/legislacao/portaria-240.pdf/
- [O20] GSI, segurança de infraestruturas críticas: https://www.gov.br/gsi/pt-br/assuntos/seguranca-de-infraestruturas-criticas; Decreto 10.569/2020: https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2020/decreto/d10569.htm
- [O21] Cemaden, Mapa Interativo (MCTI): https://www.gov.br/mcti/pt-br/acompanhe-o-mcti/noticias/noticias-julho-outubro-2026/como-acompanhar-a-chuva-na-sua-cidade-pelo-mapa-interativo-do-cemaden; pluviômetros automáticos: http://www2.cemaden.gov.br/pluviometros-automatico/; FAQ: https://www.gov.br/cemaden/pt-br/paginas/historico-da-criacao-do-cemaden
- [O22] US EPA, EPANET e EPANET-RTX: https://www.epa.gov/water-research/epanet; estudo de caso RTX: https://nepis.epa.gov/Exe/ZyPURL.cgi?Dockey=P100LHQ6.TXT; WNTR: https://github.com/USEPA/WNTR

Técnicas e de mercado (fornecedores: informação de produto, sem validação independente).

- [T1] AWWA, controle de perdas (método IWA/AWWA, manual M36): https://www.awwa.org/resource/water-loss-control/
- [T2] Alliance for Water Efficiency, quatro pilares do controle de vazamento: https://allianceforwaterefficiency.org/wp-content/uploads/2025/08/Fri_Track-A_Disillusion-of-Progress.pdf
- [T3] Liemberger e Farley (2004), estratégia de redução de água não faturada: https://sswm.info/sites/default/files/reference_attachments/LIEMBERGER%20FARLEY%202004%20Developing%20a%20NRW%20Reduction%20Strategy.pdf
- [T4] Ohio EPA, orientação de programa de exercício de válvulas (cita AWWA G200-15 e M44): https://dam.assets.ohio.gov/image/upload/epa.ohio.gov/Portals/28/documents/pws/valve-exercising-program-guidance.pdf
- [T5] AEGIS, inspeção de válvulas de rede: https://www.aegislink.com/content/dam/aegislink/resources/publications/public/LossControl/AIS-LC-WaterValveInspection_12-4.pdf
- [T6] NASSCO, avaliação de esgoto (PACP, LACP, MACP): https://nassco.org/trenchless-technology/assessment; graus de condição: https://www.nassco.org/wp-content/uploads/2021/02/june_2016.pdf
- [T7] Timefold, Field Service Routing: https://docs.timefold.ai/field-service-routing/latest/user-guide/use-cases e https://timefold.ai/products/field-service-routing
- [T8] IBM Maximo, manutenção preventiva e rotas: https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=module-preventive-maintenance e https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=hierarchies-preventive-maintenance-records-associated-routes; água e esgoto: https://www.ibm.com/products/maximo/energy-utilities
- [T9] Esri, ArcGIS Field Maps e Workforce, uso em saneamento: https://www.esri.com/en-us/arcgis/products/arcgis-field-maps/overview; https://doc.arcgis.com/en/workforce/faqs/faqs-general.htm; https://www.esri.com/en-us/industries/water/mobile-workforce
- [T10] Medidores inteligentes (AMI): https://youniometering.com/smart-water-meters-in-the-real-world-lessons-from-5-years-of-utility-projects-and-complaints/; módulo LoRaWAN e wM-Bus (LoRa Alliance): https://lora-alliance.org/wp-content/uploads/2026/05/Unimet-Lite-Technical-Product-Brochure-EN.pdf; alarmes de medidor ultrassônico: https://www.fiorentini-iberia.com/en/solutions/products/water-meters/ultrasonic-smart-water-meters/ssm-aquo-lorawan/
- [T11] Supervisório e telemetria de saneamento: https://www.hms-networks.com/tech-blog/blogpost/hms-blog/2026/08/04/scada-integration-remote-pumping-stations; https://precisionfederal.com/insights/ai-for-water-utilities; https://borelogix.com/scada/; https://tatsoft.com/water-wastewater-scada/
- [T12] Leitura por foto com OCR no Brasil: https://hydrix.com.br/; https://scanflow.com.br/; experiência de concessionária (Águas Guariroba, ABES): https://abes-dn.org.br/anaiseletronicos/36_Download/TrabalhosCompletosPDF/V-027.pdf; termo de referência de leitura com foto e crítica (DAE Jundiaí): https://daejundiai.com.br/wp-content/uploads/2022/07/PE-31-22-Anexo-VI-Termo-de-Referencia.pdf

## 2. Quadro-resumo dos achados

Legenda: Cl. = classe (A ausente, B raso, C inovação); Prio. = prioridade sugerida; Esf. = esforço relativo; Param. = vira parâmetro do tenant (ADR-0038). "Nova" indica tarefa nova (ID a reservar); "Ajusta" indica tarefa existente a ajustar.

| ID | Cl. | Achado | Prio. | Esf. | Encaixe | Param. |
|---|---|---|---|---|---|---|
| OPE-01 | A | Módulo de perdas operacional sem tarefa dona (DMC, vazão mínima noturna, balanço por DMC) | alta | G | Nova (onda B, antes de ED-78); ajusta ED-48 e ED-78 | Sim |
| OPE-02 | A | Operação de ETA, ETE, elevatórias e reservatórios sem supervisório: diário, ronda e turno | alta | G | Nova (onda B); apoia-se em ED-17, ED-50, P1.31 | Sim |
| OPE-03 | A | Soluções individuais, limpa-fossa e ligação obrigatória à rede | alta | G | Nova (onda B); ajusta ED-13, ED-22, ED-34 | Sim |
| OPE-04 | A | Gestão de pressão: zonas, pontos de controle e ajuste de válvulas redutoras | média | M | Nova (junto de OPE-01); ajusta ED-17 | Sim |
| OPE-05 | A | Usos operacionais e emergenciais e água exportada no balanço hídrico | média | P | Ajusta OPE-01 e ED-81 | Sim |
| OPE-06 | A | Insumos químicos de tratamento: consumo, lote, ficha de segurança e controle da Polícia Federal | média | M | Nova (onda B); ajusta ED-50 e ED-58 | Sim |
| OPE-07 | A | Limpeza e desinfecção de reservatórios com registro público | média | P | Ajusta ED-50 e ED-79 | Sim |
| OPE-08 | A | Eficiência energética das unidades (kWh por m³, demanda, fator de potência) | média | M | Nova; ajusta ED-77 e ED-48 | Sim |
| OPE-09 | A | Programa de exercício de válvulas, de hidrantes e de descarga de rede | média | M | Ajusta ED-56 e ED-54 | Sim |
| OPE-10 | A | Crise hídrica: rodízio, racionamento, bandeira de contingência e carro-pipa | média | M | Nova (onda B); ajusta ED-35 e ED-54 | Sim |
| OPE-11 | A | Privacidade do rastreamento do executor e das fotos de campo | média | P | Ajusta ED-20, ED-66, P3.10 | Sim |
| OPE-12 | A | Inspeção de esgoto por vídeo (CCTV) e desobstrução programada | baixa | M | Ajusta ED-56 e ED-64 | Não |
| OPE-13 | A | Efluentes não domésticos: cadastro, pré-tratamento e amostragem pelo prestador | baixa | M | Nova (onda C); reaproveita ED-50 | Sim |
| OPE-14 | B | Segurança do trabalho na OS: habilitação, PET, análise de risco, espaço confinado e trabalho isolado | alta | M | Ajusta ADR-0041, P1.31, P3.31, ED-59, ED-71 | Sim |
| OPE-15 | B | Alvo da OS além da unidade consumidora (rede, ativo, unidade operacional, ocorrência) | alta | M | Ajusta P1.31 e ED-36 | Sim |
| OPE-16 | B | Ocorrência de rede (incidente) como entidade, ligada às NR 9 Nível I-04 e I-05 | alta | M | Nova (onda A ou B); ajusta ED-35, ED-54, ED-77 | Sim |
| OPE-17 | B | Controle ativo de vazamento: pesquisa, reparo e ganho medido | alta | M | Nova (junto de OPE-01) | Sim |
| OPE-18 | B | Medição inteligente e telemetria: dispositivo, alarme que abre OS, validação e estimativa | alta | G | Ajusta ED-17 e ED-15 | Sim |
| OPE-19 | B | Dimensionamento, retenção e agregação de séries temporais | alta | M | Ajusta ED-17, P2.3, P0.23 | Sim |
| OPE-20 | B | Manutenção preventiva de ativos não lineares (plano por tempo e por uso, rotas, checklist) | alta | G | Ajusta ED-56 (e antecipa da onda C); ED-72 | Sim |
| OPE-21 | B | Janela de atendimento, "a caminho" e aceite do usuário na OS | média | M | Ajusta ED-20, ED-41, ED-38 | Sim |
| OPE-22 | B | Sala de situação operacional em tempo quase real, fora do BI | média | M | Nova; ajusta ED-49 | Não |
| OPE-23 | B | Qualidade do trabalho de leitura: releitura amostral, faixa falsa, desvio de posição | média | M | Ajusta ED-15 e ED-20 | Sim |
| OPE-24 | B | Ciclo de vida econômico do parque de hidrômetros e bancada de ensaio | média | M | Ajusta ED-14 | Sim |
| OPE-25 | B | Lodo de ETA e de ETE como resíduo do próprio tenant (gerador) | média | M | Antecipa parte da trilha GRS; ajusta ED-70 | Sim |
| OPE-26 | B | Reclamação de qualidade que gera coleta de amostra e resposta no protocolo | média | P | Ajusta ED-35 e ED-50 | Sim |
| OPE-27 | B | Obras e projetos: viabilidade, projeto de loteamento, recebimento de rede, medição e recomposição | média | G | Nova (onda C); ajusta ED-53, ED-37 | Sim |
| OPE-28 | B | Frota: checklist, documentos do motorista e equipamentos especiais como recurso | média | M | Ajusta ED-59 | Sim |
| OPE-29 | B | Almoxarifado: número de série, lote, validade e kit por tipo de OS | média | P | Ajusta ED-58 | Sim |
| OPE-30 | B | Contrato de ingestão de supervisório (somente leitura) e rascunho do ADR de exceção | média | P | Ajusta ED-17; ADR-0028 (j) | Não |
| OPE-31 | B | Resíduos sólidos operacionais: pesagem, roteiro, coleta seletiva e plano operacional | baixa | G | Trilha GRS (2028, sob demanda) | Sim |
| OPE-32 | B | Drenagem: plano de operação e manutenção e monitoramento pluviométrico | baixa | M | Ajusta ED-64 | Sim |
| OPE-33 | C | Otimização de programação e roteirização com habilidades, janelas e SLA | alta | G | Ajusta ED-36 e ED-59; ADR-0010 (pgRouting) | Sim |
| OPE-34 | C | Leitura por foto com reconhecimento óptico e revisão por exceção | média | M | Ajusta ED-18 e ED-19 | Sim |
| OPE-35 | C | Modelo hidráulico calibrado com telemetria e cenários de falha | baixa | G | Ajusta ED-63 (após OPE-18) | Não |
| OPE-36 | C | Detecção de anomalia explicável em séries (vazão noturna, pressão, consumo) | média | M | Nova (após OPE-01 e OPE-18) | Sim |
| OPE-37 | C | Cruzamento de edificações com unidades consumidoras para perdas aparentes | média | M | Nova (onda C); reaproveita P1.2, ED-57 | Sim |
| OPE-38 | C | Manutenção por condição e criticidade de peça sobressalente | média | M | Ajusta ED-56 e ED-58 (após OPE-18, OPE-20) | Sim |
| OPE-39 | C | Identificação de hidrômetro, lacre e ativo por QR, código de barras ou NFC | baixa | P | Ajusta ED-18 e ED-20 | Não |
| OPE-40 | C | Assistência por voz e rascunho de parecer da OS, com os limites do ADR-0028 (c) | baixa | M | Ajusta ED-43 e ED-20 | Sim |

Ordem de dependência sugerida (sem datas): OPE-15 antes de ED-36; OPE-14 antes de qualquer OS de espaço confinado ou vala em produção; OPE-19 e OPE-30 antes de OPE-18; OPE-01 antes de ED-78; OPE-16 antes de ED-77 (indicadores de continuidade); OPE-18 antes de OPE-35, OPE-36 e OPE-38.

## 3. Detalhamento por achado

### 3.1 Classe A: ausentes do plano

**OPE-01. Módulo de perdas operacional sem tarefa dona**

- Descrição: não há tarefa que construa o controle operacional de perdas: distrito de medição e controle (DMC) como entidade viva, macromedidores de entrada e saída, vazão mínima noturna (VMN) e vazão noturna líquida (VMN menos consumo noturno legítimo), balanço hídrico mensal por DMC com grau de confiança, índice de vazamento da infraestrutura (ILI) com dados reais, metas por DMC e fila de DMC por prioridade. O código existente (`intelligence/sigsan-losses`, `WaterBalanceCalculator`) calcula com marcadores no lugar das entradas (18g §1.2; 06 §10.c). A janela clássica de VMN é de madrugada, entre 02:00 e 04:00 [T3].
- Por que importa: a NR 15/ANA exige balanço hídrico padronizado, base SINISA, anual, e Plano de Gestão de Redução e Controle de Perdas com diagnóstico, acompanhamento de perdas aparentes e reais, evolução da hidrometração e da macromedição e ações [O1]; a ANA passa a verificar a adoção a partir de maio de 2028 [O2]. A Lei 11.445, art. 11-B, obriga contratos a definir metas de redução de perdas [O18]. O indicador Nível I-01 da NR 9/ANA é perdas por ligação em l/lig./dia [O5]. O manual AWWA M36 e o método IWA/AWWA tratam o balanço como ponto de partida do programa de perdas [T1].
- O que o plano já tem: P4.2 (SINISA assistido, caso de água não faturada de 3.000 m³ e ILI); ED-48 (macromedição e balanço por setor, em G); ED-17 (telemetria); ED-77 (fichas de indicador); ED-78 (balanço padronizado e plano da NR 15/ANA, que "sobe por dependência" e cita "módulo de perdas"); GIS-CADASTRO §11 (zona DMA ligada a `dma_district`); P1.26 (`gis_zone`). O módulo de perdas só aparece no R9 de 2028 sob demanda (plano §4, "2028, sob demanda") e em 15c (N1).
- Onde encaixaria: tarefa nova (sugestão de ID ED-83, onda B), com dependência de ED-17, ED-48 e P1.26, e dependência inversa de ED-78. Resolver a dupla fonte de verdade entre `gis_zone` (DMA) e `dma_district` (05 L9).
- Prioridade alta; esforço G. Parâmetros propostos (classe d, exceto o que a norma fixar): `loss.night_window` (padrão 02:00 a 04:00), `loss.legit_night_use_l_per_conn_h` (consumo noturno legítimo por ligação; sem padrão de norma [INFERÊNCIA]), `loss.dmc_target_l_per_conn_day`.
- Fontes: [O1], [O2], [O5], [O18], [T1], [T3]; 05 L9; 06 §10.c; 18g §2.2.

**OPE-02. Operação de ETA, ETE, elevatórias e reservatórios sem supervisório**

- Descrição: falta o registro operacional do dia a dia da unidade: diário de operação por turno (equipamentos em operação e em reserva, níveis, vazões, pressões, horímetro, partidas, ocorrências, passagem de turno), rondas com roteiro e checklist, leituras manuais como fonte de série com marca de origem "manual", alerta de ronda não realizada e apuração da cobertura de frequência exigida. A Portaria 888 exige turbidez medida no efluente de cada unidade filtrante, no mínimo a cada duas horas para filtração rápida (art. 28, §3º), e pontos de amostragem na saída de cada filtro, na saída do tratamento, nos reservatórios, na rede e nas captações (art. 14, XXIII) [O9].
- Por que importa: prestador pequeno, perfil provável do cliente do Ziip SAN, costuma não ter CLP nem historiador [INFERÊNCIA: perfil do piloto a confirmar na P0.30]. Nesse caso a ronda manual é a única fonte do dado de processo. O levantamento 09 já registra QA-08 (turbidez por filtro a cada 2 h) como parcial e dependente de ingestão SCADA.
- O que o plano já tem: ED-17 (REST e arquivo de telemetria, "registro manual de paradas da ETA"); ED-50 (plano de amostragem, cadeia de custódia, conformidade); ADR-0028 (j) (MQTT e OPC só por exceção); GIS §4.2.5 (cadastro de ETA, reservatório, elevatória com "telemetria (ID do ponto)"); P1.31 (OS recorrente possível).
- Onde encaixaria: tarefa nova "Diário de operação e rondas" na onda B, sobre a tabela de séries da ED-17 (com origem `MANUAL`), o app da ED-18 (formulário offline) e o motor de conformidade da ED-50. Inclui a apuração de cobertura de frequência e do percentil mensal de turbidez (09 QA-08).
- Prioridade alta; esforço G. Parâmetros: `ops.round_interval_by_unit` (limite inferior de frequência fixado pela norma, só reduz o intervalo), `ops.shift_pattern`.
- Fontes: [O9]; 09 QA-05, QA-08; 18g SG-810.

**OPE-03. Soluções individuais, limpa-fossa e ligação obrigatória à rede**

- Descrição: (a) cadastro da solução individual de esgoto por imóvel (fossa séptica, sumidouro, filtro; volume, última limpeza, destino do lodo); (b) o serviço de esgotamento: agenda periódica ou sob demanda, OS com caminhão e roteiro, ticket de descarga na ETE com volume, MTR do tipo "Romaneio" onde exigido, preço do serviço; (c) campanha de conexão à rede: relatório de imóveis com rede de esgoto disponível e sem ligação, OS de notificação, desativação da solução alternativa depois da ligação.
- Por que importa: a Lei 11.445 inclui na prestação de esgoto a "disposição final dos esgotos sanitários e dos lodos originários da operação de unidades de tratamento coletivas ou individuais ... incluídas fossas sépticas" (art. 3º-B, IV) [O18]. A NR 11/ANA manda desativar a solução alternativa quando a rede ficar disponível, atribui ao prestador a adequação e o monitoramento quando prestar o serviço com solução alternativa e manda a ERI definir a tarifa desse serviço [O3, O4]. O art. 45 da Lei 11.445 exige a conexão das edificações às redes disponíveis [O18]. O SG-154 (cadastro de fossa séptica) é crítico e eliminatório na POC do SAMAE (18c).
- O que o plano já tem: nada com tarefa. SG-154 está em "2028, R9, sob demanda" (18d, linha 98); 21b GE-05 marca "Lacuna"; 12 RES-40 marca "NÃO ATENDE" (limpa-fossa e lodo de fossa); ED-22 trata o usuário avulso (POC-51, exemplo "desentupimento de fossa"); ED-34 e P3.6 cobram serviço avulso; `grs/sigsan-mtr` existe; P2.23 cria a base da ligação e ramal.
- Onde encaixaria: tarefa nova na onda B ("Soluções individuais e serviço de esgotamento"), apoiada em ED-13 (situação da ligação de esgoto), ED-34 (preço do serviço), ED-22 (cliente avulso), P2.23 (relatório "rede disponível sem ligação" por cruzamento de ramal e imóvel) e na recepção de lodo na ETE (OPE-25).
- Prioridade alta; esforço G. Parâmetros (ADR-0038): `sewer.alt_solution_service_enabled` (o tenant presta ou não o serviço), `sewer.alt_solution_service_interval_months`, `sewer.connect_notice_days`, `sewer.alt_solution_tariff_source`. A obrigação legal de conexão não é desativável.
- Fontes: [O3], [O4], [O18]; 12 RES-40; 18c SG-154; 21b GE-05.

**OPE-04. Gestão de pressão**

- Descrição: zona de pressão (`gis_zone` `PRESSURE_ZONE`), pontos de controle de pressão (`PRESSURE_POINT`) e válvulas redutoras (`PRV`) existem como cadastro. Falta a operação: série de pressão por ponto, limites mínimo e máximo por zona, alarme de pressão baixa e alta, registro do ajuste (`setting`) da válvula (quem, quando, motivo, valor anterior), relação entre pressão e número de rompimentos por arco, e vínculo com reclamação de "pouca água".
- Por que importa: gestão de pressão é um dos quatro pilares do controle de vazamento, ao lado da velocidade e da qualidade do reparo, do controle ativo e da gestão de ativos [T2]. A redução de pressão reduz vazamento e rompimentos [T3].
- O que o plano já tem: GIS §4.2.3 (VRP com `setting`), §4.2.4 (`PRESSURE_POINT` com telemetria na ED-17); ED-17 (séries); P1.26 (zonas).
- Onde encaixaria: tarefa nova junto de OPE-01, reaproveitando as séries de OPE-19.
- Prioridade média; esforço M. Parâmetros: `pressure.min_mca` e `pressure.max_mca` por zona (valores de referência da NBR de projeto de rede a conferir: [INFERÊNCIA] 10 mca dinâmica mínima e 50 mca estática máxima; a norma não está na lista da P1.28).
- Fontes: [T2], [T3]; GIS §4.2.3 e §4.2.4.

**OPE-05. Usos operacionais e emergenciais e água exportada no balanço**

- Descrição: a ficha do indicador de perdas da NR 9/ANA soma "volumes de água usados para atividades operacionais, emergenciais e sociais" e exclui a água tratada exportada a outro prestador ou município [O5]. O produto não registra esses volumes: OS de descarga de rede, limpeza de reservatório, combate a incêndio, carro-pipa social e fornecimento em bloco a outro prestador não têm campo de volume nem contrato.
- Por que importa: sem esses volumes o consumo autorizado não faturado fica subestimado e as perdas, superestimadas. O balanço IWA separa consumo autorizado faturado, não faturado, perdas aparentes e perdas reais [T3].
- O que o plano já tem: P4.2 (volume produzido), ED-48 (balanço por setor), ED-81 (uso próprio na ISO 46001), ED-17 (macromedidores).
- Onde encaixaria: campo `authorized_unbilled_volume` nos tipos de OS de serviço operacional (OPE-15) e cadastro de contrato de fornecimento em bloco (ponto de entrega, macromedidor, volume contratado) no OPE-01.
- Prioridade média; esforço P. Parâmetro: `loss.unbilled_estimate_method` (medido, estimado por vazão e tempo, fixo por tipo de serviço).
- Fontes: [O5], [T3].

**OPE-06. Insumos químicos de tratamento**

- Descrição: cadastro do produto com lote, laudo do fornecedor, ficha de segurança (FDS), validade e armazenamento (silo, tanque, cilindro); recebimento por nota fiscal e conferência; consumo por dia e por m³ tratado, com conciliação entre o consumo teórico (dosagem) e o real; alerta de estoque mínimo; controle dos produtos sujeitos à Polícia Federal (cadastro, licença de funcionamento renovada, mapas mensais de controle).
- Por que importa: o custo de produtos químicos é componente relevante do custo de produção e entra no indicador de eficiência da ETA [INFERÊNCIA]. A Lei 10.357/2001 submete a controle os produtos químicos listados em portaria, com licença anual e envio periódico de informações [O19]. A Portaria 240/2019, hoje revogada, previa mapas de controle mensais e guarda de documentos por cinco anos; a lista vigente consta na legislação atual da Polícia Federal (Portaria MJSP 204/2022) [O19]. O ácido sulfúrico constava da lista da Portaria 240/2019; se algum insumo do tenant está na lista vigente deve ser conferido [INFERÊNCIA].
- O que o plano já tem: ED-50 (e SG-811 na matriz do 18g: cadastro dos produtos de dosagem, como cadastro); ED-58 (almoxarifado de hidrômetros e materiais de rede, sem lote nem validade); ED-70 (aspectos ambientais, na ISO 14001).
- Onde encaixaria: tarefa nova na onda B, ligada ao diário de operação (OPE-02) e ao almoxarifado (OPE-29). Controle da Polícia Federal como módulo opcional por tenant.
- Prioridade média; esforço M. Parâmetros: `chem.controlled_products_list` (lista ligada à norma vigente, atualizada pelo Ziip), `chem.min_stock_days`.
- Fontes: [O19]; 18g SG-811.

**OPE-07. Limpeza e desinfecção de reservatórios**

- Descrição: plano de limpeza e desinfecção por reservatório (periodicidade, OS recorrente, laudo, produto usado, volume descartado) e registro publicável. O levantamento 09 (SG-06) aponta o registro público de limpeza de reservatórios como exigência do Decreto 5.440 (Anexo, arts. 16 e 17) e da Portaria 888 (art. 14, XVII), e diz que a limpeza "não é registrada".
- O que o plano já tem: ED-50 e ED-79 (relatório anual de qualidade a cada ligação, Carta de Serviços); GIS §4.2.5 (reservatório). Nenhuma tarefa trata da limpeza.
- Onde encaixaria: tipo de OS recorrente do catálogo do pacote `sanitation`, com consulta pública no portal (P3.3) e item no relatório da ED-79. O volume descartado alimenta OPE-05.
- Prioridade média; esforço P. Parâmetro: `ops.reservoir_cleaning_interval_months` (ERI; sem padrão de norma confirmado [INFERÊNCIA]).
- Fontes: [O9]; 09 SG-06.

**OPE-08. Eficiência energética das unidades**

- Descrição: cadastro das unidades consumidoras de energia por unidade operacional (número da instalação da distribuidora), importação da fatura de energia (arquivo; leiaute da distribuidora a confirmar [INFERÊNCIA]), cálculo de kWh por m³ produzido ou bombeado, demanda contratada e medida, fator de potência, consumo na ponta e alerta de multa.
- Por que importa: a Lei 11.445 inclui o fomento à eficiência energética entre as ações do saneamento (art. 2º, XIII, na redação da Lei 14.026) [O18]. O 21b L-13 admite energia, emissões e pessoal só por entrada manual, com grau de confiança baixo.
- O que o plano já tem: ED-77 (ficha de indicador, entrada manual com confiança baixa), ED-48 (indicadores), GIS §4.2.5 (potência kW dos conjuntos).
- Onde encaixaria: tarefa nova de médio porte, ligada ao diário de operação (OPE-02) e, depois, à otimização de bombeamento (OPE-35).
- Prioridade média; esforço M. Parâmetros: `energy.demand_alert_pct`, `energy.pf_min`.
- Fontes: [O18]; 21b L-13.

**OPE-09. Programa de exercício de válvulas, hidrantes e descarga de rede**

- Descrição: programa periódico por criticidade que gera OS agrupadas por região, registra voltas até fechar, estado encontrado e deixado, vazão e pressão do hidrante, e atualiza `operational_status` da válvula e a data do último teste do hidrante. Válvula que não fecha entra em fila de substituição, com indicador de percentual de inoperantes.
- Por que importa: o isolamento de manobra (ED-54) depende de saber quais válvulas operam. A orientação do Ohio EPA, apoiada na AWWA G200-15 e no manual M44, recomenda meta de 100 % das válvulas testadas em cinco anos e válvulas críticas anualmente, e rastrear o percentual de inoperantes [T4]. O material do AEGIS recomenda registrar o número de voltas de fechamento e organizar o trabalho por região para reduzir deslocamento [T5].
- O que o plano já tem: GIS §4.2.3 (`normal_status`, `operational_status`, `INOPERABLE`, hidrante com data do último teste, voltas como valor de referência a validar); ED-54 (isolamento atravessa válvula inoperável); ED-56 (manutenção ligada ao GIS, onda C); ED-14 (alerta de validade gera OS em lote, só para hidrômetros).
- Onde encaixaria: ajuste de ED-56 (ou OPE-20) com o programa como primeiro plano preventivo de rede; usa a programação geográfica de OPE-33.
- Prioridade média; esforço M. Parâmetros: `maint.valve_exercise_years_by_class` (críticas 1 ano; demais até 5 anos, padrões [T4]), `maint.hydrant_test_months`.
- Fontes: [T4], [T5]; GIS §4.2.3.

**OPE-10. Crise hídrica: rodízio, racionamento, bandeira de contingência e carro-pipa**

- Descrição: calendário de rodízio por setor ou zona de abastecimento, comunicação aos afetados, registro de horas sem água por setor (alimenta o índice de intermitência), bandeira tarifária de contingência como parâmetro, cadastro e controle de carro-pipa (autorização, documento de abastecimento, cloro residual livre mínimo de 0,5 mg/L) e fornecimento de emergência a unidades essenciais quando a interrupção passar de 24 h.
- Por que importa: a NR 11/ANA manda prover fornecimento de emergência às unidades essenciais em interrupção superior a 24 h [O4]. O levantamento 08 lista a bandeira de contingência (Lei 11.445, art. 46; NR 13/ANA, arts. 22 a 24) como "NÃO ATENDE" (R-23) e o racionamento conforme norma da ERI (R-27); o 09 lista o carro-pipa (QA-14) como "NÃO ATENDE".
- O que o plano já tem: ED-35 (interrupção programada por área e fornecimento de emergência a unidades essenciais, sem mapa); ED-54 (manobra e aviso); ED-41 (aviso multicanal); `gis_zone` `SUPPLY_SECTOR`.
- Onde encaixaria: tarefa nova na onda B que agrupa rodízio, bandeira e carro-pipa; reaproveita OPE-16 (ocorrência) para registrar a duração.
- Prioridade média; esforço M. Parâmetros (ADR-0038): `crisis.rotation_enabled`, `crisis.tariff_flag_enabled`, `crisis.tanker_truck_min_chlorine_mg_l` (padrão 0,5, só amplia).
- Fontes: [O4], [O9]; 08 R-23, R-27; 09 QA-14.

**OPE-11. Privacidade do rastreamento do executor e das fotos de campo**

- Descrição: finalidade, base legal, aviso, retenção e acesso da trilha de GPS do leiturista e da equipe, do histórico de localização e das fotos de campo (que podem conter pessoas, placas e documentos). O levantamento 10 registra a base (art. 7º, V e IX da LGPD, aviso ao empregado, retenção curta, acesso só da chefia) e deixa em aberto a pergunta 12: base legal, aviso e prazo de retenção aceitável.
- O que o plano já tem: ED-20 (trilha GPS do leiturista e da equipe), ED-66 (GNSS, fotos com posição, azimute e hash), P1.20 (varredura de arquivos), P3.10 (RIPD). Nenhum parâmetro de retenção da trilha nem regra de rastreamento só em jornada.
- Onde encaixaria: ajuste de ED-20 e ED-66 com parâmetros; item no RIPD do tenant (P3.10).
- Prioridade média; esforço P. Parâmetros: `field.track_retention_days` (classe c, só reduz), `field.track_only_in_shift` (travado em verdadeiro, classe a), `field.photo_blur_faces` (classe b).
- Fontes: 10 (linhas de dados de funcionários e pergunta 12).

**OPE-12. Inspeção de esgoto por vídeo (CCTV) e desobstrução programada**

- Descrição: registro da inspeção interna do trecho com códigos padronizados de defeito, grau de condição de 1 a 5 e pontuação do trecho, que alimenta a probabilidade de falha da ED-56; desobstrução programada (hidrojateamento) em trechos de reincidência; investigação de infiltração e contribuição pluvial.
- Por que importa: o PACP, da NASSCO, é o padrão de codificação de defeitos e o grau de condição serve de entrada para a probabilidade de falha na gestão de ativos [T6]. [INFERÊNCIA] O uso no Brasil segue o PACP ou a prática da WRc; confirmar com os clientes.
- O que o plano já tem: ED-64 (drenagem: estado de conservação, obstrução, foto, inspeção); ED-56 (risco por probabilidade e consequência, com rompimentos de rede de água vindos de OS de reparo).
- Onde encaixaria: ajuste de ED-56 e ED-64 com tipo de OS de inspeção e anexo de relatório (arquivo de transferência padrão da NASSCO, importável).
- Prioridade baixa; esforço M. Sem parâmetro.
- Fontes: [T6].

**OPE-13. Efluentes não domésticos: cadastro, pré-tratamento e amostragem pelo prestador**

- Descrição: cadastro do gerador não doméstico (indústria, hospital, posto, lavanderia), caracterização do efluente, termo de compromisso e pré-tratamento, plano de amostragem do prestador no ponto de lançamento do usuário, notificação por não conformidade e cobrança por carga quando a ERI previr.
- Por que importa: a NR 11/ANA diz que esgotos de edificações não residenciais que não possam ser lançados diretamente na rede estão sujeitos a controle e tratamento prévios às expensas do usuário, nos padrões do prestador [O4]. Não localizei o tema nos levantamentos 08 e 20f nem em tarefa do plano.
- O que o plano já tem: ED-50 (plano de amostragem e conformidade para água), ED-80 (efluente do próprio prestador, onda C).
- Onde encaixaria: tarefa nova na onda C, reaproveitando o motor de amostragem da ED-50 com catálogo de padrão de lançamento do prestador.
- Prioridade baixa; esforço M. Parâmetros: `effluent.user_standard_catalog`, `effluent.sampling_interval_days`.
- Fontes: [O4].

### 3.2 Classe B: presentes, mas rasos

**OPE-14. Segurança do trabalho na OS: habilitação, PET, análise de risco, espaço confinado e trabalho isolado**

- Descrição: o mecanismo existe (competência com validade e "ligação com permissão" na C07 do 21a, entregue pela P3.31; análise de risco e permissão de trabalho como etapas opcionais da OS, desligadas por padrão, na P1.31 e na ED-71). Faltam quatro pontos: (i) o ADR-0041 lista como obrigatórias as NR da ANA, a Portaria 888, o CONAMA, as leis e a LGPD, e não lista as NR do MTE; (ii) o bloqueio de programação e de encerramento sem habilitação vigente e sem PET não é critério de aceite; (iii) o cadastro de espaços confinados não aparece no GIS; (iv) a vedação de trabalho isolado não vira regra de equipe mínima na programação.
- Base normativa verificada: NR-33 [O14]: cadastro atualizado de todos os espaços confinados, inclusive desativados (33.3.3, a); PET emitida por escrito antes do acesso, válida só para cada entrada, com sistema de controle que permita a rastreabilidade, arquivada por cinco anos (33.3.3, f, g, j; 33.3.3.1; 33.5.3); exame médico e ASO (33.3.4.1); vedado trabalho individual ou isolado (33.3.4.4); vedada a designação sem capacitação prévia (33.3.5.1), com capacitação inicial de 16 h para autorizados e vigias e de 40 h para supervisores de entrada, e periódica a cada 12 meses com 8 h (33.3.5.3, 33.3.5.4, 33.3.5.6). NR-35 [O15]: análise de risco antes de todo trabalho em altura (35.5.5), permissão de trabalho para atividade não rotineira (35.5.7), trabalhador formalmente autorizado (35.4.1); a carga horária e a periodicidade da capacitação devem ser conferidas no texto [INFERÊNCIA: 8 h e 2 anos, segundo consulta, sem leitura integral]. NR-18 [O16]: escavação acima de 1,25 m só inicia com liberação de profissional habilitado (18.7.2.3). NR-1 [O17]: gerenciamento de riscos ocupacionais e PGR com inventário e plano de ação, revisão no máximo a cada dois anos, com fatores psicossociais incluídos na redação em vigor desde 26/05/2026.
- Por que importa: são exigências do empregador, independentes de o tenant ter ligado a ISO 45001. O Ziip SAN não substitui o SESMT, mas pode recusar a programação de uma OS que o empregador não poderia executar. IBM Maximo copia o plano de segurança para a ordem de trabalho a partir do plano de trabalho [T8], o que confirma a prática de mercado.
- O que o plano já tem: P1.31 (ajuste v2.17), P3.31 (competência), ED-59 (equipes), ED-71 (onda B, opcional), ED-58 (EPI), ADR-0041 (item das obrigatórias), P3.30 (escopo por área).
- Onde encaixaria: ajustar o ADR-0041 (NR-1, 18, 33 e 35 do MTE como `REGULATORY` não desativáveis, no mínimo operacional); ajustar P1.31 (tipo de serviço marcado como "espaço confinado", "altura" ou "vala" exige PET, análise de risco, habilitação vigente e equipe mínima, e a OS não é programada nem encerrada sem eles); ajustar ED-59 (executor com habilitações e validade, sem virar módulo de RH, preservando o ADR-0028 (i)); ajustar o catálogo do GIS (atributo de espaço confinado em poço de visita, câmara de válvula, reservatório, poço úmido, tanques da ETE).
- Prioridade alta; esforço M. Parâmetros: `sst.required_controls_by_service_type` (classe c: o tenant só amplia), `sst.min_crew_confined_space` (travado em 2 ou mais, classe a, pela 33.3.4.4 e pela função de vigia), `sst.training_validity_months` (padrão da norma, só reduz).
- Fontes: [O14] (lida integralmente), [O15], [O16], [O17], [T8]; 21a C07 e §6.3; ADR-0041.

**OPE-15. Alvo da OS além da unidade consumidora**

- Descrição: a P1.31 centra a OS na unidade consumidora e nos efeitos comerciais (corte, religação, alteração cadastral). Boa parte da OS de saneamento não tem unidade consumidora: reparo de vazamento na rede, desobstrução de coletor, limpeza de reservatório, inspeção de hidrante, manutenção de bomba, exercício de válvula. O catálogo 20d (OS-02) diz que "toda OS é ligada a UC, endereço ou ativo". O alvo precisa ser polimórfico: unidade consumidora, feição do GIS, ativo ou equipamento, unidade operacional (ETA, EEE) e ocorrência de rede (OPE-16), com tipo de serviço operacional sem efeito comercial, origem (ocorrência, plano preventivo, inspeção, reclamação) e prazo por tipo.
- Por que importa: a NR 11/ANA manda a ERI aprovar os serviços e seus prazos de execução [O3] e atribui ao prestador executar reparos de vazamento e de extravasamento dentro do prazo da ERI [O4]. Sem alvo próprio, o tempo de reparo e o custo por ativo não são medidos.
- O que o plano já tem: P1.31 (catálogo de tipos com operação, efeito, indicador interno ou de campo), ED-36 (OS completa), ED-34 (catálogo de serviços e preço), GIS §11 (OS no mapa, OS que altera a rede abre pacote), 20d OS-02 e OS-14, `sched.priority_weights`.
- Onde encaixaria: ajuste de P1.31 (campo `target_type` e `target_id`; teste de arquitetura mantido para escrita em cliente e unidade) e de ED-36. Parâmetro de prazo por tipo, hoje só existe para religação (`rec.sla_hours`).
- Prioridade alta; esforço M. Parâmetros: `so.deadline_hours_by_type` (limite: prazo da ERI; só reduz), `so.operational_types_enabled`.
- Fontes: [O3], [O4]; 20d OS-02; P1.31.

**OPE-16. Ocorrência de rede (incidente) como entidade**

- Descrição: paralisação de abastecimento, rompimento, extravasamento de esgoto, obstrução, falta de energia, alagamento. Cada ocorrência tem origem (reclamações agrupadas, telemetria, equipe), área afetada (calculada pelo isolamento da ED-54), causa, início, fim, economias afetadas, OS vinculadas, comunicação aos usuários e à agência, fornecimento de emergência quando passar de 24 h, e restauração.
- Por que importa: a NR 9/ANA mede a intermitência da água pela quantidade de economias ativas afetadas por paralisações de duração individual igual ou superior a 6 h, somadas as repetições (Nível I-04), e a do esgoto pelos extravasamentos por extensão de rede (Nível I-05), ambos de adoção obrigatória pela ERI e incluídos nos contratos [O5, O6]. A NR 11/ANA trata da comunicação à ERI e aos usuários [O3]. O 15c já propôs o incidente (N2) como entidade que amarra OS, afetados, comunicação e correção da topologia.
- O que o plano já tem: ED-35 (interrupção programada com área e aviso, sem mapa), ED-54 (área afetada pelo isolamento), ED-77 (indicadores de interrupção não programada e intermitência), ED-41 (aviso), 21b SU-04 e SU-09 (parcial). N2 consta como "R9 de 2028, incidente" (plano §4) sem ID.
- Onde encaixaria: tarefa nova "Ocorrência de rede" (sugestão ED-84), que dá a fonte ao ED-77 e ao OPE-17; ligada ao OPE-15 (alvo) e ao OPE-22 (sala de situação). A interrupção programada da ED-35 passa a ser um subtipo.
- Prioridade alta; esforço M. Parâmetros: `outage.min_duration_hours` (padrão 6, norma; só reduz), `outage.emergency_supply_after_hours` (padrão 24, norma; só reduz), `outage.cause_catalog`.
- Fontes: [O3], [O4], [O5], [O6]; 15c N2; 21b SU-04, SU-09, L-04.

**OPE-17. Controle ativo de vazamento: pesquisa, reparo e ganho medido**

- Descrição: campanha de pesquisa de vazamento não visível por DMC (hipótese, equipamento, equipe, evidência, achado, reparo, ganho medido depois), fila de suspeitas ponderada pela vazão noturna, e medição do tempo de reparo em suas fases (conhecimento, localização, reparo). As quatro frentes de controle de vazamento são pressão, velocidade e qualidade do reparo, controle ativo e gestão de ativos [T2]. O controle ativo combina pesquisa regular e monitoramento de DMC [T3].
- O que o plano já tem: 15c N1 (campanha), registrada no R9 de 2028 sem ID; ED-36 (OS e retrabalho); ED-56 (probabilidade de falha com rompimentos vindos de OS de reparo); 21b #15 (duração média de reparo, via OS corretiva).
- Onde encaixaria: tarefa nova junto de OPE-01 (a pesquisa nasce da fila de DMC) e OPE-15 (tipos de OS de pesquisa e de reparo). O ganho medido compara VMN antes e depois.
- Prioridade alta; esforço M. Parâmetros: `leak.repair_phase_targets_hours`, `leak.survey_cycle_days_by_dmc_class`.
- Fontes: [T2], [T3]; 15c N1.

**OPE-18. Medição inteligente e telemetria: dispositivo, alarme que abre OS, validação e estimativa**

- Descrição: a ED-17 recebe leituras e alarmes por REST e arquivo, em tabela particionada, "sem broker". O modelo de dispositivo e de leitura precisa de: hierarquia ponto de medição, dispositivo, canal; qualidade e estado do dado (medido, estimado, substituído, atrasado); relógio e fuso; alarmes padronizados do medidor (fluxo contínuo ou vazamento, fluxo reverso, tubo vazio, violação, bateria, estouro) com limiares configuráveis e OS automática ou notificação ao usuário; regra de validação e estimativa (VEE) antes de a leitura virar leitura de faturamento; prioridade entre leitura de telemetria e leitura manual no ciclo (ED-15, P2.7); curva horária disponível ao usuário e como prova em revisão por vazamento oculto (P3.28).
- Por que importa: o medidor eletrônico registra volume com data e hora, alarmes e eventos de comunicação, o que cria uma linha do tempo objetiva em reclamação [T10]. Interfaces correntes: LoRaWAN, NB-IoT, wireless M-Bus (EN 13757-4/OMS) e M-Bus com fio (EN 13757-2/3) [T10]. Um fornecedor documenta o alarme de fuga ativado após 12 h de fluxo contínuo acima de 0,5 Q1 (padrão configurável) [T10]; é padrão de produto, não de norma, e por isso entra como parâmetro.
- O que o plano já tem: ED-17, ED-15, P2.7 (leitura terceirizada por arquivo), P3.28 (revisão por vazamento oculto), ED-41 (aviso), `MeterReading.Source.TELEMETRY` (18d E10).
- Onde encaixaria: ajuste de ED-17 com o modelo acima; contrato de ingestão em OPE-30; dimensionamento em OPE-19.
- Prioridade alta; esforço G. Parâmetros: `meter.alarm_leak_hours`, `meter.alarm_reverse_flow_l`, `meter.telemetry_priority_over_manual` (ADR-0038: bifurcação entre leitura automática e manual), `meter.hourly_profile_visible_to_customer`.
- Fontes: [T10]; 18d E10; 18g E-AMR.

**OPE-19. Dimensionamento, retenção e agregação de séries temporais**

- Descrição: a ED-17 fala em "tabela particionada sem auditoria por linha", sem dimensionar. Conta simples: uma leitura horária por ligação em 100 mil ligações gera 2,4 milhões de linhas por dia e cerca de 876 milhões por ano; uma leitura diária, cerca de 36,5 milhões por ano [cálculo próprio]. Um supervisório de porte médio carrega de 5.000 a 50.000 pontos de dado, com compressão por faixa morta que guarda 2 % a 10 % das amostras brutas [T11, informação de fornecedor]. Faltam: política de retenção por resolução (bruto, 15 min, hora, dia), agregados contínuos (partição mais tabela de resumo), índice por ponto e tempo, e meta de desempenho.
- O que o plano já tem: ED-17, P2.3 (tabelas de fato particionadas), P0.23 e P2.9 (carga com 20 mil e 300 mil ligações, sem série de telemetria), ADR-0013 (PostgreSQL 17, sem extensão de série temporal), ADR-0012 (metas).
- Onde encaixaria: ajuste de ED-17 (aceite de carga com 100 mil ligações em leitura horária) e de P2.9; decisão do dono, por cartão, sobre extensão de série temporal só se o PostgreSQL puro não atender (ADR-0010, item 3). Não presumir.
- Prioridade alta; esforço M. Parâmetros: `tele.retention_raw_days`, `tele.retention_hourly_months`, `tele.rollup_policy`.
- Fontes: [T11]; cálculo próprio (marcado).

**OPE-20. Manutenção preventiva de ativos não lineares**

- Descrição: o plano trata a manutenção como parte do GIS (ED-56, onda C) e cobre a rede linear. Para ETA, elevatórias, ETE e reservatórios faltam: hierarquia de equipamento (unidade, conjunto, equipamento, componente), plano preventivo por tempo e por uso (horas de funcionamento, volume bombeado), plano de trabalho com tarefas, materiais, ferramentas e plano de segurança, geração antecipada de OS com previsão, rotas de inspeção agrupadas, checklists de ronda e indicadores de backlog, MTBF e MTTR. O IBM Maximo gera ordens a partir de registros de manutenção preventiva por tempo ou por medidor, com planos de trabalho, hierarquia, rotas e cópia do plano de segurança [T8].
- Por que importa: ISO 55001 (ED-72), ISO 45001 (ED-71) e a NR 15/ANA dependem de ED-56, que está na onda C. O ED-14 gera OS em lote por validade de hidrômetro, único mecanismo preventivo do plano. O 21a (§6.4, ISO 55001, cláusula 8.1) já pede plano de manutenção, ordens, histórico, custo, MTBF e MTTR ligados ao ativo.
- O que o plano já tem: ED-56, ED-72, ED-14, ED-58 (material), ED-59 (equipes), GIS-19 (inventário regulatório pela NR 16/ANA).
- Onde encaixaria: separar de ED-56 uma tarefa "Plano de manutenção de equipamentos" na onda B (OPE-09 e OPE-38 dependem dela); ED-56 fica com inventário regulatório e risco.
- Prioridade alta; esforço G. Parâmetros: `maint.pm_generation_lead_days`, `maint.pm_overdue_tolerance_days`, `maint.checklist_templates`.
- Fontes: [T8]; 21a (ativos); ED-56.

**OPE-21. Janela de atendimento, "a caminho" e aceite do usuário na OS**

- Descrição: janela de atendimento combinada com o usuário, aviso de "técnico a caminho" com janela estimada, estados de execução visíveis (a caminho, no local, em execução, concluída), comprovante e aceite do usuário na tela do aparelho (assinatura ou código), foto antes e depois e pesquisa de satisfação ao final. A matriz 18f registra "agendamento de serviços pelo app" como não planejado (SG-756) e programação com roteirização simples como não atendida.
- O que o plano já tem: ED-20 (OS em campo, fotos, GPS, anexos; reagendamento e aviso por 20d OS-18), ED-41 (aviso multicanal), ED-38 (portal), ED-35 (satisfação).
- Onde encaixaria: ajuste de ED-20 e ED-38 (solicitação com agendamento).
- Prioridade média; esforço M. Parâmetros: `so.appointment_window_hours`, `so.require_customer_acceptance_by_type`.
- Fontes: 18f SG-756; [T7].

**OPE-22. Sala de situação operacional em tempo quase real**

- Descrição: painel único, em mapa, com ocorrências abertas, OS em execução e atrasadas, posição das equipes, alarmes de telemetria, nível de reservatórios e unidades fora de operação. O painel operacional da ED-49 é feito no Superset sobre a réplica de leitura (ADR-0027: "nenhum relatório nem consulta de BI no primário"), com defasagem. Uma sala de situação precisa de atualização em segundos ou poucos minutos.
- Por que importa: o ArcGIS Field Maps e o Workforce oferecem despacho, compartilhamento de posição e acompanhamento do andamento em mapa [T9]; o Maximo oferece conhecimento situacional com inspeções, GIS e monitoramento [T8].
- Onde encaixaria: tela nativa, alimentada por tabela de estado resumida (não por consulta livre ao primário), com meta de latência no RNF (ADR-0012); ajuste da ED-49 para separar painel de gestão (BI) de console operacional. Decisão de arquitetura por cartão.
- Prioridade média; esforço M. Sem parâmetro (a atualização por segundos é RNF).
- Fontes: [T8], [T9]; ADR-0027.

**OPE-23. Qualidade do trabalho de leitura**

- Descrição: releitura amostral por critério, relatório de faixa falsa de leitura, desvio entre a posição da leitura e a do imóvel (alerta, não bloqueio, como em 20d OS-22), taxa de impedimento por leiturista e por rota, produtividade por dia e foto obrigatória em impedimento. O GSAN tem relatórios de faixas falsas e de releitura (20e); o 15a cita fiscalização de leitura como "outro levantamento". Termos de referência de leitura no Brasil exigem foto legível, critério de impedimento só após segunda tentativa e repasse das leituras retidas em prazo curto [T12].
- O que o plano já tem: ED-15 (roteiro, autoleitura com crítica), ED-18 (fotos por ocorrência), ED-20 (trilha GPS, painel), 20d OS-19 (OS seletiva), ED-24 (fiscalização).
- Onde encaixaria: ajuste de ED-15 e ED-20 com os relatórios e a amostra de releitura; reaproveita OS seletiva (OS-19).
- Prioridade média; esforço M. Parâmetros: `read.resample_pct`, `read.photo_required_on` (impedimento; toda leitura), `read.impediment_attempts`.
- Fontes: [T12]; 20e (relatórios); 20d OS-19, OS-22.

**OPE-24. Ciclo de vida econômico do parque de hidrômetros e bancada de ensaio**

- Descrição: o 21b L-12 e a ED-14 (ajuste v2.17) cobrem cadastro metrológico, regime e prazo de troca. Faltam: critério de troca por volume totalizado e classe, além da idade; análise de sub e superdimensionamento contra o perfil de consumo; plano de amostragem de lote para ensaio; gestão de bancada própria (resultado por vazão, curva de erro, aprovação de lote) e verificação por laboratório externo. O Inmetro mantém instruções de inspeção e verificação de bancadas de ensaio de medidores de água [O12]; a verificação subsequente é feita em bancada [O13].
- O que o plano já tem: ED-14, ED-58 (hidrômetro como item controlado), P3.28 (aferição sob demanda), `meter.verification_interval_years`.
- Onde encaixaria: ajuste de ED-14 com plano de renovação por custo e benefício (perda aparente evitada contra custo de troca) e cadastro de ensaio.
- Prioridade média; esforço M. Parâmetros: `meter.replace_by_volume_m3_by_class`, `meter.sample_plan_pct`.
- Fontes: [O12], [O13]; 21b L-12. Ponto a confirmar: o FAQ do Inmetro cita a Portaria 295/2018 (item 7.5.2, sete anos); o 21b adota a Portaria 155/2022. Conferir a vigência.

**OPE-25. Lodo de ETA e de ETE como resíduo do próprio tenant**

- Descrição: o tenant de saneamento é gerador de lodo de ETA e de ETE, de gradeamento e de desarenação, sujeito a PGRS e a MTR (12 RES-35; lista de códigos 19 09 e 19 08). O código de MTR existe (`grs/sigsan-mtr`), mas o plano deixa a trilha GRS para 2028, sob demanda, e o ED-80 cobre biossólido só como pacote de esgoto da onda C. Falta o registro de geração por unidade, a pesagem ou a estimativa de volume, o armazenamento temporário, o transportador, o destino e o MTR emitido, e a recepção de lodo de fossa na ETE (OPE-03).
- O que o plano já tem: ADR-0004 (SINIR assistido), P0.25 (SINIR em modo assistido), ED-70 (aspectos ambientais), ED-80 (onda C).
- Onde encaixaria: antecipar a parte "gerador" da trilha GRS (não a coleta urbana), junto do OPE-02 e do OPE-06.
- Prioridade média; esforço M. Parâmetros: `waste.mtr_exempt_origins` (jurisdição), `waste.sludge_destination_catalog`.
- Fontes: 12 RES-35, RES-36, RES-40.

**OPE-26. Reclamação de qualidade que gera coleta de amostra e resposta no protocolo**

- Descrição: reclamação de cor, gosto, odor ou pressão registrada no atendimento gera OS de coleta no ponto de consumo, resultado no laudo, recoleta quando fora do padrão e resposta ao usuário no mesmo protocolo. O 09 cita o tipo de amostra `COMPLAINT` e o prazo de 30 dias do Decreto 5.440 (SG-06).
- O que o plano já tem: ED-35 (visão 360 com análise de água), ED-50 (recoleta por não conformidade), ED-79 (relatório anual).
- Onde encaixaria: ajuste de ED-35 e ED-50 (tipo de solicitação "qualidade da água" com fluxo próprio).
- Prioridade média; esforço P. Parâmetro: `sac.quality_complaint_deadline_days` (norma; só reduz).
- Fontes: 09 SG-06; [O9].

**OPE-27. Obras e projetos: viabilidade, projeto de loteamento, recebimento de rede, medição e recomposição**

- Descrição: parecer de viabilidade técnica com dados de rede, pressão e diâmetro; análise e aprovação de projeto de rede de loteamento; recebimento da rede executada (as-built), com doação ao patrimônio; obra com cronograma, orçamento, medição e fiscalização; OS de recomposição de pavimento derivada da OS de reparo, com medição da empreiteira; licença de intervenção em via pública e consulta de interferência com outros serviços. O ArcGIS Workforce cita o "chame antes de escavar" (One Call) entre usos típicos [T9]; não há equivalente nacional confirmado [INFERÊNCIA].
- O que o plano já tem: ED-53 (pacotes de projeto e as-built, "projetos e obras" SG-858, TR-1050, TR-1051), ED-37 (medição de empreiteira), 18f SG-737 e SG-738 (viabilidade, "não atende"), 20g (ficha de viabilidade no atendimento), GIS-CADASTRO (ciclo projetado, em obra, em operação), 18d SG-138 e SG-139 (tipos de pavimento).
- Onde encaixaria: tarefa nova na onda C ("Obras e projetos") ligando pacote GIS, OS de obra, medição (ED-37) e patrimônio (ED-56).
- Prioridade média; esforço G. Parâmetros: `works.feasibility_deadline_days` (ERI), `works.network_acceptance_checklist`.
- Fontes: [T9]; 18f; 18d.

**OPE-28. Frota: checklist, documentos do motorista e equipamentos especiais como recurso**

- Descrição: a ED-59 cobre veículo, abastecimento, manutenção, custo e equipes. Faltam checklist pré-viagem, validade de habilitação do motorista e do licenciamento, multas e equipamentos especiais (caminhão de hidrojato, vácuo, limpa-fossa, retroescavadeira) como recurso exigido pelo tipo de serviço na programação (20d OS-15 trata o "equipamento especial"), consumo médio com alerta de desvio.
- Onde encaixaria: ajuste de ED-59; o recurso especial entra no modelo de OPE-33.
- Prioridade média; esforço M. Parâmetros: `fleet.checklist_required`, `fleet.docs_alert_days`.
- Fontes: 20d OS-15; ED-59.

**OPE-29. Almoxarifado: número de série, lote, validade e kit por tipo de OS**

- Descrição: hidrômetro rastreado por número de série e lacre do recebimento à sucata; lote e validade para reagentes, produtos químicos e EPI com certificado de aprovação; kit de materiais por tipo de serviço (previsão na abertura da OS, SG-476); ponto de reposição.
- O que o plano já tem: ED-58 (estoque por local, custo médio, inventário, alçada, baixa), ED-14 (retirada com destino e sucata), ED-62 (envio da previsão por kit ao ERP), `inv.inventory_cycle_days`.
- Onde encaixaria: ajuste de ED-58.
- Prioridade média; esforço P. Parâmetro: `inv.lot_expiry_alert_days`.
- Fontes: 18g SG-476.

**OPE-30. Contrato de ingestão de supervisório (somente leitura) e rascunho do ADR de exceção**

- Descrição: o ADR-0028 (j) exige ADR de exceção e cliente pagante para MQTT ou OPC. Para não travar a primeira implantação, convém fixar agora o contrato: o Ziip SAN nunca escreve em CLP, RTU ou IHM; a ingestão é unidirecional; o lado do cliente roda um agente de saída (HTTPS, sem porta de entrada) que lê do historiador por OPC UA, MQTT/Sparkplug B, SQL somente leitura ou CSV agendado, com armazenamento local e reenvio quando o enlace volta. Esse padrão é o descrito pelo mercado [T11] e pelo modelo de zonas e conduítes da IEC 62443 [T11].
- Por que importa: saneamento é infraestrutura crítica pela Política Nacional (Decreto 9.573/2018; ENSIC, Decreto 10.569/2020) [O20]. O GIS já restringe a publicação de infraestrutura crítica (GIS §12, item 7).
- O que o plano já tem: ED-17, ADR-0028 (j), ADR-0010 (ingestão sem broker), P3.8 e LC.8 (revisão e pentest).
- Onde encaixaria: ajuste de ED-17 com o contrato (esquema do envelope, assinatura, idempotência por amostra) e minuta do ADR de exceção, sem implementar broker.
- Prioridade média; esforço P. Sem parâmetro.
- Fontes: [O20], [T11].

**OPE-31. Resíduos sólidos operacionais**

- Descrição: o plano trata resíduos no faturamento (ED-25), no GIS (ED-65: contêiner, PEV, rotas com pgRouting) e no MTR/SINIR assistido (P0.25, ADR-0004). Para o tenant que presta a coleta, faltam pesagem e ticket de balança, roteiro de coleta e sua execução (GPS), coleta seletiva e cooperativas, grandes geradores, resíduos da construção e de saúde, e o plano operacional e indicadores exigidos pela ANA: a NR 7/2024 trata das condições gerais da prestação e a NR 14/2025 dos indicadores [O8]. O 12 registra RES-42 (plano operacional) e RES-43 (indicadores) como "NÃO ATENDE".
- Onde encaixaria: trilha GRS de 2028, sob demanda, com ID (o plano a lista sem IDs). Registrar aqui apenas para dar ID e dependência.
- Prioridade baixa; esforço G. Parâmetros: `waste.collection_frequency_by_sector`, `waste.weighing_required`.
- Fontes: [O8]; 12 RES-42, RES-43.

**OPE-32. Drenagem: plano de operação e manutenção e monitoramento pluviométrico**

- Descrição: a NR 12/ANA exige plano de operação e manutenção dos sistemas de drenagem aprovado pela ERI, com periodicidade e critérios, cadastro georreferenciado atualizado, monitoramento pluviométrico, fluviométrico e de qualidade, e relatório de indicadores operacionais (arts. 11 e 12) [O7]. A ED-64 cadastra a rede e o estado. Faltam o programa de limpeza de bocas de lobo e galerias, a ocorrência de alagamento e a ligação com chuva. O Cemaden mantém mais de 3.800 pluviômetros automáticos, com dado a cada 10 minutos em chuva, em mapa interativo e com download mensal; os dados são brutos e a fonte deve ser citada [O21]. Não confirmei API formal [INFERÊNCIA].
- Onde encaixaria: ajuste de ED-64 (plano de O&M e OS recorrentes) e tipo de ocorrência de alagamento em OPE-16.
- Prioridade baixa; esforço M. Parâmetros: `drain.cleaning_interval_by_class`, `drain.rain_source` (Cemaden, pluviômetro próprio).
- Fontes: [O7], [O21].

### 3.3 Classe C: inovações

**OPE-33. Otimização de programação e roteirização com habilidades, janelas e SLA**

- Descrição: hoje a programação prevista é a sugestão automática com confirmação humana, "preenche cada equipe até a carga, pela maior folga" (20d OS-15). A prática de mercado resolve roteirização com restrições: habilidades exigidas pelo serviço, janelas de atendimento, prazos contratuais, visitas com mais de um técnico, zona de cobertura, limite de horas extras e replanejamento quando entra urgência [T7]. A Timefold documenta esses requisitos como variantes do problema de roteirização de veículos (com janelas, frota heterogênea, dinâmico) [T7]. O Esri Workforce despacha e prioriza com sincronização contínua [T9].
- Proposta: motor de sugestão com custo de deslocamento sobre o grafo viário (pgRouting já aceito no ADR-0010 para lote e coleta; estender ao tempo de viagem das equipes é decisão nova), restrições duras (habilitação do OPE-14, equipamento do OPE-28, janela do OPE-21, prazo do OPE-15) e brandas (agrupamento por região, equilíbrio de carga), resultado explicável e sempre com confirmação humana. Solver externo ou biblioteca aberta: licença a conferir [INFERÊNCIA: Timefold Solver comunitário e Google OR-Tools são de código aberto; confirmar licença e viabilidade em Java]. Sem serviço pago de terceiro no caminho crítico.
- O que o plano já tem: ED-36 (programar e desprogramar), ED-59 (equipes, escala), ED-34 (tempo médio por tipo), ED-20 (tempo real medido), `sched.priority_weights`, pgRouting (GIS §5.7).
- Prioridade alta; esforço G. Parâmetros: `sched.hard_constraints`, `sched.soft_weights`, `sched.replan_on_urgency`.
- Fontes: [T7], [T9]; 20d OS-14 a OS-17.

**OPE-34. Leitura por foto com reconhecimento óptico e revisão por exceção**

- Descrição: o leiturista fotografa o visor; o aparelho lê os dígitos no próprio dispositivo e compara com o digitado e com o histórico; divergência vai para fila de revisão. Aplicativos brasileiros fazem isso para condomínios, com validação e revisão por exceção [T12]. Para concessionária, o ganho esperado está na detecção de erro e fraude de leitura, não na troca do leiturista [INFERÊNCIA]. Termos de referência de concessionárias já exigem foto legível para a crítica [T12].
- Cuidados: foto do visor não é dado pessoal, mas a foto ampla pode ser; vale OPE-11. Reconhecimento no aparelho evita enviar imagem a terceiro. O modelo é componente de terceiro e entra no DPA se houver serviço externo.
- O que o plano já tem: ED-18 (fotos por ocorrência), ED-19 (impressão simultânea), ED-15 (crítica da leitura autoleitura).
- Prioridade média; esforço M. Parâmetros: `read.ocr_mode` (desligado, sugerir, exigir), `read.ocr_tolerance_digits`.
- Fontes: [T12].

**OPE-35. Modelo hidráulico calibrado com telemetria e cenários de falha**

- Descrição: a ED-63 exporta o EPANET e roda o solver no CI. A evolução natural é calibrar o modelo com pressão, vazão e nível de reservatório, e usá-lo para cenários: falta de energia, rompimento, fechamento de válvula, plano de manobra, bombeamento fora da ponta. A EPA mantém o EPANET-RTX, bibliotecas abertas para ligar o modelo a dados operacionais e calibrá-lo, com correlação média de cerca de 0,80 em fluxos, pressões e níveis em estudo de caso [O22]. O WNTR, também da EPA, simula pane de energia, ruptura e plano de reparo sobre modelos EPANET [O22].
- Cuidados: licença e viabilidade na pilha Java a conferir [INFERÊNCIA]. A regra N7 do 15c vale: só publicar resultado com solver, versão, premissas e confiança.
- O que o plano já tem: ED-63, ED-54 (manobra), ED-17, P3.27, 15c N7.
- Prioridade baixa; esforço G; depende de OPE-18 e OPE-19. Sem parâmetro.
- Fontes: [O22]; 15c N7.

**OPE-36. Detecção de anomalia explicável em séries**

- Descrição: regras estatísticas simples e auditáveis sobre vazão noturna por DMC, pressão por ponto e consumo por ligação: desvio contra a mesma hora e dia da semana, consumo contínuo, consumo zero, queda súbita de pressão. Cada alerta mostra a série, a regra e a confiança. O 21a admite que um modelo estatístico simples basta na primeira versão da ação preditiva (ISO 55001, 10.3).
- O que o plano já tem: 15c N5 (crítica por pares do setor, 2028 sob demanda), N7; 20d (OS seletiva).
- Prioridade média; esforço M; depende de OPE-01 e OPE-18. Parâmetros: `anomaly.rules_enabled`, `anomaly.sensitivity`.
- Fontes: 21a CQ-11; 15c.

**OPE-37. Cruzamento de edificações com unidades consumidoras para perdas aparentes**

- Descrição: a NR 15/ANA define perdas aparentes incluindo ligação clandestina, fraude, erro de leitura e falha cadastral [O1]. Cruzar a camada de edificações ou lotes (cadastro municipal, quando o titular cede, ou importação de GIS) com as unidades consumidoras e com o ramal gera lista de imóveis com consumo nulo ou sem unidade, com fila de revisão e OS de fiscalização. É extensão natural de P1.2 (conciliação), ED-57 (importação com base legal) e ED-24.
- LGPD: o cruzamento usa coordenada ligada a titular; fica restrito ao backoffice (regra do ADR-0034, GIS-CADASTRO §12) e a base legal é registrada.
- Prioridade média; esforço M. Parâmetro: `audit.unregistered_building_review_days`.
- Fontes: [O1]; GIS §12.

**OPE-38. Manutenção por condição e criticidade de peça sobressalente**

- Descrição: gatilho de OS por leitura de horímetro, corrente, vibração ou temperatura vindos da telemetria (condição), e política de peça sobressalente crítica por equipamento (mínimo e ponto de reposição). O Maximo gera trabalho por condição a partir de medições e alarmes [T8].
- O que o plano já tem: ED-56, ED-58, 21a CQ-11 (ação preditiva).
- Prioridade média; esforço M; depende de OPE-18 e OPE-20. Parâmetros: `maint.condition_triggers`, `inv.critical_spares`.
- Fontes: [T8].

**OPE-39. Identificação de hidrômetro, lacre e ativo por QR, código de barras ou NFC**

- Descrição: o leiturista ou a equipe lê o código do hidrômetro, do lacre ou da placa do ativo para confirmar identidade e posição antes de registrar a leitura ou a intervenção. Reduz erro de lançamento em unidade errada e liga o ativo à OS. Concessionária brasileira usa código de barras em mais de 100 mil hidrômetros [T12].
- O que o plano já tem: ED-18 e ED-20 (app), SG-807 (código de barras da amostra, ED-50), ED-14 (lacre).
- Prioridade baixa; esforço P. Sem parâmetro.
- Fontes: [T12].

**OPE-40. Assistência por voz e rascunho de parecer da OS**

- Descrição: ditado e rascunho do parecer de encerramento (20d OS-11 limita o parecer a 400 caracteres) e do resumo de turno, revisados pelo executor, com os limites do ADR-0028 (c): modelo sem acesso livre ao banco, texto enviado sem dado pessoal, provedor no DPA e no RIPD, transferência internacional registrada.
- O que o plano já tem: ED-43 (chatbot do consumidor), ED-20.
- Prioridade baixa; esforço M. Parâmetros: `assist.voice_enabled`, `assist.provider`.
- Fontes: ADR-0028 (c).

## 4. Itens que o plano já cobre bem

- Núcleo de OS, tipos, estados, efeito aplicado uma vez e correção por OS inversa (P1.31, ADR-0039); revalidação do corte na execução, inclusive offline (P3.4, ED-20, 20d OS-07); prioridade calculada e pesos do tenant (OS-14, `sched.priority_weights`); programação com confirmação humana (OS-15, ED-36).
- Material da OS com alçada, reserva, baixa e segregação de funções (ADR-0032, ED-58, parâmetros `inv.*`).
- App de campo Android com refresh no Keystore, dispositivo registrado, bloqueio remoto, sincronização idempotente por `task_id`, emissão simultânea com motor Java único e impressão Zebra (ADR-0026, ADR-0030, ADR-0031, ED-18 a ED-20).
- Modelo do GIS: topologia persistida, regras de conectividade, catálogo de válvula, hidrante, VRP, macromedidor, ponto de pressão, ETA, reservatório e elevatória; pacotes revisados; "rede na data"; isolamento e plano de manobra com aviso e OS em lote; campo com GNSS e PMTiles; EPANET e SWMM; OGC (ADR-0034, ED-53, ED-54, ED-63, ED-66, ED-67).
- Qualidade da água: catálogo da Portaria 888, plano de amostragem, cadeia de custódia, conformidade, SISAGUA assistido, bloco na conta e apoio à ISO/IEC 17025 (ED-50, ED-51, P2.24).
- Hidrometria regulatória: Portaria Inmetro, regime, classe, verificação e prazo de troca (ED-14, 21b L-12); revisão por vazamento oculto e aferição (P3.28).
- Inventário patrimonial pela NR 16/ANA e bens reversíveis (ED-56, GIS-19); indicadores da NR 9/ANA e fichas das ISO 24510 e 24512 (ED-48, ED-77); pacotes ISO com ativação por tenant e obrigatórias não desativáveis (ADR-0041).
- Drenagem e resíduos no mesmo modelo do GIS (ED-64, ED-65), tarifa de resíduos (ED-25), MTR e SINIR assistidos (P0.25, ADR-0004).
- LGPD no mapa (piso de agregação, `gis.public_aggregation_min_units`) e restrição de infraestrutura crítica (GIS §12).
- Telemetria sem broker: decisão consistente com o congelamento (ADR-0010, ADR-0028 j).

## 5. Inferências e pontos a confirmar

Inferências assumidas neste documento.

1. O cliente típico é um serviço autônomo ou autarquia pequena, muitas vezes sem CLP nem historiador (base de OPE-02). Confirmar no discovery do piloto (P0.30, P1.1).
2. Custo de produtos químicos como componente relevante do custo de produção (OPE-06); confirmar com o cliente.
3. Uso do PACP no Brasil (OPE-12). Confirmar com os clientes e com o responsável técnico.
4. Valores de pressão mínima e máxima por zona (OPE-04): 10 mca e 50 mca como referência da NBR de projeto de rede. A norma não está na lista de compra da P1.28.
5. Leiaute da fatura de energia por distribuidora (OPE-08).
6. Ganho do reconhecimento óptico concentrado em detecção de erro e fraude (OPE-34); sem medida própria.
7. Licenças e encaixe técnico de solver de roteirização (Timefold Solver comunitário, OR-Tools), de EPANET-RTX e do WNTR na pilha Java e no PostgreSQL. Conferir antes de qualquer proposta de ADR.
8. Equivalente nacional ao "chame antes de escavar" (OPE-27): não confirmado.
9. API formal do Cemaden (OPE-32): só confirmei o mapa interativo e o download mensal.
10. Dimensionamento de séries (OPE-19) é cálculo próprio com premissa de 100 mil ligações e leitura horária; não é dado de cliente.

Pontos a confirmar em fonte primária.

1. NR-35: carga horária e periodicidade da capacitação (item 35.4.x) e número dos itens citados; o texto foi lido por trechos. NR-33 foi lida integralmente. NR-18 e NR-1 foram lidas por trechos; a aplicabilidade da NR-18 a reparo de rede em via pública depende do enquadramento do tenant [INFERÊNCIA].
2. Vigência da Portaria Inmetro 155/2022 frente à 295/2018, citada em FAQ do Inmetro atualizado em 2023 (OPE-24).
3. Lista vigente de produtos químicos controlados pela Polícia Federal (Portaria MJSP 204/2022 e 223/2022) e se algum insumo de ETA consta (OPE-06).
4. Redação em vigor do art. 45, §§ 6º e 7º, da Lei 11.445, na redação dada pela Lei 14.026 (OPE-03); o trecho foi lido no DOU, não no texto compilado.
5. Prazo de resposta e de execução por tipo de serviço fixado por cada ERI (OPE-15, OPE-26); a NR 11/ANA remete à ERI.
6. Relação entre `gis_zone` (DMA) e `dma_district` (OPE-01), já apontada em 05 L9.
7. Os limiares padrão de alarme de medidor inteligente (por exemplo, 12 h de fluxo contínuo) vêm de fornecedor; não são norma. Entram como parâmetro (OPE-18).
8. A NR 15/ANA tem verificação da adoção pela ANA a partir de maio de 2028 [O2]; a escolha de quando ofertar o módulo de perdas é do dono do produto, por cartão.

Decisões que este documento deixa para cartão do dono do produto (não presumidas): se as NR do MTE entram como obrigatórias no ADR-0041 (OPE-14); se o OPE-01 vira tarefa da onda B ou segue em 2028 (OPE-01); se a sala de situação usa tabela de estado nativa (OPE-22); se há extensão de série temporal no PostgreSQL (OPE-19); se o serviço de limpa-fossa é prestado pelo tenant ou só cadastrado (OPE-03); se a parte "gerador" da trilha GRS é antecipada (OPE-25).
