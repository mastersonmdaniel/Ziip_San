# 22d — Lacunas e inovações: gestão, financeiro, regulatório e inovação de mercado

Análise da frente de gestão, financeiro, regulatório e inovação de mercado do Ziip SAN, sobre o plano v2.17 (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`), os ADRs 0001 a 0041, `PARAMETROS-DO-TENANT.md`, `ARQUITETURA-ALVO.md` e os levantamentos 08 a 14, 18, 20a a 20g e 21a e 21b. Posição em 22/10/2026. O documento só recomenda: nenhuma decisão do dono do produto é presumida, e as escolhas seguem por cartão. Ele não reabre decisão aceita.

## 0. Leitura rápida

1. Foram registrados 34 achados: 12 ausentes do plano (A), 11 presentes mas rasos (B) e 11 inovações de mercado (C).
2. A maior lacuna de gestão é a tarifa como processo. O plano tem tabelas de tarifa com vigência e o parâmetro `bill.tariff_readjust`, mas nenhuma tarefa de reajuste, revisão, cópia de versão e simulação de impacto (GES-01).
3. A segunda é o pacote regulatório de saída por agência (layouts, periodicidade, subsídios, índice de tarifa social) e a agenda de obrigações do tenant (GES-04, GES-05), que o levantamento 08 marca como "não atende" e nenhuma tarefa assume.
4. Metas de universalização por município, indicadores da NR 8 e da NR 9 e o instrumento contratual do prestador (contrato de programa, concessão, PPP) não têm entidade nem tarefa (GES-02, GES-03).
5. A reforma tributária está em uma tarefa de tamanho P (P2.4). Cashback na fatura, split payment, fato gerador e reequilíbrio contratual (LC 214, arts. 373 a 377) ficam sem dono (GES-13).
6. Pix Automático e cobrança híbrida (Res. BCB 587) têm tarefas, mas sem as regras operacionais do Banco Central no aceite (GES-15).
7. Transparência ativa, LAI e dados abertos não aparecem no plano (GES-06). Custo por m³, margem por categoria e projeção de receita também não (GES-09, GES-10).
8. Entre as inovações, as de maior retorno e menor risco são a explicação automática da conta (GES-26) e a analítica de fraude e perdas aparentes em regras (GES-25). Previsão de inadimplência (GES-24) só com a política de IA aceita antes (GES-08).
9. Gêmeo digital, Open Finance de dados e antecipação de recebíveis não devem entrar como construção própria (GES-31, GES-33, GES-34).
10. Nenhum arquivo além deste foi alterado; o diário de bordo não foi atualizado por restrição do briefing.

## 1. Método e fontes

### 1.1 Método

**Leitura do que já está decidido.** O plano foi lido por seção e por busca de palavra-chave e de ID de tarefa, sem leitura integral. Foram lidos: §4 inteiro (P0 a P4, trilha de editais, LC); ADR README, 0019, 0027, 0028 e 0038; `PLATAFORMA-E-VERTICAIS.md`; o catálogo de `PARAMETROS-DO-TENANT.md` (prefixos de chave contados por script); `CENTRAL-ZIIP.md` (abas Regulatório e Radar); levantamentos 08 (quadro R-01 a R-42), 14, 18e §2.1, 18 (consolidado), 20g, 10 (PUB-01 a PUB-04), 13 (AS e TR) e trechos de 11, 20f, 21a e 21b.

**Regra de classificação.** Antes de apontar ausência, a busca usou sinônimos em português e em inglês no plano, nos ADRs, nos parâmetros e nos levantamentos. Só foi classificado como (A) o que não aparece em tarefa do plano; (B) o que aparece sem o detalhe que a norma ou o mercado pede; (C) o que o mercado oferece e o produto não prevê. Achado que o levantamento já listou como "não atende", mas que o plano não assumiu, é (A), com a remissão ao levantamento. Termos pesquisados sem acerto no plano: `reajuste`, `revisão tarifária`, `NR 8`, `NR 9`, `universalização`, `LAI`, `transparência`, `cashback`, `split payment`, `Open Finance`, `gov.br`, `gêmeo digital`, `escassez`, `sazonal`, `benchmark`, `marketplace`, `previsão de receita`, `custo por m³`, `score`, `propensão`, `webhook` de saída.

**Pesquisa externa.** Três subagentes pesquisaram (i) mercado e utilities digitais, (ii) regulação, contabilidade pública, tarifas e reforma tributária e (iii) pagamentos, IA, medição inteligente, regulação por incentivo e dados públicos. Eu abri diretamente, para conferência, as fontes marcadas **V** abaixo. As marcadas **R** foram relatadas por subagente e não foram reabertas por mim. O buscador Brave ficou indisponível (credencial recusada pelo serviço); usei Perplexity, WebSearch e WebFetch. Dados de fornecedor, imprensa e consultoria estão assim identificados. O que não foi confirmado em fonte primária leva a marca [INFERÊNCIA].

**Tamanho e maturidade.** Esforço relativo P, M e G como no plano §4 (P até 2 dias, M até 1 semana, G mais de 1 semana, para uma pessoa com agentes); sem datas de prazo nem equipe. Maturidade da tecnologia: pesquisa, piloto, produção restrita ou produção ampla.

### 1.2 Fatos de mercado e normativos usados

| Tema | Fato | Fonte | Verif. |
|---|---|---|---|
| Cobrança híbrida | Res. BCB 587: só Pix Cobrança com vencimento; boleto comum ou dinâmico sem ativo financeiro; o emissor do boleto é o PSP do recebedor; Pix cancelado quando o boleto é pago; oferta facultativa; vigência em 01/02/2027 | https://www.bcb.gov.br/api/servico/sitebcb/notasImprensa/21262 | V |
| Revisão tarifária | Minuta de NR sobre revisão tarifária (CP 03/2026): receita requerida, custos eficientes, investimentos prudentes, remuneração do capital e eventos extraordinários; contribuições até 02/10/2026; norma não aprovada | https://www.gov.br/ana/pt-br/assuntos/noticias-e-eventos/noticias-periodo-eleitoral-2026/consulta-publica-sobre-revisao-tarifaria-no-saneamento-e-prorrogada-ate-2-de-outubro | V |
| Fatores Q, P e U | Minuta com fator Q (qualidade), P (perdas) e U (universalização) (verificado em 22/10/2026: a menção a fatores Q, P e U não foi encontrada nas fontes lidas; a Nota Técnica 14/2026/COTAR/SSB da CP 03/2026 discute, entre outros temas, o fator X de ganho de produtividade, o custo de capital de terceiros, a remuneração de investimentos e as receitas acessórias) | https://participacao-social.ana.gov.br/api/files/SEI_0207737_Minuta_de_Resolucao-1785521328204.pdf | R |
| SINISA 2026 | Portaria MCID 1.069/2025; coleta de 13/05 a 03/09/2026; versão preliminar até 03/11; validação até 17/11; Certificado de Adimplência até 31/12; módulos Abastecimento de Água e Esgotamento Sanitário com ambientes local e regional; Gestão Municipal pela prefeitura; a página não indica API e informa que não haverá prazos adicionais | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/area-do-prestador | V |
| Reforma tributária | LC 214/2025: cashback de 100% da CBS e 20% do IBS em abastecimento de água e esgotamento sanitário (arts. 117 e 118); reequilíbrio de contratos (arts. 373 a 377); saneamento fora da redução de 60% (leitura doutrinária) | https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp214.htm ; https://apet.org.br/artigos/reforma-tributaria-e-o-desequilibrio-das-concessoes-e-ppps-de-saneamento/ | V (texto parcial) |
| Receitas e subsídios | Lei 11.445, art. 10-A II (contratos preveem receitas alternativas, complementares ou acessórias, com compartilhamento), art. 29 §2º e art. 31 (subsídios tarifários, fiscais e internos); NT.F-0083-2020 da ARSESP (Receitas Alternativas: classificação e critérios de compartilhamento, voltada à Sabesp) distingue receitas complementares, acessórias, projetos associados e receitas de sociedades de propósito específico | https://www2.camara.leg.br/legin/fed/lei/2007/lei-11445-5-janeiro-2007-549031-publicacaooriginal-64311-pl.html ; https://normas.leg.br/?urn=urn:lex:br:federal:lei:2020-07-15;14026 ; http://www.arsesp.sp.gov.br/ConsultasPublicasBiblioteca/NT.F-0083-2020.pdf | V |
| NR da ANA | 16 NR publicadas; NR 16/2026 (Res. ANA 302/2026) institui MCRSB e MCPSB; ERIs têm 3 anos para adotar e prestadores até 2032; NR 13/2025 (Res. 271/2025) trata de estrutura tarifária e tarifa social; NR 15/2025 (Res. 275/2025) trata de perdas | https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA ; https://www.gov.br/ana/pt-br/legislacao/resolucoes/resolucoes-regulatorias | R |
| Pix Automático | Res. BCB 402/2024 (criação), 482/2025 (recebedor PJ com CNPJ ativo há 6 meses ou mais); Res. CMN 5.251/2025 (altera a Res. CMN 4.790/2020) e BCB 505/2025 (altera a Res. BCB 51/2020, contas de pagamento pré-pagas): débito automático interbancário com recebedor pessoa jurídica ou entidade não autorizada a funcionar pelo BCB pelo Pix Automático; convênios de serviço público com prazo até 01/01/2027 (Res. CMN 5.278/2025, fundamentada no Voto CMN 92/2025, e Res. BCB 544/2025); instrução de 2 a 10 dias antes da liquidação; retentativas até 3 em 7 dias se constarem da autorização | https://normativos.bcb.gov.br/Votos/CMN/202592/Voto_do_CMN_92_2025.pdf ; https://www.bcb.gov.br/content/estabilidadefinanceira/pix/automatico/guia_pix_automatico.pdf ; https://www.bcb.gov.br/content/estabilidadefinanceira/pix/pix-automatico-FAQ-participantes.pdf | R |
| Open Finance | JSR obrigatória para detentoras de conta desde 22/04/2026 (Res. BCB 406/2024, cujo cronograma do art. 6º, II foi reescrito pela Res. BCB 541/2025); iniciação de pagamento é opcional e restrita a instituições autorizadas; IN BCB 746/2026 (de 16/06/2026) retirou o teto de R$ 500 do Pix por aproximação em 01/10/2026 (verificado em 22/10/2026: a IN revoga o art. 16-A da IN BCB 512/2024, que fixava o teto, sem estabelecer novo valor; os limites do art. 3º e o limite noturno continuam aplicáveis; a IN cobre também a iniciação sem redirecionamento da Res. BCB 406/2024, não só o pagamento por aproximação) | https://wama.com.br/blog/jornada-sem-redirecionamento-no-app-da-fintech ; https://revistane.com.br/2026/06/21/pix-por-aproximacao-ganha-mais-flexibilidade-e-deixa-de-ter-teto-fixo-de-r500/ | R |
| NFAg | NFAg (modelo 75, instituída pelo art. 114 do Decreto 12.955/2026 e pelo regulamento do IBS; não há Ajuste SINIEF instituidor) obrigatória a partir dos fatos geradores de 01/12/2026 (Ato Conjunto RFB/CGIBS 4/2026, de 30/07/2026, art. 1º, XVI); Ato Técnico Conjunto 2/2026 aprovou MOC, leiaute e DANFAG; Águas do Rio já traz NFAg na fatura desde julho de 2026 | https://dfe-portal.svrs.rs.gov.br/Nfag ; https://www.nfe.fazenda.gov.br ; https://www.aguasdorio.com.br/faturas-da-aguas-do-rio-passam-a-ter-mais-detalhes-a-partir-deste-mes/ | R |
| Perdas e faturamento | SINISA 2025 (ano-base 2024): perdas na distribuição de 39,53%, 349,09 l por ligação por dia; valor de perda de faturamento de 39,1% vem de fonte secundária única; custo estimado das perdas comerciais de R$ 12,4 bilhões (dados de 2022) | https://tratabrasil.org.br/wp-content/uploads/2026/06/Release-Perdas-de-Agua-2026-SINISA-2024-v2.pdf ; https://tratabrasil.org.br/wp-content/uploads/2024/06/Estudo-da-GO-Associados-Perdas-de-Agua-de-2024.pdf | R |
| Fraude | Sabesp: 52.537 irregularidades detectadas em 2025 contra 25.793 em 2024; Copasa estima 300 mil ligações fraudulentas e cerca de 40% das perdas por fraude (dado da empresa) | https://news.copasa.com.br/copasa-promove-acoes-para-o-combate-de-ligacoes-clandestinas/ ; dado da Sabesp relatado por imprensa (Estadão e Revista Galileu, 2026), sem URL completa recuperada | R |
| IA em cobrança | Grupo Águas do Brasil com a Grão Inteligência: 36 meses de dados, mais de 7 milhões de ações de cobrança por mês, clusterização e machine learning; caso publicado pelo próprio fornecedor | https://graointeligencia.com.br/cases/analytics-aplicado-ao-saneamento-personalizando-a-regua-de-cobranca-para-reduzir-inadimplencia/ | R |
| IA em relacionamento | Oracle Utilities: líder em IDC MarketScape de gestão de experiência do cliente com IA (propensão a pagar, otimização de cobrança, assistente que explica a fatura); Copasa e Sabesp com WhatsApp e IA generativa | https://www.oracle.com/utilities/ ; https://news.copasa.com.br/ia-no-atendimento-via-whatsapp-e-webchat-da-copasa-proporciona-melhorias-para-a-jornada-do-cliente/ | R |
| Medição inteligente | Sabesp: 4,4 milhões de hidrômetros inteligentes, NB-IoT, consumo horário do dia anterior no app; Anglian Water: 1,4 milhão de medidores, fluxos contínuos detectados passaram de cerca de 9 mil para mais de 170 mil por ano, redução de 14,75 l por domicílio por dia | https://www.agenciasp.sp.gov.br/meio-ambiente/sabesp-inicia-o-maior-projeto-de-hidrometros-inteligentes-do-mundo-reforcando-o-uso-consciente-da-agua-em-periodo-de-estiagem/ ; https://swan-forum.com/case-studies/paving-the-way-for-a-resilient-future-in-eastern-england/ | R |
| Comparação social | Ensaios aleatorizados de comparação com pares: cerca de 3% a 5% (WaterSmart e Opower); cerca de 2% no Reino Unido, com carta em papel e sem efeito do e-mail; efeito decai | https://www.danielbrent.com/assets/papers/brent_cook_olsen_social_comparisons_wp.pdf ; https://ore.exeter.ac.uk/rest/bitstreams/103728/retrieve | R |
| Regulação por incentivo | Ofwat PR24: 24 compromissos comuns e 7 específicos com incentivos financeiros; contrato Sabesp/Arsesp com fatores Q e U e verificador independente (Anexo VI) | https://www.ofwat.gov.uk/wp-content/uploads/2024/12/7.-PR24-final-determinations-Delivering-outcomes-for-customers-and-the-environment-1.pdf ; https://semil.sp.gov.br/desestatizacaosabesp/wp-content/uploads/sites/24/2024/05/Anexo-VII.pdf | R |
| IA e regulação | PL 2338/2023 (de origem no Senado Federal) aguarda parecer do relator na Comissão Especial da Câmara; o texto enviado à Câmara classifica como alto risco, entre outras hipóteses, a avaliação de critérios de acesso, elegibilidade e concessão de serviços essenciais (art. 14, IV) e a segurança de infraestruturas críticas, como redes de água (art. 14, I); verificado em 22/10/2026: não há item específico sobre avaliação de crédito; LGPD art. 20 garante revisão de decisão automatizada | https://www.camara.leg.br/proposicoesWeb/fichadetramitacao/?idProposicao=2487262 ; https://www.lgpd-brasil.info/capitulo_03/artigo_20 | R |
| CadÚnico | Prestador de direito privado recebe os dados pela ERI; só prestador de direito público pode receber direto; não há API de consulta de CadÚnico para prestador privado | https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/acesso-e-gestao-dos-dados-do-cadunico-e-bpc/orientacoes-para-acesso-aos-dados-do-cadunico-1 | R |
| Preços de mercado | Fiorilli, DAEP de Dracena (contrato 017/2025): gestão comercial de água e esgoto a R$ 5.958,13 por mês; Betha, SAMAE de São Ludgero: módulos em nuvem de R$ 118,64 a R$ 907,57 por mês e hora técnica de R$ 190 a R$ 297 | https://www.emdaep.com.br/contrato/execucao_0.pdf ; https://www.samaesl.sc.gov.br/uploads/11_-_homologacao_dl_27-25.pdf | R |
| TCE-SC | e-Sfinge pela IN TC-28/2021, alterada pelas IN TC-31/2022, TC-35/2024 e TC-39/2025; remessa mensal dos módulos Registros Contábeis e Tributário desde 01/01/2022 (art. 12, na redação da IN TC-31/2022; verificado em 22/10/2026: não é bimestral para esses módulos; a IN TC-28/2021 é de 08/03/2021 e seu texto original não foi aberto) | https://www.tcesc.tc.br/esfinge/informacoes | R |
| Tesouro | Siconfi: RREO bimestral, RGF quadrimestral, MSC mensal, DCA anual; PCASP 2027 pela Portaria STN/MF 2.520/2026 | https://siconfi.tesouro.gov.br | R |
| ESG | CVM Res. 244/2026 (altera a Res. CVM 193/2023) retirou a obrigatoriedade de divulgação de sustentabilidade prevista para exercícios a partir de 2026 e manteve o regime voluntário (pratique ou explique); Decreto 12.705/2025 (Taxonomia Sustentável Brasileira); Lei 15.042/2024 (SBCE) | https://conteudo.cvm.gov.br/export/sites/cvm/legislacao/resolucoes/anexos/200/resol244.pdf ; https://www.gov.br/fazenda/pt-br/orgaos/spe/taxonomia-sustentavel-brasileira | R |

A tabela traz os fatos que sustentam os achados. A lista completa das URLs relatadas pelos subagentes está nas seções de detalhamento, no campo "Fontes" de cada achado.

## 2. Quadro-resumo dos achados

Classe: A ausente do plano; B presente mas raso; C inovação. Esforço: P, M ou G. "Parâm." indica se vira parâmetro do tenant (ADR-0038).

| ID | Cl. | Achado | Prior. | Esf. | Parâm. | Encaixe sugerido |
|---|---|---|---|---|---|---|
| GES-01 | A | Gestão tarifária: reajuste, revisão, cópia de versão e simulação de impacto | Alta | G | Sim | Tarefa nova após a P2.5; corrige a remissão da P1.6 |
| GES-02 | A | Instrumento contratual regulatório (contrato de programa, concessão, PPP), aditivos e matriz de riscos | Média | M | Sim | Tarefa nova na onda B, ligada à ED-56 |
| GES-03 | A | Metas de universalização e indicadores das NR 8 e NR 9 por município | Alta | M | Sim | Ajusta ED-48 e ED-77 |
| GES-04 | A | Pacote regulatório de saída por agência, com subsídios e índice de tarifa social | Alta | G | Sim | Tarefa nova na onda A ou B, após a P3.22 |
| GES-05 | A | Agenda de obrigações regulatórias e de controle do tenant | Média | M | Sim | Tarefa nova; usa a P3.15 e a ED-07 |
| GES-06 | A | Transparência ativa, LAI e dados abertos | Média | M | Sim | Tarefa nova opcional por natureza jurídica |
| GES-07 | A | Contingência hídrica e tarifa sazonal | Média | M | Sim | Ajusta a P2.5 e a GES-01 |
| GES-08 | A | Política de IA e registro de decisões automatizadas | Alta | P | Sim | ADR novo antes de qualquer GES-24 a GES-26 |
| GES-09 | A | Custo por m³ e por ligação, margem por categoria e custo total da OS | Média | M | Sim | Ajusta ED-32, ED-36 e ED-48 |
| GES-10 | A | Projeção de receita, arrecadação e caixa por cenário | Média | M | Sim | Tarefa nova sobre ED-32 e ED-47 |
| GES-11 | A | API pública, webhooks de saída e catálogo de conectores de parceiros | Média | M | Sim | Tarefa nova; liga P1.6, P0.10 e ED-62 |
| GES-12 | A | Obras e convênios de investimento (Transferegov e afins) | Baixa | G | Não | Decisão do dono sobre o recorte |
| GES-13 | B | Reforma tributária além da alíquota: cashback, split payment, fato gerador, reequilíbrio | Alta | M | Sim | Amplia P2.4 e P2.5 |
| GES-14 | B | Contabilidade regulatória da NR 16 (MCRSB) e de-para com o plano do cliente | Média | G | Sim | Amplia ED-32 e ED-56 |
| GES-15 | B | Pix Automático e cobrança híbrida: regras operacionais do Banco Central | Alta | M | Sim | Amplia P3.1, P3.2 e P4.5 |
| GES-16 | B | SINISA: ciclo completo, mapa de variáveis e certificado de adimplência | Média | P | Sim | Amplia P4.2 e ED-48 |
| GES-17 | B | Receitas alternativas, complementares e acessórias, com compartilhamento | Média | M | Sim | Amplia ED-34 |
| GES-18 | B | Dívida ativa: ajuizamento, alçada e prescrição | Baixa | P | Sim | Amplia ED-31 |
| GES-19 | B | Mensageria: custo por categoria de template e uso medido do SaaS | Média | P | Sim | Amplia ED-10, ED-41 e ED-42 |
| GES-20 | B | Risco e continuidade do prestador: incidente operacional, resiliência climática | Baixa | M | Parcial | Amplia P3.31 e ED-79 |
| GES-21 | B | ESG e energia: kWh por m³, inventário simplificado de emissões | Baixa | M | Sim | Amplia ED-77 |
| GES-22 | B | Perfil de acesso externo do regulador e do titular, somente leitura | Média | M | Sim | Amplia ED-75 e ED-04 |
| GES-23 | B | Ganchos de integração para compras, RH e orçamento (2029) | Baixa | P | Não | Ajusta P3.30, ED-58 e ED-37 |
| GES-24 | C | Previsão de inadimplência e régua de cobrança adaptativa | Média | G | Sim | Depois de P4.1 e ED-29, com a GES-08 |
| GES-25 | C | Analítica de fraude, ligação clandestina e perdas aparentes | Alta | M a G | Sim | Amplia ED-24, com ED-14 e ED-17 |
| GES-26 | C | Explicação automática da variação da conta e copiloto do atendente | Alta | P a M | Sim | Amplia ED-35 e ED-43 |
| GES-27 | C | Consumo quase em tempo real e alertas ao consumidor (AMI) | Média | G | Sim | Amplia ED-17, ED-39 e ED-41 |
| GES-28 | C | Metas de consumo, comparação com pares e gamificação | Baixa | M | Sim | Tarefa nova opcional |
| GES-29 | C | Benchmark com o SINISA aberto e, depois, entre tenants | Média | M | Sim | Amplia ED-48 e ED-49 |
| GES-30 | C | Regulação por incentivo: apuração auditável e simulador de fatores Q, P e U | Média | M | Sim | Amplia ED-77 e ED-48 |
| GES-31 | C | Gêmeo digital: integração, não construção | Baixa | P | Não | Amplia ED-63 |
| GES-32 | C | Login gov.br como provedor de identidade opcional no portal | Baixa | M | Sim | Amplia P3.3 |
| GES-33 | C | Open Finance (iniciação) e Pix por aproximação via PSP | Baixa | P | Sim | Amplia P3.2 |
| GES-34 | C | Antecipação de recebíveis e conta vinculada | Baixa | M | Não | Decisão do dono; não recomendada agora |

## 3. Detalhamento por achado

### 3.1 Classe A: ausentes do plano

#### GES-01 Gestão tarifária: reajuste, revisão, cópia de versão e simulação de impacto (A)

**Descrição.** Processo completo de mudança de tarifa: data-base, índice (IPCA ou fórmula paramétrica com fator X, conforme a NR 10 e a NR 6), publicação com no mínimo 30 dias de antecedência, homologação tácita quando a norma da agência a prevê, geração da nova versão de tabela a partir do ato, cópia de uma versão existente como ponto de partida, comparação entre tarifa vigente e proposta, simulação sobre a base real (receita por categoria e faixa, fatura-tipo por perfil de consumo, efeito na tarifa social e no subsídio) e histórico público de 5 anos com o método de cálculo. A revisão periódica (receita requerida, base de remuneração, fatores Q, P e U) é objeto de minuta da ANA ainda não aprovada (verificado em 22/10/2026: a menção a fatores Q, P e U não foi encontrada nas fontes lidas; a Nota Técnica 14/2026/COTAR/SSB da CP 03/2026 discute, entre outros temas, o fator X de ganho de produtividade, o custo de capital de terceiros, a remuneração de investimentos e as receitas acessórias).

**Por que importa.** A tarifa é a receita do prestador. Erro de vigência ou de publicação gera nulidade do reajuste, e a reforma tributária abre pleitos de reequilíbrio (GES-13) que precisam da mesma simulação. A transparência tarifária (tabela vigente com evolução de 5 anos, método e canais de contestação) está na NR 13 (arts. 61 e 62).

**O que o plano já tem.** Tabelas `tariffs`, `tariff_versions` (com `EXCLUDE` contra sobreposição), `tariff_category_rules` e `tariff_blocks` (`V4_1`); parâmetro `bill.tariff_readjust` (data-base, índice, ato, publicação; vigência mínima de publicação mais 30 dias, travada), que o catálogo marca como "parcial: `regulatory_act`, `published_on`"; P0.19 e P0.20 (aprovação e limites); P2.5 (ciclo e conta); ED-38 (tabela tarifária no portal). O levantamento 08 marca R-13 e R-20 como "não atende". A matriz 18e §2.1 (TR-283) aponta a P1.6 como dona de "tarifa com simulação aprovada", mas o texto da P1.6 no plano não traz essa entrega, e TR-290 (nova tabela a partir de existente) consta como "não planejado".

**Onde encaixa.** Tarefa nova de gestão tarifária, na fase P2 depois da P2.5, com migration própria no módulo de faturamento (`V4_x`), dependente de P0.19, P2.5 e do núcleo de relatórios da P3.22 (simulação lida na réplica). Corrigir a remissão da P1.6 em 18e. Teste: reajuste com publicação em menos de 30 dias recusado; simulação reproduz a conta da P2.5 para a mesma massa.

**Prioridade e esforço.** Alta; G.

**Parâmetro do tenant.** Sim. Ampliar `bill.tariff_readjust` com fórmula, índice, fator X, regra de homologação tácita e prazo de publicação (classe d, aprovação REG; o prazo mínimo de 30 dias segue travado). Chaves novas a propor, por exemplo `tariff.readjust_formula`, `tariff.tacit_approval_days`, `tariff.history_years_public`.

**Fontes.** Lei 11.445, arts. 29, 30 e 39 (V); NR 10/2024 e NR 13/2025 da ANA; levantamento 08 R-13 e R-20; CP 03/2026 da ANA (V, URL na seção 1.2).

#### GES-02 Instrumento contratual regulatório do prestador (A)

**Descrição.** Registro, por município e por serviço, do instrumento sob o qual o tenant presta: contrato de programa, concessão, PPP, subdelegação ou prestação direta com agência. Campos: partes, ato de regulação, vigência, metas contratuais, aditivos (a NR 8 trata dos aditivos de metas), matriz de riscos (NR 5), indenização de investimentos não amortizados (NR 3), cláusula de reequilíbrio e documentos. Opcional: painel dos indicadores do Decreto 11.598/2023 (margem líquida sem depreciação e amortização maior que zero, endividamento menor ou igual a 1, retorno sobre o patrimônio líquido maior que zero, suficiência de caixa maior que 1), calculado a partir da exportação do ERP do cliente.

**Por que importa.** O Novo Marco amarra metas, tarifa e indenização ao instrumento. Sem o cadastro dele, as metas (GES-03), o reequilíbrio (GES-13) e a matriz de riscos não têm onde viver, e a prestação de contas ao titular e à agência fica fora do sistema. O Decreto 11.598 não se aplica à prestação direta [INFERÊNCIA, a partir de fonte setorial], então o painel só serve aos tenants sob contrato.

**O que o plano já tem.** ADR-0019 e P2.12 tratam do contrato do SaaS (Ziip e tenant), não do contrato regulatório do tenant. O grupo G1 traz `reg.regulator`, `reg.legal_nature` e `reg.service_area`. A ED-56 cobre bens reversíveis (NR 3). O levantamento 08 R-38 trata a capacidade econômico-financeira como "fora do escopo do MVP".

**Onde encaixa.** Entidade `regulatory_contract` na onda B, ligada à ED-56 (bens reversíveis), à GES-03 (metas) e à GES-13 (pleito de reequilíbrio). A matriz de riscos da NR 5 é o primeiro uso concreto.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** O instrumento é dado cadastral. Viram parâmetros as opções de tratamento: `reg.contract_type` (enum), `reg.risk_matrix_required` (fato declarado, classe d, REG).

**Fontes.** Lei 14.026/2020 (https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2020/lei/l14026.htm) (R); Decreto 11.598/2023 (https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2023/decreto/d11598.htm) (R); NR 3, NR 5 e NR 8 da ANA (R).

#### GES-03 Metas de universalização e indicadores das NR 8 e NR 9 por município (A)

**Descrição.** Meta contratual versionada por município e ano (99% de água e 90% de coleta e tratamento até 31/12/2033, com prorrogação até 2040 só com anuência da agência), cálculo de IAA e ICA por município, verificação anual com cumprimento em pelo menos 3 de 5 anos, trajetória até a meta (ligações a incorporar por ano), indicadores da NR 9 níveis I e II (IAA, ICA, IAE, ICE, perdas por ligação, intermitência, micromedição, macromedição, duração de reparo de extravasamento, reclamações) e metas de perdas (Portaria MCID 788/2024).

**Por que importa.** A meta de universalização é verificada por município, e a meta só conta quando IAA e ICA estão em 99% ao mesmo tempo (levantamento 08 R-35). O atingimento condiciona acesso a recursos federais e aditivos.

**O que o plano já tem.** ED-48 (catálogo SINISA, indicadores parametrizáveis com metas, fechamento e semáforo), ED-77 (ficha de indicador com variáveis, base de domicílios do IBGE para cobertura e atendimento), ED-78 (balanço de perdas pela NR 15), P4.2 (SINISA). O plano não cita NR 8 nem NR 9 (busca sem acerto), e não há tabela de meta por município e ano (`loss_target`, R-33).

**Onde encaixa.** Ajuste da ED-48 e da ED-77: entidade de meta com origem (contrato, NR, plano municipal), cálculo de IAA e ICA sobre a base de domicílios da ED-77, verificação 3 de 5 e alerta de desvio pela ED-07. Depende da GES-02.

**Prioridade e esforço.** Alta; M.

**Parâmetro do tenant.** Sim: `goal.universalization_*`, `loss.target_*` com origem e vigência (classe d, REG; meta federal travada como piso).

**Fontes.** Lei 11.445, art. 11-B (V); NR 8/2024 (Res. ANA 192/2024) e NR 9/2024 (Res. ANA 211/2024) (R); levantamento 08 R-33 a R-35; Portaria MCID 788/2024 (valores a conferir).

#### GES-04 Pacote regulatório de saída por agência, com subsídios e índice de tarifa social (A)

**Descrição.** Camada de exportação parametrizada por agência: layout, periodicidade, canal, versão do leiaute e registro de envio (hash), no modo assistido do ADR-0004. Conteúdo: consumo médio por categoria e faixa, receita e usuários subsidiados, custo do subsídio (tarifário, fiscal ou interno, Lei 11.445 art. 31), contratos de grandes usuários, taxa de inadimplência por categoria, relatório anual de beneficiários da tarifa social e Índice de Tarifa Social (NR 13, art. 45).

**Por que importa.** As agências divergem: o levantamento 08 R-40 registra ARSAE-MG (bases mensais e anuais, até o dia 25), AGERGS (trimestral, CSV UTF-8), ARSESP (base trimestral de tarifa social), AGENERSA, AGEPAR e ADASA (anual, até 20 de janeiro). Para Santa Catarina, ARIS, AGIR e ARESC não têm layout levantado no plano; a pesquisa desta rodada não localizou normas próprias da AGIR.

**O que o plano já tem.** P3.22 (relatórios nativos: art. 54 da NR 11, tarifa social e isenções, NFAg-e), P1.5 e `social.annual_report` (destinatário, data e formato), P2.24 (declaração anual de quitação), P4.2 (SINISA), ED-46 (catálogo). Falta a camada de saída por agência (R-21 e R-40 estão "não atende"), o relatório de custo do subsídio e o Índice de Tarifa Social (R-15).

**Onde encaixa.** Tarefa nova na onda A ou B, depois da P3.22, dependente de P1.5 e ED-48, com um perfil de saída por agência pré-carregado (ADR-0038, item 4). O esforço cresce por agência acrescentada.

**Prioridade e esforço.** Alta; G (incremental por agência).

**Parâmetro do tenant.** Sim: `reg.reports` (mapa agência, relatório, periodicidade, canal, leiaute), classe d, REG. A separação entre exportação agregada pública e nominal restrita segue o levantamento 10 (PUB-03).

**Fontes.** Levantamento 08 (R-15, R-21, R-40); levantamento 10 (PUB-03); Lei 11.445, art. 31 (V); NR 13/2025 (R).

#### GES-05 Agenda de obrigações regulatórias e de controle do tenant (A)

**Descrição.** Calendário por tenant, gerado de `reg.*` e da natureza jurídica: ciclo anual do SINISA, relatório anual de tarifa social, declaração anual de quitação (fatura de maio), balanço de perdas da NR 15, remessa bimestral ao e-Sfinge (IN TC-28/2021 e alterações), DMR e SISAGUA, publicação de reajuste (data-base menos 30 dias), RREO e RGF do ente quando aplicável. Cada item com responsável, evidência, status e lembrete.

**Por que importa.** Hoje cada obrigação aparece dentro do seu módulo. O gestor do prestador precisa de uma visão única do que vence e do que foi cumprido, e a falta gera multa (o TCE-SC aplica multa por atraso de remessa ao gestor, segundo a pesquisa) e perda de acesso a recursos federais.

**O que o plano já tem.** A aba Regulatório da Central (P3.15) mostra, para a plataforma, parâmetros com `valid_to` em 60 dias, feriados ausentes, tarifa social sem conferência no DOU e janela do SINISA assistido; a aba Radar acompanha normas. ED-07 gera alertas de evento de negócio. Não há agenda voltada ao tenant.

**Onde encaixa.** Tarefa nova leve, usando a ED-07 para o lembrete e o catálogo de normas da P3.15 como fonte das regras.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `reg.obligations` (conjunto ativo por agência e natureza jurídica), classe d.

**Fontes.** Portaria MCID 1.069/2025 (V); IN TC-28/2021 e alterações (R, https://www.tcesc.tc.br/esfinge/informacoes); Siconfi (R, https://siconfi.tesouro.gov.br).

#### GES-06 Transparência ativa, LAI e dados abertos (A)

**Descrição.** Módulo opcional por natureza jurídica do tenant: Serviço de Informação ao Cidadão (pedido pelo sítio, 20 dias prorrogáveis por 10, recurso em 10 dias e resposta em 5, relatório estatístico anual), transparência ativa (competências, canais, tarifas, contratos, carta de serviços, indicadores) e exportação em dados abertos legíveis por máquina, sem dado pessoal. O padrão é integrar com o sistema de ouvidoria ou de acesso do município por link.

**Por que importa.** Autarquias e empresas públicas se sujeitam à LAI. Os levantamentos 10 (PUB-02) e 13 (AS-06, TR-01, TR-02) registram "não atende" e propõem módulo opcional; nenhuma tarefa o assumiu. O alcance da LAI por natureza jurídica do tenant não está confirmado (21a).

**O que o plano já tem.** ED-35 cobre ouvidoria (Lei 13.460) e protocolo; ED-79 inclui Carta de Serviços; ED-38 publica tabela tarifária; P3.20 publica contrato de adesão e regulamento. Nada para o pedido de acesso (LAI) nem para dados abertos.

**Onde encaixa.** Tarefa nova opcional, na onda B ou C, desligada por padrão.

**Prioridade e esforço.** Média; M (P se limitado a link e dados abertos).

**Parâmetro do tenant.** Sim: `pub.lai_mode` (`NONE`, `EXTERNAL_LINK`, `NATIVE_SIC`), `pub.open_data_enabled`; classe d, JUR.

**Fontes.** Lei 12.527/2011, arts. 8º a 11, 15 e 30; Lei 13.303/2016, art. 8º; Lei 14.129/2021, arts. 29 e 38 a 41 (levantamentos 10 e 13; R).

#### GES-07 Contingência hídrica e tarifa sazonal (A)

**Descrição.** Adicional ou desconto tarifário por período declarado de escassez, com ato de autorização, faixas afetadas, isenção de unidades essenciais e comunicação na fatura, mais tarifa sazonal. A busca no plano e em `PARAMETROS-DO-TENANT.md` por `escassez`, `racionamento`, `sazonal` e `bandeira` não encontrou acerto.

**Por que importa.** Há precedente brasileiro: a ADASA fixou, em 2016, tarifa de contingência de 40% sobre o consumo acima de 10 m³ no residencial e 20% no residencial popular, com isenção de serviços essenciais (R, https://www.gov.br/ana/pt-br/todos-os-documentos-do-portal/documentos-sre/alocacao-de-agua/oficina-escassez-hidrica/legislacao-sobre-escassez-hidrica/distrito-federal/resolucao-no-20-2016-estabelecimento-tarifa-cont) (verificado em 22/10/2026: a Res. ADASA 20/2016, de 07/11/2016, trata do estado de restrição de uso dos recursos hídricos e do regime de racionamento nos sistemas Descoberto e Santa Maria e foi revogada pela Res. ADASA 13/2018; a isenção da tarifa de contingência para unidades essenciais consta da Res. ADASA 17/2016, de 07/10/2016, cuja situação não foi verificada). O levantamento 08 R-23 prevê o mecanismo (Lei 11.445, art. 46; NR 13, arts. 22 a 24). A proposta de agenda regulatória 2027-2028 da ANA (CP 04/2026) inclui norma de referência sobre mitigação de impactos das mudanças climáticas, adaptação e resiliência dos sistemas (item 9-032, tema novo, com início previsto apenas ao fim do biênio, segundo a proposta) (verificado em 22/10/2026: a condição de tema mais citado não foi comprovada, pois as fontes lidas não trazem dados de frequência de citação, e a imprensa destaca como temas centrais as normas de referência de tarifa para drenagem e resíduos sólidos e as outorgas e a segurança de barragens).

**O que o plano já tem.** Nada específico. O motor de tarifa da P2.5 tem faixas e vigência.

**Onde encaixa.** Ajuste da P2.5 e da GES-01: componente de contingência com vigência própria e regra de isenção.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `tariff.contingency_*` (ato, percentual, faixas, isenções), classe b com aceite; isenção de unidades essenciais travada.

**Fontes.** Levantamento 08 R-23; ADASA Res. 20/2016 (R; racionamento, revogada pela Res. ADASA 13/2018) e Res. ADASA 17/2016 (isenção da tarifa de contingência para unidades essenciais; situação não verificada); Regulamento ERSAR 1160/2026, Portugal, Regulamento Tarifário dos Serviços de Águas (R, https://diariodarepublica.pt/dr/detalhe/regulamento/1160-2026-1169230576) (verificado em 22/10/2026: as fontes secundárias lidas não mencionam tarifa de contingência nem isenção de unidades essenciais, mas sim tarifários sociais obrigatórios e a vedação de certos encargos; o texto integral não foi lido, e a existência de dispositivo sobre contingência permanece não confirmada).

#### GES-08 Política de IA e registro de decisões automatizadas (A)

**Descrição.** ADR de IA no Ziip SAN, anterior a qualquer modelo de previsão ou de ranking: finalidades permitidas; vedação de decisão exclusivamente automatizada sobre corte, negativação, tarifa social e negativa de serviço; revisão humana; registro de cada decisão ou recomendação (modelo, versão, entradas, saída, explicação); avaliação de viés; proibição de treinar modelo com dado de um tenant em benefício de outro sem contrato próprio; RIPD; homologação só com dado sintético (P0.45).

**Por que importa.** A LGPD (art. 20) garante revisão de decisão automatizada. O PL 2338/2023, de autoria do Senado Federal (Sen. Rodrigo Pacheco), está na Câmara dos Deputados como casa revisora, aguardando parecer na Comissão Especial; o texto enviado à Câmara classifica a avaliação de acesso a serviços essenciais como alto risco (art. 14, IV; verificado em 22/10/2026 no autógrafo de 31/01/2025, que lista 12 hipóteses e não traz item específico sobre crédito; a lista de alto risco será regulamentada pelo SIA, arts. 15 e 16). A Sanepar publicou política própria que veda IA generativa em usos de alto risco. O levantamento 14 §4.6 já adverte que o Ziip vira controlador ao usar dado de tenant para treino ou benchmark. As vedações de corte (Lei 14.015) e de condicionar a tarifa social à adimplência (Lei 14.898) são federais e travadas (ADR-0038, item 3).

**O que o plano já tem.** ED-43 (chatbot com funções fechadas, minimização e transbordo humano), ADR-0028 (c), P3.9 e P3.10 (DPA, RIPD).

**Onde encaixa.** ADR novo e tarefa P de cadastro de modelos e de registro de decisões, no núcleo (plataforma), antes das GES-24, GES-25 e GES-26 com modelo estatístico ou generativo.

**Prioridade e esforço.** Alta como precondição; P.

**Parâmetro do tenant.** Sim: `ai.enabled_features` (padrão desligado, classe b com aceite), `ai.human_review_required` (classe a, travado), `ai.decision_log_retention_months`.

**Fontes.** https://www.lgpd-brasil.info/capitulo_03/artigo_20 (R); https://www.camara.leg.br/proposicoesWeb/fichadetramitacao/?idProposicao=2487262 (R); https://ri.sanepar.com.br/docs/Sanepar-2025-12-11-96pqPMRJ.pdf (R); levantamento 14 §4.6.

#### GES-09 Custo por m³ e por ligação, margem por categoria e custo total da OS (A)

**Descrição.** Contabilidade gerencial: custo por m³ faturado, por ligação e por categoria, margem por categoria, custo do subsídio e custo total da OS (material, mão de obra, frota e terceiros). A mão de obra entra por tabela de custo-hora por função, sem módulo de RH.

**Por que importa.** O SINISA 2024 (ano-base 2023) mostra receita operacional média de R$ 5,38 por m³ contra despesa total média de R$ 6,31 por m³ (release do Ministério das Cidades, R, a partir de https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa; o PDF do release não foi reaberto), e a revisão tarifária exige custos eficientes. Sem o custo apropriado, o painel de indicadores fica só com receita e volume.

**O que o plano já tem.** ED-58 (custo apropriado na OS para o material), ED-59 (custo por veículo e consumo na OS), ED-37 (medição de empreiteiras), ED-32 (eventos contábeis e exportação), ED-56 (manutenção). Nenhuma tarefa consolida o custo nem calcula custo unitário.

**Onde encaixa.** Ajuste da ED-32 e da ED-36 (apropriação por OS) e da ED-48 (indicador de custo unitário), na onda B.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `cost.hourly_rates` (por função, com vigência) e `cost.allocation_method` (critério de rateio); classe d, FIN.

#### GES-10 Projeção de receita, arrecadação e caixa por cenário (A)

**Descrição.** Curva de recebimento por faixa de atraso, previsão mensal de arrecadação, cenários de reajuste, de inadimplência e de tarifa social, efeito do cashback (GES-13). Técnica estatística simples primeiro; aprendizado de máquina só depois da GES-08.

**Por que importa.** Alimenta a revisão e o reajuste (GES-01), a provisão de devedores duvidosos (`acc.doubtful_method` com a opção `EXPECTED_LOSS` precisa da curva de perda) e a análise de capacidade financeira (GES-02).

**O que o plano já tem.** ED-32 (contas a receber e PDD), ED-47 e ED-49 (painéis), P3.22 (relatórios). Não há previsão nem cenário.

**Onde encaixa.** Tarefa nova sobre o BI da ED-47, depois da GES-01.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim, livre: `fc.horizon_months`, `fc.scenarios`.

#### GES-11 API pública, webhooks de saída e catálogo de conectores de parceiros (A)

**Descrição.** API oficial para tenants e parceiros, com credencial por tenant, escopos, cota, versionamento, ambiente de teste e documentação; webhooks de saída (conta emitida, pagamento baixado, OS encerrada, protocolo alterado); catálogo de conectores certificados (PSP e bancos, gráficas, birôs, fornecedores de hidrômetro e AMI, ERP) com programa de parceiros.

**Por que importa.** É o caminho de receita e de aderência fora do núcleo: o mercado nacional vende módulos mensais por contrato público (Fiorilli e Betha, preços na seção 1.2) e os grandes fornecedores usam marketplaces (Itron no Microsoft Marketplace). Marketplace de integrações próprio de CIS de água: não localizado [INFERÊNCIA de lacuna de mercado].

**O que o plano já tem.** P0.10 (API versionada, OpenAPI), P0.16 (limite por IP, usuário e tenant), P1.6 (planos, cotas, flags), ED-62 (conectores com ERP), P3.2 e ED-42 (webhooks de entrada), ED-07 (alertas ao tenant). Webhook de saída: nenhum.

**Onde encaixa.** Tarefa nova depois da P1.11 (idempotência) e da P1.6; cota de API no plano do contrato (ADR-0019, item 2).

**Prioridade e esforço.** Média; M inicial.

**Parâmetro do tenant.** Sim: cotas e escopos por plano (P1.6); `api.webhooks_enabled`.

#### GES-12 Obras e convênios de investimento (A)

**Descrição.** Acompanhamento de convênios e contratos de repasse federais ou estaduais para obras (medição, cronograma, prestação de contas no Transferegov), ligado a projetos e pacotes do GIS.

**Por que importa.** Prestadores municipais dependem de repasse. É função de ERP de obras, provavelmente fora do recorte do produto [INFERÊNCIA]. A ED-53 já trata projetos e as-built no GIS (SG-858, TR-1050).

**O que o plano já tem.** ED-53 (pacotes de projeto e de as-built), ED-62 (integração). Nada para convênio.

**Onde encaixa.** Decisão do dono do produto, por cartão: manter fora e declarar o limite, ou criar ponto de integração com o ERP do cliente.

**Prioridade e esforço.** Baixa; G se construído.

**Parâmetro do tenant.** Não.

### 3.2 Classe B: presentes mas rasos

#### GES-13 Reforma tributária além da alíquota (B)

**Descrição.** Ampliar a P2.4 (tamanho P, "CBS/IBS com vigência") com os pontos que o levantamento 11 marcou como "não atende": cashback de 100% da CBS e 20% do IBS para famílias do CadÚnico em abastecimento de água e esgotamento sanitário, concedido na cobrança (LC 214, arts. 112 a 118); split payment (arts. 31 a 36), cuja operacionalização para contas de consumo não foi confirmada; fato gerador de operação contínua, reescrito pela LC 227/2026 (art. 10 §3º, texto novo não lido); registro de `tax_event_date` separado de `due_on` e de `issued_on`; exibição de tributos destacados por fora, que a ANA tratou em encontro de agências (palestra, fonte frágil); reequilíbrio contratual (arts. 373 a 377: decisão em 90 dias, prorrogável uma vez, de preferência por ajuste tarifário, inclusive quando a matriz de riscos atribuía o tributo ao prestador).

**Por que importa.** Segundo a doutrina consultada, o saneamento ficou fora da redução de 60% e a carga estimada passa de cerca de 9,7% para a faixa de 26,5% a 27,5% (APET, V). A fatura e a tarifa mudam de composição, e a NFAg-e (modelo 75, instituída pelo art. 114 do Decreto 12.955/2026) tem obrigatoriedade informada a partir de 01/12/2026 pelo Ato Conjunto RFB/CGIBS 4/2026 (R, a conferir no DOU). Para prestadores privados, a EFD-Contribuições será descontinuada e a NFAg usa o código 29 no registro C600 em regime transitório (NT RFB 011/2026, R).

**O que o plano já tem.** P2.4, `V4_5` (tributos com vigência), P2.6 (NFAg-e), P3.7 (homologação fiscal), ADR-0003, FISCAL-PORTE-JAVA. O levantamento 11 propõe `tax_profile` e `cashback_enabled`, que não chegaram ao catálogo de parâmetros.

**Onde encaixa.** Subtarefas da P2.4 e da P2.5: cashback como crédito na conta (lista de beneficiários vinda da mesma origem de `social.base_import`), `tax_event_date`, campos reservados de split payment (chave da NFAg em cada cobrança) e pleito de reequilíbrio como caso de uso da GES-01 e da GES-02. Confirmar com tributarista se a autarquia é contribuinte do IBS e da CBS [INFERÊNCIA aberta].

**Prioridade e esforço.** Alta; M (P2.4 passa de P para M).

**Parâmetro do tenant.** Sim: `tax.profile` (`cashback_enabled`, `tax_event_date_rule`, `split_payment_mode`), classe b/JUR, com alíquotas sempre em tabela com vigência.

**Fontes.** LC 214/2025 (V, https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp214.htm); LC 227/2026 (R, https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp227.htm); APET (V); levantamento 11 (R17, R41, R42); https://dfe-portal.svrs.rs.gov.br/Nfag (R).

#### GES-14 Contabilidade regulatória da NR 16 (MCRSB) e de-para com o plano do cliente (B)

**Descrição.** Catálogo de fábrica do plano de contas regulatório (MCRSB) com de-para ao plano do tenant (PCASP, para autarquias, ou plano societário), balancetes mensais, relatórios trimestrais e demonstrações anuais por contrato e município, separação entre dado gerencial e dado contábil regulatório (este restrito às agências), rastreabilidade e auditoria. O inventário MCPSB já tem dono (ED-56).

**Por que importa.** A NR 16/2026 (Res. ANA 302, vigor em 31/08/2026) dá 3 anos às agências para adotar a norma e 5 para estruturar o recebimento; prestadores implantam até o exercício de 2032. Prestadores públicos usam PCASP e NBC TSP, e o Tesouro publica o PCASP anual (2027 pela Portaria 2.520/2026, R).

**O que o plano já tem.** ED-32: regra de evento para lançamento por chave de evento, plano de contas por tenant, fechamento congelado, exportação diária; `acc.doubtful_method`. ADR-0028 (e): o produto não é o sistema contábil oficial do órgão. ED-56: códigos MCPSB e conciliação GIS × patrimônio. O levantamento 08 R-37 marca "não atende" e pede roadmap de longo prazo. ED-62 integra com Betha, IPM e Fiorilli.

**Onde encaixa.** Ajuste da ED-32 com o catálogo MCRSB como dado global versionado e o de-para como configuração do tenant; relatórios por contrato lidos da GES-02. Não duplicar o sistema contábil oficial.

**Prioridade e esforço.** Média; G.

**Parâmetro do tenant.** Sim: plano de contas e de-para com vigência (grupo `acc.*`), classe d, FIN.

**Fontes.** Res. ANA 302/2026 (R, https://www.gov.br/ana/pt-br/assuntos/noticias-e-eventos/noticias-periodo-eleitoral-2026/ana-publica-norma-de-referencia-sobre-contabilidade-regulatoria-no-saneamento-basico); levantamento 08 R-37; Siconfi (R).

#### GES-15 Pix Automático e cobrança híbrida: regras operacionais do Banco Central (B)

**Descrição.** Levar ao aceite da P4.5, da ED-27, da P3.1 e da P3.2 as regras operacionais já normatizadas. Pix Automático: jornadas 1 a 4 de autorização (inclusive QR Code de autorização e primeiro pagamento imediato), agendamento de 2 a 10 dias antes da liquidação, notificação ao pagador, retentativas (até 3 em 7 dias se previstas na autorização), cancelamento pelo pagador até 23h59 da véspera e pelo recebedor até 22h, multa e juros na instrução do ciclo seguinte, conciliação por identificador de cobrança, devolução de débito indevido em 24 horas pelo banco do pagador. Cobrança híbrida (Res. BCB 587): vale só para Pix Cobrança com vencimento; o emissor do boleto é o PSP do recebedor; o Pix é cancelado quando o boleto é pago; pagamento em duplicidade segue para a devolução da P3.29.

**Por que importa.** O débito automático interbancário com recebedor pessoa jurídica ou entidade não autorizada a funcionar pelo BCB já segue o Pix Automático (Res. CMN 5.251/2025, que altera a Res. CMN 4.790/2020, e Res. BCB 505/2025, que altera a Res. BCB 51/2020 para contas de pagamento pré-pagas), e o prazo dos convênios de serviço público é 01/01/2027 (Res. CMN 5.278/2025 para contas de depósitos e Res. BCB 544/2025 para contas de pagamento pré-pagas, R) (verificado em 22/10/2026: o prazo geral de adequação foi 01/01/2026; a cobrança de água e esgoto por débito automático provavelmente se enquadra em convênio de prestação de serviço público, mas o enquadramento exato exige análise jurídica; a Res. BCB 505/2025 não cria o Pix Automático, apenas remete ao seu regime). A Sabesp lançou o Pix Automático em 2025, com Itaú e Bemobi, e oferece bônus a quem adere (R). Não foi confirmado Pix Automático na Sanepar, Copasa, Cedae nem Aegea.

**O que o plano já tem.** P3.2 (Pix Cobrança e boleto híbrido), P4.5 (Pix Automático, M), ED-27, P3.24 (FEBRABAN 150 legado condicional), `pay.channels`, `fee.*`, P3.29, linha 1211 do plano com o marco da cobrança híbrida facultativa em 01/02/2027. Os aceites não citam as regras acima.

**Onde encaixa.** Ajuste dos critérios de aceite da P3.1, P3.2, P4.5 e ED-27, com teste por regra (retentativa, cancelamento, duplicidade). Sem tarefa nova.

**Prioridade e esforço.** Alta; M.

**Parâmetro do tenant.** Sim: `pay.auto_debit.retry_policy`, `pay.auto_debit.notice_days`, `pay.auto_debit.incentive` (programa de bônus, classe d).

**Fontes.** https://www.bcb.gov.br/api/servico/sitebcb/notasImprensa/21262 (V); https://www.bcb.gov.br/content/estabilidadefinanceira/pix/automatico/guia_pix_automatico.pdf (R); https://www.bcb.gov.br/content/estabilidadefinanceira/pix/pix-automatico-FAQ-participantes.pdf (R); https://normativos.bcb.gov.br/Votos/CMN/202592/Voto_do_CMN_92_2025.pdf (R; é o Voto 92/2025-CMN, vinculado à Res. CMN 5.278/2025, cujo PDF não foi lido na verificação; a regra deve ser citada pela resolução, e não pelo voto); https://www.bemobi.com/blog/sabesp-lanca-pagamento-com-pix-automatico-em-parceria-com-a-bemobi (R).

#### GES-16 SINISA: ciclo completo, mapa de variáveis e certificado de adimplência (B)

**Descrição.** Ampliar a P4.2 (SINISA assistido) para o ciclo anual completo: mapa de variáveis dos formulários (financeiro, técnico e balanço) para a origem no Ziip SAN, rascunho pré-preenchido, versão preliminar e resposta, registro do Certificado de Adimplência com data, e distinção entre prestador local e regional, que usam ambientes distintos. A página oficial não informa API de envio, então o plano "arquivo oficial" da P4.2 precisa de confirmação [INFERÊNCIA].

**Por que importa.** O certificado condiciona o acesso a recursos federais (Lei 11.445, art. 50; Decreto 11.599, art. 7º); a dispensa dos incisos VII a IX do art. 50 foi prorrogada até 31/12/2027 pelo Decreto 12.805/2025 (R). A página oficial informa que não haverá prazos adicionais para preenchimento, o que contraria um relato de subagente sobre prorrogação de 15 dias úteis; vale a fonte oficial.

**O que o plano já tem.** P4.2, ADR-0004, ED-48, aba Regulatório da Central (janela do SINISA), levantamento 08 R-36.

**Onde encaixa.** Ajuste do aceite da P4.2 e da ED-48; a agenda da GES-05 dá o lembrete.

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `sinisa.provider_scope` (`LOCAL`, `REGIONAL`), responsável.

**Fontes.** https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/area-do-prestador (V); Decreto 12.805/2025 (R, http://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/decreto/d12805.htm).

#### GES-17 Receitas alternativas, complementares e acessórias, com compartilhamento (B)

**Descrição.** Classificar cada receita como tarifária, complementar, acessória ou de projeto associado; registrar homologação regulatória da atividade ou do preço; registrar o percentual compartilhado com a modicidade tarifária e levá-lo à receita requerida (GES-01); incluir reúso de efluentes e cobrança de outros serviços na fatura, com a separação de débitos do levantamento 08 R-42.

**Por que importa.** A Lei 11.445, art. 10-A II, exige que os contratos prevejam essas fontes, com compartilhamento. A nota técnica NT.F-0083-2020 da ARSESP, de subsídio à regulação da Sabesp (verificado em 22/10/2026: a aprovação da nota técnica pela Diretoria da ARSESP foi localizada em atas, processo ARSESP.ADM 0180-2020; o ato normativo correspondente é a Deliberação ARSESP 1.107, de 29/12/2020, cujo texto não foi aberto; situação a confirmar), distingue as classes e diz que a parcela compartilhada compõe a receita requerida.

**O que o plano já tem.** ED-34 (catálogo de serviços faturáveis e tabelas de preço com vigência), ED-25 (resíduos na conta), ED-22 (situações especiais). Não há classificação por natureza de receita nem compartilhamento.

**Onde encaixa.** Ajuste da ED-34 (campo de natureza de receita) e da GES-01.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `rev.sharing_pct`, `rev.homologation_required`; classe b.

**Fontes.** https://www2.camara.leg.br/legin/fed/lei/2007/lei-11445-5-janeiro-2007-549031-publicacaooriginal-64311-pl.html (V); http://www.arsesp.sp.gov.br/ConsultasPublicasBiblioteca/NT.F-0083-2020.pdf (V).

#### GES-18 Dívida ativa: ajuizamento, alçada e prescrição (B)

**Descrição.** Registro do ajuizamento da execução fiscal (número do processo, vara, fase), valor mínimo para ajuizar (lei do ente), alerta de prescrição pelo prazo parametrizado e controle do protesto de CDA.

**Por que importa.** O protesto de CDA tem base na Lei 9.492, art. 1º, parágrafo único, e o STF o declarou constitucional (ADI 5135, julgada em 09/11/2016) (R). O parágrafo único foi acrescentado ao art. 1º da Lei 9.492 pelo art. 25 da Lei 12.767/2012 (verificado em 22/10/2026: a aplicação a créditos de água e esgoto depende de o prestador poder inscrevê-los em dívida ativa, ponto não verificado que deve ser confirmado com o jurídico antes de virar regra do produto). O ADR-0028 (d) decidiu que o produto não conduz o processo judicial, e a decisão fica. O acompanhamento mínimo do que foi entregue à procuradoria evita perder prazo.

**O que o plano já tem.** ED-31 (inscrição, CDA, livro, parcelamento, arquivo e petição-modelo), ED-30 (negativação e protesto por arquivo), `ret.prescription_years`, `neg.*`. CENPROT por API está na lista de 2028.

**Onde encaixa.** Ajuste leve da ED-31.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Sim: `da.min_filing_amount`, `da.protest_enabled` (negativação de ente público desligada por padrão, ADR-0038).

#### GES-19 Mensageria: custo por categoria de template e uso medido do SaaS (B)

**Descrição.** Classificar cada template de WhatsApp por categoria, estimar custo, impor teto de gasto por tenant e medir uso por módulo (mensagens, SMS, tokens de IA, armazenamento, GIS).

**Por que importa.** A API do WhatsApp cobra por mensagem de template desde 01/07/2025, com categorias de preço muito diferentes entre marketing e utilidade; uma régua de cobrança com texto promocional pode ser classificada como marketing. Valores de integradores: R$ 0,3217 por marketing e R$ 0,0350 por utilidade ou autenticação [INFERÊNCIA até conferir no preço oficial da Meta]. Mudanças de cobrança para respostas na janela de 24 horas estão em fonte de terceiros, não oficial.

**O que o plano já tem.** ED-10 (franquia ou repasse de mensagens e do modelo de linguagem), ED-41 e ED-42 (mensagens medidas por tenant), ADR-0019 (cotas com alerta a 80% e 100%).

**Onde encaixa.** Ajuste da ED-42 e da ED-10, com painel de uso no console (P1.6).

**Prioridade e esforço.** Média; P.

**Parâmetro do tenant.** Sim: `msg.monthly_cap`, `msg.template_category_map`.

**Fontes.** https://zapvoxia.com.br/en/blog/whatsapp-business-api-pricing-2026 (R, integrador); https://www.courier.com/blog/whatsapp-pricing-changes-october-2026 (R, terceiro).

#### GES-20 Risco e continuidade do prestador (B)

**Descrição.** Registro de incidente operacional (falta de energia na ETA, rompimento de adutora, contaminação) e cibernético, com comunicação à agência; plano de contingência de atendimento em modo degradado; eventos de resiliência climática. A matriz de riscos contratual da NR 5 fica na GES-02.

**Por que importa.** O levantamento 13 registra o incidente operacional e cibernético com comunicação ao regulador como tendência. A Lei 14.133, art. 169, exige gestão de riscos nos contratos públicos, e a agenda regulatória da ANA destaca resiliência climática.

**O que o plano já tem.** P3.31 (riscos para o módulo ISO), ED-35 (interrupção programada e emergência, NR 11 arts. 39 a 41), ED-79 (Plano de Segurança da Água), P3.10 (incidente de segurança), ADR-0012 (RPO e RTO), contingência da NFAg-e (FISCAL-PORTE-JAVA, ED-19).

**Onde encaixa.** Ampliar a ocorrência única da P2.25 com tipo "incidente operacional" e vínculo à interrupção da ED-35.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Parcial: `reg.incident_report_channel`.

#### GES-21 ESG e energia (B)

**Descrição.** Indicador de kWh por m³ por sistema, ETA e elevatória; inventário simplificado de emissões, escopos 1 e 2 (energia elétrica, metano de ETE, combustível da frota da ED-59), com catálogo global e versionado de fatores de emissão; exportação voluntária para relatório de sustentabilidade.

**Por que importa.** A ED-77 recebe energia, emissões e pessoal só por entrada manual, com grau de confiança baixo (21b, GA-06: "lacuna; entrada manual"). A CVM, pela Res. 244/2026 (que altera a Res. CVM 193/2023), retirou a obrigatoriedade de divulgação de sustentabilidade prevista para exercícios a partir de 2026 e manteve o regime voluntário de pratique ou explique (tema de mercado de capitais, sem obrigação para prestadores em geral), e a Taxonomia Sustentável Brasileira (Decreto 12.705/2025) inclui o CNAE E. Obrigação federal de relatório ESG para autarquia municipal não foi identificada [INFERÊNCIA]. A Sanepar relata redução de até 20% de energia no piloto de gêmeo digital (R).

**O que o plano já tem.** ED-77, ED-59, ED-17, 21b L-13.

**Onde encaixa.** Ampliar a ED-77 com fonte automática (telemetria, frota) em vez de entrada manual.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `esg.emission_factors_version`, `esg.report_profile`.

**Fontes.** https://conteudo.cvm.gov.br/export/sites/cvm/legislacao/resolucoes/anexos/200/resol244.pdf (R); https://www.gov.br/fazenda/pt-br/orgaos/spe/taxonomia-sustentavel-brasileira (R).

#### GES-22 Perfil de acesso externo do regulador e do titular, somente leitura (B)

**Descrição.** Perfil de leitura com escopo por indicador e relatório para a agência e para o titular (prefeitura), com prazo, trilha de cada leitura e sem dado pessoal além do mínimo.

**Por que importa.** O SINISA tem o perfil "Regulador do Módulo" para acompanhar o preenchimento (V). A NR 16 restringe os dados contábeis detalhados às agências. Contratos como o da Sabesp preveem verificador independente.

**O que o plano já tem.** ED-75 (papel de auditor externo, somente leitura, com prazo e registro de leitura, desligado por padrão), ED-04 (acesso do órgão somente leitura em instância dedicada), ADR-0040 (permissões e escopo por área).

**Onde encaixa.** Generalizar o papel da ED-75 para "observador externo" com perfil por agência ou titular, sem tarefa separada.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `reg.external_access` (classe b, aceite), expiração obrigatória.

#### GES-23 Ganchos de integração para compras, RH e orçamento (B)

**Descrição.** Manter a decisão de 2029 sob demanda (ADR-0028, item i) e preparar pontos baratos: código de centro de custo compatível com a unidade organizacional da P3.30; natureza de despesa nos materiais da ED-58 para exportação; campo de número de empenho ou ordem de compra externa e de número no PNCP nos contratos de terceiros da ED-37.

**Por que importa.** A ED-62 já lê indicadores de compras, RH e orçamento do ERP do cliente. Os ganchos evitam retrabalho de cadastro quando algum tenant contratar os módulos.

**O que o plano já tem.** ED-58, ED-59, ED-37, ED-62, P3.30, ED-60 e ED-61 (2029). Nenhum campo de integração orçamentária.

**Onde encaixa.** Ajuste dos esquemas da P3.30, ED-58 e ED-37, sem escopo novo.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Não.

### 3.3 Classe C: inovações de mercado

Escala de maturidade: pesquisa, piloto, produção restrita, produção ampla. Todo item C que trate dado de consumidor depende da GES-08.

#### GES-24 Previsão de inadimplência e régua de cobrança adaptativa (C)

**Descrição.** Modelo que ordena as contas por propensão de atraso e adapta canal, horário e oferta de parcelamento. O modelo modula a comunicação; não decide corte, negativação nem benefício.

**Por que importa.** O Grupo Águas do Brasil, com a Grão Inteligência, trata mais de 7 milhões de ações de cobrança por mês sobre 36 meses de dados, com perfis de pior pagamento em 45% da base (caso publicado pelo fornecedor). A Oracle oferece propensão a pagar e otimização de cobrança em utilities. Um trabalho acadêmico em Joinville declara R² de 0,98, o que não equivale a acurácia e sugere risco de vazamento de dados.

**O que o plano já tem.** P4.1, ED-29, ED-30 (`neg.excluded_profiles`), ED-68 (cobrança por empresa terceirizada, parada pelo dono), ED-41 (notificações). Nenhum modelo preditivo.

**Onde encaixa.** Depois de P4.1 e ED-29, com a GES-08 aceita.

**Prioridade e esforço.** Média; G.

**Parâmetro do tenant.** Sim: `ai.collection_scoring_enabled` (padrão desligado, classe b).

**Maturidade.** Produção restrita no Brasil e em fornecedores globais; resultados autodeclarados.

**Riscos.** Técnico médio (qualidade do histórico, deriva, vazamento de rótulo). Regulatório alto: a avaliação de acesso a serviço essencial é alto risco no texto do PL 2338 enviado à Câmara (art. 14, IV; verificado em 22/10/2026); as vedações da Lei 14.015 e da Lei 14.898 impedem usar o escore sobre corte e tarifa social. LGPD alto: perfilamento financeiro; art. 20; não usar dados do Open Finance; opt-out e explicação.

#### GES-25 Analítica de fraude, ligação clandestina e perdas aparentes (C)

**Descrição.** Regras sobre o consumo e o cadastro que geram OS de fiscalização (ED-24): consumo zero com ligação ativa, queda abrupta, relação consumo por economia e por área, medidor parado ou subdimensionado, troca de hidrômetro, leitura repetida. Modelo de aprendizado de máquina só depois que as regras forem medidas.

**Por que importa.** A Sabesp detectou 52.537 irregularidades em 2025, contra 25.793 em 2024; a Copasa estima 300 mil ligações fraudulentas e cerca de 40% das perdas por fraude (dado da empresa). O custo estimado das perdas comerciais era de R$ 12,4 bilhões (GO Associados, dados de 2022). A NR 15 exige plano de redução de perdas, e a verificação das perdas aparentes é indicador do SINISA.

**O que o plano já tem.** ED-24 (OS de fiscalização por critério; auto de infração `INFRACTION_NOTICE`; `infraction.*`), ED-14 (hidrometria), ED-17 (telemetria), ED-78 (balanço de perdas). Critério único de seleção; sem rotina de anomalia.

**Onde encaixa.** Ampliar a ED-24 com biblioteca de regras e relatório de acerto das inspeções, que mede a taxa de confirmação.

**Prioridade e esforço.** Alta; M (regras) a G (modelo).

**Parâmetro do tenant.** Sim: `fraud.rules` (limiares por tenant), `infraction.*`; programa de autodenúncia com isenção de multa (Copasa) como opção.

**Maturidade.** Produção ampla nas grandes; Iguá relata 98% de acurácia (autodeclarado).

**Riscos.** Técnico médio (falsos positivos). Regulatório médio: a infração exige defesa e decisão antes do débito (`infraction.charge_timing`). LGPD médio: dado de consumo de pessoa identificável; a saída é recomendação a humano.

#### GES-26 Explicação automática da variação da conta e copiloto do atendente (C)

**Descrição.** Motor determinístico "por que a conta mudou", que compara duas contas e lista dias faturados, consumo real ou estimado, tarifa e reajuste vigentes, serviços, créditos e multa. Exposto no portal, na visão 360 do atendente e como função fechada do chatbot da ED-43. A IA generativa entra só na redação. O copiloto do atendente resume o histórico do protocolo.

**Por que importa.** A fatura alta é motivo recorrente de reclamação [INFERÊNCIA], e a NR 11 (art. 54) mede o percentual de reclamações não atendidas. A Oracle oferece assistente que explica a fatura; Copasa e Sabesp usam IA generativa no WhatsApp para segunda via, parcelamento e religação.

**O que o plano já tem.** ED-43 (funções fechadas, minimização), ED-35 (visão 360), P2.5 (mensagem de excesso de consumo, TR-306), P3.22 (relatório do art. 54).

**Onde encaixa.** Ampliar a ED-35 e a ED-43.

**Prioridade e esforço.** Alta; P a M.

**Parâmetro do tenant.** Opcional: `bill.explain_enabled`.

**Maturidade.** Produção ampla (o motor determinístico não depende de modelo).

**Riscos.** Técnico baixo. Regulatório baixo. LGPD baixo no motor, porque os dados são do próprio titular e não saem do sistema; médio no copiloto com provedor externo (DPA, minimização, ED-43).

#### GES-27 Consumo quase em tempo real e alertas ao consumidor (C)

**Descrição.** Ingerir leituras de medidor inteligente (AMI), validar, estimar e editar (VEE), exibir consumo horário no app e no portal, alertar sobre consumo contínuo por 24 horas (vazamento no cliente) e sobre picos, e usar a leitura do registrador no ciclo, sem leiturista.

**Por que importa.** A Sabesp instala 4,4 milhões de hidrômetros inteligentes em NB-IoT e mostra o consumo horário do dia anterior no app. A Anglian Water, com 1,4 milhão de medidores, passou de cerca de 9 mil para mais de 170 mil fluxos contínuos identificados por ano, com redução média de 14,75 l por domicílio por dia (dados do fornecedor parceiro Arqiva, confiança média). A Itron liga consulta em linguagem natural aos dados de medição (janeiro de 2026).

**O que o plano já tem.** ED-17 (API REST e arquivo, origem `TELEMETRY`, tabela particionada, alarmes de grandes consumidores, sem broker; MQTT sob demanda), ED-14 (Portaria Inmetro 155/2022), ED-15, ED-38, ED-39, ED-41. Não há VEE para AMI.

**Onde encaixa.** Ampliar ED-17, ED-39 e ED-41; registrar a exceção do broker no ADR-0010 só se o volume exigir.

**Prioridade e esforço.** Média; G.

**Parâmetro do tenant.** Sim: `ami.retention_days`, `ami.alert_thresholds`.

**Maturidade.** Produção ampla em grandes prestadores.

**Riscos.** Técnico alto (volume, estimativa e rejeição de leitura, conectividade). Regulatório médio (verificação metrológica; o dado exibido precisa respeitar o regulamento do medidor [INFERÊNCIA]). LGPD médio a alto: consumo horário revela rotina; base legal, retenção curta e minimização.

#### GES-28 Metas de consumo, comparação com pares e gamificação (C)

**Descrição.** Relatório mensal comparativo com pares de perfil semelhante, meta de consumo definida pelo usuário e lembretes, com adesão voluntária.

**Por que importa.** Ensaios aleatorizados com cartas de comparação social mostram redução de cerca de 3% a 5% (WaterSmart e Opower), cerca de 2% no Reino Unido, com efeito só no papel e nenhum no e-mail, e o efeito decai com o tempo. Em um dos serviços estudados o efeito não foi significativo. Estudo robusto de gamificação em água não foi localizado [INFERÊNCIA].

**O que o plano já tem.** Nada.

**Onde encaixa.** Tarefa opcional depois da GES-27, ou carta mensal no portal.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `eng.peer_report_enabled`, `eng.peer_group_min_size`.

**Maturidade.** Produção ampla em energia; água com evidência menor.

**Riscos.** Técnico baixo. Regulatório baixo (a mensagem na conta segue o modelo aprovado pela agência, `bill.invoice_template`). LGPD baixo a médio (comparação só com grupos de tamanho mínimo).

#### GES-29 Benchmark com o SINISA aberto e, depois, entre tenants (C)

**Descrição.** Painel que compara o indicador do tenant com a mediana do SINISA por porte, região e natureza jurídica, a partir de dado público importado, global e versionado, como a base de domicílios do IBGE da ED-77. Comparação entre tenants só com adesão, agregação mínima e cláusula contratual.

**Por que importa.** O SINISA é dado aberto (Lei 11.445, art. 53 §1º). A ERSAR (Portugal) e a CRA (Colômbia) publicam ranking e classificação. O levantamento 14 §4.6 alerta que usar dado de tenant para benchmark pode tornar o Ziip controlador.

**O que o plano já tem.** ED-48 (indicadores com metas), ED-49 (painel estratégico), ED-77 (base IBGE). Nenhum benchmark.

**Onde encaixa.** Ampliar a ED-48 e a ED-49.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `bench.cross_tenant_opt_in` (padrão desligado, classe b).

**Maturidade.** Produção ampla (dado público).

**Riscos.** Técnico baixo. Regulatório baixo. LGPD baixo com dado público; médio com comparação entre tenants.

#### GES-30 Regulação por incentivo: apuração auditável e simulador de fatores Q, P e U (C)

**Descrição.** Ficha de indicador com a versão da fórmula, congelamento do período, hash da apuração, trilha do dado primário (leitura, ocorrência, protocolo) até o indicador, exportação ao verificador independente e simulação do efeito tarifário de bônus ou penalidade.

**Por que importa.** A Ofwat PR24 usa incentivos financeiros de desempenho (24 compromissos comuns e 7 específicos). O contrato da Sabesp com a ARSESP tem fator Q, fator U e verificador independente. A NR 6 exige indicadores que ajustem a tarifa, e a minuta de revisão da ANA (CP 03/2026) propõe fatores Q, P e U (verificado em 22/10/2026: a menção a fatores Q, P e U não foi encontrada nas fontes lidas; a Nota Técnica 14/2026/COTAR/SSB da CP 03/2026 discute, entre outros temas, o fator X de ganho de produtividade, o custo de capital de terceiros, a remuneração de investimentos e as receitas acessórias). A ARSAE-MG (Res. 217/2025) aplica incentivos de perdas, universalização e qualidade.

**O que o plano já tem.** ED-48 (metas, fechamento, semáforo), ED-77 (variáveis, grau de confiança), P1.7 (partição do `audit_log`), cadeia de hash do armazenamento imutável (P2.18).

**Onde encaixa.** Ampliar a ED-77 e a ED-48; leitura pelo observador externo da GES-22.

**Prioridade e esforço.** Média; M.

**Parâmetro do tenant.** Sim: `perf.*` (fatores, metas e origem; classe d, REG).

**Maturidade.** Produção em Reino Unido e São Paulo; no Brasil, norma de revisão ainda em minuta.

**Riscos.** Técnico médio. Regulatório médio (definições mudam). LGPD baixo.

#### GES-31 Gêmeo digital: integração, não construção (C)

**Descrição.** Manter fora do escopo próprio o gêmeo digital em tempo real. Oferecer exportação de demanda por zona e nó, do consumo faturado e do cadastro, e um conector de arquivo ou API para ferramentas de terceiros, na linha da ED-63.

**Por que importa.** Há produção em grandes operadores (a Bentley cita a Evides, com metade das perdas por rompimento e metade do tempo de reparo, por dado do fornecedor; a Sanepar declara energia reduzida em até 20% no piloto). Exige SCADA, macromedição e calibração, e o custo de integração e calibração costuma superar o da licença.

**O que o plano já tem.** ED-63 (EPANET e SWMM no CI, demanda do consumo faturado), ED-17, ED-64.

**Onde encaixa.** Ampliar a ED-63 com o formato de exportação.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Não.

**Maturidade.** Produção ampla em grandes operadores; custo alto.

**Riscos.** Técnico alto se construído. Regulatório baixo. LGPD baixo, se a demanda for agregada por zona.

#### GES-32 Login gov.br como provedor de identidade opcional no portal (C)

**Descrição.** Opção de entrar com gov.br (níveis prata e ouro) por federação OIDC no Keycloak, para pedidos de maior risco: tarifa social, negociação de dívida, troca de titularidade. O login local por CPF, CNPJ ou e-mail da P3.3 permanece.

**Por que importa.** Reduz a gestão de senha e eleva a segurança do vínculo. A integração gratuita ao Login Único é concedida a serviço público de órgãos e empresas públicas, e prestador privado provavelmente não é elegível [INFERÊNCIA]; as notificações gov.br são restritas à administração federal.

**O que o plano já tem.** P3.3 (realm `ziipsan-portal`, MFA opcional, sem SMS), P3.20, P0.38 (Keycloak Organizations), 13 AS-01 e AS-03 (níveis de assinatura).

**Onde encaixa.** Ampliar a P3.3 com identity brokering por tenant.

**Prioridade e esforço.** Baixa; M.

**Parâmetro do tenant.** Sim: `portal.idp_govbr_enabled`, classe d.

**Maturidade.** Produção ampla.

**Riscos.** Técnico baixo a médio. Regulatório baixo. LGPD médio (CPF vindo do provedor; minimização).

**Fontes.** https://www.gov.br/governodigital/pt-br/identidade/conta-gov-br/niveis-da-conta-govbr ; https://www.gov.br/governodigital/pt-br/estrategias-e-governanca-digital/transformacao-digital/servico-de-integracao-aos-produtos-de-identidade-digital-gov.br (ambas R).

#### GES-33 Open Finance (iniciação) e Pix por aproximação via PSP (C)

**Descrição.** Oferecer, no portal e no app, pagamento por iniciação via PSP e Pix por aproximação, como capacidade do provedor, sem o Ziip SAN ou o tenant virarem participantes.

**Por que importa.** A Jornada Sem Redirecionamento é obrigatória para detentoras de conta desde 22/04/2026. A iniciação por ITP somou 64,5 milhões de transações em 2025, menos de 0,1% do Pix, e converte 85% sem redirecionamento contra 50% a 60% com redirecionamento. O Open Finance de dados para crédito é inviável para quem não é instituição participante [INFERÊNCIA]. Para a fatura de água o ganho do Pix por aproximação é baixo [INFERÊNCIA].

**O que o plano já tem.** P3.2, P4.5, `pay.channels`, `pay.pix_provider`.

**Onde encaixa.** Nota de aceite da P3.2: o provedor escolhido deve suportar JSR.

**Prioridade e esforço.** Baixa; P.

**Parâmetro do tenant.** Sim: `pay.channels`.

**Maturidade.** Produção restrita.

**Riscos.** Técnico baixo. Regulatório baixo. LGPD baixo para pagamento; alto se dados do Open Finance alimentassem escore, o que a GES-08 veda.

#### GES-34 Antecipação de recebíveis e conta vinculada (C)

**Descrição.** Relatório de carteira (aging e adimplência) em formato que sirva à cessão, ao fundo de recebíveis ou à conta vinculada.

**Por que importa.** A Sanepar prevê a vinculação de recebíveis tarifários a conta vinculada em edital de PPP (R). Oferta de antecipação sobre faturas de consumidores pulverizados: não localizada. O Drex teve a plataforma de testes desligada em novembro de 2025.

**O que o plano já tem.** ED-32 (contas a receber), P3.22.

**Onde encaixa.** Decisão do dono do produto. Não recomendada agora.

**Prioridade e esforço.** Baixa; M se for atendida.

**Parâmetro do tenant.** Não.

**Maturidade.** Pesquisa para recebíveis de consumo.

**Riscos.** Técnico médio. Regulatório alto para entes públicos (operação de crédito, Lei de Responsabilidade Fiscal, Lei Complementar 101/2000) [INFERÊNCIA]. LGPD médio (cessão de carteira com dado pessoal).

## 4. Itens que o plano já cobre bem

| Tema | Onde está | Observação |
|---|---|---|
| Tarifa social e Lei 14.898 | P1.5, ED-57, grupo `social.*`, ADR-0015 | Piso travado, não condicionada à adimplência (consolidado 18 §6, L1), `social.base_import` com origem pela agência ou direta, que acompanha a regra de acesso ao CadÚnico |
| Corte, religação e vedações | P3.4, ED-29, ADR-0015, ADR-0038 | Bloqueio pela Lei 14.015, revalidação offline, compensação por corte indevido |
| NFAg-e e motor fiscal | P2.6, P3.7, ADR-0003, ED-19 | Contingência e homologação cobertas; a regra de 01/12/2026 vai à GES-13 apenas no que excede a emissão |
| Dívida ativa e protesto | ED-30, ED-31, ED-26 | Pontos finos na GES-18 |
| Eventos contábeis e e-Sfinge | ED-32, ED-33, ED-62 | O produto não é o sistema contábil oficial (ADR-0028 e); acertado |
| Perdas e balanço pela NR 15 | ED-78, P4.2, ED-48 | Metas na GES-03 |
| Inventário patrimonial MCPSB e bens reversíveis | ED-56 | Parte contábil na GES-14 |
| Ouvidoria e interrupção programada | ED-35, P3.22 | Prazos da Lei 13.460, NR 11 arts. 39 a 41 e art. 54 |
| Qualidade da água, PSA, bloco na conta | ED-50, ED-79, P2.24 | Cobertura ampla |
| BI e painéis | ADR-0027, ED-46 a ED-49, P3.22 | Isolamento por role de tenant |
| Cobrança do SaaS e continuidade | ADR-0019, ADR-0025, P2.12, P2.15, ED-10, ADR-0024 | Sem bloqueio automático, SLA, reversão |
| Pix Cobrança, retornos e devolução | P3.1, P3.2, P3.29 | Detalhamento do BCB na GES-15 |
| Chatbot com minimização | ED-43, ADR-0028 (c) | Governança de IA na GES-08 |
| Autoleitura e hidrometria metrológica | ED-14, ED-15 | Portaria Inmetro 155/2022 |
| Parâmetros e perfis por agência | ADR-0002, ADR-0015, ADR-0038 | Estrutura certa para todos os achados acima |
| Governança ISO e LGPD | ADR-0041, P1.32, P2.25, P3.31, P3.10 | Fora do escopo desta frente |

## 5. Inferências e pontos a confirmar

**Divergências entre fontes.**
1. Prazo adicional do SINISA 2026: a página oficial diz que não haverá prazo adicional; um relato de subagente cita 15 dias úteis até 23/09/2026. Adotada a fonte oficial.
2. Pix Automático em 2026: dados do Banco Central indicam cerca de 600 mil transações em dezembro de 2025 e mais de 1.000 recebedores; uma análise do Safra cita 14 mil recebedores em agosto de 2026, não confirmada pelo Banco Central.
3. Data da NR 5 (15/01/2024 ou 16/11/2024), data da Res. ARESC 389/2026 (13/01 ou 12/02/2026) e data da IN TC-28/2021 (04/03 ou 08/03/2021) variam entre fontes.
4. Perdas de faturamento do SINISA 2025 (39,1%) vêm de fonte secundária única; conferir no indicador oficial antes de uso em material comercial.

**Inferências e fontes frágeis.**
5. Redução de 60% para saneamento na LC 214: afastada com base em doutrina e imprensa jurídica (APET), sem leitura do anexo da lei; confirmar com tributarista, assim como o enquadramento da autarquia como contribuinte do IBS e da CBS.
6. NFAg obrigatória em 01/12/2026: o consolidado 14 pediu confirmação no DOU; as fontes desta rodada (portal da SVRS e Águas do Rio, que já a usa) corroboram, mas o DOU não foi lido por mim (verificado em 22/10/2026: a data de 01/12/2026 está confirmada pelo cronograma da Receita Federal e pelo texto do Ato Conjunto RFB/CGIBS 4/2026).
7. Fala de funcionária da ANA sobre tributos "por fora" e NFAg "independentemente da natureza jurídica": palestra em evento, não norma.
8. Texto do PL 2338/2023 após o Senado: confirmei apenas o texto original e a ficha de tramitação (verificado em 22/10/2026: lido o texto enviado à Câmara, autógrafo de 31/01/2025).
9. Cashback e split payment: início de operação para contas de consumo não confirmado.
10. Existência de API de envio ao SINISA: a página oficial não informa; assumida inexistente até confirmação.
11. Decreto 11.598 e prestação direta: conclusão vem de fonte setorial.
12. Elegibilidade ao Login Único gov.br por natureza jurídica: inferida.
13. Preços de templates do WhatsApp: fontes de integradores; conferir na tabela oficial da Meta antes de embutir em contrato.
14. Resultados de IA, gêmeo digital e comparação social vêm de fornecedores, consultorias e estudos de caso. Não são médias de setor.
15. Concorrentes nomeados no briefing sem fonte localizada: Hexagon Smallworld, Esri Utility Network, Gestão Sanasa, Sigcos e Tecnosan como sistemas comerciais; sistemas comerciais da Copasa, CASAN, Cedae e Corsan. A cobertura de concorrentes é, portanto, parcial.

**Pontos a decidir por cartão (o documento não presume).**
- Entrada da GES-12 (convênios) no recorte, ou declaração de limite.
- Entrada da GES-06 (LAI) como módulo opcional, ou apenas link para o sistema do município.
- Se a GES-34 fica fora do produto.
- Ordem entre GES-01, GES-04 e GES-13, que dependem entre si.
- Aceite da GES-08 antes de qualquer item da classe C com modelo.

**Lacunas de pesquisa.** Normas próprias da AGIR; texto integral da NR 11 (o PDF não foi extraído); SIAFI e contrato comercial do Serpro; Siemens e Esri em gêmeo digital; DC Water e Veolia sem números verificáveis; leiaute do e-Sfinge para tributos; split payment específico de fatura de água.
