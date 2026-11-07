# Revisão cruzada independente — P3.16 Ensaio de saída de tenant sintético

**Revisor:** agente revisor cruzado independente (primeira revisão
independente do pacote; a `REVISAO-P3.16.md` anterior era segunda
passada do próprio autor). **Escopo:** pacote em
`~/workspace/ziipsan-producao/p3.16/entrega/` contra o acceptance
literal da P3.16 em
`~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-P3.json`,
com recálculo manual de todos os prazos e conferência estática contra
os espelhos locais (P2.5/P3.19: `Json`, `DeadlineCalculator`,
`DeadlineCountingRule`, `TicketRetentionService`, V9, V10_1, V10_2,
`pom.xml` do core). Sem JDK/Maven nesta VM: compilação não executada;
imports/assinaturas conferidos um a um contra os espelhos (todos
batem: `DeadlineCalculator.computeDueDate(LocalDate,int,
DeadlineCountingRule,Set)`, `TicketRetentionService.purgeDate(
LocalDate)`, `requirePurgeAllowed(String, ArchivalApproval)`,
`Json.write(Object)`). **Nenhum push, nenhuma escrita no GitHub.**

## Veredito: LIBERA COM CORREÇÕES APLICADAS

Os 3 defeitos de guarda abaixo eram **bloqueantes** e foram
corrigidos nesta revisão (detalhe na seção Correções). Depois deles,
não resta defeito de código sanável objetivamente: o que sobra são
(1) uma interpretação de spec **aritmeticamente forçada** que precisa
de confirmação do dono (âncora dos tetos 60/75 no Setor Público —
se o dono ler o teto literalmente do fim do contrato também havendo
consulta, o item vira bloqueio **da spec**, não do código: consulta
de até 120d + carência 30d + certificado 30d = até 180d > 75d) e
(2) fiação de integração já declarada no relatório do autor.

## Recálculo manual dos prazos (contrato 2026-03-01 dom → 2026-12-31 qui, 305 dias)

| Prazo | Conta refeita à mão | Código/teste | Confere |
|---|---|---|---|
| Exportação STANDARD | 31/12/2026 + 15 corridos = **2027-01-15** (sex) | `exportDueDate` / `exportDueStandard` | ✓ |
| Exportação PUBLIC_SECTOR | 10 úteis após qui 31/12, sem feriado: 01/01 sex=1, 04=2, 05=3, 06=4, 07=5, 08=6, 11=7, 12=8, 13=9, **14/01 qui=10** | `exportDuePublicSectorBusinessDays` | ✓ |
| Idem com feriado 01/01 | pula 01/01 → 10º útil = **2027-01-15** | `exportDuePublicSectorWithHoliday` | ✓ |
| Consulta teto | min(305, 120)=120 → 31/12 + 120 = **2027-04-30** | `readonlyCappedAt120Days` + CHECK `<= INTERVAL '120 days'` | ✓ |
| Consulta contrato curto | 01/11→31/12 = 60d → 31/12 + 60 = **2027-03-01** | `readonlyFollowsShortContractTerm` | ✓ |
| Carência STANDARD | 31/12 + 30 = **2027-01-30** | `graceStandardFromContractEnd` | ✓ |
| Carência PUBLIC_SECTOR | fim da consulta 30/04 + 30 = **2027-05-30** (conta do fim da consulta, como o acceptance manda) | `gracePublicSectorFromReadonlyEnd` | ✓ |
| Resposta à contestação | registrada 30/01 10:00 + 15 corridos = **14/02 10:00**; alerta = vencimento − 3d = **11/02 10:00** | `contestationResponseAndAlertWithClock` | ✓ |
| Certificado sem contestação | carência+30 = âncora+60 → **2027-03-01** (= âncora + 60, teto não aperta) | `certificateWithoutContestation` | ✓ |
| Certificado c/ contestação no último dia | regra daria 14/02 10:00 + 30 = 16/03 10:00; teto âncora+75 = **2027-03-16 00:00** aperta | `certificateWithContestationAtGraceEnd` | ✓ |
| Resposta atrasada (10/03) | regra daria 09/04; teto 75 segura em **2027-03-16** | `certificateCapBindsOnLateResponse` | ✓ |
| Exceção dos chamados | `purgeDate(2026-12-31)` = vigência + 5 anos = **2031-12-31** | `certificateContent` | ✓ |
| SHA-256 | vetor conhecido de `"abc"` confere | `sha256KnownVector` | ✓ |

Os tetos 60/75 **estão codificados** (`certificateDueAt` = min(regra,
âncora + 60/75)) **e testados** com datas literais — não só
documentados. Dias corridos × úteis foram distinguidos corretamente:
só a exportação do Setor Público usa dias úteis (via
`DeadlineCalculator` da P1.36, dia da abertura nunca conta); todos os
demais prazos são corridos, como o acceptance marca.

## Tabela item a item do acceptance

| # | Item do acceptance | Estado | Evidência / nota |
|---|---|---|---|
| 1 | Única migration = core `V10_5` | **Coberto** | Só há a `V10_5` no pacote; nenhuma outra migration criada/inventada. |
| 2 | Exportação em até 15 dias (10 úteis no Setor Público) | **Coberto** | Regra pura + testes com datas calculadas à mão (tabela acima). Ressalva menor: o vencimento é calculado/gravado na abertura; nada sinaliza exportação gerada **depois** do vencimento (`registerExportGenerated` não confere `export_due_at`). |
| 3 | Formatos CSV, JSON ou XML, manifesto, hash, dicionário gerado do schema | **Coberto** | `TenantExitExport.render/manifest/manifestHash/dataDictionary`; JSON **só** via `Json` do core (grep conferido: 3 usos, nenhum `ObjectMapper`/JSON manual); dicionário sem metadado = erro explícito. Ressalvas menores na seção Defeitos (CSV/XML). |
| 4 | Setor Público: consulta somente leitura pelo prazo do contrato, máx. 120 dias, antes da carência | **Parcial** | Duração = vigência do contrato (fim − início) com teto 120, em código, CHECK no banco e teste — é **interpretação** de "pelo prazo do contrato" (o modelo não tem campo de prazo de consulta no contrato); período criado na abertura e carência contada do fim dele. |
| 5 | Escrita recusada na consulta (teste) | **Parcial** | Portão puro `requireWriteAllowed` testado nos limites ([início, fim)) + `requireWriteAllowedNow` no serviço com `Clock` injetado. A ligação do portão a cada rota de escrita do backoffice é fiação de integração (declarada pelo autor). O serviço lê só a **primeira** linha `ACTIVE` se houver mais de um período. |
| 6 | Carência de 30 dias do fim do contrato; do fim da consulta quando ela existe | **Coberto** | `graceStartsAt` por nível; PUBLIC_SECTOR sem fim de consulta = erro explícito. |
| 7 | Download da exportação e contestação registradas na carência | **Coberto** | `tenant_exit_download` + `recordDownload`; contestação só dentro da carência [início, fim] inclusive, com teste nos limites. |
| 8 | Eliminação agendada só por `PLATFORM_OWNER` com MFA, cancelável durante a carência | **Coberto com correção** | Papel/MFA antes da transação + CHECK `scheduled_mfa` no banco + teste. **Correções C2/C3** fecharam: cancelamento antes do início da carência era aceito, e cancelar/executar não exigiam estado agendado. |
| 9 | Execução antes do fim da carência recusada (teste) | **Coberto** | `requireExecutionAllowed` recusa inclusive no instante exato do fim (convenção coerente com a carência inclusiva usada em contestação/cancelamento); teste `executionBeforeGraceEndRefused`. |
| 10 | Contestação na carência suspende; resposta em até 15 dias; alerta na Central antes do vencimento (teste com relógio) | **Parcial** | Suspensão por `Holds.contestationOpen` testada; resposta = registrada + 15d; alerta devido a partir de vencimento − 3d testado com instantes fixos (relógio explícito nas funções puras; `Clock` injetado no serviço). Parcial porque: (a) a antecedência de 3 dias é **convenção** (acceptance não numera) a confirmar; (b) a publicação do alerta na Central (V9_1) é job/fiação — aqui há a consulta `contestationsDueForAlert` + `alert_sent_at`. |
| 11 | Ordem judicial registrada suspende enquanto vigorar (teste) | **Coberto** | `lifted_at IS NULL` = vigente; `Holds.courtOrderActive`; teste de suspensão e de `any()`. |
| 12 | Crypto-shredding previsto no fluxo | **Parcial** | Porta `KeyDestruction` (P0.28) + `key_destroyed_at` obrigatório para `ELIMINATED` (CHECK) + recusa sem confirmação. A prova de ilegibilidade é o passo 10 do roteiro (CI/ambiente do dono). Observação de desenho em Defeitos (destruição dentro da transação). |
| 13 | Eliminação concluída e certificado em até 30 dias após carência/última resposta, máx. 75 do fim do contrato (60 sem contestação) | **Parcial — interpretação** | Codificado e testado **exatamente** como a spec no STANDARD (âncora = fim do contrato = início da carência). No PUBLIC_SECTOR a âncora é o **fim da consulta**: leitura declarada e aritmeticamente forçada (ver Veredito), mas **diverge do texto literal** ("do fim do contrato") e exige confirmação do dono. Nada no código alerta/escalona vencimento do certificado — o vencimento fica gravado em `certificate_due_at`. |
| 14 | Certificado com hashes, expiração de backups e de cópias imutáveis e exceção dos chamados | **Coberto com correção** | Hash da exportação + hash por arquivo, `backup_expires_at`, `immutable_copy_expires_at` (ausência recusa) e exceção `support_tickets` com `purge_at` da P3.19. **Correção C4:** manifesto nulo/vazio gerava certificado com `files=[]` (sem hash por arquivo) — agora recusado no `TenantExitExport.certificate` e antes da destruição da chave no serviço. |
| 15 | Natureza pública: eliminação usa temporalidade/listagem da P3.19 (RAS-18) e é recusada sem elas (teste) | **Parcial** | `executeElimination` chama `TicketRetentionService.requirePurgeAllowed` como **primeira** instrução, antes de qualquer escrita — reuso real, sem segunda implementação. Mas **neste pacote não há teste** do portão: os testes por valor de `reg.legal_nature` vivem no pacote da P3.19 (`TicketRetentionServiceTest`, conferidos: PRIVATE libera; AUTARCHY/PUBLIC_COMPANY/SEM recusam sem os 4 registros; natureza desconhecida recusa fail-closed). A recusa no caminho da P3.16 só é exercitada no passo 7 do roteiro (CI). |
| 16 | Fora de escopo: restauração seletiva e cópia lógica diária (P3.33) | **Respeitado** | Não implementado, declarado no roteiro — **não** cobrado como defeito. |

## Correções aplicadas nesta revisão

| # | Arquivo | Era | Ficou |
|---|---|---|---|
| C1 | `exit/TenantExitCalculator.java` | `requireCancellationAllowed(graceEndsAt, now)` só recusava **depois** do fim: cancelar **antes do início** da carência (ex.: durante a consulta) era aceito, contra "cancelável **durante** a carência" | Assinatura `(graceStartsAt, graceEndsAt, now)`; recusa `grace_not_started` antes do início e `grace_elapsed` depois do fim; janela [início, fim] |
| C2 | `exit/TenantExitCalculator.java` + `exit/TenantExitService.java` | `executeElimination` **ignorava o status**: executava eliminação nunca agendada (furando o portão PLATFORM_OWNER+MFA, que só existia no agendamento), executava eliminação **cancelada** (cancelamento sem efeito) e reexecutava tenant já `ELIMINATED` | Nova guarda pura `requireEliminationScheduled(status)` (código `elimination_not_scheduled`), chamada na execução e no cancelamento logo após `loadState`; teste novo `eliminationMustBeScheduledToCancelOrExecute` cobre EXPORT_PENDING/IN_GRACE/CANCELLED/ELIMINATED |
| C3 | `exit/TenantExitCalculator.java` + `exit/TenantExitService.java` | `scheduleElimination` atualizava o status incondicionalmente: tenant já `ELIMINATED` (certificado emitido, chave destruída) podia ser "reagendado" e reexecutado | `scheduleElimination` carrega o estado na transação e aplica a nova guarda pura `requireSchedulingStateAllowed` (`already_eliminated`); reagendar após `CANCELLED` continua permitido, como o roteiro (passo 5) exige |
| C4 | `exit/TenantExitExport.java` + `exit/TenantExitService.java` | `certificate(...)` aceitava `manifest == null` e emitia certificado com `files=[]` — certificado **sem o hash de cada arquivo** que o acceptance exige; e `executeElimination` não validava o manifesto, podendo destruir a DEK antes de a emissão falhar | `certificate` exige manifesto não nulo e não vazio (erro explícito, no padrão "ausência = erro"); `executeElimination` valida o manifesto junto das demais obrigações, **antes** de abrir a transação e de destruir a chave; teste novo `certificateRequiresManifest` |
| C5 | `exit/TenantExitService.java` | `cancelElimination` usava a assinatura antiga da C1 | Chamada atualizada para `(graceStartsAt, graceEndsAt, now)` + guarda de estado da C2 |
| C6 | `exit/TenantExitCalculatorTest.java` | Teste de cancelamento só cobria o limite do fim | Atualizado para a nova assinatura, com os códigos `grace_not_started`/`grace_elapsed` e o teste das guardas de estado (C2/C3). Nenhuma asserção afrouxada — só adicionadas/endurecidas |
| C7 | `exit/TenantExitMigrationConsistencyTest.java` | `checksMatchEnums` era **fraco/circular** para formatos: `'CSV'/'JSON'/'XML'` aparecem no CHECK de `tenant_exit.export_format`, então o teste passava mesmo sem a alteração da `tenant_export` da V9 | Teste novo `tenantExportFormatCheckIsWidened` exige `ALTER TABLE platform.tenant_export`, `ADD CONSTRAINT tenant_export_format_check` e a localização via `pg_constraint` |

## Defeitos e pendências (não corrigidos — motivo registrado)

1. **Âncora dos tetos 60/75 no PUBLIC_SECTOR (interpretação — decisão do dono).**
   Spec literal: teto "do fim do contrato". Código: âncora = início
   da carência (= fim da consulta no Setor Público). Com consulta de
   120d, a leitura literal torna o fluxo impossível (120+30+30=180 >
   75). A leitura do código é a única executável e fecha a conta que
   o próprio acceptance sugere (30+15+30=75), mas **não é o texto** —
   confirmar com o dono antes/junto do push; se ele impuser a leitura
   literal, o item 13 deve ser tratado como inconsistência do
   acceptance a corrigir no plano, não como defeito sanável no código.
2. **Antecedência do alerta da contestação = 3 dias (convenção).**
   Acceptance só diz "antes do vencimento". Declarada no Javadoc e no
   relatório; confirmar com o dono.
3. **Fragilidade da V10_5 no bloco da `tenant_export`.** O `DO $$`
   localiza o CHECK por `pg_get_constraintdef(oid) LIKE '%format%'`
   com `SELECT ... INTO` sem `STRICT`: no espelho da V9 só existe um
   CHECK contendo "format" (o de `format IN ('JSON')`, cujo nome
   automático é justamente `tenant_export_format_check`, removido
   antes do `ADD CONSTRAINT` de mesmo nome), então funciona contra o
   banco conhecido; se um dia houver outro CHECK com "format" no
   def, a escolha é arbitrária. Não reescrevi porque a forma robusta
   depende do banco real — **conferir na primeira aplicação da V10_5
   na CI** (o roteiro já manda abortar em qualquer falha de migração).
4. **Destruição da chave dentro da transação de banco.**
   `keyDestruction.destroy()` é efeito externo irreversível chamado
   com a transação aberta: se um passo posterior falhar e houver
   rollback, a DEK já foi destruída sem o tenant ser marcado
   `ELIMINATED` (com a C4, a causa conhecida de falha após a
   destruição — manifesto inválido — foi eliminada; restam falhas de
   SQL/commit). Desenho da porta da P0.28; registrar para a integração
   avaliar confirmação em duas fases.
5. **Coerências menores de estado (cosméticas, sem efeito nos portões):**
   status `IN_GRACE`/`HELD` da V10_5 nunca são gravados pelo serviço
   (os portões leem datas e tabelas de contestação/ordem, não o
   status); `cancelElimination` não limpa `scheduled_*` e o
   reagendamento não limpa `cancelled_at/by`; a atualização de
   `tenant_lifecycle` na execução não confere linhas afetadas
   (silenciosa se a linha não existir, embora grave `DELETED` + hash
   quando existe, como a V9 prevê).
6. **`openExit` não grava `contract_id`** (coluna da V10_5 fica sempre
   `NULL`: a assinatura recebe datas, não o id do contrato) e o nível
   (STANDARD/PUBLIC_SECTOR) é parâmetro do chamador, não derivado do
   perfil do tenant/contrato — mesmo padrão de `legalNature` na
   P3.19, mas a proveniência do nível fica para a integração.
7. **Exportação física:** `registerExportGenerated` grava um único
   formato/hash por processo (sobrescreve em nova chamada) e não
   escreve em `platform.tenant_export` (V9); o passo 2 do roteiro
   ("gerar nos 3 formatos e registrar") só registra o último. CSV usa
   as colunas da **primeira** linha (linhas heterogêneas perderiam
   chaves em silêncio) e o XML usa o nome da coluna cru como tag
   (seguro para identificadores de `information_schema`, frágil fora
   disso). Risco baixo no uso previsto; registrar.
8. **Testes fracos remanescentes (não são asserção afrouxada):**
   `manifestHash` determinístico compara a função consigo mesma
   (a correção do SHA-256 em si está ancorada no vetor de `"abc"` e
   o hash por tabela é conferido contra `render`); nenhum teste de
   serviço com banco neste pacote — padrão do projeto (regras puras
   aqui, fluxos no roteiro/CI), mas significa que
   `contestationsDueForAlert`, `respondContestation` (recálculo do
   `certificate_due_at`) e o portão RAS-18 no caminho da P3.16 só
   serão provados na CI total do dono.
9. **Sem compilação nesta VM** (sem Java/Maven/Docker): sintaxe,
   imports e assinaturas conferidos estaticamente contra os espelhos
   (incl. o padrão `inTx`/`set_config` idêntico ao da P3.19 e o
   recurso `/db/migration/core/V10_5__...sql` no caminho certo do
   classpath); a prova é a CI total + o ensaio do roteiro no PC do
   dono, como o relatório do autor já declara.

## Padrões do projeto — conferência

- **JSON só via `Json` do core:** ✓ (grep em todo o pacote; CSV/XML
  são renderizadores próprios declarados, não JSON disfarçado).
- **Nenhum dado inventado / ausência = erro explícito:** ✓
  (dicionário sem metadado recusa; certificado sem expirações recusa
  — e, com a C4, sem manifesto também; PUBLIC_SECTOR sem fim de
  consulta recusa; feriado ausente segue a semântica documentada do
  motor da P1.36, cuja fonte é o cadastro do tenant).
- **Relógio:** ✓ serviço só usa `OffsetDateTime.now(clock)` injetado;
  regras puras recebem `now` explícito; nenhum `now()` solto.
- **Testes com datas calculadas à mão, não circulares:** ✓ nas datas
  (literais conferidos nesta revisão); exceções registradas no item 8
  dos Defeitos.
- **Nenhuma asserção afrouxada:** ✓ entrega só adiciona arquivos; as
  correções desta revisão adicionam/endurecem testes.
- **SQL da V10_5 × código:** ✓ todas as colunas/tabelas usadas pelo
  serviço existem na DDL com os mesmos nomes; CHECKs de `level` e de
  formato batem com os enums; teto de 120 dias também no banco;
  grants no padrão V9/V10_1, sem DELETE (a execução não apaga linhas
  das tabelas `platform`).

**Resumo do veredito:** LIBERA COM CORREÇÕES APLICADAS (C1–C7).
Pendências que o pai/dono deve conhecer antes do push: confirmação
da âncora dos tetos no Setor Público (item 1) e da antecedência de
3 dias do alerta (item 2); prova final na CI total + ensaio do
roteiro, incluindo a primeira aplicação da V10_5 (item 3).
