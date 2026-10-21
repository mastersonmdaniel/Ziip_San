# 21a — Normas ISO de sistema de gestão: requisitos de software do Ziip SAN

Levantamento para o módulo proposto "Certificações de qualidade". Escopo: ISO 9001, ISO 14001, ISO 45001, ISO 55001, ISO/IEC 27001 e ISO/IEC 27701. Pergunta que o documento responde: o que o software do prestador de saneamento (cliente do Ziip SAN) precisa oferecer para que o cliente consiga demonstrar conformidade quando ativar cada norma.

Estado: levantamento de plano e documentação. Nenhum código foi escrito ou alterado. Nenhum outro arquivo do repositório foi editado. O plano de execução não contém tarefa para este módulo (ver §9 e §10).

---

## 0. Leitura rápida

1. **Um software não certifica ninguém.** A certificação é concedida por organismo de certificação acreditado, depois de auditoria no cliente. O Ziip SAN pode produzir, organizar e preservar evidência e apontar lacunas de cláusula. Não pode afirmar que o cliente está conforme, nem que o produto "é ISO". O módulo deve exibir o mapa de cláusulas como autoavaliação do cliente, nunca como parecer (§8).
2. **Três das seis normas pedidas já têm edição mais nova que a citada no pedido** (§2): a ISO 9001:2015 foi substituída pela ISO 9001:2026; a ISO 14001:2015, pela ISO 14001:2026; a ISO/IEC 27701:2019, pela ISO/IEC 27701:2025, que deixou de ser extensão da ISO/IEC 27001. A ISO 45001:2018 está em revisão. A ISO 55001:2024 e a ISO/IEC 27001:2022 (com a Emenda 1:2024) estão vigentes. O módulo deve versionar a edição da norma por tenant.
3. **Há 17 capacidades de software comuns a duas ou mais normas** (§4 e §5). A maior parte nasce da estrutura harmonizada das normas de sistema de gestão (cláusulas 4 a 10): informação documentada, riscos, objetivos, competência, mudança, requisitos legais, auditoria interna, análise crítica, não conformidade, incidente e fornecedor.
4. **O que é específico de cada norma** (§6) é pequeno em número de registros e grande em conteúdo: aspectos e impactos ambientais (14001), perigos e investigação de acidente (45001), inventário e ciclo de vida do ativo (55001), declaração de aplicabilidade e controles (27001), registro de operações de tratamento e atendimento a titular (27701).
5. **Reaproveitamento.** O produto já planeja grande parte da matéria-prima: OS (P1.31, ED-36), parâmetros com vigência e aceite (P0.19), trilha de auditoria (P1.7), atendimento (ED-35), almoxarifado (ED-58), qualidade da água (ED-50, ED-51), perdas, GIS e ativos (ED-56). **Novo de verdade:** auditoria interna, análise crítica, riscos e oportunidades, registro de requisitos legais por tenant, ocorrência/ação corretiva, competência e treinamento, avaliação de fornecedor, e todo o conteúdo específico de 14001, 45001, 55001, 27001 e 27701.
6. **Estado do código.** Os módulos `operations/sigsan-assets`, `operations/sigsan-maintenance`, `operations/sigsan-field`, `commercial/sigsan-portal` e os três módulos `grs/*` exceto `sigsan-mtr` não têm código-fonte hoje. O registro de módulos (`ModuleRegistry`) descreve `maintenance` com "ISO 55001, RCM" apenas como texto. O módulo `compliance/sigsan-lgpd` tem entidades de consentimento, pedido de titular, RIPD e cronograma de retenção, sem API REST e sem teste, segundo o levantamento 10.
7. **Conflitos reais com a lei são poucos** (§7): retenção de registros do sistema de gestão contra a LGPD; acesso do auditor externo contra sigilo e dado pessoal; dado de saúde na investigação de acidente; transparência pública contra confidencialidade documental. Em todos, a regra adotada é a decisão do dono: a lei vence.

---

## 1. Escopo, método e convenções

### 1.1 Método

- Leitura do plano `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (§1, §2.2, §2.3 e índices de tarefas P0 a P4, GL, LC e ED), de `ARQUITETURA-ALVO.md`, `PARAMETROS-DO-TENANT.md`, `PLATAFORMA-E-VERTICAIS.md`, `CENTRAL-ZIIP.md`, `REQUISITOS-NAO-FUNCIONAIS.md`, de ADRs citados e dos levantamentos 08, 09 e 10.
- Conferência da existência de código nos módulos (apenas presença de arquivos e nomes de tabela), sem avaliar qualidade.
- Pesquisa em fontes públicas (páginas da ISO e de seus comitês técnicos, organismos de normalização e de certificação, órgãos do governo brasileiro). **Os textos das normas ISO são pagos e protegidos; não foram lidos na íntegra.** As cláusulas de primeiro nível (4 a 10) são comuns à estrutura harmonizada e foram conferidas por resumos públicos. Subcláusulas e identificadores de controle estão marcados **[A CONFIRMAR]** quando dependem do texto adquirido.
- Nenhum trecho de norma é reproduzido. As cláusulas são citadas por número e descritas em palavras próprias.

### 1.2 Convenções

- **[INFERÊNCIA]**: dedução do autor, sem confirmação documental. Reunidas no §10.2.
- **[A CONFIRMAR]**: fato que não foi possível verificar nas fontes acessíveis.
- **Proposta**: decisão de desenho sugerida neste documento, ainda sem aceite do dono.
- Chaves de parâmetro `cert.*`, entidades `cert_*` e códigos de tarefa `CQ-*` são **provisórios**: não existem no plano nem no código.
- **Ambiguidade de "NR".** Neste projeto, "NR 11", "NR 13", "NR 15" e "NR 16" designam as Normas de Referência da ANA (condições gerais, tarifa, perdas, contabilidade regulatória). Existem Normas Regulamentadoras do Ministério do Trabalho com os mesmos números e outros temas (por exemplo, NR-11 sobre movimentação de materiais, NR-15 sobre insalubridade, NR-16 sobre periculosidade). Este documento escreve **NR 11/ANA** quando se trata da ANA e **NR-1/MTE**, **NR-7/MTE** quando se trata de segurança e saúde no trabalho.
- "SGI" designa o sistema de gestão do cliente (qualidade, ambiente, SST, ativos, segurança da informação e privacidade, em qualquer combinação).
- "Tenant" é o prestador de saneamento cliente do Ziip SAN. O Ziip atua como operador de dados pessoais do tenant (levantamento 10).

### 1.3 Limite do documento

O documento trata do software. Não avalia o que o cliente precisa fazer para obter a certificação (política, cultura, treinamento efetivo, auditoria de terceira parte). Esse limite é parte da resposta e aparece no §8.

---

## 2. Edições vigentes das normas e efeito no produto

| Norma pedida | Edição vigente apurada | Situação | Efeito no módulo |
|---|---|---|---|
| ISO 9001:2015 | **ISO 9001:2026** (6ª edição), publicada em setembro de 2026; incorpora a Emenda 1:2024 | Substitui a edição de 2015. Mudanças declaradas: termos de sistemas de gestão na cláusula 3; cultura da qualidade e conduta ética; riscos e oportunidades tratados em separado (6.1.1 a 6.1.3); gestão de mudança reforçada (6.3); melhoria contínua consolidada em 10.1 e não conformidade em 10.2; Anexo A explicativo ampliado; Anexo B removido | Suportar as duas edições. Mapa de cláusulas por edição. Registros de risco e oportunidade devem permitir tratamento distinto |
| ISO 14001:2015 | **ISO 14001:2026** (4ª edição), publicada em abril de 2026 | Substitui a edição de 2015. Condições ambientais ampliadas em 4.1 e 4.2 (clima, biodiversidade, poluição, recursos); nova 6.1.4 (estrutura de decisão sobre riscos e oportunidades); nova 6.3 (mudança); "provisão externa" no lugar de terceirização; programa de auditoria documentado; ênfase em desempenho mensurável | Suportar as duas edições. Campo para condições ambientais do contexto; indicadores ambientais com meta |
| ISO 45001:2018 | **ISO 45001:2018 com a Emenda 1:2024** (clima). Revisão em fase de projeto de norma internacional (DIS) | Publicação da nova edição prevista para 2027; a de 2018 segue certificável até lá. Temas apontados no projeto: liderança, cultura de segurança, riscos psicossociais, formas de trabalho, retorno ao trabalho | Usar 2018 como base. Prever campo de risco psicossocial desde o início [INFERÊNCIA] |
| ISO 55001:2024 | **ISO 55001:2024** (2ª edição), publicada em julho de 2024, revisão técnica da edição de 2014 | Vigente. Novidades: 4.5 decisão e valor; 6.1.2 risco e 6.1.3 oportunidade separados; 6.2.1 plano estratégico de gestão de ativos (SAMP); 6.3 planejamento de mudanças; 7.6 dados e informação; 8.1 com gestão do ciclo de vida; 10.3 ação preditiva no lugar da ação preventiva da edição anterior | Alvo direto. Estrutura do SAMP, critérios de decisão e ação preditiva viram requisitos de produto |
| ISO/IEC 27001:2022 | **ISO/IEC 27001:2022 com a Emenda 1:2024** (clima em 4.1 e 4.2) | Vigente. Anexo A com 93 controles em 4 temas (organizacionais 37, pessoas 8, físicos 14, tecnológicos 34). Declaração de aplicabilidade exigida em 6.1.3 d). Transição da edição de 2013 encerrada | Alvo direto |
| ISO/IEC 27701:2019 | **ISO/IEC 27701:2025** (2ª edição), publicada em outubro de 2025 | Substitui a de 2019. Passou a norma autônoma de sistema de gestão de privacidade, com cláusulas 4 a 10; **não exige mais certificação 27001 como pré-requisito**; controles para controlador e operador; riscos de privacidade integrados ao sistema de gestão | Adotar a edição de 2025 como alvo. A de 2019 só para cliente em transição. Os identificadores de controle de 2019 citados neste documento valem como referência histórica |

Conclusão de produto (proposta): o parâmetro `cert.<norma>.edition` guarda a edição em uso pelo tenant, e o catálogo de cláusulas do produto é versionado por edição. Nenhuma data de transição de certificados é tratada aqui; ela é definida pelos organismos de acreditação e pertence ao cliente.

**Numeração usada nas tabelas.** Para 9001, 14001 e 45001 usa-se a numeração da edição que o pedido citou (2015 e 2018), com a diferença da edição de 2026 indicada quando conhecida. Para 55001, a de 2024. Para 27701, só a estrutura harmonizada (4 a 10) é citada, por falta do texto da edição de 2025.

---

## 3. Desenho proposto do módulo "Certificações de qualidade"

Todo este capítulo é **proposta**, ancorada em decisões já aceitas do projeto.

### 3.1 Posição na arquitetura

- O motor do módulo (informação documentada, riscos, objetivos, ocorrências, ações, auditoria, análise crítica, mapa de cláusulas, papéis) é transversal e não menciona saneamento. Pertence ao **núcleo da plataforma** (ADR-0037, `PLATAFORMA-E-VERTICAIS.md` §2). A fronteira é testada: o núcleo não depende do pacote.
- O conteúdo de saneamento (aspectos ambientais típicos de ETA, ETE e rede; perigos típicos de vala e espaço confinado; classes de ativo; catálogo de obrigações setoriais) pertence ao **pacote de vertical** `sanitation`, como catálogo de fábrica extensível pelo tenant, no mesmo padrão do catálogo do GIS (ADR-0034).
- O módulo é habilitável pelo manifesto da vertical (`modules` do `vertical.json`) e por permissões `recurso:ação` (ADR-0040), por exemplo `cert:read`, `cert:write`, `cert:approve`, `cert:audit`.

### 3.2 Ativação por norma

| Ponto | Regra proposta |
|---|---|
| Capacidades comuns (§5) | Sempre disponíveis, sem norma ligada, como registros livres. É o que a decisão do dono chama de "o sistema já atende o que é comum" |
| Ligar uma norma | Parâmetro `cert.<norma>.enabled` com escopo declarado (`cert.scope_statement`), representante da direção e edição. Passa a valer: campos obrigatórios da norma nos registros comuns (por exemplo, causa raiz e verificação de eficácia na ocorrência), ciclo de revisão do documento, telas e registros específicos (§6), mapa de cláusulas da norma e checklist de prontidão |
| Várias normas ligadas | Um registro satisfaz várias cláusulas. O vínculo é muitos-para-muitos entre evidência e cláusula (`cert_clause_map`, `cert_evidence_link`). Uma ocorrência, por exemplo, pode ser marcada como não conformidade (9001), incidente de SST (45001) e incidente de segurança (27001) ao mesmo tempo, sem duplicar o registro |
| Desligar uma norma | **Nunca apaga registro** (travado, classe a). O mapa da norma é congelado, os campos obrigatórios deixam de ser exigidos, os registros continuam consultáveis pelo prazo de retenção. Aviso prévio lista o que deixará de ser exigido |
| Edição da norma | `cert.<norma>.edition`, enumeração do catálogo do produto. Troca de edição gera comparação de cláusulas e lista de evidências a revalidar |
| Aceite | Mesmo mecanismo de `parameter_versions` e `parameter_acceptances` (P0.19; `PARAMETROS-DO-TENANT.md` §6): quem propõe não aprova, aceite com hash |

### 3.3 Parâmetros provisórios

Seguem o padrão do ADR-0038: bifurcação vira parâmetro, padrão de fábrica mais protetivo, lei que proíbe fica travada.

| Chave | Descrição | Padrão de fábrica | Classe | Observação |
|---|---|---|---|---|
| `cert.<norma>.enabled` | norma ligada ou desligada | desligada | d | Exige escopo e responsável |
| `cert.<norma>.edition` | edição em uso | edição vigente do catálogo | d | |
| `cert.scope_statement` | texto do escopo do SGI | sem | d | Versionado |
| `cert.deactivation_keeps_records` | desligar não apaga registro | verdadeiro | a | Travado |
| `cert.record_retention_years` | retenção de registro do SGI sem lei específica | maior prazo de G6 que o registro comprova | c | Só amplia; dado pessoal obedece a G6 e ao RNF-40 |
| `cert.evidence_pack_personal_data` | pacote de evidência contém dado pessoal | falso | a | Travado: pacote só com dado agregado, mascarado ou pseudonimizado |
| `cert.external_auditor_access` | papel temporário de auditor externo | desligado | b | Se ligado: somente leitura, prazo definido, documento mascarado, sem revelação, registro de cada leitura |
| `cert.internal_target_vs_legal` | meta interna comparada ao prazo legal | meta interna nunca mais frouxa que o prazo legal | c | Só reduz, como `sac.ombudsman_days` |
| `cert.risk_scale` | escala e matriz de risco | matriz 5x5 do produto | d | Cada SGI define o próprio critério; o produto exige que haja critério |
| `cert.audit_cycle` | intervalo do programa de auditoria interna | sem | d | As normas exigem intervalos planejados e não fixam número |

### 3.4 Entidades candidatas (nomes provisórios)

`cert_standard_activation`, `cert_clause` e `cert_clause_map`, `cert_evidence_link`, `cert_document` (sobre a infraestrutura de documentos e aceite já prevista), `cert_risk`, `cert_objective` e `cert_indicator_link`, `cert_change_request`, `cert_training_record`, `cert_supplier_evaluation`, `cert_legal_requirement` e `cert_compliance_check`, `cert_audit_program`, `cert_audit` e `cert_finding`, `cert_occurrence` (não conformidade, incidente e reclamação, com subtipos), `cert_action`, `cert_management_review`, `cert_emergency_plan` e `cert_drill`.

---

## 4. Quadro 1 — capacidades comuns e as cláusulas que as justificam

Leitura: cada linha é uma capacidade de software. As células citam o número da cláusula e o tema em palavras próprias. "—" significa que a norma não tem cláusula própria para o tema. Numeração: ver §2. Cláusulas marcadas com asterisco dependem de conferência no texto adquirido.

| Cap. | Capacidade | ISO 9001 | ISO 14001 | ISO 45001 | ISO 55001:2024 | ISO/IEC 27001:2022 | ISO/IEC 27701:2025 |
|---|---|---|---|---|---|---|---|
| C01 | Informação documentada: criação, versão, aprovação, distribuição, retenção, descarte, integridade | 7.5.1 a 7.5.3: documento e registro necessários, atualização, controle | 7.5.1 a 7.5.3 | 7.5.1 a 7.5.3 | 7.5 e 7.6* (documentação e informação de ativos) | 7.5.1 a 7.5.3; Anexo A: proteção de registros (A.5.33) | 7.5* |
| C02 | Contexto, partes interessadas, requisitos delas e escopo | 4.1 a 4.3; 2024/2026: clima em 4.1 e 4.2 | 4.1 a 4.3 | 4.1 a 4.3 | 4.1 a 4.3 e 4.5 (decisão e valor) | 4.1 a 4.3 | 4.1 a 4.3* (inclui papel de controlador ou operador) |
| C03 | Liderança, política, papéis e responsabilidades, ciência da política | 5.1 a 5.3 | 5.1 a 5.3 | 5.1 a 5.3 | 5.1 a 5.3 | 5.1 a 5.3 | 5.1 a 5.3* |
| C04 | Riscos e oportunidades | 6.1 (2026: 6.1.1 a 6.1.3) | 6.1.1, 6.1.2 (aspectos), 6.1.3, (2026: 6.1.4) | 6.1.1; 6.1.2.1 a 6.1.2.3 (perigos, riscos e oportunidades de SST)* | 6.1.2 (risco) e 6.1.3 (oportunidade) | 6.1.1; 6.1.2 (avaliação); 6.1.3 (tratamento) | 6.1* (risco de privacidade integrado) |
| C05 | Objetivos, metas e indicadores | 6.2; 9.1.1 | 6.2; 9.1.1 | 6.2; 9.1.1 | 6.2.2, 6.2.3; 9.1 | 6.2; 9.1 | 6.2*; 9.1* |
| C06 | Planejamento e controle de mudança | 6.3; 8.5.6 | 8.1 (mudança planejada); 2026: 6.3 | 8.1.3 | 6.3; 8.2 | 6.3; 8.1; Anexo A: gestão de mudança (A.8.32) | 6.3* |
| C07 | Competência, conscientização e treinamento | 7.2; 7.3 | 7.2; 7.3 | 7.2; 7.3 | 7.2; 7.3 | 7.2; 7.3; Anexo A: conscientização e treinamento (A.6.3) | 7.2*; 7.3* |
| C08 | Comunicação, consulta e retorno das partes interessadas | 7.4; 8.2.1; 9.1.2 (satisfação) | 7.4.1 a 7.4.3 | 7.4; 5.4 (consulta e participação de trabalhadores) | 7.4; 4.2 | 7.4 | 7.4*; atendimento a titulares (§6.6) |
| C09 | Planejamento e controle operacional | 8.1; 8.5.1 | 8.1 | 8.1.1; 8.1.2 (hierarquia de controles) | 8.1 (inclui ciclo de vida) | 8.1; 8.3 | 8.1* |
| C10 | Fornecedor e provisão externa: critérios, avaliação, contratos | 8.4 | 8.1 (processos terceirizados; 2026: provisão externa) | 8.1.4 (compras, contratados, terceirização) | 8.3 (terceirização)* | 8.1; Anexo A: relação com fornecedores e serviços em nuvem (A.5.19 a A.5.23) | contratos com operadores e suboperadores (controles do Anexo A, §6.6) |
| C11 | Registro de requisitos legais e outros, com avaliação de conformidade | 4.2; 8.2.2; 8.2.3 | 6.1.3; 9.1.2 | 6.1.3; 9.1.2 | 4.2 (requisitos de partes interessadas e regulatórios) | 4.2; Anexo A: requisitos legais e contratuais (A.5.31), privacidade e proteção de dado pessoal (A.5.34) | 4.2*; base de todo o sistema |
| C12 | Monitoramento, medição, análise e avaliação; equipamentos de medição | 7.1.5; 9.1.1; 9.1.3 | 9.1.1 | 9.1.1 | 9.1 (inclui desempenho do ativo) | 9.1 | 9.1* |
| C13 | Auditoria interna | 9.2 | 9.2 | 9.2 | 9.2 | 9.2 | 9.2* |
| C14 | Análise crítica pela direção | 9.3 | 9.3 | 9.3 | 9.3 (inclui risco, oportunidade e decisão) | 9.3 | 9.3* |
| C15 | Não conformidade, ação corretiva e preventiva ou preditiva, melhoria contínua | 8.7 (saída não conforme); 10.2; 10.3 (2026: 10.1 e 10.2) | 10.2; 10.3 | 10.2; 10.3 | 10.1 a 10.3 (10.3 ação preditiva)* | 10.1; 10.2 | 10.1*; 10.2* |
| C16 | Gestão de incidente (SST, ambiental, segurança da informação, violação de dado pessoal, falha de ativo) | — (cabe como não conforme) | 8.2 e 10.2 (incidente ambiental) | 10.2 (incidente, investigação) | 10.1 (falha de ativo como não conformidade) | Anexo A: gestão de incidente (A.5.24 a A.5.28) e relato de evento (A.6.8) | controles de violação de dado pessoal (§6.6) |
| C17 | Preparação e resposta a emergências; continuidade | — | 8.2 | 8.2 | 6.1.2 e 8.1 (resiliência)* | Anexo A: continuidade (A.5.29, A.5.30), cópia de segurança (A.8.13) | — |

Observações:

- Em ISO/IEC 27001, os identificadores A.x.y são do Anexo A de 2022 e foram conferidos em resumos públicos. Não se reproduz o texto dos controles.
- A tabela não afirma que cada norma exige "módulo" ou "ferramenta" de software para cada tema. As normas exigem informação, processo e evidência. O software é um meio.
- ISO 9001:2026 alterou a estrutura do capítulo 10. A tabela traz a numeração de 2015 e indica a de 2026 entre parênteses onde conhecida.

---

## 5. Detalhamento das capacidades comuns (C01 a C17)

Formato de cada capacidade: **Oferta** (o que o software precisa fazer), **Reaproveita** (módulos e tarefas do plano), **Novo**, **Evidência** (o que o sistema gera sozinho e o que fica fora dele), **Ao ligar a norma**.

### C01 — Informação documentada

- **Oferta.** Documentos com identificação, versão, responsável, aprovador distinto do autor, data de vigência, ciclo de revisão, lista de distribuição e ciência (leitura confirmada). Registros imutáveis depois de fechados, com retenção definida e descarte controlado. Histórico completo de alterações. Anexos varridos antes de liberação. Pesquisa e recuperação. Controle de acesso por papel e por classificação.
- **Reaproveita.** Mecanismo de versão, hash e aceite de `platform.document_acceptance` (P1.24; `CENTRAL-ZIIP.md`); aprovação dupla e versão de conteúdo de `parameter_versions` (P0.19); pipeline de arquivos com ClamAV e quarentena (P1.20); publicação versionada de conteúdo de ajuda como padrão de fluxo (P1.21); trilha `audit_log` particionada (P1.7) e `audit_event` encadeado por SHA-256 (`CENTRAL-ZIIP.md` §10); armazenamento com retenção imutável (P2.18, LC.5); tabela genérica `documents` do esquema do tenant (sem fluxo de aprovação); catálogo de permissões `recurso:ação` (P0.13, ADR-0040); retenção por categoria (RNF-40, G6).
- **Novo.** Entidade `cert_document` com ciclo de vida (rascunho, em revisão, vigente, obsoleto), controle de cópia vigente, ciência por usuário, revisão periódica com alerta, classificação de informação (ligada à capacidade de segurança da informação, §6.5), modelos por norma.
- **Evidência.** Sistema gera: versão, autor, aprovador, data, hash, histórico de leitura, trilha de alteração. Fora do sistema: conteúdo e adequação do documento, aprovação formal da direção quando feita fora do sistema, documentos de origem externa que o cliente decide não importar.
- **Ao ligar a norma.** 9001, 14001, 45001, 55001, 27001 e 27701 exigem a mesma capacidade. O que muda: modelos de política (qualidade, ambiental, SST, ativos, segurança da informação, privacidade) e a lista de documentos que o cliente decidiu manter.

### C02 — Contexto, partes interessadas e escopo

- **Oferta.** Registro de questões internas e externas, partes interessadas e seus requisitos, decisão sobre relevância da mudança climática (inserida em 4.1 e 4.2 pela Emenda 1:2024), declaração do escopo e das fronteiras do sistema de gestão, revisão periódica.
- **Reaproveita.** Perfil regulatório do tenant (G1: ERI, norma, área de serviço, natureza jurídica, `reg.*`); `tenant_metadata`; área de atendimento; parâmetros e aceite (P0.19).
- **Novo.** Cadastro de partes interessadas com requisitos e fonte, com vínculo ao registro de requisitos legais (C11) e a riscos (C04). Campo "mudança climática relevante: sim ou não, justificativa".
- **Evidência.** Sistema: histórico de revisões, vínculo requisito a risco. Fora do sistema: a análise propriamente dita e a decisão da direção.
- **Ao ligar a norma.** 55001 acrescenta 4.5 (critérios de decisão); 27701 acrescenta o papel de controlador ou operador por tratamento.

### C03 — Liderança, política, papéis e responsabilidades

- **Oferta.** Política vigente publicada a todos os usuários, com ciência registrada. Matriz de papéis e responsabilidades do sistema de gestão. Designação do representante da direção, do encarregado (27701, LGPD), do responsável pela segurança da informação, do responsável pelo gestor de ativos, do responsável pela SST.
- **Reaproveita.** Papéis e perfis compostos de permissão (P0.13, ED-06, ADR-0040); unidade organizacional e escopo por área (P3.30); aceite com cargo e hash (`parameter_acceptances`); campo de encarregado por tenant previsto no levantamento 10 (PRV-10).
- **Novo.** Matriz papel x responsabilidade do SGI com vigência. Ligação entre a pessoa designada e o papel de plataforma.
- **Evidência.** Sistema: ciência da política por usuário, data, versão. Fora: comprometimento real da direção, recursos alocados.

### C04 — Riscos e oportunidades

- **Oferta.** Registro único de riscos e de oportunidades com categoria (qualidade, ambiental, SST, ativo, segurança da informação, privacidade), causa, consequência, probabilidade e impacto pelo critério do tenant (`cert.risk_scale`), responsável, tratamento, risco residual, revisão. Oportunidades tratadas em registro próprio (separação adotada pelas edições de 2024 e 2026 de 55001 e 9001). Vínculo a ação (C15), a requisito legal (C11), a ativo e a processo. Para 27001, o registro alimenta a declaração de aplicabilidade (§6.5). Para 45001, o inventário de perigos (§6.3). Para 14001, aspectos e impactos (§6.2).
- **Reaproveita.** Cálculo "probabilidade x consequência" planejado para ativos de rede na ED-56 (GIS-19); RIPD existente em `lgpd_dpia` (módulo `compliance/sigsan-lgpd`, hoje sem REST); modelagem de ameaças da plataforma (P0.24, STRIDE, uso interno do Ziip).
- **Novo.** Registro, matriz configurável, tratamento, histórico. Modelos por categoria no pacote `sanitation`.
- **Evidência.** Sistema: data, autor, revisão, vínculo. Fora: qualidade e completude do levantamento de riscos.

### C05 — Objetivos, metas e indicadores

- **Oferta.** Objetivo mensurável, meta, responsável, plano, indicador com fonte, periodicidade, série histórica e semáforo. Indicadores com fonte automática sempre que o dado existe no sistema.
- **Reaproveita.** Indicadores gerenciais, SINISA e macromedição (ED-48); catálogo de relatórios e painéis (P3.22, ED-46, ED-49, P4.3); balanço hídrico e índice de perdas (módulo `intelligence/sigsan-losses`, `WaterBalance`); conformidade de qualidade da água (ED-50); pesquisa de satisfação (`sac.satisfaction_survey`, ED-35); prazos de atendimento e OS.
- **Novo.** Entidade `cert_objective` e vínculo `cert_indicator_link` com indicadores já existentes. Indicadores próprios de cada norma (§6).
- **Evidência.** Sistema: série calculada da fonte, sem digitação. Fora: indicadores cuja fonte é externa (por exemplo, consumo de energia, taxa de frequência de acidente se o cliente não lançar as horas trabalhadas).
- **Atenção.** Indicador que tem definição normativa na ANA (NR 9, NR 15) usa a definição normativa. O objetivo interno do SGI pode ter meta mais exigente, nunca definição divergente (§7).

### C06 — Planejamento e controle de mudança

- **Oferta.** Solicitação de mudança no sistema de gestão, no processo, em instalação, em ativo ou em sistema de informação, com avaliação de risco, aprovação por outra pessoa, plano, verificação pós-implantação e encerramento.
- **Reaproveita.** Parâmetros com vigência, aprovação dupla, efeito somente futuro e simulação (P0.19; `PARAMETROS-DO-TENANT.md` §1); OS de alteração com aprovação por tipo (ADR-0039, `service_type.internal_approval`, P1.31); pacotes de edição do GIS com revisão por outra pessoa (P2.22); changelog do produto e novidades por versão (ADR-0021, P0.41, P1.24), que servem de evidência de mudança do fornecedor.
- **Novo.** Entidade `cert_change_request` genérica, com tipo e vínculo a risco.
- **Evidência.** Sistema: cadeia solicitação, avaliação, aprovação, implantação. Fora: decisão de mérito.

### C07 — Competência, conscientização e treinamento

- **Oferta.** Requisito de competência por função, matriz função x competência, registro de treinamento com data, carga, instrutor, evidência e validade, alertas de vencimento, ligação com permissão (por exemplo, executor de OS em espaço confinado só habilitado com treinamento vigente).
- **Reaproveita.** Usuários, papéis e escopo (P0.13, P3.30); equipes de campo (ED-59); executores da OS (ED-36); regra de segregação de funções do almoxarifado como padrão de bloqueio (ADR-0032).
- **Novo.** `cert_training_record` e matriz de competência. Não há módulo de RH no escopo antes de 2029 (ED-60, sob demanda), por isso o registro deve ser mínimo e independente.
- **Evidência.** Sistema: registro, vencimento, bloqueio. Fora: eficácia do treinamento, certificados emitidos por terceiros (o sistema guarda a cópia).
- **LGPD.** Dado de funcionário é dado pessoal. Dado de saúde (por exemplo, atestado de saúde ocupacional) é sensível. Registrar apenas situação e validade ("apto até"), não o laudo (§7, item 4).

### C08 — Comunicação, consulta e retorno

- **Oferta.** Plano de comunicação (o que, quando, a quem, como, quem), registro de comunicações de partes interessadas e das respostas, canal de relatos internos, pesquisa de satisfação, tratamento de reclamação ligado à capacidade de não conformidade (C15).
- **Reaproveita.** Atendimento completo, protocolo único por canal, ouvidoria, pesquisa de satisfação (ED-35); notificações multicanal (P3.5, ED-41); portal ampliado (ED-38); chamados do Ziip ao tenant (P1.23, interno ao Ziip).
- **Novo.** Plano de comunicação do SGI e registro de comunicações com órgãos (por exemplo, órgão ambiental, autoridade de saúde, ANPD).
- **Evidência.** Sistema: protocolo, canal, prazo, resposta. Fora: reuniões e comunicações verbais.

### C09 — Planejamento e controle operacional

- **Oferta.** Procedimentos operacionais e instruções vinculadas ao tipo de serviço, com checklist de execução, critério de aceitação e registro. Controles operacionais obrigatórios por tipo de OS quando a norma está ligada (por exemplo, análise preliminar de risco para serviço de alto risco, §6.3).
- **Reaproveita.** Catálogo de tipos de serviço, estados da OS e efeitos (P1.31, ED-34, ED-36); formulários, checklists e fotos no campo (ED-20, app de campo P4.4); revalidação do corte na execução (`cut.revalidate_on_execution`); material com alçada (ED-58, ADR-0032).
- **Novo.** Vínculo entre tipo de serviço e procedimento documentado (C01) e conjunto de controles exigidos por norma.
- **Evidência.** Sistema: OS fechada com checklist, foto, geolocalização, executor, material, horário. Fora: execução fiel do procedimento, que só a auditoria verifica.

### C10 — Fornecedor e provisão externa

- **Oferta.** Cadastro de fornecedor e contratado, critérios de qualificação por categoria de risco, avaliação inicial e periódica, desempenho por OS ou entrega, documentos de habilitação com validade (por exemplo, regularidade, seguro, treinamentos de SST), lista de aprovados, bloqueio de fornecedor reprovado.
- **Reaproveita.** Empreiteiras terceirizadas com contratos, penalidades e medição (ED-37); almoxarifado e fornecedores de material (ED-58); cobrança por empresa terceirizada (ED-68); integração com ERP do cliente (ED-62); cadastro de fornecedores do ERP é externo (compras e orçamento em 2029, sob demanda).
- **Novo.** `cert_supplier_evaluation`, critérios por categoria (SST, ambiental, segurança da informação, privacidade), avaliação periódica e histórico. Para 27001 e 27701, a avaliação do próprio Ziip como fornecedor e operador do cliente (§6.5 e §6.6).
- **Evidência.** Sistema: avaliação, nota, validade dos documentos, histórico por OS. Fora: auditoria in loco do fornecedor, contrato assinado.

### C11 — Registro de requisitos legais e outros

- **Oferta.** Lista de requisitos aplicáveis ao tenant (lei, norma de agência, licença, condicionante, contrato, requisito de parte interessada), com fonte, vigência, responsável, forma de atendimento, evidência e avaliação periódica de conformidade com resultado e ação. Alerta de mudança de requisito.
- **Reaproveita.** O produto já guarda a norma de origem de cada parâmetro e de cada limite (`parameter_definitions`, `parameter_legal_limits`, `PARAMETROS-DO-TENANT.md` §6); perfil regulatório do tenant (G1); catálogo normativo da qualidade da água (ED-50); changelog de legislação obrigatório (ADR-0021); aba "Regulatório" da Central Ziip (P3.15); inventário regulatório e NR 16/ANA (ED-56); validação regulatória consolidada (levantamento 14).
- **Novo.** `cert_legal_requirement` por tenant, com avaliação de conformidade periódica (9.1.2 em 14001 e 45001). Catálogo de fábrica de requisitos aplicáveis ao saneamento no pacote `sanitation`, marcado como referência a validar, no padrão de `REFERENCE_TO_VALIDATE` do catálogo GIS.
- **Evidência.** Sistema: vínculo requisito a parâmetro e a registro que o atende, data da avaliação. Fora: interpretação jurídica, parecer do tenant. O catálogo não é parecer jurídico (mesma ressalva de `PARAMETROS-DO-TENANT.md`).

### C12 — Monitoramento, medição e análise

- **Oferta.** Registro de variáveis monitoradas, periodicidade, método, equipamento, responsável, resultado, análise e decisão. Cadastro de equipamento de medição com calibração, validade e bloqueio de uso vencido.
- **Reaproveita.** Hidrometria com aferição, validade e geração de OS em lote (ED-14); instrumentos de laboratório com calibração, histórico e bloqueio (ED-51); telemetria por REST e arquivo (ED-17); `LabInstrument`, `LabQcSample`, `scada_tag` (módulo `intelligence/sigsan-quality`); relatórios (P3.22).
- **Novo.** Cadastro único de equipamento de medição que não é hidrômetro nem instrumento de laboratório (por exemplo, equipamento portátil de campo). Painel de análise por indicador.
- **Evidência.** Sistema: calibração com validade, resultado rastreado ao equipamento. Fora: certificado do laboratório de calibração e rastreabilidade metrológica externa.

### C13 — Auditoria interna

- **Oferta.** Programa de auditoria (frequência, escopo, critérios, auditores), plano da auditoria, lista de verificação por norma e cláusula, registro de constatações (conformidade, não conformidade, oportunidade, observação), relatório, ação corretiva ligada, acompanhamento. Regra de independência: auditor não audita o que executa (verificação automática pelo vínculo com a unidade organizacional).
- **Reaproveita.** Estrutura de checklist e foto do campo (ED-20) [INFERÊNCIA: serve de base para a lista de verificação]; unidade organizacional (P3.30) para a regra de independência; fila e tratamento de pendência.
- **Novo.** Tudo. É o principal item sem equivalente no plano. Modelos de lista de verificação por norma exigem conteúdo próprio (não copiado da norma).
- **Evidência.** Sistema: programa, plano, constatações, ações, prazos. Fora: competência real do auditor, imparcialidade, qualidade da auditoria.

### C14 — Análise crítica pela direção

- **Oferta.** Pauta pré-preenchida com as entradas que as normas pedem (situação das ações anteriores, mudanças de contexto, desempenho e tendências, resultados de auditoria, não conformidades, riscos e oportunidades, requisitos legais, satisfação, incidentes), ata, decisões e recursos, ações geradas, aprovação.
- **Reaproveita.** Todos os indicadores e registros acima; painéis (P3.22, ED-49); relatório anual da ouvidoria (NR 11/ANA art. 52 §2º).
- **Novo.** Entidade `cert_management_review` e geração automática da pauta a partir dos registros.
- **Evidência.** Sistema: pauta e dados de entrada com data e fonte; ata com aprovação. Fora: a realização da reunião e a participação efetiva da direção.

### C15 — Não conformidade, ação corretiva e preventiva ou preditiva, melhoria

- **Oferta.** Registro de não conformidade (de processo, de serviço entregue, de auditoria, de fornecedor, de legislação), contenção, análise de causa, ação corretiva, verificação de eficácia, ação preventiva ou preditiva (55001:2024 10.3), oportunidade de melhoria. Ciclo: aberta, em análise, em ação, em verificação, encerrada. Indicadores de reincidência e de prazo.
- **Reaproveita.** Retificação de conta (P2.20); conta retida por anomalia (`bill.hold_on_anomaly`, P2.5); fila de divergências (ADR-0031); recoleta e reanálise na qualidade da água (ED-50); OS de retrabalho (ED-36); protocolo e ouvidoria (ED-35); trilha de chamados do Ziip como padrão de máquina de estados (P1.23).
- **Novo.** `cert_occurrence` (registro único com subtipos) e `cert_action` (ação com responsável, prazo e eficácia). Ponte que cria a ocorrência a partir de eventos que já existem (por exemplo, água fora do padrão, conta retida, reclamação reiterada).
- **Evidência.** Sistema: cronologia, responsáveis, prazos cumpridos, vínculo a evento de origem. Fora: validade da análise de causa e eficácia real da ação.

### C16 — Gestão de incidente

- **Oferta.** Registro de incidente com tipo (SST, ambiental, segurança da informação, violação de dado pessoal, falha de ativo, interrupção), gravidade, data de conhecimento, relógio de prazos legais, investigação, contenção, comunicação a terceiros, lições aprendidas. Subtipo determina campos obrigatórios e papéis com acesso.
- **Reaproveita.** Prazo da ANPD em 3 dias úteis e registro por 5 anos (Res. CD/ANPD 15/2024; `ret.incident_record_years`, piso 5 anos, P1); resposta a incidente e simulação de mesa (P3.10); incidente de segurança declarado para o acesso de emergência (P3.17, ADR-0020); ocorrência de falha de ativo por OS de reparo (ED-36, ED-56); interrupção programada e imprevista (ED-35, ED-54).
- **Novo.** `security_incident` (listado como "NÃO ATENDE" em SEC-07 do levantamento 10, sem tarefa localizada no plano), com o relógio de 3 dias úteis e o aviso do operador ao controlador. Subtipos de SST e ambiental.
- **Evidência.** Sistema: relógio, comunicação registrada, cadeia de decisão. Fora: a investigação no local, laudos, comunicação feita por canal oficial fora do sistema.

### C17 — Emergência e continuidade

- **Oferta.** Plano de emergência por cenário (ambiental, SST, segurança da informação, continuidade do serviço), responsável, contatos, recursos, simulado com data, resultado e ação, e revisão após uso real.
- **Reaproveita.** Plano de manobra e aviso a afetados (ED-54); interrupção programada e fornecimento de emergência a unidades essenciais (ED-35); metas de recuperação e restauração testada (RNF-14, RNF-15, RNF-16); runbook e plantão (P3.11); ensaio de restauração (GL.3, LC.12).
- **Novo.** `cert_emergency_plan` e `cert_drill`.
- **Evidência.** Sistema: plano versionado, simulado registrado, ata de restauração. Fora: a execução do simulado em campo e a prontidão real da equipe.

---

## 6. Requisitos específicos por norma

Tudo o que está abaixo só deve ficar ativo quando a norma é ligada (§3.2). Dentro de cada norma, a tabela lista a cláusula, o recurso de software, o que se reaproveita e o que é novo.

### 6.1 ISO 9001 — gestão da qualidade

O prestador de saneamento entrega um serviço público essencial. A norma pede controle da prestação, rastreabilidade, tratamento de saída não conforme e medição de satisfação.

| Cláusula (tema) | Recurso de software | Reaproveita | Novo |
|---|---|---|---|
| 4.4 (processos e interações) | Mapa de processos com responsável, entradas, saídas, indicador e risco | Módulos de negócio como processos; tipos de serviço (P1.31) | Cadastro de processo e vínculo a módulo |
| 5.1.2 (foco no cliente) | Requisitos legais do serviço atendidos e riscos ao atendimento acompanhados | Atendimento, `sac.*`, ED-35 | Painel que cruza requisitos e reclamações |
| 7.1.5 (recursos de medição) | Calibração, validade e bloqueio do equipamento | ED-14, ED-51, ED-17 | Equipamento de medição genérico |
| 7.1.6 (conhecimento da organização) | Base de lições aprendidas ligada a ocorrências | — | Módulo simples de lições aprendidas [INFERÊNCIA: pode ficar fora da primeira entrega] |
| 8.2.1 a 8.2.3 (requisitos do serviço e comunicação com o cliente) | Contrato de adesão versionado, catálogo de serviços, análise de requisito antes de oferta | Contrato de adesão como documento versionado (NR 11/ANA arts. 17 a 20; levantamento 08, R-19); catálogo de serviços (ED-34) | Fluxo de análise crítica de requisito com aprovação |
| 8.5.1 (controle da prestação) | Checklist por tipo de OS; trava de ciclo de faturamento por (tenant, ciclo); crítica de leitura | P1.31, ED-36, ED-20, P2.1 (`job_runs`), crítica de leitura no worker | Vínculo a procedimento documentado |
| 8.5.2 (identificação e rastreabilidade) | Cadeia matrícula, unidade consumidora, OS, leitura, conta, versão de parâmetro e de tarifa gravada no resultado | ADR-0039 (unidade consumidora como raiz); `PARAMETROS-DO-TENANT.md` §1.2; `client_reading_id`; cadeia de custódia da amostra (ED-50) | Relatório de rastreabilidade ponta a ponta |
| 8.5.3 (propriedade do cliente) | Proteção do dado pessoal do titular como bem do cliente | Cifra de campo (RNF-32), máscara (P0.11), RLS | — |
| 8.5.6 (controle de mudança na prestação) | Mudança de tarifa e parâmetro com vigência, simulação, aprovação e aceite | P0.19, R3, P2.5 | — |
| 8.6 (liberação) | Liberação do ciclo de faturamento antes da emissão; liberação de OS de corte com revalidação | P2.1, P3.4 | Registro de liberação como evidência da norma |
| 8.7 (saída não conforme) | Conta retida, retificação, água fora do padrão, OS refeita | `bill.hold_on_anomaly`, P2.20, ED-50, ED-36 | Classificação única na ocorrência (C15) |
| 9.1.2 (satisfação do cliente) | Pesquisa pós-encerramento e periódica, reclamações, relatório da ouvidoria | `sac.satisfaction_survey`, ED-35, ED-38 | Consolidação no painel do SGI |
| 9.1.3 (análise e avaliação) | Painel de desempenho do processo | P3.22, ED-49 | — |

**Evidência.** Sistema: rastreabilidade, versões de tarifa, SLA de atendimento, trilha, calibração. Fora: política, cultura da qualidade, conduta ética e competência real (pontos reforçados na edição de 2026).

### 6.2 ISO 14001 — gestão ambiental

| Cláusula (tema) | Recurso de software | Reaproveita | Novo |
|---|---|---|---|
| 4.1 (contexto; 2026: condições ambientais ampliadas) | Campos de condições ambientais relevantes: clima, biodiversidade, poluição, recursos hídricos | G1 (área de serviço), manancial (qualidade da água) | Campos de contexto ambiental |
| 6.1.2 (aspectos e impactos ambientais) | Registro por atividade, instalação e ciclo de vida: aspecto, impacto, situação (normal, anormal, emergência), critério de significância configurável pelo tenant, controle associado | Instalações e ativos do GIS (ED-56); perdas (`WaterBalance`); GRS (resíduos); OS de vazamento | Entidade de aspecto e impacto; catálogo de fábrica para ETA, ETE, elevatória, rede, lodo, produtos químicos, ruído, energia |
| 6.1.3 (obrigações de conformidade) | Licenças, outorgas, condicionantes, padrões de lançamento, com vencimento, responsável e evidência | Catálogo de obrigações (C11); CONAMA 357/430 no levantamento 09; PGRS e licenças no registro de módulos (`pgrs`, sem código hoje) | Cadastro de licença e condicionante com alerta de vencimento |
| 6.1.4 e 6.3 (2026) | Estrutura de decisão sobre risco e oportunidade e mudança planejada | C04, C06 | — |
| 6.2 (objetivos ambientais) | Metas de perda, energia, lodo, reúso, consumo de produto químico | ED-48, losses | Metas ambientais |
| 8.1 (controle operacional; ciclo de vida) | Controles operacionais por instalação e por tipo de OS; critérios para fornecedor de resíduo | OS, MTR (`grs/sigsan-mtr`), SINIR assistido (ADR-0004), ED-37 | Critérios ambientais por tipo de OS e fornecedor |
| 8.2 (emergência ambiental) | Plano por cenário (extravasamento de esgoto, vazamento de produto químico, ruptura de adutora), simulado, comunicação ao órgão | ED-54, C17 | Cenários e contatos de órgão ambiental |
| 9.1.1 (monitoramento) | Consumo de energia, água e produto químico; volume de esgoto coletado e tratado; lodo gerado; análise de efluente | Perdas, qualidade, telemetria (ED-17), GRS | Entrada de consumo de energia e produto químico (lançamento manual ou arquivo) |
| 9.1.2 (avaliação de conformidade) | Verificação periódica de cada obrigação com resultado e ação | C11 | Rotina de avaliação por obrigação |

**Evidência.** Sistema: obrigações com vencimento, resultados de análise vinculados a ponto, MTR, ocorrência ambiental, simulado. Fora: licença ambiental em si, laudo de laboratório externo, conformidade física da instalação.

### 6.3 ISO 45001 — saúde e segurança ocupacional

| Cláusula (tema) | Recurso de software | Reaproveita | Novo |
|---|---|---|---|
| 5.4 (consulta e participação) | Registro de reuniões e consultas com trabalhadores, canal de relato de perigo, quase acidente | C08 | Registro de consulta e canal anônimo de relato |
| 6.1.2 (identificação de perigos, avaliação de riscos e oportunidades de SST) | Inventário de perigos e riscos por atividade e local, com tratamento pela hierarquia de controles | GIS (local), OS (atividade), ED-59 (equipe), C04 | Inventário de perigos e riscos; catálogo de fábrica para vala e escavação, espaço confinado, trabalho em altura, produto químico, trânsito, eletricidade [INFERÊNCIA: relevância ao saneamento a confirmar com SST do cliente] |
| 6.1.3 (requisitos legais de SST) | Normas Regulamentadoras do MTE aplicáveis, com avaliação de conformidade | C11 | Catálogo de NRs/MTE no pacote `sanitation` |
| 8.1.2 (hierarquia de controles) | Controle exigido por perigo, registrado na OS | P1.31 | Campo de controle por perigo |
| 8.1.3 (gestão de mudança) | Mudança de processo, equipamento e local com avaliação de risco de SST | C06 | — |
| 8.1.4 (compras, contratados e terceirização) | Qualificação de SST de empreiteira e subcontratado, documentos com validade, integração de segurança | ED-37, C10 | Documentos de SST do contratado |
| Permissão de trabalho e análise preliminar de risco (prática comum para atividade de risco; a norma exige controle operacional dos riscos) | PT e APR vinculadas à OS de risco, com bloqueio de execução sem elas | P1.31, ED-36, ED-20 (campo offline) | `Permissão de trabalho` e `APR` como anexos obrigatórios por tipo de OS [INFERÊNCIA: forma de cumprir 8.1] |
| EPI e equipamento de proteção | Entrega registrada, validade do certificado de aprovação, devolução | ED-58 (item controlado, entrega registrada), `inv.controlled_items` | Vínculo EPI a trabalhador e função |
| 8.2 (emergência) | Plano, brigada, simulado | C17 | — |
| 9.1.2 (avaliação de conformidade) | Verificação periódica dos requisitos legais de SST | C11 | — |
| 10.2 (incidente, investigação, ação corretiva) | Registro de acidente e quase acidente, investigação com causa raiz, ação, comunicação obrigatória | C15, C16 | Subtipo SST com campos e acesso restrito; exportação de dados para a Comunicação de Acidente de Trabalho feita pelo cliente [INFERÊNCIA: emissão pelo eSocial fica fora do escopo, no modelo "assistido" do ADR-0004] |

**Observação legal.** NR-1/MTE exige que o histórico do inventário de riscos do PGR seja mantido por 20 anos (item 1.5.7.3.3.1), e a NR-7/MTE manda guardar o prontuário médico por pelo menos 20 anos após o desligamento (item 7.6.1.1). Fonte: texto das normas no sítio do Ministério do Trabalho, conferido por pesquisa secundária. [A CONFIRMAR no texto oficial vigente].

**Evidência.** Sistema: inventário, APR/PT vinculada à OS, entrega de EPI, investigação, ações, treinamento vigente do executor. Fora: condição real do local de trabalho, uso efetivo do EPI, saúde ocupacional (laudos permanecem fora), participação real dos trabalhadores.

### 6.4 ISO 55001:2024 — gestão de ativos

A norma pede um sistema de gestão que gere valor do ativo ao longo da vida. Não prescreve software, nem cadastro específico. Na prática, o cadastro de ativos com criticidade e histórico é o meio usual de dar informação adequada (cláusulas 7.5 e 7.6). **Este é o módulo com maior distância entre o pedido e o que existe hoje:** `operations/sigsan-assets` e `operations/sigsan-maintenance` não têm código; ED-56 (ativos, manutenção de rede e inventário regulatório) está na onda C da trilha de editais e depende de ED-53, ED-54, ED-32 e ED-36.

| Cláusula (tema) | Recurso de software | Reaproveita | Novo |
|---|---|---|---|
| 4.2 e 4.5 (partes interessadas e decisão de gestão de ativos) | Critérios de decisão por tipo de decisão (renovar, reparar, manter, descartar), valor, custo, risco, desempenho | C02, ED-56 (plano de renovação) | Registro de critérios e de decisões tomadas |
| 6.1.2 e 6.1.3 (risco e oportunidade) | Risco de ativo por probabilidade (idade, material, vida útil, rompimentos por arco, estado) x consequência (clientes afetados, DN, local, cliente sensível) | ED-56 (GIS-19); isolamento ED-54; OS de reparo (ED-36); `GIS-CADASTRO-TECNICO.md` §11 | Metodologia configurável e relatório |
| 6.2.1 (plano estratégico de gestão de ativos, SAMP) | Documento estruturado: objetivos, critérios de decisão, papel do sistema de gestão, ligação a planos | C01, C05 | Modelo de SAMP e vínculo a objetivos |
| 6.2.2 e 6.2.3 (objetivos e planejamento, com recursos) | Plano de investimento e manutenção por ativo, com orçamento, custo e prazo | ED-32 (eventos contábeis), ED-58 (custo apropriado na OS), ED-48 | Plano de ativos com recurso |
| 6.3 e 8.2 (mudança) | Mudança de ativo, configuração e uso | C06 | — |
| 7.5 e 7.6 (informação e dados de ativos) | Inventário de ativos com hierarquia, localização, atributos, estado, ciclo de vida, qualidade do dado (exatidão, data, origem) | GIS: modelo de feições, metadados de qualidade, precisão por feição, `installed_on`, `abandoned_on`, códigos MCPSB (GIS-01, P1.26; `GIS-CADASTRO-TECNICO.md` §3); hidrômetros (ED-14); frota (ED-59); instrumentos (ED-51) | Registro de ativos não georreferenciados (equipamentos de ETA/ETE, bombas, painéis); ponte entre ativo, feição GIS e patrimônio contábil |
| 8.1 (controle operacional e gestão do ciclo de vida) | Manutenção preventiva, corretiva e preditiva ligada ao ativo; planos, ordens, histórico, custo, indisponibilidade, MTBF e MTTR | OS (P1.31, ED-36), ED-56, ED-58, `maintenance` (nome no registro de módulos) | Plano de manutenção, calendário, indicadores de confiabilidade, tratamento de inspeção |
| 8.3 (terceirização) | Contratado com requisito de gestão de ativos | ED-37 | — |
| 9.1 (desempenho do ativo e do sistema) | Indicadores de desempenho, confiabilidade, custo, risco | ED-48, perdas, qualidade | Painel de ativos |
| 10.1 a 10.3 (não conformidade, ação, ação preditiva) | Falha de ativo como ocorrência; alerta preditivo a partir de série histórica | C15, `leak_detection`/`pressure_analysis` (módulo de perdas) | Regras de alerta preditivo [INFERÊNCIA: modelo estatístico simples na primeira entrega] |

**Sobreposição com a regulação.** A NR 16/ANA (contabilidade regulatória; controle patrimonial MCPSB) e a NR 3/ANA (bens reversíveis) exigem inventário patrimonial com vida útil e rastreabilidade; ED-56 já cobre códigos MCPSB por feição e conciliação GIS x patrimônio. A ISO 55001 não pede contabilidade, mas pede informação confiável do ativo. O mesmo cadastro deve servir às duas (§7, item 9), com a definição da NR/ANA prevalecendo em campos que coincidam.

**Evidência.** Sistema: inventário, histórico de manutenção, custo apropriado, risco calculado, decisão registrada, qualidade do dado por feição. Fora: política de gestão de ativos, plano estratégico aprovado pela direção, competência do gestor, avaliação do valor real, contabilidade oficial.

### 6.5 ISO/IEC 27001:2022 — segurança da informação

Dois papéis precisam ser distinguidos. **(a)** O tenant implanta o próprio sistema de gestão de segurança da informação (SGSI) e usa o Ziip SAN como um dos serviços dentro do escopo. **(b)** O Ziip, como fornecedor e operador, pode buscar a própria certificação (a lista "2028, sob demanda" do plano cita "ISO 27001" sem dizer em qual sentido [INFERÊNCIA: trata-se do Ziip]). Este documento trata de (a), e dá ao cliente o material necessário para avaliar o fornecedor.

| Cláusula (tema) | Recurso de software | Reaproveita | Novo |
|---|---|---|---|
| 4.3 (escopo do SGSI) | Escopo, fronteiras e interfaces, com a lista de serviços de terceiros, entre eles o Ziip | C02 | Escopo com sistemas e interfaces |
| 5.2 (política de segurança da informação) | Política versionada, ciência | C01, C03 | Modelo |
| 6.1.2 e 6.1.3 (avaliação e tratamento de risco de segurança) | Registro de ativos de informação, ameaças, vulnerabilidades, risco, plano de tratamento | C04, `audit_redaction_rule` (lista campos sensíveis) [INFERÊNCIA: serve de semente do mapa de dados] | Registro de risco de SI |
| 6.1.3 d) (declaração de aplicabilidade) | Para cada um dos 93 controles do Anexo A: aplicável ou não, justificativa, estado de implementação, evidência, responsável, última revisão. Exportável | — | **Entidade nova**: catálogo de controles, declaração de aplicabilidade por tenant, **matriz de responsabilidade compartilhada Ziip x cliente** por controle |
| Anexo A: controles de acesso (A.5.15 a A.5.18, A.8.2, A.8.3, A.8.5) | Revisão periódica de acessos por papel e por escopo; relatório de usuários, papéis, último acesso, MFA | Catálogo de permissões e perfis (P0.13, ED-06), escopo por área (P3.30), MFA por tenant (ADR-0007), política de senha por tenant, sessões e trilha visíveis ao administrador (ED-06) | **Campanha de recertificação de acessos** com decisão do gestor, prazo e registro |
| Anexo A: inventário e classificação da informação (A.5.9, A.5.12, A.5.13) | Inventário de ativos de informação e classificação | Máscara de documento (P0.11, ADR-0008), classes de dado no produto, cifra por campo (RNF-32) | Inventário e classificação por tenant |
| Anexo A: relações com fornecedores e nuvem (A.5.19 a A.5.23) | Avaliação do Ziip e do provedor de nuvem como fornecedores; lista de suboperadores | Minutas de DPA e suboperadores (P3.9), certificações do provedor no AWS Artifact (ADR-0011), Central Ziip (documentos vigentes), `document_acceptance` | **Pacote de evidências do fornecedor** (§8.3) |
| Anexo A: incidente (A.5.24 a A.5.28, A.6.8) | Registro, classificação, resposta, lições | C16, P3.10, P3.17 | Entidade `security_incident` |
| Anexo A: continuidade e cópia (A.5.29, A.5.30, A.8.13) | Plano, teste de restauração, evidência | RNF-14 a RNF-16, GL.3, LC.12, P3.11 | Registro do teste de restauração por tenant (o Ziip executa; o cliente recebe a ata) |
| Anexo A: registros, monitoramento e relógio (A.8.15, A.8.16, A.8.17) | Registro de eventos de acesso e de ação | `audit_log` (P1.7), `log.access_retention_days` (piso de 6 meses), registro de leitura (RNF-41), `audit_event` encadeado | Relatório de eventos de segurança para o cliente |
| Anexo A: criptografia, mascaramento e eliminação (A.8.24, A.8.11, A.8.10) | Cifra por campo, máscara configurável, eliminação por procedimento | RNF-32, ADR-0008, `ret.contract_end`, crypto-shredding (RNF-43) | — |
| Anexo A: vulnerabilidades, desenvolvimento seguro, mudança (A.8.8, A.8.25 a A.8.32) | Evidência do ciclo de desenvolvimento e do controle de mudança do fornecedor | CI com SBOM, varredura, gitleaks, pentest (P0.3, RNF-35, RNF-36, LC.8), changelog (ADR-0021) | Resumo para o cliente |
| Anexo A: pessoas (A.6.x) e físicos (A.7.x) | Registro de triagem, termo, treinamento, política de mesa limpa etc. | C07 | Em grande parte fora do sistema; o software registra a evidência |
| 9.2, 9.3, 10.2 | C13, C14, C15 | | |

**Pontos de cuidado.**

- O catálogo de controles do Anexo A é conteúdo protegido da ISO/IEC. Reproduzir identificadores e títulos no produto exige decisão do dono quanto a licença ou reescrita (§10.2).
- A norma admite exclusão justificada de controle; o produto não deve impedir exclusão nem exigir implementação dos 93.
- **Responsabilidade compartilhada.** O cliente não pode excluir um controle só porque o Ziip o executa. Pode marcá-lo como implementado pelo fornecedor, com a evidência do fornecedor. O produto precisa dessa distinção.

**Evidência.** Sistema: declaração de aplicabilidade, revisão de acesso, trilha, incidente, restauração, versão do fornecedor. Fora: segurança física do cliente, triagem de pessoal, política interna, segurança de estações e rede do cliente, conscientização efetiva.

### 6.6 ISO/IEC 27701:2025 — gestão de privacidade

O tenant (prestador de saneamento) é, em regra, **controlador**; o Ziip é **operador** (levantamento 10). A norma de 2025 trata controlador e operador, e o módulo precisa registrar o papel por tratamento. Segundo resumos públicos, o Anexo A de 2025 separa controles de controlador, de operador e de segurança da informação comuns aos dois, e o Anexo F traz a correspondência com a edição de 2019 [A CONFIRMAR no texto]. Os identificadores de 2019 (por exemplo, o registro de tratamento no Anexo A, em A.7.2.8, e no B, em B.8.2.6) são citados só como referência histórica [A CONFIRMAR].

| Tema (cláusula ou controle) | Recurso de software | Reaproveita | Novo |
|---|---|---|---|
| Papel e escopo (4.1 a 4.3) | Papel de controlador e operador por tratamento; escopo do PIMS | C02, `reg.legal_nature` (G1) | Campo de papel |
| **Registro de operações de tratamento** (LGPD art. 37) | Por atividade: finalidade, base legal, categorias de dado e de titular, origem, destinatários, transferência, retenção, medidas de segurança, operador e suboperador, módulo e campo de origem. Exportável para a ANPD | G6 (retenção), `lgpd_retention_schedule`, `audit_redaction_rule` e campos cifrados como mapa de dados [INFERÊNCIA], `reg.legal_nature` | **Entidade `processing_activity`** (PRV-01 do levantamento 10 — "NÃO ATENDE"; sem tarefa localizada no plano) |
| Avaliação de impacto (RIPD, LGPD art. 38; 6.1) | RIPD por tratamento de alto risco, ligado à mudança (C06) | `lgpd_dpia` (módulo `compliance/sigsan-lgpd`); modelo de RIPD (P3.10) | API, fluxo e vínculo a mudança |
| Direitos do titular (LGPD art. 18; prazo de 15 dias, art. 19 II) | Canal, verificação de identidade, tipos de pedido, prazo (`lgpd.dsr_deadline_days`, classe c, só reduz), resposta, evidência | `lgpd_data_subject_request`, `DataSubjectRightsEngine`, portal (P3.20, ED-38) | API REST, canal por tenant, relatório |
| Consentimento e base legal | Registro, revogação, histórico por finalidade | `lgpd_consent` | Vínculo à atividade de tratamento |
| Encarregado e aviso de privacidade | Identidade e contato por tenant, ato de designação, aviso versionado no portal | PRV-10 e PUB-01 (levantamento 10) | Campos e página pública por tenant (white-label) |
| Contratos com operador e suboperador, transferência | Lista de suboperadores, DPA, hospedagem no Brasil | P3.9, RNF-44, ADR-0011 | Registro por tenant |
| Incidente com dado pessoal (controle de violação) | Registro, relógio de 3 dias úteis, aviso do operador ao controlador, comunicação ao titular e à ANPD | C16, Res. CD/ANPD 15/2024, `ret.incident_record_years` | Entidade e fluxo (SEC-07, "NÃO ATENDE") |
| Retenção e eliminação | Cronograma por categoria, executor, certificado de descarte | G6, `ret.*`, RNF-40, RNF-42, RNF-43 | Executor de expurgo (hoje o motor "não elimina") |
| Privacidade desde a concepção | Ponto de verificação na mudança (C06) | ADR-0008, ADR-0034 (agregação mínima de 5 imóveis), cifra de campo | Campo "tratamento novo ou alterado: RIPD feito" |
| Compartilhamento e divulgação de dado pessoal | Registro de compartilhamento com terceiro (ERI, ANA, órgão, birô de crédito) | SINISA e SINIR assistidos (ADR-0004), negativação por arquivo (ED-30) | Registro de destinatários por tratamento |

**Evidência.** Sistema: registro de tratamentos, pedidos de titular com prazos, consentimentos, incidentes, retenção executada, log de acesso a dado pessoal (RNF-41). Fora: base legal escolhida pelo jurídico do cliente, qualidade do aviso, resposta substantiva ao titular, nomeação do encarregado.

---

## 7. Conflitos e sobreposições com exigências legais

**Regra.** As seis normas não são conflitantes entre si (decisão do dono). Quando uma exigência de norma ISO colide com a lei, a lei vence, e a norma passa a ser atendida dentro do limite legal. Normas ISO descrevem o que o sistema de gestão precisa ter, em geral sem fixar prazos nem números; por isso o conflito real é raro. Os itens abaixo separam conflito real de sobreposição.

| # | Tema | Exigência da norma | Exigência legal | Natureza | Resolução | Onde entra |
|---|---|---|---|---|---|---|
| 1 | Retenção de registros do SGI | A informação documentada deve ser retida e protegida (7.5.3 em todas); as normas não fixam prazo; o cliente define | LGPD: conservar só o necessário (arts. 6º III e 15 a 16), com exceção para obrigação legal (art. 16 I); NR 11/ANA art. 22: histórico de 120 meses; NR-1/MTE e NR-7/MTE: prazos de 20 anos em itens de SST; piso de 6 meses para logs (Marco Civil art. 15); incidente 5 anos (Res. ANPD 15/2024) | **Conflito real** quando o cliente quer reter dado pessoal além do necessário, ou eliminar o que a lei manda guardar | Lei vence: o prazo é o maior piso legal aplicável; registro com dado pessoal sem obrigação legal segue o cronograma de G6 e é anonimizado ou eliminado ao fim; registro sem dado pessoal (indicador, ata sem pessoa) pode ficar mais | `cert.record_retention_years` (classe c), G6, RNF-40 |
| 2 | Acesso do auditor externo | Auditoria de terceira parte e de cliente exige acesso a registros (9.2 e auditoria de certificação) | LGPD (minimização, finalidade, segurança); ADR-0020 e ADR-0018: o Ziip não lê dado pessoal do tenant; P0.11: busca por documento exige papel; DPA | **Conflito real** se o auditor precisar ver dado pessoal | Papel `AUDITOR_EXTERNO` por prazo, somente leitura, dado mascarado, sem revelação de documento, cada leitura registrada, consentimento do tenant. Pacote de evidência sem dado pessoal | `cert.external_auditor_access`, `cert.evidence_pack_personal_data` |
| 3 | Dado de saúde na investigação de acidente e no controle de aptidão | 45001 6.1.2, 8.2, 10.2: investigar e registrar | LGPD arts. 5º II e 11: dado de saúde é sensível; base legal de obrigação legal ou tutela da saúde; NR-7/MTE | **Conflito real** de minimização | Registrar o fato e a situação ("apto até"), não o laudo; acesso restrito a SST e gestor; sem exportação em massa; retenção pelo prazo da NR | Subtipo SST da ocorrência (C16) |
| 4 | Sigilo documental x transparência | 27001: classificação e confidencialidade de documentos e de infraestrutura (A.5.12, A.5.13, A.5.33) | Entes públicos e delegatárias sob a Lei de Acesso à Informação (Lei 12.527/2011) [A CONFIRMAR o alcance por natureza jurídica do tenant]; Lei 11.445 (informação ao regulador, art. 25); restrição de infraestrutura crítica no GIS (`GIS-CADASTRO-TECNICO.md` §12) | **Conflito possível** | Classificação não pode ser fundamento de recusa de acesso exigido por lei; toda classificação "restrito" deve citar a base legal do sigilo; rede e ativos publicáveis conforme ADR-0034 | Campo "base legal do sigilo" em `cert_document` |
| 5 | Imutabilidade da trilha x eliminação | 7.5.3 e A.8.15: registro íntegro | LGPD art. 18 VI e 16 | Sobreposição já resolvida | A trilha do produto não carrega dado pessoal e o usuário nunca é apagado, só inativado (plano §2.3 e `PARAMETROS-DO-TENANT.md`); eliminação atua no dado, não na trilha | `audit_redaction_rule` |
| 6 | Logs de segurança | A.8.15: registros de eventos | Marco Civil art. 15 (piso de 6 meses); LGPD minimização | Sobreposição | Piso legal vale; acima dele, só com finalidade | `log.access_retention_days` |
| 7 | Indicadores | 9.1.1 e 6.2: o cliente define indicadores | NR 9, NR 11 e NR 15/ANA e SINISA: definições obrigatórias | Sobreposição com risco de números divergentes | Mesma definição normativa; meta interna pode ser mais exigente | C05 |
| 8 | Prazos de atendimento | Metas internas de resposta e tratamento (9.1.2, 10.2) | Lei 13.460 (ouvidoria 30 + 30 dias), NR 11/ANA, ERI | Sobreposição | Prazo legal é teto; meta interna só mais curta | `cert.internal_target_vs_legal`, `sac.*` |
| 9 | Inventário de ativo x contabilidade regulatória | 55001 7.5 e 7.6: informação de ativos | NR 16/ANA (MCPSB), NR 3/ANA (bens reversíveis), Lei 11.445 | Sobreposição forte | Um único cadastro; campos regulatórios seguem a NR | ED-56 |
| 10 | Incidente com dado pessoal | 27001 A.5.26 (resposta tempestiva), 27701 (violação) | Res. CD/ANPD 15/2024: 3 dias úteis ao controlador, registro por 5 anos; DPA: aviso do operador em prazo menor | Sobreposição | Vale o prazo legal; o aviso do Ziip ao tenant em horas (sugestão do levantamento 10) | C16 |
| 11 | Qualidade da água | 9001 8.7 e 10.2: tratar saída não conforme | Portaria GM/MS 888/2021 e 2.472/2021: comunicação à autoridade de saúde e SISAGUA, independentemente do tratamento interno | Sobreposição | A obrigação regulatória não espera o fechamento da não conformidade | ED-50 |
| 12 | Desligar uma norma | Escolha do cliente | Retenção legal e LGPD | Sobreposição | Desligar não apaga (travado, classe a); expurgo pelo cronograma | `cert.deactivation_keeps_records` |
| 13 | Hospedagem e transferência | 27001 A.5.23, 27701 (transferência de dado pessoal) | LGPD cap. V; RNF-44: dados e backups no Brasil | Sobreposição | Região do Brasil como regra; exceções registradas | RNF-44 |
| 14 | Dado de funcionário em registros de competência | 7.2 | LGPD; sem módulo de RH até 2029 | Sobreposição | Dado mínimo; base legal por obrigação legal e execução de contrato de trabalho, a definir pelo jurídico do cliente | C07 |

Itens fora de conflito mas que merecem registro: não há norma ISO que determine prazo de resposta ao titular nem que contrarie o prazo de 15 dias do art. 19 II da LGPD; a Lei 11.445 (art. 25, informação à ERI) e a NR 11/ANA (art. 22, 120 meses, e art. 52 §2º, relatório anual da ouvidoria) não foram afetadas por nenhuma exigência ISO levantada.

---

## 8. Evidência: o que o sistema gera sozinho e o que depende do cliente

### 8.1 Declaração obrigatória do produto

O Ziip SAN **não certifica, não audita e não atesta conformidade**. Cabe ao organismo de certificação acreditado, escolhido pelo cliente, auditar o sistema de gestão do cliente. O software entrega registro, rastreabilidade, indicadores e evidência de que um processo foi executado. Não prova que o processo é adequado, que as pessoas o seguem ou que o resultado é eficaz.

Consequências para o produto (proposta):

- O mapa de cláusulas deve usar "evidência disponível" e "lacuna", não "conforme" e "não conforme".
- Nenhum material do Ziip deve usar "certificado ISO" para o produto, a não ser que o Ziip obtenha certificação própria, com o escopo exato do certificado. O uso do nome das normas deve respeitar as regras de marca das organizações [INFERÊNCIA].
- A tela de prontidão e o pacote de evidência têm aviso fixo de autoavaliação.

### 8.2 Quadro de evidência por capacidade

| Cap. | O sistema gera sozinho | Depende do cliente fora do sistema |
|---|---|---|
| C01 | Versão, autor, aprovador, data, hash, leitura, trilha | Conteúdo, adequação, documentos externos não importados |
| C02 | Histórico de revisão, vínculos | A análise de contexto e a decisão sobre clima |
| C03 | Ciência da política, designações | Compromisso real da direção, recursos |
| C04 | Registro, revisão, vínculo | Qualidade do levantamento de risco |
| C05 | Séries calculadas de fontes do sistema | Indicadores de fonte externa, metas |
| C06 | Cadeia de solicitação e aprovação | Mérito |
| C07 | Registro de treinamento e vencimento, bloqueio de execução | Eficácia, certificados de terceiros |
| C08 | Protocolo, canal, prazo | Reuniões e conversas verbais |
| C09 | OS fechada com checklist, foto, hora, executor | Execução fiel |
| C10 | Avaliação, validade de documento, desempenho por OS | Auditoria in loco, contrato assinado |
| C11 | Vínculo do requisito ao parâmetro e à evidência | Interpretação jurídica |
| C12 | Calibração, validade, resultado rastreado | Rastreabilidade metrológica externa |
| C13 | Programa, plano, constatações, ações | Competência e imparcialidade do auditor |
| C14 | Pauta com dados, ata, ações | Realização da reunião e participação |
| C15 | Cronologia, prazo, vínculo ao evento | Validade da causa e eficácia |
| C16 | Relógio, comunicação registrada | Investigação no local, laudos, comunicação por canal oficial |
| C17 | Plano, ata de simulado, restauração | Execução em campo, prontidão real |

### 8.3 Evidência do fornecedor (Ziip) entregue ao cliente (proposta)

Para 27001, 27701, 9001 e 14001 o cliente precisa avaliar o Ziip como fornecedor. O produto pode entregar, por tenant, um pacote com: lista de suboperadores e hospedagem (ADR-0011, RNF-44); certificações do provedor de nuvem conferidas no AWS Artifact (ADR-0011); DPA e termos com versão e aceite (P3.9, P1.24); resumo do último pentest externo e da revisão de segurança (RNF-36, LC.8, P3.8); ata do último ensaio de restauração com tempo e contagem (RNF-16, GL.3, LC.12); política de MFA e senha vigentes do tenant (ADR-0007, ADR-0040); changelog de segurança e legislação (ADR-0021); métrica de disponibilidade e página de status (P3.18, LC.7); resumo de acesso assistido e de emergência ocorridos no tenant (P3.17); certificado de descarte, quando for o caso (RNF-42). Algumas dessas evidências só existem depois do lançamento comercial, pelo ADR-0033 (pentest externo, métrica real de disponibilidade, restauração em produção).

### 8.4 O que nunca está no sistema

Certificado emitido pelo organismo; resultado da auditoria externa; política aprovada pela direção; cultura e comportamento; segurança física; condição de trabalho; laudos de saúde; licença ambiental original; conformidade física da instalação; qualidade de dados lançados à mão.

---

## 9. Dependências no plano e tarefas propostas

### 9.1 Situação

O plano não tem tarefa para o módulo "Certificações de qualidade". As tarefas existentes que servem de base e as que ainda precisam existir antes dele:

| Base necessária | Tarefa do plano |
|---|---|
| Permissões `recurso:ação`, perfis, escopo por área | P0.13, ED-06, P3.30, ADR-0040 |
| Parâmetros com vigência, aceite, aprovação dupla | P0.19, P0.20 |
| Contrato do pacote de vertical e teste de fronteira | P0.47, ADR-0037 |
| Trilha de auditoria particionada | P1.7 |
| Arquivos com varredura e quarentena | P1.20 |
| Documentos com versão e aceite | P1.24 |
| Núcleo de OS | P1.31 (depois ED-34, ED-36) |
| Atendimento e ouvidoria | P3.4, ED-35, ED-41 |
| Relatórios e painéis | P3.22, ED-46, ED-48, ED-49 |
| Qualidade da água e laboratório | ED-50, ED-51 |
| GIS com ativos, precisão e inventário | P1.26, P2.21 a P2.23, ED-53, ED-54, ED-56 |
| Almoxarifado | ED-58 |
| Empreiteiras | ED-37 |
| Resposta a incidente e modelo de RIPD | P3.10 |
| Segurança do ambiente e documentos contratuais | P3.8, P3.9, P3.17 |
| Aquisição de normas para curadoria | Padrão de P1.28 (ABNT); equivalente ISO ainda sem tarefa |

### 9.2 Tarefas propostas (códigos provisórios)

| Código | Conteúdo | Depende de |
|---|---|---|
| CQ-01 | Núcleo do módulo: ativação por norma, mapa de cláusulas, parâmetros `cert.*`, permissões `cert:*`, versão da edição | P0.13, P0.19, P0.47, P1.7 |
| CQ-02 | Informação documentada com ciclo de revisão, ciência e classificação | CQ-01, P1.20, P1.24 |
| CQ-03 | Riscos e oportunidades; objetivos e indicadores com vínculo a fontes | CQ-01, P3.22, ED-48 |
| CQ-04 | Ocorrência e ação (não conformidade, incidente, melhoria), com pontes para eventos existentes | CQ-01, P1.31, ED-35, P3.10 |
| CQ-05 | Auditoria interna e análise crítica pela direção | CQ-03, CQ-04 |
| CQ-06 | Competência e treinamento; avaliação de fornecedor | CQ-01, P3.30, ED-37 |
| CQ-07 | Requisitos legais por tenant e avaliação de conformidade, com catálogo de fábrica | CQ-01, ADR-0021, aba Regulatório da P3.15 |
| CQ-08 | Pacote 9001 | CQ-02 a CQ-05 |
| CQ-09 | Pacote 14001: aspectos, licenças, emergência ambiental | CQ-03, CQ-04, CQ-07 |
| CQ-10 | Pacote 45001: perigos, APR e PT na OS, investigação, EPI | CQ-04, CQ-06, ED-36, ED-58, ED-20 |
| CQ-11 | Pacote 55001: inventário, ciclo de vida, risco e criticidade, SAMP, manutenção | ED-56, ED-53, ED-36, ED-58, ED-59 |
| CQ-12 | Pacote 27001: declaração de aplicabilidade, matriz de responsabilidade compartilhada, recertificação de acessos | CQ-04, ED-06, P3.8, P3.9, P3.17 |
| CQ-13 | Pacote 27701: registro de tratamentos, direitos do titular, incidente, API do módulo LGPD | CQ-04, `compliance/sigsan-lgpd`, P3.10, P1.15 |
| CQ-14 | Pacote de evidência (do tenant e do Ziip) e papel de auditor externo | CQ-02, CQ-12, CQ-13, P3.15 |
| CQ-15 | Curadoria de conteúdo: aquisição dos textos das normas, modelos de documento e catálogos de fábrica, com a política de licença do conteúdo ISO | decisão do dono |

Não há ordem de calendário. A ordem é a de dependência.

---

## 10. Fatos confirmados, lacunas e inferências

### 10.1 Fatos confirmados

**Do projeto (documentos e código do repositório):**

- O plano não menciona módulo de certificações. ISO 27001 aparece apenas na lista "2028, sob demanda" (§4 e §5 do plano; ADR-0001) e em exigências de edital sobre o provedor de nuvem (ADR-0011).
- `ModuleRegistry` registra `assets` e `maintenance` como módulos opcionais, com descrição "ISO 55001, RCM" apenas em texto. Os módulos `sigsan-assets`, `sigsan-maintenance`, `sigsan-field`, `sigsan-portal`, `sigsan-pgrs`, `sigsan-disposal` e `sigsan-collection-waste` não têm arquivos em `src/main`.
- Existem, com código, `sigsan-quality` (parâmetros, amostras, instrumentos, métodos, controle de qualidade, SCADA), `sigsan-losses` (balanço hídrico, DMA, vazamento, pressão), `sigsan-lgpd` (consentimento, pedido de titular, RIPD, cronograma de retenção, engines de anonimização e retenção), `sigsan-gis` e `sigsan-mtr`.
- O levantamento 10 registra: sem API REST e sem teste no módulo LGPD; sem registro de operações (PRV-01), sem incidente (SEC-07), sem campos de encarregado (PRV-10).
- O plano fixa pisos de retenção (120 meses, 6 meses de logs, 5 anos de incidente), parâmetros de classe a a d, aprovação dupla e aceite, `audit_event` encadeado, ClamAV, ED-56 na onda C e ED-58 na onda A.
- O Ziip é operador; o tenant é controlador (levantamento 10).

**Externos (pesquisa pública):**

- ISO 9001:2026 foi publicada em setembro de 2026, substitui a edição de 2015 e incorpora a Emenda 1:2024; mudanças em riscos e oportunidades separados, mudança, cultura da qualidade e conduta ética, melhoria em 10.1 e não conformidade em 10.2 (comitê técnico ISO/TC 176, resumo da edição e pré-visualização do sumário).
- ISO 14001:2026 foi publicada em abril de 2026 como 4ª edição (página da ISO); novidades em 4.1 e 4.2, 6.1.4, 6.3, provisão externa e programa de auditoria documentado (página da ISO "O que mudou").
- ISO 45001 está em fase de projeto de norma internacional, com publicação esperada para 2027 (comitê ISO/TC 283); ISO 45001:2018 segue vigente com a Emenda 1:2024 (clima).
- ISO 55001:2024 é a 2ª edição, publicada em julho de 2024; novidades em 4.5, 6.1.2 e 6.1.3, 6.2.1, 6.3, 7.6, 8.1 e 10.3 (página do comitê ISO/TC 251 e nota do organismo holandês de normalização).
- ISO/IEC 27001:2022 tem a Emenda 1:2024; o Anexo A tem 93 controles em 4 temas (37, 8, 14, 34); a declaração de aplicabilidade é exigida em 6.1.3 d).
- ISO/IEC 27701:2025 foi publicada em outubro de 2025, substitui a edição de 2019, é autônoma, não exige 27001 e usa cláusulas 4 a 10 (organismos de certificação e de acreditação).
- NR-1/MTE (item 1.5.7.3.3.1, 20 anos para o histórico do inventário de riscos do PGR) e NR-7/MTE (item 7.6.1.1, 20 anos para o prontuário médico), por pesquisa secundária em sítios do governo; LGPD art. 16 I permite conservação para cumprir obrigação legal.

### 10.2 Lacunas e inferências

**Lacunas (não resolvidas por este levantamento):**

1. Não li o texto integral de nenhuma das seis normas. Toda subcláusula marcada com asterisco no Quadro 1, e todos os identificadores de controle, dependem do texto adquirido. Para 27701:2025, a numeração de cláusulas e controles é a mais incerta.
2. Em ISO 9001:2026 e ISO 14001:2026 só foram verificadas as mudanças declaradas em resumos. A estrutura de subcláusulas das duas edições precisa ser conferida.
3. O alcance da Lei de Acesso à Informação sobre cada tipo de tenant (autarquia, empresa pública, concessionária privada) não foi apurado.
4. Não foi verificado se os módulos de MTR e de GIS já têm campos que sirvam diretamente à 14001 e à 55001 (apenas existência de código).
5. Não localizei no plano tarefa para: registro de operações de tratamento (PRV-01), entidade de incidente de segurança (SEC-07), API REST do módulo LGPD, campos de encarregado (PRV-10). O levantamento 10 os lista como "NÃO ATENDE" e a P3.10 cobre só prazos, modelo de RIPD e simulação de mesa. Se existirem em outro item do plano, o CQ-13 deve apontá-lo.
6. O plano não decide se o Ziip busca certificação própria (27001, 27701, 9001). A lista "2028, sob demanda" cita só "ISO 27001".
7. Não há decisão sobre a licença de uso do conteúdo das normas ISO no produto (títulos e identificadores de controle do Anexo A, modelos de lista de verificação).
8. Não foi avaliado o encaixe do módulo nas fases do plano (sem fase atribuída).
9. Não há tarefa de RH antes de 2029 (ED-60); o registro de competência do CQ-06 terá de ser independente.
10. O transporte de dados para a Comunicação de Acidente de Trabalho (eSocial) não foi pesquisado.

**Pontos [INFERÊNCIA]:**

- Riscos psicossociais devem constar desde cedo no inventário de SST (a revisão da 45001 sinaliza o tema).
- Relevância de vala, espaço confinado, altura, produto químico, eletricidade e trânsito para o saneamento (a confirmar com SST do cliente).
- `audit_redaction_rule` e a lista de campos cifrados servem de semente do mapa de dados do registro de tratamentos.
- A estrutura de checklist do app de campo (ED-20) serve de base para a lista de verificação da auditoria interna.
- PT e APR vinculadas à OS são a forma de cumprir o controle operacional de risco (8.1 da 45001), embora a norma não use esses nomes.
- O "ISO 27001" da lista de 2028 refere-se ao Ziip.
- Uso do nome das normas e de selos exige respeito às regras de marca das organizações.
- Um modelo estatístico simples basta para a primeira versão da ação preditiva (55001, 10.3).
- A base de lições aprendidas (9001, 7.1.6) pode ficar fora da primeira entrega.
- A escala de risco do tenant 5x5 como padrão de fábrica.

### 10.3 Decisões que cabem ao dono (cada uma com recomendação, sem presunção)

1. Se o módulo entra no núcleo da plataforma e o conteúdo de saneamento no pacote `sanitation` (recomendado).
2. Se o Ziip busca certificação própria e qual (27001 é a mais pedida em edital; os editais analisados exigem certificação do provedor de nuvem, não do fornecedor do software [levantamento 18h, H3]).
3. Política de licença do conteúdo das normas ISO no produto.
4. Edições alvo: 9001:2026, 14001:2026, 27701:2025 como principais, com suporte à 2015 e à 2019 apenas em transição (recomendado).
5. Papel de auditor externo: ligar por padrão ou não (recomendado: desligado).
6. Se a aquisição dos textos das normas entra como tarefa análoga à P1.28.

---

## 11. Fontes consultadas

Páginas públicas, consultadas durante o levantamento:

- ISO/TC 176, notícia da publicação da ISO 9001:2026 e transição (committee.iso.org/sites/tc176).
- Pré-visualização do sumário da ISO 9001:2026 (standards.iteh.ai).
- ISO, página da ISO 14001:2026 e "O que mudou na ISO 14001:2026" (iso.org).
- ISO/TC 283, notícia do projeto de norma da ISO 45001 (committee.iso.org/sites/tc283); DQS e DNV, revisão da ISO 45001.
- ISO/TC 251, página da ISO 55001 e nota de transição do NEN, ISO 55001:2024.
- Resumos sobre ISO/IEC 27001:2022 e Emenda 1:2024 (organismos de certificação).
- Pré-visualização da ISO/IEC 27701:2025 (standards.iteh.ai), BSI, SGS, DNV e UKAS sobre a revisão e a transição.
- Sítios do Ministério do Trabalho e Emprego (NR-1 e NR-7), por pesquisa secundária.

Documentos do projeto: `docs/PLANO-EXECUCAO-PDCA-R0-R9.md`; `docs/arquitetura/ARQUITETURA-ALVO.md`, `PARAMETROS-DO-TENANT.md`, `PLATAFORMA-E-VERTICAIS.md`, `CENTRAL-ZIIP.md`, `REQUISITOS-NAO-FUNCIONAIS.md`, `GIS-CADASTRO-TECNICO.md`; `docs/levantamento/08`, `09`, `10`, `14`; `docs/levantamento/gis/19c`; `docs/levantamento/editais/18h`.
