# Central Ziip completa (P3.15, ADR-0018/ADR-0025)

Abas próprias entregues (registro em `CentralCompleteTabs`, visões em
`CentralCompleteService` — as SETE, incluindo Infraestrutura e Radar):
Arrecadação, Pix, LGPD, Regulatório, Infraestrutura com SLO, Radar e
Suporte. A Central lê só agregados (ADR-0018): os instantâneos de
`CentralSnapshots` são montados pelo coletor a partir das fontes de
cada aba (P3.1 arrecadação, P3.2 Pix, P3.10 LGPD, P2.15 suporte) — a
fiação do coletor é da integração.

- **Regulatório** lê a biblioteca de normas da P1.34 (catálogo global
  da core V9_8, consulta `GET /api/v1/certification/standards`):
  situação (4 valores do CHECK), versões/edições, vigência futura,
  selos e perfil por agência. Projeção fiel da V9_8: `effective_from`/
  `effective_to` são ANULÁVEIS nas tabelas e a data que manda é a DA
  NORMA (`cert_standard.effective_from`, curada pela carga da P1.34,
  uma edição por norma; há norma em vigência futura SEM data, que
  permanece `VIGENCIA_FUTURA`). A situação exibida e a do filtro são
  derivadas pela regra única do core (`NormEffectivenessService`,
  P1.34): `VIGENCIA_FUTURA` + `effective_from <= hoje` → `VIGENTE`,
  sem condição de `effective_to`. Sem texto de norma, como na carga.
  - **Obrigatórias não desativáveis — mecanismo real:** NÃO existe
    coluna `mandatory` na V9_8/P1.34. A não-desativação deriva da
    CLASSE: norma `REGULATORY` é obrigatória porque
    `CertificationService.deactivate` (core) recusa desativar norma
    REGULATORY e o CHECK `chk_cert_activation_regulatory_never_inactive`
    da tenant V21_1 proíbe `REGULATORY` ficar `INACTIVE` no banco. No
    perfil por agência desta Central, tentar excluir uma REGULATORY é
    recusado do mesmo jeito: ela permanece no perfil e a tentativa
    fica sinalizada (`retainedMandatoryKeys`).
- **Infraestrutura com SLO** reaproveita `SloDefinition` e
  `BurnRateCalculator` da P3.11 (sem duplicar): burn rates calculados
  nas janelas curta e longa e tier derivado deles. A falha provocada
  NÃO cria regra nova: ela alimenta com medição simulada a regra
  EXISTENTE `central.collector.stale` da core V9_1
  (`platform.collector.last_run_age_s > 900`, catalogada pela P3.11
  com o runbook RB-02) — ver RB-09, que é o procedimento do exercício.
- **Kit de evidências**: manifesto canônico via `Json` do core,
  SHA-256 do manifesto como identidade do kit e registro na trilha
  (`CENTRAL_EVIDENCE_KIT_GENERATED`, hash + ator + instante); o hash
  da trilha é validado como SHA-256 hex de 64 dígitos, com o mesmo
  rigor dos itens.
- **Relatório mensal por contrato** (`MonthlyContractReport`):
  disponibilidade com meta de 99,9% no nível Setor Público medida a
  partir do lançamento (denominador exclui a manutenção planejada —
  nota explícita no relatório) e aderência/multa na semântica ÚNICA
  já entregue pela P2.15 (`SupportSlaService.monthlyReport`): a
  P3.15 não cria fórmula nova, apenas agrega a da P2.15 por
  severidade/contrato — aderência = resolvidos no prazo /
  (resolvidos no prazo + estourados), só contando resolvidos COM
  prazo; multa = soma por chamado estourado de
  mensalidade × `penalty_pct` (V10_2), cobrada mesmo quando a
  aderência da severidade está na meta.

## Fronteiras (remissões do aceite — não são escopo da P3.15)

- Aba **Integrações**: preenchida pela **P3.37** (que depende desta).
- Aba **Estoque**: escopo da **ED-58**.
- **Agenda de obrigações do tenant**: tela do tenant, escopo da
  **ED-98** (lê as regras do catálogo via esta Central); não é rota da
  Central — registrada em `CentralCompleteTabs.OBRIGACOES_TENANT` com
  rota nula de propósito.
