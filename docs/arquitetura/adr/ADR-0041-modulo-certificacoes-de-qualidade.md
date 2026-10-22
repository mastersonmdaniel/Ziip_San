# ADR-0041 — Módulo Certificações de qualidade: catálogo único de normas, ativáveis pelo tenant

- **Status:** Aceito, 21/10/2026
- **Decisor:** dono do produto (diretriz e cartões de 21/10/2026)
- **Afeta:** ADR-0002 e ADR-0015 (parâmetros e classes); ADR-0037 (núcleo e pacote de vertical); ADR-0038
  (bifurcação vira parâmetro); ADR-0039 (OS); ADR-0040 (permissões); ADR-0028 (qualidade da água e ISO 17025);
  `docs/arquitetura/CERTIFICACOES-DE-QUALIDADE.md`; levantamentos `docs/levantamento/certificacoes/21a` e `21b`;
  plano v2.17

## Contexto

Os prestadores de água e esgoto trabalham sob normas de três naturezas: obrigatórias (resoluções da ANA, leis,
portarias, resoluções do CONAMA, norma da agência reguladora), voluntárias certificáveis (ISO 9001, 14001, 45001,
55001, ISO/IEC 27001 e 27701), voluntárias acreditáveis ou orientativas (ISO/IEC 17025, ISO 24510, 24511 e 24512,
ISO 46001) e técnicas de referência (NBR, Portaria Inmetro de medidores). O plano até a v2.16 trata as obrigatórias
por parâmetros e perfil regulatório, mas não tem nada para as voluntárias: não há não conformidade, ação corretiva,
auditoria interna, análise crítica, controle de documentos nem registro de operações de tratamento de dados.

Os levantamentos 21a e 21b mostraram que as normas de sistema de gestão partilham 17 capacidades de software
(informação documentada, riscos, objetivos, competência, mudança, requisitos legais, auditoria interna, análise
crítica, não conformidade, incidente, fornecedor, entre outras), e que o específico de cada norma é pequeno em
número de registros. O dono do produto decidiu que o sistema deve atender a todas as normas, sem conflito entre elas,
e que só as exigências específicas dependem de o cliente ligar a norma.

## Decisão

1. **Novo módulo "Certificações de qualidade"** (nome técnico `certification`), com catálogo único de normas.
   O cliente liga e desliga cada norma; ao ligar, o sistema passa a atender as exigências daquela norma.
2. **Catálogo único com quatro classes de norma:**
   - `REGULATORY`: obrigatória por aplicabilidade (NR 8, 9, 11, 13, 15 e 16 da ANA, NR-1, 18, 33 e 35 do MTE (segurança do trabalho; ajuste de 22/10/2026), Portaria GM/MS 888/2021 e
     SISAGUA, CONAMA 357, 430 e 498, Lei 11.445/2007, Leis 14.015, 14.898, 13.460 e 12.007, Decreto 5.440/2005,
     LGPD, Portaria Inmetro 155/2022). **Não desativável.** Aparece na lista marcada como obrigatória e é alimentada
     pelo perfil regulatório (ADR-0038); a aplicabilidade depende do que o tenant tem (por exemplo, a NR 15 depende
     de a agência adotá-la; o CONAMA, de haver esgoto).
   - `VOLUNTARY`: ativável (ISO 9001, 14001, 45001, 55001, ISO/IEC 27001, 27701, 17025, ISO 46001, ISO 24510,
     24511 e 24512).
   - `REFERENCE`: técnica de referência, ativável como checklist de completude (NBR de rede, cadastro, topografia
     e medidores).
   - `EXPORT_PROFILE`: perfil de exportação de indicadores (ISO 37120, PNQS), sem entrada ativável própria.
3. **Capacidades comuns sempre disponíveis.** As 17 capacidades comuns existem sem norma ligada, como registros
   livres. Ligar uma norma torna obrigatórios os campos e ciclos que ela exige (por exemplo, causa raiz e verificação
   de eficácia na ocorrência), habilita os registros específicos da norma e o mapa de cláusulas.
4. **Várias normas ao mesmo tempo.** Um registro satisfaz várias cláusulas (vínculo muitos-para-muitos entre
   evidência e cláusula). A mesma ocorrência pode ser não conformidade (9001), incidente de SST (45001) e incidente de
   segurança (27001), sem duplicar.
5. **Desligar nunca apaga registro** (classe a, travado). O mapa da norma é congelado, os campos obrigatórios deixam
   de ser exigidos e os registros continuam consultáveis pelo prazo de retenção.
6. **Escopo declarado.** O tenant liga a norma e declara o escopo (`cert.scope_statement`), que pode ser um
   laboratório, uma ETA, uma localidade ou o tenant inteiro (cartão de 21/10/2026).
7. **Edição da norma por tenant.** `cert.<norma>.edition` guarda a edição em uso; o catálogo de cláusulas é
   versionado por edição e aceita vigência futura. Alvos: ISO 9001:2026, ISO 14001:2026 e ISO/IEC 27701:2025, com
   2015, 2015 e 2019 apenas em transição; ISO 45001:2018; ISO 55001:2024; ISO/IEC 27001:2022 com a Emenda 1:2024.
8. **O sistema não certifica.** A certificação é do organismo acreditado, após auditoria no cliente. O mapa de
   cláusulas é autoavaliação do cliente, nunca parecer do produto, e a interface o diz. O Ziip SAN produz, organiza
   e preserva evidência e aponta lacuna de cláusula.
9. **Conflito com a lei: a lei vence** (ADR-0038, item 3). Pontos de atrito: retenção de registro do sistema de
   gestão contra a LGPD, acesso de auditor externo, dado de saúde na investigação de acidente, confidencialidade
   documental contra transparência pública. O validador de coerência (ADR-0002) avisa quando uma norma ativa pedir
   algo mais restritivo que o limite legal travado.
10. **Evidência sem dado pessoal por padrão.** O pacote de evidência leva dado agregado, mascarado ou
    pseudonimizado (classe a). O papel de auditor externo vem desligado; ligado, é somente leitura, com prazo e
    registro de cada leitura (classe b).
11. **Texto das normas fora do produto.** Os textos pagos (ISO, ABNT) são comprados para curadoria dos mapas de
    cláusulas, escritos em palavras próprias; o produto guarda título, edição e número de cláusula, sem o texto
    (cartão de 21/10/2026).
12. **Arquitetura.** O motor do módulo (documentos, riscos, objetivos, ocorrências, ações, auditoria, análise
    crítica, mapa de cláusulas, papéis) é transversal e pertence ao núcleo da plataforma (ADR-0037); o conteúdo de
    saneamento (aspectos ambientais de ETA e ETE, perigos de vala e espaço confinado, classes de ativo, catálogo de
    obrigações setoriais) pertence ao pacote `sanitation`, como catálogo de fábrica extensível pelo tenant. O módulo
    se habilita pelo manifesto da vertical e por permissões `cert:read`, `cert:write`, `cert:approve` e `cert:audit`
    (ADR-0040).
13. **Pacotes por norma.** Estrutura do módulo no P1 a P3; pacotes de cada norma na trilha de editais (ED-69 em
    diante), com teste de aceite por norma ligada e desligada.
14. **Extensões do setor decididas por cartão em 21/10/2026.** O balanço de perdas da NR 15 sobe na ordem da trilha
    por dependência (ED-78, depois da ED-48); a trilha de esgoto e água bruta (efluente, outorga, licença, Declaração
    de Carga Poluidora, biossólido, reúso) é pacote próprio (ED-80); a ampliação da ISO 17025 e o cadastro de
    hidrômetros pelo regulamento metrológico entram como ajuste da ED-51 e da ED-14.

15. **Área "Normas e legislação".** O módulo tem uma área de consulta com a biblioteca de normas do setor (leis, decretos, normas de referência da ANA, normas de agências reguladoras, NBR, ISO e padrões técnicos), carregada do catálogo consolidado em `docs/normas` (ajuste de 22/10/2026, tarefa P1.34). A biblioteca guarda só metadados e resumos em palavras próprias, com selo de verificação e de vigência.

## Consequências

### Positivas

- O cliente escolhe as normas que quer demonstrar e o sistema acompanha, sem código por cliente.
- Uma só base de evidência serve a várias normas.
- Reaproveita OS (P1.31), parâmetros com aceite (P0.19), trilha de auditoria (P1.7), atendimento (ED-35), ativos
  (ED-56) e qualidade da água (ED-50, ED-51).
- Fecha lacunas regulatórias que o plano não cobria (perdas pela NR 15, efluente, relatório anual de qualidade).

### Negativas

- Muitas capacidades novas sem equivalente no plano: não conformidade, auditoria interna, análise crítica, riscos,
  requisitos legais, registro de operações de tratamento.
- Pacotes de norma exigem curadoria com os textos comprados e manutenção a cada nova edição.
- Cada norma ativável precisa de teste de aceite próprio, nos estados ligada e desligada.
- Risco de o cliente ler o mapa de cláusulas como garantia de certificação; a interface precisa evitar isso.

## Alternativas consideradas

- Só as normas voluntárias no módulo: rejeitada pelo dono do produto; as obrigatórias entram na mesma lista, sem
  poderem ser desligadas.
- Qualquer norma, inclusive as legais, desativável: rejeitada; conflita com as travas de lei federal (ADR-0038).
- Módulo e pacotes de norma só na trilha de editais: rejeitada; as tarefas do P1 a P3 precisariam ser reabertas.
- Embutir o texto das normas no produto: rejeitada por direitos autorais.

## Tarefas derivadas

- P1.32 (núcleo), P2.25 (documentos, ocorrência e ação), P3.31 (riscos, objetivos, auditoria, análise crítica,
  competência, fornecedor e requisitos legais).
- ED-69 a ED-82 (pacotes por norma, evidência, curadoria, indicadores, perdas, sistemas de abastecimento, esgoto,
  uso próprio de água, perfis de exportação).
- Ajustes: P0.19, P0.47, P1.7, P1.24, P1.28, P1.31, P3.10, P3.15, P3.22, ED-06, ED-14, ED-35, ED-48, ED-51, ED-56,
  ED-58.
- Parâmetros `cert.*` em PARAMETROS-DO-TENANT; reservas Flyway: core `V9_8`, tenant `V21_x`.
- Pendentes de decisão do dono, sem trava: pacote de validação do sistema por versão para a ISO 17025; se o Ziip
  busca certificação própria além da ISO/IEC 27001 já prevista (a ISO/IEC 27701 é a candidata).
