# Decisões a rever após a correção das citações normativas

Gerado em 22/10/2026 a partir dos relatórios de aplicação (`aplicacao-correcoes/lote-1.md` a `lote-5.md`). Em cada caso, a citação da norma foi corrigida no documento e a nota "(verificado em 22/10/2026: ...)" ficou ao lado do trecho. **Nenhuma decisão, tarefa, prazo ou parâmetro foi alterado**: cada item abaixo é uma premissa que o dono do produto, o jurídico ou o regulatório do cliente deve reavaliar.

## Lote 1

Em nenhum caso a decisão, a tarefa, o prazo ou o parâmetro foi alterado. Cada item abaixo recebeu no texto a nota "(verificado em 22/10/2026: ...)" ou o fato corrigido.

1. **Manual da ARSESP: versão de referência (ALVOS §2, §8).** O projeto leu a versão 1 (NT.F-0064-2022, 385 páginas, 29 UP). A versão vigente é a 2 de 2025 (Deliberação ARSESP 1.636/2025), com tabelas de UP, UAR e atributos revisadas. Rever se a codificação de referência e a coincidência de listas com o MCPSB (§8) devem passar a usar a versão 2; os números 385 páginas e 29 UP não foram verificados.
2. **Voto 14/2026 da ANA como base da tabela de vidas úteis (ALVOS §5).** O voto é ato preparatório; a norma exigível é a NR ANA 16/2026 (Resolução ANA 302/2026), a ser cadastrada à parte. O voto não menciona UAR; o "por UAR" do texto vem do MCPSB. Rever se a vida útil por perfil de agência (ADR-0038) deve citar a NR 16/2026 e o MCPSB, não o voto.
3. **Série NBR 10004:2024 e domínio de classe de resíduo (GIS-CT §4.5).** A série de 2024 usa classe 1 e classe 2 e abandona I, II A e II B. Se o catálogo de resíduos do GIS prevê o domínio "classe I, II A, II B", rever os valores do domínio (e o período de transição até 31/12/2026). Conferir no texto da ABNT; a fonte é secundária.
4. **Bueiro e a DNIT 030/2004-ES (GIS-CT §4.4).** A referência para a feição `CULVERT` não trata de bueiros. Rever a fonte do catálogo (DNIT 023/2006-ES e 025/2004-ES). A DNIT 023 pode ter edição nova (023/2024-ES, a confirmar no IPR).
5. **Perfil Saneago e revisão da IN (GIS-CT §4, §9.2).** O projeto citava a revisão 03; a publicada é a 04 (11/03/2024). Rever o conteúdo do perfil de importação, que dependia da leitura da IN, e registrar a revisão adotada.
6. **"Saneamento é cadastro temático" (19c §2.2, Portaria MDR 3.242/2022).** A portaria não menciona saneamento nominalmente; a inclusão decorre do art. 1º, §2º. A recomendação de guardar a inscrição imobiliária/ID da parcela do CTM permanece, mas sem apoio textual direto.
7. **"O (técnico)" para OGC Simple Features/ISO 19125 (19c §2.4).** A obrigatoriedade é decisão de projeto, não obrigação normativa; a legenda do quadro reserva "O" a obrigações legais ou contratuais. Rever a classificação.
8. **MUDDI e intercâmbio de dados subterrâneos (19c §2.4).** A MUDDI Parte 1 é só modelo conceitual; não há esquema de troca pronto. A frase "referência para intercâmbio de as-built e dados subterrâneos" precisa ser lida como referência conceitual.
9. **Tiles vetoriais na ISO 19177-1 / OGC API – Tiles (19c §2.4).** A norma define a API de tiles; o formato de codificação (MVT, por exemplo) deve ser verificado à parte. Rever a premissa "tiles vetoriais" antes de fixar o formato em ADR.

Observação sem edição de decisão: a evidência da AGENERSA (diferença de 192 km entre bases) vem de um documento não localizado e provavelmente não normativo (19c §2.3); o argumento de que "a exigência costuma estar no contrato de concessão" não depende dele, mas a evidência deve ser tratada como não verificada.

## Lote 2

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

## Lote 3

Nenhuma decisão foi alterada. Os pontos abaixo têm premissa afetada pela correção e exigem decisão do dono do produto.

1. **PNQS como `EXPORT_PROFILE`** (CQ §2; ADR-0041 item 2; 21b §3.7, decisão 2 e L-17; ED-82 no plano). O regulamento do PNQS não define perfil de exportação; a classe adequada seria `VOLUNTARY`, não certificável. A tarefa L-17/ED-82 ("Perfis de exportação para ISO 37120 e PNQS") perde a base para o PNQS.
2. **Débito em conta e Pix Automático** (PT `pay.pix_automatic`; ADR-0028 §f; 20f LEG-38 e seção 6). Para convênios de serviços públicos (caso do saneamento), a adequação de contratos e autorizações foi prorrogada para 01/01/2027 (Res. CMN 5.278/2025); a premissa "só Pix Automático desde 13/10/2025" precisa ser lida com essa ressalva. O enquadramento exato de água e esgoto em "serviços públicos" exige análise jurídica (não confirmado).
3. **Data de desligamento do FEBRABAN 150** (ADR-0028 §f). A Res. CMN 5.299/2026 não institui o regime operacional (vem da Res. BCB 565/2026, a partir de 01/07/2027); a Res. CMN 4.790/2020 segue em vigor até 30/06/2027. Ver também o conflito registrado em "Não aplicadas" sobre a data 01/07/2027.
4. **Requisito eliminatório de SOC 1 e SOC 3** (ADR-0011, requisito 9 e alternativa "VPS"). Não são certificações, e sim relatórios de asseguração; o SOC 1 cobre controles de relatório financeiro e o SOC 3 é da família SOC 2. Confirmar com o órgão contratante o que a exigência abrange (editais costumam pedir SOC 2 Tipo 2).
5. **Piso de guarda de 10 anos** (PT `ret.billing_after_closure_years`; ADR-0015). O REsp 1.117.903/RS (Temas 251 a 254) trata da pretensão de cobrança pelo prestador; a repetição de indébito está na Súmula 412 (que remete ao Código Civil) e no Tema 932. A pendência do relatório 14 deve separar as duas pretensões.
6. **Corte de água e classes protegidas no DF** (08, quadro de classes protegidas e seção 5.2 ADASA). A Lei Distrital 5.990/2017 (12 meses de carência) foi declarada inconstitucional; a Res. ADASA 14/2011, art. 122, ainda a cita. Conferir a redação vigente antes de parametrizar o corte.
7. **Comprovação da verificação inicial de medidores** (21b MT-09). A Portaria 657/2025 está suspensa (713/2025) e nunca produziu efeito; a 293/2021 não alcança medidores de água. Não construir funcionalidade que dependa da declaração de conformidade em substituição à verificação inicial sem nova norma.
8. **Canal da notificação prévia à negativação** (10 NEG-05 e quadro; 20f LEG-31; ED-30). O Tema 1.315 trata do art. 43 §2º do CDC e valida a notificação eletrônica com prova de envio e entrega efetiva; a página oficial não menciona a Súmula 359 nem a negativação. Ler o inteiro teor antes de fixar o canal.
9. **Retenção de registro de incidente por ente público** (10, quadro comparativo). A remissão à tabela de temporalidade vem do art. 10 §2º da Res. CD/ANPD 15/2024; não existe "Res. CONARQ 15" sobre o tema. Se a intenção era citar uma tabela do CONARQ, indicar a norma específica.
10. **Limites de fluoreto e radioatividade** (09 QA-12 e QA-13). As faixas do Anexo XXI reproduzem a referência de 1975 (o Guia 2024 reconhece a necessidade de atualização); a PR CNEN 3.01/012:2020 está revogada, e a referência técnica passa a ser o Anexo L da NN 3.01, embora a Portaria 888 ainda remeta à PR.
11. **Modelo de classes de resíduo** (12 RES-32 e D-MTR-6). Se a série NBR 10004:2024 usa só Classe 1 e Classe 2, o modelo `waste_class` precisa prever as duas edições por jurisdição (confirmação ainda em fonte secundária).

## Lote 4

Em nenhum caso a decisão, tarefa, prazo ou parâmetro foi alterado. Cada item abaixo tem a nota "(verificado em 22/10/2026: ...)" no texto.

1. **Fatores Q, P e U (CP 03/2026 da ANA)**: a GES-30 ("simulador de fatores Q, P e U"), a tabela de fatos de mercado (linha 36), a GES-01 (linha 107) e a GES-30 (linha 575), todas em 22d, apoiam-se numa minuta que, nas fontes lidas, não menciona esses fatores (discute o fator X, custo de capital, remuneração de investimentos e receitas acessórias). Rever o enunciado e a justificativa da GES-30 e a frase da GES-01. A síntese `lacunas/22-INCORPORACAO-NO-PLANO.md` também a repete (fora do lote).
2. **Resiliência climática na CP 04/2026**: a GES-07 afirmava que era o "tema mais citado"; só há o item 9-032 (tema novo, início ao fim do biênio). A prioridade da GES-07 não pode se escorar nessa frase.
3. **Prazo de adequação do débito em conta (convênios de serviços públicos)**: o prazo vigente é 01/01/2027 (Res. CMN 5.278/2025 e Res. BCB 544/2025), e não 01/01/2026; o enquadramento da cobrança de água e esgoto como "convênio de prestação de serviço público" exige análise jurídica. Afeta GES-15 (22d), PLT-27 (22c) e a decisão D2 do 18e (FEBRABAN 150 x Pix Automático).
4. **Res. CMN 5.299/2026 não institui regime**: é norma de diretrizes; o regime operacional vem da Res. BCB 565/2026 (a partir de 01/07/2027). A D2 (18e) e a linha L4 do 18 referem-se a ela como "fim do regime anterior".
5. **Res. BCB 505/2025 não cria o Pix Automático** (altera a Res. BCB 51/2020, contas de pagamento pré-pagas); a ligação com a Res. CMN 5.251/2025 é do projeto e depende do lote do CMN.
6. **Voto CMN 92/2025**: o PDF não foi lido pelo verificador; a regra deve ser citada pela Res. CMN 5.278/2025 (22c, F12 e linha 560; 22d, fontes da GES-15).
7. **GES-07 (contingência hídrica, 22d)**: a base ADASA 20/2016 trata de racionamento e está revogada; a isenção de unidades essenciais é da Res. ADASA 17/2016, cuja situação não foi verificada. O Regulamento ERSAR 1160/2026 é o regulamento tarifário geral e não teve dispositivo de contingência confirmado. A lacuna R-23/GES-07 deve ser reapontada.
8. **Protesto de CDA (ADI 5135)**: a aplicação a créditos de água e esgoto depende de o prestador poder inscrevê-los em dívida ativa; confirmar com o jurídico antes de virar regra do produto (22d, GES-18).
9. **IN BCB 746/2026 (GES-33)**: o efeito é a revogação do teto fixo de R$ 500, sem novo valor, e alcança também a iniciação sem redirecionamento (Res. BCB 406/2024).
10. **e-Sfinge**: a remessa dos módulos Registros Contábeis e Tributário é mensal desde 01/01/2022, e não bimestral (22d, tabela de fatos de mercado, linha 54); o texto original da IN TC-28/2021 não foi aberto.
11. **CVM Res. 244/2026**: não havia obrigação em vigor tornada voluntária; a resolução retira a obrigatoriedade prevista da Res. CVM 193/2023 (22d, tabela ESG e GES-21).
12. **Assinatura eletrônica (PLT-07, 22c)**: a IN ITI 33/2025 e o DOC-ICP-15.03 não tratam de assinatura em nuvem nem de carimbo do tempo; o regime dos PSC está no DOC-ICP-17 e no DOC-ICP-17.01. A expressão "AD-RB ou superior" deve ser conferida contra a tabela de políticas.
13. **Acessibilidade (PLT-31)**: a NBR 17225:2025 não é tradução da WCAG 2.2; a afirmação sobre alvo mínimo em pixels deve ser conferida no texto da NBR (o trecho consultado cita 44 px CSS).
14. **Hospedagem (ADR-0011, ED-065, TR-073/074, linha de hospedagem do plano e H3 do 18)**: SOC 1/ISAE 3402 e SOC 3 são relatórios de asseguração, e não certificações; a exigência do edital de Videira deve ser confirmada com o órgão (o SOC 1 trata de controles financeiros; para segurança de nuvem costuma-se esperar SOC 2 Tipo 2).
15. **ED-82 (PNQS)**: o regulamento do PNQS não define perfil de exportação de evidências; o item "Perfis de exportação ... PNQS" não tem formato normativo a seguir.
16. **NFAg**: a data de 01/12/2026 está confirmada em fonte oficial; permanece pendente o manual definitivo (o portal da SVRS ainda o apresenta como minuta). Optantes do Simples Nacional têm data própria (01/01/2027).
17. **GIS-17 (OGC API – Features, partes 1 a 3)**: a Parte 5 (Schemas) foi aprovada em 30/04/2026 e a Parte 4 segue em minuta; a decisão de escopo é do dono.
18. **Trilha GRS (NBR 10004)**: a série 2024 mudou as classes e há transição até 31/12/2026; a conferência deve ser feita no documento da ABNT.

## Lote 5

Nenhuma decisão, tarefa, prazo ou parâmetro foi alterado. Os pontos abaixo mudaram uma premissa.

1. **Prazo de adequação ao Pix Automático e novo regime geral.** São duas regras (01/01/2027 e 01/07/2027). Se a cobrança de água e esgoto é "convênio de prestação de serviço público" é questão jurídica em aberto; a urgência da P4.5 e a existência da P3.24 (FEBRABAN 150 legado) dependem dessa resposta e de os bancos do piloto aceitarem o legado só em caso intrabanco. A P3.24 e a ED-27 não foram alteradas.
2. **MP 1.393/2026 (Desenrola Brasil 3.0).** A premissa "sem efeito no produto" vale só para o art. 57 do CDC. A adesão à compra de carteiras pela União é decisão do prestador ou do ente e exige parecer (Q11); o efeito é possível em cobrança, negativação e parcelamento (P3.1, P4.1). O cronograma divulgado é chamamento em novembro de 2026, propostas em dezembro, homologação em janeiro de 2027 e contratos em fevereiro de 2027.
3. **PL 2.338/2023.** A justificativa de risco regulatório do escore de crédito (22d, GES-24, L479) apoia-se em "acesso a serviço essencial" (art. 14, IV), e não em crédito; reavaliar se o texto do GES sobre "perfilamento financeiro" continua precisando de apoio no PL.
4. **ARIS.** A RN ARIS 41/2024 revogou a Res. 04/2012 e trata da Ouvidoria; o 20f (LEG-25 e LEG-27) cita ARIS arts. 23 e 24 (RN 19/2019) para prazos de resposta e ouvidoria. Conferir se a RN 41/2024 alterou esses prazos antes de pré-carregar o perfil SC.
5. **Lei PR 14.471/2004.** Vale apenas para a Sanepar; o perfil AGEPAR (Res. 3/2020) trata a Sanepar, mas a regra de corte e religação para prestadores de água do PR é a da Lei 22.130/2024. O 08 §5.5 não distingue os prestadores; se um SAAE do Paraná for tenant, usar a lei de 2024.
6. **Assinatura em sistema próprio do município (Decreto 29/2023 de São Ludgero).** A assinatura gerada pelo sistema do município só vale como avançada após avaliação e ratificação do chefe do Executivo (§ 1º do art. 5º); isso afeta o requisito de assinatura avançada do edital (Edital 8.2.3) quando a assinatura vier do Ziip SAN.
7. **WCAG.** O RNF-60 e o ADR-0022 seguem em 2.1 AA, enquanto o PLT-31 propõe 2.2 AA. A WCAG 2.2 é compatível e é a base da ISO/IEC 40500:2025; a decisão é do dono do produto.
8. **Fatores Q, P e U (GES-30).** A justificativa depende de uma menção que não foi encontrada na CP 03/2026; a nota está no 22d e agora no 22-INCORPORACAO.
9. **CONAMA, efluentes.** A data de 02/09/2026 e a 151ª reunião são da página do processo; o comunicado do MMA não distingue as plenárias. Não codificar limites antes da publicação.

