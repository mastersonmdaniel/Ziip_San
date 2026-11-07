# GL.3 — Roteiro do ensaio de restauração local

> **Onde este roteiro roda:** no ambiente local do dono (PC dele, com Docker/Podman e PostgreSQL), sobre o backup local da **P0.28** e os mecanismos da **P3.33**. **Ele não roda na VM de desenvolvimento** (sem Docker/Postgres) e nenhum ensaio é dado como executado sem o Registro do Ensaio gerado pela execução (seção 5). O comportamento do orquestrador é provado sem Docker pelo harness `scripts/test-ensaio-restauracao-local.sh` (stubs, no padrão da P4.7).
>
> **Natureza:** isto é um **ensaio** — tempos, contagens e resultados são **registrados**, sem meta. A referência de RTO de 4 h (ADR-0012) é comparada no registro apenas como referência; **a meta vale na LC.12**.

Peças do ensaio:

| Peça | Caminho |
|---|---|
| Orquestrador | `scripts/operacao/ensaio-restauracao-local.sh` |
| Contagem + checksum (SQL) | `scripts/operacao/sql/verificacao-contagem-checksum.sql` |
| Teste de RLS (SQL) | `scripts/operacao/sql/teste-rls-restaurado.sql` |
| Prova da OS inversa (SQL) | `scripts/operacao/sql/verificacao-os-inversa.sql` |
| Restauração seletiva e desfazer (P3.33, sem reimplementação) | `scripts/operacao/restaurar-tenant.sh`, `verificar-isolamento-tenant.sh`, `desfazer-lote.sh` |
| Backup de origem (P0.28) | `scripts/local-backup.sh` → `ziipsan.dump` + `wal-lsn.txt` |
| Harness sem Docker | `scripts/test-ensaio-restauracao-local.sh` |
| Modelo de registro | `docs/operacao/ensaios-restauracao/MODELO-REGISTRO-ENSAIO-GL3.md` |

Bases do ensaio — **descartáveis e isoladas** (nunca o banco de trabalho `ziipsan-postgres-primary`:5432, nunca o de integração :55433, nunca a lateral do PITR da P0.28 :55434, nunca a da restauração mensal da P4.7 :55435):

| Base | Container | Porta | Papel |
|---|---|---|---|
| Principal do ensaio | `ziipsan-ensaio-gl3` | `127.0.0.1:55436` | restauração completa + verificações + seletiva + desfazer |
| Lateral do ensaio | `ziipsan-ensaio-gl3-lateral` | `127.0.0.1:55437` | fonte da extração seletiva da P3.33 (instante-alvo) |

Dentro dos containers descartáveis a base chama-se `ziipsan` — é o que permite aos scripts da P3.33 (`-d ziipsan`) rodarem **sem nenhuma alteração**, apontados por `ZIIPSAN_PG_CONTAINER`/`ZIIPSAN_LATERAL_CONTAINER`, que o orquestrador exporta. Senhas dos containers: efêmeras, geradas na execução, nunca registradas. A role `ziipsan_app` (que não viaja no `pg_dump`) é criada só no container principal descartável, `NOSUPERUSER NOBYPASSRLS`, com senha efêmera, antes do `pg_restore` — para os GRANTs do dump valerem e o teste de RLS ser real. Ela some com o container.

## 1. Pré-requisitos

- Ambiente local no ar (P0.27) e **backup da P0.28 existente**: diretório com `ziipsan.dump` não vazio (e `wal-lsn.txt`, quando houver), produzido por `scripts/local-backup.sh`.
- Runtime de container **podman** no PATH (os scripts da P3.33 o chamam literalmente; `ZIIPSAN_CONTAINER_RUNTIME` só renomeia o binário do orquestrador).
- **Imagem do PostgreSQL descartável ≥ a versão que gerou o dump:** a imagem padrão é `postgis/postgis:15-3.4` (PG15, precedente da P4.7). Um `ziipsan.dump` gerado por um PostgreSQL **mais novo** (16/17) **não restaura** numa imagem PG15 — o `pg_restore` falha e a etapa (b) sai **3** (fail-closed, registrado; não há contorno). Se o banco de origem for PG16+, rode com `ZIIPSAN_GL3_IMAGE` apontando para a imagem PostGIS do major correspondente.
- Scripts da P3.33 presentes no repo (`restaurar-tenant.sh`, `desfazer-lote.sh`, `verificar-isolamento-tenant.sh`) — o pré-voo recusa sem eles.
- Massa do ensaio no banco de origem (a que o backup capturou):
  - **dois tenants** no catálogo (`public.tenant_catalog`): o **alvo** da restauração seletiva e a **testemunha** (prova de não-afetação), com dados conhecidos — no ensaio, **sintéticos** (como no roteiro da P3.33);
  - no tenant-alvo, tabelas com RLS habilitada e massa (o teste de RLS sem massa sai `INCONCLUSIVO` e a etapa **reprova**: ensaio sem massa não é prova);
  - na testemunha, a tabela-sonda `restore_isolation_probe` criada (DDL no roteiro da P3.33);
  - **um lote aplicado** (`run.json` com `status APPLIED` + `reverter.sql`) no tenant-alvo, pronto para ser desfeito — de preferência um lote **com efeito por OS** (`businessEffectOrderId` no `run.json`), que é o caso do aceite da GL.3 (desfazer por OS inversa); o caminho sem OS (remoção por execução) também é aceito e provado de outro modo (seção 4, etapa e).
- Para o lote com OS: o **descritor da OS inversa** (`InverseOrderRequest` da P3.33: `tenantId`, `originalOrderId` = OS de origem, `bulkRunId` = run_id do lote, `reason`, `requestedBy`, `requestedAt`) preparado em arquivo JSON.
- Consentimento do tenant-alvo para o instante-alvo e o modo REPLACE (moldes do ADR-0020, o mesmo arquivo que o `restaurar-tenant.sh aplicar` confere).
- Espaço no sistema de arquivos do repositório: ~dump × 6 + 256 MB (o pré-voo confere).
- **Aceite da etapa:** pré-requisitos conferidos. **Abortar (saída 2):** qualquer item ausente — o orquestrador recusa no pré-voo e grava o registro com a causa; não improvisar massa nem consentimento.

## 2. Preparação do instante-alvo e da lateral

A lateral do ensaio é uma **segunda restauração do mesmo dump**: o instante-alvo do ensaio é, portanto, o **instante do backup**. Se o ensaio quiser um instante-alvo **diverso** do backup (PITR com replay de WAL), a lateral precisa ser preparada antes pelo roteiro da P0.28 (`scripts/local-pitr.sh`, container parado no instante-alvo) e o orquestrador deve ser adaptado para usá-la — nesta entrega, o replay de WAL **não** é automatizado nem fingido: o registro do ensaio declara qual dos dois casos foi executado (o modelo de registro tem o campo).

## 3. Execução

```bash
scripts/operacao/ensaio-restauracao-local.sh \
  --backup-dir /var/tmp/ziipsan-backup-<carimbo> \
  --tenant-schema <schema-alvo> \
  --testemunha-schema <schema-testemunha> \
  --instante-alvo <ISO-8601 do backup> \
  --consentimento <consentimento.json> \
  --lote-run-id <run_id> \
  --lote-dir <dir da execucao do lote> \
  [--lote-schema <schema do lote; padrao: o do alvo>] \
  [--os-inversa-json <descritor.json>] \
  [--keep] [--dry-run]
```

- `--dry-run` imprime o plano e sai 0 sem executar nada.
- `--keep` mantém os containers descartáveis no ar ao fim (o orquestrador avisa o comando de remoção); sem ele, a limpeza é parte do ensaio. O uso de `--keep` entra na seção de Desvios do registro.
- **Interrupção (Ctrl-C/SIGTERM):** o orquestrador tem trap — grava o registro como FALHA na etapa corrente ("interrompido por SIGINT/SIGTERM"), limpa os containers descartáveis (respeitando `--keep`) e sai 130/143. Não é preciso limpar à mão depois de interromper.
- **Erro de sintaxe de argumentos** (opção desconhecida, valor faltando) também grava registro mínimo de FALHA no pré-voo e sai 2 — nenhum ensaio morre sem registro.
- **Desvios declarados por quem executa:** rode com `ZIIPSAN_GL3_DESVIOS="<texto>"` no ambiente para o texto entrar na seção de Desvios do registro gerado, ao lado dos desvios que o próprio orquestrador registra (role recriada no descartável, lateral = mesmo dump, caminho do desfazer, `--keep`).
- **Para o lote com efeito por OS:** no meio do ensaio (a etapa (e) recusa a remoção de linhas e confere o descritor), abra e **encerre a OS inversa na camada de OS** (P1.31, `InverseOrderRequest`/ADR-0039) e confirme ao orquestrador que ela foi encerrada **antes** de ele verificar o efeito, rodando o processo com `GL3_OS_INVERSA_CONFIRMADA=<run_id>` no ambiente. Sem a confirmação, a etapa sai 7 — de propósito: a prova é o efeito no banco, não a palavra do operador; a confirmação só declara que a OS foi encerrada a tempo de ser verificada.

## 4. Sequência do ensaio (o que o orquestrador faz, em ordem)

| # | Etapa | O que prova | Aceite | Abortar |
|---|---|---|---|---|
| a | **Pré-voo** | runtime, dependências da P3.33, backup presente/válido, parâmetros completos, espaço | tudo presente | saída **2**, registro com a causa |
| b | **Restauração completa cronometrada** | o backup da P0.28 restaura inteiro (`pg_restore --exit-on-error`) em base isolada; lateral idem; início/fim/duração registrados | `pg_restore` sem erro nas duas bases | saída **3** |
| c1 | **Contagem + checksum** | `verificacao-contagem-checksum.sql`: contagem por tabela e SHA-256 canônico por tabela (o mesmo hash da fotografia da P3.33); checksum global = SHA-256 do TSV | ≥ 1 tabela; contagens e hashes gravados em `contagens-checksum.tsv` | saída **4** (restauração vazia reprova) |
| c2 | **Teste de RLS** | `teste-rls-restaurado.sql` **como `ziipsan_app`**: por tabela com RLS do schema-alvo — sem contexto = 0; contexto do alvo = só as linhas dele; contexto de outro tenant = 0 | resumo `RLS_RESUMO=OK` | saída **5** (inclusive `INCONCLUSIVO` sem massa) |
| d | **Restauração seletiva (P3.33)** | fotografia da testemunha **antes** (`registrar` + checksum GL.3) → `extrair` da lateral → `comparar` → **sonda durante** (`sondar`) → `aplicar` REPLACE (consentimento conferido pelo script) → fotografia **depois** (`conferir`) + checksum da testemunha antes == depois | tenant-alvo igual ao instante-alvo e testemunha **bit a bit** intacta (contagem+checksum) | saída **6** |
| e | **Desfazer do lote** | com OS: `desfazer-lote.sh` **recusa** a remoção de linhas (ADR-0039) → descritor conferido → `verificacao-os-inversa.sql`: OS com `reverses_order_id` = OS de origem, `CLOSED_EXECUTED`, com efeitos em `service_order_effects`. Sem OS: remoção por execução e checksum do schema do lote antes != depois | efeito do desfazer **provado no banco** | saída **7** |
| f | **Limpeza + registro** | containers descartáveis removidos (salvo `--keep`); registro do ensaio gravado com os valores **medidos** | registro em `docs/operacao/ensaios-restauracao/<carimbo>-gl3/registro-ensaio.md` + artefatos (TSVs) | — |

**Regra de aborto:** a primeira falha para o ensaio, com código de saída distinto por etapa (2–7), registro de FALHA gravado com etapa e causa, e containers descartáveis removidos. Não se "corrige na mão" para o ensaio passar: divergência é resultado do ensaio e fica registrada.

## 5. Como registrar

1. O orquestrador grava sozinho o **registro do ensaio** preenchido com os valores medidos (tempos por etapa e total × referência de 4 h, contagens, checksum, RLS, tenant restaurado e prova da testemunha, lote desfeito e prova do efeito) — conferir contra o **modelo** em `docs/operacao/ensaios-restauracao/MODELO-REGISTRO-ENSAIO-GL3.md`.
2. Quem executou completa no registro (ou em documento ao lado) os **desvios**: qualquer passo manual, a decisão sobre tabelas `ONLY_IN_CURRENT` da restauração seletiva (roteiro da P3.33, passo 7), e se a lateral foi o dump ou um PITR preparado à parte.
3. O registro diz, no cabeçalho e na linha da referência: **resultado de ensaio, sem meta — a meta vale na LC.12**. Não reescrever o registro à mão para "melhorar" número: ele é a evidência do ensaio.
4. Commit do diretório do ensaio (`registro-ensaio.md` + TSVs) como evidência, junto do marco da camada no diário de bordo.

## 6. Fronteiras (não são deste ensaio)

- **Meta** de RTO/RPO: **LC.12** (e LC.3 para o RDS). Aqui é registro.
- Restauração sobre o **RDS**: LC.3. PITR com replay de WAL automatizado: não existe no projeto (a P0.28 o deixou como roteiro) — ver seção 2.
- RTO **por tenant** com meta: LC.3; o `restaurar-tenant.sh` já registra o dele a cada aplicação.
- A restauração mensal automática é a **P4.7** (`monthly-restore.sh`) — rotina recorrente, outro objeto; a GL.3 é o ensaio completo, incluindo seletiva e desfazer, que a mensal não faz.
