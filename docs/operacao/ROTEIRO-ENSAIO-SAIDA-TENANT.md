# P3.16 — Roteiro de ensaio de saída de tenant sintético

> **Onde este roteiro roda:** na CI total / ambiente do dono
> (`scripts/ci.sh` no PC dele, com o banco de integração). **Ele não
> roda nesta VM de desenvolvimento** (sem Java/Maven/Docker) e nenhum
> ensaio é dado como executado sem o Registro do Ensaio preenchido
> (template na seção 4).
>
> **Dados do ensaio:** tenant **sintético** apenas (`tenant_id` de
> ensaio, contrato SaaS sintético, sem dado pessoal real). O ensaio é
> destrutivo por desenho: ele elimina o tenant sintético ao fim.

Documentos de apoio: migration core `V10_5`, `TenantExitCalculator`
(regras puras, testadas sem banco), `TenantExitService` (fluxos com
banco) e `TicketRetentionService` da P3.19 (portão RAS-18).

## 1. Pré-voo

- Rodada da CI total verde até a camada anterior; `V10_5` aplicada no
  banco de integração (conferir `flyway_schema_history` do core).
- **Aceite da etapa:** `V10_5` presente e sem erro de migração.
- **Abortar:** qualquer falha de migração — registrar e não seguir.

## 2. Massa sintética

1. Criar tenant sintético no catálogo + perfil (`legal_nature`
   parametrizável: rodar o ensaio **duas vezes**, uma `PRIVATE`/
   STANDARD e uma pública — `AUTARCHY`/PUBLIC_SECTOR).
2. Criar contrato SaaS sintético (`platform.saas_contract`) com
   vigência conhecida (ex.: 2026-03-01 a 2026-12-31) e ao menos um
   chamado de suporte (para a exceção dos chamados no certificado).
3. Prover a DEK do tenant pelo provedor da P0.28 e cifrar um campo
   sintético (a prova do crypto-shredding lê esse campo no passo 9).

## 3. Sequência do ensaio (relógio injetável em todos os passos)

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Abrir a saída (`openExit`) | Vencimentos gravados: exportação +15d (STANDARD) ou 10 dias úteis (PUBLIC_SECTOR); no PUBLIC_SECTOR, período de consulta criado com duração = prazo do contrato, teto 120d |
| 2 | Gerar a exportação nos 3 formatos e registrar (`registerExportGenerated`) | Manifesto com hash por tabela, hash geral e dicionário gerados; download registrado (`recordDownload`) |
| 3 | (PUBLIC_SECTOR) Tentar **escrever** dado do tenant durante a consulta (`requireWriteAllowedNow`) | **Recusado** (`readonly_consultation`) |
| 4 | Agendar a eliminação com papel ≠ `PLATFORM_OWNER` e depois sem MFA | **Recusado** nos dois casos (`forbidden_role`, `mfa_required`) |
| 5 | Agendar com `PLATFORM_OWNER` + MFA; cancelar dentro da carência; reagendar | Agendamento e cancelamento registrados |
| 6 | Executar **antes** do fim da carência | **Recusado** (`grace_not_elapsed`) |
| 7 | (natureza pública) Executar sem tabela de temporalidade/listagem aprovadas da P3.19 | **Recusado** (`archival_approval_missing`); registrar os 4 registros e repetir o portão liberado |
| 8 | Registrar contestação na carência; tentar executar; avançar o relógio para a janela do alerta (vencimento − 3d) | Execução suspensa (`held_by_contestation`); `contestationsDueForAlert` devolve a contestação; registrar o alerta e responder em até 15 dias |
| 9 | Registrar ordem judicial; tentar executar; levantar a ordem | Suspensa enquanto vigente (`held_by_court_order`); liberada após o levantamento |
| 10 | Avançar o relógio para depois da carência e executar (`executeElimination`) com expirações de backup/cópias imutáveis do ambiente | DEK destruída confirmada; campo cifrado do passo 2.3 **ilegível**; certificado emitido com hashes, expirações e a exceção dos chamados (`purge_at` = vigência + 5 anos); `tenant_lifecycle` = `DELETED` com o hash do certificado; vencimento do certificado dentro do teto (60d sem contestação / 75d com, da âncora da carência) |

- **Aceite do ensaio:** todos os passos com o resultado esperado,
  inclusive as **recusas** dos passos 3, 4, 6, 7 e as suspensões dos
  passos 8 e 9.
- **Abortar:** qualquer passo com resultado divergente — registrar o
  passo, o código de recusa/erro e a saída; não forçar o passo
  seguinte.

## 4. Registro do Ensaio (template)

```
Ensaio P3.16 — data: ____  ambiente/rodada CI: ____
Tenant sintético (id): ____  natureza/nível: ____
Passo 1..10: OK / FALHOU (passo, código, evidência)
Vencimentos conferidos à mão: exportação ____ consulta ____
  carência ____ certificado ____
Tempo total do ensaio: ____
Assinatura de quem executou: ____
```

## Fronteiras (não são deste ensaio)

- Restauração seletiva de um tenant e cópia lógica diária por tenant:
  **P3.33** (consome esta saída).
- Destruição física de backups e cópias imutáveis nas suas expirações:
  operação de infraestrutura (LC.3/LC.5); aqui só se registram e se
  certificam as datas de expiração.
