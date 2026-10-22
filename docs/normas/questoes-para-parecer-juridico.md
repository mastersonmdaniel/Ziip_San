# Questões para parecer jurídico

Gerado em 22/10/2026 a partir dos levantamentos de `docs/normas/partes` (em especial `04-mapa-de-eris.md`, `04-pendencias.md` e `02-pendencias.md`) e dos relatórios 22d e 21a. **Este documento não é parecer jurídico nem conselho jurídico.** É o roteiro de perguntas que o produto precisa ver respondidas por advogado, pelo jurídico do cliente ou pelo regulatório da agência antes de transformar qualquer ponto em regra de fábrica.

## Como o produto trata cada ponto enquanto não houver resposta

1. Onde lei federal veda expressamente, o parâmetro fica **travado no piso federal** (ADR-0038, item 3; ADR-0015, item 3).
2. Onde há divergência entre agências ou norma local discutível, o parâmetro é **configurável com aceite** (classe b), com padrão mais protetivo ao usuário, e o perfil da agência entra como sugestão marcada com a fonte.
3. Regra de agência ou lei estadual com validade duvidosa **não é pré-carregada** como piso; fica registrada no catálogo (`docs/normas/catalogo-normas.json`) com a ressalva.

## Questões

### Q1. Corte iniciado às sextas-feiras: agências contra a lei federal

- **Situação:** a ARIS (art. 158), a AGIR (art. 148), a ARESC (art. 85), a ARCE, a AGR-GO e a ARPE permitem o corte até 12 h de sexta-feira (ARPE: 17 h). A Lei 14.015/2020, pelo levantamento, veda iniciar o corte de sexta a domingo, em feriado e na véspera. O levantamento não encontrou pronunciamento da ARIS sobre a adequação, e nenhuma alteração da ARIS (inclusive a RN 55/2026) mexeu no art. 158.
- **Pergunta:** a lei federal prevalece integralmente sobre a regra da agência, e a agência deve ser tratada como regra a ser ajustada ou como ato nulo no ponto? Há risco para o prestador que cumpre a norma da agência?
- **Efeito no produto:** a regra de bloqueio de corte (P3.4; parâmetro de corte do grupo G9) já trava o piso federal; a pergunta confirma que não se oferece a opção da agência.
- **Fontes:** `lei-14015-2020`; `04-mapa-de-eris.md`, seção 3, item 3; `04-pendencias.md`, item 18.

### Q2. Leis estaduais sobre corte depois da ADI 7.725/TO

- **Situação:** a ADI 7.725/TO (julgamento finalizado em 23/05/2025) declarou inconstitucional lei estadual sobre suspensão de água e energia por inadimplência, por competência da União e dos municípios. As leis de SC (Lei 11.959/2001, na redação da Lei 17.780/2019, que veda corte entre 8 h de sexta e 8 h de segunda e antes de feriados; Lei 16.673/2015, art. 21, parágrafo único), do PR (Lei 22.130/2024, arts. 154 a 159) e do DF (Leis 4.632/2011 e 5.990/2017) tratam do tema. O levantamento não achou ADI específica contra a lei de SC nem contra a do PR.
- **Pergunta:** essas leis estaduais obrigam prestadores municipais ou regulados por agência municipal ou intermunicipal? Valem como piso mais protetivo ou devem ser desconsideradas?
- **Efeito no produto:** define se o perfil de SC e do PR inclui a regra estadual como piso. Enquanto não houver resposta, não é piso.
- **Fontes:** `sc-lei-11959-2001`; `04-mapa-de-eris.md`, seção 3, item 3, e anexo B, seção 4; `04-pendencias.md`, itens 6 e 18 da seção ARIS.

### Q3. Lei Distrital 5.990/2017 e o art. 122 da ADASA

- **Situação:** o SINJ-DF anota a lei como declarada inconstitucional (TJDFT em 2019; RE 1.314.679 no STF, 20/03/2024). O art. 122 da resolução da ADASA mantém a proteção de 12 meses para saúde, educação, internação e baixa renda, que dependia da lei.
- **Pergunta:** o art. 122 subsiste como ato próprio da agência? Qual o efeito sobre prestadores no DF?
- **Efeito no produto:** a proteção fica como parâmetro a confirmar, não como regra de fábrica.
- **Fontes:** `df-lei-5990-2017`; `04-pendencias.md`, seção ADASA, item 4.

### Q4. Tarifa social: retirada do benefício após dois cortes (AGIR)

- **Situação:** a RN 001/2013 da AGIR (art. 127, §7º) retira a categoria social após mais de dois cortes no ano. O levantamento aponta que isso contraria a Lei 14.898/2024.
- **Pergunta:** confirmar que a regra não pode ser carregada no perfil e que o prestador não pode aplicá-la. Em outras agências com regra análoga (a Res. 002/2010 aplicada na Paraíba, arts. 127 e 138, veda isenções e tarifas reduzidas), vale o mesmo?
- **Efeito no produto:** o perfil recusa a regra; a tarifa social segue as travas federais.
- **Fontes:** `lei-14898-2024`; `04-mapa-de-eris.md`, seção 3, item 7.

### Q5. Tarifa social: critérios locais, benefício maior e prazo de adequação

- **Situação:** o piso federal é desconto de 50% até 15 m³. AL, TO, RN, AP e SE mantêm critérios anteriores; a ADASA mantém limite de 30 m³ (mais favorável; a Lei 14.898, art. 6º, §2º, admite benefício maior); a ARES-PCJ acrescenta 25% de 15 a 20 m³. O prazo de adequação (24 meses) termina em 11/12/2026 pela vigência contada pela ANA, ou em 10/12/2026 para agências que contam de 10/12/2024.
- **Pergunta:** os critérios locais anteriores continuam válidos até a adequação? Qual data prevalece para o prazo?
- **Efeito no produto:** o parâmetro de data legal fica em 11/12/2026, com limite interno prudencial de 10/12/2026; critérios locais ficam como configuráveis dentro do que a lei federal admite.
- **Fontes:** `04-mapa-de-eris.md`, seção 3, itens 2 e 7.

### Q6. Início da verificação da NR 11 da ANA

- **Situação:** o texto da NR 11 (art. 74, §3º) fixa 20/05/2027; uma notícia da ANA diz 27/05/2027. Prevalece o texto do ato, mas o dado não foi conferido no DOU.
- **Pergunta:** confirmar a data no DOU e se há ato de retificação.
- **Efeito no produto:** data do parâmetro de verificação da NR 11.
- **Fontes:** `04-mapa-de-eris.md`, seção 3, item 2; `04-pendencias.md`, item 3.

### Q7. Juros e multa por agência

- **Situação:** juros de até 0,033% ao dia (ARIS, AGIR, ARESC e várias do Norte e Nordeste), 1% ao mês (AGERGS, AGENERSA, AGR-GO, ARSESP desde a Del. 1.791/2026), 0,5% ao mês (SAMAE de Caxias do Sul) ou remetidos ao município (ARES-PCJ). Multa em geral de 2%, com casos de 3%, 6% e 9% por faixa de atraso (AMAE Marília) e de 0,33% ao dia até 10% (ARESPCAB).
- **Pergunta:** multas acima de 2% são compatíveis com o limite do art. 52, §1º, do Código de Defesa do Consumidor, quando a relação é de consumo? Juros de 1% ao mês pro rata die e atualização por IPCA, INPC ou Selic são equivalentes para fins de teto?
- **Efeito no produto:** o parâmetro `fee.interest_mode` (classe b, padrão por dia com teto da agência) e o teto da multa; define se multas acima de 2% são oferecidas.
- **Fontes:** `04-mapa-de-eris.md`, seção 3, item 4, e seção 4.

### Q8. Qual norma vale em cada município

- **Situação:** a CASAN responde a quatro agências (ARESC, ARIS, AGIR e CISAM-SUL) conforme o município; a Corsan aplica a RN 66/2022 da AGERGS em 238 municípios e o regulamento da AGESAN em 81, com regras diferentes de média, religação indevida e vedação de corte; Sooretama (ES) consta em duas agências e Linhares passou da ARIES para a ARSP.
- **Pergunta:** o critério jurídico que determina a agência competente em cada município é o instrumento de delegação (convênio, consórcio, contrato)? Que documento o tenant deve apresentar na implantação para comprovar a agência e a data de início?
- **Efeito no produto:** a chave do perfil regulatório é agência e município, com data de início; o onboarding exige o documento.
- **Fontes:** `04-mapa-de-eris.md`, seção 3, item 5.

### Q9. Normas conflitantes dentro do mesmo regulado (Timon) e no regulamento da AGEMS

- **Situação:** em Timon (MA), o Decreto Municipal 0114/2014 e a Res. AGERT 02/2016 trazem juros (1% ao mês contra 0,033% ao dia) e prazos (72 h contra 48 h) conflitantes; a prevalência não foi apurada. Na AGEMS (MS), a Portaria 320/2025 tem conflito interno entre o art. 154, parágrafo único (urgência em 6 h), e o art. 155 (24 h).
- **Pergunta:** qual prevalece em cada caso? No caso da AGEMS, só a agência pode interpretar: formalizar a consulta.
- **Efeito no produto:** os dois valores ficam como opções no perfil, sem padrão de fábrica.
- **Fontes:** `04-pendencias.md`, seção AGERT e seção AGEMS.

### Q10. Débito pessoal e transferência de titularidade

- **Situação:** o dono do produto decidiu em 15/10/2026 que o débito é pessoal e não acompanha o imóvel, e que a transferência de titularidade sempre ocorre (`unit.transfer_with_previous_debt`, classe a, travado). O levantamento 02 não localizou súmula ou tema repetitivo do STJ sobre corte por débito pretérito.
- **Pergunta:** a decisão é compatível com a jurisprudência atual do STJ e com as normas das agências que tratam de débito do imóvel? Há agência cuja norma trate o débito como obrigação do imóvel?
- **Efeito no produto:** confirma ou ajusta a trava.
- **Fontes:** `02-pendencias.md` (itens não localizados); ADR-0038.

### Q11. Medida provisória do Desenrola 3.0 e dívidas com prestadores de serviços públicos

- **Situação (confirmada em 22/10/2026, normas.leg.br e Senado):** a MP 1.393/2026, de 25/09/2026, está em vigor e depende de conversão pelo Congresso. Institui o Desenrola Brasil 3.0 (até 30/06/2028) e, nos arts. 31-B e 31-C que acrescenta à Lei 14.690/2023, autoriza a União a adquirir carteiras de dívidas de pessoas físicas, inclusive dívidas com prestadores de serviços públicos e de utilidade pública passíveis de inscrição em cadastro de inadimplentes, com deságio mínimo de 90%, inadimplência entre 720 e 1.645 dias contados de 25/09/2026, valor original abaixo de R$ 10.000 e oferta da totalidade dos créditos elegíveis de cada devedor. Cronograma divulgado: chamamento em novembro/2026, propostas em dezembro, homologação em janeiro/2027 e contratos em fevereiro/2027.
- **Pergunta:** o prestador (ou o município titular) pode ceder à União créditos de contas de água e esgoto? Que efeito tem a cessão com deságio de 90% sobre a receita regulatória, a tarifa, a Lei de Responsabilidade Fiscal para ente público, a negativação e a cobrança em curso? O desconto repassado ao usuário vincula o prestador?
- **Efeito no produto:** cobrança e parcelamento (P3.1, P4.1).
- **Fontes:** `docs/normas/partes/05-verificadas-A.json` (MP 1.393/2026).

### Q12. Reforma tributária: tratamento do saneamento

- **Situação:** o enquadramento do saneamento fora da redução de 60% do IBS e da CBS vem de doutrina, não de texto de lei lido; a obrigatoriedade da NFAg-e em 01/12/2026 não foi lida no Diário Oficial; a NT 007/2026 da NFS-e limita a tolerância de R$ 0,01 de arredondamento a PIS e Cofins.
- **Pergunta (tributarista):** qual o tratamento de IBS e CBS para água e esgoto? Quais regras de arredondamento valem para IBS e CBS na NFAg-e? Confirmar a data de obrigatoriedade.
- **Efeito no produto:** P2.4, P2.5, ED-22 e o motor fiscal (ADR-0003).
- **Fontes:** relatório 22d (GES-13); `05-verificadas-B.json`.

### Q13. LGPD e normas de gestão

- **Situação:** o módulo de certificações pode ser pedido a reter registros por mais tempo do que a LGPD admite para dado pessoal; o auditor externo pode precisar de acesso a documento com dado pessoal; a investigação de acidente (ISO 45001) trata dado de saúde; a categoria de usuários essenciais e dependentes por saúde (CAD-80) trata dado sensível.
- **Pergunta:** qual a base legal e o prazo de guarda aceitáveis em cada caso? O acesso do auditor externo exige contrato de operador ou termo específico?
- **Efeito no produto:** parâmetros `cert.record_retention_years`, `cert.external_auditor_access` e `cert.evidence_pack_personal_data`; a lei vence (ADR-0041, item 9).
- **Fontes:** `docs/levantamento/certificacoes/21a-iso-gestao-requisitos-de-software.md`, seção 7.

### Q14. Débito automático: enquadramento do saneamento no prazo de 01/01/2027

- **Situação:** a Res. CMN 5.278/2025 manteve até 01/01/2027 o prazo de adequação ao Pix Automático para contratos e autorizações de débito de tributos, de convênios de prestação de serviços públicos e de planos de saúde, quando o destinatário final é pessoa jurídica não autorizada pelo Banco Central. A Res. BCB 565/2026 traz o novo regime geral do débito automático em vigor em 01/07/2027. São duas regras com datas diferentes (confirmado em 22/10/2026).
- **Pergunta:** os convênios de débito em conta das companhias e autarquias de água e esgoto são "convênios de prestação de serviços públicos" para fins do prazo de 01/01/2027? Se o prestador for ente público ou concessionária, muda algo?
- **Efeito no produto:** a tarefa P3.24 (FEBRABAN 150 legado) e o ED-27 perdem o objeto se o prazo valer; o parâmetro de débito automático precisa da data do tenant.
- **Fontes:** Res. CMN 5.278/2025; Res. BCB 565/2026; `docs/levantamento/11-regulatorio-cobranca-bancario-fiscal.md`.

### Q15. NR-1 do MTE e riscos psicossociais

- **Situação:** o capítulo de riscos psicossociais da NR-1 vale desde 26/05/2026; o STF suspendeu as multas até cerca de 24/12/2026 (ADPF 1316).
- **Pergunta:** que obrigações o prestador tem enquanto durar a suspensão, e o que o módulo de certificações deve registrar?
- **Efeito no produto:** pacote ISO 45001 (ED-71) e catálogo `REGULATORY` do MTE.
- **Fontes:** `docs/normas/partes/02-pendencias.md`.

## Itens que são de conferência regulatória, não jurídica

Os levantamentos também deixaram dúvidas que dependem de ler o ato na agência, e não de parecer: normas de agência que provavelmente foram superadas sem revogação expressa (ver itens 'a confirmar' em `pendencias-consolidadas.md`), textos digitalizados sem camada de texto (ARESC e outros) e atos citados só por ementa. Esses itens são tratados pela reverificação de 22/10/2026 e, quando não resolvidos, pelo regulatório do cliente.
