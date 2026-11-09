# LC.5 — Matriz aceite x configuracao

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.5 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

| # | Exigencia (aceite LC.5) | Recurso/config na entrega | Estado local |
|---|---|---|---|
| 1 | Suite de contrato P0.45 verde contra o S3 | bucket + roteiro LC5-T1 | config coberta; suite exige AWS |
| 2 | Apagar/sobrescrever XML retido recusado pelo Object Lock | `object_lock_enabled=true` na criacao + `aws_s3_bucket_object_lock_configuration` COMPLIANCE + versionamento Enabled + negacao explicita de delete na policy | coberto (retencao [A DEFINIR] P-LC5-02) |
| 3 | Prefixo e chave por tenant | prefixo `tenant/<tenant_id>/` + condicao `s3:prefix` com tag do chamante na policy | coberto |
| 4 | Nome do bucket | variavel obrigatoria | [A DEFINIR] P-LC5-01 — convencao da LC.2 |
