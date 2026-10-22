# 09 — Regulatório: qualidade da água, monitoramento e lançamento de efluentes (2015–2026)

Data da pesquisa: 12/10/2026. Escopo: potabilidade (Portaria GM/MS 888/2021 e antecessoras), SISAGUA, informação ao consumidor, laboratórios (myLIMS), esgoto (CONAMA 357/430 e a revisão de 2026), lodo, reúso, licenciamento e outorga. Cruzado com `intelligence/sigsan-quality` (entidades, `LabService`, `ScadaIntegrationService` e a migration `V2_1__create_quality_schema.sql`).

> **AVISO: isto não é parecer jurídico.** O relatório é um levantamento técnico de normas para orientar requisitos de software. Toda decisão (defaults, prazos, texto de laudo, obrigações por tenant) exige revisão por advogado/regulatório e por responsável técnico em qualidade da água antes de ir a produção. A incerteza está marcada onde existe: "NÃO CONFIRMADO" significa que não consegui confirmar em fonte oficial e diz o que falta.

## 0. Método, fontes e limites

- **Textos lidos de fato:** Portaria GM/MS 888/2021 (republicação do DOU nº 96, de 24/05/2021, seção 1, p. 69, lida a partir do PDF e conferida com a página do Ministério da Saúde/BVS); Portaria GM/MS 2.472/2021 (DOU nº 186, de 30/09/2021, seção 1, p. 163); Resolução CONAMA 430/2011 (cópia do DOU); Resolução CONAMA 498/2020 (cópia do site do CONAMA); Resolução CONSEMA-RS 355/2017; Decreto 5.440/2005 (cópia da Câmara dos Deputados); Guia do Ministério da Saúde de 2024 para implementação da norma. As tabelas dos anexos da 888 foram extraídas do PDF do DOU e conferidas contra a página do Ministério; onde as duas divergem, está dito.
- **Ferramentas:** as páginas foram lidas por um extrator que resume; por isso as tabelas críticas foram refeitas a partir do texto do PDF. Itens que só vi em síntese de busca estão marcados "fonte secundária".
- **Confiança:** alta = texto da norma lido em fonte oficial ou cópia fiel do DOU; média = fonte secundária consistente ou leitura com ambiguidade de layout; baixa = indício sem confirmação.
- Hoje (12/10/2026) **não encontrei** nenhuma portaria que revogue ou substitua a 888/2021. A página do Ministério lista apenas a 888 e a 2.472 como atualizações da norma vigente.

## 1. Resumo executivo (o que mais importa)

1. **A norma vigente de potabilidade é o Anexo XX da PRC 5/2017, na redação da Portaria 888/2021, alterada pela Portaria 2.472/2021.** A 2.472 (que o diário e o seed ignoram) mudou o Art. 44 §3 (tirou o pH das medições obrigatórias em toda amostra bacteriológica) e **substituiu o Anexo 13** (tabela de amostragem físico-química).
2. **O seed `quality_parameter` não passa na validação.** Das 28 linhas: 11 coincidem com a Portaria (E. coli, nitrato, nitrito, ferro, manganês, alumínio, arsênio, chumbo, cádmio, mercúrio, THM), 1 é parcial (coliformes totais) e **16 divergem ou não têm base** (ex.: ácidos haloacéticos 60 µg/L contra 80 µg/L da norma; fluoreto "VMP 0,7" contra 1,5 mg/L; cloro "VMP 0,2", que na norma é um **mínimo**; "agrotóxicos totais 0,5 µg/L", parâmetro que não existe). Das 100 linhas dos Anexos 9–11, **84 não têm correspondente**. A tabela está na seção 4.
3. **O modelo "um VMP escalar por parâmetro" não representa a norma.** Coliformes totais dependem da população, turbidez é percentil mensal por tipo de filtração, THM e ácidos haloacéticos são média móvel de 12 meses por ponto, nitrato e nitrito têm regra de soma, existe regra de LD/2 e LQ/2. `LabService.recordAnalysisResult` recebe `conformity` do chamador e não calcula nada.
4. **Plano de amostragem anual (Art. 44) e números mínimos (Anexos 13, 14, 15) não existem no sistema.** `SamplingPoint` não tem população atendida, tipo de manancial nem classificação SAA/SAC; a frequência é texto livre.
5. **Comunicação de não conformidade é só `log.warn`** (`LabService.notifyNonConformance`; alarme SCADA idem). A norma exige comunicação **imediata** à autoridade de saúde e à população (Art. 14 XXII), e, para cianotoxinas na saída do tratamento, também a clínicas de hemodiálise e indústrias de injetáveis (Art. 43 §9).
6. **SISAGUA, relatório anual (até 15/03, Decreto 5.440/2005) e informação na fatura não têm nenhum suporte.** Os prazos de inserção no SISAGUA **não são nacionais**: variam por estado (SP: dia 10 de cada mês, fonte secundária). Precisa ser configurável por tenant.
7. **Mudança grande em curso no esgoto:** em 02/09/2026 o CONAMA aprovou nova resolução de lançamento de efluentes que **revoga a 430/2011** e altera a 357/2005 (mais controle de nitrogênio amoniacal e fósforo em ETE, por porte), e uma resolução conjunta CONAMA/CNRH de **reúso**. **Número e publicação no DOU: NÃO CONFIRMADO.** (verificado em 22/10/2026: continua sem publicação no DOU; o comunicado do MMA de 11/09/2026 não indica em qual das duas plenárias, 01/09 ou 02/09, foi aprovada cada norma, e a associação desta matéria à 151ª reunião ordinária de 02/09/2026 vem da página do processo no SINCONAMA, que não registra o resultado da votação) O catálogo de limites de efluente precisa ser versionado por vigência, não fixo no código.
8. **Correções ao enunciado:** a Resolução CONAMA 503/2021 **não** trata de lodo de ETE (é fertirrigação com efluentes de indústrias de alimentos, bebidas, laticínios, frigoríficos e graxarias). O lodo de esgoto/biossólido é a **CONAMA 498/2020**, que revogou a 375/2006 e a 380/2006 (Art. 36). A Consulta Pública Inmetro nº 21/2025 **não** é revisão da potabilidade (trata de equipamentos para consumo de água).

## 2. (a) Quadro de normas

| Norma | Esfera | Data / última alteração | Vigente? | Fonte (URL) | Confiança |
|---|---|---|---|---|---|
| Portaria GM/MS nº 888/2021 (altera o Anexo XX da PRC 5/2017) | Federal (MS) | 04/05/2021; DOU 07/05/2021 (nº 85, seção 1, p. 126–136); republicada no DOU 24/05/2021 por incorreção. Art. 3º: vigência na publicação | Sim | https://bvsms.saude.gov.br/bvs/saudelegis/gm/2021/prt0888_07_05_2021.html ; cópia do DOU: https://ses.sp.bvs.br/wp-content/uploads/2021/05/U_PT-MS-GM-888-REP_040521.pdf | Alta |
| Portaria GM/MS nº 2.472/2021 (altera Art. 44 §3 e substitui o Anexo 13 do Anexo XX) | Federal (MS) | 28/09/2021; DOU 30/09/2021 (nº 186, seção 1, p. 163). **É a última alteração que encontrei** | Sim | https://bvsms.saude.gov.br/bvs/saudelegis/gm/2021/prt2472_30_09_2021.html ; https://ses.sp.bvs.br/wp-content/uploads/2021/09/U_PT-MS-GM-2472_280921.pdf | Alta |
| Portaria de Consolidação GM/MS nº 5/2017, Anexo XX (potabilidade) | Federal (MS) | PRC 5 de 28/09/2017. Redação atual = 888/2021 + 2.472/2021. Art. 51: revisão em 5 anos "ou a qualquer tempo" (prazo venceu em 05/2026; **nenhuma revisão publicada encontrada**) | Sim | https://www.gov.br/saude/pt-br/composicao/svsa/saude-ambiental/vigiagua/norma-de-qualidade | Alta |
| Portaria MS nº 2.914/2011 | Federal (MS) | 12/12/2011. Vigorou como ato autônomo de 14/12/2011 até a consolidação de 2017 e, depois, só como Anexo XX da PRC 5/2017 (origem confirmada no Guia 2024), cuja redação foi reescrita pela 888/2021 (o art. 2º da 888 dá nova redação ao Anexo XX; a 888 não revogou a 2.914). Dispositivo formal de revogação: art. 864, CXXXIII, da PRC 5/2017 (verificado em 22/10/2026 no texto oficial do Ministério da Saúde). Citação recomendada: "Anexo XX da PRC 5/2017, na redação da Portaria GM/MS 888/2021" | Superada | Guia MS 2024: https://www.gov.br/saude/pt-br/centrais-de-conteudo/publicacoes/guias-e-manuais/2024/guia-para-implementacao-da-norma-de-qualidade-da-agua-para-consumo-humano.pdf | Média |
| Anexo XXI da PRC 5/2017 (fluoretação: valores recomendados de fluoreto) | Federal (MS) | Referido no Art. 36 §1 da 888; conteúdo conferido no texto oficial em 22/10/2026: Anexos 1 a 3 internos (limites e compostos, métodos e técnicas, fórmula da concentração recomendada), requisitos mínimos do sistema (art. 3º, entre eles abastecimento contínuo) e técnicas de dosagem; o limite máximo de 1,5 mg/L está no Anexo 9 do Anexo XX, e o Guia 2024 esclarece que não há contradição; o mesmo guia reconhece que as faixas reproduzem a referência de 1975 e deveriam ser atualizadas (ato de atualização não localizado) | Sim (presumido) | idem Guia 2024 | Média |
| Decreto nº 5.440/2005 (informação ao consumidor sobre a qualidade da água; Anexo = regulamento técnico) | Federal | 04/05/2005. Câmara: "não consta revogação expressa"; relatórios de 2026 ainda o citam. A 888 (Art. 6 V, 14 XVIII) o cita "ou instrumento legal que venha substituí-lo" | Sim | https://www2.camara.leg.br/legin/fed/decret/2005/decreto-5440-4-maio-2005-536789-publicacaooriginal-27901-pe.html (Planalto: https://www.planalto.gov.br/ccivil_03/_ato2004-2006/2005/decreto/d5440.htm, não consegui abrir) | Média-alta |
| Leis 6.437/1977 e 8.078/1990 (sanções, CDC) | Federal | Citadas pelo Art. 46 da 888 como base das penalidades; não lidas | Sim | via 888 | Média |
| ABNT NBR ISO/IEC 17025 (edição 2017) | Técnica | Exigida pelos Arts. 20, 21 p.ú. e 22 §3 da 888. Não encontrei indício de nova edição | Sim | citada na 888; Cgcre/Inmetro (não lido) | Média |
| CNEN, Posição Regulatória 3.01/012:2020 | Federal | Citada nos §§3 e 4 do Art. 37 (radioatividade); texto **não lido**. Verificado em 22/10/2026: revogada desde 02/05/2024; a referência técnica atual é o Anexo L da Norma CNEN NN 3.01 (mesma metodologia: 1 mSv/ano e 0,1 mSv/ano), e a 888 ainda remete à PR revogada; citar a NN 3.01 (Anexo L) e manter a PR só como histórico | Não (revogada em 02/05/2024) | via 888 | Média |
| Resolução ANA nº 211/2024 (Norma de Referência nº 9: indicadores operacionais) | Federal (ANA) | 19/09/2024; DOU 23/09/2024; vigência 01/10/2024. Indicadores Nível I-02 (análises de coliformes totais no padrão) e I-03 (DBO na saída da ETE). Regulador começa a verificar 18 meses após a publicação (art. 27 p.ú.) | Sim | https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/211 | Média (fichas dos indicadores não lidas) |
| Resolução ANA nº 192/2024 (Norma de Referência nº 8: metas de universalização) | Federal (ANA) | 08/05/2024; meta 99% água e 90% esgoto até 31/12/2033 (fonte secundária) | Sim | https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/192 | Média |
| Resolução CONAMA nº 357/2005 (classes de corpos d'água, enquadramento) | Federal (CONAMA) | 17/03/2005. A 430/2011 revogou os arts. 24–37 e 39, 43, 44 e 46 (condições de lançamento) e o inciso XXXVIII do art. 2º. **Em 09/2026 aprovada nova alteração (ver abaixo)** | Sim | http://conama.mma.gov.br/?option=com_sisconama&task=arquivo.download&id=450 | Alta (data) / média (versão consolidada) |
| Resolução CONAMA nº 430/2011 (condições e padrões de lançamento de efluentes) | Federal (CONAMA) | 13/05/2011; DOU 16/05/2011. Consulta pública de 21/08 a 06/10/2025 (699 contribuições). **Aprovada, em 02/09/2026, a norma que a revoga** | Sim, até a nova norma ser publicada (**publicação NÃO CONFIRMADA**) | https://www.mprs.mp.br/media/areas/gapp/arquivos/atualizacao_intra/dou/res_conama_430.pdf ; https://www.ibama.gov.br/sophia/cnia/legislacao/CONAMA/RE0430-130511.PDF | Alta (texto) |
| **Nova resolução CONAMA de lançamento de efluentes** (altera a 357/2005 e revoga a 430/2011) | Federal (CONAMA) | Aprovada em 02/09/2026, na 151ª reunião ordinária (a plenária de 01/09 foi a 64ª extraordinária, sobre reúso, conjunta com o CNRH). Atualiza matéria orgânica e nutrientes; limites de nitrogênio amoniacal e fósforo total em efluente sanitário, por porte; águas pluviais urbanas. **Número, texto final, vigência e prazos de adequação: NÃO CONFIRMADO** (verificado em 22/10/2026: a página do processo no SINCONAMA lista quatro pedidos de vista, de MOVER, MCID, ANAMMA e Espírito Santo, e não registra o resultado, e o comunicado do MMA não indica em qual plenária cada norma foi aprovada (a data de 02/09/2026 e a 151ª reunião vêm da página do processo); a aprovação com emendas de plenário vem de fonte secundária; valores de nitrogênio e fósforo, portes, prazos e numeração não confirmados, e a minuta pré-emendas não deve ser usada para implementar; a Res. CONAMA 517/2026, de aquicultura, aprovada na mesma reunião, já teria saído em 14/10/2026, em fonte secundária, enquanto esta seguia pendente) | Não publicada (até onde apurei) | https://www.gov.br/mma/pt-br/noticias-defeso-eleitoral/conama-aprova-normas-sobre-reuso-de-agua-aquicultura-e-lancamento-de-efluentes | Média (notícia do MMA) |
| **Resolução conjunta CNRH/CONAMA de reúso direto não potável** de efluente sanitário tratado de ETE de sistemas públicos | Federal (CNRH + CONAMA) | Aprovada em 01/09/2026 (64ª extraordinária CONAMA / 61ª CNRH). Será publicada no DOU. **Número, parâmetros mínimos, prazos: NÃO CONFIRMADO** | Não publicada (até onde apurei) | idem MMA; https://saneamentobasico.com.br/abastecimento-de-agua/governo-aprova-resolucao-reuso-agua-brasil/ | Média |
| Resoluções CNRH nº 54/2005 (28/11/2005) e nº 121/2010 (16/12/2010) (reúso direto não potável; modalidade agrícola e florestal) | Federal (CNRH) | Citadas em fontes secundárias; sua sorte depois da resolução conjunta de 2026 é desconhecida | Presumida sim; **a verificar** | https://www.normasbrasil.com.br/norma/resolucao-54-2005_102111.html ; https://www.normasbrasil.com.br/norma/resolucao-121-2010_112680.html | Baixa-média |
| Resolução CONAMA nº 498/2020 (produção e aplicação de biossólido em solos) | Federal (CONAMA) | 19/08/2020; DOU nº 161, 21/08/2020, seção 1, p. 265–269. Art. 36: **revoga as Resoluções 375/2006 e 380/2006**; Art. 37: vigência na publicação | Sim | https://conama.mma.gov.br/?id=797&option=com_sisconama&task=arquivo.download | Alta |
| Resoluções CONAMA 375/2006 e 380/2006 (lodo de esgoto) | Federal (CONAMA) | Revogadas pela 498/2020 (Art. 36). A norma técnica CETESB P4.230 (2021) ainda diz "alterada"; prevalece o texto da 498 | Revogadas | idem 498 | Alta |
| Resolução CONAMA nº 503/2021 (reúso em fertirrigação de efluentes de indústrias de alimentos, bebidas, laticínios, frigoríficos, graxarias) | Federal (CONAMA) | 14/12/2021. **Não é lodo de ETE nem reúso de esgoto sanitário** | Sim | https://conama.mma.gov.br/?option=com_sisconama&task=arquivo.download&id=813 | Média (fonte secundária para o escopo) |
| Lei 9.433/1997, art. 12, III (outorga para lançamento de esgotos e resíduos líquidos, tratados ou não) | Federal | 08/01/1997; redação original do art. 12 | Sim | https://www2.camara.leg.br/legin/fed/lei/1997/lei-9433-8-janeiro-1997-374778-publicacaooriginal-1-pl.html | Média-alta (confirmado por síntese de busca) |
| Lei 15.190/2025 (Lei Geral do Licenciamento Ambiental), art. 10 §§2–3 | Federal | Sancionada 08/08/2025; Congresso derrubou 52 itens de veto (27/11/2025); vigência em 04/02/2026. Art. 10 §2 (fonte secundária): sistemas e estações de tratamento de água e esgoto **dispensados** de licenciamento até a universalização (Lei 11.445/2007); para o esgoto continua a **outorga** do lançamento. Há contestação no STF | Sim (com disputa) | https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/l15190.htm (não consegui abrir); https://www12.senado.leg.br/noticias/materias/2025/11/27/congresso-derruba-52-itens-de-veto-a-lei-geral-do-licenciamento-ambiental | Média (texto do art. 10 só em fonte secundária) |
| Resolução CONSEMA-RS nº 355/2017 (padrões de emissão de efluentes) | Estadual (RS) | 13/07/2017; revogou as Res. 128/2006, 286/2014 e 317/2016. Art. 17 II: efluente sanitário por **faixa de vazão** | Presumida sim; alterações posteriores **NÃO CONFIRMADO** | https://www.sema.rs.gov.br/upload/arquivos/201707/19110149-355-2017-criterios-e-padroes-de-emissao-de-efluentes-liquidos.pdf | Alta (texto) / média (vigência) |
| DN Conjunta COPAM/CERH-MG nº 1/2008, revogada integralmente pela nº 8/2022 (art. 52) | Estadual (MG) | 05/05/2008; DN 8 de 21/11/2022 "estabelece condições e padrões de lançamento". Revogação integral pelo art. 52 da DN 8/2022 (verificado em 22/10/2026 em reprodução do texto e em comunicados da FAEMG e do MPMG; texto oficial no SIAM, link da DN 8/2022); valores do art. 36 da DN 8/2022 (DBO, nitrogênio amoniacal, sólidos em suspensão) vistos só em trechos reproduzidos, a reconferir no SIAM | Não (revogada pela DN 8/2022) | https://www.siam.mg.gov.br/ (não lido) | Baixa |
| Resolução SS-65/2016 (SP; altera o art. 6º da Resolução SS-65/2005, Proagua; insere dados no SISAGUA) | Estadual (SP) | 02/08/2016; cadastro e plano até 10/01, controle do mês anterior até o dia 10 de cada mês (fonte secundária) | Presumida | https://prefeitura.sp.gov.br/web/saude/w/vigilancia_em_saude/228961 ; https://saude.campinas.sp.gov.br/saude/lista_legislacoes/legis_2016/E_R-SS-65_020816.pdf | Baixa-média |
| Res. ADASA/DF nº 59/2025 (adota a NR 9 da ANA no DF) | Agência (DF) | 12/11/2025 (fonte oficial localizada em 22/10/2026: SINJ-DF e comunicado da ADASA; a página do SINJ marca o texto com asterisco de republicação; fundamento: NR ANA 9/2024) | Sim (verificado em 22/10/2026) | https://www.adasa.df.gov.br/images/storage/legislacao/resolucoes_adasa/2025/SEI_187086634_Resolucao_59.pdf | Baixa-média |

## 3. Anexo A — O padrão de potabilidade (Portaria 888/2021, com a 2.472/2021)

Fonte: texto do DOU (republicação de 24/05/2021) e Portaria 2.472/2021. Unidades como na norma. Serve de gabarito para recompor o catálogo `quality_parameter`.

### A.1 Microbiológico, turbidez, desinfecção, radiológico

| Item | Valor | Onde |
|---|---|---|
| *E. coli* | Ausência em 100 mL: SAI; saída do tratamento SAA/SAC; sistema de distribuição e pontos de consumo | Anexo 1 |
| Coliformes totais, saída do tratamento | Ausência em 100 mL | Anexo 1 |
| Coliformes totais, distribuição, menos de 20.000 hab. | No mês, apenas uma amostra pode ser positiva | Anexo 1 |
| Coliformes totais, distribuição, 20.000 hab. ou mais | Ausência em 100 mL em 95% das amostras do mês | Anexo 1 |
| Recoletas (coliformes totais positivo) | Coletar em dias sucessivos até resultado satisfatório; mínimo 1 recoleta no ponto + 2 extras (montante e jusante); recoletas não entram no percentual mensal; negativo na recoleta não anula o positivo original; positivo em recoleta não é tolerado | Art. 27 §§1–5 |
| Turbidez, filtração rápida | 0,5 uT em 95% das amostras do mês, 1,0 uT no restante; 1 amostra a cada 2 h | Anexo 2 |
| Turbidez, filtração em membrana | 0,1 uT em 99%; a cada 2 h | Anexo 2 |
| Turbidez, filtração lenta | 1,0 uT em 95%, 2,0 uT no restante; diária | Anexo 2 |
| Turbidez, pós-desinfecção (subterrânea) | 1,0 uT em 95%, 5,0 uT no restante; semanal | Anexo 2 |
| Turbidez em toda a distribuição e pontos de consumo | 5,0 uT | Art. 28 §2; Anexo 11 |
| Cloro residual livre (mínimo) | 0,2 mg/L em toda a extensão (reservatório e rede) e pontos de consumo | Art. 32 |
| Cloro residual combinado (mínimo) | 2 mg/L | Art. 32 |
| Dióxido de cloro (mínimo) | 0,2 mg/L | Art. 32 |
| Carro-pipa, cloro residual livre | mínimo 0,5 mg/L | Art. 16 VI |
| Ozônio / UV | Ozônio: CT 0,34 mg·min/L a 15 °C (superficial) e 0,16 (subterrâneo contaminado por *E. coli*), com regra de ajuste por temperatura; UV: 2,1 mJ/cm² (superficial) e 1,5 mJ/cm² (subterrâneo). Após ozônio/UV, ainda é preciso residual conforme Art. 32 | Arts. 30–33 |
| Tempos de contato (cloro livre, cloraminas, dióxido de cloro; superficial e subterrâneo) | Tabelas em minutos por concentração (0,1 a 3,0 mg/L), temperatura (5 a 30/35 °C) e pH (6,0 a 9,0). Exemplos para teste: Anexo 3, C = 0,2, 5 °C, pH 6,0 → 138 min; Anexo 4, C = 0,1, 5 °C → 7.385 min; Anexo 5, C = 0,1, 5 °C → 108 min. **Importar a partir do DOU com conferência humana: o texto do PDF tem glitches de extração** | Anexos 3–8 |
| Radiológico (triagem) | Alfa total 0,5 Bq/L; beta total 1,0 Bq/L; subtrair K-40 do beta; recoletar; persistindo, seguir a Posição Regulatória CNEN 3.01/012:2020 (revogada em 02/05/2024; referência técnica atual: Anexo L da Norma CNEN NN 3.01); **nenhuma restrição ao abastecimento por motivo radiológico até a CNEN avaliar**; amostra **semestral** na rede (SAA) ou ponto de consumo (SAC) | Art. 37 |
| Cianobactérias (contagem, água bruta superficial) | ≤ 10.000 células/mL: trimestral; > 10.000: semanal. Clorofila-a mensal (≥ 10 µg/L: nova coleta para fitoplâncton; cianobactérias ≥ 10% do fitoplâncton: semanal na captação). > 20.000 células/mL: microcistinas, saxitoxinas e cilindrospermopsinas no mínimo semanais no ponto de captação. Cianotoxina acima do VMP na água bruta: análise semanal na saída do tratamento. Vedado algicida | Anexo 12; Art. 43 |
| *E. coli* na captação (superficial) | Mensal; média geométrica móvel de 12 meses ≥ 1.000 *E. coli*/100 mL → esporos de bactérias aeróbias semanais (água bruta e filtrado por filtro); remoção média < 2,5 log (99,7%) com ≥ 4 amostras/mês → Giardia e Cryptosporidium mensais por 12 meses; oocistos ≥ 1,0/L → turbidez ≤ 0,3 uT em 95% | Art. 29 |
| Subprodutos da desinfecção | Conformidade pela **média móvel dos últimos 12 meses, por ponto de amostragem** | Art. 40 |
| Nitrato e nitrito | Soma das razões (conc./VMP) ≤ 1, além dos VMPs individuais | Art. 39 |
| Parâmetros expressos por soma de analitos | Se algum analito quantificado: usar LD/2 ou LQ/2 para os < LD / < LQ; nenhum quantificado e algum < LQ: maior LQ; todos < LD: maior LD; soma dos LQ ≤ VMP da soma | Art. 41 |
| Limite de quantificação do método | LQ ≤ VMP; LD e LQ devem ser inseridos no SISAGUA | Art. 22 §§1–2 |
| pH | **Não há VMP** nos Anexos 9–11 | Anexo 11 |

### A.2 Anexo 9 — substâncias que representam risco à saúde

**Inorgânicas (mg/L):** Antimônio 7440-36-0 = 0,006; Arsênio 7440-38-2 = 0,01; Bário 7440-39-3 = 0,7; Cádmio 7440-43-9 = 0,003; Chumbo 7439-92-1 = 0,01; Cobre 7440-50-8 = 2; Cromo 7440-47-3 = 0,05; Fluoreto 7782-41-4 = 1,5; Mercúrio total 7439-97-6 = 0,001; Níquel 7440-02-0 = 0,07; Nitrato (como N) 14797-55-8 = 10; Nitrito (como N) 14797-65-0 = 1; Selênio 7782-49-2 = 0,04; Urânio 7440-61-1 = 0,03.

**Orgânicas (µg/L):** 1,2 Dicloroetano 107-06-2 = 5; Acrilamida 79-06-1 = 0,5; Benzeno 71-43-2 = 5; Benzo[a]pireno 50-32-8 = 0,4; Cloreto de vinila 75-01-4 = 0,5; Di(2-etilhexil) ftalato 117-81-7 = 8; Diclorometano 75-09-2 = 20; Dioxano 123-91-1 = 48; Epicloridrina 106-89-8 = 0,4; Etilbenzeno 100-41-4 = 300; Pentaclorofenol 87-86-5 = 9; Tetracloreto de carbono 56-23-5 = 4; Tetracloroeteno 127-18-4 = 40; Tolueno 108-88-3 = 30; Tricloroeteno 79-01-6 = 4; Xilenos 1330-20-7 = 500.

**Agrotóxicos e metabólitos (µg/L; 40 entradas, várias são somas de analitos, Art. 41):**

| Parâmetro (CAS) | VMP | Parâmetro (CAS) | VMP |
|---|---|---|---|
| 2,4 D (94-75-7) | 30 | Glifosato + AMPA (1071-83-6; 1066-51-9) | 500 |
| Alacloro (15972-60-8) | 20 | Hidroxi-atrazina (2163-68-0) | 120 |
| Aldicarbe + sulfona + sulfóxido (116-06-3; 1646-88-4; 1646-87-3) | 10 | Lindano (58-89-9) | 2 |
| Aldrin + Dieldrin (309-00-2; 60-57-1) | 0,03 | Malationa (121-75-5) | 60 |
| Ametrina (834-12-8) | 60 | Mancozebe + ETU (8018-01-7; 96-45-7) | 8 |
| Atrazina + S-clorotriazinas (1912-24-9; 6190-65-4; 1007-28-9; 3397-62-4) | 2,0 | Metamidofós + Acefato (10265-92-6; 30560-19-1) | 7 |
| Carbendazim (10605-21-7) | 120 | Metolacloro (51218-45-2) | 10 |
| Carbofurano (1563-66-2) | 7 | Metribuzim (21087-64-9) | 25 |
| Ciproconazol (94361-06-5) | 30 | Molinato (2212-67-1) | 6 |
| Clordano (5103-74-2) | 0,2 | Paraquate (4685-14-7) | 13 |
| Clorotalonil (1897-45-6) | 45 | Picloram (1918-02-1) | 60 |
| Clorpirifós + oxon (2921-88-2; 5598-15-2) | 30 | Profenofós (41198-08-7) | 0,3 |
| DDT + DDD + DDE (50-29-3; 72-54-8; 72-55-9) | 1 | Propargito (2312-35-8) | 30 |
| Difenoconazol (119446-68-3) | 30 | Protioconazol + destio (178928-70-6; 120983-64-4) | 3 |
| Dimetoato + ometoato (60-51-5; 1113-02-6) | 1,2 | Simazina (122-34-9) | 2 |
| Diuron (330-54-1) | 20 | Tebuconazol (107534-96-3) | 180 |
| Epoxiconazol (135319-73-2) | 60 | Terbufós (13071-79-9) | 1,2 |
| Fipronil (120068-37-3) | 1,2 | Tiametoxam (153719-23-4) | 36 |
| Flutriafol (76674-21-0) | 30 | Tiodicarbe (59669-26-0) | 90 |
| Tiram (137-26-8) | 6 | Trifluralina (1582-09-8) | 20 |

(No DOU o CAS 96-45-7 aparece rotulado "Ampa" na linha do mancozebe; é erro de grafia da norma, o composto é a ETU. Conferir antes de gravar.)

**Subprodutos da desinfecção (mg/L):** 2,4,6-Triclorofenol 88-06-2 = 0,2; 2,4-Diclorofenol 120-83-2 = 0,2; Ácidos haloacéticos total = 0,08 (soma de 9 ácidos, CAS na nota 5 do anexo); Bromato 15541-45-4 = 0,01; Cloraminas total = 4; Clorato 7775-09-9 = 0,7; Clorito 7758-19-2 = 0,7; Cloro residual livre 7782-50-5 = 5; N-nitrosodimetilamina 62-75-9 = 0,0001 (obrigatória só onde há cloraminação); Trihalometanos total = 0,1 (soma de 4 compostos, nota 7). Análise exigida conforme o desinfetante e o oxidante usados.

### A.3 Anexo 10 (cianotoxinas) e Anexo 11 (organoléptico)

Cianotoxinas, µg/L, VMP da concentração total (intra + extracelular, Art. 36 §2): cilindrospermopsinas 1,0; microcistinas 1,0 (equivalente MCYST-LR, soma de variantes); saxitoxinas 3,0 (equivalente STX).

Organoléptico: Alumínio 0,2 mg/L; Amônia (como N) 1,2 mg/L; Cloreto 250 mg/L; **Cor aparente 15 uH**; 1,2-diclorobenzeno 0,001 mg/L; 1,4-diclorobenzeno 0,0003 mg/L; **Dureza total 300 mg/L**; Ferro 0,3 mg/L; Gosto e odor (intensidade) 6; Manganês 0,1 mg/L; Monoclorobenzeno 0,02 mg/L; Sódio 200 mg/L; **Sólidos dissolvidos totais 500 mg/L**; Sulfato 250 mg/L; Sulfeto de hidrogênio 0,05 mg/L; Turbidez 5 uT; Zinco 5 mg/L. Ferro e manganês podem exceder o VMP até 2,4 e 0,4 mg/L se complexados com produtos de baixo risco (Art. 38 §1).

### A.4 Amostragem mínima (Anexos 13, 14, 15 e Art. 44)

**Anexo 13** (físico-química; redação da Portaria 2.472/2021). Colunas de distribuição por população: < 50.000, 50.000 a 250.000, > 250.000. A leitura do layout veio do texto do PDF; conferir no DOU.

| Parâmetro | Manancial | Saída do tratamento (nº / frequência) | Distribuição |
|---|---|---|---|
| Turbidez, residual de desinfetante, cor aparente | Superficial | 1 / a cada 2 h | conforme §3º do Art. 44 |
| idem | Subterrâneo | 1 / semanal | idem |
| pH e fluoreto (fluoreto só onde há fluoretação ou desfluoretação; senão segue "demais parâmetros") | Superficial | 1 / a cada 2 h | dispensada |
| idem | Subterrâneo | 1 / semanal | dispensada |
| Gosto e odor | Superficial / Subterrâneo | 1 / trimestral; 1 / semestral | dispensada |
| Cianotoxinas | Superficial | 1 / semanal quando cianobactérias > 20.000 células/mL | dispensada |
| Produtos secundários da desinfecção | Superficial | dispensada | 1, 4 e 8 amostras (por faixa) / bimestral |
| idem | Subterrâneo | (saída: ver nota do anexo) | 1, 2 e 3 amostras / anual, semestral, semestral |
| Acrilamida e epicloridrina (só quem usa polímero com a substância) | Ambos | 1 / mensal | 1 por faixa / mensal (dispensa se não detectado na saída, nota 6) |
| Cloreto de vinila | Ambos | 1 / semestral | 1 por faixa / semestral (obrigatório na rede mesmo sem detecção na saída) |
| Demais parâmetros (inclui agrotóxicos, Art. 44 §4) | Ambos | 1 / semestral | 1 por faixa / trimestral; se detectado na saída, trimestral na saída e na distribuição |

**Anexo 14** (bacteriológico, nº mínimo de amostras **mensais**, coliformes totais; superficial: saída do tratamento = 2 amostras semanais por unidade de tratamento; subterrâneo = semanal). Distribuição, por população abastecida:

| População | Amostras mensais na distribuição |
|---|---|
| < 5.000 | 5 |
| 5.000 a 10.000 | 10 |
| 10.000 a 50.000 | 1 para cada 1.000 hab. |
| 50.000 a 80.000 | 25 + 1 para cada 2.000 hab. |
| 80.000 a 130.000 | 1 + 1 para cada 1.250 hab. |
| 130.000 a 250.000 | 40 + 1 para cada 2.000 hab. |
| 250.000 a 340.000 | 115 + 1 para cada 5.000 hab. |
| 340.000 a 400.000 | 47 + 1 para cada 2.500 hab. |
| 400.000 a 600.000 | 127 + 1 para cada 5.000 hab. |
| 600.000 a 1.140.000 (somente na página do Ministério; **não vi no texto do PDF do DOU**) | 187 + 1 para cada 10.000 hab. |
| > 1.140.000 (idem) | 244 + 1 para cada 20.000 hab., máximo 400 |

Teste de consistência que fiz: as fórmulas são contínuas nas fronteiras (por exemplo, 65 amostras em 80.000 hab., 105 em 130.000, 165 em 250.000, 207 em 400.000, 247 em 600.000, 301 em 1.140.000), o que dá confiança de que a extração está certa, inclusive nas duas últimas faixas. Para *E. coli* a tabela traz "-" (sem número próprio); a leitura de que usa as mesmas amostras é **NÃO CONFIRMADA**. Recoletas não entram no mínimo (Art. 44 §2).

**Anexo 15** (SAC): número de amostras no ponto de consumo = 1 para cada 1.000 hab. Cor aparente, pH, coliformes totais e *E. coli*: semanal (superficial) / mensal (subterrâneo). Turbidez: semanal (superficial); subterrâneo semanal na saída e mensal no consumo. Residual de desinfetante: diário. Demais parâmetros: semestral, só na saída.

**Art. 42:** água bruta, ao menos 1 amostra semestral por captação. Superficial: DQO, DBO, OD, turbidez, cor verdadeira, pH, fósforo total, nitrogênio amoniacal total e inorgânicos, orgânicos e agrotóxicos. Subterrâneo: turbidez, cor verdadeira, pH, fósforo total, nitrogênio amoniacal total, condutividade elétrica e inorgânicos, orgânicos e agrotóxicos. **Não há VMP para esses analitos de água bruta.**

**Art. 44 (com a redação da 2.472/2021):** plano elaborado **anualmente** e submetido à autoridade municipal; coletas distribuídas uniformemente no ano; pontos representativos e **estratégicos** (grande circulação de pessoas, hospitais/creches/asilos/presídios, trechos vulneráveis como pontas de rede e locais com intermitência, locais com notificação de agravos de veiculação hídrica); incluir aglomerados subnormais e grupos vulneráveis; em **toda amostra bacteriológica** medir cor aparente, turbidez e residual de desinfetante (o pH deixou de ser exigido por essa regra); agrotóxicos conforme uso na bacia e sazonalidade.

## 4. Anexo B — Validação do seed `quality_parameter` (V2_1, 28 linhas)

Resultado: **11 coincidem, 1 parcial, 16 divergem ou não têm base.** Todas as linhas estão marcadas `portaria_ms_888 = true`, inclusive as que não existem na Portaria. Os limiares "alerta" e "crítico" são inventados do produto (não vêm da norma): devem ser guardados à parte do VMP legal.

| Código no seed | Valor no seed | O que a Portaria 888 (com a 2.472) diz | Veredito |
|---|---|---|---|
| ECOLI | VMP 0 UFC/100 mL; crítico 0–1; freq. DIARIO | Anexo 1: ausência em 100 mL (saída do tratamento, distribuição, consumo, SAI). Sem nº mínimo próprio no Anexo 14 | OK no limite; unidade deve ser presença/ausência em 100 mL; frequência "DIARIO" sem base |
| COLIFORMES_TOTAIS | VMP 0 | Saída: ausência; distribuição: < 20.000 hab. uma positiva/mês, ≥ 20.000 hab. ausência em 95% | PARCIAL: precisa de regra, não de escalar |
| CLORIFORMES_FECAIS | 0 | Coliformes fecais/termotolerantes não constam; o indicador fecal é só *E. coli*. Código com erro de digitação | Sem base |
| ESTREPTOCOCOS | 0 | Não consta | Sem base |
| PH | VMP 7,0; alerta 6–9; crítico 5,5–9,5 | Nenhum VMP de pH; pH entra como variável das tabelas CT (6,0–9,0) e é monitorado (Anexo 13) | Sem base como VMP |
| TURBIDEZ | VMP 1,0 NTU; alerta 0–5; crítico 0–10 | Unidade é **uT**. Regra por tipo de filtração (Anexo 2); 5,0 uT na distribuição; frequência 2 h / diária / semanal | Divergente (um escalar não representa) |
| COR_VERDADEIRA | VMP 15 uH | O VMP é de cor **aparente** 15 uH (Anexo 11); cor verdadeira só na água bruta (Art. 42), sem VMP | Divergente (parâmetro trocado) |
| CONDUTIVIDADE | alerta 1000; crítico 2000 µS/cm | Sem VMP; só monitoramento de água bruta subterrânea | Sem base |
| SOLIDOS_TOTAIS | alerta 1000 mg/L | O parâmetro é **sólidos dissolvidos totais, 500 mg/L** | Divergente |
| CLORO_LIVRE | VMP 0,2; alerta 0,2–2,0; crítico 0,1–5,0 | **Mínimo** 0,2 mg/L em toda a distribuição (Art. 32); **máximo 5 mg/L** (Anexo 9); carro-pipa mín. 0,5 | Semântica errada (0,2 é piso, não VMP) |
| CLORO_TOTAL | VMP 0,5; alerta 0,5–3,0 | Mínimo de **cloro residual combinado 2 mg/L** (Art. 32); cloraminas totais VMP 4 mg/L | Divergente |
| DIOXIDO_CLORO | VMP 0,2; alerta 0,1–1,0; crítico 0,05–1,5 | Mínimo 0,2 mg/L; sem máximo próprio; clorito e clorato 0,7 mg/L (faltam no seed) | PARCIAL |
| TRIHALOMETANOS | 100 µg/L | 0,1 mg/L (= 100 µg/L), **média móvel de 12 meses por ponto** (Art. 40) | Valor OK; falta a regra |
| ACIDOS_HALOACETICOS | 60 µg/L | 0,08 mg/L = **80 µg/L** | **Divergente (60 contra 80)** |
| FLUORETO | VMP 0,7; alerta 0,5–1,5; crítico 0,3–2,0; DIARIO | **VMP 1,5 mg/L**; 0,7 é valor recomendado de fluoretação (Anexo XXI da PRC 5/2017, conferido em 22/10/2026); frequência a cada 2 h se há fluoretação | Divergente |
| NITRATO | 10 mg/L | 10 mg/L (como N); regra da soma (Art. 39) | OK + regra |
| NITRITO | 1 mg/L | 1 mg/L (como N) | OK |
| FERRO | 0,3 mg/L | 0,3 (organoléptico); exceção até 2,4 se complexado (Art. 38 §1) | OK; categoria deveria ser ORGANOLEPTIC |
| MANGANES | 0,1 mg/L | 0,1; exceção até 0,4 | OK |
| ALUMINIO | 0,2 mg/L | 0,2 (organoléptico) | OK |
| ARSENIO | 10 µg/L | 0,01 mg/L | OK (unidade) |
| CHUMBO | 10 µg/L | 0,01 mg/L | OK |
| CADMIO | 3 µg/L | 0,003 mg/L | OK |
| MERCURIO | 1 µg/L | Mercúrio total 0,001 mg/L | OK |
| CARBONO_ORGANICO_TOTAL | alerta 5; crítico 10 mg/L | Não consta | Sem base |
| DEMANDA_QUIMICA_OXIGENIO | alerta 100; crítico 200 | Só monitoramento de água bruta (Art. 42 §1), sem VMP | Sem base como VMP |
| DEMANDA_BIOLOGICA_OXIGENIO | alerta 30; crítico 60 | Idem. Os 30 mg/L não são o limite de ETE da CONAMA 430 (120 mg/L ou remoção ≥ 60%) | Sem base |
| AGROTOXICOS_TOTAIS | 0,5 µg/L | Não existe VMP de agrotóxicos totais; são 40 entradas individuais com VMPs próprios (Anexo 9) | Sem base |

**Faltam no catálogo** (84 das 100 linhas dos Anexos 9–11 sem correspondente): 7 inorgânicos (antimônio, bário, cobre, cromo, níquel, selênio, urânio), as 16 orgânicas, os 40 agrotóxicos, bromato, clorato, clorito, cloraminas, fenóis clorados, NDMA, as 3 cianotoxinas, os organolépticos (amônia, cloreto, cor aparente, clorobenzenos, dureza, gosto e odor, sódio, STD, sulfato, sulfeto, zinco), alfa/beta total, cianobactérias (células/mL), clorofila-a, esporos de bactérias aeróbias, Giardia e Cryptosporidium. Também faltam campos de dado: CAS, unidade canônica, tipo de regra (máximo, mínimo, ausência, percentil, média móvel, soma), aplicabilidade (SAA/SAC/SAI, superficial/subterrâneo, ponto), referência (anexo/artigo) e versão da norma com vigência.

**Observação de modelagem:** o catálogo é gravado **por tenant** no schema do tenant (a coluna `portaria_ms_888` amarra o nome da coluna a uma versão da norma). Mudança de norma obriga migration em todos os tenants. Recomendo catálogo normativo global versionado (schema `public`, `norm_version` com `valid_from`/`valid_to`), com sobrescrita por tenant apenas para limiares operacionais.

## 5. (b) Requisitos acionáveis para o software

Situação conferida em: `V2_1__create_quality_schema.sql`, `LabService.java`, `ScadaIntegrationService.java` e entidades de `intelligence/sigsan-quality`. O módulo não tem pasta `src/test`; não há tela de qualidade no frontend (só menções em `Layout.tsx`/`api.ts`). `commercial/*` e `operations/*` não têm código.

### 5.1 Potabilidade, plano de amostragem e conformidade

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| QA-01 | Catálogo de parâmetros e valores exatos da norma, versionado | 888 Anexos 1, 2, 9, 10, 11; Art. 37 | quality | **NÃO ATENDE** (seed `V2_1`, seção 4) | 16 linhas erradas, 84 linhas ausentes; sem CAS, unidade canônica, tipo de regra, versão | Catálogo normativo global versionado; carga a partir do DOU com teste de conferência por pessoa qualificada | P0 |
| QA-02 | Motor de conformidade por tipo de regra: coliformes totais por população; turbidez em percentil mensal por filtração; média móvel de 12 meses por ponto (THM, AHA, subprodutos); soma nitrato+nitrito; LD/2 e LQ/2 para somas; LQ ≤ VMP; recoletas fora do percentual; exceção ferro/manganês | 888 Anexos 1–2; Arts. 22 §1, 27, 28, 38–41 | quality | **NÃO ATENDE**. `LabService.recordAnalysisResult` grava o `conformity` e o `vmp` que o chamador manda | Nenhuma comparação com limite; `INCONCLUSIVE` existe no enum e nunca é calculado | `ConformityEvaluator` puro, sem I/O, testado por vetores da norma (como o `WaterBalanceCalculator`) | P0 |
| QA-03 | Plano de amostragem **anual** por sistema/solução, com nº mínimo e frequência por população, manancial e ponto, coletas distribuídas no ano, pontos estratégicos e vulneráveis; recoletas fora do mínimo | 888 Arts. 14 IV, 44; Anexos 13 (2.472), 14, 15 | quality | **NÃO ATENDE**. `sampling_point.sampling_frequency` e `quality_parameter.sampling_frequency` são texto livre ("DIARIO", "TRIMESTRAL") | Não há entidade de plano, calendário, cálculo de mínimos nem controle de cumprimento (planejado × coletado × analisado) | Entidade `SamplingPlan` anual + gerador de calendário a partir dos anexos + painel de cumprimento; submissão e parecer da autoridade (30 dias, aprovação tácita) como estados | P0 |
| QA-04 | Dados de contexto do sistema: classificação SAA/SAC/SAI/carro-pipa, população abastecida, tipo de manancial (superficial/subterrâneo), tipo de tratamento e filtração, desinfetante, fluoretação, unidades de tratamento | 888 Anexos 2, 13, 14, 15; Art. 5 | quality + registry | **NÃO ATENDE**. `SamplingPoint` tem `system_id` e `sector_id` sem FK e sem nenhum desses campos (nada de "população" ou "manancial" no módulo) | Sem esses dados, nenhum mínimo de amostras pode ser calculado | Entidade `WaterSystem` (e `TreatmentUnit`, `Intake`) ligada à hierarquia territorial do `commercial/sigsan-registry` | P0 |
| QA-05 | Pontos obrigatórios: saída de cada filtro (ou mistura, se justificado), saída do tratamento, reservatórios, rede, captações; e marcação de ponto estratégico/vulnerável | 888 Art. 14 XXIII; Art. 44 §1 | quality | **PARCIAL**. `PointType` tem `ETA_OUTLET`, `RESERVOIR`, `DISTRIBUTION_NETWORK`, `RAW_WATER`, `WELL`, `SPRING` | Falta saída de filtro, captação como ponto próprio, carro-pipa, flags de ponto estratégico/vulnerável/aglomerado subnormal | Ampliar o tipo e adicionar atributos do Art. 44 §1 | P1 |
| QA-06 | Em toda amostra bacteriológica: cor aparente, turbidez e residual de desinfetante (o pH deixou de ser exigido pela 2.472) | 888 Art. 44 §3 (redação 2.472/2021) | quality | **PARCIAL**. `QualitySample` tem `turbidity_ntu`, `chlorine_residual_mg_l`, `ph_field`; não tem cor aparente | Unidade NTU em vez de uT; "cloro residual" sem dizer se livre, combinado ou ClO2; nenhuma validação de obrigatoriedade | Campos tipados por parâmetro, com validação ao fechar a coleta bacteriológica | P1 |
| QA-07 | Desinfecção: residual mínimo (0,2 livre / 2 combinado / 0,2 ClO2) em toda a distribuição, máximo de cloro livre 5 mg/L, tempos de contato (CT) por pH e temperatura, ozônio e UV | 888 Arts. 30–35; Anexos 3–8 | quality + SCADA | **NÃO ATENDE**. `ScadaTag` liga tag a ponto e parâmetro, mas o alarme é `log.warn` e não há tabela CT | Sem regra mínima/máxima, sem verificação de CT | Regras mínimas/máximas por tipo de residual; tabelas CT como dados importados; alarme persistido | P1 |
| QA-08 | Controle de turbidez por filtro a cada 2 h (rápida/membrana), diária (lenta) e semanal (subterrânea), com verificação mensal do percentil | 888 Art. 28 §3; Anexo 2 | quality + SCADA | **PARCIAL**. A ingestão SCADA existe (`ScadaDataPoint`, `scada_tag`) | Sem cálculo de cobertura de frequência nem do percentil mensal; `scada_data_point` ainda é tabela comum (candidata a hypertable) | Job mensal que fecha o percentil por filtro a partir da série; relatório de cobertura | P1 |
| QA-09 | Cianobactérias e cianotoxinas: contagem por frequência (trimestral/semanal), clorofila-a mensal, gatilhos de 10%/20.000 células/mL, cianotoxinas semanais na captação e na saída, vedação de algicida | 888 Art. 43; Anexos 10, 12 | quality | **NÃO ATENDE**. Parâmetros ausentes do catálogo | Sem máquina de estados de frequência por resultado anterior (a norma usa "o resultado da última amostragem") | Regra de frequência dinâmica por captação; alerta ao passar de 20.000 células/mL | P1 |
| QA-10 | *E. coli* na captação (mensal), média geométrica móvel de 12 meses, esporos de bactérias aeróbias, Giardia e Cryptosporidium | 888 Art. 29; Arts. 54 e 56 | quality | **NÃO ATENDE** | O prazo de adequação (12 meses) **já venceu** | Cálculo de média geométrica móvel e gatilhos; coleta programada automática no plano | P1 |
| QA-11 | Água bruta: análise semestral por captação (lista do Art. 42) | 888 Art. 42 | quality | **NÃO ATENDE** (DQO/DBO no seed, como se tivessem VMP) | Sem lista por tipo de manancial | Incluir no gerador do plano; sem VMP | P1 |
| QA-12 | Radiológico: triagem alfa 0,5 e beta 1,0 Bq/L, subtração de K-40, recoleta, fluxo CNEN, coleta semestral na rede, sem restrição de abastecimento até avaliação | 888 Art. 37 | quality | **NÃO ATENDE** (a categoria `RADIOLOGICAL` existe no enum, nenhum parâmetro) | Fluxo de decisão inexistente | Parâmetros e regra de decisão; registrar encaminhamento à CNEN | P1 |
| QA-13 | Fluoretação: monitoramento a cada 2 h na saída (superficial) e valores recomendados do Anexo XXI da PRC 5/2017, nunca acima de 1,5 mg/L | 888 Art. 36 §1; Anexo 13; Anexo XXI PRC 5 | quality | **NÃO ATENDE** (seed põe 0,7 como VMP) | Anexo XXI conferido em 22/10/2026 (ver seção 2) | Ler e implementar o Anexo XXI (valores por temperatura média) | P2 |
| QA-14 | Carro-pipa: cadastro, autorização, residual ≥ 0,5 mg/L, documento de abastecimento | 888 Arts. 14 XIX–XX, 16 | quality / operations | **NÃO ATENDE** (sem entidade) | Só vale se o tenant opera ou abastece carro-pipa | Módulo opcional por tenant | P2 |
| QA-15 | Notificação **prévia** de operações programadas que causem pressão negativa ou intermitência (autoridade de saúde, regulador e população); aviso com 72 h de antecedência para manobras com pressão abaixo da atmosférica | 888 Art. 14 XXI; Decreto 5.440 Anexo art. 15 | operations (OS) | **NÃO ATENDE** (módulo inexistente) | Ligar a OS de manobra à notificação | Gatilho a partir da OS programada | P1 |

### 5.2 Comunicação, SISAGUA, PSA e informação ao consumidor

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| SG-01 | Cadastro das formas de abastecimento e relatórios de controle enviados à autoridade (modelo da autoridade) ou registrados direto no SISAGUA se acordado; fornecer dados quando solicitado | 888 Arts. 14 XI–XIII; 6 II; 13 III | quality / compliance | **NÃO ATENDE**. Não há exportação, nem referência a SISAGUA em nenhum arquivo do repositório | Sem exportador, sem layout, sem fila de envio | Gerador de relatório mensal por tenant e por autoridade; importação em planilha do SISAGUA. **Layout oficial vigente: NÃO CONFIRMADO** (o manual de entrada de dados que achei é de 01/2016, v1.0) | P0 |
| SG-02 | LD e LQ de cada resultado, LQ ≤ VMP, e inserção no SISAGUA | 888 Art. 22 §§1–2 | quality / lab | **PARCIAL**. `QualityAnalysisResult` tem `detection_limit` e `quantification_limit` | Sem validação LQ ≤ VMP; sem tratamento do "< LQ" para cálculo | Validar ao gravar; guardar o flag "< LD" / "< LQ" | P0 |
| SG-03 | Comunicar **imediatamente** à autoridade de saúde municipal e informar a população, em linguagem clara, situações de risco por anomalia operacional ou não conformidade; informar medidas corretivas quando o padrão bacteriológico é violado; cianotoxina na saída do tratamento: comunicação imediata à autoridade, clínicas de hemodiálise e indústrias de injetáveis | 888 Arts. 14 XXII; 27 §6; 43 §9; 48 | quality / notifications | **NÃO ATENDE**. `LabService.notifyNonConformance` só faz `log.warn("NON-CONFORMANCE...")` com o comentário "Send notification"; `triggerAlarm` do SCADA idem | Sem destinatários, sem registro do envio, sem prova de data/hora | Entidade `ComplianceNotice` com destinatário, canal, texto, hora, confirmação de entrega e plano de ação; **sem prazo numérico na norma ("imediatamente"; 24 h não consta)**: o tenant define SLA interno | P0 |
| SG-04 | Plano de ação e medidas corretivas feitos em conjunto com a autoridade; ampliar amostras, frequência e parâmetros quando exigido | 888 Arts. 13 XI, 48 | quality | **NÃO ATENDE** | Sem workflow | Estado do plano de ação ligado ao `ComplianceNotice`; recálculo do plano de amostragem sob "regime ampliado" | P1 |
| SG-05 | Informação ao consumidor: **conta mensal** (onde obter dados, cuidados em risco, resumo mensal dos parâmetros básicos, problemas do manancial) e **relatório anual** (até 15 de março, enviado a cada ligação predial, com conteúdo do Anexo art. 5 II; parâmetro fora do padrão marcado "FORA DOS PADRÕES DE POTABILIDADE") | Decreto 5.440 art. 3º e Anexo arts. 5, 7, 8; 888 Art. 14 XVII–XVIII | quality + billing | **NÃO ATENDE**. `commercial/sigsan-billing` e `sigsan-portal` não têm código | Sem relatório anual (PDF/portal), sem bloco na fatura. "Parâmetros básicos": lista **NÃO LIDA** no decreto | Gerador do relatório anual por sistema + inserção na fatura quando existir o billing; prazo 15/03 como regra configurável | P0 (obrigação anual; depende do billing) |
| SG-06 | Canal de reclamações, resposta ao consumidor em até 30 dias, e registro público de qualidade e de limpeza de reservatórios | Decreto 5.440 Anexo arts. 16–17; 888 Art. 14 XVII | quality / portal | **PARCIAL**. `sample_type = COMPLAINT` existe | Sem fluxo de atendimento nem prazo de 30 dias; limpeza de reservatório não é registrada (módulo `operations` inexistente) | Amarrar o RA/protocolo (fase 7 do consolidado) à amostra e ao prazo | P1 |
| SG-07 | **Plano de Segurança da Água (PSA)**: autoridade pode exigir (Art. 49); necessário para reduzir parâmetros/frequência (Art. 50: 2 anos de histórico + PSA; parecer em 120 dias; autorização válida no máximo 2 anos; renovação com revisão do PSA, parecer em 60 dias; sem PSA implementado volta o plano padrão integral) | 888 Arts. 49–50 | quality | **NÃO ATENDE** | Sem matriz de perigos/riscos, sem medidas de controle, sem validade da autorização | Módulo PSA (metodologia OMS ou diretriz do MS; publicação do MS "Plano de Segurança da Água, um olhar do SUS": edição/ano **NÃO CONFIRMADO**, a página indica 09/04/2026) | P1 |
| SG-08 | Avaliação sistemática de risco do SAA (bacia, histórico, características físicas, O&M, qualidade distribuída) | 888 Art. 14 X | quality | **NÃO ATENDE** | Sem entidade | Anexar ao PSA | P2 |
| SG-09 | Comunicar a órgãos ambientais/recursos hídricos manancial fora da classe, e à autoridade de saúde alterações que revelem risco | 888 Art. 14 XIV–XV | quality | **NÃO ATENDE** | Sem classe de enquadramento do manancial | Campo de classe no ponto de captação + regra | P2 |
| SG-10 | Rastreio de produtos químicos (LARS/CBRS, NBR 15784:2023, que não cobre materiais em contato) e materiais em contato (ANSI/NSF 61, hoje NSF/ANSI/CAN 61, edição 2025, ou OCP/Inmetro); ART do responsável técnico; documentos para autorização inicial (ART, regularidade ambiental, laudo, plano) | 888 Arts. 14 VII–IX, 15, 23 | assets / compliance | **NÃO ATENDE** | Sem repositório de documentos regulatórios | Gestão documental com validade e alerta | P2 |

### 5.3 Laboratório (myLIMS)

A Portaria exige só o essencial (Arts. 20, 21, 22). O restante deriva da ABNT NBR ISO/IEC 17025:2017; **os números de cláusula que cito (6.4 equipamento, 6.5 rastreabilidade metrológica, 7.6 incerteza de medição, 7.7 garantia da validade dos resultados, 7.8 relato de resultados) são de memória e NÃO CONFIRMADOS nesta sessão**: conferir na norma. Itens que são boa prática da 17025 e não exigência literal da Portaria estão assim marcados.

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| LB-01 | Registro do laboratório (próprio, conveniado ou contratado): boas práticas, biossegurança e sistema de gestão conforme NBR ISO/IEC 17025; para vigilância, laboratórios de saúde pública | 888 Arts. 20–21 | lab | **NÃO ATENDE**. `quality_sample.lab_id` é UUID sem tabela de laboratório | Sem entidade `Lab`: acreditação (Cgcre/Inmetro), escopo, validade, documento | Entidade `Lab` com escopo acreditado por parâmetro e método; bloqueio de resultado fora do escopo, com alerta | P0 |
| LB-02 | Métodos conforme Standard Methods, USEPA, ISO ou OMS; método fora da lista deve ser validado e registrado conforme a 17025; LQ ≤ VMP | 888 Art. 22 e §§1, 3 | lab | **PARCIAL**. `LabAnalysisMethod` tem `standard_method`, `method_version`, LD, LQ, precisão, recuperação, incerteza | Sem registro de validação do método; sem checagem LQ ≤ VMP; o seed usa "SM 9223", "SM 4500-Cl" etc. **sem validação** | Campo de referência normativa + dossiê de validação; checagem automática | P1 |
| LB-03 | Calibração e rastreabilidade metrológica dos instrumentos; bloqueio de uso com calibração vencida (boa prática 17025) | ISO/IEC 17025 (cláusulas NÃO CONFIRMADAS) | lab | **PARCIAL**. `LabInstrument` tem `calibration_due`, `calibration_frequency_days`, `certification` (JSONB) | `updateInstrumentCalibration` **ignora o parâmetro `nextDue`**; não guarda certificado, padrão usado, laboratório calibrador, incerteza da calibração; resultado não é barrado com calibração vencida | Registro de calibração como entidade (histórico), vínculo do resultado ao instrumento e bloqueio | P1 |
| LB-04 | Controle de qualidade analítico: branco, duplicata, adição, MRC, ensaio de proficiência, cartas de controle e regras de decisão | ISO/IEC 17025 (7.7); CONAMA 430 art. 26 §1 (sistema de controle de qualidade analítica, para efluente) | lab | **PARCIAL**. `LabQcSample` e `QcType` (BLANK, STANDARD, SPIKE, DUPLICATE, CRM, PT_SAMPLE, BLIND) existem e o status é calculado pelos limites | `checkQcTrends` são só **comentários** das regras de Westgard (nada implementado) e usa `getMethod()` da entidade; nenhum resultado de rotina é bloqueado por QC reprovado | Implementar as regras e ligar o QC à liberação de lote | P1 |
| LB-05 | Incerteza de medição e regra de decisão de conformidade quando o resultado está perto do limite | ISO/IEC 17025 (7.6; 7.8) | lab | **PARCIAL**. Campo `uncertainty DECIMAL(8,4)` no resultado | Sem unidade, fator de abrangência k, nem regra de decisão; `INCONCLUSIVE` nunca é atribuído | Guardar incerteza expandida e k; regra de decisão configurável | P1 |
| LB-06 | Integridade e rastreabilidade do dado: reanálise preserva histórico, aprovação por segundo responsável, trilha de auditoria imutável | boa prática 17025; Art. 20 | lab | **PARCIAL**. `audit_log` é somente-inserção; `LabService.auditLog` grava; statuses `VALIDATED`/`PUBLISHED` existem | `recordAnalysisResult` faz **upsert** (sobrescreve o resultado; só a auditoria guarda o anterior); a migration não tem `UNIQUE (tenant_id, sample_id, parameter_id)` mas o repositório usa `Optional`; nada obriga validação por pessoa diferente do analista | Versão do resultado (reanálise gera nova linha); segregação de funções | P1 |
| LB-07 | Cadeia de custódia e condições da amostra (coleta, preservação, temperatura, recebimento, prazo de análise) | boa prática 17025 | lab | **PARCIAL**. `QualitySample` tem coletor, método, volume, recipiente, preservante, temperatura, `received_at`, status `IN_TRANSIT` | Sem prazo de validade da amostra por parâmetro nem alerta | Tabela de holding time por parâmetro (a validar com o químico responsável) | P2 |
| LB-08 | Laudo assinado por profissional habilitado (potabilidade: responsável técnico com ART; efluente: laudo assinado por profissional legalmente habilitado) | 888 Art. 23; CONAMA 430 art. 26 §2 | lab / compliance | **NÃO ATENDE**. Sem emissão de laudo nem assinatura (o `GovBrSignatureService` do MTR é stub) | Sem layout de laudo, sem assinatura | Laudo em PDF com assinatura quando o serviço existir | P1 |

### 5.4 Esgoto: efluentes, licenciamento e outorga

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| EF-01 | Limites de lançamento de efluente de ETE e conformidade: **CONAMA 430, art. 21**: pH 5–9; temperatura < 40 °C (e variação do corpo receptor ≤ 3 °C na zona de mistura); sedimentáveis ≤ 1 mL/L (virtualmente ausentes em lagos); **DBO5 ≤ 120 mg/L, que só pode ser ultrapassada com remoção ≥ 60% ou estudo de autodepuração**; óleos e graxas ≤ 100 mg/L; ausência de flutuantes; amostra filtrada para lagoas (§3). Padrões da Tabela I do art. 16 podem ser exigidos a critério do órgão (§1), sem nitrogênio amoniacal | CONAMA 430/2011 arts. 16, 21 | quality (ETE) | **NÃO ATENDE**. Existem `ETE_INLET`, `ETE_OUTLET` e `SLUDGE` como tipos de ponto, mas o catálogo é de potabilidade | Sem catálogo de efluente nem motor de conformidade; `portaria_ms_888` é a única "norma" modelada | Conjunto de normas de efluente separado do de potabilidade, com `valid_from`/`valid_to` | P0 (para tenants com esgoto) |
| EF-02 | **Rever tudo à luz da nova resolução CONAMA (aprovada 09/2026)**: nutrientes (N amoniacal, P total) por porte, controle de matéria orgânica atualizado, águas pluviais. Valores finais **NÃO CONFIRMADOS** (a proposta em consulta falava em 20 mg/L de N amoniacal e 4 mg/L de P total, com exceção por eficiência de 80%; é só minuta) | Notícia do MMA de 11/09/2026 | quality (ETE) | **NÃO ATENDE** | Risco de codificar limite que mudou | **Não codificar defaults fixos**; ler o texto publicado e criar a versão nova com data de início; manter a 430 até a vigência da nova | P0 (design) |
| EF-03 | Limites estaduais por faixa de vazão (exemplo RS, CONSEMA 355/2017, art. 17 II, efluente sanitário): Q < 200 m³/d: DBO5 120, DQO 330, SST 140; 200–500: 100/300/100 e coliformes ≤ 10^6 NMP/100 mL ou 90%; 500–1.000: 80/260/80, 10^5, 95%; 1.000–2.000: 70/200/70, 10^5, 95%; 2.000–10.000: 60/180/60, 10^4, 95%; ≥ 10.000: 40/150/50, 10^3, 95% (mg/L). *E. coli* pode substituir coliformes termotolerantes | RS CONSEMA 355/2017 | quality | **NÃO ATENDE** | Regra "por faixa de vazão" não cabe numa linha escalar | Tabela de padrões com faixa de vazão como dimensão | P1 |
| EF-04 | Automonitoramento periódico dos efluentes (critérios do órgão ambiental), amostragem representativa, coleta e análise por profissional habilitado, **laboratório acreditado pelo Inmetro (ou signatário do mesmo acordo) ou aceito pelo órgão**, com sistema de controle de qualidade analítica | CONAMA 430 arts. 24–26 | quality / lab | **PARCIAL**. Os pontos ETE existem; sem programa de automonitoramento | Frequência e parâmetros vêm da licença/órgão, não da resolução | Programa de automonitoramento por licença, com os pontos e as frequências da condicionante | P1 |
| EF-05 | **Declaração de Carga Poluidora** anual até 31 de março, referente ao ano anterior, com caracterização qualitativa e quantitativa dos efluentes | CONAMA 430 art. 28 | quality / compliance | **NÃO ATENDE** | Sem relatório | Gerador a partir do automonitoramento; ver se a nova resolução mantém a obrigação (aprimora "monitoramento e gestão de informações": **NÃO CONFIRMADO**) | P1 |
| EF-06 | Eficiência de remoção de DBO (entrada × saída, pareada) para o critério de 60% | CONAMA 430 art. 21 I d | quality | **NÃO ATENDE** | Sem pareamento `ETE_INLET`/`ETE_OUTLET` por coleta, sem cálculo | Cálculo de eficiência por campanha | P1 |
| EF-07 | Ecotoxicidade quando o órgão ambiental exigir; fósforo mais restrito onde há floração de cianobactérias a montante de captação | CONAMA 430 arts. 17, 18, 23 | quality | **NÃO ATENDE** | Sem parâmetro nem teste | Parâmetros opcionais por licença | P2 |
| EF-08 | **Outorga** de direito de uso para lançamento de esgotos (tratados ou não) e captação; registro de número, vazão, concentrações, validade e condicionantes. União via ANA, estados via órgão gestor | Lei 9.433/1997 art. 12, III | quality / compliance | **NÃO ATENDE** | Sem entidade | `WaterRightGrant` com vencimento, e alerta de 120 dias antes do vencimento (prazo interno, não da norma) | P1 |
| EF-09 | **Licenciamento ambiental** de ETA e ETE: licença, condicionantes e prazos. Pela Lei 15.190/2025 (vigente desde 04/02/2026; texto do art. 10 só em fonte secundária) ETA e ETE ficam **dispensadas** de licenciamento até a universalização, mantida a outorga; a regra é contestada no STF e os estados podem ter normas próprias | Lei 15.190/2025 art. 10 §§2–3; LC 140/2011 (não lida) | compliance | **NÃO ATENDE** | Sem registro de licença | `EnvironmentalLicense` com tipo (LP/LI/LO/LAC/dispensa), órgão, validade e condicionantes; **regime por tenant/UF**, nunca presumir a dispensa | P1 |

### 5.5 Lodo, reúso e indicadores regulatórios

| ID | Requisito | Origem | Módulo | Situação atual | Lacuna | Recomendação | Prio. |
|---|---|---|---|---|---|---|---|
| LO-01 | Lodo de esgoto para uso em solo é **biossólido** (CONAMA 498/2020): Classe A (até 10^3 *E. coli*/g de ST e processo de redução adicional de patógenos) ou B; Classe 1 ou 2 por metais (Tabela 3); titular da licença da UGL; plano de gerenciamento com ART; projeto por área; **dados de rastreabilidade enviados anualmente ao órgão** (art. 6 p.ú. e Anexo); notificação ao atingir 80% dos limites; documentos arquivados (art. 35); lodo fora dos critérios recebe outra destinação (art. 12). **Vedado** lodo de serviços de saúde, portos, aeroportos e sistemas individuais (síntese) | CONAMA 498/2020 | quality / grs | **NÃO ATENDE** (`SLUDGE` é só tipo de ponto) | Sem lote de biossólido, classificação nem rastreabilidade | Entidade `BiosolidBatch` + relatório anual; destinação alternativa via MTR/CDF (área do módulo `grs`) | P1 (se o tenant faz uso agrícola) |
| RU-01 | Reúso direto não potável de efluente sanitário tratado: modalidades, critérios e parâmetros mínimos (limpeza urbana, irrigação, processos industriais), via resolução conjunta CNRH/CONAMA de 09/2026. **Texto e parâmetros NÃO CONFIRMADOS** | Notícia do MMA e iNFRA | quality | **NÃO ATENDE** (existe `PointType.REUSE`) | Sem parâmetros | Preparar `ReuseModality` como dado, sem defaults; reler quando o DOU publicar | P2 |
| ID-01 | Indicadores regulatórios derivados dos dados de qualidade: Nível I-02 (% de análises de coliformes totais no padrão) e I-03 (% de análises de DBO no padrão na saída da ETE) | ANA NR 9/2024 (Res. 211/2024) | quality / reports | **NÃO ATENDE** | Sem relatório; fichas e fórmulas **não lidas** | Ler as fichas (Anexo I) e implementar; o regulador adota por resolução própria | P1 |

## 6. (c) Prazos e marcos

| Data | Marco | Situação |
|---|---|---|
| 2011 a 05/2021 | Portaria MS 2.914/2011 vigente (consolidada na PRC 5/2017, Anexo XX, a partir de 28/09/2017) | Superada |
| 04/05/2021 (DOU 07/05/2021; republicada 24/05/2021) | Portaria 888/2021 em vigor desde a publicação (Art. 3º) | Vigente |
| 28/09/2021 (DOU 30/09/2021) | Portaria 2.472/2021: Art. 44 §3 e novo Anexo 13 | Vigente |
| ~05/2022 | Fim do prazo de 12 meses para adequar o monitoramento de esporos de bactérias aeróbias (Art. 54). Contado "da data de publicação": 07/05/2022 se contado do DOU original, 24/05/2022 se da republicação | **Vencido** |
| ~05/2023 | Fim dos 24 meses para atingir o novo VMP de dureza (300 mg/L; Art. 55) | **Vencido** |
| ~05/2026 | Fim dos 5 anos para o Ministério revisar o Anexo XX (Art. 51). Nenhuma revisão publicada encontrada | **Vencido, sem revisão publicada** |
| Anual | Plano de amostragem elaborado e submetido à autoridade municipal (Art. 44); parecer em 30 dias, com aprovação tácita (Art. 13 VIII e p.ú.); alteração do plano via PSA: parecer em 120 dias, validade máxima 2 anos, renovação com parecer em 60 dias (Art. 50) | Contínuo |
| Mensal | Fechamento do percentual de coliformes totais e do percentil de turbidez (Anexos 1 e 2); SP: dados do mês anterior até o dia 10 de cada mês no SISAGUA (Res. SS-65/2016, fonte secundária) | Contínuo |
| 10/01 (SP) | Cadastro e plano atualizados no SISAGUA (idem) | Contínuo |
| Semestral | Água bruta por captação (Art. 42); radiológico na rede (Art. 37 §7) | Contínuo |
| 15/03 de cada ano | Relatório anual ao consumidor (Decreto 5.440, art. 3º III). Próximo: **15/03/2027** | Contínuo |
| 31/03 de cada ano | Declaração de Carga Poluidora (CONAMA 430, art. 28). Próximo: 31/03/2027, salvo mudança pela nova resolução | Contínuo (a rever) |
| 01/10/2024 | Vigência da NR 9 da ANA; verificação pelos reguladores 18 meses após a publicação (~03/2026) | Em curso |
| 04/02/2026 | Vigência da Lei 15.190/2025 (licenciamento) | Em vigor |
| 01 e 02/09/2026 | CONAMA aprova resolução de lançamento de efluentes (revoga a 430; em 02/09, 151ª ordinária) e resolução conjunta de reúso (em 01/09, 64ª extraordinária) | **Aguardando publicação no DOU** (prazos de adequação **NÃO CONFIRMADOS**) |
| Após a publicação | Prazos de adequação dos limites de N e P, e do reúso | NÃO CONFIRMADO: ler o texto |
| 31/12/2033 | Meta de universalização (99% água, 90% esgoto), via NR 8 da ANA e Lei 11.445/2007 (fonte secundária) | Futuro |

Lembrete: não há prazo numérico de 24 h para comunicar não conformidade na 888; a exigência é "imediatamente" (Art. 14 XXII; Art. 43 §9) e "previamente" para intervenções programadas (Art. 14 XXI; 72 h no Decreto 5.440).

## 7. (d) Variação regulatória: o que precisa ser parametrizável por tenant

| Parâmetro configurável | Por que varia | Evidência |
|---|---|---|
| UF, município, autoridade de saúde (SMS/SES, DSEI para áreas indígenas) | Quem recebe plano, relatório e comunicação; DSEI elabora o plano em áreas indígenas | 888 Arts. 13, 45 |
| Prazos e modo de inserção no SISAGUA (quem insere, dia do mês, cadastro/plano em janeiro) | A norma nacional delega ao estado (CIB) a definição dos prazos de inserção | 888 Art. 12 III; SP Res. SS-65/2016 (dia 10) |
| Modelo do relatório de controle exigido pela autoridade; formato de importação | "conforme o modelo estabelecido pela autoridade" | 888 Art. 14 XI |
| Plano de amostragem ampliado por determinação da autoridade (mais amostras, mais frequência, parâmetros adicionais) | Poder do município em situação de risco | 888 Art. 13 XI |
| Parâmetros de agrotóxicos a monitorar | Depende do uso na bacia e da sazonalidade | 888 Art. 44 §4 |
| Manancial (superficial/subterrâneo), população, tipo de filtração, desinfetante, fluoretação, SAA/SAC/SAI | Define quais anexos e mínimos valem | 888 Anexos 2, 13, 14, 15 |
| PSA aprovado e vigência da redução de amostragem | Autorização com validade máxima de 2 anos | 888 Art. 50 |
| Padrão de lançamento de efluente: federal (430 hoje; nova resolução depois), estadual (por exemplo RS: faixas de vazão; MG: DN 8/2022, **a confirmar**; SP: **não levantado**) e da licença | Estados podem ser mais restritivos; condicionante da licença prevalece | CONAMA 430 art. 1º e 16; RS 355/2017 |
| Classe do corpo receptor e enquadramento | Define limites de qualidade do receptor e a viabilidade da outorga | CONAMA 357/2005 |
| Autoridade de outorga (ANA ou estado) e regime de licenciamento (LO, LAC, dispensa) | Competência por domínio da água e legislação estadual | Lei 9.433 art. 12; Lei 15.190/2025 |
| Indicadores regulatórios e prazos de reporte do regulador infranacional (ex.: ADASA/DF adotou a NR 9 pela Res. 59/2025) | Cada agência regulamenta a adoção | ANA NR 8 e 9 |
| Limiares de alerta e críticos **internos** (diferentes do VMP legal) | Política do prestador | seed atual mistura os dois |
| SLA interno de comunicação e de reclamação | "Imediatamente" não tem prazo numérico | 888 Art. 14 XXII |
| Texto e idioma de comunicados e do relatório anual | Linguagem clara e acessível; marca "FORA DOS PADRÕES DE POTABILIDADE" | 888 Art. 14 XXII; Decreto 5.440 Anexo art. 8 |

## 8. (e) Perguntas em aberto para o jurídico e o regulatório

1. A nova resolução CONAMA de lançamento de efluentes e a resolução conjunta CNRH/CONAMA de reúso já foram publicadas? Número, data, texto final e **prazos de adequação** (principalmente N amoniacal e P total por porte)?
2. A nova resolução mantém a Declaração de Carga Poluidora anual até 31/03 e o automonitoramento nos termos dos arts. 24–28 da 430?
3. O Ministério da Saúde publicou ou vai publicar a revisão do Anexo XX prevista no Art. 51 (prazo de 5 anos venceu em 05/2026)? Há minuta?
4. Qual é o **layout vigente de importação do SISAGUA** e há API? O manual de 2016 (v1.0) ainda vale? Quais estados fixam prazos de inserção além de SP?
5. Para o Anexo 14: confirmar no DOU as faixas acima de 600.000 habitantes e se *E. coli* usa as mesmas amostras de coliformes totais.
6. Quais são os "parâmetros básicos" que o Decreto 5.440 exige no resumo mensal da conta e no relatório anual? Há norma mais nova do Ministério ou do regulador que substitua o decreto (a 888 admite "instrumento legal que venha substituí-lo")?
7. A Lei 15.190/2025, art. 10 §2, dispensa de fato o licenciamento de ETA/ETE? Qual o efeito das ações no STF e das normas estaduais?
8. A comunicação "imediata" tem prazo adotado pela vigilância local (por exemplo, um número de horas fixado por portaria estadual ou municipal)?
9. As Resoluções CNRH 54/2005 e 121/2010 continuam em vigor depois da resolução conjunta de 2026?
10. Qual é o texto do Anexo XXI da PRC 5/2017 (fluoretação) vigente?
11. Para tenants que são SAC ou operam carro-pipa, quais obrigações do Art. 16 e do Anexo 15 entram no escopo do produto?
12. A norma técnica CETESB P4.230 (SP) e outras normas estaduais de lodo se aplicam além da CONAMA 498/2020? E a 498 foi alterada depois de 2020 (não verifiquei)?
13. O tenant que usa laboratório terceirizado precisa guardar o escopo de acreditação do laboratório no sistema, e o regulador local aceita laboratório não acreditado (a CONAMA 430, art. 26, admite "aceito pelo órgão ambiental")?
14. Os valores numéricos das tabelas CT (Anexos 3–8) devem ser conferidos linha a linha contra o DOU por responsável técnico antes de qualquer cálculo automático.
15. Qual a política do produto para dado já gravado sob norma anterior (a 2.914 vigorou como ato autônomo de 2011 a 2017 e, depois, como Anexo XX da PRC 5/2017): o catálogo guardará versões históricas para recalcular conformidade de resultados antigos?

## 9. Achados descartados e correções

- **Consulta Pública Inmetro nº 21/2025** (datada de 19/08/2025, publicada em 22/08/2025 e encerrada em 22/10/2025; verificado em 22/10/2026: já convertida na Portaria Inmetro 188/2026): trata da Portaria Inmetro 102/2022, requisitos de conformidade de **equipamentos para consumo de água** (bebedouros, purificadores), incluindo faixa de pH 6–9,5 para a água fornecida e etiqueta ENCE a partir de 01/01/2027 (verificado em 22/10/2026: a Portaria Inmetro 188/2026 adotou como prazo limite 31/12/2027, e a minuta previa 31/12/2026; a faixa de pH aparece como critério da água fornecida no ensaio de extraíveis, Anexo C do RTQ, item 5.1). **Não** altera o padrão de potabilidade do Ministério da Saúde. A síntese inicial de busca estava errada; o prazo "31/12/2026" dessa consulta não é marco da 888.
- **CONAMA 503/2021** não é sobre lodo de ETE (ver quadro). **Lodo de esgoto = CONAMA 498/2020.**
- Não achei, em portal oficial, a data de publicação da PRC 5/2017 no DOU nem o dispositivo exato que revogou a Portaria 2.914/2011 (verificado em 22/10/2026: art. 864, CXXXIII, da PRC 5/2017). A 888 diz apenas que o Anexo XX "passa a vigorar na forma do Anexo".
- Vi um texto atribuindo à 2.914/2011 uma faixa recomendada de pH 6,0–9,5. **Não confirmei**; na 888 não há faixa de pH nos Anexos 9–11.

## 10. Limites desta pesquisa

- Não li o texto integral de: Anexo XXI da PRC 5/2017 (conferido depois, em 22/10/2026); Posição Regulatória CNEN 3.01/012:2020 (situação verificada em 22/10/2026); Decreto 5.440 no Planalto (usei a Câmara); Lei 15.190/2025 no Planalto; NR 8 e NR 9 da ANA (fichas dos indicadores); CONAMA 357/2005 no texto consolidado; normas estaduais além de RS 355/2017; texto final da nova resolução CONAMA e da conjunta de reúso (não publicados, até onde apurei).
- As tabelas CT (Anexos 3–8) foram só amostradas. Quem for importá-las precisa conferir contra a imagem do DOU, pois a extração do PDF mostra glitches (por exemplo "1 7" no lugar de "17").
- Nada neste arquivo altera o código do SIGSAN. Os achados sobre o código vêm da leitura de `V2_1__create_quality_schema.sql`, das entidades e de `LabService`/`ScadaIntegrationService`; não rodei testes.
