# Aplicação de correções de normas: lote 2

Data: 22/10/2026. Fonte das correções: `correcoes-agente-2.json` (45 normas do catálogo `docs/normas/catalogo-normas.json`). Arquivos editados (todos em UTF-8 com LF, sem CR e sem U+FFFD introduzidos): `arquitetura/FISCAL-PORTE-JAVA.md`, `arquitetura/adr/ADR-0003-motor-fiscal-portado-para-java.md`, `levantamento/00-CONSOLIDADO-enriquecimento-sigsan.md`, `levantamento/03-gsan-arrecadacao-cobranca-batch.md`, `levantamento/11-regulatorio-cobranca-bancario-fiscal.md`, `levantamento/13-regulatorio-atendimento-acessibilidade-assinaturas.md`, `levantamento/14-VALIDACAO-REGULATORIA-CONSOLIDADA.md`, `levantamento/16-PDCA-2026-10-12.md`.

Não houve alteração em `ADR-0019`, `ADR-0022` e `ADR-0031`: as ocorrências de NFS-e e NFAg-e nesses ADRs não trazem o dado errado (nenhuma cita o ato instituidor, o número de NT ou o escalonamento do Ato Conjunto 4/2026).

Observação sobre codificação: `03-gsan-arrecadacao-cobranca-batch.md` já continha 1 caractere U+FFFD antes desta rodada (conferido na cópia de segurança); não foi tocado. Contagem de linhas: só o ADR-0003 mudou (95 para 99, por quebras de linha inseridas em prosa); nos demais, as edições foram feitas dentro das linhas existentes.

## (a) Resumo

| Item | Total |
|---|---|
| Normas no lote | 45 |
| Correções aplicadas (integral) | 39 |
| Correção aplicada em parte | 1 (`febraban-layout-debito-automatico-padrao-150`, ver "Não aplicadas") |
| Sem ocorrência do dado errado nos arquivos do lote | 5 |
| Não aplicadas (itens) | 3 |
| Decisões a rever | 11 |
| Ocorrências fora do lote registradas | 7 arquivos |

## (b) Aplicadas

Convenção: onde a correção altera uma premissa, o texto recebeu a nota "verificado em 22/10/2026: ...". "L" indica a linha aproximada no arquivo.

### `levantamento/11-regulatorio-cobranca-bancario-fiscal.md`

| Norma (id) | Trecho antes → depois |
|---|---|
| `lc-lei-kandir` (L93) | "a Lei Kandir" → "a Lei Kandir (LC 87/1996)" |
| `bcb-circ-4022-2020` (L61) | "Circ. 4.022/2020" → acrescentado: contas de pagamento pré-pagas, revogada desde 01/03/2021 pela Res. BCB 51/2020 |
| `bcb-res-505-2025`, `cmn-res-5251-2025` (L34, L66, L136, L208, L209) | "recebedor PJ" → "pessoa jurídica ou entidade não autorizada a funcionar pelo BCB"; "adequação até 01/01/2026" → acrescentado "prorrogada para 01/01/2027 para tributos, convênios de serviços públicos e planos de saúde (Res. CMN 5.278/2025, Res. BCB 544/2025)"; Res. BCB 505 descrita como paralela, para contas de pagamento pré-pagas; enquadramento da água e esgoto em "serviços públicos" marcado como dependente de análise jurídica |
| `bcb-res-597-2026` (L64) | "(30/09/2026, última lida)" → conteúdo (veda transações destinadas a loterias de quota fixa) e ressalva de que a 587/2026 ainda tem efeitos futuros |
| `cmn-res-5299-2026` (L67) | "novo regime de débito automático; revoga a Res. CMN 4.790" → norma de diretrizes (art. 4º: princípios do débito automático; art. 6º: revogação diferida); regime operacional atribuído à Res. BCB 565/2026; "Futura" → "Futura (a 4.790 segue em vigor até 30/06/2027)" |
| `cmn-res-5171-2024` (L77, L145, L277) | "taxa legal = Selic − IPCA" → "taxa legal" com metodologia da resolução (Fator Selic e Fator IPCA do mês anterior, este com IPCA-15, seis casas decimais; não é diferença simples) |
| `stf-adpf-1005`, `stf-adpf-1006`, `stf-adpf-1097` (L82, L149) | "revisão anual pelo CMN; consignado entra no cálculo, 6 a 4" → R$ 600 mantido, CMN faz estudos técnicos anuais que embasem decisão motivada de atualizar ou manter, e, por 6 a 4, é inconstitucional excluir o consignado; julgamento conjunto concluído em 23/04/2026 |
| `cgnfse-nfse-padrao-nacional` (L94, L98, L180, L221) | "1ª etapa em 01/10/2026 (ato 4/2026)" e fonte "secundária" → escalonamento do Ato Conjunto RFB/CGIBS nº 4/2026, art. 1º, III (01/10/2026, 01/12/2026, 01/01/2027), regra de desconformidade até 31/12/2026, emissor "CGNFS-e, Receita Federal e CGIBS", fontes oficiais, confiança "Média a alta" |
| `svrs-nfag-e-modelo-75` (L33, L96, L175, L223) | acrescentado: instituída pelo art. 114 do Decreto 12.955/2026 e pelo regulamento do IBS, sem Ajuste SINIEF; data de 01/12/2026 confirmada (Receita Federal e art. 1º, XVI do Ato 4/2026); dispositivo do regulamento do IBS confirmado só em fonte secundária |
| `confaz-ajuste-sinief-49-2025`, `-8-2026`, `-20-2026`, `-27-2026` (L97) | "Ajustes SINIEF 49/2025, 8/2026, 20/2026, 27/2026" (ementa "NF-e com IBS/CBS") → objeto de cada ajuste: 49 (NF-e de débito e de crédito), 8 (retorno por recusa), 20 (perda de estoque e redução de valores), 27 (adia para 01/01/2027 a obrigatoriedade do 49, para o ICMS) |
| `rfb-in-2145-2023`, `rfb-in-2239-2024`, `stf-tema-1130` (L99) | "2.145/2023 e 2.239/2024 (art. 2º-A: ...)" → 2.145/2023 cria o art. 2º-A; 2.239/2024 dá nova redação ao caput e acrescenta §§ 4º e 5º (fundações) |
| `stf-tema-1130` (L182) | acrescentada a ressalva (Solução de Consulta Cosit 108, não lida na íntegra): o julgado não se estende a empresas estatais; regra vale para administração direta, autarquias e fundações (nos termos da IN 2.239/2024) |
| `rfb-nt-011-2026` (L103) | "NT de 04/02/2026" → "03/02/2026, atualizada em 20/02/2026" |
| `rfb-in-2181-2024` (L104) | "15/03/2024" → "assinada em 13/03/2024, publicada em 15/03/2024"; acrescentado que altera a IN 2.043/2021 e adia a substituição da Dirf (antes 2024) |
| `febraban-cnab-444` (L126, L301) | acrescentado: layout próprio de cada administradora de FIDC, factoring e securitizadora, derivado do CNAB 400 com a chave da NF-e nas posições 401 a 444; não é padrão FEBRABAN |

### `levantamento/13-regulatorio-atendimento-acessibilidade-assinaturas.md`

| Norma (id) | Trecho antes → depois |
|---|---|
| `mp-1393-2026` (L41, L293) | "(multa; ...)" e "altera o teto de multa administrativa" → MP de 25/09/2026 que trata sobretudo do Desenrola Brasil 3.0; art. 57 do CDC alterado apenas para combustíveis |
| `lei-14364-2022` (L47) | "parágrafos: Lei 14.364/2022" → "parágrafo acrescentado ao art. 1º" |
| `ares-pcj-res-363-2020` (L51) | "aviso de 30 dias antes da suspensão", "NÃO CONFIRMADO", confiança "Baixa" → regulamento de serviços do município de Jumirim (art. 149, I), não norma geral; vigente; texto lido; confiança "Média" |
| `lei-17354-2008-mg` (L60) | acrescentado: alterada pela Lei 24.657/2024 (ementa e alcance ampliados), vigência confirmada |
| `lei-7116` (L67) | "Leis 7.116" → "Leis 7.116/1983" |
| `portaria-interministerial-4-2024` (L90) | "Portaria Interministerial 4/2024" → número oficial completo (GSIPR/MAPA/MCID/MCTI/MD/MF/MGI/MIDR/MJSP/MS nº 4), assinatura em 21/11/2024, DOU de 22/11/2024 |
| `codigo-penal` (L92) | "Código Penal" → "Código Penal, Decreto-Lei 2.848/1940" |
| `lei-15040-2024` (L98) | "revogado pela Lei 15.040/2024" → "pelo art. 133 da Lei 15.040/2024 (normas de seguro privado)" |
| `stj-resp-1117903` (L24, L99, L281) | "julgado em 2009-2010 (data exata NÃO CONFIRMADA)" → 09/12/2009, DJe 01/02/2010; "Tema 251-254, número NÃO CONFIRMADO" → Temas 251 a 254 confirmados; prazo registrado como da pretensão de cobrança pelo prestador, não da repetição de indébito |
| `stj-resp-1113403` (L100) | "Rel. Teori Zavascki, 25/11/2009" → julgado em 09/09/2009 (DJe 15/09/2009), Temas 154 e 155; 25/11/2009 é a data de aprovação da Súmula 412 |
| `stj-earesp-600663`, `stj-earesp-676608` (L101, L282) | acrescentado: julgados em 21/10/2020, origem em telefonia (TJRS), relatores (Og Fernandes no 676.608; relator para acórdão Herman Benjamin no 600.663), prazo decenal da repetição; "modulação ... NÃO CONFIRMADO" → modulação só para indébitos contratuais não públicos cobrados após a publicação; serviços públicos sem marco temporal |
| `lei-15141-2025` (L104) | "art. 8º-A incluído" → "arts. 8º-A e 8º-B incluídos pelo art. 216 ... lei de carreiras e remuneração do Executivo federal" |
| `cnciber-res-11` (L186) | "Res. 11/CNCiber" → "Res. CNCiber 11/2025, que apenas cria o grupo de trabalho; o guia ainda não foi localizado" |
| `portaria-mtr-313-2026` (L284) | "não confirmei existência nem conteúdo" → é a Portaria nº 313, de 06/05/2026, do Ministério dos Transportes (concessões ferroviárias); sem portaria de MTR nº 313/2026 no MMA ou no Sinir |

### `levantamento/14-VALIDACAO-REGULATORIA-CONSOLIDADA.md`

| Norma (id) | Trecho antes → depois |
|---|---|
| `svrs-nfag-e-modelo-75` (L33) | nota de verificação (data confirmada; ato instituidor; sem Ajuste SINIEF) |
| `bcb-res-505-2025`, `cmn-res-5251-2025` (L35) | "recebedor PJ ... (Res. CMN 5.251 e BCB 505)" → "pessoa jurídica ou entidade não autorizada pelo BCB"; 5.251 altera a 4.790; 505 é paralela para contas pré-pagas; prazo 01/01/2027 para tributos, serviços públicos e planos de saúde |
| `stj-resp-1117903` (L122) | "número do Tema não confirmado" → Temas 251 a 254; a pergunta deve separar cobrança pelo prestador e repetição de indébito pelo usuário |

### `arquitetura/adr/ADR-0003-motor-fiscal-portado-para-java.md`, `arquitetura/FISCAL-PORTE-JAVA.md`

| Norma (id) | Trecho antes → depois |
|---|---|
| `nfse-nt-007-2026` (ADR-0003 L17; FISCAL L75) | "cálculo de IBS/CBS conforme a NT 007/2026" e "half-even ... conforme NT 007/2026" → o código cita a NT 007/2026, da SE/CGNFS-e, que aplica half-even e tolerância de R$ 0,01 apenas a PIS e Cofins (vPis, vCofins); IBS e CBS "por documento e tributo" continuam a confirmar nas NTs 004, 008, 009 e no manual da NFS-e |
| `svrs-nfag-e-modelo-75` (ADR-0003 L12; FISCAL L219) | nota de verificação (data confirmada no art. 1º, XVI do Ato 4/2026 e pela Receita Federal; instituída pelo art. 114 do Decreto 12.955/2026 e pelo regulamento do IBS; sem Ajuste SINIEF). "A confirmar no DOU" e a tarefa correspondente do ADR foram mantidos |

### Demais arquivos

| Arquivo | Norma (id) | Trecho antes → depois |
|---|---|---|
| `levantamento/03-gsan-arrecadacao-cobranca-batch.md` (L588) | `febraban-cnab-444` | "layout unificado FEBRABAN [EXT]" → layout proprietário de administradoras de FIDC, factorings e securitizadoras, não unificado FEBRABAN |
| `levantamento/00-CONSOLIDADO-enriquecimento-sigsan.md` (L19) | `febraban-cnab-444` | "CNAB 444" → "CNAB 444 (layout próprio de administradoras ..., não padrão FEBRABAN)" |
| `levantamento/16-PDCA-2026-10-12.md` (L93) | `abnt-nbr-10004-2024` | "NBR 10004:2024" → "NBR 10004-1 e 10004-2 (série 2024)" |

## (c) Decisões a rever

Nenhuma decisão, tarefa, prazo ou parâmetro foi alterado. Os pontos abaixo mudaram uma premissa e devem ser reavaliados pelo dono do produto.

1. **Instrumento e data da NFAg-e** (ADR-0003, FISCAL-PORTE-JAVA, relatórios 11 e 14). O modelo 75 foi instituído pelo art. 114 do Decreto 12.955/2026 e pelo regulamento do IBS, e não por Ajuste SINIEF; a data de 01/12/2026 está confirmada pela Receita Federal e pelo texto do Ato Conjunto RFB/CGIBS 4/2026 (art. 1º, XVI). A tarefa "Confirmar no DOU a obrigatoriedade e a data da NFAg-e" (ADR-0003) e o "A confirmar no DOU" foram mantidos; cabe decidir se a tarefa ainda procede ou se muda de natureza. A SVRS ainda tem o manual em minuta.
2. **Arredondamento e NT 007/2026** (ADR-0003, FISCAL-PORTE-JAVA). A NT 007 não é a base normativa do arredondamento de IBS e CBS; o portado "half-even por documento e tributo" fica sem norma citada até a leitura dos anexos da NT 004, das NTs 008 e 009 e do manual da NFS-e. A pendência já constava como "a confirmar".
3. **Prazo de adequação do débito automático** (relatório 11 R14, R15 e c.1; relatório 14 L35). O prazo geral de 01/01/2026 passou a 01/01/2027 para tributos, convênios de serviços públicos e planos de saúde. Se a cobrança de água e esgoto se enquadra em "serviços públicos" depende de análise jurídica. Isso pode alterar a urgência da decisão de escopo (P0) sobre Pix Automático e FEBRABAN 150.
4. **Alcance da Res. BCB 505/2025** (relatórios 11 e 14). A 505 trata de contas de pagamento pré-pagas e não cria o Pix Automático; a base da 505 é a Res. CMN 4.282/2013, art. 13, e a ligação com a Res. CMN 5.251 vem do projeto. Convém rever se o projeto quer tratá-las como par.
5. **Taxa legal do art. 406 do CC** (relatório 11 R19). A taxa é calculada pela Res. CMN 5.171/2024 a partir do Fator Selic e do Fator IPCA do mês anterior (IPCA-15), e o BCB a divulga mensalmente; a recomendação "fonte de índices IPCA/Selic com carga mensal" deve passar a consumir a taxa divulgada pelo BCB, ou replicar essa metodologia.
6. **Mínimo existencial** (relatório 11 R23). O STF mandou o CMN fazer estudos técnicos anuais para decidir de forma motivada entre atualizar e manter o valor; não determinou "revisão anual do valor". O consignado não pode ser excluído do cálculo (6 a 4).
7. **Retenção de IR por entes públicos** (relatório 11 R46). A regra do Tema 1.130 e do art. 2º-A da IN RFB 1.234 não alcança, segundo a Receita (Solução de Consulta Cosit 108, não lida na íntegra), pagamentos de empresas estatais; afeta a hipótese de companhias de saneamento constituídas como empresa e a pergunta aberta sobre SAAE.
8. **Prescrição** (relatórios 11 R22, 13 RT-01 e 14 pergunta 3). Os Temas 251 a 254 (REsp 1.117.903/RS) tratam da pretensão de cobrança da tarifa pelo prestador; a repetição de indébito pelo usuário é regida pela Súmula 412 e pelos Temas 154, 155 e 932. A decisão sobre o prazo padrão (10 anos) e sobre a retenção de 120 meses deve separar as duas pretensões.
9. **Devolução em dobro em serviços públicos** (relatório 13 CB-06 e pergunta 3). A modulação da Corte Especial alcança apenas indébitos contratuais não públicos cobrados após a publicação do acórdão; para serviços públicos não há marco temporal. O Tema 929 tem mérito julgado e acórdão ainda não publicado.
10. **CNAB 444** (relatório 03 P-table, relatório 00 e relatório 11 R10). Não é padrão FEBRABAN; a decisão "vale suportar? muitos bancos já migram" parte de premissa errada, e a frase "muitos bancos já migram" no relatório 03 não foi confirmada nem alterada.
11. **Escalonamento da NFS-e Padrão Nacional** (relatório 11 R44). Há três etapas (01/10/2026, 01/12/2026, 01/01/2027) com grupos de serviços distintos; a decisão sobre emitir NFAg-e ou NFS-e para religação, ligação nova e taxas deve considerar em qual etapa cada serviço cai.

Também ficou superada a premissa de que a regra de aviso de 30 dias da ARES-PCJ 363/2020 seria geral: ela é do regulamento de serviços de Jumirim (art. 149, I).

## (d) Não aplicadas

1. `febraban-layout-debito-automatico-padrao-150`, afirmação de que a data de 01/07/2027 "não se confirmou" como início do novo regime e se refere ao Pix Automático em contas-salário (Res. BCB 587/2026). Contradiz as correções de `cmn-res-5299-2026` e `bcb-res-505-2025`, segundo as quais, em 01/07/2027, a Res. CMN 4.790 é revogada e a Res. BCB 565/2026 passa a alterar a Res. BCB 51/2020. Os rótulos "novo regime de débito automático" / "01/07/2027" nos relatórios 11 (item 10 do resumo e c.2) e 14 (L61 e L112) foram mantidos. A parte da correção sobre o prazo de 01/01/2027 e sobre o alcance do art. 2º-A foi aplicada (ver acima). Precisa de conferência no texto da Res. BCB 565 e da Res. BCB 587.
2. `bcb-res-505-2025`, item (3): a ligação da Res. BCB 505 com a Res. CMN 5.251 "deve ser verificada no lote do CMN". Não foi alterada; só a descrição de que a 505 é paralela para contas pré-pagas.
3. `stj-resp-1117903`, relatório 11 R22 (L148): o texto fala em "recurso repetitivo da 1ª Seção" sem citar o REsp nem o Tema ("NÃO CONFIRMADOS"). A correspondência com o REsp 1.117.903/RS é provável, mas o texto não a afirma e a fonte citada (Migalhas) não foi reaberta; por isso a linha não foi alterada.

Sem ocorrência do dado errado nos arquivos do lote (nada a editar):

| Norma (id) | Motivo |
|---|---|
| `pl-4752-2025` | o relatório 13 não usa "Congresso Nacional"; já indica Senado, CCT, 24/09/2025 e última movimentação de 04/09/2026 |
| `portaria-slti-3-2007` | o relatório 13 já usa "Portaria SLTI 3/2007", de 07/05/2007, restrita ao SISP; o erro de emissor (MMA) era só do catálogo |
| `w3c-wcag-2-2` | os arquivos do lote não citam a NBR 17225 nem a tratam como tradução; o relatório 13 recomenda WCAG 2.2 AA e o ADR-0022 cita "WCAG 2.1 AA" (ver "Fora do lote") |
| `antt-res-5232-2016` | o relatório 14 só diz "5.232/2016 e 5.947/2021 revogadas", o que confere |
| `antt-res-6016-2023` | o relatório 14 cita a 6.016/2023 sem data |

## (e) Fora do lote

Ocorrências de dado desatualizado ou com potencial de divergência em arquivos que não são deste lote (não editados):

| Arquivo | Norma | Observação |
|---|---|---|
| `levantamento/12-regulatorio-residuos-mtr-sinir.md` (L22, L88, L363, L385) | `abnt-nbr-10004-2024` | "conteúdo das classes novas NÃO CONFIRMADO / fontes divergem": a verificação indica duas classes (Classe 1 perigoso e Classe 2 não perigoso), fim da divisão IIA e IIB, transição até 31/12/2026 (fonte secundária) |
| `levantamento/12-regulatorio-residuos-mtr-sinir.md` (L24, L87) | `antt-res-5232-2016`, `antt-res-6016-2023` | conferir objeto da 5.232 (Instruções Complementares, não o regulamento) e data da 6.016 (15/05/2023 é a publicação) |
| `levantamento/gsan/20f-demandas-legais-gsan-vs-ziip.md` (L85) | `stj-resp-1117903` | "número do tema não localizado": são os Temas 251 a 254 |
| `arquitetura/adr/ADR-0022-portais-historico-e-documentos.md` (L84) | `w3c-wcag-2-2` | cita WCAG 2.1 AA (RNF-60), enquanto o relatório 13 recomenda WCAG 2.2 AA; não é erro de citação, mas é divergência a decidir |
| `levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md` (L576) e `18e-aderencia-faturamento-arrecadacao-cobranca.md` (L656) | `cmn-res-5299-2026` | "fim do regime anterior" / "revogado em 01/07/2027 (Res. CMN 5.299/2026)": está correto quanto à revogação, mas a 5.299 é norma de diretrizes; o regime operacional é a Res. BCB 565/2026 |
| `PLANO-EXECUCAO-PDCA-R0-R9.md` e `levantamento/lacunas/22d-gestao-regulatorio-inovacao-de-mercado.md` | `svrs-nfag-e-modelo-75` | o catálogo localizou citações nesses arquivos de outro lote; o 22d já traz a correção, e o plano não foi aberto |
