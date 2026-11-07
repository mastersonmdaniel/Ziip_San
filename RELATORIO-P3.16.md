# Relatório — P3.16 Ensaio de saída de tenant sintético

Repo `mastersonmdaniel/Ziip_San`, branch `main`. HEAD de referência
`2ba51f8`; HEAD ao vivo conferido nesta tentativa: `0fbcd22` (pós-Onda
1). Entrega local conferida, **sem push e sem escrita no GitHub**.

## Desenho

- Módulo `shared/ziipsan-core` (o mesmo do console da P1.6 e dos
  chamados da P3.19), pacote novo `br.com.ziipsan.core.exit`:
  regras puras (`TenantExitCalculator`), renderizadores/manifesto/
  dicionário/certificado puros (`TenantExitExport`) e o serviço com
  banco (`TenantExitService`, construtor `(DataSource, tenantId,
  Clock)` e `inTx` com `set_config`, padrão do `TicketService`/
  `TicketRetentionService`).
- **Única migration: core `V10_5`** — a reservada à P3.16 no mapa do
  plano v3.0 §3 ("período de consulta somente leitura pós-contrato no
  ciclo de vida do tenant (início, fim, usuários), antes da
  carência"). Nenhum outro número foi inventado. A `V10_5` também
  amplia o CHECK de formato da `tenant_export` (V9: só `JSON`) para
  `JSON/CSV/XML`, localizando a constraint pelo catálogo.
- O portão arquivístico é **reutilizado da P3.19**:
  `executeElimination` chama `TicketRetentionService.requirePurgeAllowed`
  (RAS-18) antes de qualquer escrita — não há segunda implementação.
- Crypto-shredding pela porta `TenantExitService.KeyDestruction`
  (implementação de produção = provedor de chaves da P0.28, core
  `V6`/`tenant_key`): sem confirmação da destruição, a execução
  recusa e nada é marcado eliminado.

## Cobertura do acceptance, item a item

| Item do aceite | Estado | Como |
|---|---|---|
| Exportação em até 15 dias (10 dias úteis no Setor Público) | **Atendido (regra + teste)** | `exportDueDate`: STANDARD +15 corridos; PUBLIC_SECTOR 10 úteis pelo `DeadlineCalculator` da P1.36 com feriados do tenant. Testes calculados à mão: 2027-01-15 / 2027-01-14 / com feriado 2027-01-15. |
| Formatos CSV, JSON ou XML, com manifesto, hash e dicionário gerado do schema | **Atendido (regra + teste)** | `TenantExitExport.render` (JSON só via `Json` do core; CSV RFC 4180; XML escapado), `manifest`/`manifestHash` (SHA-256 por tabela e geral), `dataDictionary` dos metadados de `information_schema` (vazio = erro explícito). A leitura física das linhas/metadados do schema do tenant é do chamador/CI. |
| Consulta somente leitura no Setor Público (V10_5), pelo prazo do contrato, máx. 120 dias, antes da carência | **Atendido** | Tabelas `tenant_readonly_period`/`tenant_readonly_user` (teto de 120d também em CHECK); duração = prazo do contrato com teto; testes 305d→120 e 60d→60. |
| Escrita recusada na consulta (teste) | **Atendido** | `requireWriteAllowed` + `TenantExitService.requireWriteAllowedNow`; teste `writeRefusedDuringReadonly`. A ligação deste portão a cada rota de escrita do backoffice é fiação da integração (declarada). |
| Carência contada do fim da consulta (Setor Público) | **Atendido (teste)** | `graceStartsAt` por nível; STANDARD do fim do contrato. |
| Carência de 30 dias, com download e contestação registradas | **Atendido** | `tenant_exit_download` + `recordDownload`; contestação em `tenant_exit_contestation` + `registerContestation` (só na carência). |
| Eliminação agendada só por PLATFORM_OWNER com MFA, cancelável na carência | **Atendido (teste)** | `requireSchedulingAllowed` antes da transação; CHECK no banco exige `scheduled_mfa`; `cancelElimination` só na carência. |
| Execução antes do fim da carência recusada (teste) | **Atendido** | `requireExecutionAllowed` (`grace_not_elapsed`), inclusive no instante exato do fim. |
| Contestação suspende; resposta em até 15 dias; alerta na Central antes do vencimento (teste com relógio) | **Atendido (teste)** | Resposta = registrada + 15d; `Holds.contestationOpen` suspende; `contestationAlertDue`/`contestationsDueForAlert` com `Clock` injetado e `alert_sent_at`. Antecedência de 3 dias = convenção declarada (aceite não numera). A publicação do alerta na Central (V9_1) é do job da plataforma. |
| Ordem judicial suspende enquanto vigorar (teste) | **Atendido** | `tenant_exit_court_order` (`lifted_at IS NULL` = vigente); `Holds.courtOrderActive`. |
| Crypto-shredding | **Atendido como mecanismo; prova no ensaio** | Porta `KeyDestruction` + `key_destroyed_at` obrigatório para `ELIMINATED` (CHECK). A ilegibilidade do dado após destruir a DEK é o passo 10 do roteiro (CI/ambiente do dono). |
| Eliminação concluída e certificado em até 30 dias após carência/última resposta, máx. 75 do fim do contrato (60 sem contestação) | **Atendido (regra + teste)** | `certificateDueAt` = min(marco + 30d, âncora + 60/75). Recálculo à mão fecha exato (30+30=60; 30+15+30=75). **Interpretação declarada:** no Setor Público a âncora é o fim da consulta (de onde a carência conta) — com âncora no fim do contrato, o teto seria inalcançável por construção havendo consulta. |
| Certificado com hashes, expiração de backups e de cópias imutáveis e a exceção dos chamados | **Atendido (teste)** | `TenantExitExport.certificate`: hash da exportação + hash por arquivo do manifesto, `backup_expires_at`, `immutable_copy_expires_at` (ausência recusa) e exceção `support_tickets` com `purge_at` = vigência + 5 anos via `TicketRetentionService.purgeDate` (2031-12-31 no exemplo). |
| Natureza pública: eliminação usa temporalidade/listagem da P3.19 (RAS-18) e é recusada sem elas (teste) | **Atendido** | Reuso direto de `requirePurgeAllowed`/`ArchivalApproval` da P3.19 (testes por valor de `legal_nature` já vivem na P3.19); a execução chama o portão antes de qualquer escrita. |
| Ensaio com tenant sintético | **Atendido como roteiro executável** | `docs/operacao/ROTEIRO-ENSAIO-SAIDA-TENANT.md`: 10 passos (incl. as recusas esperadas), massa sintética nos dois níveis/naturezas e template de Registro do Ensaio. Só roda na CI/ambiente do dono. |
| Restauração seletiva e cópia lógica diária (P3.33) | **Fronteira respeitada** | Não implementado; declarado no roteiro. |

**Resumo: 14 itens atendidos (regra/mecanismo + teste puro), 0
parciais de regra, 2 dependências de ambiente declaradas abaixo.**

## Arquivos da entrega (`entrega/`, caminhos do repo)

- `shared/ziipsan-core/src/main/resources/db/migration/core/V10_5__tenant_exit_readonly_period.sql`
- `shared/ziipsan-core/src/main/java/br/com/ziipsan/core/exit/TenantExitCalculator.java`
- `shared/ziipsan-core/src/main/java/br/com/ziipsan/core/exit/TenantExitExport.java`
- `shared/ziipsan-core/src/main/java/br/com/ziipsan/core/exit/TenantExitService.java`
- `shared/ziipsan-core/src/test/java/br/com/ziipsan/core/exit/TenantExitCalculatorTest.java`
- `shared/ziipsan-core/src/test/java/br/com/ziipsan/core/exit/TenantExitExportTest.java`
- `shared/ziipsan-core/src/test/java/br/com/ziipsan/core/exit/TenantExitMigrationConsistencyTest.java`
- `docs/operacao/ROTEIRO-ENSAIO-SAIDA-TENANT.md`
- Revisão cruzada: `~/workspace/ziipsan-producao/p3.16/REVISAO-P3.16.md`

## Pendências honestas

1. **GitHub 429:** duas listagens ao vivo responderam (HEAD `0fbcd22`;
   `V10_5` inexistente no repo) e a terceira chamada falhou com HTTP
   429 — parada dura respeitada; o restante do inventário veio dos
   pacotes locais do mesmo HEAD (ver `espelho/README.md`).
2. **Sem compilação/CI nesta VM** (sem Java/Maven/Docker): testes
   puros não executados aqui; fluxos com banco, a V10_5 sobre o banco
   real e o ensaio completo só são provados na CI total no PC do dono.
3. **Revisão cruzada sem subagente independente:** esta frente está em
   profundidade 2/2 (sem spawn); a revisão foi uma segunda passada
   documentada (`REVISAO-P3.16.md`). Recomendado rotear revisão
   independente antes do push.
4. **Interpretações a confirmar pelo dono:** (a) âncora dos tetos de
   60/75 dias no Setor Público = fim da consulta; (b) antecedência do
   alerta da contestação = 3 dias (aceite não numera); (c) instantes
   do processo às 00:00 UTC da data do contrato (DATE da V10_1, como
   a P3.19).
5. **Fiação de integração (não é regra nova):** ligar
   `requireWriteAllowedNow` às rotas de escrita do backoffice,
   implementar `KeyDestruction` sobre o provedor da P0.28, montar a
   exportação física (linhas + `information_schema`) e o job que
   publica o alerta na Central — tudo exercitado pelo roteiro.
6. `tenant_lifecycle` não ganha status novo: a consulta vive em
   tabela própria ligada ao processo (leitura do mapa da V10_5);
   `DELETED` + hash do certificado são gravados na execução, como a
   V9 já prevê.
