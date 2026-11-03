#!/bin/bash
# P0.28: ensaio de PITR local (point-in-time recovery) — ROTEIRO, nao automacao total.
#
#   scripts/local-pitr.sh <backup-dir> <alvo: 'YYYY-MM-DD HH:MM:SS' ou LSN>
#
# Passos (o operador executa e registra os tempos; o script nao apaga o banco atual):
#   1. parar a api (nunca restaurar por cima de um banco em uso);
#   2. subir um PostgreSQL DESCARTAVEL em 127.0.0.1:55434 com o volume de WAL
#      arquivado montado em modo leitura;
#   3. restaurar o pg_dump do backup e aplicar o WAL ate o alvo
#      (recovery_target_time / recovery_target_lsn + recovery_target_action=promote);
#   4. rodar o teste de RLS no banco restaurado: como ziipsan_app, sem contexto de
#      tenant, nenhuma linha de tenant pode aparecer; com o contexto do tenant A,
#      so as linhas de A (ver TenantRlsIsolationTest);
#   5. registrar: tempo total, LSN/tempo alvo, resultado do teste de RLS.
#
# Por que roteiro e nao `pg_basebackup` automatico aqui: o compose atual nao faz
# base backup continuo (so pg_dump + WAL arquivado); o PITR completo com base backup
# exige o volume de dados + o WAL desde o ultimo base backup, e restaurar por cima
# do banco de desenvolvimento seria destrutivo. O ensaio, quando executado, usa um
# banco descartavel — nunca o de desenvolvimento. Sem runtime de container nesta VM,
# o ensaio fica como pendencia registrada, nao como numero inventado.
set -euo pipefail
BACKUP_DIR="${1:?uso: local-pitr.sh <backup-dir> <alvo>}"
TARGET="${2:?uso: local-pitr.sh <backup-dir> <alvo>}"
echo "PITR (roteiro): backup=$BACKUP_DIR alvo=$TARGET"
echo "1) pare a api:   scripts/local-env.sh down (ou pare so o container ziipsan-api)"
echo "2) suba um PostgreSQL descartavel em 127.0.0.1:55434 com o WAL arquivado montado"
echo "3) restaure o dump e aplique o WAL ate '$TARGET' (recovery_target_action=promote)"
echo "4) teste de RLS no banco restaurado (app sem contexto -> zero linhas; tenant A -> so A)"
echo "5) registre tempo total, alvo e resultado do teste de RLS no relatorio do ensaio"
echo "Este script NAO executa a restauracao sozinho: ele documenta o roteiro para que"
echo "nenhum passo destrutivo rode por acidente sobre o banco de desenvolvimento."
