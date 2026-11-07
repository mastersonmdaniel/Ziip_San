# Restauração mensal automatizada (P4.7)

Rotina que prova, uma vez por mês, que o backup local da P0.28
(`scripts/local-backup.sh`) **restaura de verdade**: ela restaura o backup mais
recente numa base **isolada**, verifica o resultado, grava um relatório mensal
e alerta em caso de falha. Origem: ADR-0012 (metas não funcionais como
critério de aceite). Dependência: P0.28 (backup e PITR locais), já entregue.

## Como roda

```bash
scripts/monthly-restore.sh [--force] [--keep] [--dry-run] [diretorio-do-backup]
```

1. **Origem.** Sem argumento, usa `$ZIIPSAN_BACKUP_DIR` ou o
   `ziipsan-backup-*` mais novo em `$ZIIPSAN_BACKUP_ROOT` (padrão `/var/tmp`,
   que é o destino padrão do `local-backup.sh`). Exige `ziipsan.dump`
   presente e não vazio; registra tamanho, SHA-256 e o LSN do WAL
   (`wal-lsn.txt`, quando existe).
2. **Destino isolado — nunca a base de trabalho.** Sobe um PostgreSQL
   descartável próprio, na mesma imagem do compose
   (`postgis/postgis:15-3.4`, com a restrição honesta de imagem registrada em
   `docs/operacao/local-env.md`):

   | Item | Restauração mensal (P4.7) | Não confundir com |
   |---|---|---|
   | Container | `ziipsan-restore-mensal` | `ziipsan-postgres-primary` (trabalho) |
   | Porta | `127.0.0.1:55435` | 5432 (trabalho), 55433 (integração, `lib.sh`), 55434 (roteiro PITR da P0.28) |
   | Base | `ziipsan_restaurado_mensal` | `ziipsan` |
   | Senha | efêmera, gerada na execução, nunca gravada | senhas do `.env` |

   O container é removido ao fim (com `--keep`, fica no ar para diagnóstico e
   a rotina avisa o comando de remoção). Uma execução abortada não bloqueia a
   próxima: o container descartável é removido no início.
3. **Restauração.** `pg_restore --exit-on-error` do dump na base isolada.
4. **Verificações** (qualquer falha reprova o mês):
   - restauração concluída sem erro;
   - contagem de tabelas (fora `pg_catalog`/`information_schema`) maior que
     zero — restauração vazia não é sucesso;
   - contagem de linhas (`count(*)`), somada tabela a tabela, **medida no
     banco restaurado** — não há comparação contra um baseline: o dump da
     P0.28 não traz contagens e o `local-backup.sh` não grava checksum
     esperado. O portão de integridade é o `pg_restore --exit-on-error`
     mais tabelas > 0; a contagem reprova se a consulta falhar e serve
     como registro conferível do conteúdo restaurado;
   - checksum SHA-256 do dump, calculado na execução e registrado no
     relatório como impressão digital do arquivo restaurado (sem valor
     esperado da P0.28 para conferir contra);
   - presença de políticas RLS no restaurado (tabelas com RLS habilitada e
     número de políticas), como conferência de conteúdo.
5. **Relatório mensal** em `docs/operacao/restauracoes-mensais/<AAAA-MM>.md`
   (pasta versionável): resultado, duração total e por fase, origem, destino
   e verificações.

**Idempotência:** se o relatório do mês já está `SUCESSO`, a rotina sai com
`0` sem restaurar de novo; `--force` executa outra vez e regrava o relatório.

### O que esta rotina NÃO faz

- **Teste de isolamento RLS: não executado aqui.** A instância descartável
  sobe sem as roles de aplicação do init (`infra/postgres/init`), então não
  há como conectar como `ziipsan_app` e provar o isolamento. Esse teste é o
  do ensaio da P0.28/GL.3 (`scripts/local-pitr.sh`: sem contexto, zero
  linhas; com o contexto do tenant, só as dele). O relatório mensal registra
  isso por escrito e só afirma a **presença** das políticas no restaurado.
- Não restaura PITR/WAL: o objeto da rotina mensal é o dump lógico da P0.28.
  O ensaio de PITR continua sendo o roteiro `scripts/local-pitr.sh` (GL.3).
- Não encosta no banco de trabalho, no de integração nem em nenhum recurso
  na AWS.

## Tempos e referências (ADR-0012)

RPO de 15 min e RTO de 4 h são **referência** no ambiente local, **sem meta**
antes do lançamento (ajuste do ADR-0012 de 12/10/2026): a duração é medida e
registrada no relatório, mas não reprova o mês. A meta vale na LC.3/LC.12.

## Falha e alerta

O projeto **não tem** mecanismo de e-mail/webhook para scripts de operação
(conferido nos scripts existentes — no `ci.sh`, aviso é saída + código de
saída). Inventar uma integração seria entregar algo que não existe. O alerta
da P4.7 é, portanto:

1. **código de saída ≠ 0** (é o sinal que cron/systemd/Agendador de Tarefas
   enxergam; no systemd a unidade fica `failed`);
2. linha `ALERTA P4.7: ...` no **stderr**;
3. arquivo `docs/operacao/restauracoes-mensais/ALERTA-RESTAURACAO-MENSAL.md`
   com data, fase e erro — apagado pela próxima execução com sucesso;
4. relatório do mês gravado com `Resultado: FALHA` e a fase que falhou.

| Código | Significado |
|---|---|
| 0 | sucesso (ou mês já restaurado com sucesso, sem `--force`) |
| 2 | pré-condição: backup não encontrado/inválido ou runtime ausente |
| 3 | restauração ou verificação falhou (relatório + alerta gravados) |
| 4 | modo produção (RDS) solicitado antes da LC.3: nada executado, sem alerta |

## Agendamento (uma vez por mês, sem serviço externo)

Artefatos em `scripts/agendamento/` — instale **um** deles:

- **cron (Linux):** `restauracao-mensal.cron` — dia 1, às 03:17, com log em
  `$HOME/ziipsan-restauracao-mensal.log`. Instalação no cabeçalho do arquivo.
- **systemd (Linux):** `ziipsan-restauracao-mensal.service` + `.timer`
  (`OnCalendar=monthly`, `Persistent=true`: se a máquina estava desligada no
  dia 1, roda na próxima subida). Instalação no cabeçalho do `.service`.
- **Windows (PC do dono, Git Bash):** o Agendador de Tarefas é o equivalente;
  crie a tarefa mensal com (ajuste os caminhos):

  ```
  schtasks /Create /SC MONTHLY /D 1 /TN "ZiipSan Restauracao Mensal" /ST 03:17 ^
    /TR "\"C:\Program Files\Git\bin\bash.exe\" -lc \"cd /g/Meu Drive/PROJETOS/Ziip SAN && scripts/monthly-restore.sh\""
  ```

  O caminho do fonte acima é um **exemplo** no padrão da cópia de trabalho do
  dono; confira o caminho real antes de criar a tarefa.

Pré-condição de qualquer agendamento: o backup da P0.28 precisa existir —
agende o `scripts/local-backup.sh` na rotina já existente do ambiente; a
restauração mensal consome o backup mais recente que encontrar e **falha com
alerta (saída 2)** se não houver nenhum, em vez de passar em silêncio.

## Teste da rotina

```bash
scripts/test-monthly-restore.sh
```

Teste de auto-verificação **sem container de verdade**: um runtime falso
(podman stub) entra no `PATH` e responde às chamadas da rotina com valores
conhecidos (7 tabelas, 10+20 linhas). São 22 verificações: dry-run, backup
ausente (saída 2 + alerta), caminho feliz (saída 0 + relatório com contagens
e checksum + alerta apagado), idempotência, falha de `pg_restore` (saída 3 +
alerta) e modo RDS (saída 4; `--dry-run` mostra o plano). O teste confere o
**comportamento da rotina**; a restauração real só é provada pela execução
mensal no ambiente local (e pelo ensaio GL.3).

## Produção: a mesma rotina sobre o RDS (a partir da LC.3)

A P4.7 define que, na produção, **a mesma rotina** roda sobre o backup do
RDS a partir da LC.3. Nesta entrega isso existe só como parâmetro e plano —
**nada roda na AWS**:

```bash
scripts/monthly-restore.sh --source rds --dry-run   # imprime o plano, sai 0
scripts/monthly-restore.sh --source rds             # recusa: sai 4, relatório NAO EXECUTADO
```

Plano (dry-run): `aws rds restore-db-instance-from-db-snapshot` (ou PITR)
para uma instância **isolada** `ziipsan-restore-mensal` em `sa-east-1`
(ADR-0011; nenhum backup fora da região, RNF-44), as **mesmas** verificações
e o **mesmo** relatório mensal, e a instância isolada apagada ao fim. A
implementação executável do modo RDS entra com a LC.3 (RDS, backup e PITR),
que é quando o RDS passa a existir.

## Variáveis

| Variável | Padrão | Para quê |
|---|---|---|
| `ZIIPSAN_BACKUP_DIR` | — | diretório do backup da P0.28 |
| `ZIIPSAN_BACKUP_ROOT` | `/var/tmp` | onde procurar o `ziipsan-backup-*` mais novo |
| `ZIIPSAN_RESTORE_REPORT_DIR` | `docs/operacao/restauracoes-mensais` | relatórios e arquivo de alerta |
| `ZIIPSAN_RESTORE_CONTAINER` | `ziipsan-restore-mensal` | nome do container descartável |
| `ZIIPSAN_RESTORE_PORT` | `55435` | porta local da base isolada |
| `ZIIPSAN_RESTORE_DB` | `ziipsan_restaurado_mensal` | nome da base isolada |
| `ZIIPSAN_RESTORE_IMAGE` | `postgis/postgis:15-3.4` | imagem do PostgreSQL descartável |
| `ZIIPSAN_CONTAINER_RUNTIME` | podman, senão docker | runtime de container |
| `ZIIPSAN_RESTORE_SOURCE` | `local` | `local` ou `rds` (produção, LC.3) |
| `ZIIPSAN_RDS_SNAPSHOT_ID` | — | snapshot do RDS (modo `rds`, LC.3) |
| `ZIIPSAN_RDS_SOURCE_INSTANCE` | — | instância de origem do PITR (modo `rds`, LC.3) |
