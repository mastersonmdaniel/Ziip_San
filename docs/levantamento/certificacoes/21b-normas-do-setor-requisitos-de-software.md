# 21b — Normas do setor de água e esgoto: requisitos de software para o módulo "Certificações de qualidade"

Data: 21/10/2026. Complementa o [21a](21a-iso-gestao-requisitos-de-software.md), que trata das normas ISO de sistema de gestão (9001, 14001, 45001, 55001, 27001 e 27701). Este documento trata das normas do próprio setor: as voluntárias (ISO 24510, 24511, 24512, ISO/IEC 17025, ISO 46001, ISO 37120 e o PNQS), as técnicas de referência (medidores, rede, esgoto, cadastro e topografia) e as regulatórias obrigatórias, que entram no mesmo catálogo marcadas como não desativáveis.

Estado: plano e documentação. Nenhum código foi escrito ou alterado e nenhum outro arquivo do repositório foi editado. Base documental: plano v2.16 (`docs/PLANO-EXECUCAO-PDCA-R0-R9.md`), `arquitetura/PARAMETROS-DO-TENANT.md`, levantamentos `08`, `09`, `10`, `gis/19c`, `editais/18d`, `18g` e `gsan/20f`, e `certificacoes/21a`.

> **Aviso.** Não é parecer jurídico nem laudo técnico. As normas ISO e ABNT são protegidas por direito autoral: o documento cita número de cláusula ou de artigo e descreve o conteúdo em palavras próprias, sem transcrever. Onde o texto da norma não foi lido, está escrito **[A CONFIRMAR]**. Resoluções, portarias e leis são textos públicos, mas o resumo aqui também é feito em palavras próprias.

---

## 0. Leitura rápida

1. **Nenhuma das normas voluntárias de serviço (ISO 24510, 24511, 24512) é certificável.** O texto das três diz que o objetivo não é fixar especificações para certificação direta e que o uso é voluntário. São diretrizes para avaliar e melhorar o serviço, com indicadores de exemplo. O que o software entrega é um sistema de indicadores e o registro do ciclo de avaliação.
2. **A ISO/IEC 17025 é acreditável, não certificável.** A acreditação é do laboratório, pela Cgcre/Inmetro. A Portaria GM/MS 888/2021 a exige do laboratório que gera dado de potabilidade (arts. 20, 21 p.ú. e 22 §3, conforme o levantamento 09). Por isso é a única norma voluntária do grupo que, na prática, o cliente tende a ter ligada. O plano já tem a ED-51 para ela, mas a ED-51 cobre cinco itens e a norma pede mais (§3.4).
3. **A ISO 46001 é certificável, mas foca o consumidor final de água.** Para um prestador, aplica-se ao uso próprio (ETA, ETE, prédios, frota) e a programas de grandes usuários. Relevância média-baixa; está em revisão (ISO/AWI 46001).
4. **ISO 37120 e PNQS não pedem módulo próprio.** A 37120 é norma de cidades (quem a aplica é o município, o prestador fornece dado). O PNQS é prêmio da ABES, não norma nem certificação. Ambos se atendem com exportação de indicadores e série histórica, sobre o mesmo motor de indicadores (§3.6 e §3.7).
5. **A parte difícil é o indicador, não a ISO.** O mesmo conjunto de variáveis (perdas por ligação, coliformes no padrão, reclamações, hidrometração, macromedição, intermitência) alimenta a ISO 24510/24512, a NR 9/ANA, a NR 15/ANA e o SINISA. Dos 24 indicadores mapeados no §3.2, 13 ficam calculáveis com o que o plano já prevê (vários só depois das tarefas da trilha de editais), 7 dependem de dado-fonte que o plano entrega só em parte e 4 não têm dado-fonte em nenhum módulo.
6. **Medidores.** A Portaria Inmetro 155/2022 trocou as classes A a D por classes de exatidão 1 e 2, definiu vazões Q1 a Q4 e fixou o intervalo máximo de verificação subsequente em 7 anos contados do ano da instalação. O campo `meters.metrological_class` (herança das classes antigas) e o campo livre `inmetro_approval` não representam isso (§4.1). Há dois marcos de transição já em curso: o fim da verificação inicial de modelos aprovados pela Portaria 246/2000 (passou) e o fim da verificação subsequente deles (adiante).
7. **A maior lacuna do plano está no lado do esgoto e da água bruta.** CONAMA 357/430/498, outorga, licença ambiental, Declaração de Carga Poluidora e Plano de Segurança da Água não têm tarefa no plano (EF-01 a EF-09, LO-01 e SG-07 do levantamento 09). Os dois atos do CONAMA aprovados em setembro de 2026 (efluentes e reúso) ainda não estavam publicados na consulta mais recente que encontrei (16/10/2026), então o catálogo precisa suportar vigência futura.
8. **Correções ao enunciado.** (a) CONAMA 503/2021 trata de fertirrigação com efluentes de indústrias de alimentos, bebidas, laticínios, frigoríficos e graxarias; não se aplica a esgoto sanitário nem a lodo de ETE (levantamento 09). (b) NR 15/2025/ANA é a Resolução ANA 275/2025 (perdas); o enunciado não trazia o número. (c) A NR 9/ANA (Resolução 211/2024) não estava na lista pedida, mas foi incluída porque define os indicadores que cruzam com as ISO 24510/24512.

---

## 1. Método, convenções e limites

### 1.1 Método

- Leitura dos documentos do projeto citados acima. Os levantamentos `09`, `19c` e `20f` não foram refeitos; este documento os cita pelos IDs deles (QA-, SG-, LB-, EF-, LO-, RU-, ID- no `09`; LEG- no `20f`) e só acrescenta o que muda a resposta.
- Pesquisa em fontes públicas em 21/10/2026: páginas de catálogo da ISO (24510, 24511, 24512, 46001, 17025, 37120) e pré-visualizações públicas de sumário; Resoluções ANA 211/2024 e 275/2025 em gov.br; Portarias Inmetro 155/2022 (por transcrições de terceiros do DOU), 657/2025 e 713/2025 (DOU); páginas do Inmetro sobre verificação; pnqs.com.br e ABES; notícias do MMA e de veículos especializados sobre o CONAMA.
- Código: conferência de existência de arquivos e de colunas (`V5_1` de medição, pacote `intelligence/sigsan-quality`), sem avaliar qualidade e sem rodar teste. O estado do restante segue o `18g` e o `09` (12/10/2026), não reverificado.
- Plano: leitura por busca de ID. Todo ID de tarefa citado foi encontrado no plano v2.16.

### 1.2 Convenções

- **[INFERÊNCIA]**: dedução do autor, sem confirmação documental. Reunidas no §7.2.
- **[A CONFIRMAR]**: fato que depende do texto adquirido da norma ou de fonte que não abriu. Reunidos no §7.3.
- **Proposta**: decisão de desenho sugerida aqui, sem aceite do dono do produto.
- "NR 11/ANA", "NR 13/ANA" etc. designam as Normas de Referência da ANA, como no `21a`; "NR-7/MTE" seria a Norma Regulamentadora do Trabalho. Aqui só aparecem as da ANA.
- Chaves `cert.*` e entidades `cert_*` são as provisórias do `21a` §3. Tarefas propostas aqui usam o prefixo **CQS-** (certificações do setor), para não colidir com CQ-01 a CQ-15 do `21a`. Nenhum dos códigos existe no plano.
- Datas de normas são marcos normativos, citados porque definem o que o software precisa calcular. Não são prazos do projeto.

### 1.3 Limites

- Os textos pagos das ISO 24510/24511/24512, 17025, 46001 e 37120 e das NBR não foram lidos na íntegra. A numeração de subitens e a lista oficial de indicadores do Anexo A (ISO 24510) e do Anexo E (ISO 24511/24512) não foram conferidas. Os indicadores do §3.2 são **exemplos de formulação própria** agrupados pelos critérios cujos títulos foram confirmados no sumário público; não são a lista da norma.
- O texto da Portaria Inmetro 155/2022 no DOU (in.gov.br) não abriu. Os itens citados vêm de duas transcrições independentes e de páginas do Inmetro; ver §4.1 e §7.3.
- Normas do próprio Ziip (ISO 9001, 27001 e semelhantes como certificação do fornecedor) estão fora deste documento; ver `21a` §8.3.

---

## 2. Quadro-síntese das normas

Colunas: **Natureza** (diretriz, requisito, regulamento); **Certificável?**; **Grupo no catálogo** (proposta); **Ativação**.

### 2.1 Voluntárias

| Norma | Edição e situação | Natureza | Certificável? | Ativação no módulo |
|---|---|---|---|---|
| ISO 24510 | 2ª edição, 2024-01; substitui a de 2007 (retirada); publicada | Diretriz: avaliação e melhoria do serviço aos usuários, do ponto de vista do usuário | **Não.** Texto declara que não visa certificação direta; uso voluntário | Liga e desliga (`cert.iso24510.enabled`) |
| ISO 24511 | 2ª edição, 2024-02; substitui a de 2007; publicada | Diretriz: gestão de serviços de esgoto e avaliação | **Não** (mesmo texto de objetivo) | Liga e desliga |
| ISO 24512 | 2ª edição, 2024-02; substitui a de 2007; publicada | Diretriz: gestão de serviços de água potável e avaliação | **Não** (mesmo texto de objetivo) | Liga e desliga |
| ISO/IEC 17025 | Edição de 2017 (3ª); a ISO a mantém como edição vigente; nenhuma revisão publicada encontrada | Requisitos de competência de laboratórios de ensaio e calibração | **Acreditação** (Cgcre/Inmetro, Rede Brasileira de Laboratórios de Ensaio), não certificação | Liga e desliga por laboratório; exigida pela Portaria 888 para o laboratório de potabilidade |
| ISO 46001 | Edição de 2019 (1ª); **revisão em desenvolvimento** (ISO/AWI 46001, substituirá a de 2019) | Requisitos de sistema de gestão da eficiência no uso da água, com orientação | **Sim**, por organismo de certificação; é a primeira norma de requisitos do ISO/TC 224 | Liga e desliga |
| ISO 37120 | Edição de 2018 (2ª); confirmada na revisão periódica, **em revisão** (ISO/WD 37120) | Indicadores de serviços urbanos e qualidade de vida, para cidades | Não é certificação ISO; existe verificação por terceiro pela WCCD (programa privado) [fonte secundária] | Perfil de exportação, sem módulo próprio |
| PNQS (ABES) | Ciclo 2026 com inscrições em 2026 nas categorias IGS, PEOS e PGA; modelo MEGSA ESG | Prêmio de gestão baseado em modelo de excelência | **Não** é norma nem certificação. A ABES tem produto separado ("ABES ESG Index") descrito como certificação em 2022 | Perfil de evidências, sem módulo próprio |

### 2.2 Técnicas de referência

| Norma | Situação (fonte) | Natureza | Ativação |
|---|---|---|---|
| Portaria Inmetro 155/2022 | RTM consolidado para medidores de água potável fria e quente usados em tarifação; em vigor desde 01/06/2022 | **Regulamento obrigatório** de metrologia legal | **Travada** (classe a) para todo tenant com medição |
| NBR 16043 (partes 1 a 4) | Edição 02/2021 (revendedor) | Norma de produto e de instalação de medidores | Referência; ativável |
| NBR 15538 | Edição 2023 (a de 2014 foi revisada) | Ensaios de avaliação de eficiência de medidores (IDM, blindagem magnética) | Referência contratual de recebimento; ativável |
| NBR 12218 | 2017, confirmada em 01/2022 (`19c`) | Projeto de rede de distribuição de água | Referência do GIS e do EPANET; ativável |
| NBR 9649 e 9648 | 1986 (`19c`) | Projeto e concepção de rede coletora de esgoto | Referência do GIS e do SWMM; ativável |
| NBR 12586 e 12587 | 1992; 12587 confirmada em 01/2022 (`19c`) | Cadastro de sistema de água e de esgoto | Referência do catálogo do GIS; ativável |
| NBR 13133 e 14166 | 13133:2021; 14166:2022 (`19c`) | Levantamento topográfico; rede de referência cadastral municipal | Referência de metadados do GIS; ativável |

### 2.3 Regulatórias obrigatórias (não desativáveis)

| Norma | Resumo do que alcança o software | Aplicabilidade condicional |
|---|---|---|
| NR 11/ANA (Res. 230/2024) | Condições gerais: cadastro, medição, fatura, corte, atendimento | Por ERI que a adote (`reg.ana_nr_adoption`) |
| NR 13/ANA (Res. 271/2025) | Estrutura tarifária e tarifa social | Idem |
| NR 15/ANA (Res. 275/2025) | Balanço hídrico padronizado e plano de redução de perdas | Serviço de água; verificação pela ANA a partir de 20/05/2028 (art. 14 p.ú.) |
| NR 9/ANA (Res. 211/2024), complementar | Indicadores operacionais de nível I e II | Idem NR 11 |
| Portaria GM/MS 888/2021 e SISAGUA | Controle e vigilância da qualidade da água | Serviço de água |
| CONAMA 357/2005 e 430/2011 | Classes do corpo receptor e lançamento de efluentes | Serviço de esgoto; 430 a ser revogada por resolução aprovada em 09/2026, ainda sem publicação confirmada |
| CONAMA 498/2020 | Biossólido em solos | Só se o tenant destina lodo a uso agrícola |
| CONAMA 503/2021 | Fertirrigação com efluentes de certas indústrias | **Não se aplica a esgoto sanitário.** Entra no catálogo só para esclarecer; sem requisito de software |
| Lei 11.445/2007 | Marco do saneamento | Todos |
| Leis 14.015/2020, 14.898/2024, 13.460/2017, 12.007/2009 | Corte, tarifa social, direitos do usuário, quitação anual | Todos (14.898 com a ERI) |
| Decreto 5.440/2005 | Informação ao consumidor sobre qualidade da água | Serviço de água |
| LGPD (Lei 13.709/2018) | Proteção de dados pessoais | Todos |

---

### 2.4 Requisitos transversais do módulo, vistos do lado do setor

O desenho geral do módulo (ativação por norma, mapa de cláusulas, `cert.<norma>.enabled`, desligar nunca apaga registro, pacote de evidência sem dado pessoal) está no `21a` §3 e vale aqui sem alteração. As linhas abaixo são o que as normas do setor acrescentam.

| ID | Requisito | Por que o setor muda o desenho | Capacidade do `21a` |
|---|---|---|---|
| SET-01 | **Duas classes de entrada no catálogo.** Voluntária: `cert.<norma>.enabled` liga e desliga, com aceite. Obrigatória: sem chave de ativação; o que o tenant configura é a **aplicabilidade** (serviço de água, de esgoto ou ambos; ERI; porte; destino do lodo) | O `21a` trata de normas que o cliente escolhe. Aqui há normas que o cliente não pode desligar (classe a do ADR-0015) | C11 (CQ-07) |
| SET-02 | **Vigência futura e vigência por edição.** Entrada de catálogo com `valid_from`/`valid_to`, inclusive para ato aprovado e ainda não publicado (CONAMA em 09/2026) e para regime de transição (Portaria 155, Anexo D) | A 430/2011 vale até a nova resolução ser publicada; a 888 está sem revisão apesar do prazo do art. 51; a 155 tem marcos de transição | C11 |
| SET-03 | **Catálogo normativo versionado fora do schema do tenant**, como recomenda o `09` §4 (o seed atual `quality_parameter` está gravado por tenant e amarrado à coluna `portaria_ms_888`) | Mudança de norma não pode exigir migration em todos os tenants | C11, ED-50 |
| SET-04 | **Motor de indicadores com ficha versionada** (variáveis de entrada, fórmula, período, informação de contexto e grau de confiança do dado) | A ISO 24510 (cl. 9.2 e 9.3, Anexo B) e a ISO 24512 (cl. 9.2, 9.3, Anexo F) descrevem exatamente esse sistema; a NR 9/ANA exige nível de confiança (arts. 15 e 18). Uma só ficha serve às duas | C05, C12; ED-48 |
| SET-05 | **Gestão documental com validade** para o que o setor exige guardar: escopo de acreditação do laboratório, certificado de calibração, portaria de aprovação de modelo de medidor, outorga, licença ambiental, ART/RRT, laudos, aprovação do plano de amostragem | `09` SG-10, EF-08, EF-09, LB-01 | C01 |
| SET-06 | **Relatório periódico ao regulador gerado da mesma fonte da evidência.** SISAGUA (mensal, plano anual), NR 9 e NR 15 (anual), SINISA (anual), relatório de reclamações (NR 11/ANA art. 54), relatório anual de qualidade ao consumidor (Decreto 5.440 art. 3º III) | A evidência de conformidade e o envio ao regulador são o mesmo dado | C12 |
| SET-07 | **Assinatura de laudo** por responsável técnico, com nível de assinatura configurável por tipo de documento (Lei 14.063, `LEG-51`) | A 888 art. 23 exige laudo assinado por responsável habilitado | C01 |


---

## 3. Normas voluntárias

### 3.1 ISO 24510:2024 — serviço aos usuários

**O que é.** Diretriz para identificar as necessidades e expectativas dos usuários e avaliar se o serviço de água e esgoto as atende, escrita do ponto de vista do usuário e não do prestador. Segunda edição, 2024-01. Ficam fora do escopo o projeto e a construção dos sistemas, a estrutura de regulação e os sistemas prediais. **Não é certificável** (ver §2.1).

**Estrutura confirmada pelo sumário público.** Cláusula 4: elementos do serviço relativos ao usuário. 5: objetivos do serviço. 6: diretrizes para atender às necessidades. 7: critérios de avaliação do serviço. 8: avaliação (8.2 política, 8.3 objetivo e escopo, 8.4 partes envolvidas, 8.5 metodologia, 8.6 critérios, 8.7 recursos, 8.8 saídas e recomendações). 9: indicadores de desempenho (9.2 sistemas de indicadores, com 9.2.3 variáveis e 9.2.4 informação de contexto; 9.3 qualidade da informação; 9.4 exemplo). Anexo A: exemplos de indicadores. Anexo B: exemplo de atribuição de grau de confiança. Nas cláusulas 5, 6 e 7 repetem-se os mesmos temas: acesso; prestação do serviço (tempo para nova ligação, reparos, preço, quantidade, qualidade da água, nível de serviço, cobertura e disponibilidade); contrato e faturamento (acordo claro, consumo, precisão, resposta a reclamação de fatura, clareza, formas de pagamento); relacionamento com o usuário (contato escrito, telefone, visita ao escritório, canal online, redes sociais, reclamações e pedidos, aviso de restrições e interrupções, aviso de situação anormal da água, informação disponível, ações com a comunidade, participação dos usuários); meio ambiente; segurança e gestão de emergência. A numeração dos subitens muda entre as versões de pré-visualização que consultei; por isso as tabelas abaixo citam o nível `x.y` dos temas, não os subitens [A CONFIRMAR na edição adquirida].

**O que a norma pede que o software gere** (em palavras próprias):

| ID | Tema (cl. 5, 6 e 7) | O que o software registra ou gera | Fonte no produto | Tarefas do plano | Situação |
|---|---|---|---|---|---|
| SU-01 | Acesso ao serviço | Domicílios e economias com rede de água e de coleta disponível, por zona; áreas sem serviço; condição de acesso de baixa renda | Cadastro de unidades, situação factível, GIS, tarifa social | P1.3, P2.23, ED-13, P1.5, P3.27 | **Parcial.** Falta a base de domicílios (IBGE) como denominador (`08` R-35, sem tarefa) |
| SU-02 | Tempo para nova ligação; reparos | Tempo da solicitação à ligação; tempo de reparo por tipo; cumprimento do prazo do catálogo de serviços | Protocolo, OS, catálogo com SLA | P3.4, P1.31, ED-34, ED-36, ED-13 | Coberta após P3.4 e ED-34 |
| SU-03 | Preço do serviço | Tarifa vigente por categoria, conta média, tarifa social | `tariff_versions`, conta | P2.5, P1.5, P3.22 | Coberta |
| SU-04 | Quantidade e nível de serviço | Volume fornecido, pressão, continuidade em horas, interrupções programadas e não programadas, racionamento | Macromedição, telemetria, cadastro de interrupção | ED-35 (interrupção programada por área), ED-17, ED-48 | **Parcial.** Interrupção não programada e cálculo de horas por ligação sem tarefa nomeada |
| SU-05 | Qualidade da água | Percentual de amostras conformes, não conformidades, comunicação ao usuário e à autoridade | `intelligence/sigsan-quality` | ED-50 | Depende da ED-50 e do catálogo revisado (`09` QA-01) |
| SU-06 | Contrato e faturamento | Contrato ou regulamento versionado e publicado; leitura e consumo; retificações; resposta a contestação; modelo de fatura da ERI; meios de pagamento | Conta, leitura, retificação, portal | P3.20 (regulamento e contrato do tenant), P2.5, P2.7, P2.20, P3.4, P3.1, P3.2, P4.5 | Coberta |
| SU-07 | Relacionamento | Canais e protocolo; reclamações por motivo; aviso de restrição e de interrupção; aviso de situação anormal da água; carta de serviços; pesquisa de satisfação; participação dos usuários | Protocolo, portal, notificações | P3.4, P3.3, P3.5, ED-35, ED-38, ED-41, ED-42 | **Parcial.** Carta de serviços e ações com a comunidade sem tarefa; aviso de situação anormal da água depende da ED-50 |
| SU-08 | Meio ambiente | Perdas, eficiência de captação, conformidade do efluente, extravasamentos | `intelligence/sigsan-losses`, `sigsan-quality` (ETE) | P4.2, ED-48 | **Lacuna** no efluente (EF-01 a EF-06 do `09`, sem tarefa) |
| SU-09 | Segurança e emergência | Plano de contingência, fornecimento emergencial acima de 24 h (NR 11/ANA arts. 39 a 41), tempo de restabelecimento | OS, cadastro de interrupção | ED-35, ED-36; C17 do `21a` | **Parcial** |
| SU-10 | Ciclo de avaliação (cl. 8) | Política, objetivo e escopo, partes envolvidas, metodologia, critérios, recursos, saídas e recomendações, com aprovação e vínculo às séries de indicadores | Novo | C05 e C14 do `21a` (CQ-03, CQ-05) | **Lacuna** |
| SU-11 | Necessidades e expectativas (cl. 4 e 5) | Levantamento periódico: pesquisa de satisfação e reclamações classificadas | `sac.satisfaction_survey` (Lei 13.460 art. 23) | ED-35 | Coberta |
| SU-12 | Qualidade da informação (cl. 9.3, Anexo B) | Grau de confiança por variável e por indicador | Novo (SET-04) | ED-48 não cita | **Lacuna** |

### 3.2 Sistema de indicadores: o que o Ziip SAN calcula e o que falta

**Regra de desenho.** As ISO 24510, 24511 e 24512 tratam os indicadores como exemplos ilustrativos: o texto afirma que a avaliação do serviço não se reduz a um conjunto único e universal de indicadores, que não há valor-alvo ou mínimo imposto e que as partes podem escolher entre os exemplos ou criar outros. Logo, o módulo não deve fixar um conjunto "da ISO": deve oferecer o catálogo de fichas de indicador, com o mesmo formato (variáveis, fórmula, período, informação de contexto, grau de confiança) e ligar cada ficha ao critério da norma que o tenant escolher. **[INFERÊNCIA]**: como a NR 9/ANA e a NR 15/ANA já impõem fichas próprias, o catálogo de fábrica deve nascer das fichas regulatórias e acrescentar as de gestão.

**Convenção da coluna final.** **A** = calculável com o que o plano já prevê (algumas só depois de tarefas da trilha de editais). **B** = o plano entrega parte do dado-fonte. **C** = nenhum módulo planejado entrega o dado-fonte. "Código hoje" segue o `18g` e o `09`, não reverificado.

| # | Indicador de exemplo (formulação própria) | Critério ISO | Equivalente regulatório | Dado-fonte | Código hoje | Plano | Cl. |
|---|---|---|---|---|---|---|---|
| 1 | Cobertura de abastecimento: domicílios com rede disponível ÷ domicílios da área | 24510 tema acesso; 24512 5.6 | NR 8/ANA; NR 9/ANA art. 8º I | Domicílios IBGE; unidades com rede (GIS, cadastro) | Não calcula | Numerador: P2.23, P3.27. Denominador: sem tarefa | B |
| 2 | Cobertura de coleta de esgoto, mesma forma | 24511; 24510 acesso | Idem | Idem | Não | Idem | B |
| 3 | Atendimento: ligações ativas ÷ domicílios | 24510 acesso | NR 9/ANA art. 8º I | Cadastro | Cadastro com REST; sem relatório | P1.3; denominador igual ao 1 | B |
| 4 | Continuidade: horas de interrupção por ligação no período | 24510 nível de serviço; 24512 5.4 | NR 9/ANA I-04 (água) e I-05 (esgoto) | Interrupção com área e duração; ligações afetadas | Não | ED-35 (programada), ED-17 | B |
| 5 | Perdas por ligação (l/lig/dia), reais e aparentes | 24512 5.7 | NR 9/ANA I-01; NR 15/ANA art. 5º I a | Volume de entrada (macromedição), consumo faturado, consumo autorizado não faturado | `WaterBalanceCalculator` existe; entradas de `LossesService` são marcadores (`08` R-31) | P4.2 (caso de água não faturada), ED-48; perdas com entrada real em "2028, sob demanda" | B |
| 6 | Índice de vazamentos na infraestrutura (ILI) | 24512 5.7 | Complementar (NR 15/ANA art. 9º V) | Idem, mais extensão de rede, pressão, ligações | Calculadora tem ILI | P4.2 (caso ILI) | B |
| 7 | Hidrometração: ligações com hidrômetro ÷ ligações | 24510 consumo | NR 15/ANA art. 5º II; NR 9/ANA II-01 | Parque de medidores e instalações | Tabelas `meters`, `meter_installations` | ED-14, P2.5 | A |
| 8 | Macromedição: volume medido na entrada ÷ volume disponibilizado | 24512 5.7 | NR 9/ANA II-02; NR 15/ANA art. 5º I c | Macromedidores no GIS, telemetria | Tipo `MACRO_METER` existe; sem leitura de macro | ED-17, ED-48 | B |
| 9 | Coliformes totais no padrão (% de análises) | 24510 qualidade da água; 24512 5.2 | NR 9/ANA I-02; Portaria 888 Anexo 1 | Resultados de análise | Modelo existe; catálogo diverge (`09` QA-01) | ED-50 | A |
| 10 | Turbidez e cloro residual no padrão | Idem | Portaria 888 Anexo 2, art. 32 | Idem | Idem | ED-50 | A |
| 11 | Cumprimento do plano de amostragem (coletado ÷ planejado) | 24512 5.2 | Portaria 888 art. 44 | Plano anual e coletas | Sem plano (`09` QA-03) | ED-50 | A |
| 12 | Reclamações por mil ligações (água; esgoto) | 24510 reclamações | NR 9/ANA II-04, II-05; NR 11/ANA art. 54 | Protocolo com motivo classificado | Sem protocolo | P3.4, P3.22 (relatório do art. 54) | A |
| 13 | Solicitações respondidas no prazo; tempo médio de resposta | 24510 reclamações e pedidos | NR 11/ANA arts. 52, 72, 73; Lei 13.460 | Protocolo e motor de prazo | Não | P3.4 | A |
| 14 | Tempo médio de ligação nova; percentual no prazo | 24510 tempo para nova ligação | NR 11/ANA arts. 72 e 73 | OS de ligação | Não | P1.31, ED-13, ED-34 | A |
| 15 | Duração média de reparo de extravasamento de esgoto e de vazamento | 24510 reparos; 24511 | NR 9/ANA II-03 | OS corretiva com tempos e tipo de serviço | Não | ED-36, ED-34 | A |
| 16 | Contas retificadas ou canceladas ÷ emitidas | 24510 precisão da fatura | — | Versões de conta | Não | P2.20, P3.22 | A |
| 17 | Leituras efetivas ÷ programadas; contas por média | 24510 consumo | NR 11/ANA art. 35 | Leituras e anomalias | Regras de média existem (`AveragePolicy`) | P2.5, P2.7, P3.22 (leituras retidas) | A |
| 18 | Arrecadação e valores a receber | 24512 5.5 | — | Pagamentos e contas | Não | P3.1, P3.22 | A |
| 19 | Beneficiários da tarifa social ÷ elegíveis informados pela ERI | 24510 acesso; 24512 5.6 | Lei 14.898; NR 13/ANA arts. 25 a 45 | Cadastro social e lote da ERI | Não | P1.5; lote da ERI (`10` TS-02) | A |
| 20 | Extensão de rede por material, década e DN; rompimentos por 100 km | 24512 5.4 e 5.5 | NR 16/ANA (inventário); SINISA | GIS e OS | `V2_3` sem tela | P3.27, ED-56, ED-36 | A |
| 21 | Esgoto coletado que é tratado; DBO do efluente da ETE no padrão | 24511; 24510 meio ambiente | NR 9/ANA I-03 | Vazão da ETE; resultados de efluente | Tipos de ponto ETE existem; sem catálogo de efluente | EF-01 a EF-06 do `09`: sem tarefa; vazão de ETE sem módulo | C |
| 22 | Vazão captada ÷ outorgada | 24512 5.7 | Lei 9.433 art. 12 | Outorga e captação | Sem entidade | EF-08 do `09`: sem tarefa | C |
| 23 | Energia consumida por m³ e emissões | 24512 5.7 e 5.8 | SINISA | Energia | Sem módulo | Nenhuma | C |
| 24 | Pessoal por mil ligações; horas de treinamento | 24512 5.5 | SINISA | RH | Sem módulo antes de 2029 (`21a` §10.2) | Nenhuma | C |

**Contagem.** A: 13 indicadores (7, 9 a 20). B: 7 (1 a 6 e 8). C: 4 (21 a 24). As famílias B e C têm saída prática já prevista no plano: valor manual com justificativa, como na P4.2 ("arquivo oficial; valor manual com justificativa"), marcado com grau de confiança baixo (SET-04).

**Três observações.**

1. **Um cálculo, quatro usos.** Os indicadores 3, 5, 6, 7, 8, 9, 12 e 15 aparecem ao mesmo tempo na ISO, na NR 9/ANA, na NR 15/ANA e no SINISA. O módulo deve calcular uma vez (ficha versionada) e publicar nas quatro formas, em vez de manter quatro cálculos (conflito já apontado no `21a` §7, linha 7).
2. **Dado-fonte do denominador.** Três dos sete B (1, 2 e 3) dependem de uma base de domicílios que o plano não tem. É a lacuna isolada de maior alcance para indicadores de cobertura (NR 8/ANA, NR 9/ANA, ISO 24510 acesso, ISO 37120).
3. **Descompasso de marco.** A NR 15/ANA passa a ser verificada pela ANA a partir de 20/05/2028 (art. 14 p.ú.) e exige balanço hídrico com entradas reais (art. 4º e Anexo I). O plano deixa "perdas com entrada real" na lista "2028, sob demanda". A P4.2 e a ED-48 cobrem o envio assistido e o balanço por setor, não o plano de gestão de perdas (art. 9º) nem a publicação (art. 12).

### 3.3 ISO 24512:2024 (água) e ISO 24511:2024 (esgoto) — gestão do prestador

**O que são.** Diretrizes para a gestão de prestadores de água potável (24512) e de esgoto (24511) e para a avaliação dos serviços, sem metas nem limites. Segunda edição, 2024-02, ambas. Aplicam-se a prestadores públicos e privados e a sistemas de qualquer porte (a 24511 inclui expressamente desde latrinas e soluções individuais até redes e estações).

**Estrutura da 24512, confirmada no sumário público.** Cláusula 4: componentes do sistema (4.3 manancial, 4.4 captação e adução, 4.5 tratamento, 4.6 reservação e distribuição, 4.7 destino de resíduos). 5: objetivos do prestador (5.2 saúde pública, 5.3 necessidades dos usuários, 5.4 serviço em situação normal e de emergência, 5.5 sustentabilidade do prestador, 5.6 desenvolvimento sustentável da comunidade, 5.7 meio ambiente, 5.8 mudança do clima, 5.9 maior exigência dos usuários e do regulador, 5.10 tecnologias disponíveis). 7: gestão (7.4.3 atividades de apoio). 8: avaliação (mesma estrutura da 24510). 9: indicadores. Anexos B e C (ações possíveis para atingir os objetivos e para a gestão), D (metodologia de avaliação), E (critérios de avaliação e indicadores relacionados aos objetivos e aos componentes), F (grau de confiança). A edição de 2024 alterou a lista de indicadores-chave do Anexo E. **A 24511 tem estrutura equivalente (capítulos 7 e 8 de gestão e avaliação, 9 de indicadores, Anexos D, E, F)** [fonte secundária; sumário da 24511 não lido].

| ID | Objetivo (cl. 5) | O que o software precisa gerar | Fonte no produto | Plano | Situação |
|---|---|---|---|---|---|
| GA-01 | 5.2 Saúde pública | Conformidade com o padrão de potabilidade, comunicação de não conformidade, plano de amostragem cumprido | `sigsan-quality` | ED-50, ED-51 | Depende de ED-50 |
| GA-02 | 5.3 e 5.4 Usuário e emergência | Indicadores 4, 12, 13, 14 do §3.2; plano de contingência | Protocolo, OS, interrupção | P3.4, ED-35, ED-36 | Parcial |
| GA-03 | 5.5 Sustentabilidade do prestador | Arrecadação e inadimplência, custo, inventário e vida útil dos ativos, pessoal | Cobrança, contabilidade, GIS | P3.1, ED-32, ED-56 | **Parcial.** Pessoal sem módulo |
| GA-04 | 5.6 Comunidade | Cobertura, tarifa social, atendimento a áreas vulneráveis | Cadastro, GIS | P1.5, P3.27 | Parcial (denominador) |
| GA-05 | 5.7 Meio ambiente | Perdas, outorga e captação, energia, lodo de ETA e de ETE, efluente | Perdas, qualidade, MTR | P4.2, ED-48; trilha GRS em 2028 | **Lacuna** em outorga, energia, lodo, efluente |
| GA-06 | 5.8 Mudança do clima | Energia, emissões, risco climático dos ativos | Sem módulo | Nenhuma | **Lacuna**; entrada manual |
| GA-07 | 5.9 e 5.10 Exigência regulatória e tecnologia | Fichas NR 9/ANA e SINISA; registro de tecnologias adotadas | ED-48 | ED-48, P4.2 | Parcial |
| GA-08 | Cl. 4 Componentes do sistema | Cadastro de manancial, captação, ETA, reservatório, rede, resíduos (água) e de rede coletora, EEE, ETE, lodo (esgoto), com população atendida, tipo de manancial, tipo de tratamento | GIS e entidade de sistema (`09` QA-04) | P1.27, ED-53; entidade `WaterSystem` sem tarefa | **Lacuna** na entidade de sistema |
| GA-09 | Cl. 7 Gestão: atividades principais e de apoio | Vínculo processo, responsável, registro e indicador (C09 e C12 do `21a`) | Novo | CQ-01, CQ-03 do `21a` | Lacuna do módulo |
| GA-10 | Anexos B e C Ações possíveis | Plano de ação ligado ao objetivo e ao indicador (C15 do `21a`) | Novo | CQ-04 do `21a` | Lacuna do módulo |
| GE-01 | 24511 Cobertura de coleta e tratamento | Indicadores 2 e 21 do §3.2 | GIS, cadastro | Ver §3.2 | B e C |
| GE-02 | 24511 Conformidade do efluente | Limites do ato vigente, eficiência de remoção, automonitoramento, laudo | `sigsan-quality` (pontos ETE) | **Nenhuma** (EF-01 a EF-07) | **Lacuna** |
| GE-03 | 24511 Extravasamento, obstrução, alagamento | Ocorrência georreferenciada com duração e causa; extravasamento é o indicador NR 9/ANA II-03 | OS, GIS, drenagem | ED-36, ED-64 | Parcial |
| GE-04 | 24511 Lodo e subprodutos | Lote de biossólido, classe, destino, rastreabilidade; MTR e CDF | `sigsan-mtr`, trilha GRS | **Nenhuma** para biossólido (LO-01); GRS em 2028 | **Lacuna** |
| GE-05 | 24511 Solução individual e fossa | Cadastro do tipo de solução no imóvel | Cadastro | `18d` SG-154 "não atende", "2028, sob demanda" | **Lacuna** |

**Observação.** A ISO 24512 e a ISO 24511 pedem "grau de confiança" do sistema de indicadores (Anexo F e equivalente). A NR 9/ANA pede o mesmo, pela metodologia de auditoria do SINISA (arts. 15 II e 18). É o argumento mais forte para o SET-04.

### 3.4 ISO/IEC 17025:2017 — laboratório

**O que é e quem a exige.** Requisitos de competência, imparcialidade e operação coerente de laboratórios de ensaio e calibração. Edição de 2017, vigente. O reconhecimento é a **acreditação** pela Cgcre/Inmetro, que publica o escopo de cada laboratório (parâmetro, método, faixa); não existe "certificado ISO 17025". A Portaria GM/MS 888/2021 exige a norma nos arts. 20, 21 p.ú. e 22 §3 (`09` §2). A exigência é do **laboratório**, que pode ser do prestador, conveniado ou contratado. O software do tenant precisa servir a quem tem laboratório próprio e a quem apenas recebe laudos de laboratório terceiro.

**Cláusulas confirmadas por fonte secundária e conferidas com o título em inglês.** 6.2 pessoal; 6.4 equipamentos; 6.5 rastreabilidade metrológica; 7.1 análise crítica de pedidos, propostas e contratos; 7.2 seleção, verificação e validação de métodos; 7.3 amostragem; 7.4 manuseio de itens; 7.5 registros técnicos; 7.6 avaliação da incerteza de medição; 7.7 garantia da validade dos resultados; 7.8 relato de resultados, com 7.8.6 relato de declarações de conformidade (as regras de decisão ficam dentro dela); 7.10 trabalho não conforme; 7.11 controle de dados e gestão da informação; 8.4 controle de registros. Cláusulas 4 (requisitos gerais), 5 (estrutura), 6.3 (instalações e condições ambientais), 7.9 (reclamações) e o restante da 8 seguem a estrutura geral da edição de 2017 [A CONFIRMAR a numeração dos subitens 7.8.8, 7.11.2 e das opções A e B da cláusula 8].

**O que o sistema deve registrar** (item 4 do pedido):

| Cláusula | Em palavras próprias | O que o sistema registra | Estado hoje (`09` e código) | Plano |
|---|---|---|---|---|
| 6.2 | Pessoal competente e autorizado para cada atividade | Autorização do analista por método e parâmetro, com validade; treinamento; resultado só aceito de analista autorizado na data; papéis de coletor, analista e validador separados | Papéis genéricos; nada por método (`18g` SG-793) | ED-51 não cita. Lacuna (C07 do `21a`, CQ-06) |
| 6.3 | Condições ambientais que afetam o resultado | Registro de temperatura do ambiente e do armazenamento de amostras quando o método exige | Nenhum | Lacuna |
| 6.4 | Equipamento identificado, calibrado e mantido | Equipamento com ciclo de calibração e verificação intermediária, manutenção, desvio, estado; **bloqueio de resultado** com calibração vencida na data da análise | `LabInstrument` tem `calibration_due`, `calibration_frequency_days`, `certification`; `updateInstrumentCalibration` ignora `nextDue`; sem bloqueio (`09` LB-03) | ED-51 ("calibração com histórico e bloqueio") |
| 6.5 | Rastreabilidade metrológica | Certificado de calibração (laboratório calibrador e seu escopo, data, validade, incerteza da calibração, correção), padrão usado, material de referência certificado (lote, validade, certificado) | Nenhuma | ED-51 cobre histórico de calibração; padrões e materiais de referência não citados |
| 7.1 | Aceitar pedido só dentro da capacidade | Requisitante, parâmetro, método e matriz pedidos; checagem contra o escopo acreditado; alerta quando fora | Sem entidade de laboratório (`09` LB-01) | ED-50 e ED-51 |
| 7.2 | Método adequado, verificado ou validado | Método com referência normativa (Standard Methods, USEPA, ISO), versão, **dossiê de validação** (LD, LQ, precisão, recuperação, linearidade, robustez), responsável, data; LQ não maior que o VMP (Portaria 888 art. 22 §1) | `LabAnalysisMethod` tem LD, LQ, precisão, recuperação e incerteza; sem dossiê nem checagem LQ contra VMP (`09` LB-02) | ED-51 trata "validação por segunda pessoa", que é validação de **resultado**, não de método. Lacuna |
| 7.3 | Amostragem planejada e documentada | Plano de amostragem, ponto, coletor, condições da coleta, desvios | Campos existem; sem plano (`09` QA-03) | ED-50 |
| 7.4 | Amostra identificada e preservada | Etiqueta e leitura de código de barras, cadeia de custódia por evento, condição no recebimento, prazo de validade da amostra por parâmetro, descarte | Parcial (`09` LB-07) | ED-50 (cadeia de custódia, recepção por código de barras, ADR-0028 a) |
| 7.5 | Registro técnico que permita repetir | Dado bruto, cálculo, fator, instrumento, analista, método na versão, lote de padrões, condições; toda alteração em nova versão | Resultado é sobrescrito por upsert (`09` LB-06) | ED-50 ("resultados com versão") |
| 7.6 | Incerteza de medição avaliada e informada | Incerteza expandida, fator de abrangência k, nível de confiança e unidade, por método, parâmetro e faixa; documento do orçamento anexado | Campo `uncertainty DECIMAL(8,4)` sem unidade nem k (`09` LB-05) | ED-51 (incerteza) |
| 7.7 | Monitorar a validade dos resultados | Controle interno (branco, duplicata, adição, material de referência), cartas de controle, regras de Westgard, ensaios de proficiência e comparações interlaboratoriais, tendência; **bloqueio da liberação do lote** com controle reprovado | `LabQcSample`, `QcType`; Westgard só em comentário (`09` LB-04) | ED-51 (Westgard). Proficiência não citada. Lacuna parcial |
| 7.8 e 7.8.6 | Laudo com conteúdo mínimo e declaração de conformidade com regra de decisão | Laudo em PDF assinado pelo responsável técnico (Portaria 888 art. 23); regra de decisão configurável (aceitação simples ou banda de guarda); emenda com referência ao laudo original | `INCONCLUSIVE` existe e nunca é atribuído (`09` QA-02, LB-05); sem laudo (`09` LB-08) | ED-51 (incerteza e regra de decisão); laudo e assinatura (SET-07) |
| 7.9 | Reclamações sobre o laboratório | Ligação ao protocolo | Não | ED-35 (C08 do `21a`) |
| 7.10 | Trabalho não conforme | Ocorrência e ação (C15 do `21a`) | Não | CQ-04 do `21a`. Lacuna |
| 7.11 | Sistema de informação validado antes do uso, com acesso controlado, integridade e cálculo verificado [A CONFIRMAR 7.11.2] | **Pacote de validação do Ziip SAN por versão**: vetores de teste do motor de conformidade e dos cálculos, registro de mudança, evidência de backup e restauração, controle de acesso por papel, trilha imutável | `audit_log` somente-inserção; nada empacotado | **Não planejado.** É lacuna do fornecedor (Ziip), não do tenant |
| 8.4 | Registros controlados | Retenção pelo maior piso aplicável, proteção, recuperação | Pisos de G6; backup (P0.28) | Parcial |
| 8 (resto) | Sistema de gestão do laboratório: riscos, melhoria, ação corretiva, auditoria interna, análise crítica | Capacidades C04, C13, C14, C15 do `21a` | Não | CQ-03 a CQ-05 do `21a` |

**Validação do resultado (item 4 do pedido).** O fluxo mínimo, que o `09` LB-06 e o ADR-0028 a) já apontam: (1) resultado lançado por analista autorizado, com equipamento calibrado na data e método dentro do escopo; (2) revisão por **segunda pessoa distinta** do analista (trava de segregação); (3) controle de qualidade do lote aprovado; (4) liberação com laudo assinado pelo responsável técnico; (5) publicação (portal, SISAGUA); (6) reanálise, cancelamento e correção sempre como **nova versão** com motivo obrigatório, nunca sobrescrita. A conformidade com o padrão de potabilidade é calculada por motor puro (`ConformityEvaluator`, `09` QA-02), não digitada.

**Cadeia de rastreabilidade a guardar** (entidades candidatas, nomes provisórios): laboratório e escopo; autorização de analista; equipamento e calibração; padrão e material de referência; método e dossiê de validação; amostra e eventos de custódia; ensaio e resultado em versões; registro de controle de qualidade; ensaio de proficiência; laudo e emendas; trabalho não conforme. Para laboratório **terceirizado**: apenas escopo e validade da acreditação (número do registro na Cgcre) e o laudo recebido, com checagem automática de parâmetro e método contra o escopo (`09` pergunta 13).

**O que fica fora do sistema.** Política e compromisso de imparcialidade e confidencialidade (cláusula 4); estrutura legal e recursos (5 e 6.1); a competência real das pessoas e o julgamento do orçamento de incerteza (o sistema guarda o resultado e o documento); instalações físicas; procedimentos redigidos; a auditoria da Cgcre e a decisão de acreditar; qualidade de dado digitado à mão; auditoria de laboratório terceiro.

### 3.5 ISO 46001:2019 — gestão da eficiência no uso da água

**O que é.** Norma de requisitos, com orientação de uso, para sistema de gestão da eficiência no uso da água, na estrutura comum das normas de sistema de gestão. Aplica-se a organizações de qualquer porte que usam água e **tem foco no consumidor final**. Primeira edição, 2019-07. É certificável por organismo de certificação, mas a certificação não é obrigatória. Há revisão em desenvolvimento (ISO/AWI 46001), que substituirá a de 2019.

**Cláusulas confirmadas no sumário público.** 6 (planejamento): requisitos legais e outros, revisão do uso da água, indicadores de atividade e de eficiência (6.2.5 e 6.2.6, [A CONFIRMAR] subitens), objetivos e plano de ação. 8.2 projeto; 8.3 aquisição de serviços, produtos e equipamentos de água; 8.4 manutenção e inspeção. 9.1 monitoramento, medição, análise e avaliação; 9.2 auditoria interna; 9.3 análise crítica. 10.1 não conformidade e ação corretiva; 10.2 melhoria contínua.

**Aplicação a um prestador. [INFERÊNCIA]** Como a norma mira o uso final, o prestador a aplica ao **uso próprio** (lavagem de filtros e uso operacional em ETA e ETE, prédios administrativos, frota) e, como serviço, a programas de eficiência para grandes usuários. O balanço hídrico da distribuição (NR 15/ANA) não substitui a revisão do uso da água da 46001, mas o consumo operacional medido alimenta os dois.

| ID | Cláusula | O que o software gera | Fonte | Plano | Situação |
|---|---|---|---|---|---|
| W46-01 | 6, revisão do uso da água | Consumo por instalação (ETA, ETE, prédio), por uso significativo, com linha de base | Medidores de uso próprio, modelados como unidades de uso operacional (consumo autorizado não faturado do balanço) | P1.3, ED-14 (medidor existe; categoria de uso próprio não prevista) | **Lacuna** |
| W46-02 | 6.2.5 e 6.2.6, indicadores | Indicador de eficiência (m³ por unidade de atividade, por exemplo m³ de água de lavagem por m³ tratado) com linha de base e meta | Motor de indicadores (SET-04) | ED-48 | Depende do SET-04 |
| W46-03 | 6, objetivos e plano de ação | Objetivo, meta, ação, responsável, recurso, verificação | C05 e C15 do `21a` | CQ-03, CQ-04 | Lacuna do módulo |
| W46-04 | 8.2 a 8.4 | Critério de eficiência na aquisição e no projeto; manutenção e inspeção de equipamento de uso de água por OS | OS, almoxarifado, fornecedor | ED-36, ED-58, CQ-06 | Parcial |
| W46-05 | 9.1 | Leitura de medidores próprios e telemetria; detecção de consumo anômalo | Leituras, ED-17 | P2.7, ED-17 | Parcial |
| W46-06 | 9.2, 9.3, 10 | Auditoria interna, análise crítica, não conformidade, melhoria | C13 a C15 do `21a` | CQ-05, CQ-04 | Lacuna do módulo |

Prioridade: baixa a média. Só vale a entrega específica (W46-01) se um tenant pedir a ISO 46001; o restante é reaproveitamento do `21a`.

### 3.6 ISO 37120:2018 — indicadores de cidades

**Relevância: baixa para o prestador.** A norma é aplicada por cidades (o titular do serviço), não por prestadores. Segunda edição, 2018-07, confirmada na revisão periódica e agora em revisão (ISO/WD 37120). A cláusula 22 trata de esgoto e a 23, de água. A verificação por terceiro é feita pela WCCD, em programa privado e voluntário [fonte secundária]. Indicadores centrais de água e de esgoto informados por fonte secundária:

| Cláusula | Indicador (paráfrase) | Origem no Ziip SAN |
|---|---|---|
| 23 | População com serviço de água potável | Indicador 1 do §3.2 |
| 23 | População com acesso sustentável a fonte melhorada | Indicador 1 (conceito mais amplo que o do prestador) |
| 23 | Consumo doméstico total de água por habitante e por dia | Consumo faturado residencial ÷ população (população é dado externo) |
| 23 | Taxa de conformidade da qualidade da água potável | Indicadores 9 e 10 |
| 22 | População atendida por coleta de esgoto | Indicador 2 |
| 22 | Parcela do esgoto com tratamento centralizado | Indicador 21 |
| 22 | População com saneamento melhorado | Inclui solução individual (GE-05) |

**Recomendação (proposta).** Não criar entrada ativável. Oferecer um **perfil de exportação** de indicadores do prestador ao município titular, no formato de ficha do SET-04. **[INFERÊNCIA]**: o prestador entrega o dado e o município calcula e certifica.

### 3.7 PNQS — Prêmio Nacional da Qualidade em Saneamento (ABES)

**O que é.** Prêmio de gestão promovido pela ABES desde 1997, baseado no Modelo de Excelência da Gestão em Saneamento Ambiental (MEGSA, com a versão ESG). Não é norma nem certificação. Categorias confirmadas no ciclo de 2025 e 2026: AMEGSA (gestão completa, por níveis), SQF (selo de qualidade de fornecedores), SQR (selo de reguladores), IGS (inovação), PEOS (eficiência operacional) e PGA (gestão de ativos). A estrutura de níveis mudou entre ciclos (em 2017: níveis B, I, II, III e IV, de 125 a 1.000 pontos, com o III em 750 e o IV em 1.000; em 2024: níveis B, I, II e III, com o III em 1.000) [A CONFIRMAR no regulamento do ciclo]. O modelo avalia oito critérios: liderança, estratégias e planos, clientes, sociedade, informações e conhecimento, pessoas, processos e resultados. A categoria PEOS exige programas exitosos dos três últimos anos encerrados, nos temas energia, perdas de água, descarbonização, operações de esgoto e lodos e resíduos sólidos.

**O que o software pode dar.** O PNQS avalia a organização, não o software. O produto contribui com: (a) **séries históricas de resultado** de pelo menos três anos fechados (econômico-financeiros, clientes e mercado, processos, ambientais e sociais), pelo motor de indicadores (SET-04); (b) **evidência de processo** por relatórios e registros; (c) para PGA, a gestão de ativos da ED-56 (ver `21a` §6.4); (d) para PEOS, os indicadores 5, 21 e 23 do §3.2. Liderança, estratégias, pessoas e cultura ficam fora. **Recomendação (proposta):** perfil de evidências, sem entrada ativável; baixa prioridade.


---

## 4. Normas técnicas de referência

### 4.1 Medidores de água: Portaria Inmetro 155/2022, NBR 16043, NBR 15538

**Fatos confirmados** (fonte entre parênteses; a transcrição do DOU foi lida em duas cópias independentes e conferida com páginas do Inmetro, porque o texto em in.gov.br não abriu):

1. A Portaria Inmetro 155, de 30/03/2022, aprova o Regulamento Técnico Metrológico (RTM) consolidado para medidores de água potável fria e quente **usados para tarifação e comercialização de água**; vigora desde 01/06/2022 (art. 8º). Segundo fontes secundárias, revogou as Portarias 295/2018 e 246/2000.
2. **Classes de exatidão 1 e 2** (RTM item 2.3), com erro máximo admissível (EMA) por campo de medição: classe 1, ±1% no campo superior até 30 °C (±2% acima), ±3% no inferior; classe 2, ±2% (±3% acima de 30 °C) e ±5%. As vazões características são Q1 (mínima), Q2 (transição), Q3 (permanente) e Q4 (sobrecarga); o medidor se designa por Q3 e pela razão R = Q3/Q1. Em operação, o EMA admitido é o dobro (item 2.3.10). Razões fixas entre vazões (Q2/Q1 = 1,6; Q4 = 1,25 Q3): fonte secundária.
3. **Verificação inicial** (item 6.2): feita após a aprovação de modelo e antes da comercialização; o medidor apresentado deve ser conforme o modelo aprovado (6.2.4). **Verificação subsequente** (6.3): feita em medidor em uso; aprovado se o erro não passar do dobro do EMA e se não vazar no ensaio de pressão estática (6.3.2); medidor não conforme ao modelo aprovado não pode ser verificado e deve ser retirado de uso (6.3.3).
4. **Intervalo máximo de 7 anos** entre verificações subsequentes, **contados a partir do ano da instalação** (item 9.2). O Inmetro responde a mesma regra para a Portaria 295/2018 (item 7.5.2). O proprietário do instrumento solicita a verificação periódica; ela é feita pela Rede Brasileira de Metrologia Legal e Qualidade (RBMLQ-I). Existe ainda a verificação após reparo, obrigatória, e a voluntária.
5. **Transição para modelos aprovados pela Portaria 246/2000** (Anexo D): podem passar por verificação inicial **até 02/07/2026** (art. 2º; marco já ultrapassado) e por verificação subsequente **até 02/07/2028** (art. 3º). Medidor de água quente acima de 40 °C e de água fria acima de 15 m³/h instalado antes da 295/2018 pode continuar em uso até 02/07/2028 se atender ao EMA do novo regulamento (art. 5º).
6. **Aprovação de modelo** é ato do Inmetro por portaria da Diretoria de Metrologia Legal, por família de modelos (exemplo público: Portaria Inmetro/Dimel nº 338/2022, família WSH, classe de exatidão 2). O Inmetro publica as portarias de aprovação de modelo em página própria (PAM).
7. A Portaria 155 inclui anexo de requisitos de segurança de software e de hardware para medidores eletrônicos controlados por software.
8. **Portaria Inmetro 657/2025** (DOU 13/10/2025) substituiria a verificação inicial por declaração de conformidade do requerente da aprovação, a partir de 01/01/2026, para instrumentos regulamentados. A **Portaria 713/2025** (DOU 31/10/2025) **suspendeu seus efeitos**; permanecem a verificação inicial e a declaração de conformidade nas condições das Portarias 293/2021, 295/2021 e 78/2022. A inclusão expressa dos medidores de água na 657 é fonte secundária.
9. **NBR 16043**: família "Medidores para água potável fria e água quente", edição 02/2021: parte 1 requisitos técnicos e metrológicos; parte 2 métodos de ensaio; parte 3 requisitos não metrológicos; parte 4 requisitos de instalação (uma listagem de revendedor marca a parte 4 como cancelada; [A CONFIRMAR]). **NBR 15538**: ensaios de avaliação de eficiência (índice de desempenho metrológico, blindagem magnética), edição 2023 revisando a de 2014; é exigência **contratual** de recebimento de lote nos termos de referência das prestadoras, não exigência do Inmetro. **NBR 8194**: padronização do formato do número de série, conexões e dimensões, em complemento à 16043. **NBR 5426**: plano de amostragem por atributos, usado no recebimento de lote.

**Divergência documentada.** O edital SAMAE (`18c` SG-167) cita validade de 5 anos para medidor velocimétrico e 10 para volumétrico "por determinação do Inmetro". O texto do RTM (item 9.2) fixa 7 anos contados do ano da instalação, sem distinguir princípio de medição. A origem da diferença não foi apurada. **Proposta:** o intervalo é parâmetro por regime normativo, com padrão 7 anos, e o prestador pode fixar prazo mais curto por marca, modelo ou volume acumulado (classe c do ADR-0015: só reduz).

**Efeito das duas transições.** (a) Depois de 02/07/2026, medidor novo só entra em verificação inicial se tiver modelo aprovado pela 155; o estoque do tenant precisa distinguir o que foi verificado antes e depois. (b) Até 02/07/2028 convivem no parque medidores aprovados pela 246/2000 (classes A a D) e pela 155 (classes de exatidão 1 e 2); depois dessa data, só os da 155 podem ser verificados. O cadastro precisa registrar o **regime** de cada medidor para calcular o prazo de troca certo.

#### Cadastro de hidrômetros (item 5 do pedido)

Estado hoje: `meters` tem `brand`, `model`, `metrological_class` (VARCHAR 10), `nominal_diameter_mm`, `digits`, `manufacture_year`, `inmetro_approval` (VARCHAR 60, texto livre), `status` (AVAILABLE, INSTALLED, IN_MAINTENANCE, STOLEN, WRITTEN_OFF) e `last_revision_on`; `meter_installations` tem instalação, retirada, leituras, `seal_number` e `protection_type`. O `18d` (linha 104) já registra "faltam vazões, tecnologia e pressão". A herança do GSAN inclui intervalo de revisão por marca (`20a` CAD-51).

| ID | Campo ou registro | Base | Existe hoje | Plano |
|---|---|---|---|---|
| MT-01 | Número do medidor único por tenant, no formato de série da NBR 8194 (edição vigente [A CONFIRMAR]) | NBR 8194; Portaria 155 | `meter_number` único | Existe |
| MT-02 | Fabricante, marca, modelo e **família de modelos** da portaria de aprovação | Portaria 155 art. 1º; aprovação por família | `brand`, `model`; sem família | ED-14 ("modelo") |
| MT-03 | **Portaria de aprovação de modelo**: número, ano e órgão (por exemplo, "Portaria Inmetro/Dimel nº 338/2022"), família e modelo aprovados, **regime normativo** (246/2000, 295/2018 ou 155/2022), situação da portaria (vigente, suspensa, revogada); validação contra as portarias de aprovação publicadas pelo Inmetro (página PAM) | RTM; Anexo D | `inmetro_approval`, texto livre de 60 caracteres | **Lacuna**: estruturar e validar |
| MT-04 | **Classe de exatidão** (1 ou 2, regime 155) ou classe metrológica A a D (regime 246/2000), com domínio por regime | RTM item 2.3 | `metrological_class`, herdado do GSAN, sem domínio | **Lacuna**: migrar o domínio |
| MT-05 | **Vazões**: Q3 (m³/h), R (ou Q1), Q2, Q4; diâmetro nominal | RTM item 2 | Só `nominal_diameter_mm` | **Lacuna** |
| MT-06 | Classe de temperatura (água fria até 40 °C; quente até 90 °C), pressão máxima admissível, classe de perda de carga, sensibilidade a perturbações do escoamento, posição de instalação | RTM; NBR 16043-1 | Nenhum | **Lacuna** |
| MT-07 | Tecnologia (velocimétrico mecânico, volumétrico, ultrassônico, eletromagnético); para eletrônico, versão do software legalmente relevante e proteção do selo | RTM, anexo de software | Nenhum | **Lacuna** |
| MT-08 | Dígitos do mostrador, ano e lote de fabricação | RTM; ED-14 | `digits`, `manufacture_year`; lote não | ED-14 ("cadastro com lote") |
| MT-09 | **Comprovação da verificação inicial**: modalidade (verificação inicial ou declaração de conformidade, conforme as portarias vigentes), data, número do documento ou selo, lote | RTM item 6.2; Portarias 293/2021, 295/2021, 78/2022, 713/2025 | Nenhum | **Lacuna** |
| MT-10 | **Recebimento de lote**: fornecedor, nota fiscal, quantidade, plano de amostragem (NBR 5426), ensaios (erro de indicação em Q1, Q2 e Q3, IDM e blindagem magnética conforme NBR 15538), decisão de aceitação do lote | NBR 15538; NBR 5426; termos de referência das prestadoras | Nenhum; `inv.controlled_items` lista hidrômetro como item controlado | **Lacuna** (ED-58 cobre estoque, não ensaio) |
| MT-11 | Estoque, local e movimentação (origem, destino, motivo) | ED-14; ED-58 | `status` sem local nem movimentação (`18d` linhas 101 e 103) | ED-14, ED-58 |
| MT-12 | Instalação: unidade, data, leitura inicial, lacre, proteção, local; retirada com destino (estoque, manutenção, sucata) | NR 11/ANA art. 22 | `meter_installations` | ED-14 |
| MT-13 | **Ano de instalação** como base do prazo (derivado de `installed_on`) | RTM item 9.2 | Derivável | Existe |
| MT-14 | **Verificação subsequente**: data, organismo, número do certificado, erro por vazão, ensaio de estanqueidade, conclusão, próxima verificação | RTM item 6.3 | `last_revision_on` (só data) | **Lacuna**; P3.28 cobre aferição **a pedido**, não a periódica |
| MT-15 | Verificação após reparo e verificação voluntária | Inmetro | Nenhum | **Lacuna** |
| MT-16 | **Prazo de troca calculado**: o menor entre o limite do regime (7 anos contados do ano da instalação; para modelos da 246/2000, também o marco de 02/07/2028) e o prazo próprio do prestador (por marca, modelo ou volume acumulado); alerta com antecedência configurável; OS de troca em lote | RTM itens 9.2 e Anexo D | `last_revision_on`; sem rotina (`18d` linha 110) | ED-14 ("alerta de validade gera OS em lote"); depende de MT-03 a MT-15 |
| MT-17 | Medidor reprovado ou não conforme ao modelo: retirada obrigatória e destino | RTM item 6.3.3 | Situações IN_MAINTENANCE, WRITTEN_OFF | ED-14 |
| MT-18 | Marca de **regime transitório** (acima de 15 m³/h ou água quente, anteriores à 295/2018) com data-limite | Portaria 155 art. 5º | Nenhum | **Lacuna** (só relevante para grandes consumidores) |
| MT-19 | Aferição a pedido do usuário: OS, laudo, EMA aplicável, custo só se normal, revisão de contas | NR 11/ANA art. 31; ARIS, AGIR, ARESC | Nenhum | P3.28 |
| MT-20 | Lacre e proteção contra fraude | NR 11/ANA | `seal_number`, `protection_type` | ED-14, ED-24 |
| MT-21 | Telemetria: tipo de comunicação, módulo, protocolo | ED-17 | `MeterReading.Source.TELEMETRY` | ED-17 |
| MT-22 | Documentos anexos: portaria, certificado, laudo, foto | — | `documents` sem serviço | P1.20, ED-14 |

**Relatórios do parque** (proposta): medidores por regime, portaria, família e ano de instalação; vencidos e a vencer por mês; verificações realizadas e reprovadas por marca, modelo e lote (indicador de qualidade do fornecedor); medidores sem portaria de aprovação vigente; medidores em regime transitório; ligações sem hidrômetro (alimenta o indicador 7 do §3.2).

**Cobertura do plano.** A ED-14 (trilha de editais) cobre cadastro com lote, modelo, classe e lacre, instalação, retirada, reinstalação e alerta de validade com OS em lote. A P3.28 cobre a aferição a pedido. Ficam sem tarefa: a estrutura de aprovação de modelo e de regime (MT-03, MT-04, MT-18), as vazões e classes de produto (MT-05 a MT-07), a verificação inicial, subsequente e após reparo (MT-09, MT-14, MT-15), o recebimento de lote com ensaio (MT-10) e o cálculo do prazo por regime (MT-16). Ver L-12.

### 4.2 Rede, cadastro e topografia

O `19c` já levantou estas normas (edição, objeto e impacto, a partir de revendedores, não do catálogo da ABNT). Este quadro só liga cada uma ao que o software guarda e ao plano.

| ID | Norma | O que o software guarda ou valida | Plano | Situação |
|---|---|---|---|---|
| NT-01 | NBR 12586 e 12587 (cadastro de água e de esgoto) | Catálogo de feições com atributos mínimos, situação (projetada, em obra, ativa, desativada), identificador estável, data de implantação, fonte (projeto, as-built, OS, importação); relatório de completude por classe. O conteúdo exato das normas exige a compra [A CONFIRMAR] | P1.26, P1.27 (cada item com norma e edição ou marcado "valor de referência a validar"), P1.28 (compra), P2.22, P3.27 | Coberta na estrutura; checklist por norma só depois da compra |
| NT-02 | NBR 12218 (rede de distribuição) | Atributos hidráulicos (DN, material, rugosidade, cota, zona de pressão), faixas de pressão como parâmetro com norma e edição, exportação EPANET | P2.21, ED-63, P1.28 | Coberta; valores da norma a validar |
| NT-03 | NBR 9649 e 9648 (rede coletora) | Trecho entre singularidades (PV, TIL, terminal de limpeza, caixa de passagem), cotas de fundo, declividade, profundidade, DN mínimo, tensão trativa como regra bloqueante com valor de referência a validar (`GIS-CADASTRO-TECNICO.md`) | P2.21, ED-63 (SWMM), ED-64, P1.28 | Coberta; valores a validar |
| NT-04 | NBR 13133 (levantamento topográfico) | Método, equipamento, data, exatidão horizontal e vertical, datum vertical, responsável técnico (ART ou RRT) | P1.26 (`capture_method`, `h_accuracy_m`, `v_accuracy_m`, `vertical_datum`, `gis_responsible_party`), P3.27 (temático e relatório de precisão), P3.26 | Coberta |
| NT-05 | NBR 14166 (Rede de Referência Cadastral Municipal) | Campo opcional "RRCM de referência" no levantamento, quando o município tiver rede | Nenhum | **Lacuna pequena** |
| NT-06 | Edição das normas de material (por exemplo NBR 7675:2026, 10160:2026) | Domínio de material e de tampão com vigência por edição | P1.27 | Coberta |

As NBR 13133 e 14166 não constam nominalmente da lista de compra da P1.28 (`GIS-CADASTRO-TECNICO` §4 lista a 9648 e outras, mas não essas duas) [A CONFIRMAR no §4 do documento]. Ver L-16.

---

## 5. Normas regulatórias obrigatórias

Artigos da NR 9/ANA e da NR 15/ANA foram lidos em gov.br nesta rodada. Os demais artigos vêm dos levantamentos `08`, `09` e `20f`, que leram o texto das normas; este documento não os releu. Situação do plano conforme o v2.16. **Atualização em relação ao `20f` (de 14/10/2026):** o plano passou a cobrir, nas versões 2.14 e 2.15, itens que o `20f` marcava como ausentes ou parciais: LEG-05 e LEG-06 (bloco mensal de qualidade na conta e declaração anual de quitação) pela **P2.24**; LEG-09 e LEG-10 (aferição e vazamento oculto) pela **P3.28**; LEG-22 e LEG-24 (compensação por corte indevido e aviso pós-corte) pela **P3.4**; LEG-23 (interrupção programada) pela **ED-35**; LEG-26 (relatório de reclamações) pela **P3.22**.

### 5.1 NR 11/ANA (Resolução ANA 230/2024) — condições gerais

Mapa completo no `20f` §2.13. Resumo do que gera e cobertura:

| Artigos | O software gera | Plano | Situação |
|---|---|---|---|
| 21 e 22 | Unidade usuária com código único, cadastro, economias por categoria, medidor com aferição, histórico de 120 meses | P1.3, P2.10, P3.20, `ret.billing_history_months` | Coberta |
| 31 | Monitoramento do consumo por hidrômetro certificado; aferição | ED-14, P3.28 | **Parcial** (cadastro do medidor certificado: MT-03 a MT-16) |
| 33 e 35 | Fatura no modelo da ERI; intervalo de leitura; faturamento por média com teto de ciclos | P2.5, `bill.*`, `meter.*` | Coberta |
| 37 e 38 | Pagamento em duplicidade e revisão de fatura | P3.1, P3.29, P2.20 | Coberta |
| 39 a 41 | Interrupção programada e de emergência, fornecimento emergencial acima de 24 h | ED-35 | Coberta (sem mapa; a área calculada pelo GIS entra na ED-54) |
| 43 a 47 | Aviso de corte com prova, religação, urgência, religação indevida | P3.4, P3.5 | Coberta |
| 52 a 55 | Protocolo, ouvidoria, regulamento da prestação, relatório de reclamações, histórico em portal | P3.4, ED-35, P3.20, P3.22 | **Parcial** (carta de serviços, art. 53, sem tarefa; L-10) |
| 72 e 73 | Catálogo de serviços com prazos aprovados pela ERI | ED-34, P3.4 | Coberta |
| 74 | Comprovação de adoção pela ERI a partir de 20/05/2027 | `reg.ana_nr_adoption` | Coberta |

### 5.2 NR 13/ANA (Resolução ANA 271/2025) — estrutura tarifária e tarifa social

| Artigos | O software gera | Plano | Situação |
|---|---|---|---|
| 6, 8 e 9 | Categorias mínimas; parcela fixa por tarifa básica; consumo mínimo só em contrato anterior | P2.5, `tariff.fixed_part_model`, `reg.contract_pre_nr13` | Coberta |
| 17 a 20 | Disponibilidade de rede, aviso de 90 dias, esgoto só onde há rede | P2.23 | **Parcial** (evento de aviso auditável: L-10) |
| 25 a 45 (art. 26 §1º) | Elegibilidade por CadÚnico ou BPC, classificação, 50% até 15 m³, uma economia por família, concessão sem condição de adimplência, relatório à ERI | P1.5, `social.*` | Coberta (prazo normativo 11/12/2026; sem decreto federal localizado no `20f`) |
| 30, 38 e 40 | Dados do CadÚnico sob sigilo, uso restrito | NIS e renda cifrados (classe a), `10` TS-02 | Coberta |
| 36 e 58 | Corte do beneficiário só se a ERI previr; mensagens obrigatórias na conta | P3.4; motor de mensagens sem tarefa | **Parcial** (L-10) |

### 5.3 NR 15/ANA (Resolução ANA 275/2025) — perdas

Lido em gov.br nesta rodada. A norma foi publicada no DOU de 19/12/2025; a ANA passa a verificar a adoção pelas ERIs a partir de 20/05/2028, escalonada por porte e índice de perdas (art. 14 p.ú.).

| Artigos | O software gera | Plano | Situação |
|---|---|---|---|
| 4 e Anexo I | **Balanço hídrico padronizado, base SINISA**, anual, com declaração à ERI | `WaterBalanceCalculator` (calcula; entradas são marcadores); P4.2 (caso de água não faturada), ED-48 (balanço por setor) | **Parcial** |
| 5 | Indicadores: perdas por ligação reais e aparentes em l/lig/dia (NR 9/ANA I-01), intermitência (I-04), macromedição (II-02) e hidrometração (código SINISA IAG1003) | ED-14, ED-17, ED-48 | **Parcial** (indicadores 4, 5, 7 e 8 do §3.2) |
| 6 a 9 | **Plano de Gestão de Redução e Controle de Perdas** por município: diagnóstico, acompanhamento de perdas reais e aparentes, evolução da hidrometração e macromedição, ações com cronograma físico-financeiro e justificativa técnico-econômica, indicadores complementares, estrutura e custo; revisão quando os resultados não convergem (art. 9 §1º) | Nenhuma (`08` R-32) | **Lacuna** |
| 11 | Tecnologias no plano (telemetria, sensores de pressão, vazão ou ruído, georreferenciamento, medição inteligente) | ED-17, GIS | Parcial |
| 12 | Publicação dos resultados nos sítios do prestador e da ERI | P3.22 (relatório), portal | **Parcial** |
| 13 e 14 | Comprovação pela ERI (Res. ANA 134/2022); lista de municípios com plano | Dado do tenant | Coberta (`reg.ana_nr_adoption`) |

### 5.4 NR 9/ANA (Resolução ANA 211/2024) — indicadores operacionais (complementar)

Lida em gov.br nesta rodada. Vigência desde 01/10/2024. Nível I (obrigatório, nos contratos, art. 7º): indicadores de cobertura e atendimento da NR 8 (art. 8º I), I-01 perdas por ligação, I-02 coliformes totais, I-03 DBO na saída do tratamento, I-04 e I-05 intermitência de água e de esgoto. Nível II (art. 10): II-01 micromedição, II-02 macromedição, II-03 duração média do reparo de extravasamento, II-04 e II-05 reclamações. Exigências ao software: informações primárias do ano civil, data-base de dezembro (art. 17); separação por município, urbano e rural e componente, com rateio em sistema integrado (art. 16); diagnóstico do **nível de confiança** pela metodologia de auditoria do SINISA (arts. 15 e 18); relatório anual de avaliação publicado (art. 23); verificação anual pelo regulador em janela de 5 anos, com metas cumpridas em 3 (art. 14). Plano: ED-48 (indicadores parametrizáveis, metas, semáforo), P4.2. Lacunas: rateio por município e separação urbano e rural (L-02), nível de confiança (L-02).

### 5.5 Portaria GM/MS 888/2021, Portaria 2.472/2021 e SISAGUA

Exigências, estado do código e requisitos já mapeados nos IDs do `09` §5 (QA-01 a QA-15, SG-01 a SG-10, LB-01 a LB-08). Nenhum foi alterado: a consulta de 21/10/2026 não encontrou nova portaria que revise o Anexo XX, apesar de o prazo de 5 anos do art. 51 ter vencido. Cobertura no plano:

| Grupo | IDs do `09` | Plano | Situação |
|---|---|---|---|
| Catálogo versionado e motor de conformidade (Anexos 1, 2, 9 a 11; arts. 22, 27, 28, 38 a 41) | QA-01, QA-02 | ED-50 ("catálogo revisado por responsável técnico"); SET-03 | Coberta por tarefa; é a base de todo o resto |
| Plano de amostragem anual, mínimos, pontos (art. 44; Anexos 13 a 15) | QA-03 a QA-06, QA-11 | ED-50; entidade `WaterSystem` (QA-04) sem tarefa nomeada | **Parcial** (L-06) |
| Desinfecção, turbidez, cianotoxinas, E. coli em captação, radiológico, fluoretação | QA-07 a QA-10, QA-12, QA-13 | ED-50 prevê motor de conformidade e plano, sem listar essas regras | **Parcial** [INFERÊNCIA: entram no motor de conformidade; o aceite da ED-50 não as nomeia] |
| Comunicação de não conformidade e plano de ação | SG-03, SG-04 | ED-50 ("comunicação de não conformidade"); P3.5 | Coberta |
| SISAGUA assistido | SG-01, SG-02 | ED-50 ("arquivo SISAGUA assistido"), ADR-0028 a 3 | Coberta; **layout vigente [A CONFIRMAR]** |
| Informação ao consumidor (conta e relatório anual) | SG-05, SG-06 | P2.24 (bloco mensal por planilha), ED-50 | **Parcial**: relatório anual sem tarefa (L-09) |
| **Plano de Segurança da Água** (arts. 49 e 50) | SG-07, SG-08 | Nenhuma | **Lacuna** (L-08) |
| Carro-pipa (arts. 14 XIX-XX, 16) | QA-14 | Nenhuma | Lacuna, só se o tenant opera carro-pipa |
| Notificação prévia de manobra (art. 14 XXI; Decreto 5.440 Anexo art. 15) | QA-15 | ED-54 ("manobras com aviso e OS em lote") | **Parcial** |
| Produtos químicos, materiais em contato, ART (arts. 14 VII-IX, 15, 23) | SG-10 | SET-05 | Lacuna (L-01) |
| Laboratório | LB-01 a LB-08 | ED-51 | **Parcial** (§3.4, L-11) |

### 5.6 CONAMA 357/2005, 430/2011, 498/2020 e 503/2021 — esgoto

Requisitos já mapeados no `09` §5.4 e §5.5 (EF-01 a EF-09, LO-01, RU-01, ID-01). **Nenhum tem tarefa no plano.** O que o software gera, em resumo:

| Norma e artigos | O software gera | IDs do `09` | Plano |
|---|---|---|---|
| CONAMA 357, arts. 9 e 10 (enquadramento) e 14 a 17 (padrões das classes de água doce 1 a 4) | Classe do corpo receptor ligada ao ponto de lançamento e de captação; padrões da classe como catálogo versionado | EF-01, SG-09 | Nenhuma |
| CONAMA 430, arts. 16 e 21 (padrões de lançamento; efluente de ETE) | Catálogo de efluente separado do de potabilidade, com vigência; conformidade por parâmetro; **eficiência de remoção de DBO** pareada (entrada e saída) | EF-01, EF-06 | Nenhuma |
| CONAMA 430, arts. 24 a 26 | Programa de automonitoramento por licença; coleta por profissional habilitado; laboratório acreditado ou aceito pelo órgão; controle de qualidade analítico | EF-04 | Nenhuma |
| CONAMA 430, art. 28 | Declaração de Carga Poluidora anual | EF-05 | Nenhuma |
| **Nova resolução do CONAMA** (aprovada em 01 e 02/09/2026; revoga a 430 e altera a 357; limites de nitrogênio amoniacal e fósforo total por porte) | **Não codificar limite fixo.** Versão nova com data de início, quando publicada. Em 16/10/2026 não havia publicação no DOU nem texto final localizado | EF-02 | Nenhuma |
| Resolução conjunta CNRH/CONAMA de reúso (aprovada em 09/2026) | `ReuseModality` como dado, sem padrão; ler quando publicada | RU-01 | Nenhuma |
| CONAMA 498, art. 6 p.ú., 12, 35, 36 | Lote de biossólido, classe (A ou B; 1 ou 2), projeto por área, rastreabilidade anual ao órgão, destino alternativo via MTR e CDF | LO-01 | Nenhuma (GRS em 2028) |
| Outorga (Lei 9.433, art. 12 III) e licença ambiental (Lei 15.190/2025) | `WaterRightGrant` e `EnvironmentalLicense` com validade e condicionantes; **regime por UF, nunca presumir dispensa** | EF-08, EF-09 | Nenhuma |
| CONAMA 503/2021 | Nenhum. Fertirrigação com efluente de indústrias; não alcança esgoto sanitário | — | Não se aplica |

### 5.7 Lei 11.445/2007 e Leis 14.015/2020, 14.898/2024, 13.460/2017 e 12.007/2009

Mapeadas nos IDs LEG-xx do `20f`. Quadro resumido, com a situação atualizada:

| Norma e artigos | O software gera | IDs do `20f` | Plano | Situação |
|---|---|---|---|---|
| Lei 11.445, art. 39 p.ú. (modelo da conta) | Modelo de fatura da ERI com bloco do fornecedor travado | LEG-02 | `bill.invoice_template`, P2.5 | Coberta |
| Lei 11.445, art. 40 (§1º comunicação; §2º aviso de 30 dias; §3º usuários protegidos) | Interrupção programada, aviso de corte com prova, classes protegidas | LEG-18, LEG-21, LEG-23 | P3.4, P3.5, ED-35 | Coberta |
| Lei 11.445, art. 45 (disponibilidade e ligação obrigatória) | Aviso de disponibilidade, lista de não conectados | LEG-16 | P2.23 | Parcial |
| Lei 11.445, arts. 50 e 53 (metas, SINISA) | Exportação SINISA assistida; metas de perdas | LEG-46, LEG-49 | P4.2, ED-48 | Parcial |
| Lei 14.015/2020 (altera Lei 13.460 art. 5º XVI e 6º p.ú.; Lei 8.987 art. 6º §4º) | Vedação de corte em sexta, sábado, domingo, feriado e véspera; aviso de 30 dias | LEG-18, LEG-19 | P3.4, P3.5; ADR-0015 (classe a) | Coberta |
| Lei 14.898/2024, arts. 2º a 8º e 12 | Tarifa social, sem perda por inadimplência, aviso de 3 meses antes da perda | LEG-14, LEG-15, LEG-04 | P1.5, `social.*` | Coberta; mensagem: L-10 |
| Lei 13.460/2017, arts. 5º, 6º, 7º (carta de serviços), 10 a 16 (ouvidoria), 23 (satisfação) | Protocolo, ouvidoria em 30 + 30 dias, pesquisa, carta de serviços | LEG-25, LEG-27 | P3.4, ED-35 | Parcial (carta de serviços: L-10) |
| Lei 12.007/2009, arts. 1º a 4º | Declaração anual de quitação na fatura de maio ou no mês seguinte à quitação | LEG-06 | **P2.24** | Coberta |

### 5.8 Decreto 5.440/2005 — informação ao consumidor sobre a qualidade da água

Art. 3º (conta mensal com resumo de parâmetros básicos; relatório anual a cada ligação até 15/03) e Anexo, arts. 5, 7, 8 (conteúdo e marca "fora dos padrões de potabilidade"), 15 (aviso prévio de manobra) e 16 e 17 (reclamação e resposta em 30 dias; registro público). Mapeado em SG-05 e SG-06 do `09` e LEG-05 do `20f`. **Plano:** P2.24 entrega o bloco **mensal** da conta, alimentado por planilha do laboratório até a ED-50 (`bill.water_quality_source`). O **relatório anual** a cada ligação não consta de nenhuma tarefa (L-09). A lista de "parâmetros básicos" do Decreto não foi lida (`09` §5.2).

### 5.9 LGPD (Lei 13.709/2018)

Mapeada no `10`. Obrigações com efeito no software e cobertura:

| Obrigação | Plano | Situação |
|---|---|---|
| Minimização, cifra de documento, índice cego, nenhum dado pessoal em URL, log ou arquivo de campo (arts. 6º, 46) | ADR-0008, P0.11, P0.12, P1.15; LEG-45 | Coberta |
| Retenção e eliminação (arts. 15, 16); pisos legais | `ret.*` (G6), P3.16 | Coberta |
| Incidente em 3 dias úteis e registro por 5 anos (art. 48; Res. CD/ANPD 15/2024) | P3.10 (prazos e modelo de RIPD), `ret.incident_record_years`; entidade de incidente sem tarefa nomeada (LEG-44) | **Parcial** |
| Direitos do titular (art. 18): correção, acesso, portabilidade, eliminação, em 15 dias (art. 19 II) | Correção por OS (ADR-0039); fluxo completo não localizado por busca textual | **Parcial**; [A CONFIRMAR] (L-15) |
| Registro das operações de tratamento (art. 37) e RIPD (art. 38) | P3.10 só prevê modelo de RIPD; `10` PRV-01 | **Lacuna** (L-15; `21a` §10.2 item 5) |
| Registro de acesso a aplicações por 6 meses (Marco Civil art. 15) | `log.access_retention_days` | Coberta |
| Coordenada de imóvel ligada a titular é dado pessoal; publicação só agregada (5 imóveis no mínimo) | `gis.public_aggregation_min_units` (classe c), P3.27 | Coberta |

---

## 6. Cobertura pelo plano e lacunas

### 6.1 O que já está coberto, por grupo

| Grupo | Situação | Tarefas do plano que sustentam |
|---|---|---|
| ISO 24510, 24511, 24512 (dados de serviço) | Dado-fonte majoritariamente previsto; faltam o ciclo de avaliação, o grau de confiança e alguns denominadores | P1.3, P1.5, P2.5, P2.7, P2.20, P2.23, P3.1, P3.2, P3.3, P3.4, P3.5, P3.20, P3.22, P3.27, ED-13, ED-14, ED-17, ED-34, ED-35, ED-36, ED-48, ED-50, ED-56 |
| ISO/IEC 17025 | A ED-51 cobre cinco itens (escopo, calibração com histórico e bloqueio, Westgard, incerteza e regra de decisão, validação por segunda pessoa). A comparação cláusula a cláusula do §3.4 mostra oito pontos que a ED-51 não nomeia | ED-50, ED-51 |
| ISO 46001, ISO 37120, PNQS | Sem tarefa; reaproveitam o motor de indicadores | ED-48 (base) |
| Medidores (Portaria 155, NBR 16043, 15538) | Cadastro, lote, modelo, classe, lacre, instalação, retirada e alerta de validade previstos; aferição a pedido prevista. Estrutura de aprovação de modelo, regime, verificações e recebimento de lote não | ED-14, P3.28, ED-58, ED-17, P1.20 |
| NBR de rede, cadastro e topografia | Bem coberta, dependente da compra das normas | P1.26, P1.27, P1.28, P2.21, P2.22, P3.26, P3.27, ED-53, ED-63, ED-64 |
| NR 11/ANA, NR 13/ANA, Leis 14.015, 14.898, 12.007 | Coberta, com poucas exceções (§5.1, §5.2) | P1.3, P1.5, P2.5, P2.10, P2.20, P2.24, P3.1, P3.4, P3.5, P3.20, P3.22, P3.28, P3.29, ED-34, ED-35 |
| Portaria 888 e SISAGUA | Prevista como bloco único (ED-50) com SISAGUA assistido; PSA fora | ED-50, ED-51, P2.24 |
| NR 15/ANA e NR 9/ANA | Parcial: indicadores e envio assistido; plano de gestão de perdas e balanço com entrada real não | P4.2, ED-48, ED-14, ED-17 |
| CONAMA, outorga, licença, biossólido, reúso | Nenhuma tarefa | — |
| LGPD | Estrutura de proteção coberta; direitos do titular e registro de operações parciais | ADR-0008, P0.11, P0.12, P1.15, P3.10, G6, ADR-0039 |

### 6.2 Lacunas

As lacunas abaixo são **propostas de tratamento**, sem aceite do dono e sem ordem de calendário; a coluna "Tarefa proposta" usa o prefixo CQS e dependências, nunca datas.

| ID | Lacuna | Origem normativa | Tarefa proposta (provisória) | Depende de |
|---|---|---|---|---|
| L-01 | Catálogo de normas do setor com duas classes (voluntária ativável; obrigatória por aplicabilidade), vigência futura, documentos com validade, relatório periódico gerado da evidência, assinatura de laudo (SET-01 a SET-03, SET-05 a SET-07) | Todas | CQS-01: catálogo de fábrica do CQ-07 do `21a` para o setor | CQ-01, CQ-07 do `21a` |
| L-02 | Motor de fichas de indicador com variáveis, contexto, **grau de confiança**, rateio por município e separação urbano e rural (SET-04) | ISO 24510 cl. 9 e Anexo B; ISO 24512 cl. 9 e Anexo F; NR 9/ANA arts. 15, 16, 18 | CQS-02 | ED-48, CQ-03 do `21a` |
| L-03 | Base de domicílios (IBGE por setor censitário) e indicadores de cobertura e atendimento | NR 8/ANA; NR 9/ANA art. 8º I; ISO 24510 acesso; ISO 37120 | CQS-03 | P1.3, P2.23, CQS-02 |
| L-04 | Continuidade: interrupção **não programada**, horas de interrupção por ligação, intermitência de esgoto | NR 9/ANA I-04, I-05; NR 11/ANA arts. 39 a 41; ISO 24512 5.4 | CQS-03 | ED-35, ED-17 |
| L-05 | Perdas conforme NR 15/ANA: balanço padronizado base SINISA com entrada real, declaração anual, Plano de Gestão de Redução e Controle de Perdas, publicação | NR 15/ANA arts. 4 a 9 e 12; marco de verificação 20/05/2028 | CQS-04 | P4.2, ED-48, ED-17, `intelligence/sigsan-losses` |
| L-06 | Entidade de sistema de abastecimento e de esgoto e seus componentes, com população atendida, tipo de manancial, tratamento, desinfetante | Portaria 888 Anexos 2, 13 a 15; ISO 24512 cl. 4; ISO 24511 | CQS-05 | P1.27, ED-53 |
| L-07 | Esgoto e água bruta: catálogo de efluente com vigência, automonitoramento, eficiência de DBO, Declaração de Carga Poluidora, outorga, licença, biossólido, reúso | CONAMA 357, 430, 498; Lei 9.433; Lei 15.190; nova resolução de 09/2026 | CQS-06 | ED-50, SET-02, SET-03 |
| L-08 | Plano de Segurança da Água, avaliação de risco do sistema, carro-pipa | Portaria 888 arts. 14 X, 16, 49, 50 | CQS-07 | ED-50, CQS-05 |
| L-09 | Relatório anual de qualidade da água a cada ligação | Decreto 5.440 art. 3º III | CQS-08 | P2.24, ED-50 |
| L-10 | Carta de Serviços; motor de mensagens legais com prova de emissão; evento de aviso de disponibilidade | Lei 13.460 art. 7º; NR 11/ANA art. 53; NR 13/ANA arts. 18 e 58; Lei 14.898 | CQS-08 | P3.5, P3.20, ED-41 |
| L-11 | ISO 17025 além da ED-51: autorização de analista por método, condições ambientais, padrões e materiais de referência, dossiê de validação de método, ensaios de proficiência, laudo com emenda, trabalho não conforme, **pacote de validação do sistema por versão** | ISO/IEC 17025 cl. 6.2, 6.3, 6.5, 7.2, 7.7, 7.8, 7.10, 7.11; Portaria 888 arts. 20 a 23 | CQS-09, como ampliação da ED-51 | ED-50, ED-51, CQ-04 do `21a` |
| L-12 | Cadastro de hidrômetros conforme o RTM: aprovação de modelo e regime, classe de exatidão 1 e 2, vazões, verificação inicial, subsequente e após reparo, recebimento de lote, prazo de troca por regime | Portaria Inmetro 155/2022 itens 2.3, 6.2, 6.3, 9.2, Anexo D; NBR 16043; NBR 15538 | CQS-10, como ampliação da ED-14 | ED-14, ED-58, P3.28, P1.20 |
| L-13 | Energia, emissões, pessoal: sem módulo; entrada manual com justificativa e grau de confiança baixo | ISO 24512 5.5, 5.7, 5.8; SINISA | CQS-11 | CQS-02 |
| L-14 | Uso próprio de água: unidades de uso operacional, revisão do uso, linha de base, plano de ação | ISO 46001 cl. 6, 8, 9 | CQS-12 | P1.3, ED-14, CQ-03 do `21a` |
| L-15 | LGPD: fluxo completo de direitos do titular e registro das operações de tratamento | LGPD arts. 18, 19, 37, 38 | Absorvida pela CQ-13 do `21a` | `compliance/sigsan-lgpd`, P3.10 |
| L-16 | NBR: vínculo à Rede de Referência Cadastral Municipal; checklist de completude do cadastro por norma | NBR 14166; NBR 12586 e 12587 | CQS-13 | P1.28, P3.27 |
| L-17 | Perfis de exportação para ISO 37120 e PNQS | ISO 37120; PNQS | CQS-14 | CQS-02 |
| L-18 | Aquisição dos textos das normas do setor, análoga à P1.28: ISO 24510, 24511, 24512, 17025, 46001, 37120; NBR 16043, 15538, 8194, 5426, 13133, 14166; texto do DOU da Portaria 155 | — | Ampliação da CQ-15 do `21a` e da P1.28 | decisão do dono |

---

## 7. Fatos confirmados, inferências e pendências de confirmação

### 7.1 Fatos confirmados

**Normas voluntárias**

- F-01. ISO 24510:2024 (2ª edição, 2024-01, estágio 60.60), ISO 24511:2024 (2024-02) e ISO 24512:2024 (2024-02) estão publicadas e substituem as edições de 2007, que constam como retiradas (iso.org; catálogo do ISO/TC 224).
- F-02. O texto das três declara que o objetivo não é estabelecer sistemas de especificação para certificação direta de conformidade, mas orientar a melhoria contínua e a avaliação do serviço; o uso é voluntário; os indicadores são exemplos, sem valor-alvo nem mínimo (texto da ISO 24511 em inglês e da ISO 24510 em francês, pré-visualizações públicas).
- F-03. Estrutura da ISO 24510:2024: cláusulas 4 a 9, com 8.2 a 8.8 (avaliação) e 9.2 a 9.4 (indicadores, variáveis, contexto, qualidade da informação), Anexo A (exemplos de indicadores) e Anexo B (grau de confiança). Estrutura da ISO 24512:2024: cláusulas 4, 5 (5.2 a 5.10), 7, 8, 9 e Anexos A a F. Mesmas fontes.
- F-04. ISO/IEC 17025:2017 é a edição vigente (3ª), confirmada pela ISO; a norma serve de critério de acreditação de laboratórios; a Cgcre/Inmetro publica o escopo de acreditação de cada laboratório (RBLE). Títulos das cláusulas 6.2, 6.4, 6.5, 7.1 a 7.8, 7.8.6, 7.10, 7.11 e 8.4 conforme o texto em inglês (pré-visualização).
- F-05. ISO 46001:2019 é a primeira norma de requisitos do ISO/TC 224, pode ser certificada e a certificação não é obrigatória; foca o consumidor final; sumário público traz 6.2.6, 8.2 a 8.4, 9.1, 9.2, 10.1 e 10.2. Revisão em desenvolvimento (ISO/AWI 46001).
- F-06. ISO 37120:2018 é a 2ª edição, confirmada, com revisão em curso (ISO/WD 37120).
- F-07. PNQS: instituído em 1997 pela ABES, baseado no MEGSA (versão ESG); categorias AMEGSA, SQF, SQR, IGS, PEOS e PGA; PEOS considera os três últimos anos encerrados e os temas energia, perdas, descarbonização, esgoto e lodos e resíduos (regulamentos e critérios do PNQS 2022 e 2025; inscrições das categorias IGS, PEOS e PGA do ciclo 2026 abertas em agosto de 2026).

**Medidores**

- F-08. Portaria Inmetro 155/2022: itens e artigos listados no §4.1, fatos 1 a 7 (duas transcrições do DOU e páginas do Inmetro).
- F-09. Portaria Inmetro 657/2025 teve os efeitos suspensos pela Portaria 713/2025 (DOU de 31/10/2025, art. 1º e 2º); permanecem a verificação inicial e a declaração de conformidade nas condições das Portarias 293/2021, 295/2021 e 78/2022.
- F-10. NBR 16043 tem partes 1 a 4, edição 02/2021; a NBR 15538 tem edição 2023 que revisa a de 2014; ambas aparecem nos termos de referência de prestadoras junto da Portaria 155.
- F-11. No código, `meters.metrological_class` é VARCHAR(10), `inmetro_approval` é VARCHAR(60) e `last_revision_on` é DATE (arquivo `V5_1`, lido nesta rodada).

**Regulação ANA e outras**

- F-12. NR 9/ANA (Resolução 211/2024, vigor em 01/10/2024): indicadores I-01 a I-05 e II-01 a II-05, arts. 7 a 10, 14 a 18, 21 e 23 (gov.br).
- F-13. NR 15/ANA (Resolução 275/2025, DOU de 19/12/2025): arts. 4 a 14; verificação a partir de 20/05/2028 (gov.br).
- F-14. CONAMA 357/2005: padrões das classes de água doce nos arts. 14 a 17 (texto oficial citado por busca). CONAMA 430/2011 continua vigente.
- F-15. O CONAMA aprovou em 01 e 02/09/2026 a resolução de efluentes (revoga a 430 e altera a 357), a conjunta de reúso e a de aquicultura. Em 16/10/2026 não havia publicação no DOU nem texto final localizado (veículos especializados e MMA).
- F-16. Em 21/10/2026 não localizei nova portaria do Ministério da Saúde que revise a Portaria 888/2021.

**Plano e projeto**

- F-17. O plano v2.16 não tem tarefa para o módulo "Certificações de qualidade". Os IDs citados neste documento existem no plano.
- F-18. O plano não tem tarefa para efluente, outorga, licença ambiental, biossólido, reúso, Plano de Segurança da Água, relatório anual de qualidade ao consumidor, Carta de Serviços nem para plano de gestão de perdas (busca textual no plano e nos documentos de arquitetura).

### 7.2 Inferências

- I-01. O catálogo de fábrica de indicadores deve nascer das fichas regulatórias (NR 9/ANA, NR 15/ANA, SINISA) e acrescentar as de gestão, porque os indicadores coincidem (§3.2).
- I-02. A ISO 46001 aplica-se ao prestador pelo uso próprio de água e por programas para grandes usuários, não ao balanço de distribuição (§3.5).
- I-03. Na ISO 37120 o prestador fornece o dado e o município calcula e verifica (§3.6).
- I-04. A regra de contagem do prazo de 7 anos "a partir do ano da instalação" (ano civil ou data exata) precisa ser confirmada e parametrizada; o item 9.2 usa a expressão "ano de sua instalação".
- I-05. O prestador deve exigir e registrar a comprovação de verificação inicial no recebimento do medidor, porque a verificação inicial é pré-condição de comercialização.
- I-06. As regras específicas de desinfecção, cianotoxinas, radiológico e fluoretação entram no motor de conformidade da ED-50, embora o aceite da ED-50 não as nomeie.
- I-07. O pacote de validação do sistema (ISO/IEC 17025, 7.11) é entrega do fornecedor; o laboratório do tenant precisa dele para validar o sistema antes do uso.
- I-08. A classificação A, B e C dos indicadores do §3.2 é julgamento do autor sobre a cobertura do plano.
- I-09. O descompasso entre a verificação da NR 15/ANA em 2028 e a posição de "perdas com entrada real" em "2028, sob demanda" é risco de planejamento, não erro do plano.
- I-10. O formato do número de série da NBR 8194 deve ser conferido na edição vigente antes de virar validação.

### 7.3 A confirmar

- V-01. Textos das ISO 24510, 24511, 24512, 17025, 46001 e 37120: subitens, Anexo A (24510), Anexo E (24511 e 24512) e lista oficial de indicadores. Sumário da ISO 24511.
- V-02. Texto do DOU da Portaria 155/2022: itens 2.3.10, 6.2.4, 6.3.2, 6.3.3, 9.2, Anexo D e letra do anexo de segurança de software; revogação das Portarias 246/2000 e 295/2018 (hoje por fontes secundárias).
- V-03. Se a Portaria 657/2025 incluía os medidores de água (fonte secundária) e se há ato posterior que retome a mudança.
- V-04. Situação da NBR 16043-4 (cancelada em uma listagem de revendedor) e edição vigente da NBR 8194.
- V-05. Numeração 6.3, 7.8.8, 7.11.2 e opções A e B da cláusula 8 da ISO/IEC 17025.
- V-06. Layout vigente do SISAGUA; lista de "parâmetros básicos" do Decreto 5.440 (`09` §5.2).
- V-07. Regulamento do PNQS do ciclo em uso.
- V-08. Número, data e texto final da resolução de efluentes e da conjunta de reúso do CONAMA, e se a Declaração de Carga Poluidora anual continua.
- V-09. Se a lista de compra da P1.28 inclui as NBR 13133 e 14166 (`GIS-CADASTRO-TECNICO` §4 não as cita).
- V-10. Se existe, em outro item do plano, tarefa para direitos do titular (LGPD art. 18) e registro de operações (art. 37), que não localizei por busca textual.
- V-11. Estado do código além dos arquivos conferidos nesta rodada (`18g` e `09`, de 12/10/2026).

---

## 8. Perguntas ao dono do produto

Cada uma com recomendação, sem presunção.

1. **Escopo da ativação.** `cert.<norma>.enabled` vale para o tenant inteiro ou também por laboratório, localidade ou instalação? A ISO/IEC 17025 e a ISO 46001 podem valer para um laboratório ou um sítio só. Recomendação: o tenant liga a norma e declara o escopo (`cert.scope_statement` do `21a`), com escopo por laboratório ou instalação dentro dele.
2. **ISO 37120 e PNQS.** Entram como perfil de exportação, sem entrada ativável, ou ficam fora do catálogo? Recomendação: perfil de exportação (baixa prioridade).
3. **Base de domicílios.** A carga do IBGE por setor censitário é global do Ziip, versionada, ou por tenant? Recomendação: global, para que todos os tenants calculem cobertura do mesmo denominador.
4. **Prazo de verificação de medidores.** Aceitar o padrão de 7 anos como parâmetro por regime, com prazo próprio mais curto permitido (nunca mais longo), e confirmar a contagem com o órgão metrológico do piloto?
5. **Compra dos textos (L-18).** Incluir as normas do setor na P1.28 e na CQ-15 do `21a`?
6. **Pacote de validação do sistema (ISO/IEC 17025, 7.11).** O Ziip assume como entrega por versão para tenants com laboratório?
7. **ED-51.** Tratar os itens de L-11 como ampliação da ED-51 ou como tarefa separada (CQS-09)? Recomendação: ampliação, porque o aceite da ED-51 já nomeia o laboratório.
8. **Perdas (NR 15/ANA).** Manter "perdas com entrada real" em "2028, sob demanda" ou levar o balanço padronizado e o plano de gestão para a trilha de editais, na dependência da ED-48? Recomendação: levar, por dependência (não por data), antes de o primeiro tenant em uma ERI que adote a NR 15 precisar dela.
9. **Esgoto.** Abrir a trilha de efluente (L-07) como extensão da ED-50 ou como pacote próprio? Recomendação: pacote próprio, porque a norma de referência (CONAMA) está em troca de versão.

---

## 9. Fontes consultadas

Consultas feitas em 21/10/2026, salvo indicação. Documentos do projeto: ver cabeçalho.

**ISO e normas voluntárias**

- ISO 24510:2024 (iso.org/standard/81484.html), ISO 24511:2024 (iso.org/standard/82489.html), ISO 24512:2024 (iso.org/standard/82490.html); catálogo do ISO/TC 224; pré-visualizações públicas de sumário e introdução (standards.iteh.ai).
- ISO/IEC 17025:2017 (iso.org/standard/66912.html); escopo de acreditação de laboratório na RBLE (inmetro.gov.br/laboratorios/rble).
- ISO 46001:2019 (iso.org/standard/68286.html), ISO/AWI 46001 (iso.org/standard/92424.html), nota do ISO/TC 224 sobre a ISO 46001.
- ISO 37120:2018 (iso.org/standard/68498.html); WCCD (dataforcities.org).
- PNQS: pnqs.com.br (Regulamento 2022; Critérios PEOS 2025); ABES-SP (inscrições 2026).

**Metrologia e normas técnicas**

- Inmetro: página de verificação; legislação metrológica em vigor; resposta sobre substituição de hidrômetro após 7 anos; Portaria Inmetro 155/2022 (transcrições do DOU e Target Normas); Portaria Inmetro/Dimel 338/2022 e 64/2023 (exemplos de aprovação de modelo); Portaria 657/2025 e Portaria 713/2025 (DOU de 13/10/2025 e 31/10/2025).
- Normas.com.br (NBR 16043 e NBR 15538); termos de referência de prestadoras (SAMAE, SEMAE Mogi das Cruzes, Sanasa) para uso da Portaria 155, NBR 15538 e NBR 5426.

**Regulação**

- ANA, Resolução 211/2024 (NR 9) e Resolução 275/2025 (NR 15), em gov.br/ana.
- CONAMA 357/2005 (conama.mma.gov.br); notícias do MMA e de veículos especializados sobre as aprovações de 01 e 02/09/2026.
- Ministério da Saúde: Portaria 888/2021 (consulta de atualização).
- Demais normas: levantamentos `08`, `09`, `10`, `19c` e `20f`, que citam as fontes oficiais.
