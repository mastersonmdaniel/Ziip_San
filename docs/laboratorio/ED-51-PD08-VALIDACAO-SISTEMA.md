# ED-51-PD08 — Validação do sistema por versão: desenho e fronteiras

<!-- Sessao produtora: 988dfd54-a5d6-408e-b95f-d01fa8aab6e8 · Papel: Produtor -->

Módulo existente `shared/ziipsan-core`, **mesmo pacote** da ED-51
`br.com.ziipsan.core.certification.lims17025` (núcleo puro, sem
JPA/Spring; persistência, controllers e telas são da integração —
padrão ED-51/ED-50). Este pacote constrói o **PD-08**: o pacote de
validação do sistema por versão para apoio à ISO/IEC 17025, que o
aceite da ED-51 deixou expressamente pendente de decisão do dono
(pendência P-ED51-01) e que o dono decidiu **construir** em
09/11/2026 (item 40 do `DECISOES-DO-DONO.md`).

## Derivação de escopo — o que as fontes fixam e o que não fixam

As fontes definem o PD-08 **apenas** como "pacote de validação do
sistema por versão para a ISO/IEC 17025", no módulo de certificações
(ADR-0041), pendente até haver cliente que o exija (plano v3.0;
decisão PD-08 de 28/10/2026) — gatilho substituído pela decisão do
dono de construir já. **Nenhuma fonte fixa:** conteúdo/checklist
normativo da validação, papéis aprovadores específicos, prazos,
retenção, periodicidade, nem se versão não validada bloqueia o uso
do laboratório. Portanto este pacote constrói o **mecanismo mínimo**
de validação por versão, reutilizando **somente** padrões que a
própria ED-51 já estabeleceu:

| Fonte (padrão da ED-51) | Decisão de execução neste pacote |
|---|---|
| Régua real `Lims17025Activation.requireEnabled()` (ADR-0041; teste ligada/desligada) | (a) O dossiê só nasce com a norma ligada; emenda também passa pela régua. Desligada, nada nasce e nada se apaga (SQL sem DELETE) |
| Tudo é dado declarado do tenant/laboratório; SQL não semeia nenhuma linha (disciplina de fontes da ED-51 §1) | (b) `systemVersion`, itens do plano e `evidenceRef` das execuções são declarados; nenhum item de checklist, nenhuma versão e nenhuma evidência nascem de fábrica — nem no Java, nem no SQL |
| `AssayExecution.validateBySecondPerson`: validador = analista é recusado | (c) Quem conclui/aprova o dossiê não pode ser quem declarou o plano nem quem executou/registou qualquer item |
| `ProficiencyTesting`: escore-z é aritmética; a avaliação é a **como reportada** pelo provedor, sem faixa de \|z\| codificada | (d) O resultado de cada item é o **como registado** por quem executou (`PASSED`/`FAILED` — dado); a conclusão exige **completude aritmética**: todos os itens declarados com execução registada; o desfecho do dossiê é a aritmética sobre os registos vigentes (qualquer `FAILED` vigente → `FAILED`) — nenhum limiar, faixa ou percentual inventado |
| `LabEquipment`: histórico de calibração por INSERT novo, nunca reescrito; `LabReport.amend`: versão seguinte com motivo, anterior `SUPERSEDED` preservada | (e) Execuções são histórico por acréscimo (novo registo do mesmo item preserva o anterior e vira o vigente); a correção de dossiê **concluído** é por emenda — revisão seguinte com motivo obrigatório e `supersedesId`, anterior `SUPERSEDED` preservada |
| SQL da ED-51: `db/pendente/` sem número Flyway, tabelas `lims_*`, `register_tenant_table` em todas, CHECKs espelhando o Java, zero seeds, zero DELETE | (f) SQL novo `ED-51-PD08__lims_system_validation.sql` com 3 tabelas `lims_system_validation*` nas mesmas regras; a numeração é da integração (P-ED51PD08-01) |

## Componentes

| Classe | Papel |
|---|---|
| `SystemValidation` | **O dossiê de validação por versão**: nasce gated pela régua com versão do sistema e plano declarados; regista execuções em histórico por acréscimo; conclui por segunda pessoa só com o plano completo; emenda rastreável de dossiê concluído (revisão seguinte, anterior `SUPERSEDED` preservada) |
| `SystemValidationPlanItem` | Item do plano **declarado** (chave × descrição) — forma validada, nenhum conteúdo de fábrica |
| `SystemValidationExecution` | Registo de execução de um item: resultado **como registado** (`Outcome.PASSED`/`FAILED`), referência de evidência obrigatória, quem executou e quando |

SQL pendente: 3 tabelas — `lims_system_validations` (dossiê/revisão,
com FK para `lims_laboratories` da ED-51 e auto-FK da emenda),
`lims_system_validation_items` (plano declarado) e
`lims_system_validation_executions` (histórico de execuções, com FK
composta para o item do plano). CHECKs espelham o Java: textos não
vazios (`btrim`), `outcome` nos valores do enum, coerência de
conclusão por estado, segregação concluinte ≠ declarante, emenda
com motivo e `supersedes`. A segregação concluinte ≠ executores
vive entre tabelas e é conferida no serviço (padrão da ED-51 para
papeis conferidos no serviço).

## Fronteiras e pendências

- **P-ED51PD08-01 (integração):** JPA/repositórios das 3 tabelas,
  numeração Flyway do SQL pendente (slot livre da faixa
  `certification`, conferindo a FK contra `lims_laboratories` do SQL
  pendente da ED-51 — os dois só resolvem juntos na numeração),
  prova do SQL em PostgreSQL, controllers/telas do dossiê. Soma-se à
  fiação geral da ED-51 (P-ED51-02, que **permanece**).
- **P-ED51PD08-02 ([A DEFINIR]/[SEM FONTE] — normativo):** conteúdo
  do checklist de validação por versão (quais itens compõem o plano
  de uma validação real) — nenhuma fonte o fixa; o plano é declarado
  pelo tenant/laboratório até existir fonte.
- **P-ED51PD08-03 ([A DEFINIR]/[SEM FONTE] — gatilhos):** quais
  mudanças de versão exigem nova validação/revalidação (maior,
  menor, correção) — não implementado nenhum gatilho; cada dossiê é
  aberto por declaração.
- **P-ED51PD08-04 ([A DEFINIR]/[SEM FONTE] — retenção):** prazo de
  retenção dos dossiês e das evidências — nenhum prazo codificado;
  o desenho é por acréscimo, sem exclusão.
- **P-ED51PD08-05 ([A DEFINIR]/[SEM FONTE] — bloqueio):** se uma
  versão **não validada** deve bloquear o uso do laboratório —
  **não implementado**: nenhuma fonte declara esse bloqueio e este
  pacote não o inventa. O dossiê regista e conclui validações; não
  intercepta ensaios nem laudos da ED-51.

## Fora de escopo

Fiação/aplicação real (P-ED51-02, permanece); qualquer requisito,
cláusula ou número da ISO/IEC 17025 além do que a ED-51 já fixou
(nenhum é citado nem codificado aqui); calculador/app de campo
(ED-19) **não é tocado** por este pacote.
