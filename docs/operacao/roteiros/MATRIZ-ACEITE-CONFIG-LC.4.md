# LC.4 — Matriz aceite x configuracao

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.4 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

| # | Exigencia (aceite LC.4) | Recurso/config na entrega | Estado local |
|---|---|---|---|
| 1 | TenantKeyProviderContract verde sobre o KMS | chaves por tenant (`aws_kms_key.tenant`) + roteiro LC4-T1 | config coberta; suite exige AWS |
| 2 | DEK por tenant gerada pelo KMS | uma KMS key por tenant (base do GenerateDataKey por tenant) | coberto |
| 3 | Rotacao | `enable_key_rotation=true` no PLATFORM (plataforma e tenants) | coberto |
| 4 | Backup da KEK: forma registada | `REGISTRO-BACKUP-KEK.md` + output `kek_backup_procedure_ref` | coberto (registo) |
| 5 | Segredos de producao no Secrets Manager | `aws_secretsmanager_secret.prod` criados SEM valor (sem `secret_string`) | coberto (nomes [A DEFINIR] P-LC4-03) |
| 6 | Sem reaproveitar segredo/chave do cofre local (teste) | nenhuma referencia a cofre local no modulo; roteiro LC4-T3 | config coberta; prova exige AWS |
| 7 | crypto.key_mode PLATFORM padrao / IMPORTED / EXTERNAL sob contrato | variavel validada + `origin` por modo + aceite obrigatorio (modelos) | coberto |
| 8 | Limitacoes IMPORTED/EXTERNAL registadas (sem rotacao automatica; EXTERNAL sem multirregiao nem material importado) | `enable_key_rotation=false` fora do PLATFORM + texto nos modelos de aceite | coberto |
| 9 | Revogacao -> dado ilegivel so daquele tenant (RNF-43) | chave separada por tenant; roteiro LC4-T4 | config coberta; prova exige AWS |
