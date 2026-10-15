# 20g — Pesquisa web: GSAN hoje, mercado de sistemas comerciais de saneamento e normas de 2024 a 2026

Data: 14/10/2026. Fontes: sites oficiais (gov.br, ANA, Portal do Software Público, ARIS), portais de licitação,
sites de fornecedores e imprensa especializada. Ferramentas: WebSearch e WebFetch nativos; os MCPs Perplexity e Brave
continuaram com chave inválida nesta sessão. Os PDFs da ANA e da ARIS foram extraídos localmente com PyMuPDF para
leitura do texto. Cada afirmação traz a fonte; o que não foi localizado está dito como tal.

> **Não é parecer jurídico.** As normas estão citadas para orientar requisitos de software; prazos e sanções exigem
> confirmação na redação consolidada e revisão jurídica antes de virarem padrão de fábrica.

---

## 1. Resumo executivo

1. O GSAN continua publicado no Portal do Software Público como **GPL-3.0**, versão **6.11.5.1**, com repositório
   GitLab de 2.167 commits e 160 tags, uma issue aberta e nenhum merge request; a comunidade está parada e nosso clone
   (último commit de 30/08/2018) é representativo do estado atual.
2. O GSAN segue sendo **comprado**: Bagé (DAEB) licitou em 2026 a implantação "atualizada" em SaaS com hospedagem em
   nuvem; Porto Alegre (DMAE) contratou customização e migração em 2022, depois de parecer da Procempa que o
   considerou defasado e caro. Isso define o perfil do concorrente: autarquias que já têm GSAN e precisam de nuvem,
   LGPD e app, mas não querem trocar de modelo de dados.
3. O mercado de autarquias (SAAE, SAMAE, SANEAR) licita em 2026 soluções **100% web, em nuvem, com app de leitura e
   OS, faturamento com Pix e dívida ativa**, em valores anuais baixos (Pirapora/MG: R$ 151 mil). É o recorte do
   Ziip SAN.
4. Em Santa Catarina os SAMAEs usam sobretudo **IPM Atende.Net** (Jaguaruna, Morro da Fumaça, Araranguá, São Bento do
   Sul, São Francisco do Sul) e **Betha** (São Ludgero, caso do edital analisado); a **Fiorilli (SIA 8)** tem módulo
   de água e esgoto com OS e almoxarifado, emissão simultânea e SNIS. São sistemas de prefeitura com módulo de água,
   não sistemas comerciais de saneamento, e esse é o espaço do Ziip SAN.
5. A ANA publicou, depois dos levantamentos 08 a 14 (12/10/2026) ou sem que eles as detalhassem, normas que afetam o
   sistema comercial: **NR 11/2024** (condições gerais; verificação a partir de **20/05/2027**), **NR 13/2025**
   (estrutura tarifária e tarifa social, Res. 271/2025), **NR 15/2025** (perdas, Res. 275), **NR 9/2024** (indicadores
   operacionais, Res. 211), **NR 12/2025** (drenagem), **NR 14/2025** (indicadores de resíduos) e **NR 16/2026**
   (contabilidade regulatória, Res. 302).
6. A NR 11 exige do prestador o que o GSAN só faz em parte e o Ziip SAN precisa fazer por desenho: cadastro com
   **histórico de 120 meses de leituras e faturamento**, **opções de data de vencimento**, cobrança pela **média**
   quando não há leitura, **identificação e aviso de pagamento em duplicidade**, **aviso prévio antes do corte por
   inadimplência**, atendimento **24 horas por 7 dias com protocolo**, **relatório mensal de reclamações** ao regulador
   e portal ou app com **12 meses** de histórico.
7. Para o piloto em SC, a **ARIS RN 19/2019** (alterada pela RN 20/2019, RN 38/2023 e RN 40/2024) traz regras
   numéricas que viram parâmetros com limite legal: 6 datas de vencimento, vencimento mínimo de 5 dias úteis (10 para
   a categoria Pública), ciclo de 27 a 33 dias, média limitada a 2 faturas em 12 meses, aviso prévio de corte com 30
   dias, **vedação de corte por faturas vencidas há mais de 120 dias**, comunicação em 24 horas após o corte, recurso
   de 10 dias, multa de 2%, juros e INPC, aviso de interrupção programada com 48 horas.
8. A **AGIR** (Vale do Itajaí) e a **ARESC** (estadual) não tiveram normas de condições gerais localizadas nesta
   pesquisa; ficam como pendência do documento 20f.

---

## 2. GSAN hoje

| Item | Achado | Fonte |
|---|---|---|
| Licença | GNU GPL 3.0, declarada na página da comunidade | [Portal do Software Público, GSAN](https://softwarepublico.gov.br/social/gsan) |
| Versão | 6.11.5.1 (Windows, Ubuntu, Debian); página de versões sem datas, remete à wiki | [GSAN](https://softwarepublico.gov.br/social/gsan); [Versões](https://softwarepublico.gov.br/social/gsan/versoes) |
| Repositório | `softwarepublico.gov.br/gitlab/gsan/gsan`: 2.167 commits, 160 tags, 1 branch, criado em 08/10/2014, 1 issue, 0 merge requests, 158 MB | [GitLab GSAN](https://softwarepublico.gov.br/gitlab/gsan/gsan) |
| Requisitos técnicos | JVM 5 ou superior, servidor JEE, EJB, JMS, JSP 2.1 | [GSAN](https://softwarepublico.gov.br/social/gsan) |
| GeoSan | módulo GIS com EPANET; repositórios `geosan` e `geosan8-0` | [GSAN](https://softwarepublico.gov.br/social/gsan) |
| Propriedade | Ministério das Cidades, desenvolvido no PMSS com o PNUD; cedido como software público | [DMAE, Concorrência 02/2022, TR](https://prefeitura.poa.br/sites/default/files/usu_doc/sites/dmae/ANEXOXII-TR.pdf) |
| Documentação | manuais de usuário, tutorial de instalação, FAQ, modelo de edital de software livre, termo de referência de GIS | [GSAN](https://softwarepublico.gov.br/social/gsan) |

Conclusão: o clone de 30/08/2018 usado nos levantamentos 01 a 04 e 20a a 20e corresponde ao estado público do
projeto. Não há evolução de comunidade a acompanhar; a evolução acontece dentro de contratos de customização, fora do
repositório.

### 2.1 Contratações recentes de GSAN

| Órgão | Objeto | Situação | Fonte |
|---|---|---|---|
| DAEB, Bagé/RS | Pregão 6/2026: fornecimento, implantação da solução atualizada, migração, customização, manutenção e suporte do GSAN, em **SaaS com nuvem da contratada**, plano de transição e LGPD; abertura em 02/09/2026 | resultado não localizado | [Alerta Licitação](https://alertalicitacao.com.br/!licitacao/DAEB-BG-5-62026); [PNCP via Alerta](https://alertalicitacao.com.br/!licitacao/BRS-343559) |
| DMAE, Porto Alegre/RS | Concorrência 02/2022: customização, migração, integração com legados, manutenção e operação do GSAN, substituindo o SCA da Procempa | contratado; vencedor não localizado | [TR](https://prefeitura.poa.br/sites/default/files/usu_doc/sites/dmae/ANEXOXII-TR.pdf) |
| DMAE, 2020 | licitação anterior contestada: parecer da Procempa (nov/2019) não recomendava o GSAN por ser "defasado" e de custo elevado; sindicato estimou R$ 12 milhões em customização | histórico | [Brasil de Fato, 24/07/2020](https://www.brasildefato.com.br/2020/07/24/sindppd-rs-denuncia-licitacao-para-contratar-sistema-de-ti-mais-caro-pelo-dmae/) |
| CASAL/AL | Licitação 05/2026 de sistema comercial com migração dos dados do sistema atual | em andamento em abr/2026 | [CASAL, esclarecimento](https://www.casal.al.gov.br/app/uploads/2026/04/RESP_ESCLAR_01_LIC_05_2026.pdf) |

O que isso diz ao Ziip SAN: (a) existe demanda por **migração a partir do GSAN** (importador ED-09 e P1.2, P2.10),
com DDL conhecido pelos `*.hbm.xml`; (b) o argumento de venda contra o GSAN em nuvem é justamente o que o plano já
prevê: multi-tenant, LGPD por desenho, app offline, Pix, API; (c) o modelo de "customização por empresa" do GSAN
(classes por empresa) é o anti-padrão que o ADR-0002 substitui por parâmetros do tenant.

---

## 3. Mercado: fornecedores e licitações de autarquias

### 3.1 Fornecedores localizados

| Fornecedor | Produto e escopo de saneamento | Presença | Fonte |
|---|---|---|---|
| IPM Sistemas | Atende.Net, nativo web e em nuvem, com solução de Água e Esgoto dentro da suíte de gestão pública | SAMAEs de Jaguaruna, Morro da Fumaça, Araranguá, São Bento do Sul e São Francisco do Sul (SC), 2021–2022 | [IPM, Jaguaruna](https://www.ipm.com.br/blog/administracao-geral/jaguaruna-sc-moderniza-gestao-publica-municipal-tecnologia-nativa-web/); [IPM, Morro da Fumaça](https://www.ipm.com.br/blog/administracao-geral/tecnologia-100-nuvem-gestao-publica-morro-fumaca/); [IPM, Araranguá](https://www.ipm.com.br/entidades-publicas-ararangua-tecnologia-ipm-para-gestao-municipal/) |
| Betha Sistemas | suíte de gestão pública em nuvem; "Betha Fatura" com emissão da conta de água na leitura (2008); público-alvo inclui SAMAE | SC e região; caso de São Ludgero | [Betha](https://www.betha.com.br/); [Betha Fatura, 2008](http://e-gov.betha.com.br/www-services/mediacenter/noticias/index.jsp?anoticia=2008&noticia=172) |
| Fiorilli Software | SIA 8, módulo Água, Esgoto e Saneamento: cadastro de unidades, hidrometria com lacres e troca, leitura com ocorrências, faturamento medido ou estimado com refaturamento e simulação em lote, leiaute para gráfica, emissão simultânea, **OS com almoxarifado**, análises laboratoriais, **SNIS**; app de leitura com emissão simultânea; "Serviços Web para Saneamento" 3.10 (2025) | prefeituras e SAAEs, SP e outros | [Fiorilli, SIA](https://fiorilli.com.br/sistemas/sia-sistema-integrado-de-arrecadacao); [Fiorilli Docs, SIA](https://docs.fiorilli.com.br/sia/sobre.html); [Downloads](https://fiorilli.com.br/downloads) |
| Acqua Manager | sistema comercial para concessionárias privadas (2008–2010: "60% do mercado privado"; primeiro cliente público Cesama, 120 mil ligações) | dado antigo; site atual não localizado | [Baguete, 2010](https://www.baguete.com.br/noticias/acqua-manager-estreia-no-setor-publico) |
| GSAN (fornecedores de serviço) | empresas que implantam e customizam o GSAN para DMAE e DAEB | nomes não localizados nos resultados | ver §2.1 |

Não localizados nesta pesquisa: "Sansys" como sistema de saneamento (o único resultado é uma empresa indiana de
saúde); páginas de produto de saneamento da Betha e da IPM com lista de funcionalidades. Os concorrentes de GIS
(Gisbase/Hidrogeo e outros) estão no levantamento `19b`.

### 3.2 O que as autarquias pedem em 2026

| Órgão | Objeto | Fonte |
|---|---|---|
| SAAE Paraisópolis/MG | sistema integrado de informações gerenciais de saneamento, automação dos setores comercial e operacional, locação; abertura 20/07/2026 | [Alerta Licitação](https://alertalicitacao.com.br/!licitacao/PNCP-05114647000168-1-000031-2026) |
| SAAE Pirapora/MG | leitura, faturamento e arrecadação integrados, locação de software, app mobile, coletores, hospedagem e suporte; **R$ 151.733,00** | [Alerta Licitação](https://alertalicitacao.com.br/!licitacao/PNCP-23535271000147-1-000040-2026) |
| Terra Nova do Norte/MT | software de medição de consumo e impressão de faturas com código de barras e **QR Code Pix**, gerenciador web e app; abertura 13/05/2026 | [Alerta Licitação](https://alertalicitacao.com.br/!licitacao/MN-5108055-8-920268-92026) |
| SANEAR Colatina/ES | sistema comercial com faturas, arrecadação, **dívida ativa**, autoatendimento na internet e **app de OS**; abertura 23/07/2026 | [Alerta Licitação](https://alertalicitacao.com.br/!licitacao/DM-N-ES-1828738) |
| SAAE Alfredo Chaves/ES | locação de sistema de gestão comercial e operacional **100% web (cloud)**, com implantação, conversão de dados, treinamento e suporte por 12 meses | [Alerta Licitação](https://alertalicitacao.com.br/!licitacao/DM-N-ES-1840445) |
| Lages/SC | leitura informatizada com coletor em smartphone, emissão e entrega de faturas de água, esgoto e resíduos, faturas para imóveis sem hidrômetro, avisos e reavisos | [Lages, TR](https://licitacoes.lages.sc.gov.br/assets/licitacao/bc9edbe355a2bd16723d3efa5eba8c23.pdf) |
| SAE Catalão/GO | software integrado de gestão comercial de saneamento | [Catalão](https://catalao.go.gov.br/site/v4/upload/licitacao/6196738a1eb68b1703e810c23a80b7d8.pdf) |

Leitura para o plano: o pacote mínimo que o mercado de autarquias compra hoje é leitura em app com emissão
simultânea, faturamento, arrecadação com Pix, dívida ativa, OS em app e autoatendimento, tudo em nuvem e por locação
mensal. O P1–P3 e a onda A da trilha cobrem esse pacote; a dívida ativa (ED-31) e o app de OS (ED-20) estão na trilha
e aparecem nos editais de autarquias pequenas, o que reforça a onda A para ambos.

### 3.3 Boas práticas de "meter-to-cash" (fontes internacionais, uso com reserva)

Fontes de fornecedores e da Water Research Foundation (projeto 4583, 2016) convergem em: validação de leitura antes do
faturamento (camada VEE), tarifas configuráveis sem código, visão única do cliente no atendimento, autoatendimento com
histórico, integração sem silos entre medição, faturamento e contabilidade, acompanhamento do prazo médio de
recebimento. O Ziip SAN já tem isso por desenho (motores puros, parâmetros do tenant, portal, outbox). Fontes:
[WRF 4583](https://waterrf.org/research/projects/planning-and-implementing-cis-and-amrami-projects),
[Itineris](https://itineris.net/knowledgebase/how-does-a-cis-handle-meter-to-cash-processes-for-water-utilities/),
[VertexOne](https://www.vertexone.ai/blog/what-is-a-meter-to-cash-process-and-how-can-utilities-optimize-it).

---

## 4. Normas de referência da ANA em 14/10/2026

Fonte: [ANA, Normativos publicados](https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA)
(acesso 14/10/2026). Normas de referência são diretrizes para as entidades reguladoras infranacionais; a aplicação ao
prestador depende da norma local, mas a comprovação de adoção pela ANA (Res. 134/2022) e o acesso a recursos federais
levam as agências a incorporá-las.

| NR | Ano | Tema | Resolução | Efeito no sistema comercial |
|---|---|---|---|---|
| 1 | 2021 | cobrança pelo manejo de resíduos sólidos | 79 | cofaturamento de resíduos (ED-25) |
| 6 | 2024 | modelos de regulação tarifária | 183 | parâmetros de tarifa por agência |
| 7 | 2024 | condições gerais de limpeza urbana e resíduos | 187 | catálogo de serviços de resíduos |
| 8 | 2024 | metas de universalização e indicadores de acesso | 192 | indicadores (ED-48) |
| 9 | 2024 | indicadores operacionais de água e esgoto | 211 | catálogo de indicadores (ED-48); já citada no 08 |
| 10 | 2024 | reajuste tarifário | 228 | vigência de tabelas tarifárias |
| **11** | **2024** | **condições gerais da prestação de água e esgoto** | **230** | **cadastro, medição, fatura, corte, religação, atendimento, prazos (§5)** |
| 12 | 2025 | drenagem e manejo de águas pluviais | — | cadastro de drenagem (ADR-0034, ED-64); já citada no 19c |
| **13** | **2025** | **estrutura tarifária e tarifa social** | **271 (18/11/2025)** | **parcela fixa e variável, categorias e faixas, tarifa social por CadÚnico e BPC, cofaturamento, cobrança por disponibilidade** |
| 14 | 2025 | indicadores de limpeza urbana e resíduos | — | indicadores de resíduos |
| 15 | 2025 | redução progressiva e controle de perdas | 275 | balanço hídrico e metas (P1.26, intelligence) |
| 16 | 2026 | contabilidade regulatória | 302 | plano de contas regulatório (ED-32) |

Não há NR própria de atendimento ao usuário nem de interrupção: esses temas estão dentro da NR 11. Fontes
complementares da NR 13: [ANA, voto da Res. 271/2025](https://www.gov.br/ana/pt-br/assuntos/governanca-regulatoria/analise-de-impacto-regulatorio-air/air-realizadas/Res.271.2025Voto.pdf),
[Agência Infra, 24/11/2025](https://agenciainfra.com/blog/ana-aprova-norma-de-referencia-para-estrutura-tarifaria-e-tarifa-social/),
[Cescon Barrieu](https://cesconbarrieu.com.br/nova-norma-de-referencia-da-ana-estabelece-regras-para-estrutura-tarifaria-e-tarifa-social-de-agua-e-esgoto/),
[ANA, guia da tarifa social](https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/tarifa-social-de-agua-e-esgoto/orientacoes-para-implementacao-da-tarifa-social-de-agua-e-esgoto/guia-orientacoes-tarifa-social.pdf).
A NR 13 não se aplica a contratos de concessão licitados antes da sua vigência, salvo aditivo; para autarquias e
contratos de programa, aplica-se pela norma da agência local.

---

## 5. NR 11/2024: o que o sistema comercial precisa fazer

Fonte primária: [Manual orientativo da NR 11/2024](https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/manual-nr11-final.pdf),
que traz o texto da norma (Res. ANA 230/2024) em anexo; texto extraído localmente em 14/10/2026. A verificação da
adoção pelas agências começa em **20/05/2027** (art. 74, § 3º), ou seja, antes do lançamento comercial do Ziip SAN:
as agências dos clientes-alvo estarão publicando ou revisando suas normas de condições gerais exatamente no período
em que o produto chega ao mercado.

| Art. | Exigência | GSAN (levantamentos 01–04, 20a–20e) | Ziip SAN hoje | Lacuna ou ação |
|---|---|---|---|---|
| 8º, § 1º e § 6º | comunicar disponibilidade de rede; levantar e informar ao titular e à agência as edificações não conectadas | não há rotina de "imóvel factível não ligado" como relatório regulatório | situação de ligação factível e potencial no cadastro (ED-13) | relatório de não conectados e aviso ao usuário (ED-13, P3.22) |
| 9º | informar ao interessado existência de rede, pressões e DN | GeoSan, sem ficha de atendimento | GIS-06 e GIS-08 | ficha de viabilidade na tela de atendimento (GIS-08, ED-35) |
| 10 | nova ligação de água só com pedido simultâneo de esgoto onde há rede | regra não encontrada | não prevista | regra de atendimento com parâmetro classe b (ED-13, ED-35) |
| 18 | contrato de adesão com cláusulas mínimas, inclusive juros e multa | não há contrato de adesão | ADR-0022 (documentos no portal) | modelo de contrato de adesão por tenant, versionado, aceito no portal (P3.20, ED-34) |
| 19 | contrato específico com grandes usuários | "contrato de demanda" parcial | órgão centralizador e usuário avulso (ED-22) | contrato especial com volume, vigência e preço (ED-22) |
| 21 | um único código por unidade usuária | inscrição e matrícula | matrícula por sequência (P1.4) | coberto |
| 22 | cadastro mínimo, com **histórico de leituras e faturamento de 120 meses**, medidor com datas de instalação e última aferição, categorias mínimas, LGPD | histórico sem retenção definida; aferição parcial | retenção de 120 meses no portal (P3.20, P2.10 importador de 120 meses); hidrometria com aferição (ED-14) | garantir retenção de 120 meses também no backoffice e no importador, com classe a |
| 23 e 24 | classificação do não residencial pela estrutura tarifária; dever do usuário de informar alteração | categorias e subcategorias | R2 cadastro (P1.3) | coberto; registrar declaração do usuário (ED-12) |
| 28 | condomínio: ligação única com medição individualizada | rateio de condomínio (02 §3) | regra de condomínio (ED-15) | coberto na trilha |
| 31 | monitorar consumo por hidrômetro certificado pelo Inmetro | cadastro de hidrômetro | ED-14 com validade Inmetro | coberto |
| 33 | fatura com **opções de datas de vencimento**; intervalo de leitura padronizado e divulgado; fatura em nome do usuário; esgoto só onde há sistema | vencimento alternativo via RA; esgoto por situação | P2.5 ciclo e conta | opções de vencimento como regra de fábrica, não exceção (P2.5); divulgação do calendário no portal (P3.3) |
| 34 | cofaturamento de outros serviços de saneamento | conta com resíduos possível | ED-25 | coberto |
| 35 | sem leitura, cobrança pela **média** de N meses, N da agência; alternativas sem hidrômetro e sem histórico | média configurável; estimado | parâmetro `metering.average_months` a criar | parâmetro classe b com limite legal por agência (PARAMETROS-DO-TENANT, P2.5) |
| 36 | consumo medido distribuído nas faixas e multiplicado pela tarifa da faixa, por categoria | motor de tarifa por faixas | motor de tarifa (P2.5) | coberto; teste de aceite com o exemplo da NR |
| 37 | mecanismo para **identificar e informar pagamento em duplicidade** | classificação "duplicidade" e devolução via RA | classificação de pagamento (P3.1) | aviso ativo ao usuário e crédito automático (P3.1, P3.5) |
| 38 | revisão de fatura a pedido, com diretrizes da agência | RA de revisão | ED-23 | coberto na trilha |
| 39 | continuidade 24 h; comunicar interrupções à agência e aos usuários | programação de abastecimento e manutenção | interrupção programada (ED-36, P3.4) | aviso automático por área (P3.5, ED-41) |
| 41 | interrupção acima de 24 h: fornecimento de emergência a serviços essenciais, com volume medido e cobrado | não há | não previsto | cadastro de unidades essenciais e OS de fornecimento de emergência com volume (ED-36) |
| 42 e 43 | hipóteses de interrupção; por inadimplência só com **comunicação prévia** dos motivos e providências, observada a Lei 13.460/2017 | aviso de corte por ação de cobrança | G9 corte e religação (classe b com limite legal) | prova de entrega do aviso (P3.5) e bloqueio de corte sem aviso (P3.4) |
| 45 a 47 | religação no prazo da agência; religação de urgência com preço e prazo informados | religação e religação de urgência | G9 | coberto; prazos por agência |
| 48, XIV | usuário anota o protocolo | protocolo do RA | protocolo em todo canal (P3.3, ED-35) | coberto |
| 49 e 50 | atos irregulares e sanções pela agência | auto de infração | ED-24 | coberto na trilha |
| 52 | atendimento **24 h por 7 dias** por telefone, meio eletrônico e posto presencial; **protocolo** para toda solicitação; ouvidoria recomendada | RA com protocolo; sem ouvidoria | portal, chamados, omnichannel (P3.3, ED-35, ED-42) | canal eletrônico 24x7 é o portal; ouvidoria como fila própria (ED-35) |
| 53 | manual ou regulamento da prestação no site e no posto | não há | Ajuda e Suporte (ADR-0020) | publicar o regulamento do tenant no portal (P3.3, ADR-0020) |
| 54 | **relatório mensal de reclamações** por motivo e sistema, com percentual não atendido, para a agência | relatórios gerenciais de RA | relatórios (P3.22, ED-45) | relatório regulatório nativo (P3.22) |
| 55 | portal e app com informações de cobrança e medição com **pelo menos 12 meses** | loja virtual | portal com 120 meses (P3.20) | coberto |
| 56 e 57 | reparo e ressarcimento de danos; prazos de recomposição de pavimento | OS de repavimentação | ED-36 | coberto na trilha |
| 58, VI e VII | protocolo em todo atendimento; dados ao **SINISA** | protocolo; SINISA assistido | ADR-0004, ED-48 | coberto |
| 59 | cadastro técnico atualizado, preferencialmente georreferenciado, com as built | GeoSan | ADR-0034 | coberto |
| 72 e 73 | lista de serviços com prazos aprovados pela agência; serviços sem prazo acordados na solicitação | prazo por especificação | catálogo de serviços com prazo (ED-34) | prazo por serviço versionado e ligado à norma da agência (ED-34) |

---

## 6. Santa Catarina: ARIS RN 19/2019

Fonte: [ARIS, RN 19/2019 compilada](https://www.aris.sc.gov.br/uploads/pagina/2729/WnCs4v1qY6GoBmaF1AeM8ao98SVihka7.pdf)
(acesso 14/10/2026; alterada pela RN 20/2019, RN 38/2023 e, nos arts. 75 e 76, pela RN 40/2024 conforme extrato no
DOM/SC). Texto extraído localmente; a matriz artigo a artigo está no documento 20f. Regras numéricas com efeito direto
no Ziip SAN, a confirmar na redação vigente:

| Tema | Regra | Efeito no produto |
|---|---|---|
| Atendimento | telefone 24 h por dia, inclusive fins de semana e feriados; resposta ao usuário em até 15 dias; protocolo informado; atendimento prioritário | ED-35, P3.3, P3.5 |
| Direitos | religação em até 4 horas em determinada hipótese; aviso de interrupção programada com 2 dias úteis; aferição a pedido quando houver indício de erro | G9 e ED-14 com limites por agência |
| Prazos de serviço | 5 dias úteis para vistoria e 10 dias úteis para a ligação após aprovação; 30 dias no art. 29 | ED-34 catálogo de prazos |
| Hidrômetro | aferição a pedido com regra de custo por resultado (art. 76); comunicados com 15 e 30 dias | ED-14 |
| Ciclo | ciclo de cerca de 30 dias, mínimo 27 e máximo 33; faturamento inicial não inferior a 15 dias; mudança de calendário avisada com 30 dias | P2.5 com parâmetros classe b |
| Vencimento | **6 datas de vencimento** à escolha; prazo mínimo de **5 dias úteis** (10 para a categoria Pública) entre apresentação e vencimento | P2.5, parâmetro classe a por agência |
| Média | média limitada a **2 faturas sequenciais em 12 meses**; retenção de fatura para verificação com comunicação imediata (art. 88) | P2.5, ED-23 |
| Conteúdo da fatura | XVII incisos obrigatórios, entre eles multa e mora, ouvidorias do prestador e da ARIS, indicação de parcelamento, aviso de alta de consumo | P2.8, P2.17 |
| Revisão | recurso em 10 dias; decisão em 10 dias; refaturamento com vencimento em 3 dias úteis; recurso à ARIS em 10 dias | ED-23 |
| Cobrança | juros, **multa de 2%** e correção pelo INPC (art. 108); parcelamento só com termo de acordo | G5 (padrão 2% já decidido), P3.4 |
| Irregularidade | perícia, notificação com AR, recuperação de consumo em até 12 ciclos, recurso à ARIS em 15 dias | ED-24 |
| Interrupção | aviso de interrupção programada com 48 horas; serviços essenciais acima de 18 horas; comunicar o usuário em 24 horas após o corte com faturas que o motivaram | P3.4, P3.5, ED-36 |
| Corte por inadimplência | aviso prévio com **30 dias**; **vedado cortar por faturas vencidas há mais de 120 dias**; vedado cortar por impedimento de acesso ao hidrômetro sem notificação; interrupção fora da norma é indevida | G9: limites legais novos para `cut.notice_days` e `cut.max_overdue_days` |

A regra dos 120 dias é a que mais muda o desenho da cobrança: a ação de corte precisa filtrar por idade da fatura e o
parâmetro de corte do tenant em SC ganha um teto que não existe nos demais estados. Entra como limite legal por agência
no catálogo G9 (ADR-0015).

Outras agências de SC: a **AGIR** (19 municípios regulados, Vale do Itajaí) e a **ARESC** (estadual, página de
resoluções de água em [aresc.sc.gov.br](https://aresc.sc.gov.br/resolucao-agua/page/4/)) não tiveram texto de
condições gerais localizado nesta pesquisa. Fonte sobre a AGIR: [relatório anual 2023](https://responsabilidadesocial.alesc.sc.gov.br/uploads/documento/comprovacao_balanco_arquivo/Relato_769_rio_Anual_2023.pdf).

---

## 7. Efeitos propostos para o plano (a consolidar no documento 20)

1. **Catálogo de limites legais por agência** (ADR-0015, PARAMETROS-DO-TENANT): incluir ARIS RN 19/2019 com
   vencimento mínimo, 6 datas, ciclo 27–33, média em 2 de 12, aviso de corte 30 dias, teto de 120 dias, multa 2%,
   INPC; incluir NR 11 como piso nacional (média, aviso prévio, 120 meses, 12 meses no portal).
2. **P2.5:** opções de vencimento, calendário de leitura publicado, limite de faturas por média e retenção de fatura
   como regras de fábrica parametrizadas.
3. **P3.4 e P3.5:** aviso prévio de corte com prova de entrega e bloqueio por idade da fatura; comunicação em 24 horas
   após o corte.
4. **P3.22:** relatório mensal de reclamações no formato do art. 54 da NR 11 e relatório de imóveis não conectados
   (art. 8º).
5. **ED-34:** catálogo de serviços com prazos por agência, versionado.
6. **ED-36:** fornecimento de emergência a unidades essenciais com volume medido.
7. **P3.20 e ADR-0020:** contrato de adesão e regulamento da prestação publicados no portal, por tenant.
8. **Importador (P1.2, P2.10, ED-09):** perfil GSAN como origem prioritária, dado que as autarquias que trocam o GSAN
   são o alvo comercial mais imediato.
9. **Pendência §2.4:** confirmar com a ARIS a redação vigente dos arts. 76, 108 e 153 e as normas da AGIR e da ARESC
   antes da homologação local.

## 8. Limites

- Os textos integrais da NR 13/2025 e da Res. 271/2025 não foram abertos; o resumo vem do voto da diretoria e de
  fontes secundárias.
- Os resultados das licitações de 2026 (Bagé, CASAL, autarquias do §3.2) não foram conferidos no PNCP.
- Páginas de produto de Betha e IPM com lista de funcionalidades de saneamento não foram localizadas; a comparação
  funcional com concorrentes diretos fica parcial.
- A RN 19/2019 da ARIS foi lida por trechos; a matriz artigo a artigo é do documento 20f.
