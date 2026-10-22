# Mapa de entidades reguladoras infranacionais (ERIs) de saneamento

Data da pesquisa: 22/10/2026. Insumo da biblioteca "Normas e legislação" do Ziip SAN, fase de plano e documentação. Os atos catalogados estão em `04-eris.json` (565 itens); o que não foi localizado está em `04-pendencias.md`.

> **Não é parecer jurídico.** Levantamento técnico para orientar requisitos de software. Os valores abaixo precisam de conferência por advogado ou pelo regulatório de cada cliente antes de virarem regra de fábrica. Marcas: **[C]** confirmado em texto lido do ato; **[S]** fonte secundária; **[NC]** não confirmado ou não localizado. O que não foi lido no texto da norma não foi tratado como confirmado.

## 1. Como o mapa está organizado

1. Esta parte de abertura sintetiza o que importa ao perfil regulatório pré-carregado (ADR-0038): totais, achados, quadro das bifurcações entre agências, adoção das Normas de Referência (NR) da ANA, perfil de Santa Catarina e divergências com os levantamentos `08`, `20f` e `20g`.
2. Os anexos A a F trazem o quadro de cada ERI (UF, nome, abrangência, normas de condições gerais e de tarifa, adoção das NR, parâmetros com artigo e marca). Cada anexo foi produzido por uma frente de pesquisa; a numeração interna das seções foi mantida.
3. O campo "adoção das NR" usa sim, parcial, não ou desconhecido. "Sim" refere-se, na maior parte dos casos, ao ciclo de 2025 da ANA, que verificou apenas as NR 3, 5, 6 e 8 para água e esgoto. Ausência na lista positiva não prova descumprimento.

## 2. Totais do catálogo

| UF ou grupo | Itens | Fonte oficial | Fonte secundária | Não confirmado | Vigentes | A confirmar |
|---|---|---|---|---|---|---|
| SC | 119 | 115 | 4 | 0 | 85 | 21 |
| SP | 74 | 69 | 5 | 0 | 64 | 7 |
| MG | 66 | 63 | 3 | 0 | 59 | 5 |
| ARES-PCJ (SP/MG) | 11 | 11 | 0 | 0 | 11 | 0 |
| RJ | 34 | 30 | 4 | 0 | 29 | 5 |
| ES | 37 | 37 | 0 | 0 | 29 | 3 |
| RS | 27 | 22 | 5 | 0 | 19 | 6 |
| PR | 17 | 15 | 2 | 0 | 12 | 4 |
| DF | 7 | 7 | 0 | 0 | 5 | 1 |
| GO | 14 | 11 | 3 | 0 | 11 | 3 |
| MS | 7 | 1 | 6 | 0 | 3 | 3 |
| MT | 16 | 7 | 9 | 0 | 14 | 2 |
| CE | 20 | 19 | 1 | 0 | 20 | 0 |
| PE | 12 | 10 | 2 | 0 | 11 | 1 |
| BA | 8 | 6 | 2 | 0 | 4 | 1 |
| AL | 10 | 4 | 6 | 0 | 8 | 0 |
| RN | 6 | 0 | 6 | 0 | 3 | 3 |
| PB | 3 | 1 | 2 | 0 | 1 | 2 |
| PI | 7 | 4 | 3 | 0 | 4 | 3 |
| MA | 13 | 10 | 3 | 0 | 4 | 9 |
| SE | 6 | 5 | 1 | 0 | 4 | 2 |
| PA | 9 | 8 | 1 | 0 | 7 | 1 |
| AM | 3 | 0 | 3 | 0 | 1 | 1 |
| RO | 12 | 10 | 2 | 0 | 8 | 4 |
| AC | 2 | 2 | 0 | 0 | 1 | 1 |
| AP | 2 | 1 | 1 | 0 | 1 | 1 |
| RR | 1 | 0 | 1 | 0 | 0 | 1 |
| TO | 9 | 8 | 1 | 0 | 8 | 1 |
| Federal/ANA | 13 | 12 | 1 | 0 | 13 | 0 |
| **Total** | **565** | **488** | **77** | **0** | **439** | **91** |

Por tipo: AGENCY_NORM 459; OTHER 34; STATE_LAW 36; MUNICIPAL_LAW 36. Por situação: VIGENTE 439; REVOGADA 13; A_CONFIRMAR 91; PARCIALMENTE_REVOGADA 14; EM_REVISAO 8.

SC reúne ARIS (37), AGIR (21), ARESC (31), normas estaduais e de outras entidades de SC (30).


## 3. Achados que mais pesam para o perfil regulatório

1. **A adoção das NR 9 a 16 ainda não foi verificada pela ANA.** O ciclo de 2025 (lista positiva divulgada em 22/12/2025) cobriu NR 3, 5, 6 e 8; os ciclos de 2023 e 2024 verificaram só a NR 1 (resíduos). O resultado do ciclo de 2026 não estava publicado em 22/10/2026. A verificação da NR 11 (condições gerais) começa em 20/05/2027 e a da NR 13 (estrutura tarifária e tarifa social) em 20/05/2028. Nenhuma das grandes ERIs editou ainda norma geral de condições gerais com referência expressa à NR 11; as exceções localizadas são ORCISPAR (PR, Res. 28/2025) e CISAM Meio Oeste (SC, RN 05/2026). O produto não pode presumir que a ERI do cliente já alinhou suas normas e deve guardar qual norma local vale.
2. **Datas divergentes em fonte oficial.** O texto da NR 11 (art. 74 §3º) fixa 20/05/2027; uma notícia da ANA diz 27/05/2027. Prevalece o texto do ato. O prazo da tarifa social é 11/12/2026 pela vigência da Lei 14.898/2024 contada pela ANA; agências que contaram a vigência de 10/12/2024 chegam a 10/12/2026 (ARSAL, entre outras). Sugestão de parametrização: 11/12/2026 como data legal e 10/12/2026 como limite interno prudencial.
3. **Corte de água: o conflito é estrutural.** ARIS (art. 158), AGIR (art. 148), ARESC (art. 85), ARCE, AGR-GO e ARPE permitem cortar até 12 h de sexta-feira (ARPE: 17 h), o que contraria a Lei 14.015/2020, que veda iniciar o corte de sexta a domingo, em feriado e na véspera. Em SC há ainda leis estaduais mais protetivas (Lei 11.959/2001, redação da Lei 17.780/2019, e Lei 16.673/2015, art. 21 p.u.), cujo alcance sobre prestadores municipais é duvidoso diante da jurisprudência do STF. A ADI 7.725/TO (julgamento finalizado em 23/05/2025) enfraquece leis estaduais sobre suspensão por inadimplência (Paraná e Distrito Federal); a Lei Distrital 5.990/2017, base dos 12 meses de proteção da ADASA (art. 122), consta como declarada inconstitucional no SINJ-DF. Recomendação do levantamento: travar no piso federal e registrar a divergência no perfil.
4. **Juros e multa mudam por agência e mudam no tempo.** A ARSESP passou a fixar multa de até 2%, juros de até 1% ao mês pro rata die e IPCA (Del. 1.791/2026), superando o "0,033% ao dia" que o `08` registra. As três agências de SC e várias do Norte e Nordeste usam até 0,033% ao dia; AGERGS, AGENERSA e AGR-GO usam 1% ao mês. A ARES-PCJ remete à legislação de cada município; o SAMAE de Caxias do Sul cobra 0,5% ao mês. A correção varia entre IPCA, INPC, Selic e índice do reajuste. A multa é de 2% na quase totalidade, mas há casos de 3%, 6% e 9% por faixa de atraso (AMAE Marília) e de 0,33% ao dia até 10% (ARESPCAB).
5. **O perfil precisa ser por município, não por tenant nem por prestador.** A CASAN responde a quatro agências (ARESC, ARIS, AGIR e CISAM-SUL) conforme o município; a Corsan aplica a RN 66/2022 da AGERGS em 238 municípios e o regulamento da AGESAN em 81, com média de 6 contra 12 consumos e religação indevida de 4 h contra 12 h; na ARES-PCJ cada município tem regulamento e resolução tarifária próprios; no Espírito Santo, Sooretama consta nas listas de duas ERIs e Linhares passou da ARIES para a ARSP em 2026. A chave do perfil deve ser agência e município, com data de início.
6. **Contagem de prazos varia.** ARIS conta em dias corridos (art. 171); ARESC, ARSP (ES), AGR-GO (nos prazos de resposta) e AGENERSA (IN 103/2023) usam dias úteis; a AGIR não define na RN 001/2013 (a ouvidoria usa dias úteis pelo CPC). Cada prazo do perfil deve ser marcado como corrido ou útil.
7. **Tarifa social: critérios locais coexistem com o piso federal.** Há desconto de 50% até 15 m³ como piso, mas AL, TO, RN, AP e SE mantêm critérios próprios anteriores; a ADASA mantém limite de 30 m³ (mais favorável); a ARES-PCJ acrescenta 25% de 15 a 20 m³; a ARSAE-MG não impõe limite de consumo. A regra da AGIR que retira o benefício após dois cortes no ano (RN 001/2013, art. 127 §7º) contraria a Lei 14.898/2024 e deve ser recusada ao carregar o perfil.
8. **Agências sem condições gerais próprias.** AGRESPI (PI), AGERO (RO) e ARSAP (AP) não têm norma de condições gerais própria localizada (valem contrato de concessão, decreto ou regulamento do prestador); em RR não há ERI estadual. A MOB (MA) depende de ato da extinta ARSEMA (2011 a 2016).
9. **Nomes corrigidos em relação ao pedido original.** AGERSA é a Agência Reguladora de Saneamento Básico do Estado da **Bahia**; a AGERBA não regula saneamento; no RJ não há agência com a sigla AGERSA (existem AGENERSA, AGERSAN de Macaé, SANEAR de Resende e a Fundação Rio-Águas). ARSEP é a agência do **Rio Grande do Norte**, não de MS (em MS a ERI é a AGEMS, com a AGEREG em Campo Grande). "ARISB-MT" corresponde, provavelmente, à ARIS-MT. A ARSAE-MG passou a se chamar Agência Reguladora de Saneamento e Energia de Minas Gerais (Lei estadual 25.669/2025), mantida a sigla.
10. **Normas lidas por imagem ou só por ementa.** Parte dos PDFs oficiais (ARESC, algumas agências de MG e SP) não tem camada de texto; nesses casos a ficha do item no JSON registra que só a ementa vale.

## 4. Quadro das bifurcações entre agências (valores com artigo nos anexos)

Valores de pré-carga sugeridos, todos sobrescrevíveis por tenant e com vigência. "NC" indica não confirmado no texto lido. A ordem dos parâmetros segue a pergunta do dono do produto: juros, prazo de atendimento (corridos ou úteis), aviso de corte, tarifa social.

| ERI | Multa / juros / correção | Aviso de corte e limites de idade da dívida | Dias e horários vedados para iniciar o corte | Religação: normal / urgência / indevida | Média sem leitura | Contagem de prazos | Tarifa social |
|---|---|---|---|---|---|---|---|
| ARIS (SC) | 2% / até 0,033% ao dia / IPCA | 30 dias; vedado cortar por tarifa vencida há mais de 120 dias | após 12 h de sexta e véspera de feriado (conflita com a Lei 14.015/2020) | 48 h / 4 h facultativa / 4 h sem ônus | 6 meses, até 3 ciclos, depois consumo mínimo | dias corridos (art. 171) | 50% até 15 m³ (RN 56/2026); corte do beneficiário admitido |
| AGIR (SC) | 2% / até 0,033% ao dia / IPCA | 30 dias; sem teto de idade para cortar (120 dias só condiciona ligação) | idem ARIS | 48 h / 4 h / 4 h, com crédito do maior entre 2x a urgência e 20% da última fatura | 6 meses, até 3 ciclos, depois consumo mínimo | não definido na RN 001/2013 | sem norma própria da Lei 14.898; art. 127 §7º ilegal |
| ARESC (SC) | 2% / até 0,033% ao dia / IPCA | 30 dias; sem teto de idade | idem; Lei SC 16.673/2015 art. 21 p.u. é mais protetiva | 48 h / 4 h / 4 h, com crédito do maior entre 2x a urgência e 20% da 1ª fatura | 6 meses, até 3 ciclos; saldo posterior parcelado em 3 vezes | dias úteis (art. 153) | Res. 313/2024 (provisória) e 348/2025 |
| ARSESP (SP) | até 2% / até 1% ao mês pro rata die / IPCA (Del. 1.791/2026) | 30 dias, correspondência emitida em até 90 dias da ocorrência; 45 dias para saúde, educação e internação | sábados, domingos, feriados e vésperas (art. 95) | 48 h / 6 h / 6 h, com crédito do maior entre 2x a urgência e 20% da 1ª fatura | 180 dias, até 3 ciclos, depois consumo mínimo | NC (resposta em 15 dias, unidade não especificada) | Social II: 50% até 15 m³; base mensal do CadÚnico |
| ARSAE-MG (MG) | até 2% / percentual NC / Selic | aviso na fatura seguinte; execução 30 dias corridos depois; novo aviso se passar de 90 dias | sexta a domingo, véspera e feriado | 2 dias corridos / 12 h facultativa / 12 h, com crédito de 2x o faturamento | 12 ciclos; máximo de 6 ciclos; depois 50%, 100% ou 150% | NC | Res. 227/2026: mínimo de 50%, sem limite de consumo; base trimestral |
| ARES-PCJ (SP/MG) | conforme a legislação de cada município | 30 dias com comprovação de recebimento | sexta a domingo, feriado e véspera (horário por regulamento municipal) | 24 h / 4 h facultativa / 12 h | 6 meses ou consumo mínimo, se menor; máximo de ciclos NC | NC | Res. 592/2024: 50% até 15 m³ e 25% de 15 a 20 m³ |
| AGENERSA (RJ) | 2% / 1% ao mês / IPCA | 30 dias; suspensão a partir do 30º dia de atraso | regulamento não fixa; Lei RJ 4.824/2006 veda vésperas de sábado, domingo e feriado | 48 h / NC / 24 h | 12 leituras | dias úteis nos prazos da IN 103/2023 | critérios da concessionária aprovados pela agência; norma pela Lei 14.898 NC |
| AGERGS (RS) | 2% / 1% ao mês pro rata / índice do reajuste | 30 dias (art. 109 §13 III, confirmado); 60 dias essenciais; 40 dias baixa renda; vedado após 90 dias da fatura vencida | sexta a domingo, feriado e véspera | 24 h / 4 h / 4 h, com crédito do dobro | 6 faturados; até 3 ciclos | NC | RED 892/2026 [S]: 50% até 15 m³ |
| AGEPAR (PR) | 2% / 0,033% ao dia / IPCA | 30 dias por escrito; vedado cortar após 90 dias | sexta a domingo, feriado e véspera | 4 h após a quitação / NC / 4 h | 5 meses | NC | Res. 36/2025: desconto também sobre a faixa fixa |
| ADASA (DF) | até 2% / 0,033% ao dia / INPC | só com atraso de 60 dias ou mais; notificação de 30 dias, sob pena de nulidade; vedado após 120 dias | sexta a domingo, feriado e véspera | 40 h úteis / NC / 3 h; desconto de 20% do consumo médio se atrasar | 12 meses | NC | art. 67-A; limite de 30 m³; proteção de 12 meses (base legal em dúvida) |
| AGR (GO) | 2% / 1% ao mês / INPC | 30 dias, só em dias úteis | após 12 h de sexta e vésperas | 24 h / 6 h (úteis) ou 14 h / 6 h; exige quitação | 6 meses; até 6 ciclos; depois 50% | dias úteis nos prazos de resposta (RN 207/2023) | RN 186/2021 sem adequação localizada |
| AGEMS (MS) | 2% / 0,033% ao dia / IPCA | 30 dias | NC neste quadro (ver anexo D) | 48 h / NC / 6 h | 6 meses; até 3 ciclos | NC | sem desconto sobre a tarifa fixa; Campo Grande com teto de 20 m³ |
| ARCE (CE) | 2% / 0,033% ao dia / IPCA | 30 dias | após 12 h de sexta e véspera | 1 dia útil após pagamento / 4 h / 4 h | 6 meses; até 3 ciclos | NC | norma específica não localizada; minuta de condições gerais pela NR 11 em audiência |
| ARPE (PE) | 2% / 0,033% ao dia / sem previsão | 30 dias | após 17 h de sexta e véspera | 48 h / 24 h / 24 h | 6 meses; ciclos NC | NC | Res. 292/2025 |
| ARSAL (AL) | 2% / 0,033% ao dia / sim | 15 dias, com aviso em até 90 dias | sábados, domingos, feriados e vésperas | 48 h / 6 h / 6 h | 180 dias; até 3 ciclos | NC | critérios locais anteriores (renda até 1,5 salário mínimo, imóvel até 60 m², consumo até 20 m³) |
| ARSP (ES) | até 2% / até 0,033% ao dia / NC | 30 dias; 45 dias para saúde, educação, internação e tarifa social | não localizada vedação por dia da semana | 48 h (72 h com retirada) / 6 h / 12 h, com crédito do valor da religação | NC | dias úteis (art. 120) | Cesan: Social I e II (Res. 051/2021 e 055/2022) |
| ARCON-PA (PA) | 2% / 0,033% ao dia / IPCA | 30 dias | sexta, sábado, domingo, véspera e feriado | 24 h / 12 h / 4 h | 6 meses | NC | NC |
| ATR (TO) | 2% / 0,033% ao dia / IPCA | 30 dias | após 12 h de sexta e véspera | 48 h / 4 h / 4 h | 4 meses; até 3 ciclos | NC | critérios locais (padrão do imóvel até 100 m², renda até 1,5 salário mínimo) |

Fontes dos valores: levantamento `08` (seção d.2) e anexos A a F desta pasta; onde as duas fontes divergem, vale o anexo, que traz a leitura mais recente. Para AGERGS, AGEPAR, ADASA e AGR (GO), parte dos valores vem do `08` e foi mantida porque os anexos não a contradisseram.

### 4.1 Parâmetros que mais variam e tratamento sugerido ao perfil

| Parâmetro | Faixa encontrada | Tratamento sugerido |
|---|---|---|
| Juros de mora | 0,033% ao dia; 0,03% ao dia; 1% ao mês pro rata; 0,5% ao mês (SAMAE Caxias); percentual remetido à lei municipal (ARES-PCJ, ARIS-MG, CISAM) | `fee.interest_daily_pct` ou mensal, com base de cálculo e teto da ERI; vigência por norma; sem capitalização |
| Multa | 2% (maioria); 3%, 6% e 9% por faixa (AMAE Marília); 2% até 30 dias e 5% depois (ORCISPAR); 0,33% ao dia até 10% (ARESPCAB) | padrão 2%, com teto configurável e aceite para valor maior |
| Correção | IPCA (SC, PR, CE, MS, PA, TO, RJ), INPC (DF, GO, BA), Selic (MG), índice do reajuste (RS), sem previsão (PE) | índice por tenant, com fonte e periodicidade |
| Prazo em dias corridos ou úteis | corridos (ARIS), úteis (ARESC, ARSP, GO e RJ nos prazos de resposta), não definido (AGIR, SP) | atributo `business_days` em cada prazo do catálogo de serviços |
| Aviso de corte | 30 dias (maioria); 15 dias (ARSAL); 45 a 60 dias para classes protegidas; atraso mínimo de 60 dias (ADASA); teto de 90 a 120 dias de idade da dívida | piso federal de 30 dias travado; atraso mínimo e idade máxima como parâmetros da ERI |
| Dias e horários vedados | sexta a domingo, feriado e véspera (MG, PR, DF, RS, PA, RJ por lei estadual) contra até 12 h ou 17 h de sexta (SC, CE, GO, PE, TO) | travar no piso da Lei 14.015/2020; guardar o valor local como informativo e exibir a divergência ao tenant |
| Religação | 24 h a 120 h (CISAM-SUL), 1 dia útil a 72 h; urgência de 4 h a 24 h; indevida de 3 h a 24 h | três prazos por ERI, em horas, com marca de útil ou corrido |
| Média sem leitura | 4 a 12 meses; teto de 3 a 6 ciclos; regra posterior (consumo mínimo, 50%/100%/150%, parcelamento do saldo) | `meter.average_months`, `meter.max_estimated_cycles`, `meter.after_cap_rule` |
| Tarifa social | 50% até 15 m³ (piso) a 30 m³ (DF); 25% adicional de 15 a 20 m³ (PCJ); sem limite (MG); critérios locais; base CadÚnico mensal ou trimestral | importador de base por ERI, teto de consumo e escala de desconto configuráveis; recusar regras que retirem o benefício por inadimplência ou corte |
| Relatórios à ERI | mensal até o dia 25 (ARSAE-MG), trimestral (AGERGS, AGIR, AGENERSA, ARIS), semestral e anual (AGEPAR), anual (ADASA) | exportador por agência com layout, periodicidade e canal |
| Compensação por descumprimento | crédito de 2x o faturamento (ARSAE-MG), 20% do consumo médio (ADASA), maior entre 2x a urgência e 20% da fatura (ARSESP, AGIR, ARESC, ARCE), desconto por interrupção de 12 h ou mais (AGERGS) | motor declarativo: gatilho, base de cálculo, multiplicador |

## 5. Adoção das NR da ANA: resumo

| ERI | Adoção (ciclo 2025: NR 3, 5, 6 e 8) | NR 9 a 16 e observações |
|---|---|---|
| ARIS (SC) | sim (NR 3, 5, 6 e 8; 210 municípios de água e 197 de esgoto) | atos próprios para NR 1, 3, 5, 7, 8 e 9; parcial para NR 6, 10, 11 e 13; sem ato para NR 4, 12, 14, 15 e 16 |
| AGIR (SC) | sim (16 municípios) | NR 1, 3, 5, 6, 7, 8 e 9 por ato próprio; não adotou NR 10, 11, 13, 15 e 16 |
| ARESC (SC) | não consta na lista conjunta de 2025 | em 15/10/2026 adotou NR 3, 5 e 8 (Res. 433, 432 e 430/2026); não adotou NR 6, 9, 10, 11 e 13 |
| CISAM-SUL, CISAM Meio Oeste (SC) | sim (NR 3, 5 e 8) | CISAM Meio Oeste: NR 11 (RN 05/2026, efeitos em 20/05/2027) e NR 13 (RN 04/2026) |
| ARSESP (SP) | parcial (NR 3) | NR 8 e 9 na agenda 2026-2027; NR 11 sem ato |
| ARSAE-MG (MG) | sim (635 municípios) | NR 9 parcial (Res. 205/2025); NR 13 citada na Res. 227/2026; NR 11 sem ato |
| ARES-PCJ (SP/MG) | sim (77 municípios) | NR 3, 4, 5, 7, 8 e 9 por ato próprio; NR 10, 11, 13 e 15 não listadas |
| AGENERSA (RJ) | parcial (NR 5 e 6 desobrigadas) | NR 4 por IN 124/2024; demais sem ato |
| AGERGS (RS), AGEPAR (PR) | sim (NR 3, 5 e 8; NR 6 desobrigada) | afirmam ter atendido todas as NR no ciclo de 2025, o que se limita às quatro NR verificadas; NR 11 e 13 sem ato geral |
| ADASA (DF) | parcial (só NR 8 cumprida) | NR 13: art. 67-A da Res. 42/2024 |
| AGR (GO) | sim (223 municípios) | NR 9: RN 366/2026 e resoluções conjuntas; NR 11 sem ato |
| AGEMS (MS) | sim (69 municípios) | Port. 320/2025 (condições gerais) não cita a NR 11 nos trechos lidos |
| AGER/MT, ARIS-MT, AGERSINOP, Cuiabá Regula (MT) | variável (AGER/MT: só NR 8) | AGER/MT RN 004/2026 sem menção à NR 11 |
| ARCE (CE), ARPE (PE) | ARCE: NR 8 em 183 municípios; ARPE: 172 municípios | ARCE com minuta de condições gerais pela NR 11 em audiência |
| AGERSA (BA) | sim (344 municípios) | NR 9: Res. 002/2026; NR 10 em audiência |
| ARSAL (AL) | parcial em 2025 (NR 5 e 6 desobrigadas) | Res. 274, 275, 276 e 282/2026 em jul. e ago. de 2026 |
| ARSP e ARIES (ES) | sim (52 e 21 municípios) | ARSP: NR 3, 5, 6, 8, 9; revisão da Res. ARSI 008/2010 pela NR 11 prevista, sem consulta localizada |
| Demais ERIs (N, NE e municipais) | ver anexos E e F | a matriz completa ERI x NR 1 a 16 está no anexo F |

Atos de adoção das NR pelas ERIs de SC, conforme os preâmbulos e as listas lidos: ARIS (NR 1: RN 49/2025; NR 3: RN 52/2025; NR 5: RN 44/2025; NR 6, parcial: RN 50/2025; NR 7: RN 46/2025; NR 8: RN 36/2022, alterada pelas RN 43/2025 e 54/2026, RN 42/2025 e RN 47/2025; NR 9: RN 57/2026; NR 10, parcial: RN 51/2025; NR 11, parcial: RN 19/2019, RN 45/2025, RN 47/2025 e RN 55/2026; NR 13, parcial: RN 56/2026); AGIR (NR 1: RN 018/2025; NR 3: RN 019/2025; NR 5: RN 020/2025; NR 6: RN 021/2025; NR 7: RN 022/2025; NR 8: RN 023 e 024/2025; NR 9: RN 025/2026); ARESC (NR 8: Res. 430 e 431/2026; NR 5: Res. 432/2026; NR 3: Res. 433/2026); CISAM Meio Oeste (NR 3 a 8: RN 01 a 06/2025; NR 13: RN 04/2026; NR 11: RN 05/2026); AGR-Tubarão (NR 7: Res. 044/2025; NR 8: Res. 045/2025 e 048/2026; NR 9: Res. 049/2026). As ressalvas estão nos anexos A e B e no JSON.

## 6. Perfil regulatório de Santa Catarina a pré-carregar

1. **Hierarquia de camadas.** Lei federal (piso travado) sobre lei estadual (Lei SC 16.673/2015 art. 21 p.u.; Lei 11.959/2001 com a redação da Lei 17.780/2019, alcance sobre prestador municipal em dúvida) sobre norma da ERI sobre regulamento ou contrato do prestador homologado pela ERI.
2. **Perfil por município.** A CASAN responde a ARESC, ARIS, AGIR e CISAM-SUL conforme o município; o mesmo tenant pode precisar de perfis distintos.
3. **Convergências das três agências** (RN 19/2019 da ARIS, RN 001/2013 da AGIR, Res. 046/2016 da ARESC): aviso de corte de 30 dias; 6 datas de vencimento; vencimento mínimo de 5 dias úteis após a apresentação (10 para a categoria pública); ciclo de leitura de 27 a 33 dias; multa de 2%; juros de até 0,033% ao dia; IPCA; religação em 48 h, urgência de 4 h e corte indevido em 4 h; vazamento oculto com desconto de até 70%; média de 6 meses por até 3 ciclos.
4. **Diferenças que mudam o código.** Contagem de prazos (ARIS corridos; ARESC úteis; AGIR indefinido); regra após a média (ARIS e AGIR: consumo mínimo sem compensação; ARESC: saldo parcelado em 3 vezes); aviso de 24 h após o corte (ARIS e AGIR sim; ARESC não consta); limite de 120 dias de idade da dívida para cortar (só ARIS); prazos rurais de ligação (ARESC 4 e 6 dias úteis; ARIS 7 e 10); crédito por corte indevido (AGIR e ARESC: maior entre 2x a urgência e 20% da fatura; ARIS: religação em 4 h sem ônus); datas de vencimento (CISAM-SUL: mínimo de 4); atendimento prioritário a partir de 60 anos (AGIR e CISAM-SUL) ou 65 (ARESC).
5. **Regras locais a recusar.** Corte permitido até 12 h de sexta (ARIS art. 158, AGIR art. 148, ARESC art. 85) e perda da tarifa social após dois cortes (AGIR RN 001/2013, art. 127 §7º).
6. **Relatórios.** AGIR exige relatório trimestral de atendimento sem dados pessoais (RN 013/2022, até o 30º dia do mês seguinte); ARIS, indicadores pela RN 57/2026 e dados de potabilidade no SISARIS até o dia 20; ARESC, penalidades pela Res. 195/2022.

## 7. Divergências com os levantamentos 08, 20f e 20g

| Tema | O que os levantamentos dizem | O que esta pesquisa encontrou |
|---|---|---|
| Textos da AGIR e da ARESC | `20g`: condições gerais da AGIR e da ARESC não localizadas | O `20f` está correto: AGIR RN 001/2013, 012/2021 e 013/2022 e ARESC 046/2016 consolidada, 313/2024 e 348/2025 foram lidas. O `08` não traz AGIR nem ARESC |
| Correção na ARIS | `20g`: INPC no art. 108 | RN 19, art. 108: IPCA (ou outro índice legal). O `08` e o `20f` estão certos |
| Média sem leitura na ARIS | `20g`: limite de 2 faturas em 12 meses | O limite está no art. 89 §8º e vale só para o exame de reequilíbrio pela ARIS; a média é de 6 meses por até 3 ciclos (art. 83) |
| Fornecimento de emergência na ARIS | `20g`: 18 horas | 24 horas desde a RN 55/2026 |
| Penalidades e conexão de esgoto na ARIS | numeração incerta | Penalidades: RN 18/2019 (a RN 17/2019 trata de procedimentos de fiscalização); conexão e disponibilidade de esgoto: RN 42/2025; a RN 55/2026 é de 15/04/2026 |
| Numeração de artigos da AGIR no `20f` | arts. 126 §7º, 108, 95-96, 121 p.u. | No PDF: art. 127 §7º (tarifa social), 102 (média), 94 (aferição), 118 p.u. (vencimento), 36 (prazos de ligação) |
| Média na ARESC | `20f`: idem ARIS (consumo mínimo) | Saldo positivo parcelado em 3 vezes (art. 90 §4º); média no art. 90 e não no 92; comunicação de 24 h após o corte não consta na Res. 046 consolidada |
| Limite de 120 dias | `20f`: agrupa ARIS, AGIR e ARESC | AGIR (art. 47 §1º IV) só para condicionar ligação e religação; ARESC (art. 6º) não o traz |
| Leis estaduais de SC | não citadas | Lei 11.959/2001 (redação da 17.780/2019) e Lei 16.673/2015 art. 21 p.u. vedam o corte de sexta a domingo, véspera e feriado; alcance a confirmar |
| ARSESP, encargos | `08`: juros de até 0,033% ao dia | Del. 1.791/2026 (15/04/2026): multa de até 2%, juros de até 1% ao mês pro rata, IPCA. Del. 1.836/2026 (leitura de 27 a 33 dias, proporcionalização; vigor por volta de 23/10/2026) não consta no `08`. Del. 1.636/2025 é o Manual de Controle Patrimonial e a 1.751/2025 trata de ressarcimento a empreendedores. O PDF integral citado no `08` está consolidado só até 12/2018 |
| ARSAE-MG | `08`: alterações 140/2020, 189/2024, 212/2025 e atende SAAEs | 140/2020 muda vigência; 189/2024 é o programa de subvenções da Copasa; 212/2025 trata de soluções alternativas; a agência mudou de nome e hoje lista Copasa, Copanor, Sanarj e Samotracia |
| AGENERSA | `08`: multa não confirmada | Decreto 48.225/2022, art. 10, item 7: multa de 2%, juros de 1% ao mês, IPCA. IN 148/2026 trata da Lei 12.007/2009 |
| AGERSA no RJ | pedido original | Não há; AGERSA é da Bahia |
| AGERGS RN 66/2022 | `08` E-14: ambiguidade do aviso | Art. 109 §13 III fixa 30 dias para inadimplência; a RN é de 09/08/2022 |
| AGEPAR Res. 3/2020 | `08` E-16: vigência condicionada | Decreto 3.926/1988 revogado pelo Decreto 8.182/2021; regulamento em vigor; art. 122 tem 19 itens |
| ADASA, 30 m³ | `08` E-15 | Limite permanece no art. 101, II; Res. 70/2026 mantém o desconto até 30 m³. Lei Distrital 5.990/2017 consta como declarada inconstitucional |
| ARCE, ARPE | `08`: ARCE 32 municípios; ARPE 172 | 32 é o número que atendeu o conjunto das NR; individualmente a ARCE aparece na NR 8 com 183 municípios; ARPE agora tem Res. 085/2013 e 292/2025 |
| ARSAL | `08`: só NR 5 e 6 | Em jul. e ago. de 2026 editou Res. 274, 275, 276 e 282 |
| Data da NR 11 | `08` E-18 | 20/05/2027 (texto do art. 74 §3º); a notícia da ANA com 27/05/2027 diverge do ato |
| Data da tarifa social | `08` E-03 | 11/12/2026 pela vigência da Lei 14.898 em 11/12/2024 (Aviso da ANA no DOU de 21/09/2026); 10/12/2026 em agências que contaram 10/12/2024 |
| Espírito Santo | não pesquisado | ARSP e ARIES mapeadas; Linhares passou à ARSP (Convênio 001/2026); Sooretama em duas ERIs |

---

# Anexos: quadros por ERI

As remissões "parte N" que aparecem dentro dos anexos correspondem aos anexos: parte 1 = A, parte 2 = B, parte 3 = C, parte 4 = D, parte 5 = E, parte 6 = F (a remissão "6B" indica o apêndice B do anexo F).

## Anexo A — ARIS (SC) e adoção das NR

Data da pesquisa: 22/10/2026. Fontes lidas: site oficial da ARIS (páginas de legislação, categorias 8, 9, 10, 11, 17, 18, 22, 24, 25, 26 e 27; relatórios; agenda regulatória 2026), texto integral ou parcial das resoluções no Diário Oficial dos Municípios de SC (DOM/SC), planilhas da lista positiva da ANA de 22/12/2025 e páginas da ANA. O inventário dos atos consta em `04-eris.json` (37 itens). Marca usada abaixo: **[C]** confirmado no texto lido; **[N]** não confirmado.

### 1. Quadro da ARIS

| Item | Informação |
|---|---|
| UF | SC |
| Nome | ARIS - Agência Reguladora Intermunicipal de Saneamento (associação pública, consórcio; CNPJ 11.400.360/0001-05; sede em Florianópolis; escritórios regionais em Chapecó, Videira, Lages, Maravilha, Rio do Sul e Joinville) [C] |
| Abrangência | 211 municípios consorciados, segundo o site e a Agenda 2026 [C]. A planilha da ANA de 2025 lista 210 municípios no componente água e 197 no componente esgoto [C]. Prestadores: múltiplos, entre eles CASAN, SAMAEs e autarquias municipais, prefeituras com prestação direta e concessionárias privadas (por exemplo BRK Ambiental em Caçador, Companhia Águas de Joinville, Jaguaruna Saneamento, Águas de Penha, Águas de São Francisco do Sul, Itapoá, Gravatal) [C pelas fiscalizações e deliberações]. A contagem exata de prestadores não foi feita [N]. Também regula resíduos sólidos (7 municípios na lista da NR 1 da ANA); a RN 41/2024 menciona ainda drenagem. |
| Condições gerais | RN 19/2019 (27/03/2019), compilada, alterada pelas RN 20/2019, 38/2023, 40/2024 e 55/2026 [C]. Revogou a RN 1/2011 [C]. |
| Fiscalização e penalidades | RN 17/2019 (fiscalização, 20/02/2019) e RN 18/2019 (penalidades, 27/03/2019) [C]. A RN 18 é a norma de penalidades; a RN 17 é a de procedimentos. |
| Tarifa | Reajuste: RN 26/2021 (alterada pela RN 51/2025). Revisão: RN 31/2021 (alterada pela RN 53/2025). Modelo contratual: RN 50/2025. Indenização: RN 52/2025. Tarifa social: RN 56/2026. Tarifa de disponibilidade de esgoto: RN 42/2025 [C]. Não existe RN geral de estrutura tarifária; as tabelas saem de deliberações por prestador (categoria 17 do site) [C]. |
| Informações e indicadores | RN 33/2021 (contábeis), RN 36/2022 (metas; alterada pelas RN 43/2025 e 54/2026), RN 57/2026 (indicadores; revogou a RN 8/2016), IN-DG 001/2025 (SISARIS, potabilidade) [C]. |
| Adoção das NR da ANA | **Parcial**: sim para NR 1, 3, 5, 7, 8 e 9; parcial para NR 6, 10, 11 e 13; nenhum ato localizado para NR 4, 12, 14, 15 e 16 (detalhe na seção 2) [C quanto aos atos lidos; N quanto a atos não localizados]. |
| Lista positiva da ANA (22/12/2025) | ARIS consta como cumpridora das NR 3, 5, 6 e 8 em 210 municípios (água) e 197 (esgoto), com NR 6 "desobrigada" em 195 dos municípios de esgoto, e da NR 1 em 7 municípios de resíduos [C, planilha oficial]. A ANA não verificou nesse ciclo as NR 9, 10, 11, 13, 15 e 16. |

### 2. Adoção das NR da ANA pela ARIS (fonte: preâmbulos dos atos da ARIS)

| NR (resolução ANA) | Tema | Adoção pela ARIS | Ato(s) | Observação |
|---|---|---|---|---|
| NR 1/2021 (Res. 114/2021) | Cobrança de resíduos sólidos | Sim | RN 49/2025 | Fora do núcleo água e esgoto. Lista positiva 2025: cumpriu em 7 municípios. |
| NR 2/2021 (Res. 106/2021) | Aditivos de contratos | Histórica | Citada na RN 39/2023 e na IN-DG 001/2022 | Revogada pela NR 8/2024. |
| NR 3/2023 (Res. 161/2023) | Indenização de investimentos | Sim | RN 52/2025 | Lista positiva 2025: cumpriu. |
| NR 4/2024 | Governança das ERIs | Nenhum ato localizado [N] | Decretos 007 e 008/2026 alteraram Regimento e Protocolo de Intenções sem citar a NR | Prazo de mandatos prorrogado pela ANA até 2028 (notícia lida por busca; não conferida na ANA). |
| NR 5/2024 (Res. 178/2024) | Matriz de riscos | Sim | RN 44/2025 | Lista positiva 2025: cumpriu. |
| NR 6/2024 (Res. 183/2024) | Modelos de regulação tarifária | Parcial | RN 50/2025 (contratual) | A Agenda 2026 ainda prevê o modelo discricionário. |
| NR 7/2024 (Res. 187/2024) | Condições gerais de resíduos | Sim | RN 46/2025 | Desobrigada na lista de 2025. |
| NR 8/2024 (Res. 192/2024) | Metas de universalização | Sim | RN 36 (alt. RN 43/2025, 54/2026), RN 42/2025, RN 47/2025 | Lista positiva 2025: cumpriu. |
| NR 9/2024 (Res. 211/2024) | Indicadores operacionais | Sim | RN 57/2026 | Ainda não verificada pela ANA em ciclo publicado. |
| NR 10/2024 (Res. 228/2024) | Reajuste tarifário | Parcial | RN 51/2025 (altera a RN 26) | Comparação integral com a NR 10 não feita. |
| NR 11/2024 (Res. 230/2024) | Condições gerais água e esgoto | Parcial | RN 19 compilada, RN 45/2025, RN 47/2025, RN 55/2026 | Verificação pela ANA a partir de 20/05/2027 (art. 74 §3º da NR 11). Não há ato único de adoção. |
| NR 12/2025 (Res. 245/2025) | Estruturação da drenagem | Nenhum ato localizado [N] | - | Fora do núcleo água e esgoto. |
| NR 13/2025 (Res. 271/2025) | Estrutura tarifária e tarifa social | Parcial | RN 56/2026 (tarifa social) | Estrutura tarifária geral e transparência tarifária sem ato [N]. |
| NR 14/2025 (Res. 276/2025) | Indicadores de resíduos | Nenhum ato localizado [N] | - | Verificação em maio de 2029 (notícia da ANA lida por busca). |
| NR 15/2025 (Res. 275/2025, 18/12/2025) | Perdas na distribuição | Nenhum ato localizado [N] | RN 36 e RN 57 tratam de perdas (Nível I-01) sem adotar a NR 15 | Verificação a partir de maio de 2028 (notícia da ANA lida por busca). |
| NR 16/2026 (Res. 302, 28/08/2026) | Contabilidade regulatória e controle patrimonial | Nenhum ato localizado [N] | RN 33/2021 antecede a NR | Prazo de 3 anos para as ERIs, até 31/08/2029 (notícia da ANA lida por busca). |

Fontes: preâmbulos de RN 36, 39, 42 a 52, 54 a 57 em https://www.aris.sc.gov.br/legislacao?cat=8 e https://www.aris.sc.gov.br/legislacao?cat=27; ANA, lista positiva de 22/12/2025. A categoria "Normas de Referência - ANA" do site da ARIS está vazia.

### 3. Parâmetros que variam e que o Ziip SAN deve pré-carregar (ARIS)

Referência: RN 19/2019 compilada (artigos conforme o texto lido), salvo indicação.

| Parâmetro | Valor ARIS | Artigo | Marca |
|---|---|---|---|
| Juros de mora | Até 0,033% ao dia (teto; o prestador pode adotar menos) | RN 19 art. 108 | [C] |
| Multa por atraso | 2% | art. 108 | [C] |
| Correção monetária | IPCA (IBGE) ou outro índice previsto na legislação vigente | art. 108 | [C] (o doc. 20g cita INPC: divergência; o texto traz IPCA) |
| Contagem de prazos | Dias corridos, salvo previsão expressa de dias úteis | art. 171 (redação da RN 38/2023) | [C] |
| Prazo de resposta ao usuário | 15 dias (corridos); número de protocolo obrigatório | art. 23 e §1º | [C] |
| Vistoria e ligação | Urbano: vistoria 5 dias úteis, ligação 10 dias úteis; rural: vistoria 7 dias úteis, ligação 10 dias úteis; informar motivo da reprovação em 3 dias úteis | art. 26 | [C] |
| Aviso prévio de corte por inadimplência | 30 dias, por escrito, discriminando as faturas | art. 153 §§1º e 5º | [C] |
| Teto de idade da dívida para cortar | Vedado cortar por tarifas vencidas há mais de 120 dias | art. 153 §2º | [C] |
| Dias e horários vedados | Vedada interrupção após 12 h de sexta-feira e na véspera de feriados; sábado, domingo e o próprio feriado não são mencionados | art. 158 | [C]. Conflita com a Lei 14.015/2020 (veda iniciar corte em sexta, sábado, domingo, feriado e véspera): usar o piso federal e recusar o parâmetro local mais frouxo |
| Serviços e usuários protegidos | Saúde, educação, internação coletiva e tarifa social: prazos e critérios da lei federal ou norma específica; serviços essenciais listados: aviso de 30 dias à ARIS | arts. 156 e 157 | [C] |
| Aviso após o corte | Informar motivos e providências em 24 h | art. 152 §1º (RN 55/2026) | [C] |
| Religação | Até 48 h após pagamento ou cessação do motivo | art. 160 | [C] |
| Religação de urgência | Facultativa, 4 h; se adotada, vale para qualquer usuário e exige informar valores e prazos | art. 161 | [C] |
| Religação por corte indevido | 4 h, sem ônus | arts. 25 III, 153 §7º, 107 §6º | [C] |
| Taxa de religação de tarifa social ou baixa renda | Vedada após a quitação da dívida | art. 107 §4º (RN 38/2023) | [C] |
| Datas de vencimento | 6 opções ao usuário | art. 84 §4º | [C] |
| Prazo mínimo entre apresentação e vencimento | 5 dias úteis; 10 dias úteis para categoria Pública; 1 dia útil em desligamento a pedido | art. 98 §1º | [C] |
| Intervalo de leitura | 27 a 33 dias; excepcional 15 a 47 com aviso de 30 dias; faturamento inicial entre 15 e 47 dias | art. 84 | [C] |
| Média sem leitura | Média aritmética dos últimos 6 meses, até 3 ciclos consecutivos; depois, se houver saldo positivo, fatura-se pelo consumo mínimo sem compensação futura; se o impedimento é do usuário, mantém-se a média até corrigir | art. 83 §§1º, 2º, 4º e 5º | [C] |
| Vazamento oculto | Desconto de até 70% do volume acima da média; reclamação em até 45 dias da leitura; esgoto pela média de 6 meses quando a água não retorna à rede; prestador define quantas faturas revisar; ARIS avalia no máximo 2 faturas sequenciais em 12 meses só para reequilíbrio | art. 89 §§1º a 8º | [C] |
| Interrupção programada | Aviso de 48 h; fornecimento de emergência a serviços essenciais quando a interrupção passar de 24 h (era 18 h antes da RN 55/2026) | arts. 25 IV, 150 e 151 | [C] |
| Revisão de fatura | Recurso ao prestador em 10 dias, decisão em 10 dias, refaturamento com vencimento em 3 dias úteis, recurso à ARIS em 10 dias; infrações: recurso em 15 dias ao prestador e 15 dias à ARIS | art. 104; art. 148 | [C] |
| Duplicidade de pagamento | Mecanismo de identificação e devolução até o próximo faturamento | art. 101 | [C] |
| Negativação e protesto | Permitidos após esgotadas as medidas administrativas | art. 154 | [C] |
| Parcelamento | Termo de acordo e confissão de dívida; paga a 1ª parcela, o usuário pode pedir o restabelecimento | arts. 110 e 155 | [C] |
| Quitação anual | Declaração com a fatura de maio do ano seguinte ou no mês seguinte à quitação | art. 111 | [C] |
| Histórico no cadastro e registros | 60 ciclos de leitura e faturamento no cadastro; registros de valores e horários de serviços cobrados por 60 meses | arts. 79 VI e 107 §5º | [C] |
| Atendimento | Canal on-line e telefone 24 h, 7 dias, com protocolo, atenuável pela ARIS conforme o porte; atendimento prioritário | arts. 21, 23 e 24 | [C] |
| Pressão | 10 a 50 mca | art. 133 | [C] |
| Qualidade da água | Comunicar anomalia aos usuários em até 6 h; dados de potabilidade no SISARIS até o dia 20 do mês seguinte | art. 129 p.u.; IN-DG 001/2025 art. 1º | [C] |
| Tarifa social: desconto | 50% sobre o valor por m³ até 15 m³ da categoria Residencial; acima disso e sobre a tarifa básica, definição do titular, contrato ou ARIS | RN 56 arts. 21 e 22 | [C] |
| Tarifa social: elegibilidade | Renda per capita até 1/2 salário mínimo e CadÚnico ou BPC (pessoa com deficiência ou idoso de 65+); cadastro atualizado em até 2 anos; adimplência não condiciona; inadimplência não faz perder o benefício | RN 56 arts. 8º, 9º e 11 | [C] |
| Tarifa social: classificação | Automática por CPF; cruzamento mensal e prazo de 30 dias da base; base trimestral da ARIS com termo de sigilo; uma economia por família; recategorização imediata | RN 56 arts. 12 a 16 e 19 | [C] |
| Tarifa social: prazo de implantação | 24 meses da entrada em vigor da Lei 14.898/2024 | RN 56 art. 5º §1º | [C] quanto ao prazo; a data final (11/12/2026 no levantamento) depende da vigência da lei [N] |
| Conexão à rede e disponibilidade | Usuário solicita a conexão em até 30 dias da notificação; mais até 60 dias para adequação; tarifa de disponibilidade na 1ª fatura após o prazo, calculada pelo contrato ou, sem previsão, por tarifa fixa de esgoto ou consumo mínimo mais volume de água | RN 42 arts. 4º, 5º, 8º e 9º | [C] |
| Reajuste | Mínimo de 12 meses; índice do contrato, depois da lei municipal, depois IPCA | RN 26 arts. 3º e 4º | [C] |
| Revisão ordinária | Ciclo de 5 anos, salvo contrato ou justificativa em plano de negócios; fatores X, Q e U | RN 31 art. 3º §3º e art. 2º; RN 53 | [C] |
| Dados regulatórios | Indicadores: envio anual e trimestral (fim do mês seguinte); contábeis: balancete mensal em PDF e Excel; SISARIS mensal até o dia 20 | RN 57 art. 9º; RN 33 art. 7º; IN-DG 001/2025 | [C]; Anexo II da RN 33 com periodicidade exata não localizado [N] |
| Multa a prestador (para relatórios internos) | Pena-base 0,2%, 0,3% e 0,4% do faturamento anual bruto, com tetos de R$ 5.000, R$ 10.000 e R$ 20.000 por infração | RN 18 art. 14 | [C]; atualização monetária dos tetos [N] |
| Limpeza programada de fossas | Agendar vistoria em até 30 dias da notificação; primeira limpeza em até 120 dias após a vistoria; frequência anual com tolerância de 60 dias | RN 39 arts. 5º, 19, 22 e 23 | [C] |

### 4. Divergências com o levantamento existente

1. Correção monetária: o doc. 20g (seção 6) indica INPC no art. 108; o texto compilado atual e a compilação anterior (uploads/pagina/2729) trazem IPCA, "ou outro índice previsto na legislação vigente". O doc. 08 está correto.
2. Média: o doc. 20g atribui à média o limite de 2 faturas em 12 meses; esse limite está no art. 89 §8º, restrito ao exame de reequilíbrio pela ARIS. A regra de média sem leitura é de 6 meses por até 3 ciclos (art. 83), como no doc. 08.
3. Fornecimento de emergência: o doc. 20g cita 18 horas; desde a RN 55/2026 o art. 151 diz 24 horas.
4. Penalidades: a RN de penalidades é a **RN 18/2019**; a **RN 17/2019** é a de procedimentos de fiscalização. As duas existem.
5. Conexão à rede e disponibilidade: número confirmado como **RN 42/2025** (25/06/2025, republicada em 03/07/2025).
6. RN 55/2026: o ato é de 15 de abril de 2026 (publicado em 23/04/2026); o estoque regulatório da ARIS traz "maio" por erro, e a RN 56 traz "2016" por erro de digitação no mesmo estoque.
7. Corte até 12 h de sexta (art. 158): confirmado e em conflito com a Lei 14.015/2020, conforme o doc. 20g. Nenhuma alteração da ARIS (inclusive a RN 55/2026) mexeu no art. 158.
8. Alterações da RN 19: as RN 20/2019, 38/2023, 40/2024 e 55/2026 são as únicas incorporadas à compilação; nenhuma outra foi localizada.
9. A RN 57/2026 revogou expressamente apenas a RN 8/2016; a RN 11/2017 (altera o anexo da RN 8) fica sem objeto, mas sem revogação expressa localizada.

### 5. Observações para o produto

- O perfil ARIS deve ser carregado por tenant com versão e vigência, porque quatro atos (RN 20/2019, 38/2023, 40/2024 e 55/2026) alteraram a RN 19 e a norma precisa ser comparada com a NR 11/2024 antes de 20/05/2027.
- Parâmetros em que a norma fixa só o teto ("até"): juros diários e desconto de vazamento. O valor efetivo vem do regulamento do prestador homologado pela ARIS (RN 19 arts. 163 e 169).
- Itens sujeitos à homologação prévia da ARIS por prestador: contrato de adesão, tabela de preços e prazos, parâmetros de multas ao usuário e critérios de cancelamento de fatura (art. 163). O módulo de configuração deve guardar o ato de homologação.
- Fatura: lista de 17 campos obrigatórios no art. 99, incluindo ouvidorias do prestador e da ARIS, histórico de 6 meses, multa e mora, parcelamento e aviso de alta de consumo; a RN 42/2025 exige aviso e discriminação da tarifa de disponibilidade e a RN 56/2026 exige divulgação mensal da tarifa social na fatura.

## Anexo B — AGIR, ARESC, normas estaduais e outras entidades de SC

Escopo: AGIR, ARESC, atos estaduais de SC sobre saneamento e demais entidades reguladoras de SC, exceto a ARIS (a cargo de outro agente). Consulta feita em 22/10/2026. O arquivo `04-eris.json` traz 72 registros; este documento traz os quadros por entidade, os parâmetros e as divergências.

Legenda das marcas: **C** = confirmado no texto da norma lido nesta rodada; **S** = fonte secundária ou cópia não oficial do ato; **NC** = não confirmado.

Observação de método: os PDFs da AGIR e da ARESC foram baixados dos sites oficiais e lidos por extração de texto. Os PDFs da ARESC que são imagem sem camada de texto (Res. 047/2016 REV, 049/2016, 052/2016, 105/2018, 111/2018, 114/2019, 115/2019, 122/2018, 139/2019 e 142/2019) não puderam ser lidos, pois não há OCR disponível no ambiente; para eles só vale a ementa da página oficial.

---

### 1. AGIR

| Item | Dado |
|---|---|
| UF | SC |
| Nome | AGIR - Agência Intermunicipal de Regulação de Serviços Públicos (até 2019 denominada Agência Intermunicipal de Regulação, Controle e Fiscalização de Serviços Públicos Municipais do Médio Vale do Itajaí); consórcio público, sede em Blumenau |
| Abrangência | 17 municípios na lista de comprovação da ANA de 2025 (Apiúna, Ascurra, Benedito Novo, Blumenau, Botuverá, Brusque, Doutor Pedrinho, Gaspar, Guabiruba, Indaial, Itapema, Luiz Alves, Pomerode, Rio do Sul, Rio dos Cedros, Rodeio, Timbó; 16 na NR 8, sem Itapema). O relatório anual citado no 20g fala em 19 municípios; a lista de associados não foi conferida (NC). Prestadores: SAMAEs de Blumenau, Gaspar, Timbó; BRK Blumenau; Guabiruba Saneamento; Pomerwasser; CONASA (Itapema); CASAN em Rodeio |
| Condições gerais | RN 001/2013, de 30/08/2013 (C), sem consolidação e sem alteração. Complementadas por RN 005/2015 (interrupções programadas), RN 012/2021 (ouvidoria) e RN 013/2022 (atendimento e relatório trimestral) |
| Tarifa, reajuste, revisão | RN 008/2019 (reajuste) e RN 009/2019 (revisão, consolidada), ambas parcialmente revogadas para resíduos pela RN 018/2025; RN 021/2025 (modelos tarifários, NR 6). Reajustes anuais e revisões saem por "Decisão" do Diretor-Geral por prestador, não por norma geral |
| Penalidades | RN 003/2013 (multa em percentual do faturamento anual: pena-base de 0,2% a 0,5%; teto de 1% do faturamento bruto na falta de limite contratual) e RN 002/2013 (rito de fiscalização) |
| Contabilidade | RN 011/2020 (Procedimentos Contábeis Regulatórios, vigência 01/01/2021) |
| Qualidade, indicadores | RN 010/2019 revogada; RN 023/2025 e 024/2025 (metas, NR 8) e RN 025/2026 (indicadores, NR 9) |
| Atendimento | RN 012/2021 e 013/2022 |
| Tarifa social | Nenhuma norma própria da Lei 14.898/2024 na lista de RNs (a última é a 025/2026). A RN 001/2013 trata de tarifa social só nos arts. 96 p.u., 127 §§4º e 7º e 146. Todos os 8 prestadores da AGIR constam como "Concluído" na Lista Positiva da ANA de julho/2026 (C, fonte ANA). A Agenda Regulatória 2026 da AGIR lista condições gerais, penalidades, reajuste, estrutura tarifária, tarifa social e perdas entre os temas a revisar (C) |
| Adoção das NR da ANA | Ver seção 5. Resumo: adotou NR 3, 5, 6, 7 (resíduos), 8, 9 e, para resíduos, NR 1; não adotou NR 10, 11, 13, 15, 16 |

#### Parâmetros da AGIR para pré-carregar (RN 001/2013, PDF lido, salvo indicação)

| Parâmetro | Valor | Artigo | Marca |
|---|---|---|---|
| Juros de mora | até 0,033% por dia | 129 | C |
| Multa por atraso | 2% | 129 | C |
| Correção | IPCA/IBGE ou outro índice previsto em lei | 129 | C |
| Prazo de resposta ao usuário | 15 dias; a RN 001 não diz se corridos ou úteis. Na ouvidoria da AGIR vale o art. 219 do CPC (dias úteis) e a prestadora tem 15 d.u. para se manifestar | 27 §2º, 28; RN 012/2021 art. 1º p.u., 3º §2º | C |
| Prazo de ligação | vistoria 5 d.u.; ligação 10 d.u. (urbana e rural); estudo 30 d.u. quando falta rede; início de obras 45 d.u. | 36, 37, 38 | C |
| Aviso de corte por inadimplência | 30 dias, com discriminação das faturas; vedado corte por impedimento de leitura não notificado | 143 §§1º, 2º, 4º | C |
| Idade máxima do débito para cortar | não há; o limite de 120 dias existe só para condicionar ligação e religação | 47 §1º IV | C |
| Dias e horários vedados | vedado após as 12 h de sexta-feira e na véspera de feriados nacionais, estaduais e municipais. Conflita com a Lei federal 14.015/2020 e é mais frouxo que a Lei SC 11.959/2001 (redação da 17.780/2019) | 148 | C |
| Serviço essencial | aviso de 30 dias à AGIR (hospitais, GLP, gás canalizado, limpeza urbana, presídios); saúde, educação, internação e tarifa social seguem a lei federal | 146, 147 | C |
| Religação | normal 48 h; urgência 4 h (facultativa, preço informado); corte indevido 4 h sem ônus, com crédito do maior entre 2x a religação de urgência e 20% da última fatura | 151, 152, 143 §§6º, 7º | C |
| Comunicação após o corte | 24 h, com motivos e providências | 142 p.u. | C |
| Tarifa social | mais de 2 cortes no ano fiscal retiram a categoria social (contraria a Lei 14.898/2024, travar); sem taxa de religação após a purgação da mora | 127 §7º; 127 §4º | C |
| Média sem leitura | média aritmética de 6 meses, até 3 ciclos consecutivos; depois, consumo mínimo sem compensação futura | 102 §§1º, 2º, 4º | C |
| Datas de vencimento | 6 datas; mínimo de 5 d.u. entre apresentação e vencimento (10 d.u. para categoria Pública, 1 d.u. em desligamento a pedido) | 128; 118 p.u. | C |
| Ciclo de leitura | 27 a 33 dias; excepcional 15 a 47 com aviso por escrito de um ciclo | 103 | C |
| Vazamento oculto | até 70% do volume acima da média; 2 faturas sequenciais em 12 meses; pedido em 30 dias do vencimento | 107 | C |
| Verificação do hidrômetro | gratuita 1 vez a cada 3 anos ou se houver erro; aviso de 3 d.u.; devolução de 12 meses se o medidor estiver fora da norma | 94 | C |
| Relatório à agência | trimestral, até o 30º dia do mês seguinte, sem dados pessoais | RN 013/2022 arts. 3º §4º, 7º | C |
| Interrupção programada | aviso de 48 h (5 d.u. para saúde, educação, internação e essenciais); normalização em 3 h, prorrogável uma vez | RN 005/2015 arts. 4º, 5º | C |
| Negativação e protesto | só após esgotadas as medidas administrativas | 144; 129 §2º | C |

---

### 2. ARESC

| Item | Dado |
|---|---|
| UF | SC |
| Nome | ARESC - Agência de Regulação de Serviços Públicos de Santa Catarina; autarquia especial estadual criada pela Lei SC 16.673/2015 pela fusão da AGESC e da AGESAN |
| Abrangência | Municípios conveniados com a ARESC. Na comprovação da ANA de 2025 aparece como ERI de 8 municípios na NR 3 (Araranguá, Balneário Camboriú, Capivari de Baixo, Imaruí, Imbituba, Itajaí, Papanduva, Porto Belo) e de 36 na NR 6, componente esgoto. Prestadores com resolução recente: CASAN (onde regulada pela ARESC), SEMASA de Itajaí, EMASA de Balneário Camboriú, SAMAE de Araranguá e de Imbituba, Águas de Bombinhas, Águas de Camboriú, EJW Águas, Consórcio Saneamento Capivari. A lista completa de convênios não foi obtida (NC) |
| Condições gerais | Res. 046/2016, de 19/01/2016, consolidada com as Res. 139/2019 e 259/2023 (C). Antecessora: Res. AGESAN 004/2011 (situação NC) |
| Tarifa, reajuste, revisão | Sem norma geral de reajuste; cada reajuste sai por resolução por prestador com índice IPCA (por exemplo, Res. 389/2026, CASAN, 5,80% por IPCA de 15 meses). Estrutura binária com TFDI pela Res. 105/2018 (NC, imagem). Primeira revisão da CASAN: Res. 111/2018 (NC). O reajuste exige análise de até 90 dias, prorrogável, e publicação 30 dias antes de produzir efeitos (Lei SC 16.673/2015, art. 23) |
| Penalidades | Res. 195/2022, de 25/01/2022, consolidada com as Res. 322/2024 e 382/2026 (C); multa em faixas de 0,5% a 100% de R$ 500.000,00 (art. 26 §4º da Lei 16.673) |
| Tarifa social | Res. 313/2024 (provisória) e Res. 348/2025 (procedimento com intermediação junto à Secretaria de Assistência Social) (C). Prestadores de municípios ARESC não constam na Lista Positiva da ANA de julho/2026 (C, fonte ANA) |
| Qualidade, atendimento | Res. 049/2016 (qualidade, NC, imagem); atendimento na própria Res. 046 (arts. 126 a 133) |
| Adoção das NR da ANA | Ver seção 5. Em 15/10/2026 adotou NR 3, 5 e 8 (Res. 433, 432 e 430/2026) e editou a Res. 431/2026 (soluções alternativas de água); consultas públicas 05 e 06/2026 precederam as duas primeiras. Não adotou NR 6, 9, 10, 11, 13, 15, 16 |

#### Parâmetros da ARESC para pré-carregar (Res. 046/2016 consolidada, PDF lido)

| Parâmetro | Valor | Artigo | Marca |
|---|---|---|---|
| Juros de mora | até 0,033% por dia | 104 | C |
| Multa por atraso | 2% | 104 | C |
| Correção | IPCA/IBGE ou outro índice previsto em lei | 104 | C |
| Contagem de prazos | dias úteis (exclui o dia do início, inclui o do vencimento; começa e termina em dia útil) | 153 | C |
| Prazo de atendimento | resposta conclusiva em 10 d.u. no canal telefônico; resposta escrita em 30 dias; cópia gratuita de gravação em 5 d.u.; gravação guardada por 12 meses | 128 §§4º, 6º, 7º; 129 | C |
| Prazo de ligação | urbana: vistoria 5 d.u., ligação 10 d.u.; rural: 4 d.u. e 6 d.u.; estudo em 30 dias quando falta rede | 30, 31 | C |
| Aviso de corte por inadimplência | 30 dias; vedado corte por débito ou impedimento anterior não notificado; corte e pagamento no mesmo dia é interrupção indevida | 79 §§1º, 2º, 5º | C |
| Idade máxima do débito para cortar | não há | 79 | C |
| Dias e horários vedados | vedado após as 12 h de sexta e na véspera de feriados. A Lei SC 16.673/2015 (art. 21 p.u.) é mais protetiva: veda o corte de sexta a domingo, na véspera de feriados e durante feriados | 85; Lei 16.673 art. 21 p.u. | C |
| Serviço essencial | aviso de 30 dias à ARESC | 82 | C |
| Religação | normal 48 h; urgência 4 h facultativa; corte indevido 4 h sem ônus e crédito do maior entre 2x a religação de urgência e 20% da primeira fatura após a religação | 87, 88, 79 §§6º, 7º | C |
| Comunicação após o corte | não há prazo de 24 h na redação consolidada | 78 | C (ausência) |
| Tarifa social | Res. 313/2024: desconto de 50% nos primeiros 15 m3, primeira faixa de ao menos 15 m3, TFDI com desconto integral abaixo de 15 m3 e proporcional acima. Res. 348/2025: prazos de 10 d.u., 20 d.u., 30 dias e 5 d.u.; multa média ou leve. Nenhuma regra de perda do benefício por cortes | 313 art. 1º; 348 arts. 1º a 8º | C |
| Média sem leitura | média aritmética de 6 meses por até 3 ciclos; saldo positivo posterior parcelado em 3 vezes pela faixa tarifária anterior; se o impedimento é do usuário, estimativa sem compensação | 90 §§1º, 2º, 4º, 5º | C |
| Datas de vencimento | 6 datas; mínimo de 5 d.u. (10 d.u. Pública; 1 d.u. em desligamento a pedido) | 103; 100 §1º | C |
| Ciclo de leitura | 27 a 33 dias; excepcional 15 a 47 | 91 | C |
| Vazamento oculto | até 70%; 2 faturas em 12 meses; pedido em 30 dias do vencimento | 97 | C |
| Verificação do hidrômetro | gratuita 1 vez a cada 3 anos; aviso de 3 d.u.; devolução de 3 meses | 70 | C |
| Condicionar ligação a débito de outro imóvel | permitido para o mesmo usuário; sem o limite de 120 dias da AGIR | 6º | C |
| Atendimento prioritário | a partir de 65 anos | 127 §3º | C |

---

### 3. Outras entidades com atuação em SC (exceto ARIS)

| Entidade | UF | Abrangência | Normas de condições gerais e de tarifa | Adoção das NR da ANA | Marca |
|---|---|---|---|---|---|
| CISAM-SUL (CREFISBA, braço regulador do Consórcio Intermunicipal de Saneamento Ambiental do Sul) | SC | 19 municípios na ANA 2025 (Anitápolis, Balneário Rincão, Cocal do Sul, Criciúma, Grão-Pará, Içara, Jacinto Machado, Meleiro, Morro Grande, Nova Veneza, Orleans, Pedras Grandes, Praia Grande, Sangão, Santa Rosa do Sul, São Ludgero, Timbé do Sul, Treviso, Urussanga) | RN 03/2017 (17/10/2017, condições gerais, texto digitalizado); RN 03/2016 (reajuste e revisão, cópia do site é minuta sem data); Resoluções CREFISBA anuais por SAMAE | Na ANA 2025: NR 3, 5 e 8 atendidas; NR 6 desobrigado. Ato de adoção individual não localizado | C (texto) / NC (vigência) |
| CISAM Meio Oeste (CISAM-REG) | SC | 10 municípios na NR 3 (Abdon Batista, Brunópolis, Campos Novos, Capinzal, Herval d'Oeste, Joaçaba, Luzerna, Ouro, Vargem, Zortéa) | Não localizei a resolução de condições gerais. O site cita a Res. CISAM MO 23/2020 e as Res. 55 e 58/2025 em contratos de laboratório | Na ANA 2025: NR 3 e 5 atendidas; NR 8 atendida em 8 municípios; NR 6 desobrigado | NC |
| AGR-Tubarão (Agência Reguladora de Saneamento, Iluminação Pública e Mobilidade de Tubarão) | SC | Município de Tubarão (concessão 038/2012 com Tubarão Saneamento S.A.) | Res. 007/2013 (serviço adequado, indicadores), Res. 040/2024 (enquadramento de economias), Res. 043/2025 (tarifa social: teto de 4% das economias, faixas 0-10 e 11-20 m3), Res. 046/2025 (penalidades). Condições gerais comerciais não localizadas | Res. 048/2026 (NR 8) e 049/2026 (NR 9) de 20/08/2026; Tomada de Subsídios 001/2026 sobre NR 3. Na ANA 2025 só consta como desobrigada nas NR 5 e 6 | C |
| ARIS | SC | 210 municípios | outro agente | outro agente | - |

Observação: a CASAN é regulada por quatro agências ao mesmo tempo (ARESC, ARIS, AGIR e CISAM-SUL), conforme a tabela tarifária da própria CASAN de abril/2026. O mesmo prestador pode, portanto, exigir perfis de agência diferentes por município dentro de um único tenant (S, site da CASAN).

---

### 4. Normas estaduais de SC e atos do poder público

| Norma | Situação confirmada | O que importa ao Ziip SAN | Marca |
|---|---|---|---|
| Lei 13.517/2005 (Política Estadual de Saneamento) | Parcialmente revogada pela Lei 17.055/2016; alterada também pela 16.940/2016 | Moldura institucional; sem regra comercial | C (ALESC) |
| Lei 17.055/2016 | Vigente | Idem; art. 12 II prevê prestação regionalizada | C |
| Lei 16.673/2015 (ARESC) | Parcialmente revogada (Leis 18.303/2021 e 19.374/2025); alterada por mais seis atos | Art. 21 p.u.: vedação de corte de sexta a domingo, véspera e feriados; art. 23: ritos de reajuste; art. 26 §4º: multa até R$ 500.000,00 | C |
| Lei 11.959/2001 e Lei 17.780/2019 | Vigência e alcance a confirmar: o texto compilado da ALESC mostra a redação original; a redação de 2019 veda o corte entre 08 h de sexta e 08 h de segunda e antes de feriados e pontos facultativos | Regra estadual mais protetiva que AGIR (art. 148) e ARESC (art. 85); o STF (ADI 2337, 2340, 3824) tem afastado leis estaduais sobre concessões de titularidade municipal | C (texto) / NC (alcance) |
| Lei 17.933/2020 | Efeito encerrado em 31/12/2020 | Parcelamento de débitos de mar-abr/2020; saldos antigos | C |
| Lei 12.465/2002 | Vigente; ADI 2879 julgada improcedente em 18/09/2023 | Tempo de espera presencial: 15 min em dia útil comum, 30 min em véspera e dia seguinte a feriado prolongado; multas progressivas | C |
| LC 484/2010 | Revogada (Lei 16.673/2015) | Histórico da AGESAN | C |
| Decretos 1.035/2008 e 2.138/2009 (CASAN) | NC na ALESC; lidos por cópia da CASAN e por decisão judicial | Tarifa de esgoto até 100% da de água (50% se só coleta); regulamento comercial da CASAN | S |
| Regionalização de água e esgoto (blocos, MIRAE-SC) | Não há lei estadual. PLC 40/2023 foi retirado em 17/02/2025 e arquivado em 20/02/2025; PL 231/2025 tramita; SC está entre os sete estados sem governança regional instalada (IAS, setembro/2026); prazo federal 31/12/2027 | Sem efeito em parâmetros hoje; monitorar | C (e-Legis) / S (IAS, Brasil 61) |
| Tarifa social, prazo de atendimento, religação, taxa | Nenhuma lei estadual específica localizada além das citadas | Vale a lei federal 14.898/2024 e as normas das agências | NC |

---

### 5. Adoção das Normas de Referência da ANA (NR 1 a 16)

Fontes: página de NR da ANA (gov.br/ana, Normas de Referência), página de comprovação da adoção (ciclo 2025, listas de 20/12/2025 em xlsx) e os atos das agências. A Res. ANA 134/2022 exige comprovação por ato normativo ou por ações; a NR 11 só tem verificação a partir de 20/05/2027 (art. 74 §3º, texto da Res. ANA 230/2024), e não em 27/05/2027, como diz uma notícia da ANA de janeiro/2025; a NR 13 só a partir de 20/05/2028.

| NR | Tema (Res. ANA) | AGIR | ARESC | Demais SC |
|---|---|---|---|---|
| 1/2021 | Cobrança de resíduos (79/2021) | RN 018/2025 (C, citada na RN) | NC | - |
| 2/2021 | Metas (revogada pela NR 8) | - | - | - |
| 3/2023 | Indenização de ativos (161/2023) | RN 019/2025 (C); atendida na ANA 2025 | Res. 433/2026 (C); em 2025 constava como desobrigada em 8 municípios | CISAM-SUL e CISAM Meio Oeste atendidas na ANA 2025; AGR-TB em consulta (TS 001/2026) |
| 4/2024 | Governança de ERIs (177/2024) | Agenda 2026 prevê adequação (C); ato NC | NC | NC |
| 5/2024 | Matriz de riscos (178/2024) | RN 020/2025 (C); atendida na ANA 2025 | Res. 432/2026 (C); ausente da lista ANA 2025 | CISAM-SUL, CISAM Meio Oeste atendidas; AGR-TB desobrigada |
| 6/2024 | Modelos tarifários (183/2024) | RN 021/2025 (C); na ANA 2025 consta desobrigada | Nenhum ato localizado; consta desobrigada (esgoto, 36 municípios) | CISAM-SUL desobrigada; AGR-TB desobrigada |
| 7/2024 | Condições gerais de resíduos (187/2024) | RN 022/2025 (listagem) | Consulta Pública 02/2026 (minuta de resíduos) | AGR-TB Res. 044/2025 (listagem) |
| 8/2024 | Metas e indicadores de acesso (192/2024) | RN 023/2025 e 024/2025 (C); atendida em 16 municípios | Res. 430/2026 e 431/2026 (C); ausente da lista ANA 2025 | CISAM-SUL e CISAM Meio Oeste atendidas; AGR-TB Res. 045/2025 e 048/2026 |
| 9/2024 | Indicadores operacionais (211/2024) | RN 025/2026 (C) | Nenhum ato localizado | AGR-TB Res. 049/2026 (listagem) |
| 10/2024 | Reajuste tarifário (228/2024) | Nenhum ato; RN 008/2019 própria; Agenda 2026 prevê avaliar | Nenhum ato; usa IPCA em resoluções por prestador | NC |
| 11/2024 | Condições gerais (230/2024, DOU 22/12/2024) | Nenhum ato; RN 001/2013 vale. Agenda 2026 lista o tema | Nenhum ato; Res. 046/2016 vale | CISAM-SUL: RN 03/2017 vale; sem ato novo |
| 12/2025 | Drenagem (245/2025) | Não se aplica ao escopo | idem | idem |
| 13/2025 | Estrutura tarifária e tarifa social (271/2025, de 21/11/2025) | Nenhum ato; Agenda 2026 lista tarifa social | Res. 313/2024 e 348/2025 são anteriores à NR | AGR-TB Res. 043/2025 anterior à NR |
| 14/2025 | Indicadores de resíduos (276/2025) | NC | NC | NC |
| 15/2025 | Perdas (275/2025) | Nenhum ato; RN 025/2026 inclui índice de perdas por ligação | Nenhum ato | NC |
| 16/2026 | Contabilidade regulatória (302/2026, 28/08/2026, DOU 31/08/2026) | Prazo até 31/08/2029; RN 011/2020 própria | Prazo até 31/08/2029 | idem |

Lista positiva da ANA no ciclo de 2025 (publicada em 22/12/2025, com as NR 1, 3, 5, 6, 7 e 8): na lista conjunta de água e esgoto aparecem em SC apenas AGIR (16 municípios), ARIS, CISAM Meio Oeste e CISAM-SUL; ARESC e AGR-Tubarão não aparecem como atendentes das quatro NR em conjunto. O resultado do ciclo de 2026 ainda não está publicado (a ANA indicava abertura do ciclo até 20/05/2026).

---

### 6. Camada estadual e federal sobre os parâmetros (para travas)

| Parâmetro | AGIR | ARESC | CISAM-SUL | Camada superior |
|---|---|---|---|---|
| Dias vedados para corte | após 12 h de sexta e véspera de feriados | idem | idem | Lei federal 14.015/2020: vedado iniciar o corte de sexta a domingo, em feriado e na véspera (S, texto transcrito em curso jurídico); Lei SC 16.673 art. 21 p.u. (C); Lei SC 11.959/2001 e 17.780/2019 (C texto, alcance NC). Travar no mais protetivo |
| Tarifa social e cortes | art. 127 §7º retira o benefício após 2 cortes no ano | sem regra | art. 110 genérico | Lei 14.898/2024 proíbe; recusar a regra da AGIR |
| Juros e multa | 0,033%/dia e 2% | 0,033%/dia e 2% | legislação municipal e contrato | Teto de multa de 2% do CDC (art. 52 §1º) não verificado nesta rodada |
| Dias úteis ou corridos | RN 001 não define; ouvidoria em úteis (CPC 219) | úteis (art. 153) | RN 03/2016 não define | - |
| Datas de vencimento | 6 | 6 | mínimo 4 | NR 11: datas de vencimento a critério da ERI |

---

### 7. Divergências com os levantamentos 08, 20f e 20g

1. **Existência dos textos.** O 20g (seção 6) diz que não localizou condições gerais da AGIR e da ARESC. O 20f (seção 4) diz que localizou e leu. Confirmo o 20f: AGIR RN 001/2013, 012/2021 e 013/2022 e ARESC 046/2016 consolidada, 313/2024 e 348/2025 estão acessíveis e foram lidos. O 20g está desatualizado nesse ponto. O 08 não contém AGIR nem ARESC (a busca por esses nomes não retornou resultado); o 20f já recomendava acrescentá-las ao quadro de agências.
2. **Numeração de artigos da AGIR no 20f.** No PDF lido: tarifa social e perda por cortes estão nos §§7º e 4º do art. **127** (o 20f cita art. 126); média sem leitura é o art. **102** (o 20f cita 108, que trata de remuneração); verificação de hidrômetro é o art. **94** (o 20f cita 95-96, que tratam de classificação); prazo mínimo de vencimento é o art. **118 p.u.** (o 20f cita 121 p.u.); prazos de ligação são do art. **36** (o 20f remete a tabela homologada do art. 126 §6º, que na verdade é art. 127 §6º). Os demais artigos citados (128, 129, 142 p.u., 143, 147, 148, 151, 152, 107, 47) conferem.
3. **Numeração de artigos da ARESC no 20f.** Média sem leitura é o art. **90** (o 20f cita 92, que trata de leituras plurimensais). Comunicação de 24 h após o corte **não existe** na Res. 046 consolidada (o 20f atribui ao art. 78). Os demais (79, 82, 85, 87, 88, 97, 103, 104, 128, 129, 153, 6º, 70) conferem.
4. **Média sem leitura na ARESC.** O 20f trata AGIR e ARESC como "idem" ARIS (depois do 3º ciclo, consumo mínimo sem compensação). A ARESC é diferente: o saldo positivo é parcelado em 3 vezes pela faixa anterior (art. 90 §4º) e só se estima sem compensação quando o impedimento é do usuário (§5º).
5. **Limite de 120 dias.** O 20f agrupa AGIR, ARESC e ARIS no condicionamento de ligação a débitos. O limite de 120 dias consta na AGIR (art. 47 §1º IV), mas **não** no art. 6º da ARESC.
6. **Contagem de prazos da AGIR.** O 20f marca "não localizado". A RN 012/2021 manda contar os prazos do processo de ouvidoria pelo art. 219 do CPC (dias úteis); a RN 001/2013 não define.
7. **8 d.u. da AGIR.** O 20f os trata como prazo da prestadora na segunda instância; no texto são o prazo do encaminhamento inicial da ouvidoria (art. 2º §6º); a prestadora tem 15 d.u. para se manifestar no processo (arts. 3º §2º e 4º §1º).
8. **Prazos de ligação da AGIR e da ARESC no 20f.** O 20f não registra os prazos da AGIR (5/10 d.u., igual à ARIS) e omite os prazos rurais da ARESC (4 e 6 d.u.).
9. **Leis estaduais de corte.** Nenhum dos levantamentos cita as leis estaduais SC 11.959/2001 (redação da 17.780/2019) e o art. 21 p.u. da Lei SC 16.673/2015, ambas mais protetivas que a regra das agências (corte até 12 h de sexta). O 20f só aponta o conflito com a Lei federal 14.015/2020.
10. **Correção monetária.** O 20g diz que a ARIS corrige pelo INPC (art. 108); o 20f diz IPCA. AGIR (art. 129) e ARESC (art. 104) usam IPCA ou outro índice legal; a RN 008/2019 da AGIR usa INPC no componente de pessoal da fórmula de reajuste. Conferência da ARIS fica com o outro agente.
11. **Datas.** As datas de publicação das páginas oficiais diferem das datas dos atos: AGIR RN 001/2013 (ato 30/08/2013, site 03/10/2013), RN 012/2021 (25/01/2021 e 04/12/2021), RN 013/2022 (26/07/2022 e 03/08/2022); ARESC 313 (ato 18/12/2024, DOE 30/12/2024, site 06/01/2025) e 348 (ato 14/08/2025, site 25/08/2025). O 20f usa as datas do site em alguns casos.
12. **Res. ARESC 313/2024 e 348/2025.** O 20f descreve a 348/2025 como "tarifa social definitiva" e a 313/2024 como provisória. Confirmo a natureza provisória da 313 pelo próprio texto, mas a 348 não a revoga expressamente e não repete os percentuais: a 348 só define a intermediação com a Secretaria de Assistência Social, os prazos e as multas. Os 50% sobre os primeiros 15 m3 e a regra da TFDI continuam vindo da 313 (e da Lei 14.898/2024).
13. **Quadro "Os três reguladores seguem o mesmo modelo".** Vale para a estrutura, mas os parâmetros divergem em pontos que mudam o código: contagem de prazos, média sem leitura, comunicação pós-corte, prazos rurais, limite de 120 dias, idade do atendimento prioritário (60 na AGIR e no CISAM-SUL; 65 na ARESC) e datas de vencimento (4 no CISAM-SUL).
14. **Tarifa social na AGIR.** O 20f diz "nenhuma norma própria da Lei 14.898/2024 localizada" e "sem regra" de cortes. Confirmo; acrescento que o art. 127 §4º garante religação sem taxa a beneficiário que purga a mora.

### 8. Observações para o plano

- Perfil de agência por município, não por tenant: a CASAN responde a quatro agências, e as regras diferem. A chave do perfil deve ser agência e município.
- A AGIR e a ARESC ainda não adotaram a NR 11; ambas têm condições gerais anteriores a 2017. O monitor regulatório deve vigiar a abertura de consulta pública sobre condições gerais nas duas agências até 20/05/2027 e sobre estrutura tarifária e tarifa social até 20/05/2028. A Agenda Regulatória 2026 da AGIR sinaliza esses temas.
- Reajustes da ARESC e da AGIR saem como atos individuais por prestador; o Ziip SAN deve guardar o ato, o índice e a data de aplicação por tenant, sem tabela geral.
- Para cortes e vedações, a recomendação é adotar o padrão mais protetivo (Lei federal e leis estaduais) e travar o que a lei federal proíbe, deixando visível ao tenant a divergência entre a norma da agência e a lei.

## Anexo C — SP, MG e RJ

Data de referência da pesquisa: 22/10/2026. Insumo da fase de plano e documentação do Ziip SAN. Não é parecer jurídico.

Legenda das marcas na coluna final dos quadros de parâmetros:

- **[C]** confirmado nesta rodada, com leitura do texto do ato ou da página oficial.
- **[C08]** consta no levantamento `08-regulatorio-saneamento-e-tarifas.md` ou no `20f` e não foi relido nesta rodada.
- **[NC]** não confirmado ou não localizado.

Fonte das adoções das NR: planilhas "lista positiva 2025" da ANA (`lista-positiva-municipios-2025-conjunto.xlsx`, `-nr3`, `-nr5`, `-nr6`), baixadas da página "Serviços de Abastecimento de Água Potável e Esgotamento Sanitário" (divulgação em 20 a 22/12/2025). Na leitura da ANA, "Desobrigado" indica que o município não precisa ter a ERI cumprindo aquela NR, conforme o tipo de prestação; o "conjunto" reúne as ERIs que cumpriram NR 3, 5, 6 e 8 de uma só vez. O ciclo de 2026 ainda não tem resultado: a Res. ANA 134/2022 prevê comunicação de não observância até 20/10/2026 e divulgação do resultado até 20/12/2026.

### 0. Achados que alteram os documentos 08 e 20f

1. **ARSESP, encargos de mora.** A Del. 1.791/2026 (15/04/2026) reescreveu o art. 79 da Del. 106/2009: multa de até 2% sobre o principal, juros de até 1% ao mês pro rata die e atualização pelo IPCA, sem capitalização. O 08 registra "juros ≤0,033%/dia", valor que deixou de valer.
2. **ARSESP, novas alterações da Del. 106/2009:** Del. 1.791/2026 (renegociação e parcelamento, Política homologada pela ARSESP) e Del. 1.836/2026 (21/08/2026: leitura de 27 a 33 dias, proporcionalização por período regular, parcial e complementar, restrição da média; vigor 60 dias após o DOE de 24/08/2026, cerca de 23/10/2026). Nenhuma consta no 08.
3. **Del. ARSESP 1.751/2025** não trata de condições gerais de cobrança: regula ressarcimento a empreendedores imobiliários e ajusta os arts. 12, 20, 32, 51 e 52 da Del. 106.
4. **Del. ARSESP 1.636/2025** é o Manual de Controle Patrimonial (20/01/2025), não é norma tarifária nem de atendimento.
5. **Texto consolidado da Del. 106:** o arquivo `ldl1062009-integral.pdf` (citado no 08) está consolidado só até 12/2018. O consolidado atual é `Deliberação 106 - SAN.pdf` (60 páginas, inclui 1.751/2025, 1.791/2026 e 1.836/2026).
6. **Tarifa social SP:** o 08 cita base trimestral em CSV (Del. 1.699/2025). O texto lido prevê base mensal (até o dia 5 a ARSESP obtém; até o dia 10 envia à Sabesp). A periodicidade trimestral do retorno em CSV não foi reencontrada. Há ainda a Deliberação CD URAE 1 Sudeste 01/2025 (Tarifa Social Paulista), que não consta no 08.
7. **ARSAE-MG:** as Res. 140/2020, 189/2024 e 212/2025 não são, no mesmo sentido, "alterações da 131". A 140 muda vigência e a Res. 133; a 189 é o Programa de Subvenções Sociais da Copasa; a 212 trata de soluções alternativas e revoga só o § 2º do art. 39. As alterações efetivas da 131 vistas no consolidado de dez/2025 são 151/2021, 156/2021, 165/2022, 179/2023, 212/2025 e 216/2025. A Res. 184/2023 trata de ressarcimento de cobranças indevidas; a Res. 94/2017 trata de atendimento ao público.
8. **ARSAE-MG mudou de nome** (Lei estadual 25.669/2025): "Agência Reguladora de Saneamento e Energia de Minas Gerais", sigla mantida. Hoje a página institucional lista como regulados de água e esgoto Copasa, Copanor, Sanarj e Samotracia; as resoluções de SAAEs e Cesama aparecem como expiradas. O 08 diz "Copasa, SAAEs".
9. **AGENERSA, multa e juros:** o Regulamento (Decreto 48.225/2022, art. 10, item 7) fixa multa de 2%, juros de 1% ao mês (0,03% ao dia) e IPCA. O 08 marcava a multa como não confirmada. **IN 148/2026** trata da comprovação da Lei federal 12.007/2009 (declaração anual de quitação). O programa DESAFOGA e a religação em 5 dias para tarifa social estão na IN 119/2024.
10. **Dias vedados no RJ:** o regulamento não os fixa; aplica-se a Lei estadual 4.824/2006 (vésperas de sábado, domingo e feriado, com exceções).
11. **AGERSA:** nenhuma agência com essa sigla foi localizada no RJ. A "AGERSA" das planilhas da ANA é a Agência Reguladora de Saneamento Básico do Estado da **Bahia**. No RJ existem AGERSAN (Macaé), SANEAR (Resende) e a Fundação Rio-Águas.
12. **Religação ARES-PCJ:** confirmada em 24 h por corte com aviso, 12 h por corte indevido, 72 h por retirada de ramal (art. 115) e urgência facultativa em 4 h (art. 116). A devolução em dobro foi realmente retirada pela Res. 460/2022 (art. 101 §2º).

### 1. ARSESP (SP)

| Campo | Conteúdo |
|---|---|
| UF | SP |
| Nome | ARSESP, Agência Reguladora de Serviços Públicos do Estado de São Paulo (LC estadual 1.413/2024, Decreto 69.339/2025) |
| Abrangência | Sabesp (URAE 1 Sudeste, contrato 01/2024, 371 municípios) e demais prestadores e municípios que delegaram a regulação (concessionárias privadas, SAAEs). Os municípios da bacia PCJ conveniados à ARES-PCJ não estão aqui. |
| Condições gerais | Del. 106/2009, consolidada e alterada por 221/2011, 804/2018, 809/2018, 1.029/2020, 1.117/2021, 1.206/2021, 1.751/2025, 1.791/2026, 1.836/2026. Dados: Del. 1.143/2021. Sanções: Del. 1.600/2024 (alt. 1.612/2024). |
| Tarifa | Sabesp URAE 1: Del. 1.748/2025 (tarifa de equilíbrio) e 1.749/2025 (tarifa de aplicação, vigor 01/01/2026; reajuste de 6,1106% mais 0,3558%). Tabela de Preços e Prazos: Del. 790/2018 (alt. 796/2018), ainda apontada pela página "Serviços e prazos". Outras concessionárias têm deliberação própria de tarifas e de serviços complementares (ex.: 1.696 e 1.697/2025, 1.779 e 1.780/2026). Próximo reajuste da Sabesp: data-base 1º de janeiro, homologação em dezembro (previsão contratual, deliberação ainda não publicada). |
| Tarifa social | Del. 1.544/2024 (Sabesp fora da URAE 1) e 1.699/2025 (URAE 1); Del. CD URAE 1 Sudeste 01/2025 (Vulnerável, Social, Social II). |
| Adoção das NR da ANA | **Parcial.** Lista 2025: NR 3 "cumpriu" (696 registros município por serviço); NR 5 apenas 8 registros "desobrigado"; NR 6 "desobrigado" (698); sem aparição na lista "conjunto" (não cumpriu o conjunto NR 3, 5, 6 e 8 em 2025). Agenda 2026-2027 (Del. 1.764/2025) prevê regulamentar NR 8 e NR 9 (projeto SSR 04, 1º sem. 2026 a 1º sem. 2027), NR 12 e perdas (NR 15, projeto SSR 05). Condições gerais de acordo com a NR 11: não localizado. Fonte: planilhas ANA e Del. 1.764/2025. |

Parâmetros a pré-carregar (ARSESP):

| Parâmetro | Valor | Artigo | Marca |
|---|---|---|---|
| Multa | até 2% sobre o débito principal vencido | Del. 106, art. 79, I (Del. 1.791/2026) | [C] |
| Juros de mora | até 1% ao mês, pro rata die | art. 79, II | [C] |
| Correção | IPCA ou índice substituto; vedada capitalização e encargo sobre encargo | art. 79, III e § 3º | [C] |
| Parcelamento | Política de Renegociação e Parcelamento homologada; escolha entre fatura, cartão e Pix; reparcelamento ao menos uma vez (residencial); parcelas discriminadas na fatura | arts. 83 e 83-A | [C] |
| Prazo de ligação | 10 dias úteis (inspeção em 3 e execução em 7) | art. 19 | [C] |
| Resposta a reclamação | 15 dias, por escrito (unidade "dias corridos ou úteis" não especificada no texto) | art. 110 | [C] (unidade [NC]) |
| Aviso de corte | correspondência específica, emitida em até 90 dias da ocorrência e com antecedência mínima de 30 dias | arts. 88, parágrafo único, e 90 | [C] |
| Saúde, educação e internação coletiva | aviso em até 120 dias da ocorrência e antecedência de 45 dias | art. 93 | [C] |
| Dias vedados para corte | sábados, domingos, feriados nacionais, estaduais e municipais e suas vésperas (exceto irregularidades do art. 87, I) | art. 95 | [C] |
| Horários vedados | nenhum horário específico localizado | — | [NC] |
| Religação | 48 h após cessar o motivo, inclusive por pagamento ou renegociação; urgência em 6 h | arts. 97 e 98 | [C] |
| Religação indevida | 6 h sem ônus; crédito do maior valor entre o dobro da religação de urgência e 20% da primeira fatura após a religação | art. 92, §§ 1º e 2º | [C] |
| Média sem leitura | média de 180 dias; no máximo 3 ciclos consecutivos; depois consumo mínimo; acerto posterior com parcelamento sem juros retroativos (Del. 1.836/2026) | art. 63, §§ 1º a 8º | [C] |
| Intervalo de leitura | 27 a 33 dias; proporcionalização de faixas fora desse limite; período acima de 45 dias não se cobra | arts. 62 e 64 (Del. 1.836/2026) | [C]; vigência exata ≈ 23/10/2026 [NC] |
| Datas de vencimento | 6 datas à escolha do usuário | art. 78 | [C] |
| Antecedência da fatura | vencimento ≥ 5 dias úteis da apresentação (10 d.u. pública; 2 d.u. em desligamento a pedido) | art. 75 | [C] |
| Devolução em dobro | direito de requerer a devolução em dobro dos valores pagos indevidamente | art. 80 | [C] |
| Tarifa social (URAE 1) | Social II: 50% da tarifa residencial nos primeiros 15 m³, por até 2 anos por economia; Vulnerável e Social: descontos contratuais (78% e 72% só em fonte secundária); cadastro CadÚnico/BPC com atualização de até 2 anos; uma ligação por família | CD URAE 1 nº 01/2025 art. 4º; Del. 1.699/2025 arts. 3º a 8º | Social II [C]; 78% e 72% [NC] |
| Cofaturamento de resíduos | divulgação prévia mínima de 60 dias; documento individualizado em 2026; mesma fatura a partir de 01/01/2027 | Del. 1.734/2025 arts. 7º e disposições finais | [C] |

### 2. ARSAE-MG (MG)

| Campo | Conteúdo |
|---|---|
| UF | MG |
| Nome | ARSAE-MG, Agência Reguladora de Saneamento e Energia de Minas Gerais (antes Agência Reguladora de Serviços de Abastecimento de Água e de Esgotamento Sanitário) |
| Abrangência | Copasa, Copanor, Sanarj e Samotracia (página "Sobre"); a ANA lista a ERI com 635 municípios de água e 306 de esgoto; municípios como Montes Claros (AMASBE) publicam as Res. 129, 130 e 131 em seus sites. |
| Condições gerais | Res. 131/2019 (consolidada em 12/2025), 129/2019 (água), 130/2019 (esgoto dinâmico), 94/2017 (atendimento ao público), 151/2021 (ouvidoria), 184/2023 (ressarcimento), 205/2025 (envio de informações), 212/2025 (soluções alternativas). Sanções: Res. 133/2019 e 149/2021 (usuários). |
| Tarifa | Resoluções de reajuste por prestador (ex.: 217/2025 Copasa, 218/2025 Copanor, 215/2025 Sanarj, 219/2025 Samotracia, 232/2026 Copasa Rio Pomba); Tabelas de Preços e Prazos (226/2026 Copasa, 222/2026 Copanor). |
| Tarifa social | Res. 227/2026 (vigor imediato; revogou 150/2021 e 199/2024); Res. 189/2024 (subvenções sociais da Copasa) com situação a confirmar. |
| Adoção das NR da ANA | **Sim para o ciclo 2025 (NR 3, 5, 6 e 8).** Lista "conjunto": NR 3, 5 e 8 "cumpriu", NR 6 "desobrigado". NR 9: Res. 205/2025 (parcial). NR 13: a Res. 227/2026 cita expressamente a Res. ANA 271/2025, mas limita a obrigatoriedade a contratos não licitados. NR 11 e demais: não localizado. Fonte: planilhas ANA; Res. 205/2025 e 227/2026. |

Parâmetros a pré-carregar (ARSAE-MG):

| Parâmetro | Valor | Artigo | Marca |
|---|---|---|---|
| Multa | máximo de 2% | Res. 131, art. 112, § 1º | [C] |
| Juros de mora | incidem sobre o valor da fatura, sem percentual fixado | art. 112, caput e § 2º | percentual [NC] |
| Correção | Taxa Selic | art. 112, caput | [C] |
| Negativação | permitida (Res. 165/2022) | art. 112, § 4º | [C] |
| Prazo de vistoria | 70% em 3 d.u. e 100% em 5 d.u. | art. 33 | [C] |
| Demais prazos (ligação, vazamento, resposta, ressarcimento) | ligação 7/10 d.u.; com prolongamento 10/20 d.u.; vazamento 24/48 h; resposta formal em 5 d.u.; ressarcimento em 30 dias | arts. 14-15, 34-35 (Res. 131), Res. 94 | [C08] |
| Devolução por pagamento em duplicidade | até 30 dias corridos da solicitação, com Selic | art. 97 | [C] |
| Aviso de corte | destacado na fatura seguinte ao débito ou por carta; execução só 30 dias corridos após o aviso; novo aviso se passar de 90 dias | art. 115, §§ 2º e 3º | [C] |
| Dias vedados | sexta-feira a domingo, véspera e feriado nacional, estadual ou municipal | art. 115, § 1º | [C] |
| Horários vedados | não fixados | — | [NC] |
| Religação | 2 dias corridos; urgência facultativa em 12 h | arts. 118 e 119 | [C] |
| Suspensão indevida | religação em 12 h sem ônus e crédito do dobro do faturamento do período | art. 117 | [C] |
| Média sem leitura | média dos 12 últimos ciclos; depois volume pós-regularização; depois presumido; no máximo 6 ciclos; depois 50%, 100% ou 150% do presumido conforme o motivo | arts. 69, 70 e 73 | [C] |
| Datas de vencimento | pelo menos 6, com até 3 trocas por ano civil; fatura ≥ 10 dias antes (5 para comercial e industrial) | — | [C08] |
| Tarifa social | desconto mínimo de 50%, sem limite de consumo; Social I (pobreza e extrema pobreza) e Social II (baixa renda); CadÚnico ou BPC; renda per capita até 1/2 salário mínimo; inclusão em até 30 dias do pedido; base trimestral da SEDESE; perda após 2 anos sem atualização | Res. 227/2026 arts. 2º a 16 | [C] |
| Desligamento | não condicionar a débitos anteriores | Res. 131, art. 114, § 2º (Res. 179/2023) | [C] |
| Ressarcimento | prescrição de 10 anos; início em até 60 dias da notificação | Res. 184/2023, arts. 8º e 30 | [C] |

### 3. ARES-PCJ (SP e MG)

| Campo | Conteúdo |
|---|---|
| UF | SP e MG (consórcio público) |
| Nome | ARES-PCJ, Agência Reguladora dos Serviços de Saneamento das Bacias dos Rios Piracicaba, Capivari e Jundiaí |
| Abrangência | 79 municípios nas listas da ANA (77 no "conjunto"), em SP, com atendimento a MG; inclui Campinas, Piracicaba, Americana, Ribeirão Preto (SAERP aplica a Res. 592/2024), Jaboticabal, Artur Nogueira. Cada município tem resolução de regulamento própria e resolução tarifária própria. |
| Condições gerais | Res. 50/2014 (consolidada 2025), alterada por 198/2017, 460/2022, 649/2025 e 650/2025; Res. 48/2014 (não conformidades, alt. 627/2025); Res. 71/2014 (fiscalização, alt. 628/2025); Res. 49/2014 (ouvidoria). |
| Tarifa | Res. 435/2022 (reajuste e revisão; alt. 580/2024 e 666/2025) mais resolução municipal (ex.: 642/2025, 707/2026, 713/2026). |
| Tarifa social | Res. 592/2024 (03/12/2024). |
| Adoção das NR da ANA | **Sim** para NR 3 (Res. 648/2025), NR 4 (regimento interno), NR 5 (651/2025), NR 7 (370/2020, alt. 645/2025), NR 8 (650/2025 e 649/2025) e NR 9 (723/2026), segundo a página "Atendimento NRs ANA" do site. Lista ANA 2025: "conjunto" cumprido (NR 3, 5 e 8; NR 6 desobrigado). NR 10, 11, 13 e 15: não listadas na página. |

Parâmetros a pré-carregar (ARES-PCJ):

| Parâmetro | Valor | Artigo | Marca |
|---|---|---|---|
| Multa, juros e correção | "conforme legislação municipal e contratos"; sem percentual na norma-piso | Res. 50, art. 98 | norma [C]; percentuais por município [NC] |
| Fatura | intervalos de 27 a 33 dias; prazo mínimo de 90 dias para revisão de contas | arts. 87, § 1º, e 100 | [C] |
| Antecedência da fatura antes do vencimento | sem regra geral na Res. 50; regulamento do município | — | [NC] |
| Datas de vencimento | oferta de datas ao usuário, número definido no regulamento municipal | art. 87 | número [NC] |
| Aviso de corte | com comprovação do recebimento e antecedência mínima de 30 dias; conteúdo mínimo: data do aviso, faturas em atraso e valores sem correção | art. 108 e § 2º | [C] |
| Dias vedados | sexta-feira, sábado, domingo, feriados e véspera | art. 112 | [C] |
| Horários vedados | a Res. 50 não fixa; regulamentos municipais podem fixar (ex.: Sertãozinho, corte de segunda a quinta, 8h às 14h, segundo o 08) | — | [NC] |
| Religação | 12 h (corte indevido), 24 h (corte com aviso), 72 h (retirada de ramal); urgência facultativa em 4 h | arts. 115 e 116 | [C] |
| Prazos de serviços (tabela) | vistoria 3 d.u. urbana e 5 d.u. rural; ligação 10 d.u. urbana e 15 d.u. rural; religação após corte por inadimplência 24 h; vazamento 5 d.u.; desobstrução 24 h; aferição 10 d.u.; troca de hidrômetro violado 2 d.u.; corte/religação a pedido 2 d.u.; vigência 01/03/2023 | art. 130-E | [C] |
| Média sem leitura | média dos últimos 6 meses medidos ou consumo mínimo da categoria, se a média for menor; máximo de ciclos não fixado | art. 94, § 6º | [C]; máximo de ciclos [NC] |
| Negativação | permitida após esgotadas as medidas administrativas | art. 109 | [C] |
| Parcelamento | livre em forma e número de parcelas; exige Termo de Acordo e Confissão de Dívida | arts. 102 e 103 | [C] |
| Devolução em dobro | retirada pela Res. 460/2022 | art. 101, § 2º | [C] |
| Tarifa social | 50% na parcela até 15 m³ e 25% de 15 a 20 m³; renda per capita até 1/2 salário mínimo; CadÚnico ou BPC; cadastro automático; financiamento por subsídio cruzado | Res. 592/2024, arts. 3º, 5º, 9º e 21 | [C] |

### 4. Outras agências de MG

| Agência | UF e abrangência | Normas de condições gerais e tarifa | NR da ANA (fonte) | Parâmetros |
|---|---|---|---|---|
| **ARISB-MG** (Agência Reguladora Intermunicipal de Saneamento Básico de MG) | MG; 35 municípios (Ouro Preto, Uberaba, Barbacena, Araguari e outros) | Res. 132/2020 (08/09/2020, condições gerais); 135 e 138/2020 (não conformidades e penalidades; alt. 423/2026); 96/2019 (reajuste); 360/2025 (indicadores) | **Sim** no ciclo 2025: "conjunto" com NR 3, 5 e 8 cumpridas e NR 6 desobrigada; NR 9 via Res. 360/2025. Fonte: planilhas ANA e lista de resoluções da agência. | Multa, juros, aviso de corte, religação e média: **[NC]** (texto da 132 só parcialmente lido). Tarifa social: **[NC]**. |
| **ARIS-MG** (antes ARIS-ZM) | MG; 35 a 37 municípios da Zona da Mata (Mariana, Governador Valadares e outros) | Res. 185/2025 (20/08/2025, condições gerais); Res. ARIS-ZM 030/2022 (versão anterior, situação a confirmar); resoluções tarifárias por município | **Sim** no ciclo 2025: "conjunto" (NR 3, 5 e 8; NR 6 desobrigada). Fonte: planilhas ANA. | Mora: juros, multa e correção "conforme legislação municipal e contratos" (art. 98) [C]. Aviso de corte ≥ 30 dias com comprovação (art. 111) [C]. Dias vedados: sextas-feiras, finais de semana e véspera de feriado (art. 113) [C]. Religação: 12 h (indevida), 48 h (com aviso), 72 h (retirada de ramal); urgência facultativa em 12 h (arts. 116 e 117) [C]. Tarifa social e média: **[NC]**. A norma não alcança concessões licitadas antes da vigência (art. 2º, § 1º) [C]. |
| **ARISMIG** (Agência Reguladora Intermunicipal de Saneamento de MG) | MG; 27 municípios (Boa Esperança, São Lourenço, Lambari, Campo Belo e outros) | Res. 053/2023 (17/11/2023, padrões de prestação); resoluções tarifárias por município (ex.: DC 040/2025) | **Parcial:** só NR 3 "cumpriu"; ausente do "conjunto" e da NR 5. Fonte: planilhas ANA. | **[NC]** (trecho lido apenas até a definição e a ligação). |
| **ARESAN** (Uberlândia) | MG; DMAE Uberlândia | LC municipal 13.679/2021 cria a agência; resoluções 006/2023 e 003/2024 (preços); Decreto 2.624/1984 e 19.545/2021 | **Não/desconhecido:** NR 3, 5 e 6 "desobrigado"; sem aparecer no "conjunto". | **[NC]** |
| **AMASBE** (Montes Claros) | MG | Usa as Res. ARSAE-MG 129, 130 e 131 para a Copasa; sem norma própria localizada | NR 6 "desobrigado" apenas; sem "conjunto" | Segue a ARSAE-MG |
| **ARSAP** (Pará de Minas), **ARSAN** (norte de MG), **ARSAMB** | MG; um município cada | Não pesquisadas | ARSAP: NR 5 "cumpriu" (1) e demais desobrigado; ARSAN: NR 3 "cumpriu"; ARSAMB: "conjunto" para água (1 município) | **[NC]** |

### 5. Outras agências de SP

| Agência | UF e abrangência | Normas | NR da ANA (fonte) | Parâmetros |
|---|---|---|---|---|
| **ARVAP / Agência CIVAP** (Vale do Paranapanema) | SP; Ourinhos (concessão, contrato 63/2024) e municípios do consórcio | Regulamento dos serviços de Ourinhos (Res. 003/2024); Res. 004/2026 (penalidades por irregularidades, publicada em 28/07/2026); 006/2026 (ligações); 009/2026 (reajuste sem concessão) | **Parcial no ciclo 2025:** NR 5 "cumpriu" (4 registros); NR 3 e 6 desobrigado; fora do "conjunto". Em 2026 publicou Res. 007/2026 (NR 8) e 010/2026 (NR 9), em 19/08/2026. | **[NC]** |
| **SRJ** (Serviço de Regulação de Saneamento de Jacareí) | SP; Jacareí e Potim | Res. SRJ 07/2017 (18/08/2017, conteúdo não lido); Lei municipal 5.806/2013 cria o SRJ | **Sim** (registro "conjunto" com NR 8 cumprida; NR 3, 5 e 6 desobrigadas) | **[NC]** |
| **AMAE Marília**, **ARESPCAB** (Casa Branca e 3 municípios), **SAEMJA** (Jaú) | SP; municipais | Não pesquisadas | AMAE e SAEMJA: NR 5 e NR 6 "desobrigado"; ARESPCAB: NR 3 "cumpriu" e NR 5/6 "desobrigado"; nenhuma no "conjunto" | **[NC]** |

### 6. AGENERSA e normas do Rio de Janeiro

| Campo | Conteúdo |
|---|---|
| UF | RJ |
| Nome | AGENERSA, Agência Reguladora de Energia e Saneamento Básico do Estado do Rio de Janeiro (Lei estadual 4.556/2005) |
| Abrangência | Concessões regionalizadas dos Blocos 1 a 4 (Águas do Rio 1 e 4, Iguá, Rio+Saneamento), Cedae, e concessões privadas anteriores (Prolagos, Águas de Juturnaíba, Águas da Condessa, Águas de Paraty, Águas da Imperatriz, Águas de Pádua). 72 municípios nas listas da ANA. |
| Condições gerais | Decreto estadual 48.225/2022 (Regulamento, Blocos 1 a 4); IN 103/2023 e 122/2024 (atendimento); IN 119 e 120/2024 (TAC de 26/03/2024); IN 128/2024 (penalidades); IN 148/2026 (Lei 12.007); IN 141/2025 (acompanhamento de tarifas); IN 151/2026 (ocorrências). Cedae: Decreto 45.344/2015 (alt. 46.855/2019). |
| Tarifa | Deliberações de reajuste por concessionária (ex.: Del. 5.021/2026, Cedae, 5,1722%). |
| Tarifa social | Regulamento, arts. 67 e 68 (critérios propostos pela concessionária e aprovados pela AGENERSA); Decreto 25.438/1999 (áreas de interesse social); Portaria 927/2025 (GT para adequar à Lei 14.898/2024). Norma final sobre critérios: **não localizada**. |
| Adoção das NR da ANA | **Parcial.** NR 4: IN 124/2024 (alt. IN 127/2024). Lista 2025: NR 5 "desobrigado" (111 registros) e NR 6 "desobrigado" (131); NR 3 e "conjunto" ausentes. NR 8, 9, 11 e 13: não localizados. Fonte: planilhas ANA e página de INs da agência. |

Parâmetros a pré-carregar (concessões dos Blocos 1 a 4, Decreto 48.225/2022):

| Parâmetro | Valor | Artigo | Marca |
|---|---|---|---|
| Multa | 2% (texto literal: "multa de 2% ao mês"; periodicidade ambígua, não assumir multa recorrente) | art. 10, item 7 | valor [C]; periodicidade [NC] |
| Juros de mora | 1% ao mês, ou 0,03% ao dia | art. 10, item 7 | [C] |
| Correção | IPCA | art. 10, item 7 | [C] |
| Antecedência da fatura | 5 dias úteis antes do vencimento; fatura entregue após o vencimento isenta de mora e abre novo prazo de 5 d.u. | art. 76 e § 4º | [C] |
| Intervalo de leitura | 27 a 33 dias | art. 58 | [C] |
| Aviso de corte | comunicação com no mínimo 30 dias; suspensão a partir do 30º dia após o vencimento | art. 6º, item 3, e art. 90, item 1 | [C] |
| Dias e horários vedados | não fixados no regulamento; Lei estadual 4.824/2006 veda o corte nas vésperas de sábado, domingo e feriado, salvo plantão de religação e outras exceções | Lei 4.824/2006, arts. 1º e 3º | lei [C] (fonte secundária); regulamento [NC] |
| Religação | 48 h da identificação do pagamento; corte indevido em 24 h; religação de economia já conectada em 48 h; nova ligação em 5 d.u. | arts. 8º, 9º e 37 | [C] |
| Prazo de resposta ao usuário | 5, 10 ou 15 d.u. conforme a prioridade (religação e carro-pipa: 5 d.u.) | IN 103/2023, art. 67 e Anexo II | [C] |
| Média sem leitura | média das últimas 12 leituras; mais de 3 meses sem leitura é irregularidade do usuário | art. 64 e item 2 | [C] |
| Tarifa mínima | residencial 15 m³ (fonte secundária); vedada em matrícula com abastecimento suspenso, com devolução em dobro | IN 119/2024 art. 4º | mínima [NC]; vedação [C] |
| Tarifa social | critérios da concessionária aprovados pela agência; inadimplente não é beneficiário; cadastro automático e isenção de ligação em casos do TAC | arts. 67 e 68; IN 119 | [C] |
| Parcelamento | até 60 parcelas no DESAFOGA (adesão facultativa), sem suspensão para quem estiver adimplente | IN 119/2024, cláusula primeira do Termo | [C] |
| Hidrômetro novo | aumento a partir de 30% da média de 12 meses gera revisão; vedada cobrança de instalação quando já há ligação | IN 120/2024, arts. 2º e 3º | [C] |

Normas estaduais do RJ levantadas: Lei 4.824/2006 (corte em vésperas), Lei 3.810/2002 (reparo em 24 h; situação a confirmar), Lei 4.934/2006 (hora marcada), Lei 7.574/2017 (identificação da equipe, alt. Lei 10.146/2023), Lei 7.529/2017 e Decreto 25.438/1999 (tarifa social em áreas de interesse social), Leis 8.769/2020 e 9.356/2021 (vedação de corte na pandemia, efeito provavelmente exaurido), Decreto 22.872/1996 (regulamento antigo).

Agências municipais do RJ: **AGERSAN** (Macaé, LC 337/2024; agenda 2026-2027 prevê NR 11 e NR 13 até 2027), **SANEAR** (Resende) e **Fundação Rio-Águas** (município do Rio, aparece com NR 3 "cumpriu" e NR 5 "desobrigado"). Parâmetros: **[NC]**. **AGERSA** não existe no RJ segundo a pesquisa (ver pendências).

### 7. Resumo de adoção das NR da ANA (lista positiva 2025 e sites)

| ERI | NR 3 | NR 5 | NR 6 | NR 8 | "Conjunto" 2025 | Observação |
|---|---|---|---|---|---|---|
| ARSESP | cumpriu | desobrigado (8 reg.) | desobrigado | sem registro | não | NR 8 e 9 na agenda 2026-2027 |
| ARSAE-MG | cumpriu | cumpriu | desobrigado | cumpriu | sim (635 mun.) | NR 9 via Res. 205/2025; NR 13 citada na Res. 227/2026 |
| ARES-PCJ | cumpriu | cumpriu | desobrigado | cumpriu | sim (77 mun.) | NR 4, 7 e 9 também no site |
| ARISB-MG | cumpriu | cumpriu | desobrigado | cumpriu | sim (35 mun.) | NR 9 via Res. 360/2025 |
| ARIS-MG | cumpriu | cumpriu | desobrigado | cumpriu | sim (35 mun.) | |
| ARISMIG | cumpriu | sem registro | desobrigado | sem registro | não | |
| ARESAN | desobrigado | desobrigado | desobrigado | sem registro | não | |
| SRJ (Jacareí) | desobrigado | desobrigado | desobrigado | cumpriu | sim | |
| ARVAP | desobrigado | cumpriu | desobrigado | sem registro | não | NR 8 e 9 em ago/2026 |
| AGENERSA | sem registro | desobrigado | desobrigado | sem registro | não | NR 4 via IN 124/2024 |
| AGERSAN, SANEAR | sem registro | desobrigado | desobrigado | sem registro | não | agenda de Macaé prevê as NR até 2027 |

Fontes: planilhas da ANA mencionadas acima. "Sem registro" indica ausência na planilha da NR, que pode significar não comprovado ou fora do escopo verificado; não equivale a descumprimento comprovado.

### 8. Orientações para o plano do Ziip SAN

- Guardar, por tenant, três níveis de parâmetro: norma da agência, regulamento do município ou contrato (ARES-PCJ, ARIS-MG, ARISB-MG, ARISMIG), e política do prestador (parcelamento, negativação). Todos com `valid_from` e `valid_to`.
- Em SP, o motor de faturamento precisa de proporcionalização de faixas por período regular, parcial e complementar (Del. 1.836/2026) e de acerto de média (art. 63 §§6º a 8º).
- Cadastro de tarifa social com origem da base (mensal em SP, trimestral em MG, automática ou por pedido), limite por consumo (nenhum em MG, 15 m³ em SP Social II, 15 m³ e 20 m³ no PCJ) e fluxo de perda com aviso por ciclos.
- Reservar campos para cofaturamento de resíduos (SP a partir de 01/01/2027) e para a declaração anual de quitação (RJ, IN 148/2026; Lei 12.007/2009).

## Anexo D — RS, PR, DF, GO, MS e MT

Data da pesquisa: 22/10/2026. Complementa o levantamento 08 (seções 5 e d.2), sem repeti-lo. Os dados estruturados das normas estão em `04-eris.json` (64 itens). Este arquivo reúne, por agência, abrangência, normas, adoção das Normas de Referência (NR) da ANA e os parâmetros que o Ziip SAN deve pré-carregar no perfil regulatório do tenant.

Legenda de situação: **Confirmado** = li o artigo no texto oficial da norma; **Secundário** = informação de notícia, agregador (LegisWeb) ou resumo; **Não confirmado** = não localizei ou há contradição entre fontes. Prazos em "dias" sem qualificação no texto da norma estão marcados como tal (a norma não diz se são corridos ou úteis). Não é parecer jurídico.

### 0. Achados que mudam o levantamento 08

1. **AGERGS, RN 66/2022, art. 109**: a dúvida E-14 está resolvida. O §13, III, exige comunicação escrita e específica com 30 dias de antecedência para a suspensão por inadimplência (inciso IV). O §2º cita 30 dias só para os incisos III e V, mas não limita o §13. O §3º veda cortar após 90 dias da fatura vencida e o §6º limita a execução a 90 dias do prazo da notificação. A data da RN 66 é **09/08/2022** (sessão 33/2022), publicada no DOE em 10/08/2022.
2. **AGEPAR, Res. 3/2020**: o Decreto Estadual 3.926/1988 foi **revogado** pelo Decreto 8.182, de 30/07/2021, e o Regulamento está em vigor (E-16). O art. 122 (fatura) tem 19 itens, listados no JSON. O PDF oficial tem a camada de texto truncada; li as páginas como imagem.
3. **ADASA, limite de 30 m³ da tarifa social** (E-15): permanece no art. 101, II, e na definição LXXXII da Res. 14/2011. É mais favorável que o piso federal e a Lei 14.898 (art. 6º §2) admite benefício maior. A Res. 70/2026 mantém o desconto até a faixa de 21 a 30 m³.
4. **ADASA, art. 122 e Lei Distrital 5.990/2017**: o SINJ-DF informa que a lei foi declarada inconstitucional (RE 1.314.679, STF, 20/03/2024). A regra dos 12 meses para saúde, educação, internação e baixa renda perdeu a base legal, mas segue no texto da agência. Tratar como parâmetro a confirmar.
5. **STF, ADI 7.725/TO (julgamento finalizado em 23/05/2025)**: lei estadual que regula suspensão de água e energia por inadimplência é inconstitucional (competência da União e dos municípios). Atinge, em tese, as leis do Paraná (Lei 22.130/2024, arts. 154 a 159) e do DF (Leis 4.632/2011 e 5.990/2017). As regras de agência e a Lei 11.445 (art. 40) não são afetadas.
6. **Lei Estadual do Paraná 22.130/2024** (Consolidação das Leis de Defesa do Consumidor): repete a vedação de corte e a religação de 24 h e acrescenta horário de 8 h às 18 h. A Lei 14.471/2004 não aparece na lista de revogações lida; situação a confirmar.
7. **A adoção "de todas as NR" noticiada por AGERGS e AGEPAR refere-se ao ciclo de 2025 da ANA, que verificou só quatro NR de água e esgoto** (NR 3, 5, 6 e 8). Nas listas positivas de 20/12/2025 as duas agências constam como "Cumpriu" nas NR 3, 5 e 8 e "Desobrigado" na NR 6. Não há verificação ainda das NR 9, 10, 11, 13 e 15.
8. **Mesma concessionária, regras diferentes dentro do RS**: a Corsan aplica o RSAE da AGERGS (RN 66/2022) em 238 municípios e o regulamento da AGESAN (CSR 003/2021) em 81 municípios, com média de 6 contra 12 consumos, religação indevida de 4 h contra 12 h e vedação de corte diferente. O SAMAE de Caxias do Sul, sob a mesma AGESAN, tem juros de 0,5% ao mês. O perfil regulatório deve ser por município, não por prestador.
9. **Duas agências publicaram regulamentos novos de condições gerais depois da NR 11 (dezembro de 2024)**: AGEMS (Portaria 320/2025, 09/12/2025) e AGER/MT (RN 004/2026, 15/05/2026). Nenhuma cita expressamente a NR 11 nos trechos lidos.
10. **Tarifa social**: segundo a lista positiva de tarifa social da ANA (julho/2026), estão "Concluído" a Caesb (DF), a Sanepar (PR) e a Sanesul (MS). Corsan (RS), Saneago (GO) e Águas Cuiabá (MT) aparecem como "em reequilíbrio" ou "obtenção dos dados", mas as datas de preenchimento são de janeiro a março de 2026 e a Corsan já tem categoria aprovada (RED 892/2026).

### 1. Adoção das NR da ANA no ciclo de 2025 (lista positiva da ANA, 20/12/2025)

Fonte: planilha `lista-positiva-municipios-2025-conjunto.xlsx` em gov.br/ana (página "Serviços de Abastecimento de Água Potável e Esgotamento Sanitário"). O ciclo cobriu NR 3, NR 5, NR 6 e NR 8. "Desobrigado" quer dizer que o município não precisa ter a ERI cumprindo aquela NR pelo tipo de contrato. Contagem de municípios por ERI.

| UF | ERI | Municípios na lista | NR 3 | NR 5 | NR 6 | NR 8 |
|---|---|---|---|---|---|---|
| RS | AGERGS | 238 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| RS | AGESAN-RS | 81 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| RS | AGERST (Santa Cruz do Sul) | 1 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| RS | AGER (Erechim) | 1 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| PR | AGEPAR | 344 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| DF | ADASA | 1 | Desobrigado | Desobrigado | Desobrigado | Cumpriu |
| GO | AGR | 223 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| GO | AMAE (Rio Verde) | 3 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| GO | AR (Goiânia) | 1 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| MS | AGEMS | 69 | Cumpriu (1 município desobrigado) | Cumpriu (1 desobrigado) | Desobrigado | Cumpriu |
| MT | AGER/MT | 2 | Desobrigado | Desobrigado | Desobrigado | Cumpriu |
| MT | ARIS-MT | 11 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |
| MT | AGERSINOP | 10 | Cumpriu | Desobrigado | Desobrigado | Cumpriu |
| MT | Cuiabá Regula | 1 | Cumpriu | Cumpriu | Desobrigado | Cumpriu |

Não constam da lista, e não confirmei se são ERI cadastradas: AGEREG (Campo Grande) e ARM (Anápolis); a AGEREG aparece na lista de tarifa social da ANA. A ANA indica que NR 9, 10, 11, 13 e 15 só passam a ser verificadas a partir de 2026 a 2028 (levantamento 08, seção c.2).

Adoção por tema, além do ciclo de 2025:

| ERI | NR 9 (indicadores) | NR 11 (condições gerais) | NR 13 e Lei 14.898 (tarifa social) | Outras |
|---|---|---|---|---|
| AGERGS | NT 01/2025-DSI e IN 32/2026 (Secundário/Confirmado) | Não localizado | RED 892/2026 (Secundário) | RED 780/2025 internaliza as NR (a confirmar) |
| AGESAN-RS | Relatório NR 9 2025/2026 no site (Confirmado) | Não localizado | CSR 033/2024 e CSR 013/2026 (Confirmado) | NR 4 (relatório 2025), soluções alternativas CSR 007/2025 |
| AGEPAR | Res. 56/2026 (Confirmado) | Não localizado | Res. 36/2025 (Confirmado) | Res. 63/2025 (NR 3), Res. 35/2025 (soluções alternativas), Res. 34/2025 (SLU) |
| ADASA | Não localizado | Não localizado | Art. 67-A, Res. 42/2024 (Confirmado) | Não verificado |
| AGR | RN 366/2026 e conjuntas 09 e 10/2026 (Confirmado na página da agência) | Não localizado | RN 186/2021 sem adaptação (A confirmar) | Conjuntas 01 a 04/2025 (NR 8, 5, 3 e soluções alternativas); RN 368/2026 (NR 7) |
| AGEMS | Não localizado | Port. 320/2025 não cita a NR 11 nos trechos lidos | Port. 315/2025 (Secundário) | Port. 320/2025 inclui matriz de riscos (NR 5) |
| AGER/MT | RN 007/2026 (Secundário) | RN 004/2026, sem menção à NR 11 no resumo | Nenhuma norma encontrada | RN 008, 009, 010/2026 (NR 3, 5 e soluções alternativas); RN 013/2026 (reajuste) |
| ARIS-MT | RN 059/2026 (Secundário) | Não localizado | Não localizado | RN 49 a 54/2025 e RN 58/2026 |
| Cuiabá Regula | Não localizado | Não localizado | Sem norma própria localizada | RN 10/2025 (soluções alternativas) |
| AGERSINOP | Res. 16/2025 (em consulta pública) | Res. 04 e 05/2019 (a confirmar) | Res. 09/2025 (Secundário) | Res. 10 a 15/2025 |

### 2. Quadros por agência

#### 2.1 AGERGS (RS)

| Campo | Informação |
|---|---|
| UF e nome | RS. Agência Estadual de Regulação dos Serviços Públicos Delegados do Rio Grande do Sul |
| Abrangência | 238 municípios na lista da ANA (Corsan e BRK Ambiental Uruguaiana). Porto Alegre (DMAE) foi incluída por contrato de regulação assinado em 24/11/2025, com fase inicial de um ano sem caráter sancionatório |
| Condições gerais | REN 66/2022 (RSAE Unificado), 09/08/2022, alterada pela RED 705/2023 |
| Tarifa e tarifa social | Tabelas por reajuste; RED 892/2026 (09/06/2026) cria a categoria 'Resid. Social Lei 14.898/24' e revisão de 5,76% |
| Outras | REN 37/2017 (compensação por interrupção), REN 74/2026 (disponibilidade de esgoto), REN 30/2016 (irregularidades), IN 32/2026 (dados) |
| NR da ANA | Sim para NR 3, 5 e 8 (ciclo 2025, NR 6 desobrigada). NR 9 internalizada (NT 01/2025, IN 32/2026). NR 11 e 13 não localizadas |

| Parâmetro | Valor | Artigo | Situação |
|---|---|---|---|
| Multa | 2% | RN 66, art. 136, I | Confirmado |
| Juros | 1% ao mês, pro rata die | art. 136, II | Confirmado |
| Correção | Mesmo índice dos reajustes tarifários, pro rata die | art. 136, III | Confirmado |
| Prazo de resposta a solicitação | 30 dias (sem qualificar se corridos ou úteis), com número de protocolo | art. 168 | Confirmado; contagem não confirmada |
| Aviso de corte (inadimplência) | 30 dias, escrito e específico | art. 109 §13, III | Confirmado |
| Execução do corte | até 90 dias do prazo da notificação; vedado após 90 dias da fatura vencida | art. 109 §6 e §3 | Confirmado |
| Dias vedados | Início de corte de sexta a domingo, feriado e véspera | art. 113 | Confirmado |
| Horário vedado | Não localizado | - | Não confirmado |
| Serviço essencial / baixa renda | Aviso de 60 dias à autoridade / 40 dias | art. 114 e §2 | Confirmado |
| Média sem leitura | Média dos 6 últimos consumos faturados; estimativa por até 3 ciclos; após o 4º, art. 125, II (consumo estimado da categoria) ou art. 100 conforme a causa | arts. 100, 120, 123 §2, 98 | Confirmado |
| Religação | 24 h após quitação; sem cobrança se o prazo falhar | art. 115 e §1 | Confirmado |
| Religação de urgência | Facultativa, 4 h | art. 116 | Confirmado |
| Religação indevida | 4 h, sem ônus, mais crédito do dobro do preço da religação | art. 111 e p.u. | Confirmado |
| Troca de vencimento | Até 2 vezes em 12 meses; número mínimo de datas não localizado | art. 122 p.u. | Parcial |
| Vencimento em dia sem expediente | Prorroga para o dia útil seguinte | art. 137 | Confirmado |
| Esgoto | Coletado 50% e tratado 70% do preço do m³ de água | art. 126 | Confirmado |
| Tarifa social | 50% sobre a tarifa até 15 m³, uma unidade por CPF, vigência em 01/07/2026 e descontos nas faturas de agosto | RED 892/2026, art. 4º (via Corsan) | Secundário |

#### 2.2 AGESAN-RS (RS)

| Campo | Informação |
|---|---|
| UF e nome | RS. Agência Reguladora Intermunicipal de Saneamento do Rio Grande do Sul (sede em Porto Alegre) |
| Abrangência | 81 municípios na lista da ANA: Corsan, SAMAE (Caxias do Sul, Bagé: DAEB, entre outros), SEMAE, COMUSA (Novo Hamburgo), SANEP (Pelotas) e outros |
| Condições gerais | Resolução CSR 003/2021 (Corsan); regulamentos próprios por prestador, como a CSR 016/2024 (SAMAE de Caxias do Sul) e regulamentos de SEMAE, COMUSA e Ivoti |
| Tarifa social | CSR 033/2024 (vigência 01/01/2025); CSR 13/2026 (revisão extraordinária da Corsan, 26/06/2026); CSR 14/2026 em consulta pública (DAEB de Bagé) |
| NR da ANA | Sim para NR 3, 5 e 8; publica relatórios NR 8 e 9 em 2025/2026 |

| Parâmetro | Corsan (CSR 003/2021) | SAMAE Caxias (CSR 016/2024) | Situação |
|---|---|---|---|
| Multa | Até 2% (art. 111) | 2%, uma única vez (art. 118 §5) | Confirmado |
| Juros | 1% ao mês, pro rata temporis (art. 111) | 0,5% ao mês, pro rata die (art. 118 §5) | Confirmado |
| Correção | Não localizada no art. 111 | Atualização monetária sobre o principal (art. 118 §5, índice não localizado) | Não confirmado |
| Multa de inscrição de dívida | Não localizada | 5% (art. 118 §6) | Confirmado |
| Aviso de corte | 30 dias para inadimplência (art. 90 §9, III); execução em até 90 dias do prazo (§2) | Corte após 30 dias do vencimento com prévio aviso (art. 118 §1) | Confirmado |
| Dias vedados | Sábados, domingos e feriados (art. 92); sem menção a sexta e véspera | Não localizado | Confirmado / Não confirmado |
| Média sem leitura | 12 últimos consumos (art. 81, I); até 3 ciclos (art. 79 §1) | Não localizado (média de 6 meses aparece só em infrações) | Confirmado / Não confirmado |
| Religação | Não verificada (art. 94 e §10) | Não localizada | Não confirmado |
| Religação indevida | 12 h, sem ônus (art. 90 §10) | Não localizada | Confirmado / Não confirmado |
| Serviço essencial | 60 dias (art. 93) | Não localizado | Confirmado |
| Tarifa social | 50% até 15 m³, classificação automática, relatório anual até junho (CSR 033/2024, arts. 4 e 6) | CSR 019/2023: tarifa subsocial de R$ 20,00 até 20 m³ para REURB-S (relação com a Lei 14.898 não verificada) | Confirmado |

#### 2.3 AGERST e AGER Erechim (RS, municipais)

| Campo | Informação |
|---|---|
| UF e nome | RS. AGERST (Santa Cruz do Sul) e AGER (Erechim) |
| Abrangência | Um município cada, regulando a Corsan |
| Condições gerais | AGERST: Res. 19/2019 homologa o regulamento da Corsan (cópia do da AGERGS vigente em 2019); AGER Erechim: Res. 016/2019 revogada pela Res. 026/2023 (conteúdo da 026 não lido) |
| Tarifa social | AGERST: Res. 92/2026 (18/08/2026, 50% até 15 m³, índice extraordinário de 2%, secundário); AGER: consulta pública 14/2026 aberta em 9 a 25/10/2026 |
| NR da ANA | Cumpriu NR 3, 5 e 8 no ciclo de 2025 |
| Parâmetros | Não confirmados: a norma vigente de cada município não foi lida. O RSAE de 2019 da AGERGS (RED 467/2018) foi revogado pela REN 66/2022 |

#### 2.4 AGEPAR (PR)

| Campo | Informação |
|---|---|
| UF e nome | PR. Agência Reguladora de Serviços Públicos Delegados do Paraná |
| Abrangência | 344 municípios na lista da ANA (Sanepar). Outros reguladores no estado: AMR (Maringá), ORCISPAR (consórcio, SAAEs e SAMAEs) e CAGEPAR (litoral): suas normas não foram lidas |
| Condições gerais | Res. 3/2020 (Regulamento de Serviços Básicos), alterada pelas Res. 36/2025 e 38/2025 |
| Tarifa | Res. 10/2025 (3ª RTP), Res. 36/2025 (tarifa social), Res. 23/2026 (reajuste, 17/05/2026) |
| NR da ANA | Sim para NR 3, 5 e 8; NR 9 pela Res. 56/2026; NR 11 não localizada |

| Parâmetro | Valor | Artigo | Situação |
|---|---|---|---|
| Multa | 2% | Res. 3/2020, art. 125 | Confirmado |
| Juros | 0,033% ao dia | art. 125 | Confirmado |
| Correção | IPCA ou índice que o substitua | art. 125 | Confirmado |
| Entrega da fatura | Mínimo de 3 dias antes do vencimento | art. 121 | Confirmado |
| Datas de vencimento | Pelo menos 6 | art. 120 §4 | Confirmado |
| Aviso de corte | Escrito, 30 dias; aviso no cavalete é facultativo | art. 142 §§2 e 3 | Confirmado |
| Execução do corte | Sem corte por fatura vencida há mais de 90 dias | art. 142 §7 | Confirmado |
| Dias vedados | Sextas, sábados, domingos, datas sem expediente bancário e vésperas | art. 142 §4 | Confirmado |
| Horário | 8 h às 18 h (lei estadual) | Lei 22.130/2024, art. 156 §1 | Confirmado; validade sujeita à ADI 7.725 |
| Prazo mínimo após vencimento | Lei estadual: 30 dias do inadimplemento e notificação de 5 dias | Lei 22.130/2024, art. 159 | Confirmado; validade sujeita à ADI 7.725 |
| Saúde, educação, internação, baixa renda | Aviso de 30 dias e prazos que preservem a saúde | art. 143 | Confirmado |
| Média sem leitura | 5 meses; sem histórico, consumo mínimo da categoria; leitura impedida por mais de 3 ciclos admite sanção | arts. 104 §§1 e 3, 119 | Confirmado |
| Religação | 4 h após comunicação do pagamento, inclusive fim de semana e feriado; após 18 h conta a partir das 8 h | art. 151 e §1 | Confirmado |
| Religação indevida | 4 h, sem ônus | art. 145 p.u. | Confirmado |
| Taxa de religação | Não se cobra por inadimplência, salvo se pedida após 90 dias do corte | art. 152 p.u.; Lei 14.471/2004 | Confirmado no Regulamento; lei a confirmar |
| Esgoto sem ligação | 60 dias para interligar; cobrança do esgoto depois; ciclos até 180 dias | Res. 38/2025, art. 31 | Confirmado |
| Esgoto | 85% (Curitiba) e 80% (demais) da fatura de água | Res. 23/2026, art. 3 | Confirmado |
| Tarifa social | 50% sobre a categoria residencial, faixa fixa e m³ até 15 m³; uma unidade por CPF | Res. 36/2025, art. 1 | Confirmado; vigência em 15/12/2025 (Sanepar) |

#### 2.5 ADASA (DF)

| Campo | Informação |
|---|---|
| UF e nome | DF. Agência Reguladora de Águas, Energia e Saneamento Básico do Distrito Federal |
| Abrangência | Caesb, todo o Distrito Federal |
| Condições gerais | Res. 14/2011 consolidada até a Res. 42/2024; Res. 65/2025 alterou o Anexo V |
| Tarifa | Res. 70/2026 (reajuste de 3,98%, 01/06/2026 a 31/05/2027); bônus-desconto: Res. 6/2010 e Lei Distrital 4.341/2009 |
| NR da ANA | Cumpriu NR 8 (NR 3, 5 e 6 desobrigadas); NR 9, 11 e 13 não localizadas |

| Parâmetro | Valor | Artigo | Situação |
|---|---|---|---|
| Multa | Até 2% | Res. 14, art. 111 | Confirmado |
| Juros | 0,033% ao dia; não incidem sobre multas e juros de períodos anteriores | art. 111 e §2 | Confirmado |
| Correção | INPC ou índice que o substitua | art. 111 | Confirmado |
| Atraso mínimo para cortar | 60 dias | art. 121 §1 | Confirmado |
| Aviso de corte | 30 dias, com motivo e faturas, sob pena de nulidade; a comunicação eletrônica ou impressa exige prova de entrega | art. 121 §§2-3; art. 25-A | Confirmado |
| Limite máximo | Vedado após 120 dias do vencimento | art. 121 §4 | Confirmado |
| Dias vedados | Sextas, sábados, domingos, feriados e vésperas de feriados nacionais e distritais | art. 123 | Confirmado |
| Saúde, educação, internação, baixa renda | 12 meses de inadimplência, com comunicação mensal e aviso à ADASA 30 dias antes | art. 122 | Confirmado; base legal declarada inconstitucional |
| Religação | Prazos do Anexo IV (40 h úteis segundo o levantamento 08) | art. 128 | Anexo IV não reverificado |
| Religação indevida | 3 h, sem ônus | art. 121 §6 | Confirmado |
| Atraso na religação | Desconto de 20% do consumo médio na fatura seguinte | art. 129 | Confirmado |
| Média sem leitura | 12 meses (levantamento 08) | Não relido | Não reverificado |
| Datas de vencimento | Não relido | - | Não reverificado |
| Tarifa social | 50% até 30 m³; CadÚnico com renda até meio salário-mínimo ou Morar Bem Faixa I; uma unidade por família; relatórios até 20/01 | arts. 101, II, e 67-A | Confirmado |
| Bônus-desconto | 20% do volume economizado em relação ao mês de referência; informação mensal na fatura | Res. 6/2010, arts. 3, 4 e 7 | Confirmado |

#### 2.6 AGR (GO) e agências municipais conveniadas

| Campo | Informação |
|---|---|
| UF e nome | GO. Agência Goiana de Regulação, Controle e Fiscalização de Serviços Públicos (AGR); conveniadas: Agência de Regulação de Goiânia (AR), AMAE (Rio Verde, Jataí, Aparecida de Goiânia, Trindade e outros) e ARM (Anápolis) |
| Abrangência | 223 municípios da Saneago; BRK Ambiental Goiás em esgoto de Aparecida, Jataí, Rio Verde e Trindade; concessões menores (Águas de Ipameri, Buriti Alegre, Saneacéu) |
| Condições gerais | RN 09/2014 e alterações (RN 14/2014, 27/2015, 80/2016, 106/2017, 188/2022, 195/2022, 196/2022); RN 207/2023 (prazos); RN 088/2017 (religação automática); RN 154/2019 (fatura, não lida); RN 198/2022 (contrato de adesão) |
| Tarifa | Resolução Conjunta 1/2026 (reajuste de 4,845%, vigência 01/04/2026); RN 186/2021 (tarifa social) |
| NR da ANA | Sim para NR 3, 5 e 8 (ciclo 2025); NR 9 por RN 366/2026 e conjuntas; NR 11 e 13 não localizadas |

| Parâmetro | Valor | Artigo | Situação |
|---|---|---|---|
| Multa | 2% | RN 09, art. 96 (RN 80/2016) | Confirmado |
| Juros | 1% ao mês pro rata dia | art. 96 | Confirmado |
| Correção | INPC | art. 96 | Confirmado |
| Prazos de atendimento | Em dias úteis: faturamento 10; viabilidade de ligação 10; reclamações 10; falta de água 1; pressão 4; restituição avaliada em 2 e paga em até 30 | RN 207/2023, arts. 1 a 10 | Confirmado |
| Aviso de corte | 30 dias; corte só em dias úteis; vedado sem notificação | RN 09, art. 73 §§1-3 | Confirmado |
| Dias e horários vedados | Sem suspensão após 12 h de sexta-feira ou de véspera de feriado nacional, estadual ou municipal; só em dias úteis | art. 77 e art. 73 §2 | Confirmado |
| Reclamação em curso | Corte vedado enquanto durar a reclamação de faturamento | RN 207, art. 1 p.u. | Confirmado |
| Serviço essencial | Aviso de 30 dias à AGR para mediação | art. 74 | Confirmado |
| Média sem leitura | Média dos 6 últimos meses medidos, por até 6 ciclos; depois 50% da média | art. 87 §§1, 2 e 4 | Confirmado |
| Datas de vencimento | Pelo menos 6 | art. 95 | Confirmado |
| Religação | 24 h após pagamento; automática; corte no ramal pela tabela de prazos | arts. 79 e RN 088/2017 | Confirmado |
| Religação de urgência | 6 h em dia útil; 14 h em fim de semana, feriado e após 18 h | art. 80 | Confirmado |
| Religação indevida | 6 h, sem ônus; crédito do maior valor entre o dobro da urgência e 10% da fatura média | art. 73 §§5 e 7 | Confirmado |
| Tarifa social | Residencial Social para CadÚnico em extrema pobreza, uma conta por usuário, validação em até 90 dias (RN 186/2021); notícias de 2026 citam renda até R$ 105 per capita e consumo até 20 m³ | RN 186, art. 1 | Confirmado (2021); critérios de 2026 secundários |

#### 2.7 AGEMS (MS)

| Campo | Informação |
|---|---|
| UF e nome | MS. Agência Estadual de Regulação de Serviços Públicos de Mato Grosso do Sul (antiga AGEPAN) |
| Abrangência | 69 municípios na lista da ANA; Sanesul (68 municípios na 2ª revisão) e serviços autônomos (SAAEs) conveniados |
| Condições gerais | Portaria 320/2025 (09/12/2025); revogou a Portaria 232/2022, que por sua vez substituíra as Portarias Agepan 147/2017, 148/2017, 150/2017 e 158/2018 |
| Tarifa | Portaria 314/2025 (2ª revisão da Sanesul, vigência 01/01/2026); Portaria 315/2025 (tarifa social, secundário); Portaria 211/2021 (tarifa social anterior) |
| NR da ANA | Cumpriu NR 3, 5 (um município desobrigado) e 8; a Portaria 320 cita a Res. ANA 178/2024 (NR 5); NR 9, 11 e 13 não localizadas |

| Parâmetro | Valor | Artigo | Situação |
|---|---|---|---|
| Multa | 2% | Port. 320, art. 107 | Confirmado |
| Juros | Até 0,033% ao dia | art. 107 | Confirmado |
| Correção | IPCA ou outro índice previamente aprovado pela AGEMS | art. 107 | Confirmado |
| Aviso de corte | 30 dias, com entrega comprovada ou impresso em destaque na fatura | art. 151 §2 | Confirmado |
| Dias vedados | Sexta a domingo, véspera e feriados nacionais, estaduais e municipais | art. 151 §1 | Confirmado |
| Atraso mínimo e máximo para corte | Não fixados | - | Não confirmado (ausência no texto) |
| Média sem leitura | 6 meses; até 3 ciclos consecutivos | art. 82 §§1 e 2 | Confirmado |
| Datas de vencimento | Pelo menos 6 | art. 83 §4 | Confirmado |
| Religação | 48 h do pagamento ou parcelamento; 6 h em emergência e urgência paga; art. 155 fixa urgência em 24 h (conflito interno) | arts. 154 e 155 | Confirmado; conflito sem solução |
| Religação indevida | 6 h, sem ônus; crédito do dobro da religação de urgência | rol de direitos do usuário (incisos III e IV) | Confirmado |
| Tarifa social | 50% até 15 m³, sem desconto na tarifa fixa, automática; 3 meses de permanência com aviso | Port. 315/2025 (notícia) e art. 76 §2 da Port. 320 | Secundário |

#### 2.8 AGEREG (Campo Grande, MS)

| Campo | Informação |
|---|---|
| UF e nome | MS. Agência Municipal de Regulação dos Serviços Públicos de Campo Grande |
| Abrangência | Águas Guariroba (concessão de Campo Grande) |
| Normas | Norma AGEREG 01/2025 (Portaria 38/2025, 23/12/2025: tarifa social até 20 m³); regulamento dos serviços por decreto municipal (alterado em 2020, texto não lido); reajustes por portaria (Portaria 37/2025: 4,49% mais 0,16%) |
| NR da ANA | Não consta da lista positiva de 2025; desconhecido |
| Parâmetros | Média, juros, multa, corte e religação: **não localizados** (regulamento não lido). Notícia de 2020 cita religação em 48 h, indevida em 12 h e corte após 60 dias para beneficiários da tarifa social, secundária e antiga |

#### 2.9 AGER/MT (estadual)

| Campo | Informação |
|---|---|
| UF e nome | MT. Agência Estadual de Regulação dos Serviços Públicos Delegados de Mato Grosso |
| Abrangência | 2 municípios na lista da ANA, mais municípios conveniados |
| Normas | RN 004/2026 (condições gerais, 15/05/2026); RN 005/2026 (condições técnicas e operacionais); RN 006/2026 (fiscalização); RN 007/2026 (indicadores); RN 013/2026 (reajuste, 07/07/2026); RN 003/2016 (Quadro Demonstrativo de Faturamento) |
| NR da ANA | Cumpriu NR 8 (NR 3, 5 e 6 desobrigadas); RN 008, 009 e 010/2026 tratam de NR 3, 5 e soluções alternativas |

| Parâmetro | Valor | Artigo | Situação |
|---|---|---|---|
| Multa, juros e correção | Remetidos à legislação aplicável, sem percentuais | RN 004, art. 48 e art. 50 §2 | Secundário |
| Datas de vencimento | Pelo menos 6; fatura disponível 10 dias antes (social, residencial e pública) e 5 dias (comercial e industrial) | arts. 28 e 31 | Secundário |
| Aviso de corte | 30 dias; vedado por fatura vencida há mais de 90 dias | art. 94 e §2 | Secundário |
| Dias vedados | Sexta, sábado, domingo, feriado e véspera | art. 95 | Secundário |
| Média sem leitura | 12 ciclos, por até 6 ciclos consecutivos; ciclos de 27 a 33 dias | arts. 33 a 37 e art. 30 | Secundário |
| Religação | 24 h (inadimplência); 12 h indevida; 72 h se houve retirada de ramal ou medidor | art. 101 | Secundário |
| Urgência e indenização | 4 h; indenização do maior valor entre o dobro da urgência e 20% da última fatura | arts. 102 e 96 §2 | Secundário |
| Tarifa social | Menciona observar regras de subsídio; sem critérios | art. 29 §1 | Secundário |

Os dados da RN 004/2026 vêm de resumo automático do texto indexado pelo LegisWeb. Conferir contra o PDF oficial antes de pré-carregar.

#### 2.10 Cuiabá Regula (MT)

| Campo | Informação |
|---|---|
| UF e nome | MT. Agência de Fiscalização e Regulação dos Serviços Públicos Delegados do Município de Cuiabá (sucessora da Arsec) |
| Abrangência | Águas Cuiabá S.A. (concessão de água e esgoto) |
| Normas | LC 558/2025 (criação); RN 6/2025 (controle social); RN 10/2025 (soluções alternativas, eficácia condicionada); RN 12/2026 (tarifa mínima por economia, 06/03/2026) |
| NR da ANA | Cumpriu NR 3, 5 e 8 no ciclo de 2025 |
| Parâmetros | Condições gerais vigentes **não localizadas** (o regulamento da concessão é citado em decisão judicial: art. 63 esgoto a 80% da água, art. 73 valor mínimo por ligação). Reportagem de 2018 cita vencimento 10 dias após a leitura e corte 30 dias após o vencimento original, secundária e antiga. Juros, multa, média e religação: não confirmados |
| Tarifa social | Sem norma própria localizada; RN 10/2025, art. 29, prevê desconto conforme a Lei 14.898 para soluções alternativas. Lista positiva: "obtenção dos dados do CadÚnico" |

#### 2.11 ARIS-MT e AGERSINOP (MT)

| Campo | Informação |
|---|---|
| UF e nome | MT. ARIS-MT: Agência Reguladora Intermunicipal de Saneamento de Mato Grosso (consórcio, sede em Cuiabá). AGERSINOP: Agência Reguladora de Serviços Públicos Delegados do Município de Sinop (Lei Municipal 2.036/2014) |
| Abrangência | ARIS-MT: 11 municípios na lista da ANA, incluindo Rondonópolis (Sanear) e Cáceres; AGERSINOP: 10 municípios (Sinop, Colíder, Carlinda, Marcelândia, Matupá, União do Sul, Vera, entre outros) |
| Normas | ARIS-MT: RN 49 a 54/2025 (NR 3, 5, 6, SLU, soluções alternativas), RN 58 e 59/2026 (reajuste e indicadores), resoluções de reajuste por município. AGERSINOP: Res. 04 e 05/2019 (a confirmar), 09/2025 (tarifa social), 10 a 16/2025 (NR) |
| NR da ANA | ARIS-MT: NR 3, 5 e 8 (NR 6 desobrigada). AGERSINOP: NR 3 e 8 |
| Parâmetros | Condições gerais de água e esgoto de cada agência **não localizadas**; multa, juros, média, aviso de corte e religação: não confirmados |

#### 2.12 Outras agências vistas apenas por lista

- **AGERR/Pantanal (MT)**: aparece na lista de tarifa social da ANA com 18 prestadores municipais; norma não pesquisada.
- **ORCISPAR, AMR e CAGEPAR (PR)**, **AGESB (São Borja), AGESG e AGER (RS)**: citadas por Corsan, ANA ou IBEAS; normas não pesquisadas.
- **ARSEP**: ver pendências. É a Agência Reguladora de Serviços Públicos do Rio Grande do Norte, não de MS.

### 3. Valores de pré-carga sugeridos (todos sobrescrevíveis por tenant)

| Parâmetro | Valor sugerido | Observação |
|---|---|---|
| Multa de mora | 2% | Coincide em AGERGS, AGESAN (Corsan), AGEPAR, ADASA (até 2%), AGR, AGEMS |
| Juros | 1% ao mês pro rata para RS (Corsan) e GO; 0,033% ao dia para PR, DF e MS | O SAMAE de Caxias usa 0,5% ao mês; AGER/MT remete à lei |
| Correção | Por tenant: AGERGS índice do reajuste; PR e MS IPCA; DF e GO INPC | Não há padrão |
| Aviso de corte | 30 dias | Piso de Lei 11.445, art. 40 §2; AGERGS/AGESAN para essencial 60 dias |
| Dias vedados | Início de corte de sexta a domingo, feriado e véspera | GO permite sexta até 12 h; AGESAN veda só fim de semana e feriado |
| Média sem leitura | Ciclos e meses por tenant: 6 meses (AGERGS, GO, MS), 5 (PR), 12 (AGESAN Corsan, AGER/MT; DF segundo o levantamento 08, não reverificado) | Limite de ciclos: 3 (AGERGS, AGESAN Corsan, MS), 6 (GO, AGER/MT); no PR a norma só trata de sanção após 3 ciclos de acesso impedido |
| Tarifa social | Piso federal de 50% até 15 m³; teto maior por tenant: 20 m³ (Campo Grande e, por notícias de 2026, GO), 30 m³ (DF) | Desconto sobre a tarifa fixa: PR e DF sim; MS não (segundo a AGEMS) |
| Religação | 24 h (AGERGS, GO, AGER/MT), 4 h (PR), 48 h (MS) | Indevida: 3 h (DF), 4 h (RS-AGERGS, PR), 6 h (GO e MS), 12 h (AGESAN, AGER/MT) |
| Contagem de prazo de atendimento | Por tipo de prazo: GO usa dias úteis nos prazos de resposta; as demais normas lidas usam dias sem qualificação | Marcar cada prazo como corrido ou útil no perfil |

Nenhum valor acima é conselho jurídico. Os valores pré-carregados devem sair apenas de campos marcados como Confirmado.

## Anexo E — Nordeste e Norte

Data da pesquisa: 22/10/2026. Escopo: ERIs de saneamento do Nordeste e do Norte. Complementa `docs/levantamento/08-regulatorio-saneamento-e-tarifas.md` (seções 5 e d.2). Os atos catalogados estão em `04-eris.json` (95 itens).

### Convenções

- **[C]** confirmado em texto lido do ato (artigo indicado). **[S]** fonte secundária (imprensa, repositório, site do prestador, trecho de busca). **[NC]** não confirmado ou não localizado.
- "Cumpriu" e "Desobrigado" nas listas positivas da ANA de 2025 contam ambos como atendimento (coluna de atendimento = SIM em todas as linhas, exceto uma). A lista só traz quem atendeu: ausência significa "não consta".
- A lista positiva de 2025 cobre apenas as NR 3, 5, 6 e 8. As NR 9 a 16 ainda não passaram por verificação da ANA; para elas valem só os atos da própria agência.
- Etapa da tarifa social: lista positiva da ANA de julho/2026 (planilha mensal), com a data de preenchimento informada pela agência.
- Fontes das listas da ANA: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/conformidade-com-normativos-da-ana-e-decretos-governamentais-monitoramento/servicos-de-abastecimento-de-agua-potavel-e-esgotamento-sanitario200b-disponivel-em-20-12-2025 e https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/lista-positiva

### Correções de nomenclatura em relação ao pedido

| Pedido | Encontrado |
|---|---|
| AGERBA (BA) | A agência de saneamento da Bahia é a **AGERSA** (Agência Reguladora de Saneamento Básico do Estado da Bahia, Lei 12.602/2012). A AGERBA regula energia, transportes e comunicações. Não há outra agência baiana de saneamento. |
| ARSEP (RN) | Confirmado: Agência Reguladora de Serviços Públicos do Rio Grande do Norte (ARSEP). Natal tem a ARSBAN e Mossoró a AGRM. |
| AGERSA/ARPB (PB) | **ARPB** - Agência de Regulação do Estado da Paraíba. |
| AGRESPI/ARSEPI (PI) | **AGRESPI** (estadual) e **ARSETE** (Teresina). |
| ARSEMA (MA) | A ARSEMA existiu (resoluções de 2011 a 2016). O site oficial de saneamento hoje é o da **MOB** (Agência Estadual de Mobilidade Urbana e Serviços Públicos). Na lista da ANA aparecem MOB (NR 5) e AGERT (Timon). |
| ARSE/agência de SE | **AGRESE** (Agência Reguladora de Serviços Públicos do Estado de Sergipe). |
| ARCON-PA/AGENERPA | **ARCON-PA** (estadual) e **ARBEL** (Belém, antiga AMAE). Não encontrei "AGENERPA". |
| ARSEPAM/AGEMAN (AM) | **AGEMAN** (Manaus). Nenhuma ERI estadual do Amazonas aparece nas listas da ANA; ARSEPAM não foi confirmada. |
| AGEAP/ARSAP (AP) | **ARSAP** (Agência Reguladora de Serviços Públicos Delegados do Estado do Amapá). |
| RR | Sem ERI estadual. A Prefeitura de Boa Vista criou a Agência Reguladora Municipal (ARM) em 2025. |
| TO | **ATR** (estadual) e **ARP** (Palmas). |

### Divergências e acréscimos em relação ao levantamento 08

1. **ARCE**: o "32 municípios" do levantamento é o número de municípios que atenderam todas as NR do ciclo 2025 (NR 3, 5, 6 e 8). Individualmente a ARCE aparece na NR 8 com 183 municípios (água) e 181 (esgoto) e, nas NR 3, 5 e 6, como "Desobrigado" em 42, 31 e 184 municípios. O levantamento deixou a tarifa social da ARCE como não pesquisada: o art. 77, I da Res. 130 remete a "resolução específica" que não consta da lista de resoluções; a Cagece aparece na lista positiva da tarifa social sob a ACFor, não sob a ARCE.
2. **ARPE (172)**: confirmado como número de municípios com todas as NR do ciclo. Antes não pesquisada; agora há Res. 085/2013, 292/2025 (tarifa social) e outras.
3. **ARSAL**: a lista de 2025 só traz NR 5 e 6 (como Desobrigado, 81 municípios), como o levantamento registrou. Em julho e agosto de 2026 a ARSAL editou as Res. 274 (diretrizes tarifárias), 275 (NR 5), 276 (NR 8) e 282 (NR 3), o que muda o quadro para o ciclo de 2026.
4. **Data de verificação da NR 11**: a notícia da própria ANA (gov.br, 09/01/2025) diz "a partir de 27 de maio de 2027". O texto da NR 11 usado no levantamento (art. 74 §3º) fala em 20/05/2027. A divergência persiste em fonte oficial e precisa ser resolvida no DOU.
5. **Prazo da tarifa social**: o levantamento adota 11/12/2026. A ARSAL, em comunicado oficial, conta 24 meses a partir de 10/12/2024 (portanto 10/12/2026). O governo do Piauí informou, em notícia de 25/03/2026, prazo "até julho de 2026" para as etapas da NR 13, sem base normativa que eu tenha localizado. Manter a data como pendência E-03 do levantamento.
6. Tarifa social estadual preexistente com critérios diferentes da Lei 14.898: AL (renda até 1,5 salário mínimo, imóvel até 60 m², consumo até 20 m³), TO (padrão do imóvel até 100 m², renda até 1,5 salário mínimo), RN (Caern: consumo de energia até 110 kWh, 40 m²), AP (Estado paga até 20 m³), SE (até a Portaria 15/2026: renda até 1/4 do salário mínimo e escala de descontos 50/30/20%). O produto precisa aceitar critérios locais, não só CadÚnico e BPC.

---

### 1. Ceará

#### 1.1 ARCE (Agência Reguladora de Serviços Públicos Delegados do Estado do Ceará)

| Campo | Valor |
|---|---|
| UF / abrangência | CE. Regulador único da água e do esgoto do estado desde 01/01/2024: Cagece em 152 municípios (a página da ARCE exclui Fortaleza; a situação atual da ACFOR [NC]) e 32 SAAEs, além da concessão de esgoto de Crato. |
| Condições gerais | **Res. ARCE 130/2010** (25/03/2010), alterada pelas Res. 159/2012, 244/2018, 05/2021, 08/2021, 19/2021, 20/2021 e 04/2022 [C]. Minuta substitutiva (NR 11) em audiência pública 08/2026 (23/03 a 30/04/2026); revogaria a 130, 159/2012 e 08/2021 e vigoraria 120 dias após a publicação. Publicação final [NC]. |
| Tarifa | Res. 259/2019 (atualização), 274/2020 (metodologia), 28/2024 (prestação direta), 25/2025 (tarifa média Cagece, tabela de 05/11/2025). Reajustes dos SAAEs por microrregião (Res. 09/2026 Centro-Norte, 15/2026 Oeste). |
| Tarifa social | Art. 77, I da Res. 130 remete a resolução específica da ARCE [NC: não localizada]. Cagece: residencial social até 10 m³ a R$ 2,33/m³ [S, site da Cagece]. Lista positiva ANA: Cagece sob **ACFor**, "Obtenção dos dados do CadÚnico e BPC" (27/02/2026). ARCE não consta. |
| NR da ANA | **Parcial.** Lista 2025: NR 8 sim (183 municípios); NR 3, 5 e 6 desobrigada; 32 municípios com todas as NR. Atos próprios: NR 8 = Res. 12/2025; NR 9 = Res. 03/2026 (indicadores operacionais; vínculo inferido); NR 11 = minuta 2026; NR 13, 15 e 16 sem ato. Fonte: lista positiva 2025 e página de saneamento da ARCE (agenda de transição, Res. 12/2024). |

| Parâmetro | Valor e artigo (Res. 130/2010) | Marca |
|---|---|---|
| Juros de mora | até 0,033% ao dia (art. 105) | [C] |
| Multa | 2% (art. 105) | [C] |
| Correção monetária | IPCA ou outro índice previsto na legislação (art. 105) | [C] |
| Vistoria e ligação | área urbana 3 d.u. + 5 d.u.; área rural 5 d.u. + 10 d.u. (art. 31). Estudo e informação de obra em 30 dias (art. 32); início de obras em 45 dias (art. 33) | [C] |
| Religação normal | 1 dia útil após o pedido, com débitos, taxa e multas pagos (art. 87, redação da Res. 05/2021). Exige quitação | [C] |
| Religação de urgência | facultativa, 4 h (art. 88) | [C] |
| Corte indevido | religação em 4 h sem ônus; crédito do maior entre o dobro da religação de urgência e 20% da fatura líquida (art. 79 §§6 e 7) | [C] |
| Aviso de corte | 30 dias (art. 79 §1); escrito, com motivo e faturas (§4) | [C] |
| Serviços essenciais | comunicar a ARCE com 30 dias (art. 82) | [C] |
| Dias e horários vedados | após 12 h de sexta e véspera de feriado nacional, estadual ou municipal (art. 85) | [C] |
| Média sem leitura | média aritmética dos últimos 6 meses medidos (art. 90 §1); no máximo 3 ciclos (art. 90 §2) | [C] |
| Intervalo de leitura | 27 a 33 dias (art. 91); plurimensal até 3 ciclos em casos listados (art. 92) | [C] |
| Vencimento | mínimo 5 d.u. após apresentação (10 d.u. categoria pública) (art. 101 §1); 6 datas (art. 104) | [C] |
| Devolução | pagamento em duplicidade devolvido até o próximo faturamento (art. 107); não devolver = erro não justificável, com pagamento em dobro (art. 107 §2); faturamento a maior com prescrição de 10 anos (art. 95) | [C] |
| Categorias | social (por resolução específica), residencial, comercial/serviços, industrial, pública (art. 77) | [C] |
| Serviços cobráveis | tabela de preços e prazos homologada (art. 35 e 113) | [C] |
| Vazamento oculto | revisão da fatura após eliminação comprovada (art. 98, Res. 08/2021 e 19/2021) | [C] (percentual [NC]) |

#### 1.2 ACFOR (Fortaleza) e ARIS-CE

| Campo | Valor |
|---|---|
| ACFOR | Res. ACFOR 02/2006 (condições gerais de Fortaleza, DOM 18/12/2006), 03/2007 e 10/2013; listadas como recepcionadas pela ARCE. Parâmetros [NC] (texto não lido). Cagece/Fortaleza aparece na lista da tarifa social como ACFor. |
| ARIS-CE | Res. 13/2022 e 34/2023 (condições gerais para municípios consorciados), 31/2023 e 26/2023 (tarifa social de Sobral e Ipueiras); recepcionadas pela ARCE. Parâmetros [NC]. |
| NR da ANA | ACFor: NR 6 desobrigada (1 município) na lista 2025. ARIS-CE: desconhecido. |

---

### 2. Pernambuco

#### 2.1 ARPE (Agência de Regulação dos Serviços Públicos Delegados do Estado de Pernambuco)

| Campo | Valor |
|---|---|
| UF / abrangência | PE. Compesa em todo o estado; concessões regionalizadas dos Sertão e RMR-Pajeú (Res. 347/2026, 354/2026, 355/2026). |
| Condições gerais | **Res. ARPE 085/2013** (08/10/2013) [C]. Base legal complementar: Decreto Estadual 18.251/1994 (Regulamento Geral da Compesa), alterado por decretos de 2014 e 2015 [S]. Contrato de adesão homologado pela Res. 278/2024. Adequação à NR 11 [NC]. |
| Tarifa | Res. 289/2025 (nova estrutura, vigência 26/04/2025), 290/2025 (preços e prazos), 230/2023 (reajuste anterior). Reajuste posterior [NC]. |
| Tarifa social | **Res. ARPE 292/2025** (25/04/2025) [C] e **289/2025** [S]. Compesa: "Concluído" na lista da ANA (23/02/2026). |
| NR da ANA | **Sim, parcial.** Lista 2025: NR 3 e 5 (185 municípios), NR 6 desobrigada (185), NR 8 (172); 172 municípios com todas as NR. Atos: Res. 295/2025 (NR 8, indicadores), 308/2025 (NR 3), 309/2025 (NR 5), 292/2025 (tarifa social). NR 10, 11, 13 (estrutura), 15 e 16: [NC]. |

| Parâmetro | Valor e artigo | Marca |
|---|---|---|
| Juros de mora | até 0,033%/dia (art. 108) | [C] |
| Multa | 2% (art. 108) | [C] |
| Correção monetária | não prevista no caput do art. 108 | [C] |
| Vistoria e ligação | urbana 10 d.u. + 15 d.u.; rural 20 d.u. + 30 d.u. (art. 35). Prazos de serviços cobráveis na Res. 290/2025 (ex.: ramal 15 dias; esgoto até 5 m, 5 dias) | [C] |
| Religação normal | até 48 h (art. 90) | [C] |
| Religação de urgência | facultativa, 24 h (art. 91) | [C] |
| Suspensão indevida | religação em 24 h; crédito do dobro do valor líquido da primeira fatura após a religação (art. 81 §§6 e 7) | [C] |
| Aviso de corte | 30 dias (art. 81 §1) | [C] |
| Dias e horários vedados | após 17 h de sexta e véspera de feriado (art. 87) | [C] |
| Serviços essenciais | comunicar a ARPE com 30 dias (art. 84) | [C] |
| Média sem leitura | média aritmética de 6 meses (art. 93 §1); limite de ciclos [NC] | [C] / [NC] |
| Vencimento | 5 d.u. (10 d.u. público) (art. 103); 6 datas (art. 107) | [C] |
| Duplicidade | devolução automática até o próximo faturamento; não devolver = dobro (art. 110) | [C] |
| Cadastro | histórico de 60 ciclos de leitura e faturamento (art. 77) | [C] |
| Tarifa social | desconto ≥ 50% na primeira faixa e nos primeiros 15 m³ (Res. 292, art. 3); elegibilidade CadÚnico com renda per capita até 1/2 salário mínimo, BPC ou imóvel Habitacional Popular Faixa 1 (art. 4); 1 economia por grupo familiar (art. 5); inclusão automática (art. 6); 3 meses de transição com aviso na fatura (art. 7). Valor: R$ 27,50 até 10 m³ (água) e R$ 55,00 com esgoto; sem cobrança de 10 a 15 m³ (portal da Compesa) | [C] / [S] |
| Esgoto | entre 40% e 100% da água (contrato de adesão, Res. 278/2024) | [S] |

---

### 3. Bahia

#### 3.1 AGERSA (Agência Reguladora de Saneamento Básico do Estado da Bahia)

| Campo | Valor |
|---|---|
| UF / abrangência | BA. Embasa e serviços autônomos de água e esgoto dos municípios conveniados. |
| Condições gerais | **Res. AGERSA 002/2017** (17/07/2017), texto compilado com as Res. 002/2019 e 005/2019, atualizado no site em 29/05/2026 [C]. Anexo I (contrato de adesão) e Anexo II (tabela de prazos). Decreto 3.060/1994 (regulamento anterior da Embasa) ainda citado em contratos [S, A_CONFIRMAR]. |
| Tarifa | Reajustes anuais por resolução: 002/2025 (4,98%, desde 18/07/2025) e 001/2026 (3,57%, desde 20/07/2026). Audiência pública 003/2026 (23/10/2026) sobre a metodologia de reajuste. |
| Tarifa social | Sem resolução final. **Consulta Pública 003/2026** (26/09 a 26/10/2026) [S]. Embasa: tarifa mínima de 6 m³ (residencial social R$ 16,28; normal R$ 44,77 desde 20/07/2026) [S]. Lista da ANA: "Obtenção dos dados do CadÚnico e BPC" (24/02/2026). |
| NR da ANA | **Sim, parcial a ampla.** Lista 2025: NR 3, 5 e 8 (344 municípios), NR 6 desobrigada; 344 municípios com todas as NR. Atos: Res. 002/2026 (NR 9, 21/07/2026). NR 10 em audiência (23/10/2026). NR 11, 13, 15 e 16 [NC]. |

| Parâmetro | Valor e artigo (Res. 002/2017) | Marca |
|---|---|---|
| Juros, multa, correção | juros e multa por dia de atraso e correção pelo INPC, sem percentuais no texto, remetidos à legislação (art. 105) | [C] (percentuais [NC]) |
| Vistoria e ligação | Anexo II (Tabela de Prazos dos Serviços) | [NC] (não transcrita) |
| Religação normal e de urgência | prazos do Anexo II; contagem a partir da comunicação de pagamento ou baixa do débito; urgência a partir da solicitação (art. 83 e 123) | [C] (prazos [NC]) |
| Corte indevido | religação em até 6 h da constatação (art. 121) | [C] |
| Aviso de corte | 30 dias com comprovação de recebimento (art. 113 §1; art. 120, I, b); 3 dias por razões técnicas ou de segurança (art. 120, I, a); aviso em destaque na fatura admitido (art. 118) | [C] |
| Classes protegidas | saúde, educação, internação coletiva e baixa renda com tarifa social: 60 dias entre vencimento e interrupção (art. 119 §2) | [C] |
| Dias e horários vedados | não localizados no texto | [NC] |
| Média sem leitura | média dos últimos 12 meses com ao menos 4 consumos reais; 3 ciclos consecutivos (art. 85 §§1 e 2) | [C] |
| Devolução em dobro | faturamento a maior devolvido em dobro (CDC art. 42), prescrição de 5 anos (art. 101); duplicidade devolvida em crédito (art. 107) | [C] |
| Vencimento | 6 datas (art. 96); prazo mínimo entre apresentação e vencimento [NC] | [C] / [NC] |
| Esgoto | Embasa cobra 80% (rede) ou 45% (caixa de reunião) do volume de água (FAQ do prestador) | [S] |

---

### 4. Alagoas

#### 4.1 ARSAL (Agência Reguladora de Serviços Públicos do Estado de Alagoas)

| Campo | Valor |
|---|---|
| UF / abrangência | AL. 87 municípios: concessões regionalizadas (Casal como produtora de água) e 12 municípios operados pela Casal. SAAEs não são regulados pela ARSAL. |
| Condições gerais | **Res. ARSAL 137/2014** (05/06/2014), Regulamento dos Serviços de Saneamento, alterada pela Res. 34/2022 [C]. Res. 18/2016 (condições técnico-operacionais). Novo Regulamento em elaboração, sem data [S]. |
| Tarifa | Res. 233/2025 (estrutura Casal, em vigor desde 08/12/2025) [S]; Res. 274/2026 (diretrizes tarifárias) [S]; Res. 165/2024 (indicadores nos reajustes dos serviços regionalizados). |
| Tarifa social | **Res. ARSAL 12/2022** [C], em revisão: Consulta Pública 002/2026 (14 a 28/04/2026) propõe nova resolução que a revoga; versão final [NC]. ARSAL não consta na lista positiva da ANA (jul/2026). |
| NR da ANA | **Parcial em 2025 e em expansão em 2026.** Lista 2025: NR 5 e NR 6 desobrigadas (81 municípios), NR 3 e NR 8 não constam. Atos 2026: Res. 282 (NR 3, 12/08), 275 (NR 5, 01/07), 276 (NR 8, 01/07), 274 (diretrizes tarifárias); CP 07/2026 (NR 9); adequação à NR 10 prevista no cronograma de consultas. NR 11 e 13: apenas a minuta da tarifa social. |

| Parâmetro | Valor e artigo (Res. 137/2014) | Marca |
|---|---|---|
| Juros de mora | até 0,033%/dia (art. 83) | [C] |
| Multa | 2% (art. 83) | [C] |
| Correção monetária | valores corrigidos, sem índice nomeado no trecho lido (art. 83) | [C] / índice [NC] |
| Vistoria e ligação | ligação em até 10 d.u.: 3 d.u. de inspeção + 7 d.u. de execução (art. 23) | [C] |
| Religação normal | até 48 h após o motivo cessado, mediante comprovação de pagamento ou renegociação (art. 101) | [C] |
| Religação de urgência | 6 h (art. 102) | [C] |
| Corte indevido | religação em 6 h; crédito do maior entre o dobro da religação de urgência e 20% (art. 96) | [C] |
| Aviso de corte | emitido em até 90 dias da ocorrência e com antecedência mínima de 15 dias (art. 92, p.u.); contas pendentes avisadas na fatura seguinte (art. 83 §2) | [C] |
| Dias e horários vedados | sábados, domingos, feriados e suas vésperas (art. 99), salvo irregularidades nas instalações | [C] |
| Média sem leitura | média dos últimos 180 dias; acima de 180 dias, a última média conhecida; depois o consumo mínimo; 3 ciclos (art. 67) | [C] |
| Cobrança complementar | limitada a 3 meses retroativos (art. 72, I); devolução do indébito em dobro (art. 72, II) | [C] |
| Vencimento | mínimo 5 d.u. (art. 79); 6 datas (art. 82) | [C] |
| Atendimento | telefone 14 h por dia, inclusive fins de semana (art. 112); comercial das 8 h às 18 h (§1) | [C] |
| Tarifa social (Res. 12/2022) | renda até 1,5 salário mínimo, imóvel até 60 m², consumo até 20 m³, adimplência e recadastramento anual (serviço Alagoas Digital); Casal 2026: 50% da tarifa mínima residencial até 10 m³ e 50% da tarifa da faixa de 11 a 20 m³ | [C] / [S] |

---

### 5. Rio Grande do Norte

#### 5.1 ARSEP-RN, ARSBAN (Natal) e AGRM (Mossoró)

| Campo | Valor |
|---|---|
| UF / abrangência | RN. ARSEP: Caern em todos os municípios, exceto Natal (ARSBAN) e Mossoró (AGRM), por delegação (LC 584/2016 e LC 682/2021). |
| Condições gerais | **Res. Normativa ARSEP 02/2016** (08/11/2016) [S: texto lido no LegisWeb; PDF oficial não localizado]. Natal: Res. ARSBAN 004/2008 (19/06/2008) e 003/2014 [S, vigência NC]. Mossoró: sem norma localizada. |
| Tarifa | Res. ARSEP 9/2023 (estrutura, 28/12/2023) [S]; Resolução Normativa Conjunta ARSEP/AGRM 001/2026 (ARSEP Res. 5/2026, 29/04/2026): reajuste de 3,79% [S]; Res. ARSEP 3/2025 (6%). |
| Tarifa social | Sem resolução da ARSEP localizada. A Caern aplica norma interna de 08/06/2020 (consumo ≤ 10 m³, energia ≤ 110 kWh, área ≤ 40 m²) [S]. Lista da ANA: ARSEP/RN e ARSBAN, ambas "Obtenção dos dados do CadÚnico e BPC" (27 e 26/02/2026). |
| NR da ANA | **Parcial.** ARSEP-RN: NR 3 (165 municípios), NR 5 (13, desobrigada), NR 6 (166, desobrigada); NR 8 não consta; nenhum município com todas as NR. ARSBAN e AGRM: NR 6 desobrigada. |

| Parâmetro | Valor e artigo (RN ARSEP 02/2016) | Marca |
|---|---|---|
| Juros, multa, correção | percentuais não localizados no trecho lido (art. 141, XV exige constar na fatura; arts. 135 e 137 citam INPC na devolução) | [NC] |
| Vistoria e ligação | urbana 3 d.u. + 5 d.u.; rural 5 d.u. + 10 d.u. (art. 43) | [S] |
| Religação | normal até 48 h (art. 121); urgência facultativa 4 h (art. 122); suspensão indevida 4 h (art. 106 §5) com crédito do dobro da religação de urgência (§6) | [S] |
| Aviso de corte | 30 dias (art. 106 §1) | [S] |
| Dias e horários vedados | após 12 h de sexta, véspera de feriado e todo o sábado e domingo (art. 116) | [S] |
| Média sem leitura | 6 meses (arts. 127 e 128); 3 ciclos; depois consumo mínimo | [S] |
| Vencimento | mínimo 5 d.u. (10 público) (art. 140 §1); número de datas não fixado | [S] |
| Devolução em dobro | faturamento a maior em dobro, salvo engano justificável (art. 134, II) | [S] |
| Esgoto (tarifa) | Caern: 70% (convencional), 35% (condominial), 100% (poço tubular) da tarifa de água (Res. Caern 26/03/2025) | [S] |

---

### 6. Paraíba

#### 6.1 ARPB (Agência de Regulação do Estado da Paraíba)

| Campo | Valor |
|---|---|
| UF / abrangência | PB. Cagepa e serviços autônomos municipais delegados. |
| Condições gerais | **Res. ARPB 002/2010** (24/02/2010) [C, hospedada pela Cagepa]. Res. ARPB 001/2012 (penalidades) [S]. Alterações e adequação à NR 11 [NC]. |
| Tarifa | Reajuste de 5,10% (Res. 010/2022, 29/12/2022) [S]; reajustes 2023 a 2026 [NC]. |
| Tarifa social | A estrutura da Cagepa tem tarifa social, excluída dos reajustes; norma específica [NC]. O art. 127 e o art. 138 da Res. 002/2010 vedam isenções e tarifas reduzidas [C], o que conflita com a Lei 14.898. Lista da ANA: "Em processo de reequilíbrio" (29/04/2026). |
| NR da ANA | **Parcial.** NR 3 (201 municípios) e NR 6 (desobrigada, 201); NR 5 e 8 não constam; sem município com todas as NR. |

| Parâmetro | Valor e artigo (ARPB 002/2010) | Marca |
|---|---|---|
| Multa e juros | multa de 2% mais juros de 1% ao mês pro rata die (art. 130) | [C] |
| Correção monetária | na devolução, juros e atualização monetária "conforme art. 130" (art. 197); índice [NC] | [C] / [NC] |
| Aviso de corte | aviso de débito com 30 dias antes da ordem de suspensão (arts. 112 e 130 §2) | [C] |
| Dias e horários vedados | de 12 h de sexta até 8 h de segunda; véspera de feriado até 8 h do dia útil seguinte (art. 116) | [C] |
| Religação | até 48 h (art. 121) | [C] |
| Urgência, indevida | [NC] (não localizadas) | [NC] |
| Média sem leitura | [NC] | [NC] |
| Vencimento | 6 datas (art. 154); leituras de 27 a 33 dias (art. 143), plurimensal até 3 ciclos (art. 144) | [C] |
| Esgoto (tarifa) | 100% da tarifa de água (art. 124 §1) | [C] |

---

### 7. Piauí

#### 7.1 AGRESPI (estadual) e ARSETE (Teresina)

| Campo | Valor |
|---|---|
| UF / abrangência | PI. AGRESPI: concessão regionalizada da MRAE-Piauí (Águas do Piauí, Contrato 648/2024) e ex-Agespisa. ARSETE: Águas de Teresina (ATH). |
| Condições gerais | **Sem norma própria da AGRESPI** (14 resoluções de 2025 e 7 de 2026, nenhuma de condições gerais). Substitutos: contrato de concessão e o Manual de Procedimento do Atendimento aos Clientes da Águas do Piauí (v. 25/05) [S]. ARSETE: Res. 02/2012 (preços e prazos) [S]; condições gerais próprias [NC]. |
| Tarifa | AGRESPI: Res. 10/2024 (revisão quinquenal, +13,24% interior), 001/2023, 002/2021, 001/2020; reajuste de 2,078% em 01/03/2026 do prestador [S, ato NC]. ARSETE: Res. 78/2025 (3,254%, 28/05/2025) e reajuste de 6,618% (DOM 29/05/2026, vigência 28/06/2026) [S]. |
| Tarifa social | AGRESPI: sem norma; ata de 2026 trata de cronograma da TSAE na MRAE [S]; lista da ANA "Obtenção dos dados do CadÚnico e BPC" (22/01/2026). ARSETE: **Res. 77/2025** [S]; lista da ANA: mesma etapa (12/03/2025, data antiga). |
| NR da ANA | AGRESPI: **sim.** NR 3, 5, 6 e 8 (224/224/224/221 municípios); 221 com todas as NR. Atos: Res. 007/2025 (NR 8), 010/2025 (NR 3), 011/2025 (NR 5), 006/2025 (soluções alternativas). ARSETE: NR 5 e 6 desobrigadas (1 município). |

| Parâmetro | Valor | Marca |
|---|---|---|
| Juros, multa, correção, prazos, aviso, dias vedados, média | não há norma da agência; consultar o contrato 648/2024 e o manual do prestador | [NC] |
| Serviços do prestador (site Águas do Piauí) | religação por corte no cavalete em 1 dia, urgência em 4 h, religação com bucha em 7 dias, supressão total em 7 dias, reposição de hidrômetro em 3 dias; esgoto a 80% | [S] |
| ARSETE | esgoto cobrado apenas com ponto de conexão disponível (IN 01/2025, Res. 80/2025); usuário residencial social isento da taxa de ligação de esgoto | [S] |

---

### 8. Maranhão

#### 8.1 MOB/ARSEMA e AGERT (Timon)

| Campo | Valor |
|---|---|
| UF / abrangência | MA. Caema (156 sistemas de água; esgoto em São Luís e Imperatriz). O site da MOB é a página oficial de saneamento (atos da ARSEMA de 2011 a 2016). Quem regula hoje a Caema e a concessão em São Luís não foi confirmado. |
| Condições gerais | **Res. ARSEMA 001/2012** (24/04/2012), Regulamento de Serviços da Caema [NC: PDF em domínio extinto]. |
| Tarifa | Res. ARSEMA 16/2016 (estrutura) e reajustes de 2014 e 2015; tabela vigente [NC]. |
| Tarifa social | Nenhum ato localizado para a Caema. Timon (AGERT/Águas de Timon): "Em processo de reequilíbrio" (30/01/2025). |
| NR da ANA | MOB: NR 5 (11 municípios, só água). AGERT: NR 5 e 6 desobrigadas. Demais NR sem ato localizado. |

| Parâmetro | Valor | Marca |
|---|---|---|
| Todos | texto da Res. 001/2012 não lido | [NC] |
| Penalidades | multa até 1% do faturamento anual; prazos de notificação de 3 a 180 dias úteis (Res. ARSEMA 10/2014, arts. 7 e 20) | [S] |

---

### 9. Sergipe

#### 9.1 AGRESE (Agência Reguladora de Serviços Públicos do Estado de Sergipe)

| Campo | Valor |
|---|---|
| UF / abrangência | SE. Microrregião de Água e Esgoto de Sergipe (MAES): Iguá Sergipe; Deso fornece água tratada ao atacado. |
| Condições gerais | **Res. AGRESE 96/2025** (29/12/2025), Regulamento Geral alterado (Anexo Único), revisão em até 4 anos (art. 227) [C]. Decreto 30.995/2018 revogou o regulamento antigo da Deso. Contestada por ação popular anunciada (resultado [NC]). |
| Tarifa | Res. 78/2025 (reajuste Iguá 2025-2026), 76/2025 (água tratada da Deso, R$ 2,21/m³). Tarifa mínima suspensa nos municípios da Iguá (notícia 2026) [S]. |
| Tarifa social | **Portaria 15/2026** (11/03/2026) [C] e 19/2026 [S]. Lista da ANA: "Obtenção dos dados do CadÚnico e BPC" (04/03/2026). |
| NR da ANA | **Sim.** Lista 2025: NR 3, 5 e 6 (74 municípios) e NR 8 (72); 72 municípios com todas as NR. Atos: Res. 84/2025 (NR 8) e 85/2025 (NR 5). |

| Parâmetro | Valor e artigo (Res. 96/2025) | Marca |
|---|---|---|
| Juros, multa, correção | remetidos ao contrato e às normas da AGRESE, sem percentual (art. 156) | [C] (percentuais [NC]) |
| Vistoria e ligação | urbana 5 d.u. (vistoria e laudo) + 10 d.u. (ligação) (art. 60) | [C] |
| Religação | até 48 h da identificação do pagamento (art. 130); corte indevido religado em 12 h (art. 131) | [C] |
| Aviso de corte | inadimplência superior a 30 dias após notificação da possibilidade de suspensão (art. 118, V); interrupção programada divulgada com 24 h (art. 121, I) | [C] |
| Dias e horários vedados | [NC] | [NC] |
| Média sem leitura | falha do hidrômetro: média de 12 meses (artigo da aferição a conferir); acima de 3 meses sem leitura por impedimento de acesso é irregularidade do usuário (art. 140 §3) | [C] / [NC] |
| Vencimento | 6 datas (art. 155); fatura entregue após o vencimento isenta multa e juros, com novo prazo de 5 d.u. (art. 156 §4); leituras de 27 a 33 dias (art. 146) | [C] |
| Rateio de áreas comuns | diferença entre totalizador e individuais dividida igualmente entre economias ativas (art. 92) | [C] |
| Tarifa social (Portaria 15/2026) | 50% de desconto em água e esgoto até 15 m³; renda per capita até 1/2 salário mínimo; CadÚnico/BPC com atualização de até 2 anos; inclusão automática; ligação irregular impede; relatório mensal | [C] |

---

### 10. Pará

#### 10.1 ARCON-PA

| Campo | Valor |
|---|---|
| UF / abrangência | PA. Municípios delegados; MRAE (Belém, Ananindeua, Marituba) com a ARCON-PA como reguladora exclusiva (Res. MRAE 5/2024 [S]). |
| Condições gerais | **Res. ARCON-PA 03/2021** (03/11/2021) [C]. Res. 01/2026 (fiscalização e penalidades, 16/04/2026) [C]. |
| Tarifa | Nenhuma resolução tarifária de saneamento localizada na página oficial. |
| Tarifa social | **Res. ARCON-PA 03/2026** (19/08/2026) [C]. ARCON-PA não consta na lista da ANA. |
| NR da ANA | **Não consta na lista de 2025.** Atos de 2026: Res. 004 (NR 8), 005 (NR 5), 008 (soluções alternativas, NR 8), 003 (tarifa social; cita NR 4 e NR 13). Datas de 004, 005 e 008 [NC]. |

| Parâmetro | Valor e artigo (Res. 03/2021) | Marca |
|---|---|---|
| Juros, multa, correção | até 0,033%/dia, 2% e IPCA (art. 107) | [C] |
| Vistoria e ligação | urbana 3 d.u. + 5 d.u. (art. 32); rural 5 e 10 d.u. | [C] |
| Religação | até 24 h (art. 87); urgência facultativa 12 h (art. 88); corte indevido 4 h (art. 79 §6) | [C] |
| Aviso de corte | 30 dias (art. 79 §1); manutenção programada com 48 h (art. 79, IV) | [C] |
| Dias vedados | sextas, sábados, domingos, véspera e dia de feriado (art. 85) | [C] |
| Média sem leitura | 6 meses (art. 90 §1); limite de ciclos [NC] | [C] / [NC] |
| Vencimento | 5 d.u. (10 público) (art. 103); 6 datas (art. 106); leituras de 27 a 33 dias (art. 91) | [C] |
| Tarifa social (Res. 03/2026) | renda per capita até 1/2 salário mínimo com CadÚnico, ou BPC (art. 3); BPC e Bolsa Família fora da renda; 3 meses de transição (art. 3 §2); perda por irregularidade com aviso por 3 meses (art. 5). Percentual e limite de m³: lei federal [NC] no texto | [C] |

#### 10.2 ARBEL (Belém)

| Campo | Valor |
|---|---|
| UF / abrangência | PA. Cosanpa no município de Belém. |
| Condições gerais | **Res. ARBEL (CSA/AMAE) 002/2017** (06/07/2017), alterada no art. 97 pela Res. 003/2023 e com o parágrafo único revogado pela Res. 001/2024 [C]. |
| Tarifa | Res. 004/2023 (procedimentos RTP e reajuste), 002/2023 (reajuste), Res. Normativa 002/2022 (28/04/2022; base da tabela de 2023). Reajustes 2024 a 2026 [NC]. |
| Tarifa social | Não consta na lista da ANA; norma local [NC]. |
| NR da ANA | Lista 2025: NR 6 desobrigada (1 município). Demais [NC]. |

| Parâmetro | Valor e artigo (Res. 002/2017) | Marca |
|---|---|---|
| Juros, multa, correção | até 0,033%/dia, 2% e correção (art. 108) | [C] |
| Religação | até 48 horas **úteis** após a solicitação (art. 90); urgência até 24 horas úteis (art. 91); corte indevido 4 horas úteis (art. 82 §5) | [C] |
| Aviso de corte | 30 dias (art. 82 §1) | [C] |
| Dias vedados | sextas, sábados, domingos, véspera e dia de feriado (art. 88) | [C] |
| Média sem leitura | 6 meses (art. 93 §1); até 6 ciclos; depois o consumo mínimo (art. 93 §§2 e 4) | [C] |
| Vencimento | 6 datas (art. 107) | [C] |

---

### 11. Amazonas

#### 11.1 AGEMAN (Manaus)

| Campo | Valor |
|---|---|
| UF / abrangência | AM. Águas de Manaus (Manaus Ambiental), concessão do município de Manaus. Regulador estadual do AM [NC]. |
| Condições gerais | **MPSAU** (Manual de Prestação e Utilização dos Serviços), criado em 2010, atualização parcial em 2018; revisão em consulta pública (23/02 a 24/03/2026) com publicação final prevista até 30/04/2026. Versão de 58 páginas lida no site da concessionária (08/2026) [S]; se é a versão final [NC]. |
| Tarifa | Reajuste de 5,52% aplicado em março/2026 sem homologação; **liminar de 11/09/2026** suspendeu o aumento e mandou reemitir faturas. Res. Normativa AGEMAN 003/2023 (reajuste extraordinário, TAG). |
| Tarifa social | "Tarifa 10" (Decreto 5.519/2023) [S]; tarifa social da concessionária de 0 a 15 m³. AGEMAN não consta na lista da ANA. |
| NR da ANA | Lista 2025: NR 5 e 6 desobrigadas (1 município). Demais [NC]. |

| Parâmetro | Valor e artigo (MPSAU 2026) | Marca |
|---|---|---|
| Ligação e religação | nova economia em 15 dias corridos; religação de economia antes ligada em 72 horas (art. 96) | [S] |
| Corte | inadimplência autorizada nos termos do contrato e do art. 40 da Lei 11.445 (art. 8, XXI); aviso, dias vedados e prazos [NC] | [NC] |
| Juros, multa, correção | [NC] | [NC] |
| Esgoto | 80% da tarifa de água (RN 003/2023 e site da concessionária) | [S] |

---

### 12. Rondônia

#### 12.1 AGERO

| Campo | Valor |
|---|---|
| UF / abrangência | RO. CAERD, SAAEs e SPEs (Águas de Jaru, Pimenta Bueno, ENORSUL, Aegea em Rolim de Moura). Rolim de Moura e Buritis têm ERIs municipais (AGERROM, AGERB). |
| Condições gerais | **Sem norma própria localizada.** Substituto: Decreto Estadual 4.334/1989 (Regulamento da CAERD) [S, vigência NC]. |
| Tarifa | Res. 101/2026 (modelos de regulação tarifária, 04/08/2026); reajustes: RD 082/2025 (CAERD, 07/2025), 083/2025 (Jaru), 084/2025 (Pimenta Bueno). Res. 108/2026 (14/09/2026) [objeto NC]. |
| Tarifa social | CAERD adotou tarifa social em dezembro/2024 por instrução interna (IN 027/2024) [S]. Lista da ANA: CAERD "Em processo de reequilíbrio"; demais prestadores "Obtenção dos dados do CadÚnico e BPC" (09/04/2026). |
| NR da ANA | AGERO: NR 3 desobrigada (4 municípios); demais não constam. Ato próprio: Res. 101/2026 (NR 6). AGERROM: NR 3 cumprida; NR 5 e 6 desobrigadas. AGERB: NR 5 e 6 desobrigadas. |

| Parâmetro | Valor | Marca |
|---|---|---|
| Média sem leitura | consumo médio, ou mínimo se maior (Decreto 4.334/1989, art. 96) | [S] |
| Consumo mínimo | não inferior a 10 m³/mês por economia (art. 94) | [S] |
| Demais (juros, multa, corte, religação, prazos) | [NC] | [NC] |

---

### 13. Acre

#### 13.1 AGEAC

| Campo | Valor |
|---|---|
| UF / abrangência | AC. Saneacre (antigo Depasa), SAERB (Rio Branco) e demais prestadores. |
| Condições gerais | **Res. AGEAC 66/2019** (10/12/2019) [C]. Res. 34/2015 (serviços delegados). |
| Tarifa | Res. 95/2023 (estrutura do Saneacre) e 98/2023 (SAERB) [S]. |
| Tarifa social | **Res. AGEAC 96/2023** (condições gerais para tarifa social do Saneacre); data [NC] (lista: 07/11/2023; notícia: reunião de 04/12/2023). AGEAC não consta na lista positiva da ANA. |
| NR da ANA | Lista 2025: NR 3 e 5 (22 municípios), NR 6 desobrigada; NR 8 não consta. |

| Parâmetro | Valor e artigo (Res. 66/2019) | Marca |
|---|---|---|
| Juros, multa, correção | "conforme legislação e contratos" (art. 100) | [C] (percentuais [NC]) |
| Aviso de corte | 30 dias com comprovação de recebimento; suspensão só após 15 dias do vencimento (art. 110, I) | [C] |
| Classes protegidas | saúde, educação, internação e baixa renda com tarifa social: prazos que preservem a saúde (art. 113) | [C] |
| Dias e horários vedados | após 12 h de sexta e véspera de feriado (art. 114) | [C] |
| Religação de urgência | facultativa, 6 h (art. 118) | [C] |
| Intervalo de leitura | 27 a 33 dias (art. 89 §1) | [C] |
| Religação normal, média sem leitura, ligação | [NC] | [NC] |

---

### 14. Amapá

#### 14.1 ARSAP

| Campo | Valor |
|---|---|
| UF / abrangência | AP. Concessionária de Saneamento do Amapá (CSA), Contrato 001/2021 (16 municípios). A CAESA não opera mais as sedes urbanas. |
| Condições gerais | **Sem norma própria localizada**; vale o contrato de concessão [NC]. |
| Tarifa | Res. Homologatória 009/2026 (30/07/2026): +6,5173%, vigência 30/08/2026 a 29/08/2027 [C]; 008/2025 (+8,1137%); esgoto a 100% da água. |
| Tarifa social | Decreto Estadual 1.644/2022 (Estado paga a tarifa social até 20 m³ por serviço para famílias do CadÚnico) [S]. ARSAP não consta na lista da ANA. |
| NR da ANA | Não consta na lista de 2025. Atos [NC]. |

Parâmetros de corte, juros, multa, religação, prazos e média: [NC].

---

### 15. Roraima

| Campo | Valor |
|---|---|
| UF / abrangência | RR. CAER, em todo o estado. Sem ERI estadual. A Prefeitura de Boa Vista instituiu a Agência Reguladora Municipal (ARM), com audiência pública em 29/08/2025, para regular a CAER em Boa Vista; lei e atos [NC]. |
| Condições gerais | Contrato de adesão aprovado pela Resolução 002/2015 **do próprio prestador** (22/10/2015) [S]. |
| Tarifa | Recomposição linear de 8,26% por ato da diretoria da CAER (Res. 005/2024, vigência 03/02/2025); esgoto a 80% da água [S]. |
| Tarifa social | Categoria "Social" na tabela da CAER [S]; norma [NC]. |
| NR da ANA | Desconhecido (nenhuma ERI de Roraima nas listas). |

Parâmetros: [NC].

---

### 16. Tocantins

#### 16.1 ATR-TO e ARP (Palmas)

| Campo | Valor |
|---|---|
| UF / abrangência | TO. BRK Ambiental/Saneatins em 47 municípios (ATR); Palmas é regulada pela ARP. |
| Condições gerais | **Res. ATR 029/2009**, alterada pela Res. 068 (ambas citadas na página da ATR). Lido o texto original de 2009; a Res. 068 [NC]. |
| Tarifa | Res. ATR 013 (reajuste; ano NC). |
| Tarifa social | Critérios locais (Res. 005/2021 na página do programa; cadeia 054, 059, 090 e 098 na lista de resoluções): renda até 1,5 salário mínimo, imóvel de padrão baixo até 100 m², desconto de até 70% [S]. Lista da ANA: ARP/BRK "Obtenção dos dados do CadÚnico e BPC" (27/02/2026); a ATR não consta. |
| NR da ANA | ATR: NR 3 (62), NR 5 (63), NR 6 desobrigada (63); NR 8 não consta; sem município com todas as NR. ARP: NR 8 sim, NR 6 desobrigada. |

| Parâmetro | Valor e artigo (Res. ATR 029/2009, texto original) | Marca |
|---|---|---|
| Juros, multa, correção | até 0,033%/dia, 2% e IPCA (art. 103) | [C] |
| Aviso de corte | 30 dias (art. 77 §1) | [C] |
| Dias vedados | após 12 h de sexta e véspera de feriado (art. 83) | [C] |
| Religação | até 48 h (art. 85); urgência facultativa 4 h (art. 86); corte indevido 4 h (art. 77 §6) | [C] |
| Média sem leitura | média dos últimos **4** meses (art. 88 §1); 3 ciclos (§2) | [C] |
| Vencimento | 6 datas (art. 102) | [C] |

---

### Matriz comparativa dos parâmetros (apenas valores com texto lido)

| Agência (norma) | Juros/dia | Multa | Correção | Aviso de corte | Vedação de dias | Religação | Urgência | Corte indevido | Média sem leitura | Datas |
|---|---|---|---|---|---|---|---|---|---|---|
| ARCE (130/2010) | 0,033% | 2% | IPCA | 30 d | sex. 12 h e véspera | 1 d.u. | 4 h | 4 h | 6 m, 3 ciclos | 6 |
| ARPE (085/2013) | 0,033% | 2% | sem previsão | 30 d | sex. 17 h e véspera | 48 h | 24 h | 24 h | 6 m, ciclos NC | 6 |
| AGERSA (002/2017) | % NC | NC | INPC | 30 d (60 d nas classes protegidas) | NC | Anexo II | Anexo II | 6 h | 12 m (mín. 4 reais), 3 ciclos | 6 |
| ARSAL (137/2014) | 0,033% | 2% | sim | 15 d (aviso em ≤ 90 d) | sáb., dom., feriados e vésperas | 48 h | 6 h | 6 h | 180 d, 3 ciclos | 6 |
| ARSEP-RN (02/2016) [S] | NC | NC | NC | 30 d | sex. 12 h, véspera, sáb. e dom. | 48 h | 4 h | 4 h | 6 m, 3 ciclos | NC |
| ARPB (002/2010) | 1% a.m. | 2% | NC | 30 d | sex. 12 h a seg. 8 h | 48 h | NC | NC | NC | 6 |
| AGRESE (96/2025) | NC | NC | NC | 30 d após notificação | NC | 48 h | NC | 12 h | 12 m (aferição) | 6 |
| ARCON-PA (03/2021) | 0,033% | 2% | IPCA | 30 d | sex., sáb., dom., véspera e feriado | 24 h | 12 h | 4 h | 6 m | 6 |
| ARBEL (002/2017) | 0,033% | 2% | sim | 30 d | sex., sáb., dom., véspera e feriado | 48 h úteis | 24 h úteis | 4 h úteis | 6 m, 6 ciclos | 6 |
| AGEAC (66/2019) | NC | NC | NC | 30 d, só após 15 d de atraso | sex. 12 h e véspera | NC | 6 h | NC | NC | NC |
| ATR (029/2009) | 0,033% | 2% | IPCA | 30 d | sex. 12 h e véspera | 48 h | 4 h | 4 h | 4 m, 3 ciclos | 6 |

Observação para o perfil regulatório por tenant: nenhum dos valores acima é constante nacional. O perfil precisa guardar, com vigência no tempo e artigo de origem, pelo menos: juros (taxa e base), multa, índice de correção, aviso de corte (dias e meio), calendário de vedação (dias da semana, horário de corte, véspera e feriado), classes protegidas e seus prazos, prazos de vistoria, ligação, religação, urgência e religação por corte indevido (em horas ou dias, úteis ou corridos), base e limite de ciclos da média sem leitura, número de datas de vencimento e prazo mínimo entre apresentação e vencimento, regra de devolução (automática, em dobro, prescrição), critérios e escala da tarifa social (renda, m³, desconto) e fonte da base (CadÚnico, BPC, cadastro estadual).

## Anexo F — Espírito Santo, ERIs adicionais, atos da ANA e matriz ERI x NR 1 a 16

Data de referência da pesquisa: 22/10/2026. Arquivo de itens: `04-eris.json` (208 itens: 37 do ES, 12 da ANA e 159 das ERIs da parte B, após remover 6 duplicatas de outras partes). Convenção: "confirmado" significa texto do ato lido; "não confirmado" (NC) significa não lido, não localizado ou ambíguo. PDFs digitalizados lidos por OCR estão indicados nos apêndices.

### 1. Matriz ERI x NR 1 a 16

#### 1.1 Como ler

- Fonte do ciclo 2025: as cinco planilhas oficiais da lista positiva da ANA de 22/12/2025 (conjunto, NR 3, NR 5, NR 6, NR 8), baixadas do portal da ANA em 22/10/2026, e as planilhas de resíduos (NR 1 e NR 7). A lista só traz quem cumpriu ou foi desobrigado; a ausência de uma NR significa "não consta" (não comprovou ou não foi verificada), e não prova descumprimento. "Desobrigada" quer dizer que o município não depende de a ERI cumprir aquela NR, pelo tipo de contrato. A NR 8 foi exigida de todos.
- O ciclo 2023 e o ciclo 2024 verificaram só a NR 1 (resíduos sólidos, por município, sem ERI de água e esgoto): 437 municípios em 2023 e 67 em 2024. Para água e esgoto, a primeira verificação foi a de 2025 (NR 3, 5, 6 e 8). Nenhuma ERI de água e esgoto tem resultado de 2023 ou 2024.
- Ciclo 2026-2027: aviso de abertura 1/2026 (DOU 19/05/2026), módulos abertos de 20/05 a 20/08/2026, para NR 1, 3, 4, 5, 6, 7, 8, 9 e 10. O resultado não estava publicado em 22/10/2026 e deve sair até 21/12/2026 (20/12/2026 é domingo). Por isso a seção 1.3 mostra, para NR 9 e NR 10, só atos próprios, e não resultado da ANA.
- NR 11: verificação a partir de 20/05/2027. NR 13: verificação do regulamento a partir de 20/05/2028 (art. 69 §2º); a lista positiva da tarifa social segue regra própria, com prazo legal em 11/12/2026. NR 12 (drenagem) e NR 14 (indicadores de resíduos) estão fora do núcleo de água e esgoto. NR 15 (perdas) e NR 16 (contabilidade regulatória, Res. ANA 302, 28/08/2026) ainda não têm verificação em ciclo publicado. Para esses itens, a matriz lista só atos próprios localizados nas partes 1 a 6.
- Siglas: C = cumpriu; D = desobrigada; "mun." = municípios na planilha. NC = não confirmado. As linhas da planilha repetem o município por serviço (água e esgoto); a contagem abaixo conta municípios.

#### 1.2 Resultado do ciclo 2025 (ANA) por ERI

| UF | ERI (sigla) | Nome na planilha | NR 3 | NR 5 | NR 6 | NR 8 | Todas as quatro ("conjunto") | Resíduos 2025 |
|---|---|---|---|---|---|---|---|---|
| AC | AGEAC | Agência Reguladora dos Serviços Públicos do Estado do Acre | cumpriu (22 mun.) | cumpriu (22 mun.) | desobrigada (22 mun.) | não consta | não | - |
| AL | ARSAL-AL | Agência Reguladora de Serviços Públicos do Estado de Alagoas | não consta | desobrigada (81 mun.) | desobrigada (81 mun.) | não consta | não | - |
| AM | AGEMAN | Agência Reguladora dos Serviços Públicos Delegados do Município de Manaus | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| BA | AGERSA | Agência Reguladora de Saneamento Básico do Estado da Bahia | cumpriu (344 mun.) | cumpriu (344 mun.) | desobrigada (344 mun.) | cumpriu (344 mun.) | sim (344 mun.) | - |
| CE | ACFor | Autarquia de Regulação, Fiscalização e Controle dos Serviços Públicos de Saneamento Ambiental | não consta | não consta | desobrigada (1 mun.) | não consta | não | NR 1: 1 cumpriu, 0 não; NR 7: 1 cumpriu, 0 desob. |
| CE | ARCE | Agência Reguladora de Serviços Públicos Delegados do Estado do Ceará | desobrigada (42 mun.) | desobrigada (32 mun.) | desobrigada (184 mun.) | cumpriu (183 mun.) | sim (32 mun.) | - |
| DF | ADASA | Agência Reguladora de Águas, Energia e Saneamento Básico do Distrito Federal | desobrigada (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | sim (1 mun.) | NR 1: 1 cumpriu, 0 não; NR 7: 1 cumpriu, 0 desob. |
| ES | ARIES | AGENCIA REGULADORA INTERMUNICIPAL DE SANEAMENTO BASICO DO ESPIRITO SANTO | cumpriu (21 mun.) | desobrigada (21 mun.) | desobrigada (21 mun.) | cumpriu (21 mun.) | sim (21 mun.) | NR 1: 0 cumpriu, 20 não; NR 7: 2 cumpriu, 18 desob. |
| ES | ARSP/ES | ARSP - Agência de Regulação de Serviços Públicos do Espírito Santo | cumpriu / desobrigada (52 mun.) | cumpriu / desobrigada (52 mun.) | cumpriu (52 mun.) | cumpriu (52 mun.) | sim (52 mun.) | NR 1: 2 cumpriu, 25 não; NR 7: 0 cumpriu, 27 desob. |
| GO | AGR - Goiás | Agência Goiana de Regulação, Controle e Fiscalização de Serviços Públicos | cumpriu (223 mun.) | cumpriu (223 mun.) | desobrigada (223 mun.) | cumpriu (223 mun.) | sim (223 mun.) | - |
| GO | AMAE | Agência de Regulação dos Serviços Públicos de Saneamento Básico - AMAE | cumpriu (3 mun.) | cumpriu (3 mun.) | desobrigada (3 mun.) | cumpriu (3 mun.) | sim (3 mun.) | NR 1: 0 cumpriu, 2 não; NR 7: 0 cumpriu, 2 desob. |
| GO | AR | Agência de Regulação de Goiânia | cumpriu (1 mun.) | cumpriu (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | sim (1 mun.) | NR 1: 0 cumpriu, 1 não; NR 7: 1 cumpriu, 0 desob. |
| MA | AGERT | Agência Reguladora de Serviços Públicos Delegados do Município de Timon | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| MA | MOB | Agência Estadual de Mobilidade Urbana e Serviços Públicos | não consta | cumpriu (11 mun.) | não consta | não consta | não | - |
| MG | AMASBE | AGENCIA MUNICIPAL DE AGUA, SANEAMENTO BASICO E ENERGIA DE MONTES CLAROS - AMASBE | não consta | não consta | desobrigada (1 mun.) | não consta | não | - |
| MG | ARESAN | AGENCIA DE REGULACAO DOS SERVICOS DE SANEAMENTO BASICO DE UBERLANDIA | desobrigada (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | NR 1: 0 cumpriu, 1 não; NR 7: 0 cumpriu, 1 desob. |
| MG | ARIS-MG | AGENCIA REGULADORA INTERMUNICIPAL DOS SERVICOS DE SANEAMENTO DA ZONA DA MATA E ADJACENCIAS | cumpriu (37 mun.) | cumpriu (37 mun.) | desobrigada (37 mun.) | cumpriu (35 mun.) | sim (35 mun.) | NR 1: 1 cumpriu, 32 não; NR 7: 0 cumpriu, 33 desob. |
| MG | ARISB-MG | Agência Reguladora Intermunicipal de Saneamento Básico de Minas Gerais | cumpriu (35 mun.) | cumpriu (35 mun.) | desobrigada (35 mun.) | cumpriu (35 mun.) | sim (35 mun.) | NR 1: 1 cumpriu, 20 não; NR 7: 0 cumpriu, 21 desob. |
| MG | ARISMIG | AGENCIA REGULADORA INTERMUNICIPAL DE SANEAMENTO DE MINAS GERAIS | cumpriu (27 mun.) | não consta | desobrigada (27 mun.) | não consta | não | NR 1: 2 cumpriu, 20 não; NR 7: 0 cumpriu, 22 desob. |
| MG | ARSAMB | AGENCIA REGULADORA INTERMUNICIPAL DE SANEAMENTO AMBIENTAL DE MINAS GERAIS-ARSAMB | cumpriu / desobrigada (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | sim (1 mun.) | NR 1: 0 cumpriu, 7 não; NR 7: 0 cumpriu, 7 desob. |
| MG | ARSAN | ARSAN - AGENCIA REGULADORA SANEAMENTO DO NORTE DE MINAS | cumpriu (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | NR 1: 0 cumpriu, 36 não; NR 7: 0 cumpriu, 36 desob. |
| MG | ARSAP | Agência Reguladora dos Serviços Públicos de Abastecimento de Água Potável e Esgotamento Sanitário de Pará de Minas | não consta | cumpriu / desobrigada (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | não | - |
| MG | Arsae-MG | Agência Reguladora dos Serviços de Abastecimento de Água e de Esgotamento Sanitário do Estado de Minas Gerais | cumpriu (635 mun.) | cumpriu (635 mun.) | desobrigada (635 mun.) | cumpriu (635 mun.) | sim (635 mun.) | - |
| MS | AGEMS (AGEPAN) | Agência Estadual de Regulação de Serviços Públicos de Mato Grosso do Sul | cumpriu / desobrigada (69 mun.) | cumpriu / desobrigada (69 mun.) | desobrigada (69 mun.) | cumpriu (69 mun.) | sim (69 mun.) | NR 1: 2 cumpriu, 40 não; NR 7: 0 cumpriu, 42 desob. |
| MT | AGER/MT | AGENCIA ESTADUAL DE REGULACAO DOS SERVICOS PUBLICOS DELEGADOS - AGER | desobrigada (2 mun.) | desobrigada (2 mun.) | desobrigada (2 mun.) | cumpriu (2 mun.) | sim (2 mun.) | - |
| MT | AGERR Pantanal | Agência Regional de Regulação dos Serviços de Saneamento do Complexo Nascentes do Pantanal | cumpriu (12 mun.) | cumpriu (12 mun.) | desobrigada (12 mun.) | não consta | não | NR 1: 0 cumpriu, 9 não; NR 7: 1 cumpriu, 8 desob. |
| MT | AGERSINOP | Agência Reguladora de Serviços Públicos Delegados do Município de Sinop | cumpriu (10 mun.) | desobrigada (10 mun.) | desobrigada (10 mun.) | cumpriu (10 mun.) | sim (10 mun.) | - |
| MT | ARIS/MT | Agência Reguladora Intermunicipal de Saneamento do Estado de Mato Grosso | cumpriu (11 mun.) | cumpriu (11 mun.) | desobrigada (11 mun.) | cumpriu (11 mun.) | sim (11 mun.) | NR 1: 0 cumpriu, 9 não; NR 7: 0 cumpriu, 9 desob. |
| MT | CUIABÁ REGULA | AGENCIA DE FISCALIZACAO E REGULACAO DOS SERVICOS PUBLICOS DELEGADOS DO MUNICIPIO DE CUIABA - CUIABA REGULA | cumpriu (1 mun.) | cumpriu (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | sim (1 mun.) | - |
| PA | AMAE/RUROPOLIS | AGENCIA MUNICIPAL DE REGULACAO DOS SERVICOS DE AGUA E ESGOTO DE RUROPOLIS - AMAE/RUROPOLIS | não consta | desobrigada (1 mun.) | não consta | não consta | não | - |
| PA | ARBEL | AGENCIA REGULADORA MUNICIPAL DE BELEM | não consta | não consta | desobrigada (1 mun.) | não consta | não | - |
| PA | SANEÁGUA | AGENCIA REGULADORA DE AGUAS E SANEAMENTO BASICO DO MUNICIPIO DE REDENCAO | não consta | desobrigada (1 mun.) | não consta | não consta | não | - |
| PB | ARPB | Agência de Regulação do Estado da Paraíba | cumpriu (201 mun.) | não consta | desobrigada (201 mun.) | não consta | não | - |
| PE | Arpe | AGENCIA DE REGULACAO DOS SERVICOS PUBLICOS DELEGADOS DO ESTADO DE PERNAMBUCO | cumpriu (185 mun.) | cumpriu (185 mun.) | desobrigada (185 mun.) | cumpriu (172 mun.) | sim (172 mun.) | - |
| PI | AGRESPI | Agência de Regulação dos Serviços Públicos Delegados do Estado do Piauí | cumpriu (224 mun.) | cumpriu (224 mun.) | cumpriu (224 mun.) | cumpriu (221 mun.) | sim (221 mun.) | - |
| PI | ARSETE | Agência Municipal de Regulação de Serviços Públicos de Teresina | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| PR | AGEPAR | Agência Reguladora de Serviços Públicos do Paraná | cumpriu (345 mun.) | cumpriu (345 mun.) | desobrigada (345 mun.) | cumpriu (344 mun.) | sim (344 mun.) | NR 1: 0 cumpriu, 12 não; NR 7: 1 cumpriu, 11 desob. |
| PR | CAGEPAR | Central de Água, Esgoto e Serviços Concedidos do Litoral do Paraná | cumpriu (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| PR | ORCISPAR | Órgão Regulador do Consórico CISPAR | desobrigada (54 mun.) | desobrigada (54 mun.) | desobrigada (54 mun.) | não consta | não | NR 1: 0 cumpriu, 12 não; NR 7: 1 cumpriu, 11 desob. |
| RJ | AGENERSA | Agência Reguladora de Energia e Saneamento Básico do Estado do Rio de Janeiro | não consta | desobrigada (58 mun.) | desobrigada (72 mun.) | não consta | não | - |
| RJ | AGERSAN | AGENCIA REGULADORA DE SANEAMENTO BASICO DO MUNICIPIO DE MACAE | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| RJ | RIOÁGUAS | Fundação Instituto das Águas do Município do Rio de Janeiro - Rio-Águas | cumpriu (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| RJ | SANEAR | Agência de Saneamento do Município de Resende | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| RN | AGRM | AGENCIA REGULADORA DOS SERVICOS PUBLICOS DE MOSSORO | não consta | não consta | desobrigada (1 mun.) | não consta | não | - |
| RN | ARSBAN | AG.REGUL. DE SERV. DE SANEAMENTO BASICO DO MUNICIPIO DO NATAL | não consta | não consta | desobrigada (1 mun.) | não consta | não | - |
| RN | ARSEP-RN | AGÊNCIA REGULADORA DE SERVICOS PÚBLICOS DO ESTADO DO RIO GRANDE DO NORTE | cumpriu (165 mun.) | desobrigada (13 mun.) | desobrigada (166 mun.) | não consta | não | - |
| RO | AGERB | Agência Reguladora de Serviços Públicos Delegados do Município de Buritis | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| RO | AGERO | Agência de Regulação de Serviços Públicos Delegados do Estado de Rondônia | desobrigada (4 mun.) | não consta | não consta | não consta | não | - |
| RO | AGERROM | Agência Reguladora do Município de Rolim de Moura | cumpriu (1 mun.) | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | NR 1: 1 cumpriu, 0 não; NR 7: 0 cumpriu, 1 desob. |
| RS | AGER | Agência Reguladora dos Serviços Públicos Municipais de Erechim | cumpriu (1 mun.) | cumpriu (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | sim (1 mun.) | - |
| RS | AGERGS | Agência Estadual de Regulação dos Serviços Públicos Delegados do Rio Grande do Sul - Agergs | cumpriu (246 mun.) | cumpriu (246 mun.) | desobrigada (246 mun.) | cumpriu (238 mun.) | sim (238 mun.) | - |
| RS | AGERST | Agência Reguladora dos Serviços Públicos Delegados do Município de Santa Cruz do Sul - Agerst | cumpriu (1 mun.) | cumpriu (1 mun.) | desobrigada (1 mun.) | cumpriu (1 mun.) | sim (1 mun.) | NR 1: 0 cumpriu, 1 não; NR 7: 0 cumpriu, 1 desob. |
| RS | AGESG | Agência de Regulação dos Serviços Públicos Delegados de São Gabriel | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| RS | Agesan-RS | Agência Reguladora Intermunicipal de Saneamento do Rio Grande do Sul  Agesan-RS | cumpriu (82 mun.) | cumpriu (82 mun.) | desobrigada (82 mun.) | cumpriu (81 mun.) | sim (81 mun.) | NR 1: 1 cumpriu, 62 não; NR 7: 0 cumpriu, 63 desob. |
| SC | AGIR | AGIR - Agência Intermunicipal de Regulação do Médio Vale do Itajaí | cumpriu (17 mun.) | cumpriu (17 mun.) | desobrigada (17 mun.) | cumpriu (16 mun.) | sim (16 mun.) | NR 1: 14 cumpriu, 2 não; NR 7: 1 cumpriu, 15 desob. |
| SC | AGR TB | Agência Reguladora de Saneamento de Tubarão | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | NR 1: 0 cumpriu, 1 não; NR 7: 0 cumpriu, 1 desob. |
| SC | ARESC | ARESC - Agência de Regulação de Serviços Públicos de Santa Catarina | desobrigada (8 mun.) | não consta | desobrigada (36 mun.) | não consta | não | - |
| SC | ARIS | AGÊNCIA REGULADORA INTERMUNICIPAL DE SANEAMENTO | cumpriu (210 mun.) | cumpriu (210 mun.) | cumpriu / desobrigada (210 mun.) | cumpriu (210 mun.) | sim (210 mun.) | NR 1: 7 cumpriu, 195 não; NR 7: 0 cumpriu, 202 desob. |
| SC | CISAM Meio Oeste | Consórcio Intermunicipal de Saneamento Ambiental - Meio Oeste | cumpriu (10 mun.) | cumpriu (10 mun.) | desobrigada (10 mun.) | cumpriu (8 mun.) | sim (8 mun.) | - |
| SC | CISAM-SUL | CISAM-SUL - Consórcio Intermunicipal de Saneamento Ambiental | cumpriu (19 mun.) | cumpriu (19 mun.) | desobrigada (19 mun.) | cumpriu (19 mun.) | sim (19 mun.) | NR 1: 1 cumpriu, 0 não; NR 7: 0 cumpriu, 1 desob. |
| SE | AGRESE | AGENCIA REGULADORA DE SERVICOS PUBLICOS DO ESTADO DE SERGIPE - AGRESE | cumpriu (74 mun.) | cumpriu (74 mun.) | cumpriu (74 mun.) | cumpriu (72 mun.) | sim (72 mun.) | - |
| SP | AMAE | AGENCIA MUNICIPAL DE AGUA E ESGOTO DE MARILIA - AMAE | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| SP | ARES-PCJ | Agência Reguladora dos Serviços de Saneamento das Bacias dos Rios Piracicaba, Capivari e Jundiaí | cumpriu (79 mun.) | cumpriu (79 mun.) | desobrigada (79 mun.) | cumpriu (77 mun.) | sim (77 mun.) | NR 1: 4 cumpriu, 48 não; NR 7: 52 cumpriu, 0 desob. |
| SP | ARESPCAB | AGÊNCIA REGULADORA DE SERVIÇOS PÚBLICOS DO MUNICÍPIO DE CASA BRANCA | cumpriu (4 mun.) | desobrigada (4 mun.) | desobrigada (4 mun.) | não consta | não | - |
| SP | ARSESP | Agência Reguladora de Saneamento e Energia do Estado de São Paulo | cumpriu / desobrigada (349 mun.) | desobrigada (4 mun.) | desobrigada (349 mun.) | não consta | não | NR 1: 0 cumpriu, 6 não; NR 7: 0 cumpriu, 6 desob. |
| SP | ARVAP | AGENCIA INTERMUNICIPAL DE REGULACAO, CONTROLE E FISCALIZACAO DE SERVICOS PUBLICOS DO VALE PARANAPANEMA - AGENCIA CIVAP | desobrigada (1 mun.) | cumpriu (2 mun.) | desobrigada (2 mun.) | não consta | não | - |
| SP | SAEMJA | Agência Reguladora do Serviço de Água, Esgoto e Saneamento do Município de Jau | não consta | desobrigada (1 mun.) | desobrigada (1 mun.) | não consta | não | - |
| SP | SRJ | Serviço de Regulação de Saneamento de Jacareí | desobrigada (1 mun.) | desobrigada (2 mun.) | desobrigada (2 mun.) | cumpriu (1 mun.) | sim (1 mun.) | NR 1: 0 cumpriu, 1 não; NR 7: 1 cumpriu, 0 desob. |
| TO | ARP | Agência de Regulação, Controle e Fiscalização de Serviços Públicos de Palmas | não consta | não consta | desobrigada (1 mun.) | cumpriu (1 mun.) | não | NR 1: 1 cumpriu, 0 não; NR 7: 1 cumpriu, 0 desob. |
| TO | ATR | Agência Tocantinense de Regulação Controle e Fiscalização de Serviços Públicos | cumpriu (62 mun.) | cumpriu (63 mun.) | desobrigada (63 mun.) | não consta | não | - |

Observações: (a) AMAE (SP) aparece duas vezes na planilha de origem (siglas AMAE e AMAE-Marília) para o mesmo CNPJ, e a ATR (TO) aparece com uma linha de rótulo "2025" por erro da planilha; ambas foram unificadas ou removidas aqui. (b) Registros "Sooretama" e "Linhares" constam nas listas da ARSP e da ARIES (ver seção 3). (c) 72 registros de ERI nas planilhas, 70 ERIs distintas.

#### 1.3 Atos próprios de adoção das NR (principalmente NR 9 a 16) por ERI

Fonte entre colchetes: parte do levantamento em que o ato foi lido. "Sem ato" significa que a busca na página oficial da ERI não achou ato; não prova ausência.

| UF | ERI | Atos próprios localizados |
|---|---|---|
| AC | AGEAC | Sem ato localizado para NR 9 a 16 [parte 5]. |
| AL | ARSAL-AL | Res. 282/2026 (NR 3), 275/2026 (NR 5), 276/2026 (NR 8); CP 07/2026 (NR 9); NR 10 prevista no cronograma; NR 11 sem ato; NR 13: minuta de tarifa social [parte 5]. |
| AM | AGEMAN | Sem ato localizado [parte 5]. |
| BA | AGERSA | NR 9: Res. 002/2026 (21/07/2026); NR 10 em audiência (23/10/2026); NR 11, 13, 15, 16 sem ato localizado [parte 5]. |
| CE | ACFor | Desconhecido [parte 5]. |
| CE | ARCE | NR 8: Res. 12/2025; NR 9: Res. 03/2026 (vínculo inferido); NR 11: minuta em audiência pública 08/2026 (23/03 a 30/04/2026); NR 13, 15, 16 sem ato [parte 5]. |
| DF | ADASA | NR 13: art. 67-A da Res. 42/2024 (tarifa social); NR 9 e 11 sem ato localizado [parte 4]. |
| ES | ARIES | NR 3: RDC 019/2025; NR 5: RDC 039/2026; NR 6: RDC 020/2025; NR 8: RDC 022 e 025/2025; NR 9: RDC 038/2026; NR 10: RDC 051/2026; NR 13: RDC 037/2026 (PDF intitulado minuta; A_CONFIRMAR); NR 11 sem ato (regulamentos por SAAE); NR 15 e 16 sem ato localizado [esta parte]. |
| ES | ARSP/ES | NR 3: Res. 110/2026; NR 5: Res. 104/2026; NR 6: Res. 109/2026; NR 8: Res. 092 e 093/2025; NR 9: Res. 108/2026; NR 7: Res. 085/2025; NR 10 sem ato; NR 11: revisão da Res. ARSI 008/2010 prevista (AAE 04, 2026/2), sem consulta localizada; NR 13 sem ato (Cesan em "obtenção de dados" na lista de tarifa social); NR 15 prevista (2027/2); NR 16 sem informação [esta parte]. |
| GO | AGR - Goiás | NR 9: RN 366/2026 e resoluções conjuntas 09 e 10/2026; NR 8, 5, 3 e soluções alternativas: conjuntas 01 a 04/2025; NR 7: RN 368/2026; NR 11 sem ato; NR 13: RN 186/2021 sem adaptação (a confirmar) [parte 4]. |
| GO | AMAE | Atua por resoluções conjuntas com a AGR/AR/ARM (GO) [parte 4]. |
| GO | AR | Resoluções conjuntas AGR/AR/AMAE/ARM (GO) [parte 4]. |
| MA | AGERT | Nenhum ato de adoção (Res. 01 a 14 não citam NR) [parte 6B]. |
| MA | MOB | Nenhum ato próprio localizado; NR 5 cumprida em 2025 sem ato visível [parte 6B]. |
| MG | AMASBE | Sem ato da agência; NR 8 incorporada ao PMSB por Lei 5.861/2025 [parte 6B]. |
| MG | ARESAN | Nenhum ato cita NR expressamente; Res. 002/2025 trata de indicadores (NR 9) sem número lido [parte 6B]. |
| MG | ARIS-MG | Res. 185/2025 (condições gerais, 20/08/2025); demais NR sem registro [parte 3]. |
| MG | ARISB-MG | NR 9: Res. 360/2025; Res. 132/2020 (condições gerais) antecede a NR 11 [parte 3]. |
| MG | ARISMIG | Sem registro; só NR 3 cumprida em 2025 [parte 3]. |
| MG | ARSAMB | NR 3: Res. 009/2025; NR 5: 010/2025; NR 6: 019/2026; NR 8: 013/2025; NR 9: 017/2026; NR 10: 018/2026; NR 13: 016/2026; NR 11 sem ato [parte 6B]. |
| MG | ARSAN | Atos 2025-2026 por ementa (NR 3, 5, 6, 8, 9, 10; números deduzidos); NR 11 e 13 sem ato [parte 6B]. |
| MG | ARSAP | NR 8 e 9: Res. 006/2026; NR 11 prevista na agenda; NR 6, 10 e 13 sem ato [parte 6B]. |
| MG | Arsae-MG | NR 9: Res. 205/2025 (30/01/2025); NR 13 citada na Res. 227/2026 [parte 3]. |
| MS | AGEMS (AGEPAN) | Port. 320/2025 inclui matriz de riscos; NR 13: Port. 315/2025 (secundária); NR 9 e 11 sem ato [parte 4]. |
| MT | AGER/MT | NR 9: RN 007/2026 (secundária); NR 3, 5 e soluções alternativas: RN 008 a 010/2026; condições gerais: RN 004/2026 sem menção à NR 11; NR 13 sem norma [parte 4]. |
| MT | AGERR Pantanal | NR 3: RN 029/2025; NR 5: 032/2025; NR 6: 033/2025; NR 8: 030/2025; NR 9: 41/2026; NR 10: 45/2026; NR 13: 42/2026; NR 11 sem ato [parte 6B]. |
| MT | AGERSINOP | Res. 09/2025 (tarifa social), 10 a 15/2025 (NR), 16/2025 (NR 9, em consulta); Res. 04 e 05/2019 (condições gerais, a confirmar) [parte 4]. |
| MT | ARIS/MT | RN 49 a 54/2025, 58/2026 (reajuste), 59/2026 (NR 9, secundária) [parte 4]. |
| MT | CUIABÁ REGULA | RN 10/2025 (soluções alternativas); sem norma própria de tarifa social [parte 4]. |
| PA | AMAE/RUROPOLIS | Só ato institucional (LC 411/2020) [parte 6B]. |
| PA | ARBEL | Desconhecido [parte 5]. |
| PA | SANEÁGUA | Só ato institucional (LC 176/2025) [parte 6B]. |
| PB | ARPB | Desconhecido além das NR 3 e 6 de 2025; NR 11: adequação não confirmada [parte 5]. |
| PE | Arpe | NR 8: Res. 295/2025; NR 3: 308/2025; NR 5: 309/2025; tarifa social: 292/2025; NR 10, 11, 13, 15, 16 sem ato [parte 5]. |
| PI | AGRESPI | NR 8: Res. 007/2025; NR 3: 010/2025; NR 5: 011/2025; soluções alternativas: 006/2025 [parte 5]. |
| PI | ARSETE | Desconhecido (NR 5 e 6 desobrigadas) [parte 5]. |
| PR | AGEPAR | NR 9: Res. 56/2026; NR 13/tarifa social: Res. 36/2025; NR 3: Res. 63/2025; soluções alternativas: 35/2025; resíduos: 34/2025; NR 11 sem ato [parte 4]. |
| PR | CAGEPAR | Nenhum ato localizado (portal fora do ar) [parte 6B]. |
| PR | ORCISPAR | NR 11: Res. 28/2025 (regulamento padrão por adesão); NR 8 e 9: Res. 02/2026; NR 4: ajuste de 07/2026; CP 46/2026 (nova minuta de condições gerais) [parte 6B]. |
| RJ | AGENERSA | NR 4 via IN 124/2024; NR 5 e 6 desobrigadas [parte 3]. |
| RJ | AGERSAN | Agenda de Macaé prevê as NR até 2027; sem ato [parte 3]. |
| RJ | RIOÁGUAS | NR 3: Portaria 001/2025; NR 8: Portaria 001/2026 [parte 6B]. |
| RJ | SANEAR | Nenhum ato próprio localizado (SANEAR Resende) [parte 6B]. Não confundir com o SANEAR Colatina (ES), que é prestador regulado pela ARSP. |
| RN | AGRM | Ver ARSEP-RN/AGRM [parte 5]. |
| RN | ARSBAN | NR 6 desobrigada; demais desconhecidas [parte 5]. |
| RN | ARSEP-RN | NR 3 cumprida em 165 municípios; sem registro de NR 8 [parte 5]. |
| RO | AGERB | Nenhum ato de adoção; RN 06/2025 cita a NR 4 [parte 6B]. |
| RO | AGERO | Res. 101/2026 (NR 6) [parte 5/6B]. |
| RO | AGERROM | NR 3: RN 02/2025; NR 9: RN 003/2026; NR 13: RN 001/2026 (cita "Normas de Referência" sem número) [parte 6B]. |
| RS | AGER | NR 3: Res. 036/2024; NR 5: 039/2025; NR 6: 044/2025; NR 8: 042 e 043/2025; NR 9: 048/2026; NR 10: 049/2026; NR 13 em minuta (CP 01/2026); NR 11 sem ato [parte 6B]. |
| RS | AGERGS | NR 9: NT 01/2025-DSI e IN 32/2026; tarifa social: RED 892/2026 (secundária); RED 780/2025 internaliza as NR (a confirmar); NR 11 sem ato [parte 4]. |
| RS | AGERST | Ver parte 4 (cumpriu NR 3, 5 e 8 em 2025) [parte 4]. |
| RS | AGESG | Nenhum ato de NR [parte 6B]. |
| RS | Agesan-RS | NR 9: relatórios no site; tarifa social: CSR 033/2024 e 013/2026; soluções alternativas: CSR 007/2025; NR 11 sem ato [parte 4]. |
| SC | AGIR | NR 1: RN 018/2025; NR 3: 019/2025; NR 5: 020/2025; NR 6: 021/2025; NR 7: 022/2025; NR 8: 023 e 024/2025; NR 9: 025/2026; NR 10, 11 e 13 sem ato [parte 2]. |
| SC | AGR TB | NR 8: Res. 045/2025 e 048/2026; NR 9: Res. 049/2026 (listagem); NR 7: 044/2025; NR 3, 5, 6, 10, 11, 13 sem ato [parte 2 e 6B]. |
| SC | ARESC | NR 3: Res. 433/2026; NR 5: 432/2026; NR 8: 430 e 431/2026; NR 6, 9, 10, 11, 13 sem ato [parte 2]. |
| SC | ARIS | NR 3: RN 52/2025; NR 5: RN 44/2025; NR 6: RN 50/2025 (parcial); NR 8: RN 36, 42, 47; NR 9: RN 57/2026; NR 10: RN 51/2025 (parcial); NR 11: parcial (RN 19, 45/2025, 47/2025, 55/2026); NR 13: parcial (RN 56/2026) [parte 1]. |
| SC | CISAM Meio Oeste | NR 3: RN 01/2025; NR 5: 02/2025; NR 6: 03/2025; NR 7: 04/2025; NR 8: 05 e 06/2025; NR 11: RN 05/2026 (efeitos em 20/05/2027); NR 13: RN 04/2026; NR 9 e 10 sem ato [parte 6B]. |
| SC | CISAM-SUL | RN 03/2017 vale como condições gerais; sem ato novo de NR 11 [parte 2]. |
| SE | AGRESE | NR 8: Res. 84/2025; NR 5: Res. 85/2025; tarifa social: Portaria 15/2026 (11/03/2026) [parte 5]. |
| SP | AMAE | Nenhum ato próprio localizado; o Estatuto (Decreto 14.442/2024, art. 3º, I) exige observar as NR da ANA [parte 6B]. |
| SP | ARES-PCJ | NR 4, 7 e 9 no site; condições gerais: Res. 50/2014 consolidada [parte 3]. |
| SP | ARESPCAB | NR 3: Res. 82/2025; NR 5: 87/2026; NR 8: 81/2025; NR 7: 96/2026; Res. 89/2026 cumpre o papel da NR 11 sem citá-la; tarifa social Res. 69/2025 [parte 6B]. |
| SP | ARSESP | NR 8 e 9 na agenda 2026-2027; sem ato localizado [parte 3]. |
| SP | ARVAP | NR 8: Res. 007/2026; NR 9: Res. 010/2026 (19/08/2026) [parte 3]. |
| SP | SAEMJA | Nenhum ato de NR; regulamento próprio (Res. 08/2017) [parte 6B]. |
| SP | SRJ | NR 7: Res. 34/2025; NR 8: 35 e 37/2025; NR 9: 39/2026; NR 4 citada na Res. 30/2025 [parte 6B]. |
| TO | ARP | NR 3: Res. ARP 27/2026; NR 5: 28/2026; NR 8: 24/2025; NR 13 sem ato; NR 9 em uso sem ato [parte 6B]. |
| TO | ATR | ATR-TO: ver parte 5 (NR 3, 5 e 6 de 2025) [parte 5]. |

ERIs fora das planilhas da lista positiva, com pesquisa em outras partes: ARCON-PA (PA; Res. 001, 003, 005 e 008/2026 vistas apenas em trecho de buscador), AGRF Gurupi (TO; sem norma de água e esgoto), AGESB São Borja e AMR Maringá (sem norma localizada), ARMPF Porto Ferreira, ARSAE Mirassol, ARSAE Andradina, AGRF-DAEA Araçatuba e ARSAE Castilho (SP; ver apêndice B.1), ARSARP (MG; só resíduos), AGEREG Campo Grande (MS; norma de tarifa social em 2025).

### 2. Esclarecimentos das datas pedidas

- NR 11 (condições gerais): o art. 74 §3º do texto da Resolução ANA 230/2024 (DOU 19/12/2024) fixa o início da verificação em 20/05/2027. A notícia da ANA de 09/01/2025 diz 27/05/2027, e a mesma notícia indica 22/12/2024 como data do DOU; ambas divergem do ato. Vale o texto do ato: 20/05/2027. O manual orientativo da ANA repete 20/05/2027.
- Tarifa social: a Lei 14.898/2024 foi sancionada em 13/06/2024 e publicada em 14/06/2024; o art. 13 dá 180 dias de vacância, e a ANA registra a vigência em 11/12/2024. O art. 7º §2º da lei e o art. 26 §1º da NR 13 (Res. ANA 271/2025) concedem 24 meses da vigência, e o Aviso da ANA no DOU de 21/09/2026 fixa o término em 11/12/2026. Agências que contaram a vigência de 10/12/2024 (por exemplo, ARSAL e atos locais citados na parte 6B) chegam a 10/12/2026. Recomendação: parametrizar 11/12/2026 como data legal e 10/12/2026 como limite interno prudencial.
- Res. ANA 134/2022, art. 6º: 20/05 (instruções), 20/08 (envio), 20/10 (comunicação de não observância) e 20/12 (resultado), todos prorrogados ao primeiro dia útil seguinte. Em 2026, 20/10 foi terça-feira e 20/12 é domingo (resultado até 21/12/2026).
- Lista positiva de 2025: a página da ANA diz "disponível em 20-12-2025"; 20/12/2025 foi sábado, e a divulgação ocorreu em 22/12/2025. O balanço de 04/09/2026 (78 ERIs cadastradas, 61 com envio da NR 8, 30 na lista após reexame, 2.875 municípios) usa critério diferente do noticiário de dezembro (20 ERIs em água e 21 em esgoto) e não foi reconciliado.

### 3. Quadro do Espírito Santo (parte A)

#### 3.1 Arranjo regulatório

- O Espírito Santo tem duas ERIs de água e esgoto cadastradas e em lista da ANA: ARSP (estadual, 52 municípios) e ARIES (consórcio intermunicipal de SAAEs, 21 municípios). Não há agência municipal de água e esgoto em Vitória, Vila Velha, Colatina ou Linhares: Vitória e Vila Velha são atendidas pela Cesan e reguladas pela ARSP (Vila Velha por convênio de regulação assinado com a antecessora ARSI, segundo notícia oficial do estado); Colatina é atendida pelo SANEAR (autarquia municipal) e regulada pela ARSP; Linhares é atendida pelo SAAE e passou da ARIES (RDC 011/2025) para a ARSP pelo Convênio 001/2026, publicado em 30/03/2026 (prazo de 30 anos), segundo a nota técnica da ARSP.
- Sobreposição a resolver no perfil do tenant: Sooretama consta nas listas de 2025 das duas ERIs (ARSP: Res. 079/2024 e Convênio 002/2023; ARIES: RDC 028/2025). Linhares consta apenas na ARIES na lista de 2025 e na ARSP na página oficial de 2026. Recomenda-se que o campo "ERI vigente" do tenant tenha data de início.
- Notícia de imprensa de 01/07/2026 diz que só a ARIES estaria na lista positiva de água e esgoto no ES; a planilha oficial mostra as duas ERIs com as quatro NR (ARSP em 52 e ARIES em 21 municípios). Prevalece a planilha.

#### 3.2 ARSP (Agência de Regulação de Serviços Públicos do ES)

| Campo | Conteúdo |
|---|---|
| UF e nome | ES; ARSP, CNPJ 26.064.356/0001-82 (planilha ANA); sucede a ARSI (LC 827/2016) |
| Abrangência | 52 municípios na lista de 2025 (entre eles Vitória, Vila Velha, Serra, Cariacica, Viana, Guarapari, Colatina, Aracruz, São Mateus, Sooretama). Prestadores: Cesan; SAAE Aracruz (Convênio 001/2022, 20 anos); SAAE Sooretama (Convênio 002/2023, 20 anos); SANEAR Colatina; SAAE São Mateus; SAAE Linhares (Convênio 001/2026) |
| Condições gerais | Res. ARSI 008/2010 (vigente, 53 p.), alterada pela Res. ARSP 041/2020; Res. 040/2020 (esgoto em imóveis com fonte alternativa); Res. 018/2018 (penalidades ao prestador); revisão da 008/2010 prevista para 2026/2 (AAE 04), sem consulta localizada |
| Tarifa | Res. 034/2020 (regime tarifário); 051/2021 e 055/2022 (estrutura e tarifa social da Cesan); 012/2017 (tarifa de disponibilidade de esgoto); Res. 107/2026 (2ª RTP da Cesan, vigência 01/09/2026, tarifa média de R$ 5,8431 para R$ 6,2516/m3); reajustes: SANEAR Res. 094/2025 (vigente até 30/11/2026; minuta do reajuste de 01/12/2026 em consulta), SAAE Aracruz Res. 103/2026, SAAE São Mateus Res. 098/2026, SAAE Sooretama Res. 079/2024 (A_CONFIRMAR), SAAE Linhares (minuta CP 2026/015) |
| Adoção das NR | NR 3: Res. 110/2026; NR 5: 104/2026; NR 6: 109/2026; NR 7: 085/2025; NR 8: 092 e 093/2025; NR 9: 108/2026; NR 10: sem ato; NR 11: revisão prevista; NR 13: sem ato; NR 15: prevista (2027/2); NR 16: sem informação. Lista de 2025: NR 3 (50 mun. C e 2 D), NR 5 (51 C e 1 D), NR 6 (52 C), NR 8 (52 C); "conjunto" 52 |
| Juros, multa e correção | Juros de mora de até 0,033% ao dia e multa de até 2% (Res. ARSI 008/2010, art. 98): confirmado. Índice de correção: não confirmado |
| Prazo de atendimento | Pedido de ligação em até 15 dias úteis (5 de inspeção e 10 de execução, art. 15); contagem de prazos em dias úteis (art. 120); interrupção programada avisada com 72 h (art. 59 §2º): confirmado. Recurso ao prestador em 15 dias e, da decisão, à agência em 15 dias: confirmado no texto, artigos não anotados |
| Aviso de corte | 30 dias por correspondência específica (arts. 59 §3º e 60); 45 dias para estabelecimento de saúde, instituição educacional, internação coletiva e usuário de tarifa social (art. 64): confirmado |
| Dias vedados | Não localizada vedação de corte por dia da semana ou véspera de feriado no texto lido: não confirmado (regra pode estar em contrato ou norma da Cesan) |
| Religação | 48 h sem retirada de ramal, 72 h com retirada, urgência facultativa de 6 h (arts. 67 e 68); interrupção indevida: 12 h e crédito do valor da religação (art. 62): confirmado |
| Tarifa social | Cesan: categorias Social I (CadÚnico em pobreza ou extrema pobreza) e Social II (BPC ou CadÚnico até 1/2 salário mínimo per capita), Res. 051/2021 e 055/2022: confirmado. Descontos de até 75% e até 60% variando com o consumo: fonte secundária (portal Faça Fácil do ES). Desconto de 50% nos primeiros 15 m3 da Lei 14.898/2024 não adotado em ato da ARSP: não confirmado. Lista de tarifa social da ANA (jul/2026): Cesan em "obtenção dos dados do CadÚnico e BPC" |

#### 3.3 ARIES (Agência Reguladora Intermunicipal de Saneamento Básico do ES)

| Campo | Conteúdo |
|---|---|
| UF e nome | ES; ARIES, consórcio público com sede em Vitória, CNPJ 45.206.105/0001-30 (planilha ANA); site aries.agr.br |
| Abrangência | 21 municípios na lista de 2025: Alegre, Alfredo Chaves, Baixo Guandu, Governador Lindenberg, Ibiraçu, Ibitirama, Iconha, Itaguaçu, Itapemirim, Itarana, Jaguaré, Jerônimo Monteiro, João Neiva, Linhares, Marataízes, Marilândia, Mimoso do Sul, Rio Bananal, São Domingos do Norte, Sooretama e Vargem Alta (prestadores: SAAEs). Linhares passou à ARSP em 2026 |
| Condições gerais | Sem norma única localizada. Regulamentos por SAAE aprovados por resolução (RDC 001/2023: Iconha; texto do regulamento não lido); RDC 043/2026 (corte em Alegre); RDC 004/2024 (multas, Iconha). NR 11: sem ato |
| Tarifa | Reajustes por SAAE em RDC (2025: 010 Vargem Alta, 011 Linhares, 012 Itarana, 014 e 029 Mimoso do Sul, 015 Iconha, 016 Itaguaçu, 017 Ibiraçu, 018 Marilândia, 023 Alfredo Chaves, 024 João Neiva, 028 Sooretama; 2026: 034 Jerônimo Monteiro, 040 Rio Bananal, 041 Iconha, 042 São Domingos do Norte; 2024: 002 Itapemirim, 006 Ibiraçu, 007 Marilândia, 008 Governador Lindenberg, 009 Baixo Guandu; atos de reajuste não lidos, só a ementa); metodologia geral: RDC 051/2026 (IPCA a cada 12 meses); disponibilidade de esgoto: RDC 026/2025 |
| Adoção das NR | NR 3: RDC 019/2025; NR 5: 039/2026; NR 6: 020/2025; NR 8: 022 e 025/2025; NR 9: 038/2026; NR 10: 051/2026; NR 13: 037/2026 (PDF com título "minuta"); NR 11: sem ato; NR 15 e 16: sem ato. Lista de 2025: NR 3 e NR 8 cumpridas, NR 5 e NR 6 desobrigadas, "conjunto" 21 |
| Juros, multa e correção | Não confirmados em ato da ARIES (dependem de cada regulamento de SAAE; Iconha lido apenas na resolução aprovadora) |
| Prazo de atendimento | Religação em até 2 dias úteis (RDC 043/2026, SAAE Alegre): confirmado para Alegre; demais SAAEs: não confirmado. RDC 037/2026: inclusão na tarifa social em 10 dias úteis |
| Aviso de corte | 30 dias, com valores e meses devidos (Alegre, RDC 043/2026, art. 2º); 60 dias para serviço essencial (art. 5º): confirmado para Alegre |
| Dias vedados | Sem corte às sextas, sábados, domingos, feriados e vésperas de feriado (Alegre, art. 4º): confirmado para Alegre |
| Tarifa social | RDC 037/2026 (A_CONFIRMAR): renda per capita até 1/2 salário mínimo com CadÚnico ou BPC, 50% nos primeiros 15 m3, inclusão automática ou por pedido, multifamiliar com medidor único: prazo de 1 ano. Itapemirim (RDC 003/2024): 50% até 10 m3, exige CadÚnico, benefício federal e adimplência: confirmado. Lista de tarifa social da ANA (jul/2026): 4 SAAEs concluídos (Baixo Guandu, Itapemirim, Mimoso do Sul, Sooretama) e 16 em reequilíbrio |
| Taxa e precedentes | RDC 005/2024 (medidas após chuvas de março de 2024: isenção, tarifa mínima, cobrança por média e suspensão de cortes); IN 002/2026 (investimentos); RA 043/2023 e RDC 047/2026 (penalidades ao prestador, pena-base de 0,06% a 0,08% do faturamento anual) |

#### 3.4 Pendências do ES

1. Consulta, minuta ou ato da ARSP para a NR 11 (prevista para 2026/2): não localizada. A CP 008/2026 que a busca mostrava como "Condições Gerais" trata de loteria.
2. Confirmar se a RDC 037/2026 da ARIES foi publicada como resolução ou segue como minuta; texto do regulamento dos SAAEs (Iconha e demais) não lido.
3. Definir regulador de Sooretama e data de transição de Linhares.
4. Índice de correção da fatura e eventual vedação de corte por dia da semana na Cesan (contrato de programa/norma da concessionária) não localizados.
5. Municípios de Vitória e Vila Velha não aparecem na lista de municípios conveniados lida da página da ARSP (a lista carregada vai até a letra M); confirmados apenas pela planilha da ANA e pela notícia do convênio de Vila Velha.

### 4. Quadro das ERIs da parte B (resumo)

Detalhes, artigos e pendências por ERI estão nos apêndices B.1 a B.4. Marcas: C = confirmado em texto lido; NC = não confirmado.

| UF | ERI | Abrangência | Condições gerais e tarifa | Adoção das NR | Parâmetros principais |
|---|---|---|---|---|---|
| SP | AMAE (Marília) | Marília; concessão RIC Ambiental (2024) | Regulamento: Decreto 14.440/2024 (cópia da concessionária); Estatuto: Decreto 14.442/2024; reajuste 6,72% (Res. AMAE 04/2026) | Nenhum ato localizado | Multa 3%, 6%, 9% por faixa de atraso (C); juros, aviso e dias vedados NC; tarifa social NC em ato da AMAE |
| SP | ARESPCAB (Casa Branca) | Casa Branca, Estiva Gerbi, São Sebastião da Grama, Araçoiaba da Serra, Tuiuti | Res. 89/2026 (condições gerais), 88/2026 (penalidades), 69/2025 (tarifa social), 99/2026 (reajuste Casa Branca) | NR 3 (82/2025), 5 (87/2026), 7 (96/2026), 8 (81/2025) | Juros 1% a.m., multa 0,33%/dia até 10% (C); aviso de corte 30 dias (C); sem corte após 12h de sexta e em véspera de feriado (C); religação 12/24/72 h (C); ligação 3+10 d.u. (C); tarifa social 50% até 15 m3 (C) |
| SP | SAEMJA (Jaú) | Jaú; Águas de Jahu (concessão) | Regulamento Res. 08/2017 e alterações; Res. 39/2023 (atendimento); tarifa referencial R$ 3,607 | Nenhum ato | Multa 2% e juros 1% a.m. (C, redações diferentes em dois artigos); aviso 30 dias (C); sem corte em dias não úteis (C); tarifa social Res. 45/2024 consta suspensa |
| SP | SRJ (Jacareí) | Jacareí e Potim; Aparecida citada | Res. 07/2017 (condições gerais, OCR) e 26/2023; reajuste Res. 41/2026 (4,64%); tarifa social Res. 30/2025 | NR 7 (34/2025), 8 (35 e 37/2025), 9 (39/2026) | Aviso 30 dias (C); sem corte após 12h de sexta/véspera (C); religação 12/24/72 h (C); tarifa social 50% até 15 m3 (C); juros e multa NC |
| RJ | SANEAR (Resende) | Resende; Águas das Agulhas Negras | Decreto 2.405/2008 (OCR); tarifa por fórmula do contrato | Nenhum ato | Corte a partir do 30º dia do aviso (C com ressalva OCR); religação em até 3 dias (C); demais NC |
| RJ | Rio-Águas | Esgoto da AP5 do Rio; água e demais áreas com a AGENERSA | Portaria 002/2019 (regulamento de esgoto); contrato 001/2012 (IPCA-E, janeiro) | NR 3 (Portaria 001/2025), NR 8 (001/2026) | Multa 2% e juros 1% a.m. (C); demais NC |
| MG | AMASBE (Montes Claros) | Montes Claros; Copasa | Usa normas da Arsae-MG; sem norma própria | Sem ato; NR 8 em lei municipal 5.861/2025 | Todos NC no âmbito da AMASBE |
| MG | ARESAN (Uberlândia) | Uberlândia; DMAE | Decreto 2.624/1984 (não lido); tarifa: Res. 002/2022, 006/2023, 003/2024, 001/2025 | Nenhum ato cita NR | Todos NC; tarifa social municipal própria (LC 309) e adesão ao desconto federal |
| MG | ARSAMB | 54 municípios filiados ao CIMVA; ANA registra Santana do Manhuaçu | Res. 002/2024 (condições gerais, OCR); Res. 018 e 019/2026; tarifa social Res. 016/2026 | NR 3, 5, 6, 8, 9, 10 e 13 (ver apêndice) | Juros 1% a.m., multa até 2%, IPCA, aviso 30 dias, sem corte de sexta a domingo/véspera/feriado, religação 48 h, ligação 10 d.u. urbana e 15 rural (C); tarifa social mín. 50% |
| MG | ARSAN (Norte de MG) | Consórcio; ANA registra São João da Lagoa | Res. 001/2021 (condições gerais, lida); Res. 007/2026-CEN | NR 3, 5, 6, 8, 9, 10 por ementa; NR 11 e 13 sem ato | Mesmos parâmetros do modelo Arsae-MG 131/2019 (C) |
| MG | ARSAP (Pará de Minas) | Pará de Minas; Águas de Pará de Minas | Regulamento no contrato 0118/2015; Res. 03/2025 (reajuste 6,324%) | NR 8 e 9 (006/2026) | Multa 2%, juros 1% a.m. (C); prazos em dias corridos (C); aviso e dias vedados NC |
| PR | CAGEPAR (Paranaguá) | Paranaguá; Paranaguá Saneamento | Nenhuma resolução localizada (portal fora do ar) | Nenhum ato | Todos NC |
| PR | ORCISPAR (CISPAR) | 54 municípios do PR (SAAE/SAMAE) | Res. 28/2025 (regulamento padrão, NR 11); Res. CISPAR 38/2022 (reajuste); tarifa social Res. 13/2025 | NR 11 (28/2025), NR 8 e 9 (02/2026) | Multa 2% até 30 d e 5% depois mais juros 1% a.m. (C); aviso 30 dias (C); sem corte em sáb., dom. e feriado (C); religação 48 h (C); tarifa social 50% até 15 m3 (C) |
| RS | AGER (Erechim) | Erechim; CORSAN | Res. 026/2023 (condições gerais); reajuste Res. 047/2025; tarifa social em consulta (CP 01/2026) | NR 3, 5, 6, 8, 9, 10; NR 13 em minuta | Multa 2% e juros 1% a.m. (C); sem corte em sáb., dom. e feriado (C); religação 48 h (C); aviso em dias NC |
| RS | AGESG (São Gabriel) | São Gabriel; São Gabriel Saneamento | Regulamento contratual (anexo do contrato 051/2012) | Nenhum ato | Juros até 0,033%/dia e multa 2% (C); aviso 30 dias (texto) ou 30 dias úteis (página): divergente; sem corte em sáb., dom., feriado e véspera (C) |
| SC | CISAM Meio Oeste | 10 municípios com regulação delegada (Campos Novos, Capinzal, Joaçaba e outros) | RN 01/2017 até 19/05/2027; RN 05/2026 (NR 11) a partir de 20/05/2027; tarifa social RN 04/2026 | NR 3, 5, 6, 7, 8, 11 e 13; NR 9 e 10 sem ato | Aviso 30 dias (C); sem corte após 12h de sexta, véspera, sáb., dom., feriado (C); religação 12/24/120 h (C); tarifa social 50% até 15 m3 (C); juros e multa remetidos à legislação |
| SC | AGR Tubarão | Tubarão; Tubarão Saneamento (já na parte 2) | Res. 007/2013; Decreto 9.159/2026 (reajuste 4,86%); tarifa social Res. 043/2025 | NR 8 (045/2025, 048/2026), NR 7 | Ligação 5 d.u., reparo e religação 24 h (C); demais NC |
| MT | AGERR Pantanal | 12 a 16 municípios (consórcio) | Sem condições gerais de água e esgoto localizadas; RN 33/2025, 45/2026; tarifa social RN 42/2026 | NR 3, 5, 6, 7, 8, 9, 10 e 13 | Tarifa social 50% até 15 m3 (C); demais NC |
| TO | ARP (Palmas) | Palmas; Saneatins/BRK | Res. ARP 8/2018 (espelho LegisWeb); tarifa por ATR (reajuste 9,37%, A_CONFIRMAR) | NR 3 (27/2026), 5 (28/2026), 8 (24/2025) | Juros até 0,033%/dia, multa 2%, IPCA (C); suspensão só com 60 dias de atraso e aviso de 30 dias (C); sem corte após 12h de sexta e véspera (C); religação indevida 12 h (C); tarifa social 69% (desatualizada) |
| RO | AGERB (Buritis) | Buritis; Águas de Buritis | Sem regulamento próprio; RN 06/2025 (tarifa social 50% até 20 m3); reajuste 003/2025 | Nenhum ato | Atendimento de 15 dias (unidade NC); demais NC |
| RO | AGERROM (Rolim de Moura) | Rolim de Moura; Aegea | RN 001/2020 (regulamento); RN 002/2026 (reajuste 5,19%); tarifa social RN 001/2026 | NR 3 (02/2025), 9 (003/2026), 13 (001/2026) | Juros até 0,033%/dia e multa 2% (C); aviso 30 dias, 60 para essenciais (C); sem corte em sáb., dom., feriado e véspera (C); religação 48 h (C); ligação 10 d.u. (C) |
| PA | AMAE Rurópolis | Rurópolis | Só lei institucional (LC 411/2020) | Nenhum ato | Todos NC |
| PA | SANEÁGUA Redenção | Redenção | Só lei institucional (LC 176/2025) | Nenhum ato | Todos NC |
| MA | AGERT (Timon) | Zona urbana de Timon; Águas de Timon | Res. 02/2016; Res. 05/2025 (reajuste e estrutura); Decreto 0114/2014 conflitante | Nenhum ato | Res. 02/2016: juros até 0,033%/dia, multa 2%, IPCA, aviso 30 dias, sem corte de sexta e véspera, religação 48 h (C); Decreto 0114/2014: 72 h e juros 1% a.m. (C); prevalência NC |
| MA | MOB | CAEMA; 11 municípios na planilha ANA (cobertura real NC) | Res. ARSEMA 001/2012 (texto não lido); tarifa: ARSEMA 16/2016 | Nenhum ato | Todos NC |

ERIs adicionais de SP vistas em menor profundidade (ARMPF Porto Ferreira, ARSAE Mirassol, ARSAE Andradina, AGRF-DAEA Araçatuba, ARSAE Castilho) constam do apêndice B.1.

### 5. Fontes e limites

- Planilhas oficiais da ANA (lista positiva 2025, resíduos 2025 e tarifa social jul/2026), páginas da ANA sobre os ciclos, atos da ARSP e da ARIES lidos em PDF (texto extraído; os que o PDF identifica como minuta estão sinalizados).
- Quatro agentes auxiliares pesquisaram as ERIs da parte B; as notas deles foram incorporadas nos apêndices. Os JSONs correspondentes foram validados e deduplicados contra as partes 1 a 5 (removidas 5 resoluções da AGR Tubarão já constantes da parte 2 e 1 da ARSEMA já constante da parte 5).
- Notícias de imprensa e páginas de concessionárias foram usadas apenas como fonte secundária e estão marcadas assim.

### Apêndices da parte B (notas integrais dos agentes)

#### B.1 SP e RJ (agências municipais)

Data da pesquisa: 22/10/2026. Arquivo de itens: `b1.json` (57 itens). Convenção: "confirmado" = lido no texto do ato; "não confirmado" = não lido, não localizado ou ambíguo. PDFs digitalizados foram lidos por OCR e estão indicados; conferir artigos no original antes de usar em código.

Fontes de apoio: planilhas da lista positiva da ANA (dez/2025) em `eri_matrix.json` e `ts_eris.txt`. As planilhas da ANA mostram, por NR, "C" (Cumpriu) ou "D" (Desobrigado); a ausência de uma NR significa que a ERI não consta da lista positiva daquela NR.

##### 1. SP - AMAE de Marília (Agência Municipal de Água e Esgoto de Marília)

- CNPJ: 52.061.181/0001-60 (mesmo CNPJ do antigo DAEM). Site: amaemarilia.com.br.
- Abrangência: município de Marília. Prestador: RIC Ambiental - Água e Esgoto de Marília S/A, concessão CST 1720/2024 (35 anos, desde 05/09/2024). Antes, prestação direta pelo DAEM, transformado em agência pela LC 938/2022.
- ANA 2025: 1 município; NR 5 e NR 6 Desobrigado; sem NR 3 e NR 8. Não consta da Tarifa Social (ts_eris).
- Normas localizadas:
  - Decreto Municipal 14.442, de 05/09/2024 (Estatuto da AMAE), vigente; alterado por Decreto 15.036/2026 (só a ementa foi vista). URL: https://amaemarilia.com.br/images/fck/file/DecretoAMAE.pdf
  - Decreto Municipal 14.440/2024 (Regulamento dos Serviços de Água e Esgoto, em uso pela concessionária; capa indica 06/09/2024; contrato de adesão cita 05/09/2024). Lido em cópia da concessionária: https://ricambiental.com.br/wp-content/uploads/2025/04/Regulamento-de-Servicos-de-Agua-e-Esgoto-2.pdf. O Decreto 14.441/2024 (regulamento da concessão) é citado pela imprensa, não foi localizado.
  - Regulamento DAEM atualizado em maio/2024 (versão anterior à concessão; situação a confirmar): https://amaemarilia.com.br/site/images/fck/file/REGULAMENTO-DAEM-atualizado-mai_2024.pdf
  - Resolução AMAE 04/2026, homologação do reajuste de 6,72% (IPCA), de 24/02/2026, Diário Oficial ed. 4141: https://www.marilia.sp.gov.br/portal/diario-oficial/ver/4292
  - Contrato CST 1720/2024: https://www.marilia.sp.gov.br/portal/contrato/2848
  - Resolução AMAE 02/2026 (parcelamento de débito fiscal da autarquia) é de natureza tributária interna, fora do escopo.
- Adoção das NR 1 a 16: nenhum ato próprio localizado. O Estatuto (art. 3, I) exige observar as NR da ANA. Reportagem de jul/2026 informa que, em 01/08/2025, a AMAE não havia editado nenhuma norma regulatória essencial (fonte secundária).
- Parâmetros variáveis:
  - Regulamento do Decreto 14.440: multa 3% até 10 dias, 6% até 20 dias, 9% acima de 20 dias, na omissão de normas do poder concedente (art. 100, parágrafo único) - confirmado. Juros, correção, aviso de corte, vedação de corte, prazos de atendimento - não confirmados.
  - Regulamento DAEM (versão final, Decreto 13.835/2022): juros 1% ao mês, multa 3% até 30 dias, 5% depois, 10% em dívida ativa, atualização IPCA (art. 120 §2) - confirmado; suspensão após 30 dias de atraso, limitada às 3 últimas contas (art. 128); religação em até 2 dias úteis (art. 130) e urgência em 6 a 8 horas (art. 131) - confirmado; esgoto não inferior a 75% da água (art. 110 §6) - confirmado.
  - Tarifa social: o regulamento DAEM prevê (art. 111, par. único) sem número; a concessionária divulga 50% nas faixas 0-5 m3 e 6-15 m3 pela Lei 14.898/2024 (fonte secundária) - não confirmado em ato da AMAE.
  - Reajuste anual pelo IPCA; 6,72% homologado em 24/02/2026 - confirmado.
- Pendências: (a) sem resolução da AMAE sobre condições gerais, tarifa social ou NR; (b) Decreto 14.440/2024 e 14.441/2024 no Diário Oficial não localizados; (c) Decreto 15.036/2026 e LC 1.037/2026 (estrutura da AMAE) não lidos; (d) o contrato de adesão da concessionária cita a Deliberação ARSESP 106/2009 como norma subsidiária, não verificado; (e) ambiente instável: intervenção em 2025, invalidação do contrato e repactuação em 19/11/2025 (Decreto 14.856/2025), o que pode alterar normas nos próximos meses.

##### 2. SP - ARESPCAB (Agência Reguladora de Serviços Públicos do Município de Casa Branca)

- CNPJ: 36.654.166/0001-79. Site: arespcab.sp.gov.br (tabela de resoluções filtrada por ano; usar o seletor de ano).
- Abrangência: convênios com Casa Branca, Estiva Gerbi, São Sebastião da Grama, Araçoiaba da Serra e Tuiuti (água e esgoto, por concessionárias privadas como Águas de Casa Branca SPE Ltda, Águas de Estiva Gerbi e Águas de Tuiuti; a agência também regula transporte coletivo e resíduos). A matriz da ANA conta 4 municípios.
- ANA 2025: NR 3 Cumpriu; NR 5 e NR 6 Desobrigado; sem NR 8. Tarifa Social: 4 itens "Concluído" (ts_eris).
- Normas de condições gerais e fiscalização (todas lidas): Res. 89/2026 (02/03/2026), Res. 88/2026 penalidades (02/03/2026), Res. 90/2026 qualidade (04/03/2026), Res. 91/2026 reajuste (09/03/2026), Res. 92/2026 ouvidoria (09/03/2026), Res. 79/2025 não conformidades. Elas revogaram as resoluções de 2020 e 2021. URLs no JSON (base https://www.arespcab.sp.gov.br/_files/ugd/).
- Tarifa: Res. 99/2026 (25/06/2026), reajuste de 5,0707% em Casa Branca; Res. 100/2026 (26/06/2026), reequilíbrio de 1,85% por efeito da tarifa social; Res. 94/2026 (Araçoiaba da Serra) e 97/2026 (São Sebastião da Grama) não lidas (digitalizadas); Res. 83/2025 (Tuiuti), 65/2024 (Estiva Gerbi), 60/2024 (Tuiuti), 48/2022 (Casa Branca, estrutura tarifária) e 50/2022 existem, não lidas.
- Tarifa social: Res. 69/2025 (21/01/2025), que revogou a Res. 52/2022.
- Adoção de NR (atos próprios): NR 3: Res. 82/2025 (03/11/2025), que revogou a 62/2024; NR 5: Res. 87/2026 (27/02/2026), que revogou a 63/2024; NR 8: Res. 81/2025 (01/10/2025), que revogou a 64/2024; NR 7: Res. 96/2026 (14/04/2026). Sem ato localizado para NR 4, 6, 9, 11, 12, 13 (a Res. 89/2026 cumpre papel de NR 11 sem citá-la; a Res. 69/2025 e 100/2026 antecedem e acompanham a NR 13).
- Parâmetros variáveis (Res. 89/2026): juros de mora 1% ao mês e multa de 0,33% ao dia limitada a 10% (art. 150, par. único) - confirmado; fatura com 7 dias de antecedência (art. 150) - confirmado; aviso de suspensão com comprovação de recebimento e 30 dias (art. 191) - confirmado; corte vedado após 12h de sexta e na véspera de feriados (art. 195) - confirmado; religação 12 h/24 h/72 h (art. 199) - confirmado; ligação urbana: 3 dias úteis de vistoria + 10 dias úteis - confirmado; resposta a reclamações em 10 dias úteis - confirmado; negativação após 30 dias corridos da notificação (art. 149 §4) - confirmado; índice de correção monetária - não confirmado. Tarifa social (Res. 69/2025): 50% até 15 m3, renda per capita até 1/2 salário mínimo, CadÚnico ou BPC - confirmado.
- Pendências: Res. 74/2025 (multas por descumprimento de prazos) e 52/2022 digitalizadas, lidas só em parte; valores de multa da Res. 88/2026 não extraídos; datas das Res. 79/2025, 62 a 64/2024 não confirmadas; a página usa seletor de ano, por isso uma leitura sem JavaScript mostra só 2020.

##### 3. SP - SAEMJA (Agência Reguladora do Serviço de Água, Esgoto e Saneamento do Município de Jaú)

- CNPJ: 50.760.370/0001-03. Site: saemja.jau.sp.gov.br.
- Abrangência: município de Jaú. Prestador: Águas de Jahu S/A (concessão plena), com concessões históricas de Águas de Mandaguahy e Sanej. Há procedimento arbitral entre município e concessionária (documentos até 07/2026 no site).
- ANA 2025: 1 município; NR 5 e NR 6 Desobrigado. Não consta da Tarifa Social (ts_eris).
- Normas (todas lidas, exceto indicado): Regulamento aprovado pela Res. 08/2017 (31/03/2017) e alterações (Res. 15/2018, 20/2019, 28/2022 não lida, 40/2023, 46/2025, 47/2025); Res. 38/2023 (hidrômetro), 39/2023 (tempo de atendimento), 44/2024 (reservatórios e abastecimento); Res. 45/2024 (tarifa social) rotulada "SUSPENSA" no site; tabela tarifária vigente de referência 01/2024. Outras resoluções listadas no site (01/2016 a 49/2025, incluindo 27/2022 sanções, 31/2022 AIR e ARR, 36/2023 níveis de água e 49/2026 dedetização) não foram lidas.
- Tarifa: Tarifa Referencial de Água R$ 3,607; domiciliar mínimo de 10 m3 a R$ 36,07 (água) e igual em esgoto; tarifa social mínima R$ 14,42. Reajustes anteriores: Res. 12/2017, 18/2018, 22/2019, 24/2020 e 25/2021, mais ofícios de 2022 e 2023 no site. Não localizado reajuste posterior a 2024.
- Adoção de NR: nenhum ato próprio localizado. As resoluções citam a NR 4 e a Lei 14.898/2024 apenas na Res. 45/2024.
- Parâmetros variáveis (regulamento consolidado): multa 2% + juros 1% ao mês pro rata (art. 127 §1) e juros 1% + multa até 2% (art. 177) - confirmado, com redação diferente nos dois artigos; corte a partir do 30º dia do aviso (art. 78 §2, texto original) - confirmado; interrupção vedada em dias não úteis (art. 78 §6, Res. 40/2023) - confirmado; suspensão programada vedada em sextas, sábados, domingos, feriados e vésperas (art. 159 §4) - confirmado; aviso duplo de 60 dias para saúde, educação, internação coletiva e tarifa social (art. 159 §5) - confirmado; ligação em 5 dias úteis (art. 160) - confirmado; atendimento: 15 min no balcão, 10 min no 0800, 24 h no site, 10 min no WhatsApp (Res. 39/2023) - confirmado; devolução de pagamento em duplicidade em 15 dias (Res. 46/2025) - confirmado; índice de correção monetária - não confirmado. Tarifa social (Res. 45/2024): 50% até 15 m3 e 25% de 15 a 20 m3 - confirmado no texto, mas a resolução consta como suspensa.
- Pendências: motivo e data da suspensão da Res. 45/2024; Res. 28/2022 e demais digitalizadas; situação tarifária durante a arbitragem.

##### 4. SP - SRJ (Serviço de Regulação de Saneamento de Jacareí)

- CNPJ: 21.971.473/0001-06. Site: srjacarei.sp.gov.br (lista de resoluções com links embutidos em visualizador; os PDFs estão em /wp-content/uploads/).
- Abrangência: Jacareí (SAAE, prestação autárquica), Potim (concessionária Águas de Potim SPE Ltda, contrato 60/2022) e Aparecida (contrato de regulação noticiado pelo site, norma não localizada). Lei de criação: Lei Municipal 5.806/2013.
- ANA 2025: NR 3 Desobrigado; NR 5 e NR 6 Desobrigado; NR 8 Cumpriu. Tarifa Social: 1 item "Concluído".
- Condições gerais: Res. 07/2017 (18/08/2017), alterada pela Res. 26/2023 (30/08/2023). Existe também Regulamento Geral do SAAE (revisão 2024) em saaejacarei.sp.gov.br, protegido por verificação anti-robô e não lido. Serviço de drenagem: Res. 09/2017.
- Tarifa: Res. 02/2015 (rito de reajuste/revisão); reajuste anual por resolução: Res. 33/2025 (13/08/2025, 9,80%) e Res. 41/2026 (07/08/2026, 4,64%, IPCA jul/2025-jun/2026); Potim: Res. 40/2026 (4,64%). Tabela de serviços: Res. 27/2024, 36/2025 e 38/2026.
- Tarifa social: Res. 30/2025 (18/02/2025): 50% sobre os primeiros 15 m3, renda per capita até 1/2 salário mínimo, CadÚnico ou BPC, cadastro automático - confirmado.
- Fiscalização e outros: Res. 08/2017 (não conformidades), 10/2018 (penalidades), 19/2020, 13/2019 (contabilidade regulatória), 01/2015 e 31/2025 (ouvidoria), 11/2018 (ações em desabastecimento).
- Adoção de NR (atos próprios): NR 7: Res. 34/2025; NR 8: Res. 35/2025 e 37/2025 (soluções alternativas); NR 9: Res. 39/2026. Citada NR 4 na Res. 30/2025. Sem ato localizado para NR 3, 5, 6, 11, 13.
- Parâmetros variáveis (Res. 07/2017, OCR): aviso de suspensão com comprovação e 30 dias (art. 121) - confirmado; corte vedado após 12h de sexta e na véspera de feriados (art. 124) - confirmado; religação 12 h/24 h/72 h (art. 127) e urgência 4 h (art. 128) - confirmado; resposta a queixas em 7 dias úteis (art. 58) - confirmado; ligação 3+10 dias úteis na área urbana (art. 70) - confirmado; juros e correção "conforme legislação federal" (art. 112) sem percentual - não confirmado para valores; multa por atraso - não confirmada.
- Pendências: Res. 02/2015, 10/2018, 13/2019, 19/2020 e outras digitalizadas só com cabeçalho; Regulamento Geral do SAAE; norma de Aparecida.

##### 5. RJ - SANEAR (Agência de Saneamento Básico do Município de Resende)

- CNPJ: 39.750.948/0001-08. Site: sanearesenderj.com.br.
- Abrangência: município de Resende. Prestador: Águas das Agulhas Negras S.A. (CAAN), Contrato de Concessão 018/2007, com 5 termos aditivos (o 5º, de 2019). Regulação criada pela Lei 3.128/2014 (Lei 3.324/2017 e 3.829/2022 são leis de organização administrativa).
- ANA 2025: NR 5 e NR 6 Desobrigado. Sem NR 3 e NR 8. Não consta da Tarifa Social (ts_eris).
- Normas localizadas: Decreto Municipal 2.405/2008 (Regulamento da Prestação de Serviços; OCR); Resolução SANEAR 01/2016 (dados regulatórios); Resolução SANEAR 02/2018 (ouvidoria); página "Estrutura tarifária" com imagens por ano e regra de atualização anual em 1º de janeiro por fórmula paramétrica (4º Termo Aditivo, cláusula 8); IN 01/2013 (hidrantes), fora do escopo. A seção "Resoluções ANA" do site está vazia.
- Adoção das NR: nenhum ato próprio localizado.
- Parâmetros (Decreto 2.405/2008, OCR): corte a partir do 30º dia do recebimento do aviso e restabelecimento em até 3 dias (art. 55 §2 e §3) - confirmado com ressalva de OCR; ramal retirado após mais de 3 meses de ligação interrompida (art. 56); multa por atraso "conforme legislação vigente" (art. 103) sem percentual; juros, correção, vedação de corte em sexta, prazos de atendimento e critérios da tarifa social - não confirmados. Esgoto cobrado a 80% da água, igual se tratado (anexo do mesmo PDF).
- Estrutura 2025 (imagem, OCR): Tarifa Referencial de Água R$ 3,6590, faixas de 0-10 m3 (mínimo) até acima de 45 m3, com colunas de água, esgoto coletado e esgoto tratado.
- Pendências: a página "Tarifa Social" é só imagem e não foi lida; contrato e termos aditivos digitalizados não lidos integralmente; tabela de 2026 não localizada; confirmar se o Decreto 2.405/2008 segue vigente.

##### 6. RJ - Rio-Águas (Fundação Instituto das Águas do Município do Rio de Janeiro)

- CNPJ: 02.712.055/0001-50. Site: fundacaorioaguas.prefeitura.rio.
- Abrangência regulatória: apenas o esgotamento sanitário da Área de Planejamento 5 (AP5; 24 bairros da Zona Oeste, de Deodoro a Guaratiba), concessão à Zona Oeste Mais Saneamento (F.AB. Zona Oeste S/A), Contrato 001/2012 (desde 05/2012). A ANA conta 1 município.
- ANA 2025: NR 3 Cumpriu; NR 5 e NR 6 Desobrigado. Tarifa Social: "Obtenção dos dados do CadÚnico e BPC" (1 item), com nova data em 10/03/2026.
- Relação com a AGENERSA (sem duplicar o que ela já cobre): conforme a Agenda Regulatória 2025-2026, a AGENERSA regula a produção de água da CEDAE no município, a Águas do Rio (Blocos 1 e 4: Zona Sul, Centro, Zona Norte e parte da AP4), a Iguá (Bloco 2: AP4 exceto Vila Valqueire) e a Rio+ Saneamento (distribuição de água, que a tabela da concessionária indica ser a da AP5). O regulamento estadual de serviços dos Blocos 1 a 4 é o Decreto estadual 48.225/2022 (citado em deliberação da AGENERSA; norma de outra parte do levantamento). A Prolagos atende a Região dos Lagos (não o município do Rio) e é regulada pela AGENERSA. Na AP5, portanto, o Ziip precisa tratar dois reguladores: Rio-Águas (esgoto) e AGENERSA (água).
- Normas da Rio-Águas (lidas): Portaria "N" 002/2019 (15/07/2019), Regulamento do Serviço de Esgotamento Sanitário; Portaria 001/2025 (15/08/2025) indenização de bens reversíveis (NR 3); Portaria 004/2025 (19/12/2025) processo regulatório; Portaria 005/2025 (23/12/2025) fiscalização e sanções; Portaria 001/2026 (22/04/2026) soluções alternativas (NR 8). Portarias 001/2011 e 001/2019 constam como original/revogada; Portaria 001/2017 revogada pela 004/2025.
- Tarifa: Contrato 001/2012, cláusula 18 (1º TA): reajuste a cada 12 meses pelo IPCA-E de setembro, aplicado em janeiro, homologado pela Rio-Águas em até 15 dias. Estrutura 2026 publicada pela concessionária (fonte secundária). Tarifa social: art. 37 da Portaria 002/2019 e agenda regulatória (consumo de até 21,24 m3/mês; aplicação da Lei 14.898/2024 com 50% nos primeiros 15 m3 em análise); 35% das economias da AP5 com tarifa social em 2025.
- Parâmetros variáveis (Portaria 002/2019): multa de mora de 2% e juros de 1% ao mês pro rata die (art. 29 §2) - confirmado; tarifa mínima de esgoto de 15 m3 (domiciliar/público) e 20 m3 (comercial/industrial) (art. 31) - confirmado; aviso e prazo de corte, vedação de corte, correção monetária e prazo de atendimento - não confirmados (o corte de água é do prestador de água).
- Adoção das NR: NR 3 (Portaria 001/2025) e NR 8 (Portaria 001/2026); demais não localizadas. Condições gerais de água e esgoto no restante do município: normas da AGENERSA e contratos 32/2021 e 33/2021 (Águas do Rio), fora deste lote.
- Pendências: percentual e data oficial do reajuste de 2026 da AP5; decisões de homologação tarifária não localizadas como atos numerados; Portaria 001/2011 sem status.

##### 7. Outras ERIs de SP encontradas (mala direta da ANA e levantamento do Ministério das Cidades; ausentes da lista positiva de dez/2025)

Incluídas com menor profundidade, para completar o cadastro. Não constam das planilhas eri_matrix.json.

- ARMPF - Agência Reguladora de Serviços Públicos do Município de Porto Ferreira (LC 101/2010). Concessionária BRK, Contrato 55/2011. Itens: Decreto 3.472/2026 (irregularidades e penalidades, aviso de corte de 30 dias), Decreto 3.581/2026 (reajuste de 3,60%, efeitos em 06/10/2026), IN 14/2024 (vencimento da fatura). Pendências: regulamento de serviços próprio e tarifa social não localizados.
- ARSAE Mirassol (Lei 3.066/2007). Concessionária Sanessol (Iguá), Contrato 386/2007. Item: Portaria 001/2008 (regulamento; multa 2%, juros 1% ao mês, IPCA; religação em 24 h). Lido em cópia da concessionária, pois o site da agência recusou o download. Pendências: resoluções homologatórias recentes e tarifa social; histórico de litígio tarifário.
- ARSAE/ARSAN Andradina. Concessionária Águas de Andradina (Iguá). Item: Portaria ARSAE 015/2011 (regulamento; multa 2%, juros 1% ao mês; religação em 24 h), em OCR, cópia da concessionária. Pendências: ato de tarifa de 2026 (tabela vigente desde 05/04/2026 só pela concessionária) e tarifa social.
- AGRF-DAEA Araçatuba (Lei 7.421/2011). Concessionária GS Inima Samar. Item: Resolução 04/2024 (racionamento). Pendências: regulamento e tarifas (resoluções do site em PDFs digitalizados sem ementa; reajuste de 4,26% em 03/2026 só por notícia).
- ARSAE Castilho (Lei 2.001/2010). Concessionária Águas de Castilho S/A. Itens: Resolução Homologatória 001/2026 (reajuste 11,25%) e referência à Lei 2.848/2019 (religação gratuita em 12 h). O site recusou download automático; leitura pelo Diário Oficial. Pendências: condições gerais e decreto de tarifa social 6.582/2021 não lidos.
- Sem item no JSON (motivo): ARSAEG Guaratinguetá, extinta; regulação transferida à ARSESP por termo de cooperação de 2020 (coberta em outra parte). ARSEP Mauá/SAMA: a água é regulada pela ARSESP e a agência trata do esgoto concedido (BRK); nenhuma norma de condições gerais ou tarifa localizada. AR-SBC São Bernardo do Campo: água regulada pela ARSESP; nenhuma norma localizada. Agências de outros estados: fora do lote. EMUSA (RJ): é a empresa municipal de Niterói; serviço de Niterói regulado pela AGENERSA; sem norma própria. AGERSAN Macaé (RJ) e AGENERSA, ARES-PCJ, ARSESP e ARVAP: cobertos em outras partes.

##### 8. Pendências gerais

1. PDFs digitalizados com leitura apenas por OCR: SRJ Res. 07/2017, SANEAR Decreto 2.405/2008, ARESPCAB Res. 69/2025, 99/2026 e 100/2026, Andradina Portaria 015/2011. Conferir artigos no original.
2. Sites que bloquearam download automático (HTTP 403 ou verificação anti-robô), contornados por cópias da concessionária ou Diário Oficial: ARSAE Mirassol, ARSAE Castilho, SAAE Jacareí.
3. Nenhum ato próprio de adoção de NR 11 (condições gerais) ou NR 13 (estrutura tarifária e tarifa social, Resolução ANA 271/2025) foi localizado em qualquer ERI do lote; a verificação da NR 13 começa em 20/05/2028 (art. 69 da NR 13). As ERIs que já têm condições gerais próprias (ARESPCAB, SRJ, SAEMJA, Rio-Águas) devem ser tratadas como regime próprio, com revisão futura.
4. Todos os reajustes e as tabelas tarifárias mudam por ato anual; modelar como dado do tenant, não como regra fixa (ver regra de bifurcação por parâmetro).

#### B.2 Minas Gerais

Data da pesquisa: 22/10/2026. Arquivo de itens: `b2.json` (39 itens). Leituras de PDFs digitalizados foram feitas por OCR (Tesseract, idioma inglês); onde isso afeta a precisão, está indicado.

Convenções: "confirmado" = lido no texto oficial; "não confirmado" = não localizado ou só noticiado. Números de NR só são atribuídos quando o texto do ato cita a norma.

Observação transversal: ARSAMB 002/2024 e ARSAN 001/2021 reproduzem o mesmo modelo de regulamento (o da Resolução Arsae-MG 131/2019, que a própria AMASBE lista como aplicável). Os parâmetros de encargos e de corte coincidem nos dois textos.

---

##### 1. AMASBE (MG)

- Nome: Agência Municipal de Água, Saneamento Básico e Energia de Montes Claros. CNPJ 23.726.764/0001-64 (planilha ANA).
- Abrangência: Montes Claros. Prestador de água e esgoto: Copasa (contrato de programa, autorizado pela Lei Municipal 5.059/2018 e atualizado pela Lei 5.415/2021). A tarifa e as condições gerais vêm da Arsae-MG; a AMASBE atua na fiscalização local e na ouvidoria.
- Planilha ANA (dez/2025): 1 município na NR 6 (Desobrigado); sem registro nas NR 3, 5 e 8.
- Normas próprias de condições gerais ou de tarifa: nenhuma. A página de resoluções da AMASBE republica atos da Arsae-MG: 129/2019 (água), 130/2019 (esgoto), 131/2019 (condições gerais) e 185/2023 (reajuste Copasa); a página de tarifas cita a Resolução Arsae-MG 217/2025 (3ª revisão periódica, 22/12/2025, efeito médio de 6,56%, vigência em 22/01/2026). Esses atos pertencem à parte de MG-ARSAE e não foram duplicados no JSON.
- Itens no JSON: `amasbe-lc-43-2014` (criação), `amasbe-lei-4780-2015` (Política Municipal e PMSB), `amasbe-lei-5861-2025` (metas e indicadores no PMSB, NR 8/2024).
- Adoção das NR: sem ato da AMASBE. A NR 8/2024 foi incorporada ao PMSB por lei municipal (Lei 5.861/2025). NR 11 e NR 13: sem ato local.
- Tarifa social: a página da AMASBE descreve desconto de 65% (Tarifa Social 1, extrema pobreza) e 55% (Tarifa Social 2) sem limite de consumo, renda per capita até R$ 218,00 ou 1/2 salário mínimo, sem citar ato; a FAQ cita a Resolução Arsae-MG 199/2024 (que alterou a 150/2021) e o desconto sobre os primeiros 20 m3. As duas páginas divergem entre si; a Arsae-MG editou a Resolução 227/2026 (vigência em 01/07/2026, segundo notícia do jornal Estado de Minas, fonte secundária). Tratar na parte da Arsae-MG.
- Parâmetros variáveis: todos não confirmados no âmbito da AMASBE (herdam o regulamento da Arsae-MG, fora deste escopo). O contrato com a Copasa (PDF digitalizado, lido por OCR) remete à Arsae-MG para tarifas, normas e metas.

##### 2. ARESAN (MG)

- Nome: Agência de Regulação dos Serviços de Saneamento Básico de Uberlândia; renomeada Agência Regional de Saneamento Básico pela Lei 14.743/2026 (01/04/2026). CNPJ 46.414.316/0001-20.
- Abrangência: Uberlândia. Prestador: DMAE (autarquia municipal, prestação direta). A Lei 14.743/2026 permite regular outros titulares por convênio.
- Planilha ANA: 1 município; NR 3, 5 e 6 Desobrigado; sem NR 8. Lista positiva da tarifa social de junho de 2026 (Nota Técnica 26/2026): DMAE "Concluído" em 09/06/2026.
- Condições gerais: não há resolução da ARESAN. Vale o Regulamento Geral do DMAE, Decreto Municipal 2.624/1984 (alterado pelos Decretos 10.500/2006 e 11.848/2009), documentado no PMSB (fonte oficial secundária); o decreto em si não foi lido. Item `aresan-decreto-2624-1984` com `SECONDARY_SOURCE`.
- Tarifa: metodologia na Resolução 002/2022 (19/10/2022); revisão 006/2023 (21/09/2023, +22,51%, 2º ciclo 2023-2027); reajuste 003/2024 (listada como de 01/11/2024, vigência 04/12/2024); reajuste vigente mais recente 001/2025 (21/11/2025, 5,9% segundo G1, vigência 21/12/2025). Estrutura: tarifa mínima por categoria e faixas (residencial: mínima R$ 30,89 até 10 m3, depois R$ 2,37 a R$ 8,06 por m3, valores de reportagem G1).
- Tarifa de desconto e de contingência: Resolução 004/2024 (18/12/2024), só aplicável com decreto de crise hídrica.
- Tarifa social: programa municipal (LC 309, isenção total para consumo até 20 m3 e renda até 2 salários mínimos, validade de 12 meses; o portal grafa 2003 numa página e 2009 noutra) e adesão ao desconto federal (50% nos primeiros 15 m3). Não há ato da ARESAN sobre tarifa social.
- Qualidade e fiscalização: Resolução 002/2025 (indicadores operacionais, dez/2025), 001/2023 (fiscalização, vigência em 02/05/2023), 003/2022 (sanções, situação a confirmar), 003/2023 e 004/2023 (resíduos e drenagem, fora de escopo), 003/2025 (condições gerais de resíduos, fora de escopo).
- Adoção das NR: nenhum ato identificado que cite expressamente NR 1 a 16. A Resolução 002/2025 trata do tema da NR 9, mas o número não foi lido. A Resolução 002/2022 antecede as NR de reajuste.
- Parâmetros variáveis: juros de mora, multa por atraso, correção, aviso de corte, dias vedados, prazo de atendimento do regulamento: não confirmados (dependem do Decreto 2.624/1984). Confirmados: tarifa 30 dias após a publicação (Res. 006/2023, art. 3); reclamação de vistoria por consumo elevado aceita até 1 ano (página do DMAE); DMAE responde e-mails da agência em 10 dias (página da ARESAN).
- Legislação institucional: Lei 13.679/2021, Lei 14.743/2026 (renomeia e regionaliza) e Lei 14.853/2026 (15/09/2026, governança).
- Portal: o servidor da prefeitura bloqueia clientes sem navegador (HTTP 403 para curl/WebFetch); a leitura foi feita por navegador automatizado. PDFs em docs.uberlandia.mg.gov.br baixam normalmente.

##### 3. ARSAMB (MG)

- Nome: Agência Reguladora Intermunicipal de Saneamento Ambiental de Minas Gerais (Ipatinga; criada por iniciativa do CIMVA em dez/2022; 54 municípios filiados). CNPJ 51.625.632/0001-81.
- Abrangência: planilha ANA registra 1 município (Santana do Manhuaçu, SAAE). Prestadores: autarquias municipais (SAAE) e concessões em modelagem (ex.: Alpercata). Na lista positiva da ANA (jun/2026), o SAAE de Santana do Manhuaçu está "em processo de reequilíbrio" (26/02/2026).
- Planilha ANA: NR 3 Cumpriu/Desobrigado; NR 5 Desobrigado; NR 6 Desobrigado; NR 8 Cumpriu.
- Condições gerais: Resolução 002/2024 (19/02/2024), vigente. Lida por OCR (PDF digitalizado).
- Tarifa: Resolução 019/2026 (modelos, NR 6/2024), 018/2026 (reajustes, NR 10/2024), ambas de 17/08/2026. Estudo de tarifa de Santana do Manhuaçu aprovado pela Resolução 001/2025 (12/05/2025, citada na Agenda Regulatória; o ato não consta na lista de resoluções do portal).
- Tarifa social: Resolução 016/2026 (23/02/2026), baseada na Lei 14.898/2024 e na NR 13.
- Qualidade e fiscalização: 017/2026 (indicadores, NR 9/2024), 013/2025 (metas de universalização, NR 8/2024), 015/2025 (fiscalização e penalidades), 009/2025 (indenização, NR 3/2023), 010/2025 (matriz de riscos, NR 5/2024). Ainda: 012/2025 e 014/2025 (soluções alternativas; duplicidade de ementa e data), 011/2025 e 003/2024 (resíduos), 001/2024 e 008/2025 (preço público de regulação).
- Adoção das NR (ato próprio com número do ato): NR 3 = Res. 009/2025; NR 5 = Res. 010/2025; NR 6 = Res. 019/2026; NR 8 = Res. 013/2025; NR 9 = Res. 017/2026; NR 10 = Res. 018/2026; NR 13 (tarifa social) = Res. 016/2026. NR 11 (condições gerais): não localizado; a Res. 002/2024 antecede a NR.
- Parâmetros variáveis (Res. 002/2024, confirmados por OCR): juros de mora 1% ao mês pro rata die; multa máxima 2%; atualização pelo IPCA (art. 92); aviso de corte de 30 dias com entrega comprovada ou impresso na fatura (art. 89, par. 3); corte vedado de sexta a domingo, véspera e feriado nacional, estadual ou municipal (art. 89, par. 2); religação em 48 horas (art. 108); ligação em 10 dias úteis (área urbana) e 15 dias úteis (rural) (art. 61); resposta a reclamação de cobrança em 10 dias úteis. Tarifa social (Res. 016/2026): desconto mínimo de 50% na primeira faixa; manutenção por 3 meses após perda da elegibilidade; aviso prévio de 3 meses antes da exclusão; metas de 70% dos elegíveis em 12 meses e 90% em 24 meses.
- Alerta de qualidade: a Res. 018/2026 traz cláusula sobre a concessionária de Paranaguá (PR), sinal de minuta reaproveitada; o arquivo da Res. 016/2026 está nomeado "016.2025".

##### 4. ARSAN (MG)

- Nome: Consórcio Regulador de Saneamento Básico do Norte de Minas (antes Agência Reguladora de Saneamento Básico do Norte de Minas, no âmbito do CODANORTE; sede em Montes Claros; 51 municípios segundo o site). CNPJ 39.645.506/0001-93.
- Abrangência: planilha ANA registra 1 município (SAAE de São João da Lagoa, "Concluído" na tarifa social em 20/02/2026). Também há estudos tarifários de SAAE de Francisco Sá e de resíduos de vários municípios.
- Planilha ANA: NR 3 Cumpriu; NR 5 Desobrigado; NR 6 Desobrigado; sem NR 8.
- Condições gerais: Resolução 001/2021 (06/06/2021), vigente, texto lido (49 páginas).
- Tarifa: ARSAN aprova estudos tarifários por município (Res. 007/2023, Francisco Sá, 27/02/2023; o estudo anexo propõe esgoto a 60% da água medida, contra 50% antes). Metodologia geral: 007/2026-CEN (reajuste, 18/08/2026) e 003/2025-CEN (modelos, NR 6). Não há reajuste recente de SAAE no portal.
- Tarifa social: não há ato próprio da ARSAN no portal; a ANA registra o SAAE de São João da Lagoa como concluído.
- Adoção das NR: 003/2025-CEN (NR 6, texto lido); 002/2025-CEN (indenização, tema da NR 3), 004/2025-CEN (metas, NR 8), 003/2026-CEN (matriz de riscos, NR 5), 004/2026-CEN (indicadores, NR 9), 007/2026-CEN (reajuste, NR 10): números por ementa, textos não lidos. Também há Res. AGE 003/2026 (governança frente às normativas da ANA) e 005/2026-CEN (comunicação, estoque regulatório, AIR e ARR). NR 11 e NR 13: não localizado.
- Parâmetros variáveis (Res. 001/2021, confirmados): juros de mora 1% ao mês pro rata die; multa máxima 2%; IPCA (art. 92); aviso de corte de 30 dias (art. 89, par. 3); corte vedado de sexta a domingo, véspera e feriado (art. 89, par. 2); religação em 48 horas (art. 108); religação de urgência em 8 horas (definições).
- Observações: o arquivo PDF da Res. 001/2021 tem nome de "resíduos sólidos", embora o conteúdo seja o regulamento de água e esgoto. A listagem de resoluções traz uma entrada de acordo de cooperação "ARIES/ARSAN" (grafia do portal) com data de 25/11/2026 (posterior à data da pesquisa; tratar como erro de cadastro).

##### 5. ARSAP (MG)

- Nome: Agência Reguladora dos Serviços Públicos de Abastecimento de Água Potável e Esgotamento Sanitário de Pará de Minas (Lei 5.927/2016). CNPJ 26.670.136/0001-00.
- Abrangência: Pará de Minas. Prestador: Águas de Pará de Minas S.A. (grupo Águas do Brasil), concessão 0118/2015, 35 anos.
- Planilha ANA: 1 município; sem NR 3; NR 5 Cumpriu/Desobrigado; NR 6 Desobrigado; NR 8 Cumpriu.
- Condições gerais: o regulamento está no Contrato de Concessão 0118/2015 (anexo), lido. Não há resolução própria de condições gerais; a Agenda Regulatória (ata de 09/06/2026) prevê norma alinhada à NR 11/2024 para 2026-2027.
- Tarifa: tarifa máxima definida no contrato; reajustes autorizados por resolução: 01/2022 (13,62%), 03/2025 (11/11/2025, 6,324%, aplicável a partir de 01/01/2026, vigente mais recente). Resolução 004/2026 (10/03/2026) regula a tarifa mínima por economia em condomínios com instalação coletiva.
- Tarifa social: critérios no contrato (CadÚnico, renda per capita até 1/2 salário mínimo, desconto sobre a tarifa residencial; suspensão por até 12 meses se inadimplente ou sancionado). Não há resolução de adaptação à Lei 14.898/2024 localizada; ARSAP e AMASBE não constam da lista positiva de junho de 2026.
- Outras resoluções: 001/2026 (ouvidoria), 002/2026 (Carta de Serviços), 003/2026 (cooperação da concessionária, resposta em 20 dias corridos), 005/2026 (reparos de vias; passeios em até 2 dias e vias pavimentadas em até 5 dias), 006/2026 (indicadores, NR 8 e NR 9, 18/08/2026).
- Adoção das NR: NR 8 e NR 9 = Res. 006/2026. NR 11 prevista na agenda; NR 6, 10 e 13: não localizado (tarifa regida pelo contrato).
- Parâmetros variáveis (contrato, regulamento): multa 2% e juros de mora 1% ao mês (art. 77); prazos do regulamento em dias corridos (art. 102); religação só após quitação de todos os débitos e pagamento da tarifa de religação (art. 77, par. único); atendimento de suspensão a pedido em 5 dias (art. 89). Não confirmados: correção monetária, aviso prévio do corte, dias em que o corte é vedado.
- Observação de fonte: a ARSAP não publica as resoluções no próprio site (apenas lei e contrato); os atos saem no Diário Oficial Eletrônico do município. A primeira publicação da Res. 03/2025 (datada de 21/11/2025) foi tornada sem efeito por erro de data; vale a de 11/11/2025.

##### 6. ARSARP (MG)

- Nome: Agência de Regulação de Saneamento Básico do Alto Rio Pardo (Taiobeiras; ligada ao consórcio COMAR). CNPJ 56.134.585/0001-97 (consulta Serasa, fonte secundária; não consta na planilha da ANA).
- Abrangência: municípios do Alto Rio Pardo (ex.: Taiobeiras, Serranópolis de Minas, Santo Antônio do Retiro, Salinas, Ninheira, Indaiabira, segundo cadastros do Instituto Água e Saneamento e notícias da agência). A Copasa (Taiobeiras) e a Copanor (Salinas) prestam serviços na região, com tarifas fixadas pela Arsae-MG; quem regula a água e o esgoto de cada município do Alto Rio Pardo não foi esclarecido.
- Normas localizadas no portal oficial (arsarp.mg.gov.br/resolucoes): 4 resoluções de 2024, todas sobre resíduos sólidos, diárias, preço de regulação de resíduos e Diário Oficial. Nenhuma norma de água e esgoto. Por isso não há item no JSON.

---

##### Outras agências de MG verificadas

Conferidas as listas da ANA (agências infranacionais, 1 estadual, 2 municipais e 5 intermunicipais), do levantamento do Ministério das Cidades (Quadros 4 e 5), da ABAR e da lista positiva de jun/2026. Nenhuma agência de água e esgoto de MG além das já cobertas (Arsae-MG, ARISB-MG, ARIS-MG, ARISMIG) e das seis deste grupo apareceu.

##### Pendências

- AMASBE: sem normas próprias; falta o texto da Lei 4.780/2015 (possível regra municipal de multa e juros) e do contrato de programa com a Copasa (OCR parcial). Tarifa social local sem ato citado na página.
- ARESAN: Decreto 2.624/1984 não localizado em texto (necessário para juros, multa, aviso e vedações de corte, prazos); Res. 002/2022 e 003/2022 digitalizadas e não lidas por completo; Res. 002/2025 (indicadores) lida só no preâmbulo; Res. 003/2024 (reajuste) não aberta; texto de Res. 001/2025 e 004/2024 com tabelas ilegíveis por OCR; data exata da Res. 002/2025 desconhecida; ano da LC 309 (2003 ou 2009) a confirmar.
- ARSAMB: Res. 001/2025 (tarifa de Santana do Manhuaçu) e estudo de 04/04/2025 não abertos; NR 11 sem ato; Res. 012/2025 e 014/2025 duplicadas; tarifas vigentes por prestador não levantadas.
- ARSAN: textos de 002/2025-CEN, 004/2025-CEN, 003/2026-CEN, 004/2026-CEN e 007/2026-CEN não lidos (números de NR deduzidos da ementa); tarifa social e NR 11 e 13 sem ato; reajustes de SAAE posteriores a 2023 não localizados.
- ARSAP: percentual de desconto da tarifa social e valores da tabela do contrato não extraídos; aviso de corte, dias vedados e correção monetária não localizados; Lei 5.927/2016 não lida.
- ARSARP: sem norma de água e esgoto localizada (agência com atuação documentada em resíduos sólidos; regulação de água e esgoto de seus municípios não esclarecida).
- Geral: números das NR 1 a 16 só foram atribuídos quando o texto do ato os citava (NR 3, 4, 5, 6, 8, 9, 10 e 13 identificadas). A numeração oficial completa das NR deve ser conferida contra o quadro da ANA por quem consolidar o levantamento.

#### B.3 PR, RS, SC e MT

Data da pesquisa: 22/10/2026. Arquivos de apoio baixados em `scratchpad\dl6b\b3\`. Itens estruturados em `scratchpad\g6out\b3.json` (40 itens). Marcas: "confirmado" = lido no texto oficial; "não confirmado" = não localizado ou não legível.

Legenda das NR da ANA usada abaixo: NR 3 indenização; NR 4 governança; NR 5 matriz de riscos; NR 6 modelos de regulação tarifária; NR 7 resíduos sólidos (condições gerais); NR 8 metas de universalização; NR 9 indicadores operacionais; NR 10 reajuste tarifário; NR 11 condições gerais de água e esgoto; NR 13 estrutura tarifária e tarifa social. Prazo federal para concluir a tarifa social: 11/12/2026 (aviso da ANA de 21/09/2026; os atos locais citam 10/12/2026 pela vigência da Lei 14.898/2024).

##### PR - CAGEPAR (Central de Água, Esgoto e Serviços Concedidos do Litoral do Paraná)

- CNPJ: 79.612.362/0001-93. Abrangência: município de Paranaguá. Prestador: Paranaguá Saneamento S.A. (concessão plena, grupo Iguá, contrato de 2012). Autarquia municipal reguladora (Lei Complementar 181/2015, fonte secundária). Planilha ANA 2025: 1 município; NR 3 cumprida, NR 5 e 6 desobrigada.
- Condições gerais: não localizada resolução. Tarifa: não localizada resolução; reajustes homologados pelo Conselho de Regulação (parecer de 14/10/2024; 3,67% em 21/11/2024 segundo a concessionária; 23,92% acumulados autorizados por decisão judicial em 08/2025, fontes secundárias). Tarifa social: não localizado ato.
- Adoção NR 1 a 16: não localizada.
- Parâmetros variáveis: todos não confirmados.
- Itens JSON: 1 (lei de transformação, SECONDARY_SOURCE).

##### PR - ORCISPAR (Órgão Regulador do Consórcio Intermunicipal de Saneamento do Paraná - CISPAR)

- CNPJ: 04.823.494/0001-65 (Maringá). Abrangência: 54 municípios paranaenses (planilha ANA e site), prestadores em geral SAAE/SAMAE e serviços autônomos municipais (por exemplo Jussara, Andirá, Marechal Cândido Rondon, Flórida). Planilha ANA 2025: NR 3, 5 e 6 desobrigado; sem registro de NR 8.
- Condições gerais: Resolução ORCISPAR 28/2025 (27/08/2025), Regulamento Padrão por adesão do prestador, VIGENTE; baseado na NR 11. Em consulta: CP 46/2026 (minuta de resolução sobre condições gerais da prestação e da fiscalização, com manual orientativo). https://orcispar.pr.gov.br/pagina/1230_Consulta-no-462026-Resolucao-Sobre-as-Condicoes-Gerais-da-Prestacao.html
- Tarifa: Resolução CISPAR 38/2022 (04/08/2022), procedimento de reajuste e revisão (cesta de índices), em uso em 2025-2026; tarifas por município em resoluções individuais (Res. CRFS/CR). Exemplo: Res. 33/2025 Jaguariaíva (revisão 17,46%).
- Tarifa social: Res. 13/2025 (26/03/2025); desde 2026 as resoluções municipais combinam revisão e tarifa social (Res. CR 19/2026 a 69/2026, muitas).
- Atendimento, qualidade, corte, penalidades: Res. 40/2022 (penalidades), Res. CISPAR 08/2024 (Manual de Fiscalização, não lida), Res. CRFS 22/2023 (disponibilidade de esgoto), Res. 02/2026 (indicadores, revoga Res. 08/2023), Res. CRFS 04/2023 (Ouvidoria, não lida), Res. CRFS 26/2025 e 03/2026 (soluções alternativas, não lidas), Res. 18/2026 (ciclo de fiscalização, não lida), Res. 28/2026 (indenização de investimentos, NR 3 provável, não lida), Res. 27/2026 (matriz de riscos, NR 5 provável, não lida), Res. 50/2026 (drenagem). Lista completa: https://orcispar.pr.gov.br/publicacoes-oficiais/373_Resolucoes.html
- Adoção das NR (ato próprio com número): NR 11 em Res. 28/2025; NR 8 e 9 em Res. 02/2026; NR 4 (mandatos do conselho) em ajuste de 07/2026 (Res. CISPAR 29/2026, não lida). NR 3, 5, 6 e 10: não confirmado (apenas títulos de resoluções).
- Parâmetros variáveis (Res. 28/2025): multa de mora 2% até 30 dias e 5% após 30 dias mais juros de 1% ao mês pro rata die (confirmado); correção monetária de fatura: sem previsão no artigo de mora (confirmado); parcelamento com juros legais e atualização pelo índice do reajuste (confirmado); aviso de corte 30 dias (confirmado); outras interrupções 15 dias (confirmado); corte vedado a sábados, domingos e feriados, sem vedação de véspera ou sexta tarde (confirmado); religação 48 horas, indevida 12 horas (confirmado); resposta a solicitações 30 dias, ligação 7 dias, sem dizer úteis ou corridos (confirmado o número, unidade não confirmada). Tarifa social (Res. 13/2025): 50% na primeira faixa até 15 m3, renda per capita até 1/2 salário mínimo com CadÚnico, ou BPC (confirmado).

##### RS - AGER Erechim (Agência Reguladora dos Serviços Públicos Municipais de Erechim)

- CNPJ: 17.931.344/0001-17. Abrangência: município de Erechim (1 município); prestador: CORSAN (concessionária, grupo Aegea). Regula também estacionamento rotativo e transporte coletivo. Planilha ANA 2025: NR 3, 5 e 8 cumpridas; NR 6 desobrigada.
- Condições gerais: Resolução 026/2023 (02/02/2023), VIGENTE, revogou as Res. 022/2022 e 016/2019 (que adotara o regulamento AGERGS). Penalidades: Res. 027/2023, alterada pela Res. 038/2025.
- Tarifa: reajuste anual por IRT da CORSAN, metodologia Nota Técnica 01/2020-DT (Res. 017/2020); Res. 047/2025 (24/11/2025) 5,34% desde 01/01/2026; data-base janeiro (Res. 037/2024). Nova metodologia NR 10: Res. 049/2026 (19/08/2026). Revisão extraordinária (1,21777%) em consulta pública.
- Tarifa social: hoje categoria residencial subsidiada RS (Res. 026/2023 e Norma AGER anexa); CP 01/2026 (Processo 14/2026, 9 a 25/10/2026) propõe tarifa social conforme Lei 14.898/2024 e NR 13 (minuta: 50% até 15 m3, CadÚnico até 1/2 SM ou BPC), EM_REVISAO. https://www.agererechim.rs.gov.br/publicacoes?cat=consultas-publicas
- Outros atos: Res. 046/2025 (compensação por interrupção; revogou a 040/2025), Res. 032/2023 (Ouvidoria), Res. 045/2025 (Manual de Fiscalização, vigência 01/10/2025), Res. 031/2023 (prazos para não conformidades), Res. 041/2025 (resíduos).
- Adoção das NR: NR 3 em Res. 036/2024; NR 5 em Res. 039/2025; NR 6 em Res. 044/2025; NR 8 em Res. 042/2025 e 043/2025; NR 9 em Res. 048/2026; NR 10 em Res. 049/2026; NR 13 em minuta (não vigente); NR 11 não localizada adoção.
- Parâmetros (Res. 026/2023): multa 2% (limite) e juros 1% ao mês pro rata die (confirmado); atualização monetária de fatura: não prevista (confirmado); antecedência mínima do aviso de corte em dias: não confirmado (suspensão executada em até 90 dias do prazo da notificação, confirmado); vedação de corte em sábados, domingos e feriados (confirmado); religação 48 horas, indevida 12 horas (confirmado); resposta 30 dias, sem unidade (não confirmado).

##### RS - AGESG (Agência de Regulação dos Serviços Públicos Delegados de São Gabriel)

- CNPJ: 17.329.329/0001-01. Abrangência: município de São Gabriel (1); prestador: São Gabriel Saneamento S.A. (concessão, contrato 051/2012). Criada pela Lei Municipal 3.354/2011 (informação da página oficial). Planilha ANA 2025: só NR 5 e 6 (desobrigada). Lista positiva da tarifa social: etapa "obtenção de dados do CadÚnico e BPC" (28/04/2026).
- Condições gerais: regulamento contratual (anexo do contrato 051/2012, arquivo intitulado Anexo IV, chamado Anexo VII no portal), sem resolução própria da AGESG localizada.
- Tarifa: tabela tarifária 2012-2025 (PDF digitalizado, não legível); reajuste de 3,83% em julho/2026 em fonte secundária.
- Tarifa social: critérios do regulamento (art. 30): renda até 2 salários mínimos, imóvel subnormal até 60 m2 e energia até 150 kWh, ou desempregado por 12 meses; não alinhada à Lei 14.898/2024.
- Adoção das NR: nenhuma localizada.
- Parâmetros (regulamento): juros de até 0,033% ao dia e multa de 2% (confirmado); aviso de corte 30 dias (confirmado no texto; a página da AGESG diz 30 dias úteis: divergência); corte vedado em sábados, domingos, feriados e vésperas (confirmado); serviço essencial 60 dias (confirmado); restabelecimento de suspensão indevida em 6 horas (informado pela página, artigo não confirmado); correção monetária: não confirmado.
- Itens JSON: 3.

##### SC - CISAM Meio Oeste (Consórcio Intermunicipal de Saneamento Ambiental Meio Oeste), via CISAM-REG/CREFISBA

- CNPJ: 08.484.353/0001-16 (Capinzal). Abrangência: 15 municípios consorciados, 10 com regulação delegada do saneamento: Abdon Batista, Brunópolis, Campos Novos, Capinzal, Herval d'Oeste, Joaçaba, Luzerna, Ouro, Vargem e Zortéa; prestadores SAMAE de Campos Novos, SIMAE de Capinzal e Ouro, SIMAE de Joaçaba, Herval d'Oeste e Luzerna, DMAE de Vargem. Planilha ANA 2025: NR 3, 5 e 8 cumpridas; NR 6 desobrigada. O site só abre em navegador (desafio anti-bot), PDFs baixados normalmente. https://cisam.sc.gov.br/resolucoes-normativas-cisam-reg-2/
- Condições gerais: RN CISAM-REG 01/2017 (vigor desde 21/12/2017) até 19/05/2027; RN 05/2026 (publicada 05/10/2026, NR 11) em vigor a partir de 20/05/2027, revogando a RN 01/2017.
- Tarifa: RN 03/2015 (procedimentos de reajuste e revisão); resoluções municipais (Res. CISAM-REG 03, 04 e 05/2025; 03/2026 Vargem). Tarifa social: RN 04/2026 (publicada 12/06/2026), que substituiu a RN 02/2026 revogada em 08/04/2026.
- Qualidade, penalidades: RN 02/2017 (fiscalização), RN 03/2017 (penalidades), RN 2015-01 (Ouvidoria) e 2015-02 (qualidade e monitoramento), não lidas além do cabeçalho.
- Adoção das NR (ato próprio): NR 3 em RN 01/2025 (18/08/2025); NR 5 em RN 02/2025; NR 6 em RN 03/2025 (18/08/2025); NR 7 em RN 04/2025; NR 8 em RN 05/2025 e RN 06/2025 (03/11/2025); NR 11 em RN 05/2026; NR 13 em RN 04/2026. NR 9 e 10: não localizadas.
- Parâmetros: RN 01/2017: aviso de corte 30 dias (art. 104), corte vedado após 12 horas de sexta e véspera de feriado (art. 107), ligação 10 ou 15 dias úteis, resposta 15 dias úteis, juros/multa/correção conforme legislação municipal (confirmado, sem percentuais). RN 05/2026: prazos em dias úteis, ligação 10 (urbana) ou 15 (rural) dias úteis, religação 12/24/120 horas, aviso de suspensão 30 dias, corte vedado sábados, domingos, feriados, vésperas e após 12 horas de sexta (confirmado); juros, multa e atualização monetária remetidos à legislação (percentuais não confirmados). Tarifa social (RN 04/2026): 50% até 15 m3, renda até 1/2 SM com CadÚnico/BPC (confirmado).

##### SC - AGR Tubarão (Agência Reguladora de Saneamento, Iluminação Pública e Mobilidade de Tubarão) - incluída por não estar coberta pelo briefing

- CNPJ: 10.157.678/0001-36 (sigla AGR TB na planilha). Abrangência: município de Tubarão (1); prestador: Tubarão Saneamento S.A. (concessão, contrato 038/2012). Planilha ANA 2025: NR 5 e 6 desobrigada; sem NR 3 e 8. Tarifa social: implementação concluída segundo a lista positiva da ANA.
- Condições gerais: Res. 007/2013 (serviço adequado, alterada pela 016/2017) e Decreto Municipal 2.539/2008 (não obtido).
- Tarifa: Decreto Municipal 9.159/2026 (reajuste 4,86% desde 01/05/2026); matriz em https://agr.sc.gov.br/tabela_tarifaria.
- Tarifa social: Res. 043/2025 (01/07/2025, vigor 14/07/2025), com limite de 4% das economias; atenção ao art. 26 da Res. 046/2025, que declara revogar uma "Resolução nº 043 de 01/07/2025" (possível erro material).
- Outros: Res. 040/2024 (enquadramento de faturamento), 035/2023 (consumo excessivo), 046/2025 (fiscalização e penalidades), 047/2026 (regimento interno), Ouvidoria Res. 034/2023.
- Adoção das NR: NR 8 em Res. 045/2025 e 048/2026; NR 7 em Res. 044/2025 (resíduos). NR 3, 5, 6, 10, 11 e 13: não localizadas.
- Parâmetros: prazos de atendimento da Res. 007/2013 (ligação 5 dias úteis, reparo e religação 24 horas) confirmados; aviso de corte, dias vedados, juros, multa e correção: não confirmados (dependem do Decreto 2.539/2008 e do contrato; a Res. 022/2019 sobre atualização de multas é PDF digitalizado).

##### MT - AGERR Pantanal (Agência Regional de Regulação dos Serviços de Saneamento do Complexo Nascentes do Pantanal)

- CNPJ: 35.468.416/0001-13 (São José dos Quatro Marcos, Mirassol d'Oeste). Abrangência: 12 municípios na planilha ANA; a imprensa cita 16 na RN 45/2026. Prestadores: SAAE/SMAE/DAE municipais e concessionárias. Planilha ANA 2025: NR 3 e 5 cumpridas; NR 6 desobrigada. Consórcio público; contrato reescrito pela Lei 1.009/2026 de Porto Esperidião (22/09/2026).
- Condições gerais de água e esgoto: não localizado ato da agência; há regulamentos aprovados por município (Res. da Diretoria 029/2026 aprova regulamento de Nortelândia, não lida).
- Tarifa: RN 033/2025 (modelos, NR 6, 18/08/2025); RN 45/2026 (reajuste, NR 10, 20/08/2026); reajustes individuais por Resolução da Diretoria Executiva (2022-2026).
- Tarifa social: RN 42/2026 (19/08/2026).
- Qualidade/penalidades: Res. 01/2022 (penalidades, 13/04/2022), Manual de Fiscalização de Água e Esgoto no portal, RN 41/2026 (indicadores, NR 9), RN 09/2021 (Ouvidoria), RN 01/2018 (funcionamento da regulação), RN 044/2026 (transparência).
- Adoção das NR: NR 3 em RN 029/2025; NR 5 em RN 032/2025; NR 6 em RN 033/2025; NR 7 em RN 034/2025 (resíduos, 18/08/2025); NR 8 em RN 030/2025; NR 9 em RN 41/2026; NR 10 em RN 45/2026; NR 13 em RN 42/2026; NR 4 em RN 021/2024 (transição do mandato); NR 11 não localizada.
- Parâmetros (RN 42/2026): 50% sobre os primeiros 15 m3, renda até 1/2 SM com CadÚnico/BPC, concessão automática (confirmado). Juros, multa, correção, prazo de atendimento, aviso e vedação de corte: não confirmados (sem condições gerais lidas).

##### Agências adicionais verificadas

- AGESB (Agência Municipal de Regulação dos Serviços Públicos Delegados de São Borja, RS, Lei 4.280/2010): regula a CORSAN em São Borja; aparece na relação de reguladores da CORSAN, mas não consta nas planilhas da ANA nem na lista da tarifa social. Resoluções 2022-2023 estão em links do Google Drive sem ementa; não incluída no JSON.
- AMR (Agência Maringaense de Regulação, PR): associada à ABAR, acompanha a Sanepar em Maringá; sem norma própria localizada; fora das planilhas da ANA.

##### Pendências

- CAGEPAR: portal oficial (cagepar.atende.net) fora do ar por extinção do contrato de hospedagem com o município; sem resolução localizada; confirmar com o município (Secretaria de Fiscalização das Concessões e Contratos) a lei, o regulamento e as resoluções; confirmar também o status da concessão (rescisão noticiada em 12/2025).
- ORCISPAR: ler Res. CISPAR 08/2024, Res. CRFS 04/2023, 26/2025, 03/2026, 18/2026, 27/2026 e 28/2026 e o texto final da CP 46/2026; confirmar se a Res. 13/2025 foi adaptada à NR 13; confirmar se há ato de adoção das NR 3, 5, 6 e 10.
- AGER Erechim: acompanhar a resolução final da tarifa social (após 25/10/2026); ler a Res. 026/2023 integralmente quanto ao prazo mínimo do aviso de corte (não achado em dias) e a Norma AGER da tarifa subsidiada (Anexo I).
- AGESG: tabela tarifária só em imagem; sem resoluções próprias; diário de publicações 2026 em imagem (33 páginas não inspecionadas); confirmar com a agência a tarifa social e eventual adoção das NR 11 e 13.
- CISAM Meio Oeste: confirmar datas das RN 02/2025 e 05/2025; confirmar se a RN 03/2015 e RN 2017 seguem como base após a RN 05/2026; verificar NR 9 e 10.
- AGR Tubarão: obter o Decreto 2.539/2008 e a Res. 022/2019; esclarecer o art. 26 da Res. 046/2025 e o status da Res. 043/2025; confirmar o limite de 4% frente à NR 13.
- AGERR Pantanal: condições gerais de água e esgoto e datas das RN 029 e 030/2025 não localizadas; RN 02/2018 (reajustes) no portal aponta para arquivo orçamentário (link possivelmente trocado); confirmar relação de municípios (12 ou 16).
- Valores monetários de multas por infração (ORCISPAR, AGER, CISAM, AGERR, AGR) não lidos; relevantes apenas para o módulo de penalidades do prestador, não para o usuário.

#### B.4 TO, RO, PA e MA

Data da pesquisa: 22/10/2026. Arquivo de dados: `g6out\b4.json` (29 itens). Downloads em `dl6b\b4\`.

Convenção de marcas: "confirmado" significa lido no texto do ato; "não confirmado" significa não lido ou não localizado. Os números de artigo referem-se ao texto lido.

##### 1. TO - ARP Palmas

- Nome e sigla: Agência de Regulação, Controle e Fiscalização de Serviços Públicos de Palmas (ARP). CNPJ 27.366.575/0001-89.
- Abrangência: apenas o Município de Palmas. Prestador: Companhia de Saneamento do Tocantins (Saneatins/BRK Ambiental), Contrato de Concessão nº 385/1999 (Leis Municipais 527/1995 e 1.471/2007), segundo o Relatório Técnico 062/2026 da ARP.
- ANA 2025: NR 3 sem registro, NR 5 sem registro, NR 6 desobrigada, NR 8 cumpriu (1 município; água e esgoto). Tarifa social: etapa "obtenção dos dados do CadÚnico e BPC" na lista positiva de jul/2026.
- Condições gerais: Resolução ARP nº 8, de 9/11/2018 (DOM Palmas 9/11/2018), vigente, alterada pela Res. ARP 26/2026 (2/7/2026, DOM 3.984 de 6/7/2026). Texto lido no espelho LegisWeb (https://www.legisweb.com.br/legislacao/?id=369229); PDF oficial não localizado (página de documentos antigos da ARP retorna 404).
- Tarifa: a ARP não publicou resolução tarifária localizada. A Res. 8/2018 remete a tabela progressiva e estrutura à ATR (Agência Tocantinense de Regulação); a ATR tratou do reajuste de 9,37 por cento da Saneatins/BRK pela Resolução ATR nº 13/2025, de 5/11/2025 (fonte secundária, https://afnoticias.com.br/arquivos/shares/resolucao_reajuste_tarifariopdf.pdf), cuja aplicação a Palmas é A_CONFIRMAR. A Lei 2.297/2017 atribui à ARP aprovar ajustes tarifários; a Prefeitura de Palmas cobrou da BRK em dez/2025 justificativa de reajuste e o reconhecimento institucional da ARP, o que indica disputa de competência com a ATR.
- Outros atos: Res. ARP 23/2025 (fiscalização e apuração de infrações, 30/5/2025), 24/2025 (soluções alternativas, NR 8, 7/8/2025), 27/2026 (indenização, NR 3, 7/8/2026), 28/2026 (matriz de riscos, NR 5, 7/8/2026), Lei 2.297/2017 (criação). Res. ARP 4/2017 (infrações do prestador de água) citada como alterada em 2026 e não lida. Res. 22 e 25/2025 e 21 (regimento) não tratam de água.
- Adoção de NR (ato próprio): NR 3 - Res. ARP 27/2026; NR 5 - Res. ARP 28/2026; NR 8 - Res. ARP 24/2025 (soluções alternativas; metas e indicadores na Nota Técnica 001/2025, não lida). NR 13 (estrutura tarifária e tarifa social): sem ato localizado; a tarifa social da Res. 8/2018 (69 por cento) não segue a Lei 14.898/2024. NR 9: o Relatório Técnico 062/2026 (19/8/2026) usa os indicadores, sem ato de adoção localizado. NR 6: desobrigada. NR 1, 2, 4, 7, 10, 11, 12, 14, 15, 16: não localizados.
- Parâmetros (Res. 8/2018, texto do espelho):
  - Juros de mora de até 0,033 por cento ao dia, multa de 2 por cento, correção pelo IPCA na fatura seguinte (art. 97): confirmado.
  - Contagem de prazos em dias úteis, sem o dia do início (art. 9, par. 4): confirmado.
  - Vencimento mínimo: 5 dias úteis (10 para categoria pública; 1 para desligamento a pedido) (art. 94, par. 1): confirmado.
  - Suspensão por inadimplência só com atraso a partir de 60 dias e aviso escrito com no mínimo 30 dias (art. 115, pars. 1 e 2): confirmado.
  - Vedada a suspensão após 12h de sexta-feira, sábados, domingos e vésperas de feriados (art. 116): confirmado.
  - Suspensão indevida: religação em 12 horas e crédito de indenização (art. 115, pars. 6 e 7): confirmado.
  - Tarifa social: desconto de 69 por cento sobre a tarifa mínima residencial 0-10 m3, consumo médio até 10 m3, área até 100 m2, renda até 1,5 salário mínimo (arts. 109-110): confirmado, desatualizado frente à Lei 14.898/2024.
  - Prazo de religação normal e demais prazos de serviços (Anexo I): não confirmado.
- Taxa de fiscalização (Lei 2.297/2017, art. 16): 1 por cento da arrecadação do prestador (periodicidade mensal na redação consolidada): confirmado.

##### 2. RO - AGERB (Buritis)

- Nome e sigla: Agência Reguladora de Serviços Públicos Delegados do Município de Buritis (AGERB). CNPJ 21.738.920/0001-81. Criada pela Lei 870/2014.
- Abrangência: Município de Buritis. Prestador: Águas de Buritis Saneamento (grupo Aegea), Contrato de Concessão nº 004/GAB/PMB/2015, 30 anos.
- ANA 2025: NR 5 desobrigada, NR 6 desobrigada; NR 3 e NR 8 sem registro (1 município).
- Condições gerais: não localizado regulamento de serviços próprio da AGERB. O Contrato 004/2015 está no site em PDF digitalizado sem camada de texto (não lido). A única resolução de prazos de atendimento é a Res. 002/2019 (publicada no site como "RN 02.2020 - prazos de atendimento").
- Tarifa: Resolução Homologatória 003/AGERB/2025, de 16/4/2025 (reajuste anual de 2025 pelo IGP-M, 8,44 por cento; o texto o chama de negativo, o que é incoerente). A página lista reajustes de 2017 a 2025; o de 2026 não foi localizado.
- Tarifa social: Resolução Normativa 006/AGERB/2025, de 24/11/2025 (Lei Municipal 2.134/2025). Desconto de 50 por cento sobre a primeira faixa residencial até 20 m3 (art. 3), inclusão automática, perda após 365 dias para nova solicitação (arts. 7-8), perda possível com 3 faturas em atraso (art. 9), aplicação condicionada a reequilíbrio contratual (art. 10): confirmado.
- Outros atos: RN 04/2025 (carros-pipa, 5/11/2025); RN 05/2025 (área de transbordo, resíduos); RN 01/2025 e reajuste de mototáxi (fora de escopo); RN 01/2020 (recomposição de pavimento) e RN 06/2020 (cronograma de atividades da concessionária) não lidas.
- Adoção de NR: nenhum ato de adoção localizado; a RN 06/2025 cita a NR 4 (governança) e a Nota Técnica 14/2024 da ANA como considerandos e prevê observar as futuras normas da ANA (art. 13).
- Parâmetros: juros de mora, multa, correção monetária, aviso de corte, dias de vedação do corte: não confirmados (contrato ilegível, sem regulamento próprio). Prazo de atendimento a novos usuários de água: 15 dias, sem especificar úteis ou corridos (Res. 002/2019, art. 1) - parcialmente confirmado; multa diária da concessionária de 0,01 por cento do faturamento do mês (art. 2): confirmado.

##### 3. RO - AGERROM (Rolim de Moura)

- Nome e sigla: Agência Reguladora do Município de Rolim de Moura (AGERROM). CNPJ 24.456.732/0001-59. Criada pela Lei 2.871/2014; reorganizada pela Lei 4.385/2023.
- Abrangência: Município de Rolim de Moura. Prestador: Águas de Rolim de Moura Saneamento SPE Ltda (Aegea), Contrato de Concessão nº 93/2016 (1º Termo Aditivo 085/2023).
- ANA 2025: NR 3 cumpriu; NR 5 desobrigada; NR 6 desobrigada; NR 8 sem registro (1 município). Tarifa social: etapa "obtenção dos dados do CadÚnico e BPC" (jul/2026).
- Condições gerais: Resolução Normativa nº 001/2020, de 30/11/2020 (DOM-RO 2/12/2020, ed. 2851), "Dispõe sobre a prestação dos Serviços de Água e Esgoto" (regulamento de 134+ artigos). Situação A_CONFIRMAR (sem revogação localizada; art. 43 de tarifa social superado em substância).
- Tarifa: reajustes anuais por resolução normativa (2020, 2022, 2023, 2024, 2025, 2026). Último: RN 002/2026, de 19/6/2026, 5,19 por cento (IGP-M 0,61 por cento mais 4,55 por cento de recomposição contratual).
- Tarifa social: RN 001/2026 (12/2/2026; Lei Municipal 4.706/2025): renda até 1/2 salário mínimo, 50 por cento de desconto sobre a primeira faixa até 15 m3, inclusão automática, busca ativa semestral, prazo de 15 dias para avaliação, relatório anual até o último dia útil de junho: confirmado. A Aegea publica tabelas tarifárias com faixa social própria.
- Adoção de NR (ato próprio): NR 3 - RN 02/2025 (controle patrimonial e indenização; texto de 20/8/2025, publicado em 5/12/2025); NR 9 - RN 003/2026 (19/8/2026, indicadores operacionais); NR 13 / tarifa social - RN 001/2026 (cita "Normas de Referência" sem número). NR 5 e NR 6: desobrigada. Demais: não localizados.
- Outros atos: RN 001 e 002/2021 (regimentos), RN reequilíbrio 02/2023 (1º Termo Aditivo), RN 003/2025 (resíduos).
- Parâmetros (RN 001/2020):
  - Juros de mora de até 0,033 por cento ao dia e multa de 2 por cento, sem correção monetária citada (art. 90): confirmado.
  - Vencimento mínimo de 15 dias da entrega da fatura (art. 86): confirmado.
  - Ligação em até 10 dias úteis (art. 55): confirmado.
  - Aviso de corte por inadimplência com 30 dias de antecedência (art. 98, par. 1): confirmado; 60 dias para saúde, educação e internação coletiva (art. 102): confirmado.
  - Vedado o corte em sábados, domingos, feriados e vésperas (art. 104): confirmado.
  - Restabelecimento em 48 horas e religação de urgência em 24 horas (arts. 106-107): confirmado; interrupção indevida em 6 horas (art. 101).
  - Resposta a reclamações em 15 dias, prorrogáveis por igual período (art. 116, V): confirmado.
  - Tarifa social (RN 001/2026): desconto de 50 por cento, limite de 15 m3: confirmado; o art. 43 da RN 001/2020 (limite de 20 m3, renda até 1 salário mínimo) está superado.
  - Tipo de dia (úteis ou corridos) para o aviso de 30 dias: não confirmado no texto.

##### 4. PA - AMAE/Rurópolis

- Nome e sigla: Agência Municipal de Regulação dos Serviços de Água e Esgoto de Rurópolis (AMAE/Rurópolis). CNPJ 41.991.426/0001-31.
- Abrangência: Município de Rurópolis. Concessão autorizada pela Lei 375/2017 e licitada na Concorrência 001/2019 (Consórcio Águas de Rurópolis, homologada em 29/5/2019).
- ANA 2025: NR 5 desobrigada (1 município).
- Normas localizadas: apenas o ato institucional (LC 411/2020, de 17/8/2020, alterada pela LC 003/2021), que autoriza a agência a editar regulamentos e resoluções. Nenhum regulamento de serviços, resolução tarifária, tarifa social ou ato de NR foi localizado (ver Pendências).
- Parâmetros variáveis: todos não confirmados.

##### 5. PA - SANEÁGUA Redenção

- Nome e sigla: Agência Reguladora de Águas e Saneamento Básico do Município de Redenção (SANEÁGUA; chamada também Agência de Regulação de Redenção - AGRR). CNPJ 20.769.050/0001-45.
- Abrangência: Município de Redenção. Prestador de água e esgoto: não identificado nas fontes consultadas.
- ANA 2025: NR 5 desobrigada (1 município).
- Normas localizadas: Lei Complementar 176/2025, de 8/10/2025 (regime jurídico; revoga a Lei 667/2014 no art. 78). O portal da Prefeitura registra o Termo 003/2026 (15/5/2026) de delegação de regulação de água, esgoto, drenagem e resíduos à agência e o convênio 004/2020 com a ARCON-PA.
- Condições gerais, tarifa, tarifa social e atos de NR: não localizados. Parâmetros variáveis: não confirmados.

##### 6. MA - AGERT (Timon)

- Nome e sigla: Agência Reguladora de Serviços Públicos do Município de Timon (AGERT). CNPJ 22.103.298/0001-06. Criada pela Lei 1.926/2014; reestruturada pela Lei Complementar 067/2025 (10/7/2025). Site oficial: https://agert.org/.
- Abrangência: zona urbana de Timon. Prestador: Águas de Timon Saneamento S.A. (Aegea), Contrato de Concessão nº 004/2015 (2º Termo Aditivo em 2025; 5º Termo Aditivo citado). A zona rural é atendida pelo SAAE Timon.
- ANA 2025: NR 5 desobrigada, NR 6 desobrigada; NR 3 e NR 8 sem registro. Tarifa social: etapa "em processo de reequilíbrio" na lista positiva de jul/2026.
- Condições gerais: Resolução AGERT nº 02, de 31/5/2016 (vigente, usada como fundamento da Res. 05/2025). Convive com o Decreto Municipal 0114/2014-GP (regulamento do edital de concessão), com parâmetros conflitantes (ver itens abaixo).
- Tarifa: Res. AGERT 05/2025 (29/4/2025; reajuste e nova estrutura, tarifa social vulnerável de R$ 10 de água e R$ 8 de esgoto até 18 m3); Res. 11/2026 (18/3/2026), suspensa pela Res. 12/2026 (27/3/2026); Res. 13/2026 (16/4/2026, 2ª parcela do 5º Termo Aditivo). O site da concessionária (fonte secundária) indica 2,90 por cento com vigência em 2/6/2026 atribuídos a uma "Resolução AGERT 5/2026" que não consta da lista oficial. Percentuais e tabela vigente das Res. 11 e 13/2026: não confirmados.
- Tarifa social: critérios no site da concessionária (fonte secundária): consumo até 20 m3, energia até 200 kWh, renda per capita até 1 salário mínimo, CadÚnico, imóvel único, sem débito, laudo em doença crônica. A Res. 05/2025 define a social como metade da residencial nas faixas até 20 m3: confirmado em ato. Alinhamento com a Lei 14.898/2024: não confirmado.
- Atos de fiscalização: Res. 03/2016 (sanções), Res. 14/2026 (procedimento e sanções, 2/7/2026). Outras: Res. 04/2025 (recurso de auto de infração), 06/2025 (agenda regulatória 2025-2027), 09/2025 (regimento interno).
- Adoção de NR: nenhum ato de adoção localizado na lista de resoluções (Res. 01 a 14). A página oficial lista as NR da ANA como material de consulta.
- Parâmetros (Res. AGERT 02/2016): juros de mora de até 0,033 por cento ao dia, multa de 2 por cento, correção pelo IPCA (art. 101); vencimento mínimo de 5 dias úteis (art. 97, par. 1); ligação em 5 dias úteis após aprovação (art. 30); aviso de corte com no mínimo 30 dias (art. 76, par. 1); vedado cortar às sextas-feiras e vésperas de feriados (art. 82); restabelecimento em 48 horas (art. 84): todos confirmados.
- Parâmetros conflitantes (Decreto 0114/2014): multa de 2 por cento e juros de 1 por cento ao mês com atualização monetária (art. 43); aviso de 30 dias (art. 4, par. 2) e 45 dias com dois avisos para saúde, educação e tarifa social (art. 4, par. 3); restabelecimento em 72 horas (art. 3, VI): confirmado. Qual norma prevalece: não apurado.

##### 7. MA - MOB (Agência Estadual de Mobilidade Urbana e Serviços Públicos)

- Nome e sigla: Agência Estadual de Mobilidade Urbana e Serviços Públicos (MOB). CNPJ 08.578.592/0001-35. Autarquia estadual; regula gás canalizado, transportes e saneamento básico (Lei 10.225/2015, alterada pela Lei 11.662/2022).
- Água e esgoto: sim. A MOB herdou o acervo da ARSEMA (extinta) e regula a CAEMA (Companhia de Saneamento Ambiental do Maranhão). A Lei Complementar Estadual 239/2021 indica a MOB como reguladora preferencial das microrregiões de saneamento (fonte secundária, marcolegal).
- Municípios (lista positiva ANA 2025, NR 5, só abastecimento de água, 11 municípios, todos "cumpriu"): Açailândia, Afonso Cunha, Alcântara, Aldeias Altas, Altamira do Maranhão, Alto Alegre do Maranhão, Alto Alegre do Pindaré, Alto Parnaíba, Amarante do Maranhão, Anapurus e Apicum-Açu. A lista alfabética termina em Apicum-Açu, o que sugere cobertura parcial da planilha ou declaração limitada da MOB; a cobertura real (todos os municípios da CAEMA) é A_CONFIRMAR. Timon não é regulado pela MOB (AGERT).
- Condições gerais: Resolução ARSEMA nº 001/2012 (24/4/2012), regulamento de serviços da CAEMA, listada na página oficial da MOB; arquivo original fora do ar, texto não lido. A CAEMA o cita como vigente.
- Tarifa: Res. ARSEMA 16/2016 (estrutura tarifária da CAEMA), 13/2015 (reajuste de 12,73 por cento), 04/2014 (5,86 por cento) e 01/2011. Nenhum ato tarifário posterior a 2016 localizado na MOB. Tarifa social: sem ato localizado; a MOB não consta da lista positiva da tarifa social de jul/2026.
- Fiscalização: Res. ARSEMA 10/2014 (penalidades; multa máxima de 1 por cento do faturamento anual), lida em cópia do FAOLEX (secundária). Taxa de regulação (TRCF): 1 por cento do faturamento líquido anual (Lei 11.662/2022; Res. MOB 5/2022).
- Resoluções MOB listadas em Gás e Saneamento (2/3/4 de 2017) tratam de gás (GASMAR) e não de água.
- Adoção de NR: nenhum ato próprio localizado para NR 1 a 16. A MOB cumpriu a NR 5 em 2025 sem ato visível no site (a comprovação pode ter sido por outro meio): não confirmado.
- Parâmetros variáveis (juros, multa, aviso de corte, dias de vedação, prazos, tarifa social): não confirmados (texto do regulamento não lido).

##### Extras (fora da minha lista, com água e esgoto no cadastro da ANA ou na tarifa social)

- ARCON-PA (Agência de Regulação e Controle de Serviços Públicos do Estado do Pará, CNPJ 02.598.119/0001-33): regula a concessão regionalizada MRAE e convênios com municípios. A página https://www.arcon.pa.gov.br/decretos-e-resolucoes lista, em 2026, as Resoluções nº 001/2026 (fiscalização e penalidades de água e esgoto), 003/2026 (tarifa residencial social), 005/2026 (matriz de riscos) e 008/2026 (soluções alternativas). Visto apenas em trecho de buscador; não lido nem incluído no JSON. Não consta da planilha da ANA que consultei. Recomenda-se que a parte responsável pelo Pará verifique.
- AGRF (Agência Gurupiense de Regulação e Fiscalização, Gurupi/TO, Lei 2.462/2019): regula o saneamento do município (concessão BRK); não consta das listas da ANA; o site oficial não publica resoluções de água e esgoto. Sem item no JSON.
- AGEAC, AGEMAN, ARBEL, ATR e AGERO constam das planilhas da ANA e não estão na minha lista; assumidas como cobertas por outras partes.

##### Pendências

- ARP: PDF oficial da Res. 8/2018 e seu Anexo I (prazos) não localizados; Res. ARP 4/2017 não lida; confirmar com a ARP/ATR qual agência homologa a tarifa de Palmas e se a Res. ATR 13/2025 se aplica; verificar se a ARP alterou a tarifa social frente à Lei 14.898/2024.
- AGERB: contrato de concessão em PDF digitalizado sem texto (precisa de OCR); sem regulamento de serviços próprio; reajuste 2026 não localizado; incoerência "reajuste negativo" de 8,44 por cento a conferir.
- AGERROM: confirmar se a RN 001/2020 foi alterada ou revogada (LC Municipal 354/2026 e Lei 4.385/2023 citadas); percentuais da RN 001/2025 não lidos.
- AMAE/Rurópolis: nenhum regulamento, resolução ou tarifa localizado; o site da Prefeitura só lista leis; verificar diário oficial municipal, o contrato de concessão e a existência de atos da agência.
- SANEÁGUA Redenção: nenhum ato operacional localizado; prestador de água não identificado; conferir o Termo 003/2026 e o diário oficial do município.
- AGERT: percentuais e tabela da Res. 11 e 13/2026; relação entre o Decreto 0114/2014 e a Res. 02/2016; texto da LC 067/2025 lido só em trechos de buscador (arquivo do Diário não baixado: os links retornam HTML).
- MOB: textos de ARSEMA 01/2012, 16/2016 e demais (hospedagem arwww3.sema.ma.gov.br fora do ar) - tentar Diário Oficial do Maranhão ou a Wayback Machine; atos pós-2017 da MOB para saneamento; cobertura municipal real da CAEMA.
- Nenhuma ERI da lista ficou sem norma alguma, mas AMAE/Rurópolis e SANEÁGUA Redenção têm apenas leis institucionais (sem regulamento nem tarifa).
