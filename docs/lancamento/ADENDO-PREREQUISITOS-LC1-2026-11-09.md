# ADENDO DATADO ao PREREQUISITOS-LC1 — LC.1 (09/11/2026, no ato do pouso)

- **Pacote:** LC.1 — Ensaio de implantação na AWS com dado sintético (preparação)
- **Natureza deste ficheiro:** adendo datado prescrito pelo `LANDING.md`
  ("Reverificação no pouso", item 1) e pela disposição do defeito menor
  **D-R1** na `REVISAO-LC.1.md`/`CONFIRMACAO-LC.1.md`: os estados de
  pré-requisitos mudaram entre a produção do pacote e o pouso, por isso
  pousa-se este adendo **em vez de reescrever** o `PREREQUISITOS-LC1.md`
  original, que pousa byte-idêntico ao confirmado.
- **Autor:** sessão `41479bcd-066d-4a8d-900c-54bc2229a13a` (papel: Executor
  de pouso) — distinta do produtor, do revisor e do confirmador.
- **Data:** 09/11/2026. **Main no ato:** HEAD
  `6495e78ead8eec9695f81807b32df2cdb526fba6` (ED-01 pousada).

## Estados que mudaram desde a conferência original

### 1. GL.4 (item A2 do aceite) — a decisão pendente foi tomada

O `PREREQUISITOS-LC1.md` dizia "não existe pasta `gl.4/`" e "decisão
formal pendente do dono" (A2 não cumprido; pendência P-LC1-07). Estado no
ato do pouso:

- A pasta `gl.4/` existe, com dossiê e termo; o **aceite formal do dono
  foi realizado** — decisão nº 37 do `DECISOES-DO-DONO.md` (09/11/2026,
  03:08 −03), sem ressalvas declaradas, ato registado em
  `gl.4/ACEITE-DO-DONO-GL4.md`. **Pendência GL.4-01 RESOLVIDA** e, com
  ela, a **P-LC1-07** deste pacote.
- O pacote GL.4 está **pousado na main**: commit
  `32fff01c0b7ea04e435d901443495366f6479b3b` (2 ficheiros novos em
  `docs/homologacao/` + verbete de diário; verificação pós-pouso 3/3 em
  `gl.4/VERIFICACAO-POS-POUSO-GL.4.md`, artefacto local).
- Efeito: a pré-condição A2 deixa de estar pendente do ato do dono. Os
  limites que o próprio dossiê declara mantêm-se (GL.2 sem ciclos reais,
  GL.3 sem ensaio real, FEBRABAN 150 com arquivos de teste e sem envio
  ao banco) — são limites do objeto aceite, não pendências deste adendo.

### 2. ED-01 (item A3 do aceite) — material existe e está pousado; fecho não declarado

O `PREREQUISITOS-LC1.md` dizia "pasta `ed-01/` existe vazia / sem
material para cruzar" (A3 não concluído). Estado no ato do pouso:

- A ED-01 foi produzida, revista, corrigida, reapreciada e confirmada, e
  está **pousada na main**: commit
  `6495e78ead8eec9695f81807b32df2cdb526fba6` (7 ficheiros novos em
  `docs/poc/`; verificação pós-pouso 7/7 em
  `ed-01/VERIFICACAO-POS-POUSO-ED-01.md`, artefacto local).
- Placar da verificação PD-01 registado no pouso da ED-01: **55/59
  comprovados, 4 parciais, 0 sem evidência**; os 4 parciais (ED-04,
  ED-08, ED-12, P4.2) são pendências dos pacotes de origem.
- Efeito honesto: o material de cruzamento **existe**; a prontidão PD-01
  **não foi declarada fechada** pela ED-01. A conclusão A3 mantém-se
  "não concluído por inteiro", agora por causa nomeada (4 parciais), e
  não por ausência de material.

### 3. IaC (busca `*.tf`: zero) — IaC local existe; a do ensaio continua [A DEFINIR]

- A frente da LC.2 já produziu localmente ficheiros Terraform
  (`lc.2/entrega/infra/aws/modules/…` — módulos foundation/edge/platform
  e outros), logo a frase literal "busca `*.tf`: zero" está superada.
- A **IaC do ensaio da LC.1 continua [A DEFINIR]**: a pendência
  **P-LC1-02** mantém as duas saídas expressas — (a) IaC mínima própria
  do ensaio ou (b) antecipação expressa do recorte da LC.2. A existência
  da IaC da LC.2 não resolve a P-LC1-02 automaticamente (é uma das
  saídas, não a resolução).

### 4. Conta AWS de ensaio (P-LC1-01) — decidida: adiada para a contratação

O `PREREQUISITOS-LC1.md` §7 tratava a conta AWS como fator externo
pendente. Estado no ato do pouso:

- **Decisão nº 41 do dono** (09/11/2026, 03:11 −03): **NÃO criar a conta
  AWS de ensaio agora**; a conta fica para a **fase de contratação** e,
  até lá, as provas da trilha LC seguem locais. Registo em
  `lc.1/DECISAO-AWS-CONTRATACAO.md` (artefacto local).
- Efeito: a P-LC1-01 deixa de ser "decisão pendente" e passa a **ato
  externo adiado por decisão**. Nada do estado provado localmente muda.

## O que NÃO muda

Todas as restantes declarações e conclusões do `PREREQUISITOS-LC1.md`
mantêm-se tal como produzidas, revistas e confirmadas — incluindo os
[SEM FONTE] da suíte de contrato da P0.45 e do placar numérico da P0.27,
que o passo 0.6/0.7 do roteiro manda fechar antes do ensaio. Este adendo
não reescreve o original nem altera nenhum dos 4 documentos confirmados
do pacote (hashes na `CONFIRMACAO-LC.1.md` §3).

---

*Fim do adendo. Ficheiro novo do ato do pouso, ao abrigo da excepção
expressa na CONFIRMACAO-LC.1.md §3 (adendo datado prescrito para D-R1).*
