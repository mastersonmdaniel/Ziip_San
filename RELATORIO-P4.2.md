# P4.2 — Relatório de entrega (R6: SINISA assistido)

Repo: `mastersonmdaniel/Ziip_San`, branch `main`, HEAD de referência `0fbcd22`.
Entrega **local, sem push**, em `~/workspace/ziipsan-producao/p4.2/entrega/`
(caminhos do repo), pronta para `push-files`. Espelho em
`~/workspace/ziipsan-producao/p4.2/espelho/`. Revisão cruzada em
`~/workspace/ziipsan-producao/p4.2/REVISAO-P4.2.md`.

## Limites de acesso (honesto)

- O MCP do GitHub respondeu **HTTP 429** na 4ª chamada útil. Parada respeitada:
  nenhuma nova chamada foi feita. Só foi espelhado o `pom.xml` real do
  `integration/ziipsan-integration` (+ listagens que confirmam o pacote raiz
  `br.com.ziipsan.integration`). O restante do desenho usou os pacotes locais
  já conferidos no mesmo HEAD (espelho/entrega da P2.5 e padrões da P3.6).
- **Não conferido:** se já existe código no pacote
  `br.com.ziipsan.integration.sinisa` no repo (pacote novo da tarefa; conferir
  antes do push) e o conteúdo atual do módulo `intelligence/ziipsan-losses`.
- **Sem compilação nesta VM** (sem JDK/Maven/Docker): a prova é a CI total
  (`scripts/ci.sh`) no PC do dono, por camada, como nas demais tarefas.

## Verificação externa obrigatória (fontes oficiais, antes da construção)

| Fonte oficial | URL | O que sustenta |
|---|---|---|
| Área do Prestador — SINISA (Ministério das Cidades) | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/area-do-prestador | Ciclo anual e etapas (coleta 13/mai–03/set; preliminar até 03/nov; resposta até 17/nov; publicação + Certificado de Adimplência até 31/dez, no ciclo 2026/ano-base 2025, Portaria MCID 1.069/2025); módulos de Água e Esgoto com **ambiente separado para prestadores regionais (mais de um município) e locais (somente um município)** |
| Portaria MCID nº 1.069, de 16/09/2025 (DOU) | https://pesquisa.in.gov.br/imprensa/servlet/INPDFViewer?jornal=515&pagina=21&data=18/09/2025&captchafield=firstAccess | Art. 8º: coleta **por sistema web** da SNSA/Ministério das Cidades; art. 7º, §§3º–4º: acesso a recursos federais do art. 50 da Lei 11.445/2007 exige adimplência, condicionada à **finalização do preenchimento**; definições de versão preliminar e resposta; calendário do ciclo e divulgação do Certificado (até 31/dez) |
| Página do SINISA (Ministério das Cidades) | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/sinisa-1/sinisa | Módulos e formulários/glossários publicados por módulo (anos 1, 5 e 10); **Glossário de Informações com código único por dado coletado** — base do catálogo carregável |
| Manual de Preenchimento — Prestadores Regionais/Locais Água (ano ref. 2025) | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/area-do-prestador/arquivos/028_manual_preenchimento_regional_defeso.pdf | Adimplência como condição de acesso a recursos federais (Lei 14.026/2020; Decreto 11.599/2023), verificada por **Certidão de Regularidade por tipo de serviço**; formulários financeiros = Receita e Cobrança; Despesas, Investimento e Pessoal; **Balanço Patrimonial** (exceto natureza Município e Autarquia); preenchimento **via Internet, no sistema online** |
| Certidão de Regularidade com o SINISA — Água (ano ref. 2024) | https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/resultados-sinisa/SEI_6346910_Certidao_de_Regularidade_com_o_SINISA___Agua.pdf | Texto da certidão: Decreto 11.599/2023, art. 7º, V (fornecimento de informações ao SINISA comprovado por certidão); validade até o lançamento da base do ano de referência seguinte |

**Conclusão da verificação (governa o desenho):** o meio de envio oficial é o
**sistema web de coleta** — não há API pública de envio nem formato oficial de
arquivo de importação publicado. Portanto o "arquivo no formato oficial" do
aceite é entregue no modo assistido do ADR-0004 ("arquivo oficial, o cliente
envia") como **pacote de transcrição** (`SinisaSubmissionFile`): documento JSON
com canal declarado `WEB_SYSTEM_TRANSCRIPTION`, valores, origens e
justificativas, e resumo SHA-256 de evidência. Nenhum formato foi presumido.

Fórmula do ILI/UARL: metodologia IWA Water Loss Task Force (Lambert) —
UARL (L/dia) = (18·Lm + 0,8·Nc + 25·Lp)·P; ILI = CARL/UARL — conferida em
literatura técnica da metodologia (ex.: https://www.mdpi.com/3042-5743/44/1/28).

## Arquivos entregues (19)

Módulo `integration/ziipsan-integration` (faixa `V7_x`/integration do plano,
dona do SINISA), pacote `br.com.ziipsan.integration.sinisa`:

| Arquivo | O que é |
|---|---|
| `SinisaForm.java` | Enum FINANCEIRO / TECNICO / BALANCO, na estrutura do Manual oficial |
| `SinisaProviderScope.java` | LOCAL / REGIONAL com a regra oficial de municípios (1 × mais de um) |
| `SinisaParameters.java` | Leitura tipada de `sinisa.provider_scope` via `Json` do core; ausência = `MissingParameterException` |
| `SinisaVariable.java` | Variável do mapa: formulário, unidade, origem (módulo/tabela/cálculo); sem origem = recusada; `officialCode` nunca presumido |
| `SinisaCatalog.java` | Catálogo **carregável** (JSON via `Json` do core): duplicidade e variável sem fonte recusadas — base da ED-48 |
| `SinisaValue.java` | Valor do ciclo: AUTOMATIC × MANUAL; **manual só com justificativa** (guarda na fábrica e no construtor) |
| `SinisaSourceData.java` | Porta do pré-preenchimento (origem sem dado = pendente, nunca zero) |
| `SinisaCycle.java` | Agregado do ciclo anual: abertura com guardas (escopo × municípios, responsável, corte LC.13), pré-preenchimento, transições RASCUNHO → PRELIMINAR → RESPOSTA_REGISTRADA → CONCLUIDO registradas em trilha, reabertura só com motivo, conclusão com certificado |
| `WaterBalance.java` | Balanço hídrico IWA: NRW, perdas de água, perdas reais (CARL), com recusas de inconsistência |
| `IliCalculator.java` | UARL e ILI (IWA/Lambert); insumo inválido ou UARL nula = erro explícito |
| `ComplianceCertificate.java` | Registro do Certificado de Adimplência: ano-base, tipo de serviço e **data obrigatória** |
| `SinisaSubmissionFile.java` | Pacote assistido de transcrição (JSON via `Json` + SHA-256); só a partir da preliminar registrada |
| `src/main/resources/sinisa/sinisa-catalog-seed.json` | Semente do mapa (6 variáveis) com origens reais da P2.5 (metering/billing/registry/derivado/manual) e `officialCode: null` em todas |

Testes (6 classes, 30+ casos): `WaterBalanceTest` (caso da v1: 3.000 m³),
`IliCalculatorTest` (UARL/ILI conferidos à mão), `SinisaCycleTest` (manual sem
justificativa recusado; transições registradas; escopo LOCAL × REGIONAL;
responsável; corte LC.13; certificado de outro ano recusado),
`SinisaParametersAndCatalogTest` (parâmetro ausente/inválido; semente;
variável sem origem e chave duplicada recusadas),
`ComplianceCertificateTest` (sem data recusado), `SinisaSubmissionFileTest`
(canal declarado, hash confere, determinismo, rascunho não gera).

**Migration: nenhuma.** O mapa de migrations do plano (seção 3) não reserva
migration para a P4.2 (a faixa `V7_x`/integration cita ED-33/ED-62 e a
`V15_x`/reporting cita ED-46 a ED-49). Pela regra da tarefa, nenhuma foi
criada: o ciclo é agregado de domínio puro com portas, e o `pom.xml` real do
módulo nem tem spring-data-jpa. A persistência entra quando a faixa receber
migration própria (declaração registrada também no javadoc de `SinisaCycle`).

## Acceptance, item a item

1. **Mapa das variáveis (financeiro, técnico, balanço) → origem no Ziip SAN** — ✅
   `SinisaVariable` + `SinisaCatalog` carregável + semente com origem por
   variável (metering/billing da P2.5, registry, derivado, manual). Códigos
   oficiais do Glossário: **não inventados** (`officialCode: null`, a carregar
   do glossário vigente — montagem completa é da ED-48).
2. **Rascunho pré-preenchido, versão preliminar e resposta registradas** — ✅
   `SinisaCycle.prefill` pela porta de origem; transições append-only com ator,
   instante e observação; preliminar exige ciclo completo (pendência nomeada).
3. **Valor manual só com justificativa** — ✅ guarda em `SinisaValue` (fábrica +
   construtor) e teste dedicado (nula e em branco recusadas).
4. **Certificado de Adimplência registrado com data** — ✅ `ComplianceCertificate`
   (data nula recusada; por tipo de serviço, como a certidão oficial) e
   conclusão do ciclo só com certificado do mesmo ano-base. Fundamento citado:
   Lei 11.445/2007 art. 50; Decreto 11.599/2023 art. 7º, V; Portaria 1.069/2025.
5. **Prestador local × regional por `sinisa.provider_scope`, com responsável** — ✅
   `SinisaParameters` + guarda de abertura: escopo incompatível com os
   municípios atendidos é recusado (definição oficial); responsável obrigatório.
6. **Arquivo no formato oficial, meio confirmado na página oficial, sem presumir
   API** — ✅ parcial honesto por desenho: confirmado oficialmente que o canal é
   o **sistema web** e que **não existe** formato de importação/API publicado;
   o arquivo entregue é o pacote assistido de transcrição com hash (ADR-0004).
   Se o SINISA publicar importação por arquivo, o gerador ganha um renderizador
   novo sem tocar no ciclo.
7. **Casos da v1 passam (água não faturada de 3.000 m³; ILI)** — ✅ como testes:
   10.000 − 7.000 = 3.000 m³; UARL = 290.000 L/dia e ILI = 1 (e 2 com CARL
   dobrado), valores conferidos à mão. (Execução real: só na CI do dono.)
8. **Base do catálogo SINISA da ED-48** — ✅ o catálogo carregável + semente são
   a base; catálogo vigente completo e painel são da ED-48 (não feitos aqui).
9. **Lembrete da janela na agenda de obrigações (ED-98)** — ⏸ da ED-98, não
   desta tarefa. O que a P4.2 entrega de insumo: as etapas e o calendário
   oficial citados nas fontes acima (coleta 13/mai–03/set etc., da Portaria
   1.069/2025) para a ED-98 montar o lembrete — nenhum lembrete foi criado aqui.
10. **Primeiro envio assistido = primeiro ano-base depois do corte (LC.13)** — ✅
    guarda de abertura: ano-base ≤ ano de corte (parâmetro vindo da LC.13, sem
    constante inventada) é recusado, com teste.

## O que fica para outras tarefas (declarado, não feito)

- **ED-48:** catálogo SINISA vigente completo (códigos do Glossário de
  Informações por ciclo/módulo, inclusive esgoto) sobre este mapa + painel e
  benchmark.
- **ED-98:** lembrete da janela do SINISA na agenda de obrigações do tenant.
- **LC.13:** fornecer o ano-base de corte real à abertura do ciclo.
- **Fiação (integração, padrão M7 da P2.5):** adaptador de `SinisaSourceData`
  agregando metering/billing/registry; persistência do ciclo quando houver
  migration da faixa; tela do ciclo. Nada disso foi simulado aqui.

## Riscos para a CI

- Assinaturas conferidas estaticamente contra o espelho do core
  (`Json`, `ApiException.Conflict/Validation.of/FieldError`,
  `MissingParameterException`); a revisão corrigiu 1 bloqueio real de
  compilação (construtor inexistente de `Validation`) e 2 asserções de teste.
- `SinisaParametersAndCatalogTest` lê a semente de
  `/sinisa/sinisa-catalog-seed.json` no classpath (recursos do módulo).
- Se o repo já tiver pacote `sinisa` no módulo integration (não conferível
  pelo 429), resolver choque de nomes antes do push.
