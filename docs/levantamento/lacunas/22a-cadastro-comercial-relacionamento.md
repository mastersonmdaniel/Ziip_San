# 22a — Lacunas e inovações na frente CADASTRO, COMERCIAL E RELACIONAMENTO

Data de elaboração: 22/10/2026. Escopo: cadastro de cliente e de unidade consumidora, ligação, hidrômetro e leitura, estrutura tarifária e faturamento, arrecadação e cobrança, tarifa social, atendimento, portal e aplicativo, comunicação com o cliente, ligação nova e grandes consumidores. Finalidade: aprimorar o que o produto já prevê, sem reabrir decisão aceita. Não é parecer jurídico; toda afirmação normativa marcada como fonte secundária ou [INFERÊNCIA] exige conferência no texto oficial e revisão do jurídico do tenant antes de virar regra.

## 0 Leitura rápida

1. O plano é denso nesta frente: unidade consumidora como raiz e OS para toda alteração (ADR-0039), parâmetros com bifurcação (ADR-0038), tarifa social com piso travado, corte com vedações e cobrança em trilha própria. Os achados abaixo são complementos, não correções de rumo.
2. Foram identificados 40 achados: 16 ausentes do plano (A), 17 presentes mas rasos (B) e 7 inovações (C), numerados de CAD-65 a CAD-104. A numeração continua a do levantamento 20a, que já usa CAD-01 a CAD-64 para regras do GSAN.
3. Dez achados têm prioridade alta: CAD-65, 68, 69, 80, 86, 87, 91, 96, 98 e 99.
4. As lacunas de maior efeito operacional são a unidade sem usuário vigente (CAD-68), a fonte alternativa de água (CAD-69), a ligação obrigatória com cobrança por disponibilidade (CAD-87) e o fluxo encadeado da ligação nova (CAD-86).
5. Na tarifa social, o desenho da identificação automática e a regra de quem pode receber o CadÚnico por natureza jurídica (CAD-91) não aparecem em tarefa; a importação está na ED-57, da onda C.
6. Há dependência de sequência: as mensagens legais da fatura (CAD-96) são exigidas pela P1.5 e pela cobrança por disponibilidade, mas o motor está na ED-79, da onda B.
7. Duas lacunas legais já apontadas em levantamento e ainda sem tarefa: superendividamento (CAD-75, LEG-35 do 20f) e atendimento prioritário e formatos acessíveis no cadastro (CAD-81).
8. Inovações de melhor relação valor e custo: qualidade cadastral contínua (CAD-98), alerta preventivo de consumo anormal (CAD-99) e régua de cobrança com promessa de pagamento (CAD-100).
9. Itens que alteram decisão registrada (login gov.br em CAD-101 e segunda via sem conta em CAD-97 tocam o ADR-0022) são apenas recomendados e dependem de cartão do dono do produto.
10. Dos 40 achados, 38 criam ou ampliam parâmetro do tenant (ADR-0038); apenas CAD-78 e CAD-83 são regras de produto.

## 1 Método e fontes

### 1.1 Método

- **Leitura do que está decidido.** Plano `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`: leitura integral das seções 2.2 a 2.4 e das tabelas das fases P0 a P3, da trilha de editais (ondas A, B e C) e da fase LC; busca por palavra-chave e por ID no restante. ADR-0002, 0015, 0038, 0039 e 0040 lidos; ADR-0041 e demais pelo índice e por busca. `PARAMETROS-DO-TENANT.md` lido por inteiro. `ARQUITETURA-ALVO.md` apenas pelo índice. Levantamentos: 20a (seções 1 a 7), 20d (seção 3), 20f (seções 1, 2.1, 2.3, 2.11 a 2.13), 20g (inteiro), 07 (seções 3 a 6), 18d (seção 3), 18f (seções 2.3, 2.9 e entregas), 08 e 13 (linhas de lacuna); 20b, 20c, 20e, 21a, 21b, 18e e 18g apenas por busca de palavra-chave.
- **Antes de afirmar ausência.** Cada candidato foi procurado em português e em inglês, por sinônimos e por IDs, no plano, em `docs/arquitetura` e em `docs/levantamento`. Só foi classificado como A o que não aparece no plano nem em levantamento; o que aparece em levantamento mas não tem tarefa ou aceite no plano foi classificado como B.
- **Classes.** (A) ausente do plano. (B) presente, mas raso: falta cadastro, regra, campo ou aceite. (C) inovação sobre o que o produto já prevê.
- **IDs.** CAD-65 em diante, para não colidir com CAD-01 a CAD-64 do 20a. Se o dono preferir reiniciar a numeração, basta prefixar com `22a-`.
- **Prioridade.** Alta: obrigação legal ou perda de receita ou risco operacional frequente. Média: ganho claro, contornável por processo manual. Baixa: depende de contexto do tenant.
- **Esforço relativo.** P, M e G na escala do plano (§4): P até 2 dias, M até 1 semana, G mais de 1 semana, por tarefa. É indicação de tamanho, não de prazo.
- **Parâmetro do tenant.** Indica se o achado cria ou amplia chave do catálogo, com a classe (a, b, c, d) do ADR-0015. Chaves propostas aqui são sugestões de nome, a validar no catálogo.
- **Pesquisa na internet.** Três rodadas, com fontes oficiais priorizadas: (1) normas e serviços públicos; (2) concorrentes e utilities; (3) verificação jurídica pontual. As ferramentas devolveram principalmente trechos de página; PDFs de normas foram lidos pelo manual da ANA. O que veio de fabricante, imprensa ou fonte secundária está marcado. O que não foi confirmado está marcado [INFERÊNCIA].

### 1.2 Fontes citadas por código

Normas e serviços públicos

- N11: Resolução ANA 230/2024 (NR 11) — https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/230
- N11M: Manual orientativo da NR 11 — https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/manual-nr11-final.pdf
- N9: Resolução ANA 211/2024 (NR 9, indicadores) — https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2024/211
- N13: Resolução ANA 271/2025 (NR 13) — https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias/2025/271
- ANA-FAQ: perguntas frequentes da tarifa social — https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/perguntas-frequentes
- ANA-CAD: orientações de acesso aos dados do CadÚnico — https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/acesso-e-gestao-dos-dados-do-cadunico-e-bpc/orientacoes-para-acesso-aos-dados-do-cadunico-1
- MDS57: Informe do Cadastro Único nº 57, de 29 de outubro de 2024 (Tarifa Social de Água e Esgoto: procedimentos de cessão de dados para operacionalização da tarifa) — https://mds.gov.br/webarquivos/MDS/2_Acoes_e_Programas/Cadastro_Unico/Informes/2024/Informe_Cadastro_Unico_N_57.pdf
- L11445: Lei 11.445/2007 compilada — https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2007/lei/l11445compilado.htm
- L14898: Lei 14.898/2024 — https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2024/lei/l14898.htm
- L13460: Lei 13.460/2017 — https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2017/lei/l13460.htm
- L14534: Lei 14.534/2023 (CPF) — https://www2.camara.leg.br/legin/fed/lei/2023/lei-14534-11-janeiro-2023-793687-publicacaooriginal-166858-pl.html
- L14181: Lei 14.181/2021 — https://www.in.gov.br/en/web/dou/-/lei-n-14.181-de-1-de-julho-de-2021-329476499
- D11567: Decreto 11.567/2023 (mínimo existencial) — https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/decreto/d11567.htm
- STF-ME: notícia do STF sobre a revisão anual do mínimo existencial — https://noticias.stf.jus.br/postsnoticias/governo-federal-deve-revisar-anualmente-valor-do-minimo-existencial-em-negociacoes-de-superendividamento/
- L10048: Lei 10.048/2000 — https://www.planalto.gov.br/ccivil_03/leis/l10048.htm
- LBI: Lei 13.146/2015 — https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13146.htm
- D5296: Decreto 5.296/2004 — https://www.planalto.gov.br/ccivil_03/_ato2004-2006/2004/decreto/d5296.htm
- LAI: Lei 12.527/2011 — https://www2.camara.leg.br/legin/fed/lei/2011/lei-12527-18-novembro-2011-611802-normaatualizada-pl.pdf
- CGU: adesão ao Fala.BR por Estados e Municípios — https://www.gov.br/cgu/pt-br/governo-aberto/noticias/2020/4/cgu-oferece-sistema-eletronico-de-acesso-a-informacao-a-estados-e-municipios
- L6766: Lei 6.766/1979 compilada — https://www.planalto.gov.br/ccivil_03/leis/l6766compilado.htm
- REN1000: Resolução Normativa ANEEL 1.000/2021 (Regras de Prestação do Serviço Público de Distribuição de Energia Elétrica) — https://www.in.gov.br/en/web/dou/-/resolucao-normativa-aneel-n-1.000-de-7-de-dezembro-de-2021-368359651
- D8727: Decreto 8.727/2016 (nome social) — https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2016/decreto/d8727.htm
- D8270: Decreto 8.270/2014 (Sirc) — https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2014/decreto/d8270.htm
- D10046: Decreto 10.046/2019 (Cadastro Base do Cidadão) — https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2019/decreto/d10046.htm
- L14063: Lei 14.063/2020 (assinaturas) — https://www.in.gov.br/en/web/dou/-/lei-n-14.063-de-23-de-setembro-de-2020-279185931
- L14129: Lei 14.129/2021, capítulo I — https://normas.leg.br/?urn=urn:lex:br:federal:lei:2021-03-29;14129!cap1
- SERPRO-CPF: serviço de consulta ao CPF — https://www.gov.br/pt-br/servicos/obter-solucao-de-consulta-de-dados-de-cadastro-de-pessoa-fisica-cpf
- DATAVALID: validação de identidade — https://www.gov.br/pt-br/servicos/obter-solucao-digital-para-validacao-de-identidade-datavalid
- GOVBR-INT: roteiro técnico de integração do login gov.br — https://acesso.gov.br/roteiro-tecnico/iniciarintegracao.html
- GOVBR-NIV: requisitos de nível prata e ouro — https://www.gov.br/governodigital/pt-br/acessibilidade-e-usuario/atendimento-gov.br/duvidas-nos-dados-cadastrais/requisitos-para-acesso-aos-servicos
- CNEFE: IBGE, Cadastro Nacional de Endereços para Fins Estatísticos — https://www.ibge.gov.br/estatisticas/sociais/habitacao/38734-cadastro-nacional-de-enderecos-para-fins-estatisticos.html
- GEOCODEBR: pacote geocodebr (CRAN) — https://cran.r-project.org/web/packages/geocodebr/readme/README.html
- DNE: Correios, guia de endereçamento — https://www.correios.com.br/enviar/precisa-de-ajuda/guia-de-enderecamento/guia-de-enderecamento
- OSM: licença do OpenStreetMap — https://www.openstreetmap.org/copyright
- BCB587: Resolução BCB 587 (cobrança híbrida) — https://www.bcb.gov.br/api/conteudo/app/normativos/exibenormativo?p1=Resolu%C3%A7%C3%A3o%20BCB&p2=587
- BCB-PIX: Fórum Pix (Res. CMN 5.251/2025 e BCB 505/2025, que alteram as Res. CMN 4.790/2020 e BCB 51/2020 sobre autorização e cancelamento de débitos em conta, com remissão ao regime do Pix Automático) — https://www.bcb.gov.br/content/estabilidadefinanceira/pix/Forum_Pix_Plenaria/20251204-Forum_Pix.pdf
- ANPD-PP: guia de tratamento de dados pelo Poder Público — https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-poder-publico-anpd-versao-final.pdf/@@download/file
- CONS-GOV: Consumidor.gov.br, regras de adesão — https://consumidor.gov.br/pages/conteudo/publico/2
- CORSAN: tarifa social automática e busca ativa — https://www.corsan.com.br/corsan-comunica-240-mil-familias-aptas-a-receber-a-tarifa-social-e-intensifica-busca-ativa-para-ampliar-acesso-ao-beneficio/
- SANEPAR-TS: https://www.sanepar.com.br/tarifa-social-de-agua-e-esgoto
- SABESP-TS: https://www.sabesp.com.br/assets/pdf/servicos/para-voce/Comunicado_Sabesp_URAE_1-26.pdf

Sistemas, prestadores e utilities

- GSAN-212: casos de uso da v2.12 do GSAN — https://www.gsan.com.br/doku.php?id=casos_de_uso:pcg:v212
- GSAN-RA: registro de atendimento do GSAN — https://www.gsan.com.br/doku.php?id=ajuda:registro_atendimento
- SANSYS: Sansys Water (Jtech) — https://conteudo.jtech.com.br/sansyswater
- SANSYS-APP: aplicativo Sansys em autarquia — https://www.saepedrabranca.com.br/noticias/aplicativo-facilita-atendimento-para-usuarios-do-sistema-de-agua-e-esgoto-da-pedra-branca
- IPM: módulo de água e esgoto da IPM — https://www.ipm.com.br/gestao-municipal-da-agua-6-vantagens-do-uso-da-tecnologia/
- FIORILLI: SIA, módulo de saneamento — https://fiorilli.com.br/sistemas/sia-sistema-integrado-de-arrecadacao
- SABESP-AV: serviços da agência virtual da Sabesp — https://novaagenciavirtual.sabesp.com.br/personal/services
- SABESP-ALERTA: alerta de consumo 30% acima da média — https://www.agenciasp.sp.gov.br/meio-ambiente/resiliencia-hidrica-clientes-receberao-alerta-no-celular-sempre-que-consumo-de-agua-ficar-30-acima-da-media/
- SABESP-IOT: hidrômetros NB-IoT — https://www.mobiletime.com.br/noticias/12/02/2026/iot-sabesp-vivo/
- SABESP-PIX: Pix Automático e cobrança por WhatsApp — https://convergenciadigital.com.br/mercado/sabesp-incentiva-a-troca-do-debito-automatico-pelo-pix-automatico/
- SABESP-LIBRAS: canais de atendimento, Libras e TDD — https://www.sabesp.com.br/site/toq/sabesp-canais-atendimento.pdf
- SABESP-TAB: tabela de preços e prazos — https://www.sabesp.com.br/site/uploads/file/asabesp_doctos/tabela_precos_prazos_deliberacoes.pdf
- COPASA-IA: https://news.copasa.com.br/ia-no-atendimento-via-whatsapp-e-webchat-da-copasa-proporciona-melhorias-para-a-jornada-do-cliente/
- COPASA-EMP: Manual do Empreendedor — https://empreendedor.copasa.com.br/wp-content/uploads/2025/08/MANUAL_DO_EMPREENDEDOR_V06.pdf
- CAESB-CS: carta de serviços — https://s3.caesb.df.gov.br/www/prod/site1/carta_servico_atual.pdf
- CAESB-CANAIS: canais e login gov.br — https://www.caesb.df.gov.br/canais-de-atendimento-ao-cliente/
- EMBASA: pedido de tarifa social e tarifa para entidade assistencial — https://atendimentovirtual.embasa.ba.gov.br/carta-de-servico-pedido-de-tarifa-social
- SANEPAR-LIG: https://www.sanepar.com.br/nova-ligacao-agua
- CASAN: consulta de viabilidade — https://online.casan.com.br/servico/consulta-viabilidade-agua
- SAMAE-JS: https://www.samaejs.com.br/central-do-usuario/ligacoes-de-agua-esgoto-e-residuos-solidos/
- SAMAE-BLU: https://samae.com.br/tabela-tarifaria
- SEMASA-ITJ: https://www.semasaitajai.com.br/informacoes/procedimentos
- SANEAGO-EFL: efluentes não domésticos — https://www.saneago.com.br/portais/requerimentos/arquivos/instrucoes_lig_efluentes_n_domesticos.pdf
- ARSESP-PROG: programas comerciais para grandes usuários — https://semil.sp.gov.br/urae1/wp-content/uploads/sites/26/2026/05/Deliberacao-Programas-Comerciais.pdf
- FOLHA-SABESP: https://www1.folha.uol.com.br/mercado/2024/11/sabesp-informa-grandes-consumidores-de-agua-que-vai-cortar-descontos-na-tarifa.shtml
- OFWAT-PSR: https://www.ofwat.gov.uk/regulated-companies/vulnerability/priority-services-register-guidance/
- OFWAT-PF: https://www.ofwat.gov.uk/pn-22-22-paying-fair-ofwat-calls-on-water-companies-to-support-customers-struggling-to-pay/
- OFWAT-TE: https://www.ofwat.gov.uk/nonhouseholds/yourwaterbill/hownonhousehold/
- WATERSMART: https://icos.urenio.org/applications/watersmart-software/
- PALOALTO: https://www.paloalto.gov/Departments/Utilities/Water/WaterSmart
- BADGER: https://www.badgermeter.com/products/analytics-software/eyeonwater-consumer-engagement/
- OPOWER: https://www.oracle.com/us/industries/utilities/social-norms-energy-cons-jpe-5225418.pdf
- OPOWER-HER: https://docs.oracle.com/en/industries/energy-water/energy-efficiency/her-configuration-v3/F36108.pdf
- SAP: https://learning.sap.com/courses/discovering-business-processes-in-sap-s-4hana-utilities/executing-billing-and-invoicing
- ORACLE-CCS: https://docs.oracle.com/en/industries/energy-water/customer-cloud-service/22c/ccs-user-guides/F71325.pdf
- JPAL: https://www.povertyactionlab.org/policy-insight/reducing-energy-and-water-use-through-information-and-social-comparisons
- WB-CR: https://openknowledge.worldbank.org/entities/publication/64ffc62a-32c5-5520-b5eb-3d390fe7e080
- MCK: https://www.mckinsey.com/industries/electric-power-and-natural-gas/our-insights/getting-a-grip-on-bad-debt-practical-steps-to-help-utilities-boost-their-resilience
- WB-PREPAID: https://www.worldbank.org/en/news/feature/2014/09/01/assessment-of-eight-african-cities-explores-limitations-and-possibilities-of-prepaid-meter-systems
- ERSAR: https://www.ersar.pt/pt/site-consultas-publicas/Documents/Projeto_regulamento_relacoes_comerciais.pdf

### 1.3 Observações sobre as fontes do projeto

- A Resolução ANA 211/2024 é a NR 9 (indicadores operacionais); as condições gerais (NR 11) são a Resolução ANA 230/2024, como o plano e o 20f já citam. A data de início da verificação pelas entidades reguladoras aparece como 20/05/2027 no texto da norma e como 27/05/2027 em notícia da ANA; o 20f adota 20/05/2027, coerente com o texto.
- O levantamento 20g registra que o Sansys não foi localizado. Há página do fabricante (Jtech) com a descrição do produto (SANSYS, SANSYS-APP); convém acrescentá-lo à tabela de fornecedores do 20g, assim como o Water Prime (IMA) e o GestCom, que apareceram na pesquisa. As alegações de base instalada vêm dos próprios fabricantes.
- O CNPJ alfanumérico já está tratado em código (`DocumentNumber`) e nos levantamentos 07 e 11; não é achado.

## 2 Quadro-resumo dos achados

Tipo: A ausente, B raso, C inovação. Prio.: A alta, M média, B baixa. Esf.: P, M, G. Parâm.: S cria ou amplia parâmetro do tenant; N regra de produto.

| ID | Tipo | Achado | Prio. | Esf. | Parâm. | Encaixe sugerido |
|---|---|---|---|---|---|---|
| CAD-65 | A | Titular falecido e sucessão da conta | A | M | S | P1.3, P1.31, ED-30 |
| CAD-66 | A | Representação legal e terceiro autorizado | M | M | S | P1.3, P1.31, P3.3 |
| CAD-67 | A | Nome social e nome civil | B | P | S | P1.3, P2.17 |
| CAD-68 | A | Unidade sem usuário vigente (desocupada) | A | M | S | P1.3, P1.2, P2.5 |
| CAD-69 | A | Fonte alternativa de água no cadastro e na cobrança de esgoto | A | M | S | P1.3, P2.5, ED-13, ED-24 |
| CAD-70 | A | Esgoto não doméstico e carga poluidora do usuário | M | G | S | nova ED, onda B |
| CAD-71 | A | Fluxo do empreendedor (loteamento, condomínio, transferência de redes) | M | G | S | nova ED, onda B |
| CAD-72 | A | Ligação provisória, hidrante e carro-pipa | B | M | S | ED-13, ED-34 |
| CAD-73 | A | Programas de regularização e renegociação por ato do ente | M | M | S | P4.1 |
| CAD-74 | A | Medida excepcional por área e mecanismo tarifário de contingência | M | M | S | P2.5, ED-22, P3.4 |
| CAD-75 | A | Superendividamento e processo de repactuação | M | M | S | P4.1, ED-30 |
| CAD-76 | A | Canais externos de reclamação (Consumidor.gov.br, Procon, agência) | M | P | S | ED-35 |
| CAD-77 | A | Pedidos de acesso à informação (LAI) | M | P | S | ED-35 |
| CAD-78 | A | Fusão e separação de cadastros duplicados | M | M | N | P1.3, P1.31 |
| CAD-79 | A | Destino da devolução (chave Pix) | M | P | S | P3.29 |
| CAD-80 | A | Usuários essenciais e vulneráveis (institucionais e pessoais) | A | M | S | P1.3, P3.4, ED-35 |
| CAD-81 | B | Perfil de atendimento prioritário e de acessibilidade | M | M | S | P1.3, ED-35, ED-40 |
| CAD-82 | B | Validação cadastral externa (CPF, CNPJ, biometria) | M | M | S | P1.3, P1.2, P1.31 |
| CAD-83 | B | Endereço normalizado e geocodificação do imóvel | M | M | N | P1.3, P2.23 |
| CAD-84 | B | Contatos verificados e preferências por finalidade | M | M | S | P1.3, ED-41, P3.5 |
| CAD-85 | B | Documentos exigidos por serviço e documentos emitidos verificáveis | M | M | S | ED-34, ED-38 |
| CAD-86 | B | Fluxo encadeado da ligação nova | A | G | S | ED-34, ED-35, ED-36 |
| CAD-87 | B | Ligação obrigatória e cobrança por disponibilidade | A | G | S | P2.23, P2.5, P3.22 |
| CAD-88 | B | Categorias opcionais da NR 13 e benefícios com validade | M | M | S | P2.5, P1.5, ED-22 |
| CAD-89 | B | Sazonalidade da unidade | B | P | S | P1.3, P2.5 |
| CAD-90 | B | Cofaturamento (NR 13, arts. 46 a 56) | M | M | S | ED-25, ED-32 |
| CAD-91 | B | Identificação automática da tarifa social e fonte do CadÚnico por natureza jurídica | A | G | S | P1.5, ED-57 |
| CAD-92 | B | Índice de Tarifa Social, lista positiva da ANA e relatório anual | M | M | S | P3.22, ED-48 |
| CAD-93 | B | Contratos especiais de grandes usuários e venda por atacado | M | M | S | ED-22, ED-17 |
| CAD-94 | B | Agendamento pelo usuário | M | M | S | ED-38, ED-36 |
| CAD-95 | B | Situação do abastecimento por endereço (consulta pública) | M | P | S | ED-35, ED-38 |
| CAD-96 | B | Motor de mensagens legais da fatura antes da ED-79 | A | M | S | P2.5, P2.17, P2.24 |
| CAD-97 | B | Segunda via e pagamento sem conta no portal | M | M | S | P3.3 |
| CAD-98 | C | Qualidade cadastral contínua | A | G | S | nova tarefa P3 |
| CAD-99 | C | Alerta preventivo de consumo anormal | A | M | S | P3.5, P2.5, ED-17 |
| CAD-100 | C | Régua de cobrança com promessa de pagamento e priorização | M | G | S | P4.1 |
| CAD-101 | C | Login gov.br como provedor federado do portal | M | M | S | P3.3, ED-38 |
| CAD-102 | C | Fatura que explica a variação e compara com domicílios semelhantes | M | M | S | P2.17, ED-38, ED-43 |
| CAD-103 | C | Cruzamento com o cadastro imobiliário municipal | M | M | S | ED-62, ED-16, ED-24 |
| CAD-104 | C | Planos de pagamento por capacidade (fatura nivelada e acordo por renda) | B | M | S | P4.1 |

## 3 Detalhamento por achado

Formato de cada achado: descrição, importância, o que o plano já tem (com IDs reais), onde encaixaria, prioridade, esforço, parâmetro e fontes.

### 3.1 Classe A: ausentes do plano

#### CAD-65 — Titular falecido e sucessão da conta

- **Descrição.** Fluxo para o caso de o titular da fatura falecer: motivo de fim de vínculo, responsável provisório (herdeiro, inventariante ou ocupante), continuidade do fornecimento, suspensão de acréscimos, aviso de corte e negativação enquanto a sucessão não é regularizada, e nova titularidade por OS.
- **Por que importa.** É caso recorrente em autarquia e gera cobrança e negativação em nome de pessoa falecida, com dano ao prestador. Hoje o plano trata apenas troca de titularidade como regra geral.
- **O que o plano já tem.** ADR-0039 itens 2 e 4 (vínculo por papel e vigência; toda alteração por OS); `unit.transfer_with_previous_debt` (classe a: o débito é pessoal do titular que o contraiu); OS `OWNERSHIP_TRANSFER` do 20d §3.3, com análise; P3.20 (troca de titularidade revoga o vínculo do portal); `neg.excluded_profiles` (ED-30), sem o perfil de titular falecido. Nenhuma ocorrência de óbito, espólio ou sucessão em plano, ADRs ou parâmetros.
- **Onde encaixaria.** P1.3 (catálogo de motivos de fim de vínculo com papel de sistema, já recomendado no 20a CAD-24); P1.31 (novo tipo de serviço `HOLDER_DECEASED`, interno e com análise); ED-30 e P3.4 (bloqueio de negativação e de aviso de corte com o perfil ativo); ED-35 (atendimento). Detecção opcional por situação cadastral do CPF (ver CAD-82) e, para ente público, por solicitação ao Sirc, que a lei restringe a entes públicos.
- **Prioridade e esforço.** Alta, M.
- **Parâmetro.** Sim: `registry.deceased_holder_policy` (classe b). Opções: manter a conta até comunicação; representante do espólio; ocupante por pedido. Padrão protetivo: continuidade do serviço e suspensão de encargos e negativação por prazo definido pelo tenant. Débito do falecido: a herança responde até as forças dela [INFERÊNCIA a confirmar com o jurídico do tenant]. Tratamento de dado de pessoa falecida sob a LGPD tem entendimento não pacificado [INFERÊNCIA]; tratar com a mesma cautela.
- **Fontes.** D8270 (acesso ao Sirc restrito a entes públicos, mediante autorização), SERPRO-CPF (a API devolve a situação cadastral; que o valor "titular falecido" seja exposto é [INFERÊNCIA]).

#### CAD-66 — Representação legal e terceiro autorizado

- **Descrição.** Entidade de representação com tipo, poderes, vigência e documento comprobatório: procurador, tutor ou curador, síndico, administradora de condomínio, inventariante, responsável legal de pessoa jurídica, e terceiro autorizado a tratar da conta sem ser titular.
- **Por que importa.** Hoje o vínculo cliente-unidade cobre quem é titular, proprietário e responsável, mas não quem age em nome do titular. Sem isso, o atendimento improvisa com anotação livre, com risco de fraude e de acesso indevido a dado pessoal.
- **O que o plano já tem.** ADR-0039 item 2 (papéis do catálogo); ED-12 (cadastro ampliado e anexos); P1.20 (pipeline de arquivos); ADR-0039 item 8 (titularidade sempre com análise); P3.3 e P3.20 (portal e vínculo). Nenhuma menção a procuração, tutela, curatela, síndico ou terceiro autorizado fora de requisito de edital.
- **Onde encaixaria.** P1.3 (`V3_6`: `customer_representations`); P1.31 (tipo de serviço `REPRESENTATIVE_SET`, com análise); P3.3 e P3.20 (acesso delegado no portal com escopo limitado e registro no log de acesso a dado pessoal); ED-15 (síndico na regra de condomínio).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `registry.representation_proof` (classe d, documentos aceitos por tipo) e `portal.delegated_access` (classe b, desligado por padrão para operações que alteram faturamento).
- **Fontes.** L14534 e L13460 art. 10-A (CPF único e suficiente; documento com fé pública), OFWAT-PSR (terceiro autorizado como serviço mínimo), SABESP-AV (cadastro de representante).

#### CAD-67 — Nome social e nome civil

- **Descrição.** Campo de nome social no cliente pessoa física, usado no tratamento do usuário (atendimento, portal, mensagens), com o nome civil mantido onde o documento exige (documento fiscal e termos).
- **Por que importa.** Dignidade no atendimento e prevenção de constrangimento. O Decreto 8.727/2016 obriga só a administração federal; para autarquia municipal é boa prática e não obrigação [INFERÊNCIA].
- **O que o plano já tem.** Nada; 07 §5.3 trata só de nome. Nenhuma ocorrência de "nome social" no repositório de documentos.
- **Onde encaixaria.** P1.3 (`customers.social_name`); P2.17 e P2.8 (qual nome sai na conta); P3.3 (saudação do portal); ED-35.
- **Prioridade e esforço.** Baixa, P.
- **Parâmetro.** Sim: `registry.social_name_display` (classe d). Opções: nome social em toda interação exceto documento fiscal (padrão protetivo); somente nome civil.
- **Fontes.** D8727.

#### CAD-68 — Unidade sem usuário vigente (desocupada)

- **Descrição.** Estado explícito da unidade consumidora sem usuário titular vigente (`VACANT`), com política de quem recebe a fatura, o que se cobra, como se estima o consumo e quando se aciona cobrança administrativa ou vistoria.
- **Por que importa.** A regra recomendada em 07 §4.3 e §6.1 ("exatamente um usuário vigente por imóvel") conflita com a realidade: o usuário sai sem pedir a transferência, o imóvel é desocupado com hidrômetro instalado, e a carga do legado (P1.2) traz unidades sem titular. Sem estado próprio, o sistema rejeita a carga ou cria titular fictício, prática que o 20a (§4.5) rejeita ao descartar o cliente fictício do GSAN.
- **O que o plano já tem.** `bill.inactive_economy_rule` (`NO_CHARGE` ou `BASIC_FEE`, classe b) e `bill.cut_connection_charge`, que tratam economia inativa por suspensão; ADR-0039 itens 2 e 3 (`PROVISIONAL`, que é cadastro incompleto e não desocupação); ED-22 (conta final e situações especiais). Nenhuma regra para unidade ativa sem usuário.
- **Onde encaixaria.** P1.3 (exceção controlada à regra do usuário vigente, com proprietário como responsável provisório); P1.2 (carga de unidades sem titular com marca `VACANT`); P2.5 (estimativa e cobrança); ED-13 e ED-24 (vistoria e fiscalização de unidade vaga com consumo).
- **Prioridade e esforço.** Alta, M.
- **Parâmetro.** Sim: `registry.vacant_unit_holder` (classe b: proprietário como titular provisório; último usuário até aviso; sem titular, com fatura retida) e extensão de `bill.inactive_economy_rule` à unidade vaga. A NR 13 permite a tarifa básica para economia inativa quando a agência prevê; a NR 11 responsabiliza o proprietário por débito decorrente de cadastro desatualizado (conforme resumo da pesquisa; reconferir o texto).
- **Fontes.** N11 (arts. 33 §2º e 48 VI), N13 (art. 10).

#### CAD-69 — Fonte alternativa de água no cadastro e na cobrança de esgoto

- **Descrição.** Entidade de fonte alternativa da unidade (poço raso ou profundo, captação superficial, água de chuva, reúso), com autorização do órgão gestor, medidor, volume captado e uso residencial ou não, e a regra de cobrança de esgoto sobre ela.
- **Por que importa.** A Lei 11.445 veda que a instalação predial ligada à rede receba outras fontes (art. 45 §2º) e admite a captação para edificação não residencial e condomínio, com autorização e medidor, pagando esgoto pelo volume captado (art. 45 §§11 e 12). A NR 13 remete ao regulador a cobrança de esgoto de quem usa fonte alternativa (art. 16 §3º). É receita perdida e risco regulatório.
- **O que o plano já tem.** `meters.kind WELL` (`V5_1`, CAD-54 do 20a); 07 §4.5 (fonte de abastecimento e poço, P1); `sewer.no_water_volume_rule` com `NON_METERED_ESTIMATE` (P2.5); `sewer.alternative_pct_enabled` (ED-13); ED-24 (fiscalização). Falta a entidade da fonte, a autorização e a regra de cobrança por volume captado.
- **Onde encaixaria.** P1.3 (`V3_6`, `unit_alternative_sources`); P2.5 (esgoto sobre volume captado); ED-13 (tipos de ligação); ED-24 (OS de verificação de fonte não declarada).
- **Prioridade e esforço.** Alta, M.
- **Parâmetro.** Sim: `sewer.alternative_source_billing` (classe b: volume captado medido; estimativa por economia; volume fixo; sem cobrança quando a agência não prevê) e `connection.alt_source_requires_authorization` (classe b).
- **Fontes.** L11445 (art. 45), N13 (art. 16 §3º).

#### CAD-70 — Esgoto não doméstico e carga poluidora do usuário

- **Descrição.** Classificação do usuário não doméstico gerador de efluente, com autorização de lançamento, caracterização do efluente, automonitoramento pelo usuário, vistoria, cobrança complementar por carga (ou tarifa diferenciada) e penalidades.
- **Por que importa.** Agroindústrias e indústrias lançam na rede com carga muito acima da residencial; sem controle há dano à ETE e perda de receita. A ED-80 trata a conformidade do próprio prestador (padrão de efluente, automonitoramento, Declaração de Carga Poluidora), não a relação com o usuário gerador. Que o piloto tenha usuários dessa natureza é [INFERÊNCIA].
- **O que o plano já tem.** ED-80 (onda C); ED-50 e ED-51 (laboratório); P3.6 e ED-34 (serviços faturáveis); 08 R-08 (flexibilização para não residenciais só se a agência previr). Nada sobre o cadastro do gerador.
- **Onde encaixaria.** Nova ED na onda B, depois de ED-50 e ED-80; ganho antecipado em P1.3 (marca `non_domestic_discharger`) e P2.5 (componente de cálculo).
- **Prioridade e esforço.** Média, G.
- **Parâmetro.** Sim: `sewer.nondomestic_charge_model` (classe d: percentual do volume de água, base atual; por carga; taxa fixa por classe).
- **Fontes.** SANEAGO-EFL (requerimento online, taxa e vistoria), OFWAT-TE (cobrança por volume e carga).

#### CAD-71 — Fluxo do empreendedor (loteamento, condomínio, transferência de redes)

- **Descrição.** Processo administrativo para empreendimentos: estudo de viabilidade, diretrizes, análise de projeto, acompanhamento da obra, termo de transferência da rede ao prestador, aceite e interligação, cobrança das taxas.
- **Por que importa.** A NR 11 dedica o capítulo VII (arts. 25 a 30) a isso: estudo às expensas do empreendedor, obras executadas por ele sob fiscalização, transferência por termo específico, interligação só após aceite, pagamento e cessão. A Lei 11.445 (art. 18-A) remete à agência o ressarcimento. Os levantamentos 20g e 20f não mapeiam esses artigos; a tabela do 20g salta do art. 24 ao 28. Concorrentes mantêm portal do empreendedor.
- **O que o plano já tem.** ED-13 e ED-34 (ligações e catálogo de serviços), ED-36 (OS), ED-37 (empreiteiras), ED-53 e P2.22 (pacotes de projeto e as-built no GIS), GIS-CADASTRO-TECNICO §6.4 (ciclo `PLANNED` a `IN_SERVICE`). Todos tratam o lado técnico; falta o processo administrativo e comercial.
- **Onde encaixaria.** Nova ED na onda B, dependente de ED-34, ED-36, ED-53 e P2.22: tipos de serviço `DEVELOPMENT_*`, lote de unidades `PROVISIONAL` do empreendimento (ADR-0039 item 3), termo de transferência com versão e aceite, vínculo com as feições aprovadas no pacote de as-built.
- **Prioridade e esforço.** Média, G.
- **Parâmetro.** Sim: `developer.study_fee`, `developer.reimbursement_rule` e prazos por etapa (classe d, conforme a agência).
- **Fontes.** N11 (arts. 25 a 30), L11445 (art. 18-A), L6766 (arts. 2º §5º, 6º, 7º e 12; a lei não fixa prazo para o prestador), COPASA-EMP, SANEPAR-LIG, CASAN, SABESP-TAB, SEMASA-ITJ.

#### CAD-72 — Ligação provisória, hidrante e carro-pipa

- **Descrição.** Tipo de ligação temporária (obra, feira, evento) com vigência, tarifa ou taxa específica e encerramento por OS; permissão de uso de hidrante com medição; ponto de abastecimento de carro-pipa com venda avulsa e comprovante.
- **Por que importa.** Editais da VISAN e do SAMAE citam "ligações provisórias" com alerta de vencimento (TR-244 e SG-241), e o 09 (QA-14) trata o carro-pipa sob a ótica da qualidade. Sem tipo próprio, o prestador usa unidades permanentes e perde o controle de prazo.
- **O que o plano já tem.** `PROVISIONAL` (ADR-0039) significa cadastro incompleto, não ligação temporária; ED-13 (tipos de ligação), ED-34 e P3.6 (serviços faturáveis e NFS-e).
- **Onde encaixaria.** ED-13 (tipo de ligação com vigência e vencimento automático por OS); ED-34 (serviço faturável); aviso de vencimento pelo mesmo mecanismo do CAD-88.
- **Prioridade e esforço.** Baixa, M.
- **Parâmetro.** Sim: `connection.temporary_max_days` e tarifa por tipo (classe d).
- **Fontes.** Levantamentos 18d (linha 189) e 18e (TR-244/SG-241), 09 (QA-14). Prática de mercado não confirmada em fonte aberta [INFERÊNCIA].

#### CAD-73 — Programas de regularização e renegociação por ato do ente

- **Descrição.** Campanha de recuperação de créditos com vigência por lei ou decreto: público-alvo (faixa de atraso, categoria), abatimento sobre multa, juros e correção, parcelas, termo de adesão, perda do benefício no descumprimento e relatório de adesão e recuperação.
- **Por que importa.** Autarquias municipais usam programas desse tipo com frequência [INFERÊNCIA; sem fonte aberta localizada]. Hoje o plano só tem a parcela bônus, opção do parcelamento, e nenhuma ocorrência de anistia, Refis ou campanha em plano e levantamentos.
- **O que o plano já tem.** P4.1 (parcelamento, `inst.*`); `inst.bonus_installments` (P1.1, desligada por padrão); ED-31 (dívida ativa); P3.1 e P3.29.
- **Onde encaixaria.** P4.1: tabela de campanhas com vigência, ato citado e aprovação dupla; interação com negativação (ED-30) e dívida ativa (ED-31).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `campaign.*` (classe b: ato normativo citado, aprovação dupla, aceite; abatimento do principal fora do limite recusado).
- **Fontes.** Levantamento 20c (COB-36), SABESP-PIX (parcelamento e cobrança por WhatsApp como canal de recuperação).

#### CAD-74 — Medida excepcional por área e mecanismo tarifário de contingência

- **Descrição.** Entidade de medida excepcional com ato (decreto de emergência ou calamidade, declaração da autoridade hídrica), vigência e área (localidade, setor, rota ou polígono), com efeitos selecionáveis: suspender corte, negativação e encargos, conceder desconto ou isenção, prorrogar vencimento, aplicar bandeira ou tarifa de contingência. Aplicação por OS de lote.
- **Por que importa.** A NR 13 prevê mecanismos de contingência em escassez declarada (art. 23) e descontos por força maior (art. 24). O levantamento 08 marcou isso como "NÃO ATENDE" (R-23, P2) e a pendência não chegou ao plano nem ao catálogo de parâmetros. Casos reais: suspensão de corte e isenção em calamidade (Corsan).
- **O que o plano já tem.** `special_situation.max_months` e `quality.noncompliance_compensation` (ED-22, por unidade); interrupção por área na ED-35; vedações de corte e compensação por corte indevido (P3.4); OS de lote (ADR-0039 item 9).
- **Onde encaixaria.** P2.5 (parâmetro de contingência com vigência); ED-22 (situação especial por área); P3.4 (bloqueio de corte consulta as medidas ativas); ED-54 (área pelo GIS).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `event.*` (classe b) e `tariff.contingency_mechanism` (classe d, só com declaração da autoridade).
- **Fontes.** N13 (arts. 23 e 24), levantamento 08 (R-23), CORSAN (menção a medida em calamidade, via pesquisa).

#### CAD-75 — Superendividamento e processo de repactuação

- **Descrição.** Registro do processo de repactuação de dívidas do consumidor (número, órgão, audiência, prazo), efeitos sobre a cobrança (suspensão de acréscimos, negativação e ações), importação do plano homologado como parcelamento com condições próprias e relatório.
- **Por que importa.** O 20f marca LEG-35 (Lei 14.181) como AUSENTE e o plano continua sem tarefa. A lei prevê conciliação com todos os credores e plano de até cinco anos; credor ausente sofre suspensão da exigibilidade e fim dos encargos de mora, e o plano pode prever exclusão de cadastros (art. 104-A). Mínimo existencial de R$ 600,00 pelo Decreto 11.567/2023, com revisão anual determinada pelo STF. Que a conta de água entre no conceito de dívida de consumo é inferência (o texto fala em serviços de prestação continuada).
- **O que o plano já tem.** LEG-35 (20f, AUSENTE); `neg.excluded_profiles` (baixa renda, ente público, classes protegidas); P4.1; P2.20 (revisão suspende cobrança).
- **Onde encaixaria.** P4.1 (situação "em repactuação" e importação do plano); ED-30 (bloqueio de negativação); P3.4 (consulta no pré-corte). Não há regra legal específica de corte para quem está em repactuação; vale a jurisprudência de que o corte pressupõe dívida atual.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `dun.repactuation_effects` (classe b; efeitos sobre encargos e negativação seguem a lei e a decisão do plano homologado).
- **Fontes.** L14181, D11567, STF-ME.

#### CAD-76 — Canais externos de reclamação

- **Descrição.** Origem "externa" no protocolo: Consumidor.gov.br, Procon (SINDEC), agência reguladora, Ministério Público e Defensoria, cada uma com prazo próprio, retorno ao canal e indicador.
- **Por que importa.** A adesão ao Consumidor.gov.br é voluntária, por termo, com resposta em até 10 dias; reclamações externas contam no relatório mensal da NR 11 (art. 54) e no relacionamento com a agência. Plano e ADRs não mencionam Consumidor.gov.br nem Procon.
- **O que o plano já tem.** P3.4 e ED-35 (protocolo, ouvidoria, motor de prazo); `sac.ombudsman_mode` com `REGULATOR_CHANNEL`; P3.22 (relatório do art. 54).
- **Onde encaixaria.** ED-35: campo de origem do protocolo e SLA por canal externo; relatório por origem em P3.22.
- **Prioridade e esforço.** Média, P.
- **Parâmetro.** Sim: `sac.external_channels` (classe d, com prazo por canal).
- **Fontes.** CONS-GOV, N11 (arts. 52 a 54).

#### CAD-77 — Pedidos de acesso à informação (LAI)

- **Descrição.** Tipo de protocolo para pedido de acesso à informação, com prazo de 20 dias prorrogável por 10, recurso em 10 dias e decisão em 5, relatório estatístico anual, para tenants submetidos à LAI.
- **Por que importa.** A lei alcança as autarquias e entidades controladas por Municípios (art. 1º, parágrafo único, II). O levantamento 13 (TR-01) previa módulo opcional e o 21a marca o alcance por natureza jurídica como "a confirmar"; o plano não tem tarefa. A adesão ao Fala.BR é voluntária e cada Município regula a lei por norma própria (art. 45).
- **O que o plano já tem.** Motor único de prazo em dias corridos ou úteis (P3.4); `reg.legal_nature` (G1); ED-35.
- **Onde encaixaria.** ED-35: tipo `INFO_ACCESS_REQUEST` com prazos configuráveis; nenhuma integração obrigatória.
- **Prioridade e esforço.** Média, P.
- **Parâmetro.** Sim: `sac.lai_enabled` (classe b, ligado por padrão para autarquia e empresa pública) e `sac.lai_deadlines` (classe c, não superior à lei).
- **Fontes.** LAI (arts. 1º, 8º, 11, 15, 45), CGU.

#### CAD-78 — Fusão e separação de cadastros duplicados

- **Descrição.** Detecção de candidatos a duplicidade e ferramenta de fusão como OS interna (`CUSTOMER_MERGE`) com visualização prévia, religação de vínculos, consentimentos, protocolos, débitos e contas do portal, e desfazimento por OS inversa.
- **Por que importa.** A unicidade por hash do documento impede duplicidade com documento, mas não a duplicidade sem CPF ou com documento digitado de formas diferentes. A migração do legado (P1.2) traz esses casos.
- **O que o plano já tem.** Índice único por hash (`V3_1`); código `customer-without-document`; P1.2 (reimportação sem duplicar); ADR-0039 item 5 (correção por OS inversa). Não há fusão.
- **Onde encaixaria.** P1.3 e P1.31 (tipo de serviço com aprovação por outra pessoa); ED-12.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Não novo: usa `service_type.internal_approval`.
- **Fontes.** Levantamento 07 (§4.3), 20a (CAD-18).

#### CAD-79 — Destino da devolução (chave Pix)

- **Descrição.** Dado de destino para devolução ao usuário: devolução Pix ao pagador de origem, chave Pix do titular (tipo CPF ou CNPJ) ou conta bancária, com validação de titularidade, cifra e retenção.
- **Por que importa.** `pay.refund_mode` admite `BANK_TRANSFER`, mas não há onde guardar o destino nem regra para validá-lo.
- **O que o plano já tem.** P3.29 (`REFUND_REQUEST`, `pay.refund_mode`); G4 (PSP e Pix).
- **Onde encaixaria.** P3.29: modo `PIX_RETURN_TO_PAYER` quando o pagamento original foi Pix [INFERÊNCIA sobre a devolução Pix a confirmar com o PSP]; chave do titular só com documento coincidente.
- **Prioridade e esforço.** Média, P.
- **Parâmetro.** Sim: `pay.refund_destination` (classe b).
- **Fontes.** BCB-PIX (regras de Pix), levantamento 20c (ARR-08).

#### CAD-80 — Usuários essenciais e vulneráveis (institucionais e pessoais)

- **Descrição.** Cadastro, por unidade, de usuário essencial (hospital, escola, internação coletiva, identificados por CNES, INEP ou alvará) e, por pessoa, de usuário dependente de abastecimento contínuo por motivo de saúde (hemodiálise domiciliar, home care), com comprovação, validade e consentimento. Efeitos: aviso reforçado e personalizado antes de corte e de interrupção programada, prioridade de religação e de água alternativa, terceiro autorizado.
- **Por que importa.** Humanitário e de baixo custo. Não localizamos norma brasileira de saneamento que preveja o cadastro pessoal; o setor elétrico tem referência (equipamento de suporte à vida, notificação específica com entrega comprovada) e o Reino Unido mantém registro de serviços prioritários com serviços mínimos (água alternativa, terceiro autorizado, formatos acessíveis). A ausência de norma é resultado de busca, não de prova.
- **O que o plano já tem.** `cut.protected_classes` (P3.4: saúde, educação, internação coletiva, tarifa social); fornecimento de emergência a unidades essenciais e interrupção por área (ED-35); "clientes sensíveis primeiro" (ED-54); NR 11 art. 41. Nada para a pessoa dependente.
- **Onde encaixaria.** P1.3 (campos e anexos em `V3_6`); P3.4 (ganchos de corte e religação); ED-35 e ED-54 (interrupção). Dado de saúde é sensível (LGPD art. 11): coletar por solicitação do próprio titular, com consentimento específico; base legal definida pelo DPO do tenant [INFERÊNCIA]; RIPD no P3.10.
- **Prioridade e esforço.** Alta, M.
- **Parâmetro.** Sim: `vulnerable.enabled` (classe b, ligado por padrão pelo ADR-0038) e `vulnerable.effects` (classe b).
- **Fontes.** L11445 (art. 40 §3º), N11 (art. 41), REN1000, OFWAT-PSR.

### 3.2 Classe B: presentes, mas rasos

#### CAD-81 — Perfil de atendimento prioritário e de acessibilidade

- **Descrição.** Campos do cliente para categoria de prioridade (Lei 10.048: pessoa com deficiência, com TEA, idosa, gestante, lactante, com criança de colo, obesa, com mobilidade reduzida, doador de sangue), acompanhante, formato acessível de documentos (braille, fonte ampliada, PDF/UA, áudio) e canal de comunicação (Libras, TDD, videoatendimento).
- **Por que importa.** O 13 (AT-10, AC-02 a AC-04) e o 20f (LEG-07, LEG-50) já recomendam; nenhuma tarefa do plano os carrega. O Decreto 5.296 exige ao menos um telefone adaptado; a LBI (art. 9º) exige prioridade e comunicação acessível. Sabesp e Caesb mantêm Libras por vídeo e atendimento agendado.
- **O que o plano já tem.** ED-40 (senhas com prioridade legal), P0.36 (contraste WCAG 2.1 AA), P3.20 (PDF com estrutura marcada), ED-39 (acessibilidade do aparelho).
- **Onde encaixaria.** P1.3 (`V3_6`); P2.17 (formato da conta); ED-35, ED-40 e ED-42 (canais). O 20f recomenda elevar o critério do portal a WCAG 2.2 AA.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `registry.accessible_formats` e `bill.braille_enabled` (classe d; braille depende de lei estadual, fonte secundária).
- **Fontes.** L10048, LBI, D5296, SABESP-LIBRAS, CAESB-CS.

#### CAD-82 — Validação cadastral externa

- **Descrição.** Validação além do dígito verificador: situação cadastral do CPF e do CNPJ, validação biográfica ou biométrica para operações de risco (troca de titularidade, tarifa social) e revalidação em lote na migração.
- **Por que importa.** Reduz titular inexistente, cadastro de pessoa falecida e fraude de titularidade. O GSAN tinha o indicador de consulta à Receita; o 20a (CAD-21) propôs `registry.validate_document_online` e a chave não chegou ao catálogo. Fornecedor de mercado alerta que base cadastral desatualizada pode gerar rejeição de fatura eletrônica [alegação de fabricante]; confirmar no manual e no XSD da NFAg-e se o destinatário sem CPF é aceito (pendência já aberta no §2.4 do plano).
- **O que o plano já tem.** ADR-0008 e `DocumentNumber` (DV e CNPJ alfanumérico); Lei 14.534 (CPF obrigatório e suficiente); P1.2; P1.31.
- **Onde encaixaria.** P1.3 e P1.2 (revalidação); P1.31 (condição da OS `OWNERSHIP_TRANSFER`); P2.6 (regra do destinatário).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `registry.validate_document_online` (classe d: nenhum; situação do CPF e CNPJ; situação mais biometria nas operações de risco). Credencial no cofre; consulta paga por tenant; guardar apenas situação e data.
- **Fontes.** SERPRO-CPF e DATAVALID (contratação por pessoa jurídica ou ente público), L14534.

#### CAD-83 — Endereço normalizado e geocodificação do imóvel

- **Descrição.** Pipeline de endereço: código IBGE do município, logradouro e bairro como catálogos, validação contra base de referência e geocodificação com nível de precisão gravado, separada do cadastro técnico de rede.
- **Por que importa.** O 07 (§6.1, item 11) pede endereço normalizado como P1, mas a P1.3 cita só CEP. O CNEFE do Censo 2022 é público, georreferenciado, com níveis de geocodificação de 1 a 6; o DNE dos Correios é base licenciada, e o uso em SaaS multi-tenant deve ser consultado; OpenStreetMap é ODbL, com Nominatim e Pelias autohospedáveis. A licença comercial do CNEFE não foi localizada.
- **O que o plano já tem.** P1.3 (CEP); `MapPointPicker` e coordenada em par (07); ED-12 (endereço alternativo); P2.23 (vínculo feição, imóvel e ligação); ED-55 (casamento por endereço); ADR-0033 (deslocamento de coordenada na anonimização).
- **Onde encaixaria.** P1.3 (catálogos de logradouro e bairro, qualidade da geocodificação); P2.23 (revisão de ponto fora do setor ou da quadra).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Não (decisão técnica do produto).
- **Fontes.** CNEFE, GEOCODEBR (referência de método), DNE, OSM.

#### CAD-84 — Contatos verificados e preferências por finalidade

- **Descrição.** Estado de verificação do e-mail e do telefone (código ou link, confirmação em duas etapas), preferência por finalidade (cobrança, serviço operacional, interrupção, pesquisa) e horário, e canal de contingência (e-mail devolvido, depois SMS, depois impresso).
- **Por que importa.** A prova de entrega do aviso de corte (P3.5) e a fatura digital por opt-in (ED-41) dependem de contato válido; hoje o contato é dado sem estado.
- **O que o plano já tem.** ED-41 (opt-in por canal e finalidade); 07 (`customer_emails` com `consent_id`, `customer_phones` em E.164); OS `CUSTOMER_CONTACT_UPDATE` com aviso ao contato anterior (20d §3.3); módulo LGPD.
- **Onde encaixaria.** P1.3 (`V3_6`); ED-41; P3.5 (usa o estado do contato como evidência).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `contact.verification_required` (classe b) e `contact.fallback_order` (classe d).
- **Fontes.** Levantamentos 07 (§5.3), 20d.

#### CAD-85 — Documentos exigidos por serviço e documentos emitidos verificáveis

- **Descrição.** (a) Lista de documentos exigidos por tipo de serviço, sem exigir firma reconhecida nem cópia autenticada, com validade e estado de verificação. (b) Emissão de certidão de ligação, declaração de quitação e comprovantes, com código verificador ou QR de autenticidade e política de assinatura.
- **Por que importa.** O 13 (CD-04) prevê o checklist sem firma reconhecida e o 20f (LEG-51) a política de assinatura; ambos ficaram sem tarefa. Lei 13.726 e Lei 14.534 limitam a exigência de documentos.
- **O que o plano já tem.** ED-34 (catálogo de serviços com SLA e cobrança), P1.20 (arquivos), ED-12 (anexos), P3.20 (contrato de adesão e regulamento publicados), `cert.positive_effect` (ED-38, certidão negativa).
- **Onde encaixaria.** ED-34 (campo `required_documents`); ED-38 e P3.20 (emissão verificável); política de assinatura no P3.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `service_type.required_documents` e `doc.signature_policy` (classe d).
- **Fontes.** L14063 (níveis de assinatura; o Decreto 10.543 vale só na esfera federal), L14534.

#### CAD-86 — Fluxo encadeado da ligação nova

- **Descrição.** Workflow por tipo de solicitação: pedido, documentos, viabilidade (rede, pressão, diâmetro), exigência de esgoto simultâneo onde há rede, orçamento por bitola, pavimento e categoria, guia ou NFS-e, agendamento de vistoria do padrão, execução, ativação (hidrômetro, primeira leitura, dias mínimos de cobrança) e vínculo do cliente.
- **Por que importa.** O edital do SAMAE (SG-525) exige sequência predefinida de atividades para ligação nova e o 18f a classificou como "não planejada". O aceite da ED-35 não a cita. Prazos de vistoria e ligação existem em norma da agência (em SC, vistoria em 5 dias úteis e ligação em 10, conforme o 20f) e a NR 11 remete os prazos ao regulador (arts. 72 e 73).
- **O que o plano já tem.** ADR-0039 item 3 (pedido cria unidade `PROVISIONAL`); `CONNECTION_CREATE` em OS de campo com dependência de vistoria (20d §3.3); ED-13, ED-34, ED-36 (`WAITING_DEPENDENCY`), P3.6; TR-300 (dias mínimos); GIS-06/08 para viabilidade.
- **Onde encaixaria.** ED-35 e ED-36: definição de `service_type.workflow` com etapas, dependências e prazos do catálogo; preços por tenant com vigência (20a Q7).
- **Prioridade e esforço.** Alta, G.
- **Parâmetro.** Sim: `service_type.workflow` (classe d) e `connection.new_requires_sewer_when_available` (classe b; a regra do art. 10 da NR 11 consta do 20g).
- **Fontes.** N11 (arts. 8º a 10, 72 e 73), SANEPAR-LIG, SAMAE-JS, SAMAE-BLU, CASAN, SABESP-TAB.

#### CAD-87 — Ligação obrigatória e cobrança por disponibilidade

- **Descrição.** Ciclo da edificação com rede disponível e não conectada: comunicação da disponibilidade, aviso de 90 dias na fatura anterior, valor de disponibilidade, prazo máximo de um ano para ligar, dispensa por inviabilidade comprovada, lista ao titular e à agência, notificação ou OS de ligação compulsória e multa.
- **Por que importa.** É receita relevante em esgoto e obrigação com aviso datado. O 20f marca LEG-16 como PARCIAL e o 08 (R-09 e R-30) como sem tarefa; o 20g aponta o relatório de não conectados para P3.22, mas a lista de relatórios da P3.22 não o contém.
- **O que o plano já tem.** `bill.cut_connection_charge` (`AVAILABILITY_FEE`), `bill.inactive_economy_rule`; situações `FACTÍVEL` e `POTENCIAL` (ED-13); `service_connections` (P2.23); mensagens legais (ED-79).
- **Onde encaixaria.** P2.23 (estado "factível não conectada" com data de disponibilidade); P2.5 (cobrança); CAD-96 (mensagem); P3.22 (relatório); ED-13 (OS de ligação compulsória).
- **Prioridade e esforço.** Alta, G.
- **Parâmetro.** Sim: `avail.notice_days` (classe c, piso 90), `avail.multiplier` (classe c, piso 2 vezes a tarifa básica, conforme 08 R-09), `avail.max_connect_months` (classe c, teto 12) e `avail.exemption_requires_proof` (classe a).
- **Fontes.** L11445 (art. 45 §§4º a 8º, conforme o 20f), N13 (arts. 17 a 20), N11 (art. 8º).

#### CAD-88 — Categorias opcionais da NR 13 e benefícios com validade

- **Descrição.** Parâmetro de categorias opcionais (residencial vulnerável, micro e pequena empresa, categoria coletiva com várias economias e hidrômetro único, entidade assistencial) com critério de enquadramento, prova e validade, e comunicado de vencimento do benefício.
- **Por que importa.** A NR 13 (art. 6º) fixa o mínimo e admite as demais; o 08 (R-05) já apontava. O catálogo tem só `social.local_categories`. Embasa mantém tarifa para entidade assistencial. Os editais pedem alerta de vencimento de benefício (TR-244 e SG-241); a P1.5 cobre só a tarifa social.
- **O que o plano já tem.** `social.local_categories` (G2); `tariff_versions`; ED-12 (catálogos); `special_situation.max_months` (ED-22).
- **Onde encaixaria.** P2.5 e `V4_1` (categorias opcionais); P1.5 (aviso de vencimento); ED-22.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `tariff.optional_categories` (classe d) e `benefit.expiry_notice_days` (classe d).
- **Fontes.** N13 (art. 6º), EMBASA.

#### CAD-89 — Sazonalidade da unidade

- **Descrição.** Marca de sazonalidade com efeito na crítica de leitura (não gerar hidrômetro parado), na média e, se a agência previr, na parte fixa fora de temporada.
- **Por que importa.** O GSAN tem `indicadorSazonalidade` por subcategoria, que desliga a anomalia de hidrômetro parado (levantamentos 01 e 02); o plano não a carrega. Em município turístico gera muitas anomalias falsas e vistorias inúteis.
- **O que o plano já tem.** `meter.average_months` e `meter.after_cap_rule` (G1); `ExpectedReadingRange`; R-23 do 08 cita tarifas sazonais.
- **Onde encaixaria.** P1.3 (marca); P2.5 (crítica e média).
- **Prioridade e esforço.** Baixa, P.
- **Parâmetro.** Sim: `meter.seasonal_rule` (classe d).
- **Fontes.** Levantamentos 01, 02 e 08.

#### CAD-90 — Cofaturamento (NR 13, arts. 46 a 56)

- **Descrição.** Contrato de cofaturamento por serviço, aviso de pelo menos 60 dias antes do início, discriminação dos valores, retirada a pedido do usuário, repasse e segregação, documento de cobrança com identificador único e regra de que inadimplência do serviço cofaturado não gera corte de água.
- **Por que importa.** A ED-25 inclui resíduos na conta, mas as regras de cofaturamento da NR 13 e o 08 (R-16) não estão em tarefa; "cofaturamento" tem zero ocorrências no plano.
- **O que o plano já tem.** ED-25 (resíduos na conta, fatura desvinculada, carnê); `cut.third_party_debt_blocks`; ED-32 (contábil); LEG-04 menciona o aviso.
- **Onde encaixaria.** ED-25 e ED-32; P2.5 (item de fatura com origem); mensagem pelo CAD-96.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `cobill.*` (classe b).
- **Fontes.** N13 (arts. 46 a 56, conforme a pesquisa), levantamento 08 (R-16).

#### CAD-91 — Identificação automática da tarifa social e fonte do CadÚnico por natureza jurídica

- **Descrição.** (a) Pipeline: recebimento da lista, validação do leiaute, cruzamento por CPF do titular da fatura e dos membros da família, desempate de uma economia por família, classificação sem pedido, lista de elegíveis sem unidade (busca ativa), validade do CadÚnico e recertificação. (b) Regra de fonte: estados e municípios não repassam dados do CadÚnico direto a prestador de direito privado; só autarquia e fundação de direito público podem recebê-los, e a agência intermedeia nos demais casos. (c) Sequência: a importação está na ED-57, da onda C.
- **Por que importa.** A Lei 14.898 manda classificar automaticamente (art. 4º); a ANA e a NR 13 fixam prazo de adaptação; a Corsan já concede para 240 mil famílias com busca ativa; Sanepar cruza o CPF do titular com o CadÚnico. Não existe API nacional para essa finalidade; a operação é descentralizada. O titular da fatura pode não ser o responsável familiar (locatário, proprietário).
- **O que o plano já tem.** P1.5 (`V4_2`, piso, `GRACE_3M`, CadÚnico de 25 meses inelegível); `social.base_import`, `social.family_tiebreak`, `social.legal_basis`, `reg.legal_nature` (G1, G2); LEG-42 (sigilo); ED-57 (importação de lista com base legal, onda C).
- **Onde encaixaria.** P1.5: aceite com leiaute, cruzamento e desempate sobre massa sintética; ED-57 fica com os adaptadores por agência; validação cruzada `social.base_import` × `reg.legal_nature` (combinação inválida travada, classe a). Antes de fixar, conferir se a P1.5 já pressupõe o cruzamento.
- **Prioridade e esforço.** Alta, G.
- **Parâmetro.** Sim: ampliar `social.base_import` e acrescentar `social.matching_rule` e `social.active_search` (classe b).
- **Fontes.** L14898, N13 (arts. 26 a 45, conforme ANA-FAQ), MDS57, ANA-CAD, CORSAN, SANEPAR-TS, SABESP-TS.

#### CAD-92 — Índice de Tarifa Social, lista positiva da ANA e relatório anual

- **Descrição.** Indicador mensal (economias residenciais beneficiadas sobre economias ativas), reconciliação com a lista positiva publicada pela ANA e relatório anual à agência.
- **Por que importa.** A NR 13 (art. 45) usa o índice no ajuste tarifário; o 08 (R-15) o listou e o plano não o tem. A ANA publica a lista positiva mensalmente, por município e regulador.
- **O que o plano já tem.** `social.annual_report` (G2); P3.22 (relatório de tarifa social e isenções); ED-48 (indicadores).
- **Onde encaixaria.** P3.22 e ED-48.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim, já parcial: `social.annual_report`; indicador sem chave nova.
- **Fontes.** N13, ANA-FAQ.

#### CAD-93 — Contratos especiais de grandes usuários e venda por atacado

- **Descrição.** Contrato com volume contratado e mínimo, tarifa negociada, vigência e renovação, reajuste, penalidade por excesso, pontos de medição e telemetria dedicados, aprovação pela agência e relatório. Inclui venda de água tratada em bloco a outro prestador.
- **Por que importa.** A NR 11 (art. 19) exige contrato específico para grandes usuários; a ARSESP tem programas comerciais para quem consome 1.000 m³ por mês ou mais; a rescisão de contratos de demanda firme pela Sabesp mostra o risco de contrato sem regra. O aceite da ED-22 diz apenas "contrato especial testado".
- **O que o plano já tem.** ED-22; ED-17 (AMR e alarmes de grandes consumidores); `large_user.min_m3` e `large_user.min_economies`; 08 R-21 (informar contratos à agência).
- **Onde encaixaria.** ED-22 (ampliar o aceite); ED-17; P2.5 (componente de cálculo). A venda por atacado é [INFERÊNCIA] sem fonte aberta, de prioridade baixa.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `large_user.*` (classes d e b).
- **Fontes.** N11 (art. 19, conforme o 20g), ARSESP-PROG, FOLHA-SABESP.

#### CAD-94 — Agendamento pelo usuário

- **Descrição.** Agendamento pelo portal, aplicativo ou WhatsApp de atendimento presencial ou por vídeo, vistoria, aferição e ligação, com janelas por turno, confirmação, reagendamento e lembrete.
- **Por que importa.** Reduz falta e retrabalho e atende a exigência de editais. Há agendamento no aplicativo da Caesb e videoatendimento agendado na Sabesp. No plano, "agendamento" aparece só como agenda de relatórios e agenda de visitas da ED-23.
- **O que o plano já tem.** ED-23 (agenda de visitas por vazamento); ED-36 (programação de OS); ED-40; ED-41.
- **Onde encaixaria.** ED-38 (portal), ED-36 (calendário de OS) e ED-41 (lembretes). As janelas são definidas pelo tenant; o produto não faz gestão de equipe (ADR-0035).
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `sched.customer_windows` (classe d).
- **Fontes.** CAESB-CS, SABESP-AV.

#### CAD-95 — Situação do abastecimento por endereço (consulta pública)

- **Descrição.** Consulta pública, sem login, de interrupções ativas e programadas por endereço, bairro ou inscrição, com previsão de retorno atualizada pelo operador.
- **Por que importa.** A NR 11 (art. 39) exige informar os usuários e, em emergência, divulgar logo após identificar a área afetada. O GSAN tem consulta de falta d'água por bairro. Não envolve dado pessoal.
- **O que o plano já tem.** ED-35 (interrupção programada por área, aviso aos afetados, sem mapa); ED-54 (área pelo isolamento); P3.5 e ED-41 (notificações).
- **Onde encaixaria.** ED-35 e ED-38.
- **Prioridade e esforço.** Média, P.
- **Parâmetro.** Sim: `portal.outage_status_visibility` (classe d).
- **Fontes.** N11 (art. 39, conforme a pesquisa), GSAN-RA, CAESB-CS.

#### CAD-96 — Motor de mensagens legais da fatura antes da ED-79

- **Descrição.** Motor mínimo no P2: catálogo de mensagens versionado por norma, condição de disparo e prova de emissão ligada à versão da conta, ampliado depois pela ED-79.
- **Por que importa.** Mensagens obrigatórias dependem do motor desde a primeira conta: perda do benefício da tarifa social com 3 meses (P1.5, `GRACE_3M`), aviso de disponibilidade de 90 dias (CAD-87), cofaturamento de 60 dias (CAD-90), alto consumo, declaração anual de quitação e bloco de qualidade da água (P2.24). O 20f (LEG-04) já pede "tarefa explícita do motor de mensagens no P2". O motor está na ED-79, da onda B.
- **O que o plano já tem.** `social.loss_notice_months`; `bill.invoice_template`; P2.24; ED-79 (motor de mensagens legais com prova de emissão).
- **Onde encaixaria.** P2.5, P2.17 e P2.24: o núcleo do motor sobe para o P2; a ED-79 mantém o restante.
- **Prioridade e esforço.** Alta, M.
- **Parâmetro.** Sim: catálogo de mensagens (classes a e b) com chave por norma.
- **Fontes.** L14898 (arts. 2º §2º e 3º), N13 (arts. 18 e 58, conforme o 20f), levantamento 20f (LEG-04).

#### CAD-97 — Segunda via e pagamento sem conta no portal

- **Descrição.** Caminho de convidado: informar o código de vinculação impresso na conta e obter apenas a fatura em aberto, a linha digitável e o Pix copia e cola, sem dado pessoal (valor, vencimento, mês).
- **Por que importa.** Reduz atrito de quem só quer pagar e alinha-se à regra de que o prestador não recusa pagamento (travado no catálogo). A P3.3 exige conta com login por CPF, CNPJ ou e-mail.
- **O que o plano já tem.** P3.3, P3.20 e ADR-0022 (vínculo por código impresso mais documento do titular); P3.8 (testes de enumeração); decisão da v2.3 sobre o login do consumidor.
- **Onde encaixaria.** P3.3 como variante. Toca o ADR-0022 e a política de anti-enumeração: depende de cartão do dono. O código impresso, de alta entropia, é preferível à matrícula; tentativas limitadas e resposta indistinta.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `portal.guest_payment` (classe b): desligado; só código; código mais sufixo do documento.
- **Fontes.** Levantamento 20e (portal), GSAN-212 (consulta de débitos por CPF ou CNPJ no GSAN).

### 3.3 Classe C: inovações

#### CAD-98 — Qualidade cadastral contínua

- **Descrição.** Painel e regras contínuas de qualidade do cadastro comercial: documento ausente, inválido ou com situação irregular; contato não verificado; categoria incompatível com CNAE; economias incompatíveis com a área construída; unidade ativa sem hidrômetro ou sem leitura em N ciclos; hidrômetro vencido; endereço sem coordenada ou fora do setor; titular possivelmente falecido; duplicidades; unidade sem usuário. Pontuação por rota e setor, fila de correção por OS de lote e tendência.
- **Por que importa.** Reduz rejeição de documento fiscal e erro de cobrança, e alimenta o recadastramento com critério. NR 11 arts. 24 e 33 §2º atribuem responsabilidade ao usuário e ao proprietário por cadastro desatualizado, o que exige que o prestador detecte e comunique.
- **O que o plano já tem.** Regras de consistência na entrada (P1.3); recadastramento (ED-16); OS de fiscalização por critério (ED-24); painel de qualidade da rede (P1.26); OS de lote (P1.31); núcleo de relatórios (P3.22).
- **Onde encaixaria.** Nova tarefa no P3, sobre o núcleo da P3.22; alimenta ED-16 e ED-24.
- **Prioridade e esforço.** Alta, G.
- **Parâmetro.** Sim: `quality.rules` (classe d, regras e limiares por tenant).
- **Fontes.** N11 (arts. 22 a 24 e 33), SANSYS (alegação de fabricante).

#### CAD-99 — Alerta preventivo de consumo anormal

- **Descrição.** Alerta ao usuário antes da fatura, no fechamento da leitura, quando a crítica aponta consumo acima de percentual da média, por e-mail, WhatsApp ou aplicativo, com orientação de teste de vazamento e atalho para pedir revisão. Com telemetria, alerta de fluxo contínuo com limites configuráveis.
- **Por que importa.** Conta alta é motivo recorrente de contestação e de revisão [INFERÊNCIA; sem estatística do setor localizada]. Sabesp envia alerta por WhatsApp quando o consumo passa de 30% da média e oferece videochamada técnica; WaterSmart, Badger e Neptune trabalham com alerta de fluxo contínuo e limites do cliente. O plano só tem revisão a pedido e mensagem na fatura.
- **O que o plano já tem.** `bill.hold_on_anomaly` (P2.5), mensagem de alto consumo (LEG-04), P3.28 (revisão por vazamento), ED-15 (autoleitura com crítica), ED-17 (AMR de grandes consumidores), P3.5 e ED-41 (envio).
- **Onde encaixaria.** P2.5 (gancho na crítica), P3.5 (envio), ED-17 e ED-39 (telemetria e aplicativo).
- **Prioridade e esforço.** Alta, M.
- **Parâmetro.** Sim: `alert.high_consumption_pct` e `alert.channels` (classe d).
- **Fontes.** SABESP-ALERTA, SABESP-IOT, WATERSMART, PALOALTO, BADGER.

#### CAD-100 — Régua de cobrança com promessa de pagamento e priorização

- **Descrição.** Régua configurável por segmento (tipo de débito, idade, categoria, classe protegida), com passos e canais, link Pix na mensagem, promessa de pagamento (data prometida que suspende os passos seguintes e reinicia se quebrada) e medição de conversão por passo. Priorização por escore de propensão, apenas para ordenar o contato.
- **Por que importa.** A Sabesp reporta cobrança por WhatsApp com R$ 50 milhões arrecadados [imprensa]; a McKinsey relata redução de perda de 2% a 5% com modelos de propensão [consultoria, com reserva]. Nenhuma ocorrência de "promessa de pagamento" em plano e levantamentos.
- **O que o plano já tem.** P3.4 (seleção mínima do aviso), P4.1 (ação de cobrança completa), ED-30, ED-41, P3.5, `dun.notice_fee`.
- **Onde encaixaria.** P4.1; escore como etapa posterior sobre o BI (ED-47). O escore nunca decide corte ou negativação: a decisão automatizada exige revisão e transparência (LGPD art. 20) e conflita com as travas da Lei 14.015.
- **Prioridade e esforço.** Média, G.
- **Parâmetro.** Sim: `dun.ruler` (classe d).
- **Fontes.** SABESP-PIX, MCK.

#### CAD-101 — Login gov.br como provedor federado do portal

- **Descrição.** Opção de identidade federada gov.br (OIDC) no realm do portal, com o nível de confiabilidade (bronze, prata, ouro) como critério por operação: prata ou ouro para troca de titularidade, tarifa social e dado de contato.
- **Por que importa.** Reduz custódia de senha e recuperação e reforça a prova de posse do CPF no vínculo. A Caesb condiciona o assistente com dados por CPF ao login gov.br. A elegibilidade de entes municipais ao Login Único não foi confirmada.
- **O que o plano já tem.** ADR-0022 e decisão da v2.3: conta própria no realm `ziipsan-portal` (CPF, CNPJ ou e-mail com senha); P3.3 (mapeamento de login por índice cego); ED-39 (PKCE).
- **Onde encaixaria.** P3.3 e ED-38 como opção. Altera o ADR-0022: depende de cartão do dono.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `portal.idp_govbr` (classe d) e `portal.min_assurance_by_operation` (classe b).
- **Fontes.** GOVBR-INT, GOVBR-NIV, CAESB-CANAIS.

#### CAD-102 — Fatura que explica a variação e compara com domicílios semelhantes

- **Descrição.** Bloco determinístico de explicação da variação (dias de leitura, consumo contra o mês anterior e a média, faixa atingida, reajuste, serviços, encargos), gerado do cálculo gravado, na fatura, no portal e como função do assistente. Comparativo opcional com domicílios semelhantes.
- **Por que importa.** Reduz contestação e contato. Sabesp oferece "revisar aumento da conta"; WaterSmart tem "Bill Explainer". O comparativo reduziu o consumo em 4,9% em ensaio controlado na Costa Rica, e relatórios de água reduzem de 3,7% a 5,6% em média; a Oracle constatou rejeição a "vizinhos" e passou a usar "casas semelhantes". O efeito recai mais sobre domicílios de renda alta.
- **O que o plano já tem.** ED-43 (assistente que só chama funções do portal), ED-38 (simulação de fatura), `bills.tariff_snapshot`, P2.17.
- **Onde encaixaria.** P2.17, ED-38 e ED-43. O comparativo usa agregado mínimo, por analogia com `gis.public_aggregation_min_units`.
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `bill.peer_comparison` (classe d, desligado por padrão).
- **Fontes.** SABESP-AV, WATERSMART, OPOWER, OPOWER-HER, JPAL, WB-CR.

#### CAD-103 — Cruzamento com o cadastro imobiliário municipal

- **Descrição.** Importar a base do cadastro imobiliário (inscrição, área construída, uso, habite-se, alvará) e cruzar com as unidades: imóvel edificado sem ligação, área divergente da economia, uso divergente da categoria. Gera OS de vistoria ou de recadastramento em lote.
- **Por que importa.** Recuperação de receita e correção de economia a menor. O IPTU aparece como identificador (07, `external_ids`), mas não como fonte de cruzamento.
- **O que o plano já tem.** ED-24 (fiscalização por critério), ED-16 (recadastramento), ED-55 (casamento de unidades por matrícula ou endereço na migração GIS), ED-62 (conectores com ERP do cliente: contabilidade, estoque, RH).
- **Onde encaixaria.** ED-62 (conector), ED-16 e ED-24. Compartilhamento dentro do mesmo ente, com base legal e finalidade registradas [INFERÊNCIA, LGPD arts. 26 e 27].
- **Prioridade e esforço.** Média, M.
- **Parâmetro.** Sim: `registry.property_registry_sync` (classe d).
- **Fontes.** Levantamentos 07 e 18d (E16).

#### CAD-104 — Planos de pagamento por capacidade (fatura nivelada e acordo por renda)

- **Descrição.** Fatura nivelada opcional (valor médio estável com acerto periódico) e acordo por capacidade de pagamento para baixa renda fora da tarifa social, com parcelas ajustadas à renda declarada.
- **Por que importa.** SAP e Oracle oferecem fatura nivelada e acordos de pagamento; a Ofwat orienta pagamento flexível, avaliação de capacidade e execução como último recurso. No Brasil a prática é rara e o ganho é incerto.
- **O que o plano já tem.** `inst.*` (P4.1), parcela bônus, tarifa social (P1.5).
- **Onde encaixaria.** P4.1, depois do CAD-75.
- **Prioridade e esforço.** Baixa, M.
- **Parâmetro.** Sim: `inst.income_based` (classe d).
- **Fontes.** SAP, ORACLE-CCS, OFWAT-PF.

## 4 Itens que o plano já cobre bem

Sem repetir o detalhe, os pontos abaixo estão desenhados com aceite e não precisam de reforço nesta frente.

- **Unidade como raiz e OS para toda alteração.** ADR-0039, P1.31, P1.3, OS de lote, histórico com `service_order_id`, teste de arquitetura contra gravação direta.
- **Parâmetros e bifurcação normativa.** ADR-0002, ADR-0015 e ADR-0038; classes a a d; vigência, aprovação dupla e aceite; perfis por agência (ARIS, AGIR, ARESC) e travas de lei federal.
- **Cliente e documento.** Cliente separado de usuário do sistema; cifra e índice cego; máscara configurável e busca por documento controlada (ADR-0008, P0.11); CNPJ alfanumérico em `DocumentNumber`; unicidade por hash; vínculo por papel e vigência; histórico de 120 meses (P2.10, P3.20).
- **Territorial e unidade.** Inscrição única, matrícula com dígito verificador, situações com flags, `service_connections` (P2.23), escopo por área (P3.30), permissões `recurso:ação` (P0.13).
- **Hidrometria.** ED-14 com Portaria Inmetro 155/2022, lacre, destino do retirado e alerta de validade.
- **Estrutura tarifária.** Consumo mínimo travado para contratos novos (`tariff.fixed_part_model`), parcela fixa e faixas, reajuste com vigência, esgoto sem água (`sewer.no_water_volume_rule`), condomínio (ED-15), situações especiais (ED-22).
- **Tarifa social.** Piso federal travado, concessão sem exigir adimplência, aviso de perda de 3 meses, cifra de NIS e renda, carência (`social.*`, P1.5).
- **Faturamento e retificação.** Ciclo com cronograma e referência fechada (P2.5), retificação como OS interna (P2.20), declaração de quitação e bloco de qualidade da água (P2.24), seis datas de vencimento.
- **Arrecadação.** Retorno idempotente, efeito da classificação, aviso de duplicidade, devolução e fila de não identificados (P3.1, P3.29), Pix híbrido e Pix Automático alinhados à regulação do Banco Central, FEBRABAN v08 (verificado em 22/10/2026: a edição vigente do padrão de débito automático de 150 posições é a v09, atualizada em 11/03/2026).
- **Cobrança e corte.** Vedações da Lei 14.015, revalidação na execução inclusive offline, compensação por corte indevido, aviso com prova de entrega, negativação com trava de perfis e prazos (P3.4, ED-30, ED-31, P4.1).
- **Atendimento.** Protocolo com motor único de prazo, ouvidoria como segunda instância, interrupção programada, relatório do art. 54 da NR 11, omnichannel e assistente com limite de funções (P3.4, ED-35, ED-42, ED-43).
- **Portal e aplicativo.** Realm próprio, vínculo por código impresso, anti-enumeração, histórico e documentos (ADR-0022, P3.3, P3.20, P3.8), aplicativo híbrido com PKCE (ED-39).
- **LGPD.** Registro de acesso a dado pessoal, RIPD e resposta a incidente (P3.10), retenção com pisos (ADR-0015).

## 5 Inferências e pontos a confirmar

### 5.1 Marcadas como [INFERÊNCIA] neste documento

- Que o piloto e os clientes-alvo tenham usuários não domésticos geradores de efluente (CAD-70).
- Que autarquias municipais conduzam programas de regularização por ato do ente com frequência (CAD-73).
- Que a herança responda pelas dívidas do titular falecido e o enquadramento do dado de falecido na LGPD (CAD-65).
- Que a API de situação do CPF exponha o valor "titular falecido" (CAD-65).
- Que a conta de água entre no conceito de dívida de consumo da Lei 14.181 (CAD-75).
- Que a devolução Pix ao pagador seja viável no PSP do tenant (CAD-79).
- Que o nome social não seja obrigatório para autarquia municipal (CAD-67).
- Base legal do compartilhamento do cadastro imobiliário e dos dados de saúde de usuário dependente (CAD-103 e CAD-80).
- Que haja demanda de venda de água por atacado no perfil de clientes (CAD-93).

### 5.2 A confirmar no texto oficial ou com terceiros

- Número exato dos artigos da NR 11 e da NR 13 citados por resumo (arts. 8º a 10, 24, 33 §2º, 48 VI, 17 a 20, 23, 24, 31, 32, 36, 41, 45, 46 a 56): a pesquisa leu parte do texto por ferramenta de resumo. Reconferir no DOU ou no manual da ANA.
- Início da verificação da NR 11: 20/05/2027 (texto) ou 27/05/2027 (notícia).
- Se a NFAg-e aceita destinatário sem CPF ou CNPJ e como trata cadastro desatualizado (manual e XSD; pendência já registrada no §2.4 do plano).
- Se municípios e autarquias podem obter credenciais do Login Único gov.br; se prestador privado consulta o Sirc ou o Cadastro Base do Cidadão (a leitura do decreto indica que não).
- Licença de uso comercial do CNEFE e condições do DNE para SaaS multi-tenant.
- Procedimento do BPC para a tarifa social, ainda não definido segundo a ANA; situação do Pix Parcelado, sem norma localizada.
- Alcance da LAI por natureza jurídica do tenant e eventual norma municipal própria.
- Valores de tabelas de preço de Sabesp, Saneago e SAMAEs vieram de PDFs de versões distintas; servem como ordem de grandeza e não como referência numérica.
- Resultados de nudges e de modelos de propensão vêm de ensaios, consultoria e fabricantes; não substituem piloto próprio.

### 5.3 Limites desta análise

- 20b, 20c, 20e, 21a, 21b, 18e e 18g foram consultados por palavra-chave. Achados que tocam cobrança, faturamento e portal (CAD-73, 75, 90, 96, 97, 100) devem ser reconferidos contra 20b, 20c e 20e antes de virarem tarefa.
- `ARQUITETURA-ALVO.md` e o ADR-0041 não foram lidos por inteiro.
- Pré-pago foi avaliado e não é recomendado: o Banco Mundial relata medidor cerca de quatro vezes mais caro, falhas e risco de fraude (WB-PREPAID). OCR de foto de hidrômetro em autoleitura ficou fora por não haver caso brasileiro de concessionária localizado.
- Nenhum dado pessoal real foi usado; nenhuma chave nem variável de ambiente foi copiada.

### 5.4 Recomendações que dependem de cartão do dono do produto

Registradas apenas como recomendação, sem presunção de decisão.

- Login gov.br (CAD-101) e pagamento sem conta (CAD-97): alteram o ADR-0022.
- Subir o núcleo da importação do CadÚnico da ED-57 para a P1.5 (CAD-91).
- Subir o núcleo do motor de mensagens legais da ED-79 para o P2 (CAD-96).
- Abrir novas ED na onda B para o fluxo do empreendedor (CAD-71) e para o esgoto não doméstico (CAD-70).
- Padrão de fábrica ligado do cadastro de usuário vulnerável (CAD-80) e do tratamento de unidade vaga (CAD-68), em coerência com o ADR-0038.
