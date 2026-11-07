# P3.33 — Roteiro de ensaio de restauração seletiva com tenant sintético

> **Onde este roteiro roda:** na CI total / ambiente do dono (`scripts/ci.sh` no PC dele, com o banco de integração e o ambiente local da P0.27). **Ele não roda nesta VM de desenvolvimento** (sem Java/Maven/Docker) e nenhum ensaio é dado como executado sem o Registro do Ensaio preenchido (seção 5).
>
> **Dados do ensaio:** tenants **sintéticos** apenas — um tenant-alvo e um tenant-testemunha, sem dado pessoal real. O ensaio é destrutivo sobre o tenant-alvo sintético por desenho: ele é restaurado a um instante anterior no meio do roteiro.

Documentos de apoio: runbook `RB-07-restauracao-seletiva-tenant.md`, pacote `br.com.ziipsan.core.restore` (portão, consentimento, diff, cópia diária, lote reversível, trilha), PITR da P0.28 (`scripts/local-pitr.sh`) e o RNF novo em `docs/arquitetura/RNF-RESTAURACAO-TENANT.md`.

## 1. Pré-voo

- Rodada da CI total verde até a camada anterior; testes puros do pacote `restore` verdes no `mvn verify`.
- Ambiente local no ar (P0.27) com arquivamento de WAL ativo (P0.28).
- **Aceite da etapa:** pré-voo conferido. **Abortar:** WAL não arquivando — sem PITR não há instante-alvo; registrar e não seguir.

## 2. Massa sintética

1. Criar **dois** tenants sintéticos no catálogo: o **alvo** (`schema` A) e a **testemunha** (`schema` B), com dados sintéticos conhecidos (contagens registradas por tabela).
2. Na testemunha, criar a tabela-sonda do isolamento:
   `CREATE TABLE <schema-B>.restore_isolation_probe (id int PRIMARY KEY, note text NOT NULL, updated_at timestamptz NOT NULL DEFAULT now());`
3. Registrar o instante **T0** (relógio do banco) — o instante-alvo da restauração será T0.
4. Gerar a cópia lógica diária dos dois tenants (`copia-logica-diaria-tenant.sh <schema>` de cada um) e conferir manifesto, `SHA256SUMS` e expiração = captura + 35 dias (RNF-43).

## 3. Mutação pós-alvo (o que a restauração deve desfazer — só no alvo)

1. No tenant-alvo, depois de T0: alterar linhas conhecidas, inserir linhas novas e criar **uma tabela nova** (a futura `ONLY_IN_CURRENT`).
2. Na testemunha: **nada** é alterado pela equipe do ensaio fora da sonda.
3. Registrar a fotografia da testemunha: `verificar-isolamento-tenant.sh registrar <schema-B> testemunha-antes.tsv`.

## 4. Sequência do ensaio

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Tentar executar a restauração com papel ≠ `PLATFORM_OWNER` e depois sem step-up do momento (`TenantRestorePolicy`) | **Recusado** (`platform-owner-required`, `mfa-required`/`mfa-not-current`) e registrado na trilha como `RESTORE_DENIED` |
| 2 | Tentar sem consentimento do tenant e com consentimento de instante-alvo diverso | **Recusado** (`consent-required`, `consent-target-mismatch`) |
| 3 | Restaurar o PITR na instância lateral até T0 e `restaurar-tenant.sh extrair <schema-A> <T0>` | Extração só do schema A, com fotografia e hash conferidos |
| 4 | **Durante** a restauração: `verificar-isolamento-tenant.sh sondar <schema-B>` (algumas vezes, inclusive durante o passo 7) | Sonda lê/grava/atualiza/apaga com sucesso em todas as tentativas |
| 5 | `restaurar-tenant.sh comparar <schema-A> <dir>` e classificar com `RestoreDiff` | Tabela alterada = `RESTORE`; tabela nova pós-T0 = `ONLY_IN_CURRENT`; demais = `UNCHANGED` |
| 6 | Consentimento do tenant sintético para T0/REPLACE (moldes do ADR-0020) e tentar aplicar **fora** da janela declarada | **Recusado** (`outside-window`) |
| 7 | Em janela: preservar a tabela `ONLY_IN_CURRENT` (passo manual da nota abaixo) e `restaurar-tenant.sh aplicar <schema-A> <dir> REPLACE`, cronometrado | Schema A igual ao de T0 (fotografia pós == extraída); tabela nova pós-T0 **preservada** pela reimportação manual registrada; registro em `audit_log` |
| 8 | `verificar-isolamento-tenant.sh conferir <schema-B> testemunha-antes.tsv` | **Isolamento OK**: nenhuma tabela da testemunha mudou |
| 9 | Operação em massa sintética no alvo: simular (`BulkRun.simulate`), aplicar e depois `desfazer-lote.sh <run_id> <dir>` | Contagens voltam ao previsto pela imagem-anterior; `run_id` errado é recusado (`run-id-mismatch`) antes de qualquer efeito |
| 10 | Operação em massa com efeito por OS: montar o `InverseOrderRequest` (ADR-0039) | Descritor ligado à OS de origem e ao `run_id`, com justificativa; `desfazer-lote.sh` **recusa** a remoção de linhas dessa execução |
| 11 | Cópia diária vencida: avançar a data de referência além de captura + 35 dias (`DailyTenantCopy.isUsableAsRestoreSource`) | Cópia **não** é aceita como fonte (RNF-43) |

> **Nota do passo 7 (honesta):** o `DROP SCHEMA ... CASCADE` do REPLACE remove também a tabela `ONLY_IN_CURRENT`. No ensaio, a decisão registrada para ela é **preservar**: antes do passo 7, exportar a tabela nova do `pre-restauracao.dump` e reimportá-la depois da aplicação (comando manual registrado no Registro do Ensaio). Se a decisão do tenant for outra (descartar), ela fica registrada do mesmo jeito — o que o aceite veda é o descarte **por omissão**.

- **Aceite do ensaio:** todos os passos com o resultado esperado, inclusive as **recusas** dos passos 1, 2 e 6 e a preservação/decisão do passo 7.
- **Abortar:** qualquer passo divergente — registrar passo, código e evidência; não forçar o seguinte.

## 5. Registro do Ensaio (template)

```
Ensaio P3.33 — data: ____  ambiente/rodada CI: ____
Tenant-alvo sintetico (id/schema): ____  Testemunha (id/schema): ____
Instante-alvo T0: ____  Fonte: PITR lateral | copia diaria
Passo 1..11: OK / FALHOU (passo, codigo, evidencia)
RTO por tenant medido (inicio da aplicacao -> fim da conferencia): ____
  (registrado SEM meta — a meta vale na LC.3; RNF novo da P3.33)
Decisao sobre ONLY_IN_CURRENT: preservar | descartar (quem aprovou: ____)
Isolamento da testemunha: OK | FALHOU
Assinatura de quem executou: ____
```

## Fronteiras (não são deste ensaio)

- RTO **com meta** (≤ 4 h do cluster é o RNF-15; o RTO por tenant ganha meta na **LC.3**).
- Crypto-shredding das cópias e dos backups: **LC.5** (aqui só a preparação por `tenantKeyId`).
- Restauração sobre o PITR do **RDS**: **LC.3**; ensaio de restauração do cluster (GL.3) consome este roteiro.
