# Ligação alerta → runbook em produção — LC.7 (PLT-23)

Produtor: sessão `92031a19-4d6d-4174-92b7-164897d85503` (papel: Produtor).

Prática de resiliência da P3.11 (PLT-23), que o aceite da LC.7 manda
usar: **todo alerta tem `runbook_url` e responsável obrigatórios** —
a P3.11 impõe a recusa de regra sem runbook na aplicação
(`AlertRule`) e no catálogo versionado. Esta LC.7 mantém a mesma
ligação em produção, sem criar regra nova nem afrouxar a recusa.

## 1. Regras executáveis (catálogo da P3.11, vigente)

| rule_key | Sev. | Owner | Runbook (na P3.11) | Ação do plantão |
|---|---|---|---|---|
| `central.collector.stale` | CRITICAL | platform | `docs/operacao/runbooks/RB-02-fila-parada.md` | Página ao plantonista; conduzir pelo RB-02 |
| `platform.outbox.oldest_pending_age_s.high` | WARNING | platform | `docs/operacao/runbooks/RB-02-fila-parada.md` | Ticket; conduzir pelo RB-02 no dia útil |

Fonte: `docs/observability/alert-rules.json` da P3.11 (2 regras, ambas
sobre métricas reais do catálogo fechado da V9_1, ambas com
`runbook_url` + `owner`).

## 2. Runbooks sem regra executável (preparação, não instrumentação)

Estes runbooks da P3.11 existem e são exercitáveis, mas **não têm
alerta que dispare** — as regras foram retiradas do catálogo executável
pela própria P3.11 por falta de métrica real própria. Em produção,
valem como procedimento de resposta manual (incidente aberto por
pessoa, sonda ou error tracking), nunca como alerta prometido:

| Cenário | Runbook | Regra pendente (P3.11) | Motivo |
|---|---|---|---|
| API indisponível / burn alto | RB-01 | `api.availability.burn_fast` / `burn_slow` | derivação do SLI de `http.server.requests` não modelada |
| Perda do PSP | RB-03 | `psp.unavailable` | sem métrica real do PSP |
| SEFAZ indisponível | RB-04 | `sefaz.unavailable` | sem métrica real da SEFAZ |
| Certificado vencido | RB-05 | `certificate.expiring` | sem métrica real de validade |
| Réplica derrubada | RB-06 | `db.replica.down` | sem métrica real de réplica |

Quando a modelagem de cada métrica for decidida (pendência P-LC7-04,
herdada da P3.11), a regra volta ao catálogo **já ligada** ao runbook
acima — a ligação nasce junto, pela recusa da P3.11.

## 3. Regras de governo em produção

1. **Nenhum alerta novo entra em produção sem `runbook_url` e
   `owner`** — vale a recusa da P3.11 (`AlertRule`/catálogo); alerta
   sem runbook é defeito, não atalho.
2. **Alerta que dispara e não ajuda** é revisto na revisão trimestral
   de SLO (rito da P3.11) — ajustar limiar exige justificativa com
   dados, como a meta de SLO.
3. **Error tracking** (serviço `[A DEFINIR]`, P-LC7-02) notifica o
   plantão, mas não substitui regra de alerta: erro sustentado vira
   regra/runbook pelo caminho do §1–§2, não fica só no painel do
   serviço de erros.
4. **Sondas sintéticas** (LC.7) não são `alert_rule` da Central: elas
   alimentam a página de status (fato de sonda, P3.18). Sonda `DOWN`
   sustentada é tratada pelo plantão como incidente do componente
   (backoffice → runbook do componente `[A DEFINIR na ativação]`;
   portal → idem) — nenhum runbook novo é inventado aqui para elas.
