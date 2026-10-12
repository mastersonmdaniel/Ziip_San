# 08 — Regulação do saneamento básico, tarifas e atendimento ao usuário (2015–2026)

Data da pesquisa: 12/10/2026. Escopo: marco legal federal (Lei 11.445/2007 e alterações), regulamentos, Normas de Referência (NR) da ANA, SINISA/SNIS, estrutura tarifária, fatura, corte/religação, prazos, metas de perdas e de universalização, ouvidoria e agências reguladoras estaduais.

> **AVISO — não é parecer jurídico.** Este relatório é um levantamento técnico para orientar requisitos de software. **Exige revisão por advogado/regulatório antes de qualquer decisão** (defaults de multa, prazos de corte, tarifa social, SINISA). Onde não consegui confirmar, está escrito **NÃO CONFIRMADO**.

## Sumário executivo (oito achados)

1. **O SIGSAN hoje não atende nenhum requisito comercial regulatório**, porque `commercial/*` e `operations/*` não existem: não há tarifa, fatura, corte, religação, protocolo de atendimento nem SINISA. O que existe (balanço hídrico IWA, qualidade, LGPD, multi-tenant) cobre só uma fração, e o balanço roda com entradas fictícias.
2. **Tarifa Social (Lei 14.898/2024 + NR 13/2025 da ANA): prazo de implantação em 11/12/2026**, dois meses à frente. Exige classificação automática por CadÚnico/BPC, desconto de 50% até 15 m³, aviso na fatura por 3 meses antes de retirar o benefício e relatório anual. É o item P0 mais urgente (R-14).
3. **A ANA já publicou 16 Normas de Referência (NR 1 a 16, a última em 28/08/2026)**; a NR 2 foi revogada pela NR 8. As que mexem no produto: **NR 11** (condições gerais, verificação a partir de 20/05/2027), **NR 13** (estrutura tarifária e tarifa social, 20/05/2028), **NR 15** (perdas, balanço hídrico padronizado base SINISA, 20/05/2028), **NR 9** (indicadores) e **NR 8** (universalização).
4. **Corte por inadimplência tem regras federais duras**: aviso ≥30 dias (Lei 11.445, art. 40), comunicação prévia e **proibição de iniciar o corte de sexta a domingo, em feriado ou na véspera** (Lei 14.015/2020); sem notificação, a taxa de religação não é devida. O sistema precisa **impedir** a OS de corte, não só avisar (R-24).
5. **A agência define o detalhe e as agências divergem muito**: ADASA só corta com ≥60 dias de atraso e protege saúde/escolas/baixa renda por 12 meses; ARSESP veda cortar por débito de outro serviço; limite de ciclos por média vai de 3 a 6; correção do débito é Selic, INPC, IPCA ou índice do reajuste. **Perfil regulatório por tenant, com vigência no tempo, é requisito de arquitetura (R-39).**
6. **Metas de universalização (99% água / 90% esgoto até 31/12/2033) valem por município** e só contam quando atendimento e cobertura atingem o patamar ao mesmo tempo; a verificação é anual (3 de 5 anos). O sistema precisa de domicílios (IBGE) e ligações ativas (R-35).
7. **SINISA**: coleta 2026 (ano-base 2025) encerrou em 03/09/2026; restam **03/11 (versão preliminar), 17/11 (validação) e 31/12/2026 (Atestado de Adimplência)**, condição para recursos federais. O módulo de integração está vazio (R-36).
8. **Decretos**: 10.710/2021, 11.466/2023 e 11.467/2023 estão **revogados**; valem o 11.598 e o 11.599/2023 (alterado pelo 12.805/2025, que dispensa até 31/12/2027 os incisos VII–IX do art. 50 para recursos federais). Decreto 7.217/2010 segue em vigor, defasado. **Valores da Portaria MCID 788/2024 (metas de perdas) não foram confirmados em fonte oficial.**

## 0. Como ler e limites da pesquisa

- **Fontes consultadas diretamente** (texto lido): textos consolidados da Câmara dos Deputados (`www2.camara.leg.br/legin`) para leis e decretos; páginas e resoluções da ANA em `gov.br/ana`; páginas do Ministério das Cidades em `gov.br/cidades`.
- **O `planalto.gov.br` e o `in.gov.br` (DOU) não responderam neste ambiente.** Por isso o texto legal vem da Câmara (fonte oficial do Legislativo) e não do Planalto. Quando o ato é a fonte mais autoritativa (resoluções ANA), li o texto da própria ANA. **Recomendo conferir a data da última alteração no Planalto** antes de citar em documento formal.
- A busca web do ambiente é fraca; por isso a varredura de **normas de agências estaduais** foi feita por leitura direta dos sites das agências (seção 5) e está marcada com confiança.
- Estado do SIGSAN conferido em 12/10/2026 no código: `commercial/*` e `operations/*` são **diretórios vazios**; `compliance/sigsan-integration` está vazio; o tenant tem só `consumer_units`, `addresses`, `documents`, `service_orders`, `users`, `roles` (`platform/sigsan-platform/src/main/resources/db/migration/tenant/V1_1__create_tenant_schema.sql`); não existe nenhuma tabela ou classe de tarifa, fatura, leitura, protocolo de atendimento ou reclamação. O módulo de perdas existe (`intelligence/sigsan-losses`), mas com entradas fictícias (`LossesService.java`, linhas 130–134, marcadas `// Placeholder`).

---

## (a) Quadro de normas

Legenda de confiança: **alta** = li o texto oficial/consolidado; **média** = li fonte oficial parcial ou o ato só aparece citado em outro ato oficial; **baixa** = só fonte secundária.

### a.1 Leis

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| **Lei 11.445/2007** (diretrizes nacionais do saneamento) | Federal | 05/01/2007. Alterada por Leis 12.862/2013, 13.308/2016, 13.312/2016, 13.329/2016, **14.026/2020**, 14.546/2023, 14.600/2023, **15.012/2024**, **15.112/2025** (esta só acrescenta o art. 50 §13, drenagem). Regulamentação alterada pelo Decreto 12.805/2025. ADIs 4.058, 6.492, 6.536, 6.583 e 6.882 citadas pela Câmara (resultado NÃO CONFIRMADO) | Sim | https://www2.camara.leg.br/legin/fed/lei/2007/lei-11445-5-janeiro-2007-549031-normaatualizada-pl.html e https://www2.camara.leg.br/legin/fed/lei/2007/lei-11445-5-janeiro-2007-549031-norma-pl.html | alta (texto da Câmara; Planalto não acessado) |
| *Nota sobre 2026*: não encontrei lei federal de 2026 que altere a Lei 11.445. Há o PL 6365/2025 (Senado) em tramitação, que não é lei | Federal | situação em abril/2026 | n/a | busca web (fonte secundária) | baixa |
| **Lei 14.026/2020** (Novo Marco do Saneamento) | Federal | 15/07/2020. Altera Lei 11.445, **Lei 9.984/2000 (ANA)** e outras | Sim | https://www2.camara.leg.br/legin/fed/lei/2020/lei-14026-15-julho-2020-790419-publicacaooriginal-161096-pl.html | média (não abri o texto; efeitos confirmados via texto consolidado da Lei 11.445 e da Lei 9.984) |
| **Lei 9.984/2000** (ANA; arts. 4º-A e 4º-B = Normas de Referência e comprovação) | Federal | 17/07/2000; arts. 4º-A/4º-B pela Lei 14.026/2020; alterada pela Lei 14.600/2023 | Sim | https://www2.camara.leg.br/legin/fed/lei/2000/lei-9984-17-julho-2000-360468-normaatualizada-pl.html | alta |
| **Lei 14.898/2024** (Tarifa Social de Água e Esgoto) | Federal | 13/06/2024 (DOU 14/06/2024); **vigência 180 dias após a publicação** (art. 13). Dia exato de início: NÃO CONFIRMADO (fonte secundária fala em 10 ou 11/12/2024) | Sim | https://www2.camara.leg.br/legin/fed/lei/2024/lei-14898-13-junho-2024-795775-publicacaooriginal-172087-pl.html | alta (texto) / baixa (dia de início) |
| **Lei 14.015/2020** (interrupção e religação de serviços públicos) | Federal | 15/06/2020 (DOU 16/06/2020). Altera Lei 13.460 (arts. 5º, 6º) e Lei 8.987 (art. 6º §4º) | Sim | https://www2.camara.leg.br/legin/fed/lei/2020/lei-14015-15-junho-2020-790326-publicacaooriginal-160882-pl.html | alta |
| **Lei 13.460/2017** (direitos do usuário de serviço público; ouvidoria) | Federal | 26/06/2017; alterada por Leis 14.015/2020, 14.129/2021 e 14.534/2023 | Sim | https://www2.camara.leg.br/legin/fed/lei/2017/lei-13460-26-junho-2017-785098-normaatualizada-pl.html | alta |
| **Lei 8.078/1990 (CDC)** — arts. 22, 42, 42-A, 52 §1º | Federal | art. 42-A pela Lei 12.039/2009; art. 52 §1º pela Lei 9.298/1996 | Sim | https://www2.camara.leg.br/legin/fed/lei/1990/lei-8078-11-setembro-1990-365086-normaatualizada-pl.html | alta |
| **Lei 13.312/2016** (medição individualizada em condomínios) | Federal | 12/07/2016; acrescenta o §3º ao art. 29 da Lei 11.445 | Sim | citada no texto consolidado da Lei 11.445 | média |
| **Lei 13.709/2018 (LGPD)** | Federal | 14/08/2018 | Sim | citada pela NR 11 (art. 22 p.u.) e NR 13 (arts. 38–40) | média (coberta pelo relatório de LGPD) |

### a.2 Decretos e portarias federais

| Norma | Esfera | Data / situação | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| **Decreto 7.217/2010** (regulamenta a Lei 11.445) | Federal | 21/06/2010. Câmara: "não consta revogação expressa". O Ministério das Cidades o lista sem "REVOGADO". Defasado pelo marco de 2020. Redação do art. 26 §2º (plano de saneamento como condição para recursos após 31/12/2024) foi dada pelo Decreto 11.467/2023, **que foi revogado** — redação hoje aplicável: NÃO CONFIRMADO | Sim, **parcialmente defasado** | https://www2.camara.leg.br/legin/fed/decret/2010/decreto-7217-21-junho-2010-606813-norma-pe.html ; https://www.gov.br/cidades/pt-br/assuntos/saneamento/marco-legal-do-saneamento/normativos | alta (vigência) / baixa (conteúdo atual do art. 26) |
| **Decreto 10.710/2021** (capacidade econômico-financeira) | Federal | 31/05/2021. **Revogado** pelo Decreto 11.466/2023 | **Não** | https://www2.camara.leg.br/legin/fed/decret/2021/decreto-10710-31-maio-2021-791402-norma-pe.html | alta |
| **Decreto 11.466/2023** (idem, nova metodologia) | Federal | 05/04/2023. **Revogado** pelo art. 22 do Decreto 11.598/2023 | **Não** | https://www.gov.br/cidades/pt-br/assuntos/saneamento/marco-legal-do-saneamento/normativos | alta |
| **Decreto 11.467/2023** (regionalização e recursos federais) | Federal | 05/04/2023. **Revogado** pelo art. 17 do Decreto 11.599/2023 | **Não** | idem | alta |
| **Decreto 11.598/2023** (metodologia de comprovação da capacidade econômico-financeira; art. 10-B da Lei 11.445) | Federal | 12/07/2023 (DOU 13/07/2023). Sem revogação expressa (Câmara). Prestador sem requerimento tempestivo: "ausência de comprovação" (art. 18) | Sim | https://www2.camara.leg.br/legin/fed/decret/2023/decreto-11598-12-julho-2023-794434-publicacaooriginal-168441-pe.html | alta |
| **Decreto 11.599/2023** (prestação regionalizada; condições para recursos federais, art. 50 da Lei) | Federal | 12/07/2023. **Alterado pelo Decreto 12.805/2025** (art. 15) | Sim | https://www2.camara.leg.br/legin/fed/decret/2023/decreto-11599-12-julho-2023-794435-normaatualizada-pe.html | alta |
| **Decreto 12.805/2025** | Federal | 29/12/2025 (DOU 30/12/2025): incisos VII, VIII e IX do art. 50 da Lei 11.445 **não se aplicam a recursos federais alocados até 31/12/2027** | Sim | https://www2.camara.leg.br/legin/fed/decret/2025/decreto-12805-29-dezembro-2025-798613-publicacaooriginal-177642-pe.html | alta |
| **Decreto 10.588/2020** (apoio técnico/financeiro e recursos federais) | Federal | 24/12/2020. Revogado pelo Decreto 11.467/2023 | **Não** | MCID (link acima) | alta |
| **Portaria MCID 1.069/2025** (ciclo anual do SINISA) | Federal/MCID | 16/09/2025. Define o ciclo 2026 (ano-base 2025). Texto da portaria não lido | Sim | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/area-do-prestador/area-do-prestador | média (citada pela página oficial do SINISA) |
| **Portaria MDR 719/2018** (auditoria e certificação do SNIS/SINISA; Projeto ACERTAR) | Federal | 12/12/2018; citada pela NR 9 (art. 18) "ou instrumento que a substitua" | Sim (substituição NÃO CONFIRMADA) | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/projeto-acertar/projeto-acertar-1 | média |
| **Portaria MCID 788/2024** (procedimentos do art. 50, IV, da Lei 11.445: índice de perdas para acesso a recursos federais) | Federal/MCID | 01/08/2024, citada pela NR 9 (art. 13). **Metas (≤35% e 303 l/lig/dia até 2025; ≤30% e 263 de 2026 a 2032; 25% e 216 em 2033) vêm de fonte secundária** | Sim | texto da portaria NÃO obtido; citada em https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/211 | alta (existência) / **baixa (valores)** |
| Portaria MDR 490/2021 (perdas, versão anterior) | Federal | citada só em fonte secundária; possivelmente substituída pela 788/2024 | NÃO CONFIRMADO | — | baixa |

### a.3 Resoluções e Normas de Referência da ANA (Lei 9.984, art. 4º-A)

Lista oficial lida em https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA. Cada resolução está em `https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/{ano}/{número}`. **Todas as NR são dirigidas às entidades reguladoras infranacionais (ERIs) e aos titulares**; o software do prestador as sofre indiretamente, pelo que a ERI do cliente regulamentar. **Não se aplicam a contratos de concessão licitados antes de cada NR** (salvo exceções, p. ex. tarifa social).

| NR | Resolução ANA | Tema | Situação | Início da verificação pela ANA (ERIs) | Confiança |
|---|---|---|---|---|---|
| NR 1/2021 | 79, 14/06/2021 (alt. pela 114, 30/12/2021) | Cobrança do manejo de resíduos sólidos urbanos | Vigente | adoção por prazos da IN 1/2023, alt. IN 2/2023 e IN 2/2025 | alta |
| NR 2/2021 | 106, 04/11/2021 | Aditivos de contratos (metas do art. 11-B) | **Revogada** pela Res. 192/2024 (NR 8) | — | alta |
| NR 3/2023 | 161, 03/08/2023 (DOU 04/08/2023) + IN 1/2024 | Indenização de investimentos não amortizados | Vigente | — | alta |
| NR 4/2024 | 177, 12/01/2024; **alterada pela 305, 02/10/2026** (DOU 05/10/2026) | Governança das ERIs | Vigente | requisito do art. 41, II (mandato fixo) só exigido a partir do procedimento anual de **2028**; p.u. do art. 41 agora: 2 anos, exceto incisos II e III (4 anos) | alta |
| NR 5/2024 | 178, 15/01/2024 | Matriz de riscos de contratos | Vigente | 18 meses (inciso I) e 24 meses (inciso II) da publicação | alta |
| NR 6/2024 | 183, 05/02/2024 (vigor 01/03/2024); alterada pela 238, 13/01/2025 | Modelos de regulação tarifária (contratual × discricionária) | Vigente | 12 meses da publicação | alta |
| NR 7/2024 | 187, 19/03/2024 | Condições gerais de limpeza urbana e manejo de RSU | Vigente | ERIs e capitais: 01/04/2025; municípios >100 mil hab.: 31/12/2025; 50–100 mil: **31/12/2026**; <50 mil: **31/12/2027** (art. 110) | alta |
| NR 8/2024 | 192, 08/05/2024 (vigor 20/05/2024); **art. 31, IV suspenso só em 2026 pela Res. 301, 14/08/2026** | Metas progressivas de universalização, indicadores de acesso (IAA, ICA, IAE, ICE) e sistema de avaliação | Vigente | 12 meses da publicação | alta |
| NR 9/2024 | 211, 19/09/2024 (vigor 01/10/2024) | Indicadores operacionais de água e esgoto (Nível I e II) | Vigente | 18 meses da publicação | alta |
| NR 10/2024 | 228, 12/12/2024 | Reajuste tarifário (IPCA; fórmula; fator X; homologação tácita) | Vigente | ERIs publicam regulamento até **20/05/2027** | alta |
| NR 11/2024 | 230, 18/12/2024 (DOU 19/12/2024) | **Condições gerais de prestação** (cadastro, fatura, corte, religação, atendimento, prazos) | Vigente | **20/05/2027** | alta |
| NR 12/2025 | 245, 17/03/2025 | Estruturação da drenagem e manejo de águas pluviais | Vigente | — | média (título lido; conteúdo não) |
| NR 13/2025 | 271, 21/11/2025 (DOU 24/11/2025) | **Estrutura tarifária e Tarifa Social** | Vigente | **20/05/2028**; prazo de implantação da tarifa social **11/12/2026** | alta |
| NR 14/2025 | 276, 18/12/2025 | Indicadores de SLU e RSU | Vigente | **20/05/2029** | alta |
| NR 15/2025 | 275, 18/12/2025 (DOU 19/12/2025) | **Redução e controle de perdas** (balanço hídrico padronizado SINISA; plano de gestão) | Vigente | **20/05/2028** | alta |
| NR 16/2026 | 302, 28/08/2026 (DOU 31/08/2026) | Contabilidade regulatória (plano de contas MCRSB; controle patrimonial MCPSB) | Vigente | ERIs: 3 anos para adotar; 5 anos para ajustar sistemas; prestadores até o exercício social de 2032 | alta |
| Res. 134/2022 | 134, 18/11/2022 | Procedimento anual de comprovação de adoção das NR pelas ERIs (ANA publica instruções até 20/05; ERI envia até 20/08; ANA comunica não observância até 20/10; resultado até 20/12) | Vigente | — | alta |
| Res. 209/2024 e 258/2025 | 209, 09/09/2024 (mediação); 258, 13/08/2025 (arbitramento) | Solução de conflitos sobre NR | Vigente | — | alta |

Observação sobre a Res. 301/2026: o texto consolidado da NR 8 na ANA diz "Resolução ANA nº 301, de 14 de **outubro** de 2026", mas a própria resolução está datada de **14 de agosto de 2026** (DOU 17/08/2026). Considerei a data da resolução.

Temas do art. 4º-A §1º da Lei 9.984 ainda sem NR publicada na lista da ANA: reúso de efluentes (IX), parâmetros de caducidade (X), metas de substituição do sistema unitário pelo separador absoluto (XI) e a NR de avaliação de desempenho prevista na NR 9 (art. 14). Há, em fonte secundária, indicação de que a ANA trabalha em reúso e em nova NR de instrumentos negociais na Agenda Regulatória 2025–2026 (Res. ANA 227/2024): **NÃO CONFIRMADO**.

### a.4 Agências reguladoras estaduais e consorciadas (normas de condições gerais)

Levantadas por leitura direta dos PDFs das agências em 12/10/2026 (parte por subagentes de pesquisa, com amostra conferida por mim: ARSAE-MG arts. 112 e 115; ADASA arts. 111 e 121; ARSESP art. 90). "Última alteração" = a mais recente que localizei; **pode haver alteração posterior não localizada**. Detalhe por artigo na seção 5.

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| **Res. ARSAE-MG 131/2019** (MG: Copasa, SAAEs) | Agência estadual | 13/11/2019 (a agência também cita 11/11/2019); alterada pelas Res. 140/2020, 151/2021, 156/2021, 165/2022, 179/2023, 189/2024, 212/2025 e **216 (16/12/2025)** | Sim | https://www.arsae.mg.gov.br/2019/11/11/resolucao-131-2019/ ; consolidado dez/2025: https://www.arsae.mg.gov.br/wp-content/uploads/2025/08/131_2019_Texto-integral-com-alteracoes-dez.2025.pdf | alta |
| **Res. ARSAE-MG 227/2026** (tarifa social) | Agência estadual | 10/06/2026, vigência 01/07/2026 | Sim | https://www.arsae.mg.gov.br/wp-content/uploads/2026/06/227_2026_estabelece_criterios_aplicacao_tarifa_social_prestadores.pdf | alta (leitura pelo subagente) |
| **Res. ARSAE-MG 205/2025** (envio de informações) | Agência estadual | 2025 | Sim | https://www.arsae.mg.gov.br/wp-content/uploads/2024/11/205_2025_gerenciamento_de_informacoes_pos_errata.pdf | média |
| **Res. ADASA 14/2011** (DF: Caesb) | Agência distrital | 27/10/2011; texto consolidado até a Res. 42/2024; **Res. 65/2025** alterou depois o Anexo V (infrações) | Sim | https://www.adasa.df.gov.br/images/storage/legislacao/resolucoes_adasa/2011/ulitma_versao_n_14_2011/Resolução_nº_14_Consolidada.pdf | alta |
| **Deliberação ARSESP 106/2009** (SP: Sabesp, municípios) | Agência estadual | 13/11/2009; alterações 221/2011, 804/2018, 809/2018, 1.029/2020, 1.117/2021, 1.206/2021 e **1.751/2025 (04/12/2025)**; o texto integral do site está consolidado só até 12/2018 | Sim | https://www.arsesp.sp.gov.br/LegislacaoArquivos/ldl1062009-integral.pdf | média |
| **RN AGERGS 66/2022** (RSAE unificado Corsan/BRK; data exata NÃO CONFIRMADA) | Agência estadual | listada "em vigor" na tabela da agência atualizada em 27/07/2026; REN 37/2017 (compensação), REN 74/2026 (06/01/2026, disponibilidade de rede), IN 32/2026 (dados) | Sim | https://www.agergs.rs.gov.br/upload/arquivos/202208/11152604-resolucao-normativa-66-2022-anexo-rsae-unificado.pdf | média |
| **Res. AGEPAR 3/2020** (PR: Sanepar) — Regulamento de Serviços Básicos | Agência estadual | 14/02/2020; alterada pela Res. 38/2025 (27/08/2025); Res. 56/2026 (12/08/2026: NR 8 e 9). Vigência condicionada à revogação do Decreto Estadual 3.926/1988 (NÃO CONFIRMADA). O PDF oficial tem trechos ausentes (art. 122 da fatura) | Sim | https://www.legislacao.pr.gov.br/legislacao/listarAtosAno.do?action=exibir&codAto=251615 | média |
| **Res. ARES-PCJ 50/2014** (consórcio PCJ, SP/MG) + regulamento de cada município | Consórcio/agência | alterada pelas Res. 198/2017, 460/2022, 649/2025 e 650/2025; tarifa social Res. 592/2024; indicadores Res. 723/2026 | Sim | https://www.arespcj.com.br/conteudo/resolucoes-ares-pcj | média |
| **Decreto RJ 48.225/2022** + IN AGENERSA 103/2023 (alt. IN 122/2024) | Estadual/agência | 13/10/2022; IN 148/2026 (11/06/2026) | Sim | https://www.rj.gov.br/agenersa/regulamento-servicos | média |
| **RN AGR-GO 09/2014** (GO: Saneago) + RN 207/2023 (prazos) | Agência estadual | 13/02/2014; alterações RN 80/2016, 106/2017, 188/2022, 196/2022; tarifa social RN 186/2021 | Sim | https://www.goias.gov.br/agr/resolucoes-normativas-do-conselho-regulador-2013-2025/ | média |
| **RN ARIS 19/2019** (SC) | Agência estadual/consorciada | 27/03/2019; compilada em 15/04/2026 (alt. RN 20/2019, 38/2023, 40/2024, 55/2026); tarifa social RN 56/2026 | Sim | https://www.aris.sc.gov.br/legislacao?cat=8 | média |
| **Res. ARCE 130/2010** (CE: Cagece) | Agência estadual | 25/03/2010; alterações até Res. 04/2022 | Sim | https://www.arce.ce.gov.br/coordenadorias/saneamento/abastecimento-de-agua-e-esgotamento-sanitario/resolucoes/ | média |
| ARPE (PE), AGEMS (MS), AGER-MT, ARSAL (AL), ARSEP | Agências estaduais | **NÃO PESQUISADAS** (site da ARPE não respondeu) | — | — | — |

---

## (b) Requisitos acionáveis para um software

Convenções: **ATENDE / PARCIAL / NÃO ATENDE / N/A**. Prioridade: **P0** = obrigatório e sem o qual o prestador não opera ou não fatura dentro da lei; **P1** = obrigação com prazo ou sanção, mas contornável por processo manual; **P2** = recomendável/futuro. Módulos usam os nomes do SIGSAN (`commercial/*`, `operations/*`, `intelligence/*`, `compliance/*`).

### b.1 Cadastro, medição e fatura

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio |
|---|---|---|---|---|---|---|---|
| R-01 | Cada unidade usuária com **um único código/inscrição**; cadastro com nome, CPF/CNPJ, telefone e e-mail, endereço, atividade econômica, nº de economias por categoria (mín. residencial, comercial, industrial, pública), data de início, **histórico de leituras e faturamento de 120 meses**, código de cobrança/categoria, nº do medidor com datas de instalação e **última aferição** | NR 11 arts. 21–22 | `commercial` (registry) | **PARCIAL**. `consumer_units` tem `matricula`, `category_id`, `water_economies`, `sewage_economies`, datas de ligação, `meter_id`, `address` (V1_1). Mas `owner_id`/`responsible_id` apontam para `users` (conceito errado, já registrado em 00-CONSOLIDADO §4.1); `category_id`/`tariff_type_id`/`meter_id` sem tabela de destino; sem telefone/e-mail do usuário, atividade econômica, histórico 120 meses nem aferição | Criar `customer` separado de `users`, vínculo cliente–imóvel com vigência, tabela de hidrômetro com aferição, economias por categoria; política de retenção (120 meses) compatível com LGPD | P0 |
| R-02 | Dados pessoais do cadastro sob a **LGPD** | NR 11 art. 22 p.u.; Lei 13.709/2018 | `compliance/sigsan-lgpd` | **PARCIAL**: módulo LGPD e RLS por tenant existem; nada ligado ao cadastro comercial (não existe) | Ligar consentimento/base legal, índice cego do documento e minimização ao cadastro | Incluir na Fase 1 do cadastro (já prevista em 00-CONSOLIDADO §3) | P0 |
| R-03 | **Medição**: hidrômetro em cada unidade; medidores certificados INMETRO; histórico | NR 11 art. 31; Lei 11.445 art. 29 §3º (individualizada em novos condomínios); Lei 13.312/2016 | `operations/sigsan-metering`, `sigsan-reading` | **NÃO ATENDE** (diretórios vazios) | Cadastro de medidores com certificação/classe, instalação e aferição | Planejado na Fase 2; incluir campo de certificado INMETRO e data de aferição | P1 |
| R-04 | **Faturar por média** de consumo dos últimos N meses quando não há leitura (N definido pela ERI); formas alternativas sem hidrometração/histórico | NR 11 art. 35 | `commercial/sigsan-billing` | **NÃO ATENDE** | N de meses, teto de ciclos consecutivos e alternativa sem medição devem ser parâmetros do tenant. Variação observada: média de 5 a 12 meses e **teto de 3 ciclos (ARSESP, AGERGS, ARIS, ARCE) ou 6 (ARSAE-MG, AGR-GO)**; depois, regra própria (consumo mínimo; 50%/100%/150% do presumido; só parte fixa). Ver seção 5 | `ConsumptionCalculator` já previsto (02-gsan-leitura...); parametrizar N | P0 |
| R-05 | **Estrutura tarifária por categorias**: no mínimo residencial, **residencial social**, comercial, industrial e pública; opcional categoria vulnerável e tratamento a micro/pequenas empresas; categoria coletiva; vigência no tempo | NR 13 art. 6º; Lei 11.445 art. 30, I | `commercial/sigsan-billing` | **NÃO ATENDE** | Sem tabela de categoria/tarifa/vigência | Modelar tarifa com vigência, categoria, faixa e tipo de parcela fixa | P0 |
| R-06 | **Parcela fixa + parcela variável**: parcela fixa = tarifa básica (sem franquia) **ou** tarifa por consumo mínimo (com franquia); parcela variável em **faixas crescentes não cumulativas** para residencial. **Contratos licitados após a NR 13: só tarifa básica** (transição gradual permitida) | NR 13 arts. 8º–9º; Lei 11.445 art. 30, II–IV | `commercial/sigsan-billing` | **NÃO ATENDE**. O plano herdado do GSAN (02-gsan-...) usa "consumo mínimo/tarifa mínima por economia" | Suportar as **duas** modalidades por contrato/tenant (consumo mínimo ainda vale nos contratos existentes) e faixas não cumulativas | P0 |
| R-07 | **Hidrômetro único com várias economias**: parcela fixa por economia + variável sobre consumo total, média por economia para as faixas; economias de categorias distintas: regra da ERI; **fatura discrimina economias por categoria** | NR 13 arts. 12–13 | billing | **NÃO ATENDE** | `water_economies` é um inteiro; falta economia por categoria | Entidade `economia` com categoria | P1 |
| R-08 | **Esgoto**: proporção sobre o volume de água (parametrizável), cobrança integral com coleta+transporte (tratamento pode ser exigido em contratos novos), flexibilização para não residenciais só se a ERI previr; cobrança só onde há rede | NR 13 arts. 14–16; NR 11 art. 33 §3º; Lei 11.445 art. 45 §4º | billing | **NÃO ATENDE** | Percentual de esgoto, fator, tratamento exigido ou não, tudo por tenant | `sewage_ratio`, `requires_treatment` por contrato | P0 |
| R-09 | **Cobrança por disponibilidade** (edificação sem conexão com rede disponível e conexão factível): valor ≥ 2× tarifa básica (ou, de preferência, 2× tarifa por consumo mínimo); aviso **90 dias antes, na fatura anterior**; dispensa mediante inviabilidade comprovada; prazo ≤ 1 ano para conectar; até 50% da receita para ligação de baixa renda | Lei 11.445 art. 45 §§4º–8º; NR 13 arts. 17–20; NR 8 art. 12 | billing + registry | **NÃO ATENDE** | Falta cadastro de edificações **sem** ligação mas com rede factível; falta controle do aviso de 90 dias | Estado "factível/não conectada" na unidade; evento "aviso na fatura" auditável | P1 |
| R-10 | **Economias inativas** (suspensas por pedido/inadimplência) continuam sujeitas à tarifa básica quando a ERI previr | NR 13 art. 10 e def. VIII | billing | **NÃO ATENDE** | — | Situação "inativa" com regra por tenant | P1 |
| R-11 | **Fatura**: modelo e itens **definidos pela ERI** (obrigatório explicitar itens e custos); em todo documento de cobrança: **nome, endereço e CPF/CNPJ do fornecedor**; opções de data de vencimento; intervalo de leitura padronizado e divulgado; fatura em nome do usuário (proprietário responde por cadastro desatualizado); canais de atendimento e de contestação | Lei 11.445 art. 39 p.u.; CDC art. 42-A; NR 11 art. 33; NR 13 arts. 13, 49 VIII, 61 VII | billing | **NÃO ATENDE** | Sem gerador de fatura; template deve ser **por tenant/ERI** (conteúdo mínimo varia, ver seção 5) | Templates versionados por agência; campos obrigatórios validados antes de emitir | P0 |
| R-12 | **Mensagens obrigatórias na fatura**: aviso de perda iminente da tarifa social (≥3 meses); notificação de irregularidade que levará à perda do benefício (≥3 meses); aviso prévio da cobrança por disponibilidade (90 dias); aviso do início do cofaturamento (60 dias); aviso de corte | Lei 14.898 arts. 2º §2º e 3º p.u.; NR 13 arts. 18 §2º, 58; Lei 11.445 art. 40 §2º | billing | **NÃO ATENDE** | Mecanismo de "mensagem condicionada" por regra/período e prova de entrega | Motor de mensagens na fatura com registro (quem recebeu, em qual ciclo) | P0 |
| R-13 | **Reajuste e revisão tarifária**: reajuste a cada ≥12 meses a partir da data-base; **IPCA** para contratos futuros de regulação contratual; fórmula paramétrica + fator X na regulação discricionária; atraso estende o período de referência; **homologação tácita**; divulgação com **≥30 dias** de antecedência; revisão periódica e extraordinária | Lei 11.445 arts. 37–39; NR 10 arts. 4º–15; NR 13 art. 60; NR 6 | billing (tarifas) | **NÃO ATENDE** | Data-base, índice, fórmula, fator X, data de publicação, vigência | Tabela `tariff_revision` com tipo (reajuste/revisão), data-base, índice, publicada em, vigente desde; bloquear vigência antes de publicação + 30 dias | P1 |
| R-14 | **Tarifa Social** (Lei 14.898 + NR 13 cap. III): família com renda per capita ≤ ½ salário-mínimo **e** inscrita no CadÚnico (atualização ≤2 anos) **ou** com BPC; **classificação automática** pelo prestador a partir dos dados repassados pela ERI; uma única economia por família (desempate: responsável no CadÚnico, endereço, titular da família, ligação mais recente); **desconto de 50% sobre a tarifa até 15 m³** (resto conforme ERI); concessão **não pode ser condicionada à adimplência**; perda por irregularidade só após aviso na fatura por ≥3 meses; permanece ≥3 meses se deixar de ser elegível; inadimplência não retira a condição (corte permitido se a ERI previr); reporte anual (nº de economias e valor do subsídio); sigilo e finalidade do dado | Lei 14.898 arts. 2º–8º, 12; NR 13 arts. 25–45, 64–66 | billing + registry + `sigsan-lgpd` | **NÃO ATENDE** | Nada existe; **dado socioeconômico sensível**; o repasse do CadÚnico é feito pela ERI, não pelo prestador (art. 30 §1º; exceção: prestação direta) | Importador de base CadÚnico/BPC (formato definido pela ERI), regra de classificação automática, fila de inclusões manuais (documentos do art. 5º da lei), histórico de enquadramento por unidade, relatório anual; trilha de auditoria e RLS | **P0** (prazo **11/12/2026**) |
| R-15 | **Índice de Tarifa Social** (razão entre economias residenciais beneficiadas e ativas) para ajuste tarifário em editais | NR 13 art. 45 | billing | **NÃO ATENDE** | — | Indicador calculado mensalmente | P2 |
| R-16 | **Cofaturamento** de outros serviços (RSU, drenagem): exige contrato; **cobrança por documento com código identificador único** quando é atividade de gestão comercial; usuário pode pedir **retirada**; segregação bancária/contábil e repasse; comunicação **≥60 dias** antes do início; discriminação de valores e serviços; **inadimplência de serviço cofaturado**: precedente local que veda cortar água por débito de outro serviço (ARSESP, ver seção 5) | Lei 11.445 art. 35 §1º; NR 13 arts. 46–58; NR 1/2021 itens 5–6 | billing + `commercial/sigsan-accounting` | **NÃO ATENDE** | — | Itens de fatura de terceiros com conta-corrente própria; flag de "retirada"; relatório de repasse | P1 |
| R-17 | **Duplicidade de pagamento**: detectar e informar o usuário; **revisão de fatura** a pedido conforme diretrizes da ERI; **repetição em dobro** de cobrança indevida (salvo engano justificável) | NR 11 arts. 37–38; CDC art. 42 p.u. | `sigsan-collection` | **NÃO ATENDE** | — | Regra na baixa de pagamentos; processo de revisão com prazo | P1 |
| R-18 | **Multa e juros de mora** parametrizáveis; **multa de mora ≤ 2%** (CDC art. 52 §1º); condições de juros/multa constam do contrato de adesão. O GSAN calcula juros por mês de calendário (erro já apontado em 00-CONSOLIDADO §5) | CDC art. 52 §1º; NR 11 art. 18, X | billing/collection | **NÃO ATENDE** | Aplicação do CDC a serviço público e juros-padrão: **ver pergunta E-02**. As 10 agências lidas convergem em **multa de 2%** e juros de **0,033%/dia ou 1% a.m.**, mas a **correção varia** (Selic: ARSAE-MG; INPC: ADASA e AGR-GO; IPCA: AGEPAR, ARIS, ARCE; índice do reajuste: AGERGS) e a ARSAE-MG proíbe acumular encargos de períodos anteriores | Parâmetros por tenant (`late_fee_pct`, `interest_pct_month`, `pro rata die`) com teto configurável | P0 |
| R-19 | **Contrato de adesão** (modelo homologado pela ERI, cláusulas mínimas, prazo preferencialmente indeterminado) disponível física ou digitalmente; contratos específicos para grandes usuários | NR 11 arts. 17–20; Lei 11.445 art. 41 | `sigsan-portal`, `documents` | **NÃO ATENDE** (existe só tabela genérica `documents`) | — | Tipo de documento "contrato de adesão" versionado por ERI; vínculo ao cliente | P1 |
| R-20 | **Transparência tarifária**: tabela de tarifas em vigor com evolução de 5 anos, método de cálculo, faixas, categorias, subsídios e canais de contestação; **simulador online** da fatura (quando a ERI exigir); app/web com histórico de **≥12 meses** de cobrança e medição | NR 13 arts. 61–62; NR 11 art. 55 | `sigsan-portal` | **NÃO ATENDE** | — | Simulador reutiliza o motor de tarifa | P1 |
| R-21 | **Informações à ERI** (periodicidade da ERI): consumo médio por categoria/faixa, receita e usuários subsidiados, contratos de grandes usuários, taxa de inadimplência por categoria; contabilidade regulatória (NR 16) | NR 13 art. 63; Lei 11.445 art. 25; NR 16 | `intelligence/*`, `commercial/sigsan-accounting` | **NÃO ATENDE** | — | Pacote de relatórios regulatórios por tenant (formato definido pela ERI) | P1 |
| R-22 | **Medição individualizada** em condomínios e contratos especiais com rateio para prédios sem individualização | Lei 11.445 art. 29 §§3º e 5º; NR 11 arts. 6º, 28–30 | billing | **NÃO ATENDE** | — | Regra de rateio de área comum | P2 |
| R-23 | **Mecanismos de contingência** (bandeiras tarifárias em racionamento declarado; tarifas sazonais; descontos por força maior) | Lei 11.445 art. 46; NR 13 arts. 22–24 | billing | **NÃO ATENDE** | — | Parâmetro opcional "bandeira" por período | P2 |

### b.2 Corte, religação, atendimento e prazos

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio |
|---|---|---|---|---|---|---|---|
| R-24 | **Corte por inadimplência**: o usuário deve ser **formalmente notificado** e a suspensão exige **aviso prévio não inferior a 30 dias** da data prevista; comunicação prévia deve informar o **dia a partir do qual o desligamento será feito**, "necessariamente durante horário comercial" (a redação da Lei 13.460, art. 5º XVI, é ambígua: pode se referir ao desligamento ou à comunicação; tratar ambos como restritos a horário comercial até haver orientação jurídica); **vedado iniciar** o corte em **sexta, sábado, domingo, feriado ou véspera de feriado**; sem a notificação a **taxa de religação não é devida** e cabe multa à concessionária; no esgoto, preservar condições mínimas de saúde | Lei 11.445 art. 40, V e §2º; Lei 13.460 arts. 5º XVI e 6º VII + p.u. (Lei 14.015/2020); Lei 8.987 art. 6º §4º | `sigsan-dunning` + `operations/*` | **NÃO ATENDE** (cobrança e OS de corte inexistentes; `service_orders` genérica) | **Varia por agência**: atraso mínimo (ADASA ≥60 dias), limite máximo de atraso para cortar (ADASA 120; ARIS 120; AGERGS e AGEPAR 90; ARES-PCJ 3 meses), validade do aviso (ARSAE-MG: novo aviso se não executado em 90 dias), horário (AGR-GO só em dias úteis; ARCE/ARIS/AGR-GO vedam após 12 h de sexta), classes protegidas (ADASA 12 meses). Calendário de feriados (nacional/estadual/municipal) como parâmetro; geração de OS de corte só após aviso comprovado; bloqueio de execução fora do horário/dia permitido; flag "taxa de religação não devida" | Máquina de estados: débito vencido → aviso → carência (≥30 d, ou prazo da ERI se maior) → OS de corte; **validações impeditivas**, não só alertas | **P0** |
| R-25 | **Restrições especiais de corte**: estabelecimentos de saúde, instituições educacionais e de internação coletiva e usuário residencial de baixa renda com tarifa social — prazos e critérios próprios | Lei 11.445 art. 40 §3º; NR 13 art. 36 | dunning | **NÃO ATENDE** | Classificação "serviço essencial/saúde" da unidade | Atributos na unidade + política de corte por classe e por ERI | P0 |
| R-26 | **Religação/restabelecimento** nos prazos da ERI; **religação de urgência**: informar valor e prazos e atender qualquer usuário onde adotada; exigência de quitação varia por agência | NR 11 arts. 44–47 | operations/field | **NÃO ATENDE** | SLA por tipo de serviço (hoje há só `scheduled_at/started_at/completed_at`) | Tabela de serviços e prazos por tenant; relógio de SLA na OS; religação de urgência com preço | P0 |
| R-27 | **Interrupções programadas e de emergência**: comunicar ERI e usuários (abrangência, duração, motivo); emergência: divulgar imediatamente; interrupção >24 h: **fornecimento de emergência a unidades essenciais**, medido; racionamento conforme norma da ERI | Lei 11.445 art. 40 §1º; NR 11 arts. 39–41 | `operations/*`, `sigsan-portal` | **NÃO ATENDE** | Registro de eventos de interrupção com área afetada (também alimenta o indicador de intermitência da NR 9) | Entidade `interruption_event` com geometria/DMA e duração | P1 |
| R-28 | **Atendimento**: canais telefônico, eletrônico e presencial; **24 h por dia, 7 dias por semana**; **protocolo numerado** para cada solicitação/reclamação; relatório à ERI de **reclamações por mês, por motivo, por sistema** e **percentual não atendido**; Carta de Serviços; resposta da ouvidoria em **30 dias** (prorrogável uma vez); prestadores sem ouvidoria: manifestações diretas ao titular/ERI | NR 11 arts. 51–55, 52 §§1º–3º; Lei 13.460 arts. 7º, 13–16; Lei 11.445 art. 23 §§2º–3º | `operations/sigsan-field`, `sigsan-portal` | **NÃO ATENDE** (não há entidade de protocolo/solicitação/reclamação; o único "protocolo" no código é do MTR) | `service_request` (RA) com protocolo, motivo, sistema, SLA, canal e resposta final; painel de reclamações; relatório mensal e anual | P0 |
| R-29 | **Prazos de execução de serviços** (ligação nova, reparo de vazamento, extravasamento de esgoto, atendimento de reclamação) conforme lista aprovada pela ERI; análise de exceções fora do controle do prestador antes de penalizar | NR 11 arts. 58 VIII, 72–73; NR 9 Nível II-03 | operations | **PARCIAL**: `service_orders` tem timestamps, mas sem SLA, sem tipo de serviço configurável, sem justificativa de exceção | Catálogo `service_type` com prazo por tenant; motivo de exceção | Incluir já no desenho da Fase 7 | P0 |
| R-30 | **Ligações**: dever de ligar quando o usuário cumpre as exigências; levantar e **repassar à ERI/titular a lista de edificações não conectadas** e as que descumpriram o prazo (≤1 ano); informações ao interessado (rede próxima, pressões, diâmetro) | NR 11 arts. 8º–10; NR 8 arts. 12 e 17; Lei 11.445 art. 45 §§6º–7º | registry + operations | **NÃO ATENDE** | Estado "edificação sem ligação com rede disponível" | Relatório periódico; cadastro georreferenciado | P1 |

### b.3 Perdas, metas e indicadores (SINISA)

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio |
|---|---|---|---|---|---|---|---|
| R-31 | **Balanço hídrico padronizado, anual, base SINISA**, em 1.000 m³/ano, 12 meses (jan–dez), por município; **declaração anual à ERI**; mais indicadores: perdas por ligação (**l/lig/dia, separadas em reais e aparentes**), intermitência, macromedição e **hidrometração (IAG1003)** | NR 15 arts. 4º–5º e Anexo I (variáveis GTA 1001/1009/1014/1203/1204–1207/1209–1211/1215–1218) | `intelligence/sigsan-losses` | **PARCIAL**: `WaterBalanceCalculator` calcula balanço IWA (perda, NRW, ILI), `water_balance` por DMA/período (V2_2). **Mas**: não há mapeamento para as variáveis SINISA; saída não está em l/lig/dia; não agrega por município/ano; entradas de `LossesService` são **placeholders** (linhas 130–134); aprovação bloqueada com entrada fictícia (correto) | Camada de saída "SINISA/NR 15" que agregue DMAs em município/ano; calcular IN049/IN051 equivalentes; ligar às fontes reais (SCADA, faturamento) | **P0** |
| R-32 | **Plano de Gestão de Redução e Controle de Perdas por município**: diagnóstico, linha de base, cronograma físico-financeiro, justificativa técnico-econômica, indicadores complementares, estrutura e custos; acompanhamento anual; publicação de resultados | NR 15 arts. 6º–12 | losses | **NÃO ATENDE** | — | Entidade `loss_reduction_plan` + ações com custo e prazo, vinculadas a DMA | P1 |
| R-33 | **Meta de perdas versus resultado** por município e ano (referência federal: Portaria MCID 788/2024, valores a confirmar) | Lei 11.445 art. 50, IV; NR 9 art. 13 | losses | **NÃO ATENDE** | Metas por tenant/contrato | Tabela `loss_target` (ano, indicador, valor, origem); alerta de desvio | P1 |
| R-34 | **Indicadores NR 9 Nível I e II**: IAA, ICA, IAE, ICE; perdas por ligação; **índice de coliformes totais no padrão**; **DBO do esgoto tratado no padrão**; intermitência de água e de esgoto; micromedição; macromedição; **duração média de reparo de extravasamento**; **reclamações** de água e de esgoto. Informações primárias **anuais (jan–dez, data-base dezembro)**, por município, por componente, urbano/rural, com **rateio** em sistemas integrados e diagnóstico do **nível de confiança** dos dados | NR 9 arts. 8º–10, 16–18, 20–22 | `intelligence/*`, `sigsan-quality`, operations | **NÃO ATENDE** em geral; **PARCIAL** apenas para coliformes (existe `quality_analysis_result`, V2_1; parâmetros da Portaria MS 888 ainda não validados — ver diário §15.4) | Falta `indicator` e `primary_info`; falta rateio; falta urbano/rural | Camada de indicadores com ficha (fórmula, periodicidade, padrão) versionada | P1 |
| R-35 | **Metas de universalização**: 99% dos domicílios com água e 90% com coleta e tratamento até **31/12/2033**, **em cada município**; meta de água só atingida se **IAA e ICA ≥ 99% simultaneamente**; esgoto idem com 90%; verificação **anual**, em janela de 5 anos com cumprimento em ≥3; prorrogação até **01/01/2040** só com estudo e anuência prévia da ERI; metas de não intermitência, perdas e tratamento | Lei 11.445 art. 11-B e §§5º, 9º; NR 8 arts. 10, 25–27; NR 9 art. 14 | `intelligence/*`, registry | **NÃO ATENDE** | Faltam domicílios (denominador, **IBGE por setor censitário**, NR 8 art. 21) e ligações ativas (numerador); falta cálculo urbano/rural | Importar base de domicílios IBGE; cobertura = domicílios com rede disponível; atendimento = ligações ativas; relatório por município/contrato/prestador | **P0** |
| R-36 | **SINISA**: prestadores e titulares são obrigados a fornecer dados (Lei 11.445 art. 53 §7º); ciclo anual com módulos Abastecimento de Água, Esgotamento Sanitário e Gestão Municipal; **Atestado/Certificado de Adimplência** (condição para recursos federais: Lei 11.445 art. 50, V; Decreto 11.599 art. 7º, V); auditoria/certificação (Portaria 719/2018); ERIs acompanham o preenchimento (perfil Regulador) | Lei 11.445 arts. 50 V, 53; Decreto 11.599 art. 7º V; NR 11 art. 58 VII; NR 9 art. 18 | `compliance/sigsan-integration` | **NÃO ATENDE**. O `ModuleRegistry.java` lista "Integrações Regulatórias: SNIS/SINISA", mas é só descrição; o diretório está vazio | Exportação das variáveis SINISA (os códigos do glossário), conferência de consistência, trilha de auditoria de cada valor (origem e responsável) | **P0** |
| R-37 | **Contabilidade regulatória e controle patrimonial** (MCRSB/MCPSB): plano de contas com univocidade e correlação "de-para"; balancetes mensais, relatórios trimestrais, demonstrações anuais; inventário de bens com vidas úteis; rastreabilidade e auditoria | NR 16 arts. 5º–17 | `commercial/sigsan-accounting` | **NÃO ATENDE** | — | Roadmap de longo prazo: obrigatório para prestadores até o exercício de 2032 (limite da NR) | P2 |
| R-38 | **Comprovação da capacidade econômico-financeira** (aditivos de contratos): dados financeiros e projeções do prestador | Lei 11.445 art. 10-B; Decreto 11.598/2023 | `commercial/sigsan-accounting` | **N/A** para o núcleo (é estudo regulatório do prestador, não função do ERP), salvo exportar dados | — | Fora do escopo do MVP | P2 |

### b.4 Parametrização regulatória por tenant

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio |
|---|---|---|---|---|---|---|---|
| R-39 | **Perfil regulatório do tenant**: ERI competente, norma de condições gerais vigente, modelo de regulação (contratual × discricionária), calendário de feriados, tabela de prazos de serviço, parâmetros de corte, multa e juros, data-base de reajuste, modelo de fatura | NR 6, 10, 11, 13; seção (d) | `platform`/`shared` | **PARCIAL**: multi-tenant robusto (RLS, provisionamento) e `tenant_metadata`, mas sem parâmetros regulatórios | Tabela de parâmetros com **vigência** e histórico, para reproduzir uma cobrança passada com a regra da época | Tabela `regulatory_profile` e `regulatory_parameter(valid_from, valid_to)` | P0 |
| R-40 | **Exportadores regulatórios por agência**: layout, periodicidade e canal variam (ARSAE-MG: bases mensais/anuais via SIR/FTP até o dia 25; AGERGS: trimestral CSV UTF-8 via SIA; ARSESP: calendário anual e base trimestral de tarifa social em CSV; AGENERSA: relatório trimestral; AGEPAR: semestral/anual; ADASA: anual e até 20/jan) | Res. ARSAE-MG 205/2025; IN AGERGS 32/2026; Del. ARSESP 1.143/2021 e 1.699/2025; IN AGENERSA 103/2023; Res. AGEPAR 56/2026; Res. ADASA 14 art. 67-A §6º (seção 5) | `compliance/sigsan-integration` | **NÃO ATENDE** (diretório vazio) | Framework de "pacote regulatório" por tenant, com validação do arquivo antes do envio (a AGERGS trata arquivo rejeitado como não entregue) | Plugin por agência + agendador; começar por ARSAE-MG, AGERGS e ARSESP | P1 |
| R-41 | **Motor de compensações automáticas por descumprimento de prazo ou indicador**: crédito de 2× o faturamento em suspensão indevida (ARSAE-MG); desconto de 20% do consumo médio se a religação atrasar (ADASA); crédito do maior entre o dobro da religação de urgência e 20% da 1ª fatura (ARSESP); desconto na fatura por interrupção ≥12 h (AGERGS, REN 37/2017); "não cobra a religação se descumprir o prazo" (ARSESP, AGERGS) | seção 5 | billing + operations | **NÃO ATENDE** | Cada agência tem fórmula própria | Regra declarativa (gatilho, base de cálculo, multiplicador) por tenant, com evidência da OS e do relógio de SLA | P1 |
| R-42 | **Separação de débitos na fatura** (ARSESP: débitos anteriores e parcelas pactuadas não vão na mesma fatura; outros serviços em fatura própria ou por opção do usuário; não cortar água por débito de outro serviço); ARSAE-MG: vedado cobrar serviço alheio ao saneamento | Del. ARSESP 106 art. 72 e 1.206/2021; Res. ARSAE-MG 131 art. 92 | billing | **NÃO ATENDE** | — | Cada linha da fatura com "serviço de origem" e flag "pode motivar corte" | P1 |

---

## (c) Prazos e marcos

### c.1 O que já venceu (relevante para o produto)

| Data | Marco | Fonte |
|---|---|---|
| 31/03/2022 | Prazo para contratos vigentes incluírem as metas do art. 11-B | Lei 11.445 art. 11-B §1º |
| 31/12/2024 | Plano de saneamento como condição para recursos federais (Decreto 7.217 art. 26 §2º; redação atual NÃO CONFIRMADA) | Decreto 7.217 |
| 31/12/2025 | ERI/titular verificar e aplicar o procedimento de conexão (≤1 ano) a todas as edificações com rede disponível | Lei 11.445 art. 45 §7º; NR 8 art. 12 §2º |
| 01/04/2025 e 31/12/2025 | Prazos da NR 7 (ERIs/capitais; municípios >100 mil hab.) | NR 7 art. 110 |
| 13/05 a 03/09/2026 | Coleta de dados do SINISA 2026 (ano-base 2025), **sem prazos adicionais** | Portaria MCID 1.069/2025, via página do SINISA |
| ~11/12/2024 | Início de vigência da Lei 14.898 (dia exato NÃO CONFIRMADO) | Lei 14.898 art. 13 |
| 2025 | Meta de perdas ≤35% (referência Portaria MCID 788/2024; valores NÃO CONFIRMADOS no texto oficial) | fonte secundária |
| 01/07/2026 | Início da vigência da tarifa social da ARSAE-MG (Res. 227/2026) | Res. ARSAE-MG 227/2026 (seção 5.1) |
| 14/08/2026 | ANA suspende, só para 2026, a exigência do sistema de monitoramento da universalização (NR 8, art. 31, IV) | Res. ANA 301/2026 |
| 02/10/2026 | ANA altera a NR 4: exigência do mandato fixo de dirigentes só a partir do procedimento de 2028 | Res. ANA 305/2026 |
| 2025–2026 | Início de verificação das NR 5, 6, 8 e 9 pela ANA (prazos de 12 a 24 meses contados da publicação) | NR 5 §§1º-2º; NR 6 art. 39 p.u.; NR 8 art. 31 p.u.; NR 9 art. 27 p.u. |

### c.2 O que vem em 2026–2030

| Data | Marco | O que o sistema precisa | Fonte |
|---|---|---|---|
| **03/11/2026** | SINISA: envio da versão preliminar das informações | Exportar/conferir dados | Página do SINISA |
| **17/11/2026** | SINISA: prazo de manifestação e validação | idem | idem |
| **31/12/2026** | SINISA: publicação das informações e **Atestado/Certificado de Adimplência** | Ter dados de 2025 consistentes | idem |
| **11/12/2026** | **Prazo para implantar a Tarifa Social** conforme Lei 14.898 e NR 13 (24 meses da vigência da lei), incluindo CadÚnico/BPC, recomposição de equilíbrio e classificação | **R-14 completo** | ANA, FAQ da Tarifa Social: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/perguntas-frequentes ; NR 13 art. 26 |
| 31/12/2026 | NR 7: municípios de 50 a 100 mil habitantes | (SLU) | NR 7 art. 110 |
| 2026 a 2032 | Meta de perdas ≤30% (valores a confirmar) | R-33 | secundária |
| **20/05/2027** | Início da verificação da **NR 11** (condições gerais) e prazo das ERIs para publicar regulamento de **reajuste (NR 10)**: as ERIs vão editar/atualizar as condições gerais de fatura, corte, religação | Parâmetros por tenant | NR 11 art. 74 §3º; NR 10 (parágrafo do prazo) |
| 31/12/2027 | NR 7: municípios <50 mil hab.; **fim da isenção dos incisos VII–IX do art. 50 para recursos federais** (Decreto 12.805/2025) | — | NR 7; Decreto 11.599 art. 15 (red. 12.805) |
| **20/05/2028** | Início da verificação das **NR 13** (estrutura tarifária e tarifa social) e **NR 15** (perdas); exigência do mandato fixo da NR 4 (procedimento anual de 2028) | R-05 a R-15, R-31, R-32 | NR 13 art. 69 §2º; NR 15 art. 14 p.u.; Res. 305/2026 |
| 20/05/2029 | Início da verificação da NR 14 (indicadores de SLU) | (SLU) | NR 14 |
| ~31/08/2029 | ERIs: 3 anos para adotar a NR 16 (a contar da publicação, DOU 31/08/2026) | R-37 | NR 16 art. 15 |
| ~31/08/2031 | ERIs: 5 anos para ajustar sistemas de recebimento (NR 16) | R-37 | NR 16 art. 16 |
| exercício de 2032 | Limite para prestadores implantarem MCRSB/MCPSB | R-37 | NR 16 art. 17 §2º |
| **31/12/2033** | **Metas de universalização**: 99% água e 90% esgoto; perdas ≤25% (referência) | R-35 | Lei 11.445 art. 11-B; NR 8 art. 10 |
| 01/01/2040 | Limite de dilação do prazo de 2033 (casos de inviabilidade, com anuência da ERI) | R-35 | Lei 11.445 art. 11-B §9º |

Rotina anual fixa da ANA (Res. 134/2022 art. 6º): 20/05 instruções; 20/08 envio pelas ERIs; 20/10 comunicação de não observância; 20/12 resultado. O prestador deve ter dados prontos **antes de 20/08** para a ERI.

---

## (d) Variação regulatória e parâmetros configuráveis por tenant

A lei federal fixa o piso; **a ERI define o detalhe** (art. 23 da Lei 11.445; NR 11 art. 5º lista 20 temas que a agência deve regulamentar). Por isso o produto precisa de um **perfil regulatório por tenant com vigência no tempo**.

### d.1 Parâmetros que devem ser configuráveis (todos com `valid_from/valid_to`)

| Grupo | Parâmetro | Origem do valor |
|---|---|---|
| Identificação | ERI competente; norma de condições gerais (número e data); adesão às NR da ANA; modelo de regulação (contratual × discricionária) | ERI/contrato |
| Fatura | Modelo e itens obrigatórios; antecedência mínima de entrega; datas de vencimento opcionais; mensagens obrigatórias; formato do código de barras/convênio | ERI |
| Cobrança | Multa de mora (teto), juros (1% ao mês pro rata die em exemplos de contratos; **valor padrão NÃO CONFIRMADO**), atualização monetária, base de cálculo | contrato/ERI; CDC |
| Tarifa | Categorias e subcategorias; parcela fixa: tarifa básica × consumo mínimo (e franquia); faixas; percentual de esgoto; disponibilidade (multiplicador); economias inativas; grandes usuários; bandeiras | contrato/ERI |
| Reajuste | Data-base, índice (IPCA ou fórmula paramétrica), fator X, prazo de publicação | contrato/NR 10 |
| Medição | Nº de meses da média; limite de ciclos consecutivos sem leitura; intervalo de leitura | ERI |
| Corte | Dias de atraso mínimos; prazo do aviso (≥30 dias) e forma; horário permitido; dias vedados; lista de feriados; classes protegidas (saúde, educação, baixa renda); quitação para religação; taxa de religação | Lei + ERI |
| Prazos | Tabela de serviços e prazos (ligação, religação, reparo, reclamação); religação de urgência e preço | ERI (NR 11 art. 72) |
| Tarifa social | Critérios locais mais benéficos que o piso federal; desconto sobre a tarifa básica; limite de consumo; desconto acima de 15 m³; periodicidade de atualização (<12 meses permitido); formato do arquivo da base CadÚnico | ERI/titular |
| Informação à ERI | Periodicidade e formato dos relatórios (reclamações, indicadores, subsídios, contabilidade) | ERI |
| Contrato | Metas de universalização, perdas, intermitência e tratamento por município e ano | contrato/plano municipal |

### d.2 O que muda por agência (levantamento de 12/10/2026)

Fonte: leitura das normas de condições gerais de 10 agências (detalhe e artigos na seção 5). **Convergências** (podem ser *defaults* do produto, sempre sobrescrevíveis): multa de mora de **2%**; juros de **0,033% ao dia** ou **1% ao mês** pro rata; correção por índice que **muda por agência** (IPCA, INPC, Selic, índice do reajuste); **aviso de corte de 30 dias**; **vedado iniciar corte de sexta a domingo, feriado e véspera** (reforçado pela Lei 14.015/2020); **6 datas de vencimento** à escolha do usuário; média de consumo quando não há leitura. **Divergências** (precisam ser parâmetro):

**Matriz 1 — fatura, cobrança e corte**

| Parâmetro | ARSAE-MG | ADASA (DF) | ARSESP (SP) | AGERGS (RS) | AGEPAR (PR) |
|---|---|---|---|---|---|
| Entrega da fatura antes do vencimento | ≥10 dias (social/resid./pública); ≥5 dias (comercial/industrial) | 10 dias | vencimento ≥5 dias úteis após a apresentação (10 d.u. pública) | vencimento ≥10 dias da apresentação | ≥3 dias |
| Datas de vencimento | ≥6; até 3 trocas por ano civil | ≥6 | 6 | troca até 2× em 12 meses (nº mínimo NÃO CONFIRMADO) | ≥6 |
| Atraso mínimo para cortar | não fixado | **≥60 dias**; vedado após 120 dias | não fixado | vedado após 90 dias da fatura vencida | vedado após 90 dias |
| Aviso de corte | destacado na fatura seguinte ao débito; executa só 30 dias corridos depois; novo aviso se não executado em 90 dias | notificação ≥30 dias, sob pena de nulidade | correspondência específica, emitida em até 90 dias da ocorrência e ≥30 dias antes (45 dias para saúde/educação/internação) | 30 dias (art. 109 §2º, redação ambígua para inadimplência); essenciais 60 dias; baixa renda 40 dias | escrito, ≥30 dias, mais aviso no cavalete |
| Classes protegidas | sem carência específica na Res. 131 | saúde, educação, internação e baixa renda com tarifa social: corte só após **12 meses** de inadimplência (Lei Distrital 5.990/2017) | saúde/educação/internação: 45 dias | prazos maiores (60 e 40 dias) | aviso de 30 dias e prazos que preservem a saúde |
| Religação | 2 dias corridos; urgência 12 h (facultativa); indevida 12 h + crédito de 2× | 40 h úteis; indevida 3 h; pode exigir quitação | 48 h; urgência 6 h; indevida 6 h | 24 h; urgência 4 h; indevida 4 h + crédito do dobro | 4 h após quitação; indevida 4 h |
| Multa / juros / correção | multa ≤2%; **Selic**; negativação permitida | juros 0,033%/dia; multa ≤2%; **INPC** | juros ≤0,033%/dia; multa 2% | multa 2%; juros 1% a.m.; índice do reajuste | juros 0,033%/dia; multa 2%; **IPCA** |
| Devolução em dobro | sim (a maior; duplicidade em 30 dias, senão em dobro) | sim (indevido; duplicidade) | sim, salvo engano justificável | sim, salvo engano justificável | sim (até 10 anos) |
| Média sem leitura | 12 ciclos; **máx. 6 ciclos** estimados, depois 50%/100%/150% | média de 12 meses; após o 2º mês só parte fixa se a causa for do prestador | 180 dias; **máx. 3 ciclos**; depois consumo mínimo | 6 faturados; **máx. 3 ciclos** | 5 meses |

**Matriz 2 — outras agências**

| Parâmetro | ARES-PCJ | AGENERSA (RJ) | AGR-GO | ARIS (SC) | ARCE (CE) |
|---|---|---|---|---|---|
| Norma-base | Res. 50/2014 **+ regulamento de cada município** | Decreto 48.225/2022 + IN 103/2023 | RN 09/2014 | RN 19/2019 | Res. 130/2010 |
| Fatura antes do vencimento | sem regra geral (por município) | 5 dias úteis | 10 dias | ≥5 d.u. (10 pública) | 5 d.u. (10 pública) |
| Aviso de corte | 30 dias com comprovação; vedado após 3 meses | 30 dias; suspensão só a partir do 30º dia de atraso | 30 dias; só em dias úteis; essenciais: avisar a agência | 30 dias; vedado após 120 dias; essenciais: avisar a ARIS | 30 dias; essenciais: avisar a ARCE |
| Dias vedados | sex–dom, feriado, véspera | NÃO CONFIRMADO no regulamento | após 12 h de sexta e vésperas | após 12 h de sexta e vésperas | após 12 h de sexta e vésperas |
| Religação | 24 h; indevida 12 h; urgência 4 h | 48 h; indevida 24 h | 24 h; urgência 6 h (úteis) / 14 h; indevida 6 h; exige quitação | 48 h; urgência 4 h; indevida 4 h | 1 d.u. após pagamento; urgência 4 h; indevida 4 h |
| Multa / juros / correção | conforme legislação municipal (exemplo: 2%, 1% a.m., IPCA) | multa NÃO CONFIRMADA; juros 1% a.m. em débitos negociados | multa 2%; juros 1% a.m.; **INPC** | juros ≤0,033%/dia; multa 2%; IPCA | juros ≤0,033%/dia; multa 2%; IPCA |
| Média sem leitura | 6 meses (máx. de ciclos NÃO CONFIRMADO) | 12 leituras; >3 meses sem leitura = irregularidade do usuário | 6 meses; máx. 6 ciclos; depois 50% | 6 meses; máx. 3 ciclos | 6 meses; máx. 3 ciclos |
| Tarifa social | Res. 592/2024: 50% até 15 m³ **e 25% de 15 a 20 m³** | critérios propostos pela concessionária; norma da Lei 14.898 NÃO CONFIRMADA | RN 186/2021 (Auxílio Brasil/CadÚnico extrema pobreza), sem adequação localizada | RN 56/2026 (cita Lei 14.898 e NR 13) | NÃO PESQUISADO |

**Tarifa social por agência (ponto crítico para o prazo de 11/12/2026)**

| Agência | Norma local | Particularidade que o sistema precisa suportar |
|---|---|---|
| ARSAE-MG | Res. 227/2026 (vigência 01/07/2026) | Social I (CadÚnico, pobreza/extrema pobreza) e Social II; **sem limite de consumo**; base trimestral do CadÚnico repassada pela ARSAE; inclusão a pedido em 30 dias; documentos guardados por 10 anos |
| ADASA | art. 67-A da Res. 14 (Res. 42/2024) | **limite de 30 m³/mês no art. 101, II**, divergente da NR 13; vedada cobrança de ligação, vistoria, 2ª via e desativação do beneficiário; relatório até 20/jan |
| ARSESP | Del. 1.544/2024 e 1.699/2025 | Residencial Vulnerável, Social (I) e Social II; base mensal CadÚnico/BPC; base trimestral de retorno em CSV (UTF-8, ";") |
| AGERGS | RED 892/2026 (fonte secundária) | 50% até 15 m³; custeio por reajuste extraordinário (5,76% segundo imprensa) |
| AGEPAR | Res. 36/2025 | 50% estendido à faixa fixa e aos 15 m³ iniciais; uma unidade por CPF; aplicação em até 120 dias |
| ARES-PCJ | Res. 592/2024 | 50% até 15 m³ + 25% de 15 a 20 m³; CadÚnico atualizado em até 2 anos; inclusão automática por CPF |
| ARIS | RN 56/2026 | 50% até 15 m³; 24 meses para implantar |

**Adesão às NR da ANA** (planilhas "lista positiva" 2025, divulgadas pela ANA em 20–22/12/2025, lidas pelo subagente; ausência = "não consta"): ARES-PCJ 77 municípios, AGR-GO 223, ARIS 210, Arpe 172, ARCE 32, AGEMS 69, AGER-MT 2; AGENERSA e ARSAL só nas NR 5 e 6. A AGERGS e a AGEPAR informaram ter atendido todas as NR no ciclo de 2025 (notícia das próprias agências, confiança média). **Implicação:** o sistema não pode supor que a agência do cliente já alinhou suas normas à NR 11/13; deve guardar *qual* norma local vale.

**Outros pontos que variam e já viram campo no produto**
- **ARSAE-MG** exige envio de **bases de dados** (Banco de Faturamento por fatura, Cadastro de Economias Sociais, solicitações e reclamações, ordens de serviço) via SIR/FTP até o dia 25 do mês seguinte (Res. 205/2025; prazo exato do Banco de Faturamento: NÃO CONFIRMADO). **AGERGS**: trimestral, CSV UTF-8, pelo SIA (IN 32/2026). **ARSESP**: Calendário Anual de Informações Periódicas. **AGENERSA**: relatório trimestral à ouvidoria. **AGEPAR**: dados de ligações semestrais e indicadores até 30/04 (Res. 56/2026). **ADASA**: indicadores anuais e tarifa social até 20/jan. → *O módulo de relatórios regulatórios precisa de "exportador por agência" (layout, periodicidade, canal)*.
- **ARSAE-MG** veda cobrar na fatura serviço alheio ao saneamento (salvo doação/serviço assistencial autorizado); **ARSESP** veda cortar por débito de outros serviços e exige fatura própria ou opção do usuário (Del. 1.206/2021; cofaturamento de resíduos na Del. 1.734/2025) → afeta R-16.
- **AGERGS** prevê **compensação automática por interrupção ≥12 h** (REN 37/2017); **ADASA** dá desconto de 20% do consumo médio se a religação atrasar; **ARSAE-MG** credita 2× o faturamento em suspensão indevida → o sistema precisa de **motor de compensações por descumprimento de prazo**, parametrizado por agência.
- **AGENERSA**: TAC com Ministério Público e Defensoria (parcelamento em até 60 vezes sem corte; religação em 5 dias para tarifa social) para concessionárias aderentes.
- **AGEPAR** (Res. 38/2025): após notificação, 60 dias para conectar ao esgoto, depois cobrança sem ligação. **AGERGS** (REN 74/2026): campanha mínima de 60 dias, isenção de 2 a 3 faturas para quem conecta no prazo, lista semestral (março e setembro) de imóveis não conectados à ERI.

---

## (e) Perguntas em aberto para o jurídico/regulatório

| ID | Pergunta |
|---|---|
| E-01 | Qual **ERI** regula cada um dos primeiros clientes e qual **norma de condições gerais** está vigente? Ela já aderiu às NR 11 e 13 da ANA? Sem isso, os parâmetros da seção (d) ficam sem fonte. |
| E-02 | O **teto de 2% de multa de mora** do CDC (art. 52 §1º) e o regime de juros se aplicam à fatura de água e esgoto de **prestadores municipais** (autarquias)? Qual default por tenant? |
| E-03 | **Tarifa social**: o prazo de **11/12/2026** (ANA) vale para contratos licitados antes da NR 13? O art. 70 da NR 13 ressalva o capítulo da tarifa social, mas o dia exato de vigência da Lei 14.898 (10, 11 ou 12/12/2024) precisa ser confirmado. |
| E-04 | Como o prestador deve **receber a base do CadÚnico/BPC** (formato, periodicidade, convênio com a ERI)? Quem é o encarregado do sigilo (NR 13 art. 38 §2º)? |
| E-05 | **Retenção de 120 meses** de histórico (NR 11 art. 22) × minimização e prazos de eliminação da LGPD. |
| E-06 | **Portaria MCID 788/2024**: qual é o texto oficial e quais as metas de perdas por ano (até 2033)? Ela substituiu a Portaria MDR 490/2021? |
| E-07 | O **SINISA** aceita envio automatizado (API/arquivo) ou só o preenchimento em formulário web? Qual o dicionário de variáveis (glossário) vigente para o ciclo 2026? |
| E-08 | **Decreto 7.217/2010**: qual é a redação vigente do art. 26 §2º depois da revogação do Decreto 11.467/2023? O decreto será substituído? |
| E-09 | Em **cofaturamento**, o prestador de água pode cortar a água por inadimplência da taxa de resíduos? A ARSESP veda (ver seção 5); e as demais agências? |
| E-10 | Em edificações sem ligação com rede factível, qual agência aceita **cobrança por disponibilidade** de **água** (NR 13 art. 20 só "admite")? |
| E-11 | Aplicabilidade das **NR 9, 11 e 13 a contratos de concessão antigos** (as NR excluem concessões licitadas antes da vigência, mas as ERIs podem regulamentar além). |
| E-12 | Resultado das **ADIs 6.492, 6.536, 6.583 e 6.882** (STF) sobre o Novo Marco: algum dispositivo relevante foi declarado inconstitucional? NÃO CONFIRMADO. |
| E-13 | Para o prestador **autarquia municipal sem ERI** designada, quais regras valem (NR 11 art. 52 §3º manda manifestar ao titular)? |
| E-14 | **AGERGS RN 66/2022, art. 109**: o aviso de 30 dias vale também para inadimplência? A leitura literal do §2º cita só os incisos III e V. |
| E-15 | **ADASA**: o limite de 30 m³ para a tarifa social (art. 101, II) segue válido diante da Lei 14.898/NR 13? Houve norma posterior à Res. 42/2024? |
| E-16 | **AGEPAR**: o Regulamento da Res. 3/2020 está em vigor sem a revogação do Decreto Estadual 3.926/1988 (art. 2º)? Qual é o texto completo do art. 122 (conteúdo da fatura)? |
| E-17 | **ARES-PCJ**: para cada município cliente, qual é o regulamento de prestação vigente (a Res. 50/2014 é só o piso)? |
| E-18 | A ANA exige que a ERI comprove adoção da NR 11 a partir de **20/05/2027** (texto) ou **27/05/2027** (notícia lida por subagente)? Confirmar no DOU. |
| E-19 | Agências **não pesquisadas** (ARPE, AGEMS, AGER-MT, ARSAL, ARSEP e outras): se algum cliente-alvo estiver nelas, repetir o levantamento. |
| E-20 | **Fatura em moeda e arredondamento**: algum ato define arredondamento (o GSAN mistura HALF_UP e HALF_DOWN; 00-CONSOLIDADO §4.1)? NÃO ENCONTRADO nas normas lidas. |

---

## 5. Detalhamento por agência reguladora

Fonte de cada linha: a norma indicada na coluna "Artigo", na versão consolidada acessível em 12/10/2026. Confiança **alta** salvo indicação. "d.u." = dias úteis. Valores de tarifa **não** foram levantados (mudam todo ano).

### 5.1 ARSAE-MG (Minas Gerais) — Res. 131/2019, 129/2019, 94/2017, 151/2021, 184/2023, 205/2025, 227/2026

| Item | Regra | Artigo |
|---|---|---|
| Fatura (conteúdo) | nome, endereço e matrícula; categoria; nº do hidrômetro; leituras e datas anterior/atual; data da próxima leitura; volume e critério de estimativa; **emissão e vencimento**; histórico de 12 ciclos e média; discriminação com valores; descontos; tributos; multa, juros e correção; total; aviso de débito pendente; qualidade da água; e-mails da prestadora e da ARSAE; aviso de reajuste (data, % médio, nº da resolução); avisos de consulta e audiência pública. Incisos sobre % de esgoto e detalhe por faixa foram **revogados pela Res. 216/2025 e passaram ao site** | Res. 131, art. 92 |
| Mensagens | método de estimativa e motivo; após o 6º ciclo estimado, proporção do presumido; data de suspensão ou do faturamento a 150%; tarifa social: aviso de perda por 3 ciclos; vedado cobrar serviço alheio ao saneamento | arts. 69, 70; Res. 227 art. 16 §2º |
| Prazos de atendimento | vistoria de ligação 70% em 3 d.u. e 100% em 5; ligação convencional 70% em 7 d.u. e 100% em 10; com prolongamento 70% em 10 e 100% em 20; vazamento 85% em 24 h e 100% em 48 h; resposta formal em 5 d.u.; ressarcimento de dano em 30 dias; protocolo guardado por 5 anos | Res. 131 arts. 14–15, 33–35; Res. 129 art. 106; Res. 94 |
| Consumo sem leitura | 12 ciclos; depois volume pós-regularização; depois presumido; **máx. 6 ciclos**; então 50% (culpa do prestador), 100% (inviabilidade técnica) ou 150% (usuário impede) | arts. 69, 70, 73 |
| Esgoto, mínimo, disponibilidade | esgoto = volume faturado de água, com exceções; "consumo mínimo" só se previsto; tarifa fixa de disponibilidade a usuário factível após comunicação e 90 dias; isenção para solução própria regular | arts. 2º IX, 21, 22, 88, 89 (mínimo: confiança média) |
| Dados à agência | bases EC (inclui **Banco de Faturamento** por fatura e **Cadastro de Economias Sociais**), OP (solicitações, reclamações, ordens de serviço, paralisações), SD e RG; mensais até o dia 25; anuais até março; via SIR (FTP)/SEI-MG; SNIS/SINISA e relatório da ouvidoria anuais (confiança média) | Res. 205 arts. 3º, 4º, 11; Res. 131 art. 8º |
| Ouvidoria | obrigatória para prestador regional ou local com **>30.000 usuários**; resposta em 30 dias prorrogável; relatório anual | Res. 151 arts. 5º, 8º, 16, 20 |

### 5.2 ADASA (DF) — Res. 14/2011 consolidada

| Item | Regra | Artigo |
|---|---|---|
| Fatura | qualidade da água; nome; inscrição, categoria e classe; endereço; hidrômetro e instalação; leituras; datas de apresentação e vencimento; consumo; **preços por faixa e parte fixa**; histórico de 12 meses; total; faturas vencidas; juros e multa; informações do bônus-desconto (Res. ADASA 6/2010); Ouvidoria-Geral do DF | art. 109 |
| Corte | só com atraso ≥**60 dias**; notificação ≥30 dias sob pena de nulidade; vedado após 120 dias; vedado sex–dom, feriado e véspera; saúde, educação, internação e baixa renda: **12 meses** (Lei Distrital 5.990/2017), com comunicação à ADASA 30 dias antes | arts. 121, 122, 123, 125 |
| Religação | 40 h úteis; indevida 3 h sem ônus; atraso: desconto de 20% do consumo médio na fatura seguinte; pode exigir quitação (urgência: NÃO CONFIRMADO) | arts. 121 §6º, 128–130; Anexo IV (extração do PDF desalinhada: valores indicativos) |
| Tarifa | **bipartida** (parte fixa + variável por faixas); mínimo de 10 m³ **revogado em 2019**; esgoto = 100% da água (50% em imóvel em construção; 60% no ramal condominial interno) | arts. 92, 100, 103; Anexo III |
| Atraso | juros 0,033%/dia; multa ≤2%; **INPC** | art. 111 |
| Revisão | pedido em até 90 dias; decisão em 30 dias; **silêncio = procedente**; devolução em dobro | arts. 112, 115–118 |

### 5.3 ARSESP (SP) — Del. 106/2009 e alterações

| Item | Regra | Artigo |
|---|---|---|
| Fatura | 23 incisos (nome, códigos, categoria, economias, endereço, tipo de ligação, leituras, volumes, datas, histórico de 180 dias e média, tarifas e valores separados água/esgoto, tributos, multa e juros, ouvidoria e SAU-ARSESP, qualidade da água, faturas vencidas, aviso de alto consumo, fator de poluição); débitos anteriores e parcelas pactuadas **não vão na mesma fatura**; outros serviços em fatura própria ou, por opção, na de água | art. 76; Del. 1.206/2021 |
| Corte | aviso por correspondência específica, em até 90 dias da ocorrência e ≥30 dias antes; saúde/educação/internação 120 dias e 45 dias; vedado sáb/dom/feriado/véspera; **vedado cortar por débito de outros serviços**; tarifa social não perde o benefício por inadimplência | arts. 88, 90, 91, 93, 95; Del. 1.699 art. 4º |
| Religação | 48 h; urgência 6 h; indevida 6 h sem ônus + crédito do maior entre o dobro da religação de urgência e 20% da 1ª fatura | arts. 92, 97, 98 |
| Consumo sem leitura | média de 180 dias, **máx. 3 ciclos**; depois consumo mínimo; consumo mínimo provisório de 10 m³/economia nas transitórias; faturar esgoto sem conexão é indevido salvo culpa do usuário | arts. 62–66, 84; Transitórias 12 e 13-A; Del. 1.117/2021 |
| Prazos | ligação em rede existente 10 d.u. (inspeção 3 + execução 7); resposta a reclamação 15 dias; laudo de aferição 15 dias; ressarcimento 60 dias; **Tabela de Preços e Prazos** homologada por prestador (não obtida) | arts. 19, 20, 108, 110, 114 |
| Dados | Calendário Anual de Informações Periódicas (publicado até 1º/12); tarifa social: base trimestral em CSV (UTF-8, ";") em até 30 dias do trimestre (confiança média) | Del. 1.143/2021; 1.699/2025 |
| Atendimento | 24 h, gratuito, protocolo; espera máx. de 1 minuto; agência presencial por município | arts. 107–111 |

### 5.4 AGERGS (RS) — RN 66/2022 (Corsan/BRK), REN 37/2017, REN 74/2026, IN 32/2026

| Item | Regra | Artigo |
|---|---|---|
| Fatura | nome, código, categoria, endereço, hidrômetro, leitura atual e as **6 anteriores**, datas, componentes e tarifas, tributos, total, indicadores de qualidade e continuidade, compensações, telefones do prestador e da ouvidoria da AGERGS (texto do art. 123 cortado: confiança média) | art. 123 |
| Corte | art. 109 §2º exige 30 dias para os incisos III e V; para inadimplência (IV) há notificação de débito e suspensão em até 90 dias (§6º): **leitura literal ambígua, confirmar com a agência**; não suspender após 90 dias da fatura vencida; vedado sex–dom/feriado/véspera; essencial/saúde/educação 60 dias à autoridade; baixa renda 40 dias | arts. 109, 112–114 |
| Religação | 24 h após quitação; urgência 4 h; indevida 4 h + crédito do dobro | arts. 111, 115, 116 |
| Esgoto | coletado a **50%** e tratado a **70%** do preço do m³ de água; disponibilidade: REN 74/2026 | arts. 93, 98, 100; REN 74 |
| Atraso | multa 2%; juros **1% a.m. pro rata die**; atualização pelo índice do reajuste | art. 136 |
| Compensação | interrupção ≥12 h: desconto na fatura por fórmula (REN 37/2017 art. 12; fórmula não transcrita); registro digital de interrupções por 5 anos | REN 37 |
| Dados | trimestral, CSV UTF-8, pelo **SIA**, até o último dia útil do mês seguinte; mensal por município conforme NR 8 e 9; arquivo rejeitado = não entregue | IN 32/2026 arts. 3–7 |

### 5.5 AGEPAR (PR) — Res. 3/2020 e Res. 38/2025

| Item | Regra | Artigo |
|---|---|---|
| Fatura | art. 122 lista o conteúdo mínimo, **mas o texto oficial é incompleto**; entrega ≥3 dias antes do vencimento; ≥6 datas; fatura simplificada grátis no site; braille a pedido (confiança baixa para o conteúdo) | arts. 120–123 |
| Corte | aviso escrito ≥30 dias; vedado sex–dom/feriado/véspera; não cortar fatura vencida há mais de 90 dias; saúde/educação/baixa renda: aviso 30 dias e prazos que preservem a saúde; corte não elimina a cobrança de esgoto pela tarifa mínima | art. 142 |
| Religação | 4 h após comprovada a quitação, inclusive fins de semana; indevida 4 h; sem cobrança por religação por inadimplência salvo se pedida após 90 dias do corte (Lei Estadual 14.471/2004) | arts. 145, 151, 152 |
| Consumo sem leitura | média de 5 meses; sem histórico: consumo mínimo da categoria; esgoto como percentual do faturado de água; Res. 38/2025: 60 dias para ligar ao esgoto após notificação | arts. 104, 115–117, 129, 31 |
| Atraso | juros 0,033%/dia; multa 2%; **IPCA** | art. 125 |
| Dados | Res. 56/2026: ligações e economias semestrais; indicadores até 30/04; georreferenciamento SIRGAS 2000; AGEPAR alimenta o SASB da ANA | arts. 3º, 16, 30, 36 |

### 5.6 ARES-PCJ, AGENERSA, AGR-GO, ARIS e ARCE

Resumo no quadro (d.2). Pontos que merecem atenção própria:

- **ARES-PCJ**: *cada município tem o seu regulamento*, que diverge do piso da Res. 50/2014. Exemplo (Sertãozinho, Res. 610/2025): limite de 4 meses de atraso, corte só de segunda a quinta das 8h às 14h, religação até 18h, mínimo de 15 m³ por economia. A devolução em dobro foi **retirada** pela Res. 460/2022. Prazos máximos (art. 130-E): vistoria 3 d.u. urbana/5 rural; ligação 10 d.u./15; religação por inadimplência 24 h; corte/religação a pedido 2 d.u.; reparo de vazamento 5 d.u.; desobstrução de esgoto 24 h; aferição 10 d.u.; troca de hidrômetro violado 2 d.u.
- **AGENERSA**: prazos de resposta em 5 d.u. (instalação, vazamento, falta de fornecimento, religação, carro-pipa), 10 d.u. e 15 d.u. (IN 103 art. 67 e Anexo II); relatório estatístico trimestral (até 05/abr, 05/jul, 05/out e 05/jan); SAC 24h por 7 dias.
- **AGR-GO**: RN 207/2023 (Saneago): falta de água ou qualidade em 1 d.u.; queda de pressão em 4 d.u.; aviso de interrupção programada com 2 d.u.; restituição em 30 d.u.; indenização por corte indevido = o maior entre o dobro da religação de urgência e 10% da fatura média; vazamento oculto: desconto de 70% (1ª) e 50% (2ª ocorrência).
- **ARIS**: vazamento oculto: desconto de até 70% (45 dias da leitura); vedado cobrar religação de baixa renda após a quitação.
- **ARCE**: indenização por corte indevido: o maior entre o dobro da religação de urgência e 20% da fatura.

> Observação: a data de 27/05/2027 para comprovar a NR 11, citada em notícia da ANA lida por um subagente, **diverge** do texto da NR 11 (art. 74 §3º: **20/05/2027**). Adotei o texto da resolução.
