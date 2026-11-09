# ED-98 — Agenda de obrigações regulatórias e de controle do tenant

Calendário de obrigações por tenant (GES-05) gerado de dois eixos: a
natureza jurídica do tenant e o perfil `reg.*` (`reg.obligations`,
classe d, definido no SQL pendente deste pacote, no padrão do
`reg.reports` da ED-97). As regras das obrigações são LIDAS do catálogo
de normas da P3.15 (`RegulatoryLibrary`, projeção do catálogo global da
P1.34): regra cuja norma não está no catálogo não vira item — é
devolvida como não resolvida, nunca inventada. O casamento
regra×catálogo é por token com **dobra de acentos e caixa** (correção
D4 da revisão: título acentuado realista casa o token; o risco
residual do casamento por token é a pendência P-ED98-06).

## Obrigações do aceite

Ciclo anual do SINISA; relatório anual de tarifa social; declaração
anual de quitação (fatura de maio — dia `[A DEFINIR]`); balanço de
perdas da NR 15; remessa bimestral ao e-Sfinge (IN TC-28/2021, só
naturezas de direito público jurisdicionadas ao TC); SISAGUA;
publicação de reajuste 30 dias antes da data-base do perfil; RREO
(bimestral) e RGF (quadrimestral) **do ente quando aplicável** —
natureza `ADMINISTRACAO_DIRETA_ENTE` **e** flag do perfil (correção D2
da revisão: a flag sozinha não impõe RREO/RGF a autarquia, empresa
pública ou concessionária privada). As periodicidades de NR 15,
SISAGUA e do item manual (RAS-17) não têm fonte no aceite: nascem
anuais **provisórias e marcadas `[A DEFINIR]`** na regra e no rótulo
do item (correção D6; P-ED98-01 ampliada). Cada item nasce com
responsável (sem responsável no perfil, a geração recusa), evidência
e situação.

## Ligações

- **Evidência (RAS-08):** `AgendaItem.linkSubmission` aceita somente um
  `SubmissionRecord` real da ED-97, do mesmo tenant, e grava o
  `evidenceRef()` da ED-97 sem reformatar. Item não manual só conclui
  com evidência ligada. Após a correção D1 da revisão, o vínculo
  confere também: o **período** do envio (igual ao do item), o
  **envio real** (só `SUBMITTED_BY_TENANT` — pacote apenas
  gerado/validado não é evidência de cumprimento) e a
  **correspondência de tipo** (agência+perfil do envio contra o tipo
  da obrigação) — a mesma evidência não quita dois itens distintos
  (no banco, índice único parcial sobre `evidence_ref`).
- **Lembretes (ED-07):** porta `ReminderPort` cujo pedido espelha o
  `TenantAlert` real da ED-07 (responsável, referência da origem,
  códigos de título/corpo). A fiação ao `TenantAlertService` exige o
  evento declarado no catálogo da P1.38/P3.36 — P-ED98-03.
- **Fiscalização (JOR-24):** agência, Tribunal de Contas ou órgão
  ambiental → ocorrência na P2.25 pela porta `OccurrencePort` (a P2.25
  não tem entrega local consumível; fiação = P-ED98-02), com plano de
  ação, prazo e lembrete nesta agenda.
- **Resíduos (RAS-17):** MTR, CDF e DMR são item MANUAL enquanto a
  ED-104 não for liberada: não aceitam evidência de envio e concluem
  com nota do responsável.
- **Alerta antecipado (P1.34):** norma em `VIGENCIA_FUTURA` com data de
  efeito na janela gera aviso ao responsável; norma sem data não alerta
  (regra do `NormEffectivenessService` da P1.34).
