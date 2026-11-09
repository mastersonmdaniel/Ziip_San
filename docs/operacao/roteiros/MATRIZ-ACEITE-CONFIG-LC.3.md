# LC.3 — Matriz aceite x configuracao

Produtor: sessao `ca6d61bf-f7f3-42dd-a535-196ff96c1069` (papel: Produtor) — bloco de conclusao do ZiipSan, 09/11/2026.
Fonte unica do aceite: `~/workspace/ziipsan-retomada/fontes/docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json` (registo integral da LC.3 lido antes da producao).
Regras do bloco: sem GitHub (parada dura HTTP 429), sem AWS (sem conta neste bloco), nenhum dado inventado, nenhum segredo literal; valores sem fonte = `[A DEFINIR]`/`[SEM FONTE]` + pendencia nomeada.

| # | Exigencia (aceite LC.3) | Recurso/config na entrega | Estado local |
|---|---|---|---|
| 1 | RDS PostgreSQL 17 | `aws_db_instance.primary` engine=postgres, engine_version="17" | coberto |
| 2 | Multi-AZ | `multi_az = true` | coberto |
| 3 | Replica de leitura | `aws_db_instance.read_replica` | coberto |
| 4 | PITR RPO<=15min / RTO<=4h medidos | backups automaticos (`backup_retention_period`) + `enable_continuous_backup=true` no plano; medicao = roteiro LC3-T1 | config coberta; medicao exige AWS |
| 5 | Cofre isolado outra conta, mesma regiao, Vault Lock conformidade + expiracao | `vault.tf` (conta de destino): `aws_backup_vault_lock_configuration` sem `changeable_for_days`; copia via `copy_action` p/ ARN na mesma regiao; `lifecycle.delete_after` | coberto (valores de retencao [A DEFINIR] P-LC3-04; conta [A DEFINIR] P-LC3-03) |
| 6 | Recusa de exclusao/alteracao no cofre c/ admin da producao | desenho do item 5; prova LC3-T3 | config coberta; prova exige AWS |
| 7 | Nada fora de sa-east-1 (RNF-44) | validacao da variavel `aws_region` + nenhum recurso de replicacao inter-regiao no modulo | coberto |
| 8 | PgBouncer transaction | `config/pgbouncer.ini` pool_mode=transaction; output `pgbouncer_pool_mode` | config coberta; hospedagem [A DEFINIR] P-LC3-05 |
| 9 | RLS no banco restaurado | roteiro LC3-T5 (RLS e da aplicacao/P0.x, nao do IaC) | roteiro coberto; prova exige AWS |
| 10 | Restauracao seletiva + desfazer P3.33 sobre o PITR | roteiro LC3-T6/LC3-T7 | roteiro coberto; prova exige AWS |
| 11 | Risco residual por escrito p/ contrato e Setor Publico | `RISCO-RESIDUAL-DESASTRE-REGIONAL.md` | coberto (artefato entregue) |
| 12 | Classe/storage/retencoes numericas | variaveis obrigatorias marcadas [A DEFINIR] | [A DEFINIR] P-LC3-01/02/04 — sem fonte no aceite |
