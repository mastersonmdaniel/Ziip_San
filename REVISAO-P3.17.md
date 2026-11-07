# Revisão cruzada — P3.17 (Acesso assistido com consentimento e acesso de emergência)

Revisor: o próprio agente executor, em passada de revisão separada da escrita
(nesta sessão, profundidade 2/2, **não é possível acionar um subagente
revisor** — limitação declarada, não escondida; a conferência abaixo foi
feita item a item contra o acceptance, relendo o código depois de pronto).
Material: spec P3.17 em `fontes/docs__plano-v3__redacao__tarefas-P3.json`,
pacote em `ziipsan-producao/p3.17/entrega/`, espelhos locais do mesmo HEAD
`2ba51f8` (core: `audit_log`/`V14`, `platform.audit_event`/`V9`, outbox
`V8`, chamados `V9_4`, papéis `P0.43`, incidente `P3.10`).
**GitHub: HTTP 429 na 2ª chamada — parada dura respeitada; nenhuma nova
chamada depois disso. Sem push, sem escrita no repo.**

## Veredito: APROVADO COM RESSALVAS

O núcleo exigido está entregue: portão que audita antes de decidir, as duas
trilhas da emergência, os dois prazos com relógio injetável e a migration
`V10_3` reservada. As ressalvas são as pendências externas já declaradas
(lookup do incidente da P3.10, job de varredura, fiação HTTP) mais as
interpretações registradas no relatório.

## 1. Acceptance × entrega, item a item

| # | Item | Estado | Evidência |
|---|---|---|---|
| 1 | core `V10_3` | ✅ | `V10_3__assisted_and_emergency_access.sql` (só essa migration; `V10_4` é da P3.18) |
| 2 | sem consentimento, acesso negado e auditado | ✅ | `AccessPolicy` (`consent-required`) + `AccessGuard.openAssisted` audita antes de lançar; teste `assistedDeniedWithoutConsentIsAudited` confere 403 + entrada `allowed=false` no `audit_log` |
| 3 | revogação encerra a sessão em até 1 min | ✅ | portão nega desde o instante da revogação (`consent-revoked`); `mustEndBy = revokedAt + 1 min` no record, na migration e no `revokeConsent` do serviço; contas à mão: revogação 10:05 → devido 10:06:00, não devido 10:05:59 (teste) |
| 4 | revelar documento e exportar bloqueados | ✅ | `reveal-document-blocked`/`export-blocked` antes de qualquer outra regra, nos dois modos; teste com consentimento vigente e dentro de sessão aberta |
| 5 | cada requisição no `audit_log` do tenant com o id do consentimento | ✅ | guard audita abertura e cada requisição; `detailsJson` (via `Json`) carrega `consentId`; teste confere as 3+1 entradas com o mesmo `consentId` |
| 6 | emergência: sem incidente declarado / sem `PLATFORM_OWNER` / sem MFA no momento → negado e auditado | ✅ | códigos `incident-not-found`/`incident-not-declared`/`incident-tenant-mismatch`, `platform-owner-required`, `mfa-required`/`mfa-not-current`; negação auditada nos DOIS lugares (teste) |
| 7 | qualquer escrita em emergência recebe 403 | ✅ | `emergency-read-only` na abertura e em cada requisição; `AccessDeniedException.status() == 403` conferido no teste |
| 8 | aviso ao tenant pela outbox em até 24 h (teste com relógio) | ✅ com ressalva de fiação | `noticeDueAt = startedAt + 24 h` imposto no construtor (CHECK de código + teste: 07/11 10:00 → 08/11 10:00; vencido só após o instante exato); o serviço grava a linha na outbox na abertura. Ressalva: quem consome a outbox e entrega o aviso é o worker da P1.9 — a entrega final ao tenant não é desta tarefa |
| 9 | cada requisição no `audit_log` do tenant e em `audit_event` com o id do incidente | ✅ | guard da emergência grava o MESMO `AccessAuditEntry` nas duas portas, com `incidentId`; teste conta 3 entradas em cada trilha |
| 10 | sessão registrada no registro do incidente | ✅ com ressalva declarada | porta `IncidentSessionRegistry` chamada na abertura + sessão persistida com `incident_id` + `sessionsByIncident(...)`. Ressalva: a P3.10 não confirmou tabela do registro, logo não há FK nem escrita numa tabela de incidente inventada — ver relatório |

## 2. Coerência interna

- **Imports/assinaturas**: relidos; saldo de chaves/parênteses = 0 em todos
  os `.java` (conferência estática; os desvios de parêntese são notação de
  intervalo em comentários). `UuidV7` existe no core (`core.audit`) e é o
  gerador do `audit_log` desde a P1.7 — o INSERT direto o usa.
- **`Json` do core**: todo JSON da entrega (metadata do `audit_log`,
  details do `audit_event`, payload da outbox, `toJson`) sai de
  `Json.write(...)`; nenhum `Map.toString()` nem JSON montado à mão —
  inclusive onde o precedente antigo (`SupportSlaService.audit`) montava
  string na mão, esta entrega NÃO o copiou.
- **Relógio injetável**: guard e serviço recebem `Clock`; os testes usam
  relógio mutável de teste e `Instant.parse` fixos — nada dorme.
- **Contas refeitas**: 07/11/2026 é sábado (conferido com `date -d`);
  +24 h de 07/11 10:00Z = 08/11 10:00Z ✔; revogação 10:05 + 1 min = 10:06 ✔;
  vigência do consentimento `[10:00, 12:00)` — às 12:00 exato já expirou ✔.
- **Defeito encontrado e corrigido nesta revisão**: os métodos
  `authorize*Request` do guard resolviam o tenant da auditoria a partir do
  consentimento/incidente quando não havia sessão, e quebravam com NPE se
  AMBOS fossem nulos. Corrigido: pré-condição explícita
  (`IllegalArgumentException` nomeando o que falta) antes de auditar —
  ausência vira erro explícito, nunca trilha silenciosamente perdida.
- **Testes NÃO executados** (sem Java/Maven nesta VM): a aprovação é
  estática + emulação das contas; a prova é a CI total no PC do dono.

## 3. Regras invioláveis

- **Nenhuma asserção afrouxada**: testes todos novos; nenhum arquivo
  existente foi tocado (a entrega só adiciona arquivos).
- **Nenhum dado inventado**: sem teto/prazo além dos do aceite (1 min e
  24 h vêm do acceptance); "MFA no momento" foi interpretado como o
  instante da abertura medido pelo relógio injetado, SEM janela de
  tolerância inventada — interpretação registrada no relatório;
  `incident_id` sem FK porque a tabela do registro (P3.10) não foi
  confirmada — inventá-la seria criar segunda fonte.
- **Migration só a reservada**: `V10_3` e nenhuma outra.
