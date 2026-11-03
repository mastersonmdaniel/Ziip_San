# Ambiente local completo (P0.27, P0.29, P0.28)

Ambiente local = Docker Compose (ou Podman Compose) com PostgreSQL, Keycloak,
emulador de S3, cofre local e PgBouncer. Nenhum recurso na AWS. Todas as portas
publicadas ficam em `127.0.0.1` (conferido por `scripts/check-local-profiles.sh` e
por `scripts/check-compose-ports.py`). Credenciais so no `.env`, fora do repo.

## Restricao honesta de imagem (leia antes de subir)

`postgis/postgis:17-3.7` NAO EXISTE no Docker Hub (verificado; so RCs de 3.7 para
PG18/19). Nenhuma tag foi inventada aqui e `17-3.6` nao e usado como substituto.
Por isso o compose e o `reset-it-db.sh` continuam em `postgis/postgis:15-3.4`, e
tudo nesta frente que dependeria do PG17 no compose funciona sobre o banco
disponivel, com a dependencia registrada aqui: **a verificacao PG17 + PostGIS 3.7
continua sendo a do host via PGDG (ci-linux.sh), nao a do compose**. Quando a tag
`17-3.7` existir, trocar a imagem do compose e apagar este paragrafo (e o
comentario equivalente no compose e no CI).

## Perfis (`scripts/local-env.sh`)

| Perfil | O que sobe | Uso |
|---|---|---|
| `dev` | PostgreSQL + Keycloak + PgBouncer + cofre (Vault) + S3 (SeaweedFS) | desenvolvimento |
| `homolog` | dev + job `migrate` + `api` (mesma imagem da P0.5) | homologacao local |
| `demo` | igual a homolog (perfil de demonstracao local, P3.23) | demonstracao local |

Cada perfil e recriavel do zero por `scripts/local-env.sh <perfil> --reset`
(apaga os volumes locais) e imprime o tempo decorrido. `local-env.sh down` para
tudo sem apagar volumes.

- Implantacao: a `api` e o `migrate` usam a MESMA imagem da P0.5
  (`scripts/build-image.sh` + `ZIIPSAN_APP_IMAGE` no `.env`). Sem imagem previa,
  o script sobe so a infra e avisa — nao inventa tag.
- Rollback da imagem: `scripts/local-env.sh rollback` volta a `api` para
  `ZIIPSAN_APP_IMAGE_PREVIOUS` e mede o tempo. O rollback exercitado ao vivo e
  registrado no relatorio da frente (nao ha numero aqui ate o ensaio rodar).
- `ziipsan.fiscal.production-allowed` e `false` em todos os perfis locais
  (`ZIIPSAN_FISCAL_PRODUCTION_ALLOWED=false` no compose, conferido pelo
  `check-local-profiles.sh`). Hoje nenhuma emissao fiscal existe no codigo
  (modulo da P0.32); quando existir, ele deve ler esta variavel e recusar
  `tpAmb=1` com ela falsa. Nunca trocar para `true` num perfil local.

## Keycloak em modo de producao local (P0.29)

- Comando: `kc.sh build && kc.sh start --optimized --import-realm` (o build roda
  na subida, no mesmo container, para nao exigir imagem customizada nem quebrar
  o `dev-up.sh`, que copia o compose sozinho).
- TLS local: `https://localhost:8443` com certificado autoassinado gerado por
  `scripts/local-tls.sh` (so localhost, 365 dias, pasta ignorada pelo git).
  `http://localhost:8080` continua como emissor (`iss`) validado pelo backend —
  trocar o emissor exigiria confiar no certificado autoassinado na JVM do
  backend, o que nao foi feito aqui.
- Realm: `sslRequired=all` (antes `external`). Fluxos de navegador exigem HTTPS;
  o backchannel interno (rede do compose, HTTP) e a excecao local documentada —
  em producao (LC.6) nao ha excecao.
- Duas replicas: perfil `ha` (`keycloak-2` em `127.0.0.1:8082`/`8445` — 8081 ja e do SeaweedFS), com
  `KC_CACHE=ispn` + `KC_CACHE_STACK=jdbc-ping` sobre o mesmo PostgreSQL.
- Ensaio do RNF-17 (derrubada sem perda de sessao): subir com `--profile ha`,
  autenticar um usuario na replica 1, derrubar a replica 1
  (`compose stop keycloak`), e confirmar que a replica 2 continua servindo o
  realm e que o refresh do token funciona. **Ensaio nao executado nesta frente
  (sem runtime de container na VM); registrar tempo e resultado quando rodar,
  nao copiar este paragrafo como prova.**

## Backup e PITR locais (P0.28)

- Arquivamento de WAL: o PostgreSQL do compose roda com `wal_level=replica`,
  `archive_mode=on` e `archive_command` para o volume `postgres-wal-archive`.
- Backup: `scripts/local-backup.sh <destino>` (pg_dump + LSN atual + contagem de
  segmentos WAL), com tempo medido.
- PITR: `scripts/local-pitr.sh <backup-dir> <alvo>` e o ROTEIRO do ensaio
  (banco descartavel em `127.0.0.1:55434`, nunca o banco de desenvolvimento):
  restaurar, aplicar o WAL ate o alvo, e rodar o teste de RLS no banco
  restaurado (sem contexto: zero linhas; com o contexto do tenant A: so A).
  Referencias: RPO 15 min, RTO 4 h — **sem meta na P0.28 (a meta vale na LC.3)**.
  Tempos medidos vao para o relatorio do ensaio; **o ensaio nao foi executado
  nesta frente (sem runtime de container nem PostgreSQL na VM no momento da
  entrega) e nenhum tempo e afirmado aqui.**

## Chaves por tenant e cofre local (P0.28)

- Core `V6` (`public.tenant_key`): uma linha por (tenant, versao), com DEKs
  cifradas pela KEK (envelope AES-256-GCM, `AesGcmEnvelope`). A KEK fica no
  cofre local (`SecretStore`, chave `kek/current`); nunca no banco.
- Provedor: `LocalVaultTenantKeyProvider` (plataforma). Cache so em memoria.
- Rotacao: cria versao nova ATIVA; a anterior fica ROTATED. **Limitacao honesta:
  a rotacao nao re-cifra os campos antigos** (o `FieldCipher` usa so a chave
  corrente); rotacao sem re-cifra e um evento a planejar, nao uma operacao
  invisivel. O job de re-cifra e pendencia futura, nao inventada aqui.
- Crypto-shredding: `destroy()` apaga o material de todas as versoes e limpa o
  cache; depois dela, o provedor falha em vez de recriar chaves em silencio
  (testado em `LocalVaultTenantKeyProviderTest`).
- R1 sobre o cofre local: KEK no cofre + DEK por tenant guardada cifrada +
  contrato do provedor verde sobre o cofre local. RDS e KMS sao LC.3/LC.4.

## Contexto de tenant local a transacao e PgBouncer (P0.28)

- `MultiTenantConnectionProvider` devolve um proxy: dentro de uma transacao
  (`setAutoCommit(false)`), o primeiro statement aplica o contexto com
  `set_config(..., true)` (local); ao fim da transacao o PostgreSQL o descarta.
- Excecao documentada: leitura do Hibernate SEM transacao explicita roda em
  autocommit, e um `SET LOCAL` morreria no proprio statement — para esse
  caminho, e so para ele, o proxy aplica em escopo de sessao e limpa na
  devolucao, como antes da P0.28. O caminho transacional (Spring
  `@Transactional`, o unico que o PgBouncer em modo transaction usa) e sempre
  local. Testes de unidade com mocks: `TenantConnectionProviderLocalTest`.
- PgBouncer: `infra/pgbouncer` (Debian + pacote do apt; nenhuma tag de imagem
  de PgBouncer inventada), `pool_mode=transaction`,
  `server_reset_query=DISCARD ALL`, userlist gerada na subida a partir do
  `.env` (nunca versionada). Porta `127.0.0.1:6432`.
- Ensaio de vazamento (pendente de ensaio ao vivo): duas transacoes de tenants
  diferentes na mesma conexao fisica (pool de 1 conexao, via PgBouncer) nao
  podem ver o contexto uma da outra — o teste de integracao existente
  (`TenantConnectionProviderTest`, pool de 1 conexao) cobre o equivalente sem
  PgBouncer; o ensaio via PgBouncer segue o mesmo roteiro e deve ser registrado
  com resultado quando executado.

## Interfaces de infra e implementacoes locais (P0.45)

| Interface | Implementacao local | Producao futura (mesma suite) |
|---|---|---|
| `ObjectStorage` | `LocalFileObjectStorage` (arquivos) + `S3ObjectStorage` (HTTP + SigV4, sem SDK) contra o SeaweedFS do perfil `storage` | S3 gerenciado (LC.1) |
| `SecretStore` | `LocalFileSecretStore` (arquivos 0600, fora do repo) + servidor Vault do perfil `vault` | Vault/KMS (LC.4) |
| `MailSender` | `RecordingMailSender` (grava `.eml`, nunca envia) | envio real (LC.1) |
| `MessageQueue` | so interface + suite de contrato; duplo em memoria SO em teste | outbox no PostgreSQL + worker (P1.9) |
| `TenantKeyProvider` | `LocalVaultTenantKeyProvider` | KMS (LC.4) |

Testes de fronteira (`ProviderBoundaryTest`): nenhum fonte de dominio importa
SDK de provedor de nuvem. Teste de varredura (`SyntheticDataScanTest`): nenhuma
migration ou init do banco local traz documento formatado sem a marca
`SINTETICO`. O roteiro de anonimizacao do piloto esta em
`docs/seguranca/roteiro-anonimizacao-piloto.md`.
