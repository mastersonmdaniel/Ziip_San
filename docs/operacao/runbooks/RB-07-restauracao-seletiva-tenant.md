# Restauração seletiva de um tenant e desfazer de operação em massa

- Tarefa: **P3.33** (PLT-03). Dependências: PITR/backup da **P0.28** e a saída de tenant da **P3.16**.
- Responsável: `platform` — execução **só por `PLATFORM_OWNER` com step-up** (portão `TenantRestorePolicy`, pacote `br.com.ziipsan.core.restore`).
- Ambiente: ambiente local (P0.27) até o lançamento; sobre o PITR do RDS, na LC.3. **Sem meta de RTO antes da LC.3** — o RTO por tenant é **registrado** a cada execução (RNF novo desta tarefa, `docs/arquitetura/RNF-RESTAURACAO-TENANT.md`).
- Scripts: `scripts/operacao/restaurar-tenant.sh`, `scripts/operacao/copia-logica-diaria-tenant.sh`, `scripts/operacao/desfazer-lote.sh`, `scripts/operacao/verificar-isolamento-tenant.sh`.

## 1. Quando usar

- Dado de **um** tenant corrompido ou apagado por erro (humano ou de lote), com os demais tenants íntegros.
- Desfazer de **uma** operação em massa aplicada (seção 7).
- **Não** é o caminho para desastre do cluster inteiro: esse é o PITR completo da P0.28 (GL.3/LC.12).

## 2. Portões (todos, antes de qualquer escrita)

1. **Papel e step-up:** executor `PLATFORM_OWNER`, com reautenticação MFA **do momento** da execução. MFA de requisição anterior não executa restauração (`mfa-not-current`).
2. **Aprovação do tenant** nos moldes do consentimento do ADR-0020 (`RestoreConsent`): vigente, não revogada, deste tenant, com **instante-alvo** e **modo** (substituição ou mesclagem) iguais aos da execução. Restaurar para outro instante ou noutro modo é outra restauração — exige consentimento novo.
3. **Janela:** a aplicação (substituição/mesclagem) só ocorre dentro da janela declarada com o tenant na aprovação (tipicamente a janela de manutenção do RNF-12). Extração e comparação podem ocorrer fora dela; a **aplicação**, não (`outside-window`).
4. **Trilha:** toda tentativa — permitida ou negada — é registrada (`RestoreAudit`: `audit_log` do tenant e `platform.audit_event`, com o código estável da decisão).

Negativa em qualquer portão: **parar**, registrar o código da decisão e comunicar o tenant. Não existe "desta vez sem consentimento".

## 3. Fontes da restauração (nesta ordem de preferência)

1. **PITR em instância lateral** (P0.28): o backup/WAL restaurado num PostgreSQL descartável, parado no instante-alvo. É a fonte dos instantes entre cópias.
2. **Cópia lógica diária do tenant** (seção 5): o `pg_dump -n` mais próximo **anterior** ao instante-alvo, ainda não expirada (RNF-43: expiração em até 35 dias — cópia vencida **não** é fonte).
3. Sem fonte íntegra para o instante pedido: **não restaurar** um instante diverso do consentido; escalar e renegociar o instante com o tenant (consentimento novo).

## 4. Procedimento — extrair e comparar

1. Restaurar o PITR na instância lateral até o instante-alvo (roteiro de `scripts/local-pitr.sh` da P0.28), container `ziipsan-pitr-lateral`. Conferir na lateral: o relógio do banco restaurado e uma contagem conhecida do tenant.
2. Extrair: `restaurar-tenant.sh extrair <schema> <instante-alvo>` — `pg_dump -n <schema>` da lateral (modelo SCHEMA do ADR-0005; tenant em schema compartilhado — modo SHARED da P4.6 — extrai por `tenant_id`, ver §8) e a **fotografia** do schema extraído (contagem + SHA-256 por tabela).
3. Comparar: `restaurar-tenant.sh comparar <schema> <dir-da-extracao>` — fotografia do estado atual e `comparacao.txt`. Na integração, a mesma comparação é classificada por `RestoreDiff` (Java): `UNCHANGED`, `RESTORE`, `ONLY_IN_EXTRACTED`, `ONLY_IN_CURRENT`.
4. Apresentar a comparação **ao tenant**: tabelas que voltam, contagens antes/depois e — decisão explícita — as tabelas `ONLY_IN_CURRENT` (criadas depois do instante-alvo): **a restauração nunca as apaga por omissão**.

## 5. Cópia lógica diária (a fonte programada)

- Job diário: `copia-logica-diaria-tenant.sh` — para cada tenant `ACTIVE` do catálogo, `pg_dump -n <schema>` em armazenamento com retenção imutável, com manifesto (`DailyTenantCopy.CopyManifest`): hash SHA-256, tamanho, captura, expiração `captura + 35 dias` (teto do RNF-43) e o `tenantKeyId` da P0.28.
- A mesma cópia alimenta a **exportação de fim de contrato da P3.16** e o **pacote de reversão da ED-04** (formato manifesto + `SHA256SUMS` + dump, o que o `importar-tenant.sh` da ED-04 já consome).
- Cópia do dia existente **nunca é sobrescrita** (imutabilidade); falha do job é alerta operacional, não retry silencioso por cima.
- **Crypto-shredding: escopo da LC.5.** Aqui ele é só **preparado**: o manifesto carrega o `tenantKeyId` para a destruição da DEK alcançar estas cópias. Não declarar shredding feito nesta tarefa.

## 6. Procedimento — aplicar (em janela)

1. Conferir os portões da seção 2 (o script `aplicar` confere o arquivo de consentimento contra schema, instante-alvo, modo e o `tenant_id` do catálogo, e exige `APLICAR_RESTAURACAO=<schema>`).
2. O script grava **antes** a fotografia de segurança do estado atual (`pre-restauracao.dump` no diretório da extração): se a aplicação der errado, o "antes" existe.
3. **REPLACE:** `restaurar-tenant.sh aplicar <schema> <dir> REPLACE` — drop do schema do tenant e `pg_restore` da extração, fotografia pós-restauração igual à extraída (o script confere e aborta a declaração de sucesso se divergir), registro em `public.audit_log`.
4. **MERGE:** **não automatizado**. A mesclagem é tabela a tabela a partir de `comparacao.txt`, com comandos revisados um a um por `PLATFORM_OWNER` e o efeito de cada tabela registrado na trilha. O script recusa `aplicar ... MERGE` de propósito.
5. Cronometrar do início da aplicação ao fim da conferência: esse é o **RTO por tenant** registrado (seção 9).
6. Encerrar a instância lateral e descartá-la.

## 7. Desfazer operação em massa (duas camadas)

Toda operação em massa nasce em duas etapas (`BulkOperation.BulkRun`): **simulação** (`SIMULATED`, com a contagem prevista) e **aplicação** (`APPLIED`, com a contagem efetiva), sob um `run_id`. Simulação pode ser **descartada**; nunca se aplica sem simular.

1. **Remoção por execução (dado):** `desfazer-lote.sh <run_id> <dir-da-execucao>` aplica a **imagem-anterior daquela execução** (`reverter.sql`, gerado na aplicação) numa única transação. `run_id` diverso do gravado aborta antes de qualquer comando (`run-id-mismatch`) — uma execução nunca desfaz outra.
2. **OS inversa (negócio, ADR-0039):** se a execução gerou efeito de negócio por OS (efeito aplicado no encerramento, P1.31), a remoção de linhas **não basta** e o script **recusa**: a correção é uma **OS inversa** ligada à OS de origem (`BulkOperation.InverseOrderRequest`, com justificativa obrigatória), que aplica o efeito contrário no **seu** encerramento. Nunca se edita nem se apaga o efeito já aplicado da OS de origem.

## 8. Tenant em schema compartilhado (modo SHARED, P4.6)

- A extração é por `tenant_id` nas tabelas compartilhadas (dump filtrado), e a aplicação é substituição/mesclagem **das linhas daquele `tenant_id`** — nunca do schema inteiro.
- A fotografia e a comparação usam o mesmo recorte por `tenant_id`; o isolamento dos demais tenants é ainda mais crítico (eles dividem as tabelas) e a prova do ensaio é obrigatória antes do primeiro uso real neste modo.

## 9. Registro (toda execução)

```
Restauracao P3.33 — data/hora: ____  ambiente: ____
Tenant (id/schema): ____  instante-alvo: ____  fonte: PITR lateral | copia diaria de ____
Consentimento (id): ____  modo: REPLACE | MERGE  janela: ____ a ____
Executor (PLATFORM_OWNER): ____  step-up em: ____
Comparacao: tabelas RESTORE ____  ONLY_IN_EXTRACTED ____  ONLY_IN_CURRENT ____ (decisao: ____)
Inicio da aplicacao: ____  fim + conferencia: ____  RTO por tenant: ____
Fotografia pos == extraida: SIM | NAO (acao: ____)
Isolamento (tenant-testemunha, no ensaio): OK | FALHOU
Trilha: restore_id / run_id: ____
```

## 10. Critérios de abortar

- Qualquer portão da seção 2 negado; hash da extração divergente; fotografia pós-restauração divergente da extraída; tenant-testemunha alterado no ensaio. Em todos: **parar, preservar as evidências e o `pre-restauracao.dump`**, registrar e escalar — nunca "corrigir na mão" por fora do procedimento.
