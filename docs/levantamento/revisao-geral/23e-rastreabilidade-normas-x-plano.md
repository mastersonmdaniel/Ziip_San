# 23e — Rastreabilidade entre as normas obrigatórias e o plano

Levantamento de 28/10/2026 sobre o [catálogo de normas](../../normas/catalogo-normas.json) (gerado em 22/10/2026,
1.676 normas) e o [plano de execução v2.21](../../PLANO-EXECUCAO-PDCA-R0-R9.md). Só documentação: o plano e o catálogo
não foram alterados, e nenhuma decisão do dono do produto foi revista. As propostas abaixo são recomendações. A matriz
completa, um registro por par norma e obrigação, está em [23e-matriz.json](23e-matriz.json).

## 0. Leitura rápida

- Recorte de 395 normas obrigatórias; 69 descartadas por não tocarem o software; 326 rastreadas em 501 pares norma e obrigação, contra 92 obrigações.
- Pares: 238 COBERTA (47,5%), 239 PARCIAL (47,7%) e 24 SEM_TAREFA (4,8%). Por norma: 118 cobertas, 185 parciais e 23 sem tarefa.
- O núcleo legal federal tem aceite verificável: corte e religação (Lei 14.015), NFAg, piso da tarifa social, negativação, quitação anual, qualidade da água e ouvidoria.
- A falha é estrutural: o perfil de cada agência de SC (prazos, avisos, encargos, média, vencimento) está em PARAMETROS-DO-TENANT, mas nenhuma tarefa o carrega e testa; 42 normas dependem de perfil por ERI, 18 delas de SC ou federais (RAS-01 e RAS-31).
- Seis lacunas altas: perfil das ERIs de SC, cashback de CBS e IBS na conta, CNPJ alfanumérico, conteúdo mínimo da fatura, classificação automática e avisos da tarifa social, e a premissa da P3.24 diante do prazo do débito automático.
- O SEM_TAREFA concentra-se em resíduos (MTR, CDF e DMR, 14 pares), CNPJ alfanumérico, retenção na fonte por órgão público, encarregado de dados, valor aproximado dos tributos e desconto por meio de pagamento.
- Grande parte do PARCIAL vem de achados 22a a 22d que entraram no aceite só como título ("Ajuste v2.18"); dar critério testável a eles resolve a maior parte.
- Vigências futuras: o §5 do plano já prevê NFAg, tarifa social, CBS e IBS, boleto híbrido e NR 11; faltam Pix Automático (01/01/2027 e 01/07/2027), cashback (01/2027 e 01/2029), NR-10 (01/06/2027), PCASP 2027 e CNPJ alfanumérico.

## 1. Método e critérios de seleção

**Universo.** Das 1.676 normas do catálogo, 795 têm `norm_class` REGULATORY; 748 delas estão VIGENTE, CONFIRMADA,
PARCIALMENTE_REVOGADA, VIGENCIA_FUTURA ou EM_REVISAO. Desse conjunto entraram:

| Recorte | Critério | Normas |
|---|---|---|
| Federal | `jurisdiction` BR ou BR-ANA | 215 |
| Santa Catarina (perfil prioritário) | SC, SC-ARIS, SC-ARESC, SC-AGIR, SC-AGR-TUBARAO, SC-CISAM-SUL | 106 |
| Outros estados | `kind` AGENCY_NORM com `cited_in` preenchido (citada nos documentos do projeto) | 74 de 427 |
| **Total** | | **395** |

Situação no recorte: 371 VIGENTE, 11 PARCIALMENTE_REVOGADA, 9 VIGENCIA_FUTURA e 4 EM_REVISAO. Nenhuma norma CONFIRMADA
entrou, porque no catálogo essa situação só ocorre nas classes REFERENCE, VOLUNTARY e EXPORT_PROFILE. O campo `cited_count` só está preenchido em 18
normas; por isso "mais citadas" foi lido como "citadas em algum documento do projeto" (`cited_in`). Entre as 74 de
outros estados há agências reguladoras de saneamento (ARSESP, ARSAE, ADASA, AGR-GO, AGERGS, AGEPAR, ARCE, AGENERSA,
ARES-PCJ, ARIS-MG) e órgãos ambientais estaduais classificados como agência no catálogo (CETESB, SIMA, COPAM, CONEMA,
FEPAM, CONSEMA).

**Normas de referência da ANA.** As NR 1 a 16 da ANA têm classe REFERENCE no catálogo e ficaram fora do recorte. Entram
de forma indireta: pelos atos REGULATORY que as citam (Res. ANA 230/2024, art. 74; Res. ANA 271/2025; Res. ANA
114/2021) e pelas resoluções das agências de SC que as adotam (AGIR, ARIS, ARESC, AGR Tubarão e CISAM). A seção 5
volta ao ponto.

**Descartes.** 69 normas não geram obrigação para um sistema de gestão do prestador:

| Código | Justificativa | Normas descartadas |
|---|---|---|
| D1 | Obrigação do empregador em SST, cumprida por programas e laudos de RH e SESMT; sem efeito direto no sistema comercial e operacional (apoio opcional pela ED-71). | mte-nr-4, mte-nr-5, mte-nr-7, mte-nr-9, mte-nr-11, mte-nr-12, mte-nr-13, mte-nr-15, mte-nr-16, mte-nr-17, mte-nr-20, mte-nr-21, mte-nr-23, mte-nr-24, mte-nr-25, mte-nr-26, mte-nr-38, lei-14457-2022 |
| D2 | Obra civil, resíduo de construção, de saúde ou industrial, ou substância em equipamento: fora do escopo do produto. | conama-307-2002, conama-348-2004, conama-431-2011, conama-448-2012, conama-469-2015, conama-358-2005, conama-313-2002, conama-516-2026, lei-15251-2010-sc |
| D3 | Norma de competência, de política geral, de classificação feita pelo órgão ambiental ou de sanção, sem obrigação operacional própria do sistema. | lei-6938-1981, lei-9605-1998, decreto-6514-2008, lc-140-2011, lei-12651-2012, conama-396-2008, sc-lei-13517-2005, sc-lei-17055-2016, tubarao-lc-020-2008, mg-dn-conjunta-8-2022 |
| D4 | Obrigação ou informação dirigida à ERI, ao titular ou à ANA, não ao prestador. | ana-res-134-2022, ana-in-1-2023, ana-in-1-2024, ana-lista-positiva-2025-aris, ana-ciclo-2023-nr1, ana-ciclo-2024-nr1, ana-ciclo-2026-aviso-abertura-1-2026, ana-lista-positiva-rsu-2025, ana-quadro-nr-1-a-16, ana-lista-positiva-agua-esgoto-2025 |
| D5 | Obrigação atendida por demonstrações financeiras, estudos econômicos ou ERP, fora do produto. | decreto-11598-2023, lei-13329-2016, rfb-in-2181-2024 |
| D6 | Obrigação de instituição financeira ou de pagamento; sem efeito no recebedor. | bcb-in-746, bcb-res-596-2026, bcb-res-597-2026, cmn-res-5299-2026, bcb-res-406-2024, bcb-res-541-2025, bcb-res-505-2025 |
| D7 | Trata da NF-e ou da NFC-e de mercadorias, que o produto não emite. | confaz-ajuste-sinief-49-2025, confaz-ajuste-sinief-8-2026, confaz-ajuste-sinief-20-2026, confaz-ajuste-sinief-27-2026, encat-nt-2025-002 |
| D8 | Efeito temporário já esgotado (pandemia). | sc-lei-17933-2020, aresc-res-173-2020, inmetro-portaria-295-2021 |
| D9 | Transporte de produto perigoso: obrigação do expedidor e do transportador. | decreto-96044-1988, antt-res-5998-2022 |
| D10 | Obrigação do fornecedor do software (registro e licença), tratada na P0.44; não é obrigação do prestador. | lei-9609-1998 |
| D11 | Sem destinatário no produto: o titular da conta é adulto e não há tratamento dirigido a crianças. | anpd-enunciado-1-2023 |

**Obrigações.** Para cada norma mantida, as obrigações que o sistema precisa cumprir ou apoiar foram escritas em palavras
próprias, a partir de `scope_summary`, `status_note` e `notes` do catálogo e dos levantamentos 08 a 14, 20 e 22. Normas
com o mesmo efeito no produto compartilham a obrigação (as dez resoluções de reajuste da ARESC, por exemplo, geram só a
OBR-17). Resultado: 92 obrigações (OBR-01 a OBR-93; o número 28 foi absorvido pela OBR-22), em 14 temas, e 501 pares.

**Busca no plano.** Três passadas: (a) número de cada norma em todo o plano, com borda numérica (só cerca de dez normas
aparecem pelo número: Decreto 5.440, Lei 14.015, Lei 14.898, Res. ANA 114, Portaria 888, Portaria Inmetro 155, Lei
13.460, Lei 14.063 e as NR-1, 18, 33 e 35 do MTE); (b) palavras-chave por tema; (c) leitura das 270 linhas de tarefa
(174 das fases P0 a P4, GL e LC e 96 entregas ED da trilha), do §2.3, do parágrafo "Prazos legais do prestador" (§5) e
dos riscos (§6). Para separar "previsto" de "aceito" foram lidos PARAMETROS-DO-TENANT, normas/decisoes-a-rever e
lacunas/22-INCORPORACAO-NO-PLANO.

**Classificação.**

- **COBERTA**: o aceite da tarefa, ou um ajuste com critério verificável, menciona a obrigação.
- **PARCIAL**: a tarefa existe, mas a obrigação está só em PARAMETROS-DO-TENANT, só como título de achado 22a a 22d no
  aceite ("Ajuste v2.18: ... CAD-75 Superendividamento ..."), ou o aceite cobre parte dela.
- **SEM_TAREFA**: nenhuma tarefa com ID trata a obrigação. Menção em parágrafo sem ID e sem aceite (a "trilha GRS, 2028,
  sob demanda", por exemplo) conta como SEM_TAREFA.
- Por norma: COBERTA se todos os pares são COBERTA, SEM_TAREFA se todos são SEM_TAREFA, PARCIAL nos demais casos.

**Gravidade das lacunas.** Alta: afeta conta, corte, documento fiscal ou cobrança do piloto de SC até o corte, ou gera
cobrança indevida ou autuação. Média: exigência de agência ou obrigação acessória com caminho alternativo. Baixa: fora de
SC, aplicação a confirmar ou efeito pequeno.

**Limites.** O catálogo traz resumos, não o texto das normas, e o texto integral não foi relido neste levantamento; as
obrigações herdam essa limitação. A classificação mede o plano, não o código. Datas citadas são fatos normativos, não
prazos de projeto.

## 2. Totais

**Por cobertura**

| Unidade | COBERTA | PARCIAL | SEM_TAREFA | Total |
|---|---|---|---|---|
| Pares norma e obrigação | 238 | 239 | 24 | 501 |
| Normas rastreadas | 118 | 185 | 23 | 326 |
| Obrigações | 42 | 44 | 6 | 92 |

**Por jurisdição (normas)**

| Recorte | COBERTA | PARCIAL | SEM_TAREFA | Descartadas | Total |
|---|---|---|---|---|---|
| Federal | 66 | 73 | 15 | 61 | 215 |
| Santa Catarina | 41 | 57 | 1 | 7 | 106 |
| Outros estados | 11 | 55 | 7 | 1 | 74 |

Em SC, o único SEM_TAREFA é a Portaria IMA 9/2026 (sistema MTR estadual). Nos outros estados, o PARCIAL alto decorre da
falta de perfil pré-carregado fora de SC (OBR-01) e de saídas para sistemas de agência sem leiaute (OBR-70).

**Por tema (pares)**

| Tema | Obrigações | Normas | Pares COBERTA | Pares PARCIAL | Pares SEM_TAREFA |
|---|---|---|---|---|---|
| A Condições gerais e atendimento | 15 | 79 | 32 | 74 | 0 |
| B Faturamento e tarifa | 14 | 71 | 67 | 53 | 0 |
| C Tarifa social | 4 | 23 | 16 | 19 | 0 |
| D Corte, cobrança e dívida | 9 | 35 | 27 | 17 | 0 |
| E Arrecadação e pagamentos | 5 | 18 | 5 | 10 | 4 |
| F Tributos e documentos fiscais | 9 | 32 | 26 | 12 | 4 |
| G Qualidade da água, esgoto e ambiente | 7 | 33 | 20 | 1 | 14 |
| H Medição e metrologia | 2 | 9 | 3 | 6 | 0 |
| I Indicadores, perdas e informação regulatória | 8 | 28 | 18 | 27 | 0 |
| J Contabilidade regulatória, ativos e contratos | 4 | 13 | 4 | 10 | 0 |
| K Dados pessoais, segurança e registros | 8 | 10 | 6 | 5 | 2 |
| L Acessibilidade e atendimento prioritário | 3 | 8 | 7 | 3 | 0 |
| M Segurança do trabalho na OS | 3 | 7 | 7 | 1 | 0 |
| N Cadastro técnico (GIS) | 1 | 1 | 0 | 1 | 0 |

**Por módulo do catálogo (`modules_affected`, normas; uma norma conta em cada módulo que afeta)**

| Módulo (modules_affected) | Normas COBERTA | Normas PARCIAL | Normas SEM_TAREFA |
|---|---|---|---|
| billing | 59 | 99 | 6 |
| registry | 10 | 75 | 2 |
| integration | 34 | 29 | 10 |
| regulatory-reporting | 17 | 51 | 0 |
| tenant-profile | 24 | 44 | 0 |
| collection | 13 | 49 | 5 |
| tariff | 34 | 27 | 0 |
| portal | 19 | 36 | 4 |
| dunning | 7 | 49 | 0 |
| customer-service | 12 | 42 | 0 |
| quality | 17 | 34 | 1 |
| accounting | 16 | 27 | 3 |
| platform | 26 | 16 | 3 |
| field-service | 7 | 29 | 0 |
| metering | 5 | 28 | 0 |
| assets | 16 | 9 | 2 |
| losses | 4 | 15 | 0 |
| grs | 3 | 4 | 12 |
| reading | 2 | 17 | 0 |
| maintenance | 11 | 5 | 2 |
| field | 13 | 3 | 2 |
| certification | 10 | 6 | 0 |
| lgpd | 3 | 8 | 1 |
| gis | 4 | 1 | 0 |

Os módulos `registry`, `dunning`, `reading` e `metering` têm proporção alta de PARCIAL porque recebem as condições gerais
das agências, cobertas só em parte pela falta de perfil (RAS-01). O módulo `grs` é o único com maioria SEM_TAREFA
(RAS-17).

## 3. Quadro de lacunas

Ordenado por gravidade. "Tarefa onde encaixa" indica a tarefa existente cujo aceite deveria receber o critério, ou onde
caberia tarefa nova; a decisão de criar ou mudar tarefa é do dono do produto.

| ID | Norma | Obrigação | Gravidade | Proposta | Tarefa onde encaixa |
|---|---|---|---|---|---|
| RAS-01 | aris-rn-19-2019 (e RN 20, 38, 40, 55), aresc-res-046-2016 (e 139, 259), agir-rn-001-2013, agr-tb-res-007-2013, cisam-meio-oeste-rn-2017-01 e 2026-05, cisam-sul-rn-03-2017 | OBR-01 perfil da ERI com vigência e validação | alta | Perfis das cinco ERIs de SC pré-carregados: cada chave de G1, G5, G6, G9 e das bifurcações com valor, artigo de origem e vigência; simulação antes de ativar; teste por perfil; troca de perfil na data (CISAM 05/2026 em 20/05/2027). Hoje o item só aparece como mitigação no §6 | P0.20 (ampliar o aceite) ou tarefa nova no P2, antes da GL.2 |
| RAS-02 | lc-214-2025, decreto-12955-2026 | OBR-50 cashback desconto de CBS e IBS na cobrança | alta | Aceite com cálculo por domicílio e período de aferição, uma cobrança por domicílio, lista da RFB (Decreto 12.955, art. 511), reflexo na NFAg e em relatório; CBS sobre consumo desde 01/2027 e IBS desde 01/2029; teste com domicílio elegível e não elegível. Hoje só o título GES-13 | P2.4 (com reflexo na P2.6) |
| RAS-03 | rfb-in-2229-2024, rfb-in-2119-2022 | OBR-53 CNPJ alfanumérico | alta | Validação do dígito verificador alfanumérico, índice cego e máscara no cadastro; importador; emitente e destinatário da NFAg e da NFS-e; arquivos bancários e Pix; teste com CNPJ alfanumérico em cada fronteira. O plano não menciona o tema, e a Receita atribui esse CNPJ a novas inscrições desde julho de 2026 | P1.3, P1.2, P2.6, P3.1 |
| RAS-04 | lei-11445-2007 (art. 39), lei-8078-1990 (art. 42-A), lei-12039-2009, decreto-5440-2005, aris-rn-19-2019, aresc-res-046-2016, arsae-resolucao-216-2025, arsesp-deliberacao-1029-2020 | OBR-19 e OBR-42 conteúdo mínimo da fatura e identificação do fornecedor | alta | Lista de campos obrigatórios por lei e por ERI como dado versionado, com teste de conteúdo do modelo da conta por perfil; antecipar o motor de mensagens legais (CAD-96) da ED-79 para o P2 | P2.17 e P2.8, antes da GL.2 |
| RAS-05 | lei-14898-2024, decreto-11016-2022, aris-rn-56-2026, aresc-res-348-2025, cisam-meio-oeste-rn-2026-04 | OBR-31 classificação automática e avisos da tarifa social | alta | Classificação pela lista oficial com origem conforme a natureza jurídica; aviso na fatura antes da perda do benefício; recusa de manter fora quem entregou documento (cobrança indevida, art. 5º, §2º); teste por caso. Hoje só os títulos CAD-91, CAD-96 e PLT-36 | P1.5 (parte da ED-57 antecipada) |
| RAS-06 | cmn-res-5278-2025, bcb-res-544-2025, cmn-res-4790-2020, bcb-res-51-2020 | OBR-45 migração do débito automático para Pix Automático | alta | Obter o parecer já previsto (enquadramento de água e esgoto como convênio de serviço público) antes de iniciar a P3.24; se enquadrar, o FEBRABAN 150 legado perde objeto no corte de 2029 e o trabalho vira campanha de conversão com consentimento guardado. A nota de 22/10/2026 da P3.24 registra as datas, mas o aceite não mudou | P3.24, P4.5, ED-27 |
| RAS-07 | aris-rn-57-2026, agir-rn-025-2026, aris-rn-36-2022 | OBR-69 indicadores operacionais da NR 9 | média | Catálogo de fábrica das fichas adotadas pelas ERIs de SC, com variável, fórmula, origem no produto e teste de cálculo sobre massa sintética | ED-77 e ED-48 |
| RAS-08 | aris-in-dg-1-2025, aris-rn-11-2017, agir-rn-013-2022 | OBR-70 e OBR-04 envio no sistema e formato da agência | média | Leiaute do SISARIS por módulo e relatório trimestral de SAC e ouvidoria da AGIR, com validação antes do envio e evidência na ED-98 | ED-97 |
| RAS-09 | lc-214-2025, lc-227-2026 | OBR-51 split payment | média | Conciliação do valor líquido com o tributo segregado na liquidação, por modo de recolhimento; teste por modo | P2.4 e P3.1 |
| RAS-10 | lei-13146-2015 (art. 62), decreto-5296-2004 | OBR-87 conta em formato acessível a pedido | média | Indicador de formato no cadastro (CAD-81) e no arquivo da gráfica (braile, fonte ampliada), PDF acessível no portal; teste | P1.3 e P2.8 |
| RAS-11 | lei-14181-2021, decreto-11150-2022, decreto-11567-2023, mp-1393-2026 | OBR-40 superendividamento e renegociação | média | Plano de repactuação com mínimo existencial informado, suspensão da cobrança durante o processo e, se houver parecer favorável, cessão a programa federal; hoje só os títulos CAD-73 e CAD-75 | P4.1 |
| RAS-12 | sc-lei-11959-2001, sc-lei-17780-2019 | OBR-36 vedações estaduais de corte | média | Pontos facultativos em `cal.holidays` e janela das 8h por UF; teste de corte às 7h de segunda-feira e em ponto facultativo | P3.4 |
| RAS-13 | stj-tema-414, lei-13312-2016, agr-tb-res-040-2024 | OBR-25 condomínio e múltiplas economias | média | Cobrança por economia com franquia e excedente, recusa do modelo híbrido vedado, apoio à medição individualizada; teste | P2.5 (ED-15 completa depois) |
| RAS-14 | aresc-res-158-2020, aris-rn-38-2023, condições gerais de SC | OBR-20 leitura fora da janela e média com teto | média | Testar `meter.average_months`, o teto de ciclos estimados e o ajuste proporcional por dias (ARESC: acima de 33 dias) | P2.5 |
| RAS-15 | stj-sumula-412, stj-tema-251-254, decreto-20910-1932, lei-10406-2002 | OBR-41 prescrição por pretensão | média | Prazo por natureza jurídica e pretensão; bloqueio de negativação, protesto e ajuizamento de dívida prescrita; teste com relógio | P4.1, ED-30, ED-31 |
| RAS-16 | cmn-res-5171-2024, lei-10406-2002 | OBR-93 teto de juros e taxa legal | média | Citar no aceite da P0.20 cada limite de PARAMETROS com teste nomeado; taxa legal pela divulgação mensal do BCB | P0.20 e P2.5 |
| RAS-17 | sc-ima-portaria-009-2026, lei-12305-2010, decreto-10936-2022, ibama-in-13-2012 | OBR-63 MTR, CDF e DMR | média | Manter a decisão "2028, sob demanda", mas dar ID e aceite à trilha GRS, com o sistema do IMA como alvo em SC; até lá, a obrigação como item manual da agenda | trilha GRS (sem ID) e ED-98 |
| RAS-18 | lei-8159-1991 | OBR-83 tabela de temporalidade de prestador público | média | Para tenant de natureza pública, exigir tabela de temporalidade e listagem de eliminação antes de expurgo e da eliminação de fim de contrato; teste por `reg.legal_nature` | P3.19 e P3.16 |
| RAS-19 | bcb-res-587-2026 | OBR-44 cobrança híbrida | média | Boleto e Pix do mesmo documento com baixa única e devolução do segundo pagamento; teste | P3.2 (devolução pela P3.29) |
| RAS-20 | anpd-res-19-2024, lei-13709-2018 | OBR-81 transferência internacional | média | Cláusulas-padrão da ANPD no contrato com provedores fora do país (modelo de linguagem, Meta, Telegram) e registro no RIPD | P3.9 e ED-43 |
| RAS-21 | agir-rn-011-2020, aris-rn-33-2021 | OBR-74 contabilidade regulatória | média | Dar critério à GES-14: catálogo MCRSB versionado, de-para com o plano do tenant, balancetes por contrato e município | ED-32 |
| RAS-22 | aresc-res-105-2018, agergs-ren-74-2026, lei-11445-2007 (art. 45) | OBR-13 cobrança por disponibilidade e TFDI | média | Componente fixo por disponibilidade de infraestrutura pela regra da ERI; teste com unidade não conectada | P2.5 e ED-88 |
| RAS-23 | anpd-res-18-2024, lei-13853-2019 | OBR-80 encarregado de dados do tenant | baixa | Campo do encarregado no perfil do tenant, exibido no portal, na política de privacidade e nos documentos | P3.3 e P3.20 |
| RAS-24 | lei-9791-1999, lei-8987-1995 | OBR-23 datas de vencimento | baixa | Testar no mínimo seis datas para concessionária e a antecedência entre entrega e vencimento | P2.5 |
| RAS-25 | mte-nr-10 | OBR-90 serviço em eletricidade | baixa | NR-10 no catálogo de SST da OS, com a redação que vigora em 01/06/2027 | P1.31 e ED-71 |
| RAS-26 | sc-lei-12465-2002 | OBR-88 tempo de espera presencial | baixa | Hora da senha e do atendimento; relatório contra 15 e 30 minutos | ED-40 |
| RAS-27 | in-rfb-1234-2012, rfb-in-2145-2023, rfb-in-2239-2024 | OBR-47 pagamento com retenção na fonte | baixa | Baixa "com retenção", com o valor retido como crédito do tenant, depois do parecer sobre o alcance | P3.1 e ED-26 |
| RAS-28 | lei-12741-2012, decreto-8264-2014 | OBR-54 valor aproximado dos tributos | baixa | Parecer; se aplicável, campo na fatura com fonte de alíquotas | P2.17 |
| RAS-29 | agergs-ren-37-2017 | OBR-09 compensação por interrupção prolongada | baixa | Regra opcional de crédito a partir da ocorrência de rede | ED-92 |
| RAS-30 | mp-1396-2026 | OBR-85 remetente autenticado | baixa | Provedores de SMS e voz com identificação da pessoa jurídica do tenant; acompanhar a conversão da MP | P3.5 e ED-41 |
| RAS-31 | arsesp-deliberacao-106-2009, arsae-resolucao-131-2019, adasa-res-14-2011, agr-go-rn-09-2014, agergs-rn-66-2022, agepar-res-3-2020, arce-res-130-2010, ares-pcj-resolucao-50-2014 | OBR-01 perfil de ERI fora de SC | baixa | Perfis fora de SC sob demanda de edital ou tenant; até lá, valores próprios aceitos pelo tenant | ED-97 e trilha |
| RAS-32 | stn-mf-portaria-2520-2026 | OBR-77 PCASP do exercício | baixa | Catálogo PCASP versionado por exercício, com de-para na ED-32 | ED-32 |
| RAS-33 | aris-rn-47-2025, agir-rn-024-2025, aresc-res-431-2026 | OBR-12 solução alternativa nas metas | baixa | Regra de cômputo da solução alternativa na cobertura, por ERI | ED-77 |

## 4. Vigências futuras e revisões

Mecanismos do plano que tratam data e troca de versão: parâmetros com vigência sem sobreposição (P0.19), alíquotas com
vigência (P2.4), estrutura tarifária com vigência e cópia de versão (P2.29), catálogo de normas versionado com selo de
vigência futura e alerta ao tenant quando a situação muda (P1.34), catálogo de padrão de efluente com vigência futura
(ED-80), data de desligamento do débito legado parametrizada (ED-27, P3.24) e o parágrafo "Prazos legais do prestador"
(§5).

| Norma | Situação | Data (fato normativo) | Data no plano | Troca de versão prevista | Avaliação |
|---|---|---|---|---|---|
| svrs-nfag-e-modelo-75, moc-nfag-modelo-75 | VIGENCIA_FUTURA | 01/12/2026 (Simples Nacional: 01/01/2027) | sim (§5, §2.4, §6) | sim: builder gerado do XSD e vetores por versão (§6), corpus XML (P1.10) | adequado; manual definitivo pendente; o prestador emite por outro meio até o corte |
| nt-2026-004-rtc-nfag | VIGENCIA_FUTURA | produção em 16/11/2026; soma de IBS e CBS no total a partir de 2027 | não | sim, pelo mesmo mecanismo de leiaute | incluir a regra de 2027 nos vetores da P2.6 |
| bcb-res-587-2026 | VIGENCIA_FUTURA | 01/02/2027 (cobrança híbrida) e 01/07/2027 (arts. 3º e 11-S) | só 01/02/2027 (§5) | sem mecanismo próprio | RAS-19; registrar 01/07/2027 no §5 |
| bcb-in-777-2026 | VIGENCIA_FUTURA | 01/02/2027 (Manual de Fluxos do Pix 2.2) | não | não | efeito absorvido pelo PSP; conferir na P3.2 |
| mte-nr-10 | VIGENCIA_FUTURA | 01/06/2027; instalações existentes, alínea específica, cerca de 01/06/2028 | não | catálogo de SST da P1.31 sem a NR-10 | RAS-25 |
| stn-mf-portaria-2520-2026 | VIGENCIA_FUTURA | exercício de 2027 | não | regra contábil configurável (ED-32), sem catálogo por exercício | RAS-32 |
| cisam-meio-oeste-rn-2026-05 | VIGENCIA_FUTURA | 20/05/2027, revoga a RN 01/2017 | data da NR 11 no §5, não a do ato | troca possível por vigência de parâmetro (P0.19), sem perfil carregado | RAS-01 |
| conama-430-2011 | EM_REVISAO | nova resolução aprovada em 02/09/2026, sem publicação | não | sim: catálogo de efluente com vigência futura (ED-80) | adequado; não codificar limites antes da publicação |
| cnrh-54-2005, cnrh-121-2010 | EM_REVISAO | resolução conjunta de reúso aprovada em 01/09/2026, sem publicação; adequação de 36 meses noticiada | não | ED-80 trata reúso sem vigência explícita | aguardar publicação; usar o mesmo catálogo com vigência |
| inmetro-portaria-657-2025 | EM_REVISAO (suspensa pela 713/2025) | sem efeito | não | ED-14 segue a verificação inicial | adequado; não construir sobre a declaração de conformidade |
| lei-14898-2024 | VIGENTE com prazo | adequação até 11/12/2026 | sim (§5, M5) | piso federal travado (P1.5) | adequado na data; RAS-05 no conteúdo |
| cmn-res-5278-2025, bcb-res-544-2025 | VIGENTE com prazo | 01/01/2027 (convênios de serviço público) | sim (resumo da v2.21 e nota da P3.24), fora do §5 | data de desligamento parametrizada | RAS-06 |
| bcb-res-565-2026 (classe REFERENCE no catálogo) | VIGENCIA_FUTURA | 01/07/2027 (novo regime do débito automático) | sim (resumo da v2.21 e nota da P3.24), fora do §5 | data de desligamento parametrizada | RAS-06; rever a classe no catálogo |
| lc-214-2025, decreto-12955-2026 | VIGENTE com transição | CBS e IBS em 01/01/2027; cashback de CBS sobre consumo desde 01/2027 e de IBS desde 01/2029 | só CBS e IBS (§5) | alíquota com vigência (P2.4) | RAS-02 e RAS-09 |
| ana-res-230-2024-nr11-art-74 | VIGENTE com marco | verificação das condições gerais da NR 11 a partir de 20/05/2027 | sim (§5) | depende de perfil por ERI | RAS-01 |
| ato-conjunto-rfb-cgibs-4-2026 | VIGENTE com etapas | NFS-e: 01/10/2026, 01/12/2026 e 01/01/2027 por grupo de serviço | não (só em decisoes-a-rever) | P3.6 sem etapa | definir na P3.6 a etapa de cada serviço avulso |
| rfb-in-2229-2024 | VIGENTE | CNPJ alfanumérico em novas inscrições desde julho de 2026 | não | não | RAS-03 |

Observação sobre o mecanismo geral: a P1.34 alerta quando a situação de uma norma muda, mas a mudança depende de nova
versão do catálogo, revisada por outra pessoa. Uma norma em VIGENCIA_FUTURA não passa a VIGENTE sozinha na data. Um
campo de data de efeito no catálogo, com transição automática e alerta antecipado na agenda da ED-98, fecharia o ciclo.

## 5. Inferências

1. **A cobertura alta do núcleo convive com um ponto único de falha.** As regras que mais pesam na conta e no corte
   estão em PARAMETROS-DO-TENANT, mas o plano não tem tarefa que carregue o perfil de cada agência de SC e o teste.
   Uma tarefa de perfis (RAS-01) resolveria os 18 pares da OBR-01 de normas de SC e federais e daria a base de
   RAS-12, RAS-14, RAS-16 e RAS-24, que tratam regras das mesmas condições gerais.
2. **A P0.20 tem aceite genérico.** "Cada limite com norma e teste" não diz quais limites. Se o aceite listar as chaves
   travadas de PARAMETROS (vencimento, média, juros, bloco do fornecedor), RAS-16 e RAS-24 passam a COBERTA sem tarefa
   nova, e a RAS-14 fica reduzida ao ajuste proporcional por intervalo de leitura, que ainda não tem chave.
3. **Os achados 22a a 22d entraram como ponteiro, não como critério.** Quinze achados citados só pelo título no aceite
   sustentam obrigações classificadas como PARCIAL (CAD-73, CAD-75, CAD-81, CAD-87, CAD-88, CAD-91, CAD-96, GES-03,
   GES-13, GES-14, GES-15, GES-16, GES-18, PLT-10, PLT-27). Converter o título em critério antes de cada tarefa
   começar resolve a maior parte do PARCIAL.
4. **O calendário do produto não coincide com o dos prestadores.** A primeira produção é no corte do lançamento (por
   volta de fevereiro de 2029); até lá o piloto cumpre pelo sistema atual, como o §5 registra. O produto precisa chegar
   com as regras de 2029, não com as de hoje: cashback de IBS (01/2029), CBS integral, NR 11 já verificada pelas ERIs,
   Pix Automático como único débito em conta para convênios de serviço público, se o parecer confirmar.
5. **As normas de referência da ANA estão subestimadas no recorte.** Como são REFERENCE, entram só pela adoção das
   agências. A chave `reg.ana_nr_adoption` já existe em PARAMETROS; ligar essa chave ao catálogo (norma ativa por
   adoção) daria rastreabilidade direta às NR 8, 9, 11, 13 e 15.
6. **Há inconsistências de classe no catálogo que afetam a seleção.** A Res. BCB 565/2026, que rege o novo regime de
   débito automático, está como REFERENCE; o recorte por REGULATORY a deixaria de fora se não fosse citada no plano.
   Convém revisar a classe das resoluções do BCB e das NR da ANA na próxima versão do catálogo (sem alteração agora).
7. **O bloco de resíduos é o maior SEM_TAREFA por decisão de escopo.** A trilha GRS ficou para 2028 sob demanda; a
   decisão é do dono e não foi alterada. O risco é o prestador do piloto gerar lodo de ETA ou ETE sujeito ao MTR do IMA
   sem apoio do sistema; a agenda da ED-98 cobre o lembrete.
8. **Fora de SC, a cobertura é deliberadamente parcial.** 55 das 73 normas de outros estados ficaram PARCIAL porque não
   há perfil pré-carregado. É coerente com o foco em SC, mas pesa em edital fora do estado (RAS-31).
9. **`related_tasks` do catálogo está preenchido em 59 normas e com ruído.** A NFAg aparece ligada a 37 tarefas e a
   NFS-e nacional a tarefas de backup. A matriz 23e pode alimentar esse campo numa revisão futura do catálogo, sem
   alteração agora.
10. **Pontos que dependem de parecer** e não devem virar regra antes dele: enquadramento do débito automático (RAS-06),
    adesão à MP 1.393/2026 (RAS-11), valor aproximado dos tributos na conta (RAS-28), retenção por estatal (RAS-27) e
    protesto de CDA (OBR-38). Todos constam de normas/questoes-para-parecer-juridico ou de normas/decisoes-a-rever.

## Anexo A — Matriz por obrigação

Cada linha traz a obrigação, a cobertura, as tarefas e as normas que a geram. Quando a cobertura de uma norma difere da
obrigação, ela aparece entre parênteses. O detalhe por par, com notas, está em [23e-matriz.json](23e-matriz.json).

**A. Condições gerais e atendimento**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-01 Parametrizar o perfil da ERI do tenant (ato de condições gerais, prazos, avisos, encargos, média, religação) com vigência e validar o conjunto antes da produção | PARCIAL | P0.19, P0.20, P3.28, ED-97 | lei-14026-2020, lei-9984-2000, ana-res-230-2024-nr11-art-74, agir-rn-001-2013, agr-tb-res-007-2013, aresc-res-046-2016, aresc-res-139-2019, aresc-res-259-2023, aris-rn-19-2019, aris-rn-20-2019, aris-rn-38-2023, aris-rn-40-2024, aris-rn-55-2026, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, sc-dec-2138-2009, sc-lei-16673-2015, ares-pcj-res-363-2020, ares-pcj-res-610-2025, ares-pcj-resolucao-198-2017, ares-pcj-resolucao-460-2022, ares-pcj-resolucao-50-2014, aris-mg-resolucao-185-2025, arsae-resolucao-129-2019, arsae-resolucao-131-2019, arsae-resolucao-140-2020, arsae-resolucao-156-2021, arsae-resolucao-165-2022, arsae-resolucao-179-2023, arsesp-deliberacao-106-2009, arsesp-deliberacao-809-2018, agepar-res-3-2020, agepar-res-38-2025, agergs-rn-66-2022, arce-res-04-2022, arce-res-130-2010, adasa-res-14-2011, agr-go-rn-09-2014, agr-go-rn-106-2017, agr-go-rn-196-2022, agr-go-rn-80-2016 |
| OBR-02 Registrar toda solicitação em protocolo único com prazo por tipo de serviço, contagem em dias úteis ou corridos com feriados locais e prorrogação justificada | COBERTA | P3.4, ED-35, P1.31 | lei-13460-2017, agir-rn-001-2013, agir-rn-013-2022, agr-tb-res-007-2013, aresc-res-046-2016, aresc-res-259-2023, aris-rn-19-2019, aris-rn-20-2019, aris-rn-40-2024, aris-rn-55-2026, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, agenersa-in-103-2023, agenersa-in-122-2024, arsae-resolucao-94-2017, agr-go-rn-207-2023 |
| OBR-03 Manter ouvidoria como segunda instância, com prazos da Lei 13.460/2017, identidade preservada e relatório anual | COBERTA | ED-35 | lei-13460-2017, agir-rn-012-2021, aris-rn-41-2024, arsae-resolucao-151-2021 |
| OBR-04 Produzir os relatórios periódicos de reclamações, SAC e ouvidoria pedidos pela agência | PARCIAL | P3.22, ED-35, ED-97 | agir-rn-013-2022 |
| OBR-05 Avisar com antecedência as interrupções programadas aos usuários afetados e à agência e atender unidades essenciais | COBERTA | ED-35, ED-54, P3.5 | agir-rn-005-2015, aris-rn-45-2025 |
| OBR-06 Publicar e manter a Carta de Serviços ao usuário | COBERTA | ED-79 | lei-13460-2017 |
| OBR-07 Publicar e versionar o contrato de adesão e o regulamento da prestação | COBERTA | P3.20 | agir-rn-001-2013, aris-rn-19-2019 |
| OBR-08 Cumprir prazos de vistoria, ligação nova e conexão à rede, distinguindo área urbana e rural quando a norma pedir | PARCIAL | P3.4, ED-36, ED-84 | agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, aris-rn-42-2025, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, arsesp-deliberacao-804-2018 |
| OBR-09 Creditar compensação ao usuário por falha do prestador (corte indevido, atraso de religação, interrupção prolongada, não conformidade) | PARCIAL | P3.4, ED-22 | aris-rn-19-2019, agergs-ren-37-2017 |
| OBR-10 Manter plano de contingência e emergência com rodízio, racionamento, abastecimento alternativo e comunicação ao usuário | COBERTA | ED-91, ED-35 | aresc-res-156-2020, aris-rn-45-2025 |
| OBR-11 Cadastrar soluções individuais de esgoto, programar a limpeza e cobrar o serviço conforme a agência | COBERTA | ED-88 | agir-rn-015-2024, aresc-res-422-2026, aris-rn-39-2023, aris-rn-48-2025 |
| OBR-12 Computar soluções alternativas nas metas de universalização e informá-las à agência | PARCIAL | ED-88, ED-77 | lei-14026-2020, agir-rn-024-2025, aresc-res-431-2026, aris-rn-47-2025, ares-pcj-resolucao-649-2025, arsae-resolucao-212-2025 |
| OBR-13 Conduzir o ciclo da ligação obrigatória à rede e a cobrança por disponibilidade ou tarifa fixa de infraestrutura | PARCIAL | ED-88, P2.5 | decreto-7217-2010, lei-11445-2007, aresc-res-105-2018, aris-rn-42-2025, agergs-ren-74-2026 |
| OBR-14 Receber redes de loteamentos e empreendimentos e tratar o ressarcimento ao empreendedor | PARCIAL | ED-84 | arsesp-deliberacao-1751-2025 |
| OBR-15 Atender à fiscalização da agência: fornecer dados sob demanda e registrar autos, penalidades e compromissos de ajuste com prazo e evidência | PARCIAL | ED-98, P2.25, ED-96 | agir-rn-002-2013, agir-rn-003-2013, agr-tb-res-046-2025, aresc-res-195-2022, aris-rn-17-2019, aris-rn-18-2019, cisam-meio-oeste-rn-2017-03, sc-lei-16673-2015, adasa-res-65-2025 |

**B. Faturamento e tarifa**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-16 Aplicar estrutura tarifária por categoria e faixa com versão, vigência e antecedência mínima de publicação | COBERTA | P2.5, P2.29 | decreto-7217-2010, lei-11445-2007, lei-8987-1995, ana-res-271-2025-nr13, stj-sumula-407, stj-tema-932-153, aresc-res-139-2019 |
| OBR-17 Carregar cada ato de reajuste ou revisão do prestador como nova versão tarifária com ato, data e vigência | COBERTA | P2.29 | agir-rn-008-2019, agir-rn-009-2019, agir-rn-021-2025, agr-tubarao-decreto-municipal-9159-2026, aresc-res-111-2018, aresc-res-255-2023, aresc-res-309-2024, aresc-res-321-2025, aresc-res-324-2025, aresc-res-335-2025, aresc-res-364-2025, aresc-res-374-2025, aresc-res-375-2025, aresc-res-389-2026, aresc-res-396-2026, aresc-res-427-2026, aris-rn-26-2021, aris-rn-31-2021, aris-rn-50-2025, aris-rn-51-2025, aris-rn-53-2025, cisam-meio-oeste-rn-2015-03, cisam-meio-oeste-rn-2025-03-nr06, cisam-sul-rn-03-2016, sc-dec-1035-2008, arsae-resolucao-217-2025 |
| OBR-18 Fornecer à agência mercado e receita por categoria e faixa para reajuste e revisão tarifária | COBERTA | P3.22, P2.29 | agir-rn-008-2019, agir-rn-009-2019, agir-rn-021-2025, aris-rn-26-2021, aris-rn-31-2021, aris-rn-50-2025, aris-rn-53-2025, cisam-meio-oeste-rn-2015-03, cisam-meio-oeste-rn-2025-03-nr06, cisam-sul-rn-03-2016 |
| OBR-19 Emitir a fatura com o conteúdo mínimo da lei e da agência (prestador, histórico, leituras, tributos, mensagens legais, qualidade, canais, lacre) em modelo aprovado | PARCIAL | P2.8, P2.17, P2.24, P3.35, ED-79 | decreto-5440-2005, decreto-7217-2010, lei-11445-2007, lei-8078-1990, agir-rn-001-2013, agr-tb-res-007-2013, aresc-res-046-2016, aresc-res-259-2023, aris-rn-19-2019, aris-rn-20-2019, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, arsae-resolucao-131-2019, arsae-resolucao-216-2025, arsesp-deliberacao-1029-2020, arsesp-deliberacao-1206-2021, agr-go-rn-196-2022 |
| OBR-20 Ajustar a conta quando o intervalo de leitura foge da janela e faturar por média com teto de ciclos estimados | PARCIAL | P2.5 | agir-rn-001-2013, aresc-res-046-2016, aresc-res-158-2020, aris-rn-19-2019, aris-rn-38-2023, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017 |
| OBR-21 Revisar consumo, conceder desconto por vazamento oculto e aferir hidrômetro com laudo | COBERTA | P3.28, ED-23 | agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, agr-go-rn-188-2022 (PARCIAL) |
| OBR-22 Retificar conta sem apagar a anterior, devolver em dobro a cobrança indevida e tratar pagamento em duplicidade | COBERTA | P2.20, P3.1, P3.29 | lei-10406-2002, lei-8078-1990, arsae-resolucao-184-2023 |
| OBR-23 Oferecer datas de vencimento (no mínimo seis para concessionária) e respeitar antecedência entre entrega e vencimento | PARCIAL | P2.20, P2.5 | lei-8987-1995, lei-9791-1999, agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, arsae-resolucao-131-2019 |
| OBR-24 Limitar a multa a 2% e não cobrar encargo sobre conta em revisão ou contestada | COBERTA | P0.20, P3.1, P2.20 | lei-8078-1990, lei-9298-1996, agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017 |
| OBR-93 Limitar os juros de mora ao teto da agência e aplicar a taxa legal quando nada for pactuado | PARCIAL | P0.20, P2.5 | lei-10406-2002, cmn-res-5171-2024, agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017 |
| OBR-25 Faturar condomínio e múltiplas economias pelo modelo admitido (franquia por economia mais excedente) e apoiar a medição individualizada | PARCIAL | ED-15, P2.5 | lei-11445-2007, lei-13312-2016, stj-tema-414, agr-tb-res-040-2024 |
| OBR-26 Cobrar esgoto pela regra do contrato e da agência (percentual, coleta sem tratamento, fonte alternativa) e não cobrar sem conexão | PARCIAL | P2.5, ED-13 | stj-tema-565, sc-dec-1035-2008, arsesp-deliberacao-1117-2021, arsesp-deliberacao-221-2011 |
| OBR-27 Cobrar o manejo de resíduos sólidos na fatura de água (cofaturamento) com isenção e repasse | COBERTA | ED-25 | ana-res-114-2021, agir-rn-018-2025, agir-rn-022-2025, aris-rn-46-2025, aris-rn-49-2025, arsesp-deliberacao-1734-2025 |
| OBR-29 Emitir a declaração anual de quitação de débitos e comprová-la quando a agência pedir | COBERTA | P2.24 | lei-12007-2009, agenersa-in-148-2026 (PARCIAL) |

**C. Tarifa social**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-30 Conceder a tarifa social pelos critérios federais (CadÚnico, BPC, renda), sem exigir adimplência, com piso federal travado | COBERTA | P1.5 | lei-11445-2007, lei-14898-2024, ana-aviso-encerramento-prazo-tarifa-social-2026, ana-lei-14898-2024-tarifa-social, ana-res-271-2025-nr13, agr-tb-res-043-2025, aresc-res-313-2024, aresc-res-348-2025, aris-rn-56-2026, cisam-meio-oeste-rn-2026-04 |
| OBR-31 Classificar automaticamente os beneficiários pela lista oficial conforme a natureza jurídica e avisar na fatura antes da perda do benefício | PARCIAL | P1.5, ED-57 | decreto-11016-2022, lei-14898-2024, agr-tb-res-043-2025, aresc-res-313-2024, aresc-res-348-2025, aris-rn-56-2026, cisam-meio-oeste-rn-2026-04 |
| OBR-32 Aplicar categorias sociais e subsídios definidos pela agência ou pelo ente (social II, vulnerável, subvenção) como parâmetros com vigência | PARCIAL | P1.5, ED-22 | ana-res-271-2025-nr13, agir-rn-001-2013, ares-pcj-resolucao-592-2024, arsae-resolucao-189-2024, arsae-resolucao-227-2026, arsesp-deliberacao-1544-2024, arsesp-deliberacao-1699-2025, agepar-res-36-2025, agergs-red-892-2026, adasa-res-06-2010, adasa-res-42-2024, agr-go-rn-186-2021 |
| OBR-33 Informar à agência a lista de beneficiários, os subsídios e o relatório anual da tarifa social | COBERTA | ED-97 | lei-14898-2024, ana-lista-positiva-tarifa-social-2026-07, agr-tb-res-043-2025, aresc-res-348-2025, aris-rn-56-2026, cisam-meio-oeste-rn-2026-04 |

**D. Corte, cobrança e dívida**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-34 Suspender por inadimplência só com aviso prévio entregue, fora das janelas vedadas, respeitando classes protegidas e teto de idade do débito, com revalidação na execução | COBERTA | P3.4, P3.5, ED-20, ED-29 | decreto-7217-2010, lei-11445-2007, lei-14015-2020, lei-8987-1995, agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, aris-rn-38-2023, aris-rn-55-2026, aris-rn-56-2026, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017, sc-dec-2138-2009, adasa-res-14-2011 |
| OBR-35 Religar nos prazos normal e de urgência, sem taxa quando o corte for indevido, com OS gerada pelo pagamento | COBERTA | P3.4 | lei-14015-2020, agir-rn-001-2013, aresc-res-046-2016, aris-rn-19-2019, cisam-meio-oeste-rn-2017-01, cisam-meio-oeste-rn-2026-05, cisam-sul-rn-03-2017 |
| OBR-36 Aplicar vedações estaduais de corte que vão além da lei federal (ponto facultativo, janela por horário) | PARCIAL | P3.4 | sc-lei-11959-2001, sc-lei-17780-2019, stf-adi-7725-to-2025 |
| OBR-37 Negativar só com aviso prévio comprovado, excluir em até 5 dias úteis após o pagamento e não manter registro além de 5 anos | COBERTA | ED-30 | lei-8078-1990, stj-sumula-323, stj-sumula-359, stj-sumula-548 |
| OBR-38 Inscrever em dívida ativa, emitir CDA e apoiar execução fiscal, protesto e transação quando o prestador for ente público | PARCIAL | ED-31, ED-30 | lc-236-2026, lei-12767-2012, lei-6830-1980 |
| OBR-39 Oferecer parcelamento com demonstrativo e bloquear o corte durante contestação | COBERTA | P4.1, P2.20 | lei-8078-1990 |
| OBR-40 Preservar o mínimo existencial no superendividamento e operar programas de renegociação do ente ou da União | PARCIAL | P4.1 | decreto-11150-2022, decreto-11567-2023, lei-14181-2021, mp-1393-2026 |
| OBR-41 Separar a prescrição por pretensão e natureza jurídica e impedir cobrança judicial, negativação e protesto de dívida prescrita | PARCIAL | P0.20, P3.1, ED-30 | decreto-20910-1932, lei-10406-2002, stj-sumula-412, stj-tema-251-254, stj-tema-932-153 |
| OBR-42 Identificar o fornecedor (nome, endereço, CPF ou CNPJ) em todo documento de cobrança | PARCIAL | P2.8, P2.17 | lei-12039-2009, lei-8078-1990 |

**E. Arrecadação e pagamentos**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-43 Emitir e conciliar Pix Cobrança e boleto pelos regulamentos dos arranjos, com webhook seguro e baixa idempotente | COBERTA | P3.1, P3.2 | bcb-in-777-2026, bcb-res-1-2020, bcb-res-443-2024, bcb-res-467-2025, bcb-res-515-2025 |
| OBR-44 Operar a cobrança híbrida (boleto com Pix) sem pagamento em duplicidade a partir da vigência da regra | PARCIAL | P3.2 | bcb-res-587-2026 |
| OBR-45 Migrar o débito automático para Pix Automático nos prazos do Banco Central e guardar o consentimento | PARCIAL | P4.5, ED-27, P3.24 | bcb-in-743-2026, bcb-res-402-2024, bcb-res-482-2025, bcb-res-51-2020, bcb-res-544-2025, bcb-res-587-2026, cmn-res-4790-2020, cmn-res-5251-2025, cmn-res-5278-2025 |
| OBR-46 Informar com clareza desconto por meio ou prazo de pagamento, se o tenant o oferecer | SEM_TAREFA | — | lei-13455-2017 |
| OBR-47 Registrar pagamento de órgão público com retenção de tributo na fonte sem tratá-lo como pagamento a menor | SEM_TAREFA | — | in-rfb-1234-2012, rfb-in-2145-2023, rfb-in-2239-2024 |

**F. Tributos e documentos fiscais**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-48 Emitir NFAg modelo 75 por fornecimento no leiaute vigente (MOC, NT), com contingência, cancelamento, substituição, DANFAG e guarda do XML | COBERTA | P2.6, P2.18, P2.20, P3.7, ED-19 | decreto-12955-2026, lc-214-2025, ato-conjunto-rfb-cgibs-1-2025, ato-conjunto-rfb-cgibs-4-2026, ato-tecnico-conjunto-rfb-cgibs-1-2026, ato-tecnico-conjunto-rfb-cgibs-2-2026, ato-tecnico-conjunto-rfb-cgibs-8-2026, moc-nfag-modelo-75, nt-2026-002-rtc-nfag, nt-2026-004-rtc-nfag, resolucao-cgibs-6-2026, svrs-nfag-e-modelo-75 |
| OBR-49 Calcular e destacar CBS e IBS com alíquotas e regras de transição por vigência | COBERTA | P2.4, P1.16 | decreto-12955-2026, ec-132-2023, lc-214-2025, lc-227-2026, ato-conjunto-rfb-cgibs-1-2025, ato-conjunto-rfb-cgibs-5-2026, nt-2026-004-rtc-nfag, portaria-conjunta-mf-cgibs-7-2026, resolucao-cgibs-6-2026 |
| OBR-50 Conceder o cashback desconto de CBS e IBS na cobrança de água e esgoto a famílias do CadÚnico, com a lista da RFB | PARCIAL | P2.4, P2.5 | decreto-12955-2026, lc-214-2025 |
| OBR-51 Suportar o recolhimento por split payment na liquidação e conciliar o valor líquido recebido | PARCIAL | P2.4, P3.1 | lc-214-2025, lc-227-2026 |
| OBR-52 Emitir NFS-e padrão nacional para serviços avulsos com os grupos de IBS e CBS | COBERTA | P1.8, P3.6 | ato-conjunto-rfb-cgibs-4-2026, cgnfse-nfse-padrao-nacional, nfse-nt-007-2026, nt-se-cgnfse-008-009-rtc, resolucao-cgnfse-3-2023 |
| OBR-53 Aceitar CNPJ alfanumérico no cadastro, nos documentos fiscais, na cobrança e nas integrações | SEM_TAREFA | — | rfb-in-2119-2022, rfb-in-2229-2024 |
| OBR-54 Informar o valor aproximado dos tributos no documento entregue ao consumidor, se a conta de água estiver alcançada | SEM_TAREFA | — | decreto-8264-2014, lei-12741-2012 |
| OBR-55 Fornecer à contabilidade a base de receitas e retenções para PIS, Cofins e Pasep e sua escrituração enquanto vigorarem | PARCIAL | ED-32 | lei-10637-2002, lei-10833-2003, lei-9715-1998, lei-9718-1998, in-rfb-1252-2012, in-rfb-2121-2022, rfb-nt-011-2026 |
| OBR-56 Usar o CPF como identificador suficiente do cidadão no cadastro | PARCIAL | P1.3, P3.3 | lei-14534-2023 |

**G. Qualidade da água, esgoto e ambiente**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-57 Controlar a qualidade da água (plano de amostragem, padrões, conformidade), enviar ao SISAGUA e informar o consumidor na conta e no relatório anual | COBERTA | ED-50, P2.24, ED-79 | decreto-5440-2005, lei-6437-1977, portaria-consolidacao-gm-ms-5-2017, portaria-gm-ms-2472-2021, portaria-gm-ms-888-2021, aresc-res-049-2016, sp-ss-res-65-2016 |
| OBR-58 Monitorar o teor de flúor pelas faixas do Anexo XXI | PARCIAL | ED-50 | prc-5-2017-anexo-xxi |
| OBR-59 Monitorar efluentes contra padrões de lançamento com vigência, automonitoramento e declaração de carga poluidora | COBERTA | ED-80 | conama-357-2005, conama-430-2011, aresc-res-049-2016, rs-consema-res-355-2017 |
| OBR-60 Cadastrar e cobrar o esgoto não doméstico por carga poluidora | COBERTA | ED-83 | conama-430-2011 |
| OBR-61 Controlar lodo, biossólido e reúso pelas resoluções vigentes | COBERTA | ED-80 | cnrh-121-2010, cnrh-54-2005, conama-498-2020 |
| OBR-62 Registrar outorgas e licenças ambientais das unidades com validade e condicionantes | COBERTA | ED-80, ED-98 | lei-15190-2025, lei-9433-1997, ana-resolution-236-2024, conama-237-1997, conama-377-2006 |
| OBR-63 Emitir MTR, CDF e DMR dos resíduos gerados pelo prestador nos sistemas estadual e federal | SEM_TAREFA | — | decreto-10936-2022, lei-12305-2010, sc-ima-portaria-009-2026, mg-copam-dn-232-2019, rj-conema-res-79-2018, sp-cetesb-dd-024-2022, sp-cetesb-dd-078-2025, sp-cetesb-dd-130-2022, sp-sima-res-27-2021, rs-fepam-portaria-576-2026, ibama-in-1-2013, ibama-in-12-2021, ibama-in-13-2012, ibama-in-13-2021 |

**H. Medição e metrologia**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-64 Controlar o parque de hidrômetros pelo regulamento metrológico (modelo aprovado, classe, verificações, prazo de troca) | COBERTA | ED-14 | lei-9933-1999, inmetro-portaria-155-2022, conmetro-resolucao-8-2016 |
| OBR-65 Registrar reparo e ensaio de hidrômetro por oficina e laboratório autorizados pelo Inmetro | PARCIAL | ED-14 | inmetro-portaria-293-2021, inmetro-portaria-457-2021, inmetro-portaria-657-2025, inmetro-portaria-713-2025, inmetro-portaria-78-2022, aris-rn-40-2024 |

**I. Indicadores, perdas e informação regulatória**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-66 Preencher o SINISA com mapa de variáveis e obter o certificado de adimplência | COBERTA | P4.2, ED-48 | lei-11445-2007, lei-14026-2020, mcid-portaria-1069-2025 |
| OBR-67 Demonstrar as condicionantes do art. 50 da Lei 11.445 (perdas, regionalização, metas) | PARCIAL | ED-48, ED-78 | decreto-11599-2023, decreto-12805-2025, lei-11445-2007, mcid-portaria-788-2024, mcid-portaria-966-2025 |
| OBR-68 Apurar metas de universalização e indicadores de acesso por município, urbano e rural | COBERTA | ED-77, ED-48 | lei-14026-2020, agir-rn-023-2025, agr-tb-res-048-2026, aresc-res-430-2026, aris-rn-36-2022, aris-rn-43-2025, aris-rn-54-2026, cisam-meio-oeste-rn-2025-05-nr08, ares-pcj-resolucao-650-2025, arsae-resolucao-205-2025, agepar-res-56-2026 |
| OBR-69 Calcular os indicadores operacionais da NR 9 adotados pela agência, com fichas | PARCIAL | ED-77, ED-92, ED-48 | agir-rn-025-2026, aris-rn-36-2022, aris-rn-57-2026, ares-pcj-resolucao-723-2026, agepar-res-56-2026, adasa-res-59-2025 |
| OBR-70 Enviar dados e indicadores no sistema e no formato da agência (SISARIS e equivalentes) | PARCIAL | ED-97 | agir-rn-023-2025, agir-rn-025-2026, agr-tb-res-048-2026, aresc-res-430-2026, aris-in-dg-1-2025, aris-rn-11-2017, aris-rn-36-2022, aris-rn-43-2025, aris-rn-54-2026, aris-rn-57-2026, cisam-meio-oeste-rn-2025-05-nr08, arsae-resolucao-205-2025, arsesp-deliberacao-1143-2021, agergs-in-32-2026 |
| OBR-71 Apurar o balanço hídrico e manter plano de redução de perdas | COBERTA | ED-78, ED-85 | lei-14026-2020, lei-14546-2023, mcid-portaria-788-2024 |
| OBR-72 Manter agenda de obrigações regulatórias com responsável, lembrete e evidência | COBERTA | ED-98 | aris-in-dg-1-2025 |
| OBR-73 Publicar informação de interesse público (LAI, dados abertos, relatórios de reservatório e segurança hídrica) | PARCIAL | ED-99 | lei-12527-2011, lei-15012-2024 |

**J. Contabilidade regulatória, ativos e contratos**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-74 Escriturar a contabilidade regulatória no plano da agência e enviar balancetes e demonstrações | PARCIAL | ED-32, ED-56 | agir-rn-009-2019, agir-rn-011-2020, aris-rn-33-2021 |
| OBR-75 Manter base de ativos e bens reversíveis com dados para indenização de investimentos não amortizados | PARCIAL | ED-56 | agir-rn-019-2025, aresc-res-433-2026, aris-rn-33-2021, aris-rn-52-2025, cisam-meio-oeste-rn-2025-01-nr03, arsesp-deliberacao-1636-2025 |
| OBR-76 Registrar contratos de programa, concessão e PPP com aditivos e matriz de riscos | COBERTA | ED-96 | agir-rn-020-2025, aresc-res-432-2026, aris-rn-44-2025, cisam-meio-oeste-rn-2025-02-nr05 |
| OBR-77 Contabilizar prestador público no PCASP do exercício e gerar remessas ao tribunal de contas | PARCIAL | ED-32, ED-33 | stn-mf-portaria-2520-2026 |

**K. Dados pessoais, segurança e registros**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-78 Tratar dados pessoais com base legal, minimização, máscara, registro de acesso e atendimento dos direitos do titular | COBERTA | P0.11, P1.15, P3.20, ED-74 | lei-13709-2018, lei-13853-2019 |
| OBR-79 Comunicar incidente de segurança à ANPD e aos titulares no prazo e manter o registro | COBERTA | P3.10 | lei-13709-2018, anpd-res-15-2024 |
| OBR-80 Divulgar identidade e contato do encarregado do tenant no portal e nos documentos | SEM_TAREFA | — | lei-13853-2019, anpd-res-18-2024 |
| OBR-81 Formalizar transferência internacional de dados com cláusulas-padrão da ANPD | PARCIAL | ED-43, ED-42, P3.9 | lei-13709-2018, anpd-res-19-2024 |
| OBR-82 Guardar registros de acesso à aplicação pelo prazo mínimo, com segurança | COBERTA | P0.20, P0.8 | decreto-8771-2016, lei-12965-2014 |
| OBR-83 Reter documentos pelos prazos legais e eliminar documento de prestador público só por tabela de temporalidade | PARCIAL | P0.20, P3.19, P3.16 | lei-8159-1991 |
| OBR-84 Dispensar exigência documental redundante nos pedidos feitos a prestador público | PARCIAL | ED-38 | lei-13726-2018 |
| OBR-85 Enviar SMS e chamadas com remetente identificado e autenticado | PARCIAL | P3.5, ED-41 | mp-1396-2026 |

**L. Acessibilidade e atendimento prioritário**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-86 Dar atendimento prioritário (idoso, PcD, TEA, gestante, acompanhante) nos canais presenciais e na fila | COBERTA | ED-40 | decreto-5296-2004, lei-10048-2000, lei-10741-2003, lei-13146-2015, lei-14364-2022, lei-14626-2023, arsae-resolucao-94-2017 |
| OBR-87 Tornar portal e documentos acessíveis e entregar a conta em formato acessível a pedido | PARCIAL | P3.20, P2.8 | decreto-5296-2004, lei-13146-2015 |
| OBR-88 Limitar e medir o tempo de espera no atendimento presencial | PARCIAL | ED-40 | sc-lei-12465-2002 |

**M. Segurança do trabalho na OS**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-89 Exigir análise de risco, permissão de trabalho e habilitação do executor na OS de espaço confinado, altura e escavação | COBERTA | P1.31, ED-71 | mte-nr-1, mte-nr-18, mte-nr-33, mte-nr-35 |
| OBR-90 Exigir autorização e habilitação para serviço em eletricidade pela NR-10 revisada | PARCIAL | P1.31, ED-71 | mte-nr-10 |
| OBR-91 Registrar entrega de EPI e apoiar o PGR com riscos psicossociais, sem meta punitiva automática | COBERTA | ED-71, P3.31 | mte-nr-1, mte-nr-6, mte-portaria-mte-1419-2024 |

**N. Cadastro técnico (GIS)**

| Obrigação | Cobertura | Tarefas | Normas |
|---|---|---|---|
| OBR-92 Classificar a exatidão posicional do cadastro técnico pelo padrão cartográfico | PARCIAL | P1.26, P3.27 | decreto-89817-1984 |
